#!/usr/bin/env bash
# Points yazi's theme.toml at the rendered launcher-material flavor. Known
# limitation: if theme.toml already has an active [flavor] table, appending
# a second one makes the file invalid TOML - yazi has no merge-safe way to
# override just the flavor keys, so this only handles a theme.toml with no
# live [flavor] section yet (the common case: yazi ships none by default).
set -euo pipefail

config_file="${XDG_CONFIG_HOME:-$HOME/.config}/yazi/theme.toml"
marker_begin="# >>> launcher-material >>>"
marker_end="# <<< launcher-material <<<"

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
        echo "[flavor]"
        echo 'dark = "launcher-material"'
        echo 'light = "launcher-material"'
        echo "$marker_end"
    } >>"$tmp_file"
fi

if [ ! -e "$config_file" ] && [ ! -L "$config_file" ]; then
    mv "$tmp_file" "$config_file"
    trap - EXIT
elif ! cmp -s "$config_file" "$tmp_file"; then
    cat "$tmp_file" >"$config_file"
fi
