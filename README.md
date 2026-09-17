# distraction-free

Local-only, open-source content blocker for macOS + Android. No backend, no accounts, no sync.
Blocks adult content, gambling, piracy, and VPN/proxy/DNS-bypass at the DNS level, always-on;
social media is blocked nightly (8pm–9am) with a manual daytime toggle; communication apps
(WhatsApp, Messenger, Telegram, Botim) are always exempt. Uninstall is deliberately slow, not
deliberately impossible.

## How it works

**macOS** — `dfdaemon` is a small local DNS resolver (Swift, raw UDP sockets, a reversed-label trie
for O(number of labels) domain lookups regardless of blocklist size) bound to `127.0.0.1:53`,
installed as a root LaunchDaemon. The Mac's system DNS is pointed at it. Blocked domains get
NXDOMAIN; SafeSearch domains get rewritten to their forced-safe-search IP (resolved live, not
hardcoded, since some providers' addresses rotate); everything else is forwarded to whatever DNS
servers your network already had configured. `dfmenubar` is a SwiftUI menu-bar app showing status
and the disable/toggle controls — it talks to the daemon only through shared JSON state files, never
directly, so it can stay fully unprivileged.

**Android** — `BlockerVpnService` is a full-capture local VPN (Kotlin). It has to capture *all*
traffic, not just DNS — Android gives a captured app no fallback route to the real network for
anything the VPN doesn't explicitly route, so a DNS-only VPN would silently kill the rest of the
device's connectivity. DNS (UDP/53) gets the block/allow/SafeSearch logic; everything else is
relayed transparently through a minimal userspace TCP/UDP NAT (`TcpNat.kt` / `UdpNat.kt`) so normal
browsing keeps working. WhatsApp/Messenger/Telegram/Botim are excluded from the VPN entirely via
`addDisallowedApplication`.

Both platforms load the same category rules from the same blocklist sources — HaGeZi's hosted lists
for adult/gambling/piracy/bypass, and this repo's own hand-maintained `shared/social-domains.txt`
for social media (needs per-platform precision a generic list can't give: block Instagram's feed,
never block WhatsApp).

## Design principles

- **Local only.** No server, no account, no telemetry leaving the device.
- **DNS-level, not browser-level.** Filters every app on the device, not just a browser extension.
- **Category-specific scheduling.** Adult/gambling/piracy/bypass/SafeSearch: always-on, no toggle.
  Social media: hard-blocked 8pm–9am, open the rest of the day with a manual "block now" toggle.
- **Friction, not fantasy.** You have root/admin on your own device, so nothing here claims to be
  tamper-proof against yourself. The in-app "disable" button logs the attempt and shows a 24h
  countdown that never actually completes, by design — the only real way off is outside the app
  (see `docs/troubleshooting.md#uninstalling`).
- **Minimal data.** Category-match counts only, never visited URLs or browsing history — see
  `docs/privacy.md`.

## Structure

- `macos/` — `dfdaemon` (the DNS resolver), `dfmenubar` (SwiftUI status/control UI), LaunchDaemon
  plist, install + blocklist-update scripts
- `android/` — VpnService-based filter + TCP/UDP relay, sideloaded APK (no Play Store)
- `shared/` — the hand-maintained social-media domain list both platforms use
- `docs/` — MVP scope, privacy, troubleshooting

## Download

Prebuilt installers are attached to each [GitHub Release](../../releases) — no Xcode or Android
Studio required:

- **macOS:** download `distraction-free-macos.zip`, unzip it, then run `./install-release.sh` from
  inside the extracted folder (asks for your password once, to install the LaunchDaemon and point
  your Mac's DNS at it). Since the binaries aren't notarized (no Apple Developer account), the script
  clears the Gatekeeper quarantine flag itself.
- **Android:** download `distraction-free-android.apk`, enable "Install unknown apps" for your
  browser/file manager, and install it. Grant the VPN permission when prompted, and — for real
  bypass-resistance — enable **Settings → VPN → Always-on VPN + Block connections without VPN** for it
  manually. Every release is signed with the same key, so installing a new version over an old one
  updates in place and keeps your streak/stats data; only switching between a locally-built debug APK
  and a signed release build requires a one-time uninstall.

New releases build automatically from a git tag (`vX.Y.Z`) via
[`.github/workflows/release.yml`](.github/workflows/release.yml).

## Setup from source

**macOS:** `cd macos && ./Scripts/install.sh` (builds in release mode, asks for your password once
to install the LaunchDaemon and point your Mac's DNS at it).

**Android:** open `android/` in Android Studio, build and install the debug APK, grant the VPN
permission when prompted, and — for real bypass-resistance — enable **Settings → VPN → Always-on VPN
+ Block connections without VPN** for it manually.

## Status

MVP built and smoke-tested: DNS blocking, subdomain matching, forwarding, and SafeSearch rewriting
are verified working on both platforms. Full page-load testing on Android was constrained by the
dev sandbox's emulator networking (see commit history / session notes) rather than the app itself —
worth a real-device pass before relying on it daily.

## Uninstall

The in-app "Disable protection" button is deliberately a fake 24h countdown that never completes —
that's the whole point. There's no in-app uninstall. To actually remove it:

**macOS** (run each line in Terminal; the `sudo` ones will prompt for your password):

```bash
sudo launchctl bootout system/com.distractionfree.daemon 2>/dev/null
sudo launchctl bootout system/com.distractionfree.blocklist-update 2>/dev/null
launchctl bootout gui/$(id -u)/com.distractionfree.menubar 2>/dev/null
sudo networksetup -setdnsservers Wi-Fi empty   # replace Wi-Fi with your active network service
sudo rm -f /Library/LaunchDaemons/com.distractionfree.daemon.plist
sudo rm -f /Library/LaunchDaemons/com.distractionfree.blocklist-update.plist
rm -f ~/Library/LaunchAgents/com.distractionfree.menubar.plist
sudo rm -rf /usr/local/opt/distraction-free /usr/local/etc/distraction-free /usr/local/var/distraction-free
sudo rm -f /usr/local/bin/dfmenubar
```

**Android:** Settings → Apps → Distraction Free → Uninstall. (Or, to keep the app but stop the VPN
immediately instead of waiting on the fake countdown, force-stop it from the same screen — a genuine
OS-level override, same as macOS root access, that this app can't and shouldn't try to prevent.)
