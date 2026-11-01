# Terminal resize and inline images: kitty, Ghostty, Termux Launcher, 2026-10-31

How kitty and Ghostty handle a live resize (dragging a window edge or split divider, changing the
font size, reflow) while kitty-graphics images are on screen, and where Termux Launcher's
terminal differs, so resizing with images can be made smooth and unsurprising. Research only; no
app code was changed.

**Pins.** kitty `fe55cd1963b6721e489f0cba39cc69a88045136d` (master). Ghostty
`3bd54e025cc3349ee76ab1af7ec1ff8f7353632b` (main). Termux Launcher `6b5fb2a0692ccb1365c94dbd77dd4a45ddbbd969`
(`v2-redesign`, main checkout). Every `path:line` below is at that pin.
kitty paths are relative to the repo root (`kitty/...`, `docs/...`); Ghostty paths are relative to `src/`.
Termux Launcher paths are relative to the repo root.

**Marks.** **[C]** = confirmed in source or official doc at the cited line. **[I]** = inferred (from
code shape, from documented platform behaviour, or from the recording). **[U]** = not verified.
Android framework behaviour (what `clipChildren` does) is not in the repo, so it is marked [I]
even where the repo's own comments agree.

**Sources.** Primary only: the two source trees, kitty's `docs/` (the graphics protocol spec is
`docs/graphics-protocol.rst`, published at sw.kovidgoyal.net/kitty/graphics-protocol/), and the
doc comments in Ghostty's `config/Config.zig` (the source of ghostty.org/docs/config/reference
[I]: the web page itself was not fetched). The Ghostty clones are sparse, so the macOS (Swift) app
and the GTK split divider code beyond `apprt/gtk/class/` were not read.

## 1. Summary

- **The two terminals solve live resize in opposite ways.** kitty *holds* the old grid and PTY
  size while the OS window is being dragged, draws the old content pinned to the top-left over a
  blank window with an "N by M cells" banner, and resizes the screen and PTY once the drag
  pauses (0.1 s default). Ghostty does not hold: every size change from the toolkit reaches the
  terminal (reflow included) after a 25 ms coalesce, and the renderer redraws at the new size.
- **Neither ever draws a terminal outside its window or split.** kitty draws each window
  through a GL viewport set to its own rectangle; Ghostty renders into a surface-sized
  framebuffer and its render-state docs tell renderers to scissor partly visible images.
- **Images follow the same rule in both**: Unicode-placeholder images are text, so reflow moves
  them like text; classic placements are cell-anchored (kitty: a row number moved by hand;
  Ghostty: a tracked pin the page list moves). Neither rescales a picture on column change; both
  re-derive size from the cell size on a font change.
- **Termux Launcher holds like kitty but does not draw like kitty.** `GridHold` pauses the
  PTY resize for the whole drag (confirmed), which is the right idea, but the held grid is then
  drawn at its old size in a view that has already shrunk, with nothing clipping the view to
  its pane frame. That confirms the first half of the hypothesis: text and classic images
  draw outside the pane during a held drag. The second half ("classic images also aren't
  clipped to the viewport") is also confirmed: they are clipped to their own cell box only.
- **Not explained by code reading**: the bottom pane's logo starting about 75 px *above* its
  top edge, and the duplicated, stacked album art. Section 7 gives the candidate causes and the
  device reproduction that would settle them.

## 2. kitty

### 2.1 When the PTY gets the new size, and what is drawn meanwhile

- **Resize events are only recorded during the drag.** `framebuffer_size_callback` stamps
  `live_resize.last_resize_event_at`, stores the new width and height, counts events, sets the GL
  viewport and requests a tick; it does not touch the screen or the PTY
  (`kitty/glfw.c:430-446`) **[C]**.
- **The screen and PTY resize once the drag pauses.** `process_pending_resizes` calls
  `update_os_window_viewport` only when `now - last_resize_event_at >= resize_debounce_time.on_end`
  (non-macOS) or, on macOS, when the OS says the live resize ended or the pause exceeds
  `on_pause` (`kitty/child-monitor.c:1411-1445`) **[C]**. Defaults are `0.1 0.5` seconds
  (`kitty/options/types.py:657`; option text at `kitty/options/definition.py:2050-2064`) **[C]**.
  The doc text says the delay is "before asking the program running in kitty to resize and
  redraw" and that the short non-macOS delay exists "to save energy" **[C]**.
- **Only then does the window layout reach `Window.set_geometry`**, which calls
  `screen.resize(...)` when rows or columns changed (`kitty/window.py:1138-1141`) and then
  `child_monitor.resize_pty` (SIGWINCH) when the pty size differs
  (`kitty/window.py:1142-1150`, `1086`) **[C]**. So the screen reflow and the PTY resize happen
  together, once per settled size. `resize_pty` is a `TIOCSWINSZ` ioctl (`kitty/child-monitor.c:734`) **[C]**.
- **What is drawn meanwhile.** While `live_resize.in_progress`, each frame first blanks the OS
  window (`kitty/shaders.c:2870-2871`), draws the windows with the *old* geometry (they have not
  been laid out again) and pins the output to the top-left of the new, differently sized
  framebuffer (`kitty/shaders.c:3408-3410`, `3452-3453`, `3465-3466`), then restores the
  viewport and draws the banner `"%u by %u cells"` (`kitty/shaders.c:2840-2851`, `3469-3472`);
  the banner is skipped in the first second of a window's life and until a second resize event
  arrives (`kitty/shaders.c:2842`) **[C]**. Net effect: old grid, unscaled, anchored top-left,
  cut off by the shrinking framebuffer, blank where the window grew [I: composed from those
  lines].
- **`resize_draw_strategy` is gone.** The parser `resize_draw_strategy` (`static`, `scale`,
  `blank`, `size`) is still in `kitty/options/utils.py:844-846`, but no `opt(...)` in
  `kitty/options/definition.py` defines it and nothing else references it **[C]**. The brief's
  premise that this option exists today is therefore wrong for this commit; the "old size until
  the program redraws" behaviour is the only strategy.
- **Clipping.** `draw_cells` sets the GL viewport to the window's own rectangle
  (`kitty/shaders.c:2604`) **[C]**; a quad outside that rectangle falls outside clip space, so a
  window never draws into its neighbour [I: standard GL clipping; kitty adds no scissor for
  cells]. Images have an extra row cull (below).
- **Splits.** kitty's layouts resize windows by keyboard action; this note found no
  mouse-drag divider in the cloned tree **[U]** (not searched exhaustively). There is no
  per-split debounce: a split layout change calls `set_geometry` directly.

### 2.2 Reflow and images

- `screen_resize` first ends any synchronized-output pause (`screen_pause_rendering(self, false, 0)`,
  `kitty/screen.c:646`) **[C]**, rewraps the text, then calls `grman_remove_all_cell_images`
  and `grman_resize` for both the main and the alternate screen
  (`kitty/screen.c:687-691`) **[C]**.
- `grman_resize` does one thing: if the column count is unchanged and the content shrank
  vertically, every classic ref's `start_row` moves up by the shrink amount
  (`kitty/graphics.c:2781-2803`) **[C]**. Virtual refs and cell images are skipped. Any column
  change leaves classic placements where they were by row and column **[C]**: nothing rewraps
  or rescales them. **Confirmed as stated in the brief.**
- **Unicode placeholders are rebuilt from text.** The cell images created from placeholder
  characters are deleted at every resize and recreated while the screen is next rendered, from
  the (rewrapped) placeholder cells (`kitty/screen.c:4040-4091`, `grman_put_cell_image` at
  `kitty/graphics.c:1257`) **[C]**. The images follow the text because they are the text.
- The alternate screen is not rewrapped during a live resize (changelog 0.4x entry, "Do not
  rewrap the text in the alternate screen buffer. Avoids flicker during live resize",
  `docs/changelog.rst:1263-1264`) **[C]**.

### 2.3 Font and cell-size change

`screen_rescale_images` deletes all cell images then calls `grman_rescale` on both screens
(`kitty/screen.c:768-773`) **[C]**. `grman_rescale` clamps each classic ref's cell offsets to the
new cell and recomputes its destination rectangle from the new cell size
(`kitty/graphics.c:2806-2820`) **[C]**. A placement with an explicit `c`/`r` follows the cells; a
natural-size one is re-derived by `update_dest_rect` from the new cell (not read in detail **[U]**).

### 2.4 Images crossing the viewport edge

- A ref is skipped only when it is entirely outside the screen rows
  (`r.top <= screen_bottom || r.bottom >= screen_top`, `kitty/graphics.c:1679-1682`) **[C]**; a
  partly visible one is drawn whole, and the window's viewport (2.1) cuts it at the window edge [I].
- When the screen scrolls inside margins, refs entirely inside the region are clipped in place
  (source rect cropped) rather than relying on the viewport
  (`kitty/graphics.c:2535-2580`); the protocol says the same: images "must be scrolled along
  with text" and "clipped" at the page area (`docs/graphics-protocol.rst:1332-1337`) **[C]**.

### 2.5 Animation, quotas, eviction

- Animation: the render loop keeps drawing during live resize (`needs_render` includes
  `live_resize.in_progress`, `kitty/child-monitor.c:1179`); on macOS the render is driven from the
  Cocoa resize callback instead (`kitty/child-monitor.c:1218-1221`) **[C]**. Animation timing
  during the drag was not read **[U]**.
- Quota: `DEFAULT_STORAGE_LIMIT` is 320 MB (`kitty/graphics.c:29`) per buffer; animation frame data lives
  on disk with a limit of 5x (`kitty/graphics.c:2022-2024`, `2191`; spec
  `docs/graphics-protocol.rst:1193-1202`) **[C]**.
- Eviction: when a new image goes over quota, unreferenced images are dropped first, then the
  rest oldest-access first, transient ones before others (`kitty/graphics.c:321-336`) **[C]**.
  Over the frame quota the client gets `ENOSPC: Cache size exceeded cannot add new frames`
  (`kitty/graphics.c:2024`); the spec text says a full store replies `ENOSPC`
  (`docs/graphics-protocol.rst:1190`) **[C]**. What a client sees for an *image* that cannot fit
  after eviction was not traced **[U]**.

### 2.6 Smoothness measures

- Old size until the debounce (2.1); the cells banner hides the intermediate states.
- Synchronized output (mode 2026): a resize cancels any pending pause so the app's redraw is
  shown (`kitty/screen.c:646`); an unmatched pause also expires after 2 s by default
  (`kitty/screen.c:3833-3855`) **[C]**.
- In-band resize notifications (mode 2048): enabling the mode sends the current size at once
  (`kitty/screen.c:2077-2079`), and every PTY resize is followed by a `CSI 48;rows;cols;ypx;xpx t`
  report (`kitty/window.py:1086-1090`, `1739-1742`) **[C]**.

## 3. Ghostty

### 3.1 When the PTY gets the new size, and what is drawn meanwhile

- **Every toolkit size change reaches the core.** GTK's resize handler stores the size and, when
  it changed, calls `surface.sizeCallback` and schedules the overlay
  (`apprt/gtk/class/surface.zig:3500-3540`); the embedded (macOS) path does the same after
  dropping duplicate sizes (`apprt/embedded.zig:1025-1042`) **[C]**.
- `Surface.resize` records the size, then mails the IO thread (`.resize`) and the render thread
  (`apprt`-independent, `Surface.zig:2618-2652`) **[C]**.
- **The IO thread coalesces at 25 ms**: the first resize arms a 25 ms timer, later ones only
  overwrite the pending size, and the timer callback applies the last one
  (`termio/Thread.zig:26-31`, `389-404`, `435-447`) **[C]**. The source comment says the
  intent is "relatively smooth resizing" and that a max-wait refinement is future work. So Ghostty
  runs about one PTY resize and reflow per 25 ms while dragging; there is no hold until release.
- `Termio.resize` takes the render-state lock, resizes the PTY first (so SIGWINCH cannot be
  parsed against old dimensions), then the terminal, then sends a mode 2048 report if enabled
  (`termio/Termio.zig:524-564`) **[C]**.
- **Drawn meanwhile.** The render thread receives the new *screen* size immediately
  (`renderer/Thread.zig:404`, `renderer/generic.zig:2249-2266`), while the grid may lag up to
  25 ms behind. The area the grid does not cover is the padding colour: `window-padding-color`
  (`config/Config.zig:2069`) with optional `window-padding-balance` (`2048`) **[C]**. No stretched
  or clipped old grid is described; a lagging frame shows the old cells at the old cell size plus
  padding [I].
- **Resize overlay.** `resize-overlay` (`always`, `never`, `after-first`; default `after-first`),
  `resize-overlay-position` and `resize-overlay-duration` (750 ms) show a transient size popup
  (`config/Config.zig:2378-2438`); the GTK widget is `apprt/gtk/class/resize_overlay.zig` **[C]**.
- **Clipping.** Each surface renders into its own GL area/Metal view (Metal sets
  `clipsToBounds`, `renderer/Metal.zig:121`) **[C]**, so a surface cannot draw into its
  neighbour [I]. `render.zig` explicitly tells renderers to scissor images that are only partly in
  the captured rows (below).
- Splits: `split_tree.zig:942` debounces *tree* changes, not pane size; split sizes arrive as ordinary
  per-surface size changes (3.1 above) **[C]/[I]**. GTK divider code was not read beyond that **[U]**.

### 3.2 Reflow and images

- `Terminal.resize` is the one entry point. It updates the pixel size (`width_px`, `height_px`)
  on every resize, even when the grid is unchanged, and **always sets synchronized output to
  false** (`terminal/Terminal.zig:4276-4310`) **[C]**. The primary screen reflows (only when
  wraparound is on); the **alternate screen never reflows** (`terminal/Terminal.zig:4343-4349`) **[C]**.
- `Screen.resize` ends with `kitty_images.markDirty()` because "Resizing moves image placements"
  (`terminal/Screen.zig:2265`) **[C]**.
- **Classic placements are tracked pins.** A placement's location is `pin`, `virtual` or
  `relative` (`terminal/kitty/graphics_storage.zig:1966-2020`); a `pin` is a tracked pin in the
  `PageList`, so reflow moves it with its cell, and a pin whose content was pruned is garbage and
  dropped (`graphics_storage.zig:729-760`) **[C]**. Size is derived on demand: with no
  `c`/`r` the placement keeps its pixel size, otherwise `columns x rows` of the *current* cell
  (`pixelSize` `2098-2125`, `gridSize` `2175-2200`); the rect's right edge is clamped to the last
  column (`rect`, `2285-2318`) **[C]**. A cell-size or grid change regenerates the render
  placements (`terminal/render.zig:2130`, `2165`) **[C]**.
- **Placeholders are row flags.** Reflow keeps `kitty_virtual_placeholder` on the rows that still
  contain a placeholder cell, forcing the slow copy path for those cells
  (`terminal/PageList.zig:1885-1900`, `1970-1990`, `2237-2240`); tests: shrinking columns keeps
  the flag when cells wrap (`PageList.zig:19163`), growing columns moves cells back and clears the
  flag on the row left empty (`19204-19245`), and a wrap moves the flag with the cell (`19247`)
  **[C]**. Images drawn from placeholders are one placement per cell row, always inside the captured
  rows (`terminal/render.zig:200-205`) **[C]**.

### 3.3 Font and cell-size change

`Surface.setFontSize` builds the new font grid, calls `setCellSize`, which stores the new cell
size, mails a `.resize` with it to the IO thread, and tells the window (`Surface.zig:2510-2531`,
`2536-2568`) **[C]**. The IO thread then runs the same `Terminal.resize`, so `width_px/height_px`
change, render placements regenerate and the program receives a mode 2048 report with the new
pixel size (`termio/Termio.zig:556`) **[C]**.

### 3.4 Images crossing the viewport edge

The render state reports every fixed placement **at full size** when any part is in the captured
rows; `x` may be negative and `y` above the first row, and the renderer "must" clip it with a
scissor or clip rect (`terminal/render.zig:196-221`, `788-800`, `1885-1900`) **[C]**. Placeholder
images never straddle (3.2). In Ghostty's own renderer the clip is the surface bounds; no
explicit scissor call was found in `renderer/generic.zig`/`renderer/image.zig` **[C]** for
"not found", **[I]** for the conclusion.

### 3.5 Animation, quotas, eviction

- Animation frames are composed eagerly into full RGBA buffers and bounded by the same byte limit
  as images (`terminal/kitty/graphics_animation.zig:1-20`); the renderer arms its own timer for
  the next frame (`renderer/generic.zig:1152-1166`, `1458-1470`) **[C]**.
- `image-storage-limit` defaults to 320 MB, separate for primary and alternate screens, and 0
  disables image protocols (`config/Config.zig:2515-2522`; storage `terminal/kitty/graphics_storage.zig:172`)
  **[C]**. Eviction order: unused transient, unused, used transient, used
  (`graphics_storage.zig:1811-1850`) **[C]**.
- Over quota: an image larger than the limit, or one that cannot be made to fit by eviction,
  returns `OutOfMemory` (`graphics_storage.zig:414`, `430-440`), which the command layer reports
  as `ENOMEM: out of memory` (`terminal/kitty/graphics_exec.zig:1102`); a frame that does not fit
  gets `ENOSPC: animation frame storage full` (`graphics_exec.zig:678`) **[C]**.

### 3.6 Smoothness measures

25 ms coalescing (3.1); PTY resized before the terminal under the lock so the app's redraw is
parsed against the right size (`termio/Termio.zig:537-541`); synchronized output reset on every
resize (`Terminal.zig:4306`) and reset by a 1 s timer otherwise (`termio/Thread.zig:38`,
`377-385`); the renderer skips drawing while a sync is open (`renderer/generic.zig:1424`); mode
2048 reports on every resize incl. cell-size-only (`Termio.zig:556`) **[C]**.

## 4. Termux Launcher today

### 4.1 The hold during a drag

- `GridHold` is the grid's pause for one transition: through `GeometryScheduler.begin(Reason.DIVIDER, ...)`
  when a scheduler is attached, else `beginHostSurfaceResize()`; `release()` settles it
  (`app/src/main/java/com/termux/app/terminal/TerminalPaneController.java:1639-1659`) **[C]**.
  Uses: pane-resize drag of a floating pane (`7139-7152`, ends in `endDrag` `7125`), split-divider
  drag (`5841`, `6403-6426`, released at `6186`/`6229`), resize-key bursts (`436`, `2417-2455`,
  committed after `RESIZE_KEY_FINISH_DELAY_MS`), focus-grow animation (`878`) **[C]**.
- `mHostSurfaceResizeDepth` counts nested host holds; the first pauses every pane, the last resumes
  them (`1660-1673`) **[C]**.
- While paused, `TerminalView.onSizeChanged` and `updateSize` set `mTerminalSizeUpdatePending`
  and only `invalidate()`; the emulator keeps its old rows and columns
  (`terminal-view/src/main/java/com/termux/view/TerminalView.java:3161-3171`, `3180-3185`) **[C]**.
  On resume the pending update runs on the next main-loop turn, after the final layout pass
  (`TerminalView.java:3583-3600`, via `setAllPaneSizeUpdatesPaused`,
  `TerminalPaneController.java:5755-5776`) **[C]**. So the PTY (and the emulator reflow) gets
  exactly one resize, on release. The scheduler's 500 ms backstop does not end a hold while a
  transition is open (`GeometryScheduler.java:144`, `onBackstop` `~395-400`) **[C]**.
- `TerminalEmulator.resize` reflows, then always sends the in-band report; a cell-size-only
  change sends it without touching the grid
  (`terminal-emulator/src/main/java/com/termux/terminal/TerminalEmulator.java:1050-1080`) **[C]**.

### 4.2 What is drawn while held, and the clipping chain

- **The held grid is drawn at its old row count in a view that already has the new height.**
  `onDraw` draws `mEmulator.mRows` rows from `currentDrawOffset()`
  (`TerminalView.java:3669-3750`, `4258-4262`) **[C]**. The offset is the grid centred in the
  view, floored at zero: `getVerticalContentOffset` -> `verticalContentOffsetPx` ->
  `centredSlackPx = max(0, extent - content) / 2` (`4302-4309`, `4317-4319`) **[C]**. So:
  - **View shrank** (the pane above the divider while dragging up, the pane below while dragging
    down): the grid overflows, starts at the view's top edge, and its bottom rows draw *below* the
    view [C for the offset math; the drawing outside is I, see below].
  - **View grew**: the grid is centred, leaving slack above and below (inside the view).
  The anchor is therefore the **top edge when overflowing** and the centre otherwise, not the bottom
  (the old bottom-anchor was removed; see the comment at `4288-4292`) **[C]**.
- **Nothing clips the TerminalView to its frame on the plain (non-travel) path.**
  - `TerminalView.onDraw` clips to its own bounds **only** `if (mTravelActive)` (`3705`), with the
    comment that "the pane frame around this view lets its children draw outside their bounds on
    purpose" (`3702-3704`) **[C]**. A divider drag does not start a travel (it opens a
    `Reason.DIVIDER` transition, `TerminalPaneController.java:1646`) [I: not every travel path
    traced].
  - The pane frame sets `android:clipChildren="false"` and `clipToPadding="false"`
    (`app/src/main/res/layout/view_terminal_pane.xml:8-9`), the split `LinearLayout` sets
    `setClipChildren(false)` and `setClipToPadding(false)`
    (`TerminalPaneController.java:5265-5266`), and the wall sets the same
    (`app/src/main/java/com/termux/app/wall/PaneWallLayout.java:521-522`) **[C]**. With
    `clipChildren=false`, Android does not cut a child's drawing at the child's bounds [I:
    framework behaviour].
  - `PaneContentFrame.setPaneShape(radius, clipToShape)` is the only clip on a tiled pane: it calls
    `setClipToOutline(clipToShape)` (`PaneContentFrame.java:252-258`) and the controller passes
    `clipToShape = paneGlassActive()` (`TerminalPaneController.java:5542`) **[C]**. So a tiled
    split pane is clipped to its rounded outline **only while the pane glass is on**; with glass off
    nothing clips it. A *floating* pane clips on its own wrapper (`content.setClipToOutline(true)`,
    `6909`) **[C]**, so floats are not expected to show this bug [I].
  - Result: with glass off, the hypothesis is confirmed from code: the held grid overflows the
    shrunk frame and is drawn over the neighbour pane (children later in the split
    `LinearLayout` draw over earlier ones, so the bottom pane's own cells win where both draw
    [I]). Which setting the recording was made with is not known **[U]**; with glass on, the
    outline clip should have hidden the overflow, so a recording that shows it is more likely glass
    off or a float/Home path.
- **Classic images are clipped to their own cell box only.** `KittyLayerPainter.geometry` computes
  `clip` as `columns x rows` cells from the placement's top-left
  (`terminal-view/src/main/java/com/termux/view/KittyLayerPainter.java:103-123`) and
  `CanvasTarget.drawPlacement` does `clipRect(clip)` and draws (`139-140`); nothing intersects
  that with the view or the pane **[C]**. The collector picks placements reaching into
  `rowCount` rows from `topRow` (`collect` `~52-58`, `TerminalBuffer.java:1239`) **[C]**.
- **Placeholder images** are drawn per cell: the image is fitted into the `cols x rows` box and
  clipped to that one cell (`terminal-view/src/main/java/com/termux/view/TerminalRenderer.java:2193-2263`,
  clip at `2262`) **[C]**. They too are cut only at the cell, not the pane.
- **Net**: text, classic images and placeholder images all draw wherever the held grid's rows
  land, which can be outside the pane. This also matches the recording for the top pane
  (visualizer bars and help line below its end) [I].

### 4.3 Reflow and images

- `TerminalBuffer.resize`: classic placements ride on the row their top-left cell was on; reflow
  moves them with that row and calls `fitToColumns`, which clamps the column and narrows the
  clip width to the room left to the right edge (`terminal-emulator/.../TerminalBuffer.java:455-466`,
  `KittyPlacement.java:78-82`) **[C]**. Pixel size is not changed. Placeholder cells wrap like
  text (`TerminalBuffer.java:373-575`) **[C]**.
- **Font change**: classic placements scale by `fontWidth / cellWidth` and `lineSpacing / cellHeight`
  at draw time, so they follow the cell (`KittyLayerPainter.java:105-112`) **[C]**; a
  cell-size-only change sends the in-band report so the program can resend
  (`TerminalEmulator.java:1056-1060`) **[C]**.
- The user doc says "changing a pane's size or font may make the program redraw or resend a
  picture" (`docs/en/Terminal_Kitty_Protocols.md:379-380`) **[C]**.

### 4.4 Limits and animation

- 16 MiB transmit, 32 MiB decoded (`terminal-emulator/.../KittyGraphicsProtocol.java:29-30`), store
  of 32 MiB and 256 images (`KittyImageStore.java:30-31`), 512 frames per image (`86`), plus a
  shared device-sized frame budget (`~75-84`) **[C]**. kitty/Ghostty: 320 MB each.
- Animation playback is suspended when the pane is off screen and resumes where it would have
  been (`TerminalView.java:4826-4850`) **[C]**. A held drag keeps the view shown, so animation
  keeps running and drawing outside the frame [I].
- What the client receives when a store limit is hit was not traced **[U]**.

## 5. Side by side

| Topic | kitty | Ghostty | Termux Launcher |
|---|---|---|---|
| PTY resize timing in a drag | Once, 0.1 s after the last event (mac: at end or 0.5 s pause) `child-monitor.c:1411-1445` | Every size change, coalesced to 25 ms `termio/Thread.zig:26-31` | Once, on release (hold) `TerminalPaneController.java:1639-1659`, `TerminalView.java:3161-3171` |
| Screen reflow timing | Same moment as PTY `window.py:1138-1150` | Same moment as PTY `Termio.zig:540-553` | Same moment as PTY (`updateSize`) |
| Drawn meanwhile | Blank + old grid pinned top-left + "N by M cells" `shaders.c:2870, 3408, 3469` | Old cells to the new surface size + padding; overlay popup `Config.zig:2378` | Old grid, top-anchored if it overflows, centred otherwise; no banner `TerminalView.java:4302-4319` |
| Clipped to window/split | Yes, per-window GL viewport `shaders.c:2604` | Yes, surface-sized framebuffer; renderer must scissor images `render.zig:196-221` | No, with glass off (`clipChildren=false` chain, `TerminalView.java:3705`); only the rounded outline when glass on |
| Classic placement on reflow | Row shift on vertical shrink with unchanged columns only `graphics.c:2781-2803` | Tracked pin, moves with cell `graphics_storage.zig:1966` | Rides with its row; narrowed to fit columns `TerminalBuffer.java:455-466` |
| Placeholder on reflow | Cell images rebuilt from rewrapped text `screen.c:687, 4040` | Row flag kept on rows that still hold cells `PageList.zig:19163-19283` | Wraps like text; drawn per cell `TerminalRenderer.java:2193` |
| Alt screen reflow | Not rewrapped in live resize `changelog.rst:1263` | Never `Terminal.zig:4343` | Not checked [U] (`TerminalBuffer.resize` takes `altScreen`) |
| Font change | `grman_rescale` `graphics.c:2806` | Regenerate from new `width_px/cols` `render.zig:2130` | Scale by cell ratio at draw `KittyLayerPainter.java:105` |
| Partly out of view | Row cull; viewport cuts the rest `graphics.c:1679` | Reported at full size; renderer scissors `render.zig:196` | Cut to own cells only `KittyLayerPainter.java:139` |
| Sync output (2026) on resize | Pause cancelled `screen.c:646` | Reset to false `Terminal.zig:4306` | Not checked [U] |
| Mode 2048 report | After every PTY resize `window.py:1086-1090` | Every resize, incl. cell-size-only `Termio.zig:556` | Every resize, incl. cell-size-only `TerminalEmulator.java:1050-1080` |
| Image store | 320 MB per buffer, frames 5x on disk `graphics.c:29` | 320 MB per screen `Config.zig:2522` | 32 MiB, 256 images, 512 frames `KittyImageStore.java:30-31` |
| Over quota (client sees) | ENOSPC for frames `graphics.c:2024`; evicts old images | ENOMEM / ENOSPC `graphics_exec.zig:678, 1102` | Not traced [U] |

## 6. Gaps ranked by user impact

1. **Held grid draws outside its pane (text and images) during a drag.**
   - *Evidence*: 4.2: no clip on the non-travel path; `clipChildren=false` on frame, split and wall;
     tiled panes clip to outline only with glass on; classic and placeholder images clip to their
     own cells; the held grid overflows a shrunk view downward (`centredSlackPx` floor). Matches the
     recording for the top pane.
   - *kitty/Ghostty*: both draw each terminal only inside its own rectangle.
   - *Direction*: clip the TerminalView's canvas to its bounds on every frame (keep the deliberate
     travel overdraw as an explicit exception), and intersect classic-image clips with the view
     and the viewport rows. Decide an overflow anchor for the held grid on purpose (pin to top-left
     like kitty so what the user sees is a cropped old screen, not a re-centred one).
2. **Duplicated, stacked album art around a resize.** (Not reproduced; see 7.)
   - *Evidence*: classic placements ride with rows and are narrowed, not removed, on reflow
     (`TerminalBuffer.java:455-466`); programs that repaint on SIGWINCH put a new picture
     while the carried one stays; the spec says erase commands do not delete graphics
     (`docs/graphics-protocol.rst:1328-1330`) so the client has to send a delete.
   - *kitty/Ghostty*: both also keep the old classic placement unless the client deletes it;
     the difference is that Ghostty's placement is tied to a pin (so the stale one at least moves
     with its text) and kitty drops all cell images and rebuilds them.
   - *Direction*: reproduce first (7). If confirmed, consider dropping classic placements on an
     alternate-screen resize (the alternate screen keeps no history in any of the three) and
     checking that a program's `a=d` after resize really deletes the carried one.
3. **No cue and no settle policy during a drag.**
   - *Evidence*: `GridHold` holds until release, with no reflow preview and no size indicator;
     kitty reflows when the drag pauses (0.1 s) and shows "N by M cells"; Ghostty shows a
     `resize-overlay`.
   - *Direction*: a size popup during the hold, and/or one early resize after a short pause
     (kitty's `on_pause` idea) so a long drag still shows the real reflow. Keep it opt-in if it
     costs a SIGWINCH storm on slow programs.
4. **Classic image cut or left behind when columns change.**
   - *Evidence*: `fitToColumns` narrows the clip to the room at the right edge; the picture is not
     rescaled (`KittyPlacement.java:78-82`). Ghostty clamps the rect's right edge to the last
     column too (`graphics_storage.zig:2285-2318`); kitty leaves it unchanged (`graphics.c:2781`).
   - *Direction*: parity is acceptable; the user-visible fix is mostly clipping (gap 1) and
     programs resending after the in-band report. Note in the user doc that programs should
     listen for mode 2048.
5. **Image store is 10x smaller than kitty/Ghostty's.**
   - *Evidence*: 32 MiB and 256 images vs 320 MB (`KittyImageStore.java:30-31`). A few album
     arts plus a logo are fine; a full-screen 4K image or an animation is not.
   - *Direction*: keep, since phone RAM is the constraint, but make what the client sees at the
     limit match the spec (`ENOSPC`) and check eviction order (kitty: unreferenced first, then oldest).
     What it does today is not traced.
6. **Synchronized output across a resize** may keep a frame frozen (not checked).
   - *Evidence*: both references cancel it on resize (`screen.c:646`, `Terminal.zig:4306`).
   - *Direction*: check the emulator's mode 2026 handling on `resize()`; make a resize end a pause.

## 7. Open questions and what needs a device reproduction

1. **Bottom pane's logo about 75 px above its own top edge (y 440 vs 515).** The offset math cannot
   put the grid above the view: `getVerticalContentOffset` is never negative
   (`TerminalView.java:4317-4319`) **[C]**. Candidates, all [I]: (a) the recording's two readings are from different frames
   (the divider moved between them) and the logo belongs to the pane's previous position;
   (b) a smooth-scroll or travel offset (`currentDrawOffset` subtracts `mScrollOffsetPixels`,
   `4258-4262`) shifted the grid up; (c) a composited overlay (frost/reflow snapshot, motion
   overlay) drawing a second copy at old coordinates, not read. Reproduce: Pong, stacked split with
   fastfetch below, drag slowly; capture a screenshot per step with *Show layout bounds*
   (`adb shell setprop debug.layout true`) and the `GEOMETRY_LOG_TAG` lines
   (`TerminalView.java`, `logSessionResize`) to read the view bounds and when each pane resizes.
2. **Duplicate art cause.** Reproduce with the music player alone in a split (alt screen?) and
   with fastfetch alone, drag, release, and note: does the duplicate appear mid-drag (then
   something resizes before release: check whether `releaseDividerHold` or the 500 ms backstop
   fired early, `GeometryScheduler.java:144`) or only after release (then it is carried
   placement plus re-sent placement)? Capture the app's APC output (`script`/`strace` on the PTY)
   to see whether it sends `a=d` before the new picture.
3. **Was glass on in the recording?** With glass on the outline clip should have hidden the
   overflow (4.2); if the recording was glass on, the clip is not reaching the TerminalView and
   there is a second cause.
4. **Alternate-screen resize and synchronized output** in `TerminalEmulator`/`TerminalBuffer`
   were not read; check both against the rows above.
5. **kitty: mouse-drag split resize** and **Ghostty: GTK/macOS divider drag** were not read, so
   "per-split debounce" claims are limited to the shared size-change path.
6. **Quota error path** in `KittyImageStore`/`KittyGraphicsProtocol` (what the client receives).
7. **Animation during a held drag**: confirm on device that a playing animation keeps drawing
   outside the frame; the code shows it keeps ticking while the view is shown.
