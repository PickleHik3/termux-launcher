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
Workspace's (normal and minimal), and Style, Corners, Margin and Minimal mode are Workspace-only.
Appearance stays one look. The usage-mode, Display-switch and per-place preferences migrate once,
with no toggle back to the old wall. The word place leaves the glossary; page, Home page, window,
display window, Workspace and wall take over, and the saved sets called workspaces become Saved
sessions so that the environment can own the word. `project-docs/active/display-fullscreen/PARKED.md`
is answered by this decision: a full-screen display is a display window in minimal mode.
The spec is `project-docs/active/wall-rework-2026-10-17.md`.

## Amended 2026-10-17, same day: Home floats and the Home keyboard

Terminal from Home does not slide Home away after all. It opens a **Home float**: a real window of
the current session, drawn floating over the Home page it was opened on, born full-size, moved by
a glass titlebar and resized at a corner, minimised into a chip, and docked into the Workspace's
row by the titlebar's Dock. The alternative was to make it the scratchpad generalised, outside any
session; it was rejected so that chips, the drawer, the shortcuts and Saved sessions see one kind
of window. The launcher's status bar therefore does come to Home, but only while a float exists.
The Home keyboard is transient by default: a gesture opens the command palette and the keyboard as
one overlay, and the Home layout's keyboard element is the **always on** toggle, which shrinks the
grid with today's reflow behind a snapshot. The capsule form of the status bar is agreed and
deferred. The spec's §4, §5 and §12 carry the detail.
