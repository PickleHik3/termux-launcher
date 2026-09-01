# Unexpected-Keyboard source snapshot

This module vendors and adapts the keyboard core from
[Unexpected-Keyboard](https://github.com/Julow/Unexpected-Keyboard), licensed
under GPL-3.0.

- Upstream commit: `38836e440d8ca779d572b52601c6b2ad10f3bb7f`
- Snapshot purpose: render the keyboard as an ordinary app-owned Android
  `View`; this module is not an Android input method.
- Generated source: `ComposeKeyData.java` is the verbatim output present at
  that commit. Its upstream generator is `srcs/compose/compile.py`.

## Copied paths

- Selected core files from `srcs/juloo.keyboard2/`: `ComposeKey.java`,
  `ComposeKeyData.java`, `Gesture.java`, `KeyModifier.java`, `KeyValue.java`,
  `KeyValueParser.java`, `Keyboard2View.java`, `KeyboardData.java`,
  `LayoutModifier.java`, `Logs.java`, `Modmap.java`, `Pointers.java`,
  `Theme.java`, `Utils.java`, and `VibratorCompat.java`. `TapGeometry.java` is
  a local addition with no upstream counterpart.
- All 90 XML layouts from `srcs/layouts/`, copied as ordinary resources under
  `src/main/res/xml/`.
- `res/xml/bottom_row.xml`, `number_row.xml`, `number_row_no_symbols.xml`,
  `numeric.xml`, `numeric_landscape.xml`, `numpad.xml`, and `greekmath.xml`.
- The keyboard declarations and Light/Dark/Black values derived from
  `res/values/themes.xml`, and keyboard dimensions derived from
  `res/values/values.xml`.
- Generated `assets/special_font.ttf`.

## Deliberate removals

The snapshot excludes the input-method service, editor connection/event
handler, settings and launcher activities, preferences and migrations,
dictionaries and native `cdict`, suggestions/candidates, autocapitalisation,
emoji, clipboard history, voice switching, direct-boot state, fold/window
tracking, layout editor UI, numeric-editor inference,
split/landscape modifiers, panes, and their resources. It also excludes
`res/layout/keyboard.xml`, `res/xml/split_middle_column.xml`, and
settings/method resources.

## Generated layout catalogue

`res/values/layouts.xml` lists every named layout in `res/xml` — its id, its
display name, and its resource id — for the launcher's layout picker and its
hot-swap ring. It is generated, not hand-written:

```sh
python3 inapp-keyboard/tools/gen_layouts.py
```

`tools/gen_layouts.py` is adapted from upstream's `gen_layouts.py`, which reads
`srcs/layouts` and emits `pref_layout_*`. Ours reads the copied resources and
emits `inapp_layout_values`, `inapp_layout_entries` and `inapp_layout_ids`, with
no `system` or `custom` pseudo entries: the launcher owns those, and its own
"launcher layout" entry stands for `~/.termux/keyboard/layout.xml`. Rerun it
after adding, removing, or renaming a layout — `LauncherKeyboardLayoutsTest`
fails when the catalogue has gone stale.

## Local adaptations

- `Config` is immutable, instance-owned, constructor-injected, and contains no
  preferences or process-wide handler. Its fixed defaults retain upstream's
  DPI-scaled swipe thresholds and default character-size multiplier.
  `EditorConfig` is a terminal-only stub with selection mode removed.
- `Keyboard2View` has an explicit `(Context, Config, Theme.Palette)`
  constructor, measures only from parent specs, exposes main-thread
  mutation/reset APIs, cancels callbacks on detach, and prevents parent
  interception during active touches. Unlike upstream's IME window (which gets
  automatic exclusion), the activity-embedded view registers its own
  view-local `setSystemGestureExclusionRects` in `onLayout` (SDK >= 29) so
  edge-column swipes are not recognized as system Back gestures. The host may
  also apply a live height scale; measurement multiplies `Config.rowHeightPx`
  and derives the height cap from `maxKeyboardHeightFraction` by the same factor
  so enlarged keyboards are not silently limited by the unscaled cap. An
  optional host-supplied height-cap reference keeps that fraction based on the
  full activity content height when the embedded view is later measured inside
  a shorter accessory container. The host may also apply live key-margin and
  corner-radius overrides: the former scales both immutable `Config` margin
  ratios, while the latter takes precedence over `Config`/palette radius;
  setters clear `Theme.Computed` and rebuild it during the next measurement.
- `ACTION_CANCEL` commits live pointers (`Pointers.onTouchCancelCommit`)
  instead of aborting like upstream. Embedded in an activity the only cancel
  source is the system pilfering the stream for a navigation gesture; the
  exclusion request above can be partially denied (the per-edge 200dp budget
  is shared with accessibility overlays such as QuickCursor), and aborting
  would silently drop the pressed key.
- `Theme.Palette` accepts resolved ARGB roles, upstream label-dimming factors,
  palette opacity, and border geometry; static keyboard styles remain a
  fallback. `Keyboard2View` exposes its resolved keyboard and label colors for
  activity-owned inset and adjustment controls. The icon font asset and its
  upstream text sizing are unchanged.
- `Gesture` receives circle sensitivity; `Pointers` receives configuration,
  uses the main looper, exposes reset, and cancels pending callbacks.
- `KeyModifier.apply_gesture` (clockwise-circle / round-trip) tries `apply_shift`
  first and returns it when Shift changes the key (i.e. letters), consulting the
  modmap `Fn` binding only afterwards. Upstream consults the modmap `Fn` binding
  first, which made the circle gesture yield the Fn key instead of a capital on
  our terminal layouts that bind `<fn>` for every letter. Non-letters and
  non-`Char` kinds still fall through to the Fn binding unchanged.
- `KeyboardData` has no static resource cache and enforces 16 rows, 32 keys per
  row, and 512 keys total, with public parse location details.
- `LayoutModifier.modify` is pure and composes the curated bottom row, the
  optional number row, and the host-enabled extra keys. The bundled bottom row
  omits clipboard, emoji, voice, and Android method-picker actions.
- Upstream's extra-key injection is ported: `KeyboardData.addExtraKeys`,
  `add_key_to_preferred_pos`, `add_key_to_pos`, `PreferredPos`, and
  `KeyPos.with_dir` match upstream, except `addExtraKeys` works on deep copies
  of the rows (upstream mutates `Row.keys` in place; our `KeyboardData`
  instances are cached and shared). `LayoutModifier.modify` mirrors upstream
  `modify_layout` ordering (bottom row, `loc` strip against the enabled extra
  keys — now active — placement of missing keys, then number row) with the
  enabled set supplied by the host through `LayoutOptions.extraKeys` instead
  of a global config; locale/method extra keys and the always-added `CONFIG`
  key are not ported.
- Stateful suggestion labels have no global provider and render empty.
- Tap correction hook (local addition): `Keyboard2View.TapResolver` plus
  `setTapResolver`, and the new file `TapGeometry.java`. At `ACTION_DOWN` the
  view resolves the static grid as upstream does, then lets the host resolver
  move the press to another key index before `Pointers.onTouchDown`; the raw
  point still reaches `Pointers`, so swipe directions are unchanged. At
  `ACTION_UP` the resolver is told the raw key, the down point and whether
  `TouchFx.swiped` fired. `TouchFx` gained a `rawKey` field for that. The
  colour-editor paint path (`paintKeyAt`) never consults the resolver. The
  model itself lives in the launcher
  (`app/.../terminal/inappkeyboard/TapModel`, `TapModelStore`,
  `TapCorrectionController`); the module holds no learning logic.
- Logging, utilities, and haptics are reduced to the retained embedded needs.

## Refresh procedure

1. Record the new upstream commit and copy the selected sources, all layout
   XML, special XML resources, generated compose table, and icon font into a
   temporary tree; do not overwrite this module directly.
2. Compare every copied file semantically against this snapshot and preserve
   upstream copyright/license headers.
3. Reapply the adaptations listed above, including the curated bottom row and
   parser limits. Do not import stripped packages or build generators.
4. Replace the reviewed files, rerun `tools/gen_layouts.py` so the catalogue
   matches the layouts that arrived, update this document's commit and path
   list, and inspect the final diff for input-method, editor-connection,
   window, or native dependencies.
5. Run `./gradlew :inapp-keyboard:assembleDebug
   :inapp-keyboard:testDebugUnitTest --console=plain` and the forbidden-import
   grep documented in the project design before merging.
