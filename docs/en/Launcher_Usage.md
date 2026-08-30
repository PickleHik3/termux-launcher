# Using Termux Launcher

Termux Launcher keeps the shell in the center of an Android home screen. You can launch apps without
leaving the terminal, organize work in native windows and panes, and save the layout as a workspace.
This guide describes v0.2.31.

## Understand the terminal hierarchy

```text
Session
└── Window
    ├── Tiled pane
    ├── Tiled pane
    └── Optional floating pane
```

- A **session** is the top-level group shown by the numbered badge at the left of the status row.
- A **window** is a tab-like workspace shown beside the badge. Each session can have several.
- A **pane** is one shell/PTTY. A window can contain recursively split and floating panes.
- A **workspace** is a saved definition of sessions, windows, panes, titles, and working directories.

Closing a pane, window, or session ends its shells. Switching between them does not.

## Touch works like a mouse

Terminal touch handling differs from stock Termux — it is tuned for full-screen TUIs, not just
shell prompts:

- **Drag scrolls, always.** Inside mouse-aware apps the drag becomes scroll-wheel events, so lists
  in `htop`, lazygit, or vim scroll naturally.
- **Tap sends a mouse click** when the running app tracks the mouse.
- **Press and hold briefly, then drag, to hold the mouse button down.** A small haptic marks the
  moment your finger becomes a held mouse drag — from there you can select text in vim, drag tmux
  splits, or resize TUI panes exactly like a desktop mouse would.
- **A quick long-press without moving** still starts ordinary text selection with the copy toolbar.
- **Pinch to zoom** changes the focused pane's font size, with jitter filtering so two-finger
  scrolling does not zoom by accident.

## Use the status row

The top surface is both status display and navigation:

- Tap the numbered session badge to open the sessions panel.
- Tap a window pill to switch windows.
- Tap `+` to create a window.
- Tap CPU, memory, or weather to open its detail panel.
- In the expanded status panel, tap the clock to open Android's clock app or the cog to open Settings.

The CPU and memory values describe the Android device, not only the foreground shell. Weather needs
location permission and credits Open-Meteo in its detail view.

A window pill breathes while its foreground process group is consuming CPU. A silent but busy build
can therefore appear active; a sleeping or idle TUI does not count as work merely because it remains
open.

A background window that rings the terminal bell receives a pulsing error-colored rim when it needs
attention. Focusing that window clears the state; a bell in the already-focused window is not marked
as new attention.

## Launch Android apps

### Pinned apps

Tap an icon in the dock to open it. Long-press an app for actions such as pinning, moving, or placing
it in a folder. Long-press empty space in the pinned row to open the list editor.

Manage the whole row under **Settings → Launcher & apps → Edit pinned apps**. The same page controls
the pinned row, most-used page, notification dots, and icon browsing behavior.

### A–Z index

Scrub horizontally across the A–Z row to jump to installed apps beginning with a letter. The index is
a browsing control, not a text field. It can be hidden under **Settings → Launcher & apps →
Alphabets row**.

### Search from the shell prompt

At an idle prompt, type the app-search prefix followed by a query:

```text
%settings
%camera
```

The default prefix is `%`. Results replace the normal dock content; choose an app to launch it. Clear
the query to return to the normal dock. Change the prefix under **Settings → Launcher & apps → App
search prefix** if `%` conflicts with a shell workflow.

### Bind an app to a key

Find an installed app in the command palette, long-press its row, then press a modified key
combination. On a hardware keyboard you can focus the row and press `Ctrl+Alt+Enter` instead. Confirm
to write the mapping into `~/.termux/termux-launcher-bindings.conf`; it takes effect immediately.

The app row displays an existing shortcut and is searchable by it. The binding overlay refuses a
bare letter so normal typing cannot be swallowed accidentally.

## Use the command palette

Long-press inside the terminal and choose **Command palette**. On a hardware keyboard, press
`Ctrl+Alt+Shift+P`. The palette searches action names and descriptions, and it keeps unavailable
actions visible with a reason.

Typing from either the built-in or hardware keyboard filters immediately. Use the result list to
browse by action category, move focus with the arrow keys, and press Enter to run the focused action.
The keycaps below the result surface expose frequent split, window, and toggle actions without a
query. Escape closes the palette.

The palette is the complete argument-free action surface: panes, windows, sessions, workspaces,
terminal navigation, clipboard, appearance, installed apps, and settings. Actions that close shells,
replace a workspace, reveal data, or run recorded commands retain their confirmation step.

Useful first searches:

| Search | What you can do |
|---|---|
| `split` | Split the focused pane vertically or horizontally. |
| `layout` | Cycle, equalize, or rotate pane layouts. |
| `window` | Create, switch, rename, or close windows. |
| `session` | Browse, create, clone, rename, switch, or close sessions. |
| `workspace` | Save or load the terminal hierarchy. |
| `font` | Change pane text size or open the font picker. |
| `settings` | Open a settings destination. |

The shorter terminal action sheet also contains URL selection, transcript sharing, wallpaper tools,
Glass Lab, Settings, reset terminal, and kill process.

## Work with panes and windows

Split actions start a new shell in the focused pane's current working directory. The new split starts
at the source pane's font size. Pinch zoom or the font-size actions affect only the focused pane;
panes that were never zoomed continue following the global terminal size.

Use the palette for these actions, or the default hardware-keyboard shortcuts:

| Action | Shortcut |
|---|---|
| Split side by side | `Ctrl+Alt+v` |
| Split top and bottom | `Ctrl+Alt+h` |
| Focus a neighboring pane | `Ctrl+Arrow` |
| Resize the focused pane | `Ctrl+Alt+Shift+Arrow` |
| New window | `Ctrl+Alt+c` |
| Previous/next window | `Ctrl+Alt+[` / `Ctrl+Alt+]`, `Ctrl+Alt+Left` / `Ctrl+Alt+Right` |
| Switch to window by number | `Ctrl+Alt+1` … `Ctrl+Alt+9` |
| Previous/next session | `Ctrl+Alt+Up` / `Ctrl+Alt+Down` |
| Switch to session by number | `Ctrl+Alt+Shift+1` … `Ctrl+Alt+Shift+9` |
| Next automatic layout | `Ctrl+Alt+l` |
| Float or dock the pane | `Ctrl+Alt+f` |
| Rename the window | `Ctrl+Alt+r` |

“Vertical split” means the dividing line is vertical, producing side-by-side panes.

### Resize panes and text

Pane geometry and terminal text size are separate:

- Drag a divider to change the neighboring panes' proportions.
- `Ctrl+Alt+Shift+Arrow` moves the relevant divider from the focused pane. Repeating the shortcut
  continues resizing in that direction.
- **Equalize panes** resets the window's split ratios.
- Resizing a divider releases an active automatic-layout policy so the manual proportions are kept.
- Workspace save records the pane tree and its ratios.

Pinch inside a pane, or run **Increase font size** / **Decrease font size**, to change only that
pane's text size. A new split inherits the source pane's size and a new window inherits the size of
the pane it came from. Panes never zoomed continue following the global terminal size. The scratchpad
keeps its own remembered size.

### Floating panes

Run **Float / dock pane** to detach the focused pane above the tiled layout. Drag its top handle to
move it and use the bottom-right grip to resize it. Run the action again to dock it. A window must
retain at least one tiled pane.

### Automatic layouts

**Next pane layout** cycles through grid, dwindle, tall, fat, horizontal, vertical, and stack. Applying
a layout makes it the policy for that window, so later splits and closes re-tile the survivors.
Manually resizing or moving panes returns the window to manual control — except under dwindle, which
tiles the way Hyprland does: a new pane halves the focused one along its longer side, a dragged pane
takes the half of the pane you drop it on, and the dividers you drag are kept.

## Use sessions

Tap the numbered badge in the status row for the compact sessions panel. Use **Session browser** in
the command palette for the full searchable session → window → pane tree.

The browser can search names, current working directories, and known foreground-process or terminal
titles. Cloning a session starts a fresh shell at the selected pane's working directory; it does not
copy the running process, shell state, scrollback, or pane layout.

Every row in the expanded sessions panel has visible rename and close buttons. Long-press rename
still works, and the palette exposes a Rename action for each session—even when it is not active.

## Save and load workspaces

Search the command palette for **Save workspace**. Enter a name and decide whether to enable **Also
save what is running**.

A normal save records:

- sessions and selected windows;
- window/pane structure and split ratios;
- floating panes and their bounds;
- focused panes, titles, and current working directories.

The optional checkbox records the foreground command in each non-idle pane. It does not freeze or
checkpoint the process.

When loading a workspace:

1. choose **Append** to keep the current hierarchy or **Replace** to remove it after the replacement
   terminals are ready;
2. if commands were recorded, separately confirm whether to run them; and
3. remember that every approved command starts again from the beginning in a normal Termux login
   shell. It is not resumed at its old execution point.

The load picker also lets you delete a saved workspace. Deleting the saved definition does not affect
running sessions. Workspace files are stored at:

```text
~/.termux/workspaces/<name>.json
```

Review a hand-edited workspace before agreeing to run recorded commands.

## Use the built-in keyboard and action row

The built-in keyboard is the default on a fresh install. The action row above it exposes keyboard,
workspace, split, layout, navigation, and scratchpad actions. A long-press or swipe can reveal the
popup action assigned to a key.

Open **Settings → Keyboard & input** to change input method, appearance, colors, typeface, extra keys,
feedback, and `~/.termux/keyboard/layout.xml` support.

The extra keys editor gives the **Display label** and **Swipe-up label** fields a glyph picker
(the `Ω` button) with a searchable catalogue of arrows, box drawing, blocks, shapes, Powerline
separators, technical key symbols, and terminal marks. Key caps are drawn with the user-interface
font, not the terminal font, so Nerd Font and Powerline private-use characters only appear in the
picker when the system font carries them. Anything the device cannot draw is filtered out instead
of being offered as an empty box, which is why a phone without a Nerd Font shows no Powerline
section at all. Those glyphs still render inside the terminal itself whenever the terminal font
provides them.

When `Ctrl+Alt` is latched, keys with launcher bindings light up by action family. Pressing a lit cap
runs the same resolved binding used by a hardware keyboard. Uppercase letters mean Shift, so
`Ctrl+Alt+r` and `Ctrl+Alt+R` are distinct shortcuts.

The keyboard height is stored independently for portrait and landscape. In landscape, the pinned-app
dock becomes a vertical rail at the left and the keyboard uses a lower height ceiling so the terminal
keeps usable rows.

## Personalize the launcher

Long-press a surface to reach its editor, or start from Settings:

- **Appearance** controls wallpaper colors, terminal fonts, icons, surfaces, and Glass Lab.
- **Terminal & status** controls full screen, pane support, clock, CPU, memory, weather, and status
  behavior.
- **Keyboard & input** controls the on-screen input method and built-in keyboard.
- **Launcher & apps** controls pinned apps, A–Z browsing, search, notification dots, and Home behavior.

See the [Settings map](Launcher_Settings.md) for every top-level section and the
[Modern terminal guide](Terminal_Modernization.md) for custom bindings, font configuration, links,
Kitty graphics, and diagnostics. The focused user references are [Terminal fonts](Terminal_Fonts.md)
and [Kitty protocols and compatibility](Terminal_Kitty_Protocols.md).

## Let terminal programs detect capabilities

Each shell receives `TERM_PROGRAM=termux-launcher` and `TERM_PROGRAM_VERSION` set to the installed
version. The terminal also answers XTVERSION and XTSMGRAPHICS queries with its identity and graphics
limits. Programs such as chafa and notcurses can use those replies to select supported rendering
instead of a conservative fallback.
