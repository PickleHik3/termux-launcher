# Appearance editor and Settings: wireframes

Wireframes for [`project-docs/active/appearance-and-settings-rework-2026-10-24.md`](../../../active/appearance-and-settings-rework-2026-10-24.md)
§2–§4: the one-rule Appearance editor and the Material 3 Settings. They exist so the developer can
judge the layout before any code and a reviewer can critique the measurements.

- `index.html` is the source. It draws every screen clean, annotated and (where it scrolls) at full
  length, side by side. It needs only Google Fonts (Roboto, Roboto Mono, Material Symbols Rounded).
  One CSS pixel is one dp. Every red dimension label is **measured from the drawn element** when
  the page loads, not typed, so a label can't disagree with the drawing.
- One phone alone: `index.html?s=<1..7>&v=<clean|anno|full>`, plus `&fs=1.3` for font scale.
- The PNGs are those phones rendered by headless Chromium at 411×914 dp ×2.625 (pong's density),
  so they come out 1079×2399 px (411 × 2.625 = 1078.9). Full-length PNGs are taller.
- Colours are placeholder M3 dark tonal roles (CSS variables at the top of `index.html`), not a
  brand. Glyphs for the Look stops and for Tray / Beads / Strip are stand-ins until Codex draws them.

## The PNGs

| File | Shows |
|---|---|
| `01-editor-rest.png`, `-annotated` | Resting editor: the live Workspace in a frame at ×0.84, every tappable surface outlined, the dots and the word for the half being edited, the pill (orientation chip · Undo, disabled until a change · Done · palette). |
| `02-dock-card.png`, `-annotated` | Dock tapped: the frame shrinks to ×0.44 with the dock outlined; the Dock card shows tier 1 (Opacity with its own-value dot, Tint, Height, Shown, Place) and the Style row peeking. The ⋮ menu is open: Reset, Also on Home. |
| `02-dock-card-full.png` | The same card unrolled: tier 2 (Style, Design chips, Blur, Corners, Margin) and the collapsed More row. The card is 882dp tall in all. |
| `02-dock-card-fontscale-1.3.png` | The same at 1.3× font scale: tier 1 just fills the visible card and the scroll cue is gone. |
| `03-pill-card.png`, `-annotated`, `-full` | Palette tapped: the Look slider with five labelled stops (Custom faint), Wallpaper (Change + shown switch), Theme System / Light / Dark, Black; then tier 2 and More. No surface is outlined. 922dp tall in all. |
| `04-home-card.png`, `-annotated`, `-full` | The frame slid to Home (wallpaper, clock widget, icon grid, Home dock), the dots and word say Home, the Home card is up: Icon size, Label size, Glass widgets; tier 2; More. |
| `05-settings-root.png`, `-annotated`, `-full` | Settings root: large title, search, Your launcher (Home, Apps & dock, Terminal, Linux apps), Everywhere (Appearance, Keyboard, Notifications, On-device AI), System (General) as grouped tonal cards. System is below the fold; the full view shows it (1064dp). |
| `06-linux-input.png`, `-annotated` | Linux apps › Input: section headers in primary, the filled segmented control Trackpad / Touchscreen / Direct touch, switch rows with leading icons, a two-way segmented row. |
| `06-linux-input-fontscale-1.3.png` | The same at 1.3×: "Touchscreen" and "Direct touch" truncate. |
| `07-setup-guide.png`, `-annotated` | Set up Linux apps, step 2 of 4 (Distro): progress line with step names, headline, one sentence, three option cards, Back and Next fixed at the bottom. |

## Measurements

All values in dp (text in sp). Phone 411×914, system bar 24 at the top, gesture area 24 at the bottom
(placeholder insets).

| Element | Value |
|---|---|
| Side gutter, every page | 16 |
| Editor frame, resting | ×0.84 → 345×768, top 40 (16 below the system bar), corner 20, gutter 33 |
| Editor frame, card up | ×0.44 → 181×402, top 32; whole launcher stays visible |
| Surface outline | 2 wide, 2 outside the surface (drawn inside for edge-to-edge surfaces: keyboard, Home) |
| Half dots row | 16 tall, dot 6, active dot 16×6 in primary, word in label-medium |
| Pill | 48 tall, corner 24, padding 4, gap 4, items 40 tall; Done filled primary; 8 above the gesture area; 297 wide |
| Card | full-width sheet, top corners 24, top edge at 462 (452 tall), content visible 364 |
| Card header | 64; handle 32×4 at 8 from the top; icon 24 at x16; title 22sp at x56; ⋮ and ✕ 48 touch |
| Icon column | 24 icon at 16 from the edge, text starts at 56 (cards and Settings alike) |
| Slider row | 72 = 8 + 24 label line + 40 slider touch; track 4, thumb 20, value label right-aligned at 16 |
| Switch row / inline segmented row | 56; switch 52×32 |
| Wide segmented row | 88 = 8 + 24 + 8 + 40 + 8 |
| Design chips row | 104; three chips 56 tall, corner 12, gap 8 |
| Look row | 120 = 8 + 24 + 40 slider + 40 stop glyph (20) and label; 5 stops on a 315 track |
| Tier divider | 1, with 8 above and below (17) |
| Segmented control | 40 tall, gap 2, outer ends corner 20, inner corners 8; selected primary-container, others surface-container-highest, no outline, no check |
| ⋮ menu | 200 wide, items 48, corner 12 |
| Settings large app bar | 152; title 28sp; back 48 touch |
| Settings search | 56, corner 28 |
| Section header | 48 (20 above, 8 below), title-small 14sp in primary, inset 32 |
| Group card | corner 24, surface-container-high, rows 72 (two-line), hairline 1 inset 56, chevron 24 at 16 from the edge |
| Settings block with segmented | 72 row + 40 control + 16 below = 128 (the annotation's "block 112" stops at the control) |
| Guide top bar | 64; progress 4 tall in four segments, gap 4; step names label-medium |
| Guide option card | 72, corner 16, gap 8, monogram 40, radio 20 |
| Guide bottom bar | 80; Back text button 40 tall; Next filled 40 tall, min 96 wide |

## Open questions found while drawing

1. **"Pill" is taken.** The glossary's **Pill** is the dictation card. The editor's pill needs its
   own term before code and strings are written.
2. **Undo and Done while a card is up.** Drawn: the pill hides under the card, so finishing takes ✕
   then Done (two taps). Alternatives: Done in the card header, or the pill riding the card's top edge.
3. **The pill card's title.** Drawn as "Appearance" (the door's name). "Look" would collide with the
   slider inside it.
4. **×0.44 is too small to judge text.** Terminal text renders at about 5dp, so Text size and Contrast
   can't be judged in the frame. Option: dragging the card taller crops the frame to a strip around
   the selected surface at a larger scale.
5. **Slide versus drag.** The frame slides between halves with a horizontal swipe, and a surface is
   moved by dragging it. At ×0.84 almost the whole frame is a surface. Which gesture wins needs a
   rule (edge swipe only? the dots as a switch?). The dots and word are this drawing's idea; the
   spec names no indicator.
6. **Custom on the Look slider.** Custom is "never a gate", so the thumb shouldn't land on it, yet it is
   drawn as a fifth stop on a slider. Option: four stops, with Custom shown as a state label only
   when reached.
7. **Hidden surfaces.** With Shown off, a surface loses its outline and can't be tapped back. It needs a
   ghost (dashed) outline in the frame, so the way out of Shown off is visible.
8. **Font scale on cards.** Rows must take a minimum height, not a fixed one. At 1.3× the Dock card's
   tier 1 exactly fills the visible card (366 of 364), so the peek that says "this scrolls" is lost;
   at 1.5× (estimated, not drawn) tier 1 itself is cut.
9. **Three long segments truncate at 1.3×.** Trackpad / Touchscreen / Direct touch get 114 each and
   two of them ellipsize. Options: autosize text, or radio rows when the third label is long.
10. **The Input page's current titles break the copy rules.** "Use Android keyboard" (three words),
    "OSK auto-show" (jargon), "Keyboard over display" (the retired word Display),
    "Touchscreen (Default, custom behavior)". Drawn as Phone keyboard, Open on tap, Keyboard space
    (Overlay / Resize), Touchscreen.
11. **System below the fold.** With the 152 large app bar and the search field, General starts below
    914. A 112 medium bar still leaves it about 6 short.
12. **Guide step count depends on the source.** The Termux X11 repo source needs no distro step (3
    steps); the nix edition has no source at all. The progress line must count the steps of the route
    taken. The source names ("Termux X11 repo", "proot distro") are jargon for step 1.
13. **Dock per half against "one Dock design".** The spec marks the Dock card ½, but the glossary says
    one dock serves both halves dressed by one Dock design. Which Dock values are per half (Opacity,
    Tint?) and which stay shared (Style, Design?) needs saying, or the card should mark them.
14. **What a tap on Home hits.** Drawn: the whole Home surface (wallpaper) is the target. A tap on a
    widget or an icon: Home card, or the widget's own menu?
15. **Landscape in the frame.** The orientation chip turns the frame landscape; in a portrait editor
    that frame rests near ×0.36. Not drawn.
16. **Place.** Drawn as Top / Bottom per the brief; the spec says "Place (edge)", which in landscape
    may mean left / right too.
17. **Icon picks are placeholders.** Material Symbols were chosen for the drawing; Look (contrast) and
    Black (contrast_square) read alike.
