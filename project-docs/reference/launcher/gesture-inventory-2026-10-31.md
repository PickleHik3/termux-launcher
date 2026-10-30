# Gesture inventory, 2026-10-31

Rule 11 of `project-docs/active/home-conventions-2026-10-30.md`: every custom gesture has a tap
path (a button, menu item, key or palette action that does the same) and a visible cue (a pill,
handle, hint, dots). This lists each gesture the launcher recognises, where, and which of the two
is missing. Names follow `docs/GLOSSARY.md`; behaviour is in `gestures-and-style.md` beside this file.

**How to read it.** File:line cites were read at `f1abe06e8`. "none" means the search found nothing
and the docs name nothing. "unverified" means the recogniser or the cue was not opened; those rows
are listed as gaps only where the docs also name no tap path or cue. A tap path through the command
palette counts, since the palette is itself reachable by a key (and the optional dock tile,
`SuggestionBarView.java:1036`). Terminal-native touch (tap, scroll, selection) is not custom and is
left out. All paths below are under `app/src/main/java/com/termux/app/` unless they start with a
module name.

## Wall and windows

| Gesture | Recogniser | Setting | Tap path | Visible cue | Docs |
|---|---|---|---|---|---|
| Border drag (sideways, from a window's border; top and bottom plain swipe, sides after a hold) | `wall/PaneWallLayout.java:1856` onTouchEvent, `wall/BorderDrag.java`, `wall/SeamSwipe.java` (bottom band) | Border preference | `window.next` / `window.previous` (`launcherctl/LauncherToolRegistry.java:441-442`), the extra keys' go-to keys, window chips | The border's glow at touch (`gestures-and-style.md`), the drawn border | Panes_Windows_And_Sessions.md:54, Layout_And_Full_Screen.md |
| Body swipe (sideways on a terminal's text) | `terminal/TerminalBodySwipe.java:36,45`, claimed in `PaneWallLayout` | Settings > Home > Gestures > Swipe on the terminal to switch windows (`terminal_body_swipe`) | Same as border drag | none; deliberately the same muscle as the border drag | Panes_Windows_And_Sessions.md:56 |
| Home page swipe and the seam (sideways anywhere on a Home page; past the last page into the Workspace) | `wall/PaneWallLayout.java` (as above); Home pages carry no border | none | Go-to keys, Edit pages, `window.next` | Page dots: unverified | Widgets.md:104, Home_Screen_And_Apps.md:96 |
| Keyboard swipe (up opens, down closes, from the bottom border) | `wall/PaneWallLayout.java` via `BorderDrag`; grabber `wall/BorderGrabber.java:70` | none | The keyboard key (extra keys); Alt up-right on the keyboard hides it (Keyboard.md:47-56) | Grabber pill, `wall/BorderGrabber.java:70` | Keyboard.md:15 |
| Status swipe (down unfolds, up folds, from the top border) | same; grabber `wall/BorderGrabber.java:75` | none | Status bar long-press menu "Expand status bar" / "Compact status bar" (`res/values/strings_surface_popup.xml:7-8`, `surfaces/SurfaceLongPress.java`) | Grabber pill, only while the bar can unfold | Status_Bar.md:21 |
| Status bar drag across the bar | `statusbar/StatusBarSwipeLayout.java:284,296` | none | The same menu item | The bar itself; no extra cue | Status_Bar.md:23 |
| Two-finger status pull | `statusbar/StatusBarSwipeLayout.java:284,296` (two-pointer branch unverified) | `status_swipe_two_fingers` (`SettingsRowIcons.java:230`) | The same menu item | none | Status_Bar.md (unverified) |
| Window chip hold then drag (reorder windows) | `terminal/TerminalWindowBar.java:669,712` | none | none found | The chip lifts (docs); no resting cue | Panes_Windows_And_Sessions.md:62 |
| Corner hold (pane buttons; a hold then drag) | `chrome/CornerHold.java:55-66`, `chrome/CornerHoldArbiter.java`, `terminal/TerminalPaneController.java:5850` | none | The corner tab on tap: unverified | The corner tab / bracket (`chrome/CornerBracket.java`) | Panes_Windows_And_Sessions.md:123 |
| Pane two-finger flick (swap with the pane across the edge) | `terminal/TerminalPaneController.java:5330-5338` (`PaneFlick`) | none | The pane's Move icon and the pane actions in the palette (Panes_Windows_And_Sessions.md:107) | none | Panes_Windows_And_Sessions.md:118 |
| Split divider drag | `terminal/TerminalPaneController.java:6106,6405` (`beginDividerDrag`), `terminal/SplitDividerHit.java` | none | Key-resize actions (Panes_Windows_And_Sessions.md:160) | The divider (`terminal/PaneDividerEdges.java:22`) | Panes_Windows_And_Sessions.md:130 |
| Home float: titlebar drag, bottom-right corner resize | `terminal/HomeFloatPolicy.java` (touch code unverified) | none | Maximise / Restore size button; no tap path to move or size | Titlebar and handle; the resize corner has no cue found | Panes_Windows_And_Sessions.md:178 |

## Terminal

| Gesture | Recogniser | Setting | Tap path | Visible cue | Docs |
|---|---|---|---|---|---|
| Pinch zoom | `terminal-view/src/main/java/com/termux/view/GestureAndScaleRecognizer.java:82-91`, `terminal/TermuxTerminalViewClient.java:310` | none | Increase / Decrease font size actions (Panes_Windows_And_Sessions.md:137) | none | Touch_Links_And_Clipboard.md:21 |
| Hold then drag (mouse button held) | `terminal/TermuxTerminalViewClient.java:832` and the terminal view | none | none | A haptic at each stage | Touch_Links_And_Clipboard.md:13 |

## Home, dock and drawer

| Gesture | Recogniser | Setting | Tap path | Visible cue | Docs |
|---|---|---|---|---|---|
| Drawer pull (down off the apps row; right or left off a side rail) | `launcher/drawer/AppDrawerGestureArbiter.java:42` (`Pull`), `launcher/drawer/AppDrawerPullGeometry.java` | always on (`app_launcher_drawer_enabled` gates it) | `app.open_drawer` action (`terminal/TerminalActionDispatcher.java:213`) and the Home gesture action "Open drawer"; a tap on the dock no longer opens it (`TermuxActivity.java:29866`) | none found | Home_Screen_And_Apps.md:158 |
| Quick-reply swipe (toward the screen's middle off a badged pinned icon) | `launcher/notifications/NotificationSwipePolicy.java:11-13` | `app_launcher_quick_reply_swipe` | none; a tap opens the app | The notification dot on the icon | Home_Screen_And_Apps.md:136 |
| Notification card swipe (sideways dismisses) | `launcher/notifications/SwipeDismissFrame.java:49,75` | none | Hold menu "Dismiss" (Notifications.md:65-66) | none; "Dismissed · Undo" appears after | Notifications.md:62 |
| Home swipe up / down on a Home page's free area | `TermuxActivity.java:25368` (`runHomeSwipe`) | Settings > Home > Gestures (`app_launcher_home_palette_gesture`, `app_launcher_home_gesture_swipe_down`) | The chosen action's own palette entry; the dock palette tile | none | Widgets.md:124 |
| Double tap (Home) | `launcher/home/HomeGestures.java:168` (`isSecondTap`), `TermuxActivity.java:11775` | `app_launcher_home_gesture_double_tap` | as above | none | Home_Screen_And_Apps.md:152 |
| Two-finger swipe up / down (Home) | `launcher/home/HomeGestures.java:181` (`twoFingerDirection`), `TermuxActivity.java:29693` | `app_launcher_home_gesture_two_finger_up` / `_down` | as above | none | Home_Screen_And_Apps.md (unverified) |
| A-Z scrub (slide along the index) | `AzScrubRowView.java:709`, `launcher/az/AzScrubGesture.java` | none | none found | The index itself and its letter bubble: unverified | Home_Screen_And_Apps.md:146 |
| A-Z double tap to lock | `AzScrubRowView.java:119,175` (double-tap timing) | Settings > Home > Gestures > Double-tap to lock | The system lock; no launcher button | none | Home_Screen_And_Apps.md:152 |
| Dock long-press, then drag (reorder, make a folder) | `SuggestionBarView.java:1219` (`onTouch`), pickup after the long-press timeout | none | Edit pinned apps, whose rows carry a drag handle (`launcher/PinnedAppsEditor.java:606`) | The lifted icon | Home_Screen_And_Apps.md:124 |
| Home widget long press (menu, edit, resize) | `launcher/widget/WidgetCellView.java:362,392` | Lock layout blocks it | Settings > Home and the Layout editor: unverified | Resize handles once editing; no resting cue | Widgets.md:94 |
| Hold an empty Home cell (add widgets) | Home page long-press, `surfaces/SurfaceLongPress.java` (unverified) | Lock layout | none found | The empty page's "Long-press to add widgets" text (Widgets.md:19) | Widgets.md:19 |
| Icon carry (drag while carrying, two-finger page) | `launcher/widget/IconCarry.java:26` (touch path unverified) | none | Edit pages and the folder popup: unverified | The carried icon | Home_Screen_And_Apps.md (unverified) |

## Keyboard and extra keys

| Gesture | Recogniser | Setting | Tap path | Visible cue | Docs |
|---|---|---|---|---|---|
| Key corner swipes (letters, layers, Ctrl/Alt legends) | `inapp-keyboard` (`juloo.keyboard2.Keyboard2View`) | layout file | The layer keys | The legend printed in the corner | Keyboard.md:37 |
| Space bar swipes: up palette, up-left/right windows, down-left/right sessions, down layout cycle, slide for the cursor | `inapp-keyboard/src/main/res/xml/bottom_row.xml:18` (`tool:app.command_palette`) | layout file | The same `tool:` actions from the palette | none found | Keyboard.md:47-66, Home_Screen_And_Apps.md:209 |
| Extra keys swipe-up slots (for example New pane's swipe-up) | `termux-shared/src/main/java/com/termux/shared/termux/extrakeys/ExtraKeysView.java` (touch code unverified) | extra keys editor | The key's own tap, and the palette | none found | Panes_Windows_And_Sessions.md:40 |
| Floating keyboard drag, grip resize | `chrome/GrabHandle.java:62` (`Drag`) | keyboard type | The Keyboard key cycles the type | The grab handle pill (`chrome/GrabHandle.java:18`), the corner grip | Keyboard.md:100-110 |

## Editors

| Gesture | Recogniser | Setting | Tap path | Visible cue | Docs |
|---|---|---|---|---|---|
| Layout editor drags (move elements, size handle, drag onto eye-off) | `layouteditor/` (unverified) | none | Position and size controls: unverified | Handles and the restore tray | Keyboard.md:23, Widgets.md:116 |

Not a gesture, left out: `wall/PlankTilt.java` is the visual answer to a border drag, not a recogniser.

## Gaps

Each line is worded as a ticket title; none is opened. "Verify" marks a row whose tap path or cue
was not opened and may turn out to exist.

| # | Gap |
|---|---|
| 1 | The app drawer's pull has no visible cue and no on-screen button |
| 2 | The two-finger status pull has no cue |
| 3 | Home's swipe up, swipe down, double tap and two-finger swipes have no cue and no direct tap path |
| 4 | The notification card swipe has no cue (the Hold menu is its tap path) |
| 5 | The quick-reply swipe has no tap path |
| 6 | The window chip hold-and-drag reorder has no tap path |
| 7 | The pane two-finger flick has no cue |
| 8 | Pinch zoom has no cue |
| 9 | The terminal's hold-then-drag mouse button has no tap path |
| 10 | The A-Z scrub has no tap path |
| 11 | The A-Z double tap to lock has no cue |
| 12 | The Home float's resize corner has no cue and no tap path |
| 13 | Holding an empty Home cell has no tap path (verify against Settings > Home) |
| 14 | The space bar swipes carry no cue (verify: legends on the key) |
| 15 | The extra keys' swipe-up slots carry no cue |
| 16 | The Home widget long press has no resting cue (verify: tap path through the Layout editor) |
| 17 | Verify the corner hold's tap path, the Home page swipe's cue, icon carry and the Layout editor drags |
