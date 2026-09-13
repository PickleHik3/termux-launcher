#!/usr/bin/env bash
# Wires the rendered launcher-material tmTheme into bat's own config and
# rebuilds bat's cache so it picks the theme up.
set -euo pipefail

bat_config_dir="${BAT_CONFIG_DIR:-${XDG_CONFIG_HOME:-$HOME/.config}/bat}"
rendered="${TERMUX_THEME_OUTPUT:?TERMUX_THEME_OUTPUT not set}"
config_file="$bat_config_dir/config"
themes_dir="$bat_config_dir/themes"
theme_copy="$themes_dir/launcher-material.tmTheme"
marker_begin="# >>> launcher-material >>>"
marker_end="# <<< launcher-material <<<"

mkdir -p "$themes_dir"

# bat only looks under $BAT_CONFIG_DIR/themes; when that differs from the
# renderer's own default output location, keep a copy there too.
if [ "$theme_copy" != "$rendered" ] && { [ ! -f "$theme_copy" ] || ! cmp -s "$rendered" "$theme_copy"; }; then
    cat "$rendered" >"$theme_copy"
fi

mkdir -p "$(dirname "$config_file")"

had_block=0
if [ -f "$config_file" ] && grep -qxF -- "$marker_begin" "$config_file"; then
    had_block=1
fi

tmp_file="$(mktemp "${config_file}.tmp.XXXXXX")"
trap 'rm -f "$tmp_file"' EXIT

if [ -f "$config_file" ]; then
    cat "$config_file" >"$tmp_file"
else
    : >"$tmp_file"
fi

if [ "$had_block" -eq 0 ]; then
    if [ -s "$tmp_file" ] && [ -n "$(tail -c1 "$tmp_file")" ]; then
        printf '\n' >>"$tmp_file"
    fi
    {
        echo "$marker_begin"
        echo '--theme="launcher-material"'
        echo "$marker_end"
    } >>"$tmp_file"
fi

if [ ! -e "$config_file" ] && [ ! -L "$config_file" ]; then
    mv "$tmp_file" "$config_file"
    trap - EXIT
elif ! cmp -s "$config_file" "$tmp_file"; then
    cat "$tmp_file" >"$config_file"
fi

if command -v bat >/dev/null 2>&1; then
    BAT_CONFIG_DIR="$bat_config_dir" bat cache --build >/dev/null 2>&1 || true
fi
