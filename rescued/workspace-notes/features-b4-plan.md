# B-4 implementation plan — horizontal paginated grid + page dots

Builds on landed B-1 + B-2 + B-3. Owns the horizontal view type and the minimum preference needed to select
it; owns nothing from B-5..B-7 (categories, folders, the full drawer-settings surface).

Survey verified 2026-08-11 against `app/termux-launcher@ffaba992`.

## 0. Verified constraints and reusable seams

| Existing fact | Reuse / consequence for B-4 |
|---|---|
| The drawer host is a full-screen sibling overlay, not an accessory band (`activity_termux.xml:1634-1670`). | Add the pager and dots only below `AppDrawerPlaneView.getContentHost()`; add no activity-layout band and make no call to `AccessoryStackLayoutPolicy.computeCombinedHeight()`, `TerminalView.updateSize()` or any toolbar-height API. B-4 must remain transform-only and cannot produce SIGWINCH. |
| `AppDrawerController` lazily adds one plane to that host (`AppDrawerController.java:532-564`) and builds one `AppDrawerContentView` into the plane (`:578-593`). | Keep one controller, plane, content view, progress spring and teardown path. Horizontal is a mode inside the existing content view, not a second drawer tree. |
| The controller captures geometry once and drives every drawer transform from one `applyFrame()` (`AppDrawerController.java:227-272`, `:850-896`). | Page changes affect only content; they never recapture the dock/accessory geometry or write transition properties. Open/close continues to use the existing `com.termux.app.Spring`. |
| `Spring` is critically damped, clamps/substeps the timestep and snaps for reduce motion (`Spring.java:4-17`, `:46-73`). | No `androidx.dynamicanimation` dependency. The existing drawer-close spring remains the only close animation; page dots do not start another animation loop. |
| The plane asks `CloseDragGate` at DOWN and fully defers owned points (`AppDrawerPlaneView.java:57-76`, `:266-295`); content-driven close reports already converge through `begin/update/endCloseDragFromContent` (`:320-350`). | The pager rectangle is content-owned for its whole stream. A close decided inside the pager travels by nested scrolling into the existing content callbacks and then through these forwarders; the plane never intercepts it. |
| Plane claiming is 1.15x slop while RecyclerView starts at 1.0x (`AppDrawerPlaneView.java:57-66`); B-2 therefore routes grid decisions through `NestedScrollingParent3` (`AppDrawerContentView.java:29-48`, `:775-876`). | No new `onInterceptTouchEvent` claim, synthetic `ACTION_CANCEL`, or `requestDisallowInterceptTouchEvent` contest. B-4 adds a nested-scroll child relay to the horizontal pager and extends the existing parent branch. |
| `AppDrawerGestureArbiter` already supplies a one-way axis latch: downward drawer at `1.15 * slop` and `dy > 1.2 * abs(dx)`; horizontal page at `1.0 * slop` and `abs(dx) > 1.1 * abs(dy)` (`AppDrawerGestureArbiter.java:23-38`, `:138-166`). | Reuse these exact thresholds inside the pager. Do not create a second set of axis constants or infer intent from velocity. |
| Vertical close has a stateful first-overpull/second-pull policy (`AppDrawerCloseArmingPolicy.java:19-34`, `:107-160`) and `AppDrawerContentView` owns its overpull spring (`AppDrawerContentView.java:86-117`, `:790-969`). | This policy remains vertical-only and byte-identical. Horizontal has no vertical scroll and therefore no overpull or 1200ms arming window; its first deliberate down-swipe can close. |
| B-3's touch split makes an active column content-owned and inactive column strip chrome (`AppDrawerTouchRegions.java:8-24`, `:43-50`); the current grid gives the rope real width (`AppDrawerContentView.java:208-228`). | In horizontal mode the rope is `GONE`, inactive, reset, and has a degenerate/null touch frame. The pager takes the full content width, including the former column strip, so there is no invisible A-Z region and no special gesture hole. |
| Search is already focusless: query/results are owned by `AppDrawerSearchController`; the pill merely reports taps/clear (`AppDrawerContentView.java:319-451`, `:629-644`). | Keep the pill and all three B-2 input channels unchanged. Never add an `EditText`; a query repartitions the ranked results into pages and returns to page 0. |
| Cell rendering, launch, and long press are centralized in `AppDrawerAppsAdapter` and delegate to `SuggestionBarView` (`AppDrawerAppsAdapter.java:24-42`, `:178-205`). | Extract the existing cell view/binding into one reusable class, then make both vertical and horizontal adapters call it. Do not duplicate icon, launch, popup, tint, label, or recycle behavior. |
| RecyclerView 1.1.0 is already a direct dependency and Java 11/JUnit4/Robolectric are configured (`app/build.gradle:111-125`, `:150-179`). | Use `RecyclerView`, `LinearLayoutManager` and `PagerSnapHelper` already present in that artifact. Add no ViewPager2 or animation dependency; production remains Java. |
| Launcher settings already use `LauncherPreferencesFragment extends MaterialPreferenceFragment`, the shared preference data store, and XML resources (`LauncherPreferencesFragment.java:34-51`; `launcher_preferences.xml:53-59`). | Add one visible `ListPreference` to the existing App drawer category. Do not build the B-7 settings screen or make the drawer cog functional in this slice. |
| The data store debounces styling propagation for 140ms and calls `requestTermuxActivityStylingOnNextResume()` (`TermuxStylePreferencesFragment.java:205-239`); string preferences route through `putString/getString` (`:425-515`). | Persist the view type through this data store and schedule a non-recreating style reload. `TermuxActivity.requestTermuxActivityStylingOnNextResume()` already broadcasts and records the next-resume reload (`TermuxActivity.java:12603-12617`). |

Two stale anticipatory comments must be corrected when B-4 lands: `AppDrawerContentView.getGrid()` says the
horizontal view will share its pool (`AppDrawerContentView.java:981-985`), and `prepareContent()` says horizontal
must subtract the A-Z width (`AppDrawerController.java:766-772`). The locked B-4 requirement supersedes both:
the A-Z rope is vertical-only, and a full-width page RecyclerView cannot share a pool whose holders are app cells.
It still shares the dock's byte-budgeted rendered-icon cache through the common cell binder.

## 1. Decisions

### 1.1 Pager: full-width page items in a horizontal RecyclerView + PagerSnapHelper

`AppDrawerHorizontalPagerView` is a horizontal `RecyclerView`. Each outer item is exactly one viewport wide and
contains a programmatic `GridLayout` for one page. The page adapter partitions the current result list into
fixed-capacity, row-major pages (`0..columns-1` across the first row, then the next row). Attach one
`PagerSnapHelper`; keep `OVER_SCROLL_NEVER`, `setHasFixedSize(true)`, `setItemViewCacheSize(1)` and no item
animator. On idle, the snap view is authoritative; while dragging, the indicator selects the nearest page once
the leading page crosses 50%.

Why this mechanism:

1. `PagerSnapHelper` snaps the outer full-width item, so one fling advances at most one complete app page and
   a slow release always settles to a page boundary.
2. A single horizontal `GridLayoutManager` would expose app cells, not pages, to `PagerSnapHelper`; making it
   page-aware requires a custom two-dimensional layout manager and snap-distance implementation on the most
   gesture-sensitive surface in the drawer.
3. Legacy `androidx.viewpager` exists in the graph, but it introduces a second pager lifecycle and intercepts
   touch internally; ViewPager2 is not a dependency. Neither improves recycling or nested-scroll arbitration.
4. A `HorizontalScrollView` would eagerly retain every app cell and has no recycler or standard page fling.

The outer recycler retains only the visible page plus one cached neighbour. A rebound page clears unused cells,
and recycled pages call the common cell's `unbind()` so rendered drawables and long-press listeners are released.
No horizontal page effect is added: page motion comes from RecyclerView's standard scroll/snap; drawer opening,
closing and reduce-motion behavior remain the house `Spring` path.

### 1.2 Exact horizontal close-arming rule

Horizontal mode has **per-stream arming, not persisted arming**:

1. A DOWN inside the pager starts `AppDrawerGestureArbiter` in `PENDING` with an all-clear eligibility snapshot.
   It is eligible to close on this same swipe; there is no first overpull, second pull, timer, or scroll-position
   condition.
2. The first move satisfying `dy >= 1.15 * touchSlop` and `dy > 1.2 * abs(dx)` latches `DRAWER_DRAG`. Positive
   `dy` is finger motion down. The latch cannot change for that stream.
3. The first move satisfying `abs(dx) >= touchSlop` and `abs(dx) > 1.1 * abs(dy)` latches `PAGE_SWIPE`. Once
   latched, no later vertical drift may close the drawer. A diagonal inside the neutral cone stays pending; a tap
   stays a tap; an upward drag can never close because the close predicate requires positive `dy`.
4. On `DRAWER_DRAG`, the pager starts a vertical `TYPE_TOUCH` nested scroll and sends each finger delta as
   scroll delta `dyScroll = -deltaRawY` through `dispatchNestedPreScroll`. The content parent consumes it, begins
   the existing content close callback once, and updates from the raw Y already observed by
   `AppDrawerContentView.dispatchTouchEvent`. The pager's layout manager is horizontally disabled for the rest
   of the stream, so diagonal residue cannot move a page underneath a closing drawer.
5. Release velocity uses the existing sign boundary: finger `velocityY` is sent to nested pre-fling as
   `-velocityY`; `AppDrawerCloseArmingPolicy.closeVelocityForNestedFling()` converts it back once before the
   controller's commit policy. A slow UP ends through `stopNestedScroll(TYPE_TOUCH)` with velocity zero; CANCEL
   takes the existing cancel callback. Both are idempotent with the later nested-stop callback.
6. The pager observer calls `super.dispatchTouchEvent()` exactly once and never intercepts, fabricates a cancel,
   or steals the target. On a close claim it cancels pending long presses and exposes a stream-level
   `suppressCellClick` guard to the common cell binder so the child that retained the touch target cannot launch
   on the closing UP.

This avoids the 1.15x/1.0x finger-speed race because the plane defers the pager rectangle at DOWN and the pager's
single arbiter evaluates both axes before its RecyclerView handles that MOVE. It avoids the A-Z conflict by
removing the rope from horizontal layout and touch routing entirely: the former right strip is ordinary pager
space and uses the same one-way axis latch as every other cell.

Chrome remains on the shipped plane path: a down-swipe starting on the pill, side/top margins, or unused bottom
band is decided by `AppDrawerPlaneView` with the same 1.15x/1.2 rule. The content and plane still converge on one
controller callback path.

### 1.3 A-Z and focusless search

- `VERTICAL`: current rope layout, scrub, letter highlight, column width subtraction, touch region and rope
  animation are unchanged.
- `HORIZONTAL`: `AppDrawerRopeColumnView` is `GONE`, `setActive(false)`, `cancelScrub()` and `resetRope()` are
  applied when the mode is selected. No width is reserved; `advanceDrawerFx()` does not advance the rope; the
  section index may still be built for vertical-mode reuse but is never exposed or applied to horizontal cells.
- Search stays focusless and uses the existing pill plus in-app keyboard, hardware keyboard and system-IME
  code-point channels. No focus request and no `EditText` is introduced. Empty query pages the alphabetically
  sorted catalogue; non-empty query pages the ranked results. Every query identity change dismisses popups,
  cancels long presses, clears any vertical scrub, resets horizontal page to 0 and recomputes page count/dots.
- Enter launches result 0 using that result's visible page-0 icon when bound, otherwise the existing nullable
  source fallback. Back continues to clear a query before closing the drawer.

### 1.4 Preference and separate grid-size storage

The only B-4-visible setting is `app_launcher_drawer_view_type`, a `ListPreference` with values `vertical` and
`horizontal`, default `vertical`. Unknown/corrupt/future values parse to `VERTICAL`, preserving the shipped view.
It sits directly below `app_launcher_drawer_enabled` in `launcher_preferences.xml`; the existing
`LauncherPreferencesFragment` and `MaterialPreferenceFragment` render it. No drawer sub-screen and no cog
behavior land before B-7.

Storage added now, UI deferred:

| Key | Type/default | Meaning in B-4 |
|---|---|---|
| `app_launcher_drawer_grid_columns_vertical` | int `0` | Vertical column count; `0 = AUTO`, preserving current width-based 4..6 resolution. |
| `app_launcher_drawer_grid_columns_horizontal` | int `0` | Horizontal columns per page; `0 = AUTO`. Never read from the vertical key. |
| `app_launcher_drawer_grid_rows_horizontal` | int `0` | Horizontal rows per page; `0 = AUTO` from usable height. Never affects vertical row flow. |

These are the three keys B-1 explicitly reserved (`features-b1-plan.md:143-145`). Add constants/defaults and
clamped accessors now so B-7 can expose grid controls without migrating a shared value. Explicit columns clamp
to the existing `AppDrawerGridMetrics.MIN_COLUMNS..MAX_COLUMNS` (4..6); explicit horizontal rows clamp to 2..6
and then to the number that physically fits. B-4 reads the vertical key through an overload whose `0` path is
the existing calculation, and reads only the two horizontal keys for page capacity. B-4 adds no grid-size UI.

`TermuxStylePreferencesDataStore.putString/getString` gains the view-type case; `putString` calls
`scheduleTermuxActivityStylingSync(false)`, whose existing debounced runnable calls
`requestTermuxActivityStylingOnNextResume(context, false)`. `TermuxActivity.reloadActivityStyling()` calls a new
`AppDrawerController.onPreferencesReloaded()` after launcher preferences are applied. That method safely closes
an engaged drawer immediately, reads the new mode, and reconfigures already-built closed content; if content was
never built it remains a no-op and the next `prepareContent()` reads the preference. No activity recreation is
needed.

## 2. Concrete class-by-class design

### 2.1 New classes in `com.termux.app.launcher.drawer`

| Class | Kind | Responsibility |
|---|---|---|
| `AppDrawerViewType` | pure enum | `VERTICAL` / `HORIZONTAL`, persisted values, null/unknown-safe `fromPreference(String)`. B-5 extends this enum rather than adding a parallel selector. |
| `AppDrawerHorizontalGridMetrics` | pure immutable | Resolve columns, rows, cell metrics, usable page width/height and `itemsPerPage` from content bounds, dot band, density, label height and the two horizontal preferences. Degenerate dimensions still yield at least one row/page capacity. |
| `AppDrawerPageModel` | pure | `pageCount(itemCount, itemsPerPage)`, `start/endForPage`, and page-index clamp. Zero results means zero pages and a hidden indicator, not a phantom empty dot. |
| `AppDrawerAppCellView` | `LinearLayout` | The exact icon+label tree currently built in `AppDrawerAppsAdapter`; `bind(dock, entry, metrics, clickGate)`, `setScrubAppearance`, `unbind`. One implementation for vertical cells and horizontal page cells. |
| `AppDrawerHorizontalPageAdapter` | `RecyclerView.Adapter<PageHolder>` | Full-width `GridLayout` page holders; binds a result-list slice row-major via `AppDrawerAppCellView`; clears unused/recycled cells; reports page count and page-0 icon. |
| `AppDrawerHorizontalPagerView` | `RecyclerView` | Horizontal `LinearLayoutManager`, `PagerSnapHelper`, page selection callback, arbiter/velocity observer, vertical nested-scroll relay, horizontal-scroll lock and click suppression for a claimed close. It never overrides interception to take a stream. |
| `AppDrawerPageIndicatorView` | draw-only `View` | Centred bottom dots, active/inactive theme colors, selected page, `Page X of N` content description; compresses radius/gap to available width without allocating per frame. Hidden for 0 or 1 page. |

### 2.2 Existing drawer classes

`AppDrawerContentView` (`:67-70`) gains:

- `setViewType(AppDrawerViewType)`, `getViewType()` and one `applyViewType()` switch. It toggles the vertical grid,
  horizontal pager, dots and rope atomically; resets vertical overpull/arming and scrub when leaving vertical,
  stops pager scroll and nested close when leaving horizontal, and never has both app surfaces visible.
- `setVerticalMetrics(AppDrawerGridMetrics)` (the current `setMetrics` body) and
  `setHorizontalMetrics(AppDrawerHorizontalGridMetrics)`. Keep deprecated/package-local `setMetrics` delegating
  to vertical during the transition so existing tests/callers do not silently address the wrong surface.
- `mHorizontalPager`, `mHorizontalAdapter`, `mPageIndicator`; the pager uses the same top and bottom margins as
  the vertical grid but a zero right margin. Dots are centred inside the existing 64dp bottom band
  (`AppDrawerContentView.java:89-97`), leaving its left edge available to B-7's cog.
- `applyResults()` submits only to the active adapter, preserving list order; query changes reset page 0, while a
  package refresh with the same query preserves and clamps the current page just as vertical preserves scroll.
- `launchFirstResult()`, `firstCellView()`, `cancelCellLongPresses()`, popup dismissal and teardown branch on the
  active app surface. Search model/pill callbacks remain singletons.
- `regionAt()` passes the active app surface bounds to the existing `AppDrawerTouchRegions.resolve()`. In
  horizontal mode it passes no active column, so the full pager is `GRID` in the existing generic sense and the
  plane defers it. No enum/signature change is needed in `AppDrawerTouchRegions`.
- `dispatchTouchEvent`, `onNestedPreScroll`, `onNestedPreFling` and `onStopNestedScroll` branch on the captured
  view type. Vertical executes the current `AppDrawerCloseArmingPolicy`/overpull code unchanged. Horizontal
  accepts only the pager's latched downward nested stream, consumes it immediately as close, and never calls
  `mPolicy.begin/end` or `takeOverpull`.
- `advanceDrawerFx()` and `resetDrawerFx()` run rope/scrub only for vertical; mode exit restores every vertical
  cell to alpha/scale 1 before it can enter the pool.

`AppDrawerAppsAdapter` (`:44-269`) keeps its public adapter and `Cell` API, but `onCreateViewHolder`, binding,
scrub appearance and recycle delegate to `AppDrawerAppCellView`. This is a refactor with vertical behavior pinned
by tests: same `SuggestionBarView.getRenderedIcon`, tint, click launch, long-press binding, label geometry,
content description and recycle cleanup. Horizontal cells always bind scrub strength 0.

`AppDrawerGridMetrics` (`:23-77`) gains
`resolve(contentWidthPx, density, labelHeightPx, requestedColumns)`. `requestedColumns == 0` delegates to the
current `resolveColumns(widthDp)` path; the existing three-argument method delegates with zero, making current
vertical output bit-for-bit identical.

`AppDrawerGestureArbiter.Eligibility` gains `public static Eligibility allClear()`; both
`AppDrawerPlaneView.PLANE_ELIGIBILITY` and `AppDrawerHorizontalPagerView` use it. The arbiter's thresholds,
evaluation order and latch are not changed.

`AppDrawerCloseArmingPolicy` is **not modified**. It remains instantiated once by `AppDrawerContentView` but is
entered only for `VERTICAL`. Its tests are extended to prove horizontal routing cannot spend, inherit, or create
vertical arming.

`AppDrawerPlaneView` is **not modified** except replacing its literal all-clear eligibility construction with
the arbiter factory. Its gate and content forwarders are exactly the B-2 extension point B-4 uses.

`AppDrawerController.prepareContent()` (`:759-779`) reads view type and all three grid accessors on each open,
calls `setViewType` before metrics/bind, subtracts rope width only for vertical, and resolves horizontal metrics
from the full open width and the pager's usable height. It gains `onPreferencesReloaded()` as described above.
`buildContent`, geometry capture, `applyFrame`, accessory bands, `onClosed` and the controller frame loop retain
one content object and one transition. `doFrame()` may receive `fxMoving=false` in horizontal mode; it gains no
pager callback or second clock.

### 2.3 Preferences and resources

- `TermuxPreferenceConstants.TERMUX_APP` beside the drawer constants (`TermuxPreferenceConstants.java:186-197`):
  view-type key/values/default plus the three grid keys/defaults and row bounds.
- `TermuxAppSharedPreferences` beside the current drawer accessors (`TermuxAppSharedPreferences.java:191-213`):
  `get/setAppLauncherDrawerViewType`, `get/set...GridColumnsVertical`,
  `get/set...GridColumnsHorizontal`, `get/set...GridRowsHorizontal`; getters sanitize old/corrupt values.
- `launcher_preferences.xml` App drawer category (`:53-59`): one `ListPreference` with simple summary provider.
- `arrays.xml` (existing ListPreference arrays begin at `:23`) and `strings.xml` (drawer strings at `:199-201`):
  two entries/values, title/summary and page-indicator accessibility text.
- `TermuxStylePreferencesDataStore` string routing (`TermuxStylePreferencesFragment.java:425-515`) and
  `TermuxActivity.reloadActivityStyling()` (`TermuxActivity.java:13065-13105`) wire persistence to the controller.

## 3. State and data flow

```
Launcher ListPreference
    -> TermuxStylePreferencesDataStore.putString
    -> TermuxAppSharedPreferences
    -> requestTermuxActivityStylingOnNextResume(false)
    -> TermuxActivity.reloadActivityStyling
    -> AppDrawerController.onPreferencesReloaded / prepareContent
    -> AppDrawerContentView.setViewType

LauncherAppDataProvider -> AppDrawerSearchController -> one result list
    -> VERTICAL: AppDrawerAppsAdapter + rope/section index
    -> HORIZONTAL: AppDrawerHorizontalPageAdapter -> full-width pages -> dots

DOWN in horizontal pager -> plane gate defers
    -> AppDrawerGestureArbiter
       -> PAGE_SWIPE: RecyclerView + PagerSnapHelper
       -> DRAWER_DRAG: vertical nested pre-scroll
          -> AppDrawerContentView callbacks
          -> AppDrawerPlaneView content forwarders
          -> existing AppDrawerController close Spring
```

The selected view type is sampled at DOWN for gesture routing and is not re-read mid-stream. Preference reload
closes an engaged drawer before changing the mode, so a stream cannot begin vertical and finish horizontal.

## 4. Numbered implementation order

1. **Pure preference and paging contracts first.** Add `AppDrawerViewType`, constants/accessors,
   `AppDrawerHorizontalGridMetrics`, `AppDrawerPageModel`, the requested-column overload, and their JUnit4 tests.
2. **Minimum preference.** Add the arrays/strings and one `ListPreference`; route it through
   `TermuxStylePreferencesDataStore` with `scheduleTermuxActivityStylingSync(false)`. Add preference round-trip
   tests before touching drawer views.
3. **Common cell extraction.** Introduce `AppDrawerAppCellView`, convert `AppDrawerAppsAdapter` to it, and run the
   complete B-2/B-3 adapter, launch, long-press, icon-cache and scrub tests. No horizontal code proceeds until the
   shipped vertical surface is behaviorally identical.
4. **Page presentation.** Implement `AppDrawerHorizontalPageAdapter` and `AppDrawerPageIndicatorView`; test page
   partition/order, full-width sizing, unused-cell cleanup, page count/selection and accessibility.
5. **Pager and nested gesture relay.** Implement `AppDrawerHorizontalPagerView`, reuse
   `AppDrawerGestureArbiter.Eligibility.allClear()`, attach `PagerSnapHelper`, and test the one-way page-vs-close
   latch plus velocity signs without modifying `AppDrawerPlaneView` ownership.
6. **Content integration.** Add mode/metrics APIs and the horizontal children to `AppDrawerContentView`; route
   results, first-result launch, popups, long-press cancellation, touch regions, nested close and teardown.
   Explicitly keep `AppDrawerCloseArmingPolicy` and overpull behind the vertical branch.
7. **Controller integration.** Read preferences in `prepareContent`, use full width/no rope in horizontal,
   add `onPreferencesReloaded`, and call it from `TermuxActivity.reloadActivityStyling`. Do not alter layout XML,
   geometry capture, accessory choreography, `applyFrame`, `computeCombinedHeight`, or terminal sizing.
8. **Regression and device pass.** Run focused tests, then both app variants' unit tests and read the JUnit XML;
   build both variants, run the device matrix below, and compare frame/memory data to a B-3 control.

## 5. Tests

Pure JUnit4:

- `AppDrawerViewTypeTest`: exact persisted values; null/empty/unknown -> vertical; horizontal round-trip.
- `AppDrawerHorizontalGridMetricsTest`: auto and explicit columns/rows, capacity, font-scale/short-height fit,
  row preference never overflows usable height, degenerate dimensions remain finite, vertical/horizontal keys
  produce independent results.
- `AppDrawerPageModelTest`: 0, 1, exact-capacity and remainder page counts; row-major start/end bounds; page clamp
  after result shrink; no phantom page for zero results.
- `AppDrawerGridMetricsTest` extended: three-argument output equals four-argument AUTO output field-for-field;
  explicit vertical columns affect only columns/cell geometry.
- `AppDrawerGestureArbiterTest` extended for the pager contract: vertical wins when eligible at 1.15x/1.2;
  horizontal wins at 1.0x/1.1; neutral diagonal stays pending; page latch survives later vertical drift; close latch
  survives later horizontal drift; up never closes.
- `AppDrawerNestedVelocityTest` extended: downward finger -> negative nested fling -> positive controller velocity,
  with exactly one negation; upward is never offered as close.
- Shared-preference tests: default is vertical; corrupt value sanitizes to vertical; three grid keys round-trip and
  clamp independently; setting horizontal columns never changes vertical columns.

Robolectric (`@Config(sdk = P)`, matching the existing drawer view tests):

- `AppDrawerAppCellViewTest`: vertical and horizontal bindings use identical icon/tint/label/click/long-press
  seams; click gate suppresses the UP of a claimed close; `unbind` clears drawable/listeners/alpha/scale.
- `AppDrawerHorizontalPageAdapterTest`: each holder is viewport width, items are row-major, last-page empties are
  non-clickable/cleared, recycled page releases every cell, page-0 icon lookup is correct.
- `AppDrawerPageIndicatorViewTest`: hidden at 0/1, correct selected dot and content description, width compression
  remains centred and finite, selected index clamps after a filtered-list shrink.
- `AppDrawerHorizontalPagerViewTest`: `PagerSnapHelper` settles exactly one page; slow partial drag snaps; first
  deliberate downward-dominant gesture dispatches vertical nested pre-scroll; page swipe sends no close;
  diagonal/up/tap do not close; horizontal layout is locked after close claim; UP and CANCEL stop once; no
  interception or synthetic cancel is used.
- `AppDrawerContentHorizontalTest`: rope `GONE` and inactive; pager consumes the former column strip and full
  width; plane gate defers pager points; first close swipe needs no arming; no overpull translation occurs;
  vertical policy state remains disarmed; query repartitions and resets page 0; package refresh preserves/clamps
  page; focusless search/Enter/Back remain unchanged; dots match page count.
- `AppDrawerContentViewTest` and `AppDrawerContentColumnTest` extended: selecting vertical restores the exact B-3
  margin, column routing, rope/scrub and first-overpull/second-pull behavior; switching modes restores cell
  alpha/scale and leaves no nested close active.
- `AppDrawerPlaneCloseGateTest` extended: horizontal pager point (including old column X) never calls plane begin;
  chrome still does; null gate remains B-1 behavior.
- `AppDrawerControllerHorizontalTest`: `prepareContent` subtracts column width only for vertical; horizontal uses
  full width and its own grid keys; a style reload safely closes/reconfigures; controller still has one plane and
  one frame callback.
- `LauncherDrawerViewTypePreferenceTest`: XML exposes exactly vertical/horizontal, data-store write persists and
  schedules non-recreating style reload, fragment summary reflects the selected entry.
- `AppDrawerOverlayHierarchyTest`: `app_drawer_host` remains a sibling of the content/accessory root and the new
  pager/dots exist only below the plane; opening, paging and closing do not call terminal size or accessory height
  mutation seams.
- Existing `TermuxActivityBackOrderTest`, `TermuxActivityDrawerIntakeTest`, launch/popup tests, icon-cache tests,
  every B-2 close-policy test and every B-3 rope/scrub isolation test remain green in both variants.

## 6. Device verification

1. **Vertical regression first:** open, scroll mid-list to top, verify the same gesture never closes; first top
   pull springs/arms; second pull within 1200ms closes; rope scrub A -> # never closes; search, launch and popup
   parity remain intact.
2. Select Horizontal in Settings -> Launcher -> App drawer. Return without process death: the next open is
   horizontal, with no activity recreation flash and no A-Z column or reserved right strip. Switch back and
   verify vertical state returns.
3. Page slowly left/right and fling both ways: every release lands on one page, no partial page remains, dots
   select at halfway and settle to the snapped page, first/last pages do not glow/stretch.
4. Verify app ordering across a boundary: last cell of page N and first cell of page N+1 follow the sorted list;
   work/clone entries keep B-3's alphabetical placement. Repeat under an RTL locale: logical page order and dots
   mirror with layout direction, while list order within each logical page stays deterministic.
5. Gesture matrix over a cell: horizontal swipe pages; deliberate down closes on the **first** swipe; horizontal
   then vertical drift still pages; vertical then horizontal drift still closes; diagonal neutral motion does
   neither; upward motion does not close; tap launches exactly once; hold then down cancels the pending popup.
6. Repeat the down-close from the former A-Z strip, the pill, a side margin and bottom band. Pager strip uses
   nested content close; chrome uses plane close; all look identical after the claim.
7. Focusless search with in-app keyboard, hardware keyboard if available, and system-IME fallback: query filters
   live, returns to page 0, dots recalculate, Enter launches result 0, Back clears before close, and no input focus
   leaves `TerminalView`.
8. Filter to 0, 1, exactly one page and one-page-plus-one results. Dots are hidden for <=1 page, last-page cells
   cannot launch stale apps, and a package uninstall on the last page clamps to the new last page.
9. Open a long-press popup, page it off screen, query, close, reopen and switch view type. No popup remains anchored
   to a recycled page and no cell stays dim/scaled or answers for its previous app.
10. Rotate/configure font scale, HOME/resume, palette summon, Back, and `animator_duration_scale 0`. Rotation/HOME
    close cleanly; reduce motion snaps the drawer close through `Spring`; paging still snaps with no ghost surface.
11. Compare `gfxinfo framestats` over ten horizontal open/page/close cycles to a B-3 vertical control. Inspect
    `dumpsys meminfo` after traversing all pages twice: the second pass must plateau, and returning vertical must
    not trigger unbounded icon retention.
12. Keep logcat on terminal resize/session-size events while paging and closing. There must be no per-page or
    per-frame `TerminalView.updateSize()`/SIGWINCH. After ten cycles, tap/type in the terminal to catch an
    invisible pager left touchable over it.

## 7. Risks

1. **Vertical close regression from a shared nested-scroll branch.** If horizontal bypass is tested after
   `mPolicy.begin` rather than before it, one horizontal swipe can arm vertical and make the next vertical open
   close unexpectedly. Capture mode at DOWN and keep begin/end/overpull entirely behind `VERTICAL`.
2. **Plane/pager slop race returning.** Any horizontal `onInterceptTouchEvent` close claim revives the exact
   1.15x-vs-1.0x speed dependency B-2 removed. The gate must own the whole pager rect, and close must be a nested
   pre-scroll relay.
3. **B-3 rope leaking into horizontal.** Leaving the column `INVISIBLE` rather than `GONE`, retaining its right
   margin, advancing its model, or passing its frame to touch routing creates a dead/scrub strip and wasted
   frames. Mode switch resets all four pieces atomically.
4. **Vertical cell behavior drifting during extraction.** Launch/profile fallback, icon tint/cache key,
   long-press pickup, content descriptions, scrub bind/recycle reset and popup anchoring all currently converge in
   `AppDrawerAppsAdapter`; parity tests must land before the horizontal adapter.
5. **Click after close swipe.** Nested scrolling deliberately does not cancel the child touch target. Without the
   stream-level click gate, a slow down-swipe that ends inside the same cell can close and launch it.
6. **Partial-page settle after data mutation.** Query/package changes while RecyclerView is settling can leave a
   removed snap target. Stop scroll, submit, clamp, then position/dot update in that order.
7. **Popup anchored to a recycled page.** Page scroll, query, view switch and close must all dismiss dock-owned
   context popups and cancel page-cell long presses before recycling.
8. **Memory spike from page holders.** A page is a group of cells, so an excessive item cache/prefetch window
   multiplies rendered icons. Cache one neighbour, unbind pages, keep the existing icon cap, and measure plateau.
9. **Full-screen invisible pager eating terminal touches.** `AppDrawerPlaneView.setFrame()` already hides alpha-0
   content (`:198-213`) and controller teardown disables content first (`AppDrawerController.java:973-989`);
   mode-switch code must not re-show a child after teardown.
10. **Accessory geometry/SIGWINCH regression.** Adding dots as a new activity band or responding to page changes
    through styling/layout APIs would pull the plane into accessory math. Both views must remain children of the
    existing content host, with page/dot layout local to it.
11. **Preference key coupling.** Reusing one column key makes a horizontal tuning change unexpectedly alter the
    shipped vertical view. Three independent keys with AUTO defaults prevent that migration trap.
12. **Corrupt/future view value blanking the drawer.** Default and unknown values must select vertical. B-5 adds a
    recognized value to the enum and preference array; it does not change the fallback.
13. **RTL index mismatch.** Layout direction can mirror physical page movement while adapter positions stay
    logical. Indicator selection must come from the snapped adapter position, not an assumed X sign.
14. **Two animation clocks.** A custom dot animator or page spring would drift from RecyclerView snap and the
    controller loop. Dots track position directly; drawer close remains on the existing house spring.

## 8. Open questions for the project lead

None. B-4 defaults to the shipped vertical view, exposes only the required vertical/horizontal selector, uses
AUTO for the B-7 grid controls, and treats the A-Z rope as strictly vertical-only.
