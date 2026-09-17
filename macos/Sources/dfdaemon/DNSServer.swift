import Foundation
import Darwin
import Network

final class DNSServer {
    private let alwaysOnTrie: DomainTrie
    private let socialTrie: DomainTrie
    private let schedule = ScheduleManager()
    private let stats = Stats()
    private let streak = Streak()

    private var listenSocket: Int32 = -1
    // Concurrent so one slow query (a cold SafeSearch lookup, a sluggish
    // upstream) can never head-of-line-block every other DNS query on the
    // machine — the recvfrom loop just keeps receiving and hands each query
    // off immediately.
    private let workQueue = DispatchQueue(label: "dfdaemon.work", attributes: .concurrent)
    private let pathMonitor = NWPathMonitor()
    private var isOnline = true
    private var safeSearchCache: [String: (address: (UInt8, UInt8, UInt8, UInt8), expires: Date)] = [:]
    private let safeSearchLock = NSLock()

    init(alwaysOnTrie: DomainTrie, socialTrie: DomainTrie) {
        self.alwaysOnTrie = alwaysOnTrie
        self.socialTrie = socialTrie
    }

    func run() throws {
        startPathMonitor()
        try openSocket()
        log("listening on \(Config.listenAddress):\(Config.listenPort), \(alwaysOnTrie.count) always-on + \(socialTrie.count) social domains loaded")

        var buffer = [UInt8](repeating: 0, count: 512)
        while true {
            var clientAddr = sockaddr_in()
            var clientAddrLen = socklen_t(MemoryLayout<sockaddr_in>.size)
            let n = withUnsafeMutablePointer(to: &clientAddr) { addrPtr -> Int in
                addrPtr.withMemoryRebound(to: sockaddr.self, capacity: 1) { sockPtr in
                    recvfrom(listenSocket, &buffer, buffer.count, 0, sockPtr, &clientAddrLen)
                }
            }
            // A blocked recvfrom uses ~zero CPU while idle — this loop does
            // no polling of its own; power draw tracks query volume, not wall time.
            guard n > 0 else { continue }
            let query = Array(buffer[0..<n])
            stats.recordTotal()
            workQueue.async { [weak self] in
                self?.handle(query: query, from: clientAddr, addrLen: clientAddrLen)
            }
        }
    }

    private func handle(query: [UInt8], from clientAddr: sockaddr_in, addrLen: socklen_t) {
        guard let name = DNSMessage.questionName(from: query) else { return }
        let domain = name.lowercased()
        streak.touch() // cheap: no-op unless the calendar day has changed

        if alwaysOnTrie.isBlocked(domain) {
            stats.recordBlockedAlwaysOn()
            respond(DNSMessage.nxdomainResponse(for: query), to: clientAddr, addrLen: addrLen)
            return
        }

        // Social-block check runs before SafeSearch: youtube.com is in both
        // lists (it needs SafeSearch when open, hard-blocking when not), and
        // checking SafeSearch first meant the bare domain could never
        // actually be blocked during the nightly window — only its
        // subdomains (ytimg.com etc.) were. Blocked wins.
        if schedule.isSocialBlockedNow(), socialTrie.isBlocked(domain) {
            stats.recordBlockedSocial()
            respond(DNSMessage.nxdomainResponse(for: query), to: clientAddr, addrLen: addrLen)
            return
        }

        if let target = Config.safeSearchTargets[domain] {
            stats.recordSafeSearchRewrite()
            if let addr = resolveCached(target) {
                respond(DNSMessage.aRecordResponse(for: query, address: addr), to: clientAddr, addrLen: addrLen)
            } else {
                forward(query: query, to: clientAddr, addrLen: addrLen) // fail open on resolve failure
            }
            return
        }

        forward(query: query, to: clientAddr, addrLen: addrLen)
    }

    // MARK: - Upstream forwarding

    private func forward(query: [UInt8], to clientAddr: sockaddr_in, addrLen: socklen_t) {
        guard isOnline else { return } // no network: drop immediately, no timeout wait, no retry churn

        let upstreamSock = socket(AF_INET, SOCK_DGRAM, 0)
        guard upstreamSock >= 0 else { return }
        defer { close(upstreamSock) }

        var tv = timeval(tv_sec: 2, tv_usec: 0)
        setsockopt(upstreamSock, SOL_SOCKET, SO_RCVTIMEO, &tv, socklen_t(MemoryLayout<timeval>.size))

        var upstreamAddr = sockaddr_in()
        upstreamAddr.sin_family = sa_family_t(AF_INET)
        upstreamAddr.sin_port = Config.upstreamPort.bigEndian
        inet_pton(AF_INET, Config.upstreamDNS, &upstreamAddr.sin_addr)

        let sent = withUnsafePointer(to: &upstreamAddr) { ptr -> Int in
            ptr.withMemoryRebound(to: sockaddr.self, capacity: 1) { sockPtr in
                sendto(upstreamSock, query, query.count, 0, sockPtr, socklen_t(MemoryLayout<sockaddr_in>.size))
            }
        }
        guard sent > 0 else { return }

        var respBuf = [UInt8](repeating: 0, count: 512)
        let n = recv(upstreamSock, &respBuf, respBuf.count, 0)
        guard n > 0 else { return }
        respond(Array(respBuf[0..<n]), to: clientAddr, addrLen: addrLen)
    }

    private func resolveCached(_ hostname: String) -> (UInt8, UInt8, UInt8, UInt8)? {
        safeSearchLock.lock()
        if let cached = safeSearchCache[hostname], cached.expires > Date() {
            safeSearchLock.unlock()
            return cached.address
        }
        safeSearchLock.unlock()

        guard isOnline, let addr = Self.resolveA(hostname) else { return nil }
        safeSearchLock.lock()
        safeSearchCache[hostname] = (addr, Date().addingTimeInterval(Config.safeSearchCacheTTL))
        safeSearchLock.unlock()
        return addr
    }

    private static func resolveA(_ hostname: String) -> (UInt8, UInt8, UInt8, UInt8)? {
        var hints = addrinfo(ai_flags: 0, ai_family: AF_INET, ai_socktype: SOCK_STREAM, ai_protocol: 0,
                              ai_addrlen: 0, ai_canonname: nil, ai_addr: nil, ai_next: nil)
        var result: UnsafeMutablePointer<addrinfo>?
        guard getaddrinfo(hostname, nil, &hints, &result) == 0, let first = result else { return nil }
        defer { freeaddrinfo(result) }
        guard let sockaddrPtr = first.pointee.ai_addr else { return nil }
        let sin = sockaddrPtr.withMemoryRebound(to: sockaddr_in.self, capacity: 1) { $0.pointee }
        let addr = sin.sin_addr.s_addr
        return (UInt8(addr & 0xff), UInt8((addr >> 8) & 0xff), UInt8((addr >> 16) & 0xff), UInt8((addr >> 24) & 0xff))
    }

    // MARK: - Socket plumbing

    private func openSocket() throws {
        listenSocket = socket(AF_INET, SOCK_DGRAM, 0)
        guard listenSocket >= 0 else { throw POSIXError(.EIO) }

        var addr = sockaddr_in()
        addr.sin_family = sa_family_t(AF_INET)
        addr.sin_port = Config.listenPort.bigEndian
        inet_pton(AF_INET, Config.listenAddress, &addr.sin_addr)

        let bindResult = withUnsafePointer(to: &addr) { ptr -> Int32 in
            ptr.withMemoryRebound(to: sockaddr.self, capacity: 1) { sockPtr in
                bind(listenSocket, sockPtr, socklen_t(MemoryLayout<sockaddr_in>.size))
            }
        }
        guard bindResult == 0 else {
            throw NSError(domain: "dfdaemon", code: Int(errno),
                           userInfo: [NSLocalizedDescriptionKey: "bind() failed: \(String(cString: strerror(errno))). Port \(Config.listenPort) may need root (sudo) or is already in use."])
        }
    }

    private func respond(_ packet: [UInt8], to clientAddr: sockaddr_in, addrLen: socklen_t) {
        var addr = clientAddr
        _ = withUnsafePointer(to: &addr) { ptr -> Int in
            ptr.withMemoryRebound(to: sockaddr.self, capacity: 1) { sockPtr in
                sendto(listenSocket, packet, packet.count, 0, sockPtr, addrLen)
            }
        }
    }

    private func startPathMonitor() {
        pathMonitor.pathUpdateHandler = { [weak self] path in
            self?.isOnline = path.status == .satisfied
        }
        pathMonitor.start(queue: DispatchQueue(label: "dfdaemon.pathmonitor"))
    }

    private func log(_ message: String) {
        FileHandle.standardError.write("[dfdaemon] \(message)\n".data(using: .utf8)!)
    }
}
