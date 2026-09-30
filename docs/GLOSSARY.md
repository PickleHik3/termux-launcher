# Glossary

The language the code, the docs and the developer use. One file: the product model (places, surfaces,
editors, voice, local AI) and the words the project uses about itself (editions, releases, pong,
the seams). AGENTS.md carries the rules; this carries the names. Terms are alphabetical within
each section. `_Avoid_` lines list the words not to use for a term.

Related: [`CONTEXT.md`](../CONTEXT.md) (a pointer to this file), [`docs/adr/`](adr/) (decisions),
[`project-docs/README.md`](../project-docs/README.md) (developer specs and research).

## Language

### Places and surfaces

**A–Z index**:
The row or column of letters along an edge that jumps the app list to the apps beginning with a
letter: scrub across it and the matches show. One of the Layout editor's elements: on an edge, or
put away in the tray. See `docs/en/Launcher_Usage.md`.
_Avoid_: alphabet bar (the Layout editor's file names still say `alphabets`), fast scroller

**Base**:
The shared appearance values every surface inherits until a property is detached for one surface.

**Border drag**:
The one finger gesture that pages the wall, on every place and in every mode: a press held on the
current page's border — the frame line on any of its four sides, less the corner squares — for the
same hold a corner takes, then dragged sideways. A tap, a sideways swipe without the hold, a drag
that sets off before it and a second finger all stay the content's; a vertical swipe off the
bottom border is the keyboard swipe, and one off the top border is the status swipe. The border
is always drawn while the
border preference is on (the slab's rim on glass, the plain stroke otherwise), on the terminal,
the Widgets page and the Display page alike, so the gesture has a line to find. The hold sinks
the page away from the user until the finger lets go, in every mode unless motion is reduced;
under Fancier Glass the page held and the page arriving both tip like planks toward the finger,
as if it pressed its weight into them, and lie flat as the settle lands; the Display place does
too, on a still copy of its picture that stands in for the motion. The release's settle carries on at the finger's speed. The status bar does
not page; its own drag is the fold across it.
_Avoid_: edge swipe, status bar swipe, page swipe

**Chrome**:
Everything the launcher draws around a pane: the status bar, dock, keyboard, borders and corner
tabs.
_Avoid_: UI (too wide), decoration

**Display**:
The third place: a Linux desktop or any X11 app, shown by the embedded X server (`x11-server/`).
It exists only when the usage mode includes it. It is reached like any other place, by the border
drag or the screen glyph in the status bar. See `docs/en/X11_Display.md`.
_Avoid_: X11 page, Termux:X11 (the server is our fork of it, but the place is the Display)

**Dock**:
The band of chrome that carries the pinned-apps row, the A–Z index and the extra keys, along the
bottom by default. It is one Surface. It comes in two styles: **Docked** (flush and square at rest)
and **Floating** (a card already rounded at rest); the stored values are still the old Default and
Rounded. Its rows may stand under the keyboard instead of over it. Not the same as the Layout
editor's "Hidden tray", which holds elements that are put away.
In Docked the top and bottom chrome are two cards peeking in from the screen's edges: an edge that
touches the screen or the strip behind a system bar draws no rim or line, so the glass under the
status bar and the navigation pill reads as the card beside it; only the inner edge keeps one.
_Avoid_: taskbar, nav bar, hotseat

**Keyboard swipe**:
The one way to the keyboard every place and every mode shares: a swipe that sets off up or down
from the current page's bottom border — the frame line or just below it, less the corner squares
— before the border drag's hold. Up opens the keyboard, down closes it; opening turns a keyboard
that was switched off back on, as the keyboard key does. It is claimed as soon as it moves, so the
content under the line is told its touch is over; a sideways start and a hold stay as they were.
A docked keyboard follows the finger and the release finishes it past a third of the way or on a
flick, and takes it back otherwise; a floating keyboard, Android's, and one switched off answer
the release alone. A small **grabber** pill on the bottom border marks it.
The keyboard itself carries no swipe of its own.
_Avoid_: pull-up, keyboard gesture, hide swipe (the retired swipe on the keyboard's top edge)

**Managed wallpaper**:
A wallpaper set through the launcher's own picker, of which the launcher keeps its own copy.
Only a managed wallpaper can move with parallax; a wallpaper set anywhere else stays still.
_Avoid_: custom wallpaper, in-app wallpaper

**Minimal mode**:
The launcher shown with only each place's content, unless the user chose otherwise: by default the
status bar, the apps bar, the A–Z index, the extra keys and the keyboard go away, and the widgets,
the pane or the display are maximised in either orientation. One mode for every place, not a state
of one: turned on and off only from the corner tab's minimal button, which every place carries, and
remembered until turned off; paging never leaves it. It has a layout of its own, the **minimal
layout**, beside the normal one, in the same element model: the Layout editor opened while the mode
is on edits it, and it says "Minimal layout", so users choose which elements minimal keeps. It
starts as the normal layout with every element put away. The keyboard can still be raised while it
is on, by the keyboard swipe or a tap. Paging is the border drag, as everywhere.
_Avoid_: full screen, focus mode, zen mode, zen view

**Orientation**:
Portrait or landscape. The shared layout has one version per orientation (and per layout variant,
see Minimal mode); appearance is never per orientation.

**Pane**:
One terminal view inside a split. Panes have corner tabs but no appearance of their own.
Their border is the shared rim; only the focused pane of a split wears the active colour, and a pane
asking for the user (bell, blocked agent, progress error) glows in the attention colour until focused.

**Pane wall**:
The row of places the launcher slides between: Home (the widgets), Terminal, Display. In code it is
`PaneWallController` and `PaneWallLayout`, and each place is a `PaneWallPage`. The wall is why a
place has a border to drag: dragging it pages the wall. See ADR 0003 and
`project-docs/reference/launcher/pane-wall-x11-study.md`.
_Avoid_: pager, home screen (Home is one place on the wall), carousel

**Parallax**:
The wallpaper panning by a fraction of the distance while the places slide sideways, with the
glass on every surface staying aligned to the wallpaper behind it.
_Avoid_: wallpaper scroll, wall motion

**Pinned notification**:
A notification held as a card in the status bar's widget slot, beside the clock, because it matched
one of the user's rules. Settings calls the rules "Essential notifications"; in code a rule is an
`EssentialNotificationRule` (package and/or keywords, whether dismissing also clears the
notification, and whether it is on at all) and a card is a `PinnedNotification`. Messages from one
conversation fold into one card with a count. Tap opens it, a sideways swipe dismisses it with a
4 s undo, and a long press shows the whole message with Open, Dismiss and Mute this rule. See
`project-docs/active/pinned-notification/SPEC.md`.
_Avoid_: essential notification (that is the rule, not the card), heads-up, pin (alone)

**Place**:
One of the three full-screen pages the launcher swipes between: Home (widgets), Terminal, Display.
The usage mode decides which places exist: Terminal alone, Terminal with Home, or all three with
the Display; a place that is off is not built at all. Layout and appearance are shared by all
places (one layout per orientation and layout variant), and so is minimal mode. What differs per place is state only:
whether the keyboard is up.
_Avoid_: screen, tab. "Page" and "wall page" are the code's word for a place (`PaneWallPage`); in prose say place.

**Status swipe**:
The keyboard swipe's mirror on the current page's top border: a swipe that sets off down or up
from it before the border drag's hold. Down unfolds the status bar, up folds it: the bar's own
two forms, compact and open, driven by the same fold its drag across itself drives, following the
finger and landing by the keyboard swipe's rule. A grabber pill marks it. It is there only while
the status bar stands along the top and can unfold; a bar on another edge or put away, and minimal
mode, leave the top border to paging. A press in the phone's own strip at the top of the screen is
never taken, so the notification shade still pulls down.
_Avoid_: status bar swipe (the retired page swipe on the bar), pull-down

**Surface**:
One themable chrome region: Dock, Keyboard, Status, Canvas. Appearance properties (blur, opacity,
grain, corner radius, side gap) attach to a surface, not to a pane.
_Avoid_: slot, region

### Look and motion

**Fancier Glass**:
The one opt-in toggle in Settings › Look that turns on the extra glass: refraction at glass edges on
every glass surface, video wallpapers, the refraction controls in the Appearance editor and the
tilting page motions. Off by default, shown on Android 13 and later, and it needs a managed
wallpaper. Lazy mode, battery saver and reduced motion override it. See ADR 0005 and
`project-docs/active/fancier-glass/SPEC.md`.
_Avoid_: glass mode, refraction mode

**Lazy mode**:
The Battery setting that stops the launcher animating while nobody is watching: the clock swaps its
digits instead of folding them, a working window's rim holds lit, and the status readings are sampled
less often. It is meant to become the default. See `docs/en/Launcher_Settings.md`.
_Avoid_: battery saver (Android's own), low-power mode

**Material palette**:
The colours the launcher takes from the system's Material roles, from the wallpaper or from a colour
scheme: primary, containers, outline and the rest. Chrome, the in-app keyboard and terminal tools
follow it, and it is exported to `~/.termux/material-colors.sh` and `.properties` for scripts. A
keyboard swatch is either dynamic (follows the palette) or pinned (an explicit colour).
_Avoid_: theme colours, dynamic colours (the keyboard's word for an unpinned swatch)

**Plank**:
A glass surface treated as a plank on a centre pivot: it tips toward the finger and springs back.
The dock's press reaction (`DockPlankController`) and the pages tipping under the border drag
(`PlankTilt`) are both planks. The off-dock apps sheet is also called a plank in the Fancier Glass
spec.
_Avoid_: card (a card is what departs or arrives during a pane change)

**Shared frame**:
The one wallpaper picture, blurred once per radius into a frame wider than the screen (1.5 times),
that every glass surface samples at its live screen position on every draw. It is why nothing
re-blurs or cuts a bitmap while places slide, and why glass stays registered to the wallpaper during
parallax. Held at blur resolution, about a quarter of the screen. See ADR 0002, 0004 and 0005.
_Avoid_: blurred wallpaper, backdrop crop (the retired per-surface cut-outs)

**Slab**:
The rounded sheet of glass a pane, the Widgets page or the Display page is drawn on: its outline
(`PaneGlass.slabOutline`) sets the radius the glass and its rim are cut to. Anything that copies a
pane while it moves, such as the departure card, copies the slab and nothing around it.
_Avoid_: tile, card, panel (the Panel is the dictation text)

**Stand-in**:
A still copy of the Display's picture that a place stands in for its `SurfaceView` while the wall
moves (`wall/SurfaceStandIn`). A surface is composited outside the view hierarchy, so it cannot
tilt or take a layer; the stand-in is a plain view that can. The picture freezes for the few hundred
milliseconds of the motion.
_Avoid_: screenshot, freeze frame

### Editing

**Appearance editor**:
The editor for how the surfaces look: glass, opacity, blur, grain, corners, palette. Entered
from the corner tab; exits straight back to the live place. There is one look, so what it edits
lands on every place; it can style one surface or all surfaces at once.
_Avoid_: surface editor (legacy umbrella name), look editor, style editor, full editor

**Corner tab**:
The small control strip revealed by pressing a pane or page corner. It carries the Appearance and
Layout buttons on every place, alongside the place's own actions.
_Avoid_: corner menu, pane menu, controls view

**Element**:
One piece of the arrangement the Layout editor shows or hides: the status bar, the pinned apps,
the A–Z index, the extra keys — each on an edge, in an order, or put away in the tray, the
status bar included now that paging is the border drag — and the keyboard,
which has no edge and no order, only on or off (in the tray or out). Along the bottom the dock's rows (not the status
bar) may stand **under the keyboard** instead of over it; with the keyboard down they are simply
the bottom's outermost bands. The keyboard's on/off is the one switch the
palette's **Keyboard on/off** flips (`keyboard_turned_off`), shared by both orientations and every
place; the last write wins, whichever door it came through.
_Avoid_: bar (for the keyboard), widget, slot (the store's word for where an element stands)

**Layout editor**:
The editor for where the elements sit and how big they are: bars, dock, keyboard, widget grid,
hidden or shown. It has no rows of controls (spec `project-docs/active/appearance-layout-editor`
§3.5): a bar moves by its grip, anything is put away by dropping it in the **restore tray** and
brought back by its chip there, and a tap selects an element and shows its **handle** — the dock's
height, the keyboard's height and chin, Home's grid cells — with a readout in real units while it
is held. The selected keyboard shows its type chips (docked, floating, split). The layout is
shared, so a change lands on every place; the layout canvas shows the place it was opened on, one
orientation at a time with a glyph toggle to the other. Entered from the corner tab's Layout glyph,
the long-press menu, or the Layout row in Settings.
_Avoid_: arrange mode, surface editor, place editor

**Layout canvas**:
The scaled model of a place's layout that the user drags elements around on, in the Layout
editor; there is no second one. Code still calls it the miniature (`LayoutCanvasView`) until
it is redrawn from the layout element pack.
_Avoid_: miniature (in new prose), preview, thumbnail, overview

**Restore tray**:
The strip under the layout canvas: one chip, with a struck-through eye, per hidden element, which
brings the element back to the edge it was put away from; one short line when nothing is hidden;
"Drop here to hide" while something is lifted.
_Avoid_: Hidden box, shelf

### Tlstore

The store's design lives in [PickleHik3/tlstore](https://github.com/PickleHik3/tlstore); these
terms are kept here because the launcher's own code and docs still use them.

**dawn**:
A terminal program in tlstore's catalog, not part of the launcher. It draws headings with kitty text
sizing (OSC 66) and is a client of the launcher's embeddings endpoint, which is why several
On-device AI details cite the "dawn brief". See `docs/en/Programs_Inside_The_Terminal.md`.
_Avoid_: the launcher's assistant

**Facts strip**:
The dim line under the standfirst: state and version, licence, author, size, `starred`.
_Avoid_: metadata, facts table

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

**Item**:
One thing a person can choose to install from tlstore, by name (`fastfetch`, `fish-shell`).
_Avoid_: package, app, tool

**Part**:
Something an item brings along that cannot be installed on its own (`fisher`, `musl-loader`).
_Avoid_: dependency, component

**Setup**:
An item that is the launcher's own arrangement of files and programs (`fish-shell`), not someone
else's program. A setup has no upstream and is never starred, nor are the programs it brings.
_Avoid_: dotfiles, config, bundle (the catalog kind that happens to hold it)

**Standfirst**:
The one line we write about an item: what it is for, in plain words, under its name.
_Avoid_: tagline, summary (the catalog's `summary` column is the old one-line description)

**tlstore**:
The launcher's own package manager for the tools and configs it shows off but does not ship in the
APK. It is a separate repository, [PickleHik3/tlstore](https://github.com/PickleHik3/tlstore); the
launcher pins the release it ships (`app/tlstore.lock`) and installs `tlstore`, `tl` and `tls`,
which are the same command. See `docs/en/Tlstore.md`.
_Avoid_: the store (for the command), package manager (for the store's catalog)

**Upstream**:
The outside project an item's program comes from, named by its GitHub repository
(`fastfetch-cli/fastfetch`). Stars go to upstreams. An item keeps its upstream when its recipe
patches the program.
_Avoid_: origin, source (the catalog's `source` column is where the file is fetched from)

### Local AI

**Default assistant model**:
The model On-device AI answers with when a program does not name one, chosen in the launcher's AI settings.
_Avoid_: default model, current model, loaded model (a model can be the default without being
loaded)

**Launcherctl**:
The launcher's localhost HTTP server and its command-line tool. The server exposes the OpenAI- and
Ollama-compatible endpoint, model management, app launch, the pane routes a shell uses to drive
terminal panes of its own, notifications, progress, clipboard and the device commands. `launcherctl`
is the client for everything but AI, and `tai` is the client for the model routes. Both read
`~/.launcherctl/endpoint` and `~/.launcherctl/token`. See `docs/en/LauncherCtl_API.md`.
_Avoid_: the API server, the launcher daemon

**On-device AI**:
The launcher's on-device AI: the models it holds and the local endpoint programs in the terminal
talk to. Programs reach it the way they reach any OpenAI-compatible service. `tai` is the
command-line tool and the internal code prefix (`com.termux.ai`, `:tai_runtime`); "TAI" as a
product name is retired from prose but survives in the CLI, code identifiers, and a few in-app
strings that still say it.
_Avoid_: the AI backend, the internal AI, the model server, TAI (as a product name in prose)

**TAI**:
Short for the on-device AI runtime: the `tai` command, the `com.termux.ai` code prefix and the
`:tai_runtime` process that keeps native model work out of the app process. In prose the product is
On-device AI; "TAI" appears in code, the CLI and the few in-app strings that still say it.
_Avoid_: TAI (as a product name in prose)

### Voice input

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

**Dictate key**:
`tool:voice.dictate` on the extra-keys row: starts or stops a dictation on any place, with the
keyboard up, down or not the launcher's.

**Dictation**:
One stretch of speech-to-text, from the voice key or the Dictate key until it pauses (a second tap,
the pill's pause, the silence timeout). What is said collects in the panel as heard; nothing is typed
while the user speaks.
_Avoid_: voice typing (the Android recognizer's path), voice session

**Insert**:
✓: the dictation typed once where the keyboard would type on the place on screen, never with an
Enter. Where nothing takes typing, it copies instead.
_Avoid_: replace, swap (the retired in-place rewrite of the terminal line)

**Mic sensitivity**:
How readily quiet speech opens a phrase: Normal (the default) or High. It sets how far the audio
Silero judges is lifted to match the room, and the voiced time a phrase needs. High catches soft
speech and hears nearby talk and TVs more.
_Avoid_: gain, VAD threshold, microphone volume

**Panel**:
What the pill grows into, downward (upward when placed low): the dictation's text (seven lines, the
oldest scrolling off the top) over the action pill, one long rounded bar holding undo, Copy and ✓. It
is where the text waits until one of them is used. Text as heard is italic in the secondary colour;
cleaned text is upright in the primary one.
_Avoid_: transcript view, preview

**Pause / resume**:
Pause stops listening: the phrases still transcribing arrive, then the cleanup runs on its own.
Resume listens again and carries on the same text; the next pause cleans all of it. The voice key
and the Dictate key do the same (start, pause, resume).
_Avoid_: stop (the × is not a stop)

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

**Undo**:
The panel's icon that puts the cleaned (or formatted) text back to what went into the cleanup: the
text as heard, or as carried on from. It turns into redo. It is only there while there is a cleanup
to take back.
_Avoid_: raw toggle, long-press (retired)

**×**:
The pill's discard: it stops listening if need be, throws the text away and closes. A sideways swipe
of the card is the same. It never means stop.
_Avoid_: close (on its own), bin (retired)

### Project and process

The words for how the project is built, shipped and tested. AGENTS.md has the rules; these are the names.

**Companion apps**:
Forks of the Termux plugins (termux-api, termux-styling, termux-boot), one branch per edition and
released on their own cadence, not with a launcher cut. A plugin is only granted the launcher's
permissions when it shares that edition's `sharedUserId` and signature, so each edition needs its
own build; all are debug-signed with the shared `testkey_untrusted.jks`, which is why an F-Droid
plugin never pairs. The Nix edition's are TLNix API, Styling and Boot.
_Avoid_: plugins, add-ons (the README's word for the same apps)

**Edition**:
One shipped applicationId, each on its own release branch: `com.termux` (the Termux edition, on
`main`), `com.termux.launcher.nix` (the Nix edition, on `nix-edition`) and `io.vaj.tl` (the VAJ
edition, on `io-vaj-package`). Features live on `dev` and reach an edition only by merging. An
edition owns its identity (applicationId, package name, manifest placeholders, ABI and bootstrap
rules) and nothing else. Every edition has its own package and so its own prefix,
`/data/data/<pkg>/files/usr`; a literal `com.termux` in code is a bug on the other two.
_Avoid_: flavour, variant, build type

**Hotfix**:
A release that ships fixes only and bumps the patch number: 1.0.1 after 1.0.0. A feature is never a
hotfix; it waits for the next minor or patch release under its own number.
_Avoid_: patch build, point release

**Integ branch**:
A throwaway branch named `integ/<topic-or-device-date>` that combines `dev` with work that is not
on it yet, so that one build can carry it to the developer's phone. It is what is installed on pong
when the note says "integ 8f3d7ed0 = dev plus the dictation marks". It is never released, and
nothing is merged from it.
_Avoid_: staging, release candidate

**Lane**:
The privileged lane: it runs allowlisted tlstore tools as the shell uid (2000) through Shizuku.
`PrivilegedLaneService` runs as shell, `PrivilegedLaneServer` listens on an abstract socket and
accepts only the app's own uid, and the client is `tl-priv` in the tlstore repository. The wire
protocol between them is `tlpriv1` (`LaneRequest`), a contract changed on both sides in step. The
allowlist guards against accidents, not against code already running as that uid. See AGENTS.md.
_Avoid_: root mode, shell mode

**Nightly**:
A build of `dev` between releases, with `versionName` `X.Y.Z+dev.<sha>`: the release it follows,
then the commit it was built from. Build metadata after `+` does not change version precedence, so
a nightly sorts equal to the release before it.
_Avoid_: dev build, snapshot

**Pong**:
The developer's own test phone, a Nothing Phone (2) A065 running Android 16, and the device of
record for measurements and device checks. It is also the developer's daily driver, often in use
while an agent works: ask before installing, force-stopping or sending input to it. Builds for it
usually come from an integ branch.
_Avoid_: the device (when a note means a phone the agent owns), the emulator

**Release**:
A published version, written as plain `X.Y.Z` and shared by every edition: the three editions ship
the same number in the same pass. Each release branch owns its `versionName`; `dev`'s is not
authoritative. Release notes are the only changelog (`project-docs/release-notes.md`).
_Avoid_: build (for a published version), edition version

**Seams**:
The deep modules and their host interfaces that were extracted out of `TermuxActivity`:
`TerminalHost`, `SurfaceEditorController.Host`, `ChromeRenderer` and friends. Work on a controller
through its seam, not through the activity.
_Avoid_: managers, helpers

**Tag**:
The git tag of a release, one per edition: `vX.Y.Z` for the Termux edition, `nix-vX.Y.Z` for the
Nix edition and `vaj-vX.Y.Z` for the VAJ edition. The `versionName` of the release commit is the
plain `X.Y.Z`. The companion apps use their own tags on their own repositories.
_Avoid_: edition suffix, version name (the tag is what the version name is prefixed to)

**VAJ edition**:
`io.vaj.tl`, on the `io-vaj-package` branch: the demo edition and the least recommended one to
install. Its packages come from the developer's own apt repository, updated sometimes, with no
promises. It gets every release like the others, but nothing should describe it as maintained,
revived or production-ready.
_Avoid_: the maintained edition, VAJ (alone, for the migration path to Nix)

**You and the developer**:
**You** is the agent reading AGENTS.md and changing the launcher. **The developer** or **the user**
is the maintainer you are talking to, who also uses the launcher as their daily driver and is often
testing your change on their own phone while you work. **The device** is that phone, pong.
_Avoid_: the owner, the client
