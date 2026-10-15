#!/usr/bin/env bash
# Dev-loop installer: strips the bundled bootstrap zips from the staging APK to
# cut adb transfer from ~189MB to ~50MB, re-signs with the repo debug key, and
# installs. Only for upgrades on devices that already have the Termux prefix
# extracted — a fresh install from this APK would have no bootstrap.
#
# Staging (./gradlew :app:assembleStaging) is the release build's R8 shrinking,
# still debuggable. BUILD_TYPE=debug installs the unshrunk debug APK instead.
# BUILD_TYPE=release is what pong runs: release builds have no ABI splits and come out
# unsigned without the CI's TERMUX_RELEASE_* variables, so this takes the universal APK,
# keeps only arm64-v8a's native libs, drops the bootstraps and signs it with the same test
# key every build uses. It builds the release APK itself, versioned
# <versionName>+dev.<sha> (".dirty" with uncommitted changes) so About tells it apart;
# TERMUX_APP_VERSION_NAME overrides the version, NO_BUILD=1 installs the last build as is.
#
# Always installs with --no-incremental (an incremental install once left the launcher
# dead, black home screen), then checks the launcher process is up.
set -euo pipefail

DEVICE="${DEVICE:-127.0.0.1:5555}"
BUILD_TYPE="${BUILD_TYPE:-staging}"
REPO="$(cd "$(dirname "$0")/.." && pwd)"
ABI_SUFFIX=arm64-v8a; [ "$BUILD_TYPE" = release ] && ABI_SUFFIX=universal
if [ "$BUILD_TYPE" = release ] && [ -z "${NO_BUILD:-}" ]; then
  if [ -z "${TERMUX_APP_VERSION_NAME:-}" ]; then
    base="$(sed -n 's/^ *versionName "\(.*\)"/\1/p' "$REPO/app/build.gradle" | head -1)"
    sha="$(git -C "$REPO" rev-parse --short=9 HEAD)"
    [ -z "$(git -C "$REPO" status --porcelain --untracked-files=no)" ] || sha="$sha.dirty"
    export TERMUX_APP_VERSION_NAME="$base+dev.$sha"
  fi
  echo "building release $TERMUX_APP_VERSION_NAME"
  (cd "$REPO" && ./gradlew -q :app:assembleRelease)
fi
APK="$REPO/app/build/outputs/apk/$BUILD_TYPE/termux-app_apt-android-7-${BUILD_TYPE}_${ABI_SUFFIX}.apk"
[ -f "$APK" ] || { echo "no $APK; build it first (./gradlew :app:assemble${BUILD_TYPE^})" >&2; exit 1; }
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
        if item.filename.startswith("lib/") and not item.filename.startswith("lib/arm64-v8a/"):
            continue
        zout.writestr(item, zin.read(item.filename), compress_type=item.compress_type)
PY
"$BT/zipalign" -f 4 "$WORK/stripped.apk" "$WORK/aligned.apk"
"$BT/apksigner" sign --ks "$REPO/app/testkey_untrusted.jks" \
  --ks-key-alias alias --ks-pass pass:xrj45yWGLbsO7W0v --key-pass pass:xrj45yWGLbsO7W0v \
  "$WORK/aligned.apk"

ls -la "$WORK/aligned.apk"
adb connect "$DEVICE" >/dev/null 2>&1 || true
adb -s "$DEVICE" install -r --no-incremental "$WORK/aligned.apk"
sleep 6
pid="$(adb -s "$DEVICE" shell pidof com.termux | tr -d '\r')"
[ -n "$pid" ] || { echo "the launcher is not running after the install: check the device" >&2; exit 1; }
echo "launcher up (pid $pid): $(adb -s "$DEVICE" shell dumpsys package com.termux | grep -m1 versionName | tr -d ' ')"
