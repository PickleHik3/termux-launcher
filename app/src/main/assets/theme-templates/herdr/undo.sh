#!/bin/sh
# Removes exactly the marker blocks apply.sh added to config.toml - the
# `auto_switch = true` line in [theme] and the two palette subtables, each of
# which carries its own table header when apply.sh had to create it - and
# restores every pre-existing key apply.sh commented out, including the user's
# own auto_switch. Then removes the rendered palette file and asks a running
# herdr server to reload. Pure POSIX sh/awk, matching apply.sh.
set -eu

config_file="${XDG_CONFIG_HOME:-$HOME/.config}/herdr/config.toml"
rendered="${TERMUX_THEME_OUTPUT:?TERMUX_THEME_OUTPUT not set}"
marker_begin="# >>> launcher-material >>>"
marker_end="# <<< launcher-material <<<"
restore_prefix="# launcher-material: "

if [ -f "$config_file" ]; then
    tmp_file="$(mktemp "${config_file}.tmp.XXXXXX")"
    trap 'rm -f "$tmp_file"' EXIT

    awk -v begin="$marker_begin" -v end="$marker_end" -v prefix="$restore_prefix" '
        BEGIN { plen = length(prefix) }
        $0 == begin { in_marker = 1; next }
        in_marker { if ($0 == end) in_marker = 0; next }
        substr($0, 1, plen) == prefix { print substr($0, plen + 1); next }
        { print }
    ' "$config_file" >"$tmp_file"

    if ! cmp -s "$config_file" "$tmp_file"; then
        cat "$tmp_file" >"$config_file"
    fi
    rm -f "$tmp_file"
    trap - EXIT

    if [ ! -s "$config_file" ]; then
        rm -f -- "$config_file"
    fi
fi

rm -f -- "$rendered"

if command -v herdr >/dev/null 2>&1; then
    herdr server reload-config >/dev/null 2>&1 || true
fi
