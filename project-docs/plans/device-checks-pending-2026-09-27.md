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
4. **Voice redesign**: the list gets added here when the build is merged.

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
