#!/usr/bin/env bash
# Checks and benchmark for the experimental libghostty-vt core (branch exp/ghostty-core),
# run from the host against a device through `ad`. Each run opens its own pane, picks the core
# with debug.tl.terminal_core before opening it, and closes the pane after. Every device batch
# starts by checking the launcher is in front and stops if it is not.
#
# Usage (DEV is an `ad` target, e.g. pong or emu):
#   ghostty-core-bench.sh DEV setup                 # write the helper scripts and the 200k-line transcript
#   ghostty-core-bench.sh DEV smoke  java|ghostty   # replies, kitty keyboard query, alt screen, keyboard resize mid-flood, exit
#   ghostty-core-bench.sh DEV look   java|ghostty   # test-terminal-protocols.sh in a pane, then a screenshot
#   ghostty-core-bench.sh DEV bench  java|ghostty [RUNS]   # time `cat` of the transcript, CPU, PSS, frames
#
# Results go to $OUT (default ~/reports/ghostty-core-<date>), one line per measurement in results.tsv.
set -euo pipefail

DEV=${1:?device}; CMD=${2:?command}; CORE=${3:-ghostty}; RUNS=${4:-3}
AD=~/.claude/skills/android-control/ad
PKG=com.termux
OUT=${OUT:-$HOME/reports/ghostty-core-$(date +%F)}
mkdir -p "$OUT"
export AD_RUNAS_PRELUDE='P=/data/data/com.termux/files; export HOME=$P/home PREFIX=$P/usr TMPDIR=$P/usr/tmp PATH=$P/usr/bin:/system/bin LD_PRELOAD=$P/usr/lib/libtermux-exec-ld-preload.so TERMUX_EXEC__SYSTEM_LINKER_EXEC__MODE=disable; cd $HOME'
B=.ghostty-bench   # under the app's $HOME

ad() { "$AD" -s "$DEV" "$@"; }
run() { ad runas "$PKG" "$1"; }
gate() {
    ad top | grep -q "$PKG/.app.TermuxActivity" || { echo "stop: the launcher is not in front on $DEV" >&2; exit 3; }
}
crashcheck() {
    local pid; pid=$(ad adb shell pidof "$PKG" | tr -d '\r')
    [ "$pid" = "$PID0" ] || { echo "FAIL: launcher pid changed ($PID0 -> ${pid:-none})" | tee -a "$OUT/results.tsv"; exit 4; }
    local n; n=$(ad adb shell logcat -d -b crash 2>/dev/null | grep -c "$PKG" || true)
    [ "$n" = "$CRASH0" ] || { echo "FAIL: new crash-buffer lines for $PKG" | tee -a "$OUT/results.tsv"; exit 4; }
}
record() { printf '%s\t%s\t%s\t%s\n' "$(date +%T)" "$CORE" "$1" "$2" | tee -a "$OUT/results.tsv"; }
wait_file() {  # wait_file PATH SECONDS
    local i; for ((i = 0; i < $2 * 2; i++)); do run "test -s $1" >/dev/null 2>&1 && return 0; timeout 0.5 tail -f /dev/null || true; done
    echo "timeout waiting for $1" >&2; return 1
}
open_pane() {
    case "$CORE" in java) ad adb shell setprop debug.tl.terminal_core java ;; ghostty) ad adb shell setprop debug.tl.terminal_core ghostty ;;
        *) echo "core must be java or ghostty" >&2; exit 2 ;; esac
    local since; since=$(ad adb shell date +%m-%d_%H:%M:%S.000 | tr -d '\r' | tr _ ' ')
    PANE=$(run "launcherctl pane open --print-id --title bench-$CORE" | tail -1 | tr -d '\r')
    timeout 2 tail -f /dev/null || true
    local seen; seen=$(ad adb shell logcat -d -T "'$since'" -s Termux.TerminalSession | grep -o 'Terminal core: [a-z]*.*' | tail -1 || true)
    record pane-core "${seen:-Terminal core: java (no log line)}"
    [ "$CORE" = java ] || [[ "$seen" == "Terminal core: ghostty" ]] || { echo "FAIL: ghostty core did not start" >&2; close_pane; exit 5; }
}
close_pane() { run "launcherctl pane close $PANE" >/dev/null 2>&1 || true; ad adb shell setprop debug.tl.terminal_core "''" || true; }
pss() { ad adb shell dumpsys meminfo "$PKG" | tr -d '\r' | awk '/TOTAL PSS:/{print "pss_kb="$3} /Native Heap:/{n=$3} /Java Heap:/{j=$3} END{print "native_kb="n, "java_kb="j}' | xargs; }
cpu_ticks() { run "cat /proc/$PID0/stat" | awk '{print $14+$15}'; }

case "$CMD" in
setup)
    gate
    tmp=$(mktemp -d)
    cat > "$tmp/gen.sh" <<'GEN'
awk 'BEGIN{for(i=1;i<=200000;i++){m=i%4
  if(m==0) printf "\033[1;3%dm%06d colour line\033[0m plain tail text here\n", i%8, i
  else if(m==1) printf "%06d 日本語 émoji 😀 mixed width ─┼─ box drawing\n", i
  else printf "%06d the quick brown fox jumps over the lazy dog 0123456789 abcdefghij\n", i}}'
GEN
    cat > "$tmp/run.sh" <<'RUN'
# run.sh FILE OUT: cat FILE, then write start and end epoch seconds to OUT.
s=$EPOCHREALTIME; cat "$1"; e=$EPOCHREALTIME
printf '\n%s %s\n' "$s" "$e" > "$2"; echo BENCH-DONE
RUN
    cat > "$tmp/replies.sh" <<'REP'
# replies.sh OUT: device attributes and the kitty keyboard query; answers saved as characters.
stty -echo -icanon min 0 time 5
printf '\033[c'; IFS= read -r -d c -t 2 da
printf '\033[?u'; IFS= read -r -d u -t 2 ku
printf '\033[?1049h\033[2Jalt\033[?1049l'
stty sane
printf 'da=%s ku=%s\n' "$(printf %s "$da" | od -An -c | tr -s ' ')" "$(printf %s "$ku" | od -An -c | tr -s ' ')" > "$1"
REP
    cp "$(dirname "$0")/test-terminal-protocols.sh" "$tmp/ttp.sh"
    run "mkdir -p $B"
    for f in gen.sh run.sh replies.sh ttp.sh; do
        run "echo $(base64 -w0 "$tmp/$f") | base64 -d > $B/$f"
    done
    rm -rf "$tmp"
    run "cd $B && bash gen.sh > bench.txt && wc -c bench.txt && ls"
    ;;
smoke)
    gate; PID0=$(ad adb shell pidof "$PKG" | tr -d '\r'); CRASH0=$(ad adb shell logcat -d -b crash 2>/dev/null | grep -c "$PKG" || true); open_pane
    run "rm -f $B/replies.out $B/flood.out"
    run "launcherctl pane write $PANE --enter 'bash $B/replies.sh $B/replies.out'"
    wait_file "$B/replies.out" 10 && record replies "$(run "cat $B/replies.out" | tr -d '\r')"
    # Resize twice (keyboard up, then down) while 200k lines are still arriving.
    run "launcherctl pane write $PANE --enter 'bash $B/run.sh $B/bench.txt $B/flood.out'"
    timeout 1 tail -f /dev/null || true; run "launcherctl keyboard show" >/dev/null || true
    timeout 1 tail -f /dev/null || true; run "launcherctl keyboard hide" >/dev/null || true
    wait_file "$B/flood.out" 300 && record resize-mid-flood "survived"
    record tail "$(run "launcherctl pane read $PANE --lines 4" | tr -d '\r' | tail -c 300)"
    run "launcherctl pane write $PANE --enter 'exit'"; timeout 2 tail -f /dev/null || true
    crashcheck; record smoke pass; close_pane
    ;;
look)
    gate; PID0=$(ad adb shell pidof "$PKG" | tr -d '\r'); CRASH0=$(ad adb shell logcat -d -b crash 2>/dev/null | grep -c "$PKG" || true); open_pane
    run "launcherctl pane write $PANE --enter 'clear; bash $B/ttp.sh'"
    timeout 4 tail -f /dev/null || true
    # The pane lives on the Workspace page: have it in front before this step.
    gate; ad shot "$OUT/look-$CORE.png"; record look "$OUT/look-$CORE.png"
    crashcheck; close_pane
    ;;
bench)
    gate; PID0=$(ad adb shell pidof "$PKG" | tr -d '\r'); CRASH0=$(ad adb shell logcat -d -b crash 2>/dev/null | grep -c "$PKG" || true)
    record before "$(pss)"
    open_pane
    for ((r = 1; r <= RUNS; r++)); do
        run "rm -f $B/t.out"; ad adb shell dumpsys gfxinfo "$PKG" reset >/dev/null
        c0=$(cpu_ticks)
        run "launcherctl pane write $PANE --enter 'bash $B/run.sh $B/bench.txt $B/t.out'"
        wait_file "$B/t.out" 600
        c1=$(cpu_ticks)
        secs=$(run "cat $B/t.out" | awk 'NF==2{printf "%.3f", $2-$1}')
        jank=$(ad adb shell dumpsys gfxinfo "$PKG" | tr -d '\r' | awk -F': ' '/Total frames rendered/{t=$2} /Janky frames:/{j=$2} END{print "frames="t, "janky="j}')
        record "run$r" "cat_s=$secs cpu_s=$(awk -v t=$((c1 - c0)) 'BEGIN{printf "%.2f", t/100}') $jank"
    done
    timeout 5 tail -f /dev/null || true
    record after-flood "$(pss)"
    crashcheck; close_pane
    timeout 5 tail -f /dev/null || true
    record after-close "$(pss)"
    ;;
*) echo "unknown command $CMD" >&2; exit 2 ;;
esac
