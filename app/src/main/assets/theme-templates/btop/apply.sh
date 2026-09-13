#!/usr/bin/env bash
# Points btop.conf at the rendered launcher-material theme.
# Ported from noctalia shell (MIT); adapted to read TERMUX_THEME_OUTPUT and
# reload only when btop is actually running.
set -euo pipefail

config_file="${XDG_CONFIG_HOME:-$HOME/.config}/btop/btop.conf"
theme_name="launcher-material"

if [ ! -f "$config_file" ]; then
    # btop writes its own default config on first run; nothing to wire yet.
    echo "btop: config file not found at $config_file, skipping" >&2
    exit 0
fi

write_if_changed() {
    local target="$1" tmp="$2"
    if ! cmp -s "$target" "$tmp"; then
        cat "$tmp" >"$target"
    fi
    rm -f "$tmp"
}

if grep -qE "^color_theme[[:space:]]*=[[:space:]]*\"${theme_name}\"" "$config_file"; then
    :
elif grep -qE '^color_theme[[:space:]]*=' "$config_file"; then
    tmp_file="$(mktemp "${config_file}.tmp.XXXXXX")"
    sed -E "s/^color_theme[[:space:]]*=.*/color_theme = \"${theme_name}\"/" "$config_file" >"$tmp_file"
    write_if_changed "$config_file" "$tmp_file"
else
    [ -s "$config_file" ] && [ -n "$(tail -c1 "$config_file")" ] && echo >>"$config_file"
    echo "color_theme = \"${theme_name}\"" >>"$config_file"
fi

if command -v pgrep >/dev/null 2>&1 && pgrep -x btop >/dev/null 2>&1; then
    pkill -SIGUSR2 -x btop || true
fi
