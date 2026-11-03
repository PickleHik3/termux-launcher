# Octopi Launcher settings and navigation audit

Observed on Pong over Tailscale ADB on 19 October 2026. App: `com.otp.octopilauncher`, Octopi Launcher **Pro v1.936, build 2377**. Device: Nothing A065, Android 16/API 36, arm64-v8a, 1080 × 2412, density 420.

This is a device-observed inventory of the home long-press menu, all six main settings sections, and their observed submenus. Values below are this installation's values, **not factory defaults**. Controls were inspected without applying layout, wallpaper, icon-pack, or font changes. Orientation and page locking were briefly exercised and restored. Notification access, default home, and OS settings were not changed. No backup was saved or restored. Termux was restored to the foreground and the Pong lease released.

## Coverage and evidence

- All six main settings sections were opened and their scrollable setting lists inspected.
- Home's grid, icon/folder, gesture, and transition editors; Dock's two tabs; Drawer dropdowns; Theme pickers and wallpaper generator; General's information, licenses, language, backup, and restore flows were opened.
- Notification badges require notification access before their detailed controls become available. Access was not granted. Those controls are described only where the app's own release notes identify them.
- Actions that add/delete/rearrange/reset content, restart the launcher, clear data/cache, apply changes, send email, open community/store links, or grant permissions were identified but not completed.
- Font, license, widget, app, and shortcut catalogs are described as catalogs, with observed examples/providers. Their individual records and third-party configuration screens were not exhaustively opened. Alternate settings revealed only by changing modes (including legacy dock and horizontal drawer) remain unverified.
- Slider endpoints, step sizes, and unspecified numeric limits were not probed by changing values. The home-grid row picker was seen from 2 through 19 with more below the viewport; its final maximum was not confirmed.
- The transition list was observed through Flip Card; its lower end was not confirmed. Later attempts to reopen this editor did not change the displayed settings tree. No conclusion about an app defect is drawn from that automation result.

Screenshots:

- [Home long-press menu](octopi-settings-assets/home-menu.jpg).
- [Wallpaper generator and Settings panel](octopi-settings-assets/generator-colors.jpg).
- [Wallpaper generator Presets strip](octopi-settings-assets/generator-presets.jpg).

The UI hierarchy supplied most labels and values. Screenshots confirmed the home menu and the generator's visual palette strip, which has no useful text in the hierarchy. Some long explanatory labels are truncated by the accessibility output; descriptions here summarize only the visible portion. This is a navigation audit, not a visual-quality or performance assessment.

## Entry point and common navigation

Launch Octopi, then long-press an empty home-screen area. This opens a rounded bottom panel over the wallpaper. The top identifies Octopi Launcher Pro and has a Supporter button. A two-column grid below contains page/content commands and launcher commands.

`Launcher settings` opens a floating settings panel with app name, Pro marker, version, and a top-right Close button. Its landing page has Search settings followed by Home, Dock, Drawer, Notifications, Theme, and General. Main sections normally share an icon navigation strip at the bottom. Several deeper editors instead use their own tabs, previews, and confirmation button.

Use an explicit in-panel Back row where available. Android Back and dialog dismissal frequently close the entire panel and return to Octopi home rather than returning to the previous settings page. External Android screens can also return to another Android task. Relaunching Octopi and using the empty-home long press is the reliable re-entry path. Inspect the screen after dismissal before sending another coordinate tap.

### Settings landing page

| Entry | Subtitle / contents | Destination |
|---|---|---|
| Search settings | Search across settings | Search panel with Up control and text field |
| Home | Layout, grid, transitions, gestures and system bars | Scrollable Home settings |
| Dock | Dock style | Dedicated panel editor |
| Drawer | Drawer layout and search | Scrollable Drawer settings |
| Notifications | Badges and notification access | Badge toggle and permission entry |
| Theme | Icon packs, themes, wallpapers and fonts | Scrollable Theme settings |
| General | Haptics, animations, language, backup, and advanced settings | Scrollable General settings |
| Close | Top-right × | Dismiss settings |

Search is more than a category index: typing `folder` displayed the Home screen icon and folder appearance navigation entry alongside related controls, including icon scale and folder-content labels. Selecting the appearance entry opened that editor. The search field has an Up control.

## Home long-press menu

| Button | Observed flow / items | Execution boundary |
|---|---|---|
| Supporter | Pro thank-you/features screen; Rate Octopi Launcher; Looks good! | Read, no rating/store action |
| Add a page | Direct page-add action; also offered in page overview | Not executed |
| Edit pages | Page overview containing Delete, Set as default page, Add a page | Opened; no page mutation |
| Add a widget | Searchable installed-provider list; provider rows expand widget previews | Opened; no placement |
| Add a shortcut | Actions and Shortcuts tabs | Both inspected; no placement |
| Orientation | Direct toggle: Auto-rotate → Locked (portrait) → Auto-rotate | Both states observed; original restored |
| Lock home page | Replaces editing menu with Home Page Locked screen | Lock and unlock observed; original restored |
| Launcher settings | Six-section settings landing page | Inspected |
| System wallpaper | Android wallpaper-app resolver | Opened; canceled |
| Set as default home app | Android home-app chooser | Opened; canceled |

### Lock and unlock

After locking, long-pressing home shows `Home Page Locked`, an unlabeled long-clickable icon, and `Long press above icon to unlock`. The actual unlock target is the icon **below the heading**, approximately (541, 320) at Pong's inspected size, not the small icon next to the heading. Long-pressing it unlocks the page and returns home. Reopening the normal long-press menu confirmed `Lock home page` was available again.

### Page overview

Edit pages exposes Delete above the page preview, Set as default page below it, and Add a page at the bottom. Destructive/default-page actions were not invoked. Page reordering and behavior with multiple home pages were not verified.

### System wallpaper and default home

System wallpaper opened a resolver headed `Complete action using Nothing Wallpaper`, with Just once and Always and alternatives including Backdrops, Live Wallpaper Picker, Photos, Sticlo Icons, and Styles & wallpapers. These options depend on installed apps.

Set as default home app opened `Set Octopi Launcher as your default home app?`, listing Blue Line Console, Nothing Launcher, Octopi Launcher, and Termux:Launcher. Termux:Launcher was marked Current default. Cancel and Set as default were present; Set as default was disabled before a selection. No selection was changed.

### Supporter / Pro information

The thank-you screen lists:

- Scroll widgets in a loop; scroll widget stacks vertically.
- More home-page transition effects.
- Actions for three/four-finger gestures and tap-and-swipe gestures.
- Nested drawer folders.
- More wallpaper-generator styles.
- Tint for frosted widgets.
- Drawer icon drop shadows.
- More fonts.
- Quick positioning/resizing of widgets.
- Support development.

The bottom offers Rate Octopi Launcher and Looks good!. This installation already has Pro, so a purchase flow was not available for inspection.

## Home settings

| Entry | Control / destination | Observed value |
|---|---|---|
| Freely position and resize items | Switch; fine positioning of items | Off |
| Home screen grid, size and spacing | Submenu | See below |
| Home screen icon and folder appearance | Submenu with preview | See below |
| Page transition effect | Full-screen preview and transition picker; Apply | Stack |
| Customise gestures | Per-screen gesture assignments | See below |
| Hide status bar | Switch | Off |
| Hide navigation bar | Switch | Off |
| Swipe up/down on folders to open them | Switch | Off |
| Pan wallpaper when scrolling through home pages | Switch | On |
| Wallpaper position | Dropdown, for a single home-screen page | Left |
| Scroll home pages in a loop | Switch; description says side-swipe gestures and wallpaper panning are disabled in this mode | Off |
| Page indicator | Dropdown | Dots |

Wallpaper position choices: **Left, Center, Right**. Page indicator choices: **Dots, Off**.

### Home → grid, size and spacing

An in-panel Back row returns from this editor. A warning says icon repositioning may be needed after changes.

| Entry | Control | Observed value / options |
|---|---|---|
| Home screen icons per row | Numeric dropdown | 5; options 3–20 confirmed |
| Home screen icon rows | Numeric dropdown | 6; recommends six rows; options starting at 2, at least through 19 observed |
| Home screen horizontal padding | Slider | 30 |
| Home screen vertical padding | Slider | 0 |

The columns description warns that changing it may resize existing items. Padding units and slider limits were not established from the hierarchy.

### Home → icon and folder appearance

The top has an example icon and a long label preview, followed by a scrollable list and an in-panel Back row.

| Entry | Control | Observed value / options |
|---|---|---|
| Home screen icon scale | Slider | 75% |
| Home screen icon labels | Switch; warns existing icons may resize | On |
| Home screen folder content labels | Switch | On |
| Home screen icon label scale | Slider | 100% |
| Show home screen labels in two lines | Switch | On |
| Invert home screen icon label color | Switch | Off |
| Folder spacing/density | Dropdown | Default; Squeezed, Compact, Default, Comfortable, Expanded |
| Max icons per row in home screen folder | Dropdown; also limited by screen width | 4; choices 1–5 |
| Max rows in home screen folder | Dropdown; also limited by screen height | 4; choices 1–12 |

### Home → page transition effect

The preview has the current effect name, a Select Transition control, and Apply. The inspected current name was STACK. The picker exposed these names in order:

Slide, Crossfade, Accordion, Zoom Out, Zoom In, Carousel, Stack, Cube, Cube Spaced, Carousel 3D, Shutter, Wheel, Pop Up, Rotate, Rotated Cards, Deck, Flip, Flip Card.

Flip Card was partially visible at the lower edge. The list's final endpoint and any further entries are unconfirmed. No effect was selected or applied. Drawer has a separate Page transition effect entry for horizontal drawer pages; its editor was not independently inspected.

### Home → Customise gestures

The editor explicitly states that gesture settings are specific to each individual screen. Each row opens an action picker; Back is in the panel.

| Gesture | Observed assignment |
|---|---|
| Swipe up | Open app drawer |
| Swipe down | Notifications |
| Swipe right from first screen | No assignment subtitle shown |
| Swipe left from last screen | No assignment subtitle shown |
| Double tap | Turn screen off |
| Navigate/tap home | No assignment subtitle shown |
| Tap and swipe up | Do nothing |
| Tap and swipe down | Do nothing |
| Swipe up with two fingers | Do nothing |
| Swipe down with two fingers | Open search |
| Swipe up with three fingers | Do nothing |
| Swipe down with three fingers | Do nothing |
| Swipe up with four fingers | Do nothing |
| Swipe down with four fingers | Do nothing |

The action picker has **Actions, Apps, Shortcuts** tabs. Actions observed:

- Do nothing.
- Open app drawer; Open search; Go to default page; Toggle dock.
- Launch Google app; Launch assistant.
- Octopi Launcher settings; Octopi Launcher settings menu.
- Turn screen off; Notification panel; Show quick settings screen; Launch recents screen.

The action picker was opened for Swipe up; assignments were not changed. The Apps and Shortcuts tabs were identified, but their gesture-specific catalogs were not separately traversed. The home Add a shortcut picker uses the same named actions except Do nothing and has Actions/Shortcuts tabs rather than Apps.

## Dock editor

Dock opens a dedicated panel with **Style** and **Settings** tabs, a live Preview area, and OK. This is not another long scroll of preference rows. Back dismissed it without using OK.

### Style tab

Panel background choices: **Classic dock, Rounded corners, None**. Below them are **Opacity** and **Corners** adjustments, then Preview. Their numeric values/endpoints were not exposed in the inspected text tree, and style choices were not changed.

### Settings tab

| Entry | Control | Observed value |
|---|---|---|
| Pages | Minus / value / plus | 1 |
| Icon scale | Minus / value / plus | 75% |
| Icons across | Minus / value / plus | 5 |
| Rows | Minus / value / plus | 1 |
| Extra rows | Minus / value / plus; tap panel to show extra rows | 0 |
| Dock position | Minus / plus | Numeric value not exposed |
| Show icon labels inside folders | Checkbox | Off |

Preview remains below these controls; Style, Settings, and OK remain available. Step sizes, limits, hidden extra-row behavior, and alternate dock styles were not changed/tested.

## Drawer settings

| Entry | Control | Observed value / choices |
|---|---|---|
| Drawer Style | Dropdown | Vertical scroll; choices Vertical scroll, Horizontal pages |
| Page transition effect | Submenu for horizontal-page transitions | Listed; not independently opened |
| Remember drawer position | Switch; don't reset scroll position | Off |
| Move drawer items down for easier reach | Switch; start items in lower half | Off |
| Show app sorting and menu bar | Switch | On |
| Tag bar style | Dropdown | Chip; choices Chip, Text |
| Show tags in multiple lines | Switch | Off |
| Show all apps | Switch; includes hidden apps in drawer | Off |
| A–Z index bar position | Dropdown | Right; choices Off, Left, Right |
| Search button style | Dropdown | Button (right); choices Off, Button (left), Button (right) |
| Start search when opening drawer | Switch; automatically show keyboard | Off |
| Turbo search | Switch; launch app if it is the sole matching result | Off |
| Show hidden apps in search | Switch | Off |
| Drawer background opacity | Slider | 50% |
| Drawer items per row | Numeric dropdown | 4; range not opened |
| Drawer icon scale | Slider | 100% |
| Drawer icon labels | Switch | On |
| Drawer icon label scale | Slider | 100% |
| Show drawer labels in two lines | Switch | On |
| Drawer icon label backgrounds | Switch; improve visibility | Off |
| Show shadows under drawer icons | Switch; works best with light backgrounds | Off |
| Invert drawer icon label and background colors | Switch | Off |
| Drawer folder style | Dropdown | Normal; choices Normal, Grid |
| Sort apps by pinyin | Switch | Off |

The bottom was reached: additional scrolling repeated the final controls. The actual drawer was briefly observed with Name, Installed, and Updated sorting choices, an Icon type/menu control, app grid, and right-side letter index. The drawer's own item/context menus are outside this settings audit.

## Notifications settings

| Entry | Flow | Observed state |
|---|---|---|
| Notification badges | Switch; when enabled without access, opens Notification Permission Required | Off |
| Launch notification permission screen | Android notification read/reply/control page for Octopi | Access not allowed |

The permission-required dialog explains that read-notification permission is needed and offers **Cancel** and **Grant permission**. Cancel was used. Its dismissal returned home. No permission was granted.

The Android permission page has Allow notification access (off), with notification categories such as Real-time, Conversations, Notifications, and Silent disabled beneath it. These are Android controls, not Octopi submenus.

The app's What's New screen states that v1.936 adds count/number versus simple-dot badges, notification-icon badges (Pro), and notification preview/dismissal in icon menus (Pro). Their exact controls, choices, and dependent flows **could not be device-inspected without granting access**. This report does not claim completeness for that conditional branch.

## Theme settings

| Entry | Control / flow | Observed value / choices |
|---|---|---|
| Default icon shape | Dropdown; requires reapplying icon pack/default icons | Default; choices Default, Circle, Squircle, Rounded square, Square |
| Adjust icon pack icons to similar size | Switch; resize to consistent size | Off |
| Theme | Dropdown | Based on wallpaper; choices Based on system, Based on wallpaper, Always use dark theme, Always use light theme |
| Notification bar icon color | Dropdown | Based on wallpaper; choices Based on system, Based on wallpaper, Dark, Light |
| Change wallpaper | Pick a new wallpaper | An attempt dismissed settings to home without exposing a picker; destination not confirmed |
| Generate wallpaper | Full-screen generator | See below |
| Apply icon pack | Installed-pack picker with target checkboxes | See below |
| Font | Searchable font-preview catalog | Default |

The icon-shape explanatory text notes that icon packs/apps may constrain the result; its full trailing wording was truncated in the hierarchy.

### Theme → Apply icon pack

Installed options shown: **Default** and **Sticlo Icons**. An Apply to row has independent **Drawer, Dock, Home** checkboxes, all checked when opened. **Overwrite custom icons** was unchecked. **Search icon packs** is available below. No pack was selected, no overwrite requested, and the external search was not launched.

### Theme → Font

A scrollable two-column catalog renders names in the corresponding face. The bottom has **Search**, a **Clear** control, and **OK**. Default was the current setting. Examples at the start included ABeeZee, Abel, Abhaya Libre, Aboreto, Abril Fatface, Abyssinica SIL, Aclonica, Acme, Actor, Adamina, ADLaM Display, Advent Pro, Afacad, Afacad Flux, Agbalumo, Agdasima, Agu Display, Aguafina Script, Akatab, Akaya Kanadaka, Akaya Telivigala, Akronim, Akshar, Akt, Aladin, Alan Sans, Alata, Alatsi, and Albert Sans. The catalog was not enumerated to its end, and no font was selected/confirmed.

### Theme → Generate wallpaper

The full-screen wallpaper preview has a top-right Close and a floating translucent toolbar. Panels/strips can remain open together: the Style or Presets strip can sit above the toolbar while Settings remains below it.

| Control | Observed content / flow | Boundary |
|---|---|---|
| Style | Horizontally scrollable named style previews | Full named strip traversed |
| Presets | Horizontally arranged palette-card thumbnails; no textual names in hierarchy | Screenshot inspected; individual palettes not selected |
| Colors | Palette-related control with a visual color strip | No text submenu appeared; exact editing behavior not established |
| Create | Generation action | Button identified; not executed |
| Apply | Apply generated wallpaper | Not executed |
| Save | Save/export generated wallpaper | Not executed; destination unverified |
| Settings | Expands three switches and palette strip | Opened, values retained |
| Close | Returns to home | Used |

Style names, through the confirmed end of the horizontal strip:

1. Soft Waves
2. Sharp Waves
3. Water Paint
4. Mesh Gradient
5. Fluted Glass
6. Glowing Curves
7. Triangles
8. Mosaic
9. Vertical Bars
10. Sheets
11. Curved Lines

Settings switches: **Recommended palette** on; **Larger wallpaper** off; **Set Octopi Launcher wallpaper** off. The Settings panel's palette strip and Presets thumbnails are visual controls absent from the textual hierarchy. Opening the generator produced its own preview; no Apply or Save action was used, so the installed home wallpaper was retained.

## General settings

| Entry | Control / intended flow | Observed state / verification |
|---|---|---|
| Animation duration | Slider with Normal indicator | Normal; not adjusted |
| Extra haptic effects | Switch; additional tap haptics | On |
| Cascading swipe home animation | Switch | Off |
| Screen off effect | Dropdown | Default; choices Default, CRT, Dissolve |
| Octopi Launcher v1.936 [build 2377] | What's New | Opened |
| Octopi Launcher Pro… | Pro features information | Entry identified; Supporter version of this information opened |
| Select app language | Android app-language picker | Opened; System default retained |
| r/OctopiLauncher Reddit page | Community/help link | Not launched |
| Send me an email | Support/contact action | Not launched; no message composed/sent |
| Big thanks to the following translators | Attribution entry with names | Read in list; any tap destination unverified |
| Set as default home app | Android home chooser | Same named action inspected from home menu |
| Licenses | Expandable dependency/license catalog | Opened; sample expansion inspected |
| Backup settings | Android Save dialog | Opened; canceled without saving |
| Restore settings | Android file picker | Opened; canceled without selecting |
| Enable legacy dock | Switch | Off; alternate mode not entered |

### What's New

Dedicated screen headed What's New and Current Version: v1.936 [2377]. Latest release shown: **16 Oct 2026**. Changes are grouped into NEW and IMPROVED with PRO markers where relevant. Bottom buttons: **Show previous changes** and **OK**. Historical expansion was not opened.

The latest notes mention notification options and the new main settings page, direct dock settings access, Dissolve screen-off effect, folder density, single-page wallpaper position, folder rearrangement, touch handling during screen-off animation, and widget scrolling detection. These are the app's own descriptions, not independent behavior verification.

### Language

Android's App language screen has a search control, Suggested section, System default, and All languages. Suggested entries on this phone included English (United States), Spanish (United States), and Arabic (Kuwait); Catalan was visible at the start of All languages. Suggestions depend on the phone. No locale was changed, and the entire language catalog was not copied.

### Licenses

Scrollable library rows show name, version, author, and license. The first inspected row was AboutLibraries Compose Material 3 Library 15.2.0, Mike Penz, Apache License 2.0. Expanding it revealed a description and **Source, Website, View license** buttons. Other visible examples included AboutLibraries UI/Core, Accompanist Drawable Painter, Activity/Activity Compose/Kotlin Extensions, Aho-CoraSick, and Android startup/architecture libraries. **OK** closes the screen. No external source/website link or full license text was opened; the complete dependency catalog was not reproduced.

### Backup and restore

Backup settings opens Android DocumentsUI in create/save mode. The prefilled filename follows `OtpLauncher_YYYYMMDD_HHMMSS.zip` (observed `OtpLauncher_20261019_115106.zip`). It provides destination browsing, New folder, view/menu controls, editable filename, and SAVE. Canceled before SAVE.

Restore settings opens DocumentsUI in open/select mode, initially Recent with filters, search, roots, other providers, and a file list. No backup file was selected, so validation, import confirmation, and post-import behavior remain unverified. Personal file/provider/account contents are deliberately omitted here.

### General → experimental/advanced area

This is a lower portion of General, introduced by an experimental-options warning, not a separate main section.

| Entry | Visible purpose / control | Observed value / boundary |
|---|---|---|
| Set Octopi Launcher wallpaper | Separate wallpaper for Octopi; enables additional functionality described in truncated text | Not executed |
| Clear Octopi Launcher wallpaper | Revert to system wallpaper | Not executed |
| Restart launcher | Restart action | Not executed |
| Clear cache and restart | Cache-clear and restart action | Cache label 2.9 Mb; not executed |
| Realign icons | Try to snap icons to grid; warns icons may overlap | Not executed |
| Reset current screen and clear all its home pages | Reset action; recommends backup first | Not executed |
| Scale screens | Switch for layouts appearing smaller/bigger | Off |
| Rescale items | Rescale-all-items action | Not executed |
| 0 unused screen configurations | Count/action row | Zero; any deeper flow not opened |

Scrolling to the end exposed the last row above. No reset/realignment confirmation dialogs were opened, because these entries could act immediately.

## Widget and shortcut picker details

### Add a widget

The picker has a searchable, vertically scrolling provider list. Each row includes the provider name and widget count. Tapping Clock expanded inline previews, including **Analog — 4 × 4** and **Digital — 4 × 4**. No preview was selected, so placement, binding permission, resize, and provider configuration were not exercised.

Observed provider inventory (count in parentheses):

- BBS Reloaded (1), Blue Line Console (1), Calendar (2), ChatGPT (1), Chrome (4), Claude (1), Clock (5), Colorgen Widgets (26), Contacts (4), Cromite (4).
- Digital Wellbeing (1), Drive (2), EarFun Audio (1), Firefox (1), Gemini (1), GitHub (1), Google (5), Google Play Store (2), Google TV (1), Home (1).
- Immich (2), Keep Notes (3), Keep Screen On (1), Komikku (1), Kustom Widget (6), Maps (2), Neutron Player (5), Nextcloud (1), Nextcloud Notes (2), Outlook (2), Photos (2).
- Proton VPN (4), ShizuWall (2), Snapchat (2), SpotiFLAC Mobile (2), System UI (1), Telegram (2), Translate (7), VLC (1), WhatsApp (1), YT Music Morphe (3), YouTube Morphe (2).

The lower endpoint was reached. The upper capture began at BBS Reloaded after an upward return in the list, so providers preceding it are not ruled out. Counts and providers are install-dependent and are not fixed Octopi settings. Individual providers' widget variants were not exhaustively enumerated.

### Add a shortcut

Two tabs: **Actions** and **Shortcuts**. Actions includes Open app drawer, Open search, Go to default page, Toggle dock, Launch Google app, Launch assistant, Octopi Launcher settings, Octopi Launcher settings menu, Turn screen off, Notification panel, Show quick settings screen, and Launch recents screen.

Shortcuts is a searchable provider list (`Search app/shortcut`), with observed providers Contacts, Drive, Google, Kustom Widget, Maps, Octopi Launcher, Outlook, Quick Cursor, Settings, Solid Explorer, System UI, WhatsApp, and Windows App. Tapping Octopi Launcher expanded an inline Octopi Launcher shortcut tile. No tile was selected or placed, and external shortcut configuration was not entered.

## Replaying the inspection

Skill used: [android-control](/home/amalvajdan/Projects/claude-setup/skills/android-control/SKILL.md). Only direct `ad` commands were used; no mobilerun goal or delegated device agent was started.

```bash
A=/home/amalvajdan/.claude/skills/android-control/ad
$A -s pong top
$A -s pong status
$A -s pong lease acquire 'Inventory Octopi Launcher settings'
$A -s pong launch com.otp.octopilauncher
$A -s pong long 540 1206
$A -s pong ui
$A -s pong tap 'Launcher settings'
$A -s pong ui
```

For subsequent entries, the inspection used `tap '<visible label>'`, coordinate taps for dropdown arrows, `swipe up`, explicit-coordinate swipes for long lists/horizontal strips, `ui`, `back`, and `shot`. Take a fresh hierarchy after each dismissal. The bottom settings-strip centers observed on Pong were approximately Dock x272, Drawer x451, Notifications x630, Theme x809, General x988, all y2231; these are screen-specific, and stale coordinates can hit home-screen app icons if the panel closes.

Exact screenshot commands:

```bash
$A -s pong shot /tmp/octopi-home-menu.png
$A -s pong shot /tmp/octopi-audit/wallpaper.png
$A -s pong shot /tmp/octopi-audit/generator-colors.png
$A -s pong shot /tmp/octopi-audit/generator-presets.png
```

The `wallpaper.png` capture showed unchanged Octopi home after the unsuccessful Change wallpaper attempt; it is not included as evidence of a wallpaper picker. JPEG copies linked above preserve the captured UI at the original dimensions. Full ephemeral command/hierarchy transcript: `/tmp/octopi-audit/transcript.txt` (contains incidental personal OS-picker contents; not committed or copied into this report).

Session cleanup:

```bash
$A -s pong launch com.termux
$A -s pong top
$A -s pong lease release
```

Final focus was `com.termux/com.termux.app.TermuxActivity`; lease output was `released pong`. No recording, monitor, logcat follower, or goal agent remained. No logcat capture was needed for this navigation audit.

### Suggested device-map lines for a future Octopi repository

```text
Package com.otp.octopilauncher; activity .MainActivity.
Launch app, long-press empty home (Pong: 540,1206), tap Launcher settings.
Main sections: Home, Dock, Drawer, Notifications, Theme, General.
Read the screen after Back/Cancel/OK: many dialogs dismiss all settings to home.
Dropdown arrows sometimes require a tap at the right edge rather than the value text.
Locked home: long-press empty home, then long-press the unlabeled icon below the
Home Page Locked heading (Pong: 541,320) to unlock.
Notification badge details require notification access; do not grant it for a read-only audit.
Generator Style/Presets strips scroll horizontally; Settings can stay open below them.
```
