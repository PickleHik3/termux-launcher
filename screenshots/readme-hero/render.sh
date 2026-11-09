#!/bin/sh
# Rebuild ../hero.webp from hero.html. Needs chromium and cwebp (libwebp) or python3-pillow.
set -e
cd "$(dirname "$0")"
chromium --headless=new --disable-gpu --hide-scrollbars --allow-file-access-from-files \
  --window-size=1600,1000 --virtual-time-budget=3000 --screenshot=/tmp/readme-hero.png \
  "file://$PWD/hero.html" >/dev/null 2>&1
python3 -c "from PIL import Image; Image.open('/tmp/readme-hero.png').convert('RGB').save('../hero.webp', quality=86, method=6)"
rm -f /tmp/readme-hero.png
echo "wrote $(cd .. && pwd)/hero.webp"
