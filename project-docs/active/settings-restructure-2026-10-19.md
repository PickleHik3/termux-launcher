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

## Features and the wave that builds each

| | Feature | Wave |
|---|---|---|
| F1 | Search from every screen, landing on the row; Enter opens the first result | 1 (this branch) |
| F2 | The four transitions | 1 |
| F3 | General › Animation speed, one multiplier for settings, page swipes, palette and floats | 1 |
| F4 | Two panes at 600 dp and up | 1 |
| F5 | Page transition picker with live preview (Slide, Depth, Fade, Carousel, Stack), Home pages only, frame-rate checked on pong with glass | 2 |
| F6 | Gestures table (swipe up/down, double tap, two-finger), privileged routes shown only when available | 2 |
| F7 | Icon scale, label size, two-line labels, label shadow | 2 |
| F8 | Page indicator: dots or off | 2 |
| F9 | Lock Home layout, unlock from the Home sheet | 2 |
| F10 | Edit pages: add, remove, reorder, default page | 2 |
| F11 | Folder density and max size | 2 |
| F12 | Open the only match | 2 |
| F13 | Remember drawer position | 2 |
| F14 | Start the drawer in the lower half | 2 |
| F15 | A–Z bar: left, right or off | 2 |
| F16 | Badges: dot or count | 2 |
| F17 | What's new from the version row | 2 |
| F19 | Restart launcher, shells kept | 2 |

F18 (app language) waits for translated strings. Wave 2 builds one feature per commit, each with
its own pure policy and test, in the order above; its rows take the places the plan's mocks show
(Home's Page transition, Gestures, Page indicator and Lock rows; Dock & drawer's A–Z row;
Notifications' badge style; General's What's new and Restart).

## Rules that keep the settings look

- `SettingsLayoutUtils.applyItemLayout` overwrites every row's layout; a row with its own layout
  joins its exemption list. Wave 1 adds no custom row layout.
- Sub-screen rows carry no icon (the row layout draws none); section rows on the root do.
- Every moved screen owes a before/after screenshot (`project-docs/device-checks.md`, "Settings
  restructure (10-19)").
