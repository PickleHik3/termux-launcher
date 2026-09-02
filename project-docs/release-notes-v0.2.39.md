# Changelog — v0.2.39

## New

### Terminal

- Automatic tiling: open a new pane with `Ctrl+Alt+Enter`. Each new pane splits the pane you are in along its longer side, so on a phone the first split is horizontal and the next goes side by side. Drag a pane onto another to put it in the half you drop it on. `Ctrl+Alt+L` cycles tiling layouts. Turn it on or off from Settings ▸ Terminal & Status ▸ Sessions and panes ▸ Automatic tiling.
- Spotlight the active pane: focus.nvim-like behaviour, the focused pane takes up 70% of the screen. Same section, off by default.
- Programs and agents can open panes of their own: `launcherctl pane open -- htop` opens a pane running `htop`, and a script or AI agent can write to, read, focus and close the panes it opened, and only those. `launcherctl pane list` shows every pane. Let programs and agents control panes, in the same section, switches it off.
- Process indicator on the window chips in the status bar: a turning ring, a tick mark or a bell icon shows what each window's process is doing.
- Programs can report progress and send notifications with the usual terminal escapes (the ConEmu progress report, the iTerm2 and rxvt notifications), which is what the ring, the bell and the tick read.

### Keyboard shortcuts

- `Ctrl+Alt+Enter` opens a new pane.
- `Alt+Arrow` moves between panes. It was `Ctrl+Arrow`, which took word jumps away from the shell and editors.

### Keyboard

- Learn where you tap (Settings ▸ Keyboard & input): the keyboard learns where your taps land on each key and nudges near-misses onto the key you meant. Off by default, and Forget learned taps clears what it has learned. (Not tested thoroughly yet, post an issue if you have problems.)
- Keyboard bottom padding adjustment. Change it from the surface editor by grabbing the bottom handle of the keyboard, or from Settings ▸ Keyboard & input ▸ Appearance.

### Extra keys

- Redesigned to be a little more intuitive (hopefully).
- One-tap CTRL, ALT, TAB and other common keys (#22).

### App drawer

- Open the keyboard with the drawer (Settings ▸ Launcher & apps ▸ Drawer layout) brings the search keyboard up as the drawer opens (#24).
- Choose between your default Android keyboard or the in-app keyboard for drawer search.
- In the Categories layout the drawer says when more than five apps are waiting to be categorized, and Re-run categorization shows the count.

### Appearance editor

- Redesigned surface editor: touch the icon on the floating pill to change the global values, tap an item (status bar, keyboard, dock, terminal) to override them for that surface.
- Custom preset slot: once you change anything in the surface editor a save icon shows up. Tap it to save the current settings as a custom preset.

### Companion apps

- Termux:Boot now has a matching build for every edition, so the scripts in `~/.termux/boot/` run after a restart (#25).

## Changes

- The dock's A–Z row gets better padding and alignment while the extra-keys row is hidden.
- Simplified app drawer close animation.

## Fixes

- On some phones the terminal collapsed to a blank area, and the dock jumped to the top, when the command input opened the Android keyboard (#21, #23).
- The Android keyboard no longer stays stuck on screen after the screen turns off and on while the command input has it.
- A URL that a tmux or other multiplexer pane wrapped at its border is found and opened whole, from either half.
- With the extra-keys row off, closing the in-app keyboard no longer stacks the dock rows at the dock's top edge.
- Images sent over the kitty graphics protocol render whatever their size. `kitten icat` used to print a wall of text once the image passed a few kilobytes.
- Terminal text at the corners of a pane no longer clips under its rounded corners.
- Scrubbing the A–Z row and sliding up onto the app icons used to tick twice. Fixed.
- `setup-launcher`: fastfetch logos made from 1-bit or 16-bit PNGs draw again. They came out blank.
- `setup-launcher`: with a one-line prompt, fish's clear no longer leaves a blank row above the keyboard. Run `setup-launcher` again to pick up the config.

## Editions

- VAJ: `setup-launcher` installs a fastfetch built for this edition. The one it fetched before could not start.
- Nix: the flake template's animated fastfetch overlay carries the same logo fixes.
