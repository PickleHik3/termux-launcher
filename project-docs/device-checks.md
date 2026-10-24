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

The 2026-10-23/24 emulator sweep (staging `1.1.0-pre.6+dev.8f303c274`, three Android 16
emulators) worked 332 Agent items: 116 passed, 148 passed on the part the emulator can show,
28 were stale, 14 failed. Its failures and end-user findings are in "Found in the sweep" below
and, with crops, at `~/reports/device-checks-2026-10-24/`. Items the sweep could not set up and
items superseded by the chrome unification, the widget redesign and the glass rounds were
dropped; what follows is what still owes a look.

## Agent
- 2026-10-22 · #133 review fixes (commit "fix(stack): a half's rest is measured in its own
  Style…") · Home Floating with a status bar along the bottom, the window Docked with it along
  the top: swipe Home → window with the window's pane not shown before → the pane meets the dock
  with no gap and no jump at the settle · emulator (no checker found the route to opposite
  bars per half; the Layout editor's place icon at 645,1728 switches halves).
- 2026-10-22 · PR #99 (migrations 9–13) and the glass rounds · Upgrade from the shipped stable
  (1.0.6 / 1.1.0 release APK) over a real profile with a Home dock height, Corners or Margin set
  apart, a Prompt line or Taskbar dock style and a Custom Look → Home keeps its rows, Prompt line
  → Tray + Search field, Taskbar → Tray + Drawer tile, the Look keeps its stop and colour, no
  first-run tour · emulator with the release APK from the GitHub release (not on disk).
- 2026-10-22 · #133 review fixes · Floating Tray's shelf (contact shadow and glow) shows under the
  slab and goes with the keyboard up · pong: the emulator's CPU renderer drew none
  (`~/reports/device-checks-2026-10-24/`, A29).
- 2026-10-21 · pass-2 seam frost · Home → Workspace landing with the keyboard up: the one frame of
  frosted terminal text is the terminal's own reflow frost; report only if it reads worse under
  one dock design · pong (one-frame, not judgeable on the emulator).
- 2026-10-17 · commit "feat(panes): a glass titlebar on every float" · The float's strip glass
  matches the pane under it, Docked and Floating, glass on and off: same frost, tint and corners,
  wallpaper staying put under the strip while the float is dragged · emulator screenshots.
- 2026-10-23 · X11 audio · In a Debian container, Chromium or any app from the drawer plays sound
  over `tcp:127.0.0.1:4713`; kill the server and tap the app again, it comes back; the tcp module
  is listed once after two container launches · a device with proot-distro (the emulator has none).
- 2026-10-24 · sweep · A sideways drag between two windows: the arriving window's page card drew
  blank, text only at the settle (E3-r4 `30-mid2.png`, E3-r2 `slide-w2w.png`) · pong, likely the
  CPU renderer.

## Developer
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

## Found in the sweep (2026-10-24), to triage
Crops and per-batch tables: `~/reports/device-checks-2026-10-24/`. Each becomes an issue or a
fix, then leaves this file.

1. **Beads never renders on Home.** Layout editor → dock → Beads → Done (pref
   `layout.dock_design=lens_beads`, also after a force-stop): Home draws one frosted slab with
   icons; no shader errors. Seen on emu by two checkers.
2. **Start in = Terminal lands on Home after a cold start.** Pref `app_launcher_start_in=workspace`
   written; force-stop + launch (or HOME) shows Home page 1. Seen three times on emu3; the
   usage-mode migration itself writes the right keys.
3. **Wall strip clock row repaints at rest**: 65–70 frames per 12 s with the clock row (0 with
   tiles only); a ticking seconds readout with a ghost digit.
4. **Floats-only workspace Replace-load moves the wall to page 2** from Home page 1.
5. **Notes widget 2x1**: "scratch.md1 min ago" with no gap; at font 2.0 the file label vanishes and
   the age clips.
6. **Layout editor at 360 dp + Large text**: "#2 Bea…" in the dock picker and "La…" by One layout /
   Home's own.
7. **Split gap slider** (row labelled Frost): 0/13/24 dp persist but the gutter looks identical in
   the miniature and a 4-pane window; may be the checker's drag.
8. **Settings does not reopen on its last page after an upgrade install** (old build on Terminal ›
   Status bar, install, `am start` → root); recents route untried.
9. **Landscape sessions drawer, keyboard hidden**: Saved tab's Add to current and Replace shrink to
   a 7 px sliver; keyboard up, the list is empty.
10. **A–Z strip default mismatch**: Drawer layout shows Left checked on a fresh profile while the
    strip draws on the right.
11. **Text over the wallpaper**: Home icon labels (white with a grey shadow box) read as a smudge on
    a light wallpaper and clip "Calendar"; folder title and "Remove from folder" bubble are dark on
    the dimmed wallpaper, the bubble overlaps the title on a disabled app; the folder popup at font
    1.3 + Large labels breaks words mid-word and clips its last row; the Home lock message is dark
    on a bright wallpaper.
12. **Display window draws over an open app drawer**; after × on the selected Display chip the chip
    stays "Display", floats draw over a blank area and an extra float chip appears.
13. **Tour**: the Workspace keyboard card shows no Skip or dots after 5 s and clips after one body
    line; with split panes the "Tap ?" card points at pane controls.
14. Smaller: Save sessions' Save sits under the keyboard (Enter works); the "Saved" toast covers the
    Sessions drawer's +; the pinned-app menu hides Add to Home below the fold; LCD clock face bottom
    segments look clipped; dragging an app onto a 14-app folder swapped cells instead of merging
    (possibly injected-drag technique); overlapping floats show each other's text through the glass
    (by design?).
