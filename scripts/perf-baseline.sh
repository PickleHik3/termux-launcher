#!/usr/bin/env bash
# Records this launcher's performance baselines from one adb-connected device and
# prints pasteable Markdown, so before/after numbers can go into
# docs/agents/performance.md.
#
# Usage: DEVICE=<serial> [PKG=<package>] scripts/perf-baseline.sh [memory|frames|start|all]
#
# DEVICE is required: bare adb reaches whichever device is attached, often a daily
# phone, so every adb call here passes -s "$DEVICE". PKG defaults to com.termux; the
# activity class stays com.termux.app.TermuxActivity on every edition.
# Overrides: MEM_INTERVAL (seconds between memory readings, default 10),
# FRAMES_SECONDS (frame capture window, default 30).
# The start step force-stops the launcher three times, ending its terminal sessions.
# Never touches shared preferences. Exits non-zero, naming the parse, on a failure.
set -euo pipefail

MODE="${1:-all}"
PKG="${PKG:-com.termux}"
MEM_INTERVAL="${MEM_INTERVAL:-10}"
FRAMES_SECONDS="${FRAMES_SECONDS:-30}"
ACTIVITY="$PKG/com.termux.app.TermuxActivity"

usage() {
  cat >&2 <<USAGE
Usage: DEVICE=<serial> [PKG=<package>] $0 [memory|frames|start|all]
  DEVICE          adb serial (required; see 'adb devices')
  PKG             package under test (default com.termux)
  MEM_INTERVAL    seconds between memory readings (default 10)
  FRAMES_SECONDS  frame capture window (default 30)
USAGE
  exit 2
}

[ -n "${DEVICE:-}" ] || usage
case "$MODE" in memory | frames | start | all) ;; *) usage ;; esac

die() {
  echo "perf-baseline: $*" >&2
  exit 1
}

# adb shell output carries CRs; strip them so awk and sed see clean lines.
sh_() { adb -s "$DEVICE" shell "$@" | tr -d '\r'; }

# min, max, mean and spread (max-min) of the numbers on stdin, space separated.
stats() {
  awk 'NR == 1 { min = max = $1 } { s += $1; if ($1 < min) min = $1; if ($1 > max) max = $1 }
       END { printf "%.1f %.1f %.1f %.1f\n", min, max, s / NR, max - min }'
}

header() {
  local model release version refresh
  model="$(sh_ getprop ro.product.model)"
  release="$(sh_ getprop ro.build.version.release)"
  # Capture first: an awk that exits on its first match closes the pipe on a long dumpsys, and
  # under pipefail the writer's SIGPIPE (exit 141) would end the script.
  version="$(sh_ dumpsys package "$PKG")"
  version="$(awk -F= '/versionName=/ { print $2; exit }' <<<"$version")"
  [ -n "$version" ] || die "versionName not found in 'dumpsys package $PKG' (is $PKG installed?)"
  # SurfaceFlinger's "refresh-rate : 90.00 Hz" is the active mode; dumpsys display lists the
  # supported modes first, so its first value is only a fallback.
  refresh="$(sh_ dumpsys SurfaceFlinger | grep -m1 -oiE 'refresh-rate *: *[0-9.]+' | grep -oE '[0-9.]+$' || true)"
  [ -n "$refresh" ] || refresh="$(sh_ dumpsys display | grep -oiE '(refreshRate|fps)=[0-9.]+' | head -1 | cut -d= -f2 || true)"
  [ -z "$refresh" ] || refresh="$refresh Hz (display mode; read the app's own rate from the frame times)"
  echo "## Baseline: $PKG"
  echo
  echo "- Date: $(date -u +%Y-%m-%dT%H:%MZ)"
  echo "- Device: $model (Android $release)"
  echo "- Version: $version"
  echo "- Display refresh rate: ${refresh:-unknown}"
  echo
}

# One meminfo reading as "totalMB nativeMB". Newer Android prints "TOTAL PSS:", older
# a "TOTAL" row; Native Heap is the first number of its row (PSS Total column).
mem_reading() {
  local out total native
  out="$(sh_ dumpsys meminfo "$PKG")"
  total="$(awk '
    /TOTAL PSS:/ { for (i = 1; i < NF; i++) if ($i == "PSS:") { print $(i + 1); exit } }
    /^ *TOTAL:? +[0-9]/ { v = $2; if (v ~ /^[0-9]+$/) { print v; exit } }' <<<"$out" | head -1)"
  native="$(awk '/^ *Native Heap/ { for (i = 3; i <= NF; i++) if ($i ~ /^[0-9]+$/) { print $i; exit } }' <<<"$out" | head -1)"
  [[ "$total" =~ ^[0-9]+$ ]] || die "meminfo parse: TOTAL PSS not found for $PKG (is it running?)"
  [[ "$native" =~ ^[0-9]+$ ]] || die "meminfo parse: Native Heap PSS not found for $PKG"
  awk -v t="$total" -v n="$native" 'BEGIN { printf "%.1f %.1f\n", t / 1024, n / 1024 }'
}

run_memory() {
  local i t n totals="" natives="" ts ns
  echo "### Memory (PSS, MB)"
  echo
  echo "| Reading | TOTAL PSS | Native Heap PSS |"
  echo "|---|---|---|"
  for i in 1 2 3; do
    [ "$i" -eq 1 ] || sleep "$MEM_INTERVAL"
    read -r t n < <(mem_reading)
    echo "| $i | $t | $n |"
    totals+="$t"$'\n'
    natives+="$n"$'\n'
  done
  ts="$(stats <<<"${totals%$'\n'}")"
  ns="$(stats <<<"${natives%$'\n'}")"
  read -r tmin tmax tmean tspread <<<"$ts"
  read -r nmin nmax nmean nspread <<<"$ns"
  echo "| min | $tmin | $nmin |"
  echo "| max | $tmax | $nmax |"
  echo "| mean | $tmean | $nmean |"
  echo "| spread (max-min) | $tspread | $nspread |"
  echo
  echo "Documented noise is +/-10 MB; treat a spread or a before/after delta inside that as noise."
  echo
}

run_frames() {
  local out total janky jpct p v
  local -A pct=()
  sh_ dumpsys gfxinfo "$PKG" reset >/dev/null
  echo "Use the app now for $FRAMES_SECONDS seconds (scroll, swipe, open panels)..." >&2
  sleep "$FRAMES_SECONDS"
  out="$(sh_ dumpsys gfxinfo "$PKG")"
  total="$(sed -n 's/^ *Total frames rendered: *\([0-9][0-9]*\).*/\1/p' <<<"$out" | head -1)"
  janky="$(sed -n 's/^ *Janky frames: *\([0-9][0-9]*\) .*/\1/p' <<<"$out" | head -1)"
  jpct="$(sed -n 's/^ *Janky frames: *[0-9][0-9]* *(\([0-9.]*\)%).*/\1/p' <<<"$out" | head -1)"
  [ -n "$total" ] || die "gfxinfo parse: 'Total frames rendered' not found"
  [ -n "$janky" ] || die "gfxinfo parse: 'Janky frames' count not found"
  [ -n "$jpct" ] || die "gfxinfo parse: 'Janky frames' percentage not found"
  for p in 50 90 95 99; do
    v="$(sed -n "s/^ *${p}th percentile: *\([0-9][0-9]*\)ms.*/\1/p" <<<"$out" | head -1)"
    [ -n "$v" ] || die "gfxinfo parse: '${p}th percentile' not found"
    pct[$p]="$v"
  done
  echo "### Frames ($FRAMES_SECONDS s of use)"
  echo
  echo "| Metric | Value |"
  echo "|---|---|"
  echo "| Total frames rendered | $total |"
  echo "| Janky frames | $janky ($jpct%) |"
  for p in 50 90 95 99; do
    echo "| ${p}th percentile | ${pct[$p]} ms |"
  done
  echo
  echo "Note: emulator and Waydroid gfxinfo reports nearly every frame janky, so frame numbers only count on a real phone."
  echo
}

# ActivityTaskManager logs "Displayed <pkg>/<activity> for user 0: +1s234ms" once the first
# frame is drawn; prints that time in ms, or nothing within 20 s.
displayed_ms() {
  local i line
  for i in $(seq 1 40); do
    line="$(sh_ logcat -d -s ActivityTaskManager:I | grep -m1 "Displayed $PKG/" || true)"
    if [ -n "$line" ]; then
      sed -n 's/.*: +\([0-9s]*[0-9]*\)ms.*/\1/p' <<<"$line" | awk -F's' 'NF == 2 { print $1 * 1000 + $2; next } { print $1 }'
      return
    fi
    sleep 0.5
  done
}

run_start() {
  local i ms kind
  echo "### Start-up (force-stop to first frame, ms)"
  echo
  echo "| Run | Kind | Displayed |"
  echo "|---|---|---|"
  for i in 1 2 3; do
    sh_ logcat -c
    sh_ am force-stop "$PKG"
    sleep 1
    # As the default home app the launcher is relaunched the moment it is stopped, cold; otherwise
    # start it ourselves.
    if [ -n "$(sh_ pidof "$PKG" || true)" ]; then
      kind="cold (relaunched as home)"
    else
      kind="cold (am start)"
      sh_ am start -n "$ACTIVITY" >/dev/null 2>&1
    fi
    ms="$(displayed_ms)"
    [ -n "$ms" ] || die "logcat parse: no 'Displayed $PKG/' line within 20 s of run $i"
    echo "| $i | $kind | $ms |"
    sleep 5
  done
  echo
  echo "Force-stopping ends every terminal session; ask before running this on someone's daily phone."
  echo
}

header
case "$MODE" in
  memory) run_memory ;;
  frames) run_frames ;;
  start) run_start ;;
  all)
    run_memory
    run_frames
    run_start
    ;;
esac
