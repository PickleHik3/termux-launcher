# Lawnchair 16 idiom comparison (2026-10-23, issue #149)

Research only; no code changed. Lawnchair side read from branch `16-dev` of
LawnchairLauncher/lawnchair on 2026-10-23 (Launcher3 sources sit directly under `src/` and `res/`,
there is no Launcher3 submodule). Line numbers are the files as fetched that day; a number is
quoted only where it was read in the file. "Not verified" marks what was not read.
Our side: constants read at file:line in this worktree (feat/unification-2, 6f6028a3c).

Fetched: Workspace, Launcher, DragController, DragView, Folder, PopupContainerWithArrow, ArrowPopup,
SystemShortcut, WidgetsFullSheet, WidgetCell, WorkspaceTouchListener, PagedView, Hotseat, CellLayout,
DeleteDropTarget, AllAppsSwipeController, LauncherAnimUtils, ItemLongClickListener, `res/values/dimens.xml`,
`config.xml`. Could not fetch (404): `allapps/AllAppsContainerView.java` and `anim/Interpolators.java`
at the paths tried (Interpolators now lives in the `platform_frameworks_libs_systemui` submodule).
Only Lawnchair's `ui/popup/` and `search/` file lists were read, not their contents.

## 1. Placing things

| Topic | What Lawnchair / Launcher3 does | What we do | Where in our code | Gap |
|---|---|---|---|---|
| Widget entry | Long-press an empty spot of Home opens an options popup that includes Widgets (WorkspaceTouchListener, popup contents not read); also the app popup's Widgets row (`SystemShortcut.WIDGETS`, SystemShortcut.java:142) | A Widgets page in the Wall with its own picker sheet; entry through the page, not a popup row | `launcher/widget/WidgetPaneView.java:256` (picker), `WidgetPickerSheetView.java` | No "Widgets" row in the app popup; no long-press-on-empty-Home entry (not verified that ours lacks it) |
| Picker | `WidgetsFullSheet`: bottom sheet, apps listed with expandable widget rows, search, recommendations; list top corner 28dp, row content corner 4dp (dimens `widget_list_top_bottom_corner_radius`, `widget_list_content_corner_radius`); fade-in 150ms (WidgetsFullSheet.java:108); sheet open/close 267ms (`config_bottomSheetOpenDuration`) | `WidgetPickerSheetView` with a hint shown 3500ms | `WidgetPickerSheetView.java:62` | Sheet corner, motion and grouping not compared (ours not measured) |
| Drop | Drag a preview from the sheet, or tap the "Add" button on a cell (`WidgetCell` add button, fade 100ms, `ADD_BUTTON_FADE_DURATION_MS`); drag view zooms 150ms (`DragView.VIEW_ZOOM_DURATION`) with spring stiffness 4000 | Widget grid with cell-sized widgets; delivery settles after 160ms | `WidgetGridView.java:406` (`SIZE_DELIVERY_SETTLE_MS = 160L`) | Tap-to-add exists in ours? Not traced |
| Resize | Resize frame around the widget, drag handles on sides; frame padding 24dp, margin 23dp (dimens `resize_frame_background_padding`, `resize_frame_margin`) | Not traced (handles and snap not found by the scout) | `WidgetGridCaps.java:27` (`MIN_CELL_DP = 55f`), `WidgetGridMetrics.java:15` (`GAP_DP = 8f`) | Unknown |
| Grid snap | Cell grid from device profile; reorder preview after a 650ms hover (`Workspace.REORDER_TIMEOUT`, Workspace.java:267), cells shift with a 150ms animation, 0.12 magnitude (CellLayout.java:207-208) | Cell grid with 55dp minimum cell and 8dp gap | as above | Reorder hover delay and preview shift not found in ours |
| App icon to Home | Drag from the drawer onto a page; drop animation 100-500ms (`config_dropAnimMinDuration/MaxDuration`), 300ms when it snaps to an adjacent page (Workspace.java:190) | Drag out of the drawer via `startDragAndDrop` | `SuggestionBarView.java:7278-7310`, pinned-bar drop at `:7368` | No drop animation constants found; cost of a drag shadow vs a live preview not compared |
| Popup "add" action | Not in stock Launcher3 popup (it offers Widgets, App info, Don't suggest, Uninstall, Install) | Pin row in the popup | `SuggestionBarView.java:6198` | Equivalent exists |
| Move and remove | Remove/Uninstall drop-target bar at the top while dragging; 56dp target (`dynamic_grid_drop_target_size`), gap 28dp, frame radius 80dp (dimens `drop_target_button_*`); labels from `remove_drop_target_label` | Uninstall is a popup row, no drop target found; remove page in the pages editor | `SuggestionBarView.java:6074`, `launcher/home/HomePagesEditor.java:164` | No drag-to-remove bar |
| Folders | Drop one icon on another after a hover, folder forms with a ring preview (`DRAG_MODE_CREATE_FOLDER`), rename inline in the open folder; open animation 200ms (`config_materialFolderExpandDuration`), content padding 16dp top, 8dp sides | Home folders with a popup that springs in and out, rename through the dock's rename title | `launcher/widget/HomeFolderPopup.java:45-47`, `HomeFolderPolicy` | Creation gesture (drop icon on icon) not traced; motion is a spring vs Lawnchair's 200ms tween |

## 2. The dock

| Topic | Lawnchair | Ours | Where | Gap |
|---|---|---|---|---|
| Slots and fill | Hotseat is a one-row CellLayout; count from the device profile (not verified here); empty slots stay empty, a drop on an occupied slot reorders | Dock slot count comes from size presets; `appSlots = slots - 1` (the Drawer tile takes one) | `dock/DockLayoutPolicy.java:29` (`SIZE_PRESETS = {1.72, 1.95, 2.18, 2.45}`), `DrawerTilePolicy.java:55-56` | Ours is size-driven, theirs count-driven; the user sees "how many icons fit" differently |
| Pin / swap / reorder | Drag between drawer, Home and dock; the reorder hint shows after 650ms; icons slide aside | Reorder handle in the pinned apps editor; drag onto the pinned bar | `PinnedAppsEditor.java:606`, `SuggestionBarView.java:7368` | Ours goes through an editor handle; theirs is direct drag with a live reorder preview |
| Long-press offers | On an icon: the app popup (same as Home). On empty dock: the Home settings sheet (Lawnchair: wallpaper/widgets/settings, `LauncherOptionsPopup.kt`; content not read) | Dock long-press behaviour not traced | `SuggestionBarView.java:1088, :5961` (long-press timeout) | Unverified |
| Icon size | Fixed by grid profile | Min 20dp, rail icon 38dp, toolbar 37.5dp | `DockLayoutPolicy.java:78-101` | Ours is configurable; fine |

## 3. Popups and surfaces

| Topic | Lawnchair / Launcher3 | Ours | Where | Gap |
|---|---|---|---|---|
| App popup shape | Stack of rounded rows: outer radius = dialog corner radius (`Themes.getDialogCornerRadius`, ArrowPopup.java:139), inner joins 4dp (`popup_smaller_radius`), single item 100dp pill (`popup_single_item_radius`); item 216dp x 52dp (`bg_popup_item_width/height`); arrow 12dp x 10dp pointing at the icon | One anchored panel, width 188-320dp, max height 0.45 of screen, 4dp gap, medium-corner surface | `popup/AnchoredMenuGeometry.java:17-23`, `AnchoredMenu.java:243-251` | We have no arrow, no row-group shapes, one big panel. Most visible difference |
| Elevation and tint | Elevation 2dp (`deep_shortcuts_elevation`); surface tint from dynamic colour shades (`color-v31/popup_shade_first/second/third`) | Elevation from `m3_comp_menu_container_elevation`; optional blur plate and tint | `AnchoredMenu.java:243-249, :275` | Shade steps per row vs one tint |
| Popup motion | Open 200ms with overshoot 200ms, fade 83ms, child fade 83ms; close 233ms (ArrowPopup.java:83-94; legacy 276/200ms at :71-77); interpolators EMPHASIZED_DECELERATE / EMPHASIZED_ACCELERATE (ArrowPopup.java:19-20) | Enter/exit not confirmed | `AnchoredMenu.java` | Unverified; needs a read or a screen recording |
| Popup rows | App info, Widgets, Install, Don't suggest, Uninstall (SystemShortcut.java), plus app shortcuts above; collapse threshold 6 (PopupContainerWithArrow.java:95) | Pin, uninstall and other rows (listed in SuggestionBarView) | `SuggestionBarView.java:6074, :6198` | Ordering and icon-leading row style not compared |
| Drag out of popup | Drag a shortcut row after 16dp (`deep_shortcuts_start_drag_threshold`) to place it | Not traced | n/a | Unknown |
| Widget sheet | See section 1 | | | |
| Folder sheet | Folder is an in-place expanding panel, 200ms | Spring popup over a dim | `HomeFolderPopup.java` | Motion model differs |
| Search field | Pill, 48dp high (`all_apps_search_bar_field_height`); group radius 28dp, result radius 4dp (`all_apps_recycler_view_decorator_*`); Lawnchair's own search in `lawnchair/src/app/lawnchair/search/` and `allapps/views/Search*` (contents not read) | Search field is a glossary surface; constants not collected | n/a | Unverified |
| A-Z scrub | Fast-scroll track 6-8dp, thumb 52dp, popup bubble 75x62dp with 32dp letter (dimens `fastscroll_*`); letters on the list side, 14sp | Edge-page delays 560 / 420 / 520ms, horizontal dominance 1.3 | `AzScrubGesture.java:83-108` | Their bubble is a standard; ours is not compared visually |
| Drawer rows | Section letters, grid of icons, work tab, header pill radius 12dp (`all_apps_header_pill_corner_radius`) | Drawer tiles | `launcher/drawer/*` | Layout comparison needs screenshots |

## 4. Gestures and timing

| Topic | Lawnchair / Launcher3 | Ours | Where | Gap |
|---|---|---|---|---|
| Page commit | A move beyond 0.4 of the page width commits (`SIGNIFICANT_MOVE_THRESHOLD`, PagedView.java:85); a reversal beyond 0.33 returns (`RETURN_TO_ORIGINAL_PAGE_THRESHOLD`, :83) | Wall: 0.35 of the page; widgets: 1/3 | `wall/PaneWallPolicy.java:20`, `WidgetPaneView.java:139` | Close; ours is a touch more eager than 0.4 and has no "return" threshold |
| Fling | Fling above 500dp/s (400dp/s "easy fling"), min fling 250dp/s, min page-snap velocity 1500dp/s (dimens `fling_threshold_velocity`, `easy_fling_threshold_velocity`, `min_fling_velocity`, `min_page_snap_velocity`) | Wall 400dp/s with a 25dp minimum distance; widgets 900dp/s; drawer 900px/s (a px unit, not dp), 0.12 min progress | `PaneWallPolicy.java:28-33`, `WidgetPaneView.java:139-143`, `AppDrawerCommitPolicy.java:29-31` | Our thresholds differ by pane (400 vs 900); one number would feel more uniform. Drawer is in px, so it varies with density |
| Settle | Duration scales with distance over velocity: `4 * round(1000 * distance / velocity)` with the velocity floor at 1500dp/s (PagedView.java:1857-1862); default 750ms (`config_pageSnapAnimationDuration`) | Settle curve cubic-bezier(0.3, 0.2, 0.05, 1); widget settle 160ms; spring settle capped at 3000ms | `terminal/Motion.java:24-27`, `WidgetPaneView.java:143`, `wall/SettleSpring.java:22` | Ours is a spring on the wall and a fixed 160ms on widgets; two settle models |
| Overscroll | Damped overscroll, `OVERSCROLL_DAMP_FACTOR`, edge half-page wrap (PagedView.java:1489) | Edge resistance 0.35; drawer over-pull up to 96dp | `PaneWallPolicy.java:35`, `AppDrawerContentView.java:114` | Comparable |
| Long-press | Platform default timeout, no override found in `config.xml`; haptic LONG_PRESS on drag start (Workspace.java:1979-1981) | Platform timeout | `SuggestionBarView.java:1088, :5961` | Same. Haptic on lift in ours not checked |
| Touch slop | Platform scaled slop; slop damps (x4) when a swipe is more than 30 degrees off axis (Workspace.java:283-285, 1412-1420) | Platform scaled slop | `SuggestionBarView.java:1092` | No angle damping in ours (not verified) |
| Drawer open | Swipe up from Home; open 600ms, close 300ms (`config_allAppsOpenDuration/CloseDuration`); atomic transition 0.3333, scrim and fade steps (AllAppsSwipeController.java:71-96) | Drawer commits at 0.5 progress or fling; | `AppDrawerCommitPolicy.java:29-31` | Their drawer is a long animation with the workspace depth-scaling; ours not compared |
| Sheet motion | Bottom sheets 267ms | Not collected | | |

## 5. State at the landing (#142)

| Topic | Lawnchair | Ours | Where | Gap |
|---|---|---|---|---|
| Final size on arrival | Not read in the fetched files; believed to measure widgets on layout, not on settle (not verified) | Cells lay out every frame during the slide; the provider's size is delivered only after a 160ms settle, and only while the page rests | `WidgetGridView.java:321-335, :342-364, :406-423` | A widget's content can change size after the page has landed. This is the #142 symptom: expected, by design of the delivery gate |
| Gate | n/a | Page marked at rest by the controller when the wall shows it; cleared when the wall commits to leaving | `WidgetPaneController.java:385, :400` (`setPageAtRest`) | The gate waits for the settle, so the size lands after the page |
| Root cause (hypothesis) | | Sizes were withheld while moving so the next place's keyboard resize is not heard (comment at `WidgetPaneController.java:378-381`). The cost is a late resize | | The fix would deliver the final size before the slide ends, from the resolved arrangement (`ResolvedArrangement`, #141), not from a post-landing timer. Not traced end to end |

## Ranked changes

Each one is ticket-sized. T = taste, needs a mockup or grilling round before code.

1. **App popup as rows with an arrow (T, mockup).** Long-press shows a grouped card of rows (rounded
   outer corners, 4dp joins, a small arrow to the icon) with Material You surface shades. Area:
   `launcher/popup/AnchoredMenu*`, `AnchoredMenuGeometry`. Largest visible gap, and the developer named
   the popup first.
2. **Popup motion (T, grilling).** Open about 200ms with fade 83ms, close about 233ms with emphasised curves. Area: `AnchoredMenu`. First read our current motion (not verified).
3. **Widgets and App info rows in the app popup.** The popup offers "Widgets" and "App info" like every launcher. Area: `SuggestionBarView` popup row builder, `WidgetPickerSheetView` entry.
4. **Widgets land at their final size.** A widget is already the right size when its page arrives. Area:
   `WidgetGridView`, `WidgetPaneController`; this is #142 and may need the arrangement from #141. No taste question.
5. **One commit and fling rule for every pane.** Pages commit past about a third to 0.4 of a page, or at one fling speed (500dp/s is the Launcher3 value), in dp for the drawer too. Area: `PaneWallPolicy`, `WidgetPaneView`, `AppDrawerCommitPolicy`. Taste: pick the numbers in a grilling round.
6. **Settle by distance and speed.** A fast flick settles quickly, a slow drag settles slowly, one model on the wall and widgets (replace the fixed 160ms). Area: `SettleSpring`, `WidgetPaneView`. Needs a grilling round on feel.
7. **Drag to remove.** While dragging an icon a Remove and Uninstall target appears at the top. Area: drag paths in `SuggestionBarView`. Mockup first.
8. **Drop on icon to make a folder.** Hovering an icon over another for a moment shows a ring and makes a folder. Area: `HomeFolderPolicy`. First check whether it exists.
9. **Reorder preview while dragging.** Icons slide aside after a short hover (Launcher3: 650ms, 150ms slide). Area: dock/pinned bar drag handler. Mockup optional.
10. **Tap to add on a widget cell.** A widget in the picker has an Add button as an alternative to dragging. Area: `WidgetPickerSheetView`. Check first whether it exists.
11. **A-Z bubble to the standard form.** Letter bubble and track widths like Launcher3 (75x62dp bubble, 52dp thumb). Area: `AzScrubGesture` view. Taste, mockup.
12. **Sheet motion and corners.** Widget sheet and folder: 267ms open, 28dp top corners. Area: `WidgetPickerSheetView`, `HomeFolderPopup`. Taste.

## Unverified

- Our popup enter and exit motion, popup row list and ordering, drag-out-from-popup.
- Whether we already have tap-to-add, drop-to-folder, a reorder preview delay, resize handles and grid snap behaviour (the scout did not reach them).
- Launcher3's hotseat slot count, dock long-press menu, and long-press timeout overrides (none found in `config.xml`, not searched in Kotlin).
- The Lawnchair overrides in `lawnchair/src/app/lawnchair/` (popup, search, theme): file lists only, no values.
- `AllAppsContainerView` and `Interpolators` (fetch 404). EMPHASIZED curve values are not quoted.
- `SeamCrossing` and `SeamTravel` durations (only `SeamTravel.DIP_SWAP = 0.5f` seen), `SeamSwipe` reach values read but not placed in context.
- All Lawnchair numbers come from `16-dev` on 2026-10-23, not from a shipped 16 release tag.
