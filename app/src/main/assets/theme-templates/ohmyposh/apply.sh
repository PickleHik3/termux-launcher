#!/usr/bin/env bash
# Exports POSH_THEME to the rendered launcher-material oh-my-posh theme: a
# fresh fish conf.d drop-in (safe to create), and a marker block added to
# ~/.bashrc and ~/.zshrc only when that rc file already exists - this never
# creates a shell rc file that was not already there. Any shell whose own
# oh-my-posh init honours $POSH_THEME picks this up.
set -euo pipefail

rendered="${TERMUX_THEME_OUTPUT:?TERMUX_THEME_OUTPUT not set}"
marker_begin="# >>> launcher-material >>>"
marker_end="# <<< launcher-material <<<"

fish_dropin="${XDG_CONFIG_HOME:-$HOME/.config}/fish/conf.d/launcher-material-ohmyposh.fish"

mkdir -p "$(dirname "$fish_dropin")"
fish_tmp="$(mktemp "${fish_dropin}.tmp.XXXXXX")"
printf 'set -gx POSH_THEME "%s"\n' "$rendered" >"$fish_tmp"
if [ ! -f "$fish_dropin" ] || ! cmp -s "$fish_dropin" "$fish_tmp"; then
    cat "$fish_tmp" >"$fish_dropin"
fi
rm -f "$fish_tmp"

add_marker_block() {
    local rc_file="$1" line="$2"
    [ -f "$rc_file" ] || return 0

    local had_block=0
    if grep -qxF -- "$marker_begin" "$rc_file"; then
        had_block=1
    fi

    local tmp_file
    tmp_file="$(mktemp "${rc_file}.tmp.XXXXXX")"
    cat "$rc_file" >"$tmp_file"

    if [ "$had_block" -eq 0 ]; then
        if [ -s "$tmp_file" ] && [ -n "$(tail -c1 "$tmp_file")" ]; then
            printf '\n' >>"$tmp_file"
        fi
        {
            echo "$marker_begin"
            printf '%s\n' "$line"
            echo "$marker_end"
        } >>"$tmp_file"
    fi

    if ! cmp -s "$rc_file" "$tmp_file"; then
        cat "$tmp_file" >"$rc_file"
    fi
    rm -f "$tmp_file"
}

posh_export="export POSH_THEME=$(printf '%q' "$rendered")"
add_marker_block "$HOME/.bashrc" "$posh_export"
add_marker_block "$HOME/.zshrc" "$posh_export"
