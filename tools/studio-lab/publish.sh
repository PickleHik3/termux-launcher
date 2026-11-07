#!/usr/bin/env bash
# Publishes the Studio lab to ~/reports/studio-lab/, served over Tailscale at
# https://amal-build.taild9c5dc.ts.net/reports/studio-lab/
# The lab fetches the app's own shader sources and recipe.json from shaders/.
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo="$(cd "$here/../.." && pwd)"
out="${STUDIO_LAB_OUT:-$HOME/reports/studio-lab}"

mkdir -p "$out/shaders"
cp "$here/index.html" "$here/lab.js" "$out/"
rm -f "$out/shaders/"*
cp "$repo/app/src/main/assets/studio/"* "$out/shaders/"
echo "Published to $out"
echo "https://amal-build.taild9c5dc.ts.net/reports/studio-lab/"
