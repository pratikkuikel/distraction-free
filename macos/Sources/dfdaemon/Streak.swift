import Foundation

/// Tracks consecutive days the daemon has been active. Cheap to check on
/// every query (a date comparison), only writes when the day actually
/// changes. Disable attempts reset the streak — see DFMenubarApp's
/// AppState.recordDisableAttempt(), which writes currentStreak = 0 here
/// directly (the daemon and menubar app share this file the same way they
/// already share stats.json and social-toggle.json).
final class Streak {
    private let lock = NSLock()
    private static let dayFormatter: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd"
        f.timeZone = .current
        return f
    }()

    /// Call once per handled query (or at startup) — cheap no-op unless the
    /// calendar day has changed since the last recorded active day.
    func touch(now: Date = Date()) {
        lock.lock(); defer { lock.unlock() }

        let today = Self.dayFormatter.string(from: now)
        var state = readLocked()

        if state.lastActiveDate == today {
            return // already recorded today
        }

        if let last = state.lastActiveDate,
           let lastDate = Self.dayFormatter.date(from: last),
           let daysBetween = Calendar.current.dateComponents([.day], from: lastDate, to: now).day,
           daysBetween == 1 {
            state.currentStreak += 1
        } else {
            state.currentStreak = 1 // first run ever, or a day was missed
        }
        state.bestStreak = max(state.bestStreak, state.currentStreak)
        state.lastActiveDate = today
        writeLocked(state)
    }

    private struct State {
        var currentStreak: Int = 0
        var bestStreak: Int = 0
        var lastActiveDate: String?
    }

    private func readLocked() -> State {
        guard let data = FileManager.default.contents(atPath: Config.streakFile),
              let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            return State()
        }
        var s = State()
        s.currentStreak = obj["currentStreak"] as? Int ?? 0
        s.bestStreak = obj["bestStreak"] as? Int ?? 0
        s.lastActiveDate = obj["lastActiveDate"] as? String
        return s
    }

    private func writeLocked(_ state: State) {
        var obj: [String: Any] = [
            "currentStreak": state.currentStreak,
            "bestStreak": state.bestStreak,
        ]
        if let d = state.lastActiveDate { obj["lastActiveDate"] = d }
        guard let data = try? JSONSerialization.data(withJSONObject: obj, options: [.prettyPrinted]) else { return }
        try? data.write(to: URL(fileURLWithPath: Config.streakFile), options: .atomic)
    }
}
