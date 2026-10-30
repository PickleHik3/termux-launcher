# Appearance editor: card anatomy and transitions

Source of truth: design system `termux-launcher-v2` (tokens.css, DESIGN.md). Prototype: `index.html`.
Units are dp unless marked. "Inferred" means no design-system source; the reason is on the same line.

## 1. Editor layout (412 × 915 phone)

| Part | Value | Source |
| --- | --- | --- |
| Editor bar top | safe top + 4 = 56 | inferred |
| Editor bar | 48 high, 12 side gap, full capsule, Base Look glass | `--tl-touch-target`, `--tl-side-gap`, `--radius-pill`, `--tl-glass-*` |
| Editor bar items | Appearance · Sheets · Wallpaper · Done | ADR 0017 cards; Sheets has no surface in the frame |
| Editor bar item | 40 high, 12 side padding, `--text-base` | inferred |
| Done | primary fill `--accent`, ink `--accent-on`, weight 500 | design system `.btn--primary` |
| Frame top | full detent top + 4 = 112 | inferred |
| Frame scale s | (half top − 8 − 24 − frame top) / 915 = 0.45 | inferred: whole page stays above the half detent; DESIGN.md says 60%, which sits under half at 915 |
| Frame corner | 24 on screen (24 / s in launcher units) | `--radius-lg` |
| Frame label | 24 high, 4 under the frame, `--text-sm`, `--muted`, page name + page dots | inferred |
| Page dots (label) | 6 × 6, gap 4, on = 1, off = 0.4 opacity | inferred |
| Ring | 2 stroke `--accent` over 4 stroke `--bg` 70%, 3 outside the surface, radius = surface radius × s + 3 | inferred |
| Ring, Appearance card | frame outset 4, radius 28 | inferred |
| Ring, page cards | frame inset 4, radius 20 | inferred |
| Ring, Sheets and Wallpaper | hidden (no surface in the frame) | inferred |
| Surface hit area in the frame | surface + 16 launcher units each side (≈ 7 on screen) | inferred |

## 2. Detents

| Detent | Sheet top | Visible height | Shows |
| --- | --- | --- | --- |
| Full | bar bottom + 4 = 108 | 807 | header, chips, body scrolls |
| Half | round(915 × 0.608) = 556 | 359 | header, chips, Look, legend strip |
| Peek | 915 − (24 + 48 + 28) = 815 | 100 | handle + title row |

- The sheet extends to the screen bottom; the body keeps `safe bottom + 16` bottom padding.
- Tap the header (handle zone or title row): one detent in the current direction; the direction flips at full and at peek.
- Drag the header: the sheet follows 1:1; beyond full or peek it moves at 0.35 × finger.
- Release over 600 px/s: next detent in the drag direction. Release under 600 px/s: the nearest detent.
- The release velocity is the spring's initial velocity.

## 3. Card anatomy (all ten cards)

### Sheet

| Part | Value | Source |
| --- | --- | --- |
| Sheet fill | `--surface` (colorSurfaceContainer) | tokens; colorSurfaceContainerLow has no token, assumption |
| Top corners | 24 | `--radius-lg` |
| Elevation | `--elev-raised` | DESIGN.md: the sheet is a raised surface |
| Side padding | 16 | `--space-4` |

### Header

| Part | Value | Source |
| --- | --- | --- |
| Handle zone | 24 high, full width, a button | user brief |
| Handle | 32 × 4, `--radius-pill`, `--muted` | inferred (M3 drag handle) |
| Title row | 48 high, left padding 16, right padding 4 | user brief, `--tl-touch-target` |
| Title glyph | 24, `--fg` | `--tl-glyph` |
| Glyph to title | 16 | `--space-4` |
| Title | `--text-xl` 22, weight 500, `--leading-tight`, one line, ellipsis | DESIGN.md card title |
| ⋮ | 48 × 48 button, glyph 24, glyph 16 from the edge | `--tl-touch-target` |
| ⋮ menu | caption "Editing <cell>", item "Reset card" | inferred |
| Wallpaper page | handle zone kept as 24 blank; Back button 48 at left 4 in place of the glyph | inferred: same header height |

### Chips

| Part | Value | Source |
| --- | --- | --- |
| Row | padding 4 top, 12 bottom, 16 sides; gap 8; total 56 | inferred |
| Chip | 40 high, capsule, 1 outline `--border`, padding 8 left 12 right | `--tl-chip-height`, `--radius-pill` |
| Chip content | 18 glyph, 8 gap, label `--text-sm` 500, chevron 18 | inferred |
| Chip max width | 50% of the row − 4; label ellipsis | inferred |
| Chip 1 | Where it applies: Everywhere · <page name> | ADR 0017 |
| Chip 2 | What it applies to: All glass · <element> | ADR 0017 |
| Chip narrowed (not Everywhere / All glass) | fill primary container, ink on-primary-container, no outline | design system selected state |
| Chip with one option | no chevron, `aria-disabled` | Wallpaper page "What" chip |
| Chip menu | caption is the chip's question; radio items with a check | inferred |
| Default, Appearance | Everywhere · All glass | inferred |
| Default, page card | <that page> · All glass | inferred |
| Default, element card | Everywhere · <element> | inferred |
| Where options, Appearance | Everywhere · Home page · Terminal page | inferred |
| What options, Appearance | All glass · Status bar · Dock · Terminal · Keyboard · Widgets · Sheets | inferred |
| What options, page card | All glass · the elements on that page | inferred |

### Row order (glass cards)

| # | Row | Control |
| --- | --- | --- |
| 1 | Look | stacked segments: Clear · Mist · Tint · Solid · Custom |
| 2–4 | Frost, Opacity, Tint | the legend strip |
| 5 | Colour | stacked segments: Primary · Secondary · Tertiary |
| 6 | Corners | horizontal slider 0–32 dp |
| 7 | Depth | horizontal slider 0–40 |
| 8 | Edge | horizontal slider 0–40 dp |
| — | More | disclosure row |
| 9 | Grain | horizontal slider 0–100% |
| 10 | Rim | inline segments: None · Hairline · Bright |
| 11+ | the element's own rows | see §4 |

- Moving Frost, Opacity or Tint sets Look to Custom in the same cell.
- Picking Clear, Mist, Tint or Solid writes its Frost, Opacity and Tint into the cell.
- Clear: Frost 2 / Opacity 14 / Tint 0. Mist: 8 / 34 / 0. Tint: 8 / 34 / 40. Solid: 0 / 78 / 0. Source: SurfaceMaterials curves at intensity 50; Tint 40 from GlobalGlass.
- Frost has 8 stops: 0, 2, 4, 8, 12, 19, 28, 48 (blur dp). Stop 3 = 8 = Base Look; inferred stop list.

### Rows

| Part | Value | Source |
| --- | --- | --- |
| Row min height | 56 | user brief |
| Touch target | 48 | `--tl-touch-target` |
| Inline row | name column 104, gap 16, control fills, value column 48 | inferred |
| Stacked row | name line, gap 8, control; padding 8 top and bottom | inferred |
| Row name | `--text-base` 16, `--leading-tight`, one line, ellipsis | DESIGN.md row title 16sp |
| Value | `--font-mono`, `--text-sm`, `--muted`, right aligned, no wrap | DESIGN.md: metrics are monospace |
| Source dot | 8 × 8, 6 after the name | user brief |
| Dot, element on page | `--accent` (primary, blue) | ADR 0017 |
| Dot, page | `--warn` (place ink, amber) | ADR 0017 |
| Dot, element | `--tl-tertiary` #CFBCFF dark / #65558F light | ADR 0017; inferred tone |
| Dot, shared value | none | ADR 0017 |
| Dot accessible name | row name gets "<Row>, from <cell>" | colour is not the only cue |
| Long-press row name | 500 ms, cancels after 8 px movement; also right-click and Enter on the name | user brief |
| Row menu | caption "<Row>: from <cell>", item "Reset this row" (disabled when this cell has no value) | inferred |

### Legend strip

| Part | Value | Source |
| --- | --- | --- |
| Track | 28 wide, 120 long, corners 14 | `--tl-slider-track`, `--tl-slider-length` |
| Track fill | inactive `--border-soft`, active `--accent` | design system legend slider |
| Thumb | 4 high line, 8 inset from each side, `--accent-on`, 6 from the fill end | `--tl-slider-thumb` |
| Track to label | 6 | user brief "6dp gap" |
| Column | 96 wide; columns 8 apart | inferred: label "Opacity 100%" fits |
| Label | name + dot + value on one line, `--text-sm` | user brief |
| Strip padding | 8 top, 12 bottom | inferred |

### Horizontal slider

| Part | Value | Source |
| --- | --- | --- |
| Track | 16 high, corners 8, in a 48 hit area | inferred (M3E slider track) |
| Fill / thumb | as the legend strip, rotated | design system |

### Segments

| Part | Value | Source |
| --- | --- | --- |
| Segment | 48 high, gap 2, outer corners 20, inner corners 8 | `--tl-segment-*` |
| Label | `--text-sm`, one line, ellipsis, min-width 0 | DESIGN.md |
| Unselected | `--border-soft` fill, `--fg` ink | design system |
| Selected | primary container, on-primary-container ink | design system |

### Switch and link

| Part | Value | Source |
| --- | --- | --- |
| Switch | 52 × 32, capsule; off `--border` / thumb `--muted` 16; on `--accent` / thumb `--accent-on` 24 | design system `.switch` |
| Link row | 56, name left, chevron 24 right | inferred |

### Picker row (replaces the custom bodies)

| Part | Value | Source |
| --- | --- | --- |
| Layout | stacked: name line, then a horizontal rail | inferred |
| Rail | gap 8, bleeds to the sheet edge, 16 inset, scrolls sideways | inferred |
| Option | 80 wide: preview 80 × 56 corners 12 on `--border-soft`, caption `--text-sm` under it | inferred |
| Selected option | 2 inset ring `--accent`, caption `--fg` | inferred |
| Status bar | Clock face: Digits · Flip · Analog; Position: Top · Floating · Bottom | replaces the clock face and position pickers |
| Terminal | Cursor trail: None · Short · Long; Effect: None · Scanlines · Glow | replaces the trail and effect previews |
| Dock | Style: Docked · Floating; Design: Tray · Beads · Strip · Peek | replaces the style and design pickers |
| Wallpaper page | Image: Blue · Dusk · Moss · Graphite | inferred sample images |

### More

| Part | Value | Source |
| --- | --- | --- |
| Row | 56, 1 hairline `--border-soft` above, 8 above that | inferred |
| Chevron | 24, rotates 0 → 180 when open | inferred |
| Open state | kept per card for the editor session | inferred |

## 4. The ten cards

| Card | Opens from | Ring | Main rows | More: own rows |
| --- | --- | --- | --- | --- |
| Appearance | editor bar | frame outset | glass rows | Motion (Classic · Mist), Wallpaper link |
| Home page | bare wallpaper, Home | frame inset | glass rows | Grid (4 × 5 · 5 × 6), Page dots, Wallpaper link |
| Terminal page | bare wallpaper, Terminal | frame inset | glass rows | Pane gap 0–12, Extra keys, Wallpaper link |
| Status bar | status pill | pill | glass rows | Clock face, Position, Height 28–48, Metrics |
| Terminal | terminal panes | panes | glass rows | Font size 10–16 sp, Cursor trail, Effect |
| Dock | dock | dock | glass rows | Style, Design, Icon size (48 · 54 · 60), Height 28–48, Margin 0–24 |
| Keyboard | extra keys or keyboard | extra keys + keyboard | glass rows | Style (Full · Floating · Split), Key opacity, Key corners 0–20 |
| Widgets | a widget | widget | glass rows | Padding 0–24, Labels |
| Sheets | editor bar | none | glass rows | Scrim 0–60%, Handle |
| Wallpaper page | editor bar or Wallpaper link | none | Image, legend strip (Dim 0–60%, Blur 0–24) | Colours from wallpaper |

All ten use the same header, chips and row kit, with the same paddings and heights. Only the title, the glyph, the chip options and the rows under More differ.

## 5. Value cells

- A cell is (where, what): where ∈ Everywhere, Home page, Terminal page; what ∈ All glass, an element.
- The chips pick the cell that edits write to.
- Shown value and dot: the first cell that holds the row, in this order: element on page → element everywhere → page → shared.
- The dot names the cell that holds the shown value; no dot = shared.
- Reset this row deletes the row from the edited cell; the shared cell resets to the default instead.
- Reset card resets every row the edited cell holds, in touch order, 30 ms apart.
- The frame is a live preview: every surface on both pages renders its resolved values.
- Assumed precedence: element before page. ADR 0017 does not say in the brief; confirm.

## 6. Motion tokens

All movement is a spring (mass 1). Settle time is to within 2% of the distance.

| Token | Damping ratio / stiffness | ω (rad/s) | Settle | Use | Source |
| --- | --- | --- | --- | --- | --- |
| spatial-fast | 0.6 / 800 | 28.3 | ≈ 235 ms, ≈ 10% overshoot | body settle, ring, value spring-back, menu scale, More chevron | `--m3e-spring-spatial-fast` |
| spatial-default | 0.8 / 380 | 19.5 | ≈ 255 ms, ≈ 1.5% overshoot | detents, sheet rise and fall, Wallpaper page, launcher scale, actions sheet | `--m3e-spring-spatial-default` |
| effects-fast | 1.0 / 3800 | 61.6 | ≈ 95 ms | every fade out | `--m3e-spring-effects-fast` |
| effects-default | 1.0 / 1600 | 40 | ≈ 145 ms | every fade in | `--m3e-spring-effects-default` |
| page | 1.0 / 324 | 18 | ≈ 320 ms | page settle | `--tl-page-omega` 18, SettleSpring.java |
| colour state | 170 ms, `cubic-bezier(0.4, 0, 0.2, 1)` | — | 170 ms | segment, chip, switch, editor bar fills | `--motion-fast`, `--tl-ease-in` |
| switch thumb | 220 ms, `cubic-bezier(0.3, 0.2, 0.05, 1)` | — | 220 ms | switch thumb travel | `--motion-base`, `--ease-standard` |

- Exits fade on effects-fast, about 65% of the effects-default entrance.
- Replaced: reveal 180, overlay fade 200, exit 220, move 240, enter 260 ms. Each path now uses one of the springs above.
- Reduced motion: spatial springs jump to their end value; fades still run; drags still follow the finger.

## 7. Transitions

| Rule | Trigger | Persists | Moves | Motion | Detent |
| --- | --- | --- | --- | --- | --- |
| a. Card → card | tap another surface in the frame | sheet, detent, handle, ⋮, chip outlines | header title and glyph and chip labels fade out, then in relabelled; body fades out, then fades in rising 8 → 0; ring moves to the new surface | label and body out: effects-fast; in: effects-default; body rise: spatial-fast; ring: spatial-fast, starts at the tap | unchanged |
| b. Surface ↔ Appearance | Appearance in the editor bar (again: back to the last surface card) | as a | as a; ring grows to the frame outline; bar item fills | as a; bar fill: colour state | unchanged |
| c. Page swipe | horizontal drag on the frame, or Left / Right on the focused frame | sheet, detent | pages follow 1:1 (÷ s); ring fades out; at the halfway point the card swaps by rule a; on settle the ring fades in on the new target | drag 1:1; release: page spring with the release velocity; ring out: effects-fast, in: effects-default; card swap: rule a | unchanged |
| c. card swap at halfway | page card | — | Home page card ⇄ Terminal page card | rule a | unchanged |
| c. card swap at halfway | element card, element on both pages | card | Where chip relabels to the new page when it is not Everywhere; values spring to the new cell | label: rule a; values: spatial-fast | unchanged |
| c. card swap at halfway | element card, element not on the new page | — | swaps to the new page's card | rule a | unchanged |
| d. Open Wallpaper page | Wallpaper in the bar, or a Wallpaper link row | card sheet and its detent, underneath | page rises from the screen bottom to the full top over the sheet; ring fades out | spatial-default; ring: effects-fast | card sheet unchanged |
| d. Back | Back, Wallpaper in the bar, or Escape | card sheet and its detent | page falls to the screen bottom; ring fades in | spatial-default; ring: effects-default | the card is at the detent it had |
| d. Done from the Wallpaper page | Done | — | page and card sheet fall together while the launcher scales out (rule f exit) | spatial-default | — |
| e. Detent, tap | tap the header | content, scroll | sheet top to the next detent | spatial-default | next in the current direction |
| e. Detent, drag | drag the header | content, scroll | sheet follows the finger; on release, to the chosen detent | 1:1, then spatial-default with the release velocity | §2 |
| f. Editor enter | Appearance, Wallpaper or Edit widgets in the Home actions sheet | — | launcher scales from full screen into the frame; sheet rises from the bottom to half; bar fades in dropping 8 → 0; frame label fades in; ring fades in after the launcher lands | launcher and sheet: spatial-default, same start; bar and label: effects-default; bar drop: spatial-fast; ring: effects-default | half |
| f. Editor exit | Done | — | ring, bar and label fade out; sheet (and Wallpaper page) fall to the bottom; launcher scales back to full screen | fades: effects-fast; sheet and launcher: spatial-default | — |
| g. Reset this row | Reset this row in the row menu | row position | value springs from the old to the new value; the dot fades out, then fades in in the new cell's colour or stays off | value: spatial-fast (overshoot shows on the fill); dot out: effects-fast, in: effects-default | unchanged |
| g. ⋮ Reset card | Reset card in the ⋮ menu | — | each row resets as above | rows start 30 ms apart in touch order | unchanged |
| Chip change | pick in a chip menu | card | chip label fades out and in; every value springs to the new cell; dots crossfade | as rule g | unchanged |
| More | tap More | — | chevron turns 180; section fades in rising 8 → 0, or fades out | chevron and rise: spatial-fast; in: effects-default; out: effects-fast | unchanged |
| Menus | ⋮, a chip, or long-press a row name | — | menu fades in and scales 0.92 → 1 from its anchor; fades out | effects-default; scale: spatial-fast; out: effects-fast | unchanged |

## 8. Colour roles used

| Use | Role / token |
| --- | --- |
| Sheet, Wallpaper page | `--surface` |
| Menus | `--surface-warm` |
| Ink | `--fg`; secondary ink `--muted` |
| Outlines | `--border`; hairlines and tracks `--border-soft` |
| Selected segment, narrowed chip, pressed bar item | `--md-sys-color-primary-container` / `--md-sys-color-on-primary-container` |
| Slider fill, switch on, ring, Done, selected picker ring | `--accent`; ink on it `--accent-on` |
| Source dots | `--accent`, `--warn`, `--tl-tertiary` |
| Glass surfaces in the frame | `--surface-warm` mixed with the Colour role at Tint, at Opacity, blur = Frost, rim `--tl-glass-rim` |
| Editor bar | `--tl-glass-fill`, blur `--tl-glass-blur`, rim `--tl-glass-rim` |

## 9. Open points

- Whether element or page wins when both hold a row (§5 assumes element).
- colorSurfaceContainerLow for the sheet: no token in the design system; `--surface` is used.
- Frame scale 0.45 instead of 60% at 915 high (§1).
- The Wallpaper page's "What" chip has one value (Wallpaper).
