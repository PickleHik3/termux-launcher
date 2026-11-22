#!/bin/sh
# Legibility card: prints the pairs of project-docs/active/legibility-contract-2026-10-27.md in a
# terminal pane, for a screenshot and the pixel script. Run it in a Termux pane over a wallpaper with
# a bright patch (pong's cloud wallpaper) and read the ratios off the screenshot.
#
#   sh legibility-card.sh

esc=$(printf '\033')
sgr() { printf '%s[%sm' "$esc" "$1"; }
reset() { sgr 0; }

echo "-- dialog panel row: black, red, blue, bold blue on ANSI white (>= 3:1 each)"
for code in "30;47" "31;47" "34;47" "1;34;47"; do
    sgr "$code"; printf ' %-8s sample text ' "$code"; reset
done
printf '\n'

echo "-- accents on the background: normal 31-36, bright 91-96"
for code in 31 32 33 34 35 36; do
    sgr "$code"; printf ' %s sample ' "$code"; reset
done
printf '\n'
for code in 91 92 93 94 95 96; do
    sgr "$code"; printf ' %s sample ' "$code"; reset
done
printf '\n'

echo "-- grey (slot 8) and faint"
sgr 90; echo "90: grey text, comments and autosuggestions"; reset
sgr 2; echo "2: faint text"; reset

echo "-- twelve alternating grey and plain lines across the pane"
i=1
while [ "$i" -le 12 ]; do
    if [ $((i % 2)) -eq 1 ]; then
        sgr 90; printf 'grey line %02d the quick brown fox jumps over the lazy dog\n' "$i"; reset
    else
        printf 'plain line %02d the quick brown fox jumps over the lazy dog\n' "$i"
    fi
    i=$((i + 1))
done
