#!/usr/bin/env bash
# Points LG_CONFIG_FILE at the user's own lazygit config followed by the
# rendered launcher-material one, so the user's settings win: a fresh fish
# conf.d drop-in (safe to create), and a marker block added to ~/.bashrc and
# ~/.zshrc only when that rc file already exists.
set -euo pipefail

rendered="${TERMUX_THEME_OUTPUT:?TERMUX_THEME_OUTPUT not set}"
user_config="${XDG_CONFIG_HOME:-$HOME/.config}/lazygit/config.yml"
marker_begin="# >>> launcher-material >>>"
marker_end="# <<< launcher-material <<<"
fish_dropin="${XDG_CONFIG_HOME:-$HOME/.config}/fish/conf.d/launcher-material-lazygit.fish"

config_list="$user_config:$rendered"

mkdir -p "$(dirname "$fish_dropin")"
fish_tmp="$(mktemp "${fish_dropin}.tmp.XXXXXX")"
printf 'set -gx LG_CONFIG_FILE "%s"\n' "$config_list" >"$fish_tmp"
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

export_line="export LG_CONFIG_FILE=$(printf '%q' "$config_list")"
add_marker_block "$HOME/.bashrc" "$export_line"
add_marker_block "$HOME/.zshrc" "$export_line"
