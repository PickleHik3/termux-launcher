# Termux Launcher

An Android launcher that wraps a terminal, a widgets page and an X11 display in one themable,
rearrangeable chrome. This glossary is the language the code, the docs and the developer use.
AGENTS.md carries the operational vocabulary (editions, pong, the seams); this file holds the
product model.

## Language

### Places and surfaces

**Place**:
One of the three full-screen pages the launcher swipes between: Home (widgets), Terminal, Display.
Layout and appearance are shared by all places (one layout per orientation). What differs per place
is state only: whether the keyboard is up, and minimal mode.
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
Portrait or landscape. The shared layout has one version per orientation; appearance is never
per orientation.

**Minimal mode**:
A place shown with only its pane: the status bar shrinks to a thin strip, the apps bar and keyboard
go away, and the pane is maximised in either orientation. Available on Display and Terminal, turned
on from the corner tab and remembered per place until turned off.
_Avoid_: full screen, focus mode, zen mode

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
The editor for how the surfaces look: glass, opacity, blur, grain, corners, palette. Entered
from the corner tab; exits straight back to the live place. There is one look, so what it edits
lands on every place; it can style one surface or all surfaces at once.
_Avoid_: surface editor (legacy umbrella name), look editor, style editor, full editor

**Layout editor**:
The editor for where the elements sit and how big they are: bars, dock, keyboard, widget
grid, hidden or shown, plus dock height, keyboard height and keyboard chin. The layout is shared,
so a change lands on every place; the miniature shows the place it was opened on. It shows one
orientation with a toggle to the other. Entered from the corner tab or
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

### Voice input

**Dictation**:
One stretch of speech-to-text, from the voice key or the Dictate key until it pauses (a second tap,
the pill's pause, the silence timeout). What is said collects in the panel as heard; nothing is typed
while the user speaks.
_Avoid_: voice typing (the Android recognizer's path), voice session

**Pill**:
The small bar at the top right of the place viewport while a dictation is up: waveform, state,
pause/resume and ×, as long as its contents and no longer. Same corner on every place until moved
by its handle. The waveform rests (a dim line) while not listening. The screen stays on for as long
as it is up (released after 3 minutes of untouched waiting text, and at once when it closes or the
app pauses).
_Avoid_: voice indicator, overlay

**Pill handle**:
The floating keyboard's grab handle, under the panel: dragged, it moves the pill and panel anywhere
in the place viewport, remembered per orientation beside the floating keyboard's place; a double tap
puts it back in the top right corner. Placed low, the panel grows upward.
_Avoid_: drag bar, grip (the floating keyboard's resize corner)

**Pause / resume**:
Pause stops listening: the phrases still transcribing arrive, then the cleanup runs on its own.
Resume listens again and carries on the same text; the next pause cleans all of it. The voice key
and the Dictate key do the same (start, pause, resume).
_Avoid_: stop (the × is not a stop)

**×**:
The pill's discard: it stops listening if need be, throws the text away and closes. A sideways swipe
of the card is the same. It never means stop.
_Avoid_: close (on its own), bin (retired)

**Panel**:
What the pill grows into, downward (upward when placed low): the dictation's text (seven lines, the
oldest scrolling off the top) over the action pill, one long rounded bar holding undo, Copy and ✓. It
is where the text waits until one of them is used. Text as heard is italic in the secondary colour;
cleaned text is upright in the primary one.
_Avoid_: transcript view, preview

**Mic sensitivity**:
How readily quiet speech opens a phrase: Normal (the default) or High. It sets how far the audio
Silero judges is lifted to match the room, and the voiced time a phrase needs. High catches soft
speech and hears nearby talk and TVs more.
_Avoid_: gain, VAD threshold, microphone volume

**Cleanup**:
The one pass a local chat model makes over the whole dictation once it pauses, at the level chosen in
Settings (Light or Polished; off by default). The panel marks what it changed, and the cleaned text
is what ✓ and Copy use; a refusal or an answer falls back to the text as heard. A dictated command
never goes to the model: command formatting handles it.
_Avoid_: polish (the setting's old name), rewrite, per-phrase cleanup

**Command formatting**:
The fixed, model-free rules that write a dictation starting with a command name as a command:
"L S dash La" becomes `ls -la`, "cd slash home" becomes `cd /home`. It runs whatever the length and
whether cleanup is on or off. The panel shows it like a cleanup, and undo takes it back.
_Avoid_: command cleanup, command rule (the model prompt's old rule)

**Undo**:
The panel's icon that puts the cleaned (or formatted) text back to what went into the cleanup: the
text as heard, or as carried on from. It turns into redo. It is only there while there is a cleanup
to take back.
_Avoid_: raw toggle, long-press (retired)

**Insert**:
✓: the dictation typed once where the keyboard would type on the place on screen, never with an
Enter. Where nothing takes typing, it copies instead.
_Avoid_: replace, swap (the retired in-place rewrite of the terminal line)

**Dictate key**:
`tool:voice.dictate` on the extra-keys row: starts or stops a dictation on any place, with the
keyboard up, down or not the launcher's.
