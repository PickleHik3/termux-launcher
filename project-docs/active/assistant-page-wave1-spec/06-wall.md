
06 · Wall entry · Assistant page build spec
Contents01 Page02 Composer03 Model chip04 No model05 Chats list06 Wall entry07 Look
# 06 · Wall entry
How the Assistant page gets onto the wall, how it is moved and removed, and how it appears in the app drawer and the dock.
Wave 1 · 412 × 915 dp frames · Termux Launcher v2 design system
 Numbered callouts Size overlay on frames Fit more (60% scale)Frames are 412 × 915 dp at 1 dp = 1 CSS px when scale is off.
06.1 Home and dots
06.2 Add a page
06.3 Landed
06.4 Edit pages
06.5 Drawer
06.6 Dock
Build notes
## Wall entry
The Assistant page is one kind of wall page: at most one, added only when the person asks. It sits after Home 1 when added, and can be moved or removed like any page. Removing it keeps the chats.
### 06.1Home 1 and the page dots
10:56
10:56
Thursday, 5 November
Settings
Notes
Downloads
06.1 · Home 1 with the Assistant page on the wallDark · Cool wallpaper · Glass on
#### Page dots and Home · 06.1
| # | Element | Size (dp) | Type | Colour role | 
| 1 | Page dots | row 20; dot 6, current 8; gap 8 | — | white 50% / 100% | 
| | Three dots: Home 1 · Assistant · Terminal. The Assistant page is an ordinary dot, no special shape or colour. Above the dock on Home; under the status pill on the Assistant page. | 
| 2 | Dock | tray 36 high, corner 20; icons 48 overhanging | — | Glass tray, Base Look | 
| | Unchanged. The Assistant is never added here on its own (see 06.6). | GlassDock tray and status pill follow the Base Look. Home icons are themed: primaryContainer disc with an onPrimaryContainer glyph.MotionSwiping between pages settles on the page spring (ω 18/s, critically damped). The current dot changes when the page passes halfway.
### 06.2Add a page
Opened from the Home actions sheet. The Assistant row is disabled when the page already exists.
10:56
10:56
Thursday, 5 November
Settings
Notes
Downloads
### Add a page
 Home pageWidgets and app icons
 AssistantOne chat for questions, files and voice notes
06.2a · Add a pageDark · Warm wallpaper · Glass on
10:56
10:56
Thursday, 5 November
Settings
Notes
Downloads
### Add a page
 Home pageWidgets and app icons
 AssistantAlready on your wall
06.2b · Add a page, Assistant already on the wallLight · Cool wallpaper · Glass on
#### Add a page · 06.2a, 06.2b
| # | Element | Size (dp) | Type | Colour role | 
| 1 | Sheet title | margin 0 8 8 | title text-xl 22/28 Regular | onSurface on surfaceContainerLow | 
| | "Add a page". Opened from the Home actions sheet row "Add a page". | 
| 2 | Existing row | min 72; tile 44, corner 14; gap 16 | label text-base 16/24 Medium; sub bodySm text-md 14/20 Regular | tile surfaceContainerHighest | 
| | Existing page kind, unchanged. Rows are separated by a 1 outlineVariant hairline. | 
| 3 | Assistant row | min 72; tile 44 | title "Assistant" label text-base 16/24 Medium; sub bodySm text-md 14/20 Regular | tile secondaryContainer / onSecondaryContainer; sub onSurfaceVariant | 
| | Sub: "One chat for questions, files and voice notes". Tap adds the page after Home 1, closes the sheet and lands on it (06.3). In 06.2b the page already exists: row disabled, tile and title at 38%, sub reads "Already on your wall" at full strength so the reason stays readable. | GlassThe sheet is opaque surfaceContainerLow, corners 28 at the top, raised (one of the two raised surfaces). Scrim scrim 45% dark / 32% light.MotionStandard BottomSheetDialog open and drag-to-close. Closing after a tap: 110 ms fade, then the page spring carries the wall to the new page.
### 06.3Landed on the new page
Every way in when there is no Assistant page adds it after Home 1 and lands there.
10:56
### New chat
 E2B
## What can I help with?
Ask anything
Assistant page addedUndo
06.3 · Landed on the new pageLight · Warm wallpaper · Glass off (solid)
#### Landed on the new page · 06.3
| # | Element | Size (dp) | Type | Colour role | 
| 1 | Page dots | — | — | — | 
| | Second dot current. The dot appears as the page is inserted. | 
| 2 | Snackbar | min 48; corner 4; side 12; 8 above the composer | bodySm text-md 14/20 Regular | inverseSurface, inverseOnSurface | 
| | "Assistant page added". Shown for 4 s. Every way in adds the page when there is none: the Add a page row, the drawer entry, a pinned dock icon, a share into the Assistant. | 
| 3 | Undo | 40 high in a 48 touch area | labelSm text-md 14/20 Medium | inversePrimary | 
| | Removes the page again and settles back to the page the person came from. Any chat already started is kept. | GlassGlass off here: page surface Solid, surfaceContainer 77.5%. The snackbar is opaque and has the M3 elevation; it is not glass.MotionSnackbar in: 170 ms fade + 8 rise on the settle curve; out: 110 ms. The page appears by the wall settling to it on the page spring, not by a separate animation.
### 06.4Edit pages
10:56
10:56
Thursday, 5 November
Settings
Notes
Downloads
### Edit pages
Home 1
Assistant
Terminal
Drag to change the order. Removing the Assistant page keeps your chats.
06.4a · Edit pagesDark · Cool wallpaper · Glass off (solid)
10:56
10:56
Thursday, 5 November
Settings
Notes
Downloads
### Edit pages
Home 1
Terminal
Drag to change the order. Removing the Assistant page keeps your chats.
Assistant page removed. Your chats are kept.Undo
06.4b · Assistant page removedLight · Cool wallpaper · Glass on
#### Edit pages · 06.4a, 06.4b
| # | Element | Size (dp) | Type | Colour role | 
| 1 | Assistant row | min 64; handle 48; tile 44; remove 48 | label text-base 16/24 Medium | tile surfaceContainerHighest; lifted row surfaceContainerHigh | 
| | Shown lifted mid-drag in 06.4a. It can sit anywhere in the order; the list is the existing Edit pages list with one more kind of row. | 
| 2 | Remove | 48 touch, icon 22 | — | onSurfaceVariant | 
| | Removes the page at once, no confirm: the chats are kept and Undo is offered (06.4b). The note under the list says so before anyone taps. | Glass06.4a shows the dock with glass off (Solid); the sheet is opaque in both.MotionDrag: the row lifts with the elevation shadow and follows the finger; others shift with 220 ms on the settle curve. Remove: row height collapses over 220 ms.Snackbar 06.4b"Assistant page removed. Your chats are kept." with Undo; same metrics as 06.3, 8 above the bottom inset. If the Assistant page was current, the wall settles on Home 1.
### 06.5App drawer entry
10:56
Search apps
Assistant
Browser
Downloads
Files
Messages
Notes
Recorder
Settings
Terminal
06.5a · App drawerDark · Warm wallpaper · Glass on
10:56
Search apps
Assistant
Browser
Downloads
Files
Messages
Notes
Recorder
Settings
Terminal
Pin to dock
06.5b · Long-press: Pin to dockLight · Warm wallpaper · Glass off (solid)
#### App drawer · 06.5a, 06.5b
| # | Element | Size (dp) | Type | Colour role | 
| 1 | Assistant entry | icon 54, glyph 26; label one line, max 84 wide | label meta text-sm 12/16 Regular | disc primaryContainer, glyph onPrimaryContainer; label onSurface | 
| | Listed like an app, in alphabetical order, label "Assistant". The icon is the speech-bubble glyph from the icon family, always themed. Tap: lands on the Assistant page; adds it after Home 1 first if there is none (06.3). | 
| 2 | Long-press menu | item 48; padding 0 16 0 12; icon 20; corner 16 | labelSm text-md 14/20 Medium | surfaceContainer; icon onSurfaceVariant | 
| | One item, "Pin to dock". When already pinned it reads "Unpin from dock". No App info: the entry is not a separate app. | Glass06.5a: drawer pane is glass (Base Look). 06.5b: Solid. Icon discs are opaque in both.MotionLong-press: icon scales to 0.92 (glass press) and the menu fades in 170 ms. Tapping the entry closes the drawer and settles the wall on the page spring.
### 06.6Dock with Assistant pinned
10:56
10:56
Thursday, 5 November
Settings
Notes
Downloads
06.6 · Dock with Assistant pinnedDark · Warm wallpaper · Glass off (solid)
#### Dock with Assistant pinned · 06.6
| # | Element | Size (dp) | Type | Colour role | 
| 1 | Pinned Assistant | icon 48 in the tray; ring 2 shown here only to mark it | — | disc primaryContainer, glyph onPrimaryContainer | 
| | Appended at the end of the dock after "Pin to dock". The ring is a callout for this sheet, not part of the build. Tap: same as the drawer entry. Long-press offers "Unpin from dock". | GlassDock tray with glass off: Solid material, 77.5%, no blur.MotionThe new icon fades in 170 ms; other icons shift with 220 ms on the settle curve.
Ways in
| From | Page exists | No page yet | 
| Add a page row | Row disabled: "Already on your wall" | Add after Home 1, land, snackbar with Undo | 
| App drawer entry | Settle on the page | Add after Home 1, land, snackbar with Undo | 
| Dock icon (if pinned) | Settle on the page | Add after Home 1, land, snackbar with Undo | 
| Edit pages | Reorder or remove | — | 
## Build notes
Page kind: add ASSISTANT to the wall page kinds with a max count of 1. The wall model inserts it at index (Home 1 + 1) on every way in and returns that index so the caller can settle to it.
Add a page: the existing BottomSheetDialog; one new row. Disabled state uses isEnabled=false and keeps the subtitle at full alpha.
Edit pages: the existing list; reorder through ItemTouchHelper. Removing the Assistant page deletes only the page entry, never the chat store.
Drawer and dock: an internal entry in the launcher's own drawer and dock adapters (not a LauncherApps activity), with a stable id so it can be pinned and sorted alphabetically with apps. It is never auto-added to the dock.
Snackbar: Material Snackbar anchored to the composer (setAnchorView) on the Assistant page, to the bottom inset on Home. Duration 4 s.

