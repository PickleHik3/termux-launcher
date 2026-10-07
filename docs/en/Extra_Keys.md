# Extra keys

The extra-keys row is the strip of keys in the dock, separate from the
[built-in keyboard](Keyboard.md). This page covers what the shipped row does, how to edit it, and
how to write it by hand in `~/.termux/termux.properties`.

## The shipped row

A fresh install has one row of seven keys. Swipe up on a key to run its second action; the small
label above a key shows what that is.

| Key | Tap | Swipe up |
|---|---|---|
| Keyboard | Show or hide the keyboard | **Next keyboard type** (docked, floating, split) |
| Mouse | **Mouse mode** on or off | |
| Widgets | **Go to Widgets** | |
| Terminal | **Go to Terminal** | |
| Display | **Go to Display** | |
| New pane | **New pane** | **New window** |
| Sessions | **Sessions** (the sessions drawer) | **New session** |

The three place keys are coloured with the theme's primary, secondary and tertiary colours. The
second page ships empty, and an empty page is not shown.

## Where the row stands

In **Appearance → Layout** the row can be moved to any edge, dropped into the slot under the
keyboard, or hidden by dragging it onto the eye-off button. See
[Layout and full screen](Layout_And_Full_Screen.md).

The row follows you from place to place, but a key that cannot act where you are goes faint and
stops responding. On the Display place, pane, window and session keys are dimmed. On Widgets,
typing keys, the modifiers, **PASTE** and **SCROLL** are dimmed.

## Edit the row

Open **Settings → Keyboard → Terminal extra keys** ("Keys and swipe actions"), or run **Edit key
row** from the [command palette](Command_Palette_And_Actions.md). The editor writes the same
`extra-keys` property you can edit by hand, so you can start in the editor and keep hand-editing
later.

- **Pages**: swipe the row sideways to switch pages. A page with no keys stays out of the row
  until it has one. With no keys on the first page, the whole row stays hidden.
- **Rows**: **Add row** adds one below the last. Drag a row by its handle to reorder it. Removing a
  row asks first, since its keys go with it.
- **Keys**: hold a key to drag it within or between rows. Tap a key to set what **Tap** and
  **Swipe up** do (a terminal key, a modifier, a row control, a macro, text, or a launcher action),
  its **Label** (blank shows the key name), its **Swipe-up label**, and its **Colour**.
- **Glyphs**: the **Ω** button next to a label opens a searchable picker of arrows, blocks, shapes,
  Powerline separators, technical symbols, terminal marks and the bundled Nerd Font icons. Search
  icons by name (`keyboard`), family (`md`, `fa`) or Nerd Font name (`nf-md-folder`), or paste any
  icon straight into a label.
- **Save** applies the row at once. **Discard** asks first if you have unsaved edits.

Next to the editor row, **Uppercase key labels** ("Show ESC, TAB and other labels in capitals.")
is on by default.

## Presets

The editor's **Presets** section replaces the keys on the current page in one tap, after a
confirmation:

- **Before the update**: the row you had before you accepted a new default (only shown when there
  is one).
- **Launcher default**: the shipped row above.
- **Classic Termux**: upstream Termux's row, `ESC TAB CTRL ALT - DOWN UP`, with `|` on the swipe-up
  of `-`.
- **Two rows**: upstream Termux's two-row layout, `ESC / - HOME UP END PGUP` over
  `TAB CTRL ALT LEFT DOWN RIGHT PGDN`. No launcher actions.
- **Clear page**: empties the page.

When an update ships a new default row, a **New key row** card offers **Switch** or **Keep mine**.
**Switch** keeps your old row under **Before the update**, so going back is one preset away.

## Write the row by hand

Set `extra-keys` (first page) and `extra-keys2` (second page) in `~/.termux/termux.properties`,
then run `termux-reload-settings`. The value is a list of rows, each a list of keys:

```properties
extra-keys = [[ESC, TAB, CTRL, ALT, LEFT, DOWN, UP, RIGHT]]
```

A key is a name or an object:

| Field | Value | Meaning |
|---|---|---|
| `key` | one key name or action | A terminal key, a row control (`KEYBOARD`, `PASTE`, `SCROLL`), or `tool:<action id>` |
| `macro` | space-separated keys | Several terminal keys in order, such as `"CTRL b d"` |
| `display` | text or a glyph | The label shown on the key |
| `popup` | a key name or another object | The swipe-up action |
| `color` | a theme role: `primary`, `secondary`, `tertiary`, `error`, `primary_container`, `secondary_container`, `tertiary_container`, `error_container`, `surface_variant`, `black`, `white` | Paints the key in that colour |

An item has either `key` or `macro`, never both. The file is read as UTF-8, so paste an icon
itself; `\uXXXX` escapes are not read.

### Launcher actions on a key

```text
tool:<action id>
tool:<action id>:name=value,name=value
```

Unlike a keyboard layout slot, an extra key can pass arguments, so it can launch an app or pick a
layout:

```properties
extra-keys = [[ \
  {key: "tool:app.command_palette", display: "⌘"}, \
  {key: "tool:app.launch:query=com.whatsapp", display: "WA"}, \
  {key: "tool:pane.layout:layout=grid", display: "▦"}, \
  {key: "tool:pane.focus_direction:direction=left", display: "←", popup: {key: "tool:pane.move_to_edge:edge=left", display: "⇤"}}, \
  {key: "tool:workspace.picker", display: "▤", popup: {key: "tool:workspace.save_prompt", display: "⛁"}}, \
  {key: "tool:terminal.toggle_scratchpad", display: "▣"} \
]]
```

Names and values are trimmed and separated by commas; there is no quoting inside a `tool:` string,
so prefer a package name for `app.launch`. Pane and window actions do nothing while
**Settings → Terminal → Sessions and panes → Split-pane controls** is off. Every action id and its
arguments are listed in [Command palette and actions](Command_Palette_And_Actions.md).

### Macros for shell programs

```properties
extra-keys = [[ \
  {key: ESC, popup: {macro: "CTRL b d", display: "tmux detach"}}, \
  {macro: "CTRL b c", display: "tmux +win"}, \
  {macro: "CTRL c", display: "^C"} \
]]
```

Macros type keystrokes into the shell. They are unrelated to the launcher's own pane actions.

## Which surface to use

| Goal | Where |
|---|---|
| A hardware-keyboard shortcut or a chord | [`termux-launcher-bindings.conf`](Custom_Keybindings.md) |
| A key on screen with a swipe-up second action | `extra-keys` in `termux.properties` |
| An on-screen key that runs an action with arguments | `extra-keys` in `termux.properties` |
| A swipe on the keyboard itself | `~/.termux/keyboard/layout.xml` ([Keyboard](Keyboard.md#your-own-layout)) |
| Keystrokes for tmux or another shell program | an extra-keys `macro`, or `send-key` / `send-text` in the bindings file |

If a `tool:` key does nothing, check the action id and argument names, check whether it needs
split panes or a text selection, and look in the app log; failed keys are logged rather than
shown as repeated messages.
