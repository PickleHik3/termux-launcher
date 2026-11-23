#!/usr/bin/env bash
# x11-im-focus.sh — tell the launcher when a text field on the display takes or loses focus.
#
# Installed as termux-x11-im. The launcher's termux-x11 starts it beside the display server, and
# only when fcitx5 is installed; it is not meant to be run by hand. Every program it needs comes
# with fcitx5 itself: bash, and the dbus package's dbus-daemon, dbus-monitor, dbus-send and
# dbus-test-tool. No Python, nothing to compile.
#
#   termux-x11-im SERVER_PID BUS_PREFIX [SERVER_ARGUMENTS...]
#
# What it does, in order:
#   1. Starts a session bus for this display at BUS_PREFIX-N, N being the display's number from
#      the server's own arguments (:0 when none is given). The launcher hands the same address to
#      every app it starts, so the apps' GTK and Qt modules and fcitx5 meet on one bus. Nothing
#      in Termux starts one otherwise: a plain app on the display has no session bus at all.
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
#
# It writes what it did to BUS_PREFIX-N.log, overwritten at each start.

set -u

SERVER=${1:?server pid}
BUS_PREFIX=${2:?bus prefix}
shift 2

DISPLAY_NAME=:0
for arg in "$@"; do
  case "$arg" in
    :[0-9]*) DISPLAY_NAME=$arg ;;
  esac
done
NUMBER=${DISPLAY_NAME#:}
NUMBER=${NUMBER%%.*}

BUS="$BUS_PREFIX-$NUMBER"
PID_FILE="$BUS.pid"
TMP_DIR=${BUS_PREFIX%/*}
X_SOCKET="$TMP_DIR/.X11-unix/X$NUMBER"

FCITX=org.fcitx.Fcitx5
VK=org.fcitx.Fcitx5.VirtualKeyboard
VK_INTERFACE=org.fcitx.Fcitx5.VirtualKeyboard1

exec > "$BUS.log" 2>&1
say() { echo "termux-x11-im: $*"; }

for tool in fcitx5 dbus-daemon dbus-monitor dbus-send dbus-test-tool launcherctl; do
  if ! command -v "$tool" > /dev/null 2>&1; then
    say "$tool is not installed; nothing started"
    exit 0
  fi
done

# A helper left from this display's last life — stopped and started again inside the few seconds
# that one takes to notice — would take the new bus's socket away on its way out. It goes first.
old=$(cat "$PID_FILE" 2>/dev/null)
if [ -n "$old" ] && [ "$old" != "$$" ] && kill -0 "$old" 2>/dev/null; then
  case "$(tr '\0' ' ' < "/proc/$old/cmdline" 2>/dev/null)" in
    *termux-x11-im*)
      kill "$old" 2>/dev/null
      n=0
      while kill -0 "$old" 2>/dev/null && [ "$n" -lt 30 ]; do sleep 0.1; n=$((n + 1)); done
      ;;
  esac
fi
echo "$$" > "$PID_FILE"

DAEMON=""; ECHO=""; FCITX5=""; MONITOR=""
cleanup() {
  for pid in "$MONITOR" "$FCITX5" "$ECHO" "$DAEMON"; do
    [ -n "$pid" ] && kill "$pid" 2> /dev/null
  done
  # Only this helper's own files: a newer one for the same display may have them by now.
  if [ "$(cat "$PID_FILE" 2>/dev/null)" = "$$" ]; then
    rm -f "$BUS" "$PID_FILE"
  fi
}
trap cleanup EXIT
trap 'exit 0' TERM INT HUP

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
server_alive() { kill -0 "$SERVER" 2> /dev/null; }
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

# 4. fcitx5's show and hide, passed on.
dbus-monitor "type='method_call',interface='$VK_INTERFACE'" 2> /dev/null | {
  echo_until=-1
  while read -r line; do
    case "$line" in
      *member=ShowVirtualKeyboard*)
        if [ "$SECONDS" -le "$echo_until" ]; then
          echo_until=-1
          continue
        fi
        say "focus in"
        launcherctl keyboard show --source focus > /dev/null 2>&1
        ;;
      *member=HideVirtualKeyboard*)
        ui=$(current_ui)
        if [ -z "$ui" ] || [ "$ui" = virtualkeyboard ]; then
          say "focus out"
          launcherctl keyboard hide --source focus > /dev/null 2>&1
        else
          # A key fcitx5 took for hardware switched it out of on-screen mode: switch it back, and
          # let the show that answers it go by.
          echo_until=$((SECONDS + 2))
          fcitx_keyboard ShowVirtualKeyboard &
        fi
        ;;
    esac
  done
} &
MONITOR=$!

# 5. Until the display, or fcitx5, is gone.
while server_alive && kill -0 "$FCITX5" 2> /dev/null; do
  sleep 5 &
  wait $!
done
say "display $DISPLAY_NAME ended"
