import Foundation

/// Query-count stats only — never the actual queried domains. Matches the
/// project's "category matches, not browsing history" privacy stance.
final class Stats {
    private let lock = NSLock()
    private var totalQueries = 0
    private var blockedAlwaysOn = 0
    private var blockedSocial = 0
    private var safeSearchRewrites = 0
    private var lastWrite = Date.distantPast
    private let writeInterval: TimeInterval = 10

    func recordTotal() {
        lock.lock(); totalQueries += 1; lock.unlock()
        writeIfDue()
    }

    func recordBlockedAlwaysOn() {
        lock.lock(); blockedAlwaysOn += 1; lock.unlock()
    }

    func recordBlockedSocial() {
        lock.lock(); blockedSocial += 1; lock.unlock()
    }

    func recordSafeSearchRewrite() {
        lock.lock(); safeSearchRewrites += 1; lock.unlock()
    }

    private func writeIfDue() {
        let now = Date()
        lock.lock()
        guard now.timeIntervalSince(lastWrite) >= writeInterval else { lock.unlock(); return }
        lastWrite = now
        let snapshot: [String: Any] = [
            "totalQueries": totalQueries,
            "blockedAlwaysOn": blockedAlwaysOn,
            "blockedSocial": blockedSocial,
            "safeSearchRewrites": safeSearchRewrites,
            "updatedAt": ISO8601DateFormatter().string(from: now),
        ]
        lock.unlock()
        guard let data = try? JSONSerialization.data(withJSONObject: snapshot, options: [.prettyPrinted]) else { return }
        try? data.write(to: URL(fileURLWithPath: Config.statsFile), options: .atomic)
    }
}
