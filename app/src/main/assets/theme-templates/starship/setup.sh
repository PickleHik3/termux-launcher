#!/usr/bin/env bash
# The app copies `bash "<dir>/setup.sh"` to the clipboard for the user to
# paste into their interactive shell, so this script detects which shell
# pasted it rather than assuming one, then adds the starship init for that
# shell only: bash/zsh via a marker block in their rc file, fish via its own
# conf.d drop-in. Idempotent: rerunning changes nothing.
set -euo pipefail

detect_shell() {
    if [ -n "${TERMUX_THEME_SHELL:-}" ]; then
        printf '%s' "$TERMUX_THEME_SHELL"
        return 0
    fi
    if [ -r "/proc/$PPID/comm" ]; then
        comm="$(cat "/proc/$PPID/comm" 2>/dev/null || true)"
        comm="${comm%%$'\n'*}"
        if [ -n "$comm" ]; then
            printf '%s' "$comm"
            return 0
        fi
    fi
    if [ -n "${SHELL:-}" ]; then
        basename "$SHELL"
        return 0
    fi
    printf ''
}

shell_name="$(detect_shell)"

case "$shell_name" in
    bash | zsh | fish) ;;
    *)
        echo "launcher-material: paste this into bash, zsh or fish (got '$shell_name')" >&2
        exit 1
        ;;
esac

marker_begin="# >>> launcher-material $shell_name >>>"
marker_end="# <<< launcher-material $shell_name <<<"

add_init_block() {
    local rc_file="$1" init_line="$2"

    if [ -f "$rc_file" ] && grep -qxF -- "$marker_begin" "$rc_file"; then
        echo "launcher-material: already set up in $rc_file"
        return 0
    fi

    if [ -f "$rc_file" ] && grep -q 'starship init' "$rc_file"; then
        echo "launcher-material: $rc_file already runs its own starship init; installing ours too, the last init wins"
    fi

    mkdir -p "$(dirname "$rc_file")"
    local tmp_file
    tmp_file="$(mktemp "${rc_file}.tmp.XXXXXX")"

    if [ -f "$rc_file" ]; then
        cat "$rc_file" >"$tmp_file"
    else
        : >"$tmp_file"
    fi

    if [ -s "$tmp_file" ] && [ -n "$(tail -c1 "$tmp_file")" ]; then
        printf '\n' >>"$tmp_file"
    fi
    {
        echo "$marker_begin"
        printf '%s\n' "$init_line"
        echo "$marker_end"
    } >>"$tmp_file"

    if [ ! -e "$rc_file" ] && [ ! -L "$rc_file" ]; then
        mv "$tmp_file" "$rc_file"
    else
        cat "$tmp_file" >"$rc_file"
        rm -f "$tmp_file"
    fi

    echo "launcher-material: added the starship init to $rc_file"
}

case "$shell_name" in
    bash)
        add_init_block "$HOME/.bashrc" 'eval "$(starship init bash)"'
        echo "Open a new terminal (or run 'exec bash') to see the prompt."
        ;;
    zsh)
        add_init_block "$HOME/.zshrc" 'eval "$(starship init zsh)"'
        echo "Open a new terminal (or run 'exec zsh') to see the prompt."
        ;;
    fish)
        fish_init="${XDG_CONFIG_HOME:-$HOME/.config}/fish/conf.d/launcher-material-starship-init.fish"
        mkdir -p "$(dirname "$fish_init")"
        tmp_file="$(mktemp "${fish_init}.tmp.XXXXXX")"
        printf 'starship init fish | source\n' >"$tmp_file"
        if [ -f "$fish_init" ] && cmp -s "$fish_init" "$tmp_file"; then
            rm -f "$tmp_file"
            echo "launcher-material: already set up in $fish_init"
        else
            cat "$tmp_file" >"$fish_init"
            rm -f "$tmp_file"
            echo "launcher-material: added the starship init to $fish_init"
        fi
        echo "Open a new terminal to see the prompt."
        ;;
esac
