# Changelog — v0.2.36

A big one: a rebuilt appearance editor, tappable links in the terminal,
redesigned shortcut hints, and a large memory cleanup that takes typical usage
from about 600 MB down to about 170 MB.

## New

### Appearance

- **The appearance editor is now a single page.** *Global* sets what every
  surface shares, *Fine tune* adjusts one surface at a time. Each row shows
  whether it follows Global (✓) or has its own value (↺). Nothing is applied
  until you tap **Done**.
- **Blur, opacity and grain are now one choice.** Pick a material — Solid,
  Glass or Frost — and how strong it should be. The individual numbers are
  still there if you want them.
- **Four ready-made looks:** Classic, Mist, Slate and Bare. Each preview is a
  small phone mock drawn over your own wallpaper, so you can see a look before
  you apply it. One tap applies it, one tap undoes it. A fifth **Custom** slot
  saves your own.
- **Pick a clock face by looking at it** — all six are drawn as themselves
  instead of listed by name.
- Rounded corners for the sessions indicator and the window pills, so the two
  chips finally match (#16).
- Rounded corners for the docked terminal frame. The spacing control is now
  named for what it does: *Margin* when docked, *Inner padding* when floating.

### Terminal

- **Tap a link to open it.** New installs get this switched on; existing
  installs keep their settings untouched. To enable it yourself, set
  `terminal-onclick-url-open = true` in `~/.termux/termux.properties`.
- Tapping a link now shows a small Copy / Open bubble above it, instead of a
  sheet that covers your output.
- Copy mode and scrollback search show a small card listing the keys you can
  use, matching the mode you are actually in.

### Keyboard shortcuts

- **Redesigned the shortcut hints.** Hold a prefix key and a card appears at
  the edge of the terminal, one shortcut per line. They used to take over the
  A-Z row and move around depending on your settings.
- Holding a prefix now highlights only the keys the card lists, and `?` is
  marked separately so the full keymap is easy to find.
- The full `?` table now uses the terminal's width and scrolls inside it,
  instead of being capped at 45% of the screen and overlapping the dock.

## Improvements

- The app drawer closes with one pull instead of two, and follows your finger.
- Notices can now offer an undo.
- Dragging a slider no longer resizes the terminal on every pixel — the preview
  updates live and the layout settles once, when you let go.
- A large internal cleanup. Nothing about it shows on screen, but it is what
  made the memory and speed work below possible.

## Fixes

### Memory and speed

- **The launcher now settles around 171 MB, where it used to sit at 601 MB**
  (measured on device).
- Animated images no longer hold onto their frames forever. Playback stops when
  they are off screen, and memory is released once nothing is showing them or
  when the system is running short. Running `clear` after a fastfetch banner
  now hands back tens of megabytes.
- Fixed a Shizuku leak that grew with every privileged command — one live
  session had built up 5,497 leftover handles.
- App icons are now kept once, at the size they are actually drawn, instead of
  every installed app holding full-size artwork for the life of the app.
- Stopped keeping a decoded copy of your wallpaper in memory for the whole
  session.
- The CPU widget shows a reading within a second of starting, instead of
  sitting blank for up to 36 seconds.

### Terminal

- Opening something full-screen — tmux, an editor, a pager — no longer wipes
  the images from the screen you came from.
- Images now land on the row they were placed on, instead of drifting down the
  screen with a gap above them.
- Long animations play through instead of snapping back partway.
- Fastfetch now places its logo in a way that also works inside tmux and Neovim
  image plugins.

### Interface

- Colours from `~/.termux/colors.properties` now reach the whole interface. The
  dock, status bar, drawer, keyboard and command palette were falling back to
  wallpaper colours in dark mode (#16).
- A pressed terminal pane keeps its edges, and a small pane no longer rounds
  into a lozenge.
- The floating status bar's chips are no longer clipped when you increase its
  corner radius.
- On the in-app keyboard, a quick tap on Ctrl now latches reliably, so Ctrl+V
  works as pressed.
- A session with a single window no longer shows two focus outlines.
- Switching or creating a window no longer flashes black when pane borders are
  turned off.
- The resize glow now follows the pane's real corners.
- `app-categories.conf` has moved into `~/.termux` alongside the other config
  files. An existing file is moved for you.

### Setup

- `setup-launcher` now installs a build toolchain, so Neovim's treesitter
  parsers and Mason packages build properly on first use.
