#!/usr/bin/env bash
# Measures this launcher's frame times under a scripted, repeatable workload: it plays a gesture
# scenario through adb input while gfxinfo records, and prints pasteable Markdown for
# docs/agents/performance.md. Unlike perf-baseline.sh's frames step, nobody has to hold the phone,
# so two builds see the same gestures.
#
# Usage: DEVICE=<serial> [PKG=<package>] scripts/perf-frametime.sh [scenario-file]
#
# The scenario defaults to scripts/perf-scenarios/pong.txt. A scenario is one action per line,
# coordinates in thousandths of the screen's width and height so a file survives a resolution
# change, though not a different layout:
#   swipe  x1 y1 x2 y2 [ms]   a straight swipe (adb input swipe)
#   hold   x  y  x2 y2 ms     press, hold ms, then drag to x2,y2 (for the border drag's hold)
#   tap    x  y
#   sleep  seconds
#   repeat n                  play the lines up to the matching "end" n times
#   end
# '#' starts a comment. Overrides: ROUNDS (plays of the whole scenario, default 3), SETTLE
# (seconds before recording, default 2).
# Read "Janky (missed deadline)" as the jank figure. The percentiles time each frame from its
# vsync to completion; frames overlap in the pipeline, so a percentile above the 11.1 ms budget
# (90 Hz) is not jank by itself, and only a change between runs means something. The launcher is brought to the front first; it sends input, so run it
# only on a device whose owner agreed.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCENARIO="${1:-$SCRIPT_DIR/perf-scenarios/pong.txt}"
PKG="${PKG:-com.termux}"
ROUNDS="${ROUNDS:-3}"
SETTLE="${SETTLE:-2}"
ACTIVITY="$PKG/com.termux.app.TermuxActivity"

usage() {
  cat >&2 <<USAGE
Usage: DEVICE=<serial> [PKG=<package>] $0 [scenario-file]
  DEVICE     adb serial (required; see 'adb devices')
  PKG        package under test (default com.termux)
  ROUNDS     plays of the scenario (default 3)
  SETTLE     seconds to wait before recording (default 2)
USAGE
  exit 2
}

[ -n "${DEVICE:-}" ] || usage
[ -f "$SCENARIO" ] || { echo "perf-frametime: scenario not found: $SCENARIO" >&2; exit 2; }

die() {
  echo "perf-frametime: $*" >&2
  exit 1
}

sh_() { adb -s "$DEVICE" shell "$@" | tr -d '\r'; }

read -r W H < <(sh_ wm size | awk -F'[ x]+' '/Override size/ { o = $3 " " $4 } /Physical size/ { p = $3 " " $4 } END { print (o != "" ? o : p) }')
[[ "$W" =~ ^[0-9]+$ && "$H" =~ ^[0-9]+$ ]] || die "wm size parse: screen size not found"

px() { echo $(($1 * W / 1000)); }
py() { echo $(($1 * H / 1000)); }

# Plays lines[from..to) once; a "repeat" recurses into its body.
declare -a LINES=()
while IFS= read -r line || [ -n "$line" ]; do
  line="${line%%#*}"
  [ -n "${line// /}" ] && LINES+=("$line")
done <"$SCENARIO"

play() {
  local from=$1 to=$2 i=$1 cmd a b c d e depth j n
  while [ "$i" -lt "$to" ]; do
    read -r cmd a b c d e <<<"${LINES[$i]}"
    case "$cmd" in
      swipe) sh_ input swipe "$(px "$a")" "$(py "$b")" "$(px "$c")" "$(py "$d")" "${e:-250}" ;;
      hold)
        sh_ input motionevent DOWN "$(px "$a")" "$(py "$b")" >/dev/null
        sleep "$(awk -v m="$e" 'BEGIN { print m / 1000 }')"
        for n in 1 2 3 4 5 6 7 8; do
          sh_ input motionevent MOVE "$(px $((a + (c - a) * n / 8)))" "$(py $((b + (d - b) * n / 8)))" >/dev/null
        done
        sh_ input motionevent UP "$(px "$c")" "$(py "$d")" >/dev/null
        ;;
      tap) sh_ input tap "$(px "$a")" "$(py "$b")" ;;
      sleep) sleep "$a" ;;
      repeat)
        depth=1 j=$((i + 1))
        while [ "$j" -lt "$to" ] && [ "$depth" -gt 0 ]; do
          case "${LINES[$j]%% *}" in repeat) depth=$((depth + 1)) ;; end) depth=$((depth - 1)) ;; esac
          j=$((j + 1))
        done
        [ "$depth" -eq 0 ] || die "scenario: 'repeat' on line $((i + 1)) has no 'end'"
        for ((n = 0; n < a; n++)); do play $((i + 1)) $((j - 1)); done
        i=$j
        continue
        ;;
      end) die "scenario: stray 'end'" ;;
      *) die "scenario: unknown action '$cmd'" ;;
    esac
    i=$((i + 1))
  done
}

field() { sed -n "s/^ *$1: *\([0-9][0-9]*\).*/\1/p" <<<"$out" | head -1; }

model="$(sh_ getprop ro.product.model)"
release="$(sh_ getprop ro.build.version.release)"
version="$(sh_ dumpsys package "$PKG")"
version="$(awk -F= '/versionName=/ { print $2; exit }' <<<"$version")"
[ -n "$version" ] || die "versionName not found in 'dumpsys package $PKG' (is $PKG installed?)"

sh_ am start -n "$ACTIVITY" >/dev/null 2>&1
sleep "$SETTLE"
sh_ dumpsys gfxinfo "$PKG" reset >/dev/null
start=$(date +%s)
for ((r = 0; r < ROUNDS; r++)); do play 0 "${#LINES[@]}"; done
elapsed=$(($(date +%s) - start))
out="$(sh_ dumpsys gfxinfo "$PKG")"

total="$(field 'Total frames rendered')"
janky="$(field 'Janky frames')"
[ -n "$total" ] || die "gfxinfo parse: 'Total frames rendered' not found"
[ -n "$janky" ] || die "gfxinfo parse: 'Janky frames' not found"
[ "$total" -gt 0 ] || die "no frames rendered: is the launcher in front and the screen on?"
missed="$(field 'Number Missed Vsync')"
slow_ui="$(field 'Number Slow UI thread')"
slow_draw="$(field 'Number Slow issue draw commands')"

echo "## Frame times: $PKG"
echo
echo "- Date: $(date -u +%Y-%m-%dT%H:%MZ)"
echo "- Device: $model (Android $release), ${W}x$H"
echo "- Version: $version"
echo "- Scenario: $(basename "$SCENARIO") x $ROUNDS ($elapsed s)"
echo
echo "| Metric | Value |"
echo "|---|---|"
awk -v t="$total" -v e="$elapsed" 'BEGIN { printf "| Frames rendered | %d (%.0f/s) |\n", t, t / e }'
awk -v j="$janky" -v t="$total" 'BEGIN { printf "| Janky (missed deadline) | %d (%.1f%%) |\n", j, 100 * j / t }'
for p in 50 90 95 99; do
  v="$(field "${p}th percentile")"
  [ -n "$v" ] || die "gfxinfo parse: '${p}th percentile' not found"
  echo "| ${p}th percentile | $v ms |"
done
echo "| Missed vsync / slow UI thread / slow draw | ${missed:-?} / ${slow_ui:-?} / ${slow_draw:-?} |"
echo
echo "Compare runs of the same scenario and rounds on the same device only; emulator and Waydroid"
echo "gfxinfo reports nearly every frame janky."
