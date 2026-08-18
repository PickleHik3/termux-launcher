# Feature A-3 implementation plan: reachable widget grid and add flow

This plan covers only A-3 on `app/termux-launcher` branch `dev` at commit
`9590be926a43a2d39963da16e10bec2abf73d067`. The reported baseline is 1,328 unit tests with zero
failures in both package variants. A-1 and A-2 are treated as shipped contracts, not code to replace.

The outcome of A-3 is deliberately concrete: entering FULL reveals a real, persisted widget grid;
the user can open an in-pane picker, choose a provider, complete the existing A-1 bind/configure
transaction, and see the configured widget in its reserved cell rectangle. A-3 does not implement
drag, resize, remove, or editable row/column controls.

All work remains Java 11, JUnit4/Robolectric, and the house `com.termux.app.Spring`. No Kotlin,
`androidx.dynamicanimation`, app-widget provider receiver, or second widget-ID lifecycle is added.

## 1. Verified reusable seams and invariants

The references below are from the shipped tree at the commit above.

| Area | Verified seam | A-3 consequence |
|---|---|---|
| Real top-pane hierarchy | `app/src/main/res/layout/activity_termux.xml:72-89` makes `terminal_window_bar_host` the first child of `terminal_content_column`; `terminal_surface_host` is the following weighted child at `:261-293`. | The widget body and picker are children of the existing FULL host. They are not an activity-root overlay and never enter bottom-accessory geometry. |
| Reserved FULL body | `activity_termux.xml:124-154` owns the top clock/media/notification slot; `:163-257` owns the bottom status row. A-2 intentionally leaves the measured space between them empty. | A `WidgetPaneView` fills only that middle body. It derives its top and bottom from these real sibling bounds, so it cannot cover either row. |
| FULL target and motion | `FullStatusBarController.java:62-75` opens from the current height, `:148-160` applies every frame through `FullStatusBarGeometry`, and `:162-174` settles. Its only motion object is the house spring at `:35-39`. | A-3 observes A-2's existing progress/state. It does not own pane height or add another FULL animation. Picker motion, if enabled, is child translation only and also uses `Spring`. |
| Settle-only terminal resize | `FullStatusBarController.java:188-197` brackets the whole transition; the real activity host calls the pane resize bracket at `TermuxActivity.java:11381-11400`. The counting regression is `FullStatusBarTerminalResizeCountingTest.java:40-101`. | Adding/rendering widgets never calls the bracket itself. FULL still emits zero `TerminalView.updateSize()` calls per frame and exactly one at each open/close settle. Adding a widget while FULL is already settled emits zero. |
| Accessory isolation | `AccessoryStackLayoutPolicy.java:7-24` sums only bottom accessory rows; its real caller is `TermuxActivity.java:2712`. `requestAccessoryGeometrySync()` is a separate bottom-stack entry point at `TermuxActivity.java:8545`. | No A-3 class imports or calls either API. The grid/picker never contributes a height to `computeCombinedHeight()` and never requests accessory synchronization. |
| FULL production integration | `TermuxActivity.java:11343-11425` is the single `FullStatusBarController.Host`; `applyFrame()` updates height, outline, top-row progress, and frost at `:11384-11395`. | Add one call there to pass the same FULL progress/settled state to `WidgetPaneController`; do not create a parallel frame callback. The regression test must exercise this real host callback. |
| Existing gesture owner | `StatusBarSwipeLayout.java:70-86` observes but never intercepts child streams. It freezes interactive/nested ownership at DOWN at `:137-155`, recognizes every `AppWidgetHostView` as interactive at `:216-234`, and consumes zero nested distance at `:257-275`. | Third-party widget touch is already a first-class child-owned region. A-3 keeps the host view inside that real hierarchy and does not put a transparent intercepting overlay over it. |
| One-way gesture policy | `StatusBarGesturePolicy.java:25-49` stores an immutable DOWN snapshot and `:65-93` permits only one-way claims. FULL itself is ineligible through `TopStatusBarState.java:18`. | Picker/grid gestures follow the same ownership model. No move-time hit-test may turn a provider-owned stream into a launcher stream. |
| Drawer production guard | `TermuxActivity.java:11494-11498` blocks status entry while other launcher surfaces are engaged; the drawer also reads FULL eligibility in the production activity. | FULL remains mutually exclusive with drawer/palette/tuning. Widget touch cannot open the drawer, and a dock-origin drawer gesture is vetoed while FULL remains engaged. |
| Back order | `TermuxActivity.java:10138-10166` puts FULL ahead of palette, app drawer, dock tuning, and navigation drawer. | A picker is a child modal of FULL: Back first dismisses the picker, the next Back closes FULL to its captured prior state, and only then may lower surfaces consume Back. |
| A-1 transaction entry | `LauncherWidgetHostController.java:130-175` allocates one ID, commits a durable transaction, tries direct bind, and otherwise launches consent. Request codes are fixed at `:30-31`. | Picker selection calls this path exactly once. A-3 extends its transaction with a reserved cell; it does not allocate IDs or launch bind/configure intents itself. |
| A-1 configure/commit path | `LauncherWidgetHostController.java:177-214` routes both results; `:217-256` verifies provider identity, applies `WidgetConfigurePolicy`, launches the host configure helper, and atomically commits ACTIVE. | Reserved placement travels through these same stages. There is no `ACTION_APPWIDGET_PICK`, raw configure intent, or second result router. |
| Per-ID cleanup | `LauncherWidgetHostController.java:259-280` durably enters deletion and invokes `deleteAppWidgetId()`; it never calls `deleteHost()`. | Decline, cancel, unavailable configuration, stale placement, and persistence failure release both the reservation and exactly that ID through the existing abandon path. |
| Host view and size seam | `LauncherWidgetHostController.java:282-315` creates only ACTIVE host views and sends deduplicated committed dimensions through `WidgetSizeOptionsPolicy`. | `WidgetGridView` asks the controller for each host view and calls `onHostSizeCommitted()` once after a cell's actual pixel bounds settle, never on measure/layout frames that did not change size. |
| Crash boundary | `SafeLauncherAppWidgetHostView.java:24-33` explicitly limits containment to `RuntimeException`; guarded update/measure/layout/draw/touch boundaries are at `:74-133`. | A-3 always hosts provider content through this view. New picker/preview guards also catch `RuntimeException` only, never `Throwable`. |
| Current durable format | `LauncherWidgetRepository.java:30-36` is schema v1; synchronous commit-before-snapshot is `:135-141`; record/pending JSON is `:168-220`. `LauncherWidgetRecord.java:13-30` currently has identity/state/options but no cell. | A-3 evolves this one repository to v2 with grid and cell data, preserving its synchronous atomic-write rule and A-1 lifecycle fields. |
| Process/package lifecycle | Widget host construction is `TermuxActivity.java:783`, start/stop is `:898-905` and `:5058-5078`, result routing is `:10308-10316`, and package reconciliation is above the dock guard at `:13084-13096`. | The grid controller binds beside the existing host controller, rebuilds from the repository on start/result/reconcile, and remains independent of whether the dock/app drawer exists. |
| Focusless precedent | `AppDrawerSearchPillView.java:23-34` documents why an `EditText` would steal the terminal's `InputConnection`; it refuses focus/text-editor status at `:229-241`. Activity routes the drawer's keyboard intake without changing editor ownership at `TermuxActivity.java:10020-10046`. | The A-3 picker has no editor and no search field. It stays in the same activity window, never requests input focus, and does not install a keyboard interceptor. |
| Glass/cache contract | The shared pre-blur LRU is capped at three entries at `TermuxActivity.java:675-684`. FULL explicitly uses the status blur radius and existing bitmap at `:4701-4714` and `:4758-4789`. | Widget chrome is transparent/material-on-glass. It adds no blur view, blur radius, wallpaper bitmap, or cache entry; the three-entry LRU remains unchanged. |

## 2. Exact user-visible behavior

### 2.1 Empty FULL state and the `(+)`

FULL retains A-2's centred top clock/media/notification row, bottom status row, outline, and glass. Between those
rows it shows:

- the widget-grid body, currently empty;
- a Material icon button for widget settings at the body's top-right;
- one centred Material tonal button containing the plus icon and the visible label **Add widget**;
- a short secondary line, **Your widgets will appear here**.

The plus is an action, not decoration: its accessibility text is **Add widget**, its minimum touch target is
48dp, and tapping either icon or label opens the picker. There is no empty-state illustration or provider bitmap.
If the device has no app-widget feature, the button is disabled and the secondary line reads **Widgets aren't
supported on this device**; no ID is allocated.

Once at least one placed record exists, the large empty CTA disappears. A compact plus button remains horizontally
centred in a dedicated 48dp action strip above the grid, with the cog at logical end. This strip is outside all
cells, so neither control covers provider content or steals a widget tap. The grid occupies the remainder of the
middle body.

The cog is present in A-3 so the information architecture does not move in A-5. In A-3 it is a real enabled,
accessible button whose production callback shows an in-pane read-only notice such as **Grid size: 6 rows × 4
columns**. A-3 persists and consumes that definition but exposes no row/column controls. A-5 replaces that callback
with the settings UI and owns validation/reflow/confirmation when dimensions change. A-3 does not add disabled
steppers, hidden preferences, or a provisional settings dialog.

### 2.2 Picker presentation

The picker is a modal **child view inside the existing FULL pane**, not `BottomSheetDialog`, `DialogFragment`, a
new activity, the app-drawer plane, or `ACTION_APPWIDGET_PICK`.

`WidgetPickerSheetView` consists of a body-local scrim and a bottom-anchored Material surface. It is laid out once
inside `WidgetPaneView`; opening/closing changes only `translationY` and scrim alpha using one house `Spring`, or
snaps under reduced motion. It never changes `terminal_window_bar_host` height. The sheet is not swipe-draggable:
it closes by Back, its close button, or a scrim tap. Avoiding a second vertical sheet-drag claimant keeps the
picker's `RecyclerView` ownership unambiguous.

This is preferred because:

- a dialog creates another focusable window and can cause the terminal view to lose its window
  `InputConnection`, even without an `EditText`;
- an external/system picker cannot carry A-3's grid reservation and preview/span model through A-1's exact
  transaction without another lifecycle path;
- reusing the app-drawer plane would couple FULL to drawer geometry, Back, blur, and the two B-4 gesture fixes;
- an in-pane child naturally shares FULL's clipping, radius, status blur, insets, and modal lifetime.

The sheet and its item views never call `requestFocus()`, are not text editors, and do not call
`KeyboardUtils.showSoftKeyboard()`, `beginExternalTextInput()`, `onSystemImeRequested()`, or any in-app-keyboard
interceptor. Touch clickability and accessibility focus remain available; neither changes Android input focus.
Opening and closing the picker must leave the same `TerminalView` as the activity's input-focused editor.

**A-3 intentionally has no picker search.** Grouping by app, stable locale-aware sorting, app headers, and a
single vertically scrolling list are sufficient for this slice and avoid inventing a third keyboard-input owner.
If later product evidence requires search, it must use a painted focusless query view and the existing three-channel
keyboard/hardware/committed-text pattern, with explicit arbitration of the activity's single keyboard interceptor;
an `EditText` remains forbidden.

The picker lists enabled HOME-screen providers for every currently accessible user profile. Groups key on
`(profileSerial, packageName)`, so personal and work instances do not merge. Each group shows badged app icon and
app label; each provider card shows provider label, its preview drawable when safely available, and current-grid
text such as **2 × 2 cells** (and **minimum 1 × 1** when resize metadata differs). A missing/broken preview falls
back to a local material placeholder plus provider icon. Preview loading is generation-tokened and sheet-scoped;
recycled rows cannot receive stale images, and no long-lived widget screenshot cache is introduced.

### 2.3 Full-grid failure

Fit is checked before allocating an app-widget ID. If the selected provider's required span is larger than the
grid or no free rectangle of that span exists:

- the picker remains open;
- the card is disabled once the catalog/occupancy snapshot knows it cannot fit;
- an inline Material error reads, for example, **No 2 × 2 space in the 6 × 4 grid. Widget wasn't added.**;
- if no listed provider can fit, the sheet header reads **Grid is full**;
- `LauncherWidgetHost.allocateAppWidgetId()` is not called and persistence is unchanged.

A concurrent/stale revision discovered after allocation is handled as a failed reservation: synchronously delete
that one unpersisted ID, refresh occupancy, and show the same no-space result. A-3 never evicts, overlaps, shrinks,
or silently moves an existing widget to make room. A-4 can free cells through remove/move; A-5 can enlarge the grid.

## 3. Class-by-class production design

### 3.1 Durable model and placement

`WidgetCellRect` (new, immutable value)

- Stores half-open cell coordinates `left`, `top`, `right`, `bottom`; derives column/row spans.
- Rejects non-positive spans and provides value equality only. Grid-bound validation belongs to the policy so a
  decoded value can be diagnosed without constructing a half-valid UI object.

`WidgetGridDefinition` (new, immutable value)

- Stores `rows` and `columns`; A-3's new-install default is **6 rows × 4 columns**.
- Provides the shared safety bounds A-5 must use later, but A-3 exposes no mutation UI.

`WidgetGridMetrics` (new, pure)

- Given the measured body rectangle, action-strip height, edge padding, inter-cell gap, and grid definition,
  returns exact pixel bounds for any `WidgetCellRect`.
- Distributes integer remainder pixels from logical start/top so the final cell ends exactly at the content edge;
  it does not independently round every cell and accumulate seams.
- Converts provider `minWidth`/`minHeight` and API-31 target-cell hints into the smallest current-grid span whose
  actual pixel rectangle contains the requested size. Default widget padding is included once in the outer host
  size calculation; it is not subtracted again when reporting host options.

`WidgetGridPlacementPolicy` (new, pure and production-called)

- Builds a `rows × columns` occupancy bitset from placed ACTIVE and PROVIDER_MISSING records. Missing-provider
  placeholders continue to occupy their cells so another widget cannot be laid over a future recovery target.
- Validates bounds and pairwise non-overlap; supports `canPlace(candidate, ignoredAppWidgetId)` for the A-4 seam.
- Auto-placement scans candidates in logical row-major order (top-to-bottom, start-to-end) and returns the first
  free rectangle at the requested span. It never rotates, shrinks, pushes, or cascades other records.
- Returns a typed result: `PLACED(rect)`, `SPAN_EXCEEDS_GRID`, `NO_CONTIGUOUS_SPACE`, or `INVALID_SNAPSHOT`.
- Is consulted from the real picker-selection path and repository write validation. A test that calls only the
  pure class is insufficient; a real `WidgetPaneController` test must demonstrate the selected provider receives
  the policy's rectangle and that a full grid prevents platform allocation.

`LauncherWidgetRecord` (extended)

- Adds the required `WidgetCellRect cell` for all normal ACTIVE and PROVIDER_MISSING records.
- Retains provider/profile/state/options/render-failure fields unchanged. DELETING retains the last cell until
  per-ID deletion completes, so a process death cannot make that region appear free while cleanup is unresolved.

`WidgetAddTransaction` (extended, not replaced)

- Adds the reserved cell rectangle and grid revision captured at picker tap.
- Carries the same placement through ALLOCATED, consent, BOUND, configure, and COMMITTING. At most one transaction
  remains pending.
- Carries a transient-origin token identifying that the flow began in FULL. The durable token is used to match a
  returned result, but a cold-start reconciliation without a delivered result does not unexpectedly reopen FULL.

`LauncherWidgetRepository` (schema v2)

- Owns grid definition, monotonic revision, records, and the one pending A-1 transaction in one JSON commit.
- Adds `reservePending(expectedRevision, transaction)`, which revalidates occupancy and commits the transaction
  before bind/configure launch, and extends `finalizeActive()` to atomically consume that exact reserved rect.
- Adds an atomic validated layout-update seam for A-4/A-5; A-3 uses it only for migration/repair, not user editing.
- Preserves the current rule that storage commits before the in-memory snapshot changes
  (`LauncherWidgetRepository.java:135-141`). A failed grid/record write never leaks a partially updated snapshot.

### 3.2 Host transaction integration

`LauncherWidgetHostController` (extended)

- Keeps ownership of allocation, bind consent, configuration, provider verification, final ACTIVE commit, and
  per-ID deletion.
- Extends `beginAdd()` to accept the reserved rect, expected grid revision, and initial size options. It checks
  capability/BUSY first, allocates once, then calls the repository's validated reservation. Reservation/storage
  failure immediately deletes the newly allocated ID through the existing cleanup boundary.
- Builds the ACTIVE record from the cell already in `WidgetAddTransaction`; neither picker nor grid inserts an
  ACTIVE record independently.
- Adds a small listener reporting repository change and terminal add outcomes (`READY`, `DECLINED`, `BUSY`,
  `UNSUPPORTED`, `NO_SPACE`, `STORAGE_FAILURE`, `CONFIGURATION_UNAVAILABLE`, `FAILED`). It never exposes raw
  exceptions or lets UI cleanup IDs.
- Continues to catch `RuntimeException` only around app-widget framework/provider calls. Routine flow still never
  calls `deleteHost()`/`deleteAllHosts()`.
- On a committed cell layout, `onHostSizeCommitted()` remains the sole provider-options write. It receives the
  outer clipped host-view bounds once, after layout, and deduplicates with existing `WidgetSizeOptionsPolicy`.

`LauncherAppWidgetHost` and `SafeLauncherAppWidgetHostView`

- Their ID and exception contracts do not change. Every grid cell uses `createHostView()` and therefore the safe
  subclass supplied by `LauncherAppWidgetHost.onCreateView()` at `LauncherAppWidgetHost.java:29-33`.
- No provider preview is inflated into a live host view and no temporary widget ID is allocated for previews.

### 3.3 Catalog and picker

`WidgetProviderCatalogLoader` (new)

- Uses `UserManager`/`AppWidgetManager` to enumerate installed providers for accessible profiles, filtering for
  `WIDGET_CATEGORY_HOME_SCREEN` and currently enabled/available packages.
- Produces immutable `WidgetAppGroup` and `WidgetProviderItem` models on a worker executor; all results carry a
  generation and are discarded after sheet close, profile/package refresh, or activity stop.
- Loads labels/icons/previews behind `RuntimeException` guards. A failed provider resource produces local fallback
  UI rather than failing the catalog.
- Recomputes span/fit annotations whenever measured grid metrics, grid revision, or provider metadata changes.

`WidgetPickerSheetView` and `WidgetPickerAdapter` (new)

- Render the in-pane scrim, close affordance, app group headers, provider cards, previews, spans, disabled/no-space
  state, loading, no-provider, and error states.
- Use one vertical `RecyclerView`; its nested scrolling is left enabled. The surrounding status parent observes
  nested start and consumes zero distance.
- Contain no `EditText`, do not request focus, and do not retain provider drawables after their sheet generation.
- Report a provider selection only. They never allocate IDs, write placement, or start an activity.

### 3.4 Grid and pane UI

`WidgetCellView` (new launcher-owned wrapper)

- Owns one `SafeLauncherAppWidgetHostView` or one local unavailable/error placeholder.
- Uses `clipChildren=true`, `clipToPadding=true`, an exact cell `clipBounds`, and a final `dispatchDraw()` canvas
  clip to `[0,width) × [0,height)`. This last clip is required because provider descendants may use translation,
  elevation, overscroll effects, or bad bounds despite ordinary child clipping.
- Rejects pointer coordinates outside its exact bounds. Provider content fills only the inner content rectangle;
  a small launcher-owned outer gutter remains available for A-4 edit-mode arming.
- Does not intercept or synthesize events inside provider content. It exposes a DOWN hit-classification seam
  (launcher gutter, non-interactive provider area, interactive descendant, nested/scrolling descendant) but A-3
  adds no long-press timer or edit claim.

`WidgetGridView` (new custom `ViewGroup`)

- Is not itself scrollable: the persisted rows × columns are all simultaneously represented in the available
  body. Collection scrolling remains inside provider RemoteViews.
- Measures each cell to the exact `WidgetGridMetrics` rectangle, lays out records in stable row-major/accessibility
  order, and keeps local placeholders for PROVIDER_MISSING/render failures within the same clip.
- Diffs by `appWidgetId`: reuses the controller's cached host view, removes stale wrappers, and never reallocates
  an ID during activity recreation.
- After an actual width/height change, posts one committed-size callback per affected ID after layout. It does not
  call it on every FULL frame; the grid is interactive/visible only at FULL settle.

`WidgetPaneView` (new)

- Is a child of `terminal_window_bar_host`, drawn above the existing glass but below the elevated top slot/status
  row. It clips its action strip, grid, scrim, and picker to the measured middle body.
- Owns the large empty CTA, compact action strip, cog, inline notices, grid, and picker. It has no authority over
  FULL height, terminal geometry, blur, or Back outside its own picker.

`WidgetPaneController` (new coordinator)

- Binds the pane, repository snapshot, catalog loader, `LauncherWidgetHostController`, grid metrics, empty state,
  picker, notices, and add-flow return behavior.
- At selection, asks `WidgetGridPlacementPolicy` for a rect, derives initial options from that exact pixel rect,
  marks the current FULL/prior state as the transaction origin, and invokes the extended A-1 `beginAdd()` once.
- Dismisses the picker before an external consent/configure activity. When the final matching result returns, it
  reopens/restores FULL after measurement if this live activity initiated the transaction, renders READY at the
  reserved rect, or renders the appropriate failure notice with no tile. It does not reopen FULL merely because
  a stale pending transaction was found on a later cold launch.
- Handles picker Back before delegating to A-2 FULL Back. `onStop()` cancels catalog/preview work and picker motion;
  it does not stop/delete the A-1 host itself.

`TermuxActivity` and `activity_termux.xml` (integration only)

- Add the middle-body `WidgetPaneView` to the real status host and construct its controller after both the widget
  host and FULL controller are available.
- Pass existing FULL progress/settle state from the real `FullStatusBarController.Host.applyFrame()` call site.
- Route Back as picker-within-FULL, then FULL, then the unchanged lower order. Route 4714/4715 only through the
  existing widget controller, whose listener refreshes the pane.
- Forward start/stop/package refresh without introducing another receiver. Do not touch accessory layout methods,
  drawer-plane hierarchy, status blur selection, or pre-blur cache capacity.

## 4. Exact add/configure/first-placement flow

1. The user taps a fit-capable provider card. `WidgetPaneController` freezes the current grid revision and metrics,
   asks the production `WidgetGridPlacementPolicy` for the provider's default span, and obtains one row-major rect.
2. It converts that rect's actual pixel size to the initial A-1 options and calls the extended
   `LauncherWidgetHostController.beginAdd(...)`. No picker class allocates an ID.
3. A-1 checks feature support and the single-pending gate, allocates one ID, and synchronously persists the existing
   `WidgetAddTransaction` plus its reserved cell/revision. If this commit fails or the revision now collides, A-1
   deletes that one allocation immediately and reports storage/no-space failure.
4. A-1 calls `bindAppWidgetIdIfAllowed()` as today. If false, it commits WAITING_FOR_BIND_CONSENT and launches
   `ACTION_APPWIDGET_BIND` with request code 4714 and the persisted ID/provider/profile/options. The picker closes.
5. A valid consent result is verified against the persisted provider/profile and continues through
   `WidgetConfigurePolicy`. Decline/cancel enters the existing two-phase per-ID deletion, clears the reservation,
   and eventually shows **Widget wasn't added**. No cell or error tile remains.
6. If configuration is required, A-1 commits WAITING_FOR_CONFIGURATION and calls
   `LauncherAppWidgetHost.startAppWidgetConfigureActivityForResult(..., 4715, options)` exactly as it does now.
   Missing/blocked configure activity, cancel, mismatch, or synchronous `RuntimeException` deletes the one ID and
   releases the reservation. Optional+reconfigurable configuration continues to use the existing A-1 policy.
7. If no configuration is required, or after a verified `RESULT_OK`, A-1 atomically changes the same transaction to
   one ACTIVE `LauncherWidgetRecord` with the same cell and clears pending. If final persistence fails, it abandons
   and deletes the ID rather than displaying an untracked widget.
8. For an immediate READY result, the grid diffs immediately. For an external flow, A-2's ordinary `onStop()` may
   close FULL; after the final matching result, the live origin token restores FULL and its prior state, then the
   grid creates the safe host view at the already committed rect. The picker itself stays closed.
9. After the cell has real bounds, `WidgetGridView` calls `onHostSizeCommitted()` once. Provider options are not sent
   per layout frame and do not participate in pane/terminal resizing.
10. If the activity/process dies, the transaction still contains its ID/provider/profile/options/cell/stage. A-1
    reconciliation resumes or expires it exactly as shipped. A cold launch does not auto-open FULL; the next user
    entry renders any successfully committed record in its reserved cell.

This is one transaction from picker tap to first placement. There is no provisional ACTIVE row, second pending
store, or UI-owned cleanup path.

## 5. Occupancy, collision, and sizing algorithm

The authoritative unit is a half-open cell rectangle. For a 4-column × 6-row grid,
`{left:1, top:2, right:3, bottom:4}` occupies columns 1-2 and rows 2-3 and has span 2 × 2.

Snapshot validation proceeds in stable record order:

1. Allocate a `rows * columns` bitset.
2. For every placed ACTIVE, PROVIDER_MISSING, and unresolved DELETING record, require
   `0 <= left < right <= columns` and `0 <= top < bottom <= rows`.
3. Visit each cell in the rectangle. An already-set bit is a collision and makes the whole snapshot invalid; no
   later-wins rule is allowed.
4. Mark every cell only after the rectangle itself validates.

Provider span calculation uses the current measured grid, not a hard-coded dp-to-cell formula:

1. Resolve provider desired/minimum outer width and height from `AppWidgetProviderInfo`; honor API-31 target-cell
   hints when valid, while never going below the provider minimum.
2. Ask `WidgetGridMetrics` for the smallest column span and row span whose exact pixel bounds contain those values.
3. Clamp neither dimension. If no such span exists, report `SPAN_EXCEEDS_GRID` and disable the card.
4. Display that default span in the picker. A-4 may later use `minResizeWidth`, `minResizeHeight`, `resizeMode`, and
   max-resize metadata for handle constraints; A-3 does not resize on add below the default required span.

Auto-placement loops `top = 0..rows-spanRows`, then logical `start = 0..columns-spanColumns`, checking all bits in
the candidate. The first entirely clear rectangle wins. RTL affects pixel mapping and visual scan direction, not
the persisted logical coordinates. This deterministic first-fit makes process-death replay and tests stable.

All normal mutations are compare-and-commit against the repository revision. A stale collision never overwrites
the winner. A-4's future move/drop can call the same policy with the moving ID ignored; collision returns a rejected
drop/snap-back result rather than pushing neighbors. A-5's future dimension change must supply a complete validated
layout in one atomic repository commit.

## 6. Persistence schema and migration

Schema v2 remains one JSON value in `launcher_widget_repository/state`:

```json
{
  "version": 2,
  "revision": 7,
  "grid": { "rows": 6, "columns": 4 },
  "records": [
    {
      "id": 41,
      "provider": "com.example/.ClockProvider",
      "profile": 0,
      "state": "ACTIVE",
      "cell": { "left": 0, "top": 0, "right": 2, "bottom": 1 },
      "options": { "appWidgetMinWidth": 180, "appWidgetMinHeight": 84 },
      "failure": null
    }
  ],
  "pending": {
    "token": "uuid",
    "id": 42,
    "provider": "com.example/.AgendaProvider",
    "profile": 0,
    "stage": "WAITING_FOR_CONFIGURATION",
    "cell": { "left": 2, "top": 0, "right": 4, "bottom": 2 },
    "gridRevision": 7,
    "options": {},
    "started": 1786512000000
  }
}
```

`pending` is omitted when absent; `failure` is omitted when null. Existing A-1 options keys, API-31 `SizeF` list,
transaction token/stage/time, provider, profile, and state retain their current meanings. Unknown future fields are
ignored. Unknown future major versions are not overwritten.

Migration is explicit and ID-preserving:

- Missing/empty state becomes v2 revision 0 with the 6 × 4 default.
- Valid v1 is decoded with every A-1 lifecycle field intact. A-1 shipped no reachable picker, so the expected
  production v1 record/pending set is empty. Defensive harness-created records are assigned deterministic 1 × 1
  cells in v1 array order. If there are more than 24, migration increases only the row count enough to retain every
  ID rather than dropping/overlapping one. A legacy pending transaction receives the next free cell and keeps its
  stage/token/time. The complete v2 snapshot is committed once.
- The 1 × 1 fallback is intentional: v1 contains no position or span, so inventing a provider-sized former layout
  would be false. On first measured render, current provider metadata may enlarge a legacy cell only into contiguous
  free cells through the same placement policy and one atomic commit; otherwise it remains clipped and receives a
  local **Needs more grid space** placeholder until A-5 can change the grid. Its system ID is retained.
- If migration write fails, the repository keeps the decoded v1 identity snapshot in memory, does not advance a
  pending transaction into ACTIVE, and retries on the next start. It never clears records merely because schema
  upgrade failed.
- Duplicate IDs, invalid providers, impossible cells, overlap, or malformed JSON are treated as recovery, not an
  empty launcher. Compare `LauncherAppWidgetHost.getAppWidgetIds()` with all safely decoded IDs, query provider
  identity, and reconstruct recoverable IDs into first-fit cells in one v2 commit. IDs that the host owns but the
  framework says are no longer bound are deleted individually. If recovery cannot be persisted, retain/retry the
  per-ID recovery set; never call `deleteHost()` and never silently forget owned IDs.
- Every v2 normal write revalidates grid bounds, collisions, identity ownership, and the pending reservation before
  serialization. A bad candidate write returns failure without replacing the last good durable/in-memory snapshot.

A-5 owns user-driven grid changes. In particular it must decide the UX for shrinking below occupied rects, preview
reflow, confirmation, and updating size options. A-3 supplies the schema, revisioned atomic API, validation, and
current read-only cog summary only.

## 7. Exact touch and gesture arbitration

### 7.1 Third-party widget content

On ACTION_DOWN, the real `StatusBarSwipeLayout` recursively sees the containing `AppWidgetHostView` and immediately
freezes `CHILD_OWNED`; FULL also makes status entry/swipe ineligible. It never intercepts later. Therefore:

- provider buttons/PendingIntents receive their complete stream;
- collection widgets receive tap, drag, fling, overscroll, and nested callbacks normally;
- `StatusBarSwipeLayout` may observe nested scrolling but its pre/post consumed arrays remain unchanged (zero);
- diagonal drift cannot become a status swipe or FULL long-press;
- a provider view changing clickability/visibility after DOWN cannot change ownership for that stream.

`WidgetGridView` itself has no pan/close gesture. `WidgetCellView` dispatches an inside-cell provider stream directly
to the host view and never starts a launcher claim later. Canvas and hit clipping both use the same exact cell
bounds, so translated/elevated provider content cannot paint or receive touches in a neighbor cell or action strip.

### 7.2 Picker

A DOWN inside the sheet freezes sheet ownership; a DOWN inside its `RecyclerView` freezes content ownership. The
sheet has no drag-to-dismiss recognizer, and the status parent is in FULL/child-owned state, so list scroll cannot
also close FULL. A DOWN on the body-local scrim freezes scrim ownership and dismisses only on an UP still within
slop. A DOWN outside the entire FULL pane cannot reach the sheet because the pane/body clip rejects it.

Back dismisses in this order:

1. picker sheet;
2. FULL to its captured COMPACT/EXPANDED prior;
3. palette, app drawer, dock tuning, navigation drawer in the existing order.

### 7.3 Seam left for A-4 edit mode

A-3 does **not** attach `OnLongClickListener` to an `AppWidgetHostView`, schedule an edit timeout, intercept a
provider stream, show handles, drag, resize, remove, or call size options for a drag frame.

It leaves these explicit seams:

- stable launcher-owned `WidgetCellView` wrappers and an outer gutter that is not provider content;
- a DOWN hit classification that can distinguish launcher gutter/non-interactive content from clickable,
  long-clickable, and scrolling/nested provider descendants;
- exact cell/pixel mapping, `canPlace(..., ignoredId)`, typed collision results, revisioned atomic layout mutation,
  and the existing committed-size callback.

A-4 must freeze that classification at DOWN. Launcher gutter or genuinely non-interactive provider background may
be launcher-owned from DOWN and arm edit long-press. A DOWN on an interactive or scrolling descendant is
provider-owned forever and may not enter edit mode after a timeout. Once edit mode already exists, handles are
launcher-owned siblings outside the host content; drag/resize uses nested-scroll-aware one-way claims and commits
only on drop/release. This preserves standard long-press behavior where safe without stealing collection scrolls or
provider long-clicks.

### 7.4 Dock, drawer, and terminal below

FULL's real measured host ends at the accessory boundary. Widget and picker streams are clipped above that edge;
the dock/keyboard below cannot receive them. Conversely, a gesture that starts on the dock retains the drawer's
existing frozen DOWN snapshot, but production eligibility sees FULL and vetoes opening. No A-3 code forwards,
redispatches, or synthesizes terminal/dock events. Closing FULL restores the terminal and dock's existing touch paths.

## 8. Explicit slice boundaries

### A-3 owns now

- reachable empty state and add affordance;
- in-pane grouped provider picker with preview/span/fit state and no text input;
- automatic first placement, occupancy validation, collision rejection, full-grid feedback;
- safe clipped host-cell rendering and provider-missing placeholder occupancy;
- schema v2 with grid dimensions and per-widget/pending cell rectangles;
- one A-1 transaction from selection through consent/configure/commit/cleanup;
- the visible cog, read-only current-grid summary, and a production callback seam for A-5;
- A-4-ready cell wrappers, hit classification, geometry, collision, and atomic mutation seams.

### Deferred to A-4

- entering/exiting edit mode by long press;
- drag shadow, move preview, collision UI/snap-back, resize handles and provider min/max resize constraints;
- remove target, confirmation/undo, haptics, edit-mode Back behavior;
- calling `onHostSizeCommitted()` at resize release;
- any edit-mode spring animation.

### Deferred to A-5

- editable row and column controls behind the cog;
- allowed range/presets, live grid preview, and accessibility wording for those controls;
- shrink/reflow policy and user confirmation when occupied rects no longer fit;
- committing changed dimensions and updating every affected provider's options.

A-3's default/persisted dimensions are not a hidden A-5 preference UI. The cog's read-only summary prevents a dead
control while keeping all row/column mutation decisions in A-5.

## 9. Numbered implementation order

1. **Characterization first.** Add/retain real-call-site tests for empty A-2 FULL body bounds, Back order, status
   child ownership, populated-host terminal resize counts, accessory isolation, status blur radius/LRU count, and
   A-1 decline/configure cleanup. Do not begin with new pure policies alone.
2. **Schema/value layer.** Add `WidgetCellRect`, `WidgetGridDefinition`, v2 record/transaction fields, exact JSON,
   revisioned atomic commits, strict validation, v1 migration, and owned-ID recovery tests.
3. **Placement/metrics.** Implement `WidgetGridMetrics` and `WidgetGridPlacementPolicy`; pin deterministic first-fit,
   pixel remainder distribution, provider-span conversion, collision/full outcomes, and A-4/A-5 mutation seams.
4. **Extend the existing A-1 transaction.** Thread cell/revision through `beginAdd`, consent, configure, commit,
   cancellation, persistence failure, process-death reconciliation, and listener outcomes. Keep request codes and
   per-ID deletion unchanged.
5. **Clipped grid rendering.** Add `WidgetCellView` and `WidgetGridView`, real safe-host creation, placeholders,
   stable diff/recreation, exact draw/touch clipping, and one post-layout options commit.
6. **Catalog.** Add profile-aware provider enumeration, grouping/sorting, safe preview/icon fallback, generation
   cancellation, current-grid span/fit annotation, and package/profile refresh.
7. **In-pane UI.** Add `WidgetPaneView`, empty/unsupported/full states, large and compact plus affordances, cog
   summary, notices, picker/adapter/scrim, focus isolation, nested list scrolling, Back behavior, and house-spring
   child transforms.
8. **Production integration.** Construct `WidgetPaneController` beside the shipped host/FULL controllers; feed it
   real A-2 progress/settle state, lifecycle, result outcomes, and package reconciliation. Restore FULL only for a
   matching live external add result. Do not touch accessory or drawer geometry.
9. **Real behavior regressions.** Drive provider selection through the real picker/controller into the real A-1
   call site; drive widget touch through the real `StatusBarSwipeLayout`; render malicious out-of-bounds content;
   prove picker Back then FULL Back; prove full grid allocates no ID.
10. **Verification.** Run focused tests, then `./gradlew testDebugUnitTest` for each configured package variant,
    inspect all JUnit XML for failures/errors/skips, and require the 1,328-test baseline plus A-3 tests. Build both
    debug variants and complete the physical-device matrix below before calling the slice done.

## 10. Unit and Robolectric test plan

### 10.1 Pure JUnit4

1. `WidgetGridPlacementPolicyTest`
   - Asserts user outcomes: a requested 2 × 2 widget lands in the first visible free 2 × 2 region; holes too narrow
     are skipped; full/noncontiguous grids return no placement; existing widgets never move; provider-missing and
     deleting cells remain unavailable; ignored-ID validation supports a future move without allowing overlap.
2. `WidgetGridMetricsTest`
   - Asserts every persisted cell maps inside the visible middle body, adjacent cells share no painted pixels,
     remainder pixels reach the final edge, RTL mirrors visual placement without changing persisted coordinates,
     and provider span labels match actual rectangles at multiple densities/body sizes.
3. `LauncherWidgetRepositoryV2Test`
   - Asserts exact grid/cell/pending round-trip, revision increments, atomic reservation/finalize, collision and
     stale-revision rejection, storage failure preserving the old snapshot, and immutable returned values.
4. `LauncherWidgetRepositoryMigrationTest`
   - Asserts empty/v1 migration, stable 1 × 1 row-major legacy placement, row expansion without dropping IDs,
     pending stage/token retention, one atomic write, retry after failed write, corrupt overlap recovery, and no
     `deleteHost()` recovery path.
5. `WidgetProviderCatalogLoaderTest`
   - Asserts personal/work grouping, HOME category filtering, locale-stable labels, badged identity, preview/icon
     fallback after `RuntimeException`, stale generation suppression, and fit/span refresh after metrics revision.
6. Existing A-1 policy tests are extended so every bind/configure outcome preserves or releases the reserved cell
   together with the ID; `RuntimeException` is contained and an injected `OutOfMemoryError` still escapes.

### 10.2 Robolectric and real production call sites

1. `WidgetPaneEmptyStateIntegrationTest`
   - Inflate the real `TermuxActivity`, settle the real FULL controller, and assert the top slot and bottom status
     row remain visible, the **Add widget** CTA is centred in their actual intervening bounds, the cog is at logical
     top-right, and neither draws over the terminal/status rows. This must not use a synthetic substitute host.
2. `WidgetPickerInputFocusIntegrationTest`
   - Focus the real `TerminalView`, open the picker from the real plus button, and assert the same activity window
     and terminal input focus remain; no descendant is an `EditText`/text editor; no system IME or accessory geometry
     request occurs; closing restores identical state.
3. `WidgetPickerProductionSelectionTest`
   - Tap a real adapter card with an injected platform boundary and assert the production pane controller consults
     `WidgetGridPlacementPolicy`, the real A-1 controller allocates once, and the durable transaction receives the
     displayed span/rect/options. This is the required anti-unwired-policy test.
4. `WidgetPickerFullGridIntegrationTest`
   - Fill the real repository/grid, open the real picker, and assert **Grid is full**, disabled cards, no platform
     allocation/bind call, no record movement, and the picker remains open.
5. `WidgetAddPlacementRoutingTest`
   - Through the real activity result route, assert direct bind/no-config, bind consent, mandatory configure, and
     optional configure all finalize the same reserved rect; consent/config cancel and unavailable configure delete
     exactly one ID and show no tile; foreign/stale results neither place nor delete an active widget.
6. `WidgetAddExternalReturnIntegrationTest`
   - Start from real FULL, launch simulated consent then configure, allow `onStop()` to close FULL, deliver the final
     matching result, and assert FULL returns once with its captured prior and the widget appears at the reserved
     rect. A cold controller recreation with only stale pending state must not auto-open FULL.
7. `WidgetGridHostViewIntegrationTest`
   - Persist ACTIVE records, recreate the activity, enter FULL, and assert safe host views reuse IDs without new
     allocation; post-layout size options fire once at exact outer cell size and an unchanged relayout fires zero.
8. `WidgetCellHardClipTest`
   - Render a provider child translated/elevated beyond its cell plus a collection overscroll drawable into a
     bitmap and assert pixels outside the cell are untouched. Send an out-of-cell touch and assert the provider does
     not receive it. This tests the product defect, not merely `clipChildren=true` source text.
9. `WidgetRemoteViewsGestureIntegrationTest`
   - Put a clickable provider child and a scrolling collection inside a real safe host/cell/status hierarchy. Send
     tap, drag/fling, diagonal drift, second pointer, and nested scroll; assert provider callbacks receive the one
     complete stream, status callbacks receive none, parent nested consumption stays zero, and a mid-stream view
     state change cannot reclassify ownership.
10. `WidgetPickerGestureIntegrationTest`
    - Assert list drag scrolls only the list, scrim tap dismisses only the picker, a moved scrim stream does not
      dismiss, and no picker gesture closes FULL or opens drawer/dock behavior.
11. `WidgetPaneBackOrderTest`
    - In the real activity, first Back closes picker while FULL remains; second closes FULL to the captured state;
      palette/drawer/tuning/navigation order is unchanged when picker/FULL are absent.
12. `WidgetPaneGeometryIsolationTest`
    - Open/close FULL with populated widgets and the picker, and extend
      `realPanesDeliverOnlyOneTerminalAndPtyResizeAtEachSettle`: zero terminal/PTY resize on every frame, one at each
      settle, zero for add/render/picker operations while settled, and unchanged accessory apply timestamp.
13. `WidgetPaneGlassIntegrationTest`
    - Assert the existing backdrop/blur/surface IDs remain the only glass, status radius is used, the pre-blur LRU
      capacity remains three, and repeated picker/grid updates add no blur key or wallpaper bitmap.
14. `WidgetProviderMissingCellTest`
    - Reconcile an uninstall through the real package/host callback and assert the ID follows A-1 deletion, a local
      clipped unavailable placeholder remains at the persisted rect, and auto-placement treats it as occupied.
15. `WidgetPaneAccessibilityTest`
    - Assert meaningful Add/settings/close/provider/span/full/error descriptions, 48dp launcher controls, stable
      grid traversal order, and no duplicate accessible provider content outside cell bounds.

Every new policy test is paired with a production integration assertion. Source-string tests may guard forbidden
calls, but they do not substitute for user-visible real-hierarchy behavior.

## 11. What only a physical device can verify

Robolectric cannot establish the following; each is a release gate on the dedicated Android phone, in both package
variants where applicable:

1. The real system bind-consent UI from an ordinary non-privileged install, including allow/decline, OEM wording,
   work-profile badging, and the absence of leaked host IDs after every result.
2. Real provider configure activities, including bind-then-configure chaining, cancel, app switch, rotation,
   launcher process kill while external UI is open, malformed/missing returned extras, and return to FULL with the
   configured widget in its reserved first cell.
3. Direct binding after system approval versus consent-required binding on a clean install; both must use the same
   transaction/placement and produce one ID/record.
4. Binder-delivered `RemoteViews` inflation/partial updates, provider resource APK boundaries, PendingIntent clicks,
   collection `RemoteViewsService` scrolling/flinging, overscroll, and OEM `AppWidgetHostView` behavior.
5. A representative provider matrix: clock, weather, calendar/agenda collection, media controls, a mandatory-config
   widget, an optional-config widget, no-preview provider, work-profile provider, and deliberately malformed test
   provider. Preview span and actual first placement must agree.
6. Draw/touch clipping under hardware acceleration: translated/elevated content, ripple, outline shadow, collection
   edge effects, and provider animation must never paint or hit outside a cell.
7. Gesture arbitration at real touch timing: tap/long-hold/diagonal/multitouch inside buttons, blank provider area,
   collection rows, cell gutter, picker list, picker scrim, status row, terminal, and dock. Each stream has exactly
   one owner; widget collection scroll never enters status/edit/drawer behavior.
8. Terminal input ownership with the software IME and embedded keyboard: open/close picker repeatedly, complete
   external bind/configure, type immediately afterward, and confirm no lost/frozen `InputConnection`, unexpected
   IME, keyboard relayout, or swallowed terminal key.
9. Real app-widget ID survival across activity recreation, force-stop, process kill, reboot, launcher upgrade, provider
   update/uninstall/reinstall, and profile lock/unlock. No duplicate, orphan, or silently rebound ID is acceptable.
10. Provider `onAppWidgetOptionsChanged()` receives the exact post-layout dp size once after first placement, with
    no storm during FULL motion, picker motion, RemoteViews updates, or unchanged relayout.
11. Default and Rounded styles with wallpaper passthrough/live blur/no blur, minimum/maximum status blur radius,
    reduced motion, gesture navigation, display/font scales, portrait/landscape, keyboard shown/hidden, and short
    available FULL bodies. Glass continuity and the three-entry pre-blur plateau must remain unchanged.
12. Trace/log ten FULL open/close cycles with empty grid, populated grid, scrolling collection, and picker. Verify
    exactly one `TerminalView.updateSize()`/PTY resize per settle, zero per frame, zero on add/picker/render at settled
    FULL, no accessory sync, stable bottom terminal row, and no frame/memory growth after warm-up.
13. Fill the grid with mixed spans. Confirm disabled picker cards/no-space wording, zero consent dialog and zero ID
    allocation on failure, then verify existing widgets remain exactly where they were.
14. Accessibility service/TalkBack traversal and activation for empty CTA, cog summary, grouped picker, disabled
    no-fit cards, provider RemoteViews, collection items, close, and Back order; accessibility focus must not become
    terminal text input focus.

## 12. Regression risks and containment

| Surface | What could regress | Containment |
|---|---|---|
| Terminal | Child attachment/requestLayout could accidentally trigger extra terminal rows/columns, lose the bottom row, or leave terminal input focus behind the picker/external flow. | Grid is a fixed child of the already-sized host, visible only at FULL settle; add/picker uses transforms only; preserve terminal focus; extend the real counting test with populated/add cases and device-log PTY events. |
| Accessory stack/keyboard | Reusing drawer/IME helpers could call `requestAccessoryGeometrySync()`, change bottom bands, or make FULL target chase A-3 UI. | In-pane focusless sheet, no editor/IME helper, no accessory APIs, and a counter-based integration test proving unchanged accessory apply time. FULL follows only A-2's measured parent seam. |
| Dock | Widget/picker vertical gestures could leak to dock, or the cog/add flow could enable the drawer under FULL. | Exact body clip, no event forwarding, existing FULL production veto, frozen DOWN claims, and real dock-origin/widget-origin gesture tests. |
| App drawer | Reusing its plane/search/controller could revive B-4 touch stealing, disturb query/folder interceptor ownership, or change Back order. | Reuse no drawer UI/controller; picker is a separate in-pane child with no search/interceptor. Run the complete drawer gesture/search/folder/Back suite unchanged. |
| A-2 FULL | A second geometry owner, child animation, or wrong visibility timing could produce per-frame SIGWINCH, cover the retargeted clock/status row, change outline, or add blur keys. | Feed from the real A-2 progress callback, never write host height, reserve measured row bounds, use child translation only, reuse existing glass, and keep the one-per-settle and three-entry-cache tests. |
| A-1 ID lifecycle | UI-owned allocation, stale full-grid race, failed final cell persistence, or cancel after configure could leak an ID or leave a phantom cell. | Extend the one durable transaction with the reservation; validate/commit before external launch; all abandon paths use existing per-ID two-phase deletion; never call `deleteHost()`; fault-inject every write boundary. |
| Provider rendering | Malformed/translated/elevated RemoteViews could crash the launcher, paint over neighbors/action controls, or receive out-of-cell touches. | Always use `SafeLauncherAppWidgetHostView`, catch `RuntimeException` only, add a hard canvas/hit clip at `WidgetCellView`, and test rendered pixels plus real event delivery. |
| Provider collection touch | A future edit long-press could arm after a collection begins scrolling or steal provider long-click/PendingIntent touch. | A-3 gives provider descendants immutable DOWN ownership and adds no edit timer. A-4 may arm only from launcher-owned/non-interactive DOWN regions and must use the documented one-way/nested seam. |
| Persistence/migration | Bumping v1 could clear A-1 records/pending state, invent collisions, lose profile identity, or make a corrupt file appear empty while host IDs remain allocated. | Decode all A-1 fields, deterministic ID-preserving migration, atomic one-shot write/retry, strict collision validation, host-owned-ID recovery, and no wholesale host deletion. |
| Package/profile changes | Catalog and grid could disagree after uninstall/profile lock; a stale async preview could bind to the wrong card; an unavailable tombstone could be overlapped. | Generation-tokened catalog, existing above-dock reconciliation seam, key groups by profile+package, stable rect placeholders, and occupancy of missing/deleting records. |
| Memory/performance | Large provider previews/RemoteViews could churn bitmaps, extend the wallpaper cache, or repeatedly reinflate host views. | Sheet-scoped recyclable previews with fallback, no widget screenshot cache, no blur cache changes, ID-diffed host reuse, device `meminfo` plateau, and catalog cancellation on close/stop. |

## 13. Open questions for the project lead

None block A-3. This plan deliberately chooses a 6-row × 4-column default, a non-searchable/non-draggable in-pane
picker, deterministic row-major placement, and a read-only cog summary until A-5. If product wants different defaults
or a searchable picker, that is a product change rather than an implementation ambiguity and should be decided
before step 2 because it changes migration defaults or keyboard-interceptor ownership.
