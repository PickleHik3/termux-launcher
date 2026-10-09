# Keyboard shortcuts

These are the launcher's default shortcuts. They work from a hardware keyboard, and from the
[built-in keyboard](Keyboard.md) with Ctrl and Alt latched. To change or add one, see
[Custom keybindings](Custom_Keybindings.md).

## See them on screen

- Latch **Ctrl** and **Alt** on the built-in keyboard and every key with a shortcut lights up,
  coloured by what it does, with a legend in the top-right corner. Press a lit key to run it.
- **Settings → Terminal → Sessions and panes → Shortcut hints** ("Show pane shortcuts while holding
  Ctrl+Alt. Off lights the ? key instead.") turns that legend off.
- The [command palette](Command_Palette_And_Actions.md) shows each action's shortcut beside it, and
  you can search by shortcut: typing `ctrl+alt+w` finds what it runs.

Shortcuts follow physical key positions, not the character your layout types. An upper-case
letter in a shortcut means Shift, so `Ctrl+Alt+R` is `Ctrl+Alt+Shift+R`.

## Which column applies

**Settings → Terminal → Sessions and panes → Split-pane controls** is on by default. With it on,
the terminal has windows and panes and the **Split panes on** column applies. Turning it off gives
traditional single-pane Termux behaviour and the **Split panes off** column applies. "(to the shell)" means
the keys go to the shell.

## Panes

| Shortcut | Split panes on | Split panes off |
|---|---|---|
| `Ctrl+Alt+Enter` | **New pane** (splits the focused pane along its longer side) | (to the shell) |
| `Ctrl+Alt+V` | **Split pane vertically** (side by side) | **Paste** |
| `Ctrl+Alt+H` | **Split pane horizontally** (stacked) | (to the shell) |
| `Ctrl+Alt+W` | **Kill focused pane** | (to the shell) |
| `Alt+Arrow` | **Move pane focus** that way (goes to the shell when there is no pane that way) | (to the shell) |
| `Ctrl+Alt+Shift+Arrow` | **Resize pane**: grow the focused pane that way | (to the shell) |
| `Ctrl+Alt+L` | **Next pane layout** | (to the shell) |
| `Ctrl+Alt+F` | **Float / dock pane** | (to the shell) |
| ``Ctrl+Alt+` `` | **Toggle scratchpad** | (to the shell) |
| `Ctrl+Alt+R` | **Rename window…** | **Rename pane…** |

"Vertical" means the dividing line is vertical, so the panes sit side by side.

## Windows

| Shortcut | Split panes on | Split panes off |
|---|---|---|
| `Ctrl+Alt+C` | **New window** | **New session** |
| `Ctrl+Alt+X` | **Close window**, after a confirmation | (to the shell) |
| `Ctrl+Alt+[` / `Ctrl+Alt+]` | **Previous window** / **Next window** | (to the shell) |
| `Ctrl+Alt+Left` / `Ctrl+Alt+Right` | **Previous window** / **Next window** | **Close sessions** / **Open sessions** (the sessions drawer) |
| `Ctrl+Alt+1` … `Ctrl+Alt+9` | **Switch to window** 1 to 9 in this session | **Switch to session by number** 1 to 9 |

On the Display place, next and previous window step through the apps open on the display.

## Sessions

| Shortcut | Split panes on | Split panes off |
|---|---|---|
| `Ctrl+Alt+Up` / `Ctrl+Alt+Down` | **Previous session** / **Next session** | same |
| `Ctrl+Alt+P` / `Ctrl+Alt+N` | **Previous session** / **Next session** | same |
| `Ctrl+Alt+Shift+1` … `Ctrl+Alt+Shift+9` | **Switch to session by number** 1 to 9 | same |
| `Ctrl+Alt+Shift+C` | **New session** | same |
| `Ctrl+Alt+Shift+X` | **Close session**, after a confirmation | (to the shell) |
| `Ctrl+Alt+Shift+R` | **Rename session…** | (to the shell) |
| `Ctrl+Alt+Shift+S` | **Toggle sessions** (open or close the sessions drawer) | same |

## Terminal and app

These work the same in both columns.

| Shortcut | Action |
|---|---|
| `Ctrl+Alt+Shift+P`, or `Ctrl+Alt+Space` then `P` | **Command palette** |
| `Ctrl+Alt+M` | **Terminal actions** (the action sheet) |
| `Ctrl+Alt+K` | **Toggle keyboard** |
| `Ctrl+Alt++` or `Ctrl+Alt+Shift+=` | **Increase font size** of the focused pane |
| `Ctrl+Alt+-` | **Decrease font size** of the focused pane |
| `Ctrl+Alt+U` | **Quick select**: pick a URL, path or hash on screen by its label |
| `Ctrl+Alt+S` | **Search scrollback** |

## On the built-in keyboard's space bar

The space bar has its own swipes, which are not shortcuts and are not changed by the bindings
file: up opens the command palette, up-left and up-right switch to the previous and next window,
down-left and down-right to the previous and next session. See [Keyboard](Keyboard.md).

## When a shortcut does nothing

- Check whether **Split-pane controls** changes its meaning (the two columns above).
- Open **Key inspector** from the command palette and press the shortcut. It shows the key event,
  the binding that claimed it, and the bytes sent to the shell. It has no shortcut of its own, so
  it can inspect any key.
- Check `~/.termux/termux-launcher-bindings.conf` for a line that replaces or removes it, then run
  `termux-reload-settings`.
- On the Display place every key goes to the Linux program, so launcher shortcuts do not apply.
- With **Settings → Keyboard → Hardware keyboard → Android language shortcut** on, `Ctrl+Space`
  goes to Android, not to the launcher.
