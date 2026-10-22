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
| Appearance editor | reliable: `ad shell am start -n com.termux/.app.activities.SettingsActivity` → Appearance → "Wallpaper, look and layout"; Done returns to Settings, Back twice to the terminal. Shortcut: long-press the top-left corner of the pane (`long 40 262 900` on the emulator at 420 dpi), then tap Appearance within about a second (`ad find Appearance`); it only opens Appearance when the pane shows no resize handles | pages `appearance_page_mode_wallpaper`, `_look`, `_icon_pack` (Layout has no id: it is the grid icon in the top pill, about 655,218); styles `appearance_editor_style_docked`, `_floating`; Done `appearance_page_done`. Tap by id: the tab row shifts per page. Changes apply only on Done. Look page: tap a Look by its label (Clear, Mist, Tint, Solid, Custom). Sliders (Blur, Depth, …) are vertical and fill from the bottom; a drag must start inside the slider (y about 1980–2295): `ad swipe 745 2270 745 1850 800` takes the fourth slider (Tint) to 100% with six columns, `2000 → 2310` to 0. With the glass edge drawn, Depth is a seventh, last column and the others narrow: find the column's x first. Tapping a status pill in the preview swaps the sheet to Status bar controls; Back with changes asks Keep editing / Discard / Save |
| Icon size, Icons per page | Appearance editor → Layout page | value shows only on screen (not in the accessibility tree); read it from a `zoom` crop |
| Command palette | tap the terminal so the keyboard is up, then swipe up on its space bar: `ad swipe 540 2250 540 2000 250` | type to filter, `ad key DPAD_DOWN` moves the selection; chip strip kbd, pick, split, font; app chips need usage history; a theme change closes it |
| Icon pack | Appearance editor → `appearance_page_mode_icon_pack` (tap a tile by its label, `ad tap Arcticons`; coordinates miss); a pinned override: long-press a dock icon › Change dock icon › a pack › an icon; (the editor reopens on its last page) | tiles `icon_pack_tile_label` (tap the tile art if the label does not take); "Pinned app icons only" is `icon_pack_pinned_only`, on by default. MBE Style (`me.iacn.mbestyle_27.apk`, F-Droid) installs as a PNG pack |
| AI models | Settings → Model centre; Export/Import use the system folder picker (USE THIS FOLDER, then ALLOW), export lands in `/sdcard/Documents/Termux Launcher models` | Get models goes stale after a `tai delete`: leave and re-enter the page |
| Keyboard layouts | Settings → Keyboard → Layouts (list at about 487,1242) → Layouts (487,671); a tap on a catalogue row slides its strip open, `ad find` by description works ("Add Colemak to the cycle"); cycle to the next layout by swiping down on the space bar: `ad swipe 540 2200 540 2330 250` | — |
| App drawer | tap the star at about 40,1625, then a slow swipe down on the dock's empty area: `ad swipe 900 1520 900 2000 600`; the dock's list glyph (about 975,1700) opens Sessions | a swipe on the dock's letters scrubs that letter's apps into the dock instead |
| Pinned apps | unpin one: long-press its dock icon → Unpin (about 160,1410; the popup is not in `ui`, screenshot it). Edit sheet: long-press an empty dock spot (about 800,1531, not 540); the first row's remove is about 938,692 and rows shift up after each removal (a tap on a row still animating out is ignored: space taps ~1.5 s); pin from the sheet by searching (field about 540,1484) and ticking the row (`ad swipe x y x y 1200`) → "Edit pinned apps": tap names under MOST USED, then Done; remove with "Remove from pinned apps" | a letter in the dock strip shows that letter's apps in the dock; the letters shift when an app is installed. The dock's grid glyph (about 810,1700) opens a split pane, not the drawer; a pane opened that way is not `launcherctl`'s to close (`exit` in it) |
| Split panes, window chips | `launcherctl pane open` / `window open -- CMD` (a command is required) is faster than the UI; `window close <id>` | window chips switch windows; on Docked, the grid glyph on the dock row opens a split |
| Sessions sidebar (legacy) | tap the session indicator `terminal_sessions_indicator`, the number box left of the chips | after it closes, focus stays on the indicator: tap the terminal before typing |
| Widgets page | not yet mapped | `WidgetPaneView` |
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

## Notes from the v1.2.0 release smoke (2026-10-21, emulator)

- Pong is often asleep (`dumpsys power | grep mWakefulness` not Awake, keyguard up). Check that before taking a lease; a run that finds it asleep reports so at once instead of waking it.
- Appearance editor: the page icons shift per page (the Look icon sits near x 390 on the Wallpaper page and near x 612 on Layout). On the Look page, tapping the terminal pane in the preview shows Cursor trail and Terminal effect; tapping the keyboard shows Keys, Radius and Spacing; tapping the dock shows Glass, Opacity, Tint, Icon size and App icons. "All surfaces" is a label, not a control.
- Backup & restore is the last row of Settings, below App behavior. Export settings goes through the system Save picker with a default name (`termux-launcher-settings-<date>.zip`); Save sits near 927,2273 on the emulator.
- Typing `\e` into the terminal with `ad type`: pass the backslash unescaped, single-quoted in the shell; a pre-escaped form mangles the printf.
- `ad long` on a dock icon launched the app instead of opening the Unpin popup (see device-checks.md, 2026-10-21).


## Notes from the graphics benchmark check (2026-10-21, emulator)

- Long terminal commands: drive a pane under runas instead of `ad type` (typing after a tap near the
  dock can switch to the Widgets page): `id=$(launcherctl pane open --print-id --no-focus)`,
  `launcherctl pane write $id --enter "CMD"`, poll `launcherctl pane read $id`, Ctrl-C with
  `launcherctl pane write $id "$(printf '\003')"`. `pane wait` caps at 300000 ms.
- Clipboard: `ad shell cmd clipboard` has no get-primary-clip; read it with `launcherctl clipboard paste` under runas.
- Benchmark graphics is the last row of Settings → Display → Linux apps → Set up Linux apps; swipe up.
- The launcher rewrites `termux-x11*` scripts in the prefix only when `X11CliInstaller.VERSION`
  changes. After installing a build that changed one, check `$PREFIX/bin/termux-x11-gpu-setup`
  is the new one before testing it.
- The emulator has no proot-distro, so container checks need a device that has one.
- The X11 installer's marker is `$PREFIX/libexec/termux-launcher/x11/.installed` ("… v14").
- Each `ad runas` call is a new shell: keep a pane id in a file (`$HOME/pid`) between calls.
- The Benchmark graphics toast shows on the launcher after Settings closes: `ad frames 6 <dir> --
  tap "Benchmark graphics"` and read the last frames.

## The display window on the emulator (2026-10-22)

- First run needs `xkeyboard-config`: the display window offers an "Install xkeyboard-config" button
  (`x11_pane_install`), which opens a terminal window that closes itself after about 90 s.
- Turn the display on: Settings → Display, the switch at about 969,458. Back to the launcher with
  `ad shell am start -n com.termux/.app.TermuxActivity`; the red monitor glyph in the dock (about
  694,1699 with the keyboard up) shows the display window (the wall pages widgets, terminal, display).
  "No display is running" → `ad tap "Start display"`, about 15 s until `x11_display_view` appears.
- No X client is installed on the emulator (no xterm, xeyes, xclock); the bare display surface is
  enough for input checks.
- The drawer route above needs the dock at y≈1531: on the display window, raise the keyboard first
  (`launcherctl keyboard show` under runas), then `tap 40 1625` and `swipe 900 1520 900 2000 600`.
  The drawer is open while `ad find app_drawer_host` shows that node. If Back leaves it open,
  `ad home` then the `am start` above closes it.
- `ad install <apk> --fresh` (the flag after the path; before it, ad reads it as the file). First run
  logs under `Termux.TermuxInstaller`; extraction takes about 8 s on the emulator, with the Welcome
  sheet over the "Installing bootstrap packages…" dialog ("Skip the tour" / Start at about 895,1476).
- `launcherctl keyboard show` pins the keyboard: X11TextFocus then logs "ignored: pinned". Run
  `launcherctl keyboard hide` before testing focus-driven show/hide.
- Mousepad in the drawer: `ad find Mousepad` (an ImageView, about 100,949). A tap into its text area
  at about 330,480 focuses it. Drawer apps get the fcitx5 environment from the launcher; one started
  by hand needs `GTK_IM_MODULE=fcitx XMODIFIERS=@im=fcitx
  DBUS_SESSION_BUS_ADDRESS=unix:path=$PREFIX/tmp/termux-x11-im-bus-0`.
- Rotate: `settings put system accelerometer_rotation 0` + `user_rotation 1`; restore 1 / 0. Keyboard
  off is the palette's "Keyboard on/off" entry, not the dock key (which only hides/shows).
- Stop the display with `kill <termux-x11 pid>` under runas (it restarts on its own); never
  `pkill -f termux-x11` inside a runas string, it kills your own shell. `ps -o` fails in the
  prefix: read `/proc/*/cmdline`.
