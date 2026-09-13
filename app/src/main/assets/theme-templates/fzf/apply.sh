#!/usr/bin/env bash
# Loads the rendered launcher-material FZF_DEFAULT_OPTS: a fresh fish conf.d
# drop-in (derived from the rendered .sh, since fish cannot source a bash
# export line), and a marker block added to ~/.bashrc and ~/.zshrc only when
# that rc file already exists - this never creates a shell rc file that was
# not already there.
set -euo pipefail

rendered="${TERMUX_THEME_OUTPUT:?TERMUX_THEME_OUTPUT not set}"
marker_begin="# >>> launcher-material >>>"
marker_end="# <<< launcher-material <<<"
fish_dropin="${XDG_CONFIG_HOME:-$HOME/.config}/fish/conf.d/launcher-material-fzf.fish"

if [ ! -f "$rendered" ]; then
    echo "fzf: rendered options not found at $rendered" >&2
    exit 1
fi

opts="$(sed -n 's/^export FZF_DEFAULT_OPTS="\(.*\)"$/\1/p' "$rendered" | head -n1)"
if [ -z "$opts" ]; then
    echo "fzf: could not read FZF_DEFAULT_OPTS from $rendered" >&2
    exit 1
fi

mkdir -p "$(dirname "$fish_dropin")"
fish_tmp="$(mktemp "${fish_dropin}.tmp.XXXXXX")"
printf "set -gx FZF_DEFAULT_OPTS '%s'\n" "$opts" >"$fish_tmp"
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

source_line="source $(printf '%q' "$rendered")"
add_marker_block "$HOME/.bashrc" "$source_line"
add_marker_block "$HOME/.zshrc" "$source_line"
