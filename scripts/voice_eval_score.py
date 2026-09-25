"""Scores VoiceReplayRig reports against a voice_eval_make.py manifest.

usage: voice_eval_score.py manifest.json label=report.txt [label=report.txt ...]

Per model and condition:
  commands  share of typed-command clips ("ls", "git status") whose text matched exactly
            (lowercase, punctuation ignored)
  WER       word error rate of the dictation clips (all text events of a clip joined)
  time      mean encode+decode ms per clip on this machine
"""
import json
import re
import sys
from collections import defaultdict

LINE = re.compile(r"^\s+\[[^\]]*\] (?P<desc>.*?)  \(encode (?P<enc>\d+)ms, decode (?P<dec>\d+)ms, steps \d+\)$")


def parse(report):
    clips, current = {}, None
    for raw in open(report, encoding="utf-8"):
        line = raw.rstrip("\n")
        if line.startswith("clip "):
            current = line[5:].strip()
            clips[current] = []
            continue
        m = LINE.match(line)
        if m and current:
            desc = m["desc"]
            ms = int(m["enc"]) + int(m["dec"])
            if desc.startswith('text "'):
                clips[current].append(("text", desc[6:-1], ms))
            else:
                clips[current].append(("other", desc, ms))
    return clips


def words(text):
    return re.sub(r"[^a-z0-9' ]+", " ", text.lower().replace("-", " ")).split()


def wer(ref, hyp):
    r, h = words(ref), words(hyp)
    d = list(range(len(h) + 1))
    for i in range(1, len(r) + 1):
        prev, d[0] = d[0], i
        for j in range(1, len(h) + 1):
            cur = min(d[j] + 1, d[j - 1] + 1, prev + (r[i - 1] != h[j - 1]))
            prev, d[j] = d[j], cur
    return d[len(h)], max(1, len(r))


def score(manifest, clips):
    rows = defaultdict(lambda: defaultdict(float))
    for name, truth in manifest.items():
        events = clips.get(name)
        if events is None:
            continue
        cond = truth["condition"]
        for group in (cond, "all"):
            row = rows[group]
            row["ms"] += sum(e[2] for e in events)
            row["clips"] += 1
            text = " ".join(e[1] for e in events if e[0] == "text")
            if truth["kind"] == "text":
                row["cmd_n"] += 1
                row["cmd_ok"] += words(text) == words(truth["expect"])
            else:
                errors, total = wer(truth["text"], text)
                row["wer_err"] += errors
                row["wer_words"] += total
                row["dict_n"] += 1
    return rows


def main():
    manifest = json.load(open(sys.argv[1]))
    results = []
    for arg in sys.argv[2:]:
        label, path = arg.split("=", 1)
        results.append((label, score(manifest, parse(path))))
    header = f"{'model':10s} {'condition':9s} {'commands':>9s} {'WER':>7s} {'ms/clip':>8s}"
    print(header)
    print("-" * len(header))
    for group in ("near", "far", "fan", "tv", "all"):
        for label, rows in results:
            r = rows.get(group)
            if not r:
                continue
            cmds = f"{int(r['cmd_ok'])}/{int(r['cmd_n'])}"
            w = f"{100 * r['wer_err'] / max(1, r['wer_words']):.1f}%"
            print(f"{label:10s} {group:9s} {cmds:>9s} {w:>7s} {r['ms'] / max(1, r['clips']):8.0f}")
        print()


if __name__ == "__main__":
    main()
