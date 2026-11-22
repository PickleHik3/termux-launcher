#!/usr/bin/env bash
# gfxbench.sh — frame timing for one launcher interaction on a device, via dumpsys gfxinfo.
#
# Usage: [AD=ad] [PKG=com.termux] gfxbench.sh SERIAL_OR_TARGET "<device shell steps>"|STEPS_FILE [cycles]
#   e.g. gfxbench.sh pong scripts/perf-scenarios/gfxbench-drawer.steps 3
#        gfxbench.sh pong "cmd input swipe 540 2230 540 1800 150; sleep 1.5; input keyevent BACK; sleep 1.2"
# A steps file is one device shell command per line ('#' starts a comment), joined with '; '.
# AD is the device-control wrapper that takes -s TARGET and passes "adb ..." through; it defaults
# to 'ad' on PATH. Needs the device lease (ad -s pong lease acquire "...") and the launcher in front.
# The guard scenarios for the live-glass backdrop service (project-docs/active/live-glass-2026-11-03.md)
# are the gfxbench-*.steps files next to this script; run each before and after a move.
# Prints the summary (percentiles, janky, slow UI thread), then a per-frame phase split of the
# last cycle, for frames over 20 ms. Phases:
#   anim  = AnimationStart -> PerformTraversalsStart (animators, choreographer callbacks)
#   trav  = PerformTraversalsStart -> DrawStart (measure + layout + PRE-DRAW LISTENERS; a
#           software capture in a pre-draw listener lands here even with no layout requested)
#   draw  = DrawStart -> SyncQueued (recording display lists)
#   issue = IssueDrawCommandsStart -> SwapBuffers (RenderThread)
# 120 Hz budget is 8.3 ms per frame.
set -euo pipefail
AD="${AD:-ad}"
PKG="${PKG:-com.termux}"
T="$1"; STEPS="$2"; N="${3:-3}"
if [ -f "$STEPS" ]; then
  STEPS="$(grep -v '^[[:space:]]*#' "$STEPS" | sed '/^[[:space:]]*$/d' | paste -sd';' - | sed 's/;/; /g')"
fi
OUT="$(mktemp -d)"
# Warm-up cycle so first-launch costs (first capture, first bind) are reported separately.
$AD -s "$T" adb shell "$STEPS" >/dev/null 2>&1 || true
$AD -s "$T" adb shell "dumpsys gfxinfo $PKG reset >/dev/null; for i in \$(seq 1 $N); do $STEPS; done; dumpsys gfxinfo $PKG" > "$OUT/summary.txt"
grep -E "Total frames|Janky frames:|percentile|Number (Slow UI|Frame deadline missed:)" "$OUT/summary.txt"
$AD -s "$T" adb shell "dumpsys gfxinfo $PKG reset >/dev/null; $STEPS; dumpsys gfxinfo $PKG framestats" > "$OUT/frames.txt"
python3 - "$OUT/frames.txt" <<'EOF'
import sys
rows=[];hdr=None
for l in open(sys.argv[1]):
    l=l.strip()
    if l.startswith('Flags,'): hdr=l.rstrip(',').split(','); continue
    if hdr and l and l[0].isdigit() and ',' in l:
        v=l.rstrip(',').split(',')
        if len(v)>=len(hdr): rows.append(dict(zip(hdr,map(int,v[:len(hdr)]))))
ms=lambda a,b,r:(r[b]-r[a])/1e6
big=[r for r in rows if r.get('Flags',0)==0 and ms('IntendedVsync','FrameCompleted',r)>20]
print(f"one cycle: {len(rows)} frames, {len(big)} over 20 ms")
for r in big:
    print(f"  total={ms('IntendedVsync','FrameCompleted',r):6.1f} anim={ms('AnimationStart','PerformTraversalsStart',r):5.1f} "
          f"trav={ms('PerformTraversalsStart','DrawStart',r):5.1f} draw={ms('DrawStart','SyncQueued',r):5.1f} "
          f"issue={ms('IssueDrawCommandsStart','SwapBuffers',r):5.1f}")
EOF
echo "raw files: $OUT"
