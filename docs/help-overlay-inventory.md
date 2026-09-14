# Home screen inventory for the per-place help overlays (2026-09-14)

Source of truth for designing an overlay help screen on each of the three places. Every item
lists where it sits, what gesture it takes, and whether the first-boot tour already demonstrates
it (card numbers are the thirteen-card run in `TourRun.java`). Facts were read from the code on
this date; file references are in the notes at the end.

## Screen anatomy, portrait, top → bottom

| # | Band | Height | View | On which places |
|---|------|--------|------|-----------------|
| 1 | Status bar host (glass). Expanded it is 96 dp: a 68 dp widget slot (clock, pinned notifications, media) over a 24 dp status row. Collapsed only the row shows. | 24 / 96 dp | `terminal_window_bar_host` | all |
| 2 | A–Z bar on the top edge (only when a place asks for it there) | 0 unless set | `place_az_bar_top` | per place setting |
| 3 | The wall: Widgets · Terminal · Display pages side by side; fills the rest | flexible | `terminal_pane_wall` | all |
| 4 | Find strip (scrollback search, only while searching) | one line | `terminal_find_bar_host` | terminal |
| 5 | Dock: pinned apps row, paged | row | `apps_bar_viewpager` | per place, default on |
| 6 | Page indicator band | 3 dp | `apps_bar_indicator_band` | with the dock |
| 7 | A–Z scrub row, or the keybind hint row in the same slot while Ctrl+Alt is latched | 19 dp | `apps_bar_az_row` / `keybind_hint_dock_row` | per place, default on |
| 8 | Extra keys row | 37.5 dp | `terminal_toolbar_view_pager` | all, default bottom |
| 9 | In-app keyboard | keyboard | `inapp_keyboard_view_host` | terminal; hidden on entering Display and Widgets unless the place asks for it |

Full-bleed layers over everything: app drawer, command palette, floating keyboard, surface editor.

Landscape: the dock becomes a rail down the left edge (`dock_rail_scroll`); the extra keys and
the A–Z bar can stand in side columns; a status bar on a side edge swaps to a column of window
buttons and never expands. The + and the chips do not exist on the rail.

Wall order left → right is Widgets · Terminal · Display. Terminal is home. Widgets exists only
when its setting is on; Display only in an X11 build.

## Status bar, left → right, per place

| Slot | Terminal | Display | Widgets |
|------|----------|---------|---------|
| Leading edge (behind the row) | Left neighbour's place icon peeking half past the edge; tap slides there | same | none (leftmost page) |
| 1 | Sessions badge: session name or number, tap opens the session switcher | — | — |
| 2 | Window chips, one per window, scrolling; then the **+** | The display's app windows as chips (icons, no +) | bare |
| 3 | CPU · RAM · Weather at the row's end; tap opens a detail card under the bar | same | Weather · RAM · CPU centred |
| 4 | AI glyph while a model runs, mouse mark while mouse mode is on | same | same |
| Trailing edge | Right neighbour's place icon peeking | none (rightmost) | Terminal's icon |
| Expanded slot (top 68 dp) | Home icon beside the clock, pinned notifications, media widget | same | same |

Gestures on the bar, on every place:

- Swipe along the bar: pages the wall. Commits at 35 % of a page or a fast fling. Tour card 1.
- Drag across the bar (down, then up): expands and collapses it. Remembered per place. Tour card 2.
- Tap a peeking neighbour icon: slides the wall to that place.
- Tap a stat: one shared detail card drops from the bar's bottom edge, dismissed by an outside tap.

## Terminal place

| Item | Does | Gesture | Position | Tour |
|------|------|---------|----------|------|
| Sessions badge | Names the session; opens the switcher | tap | status row, first | — |
| Window chips | Select a window; marks for busy, bell, done | tap; tap the selected chip again to reveal its × | status row after the badge | 3, stage 2 |
| Chip × | Closes that window; hides itself after a few seconds | tap | trailing end inside the selected chip | 3, stage 3 |
| + | New terminal window (terminal place only) | tap | end of the chip strip | 3, stage 1 |
| Panes | The split tree; each pane a terminal | touches go to the terminal; dividers drag | fills the page | — |
| Pane corners | 32 dp square at each of a pane's four corners; drops a tab | tap | four corners of every pane; with two panes eight squares cluster along the divider | 9 (glow uses the top-left) |
| Corner tab | One button on a lone pane (surface editor); move · maximise · close in a split; maximise · close when maximised | tap a button; tap anywhere else dismisses | slides out of the corner touched | 9 |
| Dock | Pinned apps, paged | tap launches; sideways swipe pages; **pull down opens the drawer** | bottom row of the accessory stack | 10 |
| App drawer | Full-bleed plane | pull down on the dock (sideways off the rail in landscape); swipe down closes | covers the screen | 10 |
| A–Z row | Alphabet index; scrub filters the icons and shows a preview beside the finger | slide along, drag up to an app, let go | between the dock and the extra keys | 11 |
| Keybind hint row | Chips saying what each key does while a prefix is latched | hold Ctrl+Alt or Ctrl+Alt+Shift | takes the A–Z row's slot | closing card copy only |
| Extra keys row | See below | tap; swipe up for the secondary; swipe left for the text-input page | above the keyboard | **not demonstrated** |
| In-app keyboard | See below | — | bottom | 4 to 8, 12 |
| Command palette | Full-plane surface | swipe up on the space bar; tap outside closes | covers the screen | 12 |
| Find strip | Scrollback search | — | one line above the dock | — |

## Display place

| Item | Does | Gesture | Position | Tour |
|------|------|---------|----------|------|
| X display surface | The embedded X server's picture; edges belong to X | all touches between the corners go to X | fills the page | — |
| Empty state | "No display" message with **Start display** (or a turn-on prompt while the setting is off) | tap the button | centred | — |
| Page corners → tab | **Power** (start or stop the display) and **cog** (display settings) | tap a corner, then a button | any of the page's four corners | — |
| Scale rail | Vertical slider with a read-out; only while the corner tab is out and a display runs | drag the thumb | along the leading edge, 16 dp in | — |
| Touchpad | 1 finger moves and taps, 2 fingers scroll, pinch, right-click, 3 fingers middle-click, switch windows, bring the keyboard back; scroll strip on its trailing edge; arrow in its bottom-left restores the keyboard | mouse mode on | takes the keyboard's place; over a split keyboard it stands in the gap | — |
| Status bar chips | The display's app windows, front one selected | tap raises that window | the terminal chips' slot | — |
| No + and no sessions badge | Display apps come from the drawer | — | — | — |
| Keyboard | Hidden on entering unless the place asks; landscape default floats it over the display | — | — | — |

The dock, A–Z row and extra keys row are present by default here too; the wall keys on the extra
keys row are the way back to the terminal without the status bar.

## Widgets place

| Item | Does | Gesture | Position | Tour |
|------|------|---------|----------|------|
| Widget grid | Exact-cell host for app widgets, non-scrolling | widgets take their own touches | fills the page | — |
| Empty hint | "Add a widget" message | — | centred | — |
| Page dots | More than one widget page | sideways swipe pages | bottom centre | — |
| Long-press menu | Add widget · Edit widgets · Add page · Remove page | long-press empty grid | popup at the touch point | — |
| Page corners → tab | **Cog** (layout settings at the widget grid) and **pencil** (edit widgets); while editing, a grid-size read-out that opens the size wheels | tap a corner, then a button | any of the page's four corners | — |
| Edit mode | Move widgets by whole cells, resize by edge handles | long-press a widget, drag; drag a handle | on the grid | — |
| Status bar | Bare: no badge, no chips; stats centred and reversed | — | — | — |
| Keyboard | Closed on entering by default | — | — | — |

## Default extra keys row, left → right

One row of seven keys, shipped as the default so a fresh install needs no properties file. A
second page ships empty and is dropped. The row sits directly above the keyboard and shows on all
three places by default (it is a per-place, per-orientation setting: bottom, left, right, hidden).

| # | Key | Tap | Swipe up |
|---|-----|-----|----------|
| 1 | Keyboard | Show or hide the keyboard | Cycle the keyboard form: docked → floating → split |
| 2 | Mouse | Mouse mode on or off | — |
| 3 | Widgets | Go to the Widgets place | — |
| 4 | Terminal | Go to the Terminal place | — |
| 5 | Display | Go to the Display place | — |
| 6 | Split | Split the pane | New window |
| 7 | Sessions | Open the session browser | New session |

Swipe left on any key jumps to the text-input page. The three wall keys are how a user leaves
the terminal without the status bar, which is the case the tour's return card now covers.

## In-app keyboard, the keys the tour and the help point at

Layout `termux_launcher_qwerty.xml`, four rows:

| Row | Keys left → right |
|-----|-------------------|
| 1 | q w e r t y u i o p |
| 2 | a s d f g h j k l (offset half a key) |
| 3 | **Shift** (1.5 wide) · z · x · **c** · v · b · n · m · Backspace (1.5) |
| 4 | **Ctrl** (1.7) · **Alt** (1.3) · **Space** (4.0) · Compose (1.3) · **Enter** (1.7) |

Modifiers latch on a tap, so a chord is separate presses. Default chords: split = Ctrl+Alt+Enter,
new window = Ctrl+Alt+C, new session = Ctrl+Alt+Shift+C.

Space bar swipes:

| Direction | Runs | Tour |
|-----------|------|------|
| Up | Command palette | 12 |
| Up-left | Previous window | 8 |
| Up-right | Next window | — |
| Down-left | Previous session | 7 |
| Down-right | Next session | — |
| Left / right | Cursor left / right | — |
| Down | Previous keyboard layout | — |

There is no Esc key: Esc is the hidden down-right swipe on `q`, and Fn+a.

## Gaps the help overlays should cover that the tour does not

- The extra keys row, including the three wall keys and the swipe-up secondaries.
- Every Display place control: start button, corner tab, scale rail, touchpad, app chips.
- Every Widgets place control: long-press menu, corner tab, edit mode, page dots.
- The status bar's peeking neighbour icons and the stat detail cards.
- Corner tabs on all four corners; the tour only ever glows the top-left one.
- The keybind hint row and the space bar's right-hand swipes.

## File references

Layout `app/src/main/res/layout/activity_termux.xml` (content column 74–82, status bar 87–195,
accessory stack 514–586, keyboard 598–730, rail 1018–1025, side columns 1038–1057).
Per-place defaults `app/place/PlaceLayoutStore.java` (apps row 150–154, extra keys 206–211,
keyboard on enter 225–233 and 315–321). Status bar order `TermuxActivity.java` 13175–13262,
gestures 14624–14697, `statusbar/StatusBarGesturePolicy.java`, `statusbar/StatusBarLensView.java`.
Corner zones `CornerZones.java` 26–45; pane tab `TerminalPaneController.java` 3129–3134,
3653–3663. Display `x11/X11PaneFrame.java`, `x11/DisplayScaleRailView.java`,
`x11/DisplayTouchpadView.java`, `x11/DisplayTouchpadPlacement.java`. Widgets
`wall/WidgetPaneFrame.java`, `launcher/widget/WidgetPaneView.java`, `WidgetPaneMenuPolicy.java`.
Extra keys `termux-shared/.../TermuxPropertyConstants.java` 413–420,
`terminal/TerminalActionDispatcher.java` 88–113, `ExtraKeysView.java` 696–790. Keyboard
`inapp-keyboard/src/main/res/xml/termux_launcher_qwerty.xml` 16–59. Tour targets
`tour/TourViewTargets.java`, run `tour/TourRun.java`.
