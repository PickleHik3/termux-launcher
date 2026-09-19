#!/usr/bin/env bash
# Re-encode the recorded help-guide clips (art/help-guide/*.mp4, the originals) into the
# APK assets (app/src/main/assets/help-guide/). Decision 2026-09-19: H.264, CRF 30, 30 fps kept,
# no audio, faststart. Rerun after re-recording a clip; the manifest is copied as-is.
set -euo pipefail
cd "$(dirname "$0")/.."
src=art/help-guide
dst=app/src/main/assets/help-guide
mkdir -p "$dst"
for f in "$src"/*.mp4; do
  n=$(basename "$f")
  ffmpeg -v error -y -i "$f" -an \
    -c:v libx264 -preset veryslow -crf 30 -tune stillimage -g 300 \
    -pix_fmt yuv420p -movflags +faststart "$dst/$n"
done
cp "$src/manifest.json" "$dst/manifest.json"
du -sh "$dst"
