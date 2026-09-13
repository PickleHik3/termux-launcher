#!/usr/bin/env bash
# Removes the launcher-material block from config.toml and restores whatever
# theme line it displaced; removes the rendered theme file.
# Ported from noctalia shell (MIT), extended to restore the previous line.
set -euo pipefail

config_file="${XDG_CONFIG_HOME:-$HOME/.config}/helix/config.toml"
theme_output="${TERMUX_THEME_OUTPUT:?TERMUX_THEME_OUTPUT not set}"
marker_begin="# >>> launcher-material >>>"
marker_end="# <<< launcher-material <<<"
disable_suffix=" # >>> launcher-material: previous >>>"

if [ -f "$config_file" ]; then
    tmp_file="$(mktemp "${config_file}.tmp.XXXXXX")"
    trap 'rm -f "$tmp_file"' EXIT

    awk -v begin="$marker_begin" -v end="$marker_end" -v suffix="$disable_suffix" '
        $0 == begin { in_block = 1; next }
        in_block && $0 == end { in_block = 0; next }
        in_block { next }
        {
            line = $0
            suf_len = length(suffix)
            if (length(line) >= suf_len && substr(line, length(line) - suf_len + 1) == suffix) {
                restored = substr(line, 1, length(line) - suf_len)
                sub(/^#/, "", restored)
                print restored
                next
            }
            print
        }
    ' "$config_file" >"$tmp_file"

    if ! cmp -s "$config_file" "$tmp_file"; then
        cat "$tmp_file" >"$config_file"
    fi

    if [ ! -s "$config_file" ]; then
        rm -f -- "$config_file"
    fi
fi

rm -f -- "$theme_output"
