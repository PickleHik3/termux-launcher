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
    side-by-side split). On a Display page it lies over the display's top edge on a dark band.
  - Home has no pane, so Home shows the strip under either choice.
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
Redesign" (the artboard "v2 live, built for the narrow width"). Design only; nothing in this section
is built. Where it differs from "Decided" above, this section wins.

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
- **Current page:** the mark in the accent inside an accent ring, for every kind; a Linux app's icon
  keeps its colours inside the ring. The solid disc goes: beside Home's dots it read as one more dot.
- **Fold:** only when the row runs out of room, as built.
- **Spacing:** packed. Home's dots stand close together, the other marks on a narrower pitch than
  today's, with a gap between kinds; no cell is stretched to fill the row. The canvas's figures, for
  a 16dp mark: dots on a 12dp pitch, other marks on 22dp, 7dp between kinds.

### Touch

- The row never changes size. The first tap that only enlarged it and the two-second hold both go.
- At touch-down, with no travel needed: the marks near the finger zoom, the slots near it widen with
  the touched mark staying under the finger, and every folded stack opens. That layout is made once
  and held until the lift.
- The zoomed marks are taller than the row, so they draw over the pane's first text line on a ground
  of Global's glass; all of it is gone at the lift.
- A tap on a mark or on a reading acts at once. A lift after a slide goes to the page under the
  finger.
- **Title:** the name of the page under the finger shows at the row's right end, and the readings
  animate away to make room for it. When the finger is over the title's place, the title slides out
  past the right end and in from the left, as if the row were one continuous surface.
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
  and its slots widen under the finger, so the pill grows while touched.
- If Home's status bar is turned on, the pill stays above the dock and the title shows at the right
  end of that bar while the finger slides on the pill.

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
  row keeps its dark band there.
- Watching each window's foreground program is new work at rest, and the icons come from the bounded
  icon store, not a second copy: read `docs/agents/performance.md` first.
- `RowEnlargement` and the bubble (`MarksBubble`, `MarksBubbleLayer`) are what this removes.
