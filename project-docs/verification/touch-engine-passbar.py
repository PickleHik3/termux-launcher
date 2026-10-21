#!/usr/bin/env python3
"""Read the keyboard touch engine's pass bar from its learned file.

The pass bar (project-docs/active/keyboard-touch-engine-2026-10-16.md): over a few days of normal
typing, take-backs into space drop by at least 30% from the warm-up's rate to the corrected
rate, and no other key's take-back rate rises.

Usage:
  touch-engine-passbar.py --pong        fetch from pong over `ssh termux` (release builds: no run-as)
  touch-engine-passbar.py FILE          read a copy of files/keyboard-tap-model.json

Read-only on the phone. The file is store version 3 (round 2). Per grid (one per layout and
orientation), every array is indexed like the layout's keys. The space bar is found as the
correctable key that learns no horizontal offset (its "x" sum stays 0) with the most taps; the keys
the misses landed on are read from the take-back counts by key hit, so no layout names are needed.
"""

import json
import subprocess
import sys

STATES = {0: "learning (warm-up)", 1: "active", 2: "paused (drift alarm)"}
PASS_DROP = 0.30
# Below these, a rate is noise; the verdict waits.
MIN_SPACE_TAPS_WARM = 100
MIN_SPACE_TAPS_ACTIVE = 300
MIN_KEY_TAPS = 50
# Another key's rate counts as risen only past this many take-backs per 100 taps.
RISE_SLACK = 0.5
MIN_RISE_TAKEBACKS = 3


def load(argv):
    if argv[1:] == ["--pong"]:
        raw = subprocess.run(["ssh", "termux", 'cat "$HOME/../keyboard-tap-model.json"'],
                             check=True, capture_output=True, text=True, timeout=60).stdout
        return json.loads(raw)
    if len(argv) == 2:
        with open(argv[1]) as f:
            return json.load(f)
    sys.exit(__doc__)


def rate(takebacks, taps):
    return 100.0 * takebacks / taps if taps else None


def fmt(r):
    return "-" if r is None else f"{r:.2f}/100"


def space_index(entry):
    n, x = entry["n"], entry["x"]
    wide = [i for i in range(len(n)) if n[i] > 0 and x[i] == 0]
    return max(wide, key=lambda i: n[i]) if wide else None


def report(data):
    if data.get("v") != 3:
        print(f"store version {data.get('v')}, expected 3: this reader is for round 2")
    e = data["engine"]
    print(f"engine: {STATES.get(e['state'], e['state'])}, switch {'on' if e['on'] else 'off'}, "
          f"taps learned {e['learned']}, warm-up {e['wt']}/1000 with {e['wb']} take-backs, "
          f"baseline {e['base']:.2f}/100, epoch {e['epoch']}")
    for key, g in data["entries"].items():
        print(f"\ngrid {key}")
        if g.get("count_epoch") != e["epoch"]:
            print("  counts belong to an older warm-up; they reset on the next tap here")
            continue
        sp = space_index(g)
        if sp is None:
            print("  no space taps yet")
            continue
        tw, ta = g["taps_warm"], g["taps_active"]
        kw, ka = g["takebacks_warm"], g["takebacks_active"]
        hw, ha = g["takebacks_hit_warm"], g["takebacks_hit_active"]
        rw, ra = rate(kw[sp], tw[sp]), rate(ka[sp], ta[sp])
        print(f"  space = key {sp}: warm-up {kw[sp]} take-backs / {tw[sp]} taps = {fmt(rw)}; "
              f"active {ka[sp]} / {ta[sp]} = {fmt(ra)}")
        hits = [(i, hw[i], ha[i]) for i in range(len(hw)) if hw[i] or ha[i]]
        print("  misses by key hit (all take-backs, warm-up/active): "
              + (", ".join(f"key {i}: {a}/{b}" for i, a, b in hits) or "none"))
        risen = []
        for i in range(len(tw)):
            if i == sp or tw[i] < MIN_KEY_TAPS or ta[i] < MIN_KEY_TAPS:
                continue
            a, b = rate(kw[i], tw[i]), rate(ka[i], ta[i])
            if b > a + RISE_SLACK and ka[i] >= MIN_RISE_TAKEBACKS:
                risen.append(f"key {i}: {fmt(a)} -> {fmt(b)}")
        print("  other keys risen: " + (", ".join(risen) or "none"))
        if tw[sp] < MIN_SPACE_TAPS_WARM or ta[sp] < MIN_SPACE_TAPS_ACTIVE:
            print(f"  verdict: not enough data (needs {MIN_SPACE_TAPS_WARM} warm-up and "
                  f"{MIN_SPACE_TAPS_ACTIVE} active space taps)")
        elif not rw:
            print("  verdict: no space take-backs in the warm-up; the drop cannot be measured")
        else:
            drop = 1 - ra / rw
            ok = drop >= PASS_DROP and not risen
            print(f"  verdict: {'PASS' if ok else 'FAIL'} (space take-back rate down {drop:.0%}; "
                  f"needs a drop of {PASS_DROP:.0%} and no other key risen)")


if __name__ == "__main__":
    report(load(sys.argv))
