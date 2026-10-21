#!/usr/bin/env bash
# Runs cleanup_eval.py on the phone under a memory watchdog. Start it with nohup so an ssh drop
# does not kill it:
#   nohup bash run_on_phone.sh results --variants A-light,B --runs 2 --warmup > run.log 2>&1 &
# Everything after the output directory is passed to `cleanup_eval.py run`.
# The watchdog polls MemAvailable every 0.25 s; below MIN_AVAILABLE_MB (default 1200) it runs
# `tai cancel; tai unload`, writes <out>/ABORT and the runner stops before the next case.
set -u
DIR=$(cd "$(dirname "$0")" && pwd)
OUT=${1:?usage: run_on_phone.sh <outdir> [run args...]}
shift
mkdir -p "$OUT"
ABORT="$OUT/ABORT"
DONE="$OUT/DONE"
rm -f "$ABORT" "$DONE"
MIN_KB=$(( ${MIN_AVAILABLE_MB:-1200} * 1024 ))

watchdog() {
    local low=999999999 n=0 kb
    while [ ! -f "$DONE" ]; do
        kb=$(awk '/^MemAvailable/ {print $2}' /proc/meminfo)
        [ "$kb" -lt "$low" ] && low=$kb
        if [ "$kb" -lt "$MIN_KB" ]; then
            echo "$(date +%T) MemAvailable ${kb} kB below ${MIN_KB} kB: cancel and unload" >> "$OUT/watchdog.log"
            touch "$ABORT"
            tai cancel >/dev/null 2>&1
            tai unload >/dev/null 2>&1
            return 1
        fi
        n=$((n + 1))
        if [ $((n % 40)) -eq 0 ]; then echo "$low" > "$OUT/min_available_kb"; fi
        sleep 0.25
    done
    echo "$low" > "$OUT/min_available_kb"
}

watchdog &
WATCHDOG=$!
python3 "$DIR/cleanup_eval.py" --cases "$DIR/cases.jsonl" run --out "$OUT" --abort-file "$ABORT" "$@"
RC=$?
touch "$DONE"
wait "$WATCHDOG"
echo "runner exit $RC; lowest MemAvailable $(cat "$OUT/min_available_kb" 2>/dev/null) kB"
[ -f "$ABORT" ] && echo "ABORTED by watchdog: $(cat "$OUT/watchdog.log")"
exit $RC
