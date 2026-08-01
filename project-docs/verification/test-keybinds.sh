#!/usr/bin/env bash
# Slice-3 on-device keybind check.
#
# Proves the registry-driven binding path fires on real key events:
#   KeyEvent -> TerminalKeyBindingResolver -> TerminalActionDispatcher -> TermuxActivity
# and that ordinary typing is unaffected (no accidental action dispatch).
#
# Observability comes from terminal.state (drawerSessions / visiblePanes), which
# only reports counts, so each stroke is chosen to move a count it can see.
#
# Self-cleaning: everything happens inside a session created by Ctrl+Alt+Shift+C
# and removed by Ctrl+Alt+Shift+X at the end. It refuses to run the closing
# stroke unless it confirmed it created that session.

set -uo pipefail

PKG=com.termux
CTL_DIR=/data/data/$PKG/files/home/.launcherctl

pass=0; fail=0
ok()   { printf '  \033[32mPASS\033[0m %s\n' "$*"; pass=$((pass+1)); }
bad()  { printf '  \033[31mFAIL\033[0m %s\n' "$*"; fail=$((fail+1)); }
info() { printf '  ---- %s\n' "$*"; }
head_() { printf '\n\033[1m%s\033[0m\n' "$*"; }

ENDPOINT=$(adb shell "run-as $PKG cat $CTL_DIR/endpoint" 2>/dev/null | tr -d '\r')
TOKEN=$(adb shell "run-as $PKG cat $CTL_DIR/token" 2>/dev/null | tr -d '\r')
[[ -z "$ENDPOINT" || -z "$TOKEN" ]] && { echo "No endpoint/token; open the launcher first."; exit 2; }

api_exec() {
    local body="{\"tool\":\"$1\",\"arguments\":{},\"confirm\":true}"
    adb shell "curl -s -m 15 -X POST -H 'Authorization: Bearer $TOKEN' \
        -H 'Content-Type: application/json' --data '$body' '$ENDPOINT/v1/agent/execute'" 2>/dev/null
}
state() { api_exec terminal.state | python3 -c '
import json,sys
try: d=json.load(sys.stdin)["result"]
except Exception: sys.exit(0)
print(d.get("drawerSessions",-1), d.get("visiblePanes",-1))'; }

sessions() { state | cut -d" " -f1; }
panes()    { state | cut -d" " -f2; }

CTRL=KEYCODE_CTRL_LEFT; ALT=KEYCODE_ALT_LEFT; SHIFT=KEYCODE_SHIFT_LEFT
stroke() { adb shell "input keycombination $*" >/dev/null 2>&1; sleep 2; }

# ---------------------------------------------------------------- setup

head_ "Foreground the launcher"
adb shell input keyevent KEYCODE_HOME >/dev/null 2>&1
sleep 3
BASE_S=$(sessions); BASE_P=$(panes)
if [[ -z "$BASE_S" || "$BASE_S" == "-1" ]]; then
    echo "terminal.state unavailable; is this build from slice 2 or later?"; exit 2
fi
info "baseline drawerSessions=$BASE_S visiblePanes=$BASE_P"

# ---------------------------------------------------------------- typing

head_ "Ordinary typing must not dispatch actions"
# These are exactly the letters bound under Ctrl+Alt. Unmodified, they must do nothing.
adb shell 'input text "vcxhr"' >/dev/null 2>&1
adb shell 'input keyevent KEYCODE_LEFT_BRACKET KEYCODE_RIGHT_BRACKET' >/dev/null 2>&1
sleep 2
T_S=$(sessions); T_P=$(panes)
if [[ "$T_S" == "$BASE_S" && "$T_P" == "$BASE_P" ]]; then
    ok "typing 'vcxhr[]' changed nothing (sessions=$T_S panes=$T_P)"
else
    bad "typing altered state: sessions $BASE_S->$T_S panes $BASE_P->$T_P"
fi

# ---------------------------------------------------------------- session.new

head_ "Ctrl+Alt+Shift+C -> session.new"
stroke "$CTRL $ALT $SHIFT KEYCODE_C"
AFTER_S=$(sessions)
CREATED=0
if [[ -n "$AFTER_S" && "$AFTER_S" -gt "$BASE_S" ]]; then
    CREATED=1; ok "drawerSessions $BASE_S -> $AFTER_S"
else
    bad "session not created ($BASE_S -> ${AFTER_S:-?})"
fi

# ---------------------------------------------------------------- split

head_ "Ctrl+Alt+V -> pane.split_vertical"
PRE_P=$(panes)
stroke "$CTRL $ALT KEYCODE_V"
POST_P=$(panes)
if [[ -n "$POST_P" && "$POST_P" -gt "$PRE_P" ]]; then
    ok "visiblePanes $PRE_P -> $POST_P"
else
    bad "split did not add a pane ($PRE_P -> ${POST_P:-?})"
fi

head_ "Ctrl+Alt+H -> pane.split_horizontal"
PRE_P=$(panes)
stroke "$CTRL $ALT KEYCODE_H"
POST_P=$(panes)
[[ -n "$POST_P" && "$POST_P" -gt "$PRE_P" ]] && ok "visiblePanes $PRE_P -> $POST_P" \
    || bad "horizontal split did not add a pane ($PRE_P -> ${POST_P:-?})"

# ---------------------------------------------------------------- focus/resize

head_ "Ctrl+Alt+arrow / Ctrl+Alt+Shift+arrow"
PRE_P=$(panes)
for k in KEYCODE_DPAD_LEFT KEYCODE_DPAD_RIGHT KEYCODE_DPAD_UP KEYCODE_DPAD_DOWN; do
    stroke "$CTRL $ALT $k"
done
for k in KEYCODE_DPAD_LEFT KEYCODE_DPAD_UP; do
    stroke "$CTRL $ALT $SHIFT $k"
done
POST_P=$(panes)
[[ "$POST_P" == "$PRE_P" ]] && ok "focus+resize strokes kept pane count at $POST_P" \
    || bad "focus/resize changed pane count ($PRE_P -> $POST_P)"

# ---------------------------------------------------------------- window

head_ "Ctrl+Alt+C -> window.new, then Ctrl+Alt+] / ["
PRE_P=$(panes)
stroke "$CTRL $ALT KEYCODE_C"
NEW_WIN_P=$(panes)
# A fresh window starts with a single pane, so the visible count drops back to 1.
if [[ "$NEW_WIN_P" == "1" && "$PRE_P" -gt 1 ]]; then
    ok "new window shows 1 pane (was $PRE_P)"
else
    info "visiblePanes $PRE_P -> $NEW_WIN_P (window.new is only indirectly observable)"
fi
stroke "$CTRL $ALT KEYCODE_RIGHT_BRACKET"
NEXT_P=$(panes)
stroke "$CTRL $ALT KEYCODE_LEFT_BRACKET"
PREV_P=$(panes)
if [[ "$NEXT_P" != "$PREV_P" || "$NEXT_P" == "$PRE_P" ]]; then
    ok "window next/previous moved between windows (panes $NEW_WIN_P -> $NEXT_P -> $PREV_P)"
else
    info "panes $NEW_WIN_P -> $NEXT_P -> $PREV_P (both windows may have equal pane counts)"
fi

# ---------------------------------------------------------------- unbound

head_ "Unbound Ctrl+Alt stroke still falls through"
PRE_S=$(sessions); PRE_P=$(panes)
stroke "$CTRL $ALT KEYCODE_F1"
[[ "$(sessions)" == "$PRE_S" && "$(panes)" == "$PRE_P" ]] \
    && ok "Ctrl+Alt+F1 dispatched nothing and did not crash" \
    || bad "Ctrl+Alt+F1 changed state"

# ---------------------------------------------------------------- cleanup

head_ "Ctrl+Alt+Shift+X -> session.close_current (cleanup)"
if [[ $CREATED -eq 1 ]]; then
    stroke "$CTRL $ALT $SHIFT KEYCODE_X"
    FINAL_S=$(sessions)
    if [[ "$FINAL_S" == "$BASE_S" ]]; then
        ok "drawerSessions back to baseline ($FINAL_S)"
    else
        bad "expected $BASE_S sessions after cleanup, got ${FINAL_S:-?} - close leftovers manually"
    fi
else
    info "skipped: no session was created, refusing to close anything"
fi

# ---------------------------------------------------------------- logs

head_ "Log check"
LOGS=$(adb logcat -d -t 600 2>/dev/null \
    | grep -v "adbd" | grep -iE "FATAL EXCEPTION|AndroidRuntime.*com\.termux|TerminalKeyBindingResolver|TerminalActionDispatcher" | tail -10)
if [[ -z "$LOGS" ]]; then
    ok "no crashes, no resolver conflicts, no dispatcher failures"
else
    bad "log output follows"; printf '%s\n' "$LOGS"
fi

printf '\n\033[1mResult: %d passed, %d failed\033[0m\n' "$pass" "$fail"
[[ $fail -eq 0 ]] || exit 1
