# Contributing

This is a personal project (originally built to solve a personal problem) that's open source so
anyone in the same position can use or improve it. Contributions are welcome, but a few things are
worth knowing before you dive in.

## Before you start

- **For anything non-trivial, open an issue first.** Saves everyone the disappointment of a big PR
  going a direction that doesn't fit the project.
- **No automated test suite exists yet.** All verification so far has been manual, on real hardware —
  see the [PR template](.github/PULL_REQUEST_TEMPLATE.md) checklist for what "tested" means here.
  Adding real test coverage is itself a very welcome contribution.
- **The emulator/simulator lies.** Several real bugs in this project's history (VPN routing on
  Android, Android 11+ package-visibility rules, macOS Gatekeeper behavior for LaunchDaemons) only
  showed up on a real device, never in the emulator/simulator. Test on real hardware before opening a
  PR — see `README.md`'s Installation section, or build from source below.

## Building from source

**macOS** (needs Xcode Command Line Tools, no Apple Developer account required):

```bash
cd macos && ./Scripts/install.sh
```

Builds in release mode and installs the LaunchDaemon + menubar app locally. Re-run after making
changes; it's idempotent.

**Android** (needs Android Studio or just the SDK/JDK 17):

```bash
cd android && ./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Project structure

- `macos/Sources/dfdaemon/` — the DNS resolver (Swift, raw sockets, no dependencies)
- `macos/Sources/dfmenubar/` — the SwiftUI menu-bar UI; talks to the daemon only through shared JSON
  state files in `/usr/local/var/distraction-free/`, never directly (keeps it unprivileged)
- `android/app/src/main/java/com/distractionfree/app/` — the VpnService-based filter + TCP/UDP NAT
  relay (Kotlin)
- `shared/` — the hand-maintained social-media domain list and motivational quotes, used by both
  platforms (keep `android/app/src/main/assets/social-domains.txt` in sync manually — it's a bundled
  copy, not a symlink, since Android assets can't reference outside the module)
- `docs/` — scope, privacy model, troubleshooting

## Code style / conventions

Nothing formal, but the existing code follows a few patterns worth matching:

- **Comments explain *why*, not *what*.** If you're tempted to write a comment describing what the
  next line does, the code should probably just be clearer instead. Comments here exist for non-obvious
  constraints, past bugs, and design tradeoffs — e.g. why `googlevideo.com` is a separate blocklist
  entry from `youtube.com`, or why the state directory needs group-write permissions.
  Where relevant, comments name the actual bug/incident that led to the current approach — that context
  is what stops the next person from "fixing" it back into the same bug.
- **Conservative estimates over impressive numbers.** The time/data-back stats are estimates, and were
  deliberately recalibrated down after the original numbers compounded into implausible totals. If
  you're adding a stat, err toward numbers a skeptical user would believe.
- **Fail safe, not clever.** On a fetch/update failure, existing blocklists must never get corrupted
  or wiped — see how `update-blocklist.sh` and `BlocklistUpdater.kt` write to a temp location and only
  replace the real file after a full success. New retry logic should follow this pattern.
- **Mirror macOS/Android intentionally, not automatically.** The two platforms share behavior
  (categories, scheduling, estimates) but not code. When you change one platform's logic in a way that
  should apply to both, update both, but don't force artificial code-sharing across a Swift daemon and
  a Kotlin VPN service just for symmetry.

## Reporting bugs vs. fixing them

If you've found a bug but aren't going to fix it yourself, please still file it — see the
[bug report template](.github/ISSUE_TEMPLATE/bug_report.yml). Turning on **Diagnostic logging** in the
app and attaching the exported log to the issue (Settings/menu → Export log) makes a real difference:
it's the difference between "X is broken" and being able to see exactly which domain got blocked or
allowed and when.

## Privacy

Don't add anything that changes what's logged/tracked without updating `docs/privacy.md` first — the
whole point of this project is that nothing leaves the device, and that's a promise to the people
using it, not just a design detail.
