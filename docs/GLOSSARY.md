# Glossary

The language the code, the docs and the developer use. One file: the product model (the wall, chrome,
editors, widgets, voice, local AI) and the words the project uses about itself (editions, releases, pong,
the seams). AGENTS.md carries the rules; this carries the names. Terms are alphabetical within
each section. `_Avoid_` lines list the words not to use for a term.

Related: [`CONTEXT.md`](../CONTEXT.md) (a pointer to this file), [`docs/adr/`](adr/) (decisions),
[`project-docs/README.md`](../project-docs/README.md) (developer specs and research).

## Language

### The wall and its chrome

The wall rework (ADR 0008, agreed 2026-10-17, spec `project-docs/active/wall-rework-2026-10-17.md`)
is built on `feat/wall-rework-phase2`, and the entries here are its words. Some code names still
say place, Widgets and Terminal (`PaneWallPage`); the retired words are kept under _Avoid_ so the
old prose can be read.

**A–Z index**:
The row or column of letters along an edge that jumps the app list to the apps beginning with a
letter: scrub across it and the matches show. One of the Layout editor's elements: on an edge, or
put away in the tray. See `docs/en/Launcher_Usage.md`.
_Avoid_: alphabet bar (the Layout editor's file names still say `alphabets`), fast scroller

**Body swipe**:
A clearly sideways swipe that starts on a terminal's text and moves the place the terminal stands
in, exactly as the **border drag** does: on a terminal **window** it pages the wall's row (between
windows, the display window among them, and from the first window back to the last **Home page**);
on a **Home float** it pages Home. Clearly sideways means at least 1.5 times as far across as up or
down at the touch slop; anything steeper stays the terminal's scroll. The terminal keeps the swipe
while it is selecting text, a program is tracking the mouse or asked for touch reports, or mouse
mode is on; taps, the long press, vertical scrolls and pinches are always the terminal's. Any pane
of a split pages the row, never between panes. Not on the display window's picture. On by default:
Settings › Terminal › Windows & panes › Swipe on the terminal to switch windows (the Home float's
is always on). One rule, `TerminalBodySwipe`.
_Avoid_: terminal swipe, text swipe, rule B

**Border drag**:
The one-finger gesture that pages the wall from a **window**, in every mode. It starts on the
current window's border: the frame line on any of its four sides and the air between it and the next
thing out, less the corner squares. Anywhere on it outside the window's text (the air on all four
sides, and the side borders' 2 dp of clearance inside the frame) it is a plain sideways swipe that
pages at once, past the touch slop, with no hold. A hold there still starts it too. Where the border
reaches further into the window (a window almost flush with the wall) it is a hold and then a
sideways drag. The bottom border reaches a little further, a fingertip tall: a clearly sideways
swipe from the window's last row or the dock's top air pages too. A drag that starts in the
terminal's text is the terminal's, unless it is a **body swipe**, which pages the same way. The edge lights in
the accent colour inside the frame where a finger can page. A **Home page** has no border: it pages
by a plain swipe anywhere on it, as any launcher does. Reach, hold, glow and tilt are
specified in `project-docs/reference/launcher/gestures-and-style.md`.
_Avoid_: edge swipe, status bar swipe, page swipe

**Chrome**:
Everything the launcher draws around a page: the status bar, dock, keyboard, borders and corner
tabs. The **Workspace** wears all of it; a **Home page** wears the same status bar, starting below
it as a window's page does, and the same dock (without the extra keys) and the keyboard, as
overlays that take no room from the grid. The dock and
status bar are one each across both halves and stay put while the wall moves (decided 2026-10-21,
replacing the 2026-10-19 Home without a launcher status bar).
_Avoid_: UI (too wide), decoration

**Crossing**:
One slide of the wall between Home and the Workspace, from the first frame the wall moves to the
frame it lands. During a crossing the dock morphs from the leaving half's **Dress** to the arriving
one in step with the wall's offset (icon size, row width, bands, Search field and Drawer tile), so
nothing changes at the landing. The keyboard travels with the half that has it open, and a reversal
or a chip tapped mid-slide retargets from where the wall is. A crossing is over only when the wall
lands.
_Avoid_: travel (the code's older word for the same motion), transition, page swipe

**Display**, **display window**:
One X display, shown whole by the embedded X server (`x11-server/`) as a **window** in the
**Workspace**: a Linux desktop or the X11 apps on it, managed inside by the display's own window
manager. It appears when something asks for it (a **Linux app** tapped in the drawer, the Display
entry under Linux Apps), joining the end of the current session's row, and leaves when the display
stops. It is an ordinary window of the row (2026-10-19): it has a chip among the windows' chips,
can be dragged to another place in the row, and its chip's × closes it, which stops the display.
There is one at most, no Display page, and no switch or mode for it. Settings has a root entry
named **Linux apps** (2026-10-24), never Display: set-up, drawer visibility and the window's input,
resolution, start-up and fixes.
See `docs/en/X11_Display.md`.
_Avoid_: Display place, Display page, the third place, X11 page, Termux:X11 (the server is our fork
of it, but the window is the display)

**Dock**:
The band of chrome that carries the pinned-apps row, the A–Z index and the extra keys, along the
bottom by default. There is one dock for both halves of the wall, dressed by one **Dock design**;
only the Workspace's carries the extra keys. Its shape follows the **Style**. Its rows may stand under
the keyboard instead of over it. Not the same as the Layout editor's restore tray, which holds elements that are put away.
_Avoid_: taskbar, nav bar, hotseat

**Dock geometry**:
The pure module that answers every measurement of the dock: one snapshot of both halves in, one DockLayout out, down to the final per-slot icon. A slide is an input to it, never a path of its own (ADR 0011).
_Avoid_: dock sizing, dock policy (the internal arithmetic only), per-caller measuring, travel icon size

**Dock rows**:
The one adapter that writes the dock's accessory views (the pinned-apps row, the A–Z index, the extra keys and the bands under the keyboard) from the applied DockLayout, and announces which Elements are on screen. Not the rows themselves.
_Avoid_: dock controller, row visibility code

**Dock design**:
Which of the dock's variants dresses it, held by the layout (so on both halves unless Home holds a
**Home layout**): #1 Tray (the default), #2 Beads (each pinned app its own round glass bead floating
on the background with air between, no shared card, in both Styles) or #3 Strip (a big clock and a
tile per terminal over the apps; the clock slides out at 3+ terminals). The extra keys are not part
of any design: the Workspace shows them under every design and Style. Separate from the **Style**, which shapes all the chrome, and from the **Search field** and
the **Drawer tile**, which any design can carry.
_Avoid_: dock style (the old name, it collides with **Style**), dock form, dock theme, Glass tray,
Lens beads, Wall strip, Taskbar (old names; Taskbar became the Drawer tile, Prompt line the Search field)

**Stack layout**:
The pure module that answers the whole bottom stack from one snapshot: heights, margins, overlap, reservation and the DockLayout from **Dock geometry**. Travel asks it for a half's rest.
_Avoid_: bottom-stack assembly, stack caches, inset math

**Dress**:
Everything a page wears: the layout it follows and the elements it leaves out, the **Dock design**,
the **Search field** and **Drawer tile**, the **Style**, Corners and Margin. The keyboard's shape
comes from the layout the dress names; the dress itself does not carry the numbers. Home's dress equals a window's, less the extra keys, unless Home holds a **Home layout**.
Whether the keyboard is open is not dress; each half remembers that.
_Avoid_: outfit, chrome plan, place chrome

**Dock item**:
A launcher action standing in the dock's apps row beside the pinned apps, added, removed and
reordered like a pin (decided 2026-10-23): the **Drawer tile**, New terminal (a new window),
New float terminal (a **Home float** on the page you are on) and Command palette. One list per
layout, so a **Home layout** may hold its own. Each has its own glyph; the drawer's is the universal one.
_Avoid_: launcher shortcut, system tile, anchored tile

**Drawer tile**:
The **Dock item** that opens the app drawer, with any **Dock design**. Until 2026-10-23 the
layout turned it on and anchored it at the start, middle or end of the apps row; now it stands
where the dock items put it. Once the Taskbar design's (until 2026-10-21).
_Avoid_: Home tile (it does not go Home), Taskbar

**App menu**:
The card a long press on an app icon opens, on the dock, on Home or in the drawer: grouped rows
with a small arrow to the icon, in the system palette's shades (decided 2026-10-23). Rows, in
order: notifications when badged, the app's shortcuts, Widgets, App info, Add to home, Pin or
Unpin, Change icon (with Reset icon once one is set), Uninstall. Home's own long press is
a sheet, not a menu: it has more to show.
_Avoid_: context popup, anchored menu (the class), popup, context menu

**Grabber**:
The small pill on a page's bottom or top border that marks the **keyboard swipe** or the **status
swipe**. A swipe starting on it, or one row into the page beside it, is taken. A tap on it, a long
press or a sideways drag stays with the page. When a border drag's glow hands over, the grabber lights.
_Avoid_: handle (the Layout editor's size control), pull tab

**Home**:
The launcher half of the wall: the **Home pages**, left of the **Workspace**. It is always there,
and it is where the home press lands. Its chrome is the dock with the pinned-apps row and the A–Z
index, the launcher status bar it shares with the Workspace, and the keyboard; it has no borders.
_Avoid_: home screen (as a name for the whole launcher), the widgets place, Widgets page

**Home page**:
One page of **Home**: a grid of widgets, paged by a plain swipe anywhere on it. Pages 2…n lie to the
right of page 1, and the first window lies to the right of the last page. A long press on an empty
cell offers Add widget, Wallpaper and Terminal (a **Home float** here). A Home page's floats are
pinned to it.
_Avoid_: place, widgets page, desktop

**Home float**:
A **window** of the current session drawn floating over one Home page instead of in the Workspace's
row: born full-size, moved only by its **titlebar** (a **body swipe** on its terminal pages Home as
a swipe beside it does), resized at its bottom-right corner, minimised into
its **window chip** in the status bar and docked into the row by the titlebar's Dock. It is what
Terminal from Home opens. The bar's chips, the drawer and the shortcuts see it like any window.
_Avoid_: floating terminal (in prose), scratchpad (that is the toggled one), popup

**Float chip**:
Retired 2026-10-21: a minimised **Home float** is a **window chip** in the status bar on both
halves, and Minimise flies the float into that chip. There is no row of chips above the dock.
_Avoid_: float chip (for the bar's chip, say window chip), Home pills, minimised pill

**Keyboard swipe**:
The one way to the keyboard in the **Workspace**: an up or down swipe that starts on the window's
bottom border, or at its **grabber**, before the border drag's hold takes. Up opens the keyboard
(turning a switched-off keyboard back on), down closes it, and the Workspace remembers which. On a
**Home page** the same swipe is the palette gesture (configurable): it opens the command palette
and the keyboard together, and both close on Esc, on a tap outside, or when the action is done.
Home has no remembered keyboard state; the keyboard stays up on Home only when **always on**. The
keyboard itself has no swipe of its own. Full behaviour in
`project-docs/reference/launcher/gestures-and-style.md`.
_Avoid_: pull-up, keyboard gesture, hide swipe (the retired swipe on the keyboard's top edge)

**Keyboard mode** (the **display window**):
Whether an open keyboard, or the touchpad that stands in its place, floats over the **display
window** (**Overlay**) or takes its room so the display shrinks to what is left (**Resize**). One
value per orientation, whatever brought the keyboard up. Only the display window has one; a
terminal's text always makes room.
_Avoid_: pan, adjust resize, keyboard covers display

**Always on** (the Home keyboard):
The Home layout's keyboard element shown rather than put away: the keyboard stays up on every Home
page and the widget grid gives it the rows, with the earlier arrangement kept so that turning it
off puts things back if nothing was edited meanwhile. With it on, a typed key opens the command
palette with that key, after the bound keys have had their turn.
_Avoid_: persistent keyboard, keyboard lock

**Commit** (a key):
The moment a key press is sent: on release, never on finger-down, so a swipe legend, a long press or
tap correction can still decide which key it was.
_Avoid_: send, key-down

**Echo**:
What the shell writes back for a committed key, drawn in the terminal; the typed character appearing
is the echo, not the key press.
_Avoid_: local echo (the app never draws a character before the shell sends it)

**Layer**, **open layer**:
Launcher UI that opens over a page and closes again: the app drawer, the command palette, a sheet,
help, the widget editor. While a layer is open it takes Back and the keys first, over the
**display window** as over a terminal window; what it does not take reaches the page below.
_Avoid_: overlay (a **keyboard mode** value), popup, modal

**Linux app**:
An app installed in the prefix or in a `proot-distro` container, read from its desktop file and
listed in the drawer under Linux Apps beside the Android apps. A tap runs it on the **display**,
opening a display window if none is up, or opens a new terminal **window** for one marked
`Terminal=true`. A whole desktop session is a Linux app that takes the display for as long as it
runs. The **Terminal** entry and the Display entry are listed with them.
_Avoid_: X11 app (some run in a terminal), desktop entry (the file, not the app)

**Managed wallpaper**:
A wallpaper set through the launcher's own picker, of which the launcher keeps its own copy.
Only a managed wallpaper can move with parallax; a wallpaper set anywhere else stays still.
_Avoid_: custom wallpaper, in-app wallpaper

**Minimal mode**:
A second saved **Workspace** layout, and nothing more. It starts with only each window's content, so
by default the bars, the A–Z index, the extra keys and the keyboard go away, and it changes no
behaviour. It is one mode for every window, turned on and off only from the corner tab's minimal
button or the command palette's Minimal action, and stays on until turned off. Home ignores it:
Home wears the normal shared layout (or its **Home layout**) whatever minimal says (decided
2026-10-21, after a day of Minimal on both halves left Home with no way out). The Layout editor opened while it
is on edits the **minimal layout**. Full behaviour in
`project-docs/reference/launcher/gestures-and-style.md`.
_Avoid_: full screen, focus mode, zen mode, zen view

**Orientation**:
Portrait or landscape. The shared layout has one version per orientation (and per layout variant,
see Minimal mode); appearance is never per orientation.

**Page**:
One full-screen stop on the **wall**: a **Home page** or a **window**. Appearance is one look for
every page. One layout dresses both halves, minimal only for windows; a Home page wears its own
only when Home holds a **Home layout**.
_Avoid_: place (the retired word for Home, Terminal and Display as three pages), screen, tab

**Pane**:
One terminal view inside a split. Panes have corner tabs but no appearance of their own. A split is resized by dragging the divider, the gap between its panes, and a corner hold only shows the pane's buttons.
Their border is the shared rim; only the focused pane of a split wears the active colour, and a pane
asking for the user (bell, blocked agent, progress error) glows in the attention colour until focused.
Under the Floating **Style** each pane of a split is its own card with Margin's air between; under
Docked each pane is its own rounded insert, with Margin's gutter of frame glass between them.

**Parallax**:
The wallpaper panning by a fraction of the distance while the pages slide sideways, with the
glass on every surface staying aligned to the wallpaper behind it.
_Avoid_: wallpaper scroll, wall motion

**Pinned notification**:
A notification held as a card in the status bar's widget slot, beside the clock, because it matched
one of the user's rules. Settings calls the rules "Essential notifications"; in code a rule is an
`EssentialNotificationRule` (package and/or keywords, whether dismissing also clears the
notification, and whether it is on at all) and a card is a `PinnedNotification`. Messages from one
conversation fold into one card with a count. Tap opens it, a sideways swipe dismisses it with a
4 s undo, and a long press shows the whole message with Open, Dismiss and Mute this rule. See
`project-docs/reference/launcher/pinned-notification-SPEC.md`.
_Avoid_: essential notification (that is the rule, not the card), heads-up, pin (alone)

**Saved sessions**:
A named set of sessions, windows, splits and working directories kept under `~/.termux/workspaces/`
and loaded back as new shells. The drawer's Saved tab lists them; Save sessions… adds one.
_Avoid_: workspace, saved workspace, durable workspace (the files and `launcherctl` keep the old
name; prose and the drawer do not)

**Session**, **window**:
The terminal's own layers: a session holds windows, and a window holds a tree of **panes**. A window
is also the unit the **Workspace** pages between: a terminal window (its split tree) or a **display
window**. New windows (Ctrl+Alt+C, a Terminal=true Linux app, Terminal from Home) land right after
the current one. Each pane runs its own shell, which is a `TerminalSession` in code; that is the
code's word for a pane's shell, not for a session. See `project-docs/reference/terminal/split-panes.md`.
_Avoid_: tab (for a window), terminal (for a pane), column (Niri's word; in prose say window)

**Start in**:
The one setting that says where a cold start lands: Home (the default) or the Workspace, which
opens one fresh shell window. It replaces the usage mode's Terminal alone.
_Avoid_: default place, start page, usage mode

**Search field**:
The field under the dock's apps row that opens the command palette, there when the layout turns it
on, with any **Dock design**. It was once a design of its own, Prompt line (until 2026-10-21).
_Avoid_: Prompt line, Ask, omnibar, search bar

**Status swipe**:
The keyboard swipe's mirror on the top border: down unfolds the status bar, up folds it, on Home as
in the Workspace. It is there only while the status bar stands along the top and can unfold. The bar has these two forms only (a
third, the capsule, was tried in phase two and removed). A press in Android's own strip at
the top of the screen is never taken, so the notification shade still pulls down. Full behaviour in
`project-docs/reference/launcher/gestures-and-style.md`.
_Avoid_: status bar swipe (the retired page swipe on the bar), pull-down

**Style**:
How the chrome is shaped as a whole: **Docked** or **Floating**. In Floating each piece is a rounded
card with air around it. In Docked the bars join flush into one frame of glass, and the pane is a
rounded insert over it; under a **Beads** dock the frame ends above the beads, which float on the
background as they do in Floating, with no glass under the gesture pill. Corners and Margin mean the same in both: the radius, and the air or the
gutter of glass around the pane. One Style holds for both orientations and the minimal layout. It
never changes a floating or split keyboard's own shape.
Full behaviour in `project-docs/reference/launcher/gestures-and-style.md`.
_Avoid_: surface mode, dock style (it is no longer the dock's alone),
capsule, rounded

**Surface**:
One piece of glass whose look can be set: the dock, the keyboard, the status bar or the canvas
(the terminal pane). In code it is a `SurfaceSlot`. Each surface carries some of blur, opacity,
grain, tint, corner radius and side gap, and each of those either follows **Base** or holds an
override of its own. AGENTS.md's "Hit every surface" uses the word more broadly, for every path a
change has to cover.
_Avoid_: panel, layer, widget

**Terminal** (the entry):
The launcher's own app entry, pinned to the dock at migration and listed in the drawer: a tap goes
to the **Workspace** at the window you left, opening one if there is none; a long press opens a new
**Home float** on the page you are on, as Home's long-press Terminal item does.
_Avoid_: terminal icon (in prose), Create Pane, Open Workspace

**Titlebar**:
The translucent glass strip along the top of every floating pane, Home float and the scratchpad:
the drag handle and Minimise, Maximise and Close; Dock is in the menu a long press on the handle
opens. It carries no readings: the status bar shows them on both halves (2026-10-21). It replaces
the floating pane's move corner and pill; resizing stays at the bottom-right corner.
_Avoid_: pill (the retired control; the dictation pill is another thing), header, handle bar

**Wall**:
The one horizontal row the launcher pages along: the **Home pages**, then the **Workspace**. Swiping
past the last Home page reaches the first window, opening a fresh one when the Workspace has none;
dragging back before the first window returns to the last Home page. In code it is still `PaneWallController` and `PaneWallLayout`, and a page is a
`PaneWallPage`. See ADR 0008.
_Avoid_: pane wall (the retired name), pager, strip, carousel, home screen

**Window chip**:
The status bar's chip for one window, the display window or a **Home float**, the same chips in the
same order on both halves of the wall. #3 Strip's tiles show the same window chips at tile size.
_Avoid_: pill (the dictation control), window pill, run strip entry, float chip

**Workspace**:
The terminal half of the wall, right of Home: the current session's windows, then the display
windows, under the full chrome. It exists while its row has at least one window; closing the last
one returns to Home. A **Home float** is a session window that is not in the row and does not keep
the Workspace alive. A home press on it goes to Home page 1; a home press from another app comes back
to the window that was left. The drawer, the corner tab and the border drag also lead back to Home.
_Avoid_: usage mode, Terminal place, terminal environment, saved workspace (that is **Saved
sessions**)

### Look and motion

**Ambient effect**:
A built-in shader effect that animates continuously rather than in answer to something. It is off
unless chosen, is labelled as a battery cost, and stops drawing under **Lazy mode**, when its page or
pane is not visible, and after about ten seconds without input, resuming on the next touch.
_Avoid_: animated background, live wallpaper (removed), animation

**Base**:
The shared value of a surface property (blur, opacity, grain, tint, corner radius, side gap) that
every surface follows until it is given its own. A surface that follows Base is **inherited**; one
with its own value is **detached**, holding an override. The resolution happens in
`TermuxAppSharedPreferences`, so every getter returns the resolved value. Setters write through the
link, so a slider or the wallpaper mode leaves a surface inherited.
_Avoid_: global, default (the shipped value is the default; Base is what the user set)

**Event effect**:
A built-in shader effect that draws only in answer to something happening (a keystroke, a cursor
move, the bell, a pane taking focus) and stops when its motion ends, so it costs nothing at rest.
Every effect is an event effect unless it is an **Ambient effect**. Effects are built in; there are
no user-supplied shaders.
_Avoid_: animation, custom shader (users cannot load their own)

**Fancier Glass**:
The extra glass: refraction at glass edges on every glass surface and the
tilting page motions. It has no switch any more (it left Settings › Look with the Looks): it is on
wherever the device supports it — Android 13 and later, with a managed wallpaper — and each Look
sets how deep the bend is. Lazy mode, battery saver and reduced motion override it. See ADR 0005
and `project-docs/active/fancier-glass/SPEC.md`.
_Avoid_: glass mode, refraction mode

**Frost** (the slider):
Look › Custom's first slider, clear to frosted: it moves a surface's blur and grain together along
one curve (`surfaces/GlassControls`; the code still calls it Glass, `GLASS`, `surface_glass_position`).
Clear sits at 20, Tint at 40, Mist at 75; Solid has no frost and sits off it. Storage keeps blur and
grain; the slider reads them back. Not the frost layer itself (the blurred picture a surface draws).
_Avoid_: Glass (the old label; every control on that page is about the glass), material (the older
family macro)

**Edge** (the slider):
Look › Custom's colour split along the glass edge, shown with Depth where the edge is drawn
(Android 13+): 0 no fringe, 40 Glint (Clear's corner glint), 100 a bold coloured edge about
3.5 dp wide all round. It is the stored dispersion (`fancier_glass_dispersion`). Clear ships 40,
the other Looks 0. It replaced the Off/Glint/Prism pill (2026-10-17).
_Avoid_: Prism (the retired third step), edge light (the gathered light itself, which is fixed)

Shade, a dimmer layer under the tint, was retired on 2026-10-17; a stored value is dropped.

**Tint amount**:
What Look › Custom's Tint slider sets since phase B: how much accent colour is mixed into the
Look's own colour, 0 to 100, from any Look. 100 is the Tint Look's Material blend. The **Hue** pill
picks which accent (primary, secondary, tertiary).
_Avoid_: tint strength (the old meaning, a share of the Look's tint family)

**Vibrancy**:
Look › Custom's saturation of what the glass shows, 0.7x to 1.5x: baked once into the shared frame
the glass samples, never into the bare wallpaper. 1.0x in every Look.
_Avoid_: saturation of the wallpaper

**Text size**:
Look › Custom's last global slider, 80% to 150%: one size for the type the launcher draws itself,
the terminal font, the keyboard's key legends and the chrome labels (status bar figures, window
chips, the dock strip's titles, Home's app labels). Each is drawn at its own size times it, so a
surface's own size stays an offset: pane zoom and the terminal font setting, Home's label size.
100% is every install's sizes as they were (`text_size_percent`, unset until moved).
_Avoid_: font scale (Android's own, which still applies on top), zoom (the terminal's pinch)

**Lazy mode**:
The Battery setting that stops the launcher animating while nobody is watching: the clock swaps its
digits instead of folding them, a working window's rim holds lit, and the status readings are sampled
less often. It is meant to become the default. See `docs/en/Launcher_Settings.md`.
_Avoid_: battery saver (Android's own), low-power mode

**Look**:
The appearance of one half of the wall, set by the Appearance editor's slider. The slider has five
stops: Clear, Mist, Tint, Solid and Custom. A surface's own slider moves the Look to Custom by
itself; Custom is never a gate. Home and the Workspace each hold their own Look and own surface
values, equal until one is edited (ADR 0013); one look covers both orientations.
_Avoid_: theme (the light, dark and black scheme is separate), preset, skin

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
re-blurs or cuts a bitmap while pages slide, and why glass stays registered to the wallpaper during
parallax. Held at blur resolution, about a quarter of the screen. See ADR 0002, 0004 and 0005.
_Avoid_: blurred wallpaper, backdrop crop (the retired per-surface cut-outs)

**Slab**:
The rounded sheet of glass a pane, a Home page or a display window is drawn on: its outline
(`PaneGlass.slabOutline`) sets the radius the glass and its rim are cut to. Anything that copies a
pane while it moves, such as the departure card, copies the slab and nothing around it.
_Avoid_: tile, card, panel (the Panel is the dictation text)

**Stand-in**:
A still copy of a display window's picture that stands in for its `SurfaceView` while the wall
moves (`wall/SurfaceStandIn`). A surface is composited outside the view hierarchy, so it cannot
tilt or take a layer; the stand-in is a plain view that can. The picture freezes for the few hundred
milliseconds of the motion.
_Avoid_: screenshot, freeze frame

### Editing

**Appearance editor**:
The one editor for how the launcher looks and sits, over the launcher scaled into a frame that
slides between Home and the Workspace. Its rule is **tap the thing**: tap a surface (Home, Status
bar, Terminal, Dock, Keyboard) and its **card** rises with everything about it, ordered by how
often it is touched, rare controls under **More**; drag a surface to move it; tap the **pill** for
what has no surface (the Look slider, wallpaper, theme, icon pack, text size, full screen, minimal,
orientation, Undo, Done). It edits the half the frame shows. Users never meet Base, inherit or
follow: a moved slider is the surface's own value, marked by a dot, and **Reset** in the card menu
puts it back on the Look. Undo steps back and Done keeps the changes. Every door to it is named
Appearance and opens on that door's object.
_Avoid_: surface editor, look editor, Look page, Layout page (the 2026-10-24 merge removed the pages), style editor

**Corner tab**:
The small control strip revealed by holding a page's corner. On a Home page it carries Appearance,
Layout, Wallpaper and help; on a window it carries Appearance, Layout, Wallpaper, Minimal mode,
Settings, help and Home. On a split pane it carries Close, Move and Maximise instead: a
split pane has no Appearance or Layout of its own, and a corner hold only shows the buttons.
_Avoid_: corner menu, pane menu, controls view

**Element**:
One piece of the arrangement the Layout editor shows or hides: the status bar, the pinned apps,
the A–Z index, the extra keys — each on an edge, in an order, or put away in the tray, the
status bar included now that paging is the border drag — and the keyboard,
which has no edge and no order, only on or off (in the tray or out). Along the bottom the dock's rows (not the status
bar) may stand **under the keyboard** instead of over it; with the keyboard down they are simply
the bottom's outermost bands. The keyboard's on/off is the one switch the
palette's **Keyboard on/off** flips (`keyboard_turned_off`), shared by both orientations and every
page; the last write wins, whichever door it came through.
_Avoid_: bar (for the keyboard), widget, slot (the store's word for where an element stands)

**Home layout**:
An opt-in layout of Home's own, for a user who wants Home laid out unlike the Workspace. Off by
default, when Home wears the one shared layout. While on, Home holds its own copy of which rows show
(the apps row, the A–Z row, the status bar), their order, edge and under-keyboard placement, the
**Dock design**, and the Search field and Drawer tile toggles (with the tile's position). The sizes
stay **linked** to the shared layout either way: the apps row's height, the #3 Strip row's height,
the keyboard's height and chin, and **Style**, Corners and Margin are one value on both halves,
edited from either, so nothing resizes on the swipe (decided 2026-10-21, #84). The keyboard's form
stays Home's own. It is the Layout editor's "Layout: One layout | Home's own" line; the row's house
segment previews Home. It is to the layout what a surface's override is to **Base**.
_Avoid_: Home variant, Home place

**Layout editor**:
Legacy name for the Appearance editor's move and size controls (2026-10-24: there is one editor,
see **Appearance editor**). The engine underneath keeps the name in code: a bar moves by dragging,
anything is put away by dropping it in the **restore tray**, and a selected element shows its
**handle** with a readout in real units. One layout dresses both halves of the wall; only look
values differ per half (ADR 0013).
_Avoid_: Layout page, Layout tab, Layout (as the name of a door; the door is Appearance)

**Layout canvas**:
The scaled model of a page's layout that the user drags elements around on, in the Layout
editor; there is no second one. Code still calls it the miniature (`LayoutCanvasView`) until
it is redrawn from the layout element pack.
_Avoid_: miniature (in new prose), preview, thumbnail, overview

**Restore tray**:
The strip under the layout canvas: one chip, with a struck-through eye, per hidden element, which
brings the element back to the edge it was put away from; one short line when nothing is hidden;
"Drop here to hide" while something is lifted.
_Avoid_: Hidden box, shelf

### Widgets

The launcher's own Home widgets after the redesign (ADR 0010, decided 2026-10-21, research
`project-docs/active/widget-redesign-research.md`). None of it is built yet: until it is, the code
and the picker still have the twelve older kinds, whose names are kept under _Avoid_.

**Bare**:
A widget drawn without its card: its ink sits straight on the wallpaper, dimmed only where it would
not read. It is an opt-in per widget, offered by the Clock, **Glance** and Weather. The **Look**'s
Clear stop does not turn it on.
_Avoid_: Clear (that is a Look stop), transparent, no card

**Clock face**:
One of the designs a clock is drawn in: Analog, Numerals, Slab, Flip, LCD, LED matrix, Tape and
Minimal, the same designs in the status bar and on the Clock widget. A new Clock widget starts on the
bar's current face, and from then on each is chosen on its own.
_Avoid_: clock style (the setting's old name), skin, Analog clock and Digital clock (the two widgets
the Clock replaces)

**Device**:
The widget for the phone's own state: battery first, then storage, memory and temperature as it
grows, and the processor only where the launcher has privileged access.
_Avoid_: Battery widget, System widget (the two it replaces), stats

**Glance**:
The one-row widget for the first look of the day: the date, the next event, the weather, the next
alarm and, when low and not charging, the battery. It draws **Bare**. It never shows the time, which is the Clock's.
_Avoid_: At a Glance (Pixel's), summary, today

**Notes**:
The widget that shows a markdown file from the home folder, opening it in the editor on a tap.
_Avoid_: Scratchpad (as the widget's name; the scratchpad is the toggled floating terminal), memo

**Widgets on glass**:
The **Look**'s one switch for whether the launcher's widgets wear its glass, following **Base**. A
widget's own Glass switch can still set it apart.
_Avoid_: widget glass (for the per-widget switch alone), glass mode

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
A simple store where prebuilt binaries for termux-launcher are distributed: the tools and configs
the launcher shows off but does not ship in the APK. It is a separate repository, [PickleHik3/tlstore](https://github.com/PickleHik3/tlstore); the
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

**Feature** (AI feature):
A launcher task that uses a model: the assistant, cleanup, app sorting, Dawn search, Dawn chat, voice
typing, read aloud. Each has its own model picker in the Model Centre. In code it is a `TaiFunction`.
_Avoid_: function (in prose), role, task

**Feature load plan**:
The one answer to "how does this feature run on this phone": on-device or remote, the model, CPU or
GPU, speculative decoding, the context window and how long the model stays loaded, each with the
reason it was chosen (your pick, measured on this phone, or the phone's default). Requests name the
feature and the runtime follows the plan; the Model Centre shows the same plan.
_Avoid_: load policy, load options (those are what one load was given, not the plan)

**Feature check**:
The part of the benchmark that runs each feature's real workload on this phone, compares CPU and GPU
and speculative decoding on and off, and records the result the feature load plan reads.
_Avoid_: speed test, feature bench

**Feature group**:
Features that work together and stay loaded together while in use: voice typing and cleanup, Dawn
search and Dawn chat, a spoken Dawn conversation.
_Avoid_: session, bundle

**GPU verdict**:
Whether the phone's GPU gives fast, correct answers. It starts from a rule by GPU family and becomes a
measurement once the self-test or a feature check has run on the GPU; "Try GPU again" clears it.
_Avoid_: GPU path (the code's name for the family rule), GPU support

**Speculative decoding**:
Generating several tokens per step from a draft the model checks, so replies come faster. Only some
model files support it.
_Avoid_: Eagle, MTP, draft model (older names in code, the CLI and the bench)

**Launcherctl**:
The launcher's localhost HTTP server and its command-line tool. The server exposes the OpenAI- and
Ollama-compatible endpoint, model management, app launch, the pane routes a shell uses to drive
terminal panes of its own, notifications, progress, clipboard and the device commands. `launcherctl`
is the client for everything but AI, and `tai` is the client for the model routes. Both read
`~/.launcherctl/endpoint` and `~/.launcherctl/token`. See `docs/en/LauncherCtl_API.md`.
_Avoid_: the API server, the launcher daemon

**On-device AI**:
The launcher's on-device AI: the models it holds and the local endpoint programs in the terminal
talk to. Programs reach it the way they reach any OpenAI-compatible service. Its runtime and
command are **TAI**.
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
`tool:voice.dictate` on the extra-keys row: starts or stops a dictation on any page, with the
keyboard up, down or not the launcher's.

**Dictation**:
One stretch of speech-to-text, from the voice key or the Dictate key until it pauses (a second tap,
the pill's pause, the silence timeout). What is said collects in the panel as heard; nothing is typed
while the user speaks.
_Avoid_: voice typing (the Android recognizer's path), voice session

**Insert**:
✓: the dictation typed once where the keyboard would type on the page on screen, never with an
Enter. Where nothing takes typing, it copies instead.
_Avoid_: replace, swap (the retired in-place rewrite of the terminal line). The pane's rounded
"insert" under the Docked **Style** is a different thing; say "the pane insert" there.

**Mic sensitivity**:
How readily quiet speech opens a phrase: Normal (the default) or High. It sets how far the audio
Silero judges is lifted to match the room, and the voiced time a phrase needs. High catches soft
speech and hears nearby talk and TVs more.
_Avoid_: gain, VAD threshold, microphone volume

**Panel**:
The dictation card's body under its strip, growing downward (upward when placed low): the
dictation's text (seven lines, the oldest scrolling off the top) over one row of controls: pause
with the waveform in it (resume once the microphone is closed), undo, Copy and Insert. It is where
the text waits until Copy or Insert is used. Text as heard is italic in the secondary colour;
cleaned text is upright in the primary one.
_Avoid_: transcript view, preview

**Pause / resume**:
Pause stops listening: the phrases still transcribing arrive, then the cleanup runs on its own.
Resume listens again and carries on the same text; the next pause cleans all of it. The voice key
and the Dictate key do the same (start, pause, resume).
_Avoid_: stop (the × is not a stop)

**Pill**:
The dictation card at the top right of the page viewport while a dictation is up: a strip (state,
a few words of detail, the handle, ×), then the panel. Same corner on every page until moved by
its strip. The screen stays on for as long as it is up (released after 3 minutes of untouched
waiting text, and at once when it closes or the app pauses).
_Avoid_: voice indicator, overlay

**Pill handle**:
The strip along the top of the pill, with its short centred handle: dragged, it moves the pill
anywhere in the page viewport, remembered per orientation beside the floating keyboard's spot; a
double tap puts it back in the top right corner. Placed low, the panel grows upward.
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

**Baseline**:
A performance number measured on pong that a change must not move the wrong way: idle PSS, frame
time while typing, frame rate at rest, cold start. A move bigger than the noise in the wrong
direction makes the change **red**. The table is in `docs/agents/performance.md`.
_Avoid_: benchmark (that is `tai benchmark`), target (a target is a goal not yet met)

**Budget**:
A limit the code enforces on what a store may keep, as a constant beside the store with a test that
asserts it: `KittyImageStore.FrameBudget`, `LauncherIconStore`, `DockIconCache`,
`WallpaperBlurCache`. Anything that holds pixels has one. See `docs/agents/performance.md`.
_Avoid_: cap, quota (in prose; `frameQuotaForThisDevice` is the code's name for a budget's size)

**Companion apps**:
Forks of the Termux plugins (termux-api, termux-styling, termux-boot), one branch per edition and
released on their own cadence, not with a launcher cut. A plugin is only granted the launcher's
permissions when it shares that edition's `sharedUserId` and signature, so each edition needs its
own build; all are signed with the shared `testkey_untrusted.jks`, which is why an F-Droid
plugin never pairs. The Nix edition's are TLNix API, Styling and Boot.
_Avoid_: plugins, add-ons (the README's word for the same apps)

**Edition**:
One shipped applicationId, each on its own release branch: `com.termux` (the Termux edition, on
`main`), `com.termux.launcher.nix` (the Nix edition, on `nix-edition`) and `io.vaj.tl` (the VAJ
edition, on `io-vaj-package`). Content reaches an edition only by merging `dev`. An
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
_Avoid_: staging (the build type), release candidate

**Janky**:
In the developer's words, something that looks wrong: misplaced, stale, abrupt or ugly. Read it as
a visual complaint first. For frames that miss their deadline, write "long frame" or "dropped
frame" and give the number.
_Avoid_: janky (for a frame-time measurement; say "long frame" there)

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
record for measurements and device checks. Its panel is 120 Hz; the launcher runs at 90 Hz while
active and 10 Hz at rest. It is also the developer's daily driver, often in use
while an agent works: ask before installing, force-stopping or sending input to it. Builds for it
usually come from an integ branch.
_Avoid_: the device (when a note means a phone the agent owns), the emulator

**Pre-release**:
The rolling channel that gets new work first. The order is `pre`, then `dev`, then the release
branches. A **drop** is one pre-release, versioned `X.Y.Z-pre.N` and shipped in all three editions.
`X.Y.Z` is the next minor version above the current stable. The rules are in
`docs/agents/releasing.md`.
_Avoid_: beta, nightly (a nightly is any build of `dev`), release candidate

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

**Staging build**:
`assembleStaging`: the release build's R8 shrinking and keep rules, but debuggable (so `run-as`
works) and signed with the published test key. Nightlies and phones get this build. A debug build
skips R8, so it cannot show a hole in the keep rules.
_Avoid_: release build (that is the published, non-debuggable type), debug build

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
