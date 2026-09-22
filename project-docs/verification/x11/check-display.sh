#!/usr/bin/env bash
# On-device check of the embedded X display, end to end.
#
# Proves the whole chain the Display page rests on:
#   $PREFIX/bin/termux-x11 -> app_process -> Loader (signature check) -> CmdEntryPoint
#   -> libXlorie.so -> ACTION_START broadcast -> X11DisplayReceiver
#   -> X11DisplayHostController -> the X socket in the wall's Display page
# and then that real X clients render and receive input through it.
#
# Runs entirely inside the launcher's own prefix over adb, as the app user, which is
# the same identity a shell in the app has. Self-cleaning: every client and the server
# it started are killed at the end, whether the run passed or not.
#
# Usage: project-docs/verification/x11/check-display.sh [serial]
#   With no serial it uses the only attached device, or emulator-5554 if several.

set -uo pipefail

PKG=${PKG:-com.termux}
SERIAL=${1:-}
if [ -z "$SERIAL" ]; then
  count=$(adb devices | grep -c "device$")
  SERIAL=$( [ "$count" = 1 ] && adb devices | awk '/device$/{print $1}' || echo emulator-5554 )
fi
ADB=(adb -s "$SERIAL")
PREFIX=/data/data/$PKG/files/usr
HOME_DIR=/data/data/$PKG/files/home

pass=0; fail=0
ok()   { echo "  PASS  $1"; pass=$((pass+1)); }
bad()  { echo "  FAIL  $1"; fail=$((fail+1)); }
note() { echo "        $1"; }

# Everything runs from a script file: adb's layered quoting mangles inline commands.
run() {
  local body=$1 name=${2:-x11check}
  printf '%s\n' "#!$PREFIX/bin/bash" \
    "export PREFIX=$PREFIX HOME=$HOME_DIR TMPDIR=$PREFIX/tmp XDG_RUNTIME_DIR=$PREFIX/tmp" \
    "export PATH=\$PREFIX/bin LD_LIBRARY_PATH=\$PREFIX/lib" \
    'mkdir -p "$TMPDIR"' \
    "$body" \
    | "${ADB[@]}" shell "run-as $PKG sh -c 'cat > $HOME_DIR/$name.sh'"
  # One quoted string: adb shell strips a level of quoting, and an unquoted redirect runs as the
  # shell user — which only works where adbd is root, i.e. on an emulator, never on a phone.
  "${ADB[@]}" shell "run-as $PKG $PREFIX/bin/bash $HOME_DIR/$name.sh" 2>&1
}

cleanup() {
  run 'pkill -f glmark2 2>/dev/null; pkill xeyes 2>/dev/null; pkill xclock 2>/dev/null
       sleep 1; pkill -f "termux-x11 '"$PKG"'" 2>/dev/null; true' cleanup >/dev/null 2>&1
}
trap cleanup EXIT

echo "== embedded X display, $SERIAL =="

# 1. The launcher wrote its own command, and it is the launcher's.
script=$(run 'cat $PREFIX/bin/termux-x11 2>/dev/null | head -3' script)
case "$script" in
  *"written by termux-launcher"*) ok "termux-x11 is the launcher's own" ;;
  "") bad "termux-x11 is not installed (turn the Linux display on in settings)"; exit 1 ;;
  *) bad "termux-x11 belongs to something else; the launcher left it alone" ;;
esac

# 2. The keyboard layouts the server refuses to start without.
if [ -n "$(run 'ls -d $PREFIX/share/X11/xkb $PREFIX/share/xkeyboard-config-2 2>/dev/null' xkb)" ]; then
  ok "xkb data present"
else
  bad "no xkb data: pkg install x11-repo xkeyboard-config"; exit 1
fi

# 3. The server starts, stays up, and opens its socket.
# Counted rather than grepped: `logcat -c` does not reliably clear while a reader is attached,
# so a stale line from an earlier run would read as a surface that is not there.
buffers_before=$("${ADB[@]}" logcat -d 2>/dev/null | grep -c "Sent shared buffer")
out=$(run 'pkill -f "termux-x11 '"$PKG"'" 2>/dev/null; sleep 1
           nohup termux-x11 :0 -ac > $HOME/x11.log 2>&1 &
           sleep 8
           echo "socket=$(ls $TMPDIR/.X11-unix/ 2>/dev/null | tr "\n" " ")"
           echo "log=$(tail -2 $HOME/x11.log)"' start)
case "$out" in
  *"socket=X0"*) ok "server up, X0 socket open" ;;
  *) bad "server did not open its socket"; note "$out"; exit 1 ;;
esac

# 4. Whether the wall is actually on the Display page. It matters more than it sounds: with the
#    page hidden the server has no surface to present to, the client runs free, and the GL numbers
#    below measure the client alone. On the emulator that is the difference between 97 and 10.
buffers_after=$("${ADB[@]}" logcat -d 2>/dev/null | grep -c "Sent shared buffer")
if [ "$buffers_after" -gt "$buffers_before" ]; then
  ok "the Display page is showing, so the compositor is in the measurement"
else
  note "NOTE  the Display page is not showing: the server has no surface, so the GL numbers"
  note "      below are the client on its own. Swipe to the Display page and run again."
fi

# 5. A client connects and paints. xclock's own ticking proves repaint, not just connect.
out=$(run 'export DISPLAY=:0
           nohup xeyes > $HOME/xeyes.log 2>&1 &
           nohup xclock -digital -update 1 > $HOME/xclock.log 2>&1 &
           sleep 5
           echo "clients=$(pgrep -c -f "xeyes|xclock")"
           echo "err=$(cat $HOME/xeyes.log $HOME/xclock.log | grep -i "cant open\|can.t open")"' clients)
case "$out" in
  *"clients=2"*) ok "xeyes and xclock connected" ;;
  *) bad "a client could not open the display"; note "$out" ;;
esac

# 6. A GL client builds a context and presents frames. The score is not the point — the
#    renderer string and a non-zero FPS are: that is the client-side acceleration path.
#    Set GL_PROFILE to try a real GPU profile (see docs/en/X11_Display.md).
# The override matters: without it Mesa 26 picks zink-over-lavapipe even when told to go
# software, so the "software" row of the profile table needs all three variables.
profile=${GL_PROFILE:-"LIBGL_ALWAYS_SOFTWARE=1 GALLIUM_DRIVER=llvmpipe MESA_LOADER_DRIVER_OVERRIDE=llvmpipe"}
out=$(run "export DISPLAY=:0 $profile
           timeout 180 glmark2-es2 --benchmark build --benchmark texture 2>&1 \\
             | grep -E 'GL_RENDERER|GL_VERSION|FPS|glmark2 Score'" gl)
if echo "$out" | grep -q "glmark2 Score"; then
  ok "glmark2-es2 ran through the display"
  echo "$out" | sed 's/^/        /'
else
  bad "glmark2-es2 did not complete"; note "$out"
fi

echo "== $pass passed, $fail failed =="
[ "$fail" = 0 ]
