---
status: proposed
date: 2026-10-27
supersedes: 0008's two halves (Home pages, then the Workspace's row) and its Start in; 0013 (each half wears its own Look)
amends: 0009 (one chrome, now dressed per page kind), 0012 (a crossing is any slide between two neighbouring pages)
---

# The wall is a list of pages

ADR 0008 split the wall into two halves, Home and the Workspace, and every later decision took
the halves for granted: 0009 dressed them with one chrome and an opt-in Home layout, 0012 gave the
slide between them one owner, 0013 let each half hold its own Look. Edit pages already lists Home
pages, terminal windows and the display side by side, but underneath it the halves still decide
where things can go: Home pages always come first, the Workspace's row always follows, the
display is a window inside that row, and Start in is a setting of its own beside the default Home
page. Users of ordinary Android launchers expect to arrange pages, add pages of different kinds
and pick one as home. The two-halves model also leaks in the code: a getter without a half answers
the Workspace, so Home's drawer and corner tab wear the Workspace dock's glass, and one ground
painted behind the whole wall carries the terminal's tint onto Home.

We decided that the wall is one ordered list of **pages**, each of a **page kind**, arranged in
Edit pages in any order, one of them the **home page**. The developer's decisions of 2026-10-27,
taken in one grilling round, follow.

## 1. Three page kinds

- **Home page**: a grid of widgets and icons, as today. Any number.
- **Terminal page** (renamed from the Workspace): at most one. It shows one session's row of
  windows side by side; windows extend it sideways (Ctrl+Alt+C, the chip bar's +, a
  `Terminal=true` Linux app all insert a window after the current one) and sessions extend it
  vertically (Ctrl+Alt+Shift+C makes a session, Ctrl+Alt+Up/Down swaps the row in place, as
  today). Edit pages shows it as one card and moves it as one. A window closes when its last pane
  exits. With no windows the page stays and starts a shell when the wall lands on it (0008's
  2026-10-19 amendment, kept). After a cold start every window comes back in place with its name,
  its split tree and each pane's working directory, with fresh shells. Removing the page with
  windows open asks first ("Close N windows and remove Terminal?", Save sessions… offered), then
  closes them.
- **Display page**: any number, all showing the one X display. A Display page is either
  **unbound** (the screen as it is) or **bound** to a Linux app: landing on a bound page raises and
  maximises its app through the window manager, launching it if it is not running. Landing (a
  committed release, not a peek) on any Display page starts the display if it is not running;
  until then the page shows a still card with the bound app's icon and name, or "Display". Leaving
  the page leaves the display running. A Linux app tapped in the drawer goes to the page bound to
  it, else runs on the first unbound Display page, else gets a new unbound Display page inserted
  after the current one. The display window stops being a window of the terminal row (reverses
  0008's 2026-10-19 amendment "the display window is an ordinary window of the row").

Pages have names: Home pages are "Home 1…n" by position, the Terminal page is "Terminal",
a Display page carries its bound app's name or "Display". Edit pages renames them.

## 2. Edit pages and the home page

Add offers Home, Terminal (only while there is none) and Display, which opens a short picker:
"Whole display" first, then the Linux apps. A new page lands after the selected one. A long press
on a page card offers Set as home, Rename, Bind app (Display pages) and Remove.

Exactly one page is the **home page**, of any kind. A cold start lands on it, and a home press
inside the launcher goes to it. A home press from another app returns to the page that was left
(the window, on the Terminal page), and a second press goes to the home page. Removing the home
page makes the first page home. **Start in** retires into the home page. **Use as** retires as a
setting: the welcome sheet's choice only seeds the wall ([Home 1][Terminal] with Home 1 as home, or
[Terminal] as home with Home's settings hidden until a Home page is added).

## 3. One chrome, dressed per page kind (amends 0009)

There is still one status bar, one dock and one keyboard, standing still while the wall moves.
Each page kind has its own default **dress**: the dock is designed for Home pages and shown there;
Terminal and Display pages show the status bar and the extra keys, with the dock off. Each element
can be shown or hidden per kind, and any page may opt into a layout of its own, generalising
0009's Home layout. Sizes stay linked across every page so nothing resizes on a swipe (0009's
amendment, kept). The extra keys become an element of their own rather than part of the dock.
Minimal mode covers the Terminal page and every Display page; Home pages ignore it.

The keyboard remembers open or closed per page kind: Home pages share the transient palette and
keyboard (or Always on), the Terminal page remembers its own, and Display pages share the display's
keyboard memory and Keyboard mode. Window chips are the Terminal page's windows (the current
session's), one chip per Display page, and the Floats; Home pages have none.

## 4. A crossing is any slide between neighbouring pages (amends 0012)

The crossing module owns every slide between two adjacent pages, holds both pages' resolved
arrangements and morphs the dress and look from the leaving page to the arriving one. Between two
pages that wear the same dress nothing morphs. A session swap on the Terminal page is not a
crossing; the row is replaced in place.

## 5. Floats and carrying

There is one **Float**: a session window floating over a Home page or over a window of the
Terminal page, with one titlebar, glass and chip. Making a pane float lifts it out of its window's
split tree into a Float pinned to that window; Dock sends a Float into the row as a window, and
Return to split puts it back in its old tree. Display pages take no Floats.

A **carry** moves something held across pages: hold a Float's titlebar, an app icon, a folder or a
widget, and page the wall with a second finger's swipe or by resting the held thing at a side edge
for about half a second (repeating while held). Floats drop on Home pages and Terminal windows;
icons and folders on Home pages, the dock and folders; widgets on Home pages. A release over a
page that takes nothing (a Display page) flies the thing back to where it came from.
**Multi-pick**: a drag that picks up an icon, folder or widget starts a stack; tapping more items
on Home pages or the dock flies them into it, tapping a stacked one sends it back, and the drop lays
them out from the drop cell in pick order. Detailed behaviour belongs to a spec.

## 6. Looks: Base, then the page (supersedes 0013)

There are two layers. **Base** is wall-wide; each page follows it or detaches individual values.
The Terminal page counts as one page for all its windows. A page may detach its Look stop, the
opacity, tint, blur and grain of the chrome as it shows them, and its own surface: the canvas on
the Terminal page, Home's own look and the new **Widgets** surface on a Home page. Shapes (corners,
margin, Style, sizes) stay wall-wide (0007, 0011), pending the architecture review of how the
dock's shape controls are wired to the Workspace.

The Appearance editor writes to Base by default. Each card carries a scope chip, **Global |
<page name>**; choosing the page detaches its values (seeded from Base) and later edits go there.
Reset returns a page to Base. 0013's "edit what you see" and its copy-to-the-other-half action
retire with the halves.

Each page paints its own ground from its own resolved Look; the Terminal page's canvas tint stays
on the Terminal page. Widgets follow their page's Widgets surface; per widget there is only the
form, Card or Bare. Bare's "dimmed only where it would not read" becomes part of the legibility
contract (ADR 0014's even-out, wave 2c) instead of a dim in view code.

## 7. Upgrading

One-way, as 0008's migration was. The wall becomes [Home 1…n][Terminal], plus one unbound Display
page after Terminal when the display window was in the row or Start in was Display. The home page
is Start in's: Home → the default Home page, Workspace → Terminal, Display → that Display page.
Terminal-only users get [Terminal] (and the Display page when used). Sessions, windows and their
order carry over. Look values that were equal on both halves become Base with nothing detached;
where they differed, Home's value becomes Base and the Terminal page (and a migrated Display page)
detach the Workspace's. The Widgets surface starts from Base. Users keep what they see: the dock
stays on the Terminal page for upgraders (new installs get it off), a Home layout becomes the Home
pages' dress, and the shared layout becomes the Terminal and Display pages'. Floating panes are
lifted once into Floats pinned to their window; Home floats keep their page.

## Considered options

- **Terminal windows as separate pages tagged by session**, all sessions on the wall at once:
  rejected, it loses the developer's rows-per-session model.
- **Several Terminal pages**, each pinned to a session: rejected for now; one page with swappable
  rows is enough.
- **A per-kind look layer** (Base → kind → page): rejected; the Terminal page is one page, so
  "Home light, terminal dark" is already one detach.
- **Edits always write to the page shown** (0013's rule): rejected, a new user with several Home
  pages would see only one change.
- **Keeping Start in and Use as as settings**: rejected; the home page and Edit pages say the same.

## Consequences

- `WallHalf` and every getter pair with and without a half go; a look value is asked for a page.
  The getters that silently answer the Workspace, and so dress Home's drawer and corner tab in the
  Workspace dock's glass, cannot survive this.
- Page storage gains a list of pages (kind, name, binding, look overrides, dress) beside Home's
  widget repository, designed as `WallPages` with stable page ids in
  `project-docs/active/page-list-2026-10-27.md`; the Terminal page's windows need a persistent store for the cold-start
  restore, which today lives only in the activity's saved state.
- The crossing module's two-half snapshot becomes a pair of neighbouring pages.
- ADR 0014 wave 2c (even-out for the keyboard, dock, status bar and widgets) waits for this model
  and then applies per page; Bare's dim is part of it.
- The docs and Settings strings that say Workspace are renamed to Terminal.
