#!/usr/bin/env bash
# Makes an animated APNG hero picture for a tlstore item's pinned readme, from
# a short video or gif of it running.
#
#   scripts/tlstore/make-hero.sh <video|gif> <out.png>
#
# The clip is trimmed to its first 4 seconds, resampled to 12 fps and scaled
# to 600 px wide (height kept even, aspect preserved), and written as an APNG
# that loops forever. ffmpeg's apng encoder always writes each frame in full
# (it has no delta/blend-region option to turn on in the first place — checked
# with `ffmpeg -h muxer=apng` / `-h encoder=apng`), so nothing here relies on
# the partial-frame blend ops some APNG decoders in the item pages' rendering
# path do not support.
#
# The result is what a pinned `readme`/hero entry in items.tsv points a
# `launcher:`/`binaries:` source at (see docs/agents/tlstore-catalog.md).
set -euo pipefail

if [ $# -ne 2 ]; then
    echo "usage: scripts/tlstore/make-hero.sh <video|gif> <out.png>" >&2
    exit 2
fi

in="$1"
out="$2"

command -v ffmpeg >/dev/null 2>&1 || { echo "make-hero.sh needs ffmpeg installed" >&2; exit 1; }
[ -f "$in" ] || { echo "no such file: $in" >&2; exit 1; }

case "$out" in
    *.png) ;;
    *) echo "the output must end .png (it is an APNG): $out" >&2; exit 2 ;;
esac

mkdir -p "$(dirname "$out")"

if ! ffmpeg -y -v error -i "$in" -t 4 \
    -vf "fps=12,scale=600:-2:flags=lanczos" \
    -f apng -plays 0 \
    "$out"; then
    echo "ffmpeg failed to build $out from $in" >&2
    exit 1
fi

echo "wrote $out"
