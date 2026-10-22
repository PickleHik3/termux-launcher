#!/usr/bin/env bash
# x11-im-focus.sh — tell the launcher when a text field on the display takes or loses focus.
#
# Installed as termux-x11-im. The launcher's termux-x11 starts it beside the display server, and
# only when fcitx5 is installed; it is not meant to be run by hand. Every program it needs comes
# with fcitx5 itself: bash, and the dbus package's dbus-daemon, dbus-monitor, dbus-send and
# dbus-test-tool. No Python, nothing to compile.
#
#   termux-x11-im SERVER_PID BUS_PREFIX DISPLAY
#
# DISPLAY is the server's own display (:N or :N.S), which the server script picks out of the
# arguments it hands the server; :0 when it is empty or not a display.
#
# What it does, in order:
#   0. Takes the display's lock, BUS_PREFIX-N.lock: one helper per display. A second start for
#      the same display waits while the helper there serves a live server, and leaves once its
#      own server has gone; a helper whose server has gone is stopped, and a lock whose owner
#      went without letting go is taken over.
#   1. Starts a session bus for this display at BUS_PREFIX-N, N being the display's number. The
#      launcher hands the same address to every app it starts, so the apps' GTK and Qt modules
#      and fcitx5 meet on one bus. Nothing in Termux starts one otherwise: a plain app on the
#      display has no session bus at all. The helper's pid is in BUS_PREFIX-N.pid while it runs,
#      so an app is only handed a bus somebody is still answering on.
#   2. Claims fcitx5's virtual-keyboard name, org.fcitx.Fcitx5.VirtualKeyboard, with
#      dbus-test-tool, which answers every call with an empty reply. fcitx5 only calls a virtual
#      keyboard whose name somebody owns, and it notices an owner whenever one comes.
#   3. Starts fcitx5 on that bus and on the display, once the display is up.
#   4. Passes fcitx5's ShowVirtualKeyboard on as `launcherctl keyboard show --source focus` and
#      its HideVirtualKeyboard as `... hide --source focus`. fcitx5 makes those calls itself when
#      a text field takes focus and when it loses it.
#   5. Takes all of it down again when the display server, or fcitx5, exits.
#
# One thing in step 4 is not obvious. fcitx5 calls the virtual keyboard only while it is in
# on-screen-keyboard mode, which a phone build starts in, and it leaves that mode at the first
# key it takes for a hardware one, hiding the virtual keyboard as it goes. The launcher's own
# keyboard types into the display as X keys, so to fcitx5 it is hardware: the first letter typed
# would hide the very keyboard it was typed on, and after it no focus change would be heard. So a
# hide is only passed on while fcitx5 is still in that mode (its current UI is still the virtual
# keyboard); otherwise it is the mode changing, and fcitx5 is put straight back. The show that
# putting it back makes is not passed on: the keyboard is up already, the user is typing on it.
# Only one show is let go, and only one that comes within ECHO_WINDOW_MS of the helper's own
# call: fcitx5 answers with a show only while a field has focus, so a later show is a field's.
#
# It writes what it did to BUS_PREFIX-N.log, overwritten at each start.

set -u

SERVER=${1:?server pid}
BUS_PREFIX=${2:?bus prefix}
DISPLAY_NAME=${3:-:0}

case "$DISPLAY_NAME" in
  :*) NUMBER=${DISPLAY_NAME#:}; NUMBER=${NUMBER%%.*} ;;
  *) NUMBER="" ;;
esac
case "$NUMBER" in
  "" | *[!0-9]*) DISPLAY_NAME=:0; NUMBER=0 ;;
esac

BUS="$BUS_PREFIX-$NUMBER"
PID_FILE="$BUS.pid"
LOCK="$BUS.lock"
TMP_DIR=${BUS_PREFIX%/*}
X_SOCKET="$TMP_DIR/.X11-unix/X$NUMBER"

FCITX=org.fcitx.Fcitx5
VK=org.fcitx.Fcitx5.VirtualKeyboard
VK_INTERFACE=org.fcitx.Fcitx5.VirtualKeyboard1
ECHO_WINDOW_MS=300

say() { echo "termux-x11-im: $*"; }
server_alive() { kill -0 "$SERVER" 2> /dev/null; }
# A live termux-x11-im other than this one: a pid alone may have been handed on since.
is_helper() {
  [ -n "$1" ] && [ "$1" != "$$" ] && kill -0 "$1" 2> /dev/null || return 1
  case "$(tr '\0' ' ' < "/proc/$1/cmdline" 2> /dev/null)" in
    *termux-x11-im*) return 0 ;;
  esac
  return 1
}

OWN_LOCK=""
DAEMON=""; ECHO=""; FCITX5=""; MONITOR=""; READER=""
cleanup() {
  for pid in "$READER" "$MONITOR" "$FCITX5" "$ECHO" "$DAEMON"; do
    [ -n "$pid" ] && kill "$pid" 2> /dev/null
  done
  # The files are only touched by the lock's owner, and the lock goes last.
  if [ -n "$OWN_LOCK" ]; then
    rm -f "$BUS" "$PID_FILE" "$LOCK"
  fi
}
trap cleanup EXIT
trap 'exit 0' TERM INT HUP

# 0. The display's lock: a symbolic link naming its owner and the owner's server, made in one
# step, so there is never a lock with nobody named in it.
waited=0
until ln -sn "$$:$SERVER" "$LOCK" 2> /dev/null; do
  owner=$(readlink "$LOCK" 2> /dev/null)
  if [ -z "$owner" ]; then
    # Gone between the two looks, or something that is not a lock in its place.
    [ -L "$LOCK" ] || rm -rf "$LOCK"
    sleep 0.05
    continue
  fi
  owner_pid=${owner%%:*}
  owner_server=${owner#*:}
  if ! is_helper "$owner_pid"; then
    # Its owner went without letting go (killed outright). One taker per dead owner: whoever
    # makes the gate checks the lock is still that owner's, and takes it away.
    if mkdir "$LOCK.gone-$owner_pid" 2> /dev/null; then
      [ "$(readlink "$LOCK" 2> /dev/null)" = "$owner" ] && rm -f "$LOCK"
      rmdir "$LOCK.gone-$owner_pid" 2> /dev/null
    else
      sleep 0.1
    fi
    continue
  fi
  if ! kill -0 "$owner_server" 2> /dev/null; then
    # A helper left from this display's last life, stopped and started again inside the few
    # seconds that one takes to notice. It would take the new bus's socket away on its way out.
    kill "$owner_pid" 2> /dev/null
    n=0
    while is_helper "$owner_pid" && [ "$n" -lt 30 ]; do sleep 0.1; n=$((n + 1)); done
    is_helper "$owner_pid" && kill -9 "$owner_pid" 2> /dev/null
    continue
  fi
  # Two starts for the one display, both servers still up: one of them is about to fail, and
  # the helper whose server goes is the one that leaves.
  server_alive || exit 0
  if [ "$waited" -lt 50 ]; then
    waited=$((waited + 1))
    sleep 0.1
  else
    sleep 1
  fi
done
OWN_LOCK=1
echo "$$" > "$PID_FILE"

exec > "$BUS.log" 2>&1

for tool in fcitx5 dbus-daemon dbus-monitor dbus-send dbus-test-tool launcherctl; do
  if ! command -v "$tool" > /dev/null 2>&1; then
    say "$tool is not installed; nothing started"
    exit 0
  fi
done

# Waits up to $1 tenths of a second for the test that follows; false if it never passes.
within() {
  local tries=$1 n=0
  shift
  until "$@"; do
    n=$((n + 1))
    [ "$n" -le "$tries" ] || return 1
    sleep 0.1
  done
}
has_owner() {
  dbus-send --print-reply=literal --dest=org.freedesktop.DBus /org/freedesktop/DBus \
    org.freedesktop.DBus.NameHasOwner "string:$1" 2> /dev/null | grep -q true
}
current_ui() {
  local ui
  ui=$(dbus-send --print-reply=literal --dest="$FCITX" /controller \
    org.fcitx.Fcitx.Controller1.CurrentUI 2> /dev/null)
  echo "${ui//[[:space:]]/}"
}
# Milliseconds into NOW_MS, from bash's own clock; whole seconds on a bash too old to have it.
now_ms() {
  local t=${EPOCHREALTIME:-}
  if [ -n "$t" ]; then
    t=${t/[.,]/}
    NOW_MS=$((10#$t / 1000))
  else
    NOW_MS=$((SECONDS * 1000))
  fi
}
# fcitx5's own virtual-keyboard object, which puts it in on-screen-keyboard mode when told to show.
fcitx_keyboard() {
  dbus-send --print-reply --dest="$FCITX" /virtualkeyboard \
    "org.fcitx.Fcitx.VirtualKeyboard1.$1" > /dev/null 2>&1
}

# 1. The display's session bus.
rm -f "$BUS"
dbus-daemon --session --nofork --nopidfile --address="unix:path=$BUS" &
DAEMON=$!
if ! within 50 test -S "$BUS"; then say "the session bus did not come up"; exit 0; fi
export DBUS_SESSION_BUS_ADDRESS="unix:path=$BUS"

# 2. The virtual keyboard's name, before fcitx5 looks for it.
dbus-test-tool echo --name="$VK" &
ECHO=$!
if ! within 50 has_owner "$VK"; then say "could not claim $VK"; exit 0; fi

# 3. fcitx5, once the display it is for is up. It leaves by itself when the display goes.
if ! within 300 eval 'server_alive && test -S "$X_SOCKET"'; then
  say "display $DISPLAY_NAME never came up"
  exit 0
fi
DISPLAY=$DISPLAY_NAME XDG_SESSION_TYPE=x11 fcitx5 &
FCITX5=$!
if ! within 150 has_owner "$FCITX"; then say "fcitx5 did not come up"; exit 0; fi
within 30 eval '[ -n "$(current_ui)" ]'
if [ "$(current_ui)" != virtualkeyboard ]; then
  # Not a phone build, or not in on-screen mode yet: put it there. Nothing listens yet, so the
  # show and hide this makes reach the launcher as nothing.
  fcitx_keyboard ShowVirtualKeyboard
  fcitx_keyboard HideVirtualKeyboard
fi
say "fcitx5 on $DISPLAY_NAME, ui $(current_ui)"

# 4. fcitx5's show and hide, passed on. The monitor is read through a descriptor of this
# shell's rather than a pipe, so the pid kept is dbus-monitor's own and it goes with the helper.
exec 3< <(exec dbus-monitor "type='method_call',interface='$VK_INTERFACE'" 2> /dev/null)
MONITOR=$!
{
  # When the helper last put fcitx5 back in on-screen mode, while the show that answers it may
  # still come; empty once one has come or the window has passed.
  echo_from=""
  while read -r line; do
    case "$line" in
      *member=ShowVirtualKeyboard*)
        if [ -n "$echo_from" ]; then
          now_ms
          if [ $((NOW_MS - echo_from)) -le "$ECHO_WINDOW_MS" ]; then
            echo_from=""
            continue
          fi
          echo_from=""
        fi
        say "focus in"
        launcherctl keyboard show --source focus > /dev/null 2>&1
        ;;
      *member=HideVirtualKeyboard*)
        ui=$(current_ui)
        if [ -z "$ui" ] || [ "$ui" = virtualkeyboard ]; then
          echo_from=""
          say "focus out"
          launcherctl keyboard hide --source focus > /dev/null 2>&1
        else
          # A key fcitx5 took for hardware switched it out of on-screen mode: switch it back, and
          # let the one show that answers it go by.
          now_ms
          echo_from=$NOW_MS
          fcitx_keyboard ShowVirtualKeyboard &
        fi
        ;;
    esac
  done
} <&3 &
READER=$!
exec 3<&-

# 5. Until the display, or fcitx5, is gone.
while server_alive && kill -0 "$FCITX5" 2> /dev/null; do
  sleep 5 &
  wait $!
done
say "display $DISPLAY_NAME ended"
