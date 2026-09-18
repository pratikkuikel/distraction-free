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

    @Published var currentStreak = 0
    @Published var bestStreak = 0

    @Published var timeBackMinutes = 0
    @Published var dataBackMB: Double = 0
    @Published var todayQuote = ""

    private var timer: Timer?
    private static let quotes: [String] = {
        (try? String(contentsOfFile: Config.quotesPath, encoding: .utf8))?
            .split(separator: "\n").map(String.init).filter { !$0.isEmpty } ?? []
    }()

    var totalBlocked: Int { blockedAlwaysOn + blockedSocial }

    init() {
        pickTodayQuote()
        refresh()
    }

    // Polling only runs while the dropdown is actually visible (driven by
    // onAppear/onDisappear in MenubarContentView) — an unconditional 5s
    // timer previously ran the whole time the app was open, which for a
    // background menu-bar utility means 24/7, re-reading half a dozen
    // small JSON files every 5 seconds for a UI almost never on screen.
    func startPolling() {
        guard timer == nil else { return }
        refresh()
        timer = Timer.scheduledTimer(withTimeInterval: 5, repeats: true) { [weak self] _ in
            self?.refresh()
        }
    }

    func stopPolling() {
        timer?.invalidate()
        timer = nil
    }

    private func pickTodayQuote() {
        guard !Self.quotes.isEmpty else { return }
        let dayOfYear = Calendar.current.ordinality(of: .day, in: .year, for: Date()) ?? 0
        todayQuote = Self.quotes[dayOfYear % Self.quotes.count]
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
        loadStreak()
        loadTimeBack()
    }

    private func loadTimeBack() {
        guard let data = FileManager.default.contents(atPath: Config.timeBackFile),
              let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return }
        let adultMin = obj["adultMinutes"] as? Double ?? 0
        let socialMin = obj["socialMinutes"] as? Double ?? 0
        let youtubeMin = obj["youtubeMinutes"] as? Double ?? 0
        let otherMin = obj["otherMinutes"] as? Double ?? 0
        timeBackMinutes = Int(adultMin + socialMin + youtubeMin + otherMin)

        let adultMB = obj["adultMB"] as? Double ?? 0
        let socialMB = obj["socialMB"] as? Double ?? 0
        let youtubeMB = obj["youtubeMB"] as? Double ?? 0
        let otherMB = obj["otherMB"] as? Double ?? 0
        dataBackMB = adultMB + socialMB + youtubeMB + otherMB
    }

    private func loadStreak() {
        guard let data = FileManager.default.contents(atPath: Config.streakFile),
              let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return }
        currentStreak = obj["currentStreak"] as? Int ?? 0
        bestStreak = obj["bestStreak"] as? Int ?? 0
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
        resetStreak() // trying to disable resets your streak — the daemon never actually stops
    }

    private func resetStreak() {
        var obj: [String: Any] = [:]
        if let data = FileManager.default.contents(atPath: Config.streakFile),
           let existing = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
            obj = existing
        }
        obj["currentStreak"] = 0
        guard let data = try? JSONSerialization.data(withJSONObject: obj, options: [.prettyPrinted]) else { return }
        try? FileManager.default.createDirectory(atPath: Config.stateDir, withIntermediateDirectories: true)
        try? data.write(to: URL(fileURLWithPath: Config.streakFile), options: .atomic)
        currentStreak = 0
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
