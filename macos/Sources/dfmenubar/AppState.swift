import Foundation
import Combine

final class AppState: ObservableObject {
    @Published var totalQueries = 0
    @Published var blockedAlwaysOn = 0
    @Published var blockedSocial = 0
    @Published var safeSearchRewrites = 0
    @Published var statsUpdatedAt: Date?

    @Published var disableAttemptCount = 0
    @Published var disableHoursRemaining: Int?

    @Published var socialManualBlock = false

    private var timer: Timer?

    init() {
        refresh()
        timer = Timer.scheduledTimer(withTimeInterval: 5, repeats: true) { [weak self] _ in
            self?.refresh()
        }
    }

    var isInForcedSocialWindow: Bool {
        let hour = Calendar.current.component(.hour, from: Date())
        return hour >= Config.socialBlockStartHour || hour < Config.socialBlockEndHour
    }

    var isSocialBlockedNow: Bool {
        isInForcedSocialWindow || socialManualBlock
    }

    func refresh() {
        loadStats()
        loadDisableLog()
        loadSocialToggle()
    }

    private func loadStats() {
        guard let data = FileManager.default.contents(atPath: Config.statsFile),
              let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return }
        totalQueries = obj["totalQueries"] as? Int ?? 0
        blockedAlwaysOn = obj["blockedAlwaysOn"] as? Int ?? 0
        blockedSocial = obj["blockedSocial"] as? Int ?? 0
        safeSearchRewrites = obj["safeSearchRewrites"] as? Int ?? 0
        if let s = obj["updatedAt"] as? String {
            statsUpdatedAt = ISO8601DateFormatter().date(from: s)
        }
    }

    private func loadDisableLog() {
        guard let data = FileManager.default.contents(atPath: Config.disableLogFile),
              let arr = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]] else {
            disableAttemptCount = 0
            disableHoursRemaining = nil
            return
        }
        disableAttemptCount = arr.count
        guard let last = arr.last, let ts = last["timestamp"] as? Double else {
            disableHoursRemaining = nil
            return
        }
        let lastDate = Date(timeIntervalSince1970: ts / 1000)
        let elapsedHours = Int(Date().timeIntervalSince(lastDate) / 3600)
        disableHoursRemaining = max(0, 24 - elapsedHours)
    }

    private func loadSocialToggle() {
        guard let data = FileManager.default.contents(atPath: Config.socialToggleFile),
              let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return }
        socialManualBlock = obj["manualBlock"] as? Bool ?? false
    }

    func recordDisableAttempt() {
        var arr: [[String: Any]] = []
        if let data = FileManager.default.contents(atPath: Config.disableLogFile),
           let existing = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]] {
            arr = existing
        }
        arr.append(["timestamp": Date().timeIntervalSince1970 * 1000])
        if let data = try? JSONSerialization.data(withJSONObject: arr, options: [.prettyPrinted]) {
            try? data.write(to: URL(fileURLWithPath: Config.disableLogFile))
        }
        loadDisableLog()
    }

    func setSocialManualBlock(_ blocked: Bool) {
        let obj: [String: Any] = ["manualBlock": blocked, "updatedAt": Date().timeIntervalSince1970 * 1000]
        guard let data = try? JSONSerialization.data(withJSONObject: obj) else { return }
        try? FileManager.default.createDirectory(atPath: Config.stateDir, withIntermediateDirectories: true)
        try? data.write(to: URL(fileURLWithPath: Config.socialToggleFile))
        socialManualBlock = blocked
    }

    func runUpdateScript() {
        let task = Process()
        task.launchPath = "/usr/bin/osascript"
        task.arguments = ["-e", "do shell script \"\(Config.updateScriptPath)\" with administrator privileges"]
        try? task.run()
    }
}
