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
- Launcher tool keys (local addition): the `Launcher_tool` kind, its
  `LauncherTool` payload, `makeLauncherToolKey`, `parseLauncherToolKey` and the
  `tool:` name prefix have no upstream counterpart. They are the one seam
  between a key slot and the launcher's action registry, so any slot — including
  a space-bar swipe written in `~/.termux/keyboard/layout.xml` — can run a
  registry tool with no per-tool code in this module. `LauncherTool` carries its
  own `hashCode`/`equals`: `KeyValue.hashCode` delegates to the payload, and
  tool keys are freshly built on every `getKeyByName`, so without them a tool
  key could not be looked up in the key maps `addExtraKeys` uses.
- Split keyboard type (local addition): the new file `SplitLayout.java`, the
  `LayoutModifier.gapUnits`/`LayoutModifier.split` delegates in front of it, and
  `Keyboard2View.setSplitGapUnits`/`getSplitGapUnits`/`getSplitGapBounds`.
  Upstream's own split — `split_middle_column.xml` plus its layout modifier —
  was not ported (see "Deliberate removals"); this one is a step of its own
  after `modify`, parting every composed row at its midpoint by a gap given in
  key-width units. A key straddling the midpoint is cut into two keys of the
  same values only when it is at least 1.5 units wide — the space bar; a letter
  key keeps its shape and the parting takes its nearer edge, so the halves may
  differ by one key. The view is told the same gap: it then keeps no view
  background and paints one slab under each run of keys instead, and refuses
  (`onTouch` returns false) a press that starts in the parting, so the press
  reaches whatever the keyboard is over. Both are inert at gap zero, which is
  the docked keyboard.
- Parting asked for in pixels (local addition):
  `SplitLayout.gapUnitsForPx`/`commonGapUnitsForPx` with `MAX_GAP_FRACTION`, the
  `LayoutModifier.commonGapUnitsForPx` delegate, and
  `Keyboard2View.getKeyContentWidthPx`/`splitSlabRadiusPx`. The launcher stands
  its mouse-mode touchpad in the parting and needs a floor on it in dp, but the
  parting is stored in key-width units and parting widens the keyboard, so the
  units that buy a pixel shrink as the gap grows; `gapUnitsForPx` inverts that,
  and `commonGapUnitsForPx` adds back what the common band loses to rows parting
  at different key boundaries (a fixed offset, so one correction is exact). The
  ask is capped at half the width so both halves keep their keys.
  `getKeyContentWidthPx` is the width the keys are laid out across, which is what
  the conversion is measured against; `splitSlabRadiusPx` is the corner radius
  `drawSplitBackground` gives the run slabs, so a host panel standing in the
  parting takes the same shape.
- Split slab colour (local addition): `Keyboard2View.setSplitBackgroundColor`
  and `getSplitBackgroundColor`. The slabs are the panel a parted keyboard lies
  on rather than a fill inside one of the host's surfaces, so the host picks
  their colour — the launcher hands in its own overlay surface role, which no
  module-side theme attribute could name. Null, the default, paints them in the
  keyboard's own background exactly as before, so nothing changes for a host
  that says nothing.
- Stateful suggestion labels have no global provider and render empty.
- Key rect probe (local addition): `Keyboard2View.getKeyRectOnScreen` and its
  `getSpaceBarRectOnScreen` alias. The host has to be able to point at a key —
  the first-boot tour glows the keys of a chord, and surfaces grow out of the
  space bar — and a rendered cap is not a child view it could measure. The walk
  mirrors `onDraw` exactly so the rect lands on the drawn cap rather than on its
  cell, and only a key's centre value is matched, never one of its eight corner
  values: a corner is a swipe, not the key being named. Kind and value decide
  the match, with flags left out, because the same key carries different
  rendering flags depending on how a layout file spells it. A layout that does
  not carry the named key answers false, which is a normal answer.
- Function-key palette slot (local addition): `Theme.Palette.functionKeyBackground` /
  `functionLabelColor`, the matching `Theme.colorKeyFunction` / `functionLabelColor` /
  `functionSubLabelColor` / `functionSecondaryLabelColor` / `functionGreyedLabelColor` fields,
  `Theme.Computed.key_function`, and the `functionStyle` parameter on
  `Theme.Computed.Key`'s constructor. Upstream's Action role covers both the real enter/editor-
  action key and every other modifier key sharing that role (shift, ctrl, backspace, arrows,
  layout switch, config — see `bottom_row.xml`), with no way to give the actual action key a
  different look from the rest. Since re-splitting that role in every layout's `role=` attribute
  would be a layout change, `Keyboard2View.isEnterKey` tells them apart at draw time instead, from

- `Keyboard2View.tierFor` (2026-09-16): a `Normal`-role key is classified from its value (enter → action tier, modifiers/non-printing key events/layout events → function tier, the space editing key → space bar) because the shipped `termux_launcher_qwerty.xml` bottom row and user layouts omit `role`; the bottom row in that layout now also carries `role="action"`/`role="space_bar"` like upstream's `bottom_row.xml`.
  a key's own value (`Kind.Keyevent` with `KEYCODE_ENTER`) rather than its role, and picks
  `key_action` (the filled Material action-button look) only for that key, `key_function`
  (function-key look) for the rest of the Action role. Space_bar no longer borrows the Action
  role's label colors — it now reads from the same label source as Normal, since
  `InAppKeyboardPaletteFactory` gives it the letter keys' tone rather than the action role's. Every
  shorter `Palette` constructor overload defaults the two new fields to `keyBackground`/
  `labelColor`, so a caller that does not migrate (the imported-Base16-palette path in
  `InAppKeyboardColorScheme.applyToPalette`, and `KeyboardColorSchemeFragment`'s preview-only
  `withKeyboardBackground` copy) renders function keys like ordinary letter keys instead of
  losing color entirely.
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
- Pressed-key popup hook (local addition): `Pointers.IPointerPreview` plus
  `Pointers.setPreviewHandler`, a `slot` field on `Pointer`, and
  `Keyboard2View.KeyPopupListener` / `KeyPopupInfo` / `setKeyPopupListener` /
  `labelFont()` / the private `keyBoundsInView` and `popupLabel`. The host draws
  a floating glyph above the key under each finger; the module only reports what
  every live pointer holds. It reports from `Pointers` — the value that would be
  committed on release, and which of the eight corners it came from — rather than
  letting the host re-derive the gesture from the touch stream, so the popup can
  never disagree with what the keyboard types. The design handoff describes its
  own gesture model (a 13px dead zone and a dot product over the configured
  directions); that model belongs to the prototype and is deliberately not
  reimplemented. Nothing in this hook changes what a key commits: every callback
  is fired beside an existing `IPointerEventHandler` call, never in place of one.
  `getNearestKeyAtDirection` additionally records the corner it chose on the
  pointer, and `apply_gesture` clears it for circle gestures, whose value comes
  from no corner. The popup view, its geometry and its palette live in the
  launcher (`app/.../terminal/inappkeyboard/KeyPopupOverlayView`,
  `KeyPopupGeometry`, `KeyPopupPalette`, `KeyPopupController`); the module draws
  none of it.
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
