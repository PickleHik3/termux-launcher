# launcherctl

`launcherctl` drives the launcher from a shell: open apps, open and drive panes and windows, post
notifications, set the progress ring, use the clipboard, read notification history, raise or lower
the keyboard, and reach a few phone functions (vibration, the torch, the battery, volume, toasts and
the wallpaper). It is installed in `$PREFIX/bin` when the launcher starts, and works from any
shell, script or coding agent on the phone. For local AI models the companion command is `tai`;
see [On-device AI](On_Device_AI.md).

## How it works

- It talks to the launcher's local server, using the address and token in `~/.launcherctl/endpoint`
  and `~/.launcherctl/token`. If they are missing, open the launcher once.
- The launcher only has to be running, not on screen. A command that needs the screen says so.
- Output is JSON. On an error it prints the server's answer (such as `not_owned` or
  `pane_not_found`) and exits 1; a mistake in the command line exits 2.
- `launcherctl --help` prints the full usage, and `launcherctl <group> --help` (for example
  `launcherctl pane --help`) one group's commands, flags and what they do. A group that needs a
  command, run without one, prints the same help and exits 2 instead of doing anything. The HTTP
  routes behind each command are in [LauncherCtl API](LauncherCtl_API.md).

**The ownership rule.** A pane or window opened through `launcherctl` belongs to the opener:
`write`, `read`, `wait`, `resize` and `close` only work on panes and windows opened this way.
`list`, `current`, `get` and `focus` work on every pane. Your own shells cannot be typed into or
read by a script.

Commands that report about "this pane" (`agent`, `notify`, `progress`) use `$TERMUX_LAUNCHER_PANE`,
which every launcher shell has, unless `--pane ID` names another.

## launch

```text
launcherctl launch <app name, package, or activity>
```

Opens an Android app, matched by name, package or activity; exact matches rank first. A Linux app
from the drawer opens on [the Linux display](X11_Display.md).

```sh
launcherctl launch whatsapp
```

## pane

```text
launcherctl pane open [--cwd DIR] [--title NAME] [--tag TAG] [--no-focus] [--print-id]
                      [--from ID] [--direction right|left|down|up]
                      [--size 0.1-0.9 | --columns N | --rows N] [--] [CMD ARGS...]
launcherctl pane current
launcherctl pane get <id>
launcherctl pane list
launcherctl pane focus <id>
launcherctl pane resize <id> --size 0.1-0.9 | --columns N | --rows N
launcherctl pane write <id> [--enter] <text>      (or the text on stdin)
launcherctl pane read <id> [--lines N]
launcherctl pane wait <id> (--match TEXT | --regex RE) [--timeout MS] [--lines N]
launcherctl pane close <id>
```

- `open` splits a new pane off the pane the command runs in and prints its `id`. With a command it
  runs that command in your login shell, and the shell stays when the command ends. It starts in
  the folder the command runs in unless `--cwd` names another. `--no-focus` leaves the keyboard
  where it is; `--tag` is a label `list` reports back; `--print-id` prints only the new pane's id,
  one line, instead of the JSON, so `id=$(launcherctl pane open --print-id …)` needs no parsing.
- Where the pane goes: `--from` names the pane to split instead (an `id` from `list`). When the
  command does not run in a pane, or its pane floats or is in another session, the focused pane is
  split, as before. `--direction` (or `--split`) puts the new pane `right`, `left`, `down` or `up`
  of it; without it the layout chooses. `--size` is the new pane's share of the split pane, from
  0.1 to 0.9 (default 0.5); `--columns` or `--rows` asks for a width or height in cells instead,
  kept so both panes stay usable. Asking for a direction or size in a window with an automatic
  layout (grid, tall, ...) switches that window to manual splits, so the layout cannot move the
  pane away again.
- `resize` sets a pane's share of the split it sits in (`--size`), or its width (`--columns`) or
  height (`--rows`) in cells. Only for panes opened with `pane open` or `window open`.
- `list` prints every window and pane. Each pane reports its `window`, whether it floats, and its
  `geometry`: left, top, columns and rows in cells within its window. `list` also prints
  `features`, what this launcher's pane commands can do (`info`, `wait` and `print-id` are the
  newest), for a script that may meet an older launcher.
- `current` prints the pane the command runs in, the way `get` prints any pane of the current
  session: the record `list` has, plus its window. Outside a launcher pane (`$TERMUX_LAUNCHER_PANE`
  unset) it says so and exits 2.
- `write` types into the pane; `--enter` presses Enter after it. At most 16 KiB per call.
- `read` prints the last lines of the pane, 60 by default, up to 500.
- `wait` returns as soon as one of the pane's last lines (60 by default, `--lines` for more)
  contains `TEXT`, or matches the regular expression `RE`, and prints that line as `text`. Text
  already in the pane counts. It gives up after `--timeout` milliseconds, 30000 by default and at
  most 300000, with `timeout`, or with `pane_not_running` when the pane's shell ends first; both
  exit 1. A timeout does not mean the command did not run, only that the text did not appear: read
  the pane before running it again.

```sh
id=$(launcherctl pane open --print-id --title tests --no-focus)
launcherctl pane write "$id" --enter 'make test'
launcherctl pane wait "$id" --regex 'passed|failed' --timeout 120000
launcherctl pane read "$id" --lines 40
launcherctl pane open --direction right --size 0.4 --no-focus -- htop   # beside this pane
launcherctl pane resize "$id" --columns 60
launcherctl pane current | jq .pane.geometry                          # with jq installed
```

## window

```text
launcherctl window open [--cwd DIR] [--title NAME] [--no-focus] [--print-id] [--] CMD ARGS...
launcherctl window list
launcherctl window focus <id>
launcherctl window close <id>
```

`open` opens a whole new full-size window with its own chip, not a pane beside the one on screen.
The command is required, and the window closes when it ends. `--cwd` is the folder it starts in,
the folder the command runs in by default; `--no-focus` keeps the current window in front while
the new one runs behind it. The `pane` commands reach the new window's pane by its `id` (also
printed as `pane`); `--print-id` prints only that id.

`list` prints the same windows and panes as `pane list`. `focus` brings a window to the front;
`close` closes one opened with `window open`, and only while every pane in it was opened that way.
Both take a window's `id` from `list`, or the `id` of any pane in it.

```sh
id=$(launcherctl window open --print-id --title tlstore --no-focus -- tlstore)
```

## agent

```text
launcherctl agent working|blocked|idle|clear [--agent NAME] [--pane ID]
launcherctl agent install-hooks
```

Tells the window chips and **Sessions** what the AI coding agent in this pane is doing, so a pane
waiting on an answer is visible from anywhere. `install-hooks` adds the four Claude Code hooks that
send these states to `~/.claude/settings.json`, leaving any hook already there alone. Details are in
[Agent status](Agent_Status.md).

```sh
launcherctl agent blocked --agent claude
```

## notify

```text
launcherctl notify [--title T] [--id ID] [--urgency low|normal|critical] [--pane ID] <body>
launcherctl notify --close ID [--pane ID]
```

Posts a message in the phone's notification shade, exactly as the OSC 99 escape does. The body
comes from the arguments or from stdin. Sending the same `--id` again replaces the earlier message;
`--close ID` takes it down. `low` is silent, `critical` uses the urgent channel. Tapping the
notification returns to the pane. See [Notifications](Notifications.md).

```sh
launcherctl notify --title Build '42 tests passed'
```

## progress

```text
launcherctl progress <0-100|clear|error [PCT]|indeterminate|paused [PCT]> [--pane ID]
```

Sets the progress ring on the window's chip, as the OSC 9;4 escape does. `error` and `paused` keep
the last percentage when none is given. Always `clear` when the work is done.

```sh
launcherctl progress 42; launcherctl progress clear
```

## clipboard

```text
launcherctl clipboard copy [<text>]      (or the text on stdin)
launcherctl clipboard paste
```

Writes or reads the Android clipboard, the one the terminal, the keyboard, the display and every
app share. `copy` works whether or not the launcher is on screen. `paste` only answers while the
launcher is on screen and **Settings → Terminal → Let programs read the clipboard** is on.

```sh
git diff | launcherctl clipboard copy
```

## notifications

```text
launcherctl notifications [--app PKG|LABEL] [--since 7d|ISO] [--until 1d|ISO] [--query TEXT] [--limit N] [--json]
launcherctl notifications apps|active [--json]
launcherctl notifications clear [--app PKG|LABEL]
```

Reads the notification history of the apps you chose in **Settings → Notifications →
Notification history** (none by default). Newest first, one line per message as
`time · app · title — text`, or JSON with `--json`. `--since` and `--until` take `90m`, `12h`,
`7d`, `2w` or an ISO date; `--app` takes a package or part of the app's name; `--limit` is 1 to
1000, 100 by default. `apps` lists the recorded apps, `active` lists what is in the shade now, and
`clear` deletes recorded messages. One-time codes are masked when they are stored, unless that is
switched off in Settings.

```sh
launcherctl notifications --app "Work Mail" --since 7d
```

## keyboard

```text
launcherctl keyboard show|hide [--source manual|focus] [--hold]
```

Raises or lowers the launcher's own keyboard. `--source manual` (the default) is you asking.
`--source focus` says a text field took focus or let it go: on the Display place it follows the
same rules as a tap, elsewhere it simply opens or closes the keyboard. `hide --hold` keeps the
keyboard down until this shell shows it again or ends. See [Keyboard](Keyboard.md).

```sh
launcherctl keyboard show --source focus
launcherctl keyboard hide --hold
```

## vibrate

```text
launcherctl vibrate [-d MS] [--force]
```

Vibrates for `MS` milliseconds, 1000 by default and at most 10 seconds. Silent mode and your
vibration setting apply unless `--force` is given.

```sh
launcherctl vibrate -d 200
```

## torch

```text
launcherctl torch on|off
```

Switches the camera flash on or off. It fails with `no_torch` on a phone without one, and with
`torch_unavailable` while another app holds the camera.

```sh
launcherctl torch on
```

## battery

```text
launcherctl battery
```

Prints the battery state as `termux-battery-status` does: `health`, `percentage`, `plugged`,
`status`, `temperature` (°C) and `current` (microamperes).

```sh
launcherctl battery
```

## volume

```text
launcherctl volume [STREAM VALUE]
```

With no arguments, lists every stream with its volume and maximum. With a stream and a whole
number, sets it. Streams are `alarm`, `music`, `notification`, `ring`, `system` and `call`. A value
above the maximum is clamped; Android refuses ring and notification changes under Do Not Disturb.

```sh
launcherctl volume music 7
```

## toast

```text
launcherctl toast [--short] <text>
```

Shows a short message on screen: the launcher's own notice while it is on screen, a system toast
otherwise. Long by default; `--short` for a brief one.

```sh
launcherctl toast --short 'build done'
```

## wallpaper

```text
launcherctl wallpaper set FILE [--home|--lock|--both]
launcherctl wallpaper get
```

`set` makes a picture the wallpaper, through the same code as the launcher's wallpaper picker, on
the home screen, the lock screen or both (the default). The picture must be an image of at most
48 MB under the Termux home or shared storage. With the home screen included, the launcher's
colours and glass follow the new picture. `get` reports what is set now. See
[Look and themes](Look_And_Themes.md).

```sh
launcherctl wallpaper set ~/pics/a.jpg --lock
```

## x11 gpu

```text
launcherctl x11 gpu [--env]
```

Says which graphics profile fits this phone for Linux apps on the display, and whether its packages
are installed. `--env` prints the exports instead, ready to `eval`. See
[GPU acceleration for your apps](X11_Display.md#gpu-acceleration-for-your-apps).

```sh
eval "$(launcherctl x11 gpu --env)"
```

## Termux:API commands on top of launcherctl

The tlstore item `termux-api-shims` puts the familiar Termux:API commands in `~/.local/bin`, built
on these commands, so scripts written for Termux:API run without the Termux:API app:

| Termux:API command | launcherctl command |
| --- | --- |
| `termux-clipboard-get`, `termux-clipboard-set` | `clipboard paste`, `clipboard copy` |
| `termux-notification`, `termux-notification-remove` | `notify`, `notify --close` |
| `termux-notification-list` | `notifications active` |
| `termux-toast` | `toast` |
| `termux-vibrate` | `vibrate` |
| `termux-torch` | `torch` |
| `termux-battery-status` | `battery` |
| `termux-volume` | `volume` |
| `termux-wallpaper` | `wallpaper set` |

Install them with `tlstore install termux-api-shims`; tlstore refuses while the real `termux-api`
package is installed. See [Tlstore](Tlstore.md#termux-api-commands).
