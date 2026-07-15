#!/usr/bin/env bash
# Dev-loop installer: strips the bundled bootstrap zips from the debug APK to
# cut adb transfer from ~189MB to ~50MB, re-signs with the repo debug key, and
# installs. Only for upgrades on devices that already have the Termux prefix
# extracted — a fresh install from this APK would have no bootstrap.
set -euo pipefail

DEVICE="${DEVICE:-100.101.173.85:5555}"
REPO="$(cd "$(dirname "$0")/.." && pwd)"
APK="$REPO/app/build/outputs/apk/debug/termux-app_apt-android-7-debug_arm64-v8a.apk"
BT="$(ls -d "${ANDROID_HOME:-$HOME/android-sdk}"/build-tools/* | sort -V | tail -1)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

python3 - "$APK" "$WORK/stripped.apk" <<'PY'
import sys, zipfile
zin = zipfile.ZipFile(sys.argv[1])
with zipfile.ZipFile(sys.argv[2], "w") as zout:
    for item in zin.infolist():
        if item.filename.startswith("assets/bootstrap-"):
            continue
        zout.writestr(item, zin.read(item.filename), compress_type=item.compress_type)
PY
"$BT/zipalign" -f 4 "$WORK/stripped.apk" "$WORK/aligned.apk"
"$BT/apksigner" sign --ks "$REPO/app/testkey_untrusted.jks" \
  --ks-key-alias alias --ks-pass pass:xrj45yWGLbsO7W0v --key-pass pass:xrj45yWGLbsO7W0v \
  "$WORK/aligned.apk"

ls -la "$WORK/aligned.apk"
adb connect "$DEVICE" >/dev/null 2>&1 || true
adb -s "$DEVICE" install -r "$WORK/aligned.apk"
