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

- **A category sort survives a second tap (11-10, #195).** Robolectric test only. Owed · pong or
  emulator: Settings › Dock › drawer categories, start Sort apps into categories, tap the row again
  mid-run: the progress notification stays, the run finishes and posts its result.
- **Voice settings fixes (11-10, #232).** Unit tests for the picker only. Owed · pong: Settings ›
  On-device AI › Voice input at font scale 1.5: the Speech engine segments read "Android system"
  and "On-device" whole on one line; at 1.0 every two-segment pill looks as before. With Base GPU
  in use, Runs on reads "Built for the GPU: uses less battery and CPU" (another model: the old
  line). Model centre › Functions › Voice typing: Base GPU's line is its GPU advice, not "Made for
  bigger phones". Installed › KittenTTS ⋯ › Voice settings opens Read aloud.
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
  keyboard still reach X. In overlay mode the display runs to the screen's bottom (or the
  Floating card's bottom inset) under the keyboard and the strip under the pill, and its size
  (`xdpyinfo | grep dimensions` in X) is the same with the keyboard up and down; the extra keys
  lie over its foot with the keyboard down. Resize mode: the display still ends above the
  keyboard. Crossing Terminal ⇄ Display with the keyboard up lands without a jump. With the
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
