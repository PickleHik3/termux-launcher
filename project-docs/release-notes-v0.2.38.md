# Changelog — v0.2.38

## New

### Keyboard

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

## Fixes

- When Android has put the app in a state where it is not allowed to run
  anything it installs, it now says so, tells you a full uninstall and reinstall
  is what clears it, and names any app sharing its user id that caused it.
  Before, this looked like a failed download, and every launch fetched the
  bootstrap again to fail the same way.
- The bootstrap directory is kept when that happens instead of being deleted, so
  a working install is not thrown away.
