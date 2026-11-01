# Pane borders: colour, focus colour and a steady glow (#206, 2026-11-01)

Settled with the developer on 2026-11-01. Ships in v2.0.0-pre.3; built now on its own branch off
`v2-redesign`, on today's editor sheet (the order-and-geometry round,
`appearance-editor-order-and-geometry-2026-11-01.md`, comes later and must keep this subcard).
No reply on #206 until pre.3 is out.

## What the user gets

A **Pane borders** subcard under the Terminal surface card, opened from a link row after Text
size, Contrast and Opacity. The Terminal entry in Elements scrolls the Terminal card to that row
(the existing section target), one tap from the subcard. The editor has no card stack: the subcard
is shown in the sheet's place like Elements, its header carries a back chevron, and both the
chevron and system Back re-open the Terminal card at the current detent, scrolled to the Pane
borders row. Done commits through the editor as every other row does; Undo and the unsaved-changes
dialog cover the new keys (`AppearanceSnapshot` capture, restore and signature).

Rows, top to bottom:

1. **Preview**: three small frames, *Normal*, *Focused*, *Attention*, drawn with the real rim and
   glow drawables the panes use (`PaneRim`, `SharedRim`, `PaneAttentionGlow` held steady). It shows
   what the terminal will get at this theme, density and Rim setting; Attention is static (no
   pulse) and only explains that it outranks the user's colours.
2. **Rim**: None / Hairline / Bright, the same key as the Rim row in Look and material (one key in
   two places, both with the editor's source indication). With Rim None the preview honestly shows
   no border.
3. **Border colour**: Theme outline (today's line) / Primary / Secondary / Tertiary / Custom.
   Custom reveals a **Custom colour** row: a hex field with a live swatch, accepting `#RRGGBB` and
   `#AARRGGBB`, alpha visible in the swatch. This is the editor's first free colour input.
4. **When focused**: the same chooser plus *Same as border*; default Primary (today's behaviour).
   Applies to the focused pane of a split and to floats, which the code already styles as focused;
   a single pane never takes the focus colour.
5. **Glow**: Off / Steady. Steady draws the existing attention glow held at its bright end
   (`setPulsing(false)`) round the pane in the Normal and Focused states, fixed strength (the
   drawable's peak). **Glow colour**, shown only when Steady: Same as border / the chooser above.
6. **Reset pane borders**: back to theme outline, Primary focus, glow off.

Not offered: breathing glow (continuous repaint, against the at-rest drawing rule; the attention
pulse is an event), border width, per-pane or per-page exceptions, a gesture on the border to open
the subcard (the line already carries drag and resize).

## Rules

- **Precedence** stays `PaneBorderStyle.decide`: Attention first, then Focus for a focused split
  pane or float, else Rim. The user's colours fill the Rim and Focus kinds; Attention keeps
  `colorError` and its pulse.
- **Scheme.** Primary / Secondary / Tertiary resolve exactly the way the editor's Tint colour
  swatches do (`EditorViews.color`, the activity theme's `colorPrimary` / `colorSecondary` /
  `colorTertiary` through `MaterialColors`), so the swatch the user picks and the line the pane
  draws are the same colour. `PaneRim.palette` moves onto that same helper for the focus colour;
  attention keeps `colorError`. Never a system palette resource read directly.
- **Where the colour lands.** The pane's resting border is the shared glass rim following the Rim
  look; the border colour tints that rim (and the `GlassRimDrawable` the Focus kind builds on
  glass), it does not add a second line. `PaneRim.lineColour()` (`colorOutline` at alpha 150) is
  the Theme outline choice and the Docked opening's line.
- **Storage.** Plain terminal-only keys in `TermuxAppSharedPreferences`, one set shared by Terminal
  and Terminal-minimal, like text size; not in the surface inheritance table. Keys: border colour
  mode and custom ARGB, focus colour mode and custom ARGB, glow on/off, glow colour mode and
  custom ARGB. Defaults reproduce today's rendering exactly, so an existing install sees no change.
- **Cost.** A steady glow invalidates once per render, never per frame; `Decision.equals` includes
  the colour, so cache invalidation follows.
- **Policy pure and tested.** The colour resolution (mode + custom + scheme → ARGB, with "Same as
  border") and the extended `decide` live in `PaneBorderStyle` (or a sibling) with unit tests;
  `PaneRim` applies the answer.

## Waves

1. Policy, storage and rendering: `PaneBorderStyle`, `PaneRim`, prefs keys, scheme-aware roles,
   steady glow for Rim and Focus; tests. No editor.
2. The subcard: Terminal card row, Elements shortcut, the preview strip, the chooser (reused for
   border, focus and glow colour), the hex field, Reset, Undo/Done/dirty integration.

## Verification owed

- pong: a crimson custom border, Primary focus, steady glow on, single pane and a split, light and
  dark theme, scheme chrome on and off; Rim None shows no border; Reset restores today's look.
- Emulator: the subcard at font scale 1.3 and 360 × 640 (no clipped chooser, hex field usable).
- The reporter's device (Solana Saga) after pre.3.
