# A dedicated GPU text renderer for the terminal on Android (2026-11-09)

Question: what would a dedicated GPU text renderer for a terminal pane look like on Android, for
Termux Launcher, where panes are live terminals drawn over the wallpaper inside rounded,
translucent glass? Read-only research. The developer asked for no verdict, so none is given: this
note lays out how HWUI draws text today, how dedicated terminal renderers work, what Android lets a
pane do, and what it would cost and leave unknown. Nothing was built or run on a device and no
code was changed. Items marked (reasoned) are inferences, not measurements or quotes. Anything the
sources did not settle is under "Open questions".

Pins (all read 2026-11-09, shallow clones or fetches, nothing built):

| Source | Pin |
|---|---|
| Ghostty (clone in the session scratchpad) | tag `vt-1.0.0`, `0fa3d577a5614150ff8e2c152de5fb9d5c6d2dec` |
| Alacritty | `aa5d942f46c40184a3f214830ff5f7989f4733a1` (HEAD of 2026-11-06; no tag fetched) |
| kitty | `2db470b78544f02d38c9df8ccef20acb171d4110` (HEAD of 2026-11-09) |
| Termux upstream `termux-app` | `8629e632fcb95da272221be327db653fb24befe9` (2026-09-26) |
| JackPal Android-Terminal-Emulator | `35188f8a8b57989a4a4ec9485e11187b46be26d9` (last commit 2020-10-20) |
| ConnectBot / termlib | `66ee8fae42dbede33d631b26c0df5c456040d4e9` / `2d4d7a866186beba45baf95902ddd492ec6e139e` |
| chuchu / umbra | `00f9fd2407f98a8e6c7d026065f8dd9d9482a635` (2026-11-06) / `169101106aa09c3580667ec2e0132d7019829e90` (2026-02-22) |
| AOSP (`frameworks/base`, `frameworks/minikin`, `external/skia`) | tag `android-16.0.0_r1` |

Companion note: `libghostty-vt-research-2026-11-07.md` (the terminal-state library, which draws
nothing). Paths below are repo-relative to this repo unless prefixed by a project name.

## Where this repo is today

- One Java renderer, `terminal-view/src/main/java/com/termux/view/TerminalRenderer.java`, draws
  through the view's `Canvas`. There is no `GLSurfaceView`, `SurfaceView`, `TextureView`, EGL or
  `RuntimeShader` in `terminal-view/` (grep of the module; the only drawing entry is
  `TerminalView.onDraw`, `TerminalView.java:1022`).
- `renderRows` (`TerminalRenderer.java:1115`) walks visible rows; a per-cell loop (about
  `:1457-1790`) builds runs of equal style and flushes each with `drawTextRun`
  (`:2290-2351`, `drawTextRunConfigured` `:2402`). A run is one `canvas.drawTextRun(...)` call
  with one `Paint` (`:2517`); a few special cells use `canvas.drawText` (`:2066, :2081`).
  Underlines, strikethrough, cursor and box/block shapes are `canvas.drawRect` geometry
  (`:2487, :2496, :2722-2764, :3317-3399`), not font glyphs.
- Cell backgrounds and cursor blocks are painted for every row before any glyph is painted, so ink
  that overhangs its cell (Nerd Font symbols) lands over the next row's fill
  (`TerminalRowNodes.java:9-20`).
- `RowRenderCache` (`:350` field in the renderer) remembers, per visible row, exactly what the row
  was drawn from (text, widths, style longs, side tables, the cursor and selection slice) and
  compares by value, so an unchanged row is replayed instead of re-derived
  (`RowRenderCache.java:10-47`). A scroll moves every row's state `k` places instead of
  re-recording (`:20-27`).
- The replay uses `android.graphics.RenderNode`: three per visible row (backgrounds, glyphs,
  images), recorded on the UI thread and replayed with `drawRenderNode`
  (`TerminalRowNodes.java:30-57`, `TerminalRenderer.java:1326-1355`). `RenderNode` is public from
  API 29, so below 29 or on a software canvas the renderer draws straight onto the canvas
  (`TerminalRenderer.java:1205-1222`, `ScrollRenderCache.java:37-46`). A 45-row pane therefore
  holds 135 render nodes (reasoned, 45 x 3).
- Per-cell font decisions the draw path makes today, each of which a GPU path would have to
  reproduce: fallback typeface choice (`FallbackFontResolver.java`), `symbol_map` ranges with a
  memoised `Paint.hasGlyph` check (`SymbolGlyphCache.java:1-18`), font feature and variation
  strings per run, a contrast floor on ink (`ContrastFloor.java`, `legibleInk` at
  `TerminalRenderer.java:2505`), text-sizing blocks drawn as one piece because their ink leaves
  the row's line box (`drawTextBlock`, `:1845`), kitty images and placeholders
  (`KittyLayerPainter.java`), cursor trail (`KittyCursorTrail.java`), URL underlines
  (`UrlUnderlines.java`) and selection.
- The pane is clipped to a rounded shape; `PaneShape` (`app/src/main/java/com/termux/app/terminal/PaneShape.java`)
  caps the radius at a third of the shorter side and computes how far content sits in from the
  corner arcs so no glyph is cut (`PaneShape.java:14-34`). Glass behind a pane is recorded and
  blurred through `RenderEffect` on the RenderThread and re-recorded when what is beneath it
  changes (`docs/adr/0021-glass-records-what-is-beneath-it.md`; `app/src/main/java/com/termux/app/chrome/PanelBackdrop.java`,
  `GlassEffects.java`). Pane transitions snapshot a pane into a `RenderNode` and clip it with a
  path (`app/src/main/java/com/termux/app/terminal/PaneSnapshot.java:84, :115`).
- Budgets from `docs/agents/performance.md`: idle PSS 203-213 MB (native heap about 91 MB); at
  rest the launcher draws 5 frames in 30 s; no continuously repainting animation; anything holding
  pixels sits behind a bounded store; live glass is capped at 8 surfaces, about 8.8 MiB; frame
  budget 11.1 ms at 90 Hz; glyph-heavy pane render median 2.4 ms; cold start 1.33-1.54 s.
- Flood measurement given for this task (52x45 pane, 200k-line flood, pong): Java core 4.9-5.2 s
  with the screen on, about 3.5 s with the screen off, 25-36% janky frames. So drawing is roughly
  1.5 s of 5 s, and parsing runs on the main thread. (reasoned) A renderer change can address at
  most the drawing share; the parse share and its main-thread placement belong to the core.

## 1. How HWUI and Skia draw text today

What the sources show (AOSP `android-16.0.0_r1`):

- Path from `Canvas.drawText`/`drawTextRun` to the GPU: Minikin lays the text out and a layout
  cache holds results (`frameworks/minikin/include/minikin/LayoutCache.h:208-232`, a static
  `LayoutCache` of `kMaxEntries = 5000`). `SkiaCanvas::drawGlyphs` copies glyph ids and positions
  into an `SkTextBlobBuilder` run (`frameworks/base/libs/hwui/SkiaCanvas.cpp:820-841`) and calls
  `mCanvas->drawTextBlob`. So what is recorded is glyph ids plus positions, not the string.
- Recording versus drawing: with hardware acceleration, drawing code runs on the UI thread into a
  display list; the RenderThread replays it. Views that were not invalidated replay their old list
  (developer.android.com "Hardware acceleration", "Hardware accelerated drawing model"). Display
  lists are the unit `RenderNode` exposes, and this repo already records per row (above).
- The glyph cache is Skia's, per process, owned by HWUI's `CacheManager`
  (`libs/hwui/renderthread/CacheManager.cpp`):
  - `fGlyphCacheTextureMaximumBytes = mMaxGpuFontAtlasBytes` (`:106`), where
    `mMaxGpuFontAtlasBytes = nextPowerOfTwo(mMaxSurfaceArea)` (`:68`) and `mMaxSurfaceArea` is the
    display's width x height times a policy scale (`:40-42`; the scale value comes from
    `loadMemoryPolicy()` and was not read). Skia's own default is `2048 * 1024 * 4` bytes
    (`external/skia/include/gpu/ganesh/GrContextOptions.h:126`).
  - A CPU font cache at least four times the GPU atlas limit (`:70-73`).
  - Resource cache limit `mMaxSurfaceArea * surfaceSizeMultiplier` (`:62`).
  - `trimMemory` purges by trim level: scratch and unlocked resources, font cache and the whole
    resource cache on deeper levels (`:119-168`). A custom renderer's memory is outside this.
  - Glyph atlas planes use A8 and A565 mask formats in the Ganesh text manager
    (`external/skia/src/gpu/ganesh/text/GrAtlasManager.cpp:105-116`, the readback switch shows
    `kA8` and `kA565`).
- Ganesh versus Graphite: HWUI's pipeline choice in the files read is only `SkiaGL` or `SkiaVulkan`
  (`libs/hwui/Properties.cpp:246-256`: property `debug.hwui.renderer` of `skiavk` or `skiagl`,
  default `skiavk` only when `use_vulkan()` is true, else `skiagl`). `SkiaOpenGLPipeline.cpp` and
  `SkiaVulkanPipeline.cpp` build Ganesh targets (`GrBackendRenderTargets::MakeGL`,
  `SkiaOpenGLPipeline.cpp:144`), and the context options being tuned are `GrContextOptions`
  (`CacheManager.cpp:102-110`). Graphite's `ContextOptions.h` exists in `external/skia`
  (`include/gpu/graphite/ContextOptions.h`) but no Graphite pipeline appears in the HWUI files
  read. No Android documentation page on Graphite in HWUI was found. This is not a full-tree
  search.
- Cost of a `drawText`/`drawTextRun` per run (what the sources support): CPU work per call is
  layout (cache hit or Minikin shaping), a text-blob build and an op recorded; the pixel work is
  done on the RenderThread by Skia using the glyph atlas. No per-call timing is published in the
  sources read. This repo's own number is the pane render median 2.4 ms for a glyph-heavy slice
  (`docs/agents/performance.md`), which includes recording.
- Are per-row `RenderNode`s already "a GPU renderer in effect"? (reasoned) Partly. The glyph
  rasterisation and the atlas are already GPU-resident, and a replayed row issues textured quads
  from Skia's atlas on the RenderThread, so the pixel work is GPU work. What the Canvas path does
  not have, compared with the dedicated renderers below, is one data structure keyed by terminal
  cell: it is a stream of per-run draw ops, each carrying its own layout, `Paint` state and glyph
  positions, and the row cache is how this fork avoids paying for them again. A dedicated renderer
  replaces "ops per run" with "one instance per cell, one instanced draw per layer", and moves the
  per-cell work (resolve style, look up glyph in the atlas) to a place the fork controls.
  Whether that is cheaper on pong is not measured (see Open questions).
- Scaling and text caveats from the docs: `setSubpixelText` is hardware-accelerated from API 28,
  `setLinearText` is not supported, text scaling is correct from API 18 for `drawText`
  (developer.android.com "Hardware acceleration", support tables).

## 2. How dedicated terminal GPU renderers work

### Ghostty (OpenGL; `vt-1.0.0`)

- Backends in the tree are OpenGL and Metal (`src/renderer/OpenGL.zig`, `Metal.zig`,
  `generic.zig` is the shared renderer over them). Android is not a Ghostty renderer target; the
  Android work is the VT library only (see the companion note).
- It is desktop OpenGL 4.3 core: `MIN_VERSION_MAJOR = 4`, `MIN_VERSION_MINOR = 3`
  (`src/renderer/OpenGL.zig:39-40`), created through EGL with the core-profile bit
  (`:61-68`). The text vertex shader reads cell backgrounds from a `std430` shader storage buffer
  (`src/renderer/shaders/glsl/cell_text.v.glsl`, `layout(binding = 1, std430) readonly buffer
  bg_cells`) and the fragment shader samples `sampler2DRect` atlases in pixel coordinates
  (`cell_text.f.glsl:3-4`). (reasoned) `sampler2DRect` and the desktop profile mean a port to
  GLES 3.x would rewrite those lines; SSBOs exist from ES 3.1.
- Data model: per row, a list of `CellText` instances plus a flat array of 4-byte background
  colours indexed by cell (`src/renderer/cell.zig:36-60`). One `CellText` is
  `glyph_pos[2]u32, glyph_size[2]u32, bearings[2]i16, grid_pos[2]u16, color[4]u8, atlas u8,
  bools u8` (`src/renderer/opengl/shaders.zig:234-257`; the file's own test comment names 32
  bytes). The vertex shader expands an instance into a quad from `gl_VertexID` as a 4-vertex
  triangle strip (`cell_text.v.glsl:55-95`), and the draw is `glDrawArraysInstanced`
  (`src/renderer/opengl/RenderPass.zig:131`).
- Rebuild on dirty rows: `rebuildCells` does a full rebuild if the terminal state is `dirty ==
  .full` or the grid size changed, otherwise it visits rows and `continue`s past any row whose
  dirty flag is false, clearing and rebuilding only dirty rows (`src/renderer/generic.zig:2630-2760`,
  the row loop at `:2720-2750`). The terminal state it reads is the same dirty-row render state
  libghostty-vt exports (`include/ghostty/vt/render.h`: dirty enum, per-row dirty at `:442`, row
  identity at `:80-91` and `:480`). `updateFrame` (`:1371`) copies state under a lock, `drawFrame`
  (`:1716`) draws; both run on a render thread (`src/renderer/Thread.zig`).
- Frames in flight: `swap_chain_count = 3` (`OpenGL.zig:34`), each frame owning its buffers and
  atlas textures; the atlas is re-synced per frame when its `modified` counter moved
  (`generic.zig:1891-1904`, `syncAtlasTexture` `:3677`). (reasoned) That means three GPU copies of
  each atlas plus the CPU copy; a mobile port could use fewer.
- Glyph atlas: `src/font/Atlas.zig` is a rectangle-packing atlas (Jylanki skyline, `:1-14`),
  always square, three formats (`grayscale` 1 byte, `bgr` 3, `bgra` 4, `:53-67`), with `modified`
  and `resized` atomic counters so the GPU copy knows when to re-upload or re-create (`:42-51`),
  `reserve`, `set`, `grow` (`:136, :256, :314`). Two atlases exist, grayscale and colour, both
  created at 512x512 (`src/font/SharedGrid.zig:92-94`), so a fresh pair is 256 KiB + 1 MiB
  (reasoned arithmetic). The CPU buffer `data: []u8` (`Atlas.zig:25`) is what makes re-upload after
  loss possible.
- Sharing: fonts, glyph caches and atlases are shared by every surface with the same font
  configuration (`src/font/SharedGridSet.zig:1-8`).
- Rasterising and shaping: FreeType and HarfBuzz (`src/font/face/freetype.zig`,
  `src/font/shaper/harfbuzz.zig`), CoreText on macOS; backends enumerated in
  `src/font/backend.zig:7-26`. Box drawing, blocks and similar are drawn by a sprite generator,
  not looked up from fonts (`src/font/sprite/`).
- Licence: MIT (companion note).

### Alacritty (GLES2 path; `aa5d942`)

- Two text renderers behind one trait: GLSL3 and GLES2, selected by the context API and a config
  preference (`alacritty/src/renderer/mod.rs:117-160`, `text/gles2.rs`, `text/glsl3.rs`).
- GLES2 path: batched vertices, not instances. `BATCH_MAX` is the largest multiple of four that
  fits a `u16` index (`text/gles2.rs:221-225`), drawn with `glDrawElements` from a pre-built
  index buffer (`:60-61, :84-95`). The glyph atlas is 1024x1024 RGBA (`text/atlas.rs:11-12, :79-88`;
  a GLES context cannot upload RGB into an RGBA texture, `:161-173`); a full atlas starts a new
  one (`:268`).
- Subpixel text without a shader that reads two textures: dual-source blending when available,
  else a three-pass scheme selected by a `renderingPass` uniform in the fragment shader
  (`alacritty/res/gles2/text.f.glsl`, pass 0 backgrounds, pass 1 coverage into alpha, pass 2 background through the inverted mask, any other value the foreground colour through the mask).
  Dual-source blending is an optional extension (`gles2.rs:36-55`).
- Redraw policy: each draw walks every renderable cell and batches glyphs
  (`text/mod.rs:58-65, :135-168`); there is no per-row retained GPU buffer in the files read. Damage
  tracking exists and is used for `swap_buffers_with_damage` on Wayland EGL
  (`display/mod.rs:608-616`).
- Context loss: a recovery routine rebuilds the renderer, resets the glyph cache and marks the whole
  frame damaged (`display/mod.rs:588-603`, "Recovered window from gpu reset").
- Rasterising: `crossfont` (`alacritty/Cargo.toml:30`), box drawing built in
  (`text/builtin_font.rs`, 1032 lines). A grep of `Cargo.toml` and `Cargo.lock` found no
  HarfBuzz-family dependency, so no ligature shaping in the files read.
- Licence: Apache-2.0 (`LICENSE-APACHE`).

### kitty (`2db470b`)

- OpenGL 3.1 minimum on Linux, 3.3 on macOS, GLSL 140 (`kitty/data-types.h:25-32`). Shaders are
  now written in Slang (`kitty/shaders/cell.slang`, `#language slang 2026`, `background.slang`).
- Sprite map: glyphs live in a `GL_TEXTURE_2D_ARRAY`, grown by copying layers with
  `glCopyTexSubImage3D` and filled with `glTexSubImage3D` (`kitty/shaders.c:183-265`); the cell
  shader turns a sprite index into array coordinates (`cell.slang:55-65`). Cells are instanced
  attributes (`shaders.c:2258`).
- Rebuild policy: `cell_prepare_to_render` rewrites the whole cell buffer when the screen is
  dirty, scrolled, resized or `reload_all_gpu_data` is set (`shaders.c:1357-1383`, the
  `update_cell_data` macro maps the buffer "for full rewrite"). That is a screen-level dirty flag,
  not per-row, in the code read.
- Shaping and raster: HarfBuzz (`kitty/fonts.c:1265-1277`, one `hb_buffer_add_codepoints` call per
  run) and FreeType (`kitty/freetype.c`); ligatures work; decorations and box drawing are
  generated (`kitty/decorations.c`, `fonts/`).
- Licence: GPLv3 (`LICENSE`); code cannot be copied into this app.

### Android terminals

| Project | What it draws with | Source |
|---|---|---|
| Termux upstream (`8629e63`) | `Canvas.drawTextRun` / `drawText` in `TerminalRenderer`, called from `TerminalView.onDraw`. The only GL mention is a comment about `GL_MAX_TEXTURE_SIZE` in `TerminalBitmap.java:74-82` | `terminal-view/.../TerminalRenderer.java:182-262`, `TerminalView.java:1022` |
| JackPal Android-Terminal-Emulator (`35188f8`, last commit 2020-10-20) | `Canvas.drawText` in `PaintRenderer`; a bitmap-font renderer `Bitmap4x8FontRenderer` exists too | `emulatorview/.../PaintRenderer.java:39, :103-107` |
| ConnectBot | Uses the `org.connectbot:termlib` 0.3.14 library (`gradle/libs.versions.toml:33, :79`). termlib draws on a Compose `Canvas` with `nativeCanvas`, shapes through `Paint` with ligature features switched off (`fontFeatureSettings = "'liga' 0, 'clig' 0"`), and its emulator is libvterm in C | `termlib/lib/src/main/java/org/connectbot/terminal/Terminal.kt:86, :1637, :1683`, `TerminalShaping.kt:24`, `lib/src/main/cpp/libvterm/` |
| chuchu (libghostty-vt Android SSH client) | Compose `Canvas` and `nativeCanvas.drawText` / `drawRect` with one `Paint` per typeface; cells arrive from the Zig bridge as a snapshot `ByteBuffer`. So libghostty-vt state drawn by CPU-recorded Canvas ops, not a GPU text renderer | `android/app/src/main/java/com/jossephus/chuchu/ui/terminal/TerminalCanvas.kt:114-145, :592-874`, `service/terminal/GhosttyBridge.kt:30` |
| umbra (libghostty-vt, "GPU-accelerated") | Design only. `AGENTS.md` and `docs/ARCHITECTURE.md` describe a Vulkan `SurfaceView` renderer with a glyph atlas, a render thread blocked on a condition variable and woken only by dirty cells, and HarfBuzz/FreeType; the code in the tree is a 64-line `jni_bridge.cpp` whose Vulkan parts are `TODO` comments, and `AGENTS.md` says "JNI stub only" | `umbra/AGENTS.md`, `docs/ARCHITECTURE.md:186-250`, `docs/PRD.md:21-56`, `terminal/src/main/cpp/jni_bridge.cpp:41, :61` |

- Not examined: Material Terminal and "Tabby"-like apps, which were named as candidates; no source
  for them was opened. Termux:X11 draws an X server, not a terminal's cells, so it was left out.
- Result: none of the Android terminals read ships a GPU glyph-atlas text renderer. The one
  that plans to (umbra) has not built it.

## 3. Android surface choices for a pane

The constraints a pane has to keep: rounded clipping (`PaneShape`), translucency over a wallpaper
that is a separate window, blur beneath it, drawn in the pane's place in the launcher's view tree.

| Choice | What the sources say | Fit to this pane |
|---|---|---|
| `SurfaceView` | A separate surface. Z-ordered behind the window with a hole punched through it, or above it (docs class description; `SurfaceView.java`, composition order replaces `setZOrderMediaOverlay`, which is deprecated, `:815-825`). "A full alpha-blended composite will be performed each time the Surface changes" for views over it (docs). Alpha: arbitrary alpha only from API 34; before that alpha between 0 and 1 was ignored. With composition order at or above 0 the alpha is applied to the surface, below 0 to the hole punch (docs "Alpha"; `SurfaceView.java:555-560, :1110-1121`). Clip: `setClipBounds` (docs); rounded clip: `SurfaceView.setCornerRadius` is `@hide` (`:748-761`) and rounds both the surface and the punched hole (`:726-745`); `SurfaceControl.Transaction.setCornerRadius` is `@hide` with `@UnsupportedAppUsage(maxTargetSdk = R)` (`SurfaceControl.java:3700-3714`); `setBackgroundBlurRadius` is `@hide` (`:3754-3764`). `SurfaceControl.Transaction.setAlpha` has no hide tag in the file (`:3466-3475`). `SurfaceView.java` contains no reference to a View outline (grep). | See below |
| `TextureView` | Hardware-accelerated windows only; "will draw nothing" in software. Behaves as a regular View, so translucency, rotation and "complex clipping" work (docs). "TextureView contents must be copied, internally, from the underlying surface into the view" and docs recommend `SurfaceView` as the general solution (docs). Memory cost and latency are not stated in the class description. | Clip, alpha and in-tree z-order work as for any view; costs the copy and an extra buffer set |
| GL into a `HardwareBuffer`, then `Bitmap.wrapHardwareBuffer`, drawn with `Canvas.drawBitmap` | `HardwareBuffer` is API 26 and "allows sharing buffers across different application processes ... accessible to the GPU" (docs). `Bitmap.wrapHardwareBuffer` creates a hardware bitmap (docs summary); hardware bitmaps are immutable and cannot be read back with `getPixels` (docs). `Bitmap.getHardwareBuffer` is API 31 (docs). The API level of `wrapHardwareBuffer` was in a truncated part of the page and was not read. | See below |
| `GLSurfaceView` | Renders on a dedicated thread; `RENDERMODE_WHEN_DIRTY` draws only on `requestRender()`; default surface is RGB_888 and a translucent one needs `setFormat(PixelFormat.TRANSLUCENT)` (a 32-bit surface, docs); it is a `SurfaceView` subclass, so the SurfaceView rows apply | As `SurfaceView`, plus a thread per view |
| Custom GL functor into HWUI's draw (the WebView route) | `RecordingCanvas.drawWebViewFunctor(int)` is `@hide` (`RecordingCanvas.java:162-166`); `WebViewDelegate`, the class that exposes it, is `@hide @SystemApi` ("used by the WebView provider implementation", `WebViewDelegate.java:40-46, :121`); the older `callDrawGlFunction` forms are `@deprecated` there (`:80-135`); the native side is `WebViewFunctor` in `libs/hwui/WebViewFunctorManager.h`. | Not available to an app; it is not a public API |
| `HardwareRenderer` and `RenderNode` | Public Java classes (`graphics/java/android/graphics/HardwareRenderer.java`, 1654 lines). They draw display lists through Skia; no entry point for app-supplied GL or Vulkan commands was found in the file's public surface (not a line-by-line audit). | Already used for rows; not a way to run a custom shader pass |

Reading the SurfaceView row against this pane (reasoned, from the class description and
`SurfaceView.java`):

- Z-below: the surface sits behind the window and the window shows it through a punched hole.
  Whatever the launcher draws under the pane inside the window (the glass backdrop, the blurred
  wallpaper picture) is cleared by the hole where the pane is, so the translucent look would have
  to be painted into the surface itself, and the glass would no longer sample what is behind the
  pane through the view tree.
- Z-above: the surface covers the window content around it, including the dock, overlays, sheets
  and menus that sit above a pane in the view tree, unless each of those is also a surface. The
  docs' note about overlapping `SurfaceView`s not blending properly applies when several panes
  overlap with z-below.
- Alpha on the surface is only specified for API 34 and later; minSdk is 26 (reasoned: translucency
  as a View property would be unspecified on 26-33).
- Rounded corners: the public route is not documented for `SurfaceView` in the pages read; the
  hidden `setCornerRadius` is greylisted per the annotation shown for the `SurfaceControl` method
  (`maxTargetSdk = R`), and this app targets 28 (reasoned: whether the hidden-API policy lets a
  target-28 app call it was not checked).
- A `SurfaceView`'s pixels are not in the view's display list, so snapshots built by recording a
  view into a `RenderNode` (`PaneSnapshot.java`, the departure card and the live-glass
  recording) would not capture it (reasoned from the hole-punch description).
- Pane count: each pane would be its own surface and buffer queue (reasoned).

Reading the `HardwareBuffer` row (reasoned): the GL renderer draws into an off-screen buffer on its
own thread, and the buffer is then an ordinary image in the view tree, so `PaneShape` clipping,
alpha, the glass behind it, snapshots and `RenderNode` recording keep working. It gives up the
direct-to-compositor path and has to synchronise buffer hand-off between the GL thread and the
RenderThread; the sources read do not describe that hand-off (fences, buffer reuse) and it is not
settled here. A hardware bitmap cannot be drawn into or read back on the CPU (docs).

API availability for the GPU API itself:

- GLES: 2.0 from API 8, 3.0 from API 18, 3.1 from API 21, 3.2 from API 24, each "on devices that
  provide an implementation"; declare the version you need or check at run time
  (developer.android.com "OpenGL ES"). minSdk 26 is above all of those, so the OS floor is not the
  limit; per-device implementation is. Ghostty's 4.3 core profile is not part of that list.
- Vulkan: the NDK Vulkan guide requires "a device ... that supports Vulkan, running Android 7.0
  (Nougat), API level 24 or higher" (developer.android.com NDK graphics getting started). Per-device
  presence at API 26 and the Vulkan minor versions available were not found in the pages read.
- HWUI picks Ganesh on GL or on Vulkan by property or flag (`Properties.cpp:246-256`); a custom
  renderer's API choice is independent of that choice.

## 4. Text shaping and rasterising for an atlas

- Rasterise with Android's own `Paint`/`Typeface` into a bitmap, then upload: the Android 15
  behaviour-change page lists exactly this as an option for apps with their own renderer: "Use
  platform APIs for text rendering. You can render text to a bitmap-backed `Canvas` and use that to
  get a raw image if necessary." (developer.android.com "Behavior changes: all apps", Android 15).
  This keeps fallback fonts, variable fonts and emoji on the platform's side, and needs no native
  library, so it fits the three editions' package-name constraint (reasoned).
- Bundle FreeType and HarfBuzz: what Ghostty and kitty do (Ghostty `src/font/backend.zig`,
  `shaper/harfbuzz.zig`; kitty `fonts.c`, `freetype.c`). It brings a native build per ABI (arm64,
  armv7, x86_64) and its own font discovery; Android exposes system fonts through APIs the
  note did not survey (`Typeface`, `android.graphics.fonts.Font`, NDK font APIs: not read).
- Colour fonts and emoji: from Android 13 (API 33) the system emoji font is vector COLRv1 rather
  than PNG; the legacy PNG file `NotoColorEmojiLegacy.ttf` was kept in 13 and 14 and removed in
  Android 15. An app with its own renderer must use platform text rendering, add COLRv1 support
  ("FreeType ... supports COLRv1 in version 2.13.0 and higher"), or bundle the old font as "a last
  resort", missing newer emoji (same page). Pong runs Android 16, so a self-rasterising path would
  have to handle COLRv1 on the device of record.
- Platform glyph drawing API: `Canvas.drawGlyphs` (array of glyph ids with a specified `Font`) is
  API 31 (developer.android.com `Canvas`). (reasoned) It is another Canvas-side way to separate
  shaping from drawing; the sources do not describe an API that returns a glyph's bitmap by id.
- Subpixel positioning: a terminal puts each glyph on a fixed cell origin, so positioning inside a
  cell is the only subpixel question, and `Paint.setSubpixelText` is hardware-accelerated from API 28
  (docs support table). An atlas fixes the subpixel phase when a glyph is rasterised; Ghostty and
  Alacritty both rasterise one bitmap per glyph and place it by integer bearings
  (`cell_text.v.glsl:70-95`, `generic.zig` `addGlyph` `:3477`), so no per-position raster
  (reasoned; Alacritty's subpixel colour comes from the LCD mask and blending above).
- Wide and CJK cells: libghostty-vt cells carry spacer cells for wide characters (companion note,
  reflow section); a renderer draws one glyph spanning two columns. The Canvas path today handles
  width by run, measuring against the cell width and fitting symbol runs (`TerminalRenderer.java:2034-2081`).
- Ligatures: need a shaper over runs. kitty and Ghostty shape runs with HarfBuzz; Alacritty has no
  shaper in the files read, so no ligatures; termlib turns ligature features off. Today's path
  passes font feature strings per run to `Paint` (`TerminalRenderer.java`, run configuration at
  `:2402`). A glyph-atlas renderer keyed by glyph id (not by codepoint) is what makes ligatures
  and multi-glyph clusters work, and it also needs a cell-spanning rule for a cluster larger than
  one cell (reasoned from Ghostty's `CellText` carrying glyph size and bearings separately from
  the grid cell).
- Box drawing, blocks, braille and powerline shapes: Ghostty and Alacritty generate them
  (`src/font/sprite/`, `text/builtin_font.rs`) so they tile exactly; this repo draws them as
  `drawRect` geometry (`TerminalRenderer.java:2722-3399`). A GPU path would carry its own
  generator or draw them as rectangles in the same pass.

## 5. Costs

Memory:

- Atlas: fonts and glyphs are shared by all panes with the same font in Ghostty
  (`SharedGridSet.zig:1-8`); the repo's panes can have different sizes and fonts, so sharing would
  be keyed the same way (reasoned). Starting sizes seen: Ghostty 512x512 grayscale + 512x512
  BGRA (`SharedGrid.zig:92-94`), Alacritty 1024x1024 RGBA = 4 MiB per page
  (`atlas.rs:12, :79-88`), Skia up to `nextPowerOfTwo(screen area)` bytes for its own atlas
  (`CacheManager.cpp:68`). Any atlas here would need a budget constant and a test like the stores
  in `docs/agents/performance.md`.
- Per-pane cell buffers: Ghostty's foreground instance is 32 bytes per glyph and the background 4
  bytes per cell (`shaders.zig:234-257`, `cell.zig:36-60`); a 52x45 pane is 2,340 cells, about
  75 KB + 9 KB per buffer set (reasoned arithmetic), times frames in flight.
- Output pixels: a `SurfaceView` or `HardwareBuffer` path holds a full-pane RGBA buffer, 4 bytes per
  pixel, and a buffer queue holds more than one (reasoned). A full-screen pane at about 1080x2400
  is about 10 MB per buffer; the device-of-record resolution and the queue depth were not
  measured. The Canvas path holds no per-pane pixel buffer of its own.
- GPU memory of Skia's glyph atlas stays in HWUI's process cache and is trimmed by it
  (`CacheManager.cpp:119-168`); the launcher's own atlas would have to be trimmed from
  `TermuxActivity.onTrimMemory` (named in `performance.md`).
- Idle ground to compare with: 203-213 MB PSS (`performance.md`). No source gives the native GPU
  driver's overhead for an extra GL context on Adreno.

Contexts, sharing and loss:

- One `GLSurfaceView` has "a dedicated thread" and its own EGL context (docs). EGL contexts can be
  created sharing objects with another (the third argument to `eglCreateContext` in the docs'
  example is `EGL_NO_CONTEXT`, the share-context slot); sharing the atlas between several panes
  needs one share group. Whether Adreno's driver on pong shares textures across such contexts without
  penalty was not found.
- `GLSurfaceView.onPause`/`onResume` may tear down and re-create the EGL context unless
  `setPreserveEGLContextOnPause` is set (docs). Alacritty rebuilds and re-uploads on a GPU reset
  (`display/mod.rs:588-603`). Ghostty keeps the atlas bytes on the CPU (`Atlas.zig:25`), which is what
  makes a re-upload possible. (reasoned) The launcher is home and is rarely destroyed, but a GL
  context lost on `onTrimMemory` or surface destruction must be rebuildable from CPU-side state, so
  a CPU copy of the atlas, or re-rasterisation on demand, is part of the cost.
- `SurfaceView` surface lifecycle: surfaces are created and destroyed with visibility and window
  state; umbra's design note releases Vulkan resources in `surfaceDestroyed` and resumes from the
  last VT state (`docs/ARCHITECTURE.md:248-249`).

Battery at rest and start-up:

- At rest the launcher draws 5 frames in 30 s (`performance.md`). A GL renderer can follow the same
  rule with `RENDERMODE_WHEN_DIRTY` or a thread parked until the core marks rows dirty (docs;
  Ghostty and umbra's design do the same). Whether SurfaceFlinger recomposes an unchanged
  `SurfaceView` layer on every vsync was not found in the sources read.
- Blink and cursor trail: anything that repaints continuously (cursor blink, the cursor trail)
  would re-issue a GPU frame per tick; today they go through View invalidation (reasoned).
- Start-up: a GL path adds EGL init, shader compilation and atlas warm-up. HWUI avoids shader
  compile cost with a persistent shader cache (`CacheManager.cpp:107-110`, `ShaderCache`); a
  custom program has no such cache unless it saves program binaries itself (reasoned; not
  checked). Cold start today is 1.33-1.54 s (`performance.md`). No measured number exists for the
  added cost.

Build and maintenance (reasoned):

- A Java renderer over `android.opengl.GLES30` and `Paint` rasterisation adds no native library,
  so the three editions' package names do not matter; bundling FreeType/HarfBuzz adds a native
  build for three ABIs on top of whatever the VT core needs.
- Ghostty's renderer is MIT but desktop 4.3 core, kitty's is GPLv3, Alacritty's GLES2 path is
  Apache-2.0: Alacritty's is the only one in the three that is both GLES-based and licence-
  compatible for reading closely; Ghostty's is readable as a design for an instanced cell shader.
- The renderer's feature set today is large (see "Where this repo is today"); every item in it
  would have to be re-created or delegated to a Canvas overlay layered on the surface, and the
  overlay relationship is then the same surface-choice question.

## 6. Public measurements, Canvas text versus GL text on Android

- Nothing public found with numbers and a stated method that compares Canvas `drawText` against
  a GL glyph-atlas renderer on Android phones. Searches for an Android terminal GL-vs-Canvas
  benchmark returned the Alacritty GLES2 source, a WebGL terminal renderer's own README claim
  (not Android, self-reported, not used), a 2014 blog description of HWUI's font renderer
  (secondary, not used) and desktop terminal posts. No Android hwui text benchmark was found.
- Nearest primary numbers are unrelated to the comparison: Ghostty 1.4.0's parser throughput
  figures (companion note) and this repo's own frame numbers (`performance.md`).
- The only comparison the data here can support is internal: screen on versus screen off in the
  flood test (about 1.5 s of 5 s is drawing), which includes recording on the UI thread and the
  RenderThread work, and does not separate them.

## Advantages (what the sources support)

- Per-cell GPU data (one instance per glyph plus one background per cell) with dirty-row rebuilds
  is the model Ghostty uses and a ready fit for libghostty-vt's dirty rows and stable row ids
  (`render.h:80-91, :442, :480`); the fork's `RowRenderCache` already solves the same
  "what changed" question with value comparison.
- Rasterise each glyph once into an atlas the fork owns and budgets, instead of leaving it in
  Skia's per-process cache (`CacheManager.cpp:62-75`), which gives a bounded, testable store in
  the style of `LauncherIconStore` (reasoned).
- Replaces "N recorded ops per run, each with its own layout and `Paint` state" with instanced
  draws; the cost of layout moves to when a glyph first enters the atlas (reasoned).
- Atlas lookup keyed by glyph id permits shaping outcomes beyond one codepoint per cell, and a GPU
  path can carry effects (the repo already uses AGSL for effects) in the cell shader (reasoned).
- The scroll case: stable row ids would shift instance rows instead of re-recording row nodes,
  the same trick `RowRenderCache.lastShift` performs for display lists (reasoned).
- Idle can stay at zero: dirty-driven rendering is a documented mode (`RENDERMODE_WHEN_DIRTY`).

## Disadvantages and risks

- HWUI already draws text on the GPU from an atlas, and the fork already replays unchanged rows
  from display lists. The gain is not measured on Android by anyone publicly; the drawing share
  of the flood test is about 30% of wall time and the parse share is outside this change.
- The surface choices each give something up: `SurfaceView` has no public rounded clip, alpha only
  from API 34, and either hides the glass or covers the chrome above the pane (above); `TextureView`
  adds a copy; the hardware-buffer path adds hand-off synchronisation and an extra full-pane
  buffer; the WebView functor route is closed to apps.
- Feature parity is large: kitty images and placeholders, text-sizing blocks that draw outside
  the row, cursor trail, URL underlines, selection, contrast floor, fallback and `symbol_map`
  resolution, feature and variation strings, box and block geometry, and the below-API-29
  fallback. The recent Canvas work is built around `TerminalRow` and `RowRenderCache`.
- Colour fonts on Android 15 and later need COLRv1; a custom rasteriser either uses the platform
  (a bitmap-backed Canvas) or FreeType 2.13.0 or newer.
- Two new failure classes in the home screen: GL context loss and a render thread that can hang
  or fall behind. `AGENTS.md` says never to gate window drawing without a fail-safe; a surface
  that stays black until the first GL frame is that kind of gate.
- Panes become surfaces or buffers with their own lifecycle; snapshots and transitions that record
  views into `RenderNode`s (`PaneSnapshot`) would need a different capture source (reasoned).
- Ghostty's own renderer cannot be dropped in: desktop GL 4.3 core, `sampler2DRect`, SSBOs and
  three frames in flight (above); it is a design reference. kitty's cannot be copied (GPLv3).

## Unknowns that cost the most to settle

- Whether a GL path is cheaper than the current record-and-replay path on pong for the flood
  and for the typing frame budget. Needs a prototype or an instrumented split of recording versus
  RenderThread time.
- Which HWUI pipeline pong runs (`skiagl` or `skiavk`) and what that does to custom GL beside it.
- Whether a public, supported rounded and translucent presentation exists for a surface on
  API 26-34.

## Open questions

Need a build or a device:

- How much of the 1.5 s of drawing in the flood test is UI-thread recording and how much is
  RenderThread rasterisation? Nothing in the sources splits them; a Perfetto trace of the existing
  build would. Check which pipeline pong's HWUI uses (`getprop debug.hwui.renderer`,
  `dumpsys gfxinfo`).
- Cost of an off-screen GL context plus a `HardwareBuffer` hand-off per pane on Adreno 730 at
  90 Hz: buffer count, fences, and whether the hardware bitmap can be re-wrapped each frame without
  a copy.
- Whether SurfaceFlinger recomposes an unchanged `SurfaceView` layer each vsync (battery at rest)
  and what its buffer memory is on pong.
- Whether a target-28 app may call the hidden `SurfaceView.setCornerRadius` or
  `SurfaceControl.Transaction.setCornerRadius` on Android 16 (hidden-API policy not checked).
- Start-up cost of EGL init and shader compile in this process, and whether Adreno's driver caches
  compiled programs.
- Atlas size in practice for a real transcript with the launcher's fallback chain, Nerd symbols and
  emoji, and the glyph churn in the flood test.

Source-only, not fully settled:

- The `loadMemoryPolicy()` scale values that set Skia's atlas ceiling on a phone
  (`CacheManager.cpp:40-42` uses them; the policy file was not read).
- Whether HWUI at Android 16 has any Graphite path behind a flag; only the pipeline-selection code
  and Skia's options header were read.
- `Bitmap.wrapHardwareBuffer`'s API level and GL-to-`HardwareBuffer` interop requirements (an EGL
  extension) were not read from the primary docs; the docs page for `Bitmap` was truncated.
- Per-device Vulkan availability at API 26-29 and EGL share-group behaviour: only the NDK page's
  API 24 floor and the `eglCreateContext` argument list in the docs example were read.
- Material Terminal and "Tabby"-like apps (named in the brief) were not examined.
- Which of Ghostty's glyph-cache and cell-buffer sizes matter at a 52x45 grid: only the struct
  layout and the initial atlas sizes were read, not a profile.

## Sources

AOSP, tag `android-16.0.0_r1` (fetched from `https://android.googlesource.com/platform/<repo>/+/android-16.0.0_r1/<path>`):
- `frameworks/base`: `libs/hwui/renderthread/CacheManager.cpp`, `libs/hwui/Properties.cpp`,
  `libs/hwui/Properties.h`, `libs/hwui/SkiaCanvas.cpp`, `libs/hwui/hwui/DrawTextFunctor.h`,
  `libs/hwui/pipeline/skia/SkiaOpenGLPipeline.cpp`, `libs/hwui/pipeline/skia/SkiaVulkanPipeline.cpp`,
  `libs/hwui/WebViewFunctorManager.h`, `core/java/android/view/SurfaceView.java`,
  `graphics/java/android/graphics/RecordingCanvas.java`,
  `graphics/java/android/graphics/HardwareRenderer.java`,
  `core/java/android/webkit/WebViewDelegate.java`, `core/java/android/view/SurfaceControl.java`
- `frameworks/minikin`: `include/minikin/LayoutCache.h`
- `external/skia`: `include/gpu/ganesh/GrContextOptions.h`,
  `src/gpu/ganesh/text/GrAtlasManager.cpp`, `include/gpu/graphite/ContextOptions.h`

Android developer documentation (fetched 2026-11-09):
- SurfaceView: https://developer.android.com/reference/android/view/SurfaceView
- TextureView: https://developer.android.com/reference/android/view/TextureView
- GLSurfaceView: https://developer.android.com/reference/android/opengl/GLSurfaceView
- HardwareBuffer: https://developer.android.com/reference/android/hardware/HardwareBuffer
- Bitmap: https://developer.android.com/reference/android/graphics/Bitmap
- Canvas: https://developer.android.com/reference/android/graphics/Canvas
- Hardware acceleration: https://developer.android.com/develop/ui/views/graphics/hardware-accel
- OpenGL ES overview: https://developer.android.com/develop/ui/views/graphics/opengl/about-opengl
- Vulkan getting started (NDK): https://developer.android.com/ndk/guides/graphics/getting-started
- Android 15 behaviour changes (PNG emoji font removed): https://developer.android.com/about/versions/15/behavior-changes-all
- Android 13 features (colour vector fonts, referenced from the page above): https://developer.android.com/about/versions/13/features

Terminal projects (cloned shallow, pin above):
- Ghostty: https://github.com/ghostty-org/ghostty at `vt-1.0.0`: `src/renderer/OpenGL.zig`,
  `src/renderer/generic.zig`, `src/renderer/cell.zig`, `src/renderer/opengl/shaders.zig`,
  `src/renderer/opengl/RenderPass.zig`, `src/renderer/shaders/glsl/cell_text.v.glsl`,
  `src/renderer/shaders/glsl/cell_text.f.glsl`, `src/font/Atlas.zig`, `src/font/SharedGrid.zig`,
  `src/font/SharedGridSet.zig`, `src/font/backend.zig`, `src/font/shaper/`, `src/font/face/`,
  `include/ghostty/vt/render.h`
- Alacritty: https://github.com/alacritty/alacritty : `alacritty/src/renderer/mod.rs`,
  `alacritty/src/renderer/text/{atlas,gles2,glsl3,mod,builtin_font}.rs`,
  `alacritty/res/gles2/text.f.glsl`, `alacritty/src/display/mod.rs`, `alacritty/Cargo.toml`,
  `LICENSE-APACHE`
- kitty: https://github.com/kovidgoyal/kitty : `kitty/shaders.c`, `kitty/shaders/cell.slang`,
  `kitty/data-types.h`, `kitty/fonts.c`, `kitty/freetype.c`, `LICENSE`
- Termux: https://github.com/termux/termux-app : `terminal-view/src/main/java/com/termux/view/`,
  `terminal-emulator/src/main/java/com/termux/terminal/TerminalBitmap.java`
- JackPal: https://github.com/jackpal/Android-Terminal-Emulator :
  `emulatorview/src/main/java/jackpal/androidterm/emulatorview/PaintRenderer.java`
- ConnectBot: https://github.com/connectbot/connectbot (`gradle/libs.versions.toml`) and
  https://github.com/connectbot/termlib (`lib/src/main/java/org/connectbot/terminal/Terminal.kt`,
  `TerminalShaping.kt`, `lib/src/main/cpp/libvterm/`)
- chuchu: https://github.com/jossephus/chuchu : `android/app/src/main/java/com/jossephus/chuchu/ui/terminal/TerminalCanvas.kt`,
  `service/terminal/GhosttyBridge.kt`
- umbra: https://github.com/charliesbot/umbra : `AGENTS.md`, `docs/ARCHITECTURE.md`, `docs/PRD.md`,
  `terminal/src/main/cpp/jni_bridge.cpp`
- awesome-libghostty (where chuchu and umbra are listed): https://github.com/Uzaaft/awesome-libghostty

This repo:
- `terminal-view/src/main/java/com/termux/view/`: `TerminalRenderer.java`, `RowRenderCache.java`,
  `TerminalRowNodes.java`, `ScrollRenderCache.java`, `SymbolGlyphCache.java`, `TerminalView.java`
- `app/src/main/java/com/termux/app/terminal/PaneShape.java`, `PaneSnapshot.java`;
  `app/src/main/java/com/termux/app/chrome/` (`PanelBackdrop.java`, `GlassEffects.java`)
- `docs/agents/performance.md`, `docs/adr/0021-glass-records-what-is-beneath-it.md`
- `project-docs/reference/terminal/libghostty-vt-research-2026-11-07.md`
