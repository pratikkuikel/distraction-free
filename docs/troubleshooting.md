# Troubleshooting

## macOS

**Nothing is being blocked / `dig pornhub.com` resolves normally**
1. Check the daemon is running: `sudo launchctl print system/com.distractionfree.daemon` — look for
   `state = running`.
2. Check your Mac's DNS is actually pointed at the daemon: `networksetup -getdnsservers Wi-Fi`
   should show `127.0.0.2`. If it shows something else (a network change, a VPN app, or manually
   editing Network settings can reset this), re-run `Scripts/install.sh` or manually:
   `sudo networksetup -setdnsservers Wi-Fi 127.0.0.2`.
3. Check the daemon's log: `cat /usr/local/var/distraction-free/daemon.log` — a `bind() failed`
   line means something else is already using port 53, or it's not running as root.

**A site is wrongly blocked (false positive)**
The blocklists are plain text files — open `/usr/local/etc/distraction-free/always-on.txt` or
`social.txt`, find the offending line, remove it, save, then
`sudo launchctl kickstart -k system/com.distractionfree.daemon` to reload. This edit is
overwritten on the next `Scripts/update-blocklist.sh` run — if you want it permanent, also
report it upstream to [HaGeZi's issue tracker](https://github.com/hagezi/dns-blocklists/issues)
for the shared lists, or edit `shared/social-domains.txt` in the repo for the social list.

**A site that should be blocked isn't**
Domain-based blocking only catches queries this device actually makes over plain DNS. If an app
hardcodes an IP address (skipping DNS entirely) or uses DNS-over-HTTPS to a provider not on
HaGeZi's bypass list, it won't be caught. The `doh-vpn-proxy-bypass` list specifically targets known
DoH/VPN/proxy providers for this reason — if you find a gap, it belongs in that upstream list.

**Recovery from a corrupted/broken daemon**
The daemon fails open by design at the OS level: if it crashes and isn't restarted (shouldn't
happen — `KeepAlive` in the LaunchDaemon plist restarts it automatically), your Mac's DNS is
pointed at `127.0.0.2` with nothing listening there, so **all** DNS resolution stops, not just the
blocked categories — you'd notice immediately (nothing loads) rather than silently losing
protection. To recover: `sudo networksetup -setdnsservers Wi-Fi Empty` (falls back to DHCP-provided
DNS) restores normal browsing while you debug the daemon.

**Uninstalling**
The in-app "Disable protection" button is intentionally not a real uninstall path (see
`docs/mvp-plan.md`). To actually remove everything:
```
sudo launchctl bootout system/com.distractionfree.daemon
sudo launchctl bootout "gui/$(id -u)/com.distractionfree.menubar"
sudo rm /Library/LaunchDaemons/com.distractionfree.daemon.plist
rm ~/Library/LaunchAgents/com.distractionfree.menubar.plist
sudo networksetup -setdnsservers Wi-Fi Empty
sudo rm -rf /usr/local/opt/distraction-free /usr/local/etc/distraction-free /usr/local/var/distraction-free
```

## Android

**App loses network access entirely / "No internet" everywhere**
The VPN captures all traffic and relays it through a minimal userspace TCP/UDP NAT (see
`android/app/src/main/java/com/distractionfree/app/TcpNat.kt`) — required so that capturing DNS
doesn't also cut off everything else. If this relay ever misbehaves, disable the VPN from
**Settings → Network & internet → VPN** (an OS-level action, same acknowledged limit as the
in-app disable button) rather than force-stopping the app blind.

**A blocked category isn't being blocked**
Check **Settings → Network & internet → VPN → Distraction Free** shows "Connected." If Android shows
the VPN as off, protection isn't running — reopen the app to restart it (it also auto-restarts on
boot if permission was already granted).

**WhatsApp/Messenger/Telegram/Botim stop working**
These are meant to be exempted at the OS level via `addDisallowedApplication` — if one broke, its
package name in `AppExemptions.kt` may be stale (app package names occasionally change). Check the
installed package name via **Settings → Apps → [app] → Advanced → App details** and update
`AppExemptions.communicationPackages` to match.

**Reboot recovery**
`BootReceiver` restarts the VPN automatically after reboot (and after the app itself is updated,
which otherwise kills it), but only if VPN permission was already granted once — Android requires
that consent to have happened interactively at least once; it can't be silently re-granted by a
boot receiver.

**Protection stops when I close / swipe away the app**
Closing the app is supposed to do nothing — the VPN runs as a foreground service. Some phone makers
(Xiaomi, Oppo, Vivo, OnePlus, Samsung's "sleeping apps", …) kill background apps aggressively anyway,
and Android itself doesn't reliably restart a killed service. Three layers keep it running, in order
of how much you need to do:

1. **Automatic (nothing to do):** a watchdog alarm checks every ~5 minutes that the VPN is up and
   restarts it if not, and a swipe-away triggers an immediate re-check (usually back within seconds).
   It survives the app's process being killed.
2. **Allow background running:** the app asks once for the "ignore battery optimizations" exemption
   (there's a button on the main screen if you dismissed it). This stops most phone-maker battery
   managers from killing it in the first place. On some phones you also need to allow **Autostart /
   Auto launch** for the app in the phone's own security/battery settings (verified on a Realme: without
   it the VPN was not restarted after an app update until the app was opened). See
   [dontkillmyapp.com](https://dontkillmyapp.com) for your model, and lock the app in the recents screen
   if your phone offers that, so "Close all" skips it.
3. **Always-on VPN (recommended):** **Settings → Network & internet → VPN → Distraction Free (⚙) →
   Always-on VPN**, plus **Block connections without VPN**. The main screen shows whether it's on. If
   protection ever does stop, the phone then has no internet at all until it's back, instead of
   silently running unprotected.

**The one thing no app can survive:** a real **Force stop** (Settings → Apps → Distraction Free →
Force stop, or a phone-maker "clean/close all" that uses it). Android cancels every alarm and blocks
every broadcast to a force-stopped app until you open it again by hand — this holds even with
Always-on VPN, which we verified. Opening the app restarts protection immediately.

## Both platforms

**A toggle/block change doesn't seem to take effect for a bit**
This is expected, not a bug — DNS blocking has a few layers of caching between "the rule changed"
and "your browser actually notices":
- **Our own DNS decision** changes almost instantly (the daemon/VPN re-checks the toggle file every
  couple seconds).
- **The OS-level DNS resolver cache** can hold onto the old answer for a while after that, since our
  blocked responses don't carry a negative-cache TTL (we're a minimal DNS server, no SOA record) —
  the OS falls back to its own default caching duration. Both platforms now flush this automatically
  the moment a toggle actually changes (macOS: the daemon signals `mDNSResponder` directly, no
  restart; Android: the VPN restarts, which gives every app a fresh network and invalidates the
  cached results tied to the old one — a brief connectivity blip, same tradeoff already accepted for
  blocklist updates).
- **The browser itself** is the one layer we can't reach into. A tab that was already open and
  already connected before you flipped the toggle won't redo a DNS lookup — it just keeps using the
  connection it already has. **Open a brand-new tab** (don't just reload the old one) to see the
  current state; if that's still wrong, it's a real bug, not caching.

In practice: expect it to take a few seconds up to roughly a minute for a toggle change to be
visible in a browser you were actively using at the time. A tab you open fresh after the toggle
change reflects it immediately.

**Safety valve**
Neither platform can ever fully block emergency dialing, core OS update checks, or connectivity
diagnostics — those don't go through DNS/HTTP in a way either implementation intercepts, and
nothing in the blocklists targets them.
