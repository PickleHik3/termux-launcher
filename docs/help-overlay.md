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
and the boxes of controls inside the band unless nothing fits otherwise (`HelpLeaderRouter.arrange`).
A second page starts only when both columns are full, which the default layouts never reach.

From the phone, same day: leaders are back, drawn in the pair's colour (straight when the card
faces its box, one elbow when beside it); cards sit where the eye looks for them (`HelpLeaderRouter.arrange`:
under a control above the wall, over one below it, beside one inside it, cascading a little along
an edge, sliding away from the control when the spot is taken); and the keyboard is two hints again, the prefix keys (Ctrl and Alt,
with the chords they start) and the space bar (its swipes).

## Fourth pass (2026-09-15): help by topic

From issue #36 (the revised onboarding review), landed on top of the third pass above.

- **Terminal opens on a chooser; Home and Display still open straight into the overview.**
  `HelpPresentationModel.TERMINAL_DEFAULT_MODE` is `TOPICS`; `WIDGETS_DEFAULT_MODE` and
  `DISPLAY_DEFAULT_MODE` are both `OVERVIEW` — Display's value is a placeholder for the screen
  check the spec still owes, not a considered choice.
- **Groups.** Every catalogue entry (`HelpTopics.Entry`) carries a `Group`: Everyday, Keyboard,
  Multitasking. The chooser lists Everyday first. Display and Home have no Keyboard or
  Multitasking topics at all — their catalogues are Everyday-only.
- **Show basics / Show all.** `showBasics()` filters the chooser to the Everyday group;
  `showAll()` drops into the overview from its first page. Neither is a gesture on the launcher.
- **Overview paging.** The "1 / 2" pill is replaced by Previous and Next (`previous()`, `next()`,
  each clamped at its end) and a section label naming the group of the page's first entry
  (`sectionLabelRes()`), drawn by `HelpOverlayView` in place of the pill.
- **Close.** An explicit close request (`close()` → `Effect.CLOSE`) alongside the outside-tap
  dismiss the third pass already drew (`help_close` pill).
- **Show gesture.** `showGesture()` asks the renderer for a finite demonstration over the
  selected topic's control, and leaves help open (`Effect.demonstrate`), only when that control
  is on screen (`canShowGesture()`). The spec has the renderer draw a static directional cue
  instead of the finger trace when the system animator scale is zero (`ReducedMotion`); the trace
  itself is the tour's, drawn through the shared `TourFingerPainter`.
- **Try it.** `tryIt()` closes help and hands the topic's lesson id to practice
  (`Effect.practice`), only for a topic with both a measurable control and a lesson
  (`canTryIt()`). `TourController.startPractice(lessonId)` runs that one lesson alone: Done/End
  practice instead of the lesson's three buttons, clears on the lesson's own signal, and writes
  nothing to the tour's prefs, so practising can never finish, restart or skip the stored run.
- **Reading never acts.** `open`, `selectTopic`, `backToTopics`, `showBasics`, `showAll`,
  `previous`, `next` and `remeasure` all answer `Effect.NONE` — browsing help changes only the
  model's own state, never the launcher's.
- **Colour identity from the catalogue.** `overviewColor` derives a box's colour from the entry's
  fixed position in the place's whole catalogue (`identityIndex`, `HelpTopics.sizeFor`), not from
  whichever subset happens to be measurable this pass, so a control keeps its colour with the
  keyboard up or down. Topic mode instead wears the place accent (`topicHighlightColor`).
- **Missing targets are explained, never redirected.** `highlightTargetId()` answers null rather
  than another control's rect when the topic's own control cannot be measured; `revealRes()`
  supplies the catalogue's "how to bring it back" line and `relatedTopicId()` an optional related
  topic — never a substitute rect.
- **Palette and Settings entries.** The spec adds a Help entry to the command palette (app
  category, tool `app.open_help`) and to Settings beside Replay the tour (`EXTRA_SHOW_HELP`), both
  reaching the activity's one `showHelpOverlay()`.
- **Accessibility.** The spec asks for focus and a topic announcement when help opens, and a
  label on every button (Close, Previous, Next, Show gesture, Try it); the panel takes focus and
  announces the topic title, and every button carries a content description.
- **Overview stays the third pass's.** `keys` and `corners` are topics in the chooser only: the
  overview keeps per-key labels on the extra keys row with no box round it, and no corner-tab card
  (the second pass dropped that hint because the reader reached help through that tab).
- **Hands off to the tour.** The `corners` topic's lesson is `find_help`
  (`TourRun.FIND_HELP` — see `docs/first-boot-tour.md`), so "Try it" there and the tour's
  own first lesson are the same three taps.

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

Superseded by the topic catalogue (fourth pass): Sessions badge → `sessions`; Window chips and +
→ one `windows` topic (issue #36 asks the highlight to cover both); Status bar → `status`; Stats
cluster → `stats`; Pane corner → `corners`; Divider → `divider`; Dock → `dock`; A–Z row → `az`;
Extra keys → `keys`; Ctrl + Alt → `prefix` (the basics) plus `shortcuts` (the full chord
reference, moved to Multitasking); Space bar → `space` (the palette gesture only). Terminal opens
on the chooser now; this table is still what "Show all" renders.

#### Terminal topic catalogue (`help_topic_…`, verbatim from `strings.xml`)

| Id | Group | Purpose | Action | Reveal |
|----|-------|---------|--------|--------|
| `dock` | Everyday | The dock holds your Android apps. | Pull it down for the app drawer. | Show the dock in the Layout editor to see it. |
| `az` | Everyday | The A–Z row picks an app by its first letter. | Slide along it, then drag up to the app you want. | Show the A–Z row in the Layout editor to see it. |
| `status` | Everyday | The status bar shows which place you are on. | Swipe along it to change place, or drag it down for the clock. | Show the status bar in the Layout editor to see it. |
| `stats` | Everyday | The status widgets show how the phone is doing. | Tap one for details. | Turn a status widget on in Settings to see it. |
| `corners` | Everyday | Every pane corner holds the controls for that pane, including this help. | Press a corner, then tap the ? button. | Press any corner of a pane to see its controls. |
| `sessions` | Everyday | Sessions group your windows. | Tap the badge to switch. | Show the status bar in the Layout editor to see the badge. |
| `windows` | Everyday | Windows are tabs for your terminals. | Tap one to switch, or tap + for a new one. | Show the status bar in the Layout editor to see your windows. |
| `keys` | Keyboard | The extra keys row carries the keys a phone keyboard leaves out. | Tap a key, or swipe up on it for its second key. | Show the keyboard to see this key. |
| `prefix` | Keyboard | Ctrl and Alt start the key chords. | Hold them both to see what every other key does. | Show the keyboard to see these keys. |
| `space` | Keyboard | The command palette finds any action by name. | Swipe up on the space bar to open it. | Show the keyboard to see the space bar. |
| `divider` | Multitasking | The divider sets how much room each pane gets. | Drag it to give one pane more. | Split a pane to get a divider. |
| `shortcuts` | Multitasking | Key chords open a split, a window or a session without leaving the keys. | Hold Ctrl and Alt, then press the key for the one you want. | Show the keyboard to use the chords. |

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

Superseded by the topic catalogue (fourth pass): App chips → `windows` (the display-apps
strings); Stats cluster → `stats`; Status bar → `status`; Scale rail → `scale`; Touchpad →
`touchpad`; Start display → `start`. Corner tab has no catalogue entry — the fourth pass drops it
as a topic on Display.

#### Display topic catalogue (`help_topic_…`, verbatim from `strings.xml`)

| Id | Group | Purpose | Action | Reveal |
|----|-------|---------|--------|--------|
| `status` | Everyday | The status bar shows which place you are on. | Swipe along it to change place, or drag it down for the clock. | Show the status bar in the Layout editor to see it. |
| `stats` | Everyday | The status widgets show how the phone is doing. | Tap one for details. | Turn a status widget on in Settings to see it. |
| `windows` | Everyday | These chips are the apps running on the display. | Tap one to bring it forward, then tap × to close it. | Start the display and open an app to see its chip. |
| `start` | Everyday | The Linux display runs desktop apps beside your terminal. | Tap to start it. | Stop the display to see this button again. |
| `scale` | Everyday | The scale rail sets how large the display is. | Drag it to resize the display. | Start the display to see the rail. |
| `touchpad` | Everyday | The touchpad moves the pointer on the display. | One finger moves and taps, two fingers scroll, three fingers switch windows. | Turn Mouse mode on to see the touchpad. |

### Widgets

| Box | Measured from | Title | Lines | Only when |
|-----|---------------|-------|-------|-----------|
| Status bar | as Terminal | Status bar | Swipe along it to change place. Drag down for the clock and notifications. | always |
| Corner tab | as Display | Corner tab | ⚙ opens layout settings. ✎ edits your widgets. ? shows this help. | always |
| A widget | the first visible widget host in `WidgetGridView` | A widget | Long-press to move or resize it. | ≥1 widget |
| Empty space | the largest empty cell region of the grid | Empty space | Long-press to add a widget or another page. | any empty cell |

Superseded by the topic catalogue (fourth pass): Status bar → `status`; A widget → `widget`;
Empty space → `empty`. Corner tab has no catalogue entry, as on Display. The catalogue enum stays
`PaneWallPage.WIDGETS`; the copy itself already says Home, not Widgets (`ab869d92`), matching
`CONTEXT.md`'s place name.

#### Home topic catalogue (`help_topic_…`, verbatim from `strings.xml`)

| Id | Group | Purpose | Action | Reveal |
|----|-------|---------|--------|--------|
| `status` | Everyday | The status bar shows which place you are on. | Swipe along it to change place, or drag it down for the clock. | Show the status bar in the Layout editor to see it. |
| `widget` | Everyday | A widget shows information from an app right on Home. | Long-press it to move or resize it. | Long-press an empty cell to add your first widget. |
| `empty` | Everyday | Empty space is where a new widget goes. | Long-press it to add a widget or another page. | Move a widget aside to make room. |

## Code shape

New package `com.termux.app.help`:

- `HelpTopics` — the one catalogue of every control help explains, pure: a stable id per place
  (the id `HelpTargets` measures the control under), a `Group`, purpose/action/reveal string res
  ids, an optional lesson id from `TourRun.lessons()`, an optional related topic id, and an
  `identityIndex` that pins the entry's overview colour. `forPlace`, `entry`, `sizeFor`, `all`.
- `HelpPresentationModel` — what help is showing and what it wants done about it, pure: a mode
  (`TOPICS`, `TOPIC`, `OVERVIEW`), the selected topic, the overview page and the basics filter.
  Commands (`open`, `selectTopic`, `backToTopics`, `showBasics`, `showAll`, `previous`, `next`,
  `showGesture`, `tryIt`, `close`, `remeasure`) return an `Effect` (`NONE`, `DEMONSTRATE`,
  `CLOSE`, `CLOSE_AND_PRACTICE`); queries answer what to render, including the one target to
  highlight and its colour. Reads `HelpTopics`; renders nothing and measures nothing itself.
- `HelpOverlayView` — scrim, boxes, in-place key labels, cards, leader lines, paging pill, close
  pill; consumes touches; `show(place)`, `dismiss()`, re-measures on global layout like
  `FirstBootTour.obtainOverlay` does. Renders `HelpPresentationModel` by mode (fourth pass).
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
