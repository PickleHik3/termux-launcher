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

```json
// key "home"  (terminal, terminal_minimal and display have the same shape)
{
  "version": 1,
  "dock_design": "glass",
  "search_field": true,
  "drawer_tile": "tray",
  "drawer_tile_position": "end",
  "status_compact": false,
  "keyboard_mode": "transient",
  "keyboard_form": "docked",
  "portrait": {
    "placements": { "status_bar": "top", "apps_row": "dock", "az_row": "off",
                    "az_bar": "off", "extra_keys": "above_keyboard" },
    "bars": { "order": ["apps_row", "extra_keys"],
              "under_keyboard": ["extra_keys"], "restore_edge": {} },
    "widget_columns": 4, "widget_rows": 6
  },
  "landscape": { "…": "same keys" }
}
```

```json
// key "sizes"
{
  "version": 1,
  "portrait":  { "keyboard_height": 0.42, "keyboard_chin": 0, "dock_height": 64, "wall_strip_height": 40 },
  "landscape": { "keyboard_height": 0.55, "keyboard_chin": 0, "dock_height": 56, "wall_strip_height": 36 }
}
```

The values above are examples; the builder takes names, types and units from the existing keys
and writes them in the table at `LayoutsJson`'s head. `widget_columns` and `widget_rows` appear
only in `home`. `keyboard_shown`, `keyboard_rows`, `keyboard_open` and `minimal` stay where they
are unless the builder finds one is per layout (see §9, point 2). Unknown fields are kept on
rewrite and ignored on read. A document that fails to parse is replaced by the shipped default
for its kind and logged once; the other four are untouched.

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

## 9. Open points for the developer

1. **Downgrade guard (settled).** `dev`'s `PlaceLayoutStore.migrateIfNeeded` (dev:895-898)
   returns at once when the stored version is 8 or higher, so 1.2.x leaves a v2 store alone and
   reads the flat keys it knows. Keys v2's steps 9-16 renamed (e.g. `dock_style`) read as unset
   there, as they already do for any v2 downgrade today.
2. **Where the loose keys go.** `keyboard_shown`, `keyboard_rows`, `keyboard_open` and `minimal`
   are not named in decisions 5 or 6. The spec keeps them where they are (per page kind in the
   keyboard's own store, and one minimal flag); say if they should move into the kind documents.
3. **Terminal-minimal is worn by Display too.** While minimal is on, the Display layout is not
   worn, so the Display's own edits show only when minimal is off. This follows decision 1.
4. **Start-in on the Terminal.** The fold rule names the Start-in Home page; when Start in is the
   Terminal this spec uses the first Home page.
