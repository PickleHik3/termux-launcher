# Widgets

Home is the place to the left of the terminal. It holds a grid of widgets on one or more pages:
Android home-screen widgets and the launcher's own. This page covers adding, arranging and sizing
them.

To get there, hold the page border and drag, or tap the house icon peeking in from the status bar.
The widget page is switched on and off with **Settings → Apps → Home widgets** ("Show a widget page
beside the terminal.").

## Add a widget

1. **Hold an empty cell** on the page (an empty page says "Long-press to add widgets"). The menu
   offers **Add widget**, **Edit widgets** and **Remove page**.
2. Tap **Add widget**. The picker lists the launcher's own widgets first, under **Termux
   Launcher**, then the widgets of your other apps. Each card shows its size in cells.
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

Eleven widgets come with the launcher, drawn in the launcher's colours and following its theme,
wallpaper colours and glass:

| Widget | What it shows |
|---|---|
| **Analog clock**, **Digital clock** | The time; up to two more cities or time zones. |
| **Agenda** | Your next calendar events. |
| **Calendar** | A month view. |
| **Weather** | The weather for the location set in [Status bar](Status_Bar.md#weather). |
| **Battery** | Charge and charging state. |
| **System** | CPU, RAM and storage. |
| **Media** | What is playing, with play, pause, previous and next. |
| **Notifications** | Recent notifications, with **Clear all**. |
| **Tasks** | A task list kept in `~/notes/tasks.md`. |
| **Scratchpad** | A note kept in `~/notes/scratch.md`. |

- Tasks and Scratchpad are plain markdown files, so the shell sees exactly what the card shows. Tap
  the scratchpad to open the file in your editor.
- The clocks, Tasks and Scratchpad take settings from a cog that appears while you edit the page:
  the extra time zones, or the file path (the scratchpad can switch between several files).
- Agenda and Calendar ask for calendar access on the card itself. Notifications and Media ask for
  notification access the same way.

**Settings → Apps → Built-in widget style** chooses how they are drawn: **Tonal** ("soft cards with
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

- Swipe sideways inside the grid to move between pages.
- **Add page** on the corner tab adds one.
- **Remove page** is in the empty-cell menu.

## Grid size

The grid size is set per orientation, in whole cells, and is capped at what the screen fits.

- **From the page.** Hold a corner of the Widgets page and tap **Edit widgets**. While you edit,
  the tab reads the size as columns × rows. Tap it for **Columns** and **Rows** wheels; the widgets
  rearrange as you turn them. Tap the tick (**Keep changes**) or the cross (**Discard changes**).
- **From Layout.** In Appearance → **Layout** on Home, drag the round handle on the corner of the
  first cell. It snaps to whole cells: dragging out makes the cells bigger and fewer. See
  [Layout and full screen](Layout_And_Full_Screen.md).

Widgets that no longer fit a smaller grid move to free space or to a new page; none are dropped.

## The keyboard on Home

Nothing on Home takes terminal typing, so Home always comes back with the keyboard down. If you
raise it (swipe up from the bottom border) it opens over the page, so the widgets keep their
places. A text field inside a widget opens the Android keyboard.
