# Terminal chrome unification — spec (2026-09-13)

Review page: `.lavish/terminal-chrome-unification.html` (decisions folded in). Base: dev 95187825.

## Streams

**A · Sessions drawer.** `TerminalSessionBrowser` (foot sheet, Save/Load) and the status-chip
dropdown (`SessionsPanelView` in `StatusCardHost`'s PopupWindow) both bind
`getSessionBrowserSessions()`. Replace both with one drawer on the sheet plane:
`Placement.terminalLeading()`, full terminal-area height across split panes, width
`min(340dp, 78%)`, slides in from the leading edge, 28% scrim over the rest, close by scrim tap,
leading swipe or Back. Corners follow the terminal radius (`PaneShape.radiusForBounds` cap).
Header: Sessions · Live | Saved segment · +. Live rows expand to windows; tap switches and closes;
… unfolds an inline action row (Rename = inline field, Close = inline confirm). Saved: workspace
rows (panes, age); tap = inline Add to current | Replace; … = inline Delete confirm. Live footer:
Save as workspace… (inline name). Chip tap, DRAWER key, `session.browser`, `session.panel` all
toggle it. Delete `SessionsPanelView`, `SessionsPanelMetrics`, the sessions case of
`StatusCardHost`; scrub stale "drawer" comments.

**B · Notice pill.** `AppNoticeHostView` gains a terminal placement: inside
`terminal_surface_host`, centred over the whole area, sliding down out of the top rim to 8dp.
Dress from a `TerminalDress` provider: radius `min(terminal radius, height/2)`, fill = pane fill
(glass tint over blur / terminal background 246/255 docked), hint-surface hairline. The dress is
used everywhere, reading stored preferences when no terminal is on screen. Delete
`SessionSwitchIndicatorView` and `TerminalKeyChordOverlay`; callers post `AppNotice` kinds.
Holds: Readout 1000 · Confirm 1600 · Info 2600 · Refusal 3800 · Undo 9000 · Sticky until
resolved (pending chord).

**C · Corner-only frame controls.** Replace perimeter bands with 32dp corner squares. Widgets and
Display tabs: tap any corner, tab appears at that corner; edit-mode carve-out stays. Display scale
rail shown only while the tab is open; `X11PaneFrame.watchForTap` excludes corners only. Terminal:
drag a pane corner to resize (a corner on a seam end moves that seam, on a crossing both); no
seam band. Floating pane moves from its top-leading corner; floating keyboard from its top
corners. Corner bracket drawn on touch-down only.

**D · Tap precision.** `TerminalView`: click cell from ACTION_DOWN when total movement < one
row, else lift; set `scrolledWithFinger` only when a scroll was delivered. Floor mapping stays
(correct). Then a hold-to-aim loupe: still ≈150ms shows the aimed cell outline + magnified row
strip above the finger; drag moves the aim; lift clicks; 500ms still hold = text selection as
today. No events while aiming.

## Build plan

| Phase | Branch | Deliverable | Depends on | Gate |
|---|---|---|---|---|
| P0 | dev | this file | — | — |
| P1 | feat/notice-pill | TerminalDress, terminal placement + kinds, deletions, callers | — | module tests; compile; docked/floating, 1/many panes, keyboard up/down on the HTC 10 |
| P2 | feat/sessions-drawer | terminalLeading placement, drawer UI, routing, deletions | P1 merged | binding/routing tests; save/load-add/load-replace/rename/close/delete on device |
| P3 | feat/corner-controls | corner hit-testing in WidgetPaneFrame, X11PaneFrame, TerminalPaneController, FloatingPaneContainer, FloatingKeyboardFrame; rail tied to tab | — | hit-test tests per frame; edge taps reach Display content |
| P4+P5 | feat/tap-precision | D-1 rules + tests, then loupe | — | terminal-view tests; TUI menu taps on device |

P1, P3, P4 in parallel; P2 after P1. Agents commit, never push, never touch devices. Merge only
green. pong only when the user pings.
