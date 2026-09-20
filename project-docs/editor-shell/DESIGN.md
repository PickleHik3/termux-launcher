# The editor shell — design (2026-09-17)

Item 08 / D6 of `project-docs/landscape-round/SPEC.md`. This is P6a: the design only. No code
changes ride with it.

The user's words after reviewing both editors: *"I really feel like the Appearance and Layout
surfaces need a redesign, I can't put my finger on it, but it looks not production ready."* Offered
five scopes, they chose the largest: **one shared editor shell — a common header, a common section
model, and a common control kit — used by both editors**, so a rule fixed once holds in both and the
next editor surface inherits it.

Captures this was measured against: `.lavish/landscape-review/31-appearance.png` (1300×600 px,
180 dpi, density 1.125 → 1155.6 × 533.3 dp), `63-appearance-large-settled.png` and
`64-appearance-scrolled.png` (260 dpi, density 1.625, font 1.3× → 800 × 369 dp),
`27-home-layout.png` and `28-home-portrait-layout.png` (Layout, 1300×600).

Those two devices are the design's two reference cases throughout. **Wide** = 1155 × 533 dp at
1.0×. **Short** = 800 × 369 dp at 1.3×. Every number below is checked against both.

---

## 1. The diagnosis

Six findings, each re-read from source.

**1 — Rows are below the minimum touch target.** `surface_editor_row.xml:15`, `:83` and
`surface_editor_action_row.xml:129` set the row's inner `LinearLayout` to a fixed
`android:layout_height="32dp"`; `layout_editor_slider_row.xml:144` is the same 32 dp. That is 36 px
at 180 dpi and 52 px at 260 dpi — identical in dp, because it is declared in dp. The platform
minimum is 48 dp. The `SeekBar` inside is 32 dp tall too (`:31`), so the only reliably grabbable
thing in the row is the handle. The header's four glyphs are 30 dp
(`surface_editor_pill.xml:59, 69, 81, 91`; `layout_editor.xml:51, 62, 74`) — also under 48.

**2 — Controls are sized by the card, not by the value.** The slider is
`layout_width="0dp" layout_weight="1"` (`surface_editor_row.xml:31-32`), so its width is whatever
the card has left. On the wide device: card content ≈ 1117 dp, minus the 78 dp label
(`:21`), the 42 dp value (`:37`) and the 26 dp chip (`:51`) leaves ≈ 971 dp; the drawn track
measures 1004 px = **892 dp**. Corners' real range is **0–40 dp**
(`SurfaceEditorProperties.java:247` — the review said 0–24; 24 dp is the *current value* in the
capture, not the maximum). 892 dp of travel for 41 positions is **21.8 dp of finger travel per
step**. The track itself is the platform hairline with almost no filled/unfilled contrast.

**3 — Two toggle groups share one line at two different segment widths.** `surface_editor_pill.xml`
gives Docked/Floating `layout_weight="5"` over two segments (`:122`) and Solid/Glass/Frost
`layout_weight="6"` over three (`:145`). 2.5 : 2.0 = **1.25**, and the captured widths are 238 dp
and 192 dp — a ratio of 1.24. They read as one ragged five-segment row asking one question, when
they are two questions.

**4 — The preset previews do not preview.** `SurfaceEditorPresetPreview.CARD_WIDTH_DP = 42` (`:17`)
against `REFERENCE_WIDTH_DP = 360f` (`:23`) makes `presetScale()` = **0.1167**. Every value a preset
actually differs by is multiplied by that: a 24 dp corner radius draws as **2.8 dp**, a 4 dp margin
as **0.47 dp** — under one pixel at any density this app ships on. So Classic, Mist, Slate and Bare
render as four near-identical dark phone outlines at 42 × 68 dp, and Custom is an empty dashed box.
The mechanism is the scale, not the art.

**5 — Content is sliced, not scrolled.** Both editors *do* enable a fade
(`SurfaceEditorController:1029-1030`, `LayoutEditorController:390-391`, 18 dp) and both *disable*
the scrollbar outright (`SurfaceEditorController:1025`, `LayoutEditorController:386`). Neither caps
the body at a whole number of rows: `SurfaceEditorPillMetrics.bodyCapPx` (`:80-84`) returns raw
arithmetic clamped to `dp(80)`…`dp(360)`, and `LayoutEditorPlan.rowsHeightCapPx` (`:278-286`) is
`max(minPx, screenHeight − miniature − chrome)`. So the cut lands wherever it lands. In `63-…` the
Grain row is cut through the middle of its glyphs; in `64-…` the Corners row is cut through the
middle of its glyphs at the *top* and collides visually with the pill row above it. An 18 dp fade
over a 52 px row does not read as "more below"; it reads as a rendering fault.

**6 — No grouping, and vast dead space.** Six properties of three kinds — material (Opacity, Blur,
Grain), shape (Corners, Margin) and wallpaper (Wallpaper) — render as six identical rows with no
section structure at all; the Layout editor already has headings
(`LayoutEditorController.sectionTitle:403-419`) and Appearance has none. In Layout, the portrait
miniature's frame is `PORTRAIT_FRAME_SCREEN_FRACTION = 0.55f` of the screen *height*
(`LayoutEditorPlan:270-276`) at `PORTRAIT_ASPECT = 9/19.5` (`PlaceMiniatureView:96`): on the wide
device that is 0.55 × 600 = 330 px tall and **152 px wide**, sitting in a 1300 px card between two
≈ 550 px empty gutters, while the controls below are clipped off-screen entirely (`28-…`).

**The through-line: controls inherit the card's width instead of declaring their own — and the card
inherits the screen's.** That is why it feels worse in landscape and why it reads as unfinished.

**The shell's spine inverts it: the control kit declares its widths, the row declares its width from
the kit, the body declares its width from the row, and the card declares its width from the body.**
Nothing stretches to fill a screen. Nothing is cut mid-glyph. Everything is a number that a pure
class returns and a test asserts.

---

## 2. The control kit

All numbers in dp. `EditorShellMetrics` (§7) is the class that returns them.

### 2.1 The row

```
│←16→│←──── 96 ────→│←12→│←──── track ────→│←12→│← 52 →│←8→│ 28 │←16→│
│ pad│ label         │    │ control          │    │ value│   │chip│ pad│
                     ↑ minHeight 48, height wrap_content, padding 6 top/bottom
```

| Part | Size | Why this number |
|---|---|---|
| Row height | `minHeight = 48`, height `wrap_content`, 6 dp vertical padding | 48 is the platform floor. Fixed 48 would clip at 1.3× — AGENTS.md: "fixed-height rows plus a 1.3× font scale is what clips". At 1.0× the row measures exactly 48; at 1.3× a 12.5 sp label's line box is ≈ 21 dp and the row measures ≈ 48 still; the `wrap_content` is the escape hatch for a 1.5×+ scale or a tall locale. |
| Row pitch | 48 (nominal), re-read from the measured row for the overflow maths | The overflow cap quantises to the *measured* pitch, not the nominal one, so 1.3× does not desync it (§5). |
| Label column | **96**, single line, `ellipsize=end`, autosize 12.5 → 10 sp | The longest shipped label is "Wallpaper" (`strings.xml:1256`): ≈ 55 dp at 12.5 sp, ≈ 72 dp at 1.3×. The shipped 78 dp column leaves 6 dp of slack at 1.3× and none at 1.4×. 96 holds every shipped Appearance and Layout label at 1.3× without autosizing. |
| Control column | see below | |
| Value column | **52**, right-aligned, single line, autosize 11.5 → 9 sp | Widest string is "100%" — ≈ 28 dp at 11.5 sp, ≈ 37 dp at 1.3×. 52 holds it plus "24 dp" and the Layout editor's "68%". |
| Link chip | **28** visual, 48 touch target via `TouchDelegate` into the row's padding | The chip is a tap target (it puts the row back on Base). 26 dp today is under the floor; growing it visually would make it shout, so the visual stays small and the target grows. |
| Gaps | 12 / 12 / 8 | |
| Row inner width | **304 min, 488 max** | min = 96+12+96+12+52+8+28 (min track 96); max = 96+12+280+12+52+8+28. |

**Switch rows** and **action rows** use the same three columns; the control column holds a
`MaterialSwitch` (leading-aligned in the control column, not trailing at the row's edge, so it lines
up with the sliders' handles-at-rest) or the chevron. The whole row is the touch target for both.

**At 1.3× font scale:** nothing changes but the measured pitch, which the overflow maths re-reads.
**On a short landscape screen:** nothing changes; the row is the unit the body budget is spent in.

### 2.2 The slider

| Part | Size |
|---|---|
| Track width | `clamp(96, availableControlWidth, 280)` |
| Track thickness | **4** dp, fully rounded (2 dp radius) |
| Unfilled | `termuxColorOnSurface` at **24%** |
| Filled | `termuxColorPrimary` (the cyan accent) at **100%** |
| Handle | **16** dp disc, cyan, with a 2 dp ring in the card's fill colour so it reads against a filled track |
| Grab area | the full **48 dp** row height across the whole track column, not the handle |

**280 is the cap, and here is the arithmetic behind it.** Every Appearance and Layout control's step
count, from `SurfaceEditorProperties`:

| Control | Steps | dp of travel per step at a 280 dp track |
|---|---|---|
| Opacity, Grain, Wallpaper, key size, key spacing | 100 | 2.8 |
| Margin (`MAX_ALL_MARGIN_DP`, `:218`) | 48 | 5.8 |
| Corners (`:247`) | 40 | 7.0 |
| Blur (`:236`) | 30 | 9.3 |
| Icons per page (`:293`) | 20 | 14.0 |
| Key radius (`DP_TENTHS`, `:312`) | 240 | 1.17 |

280 dp is the smallest track on which **every** control's step is individually addressable
(240 steps ≤ 280 dp of travel), and it is a comfortable thumb sweep — roughly one screen-inch and a
half at 160 dpi. Beyond it, extra width buys nothing: the 892 dp track today gives Corners 21.8 dp
of travel per step, which is why a 1 dp nudge is impossible to land and the control feels
unfinished. **A wider card does not get a wider track; it gets a second column (§6).**

*Known limit, unchanged by this design:* on a 360 dp-wide portrait phone the track floors at ≈ 100 dp
and the 240-step key-radius control cannot address every tenth by drag alone. That is true today.
Adding a stepper or a typed value is out of scope for the shell.

### 2.3 Toggle segments

**One segment width per line. Two groups never share a line.** Docked/Floating and
Solid/Glass/Frost become two rows, each a standard 48 dp row with its own 96 dp label:

```
Shape      [ Docked  │ Floating ]
Material   [ Solid │ Glass │ Frost ]
```

| Part | Size |
|---|---|
| Segment height | **44** (from 30), inside a 48 dp row |
| Segment width | `clamp(88, controlWidth / segmentCount, 160)`; every segment on a line is identical |
| Group width | `segmentWidth × segmentCount`, leading-aligned in the control column |
| Text | 12 sp, autosize 12 → 9 sp, `maxLines=1`, `ellipsize=end`, `minWidth=0`, `textAllCaps=false` |
| Horizontal padding | 6 dp each side (the existing 2 dp was compensation for a too-narrow segment) |

The existing `Widget.Termux.SurfaceEditor.Segment` style already does the hard parts —
`minWidth=0`, fixed height so autosize stays on, both padding pairs cleared. It is renamed
`Widget.Termux.EditorShell.Segment` and gains the 44 dp height and 12 sp text. The Layout editor's
four declared slots (`layout_editor_pills_row.xml:205-219`) and the "unused slots come off" trick
survive unchanged — with equal widths, taking a slot off still leaves the group's corners at the
ends of what is offered.

**Why stacking, not equalising across one line:** five equal segments on one line reads as one
five-way choice. They are two questions, and one of them (Docked/Floating) is answered far less
often. Stacking costs 48 dp of body height; moving both groups *into the scrolling body* (§3) buys
back the 37 dp pinned pill row plus the 86 dp preset strip, so the fixed chrome shrinks on net.

**At 1.3×:** 44 dp segment, 12 sp → 15.6 sp text, autosize drops it as far as 9 sp before
ellipsising. "Floating" at 9 sp is ≈ 40 dp; the 88 dp minimum segment holds it.
**On a short landscape screen:** unchanged — the segments are in the body and scroll.

### 2.4 The preset tile

**The preset stops being a shrunken phone and becomes a corner of a surface at true size.**

| Part | Size |
|---|---|
| Tile | **72 × 40**, clipped at the preset's own corner radius (not a fixed 5 dp) |
| Name | 11 sp under the tile, single line, centred, 2 dp gap |
| Selected | 2 dp `termuxColorPrimary` ring, 2 dp outside the tile; name in primary |
| Custom | the same 72 × 40 footprint, 1 dp dashed stroke at onSurface 30% |
| Row | 5 tiles × 72 + 4 gaps × 8 = **392** dp of tiles; whole row 60 dp tall with a 96 dp "Preset" label |

**What it must render**, at **1:1 device dp** — no `presetScale()`, no `REFERENCE_WIDTH_DP`:

1. a fixed wallpaper crop behind (one bitmap, shared by all five tiles, so opacity differences read
   against the same thing),
2. the preset's surface fill at its **opacity**, with its **grain** at its real amplitude,
3. the preset's **blur** applied to the crop under the fill,
4. the tile's leading corner drawn at the preset's real **corner radius** — a 24 dp radius on a
   72 × 40 tile is a third of the tile's width and unmissable,
5. the preset's **margin** as real inset from the tile's leading and bottom edges,
6. **Docked and Floating resolved by their own rules** (AGENTS.md "Hit every surface": Docked is
   square at rest, Floating is already a 26 dp card) — one formula for both is explicitly wrong.

What it must *not* render: bars, a status pill, a dock slab, a phone outline. A preset does not move
bars; showing an arrangement it does not change is what made four different looks look the same.

The tile **draws itself** and never reads the live surfaces — AGENTS.md: the Appearance editor
collapses the status pane on entry, so nothing in the card may depend on a live pane.

**At 1.3×:** the tile is fixed (it is a picture, not text); only the 11 sp name grows, and the row's
60 dp absorbs it. **On a short landscape screen:** the preset row unpins and scrolls (§5).

---

## 3. The section model

### 3.1 Appearance

| Section | Rows |
|---|---|
| **Material** | Solid / Glass / Frost · Opacity · Blur · Grain |
| **Shape** | Docked / Floating · Corners · Margin |
| **Wallpaper** | Wallpaper |

Per-surface panels keep Material and Shape and add one section of their own:

| Panel | Extra section | Rows |
|---|---|---|
| Dock | **Apps** | Icons |
| Keyboard | **Keys** | Key corners · Key size · Key spacing · Colours › |
| Status | **Indicator** | Indicator corners |
| Terminal | **Frame** | Border |

The section strings are **product copy**. They are one word, they name the thing the user is
changing, and they carry no mechanism: "Shape", not "Geometry"; "Material", not "Compositing";
"Keys", not "Key metrics". The brief's working word "geometry" does not ship. The section a control
belongs to becomes a field on `SurfaceEditorProperties.Control`, so the grouping is data and
`SurfaceEditorPropertiesTest` can assert it.

### 3.2 Layout

Unchanged in content, moved onto the shell's section view: **Dock** (Height) · **Keyboard** (Type,
Opens on entry, Height, Chin) · **Widget grid** (Columns, Rows). These come from
`LayoutEditorPlan.Section` (`:50-62`) and its `rows()`/`groupsOf()` pair (`:154-185`); the enum, the
order and the store keys do not move — ADR-0001's three sizes stay exactly where ADR-0001 put them.

### 3.3 What a section header looks like

| Part | Size |
|---|---|
| Height | `minHeight = 24`, `wrap_content` |
| Text | 11 sp, bold, `termuxColorOnSurfaceVariant`, `letterSpacing = 0.06`, title case |
| Margins | 12 dp above, 4 dp below; **0 above when it is the first header in its pane** |
| Rule / divider | none |

This is the Layout editor's existing heading (11 sp, bold, onSurfaceVariant, 4 dp / 2 dp margins,
`LayoutEditorController:405-418`) with the top margin opened from 4 to 12 so a section reads as a
break rather than a slightly taller gap, and the letter-spacing that makes an 11 sp label at a
monospaced-technical weight sit calmly above a 48 dp row. Restraint is the point: the grouping is
carried by the 12 dp of air, not by a rule, a chip or a background.

### 3.4 When room runs out

- Sections **never** collapse, never become tabs, never become an accordion. The card is three to
  four sections deep; a disclosure layer would cost more taps than it saves rows.
- The body scrolls, as one list, across section boundaries. A section may straddle the visible cut.
- In the two-pane body (§6) a section **never straddles the gutter** — panes are filled
  section-major, so a section is wholly in one pane or wholly in the other.
- Scroll position is kept per panel (per surface in Appearance, per place+orientation in Layout), so
  returning to a panel returns to where the user was. `rebuildRows` already resets to 0 on a genuine
  rebuild (`SurfaceEditorController:1010`, `LayoutEditorController:371`); the shell keeps that and
  only adds "restore on re-entry to a panel whose rows did not change".

---

## 4. The header

One header, one XML (`editor_shell_header.xml`), `<include>`d by both cards.

```
│←16→│ ◆ │←8→│ Title ………………………………… │ ↺ │ Discard │ ✓ │ ✕ │←12→│
      20              15 sp medium      40   text    40  40
```

| Part | Size |
|---|---|
| Header height | **56**, dropping to **44** when the card's available height < 280 dp |
| Leading glyph | 20 dp, 8 dp gap. Appearance: the palette glyph on the shared layer, the surface's glyph on a panel. Layout: the layout glyph. |
| Title | 15 sp, `sans-serif-medium`, single line, `ellipsize=end`, fills |
| Actions | 40 dp visual, 4 dp apart; touch target 48 × 48 at the 56 dp header, 44 × 48 at the compact one, expanded by `TouchDelegate` |
| Trailing padding | 12 |

**What it carries, and in this order** (trailing edge, left to right): revert · Discard · **Done** ·
Close. Revert and Discard exist only while there is something to lose; Done is the only commit and
is the only filled control in the whole card (`dock_tuning_done_pill`); Close, where the editor has
one, only puts the card down.

- Appearance has revert, Discard-equivalent (its `reset` glyph), Done and Close
  (`surface_editor_pill.xml:57-97`).
- Layout has revert, Discard (text) and Done, and no Close (`layout_editor.xml:49-80`) — the shell's
  header simply leaves the slot empty. The slot set is per editor; the sizes, the order and the
  spacing are the shell's.

**Titles** (product copy):

| Where | String |
|---|---|
| Appearance, shared layer | **"All surfaces"** — replacing the shipped `surface_editor_global_heading` = "Global". "Global" is an engineering word for a thing the glossary calls Base and the user experiences as *all of them at once*. |
| Appearance, one surface | the surface's name: Dock · Status · Keyboard · Terminal (unchanged) |
| Layout | "Layout" (unchanged) |

**The chooser row.** Directly under the header the shell has exactly one optional 60 dp slot for the
choice that changes what the whole card is showing. Appearance fills it with the preset row; Layout
fills it with the Portrait / Landscape toggle (label "Orientation", segments unchanged). It is
pinned while the body has ≥ 200 dp of height; below that it unpins and becomes the body's first row,
because on a short landscape screen 60 dp of pinned chrome is 40% of the body.

**At each size:**

| Case | Header | Chooser |
|---|---|---|
| Wide (1155 × 533 dp, 1.0×) | 56 dp, 48 dp targets | pinned |
| Short (800 × 369 dp, 1.3×) | 44 dp, 44 × 48 targets | unpinned, scrolls |
| Portrait phone (360 dp) | 56 dp | pinned |

**Settled and surviving untouched:** the Appearance editor commits only on Done; it rests as the
outlines plus the floating palette/✓ pill (`surface_editor_float.xml`); the card is raised by the
palette or by touching a surface; ✕ only puts the card down and asks nothing; Back puts an open card
down first and from the resting state routes through the unsaved-changes dialog; dirtiness is a
comparison against the snapshot taken on entry. None of that is a look, and none of it changes.

---

## 5. Overflow

**Both affordances, and a third that matters more than either.**

1. **Quantise the cap to whole rows.** The body's height is rounded *down* to a whole number of rows
   plus a fixed peek:

   ```
   peek      = 16 dp
   wholeRows = max(1, floor((availablePx - peek) / rowPitchPx))
   capPx     = wholeRows * rowPitchPx + peek
   ```

   `rowPitchPx` is the **measured** first row, not the nominal 48, so 1.3× stays in step. The cut
   therefore always lands 16 dp into a row — a visible sliver of the next label, never through the
   middle of its glyphs. This is the actual fix for finding 5; the fade and the scrollbar are what
   make it legible.

2. **Fade 24 dp** (from 18) at whichever end has content beyond it, at both ends when scrolled into
   the middle. 18 dp over a 52 px row left half a glyph at full opacity; 24 dp covers the peek and
   the top of the next row.

3. **A persistent scrollbar.** 3 dp wide, inset 2 dp from the pane's trailing edge, full-height
   track at onSurface 12%, thumb in `termuxColorPrimary` at 60%, **always drawn while the content
   exceeds the cap** (`scrollbarFadeDuration = 0`). Both editors disable the scrollbar today
   (`SurfaceEditorController:1025`, `LayoutEditorController:386`); the shell turns it on. A thumb
   sitting at 40% of its track is the only thing on screen that says *how much* more there is.

**What reports the overflow state.** `EditorShellMetrics.bodyCap(availablePx, rowPitchPx, peekPx)`
returns a small value type — `capPx`, `wholeRows`, `overflows` — so the view layer applies a number
and asserts nothing, and a unit test can state the whole behaviour:

> At 800 × 369 dp with font 1.3×, the Appearance body reports `wholeRows = 2`, `overflows = true`
> in one pane, and `wholeRows = 2`, `overflows = true` in each of two panes.

`SurfaceEditorPillMetrics.bodyCapPx` (`:80-84`) delegates to it, keeping its dp(80)/dp(360) clamps
applied *before* quantisation. `LayoutEditorPlan.rowsHeightCapPx` (`:278-286`) likewise, keeping its
96 dp floor.

---

## 6. Landscape layout — the dead gutters

**Recommendation: the card declares its own width from the control kit and never stretches to the
screen; when there is room for two row columns, the body uses two.**

### 6.1 The card's width

```
paneWidth = min(488, (contentWidth - 24) / paneCount)
cardWidth = min(screenWidth - 20, paneCount * paneWidth + (paneCount - 1) * 24 + 32)
```

where `contentWidth = screenWidth - 20 (margins) - 32 (padding)`, 24 dp is the gutter, and
`paneCount` is 2 when `contentWidth >= 632` (= 2 × the 304 dp minimum row + 24) and 1 otherwise.
The card is centred horizontally; the leftover width becomes symmetric outer air with the live place
visible through it, which is the thing the editor is for.

Both cards are `layout_width="match_parent"` today (`surface_editor_pill.xml:20`,
`layout_editor.xml:15`). That is the single line where the through-line enters the tree.

| Device | contentWidth | panes | paneWidth | track | cardWidth | outer air each side |
|---|---|---|---|---|---|---|
| Wide 1155 dp | 1103 | 2 | 488 | 280 | 1032 | 61 dp |
| Short 800 dp | 748 | 2 | 362 | 154 | 780 | 10 dp |
| Portrait 360 dp | 308 | 1 | 308 | 100 | 340 | 10 dp |

Never three panes: a third column puts related rows three eye-movements apart, and no section here
is more than four rows deep.

### 6.2 Appearance, two panes

Panes are filled **section-major**: Material fills pane 1; Shape and Wallpaper fill pane 2. A
section never straddles the gutter. If one pane would exceed the other by more than one section, the
next section moves across. Each pane scrolls independently and reports its own overflow.

On the short device this is the difference between 2.9 visible rows and 5.8, which is why it is the
answer to finding 5 as much as to finding 6.

### 6.3 Layout, two panes

**Miniature in the leading pane, rows in the trailing pane.** Leading pane width =
`clamp(0.25 × contentWidth, miniatureNaturalWidth + 32, 0.5 × contentWidth)`; trailing pane gets
what is left, capped at 488.

With no rows beneath it, the miniature is free to take the whole body height, so
`LayoutEditorPlan.miniatureHeightPx` gains a **width** argument and, in the two-pane case, derives
the frame from `min(paneHeight, paneWidth / frameAspect)` instead of
`PORTRAIT_FRAME_SCREEN_FRACTION × screenHeight`. On the wide device the portrait frame goes from
152 × 330 px in a 1300 px card to roughly 205 × 444 px in a 300 px pane — bigger *and* no longer
marooned. When the shown orientation is landscape the miniature's natural width exceeds 50% of the
content (aspect 19.5/9 = 2.17) and the body falls back to one column with the rows beneath, which is
exactly the case P1 bounds.

**Rejected, and why:**

- *Stretch the controls to the width* — that is finding 2.
- *Centre a single column and leave the gutters empty* — honest, but spends the one axis landscape
  has plenty of on nothing while the axis it is short of stays clipped.
- *A fixed left rail (presets / nav) plus a right pane* — works for Appearance, does not generalise:
  Layout's leading item is a miniature whose width depends on the orientation shown. A two-pane
  **body** rule covers both editors with one number.
- *Make the card full-width and pad the rows* — indistinguishable at a glance from today, and it
  leaves the card covering the surfaces the user is judging.

### 6.4 The vertical budget, stated honestly

On the short device the Appearance card has ≈ 264 dp between the status bar and the dock. Chrome is
44 (header) + 20 (card padding) = 64 dp with the chooser unpinned, leaving 200 dp of body → 4 whole
rows per pane, 8 across two panes. The full shared-layer body is 3 section headers (72 dp) + 8 rows
(384 dp) = 456 dp, so a pane shows about 88% of its content and says so. Today the same card shows
six 32 dp rows in ≈ 86 dp — 45%, cut mid-glyph, with no scrollbar. **The shell does not make it fit.
It makes it whole rows, two columns, and legible about what is below.**

---

## 7. Where each rule lives in code

The repo's rule (AGENTS.md:231): geometry and policy stay pure and tested, the view layer dumb
enough to just apply the answer.

### New: `com.termux.app.editorshell.EditorShellMetrics` (pure, tested)

| Method | Owns |
|---|---|
| `rowMetrics(density, fontScale)` | 48 dp floor, 6 dp padding, the 96 / 52 / 28 columns, the 12/12/8 gaps |
| `trackWidthPx(availableControlPx, density)` | the 96…280 clamp |
| `segmentWidthPx(controlPx, segmentCount, density)` | 88…160, one width per line |
| `paneSplit(contentWidthPx, leadingNaturalPx, density)` | pane count, pane widths, the 24 dp gutter, the 632 dp threshold |
| `cardWidthPx(screenWidthPx, paneCount, paneWidthPx, density)` | §6.1's formula |
| `headerHeightPx(availableHeightPx, density)` | 56 / 44 |
| `chooserPinned(bodyHeightPx, density)` | the 200 dp rule |
| `bodyCap(availablePx, rowPitchPx, peekPx)` | quantisation, `wholeRows`, `overflows` (§5) |
| `presetTile(density)` | 72 × 40, 8 dp gap, 60 dp row |

### Extended

| Class | Change |
|---|---|
| `SurfaceEditorPillMetrics` | `parkTopPx`, `parkRegionFootTopPx` unchanged. `bodyCapPx` keeps its dp(80)/dp(360) clamps and then delegates to `EditorShellMetrics.bodyCap` for quantisation. |
| `LayoutEditorPlan` | `miniatureHeightPx` gains a pane-width argument and the two-pane branch (§6.3). `rowsHeightCapPx` keeps its 96 dp floor and delegates likewise. `Section`, `Row`, `rows()`, `groupsOf()`, `drop()`, `isDirty()`, `revert()` all unchanged. |
| `SurfaceEditorPresetPreview` | `CARD_WIDTH_DP`/`CARD_HEIGHT_DP` → 72 / 40. `presetScale()`, `REFERENCE_WIDTH_DP`, `REFERENCE_CARD_HEIGHT_DP`, `cardScale()`, `bandPx` and the six band constants **go**; they are replaced by corner-crop insets at 1:1 device dp with a Docked branch and a Floating branch (§2.4). |
| `SurfaceEditorProperties.Control` | gains a `section` field; `rowsFor(slot)` returns rows already in section order. |

### Layouts and styles

| New | Replaces |
|---|---|
| `editor_shell_header.xml` | the header blocks of `surface_editor_pill.xml:32-98` and `layout_editor.xml:28-81` |
| `editor_shell_row.xml` (slider) | `surface_editor_row.xml`, `layout_editor_slider_row.xml` |
| `editor_shell_switch_row.xml`, `editor_shell_action_row.xml` | `surface_editor_switch_row.xml`, `surface_editor_action_row.xml` |
| `editor_shell_pills_row.xml` | `layout_editor_pills_row.xml` and `surface_editor_pill.xml:110-164` |
| `editor_shell_section.xml` | `LayoutEditorController.sectionTitle`'s programmatic `TextView` |
| `Widget.Termux.EditorShell.Segment` | `Widget.Termux.SurfaceEditor.Segment` |

### What each view layer is left doing

- **`SurfaceEditorController`**: inflate, bind a `Control` to a row, write through the link, run the
  preview scopes, park the card. `applyRowsCap()` (`:1037-1052`) collapses to "measure the row pitch,
  ask `EditorShellMetrics`, set the cap, `requestLayout`". No dp literals for the kit remain in it.
- **`LayoutEditorController`**: inflate, bind a `Group` to a row, route drags to the miniature,
  sync the notice. `applyCanvasHeight` (`:309-324`) asks `paneSplit` first and `miniatureHeightPx`
  second. `CARD_CHROME_DP = 132f` and `ROWS_FLOOR_DP = 96f` (`:334, :336`) become
  `EditorShellMetrics` inputs rather than private constants.
- **`PlaceMiniatureView`**: unchanged. It already answers `frameAspect` and `reservedHeightPx`.

### Tests

`EditorShellMetricsTest` (new) asserts every number above at both reference densities and at 1.0×
and 1.3×. `SurfaceEditorPillMetricsTest`, `LayoutEditorPlanTest`, `SurfaceEditorPresetPreviewTest`
and `SurfaceEditorPropertiesTest` all exist and extend; `SurfaceEditorPresetPreviewTest` is
rewritten wholesale because the class it tests changes shape.

---

## 8. The move

One branch, `feat/editor-shell` (P6b), seven commits. P1 and P5 must be merged first.

| # | Step | Verifiable by |
|---|---|---|
| **S1** | `EditorShellMetrics` + `EditorShellMetricsTest`. No view touched. | `:app:testDebugUnitTest` green; the table in §6.1 asserted at 1155 × 533 / 1.0× and 800 × 369 / 1.3×. |
| **S2** | The row kit: shared row / switch / action / pills layouts and the segment style, both editors inflating them, every size read from S1. No structural change. | Rows measure ≥ 48 dp at both densities; the track measures ≤ 280 dp; Docked/Floating and Solid/Glass/Frost are on their own lines at one width each. Compare against `31-` and `63-`. |
| **S3** | The shared header + the chooser slot; the 56/44 rule; Layout's orientation toggle and Appearance's preset row both in the slot. | Every header action is a ≥ 44 × 48 dp target. Appearance still commits only on Done — the existing dirty/rotation tests stay green untouched. |
| **S4** | Sections: `Control.section`, the shared section view, Appearance's three groups, Layout moved onto the same view. | `SurfaceEditorPropertiesTest` asserts each control's section and the row order. |
| **S5** | Overflow: quantised cap, 24 dp fade, persistent scrollbar, per-panel scroll memory. | `EditorShellMetricsTest` asserts `wholeRows`/`overflows`; on device no row is ever cut through its glyphs at either density. |
| **S6** | The two-pane body and the card's declared width; Layout's miniature-left split and `miniatureHeightPx`'s pane-width argument. | `LayoutEditorPlanTest` extended for the two-pane branch. At 1155 × 533 both editors use both panes and no gutter exceeds 24 dp; at 360 dp portrait both fall back to one column. |
| **S7** | The preset tiles: `SurfaceEditorPresetPreview` rewritten to the 1:1 corner crop, test rewritten. | The test asserts Classic / Mist / Slate / Bare produce different radius, margin and opacity pixel values. By eye, the four are distinguishable at both densities, in both dock styles. |

S1 gates everything. S2 → S3 → S4 → S5 are sequential. S6 needs S2. S7 needs only S1 and can be
built alongside S6. Every step leaves both editors working; none of them is a point of no return.

**Gate for the whole phase** (from the spec's build plan): `:app:testDebugUnitTest` green, and both
editors walked at both densities, portrait and landscape, against the review captures.

---

## 9. What is sacred

No behaviour rides along with a look change. Explicitly:

1. **The Appearance editor commits only on Done.** It rests as the outlines plus the floating
   palette/✓ pill; the card is raised by the palette (shared layer) or by touching a surface; ✕ only
   puts the card down and asks nothing; Back puts an open card down first and from the resting state
   routes through the unsaved-changes dialog; dirtiness is a comparison against the entry snapshot.
2. **The miniature's drag model.** A bar is dragged to an edge or into the tray and the write goes
   straight through; `Drop.NONE` / `MINIATURE` / `LIVE` and `liveFollows()` are unchanged; the
   Portrait/Landscape toggle keeps its `mShownOrientation` vs `mDeviceOrientation` semantics.
3. **The layout store's schema**, including ADR-0001: keyboard height, keyboard chin and dock height
   stay in `PlaceLayoutStore`, per place per orientation. No key moves, no migration.
4. **The peek-and-slide lens.** `StatusBarLensView` is P4's, not the shell's; nothing here touches
   it.
5. **AGENTS.md's do-not-re-litigate list.** No negative or concave corner radius. No Sessions tab and
   no dot tab badges. The stored `-1` corner radius stays the "follow the style" sentinel resolved in
   `resolveAutoCornerRadiusDp` — the preset tile reads the *resolved* radius, never the raw key.
   Shipped defaults stay pinned via `adoptShippedSurfaceDefaults()`. The Appearance editor still
   collapses the status pane on entry, so every preview in the card draws itself.
   `SettingsLayoutUtils.applyItemLayout`'s exemption list still applies to anything reached from
   Settings. Terminal padding is untouched. `targetSdkVersion` stays 28.
6. **"Hit every surface."** Docked and Floating resolve geometry differently and must stay two
   formulas — in the render path and in the preset tile. Every row still works both inherited and
   detached, and setters still write *through* the link. Keyboard up and down, one pane and many,
   light / dark / black / Material You / launcher scheme: the shell adds no colour of its own beyond
   `termuxColorPrimary`, `termuxColorOnSurface` and `termuxColorOnSurfaceVariant`, all already
   scheme-aware.
7. **No new strings that explain the mechanism.** Every string this design adds or changes is in
   §3.1, §4 and §3.2, and each is one or two plain words.

---

## 10. Open questions

1. ~~**"Global" → "All surfaces"**~~ — **DECIDED 2026-09-17: yes.** `surface_editor_global_heading`
   becomes `All surfaces`. The user wrote "All Surfaces"; shipped as sentence case to match the
   house convention every sibling title uses ("Status bar", "Extra keys", "New session",
   "Terminal fonts"). Say so if title case was deliberate.
2. ~~**The preset tile's wallpaper crop.**~~ — **DECIDED 2026-09-17: the fixed shipped crop.**
   All five tiles share one constant background so opacity and blur differences read against it.
   No bitmap read while the card parks.
3. ~~**Layout's header title.**~~ — **DECIDED 2026-09-17: name the place.** Layout's header reads
   Home / Terminal / Display, following the shell's rule that the title names what is being edited.
   The shipped "Layout" title is retired.
