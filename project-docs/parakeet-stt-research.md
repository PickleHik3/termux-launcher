# Parakeet for TAI keyboard voice input: research note

Assessment date: **2026-09-25**. Local checkout: branch `dev`, **bf2a7f03**. This is desk research. No code was changed, nothing was built, and no device was touched.

**Question.** Should TAI add an NVIDIA Parakeet model as a `speech_to_text` catalog entry next to Whisper ACFT base/small `.en`? The target use is keyboard voice input: short terminal commands ("ls", "enter key") and dictated sentences up to about 10 s, often far-field with a TV or fan running. Today's STT runs on LiteRT 1.4.2 `Interpreter` with XNNPACK, 4 threads, on pong (Snapdragon 8+ Gen 1, 12 GB).

**Labels used throughout.**
- **[source]**: a number or claim stated by the source that owns it (model card, repo file, docs, leaderboard CSV).
- **[3rd-party]**: measured by an unofficial but primary GitHub/HF publisher. The raw data is linked.
- **[inferred]**: my own reasoning or arithmetic. Nobody has measured it.
- **[ours]**: from this repo's `project-docs/plans/whisper-voice-input.md`.

---

## Summary and recommendation

**Yes, but as a measured experiment.** Add exactly one model first: **`nvidia/parakeet-tdt-0.6b-v3`, using Google's pre-converted LiteRT file `litert-community/parakeet-tdt-0.6b-v3` → `parakeet_tdt_0.6b_v3_5s_i8_stateful.tflite` (614 MB)**. Run it through the existing TFLite `Interpreter` path. Do not add sherpa-onnx or onnxruntime at this stage.

Why this variant and this path:

1. **Accuracy is clearly better than what we ship.** On the Open ASR Leaderboard (English short-form, same 7 test sets, 2026-05-20 snapshot), average WER is:

   | Model | Avg WER | AMI |
   |---|---:|---:|
   | parakeet-tdt-0.6b-v3 | 6.82 | 11.39 |
   | parakeet-tdt-0.6b-v2 | 6.44 | 11.16 |
   | whisper-small.en | 9.24 | 17.93 |
   | whisper-base.en | 11.10 | 21.13 |

   AMI (meetings) is the hardest set. Parakeet is also ahead of whisper-large-v3 (7.95) [source, §1.3].
2. **It fits our runtime almost unchanged.** The LiteRT file has the same layout as our Whisper graphs: named `encode`/`decode` signatures (plus `decode_1`), a fixed window (5 s), int8 dynamic-range weights, and a `tokenizer.json` sidecar. Google's own Android sample runs exactly this file with a ~170-line TDT greedy decoder [source, §2.1]. No new native library means **+0 bytes of APK** [inferred]. sherpa-onnx would add about **24 MB of arm64 `.so`** [source, measured from the release, §2.2].
3. **Decoding is cheap where Whisper is expensive.** Our cost is dominated by Whisper small's decode: ~80 ms per token on pong [ours]. TDT decoding is an LSTM step plus a joint layer. On a desktop-class/GPU measurement the stateful `decode_1` step is ~8 ms [source/3rd-party, §3]. By FLOP count, a 5 s Parakeet encode is about the same work as a 10 s Whisper small encode (~170 ms on pong) [inferred]. Expected result: dictation faster than small.en, and commands about as fast as small.en but slower than base.en [inferred, §3].
4. **Licence is fine.** v2, v3 and 110M are all **CC-BY-4.0**, "ready for commercial/non-commercial use". This needs attribution in the app's licences screen [source, §1.2].

**v3 rather than v2 or 110M.** v2 is marginally better on English, but only v3 has a published LiteRT conversion. Converting v2 ourselves is a small but real conversion job. 110M (`parakeet-tdt_ctc-110m`) is about 5× cheaper and still beats whisper-small.en on the leaderboard (7.98 vs 9.24). It only exists for ONNX/sherpa-onnx today, though, so it is the **fallback** if the 0.6B file is too slow for commands on pong.

**Measure before committing** (details in §6):
- (a) the file loads in LiteRT **1.4.2** `Interpreter` with XNNPACK (it was produced for LiteRT 2.x);
- (b) `benchmark_model` per signature on pong at 4 threads, plus peak RSS;
- (c) WER and latency on the replay rig (`VoiceReplayRig` + `voice_mix.py`), covering commands, dictation, TV/fan noise at SNR 10/5/0 dB, and the 2026-09-24 far-field loudspeaker clip, against small.en and base.en;
- (d) int8 fidelity against NeMo fp32;
- (e) single-word and empty-audio behaviour, and v3 language-ID drift on short or noisy clips.

---

## 1. The Parakeet family

### 1.1 Variants on Hugging Face (nvidia/*)

The table comes from the HF API listing (`https://huggingface.co/api/models?author=nvidia&search=parakeet`) and each model card.

| Model | Params | Decoder | Languages | PnC | Licence | Released |
|---|---|---|---|---|---|---|
| [parakeet-tdt-0.6b-v2](https://huggingface.co/nvidia/parakeet-tdt-0.6b-v2) | 600M | TDT | en | yes | CC-BY-4.0 | 2025-05-01 |
| [parakeet-tdt-0.6b-v3](https://huggingface.co/nvidia/parakeet-tdt-0.6b-v3) | 600M | TDT | 25 EU langs, auto language detection | yes | CC-BY-4.0 | 2025-08-14 |
| [parakeet-tdt_ctc-110m](https://huggingface.co/nvidia/parakeet-tdt_ctc-110m) | ~114M | hybrid TDT + CTC | en | yes | CC-BY-4.0 | 2024-09 |
| [parakeet-ctc-0.6b](https://huggingface.co/nvidia/parakeet-ctc-0.6b) / [-1.1b](https://huggingface.co/nvidia/parakeet-ctc-1.1b) | 0.6/1.1B | CTC | en | **no** (lower-case) | CC-BY-4.0 | 2023-12 |
| [parakeet-rnnt-0.6b](https://huggingface.co/nvidia/parakeet-rnnt-0.6b) / [-1.1b](https://huggingface.co/nvidia/parakeet-rnnt-1.1b) | 0.6/1.1B | RNN-T | en | no | CC-BY-4.0 | 2023-12 |
| [parakeet-tdt-1.1b](https://huggingface.co/nvidia/parakeet-tdt-1.1b) | 1.1B | TDT | en | no | card: CC-BY-4.0 (leaderboard CSV says cc-by-nc-4.0, a discrepancy) | 2024-01 |
| [parakeet-tdt_ctc-1.1b](https://huggingface.co/nvidia/parakeet-tdt_ctc-1.1b) | 1.1B | hybrid | en | yes | CC-BY-4.0 | 2024-05 |
| [parakeet-unified-en-0.6b](https://huggingface.co/nvidia/parakeet-unified-en-0.6b) | 600M | RNN-T, offline + buffered streaming (≥160 ms) | en | yes | **NVIDIA Open Model License** | 2026-04-07 |
| [parakeet_realtime_eou_120m-v1](https://huggingface.co/nvidia/parakeet_realtime_eou_120m-v1) | 120M | cache-aware streaming RNN-T + `<EOU>` token | en | **no** | NVIDIA Open Model License | 2025-10 |
| [nemotron-speech-streaming-en-0.6b](https://huggingface.co/nvidia/nemotron-speech-streaming-en-0.6b) | 600M | cache-aware streaming RNN-T | en | yes | NVIDIA Open Model License | — |
| parakeet-tdt_ctc-0.6b-ja, parakeet-ctc-0.6b-Vietnamese, parakeet-rnnt-110m-da-dk | — | — | single language | — | — | — |

There is **no English "parakeet-ctc-110m"** from NVIDIA. The 110M English model is the hybrid `parakeet-tdt_ctc-110m`, whose CTC branch can be used alone (the card: "to switch decoder to use CTC, use decoding_type='ctc'") [source].

### 1.2 Key facts from the cards (v2/v3)

- **Architecture.** FastConformer-TDT, 600M params [source: v2 and v3 cards]. v3 `config.json`: 24 encoder layers, hidden 1024, FFN 4096, 8× subsampling, 128 mel bins, 2-layer 640-d LSTM prediction net, TDT durations `[0,1,2,3,4]`, vocab 8192 plus blank (id 8192) ([config.json](https://huggingface.co/nvidia/parakeet-tdt-0.6b-v3/blob/541d1f99c6b0c3cd0b11a95167540bb8edefd82b/config.json)) [source].
- **Output.** "Automatic punctuation and capitalization" and "Accurate word-level timestamp predictions" [source: v2 card]. v3 adds segment-level timestamps [source].
- **Licence.** "GOVERNING TERMS: Use of this model is governed by the CC-BY-4.0 license", and "This model is ready for commercial/non-commercial use" [source: v2 and v3 cards]. Commercial use is allowed with attribution.
- **Streaming.** v2/v3 are offline, full-attention models ("up to 24 minutes in a single pass"). The v3 card only shows *chunked/buffered* streaming through a NeMo script (`chunk_secs=2`, `right_context_secs=2.0`, `left_context_secs=10.0`) [source]. sherpa-onnx confirms this: the v3 int8 release "is not for streaming scenario", and its Android demo uses VAD plus "simulated streaming" ([issue #2918](https://github.com/k2-fsa/sherpa-onnx/issues/2918), maintainer reply) [source]. True streaming lives in other models (parakeet-unified, realtime-eou, nemotron-speech-streaming), which carry the NVIDIA Open Model License.
- **RAM.** "At least 2GB RAM for model to load" (NeMo/PyTorch fp32 on GPU) [source: v2 and v3 cards]. This does not describe int8 on-device use; see §2.
- **Caveat relevant to commands.** The v2 card's "Potential Known Risks" says: "Not recommended for word-for-word/incomplete sentences as accuracy varies based on the context of input text" [source]. That is a flag for one-word commands like "ls".

### 1.3 Open ASR Leaderboard numbers (dated)

Source: the leaderboard Space [`hf-audio/open_asr_leaderboard`](https://huggingface.co/spaces/hf-audio/open_asr_leaderboard). Its `init.py` pins each version to a revision of the results dataset [`hf-audio/open-asr-leaderboard-results`](https://huggingface.co/datasets/hf-audio/open-asr-leaderboard-results). Averages are the mean of the listed per-set WERs, computed by me from the CSV.

**Snapshot A: version "20-05-2026"** ("Remove Tedlium dataset"; CSV revision `f82da8589575706245e606f6c845bb3df6c93c71`). This is the last snapshot that still lists Whisper small/base and Parakeet 110M. Sets: AMI, Earnings22, GigaSpeech, LS clean, LS other, SPGI, VoxPopuli.

| Model | Size | AMI | Earn22 | Giga | LS-c | LS-o | SPGI | Vox | **Avg (7)** |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| parakeet-tdt-0.6b-v2 | 0.6B | 11.16 | 11.15 | 9.74 | 1.69 | 3.19 | 2.17 | 5.95 | **6.44** |
| parakeet-tdt-0.6b-v3 | 0.6B | 11.39 | 11.19 | 9.57 | 1.92 | 3.59 | 3.98 | 6.09 | **6.82** |
| parakeet-tdt-1.1b | 1.1B | 15.87 | 14.49 | 9.52 | 1.40 | 2.60 | 3.16 | 5.49 | 7.50 |
| parakeet-rnnt-1.1b | 1.1B | 17.01 | 13.94 | 9.89 | 1.45 | 2.50 | 2.93 | 5.44 | 7.59 |
| whisper-large-v3 | 1.55B | 15.95 | 11.29 | 10.02 | 2.01 | 3.91 | 2.94 | 9.54 | 7.95 |
| parakeet-ctc-1.1b | 1.1B | 15.67 | 13.75 | 10.28 | 1.83 | 3.51 | 4.02 | 6.56 | 7.95 |
| **parakeet-tdt_ctc-110m** | 0.11B | 15.89 | 12.37 | 10.52 | 2.40 | 5.22 | 2.54 | 6.90 | **7.98** |
| parakeet-rnnt-0.6b | 0.6B | 17.40 | 14.66 | 10.01 | 1.62 | 3.02 | 3.32 | 6.08 | 8.02 |
| parakeet-ctc-0.6b | 0.6B | 16.46 | 14.26 | 10.39 | 1.88 | 3.80 | 3.89 | 7.07 | 8.25 |
| whisper-large-v3-turbo | 0.8B | 16.13 | 11.63 | 10.14 | 2.10 | 4.24 | 2.97 | 11.87 | 8.44 |
| **whisper-small.en** | 0.2B | 17.93 | 12.97 | 11.35 | 3.05 | 7.25 | 3.60 | 8.50 | **9.24** |
| moonshine-base | 0.06B | 17.49 | 16.85 | 12.08 | 3.38 | 8.15 | 5.46 | 10.84 | 10.61 |
| **whisper-base.en** | 0.07B | 21.13 | 15.09 | 12.83 | 4.25 | 10.35 | 4.26 | 9.76 | **11.10** |
| whisper-tiny.en | 0.04B | 24.24 | 19.12 | 14.08 | 5.66 | 15.45 | 5.93 | 12.00 | 13.78 |

**Snapshot B: current version "19-09-2026"** (CSV revision `7e57dda97fe368a406fbfe5fee1de6f8266a5a04`). It uses cleaned AMI/Earnings/VoxPopuli, adds Voice Arena Monsoon (en-IN conversational), runs on H200, and uses a new normalizer. The averages are the leaderboard's own `avg` column:

| Model | Avg (8) |
|---|---:|
| parakeet-tdt-0.6b-v2 | 4.70 |
| parakeet-tdt-0.6b-v3 | 4.86 |
| nemotron-speech-streaming-en-0.6b | 5.25 |
| canary-180m-flash | 5.54 |
| whisper-large-v3 | 5.78 |
| parakeet-ctc-0.6b | 6.16 |
| whisper-large-v3-turbo | 6.36 |

Whisper small/base and Parakeet 110M are no longer listed in this version.

Caveats:
- These are stock OpenAI Whisper checkpoints. We ship the **ACFT** fine-tunes (`litert-community/whisper-acft`), and their WER has not been benchmarked here.
- The leaderboard's AMI is the **IHM (headset)** split per the v2 card metadata (`config: ihm`), so it is not a far-field test.
- NVIDIA's own card numbers for v2/v3 (Avg 6.05 / 6.34 with TED-LIUM) match Snapshot A per set.

---

## 2. On-device Android paths that exist today

### 2.1 LiteRT / TFLite: fits our existing runtime (preferred)

**Google's official sample.** `google-ai-edge/litert-samples`, `samples/litert/speech_recognition` ([README @ a1de5c7c](https://github.com/google-ai-edge/litert-samples/blob/a1de5c7c6a6ca4dc1895a764a7cb661be4cec354/samples/litert/speech_recognition/README.md)) lists **Parakeet TDT (v3)** and Parakeet CTC 0.6B as supported on CPU and GPU, with the NPU on Pixel 10 / Galaxy S23–24 [source]. Key facts [source, same README]:
- The models are pre-converted and uploaded to `litert-community` on HF.
- "None of the models supported currently are streaming models." They take **5 s** windows (2 s overlap for files; 4 s overlap for the mic).
- The TDT graph has multiple subgraphs: `encode`, then `decode` with LSTM states passed back in.
- Tokens are decoded with an HF `tokenizer.json`. Overlapping windows are merged using TDT timestamps.

**App metadata** ([model_metadata.json](https://github.com/google-ai-edge/litert-samples/blob/a1de5c7c6a6ca4dc1895a764a7cb661be4cec354/samples/litert/speech_recognition/AndroidApp/app/src/main/assets/model_metadata.json)) [source]:
- `parakeet-tdt-0.6b-v3` → `parakeet_tdt_0.6b_v3_5s_i8_stateful.tflite`
- `tokenizerUrl` = `nvidia/parakeet-tdt-0.6b-v3/tokenizer.json`
- `inputMilliseconds: 5000`, log-mel `nFFT 512, nMels 128, nFrames 500, preemphasis 0.97`
- `decodeStartTokenId: 8192` (blank)

**The HF files** ([litert-community/parakeet-tdt-0.6b-v3 @ 50dae0cb](https://huggingface.co/litert-community/parakeet-tdt-0.6b-v3/tree/50dae0cb8c7b39dda477966eff7150cd7fe206ae)). The repo is tagged apache-2.0 and its card is empty [source]:

| File | Size |
|---|---:|
| `parakeet_tdt_0.6b_v3_5s_i8_stateful.tflite` | 614,261,072 B |
| `parakeet_tdt_0.6b_v3_5s_i8.tflite` | 614,437,424 B |
| `parakeet_tdt_0.6b_v3_5s_f32(_stateful).tflite` | ~2.42 GB |
| per-SoC NPU AOT variants (e.g. `…_Qualcomm_SM8450.tflite`, which is 8 Gen 1-class; pong is SM8475) | ~1.24 GB |

There is also `litert-community/parakeet-ctc-0.6b` (`parakeet_ctc_0.6b_5s_i8.tflite`, 596 MB; lower-case, no PnC).

**Signatures and shapes.** The sibling conversion [`litert-community/parakeet-tdt_ctc-0.6b-ja`](https://huggingface.co/litert-community/parakeet-tdt_ctc-0.6b-ja) says it uses "the same layout the official LiteRT speech recognition sample uses for parakeet-tdt-0.6b-v3" and documents it [source]. Substituting v3's numbers (128 mels, 8192+1+5 logits):

| Signature | Inputs | Outputs |
|---|---|---|
| `encode` | log-mel `[1, 128, 500]` f32 | encoder states `[1, 1024, 63]` |
| `decode` (stateful file: 4-token) | enc states, token ids `[1, N]` i32, LSTM h and c `[2, 1, 640]` | logits `[1, 63, N, 8198]`, new h, new c |
| `decode_1` | same, one token | same, N = 1 |

The converter source confirms the split into encoder, and decoder+joint with `(hidden, cell)` state I/O ([convert/parakeet_tdt.py](https://github.com/google-ai-edge/litert-samples/blob/a1de5c7c6a6ca4dc1895a764a7cb661be4cec354/samples/litert/speech_recognition/convert/parakeet_tdt.py)) [source].

**The decoder we would port.** [TdtDecoder.kt](https://github.com/google-ai-edge/litert-samples/blob/a1de5c7c6a6ca4dc1895a764a7cb661be4cec354/samples/litert/speech_recognition/AndroidApp/app/src/main/java/com/google/ai/edge/examples/asr/TdtDecoder.kt) is 166 lines. Its greedy loop [source]:
- argmax over token logits, then argmax over the 5 duration logits;
- `t += (dur==0 && blank) ? 1 : dur`;
- start with the multi-token `decode`, switch to `decode_1` once the token array is full;
- swap LSTM state buffers **only on non-blank emissions**. This is the fix from commit `8a32fa03` (2026-08-13). The ja card measured the pre-fix code at CER 6.25 % against 0 % with correct semantics [source].

**Fit with TAI.** `WhisperSttRuntime` already does the following [ours: `app/src/main/java/com/termux/ai/WhisperSttRuntime.java`]:
- opens one `Interpreter` and checks for `encode`/`decode` signatures;
- binds inputs by name through `runSignature`;
- uses a custom XNNPACK delegate at 4 threads (`TaiXnnpackDelegate`);
- ships int8-DRQ files with fixed 5 s/10 s windows and a `tokenizer.json` sidecar (`TaiModelCatalog`).

The Parakeet file has the same shape of contract.

**The difference.** The sample uses the LiteRT **2.1.5 `CompiledModel`** API (`com.google.ai.edge.litert:litert:2.1.5` in [build.gradle.kts](https://github.com/google-ai-edge/litert-samples/blob/a1de5c7c6a6ca4dc1895a764a7cb661be4cec354/samples/litert/speech_recognition/AndroidApp/app/build.gradle.kts)). We use `litert:1.4.2` `Interpreter` (`app/build.gradle:202`). A `.tflite` with SignatureDefs is readable by `Interpreter.runSignature` in principle, but op versions emitted by the 2.x converter may be newer than the 1.4.2 kernels. **Loading it in 1.4.2 is test #1** [inferred]. If it fails, the options are bumping `litert` (and checking compatibility with `litertlm-android 0.17.1`), or re-converting with an older toolchain.

**Accuracy cost of int8.** The ja conversion (same recipe) measured, over 28 × 5 s windows against NeMo [source]:

| Variant | Windows matching NeMo exactly | CER vs NeMo |
|---|---:|---:|
| f32 | 28/28 | 0.0000 |
| i8 DRQ | 14/28 | 0.061 |

Expect some int8 degradation on v3 too [inferred]. The f32 file (2.4 GB) is the fallback if the i8 degradation is audible.

### 2.2 sherpa-onnx (onnxruntime)

**Parakeet exports** in the [`asr-models` release](https://github.com/k2-fsa/sherpa-onnx/releases/tag/asr-models) (asset sizes from the GitHub API) [source]:

| Asset | Tarball | Released |
|---|---:|---|
| `sherpa-onnx-nemo-parakeet-tdt-0.6b-v2-int8` | 482 MB | 2025-08-16 |
| `sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8` | 487 MB | 2025-08-16 |
| `…-tdt-0.6b-v2-fp16` | 1.12 GB | 2025-05-06 |
| `sherpa-onnx-nemo-parakeet_tdt_ctc_110m-en-36000-int8` (CTC branch) | 104 MB | 2025-07-08 |
| `…parakeet_tdt_transducer_110m-en-36000-int8` (TDT branch) | 108 MB | 2026-05-03 |
| `…parakeet-unified-en-0.6b-int8-(non-streaming / streaming-240/560/1120ms)` | ~501 MB each | 2026-04/05 |

**Unpacked sizes** (from the sherpa docs, [parakeet-tdt-0.6b-v2.rst](https://github.com/k2-fsa/sherpa/blob/c02f72ca1540163a54019e845127fa52d5de175b/docs/source/onnx/pretrained_models/offline-transducer/nemo/parakeet-tdt-0.6b-v2.rst)) [source]:
- v2 int8: `encoder.int8.onnx` 622 MB + `decoder.int8.onnx` 6.9 MB + `joiner.int8.onnx` 1.7 MB + `tokens.txt`.
- 110M CTC int8: one `model.int8.onnx` of 126 MB ([english.rst](https://github.com/k2-fsa/sherpa/blob/c02f72ca1540163a54019e845127fa52d5de175b/docs/source/onnx/pretrained_models/offline-ctc/nemo/english.rst)).
- Both "support punctuations and cases".

**RTF in the docs** is desktop, not phone [source]:

| Model | RTF | Clip | Threads |
|---|---:|---|---:|
| v2 int8 | 0.118 | 7.4 s | 2 |
| v3 int8 | 0.325 | 3.8 s | 2 |
| 110M CTC int8 | 0.066 | 7.4 s | 2 |

These come from the logs in `code-nemo/*.txt` and `code-english/tdt-ctc-110m-int8.txt`, run on a macOS/Linux host. **I found no published sherpa-onnx Parakeet RTF on an Android phone.** The docs point to prebuilt "simulated streaming" APKs (VAD + Parakeet) but give no numbers.

**RAM.** An iOS user measured **1.23 GB** for v3-int8 on CPU (iPhone 15). The maintainer said loading alone takes about 600 MB, and CoreML went to 2.9 GB and OOM ([issue #2626](https://github.com/k2-fsa/sherpa-onnx/issues/2626)) [source].

**APK cost** [source, measured by me from release v1.13.8 tarballs, 2026-09-10]:
- The static build `sherpa-onnx-v1.13.8-android-static-link-onnxruntime.tar.bz2` ships one `arm64-v8a/libsherpa-onnx-jni.so` of **24.2 MB**.
- The shared build ships `libonnxruntime.so` 22.2 MB + `libsherpa-onnx-jni.so` 4.8 MB + `libsherpa-onnx-c-api.so` 4.5 MB.
- The 4-ABI AAR is 50 MB.

Using onnxruntime-android directly instead of sherpa costs about the same for the ORT library, plus our own feature extractor and decoder.

### 2.3 Other paths

- **NVIDIA NeMo-Speech.cpp** (ggml, Apache-2.0, created 2026-07-15; [README @ 97a15afa](https://github.com/NVIDIA/NeMo-Speech.cpp/blob/97a15afa5caa9bce5baaa86c1184103877af4101/README.md)). It is "NVIDIA's official solution for local speech inference" and supports Parakeet TDT 0.6B v3 and Parakeet CTC 1.1B. NVIDIA ships `parakeet-tdt-0.6b-v3.q8_0.gguf` (714 MB) in the v3 repo [source]. Its build docs cover CPU, Metal, Vulkan and CUDA. **I found no Android build instructions** [source: `docs/build.md`]. It would be a third native runtime for us.
- **Argmax ParakeetKit Pro** ([argmaxinc/parakeetkit-litert-pro](https://huggingface.co/argmaxinc/parakeetkit-litert-pro)): LiteRT Parakeet v2/v3 (quantised encoder 915 MB) usable only through the gated, commercial Argmax Pro SDK [source]. Not usable here.
- **NeMo → ONNX** is how sherpa's exports were produced (`scripts/nemo/…` in sherpa-onnx). **NeMo → TFLite** goes through `litert-samples/convert/convert_to_tflite.py`, which supports stateful/stateless and DRQ. Its `SUPPORTED_MODELS` list is only `parakeet-ctc-0.6b`, `parakeet-tdt-0.6b-v3` and `parakeet-tdt_ctc-0.6b-ja` ([supported_models.py](https://github.com/google-ai-edge/litert-samples/blob/a1de5c7c6a6ca4dc1895a764a7cb661be4cec354/samples/litert/speech_recognition/convert/supported_models.py)) [source]. The ja subclass shows how little changes for a sibling model (blank id, mel bins). Converting **v2** or **110M** would be a similarly small subclass. For 110M, `TdtDecoder`'s hard-coded `NUM_FEATURES = 1024` would also change, because 110M's encoder width differs [inferred].

---

## 3. Expected speed on pong for 1–10 s clips

**Our baseline** [ours, device-verified 2026-09-25, 4 threads, custom XNNPACK delegate]:

| Model | Encode (10 s window) | Decode per token | Peak memory |
|---|---:|---:|---:|
| whisper small.en | ~170 ms | ~80 ms | 538 MB (`benchmark_model`) |
| whisper base.en | ~51 ms | ~27 ms | — |

This works out to about 185 ms for a short command on base.en and about 1.8 s for a 20-token sentence on small.en.

**Published Parakeet LiteRT timings.** None of these are on pong, and none are on CPU with our delegate.

| Setup | Encode (5 s) | Decode | Notes | Label |
|---|---|---|---|---|
| Pixel 8a (Tensor G3), LiteRT 2.1.5, **i8 on CPU**, ja model | 1157 ms | stateless `decode` 380 ms per call; compile 5.2 s | Thread count not stated | [3rd-party] ([ja card](https://huggingface.co/litert-community/parakeet-tdt_ctc-0.6b-ja/blob/1a2b23becb829520b8d7eda2833f26b14e5fcd32/README.md)) |
| Same Pixel 8a, f32 on GPU | 239 ms | stateful `decode_1` **8 ms**, 4-token `decode` 16 ms | A 5 s window drops from ~2.1 s (stateless) to ~0.45 s (stateful) | [3rd-party] |
| Galaxy S26 (SM8850), LiteRT 2.2.0, v3 files | p50 ≈ 63 ms (GPU, f32 and i8); 29.7 ms (f32 on NPU, AOT) | — | i8 failed NPU AOT compile | [3rd-party] ([edge-compat run JSON](https://github.com/john-rocky/edge-compat/blob/7823396d7dab941f6872a1dee54b1a7905e9d732/data/device_runs/2.2.0/2026-08-27/parakeet-tdt-0.6b-v3__parakeet_tdt_0.6b_v3_5s_i8__galaxy-s26.json)) |

The S26 records do **not** say which signature was timed, and they contain no CPU row. Treat them as "it loads and runs on Adreno/Hexagon", not as a latency for our pipeline.

**Why the stateless decode is slow and the stateful one is not** [inferred from the shapes in §2.1]:
- The `decode` signature computes the joint for **every** encoder frame and every token slot: 63 × N × 640 × 8198 MACs.
- With N = 64 (stateless file) that is about 21 GMAC per call, which explains ~380 ms on a phone CPU.
- `decode_1` is 63 × 1 × 640 × 8198 ≈ 0.33 GMAC per call, about 5–10 ms on a big core.
- **The `_stateful` file is mandatory for us.**

**Estimate for pong** [inferred]:
- **Encoder.** It is ~600M params at 8× subsampling, so ~1.2 GFLOP per 80 ms frame, ~75 GFLOP per 5 s window. Whisper small's encoder is ~88M params × 500 frames ≈ 90 GFLOP per 10 s. So **one Parakeet 5 s encode ≈ one small.en 10 s encode ≈ 170–250 ms on pong**, if XNNPACK int8-DRQ efficiency is similar. A 10 s utterance needs 2–3 overlapping 5 s windows, so about 0.4–0.7 s of encoding.
- **Decoder.** TDT skips up to 4 frames per step, so a 5 s window takes roughly tokens + ⌈63/4⌉…63 steps. That is ~20–60 `decode_1` calls at ~5–10 ms each, so **~0.1–0.5 s per window**.
- **End to end:**

  | Clip | Parakeet v3 (est.) | small.en | base.en |
  |---|---|---|---|
  | Short command (1 s audio in a 5 s window) | ~0.3–0.7 s | ~0.4 s (170 + 3×80 ms) | ~0.19 s |
  | 10 s dictated sentence | ~0.7–1.5 s | ~1.8 s+ | — |

- **Load.** `Interpreter` init for a 614 MB file. The Pixel 8a CPU compile took 5.2 s [3rd-party], so keep the model resident, as `TaiResidency` already does for Whisper.

These are estimates. Test (b) in §6 replaces them.

---

## 4. Noise robustness and far-field evidence

- **NVIDIA's own MUSAN test.** Both cards report WER with MUSAN music+noise added at fixed SNRs across the leaderboard sets (greedy, no LM) [source]:

  | Model | Clean | SNR 10 | SNR 5 | SNR 0 | SNR −5 |
  |---|---:|---:|---:|---:|---:|
  | v2 | 6.05 | 6.95 | 8.23 | 11.88 | 20.26 |
  | v3 | 6.34 | 7.12 | 8.23 | 11.66 | 19.88 |

  On AMI (the most TV/fan-like conversational set), v2 goes 11.16 → 14.38 → 18.07 → 25.43 at SNR 10/5/0.
- **Telephony.** v2 with μ-law 8 kHz: 6.05 → 6.32 [source].
- **Training data.** It is described as "Noise robust data from various sources" (v2/v3 cards), with ~120k h (v2) and ~670k h (v3) including YouTube-derived pseudo-labels [source]. No far-field or reverberation-specific evaluation is published for Parakeet. The leaderboard's AMI is the headset (IHM) split [source: v2 card metadata].
- **No head-to-head with Whisper small under the same noise.** The closest reference is the Whisper paper (§3.7, [arXiv 2212.04356](https://arxiv.org/abs/2212.04356)). It tested 14 LibriSpeech-trained models, including two older NVIDIA STT models (not Parakeet), and found they "quickly degrade as the noise becomes more intensive, performing worse than the Whisper model under additive pub noise of SNR below 10 dB" [source]. Parakeet v2/v3 are trained on far larger and noisier data than those 2022 models, so that finding does not carry over. It has to be measured.
- **Hallucination behaviour.** Our small.en/base.en far-field test produced invented text on near-silence (`[pause]`, `¶¶`, `*`, `(B)`) [ours]. A transducer emits blank per frame instead of free-running an autoregressive decoder. The realtime-EOU card says "The output text might be empty if input audio doesn't contain any speech" [source, for that model]. I expect fewer invented outputs from TDT on silence and noise [inferred], which is test (e) in §6.

---

## 5. Integration cost and risks for TAI

**LiteRT route** (recommended). Every new or changed component is [inferred], sized against our Whisper code:

| Component | Work |
|---|---|
| `ParakeetSttRuntime` | Open the `Interpreter`, check the `encode`/`decode`/`decode_1` signatures, and reuse `TaiXnnpackDelegate`. Mirrors `WhisperSttRuntime` (432 lines), but simpler: no KV cache, no prompt, no suppression list. |
| Mel front end | NeMo features: 16 kHz, preemphasis 0.97, n_fft 512, win 400, hop 160, **128** mels, per-feature mean/variance normalisation ([processor_config.json](https://huggingface.co/nvidia/parakeet-tdt-0.6b-v3/blob/541d1f99c6b0c3cd0b11a95167540bb8edefd82b/processor_config.json)). Differs from `WhisperMel` (log10, clamp, 80 mels), so a sibling class of ~150–250 lines with a golden fixture from NeMo. |
| TDT greedy decoder | Port of `TdtDecoder.kt`, ~120 lines. Must advance state only on non-blank and use `decode_1`. Timestamps come free: frame index × 80 ms. |
| Tokenizer | Decode only: id → SentencePiece piece, `▁` → space. `tokenizer.json` is 1.16 MB. Far simpler than `WhisperTokenizer`'s byte-level BPE. |
| Windowing | The file is fixed at 5 s. Our segmenter cuts at pauses with a 10 s cap. Either cap Parakeet segments at 5 s (cut at the quietest point, as today), or run overlapping windows and merge by timestamps as the sample does (`LevenshteinTokenMerger` / timestamp alignment). |
| Catalog | A new `speechEntries()` entry with `CAPABILITY_SPEECH_TO_TEXT`, a 614 MB file plus tokenizer sidecar pinned to HF revisions. The runtime is chosen by model family, today implicitly Whisper. The RAM class suggestion should be ≥8 GB. |
| Post-processing | PnC output means "ls" may arrive as "Ls." or "LS." `VoiceTerminalCleanup` already strips trailing `.?!` and lowercases single words. Multi-word commands ("git status") may gain capitals and full stops, so apply the cleanup to command-shaped phrases too. |
| APK | +0 native bytes. The model is downloaded. Add a CC-BY-4.0 attribution line for NVIDIA. |

**sherpa-onnx route.** It saves writing the mel and decoder code, and brings VAD and the Silero pipeline for free. The costs:
- **+24 MB arm64 `.so`** [source, §2.2];
- a second inference runtime, with its own threading and memory, beside LiteRT and LiteRT-LM;
- JNI class-name coupling (the sherpa Kotlin API expects its own package names);
- a 1.2 GB RAM report on iOS [source].

It is the right route only for **110M** (no TFLite exists) or for the streaming `parakeet-unified` exports.

**Risks:**
1. The **LiteRT 1.4.2 vs 2.x** op-version mismatch (see §2.1).
2. **int8 degradation** (ja: CER 6 % vs NeMo) [source].
3. **Single-word commands.** NVIDIA warns about "word-for-word/incomplete sentences" [source]. The 5 s window pads a 0.5 s "ls" with 4.5 s of silence.
4. **v3 auto language ID** on short or noisy English clips could emit another EU language. There is no language-forcing prompt in TDT [inferred]. v2 (English-only) avoids this but needs our own conversion.
5. **RAM and residency.** The file is 614 MB, larger than small.en's 538 MB peak. It is fine on 12 GB but must go through the `TaiLoadBudget` / residency manager together with chat LLMs [inferred].
6. **No phone-CPU benchmark for v3 exists publicly.** All speed claims above for pong are estimates.
7. The litert-community repo has **an empty card** and an apache-2.0 tag over CC-BY-4.0 weights. Pin the revision and attribute NVIDIA [inferred].

---

## 6. Recommendation and what to measure first

**Add `parakeet-tdt-0.6b-v3`, file `parakeet_tdt_0.6b_v3_5s_i8_stateful.tflite` @ `litert-community/parakeet-tdt-0.6b-v3` rev `50dae0cb`, as one experimental speech-to-text catalog entry on the existing LiteRT `Interpreter` path.** Make it the default only if the measurements below beat small.en. Keep base.en as the fast command model if Parakeet's command latency lands above ~0.4 s.

Measure, in this order (all on pong, 4 threads, same XNNPACK delegate):

1. **Load and op check.** Can `Interpreter` 1.4.2 open the file with `TaiXnnpackDelegate`? Record the signature keys and I/O names/shapes, init time, and how many nodes XNNPACK takes. If loading fails, try a LiteRT 2.x `Interpreter` before anything else.
2. **Per-signature `benchmark_model`** for `encode`, `decode` (4-token) and `decode_1`: latency, and peak memory next to the small.en row in the plan's table.
3. **End-to-end on the replay rig** (`VoiceReplayRig`, `scripts/voice_pull_sessions.sh`, `scripts/voice_mix.py`). Clip sets:
   - commands: "ls", "enter key", "git status", "cd dot dot";
   - 5–10 s dictation;
   - the same clips mixed with TV and fan noise at SNR 10/5/0 dB;
   - the 2026-09-24 far-field loudspeaker recording.

   Record WER per set and time-to-text for Parakeet v3, small.en and base.en.
4. **int8 fidelity.** Run the same clips through NeMo fp32 on the desktop and compare the i8 transcripts. If the gap matters, try the f32 file (2.4 GB) once for an upper bound.
5. **Behaviour checks:**
   - output on pure noise and silence: empty vs invented text;
   - single-word casing and punctuation after `VoiceTerminalCleanup`;
   - any non-English output from v3 on short or noisy clips;
   - how 5 s windowing handles a 7–10 s sentence (cut or merge quality).

**Decision rule** [inferred]:

| Result | Action |
|---|---|
| (3) shows clearly lower WER than small.en on the noisy and far-field sets, and the dictation latency ≤ small.en | Ship it as the recommended model on ≥8 GB devices. |
| Accuracy wins but commands are slow | Keep it for dictation and route single-phrase commands to base.en, or evaluate **parakeet-tdt_ctc-110m** (sherpa-onnx int8, 126 MB, or our own TFLite conversion of its CTC branch) as the fast tier. |
| Load fails and a LiteRT bump is blocked | Reconsider sherpa-onnx (+24 MB) only if (3) on a desktop NeMo run already shows a decisive accuracy win on our clips. |

## Replay-rig results (2026-09-25)

Measured on this repo's host replay rig (`VoiceReplayRig`, see the Replay rig section of
`plans/whisper-voice-input.md`): the app's own VAD, gain, command classifier and terminal cleanup
in Java; Whisper through `scripts/whisper_replay_server.py` (with the terminal bias line) and
Parakeet through `scripts/parakeet_replay_server.py` (no biasing possible). Test set from
`scripts/voice_eval_make.py`: 16 phrases (5 spoken keys, 5 typed commands, 6 dictation sentences)
× 4 Piper voices × 4 conditions at pong's measured levels = 256 clips. Synthetic voices are cleaner
than a person; read this as a comparison of models on identical audio.

| All conditions | keys (80) | typed commands (80) | dictation WER | ms / clip (desktop CPU) |
|---|---|---|---|---|
| Whisper base.en | 41 | 29 | 24.5 % | 503 |
| Whisper small.en | 55 → **59** with the `c key` alias | 37 | 17.5 % | 1542 |
| Parakeet TDT v3 | 29 | 13 | **14.8 %** | 513 |

By condition, dictation WER (base / small / Parakeet): near 5.5 / 5.5 / **3.8**, far 10.3 / **7.9** /
11.0, fan 44.5 / 28.1 / **24.7**, TV 37.7 / 28.4 / **19.9** %.

What the numbers mean:
- **Parakeet is the best dictation engine here and as fast as base.en** on the host, especially
  in noise (fan, TV).
- **Parakeet is poor at terminal input**: "Get status", "Pseudo apt update", "L S", "Tank key".
  It has no prompt, so the terminal bias line that teaches Whisper "git / sudo / ls" cannot be
  applied. A post-ASR replacement dictionary (the Freestyle idea) could fix the common ones.
- **small.en is the best command engine**; its one systematic miss ("control c key" → "c key")
  is now handled by the classifier (commit 697d8cea).
- Swapping `ctrl` for `control` in the bias line helped Ctrl+C but cost Tab/Backspace: no net
  gain, reverted.
- Short far words ("ls", "clear") are dropped by the 300 ms voiced minimum for every model.

Two integration facts found on the way:
- **Pad the audio, not the features.** Zero-padding the log-mel features of a short phrase (the
  Android sample's way) reads as "average sound" after per-bin normalisation; the graph has no
  length input, and on 1–1.5 s phrases the model repeated itself ("Enter key. Enter key",
  "Clear clear clear…"). Padding the waveform with near-silence to 5 s fixed it.
- The graph loads in desktop `ai-edge-litert` (2.x) with the expected `encode` / `decode` /
  `decode_1` signatures. Whether it loads in the app's LiteRT 1.4.2 is measured on pong with
  `SpeechGraphProbe` (debug build) — see below.

Recommendation after measuring: keep Whisper (small.en on phones with room for it) as the
default for the terminal; offer Parakeet later as an optional **dictation** engine (prompts to
agents, notes), not as the command engine, unless a replacement dictionary closes the command gap.
