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
