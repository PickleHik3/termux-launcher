# Settings restructure (2026-10-19)

The in-app settings regrouped by the wall's halves (option A, approved 10-19 from the plan page
`.lavish/settings-restructure/index.html`), plus search from every screen, Material motion between
screens, an animation-speed choice and two panes on wide screens. **No preference key changes:
rows move, values stay.**

The tree lives in code in one table, `SettingsTree` (`app/.../fragments/settings/`): every screen,
its section, the screen that opens it, its title and its XML. Deep links, saved Back stacks, search,
motion and the two-pane list all read it; a new screen goes in there or it is invisible to them.

## The tree

```
Search settings
Your launcher
  Home ............ Layout (editor, Home) · Gestures · Widgets · Pages (wallpaper scrolls, Start in)
  Dock & drawer ... Dock (pinned apps, most-used page) · Drawer (on/off, layout, app search, A–Z lock)
  Terminal ........ Layout (editor, Terminal) · Windows & panes · Status bar · Text & display
                    (font, full screen, cursor, effects) · Clipboard & compatibility · Linux apps
Shared
  Look ............ Appearance editor row · Theme · Icon appearance
  Keyboard ........ as before, plus Look (Appearance editor row, Keyboard theme)
  Notifications ... unchanged
  On-device AI .... unchanged
System
  General ......... Motion (animation speed) · Vibration · Battery · Backup, access and about
                    (Backup & restore, Permissions & services, Diagnostics, About & help, Help)
                    · Home app (Set as default launcher, Show in Recents)
```

Linux apps is the old Display section: its Linux apps page folded in on top (apps, desktop), then
Input, Resolution, Startup and Troubleshooting. It is hidden in a build without the X server, as the
Display row was.

## Where every screen went

| Today | Now | Class |
|---|---|---|
| Root › Start in | Home › Pages | `HomePreferencesFragment` |
| Appearance › Wallpaper, look and layout | Look (top row, opens the Appearance editor) | `TermuxStylePreferencesFragment` |
| Appearance › Terminal font | Terminal › Text & display | `TerminalTextPreferencesFragment` |
| Appearance › Theme, Icon appearance | Look | `TermuxStylePreferencesFragment` |
| Appearance › Wallpaper parallax | Home › Pages, "Wallpaper scrolls with pages" | `HomePreferencesFragment` |
| Appearance › Keyboard look | Keyboard › Look | `KeyboardPreferencesFragment` |
| Apps › Home app: gesture, widgets, widget style | Home | `HomePreferencesFragment` |
| Apps › Home app: Set as default launcher | General › Home app | `GeneralPreferencesFragment` |
| Apps › Home app: pinned apps, drawer switch | Dock & drawer | `LauncherPreferencesFragment` |
| Apps › Dock page (pinned apps ×2, most-used page) | Dock & drawer › Dock (pinned apps once) | `LauncherPreferencesFragment` (old name aliased) |
| Apps › Drawer layout, App search, Double-tap to lock | Dock & drawer › Drawer | unchanged pages |
| Apps › Notifications, App behavior links | removed (copies) | |
| Apps › Help | General | `GeneralPreferencesFragment` |
| Status bar | Terminal › Status bar | `StatusBarPreferencesFragment` |
| Status bar › More › Notifications | removed (copy) | |
| Terminal › Sessions and panes | Terminal › Windows & panes | `TerminalWindowsPreferencesFragment` |
| Terminal › Terminal display | Terminal › Text & display | `TerminalTextPreferencesFragment` |
| Terminal › Clipboard, Compatibility | Terminal › Clipboard & compatibility | `TerminalClipboardPreferencesFragment` |
| Display (section) | Terminal › Linux apps | `X11DisplayPreferencesFragment` |
| Display › Linux apps (page) | Terminal › Linux apps, top groups | old name aliased |
| App behavior | General | `GeneralPreferencesFragment` (old name aliased) |
| Backup & restore, Permissions & services, Diagnostics, About & help (root) | General | unchanged pages |
| Keyboard › Vibration link | General, lands on the keyboard vibration row | |

Old class names stay as thin aliases (`AppBehaviorPreferencesFragment`,
`LauncherDockPreferencesFragment`, `X11DisplayLinuxAppsPreferencesFragment`,
`TerminalStatusPreferencesFragment`), so an Intent from an older build still opens.
`SettingsTree.redirect` maps an old name to the page it shows now, and an old page plus a moved
row's scroll key to the page that holds that row.

**Saved Back stacks.** The stack Settings keeps for 30 minutes now carries `tree_version: 2`. A stack
without it was saved against the old tree: each entry is mapped to today's page, the path to it is
rebuilt from its section's page (old Status bar → Terminal › Status bar), entries for one page
collapse, and a name that no longer exists falls back to the root. A stack saved against today's tree
replays as it was.

## Search (F1)

One search, two doors. The root keeps its box as the first row; every other screen has a search
action in the toolbar that opens the search screen, its placeholder naming the section it came from
("Search Home"). Both search the whole tree (`SettingsSearchIndex`: every screen as itself, every
titled row of its XML), the searched-from section's hits first, and show one list of results with
the path under each title ("Terminal › Text & display"). A result, or the keyboard's search key for
the first one, opens the page through its section (Back walks up the tree), scrolls the row into
view and rings it once (`SettingsRowRing`, an outline in the row's overlay; no idle motion). A box on
top of every screen was the other option; one toolbar action keeps every page's own rows as they
were.

## Motion

Four patterns, each tied to one kind of move, on the settings window only, one-shot, no idle
animation:

| Move | Pattern | When |
|---|---|---|
| Deeper / Back | Shared axis X (30 dp-ish slide + fade) | a row opens a page below it, Back |
| Row to editor | Container transform from the row | Keyboard layouts, layout editor, keyboard theme, extra keys; the Layout and Appearance rows open the launcher's editor with a scale-up from the row |
| Across sections | Fade through | a search result in another section, a deep link, a two-pane section switch |
| Land and point | Scroll the row into view and ring it once | a search result, a link with a scroll key |

Reduce motion (the system's animator scale at 0) or General › Animation speed at Off: an instant
cut. Durations scale with General › Animation speed.

## Animation speed (F3)

General › Motion › Animation speed: Slower (×1.5), Normal, Faster (×0.67), Off (instant). Stored as
`animation_speed` in the app's default preferences and read only through `AnimationSpeed`, when a
motion starts, never per frame:

| Reader | Where | How |
|---|---|---|
| Settings' transitions and row ring | `SettingsMotion` | durations × speed; Off is the instant cut |
| The wall's page and window slide | `PaneWallLayout.startSlide` | the settle spring's frequency ÷ speed |
| The command palette | `TerminalCommandPaletteController.kick` | the springs' clock ÷ speed |
| Floating windows' depth ramp | `Motion.floatDepthMs` | 320 ms × speed |

A spring never divides by zero: Off runs it at 1/20 of its time. The phone's reduce motion still
wins over every choice.

## Two panes (F4)

On a screen whose smallest width is 600 dp (`layout-sw600dp/activity_settings.xml`: tablets
such as the Surface, never a phone turned to landscape) the root list stands on the left (two
fifths) and the open section on the right, with a hairline between. The detail opens on Home; a list
row switches the section (fade through); deeper pages stack in the detail on the shared axis; Back
from a section's own page leaves Settings. The list's search box searches for the detail, so the
toolbar has no search action there. A smallest-width change across 600 dp (a fold, a window resize)
rebuilds the stack for the new shape from the same bookkeeping: the phone gets the root back under
the stack, the wide shape opens Home when the reader was at the root. Phones are unchanged; the
phone layout carries a gone `settings_list` only so a restored fragment always has a container.
The list does not mark the open section yet.

## Features and the wave that builds each

| | Feature | Wave |
|---|---|---|
| F1 | Search from every screen, landing on the row; Enter opens the first result | 1 (this branch) |
| F2 | The four transitions | 1 |
| F3 | General › Animation speed, one multiplier for settings, page swipes, palette and floats | 1 |
| F4 | Two panes at a smallest width of 600 dp | 1 |
| F5 | Page transition picker with live preview (Slide, Depth, Fade, Carousel, Stack), Home pages only, frame-rate checked on pong with glass | 2 (built; frame time owed on pong) |
| F6 | Gestures table (swipe up/down, double tap, two-finger), privileged routes shown only when available | 2 (built) |
| F7 | Icon scale, label size, two-line labels, label shadow | 2 (built) |
| F8 | Page indicator: dots or off | 2 (built) |
| F9 | Lock Home layout, unlock from the Home sheet | 2 (built) |
| F10 | Edit pages: add, remove, reorder, default page | 2 (built) |
| F11 | Folder density and max size | 2 (built) |
| F12 | Open the only match | 3 (built) |
| F13 | Remember drawer position | 3 (built) |
| F14 | Start the drawer in the lower half | 3 (built) |
| F15 | A–Z bar: left, right or off | 3 (built) |
| F16 | Badges: dot or count | 4 (built) |
| F17 | What's new from the version row | 4 (built) |
| F19 | Restart launcher, shells kept | 4 (built) |

F18 (app language) waits for translated strings. Wave 2 builds one feature per commit, each with
its own pure policy and test, in the order above; its rows take the places the plan's mocks show
(Home's Page transition, Gestures, Page indicator and Lock rows; Dock & drawer's A–Z row;
Notifications' badge style; General's What's new and Restart).

## Home, wave 2 (F5–F11)

Every row reads and writes through `HomeSettings` (`app/.../launcher/home/`), in the app's default
preferences; Settings' page store hands it every key it owns. Every default is Home as it was, so
nothing is pinned for existing installs. The launcher pushes them into the pane as it comes to the
front (`TermuxActivity.applyHomeSettings`), never per frame. Settings › Home now reads: Layout ·
Icons and labels · Folders · Gestures · Widgets · Pages (Page transition, Page indicator, Edit
pages, Wallpaper scrolls with pages, Lock Home layout, Start in).

| | Decision | Where |
|---|---|---|
| F5 | All five effects kept. They are view properties on the page's content (translation, scale, alpha, `rotationY`): a frame is a render-node property change, no layout and no bitmap, and glass is not sampled again (it is aimed through the wall page's transform only). Any effect but Slide draws the page's content through a hardware layer from the first frame it moves until it is back at rest; the frame time and whether glass inside a scaled or turned page reads as misaligned are owed on pong. Home turns only: the seam into the Terminal and the rubber band at either end still slide, the wallpaper pans by the content's shift whatever the effect, and widget edit mode turns pages with the slide. The settle scales with Animation speed (Off and reduce motion cut). The preview is an editor screen (container transform from its row) that plays once on open and per pick. | `HomePageTransition`, `WidgetPaneView`, `HomePageTransitionFragment` |
| F6 | Swipe up keeps the old key and its `swipe_up`/`off` values. A swipe down with a palette open or a keyboard to put away does that first. Double tap and two fingers are watched only while set to something, from the free area only. Notifications, Quick settings and Lock screen need the accessibility service or Shizuku (target SDK 28 holds no `EXPAND_STATUS_BAR`); they are listed only while one is there, with a row saying what to turn on. | `HomeGestures`, `HomeGestureActions`, `HomeGesturesRows` |
| F7 | Icon size 80–120 %, labels 10/11/13 sp, two lines only where the cell keeps a labelled icon, shadow on or off; Home icons, folders and an open folder's apps. The dock has its own renderer and is untouched. | `HomeIconStyle`, `HomeIconLabels`, `HomeAppIconMetrics` |
| F8 | Home already drew dots past one page; Off hides them and keeps their band, so no cell resizes. | `HomePageIndicator` |
| F9 | Locked: holds on widgets, icons and folders, the pencil, the +, the picker and page removal are refused with a notice; taps launch; the sheet offers Unlock under Terminal and drops its Add and Edit rows. | `HomeLayoutLock`, `HomeActionsSheetPolicy` |
| F10 | Edit pages is a list dialog over Home, from the sheet and a Settings row (the sheet's pencil row is now Edit widgets). Moves and removals are mappings applied to the records, the shelved other orientation (its cells on a removed page dropped and laid out afresh when it returns), the floats (a float on a removed page moves to the page in its place, shell kept) and the default page, which the Home press and a cold start on Home land on. The automatic empty-page trim hands on its renumbering the same way. A removal stops at a store failure, and refuses a page a widget is still being added to. There is no page cap. Not handled: a named saved session keeps the float pages it was saved with (they are clamped on restore). | `HomePageEdits`, `HomePagesEditor`, `LauncherWidgetRepository.movePage` |
| F11 | Density: Compact five across in 64 × 76 dp, Default four across in 76 × 84 (as before), Comfortable three across in 92 × 104, never wider than the pane. Max size 9, 12, 16 (default) or 20; a folder past a lowered cap keeps its apps. A wall with a folder past 16 apps is written as version 8, so an older build goes read-only on it instead of cutting the folder to 16. Dock folders unchanged. | `HomeFolderDensity`, `HomeFolderPolicy` |

## Dock & drawer and the rest, waves 3 and 4 (F12–F17, F19)

Drawer rows read through `DrawerSettings` (`app/.../launcher/drawer/`), the badge style through
`NotificationBadgeStyle`, both in the app's default preferences by the same page-store route Home's
rows use; every default is today's behaviour, so nothing is pinned. The drawer reads them once per
open (`AppDrawerController.applyDrawerSettings`), the dock's badge look when the launcher comes to
the front (`applyHomeSettings`).

| | Decision | Where |
|---|---|---|
| F12 | Enter already opened the first result on every channel (the drawer's three, the terminal's app search), so that is unchanged. New: App search › Open the only match; with one result and a non-empty query, the drawer opens it 800 ms after the last edit, any further edit restarts the pause. The terminal's inline app search is not changed. | `AppDrawerOnlyMatchPolicy` |
| F13 | Drawer layout › Remember position. The list's first visible position and offset (the page, in the paged layout) is taken as the drawer closes and put back after the reset to the empty query; a close on a search, or the categories layout, starts at the top. Held in memory for the process, not stored. | `AppDrawerReopenPolicy` |
| F14 | Drawer layout › Start in the lower half. The vertical list and its letter strip take a top margin that puts them at the content's midline; it steps aside in landscape, while a query is typed or the keyboard asked for, and when the drawer opens onto the keyboard. The pill stays at the top. Other layouts unchanged. | `AppDrawerLowerStartPolicy` |
| F15 | Drawer layout › A–Z strip: Right (today's, default), Left, Off. Left moves the strip and the list's inset to the left and mirrors the rope's lean and tilt; Off hides the strip and gives the list its width. | `AppDrawerAzSide`, `AppDrawerRopeColumnView.setMirrored` |
| F16 | Notifications › Badge style: Dot (default) or Count. Count is the number of active badgeable notifications for the app (the sum over a folder's apps), `99+` past 99, as a pill on the dot's corner. The dock is the only surface that draws badges today (Home and the drawer draw none), so that is the only place it applies; the dock's swipe preview keeps its dot. | `NotificationBadgeStyle`, `NotificationBadgeFrame` |
| F17 | General › About › What's new (a new row beside Version and build, which keeps opening the build report bug reports rely on). Opens the Markdown report screen with this build's notes: a pre-release build reads the channel's rolling list, a stable build or nightly its `## vX.Y.Z` section, plus a link to the release page (`v`, `nix-v` or `vaj-v` tag). `bundleReleaseNotes` in `app/build.gradle` bundles the newest four releases and the channel list as two text files, about 15 KB before compression. A build ahead of its notes shows the link alone. | `WhatsNewNotes` |
| F19 | General › Home app › Restart launcher, behind a confirm. It owes the launcher a recreate on its next resume (`requestTermuxActivityStylingOnNextResume(…, true)`, the path a new wallpaper's colours take) and leaves Settings so that resume is now. The terminal service shares the launcher's process, so the process is never ended; no live activity means the next launch is fresh. A second tap within four seconds is the same restart. | `LauncherRestartPolicy` |

## Rules that keep the settings look

- `SettingsLayoutUtils.applyItemLayout` overwrites every row's layout; a row with its own layout
  joins its exemption list. Wave 1 adds no custom row layout.
- Sub-screen rows carry no icon (the row layout draws none); section rows on the root do.
- Every moved screen owes a before/after screenshot (`project-docs/device-checks.md`, "Settings
  restructure (10-19)").
