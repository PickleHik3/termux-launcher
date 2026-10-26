---
status: accepted
date: 2026-10-21
supersedes: the Tonal and Pane directions of `project-docs/reference/launcher/first-party-widgets-SPEC.md` (a spec, not an ADR)
---

# Home widgets: one Clock on the bar's faces, measured ink, and a smaller set

The launcher's own Home widgets were twelve kinds in two directions, drawn in day-or-night colours.
They had two clock widgets with their own numerals, unrelated to the status bar's six clock faces,
which could not be placed on Home at all.

On the emulator their ink was never checked against what lay behind it. Text vanished on glass and
on a dark wallpaper by day, and cards glared on dark wallpaper and disappeared on light wallpaper.
The dock and the bars, by contrast, are held to 4.5:1 by `ChromeInk` and `OnGlass`.

We made three decisions.

1. **One Clock, on the bar's faces.** One **Clock** replaces the analog and digital clocks.
   - Its **clock faces** are Analog, Numerals, Slab, Flip, LCD, LED matrix, Tape and Minimal.
   - They draw through one face renderer taken out of `TerminalClockWidget`, which the status bar
     then renders through too, so a face can't drift between the two.
   - A new Clock copies the bar's current face once. After that the two are set independently.
   - The faces' known weaknesses are fixed in that renderer, and so in the bar as well: LCD
     contrast, Flip's ghost fold lines, a heavier Minimal option, Tape's date placement, and
     seconds and date at 11 sp or more.
2. **Widgets join the measured ink.** Widgets become a band of `ChromeInk`, measured by `OnGlass`
   like the dock and the bars.
   - Ink and veil are measured against the shared frame or the wallpaper behind each widget, at
     4.5:1 for text and 3:1 for large numerals and graphics.
   - A solid card takes the page's ink polarity instead of day or night.
   - Glass falls back to the solid card where it can't be shown or where contrast settings ask for
     it.
   - Glass is set for all widgets by **Widgets on glass** in the Look, following Base, with each
     widget's own switch kept as an override.
   - The cardless **Bare** appearance is opt-in per widget for Clock, Glance and Weather.
3. **A smaller set, with aliases.**
   - Agenda and Calendar merge into **Calendar**.
   - Battery and System merge into **Device**.
   - The Scratchpad widget is renamed **Notes**.
   - Notifications is narrowed to a 2×1 minimum.
   - Terminal stays.
   - One kind is added: **Glance**, which shows no time.
   - Tonal and Pane retire for one family with a Numerals Sans/Mono choice.
   - Stored kind ids never change (`clock.analog`, `clock.digital`, `agenda`, `calendar.month`,
     `battery`, `system`): each loads as an alias that opens the new widget with matching settings.

Considered options:

- **The Clock.** Adding the bar's faces to the digital clock would have kept two clocks in the
  picker and two digital renderers to keep in step. A Clock with its own copies of the faces would
  have drifted from the bar with the first fix.
- **Contrast.** Fixed alpha cards with hand-picked ink, as today, are what failed on the device. A
  per-widget contrast check outside `ChromeInk` would have let a widget pick a polarity opposite to
  the dock's on the same wallpaper.
- **The set.** Sessions and Command widgets were proposed and dropped. The window list belongs to the
  dock rework of ADR 0009, and the redesign proposes no widget for it. "Clear" was rejected as the
  cardless name because Clear is a stop on the Look slider, and choosing that Look does not make
  widgets Bare.

Consequences:

- **Phase 0** fixes the defects the emulator showed:
  - The 1×1 Battery and Clock get their font, spacing and outer padding reworked so they scale
    without overlap.
  - The Terminal widget fills its width.
  - Widgets on the lowest rows stop hiding behind the dock.
  - The edit toolbar stops covering top-right widgets.
- **Later phases:**
  - Phase 1 is the tokens and legibility.
  - Phase 2 is the Clock, which is how the status bar's clock designs become placeable Home widgets.
  - Calendar, Weather and Glance follow, then Device and the restyles, then Bare.
- **Process.** Each phase starts with mockups on a light and a dark wallpaper for the developer's
  taste check, and ends with emulator before/after shots and pong in a batch.
- **Hosted app widgets** are clipped at the Look's Corners, capped at the system radius.

The research is `project-docs/active/widget-redesign-research.md`.
