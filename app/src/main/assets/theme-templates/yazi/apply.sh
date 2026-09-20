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

tmp_file="$(mktemp "${config_file}.tmp.XXXXXX")"
trap 'rm -f "$tmp_file"' EXIT

# Strip any existing marker block first, then always append a fresh one, so
# a stale or hand-mangled block is repaired on the next apply instead of
# being left in place. Rerunning stays idempotent: when the block already
# matches, the stripped-then-reappended content is byte-identical to what
# was there.
if [ -f "$config_file" ]; then
    awk -v begin="$marker_begin" -v end="$marker_end" '
        $0 == begin { in_block = 1; next }
        in_block && $0 == end { in_block = 0; next }
        in_block { next }
        { print }
    ' "$config_file" >"$tmp_file"
else
    : >"$tmp_file"
fi

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

if [ ! -e "$config_file" ] && [ ! -L "$config_file" ]; then
    mv "$tmp_file" "$config_file"
    trap - EXIT
elif ! cmp -s "$config_file" "$tmp_file"; then
    cat "$tmp_file" >"$config_file"
fi
