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

Until then: no work on the marks row's touch rules, its layout or the dock's page indicator.
