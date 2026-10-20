# Settings map

This page lists every top-level row in Settings, in the order they appear, with a link to the page
that explains it.

Open Settings by holding a corner of the terminal and tapping the cog, by long-pressing the
terminal and choosing **Settings**, or from the **Settings** shortcut on the app icon in another
launcher. **Search settings** at the top finds any setting inside every page, so `fonts`,
`ligatures` or `Shizuku` lead straight to the right place.

## Launcher mode

The first row: **Terminal**, **Terminal + Home** or **Terminal + Home + Display**. A preset that
sets the apps row, A–Z index, drawer, widget page, Recents behaviour and the Linux display switch;
it reads **Custom** once one of them differs. See
[Get started](Get_Started.md#choose-how-you-will-use-it).

## Appearance

Under **Personalization**. "Wallpaper, theme, fonts and icons": its first row, **Wallpaper, look
and layout**, opens the Appearance editor over the launcher, with tabs **Wallpaper | Look | Layout |
Icon pack**. Below it sit **Terminal fonts**, the app theme, wallpaper colours, terminal contrast,
the wallpaper options, icon options and keyboard look. The palette's **Look and feel settings**
opens the same page. See [Look and themes](Look_And_Themes.md).

### Look

The Look tab of the Appearance editor: the glass look of every surface, the clock face, cursor
trail and terminal effect. See [Look and themes](Look_And_Themes.md).

### Layout editor

The Layout tab of Appearance: where the bars stand, their sizes, the keyboard type and minimal mode.
See [Layout and full screen](Layout_And_Full_Screen.md).

### Theming from a color scheme

Turning **Wallpaper colors** off, or applying a Termux:Styling scheme, builds the launcher's colours
from `~/.termux/colors.properties`. See
[Look and themes](Look_And_Themes.md#theming-from-a-color-scheme).

## Apps

"Dock, drawer and search": **Set as default launcher**, **Pinned apps**, **Swipe down for app
drawer**, **Home widgets**, **Built-in widget style**, and the **Dock**, **Drawer layout**, **App
search** and **Double-tap to lock** pages. See [Home screen and apps](Home_Screen_And_Apps.md) and
[Widgets](Widgets.md).

## Status bar

"Clock, indicators and weather": **12-hour time**, **CPU usage**, **Memory usage**, **Media**,
**Weather**, **Fahrenheit** and the weather **Location**. See [Status bar](Status_Bar.md).

## Notifications

"Dots, pinned alerts and history": **Notification access**, **Notification dots**, **Pinned
notifications** and **Notification history**. See [Notifications](Notifications.md).

## Terminal

Under **Workspace**. "Panes, clipboard and effects": **Full screen**, **Extend edge colors**,
**Cursor trail**, **Terminal effect**, **Keyboard resize workaround**, the clipboard switches, and
**Split-pane controls** with its pane options. See
[Panes, windows and sessions](Panes_Windows_And_Sessions.md),
[Touch, links and clipboard](Touch_Links_And_Clipboard.md) and
[Layout and full screen](Layout_And_Full_Screen.md#full-screen). Whether scripts may open panes is
covered on [launcherctl](LauncherCtl.md).

## Keyboard

"Layouts, typing and voice input": **On-screen keyboard** (**Built-in**, **Android** or **Off**),
**Voice input**, the **Layouts**, **Size and position** and **Typing and feedback** pages,
**Terminal extra keys** and **Hardware keyboard**. See [Keyboard](Keyboard.md),
[Extra keys](Extra_Keys.md) and [Voice input](Voice_Input.md).

## Display

"Desktop apps and touch controls": the **Display** switch for the Linux display, and its input,
resolution, Linux apps, startup and troubleshooting pages. See
[The Linux display](X11_Display.md).

## On-device AI

"Models, voice and local API": the runtime, the **Model centre**, **Models for this phone**,
benchmark and the local server. See [On-device AI](On_Device_AI.md).

## App behavior

Under **App**. "Battery use and vibration": **Reduce idle activity** (lazy mode), the vibration
switches, and **Show in Recents when not the default launcher**. See
[Look and themes](Look_And_Themes.md#reduce-idle-activity). **Low-memory preset** is a switch: on, it sets the Solid look,
an opaque terminal, no cursor trail or terminal effect, no wallpaper parallax and Reduce idle activity;
off, it puts back what it replaced.

## Backup & restore

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

### AI models

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

## Permissions & services

"Access and connected services": **Files and media**, **Wallpaper access**, **Notification
access**, **Accessibility service**, **App notifications**, **All app permissions**, then
**Shizuku** and **Termux:API**. An **Action needed** badge is a status, not a failure; fix it only
if you want the feature that needs it. See [Shizuku](Shizuku.md).

## Diagnostics

"Logs and troubleshooting": **Log level**, **Log keyboard input**, plugin and crash alerts, **Test
privileged backend**, **Copy diagnostics**, **Export logs** and **Clear logs**. Diagnostics can
contain device, package and path details; review them before posting. See
[Troubleshooting](Launcher_Troubleshooting.md).

## About & help

"Guides and app information": **Documentation**, **Replay tour**, **Send feedback**, **Version and
build**, **Open-source licenses**, **Source code** and **Donate**. When reporting a bug, include the
version and edition, Android version, device architecture and the shortest steps to reproduce it.
See [Learn the launcher](Learn_The_Launcher.md#get-help).
