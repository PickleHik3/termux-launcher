#!/bin/sh
# notify.sh [--title TEXT] <body> — Android notification via OSC 99, sent to this pane's own
# tty (see tty.sh) so it works even though this script's stdout is captured by an agent's tool
# pipe. Usage: sh "<skill dir>/scripts/notify.sh" [--title "Build"] "42 tests passed"
set -eu

dir=$(dirname "$0")
. "$dir/tty.sh"

title=
while [ "$#" -gt 0 ]; do
    case "$1" in
        --title) title="${2:-}"; shift 2 ;;
        --) shift; break ;;
        *) break ;;
    esac
done
body="${*:-}"
if [ -z "$body" ]; then
    echo "usage: notify.sh [--title TEXT] <body>" >&2
    exit 2
fi

tlskill_resolve_tty || exit 1

if [ -n "$title" ]; then
    printf '\033]99;i=1:d=0:p=title;%s\033\\' "$title" >"$TLSKILL_TTY"
    printf '\033]99;i=1:d=1:p=body;%s\033\\' "$body" >"$TLSKILL_TTY"
else
    printf '\033]99;i=1:d=1:p=body;%s\033\\' "$body" >"$TLSKILL_TTY"
fi
