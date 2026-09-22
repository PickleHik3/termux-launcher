#!/usr/bin/env bash
# Re-encode the recorded help-guide clips (art/help-guide/*.mp4, the originals) into the
# APK assets (app/src/main/assets/help-guide/). Decision 2026-09-19: H.264, 30 fps kept, no audio,
# faststart. Phone takes are kept at their native 1080 px width so the Help screen shows them
# without upscaling; they take a higher CRF (40) plus text-friendly x264 tuning so the file stays
# the size a 600 px take costs at CRF 30. Rerun after re-recording; the manifest is copied as-is.
set -euo pipefail
cd "$(dirname "$0")/.."
src=art/help-guide
dst=app/src/main/assets/help-guide
mkdir -p "$dst"
for f in "$src"/*.mp4; do
  n=$(basename "$f")
  w=$(ffprobe -v error -select_streams v:0 -show_entries stream=width -of csv=p=0 "$f")
  if [ "$w" -ge 1000 ]; then
    ffmpeg -v error -y -i "$f" -an \
      -c:v libx264 -preset veryslow -crf 40 -tune stillimage -g 300 \
      -x264-params aq-mode=3:aq-strength=1.3:deblock=-2,-2 \
      -pix_fmt yuv420p -movflags +faststart "$dst/$n"
  else
    ffmpeg -v error -y -i "$f" -an \
      -c:v libx264 -preset veryslow -crf 30 -tune stillimage -g 300 \
      -pix_fmt yuv420p -movflags +faststart "$dst/$n"
  fi
done
cp "$src/manifest.json" "$dst/manifest.json"
du -sh "$dst"
