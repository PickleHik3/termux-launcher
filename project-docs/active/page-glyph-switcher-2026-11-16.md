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
