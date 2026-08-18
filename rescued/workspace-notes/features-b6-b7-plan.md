# Features B-6 and B-7 implementation plan

This plan covers two delivery slices against `app/termux-launcher` branch `dev` at commit `150366a3`: B-6 (shared folders, drag-to-folder, folder popup, and focusless rename) and B-7 (drawer settings and live apply). It assumes the committed B-5 behavior and the reported baseline of 1,216 unit tests with zero failures in both variants. No production code is part of this artifact.

The governing architectural invariant is unchanged: the drawer remains the full-screen overlay sibling declared outside the accessory stack in `activity_termux.xml:1634-1670`. Neither slice may add the drawer to `AccessoryStackLayoutPolicy.computeCombinedHeight()`, call `requestAccessoryGeometrySync()`, or otherwise cause `TerminalView.updateSize()`/SIGWINCH.

All proposed production classes are Java 11; no Kotlin is introduced. Unit coverage uses JUnit4 and Robolectric, matching the existing project stack.

---

## Part I — B-6: drag-to-folder, shared folder popup, and tap-to-rename

### 0. Verified reusable seams

| Area | Verified seam | Planned use |
|---|---|---|
| Overlay isolation | `activity_termux.xml:1634-1670` declares the drawer host as a sibling of the terminal/accessory stack. `AppDrawerContentView.java:47-66` documents the same isolation. | Keep the drag overlay, folder popup anchor, and settings control inside the existing drawer surface; do not participate in accessory measurement. |
| Shared folder model | `PinnedFolderItem.java:8-29` already owns folder identity, title, grid dimensions, tint, and member apps. `MAX_GRID` is `6` at line 9 and defaults are 3×3 at lines 10-11. | Retain this as the sole folder domain model, add an explicit membership limit, and stop treating it as intrinsically dock-only. |
| Existing config | `LauncherConfigRepository.java:20-22` currently writes schema v4. Inline folder load is at lines 64-117 and save is at lines 119-175; app members and profile information are preserved at lines 84-107 and folder tint/grid fields at lines 137-165. Legacy migration is at lines 292-309. | Migrate the inline representation to normalized folder entities and dock references in schema v5. |
| Config safety | `LauncherConfigRepository.java:230-277` prunes invalid icon overrides and the save path preserves root-level `appIconOverrides`. Repository coverage exists in `LauncherConfigRepositoryTest.java:20-266`. | Preserve the override payload verbatim through migration and extend repository tests rather than introducing a second store. |
| Existing dock folder operations | `SuggestionBarView.java:6382-6407` applies folder membership selections/additions; `removeAppFromFolder()` at lines 6558-6585 already collapses zero- and one-member folders. `persistPinsAndReload()` is at lines 4085-4095. | Move normalization into a shared transactional mutator and make all dock and drawer paths use it. |
| Existing dock drag | `SuggestionBarView.java:6416-6425` starts platform drag-and-drop; the row target handler is at lines 6441-6487 and mutation paths at lines 6489-6556. The dock row installs its drag listener at lines 2286-2295. | Reuse platform drag semantics and the same local-state identity, while adding a drawer-specific coordinator and targets. |
| Long-press pickup | `bindContextLongPressGesture()` in `SuggestionBarView.java:4190-4323` shows the context popup on long-press, then uses `LongPressPickupState` and a short horizontal pickup decision window to dismiss the popup and call `startPinnedDrag()`. Public drawer app binding is at lines 4161-4175. | Preserve the proven popup-first interaction and add a pickup delegate for drawer cells rather than adding a second recognizer. |
| Existing folder UI | Folder preview creation is at `SuggestionBarView.java:3381-3427`; it currently paints the first four raw icons in a fixed 2×2 preview. `showFolderPopup()` at lines 3990-4076 resolves members and uses a fixed `GridLayout`. | Extract a shared popup controller, use bounded/recycled content, and route all preview icons through the rendered-icon cache. |
| Existing popup/input behavior | Shared popup construction at `SuggestionBarView.java:5285-5354` uses a non-focusable `PopupWindow`, `INPUT_METHOD_NOT_NEEDED`, and leaves soft-input behavior unchanged. The existing rename dialog at lines 4116-4130 uses an `EditText` and is therefore not reusable for drawer rename. | Keep the non-focusable popup but replace folder-title editing with a focusless title view and controller. No `EditText` or focusable dialog is permitted. |
| Rendered icon budget | `SuggestionBarView.java:238-260` owns the byte-budgeted `LruCache`; its 6-16 MiB sizing and byte accounting are at lines 469-491, and `getRenderedIcon()` is public at lines 2645-2650. `AppDrawerAppCellView.java:76-94` already renders through it and unbinds at lines 122-136. | All drag ghosts, folder mosaics, and popup cells reuse cached rendered icons at requested pixel sizes; no second bitmap cache or full-view snapshot. |
| Frozen gesture policy | `AppDrawerGestureArbiter.java:41-90` captures eligibility at DOWN and lines 145-172 make page/close claims one-way. `SuggestionBarView.java:1668-1822` owns the existing stream arbitration and `AppDrawerContentView.java:1056-1133` observes drawer streams without intercepting them. | Extend the frozen snapshot and claim model with drag; never steal an active child stream. |
| Reentrant click suppression | `AppDrawerHorizontalPagerView.java:106-134` keeps `mSuppressCellClickDuringTerminalDispatch` set through `super.dispatchTouchEvent()` and clears it only in `finally`. `AppDrawerCategoryView.java:576-582` similarly keeps terminal suppression separate from resettable structural state. | Use a distinct terminal-dispatch drag suppression latch that `setInteractive()`, adapter rebinding, popup dismissal, and gesture reset cannot clear. |
| View-specific ownership | `AppDrawerContentView.java:973-1023` defines mode-specific owned regions; its DOWN snapshot includes view type and presentation state at lines 1056-1133. Horizontal page/close handling is at lines 1184-1197 and category detail handling at lines 1199-1207. | Preserve all B-4/B-5 ownership rules, adding drag eligibility to the same immutable DOWN snapshot. |
| Horizontal pager | `AppDrawerHorizontalPagerView.java:136-175` locks the layout manager after a one-way page/close claim. | Lock page swiping when drag latches, then provide deliberate edge-dwell page turns within the drag state rather than reviving the swipe claim. |
| Categories | `AppDrawerCategoryView.java:344-423` owns expansion reset and detail-vs-close claims. | Do not add drag pickup to this view; its category expansion state and existing gesture semantics remain unchanged. |
| Focusless text intake | `AppDrawerSearchController.java:19-49` defines the three channels; in-app interception is at lines 177-218, hardware input at lines 227-236, and IME code points at lines 240-268. `TerminalKeyEventHandler.java:86-102,126-130` provides the pre-terminal interceptor. | Implement folder rename with the same three channels and a non-view text model. |
| Activity routing/InputConnection | `TermuxActivity.java:9901-9923` installs the search interceptor and routes hardware/code-point input. Keyboard display at lines 9930-9943 explicitly targets `mTerminalView` without changing focus or beginning external text input. `TermuxTerminalViewClient.java:336-350,571-582,654-666` routes drawer keys before the terminal. | Insert rename ahead of drawer search while active and continue asking the existing `TerminalView` to show the IME. The popup never becomes an input editor. |
| Dock-row toggles | `TermuxActivity.java:2613-2694` independently applies apps, alphabet, and extra-key row state; the apps pager is made `GONE` and reset at lines 4243-4249. | Cancel dock pickup/drag state before hiding the apps row. Folder entities remain valid and drawer behavior does not depend on a visible dock. |

### 1. Locked product and architecture decisions

#### 1.1 View-type support

Drag-to-folder is supported in **VERTICAL** and **HORIZONTAL** only, and only in the unfiltered all-apps presentation.

- VERTICAL supports long-press pickup, top/bottom drag autoscroll, app-on-app folder creation, and app-on-folder insertion.
- HORIZONTAL supports the same drops plus edge-dwell page turns described below.
- CATEGORIES does not expose drag pickup. Its synthetic Suggestions/Recently groups can duplicate an app and its taxonomy tiles are navigation objects rather than persistence order, so a drop source or target would be ambiguous. Long-press continues to open the existing app context popup. Moving after long-press continues through the B-5 category scroll/detail/close rules; it neither creates a folder nor changes expansion state.
- Search results remain the B-2 flat ranked app list in every view type. Pickup is disabled while the query is non-empty so a transient ranking cannot define folder placement or hide a result during the gesture.

Folders are rendered as first-class cells in the VERTICAL and HORIZONTAL all-apps lists. In CATEGORIES, member applications continue to appear individually and folder entities are not inserted into category tiles or detail lists. Creating a folder in another view therefore does not alter the current categories expansion; switching to VERTICAL/HORIZONTAL reveals the folder.

#### 1.2 “Shared folder” means one persisted entity

A drawer folder and a dock folder are not separate collections that happen to use the same Java class. There is exactly one folder entity per stable folder ID in the launcher configuration:

- The normalized `folders` collection owns the `PinnedFolderItem` payload.
- The dock `items` array contains either an app item or a `folderRef` containing that stable ID.
- The drawer derives its folder cells from the same normalized `folders` collection. A folder may exist without a dock reference; drawer creation does not silently pin it.
- If a folder is pinned, dock and drawer resolve the same ID and observe the same title, tint, dimensions, membership, and subsequent mutations. Repository snapshots must resolve a given ID to the same immutable object instance for that revision, avoiding divergent copies inside one UI refresh.
- Existing dock actions such as pin/unpin affect only the dock reference unless they change folder membership. Unpinning a folder does not delete the shared entity; explicit dissolve/removal or normalization does.

Schema v5 is decisive and normalized:

```json
{
  "schemaVersion": 5,
  "items": [
    { "type": "app", "...": "existing app fields" },
    { "type": "folderRef", "folderId": "stable-id" }
  ],
  "folders": [
    {
      "id": "stable-id",
      "title": "Folder",
      "rows": 3,
      "columns": 3,
      "tintOverrideEnabled": false,
      "tintColor": 0,
      "apps": ["existing full app references"]
    }
  ],
  "appIconOverrides": ["unchanged existing payload"]
}
```

On the first successful v1-v4 load, each inline folder is extracted into `folders` and replaced in place with a `folderRef`. Its existing ID, position, title, member order, rows, columns, tint, profiles, component references, icon overrides, and root `appIconOverrides` are retained. Missing/duplicate IDs are repaired deterministically in source order before references are written. The migration is eager and idempotent: write v5 only after a complete valid in-memory snapshot has been built; on an I/O or parse failure, retain the existing recovery behavior and never partially overwrite the old file. Unknown folder references are pruned, then the collapse invariant is applied. Repository tests must load real v1-v4 fixtures and prove a second v5 load/write is byte-semantically stable.

#### 1.3 Capacity, dock rendering, and collapse invariants

Set `PinnedFolderItem.MAX_APPS = 36`. This matches the existing maximum 6×6 dimensions while bounding config size, popup work, accessibility traversal, and drag-target complexity. The current source does not actually enforce a six-member collection limit: `MAX_GRID = 6` is a dimension bound, and the existing fixed grid can merely make members beyond `rows * columns` invisible. B-6 replaces that accidental truncation with an explicit 36-member contract.

The dock continues to render a compact 2×2 preview. It shows the first four members, with a `+N` badge on the fourth quadrant when more than four exist. Tapping it opens the same recycled, scrollable folder popup as the drawer, where all members up to 36 are reachable. Stored rows/columns define the popup's preferred visible viewport, not a truncation count. A drop on a 36-member folder is rejected without mutation, gives the existing reject haptic/toast treatment, and returns the drag ghost with the house spring.

One shared normalization function runs in the same repository transaction after every drop, removal, uninstall prune, editor save, and migration:

- 2-36 valid unique members: keep the folder.
- 1 member: delete the folder entity; replace every dock reference at its existing position with that surviving `PinnedAppItem`; the drawer composer naturally emits the app again.
- 0 members: delete the entity and remove every dock reference.

Rename never changes membership and therefore never itself causes collapse. A committed rename trims surrounding whitespace; an empty/whitespace-only result becomes the localized default “Folder”. Cancel writes nothing.

#### 1.4 Chosen drag mechanism

Use Android's existing `View.startDragAndDrop()` path with process-local state, extended by a drawer drag coordinator. This is already the dock's mechanism, survives RecyclerView cell recycling, and gives explicit enter/exit/drop/cancel callbacks without intercepting the originating nested-scroll stream. The local state contains only stable app/folder IDs, source surface/view type, frozen source bounds, and the repository revision; no launcher identifiers are placed in public `ClipData`.

`ItemTouchHelper` is rejected because this is merge/drop behavior across pager pages rather than list reordering, and it conflicts with nested RecyclerView/pager ownership. A hand-rolled window-level touch interceptor is rejected because it would steal the child stream and repeat the B-4 arbitration defects. A bitmap drag shadow is also rejected because it would bypass the rendered-icon byte budget.

Long-press popup and pickup keep one recognizer:

1. A long-press first opens the existing context popup.
2. During the existing short pickup decision window, a deliberate horizontal lift beyond the existing pickup X threshold, with no definitive vertical/menu-selection motion, makes the one-way DRAG claim.
3. That claim dismisses the context popup, starts platform drag, and permanently disarms tap, scroll, page, tile, and close claims for the stream.
4. Without the lift, the popup remains the result of the long-press. A vertical slide remains popup/menu interaction, and release does not start drag.

The public drawer binder gains a pickup delegate rather than a second long-click listener. CATEGORIES and filtered search pass no delegate, so their long-press behavior is byte-for-byte the existing context-popup path.

#### 1.5 Exact four-way gesture arbitration

The four competing intent families are: **child scroll/tile action**, **drawer close**, **view-specific page/tile navigation**, and **long-press drag**. Tap/context selection remains the unclaimed terminal outcome. Arbitration is nested-scroll cooperation plus observation only; no new parent `onInterceptTouchEvent()` or synthetic touch stealing is allowed.

At DOWN, one immutable stream snapshot records:

- view type, presentation type, search-empty state, and content interactivity;
- touched stable item ID and whether that exact cell is drag-eligible;
- owned region, active RecyclerView/pager, scrollability, top state, horizontal page/index, and categories expansion state;
- close eligibility and all B-4 axis thresholds/ratios.

That snapshot is frozen until terminal UP/CANCEL. Mid-stream filtering, a page settle, category expansion, adapter refresh, or reentrant `setInteractive()` cannot make a previously ineligible stream eligible or change its axis.

Claim rules are one-way:

1. Before long-press timeout, existing slop/axis policy may claim CHILD_SCROLL, DRAWER_CLOSE, HORIZONTAL_PAGE, or CATEGORY_ACTION. Any such claim cancels the pending long-press permanently.
2. After long-press fires, the context popup owns the stationary stream. On each observed MOVE before child dispatch, the existing pickup policy gets first opportunity to recognize its deliberate horizontal lift. A qualifying lift changes PENDING/CONTEXT to DRAG exactly once.
3. DRAG immediately disallows nested-scroll/page participation through the existing child APIs, locks the active pager/layout manager, dismisses the popup, and calls `startDragAndDrop()`. It does not intercept or retarget the original touch stream.
4. Once any claim is made, no other claim can replace it. Neutral diagonals therefore cannot page and close, and an axis is never recomputed after DOWN.

VERTICAL retains RecyclerView nested scrolling versus the existing top-edge close policy. HORIZONTAL retains the current page-versus-close arbiter until DRAG claims; edge page turns after that are drag-hover actions, not touch-axis claims. CATEGORIES is frozen as drag-ineligible and retains its existing detail-scroll/tile-collapse/close state machine.

The drag click-suppression latch is separate from resettable gesture and interactivity state. It is set before popup dismissal/platform drag startup and remains set through the current `super.dispatchTouchEvent()` terminal dispatch, clearing only in that dispatcher's `finally`, following `AppDrawerHorizontalPagerView.java:106-134`. Adapter reset, `setInteractive(false)`, close completion, or a reentrant popup callback may clear structural drag state but may not clear this latch. Every app/folder cell consults it through the existing click gate, so the cell under a fast final MOVE/UP cannot launch.

#### 1.6 Drag navigation and drop behavior

HORIZONTAL does support page turns while dragging, but only after DRAG has latched:

- A drag location held within a 32dp leading/trailing edge zone for 500ms advances exactly one page using the existing pager snap/select API.
- The pointer must leave and re-enter the zone before another dwell can turn another page. First/last pages reject the corresponding direction.
- Page swipe remains locked; the dwell does not reopen or modify the original PAGE claim. Drop targets are re-resolved by stable ID only after the destination page settles.

VERTICAL autoscroll begins only in the latched drag state, within 48dp top/bottom zones, with velocity proportional to edge penetration and capped below fling speed. No autoscroll or page dwell runs while the context popup is merely open.

Drop semantics are deterministic:

- app onto app creates a folder ordered target first, source second;
- app onto folder appends the source if not already present and if capacity remains;
- app onto itself, duplicate membership, stale source revision, or a non-cell region is a no-op;
- folder-on-app and folder-on-folder merges are not added in this slice; existing dock folder dragging remains reorder-only;
- a successful mutation is one repository transaction followed by one shared snapshot publication.

A drag that began in the dock remains a dock drag; opening the full-screen drawer is not a cross-surface continuation. Before the apps row is changed to `GONE`, any active dock pickup/platform-drag hover is cancelled and its local state invalidated. Once hidden, no dock cell can receive DOWN, while shared folder entities and drawer drag remain fully usable. Re-enabling the row resolves current folder references from the repository. Alphabet and extra-key row toggles do not alter drag state.

#### 1.7 Shared popup and focusless rename

Extract the existing folder surface into `LauncherFolderPopupController`, used by both dock and drawer anchors. It remains a non-focusable `PopupWindow`, but its member grid becomes a bounded RecyclerView/GridLayoutManager so all 36 members can be reached and recycled. The controller resolves a folder ID from the latest repository snapshot before display and again before mutation; it never retains a mutable duplicate folder.

Tapping the title enters an inline visual rename mode in the popup header. `FolderRenameTitleView` is a custom drawing/accessibility view, not an `EditText`, and reports `onCheckIsTextEditor() == false`. `FolderRenameModel` owns draft code points and caret; `FolderRenameController` accepts exactly the three existing drawer channels:

- in-app keyboard through `TerminalKeyEventHandler.KeyValueInterceptor`;
- hardware keyboard through the activity's drawer `onKeyDown`/paired `onKeyUp` route;
- system IME text through `TermuxTerminalViewClient.onCodePoint()`.

While rename is active the priority is command palette, folder rename, drawer search, then terminal. Enter commits, Escape/Back cancels rename before closing the popup, Backspace/Delete edit the draft, and left/right move the caret. The draft is limited to 40 Unicode code points without splitting surrogate pairs. Commit is a repository rename transaction; cancel and popup dismissal write nothing. A successful commit refreshes both dock and drawer observers in place and keeps the popup open.

InputConnection proof:

- The popup remains `focusable=false`, `INPUT_METHOD_NOT_NEEDED`, and does not request focus.
- Its `PopupWindow` is a separate surface outside the drawer plane's layout/measurement tree; it is only positioned from an anchor rectangle and never participates in terminal or accessory measurement.
- Neither the title nor any descendant is an `EditText` or text editor, and no rename dialog is created.
- To show the soft keyboard, the activity reuses `KeyboardUtils.showSoftKeyboard(this, mTerminalView)` from `TermuxActivity.java:9930-9943`; `mTerminalView` remains the sole focus owner/InputConnection provider.
- Rename does not call `beginExternalTextInput()`, `requestAccessoryGeometrySync()`, the full styling reload path, or any accessory height API. Showing or dismissing the popup therefore cannot run `TerminalView.updateSize()` or emit SIGWINCH.
- Ending rename restores the previous drawer-search interceptor atomically, including on close, activity pause, folder deletion, or stale-revision rejection.

#### 1.8 Animation and rendered-icon memory

All new motion uses `com.termux.app.Spring`: drag return, accepted-drop morph, folder hover emphasis, and folder-popup open/close. A small Choreographer-backed adapter advances house springs and applies translation/scale/alpha; `androidx.dynamicanimation` is not added. Existing unrelated popup animation need not be rewritten, but the shared folder popup must use the house spring when extracted.

`AppDrawerDragOverlayView` is a child of the existing drawer overlay and holds a reference to one cache-produced drawable, never a `View` screenshot or uncached bitmap. Folder preview quadrants call `SuggestionBarView.getRenderedIcon()` at their actual mini-icon pixel size and use the existing byte-counting cache key. Popup cells use the normal rendered size and release drawable/view references in recycle/unbind. Horizontal page turns may prebind only the normal adjacent page; drag must not retain recycled page cells. A failed return springs toward the frozen source bounds; if that cell is no longer attached, it springs toward the frozen rectangle and fades rather than allocating a replacement snapshot.

### 2. Class-by-class B-6 design

#### Model and persistence

- **`PinnedFolderItem`**: add the explicit `MAX_APPS = 36` contract and validation helpers. Keep grid dimensions separately clamped by `MAX_GRID`.
- **`LauncherConfigSnapshot` (new)**: immutable revisioned aggregate containing resolved dock items, normalized folder entities by stable ID, and icon overrides. It guarantees one folder object per ID per revision.
- **`LauncherConfigRepository`**: advance to schema v5; implement v1-v4 normalization; expose atomic rename/create/add/remove/dissolve/pin-reference operations; publish listeners on the main thread after a durable write. Reject stale drag revisions by re-resolving IDs and validating membership rather than applying index-based mutations.
- **`LauncherFolderMutator` (new, package-private pure helper)**: enforce uniqueness, capacity, zero/one-member collapse, and reference replacement/removal. Repository and migration tests exercise it without Android views.

#### Shared dock/drawer folder surface

- **`LauncherFolderPopupController` (new)**: own the non-focusable popup, recycled member adapter, title rename lifecycle, repository observation, and house-spring transition. Accept a folder ID plus anchor, not a folder copy.
- **`FolderRenameModel` / `FolderRenameController` / `FolderRenameTitleView` (new)**: separate Unicode editing, the three input channels, and rendering/accessibility. The controller never owns an InputConnection.
- **`SuggestionBarView`**: delegate folder-popup display and folder mutation to the shared components; replace raw folder preview icons with byte-budgeted rendered icons and a `+N` overlay. Extend its public drawer long-press binder with an optional pickup delegate while keeping category/search callers popup-only. Cancel dock drag before apps-row disable.

#### Drawer composition and cells

- **`AppDrawerItem` (new)**: immutable tagged app/folder row item with stable ID and no Android dependencies.
- **`AppDrawerItemComposer` (new)**: for an empty query, suppress member apps and insert each folder once at the alphabetical position of its earliest member; tie-break by normalized title then stable ID. For non-empty query it returns the existing flat app results. The same output feeds VERTICAL and HORIZONTAL.
- **`AppDrawerAppsAdapter` and horizontal page adapter**: bind mixed `AppDrawerItem` values while preserving stable IDs, metrics, click gates, and recycle cleanup. App launch behavior is unchanged.
- **`AppDrawerFolderCellView` (new)**: render title and cached 2×2 mosaic, open the shared popup on tap, expose a drop target, and participate in the same click-suppression gate. Folder cells are targets but not drag sources in B-6.
- **`AppDrawerAppCellView`**: accept the optional pickup delegate and drag hover/accessibility state. Existing context-popup-only binding remains the default.

#### Drag and gesture ownership

- **`AppDrawerDragPolicy` (new pure helper)**: calculate pickup eligibility, edge zones/dwell, vertical autoscroll velocity, capacity acceptance, and terminal outcomes from frozen inputs. No View or clock dependency.
- **`AppDrawerDragController` (new)**: own platform local state, repository revision checks, target resolution, hover, page dwell/autoscroll scheduling, success/failure animation, and exactly-once cleanup.
- **`AppDrawerDragOverlayView` (new)**: draw one cache-backed ghost and hover affordance inside the drawer overlay without measuring accessory content.
- **`AppDrawerContentView`**: extend its DOWN snapshot with stable source/drag eligibility; observe pickup MOVE before child terminal dispatch; make DRAG a one-way claim; preserve the terminal click latch through `finally`; route edge/page and autoscroll state to the controller. Do not add interception.
- **`AppDrawerHorizontalPagerView`**: expose lock/page-settle hooks to the drag controller, retaining its current page/close and reentrant suppression behavior outside a latched drag.
- **`AppDrawerCategoryView`**: no folder or drag implementation. Only ensure mode switch/popup close cannot inherit a stale drag token; normal expansion state remains untouched.
- **`AppDrawerController`**: own the shared config snapshot subscription and drag controller; cancel drag before immediate close/view-type transition; refresh both mixed adapters after one committed revision.
- **`AppDrawerPlaneView`**: no geometry or interception changes. Its existing frozen content-alpha/touch guard remains authoritative.
- **`TermuxActivity` / `TermuxTerminalViewClient`**: add rename routing ahead of search and restore it on every terminal path. Do not change focus, accessory measurement, or terminal input when rename is inactive.

### 3. B-6 implementation order

1. Add characterization tests for v4 inline folders, current dock collapse, current long-press popup/pickup, all three drawer view types, and the B-4 terminal click-suppression cases before changing behavior.
2. Introduce `LauncherConfigSnapshot` and `LauncherFolderMutator`; implement schema v5 read/write and v1-v4 migration with golden fixtures. Keep current callers on compatibility accessors until the migration tests are green.
3. Convert dock reads/writes to folder IDs and repository transactions. Apply the 36-member limit, shared collapse invariant, cached 2×2 preview, and `+N` badge. Verify apps-row enable/disable cancels active dock drag without touching other rows.
4. Extract `LauncherFolderPopupController`, replace fixed/truncating content with the recycled grid, and retain non-focusable popup flags. Switch both old dock entry points to it before exposing it to the drawer.
5. Implement the pure rename model/controller, then wire the three activity input channels and interceptor priority. Add the custom title view only after InputConnection/focus tests prove the terminal remains the editor.
6. Add `AppDrawerItem` composition and mixed app/folder binding to VERTICAL and HORIZONTAL. Keep search and CATEGORIES on their existing flat app paths and compare AUTO-layout snapshots against B-5.
7. Add frozen drag eligibility and the persistent terminal click-suppression latch to drawer dispatch, with pure arbitration tests covering all claim pairs. Do not add drop mutation yet.
8. Add platform drag startup through the existing long-press pickup seam, overlay rendering, stable-ID targets, transaction-backed app-on-app/app-on-folder drops, reject/return behavior, and exactly-once cleanup.
9. Add vertical edge autoscroll and horizontal 32dp/500ms edge dwell with pager locking and settle-aware retargeting.
10. Replace all new motion with house `Spring`, audit cache/unbind paths, and run unit/Robolectric suites for both variants. Then perform the physical-device matrix below.

### 4. B-6 unit and Robolectric tests

#### Persistence/model tests

1. **`LauncherConfigRepositoryV5MigrationTest`** loads v1-v4 fixtures and asserts stable IDs, dock positions, profiles, title/grid/tint/member order, icon overrides, and root overrides survive; inline folders become refs; a second migration is idempotent.
2. **`LauncherConfigRepositoryNormalizedFolderTest`** asserts a drawer-only folder and a dock-referenced folder resolve from one entity table, a rename is visible on both surfaces after one revision, and unpin does not delete the entity.
3. **`LauncherFolderMutatorTest`** asserts target-first creation, append order, duplicate rejection, 36-member acceptance, 37th-member rejection, and stale/missing identity no-op.
4. **`LauncherFolderCollapseTest`** asserts zero deletes all references, one replaces every dock reference in place with the survivor, two remains a folder, and uninstall/editor/drop all call identical normalization.
5. **`LauncherConfigMigrationFailureTest`** asserts malformed/failed writes never partially replace the prior config and duplicate/missing IDs are repaired deterministically.

#### Gesture and drag tests

6. **`AppDrawerFourWayGestureArbiterTest`** parameterizes VERTICAL/HORIZONTAL/CATEGORIES across child scroll, close, page/tile, and drag; DOWN inputs remain frozen and every claim is one-way.
7. **`AppDrawerLongPressPickupTest`** asserts long-press initially shows context, qualified lift within the decision window dismisses it and starts drag exactly once, vertical/menu movement never picks up, and release without lift keeps context behavior.
8. **`AppDrawerDragTerminalDispatchTest`** recreates the B-4 reentrant reset: drag startup triggers `setInteractive(false)`/adapter reset during terminal dispatch, but source/ending cells never click or launch before the `finally` latch clears.
9. **`AppDrawerHorizontalDragEdgeTest`** asserts 32dp/500ms dwell, one page per entry, leave/re-enter requirement, boundary no-op, pager lock, settle-before-retarget, and no PAGE/CLOSE claim revival.
10. **`AppDrawerVerticalDragAutoscrollTest`** asserts no pre-latch scroll, proportional capped velocity in 48dp zones, cancellation on exit/drop, and stable-ID target resolution after recycling.
11. **`AppDrawerCategoryDragExclusionTest`** asserts every category source is frozen drag-ineligible, long-press remains context-only, expansion/detail state does not change, and existing tile/scroll/close claims are identical.
12. **`AppDrawerSearchDragExclusionTest`** asserts non-empty query results cannot start or accept folder drag and all existing rank/launch behavior remains intact.
13. **`AppDrawerDropTransactionTest`** asserts app-on-app/app-on-folder semantics, capacity rejection, failure return, one durable write/one observer publication, and no cross-surface dock insertion for drawer-created folders.
14. **`DockAppsRowDragLifecycleTest`** asserts disabling apps during pickup/drag cancels exactly once, makes the row `GONE`, leaves alphabet/extra-key rows unchanged, preserves entities, and re-enable resolves current refs.

#### Popup, rename, rendering, and regression tests

15. **`LauncherFolderPopupTest`** asserts dock and drawer use the same controller/entity, 36 members are scroll-reachable, viewport rows/columns do not truncate, stale/deleted IDs close safely, and popup flags remain non-focusable/IME-not-needed.
16. **`FolderRenameModelTest`** covers all three intake forms, Unicode code-point caret/editing, 40-code-point limit, trim/default name, commit/cancel, and no writes before commit.
17. **`FolderRenameInputRoutingTest`** asserts palette > rename > search > terminal priority, paired key-up swallowing, interceptor restoration on every exit, `mTerminalView` retains focus/InputConnection, and no external-text/accessory-geometry method is invoked.
18. **`FolderPreviewCacheBudgetTest`** asserts mosaics and drag ghosts call the shared rendered cache with actual pixel sizes, byte accounting remains bounded, recycled cells release references, and no bitmap snapshot/second cache is allocated.
19. **`AppDrawerFolderCompositionTest`** asserts member suppression and deterministic folder placement in VERTICAL/HORIZONTAL; CATEGORIES and non-empty search retain individual apps and B-5 ordering.
20. **Existing-regression suites** remain green for dock reorder/context actions, VERTICAL nested scroll/close, HORIZONTAL pagination/dots/page-close arbitration, CATEGORIES expansion/detail/click suppression, search intake, back hierarchy, insets, and accessory geometry.

### 5. B-6 device verification

Run on the dedicated Android test device in both application variants after the full unit suite:

1. Create a folder in VERTICAL, rename with the in-app keyboard, hardware keyboard, and system IME, then verify the dock and HORIZONTAL show the same name/members when pinned.
2. Fill a folder past 4 and past 9 members, verify the dock `+N`, scroll all popup members, reject member 37, and confirm memory remains within the rendered-icon budget during repeated open/drag cycles.
3. Exercise HORIZONTAL slow/fast page swipes, top-edge close, neutral diagonals, long-press pickup, both edge dwells, drop, and failed return. No stream may both page and close or launch an ending cell.
4. Exercise VERTICAL nested scrolling/autoscroll/close at top, including recycling the source offscreen during a failed drag.
5. In CATEGORIES, expand/collapse tiles and detail rows while long-pressing apps; confirm no pickup affordance, no expansion mutation, and unchanged close/click behavior.
6. Disable the dock apps row during an active pickup if the settings surface permits it, and otherwise simulate lifecycle cancellation immediately before disable. Confirm no invisible drag target remains and the other two rows are unchanged.
7. While renaming, inspect focus/InputMethod state and terminal dimensions/logs. The terminal keeps its InputConnection and no resize/SIGWINCH occurs on title tap, IME show/hide, commit, popup close, drag, or drawer close.

### 6. B-6 regression risks and containment

| Surface | Risk | Containment |
|---|---|---|
| Dock pinned row | Normalizing inline folders could change order, identity, tint, editor behavior, reorder targets, or unpin semantics. Large folders could silently hide members; hiding the apps row could leave a drag alive. | Golden migration fixtures, stable ID refs, centralized normalization, explicit `+N`/scrollable popup, and cancellation before `GONE`. Preserve existing folder-reorder semantics and all non-folder pinned items. |
| VERTICAL | Long-press movement could steal nested scroll or close, autoscroll could survive drop, and mixed cells could disturb columns/launch. | Observer-only frozen arbitration, one-way claims, latched-only autoscroll, exactly-once cleanup, stable IDs, and AUTO-layout characterization. |
| HORIZONTAL | Edge hover could revive swipe arbitration, turn multiple pages, invalidate recycled targets, or reproduce page+close/fast-launch P1s. | Lock the pager after DRAG, dwell/re-entry rule, settle-aware stable-ID resolution, frozen DOWN axis, and terminal-dispatch latch cleared only in `finally`. |
| CATEGORIES | Shared adapters or snapshot refresh could insert folders, collapse an expanded tile, or weaken B-5 click suppression. | Keep a separate flat app composition path, pass no pickup delegate, do not mutate expansion on folder revisions, and retain its existing terminal latch. |
| Search | Member suppression or drag could alter ranked results and app discovery. | Empty-query-only folder composition and frozen drag-ineligible search snapshot. |
| Rename/terminal | A focusable editor could replace the terminal InputConnection or trigger accessory geometry; interceptor teardown could leak input. | No `EditText`, focusable popup, dialog, or external text mode; explicit routing/restoration tests and terminal-focused IME request only. |
| Memory/animation | Mosaic snapshots or multiple caches could exceed the B-3 budget; a new animation dependency could diverge from house motion. | Shared byte LRU at actual sizes, drawable-only ghost, recycle cleanup, and `com.termux.app.Spring` exclusively. |

---

## Part II — B-7: drawer settings screen and live apply

### 7. Verified existing settings and live-runtime seams

| Area | Verified seam | What is still missing |
|---|---|---|
| Current launcher settings | `launcher_preferences.xml:56-69` exposes drawer enabled and the B-5 view-type `ListPreference`. `LauncherPreferencesFragment.java:34-51` provides the standard `MaterialPreferenceFragment`/style data-store pattern. | No dedicated drawer settings screen or drawer settings button; view type is the only layout control exposed. |
| Stored layout keys | `TermuxPreferenceConstants.java:195-221` defines view type plus AUTO-default vertical columns, horizontal columns, and horizontal rows. `TermuxAppSharedPreferences.java:191-264` already validates and reads/writes them. | The three grid controls have no preference XML/data-store routes. There is no icon-size key and no categories tile-column key. |
| Preference resources | `arrays.xml:41-50` supplies all three view-type labels/values. | Arrays/summaries are missing for icon size, vertical/horizontal grid values, horizontal row counts, and category tile columns. |
| Preference data store | `TermuxStylePreferencesDataStore.java:205-240` debounces styling updates; integer routing is at lines 353-427 and view-type string routing at lines 461-464,514-515. | Drawer grid integers are not routed and current style sync is too broad for safe live drawer apply. |
| Existing controller sampling | `AppDrawerController.java:770-812` reads view type and all three reserved grid keys when preparing content. Preference reload at lines 445-453 closes the drawer and updates only view type. | An already-built content tree does not re-resolve grid/icon/category metrics or rebind adapters on preference change. |
| Metrics | `AppDrawerGridMetrics.java:23-45,66-91` clamps app columns to 4-6 and currently resolves icon size automatically. `AppDrawerHorizontalGridMetrics.java:38-65` adds 2-6 rows. `AppDrawerCategoryGridMetrics.java:75-117` separately resolves 1-3 taxonomy tile columns, 40dp category icon cap, preview budget, and detail app metrics. | No requested icon-size input; categories needs its own 1-3 tile-column preference and cannot reuse the app-grid 4-6 column key. |
| Drawer chrome | `AppDrawerContentView.java:89-110,180-286` reserves a 64dp bottom band and creates the existing content children, but no settings cog. Ownership is mode-specific at lines 973-1023. | Add a content-owned accessible settings control without changing the drawer plane or gesture regions. |
| Activity preference reload | `TermuxActivity.java:13086-13129` performs a broad styling reload, invokes controller preferences at lines 13103-13104, then calls accessory geometry at line 13108 and can recreate for other style changes. Pending style handling is at lines 12617-12638,13052-13084 and resume at lines 1016-1019. | Drawer-only changes need a separate debounced/pending signal that does not run full styling, accessory geometry, or activity recreation. |
| Settings navigation | `root_preferences.xml` uses `app:fragment`; `MaterialPreferenceFragment` subclasses and `TermuxActivity.java:9636-9641` show the established launcher-settings intent pattern. | Add a dedicated fragment destination reachable from launcher settings and the drawer cog. |

### 8. B-7 settings contract

Keep the existing drawer enabled switch in the parent launcher settings for quick access. Replace the inline view-type row with one “Drawer layout” navigation preference whose summary reflects the selected type. The new `AppDrawerPreferencesFragment` owns the layout controls; the drawer cog opens this fragment directly.

The dedicated screen contains:

| Preference | Key/value contract | Visibility |
|---|---|---|
| View type | Existing `app_launcher_drawer_view_type`: `VERTICAL`, `HORIZONTAL`, `CATEGORIES` | Always |
| Icon size | New `app_launcher_drawer_icon_size_dp`: `0` AUTO, or `36`, `40`, `44`, `48` dp | Always |
| Vertical columns | Existing `app_launcher_drawer_grid_columns_vertical`: `0` AUTO, or `4`, `5`, `6` | VERTICAL only |
| Horizontal columns | Existing `app_launcher_drawer_grid_columns_horizontal`: `0` AUTO, or `4`, `5`, `6` | HORIZONTAL only |
| Horizontal rows | Existing `app_launcher_drawer_grid_rows_horizontal`: `0` AUTO, or `2`-`6` | HORIZONTAL only |
| Category tile columns | New `app_launcher_drawer_grid_columns_categories`: `0` AUTO, or `1`, `2`, `3` | CATEGORIES only |

Every row uses a `ListPreference` with a simple selected-value summary; AUTO is the default and preserves current B-5 output. Changing view type updates control visibility immediately in the settings fragment as well as scheduling live apply.

The categories-specific key is required because its overview consists of approximately 144dp taxonomy/mosaic tiles with a valid 1-3 column range, while VERTICAL/HORIZONTAL contain app cells with a 4-6 range. Reusing either app-grid key would make one surface invalid. The selected global icon target affects app cells and category detail apps. Category overview icons/previews derive their current large/small sizes from that target but remain clamped by the category 40dp cap and preview byte budget; category detail columns remain AUTO from available width and the selected icon/cell fit. Only category overview tile columns are user-controlled in B-7.

Sanitization is identical at every entry point: unknown strings fall back to VERTICAL, out-of-range integers fall back to AUTO, and actual pixel/icon geometry is still clamped to safe bounds after density/insets are known. No preference can force clipping below the established label/touch-target minimum.

### 9. Live apply without activity recreation or terminal resize

Do not route drawer layout changes through `reloadActivityStyling()`: that path intentionally reaches accessory geometry at `TermuxActivity.java:13108`. Add a drawer-specific debounced preference notification, modeled on the existing broadcast/pending pattern but with a dedicated action and handler.

The exact live flow is:

1. `TermuxStylePreferencesDataStore` persists a drawer layout value and schedules the dedicated drawer notification. Rapid changes coalesce.
2. If `TermuxActivity` is visible, its drawer-settings receiver calls only `mAppDrawerController.onPreferencesReloaded()`. If the settings activity covers it, a drawer-specific pending flag is consumed on the next `onResume()`.
3. The controller first closes an engaged/open drawer immediately and cancels any drag/popup/rename. This preserves the frozen gesture contract; it never changes layout under an active stream.
4. The controller reads all six layout values, builds one immutable `AppDrawerLayoutConfig`, and calls `applyLayoutConfig()` on the already-created `AppDrawerContentView`.
5. Content re-resolves vertical/horizontal/category metrics from its current usable bounds, reconfigures existing layout managers/pager page size/category span count, invalidates only affected rendered-icon size keys through the existing cache interface, and rebinds the existing adapters. It does not recreate `TermuxActivity` or allocate a second content tree.
6. If the drawer content has never been built, the controller retains the config and applies it on first construction. The next open uses it with no intermediate old-layout frame.

This notification handler must not call `reloadActivityStyling()`, `setMargins()`, `applySuggestionBarSettings()`, `applyAccessoryGeometryIfNeeded()`, `requestAccessoryGeometrySync()`, or `Activity.recreate()`. The drawer host remains the same overlay sibling, so live apply cannot change terminal bounds or produce SIGWINCH.

The settings cog is placed in the already reserved 64dp bottom-left band. Its region is recorded as CONTROL in the immutable DOWN snapshot. A tap closes the drawer immediately and launches the dedicated settings fragment; movement beyond slop cancels the click and follows the existing close/scroll rules without firing the control. It has a localized content description, minimum 48dp target, visible focus/pressed states, and does not overlap the rope, pager dots, or gesture exclusion edges.

### 10. Class-by-class B-7 design

- **`TermuxPreferenceConstants` / `TermuxAppSharedPreferences`**: add icon-size and category-column keys, AUTO defaults, valid ranges, and sanitizers. Retain the existing three grid keys unchanged for compatibility.
- **`arrays.xml` and drawer preference XML (new)**: add value/label arrays and `app_drawer_preferences.xml`; move view-type presentation to the dedicated screen without changing its key or stored value.
- **`LauncherPreferencesFragment` / `launcher_preferences.xml`**: keep the enabled switch; replace the inline view type with the fragment navigation row and dynamic summary.
- **`AppDrawerPreferencesFragment` (new)**: use `MaterialPreferenceFragment`, set the style data store, show/hide mode-specific grid controls immediately, and expose selected-value summaries. It performs no activity mutation itself.
- **`TermuxStylePreferencesDataStore`**: add get/put routing for the five integer layout keys and retain the existing view-type string route. All six call the dedicated debounced drawer sync, not full styling sync.
- **`AppDrawerLayoutConfig` (new pure immutable value)**: contain sanitized view type, icon dp/AUTO, vertical columns, horizontal columns/rows, and category columns. Equality lets the controller ignore duplicate notifications.
- **`AppDrawerGridMetrics` / `AppDrawerHorizontalGridMetrics`**: accept the requested icon dp before their existing geometric clamps. AUTO must produce values identical to B-5.
- **`AppDrawerCategoryGridMetrics`**: accept requested category tile columns and shared icon target, retaining its independent range/caps and byte-budget calculation.
- **`AppDrawerContentView`**: add the bottom-band settings control and callback; apply a new config to the existing child views/layout managers/adapters; extend owned-region snapshots with CONTROL. No accessory measurement or focus behavior changes.
- **`AppDrawerController`**: replace its view-type-only reload with complete config sampling, immediate safe close, cancellation of B-6 transient state, and in-place apply to an existing content view. Store the config for lazy first build.
- **`TermuxActivity`**: add the drawer-only receiver/pending-on-resume handler and settings-fragment navigation callback. Keep it disjoint from full styling and accessory geometry.

### 11. B-7 implementation order

1. Add characterization tests for all B-5 AUTO metric outputs and current view-type preference migration before introducing new keys.
2. Add constants, shared-preference accessors/sanitizers, arrays, `AppDrawerLayoutConfig`, and data-store routing. Verify all existing stored view/grid values retain their meaning.
3. Add the dedicated drawer notification, debounce, receiver, and pending-on-resume path with spies proving the full style/accessory/recreate paths are untouched.
4. Extend the three metrics calculators with requested values; prove AUTO is identical, explicit values clamp safely, and categories uses its separate 1-3 overview span.
5. Implement `applyLayoutConfig()` against the already-built content tree: close/cancel transient state, update managers/pager/category, refresh cache-dependent rendering, and rebind once.
6. Add `AppDrawerPreferencesFragment` and XML, move the view-type UI into it, keep the enabled switch in the parent, and implement mode-dependent control visibility/summaries.
7. Add the accessible bottom-band settings cog, frozen CONTROL ownership, and direct navigation to the fragment.
8. Run the complete unit/Robolectric suite for both variants, compare B-5 AUTO screenshots/behavior, then execute the live-device matrix.

### 12. B-7 unit and Robolectric tests

1. **`AppDrawerLayoutConfigTest`** asserts defaults, all valid explicit values, invalid fallback to AUTO, equality, and string view-type fallback.
2. **`TermuxStylePreferencesDataStoreDrawerTest`** asserts all six keys round-trip, grid integers no longer fall through, rapid writes coalesce, and drawer keys schedule only the drawer notification.
3. **`AppDrawerMetricsPreferenceTest`** golden-compares AUTO to B-5; verifies explicit icon/columns/rows; verifies safe bounds at narrow/wide widths and densities; and verifies category 1-3 spans are independent of app 4-6 spans.
4. **`AppDrawerPreferencesFragmentTest`** asserts the exact preferences, summaries, defaults, and VERTICAL/HORIZONTAL/CATEGORIES visibility changes without recreating the settings activity.
5. **`AppDrawerLiveApplyTest`** builds the drawer once, changes each setting, and asserts the same content instance/layout children are reconfigured and rebound once after an immediate close.
6. **`AppDrawerLiveApplyLifecycleTest`** asserts covered/background activity defers to resume, an unbuilt drawer samples on first build, duplicate configs no-op, and a pending drag/popup/rename is cancelled before apply.
7. **`AppDrawerLiveApplyGeometryIsolationTest`** spies that drawer updates never call full style reload, activity recreation, suggestion/accessory margin updates, `requestAccessoryGeometrySync()`, or terminal resize; overlay host layout bounds remain independent.
8. **`AppDrawerSettingsControlTest`** asserts 48dp accessibility target/description, frozen CONTROL ownership, tap navigation, slop cancellation, no close+click double outcome, and no overlap with dots/rope/edge gestures.
9. **View-type regression tests** repeat VERTICAL scroll/close, HORIZONTAL pagination/dots/page-close, and CATEGORIES tile/detail expansion after every live-apply combination, with search and B-6 folder behavior unchanged.

### 13. B-7 device verification

1. Open an already-built drawer, change each visible preference from the dedicated screen, return, and confirm the next open uses the new layout without activity flash/recreation.
2. Switch view type repeatedly and verify the settings screen immediately shows only its relevant grid controls; AUTO matches the shipped B-5 layout in all three modes.
3. Test explicit sizes at minimum/maximum phone widths, landscape, gesture navigation, display scaling, and large font. Labels/touch targets must remain usable and icons must not exceed cache/metric clamps.
4. Keep a folder popup/rename or drag active, trigger a settings apply through lifecycle return, and confirm the transient state cancels cleanly before the new config appears.
5. Record terminal rows/columns and SIGWINCH-sensitive logs across every setting change. They must remain unchanged; the terminal keeps focus/InputConnection.

### 14. B-7 regression risks and containment

| Surface | Risk | Containment |
|---|---|---|
| Settings persistence | Existing reserved grid values could be overwritten or interpreted differently; invalid values could produce unusable layouts. | Reuse keys/contracts, default AUTO, centralized sanitizer, compatibility tests, and geometry clamps. |
| Live runtime | Reusing full styling could resize the terminal; applying under a gesture could violate frozen ownership; rebuilding could leak views/cache entries. | Dedicated notification only, immediate close/cancel before apply, same content tree, equality no-op, and explicit geometry-isolation spies. |
| VERTICAL | Explicit icon/columns could alter scroll anchor, close-at-top, labels, or mixed folder placement. | Re-resolve metrics atomically, reset to a valid top anchor after closed apply, retain composition/stable IDs, and repeat gesture tests. |
| HORIZONTAL | Rows/columns can change page count, dots, selected index, snap state, or edge-drag bounds. | Apply only while closed, rebuild page mapping from stable items, clamp page to range, settle to page zero by policy, and rerun B-4/B-6 arbitration. |
| CATEGORIES | Treating category columns like app columns could corrupt the overview; resizing might retain an invalid expansion/detail offset. | Separate key/range/calculator; close/reset transient presentation before apply; keep detail metrics independently derived. |
| Dock/accessories | A broad preference callback could rebuild rows or request terminal geometry even though only drawer layout changed. | Drawer-only receiver never invokes style/suggestion/accessory paths; dock state and its three independent toggles remain untouched. |
| Icons/memory | Repeated icon-size changes could retain multiple size generations and exceed the LRU budget. | Use existing byte-budget eviction, invalidate only affected rendered-size keys, release adapter references, and stress-test repeated min/max changes. |

## Open questions for the project lead

None. The design-impacting choices are fixed in this plan: folders are normalized shared entities, VERTICAL and HORIZONTAL alone support pickup, folder membership is capped at 36 with a compact dock preview, HORIZONTAL uses edge dwell page turns, CATEGORIES remains flat/expansion-preserving, rename uses the terminal-owned three-channel focusless intake, and drawer settings live-apply through a dedicated non-geometry path.
