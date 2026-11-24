#!/usr/bin/env bash
# Builds this checkout's staging APK and installs it on a phone through `ad`, stopping at the
# first thing that is not as claimed. Written after three installs on pong that each reported
# more than had happened: a build that failed quietly, a fix whose merge had conflicted, and an
# install whose upload failed.
#
#   scripts/pong-install.sh [commit ...]
#
# Every commit named must be an ancestor of HEAD, or nothing is built: name the fixes the build
# is meant to carry. The tracked tree must be clean, so the APK is the commit it says it is.
#
# DEVICE         the `ad` device name (default pong)
# GRADLE_FLAGS   extra Gradle flags (default "-Pwayland=true -Pghostty=true")
# NO_BUILD=1     install the APK already built, after the same checks
# PKG            the launcher's package (default com.termux)
# AD             the `ad` tool (default ~/.claude/skills/android-control/ad)
#
# It takes the device's lease for the install and gives it back. It taps nothing and takes no
# screenshot, so it may run while the developer uses another app; it still restarts the launcher.
set -euo pipefail

DEVICE="${DEVICE:-pong}"
PKG="${PKG:-com.termux}"
AD="${AD:-$HOME/.claude/skills/android-control/ad}"
GRADLE_FLAGS="${GRADLE_FLAGS:--Pwayland=true -Pghostty=true}"
REPO="$(cd "$(dirname "$0")/.." && pwd)"
APK="$REPO/app/build/outputs/apk/staging/termux-app_apt-android-7-staging_arm64-v8a.apk"
LOG="$(mktemp -t pong-install.XXXXXX.log)"

die() { echo "pong-install: $*" >&2; exit 1; }

cd "$REPO"
[ -z "$(git status --porcelain --untracked-files=no)" ] || die "the tracked tree has changes; commit or stash them"
for commit in "$@"; do
  git merge-base --is-ancestor "$commit" HEAD || die "$commit is not in $(git rev-parse --short HEAD)"
done
head="$(git rev-parse --short=9 HEAD)"

if [ -z "${NO_BUILD:-}" ]; then
  started="$(date +%s)"
  echo "building staging at $head ($GRADLE_FLAGS); log: $LOG"
  # shellcheck disable=SC2086  # the flags are several words on purpose
  if ! ./gradlew -q :app:assembleStaging $GRADLE_FLAGS >"$LOG" 2>&1; then
    grep -E " error: |FAILED|What went wrong" -A3 "$LOG" | head -30 >&2 || true
    die "the build failed; nothing was installed"
  fi
  [ -f "$APK" ] || die "the build left no $APK"
  [ "$(stat -c %Y "$APK")" -ge "$started" ] || die "$APK is older than this build; nothing was installed"
else
  [ -f "$APK" ] || die "no $APK to install"
fi

"$AD" -s "$DEVICE" lease acquire "pong-install $head" >/dev/null || die "could not take the lease on $DEVICE"
trap '"$AD" -s "$DEVICE" lease release >/dev/null 2>&1 || true' EXIT

updated() { "$AD" -s "$DEVICE" shell "dumpsys package $PKG" | sed -n 's/^ *lastUpdateTime=//p' | head -1 | tr -d '\r'; }
before="$(updated)"
"$AD" -s "$DEVICE" install "$APK" || die "the install failed"
sleep 6
after="$(updated)"
[ -n "$after" ] && [ "$after" != "$before" ] || die "the phone's update time did not change ($after): the install did not land"
pid="$("$AD" -s "$DEVICE" shell "pidof $PKG" | tr -d '\r')"
[ -n "$pid" ] || die "the launcher is not running after the install: check the device"

# An install kills the launcher's process and its sshd with it; agents reach the phone's shell
# over ssh, so bring it back. Staging is debuggable, which is what lets run-as work.
files="/data/data/$PKG/files"
"$AD" -s "$DEVICE" shell "run-as $PKG sh -c 'export PREFIX=$files/usr HOME=$files/home \
PATH=$files/usr/bin:\$PATH LD_LIBRARY_PATH=$files/usr/lib TMPDIR=$files/usr/tmp; \
pgrep sshd >/dev/null || sshd'" || echo "pong-install: sshd did not start; start it from a terminal" >&2

echo "installed $head on $DEVICE at $after (launcher pid $pid)"
