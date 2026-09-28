# Device checks owed (parked 2026-09-27)

Parked until the PC is free to build. Run everything below on the first APK built from `dev` after
d14d2b68. Remember: after an install, the TAI API stays down until the launcher is opened.
Run the memory watchdog for any model session.

## New in this build (not on pong yet)
1. **System role fold** (d14d2b68). 1a and 1b dropped 2026-09-27: codegemma kept crashing pong, so no codegemma tests. `tai chat` against codegemma with a system prompt.
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

## Results on pong v0.2.40 (2026-09-27)
- **Pass:**
  - 1c: gemma-4-e2b honours the system prompt.
  - 2: keyboard off toggle (the developer).
  - 3: first sound after 0.4 s matches what was heard.
  - 4a: pill top-right, Warming up chip. Once, the pill vanished about 2 s in; not reproduced.
  - 4d, 4e: nothing reaches the terminal while dictating, and the cleanup runs on stop.
  - ✓ types once with no Enter.
  - Copy works.
  - Bin and swipe discard.
  - Early ✓ (E) and continuing (f2).
  - 4f3: Dictate key (the developer).
  - 4g: refusal guard kept "What is the capital of France" as dictated.
- **Fail / redesign:**
  - A, first try: the panel showed clean text, then the raw text with "um", then clean again.
  - Long-press raw/cleaned does nothing. Replace it with an undo button.
  - Copy should be an icon.
  - B: "ls dash la" stayed "Ls dash La", because under 4 words skips the cleanup. "L S dash La" became "LS dash La", because "LS" doesn't match the command list.
  - The waveform freezes on its last frame when listening stops.
  - × both stops and closes.
- **Dropped:** 1a and 1b (codegemma).

## Round 2 checks (voice pill and panel, after the pong run)
Watch logcat tag `VoiceSessionCleanup`. A command now logs `outcome=fallback:skipped:command` only
if it reaches the cleanup; normally it never does.
1. **Pause / resume / ×.** The pill shows pause and × while listening.
   - a. Pause: phrases still transcribing arrive (resume is greyed out meanwhile), the cleanup runs on
      its own, and pause becomes a microphone (resume).
   - b. Resume carries on the same text. The next pause cleans all of it. The voice key and the
      Dictate key do the same: start, pause, resume.
   - c. × while listening, while transcribing, while cleaning and while waiting: each time the text
      is gone, the pill closes at once, and nothing reaches the terminal. Swiping the card does the same.
2. **Panel icons.** Three icons: undo, copy, ✓. No bin, and no "Copy" text. TalkBack reads each one.
   - a. Undo shows only after a cleanup or a command formatting. It puts back the text as heard and
      turns into redo. Redo brings the cleaned text back. ✓ and Copy use whichever is showing.
   - b. After Kept as heard or Ready there is no undo.
   - c. Long-pressing the text does nothing.
3. **Bug A (versions).** With cleanup on, dictate several phrases, one with "um". The panel only goes
   raw (dim) → shimmer → cleaned. The text never switches back to raw by itself. Also try it with a
   resume after the cleanup has landed: the carried text stays as shown, and new phrases join it.
4. **Bug B (commands).** With cleanup off, then on:
   - a. "ls dash la" gives `ls -la`, and so does "L S dash La". The status says "Formatted as a command".
   - b. "git status" gives `git status`, "cd slash home slash user" gives `cd /home/user`, and "cat
      file dot txt" gives `cat file.txt`. There's no capital and no full stop.
   - c. "Make sure the tests pass before you push" is prose: with cleanup on it is cleaned as usual.
   - d. Undo on a command gives the words as heard.
5. **Waveform rest.** On pause, the bars ease down to a dim line in about a third of a second, and
   they rise again on resume. With animations off (developer options) they jump. At rest, GPU
   profiling or gfxinfo shows no ongoing frames from the pill.

## Round 2 results
Checks 1–5 pass (the developer, 2026-09-28).

## Round 3 checks (voice pill and panel, branch voice-round3)
1. **As heard vs cleaned.** With cleanup on, dictate a few phrases with an "um". While listening and
   cleaning, the text is italic and dimmer (secondary colour); once cleaned, it turns upright in the
   normal text colour, with the change marks fading as before. Undo brings back the italic as-heard
   text, and redo the upright cleaned text. With cleanup off, and after "Kept as heard", the text is
   upright and normal. Check the light theme, the dark theme, and the glass (wallpaper) look: the
   italic text must stay readable on each.
2. **Action pill.** Undo, copy and ✓ sit together in one long rounded bar across the bottom of the
   panel, spread evenly, in a slightly raised surface colour. They never leave it. Without undo, copy
   and ✓ share the bar. There is no bin. TalkBack still reads each control.
3. **Pill width.** While listening, with no text yet, the pill hugs the waveform, "Listening…", pause and ×: no empty
   stretch between "Listening…" and pause. It stays 36 dp tall, and "Warming up" still fits. With the
   panel up, the pill row sits at the end of the card, on the side the card hangs from.
4. **Mic sensitivity.** Keyboard → Voice input → Mic sensitivity shows Normal and High (default
   Normal). Logcat `VoiceInputSession` says `mic sensitivity normal|high` at every start.
   - a. Normal, quiet office: speak softly at arm's length. It should pick up at least as often as
     before; compare with a normal voice.
   - b. High: soft speech comes through. With a colleague talking or a video playing near the phone,
     count the phrases that appear without you speaking; they are expected on High, rarer on Normal.
   - c. Keyboard clicks, a fan or the air conditioning alone must not open a phrase on either setting.
5. **Screen stays on.** Set the phone's screen timeout to 30 s.
   - a. Dictate, then let a long cleanup run and land: the screen does not dim while listening,
      cleaning, or while the text waits.
   - b. Leave the waiting text untouched: after about 3 minutes the screen dims and sleeps normally,
      and the text is still in the panel when you come back.
   - c. Touch the pill (not a button) while it waits: the 3 minutes start over.
   - d. ×, ✓, Copy, a swipe, and leaving the app each let the screen time out normally at once
      (30 s after the last touch).
6. **Drag handle.** A short grab handle, the floating keyboard's, shows under the panel once there
   is text.
   - a. Drag it: the pill and panel follow the finger and stay inside the place, under the status
      bar and above the keyboard. Swiping the handle sideways moves the card; it never discards it.
   - b. Let go low on the screen: new lines grow the panel upward, and it never goes off the screen
      (fewer lines with the keyboard up).
   - c. Start the next dictation: the pill comes back where it was left. Rotate: landscape has its own
      place (the corner until moved there).
   - d. Double-tap the handle: back to the top right corner, and the next dictation starts there.
   - e. Swiping the card itself (not the handle) sideways still discards.
