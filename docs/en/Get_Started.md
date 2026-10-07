# Get started

This page takes you from choosing an APK to a working launcher and terminal. When you are done,
[Learn the launcher](Learn_The_Launcher.md) shows you how to move around.

## Choose the edition

Termux Launcher ships in three editions. They run the same launcher; they differ in Android package
name and in the package manager the shell uses. Pick one and keep its add-ons from the same family.

| Edition | Android package | Use it when |
|---|---|---|
| Termux (recommended) | `com.termux` | You want the official Termux package repositories. It replaces a standard Termux install. |
| Nix | `com.termux.launcher.nix` | You want it beside standard Termux, with packages from Nix. See [Nix: beginner's guide](Nix_Getting_Started.md). |
| VAJ (demo) | `io.vaj.tl` | You want it beside standard Termux with `pkg`, from a small APT repository that carries few packages. |

Open the [GitHub releases page](https://github.com/PickleHik3/termux-launcher/releases). The Termux
edition is tagged `vX.Y.Z`. From v1.0.0 the other two are tagged `nix-vX.Y.Z` and `vaj-vX.Y.Z`;
older releases used `vX.Y.Z-nix` and `vX.Y.Z-vaj`. Most phones need the `arm64-v8a` APK. Choose
`universal` if you do not know your phone's architecture.

Do not install one edition over another. Android treats them as different apps with separate
home directories, and an APK signed by a different Termux distribution cannot update the
installed app in place.

Add-ons (Termux:API, Termux:Styling, Termux:Boot) must be the matching builds from this project's
forks. The F-Droid add-ons do not pair with any edition.

## Install or update

- Download the APK from the release and open it from Android's download notification or a file
  manager.
- If Android asks, allow that browser or file manager to install unknown apps. You can turn the
  permission off again afterwards.
- To update, install a newer APK of the same edition over the old one. Your home directory,
  packages, settings and saved workspaces stay.

An update stops the app, so save work in running shells first. Never uninstall as an update step
unless you have a backup: Android deletes the app's private data on uninstall.

## First launch

1. The Termux bootstrap installs. Wait for it to finish.
2. A **Before you start** card lists what the launcher can use, one row each, with an **Allow**
   button on each row:
   - **Wallpaper**: lets the launcher colour itself after your wallpaper.
   - **Weather**: shows the weather for where you are in the status bar. This row appears only
     while the weather card is on.
   - **Linux display**: runs graphical Linux apps beside the terminal. This row appears only in
     builds that have the display.

   Allow what you want, then tap **Continue**. Everything here can be changed later in Settings.
3. A welcome card offers **Take the tour** or **Not now**. The tour runs over the real home screen;
   [Learn the launcher](Learn_The_Launcher.md#the-tour) lists its steps.
4. After the tour, an On-device AI card, **What runs on this phone**, shows what your phone can run
   offline and offers downloads. Tick what you want and tap **Download selected**, or tap **Later**.
   You can reopen it from **Settings → On-device AI → What runs on this phone**. See
   [On-device AI](On_Device_AI.md).

Then run these in your first terminal:

```sh
pkg update && pkg upgrade
```

```sh
termux-setup-storage
```

The first refreshes the Termux packages. The second asks Android for shared-storage access and
creates links to your shared folders under `~/storage`. Skip it if your tools do not need your
shared files.

## Choose how you will use it

The first card of the tour asks **How will you use it?** The same choice is the **Launcher mode**
row at the top of Settings:

- **Terminal**: just the terminal.
- **Terminal + Home**: apps and widgets as well.
- **Terminal + Home + Display**: also run Linux desktop apps.

A mode is a preset, not a lock. It sets the pinned apps row, the A–Z index, the app drawer, the
widget page, the Linux display switch and whether the app stays in Recents. Each of those can still
be changed on its own page; once one differs from the preset, the row reads **Custom**. Picking
**Terminal** while the launcher is your home app offers **Choose another home app**; nothing is
forced.

## Make it the home app

- Open **Settings → Apps → Set as default launcher**, then pick Termux Launcher in Android's
  default home app screen. The tour also asks **Use Termux as your home screen?** near its end.
- To open Settings, hold a corner of the terminal and tap the cog, or long-press the terminal and
  choose **Settings**. From another launcher, long-press the Termux Launcher icon and choose its
  **Settings** shortcut.

While you are trying the launcher out, **Settings → App behavior → Show in Recents when not the
default launcher** keeps it in Recents when another home app is active. It is on by default.

## First recovery commands

After you change files under `~/.termux`, reload them:

```sh
termux-reload-settings
```

If packages are half upgraded or commands are missing, finish the upgrade:

```sh
pkg update
pkg upgrade
```

For installation, input, layout, permission or service problems, see
[Troubleshooting](Launcher_Troubleshooting.md).
