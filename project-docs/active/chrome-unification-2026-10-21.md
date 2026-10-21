# Chrome unification

Home and the Workspace share one layout, one status bar and one dock, so the swipe between the
halves moves the page and nothing else. Decided by the developer in a grilling session on
2026-10-21; the decision record is ADR 0009 (`docs/adr/0009-one-chrome-across-the-wall.md`), the
vocabulary is in `docs/GLOSSARY.md` (**Dock design**, **Home layout**, **Search field**, **Drawer
tile**). It changes the dock-design work in [`home-dock-styles-2026-10-20.md`](home-dock-styles-2026-10-20.md).

## Decisions

### Layout

- One layout dresses both halves of the wall. Home leaves out the extra keys and nothing else.
- **Home layout** is opt-in: a switch in the Layout menu ("One layout for every screen" / "Home has
  its own layout"). While on, a Home page opens the Layout editor on Home's own layout, and the bar
  and dock may change on the swipe. Off by default.
- Minimal mode applies to both halves; a Home layout, if on, stays as the user set it.
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
- The house glyph is removed from the bar on both halves.

### Dock

- One dock: one set of rows, one height, shared by both halves.
- The extra keys are the Workspace's only. Their row's height follows the travel: full on the
  first window, zero once Home's last page is centred. The rest of the dock never moves.
- The **Dock design** is held by the layout and dresses the dock on both halves: **#1 Tray**
  (Glass tray, the default), **#2 Beads** (Lens beads), **#3 Strip** (Wall strip). The picker
  shows the number and the word under each glyph, whenever the dock is selected in the Layout
  editor on any page.
- **Search field** toggle (was the Prompt line design): the command-palette field under the apps
  row, with any design. Stored per layout.
- **Drawer tile** toggle (was the Taskbar design's Home tile): opens the app drawer on both halves,
  movable to the start, middle or end, with any design. Stored per layout.
- Taskbar's run strip is dropped: windows live in the bar's chips.
- #3 Strip keeps its window tiles though the bar's chips repeat them.
- Migration: Prompt line → #1 Tray with the Search field on; Taskbar → #1 Tray with the Drawer
  tile on (its position kept).

## Build order

Each step lands and passes an emulator check before the next.

1. Shared layout, the Home layout switch, Minimal on both halves, migration.
2. The bar on Home: same chips, floats as chips (float chips retire), readings, status swipe,
   house glyph removed.
3. The dock design on the Workspace dock, the extra keys folding with the travel, the run strip
   dropped.
4. Prompt line → Search field toggle, Taskbar → Drawer tile toggle, the #1–#3 picker names.

## Notes for later

- #3 Strip is a candidate for the developer's planned redesign that folds the status bar's work
  into the dock (one band carrying the clock, the windows and the apps). The first pass keeps the
  bar as the Layout editor places it today.
- Each design must fit the Search field and the Drawer tile; #3 Strip with the Search field is the
  tallest combination.
