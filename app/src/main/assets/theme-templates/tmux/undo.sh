#!/usr/bin/env bash
# Removes exactly the marker block apply.sh added, deleting the file if that
# was all it contained, and removes the rendered colours file.
set -euo pipefail

xdg_conf="${XDG_CONFIG_HOME:-$HOME/.config}/tmux/tmux.conf"
dotfile_conf="$HOME/.tmux.conf"
marker_begin="# >>> launcher-material >>>"
marker_end="# <<< launcher-material <<<"
rendered="${TERMUX_THEME_OUTPUT:?TERMUX_THEME_OUTPUT not set}"

if [ -f "$xdg_conf" ]; then
    target_conf="$xdg_conf"
elif [ -f "$dotfile_conf" ]; then
    target_conf="$dotfile_conf"
else
    target_conf=""
fi

if [ -n "$target_conf" ] && [ -f "$target_conf" ]; then
    tmp_file="$(mktemp "${target_conf}.tmp.XXXXXX")"
    trap 'rm -f "$tmp_file"' EXIT

    awk -v begin="$marker_begin" -v end="$marker_end" '
        $0 == begin { in_block = 1; next }
        in_block && $0 == end { in_block = 0; next }
        in_block { next }
        { print }
    ' "$target_conf" >"$tmp_file"

    if ! cmp -s "$target_conf" "$tmp_file"; then
        cat "$tmp_file" >"$target_conf"
    fi

    if [ ! -s "$target_conf" ]; then
        rm -f -- "$target_conf"
    elif command -v tmux >/dev/null 2>&1 && command -v pgrep >/dev/null 2>&1 && pgrep -x tmux >/dev/null 2>&1; then
        tmux source-file "$target_conf" >/dev/null 2>&1 || true
    fi
fi

rm -f -- "$rendered"
