# AGSL wallpapers: in-launcher scenes versus a system WallpaperService (2026-11-05)

Question from the coordinator: how should termux-launcher draw custom animated wallpapers with AGSL,
and which open questions from [`agsl-capabilities-research-2026-11-05.md`](agsl-capabilities-research-2026-11-05.md)
matter for it? Read-only research, no code changed.

Method. Platform claims cite the page or AOSP source that owns them, fetched 2026-11-05. Repo claims
cite `path:line` at the working tree (`v2-redesign`, `8fd12feca`). Where a fetched page was
summarised by the fetch tool, the quoted text is the tool's quote. Anything I could not confirm is
marked **unconfirmed**; my own reasoning from a source is marked **[inference]**. Product context is
in [`agsl-effects-and-touch-latency-2026-10-22.md`](../../active/agsl-effects-and-touch-latency-2026-10-22.md)
(Open questions, "Wallpaper scenes"),
[`live-glass-2026-11-03.md`](../../active/live-glass-2026-11-03.md) and
[`wallpaper-scenes-research-2026-10-22.md`](wallpaper-scenes-research-2026-10-22.md); not repeated here.

## Answer in short

1. The repo already contains the architecture the platform docs favour: a full-window `SurfaceView`
   behind the window, drawn by its own `HardwareRenderer` on a scene thread, plus a 1/8-scale blurred
   second pass for glass. It is a prototype behind `debug.tl.scene`. The research mostly confirms and
   hardens that design; it does not point to a different one.
2. A system `WallpaperService` can draw AGSL (its `SurfaceHolder.lockHardwareCanvas()` is API 26, and
   the Engine overrides it), but it would replace the user's photo on Home and Lock, cannot see the
   launcher's panes except through `sendWallpaperCommand`, and is what was deleted on 2026-10-06. Keep it
   out unless "scene on the lock screen" becomes a requirement.
3. Per frame, an in-window scene (Paint shader or RenderEffect) repaints the window; a `SurfaceView`
   does not. The repo measured both: one vsync per frame in-window, +1.5 ms p50 to buffer-ready on its
   own surface. `TextureView` goes through the window's GPU path and is a worse fit.
4. A View `RenderEffect` always becomes an offscreen layer: HWUI's `promotedToLayer()` tests
   `mImageFilter != nullptr`. Closed from source (question 6 below).
5. A `RenderNode`/`RenderEffect` inside the window cannot read a `SurfaceView`'s pixels (the surface is a
   separate layer behind a hole punched in the window), so glass needs the explicit low-res second pass
   the repo built. It cannot be removed.
6. `setInputBuffer` keeps filtering and tiling per the class javadoc and the Java source passes the
   bitmap shader's "direct sampling" native instance; the quick-reference sentence saying it avoids
   filtering is the outlier. Bicubic is not available for raw shaders in Skia.
7. Static fallback is already in place (`WallpaperBackdropView`). A still for the lock screen is a
   rendered `Bitmap` through `WallpaperManager.setBitmap(..., FLAG_LOCK)`, which `ManagedWallpaper`
   already does for photos. minSdk is 26, so API 26–32 devices get the static wallpaper only.
8. The biggest unknowns are device behaviours, not API behaviours: whether the scene surface sits above
   the ROM's wallpaper layer once `FLAG_SHOW_WALLPAPER` is cleared, and thermal behaviour over a long
   session. Both need pong runs.

## Repo today

How the home wallpaper is drawn.

- **Window theme.** The wallpaper theme makes the window transparent and asks Android to composite the
  system wallpaper behind it: `app/src/main/res/values-v31/themes.xml:8-14` (`windowBackground`
  transparent, `windowShowWallpaper` true); same flag in `values/themes.xml:75-77` and the night
  variants (`values-night/themes.xml:43-45`, `values-night-v31/themes.xml:44-46`).
- **Why the launcher self-draws.** `chrome/WallpaperBackdropPolicy.java:8-20`: the ROM composites at a
  zoom the app cannot read (1.03 and 1.084 measured on Nothing OS), ignores `setWallpaperZoomOut`, so
  glass crops cut from the app's own capture only line up if the app also draws that capture.
  `SELF_DRAWN` is chosen when the picture is a readable still (`WallpaperBackdropPolicy.mode`, :43-60).
- **The flag follows the mode.** `TermuxActivity.java:4580-4588`: `syncSystemWallpaperBehindWindow`
  clears `FLAG_SHOW_WALLPAPER` while the launcher draws an app-only picture, and adds it back otherwise.
  So dropping the system wallpaper from behind the window is already a supported state.
- **Self-drawn backdrop.** `chrome/WallpaperBackdropView.java:1-40` (class doc) draws the radius-0
  frame from `WallpaperBlurCache` at the captured rect, then the dim; parallax is a translate
  (`WallpaperParallax`). Found at `TermuxActivity.java:1998-1999` (`R.id.wallpaper_backdrop`).
- **System wallpaper offsets.** `TermuxActivity.java:4861-4862` calls `setWallpaperOffsetSteps(1,1)` and
  `setWallpaperOffsets(token, 0.5, 0.5)` to centre the ROM's copy; comment at :4863-4866 explains the
  zoom-out reasoning.
- **Setting and reading system slots.** `chrome/ManagedWallpaper.java:236-281` (`setBitmap(region, null,
  true, flags)` and `setStream(...)` with `FLAG_SYSTEM` / `FLAG_LOCK`, constants at :39-41);
  `chrome/WallpaperPictureReader.java:163` and `chrome/wallpaper/WallpaperSlots.java:218-219` read with
  `getWallpaperFile`. Slot rules: `chrome/wallpaper/WallpaperSlotPlan.java:99-120`.
- **Glass.** Dock, nav strip, widgets, panes and bars sample the stored frame (live-glass doc, "Today"
  table); in scene mode they sample `LiveSceneFrame` instead (`chrome/scene/LiveSceneFrame.java:17-36`),
  and fall back to the still frame when `current()` is null.

The scene prototype (already built, debug only).

- **Gate.** `chrome/scene/SceneFramePolicy.java:18-21`: `debug.tl.scene` (`off`/`contour`/`filament`),
  `debug.tl.scene_rate` (ambient 0/30/90, default 30), `debug.tl.scene_glass` (`lowres`/`still`),
  `debug.tl.scene_glass_rate` (8 Hz). With the property unset no view exists
  (`chrome/scene/SceneController.java:15-18`, `:42-51`).
- **Layer.** One `SurfaceView` at default Z order inserted into `terminal_root_container` above the
  backdrop (`SceneController.java:25-31`; wiring at `TermuxActivity.java:2000-2017` (constructor at :2002)). Default Z on
  purpose: "the surface sits below the window, which the display composites over it"
  (`chrome/scene/SceneLayerView.java:134-136`).
- **Renderer.** A dedicated `HardwareRenderer` with one content-root `RenderNode`
  (`SceneLayerView.java:504-512`); a frame sets uniforms, re-records the node with one
  `drawRect(paint)` and calls `syncAndDraw` (:470-494). All of it runs on a `HandlerThread` with its own
  `Choreographer` (class doc :31-47); the main thread only keeps gates and polls pane rects every 250 ms
  (`BASIN_REFRESH_MS`, :66, :295).
- **Frame policy.** Choreographer callback runs only while the layer could be seen; reactions run at full
  vsync until decayed, ambient frames are skipped down to the ambient rate; nothing is posted while
  hidden, screen off, Lazy mode, or ambient 0 and idle; Remove animations freezes motion
  (class doc :41-47; `SceneFramePolicy.java` ambient gate).
- **Reactions.** `chrome/scene/SceneEvents.java:22-25` caps hills 3, sparks 4, pulses 2, basins 4; they
  reach the program as `float4` uniform arrays (`chrome/scene/ContourProgram.java:33-45`: `uHill[3]`,
  `uBasin[4]`, `uSpark[4]`, `uPulseRect[2]`, `uPulseT[2]`, plus colours). Reactions cross threads in a
  lock-free ring (`SceneEventRing`).
- **Glass pass.** `chrome/scene/SceneLowRes.java:32-56`: the scene drawn again at 1/8 per axis
  (`DIVISOR = 8`) into an `ImageReader` surface by a second `HardwareRenderer`, `RenderEffect` blur on
  the node, result published as `LiveSceneFrame`; images are kept open by a ledger
  (`SceneImageLedger`) so the producer cannot recycle a buffer glass still samples.
- **Measured** (`project-docs/active/input-latency-study.md:858-881`, pong, 2026-10-23): Contour on its
  own surface adds about +1.5 ms p50 (p90 +3 to +5) to buffer-ready, no Buffer Stuffing, versus +10 to
  15 ms and 97% stuffing in the window; main-thread cost about 1 ms per scene frame because
  `syncAndDraw` blocks for the sync; the low-res pass at 15 fps adds about 1.1 ms per pass.
- **Not built.** No static still export, no parallax in scene mode ("the scene does not pan",
  `LiveSceneFrame.java` doc), no pre-API-33 path (the prototype is a debug property), no
  `Surface.setFrameRate` call (grep over `chrome/scene/` finds none).

Build constants. `gradle.properties:29-32`: `minSdkVersion=26`, `targetSdkVersion=28`,
`compileSdkVersion=36`. AGSL's floor is API 33, so scenes are an opt-in layer on API 33+, as
`PaneRetroEffect.available()` already is.

## 1. In-launcher scene versus a system WallpaperService

What a `WallpaperService` Engine gives AGSL.

- **Hardware canvas.** `SurfaceHolder.lockHardwareCanvas()` is "just like `lockCanvas()` but the returned
  canvas is hardware-accelerated", added in API 26
  ([SurfaceHolder](https://developer.android.com/reference/android/view/SurfaceHolder)). In
  `WallpaperService.java` the Engine's holder overrides it: `lockHardwareCanvas()` calls
  `prepareToDraw()` then `super.lockHardwareCanvas()` (`prepareToDraw()` only pokes the draw lock in
  `STATE_DOZE`) ([AOSP WallpaperService.java](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/service/wallpaper/WallpaperService.java)).
  The buffer is not preserved: "required to fully cover the surface every time", no partial updates
  ([Surface.lockHardwareCanvas](https://developer.android.com/reference/android/view/Surface)).
- **AGSL on that canvas** is **[inference]**, not a documented statement: a hardware canvas is the same
  Skia-backed Canvas pipeline a View uses, and AGSL is documented as a stage of "the Canvas or
  RenderNode drawing pipeline"
  ([RuntimeShader](https://developer.android.com/reference/android/graphics/RuntimeShader)). No page says
  `RuntimeShader` works on a wallpaper Engine's canvas: **unconfirmed** until a test engine draws one.
  The AGSL level (33) is the floor either way.
- **Engine hooks.** `onVisibilityChanged` ("It is very important that a wallpaper only use CPU while it
  is visible"), `onOffsetsChanged`, `onZoomChanged`, `onCommand(action, x, y, z, extras, resultRequested)`,
  `onComputeColors()` (default null), `notifyColorsChanged()` rate-limited to once per 1000 ms
  (`NOTIFY_COLORS_RATE_LIMIT_MS`), `supportsLocalColorExtraction()` default false, `isPreview()` (AOSP
  source above). Touches reach a wallpaper only through windows with `FLAG_SHOW_WALLPAPER`: "all touch
  events sent to this window is also sent to the wallpaper", switchable with
  `setWallpaperTouchEventsEnabled` ([WindowManager.LayoutParams](https://developer.android.com/reference/android/view/WindowManager.LayoutParams)).
- **Cross-process.** The Engine lives in the service's process; the launcher can only talk to it with
  `WallpaperManager.sendWallpaperCommand(windowToken, action, x, y, z, extras)` (API 7,
  [WallpaperManager](https://developer.android.com/reference/android/app/WallpaperManager)). Pane rects,
  scroll position and glass state would all be marshalled as commands. In-process, the repo's event ring
  does this with no binder. **[inference]** that command throughput is adequate for per-frame rects; not
  tested.
- **Colours.** `getWallpaperColors` "can return null when ... the user has chosen to use a live
  wallpaper: live wallpapers might not implement `onComputeColors()`"
  ([WallpaperManager](https://developer.android.com/reference/android/app/WallpaperManager),
  [WallpaperColors](https://developer.android.com/reference/android/app/WallpaperColors): API 27, hints
  API 31). A service wallpaper has to produce its own `WallpaperColors` or Material You falls back.
- **Reach.** The user must choose it as their live wallpaper; it then replaces the photo on Home (and
  Lock if "Same as Home"), which collides with "static photo wallpapers (Home/Lock, Same as Home) must
  keep working", and with the 2026-10-06 removal of `LockLiveWallpaperService`. Its one real advantage is
  the lock screen and being visible behind other apps.

What in-launcher drawing needs, and what it costs.

- **Window.** The scene lives behind a translucent window. `FLAG_SHOW_WALLPAPER` "ensures that the
  wallpaper surface will be there if this window actually has translucent regions"
  ([WindowManager.LayoutParams](https://developer.android.com/reference/android/view/WindowManager.LayoutParams)),
  so the window must be translucent to see anything behind it. The repo window is, via its theme.
  Clearing the flag while a self-drawn picture is up is already done (`TermuxActivity.java:4580-4588`),
  so the system wallpaper is not composited when the scene is on. Whether the scene's `SurfaceView` layer
  is ordered above the ROM's wallpaper layer in every case (rather than showing it through) is
  **unconfirmed**: the `SurfaceView` doc says only that the surface "is Z ordered so that it is behind the
  window holding its SurfaceView" and the view "punches a hole in its window"
  ([SurfaceView](https://developer.android.com/reference/android/view/SurfaceView)). The repo's pong
  runs show the scene visible with the flag cleared; behaviour with the flag set was not checked here.
- **Lock screen and other apps.** They keep whatever is stored as the system wallpaper; an in-launcher
  scene is invisible there by construction. **[inference]** from the in-process design and the docs on
  `WallpaperManager` slots.
- **Colour extraction.** Android derives Material You colours from the system wallpaper, not from the
  launcher's pixels. A scene changes nothing there, so the theme keeps following the photo (the repo
  does not call `getWallpaperColors` anywhere in `app/src/main/java`, by grep). If scene hue should drive
  chrome colours the app has to compute its own palette (`WallpaperColors.fromBitmap`, API 27, takes any
  bitmap). **[inference]**
- **Reading the photo.** `getWallpaperFile` and `getDrawable` are restricted: from Android 14 they
  "will always throw a `SecurityException`" without `MANAGE_EXTERNAL_STORAGE`; on 13 the default
  wallpaper may be returned
  ([WallpaperManager](https://developer.android.com/reference/android/app/WallpaperManager)). The
  launcher already tracks a refused read (`mWallpaperReadPermissionDenied`,
  `TermuxActivity.java:1646, 4570`). A procedural scene needs no photo, so it has no such dependency.
- **Parallax and zoom.** `setWallpaperOffsets` positions the system wallpaper only
  ("when that wallpaper is visible behind the given window"); with the system layer hidden the scene
  needs its own offset uniform. The repo already owns the shared offset (`WallpaperParallax`).
  `setWallpaperZoomOut` is ignored on the tested ROM (`WallpaperBackdropPolicy.java:11-12`).
- **Recents and app-launch animations.** The window is translucent (`values/themes.xml:25`) with a
  transparent background; what the system shows in recents and during launch with the scene on is
  **unconfirmed** (not covered by a primary page I read). Test on pong.

## 2. Drawing a full-screen animated scene behind other views

| Approach | What the sources say | Verdict |
|---|---|---|
| View with Paint shader (`onDraw`) | Works anywhere a Paint does ([Using AGSL](https://developer.android.com/develop/ui/views/graphics/agsl/using-agsl)); every frame invalidates the window's display list. Repo: "an in-window scene cost a vsync per frame" (`agsl-effects-and-touch-latency-2026-10-22.md:106-108`). | Fine for bounded overlays, wrong for a full-screen scene. |
| `RenderEffect` on a View | "more expensive than drawing a custom View" (Using AGSL); HWUI makes it a layer (section 6). A scene generates pixels, it has no input to filter. | Not for the scene; fine for glass. |
| `SurfaceView` + own renderer | Separate surface behind the window, composited by SurfaceFlinger, "a full alpha-blended composite will be performed each time the Surface changes" ([SurfaceView](https://developer.android.com/reference/android/view/SurfaceView)); position updates are synchronous with View rendering since N, so translating and scaling cause no artifacts (same page). | The repo's choice; measured cheap. |
| `TextureView` | Hardware-accelerated window only; "When rendered in software, TextureView will draw nothing" ([TextureView](https://developer.android.com/reference/android/view/TextureView)); frames go through the window's GPU draw. | Not worth it here: the point of the surface is to keep scene frames out of the window's draw. |

Frame pacing and rate.

- Each thread can own a `Choreographer`; the repo's scene thread runs its own, and skips ambient frames
  against a period (`SceneFramePolicy`). `Surface.setFrameRate` (API 30; 31 adds the strategy argument;
  `FRAME_RATE_COMPATIBILITY_AT_LEAST` is API 36, meant for "UIs, animations, scrolling and fling, and
  anything that is not a game or video") only hints the display rate: it "won't introduce frame rate
  throttling", and it can change Choreographer timings
  ([Surface](https://developer.android.com/reference/android/view/Surface)).
  The app still has to skip frames itself, which the repo does.
- The frame-rate guide: no guarantee of the requested rate; battery saver can restrict the display's
  refresh rate; keep the value you pass stable and call once, not per frame, because a call "is likely to
  result in a change to the display refresh rate, which may result in a frame drop"
  ([Frame rate](https://developer.android.com/media/optimize/performance/frame-rate)).
  Whether a 30 Hz hint on the scene surface lowers the display rate while the window next to it needs 90
  Hz is **unconfirmed** (the page says the platform picks among surfaces, "even ... a multiple"); test.
- Adaptive refresh rate (Android 15+, `Display.hasArrSupport`, `getSuggestedFrameRate`, API 36) is
  described in the Android 16 notes
  ([Android 16 features](https://developer.android.com/about/versions/16/features)); how the SurfaceFlinger
  scheduler weighs a low-rate scene surface under it is **unconfirmed**.

Pausing and power.

- The Engine rule applies in spirit to any scene: "a wallpaper only use CPU while it is visible"
  (WallpaperService source). The repo already gates on layer visible, screen on, Lazy mode and animations
  (class doc). `SurfaceView` surfaces are created and destroyed with window visibility ("created ... while
  the SurfaceView's window is visible"), so the renderer must stop touching a surface after
  `surfaceDestroyed` (SurfaceView doc; the repo waits, bounded, for the scene thread to let go,
  `SceneLayerView.java` class doc).
- Battery saver, thermal status and Doze: the platform APIs (`PowerManager.isPowerSaveMode`,
  thermal status) were not fetched for this note: **unconfirmed** as to exact signals; the live-glass doc's
  `LiveBlurPolicy` already uses a battery-saver term. A covered launcher (another app in front) stops its
  window visibility, which stops the surface; whether `onWindowVisibilityChanged` fires reliably for
  split-screen or a translucent app above the launcher is **unconfirmed**.
- A continuous scene costs GPU power at any rate: the repo's notes already record that (kitty note,
  decision 5 in the 10-22 spec). No primary source quantifies it; the pong thermal run is the only
  evidence that counts.

## 3. Glass sampling the animated scene

- **A `RenderNode` cannot be an AGSL child**, so a live surface takes the effect route
  (live-glass doc, "Two routes, one picture seam"). For the scene this is moot: the `SurfaceView` content
  is not part of the window's RenderNode tree at all (separate surface behind a hole), so no
  `RenderEffect` or `LiveBackdrop.recordBelow` recording in the window can contain it **[inference]**
  from the `SurfaceView` doc. The scene must therefore be rendered a second time into something the
  window can sample. That is exactly `SceneLowRes`.
- **Low-res pass.** 1/8 per axis into an `ImageReader` surface by a `HardwareRenderer`, blur as
  `RenderEffect` on the node (cheap at that size), published as a bitmap and one `BitmapShader` that glass
  draws on the paint route (`SceneLowRes.java:32-56`, `LiveSceneFrame.java:17-36`). The costs measured
  were +1.1 ms per pass at 15 fps on the scene thread, not on the main thread
  (`input-latency-study.md:881`).
- **Window repaint.** A published frame invalidates only the glass surfaces that drew the last one, at
  the glass rate (default 8 Hz), "never at the scene's own rate" (`LiveSceneFrame.java` class doc). So the
  scene's cost to the window is the glass rate times the glass surfaces on screen, plus one GPU sample of
  an 8x-bilinear-upscaled bitmap per glass draw.
- **Hardware bitmap versus software copy.** `Bitmap.wrapHardwareBuffer` gives a no-copy bitmap; the
  repo records that "a hardware bitmap on the window's glass stalled the UI thread every frame", so glass
  samples a software copy (`agsl-effects-and-touch-latency-2026-10-22.md`, "Scenes hold still" paragraph;
  `chrome/scene/SceneSoftPool.java`). I did not find a primary-source explanation of the stall:
  **unconfirmed** cause.
- **`setInputBuffer`.** The natural reason to use it is a low-res bitmap holding scene *data* (height,
  flow) rather than colour. `RuntimeShader.setInputBuffer` passes
  `shader.getNativeInstanceWithDirectSampling()` and the class javadoc says such shaders "work much like a
  regular `BitmapShader` (including filtering and tiling)" with no colour-space transform and no
  premultiply ([AOSP RuntimeShader.java](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/graphics/java/android/graphics/RuntimeShader.java)).
  Glass today reads a colour bitmap through a plain `BitmapShader` on the paint route, so it does not need
  it.
- **Per-frame cost summary** (all repo-measured, none generic): scene +1.5 ms to buffer-ready; low-res
  1.1 ms per pass at its own rate; glass repaint at the glass rate. The 1.5 s typing freeze
  (10-22 doc) removes all of it while typing.

## 4. Reacting to surfaces

- **Touch, scroll, page offset, open-window rects** all reach the shader as uniforms. The repo hands
  them over lock-free (`SceneEventRing`), keeps events on the scene thread (`SceneEvents`), and polls pane
  rects on the main thread because "they can only be read on the main thread" (class doc, :66, :295).
  Uniform setters are the documented path and may be called per frame
  ([RuntimeShader](https://developer.android.com/reference/android/graphics/RuntimeShader)); the repo sets
  reaction uniforms only while they animate and once more when the last decays
  (`SceneLayerView.java:416-421`).
- **Arrays.** AGSL arrays are 1-D with a fixed size, indexed only by a constant or a loop variable
  ([quick reference](https://developer.android.com/develop/ui/views/graphics/agsl/agsl-quick-reference)),
  so a variable number of rects means a fixed-size array and a constant-bound `for` over it with a count
  uniform. `setFloatUniform(name, float[])` takes the length of the declared array. The Java class
  enforces no size limit (AOSP source above); no limit on total uniform bytes is documented
  (`uniformSize()` exists in Skia's `SkRuntimeEffect.h`): **unconfirmed**. In practice the Contour
  program already carries on the order of 15 `float4`-class slots (`ContourProgram.java:33-45`) and runs
  on pong; the first device where a bigger set fails will show as a shader-compile error (an
  `IllegalArgumentException` at construction or a blank surface; **unconfirmed** which).
- **Cost of reactions** is bounded by loop counts, not by how many windows exist: cap at N rects and
  unroll. The repo's caps (3 hills, 4 sparks, 2 pulses, 4 basins) follow the same rule.
- **Multiple scene children.** Extra data (a pre-rendered noise tile, a scrolling trail history) comes
  in as another `uniform shader` set with `setInputShader`/`setInputBuffer`
  ([RuntimeShader](https://developer.android.com/reference/android/graphics/RuntimeShader)). There is no
  previous-frame read; a scene that needs one ping-pongs two bitmaps on the scene thread (**[inference]**,
  the platform docs are silent).

## 5. Static fallback, lock screen, pre-33 devices, exporting a still

- **minSdk 26** (`gradle.properties:29`). `RuntimeShader` is API 33
  ([RuntimeShader](https://developer.android.com/reference/android/graphics/RuntimeShader)), so on API
  26–32 there is no AGSL scene: the self-drawn static wallpaper (`WallpaperBackdropView`) or the ROM's own
  stays. The scene is therefore a layer added on top of the static path, never a replacement, which is also
  what keeps Home/Lock/Same-as-Home photo behaviour intact.
- **Fallback while the scene is off or starting.** `LiveSceneFrame.current()` returning null puts the
  still frame back for glass (class doc); the scene view being absent leaves `WallpaperBackdropView`
  drawing. A scene that is created lazily needs a first-frame placeholder so the surface is not blank
  (`mSurfaceBlank`, `SceneLayerView.java:331, 392, 416`); the repo already tracks that.
- **Lock screen.** In-launcher scenes cannot appear there. The options are the static photo the user
  chose, or an exported still (below), or a live-wallpaper service (section 1).
- **Export a still.**
  - Render one frame to a `Bitmap` at screen size: the repo already renders offscreen with a
    `HardwareRenderer` into an `ImageReader` (`SceneLowRes.java`), at full scale that gives a hardware
    buffer to wrap or copy. Drawing a `RuntimeShader` Paint onto a software `Canvas(Bitmap)` is the simpler
    route but is **unconfirmed** (see section 6).
  - Then `WallpaperManager.setBitmap(fullImage, visibleCropHint, allowBackup, which)` (API 24; needs
    `SET_WALLPAPER`; "converted to a PNG and stored", broadcasts `ACTION_WALLPAPER_CHANGED`)
    ([WallpaperManager](https://developer.android.com/reference/android/app/WallpaperManager)). The repo's
    `ManagedWallpaper.java:251` already makes that exact call with `FLAG_SYSTEM`/`FLAG_LOCK` and tracks the
    ids it wrote (`ManagedWallpaper.java:374-383`), so an exported scene still fits the "managed" slots; the
    slot plan would need a `SET_SCENE_STILL` kind beside `SET_PHOTO`
    (`WallpaperSlotPlan.java:99-120`). **[inference]**
  - Side effects: the system recolours Material You from the new bitmap, and the launcher's own capture
    of the system wallpaper changes with it (the glass still frame), which is wanted but must not race the
    live scene frame.

## 6. Open questions from the previous note, closed where possible

1. **HWUI offscreen layer for RenderEffect: closed.** `RenderProperties.h` defines
   `promotedToLayer()` as `mLayerProperties.mType == LayerType::None && fitsOnLayer() &&
   (mNeedLayerForFunctors || mLayerProperties.mImageFilter != nullptr ||
   getStretchEffect().requiresLayer() || (alpha ...))`; `fitsOnLayer()` requires width and height
   within `DeviceInfo::maxTextureSize()` and non-zero
   ([RenderProperties.h](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/libs/hwui/RenderProperties.h)).
   In `RenderNodeDrawable::drawContent` the filter is read only in the layer branch and applied to a
   snapshot of the layer surface with `SkImages::MakeWithFilter`; a node with an image filter that fits
   is therefore always a layer. The layer surface is created on size-quantised boundaries, so it can be
   larger than the node (a source comment; the allocation is not in that file)
   ([RenderNodeDrawable.cpp](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/libs/hwui/pipeline/skia/RenderNodeDrawable.cpp)).
   A "render effect cache" path (`updateSnapshotIfRequired`) exists behind a flag; whether it avoids
   re-filtering unchanged content and what the flag is by default is **unconfirmed**.
2. **Software canvas: still unconfirmed.** No Android page says `RuntimeShader` works or fails on a
   `Bitmap`-backed `Canvas`. Skia's header warns that the "software renderer and pipeline-stage effects
   remain largely ES3-unaware" ([SkRuntimeEffect.h](https://raw.githubusercontent.com/google/skia/main/include/effects/SkRuntimeEffect.h)),
   which implies a raster path exists for ES2-conformant effects. A 10-line test (draw a gradient
   `RuntimeShader` onto `Bitmap.createBitmap` via `Canvas(bitmap)`) settles it.
3. **SurfaceView / TextureView / WallpaperService: partly closed.** SurfaceView is a separate layer
   behind a hole and "a full alpha-blended composite will be performed each time the Surface changes"
   (SurfaceView doc). TextureView is hardware-window-only. A wallpaper Engine gets a hardware canvas
   (API 26, source above); AGSL on it is deduced, not documented. `RemoteViews`: no primary source:
   still **unconfirmed**.
4. **`setInputBuffer` filtering: mostly closed.** Source and class javadoc say filtering and tiling
   are kept and only colour-space and premultiplication are skipped; the quick reference's "will avoid
   color space transformations and filtering" is the outlier, and Skia says bicubic is unsupported for
   raw shaders ([SkSL docs](https://skia.org/docs/user/sksl/)). I could not read the JNI file
   (`android_graphics_RuntimeShader.cpp` returned 404 at the paths tried), so the native handling is not
   read. A bitmap test with nearest versus linear `BitmapShader` settles it.

## Recommended architecture

Keep and finish what exists; add nothing from the system wallpaper side.

1. **Scene on its own `SurfaceView` behind the window** (today's `SceneLayerView`), drawn by a
   `HardwareRenderer` on a scene thread, opt-in, API 33+. Rationale: measured cheap, no window repaint,
   confirmed by docs as a separate composited surface.
2. **Static path untouched.** `WallpaperBackdropView` and the managed Home/Lock slots stay the only
   system-facing wallpaper code. A scene is off for anyone who has not enabled it, on API 26–32, with
   Lazy mode, with the screen off, covered, or under Remove animations.
3. **Glass reads the 1/8 low-res pass** (`LiveSceneFrame`), published at a low fixed rate, falling back
   to the still frame. Do not try to feed glass from the window's own RenderNode tree; it cannot see the
   surface.
4. **Reactions as bounded uniform arrays**, one ring buffer across threads, pane rects polled at 4 Hz,
   N fixed per scene.
5. **A one-shot "use this scene's still as wallpaper"** action renders a frame offscreen and calls
   `setBitmap(..., flags)` through `ManagedWallpaper`; this is the only lock-screen story and it is
   static.
6. **No `WallpaperService`.** Trade-off: no live scene on the lock screen or behind other apps, in
   exchange for no replacement of the user's photo, no cross-process command channel and no `onComputeColors`
   to maintain. Revisit only if the developer wants the lock screen animated.

Trade-offs in one place.

| Choice | Gains | Costs / risks |
|---|---|---|
| SurfaceView scene (today) | no window repaint; own thread; measured +1.5 ms | extra composited layer; glass needs a second pass; ordering versus the ROM's wallpaper layer unconfirmed |
| In-window Paint shader | simplest; glass can record it | window repaints every scene frame (measured one vsync per frame) |
| System WallpaperService | lock screen and behind apps; own process | replaces the photo; command-channel for reactions; colours and touch plumbing; was deleted for these costs |
| Low-res glass pass | cheap glass over a live scene | extra renderer, ledger and soft copies; stall with hardware bitmaps unexplained |
| Exported still | lock screen gets the look; no service | static; recolours the system theme; one more `ManagedWallpaper` slot kind |

## Open questions, ranked by how much they block the work

1. **Does the scene surface sit above the ROM's wallpaper layer in every state, and what do recents,
   app launch, rotation and split-screen show with `FLAG_SHOW_WALLPAPER` cleared?** Blocks shipping the
   scene beyond debug. Settle with a pong checklist (device-checker batch): scene on, flag set versus
   cleared, open and close an app, recents, rotate, split-screen; plus `dumpsys SurfaceFlinger` layer
   order. No primary page covers it.
2. **Thermal and battery over a long session** at the default 30 Hz ambient with glass at 8 Hz. Blocks
   making it default-on, and picking the ambient rate. Settle with a 20-minute pong run, thermal status
   and `dumpsys batterystats`/GPU time (the repo's existing Perfetto method), at 30 versus 15 versus 0.
3. **Hardware-bitmap stall for glass.** Blocks dropping the software copy (`SceneSoftPool`), which costs
   memory and a copy. Settle with a Perfetto trace of the UI thread with a hardware bitmap versus the soft
   copy; look for the sync wait on the buffer (source read of HWUI texture upload is the alternative).
4. **Does a 30 Hz `Surface.setFrameRate` on the scene surface lower or hold the display rate beside a
   90 Hz window?** Blocks the cheapest power lever. Settle with `setFrameRate(30, AT_LEAST/DEFAULT)` on
   pong and `dumpsys display` refresh rate during a still scene, with and without typing.
5. **Total uniform size / array length limit and the failure mode.** Blocks richer reaction sets
   (more windows, history). Settle with a test shader declaring 64 then 256 `float4` and reading the
   error; SkSL/Skia source for `uniformSize()` limits is the alternative.
6. **Software `Canvas` runs AGSL?** Blocks the simplest still-export route. Settle with the 10-line bitmap
   test above; otherwise export through the `HardwareRenderer`/`ImageReader` path the repo already has.
7. **Does the `RenderEffect` render-effect cache avoid re-filtering static content?** Affects glass
   effect cost at rest, not the scene. Settle by reading `RenderNode::updateSnapshotIfRequired` and its
   flag default in HWUI.
8. **`WallpaperService` Engine plus `RuntimeShader`.** Only matters if a lock-screen live scene is ever
   wanted. Settle with a throwaway Engine that draws a runtime-shader rect on `lockHardwareCanvas`.
9. **Battery-saver, thermal and Doze signals, split-screen window-visibility behaviour.** Small: the
   existing gates (screen on, visible, Lazy mode) cover most of it. Settle by reading `PowerManager` /
   `PowerManager.OnThermalStatusChangedListener` docs and a pong split-screen test.
10. **`setInputBuffer` native filtering and `RemoteViews` support.** Low impact now: glass does not use
    `setInputBuffer`, and widgets need no AGSL. Settle with a bitmap test and a RemoteViews probe.
