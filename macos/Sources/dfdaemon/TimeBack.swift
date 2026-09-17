import Foundation

/// Per-category "time back" and "data back" counters — both incremented
/// directly the moment a block happens, not computed from raw counts. Same
/// spirit as the stats card's estimates: illustrative, not measured.
final class TimeBack {
    private struct Category {
        var minutes: Double = 0
        var mb: Double = 0
    }

    private let lock = NSLock()
    private var adult = Category()
    private var social = Category()
    private var youtube = Category()
    private var other = Category()
    private var lastWrite = Date.distantPast
    private let writeInterval: TimeInterval = 10

    init() {
        load()
    }

    func recordAdultBlock() {
        lock.lock(); adult.minutes += Config.adultMinutesPerBlock; adult.mb += Config.adultMBPerBlock; lock.unlock()
        writeIfDue()
    }
    func recordSocialBlock() {
        lock.lock(); social.minutes += Config.socialMinutesPerBlock; social.mb += Config.socialMBPerBlock; lock.unlock()
        writeIfDue()
    }
    func recordYoutubeBlock() {
        lock.lock(); youtube.minutes += Config.youtubeMinutesPerBlock; youtube.mb += Config.youtubeMBPerBlock; lock.unlock()
        writeIfDue()
    }
    func recordOtherBlock() {
        lock.lock(); other.minutes += Config.otherMinutesPerBlock; other.mb += Config.otherMBPerBlock; lock.unlock()
        writeIfDue()
    }

    private func load() {
        guard let data = FileManager.default.contents(atPath: Config.timeBackFile),
              let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return }
        adult.minutes = obj["adultMinutes"] as? Double ?? 0
        adult.mb = obj["adultMB"] as? Double ?? 0
        social.minutes = obj["socialMinutes"] as? Double ?? 0
        social.mb = obj["socialMB"] as? Double ?? 0
        youtube.minutes = obj["youtubeMinutes"] as? Double ?? 0
        youtube.mb = obj["youtubeMB"] as? Double ?? 0
        other.minutes = obj["otherMinutes"] as? Double ?? 0
        other.mb = obj["otherMB"] as? Double ?? 0
    }

    private func writeIfDue() {
        let now = Date()
        lock.lock()
        guard now.timeIntervalSince(lastWrite) >= writeInterval else { lock.unlock(); return }
        lastWrite = now
        let snapshot: [String: Any] = [
            "adultMinutes": adult.minutes, "adultMB": adult.mb,
            "socialMinutes": social.minutes, "socialMB": social.mb,
            "youtubeMinutes": youtube.minutes, "youtubeMB": youtube.mb,
            "otherMinutes": other.minutes, "otherMB": other.mb,
        ]
        lock.unlock()
        guard let data = try? JSONSerialization.data(withJSONObject: snapshot, options: [.prettyPrinted]) else { return }
        try? data.write(to: URL(fileURLWithPath: Config.timeBackFile), options: .atomic)
    }
}
