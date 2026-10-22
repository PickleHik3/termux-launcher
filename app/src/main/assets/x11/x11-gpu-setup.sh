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
#   3. Brings the installed packages up to date, then installs the small test program and the
#      driver packages for each way (remembering which were already there).
#   4. Starts a private display for the tests, so nothing you have open is disturbed.
#   5. Tries each way with a short 3D test, hardest scenes first. A way passes only if it
#      really uses the graphics chip, finishes without crashing, and is clearly faster than
#      the no-acceleration baseline.
#   6. Keeps the best one and saves it.
#   7. If a Debian container (proot-distro) is installed, repeats the test inside it.
#   8. Removes the test program and the drivers that lost, unless you asked to keep them.
#      Only what this run installed goes, with the dependencies nothing else needs any more;
#      nothing that was there before is touched. A run that stops early (an error, Ctrl-C, a
#      closed terminal) puts things back the same way, keeping only the display's own packages.
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
# X programs in Termux look for the display's socket in the prefix's tmp, whatever TMPDIR a shell
# has set, so the display the script starts must put it there too.
export TMPDIR=$PREFIX/tmp
ICD_DIR=$PREFIX/share/vulkan/icd.d
OUT_DIR=$HOME/.config/termux-launcher
OUT_FILE=$OUT_DIR/x11-gpu.env
LOG=$TMPDIR/x11-gpu-setup.log
# proot-distro 5.x keeps a container's root under containers/<name>/rootfs; older versions
# used installed-rootfs/<name>. Take whichever is there.
DISTRO_ROOT=$PREFIX/var/lib/proot-distro/containers/debian/rootfs
[ -d "$DISTRO_ROOT" ] || DISTRO_ROOT=$PREFIX/var/lib/proot-distro/installed-rootfs/debian
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
# Termux comes with either apt (the default) or pacman. Both are driven the same way here, and
# neither ever gets to ask a question.
if command -v pacman >/dev/null && [ -f "$PREFIX/etc/pacman.conf" ] && ! command -v dpkg >/dev/null; then PM=pacman; else PM=apt; fi
installed_before() {
  if [ $PM = pacman ]; then pacman -Qq "$1" >/dev/null 2>&1
  else dpkg -s "$1" 2>/dev/null | grep -q '^Status: install ok installed'; fi
}
# A full upgrade before anything is added: installing onto stale libraries (or pacman's -Sy
# without -u) leaves packages built against versions the phone does not have.
pm_upgrade() { if [ $PM = pacman ]; then pacman -Syu --noconfirm; else pkg update -y && pkg upgrade -y; fi; }
pm_refresh() { if [ $PM = pacman ]; then pacman -Sy --noconfirm; else apt-get update; fi; }
pm_install() { if [ $PM = pacman ]; then pacman -S --needed --noconfirm "$@"; else pkg install -y "$@"; fi; }
# With apt the X11 packages live in a repository that is itself a package; pacman has it built in.
REPO_PKG=""; [ $PM = apt ] && REPO_PKG=x11-repo

# Installed package names, space-separated. With apt, a package that an install cut off halfway
# left unpacked counts as installed, so it goes again like the rest. Given "pd": the Debian
# container's (proot-distro quotes each word, so the format string arrives intact).
installed_now() {
  if [ $# = 0 ] && [ $PM = pacman ]; then pacman -Qq 2>/dev/null | tr '\n' ' '; return; fi
  # shellcheck disable=SC2016
  "$@" dpkg-query -W -f '${Status} ${Package}\n' 2>/dev/null | awk '{ sub(/\r$/, "") }
    $1 ~ /^(unknown|install|hold|deinstall|purge)$/ && $3 != "not-installed" && $3 != "config-files" {
      sub(/:.*/, "", $4); printf "%s ", $4 }'
}
# Set arithmetic on space-separated names; each prints one space-separated line, no repeats.
set_minus() {
  local -A drop=(); local w out=()
  for w in $2; do drop[$w]=1; done
  for w in $1; do [ -n "${drop[$w]:-}" ] || { drop[$w]=1; out+=("$w"); }; done
  echo "${out[*]}"
}
set_and() {
  local -A have=(); local w out=()
  for w in $2; do have[$w]=1; done
  for w in $1; do [ "${have[$w]:-}" = 1 ] && { have[$w]=2; out+=("$w"); }; done
  echo "${out[*]}"
}
# apt_sim [pd] ARGS... -> the names an "apt-get -s ARGS" would take out. Fails when apt cannot
# even plan, e.g. after an install was cut off and dpkg first wants "dpkg --configure -a".
apt_sim() {
  local run=(); [ "$1" = pd ] && { run=(pd); shift; }
  "${run[@]}" apt-get -s "$@" 2>/dev/null |
    awk '{ sub(/\r$/, "") } $1 == "Remv" || $1 == "Purg" { sub(/:.*/, "", $2); printf "%s ", $2 }'
}

# Keep track of what we install, for the summary in step 3. What is removed again at the end is
# worked out from the package list itself (see take_out below), so an install that was cut off
# or only partly worked is covered too.
WE_INSTALLED=(); NOT_AVAILABLE=()
install_pkgs() {
  local missing=()
  for p in "$@"; do installed_before "$p" || missing+=("$p"); done
  [ ${#missing[@]} -eq 0 ] && return 0
  detail "Installing: ${missing[*]}"
  # On the terminal, not in the log: an install can take minutes and must not look stuck.
  if pm_install "${missing[@]}"; then
    WE_INSTALLED+=("${missing[@]}")
    return 0
  fi
  NOT_AVAILABLE+=("${missing[@]}")
  detail "Could not install ${missing[*]} (see the output above)."
  return 1
}

# Background helpers we start and must stop again.
SERVER_PID=""; VIRGL_PID=""
stop_virgl()   { [ -n "$VIRGL_PID" ] && kill "$VIRGL_PID" 2>/dev/null; VIRGL_PID=""; pkill -f virgl_test_server_android 2>/dev/null; true; }
stop_display() {
  [ -n "$SERVER_PID" ] && { kill "$SERVER_PID" 2>/dev/null; sleep 1; rm -f "$TMPDIR/.X11-unix/X$DISPLAY_NO"; }
  SERVER_PID=""; true
}

# ---------------------------------------------------------------------------------------------
# Putting things back. BEFORE is the package list taken just before the first install (step 3),
# PROOT_BEFORE the container's, taken just before its install (step 7). Whatever is installed at
# the end and was not in that list is this run's. Empty means "nothing to put back".
BEFORE=""; PROOT_BEFORE=""; PROOT_TEST_PKGS="glmark2-es2 libgl1-mesa-dri"
ALL_PKGS=""; CHOSEN=""; PROOT_WINNER=""; FINISHED=0; CLEANED=0; TOOK_OUT=0

# take_out WHERE NEW NAMED KEEP — remove this run's packages from WHERE ("" = Termux, "pd" = the
# Debian container). NEW: this run's packages there; NAMED: the ones the script asks for by
# name; KEEP: names that stay. What goes:
#   targets = NAMED that are NEW, minus KEEP (pacman: plus NEW packages left as dependencies of
#             nothing, which an install cut off halfway can leave)
#   plan    = what the package manager's own dry run takes out with the targets: them, plus the
#             dependencies nothing that stays needs (pacman -Rsp, apt-get -s --auto-remove)
#   remove  = plan that is NEW, minus KEEP
# A package that was there before is never NEW, so it stays, even an unneeded one the user
# already had. A kept package's dependencies are needed by something that stays, so they are
# never in the plan. apt would also take out whatever depends on a removed package, so its
# exact removal is dry-run once more and refused if it reaches anything outside "remove".
take_out() {
  local where=$1 new=$2 named=$3 keep=$4 verb=remove hand targets plan remove extra
  [ -n "$where" ] && verb=purge   # inside Debian, purge as before: nothing of these was there
  if [ -z "$where" ] && [ $PM = pacman ]; then
    hand="pacman -R"
    targets=$(set_minus "$(set_and "$named" "$new") $(set_and "$(pacman -Qdtq 2>/dev/null)" "$new")" "$keep")
    [ -z "$targets" ] && return 0
    # shellcheck disable=SC2086
    plan=$(pacman -Rsp --print-format %n $targets 2>/dev/null | tr '\n' ' ') || plan="?"
  else
    hand="apt-get $verb"; [ -n "$where" ] && hand="proot-distro login debian -- apt-get $verb"
    targets=$(set_minus "$(set_and "$named" "$new")" "$keep")
    # With no named target left, there may still be dependencies of an install that was cut off.
    # shellcheck disable=SC2086
    if [ -n "$targets" ]; then plan=$(apt_sim $where --auto-remove $verb $targets) || plan="?"
    else plan=$(apt_sim $where autoremove) || plan="?"; fi
  fi
  TOOK_OUT=1
  if [ "$plan" = "?" ]; then
    detail "The package manager could not work out what to remove (it may still be busy, or an"
    detail "install was cut off). Once it works again, remove these by hand: $hand $targets"
    return 1
  fi
  remove=$(set_minus "$(set_and "$plan" "$new")" "$keep")
  [ -z "$remove" ] && { TOOK_OUT=0; return 0; }
  if [ $PM = apt ] || [ -n "$where" ]; then
    # shellcheck disable=SC2086
    extra=$(set_minus "$(apt_sim $where $verb $remove)" "$remove")
    if [ -n "$extra" ]; then
      detail "Not removing $remove: apt would take $extra with it, and that stays."
      return 1
    fi
  fi
  if [ -n "$where" ]; then detail "Removing the test program from Debian again: $remove"
  else detail "Removing what was only needed for the tests: $remove"; fi
  printf '\n### %s: removing %s; left in place: %s\n' "${where:-termux}" "$remove" "$(set_minus "$new" "$remove")" >>"$LOG"
  # A held lock (another install running) or a half-done install makes this fail cleanly with
  # the package manager's own message; nothing is removed then, and the line below says what to.
  # shellcheck disable=SC2086
  if [ -n "$where" ]; then "$where" apt-get purge -y $remove
  elif [ $PM = pacman ]; then pacman -R --noconfirm $remove
  else apt-get remove -y $remove; fi || {
    detail "Some packages could not be removed (see the output above). If another install was"
    detail "running or was cut off, finish it first (apt: dpkg --configure -a), then: $hand $remove"
    return 1
  }
}

cleanup_container() {
  [ -n "$PROOT_BEFORE" ] || return 0
  local before=$PROOT_BEFORE; PROOT_BEFORE=""   # once only
  take_out pd "$(set_minus "$(installed_now pd)" "$before")" "$PROOT_TEST_PKGS" ""
}

# What stays: the display's own packages, the winner's drivers and, when the container chose
# virgl, its helper. A run that stops before step 6 has written its result has no winner, and
# keeps only x11-repo and xkeyboard-config: the script says up front that those stay, the
# display needs them, and the launcher would install them for it anyway.
cleanup_prefix() {
  [ -n "$BEFORE" ] || return 0
  local before=$BEFORE new keep_new; BEFORE=""   # once only
  KEEP_PKGS="$REPO_PKG xkeyboard-config"
  [ -n "$CHOSEN" ] && KEEP_PKGS="$KEEP_PKGS ${PKGS[$CHOSEN]}"
  [ "$PROOT_WINNER" = virgl ] && KEEP_PKGS="$KEEP_PKGS virglrenderer-android"
  new=$(set_minus "$(installed_now)" "$before")
  # A kept package that first came in as another's dependency is marked as wanted for itself, so
  # neither this dry run nor a later autoremove counts it, or what it needs, as left over.
  keep_new=$(set_and "$KEEP_PKGS" "$new")
  if [ -n "$keep_new" ]; then
    # shellcheck disable=SC2086
    if [ $PM = pacman ]; then pacman -D --asexplicit $keep_new >/dev/null 2>&1
    else apt-mark manual $keep_new >/dev/null 2>&1; fi
  fi
  TOOK_OUT=0
  if [ -n "$new" ]; then take_out "" "$new" "$ALL_PKGS" "$KEEP_PKGS"; fi
  [ $FINISHED = 1 ] && [ $TOOK_OUT = 0 ] && detail "Nothing to remove."
  [ $FINISHED = 1 ] && detail "Kept:$( for p in $KEEP_PKGS; do printf ' %s' "$p"; done )"
  true
}

# Runs once: at the end of step 8, or on the way out of a run that stopped early. While it
# runs, Ctrl-C and the like are ignored (and so by the package manager it starts), so a
# removal is never cut in half. A package manager interrupted earlier has already exited by
# then: the shell runs this only after the foreground command is gone, and apt and pacman
# release their locks when they stop on a signal.
cleanup() {
  [ $CLEANED = 1 ] && return 0; CLEANED=1
  trap '' INT TERM HUP
  stop_virgl; stop_display
  if [ $FINISHED = 0 ] && { [ -n "$BEFORE" ] || [ -n "$PROOT_BEFORE" ]; }; then
    if [ $KEEP = 1 ]; then say "Stopped before the end. Leaving every installed package in place, as asked."; return 0; fi
    say "Stopped before the end. Putting back what this run installed."
  fi
  if [ $KEEP = 1 ]; then [ $FINISHED = 1 ] && detail "Keeping every package, as asked."; return 0; fi
  cleanup_container
  cleanup_prefix
}
trap cleanup EXIT
# Ctrl-C, a kill, or a closed terminal end the run through the EXIT trap above, with the usual
# exit codes. With the terminal gone, what cleanup says goes to the log instead.
trap 'exit 130' INT
trap 'exit 143' TERM
trap 'exec </dev/null >>"$LOG" 2>&1; exit 129' HUP

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
detail "Package manager: $PM"

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

ALL_PKGS="$REPO_PKG xkeyboard-config glmark2 ${PKGS[software]}"
for c in "${CANDIDATES[@]}"; do ALL_PKGS="$ALL_PKGS ${PKGS[$c]}"; done
# shellcheck disable=SC2086
ALL_PKGS=$(printf '%s\n' $ALL_PKGS | awk '!seen[$0]++' | tr '\n' ' ')
detail "Packages needed for the tests: $ALL_PKGS"
if [ $KEEP = 0 ]; then
  detail "The test program and the drivers that lose will be removed again at the end."
  detail "(xkeyboard-config${REPO_PKG:+ and $REPO_PKG} stay: the display itself needs them.)"
fi
detail "Your installed packages are brought up to date first; that can take a few minutes."
if [ $YES = 0 ]; then
  printf '\nContinue? [Y/n] '; read -r answer
  case "$answer" in n|N|no|NO) echo "Stopped. Nothing was changed."; exit 0 ;; esac
fi

# ---------------------------------------------------------------------------------------------
step 3 "Installing the test program and drivers"
detail "Bringing the installed packages up to date."
pm_upgrade || { say "The packages could not be brought up to date (see the output above), so nothing new was installed."; exit 1; }
# What is on the phone now stays, whatever happens next. Taken after the upgrade, not before it:
# a dependency an upgraded package newly pulled in belongs to that package, and taking it out
# again would break it. Without this list nothing could be told apart, so nothing is removed.
BEFORE=$(installed_now)
[ -z "$BEFORE" ] && detail "Could not list the installed packages, so nothing will be removed at the end."
if [ $PM = apt ]; then
  install_pkgs x11-repo || { say "The X11 package repository could not be added, so the drivers cannot be installed."; exit 1; }
elif ! grep -q '^\[x11\]' "$PREFIX/etc/pacman.conf"; then
  say "pacman.conf has no [x11] repository, so the display's packages cannot be installed. Add it and run again."; exit 1
fi
# The X11 repository is a package on apt, so its list arrives only with another refresh; pacman's
# came with the upgrade.
if [ $PM = apt ]; then
  detail "Refreshing the package lists."
  pm_refresh || detail "Refreshing the package lists failed; trying with what is cached."
fi
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
for p in $ALL_PKGS; do case " ${WE_INSTALLED[*]} ${NOT_AVAILABLE[*]} " in *" $p "*) ;; *) ALREADY="$ALREADY $p" ;; esac; done
result "newly installed: ${WE_INSTALLED[*]:-none}; already on the phone:${ALREADY:- none}; not available here: ${NOT_AVAILABLE[*]:-none}"

# ---------------------------------------------------------------------------------------------
step 4 "Starting a private display for the tests"
SOCKET=$TMPDIR/.X11-unix/X$DISPLAY_NO
# A socket file alone proves nothing: an earlier run may have left one behind. Ask the display.
display_answers() { DISPLAY=$TEST_DISPLAY timeout 10 glmark2-es2 --off-screen -s 64x64 -b build:duration=0.2 >/dev/null 2>&1; }
if [ -S "$SOCKET" ] && display_answers; then
  detail "A display $TEST_DISPLAY is already running; using it."
else
  if [ -e "$SOCKET" ]; then
    detail "Removing a leftover socket from an earlier run."
    rm -f "$SOCKET" "$HOME/.tmp/.X11-unix/X$DISPLAY_NO"
  fi
  if ! command -v termux-x11 >/dev/null; then
    say "The display command (termux-x11) is not installed. Turn the Linux display on in the launcher's settings first."; exit 1
  fi
  # A display only for measuring: no fcitx5 beside it, however it is set up for the real one.
  TERMUX_X11_IM=0 termux-x11 "$TEST_DISPLAY" -ac >>"$LOG" 2>&1 &
  SERVER_PID=$!
  for _ in $(seq 1 20); do [ -S "$SOCKET" ] && break; sleep 0.5; done
  if [ ! -S "$SOCKET" ]; then
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
  SCORE=$(echo "$out" | sed -n 's/^ *glmark2 Score: *//p' | head -1 | tr -d ' \r'); SCORE=${SCORE:-0}
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
CHOSEN=$WINNER   # from here on, a run that stops early keeps the winner's drivers too
detail "Saved to $OUT_FILE."
detail "Apps started from the launcher's drawer pick the installed drivers on their own; for a shell, run: source $OUT_FILE"

# ---------------------------------------------------------------------------------------------
step 7 "The Debian container"
PROOT_WINNER=""; PROOT_BASE=0; PROOT_SCORE=0
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
    # The container's own list, so only what this install adds is purged again.
    PROOT_BEFORE=$(installed_now pd)
    [ -z "$PROOT_BEFORE" ] && detail "Could not list Debian's packages, so the test program will stay there."
    pd sh -c "apt-get update && apt-get install -y $PROOT_TEST_PKGS" ||
      detail "Could not install it (see the output above); skipping the container."
  fi
  if pd sh -c 'command -v glmark2-es2' >/dev/null 2>&1; then
    pd_test() { # ENV-STRING -> RENDERER SCORE STATUS
      local envs=$1 out rc
      # shellcheck disable=SC2086
      out=$(pd env DISPLAY=$TEST_DISPLAY XDG_RUNTIME_DIR=/tmp $envs timeout $TEST_TIMEOUT $TEST_CMD 2>&1); rc=$?
      printf '\n### debian: %s (exit %s)\n%s\n' "$envs" "$rc" "$out" >>"$LOG"
      RENDERER=$(echo "$out" | sed -n 's/^ *GL_RENDERER: *//p' | head -1)
      SCORE=$(echo "$out" | sed -n 's/^ *glmark2 Score: *//p' | head -1 | tr -d ' \r'); SCORE=${SCORE:-0}
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
    [ $KEEP = 0 ] && cleanup_container
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
FINISHED=1
cleanup

say "Done. Summary:"
detail "Graphics chip: $GPU_NAME"
detail "Linux apps in Termux: $WINNER"
[ -n "$PROOT_WINNER" ] && detail "Linux apps in the Debian container: $PROOT_WINNER"
detail "Details: $OUT_FILE   Full log: $LOG"
detail "If an app shows a black window or wrong colours, the launcher's Display options have two compatibility switches for that."
