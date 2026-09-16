# Layout freedom: every bar on every edge (decided 2026-09-16)

Review page: `.lavish/layout-freedom.html`. Decision: scope **A** — status bar, apps row, A–Z
index and extra keys can each sit on any of the four edges, ordered when they share an edge, per
place and orientation. Portrait side columns are allowed (the editor warns when the canvas gets
narrow). Free-form coordinates were rejected; keyboard-vs-edge anchoring is deferred. The keyboard
keeps its forms; widget grid, terminal canvas and split panes are content, not chrome.

## Why the editor felt limited

Each bar has its own hand-wired host view (`activity_termux.xml`): the apps row and the side rail
are two different view trees, the extra keys column is a second `ExtraKeysView`, the A–Z index has
three hosts. Side-column widths are summed by a fixed `max()` chain in `TermuxActivity`
(~5756-5781) and re-encoded by hand in `PlaceMiniatureView.computeBlocks` (~509-547). Every new
freedom had to be written twice.

## Model

```java
enum Edge { TOP, BOTTOM, LEFT, RIGHT }
enum Element { STATUS, APPS, AZ, EXTRA_KEYS }
final class Slot { boolean hidden; Edge edge; int order; }      // 0 = outermost on that edge
final class PlaceLayout { EnumMap<Element, Slot> slots; /* keyboard mode/form, grid unchanged */ }
final class EdgeStackPolicy {
    static List<Element> stack(PlaceLayout l, Edge e);          // ordered, outermost first
    static int thicknessPx(Element e, Edge edge, Metrics m);
    static Insets contentInsets(PlaceLayout l, Metrics m);      // replaces the max() chains
    static List<Drop> targets(PlaceLayout l, Element e, PlaceOrientation o); // edge + index
}
```

One generic `EdgeStackView` per edge as a child of `terminal_root_container`; each bar is ONE
view re-parented into a stack (the way `StatusBarEdgeArrangement.moveHost` already does), and
turns on its side via `setEdge` (`ExtraKeysView.setVertical` and the status bar already do). The
real screen and the miniature both call `EdgeStackPolicy`.

Rules kept: status bar never hidden (the wall pager rides it); only a TOP status bar gets the
system-bar glass strip; the A–Z index may still ride the apps row (then its own slot is ignored).

## Migration

Stored keys `place.<place>.<orientation>.<key>` keep their values (`bottom|left|right|hidden` and
the status/A–Z edges are a subset of the new model). New sibling `<key>_order`; absent = today's
fixed stack as verified in L1 (top: status 0, A–Z 1, apps 2, extra keys 3; bottom: extra keys 0,
A–Z 1, apps 2, status 3; sides: status 0, apps 1, extra keys 2, A–Z 3), so
an updated install renders identically. `MIGRATION_VERSION` 3 → 4. Order keys join
`ARRANGEMENT_KEYS` so Discard/↺ restores a re-order.

## L1 outcome (2026-09-16, `6b9db619`)

`Element`, `Slot`, `EdgeStackPolicy` (`stack`, `edgeOf`, `orderOf`, `thicknessPx`,
`contentInsets`, `targets`, `Metrics`, `Drop`), `PlaceLayout.slots`, store v4 with `<key>_order`.
All four edges accepted for all four bars (TOP rows are stored but not rendered until L2). The
shared side column (status bar merged with the rail or extra keys column, `max()` instead of a
sum) is replaced by stacking; L2 removes `StatusBarEdgeGeometry.sharedColumnLengthPx` and friends.
The miniature draws a bottom status bar outermost while the screen renders it innermost; L4
fixes the miniature.

## L4 outcome (2026-09-16)

`PlaceMiniatureView.computeBlocks` loops over `EdgeStackPolicy.stack` per edge (edges claimed
TOP, LEFT, RIGHT, BOTTOM), so the picture and the screen agree — including the bottom status bar,
which now draws above the dock. `MiniatureDragPolicy` is an adapter over `EdgeStackPolicy.targets`:
every edge in both orientations, one drop zone per gap in the edge's stack, drawn as insertion
lines inside the hovered edge's dashed outline. A drop writes through `EdgeStackPolicy.withDrop`
→ `store.setSlot`, renumbering every band on the edge (a riding A–Z index has its own slot pinned
to the edge it draws on, or the drop the user made is not the stack they get). The portrait
refusal became a one-line notice in the editor, shown when the canvas keeps under 60 % of the
picture's width (`MiniatureDragPolicy.canvasWidthFraction`); nothing is blocked. Rows on the TOP
edge are storable and drawn on the miniature but still not rendered by the screen until L2.
## L2 outcome (2026-09-16)

`EdgeStackView` (generic, `app:edgeStackEdge`, orientation from the edge, children given
outermost-first and reversed for BOTTOM/RIGHT) is inflated four times in `activity_termux.xml`:
`place_edge_stack_left`/`_right` beside the padded content root, `place_edge_stack_top`/`_bottom`
inside `terminal_content_column` with `terminal_surface_host` the weighted residual between them.
`TermuxActivity.applyEdgeStacks` walks `EdgeStackPolicy.stack` for each edge and re-parents the
bar hosts; it runs from `doSyncPlaceLayout` and again from `applyTerminalOverlayInsets`, so the
arithmetic and the screen never disagree. `EdgeStackPolicy.contentInsets` replaces the `max()`
chain, and `railWidthPx`/`extraKeysColumnFootprintPx`/`statusBarColumnFootprintPx`/
`azBarColumnFootprintPx` — plus `statusBarColumnLeadInPx`, `statusColumnTopOffsetPx`,
`isStatusColumnShared`, `AzBarHostGeometry.edgeInsetPx` and
`StatusBarEdgeGeometry.sharedColumnLengthPx`/`columnTopOffsetPx`/`sharesColumn`/`contentInsetPx`/
`holdsSide` — are gone. Each side stack carries its own display cutout as padding, so every bar is
a plain band. The A–Z index has one host for every edge off the dock (`place_az_bar_host`) instead
of a top one and a column one. `StatusBarEdgeArrangement.moveHost` became `band`: the walk owns
placement, the arrangement owns the band and the turn.

Not done in L2, and honest about it: the pinned apps and the extra keys still render as the dock's
own rows for a BOTTOM *or* a TOP slot, because `PlaceLayout.appsRow`/`extraKeys` map TOP to BOTTOM
for every caller including the miniature. A single `ExtraKeysView` shared with the toolbar pager
was not attempted — the bottom instance is a `ViewPager` page and unifying it means taking the
extra keys out of the pager, which is the text-input page's swipe. Both belong with L3's port of
the accessory stack into the bottom `EdgeStackView`.

## L3 outcome (2026-09-16)

Two bars became one portable view each, and the top edge renders.

**Pinned apps.** `SuggestionBarView` has a vertical form (`setVerticalForm`): one column instead of
one row, slots at the rail's own pitch (`DockLayoutPolicy.railSlotLengthPx` = 38dp icon + 10dp of
air either side, `TOP`-aligned so the last slot does not swallow the slack), a fixed rail icon size
rather than a share of a row's height, and one page holding every pinned item while its host
scrolls. The one bar is *lent* to whichever host the place asks for — `apps_bar_plank_layer` for a
bottom row, `place_apps_bar_host` (a `DockRailScrollView` that `applyEdgeStacks` moves between the
stacks) for every other edge. `updateDockRailView`, the `dock_rail_scroll`/`dock_rail_list` tree and
`SuggestionBarView.getDockRailEntries`/`launchEntryFromRail` are gone: the rail was a second tree of
plain `ImageView`s rebuilt on every pass, which is why it had no long-press pinning, no folders, no
drag pickup and no icon cache. It has all four now because it is the row.

**Extra keys.** One `ExtraKeysView` per key page, owned by the activity (`lendExtraKeysPage`) and
lent out: to `TerminalToolbarViewPager`'s page 0 while the keys are the dock's bottom row — which is
what keeps the swipe across to the text-input page untouched — and to `place_extra_keys_host` on
every other edge, vertical on a side and lying down along the top. `mColumnExtraKeysView` and the
second `ExtraKeysView` it held are gone, so `setPickMode`, `KeyUsabilityPolicy`, `refreshKeyStyles`
and a latched modifier are one view's state wherever the keys stand.
`ExtraKeysColumnGeometry` is **not** dead — it still sizes and centres the keys in a side column —
and stayed.

**TOP rows render.** `PlaceLayout` lost its five derived fields (`statusBarEdge`, `appsRow`,
`azRowShown`, `azBarEdge`, `extraKeys`); the slots map is the only model. `PlaceChromePolicy` is
rewritten on `EdgeStackPolicy.edgeOf`/`isShown`, so a top slot is a top edge rather than being
folded into the bottom. The A–Z index rides the pinned apps row wherever that row *lies down*
(`azRidesAppsRow`) — top or bottom; a rail leaves the index standing alone, which is what landscape
has always done. `DockLayoutPolicy` grew `appsOnRail` beside `appsRowOnEdge` (now "off the dock",
top included) and outputs `appsRowBandPx` (the band a lying-down row claims wherever it lies),
`railBandPx`, `railIconSizePx`, `railIconSpacingPx`, `railSlotLengthPx`.

Honest boundary: none of this is device-verified. A row or a column standing off the dock still has
no glass sheet of its own — the rail never had one either — so a top apps row draws over the
wallpaper rather than over dock glass. `contentInsets` already summed and needed nothing new.

## Defects found on pong (2026-09-16) and the fix plan

Review page `.lavish/layout-fixes.html`. Decisions: the A–Z index rides the apps row only when
both sit on the same edge (moving only the row never moves the index); fix all three phases.

| Phase | Branch | Fixes | Depends on |
|---|---|---|---|
| P1 | `fix/layout-p1` | rail render gates axis-aware in `SuggestionBarView` (icons piled, hidden after reload); dock glass width no longer frozen to a stale pixel width in `applyAccessoryLayerBounds` | — |
| P2 | `fix/layout-p2` | one shared plank with the Appearance radius for a lying-down row off the dock; `azRidesAppsRow` same-edge rule; `withDrop` moves a riding index with the row | — |
| P3 | `fix/layout-p3` | the bottom dock rows join `place_edge_stack_bottom` (ordered container above the keyboard) so every bottom order renders; `AccessoryStackLayoutPolicy` reads the resolved order | P2 |

## P3 outcome (2026-09-16)

The dock's own rows stand in one ordered `EdgeStackView` (`accessory_row_stack`, edge bottom)
inside `accessory_stack_container`, anchored `layout_above="@id/inapp_keyboard_container"` with
`alignWithParentIfMissing`, and `applyEdgeStacks` fills it from `EdgeStackPolicy.stack(BOTTOM)`.
The `layout_above` chain — apps over the indicator band over the letters over the toolbar pager —
is gone, and with it the reason no bottom order but the shipped one ever drew. Each row travels in
a host of its own so its furniture goes with it: `apps_bar_row_host` carries the indicator band,
`apps_bar_az_host` the keybind strip in the letters' slot, `terminal_toolbar_host` the hairline
over the keys. `AccessoryStackLayoutPolicy` gained `dockRows`/`rowOverAz`/`rowUnderAz` and the A-Z
row's crown and chin are read off the resolved order (`DockLayoutPolicy.DockInputs.rowOverAz` /
`rowUnderAz` replaced `extraKeysRowShown`), so whichever band the user puts over or under the
letters does the job the apps row and the extra keys used to.

`place_edge_stack_bottom` stayed where it is, the last band of `terminal_content_column`, and the
walk splits the one bottom stack between the two: the dock's rows go into the accessory stack,
everything else stands above the whole of it. It is the accessory stack that keeps the rows above
the in-app keyboard in all three forms, that wears the dock's glass
(`accessory_surface_host` fills it), and whose height is computed arithmetic — dock rows plus
keyboard, capped by `computeMaxAccessoryStackHeightPx`, which *subtracts* a bottom status bar's
height from the ceiling. Folding the status bar in would have rewritten that arithmetic and put
dock glass behind a bar that has its own; keeping it out makes the shipped screen a zero-diff.
The cost, and it is the honest one: a bottom status bar always stands above the dock whatever
order it is given, which is the L1 default (status innermost) and the only placement it has ever
had.

## P4 outcome (2026-09-16)

**The side stacks flank the canvas only.** `terminal_content_column` grew a middle row,
`terminal_canvas_band` — left stack, `terminal_surface_host`, right stack — and
`place_edge_stack_left`/`_right` moved into it from `terminal_root_container`. A rail therefore
takes its width off the terminal and nothing else: the status bar's chips, the dock's rows and the
in-app keyboard keep the whole width, which is what the miniature has drawn since L4. The canvas is
the weighted residual of the band exactly as it is of the column, so the horizontal half of
`EdgeStackPolicy.contentInsets` is now structure rather than a padding anyone applies — the root
keeps only what is left of that answer, the display cutout, and `EdgeStackView.setCutoutPx` is gone
with the days when a stack stood outside the padded root. Four numbers followed the band: a side
status column no longer cancels the root's padding with negative margins, its content length is the
band's height, its row and its stacked clock no longer add the system status-bar inset twice, and
the lens keeps no system-bar clearance of its own. A side bar is a plain band from the top of the
canvas to the bottom of it.

**The drawer turns with the row.** `AppDrawerPullGeometry` is the one place the edge decides both
halves: `pullFor` (left rail → swipe right, right rail → swipe left, top and bottom → pull down) and
`seedFor` (the rectangle the plane grows out of and shrinks back into — the rail's column, the
plank, or the dock's glass). `AppDrawerController.beginDrag` takes the pull and the seed, freezes
both with the rest of the capture, sizes the open travel off the screen's width for a rail, rounds
the seed by its short side and skips the dock's hop sideways. The arbiter already had the axis
(`Pull.RIGHT`/`LEFT`, wired to `DockRailScrollView` since the landscape rail); what it lacked was a
row that stood down. `SuggestionBarView` is told its pull (`setDrawerPull`) instead of deriving it
from the orientation, and in the rail form it arbitrates nothing at all — so a drag down the rail
scrolls the pinned apps instead of opening the drawer, and a sideways drag reaches the scrolling
host that owns the pull. A row on the top edge pulls down from the top and the plane grows out of
its plank.

Honest boundary: none of this is device-verified. The close drag started from inside an open plane
is still the plane's own vertical gesture whatever edge the drawer came from; it settles back into
the seed rectangle either way, so a rail's drawer still shrinks into the rail.

## Build plan

| Phase | Branch | Deliverable | Depends on |
|---|---|---|---|
| L1 ✅ | `feat/layout-policy` | `Slot`/`Element`/`EdgeStackPolicy`, `PlaceLayout` widened, store v4 + order keys, pure tests | current batch merged |
| L2 ✅ | `feat/layout-hosts` | `EdgeStackView` hosts in the XML; status bar, extra keys (single view), A–Z re-parented; `contentInsets` replaces the `max()` chains | L1 |
| L3 ✅ | `feat/layout-rail` | `SuggestionBarView` vertical form; `updateDockRailView` deleted; one `ExtraKeysView` per edge; TOP rows render | L2 |
| L4 ✅ | `feat/layout-miniature` | miniature and `MiniatureDragPolicy` on `EdgeStackPolicy` (edge + insertion index), portrait side columns with the narrow-canvas warning | L1 (parallel with L2) |
| L5 | integ | docs/en, release note, Waydroid + pong checks | L2–L4 |
