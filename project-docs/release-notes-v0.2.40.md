## New

### Launcher

- The home screen is three places side by side: your widgets on the left, the terminal in the middle and a Linux display on the right. Swipe left or right on the status bar to move between them, or use the place switch beside the clock in the expanded status bar: one pill with the three places on it, the marker following your finger and sliding across when you tap. Past the last place you come back round to the first.
- The terminal never changes size when you move between places, and the other two places sit inside the same frame as the terminal.

### Linux display

- Run a Linux desktop or X11 apps beside the terminal. Turn it on from the Display place or from Settings ▸ Launcher & apps ▸ Linux display, install the keyboard layouts it needs, and start it with the button on the page or with `termux-x11 :0` in a shell. Everything you know from Termux:X11 keeps working, including `termux-x11-preference`.
- The Display segment of the place switch shows a dot while a display is running; hold it to stop the display, after asking. A display only ever starts when you ask for it.
- Your keyboard and the extra-keys row type into the display while it is showing. With a hardware keyboard, everything reaches the display except the launcher's own Ctrl+Alt shortcuts.
- Display options (Settings ▸ Launcher & apps): touch as trackpad, touchscreen or direct touch; resolution the same as the screen, scaled, fixed or custom; text and icon size; clipboard sharing; and how the launcher starts a display — with the launcher, with a command of your choosing, pointing new shells at it, and two compatibility switches for GPUs that draw black or with wrong colours.
- `launcherctl x11 gpu` says what your phone's GPU can do for Linux apps and, with `--env`, prints the exact settings to use; the same answer sits at the bottom of Display options.

## Changes

- The pull-down status pane is gone; the widget grid it held is the Widgets place of the home screen.

## Editions

- Nix and VAJ: nothing exclusive in this release. The Linux display's packages come from each edition's own package source; the VAJ repository does not carry them yet.
