# Help overlay — spec (approved on the review page 2026-09-14)

A help layer the user opens from a **?** button on every corner tab. It dims the screen, boxes
each visible control of the place the wall rests on, and connects each box to a short hint with
a leader line that crosses nothing. It is measured live when it opens, so anything not on screen
is left out. The first-boot tour (`docs/first-boot-tour.md`) teaches gestures once; this overlay
is there every time. Wireframes and the inventory it is built from: `docs/help-overlay-inventory.md`
and the review page `.lavish/help-overlay-inventory.html` (sections "Help overlay wireframes").

## Decisions

- **Entry.** A `?` button joins every corner tab: the terminal pane's
  (`TerminalPaneController`, one button on a lone pane → two; three in a split → four; two when
  maximised → three), the Display page's (`X11PaneFrame`: power, cog, ?) and the Widgets page's
  (`WidgetPaneFrame`: cog, pencil, ?; the grid-size read-out while editing keeps ? too). Tapping it
  dismisses the tab and opens the overlay for the current place.
- **Coverage.** Every control the place owns, tour-taught or not. Chrome shared by all three
  places (dock, A–Z row, extra keys, stats) is taught on the **Terminal** overlay only. The
  Display and Widgets overlays carry their own controls plus the status bar. (The Display stats
  hint is kept unless the developer says otherwise.)
- **Measured live.** Candidate controls are measured when the overlay opens and on every layout
  pass while it is up, the way `tour/TourViewTargets` does (`findViewById`, on-screen check,
  rect in overlay coordinates, keyboard key rects through the activity's key probe). A control
  that is gone, zero-sized or off-screen is omitted with its hint: a disabled stat, the closed
  keyboard, the touchpad with mouse mode off, the + on Display, the badge on Widgets. Nothing is
  positioned by hand.
- **Boxes.** A dashed border inset 2 dp inside each measured rect, rounded to the control's own
  corner. Width-spanning rows (dock, A–Z) are boxed whole. The extra keys row is **not** boxed
  as one: see below.
- **Extra keys.** Each key is labelled in place on its own cap: tap action on the first line,
  swipe-up secondary on the second, read from the row the user actually has (`ExtraKeysView`
  children and their key definitions, `TermuxTerminalExtraKeys` for what `tool:` ids do). Short
  product labels per tool id: keyboard.cycle_form "form", mouse.toggle "Mouse", wall.widgets
  "Widgets", wall.terminal "Terminal", wall.display "Display", pane.split "Split", window.new
  "window", session.browser "Sessions", session.new "session"; plain keys use their display
  glyph. Secondary line shown as "↑ <label>".
- **Hints.** One card per box: bold title and one or two short lines, product voice, no
  mechanism (AGENTS.md "User-facing text"). Cards live in the wall band, in two columns, in the
  vertical order of their controls. Same card surface and theme tokens as `TourOverlayView`,
  place accent, font scale respected.
- **Leader lines.** Orthogonal, at most two bends. A control above the wall sends its line
  straight down to its card; a control below sends it up from an end cap through an **edge lane**
  (a 12 dp gutter each side, one line per lane); a control beside the wall connects sideways.
  The router (`HelpLeaderRouter`, pure Java, unit-tested) checks every segment against every
  box, card and existing line and moves a card down a slot when a crossing would occur. Cards
  that will not fit go to a second page of the overlay (pill "1 / 2", tap flips) rather than
  overlap.
- **Chrome.** A scrim dims the screen. The overlay **consumes every touch** (unlike the tour)
  and closes on any tap outside a card, on Back, on a place change, and when the activity
  pauses. A "Tap anywhere to close" pill sits in the free band. Re-measure on every layout pass
  (rotation, keyboard, font scale, dock style); landscape rail and side columns come through the
  same measurer.
- **Out of scope.** Animation beyond a fade; gesture demonstrations; persistent state; a Settings
  entry; launcherctl tool.

## Second pass (2026-09-14, from the phone)

- No hint about the corner tab or the pane corners: the user reached the help through that tab.
- The status bar box spans the whole bar, not the peeking place icon at its end.
- A quick reference, not a manual: the windows chips and the + are one hint; the keyboard's
  bottom row (chords and space-bar swipes) is one hint; every line is as short as it can be and
  the cards are tighter. The tables below are superseded where they differ.

## Third pass (2026-09-14): colour pairs, one page

Each hint has its own colour, shared by the dashed box on the control and the border and title of
its card (`HelpPalette`: hues spread evenly from the place accent). That pairing is the whole link,
so no leader lines are drawn and nothing can cross; cards flow down two columns of the wall band
in screen order, each in the column nearer its control, yielding to the key labels, the footer
and the boxes of controls inside the band unless nothing fits otherwise (`HelpLeaderRouter.pack`).
A second page starts only when both columns are full, which the default layouts never reach.

## Per place: boxes and copy

Strings go in `strings.xml` under `help_…`. Titles bold, lines as given.

### Terminal

| Box | Measured from | Title | Lines | Only when |
|-----|---------------|-------|-------|-----------|
| Sessions badge | `terminal_sessions_indicator` | Sessions | Tap to switch sessions. | shown |
| Window chips | `TerminalWindowBar` chip strip | Windows | Tap to pick a window. Tap it again to show its × button. | terminal windows shown |
| + | `TerminalWindowBar.createWindowButtonView()` | New window | Tap + to open a terminal window. | shown |
| Stats cluster | `terminal_status_stats_cluster` (box only the visible children) | the visible stats, e.g. CPU · RAM · Weather | Tap one for details. | ≥1 stat visible |
| Status bar | anchor at a peeking place icon (`StatusBarLensView`) or the bar's end cap | Status bar | Swipe along it to change place. Drag down for the clock and notifications. | always |
| Pane corner | one corner zone of the active pane (`CornerZones`, 32 dp square) | Pane corners | Tap one for move, maximise, close and this help. (lone pane: Tap one for the pane's controls and this help.) | always |
| Divider | the split divider view | Divider | Drag to resize the panes. | ≥2 panes |
| Dock | `apps_bar_viewpager` / `dock_rail_scroll` | Dock | Pull down for the app drawer. (rail: Swipe off the rail for the app drawer.) | shown |
| A–Z row | `apps_bar_az_row` / `place_az_bar_top` / `place_az_bar_column` | A–Z row | Slide to filter your apps, drag up to one and let go. | shown |
| Extra keys | each `ExtraKeysView` key | in-place labels | tap / ↑ secondary | row shown |
| Ctrl + Alt | keyboard key rects "ctrl" and "alt" as one box | Key chords | Ctrl, Alt, Enter splits the pane. Ctrl, Alt, C opens a window. Ctrl, Alt, Shift, C opens a session. | keyboard up; read the bound chords, omit a line whose action is unbound |
| Space bar | key rect "space" | Space bar | Swipe up for the command palette. Swipe toward a corner for the next or previous window or session. | keyboard up |

### Display

| Box | Measured from | Title | Lines | Only when |
|-----|---------------|-------|-------|-----------|
| App chips | `TerminalWindowBar` chip strip | Display apps | Tap once to show its × button. | ≥1 chip |
| Stats cluster | as Terminal | | Tap one for details. | ≥1 stat visible |
| Status bar | as Terminal | Status bar | Swipe along it to change place. Drag down for the clock and notifications. | always |
| Corner tab | the tab that opened the overlay is dismissed; box the corner zone it came from | Corner tab | ⏻ starts or stops the display. ⚙ opens display settings. ? shows this help. | always |
| Scale rail | `DisplayScaleRailView` | Scale | Drag to resize the display. | shown |
| Touchpad | `DisplayTouchpadView` | Touchpad | One finger moves and taps. Two fingers scroll, pinch or right-click. Three fingers middle-click or switch windows. | mouse mode on |
| Start display | `x11_pane_start` | Start display | Tap to start the Linux display. | no display running |

### Widgets

| Box | Measured from | Title | Lines | Only when |
|-----|---------------|-------|-------|-----------|
| Status bar | as Terminal | Status bar | Swipe along it to change place. Drag down for the clock and notifications. | always |
| Corner tab | as Display | Corner tab | ⚙ opens layout settings. ✎ edits your widgets. ? shows this help. | always |
| A widget | the first visible widget host in `WidgetGridView` | A widget | Long-press to move or resize it. | ≥1 widget |
| Empty space | the largest empty cell region of the grid | Empty space | Long-press to add a widget or another page. | any empty cell |

## Code shape

New package `com.termux.app.help`:

- `HelpOverlayView` — scrim, boxes, in-place key labels, cards, leader lines, paging pill, close
  pill; consumes touches; `show(place)`, `dismiss()`, re-measures on global layout like
  `FirstBootTour.obtainOverlay` does.
- `HelpTargets` — the per-place candidate list and their measurement, reusing the `ViewFinder`
  idea from `tour/TourViewTargets` (do not couple to the tour's classes; copy the two helpers if
  needed).
- `HelpLeaderRouter` — pure: given overlay size, target rects with their side (ABOVE, BELOW,
  LEFT, RIGHT, INSIDE) and card sizes, returns card positions, polylines and page assignment
  with the no-crossing guarantee. Deterministic.
- `HelpCopy` — titles and lines from resources; extra key label table.
- Debug log tag `TermuxHelp` (mirror `tour/TourLog`).

Entry wiring: a `HELP` action on each of the three corner tabs, routed to one activity method
`showHelpOverlay()` that dismisses the tab and opens the overlay for `currentWallPlace()`.
Dismiss on Back through the activity's existing back handling, on `onWallPageSettled`, on
`onPause`.

## Acceptance

```sh
export JAVA_HOME=$HOME/.local/opt/jdk21 PATH=$HOME/.local/opt/jdk21/bin:$PATH
./gradlew :app:compileDebugJavaWithJavac
./gradlew :app:testDebugUnitTest --tests 'com.termux.app.help.*'
./gradlew --stop
```

Router tests must cover: the three default portrait layouts as synthetic rects produce no
crossing segment and no overlapping card; a missing target is omitted with its card; more cards
than fit go to page 2; each edge lane carries one line; a control with no free slot in its column
falls to the other column before it falls to page 2. Strings are one plain sentence each.

Git: commit on `feat/help-overlay` in small commits, never push, never touch `dev`, no devices
or emulators. Stop the Gradle daemon when done (this machine is short on RAM).
