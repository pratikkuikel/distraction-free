# Privacy

## What's collected

Nothing leaves either device. There is no backend, no account, no analytics SDK, no crash
reporter phoning home. Everything below lives only in local files on the Mac or the phone.

**macOS** (`/usr/local/var/distraction-free/stats.json`, root-owned, world-readable):
- Total DNS queries handled (a count, not a list)
- Count blocked by the always-on categories (adult/gambling/piracy/bypass)
- Count blocked by the social-media schedule
- Count of SafeSearch rewrites
- A timestamp of the last update

**Android** (`<app-private-storage>/state/stats.json`, only readable by the app itself):
- Same four counts as above

**Disable-attempt log** (macOS: `~/Library/Application Support/distraction-free/disable-log.json`;
Android: app-private storage): a timestamp per "disable protection" click. Nothing else — no reason
text is required, no location, no device info.

**Never collected:** the domains you actually visited, browsing history, search terms, IP
addresses of sites you reached, or anything that could reconstruct what you looked at. The DNS
daemon/VPN service decides block-or-allow per query and immediately discards the query itself,
incrementing only a counter.

## What the blocklists cost you

Domain lists (HaGeZi's hosted lists, and this project's own hand-written social-media list) are
fetched over plain HTTPS from `cdn.jsdelivr.net` (a public CDN mirroring the HaGeZi GitHub repo).
That fetch reveals to jsDelivr/HaGeZi's CDN that a device requested their blocklist files — the
same as visiting any public webpage. No query parameters, device identifiers, or account
information are attached to that request.

## SafeSearch and forwarded DNS queries

Non-blocked DNS queries are forwarded to an upstream resolver — on macOS, whichever DNS servers
your network already had configured before installation (captured once at daemon/VPN start); on
Android, the network's own configured DNS servers, discovered the same way. This project doesn't
introduce a new third party into your DNS path — it filters locally, then defers to whatever
resolver you were already trusting.

## Why category counts instead of full logs

A false-positive report or a "why was this blocked" debugging session doesn't need a URL history —
a count of "blocked by category X, N times today" is enough to know the tool is working, and
deliberately isn't enough to reconstruct browsing activity, including from the developer/user
themselves reviewing their own device later.
