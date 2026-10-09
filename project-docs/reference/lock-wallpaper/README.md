# Lock-screen preview cutout

`reference-nothing-picker.png` is the developer's reference: Nothing's wallpaper and style page on pong, 2026-10-03.

Codex CLI 0.160 drew the generic, vendor-neutral lock-screen cutout on 2026-10-03. The brief is `codex/PROMPT.md`, and the sources and previews are in `codex/`.

The app copies are `app/src/main/res/drawable/lock_preview_overlay.xml` (viewport 360×800) and `lock_digit_0..9.xml` / `lock_digit_colon.xml` (56×96 / 20×96). In them the placeholders became white: #FF00FF is opaque, and #808080 is white at 0.6 alpha. Every phone's lock screen draws white over the wallpaper, so this colour stays the same in light and dark themes. The preview composes the current time from the digit glyphs in the clock area (x=28, y≈120–260).
