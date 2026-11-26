# Status row and the Marks look (decided 2026-11-23)

Design only; nothing is built. Decided with the developer over four mock-up rounds on 2026-11-23.
Mock-ups: `~/reports/status-row-locked/` (the locked design), `~/reports/page-marks-3/` and
`~/reports/page-marks-2/` (the rounds before it), and the OpenDesign project "Page bar everywhere
and Overview" (round one, which also holds the Overview screen).

It extends the page bar (`page-glyph-switcher-2026-11-16.md`): the page bar's order, fold rule,
slide and bubble stay as they are. What changes is the look, the place, and the status bar.

## Decided

- **Marks look.** A second look for the page bar beside today's glyphs. No pill; each page is a
  mark in the legibility ink.
  - Home page: a dot. Terminal window: the prompt with an underscore. Display: a screen (only
    while a display runs). Assistant: a spark. Overview: two cards, at the left end, set apart.
  - The current page is a solid disc with its number or sign cut out.
  - A window asking for the user carries a burst in the attention colour, the only colour on the
    line.
- **Place: in the status bar, two choices in one setting.**
  - *Strip*: the status bar stays its own bar; the marks take the window chips' place and the
    readings stay on the right.
  - *In the pane*: the status bar becomes a thin row inside the top pane (top-left in a
    side-by-side split). Every page with a frame carries it the same way (2026-11-26, from the
    developer's phone report; this replaces the Display's dark band): a Terminal window's top
    pane, the Assistant's card, the Display's window and a Linux window each hold the row's room
    at their top (`PaneRowPolicy.pageRoomPx`, one number for all), the row lies in that room with
    the insets the frame's corner arc asks for (`PaneRowPolicy.rowRect`), the same marks and
    readings sizes and the hairline, and the page's own content starts below it. The Display's
    surface is laid out below the row, so the row never lies over the display's pixels and needs
    no band. While the row stands the Assistant's card stands where the Terminal's top pane does
    (`PaneWallPolicy.pageTopFrameInsetPx`); its sides and bottom keep their own inset. A slide
    between two of these pages keeps the row where it stands and places it on the arriving
    page's frame at the commit (built 2026-11-26, not yet seen on pong).
  - Home has no pane, so a Home that shows the status bar shows the strip under either choice,
    and wears it through every slide that can show that Home, swapping back to the row at the
    commit on a page with a pane.
  - A Home that shows no status bar (the fresh-install default) never wears the strip: the host
    stays the row at rest on Home (not shown there), and through a Home ⇄ Terminal slide in
    either direction, landed or sprung back, the row rides in and out with the Terminal's top
    pane at its rest offset, height and insets, with its hairline and marks, and nothing swaps
    at the crossing's start or at settle (`StatusPresentation.of`, `StatusPresentation.rowPage`;
    built 11-26, not yet seen on pong).
- **The thin row.** As thin as it can be at rest; it enlarges on touch.
  - Rest: 16dp, marks at 12dp, readings at 9sp, no divider.
  - Pressed: 32dp, marks at 18dp, readings spelled out. It grows over the pane on a solid ground;
    the terminal does not resize or reflow.
  - A tap that does not move keeps it enlarged for two seconds.
  - Slide, bubble (dropping below the row) and commit-on-release are the page bar's.
- **Leaves the bar:** the window chips and the + chip. Window names show in the bubble and on
  Overview's cards; New window is in Overview.
- **Overview.** One screen of cards for what runs inside the launcher: Terminal windows (splits
  shown), floating windows, the Display with its apps. Stills, not live views. Opened from the
  Overview mark.

## Not doing

- A rail above the extra-keys bar or the dock on workspace pages (costs a row on every page).
- The line on the pane's top edge. It saves at most one more row than the thin row and needs a
  plate under each group on solid panes over a contrasting wallpaper.

## Open

- Whether 12dp marks read on pong at arm's length; if not, the rest height goes to 18dp.
- Touch reach of the thin row: 32dp in the mock-up (6dp above the row, 10dp below), under the
  usual 48dp, because the terminal's first row must keep its own taps.
- The name "Marks" is a working name; no glossary entry yet.
- Overview against the Sessions drawer: replace it or stand beside it.
- Whether Home's page bar pill above the dock goes when the look is Marks.
- The expanded status pane has no place in the thin row yet.

The dp figures come from the mock-ups, not from a device.

## Parked: the marks row redesign (the developer, 2026-11-25)

The marks row is parked as built; the developer will revisit it. What they have in mind, so the next
round starts from it:

- **Condensed and left-aligned.** The marks sit packed at the row's start instead of spread across it.
- **One gesture.** A touch enlarges the row at once and the finger slides along it to the page it
  wants; lifting goes there. No separate "first tap enlarges, second tap acts" (today's thin-row rule,
  which made single taps on Terminal and Linux pages look dead on pong, 2026-11-25).
- **The same on Home.** By default Home pages hide the status bar, and the page switcher (in the marks
  look) stands above the app dock again, as in earlier builds, with the same touch-and-slide gesture.
- **The dock's page indicator gets redesigned.** Where it sits now it reads as Home's page dots (the
  Android norm), not as the dock's own pages.

Reopened on 2026-11-26 (next section), except the dock's page indicator, which stays parked.

## The marks row redesign (decided 2026-11-26)

Decided with the developer on 2026-11-26, working from their canvas "Launcher Top Status Bar
Redesign" (the artboard "v2 live, built for the narrow width"). Built on `feat/marks-redesign` on
2026-11-26 and not yet seen on a device; the points marked "not built" are decided only. Where it
differs from "Decided" above, this section wins.

### What it replaces

- The Marks look, as built. Glyphs stays the other look and Status bar › Page bar keeps its two
  choices.
- Not taken from the canvas: a colour per page kind, the page's name beside the current page at
  rest, the readings redrawn as a number over a bar, and the 26dp height. The readings stay as built.

### The marks

- **Home:** a dot. Only Home uses dots.
- **Terminal window:** a rounded square with the window's number cut out of it, on every window at
  rest, not only the current one. The number is centred on its own shape, not on the font's metrics.
- **Assistant:** the spark. **Overview:** its mark at the left end, set apart; its drawing must not
  read as a folded stack.
- **Linux app** (a Linux window, or a Display page bound to an app): the app's own icon in its own
  colours, the one exception to the ink. Where no icon is found, the screen mark. A Display page
  bound to no app is the screen mark.
- **Full-screen program:** while a Terminal window's focused pane runs a full-screen program that has
  a glyph, that glyph stands in place of the numbered square, in ink.
- One mark per window, never one per pane.
- **Ink:** the legibility ink, with the scheme's accent for the current page and the page under the
  finger. The attention burst is unchanged.
- **Current page:** the mark in the accent, for every kind, with nothing drawn around it; a Linux
  app's icon keeps its colours and is at full strength while every other app icon is dimmed to 60%
  (the ring was built first and removed on 2026-11-26 after it was seen on the phone). The solid
  disc went earlier: beside Home's dots it read as one more dot.
- **Fold:** only when the row runs out of room, as built.
- **Spacing:** packed. Home's dots stand close together, the other marks on a narrower pitch than
  today's, with a gap between kinds; no cell is stretched to fill the row. The canvas's figures, for
  a 16dp mark: dots on a 12dp pitch, other marks on 22dp, 7dp between kinds.

### Touch

- The row never changes size. The first tap that only enlarged it and the two-second hold both go.
- At touch-down, with no travel needed: the touched mark stays exactly where it is, the marks after
  it spread out at the touch pitch toward the row's end with every fold opened, the marks before it
  keep their room in equal cells, and the marks near the finger zoom. That layout is made once and
  held until the lift; the focus follows the finger's own position.
- No glass behind the marks: the zoomed marks are drawn over the row in its own inks, a little
  taller than it where they must be, and are gone at the lift. (The glass ground and the layout
  shifted onto the row were built first and changed on 2026-11-26 after the developer used them.)
- A tap on a mark or on a reading acts at once. A lift after a slide goes to the page under the
  finger.
- **Title:** the name of the page under the finger stands in a small glass capsule at the row's right
  end (Global's glass over the stored backdrop, never a Live picture), and it cross-fades with the
  readings: one progress value, so the name is never at full strength over readings still visible.
  When the finger is over the title's place, the capsule slides out past the right end and in from
  the left, as if the row were one continuous surface. Wherever the capsule stands, a mark whose
  centre lies under it is not drawn: the title has priority.
- **Reach:** the terminal's first text line stays the terminal's. The row keeps Android's Back swipe
  off itself, so a slide that starts on the leftmost marks is the row's.
- Unchanged from the build: a move across the row before a slide starts gives the touch up, the
  finger may wander across the row once sliding, and sliding back to the mark the touch began on
  chooses nothing.

### Places

- The thin row is the default place on a new install and after the 2.0 reset. The strip stays as the
  other choice, with the same marks and the same touch.
- **Home:** the status bar is hidden by default, and the page bar stands above the dock in this look:
  a small glass pill fitted to its marks with no spare room, centred, with no title. Its marks zoom
  and its marks spread under the finger, so the pill's own glass widens at its end to hold them,
  once at the touch-down and once at the lift.
- If Home's status bar is turned on, the pill stays above the dock and the title shows at the right
  end of that bar while the finger slides on the pill.

- **A hairline** one pixel thick runs under the thin row, in the row's ink at low strength, so the
  marks and readings do not sit bare at the pane's top edge. Not on the strip or on Home; on every
  page that carries the row, the Display's included.
- **In a split** (decided 2026-11-26, built 2026-11-26 on marks/split-row; not yet seen on pong).
  Two columns: one row across the panes that touch the top, the marks at the left end and the
  readings at the right end, so the panes' first text lines line up. Three or more columns: the bar
  leaves the panes and stands above them at the row's own height, with the hairline between it and
  the terminals. Each pane's top already gives the row its room, so this is meant to move the
  panes' frames without resizing a terminal. Place = In the pane only; rows-only splits keep the
  row in the top pane.

### What the build chose where this section was silent

- A Display or Linux window with no icon is a filled screen, with its number cut out when there are
  several. Overview's mark is two upright cards side by side.
- A bar down a side edge shows no title.
- Second look (2026-11-26, from the first phone run): the finger is held to the span from the first
  mark's centre to the last mark's for focus and every mark's size, so past either end the end
  mark stays focused at its full size and nothing moves (`MarksTouchLayout.clampedPos`); the
  title's side follows the real finger and the focused mark. The title capsule's ground strip is
  cut to the capsule's round rect by the ground itself (`GroundUnderGlass.round`), where it was a
  flat rectangle behind the rounded glass. The status row is measured at its rect's size by the
  host, so the marks are laid out once; the strip's layout params gave the row a second height
  that the host's own placement then replaced on every pass.
- In a split: before this, only the top-left pane held the row's room, so the other columns' first
  lines stood a row higher. Now every pane that touches the window's top holds it
  (`StatusRowPane.alongTop`), and "columns" counts those panes (`PaneRowPolicy.splitRow`): two
  columns where one is split into rows is still two, and columns inside the upper row of a stacked
  split count as columns. The row's ends clear each end pane's own corner arc; over the band they
  clear none (the cards start below it) and keep the 10dp and 16dp insets. Under the band a pane's
  view keeps its size and its content's place; only its card (outline, rim, glass clip, bezel)
  starts a row lower (`PaneContentFrame.setCardTopPx`), so no terminal is laid out again. Floats
  and Linux panes hold no room, as before.

### Window controls and the name chip (decided 2026-11-26, after the second pong look)

One way to close, minimise, maximise and move what runs in the launcher, with no extra margin and
nothing drawn inside a pane.

- **A window's controls are on its mark.** Touch a window's mark (a Terminal window, a Linux window,
  a Display) and keep still for about 400 ms: the title's place in the row becomes the controls,
  Minimise, Maximise, Move, Close, in that order with Close at the far right; slide onto one and
  lift. A touch that moves before then is the page-choosing slide, unchanged; lifting anywhere but
  on a control does nothing. With the Glyphs look, holding a window chip does the same. Home,
  Overview and Assistant marks are pages, not windows: holding them opens nothing.
- **Minimise** takes the window out of the row; it waits in Overview as its card and comes back from
  there (this settles "Minimise under Marks has no way back").
- **Maximise** fills the whole screen with the page: no row, no dock, no system bars. A swipe down
  from the top edge shows the system bars and the row for a few seconds (Android's transient bars);
  Maximise again restores.
- **Move** is for Linux windows: it lifts the window to carry it onto Home as a Float (what the rim
  tab's "Hold to move" did). **Scale** is a fifth control for Linux windows only (Minimise, Maximise,
  Move, Scale, Close), opening the same Scale rail for that window (decided 2026-11-27). With Scale on
  the mark the Linux rim tab is retired; Dock stays in the Float's handle menu.
- **Close.** A Terminal window closes at once when every pane is at an idle shell prompt (its
  foreground process is the shell) and asks first when a pane runs anything else. A Linux window
  closes its app. A Display page closes, asking first while its desktop is still running.
- **Panes in a split** have no mark: hold a pane's bottom-right corner arc (the arc itself, about
  32dp) for Close, Maximise (the pane fills its window; again restores the split) and Move (drag it
  into another slot). In a split the pane tab offers only these three. A window with one pane
  keeps its own corner tab as it was (Appearance, Minimal, tiling, Settings, Help, More; decided
  2026-11-27 once it showed that tab holds those doors).
- **Floats** on Home keep their title bar (drag, Minimise, Maximise, Close) as they are.
- **Name chip** (an experiment for the next pong build; kept only if the developer likes it): at
  rest, the current window's mark widens into a chip holding its number and its name, cut with an
  ellipsis past about 14 characters, at its place among the marks; the marks after it move right and
  the row folds into stacks sooner. On every page that carries the row, Strip and In the pane; never
  on Home's pill. While a mark is held, the name stays beside the controls.

### Open

- Whether a number reads inside a 12dp square on pong at arm's length; the 18dp fallback above stands.
- A status bar down a side edge has no right end: where its title shows.
- Whether Android's Back swipe is what takes the leftmost marks today; it fits the code and is not
  proved on pong.
- The ground over a Linux window: Global's glass with Live blur on is unproven there (below).

### Notes for the build

- Live blur records what is beneath a surface (`LiveBackdrop`), and a terminal pane is an ordinary
  view, so with Live blur on the zoom ground can blur the terminal text under it. A Linux window under
  the Wayland Display is a `TextureView`, which the recording does not skip by rule, but nobody has
  checked that it appears in one. The X11 Display is a `SurfaceView`, which a recording must skip: the
  display is laid out below the row's room, so the row lies over the window's own glass, never over
  the surface.
- Watching each window's foreground program is new work at rest, and the icons come from the bounded
  icon store, not a second copy: read `docs/agents/performance.md` first.
- `RowEnlargement` and the bubble (`MarksBubble`, `MarksBubbleLayer`) are what this removes.
