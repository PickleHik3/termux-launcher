# Settings map

This page lists every top-level row in Settings, in the order they appear, with what each page
holds and a link to the page that explains it.

Open Settings by holding a corner of the terminal and tapping the cog, by long-pressing the
terminal and choosing **Settings**, from **Settings** in the command palette on Home, or from the
**Settings** shortcut on the app icon in another launcher. **Search settings** at the top finds any
setting inside every page, so `fonts`, `ligatures` or `Shizuku` lead straight to the right place.
Search also finds the settings that live in the Appearance editor (theme, wallpaper, icon pack,
full screen and the rest) and opens the editor for them.

Settings is grouped in three: **Your launcher** (Home, Dock, Terminal, Linux apps),
**Everywhere** (Appearance, Keyboard, Notifications, On-device AI) and **System** (General).

## Your launcher

### Home

"Pages, widgets, gestures and where you start". **Appearance** opens the Appearance editor on
Home. Then **Icons and labels**, **Folders**, **Gestures**, **Home widgets** and **Pages** (**Page indicator**, **Edit pages**, **Wallpaper scrolls with pages**, **Lock Home layout** and **Start in**).

**Gestures** holds every gesture in one place: the five on a Home page's free area (swipe up,
swipe down, double-tap, two-finger swipe up and down), then **Double-tap to lock** (its page picks
the lock method), **Swipe up on app icons for quick reply** (on by default; off, a swipe up on an icon with a
notification dot behaves like on any other icon) and **Swipe on the terminal to switch windows**.

**Start in** is **Home** (the default) or **Terminal**: where the launcher opens after a restart;
**Terminal** opens the Workspace with one fresh shell window. See
[Home screen and apps](Home_Screen_And_Apps.md) and [Widgets](Widgets.md).

### Dock

"Dock, drawer, A–Z and app search": **Pinned apps** and **Most-used apps page** for the dock, then
**Drawer layout** (its page starts with **Grid**, the drawer's view), **App search** and **Linux
apps in the drawer**, which opens the Linux apps section. See
[Home screen and apps](Home_Screen_And_Apps.md).

### Terminal

"Windows, panes, status bar and text". **Appearance** opens the Appearance editor on the
Terminal, then the **Windows & panes**, **Status bar**, **Text & display** (font,
cursor trail, terminal effect) and **Clipboard & compatibility** pages. See
[Panes, windows and sessions](Panes_Windows_And_Sessions.md), [Status bar](Status_Bar.md),
[Terminal fonts](Terminal_Fonts.md) and [Touch, links and clipboard](Touch_Links_And_Clipboard.md).
Whether scripts may open panes is covered on [launcherctl](LauncherCtl.md).

### Linux apps

"Desktop apps, their windows and how they start": **Show Linux apps in drawer**, **Set up Linux
apps**, **Hidden Linux apps**, **Window manager**, then the **Input**, **Resolution and scaling**,
**Startup** and **Fixes** pages. There is nothing to switch on: the display starts when a Linux app
asks for it. Builds made without the display leave this row out. See
[The Linux display](X11_Display.md) and [Linux apps from a distro](Linux_Apps_From_A_Distro.md).

## Everywhere

### Appearance

"Wallpaper, glass, theme, icons and layout". Not a page: the row opens the Appearance editor over
the live launcher, where you change how everything looks and where it sits. Every way into the
editor is called **Appearance**: this row, the Appearance rows on Home, Terminal and Keyboard, and
the corner tab. See [Look and themes](Look_And_Themes.md) and
[Layout and full screen](Layout_And_Full_Screen.md).

Turning **Wallpaper colors** off, or applying a Termux:Styling scheme, builds the launcher's colours
from `~/.termux/colors.properties`. See
[Look and themes](Look_And_Themes.md#theming-from-a-color-scheme).

### Keyboard

"Layouts, size, typing, voice and look": **On-screen keyboard** (**Built-in**, **Android** or
**Off**) and **Voice input**; the built-in keyboard's **Layouts**, **Size and position** and
**Typing and feedback** pages; **Appearance** and **Keyboard theme**; **Terminal extra keys**; and
**Hardware keyboard** in a group of its own. See [Keyboard](Keyboard.md),
[Extra keys](Extra_Keys.md) and [Voice input](Voice_Input.md).

### Notifications

"Dots, pinned alerts and history": **Notification dots**, **Pinned notifications** and
**Notification history**. **Notification access** itself is on General › Permissions & services.
See [Notifications](Notifications.md).

### On-device AI

"Models, voice and assistant": the runtime, the **Model centre**, **Models for this phone**, a
link to **Voice input** (which lives under Keyboard), the benchmark and the local server. See
[On-device AI](On_Device_AI.md).

## System

### General

"Motion, vibration, battery, backup and about": **Animation speed** and **Page transition**;
**Vibration** (the one place for app, launcher and keyboard vibration); **Battery & performance**;
the **Backup & restore**, **Permissions & services**, **Diagnostics** and **About & help** pages;
and **Set as default launcher**, **Restart launcher** and **Show in Recents when not the default
launcher**.

**Battery & performance** holds **Reduce idle activity** (lazy mode) and **Low-memory preset**,
with shortcuts to the motion it trims. See
[Look and themes](Look_And_Themes.md#reduce-idle-activity). **Low-memory preset** is a switch: on,
it sets the Solid look, an opaque terminal, no cursor trail or terminal effect, no wallpaper
parallax and Reduce idle activity; off, it puts back what it replaced.

#### Backup & restore

"Move your setup to another phone": **Export settings** saves your launcher setup to one `.zip`
file (it suggests Downloads), and **Import settings** restores it, on this phone or a new one, on
any edition.

The file holds:

- the launcher's settings: Appearance, Layout, dock, drawer, pinned and hidden apps, status bar,
  keyboard, terminal, display, on-device AI settings, terminal font choices and Shizuku switches;
- your config files in `~/.termux`: `termux.properties`, `colors.properties`, `font.ttf`,
  `font-italic.ttf`, `fonts.conf` and your own files in `fonts.d/`,
  `termux-launcher-bindings.conf`, `keyboard/layout.xml`, `launcher-theme.properties`,
  `app-categories.conf`, `shell`, and the `theme-templates/`, `workspaces/` and `background/`
  folders, plus `~/.config/termux/termux.properties`;
- the keyboard's label font, and the home and lock screen pictures you set from the launcher.

It leaves out your home folder, installed packages, tokens and API keys, usage history, the
clipboard, notification history and widgets. A file over 20 MB is left out of the export.

Importing replaces your current launcher settings and restarts the launcher, which closes open
terminal sessions. Afterwards, add your widgets again, grant the permissions the launcher asks
for, and pick your downloaded terminal font again so it downloads on this phone.

##### AI models

Downloaded on-device AI models are too large for the settings file, so they have their own rows.
**Export AI models** shows how many models you have and how much space they take, and copies them
into a folder you pick (it opens in Documents). It makes a `Termux Launcher models` folder there,
with one folder per model: chat models, speech-to-text, voice output, embedding and image models
alike, each with every file it needs. The copy runs in the background with a notification you can
cancel from; a model already saved there at the same size is skipped, so running it again finishes
an export that was cut short.

**Import AI models** adds them back, on this phone or a new one: pick the `Termux Launcher models`
folder (or one model's folder inside it). Each model returns under its own name with its
settings, so the features that used it pick it up again. A model already on the phone is left as
it is. The same folder import is in the Model centre, under the link bar's menu.

Import your settings first, then your models.

For your home folder and packages, use `termux-backup` and `termux-restore` in the terminal.

#### Permissions & services

"Access and connected services": **Files and media**, **Wallpaper access**, **Notification
access**, **Accessibility service**, **App notifications**, **All app permissions**, then
**Shizuku** and **Termux:API**. An **Action needed** badge is a status, not a failure; fix it only
if you want the feature that needs it. See [Shizuku](Shizuku.md).

#### Diagnostics

"Logs and troubleshooting", the one place for logging: **Log level** (the launcher's),
**Termux:API log level** (shown while Termux:API is installed), **Log keyboard input**, plugin and
crash alerts, **Test privileged backend**, **Copy diagnostics**, **Export logs** and **Clear
logs**. Diagnostics can contain device, package and path details; review them before posting. See
[Troubleshooting](Launcher_Troubleshooting.md).

#### About & help

"Guides and app information": **Help** (the in-app help), **Documentation**, **Discord**,
**Replay tour**, **Send feedback**, **Version and build**, **What's new**, **Open-source
licenses** and **Source code**. When reporting a bug, include the version and edition, Android
version, device architecture and the shortest steps to reproduce it. See
[Learn the launcher](Learn_The_Launcher.md#get-help).
