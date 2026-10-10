# Command palette and actions

The command palette is a searchable list of everything the launcher can do from the terminal. The
same actions sit behind keyboard shortcuts, extra keys and keyboard swipes, so this page also lists
every action id you can use in those places.

## Open it

- Swipe up on the space bar of the [built-in keyboard](Keyboard.md).
- Press `Ctrl+Alt+Shift+P`, or `Ctrl+Alt+Space` then `P`.
- Hold the terminal, tap **More**, then **Command palette**.

## Use it

- Type to filter. Matching is fuzzy and also looks at action ids and shortcuts, so `split`,
  `pane.split` and `ctrl+alt+enter` all find **New pane**.
- With nothing typed, press **↓** to browse by category. Arrow keys move, **Enter** runs, **Esc**
  closes.
- If nothing matches, **Enter** runs what you typed in the shell.
- Rows marked **›** open a list of choices (a layout, a place, an edge). Some rows ask you to type a
  value, such as a new window name, then **Enter** applies it.
- Actions that end shells (**Kill focused pane**, **Close window**, **Close session**) ask first.
- An action that cannot run right now stays in the list, greyed, with the reason, for example
  "Unavailable: compatibility mode is on" when **Split-pane controls** is off.
- The four keys under the list are your most-used actions.

Besides the actions, the palette has three sections of its own:

- **Apps**: your most-used apps with nothing typed, the best matches while you type. A row shows
  any shortcut bound to it. Hold a row to bind a key to it (see
  [Custom keybindings](Custom_Keybindings.md#bind-an-app-from-the-palette)).
- **Sessions**: two rows per session, one to switch to it and one to rename it.
- **Keyboard**: a row per keyboard type and, once you cycle through more than one layout, a row per
  layout, with the one in use marked.

The shorter **Terminal actions** sheet (`Ctrl+Alt+M`, or hold the terminal and tap **More**) has
**Command palette**, **Select URL**, **Share transcript**, **Appearance**, **Enable Wallpaper** or
**Disable Wallpaper**, **Style** (when Termux:Styling is installed), **Settings**, **Reset** and
**Kill process**.

## Action ids

Every action below can be bound in `~/.termux/termux-launcher-bindings.conf`, put on an extra key as
`tool:<id>` (with arguments as `tool:<id>:name=value`), or put on a keyboard swipe as
`tool:<id>` (no arguments). The title is what the palette shows. Shortcuts marked "on" or "off"
apply only with **Split-pane controls** on or off; see [Keyboard shortcuts](Keyboard_Shortcuts.md).
Pane, window, session-browser and workspace actions need **Split-pane controls** on, except
**Kill focused pane** and **Rename pane**, which need only a running session.

### Pane

| Id | Title | Arguments | Default shortcut |
|---|---|---|---|
| `pane.split` | New pane | | `Ctrl+Alt+Enter` (on) |
| `pane.split_vertical` | Split pane vertically | | `Ctrl+Alt+V` (on) |
| `pane.split_horizontal` | Split pane horizontally | | `Ctrl+Alt+H` (on) |
| `pane.focus_direction` | Move pane focus | `direction`: `left`, `right`, `up`, `down` (required) | `Alt+Arrow` (on) |
| `pane.resize` | Resize pane | `direction`: `left`, `right`, `up`, `down` (required) | `Ctrl+Alt+Shift+Arrow` (on) |
| `pane.kill_focused` | Kill focused pane | | `Ctrl+Alt+W` (on) |
| `pane.layout` | Pane layout | `layout`: `stack`, `grid`, `dwindle`, `tall`, `fat`, `horizontal`, `vertical` (required) | |
| `pane.next_layout` | Next pane layout | | `Ctrl+Alt+L` (on) |
| `pane.equalize` | Equalize pane dividers | | |
| `pane.rotate` | Rotate pane layout | `direction`: `clockwise` (default), `counterclockwise` | |
| `pane.move_to_edge` | Move pane to edge | `edge`: `left`, `right`, `up`, `down` (required) | |
| `pane.toggle_float` | Float / dock pane | | `Ctrl+Alt+F` (on) |
| `pane.rename` | Rename pane | `name` (required; empty restores the default) | |
| `pane.rename_prompt` | Rename pane… | | `Ctrl+Alt+R` (off) |

### Window

| Id | Title | Arguments | Default shortcut |
|---|---|---|---|
| `window.new` | New window | | `Ctrl+Alt+C` (on) |
| `window.close` | Close window | | `Ctrl+Alt+X` (on) |
| `window.next` | Next window | | `Ctrl+Alt+]`, `Ctrl+Alt+Right` (on) |
| `window.previous` | Previous window | | `Ctrl+Alt+[`, `Ctrl+Alt+Left` (on) |
| `window.select` | Switch to window | `index`: 0 to 64, counted from 0 (required) | `Ctrl+Alt+1` to `9` (on) |
| `window.rename` | Rename window | `name`: up to 14 characters; empty restores the automatic label (required) | |
| `window.rename_prompt` | Rename window… | | `Ctrl+Alt+R` (on) |

`window.next` and `window.previous` step through the open apps on the Display place.

### Session and workspace

| Id | Title | Arguments | Default shortcut |
|---|---|---|---|
| `session.new` | New session | `name`; `failsafe`: `true` or `false` (default) | `Ctrl+Alt+Shift+C`; `Ctrl+Alt+C` (off) |
| `session.browser` | Sessions | | |
| `session.panel` | Toggle sessions | | `Ctrl+Alt+Shift+S` |
| `session.clone_current` | Clone session with CWD | | |
| `session.next` | Next session | | `Ctrl+Alt+N`, `Ctrl+Alt+Down` |
| `session.previous` | Previous session | | `Ctrl+Alt+P`, `Ctrl+Alt+Up` |
| `session.close_current` | Close session | | `Ctrl+Alt+Shift+X` (on) |
| `session.activate_by_index` | Switch to session by number | `index`: 0 to 64, counted from 0 (required) | `Ctrl+Alt+Shift+1` to `9`; `Ctrl+Alt+1` to `9` (off) |
| `session.rename` | Rename session | `name`: up to 8 characters; empty clears it (required) | |
| `session.rename_at_index` | Rename session by index | `index` then `name` (both required) | |
| `session.rename_prompt` | Rename session… | | `Ctrl+Alt+Shift+R` (on) |
| `workspace.picker` | Load workspace | | |
| `workspace.save_prompt` | Save workspace | | |

### Terminal

| Id | Title | Arguments | Default shortcut |
|---|---|---|---|
| `terminal.toggle_soft_keyboard` | Toggle keyboard | | `Ctrl+Alt+K` |
| `terminal.toggle_toolbar` | Toggle dock | | |
| `terminal.font_size_increase` | Increase font size | | `Ctrl+Alt++`, `Ctrl+Alt+Shift+=` |
| `terminal.font_size_decrease` | Decrease font size | | `Ctrl+Alt+-` |
| `terminal.select_url` | Select URL | | |
| `terminal.select_at_cursor` | Select at cursor | | |
| `terminal.select_all` | Select all | | |
| `terminal.hints` | Quick select | | `Ctrl+Alt+U` |
| `terminal.search_scrollback` | Search scrollback | | `Ctrl+Alt+S` |
| `terminal.share_transcript` | Share transcript | | |
| `terminal.share_selected` | Share selected text | | |
| `terminal.reset` | Reset terminal | | |
| `terminal.jump_previous_prompt` | Jump to previous prompt | | |
| `terminal.jump_next_prompt` | Jump to next prompt | | |
| `terminal.toggle_scratchpad` | Toggle scratchpad | | ``Ctrl+Alt+` `` (on) |
| `terminal.action_sheet` | Terminal actions | | `Ctrl+Alt+M` |
| `extrakeys.edit` | Edit key row | | |

### Clipboard

| Id | Title | Arguments | Default shortcut |
|---|---|---|---|
| `clipboard.paste` | Paste | | `Ctrl+Alt+V` (off) |
| `clipboard.copy_selected` | Copy selected text | | |

### Keyboard

Most of these need the built-in keyboard; **Keyboard on/off**, **Dictate** and **Mouse mode** do
not.

| Id | Title | Arguments | Default shortcut |
|---|---|---|---|
| `keyboard.set_form` | Keyboard type | `form`: `docked`, `floating`, `split` (required) | |
| `keyboard.cycle_form` | Next keyboard type | `direction`: `forward` (default), `backward` | |
| `keyboard.show` | Show keyboard | `source`: `manual` (default), `focus` | |
| `keyboard.hide` | Hide keyboard | `source`: `manual` (default), `focus` | |
| `keyboard.clipboard` | Clipboard history | | |
| `keyboard.toggle_enabled` | Keyboard on/off | | |
| `keyboard.cycle_layout` | Cycle keyboard layout | `direction`: `forward` (default), `backward` | |
| `keyboard.select_layout` | Switch keyboard layout | `layout`: a layout id such as `latn_dvorak`, or `main` for your `layout.xml` (required) | |
| `voice.dictate` | Dictate | | |
| `mouse.toggle` | Mouse mode | | |

### Places

| Id | Title | Arguments | Default shortcut |
|---|---|---|---|
| `wall.go` | Go to place | `page`: `widgets`, `terminal`, `display`, `left`, `right` (required) | |
| `wall.widgets` | Go to Widgets | | |
| `wall.terminal` | Go to Terminal | | |
| `wall.display` | Go to Display | | |

### Appearance

| Id | Title | Arguments | Default shortcut |
|---|---|---|---|
| `appearance.set_wallpaper` | Set wallpaper | | |
| `appearance.toggle_wallpaper` | Toggle wallpaper | | |
| `appearance.toggle_cursor_trail` | Toggle cursor trail | | |
| `appearance.surface_editor` | Surface editor | | |
| `fonts.pick` | Terminal fonts | | |
| `fonts.install` | Install terminal font | `id`: a family id such as `maple-mono` (required); `nerd_icons`: `true` (default) or `false`; `ligatures`: `never`, `cursor` (default), `always`; `weight`: 0 to 1000, 0 keeps the family default | |

The old id `appearance.glass_lab` still runs **Surface editor**.

### App

| Id | Title | Arguments | Default shortcut |
|---|---|---|---|
| `app.command_palette` | Command palette | | `Ctrl+Alt+Shift+P`, `Ctrl+Alt+Space` then `P` |
| `app.open_settings` | Open settings | | |
| `app.open_help` | Help | | |
| `app.open_look_and_feel` | Look and feel settings (opens **Settings → Appearance**) | | |
| `app.open_apps_bar` | Apps bar settings | | |
| `app.key_inspector` | Key inspector | | |
| `app.open_app_drawer` | App drawer | | |
| `app.open_drawer` | Open sessions | | `Ctrl+Alt+Right` (off) |
| `app.close_drawer` | Close sessions | | `Ctrl+Alt+Left` (off) |
| `app.launch` | Launch app | `query`: a package name, app label or stable id (required) | |

### Not in the palette

The palette offers an action that needs one text or choice argument; it leaves out actions that
need a number or several values. `window.select`, `session.activate_by_index` and
`session.rename_at_index` are reached through their shortcuts and the **Sessions** section instead;
`app.launch` through **Apps**; `keyboard.set_form` and `keyboard.select_layout` through
**Keyboard**.

These have no palette row at all but can still be bound:

| Id | Arguments | What it does |
|---|---|---|
| `workspace.save` | `name` (required); `overwrite`, `captureCommands`: `true` or `false` (default) | Save a workspace without asking |
| `workspace.load` | `name` (required); `mode`: `append` (default) or `replace`; `runCommands`: `true` or `false` (default) | Load a workspace |
| `workspace.list` | | List saved workspaces |
| `workspace.delete` | `name` (required) | Delete a saved workspace |

## Actions for programs only

`pane.open`, `pane.list`, `pane.focus`, `pane.close`, `pane.write`, `pane.read` and `terminal.state`
exist for programs and scripts, through `launcherctl` and the local API. A binding can name them,
but they are not meant for a key. `window.open`, `agent.status`, `shell.notify`, `shell.progress`,
`clipboard.write` and `clipboard.read` are only reachable from `launcherctl` and cannot be bound;
the bindings file reports them as unknown. See [LauncherCtl](LauncherCtl.md).
