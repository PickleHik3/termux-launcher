# Layout and full screen

This page covers where the bars stand, their sizes, the keyboard type, minimal mode and full screen.
All of it is arranged in the **Layout** tab of **Appearance**.

## Open Layout

Open **Appearance** in any of these ways, then tap **Layout** in the pill at the top
(**Wallpaper | Look | Layout | Icon pack**):

- **Settings → Appearance** ("Wallpaper, theme and layout").
- Hold a corner of a page and tap **Appearance**.
- Long-press the terminal and choose **Appearance**.

Home, Terminal and Display share one layout, so whatever you arrange lands on all three. The canvas
shows the place you opened it from. Portrait and landscape each have their own layout.

## The editor

The canvas fills the editor. Under it is a sheet with two rows:

- **Row A**: the Portrait/Landscape toggle, **Style: Docked / Floating**, and the eye-off **Hidden
  elements** button.
- **Row B**: **Corners** and **Margin**, or the keyboard's tools while the keyboard is selected.

**Undo** and **Done** are in the top bar. **Done** applies everything you changed on any Appearance
tab. Back with nothing changed closes at once; with changes it asks whether to keep editing,
discard or save.

The canvas draws the status bar, keyboard and minimal layout as they are now. The apps row is drawn
with seven placeholder icons; the extra keys show your real keys.

## Move a bar

The bars are **Status bar**, **Apps row**, **A–Z index** and **Extra keys**.

- **Drag**: press a bar anywhere and drag it to any of the four edges. While it is lifted, the edges
  it may stand on are outlined. Dropping it between two bars on the same edge sets their order.
  Letting go anywhere else, or pressing Back, puts it back.
- **Tap**: a tap selects a bar and shows a four-arrow **Move** button beside it. Its menu lists the
  edges the bar can go to, and **Hide**.

The terminal stays where it is and the bars arrange around it. The A–Z index rides the apps row
while they share an edge.

**Under the keyboard.** The canvas also offers a slot below the keyboard. Drop the apps row, the A–Z
index or the extra keys there and they stay at the bottom of the screen, with the keyboard opening
above them. The status bar always stands over the keyboard.

## Hide and bring back

- Drop a bar on the **eye-off** button to hide it. The button lights up while a bar that may be
  hidden is lifted, and shows how many things are hidden.
- Tap eye-off to show the hidden elements as tiles, each with a restore arrow. Tap a tile to bring
  it back to the edge it left, or drag it onto the canvas to choose the edge.

**Keyboard on/off.** Drag the keyboard onto eye-off to switch it off; its tile switches it back on.
Off, nothing raises it, not a tap on the terminal and not a text field, until you turn it on again.
It is the same switch as **Keyboard on/off** in the command palette, and one switch for every place
and both orientations. A swipe up from the bottom border also turns it back on.

## Sizes

Tap something on the canvas to select it; anything with a size gets a handle. Each size is set per
orientation, and a readout shows it while you hold the handle.

- **Dock height**: drag the dock's inner edge ("Dock N dp").
- **Keyboard height**: drag the keyboard's top edge ("Keyboard N dp").
- **Bottom padding**: drag the bottom of the keys up for space under the last row ("Bottom padding
  N dp").
- **Widget grid** (Home only): drag the round handle on the corner of the first cell. It snaps to
  whole cells. See [Widgets](Widgets.md#grid-size).

## Keyboard type

Select the keyboard and **Keyboard type** chips appear: docked, floating or split, for the
orientation on the toggle. **Key radius** under them rounds the key caps. Sizes for the floating
and split keyboards are on [Keyboard](Keyboard.md).

## Style, corners and margin

- **Docked** joins the bars into one flush glass frame, with the content as a rounded insert.
- **Floating** gives each bar its own card.
- **Corners** ("Corners · N dp") and **Margin** ("Margin · N dp") apply in both styles.

## Minimal mode

Minimal mode is a second saved layout. It starts with only the content showing: the status bar, the
pinned apps, the A–Z index, the extra keys and the keyboard go away, and the widgets, terminal or
display take the room. Open Layout while it is on to choose what it keeps.

- **Turn it on**: hold a corner and tap the four outward corners. **Turn it off**: the same button,
  now pointing inward. Nothing else turns it off.
- It is one mode for the whole launcher. Turn it on from Widgets and the terminal and display are
  minimal too; moving between places never turns it off. It stays on across restarts.
- Everything else works as in your normal layout. Splits show all their panes, and the status bar
  and borders answer the same gestures.
- The keyboard goes down when you enter. Swipe up from the bottom border, or tap the terminal, to
  raise it.

The page border stays drawn in minimal mode, so you can still hold it and drag to change place.

## Full screen

- **Settings → Terminal → Full screen** ("Hide system bars while using the launcher") hides
  Android's status and navigation bars. It is off by default.
- Hiding the launcher's own status bar in Layout gives its band to the content too.
- **Settings → Terminal → Extend edge colors** ("Match pane padding to nearby terminal colors.", on
  by default) fills the padding around a pane with the colours at its edge, so a full-screen
  program reaches the screen's edge.

## Border gestures

These work on every place and in every layout, minimal mode included:

- Hold the page border and drag sideways to change place.
- Swipe up or down on the bottom border to open or close the keyboard.
- Swipe down or up on the top border to open or fold the status bar, while it stands along the top.

See [Learn the launcher](Learn_The_Launcher.md#move-around).
