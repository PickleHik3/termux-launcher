# Launcher basics against Launcher3 (2026-10-25, follows #149)

Research only; no code changed. The question: which everyday home-launcher flows does AOSP Launcher3
(the Pixel launcher's base) implement, and where does Termux Launcher do the same, do it
differently, or not do it. Launcher3 side read from `main` of
`platform/packages/apps/Launcher3` on android.googlesource.com on 2026-10-25, plus the
developer.android.com pages named under Sources. Our side read in this worktree (HEAD 39742e928) by
class; the gap column says Same / Different / Missing in one line and does not judge quality.

How the sources were read. Every Launcher3 file was fetched as `?format=TEXT` (the base64 body of the
file) and read through the fetch tool's summariser, so **no line numbers were seen**: a claim is cited
to the file URL and the symbol or constant that carries it. Values quoted (500 ms, 650 ms, 0.66 and so
on) were read as named constants. Several large files were cut off by the fetcher (see "Not reached
or not read" at the end); nothing in this note rests on the cut-off part. Where a claim is general
Android knowledge and not read in a primary source, it says "not verified".

Summary. Launcher3's model is a drag-and-drop workspace: long-press lifts an icon, the workspace
shrinks into a spring-loaded state, hovering over an adjacent page for 500 ms turns the page,
hovering over a cell for 650 ms reorders neighbours, hovering over an icon makes a folder ring, and
two drop targets (Remove, then Uninstall or Reconfigure) sit above the page. Ours matches the shared
shape for the drawer-to-Home drag, folders, the long-press app menu, the widget picker, page
management and the empty-space card, and goes its own way in three places: Home items move through an
edit mode (not a direct long-press lift), removal and uninstall are menu rows (not drop targets), and
the dock is not a drop target for Home items. It is missing the pin-request handshake
(`PinItemRequest`, `ACTION_INSTALL_SHORTCUT`), dragging a deep shortcut out of the menu to pin it, and
a work-profile tab in the drawer.

## Table

| Flow | Launcher3 behaviour | Termux Launcher | Gap |
|---|---|---|---|
| 1 Drawer to Home drag | Long-press in All Apps lifts the icon (LONG_PRESS haptic), spring-loaded state, page turns after 500 ms hover, 650 ms hover reorders, no room shows "out of space" | `AppDrawerDragController` picks up, `DrawerHandOverPolicy` hands over above the drawer top (250 ms hold or 24dp), `HomeAppDropPolicy` places in the nearest free cell, `NO_ROOM` on a full page | Different: hand-over by leaving the drawer's top edge, no shrunken spring-loaded view. Same: nearest free cell, refusal on a full page |
| 2 Move icon on Home, pages, dock | Long-press lifts directly; adjacent-page hover turns the page; drop on an occupied cell reorders neighbours; Hotseat is a drop target | `WidgetPaneController` edit mode (`beginMoveDrag`), edge flip after 350 ms in a 24dp band, displacement preview; over the dock `HomeAppDropPolicy` gives CANCEL | Different: edit mode first. Missing: drag Home to dock and back (the dock is edited in `PinnedAppsEditor`) |
| 3 Folders | Hover on an icon shows the ring, drop creates the folder; name edited inline with suggestions; last item leaves and the folder becomes that item | `HomeFolderPolicy` (300 ms rest, then merge; cap 16 default), `HomeFolderPopup`, `FolderRenameController`; one app left becomes its own icon, none removes the folder | Same: create, add, last-item rules. Different: popup rename, no label suggestions found, cap 9 to 20 |
| 4 Remove / uninstall / app info | Remove drop target (Cancel when not removable); Uninstall or Reconfigure secondary target; App info only a menu row | Menu rows only: `AppMenuPlan.Row.UNINSTALL` (`ACTION_DELETE`), App info row (`ACTION_APPLICATION_DETAILS_SETTINGS`) in `SuggestionBarView`; removal inside edit mode and folders | Different: no drag-to-remove or drag-to-uninstall target; no undo found |
| 5 App menu | Popup: Widgets, App info, deep shortcuts last, dot and notifications; drag a shortcut out to pin | `AppMenuPlan`: notifications, shortcuts (max 6, More), Widgets, App info, Add to home, Pin, Change icon, Uninstall | Same: rows and notifications. Missing: drag a shortcut out of the menu to pin it |
| 6 Widgets and pin requests | `WidgetsFullSheet` (search, recommendations, work tab); `AppWidgetResizeFrame` handles with 0.66 snap; `AddItemActivity` takes `PinItemRequest` | `WidgetPickerSheetView` (search, previews), `GridSizeWheelView` resize, `WidgetPaneController` reconfigure, `LauncherWidgetHostController` bind; no `PinItemRequest` | Same: add, bind, configure, reconfigure. Different: resize by wheel, not edge handles. Missing: pin shortcut or widget requests and `ACTION_INSTALL_SHORTCUT` (nothing in `app/src/main`) |
| 7 Page management | Extra empty page offered while dragging, stripped after; default page 0 | `HomePageEdits`, `HomeActionsSheetPolicy` (Add page, Edit pages, Remove page only when empty and not last), `HomeSettings.defaultPage` | Same: default page, empty-page removal. Different: pages added by menu, not while dragging; reorder exists here |
| 8 Search and drawer | All-apps search box, A-Z list with fast scroller, Personal / Work tabs, pause toggle, private space | `AppDrawerSearchController`, `AppDrawerAzSide`, categories and drawer folders; profiles read in `LauncherAppDataProvider` | Same: search and A-Z. Different: categories, folders, prompt-line search. Missing: work tab and pause toggle |
| 9 Empty-space long press | Options popup: Wallpapers, Widgets, Edit home screen (flagged), Home settings | `HomeActionsSheet`: Add a widget, Add an app, Add a page, Edit pages, Wallpaper, Appearance, Launcher settings, Help; layout lock | Same entry. Different: a sheet with more rows; Home layout lock is ours alone |
| 10 Back / Home | Back: drag, floating view, handlers, state. Home: close views, NORMAL, default page when already home | Back: `WidgetPaneController.onBackPressed` (edit mode first), `AppDrawerController.onBackPressedInDrawer`; Home: `TermuxActivity.onNewIntent` closes overlays, lands on Home page 1 | Same: Home closes everything. Different: Home goes to page 1 (not traced to the default-page setting) |

## 1. Drag an app from the drawer to the home grid

Launcher3.

1. Trigger: long-press an icon in All Apps. `ItemLongClickListener.onAllAppsItemLongClick` refuses
   unless the state is `ALL_APPS` or `OVERVIEW`, refuses while `workspace.isSwitchingState()`, and
   refuses when `canStartDrag` fails (workspace locked while binding, or a drag already running). A
   `DragListener` hides the source icon (INVISIBLE, then VISIBLE on `onDragEnd`), then
   `workspace.beginDragShared` starts the drag.
2. Lift: `LauncherDragController.startDrag` performs `HapticFeedbackConstants.LONG_PRESS` on the drag
   layer and scales the drag view (`R.dimen.pre_drag_view_scale` for icons).
3. State: the launcher goes to the spring-loaded state. `SpringLoadedState` shrinks the workspace
   (`getWorkspaceSpringLoadScale`), leaves the Hotseat unscaled at (1, 0, 0), shows page backgrounds
   at alpha 0.2, takes 150 ms in or out, and keeps icons draggable
   (`FLAG_WORKSPACE_ICONS_CAN_BE_DRAGGED`).
4. Page turns: `SpringLoadedDragController` (`ENTER_SPRING_LOAD_HOVER_TIME` = 500 ms;
   `ENTER_SPRING_LOAD_CANCEL_HOVER_TIME` = 950 ms when no layout is under the finger) calls
   `snapToPage` on the hovered page when its alarm fires; `Workspace.onDragOver` re-arms it. I found no
   separate edge-scroll timer: `DragController`, `LauncherDragController` and `DragLayer` hold no
   `SCROLL_DELAY` or scroll-area code (see the end of this note).
5. Cell feedback: `CellLayout.visualizeDropLocation` plays `MSDLToken.DRAG_INDICATOR_DISCRETE` when the
   target cell or span changes (behind `Flags.msdlFeedback()`). Hovering a cell for `REORDER_TIMEOUT` =
   650 ms (`Workspace`) triggers `performReorder`; `CellLayout` animates the neighbours aside in
   `REORDER_ANIMATION_DURATION` = 150 ms (`ReorderPreviewAnimation`).
6. Drop: `Workspace.onDrop` handles external drops (`onDropExternal`); a drop into the Hotseat is
   recognised (`isHotseatLayout`, `CONTAINER_HOTSEAT`). Drop animation modes include
   `ANIMATE_INTO_POSITION_AND_REMAIN`; `ADJACENT_SCREEN_DROP_DURATION` = 300 ms.
7. Edge cases. Full page: `onNoCellFound` shows `out_of_space` or `hotseat_out_of_space` and logs
   `LAUNCHER_ITEM_DROP_FAILED_INSUFFICIENT_SPACE`. On another icon: folder (flow 3). On the dock:
   placed in the Hotseat if it has room. Cancel: back runs `mDragController.cancelDrag()` first
   (`Launcher.getOnBackAnimationCallback`); `DragController.cancelDrag` exits the last target,
   dispatches a drop-complete with no target, and `endDrag` animates the view back
   (`animateDragViewToOriginalPosition`). A release off every target falls to `getDefaultDropTarget`,
   which returns the workspace.
8. Drawer closing: the state change at drag start takes All Apps away; the state manager was not read in
   detail.

Termux Launcher. `AppDrawerDragController` starts the pickup as the drawer's own drag (folders,
drawer drop targets). `DrawerHandOverPolicy` hands the icon to Home once the finger is above the
drawer's top for 250 ms (`HOLD_MS`) or 24dp past it (`CROSS_DP`). `HomeAppDrop` carries the state;
`HomeAppDropPolicy.resolve` gives PLACE, NO_ROOM or CANCEL: over the grid with a free cell it places
in the nearest one, a page with no free cell refuses, and off the grid, over the dock or on a cancel
nothing is put down. `HomePageTransition` turns pages. Haptic on the drawer drag: `CLOCK_TICK` in
`AppDrawerDragController`.
Gap: Different for the hand-over and for the missing spring-loaded shrink; Same for the nearest free
cell and the full-page refusal. Not traced: whether neighbours displace during a drawer drop.

## 2. Move an icon on Home, across pages, into and out of the dock

Launcher3.

1. Trigger: long-press a Home icon. `ItemLongClickListener.onWorkspaceItemLongClick` runs when the
   state is `NORMAL`, `OVERVIEW` or `EDIT_MODE` and the tag is an `ItemInfo`; `beginDrag` starts the
   folder's drag for an item inside an open folder (`folder.startDrag`), else
   `workspace.startDrag(cellInfo, DragOptions)`.
2. Feedback: same lift haptic and spring-loaded state as flow 1; `DragLayer.animateViewIntoPosition`
   and `playDropAnimation` animate the drop.
3. Across pages: hover on an adjacent page for 500 ms, `SpringLoadedDragController.onAlarm` calls
   `snapToPage`. A trailing empty page is offered during the drag (`addExtraEmptyScreenOnDrag`,
   `addExtraEmptyScreens`).
4. Dock: `Hotseat extends CellLayout`, one row (one column when vertical), `numShownHotseatIcons`
   cells, a QSB beside it. A drop on a hotseat cell is a `CONTAINER_HOTSEAT` drop (`Workspace.onDrop`,
   `hasMovedIntoHotseat`). Out of a dock slot is the same long-press.
5. Occupied cell: `performReorder` computes a solution (`ReorderAlgorithm`) and
   `animateItemsToSolution` moves the neighbours; dropping on an icon is flow 3.
6. Cancel: back, or a drop off every target; the item returns (`DragController.cancelDrag`). When the
   item cannot be removed, the Remove target reads Cancel (`DeleteDropTarget.setTextBasedOnDragSource`).

Termux Launcher. Home items (app icons, folders, widgets, built-ins) are records moved by
`WidgetPaneController`: a long-press enters edit mode (`enterEditMode`), then `beginMoveDrag`,
`moveDrag`, `endMoveDrag`. `watchPageEdge` turns the page after `EDGE_FLIP_DELAY_MS` = 350 within an
`EDGE_BAND_DP` = 24dp band; `updateDragCandidate` shows a ghost and a neighbour preview
(`clearDisplacementPreview`). Back leaves edit mode first. `HomeItemPlacementPolicy` appends a page
when an outside item finds no room. A move into the dock is not a drop on Home (`HomeAppDropPolicy`
refuses over the dock); pins are managed in `PinnedAppsEditor` and the menu's Pin row.
Gap: Different for edit-mode-first and the 350 ms edge flip; Missing for Home-to-dock and
dock-to-Home drags.

## 3. Create a folder by dropping one icon on another; rename; remove the last item

Launcher3.

1. Create: while dragging over an icon, `Workspace.manageFolderFeedback` calls `willCreateUserFolder`
   and draws `mFolderCreateBg` with `animateToAccept`; the mode is `DRAG_MODE_CREATE_FOLDER`.
   `CellLayout.canCreateFolder` allows only plain icons (`DRAGGABLE_ICON`). Dropping creates the folder
   (`createUserFolderIfNecessary`). Dropping on an existing folder is `DRAG_MODE_ADD_TO_FOLDER` and
   `addToExistingFolderIfNecessary`.
2. Folder icon: `FolderIcon.onDragEnter` animates the ring (`PreviewBackground`) and, for a supported
   source, opens the folder after `ON_OPEN_DELAY` = 800 ms (`mOpenAlarm`, `beginExternalDrag`);
   `DROP_IN_ANIMATION_DURATION` = 400 ms. A tap opens it through the activity's click listener.
3. Open: `Folder.animateOpen` closes any other open folder, centres on the icon, adds itself to the
   drag layer; `FolderAnimationManager` plays it. Items live in `FolderPagedView`; a drag into the left
   or right band (`ICON_OVERSCROLL_WIDTH_FACTOR` = 0.45 of a cell) shows a hint for
   `SCROLL_HINT_DURATION` = 500 ms, then scrolls.
4. Rename: `Folder.mFolderName` is a `FolderNameEditText` inside the open folder;
   `startEditingFolderName` and `showLabelSuggestions` (from `FolderNameProvider`, computed on
   `MODEL_EXECUTOR` when the folder holds more than one item). IME Done or back commits
   (`onEditorAction`, `onBackKey`: `mInfo.setTitle`; an empty title restores the hint).
5. Drag out: `Folder.onDragExit` starts `mOnExitAlarm` for `ON_EXIT_CLOSE_DELAY` = 400 ms; the folder
   closes (`completeDragExit`) and the item is carried onto Home. Inside the folder, a reorder waits
   `REORDER_DELAY` = 250 ms.
6. Last item: `Folder.onRemove`: with one item or fewer an open folder closes; a closed one calls
   `replaceFolderWithFinalItem`, so the folder becomes the lone icon. If the folder itself is being
   dragged, `mDeleteFolderOnDropCompleted` defers it. A failed drag returns the item to the folder
   (`onDropCompleted`, `mFolderIcon.onDrop(d, true)`).

Termux Launcher. `HomeFolderPolicy`: an icon dropped on an icon makes a folder of the two (dropped
app first, named after it) once it has rested 300 ms (`MERGE_HOVER_MS`); dropping on a folder
appends; cap 16 (`MAX_APPS`, offered 9/12/16/20); no duplicates; `remove` leaves the last app's own
icon when one remains and nothing when none does; widgets, built-ins and folders never merge.
`HomeFolderPopup` and `HomeFolderOverlay` show it; `FolderRenameController` and `FolderRenameModel`
rename (capped at `MAX_CODE_POINTS`, blank falls back to "Folder"); the drawer has its own folders
(`AppDrawerFolderCellView`, `LauncherFolderMutator`).
Gap: Same for create, add and the last-item rules. Different for the popup and for rename (no
suggestions found). Not traced: dragging a whole folder between pages, the hover ring.

## 4. Remove from home vs uninstall vs app info

Launcher3.

1. Drop targets: `DeleteDropTarget extends ButtonDropTarget`, label `remove_drop_target_label`, icon
   `ic_remove_no_shadow`. `supportsDrop()` is always true; an item without a database id
   (`ItemInfo.NO_ID`) reads `android.R.string.cancel` and acts as Cancel. `onDrop` calls
   `prepareToUndoDelete()`, which the drop handler turns into an undo.
2. `SecondaryDropTarget` offers one of UNINSTALL (`uninstall_drop_target_label`: installed non-system
   apps; blocked by `DISALLOW_APPS_CONTROL` or `DISALLOW_UNINSTALL_APPS`, cached 5 s),
   DISMISS_PREDICTION (predicted items) or RECONFIGURE (a reconfigurable widget). INVALID hides it.
3. Uninstall mechanics: `performUninstall` builds an intent from the `delete_package_intent` string with
   `package:<pkg>/<class>` and `EXTRA_USER`; it does not set `EXTRA_RETURN_RESULT`. After the launcher
   resumes, `DeferredOnComplete` checks whether the app is gone: gone reports success
   (`UNINSTALL_COMPLETED`); still installed marks the drag cancelled (`UNINSTALL_CANCELLED`) and the
   icon returns.
4. App info is not a drop target. It is `SystemShortcut.APP_INFO` in the long-press menu, always
   created. `SystemShortcut.UNINSTALL_APP` exists only for private-space items.
5. Cancel: dragging to the Cancel label or off every target returns the item.

Termux Launcher. No drag-to-remove or drag-to-uninstall target found. `AppMenuPlan.Row.UNINSTALL`
(added only for an `androidPackage`) runs `Intent.ACTION_DELETE` from `SuggestionBarView`; the App
info row opens `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` from the same class. Taking an app out
of a folder is handled in `WidgetPaneController`; unpinning from the dock is the menu's Pin row.
Gap: Different (menu rows, not targets) for all three. Missing: undo for remove (not found). Not
traced: a result check after uninstall.

## 5. Long-press app menu

Launcher3.

1. Open: `PopupContainerWithArrow.showForIcon` returns null if a popup is open or
   `ShortcutUtil.supportsShortcuts` is false. It collects system shortcuts, counts deep shortcuts
   (`PopupDataProvider.getShortcutCountForItem`), inflates `popup_container`, and loads shortcuts in
   the background (`PopupPopulator.createUpdateRunnable` on `MODEL_EXECUTOR`).
2. Layout: system rows first, then the Widgets shortcut, then deep shortcuts last
   (`deep_shortcut_container`). `SHORTCUT_COLLAPSE_THRESHOLD` = 6: above it, non-widget system
   shortcuts collapse into an icon-only row. `updateHiddenShortcuts` hides rows past `MAX_SHORTCUTS`
   (value in `PopupPopulator`, not read).
3. System shortcuts (`SystemShortcut`): `WIDGETS` (only when the package has widgets), `APP_INFO`
   (always), `INSTALL` (web-UI or instant apps), `DONT_SUGGEST_APP` (predicted items),
   `PRIVATE_PROFILE_INSTALL`, `UNINSTALL_APP` (private space), `BUBBLE_SHORTCUT`.
4. Static, dynamic, pinned: the developer page says static shortcuts come from `shortcuts.xml`, dynamic
   ones are pushed at runtime and pinned ones are user-made; most launchers show four; users drag a
   shortcut to pin it. A launcher reads them with `LauncherApps.getShortcuts` and
   `FLAG_MATCH_DYNAMIC` / `MANIFEST` / `PINNED` (not verified: the LauncherApps page body could not be
   read).
5. Notification dots and previews: not read in this pass. `PopupContainerWithArrow` references a
   notification container; `FolderIcon` carries `mDotInfo`.
6. Dragging a deep shortcut: `LauncherPopupItemDragHandler.onLongClick` needs `canStartDrag` and a
   `DeepShortcutView` parent, hides the shortcut's icon, sets the container to `CONTAINER_SHORTCUTS`
   and calls `workspace.beginDragShared` with a `ShortcutDragPreviewProvider`. The pre-drag condition
   uses `deep_shortcuts_start_drag_threshold`. `onDragStart` sets `mDeferContainerRemoval` and calls
   `animateClose()`; `onDragEnd` finishes the close. A touch outside closes the popup but a tap on the
   source icon still launches the app.
7. Edge cases: a second long-press while a popup is open does nothing; movement past the touch slop stops
   touches reaching the shortcut views (`onInterceptTouchEvent`).

Termux Launcher. `AppMenuPlan` builds the rows: notifications (when badged), shortcuts up to
`MAX_SHORTCUT_ROWS` = 6 with a More row, Widgets, App info, Add to home, Pin/Unpin, Change icon,
Uninstall. Shortcuts come from `LauncherAppDataProvider` and `LauncherAppLauncher`; notification
badges from `launcher/notifications/*`; the card is `AppMenuCard` over `AnchoredMenu`.
Gap: Same for the rows. Different: Add to home and Change icon are ours. Missing: dragging a shortcut
out to pin it (not found). Not traced: the static, dynamic and pinned distinction.

## 6. Add and resize a widget; apps asking to pin a widget or shortcut

Launcher3: picker, drag, resize.

1. Entry: the empty-space popup's Widgets row (`OptionsPopupView.openWidgets` reuses an open
   `TYPE_WIDGETS_FULL_SHEET` or calls `WidgetsFullSheet.show(launcher, true)`; a toast in safe mode),
   or the app menu's Widgets row (`SystemShortcut.WIDGETS`).
2. Sheet: `WidgetsFullSheet` has a search bar and recommendations in a sticky header, a primary list, and
   (with a work profile) a work list with Personal / Work tabs, work widgets filtered to non-paused users;
   a two-pane layout on tablets and unfolded foldables (`widgets_two_pane_sheet`). `enterSearchMode`
   hides recommendations and tabs; back in search resets the bar first.
3. Drag: `ItemLongClickListener.onWidgetItemLongClick` uses the preview drawable or
   `AppWidgetHostViewPreview` (false when the preview has not loaded) and drags through
   `PendingItemDragHelper`. After the drop `Launcher.handleActivityResult` finishes with
   `completeTwoStageWidgetDrop`.
4. Bind and configure: `REQUEST_BIND_APPWIDGET` (OK calls `addAppWidgetImpl`; cancel removes the extra
   empty screen), `REQUEST_CREATE_APPWIDGET` / `REQUEST_PICK_APPWIDGET` (`completeTwoStageWidgetDrop`),
   `REQUEST_RECONFIGURE_APPWIDGET` (`completeAddAppWidget`). The developer page: `allocateAppWidgetId`,
   `bindAppWidgetIdIfAllowed`, otherwise `ACTION_APPWIDGET_BIND`; launch the configure activity unless it
   is optional (`configuration_optional` and `reconfigurable`); pass size options as a `List<SizeF>`
   (at most 16).
5. Resize: `AppWidgetResizeFrame.showForWidget` shows a frame with four handles (hidden on an axis the
   provider's resize mode forbids). A span step happens once the fractional drag passes
   `RESIZE_THRESHOLD` = 0.66; spans clamp to provider min and max and to the grid;
   `snapToWidget` animates 150 ms (`SNAP_DURATION_MS`). Release runs `onTouchUp` and posts a snap;
   dismiss runs `resizeWidgetIfNeeded(true)` and announces the size. A reconfigure button shows for
   reconfigurable widgets, with a one-time tip. A touch outside, or a D-pad or Home key, closes the
   frame. No haptic was read in this file.
6. Reconfigure by drag: `SecondaryDropTarget` RECONFIGURE (flow 4).

Launcher3: pin requests.

1. An app calls `ShortcutManager.requestPinShortcut` or `AppWidgetManager.requestPinAppWidget`; the
   system sends `ACTION_CONFIRM_PIN_SHORTCUT` or `ACTION_CONFIRM_PIN_APPWIDGET` to the default home
   app, which reads `LauncherApps.getPinItemRequest(intent)` and calls `accept()` after it placed the
   item. The developer shortcuts page confirms `requestPinShortcut` needs the user's confirmation
   and gives the app no callback on decline. The `PinItemRequest` reference body could not be read, so
   its method list is not verified there; the use is verified in `AddItemActivity`.
2. `AddItemActivity.onCreate` reads `PinRequestHelper.getPinItemRequest` and finishes when it is null or
   the type is neither `REQUEST_TYPE_SHORTCUT` nor `REQUEST_TYPE_APPWIDGET`. It shows
   `AddItemWidgetsBottomSheet` with the app's name and a preview. A plain tap on the preview does
   nothing; a long-press starts the drag.
3. Drag path: `startDragAndDrop` with a 10 px transparent shadow and `DRAG_FLAG_GLOBAL`, then Home is
   launched (`ACTION_MAIN`, `CATEGORY_HOME`, `FLAG_ACTIVITY_NEW_TASK`) and the activity registers with
   `Launcher.ACTIVITY_TRACKER`. `PinItemDragListener` (a `BaseItemDragListener`) builds
   `PendingAddShortcutInfo` or `PendingAddWidgetInfo` (`CONTAINER_PIN_WIDGETS`), checks
   `mRequest.isValid()` and cancels a `CancellationSignal` at cleanup. The sheet finishes on pause.
4. Add-automatically path ("place automatically"): shortcuts go to `ItemInstallQueue.queueItem`, then
   `mRequest.accept()`; widgets `allocateAppWidgetId`, `bindAppWidgetIdIfAllowed`, else the bind flow,
   then `acceptWidget` puts `EXTRA_APPWIDGET_ID` in the options for `accept(options)`.
5. Cancel: the Cancel button (`LAUNCHER_ADD_EXTERNAL_ITEM_CANCELLED`) or back (`..._BACK`) closes the
   sheet; a widget whose minimum span exceeds the grid ends the flow; a bind result that is not OK
   deletes the allocated id.

Termux Launcher. `WidgetPickerSheetView` (search, previews, hint shown 3500 ms), `WidgetPickerAdapter`,
`WidgetAddTransaction`, `LauncherWidgetHostController` (allocate, bind through
`ACTION_APPWIDGET_BIND`, configure), `WidgetBindFlowPolicy`. Widgets move through the edit mode of
flow 2; resize is `GridSizeWheelView` (haptic `CLOCK_TICK`); reconfigure is in
`WidgetPaneController`. Handling of `ACTION_CONFIRM_PIN_*`, `PinItemRequest` and
`ACTION_INSTALL_SHORTCUT`: none found in `app/src/main`.
Gap: Same for add, bind, configure and reconfigure. Different for resize (wheel, not edge handles).
Missing for the whole pin-request handshake and the legacy install-shortcut broadcast.

## 7. Home page management

Launcher3.

1. During a drag, `Workspace.addExtraEmptyScreenOnDrag` and `addExtraEmptyScreens` offer a trailing
   empty page; on drop it is committed (`commitExtraEmptyScreens`) or removed
   (`removeExtraEmptyScreen`, `removeExtraEmptyScreenDelayed`). `stripEmptyScreens` drops empty pages
   after items are removed; `convertFinalScreenToEmptyScreenIfNecessary` keeps the last page empty.
2. The default page is `DEFAULT_PAGE` = 0; `moveToDefaultScreen` snaps there and focuses it. The Home
   key while already home goes there (`Launcher.onNewIntent`).
3. `OptionsPopupView` offers "Edit home screen" (`enterHomeGardening`, state `EDIT_MODE`) only behind the
   `MULTI_SELECT_EDIT_MODE` flag. No page-reorder UI was found in the files read.
4. Page-count limits and the page indicator were not read.

Termux Launcher. `HomePageEdits` renumbers pages on add, remove and reorder and clamps the default;
`HomeActionsSheetPolicy` offers ADD_PAGE, EDIT_PAGES (reorder, remove, add, choose the default) and
REMOVE_PAGE (only when the page is empty and more than one page remains); `HomeSettings.defaultPage`
(`KEY_DEFAULT_PAGE`). An edit session keeps a snapshot, and its cross restores the user's empty pages.
Gap: Same for the default page and removing empty pages. Different: pages are made by menu, not by an
extra page during a drag. Different: page reorder exists here and was not found in the Launcher3 files
read.

## 8. Search and drawer; work profile

Launcher3.

1. `ActivityAllAppsContainerView` holds `AdapterHolder`s MAIN, WORK and SEARCH. The search box is
   `search_container_all_apps` (a floating bar behind `Flags.floatingSearchBar`). Entering search
   switches to `SearchRecyclerView`; `SearchTransitionController` animates the A-Z list out (300 ms
   default) and `reset(animate, exitSearch)` returns. Back from search goes through the search
   transition and `reset`.
2. Layout: `AlphabeticalAppsList`, a grid of `numShownAllAppsColumns`, a fast scroller (`fast_scroller`,
   behind `Flags.letterFastScroller()`), `FloatingHeaderView`. Predictions are not in this file.
3. Work profile: tabs exist only when work apps exist (`shouldShowTabs`, `mHasWorkApps`): an
   `AllAppsPagedView` with a `PersonalWorkSlidingTabStrip`. `WorkProfileManager` owns the state and the
   pause toggle (the toggle is not defined in this file; it fades out when search begins). Disabled
   and education cards: `VIEW_TYPE_WORK_DISABLED_CARD`, `VIEW_TYPE_WORK_EDU_CARD`. Private space has its
   own header (`PrivateProfileManager`).
4. Search from Home: the Hotseat holds a QSB (`search_container_hotseat`, `Hotseat`); the search
   provider behind it is outside this tree and was not read.

Termux Launcher. `AppDrawerSearchController`, `AppDrawerSearchModel`, `AppDrawerSearchInputView`; the
list through `AppDrawerController` with an A-Z side (`AppDrawerAzSide`, `AppDrawerSectionIndex`),
categories (`AppDrawerCategory*`) and drawer folders; the dock carries a search field
(`PromptLineFieldView`). `LauncherAppDataProvider` calls `launcherApps.getProfiles()` so work apps are
listed. Back inside the drawer: `AppDrawerController.onBackPressedInDrawer`.
Gap: Same for search and A-Z. Different: categories, folders, prompt-line search. Missing:
Personal/Work tabs and the work pause toggle (no tab and no `isQuietModeEnabled` use found).

## 9. Wallpaper and Home settings entry

Launcher3.

1. `WorkspaceTouchListener`: a press on empty workspace, with no floating view open, in `NORMAL` state,
   inside the drag layer inset by the device profile's insets and edge margin, waits for a long press;
   movement beyond twice the touch slop cancels it. On fire: a `LONG_PRESS` haptic (ignoring the view
   setting), then `launcher.showDefaultOptions(x, y)`. A mouse right-click shows it at once.
2. Popup (`OptionsPopupView.getOptions`): Wallpapers (`ACTION_SET_WALLPAPER`, `FLAG_ACTIVITY_CLEAR_TASK`,
   focus-wallpaper extras; a toast when an admin disabled it), Widgets (flow 6), Edit home screen
   (flag), Home settings (`ACTION_APPLICATION_PREFERENCES`, new task, clear task).
3. A tap on empty space calls `mWorkspace.onWallpaperTap` (body not read).
4. The file does not check drag, edit or lock state; the workspace's `isHandlingTouch` guards a scroll.

Termux Launcher. `HomeActionsSheet` (with `HomeActionsSheetPolicy`): Add a widget, Add an app, Add a
page, Edit pages, Wallpaper (`Item.WALLPAPER`), Appearance, Launcher settings, Help, plus the layout
lock (`HomeLayoutLock`: Settings > Home > Lock Home layout).
Gap: Same entry and the same top rows. Different: a sheet with more rows, and the lock, which has no
counterpart in the Launcher3 files read. Launcher3's flagged edit mode corresponds to our Edit pages
row.

## 10. Back and Home key in each state

Launcher3.

1. Back: `Launcher.getOnBackAnimationCallback` runs, in order, the auto-cancel action mode, the drag
   (`mDragController.cancelDrag`), the top open floating view (`AbstractFloatingView.getTopOpenView`,
   `canHandleBack`), registered `BackPressHandler`s, then the state handler (`onStateBack`). A folder
   is a floating view; while its name is edited, `Folder.onBackInvoked` first dispatches a back key to
   the name field. `WidgetsFullSheet` clears search first. The resize frame closes on a touch outside.
2. Home: `Launcher.onNewIntent`: `alreadyOnHome` is `hasWindowFocus()` and no
   `FLAG_ACTIVITY_BROUGHT_TO_FRONT`. For `ACTION_MAIN` it runs `closeAllOpenViewsExcept`, goes to
   `NORMAL`, and resets the apps view when not already home. `shouldMoveToDefaultScreen` needs
   `alreadyOnHome`, the NORMAL state and no open floating view, and the move
   (`mWorkspace::moveToDefaultScreen`) is posted unless a touch is being handled.
3. Haptics: lift `LONG_PRESS` (`LauncherDragController.startDrag`), empty-space long-press `LONG_PRESS`
   (`WorkspaceTouchListener`), cell change `MSDLToken.DRAG_INDICATOR_DISCRETE`
   (`CellLayout.visualizeDropLocation`). None in `Folder` or `AppWidgetResizeFrame`; `Workspace` turns
   its own haptic feedback off in its constructor.
4. The state manager's own back order (`LauncherState` back target) was not read.

Termux Launcher. Back: `WidgetPaneController.onBackPressed` (leaves edit mode first, then
`WidgetPaneView.onBackPressed`), `AppDrawerController.onBackPressedInDrawer`, and a folder rename
cancels on KEYCODE_BACK (`FolderRenameController`); the activity-level dispatch was not traced. Home:
`TermuxActivity.onNewIntent` closes every overlay (`mOverlays.closeAll(CloseReason.HOME)`), and
`mHomePress.goesHome()` then `goHome` lands on Home page 1, from a window or from another page.
Haptics found: `LONG_PRESS` in `WallStripRowView`, `WidgetCellView`, `WidgetPickerAdapter` and
`WidgetGridView`; `CLOCK_TICK` in the drawer drag, the rope column and the size wheel; none found on
the Home move or the folder merge.
Gap: Same for "Home closes everything". Different: our Home goes to the first Home page, Launcher3's to
the default page, and only when already home and idle.

## Not reached or not read

- `popup/OptionsPopupView.java` returned 404; it was read from `views/OptionsPopupView.java`.
- `Workspace.java`, `CellLayout.java` and `Launcher.java` are large and the fetcher cut each at 100 000
  characters. `Workspace` was read in two chunks (the last 1 652 characters unread); `CellLayout`'s last
  7 600 and `Launcher`'s last 77 592 were not read (`onBackPressed`, `startActivitySafely`, uninstall).
- No workspace edge-scroll timer was found (`DragController`, `LauncherDragController` and `DragLayer`
  hold none). The page-turn mechanism read is `SpringLoadedDragController` (500 ms hover).
- The developer.android.com pages for `LauncherApps` and `LauncherApps.PinItemRequest` returned
  navigation only; the method lists for those two came from the fetch tool's own knowledge and are not
  verified in a primary source. Pin-request behaviour above rests on `AddItemActivity` and
  `PinItemDragListener`.
- The drag-and-drop developer page gave concepts only; `DragEvent` details are not verified there.
- Not read at all: `PopupPopulator` (`MAX_SHORTCUTS`), the notification container and dot code,
  `WidgetCell`, `FolderPagedView`, `PagedView`, `AbstractFloatingView`, `WorkProfileManager`,
  `LauncherState` back targets, the wallpaper chooser, the QSB provider.
- Our side: the activity-level back dispatch was not traced; spring-loaded behaviour and displacement
  during a drawer drop were not verified; where a table cell says "not found" it is the result of a
  grep or a class scan, not a proof of absence.

## Sources

Launcher3 (each under
`https://android.googlesource.com/platform/packages/apps/Launcher3/+/refs/heads/main/src/com/android/launcher3/`):

- `dragndrop/DragController.java` (`cancelDrag`, `endDrag`, `checkTouchMove`, `findDropTarget`, `mIsInPreDrag`)
- `dragndrop/LauncherDragController.java` (`startDrag`, LONG_PRESS haptic, `getDefaultDropTarget`)
- `dragndrop/SpringLoadedDragController.java` (`ENTER_SPRING_LOAD_HOVER_TIME`, `onAlarm`)
- `dragndrop/DragLayer.java`, `dragndrop/AddItemActivity.java`, `dragndrop/PinItemDragListener.java`
- `states/SpringLoadedState.java`
- `Workspace.java` (`REORDER_TIMEOUT`, `DRAG_MODE_*`, `onDragOver`, `onDrop`, `onNoCellFound`, empty-screen methods, `moveToDefaultScreen`)
- `CellLayout.java` (`REORDER_ANIMATION_DURATION`, `performReorder`, `visualizeDropLocation`, `canCreateFolder`)
- `Launcher.java` (`getOnBackAnimationCallback`, `onNewIntent`, `handleActivityResult`)
- `touch/ItemLongClickListener.java`, `touch/WorkspaceTouchListener.java`
- `DeleteDropTarget.java`, `SecondaryDropTarget.java`, `Hotseat.java`
- `folder/Folder.java`, `folder/FolderIcon.java`
- `popup/PopupContainerWithArrow.java`, `popup/SystemShortcut.java`, `views/OptionsPopupView.java`
- `widget/picker/WidgetsFullSheet.java`, `AppWidgetResizeFrame.java`
- `allapps/ActivityAllAppsContainerView.java`
- Directory listing of `dragndrop/`

Android developer docs:

- https://developer.android.com/develop/ui/views/launch/shortcuts/creating-shortcuts
- https://developer.android.com/develop/ui/views/appwidgets/host
- https://developer.android.com/develop/ui/views/touch-and-input/drag-drop
- https://developer.android.com/reference/android/content/pm/LauncherApps (body not readable)
- https://developer.android.com/reference/android/content/pm/LauncherApps.PinItemRequest (body not readable)

Our side (this worktree): `app/src/main/java/com/termux/app/launcher/` (`drawer/AppDrawerDragController`,
`drawer/DrawerHandOverPolicy`, `widget/HomeAppDropPolicy`, `widget/HomeItemPlacementPolicy`,
`widget/HomeFolderPolicy`, `widget/WidgetPaneController`, `popup/AppMenuPlan`, `home/HomePageEdits`,
`home/HomeActionsSheetPolicy`, `home/HomeSettings`, `data/LauncherAppDataProvider`),
`app/src/main/java/com/termux/app/SuggestionBarView.java`, `app/src/main/java/com/termux/app/TermuxActivity.java`,
`docs/GLOSSARY.md`.
