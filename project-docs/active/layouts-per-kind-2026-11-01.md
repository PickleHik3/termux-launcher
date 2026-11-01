# One layout per page kind

The spec behind ADR 0020. Decided with the developer in a grilling on 2026-11-01 (eleven
decisions, all in the ADR). Read at v2-wave1 `c4e708087`; paths are under
`app/src/main/java/com/termux/app/`. Line numbers are approximate and must be re-read by the
builder.

## 1. Current state

**Store.** `place/PlaceLayoutStore.java` (about 2,700 lines) keeps flat keys
`layout.<scope><orientation>.<key>` in the default launcher SharedPreferences.

| Scope | Prefix | Worn by |
|---|---|---|
| NORMAL | `""` | Home pages with no layout of their own, the Terminal, the Display |
| HOME | `home.` | Home pages, for the dock and the widget grid |
| MINIMAL | `minimal.` | Terminal and Display while minimal mode is on |
| page | `page<id>.` | one page, behind flag `place.page_layout.<id>`; `setPageLayout` :645 copies NORMAL; `dropPageLayout` :677 |

**Keys per orientation:** placements `status_bar`, `apps_row`, `az_row`, `az_bar`, `extra_keys`;
`<bar>_order`, `<bar>_under_keyboard`, `<bar>_restore_edge`; `keyboard_mode`, `keyboard_form`,
`keyboard_height`, `keyboard_chin`; `widget_columns`, `widget_rows` (always Home scope, `gridKey`);
`dock_height`, `wall_strip_height`; `bar_order`; `status_compact`; `keyboard_float_x/y`.
**Layout-wide:** `dock_design`, `search_field`, `drawer_tile`, `drawer_tile_position`.
**Others:** `minimal`, `home_layout`, `keyboard_shown`, `keyboard_rows`, `keyboard_open`,
`voice_pill_x/y`.

**Cross-layout rules:** `LINKED_KEYS` (`dock_height`, `wall_strip_height`, `keyboard_height`,
`keyboard_chin` shared through NORMAL), `gridKey`, `readKey` (page falls back to Home), the dock
rule (`DressResolver.dockLayoutOf`, `dockVariant`, `dockKey`, `dockReadKey`), `kindShows`,
`holdsOwnVisibility`, `seedIfNeeded`. Wearing: `chrome/DressResolver.layoutOf` ~:246-258.

**Looks:** termux-shared `LookGrid`, prefix `look.`, per kind `kind:<kind>`, per page `@<page>`.

**Migration:** `MIGRATION_VERSION` 16; `migrateFrom` ~:2055-2080; steps 5 to 16 (per-place keys;
fold into `layout.<o>.`; one minimal flag; drop `az_minimised`; `dock_style` to `dock_design`;
Home wears status bar; Prompt line to Tray and search; Taskbar to Tray and drawer tile; drop Home
copies of linked keys; `home_layout` on every install; kind visibility, not on fresh installs;
`home_layout` to page layouts with `place.page_layout.from_home`). Stable 1.2.2 (branch `dev`)
writes version 8; tag v1.3.0-pre.4 writes 14; v2 writes 16. No end-to-end upgrade test exists;
`PlaceLayoutLinkedKeysTest` seeds 8 but covers only linked keys.

**Callers** (about 120 direct sites): `TermuxActivity` 56, `DressResolver` 14,
`PlaceArrangeSnapshot` 11, `LayoutEditorPlan` 10, `PlaceSizePreferences` 9,
`ResolvedArrangement` 8, `SurfaceEditorController` 6, `LayoutEditorController` 6.

**Why now:** the two dock bugs of 2026-11-01 (ADR 0020, context).

## 2. Target state

- Four layouts (`home`, `terminal`, `terminal_minimal`, `display`) and one `sizes` document, in
  a new SharedPreferences file `layouts`.
- A pure `place/layout/Layouts` (immutable): `Layouts.kind(Kind)` returns a `KindLayout`;
  `Layouts.sizes()` returns `Sizes`. `KindLayout.portrait()` / `.landscape()` return
  `OrientationLayout`. All with `with…` copy methods, no setters.
- `LayoutStore` (Android): `load()` once at start, `current()` returns the object,
  `update(Kind, UnaryOperator<KindLayout>)` writes that one document whole and posts one redress
  request. `PlaceLayoutStore` keeps only migration code and the legacy readers step 17 needs.
- `LayoutKind` for a page: `Home` for a widget page, `Terminal` for the Terminal page, `Display`
  for a Display page; replaced by `TerminalMinimal` for the last two while minimal is on.
  `DressResolver.layoutOf` becomes this one function.
- Gone: NORMAL, page scopes, `setPageLayout`, `dropPageLayout`, `place.page_layout.*`,
  `LINKED_KEYS`, `gridKey`, the page-to-Home `readKey` fallback, the dock rule, the `@<page>` look
  tier.

## 3. The JSON shape

`layouts` keys and values. `version` is the document's own version, 1 now.

As built (2026-11-01). Field names, types and units are the flat keys' own, so step 17 copies
rather than translates; the authoritative table is the comment at the head of
`place/layout/LayoutsJson.java`.

```json
// key "home"  (terminal, terminal_minimal and display have the same shape)
{
  "version": 1,
  "dock_design": "peek_glass", "search_field": true, "drawer_tile": false,
  "drawer_tile_position": "end", "keyboard_shown": false, "keyboard_rows": 0,
  "keyboard_open": false,
  "portrait": {
    "status_bar": "top", "apps_row": "bottom", "az_bar": "bottom", "az_row": true,
    "extra_keys": "hidden", "apps_row_order": 2, "extra_keys_under_keyboard": false,
    "apps_row_restore_edge": "bottom", "keyboard_mode": "overlay", "keyboard_form": "docked",
    "bar_order": "…", "status_compact": false,
    "dress.status_bar": true, "dress.apps_row": true, "dress.az_bar": true,
    "dress.extra_keys": false,
    "widget_columns": 4, "widget_rows": 6
  },
  "landscape": { "…": "same keys" }
}
```

```json
// key "sizes"
{
  "version": 1,
  "portrait":  { "keyboard_height": 0.9, "keyboard_chin": 6, "dock_height": 1.25, "wall_strip_height": 44 },
  "landscape": { "keyboard_height": 1.0, "keyboard_chin": 0, "dock_height": 1.1, "wall_strip_height": 40 }
}
```

- Placements are the flat values: an edge or `"hidden"`; `az_row` is a boolean, `az_bar` an edge.
- `dress.<element>` is the kind's visibility (was `place.dress.<kind>.<o>.<element>`);
  Terminal-minimal holds none (its slots carry it).
- `keyboard_height` and `dock_height` are scales (floats); `keyboard_chin` and
  `wall_strip_height` are dp (ints).
- An absent field reads the same shipped default as an absent flat key did (some defaults come
  from global preferences), so shipped documents hold only the visibility.
- `widget_columns` and `widget_rows` appear only in `home`. Unknown fields are kept on rewrite
  and ignored on read. A document that fails to parse is replaced by the shipped default for its
  kind and logged once; the other four are untouched.

Global, outside `layouts`: `keyboard_float_x/y`, `voice_pill_x/y`, `app_launcher_dock_style`.

## 4. Migration step 17

`MIGRATION_VERSION` 17. Steps 9 to 16 stay and run first, so every older install is first
brought to the v16 flat shape. Step 17 reads the flat keys and writes the five documents in one
`apply()`; it does not touch the flat keys.

**Fold rule, per setting, per orientation:**

| Target | 1st | 2nd | 3rd |
|---|---|---|---|
| `home` | the `home.` value | the Start-in Home page's page layout | NORMAL, then shipped default |
| `terminal` | the Terminal page's own page layout | NORMAL | shipped default |
| `terminal_minimal` | the `minimal.` value | the `terminal` result | shipped default |
| `display` | a copy of the `terminal` result | | |
| `sizes` | NORMAL (linked keys) | | shipped default |

Notes. "Start-in Home page" is the page `Start in` names; if it is the Terminal, use the first
Home page. Widget grid keys come from the Home scope only. `status_compact` folds the same
way. Layout-wide values follow the same order per kind.

**Looks:** for each kind, the cells `look.…@<page>` of the Start-in Home page (Home), or the
Terminal page (Terminal) fold into `@kind:<kind>` when that cell is absent there. Display folds
from the Terminal's. Other pages' `@<page>` cells are dropped from reads; they stay in the file
until the old-key cleanup. `look_grid_kind_migrated` is untouched.

**Fresh installs** write the shipped defaults for the five documents and set version 17.

**Not-yet-migrated downgrade:** a store at version 17 opened by v2 pre-releases of 16 is not
supported; they are pre-releases.

## 5. Upgrade-matrix fixtures

Under `app/src/test/resources/layout-upgrade/`, one `.json` per fixture holding the flat prefs,
and one `…-expected.json` holding the five documents. Each is a plain map from key to value with
types, so the test builds an in-memory prefs.

| Fixture | From | What it holds |
|---|---|---|
| `v8-stable-1.2.2` | dev's version 8 | NORMAL keys only, linked sizes, a minimal flag, a dock style |
| `v8-defaults` | version 8, nothing set | proves the shipped defaults |
| `v14-pre4` | v1.3.0-pre.4 | HOME and NORMAL differ; a `home_layout` flag; MINIMAL values |
| `v16-no-page-layouts` | v2 pre-release | HOME, NORMAL, MINIMAL |
| `v16-home1-own-dock` | v2 pre-release | Home 1 with a page layout and its own dock, Home 2 with none (the 2026-11-01 case) |
| `v16-start-in-terminal` | v2 pre-release | Start in names the Terminal; the first Home page is used |
| `v16-page-looks` | v2 pre-release | `look.…@<page>` cells on the Start-in page and on another |

Test class `place/layout/LayoutUpgradeMatrixTest`: each fixture goes through the full
`migrateFrom` chain; the five documents are compared with the expected JSON (field by field, not
by string). Plus `LayoutsJsonTest` (round trip, unknown field kept, bad document replaced),
`LayoutKindResolverTest` (page and minimal flag to kind), `LayoutSizesSharedTest` (a kind change
changes no size).

## 6. What each caller changes to

| Caller | Change |
|---|---|
| `TermuxActivity` (56) | reads `layouts.kind(kindOfFront())` and `layouts.sizes()`; deletes page-layout and NORMAL branches; one `onLayoutsChanged()` redress |
| `chrome/DressResolver` (14) | `layoutOf(page)` returns the kind document; `dockLayoutOf` and the dock keys are deleted |
| `place/PlaceArrangeSnapshot` (11) | snapshots a `Layouts` value instead of reading keys |
| `LayoutEditorPlan` (10) | plan targets `LayoutKind`; titles it ("Home layout"); the page-layout switch row is removed |
| `place/PlaceSizePreferences` (9) | reads and writes `sizes` only |
| `ResolvedArrangement` (8) | takes a `KindLayout` and `Sizes` in its input |
| `SurfaceEditorController` (6), `LayoutEditorController` (6) | edit through `LayoutStore.update`; the kind is the one the editor opened on |
| `launcherctl` and its receiver | `layout get <kind>` and `layout set <kind> FILE` (validate, `update`, redress) |
| Look grid callers | `@<page>` writes become `@kind:<kind>`; the editor's Where chip loses "This page" |

`seedIfNeeded`, `kindShows` and `holdsOwnVisibility` become fields of the shipped default per kind.


**Chosen design (2026-11-01, accepted by the coordinator).** Callers keep `PlaceLayoutStore` as
the typed accessor; the store gives kind semantics. Once step 17 has run, its preferences are a
`LayoutDocumentPrefs` that routes every layout key to the kind's document (no scope = Terminal,
`home.` = Home, `display.` = Display, `minimal.` = Terminal-minimal; the four sizes to `sizes`;
the widget grid to Home; `place.dress.*` and a place's `keyboard_open` to the kind). Its views
are `LayoutVariant.ofKind(kind, minimal)`; `DressResolver.layoutOf` is that lookup. The flat keys
are never written. `LayoutSourceRulesTest` keeps flat key strings, page layouts and the dock rule
out of every other file.

## 7. Acceptance checks

**Unit (the builder's, run in CI):** the four classes in §5; `DressResolverTest` updated so no
test names a page layout; the existing `PlaceLayoutLinkedKeysTest` is replaced by
`LayoutSizesSharedTest`; a source test that fails if `PlaceLayoutStore` is read outside
migration code.

**Pong (the developer, one batch at the end):**

1. Swipe Home 1 to Home 2 and back with a dock that was different on each: nothing snaps; one dock.
2. Open the editor on Home 2, change the dock design: Home 1 and Home 2 both change; the title
   reads "Home layout".
3. Open the editor on the Terminal, then on a Display page: titles "Terminal layout" and
   "Display layout"; a change on one does not move the other.
4. Turn minimal on: the editor on the Terminal reads "Terminal minimal layout"; Home is unchanged.
5. Change the keyboard height on the Terminal; swipe to Home and to a Display page: no resize.
6. Upgrade over a v2 pre.2 install: every page looks as before the upgrade, Display looks like the
   Terminal did.
7. `launcherctl layout get home > h.json`, edit one value, `layout set home h.json`: the change
   shows live; an invalid file is refused with the field named.
8. Downgrade check (to verify, §9 point 1).

## 8. Risks

- **A lost layout.** Folding picks one page's values for Home; a user with different docks per
  Home page keeps one. The matrix test `v16-home1-own-dock` names the expected result.
- **Display diverges.** After the first edit it no longer follows the Terminal. Intended.
- **120 call sites in one branch.** The branch is large; the source test in §7 keeps stragglers
  out. `TermuxActivity` is 35,000 lines, so the builder works from the grep list, not by reading it.
- **Doubled storage and a stale read.** Old flat keys remain; nothing may read them after step
  17. The source test covers it.
- **Whole-document writes.** Two writers in one frame could lose one; `update` runs on the main
  thread only and the store asserts it.
- **Redress.** `Layouts` is immutable, so a reader that caches it past a redress shows stale
  chrome. Readers take `current()` per pass (ADR 0018 §2).

## 8a. One icon size (step 5, added 2026-11-01)

Developer's decisions: one global icon size for the Home grid and every dock design, docked or
floating; four steps S, M, L, XL; one value for both orientations; built on this branch after
`launcherctl layout`.

- Today (scout map at v2-wave1): dock height = `dock_height` scale (0.4–3.0, default 2.18) ×
  37.5 dp (DockLayoutPolicy.BASE_TOOLBAR_HEIGHT_DP); icon scale from two fitted curves
  (Default and Capsule, DockLayoutPolicy.java:64-71), fill ratio in
  AccessoryStackLayoutPolicy:111-113; beads fit icons to slot width (LensBeadGeometry,
  MIN_FIT_SCALE 1.1); Home grid icons `app_launcher_home_icon_scale` 48/54/60 dp
  (HomeIconSize.java:17-20); presets {1.72, 1.95, 2.18, 2.45} exist (DockLayoutPolicy.java:31).
- Target: `sizes` holds `icon_size` (S|M|L|XL) once. Each step maps to one icon dp (pick the four
  from today's Home 48/54/60 and the dock's default so M looks like today's default dock); the
  dock's height = icon dp + the design's own padding/frame (+ wall strip's row). Floating and
  docked share one curve; beads stop fitting to slots. `dock_height` stops being stored.
- Migration (part of step 17): nearest step to the user's Home icon size and dock height (Home
  icon size wins when they disagree); test fixtures assert the step for the phone fixture
  (dock_height 2.994 portrait / 2.18 landscape).
- Editor: the dock height slider and the Home icon size row become one "Icon size" S/M/L/XL
  control; settings pages are fragile, change only that row.
- Acceptance: every design at each step draws the same icon px (unit test over DockSurfaceSpec
  designs × styles); on pong, cycling designs keeps icon size; Home grid icons match the dock.

## 8b. Home in landscape (step 6, added 2026-11-01)

Developer's decisions (ADR 0020 §12): Home rotates; one grid and one arrangement for both
orientations (portrait wins at migration); widgets relayout, never zoom; landscape dock as a side
bar on the long edge. Built on this branch after step 5.

- Today (scout map at v2-wave1): grid per orientation `layout.home.<o>.widget_columns/rows`
  (PlaceLayoutStore ~:1626-1651, gridKey ~:1926); caps WidgetGridCaps (MIN_CELL_DP 55);
  LauncherWidgetRepository keeps the on-screen orientation's records and shelves the other
  (applyOrientation ~:697-745, first turn reflows); wall-size memory grows per orientation
  (WidgetPaneController ~:372-384); BuiltinWidgetView zoom = min(2, height/design) (~:462);
  WidgetSizeOptionsPolicy already reports portrait+landscape dp (~:40-71); no landscape branch in
  DockLayoutPolicy; TermuxActivity has configChanges orientation|screenSize, no lock.
- Launcher3 reference (AOSP main, read 2026-11-01): InvariantDeviceProfile one numRows/numColumns;
  DeviceProfile transposeLayoutWithOrientation / isVerticalBarLayout (hotseat on the right in
  ROTATION_90, left in seascape, padding = hotseatBarSizePx); legacy cell math in landscape;
  updateIconSize shrinks icons to the cell; hideWorkspaceLabelsIfNotEnoughSpace; WidgetSizes
  getWidgetSizesDp over supportedProfiles → OPTION_APPWIDGET_SIZES + min/max union.
- Target: grid count one value in the `home` document (not per orientation); one record set,
  no shelf; built-in widgets get size steps (each widget picks a layout for its cell px; no
  scale transform above 1.0); dock side bar in landscape on phones (smallest width < 600 dp),
  page indicator beside it; labels hidden by the Launcher3 rule; widget size options pushed only
  when changed.
- Migration (step 17): portrait grid + positions win; landscape records and counts dropped.
- Acceptance: unit tests for cell math both orientations (same cells, no reflow), label hiding,
  widget size options (both sizes, min/max union), built-in widget never scales above 1.0; pong:
  rotate Home with the developer's widgets — every widget stays in its cell, no widget grows
  past its cell, dock moves to the side, rotate back restores exactly.

## 9. Open points for the developer

1. **Downgrade guard (settled).** `dev`'s `PlaceLayoutStore.migrateIfNeeded` (dev:895-898)
   returns at once when the stored version is 8 or higher, so 1.2.x leaves a v2 store alone and
   reads the flat keys it knows. Keys v2's steps 9-16 renamed (e.g. `dock_style`) read as unset
   there, as they already do for any v2 downgrade today.
2. **Loose keys (settled 2026-11-01).** `keyboard_shown`, `keyboard_rows` and `keyboard_open` move
   into each kind's document (that kind's rest, ADR 0018 §4); step 17 folds them by the same rule.
   `minimal` stays one global flag.
3. **Terminal-minimal is worn by Display too (settled).** While minimal is on, the Display layout
   is not worn; its own edits show with minimal off. Minimal is one look for every window.
4. **Start-in on the Terminal (settled).** The fold rule then uses the first Home page on the wall.
5. **Terminal keyboard_shown (deviation, 2026-11-01).** The Terminal's and the Display's
   `keyboard_shown` stays the existing global keyboard-off preference; only Home's is in its
   document. No settings page changed.
6. **Callers keep the accessor (chosen design, 2026-11-01).** See §6: `PlaceLayoutStore` stays
   the typed accessor over the documents instead of every caller reading `Layouts` fields.
7. **The page look tier stays for now (2026-11-01).** The Workspace half's look lives in the
   Terminal page's own cells (`detachSurfaceValue(..., terminalPageId())`), so `LookGrid` still
   reads `@<page>` cells and the Where chip keeps "This page" until those writers move to
   `@kind:` cells. Step 17 already folds the Start-in Home page's and the Terminal page's cells
   into `@kind:` cells. Vibrancy and Margin have no kind row; their page cells are not folded.
8. **Home's Style, Corners and Margin.** They are linked across both halves since #84
   (`TermuxAppSharedPreferences#getLayoutStyle(boolean)` ignores the half), so there is no
   Home-only value to fold. Home holds its own layout (`homeHoldsOwnLayout()` is true).
