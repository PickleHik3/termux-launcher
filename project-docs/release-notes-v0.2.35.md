# Changelog — v0.2.35

## New

### App Drawer

- Added an app drawer accessible by swiping down on the app icons row.
- Added 3 app drawer layouts:
  - Vertical
  - Horizontal
  - Categories
- Categories can be corrected per app, and a Games category joins the set.
- The drawer can be sorted by an on-device AI model, or through any AI chat app
  you already use via a copy-paste prompt. Results land in a hand-editable
  `~/.termux/app-categories.conf`, and your own edits always win.
- A robot glyph in the status bar shows while an AI model is loaded, with a
  countdown to its idle unload.
- Folders are shared between the dock and the drawer: drag an app onto another
  to merge, drag one back out to remove it, rename in place. Folders hold up to
  36 apps and can be placed anywhere in the drawer.

### Widgets Page

- Added swipe-down gestures on the status bar:
  - Half swipe expands the status bar.
  - Full swipe opens the widgets page.
- Widget pages can be added, reordered and edited in place; long-press a widget
  to move, resize or remove it.

### Keybinds

- Added a display name field for custom keybinds (`map --label "…"` in
  `~/.termux/termux-launcher-bindings.conf`).
- Display names now appear in the keyboard hints popup.
- Added a tmux-style prefix key: declare `leader ctrl+space` in the bindings
  file and every Ctrl+Alt shortcut also answers to the prefix followed by the
  same key.
- Reworked pane, window and session navigation so each level has its own chord:
  - `Ctrl+Arrow` moves between panes.
  - Prefix + Left/Right walks windows, prefix + Up/Down walks sessions.
  - Prefix + a number picks a window, prefix + Shift + a number picks a session.
- Keybind hints now light up in the extra-keys A-Z row while a prefix is held,
  and `?` opens the full keybind table.
- Holding Ctrl+Alt on a hardware keyboard shows the same hints popup the in-app
  keyboard shows.

### Terminal

- Added scrollback search on the dock, with vim-style copy mode over the
  transcript (`hjkl`, `v`/`V`/`Ctrl-V` selection, `y` to copy).
- Moved every terminal prompt onto one in-app sheet, instead of system dialogs.
- Added session, window and pane renaming from an anchored chip.
- Renamed Hints to Quick select and reshaped it to match the search bar.
- Added kitty graphics Unicode placeholders, enabling images inside tmux and
  Neovim image plugins.
- The terminal name is configurable: set `terminal-term = xterm-kitty` in
  `~/.termux/termux.properties`.
- A commented `~/.termux/termux.properties` is now seeded on install, so the
  launcher's properties are discoverable without a download.

### Nerd Fonts

- Nerd Fonts can now be used in the in-app keyboard and Extra Keys.
- The status bar now uses Nerd Fonts bundled with the app.

### Lazy Mode

- Added an experimental Lazy Mode designed to reduce idle resource usage.
- Enable it from **Settings → Terminal & Status**.
- If testing goes well, Lazy Mode is intended to become the default in a future
  release.

### Launcher

- Simplified the launcher setup script.
- Added a new repository for hosting Termux Launcher-specific binaries; every
  install is verified against a pinned checksum.
- Added Fastfetch with GIF support.
- Added `setup-nvim`, a Neovim distro chooser (AstroNvim by default, or NvChad,
  LazyVim, kickstart or stock) themed from your wallpaper palette.
- Added one switch for the launcher versus terminal-only use case, in
  **Settings → Launcher & apps**.
- Landscape is now usable: the dock becomes a side rail on the edge you pick,
  and the drawer is denser.
- The whole interface can follow the terminal colour scheme
  (`~/.termux/colors.properties`) instead of the wallpaper palette, with
  per-token overrides in `~/.termux/launcher-theme.properties`.
- Rebuilt the weather card around the forecast, with a greeting on arrival and
  an optional Fahrenheit unit.
- The flip clock animates seconds on their own pair, with a metadata cell
  beside the digits.

### Extra Keys

- Added a visual-style Extra Keys editor: tap a key to edit it, hold and drag
  to move it, with macros and swipe-up actions per key.
- Added a glyph picker behind the label fields, searchable across all 10,512
  bundled Nerd Font icons.
- The refined key row now ships as the built-in default — no setup script
  needed, and your own `extra-keys` property still wins.

## Changes

- Reworked in-app notifications: every stock toast is replaced by one quiet
  chip in the top-right that you can act on, and mirrored notification cards
  can be swiped away.
- New motion system: panes, windows and the drawer move with consistent
  physics, and every terminal pane sits on its own glass slab.
- Refined dock animations so app icons now animate together with the dock.
- Resizing a pane lights the edge being resized instead of drawing a border,
  and a keybind that runs briefly names itself.
- On the in-app keyboard, the pressed key now dominates visually and hint
  lighting fades out.
- Improved window and session rename surfaces to look more like the Processes
  interface.
- Improved terminal font handling so that when the space next to a Nerd Font
  icon is empty, the glyph can draw across two cells.

## Fixes

- Fixed dragging app icons into and out of folders.
- Fixed the touch surface around the clock widget: the system clock now opens
  only when the clock itself is touched.
- Changed volume key behaviour so volume keys control volume by default,
  preventing Home from hijacking them. Existing users can change this in
  `~/.termux/termux.properties`.
- A hand-written `~/.termux/colors.properties` is respected again (#11), and
  the Style entry is back in the terminal long-press menu when Termux:Styling
  is installed.
- Fixed page swipes on the pinned apps row landing back on the page they came
  from.
- The launcher no longer grows its memory use while the expanded status bar is
  open.
- Reopening Settings from a retained task no longer crashes after an upgrade.

## Security

Findings from an external review of the launcher's local API surface, all
fixed:

- Browser pages and other network origins can no longer reach the local API:
  CORS is granted to loopback origins only, and the `Host` header is checked
  against the bound address to close DNS rebinding.
- Media paths in inference requests are validated, so a request can no longer
  read arbitrary files or pivot into the LAN.
- Notification history is opt-in and off by default; turning it off deletes
  what was captured.
- LAN mode now ends itself after 12 hours, rebinding to loopback and rotating
  the token.
- The setup script verifies everything it installs against pinned checksums
  and fails closed on a mismatch.
