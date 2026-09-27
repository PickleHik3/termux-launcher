#!/bin/sh
# progress.sh <0-100|clear|error [PCT]|indeterminate> [--pane ID] — the progress ring on the
# window chip, through `launcherctl progress`. Same code path as OSC 9;4, over the local API,
# so it works from a process with no terminal. Without --pane it reports for
# $TERMUX_LAUNCHER_PANE, or the current pane when that is unset (opencode's tool runner).
# Usage: sh "<skill dir>/scripts/progress.sh" 42
#        sh "<skill dir>/scripts/progress.sh" clear
#        sh "<skill dir>/scripts/progress.sh" error 87   # PCT optional, keeps the last value
set -eu

if ! command -v launcherctl >/dev/null 2>&1; then
    echo "progress.sh: launcherctl is not on PATH; open Termux Launcher once" >&2
    exit 1
fi
if [ "$#" -eq 0 ]; then
    echo "usage: progress.sh <0-100|clear|error [PCT]|indeterminate> [--pane ID]" >&2
    exit 2
fi
exec launcherctl progress "$@"
