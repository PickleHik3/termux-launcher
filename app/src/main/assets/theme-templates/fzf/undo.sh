#!/usr/bin/env bash
# Removes the fish drop-in, the marker block from ~/.bashrc and ~/.zshrc
# (whichever exist), and the rendered options file.
set -euo pipefail

rendered="${TERMUX_THEME_OUTPUT:?TERMUX_THEME_OUTPUT not set}"
marker_begin="# >>> launcher-material >>>"
marker_end="# <<< launcher-material <<<"
fish_dropin="${XDG_CONFIG_HOME:-$HOME/.config}/fish/conf.d/launcher-material-fzf.fish"

rm -f -- "$fish_dropin"

remove_marker_block() {
    local rc_file="$1"
    [ -f "$rc_file" ] || return 0
    local tmp_file
    tmp_file="$(mktemp "${rc_file}.tmp.XXXXXX")"
    awk -v begin="$marker_begin" -v end="$marker_end" '
        $0 == begin { in_block = 1; next }
        in_block && $0 == end { in_block = 0; next }
        in_block { next }
        { print }
    ' "$rc_file" >"$tmp_file"
    if ! cmp -s "$rc_file" "$tmp_file"; then
        cat "$tmp_file" >"$rc_file"
    fi
    rm -f "$tmp_file"
}

remove_marker_block "$HOME/.bashrc"
remove_marker_block "$HOME/.zshrc"

rm -f -- "$rendered"
