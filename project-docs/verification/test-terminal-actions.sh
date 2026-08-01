#!/usr/bin/env bash
# On-device probe for the slice-2 terminal action tools.
#
# Verifies the path  /v1/agent/execute -> LauncherToolExecutionHandler
#                    -> TerminalActionDispatcher -> TermuxActivity
# which unit tests cannot cover (loopback binding is blocked on the dev host).
#
# Safe by default: read-only checks, pane/window creation inside a dedicated
# test session, and the 409-when-backgrounded path. Actions that terminate a
# shell run only with --destructive, and only after confirming the test session
# really was created (so cleanup can never close a session the user was using).
#
# Usage:
#   ./test-terminal-actions.sh              # safe tier
#   ./test-terminal-actions.sh --destructive  # adds kill/close on the test session

set -uo pipefail

DESTRUCTIVE=0
[[ "${1:-}" == "--destructive" ]] && DESTRUCTIVE=1

PKG=com.termux
HOME_DIR=/data/data/$PKG/files/home
CTL_DIR=$HOME_DIR/.launcherctl
TEST_SESSION="slice2-probe"

pass=0; fail=0
ok()   { printf '  \033[32mPASS\033[0m %s\n' "$*"; pass=$((pass+1)); }
bad()  { printf '  \033[31mFAIL\033[0m %s\n' "$*"; fail=$((fail+1)); }
info() { printf '  ---- %s\n' "$*"; }
head_() { printf '\n\033[1m%s\033[0m\n' "$*"; }

# ---------------------------------------------------------------- endpoint

head_ "Endpoint"
ENDPOINT=$(adb shell "run-as $PKG cat $CTL_DIR/endpoint" 2>/dev/null | tr -d '\r')
TOKEN=$(adb shell "run-as $PKG cat $CTL_DIR/token" 2>/dev/null | tr -d '\r')
if [[ -z "$ENDPOINT" || -z "$TOKEN" ]]; then
    echo "Could not read endpoint/token from $CTL_DIR."
    echo "Open the launcher once so LauncherCtlApiServer starts, then retry."
    exit 2
fi
info "endpoint $ENDPOINT"
info "token    ${#TOKEN} chars"

# curl runs on the device: the API binds 127.0.0.1 in the device's netstack.
api_get() {
    adb shell "curl -s -m 10 -H 'Authorization: Bearer $TOKEN' '$ENDPOINT$1'" 2>/dev/null
}
api_exec() {
    local tool="$1" args="${2:-{\}}"
    local body="{\"tool\":\"$tool\",\"arguments\":$args,\"confirm\":true}"
    adb shell "curl -s -m 15 -X POST -H 'Authorization: Bearer $TOKEN' \
        -H 'Content-Type: application/json' --data '$body' '$ENDPOINT/v1/agent/execute'" 2>/dev/null
}

# field <dotted.path>  -> JSON-normalized value ("true"/"false", not Python bools)
field() { python3 -c '
import json,sys
try: d=json.loads(sys.stdin.read())
except Exception: sys.exit(0)
for k in sys.argv[1].split("."):
    if isinstance(d,dict) and k in d: d=d[k]
    else: sys.exit(0)
print(d if isinstance(d,str) else json.dumps(d))' "$1"; }

# The API nests the failure code differently per surface: top-level "code",
# "error.code", or a flat "error" string. Return whichever is present.
errcode() { python3 -c '
import json,sys
try: d=json.loads(sys.stdin.read())
except Exception: sys.exit(0)
for path in (("code",),("error","code"),("error",),("result","code")):
    v=d
    for k in path:
        if isinstance(v,dict) and k in v: v=v[k]
        else: v=None; break
    if isinstance(v,str): print(v); break'; }

state_field() { api_exec terminal.state | field "result.$1"; }

# ---------------------------------------------------------------- tools list

head_ "Registry over HTTP"
TOOLS_JSON=$(api_get /v1/agent/tools)
COUNT=$(printf '%s' "$TOOLS_JSON" | field count)
# 28 static registry tools plus any tools discovered from configured MCP servers.
[[ -n "$COUNT" && "$COUNT" -ge 28 ]] && ok "tool count = $COUNT (>= 28 static)" \
    || bad "tool count = ${COUNT:-<none>} (expected at least 28)"
EXTRA=$(printf '%s' "$TOOLS_JSON" | python3 -c '
import json,sys
d=json.load(sys.stdin)
static={"capabilities.get","apps.search","apps.launch","notifications.recent","notifications.since",
 "notifications.search","notifications.stats","media.now_playing","system.resources","intent.open",
 "memory.write","memory.search","events.tail","user.confirm","terminal.state","pane.split_vertical",
 "pane.split_horizontal","pane.focus_direction","pane.resize","pane.kill_focused","window.new",
 "window.close","window.next","window.previous","session.new","session.next","session.previous",
 "session.close_current"}
print(",".join(sorted({t["name"] for t in d.get("tools",[])} - static)))')
[[ -n "$EXTRA" ]] && info "MCP-discovered tools present: $EXTRA"

TERMINAL_TOOLS=(terminal.state pane.split_vertical pane.split_horizontal pane.focus_direction
                pane.resize pane.kill_focused window.new window.close window.next window.previous
                session.new session.next session.previous session.close_current)
missing=0
for t in "${TERMINAL_TOOLS[@]}"; do
    printf '%s' "$TOOLS_JSON" | grep -q "\"$t\"" || { bad "missing tool $t"; missing=1; }
done
[[ $missing -eq 0 ]] && ok "all 14 terminal tools advertised"

EXEC_TERMINAL=$(printf '%s' "$TOOLS_JSON" | python3 -c '
import json,sys
d=json.load(sys.stdin)
print(sum(1 for t in d.get("tools",[]) if t.get("executor")=="terminal"))')
[[ "$EXEC_TERMINAL" == "14" ]] && ok "14 tools report executor=terminal" \
    || bad "executor=terminal count = $EXEC_TERMINAL"

# UI metadata must not leak into the agent contract.
LEAK=$(printf '%s' "$TOOLS_JSON" | python3 -c '
import json,sys
d=json.load(sys.stdin)
allowed={"name","openAiName","description","schema","risk","requiresConfirmation","executor"}
bad=[t["name"] for t in d.get("tools",[]) if set(t) - allowed]
print(",".join(bad))')
[[ -z "$LEAK" ]] && ok "no UI metadata in /v1/agent/tools" || bad "UI metadata leaked: $LEAK"

# ---------------------------------------------------------------- foreground

head_ "Bring launcher to foreground"
adb shell input keyevent KEYCODE_HOME >/dev/null 2>&1
sleep 2
ATTACHED=$(api_exec terminal.state | field ok)
[[ "$ATTACHED" == "true" ]] && ok "terminal.state ok while foreground" \
    || bad "terminal.state failed while foreground: $(api_exec terminal.state)"

SPLITS_ENABLED=$(state_field splitPanesEnabled)
BASE_SESSIONS=$(state_field drawerSessions)
BASE_PANES=$(state_field visiblePanes)
info "splitPanesEnabled=$SPLITS_ENABLED drawerSessions=$BASE_SESSIONS visiblePanes=$BASE_PANES"
if [[ "$SPLITS_ENABLED" != "true" ]]; then
    info "Compatibility mode is on, so split/window tools should answer 409 splits_disabled."
fi

# ---------------------------------------------------------------- 409 path

head_ "Detached behavior (409 activity_not_running)"
adb shell am start -a android.settings.SETTINGS >/dev/null 2>&1
sleep 3
DETACHED=$(api_exec pane.split_vertical)
D_ERR=$(printf '%s' "$DETACHED" | errcode)
D_MSG=$(printf '%s' "$DETACHED" | field message)
if [[ "$D_ERR" == "activity_not_running" ]]; then
    ok "backgrounded launcher returns activity_not_running"
else
    bad "expected activity_not_running, got: ${D_ERR:-?} / ${D_MSG:-$DETACHED}"
fi
adb shell input keyevent KEYCODE_HOME >/dev/null 2>&1
sleep 2
[[ "$(api_exec terminal.state | field ok)" == "true" ]] && ok "reattached after returning home" \
    || bad "did not reattach after returning home"

# ---------------------------------------------------------------- validation

head_ "Argument validation"
BADDIR=$(api_exec pane.focus_direction '{"direction":"sideways"}')
[[ "$(printf '%s' "$BADDIR" | errcode)" == "bad_request" ]] \
    && ok "invalid direction rejected with bad_request" \
    || bad "invalid direction: $BADDIR"

UNKNOWN=$(api_exec pane.teleport)
printf '%s' "$UNKNOWN" | grep -q 'not_found\|not_implemented' \
    && ok "unknown tool rejected" || bad "unknown tool: $UNKNOWN"

# ---------------------------------------------------------------- test session

head_ "Create an isolated test session"
NEW=$(api_exec session.new "{\"name\":\"$TEST_SESSION\"}")
sleep 2
AFTER_SESSIONS=$(state_field drawerSessions)
SESSION_CREATED=0
if [[ "$(printf '%s' "$NEW" | field ok)" == "true" ]] && \
   [[ -n "$AFTER_SESSIONS" && -n "$BASE_SESSIONS" && "$AFTER_SESSIONS" -gt "$BASE_SESSIONS" ]]; then
    SESSION_CREATED=1
    ok "session.new created a session ($BASE_SESSIONS -> $AFTER_SESSIONS)"
else
    bad "session.new did not add a session ($BASE_SESSIONS -> ${AFTER_SESSIONS:-?}): $NEW"
fi

# ---------------------------------------------------------------- panes

head_ "Pane and window actions"
if [[ "$SPLITS_ENABLED" == "true" && $SESSION_CREATED -eq 1 ]]; then
    PRE=$(state_field visiblePanes)
    api_exec pane.split_vertical >/dev/null; sleep 2
    POST=$(state_field visiblePanes)
    [[ -n "$POST" && "$POST" -gt "$PRE" ]] && ok "pane.split_vertical raised visiblePanes ($PRE -> $POST)" \
        || bad "pane.split_vertical did not add a pane ($PRE -> ${POST:-?})"

    for dir in left right up down; do
        r=$(api_exec pane.focus_direction "{\"direction\":\"$dir\"}")
        [[ "$(printf '%s' "$r" | field ok)" == "true" ]] || bad "focus $dir: $r"
    done
    ok "pane.focus_direction accepted all four directions"

    r=$(api_exec pane.resize '{"direction":"left"}')
    [[ "$(printf '%s' "$r" | field ok)" == "true" ]] && ok "pane.resize ok" || bad "pane.resize: $r"

    api_exec window.new >/dev/null; sleep 2
    r=$(api_exec window.next); [[ "$(printf '%s' "$r" | field ok)" == "true" ]] \
        && ok "window.new + window.next ok" || bad "window.next: $r"
    r=$(api_exec window.previous); [[ "$(printf '%s' "$r" | field ok)" == "true" ]] \
        && ok "window.previous ok" || bad "window.previous: $r"
else
    info "skipped (splits disabled or test session missing)"
fi

# ---------------------------------------------------------------- destructive

head_ "Destructive tier"
if [[ $DESTRUCTIVE -eq 1 && $SESSION_CREATED -eq 1 ]]; then
    r=$(api_exec pane.kill_focused); sleep 2
    [[ "$(printf '%s' "$r" | field ok)" == "true" ]] && ok "pane.kill_focused ok" || bad "pane.kill_focused: $r"

    r=$(api_exec window.close); sleep 2
    [[ "$(printf '%s' "$r" | field ok)" == "true" ]] && ok "window.close ok" || bad "window.close: $r"

    # Cleanup: only ever runs when we know we created a session.
    r=$(api_exec session.close_current); sleep 2
    FINAL=$(state_field drawerSessions)
    [[ "$(printf '%s' "$r" | field ok)" == "true" ]] && ok "session.close_current ok" || bad "session.close_current: $r"
    info "drawerSessions now $FINAL (started at $BASE_SESSIONS)"
elif [[ $DESTRUCTIVE -eq 1 ]]; then
    info "skipped: no confirmed test session, refusing to close anything"
else
    info "skipped (pass --destructive to run). Test session '$TEST_SESSION' left open for manual cleanup."
fi

# ---------------------------------------------------------------- crashes

head_ "Crash check"
CRASH=$(adb logcat -d -t 400 2>/dev/null | grep -v "adbd" | grep -iE "FATAL EXCEPTION|AndroidRuntime.*com\.termux|TerminalActionDispatcher" | tail -8)
if [[ -z "$CRASH" ]]; then ok "no fatal exceptions or dispatcher errors in recent logcat"
else bad "log output follows"; printf '%s\n' "$CRASH"; fi

printf '\n\033[1mResult: %d passed, %d failed\033[0m\n' "$pass" "$fail"
[[ $fail -eq 0 ]] || exit 1
