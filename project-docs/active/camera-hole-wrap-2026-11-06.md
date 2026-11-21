# Giving way to the camera hole

Status: spec, for review. Base `cd12da403` (v2-redesign). Decisions by the developer, 2026-11-06.
Builds on #163 (the status row's camera gap). Paths below are under
`app/src/main/java/com/termux/app/` unless they start with another root.

## 1. Problem and decided behaviour

Under Full screen (system bars hidden) the status bar card, or with the bar off the pane's top,
reaches the screen's top edge and its glass and rim run under the camera. #163 already moves the
**row's contents** out of the camera's band; the **glass, rim and outline** still run through it.

Decided:

- **Status bar: split around the hole.** Two cards, the hole in the gap. Every corner convex, the
  bar's own radius. A side card narrower than `SIDE_CARD_MIN` collapses away and the hole sits at
  the bar's end.
- **Pane top, bar off, Full screen on: the same treatment** (see §6 Q2: a pane cannot be two cards).
- **Cutout mode stays Auto / Custom / Off.** Auto traces Android's real shape
  (`DisplayCutout.getCutoutPath()` on API 31+, the bounding rect below). Custom adds Move
  sideways, Move down, Size, Margin on top of the traced shape; a live outline is drawn round the
  real camera while a slider moves. `status_bar_cutout_center` / `_width` migrate.
- Pong: Nothing Phone (2), 1080x2412 @ 420dpi (2.625), punch hole top-left.

Floating bar, hole mid-bar (both cards stay):

```
 screen top ─────────────────────( ● )─────────────────────────
   ╭──────────────────────────╮   ●   ╭───────────────────────╮
   │ 12:41  ☁ 18°   [chips]   │ gap   │ [chips]   82%  ▮▮▮    │
   ╰──────────────────────────╯       ╰───────────────────────╯
```

Floating bar, pong (hole near the left end: left card < SIDE_CARD_MIN, collapses):

```
 ( ● )
   ●    ╭─────────────────────────────────────────────────────╮
        │ 12:41  ☁ 18°   [chips]                82%  ▮▮▮      │
        ╰─────────────────────────────────────────────────────╯
 ^ bar's left end moves to the gap's right edge; right end unchanged
```

Docked bar (decided: split; see §6 Q1 — this is where it meets the code badly):

```
 ┌──────────────────────( ● )──────────────────────────────────┐  joined frame glass,
 │ 12:41  ☁ 18°  [chips]  ●   [chips]      82%  ▮▮▮           │  no rim on screen edges
 │ ╭───────────────────────────────────────────────────────╮   │  ← pane insert (opening)
```

Bar off, Full screen, pane top (recommended reading, §6 Q2):

```
 ╭──────────────────────( ● )──────────────────────────────╮
 │ rim stops ──────────╯ gap ╰────────── rim resumes        │   text starts below the box
 │ $ ls                                                     │
```

## 2. One pure policy: `chrome/CameraHoleGeometry`

New pure class `chrome/CameraHoleGeometry.java` (no Android types; plain ints and floats).
It owns the camera's keep-out box; nothing else computes it.

### Inputs (window px; launcher window is at the display origin, `LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES`, `TermuxActivity.java:12327`)

| Input | Source |
|---|---|
| `shapeLeft/Top/Right/Bottom`, `hasShape` | Auto: bounds of `getCutoutPath()` ∩ top band (API 31+); else union of `getBoundingRects()` touching y=0 (today's code, `TermuxActivity.java:10938-10945`) |
| `topBandPx` | `getInsetsIgnoringVisibility(statusBars()).top` (today, `:10970`) |
| `mode` | `StatusRowKeepOut.Mode` (AUTO/CUSTOM/OFF), unchanged |
| `dxPx, dyPx, scalePct, marginPx` | Custom prefs (§4); Auto uses `dx=dy=0, scale=100, margin=GAP_MARGIN_DP (4dp)` |
| `windowWidthPx` | for the no-shape Custom base |
| surface: `left, top, right, bottom, radiusPx` | the asker's rect at rest and its own corner radius |

Path work (`Path.op(INTERSECT)`, `computeBounds`) runs in the host, only when the
`DisplayCutoutCompat` differs from the last one (equals), never per inset dispatch: the IME
animation dispatches insets every frame.

### Outputs

- `keepOutBox(screen) -> int[4] | null` (the box all readers use):
  - OFF, or AUTO without a shape: null.
  - base = shape bounds; CUSTOM without a shape: base = `CUSTOM_BASE_DP` (24dp) wide, `0..topBandPx`
    tall, centred on the window.
  - scale about base centre by `scalePct/100`; offset by `(dx, dy)`; outset by `margin` left, right
    and bottom (top stays 0 when base top ≤ margin). Empty after scaling -> null.
- `split(box, surface) -> Split`, a value object, one of:
  - `NONE` — no box, or `surface.top >= box.bottom`, or `surface.bottom <= box.top`, or
    `box.right <= surface.left || box.left >= surface.right`.
  - `TWO(leftCardRight, rightCardLeft)` — both sides ≥ `minSide`.
  - `TRIM_LEFT(newLeft = box.right)` / `TRIM_RIGHT(newRight = box.left)` — one side < `minSide`.
  - `COVERED` — both sides < `minSide` (surface narrower than the hole; keep NONE drawing, log once).
  - `minSide = max(2·radiusPx, SIDE_CARD_MIN_DP·density)`, `SIDE_CARD_MIN_DP = 40`.
- The split is horizontal only and spans the surface's full height: it depends on the box and the
  surface's left/right, not its height, so the bar's compact <-> expanded change needs no new answer.
- `paneTopGap(box, paneRect) -> [gapStart, gapEnd)` in pane-local x, empty when no overlap or the
  pane top ≥ box bottom.

### Readers (one owner per number)

| Reader | Today | After |
|---|---|---|
| `StatusRowKeepOut.keepOutBox` (`statusbar/StatusRowKeepOut.java:221`) | own AUTO/CUSTOM maths | delegates to `CameraHoleGeometry.keepOutBox`; `answer()` keeps the corner maths |
| `TerminalWindowBar` / `WindowBarKeepOut` (`terminal/TerminalWindowBar.java:554`, `terminal/WindowBarKeepOut.java:24`) | `StatusRowKeepOut.answer` | unchanged (follows through `answer`) |
| status bar glass views | none | `split()` |
| pane rim | none | `paneTopGap()` |
| `FullScreenCornerPolicy.paneTextTopInsetPx` (`chrome/FullScreenCornerPolicy.java:49`) | `safeTopPx` and the bounding rect's left/right | box bottom and box left/right (§6 Q4: changes a pinned number) |

`StatusRowKeepOut.Screen` (`:49`) swaps `holeLeft..holeBottom/customCenterOffsetPx/customWidthPx`
for the shape bounds plus `dx, dy, scalePct, marginPx`; `equals/hashCode` follow. Built in one
place, `TermuxActivity.syncStatusRowKeepOut` (`:10933`), which also stores the box for the panes
(replacing `mTopCutoutHole`, `:3559`).

### Tests (`app/src/test/java/com/termux/app/chrome/CameraHoleGeometryTest.java`)

Fixture numbers at density 2.625, window 1080x2412, Floating bar 21..1059 x 21..105 (r = 26dp = 68px):

1. Circle hole (shape 60..120 x 30..90): box 49..131 x 0..101; bar split -> left side 28 < minSide -> `TRIM_LEFT(131)`.
2. Centre circle (510..570 x 30..90): `TWO(499, 581)`.
3. Pill hole (440..640 x 20..90): `TWO(429, 651)`.
4. Teardrop/odd path: bounds only are used; a path whose bounds reach 0..topBand gives a full-band box.
5. No cutout, AUTO: null box, `NONE`. No cutout, CUSTOM: 24dp box at centre.
6. Landscape (cutout on a side, no rect touching y=0): null, `NONE`.
7. Surface below the box (bar top ≥ box bottom; system bar shown): `NONE`.
8. Side too narrow on the right, and both sides too narrow -> `TRIM_RIGHT`, `COVERED`.
9. Nudges: dx, dy, scale 50/200, margin 0/24 shift and size the box exactly; scale about centre.
10. OFF: null whatever the shape.
11. Migration (§4): old centre/width -> new dx/scale reproduces the old box within 1px, with and without a shape.
12. `minSide` uses 2·radius when larger than 40dp.
13. Existing `StatusRowKeepOutTest`, `StatusRowLayoutKeepOutTest`, `WindowBarKeepOutTest`,
    `FullScreenCornerPolicyTest` keep passing (AUTO margin stays 4dp; only Q4 changes a number).

## 3. Drawing

What exists: the bar is `terminal_window_bar_host` (`StatusBarSwipeLayout`,
`res/layout/activity_termux.xml:129-132`), its glass the `terminal_window_bar_background` view
(`:147-148`) and the frost twin `terminal_window_bar_wallpaper_backdrop` (`:139-140`), drawable from
`TermuxActivity.buildTerminalWindowBarBackground` (`:32681`) ->
`GlassSurfaceFactory.statusBarSurface(...)` (`chrome/GlassSurfaceFactory.java:213-255`): a
rounded-rect slab with an all-edge rim under Floating, inner-edge rim only under Docked. Radius
`resolveStatusBarCapsuleCornerRadiusPx` = min(Corners, height/2) (`TermuxActivity.java:7699`). No
shadow/elevation found on the bar. Under a joined Docked frame the bar wears no glass of its own
(`frameStandsDown`, `:3660`; `onPlank` -> null drawable).

### Status bar (Floating)

- **Two views, not one non-convex shape.** Add `terminal_window_bar_background_side` and
  `terminal_window_bar_wallpaper_backdrop_side` beside the existing two, `GONE` unless the answer is
  `TWO`. Each card is a plain rounded rect: its own `statusBarSurface(...)` drawable (all-edge rim,
  same radius) and its own frost crop, anchored by `GlassAnchor` like the main one. One element,
  one hierarchy (ADR 0018 §1): the side card is part of the bar's view, not a second surface.
- Main card takes the wider side. `TRIM_*` and `TWO` are applied as left/right margins on the
  background/backdrop views (`FrameLayout.LayoutParams`), compare-before-set, in the same pass that
  already applies the bar's glass (`applyTerminalWindowBarGlass`); never in measure/layout/draw.
- Contents: the existing `StatusRowKeepOut.flow` keeps the row's chips off the gap. Under `TRIM_*`
  the row's start inset becomes the gap end (the collapsed side holds nothing). The **slot** (clock,
  pinned cards, media, 65/69dp) stands in the main card only: its parent gets the same side margin.
- Ink band (`GlassBackdropCache.Band.WINDOW_BAR`) measures the main card only.
- Glass kinds (ADR 0021): the bar is **Wallpaper** kind, so the side card costs one more drawable
  sampling the shared frame: no bitmap, no extra blur, no Live slot. Memory: two drawables and two
  views, < 2 KB.
- Motion: the split is a function of the bar's rest rect. A crossing's `BarMarginEase` edge grow
  (`StatusRowLayout.setEdgeGrow`) draws both cards moved with their edge; the side card nearer an
  edge moves with it. The split is never recomputed per frame.

### Status bar (Docked)

See §6 Q1. Recommended: no glass change (the frame's screen edge carries no rim, so nothing visibly
runs into the camera); contents only, as today, plus the slot rule above.

### Pane top (bar off, Full screen)

- The lone pane's rim is `borderView`'s `terminalBorderRim(...)` (`TermuxActivity.java:3483`,
  `:3610-3636`); split panes draw their own via `GlassRimDrawable` (`GlassRimDrawable.java:57`,
  radius from `PaneShape.radiusForBounds` per draw).
- Add `setTopGap(int start, int end)` to the rim drawable(s): on draw, `canvas.clipOutRect(start,
  0, end, strokeWidth·2)` around the stroke only, when the gap is non-empty. One rect, no
  allocation, no outline change. Set from `syncPanesForScreenTop` (`:3570`), which already runs
  after every window layout and compares first.
- The pane's glass and outline stay one convex card. The camera is opaque, so glass under it is
  not seen; only the rim line running into the hole is.
- Text: `paneTextTopInsetPx` moves the first row below the box (Q4).

### Not touched

Layout canvas (ADR 0007 shape model `chrome/LiveChromeShape.java`): no split shown in the editor's
canvas. Landscape: no top hole, `NONE`. Status bar on a side or the bottom edge: `NONE` (surface not
in the top band).

## 4. Settings

`res/xml/status_bar_preferences.xml:52-75`, category "Camera cutout". Fragment
`fragments/settings/termux/StatusBarPreferencesFragment.java:45-62` shows the Custom rows only on
Custom (keep). Data store `TerminalIOPreferencesDataStore.java:157-196`. Constants
`termux-shared/.../TermuxPreferenceConstants.java:716-733`. Footprint stays inside this category
(settings pages are fragile).

| Row | Key | Type | Range | Default | Title | Summary |
|---|---|---|---|---|---|---|
| Mode | `status_bar_cutout_mode` (kept) | pill | auto/custom/off | auto | Keep clear of the camera | — |
| Sideways | `status_bar_cutout_offset_x` | int dp | -200..200 | 0 | Move sideways | Lines the gap up with the camera. |
| Down | `status_bar_cutout_offset_y` | int dp | -24..24 | 0 | Move down | — |
| Size | `status_bar_cutout_scale` | int % | 50..200 | 100 | Size | — |
| Margin | `status_bar_cutout_margin` | int dp | 0..24 | 4 | Margin | Space between the camera and the bar. |

- Strings replace `settings_status_cutout_center_*` / `_width_*` (`res/values/strings.xml:154-157`);
  title `settings_status_cutout_title` becomes "Keep clear of the camera".
- The Appearance card's tile and link (`surfaces/cards/StatusBarCard.java:144-146, 374-381`) keep
  working unchanged (`cameraGapOn` reads mode only).

### Migration (old centre/width -> new)

Old Custom box: centre `windowW/2 + center`, width `width`, no margin. Needs the window and the
shape, so it runs in the activity on the first `syncStatusRowKeepOut` with a known window width,
once, guarded by `status_bar_cutout_migrated` (bool):

- Only if mode is custom or either old key is stored; otherwise just set the flag.
- `margin = 0` (the old box had none), `offset_y = 0`.
- With a shape: `scale = round(100·widthPx / shapeWidthPx)` clamped 50..200;
  `offset_x = round(px2dp(windowW/2 + centerPx − shapeCentreX))` clamped -200..200.
- No shape: base is the 24dp box, so `scale = round(100·width/24)` clamped 50..200 (old 0..160dp:
  above 48dp clamps), `offset_x = center`.
- Old keys are left in place (harmless), never read again.

### Live outline

- Brief says "drawn round the real camera on the home". Settings is `SettingsActivity`, a separate
  window that covers the home (Q3). Spec: the outline is drawn by **the window that holds the
  slider**, at the box's position in that window's coordinates (the same cutout), as a
  `ViewOverlay` drawable on its decor view: 2dp stroke in the accent colour, the box outline
  rounded to its own half-height.
- Shown on Slider touch start (`addOnSliderTouchListener`; `SliderPreference.java:140-160` today
  only has a change listener), updated on change, removed on touch stop, on `onPause`, on fragment
  `onDestroyView`, and by a 1.5 s backstop after the last change. It lives in that window's overlay
  only, so it cannot outlive the window or reach the home's.
- If Settings does not draw into the cutout area, the fragment sets
  `LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES` only while the outline shows (and restores on remove).

## 5. Waves

Each wave: `./gradlew :app:compileDebugJavaWithJavac` and `testDebugUnitTest` for `:app`; device
checks go on `project-docs/device-checks.md` in the same commit.

| Wave | Content | Pong checks owed |
|---|---|---|
| 0 | Read pong's cutout: `dumpsys display`/`DisplayCutout` bounding rect, path bounds, safe top (main session; feeds fixture numbers) | the numbers |
| 1 | `CameraHoleGeometry` + tests; `StatusRowKeepOut.keepOutBox` delegates; `Screen` fields; Auto uses `getCutoutPath` on 31+ with cache | Full screen, bar on: chips still clear the camera; Floating and Docked; keyboard up/down; no change with system bar showing |
| 2 | Floating split: side views, `TRIM_*`, slot margin | Floating, Full screen: bar starts right of the camera (pong collapses left card); radius matches the right end; compact <-> expanded keeps the split; Home <-> Terminal crossing has no flash at the gap; light/dark/black/Material You/scheme chrome; font scale 1.3 |
| 3 | Pane top rim gap + text inset from box bottom | Bar off, Full screen: one pane and a 2-way vertical split; rim breaks around the camera, text below it; Floating and Docked; rotate to landscape and back |
| 4 | Settings rows, migration, live outline | Custom: each slider moves/sizes the gap and the outline follows the camera; leave mid-drag (Home key, recents) -> no outline anywhere on return; upgrade from a build with custom centre/width -> same gap |
| 5 | Docked, per Q1's answer | per answer |

## 6. Decisions on the open questions (developer, 2026-11-06)

- **Q1 Docked:** contents only, as today. No glass split under Docked; Floating gets the split.
- **Q2 Pane top:** option (a). The rim's top line breaks around the hole's box; the pane's glass and
  outline are unchanged; text starts just below the box.
- **Q3 Outline preview:** drawn over the Settings page at the camera's real position while a slider
  moves (§4), removed on release, onPause, onDestroyView and by the 1.5 s backstop. Not in the
  Appearance editor.
- **Q4 Pane text inset:** reads the traced box (one owner); `FullScreenCornerPolicyTest:82`'s pinned
  value changes with it.
- **Q5 Pong's hole:** read from pong 2026-11-06: boundingRect 454..540 x 0..126 px, insets top 126,
  1080 px wide. Near the centre, not top-left; the existing `pong()` fixtures are right. Both split
  cards stay wide on pong; the "left card collapses" sketch is the narrow-side case, not pong's.
  Wave 0 still reads the cutout *path* for the shape.
- **Q6 Expanded bar:** split for its full height; second-row items flow either side.
- **Q7 Margin:** one Margin setting read by both Auto and Custom (default 4 dp, today's value).
