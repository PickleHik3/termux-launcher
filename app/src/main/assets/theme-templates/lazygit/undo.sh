#!/usr/bin/env bash
# Removes the fish drop-in, the marker block from ~/.bashrc and ~/.zshrc
# (whichever exist), and the rendered theme file.
set -euo pipefail

rendered="${TERMUX_THEME_OUTPUT:?TERMUX_THEME_OUTPUT not set}"
marker_begin="# >>> launcher-material lazygit >>>"
marker_end="# <<< launcher-material lazygit <<<"
# The marker every template shared before this one was split out per
# template; still strip it so a phone that already had this block (written
# under the old, shared marker) gets cleaned up on upgrade.
old_marker_begin="# >>> launcher-material >>>"
old_marker_end="# <<< launcher-material <<<"
fish_dropin="${XDG_CONFIG_HOME:-$HOME/.config}/fish/conf.d/launcher-material-lazygit.fish"

rm -f -- "$fish_dropin"

remove_marker_block() {
    local rc_file="$1"
    [ -f "$rc_file" ] || return 0
    local tmp_file
    tmp_file="$(mktemp "${rc_file}.tmp.XXXXXX")"
    awk -v begin="$marker_begin" -v end="$marker_end" \
        -v old_begin="$old_marker_begin" -v old_end="$old_marker_end" '
        $0 == begin || $0 == old_begin { in_block = 1; next }
        in_block && ($0 == end || $0 == old_end) { in_block = 0; next }
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
