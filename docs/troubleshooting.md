# Troubleshooting

## macOS

**Nothing is being blocked / `dig pornhub.com` resolves normally**
1. Check the daemon is running: `sudo launchctl print system/com.distractionfree.daemon` — look for
   `state = running`.
2. Check your Mac's DNS is actually pointed at the daemon: `networksetup -getdnsservers Wi-Fi`
   should show `127.0.0.1`. If it shows something else (a network change, a VPN app, or manually
   editing Network settings can reset this), re-run `Scripts/install.sh` or manually:
   `sudo networksetup -setdnsservers Wi-Fi 127.0.0.1`.
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
pointed at `127.0.0.1` with nothing listening there, so **all** DNS resolution stops, not just the
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
`BootReceiver` restarts the VPN automatically after reboot, but only if VPN permission was already
granted once — Android requires that consent to have happened interactively at least once; it can't
be silently re-granted by a boot receiver.

## Both platforms

**Safety valve**
Neither platform can ever fully block emergency dialing, core OS update checks, or connectivity
diagnostics — those don't go through DNS/HTTP in a way either implementation intercepts, and
nothing in the blocklists targets them.
