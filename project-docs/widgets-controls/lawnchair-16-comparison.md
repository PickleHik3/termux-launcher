# Lawnchair 16 vs. this launcher's widgets — what is missing, and what is not

Research record, 2026-09-22. Companion to [`appwidget-host-research.md`](appwidget-host-research.md),
which owns the *platform* facts (preview loading costs, night-mode resolution, the bind/configure
contract) and is not restated here. This document compares two real implementations and proposes
work.

Everything below is either **verified in source** (a file and line you can open) or explicitly
marked **inferred**. Where Lawnchair's behaviour turns on a build flag or a device property that
cannot be evaluated from a checkout, the flag is named and the uncertainty is stated.

## 1. What was read

| | |
|---|---|
| Lawnchair | `github.com/LawnchairLauncher/lawnchair`, branch `16-dev` @ `1c79ee5e7a4cc559b0c81dc7daf0ccf92b60d11c` (2026-09-20, also tagged `nightly`). Shallow clone into the session scratchpad; not in this tree, not committed. |
| Its Launcher3 base | "based on Launcher3 from Android 16" (`README.md:20`); `buildToolsVersion "37.0.0"`, `targetSdk = 37` (`build.gradle:25,33`). AOSP Launcher3 is vendored **at the repo root** — `src/com/android/launcher3/…`, `quickstep/`, `res/` — with Lawnchair's own module in `lawnchair/`. Patches to AOSP files are in-place, usually marked `// Lawnchair:` or `// LC-Note:`. |
| This launcher | branch `dev` @ `6552de81`, `app/src/main/java/com/termux/app/launcher/widget/` (32 files, 7,094 lines) plus `TermuxActivity`/`WidgetPaneFrame` wiring and 51 unit tests. |
| Platform | AOSP `frameworks/base` `refs/heads/main`, fetched 2026-09-22: `AppWidgetHost.java`, `AppWidgetHostView.java`, `AppWidgetManager.java`. |

Throughout, Lawnchair paths are relative to its clone root and this launcher's are relative to the
repo root. **AOSP** means a file outside `lawnchair/`; **LC** means Lawnchair's own code or an
in-place patch to an AOSP file.

## 2. Lawnchair 16's widget stack

### Host and host-view lifecycle — AOSP, thinly wrapped

`LauncherWidgetHolder` (`src/com/android/launcher3/widget/LauncherWidgetHolder.java`) is the
abstraction over the platform `AppWidgetHost`; the host object itself is
`ListenableAppWidgetHost`/`LauncherAppWidgetHost`. Host id is `1024`
(`LauncherWidgetHolder.java:79`). Listening is a **flag lattice**, not a pair of calls:
`FLAG_LISTENING`, `FLAG_STATE_IS_NORMAL`, `FLAG_ACTIVITY_STARTED`, `FLAG_ACTIVITY_RESUMED`
(`:81-87`) all feed `setShouldListenFlag` (`:554-570`), and `startListening`/`stopListening` run on
`UI_HELPER_EXECUTOR` (`ListenableAppWidgetHost.kt:70`) — off the main thread, tolerating binder-size
errors (`LauncherWidgetHolder.java:139-148`).

Two things this buys that a naive host does not have:

- **A view before the host is listening.** `createViewInternal` returns a
  `PendingAppWidgetHostView` placeholder rather than making a binder call when `FLAG_LISTENING` is
  clear (`:496-501`), and a bare `ListenableHostView` when inflation is happening off the main
  thread under the `enableWorkspaceInflation()` aconfig flag (`:502-508`). Binder failures from
  `createView` fall through to `switchToErrorView()` (`:509-527`).
- **View recycling across binds.** `LauncherAppWidgetHost.onCreateView` hands back a view stashed by
  `recycleViewForNextCreation` (`LauncherAppWidgetHost.java:46-63`), which is how
  `recycleExistingView` (`LauncherWidgetHolder.java:473-491`) makes the *platform's* `createView`
  reuse an existing `View` object instead of allocating one.

The host-view class stack is four deep: `AppWidgetHostView` → `NavigableAppWidgetHostView`
(keyboard nav, `DraggableView`/`Reorderable`, suppresses provider padding via `mDisableSetPadding`,
`NavigableAppWidgetHostView.java:149-162`) → `BaseLauncherAppWidgetHostView` (rounded-corner
enforcement, error view, `onLayout` wrapped in try/catch falling back to `switchToErrorView()`,
`BaseLauncherAppWidgetHostView.java:104-139`) → `LauncherAppWidgetHostView` (long-press
interception, scrollable detection, auto-advance, **deferred updates while dragging**,
`LauncherAppWidgetHostView.java:134-141, 189-267, 346-408`).

That last one matters: `beginDeferringUpdates()`/`endDeferringUpdates()` (`:234-247`,
`UPDATE_LOCK_TIMEOUT_MILLIS = 1000`) buffer `mLastRemoteViews` so a provider update arriving
mid-drag does not re-inflate the view under the finger. `dispatchRestoreInstanceState` is wrapped
in try/catch (`:439-445`) against bad provider `Parcelable` state.

**Dead code worth knowing about.** `QuickstepWidgetHolder` — AOSP's sophisticated shared-listener
design — is present but never Dagger-bound in this fork: `quickstep/…/dagger/Modules.kt:73-77` binds
`WidgetHolderFactory` to Lawnchair's own `LawnchairWidgetHolder.Factory` (a subclass that adds
nothing), and `src_no_quickstep/…/Modules.kt` binds plain AOSP. So Lawnchair ships the simpler
holder. Three `import app.lawnchair.LawnchairAppWidgetHostView` statements in AOSP widget files
(`LauncherWidgetHolder.java:69`, `LauncherAppWidgetHost.java:27`,
`PendingAppWidgetHostView.java:74`) are unused leftovers.

**LC additions.** `LawnchairAppWidgetHostView.kt` maps specific provider components to hard-coded
local layouts (`customLayouts`, `:63-65` — currently only Smartspace) and makes `updateAppWidget` a
no-op once a custom view is installed (`:42-45`). `HeadlessWidgetsManager.kt` is a *second*,
separate `AppWidgetHost` (host id `1028`, `:65`) that binds widgets off-screen purely to read their
`RemoteViews` as a data source, persisting the chosen widget id in device prefs (`:110-126`); its
`close()` is `TODO("Not yet implemented")` (`:62`).

### Picker and tray — AOSP, restyled by Lawnchair

`WidgetsFullSheet` owns three parallel adapter/RecyclerView pairs — `PRIMARY`, `WORK`, `SEARCH`
(`WidgetsFullSheet.java:1107-1184`). One-pane vs. two-pane is now a single condition:

```java
private static int getWidgetSheetId(BaseActivity activity) {
    boolean isTwoPane = activity.getDeviceProfile().getDeviceProperties().isTablet();
    return isTwoPane ? R.layout.widgets_two_pane_sheet : R.layout.widgets_full_sheet;
}
```
(`WidgetsFullSheet.java:818-822` — note this is **simpler than the AOSP `main` version quoted in
`appwidget-host-research.md` §2**, which also tested landscape and two-panel state. The 16-dev tree
tests tablet alone.) `shouldRecreateLayout()` (`:970-974`) re-inflates the whole sheet on
fold/unfold.

Grouping is by app, alphabetical, with one LC deviation: `WidgetsListAdapter`'s comparator
special-cases Lawnchair's own package to sort first (`WidgetsListAdapter.java:494-507`) before
falling back to `LabelComparator` and a current-user-first tie-break (`:509-516`). Expansion state
lives in `mWidgetsContentVisiblePackageUserKey`, toggled by `onHeaderClicked` (`:372-397`), and
visible entries are diffed through `DiffUtil` (`:215-261`). In the two-pane sheet a header click
rebinds the right pane instead of expanding inline (`WidgetsTwoPaneSheet.java:507-582`).

Within an app's row, widgets are packed into a table by `WidgetsTableUtils`: sorted widgets-first,
then by `spanY`, then `spanX` (`:47-56`), packed up to `MAX_ITEMS_IN_ROW = 3` while the row's pixel
width fits and the items share a `WidgetPreviewContainerSize` (`:120-165`).

**Search** is main-thread and synchronous, merely deferred one message:
`SimpleWidgetsSearchAlgorithm.doSearch()` posts through a plain `Handler` (`:52`); matching is
`StringMatcherUtility` against the package title (whole app matches) or the item label (`:65-92`).
Results swap in the dedicated `SEARCH` adapter rather than filtering in place.

**Recommendations** are a whole subsystem — `WidgetRecommendationsView` (a `PagedView` of category
tables, capped at `MAX_CATEGORIES = 3`, `:64, 189-254`), `WidgetRecommendationCategoryProvider`
mapping `ApplicationInfo.category` to a bucket on a worker thread (`:41`), and
`WidgetsRecommendationTableLayout` greedily fitting rows into the available height (`:147-175`). The
data comes from the platform app-prediction service via `Favorites.CONTAINER_WIDGETS_PREDICTION`
(`quickstep/…/QuickstepLauncher.java:611-631`). **Not verifiable from the checkout:**
`enable_categorized_widget_suggestions` and `enable_tiered_widgets_by_default_in_picker` are
declared in `aconfig/launcher.aconfig` (`:145-150, 368-373`) with no `state:` field, which by aconfig
convention means disabled unless a build-time overlay flips them. Whether a shipped nightly shows
recommendations at all is therefore unknown from source.

### Previews — the full three-tier ladder

`DatabaseWidgetPreviewLoader.generatePreviewInfoBg` (`:106-138`) implements exactly the precedence
`appwidget-host-research.md` §1 describes, and this is the single most concrete thing Lawnchair does
that this launcher does not:

1. **Generated preview** (API 35): `BuildCompat.isAtLeastV() && Flags.enableGeneratedPreviews() &&
   (widgetInfo.generatedPreviewCategories & WIDGET_CATEGORY_HOME_SCREEN) != 0` →
   `WidgetManagerHelper.loadGeneratedPreview()` → a ready-made `RemoteViews` (`:113-120`).
2. **`previewLayout`** (API 31): clone the provider info and force `initialLayout = previewLayout`
   (`:122-131`), with the in-source comment admitting it is "a hack … since there is no API for
   rendering a preview layout for work profile apps yet".
3. **Bitmap fallback**: `info.previewImage`, else a synthesised grid-lines-plus-icon placeholder
   (`:133-136`, `:160-275`).

Tiers 1 and 2 produce a **live `AppWidgetHostView`** in the tray cell, not a picture:
`WidgetCell.applyPreview` (`:300-308`) builds one via `createAppWidgetHostView()` (`:492-499`) and
binds the `RemoteViews` into it. Everything runs on `UI_HELPER_EXECUTOR` through a `CancellableTask`
delivered back on `MAIN_EXECUTOR` (`:86-97`), and `WidgetCell.clear()` cancels the in-flight request
on recycle (`:233-236`). `WidgetManagerHelper.loadGeneratedPreview` (`:168-181`) is LC-hardened —
it catches `NoSuchMethodError`/`NoClassDefFoundError` as well as `IllegalStateException`, for
platforms missing the API.

Preview cells snap to fixed template sizes (`WidgetPreviewContainerSize.forItem()`,
`widget/picker/util/WidgetPreviewContainerSize.kt:40-99`) and scale the live view into them, rather
than sizing each cell to the widget's own span.

### Drag and drop, placement, reflow — AOSP, one LC escape hatch

Dragging from the tray is a three-party affair. `PendingItemDragHelper.startDrag` (`:113-256`)
builds the drag shadow from bitmap or `RemoteViews` and registers a `WidgetHostViewLoader` as a
`DragController.DragListener` (`:208`). That loader, **during the drag**, allocates a real widget id,
silently binds it with `bindAppWidgetIdIfAllowed`, inflates a real host view and parks it invisible
in the drag layer (`WidgetHostViewLoader.java:79-147`) — so the widget is already live by the time
the finger lifts. If the provider needs configuration it skips the preload entirely (`:87-91`).
`onDragEnd` deletes any unused id (`:47-74`). LC substitutes `LawnchairAppWidgetHostView` for the
drag preview so its own custom-layout widgets drag correctly (`PendingItemDragHelper.java:96-102,
145, 159`).

The drop lands in `Workspace.onDropExternal` (`:3044-3198`), which runs
`cellLayout.performReorder(..., MODE_ON_DROP_EXTERNAL)` and then `addPendingItem(...)` (`:3119`),
followed by `WidgetSizes.updateWidgetSizeRanges` if the span changed (`:3132-3134`).

The reorder algorithm (`celllayout/ReorderAlgorithm.java`) tries three strategies and picks by area:

```java
ItemConfiguration dropInPlaceSolution = dropInPlaceSolution(reorderParameters);
ItemConfiguration swapSolution = findReorderSolution(reorderParameters, true);
ItemConfiguration closestSpaceSolution = closestEmptySpaceReorder(reorderParameters);
if (swapSolution.isSolution && swapSolution.area() >= closestSpaceSolution.area()) return swapSolution;
else if (closestSpaceSolution.isSolution) return closestSpaceSolution;
else if (dropInPlaceSolution.isSolution) return dropInPlaceSolution;
return null;
```
(`:492-513`.) The "swap" path is where the push happens: `findReorderSolutionRecursive` (`:60-126`)
retries with the item **shrunk** alternately in X then Y down to `minSpanX`/`minSpanY` when no
arrangement fits (`:107-116`), and `rearrangementExists` (`:128-200`) pushes the intersecting views
as a *block* along a direction vector derived from where in the cell the finger is
(`attemptPushInDirection`, `:324-361`), then as a block without direction (`:363-413`), then one
view at a time (`:202-219`). Any item with `!lp.canReorder` aborts the whole rearrangement
(`:170-172`). **LC patch:** when the `allow_widget_overlap` preference is on, `rearrangementExists`
short-circuits to `return true` with an empty intersecting set (`:136-140`) — widgets simply overlap.

Initial spans come from `LauncherAppWidgetProviderInfo.initSpans` (`:105-178`): min/max spans from
`minResizeWidth/Height` and (API 31+) `maxResizeWidth/Height`, default span from `minWidth/Height`,
then **`targetCellWidth`/`targetCellHeight` win if they fall inside the min/max range**
(`:157-163`), and everything is clamped to the grid (`:176-177`).

### Resizing — a floating frame, not handles on the cell

`AppWidgetResizeFrame` (959 lines) is a separate view added to the drag layer with four edge handles
(`:74-78`), gesture-exclusion rects on API 29+ (`:217-218`), and a snap threshold of `0.66` of a
cell (`RESIZE_THRESHOLD`, `:490-492`). Min/max span enforcement happens in
`IntRange.applyDeltaAndBound()` (`:903-927`). Crucially, a resize re-enters the *same* reorder
algorithm through `CellLayout.createAreaForResize` (`:556-557`, `CellLayout.java:1582-1611`), so
neighbours are pushed by a resize exactly as by a move. It carries a **reconfigure button**
(`:333-345`, shown iff `LauncherAppWidgetProviderInfo.isReconfigurable()`), and commits on
`onDetachedFromWindow` via `resizeWidgetIfNeeded(true)` (`:577-588`). There is **no haptic feedback
anywhere in this file** (verified by grep).

Two LC preferences reach in here: `forceWidgetResize` promotes any `resizeMode` to `RESIZE_BOTH`
(`:280-284`) and `widgetUnlimitedSize` forces min spans to 1 and max spans to the whole grid
(`:287-295`).

Size options go out through `WidgetSizes.getWidgetSizeOptions` (`:118-138`) — min/max width/height
always, plus `OPTION_APPWIDGET_SIZES` as an `ArrayList<SizeF>` on API 31+ — and are written by
`WidgetSizeHandler.updateSizeRangesAsync` (`:42-57`), which **reads the current options back first
and only writes if the `SizeF` list differs**, with the comment "updating the options is a costly
call as it wakes up the provider process and causes a full widget update" (`:36-40`).
`OPTION_APPWIDGET_HOST_CATEGORY` is never set anywhere in the tree.

### Rounded corners — AOSP `RoundedCornerEnforcement`

`computeEnforcedRadius` (`:102-114`) returns a fixed dimen pre-S; on S+ it reads
`android.R.dimen.system_app_widget_background_radius` and clamps it to the launcher's own maximum
unless `useSystemRadiusForAppWidgets()` is set. It then *finds* the widget's `android:id/background`
view (`:54-68, 143-167`), lets a widget opt out by declaring that id with `clipToOutline=true`
itself (`:73-75`), and clips only the background rectangle (`:86-96`) — not the whole host view. The
same enforcement is applied to tray cells and drag previews (`WidgetCell.java:145`,
`PendingItemDragHelper.java:69,74-75`). LC exposes it as the `rounded_widgets` preference, live-set
through the static `RoundedCornerEnforcement.sRoundedCornerEnabled` (`LawnchairLauncher.kt:224-226`).

### Dynamic colour and dark mode

Dynamic colour is **AOSP skeleton, LC implementation, off in vanilla AOSP**. The hook is
`LocalColorExtractor.newInstance()` resolving `R.string.local_colors_extraction_class`
(`LocalColorExtractor.java:49`), which AOSP ships **empty** (`res/values/config.xml:72`). Lawnchair
overrides it *only for API 31+* (`lawnchair/res/values-v31/config.xml:3` →
`app.lawnchair.AccentColorExtractor`). Every widget host view subscribes on attach and unsubscribes
on detach (`BaseLauncherAppWidgetHostView.java:92-102`), and `onColorsChanged` posts to
`setColorResources` (`LauncherAppWidgetHostView.java:419-421`). The colour source is *not*
`WallpaperColors` directly but Lawnchair's Monet `ThemeProvider`
(`AccentColorExtractor.java:61,82-91`), which itself listens for wallpaper colours, accent-colour
preference changes and `OVERLAY_CHANGED` broadcasts (`ThemeProvider.kt:51-84`).

The `setColorResources` override is a genuine LC bug fix, marked in-source:

```java
// LC-Note: Fix widget idmap theming issue
if (Utilities.ATLEAST_U) {
    RemoteViews.ColorResources colorResources = RemoteViews.ColorResources.create(getContext(), colors);
    …
    super.setColorResources(colorResources);
} else {
    super.setColorResources(colors);   // LC-Note: Fall back for Android 12 impl
}
```
(`LauncherAppWidgetHostView.java:109-131`.) The platform documents the `SparseIntArray` overload as
mapping `system_neutral*`/`system_accent*` resources and warns that "Calling this method will
trigger a full re-inflation of the App Widget"
([`AppWidgetHostView.java:938-950`](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/appwidget/AppWidgetHostView.java)).

Dark text is a **one-shot** read: the `LauncherAppWidgetHostView` constructor calls
`setOnLightBackground(true)` if `R.attr.isWorkspaceDarkText` resolves true (`:104-106`). Nothing
re-reads it. A runtime day/night flip is handled by **recreating the activity**:
`WallpaperThemeManager.kt` funnels `onConfigurationChanged`, `onColorHintsChanged` and
`onColorsChanged` into `updateTheme()`, which calls `activity.recreate()` when the resolved theme
resource has changed (`:64-83`).

### Persistence and restore — the one area Lawnchair is in a different league

Widgets live in the `favorites` SQLite table: `APPWIDGET_ID`, `APPWIDGET_PROVIDER`, `RESTORED`,
`APPWIDGET_SOURCE` (`LauncherSettings.java:276-341`). `RESTORED` is a bit field —
`RESTORE_COMPLETED=0`, `FLAG_ID_NOT_VALID=1`, `FLAG_PROVIDER_NOT_READY=2`, `FLAG_UI_NOT_READY=4`,
`FLAG_RESTORE_STARTED=8`, `FLAG_ID_ALLOCATED=16`, `FLAG_DIRECT_CONFIG=32`
(`model/data/LauncherAppWidgetInfo.java:50-77`).

The restore path: `RestoreDbTask.sanitizeDB` sets every restored widget row to
`FLAG_ID_NOT_VALID | FLAG_PROVIDER_NOT_READY | FLAG_UI_NOT_READY` (`:296-302`);
`restoreAppWidgetIds` consumes the backup agent's old-id→new-id map and rewrites each row
(`:452-553`) using a **throwaway** `AppWidgetHost` (`:220`) rather than the live one. Per-item repair
then happens lazily at load time in `WidgetInflater.inflateAppWidget` (`WidgetInflater.kt:50-182`):
re-resolve the provider by `ComponentName`+user, allocate a fresh id, attempt a silent
`bindAppWidgetIdIfAllowed`, and clear the flags to `RESTORE_COMPLETED` or `FLAG_UI_NOT_READY`.
Meanwhile `PendingAppWidgetHostView` shows a placeholder and self-heals via `reInflate()` once
`WidgetManagerHelper.isAppWidgetRestored` flips (`:207-260`).

Note what is **not** there: `OPTION_APPWIDGET_RESTORE_COMPLETED` appears only as a `//TODO`
(`WidgetManagerHelper.java:56`, bug `b/63667276`), and `requestPinAppWidget` and
`RemoteViews.RemoteCollectionItems`/`setRemoteAdapter` have **zero hits** in the whole tree.
Collection widgets are entirely the platform's job; Launcher3 contributes only the scrollable
detection that stops the workspace stealing the touch stream
(`LauncherAppWidgetHostView.java:189-218, 309-312`).

### Reconfiguration and widget clicks

Reconfiguring a placed widget is offered in three places, all keyed on
`LauncherAppWidgetProviderInfo.isReconfigurable()` = `ATLEAST_P && configure != null &&
(widgetFeatures & WIDGET_FEATURE_RECONFIGURABLE) != 0` (`:228`): the resize frame's button
(`AppWidgetResizeFrame.java:333-345`), a drag-to-target (`SecondaryDropTarget.java:150-159,
255-282` → `DropTargetHandler.kt:43-49`), and an accessibility action
(`LauncherAccessibilityDelegate.java:79, 97-98`). All three call
`startConfigActivity(..., REQUEST_RECONFIGURE_APPWIDGET)` → the platform
`startAppWidgetConfigureActivityForResult` (`LauncherWidgetHolder.java:286`), with a retry that
re-allocates the id on `IllegalArgumentException` (`:278, 298-312`).

Widget *clicks* are intercepted per view: `QuickstepLauncher.java:318` installs a
`QuickstepInteractionHandler` through `setOnViewCreationCallback`, and its
`onInteraction(View, PendingIntent, RemoteResponse)` (`:62-128`) grafts the launcher's own open
animation onto every widget click, blocks clicks during split-select, and sets
`MODE_BACKGROUND_ACTIVITY_START_ALLOWED` before calling `RemoteViews.startPendingIntent` itself
(`:122`).

### Smartspace is not a widget

Two mechanisms, both LC, neither a hosted `RemoteViews`. The default path inflates a native view
(`R.layout.smartspace_container` → `SmartspaceViewContainer` → `BcSmartspaceView`) and pins it
directly into row 0 of the first `CellLayout` with `canReorder=false`, entirely outside
LauncherModel's item system (`Workspace.java:661-689`). The secondary path registers a real
`AppWidgetProvider` with `widgetFeatures="reconfigurable|hide_from_picker|configuration_optional"`
(`lawnchair/res/xml/smartspace_appwidget_info.xml`) whose host view intercepts `setAppWidget` to
substitute a local layout and makes `updateAppWidget` a no-op
(`LawnchairAppWidgetHostView.kt:22-45`). AOSP's plugin-based `CustomWidgetManager` exists but no
class in the tree implements `CustomWidgetPlugin` — dead.

## 3. This launcher today

Read `appwidget-host-research.md` first; much of what follows is that research, implemented.

**Host.** `LauncherAppWidgetHost extends AppWidgetHost`, host id `0x544C`
(`app/src/main/java/com/termux/app/launcher/widget/LauncherAppWidgetHost.java:19`). Two deliberate
departures from the naive host: a shared background `INFLATE_EXECUTOR` installed on every host view
via `setExecutor` (`:31-38, 59`), and `widgetContext()` (`:87-100`), which inflates against the
**application** context (so AppCompat's view factory cannot substitute classes and break
`RemoteViews` action validation) while transplanting the activity's night-mode bits with a sparse
`createConfigurationContext` override. Both are documented in place, both are load-bearing, and
neither has a Lawnchair equivalent — Lawnchair uses the activity context and lives with it.

**Host view.** `SafeLauncherAppWidgetHostView` contains provider failures to one tile: it overrides
`getErrorView()`/`getDefaultView()` to report through a `FailureListener` and paint a local error
tile, and — the subtle part — re-applies that tile **as `RemoteViews`** so the framework's
`mLayoutId` bookkeeping moves off the provider's layout, because up to API 32 the async inflate path
reuses the current child when the next update declares the same layout id
(`SafeLauncherAppWidgetHostView.java:51-64, 101-127`). Only `RuntimeException` is caught; the class
comment enumerates exactly what it cannot recover from.

**Lifecycle and transactions.** `LauncherWidgetHostController` (719 lines) owns start/stop listening
(`:194-217`), the three-stage add transaction, reconciliation, and a `Platform` seam that makes the
whole thing unit-testable (`:60-77, 663-707`). The add flow is a durable state machine, not a
sequence of callbacks: `beginAdd` reserves the cell against a grid revision, allocates the id,
persists a `WidgetAddTransaction`, then binds (`:242-291`); `continueAfterBound` decides configure
through `WidgetConfigurePolicy` and launches it (`:384-411`); `commitActive` writes the final record
(`:414-425`); `abandon` tombstones before deleting, so a process death mid-flow is resumable
(`:427-443`). Configuration goes through the platform's cross-profile-safe
`startAppWidgetConfigureActivityForResult` (`:689`). Reconfiguration of a placed widget is offered
only when `WIDGET_FEATURE_RECONFIGURABLE` is set (`WidgetConfigurePolicy.java:380-386`) — the same
gate Lawnchair uses — and is deliberately routed on its own request code so a cancel cannot delete
the widget (`LauncherWidgetHostController.java:36-37, 320-341`).

**Reconciliation** has no Lawnchair counterpart in this form. `reconcileProviders` (`:508-628`)
walks every record against `host.getAppWidgetIds()` and the live provider info, applying a pure
decision table (`WidgetProviderReconcilePolicy.java`) — tombstone a vanished provider, resume an
interrupted deletion, expire a pending transaction older than 24 hours, resume a configure that was
interrupted — and then **recovers host-owned ids the repository has lost** by placing them on the
first page with room (`:569-607`). This is the same job Launcher3's `RestoreDbTask` +
`WidgetInflater` do, minus the backup-agent id remap.

**Night mode.** `discardHostViewsBuiltInAnotherMode()` (`:134-143`) drops cached host views when the
activity's `uiMode` night bits differ from the ones they were built in, checked on `onStart` and
before every `createHostView`. Lawnchair instead recreates the whole activity.

**Storage.** One JSON blob in `SharedPreferences`, schema version 3, written with `commit()` before
the in-memory snapshot changes (`LauncherWidgetRepository.java:26-66`), with migrations from v1 and
v2 (`:480-524`). Records carry provider, profile serial, state, cell rect, page and the size-options
bundle (`LauncherWidgetRecord.java`). `allowBackup` is a manifest placeholder
(`app/src/main/AndroidManifest.xml:104-105`); there is no backup agent and no id-remap handling.

**Grid and placement.** A single `WidgetGridDefinition` (rows × columns, default 5 × 4,
`WidgetGridDefinition.java:9-14`) with per-page collision universes. New widgets land by row-major
first-fit (`WidgetGridPlacementPolicy.findPlacement`, `:162-179`) on the page the user is looking at
(`WidgetPaneController.java:261-268`). The grid itself is a per-place, per-orientation preference —
`PlaceLayout.widgetRows/widgetColumns` for `PaneWallPage.WIDGETS`, re-applied on every rotation
(`TermuxActivity.java:9962-9968`) and editable from the pane's own border tab
(`TermuxActivity.java:15196-15206`).

**Edit mode** is a modal session on the pane, not a floating frame. `WidgetEditPolicy.snapMove`
(`:53-88`) finds the nearest same-span position to the finger, and if it is occupied, tries to
rehome every blocker into its nearest free hole, largest blocker first (`displace`, `:95-131`),
falling back to the nearest collision-free position. `WidgetEditPolicy.resize` (`:166-200`) walks
one edge in whole-cell steps and only accepts collision-free rects — **a resize never displaces a
neighbour**. Cross-page drag is real: `WidgetDragLayerView` photographs the cell and flies the
picture while the pages turn under it, with a 350 ms dwell in a 24 dp edge band
(`WidgetPaneController.java:365-367, 636-694`).

**Size options.** `WidgetSizeOptionsPolicy.calculate` (`:38-82`) keeps a portrait and a landscape
size under private keys, derives min/max from them, sets `OPTION_APPWIDGET_HOST_CATEGORY` (which
Lawnchair never sets), and on API 31+ writes `OPTION_APPWIDGET_SIZES`. It dedupes against the
previous bundle before `updateAppWidgetOptions` (`:81`, `LauncherWidgetHostController.java:488-505`).
Initial options are computed at add time from the target cell minus the gutter and minus
`AppWidgetHostView.getDefaultPaddingForWidget` (`WidgetPaneController.java:276-289`).

**Picker.** `WidgetProviderCatalogLoader` enumerates providers per profile, filters to
`WIDGET_CATEGORY_HOME_SCREEN` and enabled packages (`:213-231`), sorts by collated app label, and
reports **twice** — app rows first, per-provider detail after (`:242-303`) — with the whole catalog
cached for the session. Spans come from `minWidth/minHeight` plus the provider's default padding,
raised by `targetCellWidth`/`targetCellHeight` on API 31+ (`:269-283`). Artwork is deferred to bind
time and held in a heap-budgeted `LruCache` (`:83-88, 169-190`), keyed per provider per profile, with
a remembered "nothing to show" sentinel. Search folds accents and case on both sides and matches app
labels as well as widget labels (`WidgetPickerSearch.java:425-454`). Previews are a **56 dp square
bitmap** from `loadPreviewImage`, falling back to the provider icon
(`WidgetPickerAdapter.java:45`, `WidgetProviderCatalogLoader.java:174-182, 374-377`).

**Corners.** `WidgetCellView` hard-clips every cell to
`android.R.dimen.system_app_widget_background_radius` on API 31+, 16 dp below
(`WidgetCellView.java:195-212`).

**Tests.** 51 unit test files under `app/src/test/java/com/termux/app/launcher/widget/`, covering
the bind/configure routing tables, repository migrations, placement, edit policy, night mode and
picker integration.

## 4. Gap table

Cost is rough engineering size for this codebase, not Lawnchair's.

| Area | Lawnchair 16 | This launcher | Gap | Does it matter here? | Cost |
|---|---|---|---|---|---|
| **Tray previews** | Generated preview → `previewLayout` → bitmap; live host view in the cell (`DatabaseWidgetPreviewLoader.java:106-138`, `WidgetCell.java:300-308`) | 56 dp bitmap from `loadPreviewImage`, else the provider icon (`WidgetProviderCatalogLoader.java:174-182`) | Both modern tiers missing; previews are small and often just an icon | **Yes.** This is the most visible quality difference and it costs one binder call, not a redesign | M |
| **Preview size** | Cell snapped to a template size, live view scaled into it | Fixed 56 dp square regardless of span | A 4×1 clock and a 2×2 calendar look identical in the list | Yes — shape is how people recognise a widget | S (with the above) |
| **Backup/restore** | `RESTORED` bit field, `sanitizeDB`, old-id→new-id remap, lazy per-item repair, placeholder view that self-heals (`RestoreDbTask.java:242-553`, `WidgetInflater.kt:50-182`) | None. JSON in `SharedPreferences`, no backup agent, no `ACTION_APPWIDGET_HOST_RESTORED` handling | A restored device loses every widget silently | Depends on whether backup is on at all — see D1 | L |
| **Dynamic colour** | `setColorResources` from Monet, re-pushed on attach and on scheme change, API-gated (`LauncherAppWidgetHostView.java:109-131`, `AccentColorExtractor.java`) | Nothing; widgets get default `system_accent*`/`system_neutral*` | Widgets do not follow the launcher's palette | Yes, given how much this app invests in theming | M |
| **Resize displaces neighbours** | Resize re-enters the same reorder algorithm (`AppWidgetResizeFrame.java:556-557`) | `WidgetEditPolicy.resize` only accepts collision-free rects (`:166-200`) | You cannot grow a widget into an occupied cell even when the neighbour has somewhere to go | Yes — the move path already does displacement, so this is an inconsistency users will feel | M |
| **Deferred updates while dragging** | `beginDeferringUpdates`/`endDeferringUpdates`, 1 s timeout (`LauncherAppWidgetHostView.java:234-247`) | None; a provider update during a drag re-inflates under the finger | Rare but ugly; a clock widget ticking mid-drag re-lays out | Yes, and it is cheap | S |
| **Size-option write dedupe against the live bundle** | Reads `getAppWidgetOptions` back and skips the write if the `SizeF` list matches (`WidgetSizeHandler.kt:42-57`) | Dedupes against its own stored bundle only (`WidgetSizeOptionsPolicy.java:81`) | After a restore or an external change, the stored bundle and the real one can disagree | Marginal | S |
| **Configure retry on stale id** | Retries up to 3×, re-allocating the id on `IllegalArgumentException` (`LauncherWidgetHolder.java:278-312`) | One attempt; `ActivityNotFoundException`/`SecurityException` abandons the transaction | Rare | Low — reconciliation recovers | S |
| **Picker recommendations** | Category-bucketed suggestions from the app-prediction service | None | Discovery | **No.** See non-gaps | — |
| **Two-pane picker** | Tablet layout with an app list beside the grid (`WidgetsFullSheet.java:818-822`) | One sheet | Tablet polish | Low priority; landscape is the real case here | M |
| **Work-profile tab** | A dedicated `WORK` adapter and pager | Profiles are enumerated and badged but merged into one list (`WidgetProviderCatalogLoader.java:206-231, 363-369`) | Work widgets are mixed in with personal | Low — badging already distinguishes them | M |
| **Drag from tray to a cell** | Long-press a tray cell, drag, the widget binds mid-drag, drops where the finger is (`PendingItemDragHelper.java`, `WidgetHostViewLoader.java`) | Tap places at the first free cell (`WidgetPaneController.java:261-296`) | No placement control at add time | Arguable — see non-gaps | L |
| **Rounded-corner subtlety** | Clips only the widget's `background` view, honours opt-out (`RoundedCornerEnforcement.java:54-96`) | Hard-clips the whole cell (`WidgetCellView.java:216-224`) | A widget that already draws its own corners gets double-rounded; one that draws to its edges gets cut | Minor but real; the cell comment already acknowledges it | S |
| **Widget click interception** | Per-view `InteractionHandler` grafting the launcher's open animation (`QuickstepInteractionHandler.java:62-128`) | None; the platform dispatches | No launch animation from a widget tap | No — this app has no app-open animation to graft | — |
| **Collection/list widgets** | Nothing. Zero hits for `setRemoteAdapter`/`RemoteCollectionItems` | Nothing; `WidgetGridView`'s comment notes providers keep their own nested scrolling | **No gap** | — | — |
| **`OPTION_APPWIDGET_RESTORE_COMPLETED`** | `//TODO` only (`WidgetManagerHelper.java:56`) | Not used | No gap | — | — |

## 5. Non-gaps — what not to copy

**The workspace/page/hotseat model.** Launcher3's widgets live on a `CellLayout` inside a `Workspace`
`PagedView` alongside icons and folders, with a hotseat, a QSB and a first-page pinned item. Here the
home screen is one *place* on a pane wall (`PaneWallPage.WIDGETS`) whose arrangement is resolved per
place and per orientation by `PlaceLayoutStore` (see `../per-place-layout/SPEC.md`). Widgets are the
only occupants of that grid; there are no icons to reflow around, no folders, no hotseat. Every part
of Lawnchair's placement code that exists to reconcile *heterogeneous* items — `canReorder`,
`ItemConfiguration`, `MulticellReorderAlgorithm`, `WorkspaceItemSpaceFinder`'s screen-exclusion
logic — is solving a problem this app does not have. `WidgetGridPlacementPolicy` plus
`WidgetEditPolicy` are the right size for the job.

**Recommendations.** They depend on a platform app-prediction service returning
`CONTAINER_WIDGETS_PREDICTION`, they are gated behind aconfig flags whose shipped state cannot be
determined from source, and they exist to help a general-audience launcher's users discover widgets.
The audience for a terminal launcher's widget pane is not discovering widgets by accident. Skip.

**Smartspace.** Both Lawnchair mechanisms are workarounds for wanting a first-class native view in a
widget-shaped hole. This app already has a status bar that owns clock, weather and notifications as
native views with the app's own theming. Adding a smartspace would be re-solving a solved problem
with a worse mechanism.

**`allow_widget_overlap` / `force_widget_resize` / `widget_unlimited_size`.** These three Lawnchair
preferences exist because its users ask for them; each one lets the user construct a broken layout.
Overlap in particular short-circuits the reorder algorithm entirely (`ReorderAlgorithm.java:136-140`).
The house rule that a setting must survive being explained in one plain sentence rules all three out.

**Activity recreation for day/night.** Lawnchair recreates the whole activity on a theme change
(`WallpaperThemeManager.kt:70-79`). This app deliberately does not — it drops the cached host views
and rebuilds them (`LauncherWidgetHostController.java:122-143`), which is strictly better here
because recreating `TermuxActivity` would tear down terminal sessions' view state.

**A second headless host for reading widget content.** `HeadlessWidgetsManager` binds widgets
off-screen to scrape their `RemoteViews` as a data source. It leaks by construction (`close()` is
`TODO`). Do not.

**`QuickstepWidgetHolder`'s shared-listener design.** It exists so Launcher and Taskbar can host the
same widget id simultaneously. There is one host here. And Lawnchair does not even ship it.

**The `Executor`-free inflate.** Lawnchair inflates widget `RemoteViews` on the main thread (it never
calls `AppWidgetHostView.setExecutor`); this launcher already moved that off-thread
(`LauncherAppWidgetHost.java:31-38`). Do not regress toward Lawnchair here.

## 6. Proposed spec

Ordered by value per unit cost. Each item is independent unless a dependency is named. Nothing here
is decided; the open decisions are collected in §6.9.

### 6.1 Real widget previews in the picker — S/M, highest value

**Problem.** Every tray card is a 56 dp square bitmap decoded from `loadPreviewImage`, and for a
provider with no `previewImage` it is just the app icon
(`WidgetProviderCatalogLoader.java:174-182`). Users cannot tell a 4×1 from a 2×2, and a large share
of modern widgets ship `previewLayout` rather than `previewImage`, so they show as bare icons today.

**Proposed behaviour.** Implement the platform's documented precedence, as
`appwidget-host-research.md` §1 already established it:

1. API 35+ and `(info.generatedPreviewCategories & WIDGET_CATEGORY_HOME_SCREEN) != 0` →
   `AppWidgetManager.getWidgetPreview(provider, profile, WIDGET_CATEGORY_HOME_SCREEN)`, giving
   `RemoteViews`.
2. API 31+ and `info.previewLayout != 0` → `new RemoteViews(packageName, info.previewLayout)`.
3. Otherwise today's bitmap path, unchanged.

Tiers 1 and 2 are applied into a real `AppWidgetHostView` — created against `widgetContext()`, with
`setAppWidget(0, info)` and no bound id, exactly as `WidgetCell.createAppWidgetHostView` does — and
placed in the card. Tier 3 keeps the existing `ImageView`. Wrap every tier in try/catch including
`NoSuchMethodError`/`NoClassDefFoundError`, as Lawnchair learned to
(`WidgetManagerHelper.java:168-181`).

**Files.** `WidgetProviderCatalogLoader.java` (a `preview()` boundary method returning a small
sealed result instead of a `Drawable`), `WidgetPickerAdapter.java` (bind a view, not only a
bitmap), `WidgetProviderItem.java`, `WidgetCellView`-style clipping for the card.

**APIs.** `AppWidgetManager.getWidgetPreview` / `AppWidgetProviderInfo.generatedPreviewCategories`
(API 35), `AppWidgetProviderInfo.previewLayout` (API 31), `RemoteViews(String, int)`,
`AppWidgetHostView.updateAppWidget`.

**Dependency.** None. 6.2 should land with it.

### 6.2 Cards shaped like the widget — S

**Problem.** Every card is square, so the one piece of information a preview should carry — the
shape — is thrown away.

**Proposed behaviour.** Size each card from the item's `columnSpan`/`rowSpan`, already computed at
`WidgetProviderCatalogLoader.java:269-283`, snapped to a handful of template aspect ratios (1×1,
2×1, 2×2, 4×1, 4×2, 4×4) rather than one per span, so the row heights stay uniform within a section.
Scale the live preview into the card and clip it to the widget corner radius. Lawnchair's equivalent
is `WidgetPreviewContainerSize.forItem()`.

**Files.** `WidgetPickerAdapter.java`, `WidgetProviderItem.java`.

**Dependency.** Pairs with 6.1; alone it would only resize a 56 dp icon.

### 6.3 Resize that pushes its neighbours — M

**Problem.** `WidgetEditPolicy.resize` only accepts collision-free rects (`:166-200`), so growing a
widget stops dead at the first occupied cell, while *moving* the same widget cheerfully pushes
whatever is in the way (`snapMove`/`displace`, `:53-131`). The two gestures live in the same edit
session and behave by different rules.

**Proposed behaviour.** Give `resize` the same displacement pass `snapMove` has: for each candidate
rect, if it collides, call the existing `displace()` to rehome the blockers; accept the candidate if
every blocker finds a hole, otherwise keep scanning outward. Return the displacement map in the
`Candidate` (the field already exists and is documented as "always empty for a resize") and preview
it with the same `slideCell` animation the move path uses. Commit through the existing atomic
`putRecords` batch. Lawnchair reaches the same outcome by routing resize back through its one
reorder algorithm (`AppWidgetResizeFrame.java:556-557`).

**Files.** `WidgetEditPolicy.java`, `WidgetPaneController.java` (`resizeDrag`/`endResizeDrag`),
`WidgetEditPolicyTest.java`.

**Dependency.** None.

### 6.4 Hold widget updates during a drag or resize — S

**Problem.** `SafeLauncherAppWidgetHostView.updateAppWidget` applies whatever the provider pushes,
whenever it pushes it. During a cross-page drag the cell is a photograph
(`WidgetDragLayerView`), but during an in-page move or a resize the live view can re-inflate under
the finger.

**Proposed behaviour.** Add `beginDeferringUpdates()`/`endDeferringUpdates()` to
`SafeLauncherAppWidgetHostView`: while deferring, stash the latest `RemoteViews` and apply it on
end; release automatically after a 1 s timeout so a dropped gesture cannot freeze a widget. Called
from `WidgetPaneController.beginMoveDrag`/`endMoveDrag` and the resize pair. This is
`LauncherAppWidgetHostView.java:234-247` almost verbatim, and it is safe because the deferral is
local — nothing is told to stop sending.

**Files.** `SafeLauncherAppWidgetHostView.java`, `WidgetPaneController.java`.

**Dependency.** None.

### 6.5 Widgets follow the launcher's colours — M

**Problem.** A widget that respects Material You draws in the *system's* palette, not the one the
user chose in this app's theme editor. On a phone whose system accent differs from the launcher's
theme, every widget looks foreign.

**Proposed behaviour.** Build a `SparseIntArray` mapping `android.R.color.system_accent{1,2,3}_*`
and `system_neutral{1,2}_*` to the launcher's resolved theme colours, and call
`AppWidgetHostView.setColorResources(SparseIntArray)` on every host view — at creation and again
whenever the theme changes. Per the platform, "Calling this method will trigger a full re-inflation
of the App Widget"
([`AppWidgetHostView.java:938-950`](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/appwidget/AppWidgetHostView.java)),
so push it only when the mapping actually changed — the platform already short-circuits an identical
mapping (`AppWidgetHostView.java:951-954`), but going through `createHostView` keeps it cheap. Follow Lawnchair's SDK
split: on API 34+ build `RemoteViews.ColorResources.create(context, colors)` and call that overload;
below, pass the `SparseIntArray` (`LauncherAppWidgetHostView.java:109-131`).

The obvious integration point is the existing per-place look / theme system, and the obvious
control is one switch. Copy would be roughly "Let widgets use this app's colours" with one sentence.

**Files.** `LauncherWidgetHostController.java` (a `applyColors()` alongside
`discardHostViewsBuiltInAnotherMode`), `LauncherAppWidgetHost.java`, the theme plumbing that already
resolves palette changes.

**APIs.** `AppWidgetHostView.setColorResources` (API 31) and the
`RemoteViews.ColorResources` overload (API 34).

**Open decision: D2.**

### 6.6 Corner clipping that respects the widget — S

**Problem.** `WidgetCellView` clips the whole cell to the system radius
(`WidgetCellView.java:213-225`). A widget that already draws its own rounded background gets clipped twice (visible as
a thin light seam at the corners); a widget that deliberately fills its rectangle gets its corners
cut.

**Proposed behaviour.** Port `RoundedCornerEnforcement`'s three rules, which are small and entirely
local: find the child with `android:id/background`; if it exists and already has
`clipToOutline == true`, clip nothing (the widget opted out); otherwise clip **that view's**
rectangle, not the cell's, to `min(our radius, system_app_widget_background_radius)`
(`RoundedCornerEnforcement.java:54-114`).

**Files.** `WidgetCellView.java`, a new small `WidgetCornerPolicy` for the pure part plus its test.

**Dependency.** None.

### 6.7 Verify size options against the live bundle — S

**Problem.** `WidgetSizeOptionsPolicy` dedupes against the *record's* stored bundle
(`LauncherWidgetHostController.java:488-505`). If the two ever diverge — after a provider update, a
reinstall, or anything that resets options — the launcher will believe the provider knows a size it
does not.

**Proposed behaviour.** Before writing, read `AppWidgetManager.getAppWidgetOptions(id)` and compare
the `OPTION_APPWIDGET_SIZES` list; skip the write only when the *live* bundle matches. This is
`WidgetSizeHandler.kt:42-57`, and its comment states the reason: the write "wakes up the provider
process and causes a full widget update", so two reads beat one unnecessary write.

**Files.** `LauncherWidgetHostController.java` (`Platform` gains `getOptions(int)`),
`WidgetSizeOptionsPolicy.java`, `LauncherWidgetOptionsIntegrationTest.java`.

**Dependency.** None.

### 6.8 Backup and restore of the widget pane — L

**Problem.** There is no restore path. `allowBackup` is a build placeholder
(`AndroidManifest.xml:104-105`); the repository is JSON in `SharedPreferences` keyed by widget id;
and widget ids are **not stable across a restore** — the system hands the host a new set and tells
it the mapping through `ACTION_APPWIDGET_HOST_RESTORED` with `EXTRA_APPWIDGET_OLD_IDS` and
`EXTRA_APPWIDGET_IDS`
([`AppWidgetManager.java:460-496`](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/appwidget/AppWidgetManager.java)).
Without handling that broadcast, a restored install finds every id invalid and — thanks to
reconciliation — tombstones the lot.

**Proposed behaviour (sketch, pending D1).** A `BroadcastReceiver` for
`ACTION_APPWIDGET_HOST_RESTORED` filtered to `EXTRA_HOST_ID == 0x544C` that rewrites the repository's
ids through the old→new map, and a record state (`PROVIDER_PENDING`, alongside today's
`PROVIDER_MISSING`) that survives a provider not yet installed, showing a placeholder cell instead
of a tombstone, with reconciliation promoting it to `ACTIVE` when the provider appears. Lawnchair's
equivalent is the `RESTORED` bit field plus `WidgetInflater` (`WidgetInflater.kt:50-182`) and
`PendingAppWidgetHostView` (`:207-260`).

This is the largest item here and the only one whose value depends entirely on a policy question
the user owns.

**Files.** `LauncherWidgetRepository.java` (schema v4), `LauncherWidgetRecord.java`,
`WidgetProviderReconcilePolicy.java`, `LauncherWidgetHostController.java`, a new receiver,
`AndroidManifest.xml`.

**Dependency.** D1 first.

### 6.9 Open decisions

| # | Decision |
|---|---|
| **D1** | **Does the widget pane need to survive a device-to-device restore at all?** This launcher's whole state — sessions, workspaces, keybinds, themes — lives in files and preferences whose backup posture is set by the build (`TERMUX_APP_ALLOW_BACKUP`). If widgets are expected to come back on a new phone, 6.8 is an L-sized project and should be specced on its own. If they are not — "set your widgets up again" being an acceptable answer — then 6.8 is closed and the only work is making the loss *quiet and correct* rather than a screen of error tiles, which is an S. |
| **D2** | **Should widgets take the launcher's colours, or the system's?** 6.5 makes every Material You widget repaint in the app's palette. That is either the obvious right thing (the pane is part of the app's surface) or an overreach (a widget is the other app's UI and should look like it does everywhere else). If it ships, is it on by default, or a switch — and is it one switch or per-place, like the rest of the look system? |
| **D3** | **Does adding a widget stay a tap, or become a drag?** Today a tap places at the first free cell (`WidgetPaneController.java:261-296`), and the picker greys out anything that will not fit (`canFit`, `:254-259`). Lawnchair drags from tray to cell. The tap is simpler, works one-handed, and fits a pane that is already modal; the drag gives placement control at add time. Note that the edit session can already move the widget afterwards, which makes the drag mostly a convenience. Cost of the drag version is L — the tray sheet and the grid would need to share a drag stream across a sheet dismissal. |
| **D4** | **Rotation loses the layout.** Verified: `setGridDefinition` reflows every record in place when the grid changes (`LauncherWidgetRepository.java:104-132`), and rotation re-applies the orientation's grid (`TermuxActivity.java:9962-9968`). So portrait → landscape → portrait does **not** return widgets to where they were; the second reflow starts from the landscape positions. Should each orientation keep its own cell assignments (a per-orientation record set, schema change, M), or is reflow-and-forget acceptable? |
| **D5** | **Work-profile widgets.** They are enumerated, badged and placeable today, merged into the one alphabetical list (`WidgetProviderCatalogLoader.java:206-231`). Does this app want a separate tab like Lawnchair's `WORK` adapter, or is the badge enough? |

### 6.10 Suggested order

6.1 + 6.2 together (one picker phase), then 6.4 and 6.6 and 6.7 as one small-fixes phase (all three
touch different files and share no state), then 6.3 (edit-policy phase, needs its own tests), then
6.5 once D2 is answered, then 6.8 only if D1 says so. 6.1/6.2 and 6.3 can run in parallel — different
files, no shared seam.

## 7. Unverified and open questions

- **Lawnchair's shipped flag state.** `enable_generated_previews`,
  `enable_categorized_widget_suggestions` and `enable_tiered_widgets_by_default_in_picker` are
  declared in `aconfig/launcher.aconfig` without a `state:` field. Aconfig's convention makes that
  DISABLED, but a build-time flag-values overlay outside the checkout could flip them. So: the
  generated-preview *code path* is verified to exist; whether a nightly Lawnchair actually uses it is
  not. This does not affect the proposal — `getWidgetPreview` is a platform API and this launcher
  would call it unconditionally on API 35+.
- **`DatabaseWidgetPreviewLoader.java:114` reads `generatedPreviewCategories` behind
  `BuildCompat.isAtLeastV()`**, i.e. API 34, while the field is API 35 (`VANILLA_ICE_CREAM`
  + `FLAG_GENERATED_PREVIEWS`). Whether that is safe on a 34 device depends on field-defaulting
  behaviour not visible in the file. **Inference:** this launcher should gate on API 35 and catch
  `NoSuchFieldError`/`NoClassDefFoundError` regardless.
- **Whether tier-1/tier-2 previews are common in practice** was not measured. The claim that "a large
  share of modern widgets ship `previewLayout`" in 6.1 is an **inference** from the API being the
  documented recommendation since API 31, not a survey of installed providers. Worth a quick
  measurement on pong before committing to 6.1's size.
- **`AppWidgetHost.restoreStarted`/`restoreFinished`** do not exist in the current
  `frameworks/base/main` `AppWidgetHost.java` (verified by grep). The host-side restore contract is
  the `ACTION_APPWIDGET_HOST_RESTORED` broadcast plus the id map; 6.8 is written against that.
- **Lawnchair's `WidgetPreviewContainerSizes.kt` template tables** were located but not read
  line-by-line, so 6.2's suggested aspect ratios are this document's own proposal, not a copy.
- **Not tested on a device.** Nothing here was built, installed or run. Every behavioural claim about
  this launcher is read from source or from its unit tests, not observed.
- Minor, noticed in passing and not part of the proposal:
  `WidgetPaneController.messageFor` (`:933-945`) returns six hard-coded English strings where the
  rest of the file uses `R.string` — worth folding into whichever phase touches that file next.
