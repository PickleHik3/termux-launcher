#!/usr/bin/env bash
# Selects the rendered launcher-material theme for every fish session, through
# a conf.d drop-in of our own - the same shape fzf, lazygit and oh-my-posh use.
#
# `fish_config theme choose` rather than `theme save`: save writes the colours
# into fish's universal variables, which fish's own documentation says freezes
# them, and freezing them is exactly what this template must not do - the
# theme file carries a [light] and a [dark] section and fish is what switches
# between them when the terminal reports its appearance. `choose` leaves that
# switching in place, and it touches nothing of the user's, so undo.sh only has
# to delete this file for their own theme choice to apply again.
set -euo pipefail

rendered="${TERMUX_THEME_OUTPUT:?TERMUX_THEME_OUTPUT not set}"
dropin="${XDG_CONFIG_HOME:-$HOME/.config}/fish/conf.d/launcher-material-fish.fish"

if [ ! -f "$rendered" ]; then
    echo "fish: rendered theme not found at $rendered" >&2
    exit 1
fi

mkdir -p "$(dirname "$dropin")"
tmp_file="$(mktemp "${dropin}.tmp.XXXXXX")"
trap 'rm -f "$tmp_file"' EXIT

cat >"$tmp_file" <<'DROPIN'
# launcher-material - written by Termux Launcher, do not edit.
# Removing this file puts your own theme choice back.
if status is-interactive
    and type -q fish_config
    fish_config theme choose launcher-material
end
DROPIN

if [ ! -f "$dropin" ] || ! cmp -s "$dropin" "$tmp_file"; then
    cat "$tmp_file" >"$dropin"
fi
rm -f "$tmp_file"
trap - EXIT

# Where fish is installed, load the theme once here too: it costs nothing and
# it is the earliest point a malformed theme file would show up.
if command -v fish >/dev/null 2>&1; then
    fish -c 'fish_config theme choose launcher-material' >/dev/null 2>&1 \
        || echo "fish: could not load the launcher-material theme" >&2
fi
