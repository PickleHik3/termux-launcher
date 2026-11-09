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
#   ghostty-core-bench.sh DEV parse  [RUNS] [COLS ROWS]     # parse the transcript in-process, nothing drawn (default 3, 52 45)
#   ghostty-core-bench.sh DEV frames java|ghostty WORKLOAD  # per-frame timing (gfxinfo framestats) and per-thread CPU
#       WORKLOAD: flood (cat the transcript), tui (full-screen redraw at ~60 fps for 10 s),
#       type (keystrokes every 250 ms while lines stream), kitty (an animated image for 10 s; Java core only),
#       btop (btop or htop for 12 s, if installed). Summaries come from framestats-summary.py.
#
# `parse` runs com.termux.terminal.TerminalParseBench in its own app_process (as the app user, the
# installed APK on the class path), not in the launcher: per run it times the Java core, the Ghostty
# core writing only, and the Ghostty core with the mirror, one JSON line each.
#
# Results go to $OUT (default ~/reports/ghostty-core-<date>), one line per measurement in results.tsv.
set -euo pipefail

DEV=${1:?device}; CMD=${2:?command}; CORE=${3:-ghostty}; RUNS=${4:-3}
if [ "$CMD" = parse ]; then CORE=parse; RUNS=${3:-3}; COLS=${4:-52}; ROWS=${5:-45}; fi
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
    cat > "$tmp/tui.sh" <<'TUI'
# tui.sh OUT: redraw the whole screen with coloured cells about 60 times a second for 10 s.
read -r rows cols < <(stty size)
frames=()
for f in 0 1 2 3 4 5 6 7; do
  buf=$'\033[H'
  for ((r = 0; r < rows - 1; r++)); do
    line=''
    for ((c = 0; c < cols; c += 4)); do line+=$'\033[48;5;'$(( (r + c / 4 + f) % 216 + 16 ))'m'"$(printf '%02d%02d' $((r % 100)) $(((c + f) % 100)))"; done
    buf+="${line:0:4096}"$'\033[0m\n'
  done
  frames+=("$buf")
done
printf '\033[?1049h\033[?25l'
end=$(( ${EPOCHSECONDS} + 10 )); n=0
while (( EPOCHSECONDS < end )); do printf '%s' "${frames[n % 8]}"; n=$((n + 1)); sleep 0.016; done
printf '\033[?25h\033[?1049l'; echo "frames=$n" > "$1"; echo TUI-DONE
TUI
    cat > "$tmp/stream.sh" <<'STR'
# stream.sh OUT: print a numbered line every 10 ms for 12 s (keystrokes arrive meanwhile).
end=$(( ${EPOCHSECONDS} + 12 )); n=0
while (( EPOCHSECONDS < end )); do n=$((n + 1)); printf 'stream %06d the quick brown fox jumps over the lazy dog\n' "$n"; sleep 0.01; done
echo "lines=$n" > "$1"; echo STREAM-DONE
STR
    cat > "$tmp/kanim.sh" <<'KAN'
# kanim.sh OUT: a 256x256 kitty graphics animation, 24 grey frames, 33 ms apart, looped for 10 s.
send() {  # send ACTION_KEYS FILE: transmit FILE's bytes base64-encoded in 4096-byte chunks
  local keys=$1 j more lines; mapfile -t lines < <(base64 -w 4096 < "$2"); local n=${#lines[@]}
  for ((j = 0; j < n; j++)); do
    more=$(( j < n - 1 ? 1 : 0 ))
    if (( j == 0 )); then printf '\033_G%s,m=%d;%s\033\\' "$keys" "$more" "${lines[j]}"
    else printf '\033_Gm=%d;%s\033\\' "$more" "${lines[j]}"; fi
  done
}
tmpf=$TMPDIR/kanim.rgb
for ((k = 0; k < 24; k++)); do
  v=$(printf '%03o' $(( 40 + k * 8 )))
  head -c 196608 /dev/zero | tr '\0' "\\$v" > "$tmpf"
  if (( k == 0 )); then send "a=t,i=909,q=2,f=24,s=256,v=256" "$tmpf"
  else send "a=f,i=909,q=2,f=24,s=256,v=256,z=33" "$tmpf"; fi
done
printf '\033_Ga=p,i=909,q=2,c=24,r=12\033\\'
printf '\033_Ga=a,i=909,q=2,r=1,z=33\033\\'  # the first frame's gap, or the loop stalls on it
printf '\033_Ga=a,i=909,q=2,s=3,v=1\033\\'
echo started > "$1.start"
end=$(( ${EPOCHSECONDS} + 10 )); while (( EPOCHSECONDS < end )); do sleep 0.5; done
printf '\033_Ga=d,d=I,i=909,q=2\033\\'; rm -f "$tmpf"; echo done > "$1"; echo KITTY-DONE
KAN
    cat > "$tmp/tuiapp.sh" <<'APP'
# tuiapp.sh OUT: run btop, else htop, for 12 s.
if command -v btop >/dev/null; then timeout 12 btop; echo app=btop > "$1"
elif command -v htop >/dev/null; then timeout 12 htop; echo app=htop > "$1"
else echo app=none > "$1"; fi
echo APP-DONE
APP
    cp "$(dirname "$0")/test-terminal-protocols.sh" "$tmp/ttp.sh"
    run "mkdir -p $B"
    for f in gen.sh run.sh replies.sh ttp.sh tui.sh stream.sh kanim.sh tuiapp.sh; do
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
parse)
    gate; PID0=$(ad adb shell pidof "$PKG" | tr -d '\r'); CRASH0=$(ad adb shell logcat -d -b crash 2>/dev/null | grep -c "$PKG" || true)
    run "test -s $B/bench.txt" >/dev/null 2>&1 || { echo "no $B/bench.txt: run setup first" >&2; exit 2; }
    raw="$OUT/parse-$(date +%H%M%S).jsonl"
    # The bench copies the library out of the APK into \$TMPDIR; app_process cannot load it from there.
    run "CLASSPATH=\$(/system/bin/pm path $PKG | head -1 | cut -d: -f2) /system/bin/app_process /system/bin com.termux.terminal.TerminalParseBench $B/bench.txt --runs $RUNS --cols $COLS --rows $ROWS --lib-dir \$TMPDIR" \
        | tr -d '\r' | grep '^{' > "$raw" || true
    [ -s "$raw" ] || { echo "FAIL: the parse bench printed nothing" | tee -a "$OUT/results.tsv"; exit 4; }
    while IFS= read -r line; do
        label=$(printf '%s' "$line" | grep -o '"run":[0-9]*,"mode":"[a-z-]*"' | sed 's/"run":\([0-9]*\),"mode":"\(.*\)"/parse-\2-run\1/' || true)
        record "${label:-parse-header}" "$line"
    done < "$raw"
    crashcheck
    ;;
frames)
    WORK=${4:?workload: flood, tui, type, kitty or btop}
    [ "$WORK" = kitty ] && [ "$CORE" != java ] && { echo "kitty graphics are off in the Ghostty core; use java" >&2; exit 2; }
    gate; PID0=$(ad adb shell pidof "$PKG" | tr -d '\r'); CRASH0=$(ad adb shell logcat -d -b crash 2>/dev/null | grep -c "$PKG" || true)
    open_pane
    timeout 2 tail -f /dev/null || true
    tag="$OUT/frames-$WORK-$CORE-$(date +%H%M%S)"
    threads() { run "for t in /proc/$PID0/task/*; do c=\$(cat \$t/comm); sed 's/.*) //' \$t/stat | awk -v c=\"\$c\" '{print c \"\t\" \$12+\$13}'; done"; }
    threads > "$tag.threads0"
    run "rm -f $B/w.out"
    ad adb shell dumpsys gfxinfo "$PKG" reset >/dev/null
    # Collect framestats on the device every 0.5 s for 20 s (the buffer keeps only the last ~120 frames).
    collect() { ad adb shell "rm -f /data/local/tmp/fs.txt; nohup sh -c 'i=0; while [ \$i -lt 40 ]; do dumpsys gfxinfo $PKG framestats >> /data/local/tmp/fs.txt; dumpsys gfxinfo $PKG reset >/dev/null; sleep 0.5; i=\$((i+1)); done' >/dev/null 2>&1 &"; }
    run "rm -f $B/w.out.start"
    [ "$WORK" = kitty ] || collect
    case "$WORK" in
        flood) run "launcherctl pane write $PANE --enter 'bash $B/run.sh $B/bench.txt $B/w.out'" ;;
        tui)   run "launcherctl pane write $PANE --enter 'bash $B/tui.sh $B/w.out'" ;;
        kitty) run "launcherctl pane write $PANE --enter 'clear; bash $B/kanim.sh $B/w.out'"
               wait_file "$B/w.out.start" 120 || { close_pane; exit 1; }; ad adb shell dumpsys gfxinfo "$PKG" reset >/dev/null; collect ;;
        btop)  run "launcherctl pane write $PANE --enter 'bash $B/tuiapp.sh $B/w.out'" ;;
        type)  run "launcherctl pane write $PANE --enter 'bash $B/stream.sh $B/w.out'"
               timeout 1 tail -f /dev/null || true
               gate
               # Keystrokes go to the focused pane; the bench pane was opened focused.
               ad adb shell "for i in \$(seq 1 40); do input keyevent KEYCODE_A; sleep 0.25; done" ;;
        *) echo "unknown workload $WORK" >&2; close_pane; exit 2 ;;
    esac
    wait_file "$B/w.out" 120 || { close_pane; exit 1; }
    timeout 3 tail -f /dev/null || true
    threads > "$tag.threads1"
    record "frames-$WORK" "$(run "cat $B/w.out" | tr -d '\r' | head -1)"
    # Let the collector finish its 20 s, then fetch.
    for i in $(seq 1 30); do ad adb shell "pgrep -f 'dumpsys gfxinfo' >/dev/null" || break; timeout 1 tail -f /dev/null || true; done
    ad adb shell cat /data/local/tmp/fs.txt | tr -d '\r' > "$tag.framestats"
    ad adb shell rm -f /data/local/tmp/fs.txt
    crashcheck; close_pane
    hz=$(ad adb shell dumpsys display | tr -d '\r' | grep -oE 'renderFrameRate [0-9.]+' | head -1 | awk '{print $2}')
    summary=$(REFRESH_HZ="${hz:-60}" python3 -I "$(dirname "$0")/framestats-summary.py" "$tag.framestats" "$tag.threads0" "$tag.threads1")
    printf '%s\n' "$summary" > "$tag.summary"
    record "frames-$WORK-summary" "$(printf '%s' "$summary" | head -1)"
    printf '%s\n' "$summary"
    ;;
*) echo "unknown command $CMD" >&2; exit 2 ;;
esac
