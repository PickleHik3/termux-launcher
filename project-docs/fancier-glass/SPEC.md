# Fancier Glass — spec (2026-09-27)

Status: agreed with the developer on 2026-09-27. Built since: §4's refraction (ADR 0005) and M8.
Motion mockups: `.lavish/fancier-glass-motion.html`. Prototype wallpaper pack: `~/Projects/termux-launcher/wallpaper-packs/proto-01/`.
Measurements behind the bug list: pong session 2026-09-27; see `project-docs/plans/perf-home-terminal-idle-handoff-2026-09-27.md`.

## 1. Scope

Two layers:

- **The default mode is today's glass, with its bugs fixed.** It is on for every user. Its look and behaviour stay exactly as they are.
- **Fancier Glass is one opt-in toggle.** It turns on AGSL refraction on every glass surface, video wallpapers, and the refraction controls.

Out of scope:
- The Android 17 QPR2 `RenderNode.setBackdropRenderEffect` path. It was rejected as too early.
- Glass over a system live wallpaper. It is not possible per surface.

## 2. Default mode (all users)

Behaviour that stays exactly as it is:
- The same frost, grain, tint and opacity.
- Blur and grain switch off when the wallpaper is a system live wallpaper (`WallpaperPicture.NO_STILL`).
- The key-press lens and the dock/nav-strip refraction (`GLASS_AGSL`, API 33+) stay as they are.

Bug fixes (all versions, API 26+):
1. **One shared frame.** Every glass surface samples the shared pre-blurred frame by its live screen position: panes, dock, keyboard, status bar, window bar, sheets. The per-surface cut-out crops go (this finishes ADR 0002's original plan). Nothing allocates a bitmap during a slide, a pre-roll or a settle.
2. **Pane alignment.** Pane glass must stay registered to the wallpaper behind it for the whole slide; today it looks like an offset picture. Confirm the cause before fixing it.
3. **Quarter-resolution cache.** Store each blurred frame at the resolution it was blurred at, about a quarter of the screen, and scale it up when drawing, instead of upscaling it to full size. A 1.5×-wide frame drops from about 15.6 MB to about 1 MB, so every radius in use fits and nothing is evicted mid-session.
   - Also resolve how the `surface_inherit_*` flags map onto radii. Pong stores 8, 16, 17 and 19 plus the unblurred 0.
   - Check that the look matches today's pixel for pixel, within the tolerance of the blur.
4. **Keyboard material.** The solid↔glass switch (solid on Home, glass on Terminal) crossfades with the slide progress instead of swapping at settle. This removes the two-step swap from the keyboard-local crop to the unified crop.
5. **Pane gap.** When the keyboard retracts, the pane no longer leaves a band of sharp wallpaper for 350–500 ms and then jumps to full height at settle.
6. **Empty terminal.** When the keyboard rises on arrival, the terminal text no longer sits behind the keyboard and jumps 300–400 ms after landing. M1's frost-on-reflow applies in the default mode too.
7. **Display blank.** After the settle resize, the Display pane no longer shows black and then grey for 150–400 ms.
8. **Frame costs.**
   - Pre-roll frames: 30–77 ms today.
   - Settle frames: 40–69 ms today. Arriving on Terminal costs two long frames back to back.
   - Target: no frame above 16.7 ms at pre-roll or settle on pong.
9. **Minor.**
   - The dock and keyboard keep rising about 300 ms after the page has landed.
   - The pill label and the stats text overlap in the page bar mid-slide.

## 3. The Fancier Glass toggle

- **Where:** Settings > Look (the Appearance page, `TermuxStylePreferencesFragment`), a section "Fancier Glass" below the Battery section that holds Lazy mode (both moved there from the Terminal page 2026-09-29; keys unchanged). Off by default.
- **Hint:** "Bends the wallpaper at glass edges. Set the wallpaper with the in-app wallpaper picker." While disabled the summary reads "Off: the wallpaper was not set with the in-app wallpaper picker."
- **Shown only on API 33+.** Below that it is hidden.
- **Needs an in-app wallpaper** (a managed picture or video). With any other wallpaper, the toggle is greyed out with the reason, and everything behaves as the default mode. A switch already on stays on (stored) but `FancierGlassPolicy.active` is false, so nothing refracts until an in-app wallpaper is back. The page re-checks on every resume (`WallpaperPictureReader.managedPictureOnScreen`: stored wallpaper id equals the system's and the exact copy is on disk).
- **What it turns on:**
  1. AGSL refraction on every glass surface (§4).
  2. Video wallpapers, both modes (§5).
  3. The refraction controls in the Appearance editor (§4).
  4. The Fancier Glass motions (§6).
- **What overrides it:** Lazy mode, battery saver and reduced motion stop every animation and video, and the glass shows the resting look. The toggle itself stays on.

### 3.1 Every glass surface (coverage as of 2026-09-29)

The look is `TermuxActivity.mFancierGlassLook` (null in the default mode). The dock, the keyboard host and the under-keyboard card follow the dock's material, so in the default mode they draw `Look.DEFAULT` rather than nothing: `GlassStack.lookFor` is the one rule, and the keyboard's capsule, the under-keyboard card and the dock's `RenderEffect` all read it. One program, `chrome/GlassRefraction.Program`, draws it. It reaches a surface by one of three routes: **(a)** a `chrome/SharedFrameDrawable` handed the look through `setRefraction`, **(b)** a `terminal/PaneGlassBackdropView` through its own `setRefraction`, **(c)** a `RenderEffect` over the view's own pixels (`buildGlassRefractionEffect`). A surface with Solid material, or with blur off, gets no frame and so no glass, as in the default mode. A software canvas (a `RealtimeBlurView` drawing the window into its bitmap) never gets the refracted frame: routes (a) and (b) and the corner tab skip it there.

| Surface | Frost | Refraction | Rim |
|---|---|---|---|
| Dock | shared frame | (c) | the capsule, or square; overscanned bottom |
| Under-pill nav strip | shared frame | (c) | square |
| Keyboard host | shared frame | (a), built by `GlassStack.build`; `Look.DEFAULT` in the default mode | the capsule, or square with a bottom seam over the strip |
| Under-keyboard card (`UnderKeyboardBand`) | shared frame | (a), built by `GlassStack.build`; `Look.DEFAULT` in the default mode | the card's own radius |
| Status band and window bar | shared frame, `WallpaperFrostPainter.updateTopPane`; live blur when there is no frame | (a) | one sheet in the docked style (the band's bottom and the bar's top are seams); the capsule's own radius in the floating style; none for a bar on the dock's plank, which has no frost of its own |
| Command palette, terminal sheet | shared frame; live blur when there is no frame | (a) | the plane's corner |
| App drawer plane | shared frame, with the live blur kept on top of it so the terminal ghosts through; tint only in wallpaper mode with no frame | (a) | the plane's corner |
| Terminal panes, Widgets page, Display page | shared frame | (b) | the slab's own radius; none under a corner mask |
| Corner tabs, every place (since 2026-09-29) | shared frame, under the tab's fixed scrim; no tint or grain of the pane's | the tab's own program, as (a) | the tab's rect turning its one free corner; the edge it slid out of and the frame side are seams (`CornerTabGeometry.refractionSeams`) |
| Off-dock plank, and the A–Z bar's capsule off the dock (since 2026-09-29) | shared frame, `WallpaperFrostPainter.applyOffDockSheet`, at the dock's radius; live blur only when there is no frame | (a) | the outline's own clamped radius on all four sides |

The dock's frame, tint, grain, rim and refraction, the keyboard capsule's and the under-keyboard card's are one stack, `chrome/GlassStack` (a plain `Spec` in, the layered drawable out), so a value that differs between them is a value in the spec and nowhere else. The rim is `GlassSurfaceFactory.rim`, written once.

Deliberately without refraction:
- **The A–Z pull tab and the letters it slides out** (`AzPullTabLayer`): tint only. They stand over live content, not over the wallpaper.
- **Popup menus** (`AnchoredMenu`): separate windows with no access to the shared frame.

Known, left as they are: the live-blur fallbacks of the palette, the sheet, the drawer and the top bars still run a `RealtimeBlurView` (whole-window software capture) whenever they show. They never refract.

## 4. Refraction

- **One shader for every glass surface.** It extends today's `GLASS_AGSL` (`TermuxActivity.java:3399-3495`) and samples the shared frame, or the live-recorded backdrop while a video plays. Per pixel it bends the frame near the rim and adds the rim light.
- **Controls:** global only, in the Appearance editor, visible only while Fancier Glass is on.
  - **Bend** (`uStrength`): how strongly the glass bends the image behind it.
  - **Edge width** (`uBand`): how far in from the rim the bending reaches.
  - **Edge light** (`uRim`): how bright the rim highlight is.
  - Defaults are to be tuned on pong. The resting look with Fancier Glass on and the default values must be close to the default mode.
- **Idle cost stays at zero.** Nothing redraws at rest, and uniforms change only while something moves.

## 5. Wallpapers (Fancier Glass only)

**Parallax** is a user toggle for both still and video wallpapers. A video that is not 1.5× wide plays with parallax off.

**Mode 1: continuous video.**
- Import an MP4 (H.264 or HEVC) through the in-app wallpaper picker. GIF and WebP are converted to MP4 on import, never decoded full-screen on the CPU.
- It plays through hardware decode into a view whose pixels the glass can read (`TextureView`, or `MediaCodec` into a `SurfaceTexture`). While it plays, the glass reads that live frame as its backdrop.
- **Frame rate:** a user choice between 30 fps (the default) and 60 fps.
- **Pauses** when the screen is off, another app is in front, Lazy mode or battery saver is on, or the device is thermally throttled. While paused, the glass reads the paused frame through the fast shared-frame path.
- **Pong budgets:** to be set from the first working build. Measure idle fps, GPU time per frame and battery drain per hour.

**Mode 2: unlock pair.**
- **A pack has five parts** (see `wallpaper-packs/proto-01/pack.json`):
  - a lock still, applied as the system lock wallpaper;
  - an unlock clip whose first frame is the lock still;
  - a home still, which is the clip's last frame and is also set as the system home wallpaper, so the system's app open/close animations reveal the same picture;
  - 1080-wide centre crops for when parallax is off;
  - `pack.json`.
- **Unlock animation:** a user choice between U1 Defrost (the default) and U2 Hold-then-move (§7). Both are prototyped on pong. Whether both stay after the prototype is decided then.
- **When it plays:** only when the launcher is the first thing shown after an unlock. Unlocking straight into another app plays nothing.
- **Idle cost is zero** after the clip.
- **Packs** download from the separate `tlwalls` repository, not bundled in the APK, through the existing download engine (the one used for models).
  - Packs are not signed. Integrity comes from HTTPS plus a SHA-256 for every file in the repo's catalogue.
  - Downloads get strict size limits (per file and per pack) and a strict `pack.json` schema; unknown fields are ignored and paths outside the pack are refused.
  - Media is decoded only by the platform decoders. A pack carries no code, shaders or HTML.
  - Each pack is validated on download: first and last frames match the stills (PSNR > 40 dB).
  - Content: original work only. proto-01 is the first pack; its README describes how it was made and its licence.

## 6. Motions (Fancier Glass unless noted)

| ID | Motion | Fancier Glass | Default mode |
|---|---|---|---|
| M1 | Keyboard open/close | The slab rises with a refracting top lip. Terminal text frosts during the reflow and lands sharp | Frost-on-reflow (bug fix 6) |
| M2 | Keyboard position change | The leading edge's refraction grows with velocity and eases back to the resting look | Plain glide |
| M3 | Status bar expand/retract (driven by the drag across the bar and, since 2026-09-29, the status swipe off the page's top border: `BorderDrag.Claim.STATUS` → `PaneWallLayout` → `TermuxActivity.dragTopStatusBar` / `setTopStatusBarCollapsed`, released by `KeyboardReveal`'s rule) | The sheet unrolls; its lower lip bends the pane below; tiles fade in only after 60% of the travel | The same, without the lip |
| M4 | Portrait ↔ landscape | The chrome frosts and dims during rotation, and the new layout thaws in. The wallpaper stays sharp | Crossfade |
| M5 | App launch from the dock | The icon's lens swells, the dock gives, and the launch uses `ActivityOptions.makeClipRevealAnimation` from the icon's bounds. Return plays in reverse | Lens (current) |
| M6 | Page slide | The glass is registered to the wallpaper at its true position. Side-edge refraction grows with slide speed | Registration fix only |
| M8 | Border drag paging (built 2026-09-28; every mode since the border drag replaced the minimal-mode edge swipe and the status bar's page swipe) | A press held on the pane's border, then dragged sideways, drags the wall. The page under the finger and the page arriving beside it both tip, each about its own vertical centre line, like planks the finger presses its weight into: each dips the edge nearer the point the finger pressed (a finger on a page's centre line dips it toward the page it shares the screen with), by the same angle for both, peaking at 18° half a width out (12° before 2026-09-28; before the later 2026-09-28 rework only the held page tipped, leading side down). The weight stays where it pressed on the held page, so the planks keep leaning through the release while the settle — a critically damped spring seeded with the finger's speed (`wall/SettleSpring`), which never passes its rest — lays them flat (`wall/PlankTilt`, `wall/BorderDrag`). Since 2026-09-28 the hold itself has weight, in every mode (`wall/PageSink`): the page sinks on a spring to 0.94 scale and 14% darker (a colour filter on the layer's paint) and stays down through the drag, then springs back up past flush by under 1% as the finger lets go; under Fancier Glass a held left or right border also pushes that side in by up to 6°, handing over to the travel's tip over the first quarter width. A hardware layer on each tipping page for the length of the gesture; no capture. The Display place moves like the others on a snapshot stand-in (since 2026-09-29; `wall/SurfacePage`, `wall/SurfaceStandIn`, `x11/X11PaneFrame`): its picture is a `SurfaceView`, which a rotation leaves flat and a layer strands, so when a motion begins with it on screen (the hold's sink, a drag, a slide carrying it on or off) its current frame is copied with `PixelCopy`, and once the copy lands a plain view holding it goes up over the surface's exact bounds and the surface goes down beneath it; from that frame the page takes the tilt, the scale and the layer's dim at whatever point the motion has reached. Until the copy lands, and whenever it fails (no surface, no data, secure content, zero size, no memory), the page keeps the old motion: scale alone, no layer, no tilt. A parked Display arriving uses the one copy kept from its last motion if it is the same size and under 30 s old, and slides in flat otherwise. With no display running the page is plain views and takes every transform as it is. At rest the surface comes back first, beneath the stand-in, which goes two frames after the surface is handed back (300 ms backstop). The picture freezes for the motion; the X session runs on underneath. One surface-sized ARGB bitmap is kept at most, dropped when the display stops, the page leaves the wall or the window, or 30 s after its last use; reduced motion takes no copy. A Display page view that is not a `SurfacePage` keeps the old motion. The window strip's overswipe and the place buttons slide the wall flat and sink nothing | Plain page change, no tilt; the sink still plays unless motion is reduced |

Moving panes (2026-09-29, both modes): while a pane frame animates, whether the FLIP move of a swap, rearrange or split or the entry pop, its glass samples the wallpaper at the frame's current on-screen position on every frame, so the frost stays registered to the wallpaper instead of riding along and snapping in on landing. The frame publishes its animated offset through `GlassAnchor.setMotion` (`terminal/PaneGlassMotion`), which the plank's press never does, and withdraws it when the animation ends or is cancelled. The backdrop is still the cached wallpaper-only frame; there is no live blur.

M7 (a sheet dropping out of the touch point) is out for now.

## 7. Unlock variants

These rules apply to both variants:
- The first frame is the lock still, or a blurred, dim version of it.
- No sharp edges move during the first 300 ms.
- Any touch jumps to the resting state within one frame.
- **Trigger:** `onWindowFocusChanged(true)` and `!KeyguardManager.isKeyguardLocked()`, after an `ACTION_USER_PRESENT` seen since the last screen-off.

The variants:
- **U1 Defrost (the default choice).** The clip plays under a heavy frost that thins to the normal glass level. The chrome fades in over 30–52% of the clip.
- **U2 Hold, then move (the other choice).** It shows the exact lock still and starts the clip one frame after the trigger. The chrome is present and still from the start.

## 8. Tests (on pong, and on the HTC for landscape)

- **Frame timing:**
  - Button and swipe transitions Home↔Terminal↔Display, with and without an app on Display, in both the default mode and Fancier Glass;
  - p50, p90, the pre-roll frame and the settle frame, compared with the 2026-09-27 numbers;
  - measured with `dumpsys gfxinfo framestats` and a perfetto trace for each case.
- **Cursor trail cases**, as your earlier request requires:
  - an in-pane jump;
  - a pane switch;
  - a pane switch under tilt or press;
  - frame time with the trail and the refraction animating together.
- **Idle fps** on every page with a still wallpaper, a paused video and a playing video. Battery drain per hour for mode 1.
- **Unlock** under PIN, fingerprint and face unlock, and unlocking into another app. Check for doubled or broken frames in a screen recording.
- **Pixel identity:** default mode after the fixes against today's build, at rest on all three pages.

## 9. Records

- A new ADR amending 0002: all surfaces sample the shared frame, the cache is stored at blur resolution, and Fancier Glass adds AGSL refraction plus a live backdrop for video.
- A backlog entry replacing the "AGSL glass" deferred row.

## 10. Decisions (2026-09-27)

1. U1 and U2 are both a user choice for now. After the prototype, the developer decides whether both stay.
2. Packs live in a separate repository named `tlwalls`. They are not signed; see §5 for what replaces signing.
3. Continuous video offers both 30 and 60 fps.
4. The refraction controls do not touch the key-press lens or the dock refraction while Fancier Glass is off. Current behaviour stays.

## 11. Window-switch card (2026-09-29)

Switching, creating or closing a window pans a snapshot of the outgoing surface (`captureTerminalDeparture`) away while the wall slides the new page in. The snapshot was drawn on a software canvas, which cannot run the refraction program, so its ground was the shared blur frame painted across the whole terminal rectangle, on an opaque plate with a shadow. The result was a hard-edged block of the wrong backdrop around the panes' rounded frames, riding along with the pan (evidence: `evidence-2026-09-29/`).

Rule: a copy of the glass is clipped to the slabs' own rounded outlines (`PaneGlass.slabOutline`, cut at the radius `PaneGlass.apply` uses), never to a window or page rectangle. The card's gaps and corners stay see-through, so the live wallpaper shows there, and the card carries no plate. The live panes are unaffected: their slabs already re-aim per frame of the page slide (`invalidatePaneGlassPositions`) and draw inside their own rounded frames. The card's slabs show the plain blur rather than the refracted frame while they move; drawing the snapshot from a hardware `RenderNode` (as the split reveal does) would restore it.

### 11.1 Pane changes: the final rule and every path (2026-09-29)

Rule: nothing that moves or freezes during a pane change carries a rectangle. A copy of a pane is the slab and only the slab: cut at the slab's own radius (`PaneShape.radiusForBounds`), with no ground, plate or shadow. Where the panes can simply move, they move as live frames, whose glass re-aims every frame through `GlassAnchor` (`PaneGlassMotion`), and no copy is made. A copy that translates carries no glass of its own (the departure card's gaps show the live wallpaper); a copy that is only clipped in place (the split reveal) keeps the aim it was recorded at. There is no second aim mechanism.

A frozen pane goes through `terminal/PaneSnapshot`: on API 29+ with a hardware window a recorded `RenderNode` (draw ops, no pixels, the refraction program survives) with its outline set to the slab's rounded rect and clip-to-outline on, since a recording, unlike the live frame, is not cut to its own outline by the renderer; otherwise a bitmap, drawn under a clip to the same rounded rect. `PaneSnapshot.route(sdk, hardware)` makes the choice.

| Path | Pane change | Copy? | Rule |
|---|---|---|---|
| FLIP move (`animateMoveFromOrigins`) | swap, move, rearrange, maximise, restore, close (siblings growing) | none, live frames | live glass re-aimed per frame |
| Entry pop (`animatePaneEntry`) | new pane with no reveal | none, live frame | live glass re-aimed per frame |
| Split reveal (`captureSplitRevealSnapshot`, `SplitRevealDrawable`) | split | the old pane | `PaneSnapshot`, cut to the rounded slab (was square-cornered on both routes) |
| Close ghost (`ghostRemovedPane`, `PaneMotionOverlayView.Ghost`) | close | no bitmap: a tinted rounded outline | radius now capped as the slab's is |
| Departure card (`captureTerminalDeparture`) | window switch, new window, close window | bitmap, half resolution | stays a bitmap: the pane tree is torn down right after the capture, and a recording only references the live child display lists. Clipped to `PaneGlass.slabOutline`, no plate (§11) |
| Display stand-in (`wall/SurfaceStandIn`) | page motion only, not a pane change | the display's picture | the picture itself is rectangular; not glass |

Default mode had the same square corners on the split reveal (its slabs are glass too); with the pane glass off there is no rectangle to cut.
