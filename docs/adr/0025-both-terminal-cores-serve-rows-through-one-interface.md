---
status: proposed
date: 2026-11-09
---

# Both terminal cores serve rows through one interface

Number note: 0024 is taken on `v2-redesign` (`0024-the-wallpaper-moves-in-glsl.md`) but not on
`exp/ghostty-core`; this ADR takes 0025 so the two never collide on merge.

The experimental libghostty-vt core (`debug.tl.terminal_core=ghostty`, waves 1 and 2 on
`exp/ghostty-core`) parses about 15 times faster than the Java core on pong but then copies
Ghostty's state into the Java `TerminalBuffer` so every reader keeps working
(`GhosttyMirror.applyScreen`, `scrollIntoTranscript`). That copy is two thirds of the Ghostty
core's parse time (129–146 ms of 203–223 ms on a 13 MB transcript) and is the 35.8 ms p90
main-thread start delay in a flood. Wave 3 is about removing it without breaking the Java core,
which stays the default and the fallback.

Reading the code changed the goal. Most of the copy is not the screen. Each output turn
copies every history row that scrolled off, up to the whole 2000-row transcript, and only 45
screen rows. Any Canvas renderer needs the screen in Java anyway: `RowRenderCache` compares
`TerminalRow` values and `drawRowGlyphs` draws their `char[]`. So the cost to cut is the history
copy and the per-turn timing, not the idea of a Java row.

We decided:

1. **One read interface, `TerminalContent`, row-granular, with `TerminalRow` as the value it
   hands out.** `TerminalBuffer` implements it as it is (the Java core does not change);
   `GhosttyContent` implements it for the Ghostty core. Readers that walk rows, text, the
   transcript or the scroll position go through it. Cursor and mode getters stay on
   `TerminalEmulator`: both cores already serve them as plain fields.
2. **The Ghostty core keeps a screen snapshot and serves history on demand.** Up to `mRows` Java
   rows are rewritten from Ghostty's render state, dirty rows only, once per output turn (later,
   once per frame). History rows are read through grid refs when a reader asks, into a small
   window of reused rows keyed by absolute row number. No Java transcript is kept for a Ghostty
   pane.
3. **One owner per two-phase number.** The snapshot is the only thing readers see: its cursor,
   size, history count and scrolled-row count come from the same render-state update as its
   rows. `TerminalView.mTopRow` stays the only viewport. We never scroll Ghostty's own viewport.
4. **Kitty graphics on the Ghostty core: Ghostty stores and decodes; we keep only the bitmaps
   being drawn,** under a budget beside `KittyImageStore`'s. Sixel, iTerm2 images and OSC 66
   text sizing are dropped in Ghostty mode; OSC 99 notifications are kept through a string
   sniffer (`vt_write_until`); OSC 8, OSC 133 and OSC 9;4 map onto Ghostty's own data.
5. **Parsing on the pty reader thread is designed now but built last, behind a measurement.**
   With the history copy gone, a 4 ms output turn is mostly `vt_write` at about 180 MB/s; moving
   it off the main thread saves well under 10% of the main thread in a flood, at the price of a lock
   and a second hold path. It ships only if the flood or typing numbers still miss budget.
6. **A failed Ghostty pane falls back by replay.** With no Java copy to fall back on, the
   fallback formats the Ghostty screen and recent history as VT text (`ghostty_formatter`) and
   feeds it to a fresh Java emulator. A native crash in the Ghostty core turns the switch off for
   new panes.

## Considered options

- **Keep the copy, move it to a background thread.** Every one of the 21 reader classes reads
  `TerminalBuffer` on the main thread with no lock, so a background copy needs either a second
  full buffer (double the transcript) or locks in every reader. It keeps the cost and adds a
  race. Rejected.
- **Readers read cells over JNI (`get_multi`, `CELLS_RAW`) with no Java rows at all.** That puts
  a JNI call per cell or per row into row recording, which is already the 12 ms UI-thread p90 in
  full-screen redraws, and it rewrites every reader. Rejected; a GPU renderer that reads the
  render state natively is the place for this (out of scope, seam left).
- **Use Ghostty's viewport for scroll-back drawing.** Two owners for the scroll position, and
  Ghostty marks every row dirty after a viewport scroll, which would defeat `RowRenderCache`'s
  shift path. Rejected.
- **Ghostty's dirty flags replace `RowRenderCache`'s value compare.** The flags are
  conservative (a rewritten but identical row is dirty), and the exact compare is what keeps
  identical rows from being re-recorded. Kept as a fast path only.

## Consequences

- The Java core's behaviour, tests and numbers stay as they are; readers change their calls once.
- A Ghostty pane's scroll-back, find, selection and URL tap read native memory on the main
  thread, a few microseconds per row (reasoned from the parse bench; slice 2 measures it).
- Ghostty mode loses sixel, iTerm2 images and text sizing until someone builds a translator.
- ADR 0019 (resize on pause) is untouched: it rejected live reflow for main-thread cost, and
  this ADR does not move reflow off the main thread.
- The spec is `project-docs/active/ghostty-core-wave3-2026-11-09.md`.
