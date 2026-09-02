# Changelog — v0.2.39

Panes that tile themselves, a pane API for the programs and agents running in your shell, a rebuilt appearance editor and extra-keys editor, and a set of fixes for the Android keyboard.

## New

### Terminal

- Automatic tiling: a new `dwindle` pane layout. Each new pane splits the pane you are in along its longer side, so on a phone the first split stacks and the next goes side by side. Drag a pane onto another and it takes the half you drop it on. `Ctrl+Alt+L` cycles to it after `grid`, and Settings ▸ Terminal & Status ▸ Sessions and panes ▸ Automatic tiling makes it the default for new windows.
- Spotlight the active pane: the pane you are working in takes most of the screen and the others shrink aside. Same section, off by default.
- Programs and agents can open panes of their own: `launcherctl pane open -- htop` opens a pane running `htop`, and a script or AI agent can `write` to, `read`, `focus` and `close` the panes it opened, and only those. `launcherctl pane list` shows every pane. Let programs and agents control panes, in the same section, switches it off.

- The window bar shows what each window's command is doing. A turning ring takes the place of the process glyph while a command works, and fills to a percentage when the program reports one. A bell marks a window whose command is waiting for an answer, and a tick marks one that finished while you were looking elsewhere. Both hold until you visit the window.
- Programs can report progress and send notifications with the usual terminal escapes (the ConEmu progress report, the iTerm2 and rxvt notifications), which is what the ring, the bell and the tick read.

### Keyboard shortcuts

- `Ctrl+Alt+Enter` opens a new pane without asking which way to split. `Ctrl+Alt+V` and `Ctrl+Alt+H` still split the way you say, and the Ctrl+Alt hint strip now lists the new chord first.
- `Alt+Arrow` moves between panes. It was `Ctrl+Arrow`, which took word jumps away from the shell and editors. With no pane in that direction the key goes to the shell as usual.

### Keyboard

- Learn where you tap (Settings ▸ Keyboard & input): the keyboard learns where your taps land on each key and nudges near-misses onto the key you meant. Off by default, and Forget learned taps clears what it has learned.
- Space under the keys: a slider that lifts the bottom key row away from the screen edge, for phones with a gesture bar. The same gap can be dragged from the appearance editor.

### Extra keys

- The extra-keys editor is now a Settings page (Settings ▸ Keyboard & input ▸ Edit extra keys) with a live preview of the row, one-tap chips for the keys you most often want, CTRL first, drag to reorder, a tap and a swipe action per key, and presets: Launcher default, Classic Termux, Two rows and Clear page (#22).

### App drawer

- Open the keyboard with the drawer (Settings ▸ Launcher & apps ▸ App drawer) brings the search keyboard up as the drawer opens (#24).
- Search with the Android keyboard puts your system keyboard under the search pill, so its suggestions, autocorrect and swipe typing apply.
- In the Categories layout the drawer says when more than five apps are waiting to be categorized, and Re-run categorization shows the count.

### Appearance editor

- Rebuilt. It opens as a small pill with every surface it can edit outlined: Dock, Terminal, Status and Keyboard. Tap a surface to get its card, with everything that surface owns in one list, or tap the palette for the shared layer: presets, Docked or Floating, Solid, Glass or Frost, and the numbers that move every surface.
- Every slider fades the card while your thumb is down, so the change shows where it lands, with the value echoed over the surface.
- Size pills sit on the dock's top edge and under the keyboard's last row, and a drag on either resizes it.

### Companion apps

- Termux:Boot now has a matching build for every edition, so the scripts in `~/.termux/boot/` run after a restart (#25).

## Changes

- The read-out that names the action a key or a palette entry just ran now appears on the top-centre pill with every other message, and the palette no longer repeats it.
- The dock's A–Z row gets a taller touch band while the extra-keys row is hidden.
- Closing the app drawer is one quick motion instead of trailing off at the end.

## Fixes

- On some phones the terminal collapsed to a blank area, and the dock jumped to the top, when the command input opened the Android keyboard (#21, #23).
- The Android keyboard no longer stays stuck on screen after the screen turns off and on while the command input has it.
- The Android keyboard gets out of the way when you open the app drawer over it, and comes back when the drawer closes.
- A thin band of wallpaper no longer shows between the extra keys and the Android keyboard.
- A URL that a tmux or other multiplexer pane wrapped at its border is found and opened whole, from either half.
- With the extra-keys row off, closing the in-app keyboard no longer stacks the dock rows at the dock's top edge.
- Images sent over the kitty graphics protocol render whatever their size. `kitten icat` used to print a wall of text once the image passed a few kilobytes.
- Text at the corners of a pane no longer clips under its rounded corners.
- The prompt sits at the same distance from the bottom of its pane whatever the pane's height. It used to float up to a full line above it.
- A prompt at the bottom of the screen stays there when the keyboard closes instead of being left mid-screen.
- Raising the keyboard's opacity now covers the wallpaper behind the keys instead of brightening the grain.
- Scrubbing the A–Z row and sliding up onto the app icons ticks once per icon, not once per letter underneath.
- `setup-launcher`: fastfetch logos made from 1-bit or 16-bit PNGs draw again. They came out blank.
- `setup-launcher`: with a one-line prompt, fish's clear no longer leaves a blank row above the keyboard. Run `setup-launcher` again to pick up the config.

## Editions

- VAJ: `setup-launcher` installs a fastfetch built for this edition. The one it fetched before could not start.
- Nix: the flake template's animated fastfetch overlay carries the same logo fixes.
