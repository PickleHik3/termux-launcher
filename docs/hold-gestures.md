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
  (`LONG_PRESS`) and owns the gesture: lift opens the tab, a drag at a seam
  resizes (tick on the first move), a drag at an outer corner drags the tab out. A second finger
  before the timer abandons the hold and both fingers go to the terminal. `TerminalView` gains a
  hold-exempt hook (rect per pane, or a flag on the down) so its own hold never races the overlay's.
  Nothing is drawn, at rest or during the hold: the buzz says the hold took, the tab says which corner (the bracket outlived the touch on pong and was removed). Chrome-band and gutter placements were dropped: the bands carry their
  own controls and are not always there, a split has nothing outside its inner corners.
- **T_hold: three quarters of `ViewConfiguration.getLongPressTimeout()`, floor 250 ms.** 300 ms on a
  default phone; 750 / 1125 ms when Android's Touch & hold delay is Medium / Long. One constant,
  `HoldTiming` in terminal-view, shared by the corner hold and the terminal hold.
- **Terminal hold: a hold that only ever moves forward, and nothing to aim at.** At T_hold the hold
  is recognised from the view's own timer (the `GestureDetector` long press no longer decides this
  path), one buzz. Mouse program: lift → click the cell (`TapPrecision` picks the press point, as a
  plain tap does); drag → button-held mouse drag if the program wants motion (tick on the first
  reported move), otherwise nothing moves and the lift clicks where the finger is; a second finger →
  hold abandoned, wheel or pinch as today; still until T_select → a second, different buzz and text
  selection at the finger with Copy · Paste · More. Plain shell: the buzz at T_hold starts text
  selection as Termux always has, no second stage; a drag before T_hold scrolls everywhere.
- **The loupe is gone.** Revised on pong 2026-09-15 (third feel): the magnified strip invited the
  finger to rest and aim, and aiming takes longer than T_select, so selection kept landing in the
  middle of it. Without it there is nothing to wait for past the first buzz. `AimState`, the strip,
  the hint under it and its counter are removed; a Mouse-mode long-press reticule was considered
  and dropped (Mouse mode sends the press on landing, so an aim would have to defer it).
- **After the buzz, one cell of travel is a drag.** Before the hold the 8 dp slop decides scroll
  versus hold, as everywhere on Android. After it, a move of one cell width sideways or one row up
  or down from the landing point (or the slop, whichever is smaller) starts the mouse drag and
  cancels the selection stage; a distance, never a cell-boundary crossing. Found on pong
  2026-09-15 (fourth feel): a careful one-column border resize in herdr stayed inside the slop, so
  nothing was reported and selection opened at T_select instead.
- **T_select: twice `ViewConfiguration.getLongPressTimeout()`.** 800 ms on a default phone,
  2000 / 3000 ms at Medium / Long, so the two buzzes are always at least half a second apart.
  `HoldTiming.selectTimeoutMs`. Start at 800; lower toward 600 only if it feels sluggish.
- **Selection door in mouse programs: the long press, as everywhere on Android.** Revised on pong
  2026-09-15 from "hold, then tap a second finger", which nobody expects. The action sheet keeps its
  two doors and gains no gesture.
- **Haptics, one vocabulary.** Hold recognised (corner or terminal): `LONG_PRESS`. Selection from
  the hold: `CONFIRM` (API 30+, else `LONG_PRESS`). Drag committed (mouse drag, seam resize):
  `CONTEXT_CLICK`. Corner tab button tapped: `CONTEXT_CLICK`.
- **Copy.** The word is *hold*; "press" and "tap" leave the corner strings.
  `tour_card_find_help_corner` → "Hold a pane corner." Help topic Pane corners action → "Hold a
  corner, then tap the ? button.", reveal → "Hold any corner of a pane to see its controls." Tour
  closing card → "Hold a pane corner for the Appearance and Layout editors to make the launcher
  yours." New Help line for the terminal surface: "Hold for a moment to use the mouse: lift to click,
  drag to drag. Keep holding to select text." `docs/en/Launcher_Usage.md` 24–47 rewritten to this
  grammar.
- **Out of scope.** A Settings entry for any timing; the Mouse mode toggle; the Display page's
  touchpad and its long press; the floating toolbar's auto-hide.
- **Widgets and Display corners hold too** (2026-09-15, after the terminal's hold proved right on
  pong): `WidgetPaneFrame` and `X11PaneFrame` stop intercepting a corner square on DOWN; the child
  gets the gesture, a still hold of T_hold buzzes and claims it (the child sees `ACTION_CANCEL`), the
  tab opens on lift. Same 40 dp square, no bracket, same haptics. A Widgets long-press on an empty
  cell inside a corner square now loses to the corner hold.

## Build plan

One worktree, `feat/hold-gestures`, off dev. Phases land as commits on that branch; merge to dev
only on the developer's confirmation.

| Phase | Deliverable | Depends on |
|-------|-------------|------------|
| 0 | `HoldTiming` (terminal-view, pure Java) with unit tests | — |
| 1 | `PaneInteractionOverlay` hold-through: pass-through until T_hold, cancel to the terminal, tab on lift, seam resize / tab drag after the hold, 40 dp squares, haptics; `TerminalView` hold-exempt hook; copy changes. Tests: `TerminalPaneCornerTabTapTest` gains tap-passes-through and hold-opens; a pure state-machine test for the overlay gesture in the style of `AimStateTest` | 0 |
| 2 | `TerminalView`: hold recognised at T_hold, branch by motion, selection at T_select, plain-shell selection at T_hold, mouse-drag arm folded into the hold state; the loupe and its hint removed. Tests: new `HoldGestureTest` driving every row of the grammar | 0 |

Gates: `./gradlew testDebugUnitTest` green on the branch after each phase (compare failing-name
lists against a clean worktree, never counts); then the device checks on pong before the merge is
proposed. The emulator cannot judge hold timing or haptics.

## Status

2026-09-15: Phases 0, 1 and 2 are on `feat/hold-gestures` (677ce3da, 413da10a and the Phase 2
commit); every module's unit suite is green (4,665 tests). Also landed beyond the plan: the tour
glow and the help box use the pane's 40 dp square; in Mouse mode the touchpad press is deferred
while a corner may still claim the touch (`MouseModePress`), so a corner hold no longer clicks.
First feel on pong 2026-09-15: the corner bracket outlived the touch (removed, a956ec7e) and the
second-finger selection was too far from what users expect (replaced by the T_select stage above).
Third feel: the loupe kept colliding with T_select and was removed (see "The loupe is gone").
Fourth feel: one-column border resizes missed the 8 dp slop and fell into selection; after the
buzz one cell of travel now starts the drag.
Merged to dev b8423175 (2026-09-15). Follow-ups on `fix/pane-corner-radius-and-edge-touch`: the
pane frame forwards clearance touches (44ff1f2c), one pane corner radius rule (538404eb), and the
Widgets/Display corners hold like the terminal's (see "Widgets and Display corners hold too").
Evidence for the first two: `docs/research/2026-09-15-corner-touch-targets.md`.

## Device checks on pong

1. tmux: tap the clock at a pane's bottom-right. Nothing opens; tmux gets the click.
2. vim in a split: tap the last column of the status line at the inner corner. Cursor moves; no
   tab, no resize.
3. Hold any corner: one buzz at about a third of a second, nothing drawn, lift opens the tab. Ten of ten.
4. Hold a seam corner, then drag: the split resizes. Lift without dragging: the tab.
5. vim: hold a word. One buzz, no magnifier. Lift: cursor lands on the word.
6. vim with `set mouse=a`: hold, then drag. Tick on the first move, visual selection follows.
7. vim: hold and keep holding. Second buzz at about 0.8 s, selection handles at the cell;
   Copy · Paste · More; More opens the action sheet.
8. vim: hold, then drag, then keep still. No selection appears however long the finger stays.
8b. herdr (or tmux): hold a pane border, then move one column. The border follows; no selection.
9. Plain shell: hold → selection after one buzz; a fast drag still scrolls.
10. Two-finger scroll and pinch still work in vim and the shell, including with one finger already
    resting.
11. Accessibility → Touch & hold delay = Long: corners and holds slow down with it. Restore.
12. Tour lesson 1 and Help → Pane corners → Try it complete on a hold.

## Risks

- `ACTION_CANCEL` into the terminal must read as "nothing happened" in `TapPrecision` and the wheel
  path, never as a click (Phase 1 test).
- A second finger after the hold abandons it and hands both fingers to the wheel or pinch; a
  two-finger scroll that begins with one finger already resting is the case to test.
- Programs that want clicks but not motion (tmux default): after T_hold a drag moves the aim and
  lift clicks. Confirm it reads well on the phone.
- If the hold proves undiscoverable, fix it in the tour and help, not with an always-on mark: the
  terminal-chrome spec dropped perimeter bands on purpose.
