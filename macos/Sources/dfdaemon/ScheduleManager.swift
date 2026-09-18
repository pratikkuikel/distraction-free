import Foundation

/// Decides whether the social-media blocklist is active right now:
/// hard-blocked 20:00-09:00 daily (no override), otherwise driven by a
/// manual toggle file the unprivileged menubar app writes.
final class ScheduleManager {
    // Queries are handled concurrently (see DNSServer.workQueue), so this
    // small bit of mutable state needs its own lock.
    private let lock = NSLock()
    private var manualBlock = false
    private var lastToggleCheck = Date.distantPast
    private let toggleCheckInterval: TimeInterval = 2 // cheap stat(), fine to poll often

    func isSocialBlockedNow(now: Date = Date()) -> Bool {
        if isInForcedWindow(now: now) { return true }
        refreshToggleIfNeeded()
        lock.lock(); defer { lock.unlock() }
        return manualBlock
    }

    func isInForcedWindow(now: Date = Date()) -> Bool {
        let hour = Calendar.current.component(.hour, from: now)
        // 20:00-23:59 or 00:00-08:59
        return hour >= Config.socialBlockStartHour || hour < Config.socialBlockEndHour
    }

    private func refreshToggleIfNeeded() {
        let now = Date()
        lock.lock()
        guard now.timeIntervalSince(lastToggleCheck) >= toggleCheckInterval else { lock.unlock(); return }
        lastToggleCheck = now
        lock.unlock()

        guard let data = FileManager.default.contents(atPath: Config.socialToggleFile),
              let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            return
        }
        let newValue = (obj["manualBlock"] as? Bool) ?? false
        lock.lock()
        let changed = newValue != manualBlock
        manualBlock = newValue
        lock.unlock()

        // Our NXDOMAIN responses carry no negative-cache TTL (we're a
        // minimal DNS server, no SOA record), so macOS's own resolver falls
        // back to its own default caching duration — flipping the toggle
        // off doesn't necessarily un-block a domain immediately from the
        // user's point of view, it just looks like nothing happened for a
        // bit. The daemon is already root, so it can just flush the
        // resolver cache itself the moment the toggle actually changes,
        // instead of waiting for that cache to expire on its own.
        if changed { flushSystemDNSCache() }
    }

    private func flushSystemDNSCache() {
        let task = Process()
        task.launchPath = "/usr/bin/killall"
        task.arguments = ["-HUP", "mDNSResponder"]
        try? task.run()
    }
}
