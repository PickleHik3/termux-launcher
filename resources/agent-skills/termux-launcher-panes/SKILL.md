---
name: termux-launcher-panes
description: Open and drive terminal panes in Termux Launcher from inside a shell — show work in a live pane (a TUI, a build, kitty-graphics output), type into it, read it back, focus it, close it. Use when asked to display something in a new pane/window/split, to run a program the user wants to watch, or to render an image/preview in the terminal while you keep working in your own shell.
---

# Panes for agents in Termux Launcher

You are running in a shell inside Termux Launcher, an Android terminal that tiles
panes like a tiling window manager. You can open a pane of your own — a second
terminal on screen next to the one you run in — and use it the way you would use a
browser tab: run something visible there (a TUI, a log tail, `kitten icat` for an
image), while you keep working in your own shell. The user sees your pane appear,
tiled automatically.

Everything goes through one CLI, already on PATH:

```sh
launcherctl pane open [--cwd DIR] [--title NAME] [--tag TAG] [--no-focus] [--] [CMD ARGS...]
launcherctl pane list
launcherctl pane write <id> [--enter] <text>     # or: ... write <id> [--enter] < file
launcherctl pane read <id> [--lines N]           # last N transcript lines (default 60, max 500)
launcherctl pane focus <id>
launcherctl pane close <id>
```

All output is JSON. On an HTTP error the JSON body (with `error.code`) is printed
and the command exits 1.

## The one rule: you own only what you open

`write`, `read` and `close` work **only on panes you opened with `pane open`**
(HTTP 403, code `not_owned`, otherwise). The user's own shells are out of reach —
never try to work around that. `list` and `focus` work on every pane.

## Opening a pane

```sh
id=$(launcherctl pane open --title preview --tag my-agent --no-focus -- htop \
     | sed -n 's/.*"id":"\([^"]*\)".*/\1/p')
```

- Prefer `--no-focus`: the pane appears without stealing the user's keyboard.
  Use `pane focus "$id"` later when you want their attention on it.
- The command runs through the user's login shell (their PATH applies) and a
  shell prompt stays behind when it exits — the pane does not vanish with the
  program. Omit the command for a plain shell you `write` to later.
- `--tag` labels the pane as yours in `pane list` (shown under `"agent"`).
- A single string instead of `--` argv is run via `sh -c`, so pipes work:
  `launcherctl pane open 'make 2>&1 | tee build.log'`.

## Driving it

```sh
launcherctl pane write "$id" --enter 'echo hello'   # --enter presses Enter; max 16 KiB per write
launcherctl pane read "$id" --lines 40              # {"text": "...", "running": true}
launcherctl pane close "$id"                        # when you are done — do not leave litter
```

`read` returns the pane's transcript tail — use it to see what your program
printed, like reading a page you opened. `"running": false` means the shell in
the pane has exited; `write` then answers 409 `pane_not_running`.

## Showing graphics

The terminal supports the kitty graphics protocol (plus sixel and iTerm images).
To show an image: `launcherctl pane open --title artifact --no-focus -- kitten icat --hold out.png`
(or run `kitten icat` via `write` in a pane you keep open). Anything a terminal
can render, a pane can show: `bat` a file, a TUI dashboard, a live `tail -f`.

## Knowing what is on screen

`launcherctl pane list` → windows of the current session with their panes:
`id`, `title`, `name`, `cwd`, `pid`, `running`, `columns`/`rows`, `focused`, and
`agent` (null for the user's panes, your tag/command for yours), plus
`activePane` and the window's retained `layout`.

## Failure modes

- `activity_not_running` (409): the launcher UI is not in the foreground; the
  user is in another app. Tell them, do not retry in a loop.
- `panes_api_disabled` (403): the user switched **Let scripts open panes** off
  (Settings → Terminal & Status → Sessions and panes). Respect it.
- `pane_open_failed` (409): no active session or the terminal limit was hit.
- `pane_not_found` (404): stale id — the user closed your pane; `pane list` to resync.
- `launcherctl: missing ~/.launcherctl/...`: the launcher has not started its
  local API yet — the user needs to open the launcher once.
- Rate limits per minute: open 30, write/read/list ~240, focus 120, close 60.

## Manners

- One or two panes, not a wall: open a pane to show something, close it when done.
- Never `write` secrets — the pane is visible on screen.
- If the user has **Focused pane grows** enabled, `pane focus` makes your pane
  the big one — use it when you have something worth looking at, not constantly.
