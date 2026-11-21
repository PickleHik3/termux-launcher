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

- 2026-11-19 · proto/glass-ground-283 (#283) · The prototype: the status bar's and the dock's glass draw the ground (wallpaper dim) of the page beneath them, gate `GlassGround.ON`. Owed on pong, one recorded round per build (`scripts/pong-round.sh`), the Terminal pages' wallpaper dim raised (about 60) and Home's at 0: frame times of both crossings (Home → Terminal, Terminal → Home) with the gate on, beside the same build with `GlassGround.ON = false` (the baseline), Docked and Floating, Live blur off and on, the 90 Hz rate named beside each number (docs/agents/performance.md); `glass-ground` lines in the motion log show the dim under each bar per frame (`DIAG(283)`). Then the developer's verdict on #283: build it, or withdraw ADR 0031 §5. Passes when the bars darken with the Terminal's dim at rest and through both crossings with no step at the seam or the landing, look as on Home with the dim at 0, and the frame times are not worse than the baseline beyond noise · pong. Not seen on pong: any of it.
- 2026-11-19 · feat/one-look (#281, #282) · The chrome wears one look on every page and the crossing blends no look. Passes also when, on both crossings: the Peek dock still turns from card to slab, the keyboard still fades toward the end where it is down, the extra keys' legends, the dock tiles and the prompt line drift in ink with no flip at the landing, and the status bar's band and frost show no step at the first or the landed frame (the arriving frost and the arriving-look band are gone). Passes when, after setting the dock, status bar and keyboard to their own Opacity and Blur and Global to a Look, both crossings (Home ⇄ Terminal) show the bars, dock and keyboard with the same glass and tint at the first and the landed frame, Docked and Floating, keyboard up and down; the Appearance editor shows no Applies to row on the Status bar, Dock and Keyboard cards and no glass rows on the Home and Workspace cards; the Terminal card's Opacity still darkens the panes only · pong, one recorded round (`scripts/pong-round.sh`) plus screenshots of the four cards. Not seen on pong: any of it.
- 2026-11-19 · feat/one-look (#284) · The Appearance editor has six cards. Passes when: the pill reads Global · Home · Terminal, and Global · Terminal under Use as: Terminal only; the pane corner, the terminal sheet's Appearance and a swipe in the editor to a Terminal page open the Terminal card, which holds Text, the terminal's glass, Panes, Effects and Layout (with Minimal layout); Global shows Look open and Fine-tune, Shape, Colours and Screen closed on a first visit, with no Layout list, Status position or Dock type; the Status bar, Dock and Keyboard cards show only Opacity and Blur under Appearance (the keyboard its link and Keys opacity, the dock Reflection while Peek), no "All pages" line; the Dock's Bars have no switches; hiding a row on Home's Layout leaves the Terminal's and the other way round; a tap on a widget in the editor raises the Home card; each card keeps its open sections across visits · emulator, portrait and landscape, light and dark; then pong. Not seen on pong: any of it.
- 2026-11-19 · feat/one-look (#286) · Rows moved to the card where their effect is. Passes when: Global › Shape has Icon size (Home's icons and the dock's both resize) and Global › Screen has Text size and Page transition; Home and Dock no longer have them; the Terminal card's Text has Font size (the panes' text resizes live, Discard puts it back); Effects shows Screen events and Composite signal only while a Screen effect is on; the Keyboard card shows Width and Floating height only while the type is Floating and Split gap only while it is Split, the keyboard taking the new size on release; Discard restores every one of them · emulator, then pong. Not seen on pong: any of it.
- 2026-11-19 · feat/one-look (#285) · The reset arrow. Passes when: every slider row keeps an empty slot at its end, so tracks are one length and arrows stand in one column (font scale 1.0 and 1.3); moving the dock's, status bar's or keyboard's Opacity or Blur shows ↺ at that row's end and in the card's header; Global's sliders and every preset leave those own values as they are; the row ↺ puts one value back on Global, the header ↺ the whole surface, and Global's header ↺ asks "Put every surface back on Global?" and clears all three, Undo bringing them back; the Terminal card shows no arrow; no dot or caption marks a row and long-pressing a row's name opens no menu; TalkBack reads the arrow as "Put Opacity back on Global" and the slider as "Opacity, its own" or "following Global" · emulator, then pong. Not seen on pong: any of it.
- 2026-11-19 · feat/one-look (#293) · `launcherctl look`. Passes when, over ssh: `look set opacity 60 --element dock` changes the dock at once and `look get opacity --element dock` answers from dock; `look reset --element dock` puts it back on Global; `look set dim 30 --element background` darkens the Terminal's pages live and `look set opacity 70 --element background` changes the panes live (a write through redressChrome may not redraw them); `--page`, `--kind`, `--element widgets` and `sheets` are refused with one line · pong over ssh. Not seen on pong: any of it.
- 2026-11-19 · feat/one-look-289 (#289) · Settings keeps one home per setting. Passes when: Settings has no old Look page, and the root row Appearance opens the editor on the Global card; Terminal has a TUI app themes page (the Global card's TUI app themes link opens it, and switching a tool on still asks for its setup line); Home, Status bar, Terminal › Text and display, Terminal › Windows and panes and Terminal fonts each show one Appearance row that opens the Home, Status bar or Terminal card, and none of the removed rows (Icon size to Wallpaper scrolling, the status switches and 12-hour time, Cursor trail to Composite signal, Split-pane controls to Focus active pane, Font size) is on them; Keyboard has no Size and position page; searching Settings for "Label size", "Fahrenheit" or "Font size" opens the editor on the Home, Status bar or Terminal card; under Use as: Terminal only no Home row is found by search; the Performance page's switches still change the same settings as their card rows; the command palette's Look opens the editor · emulator, then pong. Not seen on pong: any of it.
- 2026-11-19 · feat/one-look-287 (#287) · The Terminal's Background: Opacity, Blur and its own Wallpaper dim (provisional shipped Opacity 40%, `DEFAULT_TERMINAL_BG_OPACITY`, against Global's 10%; the developer picks the number on pong). Passes when: the Terminal card shows Background (Opacity, Blur, Wallpaper dim) with no arrow and no Presets, Grain, Tint, Corners, Rim, Edge or Depth; Opacity darkens the panes only and with Wallpaper dim at 0 the wallpaper around the panes looks as on Home; Wallpaper dim darkens the Terminal's pages, a Display page and the Assistant page and travels with them through both crossings, while Home is never dimmed and the Home card has no Wallpaper dim; Global's Opacity, Blur and every preset leave the panes and the dim as they are; Discard and Undo put the three back; a fresh install shows a terminal darker than the bars; Show wallpaper off gives a solid terminal and on again restores its Opacity; the low-memory preset ends with an opaque terminal · pong, in the one-look check wave. Pong: pick the shipped Opacity. Not seen on pong: any of it.
- 2026-11-19 · fix/keyboard-over-drawer (#294) · The keyboard under an open drawer is part of the drawer's sheet (an exception to ADR 0031's one look). Passes when, Docked, light and dark, with the in-app keyboard up and the drawer opened (and with search-on-open), the keyboard shows the same glass as the drawer, including at Global Clear (the 60% floor), and its top-left and top-right corners are rounded like the drawer's; the look arrives and leaves with the drawer's motion with no step or flash at either end (recorded frames of an open and a close); once the drawer closes the keyboard is square-topped and clear again. Floating: the keyboard card keeps its own corners and wears the sheet's glass while the drawer is open · pong, one recorded round plus screenshots zoomed on the keyboard's top corners. Not seen on pong: any of it.
- 2026-11-20 · fix/dock-filter-landing · The dock and keyboard glass no longer flash at a Home ⇄ Terminal landing (the landing wore the left page's even-out for 2 frames, or the whole swipe on the first crossing after a start). Seen on pong (Docked, keyboard up on the Terminal, shipped Appearance): rounds flash-r1/r3 (before: −5 dip or +4/+6 step at landing) and flash-r5/r6 (after: from the second crossing `kept=true` and a landing change under 1; the first crossing after a start keeps a −2 to −3 step). Not seen: Floating, keyboard down on the Terminal, a wallpaper change between crossings. Also seen, not fixed: a dropped frame (32 ms) mid-swipe as the keyboard descends · pong.
- 2026-11-20 · fix/kbglass (#294) · The keyboard under the open drawer now stands on the drawer's dim as well as its glass. Passes when, Docked, light and dark, at Global 60 and with Live blur, drawer open with the in-app keyboard up, the keyboard's tone matches the drawer body (no lighter band), and the darkening arrives and leaves with the drawer with no flash · pong, screenshots zoomed on the seam. Seen on the emulator (Docked, light, default opacity): keyboard 175–195 against drawer 173–192 (was 200–216). Not seen on pong: any of it; Floating and Live blur unchecked.
- 2026-11-20 · fix/clockink · A Home widget's ink is measured on Home's ground, not on the Terminal's dim it lies under while the Terminal is in front. Passes when, with a bright wallpaper and a Bare clock, the clock keeps the same (dark) ink after Home → Terminal → Home and after an opacity change · pong. Seen on the emulator with an orange wallpaper. Not seen on pong; Glass widget cards still measure under the page in front (unchanged).
- 2026-11-18 · feat/wr-269 (#269) · Home's long-press menu, the terminal's menu (and its More entry) and the app drawer meet the screen's bottom edge, with the band under the pill from one owner. Passes when, in light and dark: each menu's surface runs to the physical edge under the pill with no line above it and its rows clear of the pill; the Docked app drawer's glass runs to the edge with its grid and search clear of the pill, keyboard down, with the in-app keyboard up and with the system keyboard; the Floating drawer's card stops above the pill with its four rounded corners; no rim or border along any surface's bottom; with each one open the bottom-edge swipe still goes Home or to Recents · pong, screenshots with a zoom of the bottom band. Not seen on pong: any of it; on the emulator the drawer always opened with the keyboard up, so the drawer with the keyboard down and with the system keyboard were not seen anywhere. The command palette stays a floating card by the developer's decision (11-18); #269 does not apply to it. The Floating drawer is a card by design (AppDrawerController resolveOpenRect, ADR 0029 §6).
- 2026-11-18 · feat/wr-268 (#268) · The stack's glass reaches the physical bottom edge and the decor strip is gone. Passes when, in light and dark, Docked keyboard up (Terminal and Home, glass keyboard and one with its own opacity or scheme colour), Docked keyboard down, Floating Tray keyboard up and down, and Floating Peek keyboard down each show no line above the pill, the glass (or the keyboard's panel) running to the edge with its rim on top and sides only, Floating's card still floating; the keys and dock icons stand where they did; a keyboard toggle at rest leaves no keyboard row in the band under the pill and resizes the Terminal once; the bottom-edge swipe still goes to the system · pong, screenshots with a zoom of the bottom band. Not seen on pong: any of it; bands under the keyboard (Docked) get no foot and show wallpaper under the pill.
- 2026-11-18 · fix/wr-crossing-resize (#262) · With two Workspace windows, a keyboard toggle on the Terminal resizes both windows' shells at once, and the next Home → Terminal crossing resizes none. Passes when pong's motion log shows two `pty-resize ... cause=resume` lines at the toggle and no `INVARIANT zero-resize-per-crossing` in 12 Home → Terminal crossings, Docked and Floating, keyboard up and down on each side (Home keyboard up not reached on the emulator) · pong, motion log (`launcherctl motion-log dump`), APK 262-resize-diag-staging-ea54a6a23
- 2026-11-18 · feat/wr-kbd (#263) · The system keyboard rides its own animation. Passes when, on API 28 and 29 (no emulator here), the bar's text field, the palette with the system keyboard and a Settings field each lift the dock above the keyboard; and with no keyboard shown after a request, the motion log shows `hold-released hold=system-ime reason=backstop` and the dock stays down · API 28/29 phone. Not seen on screen: any of it.
- 2026-11-18 · feat/wr-kbd (#263) · The dock and rows rise and sink with the system keyboard frame by frame, with no jump to the end state · pong, screen recording frame by frame, Docked and Floating, keyboard up and down. Not seen on screen: any of it.
- 2026-11-18 · feat/wr-kbd (#262) · Home 2 → Terminal with a float open on Home 2 (named in ADR 0030). Passes when the keyboard rises with the seam and shows no snap at the landing, and the motion log shows `crossing-resized` 0 and one `pty-resize` at most per keyboard toggle at rest, none per crossing · pong (`scripts/pong-round.sh`), Docked and Floating. Not seen on screen: any of it.
- 2026-11-18 · feat/wr-kbd (#262) · The keyboard shows on the frame it is asked for. Passes when opening the in-app keyboard at rest (glass, blur on, light and dark) shows keys and their glass on the first frame with no base-colour frame, closing shows no wallpaper seam at the dock's bottom, and the terminal's cursor line stays visible above the keyboard after it rises · pong and emulator, screen recording frame by frame. Not seen on screen: any of it.
- 2026-11-18 · feat/wr-flicker · Turning transitions keep the frozen pages layer. Passes when, with Live blur on, a Carousel (or Depth, Stack) crossing under the dock shows no change of the dock's glass at its first frame or its last, and a Perfetto trace shows no `LiveBackdrop.pageLayer` section while the wall moves or rests untouched, at most four a second per surface while the terminal prints at rest · pong, Docked and Floating. Not seen on screen: any of it; the perspective of a rotating page is drawn from the matrix the view reports.
- 2026-11-17 · feat/wr-step3 (D1-A step 2, side lanes) · The bottom band stands at the union of both sides' rests. Passes when Home and the Terminal look as before at rest (Docked and Floating, keyboard up and down, one pane and many, apps row under the keyboard on both and not); the Workspace's extra keys slide in from the right with the Terminal page and out with it instead of switching; the container is taller on Home by the extra keys' room, the slab starts below it and the terminal's room is unchanged (no resize on a crossing); with the motion log on `foreign-writer`, `landing-wrote`, `crossing-resized` and the rows guard stay at 0 · pong or emulator. Not seen on screen: any of it.
- 2026-11-17 · feat/wr-264-glass-service (#264) · Live glass keeps one picture: with Live blur on, a float open on Home and a glass widget on Home 2, in light and in dark. Passes when Terminal → Home and Home 2 → Terminal landings, and the first frame of each crossing, show no pop or swap on the dock slab, the float or the widget (screen recording, frame by frame around the landing); a float slides as one piece with no rebuild mid-swipe; with the motion log on no surface's kind changes during a crossing · pong, screenshots and recording, light and dark. Not seen on screen: any of it.
- 2026-11-17 · feat/wr-264-glass-service (#264) · Live blur's policy events. Passes when flipping Live blur in Battery & performance, and turning battery saver on and off from quick settings, change the dock, a float and a widget between live and stored glass at once (no restart, no second swipe); Battery & performance has no See through row · pong. Not seen on screen: any of it.
- 2026-11-17 · feat/wr-264-glass-service (#264) · Live glass at rest costs nothing. Passes when, with Live blur on, the dock, a float and two widgets resting on Home draw no frames (GPU profile bars flat) and `dumpsys meminfo` graphics stays within the 8-slot budget (about 8.8 MiB of blur layers) with nine glass widgets across Home pages · pong. Not seen on screen: any of it.
- 2026-11-17 · feat/wr-256-frame-pipeline (#256) · Emulator sweep after the chrome commit moved into the frame pipeline (no draw cancel, no commit on demand). Passes when Home ⇄ Workspace crossings, keyboard up and down (Docked and Floating), and an Appearance editor Frost/Opacity/Tint drag look as before; a cold start and a relaunch show no frame of unstyled or misplaced dock, keyboard or bars; with the motion log on, `first-frame-release` reads `signal` and `frame-flush` lines name only `editor glass drag`; at rest no frame is drawn (GPU profile bars stay flat) · emulator. Not seen on screen: any of it.
- 2026-11-21 · feat/page-bar (#298) · Page bar at rest. Passes when one pill stands centred above the dock with one filled glyph per page in the swipe order (reorder pages in Edit pages and it follows), Home and Terminal glyphs show their numbers cut out, the current page is the only accent-filled glyph and obvious at a glance, a Display glyph shows only while a display runs, no widget overlaps the pill; with the apps row hidden on Home the pill stands at the dock's place at the bottom; at rest no frame is drawn (GPU profile bars flat) · pong, Docked, Floating and Beads. Seen on the emulator only (see the round-two lead report).
- 2026-11-21 · feat/page-bar (#298) · Tap and slide. Passes when a tap on a glyph goes to that page (Home pages, windows, Assistant, a running Display); a slide lifts the glyph under the finger into a round bubble with the page's name above, its two neighbours grow and the row shifts, the current page's accent fades, a tick plays per glyph, the page changes only on release; sliding back to the start or up off the pill cancels; no glyph shrinks to a dot and no stack plate pokes out of the pill; the slide is not taken by the dock seam swipe, a long-press or Home paging · pong. Seen on the emulator only.
- 2026-11-21 · feat/page-bar (#298) · Folding. Passes when, with more pages than fit (ten and more windows, then many Home pages), the windows fold first into one stacked glyph that reads as a stack with the right count, sliding over it steps through its pages in the bubble, and nothing scrolls or shrinks · pong. The "9 with ten windows" report is chased separately; the emulator read 10 and 11 correctly.
- 2026-11-21 · feat/page-bar (#298) · Dock capsules. Passes when, with the dock on two or more pages, capsules stand under the pill with clear air above the dock's slab or card (about 6dp) and about 14dp under the pill on Docked and Floating, Tray, Strip, Peek and Beads; current wide in the accent, the others small and neutral; they dim while the bar slides; with one dock page none show · pong.
- 2026-11-21 · feat/page-bar (#298) · Keyboard and room. Passes when the pill hides while the in-app keyboard or the system keyboard is up on Home and comes back when it goes down, and turning the Page bar off in the Home card's Layout list (Done) removes the pill and makes Home's grid taller; Discard leaves it as it was · pong.
- 2026-11-21 · feat/page-bar (#298) · The page switcher key. Passes when touch-and-slide from the key pops a full-width bar with equal slots on an opaque card above the extra-keys bar (above the keyboard when it is up), release goes there and the bar goes away; a tap keeps it up for one use, then a glyph tap, a tap elsewhere or Back puts it away · pong, keyboard up and down.
- 2026-11-21 · feat/page-bar (#298) · Looks and text. Passes when the pill, glyph ink and capsules read on light, dark, black, Material You and scheme chrome, with and without a glass widget on the page (the current page obvious on each); at font scale 1.3 the bubble's name is one ellipsized line and two-digit numbers fit; TalkBack reads each glyph as a button with the page's name and activates it · pong.
- 2026-11-21 · feat/page-bar (#298) · Memory. Passes when `dumpsys meminfo` on Home with no glass widget shows no more graphics memory than the build before the page bar by more than one blur frame · pong.
- 2026-11-21 · 09ef9a875 (#298) · Page marks on every ground. Seen on the emulator (Clear, Peek and Tray, generated pale and dark wallpapers): the pill's body equals a glass widget's in light and night, current glyph 4.56–10.2:1, others 3.2–6.9:1, Home's grabber over the dock 3.17–6.98:1. Passes when, on pong's own wallpapers in light and night, the pill reads like the widget beside it, the current page is the strongest glyph, the sliding bubble wears the pill's card with a readable name above it, and the grabber between the pill and the dock is visible; with the dock on two pages the active capsule is stronger than the resting ones (never seen: the emulator's dock stayed on one page) · pong. Not seen on pong: any of it; Mist, black, Material You, scheme chrome; Adaptive legibility on.
- 2026-11-21 · 74a535878, fa42beebe, f6c069f4a · Keyboard theme and Layouts previews. Seen on the emulator: both previews draw the layout in use (QWERTY Español with ñ), chips step through the cycle, a painted ñ is painted on the live keyboard, the Layouts row sits in the card under Font. Passes when the same holds on pong with its own cycle and an edited layout.xml (the preview shows the edited keys), and the Layouts page preview is a sensible height at font scale 1.3 · pong.
- 2026-11-21 · de8f8697b · A layout removed from the cycle. Seen on the emulator after a process kill. Passes when, with a second layout showing on the live keyboard, removing it in Settings leaves the keyboard on the remaining layout at once and after the launcher is restarted · pong.
- 2026-11-21 · b2e719271 · Export logs → Save to file. Seen on the emulator with the permission granted by adb. Passes when Settings → General → Diagnostics → Export logs → menu → Save to file is enabled, asks for storage permission where it is not granted (the prompt was never seen: the emulator had it permanently denied), and writes the log file to shared storage with a toast · pong.
- 2026-11-19 · 7f5395bda (#278) · Photo shader editor at font scale 1.3 and 360 dp. Passes when New shader and From a photo are equal width on one line, the page reads top to bottom as name, Quality, Frame rate, Copy, Photo, Depth, then the code last, and the Depth row in every state (hidden until a photo is chosen, none, downloading, generating, generated, picked, May not match, failed) never overlaps its buttons, on light, dark, black, Material You and scheme chrome · emulator, then pong. Not seen on screen since the one-page rework: any of it.
- 2026-11-19 · 7f5395bda (#278) · From a photo round trip. Passes when Copy puts the instructions on the clipboard, Paste at the bottom fills the code from the clipboard (an empty clipboard shows "The clipboard has no text."), choosing a photo returns the page at the same scroll position with no keyboard and Generate depth in view, and the depth guide link opens the guide page · pong.
- 2026-11-19 · 7f5395bda (#278) · Generate depth, the rest. Seen on pong 11-18 (build 80f701eab): the download question, the download, the generated depth image and Apply. Still owed: Cancel on the question and mid-run does nothing more, a later Generate starts at once, changing the photo clears a generated depth and keeps a picked one with "May not match the new photo.", and Back from the editor leaves no files in the shader's folder (`run-as ... ls files/shaders/<id>`) · pong.
- 2026-11-19 · 7f5395bda (#278) · A photo shader moves. With a photo shader (with depth) as the wallpaper, two screenshots a few seconds apart differ, the picture shifts as Home pages slide both ways and rests when they stop (tlPageOffset), and a touch pushes it and settles over about 2 s (tlTouchAge) · pong.
- 2026-11-19 · fix/scene-layer-above-backdrop 66d27135e (owed to feat/window-restructure, not on v2-redesign) · Shader wallpapers move on the restructured window. Pong's build 01beaf7bb carries the fix as 600b3abbe with scene logging 56ee9239e. Passes when Aurora (or any shader) visibly moves on Home and `logcat -s TLSceneDiag` shows `frame` lines with the clock advancing · pong. Not seen: pong had a plain photo set when the build went on.
- 2026-11-19 · 7f5395bda (#278) · Deleting the Model Centre's Wallpaper depth row (the row itself was seen on pong 11-18): deleting it removes the model and the next Generate asks to download again · pong.
- 2026-11-19 · 7f5395bda (#278) · Cold start with a photo shader (with depth) as the wallpaper: force-stop and relaunch. Passes when the wallpaper shows the photo moving with no black frame and no crash, and the shader's tile shows the photo · pong, prefs edited before the force-stop.
- 2026-11-16 · feat/page-glyph-switcher · Home glyph row. Passes when the row of glyphs sits where the page dots were, above the dock's line, in Docked and Floating dock styles, and no widget is resized · emulator, then pong. Not seen on screen: any of it.
- 2026-11-16 · feat/page-glyph-switcher · Home glyph row contents. Passes when the row shows Home, Terminal, Assistant, Display in that order; Assistant is hidden when Assistant is off; Display is hidden when no display runs; the numbers read right with one and with many Home pages and Terminal windows, 1-digit and 2-digit · emulator, then pong. Not seen on screen: any of it.
- 2026-11-16 · feat/page-glyph-switcher · Terminal only. Passes when the panel has no Home glyph and the setup card says the key exists, with no Add · emulator, then pong. Not seen on screen: any of it.
- 2026-11-16 · feat/page-glyph-switcher · Tap a glyph. Passes when the panel opens with the current page highlighted and stays; a tap on a cell goes there; a tap outside or Back closes it · emulator, then pong. Not seen on screen: any of it.
- 2026-11-16 · feat/page-glyph-switcher · Slide along the glyph row. Passes when cells lift on the wave, the title shows above the highlight, a tick sounds on each new cell, the page changes only on release, and moving above the panel cancels · emulator, then pong. Not seen on screen: any of it.
- 2026-11-16 · feat/page-glyph-switcher · The extra-keys key. Passes when tap and slide behave the same as on the glyph row, the panel is a full-width strip right above the extra-keys bar, with the keyboard up the panel rides above it, on Home, Terminal, Assistant and Display · emulator, then pong. Not seen on screen: any of it.
- 2026-11-16 · feat/page-glyph-switcher · The key's cap. Passes when it shows the current page's glyph and number and updates on page and window change · emulator, then pong. Not seen on screen: any of it.
- 2026-11-16 · feat/page-glyph-switcher · Setup card on an updating install. Passes when it shows once; Add puts the key at the left end of the row each bar reads (its own row, Global's stored row, or a built-in set, which bars still follow afterwards; on a default install every bar's row starts with the key) and the bars show it at once; Not now closes the card · emulator, then pong. Not seen on screen: any of it.
- 2026-11-15 · feat/extra-keys-and-dot · Appearance reset on upgrade. Install over a build from before it (pre.5, and a 1.2.x stable) whose look was customised (another Look, dock style, status widgets, keyboard theme, a font from Settings › Fonts, Home's keys on, a taller dock), with Home pages, widgets, pins, a wallpaper, extra keys and two sessions. Passes when: the first start opens on Clear with the shipped layouts and the default font; the card "A fresh look" shows once on Home after the setup cards; Open Appearance opens the editor, Dismiss closes it, and neither brings it back after a restart; the glass nudge does not show; Home pages, widgets (same cells), pins, wallpaper, extra keys, the page switcher's spot, the Home pill's spot and the sessions are as they were; a second restart changes nothing. A fresh install shows no card · emulator, then pong.
- 2026-11-14 · feat/extra-keys-bar · Extra keys on a side column and along the top edge (Appearance › layout). Passes when the keys stand there, scroll or flip in the right direction and press as along the bottom · emulator or pong.
- 2026-11-14 · feat/extra-keys-bar · CTRL armed on Page 2, then typing `c` in the terminal sends Ctrl+C (a running `sleep 100` stops). Passes when it stops and CTRL disarms · emulator or pong.
- 2026-11-14 · feat/extra-keys-bar · A text input page reached by a sideways swipe still opens the keyboard and sends on Enter. Passes when the text arrives in the shell · emulator or pong.
- 2026-11-14 · feat/extra-keys-bar · Key sets per place. On a fresh install a Display page shows Keyboard, Mouse, Esc, Tab, Ctrl, Alt, and the Assistant page shows Keyboard, Dictate, Paste. In the editor give Terminal its own keys, Save: Terminal shows them while Display keeps its own; "Use Global keys", Save: Terminal's set is gone. A Ctrl armed before moving between places is let go. The side column and top edge follow each place's set. An existing install with a saved `extra-keys` row keeps it, and a fresh install has no place keys · emulator or pong.
- 2026-11-15 · feat/extra-keys-bar · Home pill, look and spot. The switch has no UI yet: set `place.dress.home.portrait.extra_keys` and `place.dress.home.landscape.extra_keys` to true in the launcher's shared preferences (`run-as` + `sed`, then force-stop, in that order, so the relaunch reads them) until the developer picks the switch. Passes when: the pill floats over Home at the right side, vertically centred, as a glass pill that is as wide as its keys with a soft shadow; it matches the other floating glass chrome in light, dark, black, Material You and scheme chrome; it is in the same spot on every Home page; with Home's key size 0.6 and 1.6 it grows and shrinks and the keys stay inside · emulator or pong.
- 2026-11-15 · feat/extra-keys-bar · Home pill, picking it up. Hold the pill for 400 ms. Passes when it lifts (a little larger, shadow deeper) with a firm haptic and the Edit keys chip appears above it (below it near the top edge); dragging moves it with the finger; a drop lets it settle back and stays clear of the status bar, the dock and the A–Z tab, on both sides of the tab; the chip goes 2.6 s after the drop or on a tap elsewhere · pong.
- 2026-11-15 · feat/extra-keys-bar · Home pill, saved spot. Drop it in a corner, then rotate to landscape and back, and again with a taller dock (more rows). Passes when it keeps its place by the window's fractions, is nudged clear when the dock grows, and returns to the dropped spot when the room comes back · pong.
- 2026-11-15 · feat/extra-keys-bar · Home pill, keys and holds. Tap each key (palette, app drawer, settings). Passes when each fires once; a hold on a key picks the pill up and fires nothing, repeats nothing, and does not open Home's long-press sheet or a surface popup; a hold beside the pill still opens the sheet; Edit keys opens the keys editor on Home's set and hides the chip · emulator or pong.
- 2026-11-15 · feat/extra-keys-bar · Home pill, hidden under other things. Passes when the pill is gone under the app drawer, the command palette, Home's sheet, the Appearance editor and the Layout editor, and back after each closes; a focused Home terminal float shows the Terminal keys on the bar and the pill returns when the float lets go · emulator or pong.
- 2026-11-15 · feat/extra-keys-bar · Home pill, flights between pages. Swipe Home to the Terminal, Display and Assistant pages and back, five times each way. Passes when the pill flies into the docked bar (and the bar back out to the pill) in about 280 ms as one object, with no frame showing two bars or none and no key-set morph on top; a second swipe during a flight ends it cleanly; the bar's fly-in is not cut off at an edge (an ancestor's clip would do that); with Remove animations on, the change is a jump · pong.
- 2026-11-15 · feat/extra-keys-bar · Home pill and the keyboard. On Home bring the keyboard up and down by every way (swipe, key, system IME). Passes when the pill rides the reveal into the docked place above the keyboard and back to its spot, at the pace of the swipe; a swipe that reverses tracks it; the docked pill sits where the bar sits on the Workspace (same width, row height and side inset); holds repeat and lock when docked and move the pill when floating · pong.
- 2026-11-15 · feat/extra-keys-bar · Home pill, the editor's switch. Settings › Keyboard & extra keys › Home with Home's keys off, then Show keys on Home, Save, back to Home. Passes when: before, the card reads "Home shows no keys" with only Show keys on Home (no Global wording); after Show the page lists the three starter keys with Hide keys on Home, and the Save bar lights; after Save the pill is on Home with no restart; Hide, Save drops it, and Show, Save brings the same keys back; with a hand-written Home line and the pill off, Show shows that line, not the starter keys; the other four targets read as before; light, dark, font scale 1.3 (button text stays on one line) · emulator or pong.
- 2026-11-15 · feat/extra-keys-bar · Home pill, switch off. With Home's keys off, nothing floats on Home, the dock is as before, and a page change shows the bar's own morph as before · emulator or pong.
- 2026-11-15 · fix/split-gap-slider · Inner gap drives the gap between split panes. Split a Workspace window side by side and top over bottom, Appearance › Inner gap at 0, 2, 10 and 24, under Floating and Docked, keyboard up and down. Passes when: on release the gap on screen is the slider's number (Floating: wallpaper between cards; Docked: frame glass between inserts), the shells resize once on release and not during the drag, Cancel puts the old gap back; at 0 and 2 the divider still drags from a touch on the seam (5dp either side of it), a touch just past that lands in the terminal; Margin no longer moves the gap · emulator, then pong.
- 2026-11-15 · feat/edit-pages-one-terminal-card · Edit pages shows the Terminal page as one card. With 0, 1 and 4 windows (Ctrl+Alt+C), and a Display page on the wall: open Edit pages from Home and from a terminal window. Passes when: one Terminal card stands where the Terminal page is, captioned "Terminal" (0 or 1 window) or "Terminal, 4 windows" over its top left, unclipped at font scale 1.3, dark ink on a cream wallpaper and white on a dark one; its picture is the window in front (switch to window 3 first, then reopen); no "Terminal ⋮⋮" grip, no red button on it, no seam gaps either side; opened from a terminal window the strip is focused on it. Long-press it: Set as home (when not home) and Remove Terminal only. Drag it before the first Home page, between two Home pages and past the Display card: it lands there and the wall's order follows (swipe the wall after closing); a Home card dragged onto its place lands there. Tap it: the wall opens on the window that was in front; with 0 windows (after Remove Terminal, then Add terminal) the tap lands on the Terminal page and starts a shell. Its home button makes Terminal home (the cold start lands there). TalkBack: the card reads "Open Terminal, 4 windows" and offers Move Terminal page left and right. Add terminal is offered only while there is no Terminal page · emulator or pong.
- 2026-11-13 · fix/desktop-exit-never-reported · A desktop's end is always noticed. Surface tablet: start XFCE from the drawer, stop the display from the pane corner (long-press, power), tap XFCE again. Passes when: XFCE starts again instead of doing nothing; then logout from XFCE: openbox comes back and XFCE can start again; `$PREFIX/tmp/termux-x11-session.log` exists and holds the desktop's output; start a second desktop and answer yes to the take-over: the first stops, the second starts; a stale `$PREFIX/tmp/termux-x11-session.pid` naming an unrelated pid is never killed by a take-over · Surface tablet.
- 2026-11-13 · feat/phantom-limit-notice · Android stopped your Linux apps. Surface tablet (Android 12+), limit at Android's default (`adb shell device_config get activity_manager max_phantom_processes` prints null or 32): start a desktop and open panes until Android trims (logcat "Trimming phantom processes"), or end a pane's shell with `kill -9` from another pane. Passes when: the card "Android stopped your Linux apps" stands at the top of Settings, on Home's long-press sheet and on the Workspace's More sheet, light, dark, black, Material You and scheme chrome, font scale 1.3 (buttons stay on one line, Dismiss ellipsizes first); a desktop stopped by starting another one (take-over) raises nothing; Dismiss clears all three and survives a launcher restart, and the next kill raises it again. With Shizuku connected: Fix shows "Lifting the limit…", then "Your Linux apps can keep running" with Undo and Done, and the limit reads 2147483647 with the monitor false; Settings › General › Diagnostics › Process limit reads "Off · Tap to restore Android default", Restore puts both back (null) and sync back to none (`device_config get_sync_disabled_for_tests`). Without Shizuku: How to fix opens the help topic and Diagnostics opens on the Process limit row; Android 14's Developer options switch alone makes the card go on the next open · Surface tablet, by the developer.
- 2026-11-13 · feat/display-rest-rows-outside · Display rows below the display. A Display page in keyboard Overlay mode, extra keys row on and a status bar along the bottom, Docked and Floating Style, portrait and landscape (the split landscape keyboard too). Keyboard down: the extra keys and the bottom status bar stand below the display, nothing over it; keyboard up: the display keeps its size (`xdpyinfo | grep dimensions` in X, same up and down), the keyboard lies over it with the extra keys riding on top, and the bed and the extra keys read as one piece of the keyboard's glass from top to bottom, no see-through strip or frost edge where the display ends (light, dark, black, Material You, and a scheme theme). With nothing along the bottom (extra keys and status bar off or on a side) the bed still shows the live display frosted behind it. Resize mode, Terminal and Home keyboards unchanged; crossing Terminal ⇄ Display with the keyboard up lands without a jump · Surface tablet, by the developer.
- 2026-11-12 · feat/display-paste-shortcut (85c0b37fb) · Paste shortcut per app. In herdr-gui on the Display, the keyboard's paste key and the clipboard panel paste with Ctrl+V by default; set Settings › Linux apps › Apps › herdr-gui › Paste shortcut to Ctrl+Shift+V and both paste there while another app still gets Ctrl+V; Settings › Display input › Paste shortcut changes every app left on Follow; a desktop session has no per-app row. Passes when: text lands in herdr-gui from the paste key, the panel and a held-Shift paste · pong.
- 2026-11-12 · feat/display-image-clipboard (4e0ec755a) · Pictures paste into Linux apps. Copy a screenshot on Android, paste into an X app that takes images (herdr-gui, GIMP or `xclip -selection clipboard -t TARGETS -o` then `-t image/png -o > a.png`). Passes when: TARGETS lists image/png, the pasted picture matches, text paste still works afterwards, a 20 MB+ picture pastes without the display freezing for more than a moment, and the display opens with no crash on arm64 (libXlorie was rebuilt by hand; the native CI workflow has not run on it) · pong.
- 2026-11-12 · fix/home-chip-tap (fdd5d3138) · Window chip tap from Home. On Terminal 1 press the house key (lands on Home 1, Home 2 between), then tap Terminal 1's chip in the status bar. Passes when: the wall goes to Terminal 1 instead of expanding the chip; on Terminal and Display pages a tap on the selected chip still expands it · pong.
- 2026-11-12 · feat/pill-status-name (f000fa045) · Pill shows the program's name. With a program reporting working or blocked (OSC 7501 or an agent status), the window pill shows `<name> NN%` without the status word; TalkBack still reads the word. Passes when: unclipped at font scale 1.3, light and dark · pong.
- 2026-11-12 · debug/display-gap fix (a6a175a3f) · Display holds its size until the stand-in covers it. Record (`screenrecord`) three Display drags out and three house-key jumps, plus a touch-down on the Display page. Passes when: no frame shows bare wallpaper where the pane was · pong.
- 2026-11-12 · Display typing · Gboard on the Display: typing a word in herdr-gui shows no cursor stepping back and retyping as a letter is corrected (LorieView.replaceText retypes the word; not fixed) · pong, by the developer.
- 2026-11-12 · Home weather · After a long lock, unlocking onto Home shows no "1…" flash in the weather row before it fills · pong, by the developer (needs a recording when it recurs).

- 2026-11-12 · feat/display-app-launch-env · Per-app Scale and Graphics reach the app. Passes when: a GTK app (e.g. mousepad) and a Qt app (e.g. qterminal) opened at Scale 1.5 and 2 draw their text and widgets that much larger than at Follow, Follow draws as before, a desktop session has no Scale or Graphics rows; an app set to a GPU profile (Linux apps › Apps › the app › Graphics) logs that profile on launch while another app follows the display pick; an app in a distro (proot) container honours the same Scale. Pong for the GPU and distro parts; emulator for the rest.
- 2026-11-12 · feat/display-apps-menu · Per-app Linux app settings. Settings › Linux apps › Apps lists every installed Linux app with its icon; an app with any change shows "Custom settings". On an app's page set Touch mode to Direct, open that app on the display: touch is direct while its window is in front, and switching to another app's window (or closing it) goes back to the global mode; changing the global mode still reaches apps left on Follow. Graphics "Draw in software" and Show in app drawer "Hide" keep the choices made on the old Graphics acceleration list and Hidden apps page (set them on the base build first). Passes when: all of that holds, light and dark, font scale 1.3 (summaries wrap, no clip), and a settings reopen after leaving an app page lands on the Apps list · emulator, then pong.
- 2026-11-12 · feat/terminal-notifications · Terminal events as Android notifications. Passes when: with the launcher off screen a bell, OSC 9, OSC 99 and (Fast engine) OSC 7501 blocked each post; with the launcher visible on Home, the Assistant, the Display or a focused Home float they post; with the Terminal page showing nothing posts for any pane (in-app notice and pill only); a window has one notification that updates in place and moves to a higher kind's channel; bringing the pane to front or the Terminal page to its window, blocked to working and closing the session each clear it; the lock screen shows only the kind, no message; a reply to Needs input or Bell types the text and Return when the same program still waits, and otherwise opens that pane with "Not sent" and a tap copies the text; on Android 12+ a reply needs the phone unlocked; Android's settings list six channels in one Terminal group, and the two older ones keep the user's settings under their new names; Terminal settings > Notifications opens the app's notification settings; both engines (Standard posts everything but OSC 7501); with POST_NOTIFICATIONS denied only in-app notices show · pong.
- 2026-11-12 · fix/display-still-handoff · Display leaves without a bare frame. With X running on a Display page, Depth transition, record (`screenrecord`, 120 Hz where the device has it) three Display → Assistant drags and three Display → Home house-key jumps, and step the frames. Passes when: no frame shows bare wallpaper where the pane was (the pane holds still a frame or two longer, then its still travels); landing back on the Display page shows no bare frame either; with the motion log on, `page-pose` lines for the DISPLAY page show `still=false` then `still=true` within about 160 ms of the drag's start · pong.
- 2026-11-12 · feat/ghostty-parity · Fast engine, clipboard read. In a Fast pane (DA1 `?62;4;22;52c`) run `scripts/terminal-probe/ghostty-parity-probe.sh` and a one-line OSC 52 read. Passes when: the sheet names the foreground program and shows only with that terminal on screen (raise the request, go Home, come back: it appears then); Deny, Allow once, Allow for this session and Always allow each do what they say across a new pane and an app restart; Reset clipboard permissions makes the next read ask again; a second request while the sheet is open adds no second sheet · pong.
- 2026-11-12 · feat/ghostty-parity · Fast engine, OSC 7501. Emit working with progress, blocked with a message, done, error, clear, and two ids at once. Passes when: the pill shows `<name> <word> [NN%]` unclipped for each and returns to the title on clear; with the launcher on Home a notice shows for needs input, done and error, and a tap on it lands on that pane at rest (the emulator caught only mid-slide frames); typing into the pane clears done; `exit` in the shell drops a working record (never seen on a device) · pong.
- 2026-11-12 · feat/ghostty-parity · Fast engine, library key encoders over adb. With a byte-echo script (`cat -v`, then kitty flags pushed) tap every key of the extra-keys row including sticky Ctrl, Alt, Shift and Fn combinations, by coordinates. Passes when: each tap gives the "library" bytes listed in `project-docs/reference/terminal/ghostty-parity-input-differences-2026-11-11.md`, and with `debug.tl.terminal_input=java` set before the pane opens, the "Java" bytes · emulator (taps were never driven there), then pong.
- **GPU probe runs in :tai_runtime (11-11, fix/tai-gpu-probe-runtime).** Pass: after a fresh
  install or update, opening Settings › Model Centre starts `:tai_runtime` (`ps -A | grep tai_runtime`),
  `files/tai/gpu-probe.json` appears with `"build"` = this build and `"facts"` (no `"inflight"`),
  the launcher process never logs `TaiGpuProbe`, pong (Adreno) preselects the GPU again, and
  "Check the GPU" shows only on a phone whose probed path is unknown (emulator: swiftshader/OTHER).
  Emulator, then pong.
- **Choose a key opens on its grid (11-11, fix/keyboard-settings).** Pong reported the sheet
  empty below the search field with Gboard up at once. Pass: Settings › Keyboard › Terminal extra
  keys › + opens with chips, key grid and Cancel / confirm visible and the keyboard down; tapping
  search raises Gboard and the grid and buttons stay above it; light, dark, font 1.5. Emulator
  (Gboard) confirmed 11-11; pong is owed because the keyboard never auto-opened on the emulator.
- **Keyboard & extra keys section (11-11, fix/keyboard-settings).** Pass: Settings › Keyboard shows
  Terminal extra keys and Uppercase key labels under "Keyboard & extra keys", no separate Terminal
  extra keys section or filter chip, both rows still work with Android chosen as the keyboard, and
  the Uppercase setting kept its value across the update. Pong.
- **A Display page with no display takes plain swipes (11-10 a1fac567a; 11-11 fix/display-idle-swipe).**
  FAILED on pong 11-11: the page could not be left by a plain swipe (every Display page's border
  zone owned its content, which refused the body swipe; it now owns it only while a display
  runs). Emulator 11-11: idle page pages back by a plain swipe; Start display, the swipe goes to
  X; server stopped, the plain swipe pages again. Owed on pong: the same three, keyboard up and
  down. Arriving started the display on pong because "Start with the launcher" (Display ›
  Startup, also set by Start in = Display) also started it on every landing; since 11-11 it starts
  on landing only until a display has run since the launcher started (unit test only). Owed on
  pong with the setting on: stop the display, leave and come back: the page shows Start.
- **Drawer keeps its icons while the catalogue reloads (11-11, fix/display-idle-swipe).** Pong
  11-11: during a category sort the drawer showed "Nothing here yet" for about a second. Owed on
  pong: Settings › Dock › Drawer › Category sorting, run a sort, open the drawer as it finishes
  (and after): the previous icons, never the empty message; a category with no apps still shows
  it once loaded. Unit-tested only (AppDrawerContentCategoriesTest).
- **Live dock reorder and drawer-to-dock pins (11-10, #155; reworked 11-11, fix/dock-carry-feel).**
  Pong 11-11 found the part slow, the pick-up sideways-only, the edge turn quick, no second-finger
  turn and a drawer pick-up that landed nowhere. Reworked: 200 ms parting (an icon's middle keeps
  the 300 ms folder rest), a lift by any slide past 16 dp, a 700 ms edge hold, a second-finger page
  turn, and a drawer pick-up handed to Home at once. Emulator emu2 11-11, Docked, keyboard down,
  injected input: all five seen working (a diagonal lift, the row parted within half a second, no
  edge turn at 0.4 s and a turn by 1 s, a two-finger turn to page 2, the drawer closed on pick-up
  and the app pinned on the row and placed on Home). Owed · emulator: the same on Floating and with
  the in-app keyboard up; a cancel leaves no icon offset, faded or enlarged. Back during a carry
  does not cancel it (it never did).
- **Card looks survive a relaunch and a kill mid-edit (11-10, fix/card-looks-session).** Unit tests
  only. Owed · emulator: in the Appearance editor take a card on Custom to a Look, change the theme
  (the editor relaunches), then Undo: the card is back on its Custom with the values from before
  the session. Same start, then `am force-stop` before Done and a cold start: same result.
- **One resolver for what the stored wallpaper plays (11-10, refactor/scene-source).** A refactor
  with no intended change: `SceneSource` now feeds both the running scene and the still. Unit tests
  pin the file and key rules; nothing drawn was looked at. Owed · emulator or pong: with a video
  stored, tap another rate pill (the scene keeps playing and switches to that version once made);
  Use as wallpaper with a video and with a shader (the still on Home and Lock is the frame of what
  is playing); cold start with each stored (the scene comes up); `debug.tl.shader_file` and
  `debug.tl.video_file` still override and clear.
- **GIF import converts to video (11-09, perf/gif-wallpaper).** Seen on pong 11-09 with the
  developer's 32 MB 800x1412 5 s GIF: the stored file is a 3.2 MB HEVC .mp4, the GIF is gone, the
  hint reads "GIF converted to video · 800×1412 · 5 s · 3 MB · hardware decoded", it plays through
  the Qualcomm decoder with 0 dropped frames, and the launcher fell from about 50-65% of one core
  (scene thread 42%) to 44% (scene thread 17%, RenderThread 18%, main 8%); the rest is the fixed
  30 Hz scene loop plus the 15 Hz glass pass. Still owed · pong: the conversion time by the clock
  (the log puts it under 15 s); the loop seam (end to start) has no hitch, flash or black frame;
  it is cover-cropped like a video. Emulator: a one-frame GIF stays a still; `debug.tl.video_file`
  on a .gif runs the same conversion; fallback (force a failure): the GIF still plays, its hint
  says "decodes on the CPU", and its frames still update with the hardware canvas (a frozen frame
  means updateTexImage ran before the render thread posted the buffer).

- **Terminal engine: Fast (libghostty-vt) (11-09, merged from exp/ghostty-wave3).** Verified on
  pong 11-09 on both engines: parse and frame benchmarks, the protocol sheet, emoji widths,
  XTVERSION/DA1/mode 2027, kitty images (PNG, animated GIF, tmux placeholders), sixel at the top and
  bottom of a pane, mode 2026 hold, trim with an image on screen, links in scrollback, prompt jumps,
  OSC 99 notifications, the Terminal engine row in light and dark, the crash guard (force-stop keeps
  Fast on, a native crash turns it off, an update turns it back on), an idle Fast pane (6 frames in
  30 s). Sixel against a real Standard baseline (11-09, the setting on Standard, not the property):
  top, bottom, taller than the pane, cut at the right edge, `clear` and `\e[2J` all match; DA1 is
  `?62;4;22` on Fast and `?64;1;2;4;6;9;15;18;21;22` on Standard. Fast draws a sixel taller than the
  pane up into the pane's top padding by a few pixels where Standard stops at the first row
  (cosmetic). Still owed · drop build: Settings › Terminal › Clipboard & compatibility shows
  Terminal engine (the library is in the APK).

- **Pick previews, Done keeps (11-09, round 4).** Emulator-checked on the Shader page: tile tap
  previews live, Back reverts, Done writes the stills; pencil opens the editor; Apply in the title row.
  Owed · pong: pick a shader tile and Back (the previous wallpaper is back, no stills written); pick
  and Done, then Back to photo restores the photo; an example tap makes a copy that Back removes and
  Done keeps (two examples then Done: only the last copy stays); edit the in-use shader, Apply, Back
  (choice reverts, source kept); over a photo import a video then Back (photo back); over a video
  import a video then Back (the new one stays, stills written); change the rate pill then Back (rate
  restored); the pencil's touch target and look on bright and dark thumbnails at light/dark/font 1.3;
  rotate while previewing (the preview reverts: known).

- **Frame-rate versions of the stored video (11-09, round 3).** Emulator-checked: a 60 fps clip
  imported at 30 stores original.mp4 + v60.mp4 (copy) + v30.mp4; tapping 15 shows "Preparing 15 fps
  version… NN%" with Choose and Back to photo greyed and the pills live, the layer plays the source
  meanwhile and swaps to v15.mp4 when done, and the source is pruned once 15/30/60 all exist; a
  24 fps clip greys 60 with "Clip is 24 fps" and clamps a stored 60 to 30; a 20 fps GIF greys 60,
  encodes 15 and 30 and drops the .gif after both. Owed · pong: import the 1080p60 clip at 30
  (encode time by the clock, launcher CPU with the 30 fps file vs the 16% codec share measured
  with the 60 fps source), tap 15 (codec share near 4%), the 15 and 30 versions look right (no
  stutter from the frame drop, rotation kept on a rotated clip), `v<rate>.mp4` sizes; the GIF
  flow on the 32 MB GIF (15 and 30 fps versions, time each); kill the app mid-encode and reopen
  the Video page (the preparation resumes, no .tmp left); a still (one-frame) GIF stays a still
  with no bar; light/dark and font 1.3 for the rate note and the longer status line.

- **Scene cold start (11-09, fix/scene-first-frame).** On the CPU-rendered emulator a cold start with a
  shader or video stored sometimes ANR'd ("waited 5001 ms for FocusEvent"): the window's first layout
  waited in `SurfaceSyncGroup` for the scene SurfaceView's first buffer, which came only after the EGL
  setup and the shader compile or player prepare. The surface now posts one cleared buffer (the system
  wallpaper's primary colour, else black) right after the EGL context exists. Emulator after the fix:
  video 4/4 clean starts, shader 2/3 (the one ANR was a render-thread wait with the host at load 35).
  On pong: ten cold starts each with a shader and a video stored (`am force-stop com.termux`, Home),
  no ANR, the first visible frame is a flat colour at most for a blink, then the scene · pong.
- **Shader and video wallpapers (11-09, feat/gl-wallpaper, spec shader-video-wallpapers-2026-11-09).**
  Emulator (x86_64 debug, emu2, `debug.tl.shader_guard=off` because CPU GL trips the heavy-frame
  guard) confirmed: a pasted Shadertoy plasma and a ShaderEditor `void main` gradient build and
  move; the touch spot follows the finger; status-bar and dock glass show the blurred scene; a
  broken file logs `Line N:` at the user's line and keeps the last good program; the debug file
  reloads within 1 s; `setprop debug.tl.shader_file ""` removes the layer; Photo | Video | Shader
  row; New shader → Paste → Apply sets the still on Home+Lock and recolours the look; the list is
  a 3-column grid with the active tile bordered; editing the active shader rebuilds the layer;
  Video → Choose video or GIF → system picker imports an H.264 clip that plays on Home; GIF and
  video are cover-cropped; a clip with rotation metadata stands upright; Back to photo puts the
  photo back and the picker reopens on Photo. The stored shader survives a reinstall.
  Debug routes: `debug.tl.shader_file=<path>`, `debug.tl.video_file=<path>` (files must sit
  where the app can read them: push to /data/local/tmp, chmod 644, `run-as` copy into
  files/home); `debug.tl.shader_guard=off`.
  Owed on pong (with the developer's go): power and heat, 20 min each at Half/30, for a light
  shader (gradient), the plasma and a raymarcher, one HEVC 1080p30 clip and one GIF, against the
  11-05 baseline (no scene 1.27 W, Aurora 1.2 W, Particles 2.0 W); frame time via
  `dumpsys gfxinfo`; a clip with rotation metadata (the emulator's decoder does not rotate, pong's
  may, so a double turn is the thing to look for); Back to photo restores the real previous
  photo (the emulator had none to remember: READ_EXTERNAL_STORAGE denied on a fresh install);
  screen off/on and the drawer over the scene resume where they stopped; battery saver holds the
  frame; the heavy-frame guard steps a heavy shader Half → Quarter → hold with "Too heavy at this
  quality" on the editor page; the pending marker reverts to Photo after a forced kill during the
  first 3 s of a new shader; `dumpsys meminfo` graphics flat over 5 min for a shader and a video;
  `dumpsys media.player` shows one player; Import file, Texture, Delete (incl. the shader in use)
  flows; light/dark and font scale 1.3 on the three pages; terminal-app mode "Also set as phone
  wallpaper" · pong.
- **Live blur plans once per swipe (11-08, perf/glass-translate).** Live blur and Glass Look on,
  a terminal printing (`yes | pv -qL 20` in a pane), a Home float and a built-in widget on Home2,
  the in-app keyboard up once and down once. Swipe slowly Home2 ↔ Workspace ↔ AI. Passes: the
  glass under the status bar, the dock, the panes, the float, the widgets and the keyboard
  tracks the wallpaper and the pages under it on every frame (no stale strip, no frozen picture);
  the terminal keeps scrolling under the glass mid-swipe; the landed look is the same as before
  this change. Also: open the drawer mid-rest, then swipe; show a Home float, then swipe at once.
  How: pong with the developer's go-ahead, `screenrecord` read frame by frame; a perfetto trace
  shows `LiveBackdrop.plan` only on a swipe's first frame and after a layout pass.
- **CRT screen events (#225).** Owed on pong, a pane on the CRT effect, Screen events on:
  backspace at column 0 while typing tears the pane each time (a held backspace tears about every
  150 ms, never a stutter); a keybinding that moves focus between two panes wobbles the new one;
  a split (keys and the pane-agent API) plays the bright-line power-on after the divider sweep,
  not under it; closing a pane (titlebar ✕, `pane.close`) collapses it to a line before it goes,
  in a split and as the last pane of a window, and on a Home float; `kill -9 $$` in a pane shows
  snow before it goes (the kept "[Process completed]" pane on an outside kill snows and stays);
  closing the current session powers off its panes, then the session slides away as before.
  Battery and performance > Idle glitch rate shows only with Idle animations on and the CRT
  effect; Often tears an idle pane every 10–30 s, and typing still pauses it.
- **Keyboard rises in its landing inks (11-08, fix/crossing-look).** Home2 → Workspace with the
  in-app keyboard rising, then Workspace → Home2; keyboard up and down at the start; Docked and
  Floating dock. Passes: the rising keyboard's function chips and legends are already the
  Workspace's mid-swipe, with no change at landing; the strip under the navigation bar looks the
  same mid-swipe as at rest at either end. 11-08 (fix/rising-keyboard-inks): Glass Look and Live
  blur on, light wallpaper: the toolbar row above the keys (keyboard, mouse, home, >_, display,
  grid, list) rises with its icons already crisp and the >_ / display glyphs already saturated,
  no brightening at landing; the special keys (shift, backspace, Ctrl/123+, Fn/Alt, Enter's well)
  do not darken at landing. 11-08 (fix/rising-fn-keys): the crossing's first frame judged the
  keyboard on the page it left (band below the screen, a stand-in read, chips at 0xCA, 0xE1 at
  landing); it now judges again when a layout moves the band. Same swipe in a 30 fps recording:
  the special keys' fill is the landed one on every frame they show, with no step at landing
  and none mid-rise.
  How: pong with the developer's go-ahead, `screenrecord` read frame by frame.
- **No keyboard under the pill after landing (11-08, fix/keyboard-under-dock).** Dock design
  Peek glass, Live blur and Fancier glass on, gesture navigation; in-app keyboard up on the
  Workspace, down on Home2. Workspace → Home2, Docked and Floating, and back. Passes: once the
  keyboard has slid down, no keycap (the number row) shows at the screen's bottom under the
  gesture pill on any frame, during the dock's rise or in the ~0.5 s before the wall settles; the
  band under the pill shows the dock's glass. Back to the Workspace, the keyboard rises in above
  the pill band, not out of it. How: pong with the developer's go-ahead, `screenrecord` at 30 fps
  read frame by frame around the landing.
- **Peek reflection never sharp (11-08, fix/peek-band).** Dock design Peek glass, Reflection
  on, Fancier glass on; Workspace (keyboard up) → Home2 and back, Docked and Floating. Passes: from
  the landing on, the icons' reflection under the A–Z letters is soft and faint on every frame,
  with no sharp, bright copies in the ~0.5 s before the wall settles, and nothing changes in the
  band at the settle. Also look at the dock's glass edge over that half second: the card's rim
  should already be where it rests. How: pong with the developer's go-ahead, `screenrecord` read
  frame by frame around the landing.
- **Backdrop layers, no pop (11-07, perf/glass-slide-on-swipe).** Look Custom (Opacity 12, Blur 20,
  Edge 40, Depth 90), Live blur on, glass widgets on Home, a float over them. Screenshots at rest,
  on the first moving frame, mid-swipe and after landing, Home ↔ Workspace both ways. Passes: the
  first and the landed frame match the resting screenshot in every glass; mid-swipe glass shows
  the same wallpaper, widgets and pages, moving. Ship gate. How: pong with the developer's
  go-ahead, `screenrecord` read frame by frame.
- **Backdrop layers, soft edges and glass on glass (11-07, same branch).** Side by side, resting
  against mid-swipe: widget edges under the float's glass and text edges under the dock's glass
  look equally soft; two stacked floats over Home show the float behind and Home through both
  while swiping. Passes: no difference visible at arm's length. How: pong, screenshots.
- **Backdrop layers, scene and transitions (11-07, same branch).** A live scene on, then each page
  transition (Slide, Slide with plank tilt, Depth, Fade, Carousel, Stack) and the sink. Passes:
  the scene keeps moving under the glass at its glass rate during the swipe; no blank or dark
  glass in any style; keyboard up and down, Docked and Floating dock. How: pong, `screenrecord`;
  a perfetto trace counts `Glass.layers.fallback` per style.
- **See through (11-07, same branch).** Battery & performance → See through: One layer, then All
  layers. Passes: with One layer the front of two stacked floats shows the back float (titlebar
  and text) frosted over the wallpaper only, with no widgets or pages through both, at rest and
  swiping; a single float over widgets still shows the widgets; All layers brings the widgets
  back through both. How: emulator or pong.
- **Backdrop layers, the cost (11-07, same branch).** In one session: Custom look on the base
  5169acb0f and on this branch, then Solid, 10 Home ↔ Workspace round trips each. Passes:
  RenderThread "flush layers" per swipe frame near Solid's 1.4 ms (was 4.1); swipe p90 within a
  few ms of Solid's; `Glass.layers.local` about once per swipe per surface, `Glass.layers.fallback`
  rare. How: pong with the developer's go-ahead, perfetto.

- **Live glass during a page swipe, the look (11-07, perf/glass-slide-on-swipe).** Look Clear,
  Live blur on, a float over Home's glass widgets, the dock showing pages under it. Swipe Home ↔
  Workspace slowly and fast, scene off and on. Passes: the float's glass still shows the widgets'
  glass under it all the way across; the dock's glass shows both pages sliding under it, blurred,
  with the scene still moving under it; nothing pops or jumps in the glass on the first frame of
  the swipe or as the wall settles (a terminal printing or the clock ticking under the glass may
  hold still for the quarter second and catch up at settle); a slow drag that reverses past rest
  shows the other neighbour, not an empty strip; keyboard up on the Workspace (the dock lifts as
  it crosses). How: pong with the developer's go-ahead, `screenrecord` of five swipes each way.
- **Live glass during a page swipe, the cost (11-07, perf/glass-slide-on-swipe).** Look Clear
  against Look Solid, 10 Home ↔ Workspace round trips each, swipe frames from `gfxinfo
  framestats`, scene off and on. Passes: Clear within about 1–2 ms of Solid at the median of
  total and of ISSUE_DRAW_COMMANDS_START → FRAME_COMPLETED (before this change Clear 23 / p90
  43 ms total, 16.4 ms issue → completed; Solid 16.7 / 22, 12.1); no frame at the swipe's start
  worse than before (the page layer's capture runs there); at rest on Home with a scene, the
  frame count per 30 s no worse than before; PSS within noise. How: pong with the developer's
  go-ahead, the same scripted round trips as the measurement that motivated it, a perfetto trace
  for one round trip (`LiveBackdrop.pageLayer` sections show the captures, at most three per
  swipe and usually one).

- **Float titlebar wears Live glass.** Owed on pong, Live blur on: a floating window's titlebar
  shows what is beneath the float (the page, the terminal behind, another float's glass), not only
  the wallpaper; drag it over a second float and the strip shows that float through it; resize
  and maximise keep it aligned with the body; Clear and another Look; with Live blur off the strip
  is the stored frame as before; minimise and bring back; with nine or more Live surfaces open the
  strip still follows its float (it takes no slot of its own).
- **Drawer category cards stay glass in a dark theme over a bright wallpaper.** Owed on pong:
  system dark theme, bright wallpaper, open the app drawer's Categories view. Each category
  container must show the drawer's backdrop through it (a faint lift, not a near-black slab;
  the old build sampled about #1A1712 inside a card). Press a card: it must still look pressed.
  Repeat in light theme: the cards must still be findable against the frost. Pinned folder shells
  in the dock and the drawer's search rows use the same wash; look at them too.

- **One status bar and dock look for every page (11-08, perf/shared-chrome, wave 2a).** Owed on
  pong, on an install whose Home and Workspace differed in status bar edge, compact or expanded
  state, dock design, Search field or Drawer icon: after the update every page (Home, Workspace,
  Assistant, Display) wears what Home wore, portrait and landscape, keyboard up and down,
  Docked and Floating. Change the status bar's edge, its compact rest, the dock design, the
  Search field or the Drawer icon from the Workspace, then from Home: the other page shows the
  same at once, and Discard puts it back. The dock's height is the same on every page.
  Landscape Home keeps its side-bar dock. Swipe Home2 <-> Workspace 5 times: the status bar and
  dock do not re-dress or move at the landing. Trace: no `Settle.dockDesign` and no
  `Bar.refresh` at landing where only these values differed before · pong.
- **One Margin for every page (11-08, perf/bar-travel).** Owed on pong, on an install whose
  Home Margin differed from the Workspace's: after the update every page (Home, Workspace,
  Assistant, Display) wears the Margin Home wore, on the status bar, the dock and the panes,
  Docked and Floating, keyboard up and down, one pane and split. The Appearance editor shows
  Margin only on Customization (not on Home, Workspace, Dock, Status bar, Terminal or Keyboard);
  moving it, with Where on Everywhere or on a page, moves every page alike, and Discard puts it
  back. Swipe Home2 <-> Workspace 5 times: the status bar keeps its inset through the slide and
  the landing, and the window chips and the Wall strip are the landed page's. Trace: no
  `Bar.refresh` under `Touch.dispatch` on a drag, no `Wall.halfRedress` where only the Margins
  differed before · pong.
- **Toolbar text input built when idle (11-08, perf/bar-travel).** Owed on pong: force-stop,
  let the launcher come back on Home, wait 3 s, then swipe Home2 -> Workspace with the extra
  keys on the dock. Trace: one `Toolbar.prepareTextInput` slice about 1-2 s after the first frame,
  outside any frame's traversal; the first swipe has no `inflate` of `TextInputEditText` under
  `measure` and no frame near the 74 ms one. Functional: swipe the extra keys left to the text
  input, type and send; the paste button pastes and a long press returns to the keys; rotate
  with text typed and it is still there; with the extra keys placed on another edge, they still
  show there after a cold start · pong.
- **Landing dresses the dock once (11-08, perf/landing-settle).** Owed on pong, with Home's
  layout on a different Dock design or Drawer tile from the Workspace's: Perfetto over 5
  Home <-> Workspace round trips shows one `Dock.restyle` per `Wall.settleChrome` (was two, one
  under `Place.dockDress` and one under `Settle.dockDesign`); note the `Place.hosts.*`,
  `Place.edgeStacks.walk/.metrics` and `Bar.refresh` slices for the next round. Functional, Docked
  and Floating, keyboard up and down on the Workspace: the landed dock wears the arriving page's
  design, tile and height, with no jump a frame later; a design with the Search field or Wall
  strip on one half only lands at full height; after changing a Dock design or a look setting in
  the editor, the next landing still re-dresses; a slide that comes back is unchanged.
- **Page-swipe stalls: landing layout once, frost skip, keyboard pre-measure.** Owed on pong,
  Perfetto over 5 Home <-> Workspace round trips at 90 Hz: `Wall.settleChrome` under about 15 ms
  (was ~43); `Place.syncLayout` one call per `Wall.settleChrome` (was two; count its slices
  under each `Wall.settleChrome`); `Frost.updateTopPane` mostly absent from the landing, running
  when the pane moves; `Wall.preRollKeyboard` under about 10 ms, with `Keyboard.showInternal`
  and its children (`Keyboard.ensureView`, `.setContainerVisible`, `.geometrySync`,
  `.recheckLayout`) showing what is left, and `Keyboard.prewarmMeasure` appearing after a
  landing. Functional, on the Workspace: keyboard up and down by tap and by swipe; terminal
  bounds and dock lift as before; Home unchanged; with the keyboard down, taps where it would
  stand reach the pane below; the status and window bar frost follows keyboard up/down, a pane
  resize, a split round the camera, a wallpaper change and a scheme change (Light, Dark, Black,
  Material You).
- **Camera hole, wave 4: Settings rows, migration, live outline.** Owed on pong: Settings >
  Status bar > Camera cutout, Custom: Move sideways, Move down and Size each move or size the
  gap, and while a finger is on any of the four sliders (Margin too, also in Auto) an accent
  outline is drawn round the camera's box over the page, following the value. It must go on
  release, on the Home key or recents mid-drag, on Back, and by itself 1.5 s after the last
  move; after a mid-drag Home, nothing is drawn on the page or on the home when coming back.
  Check the Settings page does not jump when the outline appears (it lets the window draw
  under the camera while it shows). Upgrade from a build with a custom centre and width: the
  gap stands where it did (margin 0, scale and Move sideways derived once); Off hides Margin.
- **Camera hole, wave 3: the pane top round the camera.** Owed on pong, status bar off and Full
  screen: one pane, then a 2-way vertical split (the camera over the seam or over one pane):
  the rim's top line breaks over the camera's box and resumes either side, the glass and the
  outline are unchanged, the first text row starts just below the box (it used to start at the
  safe inset, 126 px; now at the traced box's bottom). Floating and Docked; rotate to landscape
  and back (no break in landscape); status bar on or system bar showing: no break.
- **Camera hole, wave 2: the Floating bar splits round the camera.** Owed on pong, Full screen,
  Floating status bar: the bar's glass and rim stop either side of the camera, both cards with
  the bar's own radius (pong's hole is mid-bar, so two cards; check each end of Move sideways
  in Custom for the collapse to one card); the chips and readings still clear the gap; compact
  and expanded keep the split, the expanded bar's second row runs either side; Home and Terminal
  crossing leaves no flash at the gap and the cards ease with their outer edges; light, dark,
  black, Material You, scheme chrome; font scale 1.3; Docked and the system bar showing are
  unchanged; keyboard up and down. The frost and the rim of the second card are not yet seen
  on a device: look for a seam or a double rim. Pong's first build of this (11-07) showed the
  main card's frost still spanning the whole bar under the camera and behind the left card; the
  frost layers now take the cards' margins too. Check the main card's left end is rounded at the
  camera's right edge, no glass under the camera, and the left card ends at the camera's left
  edge (not past it); wallpaper pan and a Home/Terminal crossing move both frosts together.
- **Camera hole, wave 1 (feat/camera-hole).** Owed on pong: Full screen with the status bar on,
  the chips and readings still clear the camera (the gap now follows the cutout path's bounds,
  which may be narrower than the bounding rect: check the margin looks even each side), Floating
  and Docked; keyboard up and down; with the system bar showing nothing moves. Settings > Status
  bar > Camera cutout: Margin shows for Auto and Custom; Move sideways, Move down and Size only
  on Custom.
- **Peek reflection through the A–Z row (11-07, fix/peek-dock-travel).** Not seen on a device. On
  pong, Peek dock with the A–Z row: the icon reflections run continuous through the letters, with
  no darkened patch behind them, and the letters stay readable on a bright wallpaper and on a dark
  one (they keep their own halo).

- **Peek glass with the A–Z row (11-06, fix/peek-with-az).** Not seen on a device. On pong, Home
  with Peek glass, apps row over the A–Z row, extra keys hidden: the card rises from the screen's
  edge with its top through the icons' centres, the A–Z letters sit on it with no second slab or
  line behind them, the reflections run the card's full height behind the letters, and the letters stay legible.
- **Peek glass with the A–Z row (11-06, fix/peek-with-az).** Not seen on a device. On pong, Home
  with Peek glass, apps row over the A–Z row, extra keys hidden: the card rises from the screen's
  edge with its top through the icons' centres, the A–Z letters sit on it with no second slab or
  line behind them, the reflections fade out above the letters, and the letters stay legible.
  Check Docked and Floating, Home keyboard up and down (the card stands on the keyboard), the A–Z
  row hidden (as before), the A–Z row ordered over the apps row (falls back to the Tray slab),
  extra keys on the bottom (Tray slab), a Workspace page (Lens beads unchanged), and at the
  largest font scale. Look for a reflection cut off too short by the A–Z row's top padding.

- **Peek glass polish (11-07, fix/peek-with-az).** Not seen on a device; needs Android 13+ with
  Live glass on. On pong, Peek glass over a light and then a dark wallpaper, A–Z row under the
  icons: the mirror is clearly readable just under each icon and softens and fades all the way
  down to the card's bottom edge (past the letters, no early cut-off), seen through a faint still
  ripple (nothing moves at rest); a fine bright line (1.5 dp, over the glass's own edge) runs
  along the card's top edge and round its top corners, visible on both wallpapers, and behind the A–Z letters the reflection
  is dimmed and greyer so the letters read over a bright icon. Check the A–Z row hidden (the
  specular line only), Docked and Floating, keyboard up, Live glass off (the old still
  reflection: no ripple, line or band), light/dark/scheme chrome, and the largest font scale.

- **Wallpaper flow, round two (11-06, fix/wpflow-editor).** Seen on pong 11-06 13:22 (77 fps
  recording): no chrome over the closing menu, no card-sheet tabs under the rising Wallpaper
  sheet, the door's Done rides the sheet down with the editor, grid and scene page cross-fade,
  no duplicate frames at ramp starts, the Notifications card complete mid-swipe, and the
  recolour relaunch shows no home frame. Still owed: Home long-press › Wallpaper, the editor's
  first moving frame within two frames of the menu's last frame (the picker now loads while the
  menu slides; the slide itself must not stutter); after "Use as wallpaper" on a scene of
  another colour (Aurora from a purple scene), Done exits the whole editor as before the
  relaunch, the grid comes back at its scroll and tab, and Back instead of Done slides the sheet
  down onto the card sheet with no one-frame pop; the editor exit's last frame equals the home
  at rest (no corner drop, no pixel shift); the terminal's More › Wallpaper and Appearance
  rows wait for their sheet the same way. How: `adb -s <serial> shell screenrecord`, frames
  stepped (scratchpad `wallpaper-flow/verify`).
- **Assistant page with a real model (11-06, 41019af0b, ADR 0022).** On pong, add the Assistant
  page (Edit pages › Add assistant, or the drawer's Assistant entry), download Gemma E2B from
  the model chip's sheet, ask a question: the answer streams in plain text, Stop turns it into
  "Stopped" with its text kept, Copy/Share/Try again sit under a finished answer, a fenced code
  block scrolls sideways with its own Copy while a sideways swipe elsewhere pages the wall. A
  question sent before the model was ready ("Waiting for the model") is answered once it loads.
  Leave the page mid-answer and come back: the text has kept growing. Stop must not cut off a
  local-API answer started elsewhere (stop() uses the runtime-wide cancel). Watchdog on
  MemAvailable while loading (pong memory rule). How: pong, `ad -s pong`, with the go-ahead.
- **Assistant page dictation (11-06, 41019af0b).** The composer's mic: a Listening row with
  levels, phrases appear as each pause closes, Done puts them in the field; first use asks for
  the microphone. How: pong (the emulator has no microphone).
- **Assistant page theme and font (11-06, 41019af0b).** Switching dark/light while the launcher
  runs re-themes the page and its model sheet without a relaunch and stays on the Assistant page
  (emulator: it kept the old theme and dropped to the Terminal page). At font scale 1.5 the
  no-model notice's Download button doesn't wrap (emulator: "Downloa/d"). The "Assistant page
  added" snackbar doesn't cover the composer. How: emulator or pong.

- **Voice models in the Benchmark (11-05, #223).** On-device AI › Benchmark: a Text | Voice
  choice heads Home; Voice shows the voice board (or "No results yet"), Run a benchmark opens
  Choose listing the installed speech models (one line on top, no Quick/Standard), Continue →
  Check sheet → Run. Each model runs load, warm-up, clips on the CPU, then the GPU where the
  Speech accelerator row offers it; the dial reads "N× real time"; the whole run on pong stays
  under about five minutes plus cool-downs. Rows read "4.2× real time · 3% word errors · loads in
  0.9 s"; a GPU that fell back says "GPU unavailable, ran on CPU" and sits under Not ranked. A
  result's Use this sets Keyboard › Voice input › Speech model and its accelerator row to that
  model and processor, and a dictation afterwards works. The Text board and the Model centre
  speed pills are unchanged · pong or emulator.

- **Dock tiles lift on a held press (11-04).** On the Tray and Strip dock designs, with the
  Drawer, Command palette and New float terminal tiles all on: a finger held still on each
  tile for about half a second lifts it (haptic tick, the tile grows) with no Edit/Hide card
  opening, and dragging along the row and letting go drops it on the nearest column, swapping
  with a tile it lands on; the order survives a restart and the Dock card's Reset puts the
  palette and float tiles back last. A hold on the dock's empty frame still opens the card, and
  a quick sideways swipe starting on a tile still pages the row · pong or emulator.
- **MNN 3.6.2 runtime (11-04, #211).** On pong: the MNN chat models already installed load and
  answer (CPU and OpenCL), an embedding model and a diffusion model still run, and
  `taobao-mnn/MobileMoE-S-SFT-MNN` (716 MB, uses IndexedLinear) imports, loads and answers.
  Optional: `LFM2.5-8B-A1B-MNN` (4.8 GB) · pong.
- **AI runtimes updated (11-04).** A LiteRT-LM chat model loads and answers on GPU and on CPU
  (and the Benchmark route still returns numbers); voice typing's speech detection still starts
  and stops on speech · pong.
- **Keyboard frosts the display (11-04).** A Display page with an X app that moves, keyboard
  up, in overlay and in resize mode, Docked and Floating Style, Docked with the frame joined:
  in overlay mode the bed shows the live display frosted behind it, keys sharp, at the Keyboard
  card's Display-pages Opacity and Tint; no wallpaper picture in the bed, no frost outside its
  rounded outline, and the strip under the pill matches the bed. In resize mode the bed wears
  the ordinary wallpaper glass with the Display-pages look values, no frost layer
  (`dumpsys SurfaceFlinger --list` shows no 'Keyboard frost'). Frost 0 is see-through, Frost
  high is clearly frosted. Battery saver on (and Settings › Accessibility window blur off):
  see-through, same Opacity and Tint, and the frost returns when it goes off with the keyboard
  still up. Rotate with it up. Open and close the keyboard twenty times, leave the page and come
  back, HOME and back: no stuck blur band with the keyboard down, `dumpsys SurfaceFlinger
  --list | grep -c 'Keyboard frost'` at most 1. Typing lands in X; display taps above the
  keyboard still reach X. Overlay-mode sizing and the bed's material with rows along the
  bottom: see the 11-13 item. Resize mode: the display still ends above the keyboard. Crossing Terminal ⇄ Display with the keyboard up lands without a jump. With the
  Appearance editor open over a Display page the frost sits exactly under the scaled keyboard in
  the preview (no patch elsewhere), follows it through peek/half/full, and stays right on Done. Terminal and Home keyboards unchanged (Terminal overlay solid) ·
  pong (API 34+, blur supported), plus an API 30 emulator for the see-through fallback.
- **No hitch at a look-only landing (11-04).** Terminal page's Dock Opacity detached to 20,
  Home's at 90, nothing else apart: swipe Home 2 to the Workspace and back, slow and flung, with
  a perfetto trace. The landing frame holds no `Wall.halfRedress` section (it runs in the frame
  after, as a shape-apart landing's does) and draws under 16 ms; nothing visible hitches as the
  wall lands. Docked and Floating, keyboard up and down · pong.
- **Dock opacity blends across a crossing (11-04).** Terminal page's Dock Opacity detached to 20,
  Home's at 90, same Style, Corners and Margin: swipe Home 2 to the Workspace and back, slow and
  reversed. The dock stays on screen (no dip) and its opacity eases with the finger; the landing
  frame changes nothing in the dock. Again with the dock's Tint and Grain apart: both ease too.
  Known gaps, still changing at the landing: the dock's Blur radius, the keyboard bed's own
  look, the under-pill strip and the status bar's look. Docked (also on a joined frame, where
  the dock's sheet stands down and nothing blends) and Floating, keyboard up and down, Live blur
  on and off · pong.
- **Terminal pane stays sharp after a crossing (11-04).** Home Margin 16, Workspace Margin 4,
  and the dock's opacity apart on Home: swipe Home 2 to the Workspace with a session showing
  text. Record the screen: no frame after the landing shows the pane's text blurred (it was ~4
  frames). Live blur on and off, Docked and Floating, keyboard up and down · pong.
- **Crossings turn by the Page transition (11-04).** Page transition Carousel, then Depth, Fade,
  Stack and Slide: swipe Home 2 to the Workspace and back, slow, flung and reversed mid-way. The
  two pages move as Home 1 and Home 2 do under the same setting (Slide: flat); the status bar,
  dock and keyboard stay flat on screen and ease with the finger; a bar only one end wears fades
  in place under the turning ones; the terminal pane's glass and Live blur (on and off) turn
  with their page; a Display page in front still slides. Docked and Floating dock, keyboard up
  and down · pong.
- **Home page arrives live (11-04).** Home 1 (home), Home 2, Terminal: on Home 1 swipe to Home 2,
  go to the Workspace, keyboard up, then the extra keys' Home key, and again with the phone's
  home press: Home 1 slides in with its widgets and status bar from the first frame, never the
  "Home 1" card. On Home 1 at rest, wait 10 s, then swipe right toward the Workspace: the wall
  leaves from Home 1, not Home 2. A float chip for a float on Home 2 lands on Home 2 · pong.
- **Bar across a Margin apart (11-04).** Home Margin 16, Workspace Margin 4, both with the bar on
  top: swipe Workspace -> Home 2 and Home 2 -> Workspace, slow and flung. The bar stays on screen
  the whole way (the window chips pan out, Home's content pans in), its width easing to the
  arriving page's Margin with the finger, and nothing about it changes on the landing frame; a
  reversal mid-way eases back; no bare handle at the top mid-swipe. Docked and Floating, keyboard
  up and down, Live blur on and off (the bar's glass stays sharp at its edges while it eases).
  Only the glass stretches: the clock, chips and readings keep their size mid-swipe (no wide or
  narrow text), the start ones moving with the left edge and the end ones with the right; on a
  Floating card the rounded corners follow the eased width both ways (wider and narrower).
  Same Margins: as before · pong.
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
  The captions take dark ink on a light (cream) wallpaper and white
  on a dark one, in light and dark theme, Material You on and off.
  (3) Rename: the keyboard is up as the dialog opens, the field focused. Emulator or pong.
- **Remove and bring back the Terminal page (11-04, #202, #200).** (1) Two sessions, three
  windows in all: long-press the Terminal card › Remove Terminal asks "Close 3 windows and remove
  Terminal?"; Cancel keeps everything; Close stops the shells, the Terminal card goes, no undo
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
- **Volume keys on the Display page (11-05).** Display page in front with a display running:
  volume up and down move the phone's volume and show the system panel; same with no display
  running; a physical keyboard still types into X (that path now runs through the X11 input
  handler, which also releases stuck modifiers) · pong.
- **Home Wallpaper dim (11-05).** Home tab > Appearance > Wallpaper dim (after Depth): drag 0 →
  60 with Home behind the editor: the wallpaper darkens live, stays after Done, Cancel restores.
  With two Home pages, one drag darkens both; the Terminal page keeps its own dim. Add a third
  Home page afterwards: it is as dark as the others · pong.
- **Display page after its display stops (11-05).** Display page in front, kill or stop the
  display: only "No display is running." / Start display / Get GUI apps, no monitor icon behind
  the text and no "Display" word on the button, not a black page in the dark theme; same in
  landscape. Swipe to Home and back with a display running, then stop it: the empty state, not a
  frozen picture. First visit after a cold start: at most a brief card, then the display. The
  text group sits on a readable glass panel over a busy wallpaper, light and dark theme · pong.
- **Key combos in the extra keys picker (11-05).** Row + , type `ctrl+f7`: the first row reads
  "Send Ctrl+F7 as keys", the cap reads Ctrl+F7; in xev on the display it arrives as Control
  press, F7, Control release. `ctrl + m` saved as a plain macro sends Ctrl+M (Enter) in the
  terminal. A category chip unchecks while a query is typed and returns when it is cleared ·
  pong.
- **Terminal extra keys guide (11-05).** Settings > Keyboard > Terminal extra keys: below Add row,
  "What a key can be" with eight facts on their own lines, at font 1.0 and 1.3; Cancel / Save
  stay pinned; in landscape Row 1 and Add row fit above the fold and the bar is one compact row ·
  pong.
- **No flash when the display closes itself (11-06).** Firefox (or any app started as the
  display's start-up app) on the Display page, quit it from inside (Ctrl+Q): the page goes from
  the app's last frame straight to the empty state on its panel, with no frame of bare wallpaper
  and no white sheet; same for Stop from the page's menu; a temporary Display page (opened from a
  Linux app) still disappears with its display · pong.
- **New Home page wears the page in front's look (11-06).** Home 1 with its own dim and glass,
  Edit pages > Add page: Home 2 looks the same from its first frame; add a page while Home 1 has
  no overrides: the new page follows Global · pong.
- **Whisper Base on the GPU (11-06, #227, b6ff781a3).** On-device AI › Speech: "Base GPU ·
  Many languages" is listed with "Runs on the GPU and uses little CPU. Many languages, 30-second
  window." and "Try it on this phone's GPU" (dimmed with "No usable GPU found on this phone"
  only where there is no OpenCL). Seen on pong 11-06 up to install and select; still owed: (1) the
  Voice benchmark runs Base GPU on the GPU and afterwards the sheet, the Model centre row and the
  Speech picker card read "Recommended" or "Slower on this phone"; (2) dictation with it on Auto
  and on GPU gives correct text, and on CPU still works (slow); (3) the accelerator dialog lists
  Auto, CPU and "GPU · uses less battery, may be slower"; (4) no crash or `stt_gpu_unavailable` in
  logcat on pong; (5) the speech card offers no language or window switch for it; the install sheet's size pill
  (Base / Small / Base GPU, size on a second line, aadab9db7) fits on pong at font 1.0 and 1.3,
  as it did on emu2; (6) put the
  developer's own speech model back as the voice-input model afterwards. Pong reference: 59 s of
  audio in ~14 s on GPU, Small.en CPU ~17 s · pong.
- CRT signal (wave 9), on pong: (1) default CRT text is no softer than before; (2) colour
  boundaries (a coloured prompt on dark, an icat image's edges) blend sideways; (3) Composite
  signal on turns a dithered icat image's checker into flat shades, and off restores it; the
  switch appears only with a CRT style, on Terminal and Performance pages; (4) status bar, dock
  and keyboard chrome look unchanged.
- **Dictation cleanup rows on Voice input.** Settings > Voice input > Dictation cleanup: "Cleanup model" reads "Automatic · <model>" and a tap lists Automatic, each installed chat model and "Remote · <model>" only when a remote is set up, with "Get more models" opening the Model centre; Raw text is never listed (a stored Raw text shows as the summary until another is picked). "Runs on" shows CPU | GPU (hidden with no GPU path and when the pick is Remote), follows the model in use, and agrees with the Tidy dictation sheet afterwards. "Level" is a Light | Polished pill that agrees with the sheet; turning "Clean up dictation" off dims all three rows · pong.
- **Keyboard glass kept across chrome applies (11-07, perf/page-settle-stalls).** With the
  in-app keyboard up on the Workspace and Fancier Glass on: swipe Home ↔ Workspace 5 times, open
  and close the keyboard, change the wallpaper, rotate. The keyboard's glass shows the wallpaper
  under it at every step, its rim and light look as before, and nothing goes blank or freezes on
  an old wallpaper. Trace: Chrome.kb.glass well under 1 ms after the first build · pong.
- **Keyboard palette built once per set of inputs (11-07, perf/page-settle-stalls).** With the
  in-app keyboard up on the Workspace: flip light/dark in Android settings, change the wallpaper
  under Material You, switch the launcher's colour scheme on and off, change the keyboard theme
  (System, Light, Dark), the Glass Look (Scheme, Obsidian) and the key opacity. After each, the
  keys and legends re-tint at once to match (no keyboard hide and show needed) and the legends
  stay readable on light and dark wallpaper. Trace over 5 Home ↔ Workspace round trips:
  `Keyboard.palette.glass` well under 1 ms where it has no `Keyboard.palette.glass.build`
  child; `.build` only on the first apply and after a faces, tone or theme change · pong.
- **Home swipe up follows the finger (11-07).** Settings > Home > Gestures, Swipe up set to the
  app drawer: on a Home page's free area the drawer rises with the finger, its top edge staying
  level with the fingertip the whole way (no running ahead, no dock hop; 11-08, fix/drawer-pull-1to1),
  and comes back down with it when the finger reverses; let go after about 100 dp of travel
  (roughly 2 cm), or flick after a short drag, and it opens, a short drag eases back (the same finger
  distances as before the fix); the first pull after a cold start (no earlier
  drawer use) is as smooth as the later ones; page swipes, widget touches, long press, double tap,
  two-finger swipes and pinch behave as before; Swipe up set to anything else (palette, none,
  lock) works exactly as before; swipe down is unchanged · pong.
- **Custom Look is kept (11-07, fix/custom-look-kept).** Appearance editor › Customization: set a
  Custom look (Opacity 12, Blur 20%, Grain 3, Tint 0, Edge 40, Depth 90, Rim Bright). (1) Tap Solid,
  then Custom: every slider is back where you left it. (2) Tap Solid, Done, open the editor again
  (it opens on Solid), tap Custom: your values are back. (3) Tap Solid, then Back through
  "Discard changes": the look is as it was before, and Custom still brings back the same values.
  (4) From a preset move one slider: the Custom chip lights, then Solid and Custom again keeps that
  slider. (5) The same on the Home, Workspace and a surface card (Status bar, Dock, Keyboard): move
  Opacity, tap Solid, then Custom: the card's own values are back, also after Done and reopen;
  Discard leaves the card as it was · pong.
- **Show wallpaper off reaches every surface (11-07, fix/wallpaper-off-chrome).** Appearance
  editor > Global > Show wallpaper off, Done: no wallpaper picture, blurred or sharp, on the
  keyboard, the extra-keys bar, the band under the keyboard, the dock, the status bar, the nav
  strip or a floating window's bar; each is plain tint like the terminal panes. Check with the
  keyboard up and down, on Home and on Workspace, in a Docked and a Floating dock, with the
  keyboard's, extra keys' and status bar's Blur detached to a non-zero value. Then Show wallpaper
  back on: the blurs return as they were · pong.
- **Travel keyboard dressed by the frame's apply, IME handed over after the landing (11-07,
  perf/page-settle-stalls).** Swipe Home → Workspace slowly with the Workspace's keyboard
  remembered up, in the Glass and Solid Looks, Docked and Floating dock: the keys rise from the
  bottom already in their glass and legends, with no flash of plain keys in the first frames.
  Swipe back half way and let go: the keyboard goes down and Home's search field still opens the
  phone's keyboard. Land on the Workspace and tap the terminal: only the in-app keyboard shows,
  never the phone's. Land on the Display place with the phone's keyboard in use there: it still
  takes the phone's keyboard. Trace over 5 round trips: no `Chrome.kb.host` under
  `Keyboard.setContainerVisible`, no `Keyboard.suppressSystemIme` inside `Wall.preRollKeyboard`
  or `Wall.settleChrome`, one in a posted message just after the landed frame · pong.
- **Top-pane frost cut with the frame's commit (11-08, perf/swipe-main-thread).** Swipe Home2 ↔
  Workspace 5 times slowly and 5 times fast, keyboard remembered up and down on the Workspace,
  Docked and Floating dock, status bar at the top: the status bar's glass and the strip behind
  the system bar show the wallpaper under them on every frame of the slide and at the landing,
  with no frame of stale or blank frost as the bar changes dress at the seam. Change the
  wallpaper and open the Appearance editor once: the bar's frost follows. Trace: no drag frame
  holds two `Frost.updateTopPane` passes; under `Wall.chromeTravel`, the new `Travel.*`,
  `Place.hosts.*` and `Settle.*` sections name the rest of each slow frame · pong.
- **A finger on the focused pane leaves the bar alone (11-08, perf/swipe-ends).** On the
  Workspace with one pane and with a split, keyboard up and down, Docked and Floating: start 5
  Home2 <-> Workspace swipes on the terminal; the terminal keeps drawing through each slide. Tap
  the pane that has focus: nothing in the bar changes. In a split, tap the other pane: it takes
  focus (cursor, border, dim) and the window chip's label follows it. With a Home float focused,
  go to the Workspace and tap a pane: the float lets go of the keyboard and the bar selects the
  window. Switch windows from a chip: the bar refreshes in full. Trace: no `Bar.refresh` under
  `View.onTouchListener#onTouch - TerminalView`; a tap on the other pane shows only
  `Bar.chipsOnly` · pong.
- **Swipe landing and Peek travel (perf/swipe-stalls).** 2026-11-08 · 96f3c94ae, 30270fe41 ·
  The place settle now runs just after the landed frame. Home2 → Workspace (keyboard remembered
  up): the keyboard is up, the extra keys live, the terminal at its keyboard-up size, the window
  chips on the bar; Workspace → Home2: the keyboard away, the A–Z bar and apps row on Home, the
  grid at its full height. Swipe back the instant a page lands, ten times each way: never a
  half-landed chrome (keyboard over Home, dock at the wrong height). A drag that springs back
  changes nothing. Reduced motion on: every swipe lands as before. Docked and Floating, keyboard
  up and down, one pane and a split. Peek glass dock: the card's reflection fades to the screen's
  edge at rest after each landing, and the strip under the pill matches it · emulator or pong.
- **A dock icon let go at the screen's edge goes back (11-08, fix/dock-drop-left-edge).** Docked
  and Floating, keyboard down, on Home2 and Home1, with two dock pages: lift a dock icon
  (long-press, slide sideways) and let go at the screen's leftmost edge, at dock height and up
  over Home; rest there first so Home's page (and the dock's, on its second page) turns, then let
  go. Each time the icon is back in its slot with no gap, or moved where the row put it; never a
  hole. Then drop one on a free Home cell (it moves there, the dock closes up), on Remove (gone,
  Undo brings it back) and back on its own slot (nothing moves) · pong.- **The Workspace is Terminal, Display and Assistant pages (11-09, feat/workspace-family,
  ADR 0025).** With an Assistant page and a Display page on the wall: the Assistant page wears
  what a terminal page wears (status bar, extra keys, dock rows as set; the window bar without
  window chips). Type in the composer with the soft keyboard up and with the in-app keyboard up:
  the composer and its send button are never under the extra keys, the dock or a keyboard. Swipe
  Terminal ↔ Assistant ↔ Display ten times: no chrome moves or redraws at landing. Swipe Home ↔
  Assistant: the same crossing as Home ↔ Terminal. Open Appearance and Layout from a long-press on
  the Assistant page's chrome: the title says "Workspace layout", the Where chip "Workspace
  pages", and a change made there shows on the terminal and the Display page. Upgrade an install
  that had its own Display layout: the Display page now wears the terminal's. Minimal mode on: the
  Assistant page wears the minimal layout · emulator.
  Undo brings it back) and back on its own slot (nothing moves) · pong.- **Example shaders (11-09, feat/shader-examples).** Wallpaper picker, Shader kind: under your shaders an "Examples" heading and five tiles (Plasma, Aurora, Drift, Caustics, Starfield), none with an In use border, each with a moving thumbnail like the saved ones; with no saved shaders the heading is still there. At font scale 1.3 the heading and tile names stay on one line. Tap Aurora: the editor shows its name and code, Quality Half, Rate 30, the line "Example. Apply saves a copy you can edit.", and no Delete. Apply: the wallpaper moves, the list shows a saved "Aurora" tile with the border and the Aurora example is still under Examples. Open Aurora again and Apply: a second saved shader, "Aurora 2". Edit and delete the saved copy: the example is unchanged. Back out of an example without Apply: nothing new in the list. Look at each example running: Drift takes the look's colours and shifts a little with Home pages; Caustics brightens near a touch; Starfield is light with dark off and dark with it on; none drops frames at Half and 30 fps · emulator, then pong.
  Undo brings it back) and back on its own slot (nothing moves) · pong.- **Wallpaper kind pills carry glyphs (feat/kind-glyphs).** 2026-11-09 · Open the Wallpaper
  picker: Photo, Video and Shader each show a glyph left of the label, in the label's colour
  (checked and unchecked). Light, dark and the launcher's scheme theme; font scale 1.3: the three
  segments still fit one line, no clipped label. Dock style and the editor's Quality and Frame
  rate rows are still text-only (a check mark on the selected one, as before) · pong.

- **Video import shows progress (feat/video-progress).** 2026-11-09 · Wallpaper picker, Video: choose an animated GIF. Under "Choose video or GIF" a bar appears with "Copying…", then fills with "Converting GIF to video… NN%" rising to 100; Choose, the rate pills and Back to photo are greyed meanwhile; then the bar goes and the line shows "In use: name" with its hint. Choose a plain MP4: a brief "Copying…", then the same final state. Pick a broken file: the bar goes and the error line shows, controls back. Rotate or leave mid-import: no crash; reopen later and the final state is shown. Font scale 1.3: the label stays on one line · pong.

- **The crossing's views adapter left the activity (11-10, refactor/crossing-driver).** A move
  with no intended change, so any difference in a swipe is a defect. Home ⇄ Terminal ten times
  each way, Docked and Floating, keyboard up and down: the extra keys fold into their own bottom
  edge towards Home and come back; a whole-stack dip shows the stack's picture sliding and lets
  it go at the landing; the status bar rides and is opaque at rest. Then once rotated, and once
  with the app drawer opened straight after a landing. Frame time as before (the emulator passed
  positions 8 of 8 on d117e1d31; it cannot judge motion) · pong.
- **Edit pages reads its rules from one place (11-10, refactor/overview-host).** A move with no
  intended change. Set as home on a Home page, the Terminal page, the Assistant and a Display
  page. Drag a Home page past the Terminal card, and the Terminal card to the front. Bind a
  Display page to an app: its card carries the app's name, in order beside the Terminal card.
  With a temporary display up, "Add display" still shows and its card is not listed. Home on the
  Terminal page, remove the last window: home falls back to a Home page. Use as Terminal only:
  "bring back" appears. Rename a Home page · emulator, then pong.
- **The dock's code left the activity (11-10, refactor/dock-controller, ADR 0018 wave D part
  two).** Moves with no intended change, so any difference is a defect. Each dock design (Tray,
  Beads, Strip, Peek), Docked and Floating: the shape is whole, the strip under the gesture pill
  continues the dock, beads sit one under each icon. Home ⇄ Workspace with different designs,
  ten times: one restyle at the landing, nothing of the other design left. Change the design so
  the Search field or the Wall strip comes and goes: the dock eases once and rests with nothing
  overlapping. Press the dock: tilt, specular and rim glow, and the touch light under the
  finger; while typing on the in-app keyboard the dock does not sway. Drag a pinned icon one
  slot and back: beads follow. The A–Z bead lifts under the finger. Apps row moved to the top
  edge: the plank's padding, radius and the status strip's lead. Keyboard up and down, rotated
  once. The emulator passed rests, crossings, press, typing and the A–Z lift on c1db54f54; the
  icon drag, the ease in motion and the last cluster (f17339e85) are unseen · emulator, then pong.
- **The status bar's code left the activity (11-10, refactor/statusbar-controller, ADR 0018 wave
  B step one).** A move with no intended change. Bar on the top, the bottom and a side; Compact
  and Expanded on each. Fold by dragging the bar and by the pane's top-border swipe, a drag held
  half way and let go, the fold's landing. Home ⇄ Workspace ten times: the bar rides, its margin
  eases, the strip behind the system bar follows. The editor holds the bar expanded and lets it
  go. Minimal mode. The notifications mark, the widgets' ink after a wallpaper change, the stats
  and weather cards. The emulator A/B (old against new) matched at rest, across crossings, in
  the editor and at the fold's landing; the bar-body drag, the other edges and the stats card
  are unseen. Seen on both builds, so not from this move: after a fold round trip the window
  bar's chips and the weather widget rest 3 to 10 px left of where they started until the next
  settle · pong.
- **The sheets' code left the activity (11-10, refactor/sheets-controller, ADR 0018 wave H step
  one).** Moves with no intended change. Command palette: open by key, from the dock's Search
  field and by the swipe up on Home; type, pick a row, Back; a confirm-required action; a
  contact search and its permission row; with the keyboard floating; over Home and over the
  Workspace; its frost and corners Docked and Floating. App drawer: pull, tile tap, Back, the
  Home key; search with the built-in keyboard (it goes down with the drawer unless it was up
  first) and over a display. A dock folder and a Home folder renamed with each keyboard. The
  terminal's More sheet and Home's long-press sheet. The dock's palette and float tiles turned
  on and off in the Dock card: the dock restyles once. Cold start: the drawer warms up, the
  "Windows restored" notice shows. Notices while a split or the terminal limit refuses · emulator,
  then pong.
- **The crossing's driver left the activity (11-10, refactor/crossing-lead, ADR 0012).** A move
  with no intended change. Home ⇄ Workspace ten times each way with different dock designs on
  the two halves, Docked and Floating, keyboard up and down: the dock morphs, the beads melt
  across the seam, the look blends, the bar rides, and the landing lays out nothing. A swipe
  abandoned half way from each side. Home ⇄ Home between two Home pages. A swipe begun while the
  keyboard is rising, and one begun from a Display page. The emulator A/B matched at rest and
  mid-swipe from Home; Home ⇄ Home and the abandon from the Workspace were not seen, and in the
  mid-swipe hold the dock plate stood about 150 px higher in two of three old-build runs and in
  none of the new build's (read as timing, not proven) · pong.
- 2026-11-10 · fix/sound-stale-pidfile · Reproduce the user report first on the old build: with sound running, `echo $(pidof system_server) > $PREFIX/var/run/termux-launcher/sound/pid`, then `pkill -9 pulseaudio` and open a new pane: the old build gives up (logcat `Termux.LauncherSound` "Failed to validate PID file" … "kept stopping"); the new build starts the server and `pactl info` answers. Then kill the app from recents with kew playing, reopen, open a new pane: `pactl info` answers and a restarted kew plays · emulator, then pong.
- 2026-11-10 · feat/sheets-folders-157 (#157) · The widget picker and Home's long-press sheet slide up and down in 267 ms with 28 dp top corners; a Home folder, a dock folder and a drawer folder cell grow out of their icon in 200 ms with no dim, and Back or an outside tap shrinks them back. Passes when: each opens and closes three times with nothing left behind (no stuck half-way card, no invisible folder still taking taps, no dim); Home's sheet swiped down and opened again slides up fully; a row that opens an editor (Wallpaper, Appearance) opens only after the card has gone; the folder grow is never cut off at the popup's edge, Docked and Floating, keyboard up and down, light, dark, black, Material You and scheme chrome, font scale 1.3 (the folder title ellipsizes on one line) · emulator, then pong.
- 2026-11-11 · feat/drawer-add-to-folder · Long-press an app in the drawer that is in no folder, the menu has "Add to folder" after "Add to home"; it opens a list of the folders by name (not ones the app is already in), and a pick puts the app into that folder and the drawer shows it at once. "New folder with…" ends that list; it asks for a second app (not this one, not one already in a folder) and the drawer then shows a folder of the two. No row on the dock, on Home, inside a folder, or when no folder can take the app and no other app is free. Passes when: this holds in the category and the A-Z drawer views, light, dark, black, Material You and scheme chrome, font scale 1.3 (long folder names ellipsize) · emulator, then pong.
- 2026-11-13 · feat/xfce-termux-route · Set up Linux apps on the Termux route: the Pick your apps card has an XFCE row, unticked by default (and absent on VAJ). Tick it and run the setup: it installs xfce4 with xfdesktop, the whisker menu, the pulseaudio plugin and the everyday extras. Passes when: an XFCE session started from Display shows a wallpaper, a volume icon in the panel and the whisker menu; tapping a text field in an XFCE app (Mousepad) opens the Android keyboard; a proot-distro desktop session still behaves as before · Surface tablet.
- 2026-11-14 · fix/catalogue-hears-installs-away · Download an APK, tap the download notification and install it with the installer in front (InstallerX), then press Home and open the drawer at once. Passes when: the new app is in the drawer, in drawer search and under its letter in the A–Z row without any other action; uninstalling it from Settings and returning Home removes it the same way; the same with the dock app index switched off. The emulator passed install and removal behind Settings with a preinstalled app (Clock) on the default setup; a real APK install, the index-off setup and the old build were not run · pong.
- 2026-11-15 · fix/status-row-keeps-chip · The status row keeps one whole chip when its readings are wider than the row. On a 1080 px phone with the status row's readings all on (CPU, RAM, weather, battery, AI glyph, notifications), carry over an old Appearance with a status corner radius above the slider maximum, make the radius very round, then open the Widgets place (centring spacer) and a place with many windows. Passes when: the badge and the front window chip are drawn whole at every radius, a reading that does not fit is hidden whole (the stats cluster goes first, then the end widgets) and never half drawn or overlapping, a row that fits looks as before, and in a right-to-left locale the chip still holds its room at the right end; with a camera hole in the status band the same holds on both sides of the gap · emulator, then pong. Not seen on screen: any of it (unit tests and Robolectric only).
- 2026-11-15 · fix/onboarding-wizard · First-run setup on a fresh install. Passes when: (1) the wizard's first card appears only after the "Installing bootstrap packages" dialog has gone, never under it; a bootstrap install that fails still leaves the wizard on screen behind the error dialog; (2) Skip on the first card keeps the pre-selected values (Run setup again shows them) and the Setup cards open at once, not on the next start; once they are closed the page switcher's hint and the one-time cards are not held back; (3) Run setup again › Keep my current setup closes with no Setup cards, and none open on the next start; (4) with "Use the system wallpaper" left pre-selected and no storage access, Finish asks for storage access once, before the Setup cards; Not now then opens the cards, and no "Allow wallpaper access" dialog follows on the swipe to the Terminal page; (5) the Setup cards line reads "Opens by itself only when a new card appears; find it any time in Settings › General › Setup." and the sheet does not open on the next start; (6) on the Look step without wallpaper access the worn Look (Mist on a mid phone) draws as picked and TalkBack says the same, Solid can still be picked, and the heading reads "Wallpaper and look"; this step and the On-device AI step scroll until "Colours from wallpaper" and "Nothing downloads now." sit fully above the buttons, at font scale 1.0 and 1.3 · emulator, then pong. Not seen on screen: any of it (unit tests only).
- 2026-11-15 · fix/bar-text-field-focus · The extra-keys bar text field never takes focus while a key page shows. Several terminal windows, in-app keyboard up, tap another window chip in the window bar twice in a row. Passes when: the system keyboard never appears and `dumpsys input_method | grep mInputShown` stays false after each switch, and `dumpsys activity top` shows focus on the terminal view, not `terminal_toolbar_text_input`; the same after closing a window, closing a pane, Terminal to Display and back, a key-set swap and hiding and showing the bar; swiping the bar to the text page still focuses the field and brings up the right keyboard, Send writes to the terminal, and swiping back returns focus to the terminal · emulator, then pong. Not seen on screen: any of it (Robolectric only).
- 2026-11-15 · fix/settings-root-top · Settings root starts at the top. Passes when: the "Search settings" field sits right under the "Settings" title bar (about 8 dp gap), with no empty band, in portrait and landscape, with and without a display cutout, font scale 1.0 and 1.3, and the field is not under the title bar or status bar; Keyboard, General and the other sub-pages look as before; with a process-limit notice showing, the card still appears above the search and its buttons work; scrolling the root and opening search from the field still work · emulator, then pong. Not seen on screen: any of it (Robolectric layout test only).
- 2026-11-16 · feat/keyboard-edge-reach · In-app keyboard edge reach. With the keyboard up in a terminal, tap the very edge of the screen beside `q`, `p`, `a`, `l`, the left and right ends of the bottom row, the strip just above the top row and the strip just under the bottom row. Passes when: each tap types the outer key beside it (the row's own first or last key, the key above or below), once, with its press effect; a hold there opens that key's long press and a swipe from there gives its corner; a tap between the two halves of a split keyboard still types nothing; on the Kazakh Latin layout a tap at the screen edge beside `ä` types nothing while one within half a key of it types `ä`; a back swipe from the screen edge beside the keyboard behaves as before; nothing under the keyboard's bottom margin (dock, nav strip) types a key; the same on the function-key tier, in landscape, and Docked and Floating · emulator, then pong. Not seen on screen: any of it (Robolectric only).
- 2026-11-17 · feat/wr-254-editor-draft (#254) · The Appearance editor's draft over the store. Open Appearance, move a Look and a few sliders on two cards, switch the theme, then from a shell run `launcherctl look set opacity 70 --element dock` (a cell you did not touch) while the editor is still open. Passes when: the dock shows 70 at once; Discard leaves the dock at 70 and everything else as it was before the editor; the same run ending with Done keeps your edits and the dock at 70; setting a slider back to its first value clears the unsaved dot and Back then closes with no question; Back above peek collapses to peek and at peek asks Keep editing / Discard / Save; a theme pick relaunches the window and the unsaved dot and Undo survive it; force-stopping the app mid-edit and starting it again leaves the saved look as it was before the editor (only the draft is lost), and Wallpaper, Icon pack and Home's rows picked in the editor stay as picked (they are written as Settings writes them); minimal mode turned on in the editor is off again after Discard and stays on after Done; with the editor open, changing the peek reflection in the Dock card moves the dock's peek at once · emulator, then pong. Not seen on screen: any of it (Robolectric only).
- 2026-11-17 · feat/wr-254-editor-draft (#254, round 3) · The arrangement and the widgets' glass on the editor's draft. Open Appearance on Home with a widget whose own Glass is On, flip Home's Glass widgets switch, move the dock to another edge, hide the A–Z bar, change the icon size, then slide the wall to a window and back (keyboard up on the window). Passes when: every change shows at once; Discard puts the widget's own Glass, the dock, the A–Z bar and the icon size back, while the window still comes back with its keyboard up; the same run ending with Done keeps all four; moving the dock away and back to its edge leaves no unsaved dot; a theme pick mid-edit keeps the moved dock and its unsaved dot (the widget glass preview is not carried over a theme relaunch); `launcherctl layout` writing another field while the editor is open shows at once and survives Discard · emulator, then pong. Not seen on screen: any of it (Robolectric only).
- 2026-11-17 · feat/wr-258-window-layers (#258, window restructure step 2) · The window is ordered by layer (ground, pages, chrome, sheets, editor, notices). Passes when: with the Appearance editor open, its card sheet, bar, picker and Choose photo are above every sheet and the setup sheet is above the launcher's miniature; open the app drawer, the command palette, the terminal sheet, Home's and the terminal's long-press sheets and the widget picker with the keyboard up and down: each opens over the dock, the pill and the keyboard, runs to the screen's bottom edge under the gesture pill, and closes cleanly; a notice, the reset cards, the help overlay, the key popup bubbles and the voice card show over everything, a notice over an open sheet and over the editor; a window chip's long-press menu drops from its chip; nothing that used to draw over the status bar or the gesture pill (editor, notices, help, sheets) now draws under them or is cut off at the bars; the find bar stands over the keyboard host and a floating keyboard, and the editor's tap layer over both; rename a window from an open sheet and the chip is above the sheet; light, dark, black, Material You and scheme chrome, Docked and Floating, font scale 1.3 · emulator, then pong. Not seen on screen: any of it (Robolectric and layout-parse tests only).
- 2026-11-17 · feat/wr-257-holds-guards (window restructure step 2, #257) · The waits became named Holds, the guards and the counter table run while the motion log is on, and the sheets' first-layout waits use one helper. With Settings › Debugging › Motion log on and `launcherctl motion-log clear`: (1) the relaunch first frame: apply a system wallpaper from the Wallpaper page with the Appearance editor open, so the activity relaunches. Passes when: the first frame drawn is the editor as it stood (never the plain home with the editor cut in later), nothing stays black, and the dump's counters show `hold:relaunch-reveal` taken once and released by signal (a backstop is a finding); (2) a normal session (crossings, keyboard up and down, the Appearance editor, X11 app open, Home presses), then `launcherctl motion-log dump`. Passes when: it ends with `--- counters ---` and the table; `foreign-writer`, `landing-wrote` and `crossing-laid-out` are absent or say where they came from (`crossing-laid-out CHROME_COMMIT late in pre-draw` is a chrome commit that drew one frame of the new look on the old layout: note whether it comes with the keyboard opening by insets); `pre-draw-step:CHROME_COMMIT` against `hold:...` counts shows how many commits ran late; `hold:x11-cover`, `hold:x11-swap`, `hold:x11-next-layout` and `hold:foreground-poll` show take and release counts by signal, landing or backstop; `motion-log clear` then dumps no counters; (3) the sheets that moved onto the first-layout helper still slide in on their first frame and never show in place first: Home's long-press sheet and a custom-rows sheet, the widget picker, a folder popup growing out of its icon, the terminal's drawer and foot panels, an extra-keys set swap, the Home pill docking and undocking with the keyboard, a built-in widget's ink on first show, the app drawer's search with the keyboard raised (the bands re-measure once the keyboard is up), a settle that waits on the wall's first draw (a window or Home page arriving), the AI bench list's first fill, and the Display touchpad standing in a split keyboard's parting after a widened gap · emulator, then pong. Not seen on screen: any of it (Robolectric only).
- 2026-11-17 · feat/wr-266-legibility (#266, window restructure step 4) · Legibility corrections are kept per page and band; the dock's legends drift with the glass. On a light Home and a dark Workspace window, then the reverse (and again with the system in light, then dark, and Material You and scheme chrome), swipe Home to the Terminal and back slowly, then at speed, with the extra keys row showing. Passes when: the extra keys' labels move from dark to pale (or back) in small steps on the same curve as the dock's glass, with no flip at any point of the swipe and no frame where the labels are the wrong ink against the glass for a long stretch (contrast is lowest around the middle, accepted); an abandoned swipe returns the labels to the ink they started in; the landed labels are the ink the re-dress picks at rest (no change at the landing); with the motion log on, `launcherctl motion-log dump` shows no `legibility.sampledInCrossing` or `legibility.measuredInCrossing` lines for a Home to Workspace swipe after the page was visited once (a line on the first crossing after a wallpaper change is the keyboard's judging for a page not yet visited: note it); change the wallpaper, then swipe again: the labels still drift and nothing holds the swipe; open the Appearance editor, change the dock's opacity on the Workspace card, close it, and swipe: the drift ends in the new look's ink. Also passes when: the dock tiles' glyphs, the Prompt line's inks and the in-app keyboard's legends (with the keyboard up on both pages) drift the same way, with no flip and no key relayout during the swipe, and the keyboard's keys are not blurred or flickering at the look steps; the first crossing after a launch or a wallpaper change finds both pages already measured (no hitch at the swipe's start) · emulator, then pong. Not seen on screen: any of it (unit tests only).
- 2026-11-17 · feat/wr-259-bottom-stack (#259, window restructure step 3) · The bottom stack's transforms have one writer, a step of the frame table, on the guards. (1) Emulator sweep, orchestrator's: Settings › Debugging › Motion log on, `launcherctl motion-log clear`; slow drag and fling Home ⇄ Workspace ten times each way, Docked and Floating, keyboard up and down at each end, a swipe abandoned half way from each side, a Home-key Workspace → Home, a fold of the extra keys only (a Home page whose rows match the Workspace's but for the extra keys), the system keyboard raised on Home (the dock rides its lift), press-and-tilt the dock with the keyboard down (the whole stack tilts) and up (the glass and its rows tilt, the keyboard stands), and the app drawer pulled from the dock and closed; then `launcherctl motion-log dump`. Passes when: the counters show no `foreign-writer stack …` and no `landing-wrote stack …` line (an app-drawer or editor line names the next writer to route, and is a finding, not a pass); `pre-draw-step:STACK` is counted (the step runs in the frame's pre-draw). (2) Pong, by eye, no worse than today: the stack travels, sinks and is cut as before, the keyboard rises out from between the dock and the bands, nothing of the slid keyboard shows under the pill at a crossing's tail, the Peek card's reflection follows the rows, and no frame lags the wall by one (the stack's writes moved from the wall's callbacks to the frame table's step) · emulator, then pong. Not seen on screen: any of it (Robolectric and pure tests only).
- 2026-11-17 · feat/wr-265-glass-panels (#265, window restructure step 3: pane slabs, panels and the Display's keyboard frost join the glass service) · (1) Pane slabs: slide Home ⇄ Workspace and Home ⇄ Home slowly and by fling, tilt the plank, drag a divider, drag a float, with Fancier Glass on and off, Docked and Floating, light and dark. Passes when: every slab (terminal panes, the Widgets page, a Home float, a float's title bar, the Display's frame, the Assistant page and the corner tab) keeps its wallpaper fixed to the screen while its page travels, exactly as before, with no frame where a slab shows the wallpaper in its old place; at rest the idle audit counts no frames from the glass. (2) Panels: open and close the app drawer, the command palette, the terminal sheet, Home's and the terminal's long-press menus and a popup menu (drawer long-press) with Live blur on, then off, then on again while one is open (Settings › Battery & performance), with a float and several glass widgets on Home. Passes when: each panel looks as before, opening and closing; Live off puts an open panel on a still picture without a blank frame; a widget never loses its glass behind a panel's back (it goes to the stored frame when a panel takes its slot and comes back when the panel shuts); the same in light, dark, black, Material You and scheme chrome. (3) The Display's keyboard frost: open the Display with the in-app keyboard up, over a running X11 app, in resize and overlay modes, then turn battery saver on and off and Android's window blur switch (developer options) off and on. Passes when: the bed is frosted under the keys and follows the keyboard's reveal frame for frame, the blur goes away at once on battery saver or with the switch off and returns when it is on again, the bed stays see-through (Opacity and Tint unchanged) while there is no blur, nothing flashes, and leaving the Display, Home and back from another app all take the frost away and bring it back · pong, light and dark. Not seen on screen: any of it (Robolectric only).
- 2026-11-17 · feat/wr-260-rows-to-sides (#260, window restructure step 3) · The landing guard over rows. Settings › Debugging › Motion log on, `launcherctl motion-log clear`; Docked, then Floating, keyboard up and down at each end: swipe Home ⇄ Terminal five times each way, Home 1 → Home 2 with two different page layouts (apps row on the left edge on one), Terminal → Display, then `launcherctl motion-log dump`. Passes when: the within-side landings (Home 1 → Home 2, Terminal → Display) count no `landing-wrote rows …` line even when the apps row changes edge; each Home ⇄ Workspace landing that swaps a row today shows `landing-wrote rows <view>.visibility` or `.parent` naming that row: record which rows. Those lines measure what the rows split still owes (the rows are not yet split by side; see #260's report); with the status bar along the bottom (status-at-bottom layout) no line names the status bar's host · emulator, then pong. Not seen on screen: any of it (unit tests only).
- 2026-11-17 · feat/wr-258b-elevation (#258, part 2) · Elevation orders nothing but the A-Z row and the edge stacks; the rest stands by child order and elevation only casts shadows. Passes when it looks as before: A-Z scrub on the dock (the orb behind the dock's glass, the bloom and labels over the keyboard and every Home or Workspace row, the letters over the keybind hint row while a Ctrl+Alt prefix is latched), Docked and Floating, keyboard up and down; the editor's tap layer over the floating keyboard and the find bar, and the labels over the tap layer; a float focused over the floats beside it and over the tiled panes, with its shadow growing in on undock and fading on dock, one pane and many; a Home widget dragged over its neighbours with its lift shadow, and dropped back; a Home page swipe with each page transition (Slide, Stack, Depth, Fade), both directions and reversing half way, the sinking page under and the sliding one over; a window chip lifted in the bar over its neighbours with its shadow, moved and let go; the picker over the editor's card sheet and bar, falling away on Back, with the card still touchable once down; light, dark, black, Material You and scheme chrome · emulator, then pong. Not seen on screen: any of it (Robolectric and layout-parse tests only).
- 2026-11-17 · feat/wr-step3 (window restructure step 3: the keyboard follows the seam, the landing after the landed frame) · In a Home ⇄ Workspace crossing the keyboard, the dock and the status bar now move with the finger on the look's curve (no early hide of a leaving keyboard, no chrome landing early, no separate chrome clock under turning page transitions), and a crossing lands after its landed frame instead of inside the wall's layout. Settings › Debugging › Motion log on, `launcherctl motion-log clear`; Docked, then Floating, keyboard up and down at each end: swipe Home 2 ⇄ Terminal slowly and by fling five times each way, hold a drag half way for two seconds and reverse it, a drag past half and back before release, a Home key Workspace → Home, with a float open on Home 2, and under each page transition (Slide, Stack, Depth, Fade); then `launcherctl motion-log dump`. Passes when: held half way the keyboard is held half way and the dock rides it; reversing brings it back down the same way; nothing jumps on the landed frame; the counters show no `crossing-laid-out` and no `foreign-writer`, and `landing-wrote` only the three Terminal → Home lines (`accessory_stack_container.translationY`/`.clipBottom`, `inapp_keyboard_container.clipBottom`: the keyboard put away at the landing, owed to the room decision in step 3's report). Seen on tl16 (Docked, Peek, one pane and two, a float on Home): counters as stated, held-half-way shot. Not seen: Floating, pong, turning page transitions · pong.
- 2026-11-17 · feat/wr-step3 (wave 2 s3d: a Home float's room from Home's rest, the body surface cut by the stack's writer, the drawer leaves a down keyboard alone) · Motion log on, `launcherctl motion-log clear`; Docked, then Floating, keyboard up on the Terminal: open a float on Home 2, swipe Home 2 → Terminal → Home 2 slowly and by fling, then a Home drawer pull from the dock with the Terminal's keyboard down. Passes when: the float's shell never reports a `pty-resize` in the crossing or after the landing (no 44x37 → 44x25 → 44x37), the float stands the same size on Home 2 before, during and after; the flat surface under the Terminal (and the strip under the dock) looks as before at rest on both pages, and in the crossing its bottom edge moves smoothly between the two rests with no jump at the landing; the dump shows no `foreign-writer stack inapp_keyboard_container.clipBottom` after the drawer pull and the down keyboard is never visible during it. The side stacks, the find strip and the A-Z overlay still take the front page's value as a layout margin or padding and re-lay out once at the landing (wave report wave2-s3d) · emulator, then pong. Not seen on screen: any of it (unit tests and Robolectric only).
- 2026-11-18 · feat/window-restructure 8e16d358b (X server lifecycle in x11/DisplayServer) · Start a display from the Display page, stop it from the place bar's confirm, start it again, kill it from Settings or with `pkill termux-x11` in a shell, and relaunch the launcher with Start in = Display. Seen on pong 11-18 on 3830ac704: start, stop from the confirm, start again and `pkill` all work, and the display keyboard and touchpad keys go grey with the display. After a stop or kill, the Display page stays in front showing Start display (intended once a display has run, DisplayServer.java:73-74). Still owed: a relaunch with Start in = Display lands on the display window, and the chip row shows a running X app's chip · pong (X11 app installed).
- 2026-11-18 · feat/window-restructure (editor leftovers) · Turn Glass widgets on in the Home card with a built-in widget on Home, then change the theme so the launcher relaunches with the editor open. Passes when: the widget still shows the glass preview and the dot is on; Discard puts it back; the editor opens as before from a cold start and from a relaunch · emulator, then pong. Not seen on screen: any of it (unit tests only).
- 2026-11-18 · feat/window-restructure (palette room) · Open the command palette on a terminal with the in-app keyboard up, then down, Docked and Floating dock, and with a floating keyboard. Passes when: the glass and its keycap strip stand above the dock rows and the keyboard, never over them, and typing on the keyboard reaches the palette; on Home it stands as before · pong. Seen on pong 11-18 on 3830ac704: Docked, keyboard up (stands above, typing reaches it) and keyboard down (stands clear; typing not tried). Still owed: Floating, a floating keyboard, and Home (the checker found no way to open it there).
- 2026-11-18 · feat/window-restructure (keyboard switch entry) · Open the Appearance editor on a Terminal with the in-app keyboard shown, Layout card. Passes when: the Keyboard switch reads ON at entry; one tap OFF lights Undo and a tap back ON puts it out; Back with no change leaves without the Unsaved changes dialog · pong.
- 2026-11-18 · feat/wr-270 (#270, the page contract, Assistant behind it) · Back is one chain: raised surfaces, then the front page, then Home page 1. Open the Assistant from the drawer entry and from the page overview's Add assistant. With the Chats list open, Back closes it and stays; with the page switch panel, the command palette, a long-press sheet, the app drawer or the Appearance editor (Discard at peek) over the Assistant, each Back closes that one surface and stays on the Assistant; with nothing open, Back goes to Home page 1 (before: nothing happened); from Home page 2 Back does what it did before. Tap the message field: the launcher's keyboard comes up and types into it; with the launcher's keyboard off, the phone's opens. Motion log on, `launcherctl motion-log clear`, Home ⇄ Assistant five times each way slow and by fling, then dump: no `crossing-resized`, `foreign-writer` or `crossing-laid-out` line naming the Assistant · emulator (emu2 was leased elsewhere on 11-18), then pong. Not seen on screen: any of it (unit tests only).
- 2026-11-18 · feat/wr-271 (#271, Home behind the page contract, slices 2, 3 and the wall's Home seam) · Home's leave work runs at the commit, shown work at the landing's front step. Passes when: Home 1 ⇄ Home 2 turns the grid and the glyph row with nothing re-laid and an open widget control or folder popup closing on the swipe; Home → Terminal closes a folder popup as the slide starts, a focused float gives the keyboard back to the window, and after Terminal → Home widgets still take a resize (move a widget or change Margin) and keep updating after screen-off; a slide away from Home that is reversed mid-way (grab it again and bring it back) leaves widgets resizing as before; the Home press lands on Edit pages' default page · emulator (no lease tool reachable on 11-18), then pong. Not seen on screen: any of it.
- 2026-11-18 · feat/wr-271 (#271) · Back on Home now goes through the page, after everything raised over it. Passes when: in widget edit mode Back commits the edit; with the widget picker open Back closes it; a focused float with the launcher's keyboard up (always-on off) lowers the keyboard and the float keeps focus; with the app drawer or the palette open over Home in edit mode, the first Back closes the drawer or palette and the second commits the edit (before: the edit committed first); on Home page 2 with nothing open Back goes to Home page 1; on Home page 1 nothing happens · emulator, then pong. Not seen on screen: any of it.
- 2026-11-18 · fix/terminal-only-relaunch (#276) · Terminal only, not the phone's home app: open the app, Ctrl+D the only shell, open it again from its icon without force-stopping. Passes when: the second open shows a working shell (no `>_` card, no close after a second); with Home pages shown or as the home app, closing the last shell still leaves Home with no shell, and a theme change there does not start one · emulator, then pong. Not seen on screen: any of it (unit tests only).
- 2026-11-18 · feat/window-restructure 48fef9e64 (flicker waves 3–5) · One pong round, `scripts/pong-round.sh <dir>` on the logging build (apks/55-l5-diag-staging-cb783f30a), twice: once with Home's keyboard down and the Terminal's up, once with both up (Home now keeps its keyboard per visit). Passes when `summary.txt` lists no lone jump after either landing (the dock icons never vanish after Terminal → Home, and the keyboard glass never flashes lighter after Home → Terminal), both crossings land, the counters show no guard, INVARIANT or keyboard-preroll lines, and the strips show the pane glass unchanged at a carousel crossing's first frame · pong. Not seen on screen: any of it; the causes were found on pong's log of e4cb9b95c and fixed on the emulator.
- 2026-11-18 · feat/window-restructure 48fef9e64 (shared pages pool, live glass over frozen pages) · On Home ⇄ Terminal with carousel, the glass over the terminal pane, the dock and the keyboard keeps its blur, frost and refraction moving with the pages (nothing looks baked or a step behind), at rest and in the swipe; `dumpsys meminfo com.termux` Graphics stays within about 6 MB of the build before after ten crossings; Live blur off still puts every surface on a still picture · pong. A phone with 4 GB or less (eighth-scale pictures) is not available; owed if one turns up. Not seen on screen: any of it.
- 2026-11-18 · feat/window-restructure 48fef9e64 (Home keeps its keyboard) · Raise the in-app keyboard on Home, go to the Terminal and back: Home comes back with it up; lower it on Home, go and come back: it stays down; with Home's keyboard set to always on it is up every time · pong. Look for two things seen once on the emulator: a keyboard grab pill over the dock card after Terminal → Home (Home's keyboard down, the Terminal's up), and Home's page glyphs showing faintly through the raised keyboard · pong, Docked and Floating.
- 2026-11-18 · feat/wr-272 (#272, the Display behind the page contract, slices 2–5) · With an X11 app on pong, motion log on: land on a Display page from Terminal and from Home, slow and by fling. Passes when: the still gives way to the live screen with no black frame and no jump, and the log's `hold-released hold=x11-swap` / `x11-cover` / `wall-still` lines say signal far more often than backstop; a cold start that finds the display stopped offers Start; touchpad mode shows the pad on the first frame of the switch; leaving the Display for a Terminal, the first key typed reaches the shell (the hand-back no longer retries after layouts; the per-key safety net covers a miss); Display page to Display page keeps the display attached with no flash · pong. Not seen on screen: any of it (unit tests only).
- 2026-11-18 · feat/wr-272 (#272) · Back on a Display page runs through its page. Passes when: with a layer open, Back closes the layer; with the keyboard the page raised (or the keyboard in the frame) up, Back lowers it; on a running display Back goes to the X app; on a stopped display with nothing raised Back goes to Home page 1 (before: nothing happened). Keyboard up and down, built-in and Android keyboard routes, Docked and Floating · emulator for the stopped-display Back, then pong. Not seen on screen: any of it.
- 2026-11-18 · feat/wr-267 (#267, the top stack) · Home ⇄ Terminal, slow and by fling, with the status bar at the top edge and again at the bottom edge, light and dark, two looks apart (Home's status bar opacity, tint and blur set differently from the Terminal's), window chips shown. Passes when: the bar never switches its look, frost or ink in one frame (it blends across the swipe and nothing steps at the landing); the window chips and the session badge slide in and out with the Terminal and rest out of sight on Home; nothing about the chips changes at the landing; the motion log shows no `landing-wrote`, `foreign-writer` or INVARIANT line naming `top`; the frost strip behind the system bar does not flash on a crossing toward a page that shows it · pong (on hold), emulator sweep in the #267 report. Not seen on pong: any of it.
- 2026-11-18 · feat/wr-rests (#273 slices 2–3, one Terminal page per window, the Terminal's Back) · Window 1 → window 2 by swipe and by chip, one pane and several, then type: the keys reach the arriving window's shell at once. Display → Terminal: the first key typed reaches the shell (the Terminal now takes the keys on its own front, after the landing, not the Display at the commit). Back on a terminal with nothing open: with the launcher's keyboard up it goes down; pressed again it goes to Home page 1 (before: nothing happened); with Back mapped to Escape (`back-key=escape`) it sends Escape and stays; a text selection still ends on Back · emulator, then pong. Not seen on screen: any of it until the sweep below says so.
- 2026-11-18 · feat/wr-273b (#273 slices 4–5: the wall host goes, the landed settle is a frame step, the return hold goes) · Motion log on. Home ⇄ Terminal ⇄ Display, slow and by fling, keyboard up and down, Docked and Floating; window 1 → window 2 by swipe and chip; the status bar's top-border swipe and the bottom-border keyboard swipe; a held border's sink. Passes when: the chrome lands with the page and the settle shows no step in the landed frame (no `landing-wrote`, `INVARIANT` or `crossing-resized`); the terminal's rims fade and its frame line sinks with the page as before; leaving for another app and coming back with nothing changed logs `return rest unchanged` and no `pty-resize`, and after a change (keyboard toggled elsewhere, font, rotation) one `return rest changed` and one resize; screen off and on the same · pong. Not seen on pong: any of it.
- 2026-11-18 · fix/wr-back-glyph · Back on Home page 2 goes to Home page 1 and the Home glyph row reads "1" at once (before: it kept "2"). Assistant page with its text field focused and the in-app keyboard up: Back drops the field and lowers the keyboard, the next Back goes to Home page 1 (before: three Backs did nothing); with Chats open, the first Back still closes Chats · pong. Not seen on screen: any of it.
- 2026-11-18 · feat/wr-261 (#261, the apps row writes only its own) · Docked and Floating, keyboard up and down: drag a dock icon and reorder it (the lifted slot hides and comes back, also when the icon goes to Home or Remove); scrub A–Z and hold on an icon (the focused icon dims and the previous one comes back); turn the row's pages (the ticks follow, the rail's two ticks follow its scroll); tap the drawer tile, and drag it to another column. Passes when each works as before, icons draw on the first frame after a restart with no blank row, Home ⇄ Terminal shows no icon jump at the landing (the crossing morph is gone), and the motion log shows no `foreign-writer` naming the apps row · pong. Not seen on pong: any of it.
- 2026-11-18 · fix/wr-clip84 (the body's cut at a landing) · Motion log on, `launcherctl motion-log clear`, Home ⇄ Terminal slow and by fling, snap-backs on the Terminal, keyboard up and down, Docked and Floating, then dump. Passes when no `landing-wrote` line names `terminal_background.clipBottom` (before: 1430->1514 or 2002->2086 Docked, 1349->1428 Floating, on some Terminal landings) · pong. Seen on the emulator (tl16, pong's prefs2): Docked 13 crossings and Floating 8, none. Not seen on pong.
- 2026-11-19 · feat/wr-glue (the pages' activity glue leaves TermuxActivity) · Motion log on. A float inside a terminal window keeps its frosted glass at rest, across a crossing, and after another window that also holds a float is closed (the floats now get their glass through the Terminal page). Home floats and widgets keep their glass. Home ⇄ Terminal ⇄ Display: the Display page starts, raises or runs its bound app on landing as before; touchpad mode shows the pad over the keyboard on the first frame; the phone's keyboard over the Display still opens and closes; Floating dock as well as Docked. Keyboard on arrival per page as before (keyboard-on-enter now read from each page's rest) · pong. Seen on the emulator (tl16b, prefs2, Docked): Home ⇄ Terminal ⇄ Assistant 11 crossings, Back, a Home float's glass, resume; no INVARIANT, crossing-resized or foreign-writer; landing-wrote terminal_background.clipBottom on Home (keyboard up) → Assistant, same on base. Not seen anywhere: window floats, Display, Floating.
- 2026-11-19 · fix/wr-leftovers (return wait, keyboard cut, PAGE_FRONT, kinds-only wall, dock carry) · Motion log on. Leave for another app and come back with nothing changed: `return rest unchanged ... release=signal` on most returns (before: always `release=backstop`). Home ⇄ Terminal, Floating, keyboard down: no `landing-wrote inapp_keyboard_container.clipBottom` (before 0<->1). Terminal → Assistant → Display and back: no `crossing-laid-out PAGE_FRONT`; the Display takes the keys and the pad one frame after its landing. Floating dock: drag an icon by its top edge onto another (folder) and past the last one (reorder) · pong. Seen on the emulator (emu2, prefs2): keyboard cut 0 landing-wrote in 16 Floating + 15 Docked crossings; Floating reorder and merge work. Not seen: release=signal (emulator: first frame 350-500 ms after resume, focus never before the 160 ms backstop), Display/Assistant landings (not on that ring).
- 2026-11-19 · feat/one-look-288 (#288) · Menus and dialogs wear Global's glass with a floor on opacity (provisional 60%, SheetGlass.OPACITY_FLOOR_PERCENT; the developer picks the number on pong). Passes when: with Global on Clear the app drawer, a folder, a long-press menu, the command palette and a notice are each at the floor and readable over a busy wallpaper, in light and dark; with Global above the floor they match Global; the Appearance editor has no Menus and dialogs row; a Home float on Clear stays clear, below the floor. Pong: pick the floor number. · how: pong, in the one-look check wave.
- 2026-11-19 · fix/display-x11 (display flicker under a shader wallpaper) · Set a Live scene wallpaper, open a Display page with a running X app and leave it up for about 30 s, then lower and raise the in-app keyboard over it. Passes when the desktop never vanishes (before: about every other second the wallpaper showed through) and the dock, status bar, window chips and keyboard still draw over it · pong. Not seen on screen: any of it (unit tests only). The typing snap-back is #296; the X11Keys log was read (keys arrive correctly) and its lines are removed.
- 2026-11-19 · feat/home-float-chips (#295) · Home's status bar shows a chip per float on the Home page shown. Passes when: opening a float on Home adds a chip at the bar's start in Home's accent and closing it removes it; a minimised float's chip shows dimmed with its dot and the float folds into that chip; a tap on a chip brings its float forward, or back from minimised with the keyboard up; Home 2's floats show only on Home 2; across Home ⇄ Terminal (swipe and Home key) Home's chips slide out to the left with Home's content while the window chips slide in from the right, never both in the slot at once, and the Terminal shows no Home chip; Docked and Floating, keyboard up and down, the status bar along the top and down a side (the chips stack in a column) · emulator, then pong. Seen on the emulator (Docked, bar on top): one and two floats, minimised chip, tap restore, Home key crossing back. Not seen on pong: any of it.
- 2026-11-20 · fix/drawer-keyboard-cards (merged 8729a3b12) · In Docked the app drawer is always its own inset glass card (Margin from the sides and top, the pane's corners all round), keyboard up or down, through the pull, the release, a cancelled pull and every close; a keyboard the drawer raised slides away opaque in its card look (scroll, A–Z touch, Back close) with the card's bottom one gap above its live top; the keyboard card is rounded at its own top under the drawer; the A–Z strip spreads over the card. Seen on pong (Docked, Vertical layout, dark): pull from Home, scroll and A–Z slide-away, Back mid-slide, Back close, cancelled pull, app launch (rounds card3–card5 in ~/.cache/window-restructure/rounds/cards). Owed: Pages and Categories layouts, light theme, the system keyboard (card ends above it), opening from Terminal with the keyboard already up (it stays and keeps the Terminal's look), Floating (its close still fades the keyboard; may show the keys over bare wallpaper) · pong.
- 2026-11-20 · feat/adaptive-legibility (#297) · One legibility measurement for the whole wallpaper; Adaptive legibility (Global and Terminal cards, beside Contrast) puts each item back on the wallpaper behind it; widget glass takes the correction; the Even out glass developer switch is gone. Passes when, over a mixed wallpaper (a bright patch on a dark picture) with the switch off: dragging a glass widget, a Bare widget and a Home float onto and off the bright patch changes nothing about its glass, its ink or the terminal palette; with the switch on: the panes, dock and keyboard look as on the previous build and a glass widget moved onto the bright patch darkens its glass toward its text; flipping the switch on either card flips the other card's row and re-dresses at once; the widget correction fades out as the Widgets Opacity goes to 0 · pong, Docked and Floating, keyboard up and down, one pane and several, light, dark and scheme chrome (Material You). Not seen on pong: any of it.
- 2026-11-21 · 96b670b4c (#302) · Under Floating the extra-keys bar's own glass card. Seen on pong with Peek and the default layout only. Passes when, for Tray, Beads and Strip as well: Home ⇄ Terminal lands with no step or late change, keyboard up and down, and the extra-keys card at rest looks like the old capsule except 15 px shorter with the keyboard down. Also: Docked is unchanged; the system keyboard, the split and floating keyboards and landscape cross without a step; a layout with the apps or A–Z row on the Terminal's dock still morphs as before. · pong or emulator, `scripts/pong-round.sh` plus a design change per round
- 2026-11-21 · 8023c4d12 (#308) · The dock's card for a place with no apps row. Passes when: on the Terminal side the card lists only Bars and Appearance, Home's dock card lists everything, and moving the apps row off the dock inside the editor updates the card. · emulator, Appearance editor, never Done
- 2026-11-22 · #309 · On Home the keyboard comes up over the dock. Seen on pong only with a Home float under Floating + Peek. Passes when, for the command palette and a terminal widget too: the dock's icons, rows and card stay at their rest place under the keyboard, nothing of the dock shows above it, no seam or second coat, a tap between the split keyboard's halves does not reach the dock, the palette's glass ends at the keyboard's top and the Home pill sits on it. Also: Docked with a gesture strip (no flash of glass above the keyboard when it shows), Tray, Beads and Strip, the system keyboard, a Home with its keyboard always on (the dock still rides), and Home ⇄ Terminal with the keyboard up at both ends. · emulator or pong
- 2026-11-22 · #310 · Home floats. Seen on the emulator under Docked style only. Passes when under Floating too, and in landscape and with the system keyboard: a new float opens above the keyboard's place, only the focused one makes room, each new one takes a free step. · emulator or pong
- 2026-11-22 · feat/page-bar-compact · The compact page bar and its lift. Passes when: at rest the pill is clearly smaller and sits close over the dock (Docked, Floating, Peek, dock hidden); a slide lifts the glyph under the finger and its neighbours smoothly out of the pill with the page's name above it, following the finger with no step, and they settle back smoothly on release, cancel and a page chosen; no bubble appears; a glyph is still easy to hit at 48dp; the lifted glyphs and the name are readable over light and dark wallpapers in light and dark mode and with the scheme chrome (the ink margins were 2–5 % over target before); the key's pop-up in a workspace lifts the same way; font scale 1.3 keeps the name on one line; Home's widgets look right with the grid 42 px taller at 420 dpi (row count is the grid's own, each row grows by 42 px ÷ rows) · pong
