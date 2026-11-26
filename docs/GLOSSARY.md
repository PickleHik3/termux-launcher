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
Settings › Home › Gestures › Swipe on the terminal to switch windows (the Home float's
is always on). One rule, `TerminalBodySwipe`.
_Avoid_: terminal swipe, text swipe, rule B

**Bottom stack**:
The bottom of the window seen as one stack: the dock's rows, the keyboard and the glass behind them, laid out once for both **sides** and moved by a crossing as a single piece. See **Top stack** for its twin and **Stack layout** for its geometry.
_Avoid_: dock area, accessory stack

**Border drag**:
The one-finger gesture that pages the wall from a **window**, in every mode. It starts on the
current window's border: the frame line on any of its four sides and the air between it and the next
thing out, less the corner squares. Anywhere on it outside the window's text (the air on all four
sides, and the side borders' 2 dp of clearance inside the frame) it is a plain sideways swipe that
pages at once, past the touch slop, with no hold. A hold there still starts it too. Where the border
reaches further into the window (a window almost flush with the wall) it is a hold and then a
sideways drag. The bottom border reaches a little further, a fingertip tall: a clearly sideways
swipe from the window's last row or the dock's top air pages too. A drag that starts in the
terminal's text is the terminal's, unless it is a **body swipe**, which pages the same way. On the
**display window** the border stops at the window's edge: its picture is the Linux app's to the rim,
so neither the side clearance, the boxes by the grabbers nor the last row's band reach into it, and
only where the air is thinner than the least band does the frame line itself page. The edge lights in
the accent colour inside the frame where a finger can page. A **Home page** has no border: it pages
by a plain swipe anywhere on it, as any launcher does. Reach, hold, glow and tilt are
specified in `project-docs/reference/launcher/gestures-and-style.md`.
_Avoid_: edge swipe, status bar swipe, page swipe

**Border hold**:
A finger held still on a **window**'s left or right border for about 1.5 s pages the row once,
with a vibration and a small `<` or `>` at that border as the hold fills. On every window kind and
during a **carry**; a hold that moves first is a **border drag**. Decided 2026-11-24, not built.
_Avoid_: edge hold, long-press paging

**Carry**:
Moving a **Linux window** by holding Move on its **rim tab**: the window lifts and follows the
finger across pages (a **border hold** or a second finger's swipe pages under it), and the drop
decides the result: over a pane or another Linux window, a split on that half; over a **Home page**,
a **Float**; between two windows, its own window in that spot. Decided 2026-11-24, not built.
_Avoid_: drag and drop, move mode

**Chrome**:
Everything the launcher draws around a page: the status bar, dock, keyboard, borders and corner
tabs. The **Terminal** wears all of it; a **Home page** wears the same status bar, starting below
it as a window's page does, and the same dock (without the extra keys) and the keyboard, as
overlays that take no room from the grid. The dock and
status bar are one each across both halves and stay put while the wall moves (decided 2026-10-21,
replacing the 2026-10-19 Home without a launcher status bar).
_Avoid_: UI (too wide), decoration

**Crossing**:
One slide of the wall between Home and the Terminal, from the first frame the wall moves to the
frame it lands. During a crossing the dock morphs from the leaving half's **Dress** to the arriving
one in step with the wall's offset (icon size, row width, bands, Search field and Drawer tile), so
nothing changes at the landing. The keyboard travels with the half that has it open, and a reversal
or a chip tapped mid-slide retargets from where the wall is. The pages move by the **Page
transition**; the status bar, window bar, dock and keyboard stay flat and ease every difference
between the two dresses (Margin, glass) with the offset. A crossing is over only when the wall
lands.
_Avoid_: travel (the code's older word for the same motion), transition, page swipe
_ADR 0015 (proposed): any slide between two neighbouring pages, not only between the halves._

**Display**, **display window**:
One X display, shown whole by the embedded X server (`x11-server/`) as a **window** in the
**Terminal**: a Linux desktop or the X11 apps on it, managed inside by the display's own window
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

**X11 Display**, **Wayland Display**:
The two ways Linux apps reach the screen, chosen under Settings › Linux apps › Display. **X11
Display** is the embedded X server and its one **display window**; **Wayland Display** is the
launcher's own compositor (`wayland-host/`, its own process), which shows each app window as a
**Linux window**. X11 Display is the default and stays until the developer says otherwise; Wayland
Display is in every build, CI's included, unless it is made with `-Pwayland=false`.
_Avoid_: backend (in copy), Termux:X11, compositor (in copy)

**Dock**:
The band of chrome that carries the pinned-apps row, the A–Z index and the extra keys, along the
bottom by default. There is one dock for both halves of the wall, dressed by one **Dock design**;
only the Terminal's carries the extra keys. Its shape follows the **Style**. Its rows may stand under
the keyboard instead of over it. Not the same as the Layout editor's restore tray, which holds elements that are put away.
_Avoid_: taskbar, nav bar, hotseat
_ADR 0030 (accepted, not yet built): the dock's rows belong to a **side** and travel with it, riding the keyboard's top; the glass slab stays with the shared stack and only blends. Floating follows the same rule with its own card geometry._

**Dock geometry**:
The pure module that answers every measurement of the dock: one snapshot of both halves in, one DockLayout out, down to the final per-slot icon. A slide is an input to it, never a path of its own (ADR 0011).
_Avoid_: dock sizing, dock policy (the internal arithmetic only), per-caller measuring, travel icon size

**Dock rows**:
The one adapter that writes the dock's accessory views (the pinned-apps row, the A–Z index, the extra keys and the bands under the keyboard) from the applied DockLayout, and announces which Elements are on screen. Not the rows themselves.
_Avoid_: dock controller, row visibility code
_ADR 0030 (accepted, not yet built): the stack's writer places each side's row host from the scene, and both sides' rows stay laid out and dressed at all times; a landing only switches touch and focus._

**Dock design**:
Which of the dock's variants dresses it, held by the layout (so on both halves unless Home holds a
**Home layout**): #1 Tray (the default), #2 Beads (each pinned app its own round glass bead floating
on the background with air between, no shared card, in both Styles) or #3 Strip (a big clock and a
tile per terminal over the apps; the clock slides out at 3+ terminals). The extra keys are not part
of any design: the Terminal shows them under every design and Style. Separate from the **Style**, which shapes all the chrome, and from the **Search field** and
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

**Default drawer**, **section**:
The app drawer type a new install opens on (2026-11-21): two pages side by side, **Apps** (the
Vertical grid with its A–Z strip) and **Categories** (the Categories tiles), moved between by a
horizontal swipe or a tap on the **section switch**. Each page is a **section**; the drawer
remembers the one it was left on, and Back never changes it. Upgrading installs keep their type.
Code: `AppDrawerViewType.DEFAULT`, `AppDrawerSection`, `AppDrawerSectionPager` (the one position).
_Avoid_: tab, page (Horizontal's pages), section index (the A–Z index, `AppDrawerSectionIndex`)

**Section switch**:
The centred capsule at the top of the **Default drawer** with the two section names; a smaller
capsule rides under the selected one, following the swipe. In code `AppDrawerSectionSwitchView`.
_Avoid_: pill (the dictation pill), tabs, toggle

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
The launcher half of the wall: the **Home pages**, left of the **Terminal**. It is always there,
and it is where the home press lands. Its chrome is the dock with the pinned-apps row and the A–Z
index, the launcher status bar it shares with the Terminal, and the keyboard; it has no borders.
_Avoid_: home screen (as a name for the whole launcher), the widgets place, Widgets page
_ADR 0015 (proposed): Home stops being a half; a **Home page** is one page kind among three, and the home press lands on the **home page**._

**Home page**:
One page of **Home**: a grid of widgets, paged by a plain swipe anywhere on it. Pages 2…n lie to the
right of page 1, and the first window lies to the right of the last page. A long press on an empty
cell offers Add widget, Wallpaper and Terminal (a **Home float** here). A Home page's floats are
pinned to it.
_Avoid_: place, widgets page, desktop

**Home exit**:
What a home press does with a screen open over a page (ADR 0034). A screen holding unsaved changes
asks first, over itself; then everything open leaves together and the press stays on the page
underneath: the wall does not page. With nothing open it is not a Home exit: the home press goes to
the **home page**, or back to the page left when it returns from another app.
_Avoid_: Home close, clear on home

**Home float**:
A **window** of the current session drawn floating over one Home page instead of in the Terminal's
row: born full-size, moved only by its **titlebar** (a **body swipe** on its terminal pages Home as
a swipe beside it does), resized at its bottom-right corner, minimised into
its **window chip** in the status bar and docked into the row by the titlebar's Dock. It is what
Terminal from Home opens. The bar's chips, the drawer and the shortcuts see it like any window.
_Avoid_: floating terminal (in prose), scratchpad (that is the toggled one), popup
_ADR 0015 (proposed): becomes a **Float**, which may also hang over a Terminal page window._

**Float chip**:
Retired 2026-10-21: a minimised **Home float** is a **window chip** in the status bar on both
halves, and Minimise flies the float into that chip. There is no row of chips above the dock.
_Avoid_: float chip (for the bar's chip, say window chip), minimised pill, Home pill (that names the extra keys on Home, not a minimised float)

**Home pill**:
Home's extra keys as a small floating glass pill over Home's content, in the same spot on every
**Home page**. A hold picks it up to move it and shows "Edit keys". It flies to the docked bar above
the keyboard, and between Home and the **Terminal**'s pages.
_Avoid_: floating bar, key bubble

**Keyboard swipe**:
The one way to the keyboard in the **Terminal**: an up or down swipe that starts on the window's
bottom border, or at its **grabber**, before the border drag's hold takes. Up opens the keyboard
(turning a switched-off keyboard back on), down closes it, and the Terminal remembers which. On a
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
_ADR 0028 (accepted, not yet built): open layers live in the sheets **window layer**. Do not call a window layer plain "layer"._

**Linux app**:
An app installed in the prefix or in a `proot-distro` container, read from its desktop file and
listed in the drawer under Linux Apps beside the Android apps. Under **X11 Display** a tap runs it on the
**display**, opening a display window if none is up; under **Wayland Display** it opens a **Linux
window**. One marked `Terminal=true` opens a new terminal **window** under either. A **desktop**,
the **Terminal** entry and the Display entry are listed with them.
_Avoid_: X11 app (some run in a terminal), desktop entry (the file, not the app)

**Desktop**:
A whole Linux desktop the user installs and opens as one thing: XFCE, LXQt, MATE, Plasma, sway,
labwc. It is listed in the drawer with the **Linux apps**, and the apps opened inside it stay
inside it. Under **X11 Display** it takes the **display** for as long as it runs; under **Wayland
Display** it is one **Linux window**. A desktop made for X11 can open under either; one made for
Wayland opens only under Wayland Display. Decided 2026-11-26.
_Avoid_: display (that is X11 Display or Wayland Display), session (a terminal's), desktop
environment (too long for a row, and wrong for sway and labwc), DE, window manager (in copy)

**Linux window**:
One app window shown as its own **window** in the **Terminal**'s row under **Wayland Display**,
beside terminal windows: the window dress, a window chip with the app's name and icon, and a **rim
tab**. An app opens full screen in a new Linux window after the current one; the user **carries** it
into a split or a **Float** on purpose. A dialog with a parent is drawn over its parent's window. The
**border drag** stops at its edge, as on the display window. Decided 2026-11-24; until it is built,
Wayland Display shows Linux windows as tabs on their own screen.
_Avoid_: app window (confusable with Android apps), Wayland window, X11 window

**Managed wallpaper**:
A wallpaper set through the launcher's own picker, of which the launcher keeps its own copy.
Only a managed wallpaper can move with parallax; a wallpaper set anywhere else stays still.
_Avoid_: custom wallpaper, in-app wallpaper

**Minimal mode**:
A second saved **Terminal** layout, and nothing more. It starts with only each window's content, so
by default the bars, the A–Z index, the extra keys and the keyboard go away, and it changes no
behaviour. It is one mode for every window, turned on and off only from the corner tab's minimal
button or the command palette's Minimal action, and stays on until turned off. Home ignores it:
Home wears the normal shared layout (or its **Home layout**) whatever minimal says (decided
2026-10-21, after a day of Minimal on both halves left Home with no way out). The Layout editor opened while it
is on edits the **minimal layout**. Full behaviour in
`project-docs/reference/launcher/gestures-and-style.md`.
_Avoid_: full screen, focus mode, zen mode, zen view

**Notice**:
The one transient message in the app, on a pill centred at the top of the screen, inside the
terminal's rim while a terminal is on screen. Replaces stock toasts.
_Avoid_: toast, pill (that is the dictation card), banner, chip

**Orientation**:
Portrait or landscape. The shared layout has one version per orientation (and per layout variant,
see Minimal mode); appearance is never per orientation.

**Overview**:
The screen that shows every page of the **wall** as one sideways strip of cards in wall order and
goes to any of them: a tap on a card lands on its page, and the card of the page it was opened
from wears the accent ring. Opened from the status bar's session number; Back and its ✕ return to
that page. A sheet: it wears **Global**'s glass at the sheets' floor over the wallpaper. It is to
replace the sessions manager and **Edit pages**. Spec: `project-docs/active/overview-2026-11-24.md`.
Code: `com.termux.app.overview`.
_Avoid_: page overview (that is Edit pages, `PageOverviewView`), switcher, recents, sessions manager

**Page**:
One full-screen stop on the **wall**: a **Home page** or a **window**, each with a **page kind**, a **side**, a **rest** and its own **front** and **back** handling. Appearance is one look for
every page. One layout dresses both halves, minimal only for windows; a Home page wears its own
only when Home holds a **Home layout**.
_Avoid_: place (the retired word for Home, Terminal and Display as three pages), screen, tab
_ADR 0015 (proposed): a page is a Home page, the Terminal page or a Display page, and each follows **Global** or detaches its own look values._
_ADR 0030 (accepted, not yet built): every Home page and every window (terminal, display, Assistant) is a page of one contract and names its **side**; a window is a page of the Terminal._

**Page bar**:
The glass pill above the dock on Home: one glyph per page in the wall's order (a stacked glyph with
a count where neighbouring pages of one kind fold), the current page in the accent, and the dock's
page capsules under it. Tap a glyph, or slide along it and let go; the glyph under the finger rides
in a round bubble with the page's name. The page switcher key pops the same bar up for one use.
Spec: `project-docs/active/page-glyph-switcher-2026-11-16.md` ("Page bar"). Code:
`com.termux.app.wall.pageswitch` (`PageBar*`).
_Avoid_: glyph row, page panel, dots, ticks (the retired versions), scrubber

**Marks**:
The second look for the pages, chosen under Status bar › Page bar (Glyphs or Marks; a new install
wears Marks, an existing one keeps Glyphs). Each page is a plain mark: Home a dot, a Terminal window
a prompt, a Display a screen, the Assistant a spark; the current page is a solid disc with its
number or sign cut out, and a window that wants you gets a small burst. In the status bar the
**Marks line** stands where the window chips were, along the row or down a side edge's column, on
every page; tap a mark to go to that page. Spec: `project-docs/active/status-row-and-marks-2026-11-23.md`.
Code: `PageBarLook`, `PageMarkShapes`, `MarksLook`, `MarksLine` in `com.termux.app.wall.pageswitch`;
`MarksLineView` in `com.termux.app.statusbar`.
_Avoid_: dots, ticks, glyph row (the page bar's own **Glyphs** look)
_Decided 2026-11-26, not yet built: only Home is a dot; a Terminal window is a rounded square with its number cut out, a Linux app its own icon, a window running a full-screen program that program's glyph. The current page is its mark inside an accent ring. A touch zooms the marks at once and the finger slides to a page, whose **title** shows at the row's right end. On Home the **page bar** wears this look above the dock._

**Title** (of a mark):
The name of the page under the finger, shown at the right end of the status bar while the finger
slides along the **Marks line**; the readings give it their place. Decided 2026-11-26, not yet
built; it replaces the bubble the Marks line shows today.
_Avoid_: readout, chip, label, bubble (the round bubble is the **page bar**'s, under Glyphs)

**Page switcher**:
The page switcher key: an optional key on the extra-keys bar whose cap shows the current page's
glyph; it pops up the **page bar**.
_Avoid_: dot, picker, places dock (the retired space-bar version), nav button, floating button

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

**Read-out**:
A fleeting **notice** naming what a key just did or something still under way, such as a pane's
size in cells during a resize drag. A new label swaps its words in place, and any real notice
displaces it.
_Avoid_: size banner, resize overlay, hint

**Rim tab**:
The small glass tab on a **Linux window**'s rim at its top-right corner, outside the app's pixels. A
tap shows Close (asks the app; Force close is offered if it is still there after 5 s), Minimise (into
its window chip), Move (a handle: hold it to **carry** the window) and Scale (the app's scale rail).
A Linux window has no **corner tab**; as a **Float** it wears the **titlebar** instead.
_Avoid_: corner tab (that is the terminal window's), handle, pill

**Rest**:
What a **page** asks of the chrome while it is in front and still: whether the keyboard is up, whose extra keys show, whether its text fields type with the phone's keyboard, what the bars show and the look it wears.
_Avoid_: resting state, chrome plan

**Scene**:
The pure answer to everything a stack shows on one frame, from a crossing's two ends and how far it has gone; a renderer is the only code that writes it to the views.
_Avoid_: pose table, layout pass

**Saved sessions**:
A named set of sessions, windows, splits and working directories kept under `~/.termux/workspaces/`
and loaded back as new shells. The drawer's Saved tab lists them; Save sessions… adds one.
_Avoid_: workspace, saved workspace, durable workspace (the files and `launcherctl` keep the old
name; prose and the drawer do not)

**Session**, **window**:
The terminal's own layers: a session holds windows, and a window holds a tree of **panes**. A window
is also the unit the **Terminal** pages between: a terminal window (its split tree) or a **display
window**. New windows (Ctrl+Alt+C, a Terminal=true Linux app, Terminal from Home) land right after
the current one. Each pane runs its own shell, which is a `TerminalSession` in code; that is the
code's word for a pane's shell, not for a session. See `project-docs/reference/terminal/split-panes.md`.
_Avoid_: tab (for a window), terminal (for a pane), column (Niri's word; in prose say window)

**Side**:
Home or the Terminal: the two halves of the **wall** seen from the seam. A side owns the rows its pages share (Home: the apps row, the A–Z row, the search field and Home's own extra-keys bar; the Terminal: the extra keys, the minimal-layout tab and the **window chips**) and travels with them across a crossing. Every **page** names its side.
_Avoid_: half (the pre-0030 word), place, "its page" for a shared row
_ADR 0030 (accepted, not yet built): the app as built still lays rows out per page and swaps them at the landing._

**Top stack**:
The top of the window seen as one stack, like the bottom one: the status bar's glass (the strip behind the system bar and the bar's own card) is one slab both ends of a crossing share. It stays put and blends its look, frost and ink from the leaving page to the arriving one on the crossing's curve; the clock and readings stand on it; the **window chips** are the Terminal's and slide across it with the Terminal, so on Home they rest out of sight. A bar only one end shows still rides with its page. The bar edge setting puts the same stack at the top or the bottom. In code `TopStackScene` (pure) and `TopStackRenderer` (its one writer), ADR 0030 section 6.
_Avoid_: top band (the pre-#267 name for the strip and bar's glass), wallpaper twin (the bars draw their own frost now)

**Side lane**:
The part of the **bottom stack** that only one **side** uses: a row only Home's layout has stands in Home's lane and a row only the Terminal's has in the Terminal's, while a row both show in the same place belongs to the shared stack.
_Avoid_: half row, per-side row

**Start in**:
The one setting that says where a cold start and a home press land: Home (the default), the
Terminal, which opens one fresh shell window (a home press goes to its first window), or Display, the **display window**, which holds only while the
display starts with the launcher (choosing it turns that on; turning it off falls back to the
Terminal when a terminal window is open, else Home). It is also what the page overview's home
button sets: a Home page, the first terminal window or the display window. It replaces the usage
mode's Terminal alone. Under **Use as** Terminal only it offers Terminal and Display, and a stored
Home reads as the Terminal.
_Avoid_: default place, start page, usage mode
_ADR 0015 (proposed): retires into the **home page**._

**Use as**:
Home screen (the default) or Terminal only, asked on the welcome sheet's first page and kept in
Settings › General › Launcher beside **Start in** (`use_as`, policy in `UseAs`). Terminal only takes
the Home page off the wall, hides Home's settings, drops Home from Start in and removes the Home
card and both Match rows; the home press lands on the Terminal. Whether the app is the phone's
home app is a separate choice.
_Avoid_: usage mode, launcher mode, terminal alone
_ADR 0015 (proposed): retires as a setting; the welcome sheet's choice only seeds the wall._

**Search field**:
The field under the dock's apps row that opens the command palette, there when the layout turns it
on, with any **Dock design**. It was once a design of its own, Prompt line (until 2026-10-21).
_Avoid_: Prompt line, Ask, omnibar, search bar

**Status swipe**:
The keyboard swipe's mirror on the top border: down unfolds the status bar, up folds it, on Home as
in the Terminal. It is there only while the status bar stands along the top and can unfold. The bar has these two forms only (a
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
One piece of glass whose look can be set, also called an **element** in the look grid: the status
bar, the dock, the keyboard, the terminal (the canvas), the **Widgets** (the Card-form widgets'
glass on a Home page) and the **Sheets** (the app drawer, folders, anchored menus, the command
palette, notices and the Home float, which share one set of cells). In code it is a
`SurfaceSlot`. The status bar, the dock and the keyboard may each hold an **own value** for
Opacity and Blur and otherwise follow **Global**; the Widgets and the Sheets hold nothing of their
own; the terminal has its own background (ADR 0031). AGENTS.md's "Hit every surface" uses the word
more broadly, for every path a change has to cover.
_Avoid_: panel, layer, widget (for the Widgets element say "the Widgets surface")
_ADR 0031 (accepted, not yet built): as built every element holds every look property, per kind of page._

**Terminal entry**:
The launcher's own app entry, pinned to the dock at migration and listed in the drawer: a tap goes
to the **Terminal** at the window you left, opening one if there is none; a long press opens a new
**Home float** on the page you are on, as Home's long-press Terminal item does.
_Avoid_: terminal icon (in prose), Create Pane, Open Workspace

**Terminal engine**:
What reads a pane's output and keeps its screen and history: **Standard** (the Java core Termux
has always had) or **Fast** (libghostty-vt, Ghostty's terminal library, about 20 times quicker at
reading heavy output). Chosen on Settings › Terminal › Clipboard & compatibility for panes opened after the
change; offered only in builds that carry the library. A native crash with a Fast pane open turns
Fast off for new panes until the app is updated. In code a `TerminalContent` behind
`TerminalSession.chooseEngine` (ADR 0026); `debug.tl.terminal_core` overrides it for testing.
_Avoid_: core (in prose), backend, Ghostty mode, renderer (drawing is the same for both)

**Titlebar**:
The translucent glass strip along the top of every floating pane, Home float and the scratchpad:
the drag handle and Minimise, Maximise and Close; Dock is in the menu a long press on the handle
opens. It carries no readings: the status bar shows them on both halves (2026-10-21). It replaces
the floating pane's move corner and pill; resizing stays at the bottom-right corner.
_Avoid_: pill (the retired control; the dictation pill is another thing), header, handle bar

**Wall**:
The one horizontal row the launcher pages along: the **Home pages**, then the **Terminal**. Swiping
past the last Home page reaches the first window, opening a fresh one when the Terminal has none;
dragging back before the first window returns to the last Home page. In code it is still `PaneWallController` and `PaneWallLayout`, and a page is a
`PaneWallPage`. See ADR 0008.
_Avoid_: pane wall (the retired name), pager, strip, carousel, home screen
_ADR 0015 (proposed): the wall is one ordered list of pages of any **page kind**, in any order, arranged in Edit pages._

**Window chip**:
The status bar's chip for one window, the display window or a **Home float**, the same chips in the
same order on both halves of the wall. #3 Strip's tiles show the same window chips at tile size.
_Avoid_: pill (the dictation control), window pill, run strip entry, float chip

**Window layer**:
One of the six named layers of the launcher's window, back to front: ground (wallpaper, live scene, the X11 surface), pages, chrome, sheets, editor and notices. The layer's number is the only Z between layers; inside a layer the order is child order, and elevation only draws shadows. Not an **open layer**, which lives in the sheets window layer.
_Avoid_: layer (on its own), z-level, overlay
_Built so far: every top-level view stands in a layer's host (ground, chrome, sheets inside the launcher's shell; editor and notices beside it), runtime views join their host through `WindowLayers`, and `bringToFront` is gone. The shell's body holds the pages and, until the stacks move, the chrome laid out against them; elevation orders nothing but the A-Z row and the edge stacks, which the row rebuild (#260) replaces; every other elevation draws a shadow (ADR 0028)._

**Terminal**:
The terminal half of the wall, right of Home: the current session's windows, then the display
windows, under the full chrome. It exists while its row has at least one window; closing the last
one returns to Home. A **Home float** is a session window that is not in the row and does not keep
the Terminal alive. A home press on it goes to Home page 1; a home press from another app comes back
to the window that was left. The drawer, the corner tab and the border drag also lead back to Home.
_Avoid_: Workspace (the name before ADR 0031), usage mode, Terminal place, terminal environment, saved workspace (that is **Saved
sessions**)

### Pages (ADR 0015, proposed)

These words come from ADR 0015 (the wall is a list of pages), still a draft on 2026-10-27. Until it
is accepted the entries above describe the app as built.

**Page kind**:
What a page is: Home, Terminal, Display or Assistant; the **side** follows from it (Home for Home, the Terminal for the rest). Each kind has its own
default **Dress**: the dock on Home pages; the status bar and extra keys, dock off, on the others.
_Avoid_: page type, place

**home page** (the one set as home):
The one page, of any kind, that a cold start and a home press inside the launcher land on, chosen
in Edit pages with Set as home. Written lower-case; a **Home page** (capitalised) is the kind. A
home press from another app returns to the page that was left first.
_Avoid_: start page, default page, Start in

**Terminal page**:
The one page (at most one) that holds the sessions: one session's row of windows side by side,
extended sideways by windows (Ctrl+Alt+C) and vertically by sessions (Ctrl+Alt+Shift+C, swapped in
place by Ctrl+Alt+Up/Down). It stays on the wall when its last window closes and starts a shell
when landed on. Named "Terminal" in the UI.
_Avoid_: Workspace, run, shell (that is what a pane runs)

**Display page**:
A page showing the one X display, either unbound (the screen as it is) or bound to a Linux app,
which landing on it raises (and launches). Landing starts the display when it is not running. Any
number; it takes no Floats and no drops.
_Avoid_: display window (the retired row window), X11 page

**Float**:
A session window floating over a Home page or a Terminal page window, with one titlebar and chip.
A floating pane is lifted into a Float pinned to its window.
_Avoid_: Home float (the narrower old word), floating pane

**Carry**:
Moving a held thing (a Float, an app icon, a folder or a widget) across pages: a second finger's
swipe or a rest at the side edge pages the wall under it. A release where it cannot drop flies it
back.
_Avoid_: drag across pages, pick up (that is the first half of a carry)

**Multi-pick**:
A carry of several items: after one is picked up, taps on more Home items fly them into the held
stack; the drop lays them out in pick order.
_Avoid_: multi-select, bulk move

**Page list**:
The wall's ordered pages and its **home page**, held by one module (`WallPages`, designed
2026-10-27). Pages have stable ids; what stands on a page is kept by its owner under that id.
_Avoid_: page order (as a store), wall model

**Stop**:
One place the wall can land: a page, or one window of the **Terminal page**. The Terminal page
gives one stop per window of the current session, or one when it has none.
_Avoid_: page index, position

**Widgets** (the surface):
The glass of the widget cards on a Home page. It holds no look of its own: it wears **Global**'s
while Glass widgets is on, and each widget only chooses Card or **Bare**.
_Avoid_: widget glass (in prose)
_ADR 0031 (accepted, not yet built): as built it is a surface with look values of its own._

**Ground**:
What is painted between the wallpaper and a page: its wallpaper dim, plus the canvas tint on the
**Terminal page** when the canvas is not glass. One per page; neighbouring grounds meet in a soft
seam that travels with the pages. The ink solver reads the ground under a rect.
_Avoid_: wall ground (the retired single colour), unified background dim

**Wallpaper dim**:
A black dim over the wallpaper behind the Terminal's pages, set on the Terminal card. It darkens
everything around the panes, the chrome's glass included; the panes' own darkness is their
Opacity. Home's pages have none.
_Avoid_: background dim, wallpaper opacity
_ADR 0031 (accepted, not yet built): as built the Home card sets a dim for Home's pages, the Terminal's pages read a wall-wide one that no card sets, and chrome glass does not show it._

**Cell**:
The stretch of the **wall** one page takes, in the order Edit pages arranged: every page has one,
whatever its kind, and the wall pages from cell to cell. A cell shows its page **live** or as a
**still**.
_Avoid_: wall slot, place window (the retired list of at most one page per kind around the current
one), slot

**Live page**:
The page its kind's one view is drawing right now: the widget grid for one Home page, the row of
windows for the Terminal page, the X display for one Display page. At most one live page per kind;
the page the wall stands on is always live once it has landed.
_Avoid_: bound page (binding is the choice, live is the state), current page (that is the page the
wall stands on, which may still be a still mid-slide)

**Still**:
What a cell shows while its page is not live: the page's last picture, else a card with the page's
name and its kind's icon. A still takes no touch and wears no chrome of its own; the **Dress** is the
page kind's whether the page is live or still. Landing on a still binds the kind's view to that page
and the still gives way to the view.
_Avoid_: stand-in (that is the surface's copy while a live display moves), placeholder, snapshot

**Binding**:
Which page of a kind the kind's one view draws. A landing re-binds; a far jump by name re-binds to
the page landed on and never to the pages passed over.
_Avoid_: bound run, bound slot

**Page override**:
A look value one page holds instead of the wall's: a **Cell** in the page's row of the look grid
(`look.<property>@<page id>`, or `look.<property>.<element>@<page id>` for one element). Reset at
that cell deletes it (ADR 0017).
_Avoid_: half value, per-page setting
_ADR 0031 (accepted, not yet built): goes; no look value is held for a page or a kind of page._

**Page transition**:
How pages move past each other on the wall: Slide, Depth, Fade, Carousel or Stack, one setting for
every page move (Home ⇄ Home, a **Crossing**, window ⇄ window). A live Display page always slides.
_Avoid_: page effect, swipe animation

**Own value**:
A look value a surface holds for itself in place of **Global**'s: the Opacity or the Blur of the
status bar, the dock or the keyboard. A surface without one **follows** Global. Nothing set on
Global clears it; the **reset arrow** does.
_Avoid_: cell (the look grid's earlier word; a **Cell** is a page's stretch of the wall), override,
detached value
_ADR 0031 (accepted, not yet built): as built a value is a cell of the look grid, keyed `look.<property>[.<element>][@kind:<kind>]`, read element on the kind, element everywhere, all glass on the kind, then Global; every element has a cell for every property._

**Scope chip**:
The two chips at the top of every Appearance card: **Where it applies** (Everywhere | Home pages,
Terminal or Display pages, the kind of the page in front | <page name>) and **What it applies to**
(All glass | <element>). Together they name the **Cell** the card's
rows read and write. Before ADR 0017 there was one chip, **Global | <page name>**, on four cards.
_Avoid_: Global | <page name> as the scope chip's label (the old chip). **Global** is the Appearance
editor's card for the look every surface wears (developer, 2026-11-03).
_ADR 0031 (accepted, not yet built): goes, with the Applies to row that replaced it; no card chooses a page._

### Look and motion

**Adaptive legibility**:
The switch beside Contrast on the Global and Terminal cards (one stored value, off by default).
Off, the wallpaper's lightness range and ink are measured once from the whole still picture and
every glass item (panes, Home floats, widgets, dock, keyboard, status bar, strip) takes its
correction and ink tone from that one measurement, so moving an item changes nothing about it.
On, each item measures the wallpaper behind itself (ADR 0014, amendment of 2026-11-20;
`LegibilityScope`).
_Avoid_: even-out (in user-facing text), per-item contrast

**Backdrop layers**:
A Live glass surface's backdrop, at rest and while the pages move alike, as a short stack of
pictures that each refresh on their own trigger: the **ground** (the stored pre-blurred wallpaper
frame at the parallax offset, or a live scene's frame at its glass rate), the **pages** (while the
pages move, the pages under the dock or a pinned float, captured blurred as the swipe starts and
moved by their travel; at rest they are in the local layer) and the **local** layer (everything
else beneath the surface, drawn from the views themselves and blurred). The glass composites them
in its one draw, so the first, every and the landed frame of a swipe are one composition and a
landing has nothing to swap (ADR 0029 §3, `BackdropLayers`).
_Avoid_: frozen glass, page snapshot, freeze frame

**Ambient effect**:
A built-in shader effect that animates continuously rather than in answer to something. It is off
unless chosen, is labelled as a battery cost, and stops drawing under **Lazy mode**, when its page or
pane is not visible, and after about ten seconds without input, resuming on the next touch.
_Avoid_: animated background, live wallpaper (removed), animation

**Global**:
The look every glass surface wears until it holds an **own value**: one value for each look
property, set on the Global card. A surface **follows** Global where it holds none. Settings rows,
presets, the wallpaper policy and launcherctl write Global unless they name a surface.
_Avoid_: Base (the earlier word, renamed in ADR 0031), default (the shipped value is the default; Global is what the
user set), inherited and detached (the old flag words)

**Backstop**:
The time or frame count after which a **Hold** releases on its own because its signal has not
come. A backstop is a fail-safe, not a schedule: the design is that the signal arrives first, and
every backstop that releases a hold is written to the motion log by name so it can be counted.
_Avoid_: timeout (a timeout is an error; a backstop keeps the screen alive), fallback delay
Every Hold's release is counted by name and reason in the counter table, and a backstop release also leaves a ring line. _ADR 0028: an unexpected backstop counts against the release bar; the waits that are not yet Holds (the keyboard's gates, the apps row's settles) join as their steps land._


**Event effect**:
A built-in shader effect that draws only in answer to something happening (a keystroke, a cursor
move, the bell, a pane taking focus) and stops when its motion ends, so it costs nothing at rest.
Every effect is an event effect unless it is an **Ambient effect**. Effects are built in; there are
no user-supplied shaders.
_Avoid_: animation, custom shader (users cannot load their own)

**Fancier Glass**:
The extra glass: refraction at glass edges on every glass surface and the
tilting page motions. It has no switch any more (it left the old Settings › Look page with the Looks): it is on
wherever the device supports it — Android 13 and later, with a managed wallpaper — and each Look
sets how deep the bend is. Lazy mode, battery saver and reduced motion override it. See ADR 0005
and `project-docs/active/fancier-glass/SPEC.md`.
_Avoid_: glass mode, refraction mode

**Frost** (the slider):
The glass strip's first slider on every card with glass (and All surfaces' on the Appearance
card), clear to frosted: it moves a surface's blur and grain together along
one curve (`surfaces/GlassControls`; the code still calls it Glass, `GLASS`, `surface_glass_position`).
Since ADR 0017 it snaps to eight **Frost stops** (0, 10, 20, 40, 55, 75, 90, 100 on the old
scale) so that every distinct blur radius in use fits the shared-frame cache; Solid sits at 0,
Clear at 20, Tint at 40, Mist at 75. Storage keeps the stop, and a **Grain** set on its own
under More holds while Frost moves blur alone. Not the frost layer itself (the blurred picture a
surface draws).
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
A **Global** slider, 80% to 150%: one size for the type the launcher draws itself,
the terminal font, the keyboard's key legends and the chrome labels (status bar figures, window
chips, the dock strip's titles, Home's app labels). Each is drawn at its own size times it, so a
surface's own size stays an offset: pane zoom and the terminal font setting, Home's label size.
100% is every install's sizes as they were (`text_size_percent`, unset until moved).
_Avoid_: font scale (Android's own, which still applies on top), zoom (the terminal's pinch)
_ADR 0031 (accepted, not yet built): as built the slider sits on the Terminal card under the name Font size._

**Frame pipeline**:
The window's one frame order: inputs only record that they have work and ask for a frame, and the pipeline then runs the **frame steps** in a fixed order, only on frames something asked for.
_Avoid_: frame loop, draw hooks

**Frame step**:
One entry in the frame pipeline's fixed table, such as the page's front work, the chrome geometry pass or the glass commit; new code that touches a view a **scene** owns joins as a step instead of hooking a frame of its own.
_Avoid_: callback, listener

**Gate**:
A **Hold** that keeps a window from drawing until the first frame is right: the keyboard reveal
gate, the first-draw gate. A gate's **Backstop** is short, because a gate that stays shut is a
black home screen.
_Avoid_: block, freeze
The window's first frame after a relaunch is a gate on the frame pipeline: the relaunch reveal holds it for the surface it carried, with frame-count and time backstops. The first-draw gate waits through the **First-layout helper**. _ADR 0028 (partly built): the keyboard reveal gate and close correction are deleted in step 3, after which nothing else cancels a draw._

**Glass service**:
The one registry every glass surface joins. An entry names its element (the look cells it reads), the view it draws in, the kind it wants and a budget priority (dock, then floats, then widgets); the service owns the granted kind, the layers, refresh, commit order and release. There are four kinds: **Wallpaper** (the stored pre-blurred frame), **Snapshot**, **Live**, and **Compositor** (the keyboard's frost over the X11 screen on the display window, falling back to Wallpaper). A surface keeps one picture for its life, and its kind changes only on a policy event (the Live blur switch, battery saver, a memory trim, a slot freeing).
_Avoid_: backdrop service (the 0021 name), blur manager
_ADR 0029 (accepted, partly built): the registry, its four kinds, the Live core and the commit as the frame table's last step are built (`com.termux.app.glass`), and the commit walks the entries back to front by window layer. The dock, floats, widgets, pane slabs and the tab, the panels (drawer, palette, sheet, menus) and the keyboard's Compositor frost have joined: panels pay from the same eight Live slots, a slab is aimed in the commit and nobody pushes it, and the Compositor falls back to Wallpaper on a policy event or a failure. The bars and the foot join in later steps._

**Page glass declaration**:
How a **page** hands the **glass service** its own glass surfaces (panes, widgets, floats): the page lists them and the service joins them, so the page never registers glass itself.
_Avoid_: page glass consumer, glass hook

**Legibility correction**:
What a band's legends have to stand on at a page's rest: the opaque face under each row of the dock or the keyboard, and the legend tone chosen on the keyboard's bare face. The glass service keeps one per **page** and band (dock, keyboard, strip, panes) in its legibility store, which is what the tint sampler became: measured when the page rests and measured again only when the wallpaper, the palette, light or dark, the band's rest rectangle or its look changes. Keying on the page covers parallax. A crossing reads the two ends' stored corrections and blends the ink between pale and dark on the glass's own curve; it measures nothing, and a page never visited is measured once, at rest, before its first crossing.
_Avoid_: tint sample, even-out (that is the stage that narrows the wallpaper's range, not the stored value).

_ADR 0029 section 9 (built for the dock and the keyboard): the dock's legends (extra keys, tiles, prompt line) and the keyboard's palette drift between the two pages' kept inks on a look-only crossing. The strip and panes bands have no crossing consumer: the A–Z letters belong to Home's rail and the panes travel with their page._

**Guard**:
One of three checks every layer's writer runs while the motion log is on: a **foreign writer** (a property the writer owns was changed by someone else), **landing wrote** (the landing frame changed something) and **crossing laid out** (a layout request from a pose writer, or one a pre-draw step made late in the frame, which draws the new look on the old layout). A guard logs and counts in the **Counter table** and never crashes.
_Avoid_: assertion, invariant check (an invariant is a motion-log rule over events; a guard watches a writer)

**Hold**:
A wait for one signal before something is let go: a layout the pass booked, a view's first draw,
a surface's first frame after a swap, the settle of a return. A hold is named, is released once
by the signal it waits for or by its **Backstop**, and says which of the two released it. A hold
that is superseded by a newer one is a no-op when it fires.
_Avoid_: wait, timer, delay, debounce (a debounce coalesces repeats; it waits for nothing)
A hold is the `window.Hold` type: a name, a backstop, and a release that logs signal, landing or backstop and counts under `hold:<name>:<reason>`. The waiting element stays at its before-pose and the rest of the window keeps drawing. The X11 surface's cover, swap and size settling and the window-foreground reply are Holds today. _ADR 0028 (partly built): the hold as state in the frame's snapshot, and the wallpaper colours callback as a Hold, wait for the steps that build the snapshot and the editor's draft._

**Counter table**:
The tally of every hold release, guard hit and invariant, one line per name with its count, first and last time and last detail. It lives outside the motion log's ring, so it survives the ring rolling over; `launcherctl motion-log dump` ends with it after a `--- counters ---` line and `motion-log clear` resets it. It counts only while the motion log is on. The 2.0 release bar reads it: every guard and an unexpected backstop at zero after a day of use.
_Avoid_: stats, metrics

**First-layout helper**:
The one shared way for a sheet, popup, widget or gate to wait for its own first layout or draw (`window.FirstLayout`): it runs a callback in the next pre-draw pass (or after the next draw or layout), never cancels a draw and lets go of its listener. A wait on something delivered late is a **Hold** instead.
_Avoid_: pre-draw listener (the helper is the only code besides the frame pipeline that registers one)


**Lazy mode**:
The Battery setting that stops the launcher animating while nobody is watching: the clock swaps its
digits instead of folding them, a working window's rim holds lit, and the status readings are sampled
less often. It is meant to become the default. See `docs/en/Launcher_Settings.md`.
_Avoid_: battery saver (Android's own), low-power mode

**Look**:
The glass every surface wears, set on the Global card. Its slider has five
stops: Clear, Mist, Tint, Solid and Custom. A surface's own slider moves the Look to Custom by
itself; Custom is never a gate. One Look covers every page and both orientations (ADR 0031).
_Avoid_: theme (the light, dark and black scheme is separate), preset, skin
_ADR 0031 (accepted, not yet built): as built Home and the Terminal each hold a Look of their own (ADR 0013)._

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
slides between Home and the Terminal. Its rule is **tap the thing**: tap a surface (Home, Status
bar, Terminal, Dock, Keyboard) and its **card** rises with everything about it, ordered by how
often it is touched, rare controls under **More**; drag a surface to move it; tap the **pill** for
what has no surface (the Look slider, wallpaper, theme, icon pack, text size, full screen, minimal,
orientation, Undo, Done; that bar is the **editor bar**, never the Pill, which is the dictation
card). A moved Opacity or Blur slider on a surface's card is that surface's **own value**, marked
by the **reset arrow**. Every edit goes into one **draft** shown over the saved values: Undo and
Discard drop the draft, Done saves it once, and a look set from outside while the editor is open
(`launcherctl look`) shows at once and survives both. Every door to it is named Appearance and
opens on that door's object.
_Avoid_: surface editor, look editor, Look page, Layout page (the 2026-10-24 merge removed the pages), style editor
_ADR 0031 (accepted, not yet built): as built the editor edits the half the frame shows, marks an own value with a coloured dot and resets it from the card's menu._

**Reset arrow**:
The mark of an **own value** and the way back to **Global**: at a row's end it resets that value,
in a surface card's header that surface, and in the Global card's header every surface, after
asking once.
_Avoid_: source dot (the earlier coloured mark), Reset this row
_ADR 0031 (accepted, not yet built)._

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
An opt-in layout of Home's own, for a user who wants Home laid out unlike the Terminal. Off by
default, when Home wears the one shared layout. While on, Home holds its own copy of which rows show
(the apps row, the A–Z row, the status bar), their order, edge and under-keyboard placement, the
**Dock design**, and the Search field and Drawer tile toggles (with the tile's position). The sizes
stay **linked** to the shared layout either way: the apps row's height, the #3 Strip row's height,
the keyboard's height and chin, and **Style**, Corners and Margin are one value on both halves,
edited from either, so nothing resizes on the swipe (decided 2026-10-21, #84). The keyboard's form
stays Home's own. It is the Layout editor's "Layout: One layout | Home's own" line; the row's house
segment previews Home. It is to the layout what a surface's **own value** is to **Global**.
_Avoid_: Home variant, Home place

**Layout editor**:
Legacy name for the Appearance editor's move and size controls (2026-10-24: there is one editor,
see **Appearance editor**). Its shrunken Layout mode, the **layout canvas** with its drags and
handles, was retired on 2026-10-26 (editor-redesign §1): a bar moves by its card's **Position**
row, sizes are rows on the cards, a hidden bar waits in the stack beside the frame, and the
editor edits the orientation the phone is in (turn the phone for the other). The session
underneath keeps the name in code (`LayoutEditorController`, `LayoutEditorPlan`).
_Avoid_: Layout page, Layout tab, Layout mode, Layout (as the name of a door; the door is Appearance)

**Layout canvas**:
Retired 2026-10-26 with Layout mode: the scaled model of a page's layout (`LayoutCanvasView`,
once called the miniature) that elements were dragged around on. Nothing draws it now.
_Avoid_: miniature, preview, thumbnail (and not overview: **Overview** is the wall's screen)

**Restore tray**:
Retired with the layout canvas. Hidden bars now wait as cards in the stack beside the editor's
frame: a tap opens the bar's card, a drag onto the frame brings it back.
_Avoid_: Hidden box, shelf

### Widgets

The launcher's own Home widgets after the redesign (ADR 0010, decided 2026-10-21, research
`project-docs/active/widget-redesign-research.md`). None of it is built yet: until it is, the code
and the picker still have the twelve older kinds, whose names are kept under _Avoid_.

**Bare**:
A widget drawn without its card: its ink sits straight on the wallpaper and nothing is drawn behind
it (decided 2026-10-27; it used to lay a soft dim where the ink would not read). Only the ink adapts:
its tone moves, or it flips to the other side, and on a wallpaper where neither reads the text may
fall short. It is an opt-in per widget, offered by the Clock, **Glance** and Weather. The **Look**'s
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
The **Look**'s one switch for whether the launcher's widgets wear its glass, following **Global**. A
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

**Default model**:
The model On-device AI answers with when a program, or the Assistant page, names none, chosen in the
launcher's AI settings. In code it is the `tai_role_default_assistant` role (ADR 0027).
_Avoid_: default assistant model (its old name), current model, loaded model (a model can be the
default without being loaded)

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

**Feature request**:
The one way a Feature asks for an answer: it names the Feature, the messages and where the answer
goes, and gets back events (Waiting, Loading, Text, Done, Error) and a handle that cancels that
request alone. It follows the Feature load plan, chooses on-device or remote and loads the model.
Launcherctl's chat routes, the Assistant page and Cleanup all go through it (ADR 0027).
_Avoid_: chat call, request pipeline, transport

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

**Model availability**:
Which models are on this phone and, for a Feature, which model serves it and that model's state:
missing, downloading, on the phone, loading, loaded, remote ready, remote not set up. Every screen
that shows a model's state reads it here (ADR 0027).
_Avoid_: model presence (presence means loaded in the runtime), installed models, model status

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

### Settings

The words for the launcher's own settings and the one list of them (spec #311). Nothing here is
built yet; these are the names the build uses.

**Settings registry**:
The one list of the launcher's settings. Settings rows, the Appearance editor, `launcherctl config`,
backup restore, resets and the Assistant all read and save a setting through it. A save returns a
Change (old and new value) or a Refusal (the reason and the allowed values). Storage does not move:
it reads and writes the stores and keys the settings already use. Its index (lookup by id, titles,
complaint phrases) builds only when something asks for it, never at start-up.
_Avoid_: settings service, config (the file and the command are faces of it, not the source)

**Setting declaration**:
One setting written down once: its dotted id (`terminal.contrast`), kind and range or choices,
default, store and key, refresh, reset group, the complaints it answers, whether the Assistant may
offer it and whether it is exported. A declaration is a constant, so the store's getters take their
range from it and each range is written once. The look grid, the layout documents and the font
sizes (whose key follows the display and whose range follows the screen's density) are reached
through adapters instead of one declaration per value.
_Avoid_: entry, setting definition

**Settings applier**:
Carries out the refresh a setting declaration names after a save: at once while the launcher is on
screen, otherwise when it next comes back. A declaration whose key a preference listener already
refreshes says so, and the applier adds nothing.
_Avoid_: styling request (one of the refreshes it can run), refresh manager

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
allowlist guards against accidents, not against code already running as that uid. See
`docs/agents/privileged-lane.md`.
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
