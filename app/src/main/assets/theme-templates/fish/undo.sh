#!/usr/bin/env bash
# Removes the conf.d drop-in and the rendered theme file. apply.sh changed
# nothing else - no universal variables, no config.fish line - so deleting the
# drop-in is what puts the user's own theme choice back.
set -euo pipefail

rendered="${TERMUX_THEME_OUTPUT:?TERMUX_THEME_OUTPUT not set}"
dropin="${XDG_CONFIG_HOME:-$HOME/.config}/fish/conf.d/launcher-material-fish.fish"

rm -f -- "$dropin" "$rendered"

themes_dir="$(dirname "$rendered")"
rmdir -- "$themes_dir" 2>/dev/null || true
