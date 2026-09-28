# Voice activity detection: energy vs Silero vs hybrid (2026-09-27)

Harness: `scripts/voice-eval/` (PC; synthetic Piper voices from `scripts/voice_eval_make.py` plus
synthesised tv/babble/fan/white noise at 10/5/0 dB). Good for comparing detectors, not for absolute
accuracy on real voices.

**Decision: ship Silero VAD v5 (bundled in the APK, about 2 MB, converted to LiteRT).** It loses far
less speech in loud rooms (fan at 0 dB: 1 missed clip vs 18, 0 dropped words vs 70; babble at 0 dB: 0 vs
10 and 0 vs 77) and roughly halves hallucinated text, at about the same word error rate as the energy
detector with Whisper. The hybrid (Silero plus a loudness gate relative to the loudest speech so far)
did not help: it clipped more onsets on clean audio and doubled hallucinations with a TV at 5 dB.
moonshine-tiny was dropped (60-70% WER on clean audio). Whisper small.en is the steadier speech model in
noise; Parakeet stays the faster one for quiet, close-up dictation.

## Shipping it: conversion, parity and the app side

**Conversion** (`scripts/voice-eval/convert_silero.py`). Source: Silero VAD v5.1.2
`src/silero_vad/data/silero_vad.onnx` (sha256 `2623a295…bdd788f`). The 16 kHz branch of the graph is
small and fixed, so it was rebuilt by hand as a TensorFlow function with the weights copied out of
the ONNX constants, then converted with `TFLiteConverter` restricted to `TFLITE_BUILTINS`, float32,
no optimisations. onnx2tf and ai-edge-torch were not needed: the rebuild sidesteps Silero's
`sr == 16000 / 8000` If nodes and its dynamic-shape LSTM wrapper, and gives the interface the app
wants directly:

| tensor | shape | meaning |
|---|---|---|
| in `input` | `[1, 576]` float32 | 64 samples of context (the previous chunk's tail, zeros at the start) + one 512-sample chunk at 16 kHz, samples in [-1, 1] |
| in `state` | `[2, 1, 128]` float32 | LSTM h and c; zeros at the start of a stream |
| out `prob` | `[1, 1]` float32 | speech probability of the chunk |
| out `state` | `[2, 1, 128]` float32 | state for the next chunk |

The LSTM state is an explicit input/output pair, never a model variable, so the Java side owns it.
The `sr` input is gone (16 kHz only), which also drops the 8 kHz weights: the file is **1,247,416
bytes** (the upstream ONNX is 2.3 MB), sha256 `c2fd2657…deab751429`. Its ops are all builtin and all
version 1 (no Flex): ADD, CONCATENATION, CONV_2D, FULLY_CONNECTED, LOGISTIC, MIRROR_PAD, MUL, PAD,
RELU, RESHAPE, SPLIT, SQRT, STRIDED_SLICE, TANH. About 0.7 M multiply-adds per 32 ms chunk.

Tool versions (a Python 3.12 venv at `~/.cache/termux-launcher/silero-conv`, made with uv, because
the harness venv's Python 3.14 has no TensorFlow wheels): tensorflow-cpu 2.19.0, onnx 1.17.0,
onnxruntime 1.20.1, ai-edge-litert 1.4.0, numpy 2.1.3. The conversion is deterministic (two runs
gave the same sha256).

**Parity.** The script streams 16 clips (near/far/fan/tv, four voices each) plus 5 s of white noise
chunk by chunk through the ONNX model (onnxruntime) and the tflite (ai-edge-litert), carrying state
and context exactly as the app does: over 1,945 chunks the largest |p_onnx − p_tflite| is
**2.6e-6** (target < 1e-3), with no decision flips at the 0.5 onset or the 0.35 hold.

**App side** (`app/src/main/java/com/termux/app/terminal/inappkeyboard/voice/`):

- `SileroVad` reads the asset into a direct buffer on the capture thread and runs it on the classic
  `org.tensorflow.lite.Interpreter` (litert 1.4.2) with one thread and stock XNNPACK; the
  `TaiXnnpackDelegate` thread-pool shim buys nothing for a model this small. Buffers are allocated
  once, and tensors are matched by size rather than by index.
- `SileroVoiceDecider` (pure Java, tested with a fake probability source) re-chunks the detector's
  30 ms frames into 512-sample chunks with 64 samples of context, and gives frame *i* the
  probability of the chunk that has finished by the end of frame *i* (0 before the first), with
  onset 0.5 / hold 0.35: the alignment `vads.py`'s `SileroDecider` evaluated.
- `VoiceActivityDetector` takes the decider as an optional fifth constructor argument. Only the
  voiced decision changes; pre-roll, pause close, minimum voiced time, window cut and silence
  auto-stop are untouched, and frame RMS and the noise floor are still measured for the level meter
  and the quietest-frame cut. The energy decision's 8-frame (240 ms) floor warm-up does not apply on
  the Silero path, so a word spoken the moment the microphone opens is no longer clipped.
- Fallback: if the model cannot be opened (missing asset, LiteRT's native library not loading,
  unexpected tensors) the session uses the energy detector; if it throws mid-stream the rest of the
  session does. Either is logged once per process under the `SileroVad` tag. `VoiceInputSession`
  logs `voiced decision: silero|energy (N ms to load)` at every session start.
- `VoiceReplayRig` / `VoiceReplayCore` (host JVM) still use the energy decision, because LiteRT's
  native library does not run there. The PC harness remains the place to replay Silero.

Device checks owed: the load-time log line (tens of milliseconds expected), `voiced decision:
silero` appearing, the first word after the start tone not being clipped, pauses still closing
segments with a TV or fan on, and the per-chunk CPU cost (should be well under 1 ms).

## Round 3 (2026-09-28): quiet speech in an office, and "Mic sensitivity"

Device report (pong, round 3): speaking quietly in an office is sometimes not picked up.

**Every gate between the microphone and a phrase** (Silero path, `VoiceInputSession.capture`):

| gate | where | before | after |
|---|---|---|---|
| lead-in discard | `VoiceLeadInDiscard.START_TONE_MS` | first 180 ms after the mic opens dropped (with voice sounds on) | unchanged: it only ever touches the start tone |
| energy / level floor | `VoiceActivityDetector` `ABSOLUTE_FLOOR` −70 dBFS, onset 9 dB / hold 6 dB over the 10th-percentile floor | **not applied on the Silero path**; only the fallback decision uses it | unchanged (the fallback is not what the report is about) |
| Silero level | the raw frame, no gain | Silero saw pong's un-AGC'd −45…−60 dBFS speech as it came | a copy lifted so the noise floor sits at a target, never down, capped (`VoiceMicSensitivity.sileroGain`) |
| Silero probability | `SileroVoiceDecider.ONSET` / `HOLD` | 0.5 / 0.35 | unchanged |
| minimum voiced time | `MIN_VOICED_MS` | 300 ms | Normal 300 ms, High 420 ms |
| pause close | "Pause that ends a phrase" | 600 ms default | unchanged |
| segment gain | `VoiceGain` | peak to −6 dBFS, at most +40 dB, after the VAD | unchanged: a −60 dBFS peak still lands at −20 dBFS, well inside what Whisper and the runtime take |

**What discriminates.** Silero's probability depends on the absolute level. On the developer's own
debug captures from pong (`~/.cache/termux-launcher/voice-clips/seg*.wav`, before `VoiceGain`;
frame RMS p90 −45 to −52 dBFS over a −59 to −64 dBFS room), the share of chunks over 0.5 at the
clip's own level, 6 dB down and 10 dB down was, for seg7: 75 %, 29 %, 4 %; for seg2: 69 %, 58 %,
39 %; seg7 lifted +6 dB rose from 75 % to 87 %. Stationary noise does not
move with level the same way: pink, fan and keyboard clicks gave 0 % of chunks over 0.5 from −66 to
−36 dBFS, while babble went 6 % → 79 % → 99 % and a TV 23 % → 78 % → 89 % from −66 to −60 to
−54 dBFS. So the lever for quiet speech is level, not threshold, and its price is background talk.

**Probe** (`scripts/voice-eval/sensitivity_probe.py`, run 2026-09-28): speech is the 7 real phrases
at 0/−6/−10 dB over a −64 dBFS pink room (the room does not get quieter with the speaker), and 16
synthetic sentences plus 12 key words at a phrase RMS of −52/−58/−62 dBFS over the same room;
noise-only is 120 s each. Same Segmenter as the app (600 ms pause, 10 s window). Speech cells are
"missed clips / share of the phrase inside a segment"; noise cells are false phrases a minute.

```
speech: missed clips / voiced share of the phrase, by level
before: no lift, 0.50/0.35, 300 ms     key-52: 0/12 100%  key-58: 0/12 100%  key-62: 5/12  57%  real+0: 0/7  90%  real-10: 4/7  37%  real-6: 0/7  77%  syn-52: 0/16 100%  syn-58: 0/16  99%  syn-62: 3/16  71%
thresholds 0.40/0.25, 300 ms           key-52: 0/12 100%  key-58: 0/12 100%  key-62: 2/12  81%  real+0: 0/7  91%  real-10: 4/7  38%  real-6: 0/7  82%  syn-52: 0/16 100%  syn-58: 0/16  99%  syn-62: 2/16  80%
fixed +12 dB                           key-52: 0/12 100%  key-58: 0/12 100%  key-62: 0/12 100%  real+0: 0/7  91%  real-10: 0/7  88%  real-6: 0/7  91%  syn-52: 0/16 100%  syn-58: 0/16 100%  syn-62: 0/16 100%
Normal: floor->-63, max +6, 300 ms     key-52: 0/12 100%  key-58: 0/12 100%  key-62: 2/12  83%  real+0: 0/7  91%  real-10: 3/7  47%  real-6: 0/7  83%  syn-52: 0/16 100%  syn-58: 0/16 100%  syn-62: 1/16  91%
floor->-60, max +12, 300 ms            key-52: 0/12 100%  key-58: 0/12 100%  key-62: 1/12  91%  real+0: 0/7  91%  real-10: 0/7  73%  real-6: 0/7  90%  syn-52: 0/16 100%  syn-58: 0/16 100%  syn-62: 0/16  96%
floor->-58, max +12, 360 ms            key-52: 0/12 100%  key-58: 0/12 100%  key-62: 1/12  91%  real+0: 0/7  91%  real-10: 0/7  81%  real-6: 0/7  90%  syn-52: 0/16 100%  syn-58: 0/16 100%  syn-62: 0/16  99%
High: floor->-56, max +12, 420 ms      key-52: 0/12 100%  key-58: 0/12 100%  key-62: 0/12 100%  real+0: 0/7  91%  real-10: 0/7  87%  real-6: 0/7  90%  syn-52: 0/16 100%  syn-58: 0/16 100%  syn-62: 0/16  99%
floor->-52, max +18, 450 ms            key-52: 0/12 100%  key-58: 0/12 100%  key-62: 0/12 100%  real+0: 0/7  91%  real-10: 0/7  89%  real-6: 0/7  91%  syn-52: 0/16 100%  syn-58: 0/16 100%  syn-62: 0/16 100%

noise only: false segments per minute
before: no lift, 0.50/0.35, 300 ms     room-64: 0.0  bab-70: 0.0  bab-66: 0.0  bab-62: 0.0  tv-70: 0.0  tv-66: 0.0  fan-58: 0.0  clicks: 0.0
thresholds 0.40/0.25, 300 ms           room-64: 0.0  bab-70: 0.0  bab-66: 0.0  bab-62: 0.0  tv-70: 0.0  tv-66: 0.0  fan-58: 0.0  clicks: 0.0
fixed +12 dB                           room-64: 0.0  bab-70: 0.0  bab-66: 1.0  bab-62: 7.0  tv-70: 12.0  tv-66: 8.5  fan-58: 0.0  clicks: 0.0
Normal: floor->-63, max +6, 300 ms     room-64: 0.0  bab-70: 0.0  bab-66: 0.0  bab-62: 0.0  tv-70: 0.0  tv-66: 0.5  fan-58: 0.0  clicks: 0.0
floor->-60, max +12, 300 ms            room-64: 0.0  bab-70: 0.0  bab-66: 0.0  bab-62: 0.0  tv-70: 0.0  tv-66: 14.5  fan-58: 0.0  clicks: 0.0
floor->-58, max +12, 360 ms            room-64: 0.0  bab-70: 0.0  bab-66: 0.5  bab-62: 6.5  tv-70: 1.5  tv-66: 12.0  fan-58: 0.0  clicks: 0.0
High: floor->-56, max +12, 420 ms      room-64: 0.0  bab-70: 0.0  bab-66: 0.5  bab-62: 8.0  tv-70: 4.0  tv-66: 11.0  fan-58: 0.0  clicks: 0.0
floor->-52, max +18, 450 ms            room-64: 0.0  bab-70: 0.0  bab-66: 0.5  bab-62: 7.5  tv-70: 12.5  tv-66: 8.0  fan-58: 0.0  clicks: 0.0
```

**Decision.**

- **Normal (default): lift the floor to −63 dBFS, at most +6 dB; 300 ms voiced.** On pong's −60 to
  −65 dBFS room that is 0 to +2 dB, up to +6 dB in a very quiet room. Quiet speech gets through more
  often (at −62 dBFS: 1/16 sentences missed rather than 3, 2/12 key words rather than 5; the real
  phrases 10 dB down: 3/7 rather than 4/7), and the only false phrases in 16 minutes of noise were
  0.5 a minute with a TV at −66 dBFS. The stronger lifts tried for the default (floor to −60 or
  −58 dBFS, up to +12 dB) let a TV in at 12–14.5 a minute.
- **High: lift the floor to −56 dBFS, at most +12 dB; 420 ms voiced.** Nothing quiet was missed (the
  real phrases 10 dB down: 0/7, 87 % of their audio kept, against 37 % before), at the cost of
  4–11 false phrases a minute from a TV at −70 to −66 dBFS and 8 from babble at −62. The longer
  minimum (300 → 420 ms) is there so a short burst of someone else's speech is less likely to open
  a phrase; in the probe it cost no key word at −62 dBFS. Stronger still (−52 dBFS, +18 dB) kept a
  further 2 % of phrase audio and tripled the false phrases from a TV at −70 dBFS.
- **Thresholds stay 0.5 / 0.35.** Lowering them to 0.4 / 0.25 saved key words (5 → 2 missed at
  −62) but none of the quiet real phrases (4/7 missed 10 dB down, as before): their probabilities
  sit far under any threshold. They were not re-run over the full 1,024-variant grid above, so they
  are left as that grid chose them.
- **No single default serves both** an office where the user speaks softly and one where colleagues
  talk: the gain that finds the soft voice also finds theirs. Hence the setting, Keyboard → Voice
  input → **Mic sensitivity** (`keyboard_voice_mic_sensitivity`, `normal` | `high`).

The lift touches only Silero's copy of the frame (`SileroVoiceDecider.decide(frame, inSpeech,
gain)`); the segment, the level meter and the energy fallback see the audio as captured. The harness
`vads.py` `SileroDecider` still feeds Silero the raw audio, i.e. the "before" row; the probe's
`GainedSilero` is the app's current decision.

Device checks owed: round 3, check 4 in `device-checks-pending-2026-09-27.md`.

---

# Voice VAD × STT evaluation

1024 speech variants, 16 noise-only variants. VAD settings: pause 600 ms, window 10 s, Silero onset/hold 0.5/0.35. Synthetic speech (Piper): compare models against each other, not against real-voice accuracy. RTF is this PC's, not the phone's.

## Verdict (Silero vs energy, all noise levels pooled)

- Silero reduces clipped onsets (share of speech segments) by 72% (energy 5.96% → Silero 1.67%; tangible, needs ≥10% and ≥2% absolute).
- Silero reduces mid-word cuts (per clip) by 84% (energy 0.08 → Silero 0.01; tangible, needs ≥10% and ≥0.05 absolute).
- Silero reduces dropped words (share of words) by 93% (energy 6.57% → Silero 0.46%; tangible, needs ≥10% and ≥1% absolute).
- Silero reduces missed clips (no segment at all, share) by 89% (energy 10.84% → Silero 1.17%; tangible, needs ≥10% and ≥1% absolute).
- Silero increases false segments on noise-only audio (per minute) by 0% (energy 3.75/min → Silero 3.75/min; not tangible, needs ≥10% and ≥0.5/min absolute).
- Silero reduces spurious segments in speech clips (per clip) by 71% (energy 0.03 → Silero 0.01; not tangible, needs ≥10% and ≥0.05 absolute).
- Silero increases WER with parakeet by 3% (energy 52.03% → Silero 53.49%; not tangible, needs ≥10% and ≥1% absolute).
- Silero reduces hallucinated outputs with parakeet (count) by 54% (energy 35.00 → Silero 16.00; tangible, needs ≥10% and ≥3 absolute).
- Silero increases WER with whisper-small.en by 0% (energy 36.92% → Silero 37.08%; not tangible, needs ≥10% and ≥1% absolute).
- Silero reduces hallucinated outputs with whisper-small.en (count) by 40% (energy 47.00 → Silero 28.00; tangible, needs ≥10% and ≥3 absolute).

## 1. VAD behaviour

| VAD | noise | clips | clipped onsets | clipped ms (mean) | onset lag ms | mid-word cuts | dropped words | missed | segs / phrases | over / under-split | spurious | false segs/min (noise-only) |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| energy | clean | 256 | 3.5% | 181 | 22 | 13 | 24/1472 | 11 | 254/256 | 7/11 | 5 | 3.75 |
| hybrid | clean | 256 | 8.7% | 170 | 123 | 21 | 32/1472 | 18 | 242/256 | 4/18 | 10 | 1.50 |
| silero | clean | 256 | 6.9% | 189 | 122 | 14 | 27/1472 | 11 | 245/256 | 0/11 | 4 | 1.50 |
| energy | babble@10dB | 64 | 1.5% | 99 | -578 | 2 | 0/368 | 0 | 65/64 | 1/0 | 1 | 3.00 |
| hybrid | babble@10dB | 64 | 1.5% | 40 | -952 | 2 | 0/368 | 0 | 65/64 | 1/0 | 0 | 9.00 |
| silero | babble@10dB | 64 | 0.0% | – | -952 | 0 | 0/368 | 0 | 65/64 | 1/0 | 0 | 9.00 |
| energy | babble@5dB | 64 | 6.1% | 267 | -600 | 5 | 5/368 | 2 | 66/64 | 3/2 | 1 | 3.00 |
| hybrid | babble@5dB | 64 | 0.0% | – | -950 | 0 | 0/368 | 0 | 65/64 | 1/0 | 1 | 9.00 |
| silero | babble@5dB | 64 | 0.0% | – | -950 | 0 | 0/368 | 0 | 65/64 | 1/0 | 0 | 9.00 |
| energy | babble@0dB | 64 | 10.2% | 216 | -571 | 12 | 77/368 | 10 | 59/64 | 4/10 | 3 | 3.00 |
| hybrid | babble@0dB | 64 | 0.0% | – | -967 | 0 | 0/368 | 0 | 64/64 | 0/0 | 1 | 9.00 |
| silero | babble@0dB | 64 | 0.0% | – | -967 | 0 | 0/368 | 0 | 64/64 | 0/0 | 1 | 9.00 |
| energy | fan@10dB | 64 | 0.0% | – | -37 | 0 | 0/368 | 1 | 63/64 | 0/1 | 0 | 0.00 |
| hybrid | fan@10dB | 64 | 1.6% | 99 | 42 | 2 | 1/368 | 2 | 64/64 | 2/2 | 0 | 0.00 |
| silero | fan@10dB | 64 | 0.0% | – | 44 | 0 | 0/368 | 0 | 64/64 | 0/0 | 0 | 0.00 |
| energy | fan@5dB | 64 | 4.7% | 333 | -3 | 4 | 5/368 | 3 | 64/64 | 3/3 | 0 | 0.00 |
| hybrid | fan@5dB | 64 | 1.5% | 100 | 52 | 2 | 0/368 | 0 | 65/64 | 1/0 | 0 | 0.00 |
| silero | fan@5dB | 64 | 0.0% | – | 52 | 0 | 0/368 | 0 | 64/64 | 0/0 | 0 | 0.00 |
| energy | fan@0dB | 64 | 12.2% | 261 | 113 | 11 | 70/368 | 18 | 49/64 | 2/18 | 0 | 3.00 |
| hybrid | fan@0dB | 64 | 1.6% | 130 | 70 | 2 | 0/368 | 1 | 64/64 | 1/1 | 0 | 0.00 |
| silero | fan@0dB | 64 | 0.0% | – | 70 | 0 | 0/368 | 1 | 63/64 | 0/1 | 0 | 0.00 |
| energy | tv@10dB | 64 | 0.0% | – | -780 | 0 | 0/368 | 0 | 64/64 | 0/0 | 5 | 12.00 |
| hybrid | tv@10dB | 64 | 1.5% | 40 | -879 | 2 | 0/368 | 0 | 65/64 | 1/0 | 2 | 12.00 |
| silero | tv@10dB | 64 | 0.0% | – | -879 | 0 | 0/368 | 0 | 64/64 | 0/0 | 2 | 9.00 |
| energy | tv@5dB | 64 | 0.0% | – | -822 | 0 | 0/368 | 0 | 65/64 | 1/0 | 5 | 12.00 |
| hybrid | tv@5dB | 64 | 0.0% | – | -884 | 0 | 0/368 | 0 | 65/64 | 1/0 | 16 | 9.00 |
| silero | tv@5dB | 64 | 0.0% | – | -928 | 0 | 0/368 | 0 | 64/64 | 0/0 | 1 | 9.00 |
| energy | tv@0dB | 64 | 4.2% | 115 | -813 | 4 | 1/368 | 0 | 72/64 | 7/0 | 8 | 9.00 |
| hybrid | tv@0dB | 64 | 0.0% | – | -912 | 0 | 0/368 | 0 | 66/64 | 2/0 | 8 | 9.00 |
| silero | tv@0dB | 64 | 0.0% | – | -942 | 0 | 0/368 | 0 | 65/64 | 1/0 | 0 | 9.00 |
| energy | white@10dB | 64 | 5.0% | 293 | 48 | 5 | 2/368 | 7 | 60/64 | 3/7 | 0 | 0.00 |
| hybrid | white@10dB | 64 | 1.6% | 40 | 39 | 1 | 1/368 | 1 | 64/64 | 1/1 | 0 | 0.00 |
| silero | white@10dB | 64 | 0.0% | – | 40 | 0 | 0/368 | 0 | 64/64 | 0/0 | 0 | 0.00 |
| energy | white@5dB | 64 | 19.1% | 212 | 148 | 12 | 52/368 | 22 | 47/64 | 3/22 | 0 | 0.00 |
| hybrid | white@5dB | 64 | 1.5% | 130 | 45 | 2 | 0/368 | 0 | 65/64 | 1/0 | 0 | 0.00 |
| silero | white@5dB | 64 | 0.0% | – | 45 | 0 | 0/368 | 0 | 64/64 | 0/0 | 0 | 0.00 |
| energy | white@0dB | 64 | 44.8% | 180 | 445 | 18 | 151/368 | 37 | 29/64 | 2/37 | 0 | 0.00 |
| hybrid | white@0dB | 64 | 0.0% | – | 61 | 0 | 0/368 | 0 | 64/64 | 0/0 | 0 | 0.00 |
| silero | white@0dB | 64 | 0.0% | – | 61 | 0 | 0/368 | 0 | 64/64 | 0/0 | 0 | 0.00 |

## 2. VAD × STT × noise

| VAD | STT | noise | WER | commands | hallucinations (stock) | RTF (PC) |
|---|---|---|---:|---:|---:|---:|
| energy | parakeet | clean | 33.4% | 42/160 | 7 (0) | 0.230 |
| hybrid | parakeet | clean | 38.3% | 32/160 | 7 (1) | 0.218 |
| silero | parakeet | clean | 38.8% | 38/160 | 3 (1) | 0.213 |
| energy | whisper-small.en | clean | 33.0% | 50/160 | 10 (0) | 0.755 |
| hybrid | whisper-small.en | clean | 33.4% | 52/160 | 12 (0) | 0.716 |
| silero | whisper-small.en | clean | 32.0% | 55/160 | 6 (0) | 0.667 |
| energy | parakeet | babble@10dB | 28.7% | 9/40 | 1 (0) | 0.222 |
| hybrid | parakeet | babble@10dB | 31.9% | 10/40 | 1 (0) | 0.217 |
| silero | parakeet | babble@10dB | 49.7% | 7/40 | 1 (0) | 0.191 |
| energy | whisper-small.en | babble@10dB | 22.3% | 12/40 | 2 (0) | 0.606 |
| hybrid | whisper-small.en | babble@10dB | 20.7% | 11/40 | 3 (0) | 0.538 |
| silero | whisper-small.en | babble@10dB | 27.4% | 12/40 | 3 (0) | 0.408 |
| energy | parakeet | babble@5dB | 46.8% | 3/40 | 0 (0) | 0.252 |
| hybrid | parakeet | babble@5dB | 58.5% | 1/40 | 0 (0) | 0.213 |
| silero | parakeet | babble@5dB | 60.6% | 1/40 | 0 (0) | 0.208 |
| energy | whisper-small.en | babble@5dB | 27.4% | 10/40 | 2 (0) | 0.686 |
| hybrid | whisper-small.en | babble@5dB | 44.9% | 15/40 | 4 (0) | 0.423 |
| silero | whisper-small.en | babble@5dB | 42.8% | 15/40 | 3 (0) | 0.419 |
| energy | parakeet | babble@0dB | 92.0% | 0/40 | 3 (0) | 0.281 |
| hybrid | parakeet | babble@0dB | 93.6% | 0/40 | 0 (0) | 0.213 |
| silero | parakeet | babble@0dB | 93.6% | 0/40 | 0 (0) | 0.213 |
| energy | whisper-small.en | babble@0dB | 95.2% | 0/40 | 4 (0) | 0.753 |
| hybrid | whisper-small.en | babble@0dB | 134.3% | 0/40 | 4 (0) | 0.533 |
| silero | whisper-small.en | babble@0dB | 134.3% | 0/40 | 4 (0) | 0.533 |
| energy | parakeet | fan@10dB | 10.1% | 23/40 | 0 (0) | 0.215 |
| hybrid | parakeet | fan@10dB | 12.2% | 19/40 | 0 (0) | 0.237 |
| silero | parakeet | fan@10dB | 11.7% | 19/40 | 0 (0) | 0.225 |
| energy | whisper-small.en | fan@10dB | 13.3% | 17/40 | 0 (0) | 0.697 |
| hybrid | whisper-small.en | fan@10dB | 13.0% | 18/40 | 0 (0) | 0.804 |
| silero | whisper-small.en | fan@10dB | 13.0% | 18/40 | 0 (0) | 0.728 |
| energy | parakeet | fan@5dB | 18.9% | 12/40 | 0 (0) | 0.226 |
| hybrid | parakeet | fan@5dB | 15.2% | 16/40 | 0 (0) | 0.230 |
| silero | parakeet | fan@5dB | 15.4% | 16/40 | 0 (0) | 0.229 |
| energy | whisper-small.en | fan@5dB | 15.2% | 17/40 | 0 (0) | 0.779 |
| hybrid | whisper-small.en | fan@5dB | 16.8% | 14/40 | 0 (0) | 0.787 |
| silero | whisper-small.en | fan@5dB | 16.0% | 14/40 | 0 (0) | 0.784 |
| energy | parakeet | fan@0dB | 39.1% | 4/40 | 0 (0) | 0.241 |
| hybrid | parakeet | fan@0dB | 23.7% | 6/40 | 0 (0) | 0.246 |
| silero | parakeet | fan@0dB | 23.9% | 6/40 | 0 (0) | 0.246 |
| energy | whisper-small.en | fan@0dB | 35.6% | 6/40 | 1 (0) | 0.799 |
| hybrid | whisper-small.en | fan@0dB | 24.7% | 10/40 | 0 (0) | 0.795 |
| silero | whisper-small.en | fan@0dB | 23.4% | 10/40 | 0 (0) | 0.791 |
| energy | parakeet | tv@10dB | 108.2% | 1/40 | 7 (0) | 0.174 |
| hybrid | parakeet | tv@10dB | 81.9% | 2/40 | 5 (0) | 0.180 |
| silero | parakeet | tv@10dB | 120.7% | 0/40 | 5 (0) | 0.168 |
| energy | whisper-small.en | tv@10dB | 21.0% | 24/40 | 9 (0) | 0.508 |
| hybrid | whisper-small.en | tv@10dB | 18.6% | 24/40 | 6 (0) | 0.570 |
| silero | whisper-small.en | tv@10dB | 21.0% | 25/40 | 5 (0) | 0.470 |
| energy | parakeet | tv@5dB | 115.7% | 0/40 | 8 (1) | 0.175 |
| hybrid | parakeet | tv@5dB | 119.1% | 0/40 | 17 (1) | 0.187 |
| silero | parakeet | tv@5dB | 121.8% | 0/40 | 4 (0) | 0.163 |
| energy | whisper-small.en | tv@5dB | 36.7% | 12/40 | 8 (0) | 0.486 |
| hybrid | whisper-small.en | tv@5dB | 46.0% | 8/40 | 19 (1) | 0.532 |
| silero | whisper-small.en | tv@5dB | 35.1% | 15/40 | 4 (0) | 0.462 |
| energy | parakeet | tv@0dB | 138.3% | 0/40 | 9 (0) | 0.182 |
| hybrid | parakeet | tv@0dB | 147.1% | 0/40 | 11 (0) | 0.175 |
| silero | parakeet | tv@0dB | 146.3% | 0/40 | 3 (0) | 0.169 |
| energy | whisper-small.en | tv@0dB | 92.3% | 9/40 | 11 (0) | 0.646 |
| hybrid | whisper-small.en | tv@0dB | 91.2% | 9/40 | 11 (0) | 0.623 |
| silero | whisper-small.en | tv@0dB | 87.0% | 9/40 | 3 (0) | 0.599 |
| energy | parakeet | white@10dB | 14.1% | 17/40 | 0 (0) | 0.222 |
| hybrid | parakeet | white@10dB | 13.6% | 19/40 | 0 (0) | 0.241 |
| silero | parakeet | white@10dB | 12.2% | 21/40 | 0 (0) | 0.224 |
| energy | whisper-small.en | white@10dB | 15.4% | 15/40 | 0 (0) | 0.766 |
| hybrid | whisper-small.en | white@10dB | 15.4% | 15/40 | 0 (0) | 0.694 |
| silero | whisper-small.en | white@10dB | 14.6% | 15/40 | 0 (0) | 0.656 |
| energy | parakeet | white@5dB | 29.3% | 5/40 | 0 (0) | 0.234 |
| hybrid | parakeet | white@5dB | 16.8% | 10/40 | 0 (0) | 0.226 |
| silero | parakeet | white@5dB | 17.0% | 11/40 | 0 (0) | 0.224 |
| energy | whisper-small.en | white@5dB | 29.0% | 4/40 | 0 (0) | 0.907 |
| hybrid | whisper-small.en | white@5dB | 22.9% | 9/40 | 0 (0) | 0.696 |
| silero | whisper-small.en | white@5dB | 22.1% | 9/40 | 0 (0) | 0.691 |
| energy | parakeet | white@0dB | 57.7% | 0/40 | 0 (0) | 0.243 |
| hybrid | parakeet | white@0dB | 27.7% | 5/40 | 0 (0) | 0.237 |
| silero | parakeet | white@0dB | 27.7% | 5/40 | 0 (0) | 0.237 |
| energy | whisper-small.en | white@0dB | 55.1% | 0/40 | 0 (0) | 0.928 |
| hybrid | whisper-small.en | white@0dB | 28.7% | 7/40 | 0 (0) | 0.764 |
| silero | whisper-small.en | white@0dB | 28.7% | 7/40 | 0 (0) | 0.764 |

## 3. Clean audio by room condition (WER)

| VAD | STT | fan | far | near | tv |
|---|---|---:|---:|---:|---:|
| energy | parakeet | 41.2% | 21.5% | 9.0% | 61.7% |
| hybrid | parakeet | 37.2% | 19.9% | 10.6% | 85.4% |
| silero | parakeet | 34.3% | 18.9% | 9.3% | 92.6% |
| energy | whisper-small.en | 42.8% | 19.1% | 9.3% | 60.9% |
| hybrid | whisper-small.en | 38.0% | 19.1% | 9.8% | 66.8% |
| silero | whisper-small.en | 38.3% | 19.9% | 8.5% | 61.2% |

## Most frequent hallucinated outputs

| STT | text | count |
|---|---|---:|
| parakeet | The transcript breaks into pieces. | 4 |
| whisper-small.en | *Crowd talking* | 4 |
| whisper-small.en | What is the | 3 |
| parakeet | Yes. | 3 |
| whisper-small.en | Remind me | 3 |
| parakeet | Control C | 3 |
| whisper-small.en | Alas! | 3 |
| parakeet | Pseudo Act Update. Clear. What is the weather like in Kuwait | 3 |
| whisper-small.en | Pseudo app update Clear! What is the weather like in Kuwait  | 3 |
| parakeet | PKG install Python. What is the weather like in Kuwait City  | 3 |
| whisper-small.en | PKG install python What's the weather like in Kuwait city to | 3 |
| whisper-small.en | The transcript breaks into pieces. | 3 |
| whisper-small.en | We look at the new season of | 3 |
| parakeet | And yeah. | 3 |
| whisper-small.en | And now, the rest of the | 3 |
| whisper-small.en | Backspace | 2 |
| parakeet | Yeah. | 2 |
| whisper-small.en | QW | 2 |
| parakeet | Remind me. | 2 |
| whisper-small.en | For more information on the | 2 |
