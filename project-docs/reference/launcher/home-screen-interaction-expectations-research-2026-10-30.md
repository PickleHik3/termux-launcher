# What users expect from an Android home screen — research (2026-10-30)

**Date:** 2026-10-30 · **Branch:** `v2-redesign` · Reference. Claims I could not trace to a primary source are marked
**(unverified)**; claims inferred from reading code rather than stated by a document are marked **(reasoned)**. The tables describe
the three platforms in their own words; the sections about this launcher use `docs/GLOSSARY.md` (open layer, Start in, App menu,
Home page, Appearance editor, home press).

**Decisions** taken from this note (the grilling round of 2026-10-30) are in
`project-docs/active/home-conventions-2026-10-30.md`: the rules, the departures and the work owed. This note stays the evidence.

**Question.** Which interactions do users take for granted on a launcher's elements (app icon, folder, dock, widget, home page,
app drawer and search, wallpaper, notification and status area, long-press menu, edit mode, gestures, Home and Back), what feedback
and ways out they expect, where Pixel Launcher (Launcher3), Samsung One UI Home and the iOS Home Screen disagree, and what counts
as a broken expectation, so Termux Launcher meets them.

## Sources and how far they reach

Numbers in the tables point to the list at the end. What I could and could not read:

- **Launcher3 source** was read as served by `android.googlesource.com` for `refs/heads/main`, head commit `2663cf0a414bd603d6a833cb691cea76eb1897b7`
  (dated 2025-03-10 by that server). It is the AOSP launcher, not the Pixel Launcher build, which adds Google code (feed, search, suggestions).
  A behaviour here is "what AOSP does"; Pixel's own build is not in the tree. Line numbers are from that snapshot and will drift.
- **Google Help** (`support.google.com/android`, `/pixelphone`) states Pixel/Android user-facing steps. Primary for behaviour, silent on timing.
- **Apple**: the iPhone User Guide pages (fetched 2026-10-30, they are the iOS 27 revision) and HIG JSON pages. Primary.
- **Samsung**: two official US support articles. They state a lot about the menus and little about Home/Back.
- **Material Design 3** (`m3.material.io`) is a script-rendered site; neither fetch tool returned any body text for the gestures or menus pages.
  No M3 claim is made here. This is a gap, not a finding.
- **Usability research**: one Nielsen Norman Group article was read in full for the quote used. **No peer-reviewed paper (CHI, MobileHCI)
  with a DOI was retrieved**, so none is cited. NN/g's own report on gestures (long press on iOS and Android) is paywalled and unread.
- The `ACTION_MAIN`/`CATEGORY_HOME` constants page for `Intent` did not render in the fetch tool. The contract is shown through
  Launcher3's own `onNewIntent` instead.

## Summary: the ten strongest expectations

1. **Home from inside the launcher means "back to the start".** Launcher3's `onNewIntent` closes every floating view, returns to the
   Normal state, resets the app list and scrolls the workspace to the default screen, but only when the launcher already has focus and
   is in Normal state with nothing open ([L3 Launcher.java:1639-1675]). A home press must not re-open the last open layer or leave an editor
   open.
2. **Back never leaves the home app.** Back is consumed in a fixed order (action mode, drag, top floating view, registered handlers,
   then the state, which returns to the previous state) and at Normal it does nothing destructive ([L3 Launcher.java:631-675,
   LauncherState.java:447-452]). Android's predictive-back docs say system back-to-home animations run only when the app declares no
   callback of its own ([A1]).
3. **Long-press is the door to everything, in this order of targets**: an icon opens a shortcuts popup and can then be dragged; empty
   space opens the home menu (Wallpaper, Widgets, Settings) with a haptic tick ([L3 WorkspaceTouchListener.java:196-215,
   OptionsPopupView.java:200-225]; Google Help [G1]; Apple HIG [H1]). iOS reaches edit mode the same way; Samsung adds a Lock layout switch.
4. **Pick-up is the start of edit mode, and drop is the end of it, with no Save.** Pixel lets you drag straight out of a long press
   [G1]; iOS needs a tap on the background (Face ID iPhones) or Home to end jiggle mode [A2]; Launcher3 returns from spring-loaded
   mode 500 ms after the drop ([L3 LauncherAnimUtils.java:39, Workspace.java:2061]). An edit applies and returns you where you were.
5. **Dropping an icon on another icon makes a folder; hovering pages in a neighbour page** after a short pause ([G1], [A2];
   Launcher3 enters `DRAG_MODE_CREATE_FOLDER` on hover, Workspace.java:2704; the reorder waits `REORDER_TIMEOUT = 650` ms, Workspace.java:249).
6. **Removing from the home screen and uninstalling are two different words.** Google: "Remove takes an app off your Home screen
   only. Uninstall takes it off your device" [G1]; Apple: Remove App then Remove from Home Screen or Delete App, with a confirm only
   for delete [A5]. Launcher3 prepares an undo before a drop-delete (`DeleteDropTarget.onDrop` → `prepareToUndoDelete`, line 122).
7. **A widget tap opens its app, and only declared buttons do something else.** HIG says an interaction outside buttons and toggles
   launches the app [H2]. Android widgets get touch and vertical swipe only [A3]. Long-press on a widget enters resize mode
   with handles [A3]; "tap outside the widget" ends it [G1].
8. **The app drawer opens by swiping up from the dock/bottom and is searchable by typing.** Google Help describes swipe up for All Apps
   [G1][G3]; iOS puts the list to the left of the last page, with a search field and an alphabetical list [A3b]. Samsung: swipe up or
   down for the Apps screen [S1].
9. **The system swipe from the bottom edge is the system's.** Both Android and iOS give it to Home/Recents [G4][A4]; a launcher that
   uses the bottom edge for its own gesture fights it. HIG: do not replace or conflict with system gestures [H3].
10. **A haptic accompanies a long press, a pick-up or a snap, and nothing else.** Android: "less is more", use the predefined
    constants, and the user's touch-feedback setting decides [A6][A7]. Launcher3 plays `LONG_PRESS` on the empty-space long press
    ([L3 WorkspaceTouchListener.java:206]).

## Home screen elements

Platform column: **P** = Pixel/AOSP Launcher3, **S** = Samsung One UI Home, **i** = iOS Home Screen. Source codes are in the list at the end.

### App icon

| Interaction | Expected result | Platforms | Source |
|---|---|---|---|
| Tap | Open the app | all | [G3][A5b] |
| Long-press, lift | Shortcuts list for the app (static + dynamic); an icon with none still gets a menu | P, i | [G1] "Touch and hold the app, then lift your finger. If the app has shortcuts, you'll get a list."; [H1] |
| Long-press, then drag | Pick the icon up; the page shows images of each Home screen to drop into | P | [G1] "Touch and drag the app. You'll find images of each Home screen."; L3 `Workspace.beginDragShared` (Workspace.java:1678) |
| Long-press on a shortcut in the popup, drag | Pin that shortcut as its own home icon | P | [G1] |
| Tap a shortcut in the popup | Run it without pinning | P | [G1] "To use a shortcut without adding it to a Home screen, tap it." |
| Number of shortcuts | Up to four on most launchers; not more | P, i | [A1b] "Most supported launchers display up to four shortcuts"; [H1] "you can provide a total of four" |
| Menu items Edit / Remove | The icon menu also offers to remove the app and edit the Home Screen | i | [H1] |
| Drag onto another icon | Make a folder, drop in the same gesture | P, S, i | [G1][S1][A2] |
| Notification badge or dot | Long-press can preview notifications | S | [S2] ("Notifications on app icons ... touch and hold an app to preview") |
| Haptic on pick-up | A tick when the icon lifts | P | **(reasoned)** no `performHapticFeedback` is in the drag path of the snapshot ([L3 DragController.java], [L3 Workspace.java]); the tick on icon long-press comes from the view's default long-press haptic, which Android documents as default for touch & hold ([A7]). `Workspace` switches its own haptic off (Workspace.java:332) |
| Pick-up visual | The dragged copy scales up over 500 ms | P | L3 `DragController.DRAG_VIEW_SCALE_DURATION_MS` (DragController.java:102) |

### Folder

| Interaction | Expected result | Platforms | Source |
|---|---|---|---|
| Tap | Open in place over the page | all | [G1][S1][A2b] |
| Tap outside the open folder | Close it | P, S | L3 `Folder.onControllerInterceptTouchEvent` (Folder.java:1684-1694); [S1] "To exit the folder, tap anywhere outside the folder." |
| Back | Close the open folder before anything else | P | L3 `getOnBackAnimationCallback` step 3, the top floating view (Launcher.java:631-675) |
| Tap the name | Edit the name, suggested names offered | P, S, i | [G1] "tap the suggested folder name"; [S1]; [A2b] (long-press folder, Rename) |
| Drag an icon out | Out of the folder onto the page; an emptied folder disappears | P, i | [A2b] "When the folder is empty, it's deleted automatically."; [G1] "After the last one is removed, the Home screen will be removed." (same for a page) |
| Drag over a folder from the page | The folder takes the icon | P | L3 `DRAG_MODE_ADD_TO_FOLDER` (Workspace.java:277 area) **(reasoned, constant names only)** |
| More than one page of items | A folder can scroll to a second page; hovering an edge scrolls it after 500 ms | P, i | L3 `Folder.SCROLL_HINT_DURATION = 500` (Folder.java:154); [A2b] "A folder can contain multiple pages of apps." |
| Close delay after dragging out | The folder lingers 400 ms before closing | P | L3 `Folder.ON_EXIT_CLOSE_DELAY = 400` (Folder.java:170) |
| Delete a folder | Samsung: the folder goes and its apps return to the Apps screen; two copies (Home + Apps) are deleted separately | S | [S1] |

### Dock (hotseat)

| Interaction | Expected result | Platforms | Source |
|---|---|---|---|
| Position | Bottom row, same on every page | all | [S1] "The bottom row on your Home screen is reserved for your favorite app shortcuts."; [A2] "drag ... to the Dock at the bottom" |
| Drag an icon onto it | Accepted as a normal drop target; the dock does not scroll with pages | P, S, i | [A2] **(page-scroll part reasoned)** |
| Tap, long-press | Same as an icon on the page | all | as above |
| Swipe up from the dock area | Open the app drawer | P, S | [G3] "Swipe up from the bottom of your screen to the top."; [S1] "Swipe up or down to access the Apps screen" |

### Widget

| Interaction | Expected result | Platforms | Source |
|---|---|---|---|
| Add | Long-press empty space, pick Widgets; choose the app, then the widget; long-press it and slide it to a page | P | [G1] |
| Add on iOS | Long-press the background, Edit, Add Widget; swipe to a size; Add | i | [A6b] (widgets article: "Tap Add Widget. ... Swipe to choose an appearance, then tap Add Widget.") |
| Configure | The app's configuration appears right after the drop; since Android 12 it can be reopened later | P | [A3] |
| Resize | Long-press then release enters resize mode with an outline and dots; drag handles or corners; tap outside ends | P | [A3]; [G1] "an outline with dots is visible on the sides ... tap outside the widget" |
| Resize result | The widget redraws for the new dp size; information widgets must fit without scrolling | P | [A3] |
| Tap on the widget | Open the app, deep-linked to the content shown; only buttons and toggles act without launching | i (HIG), P | [H2] "When people interact with your widget in areas that aren't buttons or toggles, the interaction launches your app." Android: [A3] "The only gestures available for widgets are touch and vertical swipe." |
| Long-press a widget | Resize mode, or on iOS the quick-actions menu with Edit Widget | P, i | [A3][A6b] |
| Move | Long-press and drag like an icon; it can cross pages | all | [A3] "Users can move widgets across their home screen panels." |
| Drop on a full spot | The neighbours reflow ahead of the finger; if nothing fits, the widget shrinks or returns | P | L3 `Workspace.onDrop` with `resizeOnDrop` (Workspace.java:2025-2213) |
| Replicates an icon | Adds nothing; users remove such widgets | i | [H2] "Replicating an app icon offers little additional value" |

### Home page / workspace

| Interaction | Expected result | Platforms | Source |
|---|---|---|---|
| Swipe left or right | Next or previous page; dots show where you are | all | [A2b] "a bright dot in a row of dots above the Dock shows you which page you're on" |
| Swipe past the last page, iOS | App Library | i | [A3b] "swipe left past all your Home Screen pages to get to App Library" |
| Swipe up | App drawer | P, S | [G3][S1] |
| Swipe down on the page | Notification panel is a switch on Samsung ("Swipe down for notification panel"); its default is **(unverified)** | S | [S2] "touch and hold your Home screen, then tap [switch] next to Swipe down for notification panel" |
| Swipe down on the page, Pixel | Notifications or Search | P | **(unverified)** not in Google Help pages read |
| Drag an icon to the page edge | After a short wait the next page appears; on the last page iOS makes a new one | P, i | [A2b] "You might need to wait a second for the new page to appear."; L3 `Workspace.scrollRight/scrollLeft` (Workspace.java:3245 area) **(timing not read)** |
| Remove the last item on a page | The page disappears | P, i | [G1][A2b] |
| Rearrange pages, hide pages | In edit mode, page thumbnails with checkmarks; drag to reorder | i, S | [A3b]; [S1] "Touch and hold the screen you want to move, and then drag it to the desired position." |
| Set the main/default page | A house icon on the page chooses it | S | [S1] "Tap the [house] icon ... The bolded Home icon indicates which one is the main Home screen." |
| Lock the layout | A switch freezes position and removal | S | [S1] "You can lock your Home screen to prevent its items from being removed or repositioned." |
| Hold a drag in the middle | Other items slide aside after 650 ms | P | L3 `Workspace.REORDER_TIMEOUT` (Workspace.java:249) |
| Spring-loaded mode | Pages shrink during a drag so you can see neighbours; exits 500 ms after the drop | P | L3 `LauncherState.SPRING_LOADED`, `SPRING_LOADED_EXIT_DELAY = 500` (LauncherAnimUtils.java:39) |
| Pinch | Samsung and some Pixel builds open a page overview | S | **(unverified)** no primary source retrieved; Launcher3's `OVERVIEW`/`EDIT_MODE` states exist (LauncherState.java:138) but the pinch trigger was not read |
| Double-tap | Lock the screen on some launchers | S | **(unverified)** |

### App drawer and search

| Interaction | Expected result | Platforms | Source |
|---|---|---|---|
| Open | Swipe up from the bottom of the home screen | P, S | [G3][S1] |
| Close | Back, or swipe down at the top of the list | P | L3 `LauncherState.onBackInvoked`: Back goes to the last state (LauncherState.java:447-452); swipe-down **(reasoned)** |
| Find | A search field at the top; type to filter; keyboard raised on open from the search entry | P, i | L3 `toggleAllAppsSearch` (Launcher.java:1691 area); [A3b] "Tap the search field ... Or scroll up and down to browse the alphabetical list." |
| Fast scroll | A vertical scrubber with a letter popup | P | L3 `RecyclerViewFastScroller` with `fast_scroller_popup` (ActivityAllAppsContainerView.java:273-276) |
| Long-press an app | Same menu and drag as an icon; dragging places a copy on the home screen, the drawer entry stays | P, i, S | [G1]; [A3b] "Touch and hold the app until the app icons begin to jiggle and the app moves to the Home Screen"; [S1] |
| Work apps | A separate Work tab | P | [G3] |
| iOS App Library | Categories plus Suggestions and Recently Added; apps cannot be re-categorised by the user | i | [A3b] |

### Wallpaper

| Interaction | Expected result | Platforms | Source |
|---|---|---|---|
| Open the picker | Long-press empty space, then Wallpaper (first item in the menu) | P | L3 `OptionsPopupView.getOptions` (OptionsPopupView.java:200-208) |
| Scrolls with pages | The wallpaper shifts slightly as you swipe pages | P | L3 `EXTRA_WALLPAPER_OFFSET` (OptionsPopupView.java:68) — an offset is passed to the picker; the parallax behaviour itself is **(reasoned)** |
| Return | Leaving the picker returns to the home screen the long-press came from | P | **(reasoned)** the picker is a separate activity started by `startWallpaperPicker`; Back returns to the launcher |

### Notification and status area

| Interaction | Expected result | Platforms | Source |
|---|---|---|---|
| Swipe down from the top edge | The system notification panel; the launcher does not own it | all | [S2] "just swipe down from the top of your device's screen to open the Notification panel." |
| Swipe down on the home page | Optional shortcut to the same panel | S | [S2] setting above |
| Badges / dots on icons | Show pending notifications; Long-press previews them (Samsung) | P, S, i | [S2]; iOS: [A3b] "To allow app notification badges to appear on apps in App Library, turn on Show in App Library." |
| Status bar tap | Scroll-to-top in apps on iOS, nothing on the home screen | i | **(unverified)** |

### Long-press menus

| Interaction | Expected result | Platforms | Source |
|---|---|---|---|
| Long-press empty space | Home menu: Wallpaper, Widgets, (Edit Home screen, flagged), Settings | P | L3 `OptionsPopupView.getOptions` (OptionsPopupView.java:200-225); `WorkspaceTouchListener.maybeShowMenu` (:196-215) |
| Haptic | One `LONG_PRESS` tick, `FLAG_IGNORE_VIEW_SETTING`, when the menu appears | P | L3 WorkspaceTouchListener.java:206 |
| Long-press only counts inside the safe area | Presses on the edge strips by the gesture margin do nothing | P | L3 WorkspaceTouchListener.java:100-109 (`mTempRect.inset(dp.edgeMarginPx ...)`) |
| Mouse right-click | The menu opens on press, no delay | P | L3 WorkspaceTouchListener.java:111 |
| Menu content | Short title, icon, no app name, at most four app shortcuts | i, P | [H1]; [A1b] |
| Menu dismissal | Tap outside or Back closes it | P | L3 `getOnBackAnimationCallback` step 3; **(tap-outside reasoned)** |
| A second long-press during a drag | Ignored | P | L3 `ItemLongClickListener.canStartDrag`: "Return early if an item is already being dragged" (ItemLongClickListener.java:181-196) |

### Edit / customise mode

| Interaction | Expected result | Platforms | Source |
|---|---|---|---|
| Enter | Long-press an item or the background | all | [G1][A2][S1] |
| Enter explicitly | A menu item "Edit home screen" (feature-flagged in AOSP) | P | L3 `OptionsPopupView.getOptions`, `MULTI_SELECT_EDIT_MODE` (OptionsPopupView.java:213) |
| Visible state | iOS: items jiggle; Pixel: pages shrink (spring-loaded) | i, P | [A2]; L3 `SPRING_LOADED` |
| Leave | iOS: tap the background (Face ID) or press Home (SE); Pixel: drop, then the launcher leaves spring-loaded 500 ms later; Back also returns | i, P | [A2]; L3 `Workspace.java:2061, 2228` |
| Undo of a delete | The drop-on-Remove prepares an undo | P | L3 `DeleteDropTarget.onDrop` (DeleteDropTarget.java:119-124); the surface of that undo (snackbar) was **not read** |
| Cancel a drag | Back while dragging cancels it and the item goes home | P | L3 `getOnBackAnimationCallback` step 2, `mDragController::cancelDrag` |
| Drop on a rejected target | Visible reject feedback | i | [H4] "When people drop an item on an invalid destination, or when dropping fails, provide visual feedback." |
| Undo after a drop | Prefer letting people undo | i | [H4] "Prefer letting people undo a drag-and-drop operation." |
| Reset layout | A named reset in Settings | i | [A2] "Reset Home Screen Layout" |

### Gestures

| Interaction | Expected result | Platforms | Source |
|---|---|---|---|
| Swipe up from the bottom | Go Home from any app | P, i | [G4]; [A4] "Swipe up from the bottom edge of the screen to return to the Home Screen at any time." |
| Swipe up and hold | Recents / App Switcher | P, i | [G3] "Swipe up from the bottom, hold, then let go."; [A4] |
| Swipe along the bottom edge | Switch to the adjacent app | i | [A4] |
| Edge swipe | Back (Android gesture navigation) | P | [G4]; predictive back [A1] |
| Gesture sensitivity | The user can change the edge width | P | [G4] |
| Tap / swipe / drag | The same everywhere; do not give them app-private meanings | all | [H3] "Avoid using a familiar gesture like tap or swipe to perform an action that's unique to your app" |
| Custom gestures | Add them only for frequent specialised tasks, supplement the standard way, teach them | all | [H3] |
| Hidden gestures | Swipes are less discoverable; give them a cue; one swipe, one meaning per screen | all | [N1] |

### Home and Back

| Interaction | Expected result | Platforms | Source |
|---|---|---|---|
| Home while in the launcher, all quiet | Move to the default page | P | L3 `Launcher.onNewIntent`: `moveToDefaultScreen` when `alreadyOnHome && isInState(NORMAL) && getTopOpenView == null` and not mid-touch (Launcher.java:1646-1674) |
| Home with something open | Close all floating views (folders, popups, sheets) with animation; return to Normal state | P | L3 Launcher.java:1651-1660 (`closeAllOpenViewsExcept ... TYPE_LISTENER`, `goToState(NORMAL)`) |
| Home while in All Apps | Return to the home screen and reset the app list | P | L3 Launcher.java:1657-1662 (`mAppsView.reset`) |
| Home while the keyboard or an overlay (feed) is up | The overlay hides | P | L3 `mOverlayManager.hideOverlay` (Launcher.java:1684) |
| Home from another app | Come back to the launcher where it was, not reset; the default-page jump only happens when already home | P | L3: `alreadyOnHome` is false for `FLAG_ACTIVITY_BROUGHT_TO_FRONT`, so the jump is skipped (Launcher.java:1647-1650) |
| Home contract | The home app declares `ACTION_MAIN` + `CATEGORY_HOME` | all | **(unverified in this pass)** the `Intent` reference page did not render; `onNewIntent` above shows launcher handling of `ACTION_MAIN` |
| Back with a drag in flight | Cancel the drag | P | L3 Launcher.java:631-675 |
| Back with a popup, folder, sheet open | Close the top one | P | same |
| Back from the normal home screen | Nothing visible; a launcher does not exit | P | L3 `LauncherState.onBackInvoked` does nothing at NORMAL (LauncherState.java:447-452) |
| Back with predictive animation | Handled by the system only if the app has no overriding callback; Android 16 has an observer priority that does not consume | P | [A1] "`PRIORITY_SYSTEM_NAVIGATION_OBSERVER` ... doesn't consume the back event" |
| Back from All Apps | Return to the page the drawer was opened from | P | L3 `LauncherState.onBackInvoked` goes to `getLastState()` |
| iOS | There is no Back; Home (swipe up or Home button) is the only way out | i | [A4] |

## Where platforms disagree

| Topic | Pixel / Launcher3 | Samsung One UI Home | iOS | Sources |
|---|---|---|---|---|
| App list | A drawer; swipe up opens it | A separate Apps screen; swipe up or down | No drawer; App Library past the last page | [G3][S1][A3b] |
| Edit mode entry | Long-press an icon or empty space; no visible "jiggle" | Long-press empty space opens Settings, page management and a layout lock | Long-press, then Edit Home Screen; icons jiggle | [G1][S1][A2] |
| End of edit mode | Implicit on drop | Tap Back or the checkmark **(unverified)** | Tap the background or Done | [A2] |
| Home with a free hand | Page returns to default | Same expected; the main-page house sets the default | Home takes you to the first page after the second press **(unverified)**; guide says only that swiping up returns Home | [S1][A4] |
| Widgets | On the page and resizable | On the page and resizable; plus Edge panels | Home plus Today View and Smart Stack | [A3][S2][A6b] |
| Removal | "Remove" and "Uninstall" listed separately | Not read | "Remove from Home Screen" and "Delete App"; Delete confirms | [G1][A5] |
| New icon policy | Added to the page by default | Added to the Apps screen by default **(unverified)** | A setting: Home Screen and App Library, or App Library only | [A3b] |
| Notification dots | Dot, long-press peek | Number badge optional; long-press preview | Badges that show in App Library only if turned on | [S2][A3b] |
| Page hiding | Not offered | Not read | Hide pages; App Library comes closer | [A3b] |

What carries over, as far as the sources show: long-press to enter edit or open a menu, drag to move, drag onto to make a folder, tap
outside to close, and "swipe up from the bottom edge goes home" on both Android and iPhone. Cross-platform carry-over is **reasoned**
from the repeated wording across [G1], [S1] and [A2]; no study retrieved measures it.

## What counts as a broken expectation

Each line is something the primary sources imply a user will read as a bug, said in this launcher's words.

- A home press leaves an open layer or the Appearance editor open, or re-opens it later. (Launcher3 closes them all.)
- A home press inside the launcher does not land on Start in. (Launcher3 lands on its default screen, only when nothing is open
  and no touch is in flight.)
- Back leaves the launcher, shows a blank frame, or closes the wrong layer first. (The order is action mode, drag, topmost layer, state.)
- An editor has no way out (no Back, no Done, no outside tap where one is expected), or leaving it lands on another page or card
  than the one you came from.
- A drop with no feedback: no drop-target highlight, no reject cue, no Undo for a Remove.
- A long press with no haptic or no visible response, or two long presses that start two drags.
- A widget tap that does nothing, or does something other than open its app (apart from declared buttons).
- Remove (off a Home page) and Uninstall (off the phone) share a word or an action.
- A gesture in the system's bottom-edge zone that fights its Home or Recents swipe.
- A custom gesture that replaces a standard one instead of adding to it, or has no visible cue.

## Checklist for Termux Launcher

Phrased as tests. Which of these became rules, and which are recorded departures, is in
`project-docs/active/home-conventions-2026-10-30.md`.

**Home press and Back**
1. With the Appearance editor, a folder, the App menu, Home's long-press sheet, the app drawer or the wallpaper page open, a home
   press closes all of them (the Appearance editor through its unsaved-changes rule) and lands on Start in.
2. With nothing open, a home press lands on Start in and does not start a second animation if a touch is in flight.
3. Back closes, in order: an active selection or find mode, a drag in flight, the topmost open layer, then steps back; on a quiet
   page it does nothing and never finishes the activity.
4. Back above peek in the Appearance editor collapses the card sheet to peek; at peek it runs the unsaved-changes dialog. Neither
   leaves the launcher.
5. A home press that brings the launcher back from another app shows the page you left, not Start in.

**Long press and drag**
6. A long press on an app icon opens the App menu with at most four shortcuts and plays one long-press haptic; dragging out of
   that press picks the icon up.
7. A long press on an empty cell of a Home page opens Home's long-press sheet and plays one haptic; it does nothing inside the
   edge-gesture margin.
8. A second long press while a drag is in flight starts nothing.
9. Dragging holds the slot for about 650 ms before neighbours reflow, and holding at a page edge brings the neighbouring page.
10. A drop onto an icon makes a folder with visible acceptance; onto a folder adds to it; a folder left with one app dissolves
    into that app's icon.
11. A rejected drop shows a reject cue and returns the item; a Remove drop offers Undo.

**Editors**
12. Every editor (the Appearance editor, Edit pages, the widget editor, the wallpaper page) leaves by Done or Back, and leaving
    shows the page and card you came from.
13. A drag's lifted state ends a short fixed time after the drop, the same for every surface.
14. Remove and Uninstall are separate words and separate actions wherever both are offered.

**Widgets**
15. A tap on a widget (an app widget or the launcher's own) opens its app or its declared button's action; a long press lifts it
    into resize with handles; a tap outside ends the resize.
16. A widget dropped on a full spot reflows its neighbours or shrinks; it never silently overlaps.
17. An app widget's configuration screen appears after the drop and can be reopened.

**App drawer and search**
18. The app drawer opens from the Drawer tile or the swipe up; Back closes it; a home press closes it and clears its search text
    and scroll.
19. The Search field is one tap away, and the system keyboard is raised through `TermuxActivity.onSystemImeRequested()`.
20. The A–Z index shows its letter while touched and does not take the page swipe.
21. A long press on a drawer entry drags a copy to a Home page; the drawer entry stays.

**Gestures and chrome**
22. No launcher gesture starts in the system's bottom-edge zone; the keyboard swipe starts on the page's bottom border, above the dock.
23. The status swipe does not shadow the system notification panel unless opted in; the two-finger status pull stays opt-in.
24. Every custom gesture has a tap path and a visible cue.
25. Haptics only on long-press recognition, pick-up and a snap or detent crossing; none per frame of a drag; each honours both
    the app's Haptics switch and Android's touch-feedback setting.

**Wallpaper**
26. The wallpaper page opens from Home's long-press sheet or the Appearance editor; its Done returns to the card it came from
    with the new colours applied.

**Reverse states**
27. Every way in has a way out: add a widget, remove it; make a folder, empty it; hide a page, show it.

## Not resolved (everything marked unverified above)

- Pixel/Samsung swipe-down on the home page (Pixel), pinch overview, double-tap lock, status-bar tap behaviour.
- What iOS does with Home when already on a later page (page 1 jump) and Samsung's end-of-edit gesture.
- Samsung's defaults for new apps and for the swipe-down panel.
- The surface of Launcher3's delete undo (not read), and the haptic on pick-up (not traced).
- `ACTION_MAIN`/`CATEGORY_HOME` wording from the `Intent` reference page.
- Everything from Material Design 3 and from peer-reviewed HCI papers (none retrieved with a DOI).

## Sources

**Android**

- **A1** Predictive back: `https://developer.android.com/guide/navigation/custom-back/predictive-back-gesture`
- **A1b** App shortcuts: `https://developer.android.com/develop/ui/views/launch/shortcuts`
- **A3** App widgets overview: `https://developer.android.com/develop/ui/views/appwidgets/overview`
- **A6** Haptics design principles: `https://developer.android.com/develop/ui/views/haptics/haptics-principles`
- **A7** Add haptic feedback to events: `https://developer.android.com/develop/ui/views/haptics/haptic-feedback`
- **G1** Add apps, shortcuts & widgets to your Home screens (Android Help): `https://support.google.com/android/answer/9450271`; Pixel copy: `https://support.google.com/pixelphone/answer/2781850`
- **G3** Find, open & close apps on Android: `https://support.google.com/android/answer/9079646`
- **G4** Get around on your Android device: `https://support.google.com/android/answer/9079644`

**AOSP Launcher3** (`https://android.googlesource.com/platform/packages/apps/Launcher3/+/refs/heads/main/<path>`, head `2663cf0a414bd603d6a833cb691cea76eb1897b7`)

- **L3 Launcher.java**: `src/com/android/launcher3/Launcher.java` — `onNewIntent` (1639-1690), `getOnBackAnimationCallback` (631-675), `onBackPressed` (2124)
- **L3 LauncherState.java**: `src/com/android/launcher3/LauncherState.java` — `onBackInvoked` (447-452)
- **L3 Workspace.java**: `src/com/android/launcher3/Workspace.java` — `REORDER_TIMEOUT` (249), `beginDragShared` (1678), `onDrop` (2025-2230), drag modes (2693-2710)
- **L3 WorkspaceTouchListener.java**: `src/com/android/launcher3/touch/WorkspaceTouchListener.java` — `maybeShowMenu` (196-215)
- **L3 ItemLongClickListener.java**: `src/com/android/launcher3/touch/ItemLongClickListener.java` — `canStartDrag` (181-196), `beginDrag` (80-100)
- **L3 OptionsPopupView.java**: `src/com/android/launcher3/views/OptionsPopupView.java` — `getOptions` (200-225)
- **L3 Folder.java**: `src/com/android/launcher3/folder/Folder.java` — constants (154-170), outside-tap close (1684-1694)
- **L3 DragController.java**: `src/com/android/launcher3/dragndrop/DragController.java` — `DRAG_VIEW_SCALE_DURATION_MS` (102)
- **L3 DeleteDropTarget.java**: `src/com/android/launcher3/DeleteDropTarget.java` — `onDrop` (119-124)
- **L3 LauncherAnimUtils.java**: `src/com/android/launcher3/LauncherAnimUtils.java` — `SPRING_LOADED_EXIT_DELAY` (39)
- **L3 ActivityAllAppsContainerView.java**: `src/com/android/launcher3/allapps/ActivityAllAppsContainerView.java` — fast scroller (273-276)
- **L3 PopupContainerWithArrow.java**: `src/com/android/launcher3/popup/PopupContainerWithArrow.java` — `showForIcon` (189), `MAX_SHORTCUTS` (437)

**Apple**

- **A2** Move apps and widgets on the iPhone Home Screen: `https://support.apple.com/guide/iphone/move-apps-and-widgets-on-the-home-screen-iphd2fc8ce30/ios`
- **A2b** Move apps and create folders (HT200290): `https://support.apple.com/HT200290`; Organize your apps in folders: `https://support.apple.com/guide/iphone/organize-your-apps-in-folders-iph822ece7dd/27/ios/27`
- **A3b** Find and use your apps in App Library: `https://support.apple.com/guide/iphone/find-and-use-your-apps-in-app-library-iph87abad19a/ios`
- **A4** Learn gestures to navigate iPhone: `https://support.apple.com/guide/iphone/learn-gestures-to-navigate-iphone-iphfdf164cac/ios`
- **A5** Remove or delete apps from iPhone: `https://support.apple.com/guide/iphone/remove-or-delete-apps-iph248b543ca/27/ios/27`
- **A5b** (same pages as A2/A2b for tap-to-open)
- **A6b** How to add and edit widgets on your iPhone: `https://support.apple.com/en-us/118610`
- **H1** HIG, Home Screen quick actions: `https://developer.apple.com/design/human-interface-guidelines/home-screen-quick-actions`
- **H2** HIG, Widgets: `https://developer.apple.com/design/human-interface-guidelines/widgets`
- **H3** HIG, Gestures: `https://developer.apple.com/design/human-interface-guidelines/gestures`
- **H4** HIG, Drag and drop: `https://developer.apple.com/design/human-interface-guidelines/drag-and-drop`

**Samsung**

- **S1** Organize the Home screen on your Galaxy phone or tablet: `https://www.samsung.com/us/support/answer/ANS10001898/`
- **S2** What is One UI Home on Galaxy phones and tablets?: `https://www.samsung.com/us/support/answer/ANS10003678/`

**Usability research**

- **N1** Nielsen Norman Group, Mobile Usability 2nd Research Study: `https://www.nngroup.com/articles/mobile-usability-2nd-study/` ("Swiping is still less discoverable than most other ways of manipulating mobile content, so we recommend including a visible cue when people can swipe"; "don't employ the same swipe gesture to mean different things on different areas of the same screen.")
