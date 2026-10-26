# Device checks owed (Developer)

Built, merged, and waiting for the developer's hand on the phone: feel, frame timing, a thumb
mid-gesture, a lock screen, taste, a Linux container or package only the developer installs,
Perfetto. Agents never work this file's items; an agent that owes one adds it here in the same
commit as the change. Items an agent can drive over adb live in `project-docs/device-checks.md`.

One line per item: date · commit or PR · what to look at · what passes · how (device, route).
Seen items are **deleted**, never ticked; the commit or a bug is the record. Before a drop or a
cut the developer works this list or accepts what is left (recorded as in
`docs/agents/releasing.md`, "Cutting a stable release").

## Not drivable over adb (pong checker, 2026-10-26)
- 2026-10-25 · commit "fix(x11): GPU setup follows Termux's own package manager, refreshes once,
  never asks; add --estimate" · a full run on pong (pacman Termux with dpkg installed) prints
  "Package manager: pacman", syncs the databases once, installs the Vulkan wrapper's
  .pkg.tar.xz, asks nothing after the page's confirmation, and removes glmark2 and the losing
  drivers at the end (`pacman -Q glmark2` → not found) · pong.
- 2026-10-25 · commit "feat(settings): run the graphics benchmark on its page, in a read-only
  terminal" · Run benchmark: the dialog says "Working out the sizes…", then three sizes, Run
  enabled; Run shows the panel with "Step N of 8 · …" and live output, no session/pane/tab
  appears in the session manager; Stop during step 3 shows the script putting packages back and
  ends "stopped" with nothing half-removed; leave mid-run: the notification counts steps and
  tapping it reopens the page on the live output (no second Settings on top); return after it
  finished: Close, then the results list shows the new scores; no "press Enter" line at the end ·
  pong.
- 2026-10-25 · commit "fix(peek): the strip under the pill wears the card's soft rim on its
  sides" · Peek glass, Docked, keyboard down, Android 13+, on the home page whose wallpaper is
  bright just left of the card (page 1 on pong): no lighter patch in the strip under the first
  icon, the card's sides run on into the strip's with no change at the seam; check the right
  side too, with Fancier glass on and off, and that the strip's top and bottom edges show no bend.
  Keyboard up / Floating / Search field on: the strip as before (full width, seamed) · pong.
- 2026-10-25 · same commit · Sound off: server, `dbus-daemon --config-file=…/bus.conf` and the
  media helper are gone at once, both drop-ins (prefix and every distro) removed, a new session
  has no PULSE_SERVER/DBUS_SESSION_BUS_ADDRESS; Media controls greys out · pong.
- 2026-10-25 · same commit · an MPRIS player playing (Termux's kew 4.4.1 registers no MPRIS
  name: use mpv with mpris or a distro player): the media notification shows title, artist and
  local cover art; play/pause, next, previous and seeking from the notification and the lock
  screen drive the player; the status-pane media row and the home Media widget show it · pong.
- 2026-10-25 · same commit · focus: an MPRIS player playing, a navigation prompt / short
  transient: it pauses and resumes by itself; a duck (a notification sound while playing): volume
  dips and returns; Firefox from the drawer playing a video (no MPRIS) is muted while another app
  plays and unmuted once one of them stops · pong.
- 2026-10-23 · #142 phase 2 (commit "feat(place): the crossing morphs the dock's row and holds
  every reader") · the audit's slide clips re-shot, frames recorded: Home → window and window →
  Home, Docked and Floating, each of Tray, Beads and Strip → the pinned icons change size and
  spacing smoothly over the whole slide with no step at the landing (the 118 → 143 px snap gone:
  measure the icon in the last slide frame and the first rest frame, equal within 1 px), the
  Drawer tile fading with its half rather than leaving at the end, no blurred flash at the pane's
  top as the last band settles, Strip's clock not leaving in the last frames, the pane not
  shortening after the landing, the keyboard travelling with the half that has it open · emulator.
- 2026-10-24 · "fix(glass): hold the refraction's corner radius to half its rect" · Floating,
  dock rows hidden, keyboard down, extra keys alone: the pill's ends are round, with no notch
  folding in and no line along the middle; same with the keyboard up, light and dark. The
  emulator never showed the defect, so only pong can confirm the fix · pong.
- 2026-10-24 · bezel commit · how the CRT bezel looks over a real wallpaper with fancier glass on:
  does it read as a monitor frame, not a second pane? · pong.
- 2026-10-24 · commit "feat(home): carry an app from the drawer onto a Home page" · the drawer to
  Home hand-over feels like one gesture: no flash or jump of the icon as the drawer closes and the
  wall slides, and the drop settle reads like Home's own · pong.
- 2026-10-24 · finding (emulator, #152) · a 35° drag from the bottom gap, 500 px across, did not
  turn the page under injection though the ramp asks ~2.6 slops: a thumb at about 35° on pong turns
  the page, and one past 60° never does · pong.
- 2026-10-23 · #142 phase 2 (commit "feat(place): the crossing morphs the dock's row and holds
  every reader") · a slow drag Home → window and back, and a fling reversed mid-way, in both
  Styles with Beads on Home and the Tray on the window: the icons grow or shrink with the finger
  and track it back on the reversal, they never look soft or blurred while scaled, the dock and
  keyboard feel as before, nothing lands late · pong.
- 2026-10-23 · #153 (commit "feat(widgets): widget pages settle on the wall's spring") · on
  Home with two or more pages, a quick flick and a slow drag past the commit point, and a short
  drag let go to spring back, in Slide and in a turning page transition: the page settles like
  the wall does (a flick lands quickly, a slow drag eases in), no step at the release, no
  overshoot, the wallpaper pans with it; Animation speed Slow and Off still apply · pong.
- 2026-10-23 · #151 (commit "feat(launcher): the App menu as grouped rows with an arrow") · the
  App menu's open and close on pong: the open's slight overshoot out of the arrow, the 83 ms row
  fade, the 233 ms close, one long-press buzz on open (none twice, none with Haptic feedback off)
  → feels like Launcher3's, never laggy · pong.
- 2026-10-25 · commit "flip end pins at the old clips' size" · Flip Clock widget, the expanded bar
  and the dock: each card shows a clear small rectangle at both ends of the split line (3.2dp by
  7.4dp at card scale, a pixel past the card's edge), under the digit so none crosses a glyph; no
  leaf shading at the fold; the flipping leaf carries no pins and they stay put between flips ·
  pong.

## Owed since before 2026-10-26
- 2026-10-25 · commit "fix(statusbar): the status row flows around the camera and clears the
  screen's corners" (#163) · on pong with the bar along the top in Full screen: the gap sits on
  the camera, nothing hides under it, the row's first and last items clear the corner curve in
  Docked and Floating; folding the bar open and shut moves no chip under the camera; Camera
  cutout › Custom moves the gap with Position and resizes it with Width, Off removes it · pong.
- 2026-10-25 · commit "fix(graphics): the benchmark bar stands apart from the cards, and its
  output fits" · during a run the floating bar reads as a layer above the cards (tint, outline,
  shadow) in light, dark, black and the Material You scheme; the terminal's lines mostly fit
  without wrapping and the text stays readable; rotate mid-run and the text resizes · pong.
- 2026-10-25 · commit "feat(settings): run the graphics benchmark on its page, in a read-only
  terminal" · the run panel and the confirmation read well in light, dark and black themes and at
  font scale 1.3 (the terminal box keeps the terminal's own colours); rotate during a run · pong.
- 2026-10-25 · commit "feat(settings): Sound and Media controls switches on the Linux apps page" · the
  Audio section's two switches and its status line read right and do not clip at font scale 1.3
  and in dark/light/scheme themes; Media controls greys out with Sound off · pong.
- 2026-10-25 · commit "feat(sound): one launcher-run sound server and media controls for every
  shell and distro" · Settings › Terminal › Linux apps › Audio shows "Sound kept stopping…" after
  three kills of the server within a minute (`kill -9`), and "Using your own sound setup" with a
  `client.conf` in `~/.config/pulse`; each line goes once the cause does · pong.
- 2026-10-25 · commit "feat(sound): one launcher-run sound server and media controls for every
  shell and distro" · a day of real use: sound from kew, Firefox and an xfce
  desktop survives closing them, the screen off and an hour idle (the reporter's moto g15 case);
  battery at rest no worse than before (the server suspends its sink when idle) · pong.
- 2026-10-25 · commit "fix(dock): each Peek glass surface wears its own reflection fade" ·
  Fancier glass on, Peek, gesture navigation: under each icon a mirrored icon starts right at
  the icon's lower edge on the card (the strongest part, about 29 px on pong, above the strip)
  and fades on down behind the pill with no break, step or dark line where the strip starts;
  before the fix the card above the strip showed none of it (screenshot
  `peek-dock-bug.png`, 10-25). If the card part is still empty, `adb logcat -s
  PeekGlassReflection` prints each view's parent, child index, size, origin and band: send that
  line for the host · pong.
- 2026-10-25 · same commit · the band at the pill's height: the strip under the pill reads ~25%
  darker than the card right above it in the gaps between icons (pong screenshot: card
  48,69,141 → strip 29,53,112 at x 328), with or without a reflection; this fix does not touch
  it (the strip's glass and tint are the shared under-pill code). Judge whether it also shows
  under the Tray; if only under Peek, it is the card's own glass program, if under both it is the
  strip's · pong.
- 2026-10-25 · same commit · a page swipe and an icon press under Peek, Fancier glass on: the
  reflections ride with the icons with no stutter, GPU bars flat at rest · pong.
- 2026-10-25 · commit "feat(dock): draw the Peek glass card and the icons' reflections on it" ·
  Home, Docked, Dock card → Design → Peek: a dark glass card inset about 3% from each side with
  generous top corners, the icons' centres on its top edge, no lower corners anywhere, the card
  running on under the gesture pill with no seam or step where the strip under the pill starts,
  the rim on its top and sides continuing down the strip's sides; the row's end icons stand inside
  the card · pong.
- 2026-10-25 · same · Fancier glass on (managed wallpaper, Android 13+): each icon's reflection
  hangs from its lower edge and fades and blurs down the card into the strip; pressing an icon,
  a notification dot arriving and an icon changing show in the reflection on the same frame; a
  page swipe carries the reflections with the icons; at rest the GPU bars stay flat (no redraw)
  and nothing janks during a swipe · pong.
- 2026-10-25 · same · Fancier glass off: the reflections are soft still pictures that come back
  about 0.2 s after an icon or a dot changes, step away during a page swipe and return on the page
  the row lands on; no redraws at rest · pong.
- 2026-10-25 · same · the reflection hangs from the icons' lower edges, not from the card's top
  edge as the brief said (mirrored about the icons' centres it falls exactly under the icons and
  never shows); judge the look, the line is one constant (`PeekGlassGeometry.compute`) · pong.
- 2026-10-25 · commit "feat(editor): a Reflection slider on the Dock card for Peek glass" · Dock
  card: Reflection shows only while Peek is the design (Tray/Beads/Strip hide it, picking Peek
  brings it back without closing the card); 0 hides the reflections, 100 is strong, the default
  50 is clearly visible; dragging it updates the dock live without stutter; the card's other rows
  look exactly as before · pong.
- 2026-10-25 · same commits · Peek glass falls back to the tray's slab (shelf and shadow under
  Floating included) with no reflection left behind: the keyboard up; the system
  keyboard lifting the dock; Search field on; the A–Z row on the dock; a Workspace window with the
  extra keys; and comes back as the card when each goes away. The long-press app menu over the
  icons is not reflected (by design) and nothing flickers under it · pong.
- 2026-10-25 · same · Peek glass Docked narrows the apps row by the card's inset plus 4 dp, also
  where it falls back to the tray (a Workspace window on the shared layout): judge the icons'
  size there; 3-button navigation (taller strip, buttons over the card) and no navigation bar
  (the reflection only has the air under the icons) · pong.
- 2026-10-24 · widget settings sheet (7fd67a8c8) · Clock gear: the bottom sheet of cards matches
  the mockup (~/reports/clock-settings-2026-10-24/); real faces in the tiles, check badge on the
  selected one; Save and the Glass labels show their text; the selected Glass segment's corners are
  rounded; Save stays above the keyboard in the Second clock editor; Cancel, Back and swipe-down
  discard; Weather's gear in the same cards · pong (no emulator).
- 2026-10-22 · #133 · Slide to Home with Beads, Search field and Drawer tile on: `dumpsys
  gfxinfo` framestats over ten slides each way against the merge note's baseline (median 17 ms,
  p99 57 ms); no worse, no visible step at the settle; both Styles · pong, release build.
- 2026-10-21 · chrome unification · Workspace → Home swipe slow and fast, keyboard up and down:
  the dock never sinks then snaps, only the extra keys fold; Beads swell and fuse into the slab as
  Home slides away and shrink back out on return, no bare dock or square frame; the Workspace dock
  settles as one frosted slab · pong for jank (first confirm Beads draws at all: the emulator
  showed a slab, "Found in the sweep" 1).
- 2026-10-21 · Border drag between windows starts reliably near the seam; taps, long press, dock
  paging and selection are unaffected; last-row swipe, diagonal, short flick, A–Z scrub · pong.
- 2026-10-21 · Three windows, keyboard up on the last: rightward swipe on text, border drag and
  chip tap to the previous window at 60 fps, the dock and keyboard stay put every frame · pong.
- 2026-10-20 · glass refraction · Taste: page drag 18°, held border 6°, pane press 1° read as
  refracting tipped glass; the glint travels round the corner with the tip and fades at the
  settle, rest light restored with no top-left smear; knobs TILT_* and in GlassRefraction · pong.
- 2026-10-20 · glass refraction · Frame time of a held page drag and a seam fling, two panes,
  Living glass on vs off (`perf-frametime.sh`); report any p90 worse than off beyond noise · pong.
- 2026-10-19 · page effects · Depth, Stack, Carousel, Fade with a glass pane, a glass widget and a
  Home float: the frosted wallpaper inside slabs never reads misaligned while the page scales,
  nothing snaps at rest; frame time of each effect vs Slide, drop any effect with p90 worse beyond
  noise · pong.
- 2026-10-19 · wall seams · Home → terminal slide with own layouts: dock leaving and bar arriving
  as one motion; `Wall.seamLayout`, `Wall.seamDress`, `Wall.canvasReach` frames not red against
  the pre-roll frames; a widget never visibly reflows on the first slide frame · pong.
- 2026-10-19 · Body-swipe angle: 1.5× across-over-down pages, vertical scroll never pages, a lazy
  diagonal flick does, start-on-text and border feel the same · pong.
- 2026-10-19 · settings restructure · Taste of the Settings root, Home and Terminal sections
  against the approved plan (names, order, summaries) and the new drawer rows' wording; General ›
  Animation speed Slower, Faster, Off visibly change Home swipe, palette open and float-out · pong.
- 2026-10-19 · On the Surface, landscape and portrait: two-pane Settings list and section read
  well side by side, light and dark · surface.
- 2026-10-19 · Display window arrival and departure feel like a window switch, no empty or black
  frame; plank tilt on a border drag off the display once its still copy is up · pong.
- 2026-10-18 · release build · Start in = Terminal cold start with no Home flash (the emulator
  landed on Home: "Found in the sweep" 2); Appearance over Home set apart (Floating, Margin 24);
  dark theme, rotation, font scale · pong.
- 2026-10-17 · Fresh-session welcome card reads well over light and dark wallpaper (the emulator
  showed its description lines nearly invisible on dark glass in the light theme); "Tap to read
  the docs" opens the guide · pong, release build.
- 2026-10-16 · touch engine · After days of typing on 1dbeda5b1,
  `project-docs/verification/touch-engine-passbar.py --pong`: space take-backs drop 30%+, no
  other key's rate rises · pong.
- 2026-10-15 · voice · One sitting by voice: dictate a sentence the cleanup changes, edit the
  card text, marks stay and move with typing, committed text equals what was shown; Mic
  sensitivity Normal and High pick up soft speech, clicks never open a phrase · pong.
- 2026-10-15 · terminal · Drag, fling and settle a long transcript with bold, 256 colours, URLs, a
  kitty image and text-sizing blocks, one and split panes, keyboard up and down: every row lands as
  before, blocks whole, cursor row right, find overlay tracks · pong.
- 2026-10-12 · Export settings, then Import settings as the default home: the launcher returns as
  home within seconds with the same look, layout and font size, no home-app picker · pong.
- 2026-10-13 · TAI · E4B GPU load near 3.7 GB free with a memory hog: `oom_guard` stops the
  runtime, launcher and Shizuku stay, booked as stop not crash · pong, by hand, everything saved.
- 2026-10-23 · ported from 1.3.0-pre.2 · Daily use with the real GPU and Firefox: display keyboard
  and touchpad, the mode button's look and TalkBack, Qt and dialog focus, drawer search from
  Keyboard off, the xterm text tap gap; stock x11 Chromium opens from the drawer and plays video
  with sound once the pulse stopgaps are removed · pong, release build.
- 2026-10-23 · ported · Fullscreen on: the window bar sits at the top with no empty band on Home
  and Workspace; does the punch-hole camera cover window-bar chips · pong, release build.
- 2026-10-22 · round-fix branch, by hand · No icon reflection over the docked keyboard on the
  terminal; Glass 0 shows only the rim; chip-jump `TermuxWindowBar` log trace · pong.
- 2026-10-22 · second drop · Home → window 2 → window 1 by chips leaves no wallpaper gap above the
  extra keys, also split; the apps row → Home slide shows icons at their final size, Docked,
  Floating, Beads · pong.
- 2026-10-25 · commit "feat(wall): the page overview replaces the Edit pages list" · Pinch in
  on Home's free area, Home's sheet › Edit pages, and Settings › Home › Edit pages all zoom out to
  one strip: Home pages, a gap with a thin divider, then the session's windows (display where its
  chip stands); the zoom out and back in has no jank and no flash at either end; the wallpaper
  stays full-screen behind; the focused card is live (clock ticks, terminal text scrolls, dock
  shows) and the next card peeks in from the right; red × centred on each card's top edge (none on
  the last Home page), home button on the bottom edge of Home cards only, filled on the default
  page and outlined elsewhere, tap makes that page the default; dots below, three dark pills at the
  bottom, legible at font scale 1.3 · pong.
- 2026-10-25 · same commit · Scroll and fling settle on a card; cards you have scrolled past show
  their picture afterwards (not the live page or a wrong page); a terminal card never focused shows
  its text; the display card shows the display's picture, and the display's surface never shows
  through the cards; hold a card and drag: its own half makes room, dropped across the seam it
  springs back, dropped in its half the order sticks after the overview closes (Home pages, floats
  and the default page follow; window chips follow) · pong.
- 2026-10-25 · same commit · × on an empty page or an idle terminal goes at once; on a page with
  items, a terminal running something (`sleep 100`) or the display: the card goes with an
  "Undo" snackbar above the pills; Undo brings it back in place; after the snackbar times out, or
  another action, or closing the overview, it is really gone (the `sleep` shell is killed only
  then; the display stops) · pong.
- 2026-10-25 · same commit · + Add page with a Home page focused inserts right after it, with a
  window focused after the last Home page; + Add terminal / + Add display after the focused window,
  or first from a Home page; + Add display greyed while a display is open; Back and a tap on a card
  zoom into that card and the wall rests there (Back from the Settings row returns to Settings
  › Home); TalkBack reads each card, its × and its home button · pong.

## Graphics acceleration page (#162)
- 2026-10-24 · same · Run benchmark on pong (Adreno): every fitting profile gets a score, the
  fastest wins, the Automatic row and the Set up Linux apps summary name it; pick another
  installed profile and open a GPU app: it draws, and glmark2-es2 names that renderer · pong, by
  hand.
- 2026-10-24 · commits "feat(x11-server): a headless server never announces itself…" and
  "fix(x11): the GPU benchmark tests on a headless display of its own…" · display on and showing
  a session, Run benchmark: the display window never switches or reloads during the run, and
  afterwards shows the same session, still connected, glass intact, no "soft reset"; the log
  names the test display (:7 or the first free number above) · pong.
- 2026-10-24 · same · display off, Run benchmark to the end, then once more stopped with Ctrl-C
  during step 5: afterwards no `termux-x11` process (`ps -ef | grep termux-x11`) and no new socket
  in `$PREFIX/tmp/.X11-unix` · pong.
- 2026-10-24 · commit "feat(settings): a Graphics acceleration row on Linux apps" · Settings →
  Linux apps: a Graphics acceleration row with an icon right under Set up Linux apps; tap it: the
  page opens, Back returns to Linux apps; search "graphics" finds the page under Linux apps; the
  guide's own Graphics link still opens it · pong.

## Power-user round, wave 1 (2026-10-25)
- 2026-10-25 · b6b33bc7c · Contrast floor on pong: a Powerline/starship prompt on a transparent pane has no colour seams at the arrows; typing, scroll and heavy-output frame times match the baselines in docs/agents/performance.md · pong.
- 2026-10-25 · 4957e1b52 · A floating terminal on Home fades with the drawer and comes back without a flash · pong.
- 2026-10-25 · — · First tap on an A–Z letter whose apps aren't pinned: how long the grey loading tiles show (decides whether to pre-render) · pong.

## Power-user round, wave 2 and follow-ups (2026-10-25)
- 2026-10-25 · b27f2b2df · First tap on an unpinned A–Z letter: icons already there on release; repeated scrubs leave no memory growth · pong.

## Power-user round, wave 3 so far (2026-10-25)
- 2026-10-25 · 5b5b34d01 · Five seconds for Undo: long enough in real use? · pong.

## Power-user round, Home ↔ dock and shortcuts (2026-10-25)
- 2026-10-25 · 6b6fc51b8 · Drag feel on pong: the hold, the sideways pickup from the dock, no jank in the carried picture · pong.

## Editor redesign (2026-10-26)
- 2026-10-26 · editor redesign wave 5 (commit "feat(launcher): A–Z glass off, Faint and Pull
  tab") · A–Z Pull tab: Back still works from that edge above and below the tab, and the page
  swipe never takes a drag that began on it (Faint and Pull tab on Right, Left and Top, Home and
  Workspace, Docked and Floating seen on emu 10-26; injected edge swipes cannot prove Back) · pong.
- 2026-10-26 · editor redesign wave 5 (commit "feat(launcher): long-press popup on the dock,
  status bar and keyboard") · the dock popup under Floating Style, on Home and the Workspace,
  keyboard up and down (dock, status bar and keyboard popups under Docked seen on emu 10-26) · emulator.
- 2026-10-26 · wave 4 · The welcome page's two choice cards: do they read as the app's segment style? · pong.
- 2026-10-26 · wave 4b · Dark labels with a pale glow on a light wallpaper: does it read as Home's label, or should the shadow stay dark always? · pong.

## Home ⇄ Workspace as one motion (2026-10-26, f30d90c1c)
- 2026-10-26 · A spring-back to Home leaves Home's room intact (no stray bottom inset) · pong.
- 2026-10-26 · Lens beads and the Search field during the crossing; a plain border with the pre-size · pong.
- 2026-10-26 · Halves dressed alike under Slide: keyboard timing unchanged from the 09-27/28 calls · pong.
- 2026-10-26 · Home-only swipes, Peek, the drawer, reduced motion and Animation speed off: unchanged · pong.
- 2026-10-26 · Perfetto: first-frame cost of the stack picture (trace section Wall.stackPicture) and memory while a crossing runs; below Android 10 the old dip runs · pong.
