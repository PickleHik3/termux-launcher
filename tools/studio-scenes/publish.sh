#!/bin/sh
# Copy the scene lab + the app's studio assets to the Tailscale reports server, with a fresh cache stamp.
set -e
here=$(cd "$(dirname "$0")" && pwd); assets="$here/../../app/src/main/assets/studio"
out="${1:-$HOME/reports/studio-scenes/lab}"; v=$(date +%s)
mkdir -p "$out/studio/scenes"
cp "$here/worker.js" "$out/"; sed "s#<script>#<script data-v=\"$v\">#" "$here/index.html" > "$out/index.html"
cp "$assets/scene.agsl" "$assets/scene-words.json" "$out/studio/"; cp -r "$assets/scenes/." "$out/studio/scenes/"
echo "https://amal-build.taild9c5dc.ts.net/reports/studio-scenes/lab/?v=$v"
