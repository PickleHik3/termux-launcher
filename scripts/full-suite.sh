#!/usr/bin/env bash
# The full unit suite of the checkout it runs in, one at a time on this machine.
#
#   scripts/full-suite.sh [LOGFILE]
#
# Several worktrees build at once here, and two full suites side by side each take about twice as
# long. A lock in ~/.cache makes a second suite wait for the first; builds and scoped test runs
# don't take it and keep running beside a suite. Prints one line per module from the result XML
# (tests, skipped, failures + errors, and the first failing classes) and exits with Gradle's code.
set -u
ROOT=$(cd "$(dirname "$0")/.." && pwd)
LOG=${1:-$ROOT/build/full-suite.log}
mkdir -p "$(dirname "$LOG")" "$HOME/.cache"
LOCK=$HOME/.cache/termux-launcher-full-suite.lock

exec 9>"$LOCK"
if ! flock -n 9; then
    echo "another full suite is running ($(cat "$LOCK.owner" 2>/dev/null)); waiting" >&2
    flock 9
fi
echo "$ROOT since $(date +%H:%M)" > "$LOCK.owner"

cd "$ROOT"
start=$(date +%s)
./gradlew testDebugUnitTest --continue > "$LOG" 2>&1
code=$?
echo "gradle exit=$code in $(( $(date +%s) - start )) s; log $LOG"
rm -f "$LOCK.owner"
flock -u 9

python3 -I - "$ROOT" <<'PY'
import glob, sys, xml.etree.ElementTree as E
root = sys.argv[1]
for m in ('app', 'inapp-keyboard', 'terminal-emulator', 'terminal-view', 'termux-shared', 'x11-server'):
    t = s = f = 0
    bad = []
    for p in glob.glob(f'{root}/{m}/build/test-results/testDebugUnitTest/*.xml'):
        r = E.parse(p).getroot()
        t += int(r.get('tests')); s += int(r.get('skipped'))
        n = int(r.get('failures')) + int(r.get('errors')); f += n
        if n: bad.append(r.get('name'))
    print(f'{m} tests {t} skipped {s} failures {f} {bad[:5]}')
PY
exit $code
