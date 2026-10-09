"""Builds out/report.md from out/segments.jsonl (vad_compare.py) and out/stt_results.jsonl
(stt_compare.py, optional): VAD x STT x noise tables and an automatic verdict.

usage: report.py [--out scripts/voice-eval/out]

Tables:
  1. VAD behaviour, per VAD x noise level: clipped onsets (share of speech segments whose first
     word lost its start), mean clipped ms, detector onset lag, mid-word cuts, dropped words,
     missed clips, segments vs natural phrases, spurious segments, false segments per minute of
     noise-only audio. (Definitions: vad_compare.py docstring.)
  2. VAD x STT x noise level: WER, typed-command accuracy, hallucinations (non-empty output on
     noise-only / spurious segments; how many were stock phrases), RTF on this PC.
  3. Clean-audio WER per room condition (near / far / fan / tv), VAD x STT.
Verdict: Silero against energy on each headline metric, pooled over every noise level, as a
relative change; "tangible" needs >= 10 % relative AND a meaningful absolute change (stated per line).
"""
import argparse
import json
import os
from collections import defaultdict

from common import OUT, read_jsonl


def noise_order(label):
    if label == "clean":
        return (0, "", 0)
    kind, snr = label.split("@")
    return (1, kind, -int(snr.rstrip("dB")))


def pct(a, b):
    return f"{100 * a / b:.1f}%" if b else "–"


def fmt(v, spec=".1f"):
    return "–" if v is None else format(v, spec)


def vad_rows(segs):
    agg = defaultdict(lambda: defaultdict(float))
    for r in segs:
        key = (r["vad"], r["noise"])
        a = agg[key]
        m = r["metrics"]
        if r["noise_only"]:
            a["noise_minutes"] += r["seconds"] / 60
            a["false_segments"] += m.get("false_segments", 0)
            continue
        a["clips"] += 1
        for k in ("speech_segments", "clipped_onsets", "clipped_ms", "mid_word_cuts", "dropped_words", "words",
                  "missed", "spurious", "phrases", "segments"):
            a[k] += m.get(k) or 0
        a["split_abs"] += abs(m.get("split_error") or 0)
        a["over"] += int((m.get("split_error") or 0) > 0)
        a["under"] += int((m.get("split_error") or 0) < 0)
        if m.get("onset_late_ms") is not None:
            a["lag_sum"] += m["onset_late_ms"]
            a["lag_n"] += 1
    return agg


def stt_rows(results, by_condition=False):
    agg = defaultdict(lambda: defaultdict(float))
    for r in results:
        key = (r["vad"], r["stt"], r["condition"] if by_condition else r["noise"])
        if by_condition and r["noise"] != "clean":
            continue
        a = agg[key]
        a["proc_ms"] += r["proc_ms"]
        a["audio_s"] += r["audio_s"]
        a["halluc"] += len(r["hallucinations"])
        a["stock"] += r["stock_hallucinations"]
        if r["noise_only"]:
            a["noise_segments"] += r["segments"]
            continue
        a["wer_err"] += r.get("wer_err", 0)
        a["wer_words"] += r.get("wer_words", 0)
        if "cmd_ok" in r:
            a["cmd_ok"] += r["cmd_ok"]
            a["cmd_n"] += 1
    return agg


def verdict(vagg, sagg):
    """Silero vs energy, pooled over all noise levels. Positive "reduces" = Silero better."""
    lines = []

    def pooled(agg, vad, fields, stt=None):
        out = defaultdict(float)
        for key, a in agg.items():
            if key[0] != vad or (stt is not None and key[1] != stt):
                continue
            for f in fields:
                out[f] += a[f]
        return out

    def compare(label, e, s, unit, min_abs):
        if e is None or s is None:
            return
        if e == 0 and s == 0:
            lines.append(f"- {label}: none with either VAD.")
            return
        if e == 0:
            lines.append(f"- {label}: energy {e:.2f}{unit}, Silero {s:.2f}{unit} — **Silero is worse** (energy had none).")
            return
        change = (e - s) / e * 100
        tangible = abs(change) >= 10 and abs(e - s) >= min_abs
        word = "reduces" if change > 0 else "increases"
        tag = "tangible" if tangible else "not tangible"
        lines.append(f"- Silero {word} {label} by {abs(change):.0f}% "
                     f"(energy {e:.2f}{unit} → Silero {s:.2f}{unit}; {tag}, needs ≥10% and ≥{min_abs}{unit} absolute).")

    fields = ("clipped_onsets", "speech_segments", "mid_word_cuts", "dropped_words", "words", "false_segments",
              "noise_minutes", "spurious", "clips", "missed")
    E, S = pooled(vagg, "energy", fields), pooled(vagg, "silero", fields)
    if not E["clips"] or not S["clips"]:
        return ["Both energy and silero runs are needed for a verdict."]
    r = lambda a, b: 100 * a / b if b else None  # noqa: E731
    compare("clipped onsets (share of speech segments)", r(E["clipped_onsets"], E["speech_segments"]),
            r(S["clipped_onsets"], S["speech_segments"]), "%", 2)
    compare("mid-word cuts (per clip)", E["mid_word_cuts"] / E["clips"], S["mid_word_cuts"] / S["clips"], "", 0.05)
    compare("dropped words (share of words)", r(E["dropped_words"], E["words"]), r(S["dropped_words"], S["words"]), "%", 1)
    compare("missed clips (no segment at all, share)", r(E["missed"], E["clips"]), r(S["missed"], S["clips"]), "%", 1)
    if E["noise_minutes"] and S["noise_minutes"]:
        compare("false segments on noise-only audio (per minute)", E["false_segments"] / E["noise_minutes"],
                S["false_segments"] / S["noise_minutes"], "/min", 0.5)
    compare("spurious segments in speech clips (per clip)", E["spurious"] / E["clips"], S["spurious"] / S["clips"], "", 0.05)
    stts = sorted({k[1] for k in sagg})
    for stt in stts:
        f = ("wer_err", "wer_words", "halluc")
        e, s = pooled(sagg, "energy", f, stt), pooled(sagg, "silero", f, stt)
        if e["wer_words"] and s["wer_words"]:
            compare(f"WER with {stt}", r(e["wer_err"], e["wer_words"]), r(s["wer_err"], s["wer_words"]), "%", 1)
        compare(f"hallucinated outputs with {stt} (count)", e["halluc"], s["halluc"], "", 3)
    return lines


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default=OUT)
    args = ap.parse_args()
    segs = read_jsonl(os.path.join(args.out, "segments.jsonl"))
    results = read_jsonl(os.path.join(args.out, "stt_results.jsonl"))
    if not segs:
        raise SystemExit("no segments.jsonl: run vad_compare.py first")
    cfg_path = os.path.join(args.out, "vad_config.json")
    cfg = json.load(open(cfg_path)) if os.path.isfile(cfg_path) else {}
    vagg = vad_rows(segs)
    sagg = stt_rows(results)
    L = ["# Voice VAD × STT evaluation", ""]
    n_speech = len({r["vid"] for r in segs if not r["noise_only"]})
    n_noise = len({r["vid"] for r in segs if r["noise_only"]})
    L.append(f"{n_speech} speech variants, {n_noise} noise-only variants. VAD settings: pause "
             f"{cfg.get('pause_ms', '?')} ms, window {cfg.get('window_s', '?')} s, Silero onset/hold "
             f"{cfg.get('silero_onset', '?')}/{cfg.get('silero_hold', '?')}. Synthetic speech (Piper): compare "
             f"models against each other, not against real-voice accuracy. RTF is this PC's, not the phone's.")
    L += ["", "## Verdict (Silero vs energy, all noise levels pooled)", ""]
    L += verdict(vagg, sagg)

    L += ["", "## 1. VAD behaviour", "",
          "| VAD | noise | clips | clipped onsets | clipped ms (mean) | onset lag ms | mid-word cuts | dropped words | "
          "missed | segs / phrases | over / under-split | spurious | false segs/min (noise-only) |",
          "|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|"]
    for (vad, noise), a in sorted(vagg.items(), key=lambda kv: (noise_order(kv[0][1]), kv[0][0])):
        fpm = a["false_segments"] / a["noise_minutes"] if a["noise_minutes"] else None
        L.append(f"| {vad} | {noise} | {a['clips']:.0f} | {pct(a['clipped_onsets'], a['speech_segments'])} | "
                 f"{fmt(a['clipped_ms'] / a['clipped_onsets'] if a['clipped_onsets'] else None, '.0f')} | "
                 f"{fmt(a['lag_sum'] / a['lag_n'] if a['lag_n'] else None, '.0f')} | {a['mid_word_cuts']:.0f} | "
                 f"{a['dropped_words']:.0f}/{a['words']:.0f} | {a['missed']:.0f} | "
                 f"{a['speech_segments']:.0f}/{a['phrases']:.0f} | {a['over']:.0f}/{a['under']:.0f} | "
                 f"{a['spurious']:.0f} | {fmt(fpm, '.2f')} |")

    if results:
        L += ["", "## 2. VAD × STT × noise", "",
              "| VAD | STT | noise | WER | commands | hallucinations (stock) | RTF (PC) |",
              "|---|---|---|---:|---:|---:|---:|"]
        for (vad, stt, noise), a in sorted(sagg.items(), key=lambda kv: (noise_order(kv[0][2]), kv[0][1], kv[0][0])):
            rtf = a["proc_ms"] / 1000 / a["audio_s"] if a["audio_s"] else None
            cmds = f"{a['cmd_ok']:.0f}/{a['cmd_n']:.0f}" if a["cmd_n"] else "–"
            L.append(f"| {vad} | {stt} | {noise} | {pct(a['wer_err'], a['wer_words'])} | {cmds} | "
                     f"{a['halluc']:.0f} ({a['stock']:.0f}) | {fmt(rtf, '.3f')} |")
        cagg = stt_rows(results, by_condition=True)
        L += ["", "## 3. Clean audio by room condition (WER)", ""]
        conds = sorted({k[2] for k in cagg})
        L.append("| VAD | STT | " + " | ".join(conds) + " |")
        L.append("|---|---|" + "---:|" * len(conds))
        for vad, stt in sorted({(k[0], k[1]) for k in cagg}, key=lambda x: (x[1], x[0])):
            cells = [pct(cagg[(vad, stt, c)]["wer_err"], cagg[(vad, stt, c)]["wer_words"]) for c in conds]
            L.append(f"| {vad} | {stt} | " + " | ".join(cells) + " |")
        # The most common hallucinated strings, for the terminal sanitiser's block list.
        counts = defaultdict(int)
        for r in results:
            for h in r["hallucinations"]:
                counts[(r["stt"], h.strip())] += 1
        if counts:
            L += ["", "## Most frequent hallucinated outputs", "", "| STT | text | count |", "|---|---|---:|"]
            for (stt, text), n in sorted(counts.items(), key=lambda kv: -kv[1])[:20]:
                L.append(f"| {stt} | {text.replace('|', '/')[:60]} | {n} |")
    else:
        L += ["", "_No stt_results.jsonl yet: run stt_compare.py for the WER tables._"]
    path = os.path.join(args.out, "report.md")
    with open(path, "w", encoding="utf-8") as f:
        f.write("\n".join(L) + "\n")
    print("\n".join(L[:L.index("## 1. VAD behaviour")]))
    print("wrote", path)


if __name__ == "__main__":
    main()
