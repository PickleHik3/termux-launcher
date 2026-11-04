# Device checks owed (Agent)

Built, merged, not yet seen on a device. The list a drop or a cut is checked against first.
Every item here can be driven over adb: the emulator, or pong with the developer's go-ahead.
An item that needs a hand on the phone (feel, frame timing, a thumb mid-gesture, a lock
screen, taste, a Linux container or package the developer installs, Perfetto) goes to
`project-docs/developer-checks.md` instead.

One line per item: date · commit or PR · what to look at · what passes · how (device, route).
Verified items are **deleted**, never ticked; the PR, the commit or a bug is the record. An item
lands here in the same commit or PR as the change it belongs to, whenever a hand-over still owes
a device check. Before a drop or a cut, work through this list; an item two releases old is
re-judged and dropped if nothing rides on it. The file stays under twenty items; past that, the
oldest are re-judged first.

The 2026-10-23/24 and 10-26 emulator sweeps were worked and pruned on 2026-11-02; their
unfixed findings were listed for the developer to file as issues, not kept here.

## Agent

- **Drawer pages fill the page (11-04).** Drawer view = pages, keyboard down: the rows spread
  from the search field to the page dots, with no dock-high empty band under the last row; the
  dots sit in their own band. Also at font 1.3 and with an explicit Rows count · pong.
- **Pane corner panel: More (11-04).** Hold a pane corner on a lone pane and on a split pane:
  More is the last button in both (lone: Appearance, Minimal, Auto-tiling, Settings, Help, More;
  split: Close, Move, Maximise, More). Tapping it opens the same sheet a long press on the
  Workspace opens, and Share, Reset and Kill act on that pane's session (try it from the
  non-focused pane of a split). The three-dot glyph sits like its neighbours, light and dark
  theme · pong.
- **Home press lands on the page set as home (11-04).** Wall Home 1 (set as home), Home 2,
  Terminal: press Android Home from the Terminal page, from Home 2 and from Home 1. Each ends on
  Home 1 (pressing on Home 1 doesn't move). Then swipe Home 1 → Home 2 → Home 1 inside Home:
  the dots and the status bar follow each page, and Home 1 slides in drawn, never an empty page
  for a frame · pong.
- **Full screen pane under the camera: keyboard up/down slide lands without a jump (11-04).**
  Fix #190 (the slide's row prediction now reads the text top inset the resize uses). With a pane
  Full screen under the camera cut-out and a busy shell (prompt on the last row), raise and lower
  the in-app keyboard, Docked and Floating dock: the rows slide straight to where they rest, with
  no step at the end of the slide · pong.
- **Display Start with (11-04, #166).** Debian with `xfce4 dbus-x11` installed, Settings › Linux
  apps › Start with lists "Debian desktop (XFCE)" (a distro without XFCE is not listed) and set to
  it: opening the display (Display page Start, a drawer Linux app, `termux-x11 :0` from a shell)
  shows XFCE; leaving and reopening the Display page, or closing its window while the server
  runs and opening it again, does not start a second session (`ps` inside Debian shows one
  `xfce4-session`).
  Start with = A command… `xterm`: an xterm opens with the display; a command `false` shows "The
  display's start command did not run." once, with no retry. The tap that starts the display on
  a drawer desktop (XFCE from the drawer) does not start a second one · emulator.

- **Drawer pages swipe closes the keyboard (11-04).** Drawer view = pages, "Open keyboard
  automatically" on: open the drawer (keyboard rises), swipe sideways to the next page: the keyboard
  goes as the swipe starts, the query stays. Dragging an app to the page edge (which turns the page)
  does not close it. In-app and system keyboard · pong.
- **Edit pages: a Home page vanished once (emu2, 11-04)** after Bring back → force-stop → Add
  page → Rename → night toggle; not reproduced. When re-running that sequence, capture
  `wall_pages.xml` (with `next`) and the widget repository's stored page ids before and after
  the night toggle · emulator or pong.
- **Edit pages fixes (11-04, after the emu2 check).** (1) Use as Terminal only set from
  Settings › Use as (not the welcome sheet): Edit pages shows "Bring back N Home pages" with N
  the Home pages hidden; tapping it shows them again and Use as reads Home screen. (2) Every Home
  and Display card carries its name over its top left ("Page 2", "Display", the app's name); a
  renamed card shows its new name at once; a long name ends in "…" short of the red button.
  The captions and the "Terminal ⋮⋮" grip take dark ink on a light (cream) wallpaper and white
  on a dark one, in light and dark theme, Material You on and off.
  (3) Rename: the keyboard is up as the dialog opens, the field focused. Emulator or pong.
- **Remove and bring back the Terminal page (11-04, #202, #200).** (1) Two sessions, three
  windows in all: long-press a Terminal card › Remove Terminal asks "Close 3 windows and remove
  Terminal?"; Cancel keeps everything; Close stops the shells, the Terminal cards go, no undo
  snackbar, the wall lands on a neighbour, Add terminal appears. (2) Force-stop and relaunch:
  still no Terminal page, and no shell started (the empty-workspace autosave). (3) Each of
  Ctrl+Alt+C, the drawer's New terminal, a Terminal=true Linux app and `launcherctl` pane open
  brings the Terminal page back after the last Home page, lands on it and opens the window
  there. (4) Edit pages › Add terminal adds an empty Terminal page after the focused card;
  landing on it starts a shell. (5) The Terminal page's own look (a detached canvas tint) after
  it comes back. Emulator or pong.
- **Edit pages under Terminal only (11-04, #203).** Set Use as to Terminal only. (1) Appearance
  editor › Terminal card › Links shows Pages; it opens Edit pages with no Home card and no Add
  page pill, and a "Bring back N Home pages" pill (N = the pages set aside). (2) Tap it: the Home
  cards come back in their order with their icons and widgets, the pill goes, Add page returns,
  and Settings › Use as reads Home screen. (3) Back out: Home pages are on the wall again.
  Emulator or pong.
- **Edit pages card menu and Display picker (11-04, #201, #200).** (1) Long-press a Home card
  and let go: a menu with Set as home, Rename, Remove opens at the finger; long-press and move
  on: the menu goes and the card drags as before. (2) Rename a Home page and a Display page: the
  dialog's keyboard comes up, the card shows the new name; an empty name goes back to "Page n" /
  "Display". (3) Remove from the menu: the same undo snackbar as the red button. (4) The first
  Terminal card offers Set as home and Remove, later ones Remove only. (5) With a Linux app
  installed, Add display opens a list, Whole display first then apps; an app adds a Display card
  after the focused card named after it; Bind app on a Display card swaps its app (Whole display
  unbinds); an app already bound to a page is not listed. (6) With no Linux apps, Add display
  adds the whole display at once, as before. Light and dark theme, editor open and closed.
  Emulator or pong.
- **Blank Device ring after deep sleep: diagnose (11-04).** Fix 854a10047 (re-read the battery on
  start and screen-on; "–" when there is no reading) is merged, but the cause is unconfirmed. The
  diag build is `../diag-apks/termux-app_v2.0.0-pre.1+dev.0fac95378_termux_apt-android-7_arm64-v8a.apk`
  (sha256 89f7ec81…; v2-redesign 74fcf5a98 plus the `DevWidgetDiag` log commit on the local branch
  `diag/devwidget-pong`, worktree `../tl-diag-pong`; the branch is never merged or pushed). Install it on pong with the
  developer's go-ahead (staging, test key: updates over the pre release). Then the developer leaves the
  phone asleep for an hour or more and wakes it. Read `adb logcat -s DevWidgetDiag`. Done when the
  wake's lines name the cause: `readNow` with `sticky=true` and a level, then `bindLive label=<level>`,
  means the fix holds. `label=–` means the system gave no reading. Report which, then delete the
  branch, the worktree and the APK · pong.
- **Temporary Display page (11-04).** With no Display page on the wall: (1) tap a Linux app in
  the drawer: a Display page appears and the app runs on it; Edit pages lists no Display card and
  Add display is enabled. (2) Close the app: about 2 s later the page goes, the wall lands on its
  neighbour, the display stops. (3) Relaunch, open Edit pages, tap Add display: a Display card
  appears, the app keeps running; close the app and the page stays. (4) Relaunch from a wall with
  no Display page, force-stop with the app up, start again: no Display page left. Emulator or pong.
- **Live glass wave 4 owed (11-03).** (1) The bars at a cold start (force-stop, relaunch): no
  see-through status or window bar before the stored frame lands. (2) Live widgets on pong (wave
  3b): Live blur on, a widget shows the icons and widgets beneath it; off shows wallpaper only.
  (3) Android 8-11 panels on a real device: drawer, palette, sheet and menus blur what is beneath
  them and release on close (API 30 emulator only so far). (4) Contrast on custom schemes on pong
  changes the terminal live. (5) The Wallpaper entries on pong: each opens the picker, and the
  Workspace's More sheet has one.
- **Live blur floats (11-03, wave 3).** Live blur on: a Workspace float over a terminal printing
  (`yes`) shows the text scrolling blurred behind it; two overlapping Home floats, the upper shows
  the lower's glass and text, raising swaps them; minimise, restore, close, dock back leave no stale
  glass (a docked-back pane is on the stored frame); keyboard up (pong).
- **Live blur on the dock (11-03, wave 2).** Live blur on, Floating dock, terminal text or a page
  sliding under the dock: the dock shows it blurred and following; Live off shows wallpaper only;
  blur strength the same either way (emulator or pong).
- **Battery & performance at the root (11-03, a38b183c3).** Pong 11-03: root row in System
  above General, page opens, glyphs read at 1.0× in dark. Still owed: two-pane (landscape)
  Settings, search for "Glass refraction" lands on the page, glyphs at 1.3× font and in light.
- **Dock glass after the GlassEffects move (11-03, 5559211a5).** Emulator was pixel-identical for
  the four dock types, Docked; still owed: Floating style for the four types, and a held extra
  key's lens on the dock still magnifies and fades (pong).
- **Home key with Start in on the Terminal (11-02, 1d7506c51).** With two Home pages and Start in
  set to the Terminal page (then to a Display page), the extra keys' house key from the Terminal
  goes to Home 1, not the nearer Home 2; with Start in on Home 2 it goes to Home 2.
- **One icon size (11-02, ADR 0020 §3; cap rule a6f81e3c3).** Pong 11-02: Dock card reads "7 pinned
  apps fit up to M", L/XL off (Docked). Still owed: pin an 8th (drops to S), unpin (choice returns);
  Floating with Lens beads on the Terminal caps 7 pins at S; Home grid icons equal the dock's; no
  icon touches the edge. Pong's stored choice is S from the old migration: pick M once.
- **Home in landscape (11-02, ADR 0020 §12; turn fix a364a4624).** Pong 11-02 on +dev.19df39d42: a
  turn cut the grid to 5 columns and saved the reflow (fixed; emulator with pong's store: no write
  through 90/270/0). Owed on pong with real widgets: rotate both ways and back, widget store
  revision unchanged, every widget keeps its cell, no widget zooms. Pong's landscape keeps its own
  dock setting (hidden there, developer 11-02), so no side bar on pong is expected.
- **Glass controls reach + dark sections (11-03, 509b634db).** On pong, dark theme: the editor's
  sections stand apart from the sheet (lighter fill, thin outline) and, with Appearance open, the
  unselected Look tiles (Mist, Tint, Solid, Custom) still read as tiles on the lighter section.
  Keyboard card › Applies to Global › Opacity and Blur move the keyboard on Home and on a Display
  page as well as the Terminal; Customization Opacity moves the Home keyboard while no Keyboard ›
  Home value was set. Turn Glass refraction off in Battery & performance: Depth and Edge read
  "Glass refraction is off." and the card shows a Glass refraction link that lands on the switch;
  with Reduce idle activity on they read "Off while Reduce idle activity is on." · pong, go-ahead.

- **Removing a Display card (11-02, #193).** Edit pages › Display card × with Firefox on it: after
  the Undo window a stop question (an app is open); Confirm: `wall_pages.xml` has no page 9 and no
  `pending`, X and Firefox stop; Cancel: the card's page is back and the display runs; Undo before
  expiry leaves the display untouched.

- **CRT wave 1 on pong (11-02, 41b6a6036).** Terminal effect CRT on a dark Look and on a bright
  glass tint: the 8/10 dp bezel reads lit at the top and shaded at the bottom over glass and over a
  solid fill; the screen edge has an inner shadow; box lines carry a faint orange/blue fringe; the
  triad mask is a hair of colour fringe on glyphs, not a grid over the background (emulator 11-02
  showed a grid over saturated green; judge on the phone); the dock, status bar and keyboard keep
  crisp icons and letters. Typing with Terminal effect on and off: no missed echo frames beyond the
  10-22 baseline (`project-docs/active/input-latency-study.md` method).
- **Trail halo + decays on pong (11-02, 963e8329e).** Cursor trail Default and Comet on a dark Look
  and on a bright glass tint: a faint additive glow (peak alpha 0.28 since the developer's 11-02
  look, a bright core within a cell and a skirt two cells out with no visible edge) hugs the
  trail quad on moves in both directions (no dark band, no glow only inside, no pale box); a
  `cursor_trail_color` from kitty.conf colours the halo too; the other four styles are unchanged.
  Typing feel at kitty's 0.10/0.40 s decays (the app's defaults again since 11-02): a newline's
  streak settles well under a second over a slow ssh echo; a `cursor_trail_decay` line in
  kitty.conf still wins. Known: an echo burst of 3+ cells starts a trail (kitty's 2-cell
  `cursor_trail_start_threshold`), so fast typing over ssh trails now and then.
- **CRT screen events on pong (11-02, 2b7484ebd).** Terminal effect CRT (also green, amber), more
  than 1.5 s after the last key or scroll: a bell (`sleep 2; printf '\a'`) tears one 4–5 row band
  sideways with a red/blue split for about 150 ms and nothing else moves (emulator: seen); a pane
  opened by touch or `launcherctl pane open` powers on from a bright centre line over about 400 ms
  (emulator: seen); tapping the other pane of a two-pane window shears it sideways for about
  300 ms, strongest at the top and bottom (emulator: not resolvable at 4 fps); a shell killed from
  outside (`kill -9 <pid>`) shows about 300 ms of grey snow before "[Process completed (signal 9)]"
  (emulator: seen). Nothing within 1.5 s of a key or a scroll, nothing with Remove animations on,
  nothing with Settings › Terminal › Text & display › Screen events off, nothing on TFT or on the
  chrome. A pane at rest after an event draws no further frames (gfxinfo frame count flat).
  Known gaps, not bugs: a plain `exit` closes the pane before the snow; power-off is unwired; a
  split or focus change started from the keyboard is held by the typing rule.
- **Idle animations + dim on pong (11-02, 88e25065e).** Battery and performance › Idle animations
  off (default): a CRT pane at rest renders no frames (`dumpsys gfxinfo com.termux reset`, 30 s,
  then `dumpsys gfxinfo com.termux`: 0; emulator: 0 in 20 s). On: the pane breathes very gently
  with no flashes at a low steady rate (emulator: 120 frames in 20 s, 6 fps on CPU rendering; the
  brief aimed at about 20); one small sideways tear within 2–5 minutes of idling (not waited for
  on the emulator); everything stops on a key for 1.5 s, on screen off, Lazy mode, power save,
  Remove animations, or when the pane leaves the screen. Dim, switch or not: with two panes the
  unfocused CRT pane's text is about 10 % darker and slightly greyer, snaps on tap (emulator:
  near-white pixel share 1.3 % vs 2.7 %, seen); one pane alone is never dimmed; TFT never dims.
- **Solid CRT bezel on pong (11-03, wave 7).** Terminal effect CRT on a glass Look with a
  visible wallpaper: the 8/10 dp band round the screen is a flat opaque panel tone (the drawer's
  colour), no wallpaper or blur through it, lit at the top and shaded at the bottom as before, the
  1 dp line still round the screen; the glass blur and tint still show inside the screen behind the
  text, and the near-black tube face at the picture's pulled-in corners stands off the bezel. Same
  with Glass off and on the light theme (a pale bezel). TFT: the same solid band.
- **Deck keyboard on pong (11-03, wave 8a, 2734fdf36).** Terminal effect CRT, docked in-app
  keyboard up: the keyboard bed is an opaque dark plastic in the drawer's tone (no wallpaper
  through it), each key a raised push-button with a faint lighter top edge and a 2 dp darker foot,
  6 dp corners; return is amber with dark ink; hold Ctrl: its label turns amber and a 2 dp amber bar
  sits above its foot; release: plain. Docked form: the keyboard card's rounded corners still clip
  the bed. Effect off: the Look's glass keyboard comes back exactly. CRT green / amber / Material:
  return and the latched bar take green / amber / the theme's primary (emulator emu2, dark theme:
  bed, bevel and amber return seen; latched bar and the other palettes not exercised).
- **Deck window bar + extra keys on pong (11-03, waves 8b-8c).** Terminal effect CRT on the
  Workspace: the window bar is an opaque dark slab (lit top, shaded bottom, no wallpaper through
  it, the blur off); each window chip and the "+" a raised 4 dp button with a 2 dp foot; the chip
  in front has an amber title and a lit 5 dp LED at its left; the sessions count square at the
  bar's left is the same button without an LED; a window that rings its bell behind
  lights its LED; a working window's LED breathes between a quarter and full; an idle one is dark.
  CPU, RAM and weather sit in one near-black inset window in cyan with a soft glow, the dots dim
  cyan; sizes as before. The extra keys row: opaque bed, raised buttons, the active mode button
  (keyboard / mouse pad / ...) amber with a 2 dp bar, a latched CTRL amber. Docked: the bar card's
  rounded corners still clip the slab. Effect off and on three times: the bar's glass and blur, the
  stats cluster's padding and the row's look come back exactly. CRT green / amber / Material: the
  lights and the display take green / amber / the theme's primary. Home page: everything plain.
- **Deck bands + single frame on pong (11-03, wave 8e, e29a3379f).** Docked layout, CRT on, the
  keyboard up: the band behind the system status bar (clock, signal) and the band behind the
  navigation pill are the same opaque slab as the bar and the keyboard, no wallpaper through
  either; look for a seam where the status band meets the bar's top (each band shades on its own).
  The CRT pane's frame is one opaque ring: no thin light line outside it and no grey line between
  it and the screen; the glass blur and tint still show inside the screen. Keyboard down (dock foot
  showing): the foot keeps the dock's own glass. Effect off: both bands gone, the status strip's
  glass and blur back, the pane's edge light back round the glass slab.
- **Live windows while swiping (11-01).** Three Workspace windows, each running something that
  prints (`top`, `cmatrix`). From the middle one, drag the border slowly left: the next window
  slides in live beside it, its output moving, at the same size and glass as the current one.
  Release past half: no blank frame, no flash, the arriving window is in front with its prompt
  where it was; the motion log shows no `pty-resize` at the landing. Open a window with the
  keyboard down, go to another, raise the keyboard, then drag toward the first: its one
  `pty-resize` comes as the drag begins, its rows fit its pane as it slides in (text the same
  size as the current window's), and none comes at the landing. Release short: both
  slide back and nothing changes. Repeat right, repeat with a split window and a Stacked window
  as the neighbour, and with the keyboard up. A chip tap and a keyboard shortcut still switch at
  once. Settings › Battery and performance › Live windows while swiping off: the swipe shows the
  old empty slot then the slide at release (today's behaviour). At rest (`dumpsys gfxinfo`, no
  touch) the idle frame count is unchanged from the setting off.

- **Home → Terminal, one pane, keyboard closed: no bottom snap (11-01, flush padding).** On pong with
  a single pane and the keyboard closed, swipe Home → Terminal (after one Terminal → Home trip
  since the launcher started). The pane's bottom gap above the extra keys is there for the whole
  slide and does not move at landing; the bottom text rows do not jump up. Was: the pane ran
  flush onto the toolbar mid-slide and pulled up ~10 px (4dp) at landing.
- 2026-11-01 · e9b879382 + 1a21b62f8 + 3c4454300, Home ⇄ Terminal keeps the terminal's rest; stacks ride with their pages; Home 2 wears Home's dock · emulator-verified 2026-11-01 with pong's prefs (Home 2 → Terminal, keyboard closed: no pty-resize between crossing-begin and land, the toolbar rides in under the pane, no band, no drop at landing; Home 2 draws Peek like Home 1). Owed on pong: the same swipe both ways with keyboard closed and open; Home 1 ⇄ Home 2 with no dock pop; the toolbar's glass while it slides sideways (Floating and Docked); a Home float terminal, if used, is not laid out short; reduced motion; the stack no longer rises after landing (f2b4cc385 reversed by 72682966b; revert that one commit to bring the rise back) · pong.
- 2026-10-31 · Workspace terminal 4dp off its bars, padding favours rows, pills moved (merge after a6d1fa3d5) · pong, Terminal page, keyboard up and down, Floating and Docked status and dock, one pane and split, Corners 0 and bare glass, light/dark, font 1.0 and 1.3: 4dp above and below the pane, Margin at the sides; rows before vs after (expect +1 to +2); corner glyphs never cut; the keyboard pill sits on the pane's rim and the swipe still works (on a bare square pane it may touch the last row); the status pill hangs under the bar and the pull works from it; the Docked insert's glass matches the pane, also mid-slide Home ⇄ Workspace; the Floating side rails (A–Z, side apps) may stand ~8dp longer than the pane at each end; Home's spacing and pills unchanged; no terminal resize as a slide lands (motion-invariants.sh) · pong.
- 2026-10-31 · 2e476b3dd + the Media polaroid merge, plan-hosted widgets keep their parts in place · pong: the Media card, at its size and after a few seconds of playback (a progress tick re-lays it), shows the art inset with its own corners and nothing over it, words beside it, the bar and back/play/next at the foot; an upright 2x2 card is a polaroid (square art across the top in a thin 8dp frame, title, artist, bar and controls centred below); the 255x160 Short card has square art on the start side with three controls beside; a long title ellipsizes; the art's corners stay concentric after a resize between the layouts; Resume, idle and paused; Clock, Calendar, Weather, Notifications stay put after a rebind or page change; back/next no longer wear discs, check they still read as buttons; light/dark/black, font 1.0 and 1.3 · pong.
- 2026-10-31 · ADR 0018 wave D, the dock as one surface (2801c1f14; the root stops fitting the bottom inset, `BottomInsets`, the dock host reaches the screen's edge) · seen on pong 19:30: the seam loop reads +2.5 / +3.4 / +2.4 at opacity 34 / 0 / 100 (was +12), Home 2 wears Peek like Home 1, Home's Peek card stands on Home's keyboard. Still owed: `dockfoot-check.py` at row 2349 reads under 4 at dock opacity 0, 34 and 100; the dock never sits under the gesture pill after an insets change (rotate, the system IME shown and hidden, a notification shade pull) or a Peek ↔ Tray switch; Docked Tray, Floating Tray, Beads, Peek, keyboard down and up, one pane and split, light/dark/black look as before but for the seam (no band under the pill for the Floating Tray card); the terminal's rows, widgets, drawer, sheets, palette, A–Z tab and floating keyboard stop above the pill; the system keyboard over the terminal and over Home moves nothing and leaves no glass under it; Display with the system keyboard keeps its room; the editor's Dock card rings the dock and its rows change it live; `launcherctl look set opacity 95 --element dock` darkens the whole dock, foot included; `motion-invariants.sh` over a Home ⇄ Terminal round trip reports no new resize; `launcherctl keyboard show` once did not return for ten minutes although the keyboard came up: watch for it · pong.
- 2026-10-30..31 · the look grid (ADR 0017 W1–W3: 322432562, ed18a80a3, 5672889bc, fb44e0fb2, 732a34e39, ef0f80ede) · emulator at font 1.0 and 1.3, light, dark and scheme chrome, then pong: installed over the previous build with no data cleared, Home, every page kind, dock, status bar, keyboard, terminal, Card widgets, drawer, folders, menus, sheets and the rename chip look as before (a blur of at most 2 dp may shift), and a fresh install shows Clear and Home's keyboard as Mist over widgets; every glass card shows the Where and What chips (the Appearance card only Where) and reads Look, Frost, Opacity, Tint, Colour, Corners, Depth, Edge, then More: Grain, Rim; every value slider is a horizontal row with no clipping; Dock card Look, Grain and Rim change the dock alone; What → All glass moves every glass element in the frame, Done keeps it, Undo returns it; the source dot goes tertiary on the dock and primary on its page; long-press a row name, "Reset this row" clears that cell only and is disabled where it holds nothing; Margin and Vibrancy are never greyed except Margin on the Dock card until What is All glass; Display pages show the keyboard Clear; the Keyboard card's Where chip offers Home pages and a kind value wears the secondary dot; `run-as` write `look.opacity.dock` 90 and `look.frost.dock` 0 (force-stop first): the dock turns solid, the drawer, folder popups, menus, terminal sheet and rename chip keep their glass; idle PSS (`dumpsys meminfo`, Home, one Card widget) within noise of before · emulator, pong.
- 2026-10-31 · editor odds · an App menu on an app with many shortcuts shows four, then More shortcuts; the Widget editor's Back keeps the edit and its cross discards it; in the Appearance editor a tap on a Card-form widget raises the Widgets card with the ring round that widget, a tap on a Bare widget opens Home's card with no widget ring; a card swap (dock, then status bar) keeps the sheet at half · emulator.
- 2026-10-30 · 418a2d7ef, the setup sheet fix after 47f2e0316 and b324fb00f · on pong: Appearance › Global › Change wallpaper › Choose photo › crop › Done shows no editor flash, shows its progress line, and ends on the Global card with the new wallpaper and accents (also with a photo whose colours match the old one, launcher-only mode, Choose photo then cancel, and the setup wizard's wallpaper card, whose Done still closes); with a setup card open and wallpaper mode off, applying a wallpaper rebuilds without opening the card sheet, and opening it from Settings › Setup then applying one that switches the mode brings it back; a long-press on the status bar's top offers Expand or Compact status bar, lands as the status swipe does, survives a restart, and is absent with the bar down a side · pong.
- 2026-10-30 · ADR 0016, the wall's page stops (#183 to #187) · pong, with Home, a Terminal window and a Display page: from the Display page its key then Home lands on Home; the Terminal key then a left swipe on Home slides the Terminal page in; the Android home button from the Display page shows Home; a fling right on the Terminal page's seam slides and lands with no rubber-band; with two Home runs or two Display pages, the leaving page shows a still in the chrome's ink and the arriving one turns live on landing; with every Terminal window closed, reaching the empty Terminal page by swipe or by its key starts one shell on landing with no over-swipe; the extra keys show the go-to keys for the page kinds the wall has and fade those that cannot act · pong; emulator pass at https://amal-build.taild9c5dc.ts.net/reports/wall-cells-2026-10-30/.
- 2026-10-29 · a11264eec (LorieView lastClipboardTimestamp starts at 0) · on pong: copy a URL in the terminal before the display starts, start Firefox, tap its address bar and use the keyboard's paste key: the URL appears; copy in Firefox and paste in the terminal · passes when both directions carry the text · pong.
- 2026-10-29 · the terminal palette wears the pane band's ink (fix/contrast-polarity-flap) and the herdr theme hook keeps the user's rows (after c496185e3) · on pong with the blue wallpaper and herdr open: Appearance › Terminal › Contrast › Harder, Done, reopen the editor, Done again, leave and return: the terminal keeps one ink, the editor's miniature matches the live terminal, herdr re-themes at most once; Default and Softer unchanged. Then, with the developer's `claude` row in `~/.config/herdr/config.toml` commented `# launcher-material:` and the shadowing `~/.termux/theme-templates/herdr/` copy removed, toggle the herdr tool or flip light/dark: the row comes back plain, the `[theme.custom.*]` tables and `auto_switch` are still spliced, and herdr's Claude sidebar keeps the project folder in teal across a re-theme · pong.
- 2026-10-29 · the home press and the extra keys' house follow Start in; Edit pages fixes (0ab0ea5f4, 7d753cc0c, 93c35220f and the overview round 6a688dc92..1504e53d4) · on pong: in Edit pages the terminal card's home button makes Start in read Terminal (the display card, Display); the home press then lands on the first terminal window (window 1 in front), on the display when it is marked, on a marked Home page as before; a return from another app shows the page left; after a force-stop the launcher opens where the marker is; the overview opens at once, the sheet folds to its header, the chips read Add page, Add terminal, Add display whole at font 1.3, a tap on a side card centres it and a tap on the centred card zooms in, Back asks no Discard, and with herdr running five swipes between Home and the terminal card write no `TermGeom` resize line until the card is zoomed into · pong.
- 2026-10-28 · fix/sound-ghost-server (3e6eb6b52; emulator passed, pong owed) and 4a5918b59 · Exit in the notification with `pacat --raw /dev/zero &` playing, open a new pane: `pactl info` answers and a new `pacat` shows RUNNING; after `rm -rf $PREFIX/var/run/termux-launcher/sound` a new pane restarts the server within ~15 s (logcat `Termux.LauncherSound` "pulseaudio is broken"); a new pane after Exit has `DBUS_SESSION_BUS_ADDRESS` and `dbus-send` lists names. Separately, Home → Terminal with the pinned apps under the keyboard, Floating, extra keys off writes one `TermGeom` resize per crossing (pong-verified for the default layout only) · pong.
- 2026-10-22 · PR #99 (migrations 9–13) and layouts step 17 · upgrade the shipped stable (1.0.6 / 1.1.0 release APK, from the GitHub release) over a real profile with a Home dock height, Corners or Margin set apart, a Prompt line or Taskbar dock style and a Custom Look: Home keeps its rows, Prompt line → Tray + Search field, Taskbar → Tray + Drawer tile, the Look keeps its stop and colour, no first-run tour · emulator.
- 2026-11-01 · 1ac2f4ef6 (#191 follow-up) · on pong (11.5 GB MemTotal), with the launcher in
  front, `tai status` then `POST /v1/ai/runtime/load` of the usual E4B model on `gpu`: the
  response's `memoryBudget.reserveBytes` reads about 1,970,000,000 (a sixth of MemTotal, up from
  805,306,368) and `neededFreeBytes` = `estimatedBytes` + that; the load still fits when
  `availableBytes` clears it, and a refusal shows those three numbers in agreement (no
  `fits:false` while available exceeds needed). Record the three numbers either way · pong, go-ahead,
  curl from a shell on the phone with `~/.launcherctl/endpoint` and `token`.
**Pane borders card (2026-11-01, #206).**
Seen on the emulator (emu2, staging x86_64 of 9a8e5683c, 1080 × 2400 and 360 × 640 at font 1.3):
the row and its label open the subcard, the back arrow and system Back return to the Terminal
card scrolled to the row, Custom colour's dialog gets the keyboard and commits `#DC143C` /
`#00AAFF` into the strip and the live float, garbage keeps the dialog open, Glow Steady shows on
the live float, Back twice reaches Unsaved changes and Discard restores the entry values. At
360 dp wide the six-swatch choosers fall into `SegmentRow`'s vertical stack (one swatch per row),
nothing clipped. Still owed:
- 2026-11-01 · 9a8e5683c · on pong: a crimson custom border on a single Terminal-page pane and on
  a split (the focused pane Primary, the other crimson), Glow Steady, light and dark theme, the
  launcher's scheme chrome on and off; the in-app keyboard comes back after the Custom colour
  dialog's OK and after Cancel; Reset pane borders and the Terminal card's Reset put back the
  theme outline, Primary and no glow · pong, go-ahead.
- 2026-11-01 · 9a8e5683c · Docked style: the divider edges take the border colour and no glow is
  drawn round a Docked pane (the glow is skipped there on purpose) · pong or emulator.
- 2026-11-04 · #205 · After `pm install -r` over a running build, with the launcher not opened, `curl $(cat ~/.launcherctl/endpoint)/v1/ai/status` answers (the API binds when the main process is revived as a service host) · pong or emulator.
**Linux apps guide marks an installed distro (2026-11-04, #176).**
- 2026-11-04 · feat/176-installed-distro · emulator: install Debian by hand with
  `proot-distro install debian`, then open Settings › Linux apps › Set up Linux apps and go to the
  Linux step: Debian shows "Installed" (in place of "Recommended"), the others do not, and the
  copied command on the last step has no `proot-distro install` line. With nothing installed the
  picker and command are as before · emulator, no pong needed.
- 2026-11-04 · feat/176-installed-distro · emulator, #177: pick a full Linux, tick Desktop on
  the Apps step (off by default, not offered on the Termux route); the Command step shows the
  Start with note and the command installs xfce4. Run it, then `dbus-launch startxfce4` with
  DISPLAY set draws XFCE. Debian, Ubuntu and Arch (Arch is untested: pacman group xfce4) · emulator.

**App menu shortcut icons and the More shortcuts card (2026-11-04, #198).** Not yet seen on a
device. Owed:
- 2026-11-04 · App menu on a Home icon, a drawer icon and a dock icon of an app that publishes
  shortcuts (Chrome, Messages): each shortcut row shows that shortcut's own icon, untinted; an
  app whose shortcuts have none keeps the bolt glyph · pong.
- 2026-11-04 · More shortcuts on an app with more than four shortcuts: the side menu is the App
  menu's card (same rows, shades, type, no arrow), beside the card and centred on the More row,
  on the left when the right has no room, scrolling when taller than the screen; a tap launches
  and closes both; the main card's drag highlight still follows the finger onto it · pong.
- 2026-11-04 · The same in light and dark theme, with the launcher's scheme chrome on and off,
  and with an icon pack pinned (shortcut icons are the app's own, not the pack's) · pong.
- 2026-11-04 · Font scale 1.3: long shortcut labels ellipsize on one line · pong or emulator.

**Move to category in the App menu (2026-11-04, #199).** Not yet seen on a device. Owed:
- 2026-11-04 · Long-press an app in the drawer (every view type) and a Home icon of a drawer app:
  the card shows Move to category alone below the shortcuts; it opens the categories picker, a
  pick (or Automatic) moves the app and the Categories view regrouping follows · pong.
- 2026-11-04 · The row is absent on a dock icon, a dock folder member and a Linux app entry · pong.
- 2026-11-04 · Light and dark theme, scheme chrome on and off, font scale 1.3 (one line, ellipsized) · pong.

**New float terminal tile on the dock (2026-11-04, #197 narrow).** Not yet seen on a device; the
tile is off by default; the switch is New float terminal in the Dock card's Options and the
Elements list (pref `dock_float_terminal_button`). Owed:
- 2026-11-04 · The switch appears in both places, flips the tile live, is undone by Discard and
  cleared by the Dock card's Reset · pong.
- 2026-11-04 · With the switch on: the apps row gains one last column holding the tile; a tap opens a
  floating terminal on Home as the Terminal entry's New float here does; Docked and Floating dock
  styles, keyboard up and down, one pane and many · pong.
- 2026-11-04 · Both the Command palette tile and the Float tile on: palette then Float, each one
  column; dragging the palette tile or the Drawer tile never lands on the Float column; a page
  swipe holds all still tiles, Lens beads keep a bead under each · pong.
- 2026-11-04 · Light and dark, scheme chrome, glyph ink on a pale wallpaper (glyph is the status
  bar's add-window drawable, the closest existing) · pong.

**Swipe-up handoff from other apps (2026-11-04).** Not yet seen on a device. The latched pref
`app_launcher_animation_safe_mode` clears on the next cold start; an app with no icon here is no
longer counted as an error. Owed:
- 2026-11-04 · Swipe home from an app pinned on the dock, from one on a Home page, and from one
  opened from the drawer: the first two shrink into their icon, the third plays the plain
  fallback, and `app_launcher_animation_safe_mode` stays false. `adb logcat -s
  LauncherTransitionController` shows one "Gesture handoff" line each (source dock, home, none) ·
  pong.

- **Last change wins on the Global and Workspace sliders (11-04).** Dock card (Applies to
  Global) Opacity to 20, Keyboard card (Applies to Home) Corners to 30. Global card: move Opacity;
  the dock now wears Global's opacity on Home and Terminal pages, the keyboard keeps its 30 dp
  corners. Move Global Corners; the keyboard's corners follow. Dock Opacity to 20 again: only the
  dock changes. Workspace card Opacity: Terminal and Display pages follow, the dock on Home stays
  at 20. Discard after any of these puts every value back. Global Margin moves the air on every
  surface · pong.
- **One-line slider rows (11-04).** Global, Dock, Terminal and Keyboard cards at font 1.0 and
  1.3, light, dark and the launcher's scheme chrome: each slider is one line (glyph, name, a thick
  track) with its value inside the track's end, white-on-fill where the fill reaches it and grey
  over the empty track, never cut; a long name at 1.3 falls back to name and value over a
  full-width track. Drag each: the value follows with no jump in the card; long-press a name opens
  its menu; TalkBack reads the value · pong.
- **Rows in group order (11-04).** Global, Workspace, Dock, Terminal and Keyboard cards: inside
  each section the Look and the structural choices come first (Dock Type, Status bar Position,
  Keyboard Form, Style), then the sliders, then switches and small choices (Rim, Contrast), then
  colours (Tint colour), then links (Pane borders, Refraction), with a small gap
  between groups and no new headings; the Keyboard's Linked toggle still stands between its two
  opacities; sections and their open or closed state are as before · pong.
- **Home card sliders reset what is beneath them (11-04).** Dock card (Applies to Global)
  Opacity to 20; Home card Opacity: the dock on Home pages follows Home, on the Terminal page it
  stays at 20 · pong.
- **Margin per page kind (11-04).** Before anything: Home and Terminal pages look as they did
  after the update. Workspace card Margin to 20: Terminal and Display pages' dock, status bar and
  pane air move, Home pages do not; Home card Margin to 4: Home alone moves; swipe Home ↔
  Terminal: each settles on its own margin with no jump. Terminal card Outer margin and Dock
  (Applies to Home) Margin edit those levels; Global Margin resets both kinds; Discard restores ·
  pong.
