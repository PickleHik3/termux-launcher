#!/usr/bin/env bash
# Removes everything apply.sh and setup.sh added: the fish drop-ins, this
# template's own POSH_THEME marker block from ~/.bashrc / ~/.zshrc
# (apply.sh) plus any block still written under the marker every template
# used to share before each got its own (upgrade path), the per-shell init
# marker block from whichever rc file has one (setup.sh), and the rendered
# theme.
set -euo pipefail

rendered="${TERMUX_THEME_OUTPUT:?TERMUX_THEME_OUTPUT not set}"
own_begin="# >>> launcher-material ohmyposh >>>"
own_end="# <<< launcher-material ohmyposh <<<"
old_begin="# >>> launcher-material >>>"
old_end="# <<< launcher-material <<<"
fish_dropin="${XDG_CONFIG_HOME:-$HOME/.config}/fish/conf.d/launcher-material-ohmyposh.fish"
fish_init="${XDG_CONFIG_HOME:-$HOME/.config}/fish/conf.d/launcher-material-ohmyposh-init.fish"

rm -f -- "$fish_dropin" "$fish_init"

remove_block() {
    local rc_file="$1" begin="$2" end="$3"
    [ -f "$rc_file" ] || return 0
    local tmp_file
    tmp_file="$(mktemp "${rc_file}.tmp.XXXXXX")"
    awk -v begin="$begin" -v end="$end" '
        $0 == begin { in_block = 1; next }
        in_block && $0 == end { in_block = 0; next }
        in_block { next }
        { print }
    ' "$rc_file" >"$tmp_file"
    if ! cmp -s "$rc_file" "$tmp_file"; then
        cat "$tmp_file" >"$rc_file"
    fi
    rm -f "$tmp_file"
}

remove_block "$HOME/.bashrc" "$own_begin" "$own_end"
remove_block "$HOME/.zshrc" "$own_begin" "$own_end"
remove_block "$HOME/.bashrc" "$old_begin" "$old_end"
remove_block "$HOME/.zshrc" "$old_begin" "$old_end"
remove_block "$HOME/.bashrc" "# >>> launcher-material bash >>>" "# <<< launcher-material bash <<<"
remove_block "$HOME/.zshrc" "# >>> launcher-material zsh >>>" "# <<< launcher-material zsh <<<"

# setup.sh (unlike apply.sh) creates a missing rc file, so if stripping its
# block leaves nothing behind, remove the file it created rather than leave
# an empty one where there was none before.
[ -f "$HOME/.bashrc" ] && [ ! -s "$HOME/.bashrc" ] && rm -f -- "$HOME/.bashrc"
[ -f "$HOME/.zshrc" ] && [ ! -s "$HOME/.zshrc" ] && rm -f -- "$HOME/.zshrc"

rm -f -- "$rendered"
