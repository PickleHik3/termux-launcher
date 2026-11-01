# Terminal resize on pause, 2026-11-01

Decision: ADR 0019. Research: `project-docs/reference/launcher/terminal-resize-and-images-research-2026-10-31.md`.
Grilled with the developer on 2026-11-01; every behaviour below was chosen, not assumed.
One wave, one pong build. Base: v2-redesign.

## What the user sees

1. **No pane draws outside its frame**, in any state: text, classic kitty images and placeholder
   images stay inside the pane they belong to.
2. **Dragging a split divider or a floating pane's corner**: while the finger moves, each pane
   shows its old grid at its old cell size, anchored at its top, cut at the frame when the pane
   shrinks, the pane background below the grid when it grows. When the finger has been still
   (inside touch slop) for **100 ms**, the panes resize: the grid reflows and the program gets
   SIGWINCH. Moving again holds again. Release resizes once more if the size changed since the
   last pause.
3. **The size read-out**: from the drag's first move past touch slop, a **read-out** on the
   **notice** names the size in cells, both panes in layout order for a divider
   (`80×12 · 80×20`, top/left first), one size for a floating pane (`80 × 24`). It shows the size
   the panes will have when the finger stops, and its words change only when a cell count does.
   It is taken down 0.75 s after release. A real notice arriving mid-drag displaces it.
4. **Keyboard rise/fall, resize keys and focus-grow** are unchanged: one resize at the end.
5. **Synchronized output**: a resize ends an open synchronized update (DECSET 2026) and draws.
6. **Images around a resize**, as kitty and Ghostty do:
   - a classic image whose pane narrows keeps its size and is cut at the pane's edge;
   - a placeholder logo wraps into strips when its pane is narrower than the logo (accepted);
   - an old classic image stays until its program deletes it; we change nothing here unless the
     reproduction below shows we keep one the program did delete.
7. **Alternate screen** (full-screen programs: kew, btop, editors): a resize keeps its rows where
   they are, top-anchored, cut or padded at the bottom and right, as kitty does; it never
   rotates or reflows them (finding 1 below).
8. **Image store full**: the store stays 32 MiB / 256 images. When a new image does not fit,
   older images that have no placement are evicted first, as the graphics spec asks; only then is
   the new one refused, with the reply a client expects (verify today's reply first).

## Out of scope

Live (Ghostty-style) reflow; a setting for the pause; changing the placeholder wrap; raising the
store size; moving the reflow off the main thread.

## Anchors (verified at 11b00a315)

| Where | What |
|---|---|
| `terminal-view/.../TerminalView.java:3161-3171` | `onSizeChanged`: paused → `mTerminalSizeUpdatePending` + `invalidate()` |
| `TerminalView.java:3583-3600` | `setTerminalSizeUpdatesPaused`, the pending update runs on resume |
| `TerminalView.java:3700-3706` | `onDraw`: `clipRect` to the view only when `mTravelActive`; comment says the frame lets children draw outside |
| `TerminalView.java:4312-4319` | `centredSlackPx`: an overflowing grid starts at the near edge |
| `app/.../terminal/TerminalPaneController.java:1639-1673` | `GridHold`, `beginHostSurfaceResize` / depth |
| `TerminalPaneController.java:5755-5776` | `setAllPaneSizeUpdatesPaused` |
| `TerminalPaneController.java:5841`, `6403-6426`, `6186`, `6229` | divider hold: field, `beginDividerDrag`, `releaseDividerHold` call sites |
| `TerminalPaneController.java:7125-7152` | floating pane: `endDrag`, `mResizeHold`, `setSizeUpdatesPaused` |
| `TerminalPaneController.java:5265`, `5538-5545`; `PaneWallLayout.java:521`; `res/layout/view_terminal_pane.xml:8-9` | `clipChildren=false` chain; outline clip only with pane glass |
| `terminal-view/.../KittyLayerPainter.java:90-141` | classic placement geometry and clip (own cells only) |
| `terminal-view/.../TerminalRenderer.java:~2236-2262` | placeholder cell draw, per-cell clip |
| `terminal-emulator/.../TerminalSession.java:159-172` | `updateSize`: PTY size + `mEmulator.resize` on the main thread, trace `Terminal.resize` |
| `terminal-emulator/.../TerminalEmulator.java:321-378`, `1050-1080` | DECSET 2026 state and timeout; `resize` |
| `terminal-emulator/.../KittyImageStore.java:30-31`, `86`; `KittyGraphicsProtocol.java:29-32` | store and transmit limits |
| `app/.../notice/AppNotice.java:157-193` | `readout(context, label, holdMs)`, `clearReadout` |

## Performance gate

- New scenario `scripts/perf-scenarios/pong-divider-drag.txt` for `scripts/perf-frametime.sh`:
  a stacked split on Terminal, the top pane holding a 4000-line scrollback; divider drags that
  move, pause (≥150 ms still), move again, and release; once with pane glass on, once off, and
  once with a kitty image on screen. The scenario format gains whatever it needs for a drag with
  pauses (e.g. a `drag` action through `input motionevent` with waypoints and waits).
- Before: three runs on the build pong has now. After: three runs on the wave's build. p50, p90
  and "Janky (missed deadline)" must not move the wrong way; nothing draws at rest.
- Record `Terminal.resize` trace times during the scenario. If p99 exceeds 11.1 ms, add a guard
  (that pane waits for release for the rest of the drag) in a follow-up.
- Both results go in `docs/agents/performance.md`, with the display rate named.
- Every pong run and the install are asked for first.

## Reproductions before the wave (emulator)

1. kew's duplicated album art: kew alone in a split, drag, release; does the duplicate appear
   mid-drag or after release, and does kew send `a=d` after its resize?
2. The image store full: what reply a client gets today, and whether unplaced images are evicted.
3. The fastfetch logo drawn above its pane's top edge (pong recording, 5.5 s), with pane glass on
   and off.

Findings (2026-11-01, emulator `emu`, debug build installed 08:24, pane glass on — it is on whenever
the launcher stands on a wallpaper, `LookGlassSpec.slabShown()`):

1. **kew's art is text, not a kitty image here.** kew drew its cover with half-block characters
   (`▀`, 24-bit colour) in every run, kitty APC count 0 in a `script` log; pong's recording shows
   the same blocky art. The duplicate is a text effect.
   **Reproduced:** after a keyboard-driven shrink of kew's pane, the pane showed its rows rotated
   (the cover's lower half above its upper half, "Repro / 2:09" above "Resize Test / Agent")
   while the emulator's own screen text was in the right order; kew's next repaint fixed it.
   kew runs on the alternate screen (`?1049h`). Cause, confirmed in code: the alternate buffer
   resizes through the same ring as the main screen with its total set to the new row count
   (`TerminalEmulator.java:1220`), and "a shrinking alternate buffer keeps its old ring but wraps
   it at the new, smaller total, so its rows do not land anywhere stable"
   (`TerminalEmulator.java:1085-1089`). kitty resizes the alternate screen without reflow, copying
   rows 0..n in place and cutting columns (`kitty/resize.c:433-463`, `screen.c:321`, at fe55cd1).
   **We differ → in the wave:** the alternate screen keeps its rows in place on resize, top-anchored,
   cut or padded at the bottom and right, never rotated or reflowed. Resize-on-pause would show the
   rotation at every pause otherwise.
2. **Image store full: we refuse, the spec evicts.** Twelve unplaced 1024×1024 RGBA uploads
   (4 MiB each, `a=t`): 1-7 `OK`, 8-12 `ENOSPC:image store is full`; placing 1 and 2 afterwards
   `OK`, 12 `ENOENT`. **In the wave:** evict older images with no placement before refusing.
3. **Held grid with glass on: nothing spills on the emulator**, either direction. But a *growing*
   pane shows its old grid **centred**, not top-anchored (`centredSlackPx`,
   `TerminalView.java:4312-4319`): the bottom pane grown by an upward drag drew rows 14-30 lower
   in the pane, and kew's old frame sat mid-pane in the grown top pane. **In the wave:** while held,
   the old grid is anchored at the top (behaviour 2 above). The logo above the bottom pane's top
   edge (pong 5.5 s) did not reproduce here; the always-on clip covers it whatever its cause, and
   the pong check after the wave looks for it.

Screenshots: session scratchpad `drag1/`, `drag2/`, `drag3/strip.png`, `pre3top.png`, `nowtop.png`.

## Tests owed

- Clip: a JVM/Robolectric test that a pane's draw never touches pixels outside its frame with a
  held, overflowing grid (text, classic image, placeholder image).
- Pause: the pause timer resizes after 100 ms still, not while moving, once more on release only
  if the size changed; keyboard/resize-key/focus-grow holds unchanged.
- Read-out text for a divider and a floating pane; changes only on a cell-count change.
- DECSET 2026 cleared by `resize`.
- Store eviction order and the refusal reply.

## Device checks owed (pong, after the wave)

- The recording's scenario again (kew + fastfetch stacked, divider dragged): nothing crosses a
  pane edge, glass on and off.
- The read-out wording and timing on a divider and a floating pane.
- The performance gate above.

## Pong measurements and traces (2026-11-01, 90 Hz, 11.1 ms budget)

Method: `scripts/perf-scenarios/pong-divider-drag-{up,down}.txt`, `ON_DEVICE=1 ROUNDS=1`, split reset
to half before every run, 5 up + 5 down per build; builds installed through device-drops.

| Per drag | Before (39f0faf4d) | After (b1628d923) |
|---|---|---|
| Frames | 135.4 | 161.8 |
| Janky (missed deadline) | 5.8 (4.3%) | 10.9 (6.7%) |
| p50 / p90 / p99 | 12.6 / 23.7 / 32.3 ms | 13.5 / 23.6 / 32.9 ms |

Gate result: jank moved the wrong way. Paired Perfetto traces of one verified drag (32 → 21 rows)
on each build:

- **The pause-resize is cheap:** `Terminal.resize` ≤ 0.16 ms, `Terminal.updateSize` ≤ 0.58 ms, five
  per drag (two pauses + release) against two. No guard needed.
- **A. Cursor trail (after only):** `Effect.cursorTrail.draw` 45× during the drag and 17 frames
  (0-196 ms) after release; none in the before build. A reflow snaps the trail
  (`TerminalView.java:3244`), but the held grid's re-anchoring moves the grid on screen without one,
  and the trail animates that as a cursor jump. Visible glitch as well as frames.
- **B. Read-out exit:** 16 frames at 767-942 ms after release (the 750 ms linger + 160 ms fade), main
  thread ~2 ms each, but RenderThread redraws the whole page (`Drawing 0 210 1080 2235`) per frame.
- **C. The drag itself (both builds):** every move re-lays out (≤ 8-12 ms) and re-renders both panes'
  text (`Terminal.render` ≤ 8 ms), giving 14-23 ms frames. Frame-timeline jank is almost all
  "Buffer Stuffing" in both builds (frames queued faster than SurfaceFlinger takes them).

Follow-up wave (decided 2026-11-01): A no trail from resizes or pane moves; B the read-out's fade
redraws only the pill (or no fade); C a held pane reuses its last render instead of re-rendering
its text per frame. Re-measure with the same 10 drags and a paired trace.

### After the follow-up wave (04b5921b1, 2026-11-01, 90 Hz)

Same method, staging `2.0.0-pre.1+dev.04b5921b1`; all 10 drags landed (top rows 33 after up, 31
after down; no missed press).

| Per drag | Before (39f0faf4d) | After (b1628d923) | Follow-up (04b5921b1) |
|---|---|---|---|
| Frames | 135.4 | 161.8 | 145.4 |
| Janky (missed deadline) | 5.8 (4.3%) | 10.9 (6.7%) | 10.0 (6.9%) |
| p50 / p90 / p99 | 12.6 / 23.7 / 32.3 ms | 13.5 / 23.6 / 32.9 ms | 14.7 / 22.7 / 33.2 ms |

Paired trace (one drag, 32 → 21 rows):

- **A held:** no `Effect.cursorTrail.*` slice anywhere in the trace.
- **B held:** no run of page redraws after release; frames return to ~3 ms `doFrame` within
  ~200 ms of the settle.
- **C held, and the open risk did not happen:** while held, `Terminal.scrollCache.record` runs 4×
  (once per pane per pause, 2.1-2.7 ms) and `Terminal.scrollCache.draw` 20× (≤ 0.05 ms). It is not
  re-recorded each move.
- **What still misses the budget:**
  1. *The per-move layout pass.* Every move frame runs `measure` + `layout` of 8-10 ms (13 passes
     in the drag), giving 16-21 ms traversals. This is the stock `LinearLayout`
     `setLayoutParams` → full `requestLayout` path (`TerminalPaneController.java` ~2802-2813,
     ~5637), untouched by either wave.
  2. *The settle frame.* At release both panes render at their new size in one frame
     (`Terminal.render` 12.3 + 15.7 ms, traversal 31.4 ms, then a 19 ms RenderThread
     `DrawFrames`). That is the drag's worst frame. Normal renders are 2-4 ms, so the first render
     after a reflow is the expensive one.

Gate result: jank is still above the before build (10.0 against 5.8 per drag), though no longer
from A or B.

### Wave D (a3d3a0981, 2026-11-01, 90 Hz)

D1 re-lays out only the dragged split in place (`SplitRelayout`, trace `Pane.splitRelayout`). D2
weights the pane backdrop mean by area (`ChromeInk.meanPaneSurface`), keeps an applied backdrop
under 1.05:1 (`ContrastFloor.sameBackdrop`), and traces `Terminal.groundSettled` /
`Terminal.contrastInvalidate`. D2's cause was reproduced on emu2 with a reverted `TLGround` log:
the equal-weight mean crept #8D → #83 over one drag and every settle re-recorded both panes.

| Per drag | Before (39f0faf4d) | Follow-up (04b5921b1) | Wave D (a3d3a0981) |
|---|---|---|---|
| Frames | 135.4 | 145.4 | 156.2 |
| Janky (missed deadline) | 5.8 (4.3%) | 10.0 (6.9%) | 11.4 (7.3%) |
| p50 / p90 / p99 | 12.6 / 23.7 / 32.3 ms | 14.7 / 22.7 / 33.2 ms | 11.3 / 22.8 / 33.4 ms |

Paired trace (32 → 21 rows):

- **D2 held:** no `Terminal.contrastInvalidate` at all; `Terminal.groundSettled` 3× at 2-3 ms; the
  31 ms post-lift frame is gone (worst `Terminal.render` 7.9 ms).
- **D1 moved the cost, not removed it:** window `layout` is down to 0.2 ms, but
  `Pane.splitRelayout` itself takes 5-11 ms (mean 8.3, 6 per drag) inside the touch handler, on big
  cores, with no named section under it. The cost is the panes' own measure/layout, not the
  window's. Lead (unconfirmed): per-move work on a size change inside a pane frame, e.g.
  `ChromeInk`'s per-pane wallpaper measurement (the emulator log showed it re-measuring on every
  geometry step) or a glass backdrop view's resize. Next: trace sections inside the pane frame's
  measure/layout and the per-pane ground measurement, one more pong trace, then the fix.
- **Named (diagnostic build d8337be6b, trace sections kept as 4a95c7c02):** inside
  `Pane.splitRelayout` (6.5-13.4 ms), `Pane.glass.layout` x2 takes 6-12 ms, almost all of it
  `Ink.terminalPane` x2 (5-10 ms): each pane's glass re-asks `ChromeInk.terminalPane` for its
  veil on every rect change (`PaneGlassBackdropView.onLayout` → `refreshVeil` →
  `paneGlassVeil`). The wallpaper sample is only 0.4-1 ms of it; the rest is the ink resolve.
- **Float corner drag** (same build, 640x1102 → 820x1402 with two pauses): `Pane.floatBounds`
  is 0.2 ms, then a window `layout` of 5-9 ms per move: `Ink.terminalPane` 2.4-4.6 ms,
  `FloatTitlebarView.onSizeChanged` 1.4-2 ms, the rest of the window ~1 ms. `Terminal.resize`
  at each pause is ~8 ms (columns change, unlike a divider's rows).
- **Float move** (handle, -120,+240 px): window layout ~1 ms, but `Ink.terminalPane` runs from
  the draw pass ~2x per frame for the whole move (133 calls, 2.7 ms each, ~5 ms a frame) and
  stops at rest.
- One cause across all three: the pane's ink is resolved anew whenever its on-screen rect
  changes. Next: hold the veil during a pane gesture and resolve once at each pause and at the
  lift, like the grid (wave E).
- **Wave E (ce5688cbf, veil held per gesture; 2026-11-01, phone charging):** divider drags
  146.8 frames, 9.7 janky, p50/p90/p99 12.3/20.7/31.5 ms. `Pane.splitRelayout` 10 → ~1 ms;
  `Ink.terminalPane` only at pauses and the lift (11 per drag). Per call: sample 0.5 ms,
  lightness 0.8 ms, resolve 0.02 ms, ~1.2 ms other, so faster sampling alone would halve a
  call that now runs only at pauses: the summed-area sampler is not worth building for drags.
- **The janky count is mostly Buffer Stuffing**, not app lateness: in the wave E trace 62 of
  68 flagged frames are Buffer Stuffing, 4 are App Deadline Missed. App Deadline Missed per
  traced divider drag: 6 (04b5921b1, worst 52 ms) → 5 (wave D, 32 ms) → 4 (wave E, 30.5 ms).
  The four left are the gesture's edges: the press (5 ms input), the first move after a pause
  (both held panes record their cache, 15.8 ms), the lift (22 ms in the up handler: the
  snap re-layout with the veil solve 7.8 ms, `Chrome.requestSync` 4.2 ms), and a render-side
  frame ~750 ms after.
- **Floats are not held yet:** float move and corner drag still run `Ink.terminalPane` ~2x
  per frame from `PaneGlassBackdropView.onDraw` with a fresh rect each call; the divider hold
  works. Cause unknown from reading; a `TLVeil` diagnostic log is next.
- Floating pane corner drag: same propagation (`applyFloatBounds` → `container.setLayoutParams`,
  `TerminalPaneController.java` ~4592); not measured yet. Pong's Home has no free Terminal entry
  to open a float without changing the developer's Home, so its trace needs a float opened by
  the developer or a route that leaves Home untouched. The remaining cost is the layout pass and the settle frame, not the pause-resize. Whether the
follow-up passes, and whether a wave D takes the layout pass (and the settle render), is the
developer's decision.
