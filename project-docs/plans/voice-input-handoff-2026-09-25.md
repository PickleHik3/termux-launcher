# Handoff: voice input, speech models and the model importer (2026-09-25)

Branch `dev` (not pushed), 45 commits since `bff9b049`. pong has the build from `0007c5c6`
(installed 15:16). The `tlstore` worktrees (`tl-wt-tlstore-*`) belong to another session.

## What this session shipped

**Voice input (in-app keyboard, on-device Whisper)** — design and status in
`whisper-voice-input.md` (Refinement pass, Feedback and polish, Replay rig, Settings sections).
- Refinement batch: meter in dB above the noise floor, spoken keys need " key" (setting "Bare
  command words"), silence auto-stop 5/10/30 s/until tap, per-phrase info logs (never the text),
  per-segment deadline + tap-to-cancel while transcribing, terminal sanitiser (no newlines/control
  chars into the shell, drop `[Music]`-style output).
- Feedback: start/stop blips ("Voice sounds"), haptics follow key haptics, 180 ms lead-in dropped.
- Opt-in "Polish dictation with local model" (Gemma E4B/E2B via TAI, raw text on any failure).
- Pill: close ✕, command chip ("⏎ Enter"), pending "…" mark.
- VAD tuned on pong (bottom mic, no AGC: speech frames −45…−52 dBFS, room −62): absolute floor
  −70 dBFS, onset 9 dB / hold 6 dB, floor = 10th percentile of the last 5 s of all frames (the
  old non-voiced-only floor deadlocked under a TV), 90 ms energy smoothing (fan), VoiceGain
  peak-normalises each segment (up to +40 dB) before transcription.
- "c key" / "ckey" alone = Ctrl+C (small.en drops "control").

**Speech model picker** — Keyboard → Voice → Speech model (`SpeechModelPreferencesFragment`,
`TaiSpeechModels`): lists installed speech models by capability, tap to use, delete/cancel,
download dialog in plain words, activate only on successful download, window switch keeps the old
file until the new one is in, stale model id falls back. TAI page keeps one pointer row.

**Model importer** — guided flow (`TaiImportFlow`, `TaiImportFit`, `TaiImportNames`,
`TaiImportMessages`): link or file → detect → summary card with "Will it run on this phone?" →
progress → Ready with Try it / Use as default; plain errors with details behind a button. Review
and acceptance checks: `project-docs/tai-importer-user-review.md`.

**Research and tooling**
- `project-docs/freestyle-voice-comparison.md`, `project-docs/parakeet-stt-research.md` (incl. the
  replay-rig comparison and the pong probe numbers).
- Replay rig: `./gradlew :app:testDebugUnitTest --rerun --tests '*VoiceReplayRig'
  -Dvoice.replay.in=<wav or dir> -Dvoice.replay.model=base|small|parakeet
  [-Dvoice.replay.terminal=false] [-Dvoice.replay.keep=true]` → `app/build/voice-replay/report.txt`.
  Keep `--rerun` (Gradle otherwise reuses the result). Needs `~/.cache/termux-launcher/{venv,
  whisper,parakeet}` (models pulled from pong / Hugging Face; venv has numpy, ai-edge-litert,
  scipy, piper-tts).
- Synthetic test set: `scripts/voice_eval_make.py` (256 clips, 4 Piper voices × near/far/fan/TV at
  pong levels) and `scripts/voice_eval_score.py manifest.json label=report.txt …`.
- Device probe (debug build only): `com.termux.ai.SpeechGraphProbe` via `app_process` as the adb
  shell user (it aborts inside the app sandbox).
- Recordings of the developer's voice live only in `~/.cache/termux-launcher/voice-clips/` —
  never commit them.

## Measured results worth remembering

- Replay rig, 256 synthetic clips: keys / typed commands / dictation WER —
  base.en 41/80, 29/80, 24.5 %; small.en 59/80, 37/80, 17.5 %; Parakeet v3 29/80, 13/80, 14.8 %.
- small.en recovers far-field long sentences that base.en outputs as "[Music]" (real pong clip).
- Parakeet v3 on pong: loads in LiteRT 1.4.2, encode 173 ms per 5 s, `decode_1` 1 ms, +1.2 GB RSS.

## Remaining items (priority order)

1. **Command words redesign** (developer asked; proposal only): drop the mandatory " key",
   whole-phrase commands: enter / next line / send, tab, escape, space, backspace, delete word /
   delete N words (Ctrl+W×N), delete all / clear line (Ctrl+U), clear screen (Ctrl+L), control c /
   cancel, up / previous command, undo that (remove last dictated phrase). Numbers as words or
   digits. Test in the rig first (add phrases to `voice_eval_make.py`).
2. **Short far words dropped** ("ls", "clear" at desk distance, every model): the 300 ms voiced
   minimum in `VoiceActivityDetector` / `WhisperSegmenter`. Tune with the rig; watch hallucinations.
3. **Real-voice recordings**: debug builds write `cache/voice-debug/session-*.pcm`; pull with
   `scripts/voice_pull_sessions.sh` (not yet device-verified) and add `.expect` files so the rig
   has a regression set in the developer's voice.
4. **Parakeet as an optional dictation engine** (not the command engine): `ParakeetSttRuntime`
   next to `WhisperSttRuntime` (port the TDT loop from `scripts/parakeet_replay_server.py`), pad
   the *audio* to 5 s (zero feature padding makes it repeat), catalog entry + tokenizer sidecar,
   memory budget (+1.2 GB), picker shows it automatically (capability-based). Consider a
   replacement dictionary ("get status" → "git status", "pseudo" → "sudo").
5. **Terminal bias hurts prose** (drops "like", noise → "ok"): a setting, or bias only short
   phrases.
6. **Device checks not done**: Gemma polish end to end; importer Try it, Cancel during a local
   copy, and the Qwen2.5-0.5B `.task` that fails in Waydroid with "Chosen prefill work group size
   exceeds available state entries" (check on pong); speech picker window-switch failure path and
   old-file deletion order.
7. **Freestyle recommendations not done**: user vocabulary + post-ASR dictionary (#6), no-speech
   probability gate (#7), friendly error text (#8), hold-to-talk option, transient audio focus.
8. **Housekeeping**: flaky in full runs (pass alone) — `TerminalIOPreferencesDataStoreLazyModeTest`,
   `KeyboardPreferencesFragmentTest.theVoiceRowsWriteThroughToTheSharedPreferencesAndRejectStrays`.
   Decide whether the per-second VAD debug log and the debug segment/session dumps stay.
   `LiteRtEmbeddingRuntime` likely has the single-thread XNNPACK problem Whisper had.

## Practical notes

- pong: `ANDROID_ADB_SERVER_PORT=5038`, serial `100.101.173.85:5555`. Large `adb push`/`install`
  fail on the Tailscale link; send with `cat f | ssh termux 'cat > ~/f'` then
  `adb shell 'run-as com.termux cat files/home/f > /data/local/tmp/f; pm install -r /data/local/tmp/f'`.
  Termux's remote shell is fish (wrap in bash); after a reinstall sshd may need restarting via
  run-as. Installs are allowed without asking; never type into pong while the developer uses it.
- Waydroid (`~/.claude/skills/waydroid/wd`): the universal debug APK runs; `screencap` returns
  empty files, use `uiautomator dump`; `run-as` is blocked in the container.
- Workers merged this session repeatedly needed small compile/test fixes — always build after a
  merge (`./gradlew testDebugUnitTest :app:assembleDebug`).
