---
status: accepted
date: 2026-11-17
amends: 0023 §2 (what only one side has belongs to that side, Home or the Workspace, not to each page; rows ride the keyboard's top across a crossing; Floating follows the same rule)
---

# Pages, sides and the bottom stack

Decided on the map [Map: one writer and one frame order for the launcher's window](https://github.com/PickleHik3/termux-launcher/issues/236), in [The page contract](https://github.com/PickleHik3/termux-launcher/issues/243), [Who owns the rows that only one page has in a crossing](https://github.com/PickleHik3/termux-launcher/issues/238), [Who owns the keyboard coming up and going down](https://github.com/PickleHik3/termux-launcher/issues/244), [How SuggestionBarView splits between Home's apps row and the stack's writer](https://github.com/PickleHik3/termux-launcher/issues/248) and [Order of work, checks and drops before 2.0 stable](https://github.com/PickleHik3/termux-launcher/issues/247). It runs on the frame order of [ADR 0028](0028-one-frame-pipeline-a-view-property-has-one-writer.md).

ADR 0023 §2 said what only one page has travels with that page. It was accepted on 2026-11-08 and
never built. What was built meanwhile was a second row host, a hidden copy dressed twice and
promoted at the landing, which is the option 0023 rejected. On pong a Terminal to Home landing still
swaps the dock slab's picture, rows fall and rise in place, and with a floating terminal open on
Home the keyboard snaps up in one frame after a Home 2 to Terminal landing, with the terminal
resized twice. The keyboard's numbers had five owners, there was no page interface (six enums named
page kinds, and every wall event went to the activity), and a side owning rows had no name.

We decided the following.

## 1. One page contract

Every stop on the wall implements it: each Home page and each window (terminal, display, Assistant).
One `PageKind` (Home, Terminal, Display, Assistant) replaces the six kind sets and their mapping
helpers. ADR 0015 stays proposed. A page has:

- a kind and a **side**;
- its view, laid out once in its wall cell;
- a **rest**: a pure answer to what it asks of the chrome (keyboard up or down, the extra-keys target,
  the system IME, what the bars show, its look kind), tested without Android;
- on-front and off-front work, run once at the structure point;
- Back;
- a declaration of its own glass entries (panes, widgets, floats).

Pose is not a member. The wall's writer writes every page's slide and transition from the scene, so
no page moves itself, and the wall's hold-still becomes a Hold. Which page is in front and how far
the seam has travelled are snapshot state; one-time work (focus, the system IME, resuming a session)
runs when the front page changes, called by the pipeline and never from a wall callback. The
activity's wall host goes.

## 2. Sides and ownership

A **side** is Home or the Workspace. Terminal, display and Assistant windows are Workspace pages.

| Owner | Owns |
|---|---|
| page | Home page: widget grid, glyph row. Terminal window: its panes. Display window: the X11 screen. Assistant: its chat |
| side | Home: apps row, A–Z row, search field, Home's own extra-keys bar. Workspace: extra keys, minimal-layout tab, the window chips |
| shared chrome | status bar, the stack's slab and the keyboard, the Home pill, the page switch panel (positioned from the scene) |

Each side owns its own extra-keys bar. When both show one, both travel; there is no shared bar. The
page switcher key's cap shows its own side's page, so a landing changes no cap. Floats and widgets
are content of the Home page they sit on.

## 3. Back is one chain

The host owns it: first anything raised over the page (a sheet, a menu, the Appearance editor, the
switch panel), then the front page, then the wall's default, Home page 1.

## 4. Rows travel with their side and ride the keyboard's top

Within a side rows stay put: Home 1 to Home 2 and Terminal to Display move no row, and a key set or
row layout changes in place. Between sides rows move two ways at once, sideways with their side and
vertically glued to the keyboard's top, so a row never hangs over a sinking keyboard. The stack's
writer places them from the scene. The glass slab belongs to the shared stack: it never slides, and
its height and look blend ([ADR 0029](0029-each-glass-surface-keeps-one-picture.md) section 8). Rows
carry no glass. Floating follows the same rule with its own card geometry: the card stays put and
blends, the rows inside travel, and its dip and swap go. Docked and Floating keep resolving geometry
apart.

Both sides' rows stay laid out and dressed at all times, on screen or off. A landing writes nothing
to a row; it switches touch and focus. A change that arrives mid-crossing (an app installed, a
badge, a key edit) applies when it happens. The landing guard covers rows as it covers materials.

The apps row stays whole as Home's row. Its crossing morph is deleted, parent clipping becomes its
host's configuration, and the tick strip, the A–Z focus targets and the lift targets are written by
their owners from values it exposes. It exposes its rest height and reads no other view's live
geometry to place itself. Touch-time reads stay as input.

## 5. The stack's scene owns every keyboard number

One pure scene computes the keyboard's top, the dock's lift, flush absorption, the rows' and extra
keys' positions, the strip under the pill and the room left for the page. Its inputs come from the
snapshot: each side's rest, the seam's progress, the keyboard's measured height, and the system
IME's inset or animation progress. Every phase reads that one answer, with one parity test. The
keyboard module only reports its height; the choreographer's geometry, the activity's lift math and
the settle spring fold in or go.

- **The keyboard follows the seam.** In a crossing its height is a pure function of the seam's
  progress on the look's curve. A drag held halfway holds it halfway, reversing reverses it, and
  progress 1 is the arriving rest. The hide over half, the late landing window, the pre-roll and the
  settle spring go.
- **A page is sized for its own rest; a crossing never resizes anything.** A resize happens only when
  a page's own rest changes (the keyboard toggled on it, rotation, a window resized). Resume and
  focus never resize unless the rest changed. The pre-size, the paused updates and the resume resize
  go; the "one resize per crossing" guard becomes "a crossing resized" and counts zero.
- **System IME.** On Android 11 and later its `WindowInsetsAnimation` progress is a snapshot input,
  so the dock and rows ride it up. Below 11 a named Hold with a backstop waits for the insets. The
  accept-insets flag with no timeout goes. Every path that shows the IME still calls
  `TermuxActivity.onSystemImeRequested()` first.
- **The reveal gate and the close correction are deleted.** The keyboard shows on the frame it is
  asked for.
- **A toggle at rest changes the room on the first frame.** Opening, the terminal shrinks on the
  first frame and the keyboard rises into the freed room. Closing, it grows on the first frame and
  the shell fills the uncovered rows as it redraws. The keyboard never waits on the shell. The
  toggle's motion is an animation input the scene reads.

## 6. The top stack

A top stack like the bottom one: one shared slab that stays put and blends, its own scene and
writer, the status bar's content on it, and the window chips as Workspace content sliding across it.
The bars' wallpaper twins move in, and they get no legibility correction in this restructure.

## 7. Order

Pages move behind the contract in this order: Assistant (the smallest, proves the contract), Home,
Display, then Terminal on a proven contract. Each kind starts with a split plan the developer checks.
The Workspace side's rows do not wait on Terminal.

## Amendment, 2026-11-17 (decided with the developer while building [#260](https://github.com/PickleHik3/termux-launcher/issues/260))

1. **A row both ends show in the same place is the stack's.** When both ends of a crossing show
   a row on the same edge (pong pins the apps row under the keyboard on the Terminal as well as on
   Home), it belongs to the stack like the slab and the keyboard and stays put. A row only one end
   shows goes with that end's side. The stack's writer decides per crossing from both ends'
   layouts (`SideRows`).
2. **Per-page row layouts stay, within a side.** Between pages of one side whose layouts differ,
   the landing may move rows to their new edges as before, and the landing guard counts nothing
   for that case. A Home and Workspace landing still writes nothing to a row.

The status bar standing among the bottom rows keeps its behaviour until the top stack
([#267](https://github.com/PickleHik3/termux-launcher/issues/267)) takes it.

## Considered options

- **A hidden second row host promoted at the landing.** Rejected, as in 0023: a second copy dressed
  twice, with a flash whenever the copies differ. The crossing branch's second host and its hand-over
  are never merged; its stack scene, stack renderer and stack material are the base.
- **Rows owned by each page.** Rejected: pages on one side would move rows between each other for
  nothing, and the dock would slide away and back between two Home pages.
- **One shared extra-keys bar.** Rejected: it would depend on whether both sides' keys match.
  Accepted cost: two bars that both follow Global slide as twins.

## Consequences

- The activity loses the wall host, the chrome's commit on demand and the keyboard lift math.
- Home 2 to Terminal with a float open on Home is a named pong check: no keyboard snap, no second
  resize.
- Readers that find rows by the stack's ids (`DockDress`, `DockRowViews`, `CrossingViews`,
  `SurfaceLongPress`, `HelpTargets`) reach rows through their side.
