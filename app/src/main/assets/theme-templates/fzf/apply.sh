#!/usr/bin/env bash
# Loads the rendered launcher-material FZF_DEFAULT_OPTS: a fresh fish conf.d
# drop-in (derived from the rendered .sh, since fish cannot source a bash
# export line), and a marker block added to ~/.bashrc and ~/.zshrc only when
# that rc file already exists - this never creates a shell rc file that was
# not already there.
set -euo pipefail

rendered="${TERMUX_THEME_OUTPUT:?TERMUX_THEME_OUTPUT not set}"
# Per-template marker: fzf, lazygit and ohmyposh all write into the same
# ~/.bashrc and ~/.zshrc, so a shared marker would let one template's undo
# remove another's block (or its apply skip adding its own block because it
# mistook another template's block for its own).
marker_begin="# >>> launcher-material fzf >>>"
marker_end="# <<< launcher-material fzf <<<"
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

    local tmp_file
    tmp_file="$(mktemp "${rc_file}.tmp.XXXXXX")"

    # Strip any existing block under our own marker first, then always
    # append a fresh one, so a stale or hand-mangled line is repaired on the
    # next apply instead of being left in place. Rerunning stays idempotent:
    # when the block already matches, the stripped-then-reappended content
    # is byte-identical to what was there.
    awk -v begin="$marker_begin" -v end="$marker_end" '
        $0 == begin { in_block = 1; next }
        in_block && $0 == end { in_block = 0; next }
        in_block { next }
        { print }
    ' "$rc_file" >"$tmp_file"

    if [ -s "$tmp_file" ] && [ -n "$(tail -c1 "$tmp_file")" ]; then
        printf '\n' >>"$tmp_file"
    fi
    {
        echo "$marker_begin"
        printf '%s\n' "$line"
        echo "$marker_end"
    } >>"$tmp_file"

    if ! cmp -s "$rc_file" "$tmp_file"; then
        cat "$tmp_file" >"$rc_file"
    fi
    rm -f "$tmp_file"
}

source_line="source $(printf '%q' "$rendered")"
add_marker_block "$HOME/.bashrc" "$source_line"
add_marker_block "$HOME/.zshrc" "$source_line"
