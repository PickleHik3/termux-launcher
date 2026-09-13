#!/usr/bin/env bash
# Removes exactly the marker block apply.sh added to theme.toml and the
# rendered flavor directory's flavor.toml.
set -euo pipefail

config_file="${XDG_CONFIG_HOME:-$HOME/.config}/yazi/theme.toml"
rendered="${TERMUX_THEME_OUTPUT:?TERMUX_THEME_OUTPUT not set}"
marker_begin="# >>> launcher-material >>>"
marker_end="# <<< launcher-material <<<"

if [ -f "$config_file" ]; then
    tmp_file="$(mktemp "${config_file}.tmp.XXXXXX")"
    trap 'rm -f "$tmp_file"' EXIT
    awk -v begin="$marker_begin" -v end="$marker_end" '
        $0 == begin { in_block = 1; next }
        in_block && $0 == end { in_block = 0; next }
        in_block { next }
        { print }
    ' "$config_file" >"$tmp_file"
    if ! cmp -s "$config_file" "$tmp_file"; then
        cat "$tmp_file" >"$config_file"
    fi
    if [ ! -s "$config_file" ]; then
        rm -f -- "$config_file"
    fi
fi

rm -f -- "$rendered"
