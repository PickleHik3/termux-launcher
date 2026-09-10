# Per-place layout — spec v1 (decided 2026-09-06)

Rule: anything that decides *what is on screen and where* is a property of a **place**
(Home/widgets · Terminal · Display = `PaneWallPage`) and, for arrangement, of the **orientation**.
Looks stay shared with per-place overrides. The status bar is never hidden, only moved, so the
wall's paging gesture survives every arrangement. Global settings keep only what is genuinely global.

## Model

| Setting | Scope | Values | Notes |
|---|---|---|---|
| Status bar | place × orientation | top · bottom · left · right | Never hidden. Left/right = vertical bar. Compact and expanded exist in every position (expanded vertical = wider column, clock stacked). Pager gesture follows the bar's axis; the collapse gesture is the perpendicular one. |
| Status bar rest state | place (memory) | compact · expanded | Remembered as the user leaves it. Replaces global `top_pane_clock_collapsed`. |
| Apps row | place × orientation | bottom · left · right · hidden | Today's "Apps bar side" becomes the landscape value. Left/right = today's rail. Alphabets row: on/off, follows the apps row. |
| Extra keys | place × orientation | bottom · left · right · hidden | Today's Display-only column becomes the shared implementation for every place. |
| Keyboard mode | Display place × orientation | resize · overlay | Overlay: keyboard floats over the display, which keeps its size. Default overlay in landscape, resize in portrait. Other places always resize. |
| Keyboard on enter | place (memory) | as left · open · closed | Generalises `x11_keyboard_shown` to every place. |
| Widget grid | home × orientation | columns × rows | Landscape gets its own grid. |
| Surface looks (slot × property) | shared, place override | as today | Editor opened on a place edits that place. Reset long-press and Presets clear every place override. |
| Everything else | global | — | theme, fonts, icons, panes, agents, drawer, search, X server, services… |

**Merge rule.** Two chrome surfaces that touch render as one blended dock, the way apps row +
alphabets + extra keys already do. Status bar at the bottom joins the dock; extra keys and apps bar
on the same edge share one column. Think "edge stacks": one per edge, blending whatever lands on it.

## Storage and the one seam

`PlaceLayoutStore` wraps `TermuxAppSharedPreferences`.
- arrangement keys: `place.<home|terminal|display>.<portrait|landscape>.<key>`
- memory keys: `place.<place>.<key>`
- look overrides: `place.<place>.look.<shared key>`
- a missing scoped key falls back to the shared key (today's globals become the shared layer).

It resolves one immutable `PlaceLayout` for (place, orientation). `buildChromeSpec()`,
`DockLayoutPolicy`, the rail, the extra keys column and the status bar host read *that* — no
per-place branches scattered through `TermuxActivity`.

One-time migration on first read:
- `app_launcher_dock_rail_side` → `place.*.landscape.apps_row` (left|right)
- `x11_extra_keys_side` → `place.display.*.extra_keys`
- `x11_hide_status_bar` → dropped (no hidden state)
- `x11_keyboard_shown` → `place.display.keyboard_open`
- `top_pane_clock_collapsed` → `place.*.status_compact`

## Layout page (Settings root destination)

Place tabs · orientation pill · live miniature of the place · rows: Status bar (Top/Bottom/Left/Right),
Apps row (Bottom/Left/Right/Hidden), Alphabets row (toggle), Extra keys (Bottom/Left/Right/Hidden),
Keyboard on enter (As left/Open/Closed), Display only: Keyboard (Resizes/Overlay), Home only:
Grid columns/rows, "Look of this place" → surface editor for that place. Tapping an element in the
miniature scrolls to its row. Rows use `SegmentedPillPreference`.

## Surface editor per place

- Opened on a place (long-press, Layout page, deep link): edits that place. Header names the place;
  rows show a "follows shared" mark until touched (like today's detached mark).
- Opened from Settings → Look: edits the shared layer; overridden rows carry a note naming the places.
- Reset tap: revert to entry state (unchanged). Reset long-press: shared back to defaults *and* every
  place override cleared. Presets: apply to shared and clear every place override. Custom preset
  captures shared only. Dirtiness compares the scoped snapshot.

## Gestures

Horizontal bar (top/bottom): pages on horizontal swipe, collapses on vertical. Vertical bar
(left/right): pages on vertical swipe (top→bottom = Home→Terminal→Display), collapses on horizontal
swipe toward its edge. Extra keys `wall.*` tools stay.

## Settings root (phase 6)

Layout · Look (theme, shared surfaces, fonts, icons, keyboard look) · Terminal (panes, hints, agents,
lazy mode, full screen) · Status bar (clock, stats, weather, notifications) · Keyboard (IME choice,
layouts, typing, extra keys editor) · Apps (pinned, drawer, search, widgets pane, home app) ·
Linux display (server only) · System & info (unchanged). A page answers one question. Old fragment
names stay as aliases so deep links keep working.

## Build plan

| # | Branch | Delivers | Depends on |
|---|---|---|---|
| 1 | `feat/place-layout-store` | `PlaceLayout` + `PlaceLayoutStore` (resolver, migration, per-place memory); chrome/dock/rail/column/status host read the resolved layout; extra keys and apps row positions on every place; per-place status rest state and keyboard memory; display-only code retired; tests | — |
| 2 | `feat/layout-settings-page` | Layout root destination; old rows removed from Display/Launcher/Keyboard pages; docs | 1 |
| 3 | `feat/place-surface-editor` | scoped look overrides; editor per place; reset/presets clear overrides; Look page edits shared | 1 |
| 4 | `feat/status-bar-positions` | bottom/left/right status bar, compact + expanded, axis gestures, merge rule (edge stacks) | 1, 2 |
| 5 | `feat/display-keyboard-overlay` | overlay keyboard mode on Display; default overlay in landscape | 1, 2 |
| 6 | `feat/settings-root` | root restructure, fragment aliases, search index, docs/en settings map | 2 |

Each phase: worktree off `dev`, sub-agent builds, orchestrator reviews/merges, APK built and checked
on the emulator before dependent phases start. `stash@{0}` ("landscape-agent-wip 2026-08-18: rails,
vertical clock, extrakeys glass") holds an earlier vertical-clock attempt worth reading before phase 4.

## Side queue

- Home place status bar: stats order Weather · RAM · CPU, grouped as one cluster, always centred in
  the row (today: row end, sliding under the clock via `alignStatsUnderClock`). Branch
  `feat/home-status-stats-centered`.

### Landscape polish queue (2026-09-07, from device review on pong)

1. **Side column spans the display's length.** The bar's host lives inside the padded content root,
   so its surface stops at the inside of the system bars (63px/96px) and the row inside it stops
   again (`applyStatusColumnRowGeometry` adds `mLastStatusBarInsetTop`, `statusColumnContentLengthPx`
   subtracts the nav inset — both already excluded by the root). The chips get too little room and
   the surface visibly stops before the display's rounded corner. Surface runs the whole display
   length on a side edge; only the *content* stays inside the system bars; docked squares its outer
   corners, capsule keeps rounded ends. Not an Android limit — the window is already edge-to-edge;
   the only hard stop is the physical rounded corner (`RoundedCorner`, ~10dp).
   *Done in `4ee40dcd`: the content's own centring and the extra keys/rail double-padding.*
2. **No dead padding in landscape** between the bar and the terminal, and between the bar and the
   keyboard surface.
3. **The keyboard must not resize the Home place** — it breaks the widget grid. A widget's own text
   field raises the Android IME (over the place, no resize); the keyboard key in the extra keys bar
   raises the in-app keyboard.
4. **Surface editor never opens in portrait.** Fix, and curate what it offers in landscape: strip
   the rows that cannot apply to a side-standing bar or a column'd extra keys.
   *Partly done in `8bd20769` (merged): the editor's free band was measured from the bar's lower
   edge wherever it stood, so a bottom bar or a side column collapsed the band and everything the
   editor draws parked off the bottom of the screen. `SurfaceEditorScene` derives the band from the
   edge instead, never inverts it, never leaves the host and keeps 120dp, and it now also decides
   which surfaces, rows and handles the arrangement has (no dock target where nothing stands on the
   dock band; no size/apps rows or size grip where the pinned apps are a rail; no chin grip with no
   glass under the last key row).*
   **Closed on the device 2026-09-07:** on pong, portrait, through the terminal's own menu
   (More… → Surface editor), the editor opens, the pill parks in the band and the palette card
   raises with every row. The portrait failure was the same band collapse after all, and the
   keyboard is what caused it there: with the bar along the top and the accessory stack carrying
   dock + keyboard, the room between them is near zero, so the old `Math.max(top, bottom)` parked
   everything on the bottom edge. The 120dp floor is what fixed it.
   Left over: a tap straight on the status column in landscape does not raise its card — the
   palette pill does; worth checking whether the bar's own gesture swallows the tap.

### Landscape polish — state after 2026-09-07

Merged on `dev`: `4ee40dcd` (column content on the bar's centre line, extra keys/rail double
padding), `a7293aa7` (a side column runs the display's length, starts past the cutout),
`9efc31ed` (home place: keyboard floats, IME handed to the place while the in-app keyboard is
down), `8bd20769` (surface editor band + per-arrangement offer). Everything verified on the
emulator, and all four verified on **pong** (Nothing A065, landscape and portrait) after it was
unlocked: column surface 0..1080 against a 141..969 before, all column content on one centre line
(dots included), the bar's inner edge flush with the terminal (was a 145px cutout-wide gap), extra
keys at 47dp with 26px margins (was 38dp with 96/63), and the surface editor opening in portrait.

## Layout page v2 — the page is the editor (decided 2026-09-10)

Supersedes "Layout page" above. Option B (draggable miniature) on option A's skeleton; the surface
editor is untouched. The lavish review page it was agreed on is gone with its worktree; this section is the record.

**Page.** Place pill only — no caption, no orientation pill, no legend. Two `PlaceMiniatureView`s
side by side, portrait and landscape, each live for its orientation. Below them one compact row per
element — Status bar · Pinned apps · A–Z index · Extra keys · Keyboard (+ Widget grid on Home) —
showing "portrait · landscape" values with a chevron; tap opens a chooser sheet holding the existing
`SegmentedPillPreference` pills, one pill row per orientation (per-place settings such as keyboard on
enter/type show one row). No "Look of this place" row (dropped 2026-09-10: the per-place look is edited from the
long-press menu's Surface editor). Tapping a bar in a miniature opens its
chooser. New custom rows go on the `SettingsLayoutUtils` exemption list.

**Drag.** Every bar (status bar, apps row, A–Z, extra keys) carries a visible grip. Touch-down on the
grip lifts the bar at once (no long-press) and the miniature asks its scroll parent not to
intercept. Legal slots for that bar and orientation appear as dashed outlines at the edges (model
table above: status bar top/bottom, + left/right in landscape; apps row and extra keys bottom or
hidden, + left/right in landscape; A–Z only hides while it rides the apps row, gets edges when the
apps row is hidden). A tray under the phone appears for bars that may hide; the status bar never
hides and gets none. Release over a slot or the tray writes `PlaceLayoutStore` for (place,
orientation) immediately, like today's pills — no Done; release elsewhere springs back. Both
miniatures and the rows redraw from the store. A pure `MiniatureDragPolicy` owns legal slots and
hit-testing, unit-tested like `DockLayoutPolicy`.

| # | Branch | Delivers | Depends on | Gate |
|---|---|---|---|---|
| 1 | `feat/layout-restructure` | twin miniatures in `LayoutOverviewPreference`; caption, orientation pill and legend removed; compact rows + chooser sheets (Home grid, Display keyboard-mode folded in); tap on a bar opens its chooser | — | Robolectric: each chooser writes the right scoped key and rows re-read it; Display tab still hidden without X11; emulator screenshots at default and narrow width |
| 2 | `feat/layout-drag` | grips, slots, trays, spring-back; `MiniatureDragPolicy` + tests; scroll interception; content descriptions so TalkBack reaches every value via the rows | 1 | policy tests; on pong drag every bar in both phones, hide/unhide; the list never scrolls during a drag; judge motion on pong |

Out of scope: the surface editor, `PlaceLayoutStore` keys, launcher behaviour.

## Arranging inside the surface editor (phase 3, decided 2026-09-10)

The Layout page arranges a place from a picture of it; the surface editor arranges it on the real
screen. Same keys, same writes, no Done: a pick lands in `PlaceLayoutStore` for the edited place and
the orientation on screen, and the chrome re-lays out under the open editor.

**The Place section.** Every surface's card leads with a section titled "Place", above the look
rows, holding that surface's placement controls for the current orientation only — the value sets
and labels the Layout page's chooser uses (`LayoutChooserModel`'s rules, mirrored in
`PlaceArrangeModel`, held against it by a test). Which surface carries which element:

| Card | Place section |
|---|---|
| Status bar | its edge — Top/Bottom, plus Left/Right in landscape; never hidden |
| Dock | the bars standing on the dock band: pinned apps, A–Z index, extra keys |
| Terminal / canvas | the bars that are *not* on the dock band — a rail, a column, an edge of their own, or away — plus the widget grid's columns and rows on Home |
| Keyboard | Type (Docked/Floating/Split), On enter (As left/Open/Closed), and Keyboard mode (Resizes/Floats over) on the Display place |

A bar off the dock band has no glass surface and therefore no card of its own, so the canvas — the
one surface every place always has — is where it is reachable. That is the way back for a hidden
bar, and the reason no arrangement is a one-way door. The row-order rule holds: a row the state
makes inert is dropped, not drawn dead.

**Hold to move.** At rest (outlines plus the pill), a long press on a bar's outline lifts it: the
outline follows the finger as a ghost, every edge that bar may legally stand on in this orientation
is drawn as a dashed slot, and a bar that may hide gets a tray captioned "Drop here to hide" in the
free room. The dock's outline lifts whichever of its rows the finger went down on. Release over a
slot or the tray writes as a pick does; release anywhere else springs the ghost back and writes
nothing. A tap still opens the card. The status bar never hides, so lifting it shows no tray; the
editor's own capture layer already claims the touch, so the bar's paging gesture never sees it.
First entry after this ships shows "Hold a bar to move it" once.

**Commit semantics are unchanged.** Only ✓ commits, ✕ puts the card away, Back from the resting
state routes through the unsaved-changes dialog. The entry snapshot now carries every place's
arrangement (both orientations) as well as its look, so Back → Discard and the ↺ tap put the
arrangement back too, and dirtiness notices a moved bar. Restoring writes the values back through
the store's setters, so a key that was resolving from the shared layer is materialised at the value
it was resolving to — the same answer, spelled out.

**Rules.** A pure `PlaceArrangePolicy` (`com.termux.app.place`) owns them, the way
`MiniatureDragPolicy` owns the page's: status bar top or bottom, plus left/right in landscape, never
hidden; pinned apps and extra keys bottom or away, plus left/right in landscape; the A–Z index can
always be put away, and picks an edge of its own only while the pinned apps are off the dock band
(`PlaceChromePolicy.azIndexStandsAlone`). It also hit-tests a point against the slot rectangles,
nearest centre first, so the two slots that meet at a corner resolve.

**Rotation.** The activity handles `orientation` itself, so the editor is not torn down mid-edit:
the open card, the entry snapshot and dirtiness carry across, and the Place section re-reads for the
new orientation on the layout pass that follows.

| # | Branch | Delivers | Depends on | Gate |
|---|---|---|---|---|
| 3 | `feat/editor-arrange` | Place section on every card; live write-through and re-layout; hold-to-move with slots, tray and spring-back; `PlaceArrangePolicy` + `PlaceArrangeModel` + arrangement in the entry snapshot | Layout page v2 phase 1 | `PlaceArrangePolicyTest` and the editor's place tests green in `:app:testDebugUnitTest`; `:app:assembleDebug`; on pong: change an edge from the card, hold-and-move the extra keys, hide via the tray, rotate with a card open, Back → revert, then ✓ |
