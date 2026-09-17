#!/usr/bin/env bash
# Removes the rendered palette module and the init.lua marker block. The
# colorscheme and its plugin spec are removed only if each is still
# byte-identical to the template's own copy, so a user's hand edit survives.
set -euo pipefail

theme_dir="${TERMUX_THEME_DIR:?TERMUX_THEME_DIR not set}"
rendered="${TERMUX_THEME_OUTPUT:?TERMUX_THEME_OUTPUT not set}"
config_dir="${XDG_CONFIG_HOME:-$HOME/.config}/nvim"
marker_begin="-- >>> launcher-material >>>"
marker_end="-- <<< launcher-material <<<"

remove_if_unedited() {
    local target="$1" source="$2"
    if [ -f "$target" ] && cmp -s "$target" "$source"; then
        rm -f -- "$target"
    fi
}

remove_if_unedited "$config_dir/colors/launcher-material.lua" "$theme_dir/colors/launcher-material.lua"
remove_if_unedited "$config_dir/lua/plugins/launcher-material.lua" "$theme_dir/lua/plugins/launcher-material.lua"

init_file="$config_dir/init.lua"
if [ -f "$init_file" ]; then
    tmp_file="$(mktemp "${init_file}.tmp.XXXXXX")"
    trap 'rm -f "$tmp_file"' EXIT
    awk -v begin="$marker_begin" -v end="$marker_end" '
        $0 == begin { in_block = 1; next }
        in_block && $0 == end { in_block = 0; next }
        in_block { next }
        { print }
    ' "$init_file" >"$tmp_file"
    if ! cmp -s "$init_file" "$tmp_file"; then
        cat "$tmp_file" >"$init_file"
    fi
    if [ ! -s "$init_file" ]; then
        rm -f -- "$init_file"
    fi
fi

rm -f -- "$rendered"
