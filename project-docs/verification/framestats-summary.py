#!/usr/bin/env python3
"""Summarise `dumpsys gfxinfo <pkg> framestats` dumps and per-thread CPU for one workload.

Usage: REFRESH_HZ=<hz> framestats-summary.py FRAMESTATS [THREADS_BEFORE THREADS_AFTER]

FRAMESTATS is one or more framestats dumps appended together (frames are de-duplicated by their
IntendedVsync). THREADS_* are "comm<TAB>ticks" lines, one per thread, taken before and after.

Per frame (nanosecond timestamps from HWUI's FrameInfo):
  start delay  HandleInputStart - IntendedVsync   the main thread was busy when the frame was due
  ui           SyncQueued - HandleInputStart       input, animation, measure/layout, recording
  render       FrameCompleted - SyncStart          RenderThread: sync, issue draw commands, swap
  gpu          GpuCompleted - IssueDrawCommandsStart (when the driver reports it)
  total        FrameCompleted - IntendedVsync
The first line is a one-line summary; the rest is a table.
"""
import sys

FIELDS = ("IntendedVsync", "HandleInputStart", "SyncQueued", "SyncStart",
          "IssueDrawCommandsStart", "FrameCompleted", "GpuCompleted")


def frames(path):
    seen, out, header = set(), [], None
    with open(path, encoding="utf-8", errors="replace") as f:
        for line in f:
            line = line.strip()
            if line.startswith("Flags,"):
                header = line.rstrip(",").split(",")
                continue
            if header is None or not line or not line[0].isdigit():
                if line.startswith("---PROFILEDATA---") or not line:
                    continue
                header = None if line.startswith("**") else header
                continue
            cols = line.rstrip(",").split(",")
            if len(cols) < len(header):
                continue
            row = dict(zip(header, cols))
            try:
                if int(row["Flags"]) != 0:
                    continue
                v = {k: int(row[k]) for k in FIELDS if k in row}
            except (KeyError, ValueError):
                continue
            if v["IntendedVsync"] in seen or v.get("FrameCompleted", 0) <= 0:
                continue
            seen.add(v["IntendedVsync"])
            out.append(v)
    return sorted(out, key=lambda r: r["IntendedVsync"])


def pct(xs, p):
    if not xs:
        return float("nan")
    xs = sorted(xs)
    return xs[min(len(xs) - 1, int(round(p / 100 * (len(xs) - 1))))]


def threads(before, after):
    def load(path):
        d = {}
        with open(path, encoding="utf-8", errors="replace") as f:
            for line in f:
                parts = line.rstrip("\n").split("\t")
                if len(parts) == 2 and parts[1].strip().isdigit():
                    d[parts[0]] = d.get(parts[0], 0) + int(parts[1])
        return d
    a, b = load(before), load(after)
    delta = {k: b.get(k, 0) - a.get(k, 0) for k in b}
    return sorted(((v / 100.0, k) for k, v in delta.items() if v > 0), reverse=True)


def main():
    fr = frames(sys.argv[1])
    if len(fr) < 1:
        print("frames=0 (no framestats rows: was the pane on screen?)")
        return
    ms = lambda a, b, r: (r[b] - r[a]) / 1e6
    delay = [ms("IntendedVsync", "HandleInputStart", r) for r in fr]
    ui = [ms("HandleInputStart", "SyncQueued", r) for r in fr]
    rt = [ms("SyncStart", "FrameCompleted", r) for r in fr]
    gpu = [ms("IssueDrawCommandsStart", "GpuCompleted", r) for r in fr if r.get("GpuCompleted", 0) > 0]
    total = [ms("IntendedVsync", "FrameCompleted", r) for r in fr]
    import os
    hz = float(os.environ.get("REFRESH_HZ", "60") or 60)
    budget = 1000.0 / hz  # one refresh period
    late = sum(1 for t in total if t > 2 * budget)
    head = (f"frames={len(fr)} budget_ms={budget:.1f} total_p50={pct(total, 50):.1f} total_p90={pct(total, 90):.1f} "
            f"total_max={max(total):.1f} over_2x_budget={late} delay_p90={pct(delay, 90):.1f} delay_max={max(delay):.1f} "
            f"ui_p90={pct(ui, 90):.1f} render_p90={pct(rt, 90):.1f} gpu_p90={pct(gpu, 90):.1f}")
    print(head)
    print(f"{'ms':<12}{'p50':>8}{'p90':>8}{'p99':>8}{'max':>8}")
    for name, xs in (("start delay", delay), ("ui", ui), ("render", rt), ("gpu", gpu), ("total", total)):
        if xs:
            print(f"{name:<12}{pct(xs, 50):>8.1f}{pct(xs, 90):>8.1f}{pct(xs, 99):>8.1f}{max(xs):>8.1f}")
    if len(sys.argv) >= 4:
        print("thread CPU seconds during the workload:")
        for secs, name in threads(sys.argv[2], sys.argv[3])[:8]:
            print(f"  {name:<20}{secs:>7.2f}")


if __name__ == "__main__":
    main()
