# Permissions and Shizuku

This page lists every permission Termux Launcher asks for and what it uses each one for, then
explains Shizuku, the optional service that lets the launcher read system information Android
normally hides. Both live under **Settings → Permissions & services**.

## Permissions

**Settings → Permissions & services** has two groups. **Permissions**: **Files and media**,
**Wallpaper access**, **Notification access**, **Accessibility service**, **App notifications** and
**All app permissions** (Android's own page). **Connected services**: **Shizuku** and
**Termux:API**. A permission is asked for when a feature first needs it; turn down the ones for
features you do not use.

| Permission | What it is for |
| --- | --- |
| Default home app | Termux Launcher as the home screen. Set it in **Settings → Apps** or Android's settings. |
| Approximate location | The weather on the [status bar](Status_Bar.md) and the Weather widget, and only while no location is picked. Pick a city in **Settings → Status bar → Weather → Location** and no location permission is asked for. |
| Microphone | [Voice input](Voice_Input.md): the Enter-key swipe on the built-in keyboard and the **Dictate** action, while a dictation listens. |
| Notification access | Notification dots, media controls, pinned notifications and, if you choose apps for it, **Settings → Notifications → Notification history**, which `launcherctl notifications` reads. See [Notifications](Notifications.md). |
| Notifications (app notifications) | The launcher's own notices, and messages and progress sent from the shell (`launcherctl notify`, `launcherctl progress`, the OSC 99 escape). |
| Wallpaper access | Lets the glass bars blur the system wallpaper; without it they render flat. |
| Set wallpaper | The Appearance wallpaper picker's Home and Lock wallpapers, and `launcherctl wallpaper set`. See [Look and themes](Look_And_Themes.md). |
| Calendar | The built-in Agenda and Calendar widgets, asked when you place one. |
| Bind app widgets | Android widgets on the Widgets place; Android asks when you place one. |
| Change audio settings | `launcherctl volume`. |
| Vibrate | Haptic feedback and `launcherctl vibrate`. |
| Files and media / All files access | Shell access to shared storage (`termux-setup-storage`). The launcher itself does not touch your files. |
| Accessibility service | Only the **Accessibility** method of double-tap the A-Z row to lock the screen. |
| Battery optimisation exemption, wake lock, display over other apps, start at boot, install packages | As in Termux: keeping sessions alive, `termux-wake-lock`, Termux:Boot scripts and installing APKs from the shell. |
| Run commands (`RUN_COMMAND`) | Lets other apps run commands in the launcher's shell, as in Termux. Its name follows the edition: `com.termux.permission.RUN_COMMAND`, `com.termux.launcher.nix.permission.RUN_COMMAND`, or `io.vaj.tl.permission.RUN_COMMAND`. |
| Display server (`X11_DISPLAY`) | Lets the display server started from your shell show its screen in the launcher. Only apps signed like the launcher can hold it. |

`READ_LOGS`, `DUMP`, `WRITE_SECURE_SETTINGS` and `PACKAGE_USAGE_STATS` are declared as in Termux and
can only be granted over ADB. The launcher does not ask for `QUERY_ALL_PACKAGES`.

## What the launcher uses Shizuku for

Shizuku is optional. The launcher, terminal, dock, panes, workspaces and app launching all work
without it. With it, the launcher can:

- **Show CPU and process details.** The CPU/RAM values on the status bar open a small system
  monitor. Shizuku lets it read CPU and per-core activity, load average, detailed memory and a real
  list of processes.
- **Name what runs in each pane.** Window chips can show a process name or an editor's open file and
  use measured CPU activity for the working indicator.
- **Lock the screen from the A-Z row** with the **Shizuku** method of double-tap to lock, keeping
  the phone's normal screen-off animation.
- **Run catalogue tools as the shell user** through the [privileged lane](#the-privileged-lane),
  which is how tlstore's `btop` sees the whole phone.

If configured, `su` or `rish` can stand in for Shizuku for the system monitor and the window chips.
The Shizuku lock method has no fallback; choose the Accessibility method instead. Without any
privileged backend, basic memory use still comes from Android; CPU can show `--`, old readings are
marked `stale`, and the process list is absent.

## Setting it up

Shizuku lets an app use selected system APIs with the privileges of ADB or root, after you approve
that app. Root is not required: Shizuku can start through Wireless debugging.

1. Install the Shizuku app.
2. Start its service through Wireless debugging or root. Follow the
   [official Shizuku setup guide](https://shizuku.rikka.app/guide/setup/).
3. In Termux Launcher, open **Settings → Permissions & services → Shizuku**.
4. Tap **Request Shizuku permission** and approve the Shizuku dialog.

## The Shizuku page

**Settings → Permissions & services → Shizuku** shows **Backend status** at the top, as
`TYPE · STATE` (for example `SHIZUKU · READY`). Below it, **Backend policy** has four controls, all
on by default:

- **Enable privileged features**: off turns every privileged operation off.
- **Prefer Shizuku backend**: try Shizuku first, then fall back as the policy allows.
- **Allow shell fallback**: "If Shizuku is unavailable, su / rish may be used."
- **Request Shizuku permission**: request or re-check the permission now.

### The privileged lane

The **Privileged lane** section lets tools from the tlstore catalogue that are marked to need it
start as Android's shell user from a terminal session. `btop` is one: it can then see every process,
the real disks and the network.

- **Lane status**: **Running as shell**, **Connecting to Shizuku…**, **Shizuku isn't running**,
  **Shizuku permission missing** or **Off**.
- **Allow catalog tools to run as shell via Shizuku** (on by default). Off, every request from the
  terminal is refused.

## rish in the terminal

`rish` is Shizuku's shell helper. It gives commands in the terminal an ADB-privileged shell, which
can read system process data and run tools outside the app sandbox. Its `RISH_APPLICATION_ID` must
match the installed edition: `com.termux`, `com.termux.launcher.nix` or `io.vaj.tl`.

## Troubleshooting

### Shizuku is unavailable after a reboot

A Wireless debugging start does not survive a reboot. Start Shizuku again, then open **Settings →
Permissions & services → Shizuku** so the launcher checks again. Until then the launcher uses `su` or
`rish` if **Allow shell fallback** is on, or the unprivileged behaviour above.

### Permission is denied or the prompt does not appear

Open the Shizuku app and check Termux Launcher's authorisation. Grant it there, or reset an earlier
denial, then tap **Request Shizuku permission** again. Check that **Enable privileged features** and
**Prefer Shizuku backend** are on.

### The stats card shows `--`, `stale`, or no processes

CPU percentages need two samples, and the process list is sampled only while the card is open, so
leave it open for a few seconds. If it does not recover, check that the page says
`SHIZUKU · READY`, restart Shizuku, and try **Settings → Diagnostics → Test privileged backend**.

### btop says the lane isn't there

Shizuku must be running and Termux Launcher granted in it, and **Allow catalog tools to run as shell
via Shizuku** must be on. **Lane status** names what is missing.
