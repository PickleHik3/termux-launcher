# voice-eval: energy VAD vs Silero VAD, and Parakeet vs Whisper vs moonshine

A PC-side harness for two questions about voice dictation in the in-app keyboard:

1. Does a neural VAD (Silero v5) give tangible benefits over the app's energy VAD
   (`VoiceActivityDetector.java`): fewer clipped word onsets, fewer false segments on noise,
   fewer words cut mid-word, better phrase segmentation, lower WER downstream?
2. How do Parakeet TDT v3, Whisper small.en and moonshine-tiny compare on the same segments
   (WER, hallucinations on noise, real-time factor)?

Nothing here touches the app or Gradle. No audio or model is ever written into the repo: clip
variants (noise mixes) are rebuilt in memory from their ids, and everything the harness writes
goes to `scripts/voice-eval/out/` (git-ignored).

## Files

| file | what it does |
|---|---|
| `vads.py` | Line-by-line Python port of `VoiceActivityDetector.java` (constants and logic cited by Java line number) and the Silero v5 ONNX decider. Both share the *same* segment state machine (300 ms pre-roll and tail, 600 ms pause close, 300 ms minimum voiced, 10 s window cut at the quietest frame), so only the voiced/unvoiced decision differs. |
| `common.py` | Clip set loading (the `voice_eval_make.py` manifest, optional real clips), noise synthesis and mixing at an SNR, `VoiceGain` port. |
| `align.py` | Ground-truth timing: speech extent and natural phrases from the clean near clip's energy, word boundaries from Parakeet TDT's token timestamps. Cached in `out/alignments.json`. |
| `vad_compare.py` | Runs each VAD over every variant and writes `out/segments.jsonl` with per-clip metrics. |
| `stt_engines.py` | Parakeet and Whisper (the rig's own Python runtimes, imported from `scripts/`), moonshine-tiny (ONNX). |
| `stt_compare.py` | Cuts each variant by each VAD's segments, applies VoiceGain, transcribes with each STT, scores WER with `voice_eval_score.wer`, counts hallucinations and measures RTF. `out/stt_results.jsonl`, transcript cache in `out/stt_cache/`. |
| `sensitivity_probe.py` | Quiet speech against office noise on the Silero path: the developer's real clips (6 and 10 dB down) and synthetic phrases at -52/-58/-62 dBFS, then 120 s each of room, babble, TV, fan and keyboard clicks, through the same Segmenter with Silero shown a floor-referenced lift of each frame (the app's `VoiceMicSensitivity`). Prints missed phrases and false phrases a minute; writes nothing. The numbers behind the "Mic sensitivity" setting. |
| `convert_silero.py` | Builds the app's bundled `app/src/main/assets/vad/silero_vad_v5.tflite` from `silero_vad.onnx` (hand rebuild in TensorFlow, builtin ops only) and checks it against the ONNX model chunk by chunk. Needs its own Python 3.12 venv with TensorFlow; the pinned versions are in its docstring. |
| `report.py` | `out/report.md`: VAD table, VAD × STT × noise table, clean WER per room condition, top hallucinated strings, automatic verdict. |

## Setup (once)

Everything goes into the existing venv, `~/.cache/termux-launcher/venv` (Python 3.14; it already
has numpy, scipy, onnxruntime, ai-edge-litert, piper-tts).

```sh
V=~/.cache/termux-launcher/venv/bin

# Moonshine's tokenizer (the only new package; onnxruntime is already there)
$V/pip install tokenizers

# Silero VAD v5 (2.3 MB ONNX; inputs input/state/sr)
mkdir -p ~/.cache/termux-launcher/silero
curl -L -o ~/.cache/termux-launcher/silero/silero_vad.onnx \
  https://github.com/snakers4/silero-vad/raw/v5.1.2/src/silero_vad/data/silero_vad.onnx

# moonshine-tiny, ONNX export of UsefulSensors/moonshine-tiny (~110 MB float32)
M=~/.cache/termux-launcher/moonshine/tiny
mkdir -p $M/onnx
for f in config.json generation_config.json tokenizer.json; do
  curl -L -o $M/$f https://huggingface.co/onnx-community/moonshine-tiny-ONNX/resolve/main/$f
done
for f in encoder_model.onnx decoder_model_merged.onnx; do
  curl -L -o $M/onnx/$f https://huggingface.co/onnx-community/moonshine-tiny-ONNX/resolve/main/onnx/$f
done
```

Already in place from the replay rig:

- `~/.cache/termux-launcher/parakeet/parakeet_tdt_0.6b_v3_5s_i8_stateful.tflite` + `tokenizer.json`
- `~/.cache/termux-launcher/whisper/whisper-acft-small-en/acft_whisper_small.en_10s_drq.tflite` + `tokenizer.json`
- the synthetic set `~/.cache/termux-launcher/voice-eval/` (`scripts/voice_eval_make.py`); if it is
  missing: `$V/python scripts/voice_eval_make.py`

Optional moonshine alternative, if the ONNX export gives trouble: `$V/pip install transformers torch`
and pass `--moonshine-backend transformers` (downloads `UsefulSensors/moonshine-tiny` from Hugging
Face on first use). The app itself would run litert-community's `moonshine_tiny_5s_f32.tflite`
(fixed 5 s windows); the ONNX graph is the same weights at any length.

## Run

From the repo root:

```sh
V=~/.cache/termux-launcher/venv/bin
cd scripts/voice-eval

# 1. VADs (aligns first: ~1 min of Parakeet over the 64 near clips, then ~3-5 min)
$V/python vad_compare.py

# 2. STTs over both VADs' segments (the long step, see timings below)
$V/python stt_compare.py

# 3. The report -> out/report.md
$V/python report.py
```

Defaults: all 256 synthetic clips clean (near / far / fan / tv), plus the 64 *near* clips mixed
with TV, babble, fan and white noise at 10, 5 and 0 dB SNR (768 more), plus 16 noise-only
variants of 20 s each (each condition's room between phrases, alone and with each noise × SNR).
`vad_compare.py` and `stt_compare.py` must be given the same clip flags.

Useful flags (both scripts):

| flag | meaning |
|---|---|
| `--kinds dictation` | only the dictation sentences (skip keys and typed commands) |
| `--voices lessac,alan` | a subset of the four Piper voices |
| `--conditions near,far` | a subset of the room conditions |
| `--noises tv,babble --snrs 5,0` | a subset of the noise grid; `--noises ''` for none |
| `--augment near,far` | which conditions get the noise grid (default `near`) |
| `--real ~/.cache/termux-launcher/voice-clips` | also run the developer's real clips (truth from `<stem>.txt` next to each WAV, if present; WER only for those) |
| `--vads energy,silero,oracle` | `oracle` = each natural phrase ±300 ms from the truth timing: the WER upper bound |
| `--silero-onset 0.5 --silero-hold 0.35` | Silero hysteresis (vad_compare only) |
| `--pause-ms 600 --window-s 10` | segmenter settings (vad_compare only; the app's defaults) |
| `--stt parakeet,moonshine-tiny` | a subset of the STTs (stt_compare only); `whisper-base.en` also works |
| `--threads 4` | CPU threads per model (stt_compare only) |

Quick smoke run (~3-5 minutes):

```sh
$V/python vad_compare.py --kinds dictation --voices lessac --snrs 5 --out out/quick
$V/python stt_compare.py --kinds dictation --voices lessac --snrs 5 --out out/quick
$V/python report.py --out out/quick
```

## Expected runtime (a 12-core desktop, 4 threads per model)

| step | estimate |
|---|---|
| alignment (once, cached) | ~1 min |
| `vad_compare.py`, defaults | 3-5 min (Python frame loop for the energy port, ~30 ms of Silero per clip) |
| `stt_compare.py`, Parakeet | ~10 min (~2 000 segments, ~0.3 s each) |
| `stt_compare.py`, Whisper small.en | 40-60 min (10 s window encode ~1 s + greedy decode) |
| `stt_compare.py`, moonshine-tiny | ~3-5 min |
| `report.py` | seconds |

Transcripts are cached by segment audio hash, so re-running after a report tweak, or adding a
VAD whose segments match an earlier one, costs nothing for segments already seen. Delete
`out/stt_cache/` after changing an STT engine.

## What the metrics mean

Full definitions are in the docstrings of `vad_compare.py` and `stt_compare.py`. In short:

- **clipped onsets**: a segment that starts (pre-roll included) after its first word began. The
  STT never hears that word's start.
- **onset lag**: how late the first voiced frame comes after the first word; the 300 ms pre-roll
  hides up to 300 ms of it.
- **mid-word cuts**: segment starts/ends falling inside a word.
- **dropped words**: words less than half covered by any segment.
- **segments / phrases**: segments sent vs natural phrases (speech runs separated by ≥ the pause);
  over-split and under-split clips counted separately.
- **spurious**: segments on a speech clip that overlap no target speech.
- **false segments/min**: segments on noise-only audio.
- **WER**: word error rate of all of a clip's segment texts joined, with `voice_eval_score.py`'s
  normalisation. Clipped onsets and dropped words show up as deletions, hallucinations as insertions.
- **hallucinations**: non-empty text from noise-only or spurious segments; "stock" = known phrases
  such as "Thank you.", "[Music]", "ok".
- **RTF**: STT seconds per second of audio sent, on this PC. Not the phone's number: pong's
  figures are in `project-docs/reference/voice-ai/parakeet-stt-research.md`.

The verdict compares Silero against energy pooled over every noise level, as "Silero reduces X by
Y%", and calls a change tangible only at ≥ 10 % relative *and* a stated absolute minimum.

## Caveats

- Speech is synthetic (Piper); compare VADs and models against each other, not with real-voice accuracy.
- Word timings come from Parakeet aligning its own transcript (±80 ms inside a phrase; phrase
  start/end are energy-exact), with 40 ms slack in every timing metric.
- Whisper runs the plain dictation prompt; the app's terminal-bias prompt needs the Java tokenizer
  and only exists in `VoiceReplayRig`.
- The Python energy VAD is a port; if `VoiceActivityDetector.java` changes, update `vads.py` (every
  line cites the Java line it mirrors, as of dev `cecc2d31`).
- Silero decisions come from 32 ms chunks mapped onto the app's 30 ms frames, each frame reading
  the last chunk that has finished (causal, as it would run live).
