# Page glyph row and switcher (2026-11-16)

Replaces the floating **page switcher** (the edge dot and its picker, `com.termux.app.wall.switcher`)
with one switcher reached two ways: a glyph row where Home's page dots are, and an opt-in extra-keys
key. Decided with the developer on 2026-11-16.

## Removed

- The edge dot, its picker and everything wired to them: `PageSwitcher`, `DotView`, `DotGesture`,
  `DotPlacement`, `Edge`, `HintGate`, `PickerView`, `PickerLayout`, `PickerMotion`, `PickerHaptics`
  unless reused; the `page_switcher_layer` in `activity_termux.xml`; the Settings › General toggle
  (`page_switcher_enabled`) and the dot's prefs (enabled, edge, height, hint shown) and strings; the
  first-run hint; their tests; the glossary entry and `docs/en/Home_Screen_And_Apps.md` section.
- Stored values of the removed prefs are left alone or cleared; nothing reads them.
- Pure parts worth keeping (the page list in `PickerModel`, `PickerTargets.carryOut`) move to the new
  switcher's package rather than being rewritten.

## The glyph row (Home)

- Replaces `PageDotsView` in the band under the Home grid (`WidgetPaneView`). The band's height is
  unchanged, so no widget resizes. The dock's line stays separate below it.
- Four small glyphs, no capsule behind them, in this order: **Home, Terminal, Assistant, Display**.
- Home's glyph holds the current Home page number. Terminal's glyph holds the current terminal window
  number (one glyph however many windows are open). Assistant and Display have no number.
- A glyph whose page is not on the wall is hidden: no display running, Assistant off, Home absent
  under Terminal only.
- Shows even with one Home page. Settings › Home › Page indicator becomes **Glyphs / Off**; the stored
  value `dots` reads as Glyphs. Off hides the row (the band stays).
- The row's touch area is taller and wider than the glyphs.
- Glyphs are drawn by Codex in the family of `ic_wall_assistant` (24dp, filled, evenOdd cut-outs);
  Home and Terminal carry a cut-out slot whose centre and size are constants used to centre the number.

## The switcher (one component, two entry points)

- A full-width panel with no background (no card, glass, rim or scrim) holding one row with one cell
  per page: Home 1…n · Terminal 1…n · Assistant · Display, grouped by kind, absent kinds left out.
  The glyphs and the title float over the wall with the A–Z bar's legibility treatment (its halo
  stroke under each glyph, number and the title, `GlassInk.HALO_*`); the focused glyph draws in the
  Material accent colour (the A–Z bar's accent, through the scheme) with a soft glow of the same colour.
- Cells near the finger lift and grow on the A–Z bar's sine wave (`AzScrubRowView`'s envelope:
  `env = exp(-d² · 0.85)`, lift `sin(min(1, env) · π/2)`), so a dozen small cells stay pickable. The
  wave math is pure and shared, not copied.
- The highlighted page's full name shows above it: kind + number + title, ellipsized —
  "Terminal 2 · htop", "Home 1", "Assistant", "Display · XFCE".
- A haptic tick when the highlight crosses into a new cell.
- **Tap** the entry point: the panel opens and stays; tap a cell to go there.
- **Touch and drag** sideways from the entry point: the panel opens and the cell under the finger is
  highlighted. **Release** on a cell switches. **Move above the panel** to cancel (highlight clears;
  release does nothing). Tap outside or Back closes an open panel.
- On open, the current page's cell is highlighted.
- **The page changes only on release.** Nothing lands while sliding (a landing can start a shell,
  raise or run a Linux app, start the display, and swaps the extra-keys set). The move on release is
  the existing switcher move (`goToSwitcherPage`, animation per its current rule).
- Navigation only: no add or start cells.
- From Home the panel sits over the glyph row; from the key it is a full-width strip directly above
  the extra-keys bar. It rides above the keyboard when one is up.

## The extra-keys key

- A new opt-in key (tool key, e.g. `app.page_switcher`). Its cap shows the current page's glyph and
  number. Touch and drag or tap behave as above, the panel anchored above the bar.
- Works on every bar, Home's included: it opens the same panel, anchored above that bar. The key
  editor offers it for every bar. Not in any default row. (Decided 2026-11-16, replacing "Terminal,
  Assistant and Display only".)
- Updating users get a new **Setup card** (the setup sheet already re-opens for new cards only):
  - used as a home screen: "Add" puts the key at the left end of Global's row (the default row
    when Global has none stored) and of every page's own row that exists then. A page without its
    own row follows Global, so it gets the key through Global, and so does a row made later. Nothing
    is copied, and the key is never added twice. "Not now" dismisses;
  - used as a terminal only: the card only says the key exists (no Add).
  New installs see the card the same way.

## Surfaces to check

Keyboard up and down (key panel rides above it); one and many Home pages; Terminal only (no Home
glyph); display running and not; Assistant on and off; light/dark/black/Material You and scheme
chrome; density and font scale (title above the wave); Docked and Floating dock styles (the row sits
above the dock's line in both).

## Status

Built on branch feat/page-glyph-switcher; device checks owed in `project-docs/device-checks.md`.

## Page bar (decided 2026-11-20, replaces the glyph row and the panel)

Grilled with the developer on 2026-11-20 against the mock "Page bar — glyph directions"
(artboard "As decided 2026-11-20"). Built on branch feat/page-bar (#298); device checks owed in
`project-docs/device-checks.md`. What the code has today is above; this section
says what replaces it.

- **One switcher.** Home's four-glyph row and the per-page panel both go. In their place is one
  pill above the dock: the page bar. It is the resting indicator and the scrubber.
- **One glyph per page, in the wall's own order** (`WallPages`), not grouped by kind as
  `PageSwitchModel.ROW` does now. Equal slots.
- **Current page:** its glyph in the accent. No text capsule at rest.
- **Display:** a glyph only while a display is running; no mark inside the monitor.
- **Tap** a glyph: go to that page. **Slide** along the pill: the glyph under the finger lifts at
  2x into a separate round bubble above the pill, with the page's name over it; its two neighbours
  grow to 1.25x and the row shifts aside, as drawn; the current page's accent fades until release
  or cancel. The pill's outline never changes (no bulge: outlines stay convex). The page changes
  **on release only**; sliding back to the start cancels.
- **Finger to page** is read from the rest slots by one pure policy, since the drawn glyphs move.
- **Bubble label:** the page's or window's own name when it has one, else kind and number
  ("Home 1", "Terminal 2"). One line, ellipsized.
- **When pages do not fit:** neighbouring pages of one kind fold into one stacked glyph with a
  count; sliding over it steps through them in the bubble. Windows fold first. No scrolling, no
  shrinking.
- **Where:** Home only. The Terminal's opt-in page switcher key pops the same bar up over the
  keyboard for one use.
- **Dock pages:** the tick strip (`PageTickStripView`) is replaced by the mock's capsules under
  the pill: the current dock page wide and in the accent, the others small and neutral, all dimmed
  while sliding. The most-used page has no colour of its own.
- **Setting:** the existing Home page indicator setting keeps its key; its default now means the
  page bar, and it can still be turned off.
- **Ink:** glyphs and capsules take the legibility ink for the pill's ground (#297), not fixed
  colours.
- **Screen readers:** every glyph is a labelled button (today's row is hidden from them).

Open while building: which page moves `WallPages` permits (so which interleavings the fold rule
must handle), the pill's height against Home's grid, and its place under each dock style.

Round two (decided 2026-11-21, built on feat/page-bar):

- **Filled glyphs.** The bar's glyphs are filled shapes (`PageBarGlyphPainter`), the number and the
  Terminal's chevron cut out so the ground shows through. The current page is filled in the
  accent, the others in the ink; where the accent and the ink are close (CIELAB distance under
  25, `PageBarInk`) the others draw at half alpha, so the current glyph is the only solid one.
  The page switcher key's cap keeps the outline glyphs.
- **Stack.** A fold draws the front glyph with its count and two plates behind it, each layer cut
  apart from the one in front by a 1.5-unit gap, so it reads as a stack of cards.
- **Width.** On Home the pill hugs its glyphs, centred on the window's centre and kept inside the
  pane. The bar the page switcher key pops up on a workspace page spans the width with equal
  slots, on an opaque card (it stands over the terminal's text).
- **Place and room** (`PageBarBand`): the band is the air above the pill and the pill, plus 12dp
  under it while dock rows stand below; with no dock rows the pill's bottom stands on the dock's
  own bottom line (its edge gap). With the page bar hidden the band is nothing: Home's grid gets
  the height. While the in-app keyboard or the system keyboard is up on Home the pill hides and the
  band keeps its height.
- **Dock capsules** keep 6dp of air above the dock's own surface on every dock kind
  (`PageTickStrip.SURFACE_AIR_DP`); the dock is 4dp taller where they show.
- **Setting.** The page bar's switch is a row of the Home card's Layout list (between Status bar
  and Apps row), writing the same stored value as before (`app_launcher_home_page_indicator`,
  `dots` or `off`); the switch under More options is gone.

Round three (asked 2026-11-22, built on feat/page-bar-compact; replaces the bubble, the 2x lift,
the 1.25x spread and the band numbers of the sections above):

- **Compact.** The glyphs are 16.5dp (were 22). The drawn pill (the slab) is one glyph tall plus
  the bar's one small gap, 4dp, above and below (24.5dp, was 48); its ends are 4dp past the end
  glyphs. Home's slots are the fold's 40dp (were up to 53), so glyph centres are 40dp apart.
- **Touch stays 48dp.** The touch row is still 48dp tall and one 40dp slot per glyph; the band is
  that row, so it is 48dp over dock rows (was 64) and the slab stands 4dp above the dock rows. With
  no dock rows the slab's bottom stays on the dock's bottom line. The key's pop-up keeps its
  full-width slab and its touch row's bottom sits on the keys.
- **No bubble.** A slide lifts the resting glyphs themselves on the A–Z bar's wave (`ScrubWave`):
  the glyph under the finger rises until its bottom is the A–Z lift for the letter under the
  finger (18dp) above the slab's top and grows to 1.5x (24.75dp; 0.4 of the old 44dp bubble glyph
  would have been smaller than the rest glyph), its neighbours by the wave's envelope. Nothing
  shifts sideways. It rises and settles over the A–Z settle time (165ms) and follows the finger
  with no step between slots. While lifted the glyphs draw on the parent's overlay, so they can
  rise out of the bar; the bar draws none of them then.
- **Name.** The page's name rides 4dp above the lifted glyph, 13sp, one line, ellipsized to the
  pane less its insets, fading with the lift.
- **Ink off the slab.** A lifted glyph's ink moves from the slab's to inks solved on the wallpaper
  above the slab (one sample per dress pass, `PageMarkInk` on that ground, the name's bare-text
  ink), so no slab-inked glyph stands on bare wallpaper. The key's pop-up keeps its own inks.
