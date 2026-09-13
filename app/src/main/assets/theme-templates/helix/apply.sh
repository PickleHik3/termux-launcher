#!/usr/bin/env bash
# Sets `theme = "launcher-material"` in helix's config.toml, keeping any
# previous theme line as a commented, tagged marker so undo.sh can restore it.
# New for the launcher: noctalia ships no helix apply hook, only an undo one.
set -euo pipefail

config_file="${XDG_CONFIG_HOME:-$HOME/.config}/helix/config.toml"
marker_begin="# >>> launcher-material >>>"
marker_end="# <<< launcher-material <<<"
disable_suffix=" # >>> launcher-material: previous >>>"
theme_line='theme = "launcher-material"'

mkdir -p "$(dirname "$config_file")"

if [ ! -e "$config_file" ] && [ ! -L "$config_file" ]; then
    {
        echo "$marker_begin"
        echo "$theme_line"
        echo "$marker_end"
    } >"$config_file"
    exit 0
fi

had_block=0
if grep -qxF -- "$marker_begin" "$config_file" 2>/dev/null; then
    had_block=1
fi

tmp_file="$(mktemp "${config_file}.tmp.XXXXXX")"
trap 'rm -f "$tmp_file"' EXIT

# Copy the file through unchanged, except: comment out the first active
# top-level `theme = ...` line found outside any existing launcher-material
# block, tagging it so undo.sh can find and restore it exactly.
awk -v begin="$marker_begin" -v end="$marker_end" -v suffix="$disable_suffix" '
    $0 == begin { in_block = 1; print; next }
    in_block && $0 == end { in_block = 0; print; next }
    in_block { print; next }
    !disabled && $0 ~ /^[[:space:]]*theme[[:space:]]*=/ {
        print "#" $0 suffix
        disabled = 1
        next
    }
    { print }
' "$config_file" >"$tmp_file"

if [ "$had_block" -eq 0 ]; then
    # Make sure the appended block starts on its own line.
    if [ -s "$tmp_file" ] && [ -n "$(tail -c1 "$tmp_file")" ]; then
        printf '\n' >>"$tmp_file"
    fi
    {
        echo "$marker_begin"
        echo "$theme_line"
        echo "$marker_end"
    } >>"$tmp_file"
fi

if [ ! -e "$config_file" ] && [ ! -L "$config_file" ]; then
    mv "$tmp_file" "$config_file"
    trap - EXIT
elif ! cmp -s "$config_file" "$tmp_file"; then
    cat "$tmp_file" >"$config_file"
fi
