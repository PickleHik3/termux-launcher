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
- 2026-10-24 · commit "feat(gestures): the drawer swipe is always on; quick reply gets its own
  switch" · Home › Gestures no longer lists Swipe down for app drawer; a gesture's choices always
  include Open drawer; swiping down on the apps row/dock opens the drawer (also after updating from
  a profile that had it off) · emulator.
- 2026-10-24 · same commit · Home › Gestures has "Swipe up on app icons for quick reply", on by
  default: on, swiping up on a dock icon with a notification dot opens the reply card as before;
  off, the same swipe does nothing special (no card; bottom-dock icon behaves like an unbadged one,
  and on a rail the sideways drawer pull works from the badged icon); flip it back on and the card
  returns without a restart · emulator.
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
- 2026-10-25 · commit "feat(clock): flip cards carry end pins at the split line" · a Flip Clock
  widget at every size, 12h and 24h, light and dark theme, and the expanded status bar's Flip:
  each card shows a small tab on its left and right rim at the split line, sized with the card, in
  the rim's ink; no line or mark crosses a digit. With it, commit "keep the LCD period box inside
  the column the fit measured": an LCD widget at 2×1, 12h, shows its whole period box · emulator.
- 2026-10-25 · appearance fixes ("the terminal menu's Appearance opens the editor at rest", "every
  value slider is a strip column", "Home's card opens on Home's own glass") · (1) long-press a
  terminal › More › Appearance: the editor opens with no card, frame on the Workspace, never a
  Settings page; same over the display window. (2) Every card: no horizontal slider except the
  Look's, no More row, every row reachable by scrolling; the Keyboard card's strip wraps onto two
  lines; at font scale 1.3 and 2.0 every rotated legend reads whole. (3) Home card: Frost, Opacity,
  Tint at the top read Home's values; moving them changes the widgets, status bar and dock on Home
  in the frame at once and live after Done, the Workspace unchanged; Reset Home puts them back ·
  emulator, then pong (Home opacity 92, blur 0).
- 2026-10-25 · drawer keyboard (commits "lay the stack out for a keyboard raised under the open
  drawer", "reveal the built-in keyboard the open raised") · Built-in keyboard + Open keyboard
  automatically: the keyboard shows all four rows right above the nav inset and the list's last row
  clears it; on close the plane lands on the dock above the keyboard, then the keyboard goes down
  with the dock, no hop; same on the Workspace where the pass resizes the terminal once; Floating
  and Docked, no visible jump from the one pass under the plane · emulator.
- 2026-10-24 · "fix(terminal): a pane under a terminal effect wears a bezel" · turn on CRT, CRT
  amber and TFT: every pane wears a frame (12 dp at the sides, 16 dp top and bottom, 10 dp inner
  corners) of its own glass with a hairline round the screen. Scanlines and bend stay inside the
  frame, and no text is cut off at the middle of any edge: run `seq 1 200; printf '%0.sX' {1..300}`
  and check the first and last column and row are whole. Turning the effect off removes the frame
  and the grid gets its columns back in one step. Check one pane and a 2×2 split, keyboard up and
  down, Floating and Docked, light and dark · emulator (API 33+).
- 2026-10-24 · same commit · Terminal glass Opacity 0 with an effect on: the frame is opaque in
  the theme's surface colour, light and dark · emulator.
- 2026-10-24 · "fix(chrome): draw Home's chrome without the terminal effect" · with CRT on, Home's
  dock icons, status bar and widgets show no scanlines; on the Workspace the status bar, dock,
  extra keys and keyboard do. The chrome switches when the wall lands, both ways, and stays on
  the half it landed on · emulator.
- 2026-10-24 · commits "feat(home): carry an app from the drawer onto a Home page" and
  "feat(drawer): an app goes to Home only when carried out through the drawer's top" · open the
  drawer from Home and from the Workspace, long-press an app and slide: the drag stays the
  drawer's, so dropping it on another app makes a folder, on a folder fills it, and a folder tile
  still moves, as before. Carry the app up past the top of the app list (onto the search pill) and
  either rest there ~¼ s or go a thumb's width further: the drawer closes under the finger, Home
  comes in and the icon keeps its size and place under the finger with no jump (it takes Home's
  size only as it settles), with a white cell outline under it, and follows the finger while the
  drawer is still closing. Dip back into the list before the ¼ s and nothing happens. Then on
  Home: let go over a free cell and the icon settles there; over a taken cell it goes to the
  nearest free one; rest at a side edge for ~350 ms and the page turns (past the last page a new
  one is made and kept only if the icon lands there); a full page shows a red outline and "No room
  on this page."; let go over the dock or off the grid and nothing is added. Opened from the
  Workspace, a still finger during the wall's turn may let the icon ride the page until it moves.
  With Home's layout locked the app never leaves the drawer · emulator.
- 2026-10-24 · commit "feat(drawer): the search keyboard slides away as the list scrolls" · open
  the drawer with the built-in keyboard revealed under the search (a pill tap), then scroll the
  vertical list down: nothing moves for the first ~24 dp, then the keyboard slides down with the
  finger until it is gone and the list grows to the full height without jumping; scrolling back up
  does not bring it back, a pill tap does. A scroll stopped with the keyboard less than halfway down
  springs it back up. With the Android keyboard (and with "Use system keyboard") the phone's
  keyboard goes at the threshold and the pill brings it back · emulator.
- 2026-10-24 · commit "fix(drawer): the search raises the built-in keyboard on every place" · with
  Settings › Keyboard › On-screen keyboard = Built-in and the drawer's "Open keyboard
  automatically" on, open the drawer on Home (keyboard down) and on the Workspace (keyboard up and
  down): the built-in keyboard rises through the plane under the search and the phone's keyboard
  never shows; typing filters; closing the drawer puts down a keyboard the drawer raised and keeps
  one that was up before. With Android selected, and with "Use system keyboard" on, the phone's
  keyboard shows instead · emulator.
- 2026-10-25 · commits "one Frost slider…" and "glass rows as a strip of vertical sliders…" ·
  the Status bar, Terminal and Dock cards open on a strip of upright sliders reading "Frost · n%",
  "Opacity · n%", "Tint · n%" (the Terminal's adds "Text size"), the Keyboard's on Frost, Opacity,
  Key opacity, Tint, Key corners, Key spacing; no Blur or Grain row anywhere; a moved slider shows
  the dot after its legend and Reset takes it away; Height, Corners and Margin stay sliders across
  the card; at font scale 1.3 and 2.0 every legend reads whole beside its track · emulator.
- 2026-10-25 · same commits · the Appearance card at a Look stop shows no strip; tap Custom: the
  slider holds Custom and "All surfaces" shows Frost, Opacity, Tint, Vibrancy, Text size (with Edge
  and Depth on Android 13 with a launcher wallpaper); Frost changes every surface on the half the
  frame shows and not the other half; a Look stop puts the strip away · emulator.
- 2026-10-25 · commit "every door opens the one editor…" · the lone terminal pane's corner tab
  Appearance opens the editor on the Terminal card (no picker); Home's tab opens the Home card; the
  display window's tab opens the editor at rest; the terminal menu's Set wallpaper and Home's
  Wallpaper action open the Appearance card with Wallpaper in view; Change raises the picker, Back
  puts it down; Photo… and back returns to the picker over the editor · emulator.
- 2026-10-25 · commit "the Glass widgets switch applies to every widget" · with a Clock widget whose
  own Glass is On (its settings), the Home card's Glass widgets off makes it solid at once and its
  settings read Page; Undo gives it On back; the row's note reads whole at 1.0 · emulator.
- 2026-10-25 · commit "the Custom stop on one half never writes the other half" · halves on
  different Looks: on the Workspace move the Look to Custom and back, slide to Home and back,
  change Theme, Done: Home's dock and status bar look as before. Pong still holds Solid's numbers
  on Home from before the fix: the developer picks Home's Look once (or Copy to Home) · pong, with
  the developer's go-ahead.
- 2026-10-24 · #159 editor (commit "feat(surfaces): the editor rests as the frame and the
  editor bar") · open Appearance from a corner tab on a window and on Home: the frame grows to about
  ×0.8 under a 16 dp top line, every surface wears a faint 1 dp outline, the half line reads
  "Workspace" or "Home" with the right dot long, the bar (Portrait · Undo greyed · Done · palette)
  stands 8 dp over the gesture area, nothing overlaps at font scale 1.0 and 1.3 (fix commit "pose
  the frame whole above the half line": the whole frame stands above the half line, which stands
  above the bar; on Home with no keyboard up too) · emulator.
- 2026-10-24 · same commit · a plain horizontal swipe on the frame slides the real wall to the
  other half inside the frame, the half line follows (fix commit "slide the frame into the real
  Workspace": the Workspace shows its window and terminal; with no window in the row the swipe
  starts the first shell and the frame lands on it; a tap on that terminal opens the Terminal
  card; a Workspace-only Tint shows on the Workspace's dock); a hold on the dock lifts it on the layout
  drawing and a drop on the top edge moves the live dock there; a tap still opens the card · emulator.
- 2026-10-24 · #159 cards (commit "feat(surfaces): one card per object") · tap the dock: the card
  rises to its half detent with Opacity, Tint, Height, Visible, Position; the frame shrinks to
  about ×0.6, whole (its top under the status bar's line, its bottom over the dots and the word,
  which stand over the card, never over the keys); only the dock wears the 2 dp ring; drag or
  fling the handle or the header up: the card goes full and the frame to ×0.44, following the
  finger; a tap on the handle flips the detents; ✕, a tap on the frame and Back each put the card
  down; at font scale 1.3 and 2.0 the frame stays whole and below the status bar, the last row
  scrolls clear of the gesture area and three-word segments turn into a list · emulator.
- 2026-10-24 · fix commit "hand Back's press to the client when a view has no emulator" · open
  the Home card from Settings › Home › Appearance, move a slider, hide the dock: Back puts the card
  down, a second Back asks Keep editing / Discard / Save; Discard: the dock is back, the slider's
  value is as before (both halves, `layout.*` keys of every layout as at open); the same on the
  Workspace; Save and Done keep · emulator.
- 2026-10-24 · fix commit "carry the editor's session across a theme relaunch" · Appearance card,
  pick a Look stop, then Dark: the activity relaunches into dark and the editor comes back with
  the Appearance card up and the Look still picked; Undo puts the theme and the Look back (a second
  relaunch, the editor again comes back); Discard on leaving does the same · emulator.
- 2026-10-24 · fix commit "polish" (editor card rows) · the Dock card: Tray / Beads / Strip show
  glyph and word inside their outlines at 1.0 and 1.3; a slider at its maximum keeps the thumb
  inside the 16 dp margin; the Style row's icon stands 16 dp in and "Floating" is centred in its
  segment, no check mark in either · emulator.
- 2026-10-24 · same commit · Visible off on the dock: the dock goes, a dashed ghost stays on its
  edge (with the A–Z row still up, the dashed target spans the row and the apps' band over it), a
  tap on the ghost opens the Dock card, Visible on brings it back; the same for the status bar and
  the keyboard; the Keyboard card's Visible reads on exactly while the frame draws the keyboard,
  and on Home without always-on the editor opens with the keyboard down and its ghost · emulator.
- 2026-10-24 · same commit · every row of the Appearance card writes and shows live: Look stops,
  Wallpaper Change (the picker over the editor, Back returns, Done closes keeping it), Theme
  (the activity relaunches and the editor comes back with its card and Undo), Icon pack (tiles
  open under the row), Monochrome, Text size, Wallpaper colours, Contrast, Full screen, Minimal;
  Undo puts every one back · emulator.
- 2026-10-24 · same commit · the Home card on Home: Icon size, Label size, Glass widgets, Two-line
  labels, Label shadow, Folders, Numerals, Page dots, Folder limit and Own layout change the live
  Home at once; Reset Home puts them back as shipped · emulator.
- 2026-10-24 · #159 halves (commit "feat(surfaces): cards write the half the frame shows") · on
  Home, the Dock card's Opacity changes Home's dock only; swipe to the Workspace: its dock is as it
  was, the card's dot is gone; ⋮ Copy to Workspace makes them equal; Reset dock puts Home's back on
  its Look; the Look slider moves one half's stop; Undo after edits on both halves puts both back
  · emulator.
- 2026-10-24 · #159 doors (commit "feat(surfaces): every door opens on its object") · the corner
  tab on Home opens the Home card, on a window the Terminal card; the terminal's long-press
  Appearance opens the Terminal card; the palette's Appearance tool opens the page's card; a
  Settings deep link with `dock` opens the Dock card · emulator.

- 2026-10-24 · widgets on glass default (commit "feat(widgets): glass is on by default") · on an
  install upgraded from a build that never stored the switch: widgets that never had a Glass choice
  now wear the Look's glass over the wallpaper; one set to Off or Bare keeps its solid or bare
  card; turning Widgets on glass off returns the unset ones to the solid card. A fresh install
  opens with glass widgets.
- 2026-10-24 · clock widget meta column (commit "feat(clock): widgets stack AM/PM over the
  seconds") · on the emulator with a 12-hour clock widget in each face (Numerals, Slab, Minimal,
  Tape, LED, LCD, Flip) at 2x1, 2x2 and 4x2: the period stands over the seconds like the expanded
  status bar, nothing clips at the card's right edge; seconds off keeps the period on the digits'
  row; a 24-hour clock shows the seconds alone and nothing shifts.
- 2026-10-24 · #159 model (commit "feat(chrome): the dock, status bar and keyboard wear the
  look of the half they stand on") · with Home's dock opacity set apart from the Workspace's
  (`run-as` + an `app_bar_opacity_home` / `surface_base_opacity_home` key, edited before a
  force-stop), each half at rest shows its own dock, keyboard and status bar glass; an untouched
  install looks exactly as before · emulator.
- 2026-10-24 · same commit · a slide Home ↔ window between halves whose dock looks differ: the
  stack dips and comes back up in the arriving half's glass, never changing while it shows; the
  status bar takes Home's glass only as the slide lands on Home, Docked and Floating, keyboard up
  and down · emulator.
- 2026-10-24 · same commit · a Home glass widget wears Home's Look (Base) and not the
  Workspace's once the two differ · emulator.
- 2026-10-24 · #159 Settings filing (commit "feat(settings): Appearance opens the editor from the
  root; Shared becomes Everywhere" and the six after it) · the Settings root reads Your launcher
  (Home, Apps & dock, Terminal, Linux apps) · Everywhere (Appearance, Keyboard, Notifications,
  On-device AI) · System (General); tapping Appearance opens the Appearance editor over the
  launcher (no page), Done comes back to Settings; the same on the sw600dp two-pane list (a tablet
  AVD or `wm size 1600x2560`) · emulator, staging build.
- 2026-10-24 · same round · Settings search: "icon pack", "app theme", "widgets on glass" and
  "full screen" each list a hit under Appearance, and tapping it opens the editor; "linux apps"
  lands on the Linux apps section; "vibration" lands on General only · emulator.
- 2026-10-24 · same round · Home › Gestures shows the five Home gestures, then Double-tap to lock
  (opens its method page), Swipe up on app icons for quick reply and Swipe on the terminal to switch
  windows; flipping each switch there sticks across a reopen and takes effect on the launcher
  (terminal swipe off: a sideways
  swipe on a window's text no longer changes place) · emulator.
- 2026-10-24 · same round · Apps & dock › Linux apps in the drawer opens Linux apps, Back returns
  to Apps & dock; the Drawer layout page's first row reads Grid; Linux apps › Fixes opens the old
  Troubleshooting rows · emulator.
- 2026-10-24 · same round · General › Diagnostics shows Log level and, with Termux:API installed,
  Termux:API log level (changing it is kept by Termux:API); without Termux:API that row is absent;
  Permissions & services › Termux:API › Diagnostics lands on that row · emulator (install
  Termux:API for the second half).
- 2026-10-24 · same round · About & help starts with Help, which opens the in-app help, Back
  returns to About & help; General has no Help row; Keyboard shows Hardware keyboard in its own
  group; Terminal › Text & display has no Full screen; Notifications has no Notification access
  row · emulator, also at font scale 1.3.
- 2026-10-23 · #142 phase 2 (commit "feat(place): the crossing morphs the dock's row and holds
  every reader") · the audit's slide clips re-shot, frames recorded: Home → window and window →
  Home, Docked and Floating, each of Tray, Beads and Strip → the pinned icons change size and
  spacing smoothly over the whole slide with no step at the landing (the 118 → 143 px snap gone:
  measure the icon in the last slide frame and the first rest frame, equal within 1 px), the
  Drawer tile fading with its half rather than leaving at the end, no blurred flash at the pane's
  top as the last band settles, Strip's clock not leaving in the last frames, the pane not
  shortening after the landing, the keyboard travelling with the half that has it open · emulator.
- 2026-10-23 · same commit · a diagonal thumb at 35° off the horizontal, under four slops of
  travel (about 32 dp), does not turn a Home page or slide the wall from the bottom gap or the
  status bar border; it pages once it carries on that way well past that; at 25° it pages at the
  usual slop; a widget that scrolls vertically scrolls more readily under a diagonal · emulator.
- 2026-10-23 · same commit · the drawer's fling threshold is the same in dp at 2.6 and 3.5
  density (`wm density`, force-stop after each): a short quick pull off the dock opens it at the
  same thumb speed on both · emulator.
- 2026-10-23 · #151 (merge d9b5c4fb3) · the App menu on a dock-folder member, in the black theme
  and with the scheme chrome, near the left edge, with 7+ shortcuts (collapse), and the
  open/close motion in a frame recording (dock icon light/dark and a drawer cell at the top
  right seen) · emulator.
- 2026-10-23 · same commit · font scale 1.3 and a small density: labels on one line, ellipsized,
  rows still 52 dp; an app with many shortcuts on a short landscape screen scrolls inside the card
  rather than running off it · emulator.
- 2026-10-23 · same commit · a window chip tapped on Home mid-slide, and a reversal past halfway:
  the icons run back the way they came with no jump, the pane's bottom never stays clipped by the
  dock on the window that comes back, and the keyboard pre-rolled for the slide goes away when the
  wall returns; landscape with the rail on one half: the rail and the row each as laid out, no
  icon scaling · emulator.
- 2026-10-23 · same commit · a frame measurement of ten slides each way (gfxinfo framestats) on
  the arm64 staging build against the #133 baseline (median 17 ms, 90th 24 ms, 99th 57 ms): no
  worse, and no slide frame re-renders the row (a perfetto trace shows no `Wall.*` layout of the
  apps row between the first frame and the settle) · pong, with the go-ahead.
- 2026-10-23 · #146 (merges 56fcaf1cb, b1b829854) · landscape rail, seen 2026-10-23 on the
  emulator except: the two ticks beside the rail follow the scroll (second warm at the end), a
  tap on a most-used app at the column's end launches it, and in Floating the rail never
  stands under the A–Z card; a long press still opens the menu and a sideways drag still
  pulls the drawer · emulator.
- 2026-10-24 · #146 round 3, commit "fix(dock): the rail's ticks follow the column as drawn" ·
  Home, landscape, 7 pins + most-used, Drawer tile on: the two ticks beside the rail are there
  at the top and at the end of the scroll (second warm at the end), including after an icon or
  icon-pack change while on Home; at the true end (a further drag moves nothing) the last
  most-used icon is whole with its air below it, then `dumpsys activity top` shows
  `place_apps_bar_indicator` as `V` (round 2's dump at the true end already showed the column
  12 slots of 152 px, viewport 4 slots, end aligned; the earlier cut-icon shot reads as a
  scroll stopped 60 px short of it) · emulator.
- 2026-10-23 · commit "refactor(place): resolve the arrangement once per page and orientation"
  (#141) · the 2026-10-23 consistency audit's cells re-shot: Docked and Floating × Tray, Beads and
  Strip × Home and a window × keyboard up and down, portrait and landscape → the same chrome as
  the audit's shots at rest (no Element gained or lost, same Style, same dock) · emulator.
- 2026-10-23 · same commit · a slow drag Home → window and back in Docked and in Floating, with
  Tray and with Beads → no Element appears or disappears mid-slide or at the landing that the
  audit's clips did not show; a Style or Dock design change in the Layout editor shows on the
  next pass on both halves · emulator, frames recorded.
- 2026-10-23 · #143 (merge f19d7e1ff) · the status bar at rest with the capsule, folded and open:
  row height, foot and slot the same as 52831bcac (Docked Tray and Floating seen) and the row
  does not jump when a fold animation ends · emulator.
- 2026-10-24 · commits "feat(settings): group each section's rows into one rounded tonal
  card", "lead every settings row with a Material Symbols icon", "fill the segmented controls and
  the section filter" · Settings root, Home, Keyboard, Terminal › Linux apps › Input, General, in
  light, dark and black and with the launcher's scheme chrome on: each section's rows sit on one
  rounded card a step off the page colour, 24dp outer corners, a faint hairline between rows and
  none above the first; the search field and status cards stand alone; pressing the first or last
  row of a card ripples inside the rounded corners. Every row leads with a 24dp icon, titles
  aligned across the card; an off row dims its icon with its text. Segmented rows (Keyboard ›
  On-screen keyboard, Look › App theme) are filled: the chosen segment on the primary container
  with a check, the rest tonal, small gaps, no outline; General's section filter chips match
  them. Repeat at font scale 1.3 (`settings put system font_scale 1.3`, then force-stop): no
  clipped title or segment label (segments stack instead), rows grow · emulator.
- 2026-10-24 · Set up Linux apps guide (commit "feat(settings): Set up Linux apps becomes a stepped
  guide") and the Input copy · Settings › Linux apps › Set up Linux apps opens a full-screen guide:
  Termux build shows Source, then (A full Linux) Pick a Linux, Starter apps, Copy the command,
  Graphics, with the pill line counting 5 or 4 (Termux's own apps skip the Linux pick); Debian
  wears "Recommended" and the badge drops under its name at font scale 2.0; Next reads Done on
  Graphics; Copy puts the command on the clipboard and Done closes Settings with the notice; Back
  on step 1 leaves; the Linux apps row then reads "Debian with a browser and a file manager"
  (and "Not set up yet" on a fresh install); Graphics opens Graphics acceleration and its pick
  shows on return. Nothing clips at font scale 1.3 and 2.0, light and dark, scheme chrome on.
  nix build: one page with the advice and Read the docs, no progress line or footer; VAJ build:
  Termux's own apps offer a file manager only. Input page: "Keyboard pops up", "Keyboard over the
  window", Trackpad · Touch · Direct with a one-line summary that follows the pick · emulator,
  one install per edition.
- 2026-10-24 · "fix(glass): draw a lone band as a whole card under Fancier Glass" · Fancier Glass
  on (bend 21, edge width 14), Floating, Corners 3, dock rows hidden, keyboard down: the extra-keys
  bar alone has no chevron at either end and no line across its middle; same with the keyboard
  up (the card under it), in Docked, and at a capsule Corners; a tall dock's small corners show no
  crease running in from the corner · pong (the emulator never showed it).
- 2026-10-25 · terminal effect at terminal Opacity 0 (emulator b-bezel-opaque.png) · the scanlines
  fade from the pane's interior and are faint on the bezel while the bezel should carry none; check
  whether the effect rides the glass layer and should instead sit over the text at any opacity ·
  emulator or pong.
- 2026-10-24 · "fix(glass): hold the refraction's corner radius to half its rect" · Floating,
  dock rows hidden, keyboard down, extra keys alone: the pill's ends are round, with no notch
  folding in and no line along the middle; same with the keyboard up, light and dark. The
  emulator never showed the defect, so only pong can confirm the fix · pong.
- 2026-10-24 · bezel commit · how the CRT bezel looks over a real wallpaper with fancier glass on:
  does it read as a monitor frame, not a second pane? · pong.
- 2026-10-24 · commit "feat(home): carry an app from the drawer onto a Home page" · the drawer to
  Home hand-over feels like one gesture: no flash or jump of the icon as the drawer closes and the
  wall slides, and the drop settle reads like Home's own · pong.

- 2026-10-24 · Appearance + Settings rework (branch feat/appearance-settings-rework, on pong as
  1.1.0-pre.6+dev.f076a2dbe) · taste: the one-rule editor (tap a surface → card; palette glyph →
  Appearance card; swipe the frame between Home and Workspace; hold-drag moves), the per-half dock
  look while sliding (the stack dips and comes back in the arriving half's glass), the M3 settings
  pages in your scheme, the clock widgets' stacked AM/PM, glass widgets by default · pong.
- 2026-10-24 · same · known small items from the emulator: Settings reopened after a force-stop
  while inside the Set up Linux apps guide lands on the root, not Linux apps; after Done with a
  theme change the Settings screen behind the editor keeps the old theme until reopened; the
  "Windows restored" toast can overlap the editor bar right after a restart; the status bar's
  hidden ghost was not exercised · pong or emulator.
- 2026-10-24 · #159 editor · the card's rise, the frame's shrink and the half line moving with the
  card read as one motion; a header drag between detents follows the thumb; no dropped frame while
  a slider drags on the dock card · pong.
- 2026-10-24 · same · the hold-and-drag on the live frame: the switch to the layout drawing under
  the thumb is acceptable, or a drag on the live frame itself is wanted · pong.
- 2026-10-24 · #159 model · the dip a differing look now causes on every crossing (the same dip
  a differing icon size gets) is acceptable to the eye, or the status bar's change at the landing
  on Home reads as a snap · pong.
- 2026-10-24 · finding (emulator, #152) · a 35° drag from the bottom gap, 500 px across, did not
  turn the page under injection though the ramp asks ~2.6 slops: a thumb at about 35° on pong turns
  the page, and one past 60° never does · pong.
- 2026-10-24 · finding (emulator) · landscape window with the keyboard up: the terminal was a ~230 px
  strip with blurred, unreadable text and the rail showed only the Terminal icon (evidence2/50-
  landscape-window.png): confirm it is the emulator, or a real landscape-with-keyboard regression ·
  pong, rotate with the keyboard up.
- 2026-10-23 · finding (emulator, #151) · the App menu showed only after the injected finger
  lifted, not at the long-press threshold (icon lifted at 2 s): confirm it is the injection, or a
  real wait for release · pong, a hand on the icon.
- 2026-10-23 · finding (emulator, #146/#142) · after changing the Dock design to Beads in the
  Layout editor on Home, the dock rested on the most-used page instead of the 7 pins: leftover
  emulator state, or the resting page lost on a design change · emulator, fresh install.
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
- 2026-10-23 · #150 (commit "fix(windows): a new window takes the keys…") · a Bluetooth keyboard
  on the Surface, splits on: Ctrl+Alt+C, then type at once → the keystrokes land in the new
  window's shell with no tap; the same from the + chip, and from a display window in front ·
  Surface, hand on the keyboard.
- 2026-10-23 · #148 (commit "fix(keys): Ctrl+Shift+V pastes…") · a Bluetooth keyboard on the
  Surface: copy text in another app, Ctrl+Shift+V in a terminal window pastes it (confirmation
  notice if the tool asks for one, then the text in the shell); Ctrl+Alt+V still pastes with
  splits off and splits with them on · Surface, hand on the keyboard.
- 2026-10-24 · 68fba2325 (commit "feat(appearance): Label size and Folders stand inline like
  Numerals…") · Home card, at font scale 1.0, 1.3 and 2.0 → Label size and Folders stand on one
  row beside their name like Numerals, with their words smaller; no word is cut or ends in "…";
  where a scale cannot fit them they wrap onto their own line (never truncate) · emulator.
- 2026-10-24 · 68fba2325 · the Home card has no Folder limit row; a folder that held 20 apps keeps
  them and takes no more, one under 16 takes more up to 16; Settings › Home › Folders has no Max
  folder size row · emulator.
- 2026-10-24 · 10aced449 (commit "feat(appearance): hidden items as a stack of cards beside the
  editor frame") · open the editor on Home and on the Workspace, nothing hidden → the frame stands
  right of an empty left column (smaller than before, never off screen), at font scale 1.0, 1.3
  and 2.0 and with a card up at both detents · emulator.
- 2026-10-24 · 10aced449 · hide the status bar (Status card Visible), pinned apps, extra keys and
  the keyboard on the Workspace → one card each in a stack at the top left, all one size, each
  label whole inside its card (no "…", no clipping) at font scale 1.0, 1.3 and 2.0; the frame no
  longer draws dashed ghost bands · emulator.
- 2026-10-24 · 10aced449 · tap a card → the item comes back where it was and its card (Status,
  Dock or Keyboard) opens; drag a card into the frame and let go → the item comes back, the card
  does not open; drag a card and let go outside the frame → it springs back, nothing changes ·
  emulator.
- 2026-10-24 · 10aced449 · hold the dock or the status bar in the frame and drag it over the left
  column → a dashed "drop" zone shows, goes primary-coloured while the finger is over it, and the
  drop hides the item as a new card; a drop elsewhere moves it as before · emulator.
- 2026-10-24 · 10aced449 · Home and the Workspace share the layout (Home's own layout off): hide
  the pinned apps on Home → the Workspace still shows its dock, Home's own layout reads on in the
  Home card, nothing else on Home moved; then hide the pinned apps on the Workspace first with
  Home's own layout off → Home keeps its dock · emulator.
- 2026-10-24 · 10aced449 · the dock's Search field off (Dock card switch) and Drawer tile off
  (Dock design Tray) → a Search field and a Drawer tile card in the stack; tapping one turns it
  on again in the dock of that half only; Undo and Discard put the stack back as it was ·
  emulator.

- 2026-10-25 · commit "clock sheet: Show date and Show next alarm" · the Clock widget's gear at 2×2:
  two new rows after Show seconds (calendar and alarm glyphs, same card style, both on); turning
  Show date off drops the date and leaves the alarm under the face; turning Show next alarm off
  drops only the alarm; both off leaves the face alone, centred; the glyphs read at 24dp in both
  themes; existing clocks show both lines unchanged · pong or emulator (set an alarm first).
- 2026-10-25 · commit "flip: the AM/PM and the seconds share one centre axis" · Flip face, 12h,
  seconds on, in the widget at 2×1/2×2 and in the expanded status bar: "AM"/"PM" is centred over
  the two seconds digits, not left of them; with seconds off the period sits where it did · pong
  or emulator.
- 2026-10-25 · commit "flip end pins at the old clips' size" · Flip Clock widget, the expanded bar
  and the dock: each card shows a clear small rectangle at both ends of the split line (3.2dp by
  7.4dp at card scale, a pixel past the card's edge), under the digit so none crosses a glyph; no
  leaf shading at the fold; the flipping leaf carries no pins and they stay put between flips ·
  pong.

## Developer
- 2026-10-24 · commit "feat(editor): live drag over the real frame…" · Appearance editor, hold
  the dock and drag it over the top, side and bottom slots: the frame stays the real launcher (no
  grey tiles, clock outline or icon dock), the dock's own picture follows the finger slightly
  raised, the status bar and other bars slide aside smoothly (no stutter, no terminal reflow under
  the finger); drop: the bars settle on their real places with no flash of the old layout and no
  jump at the hand-over; release off any slot: everything slides back. Both Styles, glass on ·
  pong (no emulator).
- 2026-10-24 · same · with nothing hidden, at rest: the frame is centred with no rail; hold an
  element: the frame slides right in about 0.2s at the same size and the rail shows only its empty
  slot in a dotted border; drop on an edge: it slides back to centre; drop on the rail: it stays
  offset (and shrinks slightly to the reserve's scale). Look at the frame's right edge while
  offset at rest: it runs about 10dp past the screen on pong · pong.
- 2026-10-24 · same · with something hidden: tap the dock (card up), hold, drag, drop: the frame
  never moves or rescales and the card stays up · pong.
- 2026-10-24 · same · drag the A–Z card out of the rail: over the rail it stays a chip; over a
  slot in the frame the chip goes and a tinted shape of the index's size stands at the slot while
  the bars move aside; drop: the index appears there; let go over no slot: it stays hidden. The
  keyboard and Search field cards still restore where they were · pong.
- 2026-10-24 · commit "feat(layout-canvas): slim drop markers…" · hold the A–Z index, then the
  status bar: every slot a slim dotted rounded marker on its gap line, none touching another or
  the frame's edge, top and bottom markers starting after the side strips where those show, side
  strips only for an element that can stand on a side; the hovered slot opens to the element's
  size and corners, solid with a light tint · pong.
- 2026-10-24 · commit "feat(editor): the hidden rail's border hugs its cards…" · two elements
  hidden, hold a third: the rail's dotted border wraps both chips and one empty slot under them
  with even air on all four sides, ends there (not down at the bar), and the chips do not move
  when it shows; finger over it, solid and tinted; drop there hides the element · pong.
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

Agent:
- 2026-10-24 · commit "feat(settings): a Graphics acceleration page where the user benchmarks
  and picks" · Settings → Terminal → Linux apps → Set up Linux apps: one Graphics acceleration
  row (no copy or benchmark rows), its summary "Automatic (…) · Not measured yet"; tap it: the
  phone's chip card, Automatic plus one card per profile with a radio, the software note, Draw in
  software with one switch per Linux app, Run benchmark floating at the foot; nothing clipped or
  overlapping in light, dark and at font scale 1.3 (force-stop after the font change) · emulator.
- 2026-10-24 · same · Pick Software, Back, force-stop, reopen the page: Software is still
  picked; `launcherctl x11 gpu` shows `"pick":"software"` and `in_use_env` with
  LIBGL_ALWAYS_SOFTWARE=1; open a Linux app from the drawer and logcat `X11LinuxAppRunner` says
  "with graphics software". Pick a profile that is not installed: the app opens with the notice
  "Your graphics choice isn't installed…". Switch one app on under Draw in software with
  Automatic picked: only that app's launch logs software · emulator.
- 2026-10-24 · same · Run benchmark: a terminal session runs termux-x11-gpu-setup, Settings
  closes and the notice shows; after it finishes, the page shows a score or outcome on every
  tested profile and "Last benchmark …" on the chip card (on the emulator only software is
  tried) · emulator.

Developer:
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
- 2026-10-24 · commit "fix(x11): the GPU benchmark installs the Vulkan wrapper from its pinned
  download" · Run benchmark: step 3 downloads vulkan-wrapper-android, installs it, step 5 tests
  vulkan-wrapper with a score or outcome; if it lost, step 8 removes it (`pacman -Q
  vulkan-wrapper-android` finds nothing) and no `vulkan-wrapper.*` folder is left in
  `$PREFIX/tmp`; if it won, it stays · pong.
- 2026-10-24 · commit "feat(settings): a Graphics acceleration row on Linux apps" · Settings →
  Linux apps: a Graphics acceleration row with an icon right under Set up Linux apps; tap it: the
  page opens, Back returns to Linux apps; search "graphics" finds the page under Linux apps; the
  guide's own Graphics link still opens it · pong.

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
