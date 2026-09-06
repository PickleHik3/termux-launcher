# Getting started

This guide takes a new installation from APK selection to a usable launcher and terminal. It applies
to Termux Launcher v0.2.31.

## 1. Choose the correct edition

Termux Launcher is published in two Android package families. Pick one family and keep its app and
add-ons together.

| Edition | Android package | Use it when |
|---|---|---|
| Standard | `com.termux` | You want Termux Launcher to replace a standard Termux installation and use matching `com.termux` add-ons. |
| VAJ | `io.vaj.tl` | You want Termux Launcher installed beside standard Termux. Use VAJ-compatible add-ons. |

Open the [GitHub releases page](https://github.com/PickleHik3/termux-launcher/releases), then choose
the release tag without `-vaj` for the standard edition or the matching `-vaj` tag for VAJ. Most
current phones use the `arm64-v8a` APK. Choose `universal` if you do not know the device architecture
and that asset is available.

Do not install one edition over the other. Android treats them as different apps, with different
private Termux home directories. An APK signed by a different Termux distribution also cannot update
the installed app in place.

## 2. Install or update

Download the APK from the selected GitHub release and open it from Android's download notification or
file manager. Android may ask the browser or file manager for permission to install unknown apps.
Grant it only for the installation source you chose, then turn it off again if you prefer.

For an update, install an APK from the same package/signing family. A normal in-place update preserves
the Termux home directory, installed packages, settings, and saved workspace files. The update stops
the app, so finish or save work in running shells first. Never uninstall the app as an update step
unless you have backed up everything you need: Android removes the app's private data when it is
uninstalled.

## 3. Complete the first-launch tour

The first open shows three skippable pages made from real launcher footage:

1. update packages and request shared-storage access;
2. find Android apps from the dock or terminal search; and
3. use terminal windows, status widgets, and persistent workspaces.

Run the first command after the Termux bootstrap has finished:

```sh
pkg update && pkg upgrade
```

Run the storage command only if shell programs need files in Android shared storage:

```sh
termux-setup-storage
```

Approve Android's files prompt. The command creates familiar storage links under `~/storage`.

Finishing or skipping the v0.2.31 tour records it as complete so it does not appear on every launch.

## 4. Make it the Home app

Open **Settings → Apps → Set as default launcher**, then select Termux Launcher in
Android's default Home app screen.

You can open the app settings in either of these ways:

- long-press inside the terminal and choose **Settings**; or
- long-press the Termux Launcher icon in another launcher and choose its **Settings** shortcut.

Keeping **Show in Recents when not the default launcher** enabled is useful while testing the app
before making it the default Home app.

## 5. Learn the three places and the four surfaces

The home screen is three places side by side: **Widgets** on the left, the **Terminal** in the
middle and a Linux **Display** on the right. Swipe left or right on the status bar to move between
them, or tap the place icon peeking in from the bar's edge. The launcher opens on the place you
left it on.

From top to bottom in portrait, on the terminal:

- **Status row:** the place icon and clock, session number, terminal windows, CPU, memory, and
  weather.
- **Terminal:** the focused Termux shell or the current pane layout.
- **Dock:** pinned Android apps, the A–Z app index, and the terminal action row.
- **Keyboard:** the built-in terminal keyboard by default on a fresh installation.

In landscape, pinned apps become a vertical rail on the left. The status row, terminal, action row,
and keyboard stay to its right, clear of the display cutout.

See [Using Termux Launcher](Launcher_Usage.md) for gestures, panes, windows, the command palette, and
workspaces.

## 6. Try the essential actions

- Tap a pinned app to launch it.
- Scrub across the A–Z row to browse installed apps.
- At an idle shell prompt, type `%settings` to filter the dock for apps matching “settings”. `%` is
  the default prefix and can be changed under **Settings → Apps → App search prefix**.
- Tap `+` in the top row to create another terminal window.
- Swipe right on the status bar for your widgets, left for the Linux display. The display is off
  until you tap **Turn on** there; [The Linux display](X11_Display.md) takes it from there.
- Open the command palette by long-pressing the terminal and choosing **Command palette**. With a
  hardware keyboard, use `Ctrl+Alt+Shift+P`.
- Search the palette for `split`, `workspace`, `font`, or `settings` instead of memorizing every
  shortcut.

## 7. Choose the keyboard behavior

Fresh installations use the built-in terminal keyboard. Open **Settings → Keyboard →
On-screen keyboard** to choose:

- **Built-in terminal keyboard**;
- the Android on-screen keyboard; or
- no on-screen keyboard.

The keyboard height is remembered separately in portrait and landscape. Landscape initially follows
the portrait value until you adjust it there.

## 8. Optional components

None of these are required for the basic launcher and terminal:

- **Termux:API:** install the matching package/signing-family build when shell scripts need Android
  APIs.
- **Termux:Styling:** still works with `~/.termux/font.ttf`; the launcher's own font picker is under
  **Settings → Look → Terminal fonts**.
- **Shizuku:** optional privileged backend for features such as the Shizuku lock method. Normal
  launching, panes, workspaces, and the terminal do not need it.
- **TAI / Termux AI:** optional local model host under **Settings → Services & permissions → TAI ·
  Termux AI**. See the [TAI user guide](Termux_AI.md).
- **`tlstore`:** the launcher's own tool store. `tlstore shell` installs fish with a wallpaper-matched
  prompt, `eza` and `zoxide`; `tlstore install` opens a picker for everything else — a Neovim colour
  scheme that follows your wallpaper, the showcase binaries (sigye, fastfetch, kitten), and Claude
  Code. See the [tlstore guide](Tlstore.md).

## 9. Your first recovery commands

Reload user configuration after changing files under `~/.termux`:

```sh
termux-reload-settings
```

If packages are partly upgraded or commands cannot be found, refresh and finish the package upgrade:

```sh
pkg update
pkg upgrade
```

For installation, input, layout, permission, or service problems, continue with
[Troubleshooting](Launcher_Troubleshooting.md).
