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
- **Removing a Display card (11-02, #193).** Edit pages › Display card × with Firefox on it: after
  the Undo window a stop question (an app is open); Confirm: `wall_pages.xml` has no page 9 and no
  `pending`, X and Firefox stop; Cancel: the card's page is back and the display runs; Undo before
  expiry leaves the display untouched.

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
