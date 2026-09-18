#!/bin/bash
# Installs a pre-built release (from the GitHub Releases zip) — no Xcode/Swift
# toolchain required on this Mac, unlike Scripts/install.sh which builds from
# source. Run this from inside the extracted release folder.
# Safe to re-run for updates: never touches CONFIG_DIR/STATE_DIR contents, so
# blocklists, stats, streaks, and time/data-back counters survive an update.
set -euo pipefail

RELEASE_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INSTALL_DIR="/usr/local/opt/distraction-free"
CONFIG_DIR="/usr/local/etc/distraction-free"
STATE_DIR="/usr/local/var/distraction-free"

if [ ! -f "$RELEASE_ROOT/dfdaemon" ] || [ ! -f "$RELEASE_ROOT/dfmenubar" ]; then
    echo "Run this from inside the extracted release folder (expects dfdaemon/dfmenubar next to this script)." >&2
    exit 1
fi

echo "macOS Gatekeeper will quarantine downloaded binaries — clearing that flag..."
xattr -dr com.apple.quarantine "$RELEASE_ROOT" 2>/dev/null || true

echo "Installing to $INSTALL_DIR (requires sudo)..."
sudo mkdir -p "$INSTALL_DIR" "$CONFIG_DIR" "$STATE_DIR"
sudo cp "$RELEASE_ROOT/dfdaemon" "$INSTALL_DIR/dfdaemon"
sudo cp "$RELEASE_ROOT/run-daemon.sh" "$INSTALL_DIR/run-daemon.sh"
sudo cp "$RELEASE_ROOT/update-blocklist.sh" "$INSTALL_DIR/update-blocklist.sh"
sudo cp "$RELEASE_ROOT/social-domains.txt" "$INSTALL_DIR/social-domains.txt"
sudo cp "$RELEASE_ROOT/quotes.txt" "$INSTALL_DIR/quotes.txt"
sudo chmod 755 "$INSTALL_DIR/dfdaemon" "$INSTALL_DIR/update-blocklist.sh" "$INSTALL_DIR/run-daemon.sh"
sudo chmod 644 "$INSTALL_DIR/quotes.txt"
sudo chmod 755 "$CONFIG_DIR"
# STATE_DIR needs group-write, not just 755: the menubar app runs unprivileged
# and writes social-toggle.json directly into it (daemon just reads/applies
# it). Group-owned by staff (the default interactive-user group) + 775 lets
# that write actually succeed.
sudo chgrp staff "$STATE_DIR"
sudo chmod 775 "$STATE_DIR"

if [ ! -f "$CONFIG_DIR/always-on.txt" ]; then
    echo "Fetching initial blocklists..."
    sudo DF_CONFIG_DIR="$CONFIG_DIR" DF_INSTALL_DIR="$INSTALL_DIR" "$INSTALL_DIR/update-blocklist.sh"
else
    echo "Existing blocklists found, leaving them — the daily 4am job will refresh them."
fi

echo "Installing LaunchDaemons..."
sudo cp "$RELEASE_ROOT/com.distractionfree.daemon.plist" /Library/LaunchDaemons/
sudo launchctl bootout system/com.distractionfree.daemon >/dev/null 2>&1 || true
sudo launchctl bootstrap system /Library/LaunchDaemons/com.distractionfree.daemon.plist
sudo launchctl enable system/com.distractionfree.daemon

sudo cp "$RELEASE_ROOT/com.distractionfree.blocklist-update.plist" /Library/LaunchDaemons/
sudo launchctl bootout system/com.distractionfree.blocklist-update >/dev/null 2>&1 || true
sudo launchctl bootstrap system /Library/LaunchDaemons/com.distractionfree.blocklist-update.plist
sudo launchctl enable system/com.distractionfree.blocklist-update

echo "Pointing this Mac's DNS at the daemon..."
NETWORK_SERVICE=$(networksetup -listallnetworkservices | grep -v '^\*' | grep -v "^An asterisk" | head -1)
sudo networksetup -setdnsservers "$NETWORK_SERVICE" 127.0.0.2

echo "Installing menubar app as a login item..."
sudo cp "$RELEASE_ROOT/dfmenubar" /usr/local/bin/dfmenubar
mkdir -p "$HOME/Library/LaunchAgents"
cat > "$HOME/Library/LaunchAgents/com.distractionfree.menubar.plist" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>Label</key>
    <string>com.distractionfree.menubar</string>
    <key>ProgramArguments</key>
    <array>
        <string>/usr/local/bin/dfmenubar</string>
    </array>
    <key>RunAtLoad</key>
    <true/>
    <key>KeepAlive</key>
    <true/>
</dict>
</plist>
PLIST
launchctl bootout "gui/$(id -u)/com.distractionfree.menubar" >/dev/null 2>&1 || true
launchctl bootstrap "gui/$(id -u)" "$HOME/Library/LaunchAgents/com.distractionfree.menubar.plist"

echo ""
echo "Installed. The daemon is filtering DNS system-wide now (test: dig pornhub.com — should NXDOMAIN)."
echo "The menubar icon (shield) is running and will also start automatically at login."
