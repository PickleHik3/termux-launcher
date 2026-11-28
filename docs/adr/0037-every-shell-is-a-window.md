---
status: accepted
date: 2026-11-27
amended-by: 0043 (§1: the Assistant is no window; it is the AI page left of Home 1)
---

# Every shell is a window

Spec: `project-docs/active/tiling-desktop-2026-11-27.md`. Glossary: **window**, **Workspace**,
**control bar**, **navigation mode**.

The launcher grew three nested groupings for terminals (a session holds windows, a window holds a
split of panes) and a fourth for Linux apps (a Linux window, one app each, placed among terminal
windows). A gesture meant different things depending on which level it landed on, terminals and
apps tiled and floated by different rules, and the Assistant and the X11 Display were page kinds of
their own. The developer wants one Linux-desktop experience built around terminals, in the shape
of Hyprland and niri, on a phone held in one hand.

## Decision

1. A **window** is one shell or one Linux app, the same kind of tile either way. The X11 Display and
   the Assistant are windows too. Panes, sessions and the Display and Assistant page kinds retire;
   a saved set of windows is a saved Workspace.
2. Windows belong to numbered **Workspaces** stacked one above another, with Home at the top and an
   empty Workspace always waiting at the end. Sideways moves among a Workspace's windows; up and
   down moves between Workspaces and Home.
3. Each Workspace has its own layout. A new one starts in the scrolling layout (columns, as niri);
   any can switch to dwindle (as Hyprland), where a split that would leave a window too small to
   use makes the new window a tab of the window in focus instead.
4. Any window can float above its Workspace's tiles, or over a Home page; Pin shows a float on
   every Workspace.
5. An upgrade from v1 starts fresh: Home plus Workspace 1 with one terminal.

## Considered

- **Keep a terminal window as a split of panes.** Shells stay bundled and page together, but there
  are two levels of tiling for terminals and one for apps, and every gesture has to say which.
- **Fixed Workspaces 1–10 (Hyprland), or Workspaces side by side as the wall is today.** Fixed
  numbers give muscle memory but no order to swipe through; side by side collides with the
  scrolling layout, which already uses sideways.
- **Home behind every Workspace, or as its own side.** Behind hides the widgets once windows open
  and leaves the home press nowhere fixed to land; its own side gives the stack two axes that mean
  different things.
- **Keep splitting, or spill to the next Workspace, when dwindle runs out of room.** Splitting
  leaves slivers on a phone; spilling moves the user away from what they were doing.
- **Convert v1 layouts on upgrade.** Keeps open shells, but someone with many windows lands in
  many Workspaces; the release is a new start under a new name (ADR 0039).

## Consequences

- `TerminalPaneController`'s tree of panes inside a window, the session row, `PageKind` for Display
  and Assistant, and Overview's session rows are replaced, not extended.
- Every window kind wears the same glass, corners and focus rim; Linux windows gain their own
  Opacity (solid by default).
- Keeping a whole Workspace drawable while the user moves through the stack costs memory; only the
  Workspace in front and its neighbours hold surfaces, as `LiveNeighbourPolicy` does for windows
  today.
- Supersedes ADR 0025 (the Workspace is Terminal, Display and Assistant) and the window contract's
  "a session's window list holds terminal and Linux windows".
