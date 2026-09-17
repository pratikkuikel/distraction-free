# distraction-free

Local-only, open-source content blocker for macOS + Android. No backend, no accounts, no sync.
Blocks adult content, gambling, piracy, and other distraction categories at the network level, always-on,
with an uninstall path that's deliberately slow instead of deliberately impossible.

## Design principles

- **Local only.** No server, no account, no telemetry leaving the device.
- **System-level, not browser-level.** A Network Extension (macOS) / VpnService (Android) filters DNS
  for every app, not just a browser with an extension installed.
- **Always-on.** No schedule window in v1 — categories are blocked continuously.
- **Friction, not fantasy.** You have root/admin on your own device, so nothing here claims to be
  tamper-proof against yourself. Removal works, but requires a deliberate multi-step cooldown
  (see `docs/uninstall-friction.md`) instead of a single click, so a 2am impulse can't undo it.
- **Minimal data.** Category-match counts only, never visited URLs or browsing history.

## Structure

- `macos/` — Network Extension + LaunchDaemon
- `android/` — VpnService-based local DNS filter
- `docs/` — blocklist sourcing, uninstall-friction design, category list

## Status

Planning stage. See `docs/mvp-plan.md`.
