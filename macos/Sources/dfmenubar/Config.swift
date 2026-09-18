import Foundation

enum Config {
    static let configDir = ProcessInfo.processInfo.environment["DF_CONFIG_DIR"] ?? "/usr/local/etc/distraction-free"
    static let stateDir = ProcessInfo.processInfo.environment["DF_STATE_DIR"] ?? "/usr/local/var/distraction-free"
    static let statsFile = "\(stateDir)/stats.json"
    static let socialToggleFile = "\(stateDir)/social-toggle.json"
    static let streakFile = "\(stateDir)/streak.json"
    static let timeBackFile = "\(stateDir)/timeback.json"
    static let loggingEnabledFile = "\(stateDir)/logging-enabled"
    static let diagnosticLogFile = "\(stateDir)/diagnostic.log"
    static let installDir = ProcessInfo.processInfo.environment["DF_INSTALL_DIR"] ?? "/usr/local/opt/distraction-free"
    static let quotesPath = "\(installDir)/quotes.txt"

    static let disableLogFile: String = {
        let base = NSHomeDirectory() + "/Library/Application Support/distraction-free"
        try? FileManager.default.createDirectory(atPath: base, withIntermediateDirectories: true)
        return base + "/disable-log.json"
    }()

    static let socialBlockStartHour = 20
    static let socialBlockEndHour = 9

    /// Installed by Scripts/install.sh — not the repo checkout path, so the
    /// menubar app works regardless of where (or whether) the repo still
    /// exists on disk.
    static let updateScriptPath = "/usr/local/opt/distraction-free/update-blocklist.sh"
}
