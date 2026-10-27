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
#      driver packages for each way (remembering which were already there). The Vulkan wrapper
#      is in no Termux repository: it is downloaded from termux-desktop's release and installed
#      only if it is exactly the file expected.
#   4. Starts a display of its own for the tests, on the first free number from :7 up. It is
#      never shown and the launcher leaves it alone, so a display you have open stays exactly as
#      it was. However the run ends, this display is stopped again.
#   5. Tries every way with a short 3D test, hardest scenes first. A way passes only if it
#      really uses the graphics chip, finishes without crashing, and is clearly faster than
#      the no-acceleration baseline.
#   6. Keeps the fastest that passed and saves it, with every way's result for the launcher's
#      Graphics acceleration page (~/.config/termux-launcher/x11-gpu-results.tsv).
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
#   --display=N   look for a free display number for the tests from :N up (default :7)
#   --profiles=FILE  read the ways to try from FILE (the launcher's page writes one) instead of
#                 asking the launcher with launcherctl
#   --install-wrapper  only install the Vulkan wrapper (vulkan-wrapper-android), then stop
#   --estimate    change nothing; work out how much a run would download and need, and print
#                 four lines on stdout (progress goes to stderr), sizes in bytes:
#                   download_bytes=N   to download: the upgrade plus the new packages
#                   install_bytes=N    extra disk space while the run is going
#                   removable_bytes=N  at most what is given back at the end (the winner is not
#                                      known in advance, so it is "up to")
#                   upgrade_count=N    packages the upgrade would touch
#                 Exits 0, or non-zero with a one-line reason on stderr when it cannot tell (no
#                 network, say). Reads --profiles like a normal run. The Debian container (step 7)
#                 is not counted: its sizes would need a second package database behind proot.
#
# Which ways fit the phone, and what each needs, is the launcher's list (launcherctl x11 gpu
# --table), not this script's. Nothing here changes the launcher's settings: with the choice on
# Automatic the launcher runs a Linux app with the best profile whose packages are installed,
# so after this script only the winner's packages remain and the drawer uses it on its own.

set -uo pipefail

KEEP=0; SKIP_PROOT=0; YES=0; DISPLAY_FROM=7; PROFILES=""; INSTALL_WRAPPER=0; ESTIMATE=0
for arg in "$@"; do
  case "$arg" in
    --keep) KEEP=1 ;;
    --skip-proot) SKIP_PROOT=1 ;;
    --yes) YES=1 ;;
    --display=*) DISPLAY_FROM=${arg#--display=}; DISPLAY_FROM=${DISPLAY_FROM#:} ;;
    --profiles=*) PROFILES=${arg#--profiles=} ;;
    --install-wrapper) INSTALL_WRAPPER=1 ;;
    --estimate) ESTIMATE=1 ;;
    -h|--help) sed -n '2,/^$/p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "Unknown option: $arg (try --help)"; exit 2 ;;
  esac
done
case $DISPLAY_FROM in ''|*[!0-9]*) echo "--display wants a number, such as --display=7"; exit 2 ;; esac
if [ $ESTIMATE = 1 ] && [ $INSTALL_WRAPPER = 1 ]; then echo "--estimate and --install-wrapper do not go together"; exit 2; fi
# An estimate prints its four lines and nothing else on stdout: fd 3 keeps the real stdout for
# them, and everything else the script says goes to stderr.
if [ $ESTIMATE = 1 ]; then exec 3>&1 1>&2; fi

PREFIX=${PREFIX:-/data/data/com.termux/files/usr}
# X programs in Termux look for the display's socket in the prefix's tmp, whatever TMPDIR a shell
# has set, so the display the script starts must put it there too.
export TMPDIR=$PREFIX/tmp
ICD_DIR=$PREFIX/share/vulkan/icd.d
OUT_DIR=$HOME/.config/termux-launcher
OUT_FILE=$OUT_DIR/x11-gpu.env
# One line per way tried, tab-separated: name, outcome, score, renderer. The launcher reads it.
RESULTS_FILE=$OUT_DIR/x11-gpu-results.tsv
LOG=$TMPDIR/x11-gpu-setup.log
# proot-distro 5.x keeps a container's root under containers/<name>/rootfs; older versions
# used installed-rootfs/<name>. Take whichever is there.
DISTRO_ROOT=$PREFIX/var/lib/proot-distro/containers/debian/rootfs
[ -d "$DISTRO_ROOT" ] || DISTRO_ROOT=$PREFIX/var/lib/proot-distro/installed-rootfs/debian
# The test display, picked in step 4.
DISPLAY_NO=""; TEST_DISPLAY=""

# The short 3D test. Off-screen so the display does not pace it, three heavy scenes so software
# rendering cannot keep up, five seconds each. About 20 seconds per attempt.
TEST_CMD="glmark2-es2 --off-screen -s 1280x720 -b shadow:duration=5 -b refract:duration=5 -b terrain:duration=5"
TEST_TIMEOUT=120

say()    { printf '\n%s\n' "$*"; }
step()   { printf '\n== Step %s of 8: %s ==\n' "$1" "$2"; }
detail() { printf '   %s\n' "$*"; }
result() { printf '   -> %s\n' "$*"; }

# A fresh log per benchmark run; installing only the wrapper adds to the last run's.
[ $INSTALL_WRAPPER = 1 ] || [ $ESTIMATE = 1 ] || : > "$LOG"

# ---------------------------------------------------------------------------------------------
# Termux comes with either apt (the default) or pacman. Both are driven the same way here, and
# neither ever gets to ask a question.
#
# Which one is decided by what Termux itself says it uses, not by which tools happen to be
# installed: a pacman Termux can have the dpkg package too, with an empty dpkg database.
# TERMUX_APP_PACKAGE_MANAGER (apt or pacman) is set inside the app's own shells,
# TERMUX_MAIN_PACKAGE_FORMAT (debian or pacman) in a login over ssh; failing both, the package
# database that actually holds packages decides.
detect_pm() {
  case ${TERMUX_APP_PACKAGE_MANAGER:-} in apt) echo apt; return ;; pacman) echo pacman; return ;; esac
  case ${TERMUX_MAIN_PACKAGE_FORMAT:-} in debian) echo apt; return ;; pacman) echo pacman; return ;; esac
  if command -v pacman >/dev/null && [ -n "$(ls -A "$PREFIX/var/lib/pacman/local" 2>/dev/null)" ]; then echo pacman; return; fi
  if command -v apt-get >/dev/null && [ -s "$PREFIX/var/lib/dpkg/status" ]; then echo apt; return; fi
  if command -v pacman >/dev/null && [ -f "$PREFIX/etc/pacman.conf" ] && ! command -v dpkg >/dev/null; then echo pacman; else echo apt; fi
}
PM=$(detect_pm)
# apt never asks: no debconf dialogs, and a configuration file the user edited is kept without
# the dpkg question about it. Packages go straight through apt-get, not "pkg", whose wrapper
# would pick a mirror and refresh the lists again on every call.
export DEBIAN_FRONTEND=noninteractive
APT_OPTS=(-y -o Dpkg::Options::=--force-confdef -o Dpkg::Options::=--force-confold)
installed_before() {
  if [ $PM = pacman ]; then pacman -Qq "$1" >/dev/null 2>&1
  else dpkg -s "$1" 2>/dev/null | grep -q '^Status: install ok installed'; fi
}
# A full upgrade before anything is added: installing onto stale libraries (or pacman's -Sy
# without -u) leaves packages built against versions the phone does not have. This is the one
# refresh of the package lists; installs after it never refresh again (apt: x11-repo's own list
# is the exception, see pm_refresh).
pm_upgrade() {
  if [ $PM = pacman ]; then pacman -Syu --noconfirm
  else apt-get update && apt-get "${APT_OPTS[@]}" full-upgrade; fi
}
# apt only: the lists of a repository that was added as a package arrive with another update.
pm_refresh() { if [ $PM = pacman ]; then true; else apt-get update; fi; }
pm_install() { if [ $PM = pacman ]; then pacman -S --needed --noconfirm "$@"; else apt-get install "${APT_OPTS[@]}" "$@"; fi; }
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

# ---------------------------------------------------------------------------------------------
# The Vulkan wrapper. vulkan-wrapper-android is in no Termux repository, so it comes from the
# prebuilt termux-desktop publishes, pinned here by version and checksum: a download that is not
# byte for byte the file below is refused. Installed from the file, it is an ordinary package of
# that name, so the clean-up takes it out by name like any other if it loses.
# Credit: the wrapper driver is xMeM's (Mesa, MIT core licence; the package installs its
# license.rst), this build was packaged by lfdevs and is published by sabamdarif/termux-desktop.
# None of termux-desktop's scripts are used here. See THIRD_PARTY_NOTICES.md.
WRAPPER_PKG=vulkan-wrapper-android
WRAPPER_VERSION=25.0.0-3
WRAPPER_URL=https://github.com/sabamdarif/termux-desktop/releases/download/${WRAPPER_PKG}_$WRAPPER_VERSION
declare -A WRAPPER_SHA256=(
  [${WRAPPER_PKG}_$WRAPPER_VERSION-aarch64.pkg.tar.xz]=407a024fe25c37e34e83d32a199eb82e204aa1c80f391c5eecc88d5d4ca98f96
  [${WRAPPER_PKG}_$WRAPPER_VERSION-arm.pkg.tar.xz]=dec9d90eed2cb4d5e21acd665661e3f20476d13711b9c27eb38d9c276a3d4890
  [${WRAPPER_PKG}_${WRAPPER_VERSION}_aarch64.deb]=fc11784fee6a553eae78b30a4bff267981b735be7b1d4fac4e894e09be8058ca
  [${WRAPPER_PKG}_${WRAPPER_VERSION}_arm.deb]=2fbbf12d692534e640d270faa8f840c08ef0d38a65cbb191dd1bf896a32b923f
)
WRAPPER_DIR=""

# The file for this phone's package manager and processor; fails where there is none.
wrapper_file() {
  local arch=""
  if [ $PM = pacman ]; then
    arch=$(pacman-conf Architecture 2>/dev/null | head -n 1)
    case $arch in ''|auto) arch=$(uname -m) ;; esac
  else
    arch=$(dpkg --print-architecture 2>/dev/null)
  fi
  case $arch in
    aarch64|arm64) arch=aarch64 ;;
    arm|armhf|armel|armv7*|armv8l) arch=arm ;;
    *) return 1 ;;
  esac
  if [ $PM = pacman ]; then echo "${WRAPPER_PKG}_$WRAPPER_VERSION-$arch.pkg.tar.xz"
  else echo "${WRAPPER_PKG}_${WRAPPER_VERSION}_$arch.deb"; fi
}

drop_wrapper_download() {
  [ -n "$WRAPPER_DIR" ] && rm -rf "$WRAPPER_DIR"
  WRAPPER_DIR=""
  true
}

# The pinned pacman file is not one pacman reads as it is: its entries start with "./", so pacman
# finds no metadata in it, and its version has no release number. Once the checksum has matched,
# it is packed again the way makepkg packs, with the release added and nothing else changed.
# Prints the new file's path.
repack_wrapper() {
  local dir=$WRAPPER_DIR/pkg out=$WRAPPER_DIR/$WRAPPER_PKG-$WRAPPER_VERSION.pkg.tar
  mkdir "$dir" && tar -xf "$1" -C "$dir" || return 1
  [ -f "$dir/.PKGINFO" ] || return 1
  sed -i "s/^pkgver = ${WRAPPER_VERSION%-*}\$/pkgver = $WRAPPER_VERSION/" "$dir/.PKGINFO"
  (cd "$dir" && tar -cf "$out" .PKGINFO .BUILDINFO .MTREE data) || return 1
  echo "$out"
}

# Downloads the wrapper into a folder of its own under TMPDIR, checks it, installs it with the
# package manager (which brings its dependencies from the official repositories) and deletes the
# download again, whatever happened.
install_wrapper() {
  local name file sum rc repacked
  if ! name=$(wrapper_file); then
    detail "The Vulkan wrapper has no build for this phone's processor."
    return 1
  fi
  WRAPPER_DIR=$(mktemp -d "$TMPDIR/vulkan-wrapper.XXXXXX") || { WRAPPER_DIR=""; return 1; }
  file=$WRAPPER_DIR/$name
  detail "Downloading the Vulkan wrapper ($name)."
  if command -v curl >/dev/null; then curl -fL --retry 2 -o "$file" "$WRAPPER_URL/$name"
  elif command -v wget >/dev/null; then wget -O "$file" "$WRAPPER_URL/$name"
  else false; fi || {
    detail "The download did not work."
    drop_wrapper_download
    return 1
  }
  sum=$(sha256sum "$file" 2>/dev/null | awk '{ print $1 }')
  if [ -z "$sum" ] || [ "$sum" != "${WRAPPER_SHA256[$name]:-}" ]; then
    detail "The download is not the expected file, so it was not installed."
    printf '\n### %s: sha256 %s, expected %s\n' "$name" "${sum:-?}" "${WRAPPER_SHA256[$name]:-?}" >>"$LOG"
    drop_wrapper_download
    return 1
  fi
  if [ $PM = pacman ]; then
    repacked=$(repack_wrapper "$file") && pacman -U --noconfirm "$repacked"
  else apt-get install "${APT_OPTS[@]}" "$file"; fi
  rc=$?
  drop_wrapper_download
  return $rc
}

# Keep track of what we install, for the summary in step 3. What is removed again at the end is
# worked out from the package list itself (see take_out below), so an install that was cut off
# or only partly worked is covered too. The Vulkan wrapper comes from its own download, the rest
# from the package manager's repositories.
WE_INSTALLED=(); NOT_AVAILABLE=()
install_pkgs() {
  local missing=() from_repo=() p wrapper=0
  for p in "$@"; do installed_before "$p" || missing+=("$p"); done
  [ ${#missing[@]} -eq 0 ] && return 0
  for p in "${missing[@]}"; do
    if [ "$p" = "$WRAPPER_PKG" ]; then wrapper=1; else from_repo+=("$p"); fi
  done
  detail "Installing: ${missing[*]}"
  # On the terminal, not in the log: an install can take minutes and must not look stuck.
  if [ ${#from_repo[@]} -gt 0 ]; then
    if ! pm_install "${from_repo[@]}"; then
      NOT_AVAILABLE+=("${missing[@]}")
      detail "Could not install ${missing[*]} (see the output above)."
      return 1
    fi
    WE_INSTALLED+=("${from_repo[@]}")
  fi
  if [ $wrapper = 1 ]; then
    if ! install_wrapper; then
      NOT_AVAILABLE+=("$WRAPPER_PKG")
      detail "Could not install $WRAPPER_PKG."
      return 1
    fi
    WE_INSTALLED+=("$WRAPPER_PKG")
  fi
  return 0
}

# ---------------------------------------------------------------------------------------------
# --estimate: the sizes a run would need, worked out without changing anything on the phone.
# The package set is the run's own: ALL_PKGS minus what is installed, plus the upgrade. Dry runs
# only: apt-get -s / --print-uris, and for pacman a package database synced into a temporary
# folder (as checkupdates does), so the real databases are not touched. Not counted: the Debian
# container, and the dependencies of the Vulkan wrapper (they are ordinary repository packages
# that the drivers usually need already).
#
# The Vulkan wrapper is no repository package, so its size is a constant, measured on the pinned
# files above on 2026-10-25: download = the file's length in bytes; installed = the .deb's
# Installed-Size field (KiB * 1024), and for the pacman files the summed sizes of the archive's
# entries (tar -tv).
declare -A WRAPPER_DL_BYTES=(
  [${WRAPPER_PKG}_$WRAPPER_VERSION-aarch64.pkg.tar.xz]=383832
  [${WRAPPER_PKG}_$WRAPPER_VERSION-arm.pkg.tar.xz]=344704
  [${WRAPPER_PKG}_${WRAPPER_VERSION}_aarch64.deb]=383300
  [${WRAPPER_PKG}_${WRAPPER_VERSION}_arm.deb]=344364
)
declare -A WRAPPER_INST_BYTES=(
  [${WRAPPER_PKG}_$WRAPPER_VERSION-aarch64.pkg.tar.xz]=1658932
  [${WRAPPER_PKG}_$WRAPPER_VERSION-arm.pkg.tar.xz]=1216248
  [${WRAPPER_PKG}_${WRAPPER_VERSION}_aarch64.deb]=1712128
  [${WRAPPER_PKG}_${WRAPPER_VERSION}_arm.deb]=1269760
)
EST_TMP=""; EST_DB=""; EST_X=()
est_cleanup() { [ -n "$EST_TMP" ] && rm -rf "$EST_TMP"; true; }
est_fail() { echo "x11-gpu-setup: $*" >&2; exit 1; }

# apt, x11-repo not installed yet: its package lists arrive only once it is. For the estimate,
# fetch that one package (a download, not an install), unpack it in the temporary folder and let
# apt read a copy of its sources, keys and lists from there. Sets EST_X, the options that point
# apt at the copies.
est_apt_x11() {
  local d=$EST_TMP/x11
  mkdir -p "$d/dl" "$d/x" "$d/sources.list.d" "$d/trusted.gpg.d" "$d/lists"
  ( cd "$d/dl" && apt-get download x11-repo ) || return 1
  dpkg-deb -x "$d"/dl/x11-repo_*.deb "$d/x" || return 1
  cp -a "$PREFIX/etc/apt/sources.list.d/." "$d/sources.list.d/" 2>/dev/null
  cp -a "$PREFIX/etc/apt/trusted.gpg.d/." "$d/trusted.gpg.d/" 2>/dev/null
  find "$d/x" -path '*/etc/apt/sources.list.d/*' -type f -exec cp {} "$d/sources.list.d/" \;
  find "$d/x" -path '*/etc/apt/trusted.gpg.d/*' -type f -exec cp {} "$d/trusted.gpg.d/" \;
  cp -a "$PREFIX/var/lib/apt/lists/." "$d/lists/" 2>/dev/null
  EST_X=(-o "Dir::Etc::SourceParts=$d/sources.list.d" -o "Dir::Etc::TrustedParts=$d/trusted.gpg.d" -o "Dir::State::Lists=$d/lists")
  apt-get "${EST_X[@]}" update
}

# pacman -Si/-Qi ARGS -> "name bytes" (Installed Size, first block per name).
est_pacman_inst() {
  LC_ALL=C pacman "$@" 2>/dev/null | awk -F' *: *' '
    $1 == "Name" { name = $2; seen = (name in done); done[name] = 1 }
    $1 == "Installed Size" && !seen {
      split($2, v, " "); m = 1
      if (v[2] ~ /^KiB/) m = 1024; else if (v[2] ~ /^MiB/) m = 1048576; else if (v[2] ~ /^GiB/) m = 1073741824
      printf "%s %d\n", name, v[1] * m + 0.5 }'
}

# Prints the four lines on fd 3 (the real stdout) and exits.
estimate() {
  local p name kind rc cand=() fresh=() avail=() a_names=() b_names=() wrap=0 file d
  local -A is_a=() kinds=() size_new=() size_old=()
  local dl=0 inst=0 rem=0 upg=0 keep=" x11-repo xkeyboard-config "
  trap est_cleanup EXIT
  EST_TMP=$(mktemp -d "$TMPDIR/x11-gpu-estimate.XXXXXX") || est_fail "could not make a temporary folder"
  for p in $ALL_PKGS; do installed_before "$p" || fresh+=("$p"); done
  for p in "${fresh[@]}"; do if [ "$p" = "$WRAPPER_PKG" ]; then wrap=1; else cand+=("$p"); fi; done

  if [ $PM = apt ]; then
    detail "Refreshing the package lists (nothing is installed)."
    apt-get update || est_fail "could not refresh the package lists (is there a network connection?)"
    if ! installed_before x11-repo; then
      est_apt_x11 || est_fail "could not read the X11 repository's package list"
    fi
    for p in "${cand[@]}"; do apt-cache "${EST_X[@]}" show --no-all-versions "$p" >/dev/null 2>&1 && avail+=("$p"); done
    apt-get "${EST_X[@]}" -s full-upgrade >"$EST_TMP/a.out" 2>/dev/null || est_fail "apt could not plan the upgrade"
    while read -r name kind; do a_names+=("$name"); is_a[$name]=1; done < <(awk '$1 == "Inst" { sub(/:.*/, "", $2); print $2, "x" }' "$EST_TMP/a.out")
    upg=${#a_names[@]}
    if [ $((${#avail[@]} + ${#a_names[@]})) -gt 0 ]; then
      apt-get "${EST_X[@]}" -s install "${avail[@]}" "${a_names[@]}" >"$EST_TMP/b.out" 2>/dev/null || est_fail "apt could not plan the installation"
      while read -r name kind; do b_names+=("$name"); kinds[$name]=$kind; done < <(awk '$1 == "Inst" { sub(/:.*/, "", $2); print $2, ($3 ~ /^\[/ ? "U" : "N") }' "$EST_TMP/b.out")
      d=$(apt-get "${EST_X[@]}" -y --print-uris install "${avail[@]}" "${a_names[@]}" 2>/dev/null | awk '$1 ~ /^'"'"'/ { s += $3 } END { printf "%d", s }') || est_fail "apt could not list the downloads"
      dl=$((dl + d))
      while read -r name d; do size_new[$name]=$((d * 1024)); done < <(apt-cache "${EST_X[@]}" show --no-all-versions "${b_names[@]}" 2>/dev/null |
        awk '$1 == "Package:" { n = $2 } $1 == "Installed-Size:" { print n, $2 }')
      # shellcheck disable=SC2016
      while read -r name d; do size_old[$name]=$((d * 1024)); done < <(dpkg-query -W -f '${Package} ${Installed-Size}\n' "${b_names[@]}" 2>/dev/null)
    fi
  else
    local dbp
    dbp=$(pacman-conf DBPath 2>/dev/null); dbp=${dbp:-$PREFIX/var/lib/pacman/}
    EST_DB=$EST_TMP/db
    mkdir -p "$EST_DB" && ln -s "${dbp%/}/local" "$EST_DB/local" || est_fail "could not set up a temporary package database"
    detail "Syncing a temporary copy of the package databases (the real ones are not touched)."
    pacman -Sy --dbpath "$EST_DB" --logfile /dev/null || est_fail "could not sync the package databases (is there a network connection?)"
    for p in "${cand[@]}"; do pacman -Si --dbpath "$EST_DB" "$p" >/dev/null 2>&1 && avail+=("$p"); done
    while read -r name; do a_names+=("$name"); is_a[$name]=1; done < <(pacman -Sup --dbpath "$EST_DB" --print-format '%n' 2>/dev/null)
    upg=${#a_names[@]}
    pacman -Sup --needed --dbpath "$EST_DB" --print-format '%n %s' "${avail[@]}" >"$EST_TMP/b.out" 2>/dev/null || est_fail "pacman could not plan the installation"
    while read -r name d; do
      [ -n "$name" ] || continue
      b_names+=("$name"); dl=$((dl + ${d:-0}))
      if installed_before "$name"; then kinds[$name]=U; else kinds[$name]=N; fi
    done <"$EST_TMP/b.out"
    if [ ${#b_names[@]} -gt 0 ]; then
      while read -r name d; do size_new[$name]=$d; done < <(est_pacman_inst -Si --dbpath "$EST_DB" "${b_names[@]}")
      while read -r name d; do size_old[$name]=$d; done < <(est_pacman_inst -Qi --dbpath "$EST_DB" "${b_names[@]}")
    fi
  fi

  # An upgraded package costs its growth; a new one its whole installed size. Only new packages
  # that the upgrade did not bring in itself, and that the run does not keep, are given back.
  for name in "${b_names[@]}"; do
    if [ "${kinds[$name]}" = U ]; then
      d=$(( ${size_new[$name]:-0} - ${size_old[$name]:-0} ))
      [ $d -gt 0 ] && inst=$((inst + d))
    else
      inst=$((inst + ${size_new[$name]:-0}))
      if [ -z "${is_a[$name]:-}" ] && [ "${keep#* $name }" = "$keep" ]; then rem=$((rem + ${size_new[$name]:-0})); fi
    fi
  done
  if [ $wrap = 1 ] && file=$(wrapper_file); then
    dl=$((dl + ${WRAPPER_DL_BYTES[$file]:-0}))
    inst=$((inst + ${WRAPPER_INST_BYTES[$file]:-0}))
    rem=$((rem + ${WRAPPER_INST_BYTES[$file]:-0}))
  fi
  printf 'download_bytes=%s\ninstall_bytes=%s\nremovable_bytes=%s\nupgrade_count=%s\n' "$dl" "$inst" "$rem" "$upg" >&3
  exit 0
}

# Only the wrapper, for someone who picked it on the launcher's page without a benchmark run.
if [ $INSTALL_WRAPPER = 1 ]; then
  trap drop_wrapper_download EXIT
  if installed_before "$WRAPPER_PKG"; then echo "The Vulkan wrapper is already installed."; exit 0; fi
  detail "Bringing the installed packages up to date first."
  pm_upgrade || { say "The packages could not be brought up to date (see the output above), so nothing was installed."; exit 1; }
  if install_wrapper; then say "The Vulkan wrapper is installed."; exit 0; fi
  say "The Vulkan wrapper could not be installed."
  exit 1
fi

# Background helpers we start and must stop again.
SERVER_PID=""; VIRGL_PID=""
stop_virgl()   { [ -n "$VIRGL_PID" ] && kill "$VIRGL_PID" 2>/dev/null; VIRGL_PID=""; pkill -f virgl_test_server_android 2>/dev/null; true; }

# Whether process PID is a termux-x11 server for display :N, read off its command line: the
# server names itself "termux-x11 <package> <its arguments>", and until it has started it is the
# termux-x11 script with the same arguments; should the name not have been set, it is the bare
# app_process running the loader. A process of another user, or one that is gone, is not.
is_x11_server() {
  local args
  args=$(tr '\0' ' ' 2>/dev/null <"/proc/$1/cmdline") || return 1
  case " $args" in *termux-x11*|*app_process*) ;; *) return 1 ;; esac
  case " $args " in *" :$2 "*|*" :$2."*) return 0 ;; esac
  return 1
}

# Whether an X client gets an answer on :N.
display_answers() {
  if command -v xdpyinfo >/dev/null; then
    DISPLAY=":$1" timeout 5 xdpyinfo >/dev/null 2>&1
  else
    DISPLAY=":$1" timeout 10 glmark2-es2 --off-screen -s 64x64 -b build:duration=0.2 >/dev/null 2>&1
  fi
}

# Whether something already holds display :N: a termux-x11 server for it, a live lock, or any
# server answering on its socket. A socket alone proves nothing: a display that was killed
# leaves its socket behind.
display_in_use() {
  local p lpid=""
  for p in /proc/[0-9]*; do is_x11_server "${p#/proc/}" "$1" && return 0; done
  if [ -f "$TMPDIR/.X$1-lock" ]; then
    read -r lpid <"$TMPDIR/.X$1-lock" 2>/dev/null
    lpid=${lpid//[!0-9]/}
    [ -n "$lpid" ] && kill -0 "$lpid" 2>/dev/null && return 0
  fi
  [ -S "$TMPDIR/.X11-unix/X$1" ] && display_answers "$1" && return 0
  return 1
}

# Stops the test display this run started, and nothing else. termux-x11 replaces itself with the
# server, so the pid "&" gave is the server's own; it is checked to still be that server (a pid is
# reused once its process is gone), asked to stop, and killed if it has not within three seconds.
# Its socket and lock go once it has.
stop_display() {
  [ -n "$SERVER_PID" ] || return 0
  local pid=$SERVER_PID
  SERVER_PID=""
  if is_x11_server "$pid" "$DISPLAY_NO"; then
    kill "$pid" 2>/dev/null
    for _ in 1 2 3 4 5 6; do is_x11_server "$pid" "$DISPLAY_NO" || break; sleep 0.5; done
    if is_x11_server "$pid" "$DISPLAY_NO"; then
      kill -9 "$pid" 2>/dev/null
      sleep 0.5
    fi
  fi
  is_x11_server "$pid" "$DISPLAY_NO" ||
    rm -f "$TMPDIR/.X11-unix/X$DISPLAY_NO" "$TMPDIR/.X$DISPLAY_NO-lock" "$HOME/.tmp/.X11-unix/X$DISPLAY_NO"
  true
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
  if [ -n "$where" ]; then "$where" env DEBIAN_FRONTEND=noninteractive apt-get purge "${APT_OPTS[@]}" $remove
  elif [ $PM = pacman ]; then pacman -R --noconfirm $remove
  else apt-get remove "${APT_OPTS[@]}" $remove; fi || {
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
  [ $ESTIMATE = 1 ] && return 0   # an estimate starts and installs nothing, and stops nothing
  [ $CLEANED = 1 ] && return 0; CLEANED=1
  trap '' INT TERM HUP
  stop_virgl; stop_display; drop_wrapper_download
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
# The launcher's list of the ways that fit this chip, best first: CANDIDATES, and per way its
# packages (PKGS), the settings that select it (ENV), a helper to keep running or "-" (HELPER),
# why (WHY), and, for a way known to freeze phones with this chip, why it is not tried (SKIP).
# The launcher owns that list (X11GpuProbe); this script only measures.
declare -A PKGS ENV HELPER WHY SKIP
CANDIDATES=(); GPU_NAME=unknown
if [ -n "$PROFILES" ]; then
  TABLE=$(cat "$PROFILES" 2>/dev/null) || TABLE=""
else
  TABLE=$(launcherctl x11 gpu --table 2>/dev/null) || TABLE=""
fi
case $TABLE in
  *"PKGS['software']="*) eval "$TABLE" ;;
  *) say "Could not get the list of graphics settings from the launcher. Open Settings > Linux apps >"
     say "Graphics acceleration and tap Run benchmark there."
     exit 1 ;;
esac
detail "Graphics chip: $GPU_NAME"
detail "Package manager: $PM"

# ---------------------------------------------------------------------------------------------
step 2 "Deciding what to try"
detail "Will try, in this order:"
n=1
detail "  $n. software — ${WHY[software]} (the baseline)"
for c in "${CANDIDATES[@]}"; do
  n=$((n+1))
  if [ -n "${SKIP[$c]:-}" ]; then detail "  $n. $c — not tried: ${SKIP[$c]}."
  else detail "  $n. $c — ${WHY[$c]}"; fi
done
[ ${#CANDIDATES[@]} -eq 0 ] && detail "(Nothing on this graphics chip can be accelerated, so only software rendering will be checked.)"

ALL_PKGS="$REPO_PKG xkeyboard-config glmark2 ${PKGS[software]}"
for c in "${CANDIDATES[@]}"; do [ -n "${SKIP[$c]:-}" ] || ALL_PKGS="$ALL_PKGS ${PKGS[$c]}"; done
# shellcheck disable=SC2086
ALL_PKGS=$(printf '%s\n' $ALL_PKGS | awk '!seen[$0]++' | tr '\n' ' ')
detail "Packages needed for the tests: $ALL_PKGS"
[ $ESTIMATE = 1 ] && estimate
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
declare -A SCORES STATUSES RENDERERS
for c in "${CANDIDATES[@]}"; do
  if [ -n "${SKIP[$c]:-}" ]; then STATUSES[$c]=skipped; continue; fi
  # shellcheck disable=SC2086
  if install_pkgs ${PKGS[$c]}; then AVAILABLE+=("$c")
  else STATUSES[$c]=unavailable; detail "Skipping $c: its packages are not available here."; fi
done
# With more than one Vulkan driver installed, Turnip has to be named explicitly. The launcher
# adds the same setting when it starts an app, once it sees the second driver.
if [ -n "${ENV[turnip-zink]:-}" ] && [ "$(ls "$ICD_DIR" 2>/dev/null | wc -l)" -gt 1 ]; then
  case " ${ENV[turnip-zink]} " in
    *" VK_ICD_FILENAMES="*) ;;
    *) ENV[turnip-zink]="${ENV[turnip-zink]} VK_ICD_FILENAMES=$ICD_DIR/freedreno_icd.aarch64.json" ;;
  esac
fi
ALREADY=""
for p in $ALL_PKGS; do case " ${WE_INSTALLED[*]} ${NOT_AVAILABLE[*]} " in *" $p "*) ;; *) ALREADY="$ALREADY $p" ;; esac; done
result "newly installed: ${WE_INSTALLED[*]:-none}; already on the phone:${ALREADY:- none}; not available here: ${NOT_AVAILABLE[*]:-none}"

# ---------------------------------------------------------------------------------------------
step 4 "Starting a display of its own for the tests"
if ! command -v termux-x11 >/dev/null; then
  say "The display command (termux-x11) is not installed. Turn the Linux display on in the launcher's settings first."; exit 1
fi
# Never one that is already there: that may be the display you use, and it stays as it is.
for n in $(seq "$DISPLAY_FROM" $((DISPLAY_FROM + 56))); do
  display_in_use "$n" || { DISPLAY_NO=$n; break; }
done
if [ -z "$DISPLAY_NO" ]; then
  say "Every display number from :$DISPLAY_FROM to :$((DISPLAY_FROM + 56)) is in use, so the tests have nowhere to run."; exit 1
fi
TEST_DISPLAY=":$DISPLAY_NO"
SOCKET=$TMPDIR/.X11-unix/X$DISPLAY_NO
if [ -e "$SOCKET" ] || [ -e "$TMPDIR/.X$DISPLAY_NO-lock" ]; then
  detail "Removing what an earlier display $TEST_DISPLAY left behind."
  rm -f "$SOCKET" "$TMPDIR/.X$DISPLAY_NO-lock" "$HOME/.tmp/.X11-unix/X$DISPLAY_NO"
fi
# Headless: the launcher is never told about it, so its own display window is left alone. And no
# fcitx5 beside it, however the real display is set up.
TERMUX_X11_HEADLESS=1 TERMUX_X11_IM=0 termux-x11 "$TEST_DISPLAY" -ac >>"$LOG" 2>&1 &
SERVER_PID=$!
for _ in $(seq 1 20); do
  [ -S "$SOCKET" ] && break
  kill -0 "$SERVER_PID" 2>/dev/null || break
  sleep 0.5
done
if [ ! -S "$SOCKET" ]; then
  say "The test display did not start. Its messages are in $LOG."; exit 1
fi
detail "Test display $TEST_DISPLAY is up. Nothing is shown on screen; the tests draw off-screen."
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

# write_results -> RESULTS_FILE: software first, then every way in the launcher's order, each
# "name<TAB>outcome<TAB>score<TAB>renderer", the outcome one word (ok, crashed, timed-out,
# not-accelerated, skipped, unavailable).
write_results() {
  mkdir -p "$OUT_DIR"
  {
    echo "# Written by x11-gpu-setup.sh on $(date '+%Y-%m-%d %H:%M'). Graphics chip: $GPU_NAME."
    for c in software "${CANDIDATES[@]}"; do
      [ -n "${STATUSES[$c]:-}" ] || continue
      printf '%s\t%s\t%s\t%s\n' "$c" "$(echo "${STATUSES[$c]}" | tr ' ' '-')" "${SCORES[$c]:-0}" \
        "$(echo "${RENDERERS[$c]:-}" | tr '\t\r' '  ')"
    done
  } > "$RESULTS_FILE"
}

detail "Baseline first: software rendering."
run_test software "${ENV[software]}" -
SCORES[software]=$SCORE; STATUSES[software]=$STATUS; RENDERERS[software]=$RENDERER
if [ "$STATUS" != ok ]; then
  write_results
  say "Even software rendering did not finish ($STATUS). The display itself is not working, so no acceleration can be chosen. See $LOG."
  exit 1
fi
BASE=$SCORE
result "software: score $BASE, drawn by \"$RENDERER\""

# Every way is tried, so the launcher's page can show each one's result; the fastest that is
# clearly faster than software wins.
WINNER=software; WINNER_SCORE=$BASE
for c in "${AVAILABLE[@]}"; do
  detail "Trying $c ..."
  run_test "$c" "${ENV[$c]}" "${HELPER[$c]}"
  SCORES[$c]=$SCORE; STATUSES[$c]=$STATUS; RENDERERS[$c]=$RENDERER
  case $STATUS in
    ok)
      if [ "$SCORE" -ge $((BASE * 2)) ]; then
        result "$c: score $SCORE, drawn by \"$RENDERER\" — works, and clearly faster than software."
        if [ "$SCORE" -gt "$WINNER_SCORE" ] || [ "$WINNER" = software ]; then WINNER=$c; WINNER_SCORE=$SCORE; fi
      else
        result "$c: score $SCORE — works, but not faster enough than software ($BASE) to be worth it."
      fi ;;
    "not accelerated") result "$c: the graphics chip was not used (drawn by \"$RENDERER\"); a setting was ignored or a driver is missing." ;;
    *) result "$c: $STATUS." ;;
  esac
done
write_results

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
    pd sh -c "export DEBIAN_FRONTEND=noninteractive; apt-get update && apt-get install ${APT_OPTS[*]} $PROOT_TEST_PKGS" ||
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
      if [ -z "${ENV[virgl]:-}" ] || [ -n "${SKIP[virgl]:-}" ]; then
        detail "virgl is not tried on this graphics chip, so software is the only option inside Debian."
      elif command -v virgl_test_server_android >/dev/null; then
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
detail "Every way's result is on the launcher's Graphics acceleration page, where you can pick another one."
detail "If an app shows a black window or wrong colours, the launcher's Display options have two compatibility switches for that."
