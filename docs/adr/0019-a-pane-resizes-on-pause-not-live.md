---
status: proposed
date: 2026-11-01
---

# A pane resizes when the drag pauses, not live, and always draws inside its frame

On 2026-10-27 a recording on pong showed a stacked split (kew's album art above, fastfetch's logo
below) drawing text and pictures across its neighbour while the divider was dragged. A pane's
grid is held for the whole drag and resized once on release, and nothing clips a terminal to its
pane's frame (`project-docs/reference/launcher/terminal-resize-and-images-research-2026-10-31.md`,
§4.2). The two terminals we measured ourselves against do this differently: kitty holds the old
grid while the window moves and resizes once the motion pauses for 0.1 s, with a size banner;
Ghostty reflows the grid live, at most every 25 ms, with a size overlay. Both draw each terminal
strictly inside its own rectangle.

We decided:

1. **Every pane's terminal is clipped to its frame, always** — text, classic images and
   placeholder images. Nothing a pane holds draws over another pane.
2. **During a finger drag (a split divider or a floating pane's corner), a pane resizes when the
   finger has been still for 100 ms, and on release** — kitty's model, not Ghostty's. While the
   finger moves the old grid stays at its old cell size, anchored at the top, cut at the frame,
   with the pane's background below it when the pane grows. Every other held transition
   (keyboard, resize keys, focus grow) keeps its single resize at the end.
3. **A drag shows the size in cells as a read-out on the notice**, both panes in layout order for
   a divider (`80×12 · 80×20`), one size for a floating pane.
4. **A resize ends an open synchronized update** (mode 2026), as both references do.

Why not live: the launcher's reflow runs on the main thread (`TerminalSession.updateSize` →
`TerminalEmulator.resize`), drawn on a canvas that already pays for live glass, inside pong's
11.1 ms frame at 90 Hz. Live reflow would run up to forty reflows a second, each with the
program's own redraw, through a long scrollback. Resizing on pause costs one reflow per hesitation,
which is when the user is looking, and the read-out answers the question the live reflow would
("how big will it be?"). The pause-resize ships only if a pong drag scenario shows no worse p50,
p90 or jank than the build before it; if a reflow over a 4000-line scrollback measures past one
frame at p99, a guard follows (`docs/agents/performance.md`).

## Considered options

- **Ghostty's live reflow (25 ms).** Most responsive; rejected on the main-thread cost above.
  Revisit if the reflow moves off the main thread.
- **Keep one resize on release.** Cheapest; leaves the user guessing the size until they let go.
- **Adaptive (live while cheap, on pause otherwise).** More code and two behaviours to test, for
  a difference the read-out mostly covers.

## Consequences

- A program gets SIGWINCH once per pause during a drag instead of once per drag. A program that
  redraws on resize (tmux, editors, kew) redraws at each pause.
- Images follow the references, not anything kinder: a classic image is cut at the pane edge when
  the pane narrows, a placeholder logo wraps into strips when its pane is narrower than the logo,
  and an old classic image stays until its program deletes it.
- The spec is `project-docs/active/terminal-resize-on-pause-2026-11-01.md`.
