# launcherctl reference

`launcherctl` is a POSIX sh script on `PATH` that wraps a localhost HTTP API. It reads
`~/.launcherctl/{endpoint,token}`, which the app writes on startup; if they are missing, the
user needs to open Termux Launcher once. All output is JSON; every command prints the server's
JSON body and **exits 1 on an HTTP error**, with the error code (e.g. `not_owned`,
`pane_not_found`) inside that body — always check the exit status, do not just parse stdout.

Full route tables, auth and rate limits: `docs/LauncherCtl_API.md`. This page is the exact CLI
surface, taken from the client script itself (`LauncherCtlApiServer.java`). The installed script
has the last word: `launcherctl <group> --help` prints a group's usage and every flag, and a group
that needs a command, run bare, prints the same and exits 2 without acting. A mistake in the
command line (a flag without its value, a malformed id) also exits 2, before anything is sent.

## Working in panes and windows

How to run something next to yourself and get its result, the way a tmux, kitty or herdr user
would. Each step is a command from the reference below.

1. **Pane or window.** A pane shares your window, so the user sees your work beside you: a build,
   a test run, a server, an image. A window is a whole screen of its own with its own chip, for a
   full-screen program (an editor, `tlstore`, a TUI that wants the room) or something that should
   not shrink your pane. On a phone, prefer a window over a third or fourth split.
2. **Look at yourself first.** `launcherctl pane current` prints your pane: `.pane.columns`,
   `.pane.rows`, `.pane.geometry` (left, top, columns, rows in cells), `.pane.window`, whether it
   floats, and `.window`, the window holding it (with its `layout`). Outside a launcher pane
   (`TERMUX_LAUNCHER_PANE` unset) it exits 2; then `pane list` and pick ids explicitly.
3. **Choose a side from your size.** Split `--direction right` only when your pane is wide enough
   for two readable columns, about 100 columns or more; otherwise split `--direction down`. A
   portrait phone is almost always `down`. Do not keep splitting the same way: two right-splits
   leave three slivers. Size the new pane with `--size 0.3`–`0.5`, or with `--columns N` / `--rows N`
   worked out from your own geometry (`--rows 12` for a log tail under you). With no direction or
   size the user's layout decides, which is also fine: placing a pane explicitly switches a window
   with an automatic layout to manual splits.
4. **Keep the user where they are.** Always `--no-focus` unless the user asked to switch; the new
   pane already starts in your directory (the CLI sends `$PWD` unless `--cwd` says otherwise).
5. **Take the id, not the JSON.** `id=$(launcherctl pane open --print-id ...)` prints the new pane's
   id and nothing else. Never derive ids from list order or examples. If you do parse JSON (with
   `jq`, if installed), the paths are `.pane.id` after `pane open`, `.pane` (or `.id`) and
   `.windowId` after `window open`.
6. **Run, wait, read.** `pane write "$id" --enter 'make test'` types the command and presses
   Enter. `pane wait "$id" --regex 'passed|failed' --timeout 120000` returns when a line matches
   (output already there counts), printing that line as `.text`. Then `pane read "$id" --lines 80`
   for the context. Pick a pattern that only the finished command prints — a prompt like `$ ` may
   already be on screen. A `timeout` does not mean the command did not run: read the pane before
   you send it again. `pane_not_running` means the shell in that pane exited.
7. **Clean up.** `pane close "$id"` (or `window close`) when you are done; close only what you
   opened.
8. **Never rely on the focused pane.** Always pass your own id; the user may have moved focus,
   switched windows or left the launcher while you worked. Everything here works with the launcher
   in the background.
9. **Check before using a newer flag.** `launcherctl pane list` has `"features"`; `info` (`pane
   current`/`get`), `wait` and `print-id` are listed there, alongside `placement`, `resize`,
   `geometry` and `windows`. The installed script always matches the running launcher, so this
   matters when you talk to the HTTP API yourself or reuse a script written for a newer one.

```sh
launcherctl pane current      # read .pane.columns: 100 or more → right, fewer → down
id=$(launcherctl pane open --print-id --no-focus --direction down --rows 12 --title tests)
launcherctl pane write "$id" --enter 'make test'
launcherctl pane wait "$id" --regex '^(PASS|FAIL)' --timeout 180000
launcherctl pane read "$id" --lines 60
launcherctl pane close "$id"
```

In a script, with `jq` installed, the size check is
`[ "$(launcherctl pane current | jq .pane.columns)" -ge 100 ]`. Without `jq`, read the JSON
yourself rather than cutting it apart with `sed`: the field order is not part of the contract.

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
launcherctl pane open [--cwd DIR] [--title NAME] [--tag TAG] [--no-focus] [--print-id]
                      [--from ID] [--direction right|left|down|up]
                      [--size 0.1-0.9 | --columns N | --rows N] [--] [CMD ARGS...]
launcherctl pane current                           # the pane this runs in ($TERMUX_LAUNCHER_PANE)
launcherctl pane get <id>                          # any pane of the current session
launcherctl pane list
launcherctl pane focus <id>
launcherctl pane resize <id> --size 0.1-0.9 | --columns N | --rows N
launcherctl pane write <id> [--enter] <text>       # or: ... write <id> [--enter] < file
launcherctl pane read <id> [--lines N]             # default 60, max 500
launcherctl pane wait <id> (--match TEXT | --regex RE) [--timeout MS] [--lines N]
launcherctl pane close <id>
```

**Ownership is the security boundary**: `write`, `read`, `wait`, `resize` and `close` work only
on panes *you* opened with `pane open` (or `window open`) — every other pane, including the user's
own shells, answers HTTP 403 `not_owned`. `current`, `get`, `list` and `focus` work on every pane.

### Inspecting a pane

`pane current` (and `pane get <id>`) prints
`{"ok":true,"pane":{…},"window":{"index","id","name","current","focusedPane","owned","layout"},"layout":…}`:
the pane's record as `pane list` has it (below), the window holding it, and that window's layout.
Outside a launcher pane `pane current` exits 2 with a message on stderr. A pane of another session
is 404 `pane_not_found`.

### Waiting for output

`pane wait <id> --match TEXT` (a literal) or `--regex RE` (a Java regular expression, matched
against each line, so `^`/`$` are line ends) returns as soon as one of the pane's last `--lines`
lines (default 60, max 500) matches, printing `{"ok":true,"matched":true,"id":…,"text":"<that
line>","running":true}`. The current text counts, so output that came before the wait matches at
once. `--timeout` is in milliseconds: default 30000, max 300000 (the CLI refuses more with exit 2,
the server with 400), 0 checks once. No match in time is exit 1 with 408 `timeout` — the command
may still be running, or may have printed something you did not expect: `pane read` before you
retry, never resend blindly. If the pane's shell exits (or the pane is closed) first, it is 409
`pane_not_running`. A bad regex is 400 `bad_request`. At most four waits run at once across
everyone; a fifth is 429 `too_many_waits`. The CLI gives curl the timeout plus 15 s for this call.

### Where the pane goes

`pane open` splits **your own pane** (`$TERMUX_LAUNCHER_PANE`) by default, so the new pane lands
next to you, in your window, even when the user is looking at another one. `--from ID` splits a
different pane instead (ids from `pane list`). If your pane can't be split (it floats, or you
have no `TERMUX_LAUNCHER_PANE`), the user's focused pane is split, as before. It starts in your
current directory (`$PWD`) unless `--cwd` names another; the HTTP route alone defaults to home.

- `--direction right|left|down|up` (alias `--split`): the side of that pane the new one goes on.
  Without it the user's layout chooses.
- `--size 0.4`: the new pane takes 40% of the split pane (0.1–0.9, default 0.5). Or
  `--columns 60` / `--rows 12`: a width or height in cells, clamped so neither pane is crushed.
- A direction or size in a window with an automatic layout (grid, tall, ...) switches that window
  to manual splits — the response's `"layout"` says what is in effect (`null` = manual). Only ask
  for a placement when it matters; `pane open --no-focus -- <cmd>` alone respects the user's layout.
- `pane resize <id> --size 0.3` (its share of the split it sits in), `--columns N` or `--rows N`
  changes it later. 409 `no_split` means it has nothing beside (or above/below) it to give way.
- Every pane record has `"window"` (window id), `"floating"`, and `"geometry"`:
  `{"left","top","columns","rows"}` in cells within its window — use it to pick a sensible size.
- `pane list` has `"features": ["placement","resize","geometry","windows","info","wait","print-id"]`;
  a name that is missing means that launcher is older and lacks it (no `features` at all: none of
  them, and the placement flags are ignored).
- `--print-id` prints only the new pane's id, one line, instead of the JSON; a refusal still prints
  its JSON and exits 1. In the JSON the id is `.pane.id`.

```sh
# A 40%-wide pane to the right of this one, without taking the keyboard:
id=$(launcherctl pane open --print-id --direction right --size 0.4 --no-focus -- htop)
launcherctl pane resize "$id" --columns 50
```

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
id=$(launcherctl pane open --print-id --title preview --no-focus -- kitten icat out.png)
launcherctl pane write "$id" --enter 'make test'
launcherctl pane wait "$id" --match 'tests passed' --timeout 120000
launcherctl pane read "$id" --lines 40
launcherctl pane focus "$id"
launcherctl pane close "$id"      # close it when you're done — don't leave litter
```

Rate limits/min: open 30, write/read/wait/list/current/get 240, focus/resize 120, close 60.

## Windows

A window is a whole new full-size, own-chip surface (like the `+` in the window strip), not a
pane sharing the one on screen. Unlike a pane, the window's lifetime IS the command's: when it
exits the window closes.

```sh
launcherctl window open [--cwd DIR] [--title NAME] [--no-focus] [--print-id] [--] CMD ARGS...
launcherctl window list                 # same JSON as pane list
launcherctl window focus <id>           # any window
launcherctl window close <id>           # only a window you opened
```

`CMD` is required (unlike `pane open`). The opened pane is owned exactly like `pane open`'s, so
`pane write|read|close|focus|resize` reach it the same way; the answer has its pane id as `id`
(and `pane`) and the window's id as `windowId`. `window focus|close` take a window id from
`pane list` or the id of any pane in it. `close` answers 403 `not_owned` unless every pane in the
window is yours. Like `pane open`, it starts in `$PWD` unless `--cwd` is given, and `--print-id`
prints only the new pane's id.

```sh
id=$(launcherctl window open --print-id --title tlstore --no-focus -- tlstore)
launcherctl pane read "$id" --lines 40
launcherctl window close "$id"
```

Requests/min: open 30, focus 120, close 60.

## Agent status

Tells the window chips and sessions browser what you're doing: **Working**, **Needs you**, or
**Idle**. See `docs/Agent_Status.md` for the full picture (screen-rule fallback, hook wiring).

```sh
launcherctl agent working|blocked|idle|clear [--agent NAME] [--pane ID]
launcherctl agent install-hooks   # wires Claude Code hooks into ~/.claude/settings.json, idempotent
```

Reports for `$TERMUX_LAUNCHER_PANE` (every shell has it) unless `--pane` says otherwise. If you
are Claude Code and hooks are installed, this already happens for you — don't also call it by
hand mid-turn unless you have a specific reason to. opencode 2's tool service has no
`TERMUX_LAUNCHER_PANE`, so there `agent` needs an explicit `--pane` (get ids from `pane list`).

## Notify, progress, clipboard

```sh
launcherctl notify [--title T] [--id ID] [--urgency low|normal|critical] [--pane ID] <body>
launcherctl notify --title Report < report.txt          # body from stdin when no argument
launcherctl progress <0-100|clear|error [PCT]|indeterminate|paused [PCT]> [--pane ID]
launcherctl clipboard copy [<text>]                      # stdin when no text
launcherctl clipboard paste
```

These are the escape sequences OSC 99 (shade notification), OSC 9;4 (progress ring) and OSC 52
(clipboard) as HTTP routes, running the same code inside the app — for a process that has no
terminal to write an escape into. They need **no pane id**: with `$TERMUX_LAUNCHER_PANE` set
they go to your pane, `--pane` picks another, and with neither (opencode's tool service) they go
to the current pane. They only need the launcher *running*, not on screen — except a clipboard read.

- `notify`: `--id` names the message so a later one with the same name replaces it; `--urgency
  low` is silent, `critical` uses the urgent channel. Answers `{"ok":true,"pane":…,"id":…,
  "shown":bool}`. 60/min.
- `progress`: a bare number is a normal report; `error`/`paused` keep the last percentage unless
  one follows. Answers the state and the ring's percent. 600/min. `pane_not_ready` (409) means
  that pane's terminal hasn't started.
- `clipboard copy`/`paste`: the Android clipboard, shared with the in-app keyboard's paste key and
  every app. `copy` works whether or not the launcher is on screen, as Termux:API's
  `termux-clipboard-set` always has. `paste` refuses with 409 `launcher_not_visible` when the
  launcher isn't on screen (Android only lets the app in front read the clipboard), and is 403
  `clipboard_read_disabled` when **Settings → Terminal → Let programs read the clipboard**
  is off — the same two rules OSC 52 follows. 60/min each. A `copy` that lands also appears in
  the in-app keyboard's clipboard history (the clipboard corner of the Ctrl key), so the user can
  paste it again later; `paste` reads the Android clipboard, never that history.

Both `notify` and `progress` answer 409 `no_session` when there is no shell at all, and 404
`pane_not_found` for a stale `--pane`. `launcherctl notify --close ID` takes down the message
that `--id ID` named.

## Notification history for agents

The user can let the launcher keep a log of notifications from chosen apps (off by default, per
app). When it is on you can read it — this is how to answer "make a task list from my work email
notifications over the last week":

```sh
launcherctl notifications apps                            # which apps are recorded, with counts
launcherctl notifications --app "Outlook" --since 7d      # newest first, one line per message
launcherctl notifications --app com.google.android.gm --since 2026-09-21 --query invoice
launcherctl notifications --json --since 7d --limit 500   # structured, for a script
launcherctl notifications active                          # what is in the shade right now
```

Each text line is `time · app · title — text`. Read the lines, drop duplicates and chatter, and
turn what asks for action (a deadline, a request, a meeting) into tasks with the date and the
sender. The answer starts with a `# ...` hint line when notification access is not granted or no
app is enabled: in that case say so and tell the user to pick apps in the launcher's settings; do
not guess at notifications you cannot see. Codes in the log are masked (`••••••`) by design — do not
try to recover them. `launcherctl notifications clear [--app X]` deletes what is recorded, so use it
only when the user asks. `--since`/`--until` take `90m`, `12h`, `7d`, `2w` or an ISO date; `--app`
takes a package or part of the app's name; default limit is 100.

## Device: vibrate, torch, battery, volume, toast, wallpaper

```sh
launcherctl vibrate [-d MS] [--force]     # default 1000 ms, capped at 10 s
launcherctl torch on|off                  # first camera with a flash; 404 no_torch if none
launcherctl battery                       # health, percentage, plugged, status, temperature, current
launcherctl volume                        # every stream: alarm music notification ring system call
launcherctl volume music 7                # set one stream (clamped to its max)
launcherctl toast [--short] <text>        # in-app notice, or a system toast off screen
launcherctl wallpaper set FILE [--home|--lock|--both]   # default both; FILE under ~ or shared storage
launcherctl wallpaper get                 # ids, live wallpaper, whether the launcher set it
```

Output is JSON in the shapes the `termux-*` equivalents print (`volume` wraps its array under
`streams`). None needs a pane or a visible launcher. `vibrate` obeys the user's haptics setting
unless `--force`. `wallpaper set` goes through the launcher's own wallpaper path (stored id, glass
picture), takes seconds for a large image, and is limited to 6 a minute; only change the wallpaper
when the user asks. If the launcher is not running the wallpaper is still set and the launcher
catches up on next open (`launcher_refresh: on_next_open`).

The `termux-*` commands that Termux:API scripts call (`termux-clipboard-get`/`-set`,
`termux-notification`, `-remove`, `-list`, `termux-toast`, `termux-vibrate`, `termux-torch`,
`termux-battery-status`, `termux-volume`, `termux-wallpaper`) work here without the Termux:API
app once `tlstore install termux-api-shims` has put them in `~/.local/bin`: each one calls
these routes. So a user's script that uses them needs the shims, not Termux:API; for your own
calls, use `launcherctl` directly. The shims refuse to install beside the `termux-api`
package, which provides the same names.

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
  `window_not_found` (404) is the same for a window.
- `pane_not_tiled` (409): `--from` (or the pane you resize) floats, so it has no split.
- `bad_request` (400): a `--direction`, `--size`, `--columns` or `--rows` that isn't valid, or a
  `pane wait` regex that doesn't compile.
- `not_owned` (403): that pane/window isn't one you opened.
- `timeout` (408, `pane wait`): the text didn't show up in time. Not proof the command didn't
  run — `pane read` first. `pane_not_running` (409): that pane's shell exited before it did.
- `too_many_waits` (429): four `pane wait`s are already running; wait for one to finish.
- Exit 2 with a usage line on stderr: the command line itself was wrong; nothing was sent.
- `launcher_not_visible` (409): `clipboard paste` only works with the launcher on screen;
  `clipboard_read_disabled` (403): the user turned clipboard reads off. Say so, don't retry.
- `launcherctl: missing ~/.launcherctl/...`: the app hasn't started its local API yet.
- `429` with `Retry-After`: back off; limits are per-route.

## Manners

- One or two panes, not a wall — open one to show something, close it when done.
- Never `write` secrets into a pane; it's visible on screen.
- Never relaunch the app with `am start -n com.termux/.app.TermuxActivity` — it creates a second
  instance that fights the running one. Use `launcherctl launch` or `pane focus` instead.
