import Foundation

/// Opt-in diagnostic logging: off by default. Category-match counts are all
/// stats.json ever needed for the app to work, but debugging a specific
/// report ("X is wrongly blocked", "Y isn't blocked") needs to see actual
/// domain-level decisions — this exists only for that, only when the user
/// turns it on themselves, and never leaves the device automatically. See
/// docs/privacy.md.
final class DiagnosticLog {
    // Checking a file's mtime is cheap (a stat() call) — fine to do on every
    // query rather than caching, since toggling logging should take effect
    // immediately without a daemon restart.
    private static let maxBytes = 5 * 1024 * 1024 // rotate at 5MB — bounded, not unbounded growth
    private let lock = NSLock()
    private var lastEnabledCheck = Date.distantPast
    private var enabledCache = false
    private let checkInterval: TimeInterval = 2

    func log(_ line: String) {
        guard isEnabled() else { return }
        let timestamp = ISO8601DateFormatter().string(from: Date())
        let entry = "[\(timestamp)] \(line)\n"
        lock.lock()
        defer { lock.unlock() }
        rotateIfNeeded()
        if let data = entry.data(using: .utf8) {
            if let handle = FileHandle(forWritingAtPath: Config.diagnosticLogFile) {
                handle.seekToEndOfFile()
                handle.write(data)
                try? handle.close()
            } else {
                FileManager.default.createFile(atPath: Config.diagnosticLogFile, contents: data)
                try? FileManager.default.setAttributes([.posixPermissions: 0o644], ofItemAtPath: Config.diagnosticLogFile)
            }
        }
    }

    private func rotateIfNeeded() {
        guard let attrs = try? FileManager.default.attributesOfItem(atPath: Config.diagnosticLogFile),
              let size = attrs[.size] as? Int, size > Self.maxBytes else { return }
        try? FileManager.default.removeItem(atPath: Config.diagnosticLogFile)
    }

    private func isEnabled() -> Bool {
        let now = Date()
        lock.lock()
        if now.timeIntervalSince(lastEnabledCheck) < checkInterval {
            let cached = enabledCache
            lock.unlock()
            return cached
        }
        lock.unlock()

        let enabled = FileManager.default.fileExists(atPath: Config.loggingEnabledFile)
        lock.lock()
        enabledCache = enabled
        lastEnabledCheck = now
        lock.unlock()
        return enabled
    }
}
