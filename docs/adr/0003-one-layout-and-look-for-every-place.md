---
status: accepted
date: 2026-09-26
supersedes: the per-place scope of 0001 (sizes stay in the layout store; the store stops being keyed by place)
---

# One layout and one look for every place; places differ only in state

Home, Terminal and Display each kept their own layout (per orientation) and their own appearance
overrides. Switching places re-applied the arriving place's layout, look, status-bar height and
keyboard state at the moment the swipe was released. Measured on the HTC 5G Hub, that release
frame took 20–43 ms and skipped one or two vsyncs, and the dock visibly landed off and jumped.

We decided to keep one layout per orientation and one look, shared by all three places. Existing
setups migrate from each orientation's Terminal layout. What still differs per place is state,
never geometry or look:

- Whether the keyboard is up: Home is always closed; Terminal and Display each remember theirs.
- Minimal mode (Display and Terminal): the status bar shrinks to a strip, the apps bar and the
  keyboard go away, and the pane is maximised in either orientation.

Because the geometry is the same, the dock and keyboard can travel with the slide. Their positions
are a function of the wall's live offset, applied as transforms, and the terminal resizes once, at
settle.

The alternative was to keep per-place layouts and looks but pre-apply them before the slide
started. That keeps the flexibility, but it adds code and still pays a layout pass per switch,
and nobody was using the per-place differences deliberately.

Consequences: the Layout editor and the Appearance editor lose their place picker. Place-scoped
look keys (`PlaceLookPreferences.SCOPABLE`) and per-place layout entries in `PlaceLayoutStore`
need a versioned migration. The place-change handlers stop re-applying layout and look, and move
the remaining work to settle.

## Amended 2026-09-27: the content's room during a slide

"The terminal resizes once, at settle" stands, but the settle no longer changes the content's
room. Measured on pong (2026-09-27), holding the content at the room of the place being left left
a band of sharp wallpaper between the pane and the dock for the whole slide whenever the keyboard
retracted, and the settle then grew the pane, re-cut the widget grid and resized the X screen in
one frame — black, then grey, until the X server repainted.

The content is now laid out at the roomier of the two places from the slide's first frame
(`TermuxActivity#preRollTravelContent`): leaving a place whose chrome takes room from the content
— the terminal's keyboard, its dock padding — for one whose chrome does not, the room is given
back at once, under the keyboard the slide is about to move away. The terminal's grid and the
X screen keep their size until the settle, as before; the settle's geometry pass finds the room
already right, and what is left for it is the grid's one resize. Arriving somewhere that takes
room, the content is held at the roomier place as it always was and shrinks at settle.

While the wall travels, the terminal's rows are drawn where that resize is going to put them
(`TerminalView#setTravelDisplacement`, from `TerminalEmulator#predictRowsOnlyResizeShift`), so
the resize lands on rows that are already there, and the reflow is drawn under a brief frost that
thaws to the sharp rows — M1's frost-on-reflow, in the default mode. A travel toward a taller grid
also draws the transcript that resize will reveal, above the rows and where it will land, so the
room the terminal gains fills as it opens instead of on landing. The alternate screen's rows cannot
be placed ahead — the full-screen program repaints after the resize — so a keyboard swipe over one
is frosted from its claim until it lands (`TerminalView#holdTravelFrost`).

The wall lays out only the pages on screen (`PaneWallLayout`): a page parked off screen keeps
its last layout and is laid out again in the frame that brings it back. Because Home's room never
changes while Home is on screen, the widget grid is no longer re-cut by the terminal's keyboard;
because the Display page is only ever laid out at the room it rests with, the X screen is no
longer resized by a slide to or from it.
