import Foundation

enum Config {
    /// Blocklists live here, written by Scripts/update-blocklist.sh (run with
    /// sudo). Root-owned, world-readable. Overridable via DF_CONFIG_DIR for
    /// local testing without root.
    static let configDir = ProcessInfo.processInfo.environment["DF_CONFIG_DIR"] ?? "/usr/local/etc/distraction-free"
    static let alwaysOnList = "\(configDir)/always-on.txt"
    static let socialList = "\(configDir)/social.txt"
    /// Adult-only subset of always-on.txt — used only to tell "adult" blocks
    /// apart from gambling/piracy/bypass for the time-back estimate; the
    /// actual block decision still uses the single merged alwaysOnTrie.
    static let adultList = "\(configDir)/adult.txt"
    /// Not a file — small and stable enough to hardcode; used the same way
    /// as adultList, to tell YouTube apart from other social blocks.
    static let youtubeDomains = ["youtube.com", "youtube-nocookie.com", "ytimg.com", "youtu.be"]

    /// Runtime state the daemon writes (stats) and reads (manual toggle).
    /// Root-owned, world-readable/writable so the unprivileged menubar app
    /// can flip the toggle without needing its own admin prompt. Overridable
    /// via DF_STATE_DIR for local testing without root.
    static let stateDir = ProcessInfo.processInfo.environment["DF_STATE_DIR"] ?? "/usr/local/var/distraction-free"
    static let statsFile = "\(stateDir)/stats.json"
    static let socialToggleFile = "\(stateDir)/social-toggle.json"
    static let streakFile = "\(stateDir)/streak.json"
    static let timeBackFile = "\(stateDir)/timeback.json"
    static let installDir = ProcessInfo.processInfo.environment["DF_INSTALL_DIR"] ?? "/usr/local/opt/distraction-free"
    static let quotesPath = "\(installDir)/quotes.txt"

    // Rough, clearly-labeled per-category estimates for the "time back" /
    // "data back" stats — same spirit as Brave/uBlock's own bandwidth/time-
    // saved numbers: illustrative, not measured. Deliberately conservative:
    // one blocked DNS query is one attempted connection, not a whole
    // session, so this is priced close to a single blocked request (~1 MB,
    // ~10s) rather than an assumed browsing session — the old numbers (up
    // to 60 min / 150 MB per block) compounded into implausible totals
    // (weeks of "time back" after a day of normal use) that undermined the
    // stat's credibility. Still a flat estimate *per blocked attempt*,
    // credited the moment the block happens, not derived from raw counts.
    static let adultMinutesPerBlock: Double = 0.2   // 12s
    static let socialMinutesPerBlock: Double = 0.2  // 12s
    static let youtubeMinutesPerBlock: Double = 0.3 // 18s — video preview/thumbnail fetch is a bit heavier
    static let otherMinutesPerBlock: Double = 0.1   // 6s — gambling/piracy/bypass attempts are typically just a page load

    // Same conservative logic for data: video-heavy categories still cost
    // a bit more per attempt than a blocked gambling/piracy request, but
    // nowhere near a full session's worth.
    static let adultMBPerBlock: Double = 1.0
    static let socialMBPerBlock: Double = 1.0
    static let youtubeMBPerBlock: Double = 2.0
    static let otherMBPerBlock: Double = 0.5

    static let upstreamDNS = "1.1.1.1"
    static let upstreamPort: UInt16 = 53
    static let listenPort: UInt16 = ProcessInfo.processInfo.environment["DF_LISTEN_PORT"].flatMap { UInt16($0) } ?? 53
    /// 127.0.0.2, not 127.0.0.1 — avoids fighting any other local resolver
    /// already bound to the "default" loopback address (e.g. dnsmasq from
    /// Herd/Valet-style local dev setups using 127.0.0.1:53 for *.test).
    /// Any 127.0.0.0/8 address is loopback, so this needs no extra setup.
    static let listenAddress: String = ProcessInfo.processInfo.environment["DF_LISTEN_ADDRESS"] ?? "127.0.0.2"

    // Social media schedule: hard-blocked 20:00-09:00 daily, open the rest
    // with an optional manual "block now" toggle 09:00-20:00.
    static let socialBlockStartHour = 20
    static let socialBlockEndHour = 9

    // SafeSearch: queries for these hostnames get answered with the current
    // A record of the mapped forced-safe-search hostname instead of being
    // forwarded normally. Resolved live (and cached briefly) rather than
    // hardcoded, since forcesafesearch.google.com is a stable IP but
    // strict.bing.com is CDN-backed (Azure Front Door) and rotates —
    // verified by live lookup during implementation:
    //   dig +short forcesafesearch.google.com -> 216.239.38.120 (stable)
    //   dig +short strict.bing.com             -> CDN IP, changes over time
    static let safeSearchTargets: [String: String] = [
        "www.google.com": "forcesafesearch.google.com",
        "google.com": "forcesafesearch.google.com",
        "www.bing.com": "strict.bing.com",
        "bing.com": "strict.bing.com",
        "www.youtube.com": "restrict.youtube.com",
        "youtube.com": "restrict.youtube.com",
        "m.youtube.com": "restrict.youtube.com",
    ]
    static let safeSearchCacheTTL: TimeInterval = 300
}
