# libghostty-vt core, wave 3: one content interface, no history copy (2026-11-09)

Decision: ADR 0025. Research: `project-docs/reference/terminal/libghostty-vt-research-2026-11-07.md`
and `gpu-text-rendering-android-research-2026-11-09.md` (main checkout, untracked). Ghostty headers
and guides at `vt-1.0.0`: `include/ghostty/vt/*.h`, `dist/doxygen/pages/guides/*.md`.
Base: `exp/ghostty-core` at `ab9c067fc`. Experiment only: nothing here reaches a drop.

Paths: `te/` = `terminal-emulator/src/main/java/com/termux/terminal/`, `tv/` =
`terminal-view/src/main/java/com/termux/view/`, `app/` = `app/src/main/java/com/termux/app/`,
`jni` = `terminal-emulator/ghostty/ghostty_jni.c`. Line numbers verified at `ab9c067fc`.

## Baseline (pong, Nothing Phone 2, Android 16, 120 Hz: 8.3 ms budget)

| What | Java core | Ghostty wave 2 |
|---|---|---|
| Parse only, 13 MB / 200k lines at 52x45 | 10–12 MB/s | write only 178–184 MB/s, 0 Java alloc; with copy 59–65 MB/s |
| Copy share of Ghostty parse | — | 129–146 ms of 203–223 ms; worst turn 7–20 ms (20 turns) |
| Ghostty memory, 200k lines | — | 1.59 MB; 0.44 MB after compress (compression works on pong) |
| On-screen `cat`, split pane | 4.8–5.1 s, 6.4–6.7 CPU-s | 0.94–1.02 s, 1.4–1.5 CPU-s |
| PSS growth during the flood | +36–43 MB | about +15 MB |
| Flood, main-thread start delay p90 | 8.4 ms | 35.8 ms (the copy) |
| Full-screen redraw (`tui`), total p50 | 18.2 ms | 17.8 ms; both UI p90 ~12 ms (row recording), RenderThread p90 11–15 ms |
| btop, frames over budget | 50 | 7 |
| Typing while lines stream | equal | equal |
| Kitty animation | RenderThread-bound | not run (images off in Ghostty mode) |

Sources: `~/reports/ghostty-core-2026-11-09/wave2/results.tsv`, `frames-window/*.summary`,
`frames/*.summary`.

## Premise check

1. **"Remove the copy" is two thirds right.** The copy has two parts. The screen part rewrites at
   most `mRows` rows per turn (`GhosttyMirror.applyScreen`, `te/GhosttyMirror.java:76-85`). The
   history part rewrites every row that scrolled off since the last turn, up to the whole
   transcript (`scrollIntoTranscript`, `:95-112`, capped at `getActiveTranscriptRows()` on `:102`).
   In the bench each 4 ms turn scrolled about 10,000 lines, so each turn copied about 1,955
   history rows (`transcript_rows_used`) and 45 screen rows: about 98% of the copied rows are
   history (reasoned; slice 0 measures it). The screen part cannot go: `RowRenderCache` compares
   `TerminalRow` values (`tv/RowRenderCache.java:255-291`, `captureText` `:465-492`) and the
   renderer draws `TerminalRow.mText` (`tv/TerminalRenderer.java:1165`, `:1182`, `:2962`). A Java
   renderer needs Java rows. **The goal becomes: no history copy, and a screen copy of dirty
   rows only.**
2. **"Keep the copy, move it off-thread" is not cheaper.** Every reader below reads
   `TerminalBuffer` on the main thread without a lock, so a background copy needs a second full
   buffer or locks in 21 classes. Rejected (ADR 0025).
3. **Parsing off the UI thread is not justified by today's numbers.** With history gone, a turn is
   `vt_write` for at most `DRAIN_BUDGET_NANOS` = 4 ms (`te/TerminalSession.java:647`) plus a 45-row
   copy. Parse is 73 ms of the 0.94 s flood; the start delay was the copy (worst copy turn 7–20 ms
   against a 4 ms parse budget). Designed below (§5), built last, behind a gate.
4. **A single interface for modes and cursor is not needed.** Both cores already serve
   `isMouseTrackingActive`, `isCursorKeysApplicationMode`, `getCursorRow` and the rest as plain
   fields: the Ghostty core sets them once per sync (`applyGhosttyState`,
   `te/TerminalEmulator.java:1612-1652`; `applyGhosttyModes` `:1654-1668`). The interface covers rows, text and scroll position only.
5. **Ghostty's dirty rows will not cut row recording.** Full-screen redraws are draw-bound and
   equal on both cores (UI p90 ~12 ms). Ghostty's flags are conservative ("every row is currently
   marked dirty after the viewport scrolls", `guides/rendering.md`, Row identity), so they cannot
   replace the exact compare. They save the compare, not the recording (§6).
6. **Wave 2 has a scroll bug this design fixes.** The mirror scrolls the Java buffer through
   `TerminalBuffer.scrollDownOneLine` (`te/GhosttyMirror.java:101`), not the emulator's, so
   `mScrollCounter` (`te/TerminalEmulator.java:793`, bumped only at `:4723`) stays 0. A scrolled-back
   view or a selection does not stay pinned while output arrives (`tv/TerminalView.java:1076-1090`).
   Reasoned from code; not seen on a device.
7. **Ghostty mode today has no kitty images, links, prompt marks, progress or notifications.**
   Every byte goes only to Ghostty (`te/TerminalEmulator.java:1455-1458`), image storage is set
   to 0 (`jni:302-304`), and mirrored cells carry hyperlink id 0 (`te/GhosttyMirror.java:59-60`).
   §7–8 bring them back from Ghostty's own data.
8. **"Durable workspaces" do not read transcripts.** `TerminalWorkspace` and the capture at
   `app/TermuxActivity.java:24140` store topology only (grep: no emulator reads). No change.

## 1. The interface

```java
/** What a pane shows and holds, row by row. Main thread only. */
public interface TerminalContent {
    int columns();
    int screenRows();
    int historyRows();                 // rows above the screen readable now
    long firstRowIndex();              // absolute index of the oldest readable row
    int takeScrolledRows();            // rows that left the screen since the last call
    TerminalRow row(int externalRow);  // -historyRows() .. screenRows()-1; read-only, reused
    String text(int x1, int y1, int x2, int y2, boolean joinWrapped, boolean trim);
    @Nullable TerminalBuffer.TextBlock textBlockAt(int column, int externalRow); // null: none
}
```

- `TerminalEmulator.getContent()` returns `mScreen` (a `TerminalBuffer`, which implements it with
  its existing methods) or the `GhosttyContent`. `getScreen()` stays for the Java parser's own use
  and for the few Java-only features (sixel bitmaps).
- `row()` returns a row the caller must not keep across a frame or a turn: the Ghostty core
  reuses row objects. `RowRenderCache` already copies what it compares (`:465-592`), so this holds.
- Absolute row index: `firstRowIndex() + historyRows() + externalRow`. The Java core derives it
  from `mScrollEventCount` (`te/TerminalEmulator.java:801`); Ghostty from its scrolled count. It
  is the stable row identity for find matches, URL caches and selection anchors (§4).
- `takeScrolledRows()` replaces `getScrollCounter()`/`clearScrollCounter()`
  (`te/TerminalEmulator.java:5173-5178`) for the view; fixes premise 6.

## 2. Reader inventory

21 classes outside `terminal-emulator` read terminal state (grep for `getEmulator()`,
`TerminalBuffer`, `TerminalRow`, `getScreen()`; the scout's ~45 included tests and writers).
"Emulator" means the existing `TerminalEmulator` getter, served by both cores unchanged.

| Reader | Calls (path:line) | Served by |
|---|---|---|
| Renderer | `tv/TerminalRenderer.java:1123` `getScreen`; `:1165,1182,1305,1323,2956` `allocateFullLineIfNecessary(externalToInternalRow)`; `:1140,1818,2997,3470` `getTextBlockAt`; `:1849` `getTextBlockText` | `row()`, `textBlockAt()` |
| Renderer, cursor and modes | `tv/TerminalRenderer.java:1116-1125` `isBoldWithBright`, `isReverseVideo`, `mRows`, `mColumns`, `getCursorCol/Row`, `shouldCursorBeVisible`, `getCursorStyle`, `mColors` | emulator (snapshot fields, §3) |
| Renderer, images | `tv/TerminalRenderer.java:1275` `getKittyPlacementGeneration`; `:2213,2219` placeholder and generation; `:2229-2236` `getSixelBitmap/Rect`; `:2948-2952` extra cursors; `tv/KittyLayerPainter.java:53` `collectKittyPlacements` | emulator image API (§7); sixel and extra cursors Java only |
| `RowRenderCache` | `tv/RowRenderCache.java:281,472,480,489,501,518,528,546-567,577,586` row fields and getters | `row()` values, plus the row stamp (§6) |
| `TerminalView` | `tv/TerminalView.java:1052,1062` `getActiveRows`; `:1072-1095` transcript rows and scroll counter; `:1217` `getChar`; `:1539-1556` row text; `:4344` `getSelectedText`; `:4505-4545` `getActiveTranscriptRows`, `findPromptRow` | `historyRows()`, `takeScrolledRows()`, `row()`, `text()`, prompt rows (§8) |
| `TerminalView`, modes | `:495-2638` `isMouseTrackingActive` (13 sites); `:1563,2008,3398,3553` `isAlternateBufferActive`; `:3148` `getKeyboardFlags`; `:3832` `getCursorPositionChangedAtMillis` | emulator |
| `TextBlockGeometry`, `TextBlockSelection` | `tv/TextBlockGeometry.java:61-63`; `tv/textselection/TextBlockSelection.java:39-40` | `row()`, `textBlockAt()` (null in Ghostty mode) |
| `UrlUnderlines` | `tv/UrlUnderlines.java:107-118` transcript rows, `mText`, `getLineWrap` | `historyRows()`, `row()` |
| `TextSelectionCursorController` | `tv/textselection/TextSelectionCursorController.java:101-160` rows and `mText`; `:215,345` `getActiveRows`; `:418,484` `getSelectedText`; `:363,396` alt screen | `row()`, `text()`; emulator |
| `TerminalPaneController` (cursor trail) | `app/terminal/TerminalPaneController.java:600-663` cursor, `copyCursorBlock(getScreen())`, `isCursorEnabled`, changed-at time; `:4331` mouse; `:4446` cursor row | emulator; `copyCursorBlock` takes `TerminalContent` |
| `TerminalFindCoordinator` | `app/terminal/find/TerminalFindCoordinator.java:282-288` rows and per-row `getSelectedText` | `historyRows()`, `text()` per row |
| `TerminalScrollbackSearchOverlay` | `app/terminal/TerminalScrollbackSearchOverlay.java:179-185` same pattern | same |
| `TermuxTerminalViewClient` | `app/terminal/TermuxTerminalViewClient.java:1430-1432` `UrlDetector.find(screen, -transcript, rows)`; `:1333,1472,1495` transcript via `ShellUtils` | `UrlDetector` over `row()`; `text()` |
| `ShellUtils` | `termux-shared/src/main/java/com/termux/shared/shell/ShellUtils.java:62-69` `getTranscriptText*` | `text(0, -historyRows(), …, joinWrapped)` |
| `TerminalActionDispatcher` | `app/terminal/TerminalActionDispatcher.java:983` whole transcript for the last N lines; `:792,1975` size; `:1060,1213` progress, shell integration | `text()` over the last N rows only; emulator |
| Setup and benchmarks | `app/setup/SetupShellRun.java:175`, `app/setup/DisplayCardPage.java:273`, `app/x11/X11GpuBenchmarkRun.java:247` `getTranscriptText` | `text()` |
| `TermuxActivity` | `app/TermuxActivity.java:33936,33962` `getSelectedText` over the screen; `:33953,34013-34014` exit code, shell integration; `:34135` alt screen; `:867,873` bracketed paste; `:36507` pointer shape | `text()`; emulator (§8) |
| Input encoders | `app/terminal/TerminalBindingKeyEncoder.java:18-19`; `app/terminal/TerminalClipboard.java:120,165` paste events | emulator |
| `PageOverviewView` | `app/wall/PageOverviewView.java:1433-1437` colours, columns | emulator |
| In-module helpers | `te/UrlDetector.java`, `te/TerminalLinks.java:35` read `TerminalBuffer` rows | move to `TerminalContent` |

Writers, not readers, keep their calls: `ShellSignals.progress` (`app/terminal/ShellSignals.java:132`),
colour resets in `TermuxTerminalSessionActivityClient` (`:1434-1480`), mouse and touch reports.

## 3. The Ghostty content and its snapshot

`GhosttyContent` owns, per pane: `screen` (`TerminalRow[mRows]`, reused), `history` (§4), and the
snapshot scalars (columns, rows, cursor, modes, history count, scrolled rows, hold state). One
sync fills all of them from one `ghostty_render_state_update`:

1. Scalars as today (`jni:487-530`), plus `GHOSTTY_TERMINAL_DATA_SCROLLBAR` for the history count,
   read in the same call (guides/rendering.md, Overscan).
2. Dirty rows only into `screen` (as `copy_render_row`, `jni:409-446`), with the hyperlink id per
   cell (§8). Each rewritten row gets `stamp = ++counter` (§6).
3. Scrolled rows: from the tracked top pin (`jni:216-219`, `:465-480`), as wave 2 counts them.
   They go to `takeScrolledRows()` and move the history window's base; nothing is copied.

**One owner per two-phase number** (motion-invariants §1):

| Number | Predicting side | Settling side | Owner |
|---|---|---|---|
| Cursor position (trail, IME, a11y, renderer) | trail target at `TerminalPaneController.java:600-607` | renderer cursor at `TerminalRenderer.java:1120-1121` | snapshot cursor; `mCursorPositionChangedAtMillis` set when the snapshot's cursor moves (`TerminalEmulator.java:1469-1479`) |
| Grid size during resize | view prediction `TerminalView.predictRowsOnlyResizeShift` (`:3466-3474`) | `TerminalSession.updateSize` → `resize` (`TerminalSession.java:161-174`, `TerminalEmulator.java:1051-1090`) | snapshot `columns()/screenRows()`, taken from the same update as its rows; the renderer reads them, not `mColumns` separately |
| Scrolled rows / view top | `TerminalView.onScreenUpdated` (`:1069-1095`) | history window base | snapshot `takeScrolledRows()`; `mTopRow` is the only viewport; `ghostty_terminal_scroll_viewport` is never called |

Parity tests: `GhosttyContentTest` asserts, after a sync, snapshot cursor = `CURSOR_X/Y`, snapshot
size = `ghostty_terminal_get(COLS/ROWS)` = emulator `mColumns/mRows`, and that `takeScrolledRows()`
equals rows written past the bottom, including across an alt-screen switch and a prune.

**Holds (mode 2026).** Use the `RENDER_HOLD` callback (guides/rendering.md, Render holds): on
hold start, update the render state inside the callback; skip updates while held; end the hold
ourselves after `SYNCHRONIZED_UPDATE_TIMEOUT_MILLIS` = 150 ms (`TerminalEmulator.java:372`) by
clearing the mode. Replaces the mode poll in `applyGhosttyModes` (`:1654-1668`), which can miss a
release-and-restart between two syncs.

## 4. Scrollback on demand

- **Reading a row.** `row(y)` for `y < 0` reads history row `historyRows + y` with an untracked
  grid ref, as `nativeReadHistoryRow` does now (`jni:564-617`), into a reused `TerminalRow`.
  Cost: about 3.5 µs per 52-column row (reasoned: the bench copied about 39,000 rows in 129–146
  ms). A 45-row scrolled-back screen is about 0.2 ms.
- **History window.** A ring of `2 x screenRows` reused rows keyed by absolute index. Scrolling
  by k rows reads only the k new rows. Drawn rows keep their objects across frames, so
  `RowRenderCache`'s `state.line != line` test (`RowRenderCache.java:259`) and its shift path still work.
- **Identity.** Absolute index (§1). Prune moves `firstRowIndex()` up; an index below it is gone
  and readers drop it (find match, URL cache entry). A resize that reflows, a reset, or ED 3 bumps
  a `contentEpoch`; the window clears, find re-runs, and the selection ends, which is what a Java
  reflow already does to rows (`TerminalBuffer.resize`). Ghostty's tracked refs follow a reflow, so
  a selection could survive one later (open question 4); not in this wave.
- **History limit.** `SCROLLBACK_MAX_LINES` = the user's transcript rows, not
  `historyLimit` (`te/GhosttyCore.java:67-69`, transcript plus one page): with no Java copy there
  is nothing to keep a margin for. Byte limit: `transcriptRows x (8 + 8 x columns)` plus one page,
  so a styled flood cannot hold more than the Java core would.
- **Text and find.** `text()` uses `ghostty_formatter_format` (plain, selection range, trim) for
  ranges over 64 rows, and the row walk for short ones. Find keeps its per-row loop through
  `text()` in this wave; Ghostty's search API (tick off the lock, feed under it) is the later move
  if find over 2000 rows measures above a frame.
- **URL detection.** `UrlDetector` walks `row()`. Unchanged code; history rows cost reads.

## 5. Threading

**Slices 1–6: everything on the main thread,** as now: `vt_write`, sync and reads in one place, no
lock. Callbacks fire inside `vt_write` and only record (`jni:128-182`); Java drains them after
the sync (`TerminalEmulator.java:1645-1651`), never from inside a write.

**Slice 7, if built: parse on the pty reader thread.**

- One native mutex per terminal (in `Core`, not a Java monitor). The reader thread
  (`TerminalSession.java:203-220`) takes it per `vt_write` of at most 16 KiB (about 0.1 ms at
  180 MB/s), so the main thread never waits longer than one chunk.
- A second mutex guards the render state. Lock order is terminal, then render state, on both
  threads.
- Main thread, once per frame from `onDraw`'s pre-draw (not per turn): lock terminal → lock
  render → `begin_update` and the scalars → unlock terminal → `end_update` → copy dirty rows →
  unlock render. History reads (`row(y<0)`, `text()`) take the terminal lock per batch of rows.
- Holds: the `RENDER_HOLD` callback runs on the reader thread, already holding the terminal
  lock; it takes the render lock and calls `begin_update`. It waits at most one main-thread
  row copy.
- Callbacks: `WRITE_PTY` replies collect in a native buffer and go to `mTerminalToProcessIOQueue`
  from the reader thread after `vt_write` returns (not inside the callback, which must not block).
  Title, pwd, bell, clipboard, prompt, progress and notifications are flags or queues drained by
  the main thread at the next sync, as today.
- Resize: `updateSize` on the main thread takes the terminal lock around `ghostty_terminal_resize`;
  the next sync's snapshot carries the new size with its rows.
- Fail-safe: the frame never blocks on the terminal lock. `tryLock`; on failure draw the last
  snapshot and retry next frame; after 3 failed frames take the lock blocking.
- Gate: build only if, after slice 3, `frames flood ghostty` start delay p90 > 8.3 ms or
  `frames type ghostty` is worse than Java.

## 6. Draw cost

- Row recording is unchanged: rows stay `TerminalRow`, `drawRowGlyphs` is untouched.
- **Row stamp fast path.** `TerminalRow` gets `long stamp` (0 = unknown). `GhosttyContent` sets
  it when it rewrites a row; the Java core leaves it 0 (its parser hot path is not touched).
  `RowRenderCache.rowChanged` (`:255-291`) returns "unchanged" without the captures when the row
  object, its non-zero stamp, cursor, selection and URL key all match the last frame. Rows Ghostty
  marked dirty still get the exact compare, so a rewritten-but-equal row is not re-recorded.
- Ghostty row ids are not used: they disappear when a row changes in place and we already key
  rows by absolute index.
- **GPU seam.** `TerminalContent` plus the native render state is the input a GPU renderer would
  take; it can read the render state natively and skip Java rows. Out of scope here.

## 7. Kitty graphics on the Ghostty core

- **Advertising.** DA1 keeps no `4` on the Ghostty core (no sixel); `TERM_PROGRAM` stays `termux-launcher`. We never claim to be Ghostty or kitty.

- **Storage.** `KITTY_IMAGE_STORAGE_LIMIT` = `KittyImageStore.MAX_STORED_BYTES` (32 MiB,
  `te/KittyImageStore.java:33`) per screen, replacing 0 (`jni:302-304`). Media: direct only, as
  today. PNG: a decoder installed once with `ghostty_sys_set(GHOSTTY_SYS_OPT_DECODE_PNG)` before
  any session thread uses the library (process-global, not thread-safe).
- **Bitmaps we keep.** `GhosttyImageCache`: `Bitmap`s keyed by (image id, generation), only for
  images with a placement on screen, charged to `KittyImageStore.FrameBudget` (`:75-88`) and
  dropped when off screen, on pane close and on `onTrimMemory`. Pixels are copied out of Ghostty
  while the terminal is not being written (borrowed until the next write or tick,
  guides/images.md, Lifetime). Peak cost: Ghostty's store plus on-screen bitmaps; a budget test
  asserts both constants.
- **Placements.** Read the render-state image iterator only when
  `IMAGE_PLACEMENTS_GENERATION` moves. Layer (below backgrounds, below text, above text) maps to
  the existing passes (`TerminalRenderer.java:1171` under text, `:1191` over text; a layer under
  the cell backgrounds is new);
  sort by z inside a layer, since Ghostty does not return draw order.
- **Unicode placeholders.** Ghostty turns placeholder runs into one placement per row
  (guides/images.md, Placeholders). They draw in the row's image node, so `rowImagesMoved`
  (`RowRenderCache.java`, class comment `:40-44`) keys on the Ghostty image generation.
- **Animation.** `ghostty_kitty_graphics_animation_tick` once per drawn frame while a visible pane
  holds images; it returns the next due time, and we post one redraw then, no sooner than 8 ms.
  `NO_VALUE` schedules nothing. A pane that is not visible (`setKittyAnimationsVisible`,
  `TerminalView.java:4982-4991`) does not tick, so nothing repaints at rest.

**Fork-only features in Ghostty mode:**

| Feature | Ghostty at `vt-1.0.0` | Ghostty mode |
|---|---|---|
| Cursor trail | n/a | keep; reads the snapshot cursor |
| Sixel | DCS ignored | drop (later: sniff DCS with `vt_write_until`, decode with `TerminalSixel`, re-send as a kitty image at ground) |
| iTerm2 images (OSC 1337 File) | parsed, dropped | drop (same later route) |
| OSC 66 text sizing | parsed, dropped | drop: it changes cell layout and cannot live outside the grid |
| OSC 99 notifications | parsed, dropped; OSC 9/777 reach `DESKTOP_NOTIFICATION` | keep: sniff OSC strings with `vt_write_until`, feed `KittyNotifications` on the main thread; map OSC 9/777 to the same path |
| OSC 8 links | per cell, `ghostty_grid_ref_hyperlink_uri` | map: URI table per pane, id per cell in `TerminalRow` |
| Extra cursors | none | drop |

## 8. Shell integration and the rest

- OSC 133: the `SEMANTIC_PROMPT` callback (exit code on `COMMAND_END`) feeds
  `hasShellIntegration`, `isShellIntegrationCommandRunning`, `getLastCommandExitCode`
  (`TerminalEmulator.java:4581-4654`). `findPromptRow` (`:4662`) reads
  `GHOSTTY_ROW_DATA_SEMANTIC_PROMPT` per row through `row()`'s read path.
- OSC 9;4 progress: `PROGRESS_REPORT` callback into `setProgress` (`:4619`).
- Pointer shape: `GHOSTTY_TERMINAL_DATA_MOUSE_SHAPE` at sync into `getPointerShape` (`:4455`).

## 9. Fail-safes

- **Per-session fallback by replay.** On any Ghostty error (a non-success result, a Java exception
  in the sync), format the screen and the last `screenRows x 4` history rows as VT with
  `ghostty_formatter_format`, free the core, and `append` the bytes to a fresh Java emulator
  of the same size. Images are lost; text, colours and cursor survive. If the formatter fails too,
  start the Java core empty with one notice line. Replaces `ghosttyFailed`
  (`TerminalEmulator.java:1606-1609`), which relied on the copy.
- **Native crash.** A fault in the library kills the home screen; nothing per-session can catch
  it. Before the first `vt_write` of a process, write a marker; clear it on clean pane close. On
  start, a marker left by a process that died (API 30+: `ApplicationExitInfo`
  `REASON_CRASH_NATIVE`; below 30: the marker alone) turns the core off for new panes and logs why.
- **Memory.** Per pane: the byte limit (§4), kitty storage (§7). Compress when output has been
  idle 250 ms (one posted callback per idle edge, not a poller; incremental mode); full compress of
  every Ghostty pane on `onTrimMemory` `TRIM_MEMORY_BACKGROUND` and up, plus dropping
  `GhosttyImageCache`. armeabi-v7a has no compression (32-bit); its limits stay as they are.
- **At rest nothing draws.** No poller: syncs follow output, compress follows an idle edge, the
  animation tick follows a due time.

## Slices

Each ships behind `debug.tl.terminal_core=ghostty`; the Java core's numbers must not move.

| # | Delivers | Proves it |
|---|---|---|
| 0 | Split `Terminal.mirror` into `.screen` and `.history` trace sections; bench fields `mirror_screen_ms`, `mirror_history_ms` | `TerminalParseBenchTest`; `ghostty-core-bench.sh pong parse` shows history ≥ 90% of the copy (else revisit premise 1) |
| 1 | `TerminalContent`; `TerminalBuffer` implements it; every reader in §2 moves to `getContent()`; both cores still copy | `./gradlew testDebugUnitTest` (terminal-emulator, terminal-view, app); `smoke java`, `smoke ghostty`; `frames tui java` within noise of the baseline |
| 2 | `GhosttyContent`: history on demand (§4), `takeScrolledRows`, `text()` via formatter; the history copy is deleted | `GhosttyContentTest` (prune, alt screen, resize, parity cases of §3); `parse`: mirror at or under 10 ms over 20 turns; `bench ghostty`; `frames flood ghostty` start delay p90 under 8.3 ms |
| 3 | Snapshot holds via `RENDER_HOLD`; row stamp fast path (§6) | `RowRenderCacheTest` stamp cases; `frames tui`, `frames btop`, `frames type` for both cores, Ghostty no worse than wave 2 |
| 4 | OSC 8, OSC 133, progress, pointer shape, OSC 99 sniffer (§7–8) | unit tests feeding each sequence to both cores and comparing getters; `look ghostty` against `look java` |
| 5 | Kitty graphics on Ghostty (§7) | `GhosttyImageCacheTest` budget; `KittyImageStoreTest` unchanged; `frames kitty ghostty` (enable the workload for Ghostty) against `frames kitty java` Once images work, XTVERSION gains ` (libghostty-vt 1.0)` so programs matching "ghostty" pick kitty graphics; before that it must not contain "ghostty". |
| 6 | Fail-safes (§9) | fallback unit test (inject a failure mid-stream, compare the Java screen); `parse` memory fields after idle compress; `bench` PSS after-flood and after-close |
| 7 | Reader-thread parse (§5), only if its gate fires | lock-order tests with a fake core on the JVM; `frames flood/type/tui ghostty` |

## Risks and open questions

1. **Native crash takes the home screen down.** Settle: keep the switch debug-only; run the
   library's AFL harness corpus through our shim in a host build; the auto-off of §9.
2. **History read cost per row** is reasoned (3.5 µs). Settle: slice 2 adds a `scrollback` mode
   to `TerminalParseBench` (read 2000 history rows) and runs it in `parse`.
3. **PNG decoding on minSdk 26.** `AImageDecoder` is API 30. Settle: check whether the Wuffs code
   linked into the library can be reached; else a JNI call to `BitmapFactory` from the decode
   callback, measured for how long it stalls `vt_write`.
4. **Selection across a reflow.** Does the Java core keep or end a selection on resize today?
   Settle: read `TextSelectionCursorController` and add a test before slice 2; match it.
5. **Twice the pixels for drawn images** (Ghostty's RGBA plus our bitmap). Settle: measure PSS with
   `frames kitty` in slice 5; if it matters, lower Ghostty's storage limit.
6. **Formatter replay fidelity** (wide characters, wrapped lines, colours). Settle: slice 6
   round-trip test against the Java screen for the bench transcript and `test-terminal-protocols.sh`.
7. **Hold timeout.** Ghostty's guide suggests 1 s, ours is 150 ms. Settle: keep 150 ms (current
   behaviour) unless `tui` shows torn frames.
8. **Find over a long transcript** through per-row reads may exceed a frame. Settle: time it in
   slice 2; move to Ghostty's search API if it does.

## Out of scope

A GPU renderer; making Ghostty the default or shipping it in a drop; sixel, iTerm2 images and
text sizing in Ghostty mode; snapshots (`snapshot.h`) for durable workspaces; live reflow (ADR
0019 stands); compression on armeabi-v7a; changes to the Java parser beyond implementing
`TerminalContent`.

## Surfaces (AGENTS.md "Hit every surface")

Keyboard up and down and pane drags resize through §3's single size owner; one pane and many each
own a `GhosttyContent` and their own limits; themes reach Ghostty through `nativeSetColors`
(`jni:318-335`) as now. Dock styles, editions and the system IME are not touched.
