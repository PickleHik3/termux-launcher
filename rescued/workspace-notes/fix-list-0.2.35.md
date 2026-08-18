# Termux Launcher — fix list for 0.2.35

Compiled 2026-08-14 from on-device verification of `dev @ f028844b` on Pong (A065, Android 16),
plus GitHub issue #11 and feedback on the extra-keys editor.

Ranked by user-visible harm, not by effort. File references are from `~/termux-launcher/app/termux-launcher`.

**Status 2026-08-14 — every item below is implemented and uncommitted in the working tree.**
Unit tests: app 1542 / 1542 green. All of it is device-unverified; the checklist is at the bottom.

**Decisions taken**

- Glyph picker ships the **curated ~300 glyph** catalogue (arrows, blocks, shapes, technical, terminal marks).
- **Work-profile / cross-profile widget integration is parked.** Not verified, not scheduled; see "Parked" below.

---

## P0 — reported bugs, visible on a default install

### 1. Issue #11a — no _Style_ entry in the terminal long-press menu

- **Where:** `app/src/main/java/com/termux/app/TermuxActivity.java:578-598` (context-menu id block), `onContextItemSelected` at `:10015`
- **What:** Upstream Termux offers _Style_ on terminal long-press, which opens Termux:Styling. This fork's
  menu is `SELECT_URL · SHARE_TRANSCRIPT · SET_WALLPAPER · REMOVE_WALLPAPER · LOOK_AND_FEEL · APPS_BAR ·
SETTINGS · RESET_TERMINAL · KILL_PROCESS · GLASS_LAB · COMMAND_PALETTE`. No Style item.
  `R.string.action_style_terminal` still exists in eleven locales and is referenced by nothing.
- **Fix:** restore the item, guarded on Termux:Styling being installed; otherwise point at
  Settings › Appearance. Add a preferences-tree/menu test so it cannot silently vanish again.
- **Size:** S

### 2. Issue #11b — `~/.termux/colors.properties` silently ignored

- **Where:** `app/src/main/java/com/termux/app/terminal/TermuxTerminalSessionActivityClient.java:649-680`;
  default at `termux-shared/.../preferences/TermuxPreferenceConstants.java:778`
  (`DEFAULT_VALUE_TERMINAL_DYNAMIC_COLORS_ENABLED = true`)
- **What:** with dynamic Material colours on (the default), `applyTerminalColors()` builds the palette from
  the wallpaper and the `else` branch — the only one that opens `colors.properties` — never runs. The file
  has no effect and nothing says so. Exactly the reported symptom.
- **Fix:** OPEN DECISION, see "Open questions" 1. Recommended: an explicit `colors.properties` takes
  precedence, and the dynamic-colours switch states when it is inactive because a file is present.
- **Size:** M

### 3. `IllegalArgumentException: Invalid property: 'contrast_level'` on every palette apply

- **Where:** `app/src/main/java/com/termux/app/terminal/MaterialTerminalColorScheme.java:64` →
  `terminal-emulator/src/main/java/com/termux/terminal/TerminalColorScheme.java:71` →
  `TermuxTerminalSessionActivityClient.java:681`
- **What:** `create()` puts `contrast_level` into the same `Properties` handed to
  `TerminalColors.COLOR_SCHEME.updateWith()`, which throws on any key that is not
  `foreground` / `background` / `cursor` / `colorN`. It throws mid-iteration over an unordered map, so the
  palette is **partially applied** and `resetAllSessionColors()` + `updateBackgroundColor()` never run.
  Observed 6× in one session (once per activity start). Same method as item 2.
- **Fix:** keep the terminal props colour-only; set `contrast_level` only in
  `createMaterialRoleProperties()`, which already re-reads it with a fallback (`:161`).
  `app/src/test/java/com/termux/app/terminal/MaterialTerminalColorSchemeTest.java:33` asserts it on
  `create()`'s output, so that assertion moves with the value. Add a test that `updateWith` is never handed
  a non-colour key.
- **Size:** XS

---

## P1 — device-found defects and editor rework

### 4. Back does not close the FULL widget pane

- **Where:** handler exists at `TermuxActivity.java:10647` (`onBackPressed`); the missing hook belongs beside
  `TermuxTerminalViewClient.java:351` (`handleAppDrawerKey`)
- **What:** two Back presses left the pane open; pull-up closes it. On a device the back key travels the key
  channel and is consumed before `onBackPressed()` runs — which is what `TermuxActivityBackOrderTest`
  already documents for the drawer. The drawer has a key-channel claim; the widget pane and FULL status pane
  have none.
- **Fix:** add a pane/FULL claim in `onKeyDown` next to the drawer's, plus the matching `onKeyUp` swallow.
  Test through the key channel, not through `onBackPressed()`.
- **Size:** S

### 5. Extra-keys editor caps clip to one or two letters

- **Where:** `app/src/main/java/com/termux/app/terminal/io/ExtraKeysRowEditor.java:355` (swipe-up badge),
  `:365-370` (cap label); fallback at `ExtraKeysLayoutModel.java:52` (`label()`)
- **What:** `label()` returns the raw `key` when `display` is null, so a cap can read `KEYBOARD` — or
  `tool:workspace.picker`. The cap label is `maxLines(2)` + ellipsize inside a 56dp cap, giving two clipped
  lines; the badge is a single 9sp line of `"↑ " + popup.label()`, so `Previous session` renders as `↑ P…`.
  The live row shows glyphs because it has a mapping the editor does not reuse.
- **Fix:**
  1. Resolve cap labels through the same glyph map the live row uses (one shared resolver, used by
     `ExtraKeysView` rendering and the editor) so a cap never shows an action id.
  2. Caps never wrap: single line, auto-shrink to a 9sp floor, then ellipsize. The full action name belongs
     in the inline panel, which has the width.
  3. Same treatment for the swipe-up badge.
- **Size:** S

### 6. No glyph field for swipe-up (popup) keys

- **Where:** `ExtraKeysRowEditor.java:495-500` (inline panel fields)
- **What:** the panel writes `display` for the tap key only. A swipe-up assignment keeps whatever the action
  picker wrote — e.g. `{"key":"tool:session.previous","display":"Previous session"}` — which then clips in
  the badge and shows as a long word in the live row's swipe-up hint. There is no way to give it a glyph.
- **Fix:** add a **Swipe-up label** field mirroring **Display label**, writing `popup.display`. Round-trip
  test through `ExtraKeysLayoutModel` serialization.
- **Size:** S

### 7. In-app glyph picker — curated ~300 (decided)

- **Where:** new, `app/src/main/java/com/termux/app/terminal/io/` + `res/raw/`
- **What:** replace the "paste an emoji from the system IME" detour with a picker on both label fields.
- **Design:**
  - Catalogue as a reviewed `res/raw` CSV: code point, name, keywords, category — same review model as
    `app_drawer_category_overrides.csv`, with a test that fails on unsorted, duplicate or malformed rows.
  - Categories: Recent, Arrows, Blocks, Shapes, Powerline, Technical, Terminal marks.
  - **Filter every candidate through `Paint.hasGlyph()`** against the cap paint, so a glyph the device cannot
    draw is never offered. This is why we ship a list rather than Unicode ranges.
  - Focusless search on name + keyword, reusing the drawer/palette intake so `TerminalView` keeps its
    `InputConnection`.
  - Recents persisted in preferences, capped; live preview in a cap-shaped swatch.
  - Entry points now: Display label and Swipe-up label. Later reuse: folder rename, bindings editor.
- **Caveat to keep in the docs:** caps draw with the **UI** font, not the terminal font, so Nerd-Font and
  Powerline private-use glyphs only appear when the system font carries them; `hasGlyph()` hides the rest
  rather than showing tofu.
- **Size:** M

### 8. Glass backdrop crop is wrong in landscape

- **Where:** wallpaper pre-blur crop path (`createCachedAccessoryWallpaperBlurCrop` and the per-radius LRU)
- **What:** in landscape, panes show a brighter, mismatched wallpaper region with a hard seam at the pane's
  left edge; the clock's date row loses contrast. Portrait is correct. Likely the Nothing OS 1.10× zoom
  compensation and/or crop geometry assuming portrait dimensions.
- **Fix:** derive the crop from the current display orientation, and key the cache on orientation as well as
  radius. Add a test that the crop rect matches the surface rect in both orientations.
- **Evidence:** `.lavish/img/landscape-glass.png`
- **Size:** M

### 9. App drawer is unreachable in landscape

- **Where:** `AppDrawerGestureArbiter` / dock rail
- **What:** the portrait gesture is a swipe-down on the pinned apps row. In landscape that row is a vertical
  rail that scrolls, so neither a vertical nor a horizontal swipe opens the drawer. No gesture does.
- **Fix:** OPEN DECISION, see "Open questions" 2.
- **Size:** M

---

## P2 / P3 — small ones

### 10. Widget remove chip clips the pane rim

- **Where:** `WidgetEditOverlayView`
- **What:** a widget in the rightmost column puts its ✕ half outside the pane, over the rim. Still tappable.
- **Fix:** tuck the chip inside the pane bounds when the cell touches an edge.
- **Size:** XS

### 11. Rotation into landscape resizes the terminal twice

- **Where:** accessory layout pass
- **What:** one rotation fires SIGWINCH twice (`11 110` then `12 110`); landscape→portrait fires once. TUIs
  redraw twice.
- **Fix:** settle accessory geometry before the first `updateSize()`; extend the existing
  resize-counting test to cover a rotation.
- **Size:** S

### 12. Drawer search ranks package-name matches above display-name prefixes

- **Where:** `AppDrawerContentView` search ranking
- **What:** `mo` puts Shizuku (`moe.shizuku…`) above Mobilism and Moonlight.
- **Fix:** rank display-name prefix > display-name substring > package match.
- **Size:** XS

---

## Done in this pass

### 13. Volume rocker stays the volume rocker

- **Where:** `termux-shared/.../settings/properties/TermuxPropertyConstants.java:460`,
  `docs/en/examples/termux.properties:40-44`,
  `app/src/test/java/com/termux/shared/termux/settings/properties/VolumeKeysDefaultTest.java` (new)
- **What:** `DEFAULT_IVALUE_VOLUME_KEYS_BEHAVIOUR` flipped from `virtual` to `volume`, so a home launcher no
  longer swallows the volume rocker on a fresh install. The example file already carried
  `volume-keys = volume`; its comment was rewritten because it no longer describes an opt-in.
  `volume-keys = virtual` restores upstream behaviour.
- **Verified:** `:app:testDebugUnitTest --tests '*VolumeKeysDefaultTest*'` → 2 tests, 0 failures.
- **Status:** uncommitted in the working tree.

---

## Parked

### Work-profile / cross-profile widgets

Not verified and not scheduled. What is known: the device has no managed profile (users are `0` and
`999 DualApps`), and the picker lists only user 0's providers even though the cloned AliExpress in profile 999
has widget providers. A clone profile is legitimately excluded from widget hosting on many ROMs, so this is
evidence, not proof of a defect. Settling it needs a real work profile (Shelter/Island or an EMM) on a test
device. The code path already exists — `LauncherWidgetHostController` resolves configure-activity
availability against the provider's own profile — it is only unverified.

---

## Open questions

1. **#11b resolution.** Does an explicit `colors.properties` win over dynamic colours, or do dynamic colours
   keep winning with a visible notice, or both plus the restored Style entry?
   _Recommended: file wins, with the dynamic switch explaining itself._
   user feedback: when someone changes the color scheme using the termux styling, it can turn the "Use wallpaper colors" toggle in appearence preference off. re enabling it makes it supercede colors.properties, add a small hint under "Use wallpaper colors" as hint to inform users abotu this behavior (concisely)
2. **Landscape drawer.** Add a gesture (swipe right off the rail, or a pull tab at the rail's top), or accept
   portrait-only and document it?
   user feedback: swipe right to open the drawer. Also add an option in "Launcher & Apps" to switch the app dock to left/right when in landscape mode. when dock is set to left side, swipe right opens the app drawer.
3. **Cap label ceiling.** For a long user-typed display label: shrink to 9sp, ellipsize, or reject in the
   field with a hint?
   user feedback: ellipsize
4. **Release shape.** Items 1–6 are small and fix reported bugs. Do they land in 0.2.35 with the current
   batch, or does 0.2.35 ship first with the features held back?
   user feedback: everythign in next release.

## Additional User notes;

1. There are some ui insconsitances such as session browser is still using android's popup surface, instead i want all the terminal stuff to be in-app surfaces similar to the ones that popup for window/session renaming.
2. in the window/ session renaming popup, remove the greyed out text whcih says "auto rename" and replace it with "window" or "session" whichever one is being renamed currently (so simplifying the rename pill and better space utilization)

---

## What was built (2026-08-14)

Every numbered item plus both additional notes. Notes on the ones that turned out differently from
the plan:

- **#2** resolved as the user asked rather than as recommended: dynamic colours keep winning, but
  applying a Termux:Styling scheme now switches *Use wallpaper colors* off by itself. Only the
  styling app sends `EXTRA_RELOAD_STYLE = "colors"` — the in-app reload carries
  `EXTRA_RECREATE_ACTIVITY` alone — so an internal restyle cannot trip it. A hint under the switch
  states the precedence.
- **#3** the level no longer travels inside the palette at all:
  `createMaterialRoleProperties(context, terminalProps, level)`. A `colorKeysOnly()` filter now sits
  in front of `updateWith()`, so a stray line in a hand-written `colors.properties` is cosmetic
  rather than a half-applied palette.
- **#8 and #11 share one root cause.** `onConfigurationChanged` runs *before* the window is re-laid
  out, so the geometry pass read the outgoing orientation's metrics: it sized the accessory stack
  from stale geometry and posted a resize, then did it all again after the real layout (the two
  SIGWINCHes), and left the glass holding a crop of the outgoing frame (the brighter, mismatched
  region with the seam at the pane's left edge). The pass is now deferred to a `OneShotPreDrawListener`
  on the decor view, the blur cache is cleared on rotation, and the cache validity check carries the
  orientation.
- **#9** the landscape rail is not `SuggestionBarView` — that row is `GONE` in landscape. The rail is
  its own `ScrollView`, now `DockRailScrollView`, which arbitrates in `dispatchTouchEvent`. The
  arbiter's `portrait` boolean became a `Pull { DOWN, RIGHT, LEFT, NONE }`; the page-swipe test is
  skipped for a horizontal pull, because at 1.0×slop it would latch before the drawer's 1.15×slop and
  the rail has no pager to lose. New preference `app_launcher_dock_rail_side` (left/right, default
  left); the write recreates the activity, since the side moves the content root's cutout padding.
- **Note 1** is a first pass, not the whole ask: a reusable in-app surface
  (`TerminalSheetController`, a plane in the activity's view tree like the palette) now hosts the
  session browser and all seven dialogs it owned, including the `PopupMenu`. Back order is
  rename chip → widget/FULL panes → palette → sheet → drawer, wired through both the key channel and
  `onBackPressed()`. The remaining terminal dialogs are listed under "Still on popup surfaces".
- **Note 2** the chip's bold target tag is gone; the greyed placeholder is now the target word
  itself, which is what "simplifying the pill" asked for.

### Still on popup surfaces (next pass)

Strongest candidate first — the scrollback search has the same system-IME swap the browser had.

- `TerminalScrollbackSearchOverlay.java:63` — scrollback search, `AlertDialog` with a real `EditText`
- `TermuxActivity.java:10280` — the terminal long-press action sheet
- `TermuxActivity.java:11088` — `showKillSessionDialog`
- `TerminalHintsOverlay.java:29,39` — hints list and its "no hints" notice
- `TermuxTerminalViewClient.java:1114,1133,1140` — OSC 8 hyperlink prompt and the URL picker
- `TermuxActivity.java:10897`, `:12132`, `TermuxTerminalSessionActivityClient.java:455`, `:13411` —
  `TextInputDialogUtils` rename and new-session fallbacks, reached only when there is no in-app keyboard
- `ExtraKeysRowEditor.java:894,945` — reset and discard confirmations inside the editor
- `TerminalCommandPaletteController.java:1134` — the palette's destructive-action confirmation; it
  collapses first, so this one is a dialog by design
- `TermuxActivity.java:3953`, `:6854`, `:9875` — wallpaper-read permission, accessibility-lock prompt,
  wallpaper-target picker; launcher-side rather than terminal

### Device checklist

- Terminal long-press → **Style** opens Termux:Styling; uninstall it and the same entry opens Appearance.
- Pick a scheme in Termux:Styling → *Use wallpaper colors* turns itself off and the scheme applies.
- No `Invalid property: 'contrast_level'` in logcat across several activity starts.
- Back closes the widget pane and the FULL status pane on the first press, from the hardware key.
- Extra-keys editor: caps show glyphs, never `KEYBOARD` or `tool:…`; one line, no clipping; the
  **Swipe-up label** field writes through to the badge; the `Ω` picker inserts at the caret and the
  Powerline group is absent on a device with no Nerd Font in the UI font stack.
- Landscape: no seam at the pane's left edge, glass matches the wallpaper behind the rail; one
  SIGWINCH per rotation in both directions; swipe right off the rail opens the drawer; flip the rail
  to the right and swipe left. Vertical drags still scroll the rail; tapping a rail icon still launches.
  Worth checking landscape with the extra-keys row disabled — `AppDrawerController.captureGeometry()`
  refuses a drag when the dock has no height, which is pre-existing.
- Drawer search `mo` → Mobilism and Moonlight above Shizuku.
- Widget edit mode: the ✕ on a rightmost-column and a top-row widget sits inside the cell.
- Session browser: opens as a glass sheet with no IME swap, search types, back pops one card, the
  workspace picker's delete confirmation stacks over it. Renaming from the browser now closes the
  sheet so the chip is visible.
- Rename pill reads `window` / `session` / `pane` greyed when empty.

---

## Verification log (2026-08-14, Pong, dev @ f028844b)

Passed on device: weather °F live; extra-keys editor open/edit/save round trip into `termux.properties`
in place; rename chip (session/window/pane) with no IME swap and no resize, empty draft clears the name;
drawer categories v2 incl. Games, expand, Back-collapses-category; search rows with category subtitles;
widget pane pull-down, long-press menu policy, picker with previews, bind + RemoteViews render, edit-mode
drag/resize/remove, add/remove page and paging; pull-down hint pill; use-case switch both ways with snapshot
restore; pull-up closes FULL.

Gaps closed this pass: **SIGWINCH** — zero PTY resizes across a FULL open/close cycle (control: hiding the
keyboard fired exactly one), in both orientations; **provider configure activities** — external config screen
launched, choice returned, widget bound at the reserved cell with no orphan; **landscape** — rail, insets and
FULL pane work, three findings filed as items 8, 9, 11; **work profile** — parked, see above.

Unit tests: app 1495 / 1495 green on both `apt-android-7` and `apt-android-5` before the volume-keys change;
the new test is green after it.

---

## Device pass 2026-08-14 17:00 (build installed 15:54, `apt-android-7` universal debug)

### Passed

- **Style entry** (item 1) — terminal long-press → More → **Style** opens Termux:Styling.
- **#11b as decided** (item 2) — applying Argonaut from Termux:Styling flipped
  `terminal_dynamic_colors_enabled` to `false` by itself and the scheme applied (RED/GREEN/BLUE in
  Argonaut hues). The hint under *Use wallpaper colors* reads
  "While on, this overrides ~/.termux/colors.properties. Applying a Termux:Styling scheme turns it off."
  Re-enabling the switch superseded the file again. Device left with wallpaper colours on, as found.
- **`contrast_level`** (item 3) — zero occurrences in logcat across four activity starts and a
  scheme change. Zero `E Termux` lines in the whole session.
- **Back closes the FULL widget pane on the first hardware press** (item 4).
- **Drawer search ranking** (item 12) — `mo` → Mobilism, Moonlight, Morphe, Kuwait Mobile ID,
  SpotiFLAC Mobile, then Shizuku.
- **Action sheet contents and order** — card 1: Select URL, Search, Settings, Paste, More (Copy
  correctly absent with no selection; Paste present with a non-empty clipboard). Card 2: Command
  palette, Share transcript, Style, Set Wallpaper, Disable Wallpaper, Edit surfaces, Reset,
  Kill process (16881) — pid in the row. Back pops More back to card 1 without closing the menu.
- **Scrollback search is in-app** — `Ctrl+Alt+S` raises a glass card, typing lands in the field, and
  the match count and result rows update live (`RED` → "4 matches", rows with `+18` offsets).
- **Terminal hints** — `Ctrl+Alt+U` on an empty scrollback gives the in-app "No URLs, paths, hashes,
  or line references found." card, content-sized.
- **OSC 8 hyperlink** — tapping a hyperlink raises the in-app "Open link?" card with the target URL
  and Cancel / Copy / Open.
- **Session browser** — opens as an in-app glass sheet anchored under the sessions chip, no system
  popup, no IME swap, in-app keyboard untouched. A long-press on the terminal while it is open
  stacks nothing (it dismisses the sheet).
- **Rename pill** (note 2) — placeholder is the greyed target word (`window`), bold tag gone.

### Failed — new findings

#### A. Select URL does nothing and wedges the whole action sheet

Tapping **Select URL** shows no picker, no list and no "no URLs" notice — with two plain URLs and an
OSC 8 target on screen, and also with an empty scrollback. Worse, after that tap the action sheet
cannot be opened again at all: neither the hardware MENU key nor long-press → More… brings it back,
and Back does not clear it. Only restarting the activity restores it. Reproduced twice, once per app
start, so the sheet's controller is left believing a card is up.

This is the highest-priority regression from this pass: one tap costs the user every entry on that
menu until they relaunch.

#### B. The More card draws on top of card 1 instead of replacing it

The second card is composited over the first: card 1's rows ghost through between the More rows
("Terminal / Select URL / Search / Settings / Paste / More" visible behind), and the accessibility
tree exposes both cards' rows at once, so taps and screen readers see two menus. Card 1 should be
hidden (or dimmed and inert) while More is up. Back-popping already works, so this is presentation
and hit-testing, not navigation.

### Not yet verified from the checklist

Extra-keys editor (glyph caps, one-line labels, Swipe-up label reaching the badge, `Ω` picker at the
caret, Powerline group absent); the landscape suite (seam, one SIGWINCH per rotation, swipe away from
the rail, rail side preference, extra-keys row off); widget edit-mode ✕ inside rightmost-column and
top-row cells; Copy row appearing after a selection and Paste row disappearing with an empty
clipboard; kill-process confirmation card; palette destructive confirmation; session-browser search
field, back popping one card, and rename closing the sheet.

---

## New requests (2026-08-14, after the device pass)

### 14. Long-press menu should be a small popup at the touch point

The action sheet currently fills most of the width and sits centred. It should be a compact popup
anchored where the finger went down — like the launcher's folder and widget-pane menus — sized to its
rows rather than to the screen.

- **Size:** S

### 15. Scrollback search belongs above the dock, dock-width, with arrow-key navigation

Replace the full-height card with a search bar the width of the dock, sitting directly above it. It
grows upward to show results as they come in. Arrow keys navigate the result list from all three
input paths — the in-app keyboard, an Android system IME, and the extra-keys rows — with Enter
jumping to the highlighted match.

- **Size:** M

### 16. Even inner padding on category tiles

In the drawer's categories view the 2×2 block of small icons in the bottom-right corner sits closer
to the tile's inner edges than the large icons do. Tighten the block so its footprint is roughly one
large icon and the inner padding matches on every side.

- **Where:** `AppDrawerCategoryGridMetrics` (`SMALL_BLOCK_GAP_DP`, `TILE_INNER_PADDING_DP`,
  `SLOT_GAP_DP`), `AppDrawerCategoryTileView`
- **Size:** XS

---

## Device pass 2 — 2026-08-14 17:30-18:20 (same build)

### Newly verified as fixed

- **Item 5 — editor cap labels.** Cap 1 now draws `⌨` with an `↑ ⎘` badge; every cap is one line, no
  wrapping, no action ids. The only clipped badge left is a key whose *stored* swipe-up label is a long
  word (`Next session`), which is what item 6's field is for.
- **Item 6 — Swipe-up label field.** Present under Display label with the hint "Shown in the small
  badge above the key". Setting it to `◀` wrote
  `{"key":"tool:session.previous","display":"◀"}` into `termux.properties` and the badge became `↑ ◀`.
- **Item 7 — glyph picker.** `Ω` on both label fields opens "Choose glyph": search field, groups
  ARROWS / BLOCKS / … / TERMINAL MARKS, every cell named by its Unicode name, no tofu anywhere, and
  **no Powerline group** on this device — `hasGlyph()` filtering behaving exactly as designed. The
  chosen glyph inserted at the caret (field went from `Previous session` to `◀Previous session`).
- **Item 8 — landscape glass.** No seam at the pane's left edge; the glass matches the wallpaper behind
  the rail and the keys row. The rotation bug is gone with it.
- **Item 9 — landscape drawer.** `app_launcher_dock_rail_side` exists with a Left/Right pill and the
  summary "Which edge the landscape apps rail docks to. Swipe off the rail, away from that edge, to
  open the app drawer." Verified on both sides: rail right → swipe left opens the drawer; rail left →
  swipe right opens it. A vertical drag still scrolls the rail and does not open the drawer.
- **Item 11 — one SIGWINCH per rotation, both directions.** Portrait→landscape `WINCH=1 7 103`,
  landscape→portrait `WINCH=3 26 54`; the middle one was the rail-side preference recreating the
  activity, not a rotation.

### Failed — findings from this pass

#### C. Kill process is a silent no-op

`More → Kill process (28997)` closes the sheet and does nothing: no confirmation card, and the session
is not killed either. The checklist expects a confirmation card with the pid in the title. The sheet
itself still opens afterwards, so unlike finding A this one does not wedge anything.

#### E. Coming back from landscape drops the portrait dock rows

After a landscape session (and a rail-side change) and rotating back, the portrait dock renders with
**no apps row and no A-Z row**, while `app_launcher_apps_row_enabled` and
`app_launcher_az_row_enabled` are both still `true` in preferences. Restarting the activity restores
both. Presumably the landscape path leaves `SuggestionBarView` GONE and the portrait re-entry never
un-hides it.

#### F. Copy can never appear in the glass card

A long-press raises Android's own selection toolbar (Copy / Paste / More…), and its **More…** is what
opens the glass card — but that tap clears the selection, so the card is always built with nothing
selected and its Copy row can never appear. Re-test after item 14 changes the entry point.

#### Cosmetic

- The glyph picker's preview swatch above the search field stays empty.
- The scrollback search card reserves its full height before a query exists; it only looks right once
  results fill it. Item 15 replaces this surface anyway.

### Still not run

Widget edit-mode ✕ placement (item 10); landscape with the extra-keys row disabled (the pre-existing
zero-height-dock case); the palette's destructive confirmation; the session browser's search field,
one-card back-pop and rename-closes-sheet; and the Paste row disappearing with an empty clipboard
(no easy way to clear the clipboard from adb).

---

## New requests (2026-08-14, landscape)

### 17. Landscape rail needs edge insets

The rail's icons sit hard against the display edge on the docked side, and when the rail is scrolled
the top icon passes under the system status bar. On the right-docked side the terminal's own text also
runs underneath the icons instead of being inset past them.

- Inset the rail from the display edge (cutout-safe plus a margin of its own), pad its scroll range so
  the first and last icons clear the status and navigation bars, and make the content column inset
  past the rail on the right exactly as it does on the left.
- **Size:** S

### 18. Landscape app drawer needs its own metrics — categories view is far too sparse

In landscape the categories view keeps the portrait tile shape and the portrait seven-icon layout, so
three tiles stretch across the full width with most of each tile empty, icons scattered far apart, and
the bottom row of every tile clipped by the tile border. It reads as wasted space rather than a grid.

- Give landscape its own tile aspect and icon count (more columns of smaller tiles, or the same tiles
  with a denser icon block), and size the plane so tile content is never clipped.
- **Size:** M

---

## Fix pass 2026-08-14 evening — findings A/B/C/E and items 14, 16-18

Built, tested (app 1560/1560 green) and installed to Pong at 20:38. Verified on device over Tailscale.

### Fixed and verified

- **A — Select URL wedged the sheet.** `TerminalActionMenu.run()` executed the row's action and then
  called `dismissAll()`, which took down the card the action had just opened; the plane was left
  believing a card was up, so the menu could not be opened again until the activity restarted.
  The menu cards standing when the row fires are counted first, and only those are popped
  (`TerminalSheetController.dismissUnder(int)`). **Verified:** Select URL now lists both seeded URLs,
  latest first, and the menu reopens afterwards.
- **B — the More card bled through the first.** New `coverPrevious` flag on `show()`: a submenu hides
  the card underneath, since two translucent glass cards read as one card with both sets of rows
  showing through, and both were exposed to accessibility. A confirmation over a picker still stacks
  visibly, which is the point of stacking. **Verified:** no bleed-through, and only the More card's
  rows are in the accessibility tree.
- **C — Kill process was a silent no-op.** Same root cause as A. **Verified:** the confirmation card
  reads `Kill process (20008)` / "Really kill this session?" with Cancel and OK. Cancelled on device;
  no session was killed.
- **E — portrait dock rows stayed collapsed after landscape.** `runOrientationGeometryPass()` rebuilt
  the toolbar height and the rail but not the accessory *render* state, which is what hides the apps
  and A-Z rows in landscape; nothing re-derived it on the way back. It now calls
  `configureExtraKeysBackground()` too. **Verified:** both rows are present immediately after
  rotating to portrait.
- **14 — compact menu at the touch point.** The long-press point is captured in
  `TermuxTerminalViewClient.onLongPress` (the press itself starts selection; the menu arrives from the
  selection toolbar's More, which carries no coordinates) and passed through to an anchored card:
  wraps its rows, capped at 260dp, measured once and clamped inside the plane so a menu near an edge
  moves in by its own size. Palette and keybinding invocations stay centred. **Verified** at
  (760, 900): a ~260dp card at the finger instead of a full-width sheet.
- **The menu's "Terminal" heading is gone**, at the user's request: an empty title now means a card
  carries no heading at all. The More card still labels itself "More"; say the word and that goes too.
- **16 — even tile padding.** `smallBlockBounds()` clumps the 2x2 block to one large icon plus the
  block's own gap, centred in its slot, so each of the four cells is exactly `smallIconPx` and the
  tile's inner padding matches on every side. **Verified** on the Communication & Social tile.
- **17 — landscape rail insets.** Rail width is the docked edge's cutout inset **plus** an icon column
  and its margins, rather than the larger of the two, and the scroll view is padded on all four sides
  including the navigation-bar inset. **Verified:** icons moved from ~126px to ~275px from the display
  edge, the first icon clears the status bar, and the content column insets past the rail.
- **18 — landscape category density.** Columns are no longer width-only: a tile that would exceed 75%
  of the viewport height adds columns, up to six. Portrait is unchanged at two, the approved
  1200x600 breakpoint still gives three. **Verified:** landscape went from 3 huge tiles to 6 columns
  across 2 rows with ten categories visible.

### Left over from this pass

- The landscape drawer's second row is still clipped by the plane's bottom edge (it scrolls, so the
  rows are reachable, but the plane could use bottom padding or one more row of height), and the rail
  overlaps the drawer plane's bottom corner.
- The glyph picker's preview swatch above its search field is still empty.
- **Item 15** (dock-width scrollback search bar above the dock, growing upward, arrow-key navigation
  from the in-app keyboard, a system IME and the extra-keys rows) is not started.

---

## Item 15 — scrollback search as a bar above the dock (2026-08-14, built and verified)

App tests 1562/1562 green; installed to Pong and verified over Tailscale.

- **`TerminalSheetController.Placement`** replaces the loose anchor parameter: `centered()`,
  `at(point)` for the anchored menu, and `aboveDock()` for this bar. The docked card takes the dock's
  own width and left edge (`TermuxActivity.dockBoundsOnScreen`) and sits on the dock's top edge with
  bottom gravity and wrap height — which is what makes it grow *upward*: the bottom edge stays on the
  dock while the content pushes the top edge up, so it covers only as much terminal as it needs.
  A terminal-only install with no dock falls back to the plane's bottom inset.
- **Arrow navigation from all three input paths.** `TextSink` gains `onArrow(int delta)` with a
  default no-op, and `handleKeyCode` — the one funnel the in-app keyboard's key values, hardware and
  IME key events, and the extra-keys rows all arrive through — maps DPAD up/down to ±1 and page
  up/down to ±5 rows. The highlight is clamped, not wrapped
  (`TerminalScrollbackSearchModel.moveHighlight`, unit-tested), because an arrow held at the end of
  the list should stop there.
- **The bar itself:** query and match count share one row, results lift the bar by whole rows to a
  five-row ceiling and scroll inside it beyond that, the highlighted row is marked with `▸` (an
  unfocused list draws no selector of its own, and nothing on this plane may take focus), and ⏎ jumps
  to the highlighted match rather than the first. No heading — the field's hint says what it searches.
- **Verified on device:** 12 seeded matches, bar at dock width above the dock, two DPAD_DOWN moved the
  highlight from `Row +23` to `Row +21` with the list following, ⏎ committed and dismissed, and an
  empty result set collapses the bar back to a single row.

---

## Committed and pushed 2026-08-14 — `f028844b..cac29764` on dev (11 commits)

Both variants green before committing: app 1562, terminal-emulator 296, terminal-view 43,
inapp-keyboard 19, termux-shared 4 — zero failures on `apt-android-7` and `apt-android-5`.

```
e3ddbd7d feat(app): wire the sheet plane, the rail side and the rotation pass in
a1fc1445 feat(terminal): move every terminal prompt onto one in-app sheet plane
33ff2d98 feat(terminal): scrollback search as a bar on the dock, walked by arrow keys
de00b80d fix(terminal): stop a hand-written palette losing in silence (#11)
90dd252c feat(keys): a glyph picker, and caps that show glyphs instead of action ids
51baabff feat(launcher): make landscape usable — rail side, insets and a denser drawer
e5c3c834 fix(widgets): keep the remove chip inside the pane
eafb98bc feat(shared): the volume rocker stays the volume rocker
d89ecf7e docs: document the landscape rail, the glyph picker and the nix pages
08ee56bd feat(terminal): simplify the rename pill to one greyed target word
cac29764 fix(launcher): show the glyph being replaced, and let category rows scroll clear
```

`TermuxActivity`, `strings.xml` and `activity_termux.xml` are cross-cutting and went whole into the
hub commit, so intermediate commits are not individually buildable — only the tip is verified.

### Verification state at push

Verified on device (Pong, over Tailscale): the Style entry and the Termux:Styling precedence, no
`contrast_level` in logcat, Back closing the FULL pane, Select URL, the More card, the kill
confirmation, the anchored menu at the touch point, the drawer search ranking, the extra-keys editor's
glyph caps and Swipe-up label, the glyph picker including the absent Powerline group, the tile block
padding, the landscape rail insets, the landscape drawer density, one SIGWINCH per rotation, the dock
rows returning after landscape, and the scrollback search bar with arrow navigation.

**Unverified:** the two fixes in `cac29764` — the glyph swatch's opening preview and the category
overview's bottom padding. The build carrying them is installed on Pong, but the phone locked before
the visual check. Both are cosmetic and unit tests are green; worth a glance next time the drawer is
opened in landscape.
