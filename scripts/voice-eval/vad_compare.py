"""Energy VAD (the app's) vs Silero VAD, segment by segment, on the synthetic set (+ noise).

Writes out/segments.jsonl (one line per variant x VAD: its segments and metrics; stt_compare.py
cuts audio by these) and prints a summary; report.py turns it into the markdown report.

usage: vad_compare.py [--vads energy,silero] [--noises tv,babble,fan,white] [--snrs 10,5,0]
                      [--augment near] [--conditions near,far,fan,tv] [--kinds dictation] ...
See README.md for setup and the full commands.

Metrics (per clip; TOL = 40 ms slack for alignment error):
  onset_late_ms   first voiced frame of the first speech segment minus the first word's start:
                  how late the detector itself reacts. The 300 ms pre-roll hides up to 300 ms.
  clipped_onsets  segments whose start (pre-roll included, i.e. the audio the STT really gets)
                  falls after the start of the first word they contain (+TOL): that word's onset
                  is cut off. clipped_ms sums how much.
  mid_word_cuts   segment boundaries (starts and ends) that fall inside a word (TOL in from each
                  edge): the word is split across two segments or truncated.
  dropped_words   words less than half covered by any segment: never sent to the STT.
  segments / phrases
                  segments emitted vs natural phrases (speech runs separated by >= the pause, see
                  align.py); split_error = segments - phrases (>0 over-split, <0 merged/missed).
  spurious        segments on a speech clip that overlap no speech (e.g. the TV between words).
  false_segments  segments on noise-only audio (noise:* variants): each one would be sent to
                  the STT and is a chance for "Thank you." to be typed into the terminal.
"""
import argparse
import json
import os
import sys
import time

from align import align_clips
from common import (FRAME_MS, add_set_args, noise_label, noise_only_variants, parse_variant, select,
                    speech_variants)
from vads import DEFAULT_PAUSE_MS, DEFAULT_WINDOW_S, PAD_MS, Segmenter, make_vads

TOL = 0.04
F = FRAME_MS / 1000.0


def overlap(a0, a1, b0, b1):
    return max(0.0, min(a1, b1) - max(a0, b0))


def clip_metrics(segs, truth):
    """The speech-clip metrics above. truth is the clip's alignments.json entry (or None)."""
    m = {"segments": len(segs)}
    if not truth or not truth.get("speech"):
        return m
    s0, s1 = truth["speech"]
    phrases = truth.get("phrases") or [truth["speech"]]
    words = truth.get("words") or []
    m["phrases"] = len(phrases)
    iv = [(g["start"] * F, g["end"] * F, g) for g in segs]
    # A segment is "speech" if it overlaps the speech extent (100 ms before, 300 ms reverb after).
    speech_segs = [x for x in iv if overlap(x[0], x[1], s0 - 0.1, s1 + 0.3) > 0]
    m["speech_segments"] = len(speech_segs)
    m["spurious"] = len(iv) - len(speech_segs)
    m["split_error"] = len(speech_segs) - len(phrases)
    if speech_segs and speech_segs[0][2]["first_voiced"] is not None:
        m["onset_late_ms"] = round((speech_segs[0][2]["first_voiced"] * F - s0) * 1000)
    else:
        m["onset_late_ms"] = None  # speech never detected
    m["missed"] = int(not speech_segs)
    # Word-level truth: every word, or the whole speech run as one "word" when no timings exist.
    units = [(w[1], w[2]) for w in words] or [(p[0], p[1]) for p in phrases]
    clipped, clipped_ms = 0, 0.0
    for a, b, _ in speech_segs:
        first = next(((ws, we) for ws, we in units if overlap(ws, we, a, b) > 0), None)
        if first and a > first[0] + TOL:
            clipped += 1
            clipped_ms += (a - first[0]) * 1000
    m["clipped_onsets"] = clipped
    m["clipped_ms"] = round(clipped_ms)
    if words:
        cuts = 0
        for a, b, _ in iv:
            for edge in (a, b):
                if any(ws + TOL < edge < we - TOL for ws, we in units):
                    cuts += 1
        m["mid_word_cuts"] = cuts
        m["words"] = len(words)
        m["dropped_words"] = sum(
            1 for ws, we in units
            if sum(overlap(ws, we, a, b) for a, b, _ in iv) < 0.5 * (we - ws))
    return m


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    add_set_args(ap)
    ap.add_argument("--vads", default="energy,silero", help="energy, silero, oracle (truth +-300 ms)")
    ap.add_argument("--silero-model", default=None, help="silero_vad.onnx v5 (default ~/.cache/termux-launcher/silero/)")
    ap.add_argument("--silero-onset", type=float, default=0.5)
    ap.add_argument("--silero-hold", type=float, default=0.35)
    ap.add_argument("--pause-ms", type=int, default=DEFAULT_PAUSE_MS)
    ap.add_argument("--window-s", type=int, default=DEFAULT_WINDOW_S)
    home = os.path.expanduser("~/.cache/termux-launcher/parakeet")
    ap.add_argument("--parakeet-model", default=os.path.join(home, "parakeet_tdt_0.6b_v3_5s_i8_stateful.tflite"))
    ap.add_argument("--parakeet-tokenizer", default=os.path.join(home, "tokenizer.json"))
    ap.add_argument("--no-noise-only", action="store_true", help="skip the false-segment runs")
    args = ap.parse_args()

    clips, factory, noises, snrs, augment = select(args)
    if not clips:
        sys.exit(f"no clips under {args.set} (run scripts/voice_eval_make.py first)")
    os.makedirs(args.out, exist_ok=True)

    t0 = time.time()
    truth = align_clips(clips, os.path.join(args.out, "alignments.json"), args.parakeet_model,
                        args.parakeet_tokenizer, args.pause_ms / 1000.0, log=lambda s: print(s, flush=True))
    print(f"alignments ready ({time.time() - t0:.0f}s)", flush=True)

    names = [v for v in args.vads.split(",") if v]
    real_vads = make_vads([v for v in names if v != "oracle"], args.silero_model,
                          args.silero_onset, args.silero_hold)
    by_name = {c.name: c for c in clips}
    variants = speech_variants(clips, noises, snrs, augment)
    if not args.no_noise_only:
        variants += noise_only_variants(clips, noises, snrs, augment)

    out_path = os.path.join(args.out, "segments.jsonl")
    config = {"pause_ms": args.pause_ms, "window_s": args.window_s, "silero_onset": args.silero_onset,
              "silero_hold": args.silero_hold, "set": args.set}
    t0 = time.time()
    with open(out_path, "w", encoding="utf-8") as out:
        for i, vid in enumerate(variants):
            clip_name, noise, snr = parse_variant(vid)
            noise_only = clip_name.startswith("noise:")
            clip = by_name.get(clip_name)
            audio = factory.audio(vid)
            seconds = len(audio) / 16000
            tr = None if noise_only else truth.get(clip.align_key)
            for vad in names:
                if vad == "oracle":
                    # The best a segmenter could do: each natural phrase +-PAD_MS. Upper bound for WER.
                    if noise_only or not tr or not tr.get("phrases"):
                        segs = []
                    else:
                        segs = [{"start": max(0, int((p[0] - PAD_MS / 1000) / F)),
                                 "end": min(int(seconds / F), int((p[1] + PAD_MS / 1000) / F) + 1),
                                 "voiced_frames": int((p[1] - p[0]) / F),
                                 "first_voiced": int(p[0] / F), "last_voiced": int(p[1] / F)}
                                for p in tr["phrases"]]
                else:
                    segs, _, _ = Segmenter(real_vads[vad](), args.pause_ms, args.window_s).run(audio)
                rec = {"vid": vid, "vad": vad, "clip": clip_name, "noise": noise_label(vid),
                       "noise_only": noise_only, "seconds": round(seconds, 3),
                       "condition": clip_name[6:] if noise_only else clip.condition,
                       "kind": "noise" if noise_only else clip.kind,
                       "segments": segs}
                if noise_only:
                    rec["metrics"] = {"false_segments": len(segs)}
                else:
                    rec["metrics"] = clip_metrics(segs, tr)
                out.write(json.dumps(rec) + "\n")
            if (i + 1) % 50 == 0:
                rate = (i + 1) / (time.time() - t0)
                print(f"{i + 1}/{len(variants)} variants, {rate:.1f}/s", flush=True)
    with open(os.path.join(args.out, "vad_config.json"), "w") as f:
        json.dump(config, f, indent=1)
    print(f"wrote {out_path} ({len(variants)} variants x {len(names)} VADs, {time.time() - t0:.0f}s)")
    summarise(out_path)


def summarise(path):
    """A quick console roll-up; report.py makes the real tables."""
    from collections import defaultdict
    agg = defaultdict(lambda: defaultdict(float))
    for line in open(path, encoding="utf-8"):
        r = json.loads(line)
        a = agg[(r["vad"], "noise-only" if r["noise_only"] else "speech")]
        a["n"] += 1
        a["minutes"] += r["seconds"] / 60
        for k, v in r["metrics"].items():
            if isinstance(v, (int, float)) and v is not None:
                a[k] += v
    for (vad, part), a in sorted(agg.items()):
        if part == "noise-only":
            print(f"{vad:7s} noise-only: {a['false_segments']:.0f} false segments in {a['minutes']:.1f} min")
        else:
            print(f"{vad:7s} speech: {a['n']:.0f} clips, clipped onsets {a['clipped_onsets']:.0f}, "
                  f"mid-word cuts {a['mid_word_cuts']:.0f}, dropped words {a['dropped_words']:.0f}/"
                  f"{a['words']:.0f}, missed {a['missed']:.0f}, spurious {a['spurious']:.0f}")


if __name__ == "__main__":
    main()
