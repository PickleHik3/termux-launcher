# The wall rework: Home pages, then the Workspace (spec, 2026-10-17)

Status: agreed with the developer on 2026-10-17 after a grilling session (two rounds: the wall,
then Home floats and the Home keyboard); not started. Decision: ADR 0008. Words:
`docs/GLOSSARY.md` › The wall and its chrome. Phase one only; §12 lists what waits. Tracker: umbrella
#76 with children #77 (wall), #78 (Home layout and keyboard), #79 (floats, palette, drawer), #80 (widget
snapshot), #81 (migration and docs), all `ready-for-agent`.

## 1. Why

The launcher is three bordered places on a pane wall, and the Display place is rarely used. The
rework makes Home a conventional launcher and makes the terminal, with the display folded in, a
Workspace beside it. Nothing about the terminal's own layers (session › window › pane) changes.

## 2. The wall

One horizontal row: Home page 1 … Home page n, then the Workspace's windows.

| From | Gesture | Goes to |
|---|---|---|
| A Home page | plain swipe anywhere, left or right | the next or previous Home page |
| The last Home page | swipe left | the first window, if the Workspace has one; otherwise it stops |
| A window | border drag (unchanged: side borders hold-then-drag, top and bottom air plain swipe) | the next or previous window |
| The first window | border drag right | the last Home page |

Parallax runs across the whole row. The seam is continuous but never silent: the chrome arrives
with the first window (status bar, extra keys, corner tabs, borders), so the user sees which half
they are in.

## 3. Home

- **Content**: today's widget grid, already multi-page (`LauncherWidgetRepository`). Long press on an
  empty cell: Add widget, Wallpaper, Terminal (a new **Home float**, §4). Phase one adds nothing
  else to the pages.
- **No borders.** Android's system bar shows as on any launcher. The launcher's status bar is not
  on Home unless a Home float exists (§4.4).
- **Dock**: pinned-apps row and A–Z index, no extra keys. The drawer opens from it as today. The
  dock is an overlay on Home: it never takes room from the grid, and a dock on the bottom row rides
  up with the keyboard and settles back when it closes (the glass blur shows during the ride). The
  existing Under the keyboard option keeps it below instead.
- **Keyboard**: transient by default (§5). Home does not remember a keyboard state; the Workspace
  does, as today.
- **The Terminal entry**: a launcher-provided app entry, listed in the drawer beside Linux Apps and
  pinned to the dock at migration. Tap: go to the Workspace at the window you left, opening one if
  there is none. Long press: New float here. Home's long-press Terminal item does the same as the
  long press.
- **Home layout**: a third saved layout variant beside normal and minimal, edited by the same Layout
  editor and the same put-away machinery, with the element set reduced to the dock's rows (pinned
  apps, A–Z) and the keyboard. The keyboard element is the **always on** toggle (§5.3). Seeded from
  the normal layout with the status bar, extra keys and borders removed and the keyboard put away.
  Style, Corners, Margin and Minimal mode do not apply to Home.

## 4. Home floats

A terminal spawned from Home is a **Home float**: a real window of the current session (chips, the
drawer, Ctrl+Alt shortcuts and Saved sessions all see it) drawn in a float layer over one Home
page, not in the strip.

### 4.1 Birth, move, size

- Born full-size: the room between the status bar (§4.4) and the dock. Chrome draws over floats.
- Moved by its **titlebar**, resized at its bottom-right corner, as today's floating pane
  (`TerminalPaneController`), with the titlebar replacing the top-left move corner and the pill.
- Pinned to the Home page it was opened on and scrolls with it. Any number of floats; a tap brings
  one to the front. Size and spot are remembered per float and per orientation while the process
  lives (today's floats keep fractions of the host; add the orientation key).
- The same glass as a pane; the focused float wears the active rim, as the focused pane of a split
  does.

### 4.2 Titlebar

One translucent glass strip along the float's top, designed to the launcher's look, carrying the
drag handle and four buttons: **Minimise**, **Maximise**, **Close**, **Dock**.

- Maximise toggles full-size and remembers the previous size and spot.
- Minimise flies the float, animated, into its chip in the status bar.
- Dock sends a Home float into the strip as a tiled window right after the current window and
  slides to the Workspace. On a window's floating pane it tiles the pane back, as Dock does today.
- Ctrl+Alt+F on a strip window floats it onto the Home page last shown; on a float it docks it.
- This is the one floating-pane design: window floating panes and the scratchpad get the same
  titlebar. The pill goes.

### 4.3 Focus and the keyboard

- A tap inside a float focuses it; a tap on Home outside any float focuses Home.
- A float born on Home grabs focus and raises the keyboard overlay; a tap outside the float closes
  the keyboard (§5). Minimising or closing the focused float closes it too.
- The keyboard overlays a float and never resizes it. A focused float is lifted so its cursor row
  stays above the keyboard and put back when the keyboard closes (new: today floats are not
  lifted).

### 4.4 The status bar on Home

- Home has no launcher status bar until the first float opens; then the bar slides down from the
  top, in its compact form, and stays while any float is open or minimised. It slides up when the
  last float closes.
- Chips on Home are floats only, across all pages; a chip carries its page's dot. Tapping a chip
  pages Home to that float's page and restores it. Workspace windows are not chips on Home; the
  Terminal entry reaches them.
- Minimising unfolds the bar to compact for the landing if it was folded. The compact and expanded
  forms and the status swipe are today's; the capsule form is phase two (§12).

## 5. The keyboard and the palette on Home

### 5.1 Transient keyboard

- Default: no keyboard on Home. A configurable gesture, default swipe up from the bottom edge
  (today's keyboard swipe), opens the **command palette** and the keyboard together as one
  overlay. Both close on Esc, on focus loss (a tap outside), or when a palette action completes.
- The keyboard is an overlay on Home (already: `KeyboardOverlayPolicy`) and never changes the
  grid.

### 5.2 The palette on Home

Today's palette (`TerminalCommandPalette`, fuzzy, sections for actions, apps, contacts, sessions,
keyboard layouts) shown with a Home action set:

- Home actions: Open terminal here (new float), Go to Workspace, New window, Minimise all floats,
  Restore floats, Add widget, Wallpaper, Layout, Appearance, Settings.
- Apps, Linux apps (new section) and contacts.
- Sessions, windows and floats by name, for jumping like a tmux session picker: a window slides to
  the Workspace, a float pages Home and restores it.
- Terminal-only actions are left out on Home. Enter runs the top match. With the keyboard always
  on (§5.3) and no palette open, a typed key opens the palette with that key as the query; bound
  keys (apps, contacts, actions) fire first.

### 5.3 Keyboard always on

- The keyboard element of the Home layout, shown rather than put away, keeps the keyboard up on
  Home. The grid shrinks by the keyboard's rows on every page.
- The shrink reuses today's reflow (`LauncherWidgetRepository`: a widget that no longer fits is
  shrunk to the grid and moved to the first free spot on its page, or to a new page). Before it
  runs, the previous widget layout is kept as a snapshot, and a one-line toast says what moved.
- Turning the toggle off restores the snapshot if the Home layout was not edited meanwhile. If it
  was, the snapshot is dropped and the regained rows are left empty for the user to fill. Nothing
  the user arranged is ever auto-rearranged on the way back.

## 6. The Workspace

- **Windows in a row**: the current session's windows in their order, then the display windows.
  Switching sessions (drawer, Ctrl+Alt+Shift+C) swaps the window part; the display windows stay.
  Home floats are session windows but are never in the row.
- **A window** is what it is today: a terminal window with its split tree, or (new) a display
  window. Ctrl+Alt+V and Ctrl+Alt+H split inside a window; Ctrl+Alt+C opens a window; Ctrl+Alt+Shift+C
  opens a session. Nothing about panes changes.
- **Insertion**: a new window (Ctrl+Alt+C, a Terminal=true Linux app, Dock on a Home float) lands
  right after the current window. No drag-to-reorder in phase one.
- **Switching**: the border drag, the window chips in the status bar (display windows are chips
  too; Home floats are chips marked as floats), the Ctrl+Alt shortcuts and the drawer. The screen
  glyph jumps to the first display window, or starts the display if none is up.
- **Chrome and layout** are today's, unchanged: the Workspace layout (normal and minimal), Style,
  Corners, Margin, status bar, dock with extra keys, corner tabs, keyboard swipe, status swipe.
  Display windows wear the same chrome as terminal windows.
- **Lifetime**: the Workspace exists while the row has at least one window (terminal or display).
  Closing the last one slides Home back in. Home floats do not keep it alive and do not need it.

## 7. Display

- One display window per X display, shown whole; the display's own window manager (openbox, or a
  desktop session's) manages the toplevels. Per-toplevel windows wait for a rootless server (§12).
- **Starting**: on demand. A Linux app tapped in the drawer starts the server if needed, runs the
  app and opens or raises the display window (`X11LinuxAppRunner`, as today). A **Display** entry
  under Linux Apps opens the bare display, replacing the Start display button. The Start command
  setting stays.
- **The Display switch and the usage mode retire.** Linux apps list whenever Show Linux apps in
  drawer is on. Nothing about X11 is a mode.
- **Stopping**: long-press Stop as today; the display window then leaves the row.
- Terminal=true Linux apps open a new terminal window after the current one, not a pane.
- `active/display-fullscreen/PARKED.md` is answered: full-screen display is a display window in
  minimal mode. Delete it when this ships.

## 8. The sessions drawer

Today: Live and Saved tabs, sessions with windows and panes nested, a + in the header, a one-row
footer Save as workspace… It gains:

- Home floats under their session, marked as floats with their page; a tap pages Home and
  restores.
- A **Displays** group beside the sessions: one row per display window, with its X toplevels
  (EWMH names, `X11WindowList`) nested; a tap on a toplevel raises it on that display.
- A **Home** row in the footer, above the save row, that goes to Home page 1.
- The rename: Save as workspace… becomes Save sessions…, the Saved tab lists Saved sessions.
  `~/.termux/workspaces/` and the `launcherctl` verbs keep their names. Saved sessions record the
  float flag in phase two; until then a saved float loads as a tiled window.

## 9. Entering, leaving, resuming

| Event | Result |
|---|---|
| Terminal entry tapped | Workspace at the window last shown; a new window if none |
| Terminal entry long-pressed › New float; Home long-press › Terminal | a new Home float on the current page, full-size, focused, keyboard overlay up |
| Dock on a Home float | the float becomes a tiled window after the current one; the Workspace slides in |
| Linux app tapped | display window opened or raised (terminal window for Terminal=true) |
| Home press while a window is in front | Home page 1 |
| Home press while a Home page is in front | Home page 1; floats stay where they are; the keyboard and palette close |
| Home press from another app | the page that was in front when the launcher was left, Home page or window |
| Corner tab › Home, drawer › Home, palette › Go to Workspace | as named |
| Last row window closed | the last Home page slides in |
| Cold start, process killed in the background | Start in: Home (default) shows page 1; Workspace opens one fresh shell window. Saved sessions restore as they do today; nothing else rebuilds the row or the floats |

## 10. Settings and migration

- **Start in**: Home | Workspace. New, under the launcher's settings.
- **Palette gesture on Home**: beside the other gestures, default swipe up from the bottom edge.
- **Gone**: Usage mode (Terminal alone, with Home, with Display); the Display switch.
- **Migration, once**: Terminal alone → Start in = Workspace; the other two → Home. Display on →
  nothing (on demand now). The normal and minimal layouts become the Workspace layouts unchanged;
  the Home layout is seeded as in §3 with the keyboard put away. The Terminal entry is pinned to
  the dock. Per-place state (`PaneWallPage` names in saved instance state, place-scoped keys left
  from before ADR 0003) is dropped. No toggle back to the old wall.

## 11. Code that this touches, by name

`PaneWallController`, `PaneWallLayout`, `PaneWallPage`, `PaneWallPolicy` (the row and its policy;
`homePage()` returns TERMINAL today and the class comment calls Terminal the home screen);
`LauncherUseCaseMode`, `X11DisplaySwitch`, `SettingsActivity` and
`TermuxStylePreferencesFragment` (mode and switch); `PlaceLayoutStore` (the Home variant and its
keyboard element); `KeyboardOverlayPolicy`, `EdgeStackPolicy` (dock as overlay on Home, the ride);
`TerminalPaneController`, `TerminalWorkspace` (Home floats: page pin, orientation key, lift,
titlebar); `TerminalCommandPalette` (Home action set, Linux apps, floats); `LauncherWidgetRepository`
(snapshot around the reflow); `SessionsDrawerView` (floats, Displays group, Home row, rename);
`AppDrawerItem` and `LauncherAppEntry` (the Terminal entry and the Display entry);
`TermuxActivity.onNewIntent` (home press lands on Home page 1); `LinuxAppCatalog`,
`X11LinuxAppRunner` (unchanged, callers change).

## 12. Out of scope (phase two and later)

- The icon grid on Home pages: app icons, folders, drag and drop between pages. Home stays a widget
  canvas with a dock and a drawer in phase one.
- The status bar's **capsule** form: a third form beside compact and expanded, a thin material
  capsule at the top centre; a continuous swipe sizes the bar by where the finger lets go, quick
  swipes step one form, the bar folds to the capsule after a short idle, and the capsule is Minimal
  mode's default form. Agreed, everywhere on the wall; not phase one.
- Drag-to-reorder of windows, and Niri's consume/expel of panes into and out of columns.
- Per-toplevel X11 windows in the row (needs a rootless server).
- Saved sessions recording the float flag and page.
- Rebuilding the row or the floats after a background kill beyond what Saved sessions restore.
- A terminal or display window placed among the Home pages as a page.

## 13. Words

See the glossary. In short: **wall** (the row), **page** (one stop), **Home** and **Home page**,
**Workspace**, **window** (terminal or **display window**), **Home float**, **titlebar**,
**Terminal** (the entry), **Start in**, **Saved sessions**, **Linux app**, **always on** (the Home
keyboard). Retired: place, pane wall, usage mode, Display place, Create Pane, pill (the float's).

## 14. Docs to rewrite when it ships

`docs/en/Launcher_Usage.md`, `docs/en/Widgets.md` (keyboard on Home, always on), `docs/en/X11_Display.md`
(no Display place, no switch, the Display entry), `docs/en/Panes_Windows_And_Sessions.md` (the row,
Home floats, the titlebar, Displays group, Saved sessions), `docs/en/Command_Palette_And_Actions.md`
(the Home set), `docs/en/Home_Screen_And_Apps.md`, `docs/en/Launcher_Settings.md` (Start in, the
palette gesture; usage mode gone), `project-docs/reference/launcher/gestures-and-style.md` (Home's
plain swipe, the seam, the bar on Home), `project-docs/reference/launcher/pane-wall-x11-study.md`
(point at ADR 0008), `project-docs/reference/terminal/split-panes.md` (floats, titlebar).

## 15. Device checks owed

On the emulator first, pong when the developer asks: the seam both ways; the home press from a
window, from a Home page and from another app; last window closed; Start in = Workspace cold start;
a Linux app tap with no display up; a Home float born, moved, maximised, minimised to its chip and
restored from another page, docked into the row and floated back; the bar sliding in with the
first float and out with the last; the palette gesture with the keyboard overlay and its three
ways of closing; the dock riding up with the keyboard; always on shrinking the grid and the
snapshot restoring it; the Home layout in the Layout editor; migration from each of the three
usage modes.
