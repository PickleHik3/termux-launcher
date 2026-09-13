#!/usr/bin/env bash
# Removes exactly what apply.sh added: the color_theme line it wrote (only if
# it is still ours) and the rendered theme file.
# Ported from noctalia shell (MIT); adapted to read TERMUX_THEME_OUTPUT.
set -euo pipefail

config_file="${XDG_CONFIG_HOME:-$HOME/.config}/btop/btop.conf"
theme_file="${TERMUX_THEME_OUTPUT:?TERMUX_THEME_OUTPUT not set}"
theme_name="launcher-material"
changed=0

if [ -f "$config_file" ]; then
    tmp_file="$(mktemp "${config_file}.tmp.XXXXXX")"
    trap 'rm -f "$tmp_file"' EXIT
    awk -v pat="^[[:space:]]*color_theme[[:space:]]*=[[:space:]]*\"${theme_name}\"" \
        '$0 !~ pat' "$config_file" >"$tmp_file"
    if ! cmp -s "$config_file" "$tmp_file"; then
        cat "$tmp_file" >"$config_file"
        changed=1
    fi
fi

if [ -e "$theme_file" ] || [ -L "$theme_file" ]; then
    rm -f -- "$theme_file"
    changed=1
fi

# A reload signal delivered to a starting process kills it, so signal only
# when this run actually removed something.
if [ "$changed" -eq 1 ] && command -v pgrep >/dev/null 2>&1 && pgrep -x btop >/dev/null 2>&1; then
    pkill -SIGUSR2 -x btop || true
fi
