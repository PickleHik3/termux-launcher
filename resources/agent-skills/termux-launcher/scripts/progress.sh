#!/bin/sh
# progress.sh <0-100|clear|error [PCT]> — the ConEmu-style progress ring on this pane's window
# chip (OSC 9;4), sent straight to the pane's own tty (see tty.sh).
# Usage: sh "<skill dir>/scripts/progress.sh" 42
#        sh "<skill dir>/scripts/progress.sh" clear
#        sh "<skill dir>/scripts/progress.sh" error 87   # PCT optional, defaults to 0
set -eu

dir=$(dirname "$0")
. "$dir/tty.sh"

arg="${1:-}"
if [ -z "$arg" ]; then
    echo "usage: progress.sh <0-100|clear|error [PCT]>" >&2
    exit 2
fi

tlskill_resolve_tty || exit 1

case "$arg" in
    clear)
        printf '\033]9;4;0;\033\\' >"$TLSKILL_TTY"
        ;;
    error)
        pct="${2:-0}"
        printf '\033]9;4;2;%s\033\\' "$pct" >"$TLSKILL_TTY"
        ;;
    *[!0-9]*|'')
        echo "progress.sh: expected 0-100, clear or error, got: $arg" >&2
        exit 2
        ;;
    *)
        if [ "$arg" -gt 100 ]; then
            echo "progress.sh: percent must be 0-100, got: $arg" >&2
            exit 2
        fi
        printf '\033]9;4;1;%s\033\\' "$arg" >"$TLSKILL_TTY"
        ;;
esac
