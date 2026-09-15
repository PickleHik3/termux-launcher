# Corners and holds — spec (approved on the review page 2026-09-15)

Two touch problems on the terminal place, one grammar. The pane corners steal taps meant for the
program, and the press-and-hold ladder asks for timing no thumb can repeat. Review page with the
before/after figures and the hint mock: `.lavish/hold-gestures.html`.

## Today (dev @ 4ab57586)

- Every pane has a 32 dp square inside each corner (`CornerZones.SIZE_DP`). `PaneInteractionOverlay`
  (in `TerminalPaneController`) takes any touch that starts in one: a drag at a seam resizes, a lift
  opens the corner tab. The terminal never sees it, so a program's own controls in the corners
  (tmux status ends, vim's ruler) are unreachable there. Terminal corners give no haptic; the
  Widgets and Display frames do.
- Inside the terminal: a still finger in a mouse-reporting program opens the loupe at 150 ms
  (`AimState.AIM_DELAY_MS`); Android's long press (400 ms default, stock `GestureDetector`) then
  cancels the loupe, buzzes, and arms a mouse drag (motion reporting) or starts text selection.
  From the arm, a drag is a button-held mouse drag with a tick, a lift is text selection anyway.
  The action sheet ("copy, paste and more") is the selection toolbar's More or the menu key; it is
  not duration-gated. The explicit Mouse mode toggle bypasses all of this.

## Decisions

- **Corner target: hold-through at the corner (A).** The square stays at the pane corner and grows
  to 40 dp. On `ACTION_DOWN` in a square the overlay lets the event through and starts one timer;
  until it fires everything is forwarded, so taps, scrolls and drags reach the program as if the
  square were not there. When it fires the overlay sends the terminal `ACTION_CANCEL`, buzzes
  (`LONG_PRESS`), draws the bracket and owns the gesture: lift opens the tab, a drag at a seam
  resizes (tick on the first move), a drag at an outer corner drags the tab out. A second finger
  before the timer abandons the hold and both fingers go to the terminal. `TerminalView` gains a
  hold-exempt hook (rect per pane, or a flag on the down) so its own hold never races the overlay's.
  Nothing is drawn at rest. Chrome-band and gutter placements were dropped: the bands carry their
  own controls and are not always there, a split has nothing outside its inner corners.
- **T_hold: three quarters of `ViewConfiguration.getLongPressTimeout()`, floor 250 ms.** 300 ms on a
  default phone; 750 / 1125 ms when Android's Touch & hold delay is Medium / Long. One constant,
  `HoldTiming` in terminal-view, shared by the corner hold and the terminal hold.
- **Terminal hold: one decision point, then the finger decides.** The loupe at 150 ms is a preview
  and is never taken away by time. At T_hold the hold is recognised from the view's own timer (the
  `GestureDetector` long press no longer decides this path), one buzz, loupe stays. Then:
  lift → click the aimed cell; drag → button-held mouse drag if the program wants motion (tick on
  the first reported move, loupe rides along), otherwise the aim moves and lift clicks; a second
  finger that taps → text selection at the aimed cell; a second finger that moves → hold abandoned,
  wheel or pinch as today; keep holding → nothing changes, ever. Plain shell: the buzz at T_hold
  starts text selection as Termux always has; a drag before T_hold scrolls everywhere.
- **Selection door in mouse programs: hold, then tap a second finger.** The action sheet keeps its
  two doors and gains no gesture.
- **Hint for the second finger.** When the hold is recognised and the loupe is open, one line under
  the strip: "Tap with another finger to select text." It leaves with the loupe (lift, drag, second
  finger). Drawn on the loupe's far side from the finger, flipping above when near the pane bottom;
  takes no touches; single line, ellipsised; no fade under reduced motion. Stops for good after
  three second-finger selections (`hold_select_hint_uses`, default 0, in
  `TermuxAppSharedPreferences`); Replay the tour resets it. Never in a plain shell. The Help topic
  for the terminal keeps the sentence permanently.
- **Haptics, one vocabulary.** Hold recognised (corner or terminal): `LONG_PRESS`. Drag committed
  (mouse drag, seam resize): `CONTEXT_CLICK`. Corner tab button tapped: `CONTEXT_CLICK`. Loupe open:
  none.
- **Copy.** The word is *hold*; "press" and "tap" leave the corner strings.
  `tour_card_find_help_corner` → "Hold a pane corner." Help topic Pane corners action → "Hold a
  corner, then tap the ? button.", reveal → "Hold any corner of a pane to see its controls." Tour
  closing card → "Hold a pane corner for the Appearance and Layout editors to make the launcher
  yours." New Help line for the terminal surface: "Hold to see where you are pointing. Lift to
  click, drag to drag, or tap with a second finger to select text." `docs/en/Launcher_Usage.md`
  24–47 rewritten to this grammar and names the loupe.
- **Out of scope.** A Settings entry for any timing; the Mouse mode toggle; the Display page's
  touchpad and its long press; the floating toolbar's auto-hide; the Display and Widgets frames'
  tap-to-open corners (follow-up if the hold proves right on the terminal).

## Build plan

One worktree, `feat/hold-gestures`, off dev. Phases land as commits on that branch; merge to dev
only on the developer's confirmation.

| Phase | Deliverable | Depends on |
|-------|-------------|------------|
| 0 | `HoldTiming` (terminal-view, pure Java) with unit tests | — |
| 1 | `PaneInteractionOverlay` hold-through: pass-through until T_hold, cancel to the terminal, tab on lift, seam resize / tab drag after the hold, 40 dp squares, haptics; `TerminalView` hold-exempt hook; copy changes. Tests: `TerminalPaneCornerTabTapTest` gains tap-passes-through and hold-opens; a pure state-machine test for the overlay gesture in the style of `AimStateTest` | 0 |
| 2 | `TerminalView` / `AimState`: loupe outlives the long press, hold recognised at T_hold, branch by motion, second-finger selection, plain-shell selection at T_hold, mouse-drag arm folded into the hold state, hint pill with its counter. Tests: `AimStateTest` extended; new `HoldGestureTest` driving every row of the grammar | 0 |

Gates: `./gradlew testDebugUnitTest` green on the branch after each phase (compare failing-name
lists against a clean worktree, never counts); then the device checks on pong before the merge is
proposed. The emulator cannot judge hold timing or haptics.

## Status

2026-09-15: Phases 0, 1 and 2 are on `feat/hold-gestures` (677ce3da, 413da10a and the Phase 2
commit); every module's unit suite is green (4,665 tests). Also landed beyond the plan: the tour
glow and the help box use the pane's 40 dp square; in Mouse mode the touchpad press is deferred
while a corner may still claim the touch (`MouseModePress`), so a corner hold no longer clicks.
Owed: the device checks below on pong, then the developer's cue before merging into dev.

## Device checks on pong

1. tmux: tap the clock at a pane's bottom-right. Nothing opens; tmux gets the click.
2. vim in a split: tap the last column of the status line at the inner corner. Cursor moves; no
   tab, no resize.
3. Hold any corner: one buzz at about a third of a second, bracket, lift opens the tab. Ten of ten.
4. Hold a seam corner, then drag: the split resizes. Lift without dragging: the tab.
5. vim: hold a word. Loupe opens and stays while the finger does (count to five). Lift: cursor
   lands on the word.
6. vim with `set mouse=a`: hold, then drag. Tick on the first move, visual selection follows, the
   loupe rides along.
7. vim: hold, then tap a second finger. Selection handles at the aimed cell; Copy · Paste · More;
   More opens the action sheet.
8. The hint shows under the loupe on the first three second-finger selections and not on the
   fourth. Replay the tour, hold again: it is back.
9. Plain shell: hold → selection after one buzz; a fast drag still scrolls.
10. Two-finger scroll and pinch still work in vim and the shell, including with one finger already
    resting.
11. Accessibility → Touch & hold delay = Long: corners and holds slow down with it. Restore.
12. Tour lesson 1 and Help → Pane corners → Try it complete on a hold.

## Risks

- `ACTION_CANCEL` into the terminal must read as "nothing happened" in `TapPrecision` and the wheel
  path, never as a click (Phase 1 test).
- A still second finger (select) vs a moving one (scroll) after the hold uses the view's existing
  slop; a two-finger scroll that begins with one finger already resting is the case to test.
- Programs that want clicks but not motion (tmux default): after T_hold a drag moves the aim and
  lift clicks. Confirm it reads well on the phone.
- If the hold proves undiscoverable, fix it in the tour and help, not with an always-on mark: the
  terminal-chrome spec dropped perimeter bands on purpose.
