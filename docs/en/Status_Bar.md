# Status bar

The status bar shows where you are, your sessions and windows, the time and how the phone is doing.
This page covers what it shows, how to open it, and its settings under **Settings → Status bar**
("Clock, indicators and weather"). Pinned notification cards in the bar are on
[Notifications](Notifications.md).

## What it shows

- **Place icon and clock.** The icon beside the clock names the place you are on: a house for Home,
  a prompt for Terminal, a screen for Display. The other two places peek in from the bar's edges,
  on the side each one slides in from; tap one to go there. The Display icon reads quieter until a
  display is running.
- **The row for this place.** On the terminal: the session number, then a pill for each window,
  then `+` for a new window. On Display: the apps open on the display, front one selected. On
  Home: only the readings and the weather, centred under the clock.
- **Readings.** CPU, memory and weather, as switched on in Settings.
- **Mouse mode.** A small mouse at the end of the readings means mouse mode is on.

## Open and fold it

The bar has a slim form and an open form with a larger clock and room for cards.

- Swipe down from the page's top border to open it, and up to fold it. A small pill marks the middle
  of the border. Let go past about a third of the way, or flick, and it finishes on its own.
- Or drag across the bar itself.

The top-border swipe works on every place, minimal mode included, while the bar stands along the
top. A swipe that starts in Android's own strip at the very top of the screen still pulls the
notification shade. A sideways swipe along the window pills scrolls the pills and never changes
place.

## Tap targets

- The **session number** opens the sessions manager. See
  [Panes, windows and sessions](Panes_Windows_And_Sessions.md).
- A **window pill** switches to that window; `+` makes a new one.
- **CPU**, **memory** or **weather** opens a detail card. The weather card credits Open-Meteo.
- In the open bar, the **clock** opens Android's clock app and the **cog** opens Settings.
- The **mouse** turns mouse mode off.

## Window pills

A pill shows one short item: the open file in an editor, the running program, or the directory of
an idle shell. A program with its own icon, such as python, shows the icon alone. Marks sit in the
icon's place:

- a **ring** while the window's foreground program is using CPU;
- a **bell** once the window rang or asked for attention;
- a **tick** or a **cross** once a command finished while you were not looking.

A background window that rings the bell gets a pulsing rim until you focus it.

Coding agents add a dot before the label: **Working**, **Needs you** or **Idle**. See
[Agent status](Agent_Status.md).

## Clock

- **Settings → Status bar → Clock → 12-hour time** ("Show AM and PM."), off by default.
- The face and alignment are part of the look. Open **Appearance**, go to **Look**, slide to
  **Custom**, tap the status bar and tap **Clock**. Faces: **Flip**, **LCD**, **Minimal**, **LED
  matrix**, **Tape**, **Slab**. Alignment: **Left**, **Center**, **Right**.

When the bar is open, the clock keeps its full face and scales to the room left beside any cards.

## Media

While an app is playing, the open bar shows a media row: the album art next to the play controls,
and the title. Tap the art or the title to open the app that is playing. The row needs
**Notification access**; see [Permissions and Shizuku](Shizuku.md#permissions).

## Indicators

Under **Settings → Status bar → Indicators**:

- **CPU usage**: off by default. Tap the reading for cores, load and top processes.
- **Memory usage**: off by default.

The readings describe the whole phone, not only your shells.

## Weather

Under **Settings → Status bar → Weather**:

- **Weather**: on by default.
- **Fahrenheit**: "Show °F instead of °C."
- **Location**: opens a search. Type a city in **Search for a city** and pick a result, or choose
  **Use device location**.

While a location is picked, the weather needs no location permission, and a new pick refreshes the
weather straight away. With no location picked, it uses the phone's location and asks for location
permission. The same weather feeds the **Weather** widget; see [Widgets](Widgets.md).

## Where the bar stands

The bar can stand on any edge, or be hidden, in Appearance → **Layout**:

- **Top**: the bar described above.
- **Bottom**: it sits on the dock and grows upward when opened, with the clock at its foot.
- **Left or right**: a narrow column with the place badge, one chip per window and the readings;
  the clock is written hour over minutes when the column is open.

Hidden, the clock, weather and window pills go with it and the content takes the room. The corner
tab still opens Appearance, so you can bring it back. See
[Layout and full screen](Layout_And_Full_Screen.md).

The bar's blur, opacity, grain and tint are set in Appearance → **Look**; see
[Look and themes](Look_And_Themes.md). **Settings → Status bar → More → Notifications** leads to the
notification settings.
