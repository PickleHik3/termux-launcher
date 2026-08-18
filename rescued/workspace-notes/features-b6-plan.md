# B-6 implementation plan — shared folders, drag-to-folder, folder popup + focusless rename

Builds on B-1 through B-4 and the current uncommitted B-5 worktree. Owns drawer-root folders,
app-to-app/app-to-folder drag, the drawer folder popup, and tap-to-rename. It owns nothing from B-7
(the full drawer-settings surface or new user-facing grid controls).

Survey verified 2026-08-11 against the current worktree in `app/termux-launcher`.

## 0. Decisive answers

1. **Supported view types:** drag-to-folder is enabled in `VERTICAL` and `HORIZONTAL`. It is disabled
   for the whole `CATEGORIES` mode, including overview previews, expanded detail, and its temporary
   flat search grid. Category icons retain the shipped tap/long-press behavior: a long press opens the
   dock-identical app context popup and never enters pickup. Category expansion state, selected bucket,
   overview/detail scroll position, collapse gesture, and Back hierarchy are unchanged.
2. **Shared means one persisted entity:** a drawer folder and a dock folder are the same schema-v5
   folder record, addressed by `PinnedFolderItem.id`. The drawer owns no parallel folder collection.
   The dock order contains a `folderRef` to that record. Creating a folder in the drawer does **not**
   automatically pin it; pinning/unpinning only adds/removes the dock reference. Renaming or changing
   membership through either surface updates the one record and both surfaces rebind from it.
3. **Capacity:** add `PinnedFolderItem.MAX_ITEMS = 36`. Thirty-six matches the existing maximum 6×6
   folder geometry, is large enough for a drawer folder without allowing one object to retain an
   unbounded catalogue, and gives a simple invariant to every mutation path. The current code does not
   actually enforce an item count: it defaults rows/columns to 3×3 and clamps each dimension to 6
   (`PinnedFolderItem.java:9-11`; `LauncherConfigRepository.java:88-89`), while the popup merely stops
   drawing at `rows * cols` (`SuggestionBarView.java:4004-4029`). B-6 makes the intended limit explicit.
4. **Dock overflow:** `rows` and `cols` become the dock popup's **viewport**, not a truncation limit.
   A dock folder with more than `rows * cols` members stays pinned; its dock icon preview still shows
   the first four, and its popup is a vertically scrolling recycled grid showing every member. No app
   is silently hidden or removed. The drawer popup uses adaptive 3–6 columns and also scrolls.
5. **Folder collapse:** membership is normalized after every mutation and catalogue reconciliation.
   Zero members deletes the folder record and every dock reference. One member deletes the folder,
   returns the survivor as an ordinary drawer app, and replaces every dock reference at the same slot
   with that `PinnedAppItem`, preserving its profile identity and icon override.
6. **Rename:** tapping the drawer folder popup's title enters a focusless rename mode in that same
   in-plane popup. There is no `EditText`, dialog, focus request, or new input connection. In-app
   keyboard values, hardware `onKeyDown`, and IME `onCodePoint` are routed through one drawer input
   router; rename has priority over search while active. Enter/action or tapping outside the title
   commits one atomic repository write; that outside tap is consumed so it cannot also launch a member.
   Esc/Back cancels and restores the old title. A blank/whitespace
   commit becomes `Folder`; input is capped at 48 Unicode code points.
7. **Horizontal edges:** yes, dragging to a pager edge page-turns. A 32dp physical edge zone held for
   450ms turns exactly one adjacent logical page through the existing pager/snap path; continued dwell
   repeats only after the page settles and another 450ms elapses. First/last edges do nothing. The
   mapping honors RTL. Page swipe and edge-turn can never occur in the same pointer stream: ordinary
   page swipe is a DOWN-axis claim, while edge-turn is available only after the one-way drag latch.

## 1. Verified reusable seams and constraints

| Existing fact | Reuse / consequence for B-6 |
|---|---|
| `app_drawer_host` is a full-screen sibling explicitly outside the accessory stack (`activity_termux.xml:1634-1670`). | The drag layer and folder popup are children of `AppDrawerContentView` inside `AppDrawerPlaneView.getContentHost()`. Add no activity band and no call to `AccessoryStackLayoutPolicy.computeCombinedHeight()`, toolbar-height APIs, `TerminalView.updateSize()`, or accessory-geometry sync. Drag, popup, rename, and their animations cannot produce SIGWINCH. |
| The plane lays content out once for the final open rect and reveals it by outline clipping (`AppDrawerPlaneView.java:152-175`, `:194-214`). | Drag ghost and popup use transforms/drawing only per frame. They do not resize the plane or recapture accessory bands. The existing host-layout listener may recompute only the open rect when the system IME changes the window (`AppDrawerController.java:860-873`). |
| One controller owns the only drawer frame loop and already advances content effects (`AppDrawerController.java:488-520`). | Drag-return and folder-open/close use `com.termux.app.Spring` and return `fxMoving` from `AppDrawerContentView.advanceDrawerFx`; no animator, second `Choreographer`, or `androidx.dynamicanimation` is added. |
| The house spring clamps/substeps time, handles non-finite values, and snaps under reduce motion (`Spring.java:19-74`). | All B-6 spring channels use it: drag ghost X/Y/scale and folder popup progress. Reduce motion runs the same settle/finalization callbacks immediately. |
| Plane ownership is frozen at DOWN and content-owned points fully defer it (`AppDrawerPlaneView.java:280-295`). Content closes converge through the plane forwarders/controller (`AppDrawerController.java:373-397`). | An icon stream remains content-owned even before it becomes a drag. B-6 never makes the plane intercept a RecyclerView stream and never synthesizes `ACTION_CANCEL`. Existing close callbacks remain the only route into drawer close. |
| Content records view type, category presentation/part, active recycler, and raw Y at DOWN, then routes close through nested scrolling (`AppDrawerContentView.java:1053-1139`, `:1176-1222`). | Extend this snapshot with stream token, source stable ID, drag eligibility, horizontal page, and category expansion state. Do not re-read any of them mid-stream. Scroll/close/page/category/drag claims are one-way. |
| B-4's pager locks horizontal layout before axis resolution and carries a terminal-dispatch suppression latch (`AppDrawerHorizontalPagerView.java:105-143`, `:146-187`). | Preserve the P1 fixes. Drag suppression gets the same two-level lifetime: a stream latch plus a `terminalDispatch` latch set before `super.dispatchTouchEvent(UP/CANCEL)` and cleared only in `finally`, so reentrant adapter/interactivity resets cannot expose the retained click. |
| The vertical grid's scroll-vs-close state is sampled at DOWN and handled in `NestedScrollingParent3` (`AppDrawerContentView.java:1135-1147`, `:1163-1231`, `:1275-1315`). | B-6 does not change first-overpull/second-pull behavior. Movement that starts a nested scroll/close cancels the pending long press; a drag that latches before movement prevents the recycler from starting one. |
| Categories have a separate one-way policy and click gate (`AppDrawerCategoryGesturePolicy.java:5-30`, `:44-97`; `AppDrawerCategoryView.java:369-433`, `:576-582`). | Category mode is explicitly non-draggable. Its current overview action, scroll/close, detail scroll/collapse, transition swallowing, and click suppression stay byte-for-byte routed as B-5. |
| App cells centralize icon/tint/launch/long-press and unbind all app-specific state (`AppDrawerAppCellView.java:76-94`, `:122-136`). Both vertical and horizontal use it (`AppDrawerAppsAdapter.java:145-176`; `AppDrawerHorizontalPageAdapter.java:109-138`). | Add an optional drawer interaction binding to this common app cell. Vertical/horizontal pass the drag coordinator; category call sites pass `null` and retain `bindDrawerAppContextLongPress` exactly. Recycle must also unregister any drag source/target. |
| The current drawer binding calls `bindDrawerAppContextLongPress`, which delegates to the dock gesture with `allowDragPickup=false` (`SuggestionBarView.java:4160-4175`). The underlying gesture shows the menu first and uses a 650ms pickup window (`SuggestionBarView.java:199-201`, `:4190-4213`, `:4246-4277`). | Generalize this seam with a drawer pickup callback and a 2D pickup policy; do not clone the popup implementation. The popup is provisional during the same existing pickup window and is dismissed if pickup wins. Dock remains on its current horizontal-only pinned pickup policy. |
| Dock drag uses framework `startDragAndDrop` and a bar-wide `DragEvent` target (`SuggestionBarView.java:6416-6425`, `:6441-6487`). It creates/merges folders in `applyPinnedDrop` (`:6489-6529`). | Reuse the pure mutation semantics after extraction, not the framework transport. Drawer drag needs an in-plane return spring and pager edge turns, which the system drag shadow cannot provide. Dock transport and reorder behavior stay intact. |
| Folder popup/title already exist, but are dock-owned: fixed `GridLayout`, title click, and `EditText` rename dialog (`SuggestionBarView.java:3990-4076`, `:4116-4129`). Folder member removal already recognizes zero/one collapse (`:6558-6584`). | Extract shared folder content/mutation seams. The dock host retains a `PopupWindow`; the drawer host uses its own in-plane popup and focusless title editor. Collapse moves to repository normalization so neither host can forget it. |
| Popup windows are explicitly non-focusable and declare `INPUT_METHOD_NOT_NEEDED` (`SuggestionBarView.java:5343-5349`). | Existing app context menus remain safe during drawer use. The B-6 rename UI is even stricter: it stays inside the drawer plane and contains no text editor, so it cannot own an `InputConnection`. |
| Drawer search already has the required focusless three-channel intake (`AppDrawerSearchController.java:19-49`, `:175-218`, `:227-267`); activity hooks sit before terminal input (`TermuxTerminalViewClient.java:336-350`, `:654-666`). | Put a router in front of this existing controller. Search behavior is unchanged when rename is inactive; rename consumes all three channels when active. |
| The system-IME fallback deliberately shows the IME on the still-focused terminal and avoids `beginExternalTextInput()` because that calls geometry sync (`TermuxActivity.java:9920-9933`). | Rename requests this exact keyboard path. `TerminalView` remains the sole input-connection owner, and committed code points are intercepted before reaching the shell. There is no `requestAccessoryGeometrySync()`. |
| `PinnedFolderItem` already carries stable ID, title, rows, cols, tint, and member apps (`PinnedFolderItem.java:8-29`). Repository schema v4 stores folder bodies inline in dock `items` (`LauncherConfigRepository.java:20-22`, `:64-108`, `:119-172`). | Keep the model, normalize persistence in schema v5, and preserve source-compatible `loadPinnedItems` / `savePinnedItems` for the dock/editor. |
| `savePinnedItems` is called from both `SuggestionBarView` and the separate `PinnedAppsEditor` (`SuggestionBarView.java:4085-4095`; `PinnedAppsEditor.java:411-430`). | Schema-v5 dock saves must preserve unpinned drawer folders. Removing a folder from the pinned editor removes only the dock reference, never the shared record. |
| Rendered icons share a 6–16MiB byte-budgeted LRU; the public getter returns the actual budget (`SuggestionBarView.java:2640-2651`). Category tiles already bind lazily and release drawables (`AppDrawerCategoryTileView.java:107-140`). | Folder previews, popup cells, and the drag ghost call only `getRenderedIcon`; there is no folder cache or bitmap snapshot. Recycled/hidden views clear drawables. The ghost holds one existing drawable reference for one stream. |
| Production compiles as Java 11 and tests use JUnit 4.13.2/Robolectric 4.13 (`app/build.gradle:111-125`, `:150-178`); no dynamic-animation dependency is declared. | All new production and test sources are Java. Add no Kotlin plugin/source and no `androidx.dynamicanimation`; use the existing RecyclerView 1.1.0 and house `Spring` only. |

## 2. Folder persistence and root-list semantics

### 2.1 Schema v5: normalized folder records plus dock references

Keep the existing preference key and JSON root, bump `LauncherConfigRepository.SCHEMA_VERSION` from 4 to 5,
and write this shape:

```json
{
  "schemaVersion": 5,
  "folders": [
    {
      "id": "uuid",
      "title": "Work",
      "rows": 3,
      "cols": 3,
      "tintOverrideEnabled": false,
      "tintColor": -14671840,
      "apps": [ { "packageName": "...", "activityName": "...", "userId": 10 } ]
    }
  ],
  "items": [
    { "type": "app", "packageName": "...", "activityName": "..." },
    { "type": "folderRef", "folderId": "uuid" }
  ],
  "appIconOverrides": []
}
```

`folders` is the canonical registry. `items` remains dock order and owns placement only. On load, a
`folderRef` is hydrated to a defensive `PinnedFolderItem` copy so existing dock APIs keep returning
`List<PinnedItem>`; object identity in Java is not relied on. “Same object” concretely means one folder ID and
one serialized body, not two records which happen to use the same model class.

Repository additions:

- `LauncherConfigSnapshot loadSnapshot()` returns immutable dock items plus folder records keyed by ID.
- `List<PinnedFolderItem> loadFolders()` and `PinnedFolderItem loadFolder(id)` return defensive copies.
- `FolderMutationResult createFolder(first, second)`, `addAppToFolder`, `renameFolder`,
  `removeAppFromFolder`, `deleteFolder`, and `reconcileFolders(installedStableIds)` all do one latest-root
  read/normalize/write transaction and return the new snapshot plus collapse outcome.
- `savePinnedItems` remains source-compatible. It updates `items`, upserts bodies for any folder objects passed
  by old dock call sites, and preserves registry records not referenced by the new dock list. It never treats
  “not pinned” as “delete folder.”
- All mutation entry points enforce profile-aware `AppRef.stableId()` uniqueness, preserve existing per-member
  icon overrides, reject a source already in another folder, and reject an addition at 36 with a visible
  “Folder is full” result. The UI never mutates `folder.apps` directly after this slice.

### 2.2 Migration of existing data

Migration runs inside the first successful schema-v5 read and immediately writes v5 once:

1. Parse v1–v4 `items` with the existing permissive rules.
2. For each inline folder in dock order, sanitize/retain its ID, body, profile fields, tint, rows, cols and icon
   overrides; append one canonical `folders` record and replace the inline item with `folderRef` at the same
   dock index. Duplicate folder IDs are deterministically renamed with a generated UUID; their dock slots remain.
3. A member stable ID may belong to only one folder. First occurrence in dock order wins; later duplicates are
   removed, then zero/one normalization runs. This resolves data the current schema could represent ambiguously.
4. A legacy folder containing more than 36 members is **not truncated**. It is preserved and rendered in full,
   but additions are rejected until its count falls below 36. New folders and folders at or below the limit use
   the 36-item cap. Migration never discards user data to satisfy a new invariant.
5. Invalid/missing folder refs are dropped from dock order. Orphaned canonical folders are valid drawer folders,
   not corruption and not garbage-collected.
6. Preserve `appIconOverrides` exactly; the existing read-modify-write behavior at
   `LauncherConfigRepository.java:119-123` remains a regression requirement.

### 2.3 Drawer root items

Add a pure `AppDrawerItem` (`APP` or `FOLDER`) rather than synthesizing a fake `LauncherAppEntry`:

- In empty-query `VERTICAL`/`HORIZONTAL`, build root items from every canonical folder plus every catalogue app
  not contained in a folder. Sort by display title case-insensitively, then type, then stable ID. A folder is one
  root cell; its members are not duplicated beside it.
- Non-empty search remains the shipped app search over the full catalogue, including folder members. It returns
  ordinary app cells and never folder-title results in B-6. This keeps ranking, Enter, and all three input-channel
  behavior identical outside rename.
- The vertical A-Z section index includes folder titles only in the empty root list. During search the rope stays
  inactive exactly as shipped.
- `CATEGORIES` receives the same catalogue and bucket classifier it receives in B-5: folders are not injected,
  and member apps remain visible in their existing buckets. This is why category grouping/duplicates require no
  new persisted category placement rule.
- Dock-created folders immediately appear in both supported drawer modes; drawer-created folders are not pinned
  until the user explicitly pins them through the folder popup action.

## 3. Drag mechanism

### 3.1 Choice: child-owned long-press pickup + in-plane drag layer

Use a custom `AppDrawerDragCoordinator` and `AppDrawerDragLayer` inside `AppDrawerContentView`. The app cell that
received DOWN remains the platform touch target. The common long-press binder reports “long press ready”; a MOVE
inside the existing 650ms pickup decision window may latch drag. Only then does the cell call
`requestDisallowInterceptTouchEvent(true)` so the recycler cannot begin a scroll after pickup. This is not parent
interception or touch stealing: before the latch, RecyclerView/nested scrolling sees the ordinary stream; after
the latch, the child that already owns it continues to own it.

The layer draws one raised ghost at raw-pointer coordinates from the existing rendered drawable. It does not
reparent the source view, create a bitmap, call `startDragAndDrop`, or register a window-global drag target.
Attached target cells expose stable item IDs and icon hit rectangles to the coordinator; no target `View` is
retained as persistent state.

Alternatives are rejected decisively:

- Framework `startDragAndDrop` is correct for the one-row dock and remains there, but its system-owned shadow
  cannot run the required house-Spring return, does not compose cleanly with page-edge turns, and broadens a
  drawer-local operation into window drag events.
- `ItemTouchHelper` is a reorder mechanism for one RecyclerView. It cannot span full-page `GridLayout` holders,
  cannot target the in-plane folder popup, and claims by RecyclerView interception.
- A parent `onInterceptTouchEvent` drag would recreate the exact 1.0×/1.15× slop race that B-2 removed and violate
  the nested-scroll-only constraint.
- Bitmap snapshots would double icon memory outside the byte-budgeted LRU and are unnecessary.

### 3.2 Pickup versus the existing context popup

The dock's existing interaction is the contract: long press opens the context popup, then a pickup movement
within `PICKUP_DECISION_WINDOW_MS` dismisses it and starts drag (`SuggestionBarView.java:4198-4213`,
`:4246-4277`). Generalize `bindContextLongPressGesture` with a `PickupDelegate` and `PickupAxisPolicy`:

- Dock pinned row: existing `HORIZONTAL_ONLY`, pinned-index `startPinnedDrag`; no threshold or behavior change.
- Drawer vertical/horizontal app root cell: `RADIAL_2D`; the popup opens provisionally on long press. A movement
  of at least one touch slop in any direction, before the 650ms deadline and before any other claim, latches
  `DRAG`, clears menu highlight, dismisses the context popup, and starts the in-plane coordinator.
- If the deadline expires first, the popup becomes definitive and drag eligibility ends. Slide-to-select,
  drag-back-to-cancel, and release bounce continue through the same dock implementation.
- Releasing after long press without pickup leaves/operates the popup exactly as today. A cell never receives two
  independent long-press listeners.
- Folder cells are drop targets but not drag sources in B-6. Category calls keep the existing no-pickup delegate.

### 3.3 Drop behavior and motion

- App → different app: create `PinnedFolderItem(UUID, "Folder")`, target first/source second, remove both apps
  from the supported drawer root through membership, persist atomically, rebind, then open the new folder popup.
- App → folder: append when unique and below the effective cap, persist, rebind, and pulse/open that folder.
- App → itself, gap/chrome/letter column/pill/dots, folder at cap, stale/uninstalled target, or CANCEL: no mutation.
- A valid target is the target cell's actual icon/preview bounds expanded by 8dp, not its whole page slot. Target
  highlighting is draw-only scale/outline and is cleared before any adapter submit.
- Invalid/cancelled drop: X, Y and scale return to the source rect through house Springs
  (`Spring(0, 900, 60)` for position error, `Spring(1, 420, 41)` for scale), then the ghost releases its drawable.
  In horizontal mode, if edge-turn moved away, snap back to the frozen source page first and resolve the source
  rect by stable ID; if the source vanished, spring/fade to the frozen pickup rect.
- Successful drop: the ghost springs into the target centre and shrinks; repository mutation/rebind occurs at
  settle, not halfway through the animation. Reduced motion applies the mutation immediately and runs identical
  cleanup.
- Folder popup open/close uses one `Spring(0, 420, 41)` progress channel: alpha 0→1, scale 0.86→1, and a small
  anchor-centre translation. Its final bounds are laid out once; per-frame writes are transforms/alpha only.

## 4. Exact four-way gesture arbitration

The four competitors are **ordinary scroll**, **drawer close**, **the active view type's own gesture**, and
**drag-and-drop**. All use one frozen DOWN record and one-way claims.

### 4.1 Frozen DOWN snapshot

For every stream capture after dispatching DOWN, as the current content code requires:

`streamToken`, `viewType`, `categorySearch`, `categoryPart`, `categoryExpansionState`, active recycler,
`atTopAtDown`, `scrollableAtDown`, `touchRegion`, horizontal logical page, source `AppDrawerItem.stableId`, source
rect, `dragEligible` (only an APP root cell in vertical/horizontal), close-arming timestamp, raw X/Y, and the
current query identity. None is recomputed for that stream. Preference reload/package mutation first cancels the
stream; it never changes the snapshot in place.

### 4.2 One-way claim rules

1. **DOWN on chrome:** the plane owns it from DOWN. Its existing close arbiter runs; no source item exists and
   drag cannot arm.
2. **DOWN on vertical root cell:** the plane defers. Before long press, nested vertical scrolling and the shipped
   first-overpull/second-pull close policy are unchanged. Movement past slop cancels long press. If the long press
   fires while no motion claim exists, the 650ms pickup window opens; radial pickup latches `DRAG`. Once latched,
   vertical scroll, overpull, close, tap, and A-Z are disabled until terminal dispatch completes.
3. **DOWN on active A-Z column:** the frozen `COLUMN` claim remains the per-view gesture. It scrubs for the entire
   stream and cannot become scroll, close, or drag even if the finger crosses a cell.
4. **DOWN on horizontal root cell:** the plane defers and the pager snapshots its axis arbiter. Before long press,
   `PAGE_SWIPE` and `DRAWER_DRAG` use the shipped thresholds and remain one-way. Either claim cancels long press.
   If long press/pickup wins first, the pager layout manager stays locked, the nested close relay never starts,
   and only drag edge-turns may change page. Later diagonal drift cannot page or close.
5. **Neutral diagonal before long press:** once total distance exceeds slop without meeting page/close dominance,
   latch `MOTION_CANCELLED`: no page and no close, but tap/long-press/drag are suppressed. This prevents a late
   long press from converting the same neutral stream into drag and preserves B-4's no page+close guarantee.
6. **DOWN anywhere in Categories:** `dragEligible=false` is frozen. The B-5 claim owns the whole stream:
   overview action/scroll/close, detail scroll/collapse, transition body, or chrome close. A long press on a large
   preview/detail/search app opens the shared app popup only. It cannot change expansion state except through the
   existing tap/Back/collapse paths.
7. **Folder-popup content:** while open, popup rect is content-owned and the drawer plane defers. Member list
   scrolls normally; title tap enters rename; app tap launches; outside-popup tap closes the popup. A downward
   swipe begun outside the popup stays plane chrome and closes the drawer. No drag starts inside the popup in B-6.
8. **Terminal UP/CANCEL:** before calling `super.dispatchTouchEvent`, set
   `suppressClickDuringTerminalDispatch(streamToken)` if drag or another non-tap claim ever occurred. Finish
   drag/drop, which may synchronously persist, submit adapters, open a popup, or set drawer interactivity false.
   Only the outer `finally` clears the terminal latch. Ordinary `reset`, `setInteractive`, adapter unbind, pager
   reset, and category reset are forbidden from clearing it. This pins the second B-4 P1 class.

### 4.3 Horizontal edge-turn details

`AppDrawerHorizontalEdgeTurnPolicy` receives the frozen layout direction/page count, pointer X, pager bounds and
clock. It is active only for claim `DRAG` and only when no valid target is under the pointer. A 32dp zone and
450ms dwell return `PREVIOUS`, `NEXT`, or `NONE`; the coordinator calls the pager's existing page-position API,
waits for idle/snap, clears stale target highlights, then re-resolves attached targets. The original gesture's
axis claim is not restarted. Page dots follow the pager's existing selection callback. No edge turn occurs from
top/bottom edges, no wrap occurs, and pointer velocity does not bypass dwell.

## 5. Folder popup and focusless rename

### 5.1 Popup structure

`AppDrawerFolderPopupView` is the drawer's modal child above all three app surfaces but below the plane clip:

- final-position rounded panel using the drawer's resolved radius/tint;
- tappable title row plus optional pin/unpin action;
- `RecyclerView(GridLayoutManager)` for all resolved members, adaptive 3–6 columns, item cache 0, prefetch off,
  no platform overscroll, and max height inside the already measured drawer content;
- cells reuse `AppDrawerAppCellView` app icon/tint/launch/long-press binding with drag disabled;
- outside hit area closes the popup; Back hierarchy is rename → popup → query → category detail → drawer;
- while rename is active, a tap elsewhere inside the panel commits and is consumed while leaving the popup open;
  a scrim tap commits, consumes that tap, and closes the popup only after the write/rebind succeeds;
- package refresh reconciles by folder ID, dismissing if it collapses/deletes and preserving first visible member
  stable ID/offset otherwise.

The dock's `showFolderPopup` is refactored to use the same recycled member adapter/metrics and repository
mutation callbacks inside its existing non-focusable `PopupWindow`. Dock title tap may keep the existing
focusable settings/dialog path while the drawer is closed; the no-`EditText` rule applies to the drawer path.
The fixed `GridLayout`/`cellCount` truncation is removed.

### 5.2 Rename routing and InputConnection proof

Add `AppDrawerInputRouter implements TerminalKeyEventHandler.KeyValueInterceptor`. The activity installs this
router, not `AppDrawerSearchController`, in its existing single in-app-keyboard interceptor slot. Hardware and
IME hooks call the same router. Dispatch order is:

1. command palette (unchanged, still closes/outranks drawer);
2. active folder rename;
3. drawer search;
4. terminal search/shell.

`AppDrawerFolderRenameModel` owns original title, candidate string and code-point caret. It accepts the same
character/string/editing/slider/key-event forms as search and uses
`CommandPaletteSoftKeyDecision.decide(open, false, codePoint, ctrlDown)` for IME input. It truncates insertions at
48 code points without splitting a surrogate pair. Commit trims once and calls `renameFolder(id, normalized)`;
cancel writes nothing. Repository success updates popup, vertical/horizontal folder cells, and any dock preview
from a fresh snapshot.

The title UI is a non-focusable custom-drawn/TextView surface; it only paints candidate text and caret. It is
not an `EditText`, does not implement `onCreateInputConnection`, never calls `requestFocus`, and never changes
window soft-input mode. When a keyboard is needed it invokes the existing
`TermuxActivity.requestAppDrawerSearchKeyboard()` path (`TermuxActivity.java:9920-9933`): the already-focused
`TerminalView` keeps its `InputConnection`, IME commits arrive at `TermuxTerminalViewClient.onCodePoint` and are
claimed by the router before shell dispatch. The in-app keyboard already arrives through the interceptor and
hardware keys through `onKeyDown`; neither requires focus. The path does not call
`beginExternalTextInput()` or `requestAccessoryGeometrySync()`. The system-IME host relayout may update only the
drawer open rect through `AppDrawerController.onHostLayoutChanged`; accessory bands remain frozen.

## 6. Concrete class-by-class design

### 6.1 New production classes

| Class | Kind | Responsibility |
|---|---|---|
| `LauncherConfigSnapshot` | pure immutable | Canonical schema-v5 folder map plus hydrated dock order; defensive copies and ID lookup. |
| `LauncherFolderNormalizer` | pure | Unique membership, 36-item admission, legacy-over-cap preservation, zero/one collapse, and dock-ref replacement/removal. |
| `AppDrawerItem` | pure immutable | `APP`/`FOLDER`, stable ID, display title, section letter, app/folder payload; no synthetic package names. |
| `AppDrawerRootModel` | pure | Merge sorted catalogue with canonical folders, hide members in supported empty-query roots, and build folder-aware section positions. |
| `AppDrawerDragGesturePolicy` | pure | Frozen DOWN + one-way `PENDING/SCROLL/CLOSE/PAGE/PER_VIEW/CONTEXT/DRAG/MOTION_CANCELLED` claims and pickup deadline. |
| `AppDrawerDropTargetPolicy` | pure | App/folder/self/cap/stale hit result from stable IDs and half-open icon bounds. |
| `AppDrawerHorizontalEdgeTurnPolicy` | pure | 32dp/450ms dwell, page/RTL bounds, repeat-after-settle semantics. |
| `AppDrawerDragCoordinator` | stateful, no View inheritance | Stream token, source/target IDs, pickup/context arbitration, click-suppression lifetime, edge turn, repository mutation, and Spring return/commit state. |
| `AppDrawerDragLayer` | draw-only `View` | Draw one cached drawable ghost and target halo; transforms only; no bitmap allocation or touch interception. |
| `AppDrawerFolderCellView` | `ViewGroup` | Four-child preview drawn from shared rendered icons, title, tap popup, drop target, complete unbind. |
| `AppDrawerItemCellView` | small host | Switch between the existing app cell and folder cell without duplicating app binding. |
| `AppDrawerFolderPopupMetrics` | pure immutable | Adaptive columns, popup bounds/viewport, member icon size, drawer radius clamp, dock rows×cols viewport. |
| `AppDrawerFolderPopupAdapter` | `RecyclerView.Adapter` | Reused drawer/dock member grid, shared app binder, stable-ID anchor, no drag, unbind on recycle. |
| `AppDrawerFolderPopupView` | `ViewGroup` | Drawer modal popup, member recycler, title editor surface, pin/unpin, outside hit region, open/close Spring presentation. |
| `AppDrawerFolderRenameModel` | pure | Original/candidate/caret, Unicode-safe edit operations, 48-code-point cap, commit/cancel normalization. |
| `AppDrawerInputRouter` | three-channel router | One interceptor/hardware/IME entry; rename-first then existing search. |

### 6.2 Existing model/repository/dock classes

`PinnedFolderItem` (`:8-29`): add `MAX_ITEMS = 36`; keep `MAX_GRID = 6`, default 3×3 and all fields. Rows/cols
remain dock popup viewport metadata, not membership capacity.

`LauncherConfigRepository` (`:20-172`): schema/migration and mutation APIs from section 2. Reads and writes remain
forward-compatible with unknown root fields. `pruneInvalidIconOverrides` must traverse canonical `folders`, not
inline dock items, while preserving its app-wide override behavior (`:230-277`).

`SuggestionBarView`:

- extract folder create/add/remove/rename normalization from `applyPinnedDrop`/`removeAppFromFolder`
  (`:6489-6529`, `:6558-6584`) into repository-backed mutations used by both transports;
- generalize `bindContextLongPressGesture` with pickup delegate/axis policy while leaving every dock call site on
  its current values; expose a drag-aware drawer binding alongside `bindDrawerAppContextLongPress`;
- add `reloadSharedFolderSnapshot()` and a change callback to the drawer; all old `persistPinsAndReload` paths
  re-read canonical entities rather than retaining mutated inline copies;
- refactor `showFolderPopup` from fixed grid to the shared recycled adapter so overflow scrolls;
- change dock folder context “Delete” (`:4602-4615`) to “Remove from dock”; actual entity delete is a drawer
  folder-popup action with explicit wording;
- keep the dock's framework `startPinnedDrag` and bar `DragEvent` transport unchanged; only its drop mutation
  destination changes;
- make `createFolderPreviewButton` (`:3381-3427`) use `getRenderedIcon` rather than raw entry drawables and clear
  all four preview references on rebuild.

`PinnedAppsEditor` (`:122-134`, `:411-430`): continues presenting hydrated folder items, but saving writes dock
refs/upserts edited bodies and preserves every unpinned canonical folder. Folder mode creates one canonical folder
plus one dock ref. Unselecting a folder unpins it only.

### 6.3 Existing drawer classes

`AppDrawerAppCellView` (`:58-94`): add an overload accepting optional drag binding and `AppDrawerItem` identity.
No binding means its existing launch/context behavior byte-for-byte. `unbind` also unregisters source/target and
clears pressed/drag transforms.

`AppDrawerAppsAdapter` and `AppDrawerHorizontalPageAdapter`: evolve their visible data to `AppDrawerItem`. Page
partition and row-major order are unchanged; both create `AppDrawerItemCellView`. Query/category paths wrap
results as app items. Every last-page unused cell still unbinds. Horizontal exposes attached stable-ID bounds for
drop hit testing and source lookup after page turns.

`AppDrawerSectionIndex`: add a root-item builder/lookup while retaining current app-only methods and output for
category search/B-3 tests.

`AppDrawerHorizontalPagerView` (`:24-36`, `:105-187`): accept the frozen stream token/drag coordinator. When drag
latches, keep layout locked, stop any pending snap without fabricating a cancel, and expose logical page/idle/
direction helpers to edge-turn policy. Its existing terminal click latch remains and ORs with the coordinator's.

`AppDrawerCategoryTileView` (`:107-133`) and `AppDrawerCategoryDetailAdapter` (`:49-57`): deliberately keep their
existing no-pickup app binding. Add tests, not drag code. Category search passes the same no-pickup flag.

`AppDrawerContentView`:

- receive canonical snapshot/repository, build folder-aware roots only for empty-query vertical/horizontal, and
  keep category buckets/search app-only;
- add drag layer and folder popup last, before bringing fixed chrome to front; both remain within content bounds;
- freeze the expanded DOWN record from section 4 in `dispatchTouchEvent`, preserving its current old-stream/new-
  DOWN ordering (`:1055-1132`);
- route attached targets, raw motion and terminal dispatch to the coordinator without interception;
- OR coordinator suppression into every app/folder click gate; never clear it from `setInteractive`, mode reset,
  category reset, adapter submit, or unbind;
- horizontal edge-turn only after drag latch; vertical has no auto-scroll in B-6 (users scroll to a target before
  pickup); categories never registers drag sources/targets;
- popup open means content owns popup points and chrome owns outside points at the next DOWN; Back/rename/input
  hierarchy follows section 5;
- advance drag-return and folder-popup Springs from `advanceDrawerFx`; reset releases ghost/popup drawables before
  content becomes invisible.

`AppDrawerController`:

- own/install `AppDrawerInputRouter`; `getSearchController` remains for tests/callers, but the activity interceptor
  obtains the router;
- hardware/code-point methods delegate to the router; search host behavior is unchanged when rename is inactive;
- pass the activity's one `LauncherConfigRepository` to content and re-drive a folder snapshot on every open and
  folder mutation;
- `doFrame` gains no clock: B-6 motion is part of existing `fxMoving`;
- `onBackPressedInDrawer` asks content's rename/popup hierarchy before its shipped query/category hierarchy;
- `onClosed` cancels rename, drag, edge dwell and popup inside the existing content reset before the `finally`
  geometry flush. `applyFrame`, captured bands and accessory choreography are not changed.

`TermuxActivity` (`:9891-9912`): install the router in `setAppDrawerInterceptorActive` and rename the internal
delegates from search-specific to drawer-input-specific without changing their order in
`TermuxTerminalViewClient`. The keyboard request method is reused unchanged and no geometry method is added.

`AppDrawerPlaneView`, `AppDrawerCloseArmingPolicy`, `AppDrawerCategoryGesturePolicy`,
`AppDrawerCategoryExpansionModel`, `AccessoryStackLayoutPolicy`, `TerminalView`, and `activity_termux.xml` are
not modified for feature behavior. The existing overlay hierarchy remains the structural proof.

## 7. Numbered implementation order

1. **Pure persistence contracts first.** Add `MAX_ITEMS`, snapshot/normalizer, schema-v5 parser/writer and v1–v4
   migration tests. Make old repository APIs preserve unpinned folders before touching UI.
2. **Move all folder mutations behind the repository.** Convert dock drag, member removal, rename, folder editor,
   pin editor and uninstall reconciliation. Run existing dock/persistence/icon-override tests; verify “remove from
   dock” does not delete the entity and zero/one collapse updates every reference.
3. **Root data model.** Add `AppDrawerItem`, root builder and section-index extension. Test member hiding, folder
   sorting/A-Z, category/search exclusions, profile identity and snapshot refresh.
4. **Generic root cells.** Add folder/item cell views and convert vertical/horizontal adapters. Pin existing app
   binding, page partition, A-Z scrub appearance, query, launch and long-press behavior before enabling drag.
5. **Pure drag policies.** Add gesture/drop/edge-turn policies and exhaustive one-way-claim tests, including the
   neutral diagonal and terminal-dispatch latch contracts.
6. **Generalize the dock long-press seam.** Add pickup delegate/axis policy, retain all old dock values, and wire
   only vertical/horizontal root app cells to radial drawer pickup. Prove category calls still use no pickup.
7. **Drag layer/coordinator.** Implement ghost/target drawing, stream tokens, attached-target registry, Spring
   return/commit, mutation handoff and reentrant click suppression. Do not add interception or framework drag.
8. **Horizontal edge turn.** Add pager logical-direction/idle helpers and coordinator dwell scheduling. Verify
   snap/dots and B-4 page-vs-close latches before continuing.
9. **Shared popup member grid.** Replace dock truncation with recycled scrolling content, then add the in-plane
   drawer popup and Spring open/close. Confirm both hosts use the shared cache and unbind behavior.
10. **Focusless rename/router.** Add rename model/title UI/input router, wire all three channels, Back hierarchy,
    persistence commit/cancel and system-IME reveal. Add explicit focus/InputConnection/geometry tests.
11. **Content/controller lifecycle.** Wire snapshot refresh, folder collapse/package change, mode/query switch,
    close/HOME/palette/rotation teardown, and the existing controller frame loop. Keep plane/accessory code
    untouched.
12. **Full regression and device pass.** Run focused JUnit4/Robolectric, both app variants' complete unit suites
    and read XML; build both variants; execute section 9 with resize logs, frames and memory.

## 8. Tests

Pure JUnit4:

- `LauncherConfigRepositoryFolderSchemaV5Test`: exact normalized JSON; v1–v4 inline-folder migration; dock order,
  profile fields, tint/grid/member overrides and app-wide overrides preserved; duplicate/missing IDs; orphan
  folders retained; old APIs round-trip; dock save cannot erase unpinned folders; migration writes once.
- `LauncherFolderNormalizerTest`: app stable ID belongs to at most one folder; 36 accepted/37th rejected; legacy
  37 preserved but cannot grow; duplicate add idempotent; zero deletes refs; one replaces every ref in-place with
  preserved override; uninstall reconciliation; clone/primary independence.
- `AppDrawerRootModelTest`: empty V/H hide members and add one sorted folder item; folder title section letter;
  query/category keep full app catalogue; dock-created folder appears; unpin leaves drawer folder; rename reorders.
- `AppDrawerDragGesturePolicyTest`: each DOWN snapshot field frozen; motion before long press chooses scroll/close/
  page/per-view and permanently vetoes drag; provisional popup + radial movement within 650ms chooses drag; at
  651ms popup wins; neutral diagonal becomes motion-cancelled; direction drift never changes claim; category is
  never eligible.
- `AppDrawerDropTargetPolicyTest`: app→app, app→folder, self, expanded hit padding, gap, stale target, duplicate,
  full/legacy-overfull folder and half-open edges.
- `AppDrawerHorizontalEdgeTurnPolicyTest`: 31/32dp boundary, 449/450ms, RTL, first/last page, valid target veto,
  one turn per settle+dwell, exit/re-entry reset, non-drag no-op.
- `AppDrawerFolderRenameModelTest`: seeded title/caret; code-point insertion/delete/movement; surrogate safety;
  48-code-point cap; trim/blank fallback; Enter commit; Esc/Back cancel; original unchanged until commit.
- `AppDrawerFolderPopupMetricsTest`: drawer adaptive columns; dock rows×cols viewport; >viewport content scrolls
  rather than clips; short/large-font/IME-reduced bounds finite; radius and icon size clamps.

Robolectric (`@Config(sdk = P)`, matching shipped drawer view tests):

- `AppDrawerItemCellViewTest`: app path remains identical icon/tint/label/launch/context; folder has four-preview
  cap/title/popup; bind/unbind registers/releases targets and drawables; category no-pickup binding unchanged.
- `SuggestionBarDrawerPickupTest`: dock pinned pickup remains horizontal-only and framework-drag backed; drawer
  popup opens provisionally; timely 2D pickup dismisses it and invokes coordinator once; expired window keeps
  popup/slide selection; no second long-press listener.
- `AppDrawerDragCoordinatorTest`: ghost uses cached drawable without bitmap; target halo; invalid Spring return;
  successful settle mutates once; cancel/multitouch/package removal cleans once; cap message; source recycling safe.
- `AppDrawerDragTerminalDispatchTest`: a successful/closing UP synchronously calls `setInteractive(false)`, resets
  pager/category, submits adapters and unbinds the original cell; that retained cell still cannot launch during
  the same `super.dispatchTouchEvent`. Suppression clears only for the next DOWN. This is the mandatory B-4 P1
  regression test.
- `AppDrawerContentVerticalFolderTest`: root folder placement/A-Z; scroll vs first/second close unchanged; scroll
  cancels long press; drag cancels scroll/close/tap; column scrub can never drag; invalid return; popup/rename.
- `AppDrawerHorizontalFolderTest`: page swipe/down-close neutral-diagonal matrix unchanged; drag latch locks both;
  edge dwell turns/snap/dots/RTL; valid target blocks turn; invalid drop returns to source page; last-page cells
  have no stale targets.
- `AppDrawerCategoriesFolderIsolationTest`: overview large icons, detail cells, and flat category search always
  open context popup and never call drag coordinator; expansion/collapse state and scroll offsets survive long
  press; V/H folder mutations do not alter bucket classification/output.
- `AppDrawerFolderPopupViewTest`: all members reachable beyond viewport; bottom/edge bounds; title tap enters
  focusless editor; member tap/long-press parity; popup Back/outside hierarchy; package refresh/collapse; one house
  Spring and reduce-motion finalization; no per-frame layout/bitmap.
- `AppDrawerRenameIntakeTest`: in-app `KeyValue`, hardware keys and IME code points produce the same candidate;
  rename outranks search; commit writes once/rebinds dock+drawer; cancel writes nothing; palette still outranks;
  search resumes after editor exit.
- `AppDrawerRenameInputConnectionTest`: title is not `EditText`, cannot create an input connection, never becomes
  focused; `TerminalView` focus/input connection stays; keyboard request does not invoke
  `requestAccessoryGeometrySync`; host relayout changes only open rect; no terminal-size callback.
- `SuggestionBarSharedFolderRegressionTest`: v5 hydrated folder preview, reorder, app→folder, app→app, rename,
  choose apps, icon override and notification behavior; unpin vs delete; >rows×cols popup scrolls all members;
  landscape rail still excludes folders (`SuggestionBarView.java:2948-2962`).
- `PinnedAppsEditorSharedFolderTest`: save/reorder/unselect preserves registry; folder mode creates one record/ref;
  concurrent latest-root read preserves a drawer-created unpinned folder.
- `AppDrawerOverlayHierarchyTest` extended: drag/popup/title children remain below existing plane; no new activity
  band; drag/open/rename/edge-turn/close invoke no combined-height, accessory sync, terminal update or SIGWINCH seam.
- Every existing B-2 vertical/search/popup/cache test, B-3 rope/scrub test, B-4 pager/dot/diagonal/click-suppression
  test and B-5 category/expansion/gesture/memory test remains green in both variants.

## 9. Device verification

1. Baseline all three modes before folder creation: vertical scroll/arm/close/A-Z/search, horizontal page/dots/
   diagonal/down-close, category overview/expand/detail-collapse/search. Confirm no behavioral change.
2. Vertical: long-press app and hold past pickup window → app context popup; repeat and move promptly in each of
   four directions → pickup. Drop on gap/self (Spring return), another app (folder), then folder (add). Tap new
   folder, tap title, rename through in-app keyboard; launch and long-press members.
3. Horizontal: page normally, then drag. Dwell at both physical edges in LTR and RTL; one page turns after dwell,
   dots follow, repeated dwell crosses multiple pages only one settle at a time. Drop on new-page app/folder.
   Cancel after edge turn and verify return to source page. Repeat B-4 horizontal→vertical drift and
   vertical→horizontal drift; never page+close or close+launch.
4. Categories while overview, expanding, expanded, detail mid-list and flat search: every app long press opens
   context; no lift/ghost/edge dwell appears; expansion/scroll state does not jump. Switch back to V/H and verify
   the shared folder is present.
5. Rename each intake: in-app keyboard, hardware keyboard if available, and system IME fallback. Enter commits;
   Back/Esc cancels; blank becomes `Folder`; emoji/surrogate edits remain intact. Watch focus: terminal owns it
   throughout and typed rename text never reaches the shell.
6. Pin the drawer folder, rename/add/remove from drawer, and observe the dock update. Unpin through dock context:
   folder remains in drawer. Pin again. Remove members to one: dock slot becomes survivor and folder disappears;
   remove final member from an unpinned folder: entity disappears and app returns to drawer.
7. Fill to 36, reject the 37th with no partial mutation. Set dock viewport 2×3/3×3: preview remains four; popup
   scroll reaches member 36. Seed an oversized legacy fixture if practical: all entries survive migration, but no
   addition is allowed.
8. Install/remove/change a package while dragging, folder popup open, rename active, dock-pinned, and at 0/1
   collapse boundaries. No stale target launches; popup reconciles/dismisses; both surfaces converge on one ID.
9. Close via Back/chrome swipe, HOME, palette summon, rotation, preference-mode change and process recreation
   during popup/rename/drag-return. No ghost, halo, popup, interceptor or invisible touch surface survives.
10. Set `animator_duration_scale 0`: pickup/drop/return and folder popup snap through the same finalizers. Restore
    scale and verify Springs settle without an idle frame loop.
11. Compare `gfxinfo framestats` for repeated drag/edge-turn/popup cycles to B-5 controls. Compare
    `dumpsys meminfo` after scrolling every folder member twice: heap plateaus at the shared 6–16MiB icon budget; closing
    popup releases attached drawables; dock redraw may evict/re-render but cannot grow unbounded.
12. Keep logcat on terminal resize/session-size events for the entire matrix. There must be zero
    `TerminalView.updateSize()`/SIGWINCH caused by drag, page turn, folder popup, rename keyboard reveal or folder
    mutation. After ten cycles, tap/type in the terminal to catch an invisible layer left interactive.

## 10. Risks

1. **Dock entity deletion by old “Delete” semantics.** Once folders outlive dock placement, removing a dock item
   must not delete its record. Relabel and separate unpin/entity-delete mutations; pin with editor/context tests.
2. **Old dock saves erasing unpinned drawer folders.** `savePinnedItems` currently replaces inline folder bodies.
   Its schema-v5 read/merge/write behavior is the critical persistence migration seam.
3. **Same ID, divergent Java copies.** Dock and drawer receive defensive snapshots. No caller may mutate
   `PinnedFolderItem.apps/title` then assume another surface sees it; every change goes through repository and
   rebinds both.
4. **Reentrant close/drop launches the ending cell.** Persistence or popup/open-state reset can unbind/rebind while
   UP is still descending the view tree. The terminal-dispatch token must outlive all resets and clear only in the
   outer dispatch `finally`.
5. **B-4 neutral diagonal page+close regression.** Adding a long-press observer cannot make the pager axis live
   again. Snapshot once, lock until a one-way claim, and use `MOTION_CANCELLED` for moved neutral streams.
6. **Plane/recycler slop race returning.** A drag parent interceptor or plane re-read recreates touch stealing.
   Pickup occurs only in the existing child target; close remains nested.
7. **Long-press popup/pickup drift between dock and drawer.** Two listeners or copied timing would eventually
   disagree. One generalized binder with explicit axis/delegate is mandatory.
8. **Vertical scroll regression.** Calling `requestDisallowInterceptTouchEvent` before the drag latch makes a slow
   list scroll sticky. Call it only after long press plus pickup movement has won.
9. **Horizontal source/target recycling.** An edge turn can recycle both views. Persist stable IDs/plain rects,
   never view references; clear highlight before page motion and re-resolve after snap.
10. **Pager state regression on invalid return.** Returning to source page must use logical adapter page and the
    existing snap helper, not raw X sign; RTL and dots otherwise diverge.
11. **Category accidental drag enablement.** `AppDrawerAppCellView` is shared by detail/search. A default-on pickup
    overload would silently make category detail compete with collapse. Drag must be opt-in only from V/H roots.
12. **Category state reset from global cleanup.** A folder mutation while categories is active must refresh only
    the canonical folder snapshot; it must not submit category adapters, abort expansion or move overview/detail.
13. **Folder cap data loss.** Parsing/writing `subList(0, 36)` is forbidden. Preserve legacy oversized folders,
    reject growth, and make every member reachable in the popup.
14. **Dock popup still clipping overflow.** Merely raising the cap while retaining the fixed `GridLayout` loop
    hides apps. The shared recycler/viewport change is required in the same slice.
15. **Icon heap above the LRU.** Attached folder preview/popup/ghost views can keep evicted drawables alive. Use
    four previews maximum, item cache 0, no prefetch, staged popup/ghost cleanup and no bitmap snapshot.
16. **Cache churn in the dock.** Folder previews introduce small-size keys and a full popup can traverse 36 icons,
    evicting dock-sized entries. This may cause bounded re-render hitch; measure, but do not add a second cache.
17. **Rename stealing the terminal InputConnection.** Any `EditText`, focusable popup/dialog, `requestFocus`,
    `beginExternalTextInput`, or soft-input-mode change violates the design. Test the negative seams explicitly.
18. **Rename/search routing leak.** If both controllers see a character it can rename and filter simultaneously;
    if neither swallows an unsupported key it reaches the shell. One router and strict rename-first dispatch avoid
    both.
19. **Popup/drag animation clock drift.** A view-owned animator or second frame callback can lag the plane and stay
    alive after close. Every B-6 Spring returns motion through the controller's existing `fxMoving` path.
20. **Invisible layer eating terminal touches.** Drag/popup children must be non-interactive and release drawables
    before the existing plane content becomes invisible (`AppDrawerPlaneView.java:205-213`;
    `AppDrawerController.java:1018-1058`).
21. **Accessory geometry/SIGWINCH regression.** Treating the popup/keyboard reveal as an accessory band or calling
    geometry sync would defeat the overlay architecture. All B-6 layout stays local to the plane; the activity
    layout, combined-height policy and terminal sizing stay untouched.

## 11. Open questions for the project lead

None. The locked shared-folder decision plus the shipped view/gesture/input architecture is sufficient to choose
one normalized entity schema, supported modes, 36-item capacity, dock overflow behavior, edge-turn policy,
category isolation, collapse semantics and focusless rename path.
