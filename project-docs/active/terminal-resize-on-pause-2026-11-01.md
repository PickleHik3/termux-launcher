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
7. **Image store full**: the store stays 32 MiB / 256 images. When a new image does not fit,
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

Findings: (filled in after the reproductions)

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
