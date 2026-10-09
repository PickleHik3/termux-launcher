# Handoff: Home→Terminal slide cost and idle redraw (2026-09-27)

Status: not started. This is a code map and a set of hypotheses built from reading the code only.
Nothing has been measured or changed yet beyond the symptoms below.
`TA` = `app/src/main/java/com/termux/app/TermuxActivity.java`. "Home" = the wall's WIDGETS page (`wall/PaneWallPage.java:10-14`).

## Symptoms (measured on pong, Nothing Phone 2, 120 Hz)
1. Home→Terminal with the keyboard up: a **~30 ms pre-roll frame** when the drag starts.
2. The same slide: a **~51 ms settle frame**.
3. Idle Terminal redraws at **~21 fps**, which costs battery.

No earlier doc or commit records numbers 1 and 2. For 3, the clock comments record related measurements (see below).

## 1. Pre-roll frame (~30 ms)

Path: `StatusBarSwipeLayout.java:351` → `TA:17559 onWallDragBegin` → `PaneWallController.beginDrag` (:225) → `PaneWallLayout.beginDrag/dragTo` (:297/:303) → `applyPagePositions` (:418-445) → the `onWallOffsetChanged` listener (TA:15827-15841).

In the first frame with `keyboardReveal > 0`:
- `syncChromeTravel` (TA:15424-15447) → `PlaceChromeTravel.needsKeyboardPreRoll` (`place/PlaceChromeTravel.java:178`) → `preRollTravelKeyboard` (TA:15505-15522).
- That runs `mInAppKeyboard.show(TERMINAL_TAP)` (`TermuxInAppKeyboard.java:405`), which does:
  - `suppressSystemIme` (:1036): insets plus IMM work;
  - `showInternal` (:1187): `ensureKeyboardView` (inflates `Keyboard2View` on the first show only), `refreshMaterialPaletteIfSignatureMoved`, container VISIBLE, `requestAccessoryGeometrySync`, `recheckLayout`;
  - then a full measure and layout of the accessory stack at keyboard height. `PlaceChromeTravel.heldOverlapPx` (TA:11568) holds the terminal size.
- The same frame also carries:
  - `noteTerminalPlaceMayBeVisible()` → `TermuxTerminalSessionActivityClient.onTerminalPlaceMayBeVisible` (~:245). This draws **every deferred pane synchronously**, on purpose, "before the first frame of the slide".
  - the Terminal page's first alpha 0→1 frame, which repaints all panes and their glass.
  - `syncWallpaperParallax` (TA:3055-3089), which invalidates the backdrop, the pane glass (twice), the page `onWallMoved`, and up to 7 frost backdrops.
  - `preRollTravelDock` (TA:15525) → `syncPlaceLayout()`, only when minimal mode had put dock rows away.

Ideas to try:
- Keep the keyboard laid out, off-screen or INVISIBLE rather than GONE, so the pre-roll only toggles visibility and translation.
- Spread the synchronous redraw of deferred panes over the first frames, or start it at `beginDrag` a frame ahead.
- Drop the duplicate `invalidatePaneGlassPositions()`.

## 2. Settle frame (~51 ms)

Path: `PaneWallLayout.settleImmediately` (:378) → `TA:15805 onWallPageSettled` → `settlePlaceChrome` (TA:15589-15640). The whole settle runs in one pass on purpose, per ADR 0003 (`docs/adr/0003-...`).

What is in that one frame:
- `syncPlaceState` → `syncWallKeyboard` / `applyPlaceKeyboard` (a no-op, since the keyboard is already up), `syncDisplayTouchpad`, `applyPlaceSystemImeOwner` and `applyExtraKeysPlaceEligibility`.
- `mChrome.requestSync(SCOPE_APPLY_THIS_FRAME)`, plus **`SCOPE_ACCESSORY_RENDER|SCOPE_KEYBOARD_BACKDROP`** because `KeyboardOverlayPolicy.overlays()` differs: Home has an opaque fill, Terminal has glass.
- `applyAccessoryGeometryIfNeeded(true, "wall:settle")` (TA:8140, traced as "Accessory.geometry") → `setTerminalToolbarHeight`. This is **the terminal resize**:
  - `TerminalView.onSizeChanged/updateSize` (`terminal-view/.../TerminalView.java:2519-2555`);
  - → `TerminalSession.updateSize` (`terminal-emulator/.../TerminalSession.java:154-163`): the PTY ioctl (SIGWINCH), `emulator.resize` reflow, then a full render. The input-latency study measured a full render at ~45-75 ms.
- A second `requestSync(SCOPE_ACCESSORY_RENDER)` (TA:~15638), which re-cuts the frost crops.
- `WidgetPaneController.onWallPageShown(false)` (`launcher/widget/WidgetPaneController.java:139`) → a `render()` when the widget page index is not 0.
- `setWallLastPage`, a SharedPreferences write. Check that it uses `apply()`, not `commit()`.

Ideas to try:
- Commit the reservation, and so the resize, during the last frames of the slide, or post it one frame after the settle.
- Skip `SCOPE_KEYBOARD_BACKDROP` when the rendered material doesn't actually change.
- Merge the two `ACCESSORY_RENDER` syncs.
- Defer the widget `render()`.

Caution: moving work out of the settle frame goes against ADR 0003's "one pass at rest". Keep the look identical at rest and record any change to the ADR.

## 3. Idle ~21 fps

The main suspect is the **seconds flip in `app/src/main/java/com/termux/app/terminal/TerminalClockWidget.java`**:
- A 1 Hz ticker (`mTicker` :126-133, `syncTicker` :288) restarts a `SECONDS_FLIP_DURATION_MS = 340L` (:54) animation every second.
- `onDraw` (:586-601) → `requestAnimationFrame` (:611-618) is capped at 60 Hz on faster panels (aace41f9).
- 340 ms × 60 Hz ≈ **20.4 frames a second**, which matches the measured ~21 fps. Each frame is a full-window redraw through the frosted chrome.
- The code comments already call it "the launcher's only source of idle frames" (:2034-2037) and a third of all idle frames (:605-610, measured 2026-09-17). Lazy mode (`setLazyMode` :2044, TA:18681) turns it off.

Ideas to try, cheapest first:
- Give the clock its own layer (`LAYER_TYPE_HARDWARE`), or clip its invalidation so it doesn't dirty the glass. Measure whether the frame cost falls even though the fps stays the same.
- Cut the seconds flip to a short fixed frame count, or run it at 30 Hz.
- Hide seconds, or stop flipping them, once the screen has been idle for N seconds. Keep the minute flip.
- Add an option to show no seconds at all.

Smaller contributors to rule out:
- The 1 Hz `SystemStatsController` tick (:103/152/177), which causes full-screen 33-66 ms frames. This is fix T2 in `input-latency-study.md:176-185`. Check whether it has shipped.
- Cursor blink (`TerminalView.java:3407-3445`): off by default (rate 0).
- The busy ring's 30 Hz tick (`TerminalWindowBar.java:1183`): runs only while a shell is busy. Watch for a TUI that is marked busy.
- The kitty cursor trail (`PaneMotionOverlayView.java:202-252`): stops once settled, so it isn't an idle source.
- Infinite animators to confirm are not running on Terminal:
  - `StatusBarWidgetView.java:187` (Lottie INFINITE, in the status bar, so possibly visible on Terminal)
  - `SuggestionBarView.java:3377` (shimmer)
  - `AzScrubRowView.java:835`

## Measuring
- Frame timing: `TerminalFrameMetricsMonitor` (started at TA:1887) keeps a 240-sample ring with p50/p95 and jank.
- On pong, use `dumpsys gfxinfo` or perfetto. The method is in memory `pong-access` and `input-latency-study.md:20`. Emulator gfxinfo is unreliable (AGENTS.md:234).
- Existing trace markers:
  - `ChromeRenderer.java:325/397/494` ("Chrome.requestSync", "Frost.updateTopPane", "Chrome.commit")
  - TA:8141 ("Accessory.geometry")
- **Add markers first** in `PaneWallLayout.beginDrag/dragTo/settleImmediately`, `syncChromeTravel`, `preRollTravelKeyboard`, `TermuxInAppKeyboard.show/showInternal`, `onTerminalPlaceMayBeVisible`, `settlePlaceChrome` and `TerminalClockWidget.onDraw`. Then take one perfetto trace of the slide and one of 10 s idle before changing anything.

## History
- aac35aa9 / 87a75b67 / 20e266b9: keyboard and dock travel with the slide (added the pre-roll and the single resize at settle).
- 686751a6: wall-slide jank fix.
- aace41f9: clock flip capped at 60 Hz.
- 0c72019c / b366b162: off-screen terminal repaint deferral.
- 63ed4ccf / ac27b88a / 22c7936f: lazy mode and idle.
- 466b5fb3 / 11b0573a / 206798c9: busy ring at 30 Hz, and stopped for an idle full-screen program.
