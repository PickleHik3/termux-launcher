"""Scores VoiceReplayRig reports against a voice_eval_make.py manifest.

usage: voice_eval_score.py manifest.json label=report.txt [label=report.txt ...]

Per model and condition:
  keys      share of "enter key"-style clips that produced exactly one key press, the right one
  commands  share of typed-command clips ("ls", "git status") whose text matched exactly
            (lowercase, punctuation ignored) and pressed no key
  WER       word error rate of the dictation clips (all text events of a clip joined)
  misfire   dictation clips that pressed a key
  time      mean encode+decode ms per clip on this machine
"""
import json
import re
import sys
from collections import defaultdict

LINE = re.compile(r"^\s+\[[^\]]*\] (?P<desc>.*?)  \(encode (?P<enc>\d+)ms, decode (?P<dec>\d+)ms, steps \d+\)$")
KEY_NAMES = {"Enter": "ENTER", "Tab": "TAB", "Esc": "ESC", "Backspace": "BACKSPACE", "Space": "SPACE", "Ctrl+C": "CTRL_C"}


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
            if desc.startswith("KEY "):
                clips[current].append(("key", KEY_NAMES.get(desc[4:], desc[4:]), ms))
            elif desc.startswith('text "'):
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
            keys = [e[1] for e in events if e[0] == "key"]
            text = " ".join(e[1] for e in events if e[0] == "text")
            if truth["kind"] == "key":
                row["key_n"] += 1
                row["key_ok"] += keys == [truth["expect"]] and not text.strip()
            elif truth["kind"] == "text":
                row["cmd_n"] += 1
                row["cmd_ok"] += (not keys) and words(text) == words(truth["expect"])
            else:
                errors, total = wer(truth["text"], text)
                row["wer_err"] += errors
                row["wer_words"] += total
                row["dict_n"] += 1
                row["misfire"] += bool(keys)
    return rows


def main():
    manifest = json.load(open(sys.argv[1]))
    results = []
    for arg in sys.argv[2:]:
        label, path = arg.split("=", 1)
        results.append((label, score(manifest, parse(path))))
    header = f"{'model':10s} {'condition':9s} {'keys':>9s} {'commands':>9s} {'WER':>7s} {'misfire':>8s} {'ms/clip':>8s}"
    print(header)
    print("-" * len(header))
    for group in ("near", "far", "fan", "tv", "all"):
        for label, rows in results:
            r = rows.get(group)
            if not r:
                continue
            keys = f"{int(r['key_ok'])}/{int(r['key_n'])}"
            cmds = f"{int(r['cmd_ok'])}/{int(r['cmd_n'])}"
            w = f"{100 * r['wer_err'] / max(1, r['wer_words']):.1f}%"
            mis = f"{int(r['misfire'])}/{int(r['dict_n'])}"
            print(f"{label:10s} {group:9s} {keys:>9s} {cmds:>9s} {w:>7s} {mis:>8s} {r['ms'] / max(1, r['clips']):8.0f}")
        print()


if __name__ == "__main__":
    main()
