## What does this change?

<!-- One or two sentences. What, and why. -->

## Platform(s) affected

- [ ] macOS
- [ ] Android
- [ ] Both / shared (blocklists, docs, CI)

## How was this tested?

<!--
This project has no automated test suite yet — verification is manual, on a real device. Say what you
actually did, not just "should work". e.g.:
- Built and installed on my own Mac/phone
- Confirmed [specific domain] is blocked/allowed as expected via `dig`/ping or the app UI
- Checked diagnostic log output
- Ran for N minutes/hours without crashing
-->

## Checklist

- [ ] Tested on a real device (not just "it compiles") — the emulator/simulator hides real bugs this
      project has hit before (VPN routing, package-visibility, Gatekeeper)
- [ ] No new persistent background polling/timers without a clear reason (see the battery-profiling
      fixes in the commit history for why this matters)
- [ ] If this touches blocklists/domain matching: confirmed both a domain that should be blocked and
      one that shouldn't, not just one side
- [ ] Docs updated if behavior visible to users changed (README, docs/privacy.md, docs/troubleshooting.md)
