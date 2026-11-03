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
| Dock, every style (Tray, Beads, Wall strip, Peek) and the under-pill strip | The backdrop view's own pixels, through `RenderEffect` blur chained into the refraction shader (`"content"`) | none, identity aim | `TermuxActivity.buildRefractionEffect` (~5017), `applyDockSurfaceGlass`; per-style numbers from `DockSurfaceSpec` |
| Built-in widgets (and terminal pane slabs) | Its own `BitmapShader` over the shared frame | `GlassAnchor.place` | `terminal/PaneGlassBackdropView.java` (~116, ~239–273, ~601, ~840); built by `BuiltinWidgetView` ~766, dressed by `PaneGlass.apply` ~777 |
| Bars, palette, sheet, drawer | `SharedFrameDrawable` over the shared frame | `screen` / `layout` | `chrome/SharedFrameDrawable.java`, `WallpaperFrostPainter` |

Good news for decision 4: the dock styles already share **one** path (the style only changes
`DockSurfaceSpec` numbers). The duplication is between the three rows above, and it lives in
how each hands a picture to the one `GlassRefraction.Program`. Peek's reflection has its own small
shader (`dock/PeekGlassReflection.java`) and reads the same backdrop.

Third-party AppWidgets wear no glass today and stay out of scope.

## Design

### One seam: `GlassBackdrop`

A new deep module in `app/chrome/`: *what picture is behind this surface, and where is it aimed*.

```
interface GlassBackdrop {
    // Bind the program's picture input for a surface at this aim; the program bends and lights it.
    void bind(GlassRefraction.Program program, GlassAnchor.Aim aim);
    // Whether the picture changed since the surface last drew (the surface redraws only then).
    boolean changedSince(long stamp);
}
```

Two implementations:

- **`StoredBackdrop`**: today's shared frame and its `BitmapShader`. What `SharedFrameDrawable` and
  `PaneGlassBackdropView` do now, moved behind the interface with no behaviour change.
- **`LiveBackdrop`**: a `RenderNode` recording of what lies under the glass, blurred.

`PaneGlassBackdropView` (widgets) and the dock both draw **through the program with a picture input
from `GlassBackdrop`**. The dock stops blurring its own pixels: it becomes a frame-shader surface
like the panes, aimed with `GlassAnchor.layout` (it is moved by the dock's own transforms). That one
move is the architecture fix: after it, every dock style and every widget asks the same question
of the same seam, and switching Live on is one choice in one place.

### `LiveBackdrop`

- **What it records.** The launcher-drawn wallpaper (`WallpaperBackdropView`, `activity_termux.xml`
  ~30, in the same view tree as the dock and the wall) and the wall's page content, **excluding glass
  surfaces and what they carry**. While the backdrop records, glass hosts draw nothing
  (`LiveBackdrop.isRecording()`), so no glass sees itself or recurses.
- **How.** One `RenderNode` per frame of change for the whole wall area (recording reuses the child
  views' display lists, so it does not repaint them). Each live surface draws that node, offset by
  its aim, into its own node with `RenderEffect.createBlurEffect(radius)` chained into
  `createRuntimeShaderEffect(program.shader(), "content")`. Blur and bend run on the RenderThread on
  the GPU.
- **When.** Re-recorded only when something under the glass changed: a wall slide, a parallax
  offset, an invalidation of a view under a live surface, a wallpaper change. At rest it records
  nothing and the surfaces redraw nothing.
- **Resolution.** The blur draws into a node scaled by ¼ (like the stored frame), then is scaled
  back up, which cuts both the GPU layer memory and the blur cost by about 16×. Radius is divided
  by the same scale.
- **What does not show through in wave 2.** Glass under glass (one widget seen through another), the
  terminal pane's own glass, X11 display. Each is a later question, not this build.

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
would read as one. Key `live_blur`, default off, stored beside `fancier_glass`. Its glyph is drawn in
one family with Glass refraction's and Living glass's.

## Waves

Each wave: worker in its own worktree, diff read here, before/after shots opened here, a staging
build on pong after the wave with the numbers below.

| Wave | What | Done when |
|---|---|---|
| 0 | **Pong baseline.** `scripts/perf-baseline.sh memory frames` and `perf-frametime.sh` on `pong.txt`, plus a new scenario `pong-home-swipe-widgets.txt`: swipe across two Home pages with widgets and back, ×5. Current staging build. | numbers recorded in this file |
| 1 | **Seam, no visible change.** `GlassBackdrop` + `StoredBackdrop`; `PaneGlassBackdropView` and `SharedFrameDrawable` bind through it; the dock moves from blurring its own pixels to a frame-shader surface through the seam. Tests: aim parity for the dock between the old and new path; all four dock styles + nav strip shot before/after on the emulator, pixel-diffed. | shots identical within noise; pong numbers within noise of wave 0 |
| 2 | **`LiveBackdrop` + switch, widgets.** Recording, exclusion of glass hosts, ¼-scale blur, invalidation on change, policy + fallback, budget test, the switch row. | swipe shows the next page's widgets/icons blurred through the glass; rest frames unchanged (0.17 fps); memory and swipe numbers measured with Live on and off |
| 3 | **Dock styles on Live.** Tray, Beads, Wall strip, Peek (with its reflection), keyboard up and down, Docked and Floating. | each style shot with Live on over moving content; pong numbers |
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
