# Appearance and Layout editor — spec (2026-09-30)

Status: approved with changes by the developer on 2026-09-30 (Lavish review, five rounds), and
built the same day: §7 items 1–5 merged to dev at ad68d275, reviewed on Waydroid (API 33), on
pong since 2026-09-30 23:39. The review page and its interactive mock lived at
`.lavish/appearance-strip-mock.html` (gitignored). Known leftovers: the three Layout notices and
the "Minimal layout" title have no place in the bottom area and their strings are unused; the
layout canvas keeps its artwork rim insets in fill mode; a tray with four or more hidden elements
may wrap past its row; `KeyboardPreferencesFragmentTest.theVoiceRowsWriteThroughToTheSharedPreferencesAndRejectStrays`
is order-dependent (passes alone and in the last full run).

Supersedes: the on-screen Appearance card with per-surface pages, the Look page's glass rows, the
Layout editor's control rows, and the Fancier Glass switch and its three sliders.

## 1. Principles

1. **Legibility is arithmetic, not a setting.** Every glass surface is a band: sampled where it
   stands, veiled to a contrast target. A wallpaper can never make anything unreadable, whatever
   the user chose.
2. **One editor.** Appearance and Layout are two modes of one screen: the launcher itself, live,
   scaled into a frame, with a bottom area that never scrolls.
3. **Taste is a slider with stops.** Four Looks and Custom. A Look owns every value a slider used
   to set. Custom is where a tap on an element reveals at most two combined controls.
4. **No knobs where a handle will do.** Layout is drag to move, drop to hide, handle to resize.
5. **Nothing for the file-editing crowd in 1.0.** No look file, no `launcherctl appearance.set`.
   Removed keys stay in preferences for migration.

## 2. Legibility: every surface a band

Today `ChromeInk` samples the wallpaper under five chrome bands (`GlassBackdropCache.Band`:
`STATUS_BAR`, `WINDOW_BAR`, `SESSION_CHIP`, `AZ_STRIP`, `DOCK`), decides one ink polarity for all
chrome, and adds the smallest veil that lands each band at its `OnGlass` target (4.5 body text,
3.0 large text, 2.0 decoration). The veil is drawn over the light model, in addition to the user's
opacity (ChromeInk.java:27-60, 147, 659; OnGlass.java:48-66).

Changes:

| Surface | Today | Spec |
|---|---|---|
| Terminal pane | not a band | A band. Sampled under the pane rect; veiled to target against the palette's worst foreground colour (`MaterialTerminalColorScheme`). |
| Keyboard | not a band | A band. The host is veiled so key labels reach target on their caps. Key opacity stays taste. |
| Legibility control | "Terminal contrast" Softer / Default / Harder, terminal palette only | The same three values multiply every band's target (3.0 / 4.5 / 7.0) and still drive the terminal palette contrast. Exposed in the editor as **Legibility** (§3.4). |
| Sampler coordinates | screen rects | Root-container rects, so veils stay right while the root is scaled (§3.1). |

## 3. The editor

### 3.1 Screen

One screen, opened from: the corner tab's Appearance glyph (labelled, with a content description),
the terminal long-press sheet ("Appearance"), and a Settings row "Appearance". The Layout glyph and
sheet item open the same screen in Layout mode. There is no other appearance or layout UI.

- **Frame, 8/10 of the height.** `terminal_root_container` (activity_termux.xml:5) holds everything
  including `wallpaper_backdrop` (line 16). It is scaled to about 0.76 with a top-centre pivot and
  clipped by an outline at the device corner radius (`WindowInsets.getRoundedCorner`, API 31;
  28dp below). Wallpaper, glass, frost, refraction and keyboard scale together: a real render.
  The system status bar is outside the app window and outside the frame.
- **Bottom area, 2/10 of the height.** Fixed at rest; one taller state (§3.4) when a row is added.
  Nothing inside it scrolls, ever.
- **Mode pill.** A Material 3 segmented button (`MaterialButtonToggleGroup`, single selection,
  stock `Widget.Material3.Button.OutlinedButton` segments, checked icon) at the top of the bottom
  area with two segments: **Appearance** and **Layout**. Switching modes swaps the rest of the
  bottom area and the frame's interaction layer; the frame stays put.
- **Actions.** Undo (visible while dirty; returns to the state at open) and Done. Back with
  unsaved changes asks: Keep editing / Discard / Save. No Save icon, no ✕, no Reset.
- **Selection.** One 2dp `colorPrimary` outline on the tapped element. Nothing else glows.
- While the editor is up: the terminal's own input is intercepted, the in-app keyboard is
  force-shown so it can be tapped, and page/swipe gestures under the frame are off.

### 3.2 Components

The editor is rebuilt with stock Material 3 components and the app's Material theme. No custom
segment style (`Widget.Termux.EditorShell.Segment` goes), no hand-drawn pills, no custom card
chrome. Sliders are `com.google.android.material.slider.Slider` with M3 tick marks for the Look
slider. The bottom area is an M3 bottom sheet surface (`?attr/colorSurfaceContainer`, top corners
at `?attr/shapeCornerFamily` large). Chips are M3 `Chip`. Segmented controls are
`MaterialButtonToggleGroup` with M3 outlined segments and the checked icon. The existing
`EditorShellSheet` may host it but must not paint its own borders or backgrounds over the M3 ones.

### 3.3 Appearance mode, row 1

- **Look slider.** An M3 `Slider` with five discrete stops and labels under the ticks: Clear ·
  Mist · Tint · Solid · Custom. Sliding to a stop applies that Look live. Custom is the last stop.
- **Style toggle.** Beside the slider, a two-segment M3 toggle with glyphs only (content
  descriptions "Docked" and "Floating"): a phone outline with a bar flush at the bottom edge, and a
  phone outline with an inset pill. Style belongs to no Look; it is the one permanent toggle in
  Appearance mode. It writes `app_launcher_dock_style`.
- A hint line under row 1: "Slide to try a Look. The last stop is yours." At Custom with nothing
  selected: "Tap anything in the frame to tune it."

### 3.4 Appearance mode, row 2 (Custom only)

Row 2 appears only at the Custom stop and only after a tap in the frame. It holds the tapped
element's name and at most two controls. The tall state is one fixed height.

| Tapped | Control 1 | Control 2 |
|---|---|---|
| Terminal | **Darkness** slider: pane opacity and tint depth together | **Blur** slider (shared) |
| Status bar or dock | **Legibility** segmented Softer / Default / Harder (§2, global) | **Blur** slider (shared) |
| Keyboard | **Keys** slider: key opacity and key radius together | **Blur** slider (shared) |
| Wallpaper (any bare area) | **Soft wallpaper** on/off (wallpaper blur + mild dim) | **Dim** slider |

Rules:
- Blur is one value for every surface (`surface_base_blur`). Darkness writes the terminal's own
  opacity and the tint mix; Keys writes `in_app_keyboard_key_opacity` and
  `in_app_keyboard_key_corner_radius_dp` along one curve. Soft wallpaper writes a fixed blur + dim
  pair; Dim writes `wallpaper_backdrop_dim`.
- Moving any control at a Look stop jumps the slider to Custom, seeded from that Look. Sliding
  from Custom back to a Look applies the Look and discards the Custom values, with an Undo-able
  notice.
- Corners, margins, side gap, pane gap, grain, rim style, tint, motion, chip radius, key spacing
  and glass depth live inside the Look. They have no control anywhere.
- Custom is stored as today's `surface_custom_preset` JSON (format 2).

### 3.5 Layout mode

Layout mode is **not a miniature.** The same 8/10 frame is the canvas, drawn at full frame size
from the layout element pack (§5), because Layout must show arrangements the live launcher cannot
(the other orientation, a hidden tray, a lifted bar). Switching to Layout mode cross-fades the
frame from the live render to the layout canvas; the geometry is identical, so nothing jumps.

Row 1 in Layout mode: the mode pill, a two-segment Portrait / Landscape toggle (glyphs, with
content descriptions), and the restore tray (§3.6). No other rows. No sliders, no pills for
elements.

Interactions in the frame:
- **Move:** drag a bar to an edge, as today. Drop targets are the dotted outlines already drawn.
- **Hide:** drop into the tray. Keyboard on/off is the keyboard in or out of the tray.
- **Select:** tap an element. One outline, one handle on its inner edge, one 48dp drag target.
- **Resize by handle:** dock's top edge → dock height; keyboard's top edge → keyboard height;
  keyboard's bottom edge dragged up → bottom padding (chin); Home canvas → a corner handle drags
  widget columns and rows, snapping to whole cells. A readout shows while dragging, then goes.
- **Keyboard type:** with the keyboard selected, three glyph chips (M3 `Chip`, single choice)
  appear beside its handle: docked, floating, split. They disappear on deselect.
- **A–Z index:** on or off is drag to an edge or the tray. Position is where it is dropped, in
  both orientations. The **Minimised mode is removed** from the store, the edge policy, the chrome
  policy, the snapshot and the strings (`azMinimised` in EdgeStackPolicy, PlaceLayout,
  PlaceLayoutStore, PlaceArrangeSnapshot, PlaceArrangeModel, PlaceChromePolicy).

### 3.6 Restore tray

The "Hidden" box becomes a row of M3 chips with an eye-off icon, one per hidden element. Tapping
a chip returns the element to its last edge. Empty state is one short line, not a bar-height box.
While a bar is lifted the tray reads "Drop here to hide".

## 4. Looks

Names chosen by the developer: **Clear · Mist · Tint · Solid**. Mist is kept as rebuilt on
2026-09-29 (`project-docs/reference/launcher/mist-preset-obsidian-values.md`). The other three
replace Bare, Classic and Slate.

| Look | Replaces | Job | Recipe |
|---|---|---|---|
| Clear | Bare | Wallpaper forward; the thinnest glass that still reads as a surface | blur 3 · opacity 16 · grain 4 · corners 20 · scheme tint · hairline rim · classic motion · depth subtle |
| Mist | Mist | Soft frosted glass, the flagship | blur 25 · opacity 60 · grain 8 · corners 28 · obsidian tint · gradient rim · mist motion · depth medium |
| Tint | Classic | Tinted, low blur, denser; for loud wallpapers and a dark terminal | blur 6 · opacity 46 · grain 14 · corners 22 · obsidian tint · hairline rim · classic motion · depth subtle |
| Solid | Slate | Opaque; no blur cost; the "reduce transparency" answer | blur 0 · opacity 92 · grain 0 · corners 14 · scheme tint · hairline rim · classic motion · depth off |
| Custom | Custom | Whatever the user tuned; seeded from the Look they left | `surface_custom_preset` |

- A Look sets every key a slider used to set: material triple, corners, side gap, pane gap,
  terminal corners, chip radius, key radius and spacing, tint, rim, motion, and the Fancier Glass
  triple. Undo, Discard and the dirty signature cover all of them (today they omit tint, rim,
  motion and the Fancier keys: SurfaceEditorController.java:571-663, 2728-2787, 3534-3583).
- **Depth per Look** maps to the Fancier Glass keys bend / edge width / edge light: subtle
  4 / 10 / 18%, medium 9 / 20 / 18%, off 0 / 1 / 0. Fancier Glass is on wherever the device
  supports it (API 33, in-app wallpaper); Lazy mode turns it off with the other effects. The
  switch and its three sliders leave the UI.
- Solid keeps rounded corners (14) where Slate used 0: square corners read as another app, not
  another material.
- Style (docked / floating) is never part of a Look.

## 5. Layout canvas artwork

The canvas is drawn from `termux-layout-elements-v2` (developer's pack, 2026-09-30: eight
element states, four palettes, `tokens.json`, `renderer.py`). As its README says: derive every
bound from the real layout, paint content inside those bounds, keep selection and handles in the
editor layer, map palette roles onto the live scheme. The SVGs are design sources; the renderer's
geometry functions port to `LayoutCanvasView`'s block painters (which already paint from bounds
with Canvas and resolve colours through Material attributes). No stretched PNGs.

States from real keys: status bar collapsed or expanded from `status_compact`; keyboard docked,
floating or split from `keyboard_form` (split halves generated from the real key rows); docked dock
square; terminal prompt anchored to the pane's bottom, lines that stop when the pane gets short.

| Pack role | Material attribute |
|---|---|
| canvas | `colorSurface` |
| surface | `colorSurfaceContainerHigh` |
| terminal | `colorSurfaceContainerLow` |
| key | `colorSurfaceContainerHighest` |
| line | `colorOutlineVariant` |
| text · muted | `colorOnSurface` · `colorOnSurfaceVariant` |
| accent · accent_bg | `colorPrimary` · `colorPrimaryContainer` |
| secondary · secondary_bg | `colorTertiary` · `colorTertiaryContainer` |

The view is renamed from "miniature" to **layout canvas** in code and docs as it is touched.

## 6. What goes, and where it lands

| Today | Spec |
|---|---|
| Preset tiles Classic · Mist · Slate · Bare · Custom | Look slider stops Clear · Mist · Tint · Solid · Custom |
| Material Solid / Glass / Frost toggle | inside the Look |
| Opacity · Blur · Grain, base and per surface (15 sliders) | Custom row 2: Darkness, Keys, one shared Blur; grain inside the Look |
| Fancier Glass switch · Bend · Edge width · Edge light | depth per Look; on where supported; Lazy mode off |
| Shape Docked / Floating | style glyph toggle beside the Look slider |
| Corners · Margin · side gap · pane gap · chip radius · key radius · key spacing | inside the Look |
| Wallpaper dim slider | Custom · wallpaper: Soft wallpaper + Dim |
| Tint · rim · motion (no UI today) | inside the Look |
| Per-surface cards · ↺ chips · glowing outlines · "All surfaces" card | gone |
| Dock Apps count | Layout mode, dock handle (count follows dock height and width) |
| Clock face · position pop-up | stays on the live clock's ▾ handle |
| Keyboard colours · theme · typeface · key colour picks | Keyboard settings page, unchanged |
| Terminal contrast (Look page) | Legibility, Custom row 2 for status bar and dock |
| Save look · Undo · Done · ✕ · Reset | Undo · Done |
| Bottom padding (Look page) · Keyboard type (Keyboard page) | Layout mode only; the Settings rows are removed |
| Layout editor rows: Dock height, A–Z mode and position, Keyboard on/off, type, mode, height, bottom padding, grid columns and rows | handles, tray, keyboard-type chips |
| A–Z Minimised | removed |
| Settings "Look" page rows about glass | removed; the page keeps colour mode, wallpaper colours, wallpaper on/off and parallax, Lazy mode, icons, fonts, and the keyboard's page; its summary stops promising "sessions opacity" |
| `PlaceArrangeModel` pills never shown (Status bar, Pinned apps, Extra keys) | deleted |

Bugs fixed in the same work: the Undo snapshot folds a top-edge Pinned apps or Extra keys to the
bottom (`placementOf` defaults to BOTTOM; PlaceLayoutStore.java:456-464, PlaceArrangeSnapshot.java:
62-84); the two editors refusing each other silently (moot: one editor); the portrait Position pill
missing Left/Right (moot: drag only).

## 7. Order of work

1. **Layout mode**: remove A–Z Minimised; remove every row; select-and-handle resizing;
   widget-grid handle; keyboard-type chips; restore tray; Undo snapshot fix; Settings row; labelled
   corner glyph. Can start now.
2. **Layout canvas redraw** from the element pack; editor layer; rename from miniature. Can start
   now, disjoint files from item 1 except `LayoutCanvasView`, so run after item 1 or coordinate.
3. **Bands** for the terminal pane and keyboard; sampler in root coordinates; Legibility as the
   global multiplier.
4. **The editor shell**: scaled root in a device-radius frame; M3 rebuild; mode pill; Look slider;
   Custom row 2. Removes the card, the per-surface pages and the Look page's glass rows.
5. **Looks**: recipes, names, depth per Look; preset undo coverage.

Done already: one rim rule for dock, window bar and panes, 1dp everywhere (dev 811de0a3, on pong
since 2026-09-30 20:59; device check owed).

## 8. Decisions recorded

- 2026-09-30: bands everywhere + Apple-shaped controls (developer, round 1).
- 2026-09-30: no Fine-tune group anywhere; no look file or launcherctl route in 1.0 (round 2, 3).
- 2026-09-30: the in-app Settings page with a half-scale preview was tried earlier and rejected;
  the on-screen card is withdrawn (round 5).
- 2026-09-30: Looks never decide docked/floating; Style is its own toggle (round 3).
- 2026-09-30: A–Z Minimised removed; Layout has no knobs (round 5).
- 2026-09-30: keyboard type = glyph chips beside the handle; names Clear · Mist · Tint · Solid;
  Layout is not a miniature, it shares the big frame; a Material pill switches the two modes; the
  editor's pills and cards are rebuilt with real M3 components (final round).
