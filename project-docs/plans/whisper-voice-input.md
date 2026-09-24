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
- Multilingual prompt: `[50258 <|startoftranscript|>, <|lang|>, 50359 <|transcribe|>,
  50363 <|notimestamps|>]`. English-only prompt: `[50257, 50362]`, no language/task tokens,
  EOT = 50256.
- Tokenizer is `tokenizer.json` from the matching `openai/whisper-{size}{.en}` repo — a second HF
  repo.
- Language forcing is recommended for short clips; base is the recommended minimum for non-English.

## Decisions

### Window ("buffer") length

The three windows of one size are within 2–4 MB of each other and activations are small, so **the
window does not meaningfully change memory** — it changes latency and the longest single
utterance. Memory selects the model *size*; the window is chosen for latency:

- Default **10 s**. Voice input is segmented at pauses (below), so a 10 s window covers commands and
  dictation phrase by phrase; a segment that reaches 10 s is cut at the quietest point of its last
  second and continues in the next segment.
- 5 s offered as "Commands (fastest)", 30 s as "Long phrases". Changing it downloads that graph and
  deletes the old one (only one window per size is kept on disk).

### Model size by RAM class (default suggestion in the download dialog)

| RAM class | suggested | also offered |
|---|---|---|
| ≤ 4 GB | tiny | base |
| 6–8 GB | base | tiny, small |
| ≥ 12 GB | base | small, tiny |

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

Revisit only if KV-cached decoder graphs appear; an experimental "GPU" option is not planned.

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
- Greedy decode, stop at EOT, max tokens 64 / 128 / 224 for 5 / 10 / 30 s, repetition guard
  (abort a segment that repeats the same n-gram 3×).

### Segmentation (what makes "ls … enter" work)

Energy VAD on 30 ms frames with an adaptive noise floor:

- speech starts when a frame is ≥ 9 dB over the floor;
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

Later, not in this plan: spoken symbols ("dash", "slash", "pipe") and prompt-biasing the decoder
with shell vocabulary via `<|startofprev|>`.

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
- Download: dialog asks size (with the RAM-class suggestion), English-only vs multilingual, and
  window (default 10 s).
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

STT is a registry kind (`STT`) in the memory manager. The estimate is file size × 1.3 until the
first measured load (expected roughly 80 MB tiny, 140 MB base, 380 MB small). An STT load evicts
idle embeddings first; it never evicts a chat model that is generating. If it still does not fit,
voice input falls back to the Android recognizer. STT unloads after 2 minutes idle.

## Phases

1. Downloader sidecars, Whisper catalog entries, TAI speech-to-text section.
2. `WhisperSttRuntime` (mel, tokenizer, signatures, greedy decode), `transcribe` op on its own
   executor, `/v1/audio/transcriptions`, `tai transcribe`.
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
