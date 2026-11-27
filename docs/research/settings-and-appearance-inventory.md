# Settings and Appearance inventory

2026-11-27, based on `a3d964e74` (branch v2-redesign). Research note; no source was changed.

## How to read this

- Paths are relative to the repo root; `path:line` cites the code. Code wins over docs; disagreements are in section 5.
- Strings are quoted as resolved from `app/src/main/res/values/strings.xml` (and `arrays.xml`).
- Section 1 tables (pages, preferences) are generated mechanically from `SettingsTree.java` and `app/src/main/res/xml/*.xml`: "Read by" lists the first Java sites that mention the key's literal or its `TermuxPreferenceConstants` constant (a reference may be a write); "code default" is the `DEFAULT_*` constant of the same name when one exists. The `L` column is the XML line. Not every key has a code default by that name.
- Caveats: keys of the X11 pages are also read by the `x11-server` module (outside the Java scan), e.g. `clipboardEnable`, `displayFilteringMode` (`x11-server/src/main/java/com/termux/x11/LorieView.java`).
- Sections 3.1 to 3.3 and 3.5 come from a delegated read-only scan (launcherctl, layout editor, AI tool registry); items marked "unclear" were not verified. Section 3.4 (menus, tour, toggles) was not delivered, see 3.4 and section 6.

## 1. Settings pages

### 1.1 Reaching Settings

- `SettingsActivity` (`app/src/main/java/com/termux/app/activities/SettingsActivity.java`, manifest `app/src/main/AndroidManifest.xml:223`) hosts every page. It opens on the root (`root_preferences.xml`, `SettingsActivity.java:978`) or on a fragment named in an Intent extra: `EXTRA_INITIAL_FRAGMENT`, `EXTRA_INITIAL_PLACE`, `EXTRA_SCROLL_TO_KEY`, `EXTRA_SECTION_KEY`, `EXTRA_FROM_EDITOR`, `EXTRA_SEARCH_QUERY` (`SettingsActivity.java:58-81`).
- The tree of screens is `app/src/main/java/com/termux/app/fragments/settings/SettingsTree.java:126-238`: kinds are `section` (a root row's page), `door` (opens the Appearance editor, no page), `page` (child), `editor` (a dedicated editor entered by container transform, no XML). Retired page names resolve through `ALIASES` (`SettingsTree.java:241-255`).
- Root rows (from `root_preferences.xml`): Your launcher: Home, Apps, Terminal, Linux apps; Everywhere: Appearance (door), Keyboard, Notifications, On-device AI; System: Battery & performance, General. Plus a search row (`settings_search`) and the process-limit notice (`process_limit_notice`).
- Settings search is built from the same XMLs; the rows moved into the Appearance editor stay searchable through `app/src/main/res/xml/appearance_search_index.xml` (comment at its top: never shown, a hit opens the editor). Search key to editor card mapping: `SettingsEditorDoors.java:60-90`.
- Data stores: each page sets a `PreferenceDataStore` (table below). Pages without an explicit store use the default `SharedPreferences`. All stores that matter for look write the one file `<package>_preferences` (`TermuxConstants.java:1105`), the same file `TermuxAppSharedPreferences` reads.

### 1.2 Screens

| # | Screen (class) | Title (resolved) | Kind | Parent | XML | Data store | SettingsTree line |
|---|---|---|---|---|---|---|---|
| 1 | HomePreferencesFragment | Home | section |  | home_preferences.xml |  | 126 |
| 2 | LauncherLockPreferencesFragment | Double-tap to lock | page | HomePreferencesFragment | launcher_lock_preferences.xml |  | 128 |
| 3 | LauncherPreferencesFragment | Apps | section |  | launcher_preferences.xml | TermuxStylePreferencesDataStore (LauncherPreferencesFragment.java:61); Linux-apps row DrawerAppsDataStore (:70) | 132 |
| 4 | AppDrawerPreferencesFragment | Drawer layout | page | LauncherPreferencesFragment | app_drawer_preferences.xml | TermuxStylePreferencesDataStore (AppDrawerPreferencesFragment.java:89) | 133 |
| 5 | LauncherSearchPreferencesFragment | App search | page | LauncherPreferencesFragment | launcher_search_preferences.xml |  | 135 |
| 6 | TerminalPreferencesFragment | Terminal | section |  | terminal_preferences.xml | TerminalPreferencesDataStore (TerminalPreferencesFragment.java:39) | 139 |
| 7 | TerminalWindowsPreferencesFragment | Windows & panes | page | TerminalPreferencesFragment | terminal_windows_preferences.xml |  | 140 |
| 8 | StatusBarPreferencesFragment | Status bar | page | TerminalPreferencesFragment | status_bar_preferences.xml | TerminalIOPreferencesDataStore (StatusBarPreferencesFragment.java:37) | 142 |
| 9 | TerminalPreferencesFragment | Text & display | page | TerminalPreferencesFragment | terminal_text_preferences.xml | TerminalPreferencesDataStore (TerminalPreferencesFragment.java:39) | 145 |
| 10 | TermuxFontsPreferencesFragment | Terminal fonts | page | TerminalTextPreferencesFragment | termux_fonts_preferences.xml |  | 146 |
| 11 | TuiAppThemesPreferencesFragment | TUI app themes | page | TerminalPreferencesFragment | tui_app_themes_preferences.xml | TermuxStylePreferencesDataStore (TuiAppThemesPreferencesFragment.java:50) | 148 |
| 12 | TerminalClipboardPreferencesFragment | Clipboard & compatibility | page | TerminalPreferencesFragment | terminal_clipboard_preferences.xml |  | 150 |
| 13 | X11DisplayPreferencesFragment | Linux apps | section |  | x11_display_preferences.xml | X11DisplayPreferencesDataStore (X11DisplayPreferencesFragment.java:77) | 155 |
| 14 | GuiAppsSetupGuideFragment | Set up Linux apps | editor | X11DisplayPreferencesFragment | (built in code) |  | 158 |
| 15 | GraphicsAccelerationFragment | Graphics acceleration | page | X11DisplayPreferencesFragment | (built in code) |  | 160 |
| 16 | DesktopsPreferencesFragment | Desktops | page | X11DisplayPreferencesFragment | (built in code) |  | 161 |
| 17 | X11DisplayPreferencesFragment | Per-app settings | page | X11DisplayPreferencesFragment | (built in code) | X11DisplayPreferencesDataStore (X11DisplayPreferencesFragment.java:77) | 163 |
| 18 | X11AppPreferencesFragment | Per-app settings | editor | X11AppsPreferencesFragment | (built in code) |  | 166 |
| 19 | X11DisplayInputPreferencesFragment | Input | page | X11DisplayPreferencesFragment | x11_display_input_preferences.xml |  | 167 |
| 20 | X11DisplayResolutionPreferencesFragment | Resolution and scaling | page | X11DisplayPreferencesFragment | x11_display_resolution_preferences.xml |  | 169 |
| 21 | X11DisplayStartupPreferencesFragment | Startup | page | X11DisplayPreferencesFragment | x11_display_startup_preferences.xml |  | 171 |
| 22 | X11DisplayTroubleshootingPreferencesFragment | Fixes | page | X11DisplayPreferencesFragment | x11_display_troubleshooting_preferences.xml |  | 173 |
| 23 | TermuxStylePreferencesFragment | Appearance | door |  | appearance_search_index.xml |  | 179 |
| 24 | KeyboardPreferencesFragment | Keyboard | section |  | termux_keyboard_preferences.xml | a store set at KeyboardPreferencesFragment.java:98 | 183 |
| 25 | KeyboardPreferencesFragment | Layouts | page | KeyboardPreferencesFragment | termux_keyboard_layout_preferences.xml | a store set at KeyboardPreferencesFragment.java:98 | 186 |
| 26 | KeyboardLayoutsFragment | Layouts | editor | KeyboardLayoutPreferencesFragment | (built in code) |  | 188 |
| 27 | KeyboardLayoutsFragment | Edit layout | editor | KeyboardLayoutsFragment | (built in code) |  | 189 |
| 28 | KeyboardTypingPreferencesFragment | Typing and feedback | page | KeyboardPreferencesFragment | termux_keyboard_typing_preferences.xml |  | 191 |
| 29 | KeyboardPreferencesFragment | Voice input | page | KeyboardPreferencesFragment | termux_keyboard_voice_preferences.xml | a store set at KeyboardPreferencesFragment.java:98 | 194 |
| 30 | KeyboardVoiceDetailsPreferencesFragment | Fine-tuning | page | KeyboardVoicePreferencesFragment | termux_keyboard_voice_details_preferences.xml |  | 195 |
| 31 | KeyboardHardwarePreferencesFragment | Hardware keyboard | page | KeyboardPreferencesFragment | termux_keyboard_hardware_preferences.xml |  | 197 |
| 32 | KeyboardColorSchemeFragment | Keyboard theme | editor | KeyboardPreferencesFragment | (built in code) |  | 199 |
| 33 | ExtraKeysEditorFragment | Extra keys | editor | KeyboardPreferencesFragment | (built in code) |  | 200 |
| 34 | NotificationsPreferencesFragment | Notifications | section |  | notifications_preferences.xml | a store set at NotificationsPreferencesFragment.java:44 | 203 |
| 35 | EssentialNotificationRulesFragment | Pinned notifications | page | NotificationsPreferencesFragment | (built in code) |  | 205 |
| 36 | NotificationHistoryFragment | Notification history | page | NotificationsPreferencesFragment | (built in code) |  | 207 |
| 37 | TaiPreferencesFragment | On-device AI | section |  | termux_ai_preferences.xml |  | 210 |
| 38 | TaiRuntimePreferencesFragment | Runtime | page | TaiPreferencesFragment | tai_runtime_preferences.xml |  | 211 |
| 39 | TaiApiPreferencesFragment | Local API | page | TaiPreferencesFragment | tai_api_preferences.xml |  | 213 |
| 40 | TaiAdvancedPreferencesFragment | Advanced | page | TaiPreferencesFragment | tai_advanced_preferences.xml |  | 214 |
| 41 | TaiRemotePreferencesFragment | Remote model | page | TaiPreferencesFragment | tai_remote_preferences.xml |  | 216 |
| 42 | ReadAloudPreferencesFragment | Read aloud | page | TaiPreferencesFragment | read_aloud_preferences.xml |  | 218 |
| 43 | TaiModelCentreFragment | Model centre | page | TaiPreferencesFragment | (built in code) |  | 219 |
| 44 | PerformancePreferencesFragment | Battery & performance | section |  | performance_preferences.xml | RegistryPreferenceDataStore.forDevice (PerformancePreferencesFragment.java:64) | 222 |
| 45 | GeneralPreferencesFragment | General | section |  | general_preferences.xml | BehaviorDataStore (GeneralPreferencesFragment.java:37) | 225 |
| 46 | BackupRestorePreferencesFragment | Backup & restore | page | GeneralPreferencesFragment | backup_restore_preferences.xml |  | 226 |
| 47 | GeneralPreferencesFragment | Permissions & services | page | GeneralPreferencesFragment | services_permissions_preferences.xml | BehaviorDataStore (GeneralPreferencesFragment.java:37) | 229 |
| 48 | PrivilegedAccessPreferencesFragment | Shizuku and root | page | ServicesPermissionsPreferencesFragment | termux_privileged_access_preferences.xml | PrivilegedAccessPreferencesDataStore (PrivilegedAccessPreferencesFragment.java:42) | 231 |
| 49 | TermuxAPIPreferencesFragment | Termux:API | page | ServicesPermissionsPreferencesFragment | termux_api_preferences.xml | TermuxAPIPreferencesDataStore (TermuxAPIPreferencesFragment.java:20) | 233 |
| 50 | AdvancedDiagnosticsPreferencesFragment | Diagnostics | page | GeneralPreferencesFragment | advanced_diagnostics_preferences.xml | AdvancedDataStore (AdvancedDiagnosticsPreferencesFragment.java:52) | 235 |
| 51 | AboutSupportPreferencesFragment | About & help | page | GeneralPreferencesFragment | about_support_preferences.xml |  | 237 |

Screens with no XML (built in code): Set up Linux apps guide, Graphics acceleration, Desktops, per-app list and per-app page, Essential notification rules, Notification history, Model centre, Keyboard layouts and layout editor, Keyboard colour scheme, Extra keys editor (`SettingsTree.java:136-218`). Their rows were not enumerated; they are large fragments (`GraphicsAccelerationFragment.java` 835 lines, `TaiModelCentreFragment.java` 1749, `ExtraKeysEditorFragment.java` 1135, etc.).

### 1.3 Row layout rule and the exemption list

`SettingsLayoutUtils.applyItemLayout` (`app/src/main/java/com/termux/app/fragments/settings/SettingsLayoutUtils.java:87`) overwrites every row's layout resource, switch widget and chevron, except rows that "fully own their layout", which return early (`SettingsLayoutUtils.java:96-109`): `SegmentedPillPreference`, `SliderPreference`, `CategorySortProgressPreference`, `StatusActionPreference`, `SettingsSearchPreference`, `StatusCardPreference`, `DesktopInstallTerminalPreference`, `ProcessLimitNoticePreference`, `TaiOverridesPreference`, `TaiCatalogFilterPreference`, `TaiModelCentreRowPreference`, `TaiModelPreference`. `PillPreference` keeps its trailing pill widget (`:126`). Rows get an icon from `SettingsRowIcons` unless they declare one (`:91-95`).

### 1.4 Visibility and dependency rules coded in the fragments

XML `dependency` attributes are used only where shown in the preference tables below; most rules are code:

| Page | Rule | Source |
|---|---|---|
| Home | "Overview" (`home_edit_pages`) is disabled and unselectable while "Lock Home layout" is on, summary switches to the locked text | `fragments/settings/termux/HomePreferencesFragment.java:67-77` |
| Home | `home_gestures_helper` hint shown only while a gesture needs a helper (accessibility or Shizuku) that is missing; each gesture list is refilled on resume | `HomeGesturesRows.java:79`, `launcher/home/HomeGestures.java` (Action.needsHelper) |
| Apps | "Show Linux apps in drawer" row hidden where X11 is not available | `LauncherPreferencesFragment.java:69` |
| Drawer layout | Category sorting / Re-sort / Reset category order only for `categories` and `default` view types; Columns hidden for `categories`; Rows only for `horizontal` | `AppDrawerPreferencesFragment.java:168-186` |
| Status bar | Margin hidden when cutout mode is `off`; the three Move/Size sliders only when `custom` | `StatusBarPreferencesFragment.java:100-117` |
| Terminal | Engine row and "reset clipboard" shown only when `TerminalEngine.isAvailable()`; the `terminal_retro_effect`, `terminal_screen_events`, `terminal_retro_composite` finds are dead (keys not in this XML, see section 5) | `TerminalPreferencesFragment.java:41-52` |
| Fonts | Ligature/weight/Nerd icons enabled by font state; "Use font.ttf" enabled only when managed config exists | `TermuxFontsPreferencesFragment.java:271-293` |
| Performance | Effect/screen events/composite/idle rows only where `PaneRetroEffect.available()`; Live blur only `LiveBlurPolicy.offered(SDK_INT)`; motion rows greyed with a reason (low-memory preset, idle reduced, needs Android 13, needs picked wallpaper, needs glass refraction) | `PerformancePreferencesFragment.java:68-78`, `PerformanceRowPolicy.java` |
| Keyboard | Look/Layout/feedback rows disabled while the input method is not built in | `KeyboardPreferencesFragment.java:250-256` |
| Linux apps | Display backend rows hidden where unsupported; Start command only for the custom start; Scale/Fixed size/Custom size per resolution mode (`scaled`/`exact`/`custom`) | `X11DisplayPreferencesFragment.java:110-111, 214-224` |
| Linux apps input | Keyboard behaviour/paste shortcut enabled by readiness | `X11DisplayInputPreferencesFragment.java:105` |
| Privileged access | Request row visibility by state | `PrivilegedAccessPreferencesFragment.java:187` |
| Read aloud | "missing" notice shown when the voice is not installed; sample disabled | `ReadAloudPreferencesFragment.java:89-96` |
| TUI app themes | Templates list hidden when no entries | `TuiAppThemesPreferencesFragment.java:86` |
| Advanced diagnostics | API row hidden when `apiPreferences` is null | `AdvancedDiagnosticsPreferencesFragment.java:93, 140` |

### 1.5 Every preference row

Columns: resolved title, key, preference class, XML `defaultValue`, entries/range/attributes (array contents resolved), where the key is read (and a same-named code default), XML line. `persistent=false` rows are actions or doors and store nothing.


#### `app/src/main/res/xml/root_preferences.xml`

| Title (resolved) | Key | Type | XML default | Entries / range / attrs | Read by (first sites) | L |
|---|---|---|---|---|---|---|
|  | `process_limit_notice` | ProcessLimitNoticePreference |  | persistent=false | a/ai/TaiRemoteCatalog.java:27, a/ai/TaiRemoteCatalog.java:52 (+23) | 11 |
| Search settings | `settings_search` | SettingsSearchPreference |  | persistent=false | a/app/activities/SettingsActivity.java:991, a/app/fragments/settings/SettingsSearchFragment.java:41 | 15 |
| **[Your launcher]** | | category | | | | |
| Home | `home` | Preference |  | fragment=HomePreferencesFragment; summary="Pages, widgets and gestures" | a/app/chrome/ManagedWallpaper.java:50, a/app/chrome/ManagedWallpaper.java:62 (+783) | 22 |
| Apps | `dock_drawer` | Preference |  | fragment=LauncherPreferencesFragment; summary="Drawer, dock items and search" | a/app/fragments/settings/SettingsTree.java:41 | 29 |
| Terminal | `terminal` | Preference |  | fragment=TerminalPreferencesFragment; summary="Windows, status bar and text" | a/app/TermuxActivity.java:524, a/app/TermuxActivity.java:527 (+678) | 36 |
| Linux apps | `linux_apps` | Preference |  | fragment=X11DisplayPreferencesFragment; summary="Set up, window and start-up" | a/app/fragments/settings/SettingsTree.java:43, a/app/launcher/drawer/AppDrawerCategory.java:28 (+4) | 44 |
| **[Everywhere]** | | category | | | | |
| Appearance | `look` | Preference |  | persistent=false; summary="Wallpaper, glass, theme and icons" | a/app/fragments/settings/SettingsTree.java:45, a/app/help/HelpGlossary.java:93 (+1) | 55 |
| Keyboard | `keyboard_input` | Preference |  | fragment=KeyboardPreferencesFragment; summary="Layouts, typing and voice" | a/app/fragments/settings/SettingsTree.java:46 | 62 |
| Notifications | `notifications` | Preference |  | fragment=NotificationsPreferencesFragment; summary="Dots, pinned alerts and history" | a/app/fragments/settings/SettingsTree.java:47, a/app/launcher/home/HomeGestures.java:65 (+36) | 69 |
| On-device AI | `on_device_ai` | Preference |  | fragment=TaiPreferencesFragment; summary="Models, voice and assistant" | a/app/fragments/settings/SettingsTree.java:48, a/app/help/HelpDiagrams.java:63 (+3) | 76 |
| **[System]** | | category | | | | |
| Battery & performance | `performance` | Preference |  | fragment=PerformancePreferencesFragment; summary="Idle activity, low-memory preset and motion" | a/app/fragments/settings/SettingsTree.java:49, a/app/terminal/TerminalActionDispatcher.java:1799 | 87 |
| General | `general` | Preference |  | fragment=GeneralPreferencesFragment; summary="Motion, vibration, backup and about" | a/app/fragments/settings/SettingsTree.java:50 | 94 |

#### `app/src/main/res/xml/home_preferences.xml`

| Title (resolved) | Key | Type | XML default | Entries / range / attrs | Read by (first sites) | L |
|---|---|---|---|---|---|---|
| Edit on screen | `home_layout_editor` | Preference |  | persistent=false; summary="Edit how it looks and sits" | a/app/fragments/settings/SettingsRowIcons.java:155, a/app/fragments/settings/termux/HomePreferencesFragment.java:34 | 10 |
| **[Gestures]** | | category | | | | |
| Swipe up | `app_launcher_home_palette_gesture` | ListPreference |  |  | a/app/fragments/settings/SettingsRowIcons.java:162, a/app/fragments/settings/SettingsTree.java:381 (+5) ; code default TermuxAppSharedPreferences.java:1789 | 22 |
| Swipe down | `app_launcher_home_gesture_swipe_down` | ListPreference |  |  | a/app/fragments/settings/SettingsRowIcons.java:163, a/app/launcher/home/HomeGestures.java:36 | 26 |
| Double tap | `app_launcher_home_gesture_double_tap` | ListPreference |  |  | a/app/fragments/settings/SettingsRowIcons.java:165, a/app/launcher/home/HomeGestures.java:37 | 30 |
| Two-finger swipe up | `app_launcher_home_gesture_two_finger_up` | ListPreference |  |  | a/app/fragments/settings/SettingsRowIcons.java:166, a/app/launcher/home/HomeGestures.java:38 | 34 |
| Two-finger swipe down | `app_launcher_home_gesture_two_finger_down` | ListPreference |  |  | a/app/fragments/settings/SettingsRowIcons.java:167, a/app/launcher/home/HomeGestures.java:39 | 38 |
| More gestures | `home_gestures_helper` | Preference |  | isPreferenceVisible=false; persistent=false; summary="Turn on the launcher's accessibility service, or connect Shizuku, to lock the screen or open notific" | a/app/fragments/settings/SettingsRowIcons.java:168, a/app/fragments/settings/termux/HomeGesturesRows.java:77 | 42 |
| Double-tap to lock | `app_launcher_sub_lock` | Preference |  | fragment=LauncherLockPreferencesFragment; persistent=false; summary="Shizuku, or optional accessibility access" | a/app/fragments/settings/SettingsRowIcons.java:175, a/app/fragments/settings/SettingsTree.java:383 | 48 |
| Swipe up on app icons for quick reply | `app_launcher_quick_reply_swipe` | SwitchPreferenceCompat | true | summary="Swipe up on an app icon with a notification dot to reply from the dock" | a/app/fragments/settings/SettingsRowIcons.java:164, a/app/fragments/settings/SettingsTree.java:382 (+10) ; code default TermuxAppSharedPreferences.java:427 | 54 |
| Swipe on the terminal to switch windows | `terminal_body_swipe` | SwitchPreferenceCompat | true | summary="Swipe sideways across the text to move between windows and Home" | a/app/fragments/settings/SettingsRowIcons.java:248, a/app/fragments/settings/SettingsTree.java:408 (+6) ; code default TermuxAppSharedPreferences.java:996 | 59 |
| **[Home widgets]** | | category | | | | |
| Home widgets | `app_launcher_widget_pane_enabled` | SwitchPreferenceCompat | true | summary="Show a widget page beside the terminal" | a/app/fragments/settings/SettingsRowIcons.java:169, a/app/fragments/settings/SettingsTree.java:381 (+3) | 67 |
| **[Pages]** | | category | | | | |
| Overview | `home_edit_pages` | Preference |  | persistent=false; summary="See, reorder, add or remove your pages and Workspaces" | a/app/fragments/settings/SettingsRowIcons.java:172, a/app/fragments/settings/termux/HomePreferencesFragment.java:51 (+1) | 76 |
| Lock Home layout | `app_launcher_home_layout_locked` | SwitchPreferenceCompat | false | summary="Stop icons and widgets from being moved by accident. Taps still open them" | a/app/fragments/settings/SettingsRowIcons.java:174, a/app/fragments/settings/termux/HomePreferencesFragment.java:46 (+4) | 82 |

#### `app/src/main/res/xml/launcher_lock_preferences.xml`

| Title (resolved) | Key | Type | XML default | Entries / range / attrs | Read by (first sites) | L |
|---|---|---|---|---|---|---|
| **[Lock method]** | | category | | | | |
| Double tap A–Z Row to lock screen | `app_launcher_az_lock_method` | SegmentedPillPreference | off | entries=[Off, Shizuku, Root, Accessibility]; entryValues=[off, shizuku, root, accessibility]; summary="Double-tap the A–Z row. Shizuku keeps the normal screen-off animation. Accessibility is optional and" | a/app/fragments/settings/SettingsRowIcons.java:174, a/app/fragments/settings/SettingsTree.java:379 (+7) ; code default TermuxAppSharedPreferences.java:1954 | 7 |

#### `app/src/main/res/xml/launcher_preferences.xml`

| Title (resolved) | Key | Type | XML default | Entries / range / attrs | Read by (first sites) | L |
|---|---|---|---|---|---|---|
| **[Dock]** | | category | | | | |
| Pinned apps | `app_launcher_default_buttons` | Preference |  | summary="Choose and reorder dock apps" | a/app/fragments/settings/SettingsRowIcons.java:181, a/app/fragments/settings/termux/LauncherPreferencesFragment.java:83 (+4) ; code default TermuxAppSharedPreferences.java:249 | 9 |
| Most-used apps page | `app_launcher_most_used_page` | SwitchPreferenceCompat | false | summary="Adds a page ranked by launcher usage" | a/app/fragments/settings/SettingsRowIcons.java:182, a/app/fragments/settings/termux/TermuxStylePreferencesDataStore.java:120 (+8) ; code default TermuxAppSharedPreferences.java:1888 | 13 |
| **[Drawer]** | | category | | | | |
| Drawer layout | `app_launcher_drawer_layout` | Preference |  | fragment=AppDrawerPreferencesFragment | a/app/fragments/settings/SettingsRowIcons.java:183, a/app/fragments/settings/termux/LauncherPreferencesFragment.java:137 | 22 |
| App search | `app_launcher_sub_search` | Preference |  | fragment=LauncherSearchPreferencesFragment; persistent=false; summary="Terminal search prefix and ranking" | a/app/fragments/settings/SettingsRowIcons.java:184 | 26 |
| Show Linux apps in drawer | `x11_drawer_apps` | SwitchPreferenceCompat | true | summary="Tapping one opens it on the display" | a/app/fragments/settings/SettingsRowIcons.java:125, a/app/fragments/settings/SettingsTree.java:448 (+3) ; code default LauncherAppDataProvider.java:279 | 33 |

#### `app/src/main/res/xml/app_drawer_preferences.xml`

| Title (resolved) | Key | Type | XML default | Entries / range / attrs | Read by (first sites) | L |
|---|---|---|---|---|---|---|
| Grid | `app_launcher_drawer_view_type` | SegmentedPillPreference | default | entries=[Default, Vertical, Pages, Categories]; entryValues=[default, vertical, horizontal, categories] | a/app/fragments/settings/SettingsRowIcons.java:81, a/app/fragments/settings/termux/TermuxStylePreferencesDataStore.java:284 (+5) | 7 |
| Icon size | `app_launcher_drawer_icon_size` | SegmentedPillPreference | auto | entries=[Auto, Small, Medium, Large]; entryValues=[auto, 48, 54, 60] | a/app/fragments/settings/SettingsRowIcons.java:82, a/app/fragments/settings/termux/TermuxStylePreferencesDataStore.java:288 (+5) ; code default TermuxAppSharedPreferences.java:449 | 14 |
| Columns | `app_launcher_drawer_columns` | SegmentedPillPreference | auto | entries=[Auto, 4, 5, 6]; entryValues=[auto, 4, 5, 6] | a/app/fragments/settings/SettingsRowIcons.java:83, a/app/fragments/settings/termux/TermuxStylePreferencesDataStore.java:292 (+4) ; code default HomePinRequestActivity.java:154 | 21 |
| Rows | `app_launcher_drawer_rows` | SegmentedPillPreference | auto | entries=[Auto, 3, 4, 5]; entryValues=[auto, 3, 4, 5] | a/app/fragments/settings/SettingsRowIcons.java:84, a/app/fragments/settings/termux/TermuxStylePreferencesDataStore.java:296 (+4) ; code default LauncherConfigRepository.java:465 | 28 |
| A–Z strip | `app_launcher_drawer_az_side` | SegmentedPillPreference | right | entries=[Left, Right, Off]; entryValues=[left, right, off] | a/app/fragments/settings/SettingsRowIcons.java:85, a/app/launcher/drawer/DrawerSettings.java:40 (+1) | 35 |
| Open keyboard automatically | `app_launcher_drawer_search_on_open` | SwitchPreferenceCompat | false | summary="When the app drawer opens." | a/app/fragments/settings/SettingsRowIcons.java:69, a/app/fragments/settings/termux/TermuxStylePreferencesDataStore.java:124 (+3) | 42 |
| Use system keyboard | `app_launcher_drawer_search_android_keyboard` | SwitchPreferenceCompat | false | summary="Search with the phone's own keyboard instead of the launcher's, with its suggestions and swipe typin" | a/app/fragments/settings/SettingsRowIcons.java:86, a/app/fragments/settings/termux/TermuxStylePreferencesDataStore.java:128 (+2) | 48 |
| Search bar at the bottom | `app_launcher_drawer_search_bottom` | SwitchPreferenceCompat | false | summary="Search sits at the bottom of the drawer, within thumb reach." | a/app/fragments/settings/SettingsRowIcons.java:87, a/app/launcher/drawer/DrawerSettings.java:39 (+1) | 54 |
| Remember position | `app_launcher_drawer_remember_position` | SwitchPreferenceCompat | false | summary="Reopen the drawer where you left it." | a/app/fragments/settings/SettingsRowIcons.java:88, a/app/launcher/drawer/DrawerSettings.java:38 (+1) | 60 |
| Start in the lower half | `app_launcher_drawer_lower_start` | SwitchPreferenceCompat | false | summary="Apps begin lower on the screen, closer to your thumb." | a/app/fragments/settings/SettingsRowIcons.java:89, a/app/launcher/drawer/DrawerSettings.java:39 (+1) | 66 |
| Category sorting | `app_launcher_category_sort` | Preference |  | summary="Choose how apps are grouped." | a/app/fragments/settings/SettingsRowIcons.java:90, a/app/fragments/settings/termux/AppDrawerPreferencesFragment.java:128 (+1) | 72 |
| Re-sort apps | `app_launcher_category_refresh` | CategorySortProgressPreference |  |  | a/app/fragments/settings/SettingsLayoutUtils.java:166, a/app/fragments/settings/SettingsRowIcons.java:91 (+4) | 77 |
| Reset category order | `app_launcher_category_order_reset` | Preference |  | summary="Put the category tiles back in their original order." | a/app/fragments/settings/SettingsLayoutUtils.java:166, a/app/fragments/settings/SettingsRowIcons.java:92 (+2) | 81 |

#### `app/src/main/res/xml/launcher_search_preferences.xml`

| Title (resolved) | Key | Type | XML default | Entries / range / attrs | Read by (first sites) | L |
|---|---|---|---|---|---|---|
| **[Search]** | | category | | | | |
| App search prefix | `app_launcher_input_char` | EditTextPreference | % | summary="Type this character in the terminal to start app search." | a/app/fragments/settings/SettingsRowIcons.java:185, a/app/fragments/settings/SettingsTree.java:377 (+5) ; code default TermuxAppSharedPreferences.java:232 | 7 |
| Open the only match | `app_launcher_drawer_open_only_match` | SwitchPreferenceCompat | false | summary="Opens the app by itself when a search finds just one." | a/app/fragments/settings/SettingsRowIcons.java:186, a/app/launcher/drawer/DrawerSettings.java:38 (+1) | 13 |
| Reset usage ranking | `app_launcher_reset_usage_ranking` | Preference |  | summary="Clears learned app-use ranking without changing pinned apps." | a/app/fragments/settings/SettingsRowIcons.java:187, a/app/fragments/settings/SettingsTree.java:377 (+1) | 18 |

#### `app/src/main/res/xml/terminal_preferences.xml`

| Title (resolved) | Key | Type | XML default | Entries / range / attrs | Read by (first sites) | L |
|---|---|---|---|---|---|---|
| Edit on screen | `terminal_layout_editor` | Preference |  | persistent=false; summary="Edit how it looks and sits" | a/app/fragments/settings/SettingsRowIcons.java:155, a/app/fragments/settings/termux/TerminalPreferencesFragment.java:62 | 8 |
| Windows & panes | `terminal_sub_windows` | Preference |  | fragment=TerminalWindowsPreferencesFragment; persistent=false; summary="Key hints and program control" | a/app/fragments/settings/SettingsRowIcons.java:238 | 14 |
| Status bar | `status_bar` | Preference |  | fragment=StatusBarPreferencesFragment; persistent=false; summary="Weather place and camera gap" | a/app/fragments/settings/SettingsRowIcons.java:239, a/app/place/Element.java:22 | 21 |
| Text & display | `terminal_sub_text` | Preference |  | fragment=TerminalTextPreferencesFragment; persistent=false; summary="Terminal font" | a/app/fragments/settings/SettingsRowIcons.java:240 | 28 |
| TUI app themes | `terminal_sub_tui_themes` | Preference |  | fragment=TuiAppThemesPreferencesFragment; persistent=false; summary="Tools that follow the terminal colours" | a/app/fragments/settings/SettingsRowIcons.java:284 | 35 |
| Clipboard & compatibility | `terminal_sub_clipboard` | Preference |  | fragment=TerminalClipboardPreferencesFragment; persistent=false; summary="Clipboard access and the system keyboard" | a/app/fragments/settings/SettingsRowIcons.java:142 | 42 |
| Notifications | `terminal_notifications` | Preference |  | persistent=false; summary="Choose which terminal events reach your phone's notifications." | a/app/fragments/settings/termux/TerminalPreferencesFragment.java:71 | 49 |

#### `app/src/main/res/xml/terminal_windows_preferences.xml`

| Title (resolved) | Key | Type | XML default | Entries / range / attrs | Read by (first sites) | L |
|---|---|---|---|---|---|---|
| Edit on screen | `terminal_windows_layout_editor` | Preference |  | persistent=false; summary="Edit how it looks and sits" | a/app/fragments/settings/SettingsRowIcons.java:156, a/app/fragments/settings/termux/TerminalPreferencesFragment.java:63 | 8 |
| **[Sessions and panes]** | | category | | | | |
| Shortcut hints | `show_key_hints` | SwitchPreferenceCompat | true | summary="Show pane shortcuts while holding Ctrl+Alt. Off lights the ? key instead." | a/app/fragments/settings/SettingsRowIcons.java:244, a/app/fragments/settings/SettingsTree.java:406 (+5) ; code default TermuxAppSharedPreferences.java:960 | 14 |
| Let programs and agents control panes | `pane_agent_api` | SwitchPreferenceCompat | true | summary="Programs you run can open, switch and close panes on their own." | a/app/fragments/settings/SettingsRowIcons.java:231, a/app/fragments/settings/SettingsTree.java:406 (+4) ; code default TermuxAppSharedPreferences.java:987 | 19 |

#### `app/src/main/res/xml/status_bar_preferences.xml`

| Title (resolved) | Key | Type | XML default | Entries / range / attrs | Read by (first sites) | L |
|---|---|---|---|---|---|---|
| Edit on screen | `status_layout_editor` | Preference |  | persistent=false; summary="Edit how it looks and sits" | a/app/fragments/settings/SettingsRowIcons.java:156, a/app/fragments/settings/termux/StatusBarPreferencesFragment.java:40 | 6 |
| **[Weather]** | | category | | | | |
| Location | `status_widget_weather_location` | Preference |  | persistent=false; summary="Uses your device location" | a/app/fragments/settings/SettingsRowIcons.java:212, a/app/fragments/settings/SettingsTree.java:418 (+4) ; code default TermuxAppSharedPreferences.java:1249 | 15 |
| **[Camera cutout]** | | category | | | | |
| Keep clear of the camera | `status_bar_cutout_mode` | SegmentedPillPreference | auto | entries=[Auto, Custom, Off]; entryValues=[auto, custom, off] | a/app/fragments/settings/termux/StatusBarPreferencesFragment.java:46, a/app/fragments/settings/termux/TerminalIOPreferencesDataStore.java:162 (+4) ; code default TermuxAppSharedPreferences.java:1137 | 25 |
| Margin | `status_bar_cutout_margin` | SliderPreference | 4 | min=0; max=24; summary="Space between the camera and the bar." | a/app/fragments/settings/termux/StatusBarPreferencesFragment.java:102, a/app/fragments/settings/termux/StatusBarPreferencesFragment.java:110 (+4) ; code default TermuxActivity.java:10889 | 31 |
| Move sideways | `status_bar_cutout_offset_x` | SliderPreference | 0 | min=-200; max=200; summary="Lines the gap up with the camera." | a/app/fragments/settings/termux/StatusBarPreferencesFragment.java:103, a/app/fragments/settings/termux/StatusBarPreferencesFragment.java:105 (+4) ; code default TermuxAppSharedPreferences.java:1155 | 39 |
| Move down | `status_bar_cutout_offset_y` | SliderPreference | 0 | min=-24; max=24 | a/app/fragments/settings/termux/StatusBarPreferencesFragment.java:103, a/app/fragments/settings/termux/StatusBarPreferencesFragment.java:106 (+4) ; code default TermuxAppSharedPreferences.java:1171 | 47 |
| Size | `status_bar_cutout_scale` | SliderPreference | 100 | min=50; max=200 | a/app/fragments/settings/termux/StatusBarPreferencesFragment.java:103, a/app/fragments/settings/termux/StatusBarPreferencesFragment.java:106 (+4) ; code default TermuxActivity.java:10886 | 54 |

#### `app/src/main/res/xml/terminal_text_preferences.xml`

| Title (resolved) | Key | Type | XML default | Entries / range / attrs | Read by (first sites) | L |
|---|---|---|---|---|---|---|
| Edit on screen | `terminal_text_layout_editor` | Preference |  | persistent=false; summary="Edit how it looks and sits" | a/app/fragments/settings/SettingsRowIcons.java:156, a/app/fragments/settings/termux/TerminalPreferencesFragment.java:62 | 8 |
| **[Terminal font]** | | category | | | | |
| Terminal fonts | `terminal_fonts` | Preference |  | fragment=TermuxFontsPreferencesFragment; persistent=false; summary="Install a multi-face font with Nerd Font icons from the bundled catalog" | a/app/fragments/settings/SettingsRowIcons.java:241, a/app/fragments/settings/SettingsTree.java:397 (+1) | 14 |

#### `app/src/main/res/xml/termux_fonts_preferences.xml`

| Title (resolved) | Key | Type | XML default | Entries / range / attrs | Read by (first sites) | L |
|---|---|---|---|---|---|---|
| **[Setup]** | | category | | | | |
| Active font | `fonts_status` | StatusCardPreference |  | persistent=false | a/app/fragments/settings/termux/TermuxFontsPreferencesFragment.java:255 | 8 |
| **[Tuning]** | | category | | | | |
| Edit on screen | `fonts_layout_editor` | Preference |  | persistent=false; summary="Edit how it looks and sits" | a/app/fragments/settings/SettingsRowIcons.java:157, a/app/fragments/settings/termux/TermuxFontsPreferencesFragment.java:148 | 18 |
| Nerd Font icons | `nerd_icons` | SwitchPreferenceCompat | true | summary="Route U+E000–U+F8FF and U+F0000–U+FFFFD to the bundled Symbols Nerd Font Mono, so powerline, devicon" | a/app/fragments/settings/SettingsRowIcons.java:251, a/app/fragments/settings/termux/TermuxFontsPreferencesFragment.java:156 (+7) | 24 |
| Ligature policy | `fonts_ligatures` | SegmentedPillPreference |  | persistent=false | a/app/fragments/settings/SettingsRowIcons.java:252, a/app/fragments/settings/termux/TermuxFontsPreferencesFragment.java:125 (+1) | 30 |
| Weight | `fonts_weight` | Preference |  | persistent=false | a/app/fragments/settings/SettingsRowIcons.java:253, a/app/fragments/settings/termux/TermuxFontsPreferencesFragment.java:140 (+1) | 35 |
| Smaller text size | `font_toggle_compact_size` | Preference |  | persistent=false; summary="What the status bar's text size button switches to" | a/app/TermuxActivity.java:32065, a/app/fragments/settings/termux/TermuxFontsPreferencesFragment.java:183 | 40 |
| **[Families]** | | category | | | | |
| **[Manual control]** | | category | | | | |
| Use font.ttf / Termux:Styling | `fonts_use_font_ttf` | Preference |  | persistent=false; summary="Removes the app-managed config so the terminal goes back to ~/.termux/font.ttf. Installed families s" | a/app/fragments/settings/SettingsRowIcons.java:241, a/app/fragments/settings/termux/TermuxFontsPreferencesFragment.java:170 (+1) | 55 |
| Hand-edited config | `fonts_power_user_hint` | Preference |  | persistent=false; summary="Write your own ~/.termux/fonts.conf: it is read after every fonts.d fragment, so it overrides everyt" | a/app/fragments/settings/SettingsRowIcons.java:254 | 61 |

#### `app/src/main/res/xml/tui_app_themes_preferences.xml`

| Title (resolved) | Key | Type | XML default | Entries / range / attrs | Read by (first sites) | L |
|---|---|---|---|---|---|---|
| Tools that follow the terminal colours | `theme_templates_enabled` | MultiSelectListPreference |  | summary="Starship, Helix and other tools can pick up the terminal palette." | a/app/fragments/settings/SettingsRowIcons.java:284, a/app/fragments/settings/SettingsTree.java:401 (+5) | 7 |

#### `app/src/main/res/xml/terminal_clipboard_preferences.xml`

| Title (resolved) | Key | Type | XML default | Entries / range / attrs | Read by (first sites) | L |
|---|---|---|---|---|---|---|
| **[Clipboard]** | | category | | | | |
| Let programs read the clipboard | `terminal_osc52_clipboard_read_enabled` | SwitchPreferenceCompat | true | summary="Programs in the terminal can paste what you copied." | a/app/fragments/settings/SettingsRowIcons.java:141, a/app/fragments/settings/SettingsTree.java:414 (+4) ; code default TermuxAppSharedPreferences.java:1379 | 8 |
| Reset clipboard permissions | `terminal_clipboard_read_reset` | Preference |  | summary="Programs you always allowed will ask again." | a/app/fragments/settings/SettingsRowIcons.java:237, a/app/fragments/settings/SettingsSearchIndex.java:125 (+2) | 13 |
| Clean up clipboard text | `terminal_clipboard_cleanup` | SwitchPreferenceCompat | true | summary="Trim trailing spaces and single-line paste newlines." | a/app/fragments/settings/SettingsRowIcons.java:234, a/app/fragments/settings/SettingsTree.java:414 (+4) ; code default TermuxAppSharedPreferences.java:1407 | 17 |
| **[Compatibility]** | | category | | | | |
| Keyboard resize workaround | `terminal_margin_adjustment` | SwitchPreferenceCompat |  | summary="Try if the Android keyboard covers terminal content. Turn off if it causes gaps or jumpy resizing." | a/app/fragments/settings/SettingsRowIcons.java:235, a/app/fragments/settings/SettingsTree.java:415 (+8) ; code default TermuxAppSharedPreferences.java:2042 | 24 |
| Terminal engine | `terminal_engine` | ListPreference | standard | entries=[Standard (Termux), Fast (LibGhostty)]; entryValues=[standard, ghostty]; summary="Applies to panes you open after changing it." | a/app/fragments/settings/SettingsRowIcons.java:236, a/app/fragments/settings/SettingsSearchIndex.java:125 (+7) ; code default TermuxAppSharedPreferences.java:1317 | 28 |

#### `app/src/main/res/xml/x11_display_preferences.xml`

| Title (resolved) | Key | Type | XML default | Entries / range / attrs | Read by (first sites) | L |
|---|---|---|---|---|---|---|
| **[Apps]** | | category | | | | |
| Set up Linux apps | `x11_gui_apps_setup` | Preference |  | fragment=GuiAppsSetupGuideFragment; persistent=false; summary="Not set up yet" | a/app/fragments/settings/SettingsRowIcons.java:128, a/app/fragments/settings/termux/X11DisplayPreferencesFragment.java:63 | 9 |
| Desktops | `x11_desktops` | Preference |  | fragment=DesktopsPreferencesFragment; summary="Which desktops are installed" | a/app/fragments/settings/SettingsRowIcons.java:130 | 15 |
| Per-app settings | `x11_apps` | Preference |  | fragment=X11AppsPreferencesFragment; summary="Choose graphics, touch and scale for each app." | a/app/fragments/settings/SettingsRowIcons.java:129 | 20 |
| Display | `linux_display_backend` | ListPreference | x11 | entries=[X11 Display, Wayland Display]; entryValues=[x11, wayland] | a/app/fragments/settings/SettingsRowIcons.java:131, a/app/fragments/settings/SettingsSearchIndex.java:127 (+6) ; code default TermuxAppSharedPreferences.java:1714 | 26 |
|  | `linux_display_backend_hint` | Preference |  | persistent=false; summary="Wayland Display opens each Linux app in its own window. It is new and some apps may not work yet." | a/app/fragments/settings/SettingsRowIcons.java:53 | 34 |
| **[Desktop]** | | category | | | | |
| Start with | `x11_start_with` | ListPreference |  |  | a/app/fragments/settings/SettingsRowIcons.java:133, a/app/fragments/settings/termux/X11DisplayPreferencesFragment.java:128 (+6) ; code default TermuxAppSharedPreferences.java:1701 | 43 |
| Start command | `x11_start_with_command` | EditTextPreference |  | isPreferenceVisible=false | a/app/fragments/settings/SettingsRowIcons.java:134, a/app/fragments/settings/termux/X11DisplayPreferencesFragment.java:130 (+5) ; code default TermuxAppSharedPreferences.java:1727 | 48 |
| **[Configure]** | | category | | | | |
| Input | `x11_sub_input` | Preference |  | fragment=X11DisplayInputPreferencesFragment; persistent=false; summary="Touch mode, keyboard and clipboard" | a/app/fragments/settings/SettingsRowIcons.java:135 | 57 |
| Resolution and scaling | `x11_sub_resolution` | Preference |  | fragment=X11DisplayResolutionPreferencesFragment; persistent=false; summary="Size in percent, filtering and density in dpi" | a/app/fragments/settings/SettingsRowIcons.java:136 | 63 |
| **[Advanced]** | | category | | | | |
| Graphics acceleration | `x11_gpu` | Preference |  | fragment=GraphicsAccelerationFragment; persistent=false; summary="Test and choose the fastest graphics for Linux apps" | a/app/fragments/settings/SettingsRowIcons.java:120 | 72 |
| Window manager | `x11_window_manager` | EditTextPreference | openbox |  | a/app/fragments/settings/SettingsRowIcons.java:132, a/app/fragments/settings/termux/X11DisplayPreferencesFragment.java:277 (+3) ; code default TermuxAppSharedPreferences.java:1688 | 78 |
|  | `x11_window_manager_hint` | Preference |  | persistent=false; summary="Openbox arranges the windows, and a whole desktop brings its own instead" | a/app/fragments/settings/SettingsRowIcons.java:52 | 84 |
| Startup | `x11_sub_startup` | Preference |  | fragment=X11DisplayStartupPreferencesFragment; persistent=false; summary="Auto-start and shell connection" | a/app/fragments/settings/SettingsRowIcons.java:137 | 89 |
| Fixes | `x11_sub_troubleshooting` | Preference |  | fragment=X11DisplayTroubleshootingPreferencesFragment; persistent=false; summary="Black screen or wrong colors" | a/app/fragments/settings/SettingsRowIcons.java:138 | 95 |

#### `app/src/main/res/xml/x11_display_input_preferences.xml`

| Title (resolved) | Key | Type | XML default | Entries / range / attrs | Read by (first sites) | L |
|---|---|---|---|---|---|---|
| Touch mode | `touchMode` | SegmentedPillPreference | 1 | entries=[Trackpad, Touch, Direct]; entryValues=[1, 2, 3] | a/app/fragments/settings/SettingsRowIcons.java:106, a/app/fragments/settings/SettingsTree.java:438 (+7) | 6 |
| **[Keyboard]** | | category | | | | |
| Use Android keyboard | `x11_android_keyboard` | SwitchPreferenceCompat | false | summary="Type into display apps with the phone keyboard." | a/app/fragments/settings/SettingsRowIcons.java:71, a/app/fragments/settings/SettingsTree.java:438 (+4) ; code default TermuxAppSharedPreferences.java:1646 | 14 |
| Auto keyboard | `x11_keyboard_follows_text` | SwitchPreferenceCompat | true | summary="Automatic Keyboard show & hide when you tap text input field" | a/app/fragments/settings/SettingsRowIcons.java:139, a/app/fragments/settings/SettingsTree.java:438 (+5) ; code default TermuxAppSharedPreferences.java:1635 | 19 |
| Keyboard behavior | `x11_keyboard_over_display` | ListPreference |  | entries=[Overlay, Resize display]; entryValues=[overlay, resize]; persistent=false | a/app/fragments/settings/SettingsRowIcons.java:140, a/app/fragments/settings/termux/X11DisplayInputPreferencesFragment.java:63 (+1) | 24 |
|  | `None` | extra |  |  |  | 0 |
| **[Clipboard]** | | category | | | | |
| Share the clipboard | `clipboardEnable` | SwitchPreferenceCompat | true | summary="Copy and paste between the display and Android." | a/app/fragments/settings/SettingsRowIcons.java:141, a/app/fragments/settings/SettingsTree.java:439 | 35 |
| Paste shortcut | `x11_paste_shortcut` | ListPreference |  | entries=[Ctrl+V, Ctrl+Shift+V]; entryValues=[ctrl_v, ctrl_shift_v]; persistent=false; summary="The keys the paste key presses. Terminals usually paste with Ctrl+Shift+V." | a/app/fragments/settings/SettingsRowIcons.java:141, a/app/fragments/settings/SettingsTree.java:439 (+4) | 40 |
|  | `None` | extra |  |  |  | 0 |

#### `app/src/main/res/xml/x11_display_resolution_preferences.xml`

| Title (resolved) | Key | Type | XML default | Entries / range / attrs | Read by (first sites) | L |
|---|---|---|---|---|---|---|
| **[Resolution]** | | category | | | | |
| Resolution | `displayResolutionMode` | ListPreference | scaled | entries=[Same as the screen, Scaled (Default), Fixed size, Custom size]; entryValues=[native, scaled, exact, custom] | a/app/fragments/settings/SettingsRowIcons.java:143, a/app/fragments/settings/SettingsTree.java:441 (+3) | 7 |
| Scale | `displayScale` | SliderPreference | 100 | min=30; max=300 | a/app/fragments/settings/SettingsRowIcons.java:144, a/app/fragments/settings/SettingsTree.java:441 (+9) | 14 |
| Fixed size | `displayResolutionExact` | ListPreference | 1280x1024 | entries=[]; entryValues=[] | a/app/fragments/settings/SettingsRowIcons.java:145, a/app/fragments/settings/SettingsTree.java:441 (+1) | 22 |
| Custom size | `displayResolutionCustom` | EditTextPreference | 1280x1024 |  | a/app/fragments/settings/SettingsRowIcons.java:146, a/app/fragments/settings/SettingsTree.java:442 (+2) | 29 |
| **[Rendering]** | | category | | | | |
| Scaling | `displayFilteringMode` | SegmentedPillPreference | bilinear | entries=[Smooth, Sharp]; entryValues=[bilinear, nearest] | a/app/fragments/settings/SettingsRowIcons.java:147, a/app/fragments/settings/SettingsTree.java:442 | 38 |
| Display density | `x11_display_dpi` | SliderPreference | 0 | min=0; max=480; summary="Dots per inch (dpi). 0 lets the display choose." | a/app/fragments/settings/SettingsRowIcons.java:148, a/app/fragments/settings/SettingsTree.java:442 (+6) ; code default TermuxAppSharedPreferences.java:1604 | 44 |

#### `app/src/main/res/xml/x11_display_startup_preferences.xml`

| Title (resolved) | Key | Type | XML default | Entries / range / attrs | Read by (first sites) | L |
|---|---|---|---|---|---|---|
| **[Starting]** | | category | | | | |
| Start with the launcher | `x11_display_autostart` | SwitchPreferenceCompat | false | summary="Start the display whenever the launcher starts." | a/app/fragments/settings/SettingsRowIcons.java:137, a/app/fragments/settings/SettingsTree.java:444 (+8) ; code default WallPagesMigration.java:80 | 7 |
| Start command | `x11_display_command` | EditTextPreference | termux-x11 :0 |  | a/app/fragments/settings/SettingsRowIcons.java:149, a/app/fragments/settings/SettingsTree.java:444 (+4) ; code default TermuxAppSharedPreferences.java:1571 | 12 |
| Connect new shells | `x11_set_display_env` | SwitchPreferenceCompat | true | summary="Open Linux windows from new terminal sessions while the display runs." | a/app/fragments/settings/SettingsRowIcons.java:150, a/app/fragments/settings/SettingsTree.java:444 (+4) ; code default TermuxAppSharedPreferences.java:1593 | 17 |

#### `app/src/main/res/xml/x11_display_troubleshooting_preferences.xml`

| Title (resolved) | Key | Type | XML default | Entries / range / attrs | Read by (first sites) | L |
|---|---|---|---|---|---|---|
| **[Rendering fixes]** | | category | | | | |
| Compatibility drawing | `x11_legacy_drawing` | SwitchPreferenceCompat | false | summary="Try this if the display stays black." | a/app/fragments/settings/SettingsRowIcons.java:151, a/app/fragments/settings/SettingsTree.java:446 (+4) ; code default TermuxAppSharedPreferences.java:1614 | 7 |
| Swap colour channels | `x11_force_bgra` | SwitchPreferenceCompat | false | summary="Try this if colours look wrong on the display." | a/app/fragments/settings/SettingsRowIcons.java:152, a/app/fragments/settings/SettingsTree.java:446 (+4) ; code default TermuxAppSharedPreferences.java:1624 | 12 |

#### `app/src/main/res/xml/appearance_search_index.xml`

| Title (resolved) | Key | Type | XML default | Entries / range / attrs | Read by (first sites) | L |
|---|---|---|---|---|---|---|
| App theme | `theme_mode` | Preference |  | persistent=false | a/app/fragments/settings/SettingsRowIcons.java:281, a/app/fragments/settings/termux/TermuxStylePreferencesDataStore.java:252 (+3) | 9 |
| Wallpaper colors | `terminal_dynamic_colors_enabled` | Preference |  | persistent=false; summary="Match the launcher and terminal to the wallpaper." | a/app/fragments/settings/SettingsRowIcons.java:282, a/app/fragments/settings/termux/TermuxStylePreferencesDataStore.java:91 (+11) ; code default TermuxAppSharedPreferences.java:4479 | 13 |
| Terminal contrast | `terminal_contrast_level` | Preference |  | persistent=false | a/app/fragments/settings/SettingsRowIcons.java:283, a/app/fragments/settings/termux/TermuxStylePreferencesDataStore.java:248 (+3) ; code default EditorCardReset.java:76 | 18 |
| Wallpaper | `use_system_wallpaper` | Preference |  | persistent=false; summary="Shows the current system wallpaper behind launcher surfaces." | a/app/fragments/settings/SettingsRowIcons.java:285, a/app/fragments/settings/termux/TermuxStylePreferencesDataStore.java:88 (+6) ; code default TermuxAppSharedPreferences.java:3146 | 22 |
| Widgets on glass | `widgets_on_glass` | Preference |  | persistent=false; summary="Widgets wear the Look's glass. A widget's own Glass setting overrides it." | a/app/fragments/settings/SettingsRowIcons.java:169, a/app/fragments/settings/termux/SettingsEditorDoors.java:94 (+9) ; code default LookGridMigration.java:185 | 27 |
| Monochrome icons | `app_launcher_bw_icons` | Preference |  | persistent=false; summary="Render app icons in grayscale." | a/app/fragments/settings/SettingsRowIcons.java:286, a/app/fragments/settings/termux/TermuxStylePreferencesDataStore.java:105 (+7) ; code default TermuxAppSharedPreferences.java:1424 | 32 |
| Icon pack | `app_launcher_icon_pack_package` | Preference |  | persistent=false; summary="Use icons from an installed launcher icon pack." | a/app/fragments/settings/termux/TermuxStylePreferencesDataStore.java:308, a/app/fragments/settings/termux/TermuxStylePreferencesDataStore.java:370 (+9) ; code default TermuxAppSharedPreferences.java:1433 | 37 |
| Pinned-app icon pack | `app_launcher_pinned_icon_pack_package` | Preference |  | persistent=false; summary="Optional override for pinned apps. Otherwise they use the global icon pack." | a/app/fragments/settings/termux/TermuxStylePreferencesDataStore.java:316, a/app/fragments/settings/termux/TermuxStylePreferencesDataStore.java:372 (+15) ; code default TermuxAppSharedPreferences.java:1442 | 42 |
| Full screen | `fullscreen` | Preference |  | persistent=false; summary="Hide system bars while using the launcher" | a/app/fragments/settings/SettingsRowIcons.java:242, a/app/fragments/settings/SettingsTree.java:410 (+12) | 48 |
| Icon size | `app_launcher_home_icon_scale` | Preference |  | persistent=false | a/app/fragments/settings/SettingsRowIcons.java:143, a/app/fragments/settings/SettingsTree.java:337 (+6) | 55 |
| Label size | `app_launcher_home_label_size` | Preference |  | persistent=false | a/app/fragments/settings/SettingsRowIcons.java:158, a/app/fragments/settings/SettingsTree.java:337 (+7) | 59 |
| Two-line labels | `app_launcher_home_label_two_lines` | Preference |  | persistent=false | a/app/fragments/settings/SettingsRowIcons.java:159, a/app/fragments/settings/SettingsTree.java:338 (+8) | 63 |
| Label shadow | `app_launcher_home_label_shadow` | Preference |  | persistent=false | a/app/fragments/settings/SettingsRowIcons.java:160, a/app/fragments/settings/SettingsTree.java:338 (+9) | 67 |
| Folder density | `app_launcher_home_folder_density` | Preference |  | persistent=false | a/app/fragments/settings/SettingsRowIcons.java:161, a/app/fragments/settings/SettingsTree.java:339 (+7) | 71 |
| Widget numerals | `app_launcher_builtin_widget_style` | Preference |  | persistent=false | a/app/fragments/settings/SettingsRowIcons.java:170, a/app/fragments/settings/SettingsTree.java:339 (+8) | 75 |
| Page bar | `app_launcher_home_page_indicator` | Preference |  | persistent=false | a/app/fragments/settings/SettingsRowIcons.java:171, a/app/fragments/settings/SettingsTree.java:340 (+6) | 79 |
| Wallpaper scrolls with pages | `wallpaper_parallax` | Preference |  | persistent=false | a/app/fragments/settings/RegistryPreferenceDataStore.java:57, a/app/fragments/settings/SettingsRowIcons.java:173 (+15) ; code default TermuxAppSharedPreferences.java:3154 | 83 |
| 12-hour time | `top_pane_clock_am_pm` | Preference |  | persistent=false | a/app/fragments/settings/SettingsRowIcons.java:170, a/app/fragments/settings/SettingsTree.java:341 (+8) ; code default EditorCardReset.java:55 | 88 |
| CPU usage | `status_widget_cpu` | Preference |  | persistent=false | a/app/fragments/settings/SettingsRowIcons.java:121, a/app/fragments/settings/SettingsTree.java:341 (+6) ; code default EditorCardReset.java:59 | 92 |
| GPU | `status_widget_gpu` | Preference |  | persistent=false | a/app/fragments/settings/SettingsRowIcons.java:122, a/app/fragments/settings/SettingsTree.java:341 (+6) ; code default EditorCardReset.java:60 | 96 |
| Text size button | `status_widget_font_toggle` | Preference |  | persistent=false | a/app/fragments/settings/SettingsRowIcons.java:123, a/app/fragments/settings/SettingsTree.java:341 (+6) ; code default EditorCardReset.java:61 | 100 |
| Status bar order | `status_row_order` | Preference |  | persistent=false | a/app/fragments/settings/SettingsRowIcons.java:124, a/app/fragments/settings/SettingsTree.java:341 (+6) ; code default TermuxAppSharedPreferences.java:1033 | 104 |
| Memory usage | `status_widget_ram` | Preference |  | persistent=false | a/app/fragments/settings/SettingsRowIcons.java:195, a/app/fragments/settings/SettingsTree.java:341 (+6) ; code default EditorCardReset.java:62 | 108 |
| Battery | `status_widget_battery` | Preference |  | persistent=false | a/app/fragments/settings/SettingsRowIcons.java:196, a/app/fragments/settings/SettingsTree.java:341 (+7) ; code default EditorCardReset.java:63 | 112 |
| Media | `status_widget_media` | Preference |  | persistent=false | a/app/fragments/settings/SettingsRowIcons.java:209, a/app/fragments/settings/SettingsTree.java:342 (+7) ; code default EditorCardReset.java:67 | 116 |
| Weather | `status_widget_weather` | Preference |  | persistent=false | a/app/fragments/settings/SettingsRowIcons.java:210, a/app/fragments/settings/SettingsTree.java:342 (+7) ; code default EditorCardReset.java:64 | 120 |
| Fahrenheit | `status_widget_weather_fahrenheit` | Preference |  | persistent=false | a/app/fragments/settings/SettingsRowIcons.java:211, a/app/fragments/settings/SettingsTree.java:342 (+6) ; code default EditorCardReset.java:58 | 124 |
| Cursor trail | `terminal_cursor_trail_style` | Preference |  | persistent=false | a/app/fragments/settings/SettingsRowIcons.java:102, a/app/fragments/settings/SettingsTree.java:343 (+7) ; code default EditorCardReset.java:79 | 129 |
| Terminal effect | `terminal_retro_effect` | Preference |  | persistent=false | a/app/fragments/settings/RegistryPreferenceDataStore.java:59, a/app/fragments/settings/SettingsRowIcons.java:198 (+10) ; code default EditorCardReset.java:80 | 133 |
| Screen events | `terminal_screen_events` | Preference |  | persistent=false | a/app/fragments/settings/RegistryPreferenceDataStore.java:60, a/app/fragments/settings/SettingsSearchIndex.java:122 (+10) ; code default TermuxAppSharedPreferences.java:1336 | 137 |
| Composite signal | `terminal_retro_composite` | Preference |  | persistent=false | a/app/fragments/settings/RegistryPreferenceDataStore.java:61, a/app/fragments/settings/SettingsSearchIndex.java:123 (+10) ; code default TermuxAppSharedPreferences.java:1347 | 141 |
| Split-pane controls | `split_pane_controls` | Preference |  | persistent=false | a/app/fragments/settings/SettingsRowIcons.java:243, a/app/fragments/settings/SettingsTree.java:344 (+3) | 145 |
| Automatic tiling | `pane_dwindle_default` | Preference |  | persistent=false | a/app/fragments/settings/SettingsRowIcons.java:246, a/app/fragments/settings/SettingsTree.java:344 (+6) ; code default TermuxAppSharedPreferences.java:969 | 149 |
| Focus active pane | `pane_focus_grows` | Preference |  | persistent=false | a/app/fragments/settings/SettingsRowIcons.java:247, a/app/fragments/settings/SettingsTree.java:345 (+6) ; code default TermuxAppSharedPreferences.java:978 | 153 |
| Font size | `fonts_size` | Preference |  | persistent=false | a/app/fragments/settings/SettingsRowIcons.java:158, a/app/fragments/settings/SettingsTree.java:345 (+1) | 157 |
| Floating keyboard width | `in_app_keyboard_floating_width` | Preference |  | persistent=false | a/app/fragments/settings/SettingsRowIcons.java:263, a/app/fragments/settings/SettingsTree.java:345 (+3) | 162 |
| Floating keyboard height | `in_app_keyboard_floating_height` | Preference |  | persistent=false | a/app/fragments/settings/SettingsRowIcons.java:235, a/app/fragments/settings/SettingsTree.java:346 (+3) | 166 |
| Split keyboard gap | `in_app_keyboard_split_gap` | Preference |  | persistent=false | a/app/fragments/settings/SettingsRowIcons.java:264, a/app/fragments/settings/SettingsTree.java:346 (+3) | 170 |

#### `app/src/main/res/xml/termux_keyboard_preferences.xml`

| Title (resolved) | Key | Type | XML default | Entries / range / attrs | Read by (first sites) | L |
|---|---|---|---|---|---|---|
| **[Input method]** | | category | | | | |
| On-screen keyboard | `keyboard_input_method` | SegmentedPillPreference | built_in | entries=[Built-in, Android, Off]; entryValues=[built_in, android, none] | a/app/fragments/settings/SettingsRowIcons.java:70, a/app/fragments/settings/termux/KeyboardPreferencesFragment.java:101 (+3) | 7 |
| Voice input | `keyboard_sub_voice` | Preference |  | fragment=KeyboardVoicePreferencesFragment; persistent=false; summary="Speech and dictation cleanup" | a/app/fragments/settings/SettingsRowIcons.java:230 | 13 |
| **[Keyboard & extra keys]** | | category | | | | |
| Layouts | `keyboard_sub_layout` | Preference |  | fragment=KeyboardLayoutPreferencesFragment; persistent=false; summary="Extra keys and documentation" | a/app/fragments/settings/SettingsRowIcons.java:258, a/app/fragments/settings/termux/KeyboardPreferencesFragment.java:248 | 22 |
| Typing and feedback | `keyboard_sub_typing` | Preference |  | fragment=KeyboardTypingPreferencesFragment; persistent=false; summary="Touch accuracy, sound and key preview" | a/app/fragments/settings/SettingsRowIcons.java:106, a/app/fragments/settings/termux/KeyboardPreferencesFragment.java:248 | 28 |
| Extra keys | `edit_extra_keys_row` | Preference |  | fragment=ExtraKeysEditorFragment; persistent=false; summary="Keys and swipe actions" | a/app/fragments/settings/SettingsRowIcons.java:245, a/app/fragments/settings/termux/KeyboardPreferencesFragment.java:278 | 34 |
| Uppercase key labels | `extra_keys_text_all_caps` | SwitchPreferenceCompat | true | summary="Show ESC, TAB and other labels in capitals" | a/app/fragments/settings/SettingsRowIcons.java:261, a/app/fragments/settings/termux/KeyboardPreferencesFragment.java:466 (+1) | 40 |
| **[Look]** | | category | | | | |
| Edit on screen | `customize_keyboard_surface` | Preference |  | persistent=false; summary="Edit how it looks and sits" | a/app/fragments/settings/SettingsTree.java:398, a/app/fragments/settings/termux/KeyboardPreferencesFragment.java:116 | 49 |
| Keyboard theme | `in_app_keyboard_color_scheme_editor` | Preference |  | fragment=KeyboardColorSchemeFragment; summary="Colors and typeface" | a/app/fragments/settings/SettingsTree.java:399 | 55 |
| **[]** | | category | | | | |
| Hardware keyboard | `keyboard_sub_hardware` | Preference |  | fragment=KeyboardHardwarePreferencesFragment; persistent=false; summary="Visibility and language shortcut" | a/app/fragments/settings/SettingsRowIcons.java:262 | 64 |

#### `app/src/main/res/xml/termux_keyboard_layout_preferences.xml`

| Title (resolved) | Key | Type | XML default | Entries / range / attrs | Read by (first sites) | L |
|---|---|---|---|---|---|---|
| **[Layouts and keys]** | | category | | | | |
| Keyboard extra keys | `in_app_keyboard_extra_keys` | MultiSelectListPreference |  | summary="Add keys to the in-app keyboard's own layout." | a/app/fragments/settings/SettingsRowIcons.java:244, a/app/fragments/settings/SettingsTree.java:359 (+22) ; code default InAppKeyboardExtraKeys.java:148 | 7 |
| Layouts | `in_app_keyboard_layouts` | Preference |  | fragment=KeyboardLayoutsFragment; persistent=false; summary="Choose the layouts the keyboard cycles through" | a/app/fragments/settings/SettingsRowIcons.java:70, a/app/fragments/settings/SettingsTree.java:360 (+2) ; code default TermuxAppSharedPreferences.java:2450 | 11 |
| Layout documentation | `keyboard_docs_layouts` | Preference |  | summary="How to write your own layout.xml" | a/app/fragments/settings/SettingsRowIcons.java:57, a/app/fragments/settings/termux/KeyboardPreferencesFragment.java:127 | 17 |
| Supported key values | `keyboard_docs_keys` | Preference |  | summary="Every key name and value you can use in a layout" | a/app/fragments/settings/SettingsRowIcons.java:218, a/app/fragments/settings/termux/KeyboardPreferencesFragment.java:128 | 21 |
| **[Documentation and credits]** | | category | | | | |
| If you like this keyboard, support its amazing developer ❤ | `keyboard_credits_text` | Preference |  | summary="Powered by Unexpected Keyboard, an open-source project by Jules Aguillon." | a/app/fragments/settings/SettingsRowIcons.java:259 | 27 |
| Unexpected Keyboard on GitHub | `keyboard_credits_github` | Preference |  |  | a/app/fragments/settings/SettingsRowIcons.java:63, a/app/fragments/settings/termux/KeyboardPreferencesFragment.java:129 | 29 |
| Unexpected Keyboard on Google Play | `keyboard_credits_play` | Preference |  |  | a/app/fragments/settings/SettingsRowIcons.java:260, a/app/fragments/settings/termux/KeyboardPreferencesFragment.java:130 | 30 |

#### `app/src/main/res/xml/termux_keyboard_typing_preferences.xml`

| Title (resolved) | Key | Type | XML default | Entries / range / attrs | Read by (first sites) | L |
|---|---|---|---|---|---|---|
| **[Typing]** | | category | | | | |
| Adaptive touch accuracy | `in_app_keyboard_tap_correction` | SwitchPreferenceCompat | false | summary="Adjust key detection to your typing." | a/app/fragments/settings/SettingsRowIcons.java:265, a/app/fragments/settings/SettingsTree.java:364 (+3) | 7 |
| Learning | `in_app_keyboard_tap_correction_status` | Preference |  | dependency=in_app_keyboard_tap_correction; persistent=false | a/app/fragments/settings/SettingsRowIcons.java:266, a/app/fragments/settings/termux/KeyboardPreferencesFragment.java:131 (+1) | 12 |
| Reset learned taps | `in_app_keyboard_tap_correction_reset` | Preference |  | persistent=false | a/app/fragments/settings/SettingsRowIcons.java:112, a/app/fragments/settings/termux/KeyboardPreferencesFragment.java:139 (+1) | 17 |
| **[Feedback]** | | category | | | | |
| Key sound | `in_app_keyboard_key_sound_enabled` | SwitchPreferenceCompat |  | summary="Uses the system touch-sound volume." | a/app/fragments/settings/SettingsRowIcons.java:267, a/app/fragments/settings/SettingsTree.java:365 (+3) | 25 |
| Key preview | `in_app_keyboard_key_popup` | SwitchPreferenceCompat | true | summary="Show the pressed key above your finger." | a/app/fragments/settings/SettingsRowIcons.java:268, a/app/fragments/settings/SettingsTree.java:366 (+3) | 29 |

#### `app/src/main/res/xml/termux_keyboard_voice_preferences.xml`

| Title (resolved) | Key | Type | XML default | Entries / range / attrs | Read by (first sites) | L |
|---|---|---|---|---|---|---|
| **[Speech]** | | category | | | | |
| Speech engine | `keyboard_voice_engine` | SegmentedPillPreference | system | entries=[Android system, On-device]; entryValues=[system, on_device] | a/app/fragments/settings/SettingsRowIcons.java:271, a/app/fragments/settings/termux/KeyboardPreferencesFragment.java:569 (+5) ; code default TermuxAppSharedPreferences.java:2186 | 7 |
| Voice language | `keyboard_voice_language` | ListPreference | auto | entries=[Auto-detect, English, Deutsch, Español, Français, Italiano, Português, Nederlands, Polski, Türkçe, Русский, Українська, العربية, فارسی, हिन्दी, 中文, 日本語, 한국어, Bahasa Indonesia, Tiếng Việt, Svenska]; entryValues=[auto, en, de, es, fr, it, pt, nl, pl, tr, ru, uk, ar, fa, hi, zh, ja, ko, id, vi, sv] | a/app/fragments/settings/SettingsRowIcons.java:258, a/app/fragments/settings/termux/KeyboardPreferencesFragment.java:572 (+3) ; code default TermuxAppSharedPreferences.java:2213 | 13 |
| **[Speech model]** | | category | | | | |
|  | `speech_model_pick` | Preference |  | persistent=false | a/app/fragments/settings/SettingsRowIcons.java:201, a/app/fragments/settings/termux/KeyboardPreferencesFragment.java:173 (+2) | 23 |
| Runs on | `speech_model_accelerator` | SegmentedPillPreference | auto | entries=[Auto, CPU, GPU]; entryValues=[auto, cpu, gpu] | a/app/fragments/settings/SettingsRowIcons.java:203, a/ai/TaiSettings.java:40 (+8) | 26 |
| Unload after | `speech_model_idle_unload` | Preference |  | persistent=false | a/app/fragments/settings/SettingsRowIcons.java:202, a/app/fragments/settings/termux/VoiceSpeechModelRows.java:48 (+1) | 32 |
| **[Dictation cleanup]** | | category | | | | |
| Clean up dictation | `keyboard_voice_polish` | SwitchPreferenceCompat | true | summary="Review local corrections before inserting; requires a cleanup model." | a/app/fragments/settings/SettingsRowIcons.java:269, a/app/fragments/settings/termux/KeyboardPreferencesFragment.java:463 (+3) ; code default TermuxAppSharedPreferences.java:2240 | 39 |
| Cleanup model | `keyboard_voice_polish_model` | Preference |  | dependency=keyboard_voice_polish; persistent=false | a/app/fragments/settings/SettingsRowIcons.java:222, a/ai/TaiRemoteSettings.java:19 (+13) ; code default TermuxAppSharedPreferences.java:2252 | 44 |
| Runs on | `keyboard_voice_polish_accelerator` | SegmentedPillPreference | cpu | entries=[CPU, GPU]; entryValues=[cpu, gpu]; dependency=keyboard_voice_polish; persistent=false | a/app/fragments/settings/SettingsRowIcons.java:203, a/ai/TaiSettings.java:40 (+8) | 49 |
| Level | `keyboard_voice_polish_level` | SegmentedPillPreference | polished | entries=[Light, Polished]; entryValues=[light, polished]; dependency=keyboard_voice_polish | a/app/fragments/settings/SettingsRowIcons.java:147, a/app/fragments/settings/termux/KeyboardPreferencesFragment.java:594 (+3) ; code default TermuxAppSharedPreferences.java:2266 | 57 |
| **[Listening]** | | category | | | | |
| Listening sounds | `keyboard_voice_sounds` | SwitchPreferenceCompat | true | summary="Play a sound when listening starts or stops." | a/app/fragments/settings/SettingsRowIcons.java:267, a/app/fragments/settings/termux/KeyboardPreferencesFragment.java:460 (+3) ; code default TermuxAppSharedPreferences.java:2229 | 67 |
| Fine-tuning | `keyboard_voice_details` | Preference |  | fragment=KeyboardVoiceDetailsPreferencesFragment; persistent=false | a/app/fragments/settings/SettingsRowIcons.java:272 | 72 |

#### `app/src/main/res/xml/termux_keyboard_voice_details_preferences.xml`

| Title (resolved) | Key | Type | XML default | Entries / range / attrs | Read by (first sites) | L |
|---|---|---|---|---|---|---|
| **[Listening]** | | category | | | | |
| Phrase pause | `keyboard_voice_pause_ms` | ListPreference | 600 | entries=[400 ms, 600 ms, 800 ms, 1.2 s]; entryValues=[400, 600, 800, 1200] | a/app/fragments/settings/SettingsRowIcons.java:273, a/app/fragments/settings/SettingsTree.java:327 (+4) ; code default KeyboardPreferencesFragment.java:581 | 6 |
| Mic sensitivity | `keyboard_voice_mic_sensitivity` | ListPreference | normal | entries=[Normal: ignores background talk, High: for soft speech; may hear others]; entryValues=[normal, high] | a/app/fragments/settings/SettingsRowIcons.java:204, a/app/fragments/settings/SettingsTree.java:327 (+4) ; code default TermuxAppSharedPreferences.java:2284 | 13 |
| Stop after silence | `keyboard_voice_silence_timeout_ms` | ListPreference | 5000 | entries=[5 s, 10 s, 30 s, Until tap]; entryValues=[5000, 10000, 30000, 0] | a/app/fragments/settings/SettingsRowIcons.java:202, a/app/fragments/settings/SettingsTree.java:328 (+3) | 20 |
| **[Dictation cleanup]** | | category | | | | |
| When it runs | `keyboard_voice_details_cleanup` | Preference |  | persistent=false; summary="When you stop dictating, a local chat model cleans up the whole text in one pass. The panel marks wh" | a/app/fragments/settings/SettingsRowIcons.java:269 | 30 |
| When it is off | `keyboard_voice_details_off` | Preference |  | persistent=false; summary="The text waits exactly as heard." | a/app/fragments/settings/SettingsRowIcons.java:270 | 36 |
| Shell commands | `keyboard_voice_details_shell` | Preference |  | persistent=false; summary="Shell commands are formatted by fixed rules instead of the model (ls dash la becomes ls -la)." | a/app/fragments/settings/SettingsRowIcons.java:149 | 42 |

#### `app/src/main/res/xml/termux_keyboard_hardware_preferences.xml`

| Title (resolved) | Key | Type | XML default | Entries / range / attrs | Read by (first sites) | L |
|---|---|---|---|---|---|---|
| **[Hardware keyboard]** | | category | | | | |
| Hide on-screen keyboard | `in_app_keyboard_hide_on_hardware` | SwitchPreferenceCompat |  | summary="While a hardware keyboard is connected." | a/app/fragments/settings/SettingsRowIcons.java:257, a/app/fragments/settings/SettingsTree.java:357 (+2) | 7 |
| Android language shortcut | `pass_ctrl_space_to_android` | SwitchPreferenceCompat | false | summary="Ctrl+Space switches Android keyboard languages. Off sends it to the terminal." | a/app/fragments/settings/SettingsRowIcons.java:258, a/app/fragments/settings/SettingsTree.java:357 (+4) | 11 |

#### `app/src/main/res/xml/notifications_preferences.xml`

| Title (resolved) | Key | Type | XML default | Entries / range / attrs | Read by (first sites) | L |
|---|---|---|---|---|---|---|
| **[On the home screen]** | | category | | | | |
| Notification dots | `app_launcher_notification_dots` | SwitchPreferenceCompat | false | summary="Show a dot on apps with active notifications" | a/app/fragments/settings/RegistryPreferenceDataStore.java:65, a/app/fragments/settings/SettingsRowIcons.java:190 (+9) ; code default TermuxAppSharedPreferences.java:1829 | 7 |
| Badge style | `app_launcher_notification_badge_style` | SegmentedPillPreference | dot | entries=[Dot, Count]; entryValues=[dot, count]; summary="A dot, or how many notifications an app has" | a/app/fragments/settings/RegistryPreferenceDataStore.java:66, a/app/fragments/settings/SettingsRowIcons.java:191 (+38) | 12 |
| **[Pinned to the status bar]** | | category | | | | |
| Pinned notifications | `essential_notification_rules_manage` | Preference |  | fragment=EssentialNotificationRulesFragment; persistent=false; summary="Pin matching notifications to the top pane. Match an app package, keywords, or both" | a/app/fragments/settings/SettingsRowIcons.java:181 | 22 |
| **[For the shell]** | | category | | | | |
| Notification history | `notification_history_shell` | Preference |  | fragment=NotificationHistoryFragment; persistent=false; summary="Choose which apps are recorded for Termux" | a/app/fragments/settings/SettingsRowIcons.java:113, a/ai/TaiRuntimeHistory.java:51 (+4) | 31 |

#### `app/src/main/res/xml/termux_ai_preferences.xml`

| Title (resolved) | Key | Type | XML default | Entries / range / attrs | Read by (first sites) | L |
|---|---|---|---|---|---|---|
| **[Runtime]** | | category | | | | |
| Runtime status | `tai_runtime_status` | StatusCardPreference |  | persistent=false; summary="Nothing is loaded. Models load when a feature needs them." | a/app/fragments/settings/termux/TaiPreferencesFragment.java:169, a/app/fragments/settings/termux/LauncherSoundRows.java:25 (+5) | 8 |
| Runtime controls | `tai_runtime_controls` | Preference |  | persistent=false; summary="Stop, unload and inspect logs" | a/app/fragments/settings/SettingsRowIcons.java:229, a/app/fragments/settings/termux/TaiPreferencesFragment.java:58 | 15 |
| **[Models & voice]** | | category | | | | |
| Model centre | `tai_model_centre` | TaiModelCentreRowPreference |  | persistent=false | a/app/fragments/settings/termux/TaiPreferencesFragment.java:179, a/app/fragments/settings/termux/TaiPreferencesFragment.java:233 | 27 |
| Voice input | `tai_voice_input` | Preference |  | persistent=false; summary="Speech and dictation cleanup" | a/app/fragments/settings/SettingsRowIcons.java:230, a/app/fragments/settings/termux/TaiPreferencesFragment.java:59 | 31 |
| Read aloud | `tai_read_aloud` | Preference |  | persistent=false; summary="The voice and speed for spoken answers" | a/app/fragments/settings/SettingsRowIcons.java:206, a/app/fragments/settings/termux/TaiPreferencesFragment.java:60 | 36 |
| Benchmark | `tai_benchmark` | Preference |  | persistent=false; summary="Which models this phone runs well" | a/app/fragments/settings/SettingsRowIcons.java:101, a/app/fragments/settings/termux/TaiPreferencesFragment.java:188 (+2) | 41 |
| Remote model | `tai_remote_provider` | Preference |  | persistent=false; summary="Use a model from Google, OpenRouter, Groq, Mistral or your own server" | a/app/fragments/settings/SettingsRowIcons.java:220, a/app/fragments/settings/termux/TaiPreferencesFragment.java:62 (+1) | 48 |
| Hugging Face token | `tai_huggingface_token` | Preference |  | persistent=false; summary="Optional. Stored in app-private preferences and used only for Hugging Face model downloads that need" | a/app/fragments/settings/SettingsLayoutUtils.java:167, a/app/fragments/settings/SettingsRowIcons.java:217 (+5) | 53 |
| **[Developer access]** | | category | | | | |
| Local API | `tai_local_api` | Preference |  | persistent=false; summary="Endpoint, token and network access" | a/app/fragments/settings/SettingsRowIcons.java:231, a/app/fragments/settings/termux/TaiPreferencesFragment.java:63 | 63 |
| Advanced | `tai_advanced` | Preference |  | persistent=false; summary="Model parameters, overrides and memory" | a/app/fragments/settings/SettingsRowIcons.java:138, a/app/fragments/settings/termux/TaiPreferencesFragment.java:64 | 68 |
| **[Chats]** | | category | | | | |
| Delete all chats | `tai_delete_all_chats` | Preference |  | persistent=false; summary="No chats" | a/app/fragments/settings/termux/TaiPreferencesFragment.java:65, a/app/fragments/settings/termux/TaiPreferencesFragment.java:83 | 78 |

#### `app/src/main/res/xml/tai_runtime_preferences.xml`

| Title (resolved) | Key | Type | XML default | Entries / range / attrs | Read by (first sites) | L |
|---|---|---|---|---|---|---|
| Runtime status | `tai_runtime_status` | StatusCardPreference |  | persistent=false; summary="Nothing is loaded. Models load when a feature needs them." | a/app/fragments/settings/termux/TaiPreferencesFragment.java:169, a/app/fragments/settings/termux/LauncherSoundRows.java:25 (+5) | 5 |
| **[Controls]** | | category | | | | |
| Stop current run | `tai_runtime_stop` | Preference |  | persistent=false; summary="No run is active" | a/app/fragments/settings/SettingsLayoutUtils.java:168, a/app/fragments/settings/SettingsRowIcons.java:227 (+3) ; code default GlassResetNudgeCard.java:118 | 15 |
| Unload model | `tai_runtime_unload` | Preference |  | persistent=false; summary="No model is loaded" | a/app/fragments/settings/SettingsLayoutUtils.java:168, a/app/fragments/settings/SettingsRowIcons.java:228 (+3) | 21 |
| Runtime logs | `tai_runtime_logs` | Preference |  | persistent=false; summary="Inspect the runtime's diagnostic output" | a/app/fragments/settings/SettingsLayoutUtils.java:168, a/app/fragments/settings/SettingsRowIcons.java:68 (+2) | 27 |

#### `app/src/main/res/xml/tai_api_preferences.xml`

| Title (resolved) | Key | Type | XML default | Entries / range / attrs | Read by (first sites) | L |
|---|---|---|---|---|---|---|
| **[Endpoint]** | | category | | | | |
| API base URL | `tai_endpoint_copy` | Preference |  | persistent=false; summary="OpenAI and Ollama clients use ~/.launcherctl/endpoint and ~/.launcherctl/token. Supported surfaces i" | a/app/fragments/settings/SettingsLayoutUtils.java:167, a/app/fragments/settings/SettingsRowIcons.java:150 (+3) | 9 |
| Load models automatically | `tai_openai_auto_load` | SwitchPreferenceCompat | true | summaryOn=@string/termux_ai_openai_auto_load_summary_on; summaryOff=@string/termux_ai_openai_auto_load_summary_off | a/app/fragments/settings/SettingsRowIcons.java:216, a/app/fragments/settings/SettingsTree.java:463 (+2) | 14 |
| **[Access]** | | category | | | | |
| Require API token | `tai_api_auth_required` | SwitchPreferenceCompat | true | summaryOn=@string/termux_ai_auth_required_summary_on; summaryOff=@string/termux_ai_auth_required_summary_off | a/app/fragments/settings/SettingsRowIcons.java:217, a/app/fragments/settings/SettingsTree.java:464 (+4) | 25 |
| LAN access | `tai_lan_enabled` | SwitchPreferenceCompat | false | summaryOn=@string/termux_ai_lan_enabled_summary_on; summaryOff=@string/termux_ai_lan_enabled_summary_off | a/app/fragments/settings/SettingsRowIcons.java:219, a/app/fragments/settings/SettingsTree.java:464 (+2) | 31 |
|  | `tai_lan_note` | Preference |  | persistent=false; summary="LAN access needs the API token. Requests travel as plain HTTP, and access turns itself off after 12 " | a/app/fragments/settings/SettingsRowIcons.java:51 | 37 |

#### `app/src/main/res/xml/tai_advanced_preferences.xml`

| Title (resolved) | Key | Type | XML default | Entries / range / attrs | Read by (first sites) | L |
|---|---|---|---|---|---|---|
| **[Model tuning]** | | category | | | | |
| Parameters | `tai_parameters_defaults` | Preference |  | persistent=false; summary="Edit global runtime defaults used unless a model override is set" | a/app/fragments/settings/SettingsRowIcons.java:147, a/app/fragments/settings/SettingsTree.java:466 (+1) | 8 |
|  | `tai_runtime_overrides` | TaiOverridesPreference |  | persistent=false | a/app/fragments/settings/SettingsTree.java:466, a/app/fragments/settings/termux/TaiAdvancedPreferencesFragment.java:88 (+1) | 13 |
| Memory limits | `tai_memory_limits` | SegmentedPillPreference |  | persistent=false; summary="Keeps enough memory free for the phone to stay responsive while a model loads." | a/app/fragments/settings/SettingsRowIcons.java:195, a/app/fragments/settings/SettingsTree.java:467 (+1) | 17 |
| **[Support]** | | category | | | | |
| Share diagnostics log | `tai_share_diagnostics` | Preference |  | persistent=false; summary="Loads, evictions and failures, the runtime history and the last crash, as one text file. No prompts " | a/app/fragments/settings/SettingsLayoutUtils.java:167, a/app/fragments/settings/SettingsRowIcons.java:215 (+2) | 27 |

#### `app/src/main/res/xml/tai_remote_preferences.xml`

| Title (resolved) | Key | Type | XML default | Entries / range / attrs | Read by (first sites) | L |
|---|---|---|---|---|---|---|
| **[Server]** | | category | | | | |
| Provider | `tai_remote_provider_row` | Preference |  | persistent=false | a/app/fragments/settings/SettingsRowIcons.java:220, a/app/fragments/settings/termux/TaiRemotePreferencesFragment.java:82 (+1) | 10 |
| Server address | `tai_remote_base_url` | Preference |  | persistent=false | a/app/fragments/settings/SettingsRowIcons.java:150, a/ai/TaiRemoteSettings.java:54 (+11) | 15 |
| Get a key | `tai_remote_get_key` | Preference |  | persistent=false | a/app/fragments/settings/SettingsRowIcons.java:221, a/app/fragments/settings/termux/TaiRemotePreferencesFragment.java:83 (+1) | 19 |
| API key | `tai_remote_api_key` | Preference |  | persistent=false | a/app/fragments/settings/SettingsRowIcons.java:217, a/app/fragments/settings/termux/TaiRemotePreferencesFragment.java:85 (+1) | 23 |
|  | `tai_remote_free_plan` | Preference |  | persistent=false; summary="The free plan may use what you send to improve its models." | a/app/fragments/settings/SettingsRowIcons.java:51, a/app/fragments/settings/termux/TaiRemotePreferencesFragment.java:162 | 27 |
|  | `tai_remote_consent` | Preference |  | persistent=false; summary="Text and, for the wallpaper creator, two small JPEGs of your photo are sent to this address." | a/app/fragments/settings/SettingsRowIcons.java:52 | 32 |
| **[Model]** | | category | | | | |
| Model | `tai_remote_model` | Preference |  | persistent=false | a/app/fragments/settings/SettingsRowIcons.java:222, a/ai/TaiRemoteSettings.java:103 (+19) | 42 |
| Understands images | `tai_remote_images` | SwitchPreferenceCompat |  | persistent=false | a/app/fragments/settings/SettingsRowIcons.java:223, a/app/fragments/settings/termux/TaiRemotePreferencesFragment.java:90 (+1) | 46 |
| When to use it | `tai_remote_routing_row` | Preference |  | persistent=false | a/app/fragments/settings/SettingsRowIcons.java:224, a/ai/TaiRemoteSettings.java:24 (+6) | 50 |
| **[Manage]** | | category | | | | |
| Test connection | `tai_remote_test` | Preference |  | persistent=false; summary="Send one short message and time the answer" | a/app/fragments/settings/SettingsRowIcons.java:225, a/app/fragments/settings/termux/TaiRemotePreferencesFragment.java:88 (+1) | 59 |
| Remove | `tai_remote_remove` | Preference |  | persistent=false; summary="Forget the address, key and model" | a/app/fragments/settings/SettingsRowIcons.java:226, a/app/fragments/settings/termux/TaiRemotePreferencesFragment.java:89 (+3) | 64 |

#### `app/src/main/res/xml/read_aloud_preferences.xml`

| Title (resolved) | Key | Type | XML default | Entries / range / attrs | Read by (first sites) | L |
|---|---|---|---|---|---|---|
| No voice yet | `speech_output_missing` | Preference |  | persistent=false; summary="Get the voice model in Model centre to hear text read aloud" | a/app/fragments/settings/SettingsRowIcons.java:54, a/app/fragments/settings/termux/ReadAloudPreferencesFragment.java:64 (+1) | 6 |
| Voice | `speech_output_voice` | Preference |  | persistent=false | a/app/fragments/settings/SettingsRowIcons.java:204, a/app/fragments/settings/termux/ReadAloudPreferencesFragment.java:69 (+1) | 11 |
| Speed | `speech_output_speed` | Preference |  | persistent=false | a/app/fragments/settings/SettingsRowIcons.java:101, a/app/fragments/settings/termux/ReadAloudPreferencesFragment.java:74 (+1) | 15 |
| Play sample | `speech_output_sample` | Preference |  | persistent=false; summary="Hear the chosen voice and speed" | a/app/fragments/settings/SettingsRowIcons.java:205, a/app/fragments/settings/termux/ReadAloudPreferencesFragment.java:79 (+1) | 19 |

#### `app/src/main/res/xml/performance_preferences.xml`

| Title (resolved) | Key | Type | XML default | Entries / range / attrs | Read by (first sites) | L |
|---|---|---|---|---|---|---|
| **[Presets]** | | category | | | | |
| Reduce idle activity | `lazy_mode` | SwitchPreferenceCompat | false | summary="Saves battery while you're away. Idle animations pause, status updates slow down and glass stops ben" | a/app/fragments/settings/RegistryPreferenceDataStore.java:52, a/app/fragments/settings/SettingsRowIcons.java:194 (+18) ; code default TermuxAppSharedPreferences.java:853 | 13 |
|  | `None` | extra |  |  |  | 0 |
| Low-memory preset | `low_memory_preset` | SwitchPreferenceCompat | false | summary="Smoothest and lightest. Switches to the Solid look and an opaque terminal, and turns off the trail, " | a/app/fragments/settings/RegistryPreferenceDataStore.java:53, a/app/fragments/settings/SettingsRowIcons.java:195 (+16) ; code default TermuxAppSharedPreferences.java:866 | 22 |
| **[Smoother motion]** | | category | | | | |
| Glass refraction | `fancier_glass` | SwitchPreferenceCompat | true | summary="Turn off for smoother swipes. Glass edges stop bending the wallpaper." | a/app/fragments/settings/RegistryPreferenceDataStore.java:54, a/app/fragments/settings/SettingsRowIcons.java:197 (+8) ; code default TermuxAppSharedPreferences.java:891 | 33 |
| Live blur | `live_blur` | SwitchPreferenceCompat | false | summary="Glass blurs whatever moves behind it. Uses more battery. Off uses the least memory." | a/app/fragments/settings/RegistryPreferenceDataStore.java:55, s/termux/settings/registry/SettingDeclarations.java:153 (+10) ; code default TermuxAppSharedPreferences.java:909 | 39 |
| Live windows while swiping | `live_windows_while_swiping` | SwitchPreferenceCompat | true | summary="Show the next window's live output as you swipe between windows. Turn off to save battery." | a/app/fragments/settings/RegistryPreferenceDataStore.java:56, s/termux/settings/registry/SettingDeclarations.java:126 (+2) ; code default TermuxAppSharedPreferences.java:898 | 45 |
| Wallpaper scrolls with pages | `wallpaper_parallax` | SwitchPreferenceCompat | true | summary="Turn off for smoother page swipes. The wallpaper stops sliding with the pages." | a/app/fragments/settings/RegistryPreferenceDataStore.java:57, a/app/fragments/settings/SettingsRowIcons.java:173 (+15) ; code default TermuxAppSharedPreferences.java:3154 | 51 |
| Cursor trail | `terminal_cursor_trail` | SwitchPreferenceCompat | true | summary="Turn off for less drawing while you type. The cursor moves without a trail." | a/app/fragments/settings/RegistryPreferenceDataStore.java:58, a/app/fragments/settings/SettingsRowIcons.java:102 (+5) ; code default EditorCardReset.java:78 | 57 |
| Terminal effect | `terminal_retro_effect` | ListPreference | none | entries=[None, CRT, CRT (green), CRT (amber), CRT (Material colours), TFT grid]; entryValues=[none, crt, crt_green, crt_amber, crt_material, tft]; summary="None draws least. Other choices add a screen effect over the terminal." | a/app/fragments/settings/RegistryPreferenceDataStore.java:59, a/app/fragments/settings/SettingsRowIcons.java:198 (+10) ; code default EditorCardReset.java:80 | 63 |
| Screen events | `terminal_screen_events` | SwitchPreferenceCompat | true | summary="Bell tear, power on and off, focus wobble, death static on CRT panes" | a/app/fragments/settings/RegistryPreferenceDataStore.java:60, a/app/fragments/settings/SettingsSearchIndex.java:122 (+10) ; code default TermuxAppSharedPreferences.java:1336 | 71 |
| Composite signal | `terminal_retro_composite` | SwitchPreferenceCompat | false | summary="Blend neighbouring pixels sideways like a video cable, so dithering and pixel art read as shades. Te" | a/app/fragments/settings/RegistryPreferenceDataStore.java:61, a/app/fragments/settings/SettingsSearchIndex.java:123 (+10) ; code default TermuxAppSharedPreferences.java:1347 | 77 |
| Idle animations | `idle_animations` | SwitchPreferenceCompat | false | summary="Ambient glitch and flicker on CRT panes while nothing happens. Uses battery." | a/app/fragments/settings/RegistryPreferenceDataStore.java:62, s/termux/settings/registry/SettingDeclarations.java:89 (+10) ; code default TermuxAppSharedPreferences.java:1358 | 83 |
| Idle glitch rate | `idle_tear_rate` | ListPreference | rare | entries=[Rare (every 2–5 minutes), Sometimes (every 30–90 seconds), Often (every 10–30 seconds)]; entryValues=[rare, sometimes, often]; dependency=idle_animations | a/app/fragments/settings/RegistryPreferenceDataStore.java:63, s/termux/settings/registry/SettingDeclarations.java:98 (+10) ; code default TermuxAppSharedPreferences.java:1369 | 89 |

#### `app/src/main/res/xml/general_preferences.xml`

| Title (resolved) | Key | Type | XML default | Entries / range / attrs | Read by (first sites) | L |
|---|---|---|---|---|---|---|
| **[Launcher]** | | category | | | | |
| Use as | `use_as` | ListPreference | home | entries=[Home screen, Terminal only]; entryValues=[home, terminal] | a/app/fragments/settings/SettingsRowIcons.java:177, a/app/fragments/settings/termux/TermuxStylePreferencesDataStore.java:264 (+15) ; code default TermuxAppSharedPreferences.java:1765 | 15 |
| Run setup again | `app_launcher_setup_rerun` | Preference |  | summary="Choose again how you use the launcher." | a/app/fragments/settings/SettingsRowIcons.java:178, a/app/fragments/settings/termux/GeneralPreferencesFragment.java:49 | 23 |
| Setup | `app_launcher_setup_cards` | Preference |  |  | a/app/fragments/settings/SettingsRowIcons.java:178, a/app/fragments/settings/termux/GeneralPreferencesFragment.java:57 | 27 |
| Start in | `app_launcher_start_in` | ListPreference |  | entries=[Home, Terminal, Display]; entryValues=[home, workspace, display] | a/app/fragments/settings/SettingsRowIcons.java:176, a/app/fragments/settings/SettingsTree.java:394 (+11) ; code default SetupState.java:514 | 31 |
| **[Motion]** | | category | | | | |
| Animation speed | `animation_speed` | ListPreference | normal | entries=[Slower, Normal, Faster, Off]; entryValues=[slower, normal, faster, off]; persistent=true | a/app/fragments/settings/SettingsRowIcons.java:101, s/termux/settings/registry/SettingDeclarations.java:66 (+37) | 41 |
| Page transition | `app_launcher_home_page_transition` | Preference |  | persistent=false | a/app/fragments/settings/SettingsRowIcons.java:103, a/app/fragments/settings/SettingsTree.java:393 (+7) | 53 |
| **[Vibration]** | | category | | | | |
| Allow vibration | `app_haptics_enabled` | SwitchPreferenceCompat | true | summary="Master control. Turn off to silence launcher and keyboard vibration" | a/app/fragments/settings/SettingsRowIcons.java:104, a/app/fragments/settings/SettingsTree.java:386 (+5) ; code default Haptics.java:37 | 60 |
| Launcher vibration | `app_launcher_row_haptics` | SwitchPreferenceCompat | true | summary="Dock and app-index feedback" | a/app/fragments/settings/SettingsRowIcons.java:106, a/app/fragments/settings/SettingsTree.java:386 (+4) ; code default TermuxAppSharedPreferences.java:1930 | 65 |
| Keyboard vibration | `in_app_keyboard_haptics_enabled` | SwitchPreferenceCompat |  | summary="Key press feedback" | a/app/fragments/settings/SettingsRowIcons.java:70, a/app/fragments/settings/SettingsTree.java:368 (+5) | 70 |
| **[Audio]** | | category | | | | |
| Sound | `launcher_sound` | SwitchPreferenceCompat | true | summary="Play sound from the terminal and Linux apps" | a/app/fragments/settings/SettingsRowIcons.java:126, a/app/fragments/settings/termux/LauncherSoundRows.java:22 (+2) ; code default TermuxAppSharedPreferences.java:1666 | 80 |
| Media controls | `launcher_media_controls` | SwitchPreferenceCompat | true | dependency=launcher_sound; summary="Show what's playing and control it from notifications" | a/app/fragments/settings/SettingsRowIcons.java:127, a/app/fragments/settings/termux/LauncherSoundRows.java:24 (+2) ; code default TermuxAppSharedPreferences.java:1676 | 85 |
|  | `launcher_sound_status` | Preference |  | isPreferenceVisible=false; persistent=false | a/app/fragments/settings/SettingsRowIcons.java:126, a/app/fragments/settings/termux/LauncherSoundRows.java:58 (+5) | 91 |
| **[Backup, access and about]** | | category | | | | |
| Backup & restore | `backup_restore` | Preference |  | fragment=BackupRestorePreferencesFragment; persistent=false; summary="Move your setup to another phone" | a/app/fragments/settings/SettingsRowIcons.java:108 | 99 |
| Permissions & services | `services_permissions` | Preference |  | fragment=ServicesPermissionsPreferencesFragment; persistent=false; summary="Access and connected services" | a/app/fragments/settings/SettingsRowIcons.java:109 | 105 |
| Diagnostics | `advanced_diagnostics` | Preference |  | fragment=AdvancedDiagnosticsPreferencesFragment; persistent=false; summary="Logs and troubleshooting" | a/app/fragments/settings/SettingsRowIcons.java:110 | 111 |
| About & help | `about_support` | Preference |  | fragment=com.termux.app.fragments.settings.AboutSupportPreferencesFragment; persistent=false; summary="Guides and app information" | a/app/fragments/settings/SettingsRowIcons.java:54 | 117 |
| **[Home app]** | | category | | | | |
| Set as default launcher | `app_launcher_set_home` | Preference |  | persistent=false; summary="Opens Android’s default home app settings" | a/app/fragments/settings/SettingsRowIcons.java:111, a/app/fragments/settings/SettingsTree.java:387 (+1) | 126 |
| Restart launcher | `app_launcher_restart` | Preference |  | persistent=false; summary="Reload the launcher's screens. Terminal sessions keep running" | a/app/fragments/settings/SettingsRowIcons.java:112, a/app/fragments/settings/termux/GeneralPreferencesFragment.java:85 | 131 |
| Show in Recents when not the default launcher | `show_in_recents_when_not_default` | SwitchPreferenceCompat | true | summary="Keeps the launcher available from the Recents screen when another home app is active" | a/app/fragments/settings/SettingsRowIcons.java:113, a/app/fragments/settings/SettingsTree.java:387 (+7) | 136 |

#### `app/src/main/res/xml/backup_restore_preferences.xml`

| Title (resolved) | Key | Type | XML default | Entries / range / attrs | Read by (first sites) | L |
|---|---|---|---|---|---|---|
| Export settings | `settings_backup_export` | Preference |  | persistent=false; summary="Save your launcher setup to a file" | a/app/fragments/settings/SettingsLayoutUtils.java:169, a/app/fragments/settings/SettingsRowIcons.java:95 (+1) | 3 |
| Import settings | `settings_backup_import` | Preference |  | persistent=false; summary="Restore your setup from a saved file" | a/app/fragments/settings/SettingsLayoutUtils.java:169, a/app/fragments/settings/SettingsRowIcons.java:96 (+1) | 5 |
| Export AI models | `settings_backup_export_models` | Preference |  | persistent=false; summary="Save downloaded models so you don't download them again" | a/app/fragments/settings/SettingsLayoutUtils.java:170, a/app/fragments/settings/SettingsRowIcons.java:97 (+2) | 7 |
| Import AI models | `settings_backup_import_models` | Preference |  | persistent=false; summary="Add models from a saved folder" | a/app/fragments/settings/SettingsLayoutUtils.java:170, a/app/fragments/settings/SettingsRowIcons.java:98 (+1) | 9 |

#### `app/src/main/res/xml/services_permissions_preferences.xml`

| Title (resolved) | Key | Type | XML default | Entries / range / attrs | Read by (first sites) | L |
|---|---|---|---|---|---|---|
| **[Permissions]** | | category | | | | |
| Files and media | `app_launcher_storage_access` | StatusActionPreference |  |  | a/app/fragments/settings/termux/LauncherPreferencesFragment.java:165, a/app/fragments/settings/termux/LauncherPreferencesFragment.java:200 (+2) | 4 |
| Wallpaper access | `app_launcher_wallpaper_access` | StatusActionPreference |  |  | a/app/TermuxActivity.java:30018, a/app/chrome/KeyboardFrost.java:112 (+50) | 5 |
| Notification access | `app_launcher_notification_access` | StatusActionPreference |  |  | a/app/fragments/settings/SettingsTree.java:432, a/app/fragments/settings/termux/LauncherPreferencesFragment.java:171 (+11) | 6 |
| Accessibility service | `app_launcher_accessibility_lock_access` | StatusActionPreference |  |  | a/app/fragments/settings/termux/LauncherPreferencesFragment.java:175, a/app/fragments/settings/termux/LauncherPreferencesFragment.java:204 (+10) | 7 |
| App notifications | `app_launcher_notification_settings` | StatusActionPreference |  |  | a/app/fragments/settings/termux/LauncherPreferencesFragment.java:181, a/app/fragments/settings/termux/LauncherPreferencesFragment.java:205 (+32) | 8 |
| All app permissions | `app_launcher_app_permissions` | StatusActionPreference |  | summary="Opens Android app permissions." | a/app/fragments/settings/termux/LauncherPreferencesFragment.java:185, a/ai/CompiledModelGraph.java:328 (+33) | 9 |
| **[Connected services]** | | category | | | | |
| Shizuku | `service_shizuku` | StatusActionPreference |  | fragment=PrivilegedAccessPreferencesFragment; summary="Connect or manage privileged access." | a/app/fragments/settings/termux/ServicesPermissionsPreferencesFragment.java:71 | 15 |
| Termux:API | `service_termux_api` | StatusActionPreference |  | fragment=com.termux.app.fragments.settings.TermuxAPIPreferencesFragment; summary="Installed. Configure the add-on." | a/app/fragments/settings/termux/ServicesPermissionsPreferencesFragment.java:85 | 20 |

#### `app/src/main/res/xml/termux_privileged_access_preferences.xml`

| Title (resolved) | Key | Type | XML default | Entries / range / attrs | Read by (first sites) | L |
|---|---|---|---|---|---|---|
| **[Backend status]** | | category | | | | |
| Backend status | `privileged_backend_status` | StatusCardPreference |  | persistent=false; summary="What is running now" | a/app/fragments/settings/termux/LauncherSoundRows.java:25, a/app/fragments/settings/termux/LauncherSoundRows.java:58 (+4) | 8 |
| **[Backend]** | | category | | | | |
| Use | `priv_backend` | ListPreference | auto | entries=[Automatic, Shizuku, Root, Off]; entryValues=[auto, shizuku, root, off] | a/app/fragments/settings/SettingsRowIcons.java:276, a/app/fragments/settings/termux/PrivilegedAccessPreferencesFragment.java:31 (+8) | 21 |
| Request Shizuku permission | `priv_request_shizuku_permission` | Preference |  | summary="Request or re-check Shizuku permission now." | a/app/fragments/settings/SettingsRowIcons.java:277, a/app/fragments/settings/termux/PrivilegedAccessPreferencesFragment.java:70 (+1) | 30 |
| Let launcherctl restart the phone | `launcherctl_root_reboot` | SwitchPreferenceCompat | false | summary="Allows restart, recovery and bootloader requests from scripts." | a/app/fragments/settings/termux/PrivilegedAccessPreferencesFragment.java:239, a/app/fragments/settings/termux/PrivilegedAccessPreferencesFragment.java:257 (+2) ; code default TermuxAppSharedPreferences.java:4606 | 35 |
| **[Privileged lane]** | | category | | | | |
| Lane status | `priv_lane_status` | StatusCardPreference |  | persistent=false; summary="Catalog tools marked priv=shizuku start as the shell user inside a terminal session." | a/app/fragments/settings/termux/PrivilegedAccessPreferencesFragment.java:132 | 47 |
| Allow catalog tools to run as shell via Shizuku or root | `priv_lane_enabled` | SwitchPreferenceCompat | true | summaryOn=@string/termux_privileged_lane_enabled_on; summaryOff=@string/termux_privileged_lane_enabled_off | a/app/fragments/settings/SettingsRowIcons.java:278, a/app/fragments/settings/termux/PrivilegedAccessPreferencesFragment.java:33 (+5) | 54 |

#### `app/src/main/res/xml/termux_api_preferences.xml`

| Title (resolved) | Key | Type | XML default | Entries / range / attrs | Read by (first sites) | L |
|---|---|---|---|---|---|---|
| Diagnostics | `termux_api_logs` | Preference |  | persistent=false; summary="Termux:API log level, with the app's logs" | a/app/fragments/settings/TermuxAPIPreferencesFragment.java:22 | 6 |

#### `app/src/main/res/xml/advanced_diagnostics_preferences.xml`

| Title (resolved) | Key | Type | XML default | Entries / range / attrs | Read by (first sites) | L |
|---|---|---|---|---|---|---|
| **[Logging]** | | category | | | | |
| Log level | `log_level` | ListPreference |  |  | a/app/fragments/settings/SettingsRowIcons.java:68, a/app/fragments/settings/termux/AdvancedDiagnosticsPreferencesFragment.java:133 (+30) ; code default AdvancedDiagnosticsPreferencesFragment.java:149 | 4 |
| Termux:API log level | `termux_api_log_level` | ListPreference |  |  | a/app/fragments/settings/TermuxAPIPreferencesFragment.java:27, a/app/fragments/settings/termux/AdvancedDiagnosticsPreferencesFragment.java:136 (+2) | 6 |
| Log keyboard input | `terminal_view_key_logging_enabled` | SwitchPreferenceCompat |  | summary="Typed input may include passwords and other sensitive text. Leave off unless you are debugging, and " | a/app/fragments/settings/SettingsRowIcons.java:69, a/app/fragments/settings/termux/AdvancedDiagnosticsPreferencesFragment.java:247 (+4) ; code default TermuxAppSharedPreferences.java:3138 | 7 |
| Motion log | `motion_log_enabled` | SwitchPreferenceCompat |  | summary="Records page changes and resizes for bug reports." | a/app/fragments/settings/termux/AdvancedDiagnosticsPreferencesFragment.java:248, a/app/fragments/settings/termux/AdvancedDiagnosticsPreferencesFragment.java:262 (+2) ; code default TermuxAppSharedPreferences.java:4483 | 9 |
| **[Alerts]** | | category | | | | |
| Plugin error alerts | `plugin_error_notifications_enabled` | SwitchPreferenceCompat |  | summary="Shows a notification when a plugin command fails." | a/app/fragments/settings/SettingsRowIcons.java:72, a/app/fragments/settings/termux/AdvancedDiagnosticsPreferencesFragment.java:252 (+10) ; code default TermuxAppSharedPreferences.java:4545 | 13 |
| Crash report alerts | `crash_report_notifications_enabled` | SwitchPreferenceCompat |  | summary="Shows a notification when a crash report is available." | a/app/fragments/settings/SettingsRowIcons.java:73, a/app/fragments/settings/termux/AdvancedDiagnosticsPreferencesFragment.java:253 (+8) ; code default TermuxAppSharedPreferences.java:4556 | 15 |
| **[Checks]** | | category | | | | |
| Test privileged backend | `privileged_backend_smoke_test` | Preference |  | summary="Runs a small identity command through the selected privileged backend." | a/app/fragments/settings/SettingsRowIcons.java:74, a/app/fragments/settings/termux/AdvancedDiagnosticsPreferencesFragment.java:159 | 19 |
| Process limit | `process_limit` | Preference |  | persistent=false | a/app/fragments/settings/SettingsRowIcons.java:75, a/app/fragments/settings/termux/AdvancedDiagnosticsPreferencesFragment.java:90 (+2) | 22 |
| **[Actions]** | | category | | | | |
| Copy diagnostics | `copy_diagnostics` | Preference |  | summary="Copies app, device, and backend details." | a/app/fragments/settings/SettingsRowIcons.java:76, a/app/fragments/settings/termux/AdvancedDiagnosticsPreferencesFragment.java:160 | 26 |
| Export logs | `export_logs` | Preference |  | summary="Creates a report containing diagnostics and current logcat output." | a/app/fragments/settings/SettingsRowIcons.java:77, a/app/fragments/settings/termux/AdvancedDiagnosticsPreferencesFragment.java:166 | 27 |
| Clear logs | `clear_logs` | Preference |  | summary="Removes the current logcat buffer." | a/app/fragments/settings/SettingsRowIcons.java:78, a/app/fragments/settings/termux/AdvancedDiagnosticsPreferencesFragment.java:167 | 28 |

#### `app/src/main/res/xml/about_support_preferences.xml`

| Title (resolved) | Key | Type | XML default | Entries / range / attrs | Read by (first sites) | L |
|---|---|---|---|---|---|---|
| **[]** | | category | | | | |
| Help | `app_launcher_help` | Preference |  | persistent=false; summary="Open help for where you are: Home or the Terminal." | a/app/fragments/settings/AboutSupportPreferencesFragment.java:43, a/app/fragments/settings/SettingsRowIcons.java:65 (+2) | 5 |
| Documentation | `documentation` | Preference |  | summary="Read launcher and terminal guides." | a/app/fragments/settings/AboutSupportPreferencesFragment.java:38, a/app/fragments/settings/SettingsRowIcons.java:57 | 6 |
| Ask on Discord | `discord` | Preference |  | summary="Get help from the community. The TildeBot help bot answers first." | a/app/fragments/settings/AboutSupportPreferencesFragment.java:39, a/app/fragments/settings/SettingsRowIcons.java:58 | 7 |
| Learn the basics | `open_guide` | Preference |  | persistent=false; summary="Short chapters on getting around." | a/app/fragments/settings/AboutSupportPreferencesFragment.java:64, a/app/fragments/settings/SettingsRowIcons.java:59 | 8 |
| Send feedback | `report_issue` | Preference |  | summary="Report a bug or suggest an idea on GitHub" | a/app/fragments/settings/AboutSupportPreferencesFragment.java:40, a/app/fragments/settings/SettingsRowIcons.java:60 | 9 |
| **[App]** | | category | | | | |
| Version and build | `about` | Preference |  | summary="Version, build, project source" | a/app/fragments/settings/AboutSupportPreferencesFragment.java:74, a/app/fragments/settings/SettingsRowIcons.java:54 (+2) | 12 |
| What's new | `whats_new` | Preference |  | summary="The changes in this version." | a/app/fragments/settings/AboutSupportPreferencesFragment.java:90, a/app/fragments/settings/SettingsRowIcons.java:61 | 13 |
| Open-source licenses | `open_source_licenses` | Preference |  | summary="Libraries, upstream projects, and license texts" | a/app/fragments/settings/AboutSupportPreferencesFragment.java:119, a/app/fragments/settings/SettingsRowIcons.java:62 | 14 |
| **[Project]** | | category | | | | |
| Source code | `source_code` | Preference |  | summary="GitHub" | a/app/fragments/settings/AboutSupportPreferencesFragment.java:41, a/app/fragments/settings/SettingsRowIcons.java:63 | 17 |
| Donate | `donate` | Preference |  | isPreferenceVisible=false; summary="Support development" | a/app/fragments/settings/AboutSupportPreferencesFragment.java:138, a/app/fragments/settings/SettingsRowIcons.java:64 | 18 |

Home's own rows (icon size, labels, folders, numerals, page indicator, wallpaper scrolling, widgets on glass, theme, etc.) live in the Appearance editor; Home gestures' stored values: `app_launcher_home_palette_gesture` (swipe up, default `palette`), `app_launcher_home_gesture_swipe_down`, `..._double_tap`, `..._two_finger_up`, `..._two_finger_down` (default `none`), each with an `_app` companion key naming the app for the action `app` (`launcher/home/HomeGestures.java:33-120`). Actions: none, palette, drawer, notifications, quick_settings, lock_screen, terminal, app.

## 2. The Appearance editor

### 2.1 Model

- **One look (ADR 0031, `docs/adr/0031-the-chrome-wears-one-look-a-page-carries-what-differs.md`).** A look value lives on Global or on one surface; there is no per-page or per-kind row. Read order: the surface's own cell if present, else Global, else the shipped value (`LookGrid.resolve`, `termux-shared/.../preferences/LookGrid.java:417-433`). Keys: `look.<property>` (Global) and `look.<property>.<element>` (`LookGrid.java:42-45, 338-353`). Presence is the override; Reset deletes the key (`LookGrid.clear`, `:457`).
- ADR §2 says only the status bar, dock and keyboard may own Opacity and Blur (Frost). The code does not enforce this in the store: `LookGrid.Property.perElement` is true for FROST, GRAIN, OPACITY, TINT, TINT_HUE, DEPTH, CORNERS, RIM, EDGE (`LookGrid.java:61-97`). The restriction is in what the cards offer: surface cards list only Opacity and Frost (cards in 2.3).
- **Surface slots** (`TermuxAppSharedPreferences.java:3653-3665`): DOCK, KEYBOARD, STATUS, CANVAS (the terminal), WIDGETS, SHEETS, each mapped to a `LookGrid.Element`. **Properties** (`:3731-3745`): BLUR (grid property FROST, Global key `surface_base_blur`), OPACITY, GRAIN, TINT, CORNER_RADIUS, SIDE_GAP (grid MARGIN). SIDE_GAP applies only to DOCK, KEYBOARD, STATUS (`hasSurfaceProperty`, `:3776`).
- **Inherited vs detached.** `isSurfaceInheriting` = the element holds no own cell (`:3810-3817`); Corners and Margin have no element column, so they always follow Global (`cellElement` returns null, `:3505`). Named setters (`setAppBarOpacity`, `setAppLauncherDockCornerRadius`, ...) call `writeSurfaceValue` (`:3517`): they write the surface's own cell if it already holds one, else Global, so "make this surface show X" neither detaches a surface nor misses a detached one. `detachSurfaceValue` (editor drag path) forces an own cell; `setSurfaceInheriting(.., true)` clears it; `resetSurfaceToLook` clears every property of a slot (`:3819-3870`).
- **Corner radius -1.** `LookGrid.CORNERS_AUTO = -1` (`LookGrid.java:48`); the property range is -1..40 (`:78-79`). `resolveAutoCornerRadiusDp(slot, floating)` is the one place it becomes dp: 0 when Docked, 20 dp (`DEFAULT_ROUNDED_SURFACE_CORNER_RADIUS_DP`, Constants:585) when Floating, 26 dp (`STATUS_AUTO_CORNER_RADIUS_MAX_DP`, :595) for the status slot (`TermuxAppSharedPreferences.java:4152-4158`). Getters map negatives to the `DEFAULT_*_CORNER_RADIUS` (-1) constants (`:412-418`, `:625-629`), and `DockLayoutPolicy.java:1262-1269` resolves them. The editor's Corner radius slider clamps 0..40 (`AppearanceLooks.java:274-281`), so the editor never writes -1; but Global's shipped corner is 24 (`DEFAULT_SURFACE_GLOBAL_CORNER_RADIUS`, Constants:1856).
- **Draft and Done.** All look edits go into one `PreferenceDraft` layered over the store; Done writes it once, Undo/Discard drop it; dirty = draft vs store (`SurfaceEditorController.java:100-125, 346-351, 2602-2620, 5593-5740`). Back/leave while dirty asks "Keep editing / Discard / Save" (`:5729-5736`). The layout session and the widgets' own glass are drafts the same way. **Exception:** the app-wide choices Settings applies as it writes them (theme, wallpaper, icon packs, widgets on glass, full screen, Home's rows, the dock's switches, A-Z switches) are written straight to the store and restored from an entry snapshot on Undo/Discard (`EditorSettingsSnapshot.java:1-60`; `SurfaceEditorController.java:114-124`, `revertToEntry` `:5630+`). Dirtiness for those is a snapshot signature compare.
- **Sheet.** One sheet shows any card at three detents: half (default), full, peek (`EditorCardSheet.java:206-209`). Rows carry a tier 1/2/3 but tiers only order rows; every row is always shown and the sheet scrolls (`EditorRows.java:23-27`). Peek is sized to tier 1 (`EditorCardSheet.java:580-587`). Back above peek collapses to peek; at peek it goes through the unsaved dialog (AGENTS.md; `:2164`).
- **Cards.** Six: `EditorCard` (`EditorCard.java:22-36`): Home ("Home"), Status bar ("Status bar", slot STATUS), Terminal ("Terminal", slot CANVAS), Dock ("Dock", slot DOCK), Keyboard ("Keyboard", slot KEYBOARD), and key `appearance` titled "Global" (`appearance_card_customization`). Door keys `canvas/other/workspace/display` open Terminal, `sessions` Status bar, `widgets` Home, `sheets` Global (`EditorCard.java:85-110`). Sub-cards: Pane borders (Terminal), Icon pack (Home/Dock), A-Z strip and Extra keys (Dock), Camera cutout. Card row lists live in `app/src/main/java/com/termux/app/surfaces/cards/*.java`; the row table (`Row` enum: label, kind, glyph) is `EditorRows.java:75-313`.
- **Card Reset** writes defaults for the card's keys (`EditorCardReset.java`); the Dock card's Reset also writes Global Corners and Margin back to their defaults (`:~40-41`).
- **Glass Looks (Presets row).** Stops: Clear (`minimal`), Frosted (`frost`), Tinted (`stock`), Solid (`solid`), Liquid (`liquid`), Custom (`AppearanceLooks.java:30-36`, names `strings.xml:2345-2350`). A Look writes Frost stop, Opacity and Tint into the card's cell (`GlassRows.lookCell`); ship values `SurfacePresets.java:122-160`: Clear opacity 10 tint 0; Frosted opacity 60 tint 0; Tinted opacity 46 tint 35; Solid opacity 92; Liquid opacity 8.

### 2.2 Row to key map (what each editor control reads and writes)

Slider ranges: Opacity 0-100 (`AppearanceLooks.OPACITY_MAX`), Frost = glass stop 0..`GlassControls.GLASS_MAX`, Tint 0-100, Corner radius 0-40, Margin 0-48, Shine 0-100, Depth `GlassEdgeControls.MIN..MAX`, Text size `MIN_TEXT_SIZE_PERCENT..MAX` step 5 (registry says 80-150), Inner gap 0..`MAX_TERMINAL_PANE_GAP`, Key spacing 0..80 tenths, Key corners 0..`KEY_CORNERS_MAX_DP`. Value read: `SurfaceEditorController.java:2857-2935`; range: `:2960-3000`; write: `:3549-3720`, `:7031-7110`.

| Row (label) | Card(s) | Control | Key / store written | Default | Source |
|---|---|---|---|---|---|
| Wallpaper / Show wallpaper | Global | picker / switch | `use_system_wallpaper` via AppearanceSettingsStore; wallpaper pick via sheet | false | `Controller:2917, 7075` |
| Presets (Look) | Global, Status, Dock, Keyboard | stopped slider | `look.frost`, `look.opacity`, `look.tint` at the card's cell | Frosted on fresh install | `GlassRows.java:76`, `Controller:3471-3505` |
| Opacity | Global; Status; Dock; Keyboard | slider | Global: `look.opacity`; surface: `look.opacity.<status|dock|keyboard>` (legacy named setters: `app_bar_opacity`, `status_bar_opacity`, `in_app_keyboard_backdrop_opacity`); linked to Keys opacity on Keyboard | Global 34 (`DEFAULT_SURFACE_GLOBAL_OPACITY`); fresh install 60 | `Controller:3558-3566, 4186` |
| Frost | same | slider (glass stops) | `look.frost[.element]` and grain (a surface's own Frost brings its grain, `LookGrid.java:30-31`) | Global 8dp blur; fresh install blur 25, grain 7 | `Controller:3858-3895` |
| Tint | Global | slider | `look.tint` | 0 | `Controller:2872` |
| Accent (Tint colour) | Global | menu primary/secondary/tertiary | `surface_glass_tint_hue` / `look.tint_hue` | primary | `Controller:7108, GlassRows:46` |
| Corner radius | Global | slider 0-40 | `look.corners` (via `setLayoutCornersDp`) and `terminal_corner_radius` | 24 | `Controller:4489-4500` |
| Margin | Global | slider 0-48 | `look.margin` (`setLayoutMarginDp`); fresh install also pane gap | 12 (8 fresh) | `Controller:4501` |
| Layout style | Global | segments Docked/Floating | `app_launcher_dock_style` | docked | `Controller:3920-3935` |
| Theme | Global | segments system/light/dark | `night-mode` in termux.properties via ThemePreview, committed on Done | system | `Controller:3680, 2585` |
| Text size | Global | slider % | `text_size_percent` | 100 | `Controller:4235` |
| Icon size | Global (and Home) | segments | layout `sizes` doc `icon_size` (s/m/l/xl) | m | `Controller:6770, 7055` |
| Contrast | Global, Terminal | segments Softer/Default/Harder | `terminal_contrast_level` | default | `Controller:4364` |
| Transition | Global | segments slide/depth/fade/carousel/stack | `app_launcher_home_page_transition` | slide | `Controller:3680-3700` |
| Adaptive legibility | Global, Terminal | switch | `adaptive_legibility` | false | `Controller:4378` |
| Wallpaper colours / Dynamic colour | Global | switch | `terminal_dynamic_colors_enabled` | true | `Controller:2922` |
| Full screen | Global | switch | `fullscreen` (termux.properties via terminal store) | false | `Controller:2925` |
| TUI app themes (link) | Global | link | opens Settings > Terminal > TUI app themes | | `EditorRows:170` |
| Fine-tune: Depth, Shine | Global | sliders (Depth hidden where glass edge not drawn) | `look.depth` (bend dp), `look.shine` | Shine 50 | `Controller:4248, 4292`, `GlobalCard` |
| Glass refraction (link) | Global | link, only when Depth/Shine unavailable (refraction off, idle reduced, wallpaper not set from launcher) | opens Battery & performance | | `GlobalCard.java:38`, `Offers.refractionLink` |
| Columns x Rows | Home | steppers | layout `widget_columns`, `widget_rows` (2-8, 2-12) | 4, 5 | `Controller:7040`, `LayoutEditorController` |
| Label size | Home | segments | `app_launcher_home_label_size` | medium | `Controller:3535` |
| Folders | Home | segments | `app_launcher_home_folder_density` | | |
| Numerals font | Home | segments | `app_launcher_builtin_widget_style` | | |
| Two-line labels / Label shadow | Home | switches | `app_launcher_home_label_two_lines` (false) / `app_launcher_home_label_shadow` (true) | | `Controller:2912-2919` |
| Wallpaper scrolling | Home | switch | `wallpaper_parallax` | true | |
| Glass widgets | Home | switch | `widgets_on_glass` (also clears/sets the widgets element cells) | | `TermuxAppSharedPreferences.java:935-950` |
| Search bar / Drawer icon | Home, Terminal, Dock | switches | layout `search_field`, `drawer_tile` | | `Controller:3604-3622` |
| Icon pack / Gestures (links) | Home | link | `app_launcher_icon_pack_package`; Settings > Home > Gestures | | |
| Font size / Inner gap | Terminal | sliders | registry `fontsize` (display suffix); `terminal_pane_gap` | gap 4 | `Controller:4223, 7056` |
| Opacity / Frost (panes) | Terminal | sliders | `terminal_bg_opacity` (40), `terminal_bg_blur` | not Global's, no preset | `Controller:4458` |
| Wallpaper dim | Terminal | slider 0-100 | `terminal_wallpaper_dim` | 0 | `Controller:4458` |
| Cursor trail | Terminal | menu | `terminal_cursor_trail` + `terminal_cursor_trail_style` | on / default | `Controller:4391` |
| Effect | Terminal (only where shader runs) | preview tiles | `terminal_retro_effect` | none | `TerminalCard.java:57` |
| Screen events / Composite signal | Terminal (only while an effect is on) | chips | `terminal_screen_events`, `terminal_retro_composite` | true / false | `Controller:3238` |
| Multiplexer / Auto tiling / Enlarge focused pane | Terminal | chips | `compatibility_mode` (inverted), `pane_dwindle_default`, `pane_focus_grows` (the last two enabled only with Multiplexer on) | | `Controller:2659-2688, 3095` |
| Minimal | Terminal | chip | minimal mode `place.minimal` | | `Controller:3700` |
| Font / Pane borders (links) | Terminal | links | Settings fonts; Pane borders sub-card | | |
| Pane borders sub-card | Terminal | segments/menus/reset | `terminal_pane_border_colour` (outline), `_custom`, `terminal_pane_focus_colour` (primary), `_custom`, `terminal_pane_glow` (false), `_glow_colour` (same_as_border), `_glow_custom` | | `PaneBordersCard.java`, Constants:702-720 |
| Page bar / Elements | Status bar | segments / switch tiles | `page_bar_look` (glyphs; marks on fresh install); status tiles below | | `StatusBarCard.java:56` |
| Elements tiles | Status bar | 9 switch tiles | `status_clock_shown` (true), `status_widget_cpu` (false), `_gpu` (false), `_ram` (false), `_weather` (true), `_notifications` (false), `_media` (true), `_battery` (false), `_font_toggle` (false) | | `StatusBarCard.java:116-126`, `Controller:6270-6340` |
| Clock face / position | Status bar | menu / segments | `top_pane_clock_style` (slab), `top_pane_clock_alignment` (left) | | `StatusBarCard.java:66-110` |
| Clock format / Show seconds / Temperature | Status bar | segments / switch / segments | `top_pane_clock_am_pm` (true), `status_clock_seconds` (true), `status_widget_weather_fahrenheit` (false) | | `Controller:6232-6262` |
| Camera cutout / Pinned notifications | Status bar | links | Settings status bar cutout / essential notification rules | | |
| Dock type | Dock | menu | layout `dock_design` | see section 5 | `Controller:6612` |
| Apps in the dock | Dock | menu | `app_launcher_button_count` (7) | | |
| Dock options chips | Dock | chips | Search field `search_field`; Drawer icon `drawer_tile`; Command palette `dock_command_palette_button`; Float terminal `dock_float_terminal_button`; Notification dots `app_launcher_notification_dots`; Quick reply `app_launcher_quick_reply_swipe`; Monochrome `app_launcher_bw_icons`; Most used `app_launcher_most_used_page` | | `Controller:6625-6700`, `DockCard.java:52-85` |
| Reflection | Dock (only Peek glass type) | slider | `dock_peek_reflection` | `REFLECTION_DEFAULT` | `PeekGlassPreferences.java:16`, `Controller:3228` |
| A-Z strip sub-card | Dock | Glass background switch, Show segments, Opacity/Frost | `az_glass_background`, `az_show` | | `DockCard.java:362` |
| Extra keys sub-card | Dock | Opacity/Frost, editor link | | | `DockCard.java:370` |
| Keyboard type (Form) | Keyboard | segments docked/floating/split | layout `keyboard_form` | docked | `Controller:3680` |
| Key spacing / Key corner radius | Keyboard | sliders | `in_app_keyboard_key_margin_scale` (theme default when absent), `in_app_keyboard_key_corner_radius_dp` | | `Controller:4301, 4441` |
| Bottom padding | Keyboard | slider | layout `keyboard_chin` | 0 | `Controller:6755` |
| Width / Floating height / Split gap | Keyboard (only floating / floating / split) | sliders % | `in_app_keyboard_floating_width_scale` (0.90; landscape 0.60), `..._height_scale` (1.0), `in_app_keyboard_split_gap_fraction` (0.12; landscape 0.25) | | `Controller:7085-7105, 3244` |
| Opacity / Keys opacity / link | Keyboard | linked pair | `in_app_keyboard_backdrop_opacity`, `in_app_keyboard_key_opacity` (-1 follows), `in_app_keyboard_opacity_linked` (false) | | `KeyboardCard.java:33` |
| Frost | Keyboard | slider | `look.frost.keyboard` / `in_app_keyboard_blur_radius` (-1) | | |
| Keyboard theme / Layouts (links) | Keyboard | links | `in_app_keyboard_theme` (system), `in_app_keyboard_color_scheme`; layouts page | | |

### 2.3 Surface to card to property table

"own" = the surface may hold its own cell; "Global" = follows Global; "-" = not offered.

| Surface | Card that owns it | Opacity | Blur / Frost | Grain | Tint | Corners | Edge gap / Margin | Notes |
|---|---|---|---|---|---|---|---|---|
| All glass (Global) | Global | own value `look.opacity` | `look.frost` | follows Frost stop | `look.tint` + colour | `look.corners` | `look.margin` | Depth, Shine (`look.depth`, `look.shine`), Vibrancy are Global-only |
| Status bar | Status bar | own (`look.opacity.status`) | own | Global's, or its Frost's grain | Global | Global (stored -1 = follow style) | Global | `SurfaceSlot.STATUS` |
| Dock | Dock | own | own | Global | Global | Global | Global | slot DOCK; Docked/Floating differ in resolution |
| Keyboard | Keyboard | own + keys opacity | own | Global | Global | Global (no radius of its own) | Global | |
| Terminal panes (CANVAS) | Terminal | `terminal_bg_opacity` (never Global) | `terminal_bg_blur` | `terminal_glass_grain` | Global | n/a (`terminal_corner_radius` via Corner radius) | n/a | plus Wallpaper dim; no preset, no reset arrow |
| Widgets | Home (Glass widgets switch) | Global | Global | | | | | follow Global while `widgets_on_glass` |
| Sheets (drawer, folders, menus, notices) | none | Global with a code floor | Global | | | | | ADR 0031 §2 |
| Home | Home | no glass; grid and labels only | | | | | | wears Global |

### 2.4 Cross-cutting

- **AppearanceReset.NUMBER = 1** (`AppearanceReset.java:52`). First start of a build whose number exceeds `appearance_reset_seen` (`:55`) removes in one commit every key in `inScope` (`:163`): the named list (`:69-160`, below), every `look.*` grid cell, `surface_inherit_*`, `<key>@<page>` page values, `fontsize*`, flat `layout.*` / `place.dress.*` copies, the Appearance-group registry keys (terminal contrast, text size, cursor trail, terminal effect/events/composite, status battery/media/weather/AM-PM, dock style, plus font-size families) and Global/own surface keys. It then sets `appearance_reset_seen`, `appearance_reset_card` = due and settles the glass nudge (`:225-239`), rewrites layouts (`LayoutReset.write`) and uninstalls the managed font config and forgets the family (`:198-214`). Runs from `TermuxApplication.java:157` before the look migration steps; a fresh install only stores the number. Not removed: Home pages, widgets, pins, sessions, wallpaper, `termux.properties`, keyboard layouts and positions, one-time flags (`AppearanceReset.java:40-44`). Home's label/folder/numerals/page-indicator keys, `theme_mode`, icon pack keys, `widgets_on_glass`, `wallpaper_parallax`, `use_system_wallpaper` are not in the named list or registry group (grep of `AppearanceReset.java`).
- **adoptShippedSurfaceDefaults** (`TermuxAppSharedPreferences.java:3349`, called from `migrateLookGrid`, `:3255`), once under `shipped_surface_defaults_adopted`. Fresh install (store holds only `log_level`, `isFreshInstall` `:3380`): writes Global blur 25, opacity 60, grain 7, material Frost, tint Obsidian, motion Mist, bend `REALISTIC_BEND_DP`, shine default, margin 8 (and pane gap 8), wallpaper-mode remembered values, page bar Marks, status place pane. Existing install: pins the pre-shipped numbers (`PRE_SHIPPED_DOCK_DEFAULTS` dock blur 10, opacity 46, grain 39, corner -1, inset 10; `PRE_SHIPPED_DEFAULTS` terminal border off, corner 0, pane gap 1, keyboard height 1.0830541, key radius 2.7, clock Flip, AM/PM false, collapsed false, wallpaper-mode values 47/46/10) for every key it never set (`:3433-3470`).
- **Per edition.** This branch contains no edition-specific code path (grep for edition flags in `app/` and `termux-shared/`: none; versionName `2.0.0-pre.1`, `app/build.gradle:61`; `TERMUX_PACKAGE_NAME` `com.termux`, `:71`). Editions differ only by release-branch identity per AGENTS.md; not verified on those branches.
- **State-dependent controls:** Docked vs Floating: Layout style switch changes the corner/gap resolution (`resolveAutoCornerRadiusDp`), the Dock card's apps-row rows stand only where an apps row exists (`Offers.appsRow`, `DockCard.java:177`); Reflection only with the Peek glass dock type (`Controller:3228`); keyboard Width/Floating height only while floating, Split gap only while split (`:3244`); Screen events / Composite only while an effect is on (`:3238`) and the Effect row only where the shader runs (`TerminalCard.java:57`); Depth/Shine greyed when glass refraction is inactive (`:3102`); Home card absent under "Use as Terminal only" (`EditorRows.Offers.homeShown`). Scheme chrome: `isSchemeChromeActive` is not referenced in `surfaces/`, `fragments/` or `settings/` (grep), so no control is gated on it. Privileged: gating is in the Settings pages (status widgets: `StatusWidgetPrivilegedGate.java`, Privileged access page), not in the editor.

Keys in `AppearanceReset.NAMED` with their constant line and the `DEFAULT_*` constant of the same name (generated):

| Constant | Key (Constants line) | Default constant |
|---|---|---|
| `KEY_SURFACE_MATERIAL` | `surface_material` (:1866) | `SURFACE_MATERIAL_GLASS` (:1872) |
| `KEY_SURFACE_MATERIAL_INTENSITY` | `surface_material_intensity` (:1867) | `50` (:1873) |
| `KEY_SURFACE_GLASS_TINT` | `surface_glass_tint` (:1881) | `GLASS_TINT_SCHEME` (:1892) |
| `KEY_SURFACE_GLASS_TINT_HUE` | `surface_glass_tint_hue` (:1895) | `GLASS_TINT_HUE_PRIMARY` (:1899) |
| `KEY_SURFACE_GLASS_VIBRANCY` | `surface_glass_vibrancy` (:1905) | `100` (:1906) |
| `KEY_SURFACE_GLASS_RIM` | `surface_glass_rim` (:1939) | `GLASS_RIM_HAIRLINE` (:1944) |
| `KEY_SURFACE_GLASS_MOTION` | `surface_glass_motion` (:1946) | `GLASS_MOTION_CLASSIC` (:1951) |
| `KEY_SURFACE_GLASS_POSITION` | `surface_glass_position` (:1932) | - |
| `KEY_SURFACE_CUSTOM_PRESET` | `surface_custom_preset` (:1958) | - |
| `KEY_FANCIER_GLASS_BEND` | `fancier_glass_bend` (:1584) | `9` (:1585) |
| `KEY_FANCIER_GLASS_EDGE_WIDTH` | `fancier_glass_edge_width` (:1590) | `20` (:1591) |
| `KEY_FANCIER_GLASS_EDGE_LIGHT` | `fancier_glass_edge_light` (:1596) | `18` (:1597) |
| `KEY_FANCIER_GLASS_SPECULAR` | `fancier_glass_specular` (:1606) | `0` (:1607) |
| `KEY_FANCIER_GLASS_DISPERSION` | `fancier_glass_dispersion` (:1615) | `0` (:1616) |
| `KEY_FANCIER_GLASS_CLARITY` | `fancier_glass_clarity` (:1626) | `0` (:1627) |
| `KEY_FANCIER_GLASS_FACE_CLARITY` | `fancier_glass_face_clarity` (:1638) | - |
| `KEY_FANCIER_GLASS_DIM` | `fancier_glass_dim` (:1639) | - |
| `KEY_FANCIER_GLASS_SIZE_LENS` | `fancier_glass_size_lens` (:1640) | - |
| `KEY_EXTRAKEYS_BLUR_RADIUS` | `extrakeys_blur_radius` (:1798) | `8` (:1800) |
| `KEY_APP_BAR_OPACITY` | `app_bar_opacity` (:1805) | `43` (:1807) |
| `KEY_DOCK_GLASS_GRAIN` | `dock_glass_grain` (:1813) | `18` (:1815) |
| `KEY_APP_LAUNCHER_DOCK_CORNER_RADIUS` | `app_launcher_dock_corner_radius` (:515) | `-1` (:517) |
| `KEY_DOCK_HORIZONTAL_INSET` | `dock_horizontal_inset` (:731) | - |
| `KEY_STATUS_BAR_CORNER_RADIUS` | `status_bar_corner_radius` (:610) | `-1` (:611) |
| `KEY_STATUS_BAR_HORIZONTAL_INSET` | `status_bar_horizontal_inset` (:734) | - |
| `KEY_HOME_LAYOUT_STYLE` | `home_layout_style` (:510) | - |
| `KEY_HOME_SURFACE_CORNER_RADIUS` | `home_surface_corner_radius` (:511) | - |
| `KEY_HOME_SURFACE_SIDE_GAP` | `home_surface_side_gap` (:512) | - |
| `KEY_WALLPAPER_ENABLED_TERMINAL_BACKGROUND_OPACITY` | `wallpaper_enabled_terminal_background_opacity` (:2008) | `DEFAULT_TERMINAL_BG_OPACITY` (:2011) |
| `KEY_WALLPAPER_ENABLED_APP_BAR_OPACITY` | `wallpaper_enabled_app_bar_opacity` (:2018) | `43` (:2021) |
| `KEY_WALLPAPER_ENABLED_EXTRAKEYS_BLUR_RADIUS` | `wallpaper_enabled_extrakeys_blur_radius` (:2027) | `DEFAULT_VALUE_EXTRAKEYS_BLUR_RADIUS` (:2030) |
| `KEY_WALLPAPER_BACKDROP_DIM` | `wallpaper_backdrop_dim` (:684) | `0` (:685) |
| `KEY_TERMINAL_BG_OPACITY` | `terminal_bg_opacity` (:654) | `40` (:660) |
| `KEY_TERMINAL_BG_BLUR` | `terminal_bg_blur` (:661) | `DEFAULT_TERMINAL_GLASS_BLUR_RADIUS` (:663) |
| `KEY_TERMINAL_WALLPAPER_DIM` | `terminal_wallpaper_dim` (:666) | `0` (:668) |
| `KEY_APP_LAUNCHER_DOCK_STYLE` | `app_launcher_dock_style` (:472) | `APP_LAUNCHER_DOCK_STYLE_DOCKED` (:495) |
| `KEY_APP_LAUNCHER_BUTTON_COUNT` | `app_launcher_button_count` (:120) | `7` (:122) |
| `KEY_TERMINAL_BORDER_ENABLED` | `terminal_border_enabled` (:1820) | `true` (:2002) |
| `KEY_TERMINAL_CORNER_RADIUS` | `terminal_corner_radius` (:634) | `24` (:635) |
| `KEY_TERMINAL_PANE_GAP` | `terminal_pane_gap` (:702) | `4` (:703) |
| `KEY_TERMINAL_PANE_BORDER_COLOUR` | `terminal_pane_border_colour` (:710) | `"outline"` (:711) |
| `KEY_TERMINAL_PANE_BORDER_CUSTOM` | `terminal_pane_border_custom` (:712) | - |
| `KEY_TERMINAL_PANE_FOCUS_COLOUR` | `terminal_pane_focus_colour` (:713) | `"primary"` (:714) |
| `KEY_TERMINAL_PANE_FOCUS_CUSTOM` | `terminal_pane_focus_custom` (:715) | - |
| `KEY_TERMINAL_PANE_GLOW` | `terminal_pane_glow` (:716) | `false` (:717) |
| `KEY_TERMINAL_PANE_GLOW_COLOUR` | `terminal_pane_glow_colour` (:718) | `"same_as_border"` (:719) |
| `KEY_TERMINAL_PANE_GLOW_CUSTOM` | `terminal_pane_glow_custom` (:720) | - |
| `KEY_ADAPTIVE_LEGIBILITY` | `adaptive_legibility` (:2165) | `false` (:2167) |
| `KEY_TERMINAL_DYNAMIC_COLORS_ENABLED` | `terminal_dynamic_colors_enabled` (:2134) | `true` (:2136) |
| `KEY_TERMINAL_CURSOR_TRAIL` | `terminal_cursor_trail` (:887) | `true` (:888) |
| `KEY_TERMINAL_CURSOR_TRAIL_STYLE` | `terminal_cursor_trail_style` (:891) | `"default"` (:892) |
| `KEY_TERMINAL_RETRO_EFFECT` | `terminal_retro_effect` (:895) | `"none"` (:896) |
| `KEY_TERMINAL_SCREEN_EVENTS` | `terminal_screen_events` (:909) | `true` (:910) |
| `KEY_TERMINAL_RETRO_COMPOSITE` | `terminal_retro_composite` (:916) | `false` (:917) |
| `KEY_IN_APP_KEYBOARD_FLOATING_WIDTH_SCALE` | `in_app_keyboard_floating_width_scale` (:1324) | `0.90f` (:1330) |
| `KEY_IN_APP_KEYBOARD_FLOATING_WIDTH_SCALE_LANDSCAPE` | `in_app_keyboard_floating_width_scale_landscape` (:1327) | `0.60f` (:1332) |
| `KEY_IN_APP_KEYBOARD_FLOATING_HEIGHT_SCALE` | `in_app_keyboard_floating_height_scale` (:1347) | `1.0f` (:1353) |
| `KEY_IN_APP_KEYBOARD_FLOATING_HEIGHT_SCALE_LANDSCAPE` | `in_app_keyboard_floating_height_scale_landscape` (:1350) | `1.0f` (:1355) |
| `KEY_IN_APP_KEYBOARD_SPLIT_GAP_FRACTION` | `in_app_keyboard_split_gap_fraction` (:1368) | `0.12f` (:1374) |
| `KEY_IN_APP_KEYBOARD_SPLIT_GAP_FRACTION_LANDSCAPE` | `in_app_keyboard_split_gap_fraction_landscape` (:1371) | `0.25f` (:1376) |
| `KEY_COMPATIBILITY_MODE` | `compatibility_mode` (:1523) | `false` (:1525) |
| `KEY_DWINDLE_DEFAULT_LAYOUT` | `pane_dwindle_default` (:1662) | `false` (:1663) |
| `KEY_FOCUSED_PANE_GROWS` | `pane_focus_grows` (:1666) | `false` (:1667) |
| `KEY_IN_APP_KEYBOARD_BLUR_RADIUS` | `in_app_keyboard_blur_radius` (:1442) | `-1` (:1444) |
| `KEY_IN_APP_KEYBOARD_GRAIN` | `in_app_keyboard_grain` (:1454) | `-1` (:1456) |
| `KEY_IN_APP_KEYBOARD_KEY_OPACITY` | `in_app_keyboard_key_opacity` (:1404) | `-1` (:1407) |
| `KEY_IN_APP_KEYBOARD_OPACITY_LINKED` | `in_app_keyboard_opacity_linked` (:1418) | `false` (:1421) |
| `KEY_IN_APP_KEYBOARD_KEY_CORNER_RADIUS_DP` | `in_app_keyboard_key_corner_radius_dp` (:1305) | `10.8f` (:1308) |
| `KEY_IN_APP_KEYBOARD_KEY_MARGIN_SCALE` | `in_app_keyboard_key_margin_scale` (:1290) | `2.96f` (:1293) |
| `KEY_IN_APP_KEYBOARD_BACKDROP_OPACITY` | `in_app_keyboard_backdrop_opacity` (:1469) | `-1` (:1472) |
| `KEY_IN_APP_KEYBOARD_HEIGHT_SCALE` | `in_app_keyboard_height_scale` (:1270) | `1.0423018f` (:1280) |
| `KEY_IN_APP_KEYBOARD_HEIGHT_SCALE_LANDSCAPE` | `in_app_keyboard_height_scale_landscape` (:1276) | - |
| `KEY_IN_APP_KEYBOARD_THEME` | `in_app_keyboard_theme` (:1083) | `"system"` (:1085) |
| `KEY_IN_APP_KEYBOARD_COLOR_SCHEME` | `in_app_keyboard_color_scheme` (:1088) | `""` (:1091) |
| `KEY_TOP_PANE_CLOCK_STYLE` | `top_pane_clock_style` (:1682) | `TOP_PANE_CLOCK_STYLE_SLAB` (:1696) |
| `KEY_TOP_PANE_CLOCK_ALIGNMENT` | `top_pane_clock_alignment` (:1699) | `TOP_PANE_CLOCK_ALIGNMENT_LEFT` (:1705) |
| `KEY_TOP_PANE_CLOCK_AM_PM` | `top_pane_clock_am_pm` (:1708) | `true` (:1709) |
| `KEY_TOP_PANE_CLOCK_COLLAPSED` | `top_pane_clock_collapsed` (:1712) | `true` (:1713) |
| `KEY_STATUS_CLOCK_SECONDS` | `status_clock_seconds` (:817) | `true` (:818) |
| `KEY_STATUS_CLOCK_SHOWN` | `status_clock_shown` (:821) | `true` (:822) |
| `KEY_STATUS_WIDGET_CPU` | `status_widget_cpu` (:745) | `false` (:746) |
| `KEY_STATUS_WIDGET_GPU` | `status_widget_gpu` (:749) | `false` (:750) |
| `KEY_STATUS_WIDGET_FONT_TOGGLE` | `status_widget_font_toggle` (:753) | `false` (:754) |
| `KEY_STATUS_WIDGET_RAM` | `status_widget_ram` (:785) | `false` (:786) |
| `KEY_STATUS_WIDGET_BATTERY` | `status_widget_battery` (:788) | `false` (:789) |
| `KEY_STATUS_WIDGET_MEDIA` | `status_widget_media` (:795) | `true` (:796) |
| `KEY_STATUS_WIDGET_WEATHER` | `status_widget_weather` (:798) | `true` (:799) |
| `KEY_STATUS_WIDGET_WEATHER_FAHRENHEIT` | `status_widget_weather_fahrenheit` (:802) | `false` (:803) |
| `KEY_STATUS_WIDGET_NOTIFICATIONS` | `status_widget_notifications` (:809) | `false` (:810) |
| `KEY_STATUS_ROW_ORDER` | `status_row_order` (:760) | `""` (:761) |
| `KEY_PAGE_BAR_LOOK` | `page_bar_look` (:769) | `PAGE_BAR_LOOK_GLYPHS` (:772) |
| `KEY_STATUS_PLACE` | `status_place` (:780) | `STATUS_PLACE_STRIP` (:783) |
| `KEY_SHIPPED_SURFACE_DEFAULTS_ADOPTED` | `shipped_surface_defaults_adopted` (:1971) | - |
| `KEY_SURFACE_INHERITANCE_MIGRATED` | `surface_inheritance_migrated` (:1964) | - |
| `KEY_KEYBOARD_GLASS_SLOT_FOLDED` | `keyboard_glass_slot_folded` (:1989) | - |
| `KEY_SURFACE_TUNING_NORMALIZED` | `surface_tuning_normalized` (:1999) | `false` (:2000) |
| `KEY_LAYOUT_STYLE_MIGRATED` | `layout_style_migrated` (:501) | - |
| `KEY_SURFACE_GLASS_CONTROLS_FORMAT` | `surface_glass_controls_format` (:1937) | - |

## 3. Other control surfaces that change settings

### 3.1 launcherctl and the settings registry

Commands (paths under `app/src/main/java/com/termux/` unless noted; from the delegated scan, items "unclear" unverified):

| Command | What it changes | Key(s) | Values | Source |
|---|---|---|---|---|
| `config set ID VALUE` | any registry id; handed to the applier | per id (3.2) | out of range is a 400 `invalid_value` | `launcherctl/ConfigCommand.java:63-73`, `LauncherCtlApiServer.java:3042, 3324-3340`, `DeviceControlRoutes.java:128-130` |
| `config apply FILE` | saves every `ID = VALUE` line; all lines checked first | as `config set` | any bad line saves nothing | `launcherctl/ConfigFileCommand.java:33-65` |
| `look set PROPERTY VALUE [--element E]`, `look reset` | look grid and terminal Background | `look.<property>`, `look.<element>.<property>`, `look.background.<opacity|blur|dim>` | 3.3 | `app/settings/LookCommand.java:122-172, 250-281`, `DeviceControlRoutes.java:124-125, 324-332` |
| `layout set KIND FILE` | replaces a kind document or `sizes` | layouts file kinds `home`, `terminal`, `terminal_minimal`, `sizes` (`display`, `assistant` retired aliases) | validated by `LayoutsJson` | `app/settings/LayoutCommand.java:27-91`, `place/layout/LayoutStore.java:33`, `DeviceControlRoutes.java:126-127, 336-372` |
| `pagebar-glass [concave|well]` | Home page-bar glass style | `page_bar_glass_style` | concave, well | `launcherctl/DeviceControlRoutes.java:287-307`, `wall/pageswitch/PageBarGlassStyle.java:36-92`, Constants:2147 |
| `volume [STREAM VALUE]` | Android stream volume (not a pref) | | alarm, music, notification, ring, system, call | `DeviceControlRoutes.java:538-568` |
| `wallpaper set FILE [--home|--lock|--both]` | system wallpaper and launcher Home/Lock slots | | path under Termux home or shared storage | `DeviceControlRoutes.java:633-680` |
| `POST /v1/root/{lock,reboot,wifi,data,airplane,dnd}` | device toggles as root | none | reboot needs the Privileged switch | `DeviceControlRoutes.java:78-84, 210-268` |
| low-memory | no subcommand; registry id `battery.low_memory_preset` through `config set` | `low_memory_preset` | bool | `app/settings/LowMemoryPresetAdapter.java:26, 38` |
| use-as | no launcherctl route; written by the setup flow | `use_as` | unclear | `app/setup/SetupState.java:204-216`, `app/settings/UseAsSetting.java:27-33` |

`config list`/`get`, `look get`, `layout get` only read.

### 3.2 Registry ids (`config set`), `termux-shared/.../settings/registry/SettingDeclarations.java`

APP_PREFERENCES store unless noted. Ids with key, values, default, line:

| Id | Key | Values | Default | Line |
|---|---|---|---|---|
| theme.mode | `night-mode` (termux.properties) | system, light, dark | system | :29-33 |
| terminal.contrast | `terminal_contrast_level` | softer, default, harder | default | :40-43 |
| text.size | `text_size_percent` | 80-150 step 5 | 100 | :51-55 |
| motion.animation_speed | `animation_speed` | slower, normal, faster, off | normal | :65-68 |
| motion.page_transition | `app_launcher_home_page_transition` | slide, depth, fade, carousel, stack | slide | :76-80 |
| motion.idle_animations | `idle_animations` | bool | false | :88-90 |
| motion.idle_tear_rate | `idle_tear_rate` | rare, sometimes, often | rare | :97-100 |
| motion.cursor_trail | `terminal_cursor_trail` | bool | true | :106-108 |
| motion.wallpaper_parallax | `wallpaper_parallax` | bool | true | :115-117 |
| motion.live_windows | `live_windows_while_swiping` | bool | true | :124-126 |
| battery.reduce_idle | `lazy_mode` | bool | false | :135-137 |
| glass.refraction | `fancier_glass` | bool | true | :143-145 |
| glass.live_blur | `live_blur` | bool | false | :152-154 |
| terminal.effect | `terminal_retro_effect` | none, crt, crt_green, crt_amber, crt_material, tft | none | :161-165 |
| terminal.screen_events | `terminal_screen_events` | bool | true | :173-175 |
| terminal.effect_composite | `terminal_retro_composite` | bool | false | :182-184 |
| status.battery / media / weather | `status_widget_battery` / `_media` / `_weather` | bool | false / true / true | :192-210 |
| status.clock_am_pm | `top_pane_clock_am_pm` | bool | true | :216-218 |
| dock.style | `app_launcher_dock_style` | docked, floating | docked | :228-231 |
| notifications.dots | `app_launcher_notification_dots` | bool | false | :240-242 |
| notifications.badge_style | `app_launcher_notification_badge_style` | dot, count | dot | :247-250 |
| home.quick_reply_swipe | `app_launcher_quick_reply_swipe` | bool | true | :255-257 |
| drawer.search_on_open | `app_launcher_drawer_search_on_open` | bool | false | :262-264 |
| drawer.most_used_page | `app_launcher_most_used_page` | bool | false | :270-272 |
| battery.low_memory_preset | `low_memory_preset` | bool | false | `LowMemoryPresetAdapter.java:26, 38` |
| terminal.font_size, compact_font_size, scratchpad_font_size | `fontsize`, `fontsize_scratchpad` (display suffix) | ranges unclear | unclear | `FontSizeSettingsAdapter.java:21-72` |

Also registered: `look.*` ids (`LookSettingsAdapter.java:35, 91-104`), `layout.<kind>.<path>` ids (`LayoutSettingsAdapter.java:46, 212`); assembled in `LauncherSettingsAdapters.java:31-44`, `LauncherSettings.java:50-58`. The registry is the source of the declared keys the Appearance reset removes (those with `resetBy(APPEARANCE)`: terminal.contrast, text.size, motion.cursor_trail, terminal.effect, screen_events, effect_composite, status.battery/media/weather/clock_am_pm, dock.style).

Low-memory preset side effects (`app/settings/LowMemoryPreset.java:43-76, 132-144`): applies the Solid surface preset, then `terminal_bg_opacity` 100, `terminal_cursor_trail` false, `terminal_retro_effect` none, `wallpaper_parallax` false, `lazy_mode` true; snapshot in `low_memory_preset_snapshot`; also called by setup (`SetupState.java:465-467`).

### 3.3 Look grid via `look` (`LookCommand`)

CLI elements: status, dock, keyboard, background (`LookCommand.java:133-146`); only opacity and frost may be per element (`:176-178`).

| Property (CLI to key) | Range | Shipped | Source |
|---|---|---|---|
| blur to `frost` | 0..`FrostStops.LAST` (or `Ndp`) | Clear stop | `LookGrid.java:63`, `LookCommand.java:324-331` |
| grain | 0-100 | from frost | `LookGrid.java:65` |
| opacity | 0-100 | 34 | `LookGrid.java:66` |
| tint | 0-100 | 0 | `LookGrid.java:67` |
| colour to `tint_hue` | primary, secondary, tertiary | primary | `LookGrid.java:68` |
| depth | fancier-glass bend range | `DEFAULT_VALUE_FANCIER_GLASS_BEND` (9) | `LookGrid.java:70-72` |
| corners | auto, or 0-40 dp | 24 | `LookGrid.java:78-79` |
| rim | off, hairline, bright (retired by Shine) | hairline | `LookGrid.java:81` |
| shine | 0-100, global only | 50 | `LookGrid.java:83` |
| vibrancy | min..max | 100 | `LookGrid.java:84-86` |
| margin | 0..48, global only | 12 | `LookGrid.java:88-90` |
| background opacity / blur dp / dim | 0-100 / 0..max / 0-100 | 40 / terminal glass blur / 0 | `LookCommand.java:76-81`, `LauncherSettingsAdapters.java:49-68` |

### 3.5 Layout editor (`app/layouteditor/`, stores in `app/place/`)

Path: `LayoutEditorController` to `LayoutEditorPlan` to `PlaceLayoutStore` to `LayoutDocumentPrefs` to the `layouts` file (`LayoutStore.java:33, 66-70`); flat keys `layout.<scope><orientation>.<field>` are routed into kind documents (`LayoutDocumentPrefs.java:1-30`); sizes live in the `sizes` document.

| Control | Key / field | Values | Default | Source |
|---|---|---|---|---|
| Dock style (design) | `dock_design`; also `drawer_tile` when TASKBAR | glass_tray, lens_beads, taskbar, prompt_line, wall_strip, peek_glass | javadoc says GLASS_TRAY, `NEW_INSTALL_DEFAULT` is PEEK_GLASS; fallback unclear | `LayoutEditorController` 481-483, `PlaceLayoutStore.java:1189-1203`, `DockStyle.java:29-62` |
| Search field | `search_field` | bool | on when dock is prompt_line | `PlaceLayoutStore.java:1216-1226` |
| Drawer tile (+ position) | `drawer_tile`, `drawer_tile_position` | bool; start, middle, end or column | on for TASKBAR | `PlaceLayoutStore.java:1241-1305` |
| Keyboard form | `keyboard_form` | docked, floating, split (per orientation) | docked | `PlaceLayoutStore.java:1677-1682` |
| Keyboard shown / rows / mode | `keyboard_shown`, `keyboard_open`, `keyboard_rows`, `keyboard_mode` | bool; int, 0 = all; resize/overlay | rows 0; mode overlay | `PlaceLayoutStore.java:1115-1162, 1645-1650` |
| Dock height | `dock_height` (sizes) | 0.4-3.0 | 2.18 | Constants:454-465 |
| Keyboard height | `keyboard_height` (sizes) | 0.5-1.6 | unclear | `PlaceLayoutStore.java:1745`, Constants:1285 |
| Keyboard chin | `keyboard_chin` (sizes) | 0-48 dp | 0 | Constants:1393-1397 |
| Wall strip height | `wall_strip_height` | 56-128 dp | 84 | `WallStripRowPolicy.java:47-49` |
| Widget grid | `widget_columns`, `widget_rows` | 2-8, 2-12 | 4, 5 | `PlaceLayoutStore.java:1685-1706`, Constants:182-194 |
| Status edge / compact | `status_bar`, `status_edge`, `status_compact` | top, bottom, left, right, hidden; bool | top; false | `PlaceLayoutStore.java:1381-1539, 1815-1821` |
| Slot placement | `apps_row`, `extra_keys`, `status_bar` | bottom, left, right, hidden | unclear | `PlaceLayoutStore.java:1395-1421, 1580-1632` |
| A-Z bar | `az_row`, `az_bar` | bool; edge | true; bottom | `PlaceLayoutStore.java:1588-1613` |
| Order / under keyboard | `<element>_order`, `<element>_under_keyboard` | int; bool | | `PlaceLayoutStore.java:1442-1470` |
| Bar order | `bar_order` | comma list | | `PlaceLayoutStore.java:2917` |
| Floating keyboard / voice pill position | `keyboard_float_x/y`, `voice_pill_x/y` | fractions | unclear | `PlaceLayoutStore.java:1905, 1926` |
| Minimal mode | `place.minimal` | bool | unclear | `PlaceLayoutStore.java:1875-1883` |
| Icon size | `icon_size` (sizes) | s, m, l, xl (40, 48, 54, 60 dp) | m | `IconSize.java:16-25, 79` |

AI tool registry (`launcherctl/LauncherToolRegistry.java`, dispatch `app/terminal/TerminalActionDispatcher.java:395-430`): `keyboard.set_form` (:939-944), `keyboard.cycle_form` (:931), `keyboard.toggle_enabled` (:974), `keyboard.select_layout` (:919), `minimal.toggle` (:1364, key `place.minimal`), `terminal.font_size_increase/decrease/toggle_compact` (:993-1010), `appearance.toggle_wallpaper` (:1326), `appearance.toggle_cursor_trail` (:1332), `mouse.toggle` (:725); persistence keys not traced for most.


### 3.4 Menus, drawer edit mode, tour and setup, quick toggles

**Not delivered.** The delegated scan of long-press menus, the status bar and dock menus, drawer edit mode, the tour and setup sheet and quick toggles had not returned when this note was written, so those surfaces are NOT inventoried here. Known from this research: the dock card's chips (Command palette, Float terminal) write the keys in sections 2.2 and 3.5; Home's gestures page is in 1.5; the setup flow writes use_as and the low-memory preset (3.1). Everything else is open (section 6).

## 5. Orphaned or inconsistent (facts)

1. Dead code: `TerminalPreferencesFragment.java:41-47` looks up `terminal_retro_effect`, `terminal_screen_events`, `terminal_retro_composite`, none of which is in `terminal_preferences.xml`; the rows are in `performance_preferences.xml` (handled at `PerformancePreferencesFragment.java:68-78`).
2. `appearance_search_index.xml` lists `app_launcher_home_page_indicator` ("Page bar") as a Home row (`SettingsEditorDoors.java:101`, `SettingsTree.java:340`), but the Home card has no such row (`HomeCard.java:26-34`); the Status bar card has "Page bar" (`STATUS_PAGE_BAR`, key `page_bar_look`). `HomeSettings.KEY_PAGE_INDICATOR` is still read (`TermuxActivity.java:29676`), reset (`SurfaceEditorController.java:1718`) and snapshotted (`EditorSettingsSnapshot.java:36`).
3. `Row` enum entries with handling in the controller but listed on no card: DESIGN, CLOCK_12H, ICON_PACK_PAGE, GLASS_SECTION, STATUS_OPTIONS (no refs outside the enum), DOCK_STYLE, CHANGE_WALLPAPER and about 20 `SECTION_*`/`HINT_*` entries (`EditorRows.java:79-282`; counted by grep of `Row.<NAME>` in `surfaces/cards/`).
4. `SurfaceEditorRows` (`surfaces/SurfaceEditorRows.java`) still tables per-slot Tint, Grain, Corner and Edge rows although ADR 0031 removed them from the cards; it is used by `AppearanceSnapshot` and `SurfacePresets`.
5. ADR 0031 vs code: surfaces may own only Opacity/Blur (ADR) while `LookGrid` accepts own cells for eight properties (`LookGrid.java:61-97`). AGENTS.md says the Appearance editor commits only on Done; the app-wide choices listed in `EditorSettingsSnapshot` are written live and put back on Discard (section 2.1).
6. Comment says "The Clear look's numbers" for fresh-install blur 25, opacity 60, grain 7 (`TermuxAppSharedPreferences.java:3402-3410`), but those numbers and the Frost material are Frosted's (`SurfacePresets.java:132-140`); Clear is opacity 10.
7. `DEFAULT_SURFACE_GLOBAL_CORNER_RADIUS = 24` (Constants:1856) while AGENTS.md says the stored -1 means follow-the-style; the editor writes 0..40 only (`AppearanceLooks.java:279`). The Dock card Reset writes Global Corners/Margin (`EditorCardReset.java`).
8. `AppearanceReset` leaves Home's rows and the editor's app-wide switches (section 2.4) although its class comment says everything the editor owns is reset (`AppearanceReset.java:30-33`).
9. Dock type default: `DockStyle` javadoc names GLASS_TRAY the default while `NEW_INSTALL_DEFAULT` is PEEK_GLASS (`DockStyle.java:29-62`, per the layout scan; missing-key fallback not read).
10. `DEFAULT_*` constant defaults vs XML defaults were not compared for every key; XML `defaultValue` for `ListPreference`s without one (gesture rows, `x11_start_with`) is supplied in code.
11. Two settings called Dock style: registry `dock.style` = `app_launcher_dock_style` (docked/floating) vs layout `dock_design` (glass_tray, peek_glass, ...).
12. Several `persistent` keys reference only icon tables and the settings tree (`x11_desktops`, `x11_apps`, `keyboard_credits_text`, `in_app_keyboard_color_scheme_editor`): they are doors/labels, not stored values.

## 6. Not resolved

- Rows of the code-built Settings screens (section 1.2) and the pages' per-key range for `SliderPreference`s beyond XML.
- Whether the "Other control surfaces" scan for menus, drawer edit mode, tour/setup and quick toggles (3.4) arrived: see 3.4.
- Exact default of the dock design and of several layout fields (marked unclear in 3.1).
