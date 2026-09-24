# On-device voice input (Whisper ACFT)

Status: planned (2026-09-24). Depends on [`tai-memory-manager.md`](tai-memory-manager.md) phase 2
for residency accounting; phases 1–4 below can land before it with a conservative preflight.

Goal: speak to AI agents running in the terminal without the Android recognizer. Sub-goal: say
"ls", pause, "enter", and `ls` runs.

## Model: `litert-community/whisper-acft`

FUTO's audio-context fine-tuned Whisper, exported to LiteRT with `encode` / `decode` signatures,
int8 weights / fp32 activations (dynamic-range quantized), Apache-2.0.

| size | multilingual | English-only | file |
|---|---|---|---:|
| tiny | `tiny/acft_whisper_tiny_{5s,10s,30s}_drq.tflite` | `tiny.en/…` | 59–61 MB |
| base | `base/…` | `base.en/…` | 101–103 MB |
| small | `small/…` | `small.en/…` | 286–289 MB |

Facts that shape the design (from the model card):

- **Windows are 5 s, 10 s or 30 s**, each a separate graph file. Encode input is
  `[1, 80, 500 | 1000 | 3000]` mel frames; audio longer than the window is hard-truncated. There is
  no 1 s or 15 s graph.
- The decoder has **no KV cache**: every token re-runs the full decoder. Decode, not encode,
  dominates latency (base 5 s: encode 0.04 s, decode 0.3–0.6 s for a short sentence on desktop CPU;
  0.5–0.8 s end to end on a Snapdragon 865).
- Decode signature input order is `(mask, audio, tokens)` — bind by name/shape, not position.
  Measured: `args_0` audio `[1, frames/2, 512]`, `args_1` tokens `[1, 128]` int32, `args_2` mask
  `[1, 1, 128, 128]` float, additive causal (0 on and below the diagonal, −1e9 above). The token
  sequence is **fixed at 128**, prompt included; logits come back as `[1, 128, vocab]` and the next
  token is read at the last filled position.
- Multilingual prompt: `[50258 <|startoftranscript|>, <|lang|>, 50359 <|transcribe|>,
  50363 <|notimestamps|>]`. English-only prompt: `[50257, 50362]`, no language/task tokens,
  EOT = 50256.
- Tokenizer is `tokenizer.json` from the matching `openai/whisper-{size}{.en}` repo — a second HF
  repo.
- Language forcing is recommended for short clips; base is the recommended minimum for non-English.

## Measured (2026-09-24)

Desktop reference decoder (numpy mel + tokenizer + greedy decode against the LiteRT graphs, in the
session scratchpad; it becomes the golden fixture) over 26 synthesised clips — 13 phrases × two
Piper voices (US, GB): single command words, short shell commands, two agent-style sentences.

- **Short clips need silence around them.** Tightly cut words make the `.en` models emit `.`,
  `[Music]` or `%`. With 300 ms of lead-in and tail (what a VAD pre-roll gives), base.en 10 s gets
  most commands.
- **The 30 s window fails on short speech** (`[Music]`, `www.mooji.org`, `[BLANK_AUDIO]`) and
  **tiny fails on single words** (`[Music]`, `"I'm a child"` for `ls`).
- **A shell-vocabulary prompt fixes the rest.** `<|startofprev|>` + `git ls cd sudo apt pkg tab
  enter escape ctrl` before the task prompt took base.en 10 s and small.en 10 s to **26/26
  correct**, lowercased (`git status`, `ls`, `sudo apt update`, `tab`, `send`, `ctrl c`); without it
  `git` was always `Get`. Long sentences stay correct under the prompt.
- Synthetic voices are an optimistic proxy; first device test with a real voice decides the
  defaults below.

pong (Snapdragon 8+ Gen 1), TFLite `benchmark_model`, per signature:

| model | encode | decode step | peak memory |
|---|---:|---:|---:|
| tiny.en 10 s | 26 ms | 16 ms | 122 MB |
| base.en 5 s | 21 ms | 24 ms | 190 MB |
| **base.en 10 s** | **57 ms** | **30 ms** | **193 MB** |
| small.en 10 s | 175 ms | 81 ms | 538 MB |

A command (2–4 decode steps) is ~150 ms on base.en 10 s; a 20-token sentence ~0.7 s (small.en
~2 s). 4 threads = 6 threads; 2 threads is 1.6× slower. **GPU delegate: 0 GPU kernels created**
(unsupported ops), so the graph runs entirely on CPU anyway.

### Far-field check on pong (2026-09-24)

20 s recorded through pong's microphone (Termux:API `termux-microphone-record`, AAC 16 kHz mono)
of a YouTube video on an external loudspeaker — quiet far-field audio, mean −33.7 dB, peak
−16.5 dB — transcribed with the reference decoder in 10 s windows:

- **small.en** 10 s: "…because when a metric becomes a target, it ceases to become a good metric.
  So, for example, in the example of our cleaning robot, if you / you could imagine rewarding it
  that way" — correct apart from the window cut.
- **base.en** 10 s: `[pause]`, `¶¶` — no speech recognised; +11 dB AGC to −20 dBFS did not help.
- The 0.1 s remainder after the two windows produced invented text on both (`*`, `(B)`, `(S)`).

Consequences: far-field/loudspeaker audio needs small (offer it as the recommended size where
RAM allows, not just "more accurate"); base is still to be judged on close-talk speech, which is the
keyboard's main case; segments shorter than ~0.3 s of voiced audio are dropped, never decoded; and
fixed-window cuts split phrases, so pause-based segmentation (below) is required, not optional.

## Decisions

### Window ("buffer") length

The three windows of one size are within 2–4 MB of each other and activations are small, so **the
window does not meaningfully change memory** — it changes latency and the longest single
utterance. Memory selects the model *size*; the window is chosen for latency:

- Default **10 s**. Voice input is segmented at pauses (below), so a 10 s window covers commands and
  dictation phrase by phrase; a segment that reaches 10 s is cut at the quietest point of its last
  second and continues in the next segment.
- 5 s offered as "Commands (fastest)" — it saves only ~35 ms encode + ~6 ms per step on pong.
  **30 s is not offered**: it hallucinates on short speech, and segmentation already covers long
  dictation. Changing the window downloads that graph and deletes the old one.

### Model size by RAM class (default suggestion in the download dialog)

| RAM class | suggested | also offered |
|---|---|---|
| ≤ 6 GB | base (~195 MB resident) | — |
| ≥ 8 GB | base | small (~540 MB, ~3× slower, more accurate on hard audio) |

tiny is not offered: it fails on single command words, and base already fits a 4 GB phone.

English-only vs multilingual is asked once, at download. The choice is part of the model id
(`whisper-acft-base-en`, `whisper-acft-base`).

### CPU, not GPU

Run on CPU (XNNPACK via the default `Interpreter` path), threads = min(4, big cores), no GPU
delegate:

- Dynamic-range int8 weights with fp32 activations are hybrid ops the GPU delegate does not run
  natively; the graph would fragment across CPU/GPU with copies at every boundary.
- The per-step decoder re-run is many small dispatches, where GPU launch overhead outweighs its
  throughput.
- The chat LLM normally owns the GPU. Keeping STT on CPU means talking to an agent never contends
  with that agent's own generation for the GPU, and never forces a chat reload.

Confirmed on pong: the GPU delegate creates 0 kernels for these graphs. Revisit only if
KV-cached decoder graphs appear; an experimental "GPU" option is not planned.

### Where it runs

Inference in the `:tai_runtime` process (native crash isolation and one memory accounting), capture
in the UI process:

- New op `transcribe` in `TaiRuntimeIpc` / `TaiRuntimeService`, plus `sttWarm` to load the model
  when the mic opens so the load overlaps the first words.
- **Separate STT executor.** The service's serial executor would queue transcription behind a chat
  generation — exactly the "talk to an agent while it answers" case. STT gets its own single
  thread; only loads still take the shared load lock.
- Audio crosses IPC as a file path (`cacheDir/tai-ipc/stt-<id>.pcm`, raw PCM16), not base64.
- `WhisperSttRuntime` owned by `MultiBackendTaiRuntime`, `Interpreter.getSignatureRunner("encode" /
  "decode")`, encode frame count read from the signature's input shape.
- Also exposed as OpenAI-compatible `/v1/audio/transcriptions` and `tai transcribe <file>`, so
  agents and scripts can use the same model.

### Audio front end (Java, no new native code)

- `AudioRecord`, 16 kHz mono PCM16, `AudioSource.VOICE_RECOGNITION`; only while the activity is
  in the foreground.
- Log-mel: n_fft 400, hop 160, periodic Hann, power spectrum, 80 Slaney mel filters 0–8 kHz,
  `log10(max(x, 1e-10))`, clamp to `max − 8`, `(x + 4) / 4`, zero-pad to the window's frame count —
  Whisper's `log_mel_spectrogram` exactly. A golden fixture (Python-generated mel of a short wav)
  pins it in a unit test.
- Tokenizer: parse `tokenizer.json` vocab + added tokens; decode only (id → byte-level BPE → UTF-8).
- Greedy decode, stop at EOT; the token budget is 128 − prompt length (the decoder's fixed
  sequence), repetition guard (abort a segment that repeats the same 4-gram 3×). Suppress
  timestamp tokens, `<|startof…|>` / task tokens, and EOT on the first step.
- **Prompt biasing (core, not later):** `<|startofprev|>` + a vocabulary line before the task
  prompt. Terminal target: shell words (`git ls cd sudo apt pkg tab enter escape ctrl` plus a
  user-editable list); other targets: no prompt. Needs a byte-level BPE *encoder* for the
  vocabulary line (the multilingual vocab splits most shell words into several tokens); keep it
  under ~24 tokens so the decode budget stays ≥ 100.

### Segmentation (what makes "ls … enter" work)

Energy VAD on 30 ms frames with an adaptive noise floor:

- speech starts when a frame is ≥ 9 dB over the floor; the segment keeps **300 ms of audio before
  onset and 300 ms after the last voiced frame** (measured: without it single words hallucinate);
- a **pause of 600 ms** (setting: 400–1200 ms) closes the segment and sends it to transcription
  immediately;
- 2.5 s of silence, a tap on the voice key, or leaving the keyboard ends the session.

Each segment is transcribed and inserted as soon as it is ready, so text appears phrase by phrase.

### Voice commands

A segment whose **entire** normalized transcript (lowercase, punctuation stripped) is a command
word becomes a key instead of text:

| said | key |
|---|---|
| enter, return, send, submit | Enter |
| tab | Tab |
| escape | Esc |
| backspace, delete | Backspace |
| space | Space |
| control c, cancel | Ctrl+C |

Whole-segment matching is the safety rule: "enter the directory" said in one breath stays text;
only "enter" said on its own, after a pause, presses Enter. Keys go through
`TerminalKeyEventHandler.dispatchKeyValue(KeyValue.getKeyByName("enter"))`, so they reach whatever
has typing (terminal, command palette, drawer search) the same way a keyboard key does.

### Terminal cleanup

When typing goes to a terminal session (no key-value interceptor; add
`TermuxInAppKeyboard.hasKeyValueInterceptor()`), transcripts are cleaned before insertion:

- strip trailing `. ? !`;
- lowercase a single-word segment (`"LS."` → `ls`), leave multi-word prose as spoken;
- join consecutive segments with one space.

The vocabulary prompt already yields lowercase, unpunctuated shell text; cleanup is the safety
net. Later, not in this plan: spoken symbols ("dash", "slash", "pipe").

### Language forcing

- Multilingual models: setting "Voice language" = **Auto (default)** or an explicit language.
  Auto uses the in-app keyboard's current layout language, then the first system locale, mapped to
  the Whisper language token (`50259 + index` in Whisper's language order). Locales Whisper does
  not know fall back to English with a one-time notice.
- English-only models: no language token. Choosing a non-English language with an `.en` model
  shows a notice offering the multilingual download.

### Fallbacks

Model missing, memory refused, microphone permission denied, or a transcription error → the key
falls back to the Android recognizer (today's `launchVoiceTyping`) with a one-line notice. Long-press
on the voice key still opens the system chooser.

## Settings

**In-app keyboard settings** (`termux_keyboard_preferences.xml`, new "Voice input" category, keys
stored through `KeyboardPreferencesDataStore` → `TermuxAppSharedPreferences`):

| key | type | default |
|---|---|---|
| `keyboard_voice_engine` | list: Android system / On-device (Whisper) | **Android system** |
| `keyboard_voice_language` | list: Auto / languages (multilingual model only) | Auto |
| `keyboard_voice_commands` | switch: spoken "enter", "tab", … press keys | on |
| `keyboard_voice_terminal_cleanup` | switch | on |
| `keyboard_voice_pause_ms` | list: 400 / 600 / 800 / 1200 | 600 |
| "Speech model" | link to the TAI speech section | — |

Choosing On-device with no model installed opens the TAI speech section.

**TAI settings** (`termux_ai_preferences.xml`, new "Speech-to-text" category):

- Installed speech model row (size, language kind, window, measured memory) with delete.
- Download: dialog asks size (base / small, with the RAM-class suggestion), English-only vs
  multilingual, and window (10 s default, 5 s).
- Terminal vocabulary: extra words added to the biasing prompt (project names, commands).
- Window switch (re-downloads that graph), idle unload (default 2 min).

Speech models are kept out of the chat model list and the chat catalog.

## Downloader and catalog changes

- `CatalogEntry` gains `sidecars: [(url, localName, sha256)]` and a new capability
  `speech_to_text`. Whisper entries list `tokenizer.json` from `openai/whisper-*` as a sidecar.
- `requiresLiteRtEmbeddingTokenizer` branches on capability, not `.tflite` extension (today every
  `.tflite` would take the sentencepiece path and fail).
- Sidecars reuse the `.part` / resume / hash helpers; the main file keeps its sha256 check.
- `MultiBackendTaiRuntime` routes `speech_to_text` to `WhisperSttRuntime`.

## Memory

STT is a registry kind (`STT`) in the memory manager. Estimates from the pong benchmark: base
~195 MB, small ~540 MB (file size × ~1.9) until the first measured load. An STT load evicts
idle embeddings first; it never evicts a chat model that is generating. If it still does not fit,
voice input falls back to the Android recognizer. STT unloads after 2 minutes idle.

## Phases

1. Downloader sidecars, Whisper catalog entries, TAI speech-to-text section. **Done** (2026-09-24):
   `litert-community/whisper-acft` pinned at `f8ab0a00ea95f6e0f2cee200b18671a599a0b0d6`; four catalog
   ids (`whisper-acft-base{,-en}`, `whisper-acft-small{,-en}`), each with a `tokenizer.json` sidecar
   from the matching `openai/whisper-*` repo (also pinned) and both window variants (5s/10s, 10s
   default) via `CatalogEntry#withWindow`. `requiresLiteRtEmbeddingTokenizer` branches on
   `text_embeddings` capability, not `.tflite` extension. Speech models excluded from chat catalogs,
   installed-model lists, the default-assistant picker and `/v1/models`; `loadModel` refuses them.
   No runtime routing yet (phase 2).
2. `WhisperSttRuntime` (mel, tokenizer, signatures, greedy decode), `transcribe` op on its own
   executor, `/v1/audio/transcriptions`, `tai transcribe`. **Device-verified on pong 2026-09-24**
   (`tai transcribe`, debug build): git status fixture → "Get status" / `--terminal` "git status" on
   base.en and small.en (matches the reference); ls → "." / "ls"; GB "sudo apt update" → base.en
   `--terminal` "update" (dropped words — the 12-token BPE bias line differs from the reference's
   8-token lookup), small.en "sudo apt update"; 22-token agent sentence correct on small.en.
   Timings base.en 10 s: mel 59–125 ms (was 1.7 s before cb1aa1b8 skipped silent frames), encode
   ~142 ms, decode ~78 ms/step — 2.5–3× the benchmark_model figures (57 ms, 30 ms/step); small.en
   encode ~500 ms, ~240 ms/step. Speed pass (37e44a13): the process sat in Nothing's
   `nt_foreground` cpuset (CPUs 0-3, little cores); transcribe/sttWarm now run as foreground
   operations → `top-app`, base.en encode 114 ms, decode ~62 ms/step (~0.3 s per short command),
   small.en encode ~410 ms, ~192 ms/step. Measured and **not** faster, so dropped: reading the
   logits row in place instead of copying [128 × vocab], and LiteRT 2.2.0 instead of 1.4.2. Still
   ~2.3× behind `benchmark_model` on the same file re-run the same evening (50 ms / 27 ms), with the
   same cgroup, thread priority and XNNPACK coverage (475/476, 292/292 nodes). **simpleperf (run-as,
   cpu-cycles) found the cause: inference is single-threaded** — 99.56 % of samples on the one
   `tai-runtime-stt` tid, no XNNPACK worker threads exist, and the app's 113 ms / 61 ms equal
   `benchmark_model --num_threads=1` (118 / 60) against 76 / 28 at 4 threads. litert 1.4.2's
   NativeInterpreterWrapper does pass getNumThreads()/getUseXNNPACK() to createInterpreter, yet
   neither dropping the explicit setUseXNNPACK(true) nor a hard-coded setNumThreads(4) produced a
   worker thread (LiteRT 2.2.0 measured the same). The setUseXNNPACK(false) control confirmed
   which half was lost: the interpreter itself still spawns 7 threads, so it is specifically
   XNNPACK's pthreadpool that litert's own delegate never gets. Fix (pending device verification):
   a tiny JNI shim (`TaiXnnpackDelegate` / `libtai_xnnpack.so`) dlopens the already-resident
   `libtensorflowlite_jni.so` and calls its exported `TfLiteXNNPackDelegateCreate` directly with
   `num_threads=4`, handing the result to `Interpreter.Options.addDelegate()` in place of the
   stock `setUseXNNPACK(true)` path. **Done** (2026-09-24): `WhisperMel`
   (16 × 25 DFT, pinned to the reference fixture), `WhisperTokenizer` (decode + merge-rank BPE
   encode for the bias line), `WhisperDecoder` (prompt, suppressions, repetition guard),
   `WhisperSegmenter` (pause split, < 0.3 s voiced dropped, 300 ms padding), `WhisperAudio`
   (WAV/PCM16, linear resample). `Interpreter.runSignature("encode"/"decode")` with decode inputs
   bound by dtype/rank, CPU/XNNPACK, `min(4, cores)` threads. `transcribe`/`sttWarm` run on the
   service's `tai-runtime-stt` lane; audio crosses IPC as a path under `cacheDir/tai-ipc`.
   `POST /v1/audio/transcriptions` (multipart `file`, `model`, `language`, `prompt`,
   `prompt_mode: terminal`, `response_format` json|text) and `tai transcribe <file> [--model id]
   [--language xx] [--terminal]`. Memory: `TaiResidency.Kind.STT` at file × 1.9 until measured,
   `decideSttLoad` through `planFixed` (may evict idle chat), busy while transcribing, idle unload
   from `TaiSettings.getSttIdleUnloadMinutes` carried to the runtime process with each request.
   Prompt note: the `.en` tokenizer.json also carries `<|en|>`, so — as in the reference decoder
   that produced the golden fixture — `.en` graphs are prompted `[sot, <|en|>, <|transcribe|>,
   <|notimestamps|>]`; the 2-token `.en` prompt gives the same text on base.en and garbage on the
   multilingual graph. Device check on pong pending (see phase 3's test list).
3. Capture + VAD + permission (request code 4717) + keyboard engine toggle + insertion + fallbacks.
4. Voice commands, terminal cleanup, language forcing.
5. Memory-manager registration and eviction (after memory manager phase 2).

## Tests

- Mel fixture test against Whisper's reference output; tokenizer decode test (multilingual and
  `.en` special tokens).
- Segment → command classifier table test ("enter" → key; "enter the directory" → text).
- Terminal cleanup table test.
- Device on pong (with permission, phone not in the developer's hands): base.en 10 s latency per
  segment, "ls … enter" in a terminal, dictation into a Claude Code session while it streams a reply
  (the separate executor).
