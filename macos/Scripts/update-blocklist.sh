#!/bin/bash
# Downloads HaGeZi's wildcard-format lists for the always-on categories and
# the hand-written social-media list, merges/dedupes them, and writes them
# into /usr/local/etc/distraction-free/. Run with sudo (or via the menubar
# app's admin-prompt helper). Safe to re-run: fully replaces prior content.
set -euo pipefail

CONFIG_DIR="${DF_CONFIG_DIR:-/usr/local/etc/distraction-free}"
INSTALL_DIR="${DF_INSTALL_DIR:-/usr/local/opt/distraction-free}"
STATE_DIR="${DF_STATE_DIR:-/usr/local/var/distraction-free}"
BASE_URL="https://cdn.jsdelivr.net/gh/hagezi/dns-blocklists@latest/wildcard"

mkdir -p "$CONFIG_DIR"
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

echo "Fetching HaGeZi always-on lists..."
curl -fsSL "$BASE_URL/nsfw.txt" -o "$TMP/nsfw.txt"
curl -fsSL "$BASE_URL/gambling.medium.txt" -o "$TMP/gambling.txt"
curl -fsSL "$BASE_URL/anti.piracy.txt" -o "$TMP/piracy.txt"
curl -fsSL "$BASE_URL/doh-vpn-proxy-bypass.txt" -o "$TMP/bypass.txt"

grep -hv '^#' "$TMP"/*.txt | sed 's/^\*\.//' | sort -u > "$CONFIG_DIR/always-on.txt"
echo "  -> $(wc -l < "$CONFIG_DIR/always-on.txt") always-on domains"

# Adult saved separately (not just merged into always-on.txt): the daemon
# uses it to tell "adult" blocks apart from gambling/piracy/bypass blocks
# for the per-category time-back estimate.
grep -hv '^#' "$TMP/nsfw.txt" | sed 's/^\*\.//' | sort -u > "$CONFIG_DIR/adult.txt"

cp "$INSTALL_DIR/social-domains.txt" "$CONFIG_DIR/social.txt"
echo "  -> $(grep -cv '^#' "$CONFIG_DIR/social.txt") social-media domains"

chmod 644 "$CONFIG_DIR/always-on.txt" "$CONFIG_DIR/adult.txt" "$CONFIG_DIR/social.txt"

if launchctl print system/com.distractionfree.daemon >/dev/null 2>&1; then
    echo "Restarting daemon to pick up the new lists..."
    launchctl kickstart -k system/com.distractionfree.daemon
fi

# Marks a successful run so the retry daemon (see
# com.distractionfree.blocklist-retry.plist) knows not to bother — if this
# script exits early above (curl failure, no network), this never gets
# written, so "no recent success" is exactly the retry signal, no separate
# failure-tracking needed.
mkdir -p "$STATE_DIR"
date +%s > "$STATE_DIR/last-blocklist-update"

echo "Done."
