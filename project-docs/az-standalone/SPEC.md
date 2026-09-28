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

## State after 2026-09-07

All three phases merged on `dev` (`d034a223`, `e0447df0`, `85e739c6`). Emulator-verified in portrait and
landscape: side bar refuses the expand swipe while the top bar still expands; bare weather value in
the side bar; A–Z row in landscape with the apps rail; strip, breathing ring, label side, launch on
release and dismiss on release-away; Layout page switch, copy, rail-outside-column miniature. Also
checked on pong (2026-09-07): everything above, plus the breath repainting only while a finger is down
(gfxinfo: ~104 frames/s focused, 2 idle, 3 per 2 s after release). Untested anywhere but unit tests: strip
paging past eight matches — no letter on pong has more than eight apps.

## Round 2 (2026-09-07, from device review on pong)

| Item | Rule |
|---|---|
| Strip focus ring follows the icon | The floating strip's focused slot wears the same icon-contour ring the apps row uses (`FocusOutlineRenderer` visual built from the drawable's alpha), not a rounded rectangle. The breath scales that ring. |
| Name | The switch is "Alphabets bar" everywhere (sentence case, like "Extra keys bar"). |
| Alphabets bar edge | New per-place × orientation value `az_bar`: top · bottom · left · right, default bottom. Portrait offers top · bottom only (side values coerce to bottom, like the other rows). It applies only when the bar stands alone (apps row not at the bottom); with a bottom apps row the bar rides under it and the edge is ignored. |
| Layout page | Under the "Alphabets bar" switch, a placement pill (Top/Bottom in portrait, Top/Bottom/Left/Right in landscape) visible only when the bar is on and stands alone. Summary: "Choose which edge shows the alphabets bar." Miniature draws the band on that edge: a top band under the status bar, a side column innermost of the side columns. |
| One surface with the dock | The bar is part of whichever dock layer it sits on. Plank physics compensate the bar exactly as they compensate the pinned-apps layer (keyboard up: the glass is the plank and the letters must ride it). The drawer's dock lift moves the bar and the glass together instead of lifting and fading the letters on their own. |
| Bar on another edge | A top bar is the same horizontal row hosted under the status bar, strip floating below it. A side bar is a vertical column of upright letters, strip stacked beside it. The gesture machinery is untouched: touches are mapped into the canonical bottom-bar frame by one pure edge transform (`AzBarFrame`), and the strip is laid out in that frame and mapped back for drawing. A bar not on the bottom gets its own glass sheet in the dock material and insets the content like the extra-keys column. |

| # | Branch | Delivers | Depends on |
|---|---|---|---|
| 4 | `feat/az-bar-model` | `PlaceLayout.azBarEdge` + `PlaceLayoutStore` key/getter/setter (in `ARRANGEMENT_KEYS`); `PlaceChromePolicy.azBarEdge(layout)` (bottom unless standalone); rename; placement pill + visibility; miniature band on the edge; strings; tests | — |
| 5 | `feat/az-ring-shape` | drawable-based `resolveFocusOutlineVisual` overload sharing the cache; strip carries per-slot visuals; `drawFloatingStrip` draws the contour ring with the breath; test | — |
| 6 | `feat/az-dock-surface` | `DockPlankController` compensates a set of content layers including `apps_bar_az_row`; drawer lift moves glass and letters together; anchor chain of `apps_bar_az_row` sound with extra keys gone; tests | — |
| 7 | `feat/az-bar-edges` | `AzBarFrame` transform; `AzScrubRowView` vertical mode; top/side hosts with dock-material glass and content insets; `azGestureGeometry`/strip in canonical frame; label side; tests | 4, 6 |

## State after round 2 (2026-09-07)

Phases 4–7 merged on `dev` (`c35e5b88`, `cf698605`, `2a761ff9`, `9cb1cf11`) plus the side-column
height fix (`1f2283d7`). Emulator-verified: "Alphabets bar" title and the Position pill (Top/Bottom
in portrait, four edges in landscape, shown only when the bar stands alone); miniature bands on
every edge; icon-contour ring on the strip; a top bar under the status bar with its own glass and a
working downward gesture; left and right columns innermost of the side columns, upright letters,
strip stacked beside them, gesture and dismiss. Unit-test verified only: plank compensation of the
letters and the drawer lift (cannot be provoked over adb without hitting a control on the glass).
Installed on pong but not yet exercised there (screen was off). Open polish: on a side column the
letter wave pushes the active letters into the strip's edge by a few dp.

## Round 3: the minimised index (2026-09-28)

The developer asked for a minimised form of the index: a pull tab on its edge that brings the
letters out while it is dragged.

| Item | Rule |
|---|---|
| Three-way choice | The Layout editor's A–Z pill is **On / Minimised / Off** (values `shown` / `minimised` / `hidden`, so the two-way spellings still read). Minimised is a new per-orientation key `layout.<o>.az_minimised` beside `az_row`; the three-way is the two together, so an install that only ever stored `az_row` (or the legacy global switch) reads exactly as before and nothing is migrated. Off keeps the flag, so a tab put in the Hidden tray comes back a tab. `clear()` and the Discard snapshot (`PlaceArrangeSnapshot`) carry it. The Position pill stays while the index is On or Minimised. |
| Model | `PlaceLayout.azMinimised`, value-equal and kept by `withSlot`. A minimised index is shown but claims no band (`EdgeStackPolicy.claimsBand`): it is in no stack, costs no inset and puts nothing on the dock. `PlaceChromePolicy.azRowShown` is the band only; `azTabShown` the tab; `azIndexShown` either. The tab never rides the apps row, so `AzPreviewTargetPolicy` gives it the floating strip. Minimal mode hides it like every other element. |
| Tab on the screen | `AzPullTabLayer`, last child of `terminal_surface_host` (the canvas). The pill is the design's 48 × 28 dp, radius 10, 6 dp off the edge, with an upright A at the leading end and the six-dot grip at the trailing one; its touch area is 56 × 48 dp, reaching the canvas edge. It stands at the leading end of the index's edge (left, right in RTL rows, top on columns), starting past the corner square (`CornerZones.PANE_SIZE_DP` + 8 dp). Glass: the dock's `dockSurface`, with an opacity floor of 0.85 (`AzTabPolicy.MIN_GLASS_OPACITY`) because it stands over live content. Geometry is `AzTabPolicy`. |
| Gesture | The layer refuses any DOWN off the tab, so the pane gets it untouched. A DOWN on the tab is the tab's at once — no slop, no hold — and since the layer is above the pane wall, `PaneWallLayout.dispatchTouchEvent` never sees the stream: the border hold-drag (whose ±24 dp band the tab sits in) and the corner tab cannot claim it. Every event is handed to an ordinary `AzScrubRowView` in the layer's reveal frame, offset to its resting position, so touch → slide along → scrub is one gesture through the unchanged callback, strip and launch-on-release. The activity skips the terminal plank tilt and the keybind hint's terminal-tap for a stream that began on the tab. |
| Reveal | `AzTabReveal` state machine: tucked → out on the DOWN → returning on UP/CANCEL (after the scrub has had the release and launched) → tucked when the slide comes home; a DOWN on the way back takes it out again. The letters come out as the bar every edge uses (band + chin, 10 dp air, row inset like the dock) and slide from one bar's travel past the canvas edge on a critically damped `Spring` (k 900, d 60); instant under reduced motion. The layer clips to the canvas so they come out from behind its edge. The tab fades as they arrive. The scrub's bar bounds and glass sampling subtract the in-flight slide, so the finger is judged against where the bar will stand. |
| Edges, keyboard, minimal | Row edges (top, bottom) and side columns alike; the letters on a side are the vertical row. The canvas shrinks above the in-app keyboard, so the tab rides above it. Minimal mode hides the tab. The dock's chin/crown is never pushed onto a bottom tab's letters. |
| Miniature | The Codex `alphabets_tab` asset: 48 × 28 units, radius 10, laid over the pane at the leading end of its edge, 8 units in from the pane's edge, as in `miniature_portrait_terminal`. It is a block with a grip, so it lifts and drops on any edge (staying minimised) or into the tray. Legend and spoken description say "minimised". |

Build: one branch, four commits (model, pure policies, the layer and its wiring, the miniature)
plus docs. Unit tests: `AzIndexModeTest` (store migration, three-way round trip, chrome answers,
minimal mode, drop, pill, Discard), `AzTabPolicyTest` (placement, touch target, corner and border
clearance, RTL, reveal box, slide), `AzTabRevealTest`, `PlaceMiniatureTabTest`. Not built or run
by the worker; device checks owed: the tab and slide on every edge, a scrub that starts on the tab
and launches, the border drag and corner tab beside it, keyboard up, landscape columns, RTL.
