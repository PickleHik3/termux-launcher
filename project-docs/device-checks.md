# Device checks owed

Built, merged, not yet seen on a device. The list a drop or a cut is checked against first.

**Agent** items can be driven over adb: the emulator, or pong with the developer's go-ahead.
**Developer** items need a hand on the phone: feel, frame timing, a lock screen, taste.

One line per item: date · commit or PR · what to look at · what passes · how (device, route).
Verified items are **deleted**, never ticked; the PR, the commit or a bug is the record. An item
lands here in the same commit or PR as the change it belongs to, whenever a hand-over still owes
a device check. Before a drop or a cut, work through the Agent list and hand the Developer list
over; an item two releases old is re-judged and dropped if nothing rides on it. The file stays
under twenty items; past that, the oldest are re-judged first.

## Agent
- 2026-10-21 · commit "fix(chrome): a float restored from the Workspace lands with its keyboard"
  · minimise a focused Home float (keyboard up), go to the Workspace, tap the float's chip in the
  bar: the wall slides to Home, the keyboard stays up through the slide (no drop and re-raise),
  the float comes back focused at the height it was minimised at (ending above the keyboard),
  with no blank band above its output; the same with the keyboard down in the Workspace (it
  comes up for the float); from the palette's and the sessions drawer's restore too; afterwards
  a plain swipe Workspace → Home still lands with Home's keyboard down (unless Always on) ·
  emulator.
- 2026-10-21 · commit "fix(widgets): the empty page's hint steps out from under floats, drawer
  and palette" · on an empty Home page "Long-press to add widgets" is gone while a Home float
  shows on the page (anywhere on it, not only over the hint), while the app drawer is open or
  being dragged, and while the command palette is open; it is back once each goes (float
  minimised or closed, drawer shut, palette dismissed); no flicker of the hint as the drawer
  drags; the page's long-press menu still opens on an empty page · emulator.
- 2026-10-21 · commit "feat(dock): the Drawer tile is a toggle beside any design" (#97) · Drawer
  tile toggle beside Search (Layout editor, dock selected; Row B fits at 360 dp, the "Drawer" label
  not ellipsized, no Taskbar chip); the tile opens the drawer on Home and in a window, with
  Tray/Beads/Strip (on Beads it stands on its own bead, and the bead stays under the tile while a
  swipe carries the pages; judge it); drag it to start/middle/end and it stays; Taskbar users land
  on Tray with the tile at its old place (the Taskbar's lower 0.6 reflection is gone: the Tray's
  own) · emulator, fresh install then an install over a build with Taskbar picked.
- 2026-10-21 · commit "feat(dock): the Search field is a toggle beside any design" (#96) · Search
  field toggle beside the design picker (Layout editor, dock selected; Row B fits at 360 dp, the
  "Search" label not ellipsized, no Prompt line chip, no "Ask" anywhere); field under the apps row
  with Tray, Beads, Strip on Home and a window, no clipping (Strip + field is the tallest; Beads
  give way to the slab while the field is on, by design — judge the look); tapping it opens the
  palette; Prompt line users land on Tray with the field · emulator, fresh install then an install
  over a build with Prompt line picked.
- 2026-10-21 · commit "refactor(dock): remove the run strip" (#95) · Taskbar design: no run strip above
  the dock; the drawer tile still opens the drawer; Wall strip tiles still follow open windows ·
  emulator.
- 2026-10-21 · commit "feat(chrome): Home follows the Workspace's layout and shape" (#88) · A
  fresh install shows the same dock rows, dock height and glass shape (Style, Corners, Margin) on
  Home and on the first window, and the swipe between them does not re-dress the dock; minimal
  mode on from a window leaves Home as it was (see the next item); an upgrade from a build whose Home layout or Home
  shape differed keeps Home exactly as it was (Home layout on) · emulator, fresh install then an
  install over a pre-#88 build with a Home dock height or Margin set apart.
- 2026-10-21 · commit "fix(chrome): Home ignores minimal mode; the palette's Minimal action" · Minimal
  on in a window, then swipe Home: Home keeps its dock and layout; the palette's Minimal action
  toggles minimal from Home and from a window · emulator.
- 2026-10-21 · commit "feat(layout-editor): the Home layout line" (#89) · Layout editor row:
  labelled One layout / Home's own choice works; the Home/window preview toggle is back; the row
  fits at 360 dp; nothing else in the editor moved; the new line fits at 360 dp, default and Large
  text · emulator, also on an upgraded install whose Home layout was kept (Home's own checked).
- 2026-10-21 · commit "feat(statusbar): remove the house glyph from the bar" (#92) · The bar on
  the Workspace starts with the window chips, no house glyph, nothing clipped at the left; on Home
  no house beside the clock (since #90 no place mark at all, see the #90 item) · emulator.
- 2026-10-21 · commit "feat(dock): the Workspace's dock wears the Dock design" (#93) · Pick each
  design on a window's page → Home and the Workspace dock both wear it (Home layout off); Beads,
  Strip and Tray render with the extra keys row (Beads keep the slab while the extra keys or a
  keyboard share the dock's glass, by design; beads with the extra keys hidden); the Wall strip's
  tiles follow windows opened/closed on the Workspace; no dock re-dress on the Home↔window swipe;
  with the Home layout on and a different design, the bands and beads ease across the seam; the
  Workspace's Tray now has the tray's glass (rim, shelf, reflection) and, with the apps row next
  to the terminal, its dashes above the slab · emulator.
- 2026-10-21 · commit "feat(chrome): the status bar on Home with the same window chips" (#90) ·
  Home shows the launcher bar with the same window chips; a chip on Home slides to its window; ×
  closes it; status swipe works on Home and the shade still pulls from the top strip; no place
  marks, no gap beside the clock; the bar does not move on the Home↔window swipe; widgets are
  not covered (Home starts below the bar, lined up with the window's page across the swipe); the
  bar's + on Home opens a Workspace window and slides to it · emulator, Home layout off, then on
  with the bar placed apart.
- 2026-10-21 · commit "fix(chrome): the extra keys fold on the seam instead of the dock dipping" ·
  One layout, keyboard up and down: a slow swipe window → Home and back keeps the apps row, the
  A–Z row and the tray's glass on screen all the way; only the extra keys row shuts (opens) on
  the dock's edge; no jump of the A–Z row at settle; a Floating tray's bottom corners may read
  square mid-fold; Home layout on with a different shape still dips as before · emulator.
- 2026-10-21 · commit "fix(dock): the Wall strip keeps 14 dp inside the slab" · Wall strip on,
  Docked with no Margin and Floating, on Home and a window: the clock clears the left edge and
  the + tile ends clear of the right one, at least 14 dp inside the slab; on a Floating slab the
  clock still lines up with the first icon · emulator.
- 2026-10-21 · commits "feat(chrome): a minimised Home float is a window chip in the bar" and
  "feat(chrome): CPU and RAM in the bar on Home, not in the titlebar" (#91) · Minimise a Home
  float → it flies to its chip in the bar (down and away if the bar is hidden or the chip is
  scrolled off); tap that chip in the Workspace → Home, the float restored and focused; no chips
  row above the dock, and floats use the room down to the dock (Glass tray, Beads, Prompt line);
  CPU/RAM in the bar on Home, not in the focused float's titlebar, whose grip centres in the span
  the buttons leave; TalkBack on a minimised float's chip says "minimised" · emulator, status bar
  shown, then with the bar hidden in the layout (a minimised float then has no chip but the
  Taskbar's or Wall strip's); a minimised float's chip is dimmed with a dot, unlike an open
  window's (in the pill row and in the side column).
- 2026-10-12 · PR #65 · The widget picker opens without closing the launcher on Android 10 and 11
  (#62) · an API 29 or 30 emulator, or the reporter on the next drop.
- 2026-10-12 · commit "feat(keyboard): paste a fresh screenshot from the paste key" · **Failed on
  the emulator 2026-10-15:** with a fresh screenshot waiting, paste with a latched or held Ctrl or
  Alt still typed the screenshot's path; plain paste should give the clipboard text while the photo
  keeps showing (`~/reports/devcheck-2026-10-15/k2g_row.png`, `k2h_term.png`). The glyph swap,
  the path paste and the return to the paste glyph passed · emulator.
- 2026-10-13 · commit "fix(launcher): scale an icon pack as one, not icon by icon" · Still owed:
  an app's notification icon with a pack (needs a real app posting one; `cmd notification post`
  shows a system icon); a pack with no margin looks as it always did · emulator, Arcticons from
  `https://mirror.cyberbits.eu/fdroid/repo/com.donnnno.arcticons_1570.apk`.
- 2026-10-13 · commit "feat(tai): plan a load on the typical measured drop, not the largest" · E4B
  GPU cleanup is admitted with about 3.8–4 GB free (`tai logs`: `load_ok … gpu ctx=2048`, no
  `load refused`), and any refusal reads `needs X MB + 768 MB floor = Y MB free, Z MB available`
  with the GPU's figure. Seen loading from 6.4 GB free on 2026-10-15 (`ctx=4096`); the low-memory
  case is still unseen · pong with the go-ahead, a 0.25 s MemAvailable watchdog that unloads
  below 2 GB.
- 2026-10-14 · commit "feat(keyboard): edit, reset and create layouts in Settings › Layouts" ·
  Still owed: TalkBack reads the 48dp icons (force-stop TalkBack's tutorial first, then relaunch
  the app) · emulator.
- 2026-10-14 · commit "fix(keyboard): readable rebuilt layouts, no fallback flash on a first
  swap" · A later swap to a new user layout showed it at once, 2026-10-15; still owed: the very
  first swap right after creating the layout and adding it to the cycle, captured from the tap ·
  emulator.
- 2026-10-15 · PR #75 · Touch light on a sheet card row. The terminal action sheet's Kill process
  row is a flat light list with a grey press highlight, no rim light or glow (2026-10-15,
  `~/reports/devcheck-2026-10-15/a9holdcrop.png`); find a sheet whose rows are centred glass
  cards (the `GlassPress` overload's callers) and hold one: the nearest rim lights, a faint glow
  sits under the finger, both fade within a quarter second. Nothing lights under Reduce motion,
  lazy mode or battery saver · emulator.
- 2026-10-15 · PR #75 · Edge clarity on the Widgets and Display pages under Mist, and with RTL on
  (`debug.force_rtl` did not mirror the app; use the developer option or an RTL locale): the bent
  edge shows a clearer wallpaper, the frost inside is unchanged, corners and hairlines clean. The
  pane slab under Floating at Clear passed, also at font scale 1.3 · emulator.
- 2026-10-15 · commit "feat(glass): draw the rim as a hairline and bend on Haze's edge profile" ·
  Clear, Mist and Tint at their shipped values (an install on the old Looks re-adopts them on
  launch) on the `glass-city` and `glass-flowers` wallpapers: crop a pane corner (one pane and
  split) and the dock's top edge (Docked and Floating, keyboard up and down). Passes when the rim
  is a faint hairline, lit along the top edge and fading round the corners; the picture is
  continuous with the wallpaper outside at the outline; the bend is a narrow ring and the face
  inside it stays flat; no milky band anywhere; Clear shows a faint red/blue glint at the corners
  only; a small pane shows no picture from its opposite edge; no line on a seam (keyboard over
  the strip under the pill, status over the window bar); Solid unchanged · emulator.
- 2026-10-15 · commits "fix(terminal-view): resolve symbol_map and fallback faces inside scaled
  text" and "fix(terminal-view): spread a wide icon across the blanks after it in scaled text" ·
  `/tmp/launcher-scaled-symbols-2026-10-15/scaled-icon-cases.sh` (run with `bash`): no tofu at
  any scale; a default-width (w=0) scaled icon, e.g. `66;s=2;<icon> `, stays one scaled column
  wide and centred in it (each character is its own block, so it does not spread); an explicit
  `66;s=2:w=2;<icon><space>` block, the form dawn's ribbon uses, draws the icon spread over both
  columns, centred, about letter height; plain-size icons and Latin in a scaled block are
  unchanged · emulator.
- 2026-10-15 · commits "feat(appearance-editor): Depth alone in the Glass group, Looks on the
  hairline" and "feat(appearance-editor): Depth as the global set's last slider, no Surface |
  Glass toggle" · Look › Custom with nothing tapped has no toggle in its heading row; Depth is the
  seventh, last slider after Corner radius, its legend whole and every column whole (also at font
  scale 1.3 on a 360dp screen). Depth is absent from every element's set, and absent from All
  surfaces in Lazy mode or with no wallpaper set from the launcher. After a drag the
  readout matches `fancier_glass_bend`/`_edge_width` (`run-as` the prefs) and no other key moves.
  Clear, Mist and Tint picked from the stops read Depth 90/80/70, and a Depth nudge and back lands
  on the Look's stop again. Drag Depth to 92 on Clear, tap an element and back to All surfaces: it
  still reads 92; pick a Look and it reads that Look's position. A Custom saved with the old six values (e.g.
  13/32/60/20/20/90) opens without changing any key until Depth moves · emulator, with a
  wallpaper set from the launcher.
- 2026-10-16 · commit "test(surfaces): pin the first run on Clear, on Depth's curve" (glass plan
  Round 0) · Fresh install (`ad clear`, then a wallpaper set through the picker): the Look page
  shows Clear selected and Depth reads 90, and `run-as` the prefs holds `fancier_glass_bend` 27
  and `fancier_glass_edge_width` 18; one Depth step down writes 26/17 and the dock's bend ring
  narrows by a step, not to a third. Fails if the page says Custom with Depth 34: something wrote
  a preference before the first-run check, and the install was taken for an upgrade · emulator.
- 2026-10-16 · commit "feat(glass): the gathered light is the only glass edge" (glass plan Round
  1, always on) · Android 13+, wallpaper set from the picker, Clear, Mist and Tint: every glass
  surface shows one edge, the wallpaper's colour brightened, brightest top-left and faint all
  round, and no white line or gradient stroke beside it. Crop each: the dock (Docked and capsule,
  where the capsule must still clip to its rounded outline), the under-pill strip, the keyboard,
  the window bar capsule, a Floating pane with the border on (one edge, no rim), a split's focused
  pane (its active colour still shows), a wall page's corner tab (now lit), the frame gutter in a
  joined Docked frame (no glow along the screen's edges). No bright line on a seam (keyboard over
  the strip, status over the window bar). Over a pale sky the edge stays blue, not white or cyan.
  With no Look (Fancier Glass off, a wallpaper not set from the picker) the dock and the strip
  still show the gathered edge and no rim under it, and the other surfaces keep their rims ·
  emulator, `glass-city` and `glass-flowers` and a pale-sky wallpaper, Docked and Floating,
  keyboard up and down.
- 2026-10-16 · commit "feat(glass): the gathered light is the only glass edge" · Android 12 (or a
  forced plain draw) looks exactly as on `pre`: every rim, the dock's rim under its glass, the
  panes' gradient rim · an API 31/32 emulator.
- 2026-10-17 · commit "fix(glass): every edge follows the glass on screen, not the rule" · The
  window bar capsule: one edge on a cold start (no rim beside the gathered light on the first
  frame after the frost lands), and its stroke back the moment the frost goes (Fancier Glass off,
  status blur radius 0, the bar moved onto the dock's plank and back). The dock: its rim under the
  glass shows while a blur is in flight on a fresh wallpaper and goes once the glass is up. A
  Floating pane: its rim shows while its slab is plain (a fresh blur in flight, a corner-masked
  page) and goes when the slab refracts · emulator, logcat clean.
- 2026-10-16 · commit "feat(glass): the gathered light is the only glass edge" · Settings › Theme
  & fonts has no Adaptive glass row and nothing else on the page moved (crop the page before and
  after) · emulator.
- 2026-10-16 · commit "feat(glass): the gathered light is the only glass edge" ·
  `GlassRefractionAgslInstrumentationTest` passes, `aPaleSkysOutlineKeepsItsBlue` and the lit
  top-left corner included (only a device proves the AGSL compiles) · emulator.
- 2026-10-16 · commit "feat(keyboard): a touch engine that learns where your taps land" ·
  A swipe that starts in a moved border strip keeps its own key's swipe legend: with Adaptive
  touch accuracy past its warm-up and a border moved (the low strip of `b` typing a space), swipe
  up from that strip and get `b`'s upward legend, never the space bar's; a plain tap there still
  types a space · emulator, warm-up driven with scripted taps.
- 2026-10-16 · commit "feat(keyboard): a touch engine that learns where your taps land" · A forced drift pause shows in Settings and resume restarts the warm-up: set
  `"state":2` under `"engine"` in `files/keyboard-tap-model.json` (`run-as`, before any
  force-stop), reopen Settings → Keyboard → Typing: the status row reads Paused with the taps
  learned; tap it: Learning, and the file's `"wt"` is 0 · emulator.
- 2026-10-16 · commit "feat(keyboard): a touch engine that learns where your taps land" · The status row reads whole at font scale 1.3 in all three states (Learning, Active,
  Paused), its summary wrapping rather than clipping, and dims with the switch off · emulator,
  `font_scale 1.3`, force-stop after the change.

- 2026-10-17 · branch feat/glass-controls-unify, "feat(glass): one set of glass controls in Look › Custom" · Look ›
  Custom from Clear, then Mist, then Tint: Tint 0→100 adds visible accent each time (Mist light
  theme too); Hue Secondary/Tertiary changes the tint; Glass 0→100 goes clear→frosted with
  no sandy grain; Vibrancy 0.7x/1.5x changes the glass colour but not the bare wallpaper beside
  it; the global heading row shows "All surfaces" with Hue beside it, whole at 360dp and at Large
  text; Contrast moves with wallpaper colours off; Glass released at 5 reads 5 on reopening; no
  Shade column anywhere; Undo puts all of it back · emulator tl16, dark and light theme,
  screenshots before/after per control.
- 2026-10-17 · same branch · Migration: an install on Clear, Mist, Tint and Solid from the pre build
  keeps its stop and its colour after the update (Clear and Tint change blur and grain by design);
  a Custom with Tint 40 on Mist comes up at Tint 0 and the same colour; a saved Custom applies as it
  looked · emulator, a pre-build store per Look.
- 2026-10-17 · same branch · Edge outline: the gathered edge on dark Mist (60%, gain 2.25), light
  Mist (35%), the dock, sheets and Solid reads about as strong as on Clear, never white-clipped,
  on a pale and a dark wallpaper ·
  emulator tl16 (Android 13+), screenshots of each surface.
- 2026-10-17 · same branch, "fix(glass): the terminal's ink follows the face its panes are drawn
  with" · Light theme, wallpaper on, Terminal colours dynamic: Solid shows dark terminal text on its
  near-white panes, after switching to it from Clear and after a fresh start; a Clear-derived Custom
  dragged Opacity 0→100 in the editor flips the text to dark within a moment of release (and back to
  pale at 0), with the keyboard shown and hidden first; dark theme unchanged · emulator tl16,
  screenshots before/after.
- 2026-10-17 · same branch, "feat(glass): Edge is a slider" · Look › Custom, glass edge drawn
  (Android 13+): the global set reads Glass, Opacity, Tint, Edge, Vibrancy, Depth, whole at 360dp
  and Large text; Clear opens at Edge 40 and looks exactly as before (compare a `pre` crop), Mist
  and Tint at 0. Edge 0, 40, 70, 100 on Clear and on Mist, both themes: each step a plainly
  stronger split; seams (keyboard over strip, status over window bar) show no line · emulator
  tl16, top-edge and corner crops per value. On pong the first bold edge read as a red band on a
  dark wallpaper, and its successor as a separate yellow-green border just inside every
  surface under a gold Look; "fix(glass): Edge past 40 splits the rim's light" replaces both.
  Recheck Clear, Mist and a gold-tinted Look at Edge 100 over Celestial Field (dark) and a pale
  wallpaper: every edge of the dock, panes, status band and keyboard wears a cyan-blue px on the
  outline itself and an orange one right against it (about 1.5 dp in all), no gap and no line
  further in, both visible on the dark and the pale wallpaper, the cool side cyan-blue (not
  green) under the gold tint, the edge as a whole not redder or bluer than at 40, nothing
  white-clipped; Edge 40 identical to before (same crop boxes as `codex-emu4/sheets`).
- 2026-10-17 · same branch, "feat(glass): retire Shade" · An install whose store or saved Custom
  had Shade 60 comes up with the face as at Shade 0 and no Shade column; the chrome, terminal, A-Z
  and key legends readable on Clear and Mist over a pale and a dark wallpaper, both themes ·
  emulator tl16, a store with `surface_glass_shade` set (`run-as`, before the force-stop).
- 2026-10-17 · same branch, "feat(keyboard): a Keys slider" · Keyboard selected in Look › Custom:
  Glass, Opacity, Tint, Keys, Radius, Spacing whole at 360dp and Large text; Keys stands at the
  theme's cap opacity on a fresh store; Keys 0, 50, 100 over the dark grass and a pale wallpaper,
  both themes: every legend (letters, Ctrl, Alt, Fn, gear, Shift, Backspace, Enter, arrows)
  legible on its cap, at 0 on the glass itself; a Look stop and Undo put the theme's value back ·
  emulator tl16, keyboard crops per value. **Failed on the emulator 2026-10-17:** light theme,
  keyboard Glass 20, Opacity 35, Keys 0 over the dark grass: dark legends, Ctrl, Alt, 123+, arrows
  and Enter nearly invisible, the number row faint (Glass 75 read). Two causes: the keyboard's rows
  were measured off the dock's frame (its 25 dp blur mixed the lit grass into the bottom rows),
  and the function legends are drawn secondary (toward mid grey, black as #404040) but were judged
  as the full tone. "fix(keyboard): legends at Keys 0 judged on the keyboard's own frame, as drawn"
  reads the rows at the keyboard's radius and chooses (and veils for) the secondary rendition.
  Recheck that route at Glass 20 and 75, Keys 0, then Keys 50: every legend legible on every row
  (a light veil rising where needed, not milky past that); dark theme the same route; the theme's
  own key opacity over dark grass and a pale wallpaper unchanged in look.
- 2026-10-17 · same branch, "fix(keyboard): legends re-judged when the keyboard's own frame
  lands" · Light theme, keyboard card, Keys 0, Opacity 10, Tint 0 over the dark grass: tap and
  drag Glass 20→0, release, capture at once and again 1 s later: the second capture shows every
  legend (letters, Ctrl, Alt, Fn, 123+, Shift, arrows, Backspace, Enter, the number row) legible,
  with no further edit: pale legends with no milky veil ("fix(keyboard): legend tone chosen on the
  bare face"), the same tone a fresh start on those settings shows; Ctrl, Alt and the arrows are
  #BFBFBF at best and may read a little under 4.5:1 there (unit model 3.96). A pale wallpaper
  takes dark legends, no veil; a mid grey takes the better tone with the least veil; a face near
  the line does not flicker between tones while Glass or Opacity is dragged.
  The same in the editor's live preview; no flicker or repeated repaint after the release
  (gfxinfo flat once settled) · emulator tl16, two crops per step.
- 2026-10-17 · same branch, "feat(looks): Mist is 35% on light glass" · Light theme: picking Mist
  shows Opacity 35 and the stop reads Mist; switching the theme dark→light→dark with Mist on moves
  it 60→35→60 and the stop stays Mist; a Custom at 50 keeps 50 across the switch · emulator tl16,
  editor screenshots per theme.
- 2026-10-17 · same branch, "feat(editor): a Hue picked at Tint 0" · From Clear, Custom: pick
  Secondary in the Hue pill and the Tint column jumps to 40 with the colour showing; at Tint 15 a
  Hue pick leaves 15; Undo returns to the open state · emulator tl16, editor screenshots.
- 2026-10-17 · same branch, "fix(glass): keyboard-area labels" and "fix(glass): keyboard legends
  are chosen on every row's face" · **Failed on the emulator 2026-10-17 before the row fix:** light
  theme, Celestial Field, Custom from Solid at Opacity 10 and 0: the keyboard's Ctrl, Alt, Fn, gear
  and right-hand cluster stayed dark on their see-through chips over the near-black grass under the
  bottom row (Mist light about 2.5:1); Opacity 100 light and Opacity 10 dark passed. Recheck that
  route at Opacity 0, 10, Mist and 100: every keyboard legend legible on every row, a light veil on
  the keyboard where no legend tone reads otherwise. The A-Z letters stay pale there: the row under
  them averages 0.01-0.03 luminance on that screenshot, so pale is the measured answer; judge by
  eye. Then the first round: dark wallpaper, Opacity 100 dark labels, 10 pale; dark theme on a pale
  wallpaper the mirror; the A-Z bar on the plank and its own capsule · emulator tl16, row crops.
  **After the row fix, 2026-10-17:** Opacity 36 and dark theme passed, no veil loop; Opacity 0 and
  9 light still failed (Ctrl, Alt, Fn, gear, Shift, Backspace, right cluster dark grey on dark
  grass; 16 readable). Cause: the keyboard draws a cap's gradient with the fill's alpha on the
  paint too, so a 124/255 function chip shows about 0.23 of its colour (measured off those
  screenshots), not the 0.49 the legend was chosen on. "fix(glass): function legends on the chip
  as drawn" chooses them on the drawn chip and raises the function fill where no tone reads.
  Recheck light theme over the dark grass at Opacity 0, 9, 16 and 36: function legends legible
  (pale at 0, or on a fuller chip), letter keys as before, no milky keyboard; dark theme at 0 and 9.
- 2026-10-17 · same branch, "feat(glass): Tint colours the glass across its range, Clear
  included" · Clear, Custom: Tint 0, 40, 100, dark and light theme, a grey-ish and a colourful
  wallpaper: each step is plainly more accent-coloured behind every glass surface (dock, status,
  keyboard, panes with blur), the bare wallpaper beside the panes unchanged, no white lift; Mist and
  Solid at Tint 100 a strong accent face, readable text; the Tint Look stop renders as before
  (compare against a `pre` screenshot) and still reads Tint; terminal, A–Z, extra keys and key
  legends readable at Tint 100 on Clear, Mist, Tint and Solid, both themes; Hue pick at Tint 0
  shows 40 with visible colour on Clear. Migration: a format-1 store on the Tint Look comes up on
  the Tint stop at 35, same colour; a Custom with Tint 100 comes up at 35 · emulator tl16
  (Android 13+), screenshots per value.
- 2026-10-17 · same branch, "fix(editor): choosing a Look resets every surface to it" · Look ›
  Custom, keyboard tapped: move Glass, Opacity, Tint and Keys well off; tap Clear: the keyboard's
  glass and caps are Clear's at once (compare a fresh Clear crop); back at Custom the keyboard
  card shows the moved values again (this visit's Custom comes back); Undo puts back the look the
  editor opened on. Same with the dock's Glass · emulator tl16, keyboard crops before/after.
- 2026-10-17 · same branch, "fix(editor): Custom comes back whole after a Look is saved" · Look ›
  Custom: keyboard Glass 0, Opacity 90, Tint 100, Keys 0, terminal Glass 0, Opacity 90, Tint 100;
  Done. Reopen, tap Clear, Done; force-stop the app, reopen the editor: it opens on Clear; tap
  Custom: every card shows those values again and the keyboard and terminal look as they did
  (compare crops); tap Clear: all of Clear at once. Undo after a Custom edit puts back the look
  and the Custom the editor opened with · emulator tl16, crops before/after.
- 2026-10-17 · same branch, "fix(glass): never recycle a wallpaper frame a surface was handed" ·
  With an X11 pane showing on the wall, set a wallpaper from the in-app picker, then another with
  `launcherctl wallpaper set <file>`; repeat both with the Look editor open, and once with a
  widget pane showing: no crash (`adb logcat -b crash` empty, no `Cannot create BitmapShader for
  recycled bitmap`), and the panes' glass shows the new picture after it lands · emulator tl16,
  release build, screenshots before/after each set.
- 2026-10-17 · same branch, "fix(wallpaper): a new wallpaper re-styles in place, no recreate" ·
  With wallpaper mode on and the Look editor open, `launcherctl wallpaper set <file>`: the editor
  stays open on the same page with its state, its preview shows the new picture, and logcat has no
  `Recreating activity`; turning wallpaper mode off and on (Settings) still restyles the theme ·
  emulator tl16. Then on pong: Firefox running in an X11 display pane, set a wallpaper from the
  in-app picker, then with `launcherctl wallpaper set`: the display keeps running, Firefox still
  drawn and answering touch, no `Recreating activity` · pong with the go-ahead.
- 2026-10-17 · same branch, "fix(wallpaper): launcherctl wallpaper set runs the picker's in-place
  apply" · **Failed before it on the emulator:** with the mode on and the Look editor open,
  `launcherctl wallpaper set` left the pid but changed the window (4→5→6) and closed the editor.
  Now: run `adb logcat -b events -c`, then the same set twice with two pictures of different
  colours: the editor stays open, the window id holds, and `adb logcat -b events | grep -E
  'am_relaunch|wm_relaunch|am_destroy'` shows nothing for TermuxActivity. If a relaunch still
  shows there, repeat it with the in-app picker from the Overview: a relaunch on both is Android
  restyling for the new wallpaper colours, not our code · emulator tl16.
- 2026-10-17 · same branch, "fix(appearance): the surface and its editor session survive a
  configuration relaunch" · Open Look through the Overview, tap the keyboard in the frame, move a
  slider (Undo shows), then `launcherctl wallpaper set` with a picture of different colours. When
  `am_relaunch`/`wm_relaunch` shows for TermuxActivity, the editor comes back on Look under a
  short colorSurface cover (no hop from the Overview), keyboard still selected, Undo still shown;
  Undo puts back the look at the first open; Back goes to the Overview. Repeat from a corner
  tab's Layout door (Back then closes) and once with dark mode toggled (`cmd uimode night yes`)
  · emulator tl16.
- 2026-10-17 · same branch, "fix(x11): a relaunch's destroy never reads as the display stopping"
  · Firefox in the Display place: `launcherctl wallpaper set` (and dark mode toggled) relaunches
  the activity; the Display place comes back drawing Firefox and answering touch, the place bar's
  display dot stays on, a new terminal session still has `DISPLAY` set. If the display still
  stops, note whether `pgrep -f termux-x11` still lists the server and whether Firefox's process
  is alive: the launcher side lets go of nothing but its own view · pong with the go-ahead.
  Seen on pong (release 2f5596e2b): Firefox survived, but `printenv DISPLAY` in a pane opened with
  `launcherctl pane open` after the relaunch was empty. Re-check on the build with "fix(x11): new
  sessions' DISPLAY follows the server, not the activity": after the wallpaper change and after the
  dark-mode toggle, a new pane prints `:0` (the opt-in on); then Stop the display and a new pane
  prints nothing; and with the display stopped first, a relaunch neither starts it nor gives a new
  pane `DISPLAY` · pong with the go-ahead, emulator first.
- 2026-10-18 · same branch, "fix(x11): the Linux app runner outlives a configuration relaunch"
  · Firefox on the X11 display (from the drawer), then set a wallpaper of different colours from
  the in-app picker, again with `launcherctl wallpaper set <file>`, and once with a theme switch
  (dark mode toggled): after each, Firefox's pid is the same (`pgrep -f firefox` before/after),
  its window is drawn and answers touch on the Display place, and logcat has no
  `X11LinuxAppRunner: firefox exited with`. If it still dies, that line's code is the lead: 143
  or 137 is a signal (look for a sender), 0 is Firefox closing itself (look for a close or quit
  reaching it). Also: an Xfce desktop running, relaunch, tap Xfce again (only shows it), tap
  another desktop (asks first) · pong with the go-ahead, release build.
- 2026-10-18 · same branch, "fix(x11): a relaunched activity's Display page hears its display
  runs" · (The in-app picker case passed on pong 2026-10-18 at 2f5596e2b.) Firefox on the X11
  display, resting on the Display place: `launcherctl wallpaper set <file>` with a picture of
  different colours, then once with dark mode toggled. After each the Display place
  is still on the wall and draws Firefox (no "Start display" empty state), answers touch, the
  place bar's display dot is on, and the window chips list Firefox. Also once relaunched from the
  terminal place, then swipe to Display: same. Then stop the display, relaunch: the page shows
  its empty state, nothing restarts · pong with the go-ahead, release build.
- 2026-10-18 · commit "fix(widgets): a relaunch no longer hands a widget's saved Bundle to
  another view (#83)" · With two or more app widgets on Home (widget ids that are small numbers,
  as on a fresh install), several panes open, the Appearance editor open and the Display page
  showing, relaunch the activity with `adb shell cmd uimode night yes` then `no`, and with
  `launcherctl wallpaper set <file>` twice: no crash and no "Wrong state class" in logcat; the
  editor reopens where it was and the widgets come back. Repeat on Android 12 (API 31) if an image
  is at hand · emulator.

### Wall rework: orchestrator check of e64d95a92 (emulator, 2026-10-18 10:55)
Seen: after the Display chip in the Home palette started the X server (one process) and Stop with
its confirm killed it (none), typing reached the terminal window's prompt ("echoecho") and, in
the same process, the Home palette's query ("gtk"). The keys-dead-after-display defect is closed
on the emulator; owed on pong only.

### Wall rework: device-checker pass of e1cc519c0 (emulator, 2026-10-18 10:45)
Seen (owed on pong only): always on in the same process (no notice while the editor is up; after
Done the keyboard is up at once and the notice shows once, inside the screen; dragging the block
onto the tray's eye and Done lowers it with no notice); a Home float opens and closes; the pin; the
home press to page 1.
Failed (fix in flight): after a display session (Stop, with its confirm), typed keys reach neither
the palette's query nor the terminal until a relaunch; the first fix (b47894709) did not take.
Noticed: the "Text selected / Drag the handles…" hint stays on Home's top-right after a terminal
text selection; in the widget edit frame the X at 77,212 removes the widget outright.

### Wall rework: device-checker pass of 2a4f5741a (emulator, 2026-10-18 10:15)
Seen (owed on pong only): the always-on notice fits the screen and the widget keeps its height;
the keyboard block stands on Home's canvas and can be put away; in landscape the side dock starts
below the floats' bar; a Linux app (gtk3-demo with NoDisplay=false) listed in the palette, launched
from it and by launcherctl, drawn in the display window; Display and Terminal listed as apps; the
Display row opens the bare display; Stop (with its confirm) kills the server and the display leaves
the row twice; the Terminal pin after `exit`; a float's Close; the palette swipe and Back.
Failed (fixes in flight): after Done with the keyboard element shown, the keyboard did not rise in
the same process (only after a cold start); a drop on the tray strip does nothing (only the eye
button takes the block); the notice shows over the editor's controls; after a display session the
palette takes no typing until a relaunch; a bare "e" once opened the sessions drawer.
Not driven: the empty-query Linux apps section of the palette; the other gtk3 apps.

### Wall rework: device-checker pass of 482a9f612 (emulator, 2026-10-18 09:45)
Seen (owed on pong only): Ctrl+Alt+F floats a one-pane row window onto Home, docks it back and
floats it again with no toast; Close on a Home float does not crash; the Terminal pin stays through
`exit` and force-stop (the store holds launcher:entry); the handle menu's title reads the full
name; two Home pages (corner tab's plus), a float pinned to page 2 with chip "home · 2", the seam
from page 2 to window 1, the home press to page 1 with the float left on page 2; landscape and
back keep a float's shape per orientation; the drawer lists the float ("Float · page 2" under its
window row), its tap restores, the footer reads Home then Save sessions… and Home goes to page 1.
Failed (fixes in flight): always on cannot be put away after Done (the Home tray shows no keyboard
element) and the one widget stretched from 805 to 1363 px; the always-on notice's pill runs off the
right edge; in landscape the dock's Terminal icon covers the float chip; Linux apps are not listed
(palette, launcherctl launch 404) and a hand-started display's Stop does not stop it.
Not driven: the Display entry, a real app on the display, the rail dock, page swipes with a float
showing.

### Wall rework: device-checker pass of 651ea9d7f (emulator, 2026-10-18 09:10)
Seen (owed on pong only): #81 migration per usage mode (terminal → Start in = workspace and a cold
launch lands on window 1; home and display → home; old keys gone; version 1); the Start in row
under search and its choice standing a force-stop; no Display switch under Settings › Display;
`exit` in the only window with the empty row and the Terminal entry and `launcherctl window open`
opening a window; a Home float from Home's long press with chip, Minimise/Maximise/Close, the
handle menu with Dock and Dock making window 2; Minimise to chip and chip restore; Maximise reading
Restore size; a small float lifting over the keyboard and a full-size one sitting on it; the palette
gesture, its Home set and a completed action closing both; always on through the Layout editor
(reached by intent) keeping the keyboard up through Done and a window round trip.
Failed (fixes in flight): Close on a Home float crashed (IndexOutOfBounds in WSession.currentWindow
via removeHomeFloat); Ctrl+Alt+F on a one-pane window toasted "Index 1 out of bounds" and floated
nothing; the pinned Terminal entry vanished from the dock after `exit` while the store still held
it; the handle menu's title truncates ("ho…"); the always-on notice clips at the right edge.
Not driven: the display items (no Linux app was installed), two Home pages (page pin, seam from
page 2, chip page numbers), rotation, the drawer's float rows, large text and dark theme.

### Wall rework: emulator pass of 2026-10-18, morning (debug builds up to b0b725895)
Also seen by the orchestrator, so owed on pong only: the three-button titlebar (Move window handle,
Minimise, Maximise, Close) on a Home float; a full-size Home float shrinking above the keyboard
with its cursor in view; the status bar overlaying Home (canvas band and grid keep their bounds
with and without the bar); `exit` in the only window sliding Home in with no shell behind it and
the Home swipe then stopping; the Terminal pin surviving that exit and a force-stop; the Terminal
entry's tap on an empty row opening window 1 and sliding to the Workspace; the palette gesture
(up swipe on the page's free area) opening the Home palette with the keyboard, and Back closing
both. Not seen: the handle's long-press menu, a float lifted by the keyboard, Ctrl+Alt+F back
from the row, the page pin across a swipe, the drawer's float rows, the migration from each usage
mode, launcherctl window open on an empty row.

### Wall rework: emulator pass of 2026-10-18 (debug builds up to 47e7893ae, tl16, 420 dpi)
Seen, by the orchestrator, so these parts of the items below are done on the emulator and owe pong
only: home press lands on Home page 1; Home at rest shows no status row, extra keys or slab, the
dock with the pinned Terminal entry and the A–Z index; the seam Home → window 1 by a plain swipe;
window 1 ↔ window 2 by a top-air drag; the drag back from window 1 to Home with a 700 ms injected
drag (a 350 ms one did not page: the emulator batches injected moves late); the Terminal entry's
tap goes to the Workspace; a split pane floated by Ctrl+Alt+F wears the titlebar (Dock, Maximise,
Close, no Minimise) and Maximise/Restore size work; Home long-press › Terminal opens a Home float
full-size under the bar with its titlebar, shell over glass, keyboard up and the chip "home · 1";
Minimise hides it and the chip restores it; Dock makes it window 2 and slides to the Workspace.
Not seen: everything else listed under the sections below.

### Wall rework #79 titlebar
- 2026-10-17 · commit "feat(panes): a glass titlebar on every float" · Float a pane (Ctrl+Alt+F):
  drag its titlebar and the pane follows; Maximise fills the window and the button reads Restore
  size, a second tap puts it back where it was; Dock tiles it back; Close ends its shell. The
  resize corner still works · emulator, then pong.
- 2026-10-17 · commit "feat(panes): a glass titlebar on every float" · The scratchpad
  (Ctrl+Alt+`) shows Minimise, which hides it with its shell alive (the toggle brings the same
  shell back); a window's floating pane shows no Minimise · emulator.
- 2026-10-17 · commit "feat(panes): a glass titlebar on every float" · The strip's glass matches
  the pane under it under Docked and Floating Style, with glass on and off: same frost, tint and
  corners, the wallpaper staying put under the strip while the float is dragged, and the focused
  float's strip wearing the active rim · emulator screenshots, then pong.
- 2026-10-17 · commit "feat(panes): a glass titlebar on every float" · A tap just under the strip
  lands on the pane's first row (no move starts), and a hold-and-drag on that row still reaches
  the program · emulator.
- 2026-10-17 · commit "feat(panes): the titlebar's three buttons, Dock in the handle's menu" ·
  On a window's floating pane, a Home float and the scratchpad: the strip shows, at its trailing
  end, Minimise (a chevron down; Home float and scratchpad only), Maximise, Close, and no Dock. A
  long press on the handle (held still) opens a small menu under the strip with the window's name
  and Dock; Dock tiles the pane back, sends the Home float into the row, or docks the scratchpad.
  A drag started on the handle still moves the float and opens no menu · emulator, then pong.
- 2026-10-17 · commit "feat(wall): a full-size Home float shrinks for the keyboard" · A Home float
  born full-size (and one maximised): bring the keyboard up and its titlebar stays at the room's
  top while its bottom edge sits on the keyboard's top, the shell reflowing to the shorter pane;
  close the keyboard and it grows back to full size, still Restore size when maximised. A float
  resized smaller than the room lifts over the keyboard, keeps its size and never rises past the
  bar · emulator, then pong.
### Wall rework #79 Home floats
- 2026-10-17 · commit "feat(wall): Home floats over the Home page they were opened on" · Long
  press an empty cell on a Home page › Terminal, and the Terminal entry's long press › New float
  here (first row, above New window): a float opens full-size between the status bar and the
  dock, focused, keyboard up · emulator, then pong.
- same · Drag the float's titlebar and resize from its bottom-right corner; open a second float
  and tap the first: it comes to the front with the active rim; rotate and rotate back: each float
  comes back to its shape for that orientation · emulator.
- same · Pinned to its page: swipe to the next Home page and back (the float leaves and returns
  with its page), and swipe past the last page into the Workspace and back across the seam (the
  float slides with Home and never shows in the Workspace) · emulator.
- same · Keyboard lift: with the keyboard up, run `seq 200` so the cursor sits on the bottom row:
  the focused float rises so the cursor row stays above the keyboard, never resizes (no reflow),
  and settles back when the keyboard closes · emulator.
- same · Focus: tap inside a float (it takes the keyboard); tap on Home beside every float (Home
  takes focus and the keyboard closes); close and minimise the focused float (the keyboard closes
  too) · emulator.
- Superseded 2026-10-19 (Home wears no status bar; see "10-19 Home fixes"): 2026-10-17 · commit "feat(wall): the status bar on Home carries the floats' chips" · The first
  float brings the status bar down from the top in its compact form; it stays while a float is
  open or minimised and slides up when the last one closes. Note whether the widget grid moves
  when the bar arrives (the bar takes its room today) · emulator screenshots before/after.
- Superseded 2026-10-19 (Home wears no status bar; see "10-19 Home fixes"): same · Chips on Home are floats only, from every page, each with its page (" · 2"); Minimise
  flies the float into its chip; from another Home page, tap the chip: Home pages to the float's
  page and the float comes back focused. In the Workspace the floats' chips follow the windows'
  · emulator.
- 2026-10-17 · commit "feat(wall): Dock and Ctrl+Alt+F move windows between Home and the row" ·
  Dock on a Home float: it becomes a tiled window right after the current one and the Workspace
  slides in; Ctrl+Alt+F on a one-pane window floats it onto the Home page last shown (a window
  of several panes still floats its focused pane inside it); Ctrl+Alt+F on a focused float docks
  it · emulator.
- same · Home press with floats on pages 1 and 2 while on page 2: Home page 1, floats where they
  were, keyboard closed; closing the last float leaves the Workspace's windows untouched · emulator.
- 2026-10-17 · commit "feat(wall): the palette's Home set and the drawer's floats" · On a Home
  page the palette shows the Home set: Open terminal here (a float on this page), Go to Workspace,
  Minimise all floats, Restore floats, Add widget, Layout editor, and a float's row restores it.
  The sessions drawer lists floats under the session ("Float · page 2"; a tap pages Home and
  restores it) and its Home row goes to Home page 1 · emulator.
- Superseded 2026-10-19 (Home wears no status bar; see "10-19 Home fixes"): 2026-10-18 · commit "feat(wall): the status bar overlays Home" · The widget grid does not move
  when the bar slides in with the first float, nor when it slides out after the last one closes:
  screenshot a widget's top edge before, with the bar, and after; the bar draws over the grid and
  takes the taps on its chips · emulator screenshots, then pong.
- Superseded 2026-10-19 (Home wears no status bar; see "10-19 Home fixes"): same · A float born with the bar already there (the second float) and the first float, born as
  the bar arrives, both sit with their titlebar just below the bar's bottom edge, not under it;
  with the keyboard up and the cursor on a lower row, a smaller float lifts no higher than that
  edge · emulator.
- same · Home ↔ Workspace with a float open: the terminal in the Workspace keeps its rows below the
  bar after the slide settles, and the grid on Home is back at its full height on return · emulator.

### Wall rework #77 row
- 2026-10-17 · commit "feat(wall): one row …" · The seam both ways: with three windows and the
  current one the third, a plain swipe left on the last Home page slides window 1 in (not window
  3); a border drag right on window 1 slides the last Home page in · emulator, two Home pages.
- 2026-10-17 · same commit · Window to window by the border drag: the departing window carries on
  from where the finger left it and the next one arrives from the side with no jump back at the
  release, glass on and off; Home or the Display page never shows during a drag toward another
  window; glow, grabber, plank tilt and sink look as before · emulator.
- 2026-10-17 · same commit · A Home page pages by a plain swipe anywhere, its side borders do not
  glow or page, and the swipe on the last Home page stops (rubber-bands) when there is no window
  and no Display page (only reachable while no terminal session exists, see the hand-over note) ·
  emulator.
- 2026-10-17 · same commit · Home press: from a window → Home page 1 (slides); from Home page 2 →
  Home page 1, overlays closed; from another app (open an app, press Home) → the page that was in
  front, window or Home page, unmoved · emulator.
- 2026-10-17 · commit "feat(wall): the Workspace may be empty" · Last window closed (`exit` in
  the only window of the only session): the last Home page slides in and no shell starts (the
  drawer lists no session, the notification counts none); a plain swipe on the last Home page
  then stops (rubber-bands); a rotation and a home press from another app leave it empty ·
  emulator.
- 2026-10-17 · same commit · With the Workspace empty, the Terminal entry (drawer and dock) opens
  one window and slides to it; its long press too opens one, not two · emulator.
- 2026-10-17 · commit "fix(launcher): the Terminal entry always resolves for the dock" · With
  the Terminal entry pinned (pin it by hand if the once-only pin was spent), `exit` in the only
  window of the only session: the pin is still on the dock; tap it → one window, the Workspace
  slides in; force-stop and launch → the pin is still there · emulator.
- 2026-10-17 · commit "feat(launcherctl): window.open and pane.open start the Workspace when it
  is empty" · With the row empty (after the `exit` above), `launcherctl window open --title fresh
  -- sh` opens one window titled fresh and slides to it; `launcherctl pane open -- sh` likewise;
  `window list` then lists one window; launcher in the background (another app in front): the
  open still answers ok and the window is there on return · emulator.
- 2026-10-17 · commit "feat(wall): the Workspace may be empty" · Start in = Workspace,
  force-stop, launch: opens on the Workspace with one shell window; Start in = Home on a fresh install still gets Welcome and the first-boot
  tour with their shell · emulator.
- 2026-10-17 · same commit · A Home float with an empty row: open one on Home after the last
  window closed, type in it, close it; the row stays empty and Home stays put, no shell starts;
  with a float open and the row empty, the seam still stops · emulator.
- 2026-10-17 · same commit · No window at all: the status bar's window chips (none, or only the
  float's), the sessions drawer (empty, its + opens a session), the palette's session rows,
  Ctrl+Alt+V/H/F and `launcherctl pane.list` / `window.open` do nothing or say so — no crash ·
  emulator.
- 2026-10-17 · same commit · Start in: Settings shows the row under Launcher mode with the same
  look; Workspace + force-stop + launch opens on the Workspace with one shell window; Home (default)
  opens on Home page 1; a rotation keeps Home page 2, or the window, where it was · emulator.
- 2026-10-17 · same commit · Chrome with the first window during the seam slide, and parallax:
  the wallpaper pans across Home → Workspace → Display as before. Not built in this slice: Home
  still wears the status bar, extra keys, corner tabs and its frame (needs the Home layout, #78),
  and Home pages and window steps do not pan the wallpaper · emulator, record what shows.
- 2026-10-17 · commit "fix(wall): the drag off the first window lands Home on its last page" ·
  From window 1 (reached by the seam, and again after visiting window 2), back to the last Home
  page with the slide, glow and tilt: (a) a sideways swipe in the top air between the status bar
  and the window's top frame, to the right; (b) a hold on the left border past the hold time,
  then a drag right. For (b) the hold must be a real hold: `adb shell input swipe` sets off at
  once and the side border gives it up by design, so use a motionevent DOWN, a pause of at
  least 400 ms, then MOVE/UP (or a finger). If (a) still stays on the window, record the
  emulator's density and whether the drag moved the window at all · emulator, two Home pages.

### Wall rework #80 widget snapshot
- 2026-10-19 · issue #80 · With the Home layout's keyboard element set to always on, a Home page
  whose grid is full: turning always on shrinks the grid rows to make room (no widget lost or
  overlapped), and turning it off restores every widget to its earlier rows and size; repeat after
  a force-stop with always on still set · emulator, then pong.

### Wall rework #78 Home layout
- 2026-10-17 · commit "feat(home): Home pages wear the Home layout …" · A Home page shows no
  frame (no slab, no rim line, with pane glass on and off), no launcher status bar, no extra keys,
  and no corner tab at rest; Android's system bar shows at the top (fullscreen property off) and
  nothing of the launcher's own bar or its glass strip is left behind it · emulator, both
  orientations.
- 2026-10-17 · same commit · Dock on the bottom row: the keyboard swipe on a Home page raises the
  keyboard and the dock rides up on it, the glass blur showing during the ride, and settles back
  when it closes; the widget grid does not move or re-cut either way. With a row set Under the
  keyboard in the Home layout, that row stays on the bottom edge and the keyboard rises out above
  it · emulator.
- 2026-10-17 · same commit · Layout editor opened on a Home page (Home's corner tab has no Layout
  button; open it with `am start` and the layout-editor extra naming the widgets page, or the
  palette once #78 slice B lands): only the pinned apps, the A–Z index and the keyboard are on the
  canvas and in the tray; moves there leave the Workspace layout alone. Opened on a window: the full
  set, as before, normal or minimal · emulator.
- 2026-10-17 · same commit · Minimal mode: Home's corner tab (hold a corner) has no minimal
  button; with minimal on in the Workspace, sliding to Home shows the Home layout, and back on a
  window minimal is still on · emulator.
- 2026-10-17 · same commit · Always on: bring the keyboard chip out of the tray in the Layout
  editor on Home: the keyboard comes up, the grid loses the rows it covers on every Home page and a
  one-line notice says what moved (or that nothing did); the keyboard stays up paging Home and
  coming back from a window. Put it away again: the keyboard goes down and the widgets come back
  where they were if nothing was edited meanwhile; Discard in the editor does the same · emulator,
  with widgets in the bottom rows.
- 2026-10-17 · same commit · The seam slide: from the last Home page to the first window and back,
  the window's status bar fades out on the way to Home and is on the window when it lands, the
  window's frame and extra keys come with it, and no frame of the slide flashes the status bar on
  Home or leaves a band of wallpaper between the grid and the dock · emulator, record what shows
  (the status bar and extra keys switch at settle in this slice, see the hand-over).
- 2026-10-17 · commit "fix(home): the palette gesture starts anywhere on the page's free area" ·
  Swipe up on empty grid of a Home page (e.g. 540,1700 → 540,900 on 1080x2400): on release the
  keyboard rises and the command palette opens over it with the Home rows (Open terminal here
  first). The palette sprouts from the space bar and keyboard taps type into it rather than closing
  it. A sideways swipe on the same spot still pages Home (and leaves into the Workspace from the
  last page); an up swipe that starts on a widget or a Home float is theirs; an up swipe on the
  dock (540,2300 → 540,1500) is still the dock's · emulator.
- 2026-10-17 · same commit · Closing: Esc on the keyboard, a tap on the page outside the palette,
  Back, a swipe down on the page's free area, and running a row (Wallpaper, an app) each close the
  palette and the keyboard together; with always on the keyboard stays up each time · emulator.
- 2026-10-17 · same commit · Always on, no palette open: typing a letter on the in-app keyboard
  opens the Home palette with that letter in the query; a Ctrl+Alt binding to an app runs the app
  instead of opening the palette; with a Home float focused the letter goes to its shell. Also
  with a hardware keyboard (an unbound letter opens the palette, a bound chord runs) · emulator.
- 2026-10-17 · same commit · Settings → Apps & Home: one new row "Palette gesture on Home" under
  "Swipe down for app drawer", summary "Swipe up"; the page otherwise looks as
  before. Set Off: the swipe up on Home does nothing, a swipe down still puts away a keyboard that
  is up · emulator, 1.3× font scale too.
- 2026-10-17 · same commit · The Workspace keyboard swipe is unchanged: up opens the keyboard
  alone (no palette), down closes it, and the window remembers which · emulator.

### Wall rework #77 display
- 2026-10-17 · commit "feat(x11): the display opens on demand …" · With no display running, tap a
  Linux app (xeyes) in the drawer: the display starts, the wall slides to the display window, the
  app shows on it, and back on a terminal window the status bar has a Display chip after the
  window chips; tapping the chip goes to the display · emulator, x86_64 build with xkeyboard-config.
- 2026-10-17 · same commit · Display under Linux Apps in the drawer, with no display running:
  opens the bare display (the Start command), the window on the row. Without xkeyboard-config the
  window shows the install offer instead; swiping away from it takes the window off the row ·
  emulator.
- 2026-10-17 · same commit · Long-press Stop on the display: the apps close, the display window
  leaves the row and the wall lands on the last terminal window (on the last Home page when there
  is none); the Display chip goes; the screen glyph in the status bar reads quieter and a tap on
  it starts the display again · emulator.
- 2026-10-17 · same commit · `termux-x11 :0 &` then `DISPLAY=:0 xeyes &` from a shell, with no
  display window open: the window appears on the row (the wall does not move to it) · emulator.
- 2026-10-17 · same commit · The Terminal entry: on the dock after the first launch of this build
  (once; remove it and relaunch, it stays removed); tap from Home goes to the window last shown,
  or opens a window when there is none; long press shows New window, which opens one after the
  current window and slides to it · emulator.
- 2026-10-17 · same commit · Settings: the root page has no Use as row (Start in sits under the
  search box) and Settings → Display has no Display switch at its top; both pages otherwise look
  exactly as before · emulator, screenshot both against the previous build.
- 2026-10-17 · same commit · A `Terminal=true` Linux app (htop's desktop file) opens a new terminal
  window right after the current one, not a pane; a second tap while it runs brings that window
  back · emulator.
- 2026-10-18 · commit "fix(launcher): Linux apps installed from a pane …" · Without leaving the
  launcher, `pkg install gtk3-demo` in a pane, then open the palette on Home and type `gtk`: GTK+
  Demo is under Linux apps (it may arrive a moment after the palette opens), Display is there too
  with no query; `launcherctl launch gtk3-demo` opens it on the display window · emulator.
- 2026-10-18 · commit "fix(x11): Stop stops the display server …" · Start `termux-x11 :0 &` by
  hand in a pane, then long-press the display › power › Stop and confirm the dialog (screenshot,
  a UI dump does not see it): the server exits (`pgrep -f termux-x11` empty) and the display window
  leaves the row; the same for a display the Display entry started · emulator.

### Wall rework #81 migration
- 2026-10-17 · commit "feat(wall): migrate the usage mode to Start in, once" · Install this build
  over a build from before the wall rework, once per usage mode (set in Settings before the
  upgrade): Terminal alone lands on a Workspace window and Settings shows Start in = Workspace;
  With Home and With Display land on Home page 1 with Start in = Home. In each, `run-as <pkg> cat
  shared_prefs/<pkg>_preferences.xml` has no `app_launcher_use_case_mode`, `x11_display_enabled`
  or `wall_last_page`, and holds `wall_migration_version` = 1 · emulator.
- 2026-10-17 · same commit · After the upgrade start, change Start in, force-stop and start again:
  the choice stands and the preferences file is otherwise unchanged · emulator.
- 2026-10-17 · same commit · After the upgrade, take the Terminal entry off the dock, force-stop
  and start again: it stays off the dock · emulator.

### Wall rework: blind end-user review, 2026-10-18 (Codex on the emulator at ecfd75107)
- Four `ad codex` passes (Home, Workspace, floats, Settings) with no code shown; answers and shots
  under ~/reports/wall-rework-2026-10-18/blind/. Fixed and seen again on the release build of
  31803120e: a still long press on a dock pin no longer unpins it (menu opens, five pins survive
  the press on Terminal and on Camera, a force-stop and a relaunch); the Home palette's glass ends
  above the keycap strip, the dock rows and the A–Z row (card bottom about y 1400, dock rows
  1527–1748, keyboard below); Back over a focused float closes the keyboard first (dock back at
  y 2116); titlebar glyphs pale on dark glass; a second float opens 92 px lower and 42 px right
  of the first (keyboard down); the Home layout editor's tray shows one Keyboard chip under a
  badge of 1.
- Seen at 2fd474abb: the second float keeps its own top while the keyboard is up (titlebars at
  y 220 and 312 in both states); the Terminal entry survives an upgrade install (the catalogue
  prune no longer drops the launcher's entries). The drawer's "Saved sessions" tab reads in full at
  b5b19ef99 (the drawer's row is 153dp; the tab takes the rest of it after Live).
- Judged not defects: status-lens neighbour icons half past the screen edge (#46); the drawer's
  glass showing the pane under it, the Workspace key-row ink, the corner tab staying after Back,
  the pin editor's bottom row under the gesture inset and the Settings nits (retention labels,
  empty extra-key page, white status icons in the appearance editors, font "smallest" claims,
  Licenses path) are from before the rework and listed on the review page for a settings pass.

### Wall rework: the Surface round, 2026-10-18 afternoon (Bliss OS Android 15, x86_64, 2736x1824)
- Seen at 052f83cbd on the Surface: a Home float stays up, `dumpsys activity binder-proxies` holds
  238 for the app over 90 s (was +265/s until the kill "Too many Binders sent to SYSTEM"); the
  palette takes a tap on the floating in-app keyboard (the key lands in the query); the Home
  long-press sheet shows all rows; the A–Z rail ends at y 1607 above the dock at 1623.
- Seen at 224af804d on the emulator: the Home grabber sits centred above the dock; a plain
  `input swipe 8 1100 900 1100 700` from window 1 lands on Home; the sheet on a phone shows
  Terminal, Add a widget, Add a page, Edit pages, Layout, Wallpaper, Look, Launcher settings, Help
  (Remove page only on an empty second page: not driven).
- Owed on pong, by hand: the bottom border's glow after the fix (lit at the edge, inside the
  frame, with the keyboard up and down); the border swipe back to Home by finger; the sheet's
  slide-up, swipe-down and dim; the grabber's visibility over a light wallpaper.
- The Terminal pin leaving the first dock slot after an upgrade or cold boot was seen twice on
  the emulator and not reproduced since (install -r and a reboot kept it, store included); watch.

### Wall phase three: the 10-18 round (feat/wall-phase3)
On pong as 1.1.0-pre.6+dev.d986fc9ae (2026-10-18 20:40, launch clean). Nothing below is seen yet;
all of it is reasoned from code and unit tests. The capsule form is gone, so slice 1's owed items
are void.
- Status bar: Workspace bar swipes compact ↔ expanded with no idle fold; Minimal mode as before.
- Home pills: superseded 2026-10-19, the pills are removed (see "10-19 Home fixes").
- Display chip: open an X11 app, tap its chip → the bar keeps every window chip, the chip wears
  the app's icon; a terminal chip tapped there returns to that window.
- Home launches: empty Workspace, tap Terminal in the dock → a Home float, wall stays on Home;
  Ctrl+Alt+C / palette New window on Home → float; new float is a centred window (wallpaper left,
  right, above) in portrait and landscape; keyboard up → it ends above the keyboard, titlebar
  stays; Maximise fills the room, Restore returns; a second float cascades; restart keeps sizes.
- Bottom-border glow sits in the gap below the pane, not inside: keyboard up/down,
  Docked/Floating, one pane and a split, landscape; never over the dock or keyboard.
- Float titlebar is thinner (24 dp); Close/Maximise/Minimise still take taps just below the
  strip; dragging the strip moves the float.
- Wallpaper: on a window page, set a wallpaper in the system app, press Home → same window;
  with a Home float open, apply from the launcher's Wallpaper page → stay on the window; Home
  pressed while in front still goes to Home page 1.
- Keyboard Looks: "BG opacity" then "Key opacity" under a link button with leader lines; linked,
  either drags the other; unlink keeps values; ✕/Back restores the link state, Done keeps it;
  Large text and RTL keep the "Keyboard" heading clear of the button.
- Start in reads "Terminal" and still opens the Workspace.
- Layouts Home filter: tap Home → miniature shows the grid from the page's top (no band) and the wall goes
  to Home; dock moved there changes Home's only; Corners/Margin read "· Home"; Landscape stays
  selected; Undo restores both layouts; opened over the display, Home off returns to it; Row A
  fits at 360 dp, font 1.3.
- Widget glass: Glass on in a built-in widget's settings → blurred wallpaper, rounded corners,
  fixed to the wallpaper while sliding; readable in light/dark/Material You; off is identical to
  before; a wallpaper change updates it.
- Terminal widget: add from the picker, type into it, tap beside → keyboard goes; low on the page
  with keyboard up → prompt above keyboard and dock (portrait/landscape, Docked/Floating); resize
  in edit mode → rows/cols change, shell kept; on page 2 focused, swipe to page 1 → focus and
  keyboard go, output shows on return; recreate → same shell, process kill → fresh shell in the
  last directory; `exit` → "Session ended", tap restarts; remove while `sleep 100` → asks; never
  in the float chips, window chips, status bar or Saved sessions, never a Workspace window.
- 10-19 Home fixes (Developer, pong; reasoned from code and unit tests, nothing seen yet):
  - Keyboard on Home is glass (blurred wallpaper, the Keyboard surface's opacity and tint), not the
    opaque panel: docked and split, Docked and Floating Style, light/dark/black/Material You;
    the wallpaper under the keys lines up with the dock's (no offset after paging Home); sliding
    Home ↔ window with the keyboard up shows no flash; the display in overlay mode is still the
    solid panel; the floating keyboard is still its solid card on Home.
  - Home pills on glass: superseded, the pills are removed (below).
  - Command palette on Home opens in the screen's middle; typing so results arrive grows it about
    its middle (top rises a little), never over the dock; with the keyboard up it stands above the
    keyboard's strip; landscape keeps it inside the room; the Workspace palette is unchanged.
  - Home floats and the keyboard: a birth-shape float, a moved/resized one, a maximised one and an
    unfocused one on the same page all end at the keyboard's top (titlebar where it was); a small
    float low on the page rises to stand on the keyboard; closing the keyboard puts every float back
    exactly; restart keeps the shapes from before the keyboard; paging Home with the keyboard up
    shapes the new page's floats.
  - The seam: with Workspace windows, a plain swipe left on the last Home page carries the wall
    into window 1 under the finger, a short swipe springs back, and a border drag right off
    window 1 returns to the last Home page. With the Workspace empty (only Home floats): the same
    swipe brings an empty Workspace in under the finger with its chrome, and letting go past the
    commit opens a fresh shell as window 1 (one, not two); a short swipe or a swipe dragged back
    springs to Home and no shell appears (check the drawer's sessions); no flash or jump at the
    release.
  - Swipes on a Home float's terminal: sideways pages Home (and crosses the seam from the last
    page) as a swipe beside the float does; vertical scrolls the terminal; a tap focuses it and
    types; the titlebar still moves it and the corner still sizes it; with a selection under way a
    sideways drag extends it; in tmux/vim with mouse on, a sideways drag goes to the program; with
    a full-size float on the last page and an empty Workspace, a sideways swipe on it opens
    window 1. Terminal widgets on a Home page behave as before.
  - Typing into a terminal widget with the phone's keyboard (Gboard, in-app keyboard off): the
    Home grid keeps its size (the clock and every other widget unchanged, no one-frame squeeze as
    the keyboard rises), the dock rides on the keyboard with the same gap it keeps above the
    navigation bar, the widget rises so its prompt and cursor row sit just above the dock, never
    above the grid's top, and its text stays (no blank card); closing the keyboard puts the widget,
    the dock and the grid back exactly. The same with the in-app keyboard. Also: a widget resized
    in edit mode shows the prompt at its new size; a tall widget whose prompt is at its bottom;
    a widget high on the page (no lift needed); Home floats with the phone's keyboard end at the
    dock's top as they do with the in-app keyboard; the Workspace with the phone's keyboard still
    shrinks the terminal above it; a side rail on Home ends above the dock; fullscreen mode
    (bars hidden). Light/dark, Docked and Floating, portrait and landscape.
  - No status bar on Home (feat/wall-phase3, 2026-10-19 worker branch): with no float, one float,
    one minimised and with the bar set to top, bottom and a side in the Workspace, Home shows no
    launcher status bar and no pills, ever; the widget grid's top row starts just below the system
    status bar (as before the pills), and does not move when floats open, minimise or close; the
    Layout editor's Home miniature draws the grid from the page's top; slide Home ↔ window: the
    Workspace's bar fades out toward Home and back in with the window, no flash on settle.
  - Titlebar readings: focus a Home float → its titlebar shows CPU and RAM at the left, then the
    grip, then Minimise, Maximise, Close; the glyphs, colours and type match the Workspace bar's CPU
    and RAM side by side (switch pages to compare), in light, dark, black and Material You, glass on
    and off; unfocused floats show no readings; tap beside the floats → readings go; the values
    tick at the bar's pace and a steady value does not flicker; turn CPU or RAM off in Status bar
    settings → that reading goes; no weather. Narrow a float, and at font scale 1.3 on a 360 dp
    phone: RAM drops first, then CPU, never over the grip or a button; dragging the strip over the
    readings still moves the float. With no focused float on Home the CPU sampler is idle
    (`dumpsys` or the stats card's cadence): only a focused float's readings wake it. Height: at
    font scale 1.3 the readings' glyphs and figures sit whole inside the 24dp strip (no clipped
    tops or descenders, centred on the buttons' line); at 1.5 and above they are gone rather than
    clipped, and the strip is the plain one. Readings keep ticking on Home while the Workspace
    bar has CPU/RAM on.
  - Float chips: minimise a float → it flies into a small glass chip centred just above the dock
    (glyph and short name), the chip lands where the flight ends; minimise a second on another Home
    page → two chips, both on every page; tap a chip → Home pages to its float, which comes back
    focused and its chip goes; the last chip going lets the other floats grow back to the dock.
    While chips show, no float covers them (maximise one: it ends above the row). Keyboard up (in-app
    and the phone's): the row stands just above the dock riding the keyboard, not under it; minimise
    the focused float with the keyboard up: the flight lands on the chip's final place (the keyboard
    closes unless always on). Chips wear the dock's glass (Opacity, Blur, Grain and Tint, inherited
    and detached; a wallpaper change updates them), readable in light/dark/black/Material You;
    Docked and Floating; many floats (8+) at font 1.3: names drop, then chips narrow, none overlap;
    a tap between chips reaches the widget under, a sideways swipe between or on chips pages Home,
    a vertical swipe beside the row opens the palette as elsewhere, and a press between chips gives
    Home its focus back; a press on a chip that slides past a few pixels does not restore; on the
    Workspace there are no chips (the bar's chips still list the floats); palette Minimise all /
    Restore floats update the row; a chip's label follows its float's program (rename the window,
    run `vim` before minimising); change the scheme or Material You colours without restarting:
    the chips' glass and ink follow.

### Wall rework phase two: slice 2, Style, Corners and Margin on Home (feat/wall-rework-phase2)
- 2026-10-18 · commit "feat(wall): the chrome wears the Style, Corners and Margin of the half on
  screen" · Upgrade seeding: install over a build without the slice; Home and the Workspace look
  exactly as before (no `home_*` keys in the prefs file). Open Appearance → Layout over a Home
  page: the labels read "Corner radius · Home" and "Margin · Home"; flip Style Docked→Floating:
  only Home's dock rows, keyboard and (with a float open) the compact status bar and the float's
  corners reshape; slide to a window: the Workspace is still Docked, and back: Home is Floating
  again, with no stale shape after the settle. Same for Corners and Margin. Open the editor over a
  window: no "· Home", edits land on the Workspace only and Home keeps its own. Undo and Discard
  in a Home session put an unset Home value back unset (it follows the Workspace again) · emulator.
- 2026-10-18 · slice 2b, commits "feat(widgets): Home's Margin is the widget grid's rim" and
  "feat(home-floats): Home floats breathe Home's Margin" · Upgrade: the widget grid's rim moves
  from 6dp to the seeded 12dp (accepted; the seeding check above holds for everything else). With a
  6-column grid and a float open on a Home page, drag Margin · Home: the widget rim and the float's
  air (sides, below the status bar, above the dock) move together, tick by tick, and the grid
  re-lays at once with no flicker worse than a grid-size change. At 48dp on tl16 (~411dp wide) the
  column cap drops to 5 on release (the grid reflows once, not mid-drag); the size popup's column
  wheel now stops at 5; back to 12dp the wheel offers 6 again. A float born after the change fills
  the smaller room; a float sized smaller earlier keeps its fraction of the room. Margin 0dp: the
  widgets sit flush to the page's edge, a widget's edit chip still inside the pane. Margin over a
  window leaves Home's rim and floats alone · emulator.
- Re-check owed after the 2026-10-18 fixes (9980f4a6d, 1477dfcc8): the air from the screen's edge
  to the first widget cell is 1 × Home's Margin (plus the 2dp cell gutter) at 8 and at 48dp, and
  the column cap at 48dp on tl16 is 5; a cold start into Home with Home Floating and the
  Workspace Docked shows Home Floating with its Margin on the first frame; a widget's content is
  whole after a Margin change and Undo (Clock's "PM" and date); the editor opened from a window's
  own corner toolbar after a Home widget edit session says no "· Home" (note which toolbar came
  out: Home's has a pencil and a plus); Appearance from a corner toolbar with a Home float open
  opens (not fixed; record which toolbar it was and whether the Overview opened behind the float).
- Re-check owed after the round 2 fixes (C 31173137b, E 590ff4c8b): a Clock widget's content is
  whole ("PM" and the full date) after a Margin drag and Undo, then Done; and after a Home Style
  change to Floating, then Done, with no restart. With a Home float open, slide to a window and
  open Appearance from its corner toolbar's palette, right after a Layout session over Home: the
  editor is the Workspace's (no "· Home", no Home grid in the preview, no blank preview while the
  wall moves). A cold start with Start in = Workspace opens in the Workspace. Use a true cold
  start: set the stock home first or launch from the icon (see the device map) · emulator.
- Round 3 (2026-10-18) still saw the Clock drawn for the big Margin after Undo, then Done, with
  910c9487b in. Cause found off device, in a Robolectric reproduction: the built-in widget's zoom
  search (`BuiltinWidgetView.chooseZoom`) read its losses from labels the framework's measure cache
  had not measured; a card back at a size it had before came back at the largest zoom (the Clock
  at 149dp tall: 1.62 instead of 1.15), date cut short, "PM" clipped, and the zoom memo kept it.
  Fixed: every trial measures the layout afresh. Re-check owed: the Clock whole after a Margin drag
  and Undo, then Done; after Discard; after a Home Style flip and back; after a real Margin change
  and Done; leaving Home and coming back changes nothing · emulator.
- Round 4 (2026-10-18, ec304bb50, emulator): seen. The Clock is whole after install, after a
  Margin drag to 43 and Undo then Done, after Discard, after Style Floating and Docked, after a
  real Margin change to 25 and back to 8, and across a minute tick. At font scale 1.5 the date
  line truncates to "Saturday, Octobe…" with the time intact (acceptable for a fixed cell). Not
  seen: dark theme, rotation, keyboard up; Start in = Workspace on a true cold start.

### Wall rework phase two: slice 3, parallax across the whole row (feat/wall-rework-phase2)
- Owed · commits "feat(wall): parallax geometry spread over the whole row" and "feat(wall): the
  wallpaper pans with the row's live position" · emulator, portrait, a wallpaper set through the
  launcher's picker (self-drawn backdrop, managed copy), Wallpaper parallax on, animations on,
  Low-memory preset off. Two Home pages, two windows, a display up. Swipe Home page 1 → 2, across
  the seam to the first window, border drag to the second window, on to the Display page: the
  wallpaper pans continuously with each finger, settles at a distinct offset on each page (five
  even steps from the picture's left edge to its right), and does not jump at the seam or at a
  drag that commits; the frost (status bar, dock, keyboard, pane glass, Home's widget glass)
  follows the wallpaper with no visible lag. Swipe back the same way: the same offsets. Window bar
  jump from window 1 to window 2 and back: the pan runs over the arrival, with no snap first.
  Home press from a window: the wallpaper pans back to Home page 1's offset with the slide.
- Owed · negatives: rotate to landscape: no pan anywhere (the wallpaper is still); back to
  portrait: the pan resumes at the page's offset. Low-memory preset on: no pan; off again: back.
  Animations off (developer options or reduce motion): no pan.
- Owed · count changes: on Home, add a Home page; open a window from the Workspace (+): the
  wallpaper re-spreads over the new count without a visible jump larger than one page's step (it
  snaps, by design, rather than animating); close a window, take the display down: the same.
- Not hooked (snaps): Home's own jump to a Home page without a swipe (the home press while already
  on Home, a jump to a window from the drawer by id) snaps, as the page itself does; a session
  switch (vertical) re-spreads at once.

- Seen 2026-10-18 (faf8596db, emulator, device-checker): 2 Home pages + 3 windows pan at even
  135 px steps (640 → 100 px, the last window at the picture's right edge; 270 px steps with
  3 pages); the mid-slide offset follows the finger through the seam with no jump; frost on the
  dock and extra keys lines up with the backdrop; landscape: no pan; Low-memory on hides the
  wallpaper (solid look), off: the pan returns; adding windows snaps by at most one step. Partial:
  a window-bar jump from window 1 to 3 snapped 196 px in one frame then eased 58 px over four,
  rather than panning over both steps (the nudge's departure window is probably not reaching
  `nudgeTerminalPage`); a one-frame 40–50 px blip at a drag's end. Not seen: the Display page,
  dark theme, keyboard up, Reduce motion. Once, three `sh` windows fell to one after a slow swipe
  and taps, not reproduced.
- Owed · re-check of the 1 → 3 jump (commit "fix(wall): a window-bar jump starts its nudge before
  the wallpaper re-syncs"): the departure was already reaching `nudgeTerminalPage`; the nudge now
  starts before `rebuildDrawerSessions`' re-sync. Log the offsets of a 1 → 2 jump beside the
  1 → 3 one: a first step of about 73 % on both is the emulator's slow first frame on the settle
  curve, not a snap, and needs a pacing fix, not this one.
- Re-check 2026-10-18 (c243c23c4): a bar jump still mis-pans: both a one-step and a two-step jump
  first move the wallpaper the wrong way (about 56 px, then 120–184 px) before panning to the
  arrival (`fixround2-devcheck/80-jump1.mp4`, `81-jump2.mp4`). A first frame computed with the
  departure still current while the arrival page is already nudged in would do exactly this
  (`innerPosition` with ADJACENT from the stale index); owed.
- Owed · re-check of commit "fix(wall): the departure card's glass ground follows the wallpaper's
  parallax": the wrong-way first frame was the departure card, not the row position (already
  monotonic, current set before the nudge): `TermuxActivity.wallpaperGlassGround` drew the card's
  slabs over the frame without the parallax offset, so it showed Home's stretch of the picture
  (in the videos the card's stripes sit at 940 px over a departure at 760 and at 400); it now
  subtracts `mWallpaperParallax.offsetPx()` like `GlassAnchor`. Check: a one-step and a two-step
  bar jump, both ways, pan the wallpaper monotonically from the departure to the arrival with no
  first-frame step on the outgoing card; compare with a border drag's step (same card).
### Wall rework phase two: slice 4, Saved sessions keep Home floats (feat/wall-rework-phase2)
- Owed · commits "feat(wall): saved sessions record a Home float's page and shape (v4)" and
  "feat(wall): a saved Home float loads back as a Home float on its page" · emulator, portrait,
  at least two Home pages. On Home page 2 open two Home floats; minimise one, move and resize the
  other. In the Workspace keep a tiled window and a window split in two. Save sessions… as
  "floats". Close everything (or force-stop with the stock home set, so it is a true restart).
  Load "floats" (Replace): both floats are back on Home page 2, the moved one at its spot and size,
  the minimised one as a chip only (not shown until tapped); the tiled window and the split are in
  the Workspace's row; the drawer's Live list marks the two floats as floats; the wall stays on the
  place it was on before the load.
- Owed · older files: load a session saved before this build (version 3): unchanged, every window
  in the row.
- Owed · a page that is gone: delete Home page 2, then load "floats": both floats land on the last
  Home page.
- Owed · orientation: shape a float in landscape too, save, rotate to portrait, load: the portrait
  shape is used; rotate to landscape: the landscape shape comes back. Rotate before loading a file
  saved in portrait only: the float is born full-size (or cascaded) in landscape, and the portrait
  shape returns on the turn back.
- Owed · Replace with Home floats open that are not in the file: they close (Replace closes the
  live Home floats with the rest of the session).
- Owed · floats only: close every window of the Workspace, keep two Home floats (one minimised),
  Save sessions… as "onlyfloats"; Replace-load "floats", then Replace-load "onlyfloats": the two
  floats come back on their page and the Workspace is empty (Home shows, no window in the row,
  the Terminal entry opens a fresh window); from the Workspace the last Home page slides in, from
  Home the wall does not move. Add-to-current "onlyfloats" over a live Workspace: the floats join,
  the current session and window stay. Note: with an empty Workspace the drawer's Live list has no
  session to list the floats under (as before this slice).

- Seen 2026-10-18 (f10e312f1, emulator, device-checker): Save writes `homeFloat` with the page,
  `minimised` and a portrait shape (file version 4); Replace after a true restart brings both floats
  back on Home page 2 (one a chip, one at its spot and size) with the tiled window and the split, the
  Live list reads "Float · page 2"; a deleted page 2 lands them on page 1; Replace closes a stray
  float; a hand-made version 3 file loads as tiled windows. Landscape load: floats born full-size
  (the portrait reload of a landscape save not done). Not seen: a floats-only session; Add's
  behaviour (the file's windows arrived as a new session 2 and the view switched to it: confirm
  against the old Add). Found, outside the slice: (1) the drawer's Saved tab shows "No saved
  sessions" when opened from the list key even with files on disk, and fills only via the palette's
  Load entry or the Home picker chip (check the previous build); (2) closing the last window or
  float with an empty Workspace finishes the activity and drops to the stock launcher; (3) in
  landscape a full-width float drags left until its titlebar is clipped off-screen; (4) once, after
  a landscape session, the live session collapsed to one window with new ids, not reproduced;
  (5) chips on a deleted page keep "home · 2" until the next Replace; (6) the Save card's "Also
  save what is running" sits below its buttons; (7) the built-in Clock in landscape stacks "11"
  over "38" in a wide pill.
- Owed · re-check of (2), commit "fix(wall): the last shell closing shows Home instead of
  finishing the activity": the service stopped itself with its last shell and the activity
  finished on the disconnect. With an empty Workspace close the last window (bar ×, `exit`), then
  separately the last Home float (Close, `exit`): the last Home page shows, the launcher stays, the
  notification reads 0 sessions; the Terminal entry opens a fresh window. The notification's Exit
  still closes the app.
- Owed · re-check of (3), commit "fix(wall): a Home float stays inside its room when dragged": in
  landscape and portrait a full-width float does not move sideways at all; a smaller one stops at
  each edge of the room with its titlebar whole; a corner resize stops at the room's edge.
- Owed · re-check of (1), commit "fix(sessions): the drawer's Saved half reads the saved files
  whenever it is shown" (the bug dates from 93c031139, 2026-09-13, before the wall rework): open
  the drawer from the list key, tap Saved: the files are listed.
- Re-check seen 2026-10-18 (c243c23c4, emulator, device-checker): `exit` in the last window and
  closing the last float both leave Home on screen with the launcher resumed, and the dock's
  Terminal entry opens a fresh window (the notification's text and Exit action could not be read:
  the emulator has the app's notifications at importance NONE; pong); a float stops at its room's
  edges in portrait and landscape, titlebar whole, a resize past the corner changes nothing; the
  Saved tab lists the files right after a save and from the list key after a restart; a long press
  on a float's chip raises no selection (the Display chip not reached: the bar folds first). Also
  seen: in landscape on Home the Clock widget squashes into a thin pill under the status pill; the
  drawer's rows let terminal text show through.
### Wall rework phase two: slice 5a, window reorder by drag in the window bar (feat/wall-rework-phase2)
- Owed · commits "feat(wall): window reorder policy and moving a window in the row", "feat(wall):
  drag a window's chip along the window bar to reorder the row" and "feat(wall): window.move verb
  and Move window left / right palette actions" · emulator, portrait, status bar on the top edge.
  With three windows (1 2 3) and window 1 current, long-press window 1's chip: it lifts (grows a
  little, a haptic tick). Drag it past windows 2 and 3 and release: the bar reads 2 3 1, the
  current window is still window 1 (now third, its chip selected), and the border drag left goes
  to window 3. The drawer's Live list reads 2 3 1. Save sessions… then Load (Replace) gives 2 3 1.
- Owed · `window.move to=1`: bind `map ctrl+alt+shift+m window.move to=1` in
  `~/.termux/termux-launcher-bindings.conf`, `termux-reload-settings`, press it on window 1 (third):
  the bar reads 1 2 3 again and window 1 stays current. The palette's Move window right / left
  move it one place each way; at the end of the row they do nothing; neither is offered on Home.
- Owed · what does not move: with a Home float open, its chip (after the row's, marked as a float)
  does not lift on a long press, and neither does the display's chip; on Home and on the Display
  place no chip lifts. A tap on a chip still switches; a second tap on the current chip still
  offers its ×. A long press released without moving changes nothing; dragging away and back to
  the start changes nothing; a drag that turns mostly vertical puts the chip back and does not fold
  the status bar.
- Owed · overflow: with 8 windows, so the bar scrolls, lift the first chip and hold it near the
  bar's right edge: the strip scrolls under it until the end, the chips part as it passes them,
  and the release lands it where it shows.
- Seen 2026-10-18 (56bc8e6b4, emulator, device-checker): dragging window 1's chip past the
  other two reorders the bar, the current window follows (third, still selected), the border drag
  and the drawer's Live list agree, Save then Load keeps the order, the palette's Move window
  left/right move one place, a tap still switches, a returned or vertical drag changes nothing,
  the Display and Home float chips never lift, and with 12 windows the strip auto-scrolls at the
  right edge and the chip drops last. Found: a long press on a float's or the Display's chip leaks
  to the terminal under the bar (a "Text selected" toast and the selection menu; the toast lingers
  on Home); the Saved tab empty right after a save (the list-key route, as in slice 4); the palette's
  Move window left/right are not adjacent in the results; the folded bar's description once read a
  blank window label. Not seen: the left-edge auto-scroll, `window.move to=` from a keybinding,
  dark theme, large text, rotation.
### Wall rework phase two: slice 5b, consume and expel (feat/wall-rework-phase2)
- Owed · commits "feat(wall): consume a neighbour window into this one, expel a pane into its own"
  and "feat(wall): pane.consume and pane.expel verbs, palette rows and Niri keys" · emulator,
  portrait. With windows A B C (one pane each) and B current, palette › **Consume next window into
  this pane**: B holds B's pane and C's pane split (stacked in portrait, side by side in
  landscape), the bar and the drawer's Live list read A B, the consumed pane (C's) has the focus,
  and C's shell is still alive (its scrollback is there).
- Owed · then palette › **Expel pane to its own window** (or `Ctrl+Alt+.`): the focused pane
  leaves B into a new window right after B, the row reads A B C', C' is current (its chip
  selected) and the Workspace slid to it from the right; B is back to its one pane. Expel on a
  one-pane window does nothing and closes nothing.
- Owed · Save sessions… then Load (Replace) after each step keeps the row and the panes as shown.
- Owed · `pane.consume from=left` (`map ctrl+alt+shift+m pane.consume from=left` in
  `~/.termux/termux-launcher-bindings.conf`, `termux-reload-settings`) on B pulls A: A's pane sits
  before B's, the row reads B C. `Ctrl+Alt+,` on the row's last window does nothing.
- Owed · with a Home float open, consume on the row's last window does nothing (the float's chip
  after the row's is never consumed); with the Home float focused, `Ctrl+Alt+.` does nothing.
  Neither action is offered in the Home palette.
### Wall rework phase two: slice 6, the first-boot tour for the wall (feat/wall-rework-phase2)
- Owed · commits "feat(tour): six lessons for the wall, each taught on its half" and
  "feat(tour): switch the tour back on, with the wall's hooks" · emulator, portrait, slow injected
  swipes (`adb shell input swipe … 600`), a debug build so `adb logcat -s TermuxTour` carries the
  signal log. Fresh data (`adb shell pm clear com.termux`), launch, Welcome › **Take the tour**:
  the first card stands on Home page 1. Each lesson clears by its real gesture and the next card
  follows: swipe left on Home's free area onto the first window, then a sideways swipe right
  along the window's bottom border back to Home; swipe down on the apps row (the drawer opens);
  then the wall slides to the first window by itself, keyboard up, and the keyboard card stands
  there (no "swipe to your window" card): keyboard swipe down then up; the status card follows on
  the same window with the bar folded: slow pull down from the top border (opens whole), quick
  swipe up (folds); the wall slides back to Home page 1 for the palette card: swipe up on Home's
  free area (palette and keyboard); then to the window again: hold its corner, tap ?; the closing
  card shows over that window and its **Start** ends it.
- Owed · the half rule: on the keyboard card, swipe back to Home with the keyboard up: the card
  turns to the way back ("Swipe left past your last Home page…") and glows nothing, it is not
  cleared (the log reads "ignored: it happened on the other half of the wall"), and back on the
  window it still asks to put the keyboard away. On the apps card, a border drag onto a window
  turns it to "…or press Home…".
- Owed · **Skip** on a card moves to the next lesson; Settings › About & help › **Replay tour**
  plays the whole run again from the Welcome sheet; `adb shell am force-stop com.termux` mid-run
  (on the status card, say) and relaunch: the run resumes on the same lesson from its first half,
  on the half it starts on (the window, for the status card), with no Welcome sheet. Help ›
  **Try it** on the keyboard topic from Home lands on a window with the keyboard up. The tour never opens a second shell (`ps -A | grep bash`
  shows one before and after the run; the window bar shows one window).
- Owed · the skip rules: Home is always on the wall (`isWidgetsPageWanted`), so there is no
  terminal-only install to try; the Home lessons' skip without Home is unit-tested
  (`TourRun.droppedFor`). If the session can be left with no window (the row without a
  Workspace), Replay then leaves the seam lesson out and opens on **Open your apps**.

- Seen 2026-10-18 (78405f42a, emulator, device-checker): Consume stacks the right neighbour's pane
  under the current one with focus on it (`pwd` prints its cwd), the row and the drawer count agree;
  Expel gives the focused pane its own window right after, current and on screen; Expel on a
  one-pane window or with a float focused does nothing; `pane.consume from=left` (via a keybinding)
  pulls the left neighbour above; Save then Load keeps the two-pane window; a float's chip is never
  consumed; "Move window to…" asks for a 1-based target; landscape consume splits side by side.
  Minor: an expelled window's chip reads "sh" (the cwd and title do not carry over to the new
  window); the Saved tab empty right after a save (fixed in the slice 4 round, re-check owed);
  `launcherctl window focus` from Home makes the window current without leaving Home.
- Seen 2026-10-18 (ed6b8d160, emulator, device-checker, fresh data): every lesson clears by its
  gesture (page swipe + border drag, keyboard down/up, status pull + quick fold, swipe up for the
  palette, corner hold + ?); the Workspace lessons start on the window with no way-there card;
  wandering to Home shows the way-back card and does not clear ("ignored: other half"); Skip
  moves on; Replay tour replays; a force-stop resumes on the same lesson; one shell. Caveat: the
  apps lesson clears on a drag down on the dock strip (drawer.opened) but not on a tap on the
  A–Z letters; owed: a tap that opens the drawer should count.
- Fixed 2026-10-18 (worker branch, "fix(tour): the apps lesson also clears on a tap on the dock's
  A-Z letters"): a letter tap never opens the drawer, it lays the letter's apps out in the row and
  told the tour nothing; it now sends `launcher.az_apps_shown`, which clears the apps lesson too.
  Re-check owed: tap a letter on the apps card (the log reads the signal, the card clears); a tap
  on a pinned app only launches it and is not counted.
- Re-check 2026-10-18 (7fccb5d8d): an A–Z letter tap clears the apps lesson (`az_apps_shown`).
  Polish owed: the first card sits over the page's icon labels and the "swipe left" stage draws no
  guide arrow.
### Wall rework phase two: slice 7a, app icons on Home pages (feat/wall-rework-phase2)
- Owed · commits "feat(home): app icons as a Home item kind in the widget store (schema v6)",
  "feat(home): app icon cells on Home pages: launch, edit mode, gone apps" and "feat(home): Add to
  Home in the app menu, Add an app on the Home sheet" · emulator, portrait. Install the build over
  one that has widgets on Home (a v5 wall with a built-in and an app widget): every widget is where
  it was and works.
- Owed · drawer › hold two apps in turn › **Add to Home**: each lands in the first free cell of the
  Home page in front, with its icon and its label under it (one line, ellipsised, legible on the
  wallpaper and on glass), and a toast reads "Added to Home page N". Home sheet (hold an empty
  spot) › **Add an app** (beside Add a widget) › pick one from the list (search narrows it): same
  placement, same toast.
- Owed · fill the page in front: the next Add to Home goes to the first page with room, or onto a
  new page after the last when every page is full; the toast names that page.
- Owed · tap an icon: the app opens (with the press ripple). A pinned app's menu has Add to Home
  too.
- Owed · long-press an icon: edit mode opens as for a widget (remove chip, no resize handles,
  other items outlined) and the same press moves it; drop it on a widget and the widget slides
  aside. Hold it against the page's edge past the last page: a new page is made and the icon lands
  there; carry it back, and the empty page goes again. Remove chip takes it off.
- Owed · Appearance › Layout on Home › Margin · Home at 24: icons and widgets are inset alike
  (the same rim), and back at the old Margin both return.
- Owed · Settings › icon pack applied live: the Home icons change with the drawer's and the dock's,
  without a restart.
- Owed · `adb uninstall` a test app (or `pm disable-user` one) that is on Home: its icon stays in
  its cell, faint, under its old label; a tap opens edit mode on it and the remove chip takes it
  off. Reinstall (or enable) before removing: the icon comes back to life on its own.
- Owed · the page's grid-size popup (corner tab) still works with icons present: a smaller grid
  moves icons and widgets into free space or onto a new page, none dropped; back to the old size.
- Owed · rotate to landscape and back with icons on Home: each orientation keeps its own layout.


### Wall rework phase two: slice 9, the row and the floats after a background kill (feat/wall-rework-phase2)
- Owed · commit "feat(wall): an activity recreate brings the Home floats back around their shells"
  · emulator. Two windows (the second split), a Home float on page 2 minimised and one shown on
  page 1; type `echo before` in a pane of each. Rotate (`settings put system user_rotation 1`,
  activity recreate): both windows, the split, and both floats (page 2's still a chip, page 1's
  shown) are back, attached to the same shells: `echo after` in each, the scrollback above still
  shows `before`. No extra plain window appears for a float's shell. Rotate back: the same.
- Owed · commit "feat(wall): autosave the arrangement and rebuild it on a cold start" · emulator.
  `am kill com.termux` is not enough (the home app is revived): set the stock home first, `cd` to a
  different folder in each pane, wait 2 s, `am force-stop com.termux`, launch from the icon. The
  row (fresh shells; `pwd` prints each saved folder), the split and the floats on their pages (the
  page 2 one minimised) are back, the notice "Windows restored from before the restart" shows,
  and no program that was running starts again. Start in = Home shows Home page 1; with Start in =
  Workspace the Workspace shows.
- Owed · close every window and float, force-stop, launch: an empty Workspace, Home shows, no
  shell (`launcherctl` session list empty). Again with the notification's **Exit** then launch:
  the same. With Start in = Workspace either way: one fresh shell window.
- Owed · an explicit Saved sessions Load (Replace) still works, and a force-stop 2 s after it
  brings back the loaded arrangement, not the one before. The Saved tab never lists `.autosave`.
- Seen 2026-10-18 (ed6b8d160, emulator): after a rotation the split, the cwd and the float come
  back on the same shells (same pids); after a force-stop with the stock home set and a launch from
  the icon, the split, the /system/bin cwd and the float come back on fresh shells. Not seen: the
  "Windows restored" notice (owed: check it fires after a rebuild), the empty-arrangement restart,
  Saved sessions Load after a rebuild, the parallax card re-check.
- Fixed 2026-10-18 (worker branch, "fix(wall): the “Windows restored” notice waits for the
  window to be in front"): the notice went up inside the service connection, before the window had
  the focus and ahead of the rebuild's layout, so its hold ran out unseen (cause inferred, not seen
  on a device); it now waits for a started, focused window. Re-check owed: the force-stop rebuild
  above, with Start in = Home and = Workspace: the notice shows on the half in front.
- Re-check 2026-10-18 (7fccb5d8d, emulator): the "Windows restored from before the restart" toast
  shows about 2 s after a cold-start rebuild with both windows back. FAIL: typing `exit` in the
  last rebuilt window opened the Sessions drawer and the window stayed (and `launcherctl window
  close` answers not_owned for rebuilt windows), so the empty-arrangement restart could not be
  made; after a force-stop one live window came back. Owed: a shell spawned by the rebuild must
  be owned like any launcher window (close on exit, empty Workspace → Home). Saved sessions showed
  none because the earlier `pm clear` wiped the files, not a bug. Parallax card re-check not done.
- Fixed 2026-10-18 (worker branch, "fix(wall): a rebuilt or loaded arrangement takes its current
  window like any shell"): cause not confirmed off the device. A rebuilt window is held exactly
  as one opened by hand (newWorkspaceWindow puts it in the pane controller and its session in
  the row, so `exit` takes the FINISHED_WINDOW path); the one difference found is that
  restoreArrangement (rebuild and Saved sessions Load alike) only showed the current window and
  never ran activateSessionInPanes (focusSession, fonts, session list); it now does. `launcherctl
  window close` answering not_owned is by design: it closes only windows opened through
  window.open in this process (AgentPaneRegistry, not saved), as for any window opened by hand.
  Re-check owed: after a force-stop rebuild, type `exit` with the in-app keyboard in each rebuilt
  window (and a rebuilt float): each closes, the last leaves Home over an empty Workspace, and a
  second force-stop comes back empty; if the drawer still opens, capture logcat and the screen.
- Seen 2026-10-18 (454940fd8, emulator): after the rebuild and its notice, `exit` in the restored
  window closes it, Home stays with no drawer; a second cold start lands on Home with no shell,
  and the dock's Terminal entry opens a fresh window. Parallax card re-check still not done.
### Wall rework phase two: slice 7b, folders on Home pages (feat/wall-rework-phase2)
- Owed · commits "feat(home): folders as a Home item kind in the widget store (schema v7)" and
  "feat(home): folder cells, the folder popup and drop-to-merge on Home pages" · emulator,
  portrait, two or three apps on a Home page as icons (slice 7a's Add to Home). Install the build
  over a slice 7a build with icons on Home (a v6 wall): every icon and widget is where it was.
- Owed · long-press icon A, rest it on icon B: after about a third of a second B swells and the
  ghost sits on it; let go: one folder in B's cell, named after A, showing A and B in its 2×2
  miniature with the name under it. Edit mode stays open with the folder selected. Let go before
  the swell instead: A moves as before (B slides aside). Rest A on a widget: nothing swells.
- Owed · tap the folder (edit mode closed): the dock-style popup opens with A and B as icons with
  labels, the screen behind dimmed. Tap B: B opens and the popup closes.
- Owed · rest a third icon C on the folder and let go: the miniature shows three apps and the
  popup lists A, B, C.
- Owed · in the popup tap the folder's name: the caret appears; type a new name and Enter (the
  in-app keyboard, then the Android keyboard): the title and the cell's name change, and keep
  after the app is killed and reopened.
- Owed · in the popup hold C › **Remove from folder**: C is an icon on the page in front again,
  and the folder lists A and B. Then hold A › Remove from folder: the folder turns into B's icon
  in the same cell, and A is an icon on the page.
- Owed · make a folder again, long-press it and carry it against the page's edge to another page
  (and past the last page): it lands there whole and opens with its apps; the page it left goes if
  it is empty. Its remove chip takes the folder off.
- Owed · Appearance › Layout on Home › Margin · Home at 24: folder cells are inset like icons and
  widgets; back at the old Margin all return.
- Owed · an app in a folder uninstalled or disabled: its mini and its popup entry go faint; a tap
  on it offers Remove from folder.
- Owed · rotate to landscape and back with a folder on Home: each orientation keeps its layout and
  the folder stays a folder.
- Owed · a v7 blob with a malformed folder (developer, `run-as` edit of
  `shared_prefs/launcher_widget_repository.xml`: a folder record with no readable app): the app
  starts, that cell is empty and the rest of Home stands (covered by unit tests; seen on device
  once).

- Seen 2026-10-18 (ed6b8d160, emulator): three apps added from the picker with labels and the
  "Added to Home page 1" toast; tap launches; move within and across pages; drop on an icon
  makes a folder named after the dropped app with a 2×2 mosaic; drop on the folder adds; launch
  from the popup; Remove from folder places the app; the folder dissolves at one app; a disabled
  app draws grey and its tap opens edit mode; icons and folders survive a force-stop. Not seen:
  the folder rename (typed "Tools", the label stayed: likely the focusless rename not taking keys
  on Home; owed), Margin · Home with icons, the grid-size popup, the drawer's Add to Home route,
  the empty page being dropped.
- Fixed 2026-10-18 (worker branch, "fix(folders): a folder rename on Home takes the keyboard's
  keys"): the popup is outside-touchable, so a key press on the in-app keyboard closed it and
  cancelled the rename, and on Home the rename raised no keyboard at all. The popup now stays open
  for keyboard touches during a rename, and the rename raises the in-app keyboard (else the system
  IME through a focused terminal). Re-check owed: Home and dock folder rename with the in-app
  keyboard, then the Android keyboard, Enter commits; a tap elsewhere still closes the popup.
- Re-check 2026-10-18 (7fccb5d8d, emulator): Margin · Home insets icons, the folder and widgets
  together; the grid-size popup re-lays items with nothing lost; the folder rename takes hardware
  keys (`ad type`, Enter) but not touches on the in-app keyboard while the popup is open, and the
  popup dims the keyboard: a PopupWindow gets outside touches only as ACTION_OUTSIDE, so the
  keyboard under it can never be pressed; owed: the Home (and dock) folder popup as an in-activity
  overlay, or the keyboard inside the popup's touch path. Also: the popup's title and member
  labels are dark on dark glass, near unreadable. Not reached: Add to Home from the drawer (the
  checker could not open the drawer), the empty page being dropped.
- Fixed 2026-10-18 (worker branch, "fix(home): the Home folder popup is a layer over the Home
  pane…" and "…title, labels and rename read on light and dark glass"): the popup was a
  PopupWindow, so the keyboard below got only ACTION_OUTSIDE and the window's dim covered it;
  it is now a view over the Home pane (HomeFolderOverlay), under the in-app keyboard, the dock
  and the status bar, dimming the pane only. A tap on the dim closes it without reaching Home
  under it; a tap outside the pane closes it unless it is a rename key. The dock's folder popup
  is unchanged (still a window). The title, labels and rename take the capsule's ink rule on the
  chrome's polarity while the wallpaper shows through (dock opacity below 50). Re-check owed:
  Home folder rename typed with the in-app keyboard, keyboard undimmed, Enter commits; tap on the
  dim and on the dock each close it; the title and labels read on a dark and a light wallpaper,
  in both themes, and with the dock opacity at 80.
- Seen 2026-10-18 (454940fd8, emulator): the folder rename typed on the in-app keyboard lands on
  the popup and the label, the keyboard is not dimmed, a tap on the dim closes the popup without
  pressing the page; the title and labels read on a dark and a light wallpaper; an icon dragged
  past the last page makes a page, moved back the empty page is gone. Not found: a drawer route
  with "Add to Home" (a dock icon's long press opens Edit pinned apps, A–Z letters filter the
  dock, the palette's app rows bind keys): the sheet's "Add an app" is the only entry seen;
  developer to say where the drawer route should live. Polish seen: opening the palette on Home
  leaves a grey highlight panel around a folder cell until the palette closes; on a light
  wallpaper the icon labels (white with a shadow) read weakly; the pinned-apps editor's app icons
  are tiny while the Add an app list's are full size.
### Display as a window (10-19)
Supersedes the screen-glyph and "lands on the last terminal window" parts of #77 display above.
- 2026-10-19 · commits "feat(wall): the display window is a window of the row" · Two terminal
  windows, then Display under Linux Apps from Home: the wall slides to the Workspace with the
  display window in front, its chip last in the row and selected; no Display page, and no screen
  mark at the status bar's right edge, with or without a display up · pong.
- 2026-10-19 · same · From the display window, a border drag left: the last terminal window comes
  in beside the display, the display slides out (not a blank gap), the keyboard and the extra keys
  become the terminal's; drag right again: the display comes back, typing and the touchpad reach X
  · pong.
- 2026-10-19 · same · Tap the display's chip from a terminal window and a terminal chip from the
  display: the window switch travels like any window's; the terminal never resizes (no prompt
  redraw, no SIGWINCH line in a running `htop`) · pong.
- 2026-10-19 · same · Hold the display's chip and drag it to the front of the row: it stays there
  after a window opens or closes elsewhere; `launcherctl window.move to=1` on the display window
  does the same; `+` on the display window opens a terminal window right after it · pong.
- 2026-10-19 · same · × on the display's chip with xeyes open: asked first, xeyes closes, the
  server stops (`pgrep -f termux-x11` empty), the window on its left comes in front; with no app
  open it stops at once; on an unstarted display (no xkeyboard-config) it just goes · pong.
- 2026-10-19 · same, review fixes · Close the last terminal window while the display window is in
  the row: the display window comes in from the left instead of Home sliding in; in a row a, D, b
  close b while it is in front: the display window takes the front (no blank page) · pong.
- 2026-10-19 · review fixes · On the display window: the Terminal key (`wall.terminal`) and the
  dock's Terminal entry bring the terminal window behind it in front (a fresh shell when the row
  has none); Ctrl+Alt+X from the palette or `launcherctl window.close` stops the display (asked
  first with an app open) and never closes the hidden terminal window; Rename window does
  nothing; `launcherctl pane.expel to=right` answers `display_window` · pong.
- 2026-10-19 · review fixes · From the display window to a terminal by chip or border drag: the
  display keeps its picture while it slides out (no black or frozen frame), and typing reaches the
  terminal at once; then a dark/light mode switch (activity recreate) with the display window
  second in the row and in front: it comes back second and in front · pong.
- 2026-10-19 · "feat(display): the display window's chip names the app in front" · Open Firefox
  from the drawer: the display's chip reads Firefox (the drawer's name for it) with Firefox's icon,
  not Display; open xeyes too and focus each in turn: the chip follows the focused app; close both:
  it reads Display with the screen glyph again; TalkBack on the chip says "Firefox, Display" · pong.
- 2026-10-19 · review fixes · Palette window jumps with the display window first in the row: each
  jump lands on the window it names; `launcherctl pane.list` reports `rowIndex` one more than
  `index` for windows after the display · pong.
- 2026-10-19 · same · `launcherctl wall.go page=display` and the `wall.display` key: open the
  display when none is up, bring the window in front when one is; from another session's row it
  moves into this session's row at its end · pong.
- 2026-10-19 · same · Keyboard form on the display window: in landscape the keyboard floats over
  the display, in portrait it makes room, as before; mouse mode's key swaps pad and keyboard; the
  space bar's corner swipes step from the display window to its neighbours in the row and back
  (no longer through the display's apps) · pong.
- 2026-10-19 · same · Settings → Display → Linux apps has no Display badge row and otherwise looks
  exactly as before · pong, screenshot against the previous build.

### Settings restructure (10-19)
- 2026-10-19 · fixes after the emulator pass of 0fa4635f7 · Drawer search, with a keyboard that
  draws (Gboard or AOSP, not Mobilerun's headless IME): open the drawer from Home and from the
  Terminal, tap Search apps, type "fir": the grid filters, nothing reaches the shell behind; with
  the in-app keyboard on, its keys filter too. Back once with the system keyboard up closes the
  keyboard (Android's), again clears the query, again closes the drawer. Use Android keyboard on:
  same, and a hardware/adb key typed before the field is tapped lands in the query, not the shell.
  Settings › Home with Lock Home layout on: Edit pages is dimmed, has no chevron and a tap or a
  D-pad focus does nothing; off: as before. Edit pages at font scale 1.3: the default page's
  "Home opens here · …" wraps whole. Settings root: search "12-hour", open the result, Back to
  the root: the search box is empty and the root's own rows show. Open a search result: the old
  results fade out before the page fades in, no frame with both. Restart launcher with a Home
  float on screen: no black float frame · emulator, light and dark, font scale 1.3.
- 2026-10-19 · waves 3 and 4 (F12-F17, F19) · Drawer, emulator, light and dark, font scale 1.3.
  Settings › Dock & drawer › App search › Open the only match on: type a name that matches one app,
  it opens after a short pause, typing on cancels it; off: only Enter opens it. Drawer layout page:
  Remember position on, scroll the vertical list, close and reopen (also the paged layout: the page);
  closing on a search starts at the top. Start in the lower half on, portrait: the list and its
  letter strip start at the middle, typing a query lifts them to the top, landscape and
  keyboard-on-open leave them alone. A-Z strip Left: strip on the left, the list shifted right, the
  rope leans and tilts the right way, scrubbing works and a close-drag still starts from the pill;
  Off: no strip, the list takes the width; Right is as before. Settings › Notifications › Badge
  style Count with dots on: a dock icon with three notifications shows 3, a folder with notifications
  in two apps shows their sum, 100 or more reads 99+, text readable on light and dark fills; Dot as
  before. Settings › General › About › What's new opens this build's notes (a pre-release build the
  channel list, a stable build its release) with a working link to the full notes; the Version and
  build row still opens the build report. Settings › General › Restart launcher: Cancel does
  nothing; Restart returns to a freshly built launcher, a terminal session started before still
  shows its output, never a black screen; tap it twice quickly, one restart.
- 2026-10-19 · review fixes on wave 2 Home · Locked layout: tap a gone app's faded icon (nothing
  opens), open a folder (its name is not a rename, holding an app offers nothing), and Settings ›
  Home › Edit pages is greyed with "Unlock the Home layout to edit pages" (unlock with the switch
  on the same page: the row comes back at once). Edit pages from Settings returns to Settings ›
  Home on Done. Reorder pages in portrait, turn the phone: landscape shows the same order;
  remove a page in landscape, turn back: portrait lost the same page. A Home float on a page
  that empties (its last widget removed) follows to the page in its place, and the default page
  follows a trim. A folder grown to 18 apps survives a force-stop. With the accessibility service
  turned on, its description in Android's accessibility settings reads "Lets the launcher lock the
  screen and open notifications or quick settings from your gestures." Notifications by Shizuku
  with Shizuku stopped mid-way shows "Shizuku couldn't open that…" · emulator.
- 2026-10-19 · wave 2, Home (F5–F11) · Screenshots of Settings › Home top to bottom against the
  wave-1 build: the new groups (Icons and labels, Folders, the five gesture rows, Page transition,
  Page indicator, Edit pages, Lock Home layout) wear the same row look (height, chevron-less
  value rows, switches, category headers), nothing clipped at font scale 1.3; search "transition",
  "two-finger", "lock home" lands on each row · emulator, light, dark and black, the Material You
  scheme.
- 2026-10-19 · commit "feat(home): folder density and max folder size" · A Home folder of 14 apps
  opens as before at the defaults (four across); Compact shows five across in smaller cells,
  Comfortable three across in larger ones, and no density's popup runs off a 360 dp screen. Max
  folder size 9: a folder already holding 14 keeps them, takes no more (a drop beside it moves
  instead of merging); 20: a folder takes 20 and still holds 20 after a force-stop. The folder's
  apps wear Icons and labels' size. Dock folders unchanged · emulator, font scale 1.3, light and
  dark.
- 2026-10-19 · commit "feat(home): edit pages: reorder, remove, add and the default page" · From
  Home's sheet (Edit pages; the pencil's row now reads Edit widgets) and from Settings › Home ›
  Edit pages (grows from the row, lands on Home with the editor up, Done returns to Settings):
  three pages with widgets and icons and a Home float on page 2. Move page 1 down: its widgets
  move with it and the float stays with its page; make page 3 the default ("Home opens here"):
  the Home press from another app lands on it, and so does a cold start with Start in = Home;
  remove the float's page: it asks first, naming the items, then the float (shell alive) stands on
  the page now in its place; remove an empty page: no question; Add page adds an empty page at the
  end that stays. The last page has no remove. Locked layout: the sheet has no Edit pages and the
  Settings row shows the lock notice. Rotate after a reorder: the other orientation's layout is
  still whole (it is not reordered; note what it shows) · emulator, light and dark, font scale 1.3.
- 2026-10-19 · commit "feat(home): lock Home layout" · Long-press an empty spot: the sheet ends its
  page group with Lock layout; tap it ("Home layout locked"). Now a long press on an icon, a
  folder or a widget shows "Home is locked…" and nothing lifts; the corner tab's pencil and + show
  the same and open no wheels; taps still launch apps and open folders. The sheet now shows
  Unlock layout under Terminal and no Add or Edit rows; Unlock restores everything. Settings ›
  Home › Lock Home layout shows the same state both ways. The sheet still fits at font scale 1.3
  with every row (not the default home, an empty second page) · emulator, light and dark.
- 2026-10-19 · commit "feat(home): page indicator, dots or off" · With two or more Home pages the
  dots show at the default; Page indicator Off hides them and no widget or icon moves or resizes
  (screenshot before and after); back to Dots brings them back · emulator, light and dark.
- 2026-10-19 · commit "feat(home): icon size, label size, two-line labels and label shadow" ·
  Before/after screenshots of a Home page with app icons and a folder at the defaults: identical.
  Then Icon size 80% and 120%, Label size Small and Large, Two-line labels on (a long app name
  wraps to two lines, a short one keeps one, a small cell falls back to one), Label shadow off on a
  light wallpaper. The dock's icons do not change. At font scale 1.3 (force-stop after the change)
  and on a 5-row grid no label is cut at its bottom or sides · emulator, light and dark, the
  Material You scheme.
- 2026-10-19 · commit "feat(home): a gestures table for Home's free area" · With nothing set,
  Home behaves exactly as before: swipe up opens the palette with the keyboard, swipe down closes
  it (and puts a raised keyboard away), double tap and two fingers do nothing, a single tap on a
  widget or icon launches at once. Set each gesture in turn and try it on the free area: Command
  palette, App drawer, Terminal (the wall slides over), Open an app… (the chooser lists apps; the
  row then reads "Opens <app>"). With the accessibility service off and Shizuku disconnected the
  rows offer no Lock screen, Notifications or Quick settings and the "More gestures" row explains
  why; turn the service on and come back: they appear, and each works. Swipe down set to something
  still closes an open palette first. A double tap or two-finger swipe started on a widget, a Home
  float, the A–Z rail or the dock does nothing new; sideways swipes still page Home and cross the
  seam; a long press still opens the Home sheet; the bottom-border keyboard swipe and top-border
  status swipe are unchanged · emulator, keyboard up and down, light and dark.
- 2026-10-19 · commit "feat(home): page transition picker with a live preview" · Settings › Home ›
  Page transition grows out of its row; the miniature plays once on open and once per pick, a tap
  plays it back, then nothing moves; Apply returns to Home's page with the effect named under the
  row. On a Home with three pages, each effect (Slide, Depth, Fade, Carousel, Stack) on a slow drag,
  a release short of the commit (settles back), a fling, and a pull against page 1 and the last page
  (only a slide); the seam from the last page into the Terminal still slides, and the wallpaper
  pans with every effect. With a Home float and a glass widget on the page, both move with the
  page and their glass shows no seam or jump. Animation speed Slower / Off and Remove animations:
  the settle stretches, or lands at once · emulator, light and dark, keyboard up and down.
- 2026-10-19 · feat(settings) motion · The four motions feel right and never stutter on a long
  page (Keyboard, Linux apps); the row ring reads as "here it is", not as an alarm · pong, by hand.
Spec: `project-docs/active/settings-restructure-2026-10-19.md`.
- 2026-10-19 · commit "refactor(settings): regroup Settings by the wall's halves" · Before/after
  screenshots of every moved screen: the root, Home, Dock & drawer, Terminal and its four new
  subpages, Linux apps, Look, Keyboard (with its Look group), General. Every row looks as it did
  (row height, chevrons, switches, pills, slider, the search pill); only the grouping changed ·
  emulator, light, dark and black, the Material You scheme, font scale 1.3, against the previous
  build.
- 2026-10-19 · same · Home › Layout and Terminal › Layout open the Layout editor on that half;
  closing the editor returns to Settings on the page it was opened from · emulator.
- 2026-10-19 · same · Open Settings on Status bar with the previous build, force-stop the app,
  install this build, reopen within 30 minutes: Terminal › Status bar shows, Back goes to Terminal,
  then the root · emulator.
- 2026-10-19 · commit "feat(settings): search every screen and land on the row" · On the root
  type "vibration": the sections give way to results with their paths; tap one: its page opens
  scrolled to the row, which rings once (fades in and out, nothing keeps moving); Back walks up
  through its section. The keyboard's search key opens the first result. From Home's toolbar
  search the field opens focused with the keyboard up, reading "Search Home" · emulator, light
  and dark, font scale 1.3.
- 2026-10-19 · commit "feat(settings): Material motion between settings screens" · Root → Terminal
  → Text & display slides forward, Back slides back; Keyboard → Keyboard theme grows out of its
  row and fades away on Back; a search result fades through; Home › Layout opens the editor
  growing from the row; with Remove animations on, every one of these is an instant cut ·
  emulator, screen recording.
- 2026-10-19 · commits "feat(settings): two panes on wide screens", "fix(settings): two panes
  only on tablets" · A tablet-sized screen (`wm size 1600x2560`, density 320): the list beside
  Home; tap Terminal: the right side fades to Terminal; Text & display slides in on the right;
  Back, then Back again leaves Settings. A phone turned to landscape stays one pane, with no
  rebuild flash on rotation · emulator with `wm size`, then the Surface.
- 2026-10-19 · same · Doors from outside Settings land where they did: the display window's cog
  (Linux apps), the Apps bar opener (Dock & drawer), Look and feel (Look), the drawer's settings
  (Drawer layout), the keyboard theme from the Appearance editor, Edit extra keys · emulator.

### Terminal body swipe (10-19)
- 2026-10-19 · feat(wall) "a sideways swipe on a window's text pages the row" · Three windows
  and the display window, keyboard down then up: a horizontal swipe across the middle of window 1's
  text goes to window 2, the same from the last terminal window to the display window, and right
  from window 1 to Home's last page, each following the finger with the border drag's sink, plank
  and settle; a short swipe springs back. A vertical swipe and a 45-degree one scroll the
  transcript and never move the wall. A tap, a long press (selection starts), a pinch (font size)
  and a link tap behave as before. With a selection up, in `htop` or `vim` with `set mouse=a`
  (mouse tracking), and in mouse mode, a sideways drag stays the terminal's. On the display
  window's picture a sideways drag goes to the X app, its border still pages. Split into two
  panes: a swipe on either pages the row; a press on the gap between them still resizes, the
  tab's buttons still work, a corner hold still raises the tab. Settings › Terminal › Windows &
  panes › Swipe on the terminal to switch windows off: only the border pages; on again at the next
  touch. A Home float's terminal still pages Home by the same swipe · emulator, then pong.

### Seam travel and Workspace dock (10-19)
The Workspace's missing dock on pong is the developer's own Terminal layout (no pinned apps
element), not a defect; nothing about the dock's presence changed.
- 2026-10-19 · f2f54cc4b · Terminal layout without a dock, Home with apps and A–Z, keyboard down
  on both: slide from the last Home page to the terminal and back, slowly by the border drag and
  by a fling. Home's dock sinks below the screen's edge as the terminal comes in and rises as
  Home comes back, in step with the page; nothing appears, vanishes or jumps at settle. Capture
  the slide at 0, 25, 50, 75 and 100 % each way (`screenrecord`, frames pulled at the quarters) ·
  emulator, then pong.
- 2026-10-19 · f2f54cc4b · The shipped Terminal layout (apps, A–Z, extra keys) against Home's
  apps and A–Z: the whole dock goes down over the first half and the other half's rows rise over
  the second; at 50 % no dock row is on screen and nothing flashes there. Drag to 40 %, then to
  60 %, and back again across the midpoint: the rows follow the finger both ways, never pop · emulator.
- 2026-10-19 · f2f54cc4b · Keyboard up on the terminal (and Home with always on, then without):
  the rows sink into the keyboard's top edge rather than carrying the keyboard down; the keyboard
  ends where it rests on each side; the dock's glass has no seam or stale sliver at the keyboard's
  edge at 50 % · emulator.
- 2026-10-19 · f2f54cc4b · Status bar along the top: toward Home it slides out sideways with the
  terminal page (chips, clock and glass together), toward the terminal it slides in with it from
  the right edge; its window chips stay the Workspace's the whole way, the badge too; Docked and
  Floating Style, light and dark, and the strip behind the system bar moves with the bar in Docked.
  Under a joined Docked frame, look for the bar's glass staying behind while its content moves ·
  emulator.
- 2026-10-19 · SeamDress (45d0f76b8) · pong's setup (Terminal layout without a dock, Home with
  apps and A–Z) with Home set apart: Home Floating and the Workspace Docked, then the reverse;
  Home's Corners and Margin different from the Workspace's; Home's dock height different in the
  Layout editor. Slide terminal → Home slowly and by a fling: Home's dock rises already at its own
  size, radius, margin and Style and does not change in the last frames or at settle. Home →
  terminal: it sinks unchanged. Frames at 0, 25, 50, 75, 92 and 100 % each way, plus one just after
  settle · emulator, then pong.
- 2026-10-19 · SeamDress (45d0f76b8) · Same Home set apart, with the shipped Terminal layout
  (a dock on both halves): the whole dock goes down over the first half and comes up in the other
  half's dress over the second; at 50 % nothing of it is on screen; no row changes size or radius
  while visible. Keyboard up on the terminal, and Home with always on: the keyboard goes down with
  the dock and comes back in the other half's height and shape (it no longer stays up across the
  seam while the halves are dressed apart). Drag to 40 %, 60 %, back to 40 % and let go: the dock
  comes back in the terminal's dress, nothing pops on the spring-back · emulator.
- 2026-10-19 · SeamDress (45d0f76b8) · Status bar along the top with Home set apart (Floating vs
  Docked both ways): from Home toward the terminal the bar arrives already in the Workspace's
  shape (capsule or full-bleed, its strip behind the system bar) and does not change at settle;
  toward Home it leaves unchanged. The terminal page's own frame and panes still switch at settle
  from Home (known, to judge rather than pass) · emulator.
- 2026-10-19 · SeamDress (45d0f76b8) · Unchanged with Home not set apart (the default install):
  the 10-19 seam items above still pass, and the terminal's dock padding (the glass taller by up
  to a text row on the terminal) is never worn by Home's rows on the way to Home. Also: a Docked
  Workspace with a side status bar (joined frame) against a Floating Home: Home's rows keep glass
  of their own during the slide; the slide under reduced motion and Animation speed Slower; a
  split terminal; landscape · emulator.
- 2026-10-19 · f2f54cc4b · Unchanged elsewhere: a side rail or A–Z on a side edge in either
  layout, the status bar on a side or the bottom, reduced motion (everything snaps at settle),
  Animation speed Slower, a split terminal, landscape, the display window's slide to the terminal
  (no seam: the bar stays put), and a Home float minimised to a chip (the chip keeps its place
  during the slide, settles once) · emulator.
- 2026-10-19 · PageTopInsets · Bar along the top, keyboard down: slide terminal → Home and Home →
  terminal, by the border drag (stop and hold at 10 %, then let it spring back) and by a fling. The
  terminal's text and the top of Home's grid never move vertically: not on the first frame, not
  during, not at settle, and not on a spring-back. Take a frame just before and just after settle
  each way and diff the terminal's first row and Home's first cell rows; also check the terminal
  does not reflow (`stty size` before and after the round trip is the same, and a full-screen
  program such as `htop` gets no redraw at settle) · emulator.
- 2026-10-19 · PageTopInsets · The same round trip on every surface it could break: Docked and
  Floating, compact and expanded bar (fold the bar on the terminal, then slide), keyboard up on the
  terminal, a split terminal (2 tiles) and a floating pane, the display window in front of the
  Workspace, the plain terminal border on (its frame line must frame the page on the first frame of
  the slide, not sit a bar's height above it), opaque mode (wallpaper off: no terminal colour under
  the status bar during the slide), compatibility mode (no bar: nothing moves either), the bar on
  the bottom edge, landscape with an extra-keys column on a side (its keys keep their size during
  the slide) · emulator.
- 2026-10-19 · PageTopInsets · At rest on the terminal after a round trip, everything that reads
  the terminal's frame still lands on it: the keybind hint panel and a mode legend (copy/find),
  the background-process stack, the help overlay's border strips and the tour's top-border
  pointer, the Appearance editor's terminal outline, the Layout editor's miniature, and the top
  border's swipe that folds the bar (it starts below the bar, not under it) · emulator.

### Widgets wear the Look's glass (10-19)
Built-in widgets with Glass on now wear the Look's (Base's) blur, opacity, tint, grain, rim and
Fancier Glass through the terminal pane's slab, and every built-in widget is cut at Base's Corners.
Glass off is now the solid card everywhere (it used to go see-through while the terminal panes were
glass). Third-party widgets are unchanged: the launcher draws no card behind them.
- 2026-10-19 · feat(widgets) Look's glass · Wallpaper mode, a Clock, a Battery and a Terminal
  widget on Home with Glass on: each shows the wallpaper's blur, tint and grain as the terminal
  pane does with every surface inherited, with the gradient rim, and the Terminal widget's text
  sits on that glass (no second opaque card under it). Turn Glass off on one: it becomes the solid
  card at once, the others keep their glass · emulator.
- 2026-10-19 · feat(widgets) Look's glass · Appearance editor, shared (Base) layer: drag Blur,
  Opacity, Grain, Tint and Corners, then pick two Looks from the menu; the glass widgets follow
  each change live without flicker or reflow, and every built-in widget (Glass on or off, the
  Terminal widget's terminal included) takes the new corner. Then detach the terminal (and the
  keyboard, status bar, dock) and move only its values: the widgets do not change · emulator.
- 2026-10-19 · fix(widgets) corners in place · During a Corners or Opacity drag, the widgets'
  corners and inner shapes (Notes' square, a notification card, Media's art, Calendar's today)
  follow every tick with no flicker, a clock keeps ticking, and the Terminal widget's text is not
  redrawn from scratch; its text clearance and ground land once, about a quarter second after the
  slider stops (perfetto: no `Choreographer#doFrame` layout pass per tick from the widgets) ·
  emulator.
- 2026-10-19 · feat(widgets) Look's glass · Home's own Corners set apart from the Workspace's in
  the Layout editor: widgets keep Base's Corners, not Home's (decided: they follow the Look) ·
  emulator.
- 2026-10-19 · feat(widgets) Look's glass · Wallpaper mode off: a Glass-on widget shows the
  see-through card with no blur; on again, the blur comes back without leaving the page. Light,
  dark and black themes, Material You and the launcher's scheme chrome: the glass widgets' tint
  follows the dock's glass colour in each · emulator.
- 2026-10-19 · feat(widgets) Look's glass · Memory: with no Glass-on widget, `dumpsys meminfo`
  PSS at rest is unchanged against the build before; with Glass on and a Base blur that no surface
  uses (every surface detached to another blur), at most one more blur frame is resident and it is
  let go when the last widget turns Glass off and the page is next dressed · emulator.
- 2026-10-19 · feat(widgets) Look's glass · A small widget (1×1) at a large Base Corners (40dp):
  the card, the glass and the content clip all round at the same, capped corner; no content is
  cut in the corners of Notes, Media or Calendar at Corners 0, 24 and 40 · emulator.

### Page transition across the wall (10-19)
- 2026-10-19 · feat(wall) page transition across the wall · Settings › General › Motion now holds
  Page transition (gone from Home's Pages); a search for "page transition" finds it there, its
  summary names the effect, the preview opens from its row; an old link to Home's row lands on
  General · emulator.
- 2026-10-19 · feat(wall) page transition across the wall · Slide: the seam (last Home page ↔
  Workspace), a border drag and a body swipe between two terminal windows, a chip tap, a keyboard
  chord and `launcherctl` window switch look exactly as on the build before (screenrecord both,
  frames at the quarters) · emulator.
- 2026-10-19 · feat(wall) page transition across the wall · Each of Depth, Fade, Carousel and
  Stack, one pane and then two panes, keyboard down then up, Docked then Floating dock: (a) the
  seam both ways by drag and by fling, Home with a Home float on its page; (b) window to window by
  border drag, by body swipe released half way, by chip tap and by chord; (c) a terminal window to
  the display window and back. Each crossing poses both pages as a Home turn does (Depth: the page
  leaving forward sinks and fades in place, the next slides over it; Stack: the leaving page
  slides off the top, the next rises from beneath; Fade; Carousel turns about the shared edge),
  the dock rows, status bar and keyboard travel exactly as under Slide, the page lands flat with
  nothing left scaled, faded or turned, and a drag released half way continues from where it was
  with no jump of the leaving window. A pull into either end of the row only slides · emulator.
- 2026-10-19 · feat(wall) page transition across the wall · The display window always slides
  (its picture is a live surface); the window beside it still takes the effect, and neither
  flashes black or leaves a hole as the display comes or goes · emulator, a display up.
- 2026-10-19 · feat(wall) page transition across the wall · Reduce motion on, and Animation speed
  Off: every crossing lands at once with no effect frame; Slower and Faster stretch the seam and
  border-drag settles. Window switches by chip and chord keep their own fixed time under every
  speed, as they did before · emulator.

### Living glass (10-19)
- 2026-10-19 · feat(glass) living glass · Fancier Glass on, pane glass, Living glass on: a chip-tap
  window switch, a seam fling, a pane swap (FLIP move) and a float drag released quickly each show
  the slab's rim bending more on the edge that leads and less on the one behind while it moves,
  then rocking back once (perhaps a faint second time) as it lands, flat again within about 400 ms;
  a slow divider or float drag barely changes the rim. No band of light runs anywhere.
  Screenrecord, frames at the quarters and 400 ms after landing · emulator (AGSL needs API 33+).
- 2026-10-20 · feat(glass) no dent under a press · Holding a finger on a terminal pane no longer
  dents the glass: no dip or spread under the finger while held and no bump on release (the
  "ripple" the user disliked). The pane still tips on its press rig and the slab follows it (the
  rim away from the finger thickens a little, the face swims about 1.5 dp) and lies flat again
  after the lift; starting a scroll, a body swipe or a border drag still works · emulator.
- 2026-10-19 · feat(glass) living glass · The shader compiles on the device: the glass still
  refracts after the update (no fall back to the plain frost, no "Glass refraction shader failed"
  in logcat) · emulator, then pong.
- 2026-10-19 · feat(glass) living glass · Each of these keeps every case above at the resting look:
  Developer options' animator scale off (animations
  removed); General › Motion › Animation speed Off; lazy mode; battery saver. Slower and Faster
  stretch and shorten the settle · emulator.
- 2026-10-19 · feat(glass) living glass · At rest nothing draws: `dumpsys gfxinfo` over 30 s
  untouched after a window switch matches the build before (5 frames in 30 s on pong's baseline),
  i.e. the springs stop asking for frames once settled (about half a second after landing) ·
  emulator for the count's shape.
- 2026-10-20 · feat(glass) living glass always on · Settings › Look shows no Fancier Glass
  category on any Android version, a search for "living glass" finds nothing, and an install that
  had Motion light off now moves like the rest · emulator, API 33+.

### Tilted glass (10-20)
- 2026-10-20 · feat(glass) tilted glass · Fancier Glass on, Living glass on, two wall pages with
  glass: drag slowly between pages and hold mid-way (planks tipped, up to 18°): the picture inside
  each slab sits a few dp off the wallpaper in the gap beside it, carried toward the edge going
  away, a little larger on the near half; the near edge's rim bends more and is brighter, the far
  edge's thins and dims. Released, it slides back as the pages lie flat, and the settled glass
  matches a screenshot taken before the drag pixel for pixel. No fold, mirror strip or tear at
  any slab edge, corners included. Screenrecord, frames at a quarter, half and landing ·
  emulator (AGSL needs API 33+).
- 2026-10-20 · feat(glass) tilted glass · Hold a wall page's left or right border until it sinks
  (6° press tip): the same displacement and rim lean, toward the held side, while held; lifting
  brings it back to flat. A finger on a terminal pane (the 1.1° press rig): the rim on the edge
  away from the finger thickens a little and the face swims about 2 dp as the pane tips and
  untips · emulator.
- 2026-10-20 · feat(glass) tilted glass · Living glass off, animator scale off, reduced motion,
  lazy mode and battery saver each keep tipped slabs drawing exactly as flat ones (the plank
  still tips; the glass in it does not swim). After a held press on a tipped pane is let go and
  everything settles, `dumpsys gfxinfo` over 30 s shows no extra frames against the build before
  (the slab stops asking for frames once its tilt is flat or has held still six draws) · emulator.

### Glass perception (10-20)
- 2026-10-20 · feat(glass) glass perception · The shader still compiles on the device (no
  "Glass refraction shader failed" in logcat; panes, dock, bars and the keyboard host still
  refract) · emulator, then pong.
- 2026-10-20 · fix(glass) the glint is motion only · Rest look, Fancier Glass on, Clear and Mist,
  the same wallpaper: a still pane's top-left corner and the dock's top-left corner pixel-match
  the build before 894dd56c1 (the travelling glint), crop both at 48 dp round the corner and
  diff; the bars and the keyboard host likewise. No bright wallpaper-coloured spot at any
  top-left corner at rest · emulator.
- 2026-10-20 · fix(glass) the glint is motion only · Living glass on, a wall page dragged and held
  at the planks' 18°: the top-left light narrows into a brighter glint as the page tips, slides
  round its corner toward the edge rising toward you, with a faint one bottom-right; released, it
  fades back to the wide rest light as the page lies flat, with no snap at the end, and the
  settled glass matches the pre-drag screenshot pixel for pixel. A finger held on a terminal pane
  (1.1°) shows only a faint hint of it. Screenrecord, frames at a quarter, half, landing and
  500 ms after · emulator (AGSL needs API 33+).
- 2026-10-20 · feat(glass) glass perception · A chip-tap window switch and a seam fling: the glint
  appears while the pane moves, leans a little toward the edge that leads and fades out as it
  lands; it never moves or shows while the pane is still · emulator (AGSL needs API 33+).
- 2026-10-20 · feat(glass) glass perception · The same 18° hold: the face's picture now swims
  less as a whole (about 7 dp, was 10) and is visibly larger toward the near edge and smaller
  toward the far one; no fold, mirror strip or tear anywhere, small panes and corners included.
  On Clear, while a pane moves, its rim's picture is sharper than at rest and settles back to the
  rest look; the text area of the pane never sharpens · emulator.

### Home dock styles (10-20)
- 2026-10-20 · commit "feat(dock): pick Home's dock style in the Layout editor" · Settings › Home
  › Layout (or the editor opened on a Home page, Home filter on), in Layout mode tap the dock on
  the canvas: Row B swaps Corner radius and Margin for five previews with their names (Glass tray,
  Lens beads, Taskbar, Prompt line, Wall strip), Glass tray filled; the sheet's top edge and
  Row A do not move, nothing clipped at font scale 1.3 (names may ellipsize, never overlap).
  Tap Lens beads: it fills, Glass tray clears, the live dock behind is still the glass tray (no
  blank dock, no crash); tap a preview's name: same. Tap the canvas away from the dock: Corner
  radius and Margin come back. Undo appears after a pick and puts Glass tray back; Discard after
  a pick does too; Done keeps it across a force-stop (`layout.home.dock_style` in the prefs).
  Home filter off (a window): tapping the dock shows no dock styles. The previews read in light,
  dark and black and the Material You scheme; TalkBack reads each chip's style name ·
  emulator, portrait and landscape.
- 2026-10-20 · commit "fix(editor): Undo keeps its place, so the top pill never shifts" · In the
  Layout editor, pick a dock style (or any change): Undo appears left of Done and the top pill
  (palette | Layout | icon pack) does not move (crop the bar before and after: the pill's left
  edge within 1 px). The pill also stands at the same x on the Wallpaper, Look and Icon pack
  pages, and still fits with Undo and Done at 360 dp · emulator.

### Home dock: Glass tray (10-20)
- 2026-10-20 · feat(dock) Glass tray · Home, Floating, apps row alone and with the A–Z row under
  it, two dock pages: the page dashes stand on the wallpaper 2–3.5 dp above the slab's top edge,
  not inside the glass; the air above the icons equals the air below them (crop the dock, measure
  icon top to slab top and icon bottom to slab bottom, within 1 px); a swipe up from the dock's top
  edge still opens the palette with the keyboard. Swipe to the Workspace: its dock is pixel-for-
  pixel the build before this change (dashes inside, no shelf, no reflection), and back on Home the
  tray returns at settle · emulator (AGSL needs API 33+).
- 2026-10-20 · commit "fix(dock): one cue over Home's dock, the dashes or the pill" · Home, Glass
  tray and Lens beads: with one dock page Home's grabber pill stands just over the dock (no
  dashes); pin apps until there are two pages: the pill goes and only the page dashes show (zoom
  the band above the slab: one cue, nothing stacked or overlapping); unpin back to one page: the
  pill returns. Either way a swipe up from the dock's top edge (by the pill or the dashes) still
  opens the palette with the keyboard, and on a window the bottom border's pill is unchanged ·
  emulator.
- 2026-10-20 · feat(dock) Glass tray · The tray's glass on Home, Floating, over a dark and a pale
  wallpaper: the refraction runs in a soft band about 24 dp in from the rim with no ring or line
  where it meets the frosted face; the lower edge has a slightly brighter lip; under the slab a
  soft shadow, darkest at the contact line, with a faint light line in it, no hard edge, and it
  follows the dock while the drawer lifts it and vanishes with it. Docked: no shelf, no shadow.
  Below Android 13 (an API 31 emulator): today's plain frost, no crash · emulator.
- 2026-10-20 · feat(dock) Glass tray · Icon reflections on Home: a faint mirror of each icon
  under it in the face, clearer toward the slab's lower rim, a squeezed upside-down copy in the
  top 6 dp; subtle (if it reads as a second row of icons it is too strong). Pin, unpin and
  reorder an app, change the icon size: the reflection follows within about 0.2 s. During a page
  swipe it fades out over about 0.15 s, moving with its icons as it goes, and fades back in about
  0.3 s after the new page settles: screen-record a swipe at 60 fps and step it, no frame where
  it vanishes or appears at once (commit "feat(dock): the icon reflection fades for a page
  swipe"). At rest `dumpsys gfxinfo <pkg> framestats`
  shows no new frames over 10 s with the dock idle; logcat has no "Dock reflection capture
  failed" or "Glass refraction shader failed" · emulator.

### Drawer folder pull (10-20)
- How to open the drawer on the emulator: the dock's pull opens it on release only past half its
  travel (about 365 px at 2400 px, from the dock row) or on a fling, and injected swipes release
  with no speed, so a pull from Home's bottom dock (about 250 px of screen below the row) never
  opens it. Use either the palette (swipe up on Home's free area, `ad swipe 540 1700 540 900
  700`, type `app drawer`, Enter: the App drawer action) or Settings › Home › Gestures › Swipe
  down → App drawer, then `ad swipe 540 900 540 1700 600` on Home's free area. A pull on the dock
  can only be judged by a real finger (pong).
- 2026-10-20 · commit "fix(drawer): a pull on an open folder's empty pane collapses the folder" ·
  Drawer in Categories view: open Suggested, put the finger in the empty band above its header
  and swipe down: the folder shrinks back to its card with the finger and the drawer stays open
  at its root; a light flick there does the same. The same on a big folder (from the band and
  from the grid at its top). A tap in Suggested's band and an upward drag there leave it open;
  a swipe down at the root still closes the drawer to Home · emulator, then pong when asked.

### Home dock: Lens beads (10-20)
- 2026-10-20 · feat(dock) Lens beads · Layout editor on Home, dock selected, Dock style → Lens
  beads: the slab goes and each pinned app sits on its own round glass bead about 1.2x the icon
  (crop a bead: about 70 dp across for 58 dp icons, a clear gap to its neighbour), centred on its
  icon, in the same order as before; the page dashes still stand above the row and a swipe up
  from the dock's top edge opens the palette. The switch is live, no restart, and no frame shows
  a bare dock or a black slab (screen-record the pick at 60 fps and step through it); back to
  Glass tray the same way. Swipe to the Workspace: its dock is the build before this change ·
  emulator (AGSL needs API 33+).
- 2026-10-20 · feat(dock) Lens beads · Each bead's glass, Floating and Docked, dark and pale
  wallpaper: a frosted face, a tight soft refraction band at its rim (stronger than the tray's,
  no ring where it meets the face), a slightly brighter lip along its lower edge, a small soft
  shadow under each bead and nothing between the beads but wallpaper; each bead shows a faint
  reflection of its own icon below it, never a neighbour's, subtle. Below Android 13 (an API 31
  emulator): plain frosted round beads with the dock's tint, no rim effects, no crash. Logcat has
  no "Glass refraction shader failed" on API 33+ (a compile failure would drop every glass
  surface to plain frost) · emulator.
- 2026-10-20 · feat(dock) Lens beads · Behaviour: long-press a bead's icon opens the app menu as
  before; drag an icon to reorder (the beads stay on the slots while the icon moves, then the
  order is the dropped one); pin and unpin an app (beads appear and go with their icons); page
  to a dock page with fewer apps (beads follow at settle). A minimised Home float still shows as
  today's chip centred above the dock (not a bead yet). At rest `dumpsys gfxinfo <pkg>
  framestats` shows no new frames over 10 s with the dock idle. With the A–Z row on the dock, or
  the keyboard sharing the dock's glass, the dock falls back to the tray's slab · emulator.
- 2026-10-20 · commit "feat(dock): Lens beads ride under their icons while the dock pages
  slide" · Lens beads, two dock pages (seven and five apps, and a page of eight on a wide
  screen): swipe slowly half-way and hold, then release to change page, then a short swipe that
  springs back, then a fling. Screen-record at 60 fps and step every frame: each bead travels
  under its own icon, the next page's icons arrive on beads of their own, the beads leaving and
  arriving are cut at the row's edge with their icons; no frame shows an empty bead, an icon off
  its bead or the tray's slab (`ad swipe 900 2200 200 2200 700` for the slow one; injected swipes
  release with no speed, so a fling is a short fast `ad swipe 900 2200 300 2200 120`). At rest
  `dumpsys gfxinfo <pkg> framestats` over 10 s shows no new frames; during a swipe the janky
  frame count stays where the Glass tray's is for the same swipe (judge jank on pong, never the
  emulator). Below Android 13: the round frost beads move with their icons the same way ·
  emulator, then pong when asked.
- 2026-10-20 · commit "feat(dock): Lens beads inset the apps row 9 dp a side" · Lens beads,
  seven apps on a page, 411 dp wide (tl16 portrait): crop the dock, each bead about 1.1x its
  icon (about 50 dp across for a 46 dp icon), at least 12 dp from the bead's edge to the screen's
  edge on both ends, a clear gap between beads, none falling back to the slab; five apps: beads
  at about 1.2x as before. Switch to Glass tray and Prompt line: their row spans as it did before
  this change (icons 9 dp further out each side than under beads); the Workspace's dock is the
  build before · emulator.

### Home dock: Prompt line (10-20)
- 2026-10-20 · commit "feat(dock): Prompt line, the palette's field under the apps row on one
  slab" · Layout editor on Home, dock selected, Dock style → Prompt line: the dock becomes one
  taller slab, the apps row on top and under it a darker well reading `~ ❯ Search apps, commands,
  settings` (no "Ask" anywhere). Screen-record the pick at 60 fps and step it: the slab's top
  rises over about 0.24 s with the icons and the dashes riding it, the field fades in only once
  the icons have cleared its place, no frame shows a bare dock, a black slab, an empty strip of
  glass or an icon outside the glass; back to Glass tray and to Lens beads the same in reverse;
  pick a third style while the ease runs: it carries on from where it stood. Reduced motion on:
  it lands at once · emulator (AGSL needs API 33+).
- 2026-10-20 · feat(dock) Prompt line · Geometry, Floating then Docked, crop the dock: the field
  is 48 dp tall with its well 42 dp, its sides 14 dp inside the slab (in line with the A–Z row's
  and the dashes' inset); the air from the slab's top to the icons equals the air from the well's
  bottom to the slab's lower edge (within 1 px); the well's corners are concentric with the
  slab's (its radius the slab's less that air), square under Docked; the dashes still stand on
  the wallpaper just above the slab. Home's widgets and a minimised float's chip stand above the
  taller dock, nothing covered · emulator.
- 2026-10-20 · feat(dock) Prompt line · Use: tap the field: the palette opens with the in-app
  keyboard and the caret in its query line; type `sett` and Enter: the top result runs (Settings
  or the first row shown), never a shell command; Enter on an empty query just closes. With the
  keyboard already up the palette opens at once. A swipe up from the dock's top edge still opens
  the palette with the keyboard. TalkBack reads the field as a button named "Search apps,
  commands, settings" · emulator.
- 2026-10-20 · feat(dock) Prompt line · Colours and glass: `~` in the terminal theme's blue, `❯`
  in its green, the hint legible (4.5:1) on the well in light, dark, black and a Material You
  scheme; change the theme or flip light/dark: the prompt follows without a restart. The icons
  are reflected only in the lower bevel under the field, faint (fainter than the tray's), nothing
  mirrored over the field or above the icons; reorder an app and it follows; a page swipe fades it
  as on the tray. At rest `dumpsys gfxinfo <pkg> framestats` shows no new frames over 10 s; below
  Android 13 (API 31 emulator): plain frost slab, the well and prompt drawn, no crash; logcat has
  no "Glass refraction shader failed" · emulator.
- 2026-10-20 · feat(dock) Prompt line · Seams and the editor: swipe Home → Workspace and back:
  the field eases out with the page and back in as Home returns (superseded by "Home's own dock
  bands follow the page across the seam", pass-2 fixes below), the Workspace's dock is the build
  before, no slab jump during the slide; the drawer still opens from Home (palette route, see
  "How to open the drawer" above); in the Layout editor the dock's size grip still sizes the
  icons as before (the field is not counted as row height) · emulator.
- 2026-10-20 · feat(dock) Prompt line · Frame time of the style switch ease (a dock pass and a
  glass re-cut per frame for about 15 frames) against a tray⇄beads switch: report jank beyond the
  noise (judge jank on pong, never the emulator) · pong when asked.
- 2026-10-21 · fix(dock) "the Prompt line's field hides under the open command palette" ·
  superseded by "the Prompt line's slab eases to the tray's under the open palette" (pass-2
  fixes below): the dock no longer keeps the field's band under the palette.

### Home dock: Taskbar (10-20)
- 2026-10-21 · commit "feat(dock): Taskbar, a Home tile among the apps and the run strip above"
  · Layout editor on Home, dock selected, Dock style → Taskbar, no restart: the dock is the
  tray's slab holding a round Home tile (the drawer's grid glyph) at the row's start and the
  apps, all one size (the tray's tiles), one app fewer per page than the tray (with 7 slots, 6
  apps); no clock, no readings, no "Ask" anywhere. Screen-record the pick: no frame shows a bare
  dock or an empty row; back to Glass tray, Lens beads and Prompt line live the same way, and
  the tile goes · emulator (AGSL needs API 33+).
- 2026-10-21 · feat(dock) Taskbar · Home tile: a tap opens the app drawer as Home's own drawer
  entry does. Long-press it (no travel) and it lifts (1.12x, a haptic); drag it along the row:
  only it moves; let go in the right third: it lands in the last slot, in the middle third the
  centre slot, in the left third the first; the apps keep their order around it. Force-stop and
  relaunch: it is where it was left. TalkBack names it "All apps". Long-press an app: its menu
  as before; drag an app onto another place and onto the tile itself (it lands beside it, never
  replaces it); the tile has no Unpin anywhere · emulator.
- 2026-10-21 · feat(dock) Taskbar · Paging with the tile: with more apps than a page holds, swipe
  the dock's pages by finger: the Home tile stays put while the apps slide past it, the staged
  page's icons arrive in the slots around the tile, never under it; a committed page switch
  (fling) leaves the tile in place (it may fade with the row: report if it reads badly). The
  icons' reflection shows the tile where it stands after a move, and is faint (clearly fainter
  than the tray's: about 0.6 of it) · emulator.
- 2026-10-21 · feat(dock) Taskbar · Run strip: with no terminal, no float and no display, nothing
  stands above the dock. Open a Home float (+ in the palette, or the Terminal entry): a thin glass
  strip eases in above the dock and above its page dashes (the dashes stay hugging the slab, the
  strip never covers them or the grabber pill), inset like the slab, entries `1 <label>` and `+`
  at its end. Open Workspace windows: they come first, numbered, the display window's at its
  place, then the floats; minimise a float: its entry wears `▾`; the focused float, else the
  Workspace's window in front, is lit. Ring a bell (`printf '\a'` in a window you are not
  looking at): its entry wears the red dot. Close everything: the strip eases away. The
  minimised floats' chips never show under the Taskbar · emulator.
- 2026-10-21 · feat(dock) Taskbar · Run strip taps: a Workspace window's entry slides the
  Workspace in with that window in front (a display entry: the display window); a float's entry
  brings the float back on its page, focused, keyboard up; `+` opens a new float on Home.
  Minimise a float under the Taskbar: watch where its flight lands (the chips are off; it may
  land on no target: report what it does) · emulator.
- 2026-10-21 · feat(dock) Taskbar · Glass and rest: the strip's glass is frost with a narrow ring,
  no shelf, no lip, no icon reflection on it; light, dark and a Material You scheme read; with
  the keyboard up the strip stands above the keyboard. At rest with the strip showing,
  `dumpsys gfxinfo <pkg> framestats` shows no new frames over 10 s. Below Android 13 (API 31
  emulator): plain frost strip and slab, no crash, no "Glass refraction shader failed". Swipe
  Home → Workspace: the Workspace's dock is the build before (no tile, its usual app count),
  the strip slides away with Home; back on Home the tile and the strip return at the settle ·
  emulator.

### Home dock: Wall strip (10-20)
- 2026-10-21 · commit "feat(dock): Wall strip, the expanded dock with the Workspace's row over
  the apps" · Layout editor on Home, dock selected, Dock style → Wall strip, no restart: the slab
  grows upward (about a quarter second, the icons stay put) into one tall slab: a first row (84 dp)
  with a large clock and the date on the left and a dashed `+` tile, then the apps row. Page
  dashes still float above the slab, never inside it; the air over the row equals the air under
  the icons. The clock's left edge lines up with the first icon, the `+` tile's right edge with
  the last icon's; tiles' corners nest inside the slab's. Screen-record the pick and back to Glass
  tray, Lens beads, Taskbar and Prompt line (Prompt line ⇄ Wall strip in one step): no frame shows
  a bare dock or a blank slab; no "Ask" anywhere · emulator (AGSL needs API 33+).
- 2026-10-21 · feat(dock) Wall strip · Row by count: 0 terminals: clock about 56 %, `+` the rest;
  open one Workspace window: clock, then the terminal tile and `+`, equal; two: clock and the
  terminal area half and half; three: the clock slides out to the left and four equal tiles fill
  the row; four: five equal; six: tiles at the four-terminal width, the row scrolls by finger,
  `+` at its end. Each change slides and resizes the tiles (about 0.4 s, no jump, a new tile grows
  in from its centre, a closed one shrinks away); rename a window (its tile's title changes in
  place); the window in front wears the accent ring; ring a bell in a window you are not looking at
  (`printf '\a'`): its tile wears the amber ring and dot. Each tile shows its window's split shape
  (split a window into two and three panes and check the blocks) · emulator.
- 2026-10-21 · feat(dock) Wall strip · Taps: a tile slides the Workspace in with that window in
  front (the display window's tile: the display). `+` slides the Workspace in on a new terminal
  window (never a Home float). Long-press a tile: a small menu at the tile with Switch to window,
  Move window left/right (absent at the row's ends) and Close window; check each, Close on a
  window running something asks first. With six windows, drag the row sideways: it scrolls and
  neither the dock's pages swipe nor the drawer opens; a vertical swipe on a tile does nothing
  odd · emulator.
- 2026-10-21 · feat(dock) Wall strip · Glass and rest: the icons are reflected only in the lower
  bevel under them, faint; no icon ghost over the tiles or the clock. Tiles are dark wells,
  `+` a lighter dashed well. The clock reads on light, dark and Material You schemes (its ink is
  the dock's glass ink); the date follows the locale (switch to a non-English locale). At rest,
  `dumpsys gfxinfo <pkg> framestats` shows no new frames over 10 s except one at the minute's turn,
  and that frame redraws only the clock. Home's own clock widget is unchanged. Below Android 13
  (API 31 emulator): plain frost slab, no crash. Swipe Home → Workspace: the Workspace's dock is
  the build before (no row) once landed; the row eases out with the page and back in as Home
  returns (pass-2 fixes below), with the current windows.
  Open the drawer from the dock and the command palette by the dashes: report how the row reads
  under them · emulator.

### Home dock: pass-2 fixes (10-21)
- 2026-10-21 · fix(dock) "Lens beads draw again, the row's icons held to what a bead covers" ·
  Lens beads, `layout.home.portrait.az_row=false` (the A–Z row hidden; `az_bar` may still read
  bottom), six and seven apps on a page, Docked then Floating, 411 dp (tl16): every icon on its
  own round bead about 1.1x the icon (crop: about 50 dp beads under about 45 dp icons, a clear gap
  between beads, at least 12 dp from the screen's edges), no slab; the icons are a little smaller
  than under the Glass tray (switch to it: they grow back to the tray's size). A–Z row back on the
  dock: the slab (the fallback). Swipe the dock's pages: beads ride their icons as before ·
  emulator (AGSL needs API 33+).
- 2026-10-21 · fix(dock) "Home's own dock bands follow the page across the seam" · Prompt line,
  then Wall strip, a Workspace window open: swipe Home → Workspace slowly (`ad swipe 900 1200 200
  1200 900`) and record at 60 fps: the field (the row) shrinks out of the slab as Home slides
  away, the slab's top coming down with it, and the settle shows no step (no ~100 dp drop); swipe
  back: it grows back in as Home returns; a swipe that springs back leaves the slab as it was.
  Glass tray, Lens beads and Taskbar: the slide is the build before. At rest `dumpsys gfxinfo
  <pkg> framestats` shows no new frames over 10 s. Taskbar: the run strip slides away and back
  with Home's page (it lives in Home's float layer), with no vertical step at the settle. Judge
  the per-frame dock pass for jank on pong · emulator, then pong when asked.
- 2026-10-21 · fix(dock) "the run strip's glass is cut to its rounded outline" · Taskbar with two
  windows: crop the strip's four corners at 4x: no square frost past the rounded ring, the ring
  intact, the entries unclipped; light and dark · emulator.
- 2026-10-21 · fix(dock) "the Prompt line's slab eases to the tray's under the open palette" ·
  Prompt line: tap the field, and separately swipe up from the dock's top edge: as the palette
  opens the slab eases (about 0.24 s) to the Glass tray's height, the field fading with its band,
  and no empty strip of glass is left under the icons (pass 2's 44z); close the palette (Back,
  Enter on an empty query, a tap outside): the slab eases back and the field fades in. Reduced
  motion: it lands at once. Report jank of the ease running with the keyboard's rise on pong ·
  emulator, then pong when asked.
- 2026-10-21 · feat(dock) "the Wall strip lists Home's floats as tiles too" · Wall strip, two
  Workspace windows and two Home floats (one minimised): the row shows the windows' tiles, then
  one tile per float, the clock's share counting the floats as terminals; the focused float's
  tile wears the accent ring, else the Workspace's window in front. Tap a float's tile: the float
  comes back on Home, focused (Home stays in view); tap a window's tile: the Workspace slides in
  on it. Long-press a float's tile: nothing (no menu yet). Close a float: its tile goes ·
  emulator.
- 2026-10-21 · pass-2 equal padding, re-measure · Glass tray, Floating and Docked, with and
  without the A–Z row, and each other style's slab: read the slab's bounds and an icon's box
  from `uiautomator dump` (not the screenshot, whose crops ended 3 px under the slab in pass 2):
  air above the icon box = air under it (Docked: to the top of the gesture inset) within 1 px.
  Pass 2's own frames already measure equal: Floating tray slab 2159–2321 with the icon disc
  2180–2300 (21/21 px), Docked slab top 2169, disc 2191–2315, and 22 px under it to 2337, the
  gesture inset's top if it is 24 dp (unconfirmed: read it from the dump) ·
  emulator.
- 2026-10-21 · pass-2 seam frost, by eye · Home → Workspace landing with the keyboard up, Glass
  tray then Prompt line: one frame of frosted terminal text at the landing is the terminal's own
  reflow frost (TerminalView.frostReflow, 110 ms, since 356e6ca81 on 2026-09-27), there with
  every dock style; report only if it reads worse under one style · emulator.

### Home dock: pass-3 fixes (10-21)
- 2026-10-21 · fix(editor) "the keyboard the editor raised goes down with the Done exit" · Layout
  editor opened directly (not through the Overview) on Home, keyboard down before it opened, each
  dock style: Done, recording at 60 fps: the keyboard slides down while the frame grows back to
  full size, and no frame shows the full-size launcher with the keyboard up and the dock standing
  over it (pass 3's 12-done-wall, 13-pair1). With Home's keyboard always on: it stays up after
  Done. Through the Overview: Done as before (the keyboard goes under the Overview) · emulator.
- 2026-10-21 · fix(dock) "no float chips over the Wall strip" · Wall strip, then Taskbar, split
  panes on, a Home float: minimise it, change the theme (light ⇄ dark), relaunch the activity
  (`am force-stop` then launch), switch to the other of the two styles: no float chip ever shows
  above the dock (the float is the Wall strip's tile / the run strip's marked entry); the minimise
  flight shrinks away toward the dock. Switch to Glass tray, Lens beads, Prompt line: the chip is
  there, centred above the dock, and brings the float back · emulator.
- 2026-10-21 · fix(dock) "the run strip marks a minimised float with or without its label" ·
  Taskbar, six windows and a minimised Home float (numbers only), then two windows and the float
  (labels shown): the float's entry has a small downward triangle after its number (after its
  label when there is one), readable on the strip's glass in light and dark; crop at 4x ·
  emulator.
- 2026-10-21 · fix(dock) "the Taskbar's + grows with the font scale and wears a badge" · Taskbar,
  font scale 1.0 then 1.5 (`settings put system font_scale 1.5`), light and dark: `+` is a cross
  on a faint badge the size of the number badges, as dark as the numbers, and larger at 1.5 than
  at 1.0 (still inside its badge); tap it: a new window as before. Nothing at rest redraws ·
  emulator.
- 2026-10-21 · fix(dock) "the run strip stands on the dock wherever the launcher was scaled when
  it was placed" · Taskbar, split panes on, two windows running, Home's keyboard always on
  (`layout.home.keyboard_shown=true`): open the Layout editor on Home, pick Taskbar (from another
  style), Done: the strip stands just above the dock, which rides on the keyboard, never
  mid-screen over the clock (pass 4's 09-taskbar); compare with the keyboard down (11-taskbar-cold).
  Then each of: relaunch (`am force-stop` then launch), open and close the command palette (its
  keyboard), put the always-on keyboard away and back, rotate to landscape and back: the strip
  follows the dock's top each time. Same Done with Glass tray and a minimised Home float: its chip
  stands just above the dock. Nothing at rest redraws · emulator.

## Developer

### Home dock styles on pong (10-21)
- 2026-10-21 · feat(dock) five Home dock styles · On pong, build `1.1.0-pre.6+dev.120d6f511`: pick
  each style in the Layout editor (Layout mode on Home, tap the dock, Row B). Taste: does Glass
  tray's lower shelf read as thick glass rather than a cut, and are the icon reflections subtle
  (not a second row of icons), on a dark and a pale wallpaper? Lens beads: a dock page swipe with
  two pages — any jank while the beads ride their icons (the dock's effect is rebuilt once a
  frame during the swipe)? Prompt line and Wall strip: a slow Home ⇄ Workspace swipe — any
  stutter while the field/row eases with the page (one dock pass a frame), any step at the
  settle? Taskbar: the run strip with real nvim/htop windows, names readable; the Home tile drag.
  Wall strip: the clock row with 0, 1, 3 and 5 terminals, an attention ring after a bell. At rest
  on Home for 30 s with each style, `dumpsys gfxinfo com.termux` shows no new frames (the Wall
  strip clock's minute tick aside).
- 2026-10-21 · fix(drawer) Suggested folder pull · In the drawer's categories view, open Suggested
  and pull down from the empty area above its apps: the folder closes and the drawer stays open
  at its root (it used to close the whole drawer). A pull down at the root still closes it.

### Home dock: pong fixes (10-21)
- 2026-10-21 · 2742a62bc · Home on Lens beads (and again on Glass tray), two dock pages so the
  dashes float above the slab; swipe to the Workspace and let it settle: its dock (apps, A–Z,
  toolbar) is one rounded slab, frosted to its lower edge, no second frame or unfrosted band
  along its bottom or lower-left corner (crop the dock's lower edge, compare with the dock after
  a rotation there and back). Also landing by a fling and by the dock's Terminal entry · emulator,
  then pong.
- 2026-10-21 · 604fd8558 · Lens beads ⇄ Workspace, slow finger drags across the seam and back
  without letting go, then flings: as Home slides away the beads swell under their icons and fuse
  into the slab, which is whole by the last stretch; returning, they shrink back out of it; the
  settle changes nothing visible but the slab's own rim and the dashes' band (still a step: the
  slab's top moves by the dashes band at the settle, as before). From the Workspace the dock
  turns to swollen beads as soon as Home starts to show. No frame with a bare dock, a square
  frame or beads off their icons; dark and pale wallpaper. Where the Workspace's dock has other
  rows than Home's (A–Z, toolbar), the beads give way to the slab where the stack changes rows,
  as before · emulator for the look (record at 60 fps), pong for jank (one dock effect rebuild a
  frame while the share moves, `scripts/perf-frametime.sh` on a seam fling).
- 2026-10-21 · 2abea4147 · keyboard and dock on a swipe to the previous window · Workspace with
  three terminal windows, keyboard up, on the last window (`launcherctl window focus <last>`,
  `launcherctl keyboard show`): a slow rightward swipe on the terminal's text (`adb shell input
  swipe 200 700 900 700 1200`), recorded at 60 fps: the dock and the in-app keyboard stay where
  they are on every frame, as on the leftward swipe to the next window; also from the middle
  window both ways, by a border drag, and a short drag let go that springs back. From the first
  window the same rightward swipe still brings Home in with the dock and the keyboard travelling
  as before. Window 2 → window 1 by chip tap and by `window.select` too (pong's report did not
  say which route): no frame with the keyboard or the dock gone. Under Glass tray and Lens beads
  · emulator, then pong.
- 2026-10-21 · 49e41cd56 · Lens beads melting on the way out · Home on Lens beads, the Workspace's
  dock with other rows (A–Z, toolbar; keyboard up and down): a slow Home → Workspace drag
  recorded at 60 fps, cropped to the dock's bottom 300 px: over the first ~8 % of the drag, before
  the dock moves, the beads swell inside the slab's rounded outline into the slab; from the first
  frame the dock sinks it is the rounded slab, cut by the screen's bottom band as on the way
  back, never beads cut by a flat line; no pale line left at the dock's foot while the
  Workspace's stack is below the edge. Drag back before letting go: the beads come back out of
  the slab once the dock is home. Same rows on both halves (no A–Z, no toolbar on the
  Workspace's dock): the melt still follows the whole slide. The way back (Workspace → Home)
  looks as before. Frame time of a seam fling on pong: one dock effect rebuild a frame only over
  the first stretch · emulator for the look, pong for jank.

### Living glass (10-19)
- 2026-10-19 · feat(glass) living glass · Taste on pong, on a dark and on a pale wallpaper, under
  Clear and Mist: does a moving pane read as glass with weight (the rim thickening ahead, thinning
  behind, rocking back once on landing) and a press as a rigid pane tipping, with nothing rippling
  under the finger, rather than as an effect laid on top; and is the glass at rest exactly as
  before (screenshot before and after a slide, pixel-compare the rim) · pong, by hand (ask first).
- 2026-10-19 · feat(glass) living glass · Frame time of a seam fling and a chip-tap window switch,
  glass on, two panes, Living glass on against off (`scripts/perf-frametime.sh`): it adds two
  uniform writes per moved frame, three samples instead of one on a pixel whose bend changed, and
  up to about half a second of slab redraws after landing; report any p90 worse than off by more
  than the noise, per docs/agents/performance.md · pong, by hand (ask first).

### Tilted glass (10-20)
- 2026-10-20 · feat(glass) tilted glass · Taste on pong, dark and pale wallpaper, Clear and Mist:
  does a page drag (18°), a held border (6°) and a press on a terminal pane (about 1°) each read
  as the wallpaper refracting through a tipped plate of glass — clearly at the press, strong but
  not torn at the plank's full tip — rather than as a shimmer or a picture sliding about? If the
  press reads too faint or the plank too strong, the knobs are `TILT_SHIFT_DP`/`TILT_SOFT` (face)
  and `TILT_LEAN_GAIN`/`TILT_LIGHT` (rim) in GlassRefraction · pong, by hand (ask first).
- 2026-10-20 · feat(glass) tilted glass · Frame time of a page drag held and released, two
  panes, Living glass on against off (`scripts/perf-frametime.sh`): it adds one uniform write per
  tipped frame and the rim's live path on tipped slabs, one face sample as before; report any p90
  worse than off by more than the noise · pong, by hand (ask first).

### Glass perception (10-20)
- 2026-10-20 · feat(glass) glass perception · Taste on pong, dark and pale wallpaper, Clear and
  Mist: does a page drag (18°), a held border (6°), a press on a terminal pane (about 1°) and a
  window switch now read as glass without looking for it: the glint travelling round the corner
  with the tip and fading as it settles, the picture bending rather than sliding? And at rest: is
  every surface back to the old light, with no bright smear in the top-left corner (dock and bars
  included)? Is the glint at a press on a pane (about 1°, an eighth of it) enough, or too little?
  Knobs in GlassRefraction: `glintWeight` (how much glint a pose gives), `Look.LIVE_KEY_EXPONENT`/
  `KEY_PEAK` (glint), `Look.COUNTER`, `Look.LIVE_EDGE_SHADE`, `KEY_LIGHT_RISE` (how far the glint
  travels), `TILT_MAG`/`TILT_MAG_MAX_DP` (gradient), `TILT_SHIFT_DP`/`TILT_SOFT` (shift),
  `LIVE_CLARITY`/`LIVE_CLARITY_REACH` (live rim) · pong, by hand (ask first).
- 2026-10-20 · feat(glass) glass perception · Frame time of a page drag held and released and of
  a seam fling, Living glass on against off (`scripts/perf-frametime.sh`): at rest the program
  runs the old light, nothing more; while live, four more `pow` per pixel within 10 dp of an
  outline, no new samples anywhere; report any p90 worse than the build before by more than the
  noise · pong, by hand (ask first).

### Page transition across the wall (10-19), by hand
- 2026-10-19 · feat(wall) page transition across the wall · Glass on (pane glass, a glass widget
  and a Home float): under Depth, Stack and Carousel the frosted wallpaper inside every slab stays
  fixed to the wallpaper behind while the page scales or turns, and the leaving window's card
  (a picture) only carries its frost along; judge whether that reads as misaligned and whether
  anything snaps at rest · pong, by hand (ask first).
- 2026-10-19 · feat(wall) page transition across the wall · Frame time of each effect against
  Slide on the same build, glass on, two panes: the seam by fling and a chip-tap window switch
  (`scripts/perf-frametime.sh`). Pages are on a hardware layer only while posed, and glass re-aims
  every frame as it does under Slide, so a posed page re-renders its layer per frame; report any
  effect whose p90 is worse than Slide's by more than the noise, per docs/agents/performance.md ·
  pong, by hand (ask first).

### Widgets wear the Look's glass (10-19)
- 2026-10-19 · feat(widgets) Look's glass · Legibility on pong with your Look: widgets' text on
  the Look's glass at its own opacity (a Clear Look at 2–10 % can leave light text on a bright
  wallpaper), and whether Glass off's solid card is what you want where the cards used to go
  see-through with the terminal panes · pong, by hand (ask first).

### Seam travel and Workspace dock (10-19)
- 2026-10-19 · f2f54cc4b · Feel on pong of the Home ↔ terminal slide with your layouts: whether
  the dock leaving and the bar arriving read as one motion, and the frame time of the slide
  against the 1.1.0-pre.6 baseline (sliding between places: median 15–16 ms, worst page-change
  frame 47 ms; pre-roll and settle frames 30–77 ms). The slide now lays the stack out once at its
  start (from a Workspace without a dock, or bringing the bar from Home) or at its midpoint
  (a dip): perfetto `Wall.seamLayout` is that frame; report it red if it is worse than the
  pre-roll frames · pong, by hand (ask first).
- 2026-10-19 · SeamDress (45d0f76b8) · With Home set apart from the Workspace, the frame that
  changes the stack's halves (first frame toward Home, or the midpoint of a dip) now also
  re-dresses the stack (perfetto `Wall.seamDress` inside `Wall.seamLayout`). Report it red if that
  frame is worse than the pre-roll frames; and judge whether the whole-dock dip between two docks
  (and the keyboard dipping with it) reads well, or wants another motion · pong, by hand (ask
  first).
- 2026-10-19 · PageTopInsets · The first frame of a slide from the terminal toward Home now also
  lays the canvas and Home's page out once (perfetto `Wall.canvasReach`), and the settle back on
  the terminal once more; Home's widgets are re-laid there instead of at settle. Report it red if
  that first frame is worse than the pre-roll frames above, or if a widget visibly reflows as
  Home slides in · pong, by hand (ask first).

### Terminal body swipe (10-19)
- 2026-10-19 · feat(wall) body swipe · Feel on pong: whether 1.5x across-over-down is the right
  angle (a thumb's vertical scroll should never page, a lazy diagonal flick should), and whether a
  swipe that starts on the text feels the same as one off the border. The constant is
  `TerminalBodySwipe.SIDEWAYS_RATIO` · pong, by hand (ask first).

### Settings restructure (10-19)
- 2026-10-19 · waves 3 and 4 · Taste check on pong: the new rows' wording (Open the only match,
  Remember position, Start in the lower half, A-Z strip, Badge style, What's new, Restart launcher),
  how far down Start in the lower half starts the list, and whether the count badge reads well on
  the dock's icons at your icon size · pong, by hand (ask first).
- 2026-10-19 · review fixes on wave 2 Home · With Depth and Carousel, a glass widget (built-in,
  glass style) and a Home float on the page: during the drag and the settle the page is drawn
  through a hardware layer and scaled or turned, while the glass is aimed at the untransformed
  page. Judge whether the frosted wallpaper inside the widget and the float visibly slides or
  misaligns against the wallpaper behind during the motion, and whether it snaps at rest; take
  this together with the frame-time check above · pong, by hand (ask first).
- 2026-10-19 · feat(home) page transition · Frame time of each effect on pong with glass on (Home
  float and a glass widget on the page): `scripts/perf-frametime.sh` with a Home-page swipe
  scenario, against Slide on the same build. Depth, Fade and Stack set the page's alpha, which
  draws the page through a temporary layer for the frames of the motion; drop any effect whose
  p90 is worse than Slide's by more than the noise, and report it red per
  docs/agents/performance.md · pong, by hand (ask first).
- 2026-10-19 · feat(settings) two panes · The Surface in landscape and portrait: the list and the
  section read well side by side (row widths, the root's icon tiles, the hairline), light and
  dark · the Surface, by hand.
- 2026-10-19 · feat(settings) animation speed · General › Animation speed at Slower, Faster and
  Off: a Home page swipe, the palette opening and a terminal window floating out each take
  visibly longer, shorter, or land at once; Normal feels exactly as before · pong, by hand.
- 2026-10-19 · settings restructure · Taste check of the new root and the Home and Terminal
  sections against the approved plan page: names, order, summaries · pong, by hand.

- 2026-10-19 · feat(wall) display as a window · The display window's arrival and departure feel
  like a window switch (no flash of an empty frame, no frame of black where the surface was), and
  the plank tilt on a border drag off the display still tips once its still copy is up · pong, by
  hand.
- 2026-10-18 · feat/wall-rework-phase2 after the PR #82 merge (85ddb719a) · The seam between #82's
  relaunch survival and the wall: with the Appearance editor open over a Home page, relaunch the
  activity (`launcherctl wallpaper set <image>`): the editor comes back over Home with Home's
  labels ("· Home"), not the Workspace's; same with Firefox running on the display: the display
  window is still there afterwards and typing reaches it · pong, by hand.
- 2026-10-18 · feat/wall-rework-phase2 at e66a1710f, slices 2 and 2b (Home's own Style, Corners
  and Margin; Margin as the one air on Home) · On pong, release build: Settings → Start in =
  Workspace, then a true cold start (swipe the app away or reboot, not a force-stop): the
  Workspace opens with its chrome, no Home flash. A 6-column widget grid at the seeded Margin
  (your Workspace's, 12 dp by default): the cap on pong's 386 dp wall is 5, so the grid reflows
  once on the upgrade; judge whether six should survive (Margin ≤ 7 dp keeps it, or a 53 dp
  minimum cell). Open Appearance over a Home page and set Home apart (Floating, Margin ~24):
  dock cards, keyboard, a Home float's room and the widget rim move together, and the Workspace
  keeps its own; the capsule status bar with a float open wears Home's Margin. Dark theme and a
  rotation with Home set apart; font scale 1.5 truncates the built-in Clock's date line (time
  intact), say whether that is acceptable · pong, by hand.
- 2026-10-17 · commit "feat(motd): a rounded welcome card in the wallpaper's colours" · On a fresh
  session the card reads well over a light wallpaper as well as a dark one, and tapping "Tap to
  read the docs" opens the guide · pong, release build.
- 2026-10-17 · same branch · Taste: light-theme Mist at 35% no longer milky; the edge gain cap at
  2.25 (dark Mist now gets its full ratio) · pong.
- 2026-10-17 · same branch, "feat(glass): Tint colours the glass across its range" · Taste on
  pong: Clear at Tint 100 reads as tinted glass, not neon and not milky; the frame-tint caps
  (`GlassFrameTint.MAX_STRENGTH` 0.42, `MAX_CHROMA` 0.6, fade-out at Opacity 45) are the knobs. A
  Tint or Opacity drag stays smooth, the glass behind catches up once after release (one re-blur) ·
  pong.
- 2026-10-17 · same branch, "feat(glass): Edge is a slider" (replaces the Prism rows: on pong
  Glint and Prism looked alike) · Taste at arm's length, dark and light, Celestial Field: Edge 100
  on Clear, on Mist and on a warm/gold Look reads as light splitting at the glass rim, a crisp
  spectral edge on the outline itself (cyan-blue on the outer px, orange right inside), never a
  separate coloured border, on a dark and a pale wallpaper, and the edge does not read red
  overall; compare against the 10-17 shot of the old one, Edge 100 on a gold Look over the cat
  wallpaper, where a yellow-green line ran inside every edge:
  `/tmp/claude-1000/-home-amalvajdan-Projects-termux-launcher-app-termux-launcher/ff00a939-66f3-41a9-acb3-82df73313d71/scratchpad/pong-edge100.png`
  (dock crop `pong-edge100-dock.png` beside it; /tmp, so copy it out before a reboot). 40 is
  Clear's glint as before; the slider feels even from 40 to 100; Edge drags stay smooth. Knobs:
  `Look.BOLD_MIX` 0.4 (how far a fringe's peak mixes toward its colour per unit of the cover
  gain, the strength), `BOLD_LOBE_DP` 0.3 and `BOLD_CENTRE` 2 (width and where the two meet),
  `BOLD_WARM`/`BOLD_COOL` (the two colours) · pong.

- 2026-10-16 · commit "feat(glass): the gathered light is the only glass edge" · The edge reads
  as light, not an outline, on every surface; on Mist and Solid (and the sheet and chip, floored
  near opaque) note how faint the outline gets under their tint, for phase B's opacity scaling.
  Also record whether floating panels still stand apart from a white wallpaper: that is Round 5's
  gate · pong, release build.
- 2026-10-16 · commit "feat(keyboard): a touch engine that learns where your taps land" ·
  The touch engine's pass bar, under the round 2 take-back rule: take-backs into space drop by
  30% or more from the warm-up's rate to the corrected rate, no other key's rate rises, and aiming
  at the space bar and landing on `b` or `v` types a space · pong, a few days of normal typing
  from the 2026-10-16 install of 1dbeda5b1 (learning restarted from zero there). Read it with
  `project-docs/verification/touch-engine-passbar.py --pong`, which prints a verdict per layout
  grid; until it says PASS or FAIL it reports how much data is still missing. A FAIL, a paused
  engine, or the developer reporting misses that still go to `b` or `v` sends the next session to
  the spec's Round 2 section before any change.
- 2026-10-15 · commit "feat(settings): make the Low-memory preset a switch" · Turning it on and off
  returns the look you had (material, opacity, effects, parallax), and Reduce idle activity follows
  the switch both ways without leaving the page · pong, Settings → App behavior.

- 2026-10-15 · stronger glass edges · The same three Looks on pong with a photo wallpaper: the
  edge reads at a glance on the dock, the keyboard and the panes, and is not too strong (no
  smeared or mirrored band that distracts, no glare on a pressed key or under a finger) · pong, by
  eye.
- 2026-10-15 · voice edit keeps the cleanup's marks · Dictate a sentence the cleanup changes, tap the text to edit: the changed and added words stay in the accent colour and the struck removed words are gone. Type inside a marked word, before it and after it, delete across a mark's edge, press ✓: the marks moved with the text (typing inside grows the mark, at its edges stays plain) and the committed text is exactly what was shown · pong, by voice.
- 2026-10-15 · Low-memory preset · Settings → App behavior → Low-memory preset, Apply: back on the launcher it is the Solid look with an opaque terminal, no cursor trail, no effect, and the Reduce idle activity switch is on; picking another look in Appearance brings the old look back · pong, by hand.
- 2026-10-15 · Cursor trail Off · Appearance editor → Terminal → Cursor trail: Off stops the trail at once, a style turns it back on, Cancel restores the earlier state, and Settings shows the same on/off · pong, by hand.
- 2026-10-12 · PR #64 · App chips on a light chip (light theme or a light Look): the icon's ring is
  the dark one, not the white one; on the emulator the light chip's ring was only faintly pale
  (2026-10-14) · pong, by hand, night mode off.
- 2026-09-27 · voice panel · Keyboard → Voice input → Mic sensitivity (Normal, High): soft speech at
  arm's length is picked up on both; High lets in more of a nearby voice or video, Normal fewer;
  keyboard clicks, a fan or air conditioning alone never open a phrase on either · pong, by hand.
- 2026-10-12 · fix(appearance): Custom starts from the Look on screen (with no Custom saved;
  a saved one now comes back instead) · On pong, apply Clear,
  Done, reopen the editor, tap Custom: the sliders show Clear's numbers and the look does not
  change. Tweak Blur, tap Mist, tap Custom: the tweak is back. Undo: Clear again · pong, by hand.
- 2026-10-12 · PR #63 · The resting look at Clear and Mist on pong: the outer ~2 dp of every rim
  now mirrors the wallpaper (quarter-circle bevel along the true normal); it should read as a
  thick glass edge, not a double line. If it reads wrong, the profile in `GlassRefraction` is the
  dial, not the Looks.
- 2026-10-12 · PR #63 · Frame timing with Bend 30 and Dispersion 100 on pong: page slides and
  keyboard up/down at 90 Hz, no frame over 11 ms · `scripts/perf-baseline.sh`.
- 2026-10-12 · PR #63 · Dispersion fringing shows on the city wallpaper between 50 and 100%; on
  a blue test picture it was only measurable, not visible.
- 2026-10-12 · PR #67 · Export settings, then Import settings on pong as the default home: the
  launcher comes back as home within a few seconds with the same look, layout and terminal font
  size, and nothing asks to pick a home app.
- 2026-10-12 · PR #64 · The chip strip with your real usage on pong: Shizuku, Solid Explorer and
  the keyboard types as named chips, and typing "s" pins Shizuku.
- 2026-10-12 · commit "feat(agent-skills): pack the launcher and speech skills for tlstore" · With
  both tarballs from `scripts/pack-agent-skills.sh` unpacked into `~/.agents/skills/` and each
  linked from `~/.claude/skills/`, Claude Code and Codex each: open a pane beside the shell and
  wait on it; answer "how do I change the terminal font" from the docs, naming the settings path;
  with the speech skill, read one answer aloud and stop when told · pong, by hand.
- 2026-10-13 · commit "fix(dock): let a lone pinned-apps row's icons fill its bar" · The pinned
  apps alone on their dock or plank (no A–Z row, no extra keys), Docked and Floating, Icon size at
  its smallest, the default and its largest, with 5 and 10 apps, keyboard up and down: the bar is
  as tall as before and the icons fill it, centred between the ticks and the far rim; 10 apps
  never touch; an A–Z scrub's matches in that row are the same size; the Icon size slider reads
  the bigger icon, and adding an A–Z row in Layout shrinks the icons back to today's size · pong,
  by hand.
- 2026-10-15 · commit "perf(terminal): replay the grid between row crossings of a smooth scroll"
  · Drag, fling and let settle a long transcript in the normal buffer, one pane and split, with a
  transparent frame and an opaque one, keyboard up and down: every frame looks as before (no
  blank or stale grid, the row coming in at the bottom and leaving at the top is whole, the find
  overlay and the padding fill track the text); output arriving and the cursor blinking mid-scroll
  show at once; a pane snapshot (split reveal) mid-fling is whole; on Android 10 the
  `Terminal.scrollCache.draw` frames in a trace cost one node draw, not a render · pong, by hand.
- 2026-10-15 · commit "perf(terminal): keep a scrolled row's recording, record only the rows that
  came in" · Fill the transcript with mixed content (bold, 256 colours, a URL with underlines on,
  a kitty image if easy, and a few text sizing blocks: `printf '\e]66;s=2;Big\a\n'`, no probe
  exists in project-docs/verification), then drag and fling through it, one pane and split: every
  row lands where it did before, rows under a text block and the block itself stay whole and in
  place (also with the block's anchor just above the top edge), the cursor row looks right when it
  scrolls back in, and the row entering at the bottom is whole · pong, by hand.
- 2026-10-15 · PR #75 · The natural glass on pong at Clear, Mist and Tint: the rim reads lit from
  above and softer below, the band brightens toward the lit rim and the edge shows the wallpaper
  more clearly than the frost; it should read as real glass, not a double line or a halo. Frame
  timing with the Looks' values: page slides, keyboard up/down and a dock tap at 90 Hz, no frame
  over 11 ms · `scripts/perf-baseline.sh`, pong, by hand.
- 2026-10-15 · voice, by voice only (nothing over adb can start a dictation; the agent routes
  through the API passed on 2026-10-15) · In one sitting on pong: tap the waiting text on the
  dictation card to edit it (caret where tapped, keyboard under the card, ✓ inserts the edit, Undo
  and Redo, a 7-line scroll does not open the editor); a Polished list shows on its own lines and
  pastes whole in a shell with bracketed paste; a 90-second dictation with E2B on the GPU shows
  `cleanup early` lines in logcat while listening and the cleaned text lands 1–2 s after Pause,
  with `lang=en sent=N/M` lines and no `fallback:context_echo`; with a multilingual Whisper model
  Voice language Auto-detect names a non-English language on the card; an "um" is struck through
  and changed words are in the accent, Undo gives the as-heard text; the screen stays on while
  listening and sleeps after about 3 minutes untouched (timeout 30 s); the grab handle moves pill
  and panel, a sideways swipe never discards, a double tap returns it to the top-right · pong, by
  hand.
- 2026-10-13 · commit "feat(tai): stop a model that runs the phone under 448 MB free" and "fix(tai):
  book a memory stop as a stop, not a crash" · Start an E4B GPU load at about 3.7 GB free with a
  memory hog beside it: `tai logs` shows `oom_guard … stopped the model to keep the phone
  responsive`, the runtime goes away, the launcher and Shizuku stay up, and the Model centre and
  `tai logs` book it as a stop, not a crash. Not run by the agent: the 2 GB watchdog would unload
  first and a load like this froze the phone once · pong, by hand, with everything saved.
- 2026-10-14 · commit "feat(keyboard): slide a layout row's buttons open beneath it" · On pong the
  row opened in about 0.3 s with animations on, ripple included (`~/reports/devcheck-2026-10-15-pong/03-row-open-anim1.mp4`);
  judge whether that reads as the intended ~200 ms. TalkBack reads "name, second line, actions
  hidden" and offers Show/Hide. Typing in the layout editor with the phone's keyboard shrinks the
  XML field above it · pong, by hand.
