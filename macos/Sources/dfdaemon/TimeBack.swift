import Foundation

/// Per-category "time back" counters — incremented directly the moment a
/// block happens, not computed from raw counts. Same spirit as the stats
/// card's estimates: illustrative, not measured.
final class TimeBack {
    private let lock = NSLock()
    private var adultMinutes: Double = 0
    private var socialMinutes: Double = 0
    private var youtubeMinutes: Double = 0
    private var otherMinutes: Double = 0
    private var lastWrite = Date.distantPast
    private let writeInterval: TimeInterval = 10

    init() {
        load()
    }

    func recordAdultBlock() { lock.lock(); adultMinutes += Config.adultMinutesPerBlock; lock.unlock(); writeIfDue() }
    func recordSocialBlock() { lock.lock(); socialMinutes += Config.socialMinutesPerBlock; lock.unlock(); writeIfDue() }
    func recordYoutubeBlock() { lock.lock(); youtubeMinutes += Config.youtubeMinutesPerBlock; lock.unlock(); writeIfDue() }
    func recordOtherBlock() { lock.lock(); otherMinutes += Config.otherMinutesPerBlock; lock.unlock(); writeIfDue() }

    private func load() {
        guard let data = FileManager.default.contents(atPath: Config.timeBackFile),
              let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return }
        adultMinutes = obj["adultMinutes"] as? Double ?? 0
        socialMinutes = obj["socialMinutes"] as? Double ?? 0
        youtubeMinutes = obj["youtubeMinutes"] as? Double ?? 0
        otherMinutes = obj["otherMinutes"] as? Double ?? 0
    }

    private func writeIfDue() {
        let now = Date()
        lock.lock()
        guard now.timeIntervalSince(lastWrite) >= writeInterval else { lock.unlock(); return }
        lastWrite = now
        let snapshot: [String: Any] = [
            "adultMinutes": adultMinutes,
            "socialMinutes": socialMinutes,
            "youtubeMinutes": youtubeMinutes,
            "otherMinutes": otherMinutes,
        ]
        lock.unlock()
        guard let data = try? JSONSerialization.data(withJSONObject: snapshot, options: [.prettyPrinted]) else { return }
        try? data.write(to: URL(fileURLWithPath: Config.timeBackFile), options: .atomic)
    }
}
