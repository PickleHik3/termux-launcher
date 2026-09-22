#!/usr/bin/env bash
# Installs the launcher-material Neovim colorscheme and its plugin spec
# (write-if-changed: this template owns the two files below), and wires the
# colorscheme in for a plain config with a marker block in init.lua. The
# rendered file is the palette module itself ($TERMUX_THEME_OUTPUT,
# lua/launcher/material_palette.lua), which carries both the dark and the
# light palette; the spec picks LazyVim or AstroNvim on its own at nvim
# startup, so this script only decides whether the init.lua fallback is also
# needed.
set -euo pipefail

theme_dir="${TERMUX_THEME_DIR:?TERMUX_THEME_DIR not set}"
config_dir="${XDG_CONFIG_HOME:-$HOME/.config}/nvim"
marker_begin="-- >>> launcher-material >>>"
marker_end="-- <<< launcher-material <<<"

write_if_changed() {
    local target="$1" source="$2"
    mkdir -p "$(dirname "$target")"
    if [ ! -f "$target" ] || ! cmp -s "$source" "$target"; then
        cat "$source" >"$target"
    fi
}

write_if_changed "$config_dir/colors/launcher-material.lua" "$theme_dir/colors/launcher-material.lua"
write_if_changed "$config_dir/lua/plugins/launcher-material.lua" "$theme_dir/lua/plugins/launcher-material.lua"

file_contains() {
    [ -f "$1" ] && grep -q -- "$2" "$1" 2>/dev/null
}

is_lazyvim() { file_contains "$config_dir/lua/config/lazy.lua" "LazyVim"; }
is_astronvim() {
    file_contains "$config_dir/lua/lazy_setup.lua" "AstroNvim" ||
        file_contains "$config_dir/lua/community.lua" "AstroNvim"
}

if ! is_lazyvim && ! is_astronvim; then
    init_file="$config_dir/init.lua"
    mkdir -p "$(dirname "$init_file")"

    had_block=0
    if [ -f "$init_file" ] && grep -qxF -- "$marker_begin" "$init_file"; then
        had_block=1
    fi

    tmp_file="$(mktemp "${init_file}.tmp.XXXXXX")"
    trap 'rm -f "$tmp_file"' EXIT

    if [ -f "$init_file" ]; then
        cat "$init_file" >"$tmp_file"
    else
        : >"$tmp_file"
    fi

    if [ "$had_block" -eq 0 ]; then
        if [ -s "$tmp_file" ] && [ -n "$(tail -c1 "$tmp_file")" ]; then
            printf '\n' >>"$tmp_file"
        fi
        {
            echo "$marker_begin"
            echo 'pcall(vim.cmd.colorscheme, "launcher-material")'
            echo "$marker_end"
        } >>"$tmp_file"
    fi

    if [ ! -e "$init_file" ] && [ ! -L "$init_file" ]; then
        mv "$tmp_file" "$init_file"
        trap - EXIT
    elif ! cmp -s "$init_file" "$tmp_file"; then
        cat "$tmp_file" >"$init_file"
    fi
fi
