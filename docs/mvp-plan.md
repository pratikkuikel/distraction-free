# MVP Plan

## Scope (v1)

**In:**
- macOS: Network Extension (DNS proxy or content filter) blocking by category, always-on
- macOS: LaunchDaemon so it starts at boot and survives logout, requires admin to stop
- Android: VpnService-based local DNS filter, always-on, foreground service, restarts on boot
- Shared blocklist source: HaGeZi (adult, gambling, piracy/torrent, major streaming, VPN/proxy/Tor/altDNS)
- SafeSearch enforcement (DNS rewrite for Google/Bing/DuckDuckGo/YouTube)
- Local blocked-page / NXDOMAIN response, no full page analysis
- Uninstall-friction mechanism (see docs/uninstall-friction.md) instead of tamper-detection
- Manual blocklist update (re-run a script to pull latest HaGeZi list)

**Out (v1):**
- Any backend, account, or cross-device sync — config is per-device, copied by hand if wanted
- Streak tracking / gamification
- Scheduling (always-on instead)
- False-positive reporting UI (a local override file is enough for now)
- Tamper detection beyond uninstall friction
- iOS (Android + macOS only — no iOS Network Extension entitlement process for now)

## Build order

1. Validate HaGeZi list licensing + pick the specific list files (adult, gambling, piracy, streaming, proxy/vpn/tor)
2. macOS proof of concept: DNS-based blocking via a local resolver (e.g. a lightweight DNS proxy bound as
   the system resolver) OR NEFilterDataProvider content filter — pick based on what blocks all apps
   without requiring a paid Apple developer Network Extension entitlement for personal use
3. macOS LaunchDaemon packaging + SafeSearch DNS rewrites
4. macOS uninstall-friction mechanism
5. Android VpnService proof of concept with the same blocklist
6. Android boot-restart + foreground service hardening
7. Test: reboot, sleep/wake, network change, clock change, VPN-over-VPN conflicts
8. Write docs/troubleshooting.md and docs/privacy.md
