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

The 2026-10-23/24 emulator sweep (staging `1.1.0-pre.6+dev.8f303c274`, three Android 16
emulators) worked 332 Agent items: 116 passed, 148 passed on the part the emulator can show,
28 were stale, 14 failed. Its failures and end-user findings are in "Found in the sweep" below
and, with crops, at `~/reports/device-checks-2026-10-24/`. Items the sweep could not set up and
items superseded by the chrome unification, the widget redesign and the glass rounds were
dropped; what follows is what still owes a look.

## Agent
- 2026-10-25 · commit "fix(statusbar): the status row flows around the camera and clears the
  screen's corners" (#163) · with the status bar along the top and Full screen on, screenshots in
  portrait show a gap at the camera with the chips left of it and the readings right of it, and
  the first and last items clear of the screen's rounded corners; landscape shows no gap; Full
  screen off (row below the system bar) looks as before; at font scale 1.3 the chips ellipsize
  and nothing sits under the camera; Settings › Status bar › Camera cutout shows Auto/Custom/Off
  and the two sliders only on Custom · emulator with a punch-hole cutout (Display cutout:
  Punch hole), then pong.
- 2026-10-25 · commit "fix(graphics): skip the Vulkan wrapper on phones whose graphics driver is
  older than Vulkan 1.3" · pong (Vulkan 1.1): the Phone's Vulkan card says "Needs a newer graphics
  driver than this phone has" before and after a run; Run benchmark's sizes no longer count the
  wrapper, the run never downloads it (step 2 lists it as not tried) · pong.
- 2026-10-25 · commit "fix(x11): the virgl profiles draw through virgl, not Zink" · after a rerun
  the virgl card's renderer reads "virgl (Adreno …)", and a Linux app started on the virgl profile
  shows virgl in its GL renderer · pong (renderer already seen in the 12:55 run's results).
- 2026-10-25 · commit "fix(wall): the display window's borders stop at its edge" · display
  window in front with a Linux app maximised (a scrollable list at its foot, a menu bar at its
  top, a scrollbar at its right edge): a vertical swipe a row up from the bottom edge under the
  grabber scrolls the app and does not open the keyboard; one a row down from the top edge under
  the top grabber reaches the app and does not unfold the status bar; a sideways drag starting on
  the picture's outermost left/right pixels and in its last row reaches the app and does not page;
  a hold there is the app's. Then from the air outside the window: a sideways swipe on the bottom
  or top air and on the side air pages, a vertical swipe on the bottom air opens and closes the
  keyboard, a sideways swipe off the dock's top pages. Keyboard up and down (Overlay and Resize),
  Docked and Floating, portrait and landscape, alone in the row and beside terminal windows;
  a terminal window still pages from its last row and opens the keyboard from a row above its
  bottom edge · emulator.
- 2026-10-25 · commit "fix(home): the long-press sheet runs under the navigation bar and the
  Docked strip makes way for it" · with Beads or Strip: the sheet runs to the bottom edge, rows above
  the pill; open the drawer after the sheet closes: the strip still fades under the plane and comes
  back (Tray and Peek, the dismiss routes, Floating, keyboard up and three-button navigation seen on
  emu 10-26) · emulator.
- 2026-10-26 · commit "fix(peek): the Peek card rises from the screen's edge under Floating
  too" · Floating + Peek: card rises from the screen edge; keyboard up falls back to the Floating
  tray. Peek glass, Style Floating, keyboard down, Search field off: the card is the Docked one —
  inset about 3% from each side, top edge through the icons' middles, no gap under it, the strip
  under the gesture pill is its foot (inset as the card, reflection running on down to the screen
  edge), no shelf or shadow, no lower corners on screen; the terminal pane above stays a Floating
  card and does not overlap the dock, one pane and split. Then the in-app keyboard up: the full
  Floating tray (side margins, rounded corners, the gap above the keyboard's bottom, nothing under
  the pill) with no reflection left behind, and back down: the card again; note how far the icons
  move at that switch (the Floating gap, 6 dp). Same for Search field on, and the system keyboard
  lifting the dock. Margin 0 and Margin 48: the card's sides stay ~3% in and the end icons stand
  on it. Home and Workspace halves wearing different designs (slide between them), Floating ↔
  Docked, Peek ↔ Tray, light and dark · emulator, then pong.
- 2026-10-25 · commit "refactor(settings): edge colours follow Wallpaper colors" · Settings ->
  Terminal -> Text & display has no "Extend edge colors" row and the editor's Terminal card has no
  Edge colours row. With Wallpaper colors on, run a full-screen TUI with its own background
  (e.g. htop): the pane padding takes the edge colours; switch Wallpaper colors off (Settings or the
  editor's Appearance card): after the restyle the padding is plain again, in one pane and in
  split panes. A phone that had turned Extend edge colors off now follows the switch instead.
- 2026-10-25 · commit "refactor(settings): remove the Wallpaper alignment control" · on a build
  that still holds a stored non-100 alignment (or on Nothing OS), open Settings -> Appearance and
  the Appearance editor: no Wallpaper alignment slider or row in either, search "alignment" finds
  nothing, and the glass behind the surfaces still lines up with the wallpaper (self-drawn and
  system-drawn wallpapers both).
- 2026-10-25 · commit "feat(settings): grey Auto Keyboard until Linux apps are set up" · Settings,
  Linux apps, Input: before running Set up Linux apps, "Auto Keyboard" is greyed with "Set up Linux
  apps first"; once the guide's command has installed fcitx5 and you come back it is live and reads
  "Automatic Keyboard show & hide when you tap text input field"; its stored on/off is unchanged · pong.
- 2026-10-25 · commit "feat(wall): make the terminal or the display window Home from Edit pages"
  · Edit pages: the home button sits on every Home page, the first terminal card and the display
  card, and only one card is filled. Tap it on the first terminal: Settings › Home › Start in reads
  Terminal; on the display card: Start in reads Display and Linux apps › Start with the launcher is
  on; on a Home page: Start in reads Home. Pick each value in Settings and reopen Edit pages: the
  marker moved to match. With Display chosen, turn Start with the launcher off: Start in reads
  Terminal with a terminal open, else Home. Close the display card (let the undo lapse) while it
  is Home: same fallback. Close every terminal while the Workspace is Home: Start in reads Home.
  Force-stop with Start in = Display: the launcher comes back on the display window and the
  display runs · emulator, then pong.
- 2026-10-25 · commit "fix(wall): hold the wallpaper still while Edit pages is open" · with a
  managed wallpaper and Wallpaper parallax on, Home with three pages and two windows: open Edit
  pages and scroll from the first card to the last; the wallpaper behind the cards does not move
  at all, and the live card's glass (dock, status bar, pane slabs) stays aligned with it. Close on
  a far card: the wallpaper glides once to that page's place, like a page swipe, with no jump; a
  swipe started during that glide carries on smoothly · pong or emulator (portrait).
- 2026-10-25 · commit "fix(calendar): centre today's circle on its number" · Calendar widget month
  grid, today a two-digit date in the first (Sunday) column, then in the last column: the circle is
  centred on the digits and holds both with a margin; at font scale 1.3 and in the narrowest span
  it shrinks to its cell and touches no neighbour · pong.
- 2026-10-25 · commit "fix(statusbar): the strip behind the system bar keeps the pane's look
  after a trip to the Workspace" · Home and the Workspace with different status-bar looks
  (Opacity or Tint on one half only), wallpaper mode, Docked: swipe Home → Workspace → Home, and
  Home → part way → back: the strip behind the system status bar and the status pane below it read
  as one sheet of glass each time, in Home's look; the Workspace still shows its own · pong or
  emulator, keyboard down and up.
- 2026-10-25 · commit "feat(sound): one launcher-run sound server and media controls for
  every shell and distro" · in `proot-distro login debian`: the distro's
  `/etc/pulse/client.conf.d/termux-launcher.conf` names tcp:127.0.0.1:4713 and the cookie copy,
  `paplay` of a wav plays, `PULSE_COOKIE=/dev/null paplay` is refused · pong (no distro installed
  there on 10-25).
- 2026-10-25 · commit "feat(dock): a new install starts on Peek glass, existing installs keep the
  tray" · a fresh install (uninstall, then `adb install`, launch) opens on Peek glass
  (`layout.dock_design=peek_glass`); an update over an install that never picked a design
  (`adb install -r` over a build from before this commit) keeps the Glass tray and writes no
  `dock_design` key; an install that picked a design keeps it · pong with the developer's go-ahead
  (emulator banned).
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
  bars per half; in the editor a sideways swipe on the frame or the page label switches halves).
- 2026-10-22 · PR #99 (migrations 9–13) and the glass rounds · Upgrade from the shipped stable
  (1.0.6 / 1.1.0 release APK) over a real profile with a Home dock height, Corners or Margin set
  apart, a Prompt line or Taskbar dock style and a Custom Look → Home keeps its rows, Prompt line
  → Tray + Search field, Taskbar → Tray + Drawer tile, the Look keeps its stop and colour, no
  first-run tour · emulator with the release APK from the GitHub release (not on disk).
- 2026-10-22 · #133 review fixes · Floating Tray's shelf (contact shadow and glow) shows under the
  slab and goes with the keyboard up · pong: the emulator's CPU renderer drew none
  (`~/reports/device-checks-2026-10-24/`, A29).
- 2026-10-17 · commit "feat(panes): a glass titlebar on every float" · The float's strip glass
  matches the pane under it, Docked and Floating, glass on and off: same frost, tint and corners,
  wallpaper staying put under the strip while the float is dragged · emulator screenshots.
- 2026-10-24 · sweep · A sideways drag between two windows: the arriving window's page card drew
  blank, text only at the settle (E3-r4 `30-mid2.png`, E3-r2 `slide-w2w.png`) · pong, likely the
  CPU renderer.
- 2026-10-25 · commit "feat(clock): flip cards carry end pins at the split line" · a Flip Clock
  widget at every size, 12h and 24h, light and dark theme, and the expanded status bar's Flip:
  each card shows a small tab on its left and right rim at the split line, sized with the card, in
  the rim's ink; no line or mark crosses a digit. With it, commit "keep the LCD period box inside
  the column the fit measured": an LCD widget at 2×1, 12h, shows its whole period box · emulator.
- 2026-10-25 · drawer keyboard (commits "lay the stack out for a keyboard raised under the open
  drawer", "reveal the built-in keyboard the open raised") · Built-in keyboard + Open keyboard
  automatically: the keyboard shows all four rows right above the nav inset and the list's last row
  clears it; on close the plane lands on the dock above the keyboard, then the keyboard goes down
  with the dock, no hop; same on the Workspace where the pass resizes the terminal once; Floating
  and Docked, no visible jump from the one pass under the plane · emulator.
- 2026-10-24 · "fix(terminal): a pane under a terminal effect wears a bezel" · the rest of the matrix: a
  2×2 split, Floating, keyboard down, dark theme with a long line, CRT amber and TFT grid: each pane
  framed, scanlines only inside the screen, text whole at the edges (one pane, CRT, keyboard up,
  Docked, light seen on emu3 10-26) · emu.
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
- 2026-10-25 · commit "the Glass widgets switch applies to every widget" · with a Clock widget whose
  own Glass is On (its settings), the Home card's Glass widgets off makes it solid at once and its
  settings read Page; Undo gives it On back; the row's note reads whole at 1.0 · emulator.
- 2026-10-24 · fix commit "carry the editor's session across a theme relaunch" · Appearance card,
  pick a Look stop, then Dark: the activity relaunches into dark and the editor comes back with
  the Appearance card up and the Look still picked; Undo puts the theme and the Look back (a second
  relaunch, the editor again comes back); Discard on leaving does the same · emulator.

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
- 2026-10-24 · same commit · a Home glass widget wears Home's Look (Base) and not the
  Workspace's once the two differ · emulator.
- 2026-10-24 · same round · General › Diagnostics with Termux:API installed shows its row (Log level and the no-API case seen on emu3 10-26) · emu.
- 2026-10-24 · same round · About & help starts with Help, which opens the in-app help, Back
  returns to About & help; General has no Help row; Keyboard shows Hardware keyboard in its own
  group; Terminal › Text & display has no Full screen; Notifications has no Notification access
  row · emulator, also at font scale 1.3.
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
  audit's clips did not show; a Style or Dock type change on the editor's Dock card shows on the
  next pass on both halves · emulator, frames recorded.
- 2026-10-23 · #143 (merge f19d7e1ff) · the status bar at rest with the capsule, folded and open:
  row height, foot and slot the same as 52831bcac (Docked Tray and Floating seen) and the row
  does not jump when a fold animation ends · emulator.
- 2026-10-24 · commits "feat(settings): group each section's rows into one rounded tonal card" · under the launcher's scheme chrome (Wallpaper colours on): cards, icons and segments take the scheme (light, dark and font 1.3 seen on emu3 10-26; no Black theme any more) · emu.
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

- 2026-10-24 · same · known small items from the emulator: Settings reopened after a force-stop
  while inside the Set up Linux apps guide lands on the root, not Linux apps; after Done with a
  theme change the Settings screen behind the editor keeps the old theme until reopened; the
  "Windows restored" toast can overlap the editor bar right after a restart; the status bar's
  hidden ghost was not exercised · pong or emulator.
- 2026-10-24 · finding (emulator) · landscape window with the keyboard up: the terminal was a ~230 px
  strip with blurred, unreadable text and the rail showed only the Terminal icon (evidence2/50-
  landscape-window.png): confirm it is the emulator, or a real landscape-with-keyboard regression ·
  pong, rotate with the keyboard up.
- 2026-10-23 · finding (emulator, #151) · the App menu showed only after the injected finger
  lifted, not at the long-press threshold (icon lifted at 2 s): confirm it is the injection, or a
  real wait for release · pong, a hand on the icon.
- 2026-10-23 · finding (emulator, #146/#142) · after changing the Dock type to Beads in the
  editor on Home, the dock rested on the most-used page instead of the 7 pins: leftover
  emulator state, or the resting page lost on a design change · emulator, fresh install.
- 2026-10-23 · #150 (commit "fix(windows): a new window takes the keys…") · a Bluetooth keyboard
  on the Surface, splits on: Ctrl+Alt+C, then type at once → the keystrokes land in the new
  window's shell with no tap; the same from the + chip, and from a display window in front ·
  Surface, hand on the keyboard.
- 2026-10-23 · #148 (commit "fix(keys): Ctrl+Shift+V pastes…") · a Bluetooth keyboard on the
  Surface: copy text in another app, Ctrl+Shift+V in a terminal window pastes it (confirmation
  notice if the tool asks for one, then the text in the shell); Ctrl+Alt+V still pastes with
  splits off and splits with them on · Surface, hand on the keyboard.
- 2026-10-24 · 68fba2325 · the Home card has no Folder limit row; a folder that held 20 apps keeps
  them and takes no more, one under 16 takes more up to 16; Settings › Home › Folders has no Max
  folder size row · emulator.

- 2026-10-25 · commit "clock sheet: Show date and Show next alarm" · the Clock widget's gear at 2×2:
  two new rows after Show seconds (calendar and alarm glyphs, same card style, both on); turning
  Show date off drops the date and leaves the alarm under the face; turning Show next alarm off
  drops only the alarm; both off leaves the face alone, centred; the glyphs read at 24dp in both
  themes; existing clocks show both lines unchanged · pong or emulator (set an alarm first).
- 2026-10-25 · commit "flip: the AM/PM and the seconds share one centre axis" · Flip face, 12h,
  seconds on, in the widget at 2×1/2×2 and in the expanded status bar: "AM"/"PM" is centred over
  the two seconds digits, not left of them; with seconds off the period sits where it did · pong
  or emulator.

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

## Found in the sweep (2026-10-24), to triage
Crops and per-batch tables: `~/reports/device-checks-2026-10-24/`. Each becomes an issue or a
fix, then leaves this file.

1. **Beads never renders on Home.** Editor → Dock card → Beads → Done (pref
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
6. **Split gap slider** (row labelled Frost): 0/13/24 dp persist but the gutter looks identical in
   the miniature and a 4-pane window; may be the checker's drag.
7. **Settings does not reopen on its last page after an upgrade install** (old build on Terminal ›
   Status bar, install, `am start` → root); recents route untried.
8. **Landscape sessions drawer, keyboard hidden**: Saved tab's Add to current and Replace shrink to
   a 7 px sliver; keyboard up, the list is empty.
9. **A–Z strip default mismatch**: Drawer layout shows Left checked on a fresh profile while the
    strip draws on the right.
10. **Text over the wallpaper**: Home icon labels (white with a grey shadow box) read as a smudge on
    a light wallpaper and clip "Calendar"; folder title and "Remove from folder" bubble are dark on
    the dimmed wallpaper, the bubble overlaps the title on a disabled app; the folder popup at font
    1.3 + Large labels breaks words mid-word and clips its last row; the Home lock message is dark
    on a bright wallpaper.
11. **Display window draws over an open app drawer**; after × on the selected Display chip the chip
    stays "Display", floats draw over a blank area and an extra float chip appears.
12. **Tour**: the Workspace keyboard card shows no Skip or dots after 5 s and clips after one body
    line; with split panes the "Tap ?" card points at pane controls.
13. Smaller: Save sessions' Save sits under the keyboard (Enter works); the "Saved" toast covers the
    Sessions drawer's +; the pinned-app menu hides Add to Home below the fold; LCD clock face bottom
    segments look clipped; dragging an app onto a 14-app folder swapped cells instead of merging
    (possibly injected-drag technique); overlapping floats show each other's text through the glass
    (by design?).

## Found in the emulator sweep (2026-10-26)
Staging 1.1.0-pre.6, Android 16 x86_64; screenshots and logs at
`~/reports/device-checks-2026-10-26/`.

- Terminal only's tour starts on the Workspace but asks the user to swipe past their last Home
  page, which this mode removes. Its final card still mentions a Home float and "Tap Layout",
  whose control was retired. See `editor-fresh-terminal-tour-1.png` and
  `editor-fresh-terminal-tour-2.png`.
- Opening the first "icon pack" Settings search hit produced one ANR (SettingsActivity lost-focus
  timeout, 5001 ms); the same route passed after a force-stop. Cause unconfirmed. Screenshot
  `earlier/search-iconpack-raw.png`, log `earlier/logcat-anr.txt`, trace `earlier/anr-trace.txt`.
- About & help › Help did nothing twice after a direct cold start of Settings. Starting the
  launcher first, returning to the same Settings task and tapping Help opened HelpActivity.
  See `earlier/help-tap-raw.png` and `earlier/help-warm-host-raw.png`.
- A–Z Show is shared while Position is per screen: Pull tab chosen on the Workspace's Dock card
  also turned Home to Pull tab, though the editor's rule is that an edit changes only the screen
  you are on. Intended or not? `round2/emu/01-ws-right-pull-editor.png`, `01-home-left-pull-drag.png`.
- With the status bar hidden, a long-press on Home's bare top opens the Home actions sheet;
  opening the editor then leaves that sheet up over the preview frame until a tap outside.
- The "Theme, colours and glass" tip bubble sits over the frame at peek and covers part of the
  A–Z strip in it, portrait and landscape (`round2/emu/06-chip-tap.png`).
- The A–Z Pull tab rests lower after each drag (y about 372, then about 420) and covers the end
  of the terminal's text at the right edge.
- The editor's "Workspace" page label is near-black on the dark editor background (about #1c1c20
  on #131318), in both Use as modes (`round2/emu3/07-editor-swiperight-c.png`).
- Under Terminal only the editor still shows two page dots, the first greyed, hinting at a Home
  page that cannot be reached (same shot).
- Extra keys editor at font scale 1.3: caps clip to "CT…" and "HO…", Edit key's On tap chip
  shows "E…" for ESC, and the Display label hint reads "Blank shows the key na…"
  (`round2/emu2/26-f13-editor.png`, `27-f13-sheet.png`). Row titles, page chips and Done are fine.
- Widget picker with the in-app keyboard up: the sheet does not make room, and its last groups
  sit behind the translucent keys with their text bleeding through (`round2/emu2/34-picker-kbd-bottom.png`).
- Extra keys editor: Row options › Done shows "Key row saved" and disables Save changes, so the
  page is committed with no Discard; the sheets' Done should only put the sheet down. A colour
  edit, by contrast, enables Save (`round2/emu2/15-after-done.png`, `21-rowdown-done.png`).
- Editor, Extra keys › Position › Right on a light wallpaper: the preview draws the keys'
  icons near-white while Top, Bottom, Left and Rides apps row draw them dark, in both themes;
  the saved Right on the live launcher was dark (`round2/emu3-b/33-dark-montage.png`,
  `21-light-right-rail.png`, `22-night.png`).
- Clock widget 2×2 with Show next alarm on and a 07:30 alarm the system reports as next
  (`dumpsys alarm`): the widget shows only the date, no alarm line (`round2/emu3-b/64-clock.png`).
- On a light #EEEEEE wallpaper the system status bar's clock and icons are near-white and barely
  visible over Home and the Workspace (`round2/emu3-b/41-restored.png`, `50-home.png`).
- Uninstall from a lifted Home icon does nothing: the target shows and reddens, the drop fires
  the delete intent, and the system refuses it (`UninstallerActivity: Uid … does not have
  android.permission.REQUEST_DELETE_PACKAGES`); the manifest does not declare that permission.
  Any other Uninstall route through `ACTION_DELETE` is likely affected (`round2/emu2-b/01h.png`).
- Lock layout at font scale 1.3: the refusal "Home is locked. Long-press an empty spot to unlock
  it." wraps and its second line is cut at half height (`round2/emu2-b/02l-locked-drag.png`).
- The "Removed from Home · Tap to undo" pill takes a tap only on its "Tap to undo" words; a tap
  on its centre does nothing (`round2/emu2-b/02e-undo.png`).
- A shortcut pinned from Chrome shows a dimmed Android-robot icon for about 5 s before its own.
- The keyboard's long-press popup opens only from the frame's thin margin: a hold on the mode
  strip's icons runs the icon (Mouse mode on, Go to Terminal) instead (`round2/emu-b/02-popup-kb.png`).
- The keyboard popup looks stitched: Edit sits in its own card and the Hide card runs into the
  Docked · Floating row (`round2/emu-b/02-pk-full.png`).
- The A–Z Pull tab on the Workspace's left edge covers the terminal's `~ $` prompt
  (`round2/emu-b/01-ws-top-pull.png`).
- A–Z Position is kept per screen, per dock style and per orientation (switching to Floating showed
  no Position chosen); the card does not say so. Intended?
- Widget picker under Floating with the status bar unfolded: the sheet starts about 145 px lower
  and its last group sits under the dock (folded, it clears the dock) (`round2/emu2-c/29-after-drag.png`).
- Extra keys › Edit key: a stray dark vertical bar stands just right of the sheet's ✕, light and
  dark themes (`round2/emu2-c/20-ek-light-sheet.png`, `22-ek-night-sheet.png`).
- The "Theme, colours and glass" tip stays on screen over the dock after Back collapses the
  editor to peek (`round2/emu3-c/05c*.png`, `07c*.png`).
- A cold launch with no effect at terminal Opacity 10 shows the Workspace text heavily blurred for
  about 3 s before it sharpens (`round2/emu3-c/45*.png` against `46*.png`).
- The drawer opened on the Workspace (keyboard up, palette › open) has no backdrop: once it
  settles the live terminal's text and boxes stay drawn through it, over the icon labels
  (`round2/emu-c/01w*.png`).
- Floating Style with the Peek glass dock (cold start, keyboard down, Search off): the dock is a
  fully rounded floating card with a gap above the pill, not the card rising from the screen's edge
  with the strip under the pill as its foot (`round2/emu-c/07a*.png`, `07d*.png`); the check for
  commit "fix(peek): the Peek card rises from the screen's edge under Floating too" fails.
- Drawer on Home over a two-tone wallpaper: the "Search apps" placeholder is nearly unreadable on
  the dark half (`round2/emu-c/01g*.png`).

## Peek glass with the keyboard up (pong)
- Docked, Peek glass dock: raise the in-app keyboard. The extra keys bar sits on glass again and the
  keyboard glass spans the full width with its normal corners; hide the keyboard, the card returns.
- Same with the Search field open and with the system IME lifting the dock.

## Peek glass host never padded by the card's rim (pong)
- Peek glass dock, Docked, where the rim is drawn under the glass (below Android 13, or the glass
  program not drawing the edge): raise the in-app keyboard. The extra keys bar sits on glass and
  the keyboard glass reaches both screen sides (`dumpsys activity` shows `extrakeys_background` at
  0,0 inside `accessory_surface_host`, not 32,95). Hide it: the card returns; raise it again:
  still full width.
- With the card standing (keyboard down): frost, tint and rim match the card exactly, and the
  icons' reflections show on the card above the strip (the host's reflection view had been laid
  out 95 px down by the same padding, a likely cause of 48bf23b83's "the card shows none").

## Extra keys editor, streamlined (#164)
Agent (Settings, Keyboard, Terminal extra keys; the page editor, five sheets and the renders are in
`app/build/extra-keys-editor-renders` after `ExtraKeysEditorRenderTest`):
- Material You (Wallpaper colours on, with a coloured wallpaper): caps, cards, sheets and the dashed + read; nothing keeps a fixed navy (light and dark seen on emu2 10-26; the app has no Black theme any more).

## Power-user round, wave 1 (2026-10-25)
Seen on emu (debug, 5ce4ce853): the drawer hides Home, the contrast floor on the welcome sheet, the
widget picker ends above the dock, the theme hint shows once and clears the half's name, physical
keys type into the drawer search (Backspace, Esc), the App menu's rows are in a windows dump, and
A–Z icons arrive within a second. Still owed:

Agent
- 2026-10-25 · 0f30818a4 · TalkBack on: the command palette's field, rows, call chips and keycaps are reachable and double-tap runs them; "App menu" is announced on a dock long-press · emu.
- 2026-10-25 · 4957e1b52 · Drawer opened by dock tile and by A–Z with the keyboard up: Home fades and returns at full opacity, no flash on a quick open-and-cancel · emu.
- 2026-10-25 · 2d1caf678 · Theme hint under the Material You scheme: one line, legible, inside the screen (Light at font 1.3 seen on emu2 10-26, on the editor's palette button) · emu.

## Power-user round, wave 2 and follow-ups (2026-10-25)
Seen on emu (debug, d10b3e471): Launcher settings opens as a sheet over the live Home, Back steps
up and closes from the root, dragging the handle takes it to full height, a tap above closes it,
Settings › Home › Icon size changes Home behind the sheet; the Lock layout switch spans its line,
hides the add/edit rows and the card stays up. Still owed:

Agent
- 2026-10-25 · bcc0cb94c · Terminal contrast Softer/Default/Harder visibly change body text with wallpaper colours on (faint reads quieter on light, seen on emu2 10-26) · emu.

## Power-user round, wave 3 so far (2026-10-25)
Seen on emu (debug, 0dcd99aaf): a Home icon's App menu offers Remove from Home and no Add to home;
hold-and-drag lifts the icon above the status band; Remove turns red on hover; a drop removes it and
Undo within the 5 s notice puts it back in its cell. Still owed:

Agent
- 2026-10-25 · 7c0873d3c · Pin requests with the front page full: the shortcut lands on the next page or a new one; an app's own "Add widget" button (one with a configure screen) ends with the widget on Home (card, landing and "Unlock and add" seen on emu2 10-26) · emu.

## Power-user round, Home ↔ dock and shortcuts (2026-10-25)
Seen on emu (debug, 6b6fc51b8): a dock app dragged to a free Home cell lands there and leaves the dock;
a Home icon dragged into the dock inserts between two pins and leaves Home; Chrome's "New tab" shortcut
dragged out of the App menu lands on Home and opens Chrome. Still owed:

Agent
- 2026-10-25 · 6b6fc51b8 · Home icon onto a full dock page: the last pin moves to the next dock page; holding at the dock's edge turns its page · emu.
- 2026-10-25 · 6b6fc51b8 · Home icon released over the middle of a dock icon makes a dock folder; a dock app onto Remove unpins it and Undo pins it back · emu.
- 2026-10-25 · 6b6fc51b8 · Both drags with the keyboard up, and in Docked and Floating dock styles · emu.
- 2026-10-25 · 6b6fc51b8 · A pinned shortcut survives its app updating (`pinShortcuts`) · emu.

## Editor redesign (2026-10-26)
Built on feat/editor-redesign, waves 1 to 6; the emulator rounds are at `~/reports/editor-redesign-wave1/`,
`-wave2a/`, `-wave2/` and `-waves3-6/`. Nothing below has been seen on pong. Still owed:

Agent
- 2026-10-26 · wave 5 (same commit) · A–Z Glass background off under Floating Style on a plainly light
  and a plainly dark wallpaper, Dark and Light themes: the letters' ink flips to suit the wallpaper
  (no capsule seen on emu 10-26; on a mid-tone two-colour wallpaper the ink stayed white with an
  outline); on the dock's row, a shared plank or inside the Docked frame the shared sheet stays · emulator.
- 2026-10-26 · wave 5 (same commit) · the old holds still win: an extra key opens its alternates, a
  keyboard key its alternates, a window chip lifts, a pinned notification its menu (App menu ending on
  Edit dock, A–Z scrub, dock swipe and status fold seen on emu 10-26) · emulator.
- 2026-10-26 · wave 3 (commit "refactor(editor): retire Layout mode's canvas; rotate to edit") ·
  turning the phone with the editor open feels right: no flash, the card keeps its place (chip,
  landscape values, Position landing live, Undo across a turn and the chip hint seen on emu 10-26) · pong.
- 2026-10-26 · wave 4 · Welcome: choose Terminal only, Continue: the Home page leaves behind the sheet and the wall stands on the Workspace; Show me around skips the two Home lessons · emu.
- 2026-10-26 · wave 4 · Terminal only with no window: the home press starts one shell (home press, wall and editor swipes and the Workspace card seen on emu3 10-26; `launcherctl wall.go` does not exist, so that clause is gone) · emu.
- 2026-10-26 · wave 4 · An upgrade over a build without Use as: no prompt, Home screen, Start in where it was · emu.
- 2026-10-26 · wave 4b · Extra keys with nothing else left on the dock, on a light wallpaper: ink is dark at each Position (the five Positions on light and dark wallpapers, and hidden-dock on dark, seen on emu3 10-26; Right fails in the preview, see the findings) · emu.
- 2026-10-26 · wave 4b · a folder popup's labels keep the popup's ink on a light and a dark wallpaper (Home labels and Label shadow seen on emu3 10-26) · emu.
- 2026-10-26 · wave 4b · Change the wallpaper through the system picker while on Home: labels, hint, dots, extra keys and letters re-ink without a restart (the launcher's own `wallpaper set` seen on emu3 10-26) · emu.

## Home ⇄ Workspace as one motion (2026-10-26, f30d90c1c)
- 2026-10-26 · Both directions as a fling, a slow drag and a spring-back: no flash on the first frame (the leaving stack's picture takes over from the live stack) and none at landing (the picture is dropped and the stack re-dressed in one frame) · pong.
- 2026-10-26 · Home → Workspace: no reflow or scroll jump after arrival; the terminal is laid out at its keyboard-up size before it shows · pong.
- 2026-10-26 · Stack transition, both directions: the glass edge and the pane rims fade with the terminal text, never text without an edge · pong.
