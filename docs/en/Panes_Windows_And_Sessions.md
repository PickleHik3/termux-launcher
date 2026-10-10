# Panes, windows and sessions

The terminal runs several shells at once without tmux. This page covers splitting it into panes,
grouping panes into windows and windows into sessions, arranging them, and saving the whole set as
a workspace.

## How it fits together

```text
Session
└── Window
    ├── Pane   (one shell)
    ├── Pane
    └── Floating pane
```

- A **pane** is one shell. Panes split in either direction, as deep as you like.
- A **window** is one set of panes. Only the current window is on screen; the window chips in the
  status bar switch between them.
- A **session** holds windows. The numbered badge in the status bar is the current session.

Switching away from a pane, window or session leaves its shells running. Closing one ends them.

All of this needs **Settings → Terminal → Sessions and panes → Split-pane controls** ("Enable window
splitting and tmux-style pane shortcuts."), on by default. Turning it off returns to single-pane
Termux behaviour, closes the extra panes and disables pane and window actions, so finish or save
work in those shells first.

## Split and close panes

| To | Shortcut | Palette |
|---|---|---|
| Open a pane where there is the most room | `Ctrl+Alt+Enter` | **New pane** |
| Split side by side | `Ctrl+Alt+V` | **Split pane vertically** |
| Split top and bottom | `Ctrl+Alt+H` | **Split pane horizontally** |
| Move focus to a neighbouring pane | `Alt+Arrow` | **Move pane focus** |
| End the focused pane's shell | `Ctrl+Alt+W` | **Kill focused pane** |

The **New pane** key on the extra-keys row does the same as `Ctrl+Alt+Enter`; its swipe-up opens a
new window. A new pane starts a fresh shell in the focused pane's working directory. `Alt+Arrow`
goes to the shell when there is no pane that way. The full list is in
[Keyboard shortcuts](Keyboard_Shortcuts.md).

## Windows

- Tap a window chip in the status bar to switch to it; tap `+` to make a new one.
- `Ctrl+Alt+C` makes a window, `Ctrl+Alt+[` / `Ctrl+Alt+]` or `Ctrl+Alt+Left` / `Ctrl+Alt+Right`
  step through them, `Ctrl+Alt+1` to `9` jump to one, and `Ctrl+Alt+X` closes the current window
  after asking.
- `Ctrl+Alt+R` renames the window (up to 14 characters). An empty name brings back the automatic
  label: the file open in an editor, the running program, or the folder of an idle shell.

A chip also shows what its window is doing: a ring while a program is using the processor, a bell
when it rang, a tick or cross when a command finished while you were elsewhere, and a coloured dot
when an AI agent in it is working or waiting for you ([Agent status](Agent_Status.md)). More on the
status bar in [Status bar](Status_Bar.md).

## The pane corner tab

Hold a corner of a split pane to bring out its tab:

- **Close** ends that pane.
- **Move**: drag the move icon onto another pane to move the pane there. Under the `dwindle`
  layout it takes the half of that pane you drop it on, and shows which half before you let go.
- **Maximise** zooms the pane over the window. The tab stays out while it is zoomed and offers
  **Restore pane**.

A pane that is alone in its window keeps the general tab instead: minimal mode, **Turn on
automatic tiling** / **Turn off automatic tiling**, settings and help.

### Swap panes with a two-finger flick

Instead of **Move**, flick two fingers briskly across a split pane towards one of its edges. Its
shell swaps with the pane across that edge, and both panes slide into place. The layout's shape
and sizes stay the same, under `dwindle` too. In a T layout, flicking a lower column up swaps it
with the full-width row, and the row's shell drops into the lower cell.

- The flick has to be quick and mostly in one direction: about 48dp within 300ms.
- Nothing happens on a slow drag, with a third finger, if a finger lifts early, on a pane that is
  alone, maximised or floating, while text is selected, or towards an edge with no pane beyond it.
- The flick does not bring out the corner tab, and puts it away if it is out.
- Pinch to zoom and two-finger scroll work as before.

## Resize panes and text

- **Drag the gap between two panes** to move that divider. It snaps to whole character cells when
  you lift your finger, with a light tick. Holding a corner does not resize; it only shows the tab.
- `Ctrl+Alt+Shift+Arrow` grows the focused pane in that direction; repeat to keep going.
- **Equalize pane dividers** in the palette resets every divider in the window to equal shares.
- **Settings → Terminal → Sessions and panes → Focus active pane** ("Enlarge the active pane and
  shrink the others.") gives the pane you tap the larger share.

Text size is separate from pane size. Pinch inside a pane, or run **Increase font size** /
**Decrease font size** (`Ctrl+Alt++` / `Ctrl+Alt+-`), to change only that pane. A new split starts
at its source pane's size, and panes you never zoomed follow the global size — set it with
**Settings → Theme & fonts → Terminal fonts → Font size**. The scratchpad keeps
its own size.

## Automatic layouts

**Next pane layout** (`Ctrl+Alt+L`) cycles the window through these layouts without restarting any
shell; a window with no layout yet starts at `grid`. **Pane layout** in the palette picks one by
name.

| Layout | Result |
|---|---|
| `grid` | Near-square rows of equal panes |
| `dwindle` | Each new pane halves the focused one along its longer side |
| `tall` | A half-width main pane on the left, the rest stacked on the right |
| `fat` | A half-height main pane on top, the rest below |
| `horizontal` | Every pane side by side |
| `vertical` | Every pane in one column |
| `stack` | The focused pane fills the window; the others keep running behind it |

A layout you apply keeps managing the window: later splits and closes re-tile into it. Shaping the
window by hand (dragging or key-resizing a divider, **Rotate pane layout**, **Move pane to edge**)
hands it back to manual control; **Equalize pane dividers** does not. Under `dwindle` the dividers
you drag stay where you put them.

**Settings → Terminal → Sessions and panes → Automatic tiling** ("Split the current pane when
opening a new one.") starts every new window under `dwindle`. The lone pane's corner tab switches
it too.

## Floating panes

**Float / dock pane** (`Ctrl+Alt+F`) lifts the focused pane above the others. Move it by its
top-left corner and resize it from its bottom-right corner. Tap the pill at its top for **Close**
and **Dock**, or run the action again to dock it. The last tiled pane in a window cannot float.
Inside the pane, touch works as usual: a hold-and-drag still goes to the running program.

## The scratchpad

**Toggle scratchpad** (``Ctrl+Alt+` ``) shows or hides a floating terminal of its own. Hiding it
does not close it; its shell keeps running.

## Sessions

The **Sessions** drawer lists everything that is running. Open or close it by tapping the session
badge in the status bar, the **Sessions** key on the extra-keys row, **Sessions** or **Toggle
sessions** in the palette, or `Ctrl+Alt+Shift+S`.

- The **Live** tab lists sessions with their window and pane counts. Tap a session to switch to
  it; tap **›** to list its windows and tap one to go there.
- Tap **⋯** on a session for **Clone with CWD**, **Rename** and **End** ("End this session?",
  **End** or **Keep**).
- Tap **+** for a new session; hold **+** for a named one.

`Ctrl+Alt+Up` / `Ctrl+Alt+Down` step through sessions, `Ctrl+Alt+Shift+1` to `9` jump to one, and
`Ctrl+Alt+Shift+R` renames the current one. The palette also lists every session twice, once to
switch to it and once to rename it, so you can rename a session you are not in. Session names are
up to 8 characters.

Cloning starts a fresh shell in the current pane's folder. It does not copy the running program,
the shell's state, the scrollback or the layout. Ending the last session starts a fresh one, so the
terminal is never empty.

## Workspaces

A workspace saves the sessions, windows, pane layout and split sizes, floating panes, focus,
titles and each pane's folder, so you can rebuild them later.

- **Save**: at the bottom of the **Live** tab tap **Save as workspace…**, type a name and save.
  Tick **Also save what is running** to record the program in each busy pane. Saving over a name
  asks first. **Save workspace** in the palette opens the same field.
- **Load**: on the **Saved** tab tap a workspace, then **Add to current** or **Replace**. If it
  recorded programs, **Also start what was running** decides whether they run again. **Load
  workspace** in the palette opens this tab.
- **Delete**: tap **⋯** on a saved workspace, then **Delete**. Running sessions are not affected.

A workspace rebuilds structure, not processes. Each recorded program starts again from the
beginning in your normal shell. **Replace** builds the new terminals before removing the old ones,
but it still ends the old shells. Review a hand-edited file before letting it start programs.

Workspaces are files at `~/.termux/workspaces/<name>.json`. A name is up to 64 characters, starts
with a letter or digit, and may contain letters, digits, spaces, `_`, `-` and `.`. The
`workspace.save`, `workspace.load`, `workspace.list` and `workspace.delete` actions are available
to [custom keybindings](Custom_Keybindings.md).

## Let programs open panes

**Settings → Terminal → Sessions and panes → Let programs and agents control panes** ("Programs you
run can open, switch and close panes on their own.") is on by default. It lets a program open its
own panes with `launcherctl pane`; see [LauncherCtl](LauncherCtl.md).
