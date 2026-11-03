# Live blur: blur what is really behind the glass (2026-11-03)

Status: approved for waves 0–4 (2026-11-03). No code yet.

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
3. Live blur first, the glass shape after it.
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

## Waves

Each wave: worker in its own worktree, diff read here, before/after shots opened here, a staging
build on pong after the wave with the numbers below.

| Wave | What | Done when |
|---|---|---|
| 0 | **Pong baseline.** `scripts/perf-baseline.sh memory frames` and `perf-frametime.sh` on `pong.txt`, plus a new scenario `pong-home-swipe-widgets.txt`: swipe across two Home pages with widgets and back, ×5. Current staging build. | numbers recorded in this file |
| 1 | **Seam, no visible change.** `GlassEffects` out of `TermuxActivity` (the builder, the dock surface glass inputs, the lens/touch rebuild path, the nav strip's use); `GlassPicture` + `StoredPicture`, and `installAccessoryBackdropFrame` installs the picture it is given. Tests: the effect's program uniforms equal before and after for each dock style + nav strip; emulator shots of the four styles, keyboard up and down, before/after. | shots identical; pong numbers within noise of wave 0 |
| 2 | **`LiveBackdrop` + switch + dock.** `recordBelow`, ¼-scale blur, `LivePicture`, pre-draw re-record, `LiveBlurPolicy` + fallback, budget test, the Live blur row; every dock style (Tray, Beads, Wall strip, Peek with its reflection), keyboard up and down, Docked and Floating. | the dock shows widgets/icons/terminal sliding under it, blurred; Live off pixel-identical to wave 1; rest frames unchanged; pong numbers Live off and on |
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
  wallpaper-derived key light. Next spec, built on `GlassBackdrop`.
- Live for bars, sheets, keyboard and terminal pane slabs.
- Glass seen through glass.
