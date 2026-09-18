#!/bin/bash
# Failsafe for the daily 4am blocklist update: if the Mac was asleep, offline,
# or the fetch otherwise failed, the daily job just waits for tomorrow with no
# retry — this closes that gap. Runs every 30 minutes (cheap no-op check most
# of the time); if the last successful update is more than ~20 hours old (i.e.
# today's 4am run hasn't landed yet), retries immediately. Self-quiets once a
# run succeeds, no exponential backoff or retry cap needed — each attempt is
# just a few small HTTP requests.
set -uo pipefail

STATE_DIR="${DF_STATE_DIR:-/usr/local/var/distraction-free}"
INSTALL_DIR="${DF_INSTALL_DIR:-/usr/local/opt/distraction-free}"
MARKER="$STATE_DIR/last-blocklist-update"
STALE_AFTER_SECONDS=$((20 * 3600))

if [ -f "$MARKER" ]; then
    last=$(cat "$MARKER" 2>/dev/null || echo 0)
    now=$(date +%s)
    age=$((now - last))
    if [ "$age" -lt "$STALE_AFTER_SECONDS" ]; then
        exit 0 # recent success — nothing to do
    fi
fi

echo "$(date): last update missing or stale, retrying..."
"$INSTALL_DIR/update-blocklist.sh"
