# Live window glass under Terl: residency like streamed level chunks

Design, 2026-11-28; decided by the developer the same day (below). Read-only research against `terl` at `0ad17d3e8`
(`app/wt-terl`). Nothing here is built or measured yet. Every number is tagged **[measured]**
(with source, date and device) or **[reasoned]** (worked out from code or arithmetic, still owed
a pong check).

The decision this designs for: under Terl, every window's glass is Live. It shows what lies
beneath it (ADR 0021) and keeps one picture for its life (ADR 0029). Memory is streamed the way a
game streams level chunks: what is on screen is resident, what one gesture can reach is prepared,
and everything else is let go.


## Decided (the developer, round 2, 2026-11-28)

These answer section 7 and override the proposal below where they differ
(`terl-decisions-2026-11-28.md`, round 2).

1. **Every window's glass is Live**, memory by rings: hot (on screen) holds its GPU layers; warm
   (one gesture away: the next column, the Workspace above or below, a fling's or a hover's
   landing) holds views and display lists, no pixels; cold holds nothing. Promotion runs ahead of
   the gesture; hysteresis keeps back-and-forth from thrashing; ring changes are decided at rest,
   never mid-motion; budgets go by RAM tier; everything is released on background and memory trim.
2. **Parity is Live enough.** A tile with only wallpaper under it draws the stored frame at the
   same strength and counts as Live (no layer of its own). Floats, a carried window (navigation
   mode), tiles over a live scene, the bar and sheets hold real blur layers. Glass on glass: a
   Float over tiles shows them frosted.
3. **Live blur is on by default under Terl above 6 GB of RAM**, off below. A default only: the
   user's explicit setting wins, and v1 is unchanged.
4. **Tiles over a live wallpaper scene follow the scene** (the effect route), on-screen windows
   only; a tile one gesture away holds the parity route until it shows and the next rest.
5. **A jump across several Workspaces is a short fade** to the destination; the Workspaces in
   between stay cold (risk "Far jumps fly over cold levels" is closed this way).
6. **Blur behind see-through Linux windows**: Windows Blur applies behind them through the same
   Live path as every other window (the parity route over the wallpaper, the effect route over a
   scene or as a Float).
7. The tier numbers in section 4.6 stand as the starting point until measurements 1 and 3 set them.

Where this lives in code: `glass/GlassResidency` (rings, routes, tiers, trim table),
`glass/GlassRoute`, the route and area budget in `glass/GlassRegistry`, `GlassService.setRoutes`,
`TilingLivePolicy` (navigation centre hysteresis), `StackMotion.kindForMove` (the far-jump fade),
`LiveBlurPolicy.terlDefault`, and the window frames' glass in `TilingDesktopView.WindowFrame`
worn through `chrome/TerlGlassWear`.

---

## 0. The rule in plain words

1. **Every window is Live.** It is granted the Live kind once, at registration, and keeps it
   (ADR 0029 §4). A kind costs no bytes. Bytes live in the layers, and each layer streams on its
   own trigger (ADR 0029 §3: "each layer keeps its own refresh trigger").
2. **Rings by gestures, not pixels.** A window is **hot** while any of it is on screen, **warm**
   while one gesture can bring it on screen (the next column, the Workspace above or below, the
   place a fling or a hover will land on), and **cold** otherwise. Hot holds GPU layers, warm holds
   views and display lists but no pixels, and cold holds nothing.
3. **Draw the cheapest route that shows exactly what is beneath.** In Scrolling or Dwindle, tiles
   never overlap one another. What lies under a tile is the ground: the wallpaper, which the stored
   frame already is, blurred at the same calibrated strength (`LiveStoredParity`). Such a tile
   draws its Live picture through the **parity route**, the paint route over the stored frame,
   with no offscreen layer. A window takes the **effect route** (its own blur and refraction
   layers) only when something other than a still ground is beneath it: a Float over tiles or
   another Float, a window carried in navigation mode, or a live scene ground. The route is chosen
   from the arrangement, never from motion.
4. **Budgets cover area as well as count.** The effect route's cost is a full-resolution layer the
   size of the window. The budget is therefore an area in "screens" by RAM tier, checked at rest
   against the worst crossing reachable from here (this place plus its heaviest neighbour). Over
   budget, the worst-priority surface falls to the Wallpaper kind at rest, as a policy event.
   Nothing changes mid-motion.

---

## 1. The numbers already in the repo

### 1.1 Device and frame budget

| Fact | Value | Source |
|---|---|---|
| Pong | Nothing Phone (2), Android 16, 1080x2412 at 420 dpi (2.625) | `docs/agents/performance.md:7`; memory `pong.md` |
| Pong RAM | 12 GB class, 11.5 GB MemTotal, so it takes the quarter tier of `GlassPagePool` | `project-docs/active/tai-memory-manager.md:89` |
| Refresh | panel up to 120 Hz; apps held at 90 Hz when active and 10 Hz at rest, so the **working budget is 11.1 ms** (older numbers use 8.3 ms) | `performance.md:7-13` |
| Wave 0 display mode | read as 120 Hz on 2026-11-03 | `project-docs/active/live-glass-2026-11-03.md:227` |
| Idle PSS, live terminal | 203–213 MB, mean 207 MB, 2026-10-12 (185–200 MB on 2026-09-05; AGENTS.md uses 185–200) | `performance.md:60` |
| At rest on Home with the clock widget | 34 frames in 30 s (the seconds hand) | `live-glass-2026-11-03.md:232` |

### 1.2 What Live glass costs in time [measured, pong]

| What | Number | Source, date |
|---|---|---|
| Float over moving content, Live on vs off | swipe p50/p90 **+3–5 ms** (16–19/24–25 to 21/29–30 ms), janky +0.3–1.0 pt | `live-glass-2026-11-03.md:282-292`, 2026-11-03, `+dev.05dbf9eac` |
| Same float over the clock's seconds hand at rest | **+9 ms per tick frame** (p50 19 to 28 ms) | same; ADR 0021 Consequences |
| Home 2 ↔ Terminal, Live off/on | janky 5.7–6.1% / 6.2–5.7%; p99 89 to 93–105 ms | `live-glass-2026-11-03.md:258-264` |
| Widgets (wave 3b), Live off/on | janky 1.95% / 2.16%; p50 20/18, p90 25/24, p99 38/46 ms; about 330 frames of burst right after switching Live on | `live-glass-2026-11-03.md:385-391`, 2026-11-04, release pre.3 |
| Swipe ablation (Home2↔WS, WS↔AI) | baseline Live glass UI/RT **9.6 / 8.0 ms** per frame, skipped 9.5% / 11.3%; Solid no glass **6.1 / 4.0 ms**, 4.5% / 2.2%; Live off on WS↔AI 5.4% | `project-docs/active/swipe-perf-ablation-2026-11-08.md:91-98`, 2026-11-08 |
| Live picture planning per frame | about **1.5 ms of UI thread**; `LiveBackdrop.onPreDraw` about 0.8 s per 10 swipes | `swipe-perf-ablation-2026-11-08.md:100-108` |
| Translucent glass during a swipe | Custom look p90 40–57 ms vs 24 Solid; RenderThread "flush layers" **4.1 vs 1.4 ms/frame**; full-screen draw 9 vs 6 ms | `glass-swipe-frozen-backdrops-2026-11-07.md:8-12` |
| Glass texture re-upload | about 10 screen-sized glass textures per swipe, **about 0.7 ms each**; flush layers 2.4–3.2 ms | `swipe-model-2026-11-08.md:17` |
| Drag-start / landing stalls (with or without glass) | 37–51 ms / 30–46 ms frames | `swipe-model-2026-11-08.md:15-16` |
| Page layer, Look Clear vs Solid | swipe 23 / p90 43 ms vs 16.7 / 22 ms | `live-glass-2026-11-03.md:393-395`, 2026-11-07 |
| Panels after Snapshot/Live | palette 10.3 to 5.9% janky, sheet 8.3 to 5.4%, drawer 12.4 to 6.4% | `performance.md:71`; ADR 0021 |
| What not to do: software capture | `RealtimeBlurView` **13 ms/frame** live, **30–36 ms** per one-off capture | `live-glass-2026-11-03.md:160-162`; ADR 0021 |
| Glass sampling a live hardware-buffer frame | UI `postAndWait` **7.4–8.4 ms vs 0.6 ms**; a software copy brings it to 2.4 ms | `project-docs/active/input-latency-study.md:897-924`, 2026-10-23 |
| Scene low-res pass for glass | about 1.1 ms per pass at 15 fps | `input-latency-study.md:881` |
| Old stored-frame miss | `WallpaperManager.getDrawable()` decode about 46 ms plus a blur on the main thread | `input-latency-study.md:44` |

### 1.3 What Live glass costs in memory

| What | Number | Source |
|---|---|---|
| Live budget today | at most **8** live surfaces (`GlassRegistry.LIVE_SLOTS`, `LiveBackdrop.MAX_LIVE_SURFACES`), each blur layer at most 1,152,000 B (a 1440x3200 screen at a quarter), plus the pages pool: **15,507,456 B worst case** | `LiveBackdrop.java:79-100`; `GlassRegistry.java:37`; ADR 0029:94-96; `performance.md:45-48` |
| Priority | dock (and panels), then floats, then widgets (`GlassEntry.PRIORITY_*`, `LiveBackdrop.RANK_*`) | `GlassEntry.java:46-48` |
| Pages pool (`GlassPagePool`) | above 4 GiB: quarter scale, **6 MiB** (nine slots of 278x608x4 = 676,096 B); 4 GiB or less, or low-RAM: eighth scale, **2 MiB** (169,024 B a slot) | `GlassPagePool.java:28-45`; ADR 0029:89-92 |
| Pages per surface | 3 whole pages plus the decor (`PAGE_LAYER_SLOTS = 4`, `LivePicture.MAX_SLOTS = 8`) | `LiveBackdrop.java:92`; `LivePicture.java:512` |
| Live blur scale | a quarter (`LivePicture.SCALE = 4`); recording margin = blur radius in px (`LivePicture.java:291`) | `LivePicture.java:43` |
| Stored frames (`WallpaperBlurCache`) | 72 MB budget, at most 6 radii, **about 1 MB per radius** at a quarter | `performance.md:32` |
| Clear frame / radius-0 wallpaper | about **15.6 MB each** at 1080x2412 with 1.5x parallax | v2-redesign `project-docs/reference/glass-physical-material-2026-11-22.md:231` (fa9f55443) |
| A full-resolution glass layer | **+1.15 MB** on a 600x480 widget, **+5.18 MB** on a 1080x1200 float | same file, :227-228 |
| PSS with a Live float | 230.3 MB off, **236.4 MB on** (+6.1 MB, called "within noise") | `live-glass-2026-11-03.md:288` |
| Android 8–11 panel Snapshot | about 2.1 MB while open | ADR 0021 §5 |
| Kept-glass tiers (ADR 0023 §5, parked) | ≤4 GB 11 MB, 6 GB 17 MB, 8 GB 34 MB, 12 GB+ 56 MB; farthest page evicted first | `docs/adr/0023-…md:57-64`; parked per `swipe-perf-ablation-2026-11-08.md:122` |
| Other byte budgets (pattern to copy) | `KittyImageStore`, `LauncherIconStore`, `DockIconCache`, each with a constant and a test | `performance.md:27-33` |

### 1.4 Timing constants of the streaming parts

| Constant | Value | Source |
|---|---|---|
| A page capture may take to land | `PageLayerCapture.MAX_WAIT_FRAMES = 4` (44 ms at 90 Hz), then the swipe goes on without it | `PageLayerCapture.java:38` |
| Captures per swipe | `MAX_CAPTURES_PER_SWIPE = 3` | `LiveBackdrop.java:148` |
| Pages layer retaken at rest | at most once per **250 ms** (`PageLayerRefresh.REFRESH_INTERVAL_MS`); the last change is always taken | `PageLayerRefresh.java:181`; ADR 0029:66 |
| GPU fence hazard | taking the image once held the main thread **1.1 s** mid-swipe; it now runs on a reader thread | `PageLayerCapture.java:25-31` |
| Terl stack motion | slide **260 ms** (`StackMotion.MOVE_MS`), fade 120 ms with animations off | `tiling/desktop/StackMotion.java` |
| Border hold delay | 1.5 s today, to be shortened | `tiling-desktop-2026-11-27.md` Open |
| Which Terl places and windows hold views | the level in front ±1 (`isLiveLevel`); in a live Scrolling Workspace, columns within one screen width of the screen (`liveWindows`); navigation mode centres on `round(position)` | `tiling/TilingLivePolicy.java:36-93`; `TilingDesktop.java:2281-2322, 1757` |
| Neighbour windows | built at the same size, `GONE` at rest, shown only while the wall moves | `project-docs/active/live-neighbour-windows-2026-11-01.md` |

### 1.5 A gap in today's accounting

`BUDGET_BYTES` counts only the quarter blur layers and the pool. A Live surface also draws
through the effect route. On the effect route, `PaneGlassBackdropView.drawLive` puts a
`RenderEffect` on a `PaneGlass.live` node sized to the slab plus its margin
(`terminal/PaneGlassBackdropView.java:~416-440`). HWUI promotes a node that carries an image
filter to an offscreen layer of its own bounds, at **full resolution**. The 2026-11-22 numbers
(+5.18 MB for a 1080x1200 layer) and the +6.1 MB PSS of a Live float both match that **[reasoned
plus corroborating measurements]**. The stored route (`BitmapShader` paint) has no such layer
(`live-glass-2026-11-03.md:64-68`). That layer, not the blur, is the dominant cost of a Live
window. It is GPU memory owned by HWUI: it exists while the node is drawn and is destroyed after
the node leaves the tree, for example when its view becomes `INVISIBLE`. On today's Home the
uncounted part (dock or bar, two big floats, five widgets) comes to about **21.6 MB**, more than
the counted 14.8 MiB **[reasoned]**.

---

## 2. What a Terl window's glass actually sees

`LiveBackdrop.recordBelow` draws, in z-order, only what is drawn *before* the surface (ADR 0021
§2). For each kind of Terl surface:

| Surface | What is beneath it | Does it vary with motion? |
|---|---|---|
| Tile (Scrolling column, stacked half, Dwindle leaf) | the ground (wallpaper, or a live scene) and the Workspace's empty air. Tiles never overlap, and Home rides above Workspace 1 so its air never shows Home (`TilingLivePolicy.homeTravel`) | no: only the ground moves (parallax or scene). Arrangement changes happen at `render()` |
| Tab strip and name strip on a tile | the tile's own glass | no. It costs no slot, the same way a float's titlebar uses `setReachTop` (ADR 0021 §2) |
| Float (a Workspace's, a Home page's, pinned) | tiles and their content (terminal text, Linux pixels) and lower Floats | yes: whatever moves under it |
| Window carried in navigation mode | other windows | yes |
| Control bar | the Workspace stack (moving pages) | yes, while the stack moves |
| Sheets (search, drawer, Workspace list, Overview, menus) | everything | already Live or Snapshot (ADR 0029 §10) |
| Home widgets | the ground only (`live-glass-2026-11-03.md:314-321`) | no |

So for a tile, Live and stored are the **same picture** (wave 2 found them alike to about 0.1% of a
crop once calibrated, `live-glass-2026-11-03.md:266-270`), except over a live scene. Live has
something to show beyond the stored frame on Floats, carried windows, the bar, sheets, and
anything over a scene.

---

## 3. The Terl cases in surfaces and bytes

Geometry **[reasoned]** for pong portrait: about 120 px status bar, a two-row bar of about 315 px,
21 px gaps. That gives a tile about 1900 px tall; a full column is 1038 px wide and a half column
508 px. Windows Blur is 20 dp (53 px), which is also the margin.
Effect route = full-resolution layer (w+2m)(h+2m)x4. Blur = quarter-scale layer. One pong screen
is 10,419,840 B (9.94 MiB).

| Surface | Blur layer (quarter) | Effect layer (full res) |
|---|---|---|
| Full column 1038x1900 | 574,288 B (0.55 MiB) | 9,179,456 B (8.75 MiB) |
| Half column 508x1900 | 309,232 B (0.29 MiB) | 4,926,736 B (4.70 MiB) |
| Stacked half 508x940 | 161,392 B (0.15 MiB) | 2,568,976 B (2.45 MiB) |
| Dwindle sixth 508x620 | 112,112 B (0.11 MiB) | 1,783,056 B (1.70 MiB) |
| Float 1080x1200 | 388,476 B (0.37 MiB) | 6,195,664 B (5.91 MiB) |
| Float 700x900 | 203,616 B (0.19 MiB) | 3,243,344 B (3.09 MiB) |
| Control bar 1080x315 | 125,928 B (0.12 MiB) | 1,997,224 B (1.90 MiB) |

Two facts carry the design:

- **On-screen tiles sum to about one screen, whatever their number.** Tiles do not overlap, so 1
  or 6 visible tiles cost about one screen of effect layers plus a margin ring per tile
  (6 Dwindle tiles: 10.2 MiB). Their blur layers together are about 1/16 of that. **Off-screen
  windows cost no layer pixels**: Terl sets off-screen places `INVISIBLE`
  (`TilingDesktopView.java:593`) and HWUI drops their layers.
- **A crossing shows two places**, so for its 260 ms the on-screen area is up to two screens.

| Case | Hot surfaces | Effect bytes, every window on the effect route | Effect bytes with tiles on the parity route | Blur layers | Pool |
|---|---|---|---|---|---|
| Typical at rest: Scrolling, 2 half tiles visible, 1 Float, bar | 4 | 17.2 MiB (1.73 screens) | **7.8 MiB** | 1.08 MiB | 6 MiB (only while the bar or pinned Floats ride a moving stack) |
| Typical crossing: two such Workspaces mid-slide | 6 | 26.6 MiB (2.68 screens) | **7.8 MiB** | ~1.7 MiB | 6 MiB |
| Worst: 5 Workspaces x 6 Dwindle tiles, crossing (12 tiles on screen), 5 Floats of 700x900 (3 own and 2 pinned), bar, live scene on | 18 | 37.8 MiB (3.8 screens) | 17.4 MiB without a scene; with a scene the tiles need their scene ground, so up to 37.8 MiB | 2.4 MiB (+ scene ground ~2.4 MiB) | 6 MiB |
| Warm ring (any case) | up to ~2 x 6 + 2 columns | **0 pixel bytes**: views, `RenderNode` display lists, registry entries | | | |
| Cold (the rest of 5 x 6 = 30 windows) | 0 | 0 | | | |

Today's 8-slot rule would leave most of these windows on Wallpaper in the worst case: bar + 5
Floats + 2 tiles = 8. With the parity route, most of them need no slot to look Live.

---

## 4. The protocol

### 4.1 Two axes: kind and residency

- **Kind** (ADR 0029, unchanged): every window registers once with `wants = LIVE`. It is granted
  Live by the policy (`LiveBlurPolicy` + the registry's budget) and re-granted only on a policy
  event.
- **Residency** (new, inside the service): which of a Live surface's layers hold bytes, and which
  route draws them. Residency follows the rings. The route follows the arrangement. Neither
  follows motion.

### 4.2 Rings

| Ring | Membership (pure, from `TilingLivePolicy`) | Holds | Bytes |
|---|---|---|---|
| **Hot** | any of the window inside the screen at the current position | view `VISIBLE`; ground layer (shared stored frame); on the effect route also the local blur layer, a scene ground if any, and HWUI's effect layer; pages layer for chrome over a moving wall | area-bound (§4.6) |
| **Warm** | one gesture away: columns within 1 screen width (today's `liveWindows`); the levels above and below (today's `isLiveLevel`); the **predicted landing** (fling settle target, Workspace-list row or Overview row under the finger); in navigation mode, the ring around `navigationCentre` | views built and laid out at their final size (no pty resize, the D11 rule); glass entry registered and granted; the stored frame for its radius resident; the chrome's pages layer for that level captured at rest | no layer pixels (display lists only) |
| **Cold** | everything else | nothing: views dropped (`keepPages`), entries unregistered | 0 |
| Floor (shared) | the stored frame per radius (`WallpaperBlurCache`) | what a window can always draw | ~1 MB per radius, already budgeted |

### 4.3 Routes inside the hot ring

`GlassResidency.route(entry)` is pure and is decided when `TilingDesktop.render()` lays out an
arrangement (and at policy events), never per frame:

- **Parity**: the beneath set is the ground alone and the ground is the stored frame. The window
  draws `WindowLook.glass(...)` (today's `wearGround`): the stored frame aimed where it stands,
  through the refraction program on the paint. No blur node, no effect layer, no pre-draw walk.
  **This is still the Live kind**: the picture is what is beneath it.
- **Effect**: the beneath set holds anything but a still ground. That covers a Float, a carried
  window, the bar, a sheet, or a tile over a live scene. The surface gets `LivePicture` + effect
  layer as Floats do today.
- The beneath set is computed from window rects and z-order in the arrangement (tiles, then Floats
  in order). It needs no view walk: floats above tiles, tiles over ground. A Float dragged across
  tiles does not change any tile's route, because it is above them.

### 4.4 Promotion: what triggers it and how far ahead

Leads are worked out from the measured capture limit (≤ 4 frames, 44 ms at 90 Hz) and the 260 ms
slide. The real p90 is owed (§5).

| Intent signal | Promote | Lead it gives |
|---|---|---|
| Arrangement change (`render()`) | recompute rings and routes for every live level | at rest |
| Finger down on a border hold, the bar's slide-up, or the navigation button | the target side's warm windows → ready (pages captured for the bar and pinned Floats if stale) | border hold: the hold delay (≥ 300 ms even when shortened) |
| Fling release (navigation mode, strip) | the settle target becomes warm at release; the origin stays warm until settle ends plus dwell | settle time (≥ 200 ms) |
| Workspace list: finger resting on a row ≥ 120 ms | that Workspace warm | until let-go |
| Overview: a row or card under the finger ≥ 120 ms | that Workspace warm (its cards stay schematic: "Live pictures in Overview" is out of scope) | until tap |
| Navigation mode moves | warm ring follows `navigationCentre`, with hysteresis (§4.5) | ≥ half a screen of travel |
| Far jump with no prior signal (leader+N, a notification) | cold → hot at once: the arriving windows draw the parity picture on the first frame (it is resident), effect surfaces record in that frame's pre-draw (a display-list reference, not a capture), pages draw live until their capture lands (ADR 0029: "one holding none draws live") | none needed for glass; view build cost is owed (§5) |

Rule: a place must be warm **at least one measured capture p90 + one frame** before its first
pixel shows. A signal that cannot give that lead (the far jump) relies on the never-empty rules in
§4.7 rather than holding the motion. If a hold is ever added, it carries a backstop of 2 frames
(AGENTS.md: never gate drawing without a fail-safe).

A cheap prefetch for HWUI layers: a crossing's arriving level goes `VISIBLE` on the finger-down of
the gesture's door, not on the first moved frame. Its effect layers (and the parity draws) then
allocate on the frame that already carries the drag-start cost (37–51 ms today), not mid-travel.
Whether HWUI allocates layers for nodes positioned off screen is **owed**.

### 4.5 Demotion, eviction and hysteresis

- **Distance and dwell, both.** A window enters warm within 1 screen width (or 1 level) and leaves
  only beyond 2 widths (or 2 levels) **or** after 2 s outside the enter ring, whichever comes
  later. Back-and-forth scrolling across a column edge never drops and rebuilds views.
- **Navigation centre hysteresis.** The centre moves only when |position − centre| > 0.75 (not 0.5
  as `Math.round` does). That leaves a 0.25-level band where a hovering finger changes nothing.
- **Routes never change in motion.** A route that must change (a Float closed, so a tile's beneath
  is ground again) changes at the next rest, as a policy event (ADR 0029 §4).
- **Layer churn.** A hot surface going `INVISIBLE` lets HWUI drop its layer, and the
  `LivePicture` object stays while warm. Recording again costs a display-list reference.
- **Eviction order under budget or trim, first to go first:**
  1. cold views of the farthest places, least recently shown first (ADR 0023 §5's farthest-first);
  2. warm windows beyond the adjacent ring (predicted landings outlive plain neighbours);
  3. the pages pool's pictures (today's `trimPages`, at `TRIM_MEMORY_RUNNING_LOW`);
  4. effect routes of Home widgets while Terl is on a Workspace (they are cold anyway);
  5. tiles over a live scene → parity over the scene's last stored frame (looks frozen, never
     empty);
  6. Floats, newest of the worst priority first → Wallpaper kind (today's eviction,
     `GlassRegistry.newestLiveWorseThan`);
  7. the bar and the open sheet last.

### 4.6 Budgets by RAM tier

There are two budgets, each a constant beside its code with a test (performance.md rule 2):

**A. Effect area** (full-resolution layer pixels of hot effect-route surfaces), in screens of the
device's own size. It is checked at rest against *this place's hot set plus the heaviest
neighbour's*, so any one crossing fits:

| RAM (`ActivityManager.MemoryInfo.totalMem`, `isLowRamDevice`) | Effect area | On pong's screen | Blur + scene layers | Pages pool | Warm windows (count) |
|---|---|---|---|---|---|
| more than 8 GiB (pong) | 2.5 screens | 26.0 MB | ≤ 1/16 of area ×2 (scene), ~3.3 MB | 6 MiB | 16 |
| more than 6 up to 8 GiB | 1.75 screens | 18.2 MB | ~2.3 MB | 6 MiB | 12 |
| more than 4 up to 6 GiB | 1 screen | 10.4 MB | ~1.3 MB | 6 MiB | 8 |
| 4 GiB or less, or low-RAM | 0.5 screen (bar + one Float); tiles always parity, scene ground off | 5.2 MB | ~0.7 MB | 2 MiB (eighth) | 4 |

On pong the worst resident Live total is about 26.0 + 3.3 + 6.3 ≈ **35.6 MB** during a crossing,
and about **15 MB** at rest in the typical case (7.8 MiB effect + 1.1 MiB blur + pool). Compare
today's actual worst on Home: 15.5 MB counted + ~21.6 MB uncounted. The typical case fits the
current `idle PSS + budget` gate. **[reasoned]**

**B. Per-frame walkers** (UI thread): at most **6** effect-route surfaces may run the recorder's
pre-draw on one frame. The ablation's ~1.5 ms UI for Live planning across today's handful of
surfaces suggests about 0.3–0.5 ms each in motion **[reasoned]**. Parity surfaces walk nothing.
The seventh and later surfaces by priority go to Wallpaper at rest.

`GlassRegistry.LIVE_SLOTS = 8` stays as a **count cap on effect-route surfaces**, which become a
subset. Parity windows do not take a slot. That keeps `LiveBackdropBudgetTest` meaningful and
adds an area test beside it.

### 4.7 The first frame: never empty, never a pop

- Parity windows draw the stored frame the frame they show (it is the floor and is resident).
- Effect windows draw their backdrop layers from the first frame: the ground from the stored frame
  and the local layer recorded in that frame's pre-draw. The pages layer draws pages live until its
  capture lands, as ADR 0029 already rules. The picture never swaps at landing because it is the
  same layers at rest and moving (ADR 0029 §3).
- If the stored frame for a new radius is not resident (cold start, a Blur change), the window
  shows its tint or frost for a frame or two (ADR 0029 Consequences). Warm-up: Terl requests the
  Windows radius's stored frame when the desktop starts and when Blur changes, not on first show.
- A route change (parity ↔ effect) happens only at rest and only after `LiveStoredParity` holds:
  the two routes must look the same on pong side by side, or it is a ship-blocking pop (the
  2026-11-07 no-pop gate).

### 4.8 Trim and background

| Signal | Drops |
|---|---|
| `TRIM_MEMORY_RUNNING_MODERATE` | cold views beyond the adjacent ring; warm count halves |
| `TRIM_MEMORY_RUNNING_LOW` and above, and `TRIM_MEMORY_UI_HIDDEN` (includes backgrounding) | the pages pool (today); every warm picture; views of levels other than the one in front; `setMemoryShort(true)` → Floats and scene tiles go to Wallpaper by policy event |
| Back in front | `setMemoryShort(false)`, rings rebuilt around the place in front; first crossing captures pages once (ADR 0029) |
| `TRIM_MEMORY_RUNNING_CRITICAL` / `COMPLETE` | everything but the place in front's views; Live off in effect until the next resume |

Shells keep running in every case. Only views and pixels go.

### 4.9 Live off, older Android

- **Live blur off** (the default, `DEFAULT_VALUE_LIVE_BLUR = false`), battery saver, the
  Low-memory preset, Reduce idle activity: `LiveBlurPolicy.live` is false. Every window is
  Wallpaper (the parity draw, no pool, no blur layers). Floats lose glass on glass, as today. The
  rings still govern views.
- **Below Android 13** (`LiveBlurPolicy.MIN_SDK = TIRAMISU`, set by Glass refraction; `RenderNode`
  blur itself needs Android 12): the same as Live off. **Android 8–11**: sheets keep their
  software Snapshot (~2.1 MB while open, ADR 0021 §5); windows are stored.
- Whether Terl turns Live blur on by default is a developer decision (§7).

### 4.10 Frame rules (AGENTS.md)

- Ring and route decisions run at `render()`, at gesture doors (finger down, release), at
  navigation-centre changes (already a `render()`), and at policy events. They never run per
  frame and never inside measure, layout or draw. Visibility flips compare before setting.
- Nothing animates to stream. Promotion is a state change. At rest nothing draws (parity windows
  have no recorder; effect windows record only when something beneath changed).
- Do not throttle the blur to save time (performance.md rule 5: it tripled jank). Make each frame
  cheaper, which is what the parity route does.

### 4.11 Where it plugs in (no parallel system)

| Part | Change |
|---|---|
| `glass/GlassEntry` | add `PRIORITY_TILE` between `PRIORITY_FLOAT` and `PRIORITY_WIDGET` (Floats are windows too and keep theirs) |
| `glass/GlassResidency` (new, pure) | rings, routes, the area budget by RAM tier, eviction order; tested like `GlassPagePool` (`GlassResidencyTest`, with budget constants) |
| `glass/GlassRegistry` | `LIVE_SLOTS` counts effect-route surfaces only; route is part of the grant; "the arrangement changed" and "rings changed at rest" become policy events |
| `glass/GlassService` | `declare(owner = "terl:<level>", entries)` per live level registers all its windows at once; `Redress.onGranted` carries the route; `livePicture` attaches only on the effect route |
| `glass/GlassCover` | unchanged: Overview, Workspace list, search and drawer at rest cover the windows, which give up effect routes (ADR 0029 §4) |
| `chrome/LiveBackdrop` | an area accumulator beside `BUDGET_BYTES`; `trimPages` / `releaseAll` gain the warm drop; the pre-draw skips parity surfaces |
| `chrome/GlassPagePool` | unchanged tiers. Terl's slots: bar + 2 pinned Floats x 3 levels = 9 slots, the arithmetic it already has |
| `tiling/TilingLivePolicy` | rings with enter/leave distances and dwell, `navigationCentre` hysteresis, predicted landing input |
| `tiling/desktop/TilingDesktopView` | `WindowFrame` registers a `GlassEntry` (it registers nothing today) and wears parity (`wearGround`) or a `LivePicture` by its grant; the view implements `LiveBackdrop.Wall` (levels as pages) and `Container`, so the bar and Floats record through it with a frozen pages layer instead of re-recording the moving stack every frame |
| `TermuxActivity.onTrimMemory` | forwards the trim table above |

### 4.12 Surfaces to hit

Scrolling and Dwindle; full, half and third columns; stacked columns; one window and six;
Floats, pinned Floats and Home floats; full screen and maximised; keyboard up and down (the bar
folds to one row and the room changes, which is a `render()`); navigation mode; Overview and the
Workspace list covering; light, dark, black, Material You; live scene on and off; Live off;
Android 12 and the API 30 emulator; landscape rail.

---

## 5. Measurements owed on pong (and pass/fail)

Take them in one session per comparison (temperature moves sections about 1.5x between sessions,
`glass-swipe-frozen-backdrops-2026-11-07.md:62`). Record the display mode beside every frame
number. Ask before touching pong (AGENTS.md).

| # | What | Tool | Pass |
|---|---|---|---|
| 1 | Effect-layer reality: GPU layer bytes with 0, 1, 2, 3 Live windows and a Float | `dumpsys gfxinfo com.termux` (Layers Total / GPU cache lines), `dumpsys meminfo com.termux` (TOTAL PSS, Graphics, GL mtrack), 3 readings | layer bytes within ±15% of §3's arithmetic; else re-derive the budget from the measured bytes |
| 2 | Release: Home → another app → back; and `INVISIBLE` levels | same, plus `am send-trim-memory com.termux RUNNING_LOW` | layers and pool at 0 in the background; idle PSS back within noise of Live off |
| 3 | Route cost in motion: strip scroll and Workspace crossing with tiles parity vs effect | perfetto (`Choreographer#doFrame`, RenderThread `DrawFrame`, "flush layers", the `LiveBackdrop` sections; add `Glass.record` and `Glass.residency` sections); method of `swipe-perf-ablation` (skipped > 16.7 ms, App Deadline Missed) | parity: within noise of today's stored windows; effect: RT `DrawFrame` p90 ≤ 11.1 ms; skipped share ≤ +1 pt over stored |
| 4 | Per-surface pre-draw cost | perfetto per-surface sections, 1 to 8 effect surfaces | sets the walker cap (§4.6 B); fail if one surface > 0.7 ms UI |
| 5 | Capture latency: `PageLayerCapture` start → land | motion log line (`launcherctl motion-log dump`) + perfetto | p90 ≤ 4 frames; sets the promotion lead |
| 6 | View build cost: a cold Workspace of 6 terminals (and one Linux window) made warm | perfetto around `TilingDesktop.render` / `bindWindows`; meminfo before/after | build ≤ 1 frame per window or moved to a warm-up slice; per-window bytes recorded (TextureView buffers especially) |
| 7 | First-show allocation: the frame a level turns `VISIBLE` | perfetto (`DrawFrame` on that frame, layer allocations) | no frame > 2x budget beyond today's drag-start frame; tells whether VISIBLE-at-finger-down prefetch works |
| 8 | Thrash: scripted back-and-forth scroll across a column edge, nav-mode hover at 0.5 levels | motion log residency lines (promote/demote with ring and bytes) | zero demote→promote of one window inside the dwell |
| 9 | At rest | `dumpsys gfxinfo` over 30 s untouched, Workspace with Floats; and with the clock widget on Home | frame count equal to Live off (nothing draws at rest); seconds-hand frames under a Float no worse than today's +9 ms |
| 10 | No pop: before / during / after screenshots of a crossing and of a route change at rest | `screencap -j` on pong (only when the developer is off the phone), one `scripts/pong-round.sh` recording | first and last swipe frames identical to rest; parity vs effect indistinguishable side by side |
| 11 | Live scene ground per tile | perfetto with Contour on; `postAndWait` | `postAndWait` ≤ 1 ms avg (the hardware-buffer stall must not return) |

Gates for the whole change: idle PSS ≤ today's + the at-rest typical budget (~15 MB) on pong;
`pong.txt` and the Terl swipe scenarios ≤ +1 pt janky; at rest unchanged. A miss is reported red
with the numbers. Nothing is tuned away (performance.md "Done").

---

## 6. Risks

- **HWUI owns the layer bytes.** Freed layers may linger in Skia's resource cache until a trim, so
  measured PSS can sit above the arithmetic (measurement 2). The area budget bounds what is drawn,
  not what the cache keeps.
- **The effect layer was never counted.** Today's 15.5 MB budget understates real Live memory by
  roughly the same amount again. Fixing the accounting may show today's Home already above the
  gate the docs imply.
- **UI-thread walk scales with surfaces.** Without the parity route, six Live tiles plus Floats
  plus bar would add several ms of pre-draw per motion frame (ablation: glass is about half of
  every swipe frame).
- **Live scene grounds per tile** risk the hardware-buffer `postAndWait` stall (7.4–8.4 ms) if a
  tile samples the scene frame directly. Keep the scene ground on the recorder's layer path, never
  a `BitmapShader` over a hardware buffer.
- **Parity must really be parity.** Soft edges and calibration (`LiveStoredParity`) were checked on
  the dock and keyboard, not on window-sized slabs with the Windows Opacity. A visible difference
  makes route changes pops.
- **ADR 0029 reading.** The proposal holds that a route is residency inside the Live kind, not a
  kind change. If the developer reads it as a kind change, route changes must wait for policy
  events only (they already do here), and the parity route must be argued as "the Live picture of
  a ground".
- **Window content dwarfs glass.** A Linux window's `TextureView` keeps 2–3 window-sized buffers
  (about 8 MB each at full column size **[reasoned]**). The rings decide that memory too.
  Measurement 6 must size it, or glass gets blamed for it.
- **Far jumps fly over cold levels.** A 260 ms slide from Workspace 1 to 5 passes levels holding no
  views. They show bare air over the wallpaper, which may read as a flicker of empty places.

## 7. Cheapest fallback, and what the developer decides

**Fallback if the budget cannot hold every window on the effect route:** keep today's machinery
and add only the parity route. Tiles are Live through parity (the stored frame is what is beneath
them). The effect route is limited to the bar, open sheets, and Floats by priority, capped by
today's 8 slots. Tiles over a live scene take parity over the scene's last stored frame. That is
one pure policy plus `WindowFrame` registration, and costs about 7.8 MiB effect + 1 MiB blur at
rest in the typical case. The floor below that is Live blur off: every window stored, 0 extra
bytes.

**Decisions for the developer:**

1. Is a tile on the parity route "live" enough? (It shows exactly what is beneath; the only
   difference is that no blur runs for it.)
2. Does Terl turn Live blur on by default, or keep the switch off by default as today?
3. Over a live scene, do tiles follow the scene (effect route, area budget), or hold its last frame
   (parity)?
4. Far jumps: slide through empty levels, or slide as if the arriving place were adjacent?
5. The tier numbers in §4.6 are a starting point. Measurements 1 and 3 set them.
