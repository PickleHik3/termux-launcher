# Live neighbour windows (2026-11-01)

## The problem

A swipe between two Workspace windows shows an empty slot until release: the Workspace renders
only the active window's tree into `terminal_pane_host`, and `PaneWallLayout.stepInside` swaps
the next window in (`stepWindowFromWall` → `showWindow`) only once the finger lets go. Home ⇄ Home
and Home ⇄ window slide the arriving page in mid-gesture, because each is its own view.

## Decided with the developer

- The windows on either side of the current one are **live**: their panes are drawn, live,
  beside the current window while a drag moves the wall, and the arriving one becomes the current
  window at release with no swap.
- Only the **adjacent** windows (current − 1, current + 1) are kept ready. Windows further away
  stay as they are today; a chip tap or keyboard shortcut to any window keeps today's instant
  switch, and the ready pair is re-chosen around the window it lands on.
- **Ready, not running.** A neighbour is built and laid out at the current window's size (same
  host size, so its shells never hear a resize) but hidden (`GONE`) at rest: it draws nothing
  and costs no frames while the user sits in a window. It is made visible when a drag starts and
  hidden again at settle.
- A toggle in Settings › Battery and performance, on by default:
  **"Live windows while swiping"**: "Show the next window's live output as you swipe between
  windows. Turn off to save battery." Off: today's behaviour (wave 1); a last picture of the
  window (wave 2).

## Constraints from the code

- A `TerminalSession` has exactly one `TerminalView` (`TerminalPaneController.paneFrameFor`,
  maps keyed by session). Each session lives in one window, so each neighbour window's panes are
  its own views; nothing is attached twice. A second controller is not needed.
- `doRender` treats the host's child 0 as the active tree and removes children ≥ 1 on every
  render, so neighbour trees cannot live inside `terminal_pane_host`.
- A grid size reaches the PTY only from `TerminalView.updateSize` (pty-resize in the motion log);
  the D11 rule holds: no pty-resize during or at the end of a window swipe.

## Waves

1. Live neighbours + the toggle (this document's decisions).
2. Toggle off: the last picture of the neighbour window slides in, live at landing; pictures
   only for the two neighbours, dropped in `onTrimMemory`.
