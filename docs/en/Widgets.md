# Widgets

Home is the launcher half of the wall, to the left of the Workspace. It holds a grid of widgets on
one or more Home pages: Android home-screen widgets and the launcher's own. This page covers adding,
arranging and sizing them, and the keyboard on Home.

App icons share the same grid: each takes one cell beside the widgets, moves and leaves the same
way, and follows the same pages and Margin. See
[App icons on Home](Home_Screen_And_Apps.md#app-icons-on-home).

The home button always brings you to Home page 1. From the Workspace you can also tap **Home** at
the bottom of the sessions drawer, or drag right from the first window's border.

## Add a widget

1. **Hold an empty cell** on the page (an empty page says "Long-press to add widgets"). The menu
   offers **Add widget**, **Wallpaper** and **Terminal**, which opens a terminal floating over this
   page (see [Home floats](Panes_Windows_And_Sessions.md#home-floats)). **Edit widgets** joins them
   when the page has widgets, and **Remove page** when the page is empty and not the only one.
2. Tap **Add widget**. The picker lists the launcher's own widgets first, under **Termux
   Launcher**, then the widgets of your other apps. Each card shows its sizes in cells and, for the launcher's own, what the widget is for.
3. **Tap** a card to place the widget on the page you are on, at the size shown.

For an app's widget, Android may ask you to allow the launcher to create widgets.

### Place it on another page

Cards stay usable even when the page on screen is full. A tap with no room says "No room on this
page. Hold the widget to place it on another page."

- **Hold** a card to pick the widget up.
- Rest it at the edge of the page for a moment to turn to the next page. Past the last page it makes
  a new one.
- Let go where you want it.

Only a widget bigger than the whole grid is greyed out ("too big for this grid").

## The launcher's own widgets

Ten widgets come with the launcher, drawn in the launcher's colours and following its theme,
wallpaper colours and glass. The picker shows one card per widget, with its sizes grouped on it and
a line that says what it is for:

| Widget | What it does |
|---|---|
| **Clock** | Keep time on one of eight faces, the same ones as the status bar (Analog, Numerals, Slab, Flip, LCD, LED matrix, Tape, Minimal). At the wider sizes it adds the date, the next alarm and up to two more time zones. |
| **Glance** | Glance at the date and the weather now, then the one thing that matters most: the next event, an alarm or a low battery. Drawn Bare, at 2 × 1 and 4 × 1 only. |
| **Device** | Watch the battery first: charge and charging state, then time to full or the drain rate, storage, memory and temperature, and at 4 × 2 the last 24 hours of battery. |
| **Calendar** | See what is next. Your next events at the smaller sizes, the month at the larger ones. |
| **Weather** | See the sky now for the location set in [Status bar](Status_Bar.md#weather), then the coming hours and days. It updates as soon as you come back from picking a place, and **Set a place** opens the same picker. |
| **Media** | Control what is playing, with play, pause, previous and next. |
| **Notifications** | See messages and people first, grouped by app with counts, with **Clear all** on the larger sizes. It starts at 2 × 1. |
| **Tasks** | Keep your open tasks in reach: a task list kept in `~/notes/tasks.md`. |
| **Notes** | Keep a note on Home: a file kept in `~/notes/scratch.md`, with the note's first heading as a title, the text below it and how long ago it changed. |
| **Terminal** | Keep a live shell on the page and type into it in place. Resizing the widget changes the terminal's rows and columns. |

Widgets placed before the redesign keep working: the analog and digital clocks open as the Clock,
Battery and System as Device, and Agenda and the month Calendar as Calendar, each with the settings
it had.

- Every widget has a most useful size. Past it, a bigger widget only gains room; it does not add
  anything. Clock and Calendar are fullest at 4 × 2, Glance at 4 × 1.
- Tasks and Notes are plain markdown files, so the shell sees exactly what the card shows. Tap
  the note to open the file in your editor.
- Clock, Tasks and Notes take settings from a cog that appears while you edit the page. The Clock's
  are its **face**, **Seconds**, **24-hour time** and the extra **time zones**; Tasks and Notes take
  the file path (Notes can switch between several files).
- The Calendar's month view has arrows to step to the previous or next month; tap the month's name
  to come back to this one.
- Glance and Calendar ask for calendar access on the card itself. Notifications and Media ask for
  notification access the same way.

### Glass

Every widget's settings have a **Glass** row with four choices:

- **Page** follows **Widgets on glass** in the Look, so one switch changes every widget at once.
- **On** and **Off** set this widget apart from the page.
- **Bare** draws no card at all, only the content on the wallpaper. It is offered on Clock and
  Weather; Glance is always Bare.

Widgets follow the Base Look for their glass and corners, not the terminal or keyboard overrides.

**Settings → Home → Built-in widget style** chooses how they are drawn: **Tonal** ("soft cards with
rounded corners") or **Pane** ("squared cards with a hairline rim, like a terminal pane").

## Move, resize and remove

- **Hold a widget** to pick it up and move it.
- **Drag its edges** to resize it.
- Drop it on top of other widgets and they slide aside into free space on the page. When there is
  no room, the widget takes the nearest free spot instead.
- Select a widget and tap its remove chip to take it off the page.

**Edit widgets** (from the corner tab or the empty-cell menu) outlines every widget on the page. A
tap on any of them picks it up to move or resize.

## Pages

- Swipe sideways anywhere on a Home page to move between pages. Past the last page, the swipe
  carries on to the Workspace's first window when one is open.
- **Add page** on the corner tab adds one.
- **Remove page** is in the empty-cell menu.

## Grid size

The grid size is set per orientation, in whole cells, and is capped at what the screen fits.

- **From the page.** Hold a corner of a Home page and tap **Edit widgets**. While you edit,
  the tab reads the size as columns × rows. Tap it for **Columns** and **Rows** wheels; the widgets
  rearrange as you turn them. Tap the tick (**Keep changes**) or the cross (**Discard changes**).
- **From Layout.** In Appearance → **Layout** on a Home page, drag the round handle on the corner of the
  first cell. It snaps to whole cells: dragging out makes the cells bigger and fewer. See
  [Layout and full screen](Layout_And_Full_Screen.md).

Widgets that no longer fit a smaller grid move to free space or to a new page; none are dropped.

## The keyboard on Home

Home does not keep the keyboard up. Swipe up on the page's free area — anywhere that is not a
widget or the dock — to open the command palette and the keyboard together, over the page, so the
widgets keep their places. Swipe down to put them away. Both close on **Esc**, on a
tap outside them, or once the action you picked is done. The gesture can be changed in Settings. A
text field inside a widget opens the Android keyboard.

### Keyboard always on

To keep the keyboard up on Home, open Appearance → **Layout** on a Home page and bring the keyboard
out of the tray. Every Home page gives the keyboard the rows it covers: a widget that no longer fits
shrinks, or moves to the first free spot on its page or to a new page, and a one-line notice says
what moved. While it is on, typing a key opens the command palette with that key already typed,
unless the key is bound to an app, a contact or an action.

Put the keyboard away again and the widgets go back where they were. If you changed the widgets in
between, they stay as you left them and the regained rows are left empty for you to fill.
