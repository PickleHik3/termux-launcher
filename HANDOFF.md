# feat/key-popup — Halo Float pressed-key popup

## Done
- Whole of phase K in one commit (this commit): pointer preview hook in `Pointers`/`Keyboard2View`
  + UPSTREAM.md entry; `KeyPopupGeometry`, `KeyPopupPalette`, `KeyPopupOverlayView`,
  `KeyPopupController`
- Setting (`in_app_keyboard_key_popup`, default on) wired through prefs, fragment, XML, strings
- Tests: `Keyboard2ViewKeyPopupTest` (7), `KeyPopupGeometryTest` (12), `KeyPopupPaletteTest` (5),
  `KeyPopupControllerTest` (7) — all green

## In progress
- nothing

## Next
- Waydroid gate: visual fidelity, top-row float over the terminal, floating keyboard, two thumbs,
  light theme

## Gotchas
- The popup is driven by `Pointers`, never by a second reading of the gesture.
- The overlay lives in `android.R.id.content`, so it is never clipped by the keyboard container.
