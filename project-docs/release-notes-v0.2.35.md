# Changelog — v0.2.35

## New

### App Drawer

- Added an app drawer accessible by swiping down on the app icons row.
- Added 3 app drawer layouts:
  - Vertical
  - Horizontal
  - Categories

### Widgets Page

- Added swipe-down gestures on the status bar:
  - Half swipe expands the status bar.
  - Full swipe opens the widgets page.
- Widget pages can be added, reordered and edited in place.

### Keybinds

- Added a display name field for custom keybinds.
- Display names now appear in the keyboard hints popup.
- Added a tmux-style prefix key: declare `leader ctrl+space` in
  `~/.termux/termux-launcher-bindings.conf` and every Ctrl+Alt shortcut also
  answers to the prefix followed by the same key.
- Reworked pane, window and session navigation so each level has its own chord:
  - `Ctrl+Arrow` moves between panes.
  - Prefix + Left/Right walks windows, prefix + Up/Down walks sessions.
  - Prefix + a number picks a window, prefix + Shift + a number picks a session.
- Holding Ctrl+Alt on a hardware keyboard now shows the same hints popup the
  in-app keyboard shows.

### Terminal

- Added scrollback search on the dock, with vim-style copy mode over the
  transcript.
- Moved every terminal prompt onto one in-app sheet, instead of system dialogs.
- Added session, window and pane renaming from an anchored chip.

### Launcher

- Simplified the launcher setup script.
- Added a new repository for hosting Termux Launcher-specific binaries.
- Added Fastfetch with GIF support.
- Added one switch for the launcher versus terminal-only use case.
- Added a battery-saving "Lazy mode" in Terminal & Status: the clock stops
  animating and the status readings sample less often, which takes the
  launcher's idle CPU use down to a fifteenth of what it was.

### Extra Keys

- Added a visual-style extra keys editor.

## Changes

- Refined dock animations so app icons now animate together with the dock.
- Improved window and session rename surfaces to look more like the Processes
  interface.
- Improved terminal font handling so that when the space next to a Nerd Font
  icon is empty, the glyph can draw across two cells.
- The launcher's chrome now takes its colours from the terminal colour scheme.
- Landscape is usable: the dock becomes a side rail, and the drawer is denser.
- Resizing panes lights the edge being resized instead of drawing a border, and
  a keybind that runs briefly names itself.

## Fixes

- Fixed dragging app icons into and out of folders.
- Fixed the touch surface around the clock widget.
- The system clock now opens only when the clock itself is touched.
- Changed volume key behaviour so volume keys control volume by default.
- This prevents Home from hijacking the volume keys.
- Existing users can change this behaviour in `~/.termux/termux.properties`.
- Closed several holes in the local API surface, which was reachable from the
  network in some configurations.
- The launcher no longer grows its memory use while the expanded status bar is
  open.
