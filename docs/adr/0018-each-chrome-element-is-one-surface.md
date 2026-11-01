---
status: proposed
date: 2026-10-31
amends: 0017 (a page-kind tier joins the grid; shipped looks differ per page kind), 0012 (a crossing lays out nothing: it moves between two held rests), 0009 (the chrome's glass is one surface per element), 0016 §2 (the stack picture is a capture of the element's own view), 0004 (every sheet samples the shared frame; no element blurs the live window)
supersedes: the Floating rule in `project-docs/active/home-dock-styles-2026-10-20.md` ("there is no foot: the strip under the pill is not shown under Floating")
---

# Each chrome element is one surface, owned by one module

On 2026-10-31 a day of device checks on pong found, one after another: the dock's Opacity row
seemed dead (it was not; the Peek card's tint nearly matches a dark wallpaper, and three fixers
read code for a day because the dock's look is resolved in four places); `launcherctl look`
wrote the grid and nothing redrew (no owner of "a look changed"); the dock's glass stopped on a
hard line above the gesture pill (the card is drawn in the content tree and the strip under the
pill by a second surface added to the window's decor, with its own blur capture, its own tint,
its own rim rule and its own refresh ledger); a Linux app opened on the display with no window
chip and a page the overview could not show; the keyboard that comes up with the command palette
on Home was fully transparent over the widgets (the keyboard reads one look for every screen, and
the old Home value had become the everywhere value); and the Home → Terminal swipe swapped the
dock for the Terminal's stack on its first frame.

The map behind those (`project-docs/active/chrome-elements-2026-10-31.md`, §1) shows the same
shape for every element. The dock: a pure `dock/` package of eight policy classes, three views,
and twenty-two methods still in `TermuxActivity`, with a second surface in the decor. The status
bar: eleven policy classes, a wallpaper twin in the layout, and about thirty activity methods. The
keyboard: a vendored module, a choreographer, two policies, and about two hundred activity methods
that mention it. The command palette: a controller, a pure overlay policy, and twenty activity
methods, with a blur that captures the live window while every other glass samples the shared
pre-blurred frame, and a surface colour taken from the dock. The strip row: a view in `dock/`, its
height measured by the activity and reserved by `StackLayout`. `TermuxActivity` is 35,000 lines.
Policy is pure and tested where it was extracted, and the joints between extractions are where
the day's bugs sat.

We decided three things.

## 1. One module per element, one surface each

The chrome is five elements: the **status bar**, the **dock** (with its foot under the gesture
pill), the **strip row** (the extra-keys row), the **keyboard**, and the **sheets** (the command
palette, the app drawer, folders, anchored menus, notices, the Home float). Each is one module
with the same four parts:

- a pure **Spec**: geometry (position, size, insets, corners, seams), look (which look-grid cells
  it reads and the resolved glass), and motion answers (what it does on a crossing, a reveal, a
  drag), built from inputs the host passes and tested without Android;
- one **View** hierarchy that applies the Spec and draws nothing the Spec did not say; the
  element's whole surface is in that hierarchy, including any part that reaches under a system
  inset. There is no second surface: no decor-level overlay, no wallpaper twin, no copy whose job
  is to stay in step with the first;
- a **Host** interface naming what the element needs from outside (insets, the page in front,
  the wallpaper frame, a place to post a redraw) and what it reports back;
- a **Controller** that owns the element's events and holds its state across configuration
  changes.

`TermuxActivity` implements the hosts and nothing else of an element. The rule that already
governs the terminal and the editor ("extend the seams, not `TermuxActivity`") now governs the
chrome.

The only copy of an element that may exist is the **stack picture** a crossing takes (ADR 0016
§2), and it is a capture of the element's own view, never a drawing of its own.

The dock's foot is the dock. The dock view extends under the navigation inset; the foot is the
same surface with the same blur, tint and program, and the card's bottom edge is a seam, not a
rim, whenever the foot continues it. This holds for Docked and for Peek under Floating; the
Floating rule that there is no foot is withdrawn, because the code never honoured it and the
developer chose one glass to the screen's bottom (2026-10-31).

## 2. One path from a look to a surface

Every element resolves its look through one function, `ElementGlass.of(element, page context)`,
and the page context comes from one place, `SeamDress.lookPage`. No element reads a preference
getter for a look value. A look write, from the editor, from Settings, from `launcherctl` or from
a migration, reaches every element through one redress request; there is no second notification
path and no reader that caches a resolved look past the request.

Every sheet samples the shared pre-blurred wallpaper frame at the Sheets element's frost, as the
dock and the keyboard do. The command palette and the app drawer stop capturing the live window.
What lies under a sheet is no longer seen through it, only the wallpaper; the sheet's tint,
opacity, corners and rim come from the Sheets cells.

## 3. A page-kind tier in the look grid

ADR 0017's grid answers "everywhere" and "this page". Each kind of page carries a different
focus: on a Home page the widgets are the content and the keyboard must stand off them; on a
Display page the X11 screen is the content and the keyboard should barely cover it; on the
Terminal page the text is the content. A kind-wide answer belongs between the page and the wall.

The keys gain a kind suffix: `look.<property>[.<element>][@<page>|@kind:<home|terminal|display>]`.
The read order becomes element@page → all@page → element@kind → all@kind → element → Base →
the shipped look **for that kind**. Presence is still the override; absence still follows. A new
page of a kind starts with no cells and so wears its kind's look. A Display page with no bound
window reads the Display kind, never the Terminal page's cells.

The shipped looks differ per kind where the focus differs: the keyboard on Home pages ships
lifted (Mist: frost stop 5, opacity 60, grain on) and on Display pages ships Clear (opacity 10,
frost 0); on the Terminal page the keyboard ships as today. Every other element ships one look
for every kind until a kind-specific one is decided. The editor's Where chip offers Everywhere,
the page's kind (Home pages, Terminal, Display pages) and This page.

Upgrading installs that already hold cells the 2026-10-30 fold built from Home-only keys get a
one-way step that moves those element cells into the Home kind, so the everywhere cell is free
again and the kind defaults apply on the other kinds; a value the user set since stays where it
is.

## 4. One rest per page kind

Each page kind owns a rest: the terminal's grid, the keyboard's shown state and form, the dock's
rows and design, the strip row, the status bar's state. A rest changes only by an act on that
page, never by a page change. A crossing animates between two rests with pictures and
translations and lays out nothing; the landing finds every element at its rest. The terminal's
PTY size follows the Terminal kind's rest alone, so the motion invariant becomes no resize per
crossing (amends `project-docs/active/motion-invariants-2026-10-30.md`). The dock over the
keyboard on a Home page is Home's dock, in Home's design, for every combination of style, design,
keyboard form and orientation (the developer, 2026-10-31). A Home page's own layout never carries
a dock of its own: the dock is the Home kind's rest, read and written there from every Home page
(the developer, 2026-11-01).

## Consequences

- Five modules under their own packages, each with a Spec test suite; `TermuxActivity` loses the
  element methods (about 300 declarations by the map's count) and keeps the five hosts.
- The decor nav-bar overlay, the status bar's wallpaper twin and the palette's live blur are
  deleted. Memory at rest drops by the overlay's blur bitmap and the palette's capture buffer;
  the per-frame cost of a live window capture while the palette is open goes with it.
- The look grid store gains the kind suffix and a migration flag `look_grid_kind_migrated`; the
  editor gains one chip option per kind; `launcherctl look` gains `--kind`.
- A sheet no longer shows the content under it. Anyone who relied on reading terminal text
  through the palette loses that; the trade is one blur source for every glass.
- The spec for this round, its waves and its device cadence are in
  `project-docs/active/chrome-elements-2026-10-31.md`. Each wave lands on pong before the next.
