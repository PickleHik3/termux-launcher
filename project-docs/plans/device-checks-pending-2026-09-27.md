# Device checks owed (parked 2026-09-27)

Parked until the PC is free to build. Run everything below on the first APK built from `dev` after
d14d2b68. Remember: after an install, the TAI API stays down until the launcher is opened.
Run the memory watchdog for any model session.

## New in this build (not on pong yet)
1. **System role fold** (d14d2b68): `tai chat` against codegemma with a system prompt.
   - a. The first message succeeds through the fold instead of failing with "System role not supported".
     If it still fails, the error is raised at send time rather than at `createConversation`, and the
     retry has to move to the send path (`LiteRtTaiRuntime.generate`).
   - b. A second chat with codegemma goes straight to the folded form, with no failed first attempt and no
     extra delay.
   - c. A model that accepts a system role (e.g. gemma-4-e2b) is unchanged: same speed and same kind of output.
2. **Keyboard off toggle** (c209a314, `project-docs/keyboard-off/SPEC.md`):
   - a. Built-in keyboard: turn it off from the palette. Tap the terminal, tap in `htop` and `vim`,
      then restart the app. The keyboard stays down every time. The keyboard key brings it back.
   - b. The same with the Android keyboard selected in Settings.
   - c. Add `tool:keyboard.toggle_enabled` to the extra-keys row and flip it both ways.
   - d. While it's off, open the height editor in Settings. The keyboard shows for editing and goes down afterwards.
3. **`tai speak` first-sound time** (2c5c3584): the reported time to first sound matches when audio is
   actually heard.
4. **Voice redesign** (ddebbf92, then the panel flow and the Dictate key on top). Watch logcat tags
   `VoiceSessionCleanup` (`cleanup: … outcome=`), `VoiceTextPolisher` and `VoiceInputSession`.
   - a. The pill sits top-right of the pane area and is 36 dp. The Warming up chip shows until speech is
      ready, and its ring is a sensible size.
   - b. The waveform scrolls and stays flat in a quiet room. It rises only with speech, and the floor re-learns
      after about 1.5 s of quiet.
   - c. The panel grows downward and shows up to 7 lines. Older lines scroll off the top, and the newest
      line stays visible at the bottom. Typewriter reveal, and a shimmer while phrases are transcribing.
      With reduced motion, text appears at once. In landscape with the keyboard up, the panel shrinks
      so it doesn't cover the keyboard.
   - d. Nothing reaches the terminal while you dictate: the shell line stays empty in bash, fish,
      Claude Code and codex. The pill's × (or the voice key) stops listening. Phrases still
      transcribing arrive, and the text waits in the panel. The × then disappears.
   - e. With cleanup on (Polished, then Light), stopping runs one pass. The panel marks what changed,
      and the text waits; it is never inserted on its own. Long-press toggles raw/cleaned. With cleanup
      off, the text waits as heard, with the status Ready.
   - f. Buttons: ✓ types the text once at the cursor and doesn't press Enter. Check in bash and in a TUI
      prompt (Claude Code, codex). Copy puts it on the clipboard. The bin discards it, and so does swiping
      the card. Press ✓ or Copy while still listening: listening stops, the cleanup runs, and then the
      button acts on the cleaned text.
   - f2. Tap the voice key again while text is waiting. The new dictation continues the same text, and
      the cleanup at the end covers all of it.
   - f3. **Dictate key**: put `tool:voice.dictate` on the extra-keys row. It shows a microphone cap and
      toggles dictation on each place, with the pill in the same corner:
      - Home: with the drawer search or palette open, ✓ types into it. With a widget text field focused,
        ✓ types into the field. Otherwise ✓ copies and shows "Nothing here takes typing…".
      - Terminal: with the keyboard down or Android's keyboard selected, ✓ types into the focused shell.
        With the palette open, ✓ types into the palette.
      - Display: with a display running, ✓ types into the focused X window (built-in keyboard and Android's).
        With no display server, ✓ copies; nothing goes into the terminal behind.
      - Leaving the app while dictating stops the mic, and the text is still waiting when you come back.
   - g. Refusal guard: dictate a question ("what is the capital of France"). It is kept as dictated, not
      answered.
   - h. Command rule: "ls -la" is treated as a command, and ✓ inserts it with no added capital or full stop. A sentence that only contains a command word
      somewhere in the middle is not.
   - i. Without Gemma E2B installed, "Kept as heard" appears. A session under 4 words skips the cleanup and
      waits as heard (Ready). A session where nothing was heard closes at once.

## Carried over (already installed, never verified)
5. Read aloud on a terminal selection.
6. Silero dictation. Pause YouTube first, then check logcat for `voiced decision: silero|energy`.
7. Dictated numbers appear (Parakeet digit fix 88b0a6be).
8. The terminal no longer flashes (d433acb0).
9. Parallax with the managed wallpaper, in portrait.
10. Minimal mode, and the dock and keyboard travelling with the slide.
11. Model centre UI: downloads, pause and resume, the notification.
12. Settings restore on pong (already verified on HTC).
13. Importer fact labels.
14. Padding fill corners.
