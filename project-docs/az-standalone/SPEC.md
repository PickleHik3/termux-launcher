# Side status bar, standalone A–Z index, Layout page order (2026-09-07)

Three landscape fixes the developer asked for, specified from the code map and an emulator check.

## Decisions

| Item | Rule |
|---|---|
| Side status bar never expands | When a place's status bar edge is LEFT or RIGHT (`PlaceLayout.Edge`), the bar is always compact: no expand swipe, no expanded rest state, in every place. The per-place `status_compact` memory is kept untouched so moving the bar back to top/bottom restores what the user had. Predicate is pure and lives in `StatusBarGesturePolicy` (`expansionAllowed(edge)`). |
| Weather chip in the side bar | Shows the bare number ("23"), no degree glyph and no unit. Horizontal bars keep today's "23°". The invalid value stays "--". |
| A–Z index is independent of the apps row | `PlaceChromePolicy.azRowShown(layout)` is `layout.azRowShown` — no longer ANDed with `appsRowShown`. The row keeps its place in the bottom stack (above the extra keys, below where the apps row would be). Landscape therefore gets the row back whenever the switch is on. `TermuxActivity.isAzRowEnabled()` reads the place policy, not the legacy global. |
| Standalone index gesture | When the A–Z row is on and the apps row is not at the bottom (rail or hidden): sliding across the letters filters as today, and the matching apps appear as a **floating strip of icons** centred above the row on the existing `LauncherAzGestureFxView` overlay. The strip's rect is handed to `AzScrubGesture` as the icon track, so upward lock, icon tracking, edge paging and release-to-launch are the proven machinery unchanged. Sliding onto an icon focuses it; lifting the finger launches it; lifting anywhere else dismisses. No matches: no strip, letter preview only. |
| Focus highlight | The focused icon carries a calm breathing ring (scale/alpha pulse, ~1.6 s period, eased). It animates only while a finger is down and stops on release or cancel — no idle repaint. |
| Label side | Portrait: label above the icon (today's rule). Landscape: label below the icon. Applies to the floating strip and to the existing apps-row preview bubble alike, decided by one pure policy. |
| Layout page switch | "Alphabetical index" is always enabled. Summary: "Show an A–Z index. Slide across it to find and open apps." |
| Miniature order | Bottom rows stack inward from the screen edge: extra keys, A–Z, pinned apps (as today, matches the device). Side columns: the pinned-apps rail is outermost, the extra keys column inside it (the device pads the column in by the rail's width; the miniature had them reversed). The A–Z band shows whenever the switch is on, regardless of the apps row. Legend order reads the way the screen reads. |

## Build plan

| # | Branch | Delivers | Depends on |
|---|---|---|---|
| 1 | `feat/side-bar-compact` | `StatusBarGesturePolicy.expansionAllowed`; `StatusBarSwipeLayout.setExpansionAllowed` ANDed into `formEligible`; `TermuxActivity.isStatusBarCompact()` forced true and `setTopStatusBarCollapsed(false, …)` refused when disallowed, wired from `applyStatusBarEdge`/`refreshTerminalWindowBar`; `WeatherController.formatTemp` bare-number variant used by the vertical bar; tests | — |
| 2 | `feat/az-row-standalone` | `PlaceChromePolicy` decoupling; `isAzRowEnabled` on the policy; `AccessoryStackLayoutPolicy` heights without the apps row; new pure `AzFloatingStripPolicy` (slots, icon size, paging, label side, breathing curve); `LauncherAzGestureFxView` strip + ring + label-below; `TermuxActivity` dispatcher feeds the strip rect as the icon track and launches on release; `SuggestionBarView` exposes the filtered entries/launch without needing the row visible; tests | — |
| 3 | `feat/layout-page-az` | `LayoutPreferencesFragment`: switch always enabled, new summary; `PlaceMiniatureView`: rail-outside-column order, A–Z band independent of apps row, legend order; strings; tests | — |

Each phase: worktree off `dev`, sub-agent builds and commits, orchestrator reviews and merges, APK checked on the emulator in portrait and landscape before the work is called done. Phases 2 and 3 must not edit each other's files (2 owns `place/PlaceChromePolicy`, `launcher/az/*`, `LauncherAzGestureFxView`, `SuggestionBarView`, the A–Z region of `TermuxActivity`; 3 owns `fragments/settings/*`, `res/xml/layout_preferences.xml`; both may add strings).
