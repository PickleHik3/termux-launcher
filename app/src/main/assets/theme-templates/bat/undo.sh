#!/usr/bin/env bash
# Removes exactly the marker block apply.sh added, any extra theme copy it
# made, and the rendered tmTheme, then rebuilds bat's cache.
set -euo pipefail

bat_config_dir="${BAT_CONFIG_DIR:-${XDG_CONFIG_HOME:-$HOME/.config}/bat}"
rendered="${TERMUX_THEME_OUTPUT:?TERMUX_THEME_OUTPUT not set}"
config_file="$bat_config_dir/config"
themes_dir="$bat_config_dir/themes"
theme_copy="$themes_dir/launcher-material.tmTheme"
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

if [ "$theme_copy" != "$rendered" ]; then
    rm -f -- "$theme_copy"
fi
rm -f -- "$rendered"

if command -v bat >/dev/null 2>&1; then
    BAT_CONFIG_DIR="$bat_config_dir" bat cache --build >/dev/null 2>&1 || true
fi
