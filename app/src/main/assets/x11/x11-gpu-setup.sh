#!/usr/bin/env bash
# x11-gpu-setup.sh — find the graphics setting that works best on this phone for Linux apps.
#
# Run this from a Termux shell on the phone, once, before you start using Linux apps on the
# display. It tries every way this phone's graphics chip can be used, keeps the one that works
# best, and puts everything else back the way it was. It writes what it decided to
# ~/.config/termux-launcher/x11-gpu.env in plain words.
#
# What it does, in order (the same numbering you see while it runs):
#   1. Looks at which graphics chip the phone has.
#   2. Lists the ways that chip can be used, from the most promising to "no acceleration".
#   3. Installs the small test program and the driver packages for each way (remembering
#      which were already there).
#   4. Starts a private display for the tests, so nothing you have open is disturbed.
#   5. Tries each way with a short 3D test, hardest scenes first. A way passes only if it
#      really uses the graphics chip, finishes without crashing, and is clearly faster than
#      the no-acceleration baseline.
#   6. Keeps the best one and saves it.
#   7. If a Debian container (proot-distro) is installed, repeats the test inside it.
#   8. Removes the test program and the drivers that lost, unless you asked to keep them.
#
# Options:
#   --keep        leave every installed package in place afterwards
#   --skip-proot  do not test inside the Debian container
#   --yes         do not pause for confirmation before installing anything
#   --display=N   use display :N for the tests (default :7)
#
# Nothing here changes the launcher's settings. The launcher runs a Linux app with the
# profile whose driver packages are installed, so after this script only the winner's
# packages remain and the drawer uses it on its own.

set -uo pipefail

KEEP=0; SKIP_PROOT=0; YES=0; DISPLAY_NO=7
for arg in "$@"; do
  case "$arg" in
    --keep) KEEP=1 ;;
    --skip-proot) SKIP_PROOT=1 ;;
    --yes) YES=1 ;;
    --display=*) DISPLAY_NO=${arg#--display=} ;;
    -h|--help) sed -n '2,/^$/p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "Unknown option: $arg (try --help)"; exit 2 ;;
  esac
done

PREFIX=${PREFIX:-/data/data/com.termux/files/usr}
TMPDIR=${TMPDIR:-$PREFIX/tmp}
ICD_DIR=$PREFIX/share/vulkan/icd.d
OUT_DIR=$HOME/.config/termux-launcher
OUT_FILE=$OUT_DIR/x11-gpu.env
LOG=$TMPDIR/x11-gpu-setup.log
DISTRO_ROOT=$PREFIX/var/lib/proot-distro/installed-rootfs/debian
TEST_DISPLAY=":$DISPLAY_NO"

# The short 3D test. Off-screen so the display does not pace it, three heavy scenes so software
# rendering cannot keep up, five seconds each. About 20 seconds per attempt.
TEST_CMD="glmark2-es2 --off-screen -s 1280x720 -b shadow:duration=5 -b refract:duration=5 -b terrain:duration=5"
TEST_TIMEOUT=120

say()    { printf '\n%s\n' "$*"; }
step()   { printf '\n== Step %s of 8: %s ==\n' "$1" "$2"; }
detail() { printf '   %s\n' "$*"; }
result() { printf '   -> %s\n' "$*"; }

: > "$LOG"

# ---------------------------------------------------------------------------------------------
# Keep track of what we install so it can be removed again. A package that was already on the
# phone before we started is never touched.
installed_before() { dpkg -s "$1" 2>/dev/null | grep -q '^Status: install ok installed'; }
WE_INSTALLED=()
install_pkgs() {
  local missing=()
  for p in "$@"; do installed_before "$p" || missing+=("$p"); done
  [ ${#missing[@]} -eq 0 ] && return 0
  detail "Installing: ${missing[*]}"
  if pkg install -y "${missing[@]}" >>"$LOG" 2>&1; then
    WE_INSTALLED+=("${missing[@]}")
    return 0
  fi
  detail "Could not install ${missing[*]} (details in $LOG)."
  return 1
}

# Background helpers we start and must stop again.
SERVER_PID=""; VIRGL_PID=""
stop_virgl()   { [ -n "$VIRGL_PID" ] && kill "$VIRGL_PID" 2>/dev/null; VIRGL_PID=""; pkill -f virgl_test_server_android 2>/dev/null; true; }
stop_display() { [ -n "$SERVER_PID" ] && kill "$SERVER_PID" 2>/dev/null; SERVER_PID=""; true; }
cleanup_processes() { stop_virgl; stop_display; }
trap cleanup_processes EXIT

# ---------------------------------------------------------------------------------------------
step 1 "Looking at the phone's graphics chip"
EGL=$(getprop ro.hardware.egl 2>/dev/null)
VULKAN=$(getprop ro.hardware.vulkan 2>/dev/null)
KGSL_MODEL=$(cat /sys/class/kgsl/kgsl-3d0/gpu_model 2>/dev/null)
HAS_KGSL=0; [ -e /dev/kgsl-3d0 ] && HAS_KGSL=1
VENDORS=$(printf '%s %s %s' "$EGL" "$VULKAN" "$KGSL_MODEL" | tr '[:upper:]' '[:lower:]')

GPU_KIND=unknown
if [ $HAS_KGSL = 1 ] || [[ $VENDORS == *adreno* || $VENDORS == *qcom* || $VENDORS == *qualcomm* ]]; then GPU_KIND=adreno
elif [[ $VENDORS == *mali* ]]; then GPU_KIND=mali
elif [[ $VENDORS == *xclipse* || $VENDORS == *powervr* || $VENDORS == *img* || $VENDORS == *samsung* ]]; then GPU_KIND=other-gles
elif [[ $VENDORS == *emulation* || $VENDORS == *swiftshader* || $VENDORS == *angle* ]]; then GPU_KIND=emulated
fi
GPU_NAME=${KGSL_MODEL:-${EGL:-unknown}}
detail "Graphics chip: $GPU_NAME  (family: $GPU_KIND$( [ -n "$VULKAN" ] && echo ", has a Vulkan driver"))"

# ---------------------------------------------------------------------------------------------
step 2 "Deciding what to try"
# Each candidate: a name, the packages it needs, the settings that select it, and a helper to
# keep running (or "-"). Same table as the launcher's own recommendation (launcherctl x11 gpu).
declare -A PKGS ENV HELPER WHY
PKGS[software]="mesa"
ENV[software]="LIBGL_ALWAYS_SOFTWARE=1 GALLIUM_DRIVER=llvmpipe MESA_LOADER_DRIVER_OVERRIDE=llvmpipe"
HELPER[software]="-"
WHY[software]="no acceleration; drawn by the CPU. Works everywhere and is the baseline."

TU_DEBUG=noconform
[[ $(echo "$KGSL_MODEL" | tr '[:upper:]' '[:lower:]') =~ adreno\ ?8[0-9][0-9] ]] && TU_DEBUG="noconform,flushall,syncdraw"
PKGS[turnip-zink]="mesa mesa-vulkan-icd-freedreno"
ENV[turnip-zink]="MESA_LOADER_DRIVER_OVERRIDE=zink TU_DEBUG=$TU_DEBUG"
HELPER[turnip-zink]="-"
WHY[turnip-zink]="the open Adreno driver (Turnip) with OpenGL on top (Zink). Usually the fastest on Qualcomm phones."

PKGS[virgl]="mesa virglrenderer-android"
ENV[virgl]="GALLIUM_DRIVER=virpipe MESA_GL_VERSION_OVERRIDE=4.3COMPAT MESA_GLES_VERSION_OVERRIDE=3.2 MESA_NO_ERROR=1 LIBGL_DRI3_DISABLE=1"
HELPER[virgl]="virgl_test_server_android"
WHY[virgl]="forwards OpenGL to the phone's own driver through a helper program."

PKGS[virgl-angle]="mesa virglrenderer-android angle-android"
ENV[virgl-angle]=${ENV[virgl]}
HELPER[virgl-angle]="virgl_test_server_android --angle-gl"
WHY[virgl-angle]="like virgl, but through Google's ANGLE, for chips whose own OpenGL driver misbehaves."

PKGS[vulkan-wrapper]="mesa vulkan-wrapper-android"
ENV[vulkan-wrapper]="VK_ICD_FILENAMES=$ICD_DIR/wrapper_icd.aarch64.json MESA_LOADER_DRIVER_OVERRIDE=zink"
[ $GPU_KIND = mali ] && ENV[vulkan-wrapper]="${ENV[vulkan-wrapper]} MESA_VK_WSI_PRESENT_MODE=mailbox MESA_VK_WSI_DEBUG=blit"
HELPER[vulkan-wrapper]="-"
WHY[vulkan-wrapper]="the phone's own Vulkan driver, wrapped so Zink can use it."

case $GPU_KIND in
  adreno)     CANDIDATES=(turnip-zink virgl); [ -n "$VULKAN" ] && CANDIDATES+=(vulkan-wrapper) ;;
  mali)       CANDIDATES=(virgl-angle); [ -n "$VULKAN" ] && CANDIDATES+=(vulkan-wrapper); CANDIDATES+=(virgl) ;;
  other-gles) CANDIDATES=(virgl-angle); [ -n "$VULKAN" ] && CANDIDATES+=(vulkan-wrapper); CANDIDATES+=(virgl) ;;
  emulated)   CANDIDATES=() ;;
  *)          CANDIDATES=(virgl); [ -n "$VULKAN" ] && CANDIDATES+=(vulkan-wrapper) ;;
esac

detail "Will try, in this order:"
n=1
for c in "${CANDIDATES[@]}"; do detail "  $n. $c — ${WHY[$c]}"; n=$((n+1)); done
detail "  $n. software — ${WHY[software]}"
[ ${#CANDIDATES[@]} -eq 0 ] && detail "(This looks like an emulator, so only software rendering will be checked.)"

ALL_PKGS="x11-repo xkeyboard-config glmark2 ${PKGS[software]}"
for c in "${CANDIDATES[@]}"; do ALL_PKGS="$ALL_PKGS ${PKGS[$c]}"; done
# shellcheck disable=SC2086
ALL_PKGS=$(printf '%s\n' $ALL_PKGS | awk '!seen[$0]++' | tr '\n' ' ')
detail "Packages needed for the tests: $ALL_PKGS"
if [ $KEEP = 0 ]; then
  detail "The test program and the drivers that lose will be removed again at the end."
  detail "(x11-repo and xkeyboard-config stay: the display itself needs them.)"
fi
if [ $YES = 0 ]; then
  printf '\nContinue? [Y/n] '; read -r answer
  case "$answer" in n|N|no|NO) echo "Stopped. Nothing was changed."; exit 0 ;; esac
fi

# ---------------------------------------------------------------------------------------------
step 3 "Installing the test program and drivers"
# shellcheck disable=SC2086
install_pkgs x11-repo && pkg update -y >>"$LOG" 2>&1
# shellcheck disable=SC2086
install_pkgs xkeyboard-config glmark2 ${PKGS[software]} || { say "The display's own packages could not be installed, so the tests cannot run."; exit 1; }
AVAILABLE=()
for c in "${CANDIDATES[@]}"; do
  # shellcheck disable=SC2086
  if install_pkgs ${PKGS[$c]}; then AVAILABLE+=("$c"); else detail "Skipping $c: its packages are not available here."; fi
done
# With more than one Vulkan driver installed, Turnip has to be named explicitly.
if [ "$(ls "$ICD_DIR" 2>/dev/null | wc -l)" -gt 1 ]; then
  ENV[turnip-zink]="${ENV[turnip-zink]} VK_ICD_FILENAMES=$ICD_DIR/freedreno_icd.aarch64.json"
fi
ALREADY=""
for p in $ALL_PKGS; do case " ${WE_INSTALLED[*]} " in *" $p "*) ;; *) ALREADY="$ALREADY $p" ;; esac; done
result "newly installed: ${WE_INSTALLED[*]:-none}; already on the phone:${ALREADY:- none}"

# ---------------------------------------------------------------------------------------------
step 4 "Starting a private display for the tests"
if [ -S "$TMPDIR/.X11-unix/X$DISPLAY_NO" ]; then
  detail "A display $TEST_DISPLAY is already running; using it."
else
  if ! command -v termux-x11 >/dev/null; then
    say "The display command (termux-x11) is not installed. Turn the Linux display on in the launcher's settings first."; exit 1
  fi
  termux-x11 "$TEST_DISPLAY" -ac >>"$LOG" 2>&1 &
  SERVER_PID=$!
  for _ in $(seq 1 20); do [ -S "$TMPDIR/.X11-unix/X$DISPLAY_NO" ] && break; sleep 0.5; done
  if [ ! -S "$TMPDIR/.X11-unix/X$DISPLAY_NO" ]; then
    say "The display did not start. Its messages are in $LOG."; exit 1
  fi
  detail "Display $TEST_DISPLAY is up. Nothing is shown on screen; the tests draw off-screen."
fi
export DISPLAY=$TEST_DISPLAY

# ---------------------------------------------------------------------------------------------
step 5 "Trying each way with a short 3D test"
# run_test NAME ENV-STRING HELPER  -> sets RENDERER, SCORE, STATUS (ok / crashed / timed out / not accelerated)
run_test() {
  local name=$1 envs=$2 helper=$3 out rc
  RENDERER=""; SCORE=0; STATUS="crashed"
  if [ "$helper" != "-" ]; then
    # shellcheck disable=SC2086
    $helper >>"$LOG" 2>&1 & VIRGL_PID=$!; sleep 1
  fi
  # shellcheck disable=SC2086
  out=$(env $envs timeout $TEST_TIMEOUT $TEST_CMD 2>&1); rc=$?
  printf '\n### %s (exit %s)\n%s\n' "$name" "$rc" "$out" >>"$LOG"
  stop_virgl
  RENDERER=$(echo "$out" | sed -n 's/^ *GL_RENDERER: *//p' | head -1)
  SCORE=$(echo "$out" | sed -n 's/^ *glmark2 Score: *//p' | head -1); SCORE=${SCORE:-0}
  if [ $rc = 124 ]; then STATUS="timed out"
  elif [ $rc != 0 ] || [ -z "$SCORE" ] || [ "$SCORE" = 0 ]; then STATUS="crashed"
  elif [ "$name" != software ] && echo "$RENDERER" | grep -qiE 'llvmpipe|lavapipe|softpipe'; then STATUS="not accelerated"
  else STATUS="ok"; fi
}

detail "Baseline first: software rendering."
run_test software "${ENV[software]}" -
if [ "$STATUS" != ok ]; then
  say "Even software rendering did not finish ($STATUS). The display itself is not working, so no acceleration can be chosen. See $LOG."
  exit 1
fi
BASE=$SCORE
result "software: score $BASE, drawn by \"$RENDERER\""

WINNER=software; WINNER_SCORE=$BASE
declare -A SCORES STATUSES RENDERERS
for c in "${AVAILABLE[@]}"; do
  detail "Trying $c ..."
  run_test "$c" "${ENV[$c]}" "${HELPER[$c]}"
  SCORES[$c]=$SCORE; STATUSES[$c]=$STATUS; RENDERERS[$c]=$RENDERER
  case $STATUS in
    ok)
      if [ "$SCORE" -ge $((BASE * 2)) ]; then
        result "$c: score $SCORE, drawn by \"$RENDERER\" — works, and clearly faster than software."
        WINNER=$c; WINNER_SCORE=$SCORE; break
      else
        result "$c: score $SCORE — works, but not faster enough than software ($BASE) to be worth it."
      fi ;;
    "not accelerated") result "$c: the graphics chip was not used (drawn by \"$RENDERER\"); a setting was ignored or a driver is missing." ;;
    *) result "$c: $STATUS." ;;
  esac
done

# ---------------------------------------------------------------------------------------------
step 6 "The result"
if [ "$WINNER" = software ]; then
  say "Best choice for this phone: software rendering. None of the accelerated ways worked well enough."
else
  say "Best choice for this phone: $WINNER (score $WINNER_SCORE against $BASE for software)."
fi
mkdir -p "$OUT_DIR"
{
  echo "# Written by x11-gpu-setup.sh on $(date '+%Y-%m-%d %H:%M')."
  echo "# Graphics chip: $GPU_NAME. Chosen: $WINNER — ${WHY[$WINNER]}"
  echo "# Software baseline score: $BASE. Chosen score: $WINNER_SCORE."
  for c in "${AVAILABLE[@]}"; do
    [ -n "${STATUSES[$c]:-}" ] && echo "# Tried $c: ${STATUSES[$c]}, score ${SCORES[$c]}, renderer ${RENDERERS[$c]:-?}"
  done
  echo "# Use in a shell:  source $OUT_FILE"
  for kv in ${ENV[$WINNER]}; do echo "export $kv"; done
  [ "${HELPER[$WINNER]}" != "-" ] && echo "# Keep this running in Termux first:  ${HELPER[$WINNER]} &"
} > "$OUT_FILE"
detail "Saved to $OUT_FILE."
detail "Apps started from the launcher's drawer pick the installed drivers on their own; for a shell, run: source $OUT_FILE"

# ---------------------------------------------------------------------------------------------
step 7 "The Debian container"
PROOT_WINNER=""; PROOT_BASE=0; PROOT_SCORE=0; PROOT_INSTALLED_TEST=0
if [ $SKIP_PROOT = 1 ]; then
  detail "Skipped, as asked."
elif [ ! -d "$DISTRO_ROOT" ] || ! command -v proot-distro >/dev/null; then
  detail "No Debian container is installed (proot-distro install debian), so there is nothing to test here."
else
  detail "Found a Debian container. Testing inside it: software first, then virgl if this phone can offer it."
  detail "(Debian's own Adreno driver talks to the Linux kernel driver, not Android's, so Turnip does not apply inside.)"
  pd() { proot-distro login debian --shared-tmp -- "$@"; }
  if pd dpkg -s glmark2-es2 2>/dev/null | grep -q 'install ok installed'; then
    :
  else
    detail "Installing the test program inside Debian."
    if pd sh -c 'apt-get update -qq && apt-get install -y -qq glmark2-es2 libgl1-mesa-dri' >>"$LOG" 2>&1; then
      PROOT_INSTALLED_TEST=1
    else
      detail "Could not install it (details in $LOG); skipping the container."
    fi
  fi
  if pd sh -c 'command -v glmark2-es2' >/dev/null 2>&1; then
    pd_test() { # ENV-STRING -> RENDERER SCORE STATUS
      local envs=$1 out rc
      # shellcheck disable=SC2086
      out=$(pd env DISPLAY=$TEST_DISPLAY XDG_RUNTIME_DIR=/tmp $envs timeout $TEST_TIMEOUT $TEST_CMD 2>&1); rc=$?
      printf '\n### debian: %s (exit %s)\n%s\n' "$envs" "$rc" "$out" >>"$LOG"
      RENDERER=$(echo "$out" | sed -n 's/^ *GL_RENDERER: *//p' | head -1)
      SCORE=$(echo "$out" | sed -n 's/^ *glmark2 Score: *//p' | head -1); SCORE=${SCORE:-0}
      if [ $rc = 124 ]; then STATUS="timed out"; elif [ $rc != 0 ] || [ "$SCORE" = 0 ]; then STATUS="crashed"; else STATUS="ok"; fi
    }
    pd_test "${ENV[software]}"
    if [ "$STATUS" != ok ]; then
      result "software rendering inside Debian did not finish ($STATUS). Check that the container has libgl1-mesa-dri."
    else
      PROOT_BASE=$SCORE; PROOT_WINNER=software
      result "Debian, software: score $PROOT_BASE"
      if command -v virgl_test_server_android >/dev/null; then
        detail "Trying virgl inside Debian ..."
        virgl_test_server_android >>"$LOG" 2>&1 & VIRGL_PID=$!; sleep 1
        pd_test "${ENV[virgl]} VTEST_SOCKET_NAME=/tmp/.virgl_test"
        stop_virgl
        if [ "$STATUS" = ok ] && ! echo "$RENDERER" | grep -qiE 'llvmpipe|lavapipe' && [ "$SCORE" -ge $((PROOT_BASE * 2)) ]; then
          PROOT_WINNER=virgl; PROOT_SCORE=$SCORE
          result "Debian, virgl: score $SCORE, drawn by \"$RENDERER\" — works, and clearly faster."
        else
          result "Debian, virgl: $STATUS, score $SCORE, drawn by \"${RENDERER:-?}\" — staying with software inside the container."
        fi
      else
        detail "virgl's helper is not installed on the Termux side, so software is the only option inside Debian."
      fi
    fi
    if [ $PROOT_INSTALLED_TEST = 1 ] && [ $KEEP = 0 ]; then
      pd sh -c 'apt-get purge -y -qq glmark2-es2 && apt-get autoremove -y -qq' >>"$LOG" 2>&1 && detail "Removed the test program from Debian again."
    fi
  fi
  if [ -n "$PROOT_WINNER" ]; then
    {
      echo
      echo "# Inside the Debian container: $PROOT_WINNER (score ${PROOT_SCORE:-$PROOT_BASE}, software $PROOT_BASE)."
      echo "# Run a Debian app on the display like this (replace :0 and the app):"
      if [ "$PROOT_WINNER" = virgl ]; then
        echo "#   virgl_test_server_android &"
        echo "#   proot-distro login debian --shared-tmp -- env DISPLAY=:0 ${ENV[virgl]} VTEST_SOCKET_NAME=/tmp/.virgl_test <app>"
      else
        echo "#   proot-distro login debian --shared-tmp -- env DISPLAY=:0 <app>"
      fi
    } >> "$OUT_FILE"
  fi
fi

# ---------------------------------------------------------------------------------------------
step 8 "Cleaning up"
stop_display
if [ $KEEP = 1 ]; then
  detail "Keeping every package, as asked."
else
  KEEP_PKGS="x11-repo xkeyboard-config ${PKGS[$WINNER]}"
  [ "$PROOT_WINNER" = virgl ] && KEEP_PKGS="$KEEP_PKGS virglrenderer-android"
  REMOVE=()
  for p in "${WE_INSTALLED[@]}"; do
    case " $KEEP_PKGS " in *" $p "*) ;; *) REMOVE+=("$p") ;; esac
  done
  if [ ${#REMOVE[@]} -gt 0 ]; then
    detail "Removing what was only needed for the tests: ${REMOVE[*]}"
    pkg uninstall -y "${REMOVE[@]}" >>"$LOG" 2>&1 || detail "Some packages could not be removed; see $LOG."
    apt-get autoremove -y >>"$LOG" 2>&1 || true
  else
    detail "Nothing to remove."
  fi
  detail "Kept: $KEEP_PKGS"
fi

say "Done. Summary:"
detail "Graphics chip: $GPU_NAME"
detail "Linux apps in Termux: $WINNER"
[ -n "$PROOT_WINNER" ] && detail "Linux apps in the Debian container: $PROOT_WINNER"
detail "Details: $OUT_FILE   Full log: $LOG"
detail "If an app shows a black window or wrong colours, the launcher's Display options have two compatibility switches for that."
