# Surface look grid

The spec behind ADR 0017. Decided 2026-10-30 from the review page
(`.lavish/surface-rules-review.html`, gitignored); the developer took every recommended answer:
corners per element and margin wall-wide (D1), six elements (D2), the page beats the element's
everywhere-value (D3), Frost snaps to eight stops with Grain under More (D4), a new grid store with
a one-way migration (D5), all waves on `v2-redesign` with a pong build after the store wave and
after each later wave (D6).

## The grid

Two axes. **Where**: everywhere (the wall) or one page. **What**: all glass or one element.

| | All glass | This element |
|---|---|---|
| Everywhere | `look.<property>` (Base) | `look.<property>.<element>` |
| This page | `look.<property>@<page>` | `look.<property>.<element>@<page>` |

Read order: `element@page` → `all@page` → `element` → Base → the shipped Look. First present key
wins. Absent means follow. Reset at a cell deletes that cell's key.

`<page>` is the page id Edit pages assigns (the same id the `@<page>` suffix carries today). The
Terminal page is one page for all its windows. A Display page is a page like any other; it has no
element of its own, so only its all-glass cells matter.

## Properties

| Property | Key | Range | Base default | Notes |
|---|---|---|---|---|
| Frost | `frost` | stop 0–7 | the Look's stop | one slider moving blur and grain along the `GlassControls` curve; stored as the stop |
| Grain | `grain` | 0–100 % | from the Frost stop | under More; a set Grain holds while Frost moves blur alone |
| Opacity | `opacity` | 0–100 % | 34 | |
| Tint | `tint` | 0–100 % | 0 | the amount of accent mixed in |
| Tint colour | `tint_hue` | primary, secondary, tertiary | primary | the Hue pill |
| Depth | `depth` | 0–100 | the Look's | the refraction bend; per element through the existing uniforms |
| Edge | `edge` | 0–100 | the Look's | the dispersion; per element |
| Corners | `corners` | 0–40 dp, or auto | auto | auto = the Style formula (`resolveAutoCornerRadiusDp`); Docked and Floating keep reading it differently |
| Rim | `rim` | off, hairline | hairline | under More |
| Vibrancy | `vibrancy` | 70–150 % | 100 | Where level only (baked into the shared frame) |
| Margin | `margin` | 0–40 dp | 12 | Where level only (layout) |

Frost stops, as positions on today's 0–100 Glass scale, chosen so that every shipped Look lands on
a stop: 0 (no frost: blur 0, grain 0, Solid), 10, 20 (Clear), 40 (Tint), 55, 75 (Mist), 90, 100.
Blur per stop from the curve `4 + 32·t^1.5` dp: 0, 5, 7, 12, 17, 25, 31, 36. Eight distinct radii;
`WallpaperBlurCache` is sized to hold eight.

The Look stop (Clear, Mist, Tint, Solid, Custom) is not a property: choosing one writes that Look's
numbers into the chip's cell for every property it names, and Custom is what the slider shows when
the cell's numbers match no Look.

Not in the grid: Contrast (the legibility contract, ADR 0014), Text size, the material family
(`surface_material`, glass or frost) and its intensity, the keyboard's key opacity, key corners
and key spacing (the keyboard's own rows, one value each), the keyboard theme, Height, Style,
Design and every size. The keyboard's "Opacity link" switch and its backdrop opacity are retired.

## Elements

| Element | `<element>` | Carries | Today |
|---|---|---|---|
| Status bar | `status` | every property | `SurfaceSlot.STATUS` |
| Dock | `dock` | every property | `SurfaceSlot.DOCK` |
| Keyboard | `keyboard` | every property (Corners is new) | `SurfaceSlot.KEYBOARD` |
| Terminal | `terminal` | every property but Margin (Corners is new, the pane's slab radius) | `SurfaceSlot.CANVAS` |
| Widgets | `widgets` | every property but Margin | the `widgets_on_glass` switch |
| Sheets | `sheets` | every property but Margin | borrows the dock's resolved glass |

Sheets covers the app drawer, folders, anchored menus and popups, the command palette, notices
and the Home float. They share one cell set because they are the launcher's transient surfaces and
a user tunes them as one.

## Storage

A `LookGrid` class in `termux-shared` beside `TermuxAppSharedPreferences`, pure where it can be:

- `key(property, element?, page?)` builds the key; `parse(key)` reads one back (presets and
  launcherctl use both).
- `resolve(property, element, page)` walks the four cells and answers the value with the cell it
  came from (`Cell.ELEMENT_ON_PAGE`, `PAGE`, `ELEMENT`, `BASE`, `SHIPPED`), so the editor can colour
  its dot without a second read.
- `write(property, element?, page?, value)` and `clear(property, element?, page?)`.
- Clamps per property live here, once.

The named getters in `TermuxAppSharedPreferences` (`getAppLauncherDockOpacity`,
`getStatusBarBlurRadius`, …) keep their signatures and read the grid, so the renderer does not
change for the move. `SurfaceSlot` gains `WIDGETS` and `SHEETS` and loses nothing else; the page
API (`PageLook`, `PageLookKeys`, `LookKeys`) collapses into the grid.

Deleted: `KEY_SURFACE_INHERIT_PREFIX` and every flag, `hasSurfaceProperty`'s per-slot exceptions,
`isRetiredSurfaceProperty`, the write-through in `writeSurfaceValue`, `PAGE_WALL`/`PAGE_ALL`
plumbing where the cell replaces it, `KEY_KEYBOARD_OPACITY_SENTINEL_HEALED`,
`KEY_KEYBOARD_GLASS_SLOT_FOLDED`, `KEY_IN_APP_KEYBOARD_OPACITY_LINKED`,
`KEY_IN_APP_KEYBOARD_BACKDROP_OPACITY`, the `KEY_WALLPAPER_ENABLED_*` trio.

## Upgrade

`migrateLookGrid()` runs once, before `adoptShippedSurfaceDefaults()`'s fresh-install test is
consulted (a store holding nothing but `log_level` is still a fresh install). For every
(property, slot) and every page id in the page list:

1. Read the old resolved number through the old resolver (`getSurfaceValue(slot, property, page)`).
2. Base: the old `surface_base_*` number → `look.<property>`.
3. A surface detached on the wall (flag false, own key present) → `look.<property>.<element>`.
4. A page override (`<own key>@<page>` present) → `look.<property>.<element>@<page>`; a page's Base
   override (`surface_base_*@<page>`) → `look.<property>@<page>`.
5. Frost: the old blur and grain pair → the nearest stop; where the pair sits off the curve by more
   than a stop's width, the stop by blur and an explicit `grain` at the same cell.
6. The wall-wide Depth, Edge, Vibrancy, tint hue, rim → Base.
7. The drawer's own corner radius → `look.corners.sheets`; `widgets_on_glass` off → `look.opacity.widgets` 100 and `look.frost.widgets` 0 (solid), on → nothing.
8. Delete every old key and flag it read.

An upgrade test seeds the store from a real preference dump of pong (xml copied once into
`termux-shared/src/test/resources/`) and asserts that every named getter answers the same number
before and after, on every page.

## Renderer

- `GlassSurfaceFactory` builds a `GlassStack.Spec` per element from the grid: tint colour, strength,
  grain, opacity, corner radius, rim per element; Depth and Edge go into the refraction uniforms
  per surface instead of one `mFancierGlassLook`.
- The drawer, folders, `AnchoredMenu`, `CommandPaletteView`, notices and the Home float take the
  Sheets spec; the Card-form widget background takes the Widgets spec.
- Frost's stop is converted to blur dp in one place (`GlassControls`); the cache holds eight radii.
- Vibrancy and the frame-level tint read the Where level for the page being drawn.
- The even-out stage and the ink resolution (ADR 0014) read the same per-element spec and need no
  new rule.

## Editor

- Every card: two chips at the top, **Where it applies** (Everywhere | <page name>) and **What it
  applies to** (All glass | <element>). An element's card opens on its element; the Appearance
  card opens on All glass and has no What chip.
- Rows, in touch order: Look, Frost, Opacity, Tint, Colour, Corners, Depth, Edge; under More:
  Grain, Rim, then the element's own rows (Height, Style, Design, Margin on the dock; Key opacity,
  Key corners, Key spacing on the keyboard; Text size, Split gap, Contrast, Cursor trail, Effect on
  the terminal; Position and the clock on the status bar). Margin's row says it applies everywhere.
- The dot beside a row's name is coloured by the cell its value comes from: element-on-page,
  page, element; no dot for Base.
- Long-press a row's name: Reset this row (clears the cell). The card's ⋮ Reset clears every cell
  the chips name.
- New cards: Widgets (opened by tapping a Card-form widget or from the Home card) and Sheets
  (opened from the drawer's corner tab or the Appearance card's Elements list).
- The Appearance card's Depth, Edge and Vibrancy honour the Where chip.
- Copy per the plain-UI rule: titles are the thing, one-fact descriptions, no mechanism.

## Other writers

Settings → Look rows, the wallpaper policy (`WallpaperApplyHold` and the wallpaper-mode defaults),
`SurfacePresets`, `launcherctl look …`, the setup wizard: each names its cell, Base unless it says
otherwise. Presets carry grid keys; a preset file naming an old key is read through the migration
mapping once and rewritten.

## Waves

| Wave | Delivers | Branch and check |
|---|---|---|
| W0 | this spec, ADR 0017, glossary entries | main checkout, committed |
| W1 | `LookGrid`, the migration, getters on the grid, `WIDGETS`/`SHEETS` slots, tests | worktree; suite green; staging on pong, nothing visibly changes |
| W2 | per-element specs, Sheets and Widgets wired, Frost stops, cache size, Depth/Edge per surface | worktree; perf numbers per `docs/agents/performance.md`; pong |
| W3 | the editor: chips, rows, dots, per-row reset, Sheets and Widgets cards, Looks on element cards | worktree; emulator at 1.3× font scale; pong |
| W4 | Settings, wallpaper policy, presets, launcherctl, wizard; `docs/en`; prerelease note; device checks | worktree; pong |

## Owed

- Pong after W1 (no visible change), W2, W3, W4.
- `docs/en/Look_And_Themes.md` and `Keyboard.md` rewrite (W4).
- GLOSSARY: Cell, Sheets, Widgets (surface), Frost stop; Base and Scope chip updated.
