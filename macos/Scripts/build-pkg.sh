#!/bin/bash
# Builds a double-click .pkg installer: stages the release binaries/scripts/
# LaunchDaemon plists as a payload rooted at "/", then pkgbuild wraps that
# with pkg/scripts/postinstall (which does the runtime setup — blocklist
# fetch, service bootstrap, DNS, per-user LaunchAgent — that can't be a
# static payload file). Installer.app handles the one admin-password prompt;
# postinstall then runs as root with no further sudo needed.
#
# Usage: Scripts/build-pkg.sh [version]
# Output: distraction-free.pkg in the repo root.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VERSION="${1:-0.1.0}"
WORKDIR="$(mktemp -d)"
PAYLOAD="$WORKDIR/payload"
trap 'rm -rf "$WORKDIR"' EXIT

echo "Building dfdaemon + dfmenubar (release)..."
swift build -c release --package-path "$REPO_ROOT"

echo "Staging payload..."
mkdir -p "$PAYLOAD/usr/local/opt/distraction-free"
mkdir -p "$PAYLOAD/usr/local/bin"
mkdir -p "$PAYLOAD/Library/LaunchDaemons"

cp "$REPO_ROOT/.build/release/dfdaemon" "$PAYLOAD/usr/local/opt/distraction-free/dfdaemon"
cp "$REPO_ROOT/.build/release/dfmenubar" "$PAYLOAD/usr/local/bin/dfmenubar"
cp "$REPO_ROOT/Scripts/run-daemon.sh" "$PAYLOAD/usr/local/opt/distraction-free/run-daemon.sh"
cp "$REPO_ROOT/Scripts/update-blocklist.sh" "$PAYLOAD/usr/local/opt/distraction-free/update-blocklist.sh"
cp "$REPO_ROOT/Scripts/retry-blocklist-update.sh" "$PAYLOAD/usr/local/opt/distraction-free/retry-blocklist-update.sh"
cp "$REPO_ROOT/../shared/social-domains.txt" "$PAYLOAD/usr/local/opt/distraction-free/social-domains.txt"
cp "$REPO_ROOT/../shared/quotes.txt" "$PAYLOAD/usr/local/opt/distraction-free/quotes.txt"
cp "$REPO_ROOT/LaunchDaemons/com.distractionfree.daemon.plist" "$PAYLOAD/Library/LaunchDaemons/"
cp "$REPO_ROOT/LaunchDaemons/com.distractionfree.blocklist-update.plist" "$PAYLOAD/Library/LaunchDaemons/"
cp "$REPO_ROOT/LaunchDaemons/com.distractionfree.blocklist-retry.plist" "$PAYLOAD/Library/LaunchDaemons/"

chmod +x "$PAYLOAD/usr/local/opt/distraction-free"/*.sh "$PAYLOAD/usr/local/opt/distraction-free/dfdaemon" "$PAYLOAD/usr/local/bin/dfmenubar"

echo "Building distraction-free.pkg (version $VERSION)..."
pkgbuild \
    --root "$PAYLOAD" \
    --scripts "$REPO_ROOT/pkg/scripts" \
    --identifier com.distractionfree.pkg \
    --version "$VERSION" \
    --install-location / \
    --ownership recommended \
    "$REPO_ROOT/../distraction-free.pkg"

echo "Done: distraction-free.pkg"
