#!/usr/bin/env bash
# Wires the rendered launcher-material palette into the user's starship.toml.
# Ported from noctalia shell (MIT) and adapted: no systemd/proc discovery, uses
# the TERMUX_THEME_* environment the launcher's hook runner provides.
set -euo pipefail

palette_file="${TERMUX_THEME_OUTPUT:?TERMUX_THEME_OUTPUT not set}"
marker_begin="# >>> launcher-material >>>"
marker_end="# <<< launcher-material <<<"

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

if [ ! -f "$palette_file" ]; then
    echo "starship: rendered palette not found at $palette_file" >&2
    exit 1
fi

mkdir -p "$(dirname "$config_file")"

tmp_file="$(mktemp "${config_file}.tmp.XXXXXX")"
cleanup() { rm -f "$tmp_file"; }
trap cleanup EXIT

if [ ! -f "$config_file" ]; then
    {
        echo 'palette = "launcher-material"'
        echo "$marker_begin"
        cat "$palette_file"
        echo "$marker_end"
    } >"$tmp_file"
else
    body_file="$(mktemp "${config_file}.body.XXXXXX")"
    cleanup_body() { rm -f "$tmp_file" "$body_file"; }
    trap cleanup_body EXIT

    # Strip a previous launcher-material block and any top-level palette= line;
    # everything else in the user's file is left exactly as it was.
    awk -v begin="$marker_begin" -v end="$marker_end" '
        $0 == begin { in_block = 1; next }
        in_block {
            if ($0 == end) { in_block = 0 }
            next
        }
        /^palette[[:space:]]*=/ { next }
        { print }
    ' "$config_file" >"$body_file"

    # Drop trailing blank lines so the leading blank line below does not accumulate.
    sed -i -e :a -e '/^\n*$/{$d;N;ba}' "$body_file"

    {
        if grep -qE '^"\$schema"' "$body_file"; then
            awk '
                /^"\$schema"/ {
                    print
                    if (!inserted) {
                        print "palette = \"launcher-material\""
                        inserted = 1
                    }
                    next
                }
                { print }
            ' "$body_file"
        else
            echo 'palette = "launcher-material"'
            cat "$body_file"
        fi
        echo "$marker_begin"
        cat "$palette_file"
        echo "$marker_end"
    } >"$tmp_file"

    rm -f "$body_file"
    trap cleanup EXIT
fi

# Only touch the live path when content actually changes, and write through a
# symlink instead of replacing it with mv/sed -i.
if [ ! -e "$config_file" ] && [ ! -L "$config_file" ]; then
    mv "$tmp_file" "$config_file"
    trap - EXIT
elif ! cmp -s "$config_file" "$tmp_file"; then
    cat "$tmp_file" >"$config_file"
fi
