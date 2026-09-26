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

**Managed wallpaper**:
A wallpaper set through the launcher's own picker, of which the launcher keeps its own copy.
Only a managed wallpaper can move with parallax; a wallpaper set anywhere else stays still.
_Avoid_: custom wallpaper, in-app wallpaper

**Parallax**:
The wallpaper panning by a fraction of the distance while the places slide sideways, with the
glass on every surface staying aligned to the wallpaper behind it.
_Avoid_: wallpaper scroll, wall motion

### Editing

**Appearance editor**:
The editor for how a place's surfaces look: glass, opacity, blur, grain, corners, palette. Entered
from the corner tab; exits straight back to the live place. Edits the current place's surfaces
and can style all surfaces at once.
_Avoid_: surface editor (legacy umbrella name), look editor, style editor, full editor

**Layout editor**:
The editor for where a place's elements sit and how big they are: bars, dock, keyboard, widget
grid, hidden or shown, plus dock height, keyboard height and keyboard chin. Its canvas is the
miniature; it shows one orientation with a toggle to the other. Entered from the corner tab or
the long-press menu; Settings has no door to it.
_Avoid_: arrange mode, surface editor, place editor

**Miniature**:
The scaled model of a place's layout that the user drags elements around on. The same miniature
is the Layout editor's canvas everywhere; there is no second one.
_Avoid_: preview, thumbnail, overview

**Corner tab**:
The small control strip revealed by pressing a pane or page corner. It carries the Appearance and
Layout buttons on every place, alongside the place's own actions.
_Avoid_: corner menu, pane menu, controls view

### Tlstore

The store's design lives in [PickleHik3/tlstore](https://github.com/PickleHik3/tlstore); these
terms are kept here because the launcher's own code and docs still use them.

**Item**:
One thing a person can choose to install from tlstore, by name (`fastfetch`, `fish-shell`).
_Avoid_: package, app, tool

**Part**:
Something an item brings along that cannot be installed on its own (`fisher`, `musl-loader`).
_Avoid_: dependency, component

**Upstream**:
The outside project an item's program comes from, named by its GitHub repository
(`fastfetch-cli/fastfetch`). Stars go to upstreams. An item keeps its upstream when its recipe
patches the program.
_Avoid_: origin, source (the catalog's `source` column is where the file is fetched from)

**Setup**:
An item that is the launcher's own arrangement of files and programs (`fish-shell`), not someone
else's program. A setup has no upstream and is never starred, nor are the programs it brings.
_Avoid_: dotfiles, config, bundle (the catalog kind that happens to hold it)

**Featured item**:
The one item, chosen by hand in the catalog, that the Front page starts on and marks `new` until
it is installed.
_Avoid_: hero, spotlight

**Front**:
The store's first screen: the header of the item under the cursor over the list of every item.
_Avoid_: home, apps page, cover

**Header**:
The top of every store screen, at the same rows on each: the item's picture, its name in the
script face, its standfirst and its facts strip. On Front it follows the cursor; opening an item
keeps it still.
_Avoid_: hero, masthead (the single top row with the mark and the repo link)

**Standfirst**:
The one line we write about an item: what it is for, in plain words, under its name.
_Avoid_: tagline, summary (the catalog's `summary` column is the old one-line description)

**Facts strip**:
The dim line under the standfirst: state and version, licence, author, size, `starred`.
_Avoid_: metadata, facts table


### Local AI

**TAI**:
The launcher's on-device AI: the models it holds and the local endpoint programs in the terminal
talk to. Programs reach it the way they reach any OpenAI-compatible service.
_Avoid_: the AI backend, the internal AI, the model server

**Default assistant model**:
The model TAI answers with when a program does not name one, chosen in the launcher's AI settings.
_Avoid_: default model, current model, loaded model (a model can be the default without being
loaded)
