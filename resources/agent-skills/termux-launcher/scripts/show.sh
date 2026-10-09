#!/bin/sh
# show.sh <file> — draw an image in a pane of its own (never in this agent's own pane; that
# would corrupt whatever TUI or output the agent's own shell is showing). Opens
# `launcherctl pane open --no-focus -- kitten icat --hold <file>` and prints the pane id, the way
# launcherctl's own JSON does. Usage: sh "<skill dir>/scripts/show.sh" out.png
set -eu

file="${1:-}"
if [ -z "$file" ]; then
    echo "usage: show.sh <file>" >&2
    exit 2
fi
if [ ! -r "$file" ]; then
    echo "show.sh: cannot read $file" >&2
    exit 1
fi
if ! command -v kitten >/dev/null 2>&1; then
    echo "show.sh: kitten is not installed; install it with tlstore (tlstore install kitten)" >&2
    exit 1
fi
if ! command -v launcherctl >/dev/null 2>&1; then
    echo "show.sh: launcherctl is not on PATH" >&2
    exit 1
fi

case "$file" in
    /*) abs="$file" ;;
    *) abs="$(pwd)/$file" ;;
esac

launcherctl pane open --title "$(basename "$abs")" --no-focus -- kitten icat --hold "$abs"
