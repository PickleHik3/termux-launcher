# B-5 implementation plan — category tiles + expandable category detail

Builds on landed B-1 through B-4. Owns the third drawer view type, local category assignment, the category
overview and category expansion. It owns nothing from B-6/B-7 (drawer folders, the full drawer-settings screen,
or new category-tuning preferences).

Survey verified 2026-08-11 against the current uncommitted B-4 worktree in
`app/termux-launcher`.

## 0. Verified constraints and reusable seams

| Existing fact | Reuse / consequence for B-5 |
|---|---|
| The drawer host is a full-screen sibling overlay explicitly outside the accessory stack (`activity_termux.xml:1634-1670`). | Every B-5 view remains below `AppDrawerPlaneView.getContentHost()`. Add no activity-layout band and make no call to `AccessoryStackLayoutPolicy.computeCombinedHeight()`, `TerminalView.updateSize()`, toolbar-height setters, or accessory-geometry sync. Category scroll and expansion cannot produce SIGWINCH. |
| One controller lazily creates one plane and one `AppDrawerContentView` (`AppDrawerController.java:542-603`). | Categories are a third mode in that content object. There is no category controller, plane, host, search controller, or close transition fork. |
| The controller samples geometry once and has one transition writer (`AppDrawerController.java:227-272`, `:874-920`). | Category expansion changes only children inside the already-laid-out content bounds. It never recaptures the dock/accessory bands and never writes plane or accessory transforms. |
| The controller's frame loop already advances content effects beside the drawer and search-reveal springs (`AppDrawerController.java:488-519`). | The category expansion spring joins `advanceDrawerFx`; it does not own a second `Choreographer` callback. Reduced motion follows the controller's existing global check. |
| The house `Spring` is clamped, substepped, non-finite-safe, and snaps under reduced motion (`Spring.java:19-74`). | Use `com.termux.app.Spring` for category expand, collapse, cancellation and pull-to-collapse. Add no `androidx.dynamicanimation`. |
| `AppDrawerViewType` currently recognizes `VERTICAL` and `HORIZONTAL`, with unknown values falling back to vertical (`AppDrawerViewType.java:7-22`). The existing `app_launcher_drawer_view_type` preference already round-trips through shared preferences and the style data store (`TermuxAppSharedPreferences.java:200-250`; `TermuxStylePreferencesFragment.java:455-458`, `:508-509`). | Add `CATEGORIES("categories")` to this enum, this same preference key, and the same entries/values arrays. Preserve unknown/corrupt fallback to `VERTICAL`; add no second selector. |
| `AppDrawerContentView` already owns all three current content surfaces and switches them atomically (`AppDrawerContentView.java:192-258`, `:367-419`). Search results are pushed once then routed to the active adapter (`:534-581`). | Add one category child and convert every two-way boolean/`else` into an exhaustive `switch`. The existing search controller remains the sole result source. |
| Plane ownership is sampled once at DOWN and content-owned points fully defer the plane (`AppDrawerPlaneView.java:266-295`). Content close reports reconverge through the plane forwarders (`:320-350`). | Category list/detail points are content-owned for the whole stream. All scroll/collapse/close decisions from them use nested pre-scroll; the plane never steals a category RecyclerView's touch. |
| Vertical scroll-versus-close already samples `atTop` at DOWN and enforces first overpull/second pull (`AppDrawerCloseArmingPolicy.java:60-160`; `AppDrawerContentView.java:881-903`, `:938-1053`). | Reuse it byte-for-byte for the category overview and flat category search results. The drag that reaches the top still cannot become the drag that closes. Expanded detail adds a separate pure collapse decision; it cannot spend or inherit the close arming. |
| B-4 locks its horizontal layout manager at DOWN, before the axis is known (`AppDrawerHorizontalPagerView.java:136-175`), because a neutral diagonal previously could let the page move while a later close claim also consumed the stream. | B-5 samples view type, category presentation, hit part and `atTop` at DOWN and uses one-way outcomes. A stream can perform exactly one of tile action, list scroll, category collapse or drawer close; a retained child click is explicitly gated after any nested claim. |
| The A-Z column is meaningful only for a contiguous alphabetical list and is hidden/reset in horizontal mode (`AppDrawerContentView.java:404-418`, `:627-636`). | It is also always `GONE`, inactive and reset in categories mode, including category search. Category order and ranked search results are not A-Z sections. Categories reclaim the former column width as ordinary content. |
| Search is focusless and has a singleton model/controller; the pill only reports tap/clear (`AppDrawerSearchController.java:70-170`; `AppDrawerContentView.java:773-785`). | Do not add an `EditText` or request focus. Typing in categories mode uses the existing in-app keyboard, hardware-key and system-IME code-point channels. |
| Cell icon/tint/launch/long-press behavior is centralized in `AppDrawerAppCellView` and delegates to `SuggestionBarView` (`AppDrawerAppCellView.java:58-89`, `:117-131`). | Expanded category cells use this exact binder. The three large preview icons call the same public icon, tint, launch and long-press seams; the four small previews are one aggregate expand target and do not invent a second launch/menu path. |
| Rendered icons share one 6-16 MiB byte-budgeted LRU (`SuggestionBarView.java:238-260`, `:469-490`); cache keys include pixel size (`:2619-2646`). | Category metrics are budget-aware, tiles bind lazily, recycled tiles unbind every drawable, and expansion never keeps the overview and detail icon sets alive together. There is no category cache. |
| `LauncherAppDataProvider` builds the primary and profile catalogue off the main thread (`LauncherAppDataProvider.java:94-142`, `:210-254`, `:293-331`), but `LauncherAppEntry` currently carries no category/install metadata (`LauncherAppEntry.java:10-43`). | Collect `ApplicationInfo.category` and first-install time during the existing snapshot load, not through per-tile `PackageManager` calls on the UI thread. Preserve old entry constructors with undefined/zero metadata defaults. |
| `LauncherUsageStatsStore` already persists count and last-launch time by profile-aware stable ID (`LauncherUsageStatsStore.java:27-65`, `:96-131`), but `rankForAz` includes never-launched entries and ignores recency (`:75-89`). | Add a suggestions query that returns only positive-use entries, ordered by count, recency, label and stable ID. Do not change `rankForAz`, because the shipped dock and A-Z behavior depend on it. |
| Package callbacks already re-drive the drawer independently of dock enablement (`TermuxActivity.java:12639-12708`, `:12723-12739`), and provider invalidation drops pending callbacks (`LauncherAppDataProvider.java:78-88`). | Rebuild category models through the existing `onAppCatalogChanged()` path and keep the current idempotent `warmAsync` re-registration. No category-specific receiver. |
| Back ordering is palette, drawer, then surfaces behind it (`TermuxActivity.java:9939-9964`). Controller teardown resets search/content before restoring accessory geometry in a `finally` (`AppDrawerController.java:997-1047`). | Extend only the drawer's internal Back consumer: query first, expanded category second, drawer close third. Category teardown runs inside the existing content reset before the accessory freeze is released. |
| The app module compiles production sources as Java 11 and configures JUnit 4.13.2 + Robolectric 4.13 (`app/build.gradle:111-125`, `:177-178`). | Every B-5 production/test class is Java; add no Kotlin source/plugin. Pure policies use JUnit4 and Android view/lifecycle behavior uses Robolectric. |

## 1. Decisions

### 1.1 Third mode, not a parallel drawer

`AppDrawerViewType` becomes `VERTICAL`, `HORIZONTAL`, `CATEGORIES`. The preference array becomes Vertical,
Horizontal pages, Categories; the stored values remain `vertical`, `horizontal`, `categories`. The default and
fallback stay `vertical`.

`AppDrawerContentView` remains the single owner of the pill and result list. It gains an
`AppDrawerCategoryView` in the same top/bottom content rectangle as the other app surfaces. The mode switch has
three exhaustive branches:

- `VERTICAL`: shipped grid + rope; pager/dots/category view gone.
- `HORIZONTAL`: shipped pager + dots; grid/rope/category view gone.
- `CATEGORIES`: category view at full width; grid/pager/dots/rope gone. A non-empty search temporarily replaces
  the category view with the existing flat vertical grid at full width, but the selected preference and gesture
  type remain `CATEGORIES`.

The controller's `prepareContent()` currently has `if VERTICAL / else HORIZONTAL`
(`AppDrawerController.java:776-796`); this must become an explicit switch before the enum grows. The category
branch resolves category metrics from the full content width, the same pill/top gap and bottom band, the current
drawer radius, and the rendered-icon byte budget. It reads no vertical or horizontal grid-size preference.

### 1.2 Exact category model and assignment

The category order is fixed and is also the visual order after empty buckets are removed:

1. Suggestions
2. Recently Added
3. Social
4. Productivity
5. Utilities
6. Entertainment
7. Shopping & Food
8. Finance
9. Health
10. Photo & Video
11. Travel
12. Information & Reading
13. Other

Suggestions and Recently Added are synthetic overlays. An entry may appear in either or both and also appears
exactly once in a taxonomy bucket. Within any bucket an `AppRef.stableId()` appears at most once. Multiple
launcher activities from the same package remain distinct entries, matching the shipped drawer; cloned/work
entries remain profile-distinct for usage ranking.

Taxonomy assignment is deterministic:

1. A curated package mapping wins when present. It intentionally overrides an app's coarse declared platform
   category; this is how a declared `PRODUCTIVITY` app can be placed in Finance or Health.
2. Otherwise, on API 26+, map `ApplicationInfo.category` as follows:
   `SOCIAL -> Social`, `PRODUCTIVITY -> Productivity`, `ACCESSIBILITY -> Utilities`,
   `GAME/AUDIO/VIDEO -> Entertainment`, `IMAGE -> Photo & Video`, `MAPS -> Travel`, and
   `NEWS -> Information & Reading`.
3. `CATEGORY_UNDEFINED`, an unrecognized future integer, metadata lookup failure, or API below 26 goes to Other.

The curated map is exactly
`app/src/main/res/raw/app_drawer_category_overrides.csv`, UTF-8, with this non-quoted format:

```text
# schema=1
# package_name,category_slug
com.example.bank,finance
com.example.reader,information_reading
```

Rows are lower-case package name plus one of the non-synthetic slugs, sorted lexicographically by package.
Blank lines and `#` comments are allowed; commas/quoting/wildcards are not. A package applies to all its launcher
activities and profiles. `AppDrawerCuratedCategoryMapTest` reads the shipped raw resource and fails on a missing
schema line, malformed or unsorted package, duplicate package, unknown slug, or use of `suggestions` /
`recently_added`. That makes additions grep-friendly one-line diffs and turns map review errors into test
failures. Runtime parsing skips a bad line and falls through to platform/Other rather than blanking the drawer;
the test makes that degradation a release backstop, not normal behavior. Labels live in `strings.xml`, never in
the CSV, so map keys stay stable under localization.

Metadata is added to `LauncherAppEntry` as immutable `applicationCategory` and
`firstInstallTimeEpochMs`, with an additive constructor and existing constructors delegating to undefined/zero.
The provider fills it while already off-main-thread:

- Primary user: `ActivityInfo.applicationInfo.category` behind `SDK_INT >= 26`; obtain `PackageInfo` once per
  package and cache its `firstInstallTime` for all launcher activities in that package.
- Work/clone profile: `LauncherActivityInfo.getApplicationInfo().category` behind the same API guard and
  `LauncherActivityInfo.getFirstInstallTime()`. Failure becomes undefined/zero for that entry only.

Synthetic buckets are then computed locally:

- `Suggestions`: every catalogue entry with a positive local launch count, from new
  `LauncherUsageStatsStore.rankForSuggestions(entries)`, ordered count descending, last launch descending,
  label case-insensitively, then stable ID. There is no arbitrary cap: the tile previews the first seven and the
  expanded list exposes the full locally ranked set. With no recorded launches the bucket is empty.
- `Recently Added`: entries with a valid first-install time no more than 30 days old, ordered newest first then
  label/stable ID. A timestamp up to 24 hours ahead is tolerated as clock skew and treated as age zero; larger
  future or zero timestamps are excluded. Package replacement does not make an app recent because
  `firstInstallTime`, not update time, is used.

Every empty bucket, including Other, is omitted rather than rendered as a blank tile. If all buckets are empty,
the category view shows one non-focusable “No apps” empty state while the pill and close chrome remain usable.

### 1.3 Tile measurement and layout: custom ViewGroup in a recycled grid

The overview is a vertical `RecyclerView` with `GridLayoutManager` and at most 13 holders. Each holder is an
`AppDrawerCategoryTileView extends ViewGroup`, built programmatically. Its measured width is the exact span width.
`onMeasure` computes:

- `tileSide = measuredWidth - 2 * tileHorizontalInset`;
- root height = `tileSide + headingGap + measuredHeadingHeight + itemBottomGap`;
- two equal large slots per tile row, from one inner padding and one centre gap;
- three large icon centres in top-left, top-right and bottom-left slots;
- the bottom-right large slot divided into four equal small-icon cells.

`onLayout` places seven real `ImageView`s, one transparent expand hit target exactly over the 2x2 small block,
and one centred single-line `TextView` below the tile rectangle. The root draws only the rounded square behind the
icons; the heading is outside that draw rect by construction. Radius is the controller's resolved drawer radius,
clamped only to `tileSide / 2`. Tiles use a translucent themed fill/stroke, not twelve live blur views.

Interaction is equally explicit: the first three large icons tap to launch and long-press through
`SuggestionBarView`; the four small icons are display-only children under one accessible “Open <category>” hit
target; that target or the heading expands. Missing preview entries leave their icon slots invisible and
non-clickable; the aggregate block and heading still open any non-empty bucket.

`AppDrawerCategoryGridMetrics` chooses columns by physical fit:
`floor((usableWidth + gap) / (144dp + gap))`, clamped to 1..3. This produces two columns on ordinary portrait
phones, one rather than clipped tiles on genuinely narrow panes, and three on tablets/landscape. Side padding,
inter-tile gap, heading typography and expanded-grid columns are fields in the same pure result, re-resolved on
every open/configuration.

Why this over alternatives:

1. Nested `GridLayout`s/weighted `LinearLayout`s would create a deep hierarchy per tile and distribute rounding
   errors differently between the large slots and 2x2 block; the square and equal-slot invariant would not be a
   single measurement decision.
2. A canvas-only tile would make icons cheap to draw but would discard the existing drawable state, content
   descriptions, launch source view and long-press anchor. Seven child views preserve those seams.
3. A hand-built vertical tile container would eagerly hold 84 icon views. RecyclerView retains only attached
   tiles and gives nested scrolling/`canScrollVertically` to the existing close policy.

### 1.4 Expanded layout and expansion animation

`AppDrawerCategoryView` owns a final-layout detail layer containing a large category `TextView` and a vertical
`RecyclerView(GridLayoutManager)` of `AppDrawerAppCellView`s. A small custom detail `ViewGroup` measures from the
bottom upward:

1. Measure the large header.
2. `desiredRows = ceil(appCount / expandedColumns)` and
   `desiredListHeight = desiredRows * expandedRowHeight`.
3. Reserve `32dp` of empty space above the header and a fixed header-to-list gap.
4. `listHeight = min(desiredListHeight, availableHeight - emptyTopMin - headerHeight - gap)`.
5. Lay the list against the bottom edge, then the header immediately above it. Any remaining space stays empty
   above the header. When desired height exceeds the cap, the fixed-height RecyclerView starts at position zero
   and scrolls normally.

This is a measured bottom-aligned list, not `translationY`, reverse layout or a spacer item. It therefore remains
correct at large font scale and after a package-count change, and adapter order stays ordinary row-major order.

The visible “tile expands” transition uses no bitmap snapshot and no layout-per-frame:

- At activation, capture the selected tile's inner-square bounds in category-view coordinates and the stable
  category ID; lay detail at its final bounds once.
- A draw-only `AppDrawerCategoryMorphView` interpolates one rounded rect from the captured tile square to the full
  category body. The overview fades over progress 0..0.25; detail header/list fade over 0.35..0.70; the morph
  surface fades into the plane over 0.75..1. No tile is reparented out of RecyclerView.
- One `Spring(0f, 420f, 41f)` drives progress 0 -> 1 or 1 -> 0 and is advanced by the controller's existing
  `advanceDrawerFx` call. The same spring handles a cancelled pull by retargeting 1. Reduce motion snaps and runs
  the same finalization code.
- At the forward 0.25 staging boundary, release every overview preview drawable, then submit the detail adapter.
  On reverse, release detail at 0.35, then rebind overview before it becomes visible below 0.25. The morph rect
  bridges the deliberate 0.25..0.35 icon-free interval. Thus preview and detail icon sets are never live together.
- During `EXPANDING`/`COLLAPSING`, category-body touches are content-owned and swallowed; Back may reverse/retarget
  the spring, and a search query may replace the transition, but no app or tile action fires through it.

### 1.5 Exact three-way nested-scroll arbitration

The three user intentions are category action (expand/collapse), vertical list scroll, and drawer close. They are
resolved from a DOWN snapshot plus one-way nested-scroll claims; no parent intercepts a RecyclerView stream and
no synthetic `ACTION_CANCEL` is sent.

At DOWN, `AppDrawerContentView.dispatchTouchEvent` records all of the following and never re-reads them mid-stream:
selected `AppDrawerViewType`, category presentation, `AppDrawerCategoryTouchRegions.Part`, active RecyclerView,
`atTopAtDown`, `scrollableAtDown`, raw Y, and current close-arming timestamp. The plane gate returns content-owned
for the overview RecyclerView, expanded header/detail list, and transition body; it returns chrome for the pill,
side margins, bottom band and the empty area above an expanded category header.

The rules are:

1. **Overview tile action:** a tap within slop on the 2x2 block or heading expands; a large-icon tap launches.
   RecyclerView naturally cancels a click when it scrolls. Because a nested close deliberately does not cancel
   its retained child, `AppDrawerCategoryView.ClickGate` suppresses all tile/icon/header clicks from the moment
   any close claim is made through terminal dispatch.
2. **Overview vertical scroll/close:** run the existing `AppDrawerCloseArmingPolicy` against the overview
   RecyclerView. Mid-list at DOWN scrolls for the entire stream even if it reaches the top. Top + unarmed
   overpulls and may arm at release. A fresh top pull while armed and inside 1200ms consumes nested pre-scroll and
   drives the existing drawer close. A non-scrollable overview behaves as chrome and closes on its first
   deliberate downward pull. Upward motion scrolls/disarms. This is exactly the shipped vertical contract.
3. **Expanded detail scroll/collapse:** an expanded list that was not at top at DOWN scrolls for the whole stream,
   even if it reaches top. If it was at top, the first downward (`dy < 0`) nested pre-scroll latches
   `COLLAPSE_DRAG`, consumes the vertical delta and drives expansion progress from raw Y; it never routes that
   stream to drawer close. Upward motion latches `SCROLL` and later downward drift cannot flip it. Release reuses
   `AppDrawerCommitPolicy` in closing direction: >=50% travel or a >=900 px/s downward fling after 12% commits
   collapse; an upward fling or insufficient travel springs back expanded. CANCEL always springs back expanded.
4. **Expanded header:** tap the large header to collapse through the spring. It is not a drag surface; a moved
   stream is suppressed, not converted into close. This keeps header click and close from both firing.
5. **Drawer close while expanded:** a deliberate downward-dominant swipe beginning in the empty space above the
   header, the pill, side margin or bottom band stays on the plane's shipped arbiter and closes the drawer on that
   first stream. Back also provides deterministic hierarchy. A detail pull collapses only; closing through the
   detail requires a new gesture after overview returns (and overview's normal arming), so one continuous finger
   can never collapse a tile and close the drawer.
6. **Nested signs/end:** category nested fling uses the existing single conversion
   `closeVelocityForNestedFling(velocityY)`. Slow release ends on `onStopNestedScroll(TYPE_TOUCH)` with zero.
   Fling/end/cancel are guarded by a stream token so duplicate nested-stop callbacks finalize once.

This specifically avoids B-4's neutral-diagonal failure class: the plane has already deferred at DOWN, category
presentation/scroll position are frozen for that stream, every content claim is one-way, and the child click gate
prevents a nested claim plus retained UP from also activating a tile. There is no late axis handoff and no live
“now at top?” read.

### 1.6 Focusless search and A-Z behavior

The pill and all three input channels are unchanged; no `EditText`, focus request or external-input geometry path
is added.

- Empty query in categories mode shows overview or the currently expanding/expanded category.
- The first non-empty query immediately cancels expansion state to `OVERVIEW`, releases category icon holders,
  and shows the existing full-width vertical app grid with `LauncherRankingEngine` results. It scrolls from the
  top and uses the vertical first-overpull/second-pull close rule.
- Clearing the query returns to category overview, never to a previously expanded bucket. Back therefore clears
  the query first; the next Back closes unless the user has expanded a category again, in which case it collapses
  that category and a following Back closes.
- Enter launches ranked result zero through `SuggestionBarView`, as today. Package changes re-rank the query and
  keep it in the flat search surface.
- The A-Z rope is `GONE`, inactive, unscrubbed and reset for the entire categories mode, including flat search.
  No width is reserved and the former strip is ordinary category/search space. It reappears with exact shipped
  state only when the selected view type is `VERTICAL` and the query is empty.

### 1.7 Rendered-icon memory budget

At density 4, a 40dp large icon is 160px. One rendered icon retains two ARGB_8888 bitmaps, so it costs 204,800
bytes; a half-size 20dp icon costs 51,200 bytes. One fully populated tile would therefore cost
`3 * 204,800 + 4 * 51,200 = 819,200 bytes` (~0.78 MiB), and twelve eagerly retained tiles would cost ~9.38 MiB
before the dock or expanded list. B-5 must not build that eager shape.

Add a read-only `SuggestionBarView.getRenderedIconCacheBudgetBytes()` and pass the existing 6-16 MiB budget into
`AppDrawerCategoryGridMetrics`. Reserve at most 60% for attached category previews. With large rendered size `B`
pixels and small `B/2`, the charged cost per full tile is `32 * B^2` bytes. Metrics estimate attached tiles as
`columns * (ceil(viewportHeight / itemHeight) + 1)` and solve
`B <= sqrt((cacheBudget * 0.60) / (32 * attachedTiles))`, additionally capped at 40dp and by slot geometry. The
remaining 40% is headroom for dock icons and entries touched immediately before opening.

The overview uses `setItemViewCacheSize(0)`, disables layout-manager item prefetch, and unbinds all seven drawables
in `onViewRecycled`; recycled pools retain empty holders only. The detail list uses the exact same large icon pixel
size, so its cache keys reuse preview-large entries rather than create a third size. The staged expansion releases
overview icons before detail submission, and collapse does the reverse. Small preview entries may remain only in
the byte-bounded shared LRU and are evicted normally. No list, adapter, tile, morph view or animation holds a
bitmap snapshot.

## 2. Concrete class-by-class design

### 2.1 New classes in `com.termux.app.launcher.drawer`

| Class | Kind | Responsibility |
|---|---|---|
| `AppDrawerCategory` | pure enum | The 13 stable IDs/slugs, fixed order, synthetic/taxonomy flag and localized label resource. Curated parsing rejects synthetic IDs. |
| `AppDrawerCategoryBucket` | pure immutable | Category ID plus immutable, stable-ID-deduplicated ordered entries; exposes first seven previews without copying the whole bucket. |
| `AppDrawerCuratedCategoryMap` | parser/value | Parse the versioned raw CSV once into an immutable package map; invalid lines degrade independently. No network/update channel. |
| `AppDrawerCategoryClassifier` | pure | Curated -> platform -> Other taxonomy assignment; merges usage-ranked Suggestions and 30-day Recently Added; fixed ordering; omits empty buckets. Clock is an argument, not read internally. |
| `AppDrawerCategoryGridMetrics` | pure immutable | Overview columns/tile side/slot rectangles/icon byte-budget size, detail columns/row height, header gaps, bottom-aligned list cap, radius clamp and collapse travel. Handles zero/short bounds finitely. |
| `AppDrawerCategoryTouchRegions` | pure | `OVERVIEW_LIST`, `EXPAND_ACTION`, `DETAIL_LIST`, `COLLAPSE_ACTION`, `TRANSITION_BODY`, `EMPTY_CHROME`, `OUTSIDE`; half-open hit testing from final view bounds. Maps to plane defer/chrome without changing `AppDrawerPlaneView`. |
| `AppDrawerCategoryGesturePolicy` | pure | DOWN snapshot plus one-way `PENDING/SCROLL/COLLAPSE_DRAG/CLOSE_DRAG/ACTION` decisions. Delegates overview close to the existing arming policy; expanded detail can collapse but never close on the same stream. |
| `AppDrawerCategoryExpansionModel` | pure | Legal overview/expanding/expanded/collapse-drag/collapsing transitions, selected stable category ID, staging-boundary bookkeeping and package-refresh outcomes. The Android view owns the `Spring`. |
| `AppDrawerCategoryTileView` | custom `ViewGroup` | Exact square/slots/header measurement; rounded tile draw; seven icon children; three direct app actions; aggregate small-grid and heading expansion; click gate; complete unbind. |
| `AppDrawerCategoryTileAdapter` | `RecyclerView.Adapter` | Stable category holders, first-seven binding, selected tile lookup/bounds, release/rebind of attached previews, no retained icons in recycled holders. |
| `AppDrawerCategoryDetailAdapter` | `RecyclerView.Adapter` | Expanded bucket cells using `AppDrawerAppCellView`; same large preview pixel size; launch/long-press/click gate; stable-ID anchor lookup; unbind on recycle. |
| `AppDrawerCategoryMorphView` | draw-only `View` | Allocation-free rounded-rect interpolation from captured tile square to final category body and staged alpha; no icon or bitmap snapshot. |
| `AppDrawerCategoryView` | custom `ViewGroup` | Overview RecyclerView, bottom-aligned detail header/list, empty state, expansion Spring/state machine, touch-part geometry, raw-Y observer, click suppression, package-refresh reconciliation and callbacks to request controller frames. It does not intercept. |

### 2.2 Existing drawer and data classes

`AppDrawerViewType` (`:7-22`): add `CATEGORIES`; recognize exactly `categories`; keep all unknown values vertical.

`LauncherAppEntry` (`:10-43`): add immutable category/install fields and an additive full constructor; current
constructors preserve source compatibility and tests by supplying undefined/zero.

`LauncherAppDataProvider`:

- extend `Snapshot` and entry construction in the existing background load (`:210-254`, `:293-331`), with one
  primary-user `PackageInfo` lookup per package;
- profile metadata comes from the already-enumerated `LauncherActivityInfo`, not a current-user package lookup;
- keep invalidation, immutable snapshot publication, letter buckets and callbacks otherwise unchanged.

`LauncherUsageStatsStore` (`:27-148`): add `rankForSuggestions`. It reads both existing fields, excludes absent/
zero counts, and returns a new list. Keep JSON schema and `rankForAz` byte-identical. Add a package-private clock/
fixture constructor only if tests need deterministic persistence timing.

`SuggestionBarView` (`:238-260`, `:2619-2646`): one additive cache-budget getter. Preview/detail icons still call
`getRenderedIcon`, `applyIconColorFilter`, `launchEntryFromDrawer` and `bindDrawerAppContextLongPress`; do not
register category icons as dock launch targets.

`AppDrawerAppCellView` (`:58-89`): add a package-local raw geometry overload
`bind(dock, entry, iconPx, rowHeightPx, clickGate)`; the vertical and horizontal overloads delegate to it. Category
detail therefore reuses the same launch/menu/tint/label/unbind behavior without inventing a fake horizontal or
vertical metrics object.

`AppDrawerContentView`:

- add the category view/adapter callback wiring beside current child construction (`:192-258`), using the same top
  and bottom margins as the active app surfaces and zero right margin;
- replace two-way mode logic in `setInteractive`, `setViewType`, `applyViewType`, `applyResults`,
  `submitVisibleResults`, `firstCellView`, `cancelCellLongPresses`, touch routing, nested routing,
  `advanceDrawerFx` and `resetDrawerFx` with exhaustive switches;
- route empty-query catalogue snapshots through the category classifier while preserving the one canonical sorted
  catalogue already pushed at `:458-468`;
- category query shows `mGrid` full-width and submits the ranked results; empty query restores category view;
- generalize overpull translation to the captured active overview/search RecyclerView. Expanded detail uses no
  overpull; its top pull is category collapse;
- map category touch parts to the existing plane `CloseDragGate`, capture state at DOWN, and add the category
  nested branches before the vertical close-policy branch;
- expose `collapseCategoryIfNeeded()` and make `handleBackInDrawer()` consume query first, expansion second;
- on `setInteractive(false)`/reset, cancel category nested state, spring and click gate before views can recycle.

`AppDrawerCloseArmingPolicy` is not modified. The category overview/search routes into its existing public
contract; expanded detail never calls `begin`, `claimOnPreScroll`, or `end`, so it cannot leak arming into the
vertical mode.

`AppDrawerTouchRegions` (`:26-50`) and `AppDrawerPlaneView` are not widened. `AppDrawerContentView.regionAt()`
maps the richer category hit result to existing `GRID` (defer) or `CHROME` (plane) and continues returning COLUMN
only for vertical A-Z.

`AppDrawerController`:

- make `prepareContent()` (`:769-797`) exhaustive and pass category metrics, current drawer radius and cache
  budget to content;
- `doFrame()` already calls `advanceDrawerFx`; category mode returns expansion motion from that same method;
- `onBackPressedInDrawer()` (`:454-463`) calls the new content Back hierarchy;
- `onClosed()` (`:997-1047`) resets category state/adapters inside the existing `try`; geometry restore and the
  `finally` flush remain untouched;
- preference reload still closes immediately before changing mode (`:444-452`). No expansion state survives it.

### 2.3 Preference and resources

- Add `APP_LAUNCHER_DRAWER_VIEW_TYPE_CATEGORIES = "categories"` beside the existing values in
  `TermuxPreferenceConstants.java:193-198`.
- Teach `TermuxAppSharedPreferences.normalizeAppLauncherDrawerViewType()` (`:247-250`) to preserve categories;
  null/corrupt/future stays vertical.
- Add Categories to both arrays (`arrays.xml:41-48`) and its setting label to `strings.xml`; the existing
  `ListPreference` (`launcher_preferences.xml:53-65`) and data-store routes require no new key.
- Add localized names for all 13 buckets, category empty state and accessibility strings. Stable enum/CSV slugs
  are never localized.
- Add the curated CSV raw resource described in section 1.2. No remote config, downloaded taxonomy, database or
  user-editable map enters this slice.

## 3. Expansion and lifecycle state machine

```text
OVERVIEW
  -- small-grid/header tap --> EXPANDING(target=1)
  -- non-empty query -------> SEARCH_RESULTS (category expansion cleared)

EXPANDING
  -- spring settles 1 ------> EXPANDED
  -- Back/header -----------> COLLAPSING(target=0)
  -- query ----------------- > SEARCH_RESULTS immediately

EXPANDED
  -- header/Back -----------> COLLAPSING(target=0)
  -- detail top pull -------> COLLAPSE_DRAGGING
  -- query -----------------> SEARCH_RESULTS immediately

COLLAPSE_DRAGGING
  -- committed release -----> COLLAPSING(target=0)
  -- cancel/short release --> EXPANDING(target=1)

COLLAPSING
  -- spring settles 0 ------> OVERVIEW
  -- expansion retarget ----> EXPANDING(target=1), only if the same bucket still exists

SEARCH_RESULTS
  -- query cleared ---------> OVERVIEW
```

Back while the drawer is open is exactly: clear a non-empty query; otherwise collapse/retarget any category
detail; otherwise return false so the existing activity branch closes the drawer. One press performs one action.

Normal drawer close, `closeImmediate`, HOME/palette/lifecycle stop and a view-type preference change cancel the
category spring/nested stream, reset to overview, clear selected ID and detail adapter, release all category
drawables, then let the existing drawer close/teardown proceed. Category collapse is not animated underneath the
drawer close.

Rotation/configuration recreation follows the shipped behavior: the activity calls the immediate close path,
category state is not persisted, and the next open starts at overview with metrics re-resolved. A host relayout
caused only by the existing system-IME fallback may recompute final category bounds, but it does not recapture
accessory bands or restore an expanded category after a real configuration change.

Package add/remove/change rebuilds all buckets atomically from the newest provider snapshot:

- Preserve the overview's first visible stable category ID and pixel offset where possible.
- If selected category still exists and is non-empty, keep its stable ID/state/progress, replace its detail list,
  and preserve the first visible app stable ID + offset if that app remains; otherwise clamp to position zero.
  A morph already in flight keeps its captured source rect until settle rather than jumping to a re-laid-out tile.
- If the selected bucket becomes empty, immediately abort expansion/collapse to overview, clear its detail views
  and omit the now-empty tile. Do not animate toward a tile that no longer exists.
- A new non-empty bucket enters at its fixed enum position. Empty synthetic/taxonomy buckets remain absent.
- Cancel any context popup/long press before list replacement, so a removed app cannot launch from a recycled
  anchor.

## 4. Data and event flow

```text
LauncherAppDataProvider background snapshot
    -> LauncherAppEntry(category, firstInstallTime)
    -> sorted catalogue -> AppDrawerSearchController (unchanged)
    -> AppDrawerCategoryClassifier
         + raw curated package map
         + LauncherUsageStatsStore suggestions ranking
         + supplied current time for Recently Added
    -> non-empty AppDrawerCategoryBucket list
    -> AppDrawerCategoryTileAdapter

tile small-grid/header tap
    -> category expansion Spring on controller frame loop
    -> bottom-aligned AppDrawerCategoryDetailAdapter
    -> AppDrawerAppCellView
    -> SuggestionBarView launch / long-press / shared icon cache

DOWN on category content -> plane gate defers once
    -> nested scroll
       -> overview: shipped arming policy -> existing plane/controller close path
       -> expanded detail: category collapse only
       -> ordinary list scroll: child consumes

non-empty focusless query
    -> existing ranking engine
    -> existing vertical RecyclerView, full width, rope hidden
```

## 5. Numbered implementation order

1. **Lock contracts with pure tests.** Extend `AppDrawerViewType`; add category enum/bucket, curated-map parser,
   classifier, metrics, touch regions, gesture policy and expansion model. Add preference value/constants and raw
   map validator. No view code yet.
2. **Enrich catalogue metadata.** Add the compatible `LauncherAppEntry` constructor/fields and populate primary +
   profile category/install time in `LauncherAppDataProvider`'s existing worker snapshot. Prove package lookups are
   once per package and no UI bind queries PackageManager.
3. **Suggestions source.** Add/test `LauncherUsageStatsStore.rankForSuggestions` without changing `rankForAz` or
   persistence schema.
4. **Budget and common-cell seams.** Add the read-only icon-budget getter and raw-geometry bind overload. Run all
   B-2/B-3/B-4 cell, cache, launch, popup, vertical adapter and horizontal adapter tests before continuing.
5. **Tile presentation.** Implement custom tile view and tile adapter; validate exact square/header geometry,
   preview ordering, large-icon actions, aggregate 2x2/header expansion, accessibility and complete unbind.
6. **Detail and morph.** Implement detail adapter, bottom-up detail measurement and draw-only morph; then implement
   expansion Spring/state machine with the no-overlap icon staging boundaries.
7. **Category container gestures.** Implement category touch parts, raw-Y observation, click gate and nested
   detail collapse. Do not add interception. Drive expansion frames through the supplied controller listener.
8. **Content integration.** Add the third child and exhaustive mode/search/results/touch/nested/teardown switches
   to `AppDrawerContentView`. Keep overview/search close on the existing arming policy and expanded detail off it.
9. **Controller and Back integration.** Add category metric preparation, Back hierarchy and teardown reset. Leave
   plane geometry, `applyFrame`, accessory choreography, `computeCombinedHeight` and terminal sizing untouched.
10. **Preference/resources.** Add Categories to the existing ListPreference arrays and normalization, all category
    labels/accessibility strings and the curated CSV. Verify live style reload closes/reconfigures as B-4 does.
11. **Full regression and devices.** Run focused JUnit4/Robolectric tests, both app variants' complete unit suites
    and read JUnit XML; build both variants; execute the device matrix in section 7 with frame/memory/resize logs.

## 6. Tests

Pure JUnit4:

- `AppDrawerViewTypeTest` extended: `categories` round-trips; null/empty/unknown remains vertical; existing values
  unchanged.
- `AppDrawerCuratedCategoryMapTest`: shipped file schema, lexical order, package syntax, duplicates, known
  non-synthetic slugs; parser skips one malformed fixture line without dropping valid neighbors.
- `AppDrawerCategoryClassifierTest`: curated override beats declared category; every platform constant maps exactly
  as listed; undefined/future/pre-26 goes Other; one taxonomy membership per stable ID; synthetic overlap allowed
  without within-bucket duplicates; fixed order; empty buckets omitted; zero catalogue yields empty state.
- `AppDrawerCategoryClassifierTest` time cases: 30-day boundary included, just outside excluded, zero excluded,
  <=24h future skew included at age zero, larger future excluded, replacement/update time irrelevant.
- `LauncherUsageStatsStoreTest` extended: suggestions exclude zero-use entries; count/last/label/stable-ID ordering;
  profile stable IDs remain independent; `rankForAz` output remains unchanged.
- `AppDrawerCategoryGridMetricsTest`: 1/2/3-column breakpoints, exact square, header outside root draw rect, 2x2 block
  exactly occupies one large slot, radius clamp, bottom-up list height, font-scale/short-height behavior,
  overflow threshold, finite degenerate results, and `32 * B^2 * estimatedAttached <= 60%` budget.
- `AppDrawerCategoryTouchRegionsTest`: all half-open edges, expanded empty area chrome, header/list content-owned,
  transition body content-owned, outside chrome.
- `AppDrawerCategoryGesturePolicyTest`: action tap stays action; any nested close suppresses it; mid-list stream never
  collapses/closes after reaching top; overview first pull arms/second closes; expanded top down collapses but never
  closes; upward latches scroll; later direction drift cannot switch; non-scrollable overview closes once;
  duplicate stop is idempotent.
- `AppDrawerCategoryExpansionModelTest`: every legal transition, illegal event no-op, staging crossings fire once in
  either direction, Back retarget, committed/cancelled pull, package refresh retain/abort, query reset, teardown.
- Existing `AppDrawerCloseArmingPolicyTest`, `AppDrawerCommitPolicyTest`, `AppDrawerGestureArbiterTest`,
  `AppDrawerPageModelTest` and all rope/section tests remain byte-for-byte green.

Robolectric (`@Config(sdk = P)` for drawer views, plus an API-25 classifier/provider case where needed):

- `LauncherAppDataProviderCategoryMetadataTest`: primary declared category + once-per-package install lookup; profile
  metadata from `LauncherActivityInfo`; lookup failure defaults only that entry; old constructors still work.
- `AppDrawerCategoryTileViewTest`: measured tile is square; heading baseline/bounds are below it; all seven icon
  rectangles match metrics; only first three launch/long-press; any of four small cells hits the single expansion
  action; header expands; missing icons cannot answer; unbind clears drawables/listeners/content descriptions.
- `AppDrawerCategoryTileAdapterTest`: fixed non-empty bucket order, first-seven order, stable IDs, recycled holder
  has no drawable, item cache/prefetch policy prevents eager twelve-tile retention, selected bounds are captured in
  category coordinates.
- `AppDrawerCategoryDetailViewTest`: one/two/many rows stay bottom aligned; large header is immediately above list;
  remaining space is above; overflow scrolls; font scale cannot overlap pill/header/list; item order is row-major.
- `AppDrawerCategoryDetailAdapterTest`: exact shared icon/tint/label/launch/long-press behavior, detail icon size
  equals preview-large key, close/collapse click gate suppresses retained UP, stable-ID anchor and recycle cleanup.
- `AppDrawerCategoryExpansionViewTest`: one house Spring, final layout is not remeasured per frame, morph rect starts
  at selected square/ends at body, staged alphas, overview drawables released before detail bind, reverse order on
  collapse, no bitmap snapshot, reduced motion runs the same finalizers.
- `AppDrawerContentCategoriesTest`: category uses full width and rope/dots/pager are gone; empty query overview;
  query cancels detail and shows flat full-width grid; clearing returns overview; A-Z remains gone; Enter/Back
  hierarchy; empty-state close; package add/remove preservation and selected-empty abort.
- `AppDrawerContentCategoryGestureTest`: plane defers category list/header points; overview nested behavior equals
  vertical first-pull/second-pull; expanded top pull collapses only; expanded empty chrome closes directly;
  mid-list-to-top never changes owner; neutral diagonal cannot action+close; no interception/synthetic cancel;
  fling sign converts once; UP/CANCEL/duplicate stop finalize once.
- `AppDrawerControllerCategoriesTest`: one plane/content/controller/frame callback; category metrics use full width,
  radius and shared cache budget; expansion contributes only `fxMoving`; Back order and immediate preference reload;
  close/rotation resets category; `applyFrame` and captured accessory bands are unchanged.
- `LauncherDrawerViewTypePreferenceTest` extended: XML arrays expose exactly all three values in order, categories
  persists through the existing data store and schedules the same non-recreating style reload, corrupt remains
  vertical.
- `AppDrawerOverlayHierarchyTest` extended: every category view is below the existing plane; no new activity band;
  category open/scroll/expand/collapse/close invokes no accessory-height or terminal-size seam.
- Existing vertical `AppDrawerContentViewTest`/`AppDrawerContentColumnTest`, all B-3 rope/scrub tests, horizontal
  pager/page/dot tests, cell parity tests, package refresh, icon cache, popup/launch and activity Back/intake tests
  remain green in both variants.

## 7. Device verification

1. Run the shipped vertical regression first: scroll-to-top continuity, first spring/second close, A-Z scrub,
   focusless search, launch and popup. Switch horizontal and repeat page/close diagonal matrix and dots.
2. Select Categories through the existing setting and return without process death. Next open has category tiles at
   full width; switching among all three modes leaves no rope margin, pager, dots or invisible touch surface.
3. Inspect several populated tiles: rounded square uses drawer radius, exactly 2 large icons on row one and 1 large
   + 2x2 small on row two, heading centered below/outside. Test a short bucket with invisible unused slots.
4. Tap each large preview to launch; HOME/reopen and long-press it for dock-identical popup. Tap each quadrant of
   the small block and the heading; all expand and none launch a small icon.
5. Expansion at normal and 0 animation scale: source rect begins at the tapped tile, header/list settle at the
   bottom with empty space above, no blank/stale frame at staging boundaries, reduced motion snaps cleanly.
6. Expand a 1-row, multi-row and overflowing bucket. Every non-overflow list touches bottom and grows upward;
   overflow starts at its first app and scrolls without moving the large header.
7. Three-way gesture matrix: overview mid-list down scrolls even through top; top first pull springs/arms; fresh
   second pull closes; expanded mid-list down scrolls; expanded top down collapses only; continuing the same finger
   never closes; fresh empty-space/header-above chrome swipe closes; upward-then-down never changes owner; a slow
   drag from an expand target never expands on the closing UP.
8. Search by just typing with in-app keyboard, hardware keyboard if present and system-IME fallback. Expansion is
   dropped, flat ranked results fill full width, rope remains absent, Enter launches, first Back clears to overview,
   next Back closes. Confirm `TerminalView` never loses focus.
9. Install/remove/change packages while overview, expanding, expanded and searched. New buckets enter fixed order;
   empty buckets vanish; a still-populated selected bucket updates without jumping its morph; removing its final
   app returns immediately to overview; no removed popup/cell launches.
10. Test apps with curated, declared-only and undefined categories plus a clone/work profile. Confirm override
    precedence, profile-aware Suggestions, first-install Recently Added and Other fallback; enable a fresh prefs
    profile to verify empty Suggestions is omitted.
11. Rotate, change font scale, HOME/resume, summon palette, Back through query/detail/drawer and switch view type
    mid-session. Every lifecycle close returns category state to overview and terminal touches work afterward.
12. Compare `gfxinfo framestats` for ten overview scroll/expand/collapse/close cycles to B-4 vertical/horizontal
    controls. Use `dumpsys meminfo` before open, after scrolling all tiles, after expansion and after a second pass:
    heap plateaus inside the 6-16 MiB shared cache behavior and staged expansion shows no one-frame spike.
13. Keep logcat on terminal resize/session-size events throughout. Category scroll, morph and nested collapse must
    produce zero `TerminalView.updateSize()`/SIGWINCH events; after ten cycles, tap/type in the terminal.

## 8. Risks

1. **Category falling through a two-way `else` into horizontal.** The current code has several vertical/else
   assumptions. Missing one can show dots, reserve page state, run the pager close branch or submit category entries
   as pages. Exhaustive switches and mode tests are mandatory.
2. **Shipped vertical arming contaminated by expanded collapse.** If expanded detail calls the shared policy, a
   collapse can arm the next vertical/category overview pull. Keep its pure policy and nested state separate;
   disarm on every mode/search/expansion boundary.
3. **B-4 page/close race reintroduced.** Letting the plane observe a category RecyclerView while content also
   decides nested close, or allowing a retained click after a close claim, produces action+close on one UP. Gate at
   DOWN, one-way decisions and click suppression are the fix.
4. **Vertical rope regression.** Categories must set rope `GONE`, remove its margin, stop its frames and clear scrub;
   returning vertical must restore all four. An inactive-but-visible column creates a dead right strip.
5. **Horizontal state regression during common cleanup.** Category teardown must never stop/reposition the pager
   unless leaving horizontal; horizontal page selection, snap target, click gate and dots must keep B-4 behavior.
6. **Search active-surface mismatch.** Categories search uses the existing grid while the preference still says
   categories. Touch, first-result source, popup cancellation and overpull must use the visible surface, not only
   the enum, or an invisible category view can own gestures.
7. **Icon heap above the LRU.** Evicted drawables remain alive if attached `ImageView`s still reference them. The
   byte formula is insufficient without item-cache zero, unbind-on-recycle and expansion staging.
8. **Cache churn affecting dock/vertical/horizontal.** A complete category scroll introduces small-size keys and can
   evict 48dp entries. The 60% preview reservation, no eager tiles and same-size detail reduce but do not remove the
   re-render hitch risk; device frame/memory comparison decides whether the 40dp cap must be lower.
9. **Main-thread package metadata I/O.** Per-bind `getApplicationInfo/getPackageInfo` would make overview scroll
   stutter. Metadata belongs only in the provider's existing worker snapshot.
10. **Profile metadata leakage.** Querying current-user PackageManager for a work/clone entry silently applies the
    wrong category/install time. Use its `LauncherActivityInfo`; fallback per entry.
11. **Curated map drift.** Free-form labels, wildcards or duplicate package rows make precedence unreviewable. Stable
    slugs, lexical one-line rows and a shipped-resource validator keep it mechanical.
12. **Expansion target recycled or removed.** Never retain a tile view as state. Capture a plain rect + category ID;
    abort immediately if the bucket empties and keep an in-flight source rect immutable otherwise.
13. **List mutation during settle.** Submitting into a RecyclerView while it is carrying a nested stream can double
    stop or leave a stale popup. Cancel interaction/popups, reconcile by stable IDs, then submit/clamp once.
14. **Bottom-alignment implemented as translation/reverse layout.** Either breaks hit bounds/order and can make
    overflow start at the last apps. Custom measurement with a bottom-laid child preserves ordinary adapter order.
15. **Two animation clocks or per-frame layout.** A category-owned animator/Choreographer or `requestLayout()` in
    the morph would drift from the drawer plane and increase jank. One house Spring on the controller loop, final
    layout once, transforms/draw only per frame.
16. **Accessory geometry/SIGWINCH regression.** Treating the large header/detail as a new band, or reusing styling
    reload to animate it, would enter combined-height/terminal resize paths. All category geometry is local to the
    existing plane child.
17. **Preference normalization erases Categories.** Updating arrays/enum without the shared-preference sanitizer
    causes a selection that immediately reads back as vertical. Round-trip tests cover all three layers.
18. **Invisible category view eating terminal touches after close.** Existing plane/content guards remain necessary;
    category transition teardown must additionally cancel its nested stream and set every child non-interactive
    before the host becomes invisible.

## 9. Open questions for the project lead

None. The locked source decision, named bucket set, local-only requirement and existing B-4 selector are sufficient
to choose the storage, precedence, synthetic-bucket semantics, empty-bucket behavior, search fallback and gesture
hierarchy above. The initial curated CSV rows are reviewable product data and can evolve without changing this
design or its schema.
