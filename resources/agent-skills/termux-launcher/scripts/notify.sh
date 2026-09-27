#!/bin/sh
# notify.sh [--title TEXT] [--id ID] [--urgency low|normal|critical] <body> — a message in the
# phone's notification shade, through `launcherctl notify`. It is the same code path OSC 99
# takes, but over the local API, so it works from any process: a tool runner with no terminal,
# a background service, a pipe. Usage: sh "<skill dir>/scripts/notify.sh" [--title "Build"] "42 tests passed"
set -eu

if ! command -v launcherctl >/dev/null 2>&1; then
    echo "notify.sh: launcherctl is not on PATH; open Termux Launcher once" >&2
    exit 1
fi
if [ "$#" -eq 0 ]; then
    echo "usage: notify.sh [--title TEXT] [--id ID] [--urgency low|normal|critical] <body>" >&2
    exit 2
fi
exec launcherctl notify "$@"
