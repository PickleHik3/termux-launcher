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

## Developer
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
