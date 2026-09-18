#!/bin/bash
# Builds dfdaemon + dfmenubar, installs the daemon as a LaunchDaemon (root,
# survives logout/reboot), and the menubar app as a per-user LaunchAgent.
# Run as your normal user — this script escalates only the specific steps
# that need root, via sudo prompts, not itself.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
INSTALL_DIR="/usr/local/opt/distraction-free"
CONFIG_DIR="/usr/local/etc/distraction-free"
STATE_DIR="/usr/local/var/distraction-free"

echo "Building dfdaemon + dfmenubar (release)..."
swift build -c release --package-path "$REPO_ROOT"

echo "Installing to $INSTALL_DIR (requires sudo)..."
sudo mkdir -p "$INSTALL_DIR" "$CONFIG_DIR" "$STATE_DIR"
sudo cp "$REPO_ROOT/.build/release/dfdaemon" "$INSTALL_DIR/dfdaemon"
sudo cp "$REPO_ROOT/Scripts/run-daemon.sh" "$INSTALL_DIR/run-daemon.sh"
sudo cp "$REPO_ROOT/Scripts/update-blocklist.sh" "$INSTALL_DIR/update-blocklist.sh"
sudo cp "$REPO_ROOT/../shared/social-domains.txt" "$INSTALL_DIR/social-domains.txt"
sudo cp "$REPO_ROOT/../shared/quotes.txt" "$INSTALL_DIR/quotes.txt"
sudo chmod 755 "$INSTALL_DIR/dfdaemon" "$INSTALL_DIR/update-blocklist.sh" "$INSTALL_DIR/run-daemon.sh"
sudo chmod 644 "$INSTALL_DIR/quotes.txt"
sudo chmod 755 "$CONFIG_DIR"
# STATE_DIR needs group-write, not just 755: the menubar app runs unprivileged
# and writes social-toggle.json directly into it (daemon just reads/applies
# it). 755 silently broke that write for years (try? swallowed the error) —
# group-owned by staff (the default interactive-user group) + 775 fixes it.
sudo chgrp staff "$STATE_DIR"
sudo chmod 775 "$STATE_DIR"

echo "Fetching initial blocklists..."
sudo DF_CONFIG_DIR="$CONFIG_DIR" DF_INSTALL_DIR="$INSTALL_DIR" "$INSTALL_DIR/update-blocklist.sh"

echo "Installing LaunchDaemon..."
sudo cp "$REPO_ROOT/LaunchDaemons/com.distractionfree.daemon.plist" /Library/LaunchDaemons/
sudo launchctl bootout system/com.distractionfree.daemon >/dev/null 2>&1 || true
sudo launchctl bootstrap system /Library/LaunchDaemons/com.distractionfree.daemon.plist
sudo launchctl enable system/com.distractionfree.daemon

echo "Installing daily blocklist auto-update (4am)..."
sudo cp "$REPO_ROOT/LaunchDaemons/com.distractionfree.blocklist-update.plist" /Library/LaunchDaemons/
sudo launchctl bootout system/com.distractionfree.blocklist-update >/dev/null 2>&1 || true
sudo launchctl bootstrap system /Library/LaunchDaemons/com.distractionfree.blocklist-update.plist
sudo launchctl enable system/com.distractionfree.blocklist-update

echo "Pointing this Mac's DNS at the daemon..."
NETWORK_SERVICE=$(networksetup -listallnetworkservices | grep -v '^\*' | grep -v "^An asterisk" | head -1)
sudo networksetup -setdnsservers "$NETWORK_SERVICE" 127.0.0.2

echo "Installing menubar app as a login item..."
cp "$REPO_ROOT/.build/release/dfmenubar" /usr/local/bin/dfmenubar 2>/dev/null || sudo cp "$REPO_ROOT/.build/release/dfmenubar" /usr/local/bin/dfmenubar
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
