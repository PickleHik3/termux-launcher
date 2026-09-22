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

~~`place_edge_stack_bottom` stayed where it is, the last band of `terminal_content_column`, and the
walk splits the one bottom stack between the two~~ — **reversed by P9 (2026-09-16).** P3 kept a
second bottom stack in the content column holding the status bar alone, so the dock's rows went
into the accessory stack and the bar stood above the whole of it whatever order it was given. That
was a deliberate limitation, and it was the bug the developer reported: only the L1 default (status
innermost) ever drew. The arithmetic P3 did not want to rewrite is rewritten in P9 and comes out at
the same pixels; `place_edge_stack_bottom` is gone. What P3 got right and P9 kept: it is the
accessory stack that holds the bottom edge above the in-app keyboard in all three forms, and it is
`accessory_surface_host` that wears the dock's glass.

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

## P5 outcome (2026-09-16)

**The top stack is one row deep again.** `place_off_dock_plank_glass` was `match_parent` inside a
`wrap_content` host, and a plain `View` handed an at-most spec takes every pixel it is offered — so
the glass measured the whole content column, the plank wrapped to *that*, `place_edge_stack_top`
wrapped to the plank, and `terminal_canvas_band`'s weight had nothing left to take: no terminal at
all, and the sheet drawn over the whole screen. The plank host is a `RelativeLayout` now and the
glass is pinned `layout_alignTop`/`alignBottom` to `place_off_dock_plank_bars`, so the sheet is
exactly the bars, the host wraps them plus their air, and the canvas is the residual it was meant
to be. Nothing in the column's own params changed — they were already right.

**And one icon is no longer drawn across the terminal.** `DockLayoutPolicy.appsBarHeightHintPx` is
taken off `appsBarHeightPx`, which collapses to zero the moment the row stands off the dock, so
every caller of `SuggestionBarView.setDockRowHeightHintPx` handed a top row a hint of nothing and
the bar fell back to sizing its icon against the host it was lent to — the swallowed plank.
`DockLayout` grew `appsRowBandHintPx`, the same figure taken off `appsRowBandPx` (the band the row
claims wherever it lies), and all four call sites pass it. `iconSizePx()` falls back to the measured
host only when no band was given, and then never past `DockLayoutPolicy.maxRowBandPx` — two rail
slots, which every preset's real band stays well inside. The canvas band also clips now: a rail
holds more icons than the screen is tall and scrolls them, and unclipped the scrolled ones drew out
past the canvas to sit beside the dock.

**A side band can be lifted.** `PlaceMiniatureView.CLAIM_ORDER` claimed the side columns before the
bottom rows, so a rail ran the whole height of the phone — past the dock and into its bottom corner,
taking the grip that lifts it down there with it, while the dock's rows were narrowed by a column
the screen has not narrowed them with since P4. The order is `TOP, BOTTOM, LEFT, RIGHT` now: the
rows take the whole width, the columns stand between them and flank the canvas exactly as the canvas
band does. (The canvas rect is unchanged either way — a vertical claim never reads the width.) The
grip's hit rectangle is also the band's thickness across (`gripTouchInto`), because a column the
picture draws is a few dp wide and a grip drawn to fit inside it is far narrower than a fingertip;
along the band it is clamped to the band's own ends so it cannot take the next band's taps. A press
anywhere else on a band is still a tap, as it has always been.

Honest boundary: none of this is device-verified. The four Robolectric tests measure the real
`activity_termux.xml` and the real miniature, not the phone.

## P6 outcome (2026-09-16)

**A rail pages.** `SuggestionBarView.computePinnedItemsPerPage` answered "every pinned item" for
the vertical form and `getPinnedPagesCount` answered one, so a column shorter than its content ran
past the bottom of the canvas band — which clips since P5 — and the icons down there were
unreachable. Both now go through `DockPagingModel.railItemsPerPage(usableLengthPx, slotLengthPx)`:
as many whole `railSlotLengthPx` slots as the bar's own box holds, and the rest are pages behind
it. The swipe turned with the row rather than being written twice —
`AppDrawerGestureArbiter.evaluate` takes a `pageVertical` flag and applies the same dominance cone
to whichever axis the pages are on, and `swipeVisualOffsetX` became `swipeVisualOffsetPx` along a
`pageAxisLengthPx()` that is the width lying down and the height standing up (the clip, the commit
distance, the rubber-band, the settle and the preview page all read it). The two gestures cannot
contest a drag: the rail's drawer pull is horizontal and `DockRailScrollView` claims it in
`dispatchTouchEvent` before the bar sees the stream, while the bar's own `Pull` is `NONE` and its
pages are vertical. `setFillViewport(true)` on every edge, because a page now fits the column by
construction and there is nothing left to scroll to.

**The indicator rides with the row.** Off the dock there was no layer drawing ticks at all — the
FX views (`apps_bar_az_fx_underlay/_overlay`) are children of `accessory_stack_container` and paint
over the dock's glass — which is why a TOP row paged silently and a rail had nothing to show. The
portable host is now a `LinearLayout` (`place_apps_bar_host`) holding the scrolling host
(`place_apps_bar_scroll`, the id the bar used to have) and a `PageTickStripView`
(`place_apps_bar_indicator`), exactly as `apps_bar_row_host` holds the dock's row and its band; the
stack moves the pair. `PageTickStrip` is the pure geometry — the dock's own 13/24/2.5/4 dp ticks,
the nearest one widened by proximity to the fractional page — and the view draws it along whichever
axis it is given. `SuggestionBarView.setPageIndicator` binds it and `publishPageIndicator` feeds it
from the paths that already existed (`notifyOverflowPagePositionChanged`, the layout pass), so no
new per-frame work. On a row the strip lies under it; on a rail it stands on the inner side, the
side the terminal is on. It is `INVISIBLE` rather than `GONE` with one page, and its band is added
to `buildEdgeStackMetrics` and to the plank's glass height, so the row claims the same thickness
whether or not it happens to be paging.

**Side stacks meet the terminal frame.** `applySideStackFrameInset(terminalFrameInsetPx(true))`
pads `place_edge_stack_left/right` at both ends from `applyTerminalBorderAppearance` and from
`doSyncPlaceLayout` — one answer, derived from the same call the frame and the pane host are laid
out with, applied to the stack so every band on that edge inherits it without knowing the number.

**And the alphabets column is its whole band again.** `layoutAzBarHost` still subtracted a top
status bar's height and the accessory stack's from a column that has stood *inside* the canvas
band since P4, where neither of them is: on pong that left `place_az_bar_host_glass` 454 px of a
1362 px column, with the letters bunched into the top third under a stub of a capsule. The column
keeps only its side margins now; `AzBarHostGeometry.columnTopPaddingPx`/`columnBottomPaddingPx`
are gone and `columnLengthPx` takes the band alone. The letters were never the bug —
`AzLetterTrack` has divided the length by the letter count on both axes since L2 — so the capsule
spanning the frame-aligned column is the pitch fixed.

Honest boundary: none of this is device-verified. The eight Robolectric tests in
`TermuxActivityRailPagingTest` measure the real `activity_termux.xml`; the arithmetic is covered
pure in `DockPagingModelTest` and `PageTickStripTest`. `SuggestionBarRailFormTest.theRailIsOnePage`
became `theRailPagesByTheColumnItWasGiven` — it asserted the defect.

## P7 outcome (2026-09-16)

**One page indicator, and it is the row's.** The dock painted its own ticks from
`LauncherAzGestureFxView` (`drawPageTicksIndicator`, over the glass, while a finger owned the row)
and P6 gave the row a `PageTickStripView` of its own for every other edge — so a place with the row
on a rail had two, the dock's still lit with the accent and the rail's grey beside it, both moving
on the same swipe. The FX layer's whole interaction-overflow path is gone: `drawPageTicksIndicator`,
`drawInteractionPageIndicators`, `setInteractionOverflowState`, the position and attention
animators, the idle fade, the scratch arrays and the edge glow that path gated (which no caller
could reach — every one of them passed `useSubtlePageIndicators` true, the flag that suppressed it).
`clearDrag` and `resetAzGestureState` lost the "keep the overflow affordance" argument with it.

The strip is a band of whichever host the row is standing in, and the pair travels together:
`place_apps_bar_host` off the dock, and on the dock `apps_bar_indicator_band` — which is now a
`PageTickStripView` rather than 3dp of air, finally holding what
`AccessoryStackLayoutPolicy.computePageIndicatorBandHeightPx` has always been named for. The band is
the strip's own 9dp and it belongs to the **apps row** rather than to the gap between two rows, so
the dock is ~6dp taller with the letters shown and gains a band it never had with them hidden; that
is the price of an indicator that is always readable instead of one that appeared under a finger.
Which side of the row it takes is `PageTickStrip.leadsRow` — the stack's own reversal rule, so the
ticks lead their host on a right-hand rail (towards the terminal) and follow it everywhere else,
which is under the row on the top and bottom edges and right of a left-hand rail.

Colour is `PageTickStrip`'s: the launcher's `colorPrimary` through the two steps the FX layer took
to reach it, the page being shown at full and the rest of them muted by proximity
(`INACTIVE_ALPHA`), and the most-used page's warm `DYNAMIC_TICK_COLOR` preserved. Page state has one
source and one listener: `SuggestionBarView.publishPageIndicator` counts whichever pages the bar is
showing — the matches for a held letter, the pinned apps otherwise — which is the choice the FX
ticks made, now made where the page model already lives.

**The scrub preview opens towards the middle of the screen.** On a right-hand column the matches
rose as a vertical stack over the letters they came from, with the focused app's name drawn across
them. Two causes: `AzFloatingStripPolicy.layout` answered in `AzBarFrame`'s canonical frame, where
"along the bar" is the slot axis — so a column's matches ran *down* the column — and
`LauncherAzGestureFxView.drawFocusedAppPreviewIcon` had a `stackedBand` branch that anchored the
name bubble to the focused icon and let it climb the screen over the band.

The band is now laid out on the screen and is **always one row of icons reading left to right**,
grown away from the bar: up off a bottom bar, down off a top one, left off a right-hand column and
right off a left-hand one (`AzFloatingStripPolicy.growthFor`). Along the bar it follows the finger;
across it, it is a fixed gap clear of the letters; and it is clamped inside the canvas it was given
— the bar's own span along itself and the screen across it (`TermuxActivity.azStripCanvasBounds`).
`availableLengthPx` is the run between the bar and the far margin, so a column never lays out more
icons than the room beside it holds. The hit-test, the paging ends and the focused slot are all on
the screen now too (`resolveAzStripFocus` takes screen coordinates), and the gesture still sees the
band through `AzBarFrame.toCanonical`, so the capture wedge and the return band are untouched.
`labelSideFor` puts the name on the far side of the band from the bar. The view is a dumb consumer:
no `toScreen` mapping, no per-edge branch, only the rise direction.

Tests: `TermuxActivityPageIndicatorTest` (five, Robolectric on the real `activity_termux.xml`) —
one strip bound per edge and it is a band of the row's own host, the side it takes per edge, the
accent on the page being shown and the muted rest on every edge, the warm dynamic tick, and the FX
class asserted to have no page-tick method left at all. `AzFloatingStripPolicyTest` grew six:
growth direction per edge, no overlap with the bar for every edge × slot count × anchor, clamped
inside the canvas, a narrow column's slot count, an empty bar, and the label side.
`PageTickStripTest` grew `leadsRow`/`verticalOn` and the alpha ramp. Updated with a reason:
`DockLayoutPolicyTest`'s band column 8 → 25 px and the combined heights with it (the band is the
strip's 9dp now), `rowSwitches_…` renamed and the no-letters case expecting a band (it belongs to
the apps row), `AccessoryStackLayoutPolicyTest`'s 9 → 27 at density 3,
`LauncherAzGestureFxViewTest` and `AzFloatingStripPolicyTest` ported to the edge-aware `layout`.

Honest boundary: none of this is device-verified. The dock growing by the strip's band is arithmetic
the suite covers but a look on the phone will decide whether it reads right. And the near end of a
side bar's match band is where the finger enters it, which is also a paging-dwell zone — a dwell is
required, so a finger passing through should not flip a page, but that is reasoned, not seen.

## Build plan

| Phase | Branch | Deliverable | Depends on |
|---|---|---|---|
| L1 ✅ | `feat/layout-policy` | `Slot`/`Element`/`EdgeStackPolicy`, `PlaceLayout` widened, store v4 + order keys, pure tests | current batch merged |
| L2 ✅ | `feat/layout-hosts` | `EdgeStackView` hosts in the XML; status bar, extra keys (single view), A–Z re-parented; `contentInsets` replaces the `max()` chains | L1 |
| L3 ✅ | `feat/layout-rail` | `SuggestionBarView` vertical form; `updateDockRailView` deleted; one `ExtraKeysView` per edge; TOP rows render | L2 |
| L4 ✅ | `feat/layout-miniature` | miniature and `MiniatureDragPolicy` on `EdgeStackPolicy` (edge + insertion index), portrait side columns with the narrow-canvas warning | L1 (parallel with L2) |
| L5 | integ | docs/en, release note, Waydroid + pong checks | L2–L4 |

## P8 outcome (2026-09-16)

**A row standing alone is the icon and a sliver of air.** The plank a lying-down row off the dock
stands on kept a bar's margin *outside* its sheet of glass (`DOCK_RAIL_EDGE_MARGIN_DP`) and the
dock's own row paddings *inside* it — paddings that exist to space three rows sharing one sheet —
so a bar one icon tall claimed ~90 px of a 1080 px phone before its ticks. `DockLayoutPolicy` now
owns the row's vertical padding in one place: `LONE_ROW_AIR_DP` (4dp) around the icon and the tick
strip whenever the row is the only band in its container, and today's paddings whenever it shares
one. `DockInputs.appsRowAlone` is the switch, resolved from the arrangement by
`TermuxActivity.isAppsRowAlone` — the only dock row on the bottom edge, or the only bar on its
plank, which a rail always is. `appsRowBandPx` is re-formed around the same
`appsRowBandHintPx`, so the icon is the same size either way and only the air moved. The plank
keeps its outer margin only while it carries two bars (`offDockPlankAirPx`), so a lone row's air is
inside the sheet the glass rounds rather than counted twice; `buildEdgeStackMetrics` reads the same
answer, so the content inset and the screen agree. The rail is the same constant mirrored onto its
own axis: 4dp of padding instead of 10, and `railWidthPx` is the icon plus that air, floored at the
52dp a thumb still needs — 58dp → 52dp. The shipped bottom dock shares its sheet with the letters
and the keys, so every number of it is unchanged.

`syncPinnedAppsHost`/`syncOffDockPlank` also size the dock from the arrangement they are *given*
(`dockLayoutFor`) rather than re-reading the store, so the numbers a bar is handed and the
arrangement it is being handed them for can no longer be a pass apart.

**A separator belongs between two bands, never on a rim.** `extrakeys_divider` was a view pinned to
the top of the extra-keys host, drawn there whatever stood above it — so with the keys as the
outermost band it cut across the dock's own top edge. It is gone, and the rule is one policy method:
`EdgeStackPolicy.separatorsFor(bands)` gives one `Separator` per gap between adjacent bands and none
at either end, so a stack of three has two and a lone band has none. `EdgeStackView` draws them in
`dispatchDraw` at the boundaries it laid its children out on — over the seam rather than as a band
of its own, so no stack grows by a hairline — with the old divider's look
(`resolveAccessoryOutlineColor` at 70 × the material alpha, 1dp, held off the sides by the dock's
extra-keys inset). Only the two stacks that *are* one sheet ask for any: `accessory_row_stack` and
`place_off_dock_plank_bars`. Every band in a screen edge's stack carries its own glass, and a line
between two of those would float in the air between two sheets. The visible consequence of one rule
instead of one view: the shipped dock gains a seam between the apps row and the letters, where the
old divider only ever drew between the letters and the keys.

Tests: `DockLayoutPolicyTest` grew the lone-row air (paddings, the unchanged hint, the tighter band,
the shipped dock pinned as the oracle) and the collapsed-row case; updated with a reason —
`railWidthPx` 160 → 143 at density 2.75. `TermuxActivityEdgeStackLayoutTest` grew four Robolectric
cases on the real XML: a lone plank measuring exactly icon + tick band + 2 × air with no air of its
own, a shared plank keeping the margin it always had, a rail with the same air on all four sides,
and the plank's seam count. `EdgeStackPolicyTest` covers `separatorsFor` pure;
`TermuxActivityBottomStackOrderTest` covers the dock stack's counts (three bands → two, re-ordered
→ two, one band → none) and asserts the `extrakeys_divider` id no longer resolves.
`TermuxActivityInAppKeyboardGeometryTest` lost its divider-parent assertion with a one-line reason.

Honest boundary: none of this is device-verified. The two judgement calls a look on the phone should
settle are the plank now sitting flush against the edge of its stack (its air moved inside the
glass) and the new seam between the dock's apps row and its letters.

## P9 outcome (2026-09-16)

**The bottom edge is one stack, and the status bar is a band of it.** `edgeStack(BOTTOM)` is
`accessory_row_stack` now — for every element, the status bar included — so `applyEdgeStacks` fills
each edge from one `EdgeStackPolicy.stack(layout, edge)` walk with no split and no per-element
stack lookup (`edgeStackFor` is gone, and with it the `Map` of lists the walk kept per edge).
`place_edge_stack_bottom` is **deleted** rather than left empty: it existed only to hold the status
bar above the dock, nothing else ever landed in it, and an empty `EdgeStackView` in the content
column is a wrap_content band that measures on every pass to answer zero. `terminal_content_column`
is the top stack and the canvas band, and the canvas is still the weighted residual.

**The glass rule: the band touching the canvas keeps a sheet of its own.** A bottom status bar
ordered innermost — the only placement it has ever had — stands over the whole dock and wears the
dock's wash and blur on `terminal_window_bar_background`, exactly as it did; the dock's own sheet is
started below it (`dockGlassTopInsetPx` → `withDockGlassTopInset`, applied to
`accessory_surface_host` in both the keyboard-up and keyboard-down cases), so nothing is drawn
twice. Ordered anywhere else it is a band of the plank: no wash, no `RealtimeBlurView`, and no
wallpaper frost crop of its own (`WallpaperFrostPainter` gates the pane frost on
`Surfaces.statusBarOnDockPlank()`), because the dock's sheet is already under it.
`AccessoryStackLayoutPolicy.statusKeepsOwnGlass` is the rule and `plankBands` is what it produces —
the bottom stack less a status bar that kept its own sheet. `dockRows` became `plankBands`, and
every caller of it now counts the status bar as a band: P8's `separatorsFor` puts a hairline in each
gap (four bands on the plank → three), the letters' crown and chin are lost to a status bar over or
under them the same way they are to any row, and `isAppsRowAlone` sees it as company.

**The height arithmetic moved rather than changed.** `bottomStatusBandPx()` — the bar's own layout
height plus the air the style keeps under it — is added to the accessory stack's content height and
is no longer subtracted from `computeMaxAccessoryStackHeightPx`'s ceiling. The two cancel: the
terminal slice the ceiling protects is the same inequality it always was, and the band the bar takes
off the canvas is the same band, moved from the content column into the container below it.
`shouldShowAccessoryStack` grew a third input so a bottom status bar keeps the stack on screen with
every dock row hidden and the keyboard down — where it used to live in the content column and the
whole accessory container was `GONE`. The bar's expand/collapse animator drives the stack with it
(`applyTopStatusBarInteractiveHeight` re-adds the stack, without the terminal resize), because the
container's height is arithmetic rather than `wrap_content`. The keyboard reveal gate and its three
fail-safes are untouched, and the system-bar glass strip is still TOP-only
(`applyTerminalWindowBarBackdropInsets`).

Tests: `TermuxActivityBottomStackOrderTest` — the default order still lands on the pixels the old
`layout_above` chain drew (the oracle, unchanged); the developer's order (apps, extra keys, status,
letters reading down) puts the four hosts in exactly that order with the bar between the keys and
the letters, on the plank, three seams; status innermost keeps its own glass, two seams and the same
pixels the dock has always drawn with the bar on top; and the whole stack — the bar with it — stands
above the keyboard docked, gone and lifted out. `AccessoryStackLayoutPolicyTest` covers
`statusKeepsOwnGlass`/`plankBands` pure and the crown and chin a status bar takes.
Updated with a reason: `TermuxActivityEdgeStackLayoutTest`'s four `place_edge_stack_bottom`
assertions (the id is gone; the bottom edge's stack is `accessory_row_stack`, the content column has
two children, and the bottom stack is the one screen stack that does carry seams), and
`TermuxActivityInAppKeyboardGeometryTest`'s `shouldShowAccessoryStack` arity.

Honest boundary: none of this is device-verified. Three judgement calls a look on the phone should
settle — the seam between the status bar and the row beside it on the plank, whether a status bar
with no wash of its own reads as part of the dock or as a hole in it, and the fold gesture on a
bottom bar now that the dock's container resizes under it rather than the terminal above it.

## P10 outcome (2026-09-16)

**The lens opens towards the middle of the screen.** `StatusBarLensPolicy` — until now the place
icons' arithmetic — also answers where the bar's expanded surfaces go, from the edge it stands on:
`growthFor` (top → down, bottom → up, left → right, right → left), `card` (anchored clear of the bar
by the drop gap, centred on the canvas along the *other* axis, and clamped inside the canvas),
`widthCapPx` (off a column, the run between the bar and the far side, so a 360dp card cannot be
drawn over the bar it came from) and `enterOffsetXPx`/`enterOffsetYPx` (the card slides in out of
its bar). `StatusCardHost` is the consumer: it takes the edge (`setEdge`), keeps the platform's own
anchored drop for a top bar so the shipped screen is a zero-diff, and places the other three
itself. Tapping the system stats or the weather on a bottom bar used to drop a card off the bottom
of the screen. The bar's own chevron hint was already edge-aware (`StatusBarGesturePolicy.
expandSign`) and needed nothing.

**And the bar's row keeps the screen edge it stands on.** Swiping a bottom bar open moved its row —
the sessions chip, the window pills, the stats — 70dp up off the screen's edge and put the clock
underneath it, because the row rode the bar's *inner* edge and the widget slot the outer one, a
geometric mirror of the top bar. The rule is now the reading order rather than the mirror: the
status row is the panel's lower band and the modular slot the upper one on both row edges
(`StatusBarLensPolicy.rowOffsetPx`, `slotLeadsRow`), so a bottom bar's row stays where the compact
bar left it and the clock's band grows upward above it. `StatusBarEdgeGeometry.innerEdgeOffsetPx`
is gone with the mirror, and the slot's clip no longer has two cases. A bar down a side never rests
expanded (`StatusBarGesturePolicy.expansionAllowed`), so none of this reaches it.

**A badged icon's quick reply turns with the row, and shares the axis it now wants.**
`NotificationSwipePolicy` is the new pure rule: quick reply is a swipe *towards the middle of the
screen* starting on a pinned icon that wears a notification badge — up off the dock, down off a top
row, inward off a rail. On the bottom edge the drawer is pulled the other way, so nothing is shared
and the reply commits on the move it arms on, byte for byte what every install has. On the other
three the drawer's pull runs the same way and they share the drag in time instead: the **DOWN
decides** (only a finger that lands on a badge arms a reply; everywhere else the toward-centre axis
is the drawer's exactly as today), a **short flick commits** on release (`FLICK_DP` 24dp inside the
platform long-press timeout), and a **long drag hands off** — past 45 % of the drawer's own travel
span the reply stands down and the plane grows from where the finger is, so a badged icon is never
the one place on the row the drawer refuses to open from. A swipe along the bar is still the pages',
and an ordinary tap or long press on a badged icon is untouched.

Both hosts that arbitrate the pull run the same gate: `SuggestionBarView` for a row (its DOWN probe
is `isBadgedIconAt`, fed by the press-target map the context binding fills) and `DockRailScrollView`
for a rail, which is the outermost handler on a side and now takes a `QuickReplyProbe`.
`AppDrawerGestureArbiter` gained `claimDrawer()`, the one-way latch's hand-off door.

Tests: `StatusBarLensPolicyTest` grew six (growth per edge, the card's anchor per edge, the two
clamps, the column width cap, the slide-in offsets, the row's offset per edge and the slot's side);
`NotificationSwipePolicyTest` is eight pure cases covering every rule above;
`SuggestionBarQuickReplyTest` (five, Robolectric) covers the row — held then handed off from the
hand-off point with exactly one child cancel, an unbadged pull claiming as ever, an along-bar swipe
still paging, a rail lending nothing, and the DOWN probe; `DockRailQuickReplyTest` (two) covers the
rail's own gate. Updated with a reason: `StatusBarEdgeArrangementTest.aBottomRowKeepsItsClockAtThe
FootWhereTheRowIsNot` became `theClockLeadsTheRowOnBothRowEdges` — it asserted the mirror — and
`StatusBarEdgeGeometryTest` lost its `innerEdgeOffsetPx` case with the method.

Honest boundary: none of this is device-verified. Three judgement calls a look on the phone should
settle — a bottom bar's card rising off it, a side bar's card standing beside the column at the
capped width, and whether 24dp of flick inside the long-press timeout is the right window for a
quick reply on a top row. The hand-off is reasoned from the arithmetic and exercised at the view
seam in Robolectric, not felt under a thumb.
