# Tiling desktop: terminals and Linux apps as one desktop (2026-11-27)

Decided with the developer on 2026-11-27 (grill-with-docs). Records:
[ADR 0037](../../docs/adr/0037-every-shell-is-a-window.md) (every shell is a window),
[ADR 0038](../../docs/adr/0038-one-control-bar.md) (one control bar),
[ADR 0039](../../docs/adr/0039-terl.md) (Terl), [ADR 0040](../../docs/adr/0040-looks-are-edited-where-they-are.md) (edit mode and Settings), [ADR 0041](../../docs/adr/0041-the-settings-store-is-the-truth.md) (the settings store is the truth; terl.conf applies once). Words (**window**, **Workspace**, **control bar**,
**navigation mode**, **border hold**, **Float**, **Overview**) are the glossary's; the notes marked
"Tiling desktop" there are this spec's. Mock-ups: OpenDesign project `tiling-windows-phone`
(`index.html` tab groups, `bar.html` control bar) and the final prototype (see Prototype).

Design target: a phone in portrait, held in one hand. Tablets and external screens come later.
Options were judged on what the user sees and can do; build effort was not a factor.

## Problem Statement

A user who wants a Linux desktop built around terminals, with GUI apps when needed (Hyprland plus
waybar, or Omarchy), gets two workflows on the launcher. Terminals live in sessions, windows and
panes; Linux apps are windows of another kind placed among them; the Assistant and the X11 Display
are pages of their own. Gestures mean different things on a terminal and on an app, there is no
automatic tiling across both, and moving around needs body swipes that Linux apps cannot give up.
Linux windows cannot be faded or rounded like terminals.

## Solution

### Windows and places

- A **window** is one shell, one Linux app, the X11 Display or the Assistant: one kind of tile.
- **Workspaces** stack vertically: Home at the top, then 1, 2, 3 …, and an empty one always waiting
  at the end. Sideways moves among a Workspace's windows; up and down moves between places. The
  home press goes to Home; sideways on Home flips Home pages.
- A Workspace shows its number and the icon of its main window (biggest or longest open); a long
  press on it renames it.
- Sessions retire. A saved Workspace keeps its windows, layout and working directories; Linux apps
  open again when it is loaded.
- Fresh install and upgrade from v1 alike: Home, then Workspace 1 holding one terminal. The launcher
  opens on Home. Under the Terminal launcher mode there is no Home; the stack starts at 1.
- After a launcher restart (update, crash) everything returns as it was: Workspaces, layouts,
  Floats, tabs; surviving shells reattach and Linux apps (which outlive the launcher) slot back in.
  After a phone reboot windows reopen as new shells in their old directories and Linux apps
  relaunch.

### Layouts

- **Scrolling** (default for a new Workspace): every window opens as a column after the focused
  one. Column widths: full or half in portrait, plus thirds in landscape. A drag on the gap between
  columns resizes freely; a double tap on the gap snaps to the nearest preset. A window can be
  stacked into the column beside it (top over bottom). Focusing a column scrolls only as far as
  needed to show it whole.
- **Dwindle** (chosen per Workspace from its menu): each new window splits the focused one along
  its longer side. When the split would leave a window narrower or shorter than usable (a terminal
  under about 40 columns, an app under its minimum size), the new window joins the focused window
  as a **tab**; a tabbed tile has a thin glass tab strip with each window's icon and short name.
- **Floats** belong to one Workspace or one Home page and sit above its tiles; **Pin** shows a
  Float on every Workspace.
- **Full screen** hides the control bar down to a thin glass handle above Android's gesture line;
  sliding up on it raises the bar over the window.

### The control bar

- At the bottom of every place, above Android's gesture area. Portrait: **two rows**. Top: where
  you are (the Workspace number and icon, a **mark** per window with the focused one lit, the
  reading slot). Each window shows its name in a very thin strip along its own top edge, set off from
  the content by a hairline, for display only: nothing on it answers a touch. Bottom: the tools for what is in front, then navigation mode and search.
  - Home: the pinned apps (the bar is Home's dock).
  - A terminal in front: keyboard, split, new terminal, window menu.
  - A Linux app in front: mouse mode (touchpad or direct touch), keyboard, window menu.
- While typing, and always under the Terminal launcher mode, it folds to **one row** (place,
  tools, navigation, search) and rides on the keyboard. In landscape it is a **rail** down one side
  (right by default), the same zones top to bottom.
- Gestures on it: slide along the marks to change window; slide up for the Workspace list (let go
  on one to go there); hold for Overview. A tap on a mark goes to its window; a held mark opens the
  window's menu (float or tile, full screen, move to Workspace, tab, stack, close; plus Minimise,
  Maximise, Scale where they apply). Dragging a mark sideways reorders, up into the Workspace list
  moves the window there.
- **Search** lists Terminal first, then Linux apps, then Android apps; typing filters all three.
  What it opens lands in the current Workspace after the focused window (Android apps still open
  full screen over the launcher). An app already open goes to its window; holding its entry offers
  New window here. The Super key opens the same search.
- Android's own status bar stays shown (clock, notifications, the shade). The launcher's expanded
  status bar retires; the clock lives in Home widgets, and important notifications get a Home
  widget of their own (separate work).
- The extra-keys row is off by default (the launcher keyboard has Ctrl, Alt, Esc, Tab and arrows);
  Settings can turn it back on above the keyboard, for people typing with the phone's own keyboard.

### Moving around

- A touch on a window always belongs to its content: a terminal selects and scrolls, an app gets
  its own touches. Nothing on a window's body changes the page.
- **Border hold**: holding still on any edge of a window (left, right, top, bottom, the gap between
  tiles included) steps you one window or Workspace that way, with a tick and a small arrow at the
  edge; holding on repeats after a beat. A drag that starts on a gap at once resizes.
- **Navigation mode** (the bar's four-arrow button): windows take no input; one finger slides over
  the whole stack like a map, sideways through columns and up and down through places. Letting go
  settles on the nearest window. A tap on a window focuses it and leaves the mode; the button and
  Back leave it too. A long press on a window picks it up: dropped on another window's left, right,
  top or bottom half it takes that side; in its centre it joins as a tab; in a gap it becomes its
  own column; held against the screen's edge the view keeps moving, across Workspaces too.
- **Overview** (hold the bar): one row each for Home and every Workspace, top to bottom, each
  window a small schematic card (layout, icon, name), anchored low within thumb reach. Drag a card
  to another row to move the window there, onto another card to make them tabs; tap a card to go.
  The Saved row lists saved Workspaces.
- **Back** closes whatever lies over the windows (a sheet, a menu, Overview, navigation mode, full
  screen); with nothing open it does nothing.

### Keys

- Super-based bindings replace today's Ctrl+Alt defaults: Super+Enter terminal, Super+Q close,
  Super+arrows focus, Super+Shift+arrows move the window, Super+1…9 go to Workspace,
  Super+Shift+1…9 send to Workspace, Super+V float, Super+F full screen, Super+Space search,
  Super+Tab Overview, Super+W tab group.
- The launcher keyboard gains **Super** as a north-east swipe on its Ctrl key, whose top-right corner
  shows Terl's Super glyph; a plain tap on Ctrl is still Ctrl. Holding the swipe slides out live hints
  of what each key does, as Ctrl+Alt's hints do today. The glyph joins the four-loop Command shape with
  the launcher's own mark (the notification icon's chevron and foot); the final form is picked from the
  prototype.

### Looks

- Every window kind wears the same glass card, corners (one radius; small tiles clamp it) and gap.
- Linux windows get their own **Opacity** in Appearance, 100% by default, and per app on the app's
  page beside Scale. Fading fades the whole window (text included). Where a Linux app is
  see-through, the window's glass shows behind it, as behind a terminal.
- Focused and unfocused windows each get three controls: rim (the accent rim, on by default for
  focused), dim (0 by default) and opacity (the window's own by default).
- One wallpaper behind the whole stack, drifting a little as you move up, down and sideways.

### Edit mode and Settings

Decided 2026-11-27 in a second grilling round ([ADR 0040](../../docs/adr/0040-looks-are-edited-where-they-are.md)).
Research behind it: `docs/research/terl-surfaces-2026-11-27.md` (what today's 49 surfaces become),
`docs/research/modern-settings-appearance-ux-2026-11-27.md` (current practice),
`docs/research/settings-and-appearance-inventory.md` (v1's settings, control by control).

**Edit mode** replaces the Appearance editor.

- On the live desktop at full size, no scaled frame. Every surface shows a faint outline; a tap
  raises its card low on the screen, in thumb reach; the stack still moves while editing.
- Doors: holding empty space on Home, the Edit button in Overview, a window menu's Edit look,
  search ("appearance", "edit"), Super+comma. Each opens on the card of the thing it came from.
- Every change applies at once and stays. A slim strip at the very bottom holds Undo, Revert (drops
  everything since edit mode opened) and Done, with a count of this session's changes. Done and
  Back leave; nothing asks.
- **History** (decided 2026-11-27, third round, from the review of #413): one slider drag is one
  Undo step, from where the finger landed to where it lifted. Revert and a Look pick are one step
  each, so Undo right after Revert brings the whole session back, and Undo after a Look pick
  restores the old Look and every own value it cleared. A setting written from outside (terl.conf,
  launcherctl, the Assistant) after the session last changed it is the outside's: Undo and Revert
  skip it, the count drops it, and the pill says how many changes were kept because they were set
  elsewhere. The history ends with the session.
- **A card whose surface is not on screen** (the keyboard is down, the bar is hidden in full
  screen) brings that surface up while the card is open and puts it back when the card closes, so a
  change is still judged on the real thing. Under Use Terl as Terminal the Home card opens on its
  "Home is off" line with Change.
- A row following Global shows a hollow ring; a surface's own value a filled dot, which offers
  Follow Global. A surface holding one carries the dot on its outline too.
- While Opacity or Blur is dragged, a legibility meter shows the contrast measured on screen; below
  readable it turns amber and offers Dim wallpaper. Nothing is blocked.

| Card | Opens from | Rows |
|---|---|---|
| Wallpaper & colours | the wallpaper | picture, dim, drift, theme (light, dark, black), accent |
| Global ("All glass") | the strip | Look filmstrip, Corners, Tint colour, icon pack, text size |
| Control bar | the bar | Opacity, Blur, Margin (0 makes it flush, square bottom corners), rail side in landscape, Reading (on, off), Tools per context (Home, terminal, Linux app), dragged to reorder |
| Keyboard | the keyboard | Opacity, Blur, Height, extra-keys row |
| Windows | any window | Window tab: Opacity, Blur, Gap, Rim colour (override of the accent), Unfocused dim. Text tab (terminals): font, size, colours, cursor, effects, for every terminal |
| Home | empty Home | the grid (icons, folders, widgets), page dots, A–Z edge on Home (off, left, right, bottom) |

- **Looks.** One Look for everything, picked from a filmstrip swiped full size on the live desktop:
  the shipped Looks, three made from the wallpaper, and **Custom** at the end, which opens Opacity
  and Blur for all glass. Custom remembers its values, so trying other Looks and swiping back
  restores them. Only the control bar, the keyboard and windows may hold their own Opacity and Blur;
  the sheets (search, drawer, Workspace list, Overview, menus, notices) and the Home widgets wear
  the Look. Picking any Look (shipped, wallpaper or Custom) puts every surface on it, asking first if
  any holds its own value. Corners are Global's alone.
- **The bar** is one floating glass card; no Docked or Floating choice. In landscape the rail, Floats
  and Overview keep clear of the camera cutout (Automatic, with Custom for a phone that reports it
  wrongly).
- **The keyboard** has one docked form; floating and split retire.

**Settings** holds behaviour only; anything that can be seen is changed in edit mode.

- Opens on its search field, then seven groups, each one page, no page deeper than two levels.
  Search reaches every row and every edit-mode control (a hit on a look opens edit mode on its card).
- A Changed filter lists every setting moved from its default, each with its reset.
- While Terl is used as a terminal, Home's rows and card are hidden; one line in their place says
  Home is off, with Change. Search still finds them, with that note.

| Group | Holds |
|---|---|
| Home | Use Terl as (Home screen, Terminal); Home pages; drawer (categories, sort) and app search; notification dots |
| Workspaces & windows | new-window layout (Scrolling, Dwindle), Opens as default; terminals: scrollback, bell, clipboard, Terminal core (Termux, libghostty) |
| Keys & keyboard | Leader key (Super, a north-east swipe on Ctrl, or the keyboard's Meta key, labelled Meta as keyboards label it), its bindings (tap an action, press the chord, clashes shown in place), launcher keyboard layouts (more layouts, and a Custom layout) and typing (no autocorrect), voice input, hardware keyboard |
| Linux apps | setup, display server (X11, Wayland), Opens on (the current Workspace, or a Workspace of its own), Desktops, per-app pages (Opens as, Opens on, Opacity, Scale), input, resolution, graphics |
| Assistant | v1's On-device AI pages as they are: models, runtime, local API, remote model, read aloud |
| Notifications | history (pinned notifications retire) |
| System | battery & performance, backup, permissions (Shizuku, root, Termux:API), Tips, diagnostics, about |

- **terl.conf** (`~/.config/terl/terl.conf`): one `id = value` per line, comments allowed, built from
  the settings registry. Saving applies at once; an unreadable line shows a notice and changes
  nothing. Every row and edit-mode control has Copy as config; launcherctl uses the same ids.
  Decided 2026-11-27, third round ([ADR 0041](../../docs/adr/0041-the-settings-store-is-the-truth.md)):
  - The settings store is the truth. A saved file is applied once; Terl remembers what it applied,
    so an unchanged file never replays at start-up over later edits made in the UI. A file changed
    while Terl was not running applies at the next start. Terl never writes into terl.conf by
    itself; Export and Copy as config bring it up to date.
  - A setting the file leaves out keeps its value. `<id> = follow` puts a surface's own value back
    on Global; `<id> = default` resets any setting. Export writes every exported setting, with
    `follow` for a surface on Global.
  - A save is one update: every line lands together, or none does. A failure while checking or
    while saving rolls the whole file back, and the notice names the line.
- **Upgrade from v1** (ADR 0037 starts the layout fresh): the look and the layout start fresh
  (Appearance, every surface's values, the dock, the status bar, panes, sessions). Behaviour
  settings that still have a row in Terl carry over (keyboard, terminal, Linux apps, the Assistant
  and its models, notification history, permissions, Home's icons and pinned apps), and so do
  `$HOME`, the prefix and tlstore items. Settings whose row retires are dropped.
- **First acceptance slice**, built end to end before the other cards: search "opacity" in Settings,
  open edit mode on the live Control bar card, change its Opacity, set it again from launcherctl,
  then check Follow Global, Undo (skips the outside write) and Back (closes the card, then leaves).
  Run it once with the bar hidden in full screen, so the card brings the bar up.

**Elsewhere**

- The drawer and its categories stay. It opens by swiping up on Home's empty space or from an
  optional Drawer item in the bar's Home row; on a Workspace, search with nothing typed offers All
  apps first.
  It is a full-height glass sheet over the current place: a Recent row, then every app in A–Z order
  with its own A–Z rail on the thumb's side; the category chips and the Search apps field sit at the
  bottom, just above the bar, in thumb reach.
- The A–Z index stays. On Home it may stand along the left, right or bottom edge (bare letters, no
  glass). In every place, Home and Workspaces alike, pressing the bar's search and sliding up raises an
  A–Z rail on the thumb's side: scrubbing a letter fans its apps beside the thumb, and letting go on one
  opens it; a plain tap still opens search (chosen from the prototype, 2026-11-27).
- Home pages keep icons, folders and widgets on one grid; the bar's top row shows a dot per page.
- **Readings** (CPU, GPU, RAM, battery, weather, media) sit in a slot at the end of the bar's top row
  in every place, beside Home's widgets: a tap cycles them, a hold opens a card with all of them and
  the media controls; in the one-row bar it shows an icon and a number. The Control bar card has a
  Reading row to turn it off (chosen from the prototype, 2026-11-27).
- Icons carry a notification dot (no count) on Home, in the bar, the drawer and search; holding an
  icon shows its notifications, with quick reply where the app offers one.
- Saved Workspaces are managed only in Overview (Save from a Workspace's menu; the Saved row loads,
  renames, deletes, marks Open at start).
- The border hold is always on, with no settings (an on/off switch only if testing shows a need).
- Retired: Minimal mode (full screen covers it), the page transition choice (one motion; Android's
  Remove animations makes it a quick fade), copying a look between surfaces.
- With three-button navigation the bar sits above Android's buttons.
- First run: no tour. Hint cards on the first Home teach a gesture where it is done and leave once
  it is; Settings > System > Tips brings them back.
- Terl's own messages are a glass pill dropping from the top for about two seconds, with Undo when
  the action can be undone; it never covers the bar.

### Per-app rules

- Each app's page gains **Opens as** (tiled, floating, full screen) and **Opens on** (current
  Workspace, or a fixed one).

### launcherctl

- Full control, as hyprctl and niri msg: list Workspaces and windows; focus, move, float, tab,
  stack, resize, full screen; set a Workspace's layout; open into a Workspace; and an event stream
  (window opened or closed, focus changed, Workspace changed) for scripts, Linux status bars and
  agents.

## What can be built from what exists

- Linux windows are already one `TextureView` each, translucent (`setOpaque(false)`) with the app's
  alpha kept for ARGB buffers (`LinuxWindowsController`, `wayland-host/src/android/pixels.rs`), so
  opacity, glass behind and corner clipping are view-side work.
- Terminal and Linux leaves already share one split tree (`TerminalPaneController.Leaf`,
  `isLinux()`), and a dwindle policy exists (`DwindleTilingPolicy`); both are starting points, not
  the model, since a window no longer holds a split.
- Window controls on a held mark exist (marks redesign, 381a3e811).
- Only the Workspace in front and its neighbours hold surfaces (`LiveNeighbourPolicy`, extended to
  two directions); navigation mode keeps the Workspaces above and below ready while it is on.

## Prototype

The final interactive prototype (control bar B, Super on Ctrl and its hints, the Workspace list,
navigation mode, Overview, edit mode, Settings, the drawer, and a landscape form with the rail) lives
outside the tree; the developer picks the Super glyph from it.

## Out of Scope

- Tablets, foldables and external screens (a later pass on the same model).
- The notifications widget (its own spec).
- Live pictures in Overview (all or nothing, 2026-11-27; not possible today).
- Android apps in the tiling: Android does not let a launcher tile other apps' activities.
- The repository move and rename themselves (ADR 0039 records the decision; the move is release
  work).

## Open

- The Super glyph's final form (from the prototype).
- The look of the Workspace list, navigation mode and Overview (from the prototype).
- Where terminal behaviour and the drawer's rows sit among the seven Settings groups (placed provisionally
  above; checked in the prototype).
- Gap and resize hit sizes on a phone, and the border hold's delay (today's 1.5 s is too long for a
  step-by-step navigation gesture; to be tuned on pong).
