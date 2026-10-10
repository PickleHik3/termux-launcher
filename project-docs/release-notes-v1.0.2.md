Fixes for the dock, the keyboard and the terminal's colours that people ran into after 1.0.0, a Terminal contrast setting that now does what it says, and month arrows for the calendar widget.

## New

### Widgets Page

- The calendar widget's month view has arrows to step to other months, with each month's events marked. Tap the month's name to come back to today's. Thanks to @AbineshDev01 (#48).

## Changes

- The dock's size control in Appearance › Look is now Icon size: pick how big the pinned icons are and the dock fits around them.
- Terminal contrast's Softer, Default and Harder now look clearly different, and Default uses your theme's own colours.
- fish and Oh My Posh are no longer among the tools that follow terminal colours. If you had either on, its theme files are removed.

## Fixes

- Pinned app icons are no longer tiny, and they resize as soon as you change the dock's size (#46).
- A keyboard colour scheme's key Border colour now shows (#46).
- The terminal is no longer darker than the keyboard and the rest of the glass. Use Darkness if you want it darker (#46).
- The Docked terminal has a visible outline round its window (#46).
- Terminal contrast changes the text on a see-through terminal too, and terminal colours keep their colour over a bright wallpaper instead of turning black or white.
- Terminal apps that follow light and dark mode no longer switch theme twice when the launcher starts.
- Tools that follow terminal colours get the right light colours when the terminal turns light over a bright wallpaper.
- The dock's handle in the Layout editor stops where the dock stops growing.
- The Termux:API, Termux:Boot and Termux:Styling links in Settings open the launcher's own versions, which are the ones that work with it.

## Editions

Shipped as `nix-v1.0.2` and `vaj-v1.0.2`. Nothing was exclusive to an edition.
