#!/usr/bin/env bash
# Removes exactly what apply.sh added to starship.toml, whatever setup.sh
# added (the per-shell init marker block in ~/.bashrc / ~/.zshrc, and the
# fish conf.d drop-in), and the rendered palette.
# Ported from noctalia shell (MIT), adapted for the launcher's hook environment.
set -euo pipefail

marker_begin="# >>> launcher-material >>>"
marker_end="# <<< launcher-material <<<"
# Must match apply.sh's tag exactly: it is how a user's own displaced
# palette= line gets found and restored below.
disable_suffix=" # >>> launcher-material: previous >>>"
fish_init="${XDG_CONFIG_HOME:-$HOME/.config}/fish/conf.d/launcher-material-starship-init.fish"

expand_tilde() {
    case "$1" in
        "~") printf '%s' "$HOME" ;;
        "~/"*) printf '%s' "$HOME/${1#~/}" ;;
        *) printf '%s' "$1" ;;
    esac
}

config_file() {
    if [ -n "${STARSHIP_CONFIG:-}" ]; then
        expand_tilde "$STARSHIP_CONFIG"
    else
        printf '%s' "${XDG_CONFIG_HOME:-$HOME/.config}/starship.toml"
    fi
}

config_file="$(config_file)"

if [ -f "$config_file" ]; then
    tmp_file="$(mktemp "${config_file}.tmp.XXXXXX")"
    trap 'rm -f "$tmp_file"' EXIT
    awk -v begin="$marker_begin" -v end="$marker_end" -v suffix="$disable_suffix" '
        $0 == begin { in_block = 1; next }
        in_block && $0 == end { in_block = 0; next }
        in_block { next }
        /^[[:space:]]*palette[[:space:]]*=[[:space:]]*"launcher-material(-dark|-light)?"[[:space:]]*$/ { next }
        {
            line = $0
            suf_len = length(suffix)
            if (length(line) >= suf_len && substr(line, length(line) - suf_len + 1) == suffix) {
                restored = substr(line, 1, length(line) - suf_len)
                sub(/^#/, "", restored)
                print restored
                next
            }
            print line
        }
    ' "$config_file" >"$tmp_file"
    if ! cmp -s "$config_file" "$tmp_file"; then
        cat "$tmp_file" >"$config_file"
    fi
    # apply.sh creates a missing starship.toml from scratch, so if stripping
    # our additions leaves nothing behind, remove the file it created rather
    # than leave an empty one where there was none before.
    [ -s "$config_file" ] || rm -f -- "$config_file"
fi

rm -f -- "$fish_init"

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

remove_block "$HOME/.bashrc" "# >>> launcher-material bash >>>" "# <<< launcher-material bash <<<"
remove_block "$HOME/.zshrc" "# >>> launcher-material zsh >>>" "# <<< launcher-material zsh <<<"

# setup.sh (unlike apply.sh) creates a missing rc file, so if stripping its
# block leaves nothing behind, remove the file it created rather than leave
# an empty one where there was none before.
[ -f "$HOME/.bashrc" ] && [ ! -s "$HOME/.bashrc" ] && rm -f -- "$HOME/.bashrc"
[ -f "$HOME/.zshrc" ] && [ ! -s "$HOME/.zshrc" ] && rm -f -- "$HOME/.zshrc"

rm -f -- "${TERMUX_THEME_OUTPUT:?TERMUX_THEME_OUTPUT not set}"
