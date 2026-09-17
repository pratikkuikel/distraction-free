import Foundation

/// Decides whether the social-media blocklist is active right now:
/// hard-blocked 20:00-09:00 daily (no override), otherwise driven by a
/// manual toggle file the unprivileged menubar app writes.
final class ScheduleManager {
    private var manualBlock = false
    private var lastToggleCheck = Date.distantPast
    private let toggleCheckInterval: TimeInterval = 2 // cheap stat(), fine to poll often

    func isSocialBlockedNow(now: Date = Date()) -> Bool {
        if isInForcedWindow(now: now) { return true }
        refreshToggleIfNeeded()
        return manualBlock
    }

    func isInForcedWindow(now: Date = Date()) -> Bool {
        let hour = Calendar.current.component(.hour, from: now)
        // 20:00-23:59 or 00:00-08:59
        return hour >= Config.socialBlockStartHour || hour < Config.socialBlockEndHour
    }

    private func refreshToggleIfNeeded() {
        let now = Date()
        guard now.timeIntervalSince(lastToggleCheck) >= toggleCheckInterval else { return }
        lastToggleCheck = now
        guard let data = FileManager.default.contents(atPath: Config.socialToggleFile),
              let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            return
        }
        manualBlock = (obj["manualBlock"] as? Bool) ?? false
    }
}
