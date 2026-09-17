#!/bin/bash
# macOS only assigns 127.0.0.1 to lo0 by default; 127.0.0.2 needs an explicit
# alias before dfdaemon can bind to it. Idempotent — safe to run every boot.
/sbin/ifconfig lo0 alias 127.0.0.2 up 2>/dev/null || true
exec /usr/local/opt/distraction-free/dfdaemon
