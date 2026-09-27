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
