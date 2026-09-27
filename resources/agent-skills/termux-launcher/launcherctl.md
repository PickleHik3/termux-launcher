# launcherctl reference

`launcherctl` is a POSIX sh script on `PATH` that wraps a localhost HTTP API. It reads
`~/.launcherctl/{endpoint,token}`, which the app writes on startup; if they are missing, the
user needs to open Termux Launcher once. All output is JSON; every command prints the server's
JSON body and **exits 1 on an HTTP error**, with the error code (e.g. `not_owned`,
`pane_not_found`) inside that body — always check the exit status, do not just parse stdout.

Full route tables, auth and rate limits: `docs/en/LauncherCtl_API.md`. This page is the exact CLI
surface, taken from the client script itself (`LauncherCtlApiServer.java`).

## Apps

```sh
launcherctl launch <app name, package, or activity>
```

Matches the launcher's own app catalog (label, package, activity, partial match) and starts it.
An X11 (Linux) app runs on the embedded display instead, starting the display first if it was
off. 404 `not_found` (no match), 409 `ambiguous` (several tied matches), 500 `launch_failed`.
30 requests/min.

```sh
launcherctl launch maps
launcherctl launch com.example.maps
```

## Panes

```sh
launcherctl pane open [--cwd DIR] [--title NAME] [--tag TAG] [--no-focus] [--] [CMD ARGS...]
launcherctl pane list
launcherctl pane focus <id>
launcherctl pane write <id> [--enter] <text>       # or: ... write <id> [--enter] < file
launcherctl pane read <id> [--lines N]             # default 60, max 500
launcherctl pane close <id>
```

**Ownership is the security boundary**: `write`, `read` and `close` work only on panes *you*
opened with `pane open` — every other pane, including the user's own shells, answers HTTP 403
`not_owned`. `list` and `focus` work on every pane.

- The command always runs as an **argv array** — `pane open -- kitten icat out.png`, not a shell
  string. There is no `sh -c` form built in, so **for a pipe, name `sh -c` explicitly**:
  ```sh
  launcherctl pane open --title build --no-focus -- sh -c 'make 2>&1 | tee build.log'
  ```
- Prefer `--no-focus`: the pane appears without stealing the keyboard. `pane focus <id>` later
  when it's worth the user's attention.
- The command runs through the user's login shell (their `PATH` applies); the shell stays behind
  when the command exits — the pane doesn't vanish.
- `--tag` labels the pane as yours in `pane list` (under `"agent"`).
- `write` sends at most 16 KiB per call; `--enter` appends a carriage return.
- `read` returns `{"text": "...", "running": true}` — the transcript tail.
  `"running": false` means the shell exited; `write` then answers 409 `pane_not_running`.

```sh
id=$(launcherctl pane open --title preview --no-focus -- kitten icat out.png \
     | sed -n 's/.*"id":"\([^"]*\)".*/\1/p')
launcherctl pane write "$id" --enter 'make test'
launcherctl pane read "$id" --lines 40
launcherctl pane focus "$id"
launcherctl pane close "$id"      # close it when you're done — don't leave litter
```

Rate limits/min: open 30, write/read/list 240, focus 120, close 60.

## Windows

A window is a whole new full-size, own-chip surface (like the `+` in the window strip), not a
pane sharing the one on screen. Unlike a pane, the window's lifetime IS the command's: when it
exits the window closes.

```sh
launcherctl window open [--title NAME] [--no-focus] [--] CMD ARGS...
```

`CMD` is required (unlike `pane open`). The opened pane is owned exactly like `pane open`'s, so
`pane write|read|close|focus` reach it the same way.

```sh
id=$(launcherctl window open --title tlstore --no-focus -- tlstore \
     | sed -n 's/.*"id":"\([^"]*\)".*/\1/p')
launcherctl pane read "$id" --lines 40
```

30 requests/min.

## Agent status

Tells the window chips and sessions browser what you're doing: **Working**, **Needs you**, or
**Idle**. See `docs/agent-status.md` for the full picture (screen-rule fallback, hook wiring).

```sh
launcherctl agent working|blocked|idle|clear [--agent NAME] [--pane ID]
launcherctl agent install-hooks   # wires Claude Code hooks into ~/.claude/settings.json, idempotent
```

Reports for `$TERMUX_LAUNCHER_PANE` (every shell has it) unless `--pane` says otherwise. If you
are Claude Code and hooks are installed, this already happens for you — don't also call it by
hand mid-turn unless you have a specific reason to.

## Keyboard

```sh
launcherctl keyboard show|hide [--source manual|focus] [--hold]
```

`--source focus` says a text field took focus (a signal, not an order); default is `manual`.
`hide --hold` keeps the keyboard down until this same session calls `show` itself, or ends — used
by a full-screen program that wants the keyboard out of its way while it runs. 409
`activity_not_running` if the launcher isn't there at all; 409 `unavailable` if the in-app
keyboard is off. 240 requests/min each.

## X11 GPU

```sh
launcherctl x11 gpu           # JSON: which acceleration row fits this phone
launcherctl x11 gpu --env     # the same, as `KEY=value` lines to eval
```

```sh
eval "$(launcherctl x11 gpu --env)"
```

## Failure modes

- `activity_not_running` (409): the **launcher process itself is gone** (killed, or its Activity
  destroyed with nothing recreated yet) — not merely that another app is in the foreground. Pane
  and window routes work fine while the user is in another app or the screen is off; they only
  fail once the process is dead. Tell the user, don't retry in a loop.
- `panes_api_disabled` (403): the user turned off **Let scripts open panes** (Settings → Terminal
  & Status → Sessions and panes). Respect it.
- `pane_open_failed` / `window_open_failed` (409): no active session, terminal limit hit, or
  compatibility mode on.
- `pane_not_found` (404): stale id — the user closed your pane; `pane list` to resync.
- `not_owned` (403): that pane/window isn't one you opened.
- `launcherctl: missing ~/.launcherctl/...`: the app hasn't started its local API yet.
- `429` with `Retry-After`: back off; limits are per-route.

## Manners

- One or two panes, not a wall — open one to show something, close it when done.
- Never `write` secrets into a pane; it's visible on screen.
- Never relaunch the app with `am start -n com.termux/.app.TermuxActivity` — it creates a second
  instance that fights the running one. Use `launcherctl launch` or `pane focus` instead.
