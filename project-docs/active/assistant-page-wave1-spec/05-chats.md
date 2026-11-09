
05 · Chats list and deleting · Assistant page build spec
Contents01 Page02 Composer03 Model chip04 No model05 Chats list06 Wall entry07 Look
# 05 · Chats list and deleting
Finding, opening and deleting chats from the Assistant page, and the one row added to the On-device AI settings page.
Wave 1 · 412 × 915 dp frames · Termux Launcher v2 design system
 Numbered callouts Size overlay on frames Fit more (60% scale)Frames are 412 × 915 dp at 1 dp = 1 CSS px when scale is off.
05.1 Populated
05.2 Search
05.3 No results
05.4 Empty
05.5 Delete
05.6 Settings
Build notes
## Chats list
Opened from the Chats button in the top bar. The list covers the chat inside the same page surface.
### 05.1Populated
10:56
### Chats
Search chats
Today
Rename photos by the date they were taken18:42Use exiftool. It reads the date the photo was taken and renames each file to match.
Soft-boiled egg08:15About 6 minutes in water that is already boiling, then straight into cold water.
Yesterday
Lease notice period21:03The lease asks for two months' written notice before you move out.
Note about the heater19:30Hi Sam, The heater in the living room stopped working on Monday evening.
This week
Bread starter ratiosMonEqual weights of flour and water, about 50 g each, once a day.
RAM and storageSunRAM holds what the phone is working on right now.
Earlier
Flour to grams28 OctAbout 375 g, using 125 g per cup of all-purpose flour.
05.1a · Chats listDark · Cool wallpaper · Glass on
10:56
### Chats
Search chats
Today
Rename photos by the date they were taken18:42Use exiftool. It reads the date the photo was taken and renames each file to match.
Soft-boiled egg08:15About 6 minutes in water that is already boiling, then straight into cold water.
Yesterday
Lease notice period21:03The lease asks for two months' written notice before you move out.
Note about the heater19:30Hi Sam, The heater in the living room stopped working on Monday evening.
This week
Bread starter ratiosMonEqual weights of flour and water, about 50 g each, once a day.
RAM and storageSunRAM holds what the phone is working on right now.
Earlier
Flour to grams28 OctAbout 375 g, using 125 g per cup of all-purpose flour.
05.1b · Chats list, glass offLight · Warm wallpaper · Glass off (solid)
#### Chats list · 05.1a, 05.1b
| # | Element | Size (dp) | Type | Colour role | 
| 1 | Back | 48, icon 24 | — | onSurface | 
| | Returns to the chat it came from. System back does the same. | 
| 2 | Title | flex | title text-xl 22/28 Regular | onSurface | 
| | "Chats". | 
| 3 | New chat | 48, icon 24 | — | onSurface | 
| | Closes the list and opens an empty chat. | 
| 4 | Search field | 48 high; inset 12 from the pane, 8 below the bar; padding 0 8 0 16; icon 24, gap 12; corner full | body text-base 16/24 Regular | surfaceContainerHigh; hint and icon onSurfaceVariant | 
| | Hint "Search chats". Tap focuses and raises the keyboard; the list filters as you type (debounce 150 ms). | 
| 5 | Group header | 40 high; margin 8 20 0 | labelSm text-md 14/20 Medium | primary | 
| | Today, Yesterday, This week, Earlier. Empty groups are not shown. "This week" = the last 7 days before yesterday. | 
| 6 | Current chat | same row | — | secondaryContainer, onSecondaryContainer | 
| | The chat open on the page. Background only; no accent bar. Content description adds "Current chat". | 
| 7 | Chat row | min 72; padding 12 / 16; margin 0 4; corner 16; title–time gap 12 | title label text-base 16/24 Medium; time meta text-sm 12/16 Regular; preview bodySm text-md 14/20 Regular | title onSurface; time and preview onSurfaceVariant | 
| | Title and preview one line each, ellipsized. Preview = start of the last answer, markdown stripped. Time: hh:mm today and yesterday, weekday this week, d MMM earlier. Tap opens the chat; long-press opens the menu. | GlassThe list lives inside the same pane, so it takes the pane's Base Look glass. Search field, rows and the selected row are opaque tonal fills. 05.1b: Solid material pane (surfaceContainer 77.5%, no blur). Nothing else changes.MotionOpening: the list slides in from the left over the thread inside the pane, 220 ms on the settle curve, with a 170 ms fade; the composer fades out 110 ms. Closing reverses (110 ms fade, 220 ms slide). The pane itself never moves; page swiping is disabled while the list is open.OrderNewest activity first. Chats are kept until deleted; there is no auto-cleanup and no archive.
### 05.2Search results
10:56
### Chats
lease
 Lease notice periodYesterdayThe lease asks for two months' written notice before you move out.
 Note about the heaterYesterday…the heater is covered in the lease under repairs, so the landlord pays.
q1w2e3r4t5y6u7i8o9p0
asdfghjkl
⇧zxcvbnm⌫
?123,.
05.2 · Search resultsLight · Cool wallpaper · Glass on
#### Search results · 05.2
| # | Element | Size (dp) | Type | Colour role | 
| 1 | Search field (typed) | 48; clear button 40 in 48 touch, icon 20 | body text-base 16/24 Regular | text onSurface | 
| | Clear button appears once there is text. Groups are hidden; results are newest first. | 
| 2 | Result row | same as 05.1 | — | match primary, Semibold 600 | 
| | Matches title and message text, case- and accent-insensitive. The preview shows the matching line, cut to show the match with "…" before it if needed. | 
| 3 | Keyboard | — | — | — | 
| | Pane bottom follows the keyboard as on 01.2. | GlassThe list lives inside the same pane, so it takes the pane's Base Look glass. Search field, rows and the selected row are opaque tonal fills.MotionResults replace the list without animation.
### 05.3No results
10:56
### Chats
passport
No chats match "passport"Search looks in titles and messages.
05.3 · No resultsDark · Warm wallpaper · Glass on
#### No results · 05.3
| # | Element | Size (dp) | Type | Colour role | 
| 1 | Empty message | centred; icon 32, 8 below it; padding 0 32; gap 8 | title label text-base 16/24 Medium; line bodySm text-md 14/20 Regular | icon and line onSurfaceVariant; title onSurface | 
| | "No chats match "passport"" / "Search looks in titles and messages." The query is quoted as typed, ellipsized at 24 characters. | GlassThe list lives inside the same pane, so it takes the pane's Base Look glass. Search field, rows and the selected row are opaque tonal fills.
### 05.4No chats yet
10:56
### Chats
No chats yetChats stay on this phone until you delete them.New chat
05.4 · No chats yetLight · Warm wallpaper · Glass on
#### No chats yet · 05.4
| # | Element | Size (dp) | Type | Colour role | 
| 1 | Empty message | as 05.3 | title label text-base 16/24 Medium; line bodySm text-md 14/20 Regular | as 05.3 | 
| | "No chats yet" / "Chats stay on this phone until you delete them." Search field hidden. | 
| 2 | New chat button | 40 high, 48 touch; 16 above | labelSm text-md 14/20 Medium | secondaryContainer, onSecondaryContainer | 
| | Same action as the bar button. | GlassThe list lives inside the same pane, so it takes the pane's Base Look glass. Search field, rows and the selected row are opaque tonal fills.
### 05.5Long-press to delete
10:56
### Chats
Search chats
Today
Rename photos by the date they were taken18:42Use exiftool. It reads the date the photo was taken and renames each file to match.
Soft-boiled egg08:15About 6 minutes in water that is already boiling, then straight into cold water.
Yesterday
Lease notice period21:03The lease asks for two months' written notice before you move out.
Note about the heater19:30Hi Sam, The heater in the living room stopped working on Monday evening.
This week
Bread starter ratiosMonEqual weights of flour and water, about 50 g each, once a day.
RAM and storageSunRAM holds what the phone is working on right now.
Earlier
Flour to grams28 OctAbout 375 g, using 125 g per cup of all-purpose flour.
Delete
05.5a · Long-press: menuDark · Cool wallpaper · Glass off (solid)
10:56
### Chats
Search chats
Today
Rename photos by the date they were taken18:42Use exiftool. It reads the date the photo was taken and renames each file to match.
Soft-boiled egg08:15About 6 minutes in water that is already boiling, then straight into cold water.
Yesterday
Lease notice period21:03The lease asks for two months' written notice before you move out.
Note about the heater19:30Hi Sam, The heater in the living room stopped working on Monday evening.
This week
Bread starter ratiosMonEqual weights of flour and water, about 50 g each, once a day.
RAM and storageSunRAM holds what the phone is working on right now.
Earlier
Flour to grams28 OctAbout 375 g, using 125 g per cup of all-purpose flour.
### Delete this chat?
"Lease notice period" is removed from this phone.
CancelDelete
05.5b · Delete: confirmDark · Cool wallpaper · Glass off (solid)
10:56
### Chats
Search chats
Today
Rename photos by the date they were taken18:42Use exiftool. It reads the date the photo was taken and renames each file to match.
Soft-boiled egg08:15About 6 minutes in water that is already boiling, then straight into cold water.
Yesterday
Note about the heater19:30Hi Sam, The heater in the living room stopped working on Monday evening.
This week
Bread starter ratiosMonEqual weights of flour and water, about 50 g each, once a day.
RAM and storageSunRAM holds what the phone is working on right now.
Earlier
Flour to grams28 OctAbout 375 g, using 125 g per cup of all-purpose flour.
Chat deletedUndo
05.5c · Deleted: snackbarLight · Cool wallpaper · Glass on
#### Delete one chat · 05.5a, 05.5b, 05.5c
| # | Element | Size (dp) | Type | Colour role | 
| 1 | Pressed row | — | — | onSurface 12% overlay | 
| | Held while the menu is open. Haptic: LONG_PRESS. | 
| 2 | Menu | min 160; padding 8 0; item 48; icon 20, gap 12; corner 16 | labelSm text-md 14/20 Medium | surfaceContainer | 
| | One item, "Delete". Anchored to the row's right end. Raised (the only shadowed surface here). | 
| 3 | Confirm dialog | max 312; padding 24; gap 16; corner 28 | title title text-xl 22/28 Regular; body bodySm text-md 14/20 Regular | surfaceContainerHigh; body onSurfaceVariant; scrim black 45% / 32% | 
| | "Delete this chat?" / ""Lease notice period" is removed from this phone." Buttons: Cancel (primary) · Delete (error). | 
| 4 | Snackbar | min 48; side 12; 44 above the bottom; corner 4 | bodySm text-md 14/20 Regular | inverseSurface, inverseOnSurface; action inversePrimary | 
| | "Chat deleted" · Undo, 4 s. The chat is removed from storage when the snackbar ends. Deleting the current chat opens an empty chat behind the list. | Glass05.5a and 05.5b: glass off. 05.5c: glass on. Menu, dialog and snackbar are opaque in both.MotionMenu: 170 ms fade and scale 0.9 → 1 from the anchor. Dialog: M3 default. The deleted row collapses 220 ms on the settle curve; rows below move up with it.
## Settings: Delete all chats
The only change to Settings: one row on the existing On-device AI page.
### 05.6Delete all chats row and confirm
### On-device AI
Model
Chat modelGemma E2B
Speech modelDownloaded
Local APIOn
Chats
 Delete all chats12 chats on this phone
05.6a · On-device AI settings: Delete all chatsDark · Settings activity (opaque, no wallpaper)
### On-device AI
Model
Chat modelGemma E2B
Speech modelDownloaded
Local APIOn
Chats
 Delete all chats12 chats on this phone
### Delete all chats?
All 12 chats are removed from this phone. This can't be undone.
CancelDelete all
05.6b · Delete all chats: confirmLight · warm scheme · Settings activity
#### Settings: Delete all chats · 05.6a, 05.6b
| # | Element | Size (dp) | Type | Colour role | 
| 1 | Category "Chats" | 24 above, 8 below; gutter 16 | labelSm text-md 14/20 Medium | primary | 
| | A new category at the end of the existing On-device AI page. Rows above are existing and unchanged (shown dimmed here only to mark them). | 
| 2 | "Delete all chats" row | min 72; gutter 16; icon 24, gap 16 | title body text-base 16/24 Regular; summary bodySm text-md 14/20 Regular | title error; icon and summary onSurfaceVariant | 
| | Summary "{n} chats on this phone"; "No chats" and disabled (38%) when there are none. Tap opens the confirm. | 
| 3 | Confirm dialog | as 05.5b | — | — | 
| | "Delete all chats?" / "All {n} chats are removed from this phone. This can't be undone." Cancel · Delete all (error). No snackbar, no undo. | GlassSettings is an opaque activity; no glass.MotionStandard settings and dialog motion; nothing new.
## Build notes
Time labels in rows
| Group | Rule | Example | 
| Today | Started or updated since midnight | 18:42 (24 h or 12 h per system) | 
| Yesterday | The day before | 21:03 | 
| This week | The 5 days before yesterday | Mon | 
| Earlier | Anything older | 28 Oct; with year if not this year | 
Placement: the list is a second child of the pane (a FrameLayout holding the chat view and the list view). It covers the bar, thread and composer; it is not a new activity or a bottom sheet, so it keeps the page's glass and stays on the wall.
List: RecyclerView with header and row view types; ListAdapter + DiffUtil so deletes animate. Search is an EditText in a rounded container (not SearchView, to keep the 48 capsule); query against the chat store's full-text index.
Long-press: PopupMenu (Material style) anchored to the row; confirm with MaterialAlertDialogBuilder. Undo holds the delete for the snackbar's 4 s.
Storage: chats are kept until deleted. Removing the Assistant page does not delete them (sheet 06).
Settings: one Preference in a new PreferenceCategory on the existing On-device AI screen. Keep the edit minimal; settings pages are fragile.

