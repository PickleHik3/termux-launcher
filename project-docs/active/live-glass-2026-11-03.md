# Live blur: blur what is really behind the glass (2026-11-03)

Status: waves 0–4 delivered except 3b (2026-11-03); decision record is
[ADR 0021](../../docs/adr/0021-glass-records-what-is-beneath-it.md).

## Problem

Every glass surface shows a picture of the **wallpaper alone**, blurred once per radius and stored
(`WallpaperBlurCache`, ADR 0002/0004). The sample is re-aimed every draw (`GlassAnchor`), so a card
sliding over the wallpaper is optically right, but nothing else ever shows through: the incoming
page's widgets and icons, the terminal's text, a glass card behind another. On a swipe the glass
reads as a picture of a wallpaper rather than glass over a live scene.

The developer wants real blur as an opt-in, first on **home-page widgets and every dock style**, and
then the thick-glass shape (bevel ring, spectral dispersion, edge reflection) on top of it. That
second part is its own spec; this one only builds the live backdrop and the seam it needs.

## Decisions (developer, 2026-11-03)

1. Scope of the first build: built-in widgets on Home pages and every built dock style.
2. An opt-in switch, off by default. The stored frame stays the default and the fallback.
3. Live blur first, the glass shape after it (the shape is **parked**, developer, 2026-11-03).
4. No per-style copy of the live path: if the dock styles need one each, fix the architecture first.
5. Glass on glass: floating terminal windows (now possible on Home) see through to what is behind
   them, widgets and their glass included. One switch covers the dock, widgets and floats; a float
   costs only while one is on screen. Order: dock, then floats, then widgets.

## This goes against a written rule

`docs/agents/performance.md` rule 3: glass samples the shared frame, and "blurring per surface or
per frame is what these rules exist to stop." Rule 5 records that throttling the old live blur
(`RealtimeBlurView`, a CPU bitmap copy) tripled jank (`e40f8bc7b`). This spec goes ahead because:

- it is opt-in, so the default keeps the rule;
- it blurs on the GPU (`RenderNode` + `RenderEffect`), with no bitmap copy on the main thread;
- at rest it draws nothing (rule 1): the backdrop is re-recorded only when what lies under the glass
  changed.

It lands with **ADR 0021** that narrows rule 3 to "the default glass", and states the live mode's
own budget. The pong numbers below are a gate: a regression past noise stops the work; nothing is
tuned away.

## Today: three ways to get the picture behind the glass

| Surface | Where the picture comes from | Anchor | Code |
|---|---|---|---|
| Dock, every style (Tray, Beads, Wall strip, Peek) and the under-pill strip | The backdrop `ImageView` (`accessory_blur_backdrop`) draws a `SharedFrameDrawable`; a `RenderEffect` on that view chains a blur into the refraction shader (`"content"` = those drawn pixels), then the dock's frost colour filter | the drawable's own anchor; identity aim in the program | `TermuxActivity.buildRefractionEffect` 5011, `applyDockSurfaceGlass` 5081, `rebuildBackdropGlassEffectForLens` 6068 (11 callers), `updateAccessoryRenderEffectBackdrop` 10078, `installAccessoryBackdropFrame` 10237; nav strip 9541 |
| Built-in widgets (and terminal pane slabs) | Its own `BitmapShader` over the shared frame | `GlassAnchor.place` | `terminal/PaneGlassBackdropView.java` (~116, ~239–273, ~601, ~840); built by `BuiltinWidgetView` ~766, dressed by `PaneGlass.apply` ~777 |
| Bars, palette, sheet, drawer | `SharedFrameDrawable` over the shared frame | `screen` / `layout` | `chrome/SharedFrameDrawable.java`, `WallpaperFrostPainter` |

Good news for decision 4: the dock styles already share **one** path (the style only changes
`DockSurfaceSpec` numbers). The duplication is between the three rows above, and it lives in
how each hands a picture to the one `GlassRefraction.Program`. Peek's reflection has its own small
shader (`dock/PeekGlassReflection.java`) and reads the same backdrop.

Third-party AppWidgets wear no glass today and stay out of scope.

## Design

### Two routes, one picture seam (revised 2026-11-03 after reading the dock path)

There are two ways a surface runs the one `GlassRefraction.Program`, and both stay:

- **The effect route** (dock, nav strip): a backdrop view *draws the picture*, and a `RenderEffect`
  on that view blurs and bends whatever it drew. One offscreen layer per surface.
- **The paint route** (panes, widgets, bars): the program is the paint shader over a
  `BitmapShader` of the stored frame, aimed per draw. No offscreen layer; this is why the stored
  default is cheap and stays as it is.

A live picture is a `RenderNode`, and a `RenderNode` cannot be an AGSL child shader. So a live
surface always takes the effect route, and the seam sits at **what the backdrop view draws**, not at
the program's input:

```
// app/chrome/GlassPicture.java — the picture behind a surface, drawn at the surface's aim.
interface GlassPicture { Drawable drawable(); }   // StoredPicture wraps SharedFrameDrawable
```

- **`StoredPicture`**: the `SharedFrameDrawable` the dock's backdrop draws today, unchanged.
- **`LivePicture`**: draws the shared live `RenderNode` at the surface's aim (below).

And the effect builder leaves the activity:

- **`GlassEffects`** (`app/chrome/`): `buildRefractionEffect`, `applyDockSurfaceGlass`'s inputs and
  the lens/touch rebuild, moved out of `TermuxActivity` behind a small Host for the values the
  activity owns (dock rect, Peek rect, lens, touch, cover). Every dock style already shares this one
  builder (a style only changes `DockSurfaceSpec` numbers), so after the move there is one effect
  route for every dock style, the nav strip and, in live mode, the widgets.

In live mode a widget's `PaneGlassBackdropView` adds an effect-route layer (a `LivePicture` plus a
`GlassEffects` effect) in place of its paint-route draw; with Live off it draws exactly as today.
This answers decision 4: one picture seam and one effect builder, no per-style or per-surface copy.

Rejected: moving the dock onto the paint route. Its blur and frost colour filter run over drawn
pixels (`updateAccessoryRenderEffectBackdrop` ~10210), and a `BitmapShader` would change both: a
visible change for no gain, since live mode needs the effect route anyway.

### `LiveBackdrop`: each glass sees what is drawn beneath it (revised 2026-11-03)

- **The rule.** A live surface's picture is everything drawn *beneath* it, in the order Android
  draws (z-order, elevation included), and never itself or anything above it. Nothing can record
  itself, so no glass needs hiding, and **glass on glass** falls out: a floating terminal over Home
  shows the widgets (their glass included), icons and wallpaper under it; the dock shows the floating
  window, the page and the wallpaper. (The earlier draft recorded the whole wall with glass hidden;
  that fails because recording reuses each view's cached display list, so a "hide while recording"
  flag never reaches it.)
- **How.** `LiveBackdrop.recordBelow(surface)` walks from the surface up to
  `terminal_root_container`; at each level it draws the siblings that draw before it, with their
  transforms, into one `RenderNode`, and the launcher-drawn wallpaper (`WallpaperBackdropView`)
  at the bottom. Recording reuses each sibling's cached display list (it does not repaint them);
  containers on the path expose an ordered "draw my children below this one" (`PaneWallLayout`, the
  pane host and `BottomInsetFrameLayout` are ours; `terminal_content_column` needs a small subclass).
- **Blur.** The recording is drawn into a ¼-scale node carrying `RenderEffect.createBlurEffect`
  (radius ÷ 4), then scaled back up by the surface's `LivePicture`; the surface's `GlassEffects`
  effect (refraction, frost) runs over that as it does over the stored frame.
- **When.** Re-recorded in the pre-draw of a frame that is already being drawn because something
  invalidated; recording never invalidates, so it never schedules a frame. At rest nothing draws,
  nothing records, nothing blurs. A frame where something under the glass changed (a slide, a
  terminal printing, the clock widget's seconds hand) re-blurs the surfaces above it: that is the
  cost of the mode, measured on pong.
- **Floating windows** use it only while one is on screen, so they cost nothing otherwise.

### When Live is used

`LiveBlurPolicy`, pure like `FancierGlassPolicy`:

```
live = sdk >= 33 && liveBlurSwitch && managedWallpaperOnScreen && !batterySaver
       && !lowMemoryMarker && fancierGlassActive
```

Fancier Glass is the program Live feeds, so Live needs it. When any term fails the surface falls back
to `StoredBackdrop` at once, the same draw.

### Memory budget

Rule 2: the live layers are pixels. `LiveBackdrop` holds at most `MAX_LIVE_SURFACES` (the dock plus
the widgets on the visible page; proposed 8) GPU layers at ¼ scale, and the recorded node. Budget
constant beside the code, asserted by `LiveBackdropBudgetTest`. Layers are released when a page
leaves the screen, on `onTrimMemory` at the levels `ChromePolicy` drops the stored frame's extra
radii, and when the switch goes off.

### The switch

Settings → **Battery & performance** (`performance_preferences.xml`, `PerformancePreferencesFragment`),
in "Smoother motion" directly under **Glass refraction** (`fancier_glass`), with the same row policy
(`PerformanceRowPolicy`: offered only on Android 13+, flippable only with an in-app wallpaper and
Glass refraction on). The page moves from General to the root's System section in its own change
(settings move + row glyphs, 2026-11-03), so this row lands on the moved page and wears its glyph.

- **Live blur** — "Glass blurs whatever moves behind it. Uses more battery."

Not "Live glass": the page already has **Living glass** (`fancier_glass_motion_light`), and the two
would read as one. Key `live_blur`, default off, stored beside `fancier_glass`. One row covers the dock, floating windows and widgets. Its glyph is drawn in
one family with Glass refraction's and Living glass's.

## The backdrop service: every glass surface declares one kind (added 2026-11-03)

The drawer investigation (handoff from the drawer session, 2026-11-03; script and numbers kept with
this work) found the software blur behind most jank: `RealtimeBlurView`
(`com/github/mmin18/widget/RealtimeBlurView.java:316-383`) redraws the whole decor into a bitmap in
its pre-draw listener, about **13 ms a frame** while live and **30–36 ms** for a one-off capture.
The drawer now freezes it during motion (a8148acc8: 28.6% → 12.4% janky). Live blur's recorder
answers the same question for every surface, so all glass moves onto one service with three kinds:

| Kind | Surfaces | Picture | Cost while moving |
|---|---|---|---|
| **Wallpaper** | status bar, window bar, the off-dock plank and A–Z host, the bars | the stored pre-blurred frame (`SharedFrameDrawable`) | none |
| **Snapshot**, or **Live** while Live blur is on | app drawer, command palette, terminal sheet, popup menus (`AnchoredMenu`) | `LiveBackdrop.recordBelow`: with Live blur off, drawn once into a GPU bitmap on open and on settle and frozen while moving; with it on, refreshed like any Live surface | off: none while moving, one GPU recording + blur per open; on: a GPU re-blur on frames where something beneath changed |
| **Live** | dock, floating windows, widgets, with Live blur on | `recordBelow`, re-blurred on frames where something beneath changed | explicit, budgeted, opt-in |

Panels follow the switch (developer, 2026-11-03): Snapshot and Live are the same recorded picture
(glass on glass either way: a palette over Home shows the widgets' glass beneath it); the switch
only decides whether it refreshes while open. The command palette's sheet also takes its glass base
from the in-app keyboard's palette (`TermuxInAppKeyboard.createPalette` ~1630: theme, Look glass,
the page's keyboard look, deck look) instead of `resolveDockGlassBaseColor`
(`CommandPaletteView.refreshPalette` ~508), so the sheet and the keyboard under it always match.

Snapshot uses the recorder, not `PixelCopy`: the recording only ever holds what is beneath the
panel (PixelCopy reads the composited screen and must run before the panel appears or it copies
itself), reuses cached display lists, and blurs on the GPU. When no surface uses
`RealtimeBlurView` any more it is deleted, with its RenderScript path (Android 8–12 fall back to the
Wallpaper kind).

Checked and dropped from the drawer session's list: `PaneGlassBackdropView` builds its
`BitmapShader` only when its frame changes (`setGlass` ~273), Peek's reflection gradient is built
when its still is captured, and the scene layers' per-frame recording is one `drawRect` for a
shader that animates by design. None allocates per draw.

**Before 2b on pong** (`+dev.05dbf9eac`, 2026-11-03, `gfxbench.sh`, 3 cycles each, Terminal page,
keyboard up): **command palette** (space-bar swipe up, Back): 213 frames, 10.3% janky, p50 23 /
p90 31 / p99 46 ms, 18 slow-UI; in one cycle 40 of 72 frames over 20 ms, each with ~10 ms of `trav`
and no layout: RealtimeBlurView's capture on every frame while it is open. **Terminal sheet**
(long-press, More…, Back): 374 frames, 8.3% janky, p50 11 / p90 20 / p99 69 ms. **App drawer** (the
drawer session, `+dev.1ea98b0d9`): 12.4% janky, p50 12 ms, one 36 ms settle capture.

**After 2b part 1 on pong** (`+dev.3b931802d`, Live blur off, same steps): **palette** 239 frames,
**5.9% janky** (10.3), p50 13 / p90 16 / p99 32 ms (23 / 31 / 46), 7 slow-UI (18); one cycle 5 of 78
frames over 20 ms (40 of 72), `trav` ~1.6 ms (~10). **Sheet** 5.4% janky (8.3), p50 10 / p90 16 /
p99 73 ms (11 / 20 / 69). Both look the same as before (glass strength and tint). **Drawer** (Home, Docked, keyboard down,
swipe up + Back x2): 264 frames, **6.4% janky** (12.4, measured Floating with the keyboard up), p50 13 /
p90 22 / p99 77 ms; one open-start frame of ~75 ms (anim ~16 + trav ~14 ms) remains. Frames: frost
appears during the slide and holds at settle, with no jump.

**Measurement guard.** `scripts/perf-frametime.sh` scenarios for the drawer, palette, sheet and the
Home ↔ Workspace swipe (plus `gfxbench.sh`'s phase split, which shows a capture as `trav` time with
no layout), each with a frame budget, run before and after each move.

## Waves

Each wave: worker in its own worktree, diff read here, before/after shots opened here, a staging
build on pong after the wave with the numbers below.

| Wave | What | Done when |
|---|---|---|
| 0 | **Pong baseline.** `scripts/perf-baseline.sh memory frames` and `perf-frametime.sh` on `pong.txt`, plus a new scenario `pong-home-swipe-widgets.txt`: swipe across two Home pages with widgets and back, ×5. Current staging build. | numbers recorded in this file |
| 1 | **Seam, no visible change.** `GlassEffects` out of `TermuxActivity` (the builder, the dock surface glass inputs, the lens/touch rebuild path, the nav strip's use); `GlassPicture` + `StoredPicture`, and `installAccessoryBackdropFrame` installs the picture it is given. Tests: the effect's program uniforms equal before and after for each dock style + nav strip; emulator shots of the four styles, keyboard up and down, before/after. | shots identical; pong numbers within noise of wave 0 |
| 2 | **`LiveBackdrop` + switch + dock.** `recordBelow`, ¼-scale blur, `LivePicture`, pre-draw re-record, `LiveBlurPolicy` + fallback, budget test, the Live blur row; every dock style (Tray, Beads, Wall strip, Peek with its reflection), keyboard up and down, Docked and Floating. | the dock shows widgets/icons/terminal sliding under it, blurred; Live off pixel-identical to wave 1; rest frames unchanged; pong numbers Live off and on |
| 2b | **Snapshot + Wallpaper kinds.** Palette and sheet measured first; drawer, palette, sheet, menus onto Snapshot (Live while Live blur is on); the palette sheet's base from the keyboard's palette; status bar, window bar, plank, A–Z host onto Wallpaper; `RealtimeBlurView` deleted; guard scenarios added. | each surface's janky % and p90 before/after on pong; no `trav` spikes during motion |
| 3 | **Floating windows.** A float's glass slab (`PaneGlassBackdropView`, terminal pane) takes the live picture while Live blur is on; Home and Workspace; one float and several (each sees the lower ones). | a float over Home shows the widgets under it; pong numbers with a float over a printing terminal |
| 3b | **Widgets.** Built-in widget slabs and widget panes take the live picture. | a widget shows icons/widgets beneath it; pong numbers |
| 4 | **ADR 0021, docs, checks.** ADR; `performance.md` rule 3 narrowed and the Live budget row added; `docs/en/` line; owed items to `device-checks.md` / `developer-checks.md`. | merged |

## Wave 0: pong baseline (2026-11-03 09:05Z)

Build `2.0.0-pre.1+dev.deb750a6e` (staging), Home page 1 of 2 with clock (seconds hand), weather,
calendar and Messages widgets on glass, keyboard down, charging at 27–31%, battery saver off. Display
mode 120 Hz.

| What | Reading |
|---|---|
| Idle PSS (3 readings) | 218.1 / 198.8 / 198.3 MB, mean 205.1; native heap mean 127.4 |
| At rest, 30 s untouched | 34 frames: the clock widget's seconds hand (~1/s), not the glass. This Home's rest floor; the 5-frame doc row is a terminal place |
| `pong-home-swipe-widgets.txt` ×5, run 1 | 681 frames (49/s), 26 janky (3.8%); p50 19, p90 25, p95 25, p99 57 ms |
| same, run 2 | 687 frames (49/s), 22 janky (3.2%); p50 17, p90 25, p95 26, p99 44 ms |

`pong.txt` was not run: its swipes are measured for a terminal place with the keyboard up, and pong
was on Home. Each later wave re-measures this build back to back with its own, since the baseline
build is a pong merge, not the wave's base commit.

## Wave 1 on pong (2026-11-03 ~09:48Z)

Staging `2.0.0-pre.1+dev.a38b183c3` (wave 1 + the settings move), same Home, 47% battery.

| What | Wave 0 | Wave 1 |
|---|---|---|
| Idle PSS mean | 205.1 MB | 203.8 MB |
| At rest, 30 s | 34 frames | 32 frames |
| Home swipe ×5, janky | 3.8% / 3.2% | 3.3% / 3.1% |
| Home swipe, p50 / p90 / p99 | 19–17 / 25 / 57–44 ms | 18 / 25 / 57–53 ms |

Within noise on every row: wave 1 passes its gate.

## Wave 2 on pong (2026-11-03 ~10:55Z)

Staging `+dev.b1722d5bb` (v2-redesign f22511230 + feat/live-blur-dock with the radius calibration),
36–41% battery. Live blur toggled through Settings, no restart.

| What | Live off | Live on |
|---|---|---|
| Home swipe ×5 (Home 1 ↔ 2), janky | 3.1% / 3.1% (wave 1: 3.3 / 3.1) | not run: the Home dock wears no glass on pong |
| Home 2 ↔ Terminal swipe ×5 (new scenario), janky | 5.7% / 6.1% | 6.2% / 5.7% |
| same, p50 / p90 / p99 | 16 / 23–24 / 89 ms | 16 / 22–23 / 93–105 ms |
| At rest on the Terminal, 30 s | 0 frames | 4 frames |
| PSS mean (Terminal page) | 237.0 MB | 231.7 MB |

Within noise everywhere but the p99 (one or two long frames a run, 89 → 93–105 ms): passes the
gates. On pong's layout nothing moves beneath the dock or the keyboard glass (the terminal pane stops
above it), so Live on looks the same as off (the calibrated blur matches the stored strength;
~0.1% of the keyboard crop differs). The visible payoff arrives with floating windows (wave 3) and
panels (2b). Owed: the emulator's Floating dock over moving terminal content (the content column can
reach under the stack, `applyContentBottomMargin`).

## Wave 3 on pong (2026-11-03 ~11:55Z)

Staging `+dev.05dbf9eac` (v2-redesign a03fbe651 + feat/live-blur-floats), charging, 28–45%. A Home
float opened from the Home actions sheet (Terminal) over the clock, weather and calendar widgets.

**Seen:** with Live on the float shows the weather widget's "32°" and sun, the calendar grid and the
Messages widget's WhatsApp badge, blurred and bent, under its sharp text; Live off shows wallpaper
only. Glass on glass works.

| What (float open on Home 1) | Live off | Live on |
|---|---|---|
| Home swipe without a float (Home 1 ↔ 2), janky | 2.8% / 2.8% | — |
| Float swipe ×5 (Home 1 ↔ 2 on the top air), janky | 3.8% / 3.8% | 4.1% / 4.8% |
| same, p50 / p90 / p99 | 16–19 / 24–25 / 48–53 ms | 21 / 29–30 / 53 ms |
| At rest 30 s (the clock widget's seconds hand under the float) | 39 frames, p50 19 ms | 32 frames, p50 28 ms |
| PSS mean | 230.3 MB | 236.4 MB |

Inside the gates (janky +0.3 to +1.0 point, memory within noise), but the cost is visible: with a
float over moving content every such frame re-blurs the float, about +3–5 ms at p50/p90 on a swipe
and about +9 ms on each seconds-hand frame at rest. Opt-in, and only while a float is on screen.

## Gates on pong (90 Hz, 11.1 ms budget)

Live **off** must equal wave 0 within noise on every row. Live **on**, against wave 0:

| What | Wave 0 reference | Live on must hold |
|---|---|---|
| At rest, launcher in front | 34 frames / 30 s on this Home (seconds hand) | the same: nothing draws at rest |
| Sliding between places | median 15–16 ms, worst page-change frame 47 ms | median no worse than +1 ms; worst frame no worse |
| Home swipe with widgets (new) | 3.2–3.8% janky, p50 17–19 ms, p99 44–57 ms | janky % no worse than +1 point |
| Idle PSS | ~207 MB | no more than + the budget's layer bytes |
| `pong.txt` gestures | 2.4% janky, p90 22 ms | no worse than +1 point janky |

A miss is reported red with the numbers; the next step is the developer's call.

## Hit every surface (for this spec)

- Dock styles: Tray, Beads, Wall strip, Peek; Docked and Floating resolve geometry differently and
  keep doing so: Live changes only the picture input, never `DockLayoutPolicy`.
- Keyboard up and down: the dock lifts; its aim follows the lift (`GlassAnchor.layout`).
- Inherited and detached: Live is a phone-wide switch, not a Look property, so no inheritance row.
- Reverse states: switch off, battery saver on, wallpaper changed to a system one: each falls back to
  the stored frame on the next draw.
- Themes and scheme: unaffected (tint and ink are applied after the picture).
- Editions: no applicationId, manifest or bootstrap impact.

## Not in this spec

- The thick-glass shape: height-profile bevel, spectral dispersion, Fresnel edge reflection,
  wallpaper-derived key light. **Parked** (developer, 2026-11-03).
- Live for bars, sheets, keyboard and terminal pane slabs.
- Glass seen through glass.

## Queued after 2b part 1: the command palette's shape and theme (developer, 2026-11-03)

1. Corners follow the **Menus and dialogs** card's Corner radius (the SHEETS surface; Base until
   detached), open and closed, Docked and Floating — today the open radius copies the dock capsule
   (`TerminalCommandPaletteController` ~750, `resolveDockCapsuleCornerRadiusPx`), and the closed
   filter row (`CommandPaletteView.FILTER_ROW_H`) is short enough that any radius past half its
   height reads as the same pill.
2. The filter row a little taller, so the radius reads; height chosen from pong shots.
3. Material 3 pass: colour roles through the launcher scheme (`LauncherSchemeTheme`), not system
   attrs read directly (`CommandPaletteView.refreshPalette` ~508); surface-container tones and the
   M3 type scale for rows and chips; selected row and chips on secondary-container pairs. Checked
   in light, dark, black and Material You. Before/after shots to the developer before merge.

**2b delivered (2026-11-03, merged 28e7cae83, d8e87f67d).** Part 2: the status bar, window bar, A–Z host
and plank wear only the Wallpaper kind; `RealtimeBlurView`, `BlurImpl` and `EmptyBlurImpl` are deleted
(`AndroidStockBlurImpl` stays: it pre-blurs the stored frame). Android 8–11 panels take a software
Snapshot (`LiveBackdrop.drawBelow` on a software canvas, blurred with RenderScript, ~2.1 MB while a
panel is open, released on close; failure falls back to the frost or tint), so they blur what is
beneath them again; checked on the API 30 emulator. Every recording now draws the window's ground
first (`LiveBackdrop.drawGround`), so a flat or self-drawn ground no longer leaves the picture
see-through. Drawer: the open's Snapshot is taken in the settled scene (no frost jump; pong frames),
and a slow close brings the dock and the search keyboard down with the plane
(`AppDrawerKeyboardLowering`; pong frames). Guard scenarios: `scripts/perf-scenarios/gfxbench*.`
Owed: the bars at a cold start before the stored frame lands.

**Wave 4 delivered (2026-11-03):** [ADR 0021](../../docs/adr/0021-glass-records-what-is-beneath-it.md),
`performance.md` rule 3 and baselines, the `docs/en/` line, and the owed checks in
`device-checks.md`. Wave 3b (widgets) lands alongside.
