# Task: a generic lock-screen overlay for a wallpaper preview card

You are drawing artwork for an Android launcher's wallpaper picker. The picker shows a large
portrait preview card of the LOCK SCREEN with the wallpaper behind it. On top of the wallpaper we
need a brand-neutral "lock screen cutout" that reads as "this is your lock screen" on every phone
(Pixel, Samsung, Nothing, OnePlus...), so it must not copy any vendor's clock style or icons.
`reference-nothing-picker.png` in this folder shows the idea (left card), but do NOT copy its
clock font or layout literally.

Write everything into this folder (`out/`):

1. `out/lock_overlay.svg` and `out/lock_preview_overlay.xml` (Android VectorDrawable), viewport
   360 x 800 (the card's aspect, 9:20). Contents, all strokes/fills in placeholder colours:
   - a thin status-bar hint at the very top (a tiny left time-ish bar and three small right dots),
   - a short date line placeholder (a rounded bar ~84 x 10) above the clock area,
   - NO clock digits in this file: leave the clock area empty (top third, left-aligned at x=28
     like most lock screens, roughly y 120..260); the app draws the real time there with the
     digit glyphs below,
   - two round shortcut buttons at the bottom corners (diameter ~44, at bottom-left and
     bottom-right, ~28 from the edges and ~40 from the bottom) with a minimal generic glyph inside
     each (a flashlight-ish bar on the left, a camera-ish circle on the right) — simple, neutral,
   - a short rounded "unlock hint" bar centred near the bottom (~60 x 4).
   Keep it airy and minimal; it sits on top of busy animated backgrounds, so shapes must read at
   small sizes (the card is ~150 dp wide on screen).

2. Clock digit glyphs, one VectorDrawable per glyph: `out/lock_digit_0.xml` .. `out/lock_digit_9.xml`
   and `out/lock_digit_colon.xml`, plus matching SVGs. One consistent, generic geometric style:
   tall, light-to-regular weight, rounded terminals, monoline paths drawn as FILLED outlines
   (not strokes, so they scale cleanly), tabular widths. Digit viewport 56 x 96, colon 20 x 96.
   They must be legible from 24 dp tall up to 96 dp tall.

3. `out/preview.png`: a montage that composes the overlay at 360x800 over a mid-grey gradient with
   the time "12:45" assembled from your digit glyphs in the clock area, plus a second copy of the
   same over a dark background, side by side. Use any tooling available (Python stdlib, rsvg-convert
   if present). Also `out/digits.png` showing all 11 glyphs on one row at 96 px tall.

Colours: use ONLY placeholders — #FF00FF for primary marks (digits, date bar, shortcut rings) and
#808080 with fill/stroke alpha 0.45 for secondary marks (status hint, unlock hint, inner glyphs).
In the VectorDrawables write the placeholders as literal colours; we map them to theme attrs later.

Validate: every .xml must parse as XML with the android namespace, every .svg must parse; viewport
numbers must match the sizes above. Print a short summary of the files at the end.
