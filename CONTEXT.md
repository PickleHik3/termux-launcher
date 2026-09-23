# Termux Launcher

An Android launcher that wraps a terminal, a widgets page and an X11 display in one themable,
rearrangeable chrome. This glossary is the language the code, the docs and the developer use.
AGENTS.md carries the operational vocabulary (editions, pong, the seams); this file holds the
product model.

## Language

### Places and surfaces

**Place**:
One of the three full-screen pages the launcher swipes between: Home (widgets), Terminal, Display.
Layout is stored per place per orientation; appearance overrides are stored per place.
_Avoid_: screen, page, wall page, tab

**Surface**:
One themable chrome region: Dock, Keyboard, Status, Canvas. Appearance properties (blur, opacity,
grain, corner radius, side gap) attach to a surface, not to a pane.
_Avoid_: slot, region

**Base**:
The shared appearance values every surface inherits until a property is detached for one surface.

**Pane**:
One terminal view inside a split. Panes have corner tabs but no appearance of their own.

**Orientation**:
Portrait or landscape. Each place keeps a separate layout per orientation; appearance is never
per orientation.

### Editing

**Appearance editor**:
The editor for how a place's surfaces look: glass, opacity, blur, grain, corners, palette. Entered
from the corner tab; exits straight back to the live place. Edits the current place's surfaces
and can style all surfaces at once.
_Avoid_: surface editor (legacy umbrella name), look editor, style editor, full editor

**Layout editor**:
The editor for where a place's elements sit and how big they are: bars, dock, keyboard, widget
grid, hidden or shown, plus dock height, keyboard height and keyboard chin. Its canvas is the
miniature; it shows one orientation with a toggle to the other. Entered from the corner tab, the
long-press menu or Settings → Layout.
_Avoid_: arrange mode, surface editor, place editor

**Miniature**:
The scaled model of a place's layout that the user drags elements around on. The same miniature
is the Layout editor's canvas everywhere; there is no second one.
_Avoid_: preview, thumbnail, overview

**Corner tab**:
The small control strip revealed by pressing a pane or page corner. It carries the Appearance and
Layout buttons on every place, alongside the place's own actions.
_Avoid_: corner menu, pane menu, controls view

**Settings → Layout**:
The Settings entry that opens the Layout editor for a chosen place. A door, not an editor of its
own.

### Tlstore

**Item**:
One thing a person can choose to install from tlstore, by name (`fastfetch`, `fish-shell`).
_Avoid_: package, app, tool

**Part**:
Something an item brings along that cannot be installed on its own (`fisher`, `musl-loader`).
_Avoid_: dependency, component

**Upstream**:
The outside project an item's program comes from, named by its GitHub repository
(`fastfetch-cli/fastfetch`). Stars go to upstreams.
_Avoid_: origin, source (the catalog's `source` column is where the file is fetched from)

**Setup**:
An item that is the launcher's own arrangement of files and programs (`fish-shell`), not someone
else's program. A setup has no upstream and is never starred, nor are the programs it brings.
_Avoid_: dotfiles, config, bundle (the catalog kind that happens to hold it)

**Featured item**:
The one item the home screen shows large, chosen by hand in the catalog.
_Avoid_: hero, spotlight

**Web inset**:
A live web page the launcher sets into a band of terminal rows for a program, following the
terminal's rows, font and colours. Only tlstore may open one.
_Avoid_: web pane, web window, web view (the Android engine underneath)
