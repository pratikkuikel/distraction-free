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
        lock.lock()
        manualBlock = (obj["manualBlock"] as? Bool) ?? false
        lock.unlock()
    }
}
