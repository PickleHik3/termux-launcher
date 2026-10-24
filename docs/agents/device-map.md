# Device map

How a test agent reaches each part of the launcher over adb, so a device check spends its time
checking rather than exploring. Commands use the `ad` tool of the host's android-control skill;
plain `adb -s <serial>` works the same. Add what you had to find out; fix what turned out wrong.

## The app

- Package `com.termux` on the Termux edition (`com.termux.launcher.nix`, `io.vaj.tl` on the
  others). Main activity `.app.TermuxActivity`; it is also the HOME activity.
- Build for the emulator: `./gradlew :app:assembleDebug`, then install
  `app/build/outputs/apk/debug/termux-app_apt-android-7-debug_x86_64.apk` (`ad install` keeps
  data). Pong takes release builds only; see AGENTS.md.
- After installing on the emulator the launcher becomes home. Before handing the emulator over:
  `ad shell cmd package set-home-activity com.google.android.apps.nexuslauncher/.NexusLauncherActivity`.
- Leases: `ad lease status` shows only the time left on a lease. Its true age is the `since=`
  epoch in `~/.local/state/ad/leases/<slot>`; read that before applying the 30-minute rule, and
  never take a lease over: a long holder is usually another session's sweep. Ask that session.
- Owed checks: `project-docs/device-checks.md` (Agent items can run here; Developer items need
  the developer's hand).

## Running commands inside the app

Debug builds allow `run-as`. With this prelude, `ad runas` runs anything in the terminal's own
environment and prints the output — no typing into the terminal, no reading files back:

```bash
export AD_RUNAS_PRELUDE='P=/data/data/com.termux/files; export HOME=$P/home PREFIX=$P/usr TMPDIR=$P/usr/tmp PATH=$P/usr/bin:/system/bin LD_PRELOAD=$P/usr/lib/libtermux-exec-ld-preload.so TERMUX_EXEC__SYSTEM_LINKER_EXEC__MODE=disable; cd $HOME'
ad -s emu runas com.termux 'launcherctl pane list'
```

- `launcherctl` (`launcherctl --help`, `docs/en/LauncherCtl.md`) changes the live UI from there:
  `pane open|resize|close`, `window open|focus|close`, `keyboard show|hide`,
  `wallpaper set FILE`, `launch <app>`, `notify`, `toast`, `clipboard copy|paste`.
- To see a command run in the UI, open your own pane and drive it: `id=$(launcherctl pane open
  --print-id --no-focus)`, `launcherctl pane write "$id" --enter 'cmd'`, `launcherctl pane read
  "$id" --lines 20`. `pane write`/`read` refuse panes you did not open (`not_owned`).
- Typing into the user's own pane needs `ad type` one word at a time with `ad key SPACE`
  (e.g. `seq -s w 1 900` fills the pane with long lines).
- `launcherctl keyboard show|hide` raises or drops the in-app keyboard without a tap.
- A fresh `pre` debug install starts Docked, light, font scale 1.0, and opens with a Welcome sheet
  (Skip the tour), a media-permission dialog and a "You're set" card (Start).
- **The Look page's Depth slider is not offered until a wallpaper is set from the launcher**
  (nor in Lazy mode, with Fancier Glass off, or below Android 13). Push a PNG to `/data/local/tmp`, `chmod 644`, then
  `ad runas com.termux 'cp /data/local/tmp/x.png $HOME/x.png; launcherctl wallpaper set $HOME/x.png'`
  (an `/sdcard` path is refused). There is no unset: the emulator keeps that wallpaper.
- `launcherctl keyboard hide` can answer "inaccessible" right after the editor closes; retry.
- A second `launcherctl wallpaper set` (or picker apply) used to drop the Look's blur and opacity
  back to the install's first values (PR #74 fixes it). Until that lands, re-apply the Look after
  a wallpaper change.
- A long-press on the terminal's text starts selection; the terminal action sheet (with Kill
  process, whose confirm is a centred glass card) is the terminal view's context menu, not a
  `launcherctl` command. Other centred cards: the palette's confirm entries. The session browser's
  Rename is inline in the drawer, not a card.
- Pinned dock icons: long-press the dock's empty hint → the pin editor lists installed apps as
  checkable buttons (Calendar, Camera, Chrome, Clock on the stock image) → Done. A letter tapped in
  the A–Z row shows that letter's apps only until the next touch.
- Prefs live in `shared_prefs/com.termux_preferences.xml`; edit them **before** a force-stop,
  never after (AGENTS.md, "It is the home screen").

## Places

| Place | How to get there | Useful ids / text |
|---|---|---|
| Settings | `ad shell am start -n com.termux/.app.activities.SettingsActivity` | `settings_search_input` finds any setting by name; rows by `title` text (Appearance, Apps, …) |
| Appearance editor | reliable: `ad shell am start -n com.termux/.app.activities.SettingsActivity` → Appearance (the root row opens the editor); Done returns to Settings, Back twice to the terminal. Shortcut: long-press the top-left corner of the pane (`long 40 262 900` on the emulator at 420 dpi), then tap Appearance within about a second (`ad find Appearance`); it only opens Appearance when the pane shows no resize handles | pages `appearance_page_mode_wallpaper`, `_look`, `_icon_pack` (Layout has no id: it is the grid icon in the top pill, about 655,218); styles `appearance_editor_style_docked`, `_floating`; Done `appearance_page_done`. Tap by id: the tab row shifts per page. Changes apply only on Done. Look page: tap a Look by its label (Clear, Mist, Tint, Solid, Custom). Sliders (Blur, Depth, …) are vertical and fill from the bottom; a drag must start inside the slider (y about 1980–2295): `ad swipe 745 2270 745 1850 800` takes the fourth slider (Tint) to 100% with six columns, `2000 → 2310` to 0. With the glass edge drawn, Depth is a seventh, last column and the others narrow: find the column's x first. Tapping a status pill in the preview swaps the sheet to Status bar controls; Back with changes asks Keep editing / Discard / Save |
| Icon size, Icons per page | Appearance editor → Layout page | value shows only on screen (not in the accessibility tree); read it from a `zoom` crop |
| Command palette | tap the terminal so the keyboard is up, then swipe up on its space bar: `ad swipe 540 2250 540 2000 250` | type to filter, `ad key DPAD_DOWN` moves the selection; chip strip kbd, pick, split, font; app chips need usage history; a theme change closes it |
| Icon pack | Appearance editor → `appearance_page_mode_icon_pack` (tap a tile by its label, `ad tap Arcticons`; coordinates miss); a pinned override: long-press a dock icon › Change dock icon › a pack › an icon; (the editor reopens on its last page) | tiles `icon_pack_tile_label` (tap the tile art if the label does not take); "Pinned app icons only" is `icon_pack_pinned_only`, on by default. MBE Style (`me.iacn.mbestyle_27.apk`, F-Droid) installs as a PNG pack |
| AI models | Settings → Model centre; Export/Import use the system folder picker (USE THIS FOLDER, then ALLOW), export lands in `/sdcard/Documents/Termux Launcher models` | Get models goes stale after a `tai delete`: leave and re-enter the page |
| Keyboard layouts | Settings → Keyboard → Layouts (list at about 487,1242) → Layouts (487,671); a tap on a catalogue row slides its strip open, `ad find` by description works ("Add Colemak to the cycle"); cycle to the next layout by swiping down on the space bar: `ad swipe 540 2200 540 2330 250` | — |
| App drawer | tap the star at about 40,1625, then a slow swipe down on the dock's empty area: `ad swipe 900 1520 900 2000 600`; the dock's list glyph (about 975,1700) opens Sessions | a swipe on the dock's letters scrubs that letter's apps into the dock instead |
| Pinned apps | unpin one: long-press its dock icon → Unpin (about 160,1410; the popup is not in `ui`, screenshot it). Edit sheet: long-press an empty dock spot (about 800,1531, not 540); the first row's remove is about 938,692 and rows shift up after each removal (a tap on a row still animating out is ignored: space taps ~1.5 s); pin from the sheet by searching (field about 540,1484) and ticking the row (`ad swipe x y x y 1200`) → "Edit pinned apps": tap names under MOST USED, then Done; remove with "Remove from pinned apps" | a letter in the dock strip shows that letter's apps in the dock; the letters shift when an app is installed. The dock's grid glyph (about 810,1700) opens a split pane, not the drawer; a pane opened that way is not `launcherctl`'s to close (`exit` in it) |
| Split panes, window chips | `launcherctl pane open` / `window open -- CMD` (a command is required) is faster than the UI; `window close <id>` | window chips switch windows; on Docked, the grid glyph on the dock row opens a split |
| Sessions sidebar (legacy) | tap the session indicator `terminal_sessions_indicator`, the number box left of the chips | after it closes, focus stays on the indicator: tap the terminal before typing |
| Widgets page | tl16 1080×2400: long-press a free Home cell (e.g. 540,1990) → "Add a widget" ≈806,1338 ("Edit widgets" ≈300,1633); Termux Launcher group ≈400,630; edit mode gear ≈143,195, X ≈60,195, ✓ ≈827,178; page swipe `swipe 900 2060 150 2060 250`. Wall store `shared_prefs/launcher_widget_repository.xml` (built-in ids negative; an overlap refuses the whole wall, tag `LauncherWidgets`; force-stop before seeding) | `WidgetPaneView` |
| Voice pill | needs a microphone: pong only | — |

## Device quirks

- The emulator renders on the CPU: judge looks there, never motion or frame time.
- Injected swipes release with zero velocity; read the end state, not the fling.
- `ad ui` does not see dialog windows; screenshot and tap coordinates.
- `ad ui` truncates the terminal's text: read the terminal from a `zoom` crop, or run the command
  through `ad runas` instead.
- Force-stop the app after a density or font-scale change.
- `./gradlew :app:connectedDebugAndroidTest` installs its own copy of the app and **uninstalls it when the run ends**: the launcher's data (prefs, managed wallpaper, pins, tour) is gone and the next `ad install` is a first run. Run device checks before the instrumentation tests, or set the state up again after.
- Settings reopens on the page it was left on: Back to the root before searching (`settings_search_input`).
- Voice: with no speech model the dictate action opens Google's dictation dialog, not the card, and
  the emulator has no microphone; the card's checks run on pong.
- A contact with a photo: grant `com.termux` READ_CONTACTS, `content insert` into
  `content://com.android.contacts/raw_contacts` and `/data`, push a PNG to `/sdcard/Pictures` and
  broadcast MEDIA_SCANNER_SCAN_FILE, then `am start -a android.intent.action.EDIT -d
  content://com.android.contacts/contacts/lookup/<key>/<id>` → Add picture › Gallery › Save.
- `ad runas` needs `AD_RUNAS_PRELUDE` exported in the same shell call.
- Dock zoom crop: `ad zoom 0,1455,1080,1650` (commas). Palette app rows need a typed name (`ad type cal`).
- The F-Droid site can be blocked by a filter; `mirror.cyberbits.eu/fdroid/repo/` serves the same APKs.
- Never `pkill -f` a pattern that appears in your own command line: it kills your shell.
- `-s emulator-5554` does not resolve until the emulator is up: `ad -s emu emu start` first.
- The Appearance editor reopens on its last page; the Icon pack page's switch is `icon_pack_pinned_only` (about 935,2030).
- The only AVD is tl16 (API 36); there is none for API 29/30.
- Renderer: `-gpu auto` crashes qemu in host libEGL within about 90 s of the launcher drawing
  (`coredumpctl list` shows it; the app's install is lost on the systemd restart). The drop-in
  `~/.config/systemd/user/android-emulator.service.d/gpu.conf` starts it with
  `-gpu swangle -feature -Vulkan`, which holds. `angle_indirect` is not offered by this emulator.
- A cold boot needs the quickboot snapshot moved aside first
  (`~/.android/avd/tl16.avd/snapshots`); `emu stop` + `emu start` reloads a broken GPU state.
- Swapping one debug build for another: uninstall first. `--no-incremental` over a different build
  fails the bootstrap ("reinstall completely"). A fresh install opens the tour: Skip the tour,
  Don't allow, Start.
- Crossing the wall: swipe left goes Home → window, right goes window → Home. A held mid-slide
  frame is `input motionevent DOWN`, MOVE steps, then UP; a start at x ≤ 100 or ≥ 1000 does not
  take.
- Settings root (10-24): Your launcher (Home / Apps & dock / Terminal / Linux apps), Everywhere
  (Appearance / Keyboard / Notifications / On-device AI), System (General). The root's Appearance
  row opens the Appearance editor directly (no page); Home › Appearance and Terminal › Appearance
  open the Layout editor on that place. In the Layout editor its home icon (about 645,1728) edits Home's layout, "Home's own"
  (654,1875); a tap on the preview dock (540,1485) opens Tray / Beads / Strip / Search / Drawer;
  Done (993,220), then `ad launch com.termux` (the Settings task stays in front).

## The wall rework (feat/wall-rework, 2026-10-18)

- Home's palette gesture: `ad swipe 540 1700 540 900 700` (an up swipe on the page's free area).
  A Home float opens from `ad long 540 1000` (a free cell) then the Terminal row of the Home actions
  sheet that slides up (`ad tap Terminal`; the sheet's rows are real nodes, unlike the old menu); on a
  full-size float Close is about 1006,283 and Minimise about 796,283.
- The Terminal entry's menu is a long press at 76,2190 on the dock (New float here / New window).
  The dock's edit sheet is a long press at 540,2180; the Terminal entry is under MOST USED there.
- Layout editor on Home without the corner tab:
  `am start -n com.termux/.app.TermuxActivity --ez com.termux.app.extra.LAYOUT_EDITOR true --es com.termux.app.extra.LAYOUT_EDITOR_PLACE widgets`.
  Its tray chips are Status bar, Extra keys, Keyboard; the keyboard chip toggles always on.
- Ctrl+Alt+F over adb: `adb shell input keycombination 113 57 34` (C=31, V=47, H=36).
- Migration seeding: force-stop, then `sed -i` through `ad runas` on
  `/data/data/com.termux/shared_prefs/com.termux_preferences.xml` (insert before `</map>`:
  `app_launcher_use_case_mode`, remove `app_launcher_start_in`, set `wall_migration_version` to 0),
  then `ad launch` for a cold start (`am start` into a live instance does not re-land).
- Always run `ad logcat` and `ad crash` under `timeout`; they follow forever. A crash is read with
  `adb -s emulator-5554 logcat -d -b crash`.
- Injected swipes release with zero velocity: use 600–800 ms for page drags, past the middle of the
  screen. A border drag from a side border is a plain `input swipe` starting in the air beside the
  window (no hold needed); a hold is still `input motionevent DOWN x y`, a 500 ms pause, then MOVE
  and UP.
- Export `AD_RUNAS_PRELUDE` in every bash call; a new call loses it and `launcherctl` reports
  "inaccessible". `launcherctl` is also unavailable while no window exists.
- Ctrl+Alt+F over adb needs a prior tap on the terminal for focus.
- Home's corner tab: long press at 1040,175 on the free page; its plus (804,198) adds a page, and a
  long press on an empty cell then offers Remove page.
- The sessions drawer opens from the list key at 990,1700 in the Workspace's key row (the grid key
  at 810,1700 opens a split). In the drawer: the chevron at 102,542 expands a session, Home at
  118,1245, the session's Float row under it.
- The display's controls appear from a long press at 1030,262 on the display window; power at 604,285.
- Layout editor on Home: Done at 993,221; the tray's eye at 970,1726; the Keyboard chip at 864,1873.
  Dragging a canvas block needs `input motionevent DOWN`, a 1.2 s hold, MOVE steps and UP;
  `ad swipe` does not drag it.
- The prefix's gtk3 desktop files ship with `NoDisplay=true`, so they are not Linux apps in the
  drawer or the palette and `launcherctl launch gtk3-demo` answers 404 until that flag is false.
- Display stop: long press 1030,262 on the display window, power at 604,285, confirm Stop at
  841,1420. The display's chip sits at about 298,177 in the window bar.
- In the Home widget edit frame the X at 77,212 removes the widget; it does not exit edit mode.
- `ad home` while a window is showing stays on that window on the emulator's first press; press
  twice to reach Home page 1. Display Stop lands on the terminal window, not Home.
- A release build for the emulator: `scripts/dev-install.sh` keeps arm64 libs only and is for pong.
  For the emulator zipalign the universal release APK and sign it with
  `app/testkey_untrusted.jks` (alias `alias`, the password in dev-install.sh), then
  `adb install -r --no-incremental`; the unsigned APK fails with INSTALL_PARSE_FAILED_NO_CERTIFICATES.
- Popup menus (the dock's long-press menus, the float handle menu) are not in `ad ui`; read the
  screenshot and tap by coordinates. From the Terminal pin's menu, New float here is about 190,1746.
- `ad long` is `input swipe x y x y 800`. Before 31803120e that still press on a dock pin ran
  Unpin (the gesture took the screen corner as its origin); a pin that vanishes on a long press is
  that bug, not the store.
- A blind end-user review: `ad codex "<goal>" -c model_reasoning_effort=medium`, one area per run
  (Home, Workspace, floats, Settings), 6–35 min each; the goal text and answers of the 2026-10-18
  run are in the session scratchpad `codex-review/` and under ~/reports/wall-rework-2026-10-18/blind/.

## Wall rework phase two (feat/wall-rework-phase2, 2026-10-18)

- Home's long-press sheet (a long press on a free cell) has Terminal, Add a widget, Add a page,
  Edit pages, Layout, Wallpaper and Look; Layout opens the Appearance editor in Layout mode.
- The corner long-press toolbar hangs off the page's corner, not the screen's. On a window it is
  the window's (Appearance, minimal, tiling, settings, help); on Home it is Home's (pencil, plus,
  palette, help), whose palette is the button described "Appearance" and opens the Overview. A
  window's corner moves in with the Workspace's Margin. Home's page reaches the screen's edges
  since 9980f4a6d, so its corner squares sit at the screen's corners whatever the Margin.
- A Home float drawn over most of a Home page looks like a window. Which toolbar a corner hold
  brings out (above) tells them apart.
- Widget grid size: enter edit mode (long press a widget), tap the "4 × 5" chip, then the column
  wheel.
- The Margin slider drags relatively: aim from the thumb, read the label after each drag and
  correct.
- The Appearance editor reopens on the page it was last left on.
- Read the log with `adb shell logcat -d` rather than `ad logcat`. `ad -s emu stop PKG`
  force-stops a package.
- After `am force-stop com.termux` on the emulator the app is back within about 3 s, because it
  is the default home. A true cold start needs the stock home set first, or a launch from the
  app's icon.
- A Home page shows a house-icon status pill, the widget grid and the pin dock. A window shows a
  terminal-icon pill and the extra keys.
- The corner toolbar comes out on a long press at (40,262). Its palette button is at (84,284).
- The Home editor's Margin slider spans about x 600–1010 at y 1916, and it drags relatively.
- In widget edit mode the "5 × 5" chip is at (706,178), keep at (828,178) and discard at
  (927,178).
- In the editor, Back steps through the pages and Done closes it. A Layout page left over Home
  and brought back from a window opens on the window's own place (590ff4c8b).
- Long-press at about (540,1000) on empty Home opens the launcher's sheet; Layout at about
  (240,1925) opens the Appearance editor on its Layout page. The editor's Undo button is
  `appearance_page_undo` at about (855,220), shown only after a change; the Unsaved-changes
  dialog is not in `ui`, its Discard is at about (642,1418).
- The Margin slider gives roughly 8 px per dp from the thumb. For an exact value drag fully left
  to 0 (swipe 700→560 at y 1916), then swipe 633→678 for 8. Read the label with
  `zoom 400,1800,1080,1900`.
- `ad -s emu adb shell cmd package set-home-activity com.termux/.app.TermuxActivity` sets the
  launcher as home before a check.
- Look editor sliders at 1080 px wide, column x: Glass 90, Opacity 260–282, Tint 432–453, Shade 603,
  Vibrancy 774, Depth 946. Show them with `ad tap Custom` (the label text; a tap on the pill misses).
  Opacity at x 262: `2290 → 2262` is about 9%, `→ 2180` about 36%, `→ 1850` 100%. The value is not in
  the `ad ui` tree: read the "Opacity · N%" label off a screenshot.
- `am start` of SettingsActivity brings the existing task forward, maybe on a sub-page: tap Navigate up
  first. Set night mode before entering the editor.
- Keyboard crop in the editor preview: `ad zoom 231,1000,849,1590`.
- `ad logcat -d` follows and hangs; use `timeout 20 adb -s emulator-5554 logcat -d`.
- A theme (`cmd uimode night yes`) or font-scale change recreates the activity and closes the
  Appearance editor: live edits stay applied, no Keep/Discard. Same on pre; reopen and check there.
- Swipes near the dock toggle the Ctrl/Alt/Fn latches and the F-key layer; a force-stop clears them.

## The display keyboard on the emulator (ported from pre, 2026-10-22)

Notes from pre's display-keyboard and graphics-benchmark checks, kept where they hold on v2.
Pre's coordinates and its route to the display (Settings → Display, the dock's monitor glyph) are
left out: on v2 the display window opens from the drawer's Display entry under Linux Apps, or from
a Linux app.

- First run needs `xkeyboard-config`: the display window offers an "Install xkeyboard-config"
  button (`x11_pane_install`), which opens a terminal window that closes itself after about 90 s.
  "No display is running" → `ad tap "Start display"`, about 15 s until `x11_display_view` appears.
- No X client is installed on the emulator (no xterm, xeyes, xclock); the bare display surface is
  enough for input checks. The emulator has no proot-distro either, so container checks need a
  device that has one.
- The drawer is open while `ad find app_drawer_host` shows that node. If Back leaves it open,
  `ad home` then `ad shell am start -n com.termux/.app.TermuxActivity` closes it.
- `ad install <apk> --fresh` (the flag after the path; before it, ad reads it as the file). First
  run logs under `Termux.TermuxInstaller`; extraction takes about 8 s on the emulator.
- `launcherctl keyboard show` pins the keyboard: X11TextFocus then logs "ignored: pinned". Run
  `launcherctl keyboard hide` before testing focus-driven show/hide.
- Mousepad from the drawer focuses on a tap into its text area. Drawer apps get the fcitx5
  environment from the launcher; one started by hand needs `GTK_IM_MODULE=fcitx
  XMODIFIERS=@im=fcitx DBUS_SESSION_BUS_ADDRESS=unix:path=$PREFIX/tmp/termux-x11-im-bus-0`.
- Rotate: `settings put system accelerometer_rotation 0` + `user_rotation 1`; restore 1 / 0.
  Keyboard off is the palette's "Keyboard on/off" entry, not the dock key (which only hides/shows).
- Stop the display with `kill <termux-x11 pid>` under runas; never `pkill -f termux-x11` inside a
  runas string, it kills your own shell. `ps -o` fails in the prefix: read `/proc/*/cmdline`.
- Long terminal commands: drive a pane under runas instead of `ad type`:
  `id=$(launcherctl pane open --print-id --no-focus)`, `launcherctl pane write $id --enter "CMD"`,
  poll `launcherctl pane read $id`, Ctrl-C with `launcherctl pane write $id "$(printf '\003')"`.
  `pane wait` caps at 300000 ms. Each `ad runas` call is a new shell: keep a pane id in a file.
- Clipboard: `ad shell cmd clipboard` has no get-primary-clip; read it with
  `launcherctl clipboard paste` under runas.
- Graphics acceleration is the last row of Settings → Linux apps → Set up Linux apps;
  swipe up. Its page's Run benchmark opens a terminal session and closes Settings; the toast
  shows on the launcher after: `ad frames 6 <dir> -- tap "Run benchmark"` and read the last frames.
- The launcher rewrites `termux-x11*` scripts in the prefix only when `X11CliInstaller.VERSION`
  changes (18 on v2 since the graphics page). After installing a build that changed one, check
  `$PREFIX/bin/termux-x11-gpu-setup` and `$PREFIX/bin/termux-x11-im` are the new ones before
  testing them.

## App menu and the wall, over adb (2026-10-23)

- The App menu from a dock icon opens on release: `ad adb shell input swipe X Y X Y 4000`, then a
  screenshot about 3 s later. A `motionevent DOWN` hold alone launches the app. Dismiss it with
  Back, never a tap: the tap lands on whatever is behind the card.
- A wall drag takes only from the gap just under the pane (about y=1232 in a window at
  1080x2400); mid-screen drags on the terminal do nothing.
- The Layout editor opens with `--ez com.termux.app.extra.LAYOUT_EDITOR true --es
  com.termux.app.extra.PLACE widgets`; the Dock design chips sit at about (139,2022) Tray and
  (332,2022) Beads.
