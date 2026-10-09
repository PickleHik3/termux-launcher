#!/bin/sh
# copy.sh [<text>] — put text on the Android clipboard through `launcherctl clipboard copy`,
# the same path an OSC 52 write takes. With no argument the text is read from stdin, so
# `git diff | sh copy.sh` works. Only succeeds while the launcher is on screen (the same rule
# as OSC 52); otherwise launcherctl prints {"error":"launcher_not_visible"} and exits 1.
# Usage: sh "<skill dir>/scripts/copy.sh" 'text'   |   some-command | sh "<skill dir>/scripts/copy.sh"
set -eu

if ! command -v launcherctl >/dev/null 2>&1; then
    echo "copy.sh: launcherctl is not on PATH; open Termux Launcher once" >&2
    exit 1
fi
exec launcherctl clipboard copy "$@"
