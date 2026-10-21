# Chrome unification

Home and the Workspace share one layout, one status bar and one dock, so the swipe between the
halves moves the page and nothing else. Decided by the developer in a grilling session on
2026-10-21; the decision record is ADR 0009 (`docs/adr/0009-one-chrome-across-the-wall.md`), the
vocabulary is in `docs/GLOSSARY.md` (**Dock design**, **Home layout**, **Search field**, **Drawer
tile**). It changes the dock-design work in [`home-dock-styles-2026-10-20.md`](home-dock-styles-2026-10-20.md).

## Decisions

### Layout

- One layout dresses both halves of the wall. Home leaves out the extra keys and nothing else.
- **Home layout** is opt-in: the Layout editor's Layout line ("Layout: One layout | Home's own"; the
  row's house segment previews Home). It holds Style, Corners, Margin, Dock design and toggles too. While on, a Home page opens the Layout editor on Home's own layout, and the bar
  and dock may change on the swipe. Off by default.
- Minimal mode stays the Workspace's (amended 2026-10-21): Home wears the normal shared layout, or
  its Home layout, whatever minimal says, because Home has no corner tab to leave minimal by. The
  command palette gains a "Minimal on/off" action that turns it on or off from anywhere (the corner
  tab's button still works in a window). Home ignores minimal altogether.
- Migration also counts a differing keyboard (height, chin, form) as "Home differs", but only
  while Home's keyboard is always on.
- Migration: if a user's saved Home layout (dock rows, dock height, row order, under-keyboard flags)
  differs from their Workspace layout, turn on the Home layout with those values so nothing visibly
  changes. Otherwise Home follows the shared layout and its saved keys are dropped.

### Status bar

- Home wears the same launcher status bar as the Workspace, placed and shaped by the layout (first
  pass: the bar's position and form as the Layout editor sets them for the Workspace today).
- The same window chips on both halves: the Workspace's windows, the display window and the Home
  floats. A window's chip tapped on Home travels to that window; a float's chip tapped in the
  Workspace travels Home and restores the float.
- Float chips (the glass row above the dock on Home) retire; a minimised float is a chip in the bar.
- The bar's CPU and RAM readings show on Home again; the focused float's titlebar readings go.
- The status swipe works on Home's top border as in the Workspace; Android's own top strip still
  pulls the notification shade.
- The house glyph is removed from the bar on both halves, and so are all place marks (prompt and
  display icons).
- The bar's `+` on Home opens a new Workspace window and slides to it; a chip tapped on Home slides
  to that window; × closes it.
- Home's page starts below the bar, as a window's does.
- A minimised float's chip is dimmed with a dot. No fallback row above the dock: with the bar hidden,
  minimised floats are reached from the command palette and the drawer.

### Dock

- One dock: one set of rows, one height, shared by both halves.
- The extra keys are the Workspace's only. Their row's height follows the travel: full on the
  first window, zero once Home's last page is centred. The rest of the dock never moves.
- The **Dock design** is held by the layout and dresses the dock on both halves: **#1 Tray**
  (Glass tray, the default), **#2 Beads** (Lens beads), **#3 Strip** (Wall strip). The picker
  shows the number and the word under each glyph, whenever the dock is selected in the Layout
  editor on any page.
- **Search field** toggle (was the Prompt line design): the command-palette field under the apps
  row, with any design. Stored per layout. With #2 Beads it sits in its own glass capsule under the
  beads.
- **Drawer tile** toggle (was the Taskbar design's Home tile): opens the app drawer on both halves,
  movable to the start, middle or end, with any design. Stored per layout. With #2 Beads it gets
  its own bead.
- Taskbar's run strip is dropped: windows live in the bar's chips.
- #3 Strip keeps its window tiles though the bar's chips repeat them.
- Migration: Prompt line → #1 Tray with the Search field on; Taskbar → #1 Tray with the Drawer
  tile on (its position kept).

### Terminal grow

- A terminal pane that grows (e.g. the keyboard goes down) keeps the prompt at the bottom only while
  scrollback fills the new rows; otherwise output stays at the top.

## Deepenings

From the architecture review of 2026-10-21 (report: `~/reports/architecture-chrome-2026-10-21/`,
not in the repo). Each lands as its own pure refactor, with today's behaviour kept and the suite green,
before the build step that changes behaviour through it.

### The Dress resolver (before step 1), in `chrome/`

- For any page it answers the page's **Dress**: the layout worn and the elements that page leaves
  out, the Dock design, the Search field and Drawer tile, Style/Corners/Margin, and the keyboard's
  shape (height, chin, rows, form). Whether the keyboard is open is not dress; each half remembers
  that (Home's stays closed, ADR 0003).
- With the Home layout off, Home's Style, Corners and Margin follow the shared layout (decided
  2026-10-21; 0008 had made them Home's own). The Home-layout migration also carries a differing
  Home shape.
- The activity, the Layout editor (in place of `LayoutVariant.offers` and its Home filter) and later
  the seam crossing all read the dress.
- `HomeChromePolicy` is deleted. Its call sites (38 in `TermuxActivity`) move to the dress one by
  one; sites that mean "is this a Home page" (paging, the home press) keep a plain page check.
  `homeDockStyle()` goes with it.

### The window chips (before step 2), in `chrome/`

- One ordered list of **window chips**: the Workspace's windows, the display window at its slot,
  the Home floats. The bar and the #3 Strip tiles adapt it, and the run strip does too until step 3
  drops it. Shell marks are observed once per refresh, before the list is built.
- One place decides what a tap or close on a chip means (slide to that window, travel Home and
  restore that float, close that window); the activity carries it out, and its four tap paths
  become one. `WindowChipTarget` is absorbed; `RunStripModel` and the float chips go with their
  steps.

### Tests

Both are tested on the plain JVM through their interfaces, with no Robolectric and no reflection
into `TermuxActivity`. Reflection-based activity tests that cover the same paths are trimmed once
the new tests cover them.

### Later, inside steps 3 and 4

The seam crossing (one module from first frame to settle, fed two dresses), the Dock design seam
(#1–#3 behind one adapter each, toggles apart), and per-layout keys plus a "do these differ" query
in the layout store.

## Build order

Each step lands and passes an emulator check before the next. All four steps are built (2026-10-21).

1. [x] Shared layout, the Home layout switch, migration (Minimal stays the Workspace's).
2. [x] The bar on Home: same chips, floats as chips (float chips retire), readings, status swipe,
   house glyph removed.
3. [x] The dock design on the Workspace dock, the extra keys folding with the travel, the run strip
   dropped.
4. [x] Prompt line → Search field toggle, Taskbar → Drawer tile toggle, the #1–#3 picker names.

## Notes for later

- #3 Strip is a candidate for the developer's planned redesign that folds the status bar's work
  into the dock (one band carrying the clock, the windows and the apps). The first pass keeps the
  bar as the Layout editor places it today.
- Each design must fit the Search field and the Drawer tile; #3 Strip with the Search field is the
  tallest combination.
