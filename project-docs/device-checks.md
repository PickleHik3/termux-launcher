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
- 2026-10-16 · commit "feat(glass): Adaptive glass, an edge of gathered light" (glass plan Round
  1) · Adaptive glass off: Clear, Mist and Tint look exactly as on `pre` (crop a pane corner and
  the dock's top edge on both builds). On: the edge is the wallpaper's colour brightened,
  brightest top-left and faint all round; no white line and no second stroke; over a pale
  wallpaper the glint shrinks; no bright line on a seam (keyboard over the under-pill strip, status
  over the window bar); the dock shows one edge, not two; in a joined frame, no glow runs along the
  screen's own edges. Look for a leftover white line on a Floating pane with the border on (its
  border still comes from the gradient rim) and on a wall page's corner tab (still the hairline).
  The row is hidden on Android 12 and greyed with the hint until a wallpaper is set from the
  picker · emulator, `glass-city` and `glass-flowers` wallpapers, Docked and Floating, keyboard up
  and down.
- 2026-10-16 · commit "feat(glass): Adaptive glass, an edge of gathered light" ·
  `GlassRefractionAgslInstrumentationTest` passes, its two gathered-edge cases included (a lit
  top-left corner with `uGathered` 1; switched back off draws the hairline exactly) · emulator.
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
- 2026-10-17 · commit "feat(wall): the status bar on Home carries the floats' chips" · The first
  float brings the status bar down from the top in its compact form; it stays while a float is
  open or minimised and slides up when the last one closes. Note whether the widget grid moves
  when the bar arrives (the bar takes its room today) · emulator screenshots before/after.
- same · Chips on Home are floats only, from every page, each with its page (" · 2"); Minimise
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
- 2026-10-18 · commit "feat(wall): the status bar overlays Home" · The widget grid does not move
  when the bar slides in with the first float, nor when it slides out after the last one closes:
  screenshot a widget's top edge before, with the bar, and after; the bar draws over the grid and
  takes the taps on its chips · emulator screenshots, then pong.
- same · A float born with the bar already there (the second float) and the first float, born as
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

### Wall rework phase two: slice 1, the capsule status bar (feat/wall-rework-phase2)
- 2026-10-18 · d3f2fa8e7 · emulator: Home with a float folds to the capsule after idle ("Status
  bar folded: Home, 5:19 PM" at the top centre, titlebar clear below); window 1 on the Workspace
  folds the same ("terminal in home, 5:23 PM"); a tap opens compact; a slow 90 dp drag down
  reaches expanded (bar 136–375); a quick flick up from expanded went to the capsule, not one
  step to compact (judge: the flick was 72 dp, past compact's stop). Capsule ink reads too dark
  on a dark wallpaper (fix in flight).
- Owed: the capsule under Minimal mode (MinimalMode.apply still hides the bar); a notification
  arriving while folded (dots, and the fold cancelled); the idle fold from expanded resizing the
  Workspace terminals, by hand on pong, to judge whether it should stop at compact instead.
- Emulator note: the pin store emptied between two cold boots while another session used the
  emulator for a "Look reset" check; not attributed.

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

## Developer

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
- 2026-10-16 · commit "feat(glass): Adaptive glass, an edge of gathered light" · With the switch
  on, the edge reads as light, not an outline. Also record whether floating panels still stand
  apart from a white wallpaper: that is Round 5's gate · pong, release build.
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
- 2026-10-12 · fix(appearance): Custom starts from the Look on screen · On pong, apply Clear,
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
