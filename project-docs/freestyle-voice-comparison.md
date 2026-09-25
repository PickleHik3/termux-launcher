# On-device voice input: Freestyle vs. termux-launcher

Assessment date: **2026-09-25**. Local checkout (termux-launcher, branch `dev`): **373ce96bd32edcb7be801f001b0636df559f4f82**. Freestyle: shallow clone of `https://github.com/freestyle-voice/freestyle`, commit **dacec015490471facef6fa8b6510913d7b201f71** (2026-09-18, "Merge branch 'release/0.9.1'", desktop app `version` 0.9.1). The clone is read-only and lives in the session scratchpad, not in this repo. This is a research comparison, not an implementation plan. No code was changed, built, installed or run on a device.

Only primary sources were used: Freestyle's source, specs and docs, and this repo's code and plan notes (`project-docs/plans/whisper-voice-input.md`, the 2026-09-25 STT-refinement handoff).

**Citation keys.** `F:` = `freestyle@dacec015:`. `T:` = this repo, relative to the root. Permalink form for Freestyle: `https://github.com/freestyle-voice/freestyle/blob/dacec015490471facef6fa8b6510913d7b201f71/<path>#L<n>`.

**What Freestyle is, for this comparison.** Freestyle is a desktop app (Electron, macOS/Windows/Linux). "Mobile support on iOS and Android is coming soon" (`F:README.md:54`). It has three voice paths:

- **Desktop + local whisper.cpp.** This is the closest analogue to ours. The renderer records the whole take and posts one WAV to a local `whisper-server` when the user releases the hotkey.
- **Desktop + cloud streaming** (Freestyle Cloud/Soniox, Deepgram, ElevenLabs, OpenAI) over a WebSocket.
- **Mobile (Expo, iOS first).** Cloud streaming only. The iOS keyboard extension cannot open the microphone, so it hands capture off to the main app (`F:specs/mobile-voice-keyboard.md:8-28`). There is no Android keyboard.

There is **no on-device VAD, no phrase segmentation and no spoken-command handling** anywhere in Freestyle. Most of what transfers to us is therefore UX, robustness and text-sanitising practice, not the audio/ML core.

## Summary: ranked recommendations

Ranked by user impact per unit of effort. "Agreed" means the item overlaps a change already agreed in the handoff. "New" means it is not in the handoff.

1. **Put a deadline on each segment, and let a tap during the drain discard what is left. New. Effort S–M.** Our STT IPC waits up to **120 s** per request (`T:app/src/main/java/com/termux/ai/TaiRuntimeServiceClient.java:35,53-54`). Segments run one after another on one thread (`T:.../voice/VoiceInputSession.java:98-99,244-247`), and the session does not end until every segment has come back (`VoiceInputSession.java:339-345`). A second tap on the voice key during that drain does nothing: `stop()` returns at once because `stopRequested` is already set (`VoiceInputSession.java:168`, reached from `T:app/src/main/java/com/termux/app/TermuxActivity.java:9805-9807`). A hung runtime therefore leaves the pill and the pressed key up for minutes. Freestyle arms a **15 s** watchdog after commit and salvages the take (`F:apps/electron/src/renderer/src/lib/dictation.ts:34,175-224`; `F:apps/electron/src/renderer/src/pages/app.tsx:1920-1939`). It also always offers a discard: Escape is registered globally while dictating (`F:apps/electron/src/main/index.ts:4490-4500`), and the pill has a cancel button (`app.tsx:2090-2096,3771`). Fix:
   - set a per-segment timeout of roughly 5 s plus 3× the audio length (base.en needs about 0.2–0.7 s per phrase on pong, plan §Measured), passed through `runtimeRequest(..., timeoutMs)`;
   - on timeout, fail the segment and stop;
   - when stop has already been requested, a tap calls `cancel()`.
2. **Level meter: measure from the noise floor and use a saturating curve. Agreed (#1), with specifics.** One correction to the handoff first. Today's meter is not linear. It is a fixed **−50…−10 dBFS** window (`T:.../voice/VoiceListeningIndicator.java:154,168-170`). Speech at −40…−25 dBFS fills only 0.25–0.63 of it, about 1–3 of 5 bars, which is why it "barely moves". Freestyle's pill does three things:
   - it subtracts a floor, then maps through `ceiling · (1 − e^(−gain·x))`, which is "steep at the bottom so a whisper already reaches roughly half height" (`F:app.tsx:104-122,157-161`);
   - it rises and falls fast (0.8 / 0.78 per frame), because "any lag … reads as the app being slow" (`app.tsx:87-95`);
   - it holds the peak per history slot so no syllable is lost between frames (`app.tsx:1321-1343`).

   For us: feed `20·log10(rms / noiseFloor)` (the VAD already tracks the floor, `VoiceActivityDetector.java:118-122`) into the same curve, with 0 dB at the floor and about 25 dB reaching roughly 90 % of the bar.
3. **Feedback: haptics, an optional start/stop tone, a "Transcribing…" state and a "didn't catch that" exit. Partly answers the handoff's open question #5 (ask the developer which one they meant).** Our voice path has no haptic or sound cue at all. The keyboard's key haptics are the only ones (`T:.../inappkeyboard/TermuxInAppKeyboard.java:1178`). The pill says "Listening…" and then shows each transcript (`VoiceListeningIndicator.java:121-124`). It has no state for "mic closed, still transcribing" and gives no signal when a phrase is dropped or comes back empty (`VoiceInputSession.java:329`). Freestyle:
   - plays start/stop sine blips (F4 347 Hz / C4 255 Hz, 125 ms, gain 0.16) behind a sound setting (`F:app.tsx:252-281`);
   - on mobile, uses light/medium/success/error haptics plus chimes (`F:apps/mobile/src/lib/audio/use-dictation.ts:180,225-227,253-254,298`);
   - has explicit pill states and exits: `recording`/`transcribing`, plus `delivered`/`cancelled`/`quiet` (`app.tsx:163-168,189-211`).

   One detail matters for us: **Freestyle keeps the start chime out of the audio.** It drops frames until the ~150 ms chime has played (`F:apps/mobile/src/lib/audio/chimes.ts:18,42-48`; `F:apps/mobile/src/lib/audio/use-resident-dictation.ts:250-271`). Our VAD keeps a 300 ms pre-roll, so an unmuted tone would be picked up. If we add a tone, discard the first ~150 ms of capture.
4. **Terminal-safety sanitiser: no newlines or control characters into the shell, and drop output that is not speech. New. Effort S.** Text for a terminal is written raw with `TerminalSession.write` (`TermuxActivity.java:13670`). `VoiceTerminalCleanup` only strips trailing `. ? !` and lowercases single words (`T:.../voice/VoiceTerminalCleanup.java:23-31`). An embedded `\n` would therefore run a command, and only "enter" should do that. Freestyle collapses ASR line breaks (`F:packages/stt/src/text.ts:43-51`) and warns its own agent that "pasted newlines EXECUTE as commands" in terminals (`F:apps/server/src/lib/editor/remix-prompts.ts:180`). Add two rules:
   - (a) map `\r`, `\n` and C0 control characters to a space for terminal targets;
   - (b) drop a segment whose text has no letter or digit left once `[...]`, `(...)`, `*...*` spans and punctuation are removed. The plan measured exactly these outputs: `[Music]`, `[BLANK_AUDIO]`, `(B)`, `*`, `¶¶`, `.` (plan §Measured, §Far-field). Freestyle's version of rule (b) is its filler/punctuation-only short-circuit (`F:packages/stt/src/post-process.ts:17-18,108-115`).
5. **Silence auto-stop: offer "until tap" and consider hold-to-talk. Agreed (#3), plus one new option.** Freestyle has **no** silence auto-stop. The user ends every take, either by releasing (the default, "Hold (push-to-talk)") or by pressing again ("Toggle") (`F:apps/docs/user-guide.mdx:16-27`). `SILENCE_MS = 1600` only flattens the waveform as a "mic may be muted" hint (`F:app.tsx:213,1345-1359`). That supports the agreed 5 / 10 / 30 s / until-tap setting with a 10 s default. A hold-to-talk mode on the voice key (press to record, release to close the segment) is new and suits "ls" … "enter key" well. It conflicts with long-press, which opens the system chooser today (`TermuxActivity.java:9798-9803`), so make it a setting. Effort M.
6. **User vocabulary and a post-ASR dictionary. Partly agreed (#2 adds "key"), otherwise new. Effort M.** The plan promises a user-editable terminal vocabulary (plan §Settings, "Terminal vocabulary"), but it is not wired. `biasPromptFor` returns only the hard-coded line or an explicit `prompt` (`T:app/src/main/java/com/termux/ai/TaiManager.java:1540-1544`), and `VoiceInputSession` never sends `prompt` (`VoiceInputSession.java:272-276`). Freestyle has:
   - a **Vocabulary** list turned into a `Terms: a, b, c.` prompt under a 900-character budget (`F:apps/server/src/lib/vocabulary-bias.ts:15,41-56`; `F:packages/stt/src/asr-bias.ts:25-33,64-115`);
   - a **Dictionary** of exact replacements applied after ASR, longest key first, on word boundaries (`F:apps/server/src/lib/dictionary-replacements.ts:11-33,35-71`).

   Our bias line is capped at 24 tokens (`T:app/src/main/java/com/termux/ai/WhisperDecoder.java:27`), and a 12-token line already lost "sudo apt" (handoff). A dictionary scales where the prompt cannot, for example `get status → git status` or spoken symbols. **A lesson from Freestyle's own bug:** its audit marks "vocabulary bias for local whisper" as fixed (`F:specs/transcription-audit.md:40-43`), yet at HEAD `buildAsrVocabularyBias` has no `local-whisper` case and returns `null` (`F:apps/server/src/lib/vocabulary-bias.ts:114-179`). The prompt plumbing in `whisper-local.ts` is therefore dead. Test the wiring end to end, from settings to the prompt tokens.
7. **A no-speech gate at decode. New. Effort S–M, needs measuring.** `WhisperDecoder` already suppresses `<|nospeech|>` (`WhisperDecoder.java:112-120`) and has the first-step logits in hand (`WhisperDecoder.java:139-141`). OpenAI's reference decoder drops a segment when `p(<|nospeech|>)` at step 1 is above about 0.6 and the average log-probability is low. Computing that softmax once per segment costs almost nothing, and it would catch VAD false triggers (a door, a cough) before they are typed. Freestyle does not do this. It decodes greedily without VAD, and lists Silero VAD only as an optional lever against "trailing-silence hallucinations" (`F:specs/transcription-audit.md:84-86,96-99`). The threshold is unverified for the ACFT graphs, so measure it with the reference decoder first.
8. **Friendly error text and per-phrase logs. Agreed (#4), plus new error copy. Effort S.** A failure shows `voice_input_stopped`/`voice_input_fallback` with the raw code or message, for example "stt_failed: …" (`TermuxActivity.java:13700-13707`; `T:app/src/main/res/values/strings.xml:1781-1782`). Freestyle maps codes and platform errors to plain text: "Microphone access is off", "No microphone found", "Transcription is temporarily unavailable" (`F:dictation.ts:36-53`). For logs, Freestyle's pattern fits the agreed per-phrase line. It logs STT ms, total ms and audio duration (`F:apps/server/src/routes/transcribe.ts:435-437,568`). Its analytics carry durations and never text (`transcribe.ts:570-585`). It does log raw text, truncated to 120 characters, at debug level (`transcribe.ts:435-437`). Keep to the handoff rule and never log the transcript.

Lower priority: an optional **transient audio focus** so music pauses while listening. This is Freestyle's "duck"/"pause" playback mode, which defaults to `off` (`F:apps/electron/src/shared/audio-playback.ts:1-8`; `F:app.tsx:1740-1748`). On Android it would be `AudioManager.requestAudioFocus(AUDIOFOCUS_GAIN_TRANSIENT)`. It would cut the speaker noise that a loudspeaker feeds into the bottom mic. New, effort S, needs the developer's opinion.

## Side-by-side

| Axis | termux-launcher (on-device) | Freestyle (desktop local whisper unless noted) |
|---|---|---|
| Platform / surface | Android, Termux in-app keyboard voice key; text into terminal, palette or drawer | Electron global hotkey plus floating pill; pastes into the frontmost app (`F:apps/docs/user-guide.mdx:6-37`) |
| Capture | `AudioRecord`, `VOICE_RECOGNITION`, 16 kHz mono PCM16, 30 ms frames (`VoiceInputSession.java:131-138`) | `getUserMedia` with echo cancellation, noise suppression and AGC **off** (`F:apps/electron/src/renderer/src/lib/recorder.ts:49-53`); `MediaRecorder` webm, then decoded and resampled to 16 kHz WAV at stop (`recorder.ts:171-216`) |
| Mic lifecycle | Opened per session, released on stop, silence, hide, pause or failure (`VoiceInputSession.java:167-190,229-241`) | Stream kept for reuse, tracks stopped at commit (`recorder.ts:29-47,139-144`); mobile keyboard flow keeps the mic "armed" across phrases (`F:apps/mobile/src/lib/audio/use-resident-dictation.ts:1-30`) |
| VAD / endpointing | Energy VAD, 9 dB over adaptive floor, 300 ms pre-roll and tail, pause 400–1200 ms closes a segment, < 300 ms voiced dropped, window cut at quietest frame (`VoiceActivityDetector.java:31-41,116-203`) | **None.** User ends the take (hold or toggle). Takes < 250 ms discarded (`F:app.tsx:1850-1859`); mobile < 350 ms (`use-resident-dictation.ts:51,361-370`) |
| Auto-stop | 2.5 s silence (`VoiceActivityDetector.java:33`) → to become 5/10/30 s/until tap | None; silence only flattens the waveform after 1.6 s (`F:app.tsx:213,1345-1359`) |
| Engine | LiteRT Whisper ACFT (`encode`/`decode`), int8 DRQ, CPU XNNPACK 4 threads, in `:tai_runtime` (plan §CPU, §Where it runs) | whisper.cpp 1.8.5 `whisper-server` child process on localhost, default threads (`F:apps/server/src/lib/whisper/constants.ts:199-201`; `F:apps/server/src/lib/whisper/server.ts:212-224`) |
| Models | base / small, `.en` or multilingual, 5 s / 10 s windows (plan §Model) | base-q5_1 57 MB, small-q5_1 181 MB, large-v3-turbo 1.6 GB (`constants.ts:25-56`) |
| Streaming / partials | Phrase-by-phrase: each segment transcribed and typed as soon as it closes | Batch per take for local whisper (`supportsStreaming` false, `F:apps/server/src/lib/streaming/providers/whisper-local.ts:51-53`); cloud partials dropped in the pill (`F:apps/electron/src/renderer/src/lib/streamer.ts:6-9`) |
| Decoding | Greedy, no fallback, `<|notimestamps|>`, 4-gram×3 repetition guard (`WhisperDecoder.java:28-29,129-184`) | Greedy, `temperature_inc=0` (no fallback ladder), `no_timestamps` (`whisper-local.ts:92-104`) |
| Bias | Fixed 10-word shell line after `<|startofprev|>`, ≤ 24 tokens (`WhisperDecoder.java:25-27,80-92`) | User vocabulary → "Terms: …" (900 chars) for OpenAI/Groq; **not reaching local whisper at HEAD** (`vocabulary-bias.ts:114-179`) |
| Language | Forced: setting → keyboard layout → system locale → en (`T:.../voice/VoiceLanguage.java:93-104`) | Setting list; local sends `language` or `"auto"` (`whisper-local.ts:101`); audit says auto means English locally (`F:specs/transcription-audit.md:103-106`) |
| Post-processing | Trailing `.?!` stripped, single word lowercased, segments joined with a space (terminal only) (`VoiceTerminalCleanup.java:23-37`) | Sanitise (wrapping quotes, trailing duplicate paragraph, `<fin>`), collapse ASR line breaks (`F:packages/stt/src/text.ts:1-57`); optional LLM cleanup with filler-only skip (`F:packages/stt/src/post-process.ts:108-148`); dictionary; per-app formats; plugins |
| Commands | Whole-segment "enter", "tab", "escape", "backspace", "space", "ctrl c" → keys (`T:.../voice/VoiceCommand.java:34-57`) | None |
| Insertion | Keys through `dispatchKeyValue`; text to interceptor or `TerminalSession.write` (`TermuxActivity.java:13649-13675`) | Paste or clipboard via native helpers (`F:apps/electron/src/main/paste.ts`), ordered queue (`F:dictation.ts:242-255`) |
| Ordering | `VoiceResultSequencer` (`T:.../voice/VoiceResultSequencer.java:21-29`) | Queue drained in order (`F:dictation.ts:163-167,242-255`) |
| Level meter | 5 bars, −50…−10 dBFS fixed window, instant rise, ×0.8 decay (`VoiceListeningIndicator.java:154-175`) | FFT energy in 80–4000 Hz, floor subtract plus saturating curve, peak-hold history (`F:app.tsx:83-161,1321-1343`); mobile: `sqrt(rms·3.2)` (`F:apps/mobile/src/lib/audio/recorder.ts:83-99`) |
| Feedback | Pressed voice key, pill, transcript text | Start/stop tones (setting), check-mark / cancelled / quiet exits, notices (reconnecting, retrying, sign-in, limit, unavailable) (`F:app.tsx:170-211,252-281`); mobile haptics plus chimes |
| Cancel | Only on activity destroy (`TermuxActivity.java:13625-13630`) | Escape and pill button discard the take (`F:apps/electron/src/main/index.ts:4490-4500`; `F:app.tsx:2090-2096`) |
| Warm-up / idle | `sttWarm` when the mic opens (`VoiceInputSession.java:156-158`); idle unload 2 min (plan §Memory) | Server started when the model is chosen (`server.ts:120-136`); keep-alive default 10 min, max 10 (`server.ts:19-20,58-102`); per-dictation pre-warm does nothing for local (`F:apps/server/src/routes/transcribe.ts:620-650`) |
| Timeouts / retry | 120 s IPC per segment; first failure ends the session and falls back to the Android recognizer before any text (`TermuxActivity.java:13700-13709`) | 15 s client watchdog → REST salvage; 120 s server request; retry once after restarting a crashed server (`whisper-local.ts:68-85,109`); ≤ 3 crash restarts with a 30 s stability window (`server.ts:16-18,326-362`) |
| Permission | Rationale dialog, request code, fallback to the system recognizer on deny (`TermuxActivity.java:13595-13617`) | Error text by exception name (`F:dictation.ts:46-53`); mobile arm flow (`use-resident-dictation.ts:297-306`) |
| Logging | Failures only (handoff) | Debug timings plus truncated text, analytics with durations, local history DB of raw and cleaned text (`transcribe.ts:435-437,550-566`) |

## Per-axis detail

### 1. Audio capture

**Freestyle.** It asks the browser for raw audio. `echoCancellation`, `noiseSuppression` and `autoGainControl` are all `false` (`F:apps/electron/src/renderer/src/lib/recorder.ts:49-53`). A missing selected device falls back to the default (`recorder.ts:61-73`). A generation counter drops a stream that a newer session has superseded (`recorder.ts:74-79`). The whisper.cpp path records `webm/opus` through `MediaRecorder`, then decodes, downmixes and resamples to 16 kHz WAV only at stop (`recorder.ts:109-127,171-216`). In parallel, an AudioWorklet converts to 16 kHz PCM16 in ~80 ms chunks with nearest-sample decimation (`F:apps/electron/src/renderer/src/lib/pcm-processor.ts:9-58`). That copy is what the streamer keeps for REST salvage and replay after a reconnect (`streamer.ts:171-177,282-295`). Mobile uses `expo-audio`'s stream at 16 kHz mono Int16, downmixed and linearly resampled in JS (`F:apps/mobile/src/lib/audio/resample.ts:13-60`).

**Ours.** `AudioSource.VOICE_RECOGNITION`, 16 kHz mono PCM16, a buffer of ≥ 8 frames, read in 30 ms frames on a dedicated thread (`VoiceInputSession.java:131-164,198-226`). There is no platform AGC, which matches Freestyle's "no processing" choice.

**Takeaways.**
- The "raw, no processing" choice is the same on both sides. It explains why levels are low (handoff: −40…−25 dBFS), and the fix belongs in the meter (recommendation 2), not in capture. The plan found that +11 dB of software gain did not help base.en (plan §Far-field).
- Freestyle's "mic may be muted" hint (`F:app.tsx:1345-1359`) has an Android-specific counterpart that matters more to us. With the Android 12+ privacy toggle, or when another app holds the mic, `AudioRecord` delivers silence (inferred from Android behaviour; not verified on pong). A short "Microphone is muted or in use" pill message after ~1.5 s of all-zero frames would explain a session that seems dead.
- *Does not transfer:* device pickers, MediaRecorder/webm, WebAudio resampling. Our capture is already in the model's native format.

### 2. VAD, endpointing and segmentation

**Freestyle has none on any path.** Local whisper gets the whole take in one request. Silero VAD is listed only under "Optional latency levers (not currently needed)" (`F:specs/transcription-audit.md:94-99`). Endpointing is the user: hold/release or toggle (`F:apps/docs/user-guide.mdx:16-27`). Takes under 250 ms are discarded (`F:app.tsx:1850-1859`), and under 350 ms on mobile (`use-resident-dictation.ts:51,361-370`; `use-dictation.ts:32,327`).

**Ours** is well ahead here. The energy VAD has an adaptive floor, 300 ms pre-roll and tail, a pause-closed segment, the < 300 ms-voiced drop and the window cut at the quietest frame (`VoiceActivityDetector.java:31-41,116-203`). This is what makes phrase-by-phrase insertion and "ls" … "enter" possible.

**Observations on our VAD (not from Freestyle):**
- The floor starts at `NOISE_FLOOR_CAP` = 0.02 (−34 dBFS) (`VoiceActivityDetector.java:39,77`). The first voiced threshold is therefore 2.8 × 0.02 ≈ −25 dBFS. That sits at the loud end of the speech range the handoff measured. The floor drops as soon as a quieter frame arrives, so this matters only for a first word spoken immediately at the tap, and the pre-roll recovers most of it. The per-phrase logs (agreed #4) should include voiced ms, so a clipped first word can be spotted. *Inferred, needs a device log.*
- The session silence timeout counts from the last voiced frame and fires once (`VoiceActivityDetector.java:135-141`). The agreed "until tap" setting only needs a sentinel that disables it.

### 3. Model and inference

- **Engine.**
  - *Freestyle* runs whisper.cpp 1.8.5 `whisper-server` as a child process. It is started with only `--model/--port/--host` (`F:apps/server/src/lib/whisper/server.ts:212-224`), so whisper.cpp picks its default thread count. Readiness is an HTTP poll every 250 ms (`server.ts:266-281`). The startup timeout is 90 s and kills the process (`server.ts:231-243`). A crashed server restarts up to 3 times, and the counter resets after 30 s of stability (`server.ts:16-18,326-362`).
  - *Ours* runs LiteRT in `:tai_runtime` with our own mel, tokenizer and decoder, plus the XNNPACK shim for real 4-thread inference (plan phase 2).
  - *Does not transfer:* whisper.cpp process management, ggml q5 models, large-v3-turbo (1.6 GB, "~6 GB" RAM, `F:apps/server/src/lib/whisper/constants.ts:46-55`).
- **Decoding.** Both decode greedily with no temperature fallback and no timestamps (`F:apps/server/src/lib/streaming/providers/whisper-local.ts:92-104`; `WhisperDecoder.java:129-153`). Freestyle's audit gives the reasons: "beam-5 buys ≲1.5pp WER … at ~2×+ decode cost, and greedy hallucinates less" (`F:specs/transcription-audit.md:84-86`). We add a decode-time repetition guard (`WhisperDecoder.java:172-184`). Freestyle only removes a duplicated trailing paragraph afterwards (`F:packages/stt/src/text.ts:1-16`). Recommendation 7 (the no-speech probability gate) goes beyond both.
- **Streaming vs batch.** Freestyle's local path is batch per take. Its cloud path streams, but the desktop pill drops partials (`streamer.ts:6-9`). Our phrase-level insertion is the better fit for a terminal, where "ls" should land before "enter key" is said.
- **Warm-up and residency.**
  - *Freestyle* starts the server when a model is chosen (`server.ts:120-136`) and unloads it after a keep-alive (default 10 min, max 10, `server.ts:19-20,58-102`). Its per-dictation `pre-warm` route does nothing for local whisper (`F:apps/server/src/routes/transcribe.ts:640-644`).
  - *Ours* warms on every mic open and unloads after 2 min idle. That is right for a phone, where memory is shared with the chat model.
- **Bias.** See recommendation 6. Freestyle packs terms with de-duplication and keeps packing after a term that does not fit, "rather than giving up entirely" (`F:packages/stt/src/asr-bias.ts:95-111`). That is a useful rule if our user vocabulary outgrows 24 tokens: pack whole words, never cut a word's BPE pieces in half. Today `WhisperDecoder.prompt` truncates the token array mid-word (`WhisperDecoder.java:85-86`).
- **Language.** Freestyle local sends `language` or `"auto"` (`whisper-local.ts:101`). Its audit says "auto" means English on the local path because auto-detect doubles encoder cost on short clips (`F:specs/transcription-audit.md:103-106`); the code sends `"auto"` anyway. Our forced language from setting, layout and locale (`VoiceLanguage.java:93-104`) is the better choice for short clips, as the plan notes.

### 4. Post-processing, commands and insertion

- **Sanitising.** Freestyle's `sanitizeTranscriptText` strips wrapping quotes and trailing `<fin>` tags and removes a duplicated last paragraph (`F:packages/stt/src/text.ts:53-57`). `collapseAsrLineBreaks` turns single newlines into spaces (`text.ts:43-51`). Ours does not handle newlines or control characters (recommendation 4).
- **Hallucinations.** Neither side has a blocklist such as "Thank you." Freestyle's only guard is the filler/punctuation-only skip before LLM cleanup (`F:packages/stt/src/post-process.ts:17-18,108-115`), plus returning blank output when the transcript is empty (`F:apps/server/src/routes/transcribe.ts:477-479`). We have measured hallucination shapes (plan §Measured), so a non-speech-only filter (recommendation 4b) is cheap and well founded.
  - A phrase blocklist ("thank you", "thanks for watching") is **not** recommended. Those can be real dictation. The no-speech gate (recommendation 7) is the principled version.
- **LLM cleanup.** Freestyle's cleanup is central: tones, per-app formats, prompt-injection wrapping (`post-process.ts:14-15`), and a fallback to raw text on error (`post-process.ts:139-148`).
  - *Does not transfer.* Terminal commands must not be rewritten. The plan keeps STT off the GPU so it never contends with the chat model (plan §CPU). A cleanup pass would also add a chat round trip per phrase. One case might justify it later: an opt-in for long prose dictated into an agent's prompt.
- **Dictionary.** Freestyle applies replacements after ASR, longest key first, whole words (CJK excepted), and counts usage (`F:apps/server/src/lib/dictionary-replacements.ts:11-71`). This transfers directly (recommendation 6). It is also the natural home for the plan's "spoken symbols (dash, slash, pipe)" item (plan §Terminal cleanup).
- **Commands.** Freestyle has none. Our whole-segment rule (`VoiceCommand.java:34-57`) plus the agreed "key" suffix is the only safeguard, and it is unique to us.
- **Insertion.** Freestyle pastes through native helpers and fails closed when a redaction plugin's hook is present but the deliver call fails (`F:app.tsx:226-245`). *Does not transfer*, except the principle: when in doubt, do not type.

### 5. UX

- **Activation.** Freestyle defaults to hold (push-to-talk), with toggle as an option. It falls back to toggle on Linux without input-device access (`F:apps/docs/user-guide.mdx:16-31`). We only have tap-to-toggle plus auto-stop. See recommendation 5 for hold-to-talk.
- **Meter.** See recommendation 2. Two further details worth copying:
  - Freestyle zeroes the meter while not capturing, "so the UI doesn't jitter from room noise" (`F:apps/mobile/src/lib/audio/use-resident-dictation.ts:150-153`). Ours already zeroes non-voiced frames under 0.05 (`VoiceListeningIndicator.java:172-173`).
  - The waveform history is peak-held per 75 ms slot (`F:app.tsx:79-83,1321-1343`). With 5 bars, a peak-hold on the decay would do the same job.
- **States.** Freestyle separates `initializing`, `recording`, `transcribing` and `error`, plus exits `delivered` (check mark), `cancelled` and `quiet` (`F:app.tsx:163-211`). Its notice policy is deliberate: "a spinner that cries wolf on the happy path is just noise", so notices appear only for real faults (`app.tsx:170-184`). For us:
  - show a small pending mark while `submitted > delivered`;
  - show "Transcribing…" after the mic closes;
  - show a brief "didn't catch that" when a phrase is dropped or comes back empty.
- **Sound and haptics.** See recommendation 3. Freestyle plays the start cue before ducking, so the duck does not attenuate it (`F:app.tsx:1735-1738`). It plays the stop cue after restoring volume (`app.tsx:1827-1838`). If we add audio focus and tones together, keep that order.
- **Settings Freestyle exposes that we lack:**
  - activation mode;
  - sound on/off;
  - media playback off/duck/pause;
  - mic device;
  - keep-alive minutes;
  - vocabulary;
  - dictionary;
  - cancel-button visibility (`F:apps/electron/src/shared/dictation-prefs.ts:1-7`; `F:apps/electron/src/shared/pill-cancel.ts:1-9`; `server.ts:58-73`).

  Of these, sound, vocabulary and dictionary transfer. Activation mode transfers as the hold-to-talk option. Our TAI settings already cover keep-alive.

### 6. Robustness

- **Timeouts and cancel.** See recommendation 1. This is the most concrete rough edge in our code.
- **Session supersession.** Freestyle guards every async step with a session or generation counter: `session !== this.session` (`F:dictation.ts:338-378`) and `RecorderSupersededError` (`recorder.ts:74-79`). Ours creates a new `VoiceInputSession` per press, and `mVoiceInput != null` blocks a second one (`TermuxActivity.java:13533`). That is equivalent and simpler.
- **Failure policy.** Before any text, ours falls back to the Android recognizer; after text, it shows a notice only (`TermuxActivity.java:13700-13709`). That is better than Freestyle's local path, which only shows an error dialog or offers to switch to cloud (`F:apps/electron/src/renderer/src/lib/local-whisper-recovery.ts:1-16`). Keep ours. The weak spot is the error text (recommendation 8).
- **Retry.** Freestyle retries a local inference once after restarting a crashed server (`whisper-local.ts:68-85`). Our equivalent would be one retry of a segment that failed with a runtime-process death, not with a refusal. *Inferred to be rare.* Measure before building.
- **Memory.** Freestyle has no memory accounting. It shows only a static "RAM required" label (`constants.ts:6-15`). Our residency and eviction (plan §Memory) goes further. Nothing to take.
- **Logging and telemetry.** Freestyle logs at debug: received bytes and header, bias, STT ms plus truncated raw text, post-process ms and total (`F:apps/server/src/routes/transcribe.ts:88-92,404,435-437,541-543,568`). It sends analytics events with durations and never text (`transcribe.ts:326-341,459-464,570-585`), and keeps a local history of raw and cleaned text (`transcribe.ts:550-566`). For the agreed per-phrase log, mirror the timing fields (segment ms, voiced ms, transcribe ms, text length, command or text). Also add the runtime's `timings` block, which already carries mel, encode and decode ms and steps (`T:app/src/main/java/com/termux/ai/WhisperSttRuntime.java:190-202`). Keep the "never the transcript" rule; unlike Freestyle, we have no user-visible history to justify storing it.

## What does not transfer

- **Cloud streaming, WebSocket session transport, reconnect and replay, partial transcripts** (`streamer.ts`, `F:apps/server/src/routes/stream.ts`). Our engine is local and phrase-segmented.
- **LLM cleanup, tones, per-app formats, plugins, the Remix agent.** Terminal input must not be rewritten, and the GPU/chat-contention rule in the plan applies.
- **whisper.cpp server lifecycle and the model catalog.** Different engine and memory envelope.
- **Paste/clipboard delivery and frontmost-app context.** We type into a known target.
- **The iOS keyboard hand-off** (`F:specs/mobile-voice-keyboard.md:8-28`). Android lets our keyboard (inside our own activity) own the mic.
- **Hold-to-talk as the only mode.** On a touch keyboard it competes with long-press. Offer it only as an option (recommendation 5).

## Where we are ahead (keep)

- VAD segmentation with padding, the min-voiced drop and window cuts. Phrases land as spoken.
- Spoken command keys under a whole-segment rule.
- Forced language from layout and locale.
- A decode-time repetition guard.
- Model warm-up at mic open.
- Ordered delivery.
- Automatic fallback to the Android recognizer.
- Memory-aware loading.
