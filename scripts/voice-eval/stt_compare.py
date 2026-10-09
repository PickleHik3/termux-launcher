"""Each VAD's segments x each speech model: transcripts, WER, hallucinations, real-time factor.

Reads out/segments.jsonl from vad_compare.py (run it first with the same clip flags), rebuilds
each variant's audio, cuts it by the segments, applies VoiceGain (as the app does before
transcription) and transcribes every segment. Writes out/stt_results.jsonl, one line per
variant x VAD x STT. Transcripts are cached in out/stt_cache/<stt>.jsonl by the segment's PCM
hash, so a re-run (or two VADs cutting the same segment) never transcribes the same audio twice.

usage: stt_compare.py [--stt parakeet,whisper-small.en,moonshine-tiny] [--vads energy,silero]
                      [same clip flags as vad_compare.py]

Metrics:
  WER             word errors (substitutions + deletions + insertions, voice_eval_score.wer) over
                  reference words, all of a clip's segment texts joined in order. Missed speech
                  (a clipped onset, a dropped word) shows up here as deletions.
  commands        "key"/"text" clips (typed commands such as "git status") whose joined text
                  matched the reference exactly after normalisation (voice_eval_score.words).
  hallucinations  non-empty text from audio with no target speech: every segment of a noise-only
                  variant, and every "spurious" segment of a speech clip (one that overlaps no
                  speech). Known stock phrases ("Thank you.", "[Music]", "ok", ...) are tagged.
  RTF             STT processing seconds / seconds of audio sent, on THIS PC (not the phone).
                  Cache hits reuse the first measurement.
"""
import argparse
import hashlib
import json
import os
import sys
import time
from collections import defaultdict

from common import (FRAME_SAMPLES, SR, add_set_args, noise_only_variants, read_jsonl, select,
                    speech_variants, to_pcm16, voice_gain, words)
from stt_engines import make_engine
from voice_eval_score import wer

# Output well known as what speech models say over silence or noise.
STOCK_HALLUCINATIONS = {
    "ok", "okay", "thank you", "thanks", "thank you for watching", "thanks for watching", "music",
    "you", "bye", "yeah", "so", "oh", "hmm", "um", "uh", "applause", "laughter", "silence", "blank audio",
    "subscribe", "please subscribe",
}


def is_stock(text):
    norm = " ".join(words(text.replace("[", " ").replace("]", " ").replace("(", " ").replace(")", " ")))
    return norm in STOCK_HALLUCINATIONS


class Cache:
    def __init__(self, path):
        self.path = path
        self.data = {r["key"]: r for r in read_jsonl(path)}
        os.makedirs(os.path.dirname(path), exist_ok=True)
        self.f = open(path, "a", encoding="utf-8")

    def get(self, key):
        return self.data.get(key)

    def put(self, key, text, ms, seconds):
        r = {"key": key, "text": text, "ms": ms, "seconds": seconds}
        self.data[key] = r
        self.f.write(json.dumps(r) + "\n")
        self.f.flush()


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    add_set_args(ap)
    ap.add_argument("--stt", default="parakeet,whisper-small.en,moonshine-tiny")
    ap.add_argument("--vads", default=None, help="subset of the VADs in segments.jsonl (default all)")
    ap.add_argument("--threads", type=int, default=4, help="CPU threads per model (the app uses 4)")
    ap.add_argument("--moonshine-backend", default="onnx", choices=("onnx", "transformers"))
    ap.add_argument("--moonshine-dir", default=None, help="default ~/.cache/termux-launcher/moonshine/tiny")
    ap.add_argument("--no-noise-only", action="store_true")
    args = ap.parse_args()

    clips, factory, noises, snrs, augment = select(args)
    by_name = {c.name: c for c in clips}
    seg_path = os.path.join(args.out, "segments.jsonl")
    records = read_jsonl(seg_path)
    if not records:
        sys.exit(f"{seg_path} is empty: run vad_compare.py first")
    wanted = set(speech_variants(clips, noises, snrs, augment))
    if not args.no_noise_only:
        wanted |= set(noise_only_variants(clips, noises, snrs, augment))
    vads = set(args.vads.split(",")) if args.vads else None
    records = [r for r in records if r["vid"] in wanted and (not vads or r["vad"] in vads)]
    missing = wanted - {r["vid"] for r in records}
    if missing:
        print(f"warning: {len(missing)} selected variants have no segments (re-run vad_compare.py with "
              f"the same flags); e.g. {sorted(missing)[:3]}", flush=True)
    by_vid = defaultdict(list)
    for r in records:
        by_vid[r["vid"]].append(r)

    engines = []
    for name in [s for s in args.stt.split(",") if s]:
        t0 = time.time()
        engine = make_engine(name, args)
        engines.append((engine, Cache(os.path.join(args.out, "stt_cache", engine.name + ".jsonl"))))
        print(f"loaded {engine.name} ({time.time() - t0:.1f}s)", flush=True)

    out_path = os.path.join(args.out, "stt_results.jsonl")
    total = len(by_vid)
    t_start = time.time()
    fresh = 0
    with open(out_path, "w", encoding="utf-8") as out:
        for i, (vid, recs) in enumerate(sorted(by_vid.items())):
            audio = factory.audio(vid)
            for rec in recs:
                clip = by_name.get(rec["clip"])
                speech = None
                if not rec["noise_only"]:
                    speech = _speech_extent(rec, args.out)
                for engine, cache in engines:
                    texts, halluc, proc_ms, audio_s = [], [], 0.0, 0.0
                    for k, seg in enumerate(rec["segments"]):
                        pcm = to_pcm16(audio[seg["start"] * FRAME_SAMPLES:seg["end"] * FRAME_SAMPLES])
                        key = hashlib.sha1(pcm.tobytes()).hexdigest()
                        hit = cache.get(key)
                        if hit is None:
                            gained = voice_gain(pcm)
                            t0 = time.perf_counter()
                            text = engine.transcribe(gained)
                            ms = (time.perf_counter() - t0) * 1000
                            cache.put(key, text, ms, len(pcm) / SR)
                            fresh += 1
                        else:
                            text, ms = hit["text"], hit["ms"]
                        proc_ms += ms
                        audio_s += len(pcm) / SR
                        no_speech = rec["noise_only"] or (
                            speech is not None and not _overlaps(seg, speech))
                        if no_speech:
                            if words(text):
                                halluc.append(text)
                        texts.append(text)
                    hyp = " ".join(t for t in texts if t)
                    row = {"vid": vid, "vad": rec["vad"], "stt": engine.name, "clip": rec["clip"],
                           "noise": rec["noise"], "condition": rec["condition"], "kind": rec["kind"],
                           "noise_only": rec["noise_only"], "segments": len(rec["segments"]),
                           "texts": texts, "hyp": hyp, "proc_ms": round(proc_ms, 1),
                           "audio_s": round(audio_s, 3), "hallucinations": halluc,
                           "stock_hallucinations": sum(1 for h in halluc if is_stock(h))}
                    if clip is not None and clip.text:
                        # The reference counts only words the speaker said; hallucinated text on a
                        # spurious segment is inside hyp too and costs insertions, as it would on the phone.
                        errors, n = wer(clip.text, hyp)
                        row.update({"ref": clip.text, "wer_err": errors, "wer_words": n})
                        if clip.kind in ("key", "text"):
                            row["cmd_ok"] = int(words(hyp) == words(clip.text))
                    out.write(json.dumps(row) + "\n")
            if (i + 1) % 20 == 0 or i + 1 == total:
                el = time.time() - t_start
                print(f"{i + 1}/{total} variants, {fresh} new transcriptions, {el:.0f}s elapsed, "
                      f"~{el / (i + 1) * (total - i - 1) / 60:.0f} min left", flush=True)
    print("wrote", out_path)


_ALIGN = {}


def _speech_extent(rec, out_dir):
    """The clip's speech extent (seconds) from alignments.json, widened as vad_compare does."""
    if not _ALIGN:
        path = os.path.join(out_dir, "alignments.json")
        _ALIGN.update(json.load(open(path)) if os.path.isfile(path) else {"_": None})
    name = rec["clip"]
    key = name if name.startswith("real:") else "near-" + name.split("-", 1)[1]
    tr = _ALIGN.get(key)
    if not tr or not tr.get("speech"):
        return None
    return tr["speech"][0] - 0.1, tr["speech"][1] + 0.3


def _overlaps(seg, speech):
    a, b = seg["start"] * 0.03, seg["end"] * 0.03
    return min(b, speech[1]) > max(a, speech[0])


if __name__ == "__main__":
    main()
