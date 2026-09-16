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

## Build plan

| Phase | Branch | Deliverable | Depends on |
|---|---|---|---|
| L1 | `feat/layout-policy` | `Slot`/`Element`/`EdgeStackPolicy`, `PlaceLayout` widened, store v4 + order keys, pure tests | current batch merged |
| L2 | `feat/layout-hosts` | `EdgeStackView` hosts in the XML; status bar, extra keys (single view), A–Z re-parented; `contentInsets` replaces the `max()` chains | L1 |
| L3 | `feat/layout-rail` | `SuggestionBarView` vertical form; `updateDockRailView` deleted | L2 |
| L4 | `feat/layout-miniature` | miniature and `MiniatureDragPolicy` on `EdgeStackPolicy` (edge + insertion index), portrait side columns with the narrow-canvas warning | L1 (parallel with L2) |
| L5 | integ | docs/en, release note, Waydroid + pong checks | L2–L4 |
