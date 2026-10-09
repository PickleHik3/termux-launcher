# In-app animated wallpapers: spec (draft)

> **Superseded on the built-in parts, 2026-10-04.** The ten pre-made AGSL backgrounds this spec describes
> were removed in the Appearance round (`appearance-round-2026-10-04.md`, "Removal of the pre-made
> backgrounds"); living stills (`living-stills.md`) are the only animated backgrounds now. The moments,
> the rest pose, the Director and the Fancier Glass gate below still apply.

Written 2026-09-29. Status: draft; open questions 1–4 settled 2026-09-30 (§13), the rest open.
Nothing is built. Target: the next release (decided 2026-09-30); tracked in
`project-docs/backlog.md`.
Amended 2026-09-30 by [`generated-backgrounds-issue.md`](generated-backgrounds-issue.md) (#41), which
adds the moments (unlock, lock, pane open and close, page change, touch, bell), the Material
palette and the rest pose. Where the two disagree, the issue wins.
Related: ADR 0002 (the pre-blurred wide frame; real-time blur "stays an experiment for a future
live wallpaper"), ADR 0004 (every glass surface samples the shared frame), ADR 0005 (Fancier Glass
refracts it), `project-docs/active/fancier-glass/SPEC.md` §1, §3, §5.

The launcher draws an animated wallpaper itself, behind everything, and every glass surface keeps
sampling one shared blurred frame. That frame becomes live: it is re-blurred when the wallpaper
advances a frame, once per radius in use, at low resolution. When the wallpaper is paused the last
frame stays, and it costs nothing.

The audience is early-to-moderate power users. The rule: no new settings page. The choice is made
in the wallpaper picker, and the launcher decides when to play.

Anything marked **(assumption)** was not verified in code or on pong.

## 1. Scope

In:
- Procedural AGSL wallpapers built into the APK (phase 1, API 34+).
- A live shared frame that every existing glass surface reads, in both the default mode and
  Fancier Glass.
- One pure policy that decides when to play, at what rate, and when to pause.
- The picker, `launcherctl wallpaper`, and the still that the system is given.

- A system `WallpaperService` for the **lock screen only** (`LockLiveWallpaperService`, API 34+),
  added 2026-10-03 by [`lock-live-wallpaper.md`](lock-live-wallpaper.md). It animates the Lock
  slot's background on the keyguard and settles to the rest pose as the unlock starts.

Out:
- A system `WallpaperService` on the **home screen**. The launcher cannot blur it per surface
  (Fancier Glass §1), and under one the glass falls to `WallpaperPicture.NO_STILL` (no blur at
  all). The home screen keeps the self-drawn animation over the rest-pose still.
- User-supplied shaders. A shader is code, and packs carry no code (Fancier Glass §5).
- The unlock pair (Fancier Glass §5 mode 2). It is a clip that ends on a still, and it is not
  affected by this spec.
- Video and GIF loops. They are phase 2 (§6.2) and may be dropped.

## 2. What exists today

| Piece | What it does | What changes |
|---|---|---|
| `chrome/WallpaperBlurCache` | One frame per radius, LRU (6 radii, 72 MB). The frame rect is 1.5× wide. It is invalidated by the system wallpaper id, the managed file's mtime and length, the orientation and the rect. `obtain(radius, view)` is called from 7 sites. | Nothing. It keeps holding the **still** (the first frame). |
| `chrome/WallpaperBlurRenderer` | RenderScript blur with a 25 px cap. Downsample = `min(4, ceil(radiusPx / 25))`, so 16–19 dp on pong is ÷2 per side (a quarter of the area), and under about 9.5 dp is full size. | Nothing. Live frames don't use it. |
| `chrome/WallpaperBackdropView` | Draws the radius-0 frame, translated by the parallax, with the dim on top. | Gains a live mode that draws the wallpaper shader directly (§3.5). |
| `chrome/WallpaperParallax` | One offset, written per frame of a slide and read by each surface in its own draw. | Nothing. It is the model for `LiveWallpaperFrames`. |
| `chrome/SharedFrameDrawable` | Routes (a): a `BitmapShader` aimed per draw, or `GlassRefraction.Program` with the shader as its `content` input. | Picks the live bitmap at draw time (§3.3). |
| `terminal/PaneGlassBackdropView` | Route (b): the same, for the pane slabs. | The same change. |
| `wall/PaneControlsView` | Corner tabs. They build their own `BitmapShader` from the frame. | The same change. |
| `chrome/WallpaperFrostPainter` | Installs `SharedFrameDrawable`s on the top bars and the off-dock sheets. | Nothing. It gets live frames through the drawable. |
| `chrome/ChromeInk.sampleWallpaper` | Picks the ink colour from the resident frame. | Nothing. It keeps sampling the still (§8). |
| `TermuxActivity.syncWallpaperParallax` | Invalidates every frame reader when the offset moves. | Its reader list is extracted as `invalidateFrameReaders()`, which the live clock also calls. |
| `chrome/FancierGlassPolicy`, `WallpaperPictureReader.managedPictureOnScreen` | The Fancier Glass gate: the stored system id is current and the exact copy is on disk. | Nothing (§7.3). |

## 3. Architecture

### 3.1 The decision

- The launcher renders the wallpaper in its own process, behind everything. The self-drawn
  backdrop (`WallpaperBackdropPolicy.Mode.SELF_DRAWN`) is required. In passthrough mode the
  animation never plays.
- One offscreen renderer produces a **live blurred frame per radius in use**. The frame covers the
  same rect as the cache's frame (`frameRectRef()`, 1.5× wide), at ÷4 per side. It is re-rendered
  only when the wallpaper advances a frame.
- The surfaces keep their frame from the cache (the still). At draw time, on a hardware canvas,
  they read the live bitmap for their radius from one shared object, `LiveWallpaperFrames`, the
  way they already read `WallpaperParallax`. A software canvas keeps the still.
- The cost is one render and blur pass per live radius per wallpaper frame, for the whole screen.
  The surface count doesn't matter.

### 3.2 Where this differs from the brief

1. **The blur cache stays still.** The brief makes the shared frame itself live. The code argues
   against that. `obtain()` has 7 call sites, `ChromeInk` reads pixels from the frame, and three
   software-canvas paths (`RealtimeBlurView`, the departure card, `PaneSnapshot`) draw it. A
   hardware bitmap throws in all of them. With a live overlay read at draw time, those callers and
   the cache's invalidation stay untouched.
2. **One pass per radius, not one per frame.** A look uses several radii (pong stores 8, 16, 17
   and 19 plus 0). §3.4 caps the live set.
3. **"Quarter resolution" means ÷4 per side here.** The cache's "quarter" is ÷2 per side, forced
   by RenderScript's 25 px cap. The GPU blur has no cap, so live frames at radius 12 dp and above
   use ÷4 per side, which is 1/16 of the area.
4. **The blur is not the expensive part.** Each wallpaper frame damages the whole window, so HWUI
   replays every display list (terminal text, icons, chrome) at the wallpaper's rate. No view is
   re-recorded except the frame readers, but the GPU composite is full-screen. That is the number
   to measure (§9).

### 3.3 How a live frame reaches the surfaces

The surfaces take a `Bitmap` and wrap it in a `BitmapShader`. Fancier Glass passes that shader to
`RuntimeShader.setInputShader("content", …)`. A `RenderNode` can't be a shader input, so the live
frame has to be a **hardware bitmap**.

- **Renderer: `HardwareBufferRenderer` (API 34+).** It renders a `RenderNode` straight into a
  `HardwareBuffer` that we own. We keep a fixed ring of 3 buffers per live radius. Each buffer is
  wrapped once with `Bitmap.wrapHardwareBuffer`, and each surface builds one `BitmapShader` per
  slot and keeps it. A frame then allocates nothing: the surface swaps to the next slot's cached
  shader and calls `setInput` on its program (a child rebind, not a compile).
- **API 33 alternative: `HardwareRenderer` into an `ImageReader`.** This is the documented
  "blur a bitmap with `RenderEffect`" pattern. It works from API 29 and needs the blur from 31.
  Each acquired `Image` is a new `HardwareBuffer` object, so the frame is re-wrapped per frame
  unless buffers can be keyed by `HardwareBuffer.getId()` **(assumption: stable per slot)**.
  That means about 1 + N small allocations per frame.
- **Decided (2026-09-30): API 34+ only, one path.** The `ImageReader` path is not built. Pong is
  API 36.
- **Buffer safety:** the renderer writes slot k+1 while the window samples slot k. Slot k−1 was
  last drawn a frame ago. We assume that `HardwareBufferRenderer`'s work and the window's frame are
  ordered on the same RenderThread and GPU queue, and that the draw callback's fence covers the
  write **(assumption; verify with a tearing test, §11)**. If that fails, we use a ring of 4, or
  wait on the fence before publishing.
- **The draw-time pick** in `SharedFrameDrawable.draw`, `PaneGlassBackdropView.onDraw` and
  `PaneControlsView`:

```java
Bitmap live = canvas.isHardwareAccelerated() ? mLive.frame(mRadiusDp) : null;
// live != null: aim at the live slot's size, use that slot's cached shader.
// null: the still exactly as today.
```

  The aim math (`SharedFrameDrawable.aim`) doesn't change. The live frame stands for the same
  `frameRect`, and only its pixel size differs, which `aim` already takes as input.
- **Invalidation:** when a slot is published, the main thread calls `invalidateFrameReaders()`.
  That is the list `syncWallpaperParallax` walks today: the backdrop, the pane glass, the widgets
  and Display pages, the ten frost views and the under-pill strip. Only those views re-record.
  The clock, the terminal text and the tiles are siblings, and their display lists are reused.

### 3.4 Radii

- A live radius is a radius that a visible surface is drawing right now. Before each wallpaper
  frame, the renderer asks the readers which radii they use, and renders at most
  `MAX_LIVE_RADII = 3` of them.
- Radii within 3 dp of each other share one live frame (for example 16, 17 and 19 become 17). The
  difference can't be seen under motion **(assumption; check on pong)**. The still keeps each
  exact radius.
- A surface whose radius doesn't get a live frame draws its still. It looks frozen but correct.
- Downsample: ÷4 per side from 12 dp up, ÷2 below that. Radius 0 is the backdrop (§3.5).

### 3.5 The backdrop (radius 0)

`WallpaperBackdropView` in live mode draws the wallpaper's `RuntimeShader` directly, full-screen,
under the same parallax translate and dim. There is no texture and no buffer. Its time uniform is
set in the same main-thread message that publishes the blurred slots, so the sharp and blurred
pictures change in the same window frame. The glass lags the render start by one frame, but it is
never out of step with the backdrop.

A built-in is smooth, so drawing it at half resolution is an option if the full-screen shader cost
is too high (open question 5).

### 3.6 New pieces (all in `chrome/wallpaper/` unless noted)

| Class | Job |
|---|---|
| `AnimatedWallpaper` | Interface: `id()`, `label()`, `RuntimeShader newShader()`, `setTime(shader, seconds)`, `periodSeconds()`. |
| `Aurora`, `Mesh`, `Tide`, `Rain` | Built-ins. Each is an AGSL string plus uniforms. Coordinates are in frame pixels, so they are resolution-independent. |
| `LiveWallpaperRenderer` | Owns the `HardwareBufferRenderer`s, the source node, one blur node per live radius, and the rings. Runs `render(t, radii)`. Releases everything in `release()`. |
| `LiveWallpaperFrames` | Written on the main thread, read in draws: `frame(radiusDp)`, slot shaders, and a generation counter. It is the `WallpaperParallax` of time. |
| `AnimatedWallpaperClock` | A `Choreographer.FrameCallback`. It holds `t`, steps it at the policy's rate, starts renders and publishes slots. It stops posting callbacks while paused. |
| `WallpaperDirector` | Pure, no Android types. Inputs go in, and per frame comes out fps (0 means paused), shader time, energy, dim, the palette and up to two moment slots. It also owns the moments, the energy and the lock delay. Built like `FancierGlassPolicy` and `WallSlideClock`. Replaces the earlier `AnimatedWallpaperPolicy`. |
| `AnimatedWallpaperStill` | Renders the rest pose (energy 0) at full resolution (1.5× wide) to a software bitmap for `ManagedWallpaper.apply`. |

The render graph per frame:
- the **source node** (frame rect ÷4) is `drawRect` with the wallpaper's shader;
- each **blur node** has `setRenderEffect(createBlurEffect(r/4, r/4, CLAMP))` and draws the source
  node;
- each blur node goes into its ring slot.

The source shader runs once per live radius. At about 0.24 Mpx each, that is negligible
**(assumption)**.

## 4. Alternatives compared

| Option | Cost per wallpaper frame | Complexity | Verdict |
|---|---|---|---|
| **A. One live frame per radius (HardwareBufferRenderer + RenderEffect blur)** | One quarter-res shader eval and one blur per live radius, plus the full-screen backdrop shader | One renderer, one draw-time pick in three readers | **Chosen.** It keeps a true Gaussian that matches the still's look, works for any source (video too), and its cost doesn't grow with the surface count. |
| B. The same with an AGSL blur (API 33) | The same, with a hand-written 2-pass blur | We own the blur code and still need a renderer for the second pass | Only if `RenderEffect` blur is too slow at ÷4 (unlikely). |
| C. Procedural shader with the blur built in (a low-pass uniform), used as every surface's paint shader (no texture) | One shader eval per glass pixel per frame, per surface | No buffers at all, but every wallpaper needs a hand-made "blurred" variant, and radius changes the look | Rejected. The blur is approximate, it doesn't match the still, it can't run on a software canvas, and it has no path to video. |
| D. `RenderEffect` blur per surface (ADR 0002's rejected option) | One blur per surface per frame | Splits the frost tuning into two paths | Rejected again, for ADR 0002's reasons. |
| E. `Bitmap.createBitmap(Picture)` per frame | One hardware bitmap allocation per radius per frame | The fewest lines | A prototype only, to measure A against. |

## 5. When it plays

`WallpaperDirector` (its fps output):

| Condition | Result |
|---|---|
| No animated id, or `managedPictureOnScreen` is false, or passthrough mode, or SDK < `MIN_SDK`, or Fancier Glass not active (§13 Q2) | Not animated. The still path is today's. |
| Kill switch on (§9.4) | Paused |
| Launcher not visible (`onStop`), screen off, another activity in front | Paused, and the renderer is released after 30 s |
| Lazy mode | Paused |
| Battery saver (`PowerManager.isPowerSaveMode`, re-read on `ACTION_POWER_SAVE_MODE_CHANGED`) | Paused |
| Thermal MODERATE or worse (`TaiBenchGuardRules.THERMAL_STATUS_MODERATE`, listener as in `TaiDeviceConditions.startThermalListener`) | Paused |
| Reduced motion (`ReducedMotion.isEnabled`) | Paused |
| Thermal LIGHT, or battery ≤ 20 % and discharging | 15 fps |
| Otherwise | 30 fps (`MAX_FPS`) |

- **Paused costs nothing.** The clock posts no callbacks. The last slots stay published, so every
  surface keeps drawing its current live frame. Nothing invalidates.
- **Resume is instant.** The renderer and rings stay allocated while the launcher is visible. The
  first new frame renders on the next vsync, and nothing warms up. After an `onStop` longer than
  30 s the renderer is rebuilt on return: a few ms of allocation, while the frozen still shows
  **(assumption)**.
- **Movement never re-renders.** A slide, a pan, a keyboard travel or a pane move changes only
  `WallpaperParallax` and the aim, as today. The frame is 1.5× wide, so parallax works unchanged.
- **Time:** `t` advances only while playing, and it is kept across pauses. A cold start begins at
  0, which matches the system still.
- **Pacing:** on a 120 Hz panel, 30 fps is every 4th vsync **(assumption: pong runs at 120 Hz)**.
  A frame is skipped rather than queued when the previous render hasn't landed.

## 6. Wallpaper kinds and API levels

### 6.1 Phase 1: procedural AGSL (API 34+)

- Four built-ins: Aurora (slow ribbons), Mesh (four-colour gradient drifting; was Gradient flow),
  Tide (soft waves) and Rain (sparse glyph-cell rain). Each has a loop of 60 s or more and low contrast, so the ink sampled from the still stays
  valid (§8).
- Each is under about 60 ALU ops per pixel, with no texture reads **(target, to be checked)**.
- Each has a palette-seed uniform. Recolouring is open question 7.

### 6.2 Phase 2 (maybe): video and GIF loops

- MediaCodec decodes to YUV. `Bitmap.wrapHardwareBuffer` accepts only RGBA-type buffers
  **(assumption)**, and `HardwareBufferRenderer` can't sample a `SurfaceTexture`. So video needs a
  small GLES stage: decoder → `SurfaceTexture` (OES texture) → one draw at full resolution to the
  backdrop's surface, and one at ÷4 RGBA into the ring buffers. The existing RenderEffect blur
  nodes then read those buffers as their source instead of the shader. The blur and all readers
  are unchanged.
- The backdrop becomes a `TextureView` (or the GL stage renders into it) instead of a shader draw.
- It reuses Fancier Glass §5's rules: MP4 only, GIF and WebP converted on import, 30/60 fps, and
  the same pauses.
- Its cost is a hardware decode plus two GL draws per frame, and an EGL context of our own. That is
  why it is optional.

### 6.3 Below the minimum API

- The Animated section is hidden. An animated id stored on a device below the minimum (from a
  backup restore, or a downgrade) shows its still, which is a normal managed still.
- `launcherctl wallpaper set --builtin` sets the still and answers `"animated": false,
  "reason": "api"` (open question 8).

## 7. How it's chosen

### 7.1 The picker

- `openWallpaperPicker` launches the system photo picker straight away today
  (`launchManagedWallpaperPicker`, `PickVisualMedia.ImageOnly`). It becomes a small sheet with
  "Choose a photo…" at the top and an **Animated** row of built-in tiles. Each tile is a static
  thumbnail rendered at the rest pose (energy 0). The tiles are not live, because live previews would cost a
  renderer per tile.
- Tapping a tile:
  1. `AnimatedWallpaperStill` renders the rest pose, energy 0, not t=0 (1.5× wide, portrait size, as `launchWallpaperCrop`
     sizes it).
  2. It writes the exact copy (`managedWallpaperExactFile`) and calls `ManagedWallpaper.apply`.
     The system gets the centre crop, and the stored id follows.
  3. It stores `managed_wallpaper_animated = "<id>"`.
- Choosing a photo, or `launcherctl wallpaper set <path>`, clears the id.

### 7.2 `launcherctl`

- `launcherctl wallpaper set --builtin aurora [--home|--lock|--both]` sends
  `POST /v1/wallpaper {"builtin": "aurora", "target": "both"}`. `path` and `builtin` are mutually
  exclusive (400 `bad_request`). An unknown id returns 404 `not_found`. `--lock` alone sets only
  the still on the lock screen, which never animates.
- `launcherctl wallpaper list-builtins` sends `GET /v1/wallpaper/builtins` and returns
  `[{id, label}]`.
- `GET /v1/wallpaper` adds `"animated": "aurora" | null` and `"playing": true | false`.
- `docs/en/LauncherCtl_API.md` (the wallpaper section around :469–:515) and the tool registry
  (`launcherctl/LauncherToolRegistry`) are updated.

### 7.3 Identity and the Fancier Glass gate

- Animation is active when `managed_wallpaper_animated != null && managedPictureOnScreen`. A
  wallpaper set from outside changes the system id, so the animation stops without a new check.
- The still is set through `ManagedWallpaper.apply`, so `managedPictureOnScreen` is already true.
  `FancierGlassPolicy` needs **no change**: the brief's "extend the gate to managed animated" comes
  for free. The blur cache is keyed on the still's file, so it doesn't churn.
- `WallpaperPicture` is `MATCHES_SCREEN` (no service is running), so the self-drawn backdrop is
  allowed.
- **Decided (2026-09-30, #41):** built-ins are gated behind Fancier Glass (§13 Q2).

## 8. Interaction with existing features

| Feature | Behaviour |
|---|---|
| Clock (`TerminalClockWidget`) | It draws on the glass, as a sibling above the status backdrop. Its ticks invalidate only itself, as today, and never a backdrop. A wallpaper frame invalidates the backdrop views and not the clock. A test counts both (§11). |
| Departure card (`captureTerminalDeparture`), `PaneSnapshot`, `RealtimeBlurView` | Software canvas: the draw-time pick returns null, so they draw the still. The card is frozen by design, so a slight mismatch with frame N is fine. If it shows, one `Bitmap.copy` of the live slot at capture time fixes it (open question 6). |
| Display place stand-in (`wall/SurfaceStandIn`, `PixelCopy`) | It copies the X `SurfaceView` only. Not affected. The pane glass around it stays live. |
| Cursor trail | Independent. It shares the frame budget: trail plus refraction plus wallpaper is one of the test cases (Fancier Glass §8). Battery saver already turns the trail off, and it pauses the wallpaper too. |
| Fancier Glass refraction | Routes (a) and (b) read the live slot's shader as their `content`. Route (c) (the dock and the strip, a `RenderEffect` over their own pixels) re-runs its offscreen pass each wallpaper frame, at dock size **(cost to measure)**. |
| Idle ring (`PaneAttentionGlow`) | A drawable on the pane frame, not the backdrop. Unchanged. Lazy mode already holds its pulse static and pauses the wallpaper. |
| Page slides, border drag, `PageSink`, tilts | Unchanged: registration comes from the anchors and the parallax. A tipping page's hardware layer re-renders when its glass invalidates, so a playing wallpaper under a drag costs one layer update per wallpaper frame **(to measure; pausing during a drag is an option)**. |
| Ink colour (`ChromeInk`) | It samples the still. The built-ins keep low luminance travel so the contrast holds. |
| Material palette / wallpaper colours | The system derives them from the still (the first frame). `OnColorsChangedListener` fires once at set time, and never per animation frame. |
| Rotation | The frame rect changes, so the rings are rebuilt at the new size (the only reallocation outside set and release). Parallax is portrait only, as today. |
| Lock screen, other apps, system app animations | They see the still. |

## 9. Memory, cost and measurement

### 9.1 Memory

The frame rect is 1620×2412 (1.5× of 1080×2412).

| Item | Size |
|---|---|
| One live slot at ÷4 (405×603 RGBA) | ~0.95 MB |
| One ring (3 slots), one radius | ~2.9 MB |
| `MAX_LIVE_RADII` = 3 | ~8.7 MB |
| Source node layer, renderer overhead | ~1–3 MB **(assumption)** |
| Backdrop | 0 (drawn from the shader) |
| **Added total** | **≈ 10–12 MB**, on top of the still cache (unchanged, ≤ 72 MB) |

Everything is released on `onStop` + 30 s, `onTrimMemory(RUNNING_LOW)` or higher, and on a switch
to a photo.

### 9.2 Targets on pong

Pong is a Nothing Phone (2), A065, API 36, 1080×2412. The Snapdragon 8+ Gen 1 chip is an
**(assumption)**.

- Live render and blur, all live radii together: **< 1.5 ms GPU per wallpaper frame**.
- The backdrop shader at full screen: < 1 ms GPU.
- Window frames while playing on each place: p90 < 8 ms, and no frame above 16.7 ms that isn't
  there with the wallpaper paused.
- While paused: idle fps 0 and CPU as with a still. The framestats must match a still wallpaper.
- Battery: drain per hour while playing on Home at 30 fps, against a still. The budget is set from
  the first build (as Fancier Glass §5 does for video).

### 9.3 How to measure

- The render runs outside the window's `gfxinfo`. Trace sections `LiveWallpaper.render`,
  `LiveWallpaper.publish` and `LiveWallpaper.pick`, plus the `HardwareBufferRenderer` callback's
  timestamps, give the render cost. A Perfetto trace with GPU counters gives the GPU time.
- `dumpsys gfxinfo com.termux framestats`, playing and paused, on Home, Terminal and Display.
- A debug counter of `onDraw` calls per view class over 10 s, to prove the clock and the terminal
  text aren't re-recorded.

### 9.4 Kill switch

- A preference `animated_wallpaper_disabled`, not shown in Settings, settable through the
  existing preference route **(assumption: such a route exists)**. It pauses everything and shows
  the still.
- A self-check, in tiers. Renders are not counted during a warm-up (`RenderBudget.WARMUP_MS`, 2 s,
  and at least `WARMUP_RENDERS`, 60 renders, whichever is later) after every clock start, renderer
  (re)build, screen-on and unlock: those first renders queue behind the unlock's UI work, run at low
  GPU clocks and carry the shader compile. After the warm-up, a window of 120 renders whose p90 is
  above 4 ms steps the Director down a tier instead of killing: tier 0 is 30 fps, tier 1 is 15 fps,
  tier 2 is 15 fps with the cheaper (÷4) source resolution, tier 3 is 10 fps. Each tier gets a
  fresh renderer, a fresh window and the warm-up. Only the lowest tier failing a window, or a
  render that throws, is a kill. Each window close logs p50, p90, tier and the warm-up renders
  skipped; each step-down, kill and retry logs once.
- A kill lasts until the next screen-on, unlock or `onStart`, when the renderer is rebuilt and tried
  again from tier 0. After 3 kills in one process the still stays, and `GET /v1/wallpaper` keeps
  reporting `killed`. Choosing a different background, or re-applying the same one while it is
  killed, starts over.
- `GET /v1/wallpaper` adds `tier` (0 is full) and `kills` (count this process).
- The A-Z lock rest pose is left on `USER_PRESENT`, and also on `onStart` or `onResume` after a
  screen-off (some ROMs and face or smart unlock never send `USER_PRESENT`); one unlock per
  screen-off, whichever signal comes first.

## 10. Phased plan

**Phase 0: measure the premise (1 day).** Build option E as a throwaway behind a debug flag: one
radius, Aurora, backdrop live. Measure §9.2 on pong. Go or no-go on the render cost and the
full-window composite.

**Phase 1a: the pipeline.**
- New: `chrome/wallpaper/AnimatedWallpaper`, `Aurora`, `LiveWallpaperRenderer`,
  `LiveWallpaperFrames`, `AnimatedWallpaperClock`, `WallpaperDirector` (with unit tests like
  `FancierGlassPolicy`'s).
- Touches: `SharedFrameDrawable.draw`, `PaneGlassBackdropView.onDraw` and
  `PaneControlsView` (the draw-time pick and the slot shader cache); `WallpaperBackdropView` (live
  mode); `TermuxActivity` (the clock's lifecycle in `onStart`/`onStop`, `invalidateFrameReaders()`
  extracted from `syncWallpaperParallax`, power, thermal and lazy inputs).

**Phase 1b: choosing.**
- New: `AnimatedWallpaperStill`, the picker sheet, and `Mesh`, `Tide` and `Rain`.
- Touches: `openWallpaperPicker`/`launchManagedWallpaperPicker`, `ManagedWallpaper` (applying a
  generated bitmap), `TermuxAppSharedPreferences` (`managed_wallpaper_animated`),
  `launcherctl/DeviceControlRoutes`, `LauncherToolRegistry`, `docs/en/LauncherCtl_API.md`.

**Phase 1c: records.**
- ADR 0006 "The shared frame goes live for in-app animated wallpapers", amending 0002 and 0004.
- A backlog row. The user docs page on wallpapers.

**Phase 2 (optional): video.**
- New: `chrome/wallpaper/VideoFrameSource` (MediaCodec, EGL, OES → rings), and an import path.
- Touches: the renderer (its source is the shader or the video), and `WallpaperBackdropView` (a
  `TextureView` child). It supersedes Fancier Glass §5 mode 1's plan.

## 11. Tests

- Unit: `WallpaperDirector` over every row of §5. The radius quantisation. The draw-time pick
  (software canvas → still). `LiveWallpaperFrames`' slot rotation.
- On pong:
  - §9.2 frame timing on the three places, playing and paused, in the default mode and Fancier
    Glass;
  - the cursor trail cases from Fancier Glass §8 while playing;
  - a slide, a border drag and a keyboard travel while playing (registration: no offset between
    the backdrop and the glass);
  - the clock and terminal `onDraw` counters;
  - a tearing test: 60 fps with a high-contrast debug shader and a recorded screen, then look for
    split slots;
  - pause and resume on screen off/on, battery saver, Lazy mode, reduced motion, and a forced
    thermal status (`cmd thermalservice override-status 2`);
  - memory with `dumpsys meminfo` before and after, and after `onStop` + 30 s.
- On the HTC (API 28): the Animated section is hidden, and everything else is unchanged.

## 12. Risks

1. The full-window recomposition at 30 fps may cost more than the whole blur budget, on the
   Terminal place especially (a lot of text replayed). Phase 0 exists for this.
2. Buffer reuse ordering in §3.3 may tear. The mitigation is a ring of 4, or a fence wait.
3. `RenderEffect` blur quality at ÷4 for small radii (8 dp) may look blocky next to the still. The
   mitigation is ÷2 below 12 dp (already in the design).
4. Route (c) and the tipping pages' hardware layers re-render per wallpaper frame, which is
   hidden cost.
5. OEM wallpaper handling: some ROMs recompute colours or zoom when the still changes. That
   happens only at set time, never per frame.
6. Scope creep toward user shaders or packs. They are ruled out in §1.

## 13. Open questions for the developer

Settled 2026-09-30:

1. **Minimum API:** 34+, `HardwareBufferRenderer` only.
2. **Gating:** gated behind Fancier Glass (developer, 2026-09-30, #41); API 34 remains its own floor. Amended from "plays with Fancier Glass on or off".
3. **Frame rate:** a fixed 30 fps, 15 under pressure, no setting.
4. **Playing rule:** always while visible, paused only by the §5 conditions.

Still open (numbered as in the first draft):

5. **Backdrop resolution:** the full-screen shader per frame, or half resolution upscaled if
   phase 0 shows it costs more than 1 ms?
6. **Departure card:** is the still acceptable under the card, or should it copy the live slot at
   capture time (one GPU readback per window switch)?
7. **Colour:** settled 2026-09-30 (#41): the Material palette captured at set time and on scheme change, never from `OnColorsChangedListener`; or the background's own palette.
8. **`launcherctl` below the minimum API:** set the still and report `animated: false`
   (recommended), or refuse with 409?
9. **Live radii cap:** is 3 live radii with 3 dp merging acceptable, or must every exact radius be
   live (more passes per frame)?
10. **Phase 2:** keep video on the roadmap (it needs its own GL stage), or drop it and stay with
    procedural wallpapers only?
11. **Pause during a border drag or tilt:** worth it to spare the layer re-renders, or keep playing
    under the finger?
