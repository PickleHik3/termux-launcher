---
status: accepted
date: 2026-10-17
supersedes: 0003's "every place" scope (one layout still serves every window; Home has a layout of its own, and places are gone)
---

# One wall, two halves: Home drops the pane model, the Workspace keeps it

The launcher was three places on a pane wall — Home (widgets), Terminal, Display — every one of
them a bordered pane paged by the border drag, with a usage mode deciding which existed. The
developer rarely used the Display place, and a new user had to learn border drags before the
launcher did anything a launcher does. The X server already renders one root with its own window
manager, so "the Display" was never a thing to navigate to; it was a window that happened to be a
place.

We decided on one wall with two halves. **Home** is a conventional launcher: widget pages paged by
a plain swipe, no borders, no launcher status bar, the system bar as any launcher shows it, a dock
without the extra keys, and the keyboard with its own remembered state. The **Workspace** is
everything the terminal was: the current session's windows, then the display windows, under the
full chrome, paged by the border drag. A display is one whole X display shown as a window, started
on demand; a Linux app tapped in the drawer opens or raises it. The usage mode and the Display
switch retire, a Start-in setting (Home, or Workspace) replaces Terminal alone, and the seam between
the halves is continuous: a swipe past the last Home page reaches the first window, a drag back
before the first window returns Home, and the deliberate doors (the Terminal entry, the long-press
item, the home press, the drawer's Home row) sit beside that.

Three alternatives were weighed. Two fully separate environments with no continuity would have
been the cleanest reading of "a normal home screen", but it throws away the one-row model the
people who liked the wall use, and the developer wanted both audiences served by one layout. A
Niri-style strip of panes (every pane its own column) would have dissolved the split trees that
the terminal's keyboard shortcuts, saved sessions and corner tabs are built on; the strip's unit is
therefore the window, with splits inside. Per-toplevel X11 windows in the strip would make Linux
apps first-class, but need a rootless server; a display window per X display gives most of the
feel today, and the drawer's Displays group lists the toplevels by name for raising.

Consequences: the Layout editor edits two layouts, Home's (dock rows and keyboard only) and the
Workspace's (normal and minimal). Style, Corners and Margin are Home's own on Home (seeded from
the Workspace's, amended by phase two slice 2); Minimal mode stays Workspace-only.
Appearance stays one look. The usage-mode, Display-switch and per-place preferences migrate once,
with no toggle back to the old wall. The word place leaves the glossary; page, Home page, window,
display window, Workspace and wall take over, and the saved sets called workspaces become Saved
sessions so that the environment can own the word. The parked full-screen Display spec
was answered by this decision: a full-screen display is a display window in minimal mode.
The spec is `project-docs/active/wall-rework-2026-10-17.md`.

## Amended 2026-10-17, same day: Home floats and the Home keyboard

Terminal from Home does not slide Home away after all. It opens a **Home float**: a real window of
the current session, drawn floating over the Home page it was opened on, born full-size, moved by
a glass titlebar and resized at a corner, minimised into a chip, and docked into the Workspace's
row by the titlebar's Dock. The alternative was to make it the scratchpad generalised, outside any
session; it was rejected so that chips, the drawer, the shortcuts and Saved sessions see one kind
of window. The launcher's status bar therefore did come to Home while a float existed, until
the 2026-10-19 amendment below.
The Home keyboard is transient by default: a gesture opens the command palette and the keyboard as
one overlay, and the Home layout's keyboard element is the **always on** toggle, which shrinks the
grid with today's reflow behind a snapshot. The capsule form of the status bar was tried in
phase two and removed; Home wore its own pills for a day, and since the 2026-10-19 amendment below
wears no status bar at all. The spec's §4, §5 and §12 carry the detail.

## Amended 2026-10-19: the seam never stops at an empty Workspace

A forward swipe past the last Home page crosses even when the Workspace has no window. The swipe
brings the empty Workspace in under the finger, under its own chrome, and a fresh shell opens as
window 1 only when the release commits to it, through the same start the Terminal entry makes into
an empty row; a swipe that springs back starts nothing. Since the 10-18 round sends Home's
launches into Home floats, an empty Workspace is common, and a seam that stopped there read as a
broken gesture. A sideways swipe that starts on a Home float's terminal pages as a swipe on the page
does; the float moves only by its titlebar (spec §2, §4).

## Amended 2026-10-19: Home wears no status bar

The developer's decision: Home shows no launcher status bar of any kind. The Home pills (a windows
pill and a stats pill standing in for the bar on Home) are removed, and so is the bar the first
Home float used to bring down: the Home layout keeps the status slot put away whatever the floats
do, and the widget grid starts below the system status bar's inset alone, as it did before the
pills. What the bar carried on Home moves to the floats: the focused Home float's titlebar carries
the CPU and RAM readings, drawn and inked as the status bar draws them (weather is dropped), and
minimised floats wait in float chips, a glass row centred just above the dock that moves with it
when a keyboard is up. The Workspace keeps its bar and its chips for the Home floats. The phase two
spec's Home pills slice records what was built and removed.

## Amended 2026-10-19: the display window is an ordinary window of the row

The developer's decision: the display stops being a section of its own. Until now the display
window was built as a page of the wall at the row's end, after every window, with a screen mark
peeking in at the status bar's right edge whenever the build carried a display. It is now a
window of the Workspace's row like the terminal windows: it joins the end of the current
session's row when it opens, stands among the windows at the place it is dragged to (its chip,
`window.move`), is stepped onto and off by the border drag and the window chips, and closes from
its chip's ×. There is no Display page and no peeking mark; one display window at most, as there
is one X display.

The wall still has two pages, Home and the Workspace. The display window is kept beside the
session's terminal windows rather than in their list (`DisplaySlot`, by the window it stands
after), because it has no panes and everything that walks a session's windows walks panes; while
it is the window in front, its frame stands as the Workspace's page and the pane host waits off
screen at its own size, so the terminal never resizes for it. The display keeps what is its own
as a place in front rather than a page: its keyboard form (an overlaying keyboard), its keys, its
help, its remembered keyboard, its touchpad and mouse mode; the bar over it wears the Workspace's
badge, chips and accent.

Closing the display window stops the display: the apps on it are asked to close first, as Stop
does, and the user is asked before that when an app is open, since closing them may lose work.
A display still starting, or waiting for its keyboard layouts, simply goes. The server therefore
runs only while its window is in a row. Saved sessions do not store the display window: a display
still running when the launcher comes back announces itself and its window rejoins the row at
the end. The display badge setting, which chose the peeking mark's glyph, is removed.
