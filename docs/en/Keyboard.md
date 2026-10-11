# The built-in keyboard

This page covers the on-screen keyboard that ships inside the launcher: showing and hiding it, its
swipes and layers, the three keyboard types, and the settings that change how it types and looks.
The row of keys under the terminal is a separate thing; see [Extra keys](Extra_Keys.md).

The keyboard is a port of [Unexpected Keyboard](https://github.com/Julow/Unexpected-Keyboard) by
Jules Aguillon. Every key carries up to eight extra characters on its corners, typed by swiping
the key toward them. The port is built into the app as a view, so there is nothing to install and
your system keyboard in other apps is unchanged.

## Show, hide or switch it off

- **Tap the terminal** to bring the keyboard up.
- **Swipe up from the bottom border** of the terminal, the widgets or the display to open it, and
  swipe down from the same border to put it away. A small pill marks the spot.
- Tap the **Keyboard** key (the first key on the extra-keys row) to show or hide it. On a hardware
  keyboard, `Ctrl+Alt+K` does the same.

A hidden keyboard comes back the next time you tap the terminal. To keep it off:

- run **Keyboard on/off** from the [command palette](Command_Palette_And_Actions.md);
- or, in **Appearance → Layout**, drag the keyboard onto the eye-off button (its tile in the
  hidden row switches it back on);
- or set **Settings → Keyboard → On-screen keyboard** to **Off**.

A swipe up from the bottom border always turns it back on. On the Widgets place the keyboard goes
away, since nothing there takes typing. On the Display place its keys go to the Linux program.

## Choose which keyboard opens

**Settings → Keyboard → On-screen keyboard** has three choices: **Built-in** (the default),
**Android** (your system keyboard) and **Off**.

## Corner swipes and modifiers

- Swipe a key toward a corner to type the character printed there.
- **Ctrl** and **Alt** are real modifiers. Tap one to latch it for the next key; hold it to lock
  it until you tap it again.
- Latch **Ctrl** and **Alt** together and the keys that carry a launcher shortcut light up,
  coloured by what they do, with a short legend in the top-right corner. Pressing a lit key runs
  that shortcut. With **Settings → Terminal → Sessions and panes → Shortcut hints** off, only the
  **?** key lights instead. See [Keyboard shortcuts](Keyboard_Shortcuts.md).
- An upper-case letter counts as Shift in a shortcut, so `Ctrl+Alt+r` and `Ctrl+Alt+R` are
  different shortcuts.

## Swipes on the bottom row

| Key | Swipe | What it does |
|---|---|---|
| Ctrl | up-right | Clipboard history (see [Clipboard history](Touch_Links_And_Clipboard.md#clipboard-history)) |
| Ctrl | down-left | Meta |
| Ctrl | down-right | Numeric layer |
| Alt | up-left | Fn layer |
| Alt | up-right | Hide the keyboard |
| Alt | down-right | Open Settings |
| Space bar | up | [Command palette](Command_Palette_And_Actions.md) |
| Space bar | up-left / up-right | Previous / next window |
| Space bar | down-left / down-right | Previous / next session |
| Space bar | slide left / right | Move the text cursor |
| Space bar | down | Previous layout in your layout cycle |
| Key right of the space bar | up, down, left, right | Arrow keys |
| Key right of the space bar | up-left / up-right / down-left / down-right | Home / Page Up / Page Down / End |
| Enter | up | Voice input (see [Voice input](Voice_Input.md)) |

Shift's up-right swipe is Caps Lock and Backspace's is Delete.

## The Fn layer

Fn is the up-left swipe on the Alt key; there is no separate Fn key. While it is on:

- the top row types F1 to F10, and Z and X type F11 and F12;
- A and S type Esc and Tab;
- D, F and G type Home, Up and End; H, J, K and L type Page Up, Left, Down and Right;
- C, V and B type Insert, Delete and Page Down;
- N and M send `^C` and `^D`.

## Layouts

**Settings → Keyboard → Layouts → Layouts** ("Choose the layouts the keyboard cycles through")
picks which layouts the keyboard steps through, in order, up to 16. The first entry, **Launcher
layout (layout.xml, else QWERTY)**, is your own `~/.termux/keyboard/layout.xml` when it exists and
the bundled QWERTY otherwise. A swipe down on the space bar steps through the cycle. The palette
rows **Cycle keyboard layout** and **Switch keyboard layout** do the same, and the palette lists
each layout by name once the cycle holds more than one.

**Settings → Keyboard → Layouts → Keyboard extra keys** adds optional keys (copy, paste, F11 and
F12, dead keys and more) to the keyboard's own layout. It does not touch the extra-keys row.

## Docked, floating or split

The keyboard has three types:

- **Docked**: along the bottom of the screen. The default.
- **Floating**: a narrower keyboard over the content, placed where you drag it.
- **Split**: two halves pushed against the screen edges with one straight gap between them, for
  two thumbs.

To change type, swipe up on the **Keyboard** key (it cycles docked, floating, split), select the
keyboard in **Appearance → Layout** and pick one of the three type chips, or run **Keyboard type**
or **Next keyboard type** from the palette. The type is kept per place and per orientation, so the
terminal in landscape can float while Home in portrait stays docked.

A floating keyboard moves by its two top corners or by the pill in its handle row. Resize it from
the grip in its bottom-left corner: dragging left makes it wider and dragging up makes its rows
taller, while the right and bottom edges stay where they are.

A split keyboard cuts each row once: q to t | y to p, a to g | h to l, Shift z to v | b to m
Backspace. On the space-bar row, Ctrl, Alt and part of the space bar sit on the left, the rest of
the space bar, the arrow key and Enter on the right. Both pieces of the space bar type a space;
left-side swipes stay on the left piece and right-side swipes on the right. In mouse mode the gap
widens to make room for the touchpad.

**Settings → Keyboard → Size and position** (section **Floating and split**) holds three sliders,
each set separately for portrait and landscape:

| Slider | Default | Range | Meaning |
|---|---|---|---|
| **Floating keyboard width** | 90 | 35 to 100 | Percent of the screen width |
| **Floating keyboard height** | 100 | 60 to 160 | Percent of the normal keyboard height |
| **Split keyboard gap** | 12 | 0 to 45 | Percent of the keyboard width between the two halves |

## Size and look

- **Height**: in **Appearance → Layout**, drag the keyboard's top edge. The handle on the bottom
  of the keys sets the padding under the last row. Both are kept per orientation.
- **Surface**: in **Appearance → Look**, choose **Custom** and tap the keyboard for **Blur**,
  **Grain**, **Opacity**, **Tint**, **Radius** and **Spacing**, plus a **Keyboard theme** button.
  **Key radius** also sits beside the type chips in **Appearance → Layout**.
- **Keyboard theme** ("Colors and typeface") holds a live preview, one chip group (**Key fill**,
  **Border**, **Label**, **Corner labels**, **Bottom label**, **Background**), a set of swatches
  you tap or drag across keys to paint them, **Edit colors** / **Save colors**, **Reset to theme**
  and a hex editor.
- **Font**, on the same page: **Choose font file…** takes a TTF or OTF file from anywhere in
  internal storage; **Use system default** goes back. With a custom font the space bar's icons
  keep the bundled symbols font.

The Keyboard theme page is also in **Settings → Appearance** under **Keyboard look**, beside
**Surface style**; **Look and feel settings** in the palette opens the same page. See
[Look and themes](Look_And_Themes.md).

## Typing and feedback

**Settings → Keyboard → Typing and feedback**:

- **Adaptive touch accuracy** ("Adjust key detection to your typing."), off by default, and
  **Reset learned taps**.
- **Vibration** links to **Settings → App behavior**, where **Allow vibration**, **Keyboard
  vibration** and **Launcher vibration** live.
- **Key sound** uses the system touch-sound volume.
- **Key preview** ("Show the pressed key above your finger."), on by default.

## Hardware keyboards

**Settings → Keyboard → Hardware keyboard**:

- **Hide on-screen keyboard** while a hardware keyboard is connected.
- **Android language shortcut**: "Ctrl+Space switches Android keyboard languages. Off sends it to
  the terminal." Off by default.

If the Android keyboard covers terminal content, try **Settings → Terminal → Compatibility →
Keyboard resize workaround**; turn it off if it causes gaps or jumpy resizing.

## Your own layout

`~/.termux/keyboard/layout.xml` replaces the whole bundled layout, every key and every swipe. The
app never creates it. Start from the shipped example:

```sh
mkdir -p ~/.termux/keyboard
cp ~/.termux/launcher/examples/keyboard-layout.xml ~/.termux/keyboard/layout.xml
termux-reload-settings
```

Any swipe slot can run a launcher action written `tool:<action id>`, optionally
`tool:<action id>:<glyph>` to choose what the slot draws (see
[Command palette and actions](Command_Palette_And_Actions.md) for the ids). A split keyboard cuts a
row where a key carries `split_before="true"`, or a wide key `split_at="<units>"` from its left
edge; a row with neither is cut in the middle. Label font and key radius are settings, not
attributes of the file.
