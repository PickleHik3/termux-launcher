#!/usr/bin/env bash
# Adds one source-file line for the rendered launcher-material tmux colours,
# into whichever of tmux's own config files exists (creating the XDG one if
# neither does), and reloads a running server.
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
    target_conf="$xdg_conf"
fi

mkdir -p "$(dirname "$target_conf")"

include_line="source-file \"$rendered\""

tmp_file="$(mktemp "${target_conf}.tmp.XXXXXX")"
trap 'rm -f "$tmp_file"' EXIT

# Strip any existing marker block first, then always append a fresh one, so
# a stale or hand-mangled line is repaired on the next apply instead of
# being left in place. Rerunning stays idempotent: when the block already
# matches, the stripped-then-reappended content is byte-identical to what
# was there.
if [ -f "$target_conf" ]; then
    awk -v begin="$marker_begin" -v end="$marker_end" '
        $0 == begin { in_block = 1; next }
        in_block && $0 == end { in_block = 0; next }
        in_block { next }
        { print }
    ' "$target_conf" >"$tmp_file"
else
    : >"$tmp_file"
fi

if [ -s "$tmp_file" ] && [ -n "$(tail -c1 "$tmp_file")" ]; then
    printf '\n' >>"$tmp_file"
fi
{
    echo "$marker_begin"
    echo "$include_line"
    echo "$marker_end"
} >>"$tmp_file"

if [ ! -e "$target_conf" ] && [ ! -L "$target_conf" ]; then
    mv "$tmp_file" "$target_conf"
    trap - EXIT
elif ! cmp -s "$target_conf" "$tmp_file"; then
    cat "$tmp_file" >"$target_conf"
fi

if command -v tmux >/dev/null 2>&1 && command -v pgrep >/dev/null 2>&1 && pgrep -x tmux >/dev/null 2>&1; then
    tmux source-file "$rendered" >/dev/null 2>&1 || true
fi
