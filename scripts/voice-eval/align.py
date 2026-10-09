"""Ground-truth timing for the clips: where speech, each word and each natural phrase sit.

voice_eval_make.py does not record word timings, so they are recovered here:

1. Speech extent and natural phrases, from the *near* clip's energy. The near clip is the phrase
   at -26 dBFS over a -70 dBFS room with no reverb (44 dB SNR), so 10 ms frames more than 20 dB
   over the room are speech with ~10 ms precision. Gaps between them of at least the VAD pause
   (600 ms) separate natural phrases: what an ideal segmenter would send as separate segments.
2. Word boundaries inside the speech, from Parakeet TDT's own timestamps: the greedy TDT loop
   knows the encoder frame (80 ms) at which it emitted each token and how many frames the token
   lasts (duration head). A word runs from its first token's frame to its last token's frame plus
   duration. This is the ASR aligning its own transcript, not forced alignment to the reference
   text, so interior boundaries are good to about +-80 ms; the first word's start and the last
   word's end are snapped to the energy extent from step 1.

All four conditions of one phrase+voice share the near clip's timing (the generator places the
same speech at the same 1.0 s offset; far/fan/tv only add reverb and noise), and noise
augmentation does not move the speech either. Real clips are aligned on themselves (step 2 only).

Output: out/alignments.json
  {align_key: {"speech": [s, e], "phrases": [[s, e], ...], "words": [[word, s, e], ...],
               "asr": "<parakeet text>"}}   (seconds)
"""
import json
import os

import numpy as np

from common import LEAD_S, SR, load_wav

ALIGN_FRAME = 160  # 10 ms energy frames


def energy_extent(audio, pause_s):
    """Speech intervals of a clean clip: 10 ms frames 20 dB over the room (the room level is
    measured over the lead, before the phrase), merged across gaps shorter than pause_s."""
    n = len(audio) // ALIGN_FRAME
    frames = audio[:n * ALIGN_FRAME].astype(np.float64).reshape(n, ALIGN_FRAME)
    e = 10 * np.log10(np.mean(frames ** 2, axis=1) + 1e-12)
    lead = int(LEAD_S * SR) // ALIGN_FRAME
    room = np.median(e[:max(1, lead - 5)])
    active = e > room + 20
    idx = np.flatnonzero(active)
    if len(idx) == 0:
        return []
    phrases = []
    start = prev = idx[0]
    for i in idx[1:]:
        if (i - prev) * ALIGN_FRAME / SR >= pause_s:
            phrases.append([start * ALIGN_FRAME / SR, (prev + 1) * ALIGN_FRAME / SR])
            start = i
        prev = i
    phrases.append([start * ALIGN_FRAME / SR, (prev + 1) * ALIGN_FRAME / SR])
    # A lone 10-30 ms blip (a click) is not a phrase.
    return [p for p in phrases if p[1] - p[0] >= 0.06]


class TimedParakeet:
    """parakeet_replay_server.Parakeet, with the TDT loop re-run to keep each token's frame.

    The loop is the same as Parakeet.window (kept in step with it by hand; if that one changes,
    change this one): the only additions are `times` and `durs`.
    """

    def __init__(self, model_path, tokenizer_path, threads=4):
        from parakeet_replay_server import Parakeet
        self.p = Parakeet(model_path, tokenizer_path, threads)

    def window(self, audio):
        from parakeet_replay_server import BLANK, MAX_TOKENS, NUM_DURATIONS, WINDOW_SAMPLES, features
        p = self.p
        enc = p.enc(args_0=features(audio))["output_0"]
        max_t = enc.shape[-1]
        frame_s = WINDOW_SAMPLES / SR / max_t
        zeros = np.zeros((2, 1, 640), np.float32)
        h, c = zeros, zeros
        emitted, times, durs = [], [], []
        tokens = np.zeros((1, p.slots), np.int32)
        tokens[0, 0] = BLANK
        slot, stateful, t = 0, False, 0
        while t < max_t and len(emitted) < MAX_TOKENS:
            if stateful:
                out = p.dec1(args_0=enc, args_1=tokens, args_2=h, args_3=c)
                logits = out["output_0"][0, t, 0]
            else:
                out = p.dec(args_0=enc, args_1=tokens, args_2=zeros, args_3=zeros)
                logits = out["output_0"][0, t, slot]
            token = int(np.argmax(logits[:-NUM_DURATIONS]))
            duration = int(np.argmax(logits[-NUM_DURATIONS:]))
            if token != BLANK:
                emitted.append(token)
                times.append(t * frame_s)
                durs.append(max(1, duration) * frame_s)
                if stateful:
                    h, c = out["output_1"], out["output_2"]
                    tokens[0, 0] = token
                else:
                    slot += 1
                    if slot < p.slots:
                        tokens[0, slot] = token
                    else:
                        stateful = True
                        h, c = out["output_1"], out["output_2"]
                        tokens = np.array([[token]], np.int32)
            t += 1 if (duration == 0 and token == BLANK) else duration
        return emitted, times, durs

    def words(self, audio):
        """[[word, start_s, end_s], ...] and the joined text, over 5 s pieces with offsets."""
        from parakeet_replay_server import split
        out = []
        offset = 0
        for piece in split(audio):
            ids, times, durs = self.window(piece)
            base = offset / SR
            for tid, ts, d in zip(ids, times, durs):
                if tid in self.p.tok.special:
                    continue
                text = self.p.tok.pieces.get(tid, "")
                starts_word = text.startswith("▁") or not out
                clean = text.replace("▁", "")
                if not clean:
                    continue
                if starts_word:
                    out.append([clean, base + ts, base + ts + d])
                else:
                    out[-1][0] += clean
                    # Punctuation often comes out a frame or two late: it does not stretch a word.
                    if any(ch.isalnum() for ch in clean):
                        out[-1][2] = base + ts + d
            offset += len(piece)
        return out, " ".join(w for w, _, _ in out)


def align_clips(clips, out_path, parakeet_model, parakeet_tokenizer, pause_s, threads=4, log=print):
    """Fills out_path with every clip's timing; keys already there are kept (it is a cache)."""
    data = json.load(open(out_path)) if os.path.isfile(out_path) else {}
    todo = {}
    for clip in clips:
        if clip.align_key in data:
            continue
        # The alignment clip: the near version for the synthetic set, the clip itself for real ones.
        if clip.kind == "real":
            todo[clip.align_key] = (clip.path, False)
        else:
            path = os.path.join(os.path.dirname(clip.path), clip.align_key)
            todo[clip.align_key] = (path if os.path.isfile(path) else clip.path, True)
    if not todo:
        return data
    tp = None
    if parakeet_model and os.path.isfile(parakeet_model):
        tp = TimedParakeet(parakeet_model, parakeet_tokenizer, threads)
    else:
        log(f"align: no Parakeet model at {parakeet_model}; word timings off, speech/phrase extent only")
    for i, (key, (path, synthetic)) in enumerate(sorted(todo.items())):
        audio = load_wav(path)
        phrases = energy_extent(audio, pause_s) if synthetic else []
        words, asr = (tp.words(audio) if tp else ([], ""))
        if phrases:
            words = [w for w in words if w[2] > phrases[0][0] - 0.2 and w[1] < phrases[-1][1] + 0.2]
            if words:
                words[0][1] = phrases[0][0]
                words[-1][2] = max(words[-1][1] + 0.03, phrases[-1][1])
        elif words:
            # Real clip: natural phrases from the word timings (gaps >= the pause).
            phrases = [[words[0][1], words[0][2]]]
            for _, s, e in words[1:]:
                if s - phrases[-1][1] >= pause_s:
                    phrases.append([s, e])
                else:
                    phrases[-1][1] = e
        speech = [phrases[0][0], phrases[-1][1]] if phrases else None
        data[key] = {"speech": speech, "phrases": phrases, "words": words, "asr": asr}
        if (i + 1) % 20 == 0:
            log(f"align: {i + 1}/{len(todo)}")
    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    with open(out_path, "w") as f:
        json.dump(data, f, indent=1)
    return data
