#!/usr/bin/env bash
# One recorded Home ⇄ Terminal round on pong: both crossings recorded at every real frame, the
# motion log dumped per crossing, a brightness scan of every frame, and strips of the biggest jumps.
#
#   scripts/pong-round.sh OUTDIR [--start home1|home2] [--purpose TEXT]
#
# --start says where pong rests when the round begins. home1 (default) swipes once to Home 2,
# the page left of the Terminal, and swipes back at the end; home2 starts the H->T recording
# straight away. The round refuses to run unless the launcher is in front: when another app is in
# front, the developer is using the phone. Ask the developer before running it (AGENTS.md).
#
# Needs: the android-control `ad` tool (AD=path, default ~/.claude/skills/android-control/ad),
# an ssh alias that reaches pong's Termux (PONG_SSH, default termux) with `launcherctl` on PATH,
# ffmpeg/ffprobe, python3 with Pillow.
#
# RESCAN=1 skips the device and scans an OUTDIR a round already filled (h2t/clip.mp4, h2t.log, ...).
#
# Output in OUTDIR: h2t/ and t2h/ (clip.mp4, sheet.png, all/ frames at 216 px), h2t.log and t2h.log
# (motion log per crossing), h2t-scan.txt and t2h-scan.txt (pts and band lumas per frame, JUMP
# where a band moves more than 4 between neighbours), strip-*.png (frames N-1, N, N+1 around the
# biggest jumps outside the swipe itself), summary.txt (build, landings, counters, top jumps).
set -u

OUT=${1:?usage: pong-round.sh OUTDIR [--start home1|home2] [--purpose TEXT]}
shift
START=home1
PURPOSE="pong round: Home <-> Terminal recording"
while [ $# -gt 0 ]; do
    case "$1" in
        --start) START=$2; shift 2 ;;
        --purpose) PURPOSE=$2; shift 2 ;;
        *) echo "unknown argument: $1" >&2; exit 2 ;;
    esac
done
case "$START" in home1|home2) ;; *) echo "--start is home1 or home2" >&2; exit 2 ;; esac

AD=${AD:-$HOME/.claude/skills/android-control/ad}
TARGET=${PONG_TARGET:-pong}
SSHA=${PONG_SSH:-termux}
# Pong's crossings, in device pixels (1080x2412): a 450 ms drag across most of the width.
H2T_SWIPE=(950 1200 150 1200 450)
T2H_SWIPE=(150 1000 950 1000 450)
TO_HOME2=(950 1000 150 1000 400)
BACK_TO_HOME1=(150 1000 950 1000 400)

mkdir -p "$OUT"
OUT=$(cd "$OUT" && pwd)
VERSION=${VERSION:-unknown}

if [ "${RESCAN:-0}" != 1 ]; then

SERIAL=$("$AD" -s "$TARGET" serial 2>/dev/null)
lease() { "$AD" -s "$1" lease "${@:2}"; }
release() {
    lease "$TARGET" release >/dev/null 2>&1
    [ -n "$SERIAL" ] && [ "$SERIAL" != "$TARGET" ] && lease "$SERIAL" release >/dev/null 2>&1
}
# A lease the caller already holds (a round inside a longer device session) stays held afterwards.
HELD=0
lease "$TARGET" status 2>/dev/null | grep -q "^$TARGET is leased by .*(you)" && HELD=1
if [ "$HELD" = 1 ]; then
    release() { :; }
else
    lease "$TARGET" acquire --for 20 "$PURPOSE" || exit 1
    if [ -n "$SERIAL" ] && [ "$SERIAL" != "$TARGET" ]; then
        lease "$SERIAL" acquire --for 20 "$PURPOSE" || { release; exit 1; }
    fi
fi
trap release EXIT

if ! "$AD" -s "$TARGET" top | grep -q 'com\.termux/com\.termux\.app\.TermuxActivity'; then
    echo "the launcher is not in front: the developer may be using the phone; not recording" >&2
    "$AD" -s "$TARGET" top >&2
    exit 1
fi

motionlog() { ssh "$SSHA" "launcherctl motion-log $1"; }
VERSION=$("$AD" -s "$TARGET" shell dumpsys package com.termux | grep -m1 versionName | tr -d ' ')

if [ "$START" = home1 ]; then
    "$AD" -s "$TARGET" swipe "${TO_HOME2[@]}" >/dev/null
    sleep 2
fi

# One crossing: clear the log, record the swipe, give the landing's settle 2 s, dump the log.
cross() {
    local name=$1; shift
    motionlog clear >/dev/null
    rm -rf "$OUT/$name"
    "$AD" -s "$TARGET" frames 3 "$OUT/$name" -- swipe "$@" | tail -1
    sleep 2
    motionlog dump > "$OUT/$name.log" 2>&1
    rm -f "$OUT/$name"/f[0-9]*.png
}
cross h2t "${H2T_SWIPE[@]}"
cross t2h "${T2H_SWIPE[@]}"
motionlog clear >/dev/null

if [ "$START" = home1 ]; then
    "$AD" -s "$TARGET" swipe "${BACK_TO_HOME1[@]}" >/dev/null
fi
release
trap - EXIT
fi

# Every real frame at 216 px, with its presentation time.
for name in h2t t2h; do
    mkdir -p "$OUT/$name/all"
    rm -f "$OUT/$name/all"/*.png
    ffmpeg -loglevel error -i "$OUT/$name/clip.mp4" -fps_mode passthrough -vf scale=216:-1 \
        "$OUT/$name/all/%03d.png" 2>/dev/null
    ffprobe -v error -select_streams v -show_entries frame=pts_time -of csv=p=0 \
        "$OUT/$name/clip.mp4" > "$OUT/$name/pts.txt"
done

python3 -I - "$OUT" "$VERSION" <<'PY'
import glob, os, re, sys
from PIL import Image

out, version = sys.argv[1], sys.argv[2]
BANDS = ((0, .1), (.1, .7), (.7, .85), (.85, 1))
NAMES = ('0-10%', '10-70%', '70-85%', '85-100%')

def lumas(path):
    im = Image.open(path).convert('L')
    w, h = im.size
    px = im.load()
    res = []
    for a, b in BANDS:
        ys = range(int(h * a), int(h * b))
        res.append(sum(px[x, y] for y in ys for x in range(w)) / (len(ys) * w))
    return res

summary = ['build ' + version]
for name in ('h2t', 't2h'):
    frames = sorted(glob.glob(f'{out}/{name}/all/*.png'))
    pts = [l.strip() for l in open(f'{out}/{name}/pts.txt') if l.strip()]
    rows, jumps, prev = [], [], None
    for i, f in enumerate(frames):
        b = lumas(f)
        t = float(pts[i]) if i < len(pts) else float('nan')
        line = f'{i + 1:03d} t={t:.3f} ' + ' '.join(f'{v:6.1f}' for v in b)
        if prev:
            d = [b[k] - prev[k] for k in range(4)]
            if max(abs(v) for v in d) > 4:
                line += '  JUMP ' + ' '.join(f'{v:+.1f}' for v in d)
                jumps.append((i + 1, t, d))
        rows.append(line)
        prev = b
    open(f'{out}/{name}-scan.txt', 'w').write('\n'.join(rows) + '\n')

    # The swipe's own motion is a run of consecutive jumps; a jump that stands alone (no jump
    # in the frame before and after) or one far bigger than its neighbours is what to look at.
    nums = {j[0] for j in jumps}
    lone = [j for j in jumps if (j[0] - 1 not in nums) or (j[0] + 1 not in nums)]
    lone.sort(key=lambda j: -max(abs(v) for v in j[2]))
    for n, t, d in lone[:6]:
        trio = [f'{out}/{name}/all/{k:03d}.png' for k in (n - 1, n, n + 1)]
        trio = [p for p in trio if os.path.exists(p)]
        ims = [Image.open(p) for p in trio]
        strip = Image.new('RGB', (sum(i.width for i in ims), max(i.height for i in ims)))
        x = 0
        for im in ims:
            strip.paste(im, (x, 0))
            x += im.width
        strip.save(f'{out}/strip-{name}-{n:03d}.png')

    log = open(f'{out}/{name}.log', errors='replace').read()
    lands = re.findall(r'land landed=\S+', log)
    counters = {}
    for key in re.findall(r'guard [a-z-]+|INVARIANT\S*|keyboard-preroll', log):
        counters[key] = counters.get(key, 0) + 1
    slow = re.search(r'crossing-redraw-slow count=(\d+)', log)
    summary.append(f'\n{name}: {len(frames)} frames, landings {lands or "NONE (the crossing did not land)"}')
    summary.append('  counters: ' + (', '.join(f'{k} x{v}' for k, v in sorted(counters.items())) or 'none')
                   + (f'; crossing-redraw-slow {slow.group(1)}' if slow else ''))
    summary.append(f'  jumps >4: {len(jumps)}; standing alone (strips written for the top 6):')
    for n, t, d in lone[:6]:
        summary.append(f'    frame {n:03d} t={t:.3f} ' +
                       ' '.join(f'{nm} {v:+.1f}' for nm, v in zip(NAMES, d) if abs(v) > 4))
open(f'{out}/summary.txt', 'w').write('\n'.join(summary) + '\n')
print('\n'.join(summary))
PY
