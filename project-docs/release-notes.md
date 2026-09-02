# Changelog

Every shipped release, newest first. This is the whole changelog — the notes for the release
currently being written live in their own `release-notes-v<version>.md` until it is published, then
they move in here and that file goes. AGENTS.md has the convention and the voice.

Entries are kept as they shipped and are not revised afterwards. Where an old entry describes the
state of an edition, read it as of that release, not as of today.

Each version's **Editions** list carries only what was exclusive to the Nix (`com.termux.launcher.nix`)
or VAJ (`io.vaj.tl`) build; everything above it applies to all three.

---

## v0.2.39

### New

#### Terminal

- Automatic tiling: open a new pane with `Ctrl+Alt+Enter`. Each new pane splits the pane you are in along its longer side, so on a phone the first split is horizontal and the next goes side by side. Drag a pane onto another to put it in the half you drop it on. `Ctrl+Alt+L` cycles tiling layouts. Turn it on or off from Settings ▸ Terminal & Status ▸ Sessions and panes ▸ Automatic tiling.
- Spotlight the active pane: focus.nvim-like behaviour, the focused pane takes up 70% of the screen. Same section, off by default.
- Programs and agents can open panes of their own: `launcherctl pane open -- htop` opens a pane running `htop`, and a script or AI agent can write to, read, focus and close the panes it opened, and only those. `launcherctl pane list` shows every pane. Let programs and agents control panes, in the same section, switches it off.
- Process indicator on the window chips in the status bar: a turning ring, a tick mark or a bell icon shows what each window's process is doing.
- Programs can report progress and send notifications with the usual terminal escapes (the ConEmu progress report, the iTerm2 and rxvt notifications), which is what the ring, the bell and the tick read.

#### Keyboard shortcuts

- `Ctrl+Alt+Enter` opens a new pane.
- `Alt+Arrow` moves between panes. It was `Ctrl+Arrow`, which took word jumps away from the shell and editors.
- `Ctrl+Alt+W` closes the focused pane.

#### Keyboard

- Learn where you tap (Settings ▸ Keyboard & input): the keyboard learns where your taps land on each key and nudges near-misses onto the key you meant. Off by default, and Forget learned taps clears what it has learned. (Not tested thoroughly yet, post an issue if you have problems.)
- Keyboard bottom padding adjustment. Change it from the surface editor by grabbing the bottom handle of the keyboard, or from Settings ▸ Keyboard & input ▸ Appearance.

#### Extra keys

- Redesigned to be a little more intuitive (hopefully).
- One-tap CTRL, ALT, TAB and other common keys (#22).

#### App drawer

- Open the keyboard with the drawer (Settings ▸ Launcher & apps ▸ Drawer layout) brings the search keyboard up as the drawer opens (#24).
- Choose between your default Android keyboard or the in-app keyboard for drawer search.
- In the Categories layout the drawer says when more than five apps are waiting to be categorized, and Re-run categorization shows the count.

#### Appearance editor

- Redesigned surface editor: touch the icon on the floating pill to change the global values, tap an item (status bar, keyboard, dock, terminal) to override them for that surface.
- Custom preset slot: once you change anything in the surface editor a save icon shows up. Tap it to save the current settings as a custom preset.

#### Companion apps

- Termux:Boot now has a matching build for every edition, so the scripts in `~/.termux/boot/` run after a restart (#25).

### Changes

- The dock's A–Z row gets better padding and alignment while the extra-keys row is hidden.
- Simplified app drawer close animation.

### Fixes

- On some phones the terminal collapsed to a blank area, and the dock jumped to the top, when the command input opened the Android keyboard (#21, #23).
- The Android keyboard no longer stays stuck on screen after the screen turns off and on while the command input has it.
- A URL that a tmux or other multiplexer pane wrapped at its border is found and opened whole, from either half.
- With the extra-keys row off, closing the in-app keyboard no longer stacks the dock rows at the dock's top edge.
- Images sent over the kitty graphics protocol render whatever their size. `kitten icat` used to print a wall of text once the image passed a few kilobytes.
- Terminal text at the corners of a pane no longer clips under its rounded corners.
- Scrubbing the A–Z row and sliding up onto the app icons used to tick twice. Fixed.
- `setup-launcher`: fastfetch logos made from 1-bit or 16-bit PNGs draw again. They came out blank.
- `setup-launcher`: with a one-line prompt, fish's clear no longer leaves a blank row above the keyboard. Run `setup-launcher` again to pick up the config.

### Editions

Shipped as `v0.2.39-nix` and `v0.2.39-vaj`.

- VAJ: `setup-launcher` installs a fastfetch built for this edition. The one it fetched before could not start.
- Nix: the flake template's animated fastfetch overlay carries the same logo fixes.

---

## v0.2.38+hotfix1

### Fixes

- The flip clock now follows the alignment setting instead of always sitting in the centre.

### Editions

Shipped as `v0.2.38-nix+hotfix1` and `v0.2.38-vaj+hotfix1`. Nothing was exclusive to an edition.

---

## v0.2.38

### New

#### Keyboard

- Every layout the app ships — ninety-one of them, QWERTY and Dvorak through
  Arabic PC and Dubeolsik — can now be used, not just the launcher's own.
  Settings ▸ Keyboard ▸ Layouts picks which ones the keyboard cycles through
  and in what order.
- Swiping the space bar down steps that cycle. You can also bind a key to it
  (`keyboard.cycle_layout`), jump straight to a layout by name
  (`keyboard.select_layout latn_dvorak`), or pick one from the command palette,
  where the Keyboard section lists the cycle once it holds more than one layout.
- Your own `~/.termux/keyboard/layout.xml` is the first entry in the cycle, so a
  custom layout sits alongside the shipped ones rather than replacing them.

### Fixes

- When Android has put the app in a state where it is not allowed to run
  anything it installs, it now says so, tells you a full uninstall and reinstall
  is what clears it, and names any app sharing its user id that caused it.
  Before, this looked like a failed download, and every launch fetched the
  bootstrap again to fail the same way.
- The bootstrap directory is kept when that happens instead of being deleted, so
  a working install is not thrown away.
- The appearance editor no longer opens fully collapsed in some cases.

### Editions

Shipped as `v0.2.38-nix` and `v0.2.38-vaj`. Nothing was exclusive to an edition.

---

## v0.2.37

A hotfix for the appearance editor.

### Fixes

- The appearance editor keeps its presets and controls when there is little
  room above the dock. With the dock rows switched off, or the Android keyboard
  in use, it could open as nothing but a title with **Reset** and **Done** (#20).

### Editions

Shipped as `v0.2.37-nix` and `v0.2.37-vaj`. No edition-exclusive notes were written.

---

## v0.2.36

A big one: a rebuilt appearance editor, tappable links in the terminal,
redesigned shortcut hints, and fixes for the memory leaks that made long
sessions grow.

### New

#### Appearance

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

#### Terminal

- **Tap a link to open it.** New installs get this switched on; existing
  installs keep their settings untouched. To enable it yourself, set
  `terminal-onclick-url-open = true` in `~/.termux/termux.properties`.
- Tapping a link now shows a small Copy / Open bubble above it, instead of a
  sheet that covers your output.
- Copy mode and scrollback search show a small card listing the keys you can
  use, matching the mode you are actually in.

#### Keyboard shortcuts

- **Redesigned the shortcut hints.** Hold a prefix key and a card appears at
  the edge of the terminal, one shortcut per line. They used to take over the
  A-Z row and move around depending on your settings.
- Holding a prefix now highlights only the keys the card lists, and `?` is
  marked separately so the full keymap is easy to find.
- The full `?` table now uses the terminal's width and scrolls inside it,
  instead of being capped at 45% of the screen and overlapping the dock.

### Improvements

- The app drawer closes with one pull instead of two, and follows your finger.
- Notices can now offer an undo.
- Dragging a slider no longer resizes the terminal on every pixel — the preview
  updates live and the layout settles once, when you let go.
- A large internal cleanup. Nothing about it shows on screen, but it is what
  made the memory and speed work below possible.

### Fixes

#### Memory and speed

- **Fixed memory leaks.** A long session with images loaded could climb past
  600 MB and stay there. Memory now comes back on `clear`, when you close a
  pane, or when the system needs it.
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

#### Terminal

- Opening something full-screen — tmux, an editor, a pager — no longer wipes
  the images from the screen you came from.
- Images now land on the row they were placed on, instead of drifting down the
  screen with a gap above them.
- Long animations play through instead of snapping back partway.
- Fastfetch now places its logo in a way that also works inside tmux and Neovim
  image plugins.

#### Interface

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

#### Setup

- `setup-launcher` now installs a build toolchain, so Neovim's treesitter
  parsers and Mason packages build properly on first use.

### Editions

- **Nix** — Colours from `~/.termux/colors.properties`: a newly generated file is now
  picked up, instead of the old palette continuing to be served (#16). This edition ships
  for 64-bit devices only — `arm64-v8a` and `x86_64`. `setup-launcher` is not part of this
  edition; packages come from nixpkgs.
- **VAJ** — Ships for 64-bit ARM devices (`arm64-v8a`) only.

---

## v0.2.35-a

A small fix release on top of v0.2.35.

### Fixes

- The Style entry is back in the terminal long-press menu on the Nix edition
  when TLNix:Styling is installed (#13).
- Removed the tinted square edges behind the rounded corners of the CPU and
  weather cards (#13).
- The Lazy Mode toggle now actually saves, and applies without restarting the
  app.

### Editions

- **Nix** — The Style entry fix above is this edition's: it was the one missing it (#13).
- **VAJ** — At the time this edition received security fixes only; this small fix release
  rode along to keep the editions in step.

---

## v0.2.35

### New

#### App Drawer

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

#### Widgets Page

- Added swipe-down gestures on the status bar:
  - Half swipe expands the status bar.
  - Full swipe opens the widgets page.
- Widget pages can be added, reordered and edited in place; long-press a widget
  to move, resize or remove it.

#### Keybinds

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

#### Terminal

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

#### Nerd Fonts

- Nerd Fonts can now be used in the in-app keyboard and Extra Keys.
- The status bar now uses Nerd Fonts bundled with the app.

#### Lazy Mode

- Added an experimental Lazy Mode designed to reduce idle resource usage.
- Enable it from Settings → Terminal & Status.
- If testing goes well, Lazy Mode is intended to become the default in a future
  release.

#### Launcher

- Simplified the launcher setup script.
- Added a new repository for hosting Termux Launcher-specific binaries; every
  install is verified against a pinned checksum.
- Added Fastfetch with GIF support.
- Added `setup-nvim`, a Neovim distro chooser (AstroNvim by default, or NvChad,
  LazyVim, kickstart or stock) themed from your wallpaper palette.
- Added one switch for the launcher versus terminal-only use case, in
  Settings → Launcher & apps.
- Landscape is now usable: the dock becomes a side rail on the edge you pick,
  and the drawer is denser.
- The whole interface can follow the terminal colour scheme
  (`~/.termux/colors.properties`) instead of the wallpaper palette, with
  per-token overrides in `~/.termux/launcher-theme.properties`.
- Rebuilt the weather card around the forecast, with a greeting on arrival and
  an optional Fahrenheit unit.
- The flip clock animates seconds on their own pair, with a metadata cell
  beside the digits.

#### Extra Keys

- Added a visual-style Extra Keys editor: tap a key to edit it, hold and drag
  to move it, with macros and swipe-up actions per key.
- Added a glyph picker behind the label fields, searchable across all 10,512
  bundled Nerd Font icons.
- The refined key row now ships as the built-in default — no setup script
  needed, and your own `extra-keys` property still wins.

### Changes

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

### Fixes

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

### Security

Findings from an external review of the launcher's local API surface, all
fixed:

- The local API only answers pages served from this device; a web page from
  anywhere else can no longer reach it.
- Media paths in inference requests are validated, so a request can no longer
  read arbitrary files or pivot into the LAN.
- Notification history is opt-in and off by default; turning it off deletes
  what was captured.
- LAN mode now ends itself after 12 hours: the API goes back to this device
  only, and the old token stops working.
- The setup script verifies everything it installs against pinned checksums
  and fails closed on a mismatch.

### Editions

- **Nix** — The bootstrap now arrives with the environment already set up, so the first
  launch lands in a configured shell instead of asking you to run setup.
- **VAJ** — Added a one-time notice on first launch explaining that the edition was at the
  time deprecated and receiving security fixes only, with a link to the migration guide for
  moving to the Nix edition.

---

Releases before v0.2.35 are on their
[GitHub release pages](https://github.com/PickleHik3/termux-launcher/releases) only.
