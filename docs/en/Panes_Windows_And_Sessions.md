# Panes, windows and sessions

The terminal runs several shells at once without tmux. This page covers splitting it into panes,
grouping panes into windows and windows into sessions, arranging them, floating them over Home, and
saving the whole set to load again later.

## How it fits together

```text
Session
└── Window   (a page of the Workspace, or a Home float)
    ├── Pane   (one shell)
    ├── Pane
    └── Floating pane
```

- A **pane** is one shell. Panes split in either direction, as deep as you like.
- A **window** is one set of panes. The **Workspace**, to the right of Home, shows the windows one
  at a time in a row; a window can also float over a Home page as a
  [Home float](#home-floats).
- A **session** holds windows. The numbered badge in the status bar is the current session.

Switching away from a pane, window or session leaves its shells running. Closing one ends them.

All of this needs **Settings → Terminal → Windows & panes → Split-pane controls** ("Enable window
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

The Workspace is one row: the current session's windows in order, the
[display window](X11_Display.md) among them while a display is up. The display window joins the end
of the row when it opens and is a window like the others from then on. A new window opens right
after the one you are on, the display window included. Switching session swaps the row; opening a
Linux app or Display brings the display window into the session you are in. Closing the last window
slides Home back in.

- Drag sideways from a window's border to move to the next or previous window. Dragging right from
  the first window goes back to the last Home page.
- Swipe sideways across a window's text to do the same, on any pane of a split. A swipe that is
  more up and down than across scrolls as before, and the terminal keeps the swipe while you are
  selecting text or a program is using the mouse. **Settings → Home → Gestures → Swipe
  on the terminal to switch windows** ("Swipe sideways across the text to move between windows and
  Home") turns it off; the border drag stays.
- Tap a window chip in the status bar to switch to it; tap `+` to make a new one.
- Hold a window chip until it lifts, then drag it along the bar to move that window to another
  place in the row. The other chips make room, and letting go drops it there. The window you are
  on stays the one you are on, and the border drag, the drawer, the `Ctrl+Alt` window keys and
  Saved sessions all follow the new order. The display window's chip moves like any window's; a
  Home float's chip stays where it is. The palette's **Move window left** and **Move window right**
  do the same one place at a time, and the `window.move` action takes `to=left`, `to=right` or a
  place counted from 1, the display window counted where it stands (for
  a key in [Custom keybindings](Custom_Keybindings.md), such as
  `map ctrl+alt+shift+m window.move to=1`).
- `Ctrl+Alt+C` makes a window, `Ctrl+Alt+[` / `Ctrl+Alt+]` or `Ctrl+Alt+Left` / `Ctrl+Alt+Right`
  step through them, `Ctrl+Alt+1` to `9` jump to one, and `Ctrl+Alt+X` closes the current window
  after asking.
- `Ctrl+Alt+R` renames the window (up to 14 characters). An empty name brings back the automatic
  label: the file open in an editor, the running program, or the folder of an idle shell.

A chip also shows what its window is doing: a ring while a program is using the processor, a bell
when it rang, a tick or cross when a command finished while you were elsewhere, and a coloured dot
when an AI agent in it is working or waiting for you ([Agent status](Agent_Status.md)). More on the
status bar in [Status bar](Status_Bar.md).

### Columns: consume and expel

Borrowed from the Niri window manager, where each window of a row is a *column* that can hold
several panes. Here the column is simply a window of the Workspace's row, and two actions move
panes between windows:

- **Consume next window into this pane** (`Ctrl+Alt+,`) pulls the window to the right of the one
  you are on into it: its panes sit beside your focused pane, split the way **New pane** would
  split it, and that window leaves the row. You stay on your window, and the pane that was focused
  in the pulled window has the focus. `pane.consume from=left` pulls the window on the left
  instead. At the end of the row there is nothing to pull, and nothing happens.
- **Expel pane to its own window** (`Ctrl+Alt+.`) pushes your focused pane out into a new window
  of its own, right after the one you are on. The focus goes with it: the new window becomes the
  current one and the Workspace slides to it, as when a window is made. `pane.expel to=left` puts
  the new window right before instead. A window with one pane has nothing to expel, and nothing
  happens.

No shell is closed by either; the bar, the drawer and Saved sessions follow the new row. Home
floats are not in the row, so they are never consumed and cannot be expelled.

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
- **Settings → Terminal → Windows & panes → Focus active pane** ("Enlarge the active pane and
  shrink the others.") gives the pane you tap the larger share.

Text size is separate from pane size. Pinch inside a pane, or run **Increase font size** /
**Decrease font size** (`Ctrl+Alt++` / `Ctrl+Alt+-`), to change only that pane. A new split starts
at its source pane's size, and panes you never zoomed follow the global size — set it with
**Settings → Terminal → Text & display → Terminal fonts → Font size**. The scratchpad keeps
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

**Settings → Terminal → Windows & panes → Automatic tiling** ("Split the current pane when
opening a new one.") starts every new window under `dwindle`. The lone pane's corner tab switches
it too.

## Home floats

A terminal opened from Home floats over the Home page instead of taking you to the Workspace. Open
one with **Terminal** in a Home page's long-press menu, **New float here** on the Terminal entry,
or **Open terminal here** in the command palette on Home. It is a window of the current session,
so the window chips, the sessions drawer and the shortcuts all see it.

- It opens as a window above the dock, takes focus and brings the keyboard up.
- It stays on the Home page it was opened on and moves with that page. Open as many as you like;
  tap one to bring it to the front. The focused float wears the active rim.
- Drag its **titlebar** to move it and its bottom-right corner to resize it. Each float remembers
  its size and spot in each orientation, across a restart too (see [After a restart](#after-a-restart)).
- The titlebar carries no readings; your CPU and memory use are in the status bar, which stands
  on Home as it does in the Workspace.
- The titlebar's buttons: **Minimise** tucks the float into its window chip in the status bar,
  **Maximise** fills the room and becomes **Restore size**, and **Close** closes it and its shell.
  Long press the titlebar's handle for its menu: **Dock** moves the float into the Workspace as a
  window right after the current one and takes you there.
- A float that fills the room makes way for the keyboard as a tiled pane does: it shortens to the
  room above the keyboard and grows back when it closes. A float you made smaller keeps its size
  and lifts so the cursor's line stays above the keyboard, never past the top of the room, and
  settles back when it closes. A tap on the page outside any float puts the keyboard away, as does
  minimising or closing the focused float.
- Home wears the same status bar as the Workspace. A minimised float waits there as a dimmed
  window chip with a dot, from every page and from the Workspace too; tap it to go to its page and
  bring the float back. If you hide the status bar, reach a minimised float from the command
  palette or the app drawer.

A float does not keep the Workspace open. The status bar's chips are the same on both halves, so
tapping a window's chip on Home slides you to that window.

## Floating panes

**Float / dock pane** (`Ctrl+Alt+F`) lifts the focused pane above the others. A floating pane wears
a **titlebar**, a glass strip along its top: drag the strip to move the pane, and resize it from
its bottom-right corner. The titlebar's buttons are **Maximise**, which fills the window and becomes
**Restore size** to put the pane back where it was, and **Close**, which closes its shell. Long
press the titlebar's handle for its menu, where **Dock** puts the pane back among the others;
running the action again also docks it. The last
tiled pane in a window cannot float. Inside the pane, touch works as usual: a hold-and-drag still
goes to the running program. (The titlebar replaces the pill and the move corner floating panes
used to have.)

## The scratchpad

**Toggle scratchpad** (``Ctrl+Alt+` ``) shows or hides a floating terminal of its own. It wears the
same titlebar as a floating pane, with one more button: **Minimise** hides it, as the toggle does.
Hiding it does not close it; its shell keeps running. **Close** ends its shell.

## Sessions

The **Sessions** drawer lists everything that is running. Open or close it by tapping the session
badge in the status bar, the **Sessions** key on the extra-keys row, **Sessions** or **Toggle
sessions** in the palette, or `Ctrl+Alt+Shift+S`.

- The **Live** tab lists sessions with their window and pane counts. Tap a session to switch to
  it; tap **›** to list its windows and tap one to go there. A Home float is marked **Float**, with
  its Home page when there is one ("Float · page 2"); tapping it pages Home there and brings the
  float to front.
- While a display is running, **Displays** follows the sessions: one row per display, with the
  app windows open on it listed underneath. Tap the display to go to it, or an app window to raise
  it.
- Tap **⋯** on a session for **Clone with CWD**, **Rename** and **End** ("End this session?",
  **End** or **Keep**).
- Tap **+** for a new session; hold **+** for a named one.
- **Home** at the bottom of either tab goes to the first Home page.

`Ctrl+Alt+Up` / `Ctrl+Alt+Down` step through sessions, `Ctrl+Alt+Shift+1` to `9` jump to one, and
`Ctrl+Alt+Shift+R` renames the current one. The palette also lists every session twice, once to
switch to it and once to rename it, so you can rename a session you are not in. Session names are
up to 8 characters.

Cloning starts a fresh shell in the current pane's folder. It does not copy the running program,
the shell's state, the scrollback or the layout. Ending the last session, or closing the last
window or Home float, leaves the Workspace empty: the launcher stays on Home (from the Workspace,
Home's last page slides in), and the Terminal entry opens a fresh window.

## Saved sessions

Saved sessions keep the sessions, windows, pane layout and split sizes, floating panes, focus,
titles and each pane's folder, so you can rebuild them later. A Home float is saved with its page
and shape, and loads back as a float on that page (minimised if it was); a page that no longer
exists lands it on the last Home page. Sessions saved with only Home floats load back as those
floats alone, with the Workspace empty.

- **Save**: at the bottom of the **Live** tab tap **Save sessions…**, type a name and save.
  Tick **Also save what is running** to record the program in each busy pane. Saving over a name
  asks first. **Save workspace** in the palette opens the same field.
- **Load**: on the **Saved sessions** tab tap a name, then **Add to current** or **Replace**. If
  it recorded programs, **Also start what was running** decides whether they run again. **Load
  workspace** in the palette opens this tab.
- **Delete**: tap **⋯** on saved sessions, then **Delete**. Running sessions are not affected.

Loading rebuilds structure, not processes. Each recorded program starts again from the
beginning in your normal shell. **Replace** builds the new terminals before removing the old ones,
but it still ends the old shells. Review a hand-edited file before letting it start programs.

Saved sessions are files at `~/.termux/workspaces/<name>.json`. A name is up to 64 characters, starts
with a letter or digit, and may contain letters, digits, spaces, `_`, `-` and `.`. The
`workspace.save`, `workspace.load`, `workspace.list` and `workspace.delete` actions are available
to [custom keybindings](Custom_Keybindings.md).

## After a restart

The launcher keeps your windows, their panes and your Home floats without you saving anything.
When the screen turns or Android recreates the launcher while your shells keep running, everything
comes back attached to the same shells, scrollback and running programs included. When Android
stops the whole app in the background, the next launch rebuilds the windows, their split panes
and the floats on their pages, each pane a fresh shell in the folder it was in, and says "Windows
restored from before the restart". Programs that were running are not started again, and the
scrollback is gone. **Start in** still decides whether Home or the Workspace shows first. If you
closed every window and float, or chose **Exit** in the notification, the launcher starts the same
way: an empty Workspace, with Home showing and no shell (with Start in set to Terminal, one
fresh window). Saved sessions are separate and stay as you saved them.

## Let programs open panes

**Settings → Terminal → Windows & panes → Let programs and agents control panes** ("Programs you
run can open, switch and close panes on their own.") is on by default. It lets a program open its
own panes with `launcherctl pane`; see [LauncherCtl](LauncherCtl.md).

A program decides where its pane goes and how big it is: beside or below the pane it runs in (or
another one it names), at a share of that pane or a number of columns or rows, and it can resize
that pane later. A new pane starts in the folder the program runs in. A program can look up the
pane it runs in (its window and size) to choose a sensible place, and wait for a pane of its own to
show a given line of output instead of checking again and again. It can also open a full-size
window of its own, bring any window to the front and close the windows it opened. It can type into,
read, wait on, resize and close only the panes and windows it opened itself, never yours. Placing a pane by direction or size in a window that uses an automatic
layout (grid, tall, ...) switches that window to manual splits, so the new pane stays where it was
put.

```sh
launcherctl pane open --direction right --size 0.4 --no-focus -- htop
```
