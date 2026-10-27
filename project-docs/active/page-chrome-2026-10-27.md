# Ground, dress and crossings per page: design, 2026-10-27

Candidates C, D and E of the page architecture review
(https://amal-build.taild9c5dc.ts.net/reports/page-architecture-2026-10-27/), grilled with the
developer on 2026-10-27 under ADR 0015 §3, §4 and §6 (proposed). Builds on the page list
(`page-list-2026-10-27.md`) and the Look of a page (`page-look-2026-10-27.md`). No code yet.

## Facts this rests on (v2-redesign, 2026-10-27)

- The wall has two wall pages (`PaneWallPage.WIDGETS`, `TERMINAL`); Home pages are the widget
  grid's own pager (`WallRow` :14-18, `WidgetPaneView.onPageChangeRequested` :1066), windows the pane
  controller's (`stepWindowFromWall`, TermuxActivity :25207). The crossing runs only between the two
  wall pages (`readSeamTravel` :23574, begin :23627); Home → Home never reaches it.
- The wallpaper is one view behind the whole wall (`activity_termux.xml` :30). The dim is painted on
  the root and on `WallpaperBackdropView` (`applyUnifiedBackgroundDim` :4668), and the ink solver
  reads one `wallpaperDimColor` (`ChromeInk` :1883). A Home page draws no ground of its own.
- Layouts: `layout.<scope><orientation>.<key>`, scope minimal., home. or none
  (`PlaceLayoutStore` :1483, `LayoutVariant` :37); linked sizes :293-296; pinned dock items are
  wall-wide (`LauncherConfigRepository` :97).

## Wall slots (the page list's slice 2)

Every Home page and every Display page is its own **wall slot**; the Terminal page is one slot whose
windows keep the pane controller's paging inside it. The wall is the only pager between pages, and
builds only the current slot and its neighbours (recycled). `WidgetPaneView` loses its internal
paging and renders one page per slot; a Home → Home swipe becomes the wall's plain swipe, with the
same feel.

A Display page not in front shows its own last **Stand-in** (kept per page when it is left), or its
card when it never ran. The live surface moves over, and a bound app is raised, at the landing. No
live picture is ever on two pages.

## C: ground per page

A page's **ground** is its wallpaper dim and backdrop opacity from its PageLook snapshot, plus on the
Terminal page the canvas tint when the canvas is not glass (with glass, the tint stays on the
slab). The root keeps no tint.

- One ground strip, behind the slots and above the wallpaper, draws it. A pure function answers the
  bands from the stops' grounds and the wall's offset; seams are feathered over about 24 dp and
  travel with the pages; at rest each page shows its own ground exactly. Opaque mode keeps today's
  behaviour.
- The ink solver asks for the ground under a rect instead of one dim: `ChromeInk`, `GlassInk`'s dim
  input and Bare's ink ladder. ADR 0014 wave 2c's even-out takes the same answer.
- Owns the Bare tint bug's second cause (the terminal tint in the wall ground).

## D: dress per page kind

- **One shared arrangement**: where each element stands (edge, order, under the keyboard), edited
  from any page (0009 holds for arrangement).
- **Per-kind visibility**: which elements show. Defaults: Home pages show the status bar and the
  dock; Terminal and Display pages the status bar and the extra keys. Any element may be shown on
  any kind.
- The **extra keys** become an Element of their own, out of the dock's slot map
  (`PlaceLayout` :296).
- A page may opt into a **page layout** of its own (keyed by page id), seeded from its kind's.
  Sizes stay linked everywhere. The dock design, Search field and Drawer tile are part of a layout.
- Minimal is one switch for the Terminal page and every Display page; Home pages ignore it.
- Wall-wide as today: pinned dock items, the keyboard on/off switch (`keyboard_turned_off`).
- Keyboard open/closed is remembered per kind.
- Editor: the Elements card carries a chip "<kind> | <page>" ("Home pages | Home 2", "Terminal
  pages | Terminal", "Displays | Firefox"). The kind side writes the kind's visibility; the page
  side opts the page into a layout of its own.
- `WallHalfShape` is deleted (its `read` ignores the half); shapes come from the wall-wide values.
- Upgrade: the Home layout's visibility becomes the Home kind's; if its arrangement or dock design
  also differ from the shared layout, every Home page gets a page layout with those values, so
  nothing moves. The shared layout becomes the shared arrangement and the Terminal kind's
  visibility; upgraders keep the dock shown on the Terminal page (ADR §7).

## E: a crossing between any two pages

- A crossing is a slide whose two stops are on different pages. Window → window and a session swap
  are not crossings; nor is Home → Home when the two pages' dresses and looks are equal (a plain
  flip).
- Endpoints: a page's dress (D), its PageLook snapshot (B) and its resolved arrangement.
  `SeamCrossing.Half` becomes a page endpoint; `DockGeometry.Side {HOME, WORKSPACE}` and the
  snapshot's home/workspace fields go; `Crossing.heldArrangement(boolean home)` and
  `heldRest(boolean home)` take an endpoint.
- A far jump (a chip, the home press) is one spring today (`PaneWallLayout.goTo` :863) and morphs
  straight from the leaving page to the arriving one; pages passed on the way do not dress the
  chrome. Retargeting stays as 0012 §5.
- The keyboard travels with the kind that has it open (0012 §4).
- Existing tests carry over with Home and Workspace as two endpoints (SeamCrossingTest,
  CrossingTest, DockGeometrySeamTest, DockMorphTest, SeamTravelTest, StackLayoutTest), plus
  Home ↔ Home (own layout) and Terminal ↔ Display cases.

## Order

A slice 1 → B1 → C → (2c) → D → A slice 2 (wall slots, free order) → E → A slice 3 (Display pages).
