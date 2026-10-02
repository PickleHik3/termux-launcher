# LauncherCtl API (Local AI Endpoint)

## Overview
LauncherCtl is a localhost HTTP server that exposes an OpenAI- and Ollama-compatible inference endpoint, model management for the On-device AI runtime (`tai` internal code prefix), one app-launch route, the pane routes that let a process in a shell open and drive a terminal pane of its own, the signal routes (a notification, the progress ring, the clipboard) for a process that has no terminal to write the matching escape sequence into, and the opt-in notification history routes. It is not a general device-control or agent bridge.

- Server: in app process, isolated from native model work which runs in `:tai_runtime`.
- Bind mode: `localhost` (default, `127.0.0.1`) or opt-in `lan` (`0.0.0.0`).
- Auth: bearer token from `~/.launcherctl/token`, or `X-Api-Key: <token>` header. The token can be made optional for localhost (see [Auth](#auth)).
- Endpoint URL: `~/.launcherctl/endpoint`.
- CLIs: `$PREFIX/bin/tai` for local AI and `$PREFIX/bin/launcherctl` for `launcherctl launch <app name, package, or activity>`, `launcherctl pane …`, `launcherctl notify`, `launcherctl progress`, `launcherctl clipboard`, `launcherctl notifications`, and the device commands `vibrate`, `torch`, `battery`, `volume`, `toast` and `wallpaper`. The launcher app installs both when `TermuxActivity` starts.
- Removed helpers: `launcherctl-mcp` and `launcher-restart` are no longer installed and are deleted on upgrade.

`tai` uses this authenticated server for the local On-device AI endpoint; native AI runtime work is isolated in `:tai_runtime`.

## Files and Components

- Server implementation:
  - `app/src/main/java/com/termux/launcherctl/LauncherCtlApiServer.java`
- App startup wiring:
  - `app/src/main/java/com/termux/app/TermuxActivity.java`
- Manifest service entry:
  - `app/src/main/AndroidManifest.xml`

Runtime files under `$HOME/.launcherctl`:

- `token`: API bearer token.
- `endpoint`: local base URL (`http://127.0.0.1:<port>`).

On-device AI model packages live under app-private model storage, not under `~/.launcherctl`.

## Discovery

Read the active endpoint and token from the two files the app writes on startup:

```sh
BASE=$(sed -n '1p' ~/.launcherctl/endpoint)   # e.g. http://127.0.0.1:41237
TOKEN=$(cat ~/.launcherctl/token)
```

The first line of `~/.launcherctl/endpoint` is the active base URL. OpenAI-compatible clients expect `/v1` appended; Ollama-compatible clients use the base address as-is.

If the files are missing, open Termux Launcher once so the server starts and writes them.

## Auth

Authentication uses a bearer token. Send it with either header:

- `Authorization: Bearer <token>`
- `X-Api-Key: <token>`

The token is a startup-generated random secret stored owner-only at `~/.launcherctl/token`. Comparison is constant-time.

### Token-optional toggle (localhost only)

A setting **Require API token** (default **on**) lives under **Settings → On-device AI → Endpoint & access**. When turned **off**, requests from localhost need no token — any placeholder API key (or none) works. This is convenient for local CLI clients that cannot easily read the token file.

- `GET /` and `OPTIONS` never require auth, regardless of the toggle.
- **LAN bind mode always requires the token**, no matter the toggle state. Anyone who can reach a LAN-exposed endpoint and does not present the token gets `401`.

Rotate the token with `POST /v1/auth/rotate`, which rewrites `~/.launcherctl/token` and `~/.launcherctl/endpoint`.

## CORS, Host checks, and Health

Browser clients are accepted only when the page itself came from loopback. A request carrying an
`Origin` header is served — and granted CORS by echoing that origin back — only if the origin host is
`localhost`, `127.0.0.0/8`, or `::1`; any other origin gets `403 forbidden_origin`, token or no token.
Requests with no `Origin` at all (curl, the OpenAI SDKs, the shell helpers) are unaffected: the bearer
token governs those.

The `Host` header must name an address the server actually bound — a literal IP, or `localhost`. A
request naming any other hostname gets `403 forbidden_host`. This is what stops DNS rebinding, where a
remote page points its own hostname at `127.0.0.1` so the browser treats the API as same-origin.

- `GET /` — returns `Ollama is running` with HTTP 200. No auth. Used by clients to detect a live Ollama-compatible server.
- `OPTIONS` — CORS preflight. No auth, but subject to the origin and host checks above.

## Endpoint Reference

The complete route surface is below. Besides the On-device AI routes there are app launch, the pane and window routes, the on-screen keyboard, the three signal routes (notification, progress, clipboard), and the notification history routes. There are no media, resource, event, MCP, restart, or general device-control routes.

### Health and discovery

| Method | Path | Auth | Purpose |
| --- | --- | :---: | --- |
| GET | `/` | no | Health check, body `Ollama is running` |
| OPTIONS | any | no | CORS preflight |

### App launch

| Method | Path | Purpose |
| --- | --- | --- |
| POST | `/v1/apps/launch` | Match and launch one app from the launcher's app catalog |

The request body contains one non-empty query:

```json
{"query":"maps"}
```

The query may be an app label, package name, activity name, or partial match. Exact package,
activity, and stable-id matches rank first. Exact labels rank next, followed by prefix and
substring matches. The best unique match launches and returns HTTP 200:

```json
{
  "ok": true,
  "query": "maps",
  "label": "Maps",
  "packageName": "com.example.maps",
  "activityName": "com.example.maps.MainActivity",
  "stableId": "com.example.maps/com.example.maps.MainActivity#user=0",
  "userId": 0,
  "clonedProfile": false
}
```

No match returns HTTP 404 with error code `not_found`. Several matches tied at the best rank return
HTTP 409 with error code `ambiguous` and a `candidates` array containing up to eight app records.
An empty query returns HTTP 400 with `bad_request`. A matched app that Android cannot start returns
HTTP 500 with `launch_failed`.

A query that matches a Linux (X11) desktop app runs it on the embedded display instead of starting
an Android component; the display starts first if it was off. This route never goes through the
terminal action dispatcher, so it does not need the launcher in the foreground either, and the X
server and any apps already running on it keep going whether or not the terminal is on screen — the
display's rendering surface simply detaches while the Display page is not the one showing and
reattaches on its own once it is again.

The route allows 30 requests per minute. The installed shell client reads the endpoint and bearer
token from `~/.launcherctl`, then sends this request:

```sh
launcherctl launch maps
launcherctl launch com.example.maps
```

`launcherctl`'s other commands are `pane`, `window`, `agent`, `notify`, `progress`, `clipboard`, `notifications`, `keyboard` and `x11`, below. Use `tai` for model and inference commands.

### Panes

These routes exist so that something running inside a shell — an AI coding agent, a build, a
script — can open a pane of its own to show its work in, the way it would open a browser tab, and
drive that pane whether or not the user is currently looking at the terminal. Every route runs as
one terminal action on the UI thread. The launcher does not need to be in the foreground: it only
needs to be running at all, which covers a plain app switch (the user opened something else, or
the screen is off) as well as the terminal actually being on screen. `activity_not_running` (HTTP
409) means the launcher process itself is not there to ask — it was killed, or the Activity was
destroyed and nothing has recreated it yet — not merely that another app is in front right now.

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/v1/panes` | List the windows and panes of the current session |
| POST | `/v1/panes` | Open a new pane in the current window |
| POST | `/v1/panes/{id}/focus` | Switch to the pane's window and focus it |
| POST | `/v1/panes/{id}/write` | Type text into a pane opened through this API |
| GET | `/v1/panes/{id}/text?lines=N` | Read the last `N` transcript lines of a pane opened through this API |
| POST | `/v1/panes/{id}/close` | Close a pane opened through this API |

**Ownership is the security boundary.** Opening a pane marks it as owned by the API; `write`,
`text` and `close` answer HTTP 403 `not_owned` for any other pane, including every shell the user
opened by hand. `GET /v1/panes` and `focus` work on all panes. The token therefore buys a pane, not
the user's shells — which is why these routes could come back after the general remote-action
endpoints were removed.

`POST /v1/panes` takes:

```json
{"command": ["kitten", "icat", "out.png"], "cwd": "/data/data/com.termux/files/home/proj",
 "title": "preview", "focus": false, "tag": "claude-code"}
```

- `command` — an argv array, or a string that is run through `sh -c`. Omit it for a plain shell.
  The command runs through the user's login shell (so their `PATH` applies) and the shell stays
  behind when the command exits, exactly like a restored workspace pane.
- `cwd` — defaults to the home directory. `title` — the session name shown on the pane.
- `focus` — default `true`; `false` leaves the keyboard where the user has it.
- `tag` — a free-form label (agent name, task id) that `GET /v1/panes` reports back.

Under the `dwindle` pane layout the new pane halves the focused pane along its longer side; under
the other layouts it splits along the window's longer side and the retained layout re-tiles.

Every pane record looks like:

```json
{"id": "6d3f…", "title": "~/proj", "name": "preview", "cwd": "/data/data/com.termux/files/home/proj",
 "pid": 12345, "running": true, "columns": 80, "rows": 24, "focused": false,
 "agent": {"tag": "claude-code", "command": ["kitten", "icat", "out.png"], "openedAt": 1756569600000}}
```

`agent` is `null` for panes the user opened. `GET /v1/panes` returns
`{"windows": [{"index", "id", "name", "current", "focusedPane", "panes": […]}], "activePane", "layout"}`.
`write` takes `{"text": "…", "enter": true}` (at most 16 KiB per call; `enter` appends a carriage
return) and answers 409 `pane_not_running` once the shell has exited. `text` accepts `lines` from 1
to 500 (default 60). An unknown id is HTTP 404 `pane_not_found`; a pane that could not be opened
(no session, terminal limit reached, compatibility mode on) is HTTP 409.

Rate limits per minute: list 240, open 30, focus 120, write 240, text 240, close 60.

The shell client wraps all of this:

```sh
id=$(launcherctl pane open --title preview --no-focus -- kitten icat out.png \
     | sed -n 's/.*"id":"\([^"]*\)".*/\1/p')
launcherctl pane write "$id" --enter 'make test'
launcherctl pane read "$id" --lines 40
launcherctl pane list
launcherctl pane focus "$id"
launcherctl pane close "$id"
```

`launcherctl pane write` reads the text from its arguments, or from stdin when none are given.
Every `pane` command prints the server's JSON body and exits 1 on an HTTP error, so the error code
(`not_owned`, `pane_not_found`, …) is always visible to the caller.

### Windows

| Method | Path | Purpose |
| --- | --- | --- |
| POST | `/v1/windows` | Open a NEW full-size window (not a split) |

A pane shares the screen with whatever else is in the current window; a window is the same kind of
full-size, own-chip surface `+` in the window strip creates. This route is for a script that wants
one of those for itself instead of a pane squeezed in beside the user's — tlstore-ui's own
full-screen UI is the motivating case. It is background-safe exactly like the pane routes: the
launcher only needs to be running, not in the foreground.

`POST /v1/windows` takes:

```json
{"command": ["tlstore"], "title": "tlstore", "focus": true}
```

- `command` — an argv array, or a string that is run through `sh -c`. Unlike `POST /v1/panes`, this
  is required: a window with nothing to run is just `launcherctl window open`'s job in the
  interactive UI (Ctrl+Alt+C / the strip's `+`), not this API's. The command runs through the
  user's login shell, and the window is the command's: when it exits, the window closes and its
  chip goes with it — unlike a pane opened through `POST /v1/panes`, where the shell stays behind.
  If it was the last window, you are left with an empty home, as when you close the last window
  yourself.
- `title` — the name shown on the window's chip.
- `focus` — default `true`, switches the window strip to it; `false` leaves whichever window is on
  screen alone while the new one keeps running behind it.

The response is flat, not a pane record:

```json
{"ok": true, "id": "6d3f…", "window": 2, "columns": 80, "rows": 24}
```

`window` is the new window's index, in the same order `GET /v1/panes` lists windows under.
`columns`/`rows` are `0` when the window has not been laid out yet (an unfocused window nobody has
looked at). The opened pane is owned exactly like one opened through `POST /v1/panes`, so
`/v1/panes/{id}/write|read|close` reach it the same way — `GET /v1/panes` and `/focus` too. A
window that could not be opened (no session, terminal limit reached, compatibility mode on, or the
window could never be given a starting size) is HTTP 409 `window_open_failed`; a missing `command`
is HTTP 400 `bad_request`.

Rate limit: 30 a minute.

```sh
id=$(launcherctl window open --title tlstore --no-focus -- tlstore \
     | sed -n 's/.*"id":"\([^"]*\)".*/\1/p')
launcherctl pane read "$id" --lines 40
launcherctl pane focus "$id"
launcherctl pane close "$id"
```

### The on-screen keyboard

| Method | Path | Purpose |
| --- | --- | --- |
| POST | `/v1/keyboard/show` | Raise the in-app keyboard |
| POST | `/v1/keyboard/hide` | Put the in-app keyboard down |

Both take `{"source": "manual"}` (the default) or `{"source": "focus"}`. `manual` is the user
asking. `focus` says a text field took focus, which is a signal rather than an order: on the
Display place it goes through the same rules a tap there does — see
[The keyboard follows text fields](X11_Display.md#the-keyboard-follows-text-fields) — and
elsewhere it simply opens or closes the keyboard. Both put something on a screen, so a stopped
launcher answers 409 `activity_not_running`; 409 `unavailable` means the in-app keyboard is off.
If the user has switched the keyboard off with `keyboard.toggle_enabled`, a `manual` show turns it
back on, while a `focus` show is ignored and answers 409 `unavailable`.
Rate limit: 240 a minute each.

`hide` also takes `{"hold": true}`: the calling session keeps the keyboard down until it calls
`show` itself, instead of just for this one call. If that session ends first, the launcher shows
the keyboard again on its own — nothing is ever left stuck down. A full-screen program that wants
the keyboard out of its way while it runs (tlstore-ui) asks for a hold.

```sh
launcherctl keyboard show --source focus
launcherctl keyboard hide --source focus
launcherctl keyboard show          # source=manual
launcherctl keyboard hide --hold   # keep it down until this session shows it again or ends
```

### Notifications, the progress ring and the clipboard

| Method | Path | Purpose |
| --- | --- | --- |
| POST | `/v1/notify` | A message in the phone's notification shade — what `OSC 99` sends |
| POST | `/v1/progress` | The progress ring on a window chip — what `OSC 9;4` sets |
| POST | `/v1/clipboard` | Put text on the Android clipboard — what an `OSC 52` write does |
| GET | `/v1/clipboard` | Read the Android clipboard — what an `OSC 52` query answers |

A program that has a terminal sends these things as escape sequences and needs none of this. These
routes exist for a process that does not: a coding agent's tool runner is typically a service
parented to init with stdin on `/dev/null`, so nothing it prints can reach any terminal. Each route
runs the very same code its escape sequence does — the app has one implementation per signal
(`ShellSignals`), and the OSC handlers and these routes both call it — so a notification posted
either way is indistinguishable, a ring set either way is the same ring, and the clipboard rules are
the same rules.

**Attribution.** None of these needs a pane id. The body's optional `pane` names the pane the
signal belongs to (the window whose chip shows the ring, the pane a tapped notification returns
to); without it the signal goes to the current pane. The shell client fills `pane` from
`$TERMUX_LAUNCHER_PANE` when that is set, so from an ordinary shell the result is exactly what the
escape sequence would have done; in an environment without it (opencode's tool runner) the current
pane is a sensible default. An unknown `pane` is HTTP 404 `pane_not_found`; no pane at all (no
session) is HTTP 409 `no_session`.

**Background.** Like the pane routes, these only need the launcher to be *running*, not on screen:
`activity_not_running` (409) means the process is gone. A notification sent while the user is in
another app is precisely the case the shade is for; a progress report is state the chip picks up
when the terminal is next seen. A clipboard read is the exception, see below.

`POST /v1/notify` takes:

```json
{"title": "Build", "body": "42 tests passed", "id": "build", "urgency": "normal", "pane": "6d3f…"}
```

- `body` — the message; required unless `title` is given. Multi-line is fine.
- `title` — optional headline. As with `OSC 99`, a message with only a body uses it as the headline.
- `id` — optional name (up to 256 characters): sending the same name again replaces the earlier
  message instead of stacking another one.
- `urgency` — `low` (silent), `normal` (default), or `critical` (the urgent channel); the
  protocol's `0`/`1`/`2` are accepted too.

Answers `{"ok": true, "pane": "…", "id": "build", "shown": true}`. `shown` is false when nothing
reached the user: the launcher's notifications are turned off and it is not on screen either.
Rate limit: 60 a minute.

`POST /v1/progress` takes:

```json
{"state": "normal", "percent": 42, "pane": "6d3f…"}
```

- `state` — `clear`, `normal`, `error`, `indeterminate` or `paused` (`0`–`4` in `OSC 9;4` terms).
  May be omitted when `percent` is given, which means `normal`.
- `percent` — 0–100. Optional for `normal`, `error` and `paused`, in which case the ring keeps its
  last value, as the escape does.

Answers `{"ok": true, "pane": "…", "state": "normal", "percent": 42}`. A pane whose terminal has not
started yet is 409 `pane_not_ready`. Rate limit: 600 a minute, the same as agent status reports,
since a build prints a percentage often.

`POST /v1/clipboard` takes `{"text": "…"}` and answers `{"ok": true, "length": 12}`.
`GET /v1/clipboard` answers `{"ok": true, "text": "…"}` (`""` when the clipboard holds no text).

The clipboard is the one place a signal can *take* something from the user or replace what they
just copied, so both directions follow the rules an `OSC 52` write and query already follow, and
answer with their own codes rather than a generic one:

- Writing works whether or not the launcher is on screen, as Termux:API's `termux-clipboard-set`
  always has: a script started from another app's foreground is asking on purpose. An `OSC 52`
  write from a pane keeps the on-screen rule, since any program printing to a pane can emit one.
- Reading needs the launcher on screen. Off screen, `GET /v1/clipboard` answers 409
  `launcher_not_visible` — Android only lets the focused app read the clipboard anyway.
- Reading also needs **Settings → Terminal → Let programs read the clipboard** (on by default), the
  same switch that gates `OSC 52` queries. Off, `GET /v1/clipboard` is 403 `clipboard_read_disabled`.

There is no separate clipboard history to keep in step: the Android clipboard is the one clipboard
here, and the in-app keyboard's paste key, the terminal's own paste, the Linux display and every
other app all read it. Rate limit: 60 a minute each way.

The shell client wraps all of this:

```sh
launcherctl notify [--title T] [--id ID] [--urgency low|normal|critical] [--pane ID] <body>
printf 'line one\nline two' | launcherctl notify --title Report      # body from stdin
launcherctl progress 42            # normal, 42 %
launcherctl progress error         # keeps the last percentage
launcherctl progress indeterminate
launcherctl progress clear         # always clear when the work is done
launcherctl progress 70 --pane "$id"
launcherctl clipboard copy 'some text'
git diff | launcherctl clipboard copy
launcherctl clipboard paste        # {"ok":true,"text":"…"}
```

`POST /v1/notify` also takes `{"close": "build"}` in place of a body: it takes down the message
with that id, as `OSC 99`'s `p=close` does, and answers `{"ok": true, "pane": "…", "id": "build",
"closed": true}` (the same answer whether or not the message was still up). Shell:
`launcherctl notify --close ID [--pane ID]`.

### Notification history

A per-app log of notifications that a process in the shell can query: an agent asked to "make a
task list from my work email notifications over the last week" runs one command. It is **opt-in
per app** and off until the user picks apps in the launcher's settings (the string set
`app_notification_history_packages`); notification access alone records nothing. Only apps in that
set are written, and a notification from any other app is never stored.

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/v1/notifications` | Recorded messages, newest first |
| GET | `/v1/notifications/apps` | Recorded apps with counts and last-seen time |
| GET | `/v1/notifications/active` | What is in the shade now, for enabled apps only |
| POST | `/v1/notifications/clear` | Delete recorded rows, all or one app's |

`GET /v1/notifications` takes these query parameters, all optional:

| Parameter | Meaning |
| --- | --- |
| `app` | A package name, or part of the app's name (case-insensitive) |
| `since`, `until` | `90m`, `12h`, `7d`, `2w` (counted back from now), an ISO date or date-time, or epoch milliseconds. A bare `until` date includes that whole day |
| `query` | Text to look for in title, text, sender, conversation and app name |
| `limit` | 1 to 1000, default 100 |
| `format` | `text` for one line per message instead of JSON |

The JSON answer is `{"ok": true, "count": N, "notifications": [...]}`; each row has `time` (ms),
`timeIso`, `package`, `app`, `conversation`, `title`, `sender`, `text`, `subText`, `category`,
`channel`, `key`, `postTime` and `removedTime` (null while it is in the shade). `/apps` answers
`{"apps": [{"package", "app", "count", "lastSeen", "lastSeenIso"}]}`. `/clear` takes an optional
`{"app": "..."}` (or `?app=`) and answers `{"ok": true, "removed": N}`. A bad `since`, `until` or
`limit` is a `400 bad_request` that says what it could not read.

Every read answers with a `hint` when there is nothing to read for a reason the caller can fix:
notification access is not granted, or no app is enabled. With `format=text` the hint is a first
line starting `# `.

```sh
launcherctl notifications --app "Work Mail" --since 7d
launcherctl notifications --since 2026-09-21 --until 2026-09-28 --query invoice --limit 50
launcherctl notifications --json --app com.google.android.gm
launcherctl notifications apps
launcherctl notifications active
launcherctl notifications clear --app com.google.android.gm
```

The text form is one line per message, `time · app · title — text`:

```
2026-09-28 14:03 · Work Mail · Ann — Invoice due Friday
2026-09-28 13:00 · Chat · Project · Ben: on my way
```

What is recorded, and what is not:

- One row per distinct message. An InboxStyle bundle (Gmail, Outlook) becomes a row per line and a
  MessagingStyle chat a row per message with its sender and time, all sharing the notification's
  key. The app label, conversation title, category and channel id are kept.
- Skipped as noise: ongoing and foreground-service notifications, progress bars, media controls, and
  group summaries (a summary is dropped when its children are in the shade or when it only counts).
- An identical re-post of the same notification is one row. Removal from the shade sets
  `removedTime` on the existing rows; it never adds a row.
- Rows are pruned by age (`app_notification_history_retention_days`, 1 to 365, default 30), when
  something is written and at most once an hour.
- Codes are masked at write time while `app_notification_history_mask_codes` is on (the default): a
  4-8 digit run next to a word like code, OTP, verification, passcode, one-time or PIN is stored as
  `••••••`.

The history lives in `~/.launcherctl/launcher.db`, readable by anything running as the app's user;
that is why it is opt-in per app. The older `notifications.jsonl` mirror is gone and is deleted
the first time the new store opens.

### Device routes: vibrate, torch, battery, volume, toast, wallpaper

| Method | Path | Body | Answer |
| --- | --- | --- | --- |
| POST | `/v1/vibrate` | `{"duration_ms": 1000, "force": false}` (both optional) | `{"ok": true, "duration_ms": 1000, "force": false}` |
| POST | `/v1/torch` | `{"on": true}` | `{"ok": true, "on": true, "camera": "0"}` |
| GET | `/v1/battery` | none | `{"health": "GOOD", "percentage": 87, "plugged": "UNPLUGGED", "status": "DISCHARGING", "temperature": 29.5, "current": -312000}` |
| GET | `/v1/volume` | none | `{"ok": true, "streams": [{"stream": "music", "volume": 7, "max_volume": 15}, …]}` |
| POST | `/v1/volume` | `{"stream": "music", "volume": 7}` | `{"ok": true, "stream": "music", "volume": 7, "max_volume": 15}` |
| POST | `/v1/toast` | `{"text": "hi", "short": false}` | `{"ok": true, "length": 2}` |
| POST | `/v1/wallpaper` | `{"path": "/sdcard/a.jpg", "target": "both"}` (`target`: `home`, `lock`, `both`; default `both`) | `{"ok": true, "target": "both", "width": 2400, "height": 1080, "launcher_refresh": "live"}` |
| POST | `/v1/wallpaper` | `{"builtin": "aurora", "target": "both"}` (`palette` is ignored; `path` and `builtin` are mutually exclusive) | `{"ok": true, "target": "both", "builtin": "aurora", "palette": "own", "animated": true, "reason": null, "launcher_refresh": "live"}` |
| GET | `/v1/wallpaper/builtins` | none | `{"ok": true, "builtins": [{"id": "aurora", "label": "Aurora", "palettes": ["own"]}, ...]}` |
| GET | `/v1/wallpaper` | none | `{"ok": true, "home_id": 12, "lock_id": 13, "live": false, "managed": true, "animated": "aurora", "palette": "own", "playing": true, "reason": null, "tier": 0, "kills": 0, "lock_slot": "same_as_home", "lock_motion": true, "lock_live": true, "desired_width": 1080, "desired_height": 2400}` (`lock_slot`: `same_as_home`, a background id, or `photo`; `lock_live`: the launcher's live wallpaper holds the lock screen) |

These are the answers `termux-vibrate`, `termux-torch`, `termux-battery-status`, `termux-volume`
and `termux-toast` give, so a compatibility script can pass them through. They need no pane and
no visible launcher; the same bearer token and a per-route rate limit apply (30 a minute for
vibrate and torch, 60 for toast and volume writes, 120 for battery and volume reads, 6 for wallpaper sets and 30 for the wallpaper read).

- `vibrate` calls `Haptics.vibrate(context, ms, force)`, so the user's haptics setting and
  silent mode apply unless `force` is true. Duration is capped at 10 s. 400 `bad_request` for a
  non-positive duration.
- `torch` uses the first camera that reports a flash, and needs no permission. 404 `no_torch`
  when no camera has one, 409 `torch_unavailable` when another app holds the camera.
- `battery` values are the `termux-battery-status` names: `health` is `GOOD`, `COLD`, `DEAD`,
  `OVERHEAT`, `OVER_VOLTAGE`, `UNSPECIFIED_FAILURE` or `UNKNOWN`; `plugged` is `PLUGGED_AC`,
  `PLUGGED_USB`, `PLUGGED_WIRELESS` or `UNPLUGGED`; `status` is `CHARGING`, `DISCHARGING`,
  `FULL`, `NOT_CHARGING` or `UNKNOWN`; `temperature` is degrees Celsius; `current` is
  microamperes (`CURRENT_NOW`, 0 when the device does not report it; the sign convention is the
  device's own).
- `volume` streams are `alarm`, `music`, `notification`, `ring`, `system` and `call`. The array
  `termux-volume` prints is wrapped under `streams`. A value above the maximum is clamped. 403
  `volume_refused` when Android refuses (ring and notification under Do Not Disturb).
- `toast` raises an in-app notice through `AppNotice` while the launcher is on screen and a
  stock system toast otherwise; long by default, `"short": true` for the brief one.
- `wallpaper` sets the system wallpaper through the same code the in-app picker uses
  (`ManagedWallpaper.apply`), so the launcher's stored wallpaper id and exact-picture copy follow
  and the glass treats it as its own picture; a wide picture is cut to the screen-sized centre
  exactly as after a pick. `path` must be absolute, readable, a decodable image of at most 48 MB,
  and under the Termux home or shared storage (`/sdcard`, `/storage/emulated`; symlinks are
  resolved first, so `~/storage/shared/...` works). Errors: 400 `bad_request`, 403
  `path_not_allowed` or `unreadable`, 404 `not_found`, 413 `too_large`, 415 `not_an_image`, 500
  `wallpaper_failed`. The call blocks until Android has taken the picture (seconds for a large
  one). It never needs the Activity: the wallpaper is always set. For `home` and `both` the
  launcher is also told to re-dress (wallpaper mode on, styling reload): `launcher_refresh` is
  `live` when the launcher is running, and `on_next_open` when it is not, in which case the
  reload happens the next time it opens. `lock` alone changes nothing in the launcher. `GET`
  reports the current ids, whether a live wallpaper is on, and whether the home wallpaper is the
  one the launcher set (`managed`).
- `wallpaper` with `builtin` sets one of the launcher's generated backgrounds (ids from
  `GET /v1/wallpaper/builtins`) instead of a photo. The still is its rest pose, rendered with the
  chosen palette at the size the photo cropper writes and applied through the same code as a
  photo, so the exact copy and stored id follow. `palette` is ignored; the wallpaper always uses
  its own colours and the system theme follows it. Rendering
  needs Android 14 (API 34): below it the call is 409 `unsupported` with `"reason": "api"`. On API
  34 or later the still is always set, and `animated` says whether the live frames will play: they
  need Fancier Glass active, otherwise `"animated": false, "reason": "fancier_glass_off"` (or
  `killed` when the hidden `animated_wallpaper_disabled` preference is on). Errors: 400
  `bad_request` (both `path` and `builtin`, or a bad `palette` or `target`), 404 `not_found` for an
  unknown id, 500 `wallpaper_failed`. Setting a `path` afterwards forgets the generated
  background. `GET /v1/wallpaper` adds `animated` (the id or null), `palette` (mode or null),
  `playing`, and `reason` (`api`, `fancier_glass_off`, `paused`, `killed`, `inactive`, or null while
  playing); with no launcher screen registered it reports `playing: false`, `reason: "inactive"`.
  Set from `launcherctl` with no launcher screen, Material colours come from the application
  theme rather than the visible scheme.

```sh
launcherctl vibrate -d 200 --force
launcherctl torch on; launcherctl torch off
launcherctl battery
launcherctl volume                 # list every stream
launcherctl volume music 7
launcherctl toast --short 'build done'
launcherctl wallpaper set ~/pics/a.jpg --lock   # --home | --lock | --both (default)
launcherctl wallpaper set --builtin aurora --palette own --home
launcherctl wallpaper list-builtins
launcherctl wallpaper get
```

### OpenAI-compatible

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/v1/models` | List installed, loadable models with On-device AI metadata |
| GET | `/v1/models/{id}` | Return one model object (filtered from `/v1/models`) |
| POST | `/v1/chat/completions` | Chat completions: text, image/audio input, tools, SSE streaming |
| POST | `/v1/responses` | Stateless OpenAI Responses adapter (text/image input, function calls/results) |
| POST | `/v1/completions` | Legacy text completions, SSE streaming |
| POST | `/v1/embeddings` | Embeddings for models advertising `text_embeddings` |
| POST | `/v1/tokenize` | `{model, input}` in, `{tokens: n}` out, using the model's own tokenizer |
| POST | `/v1/audio/transcriptions` | Speech to text with the voice input speech model: multipart `file` (WAV or raw 16 kHz PCM16), `model`, `language`, `prompt`, `response_format` `json`\|`text`\|`verbose_json`; see [Voice input](Voice_Input.md) |
| POST | `/v1/audio/speech` | Speech output with the voice model (KittenTTS): `wav` whole, `pcm` streamed per sentence; see [Text to speech](Text_To_Speech.md) |
| POST | `/v1/ai/speak` | `tai speak`: plays the text on the phone (JSON or plain-text body; `?voice=&speed=`; `?format=wav` returns audio instead) |
| POST | `/v1/ai/speak/stop` | Stops whatever the phone is reading aloud |
| POST | `/v1/ai/images/generations` | Text to image with an MNN diffusion model (Stable Diffusion 1.5, Taiyi, Sana); see [`POST /v1/ai/images/generations`](#post-v1aiimagesgenerations) |
| POST | `/v1/ai/images/cancel` | Discards the image generation in flight (the engine cannot stop mid-run) |

OpenAI `/v1/*` streaming uses Server-Sent Events (`text/event-stream`) and ends with `data: [DONE]`.

#### `GET /v1/models` metadata

Each entry in the standard OpenAI-shaped `data` array includes On-device AI metadata prefixed with an underscore so existing OpenAI clients ignore it:

- `_backend`: backend routing for the model, currently `litert-lm` (default LiteRT-LM runtime) or `mnn-llm` (bundled MNN backend).
- `_capabilities`: ordered list of endpoint capability strings, for example `text_chat`, `image_input`, `audio_input`, `tool_use`, or `code`. This is what the installed APK can currently serve and is identical to `_endpoint_capabilities`.
- `_source_capabilities`: informational upstream/package capabilities. Clients should not treat these as enabled endpoint features.
- `_default_max_output_tokens`, `_endpoint_context_window`, and `_source_context_window`: runtime default, the context window served on this device, and the model's own limit. The endpoint window grows with device RAM up to the model's limit and follows the **Context window** setting when one is set; see [On-device AI backends](On_Device_AI_Backends.md#context-window-sizing).
- `_tool_mode`: present for tool-capable models. MNN tool support is `prompt_fallback` (the model's own chat template renders the tools when it can, the server's prompt otherwise); LiteRT tool support is native when advertised.

`GET /v1/models/{id}` returns the single matching object (HTTP 404 if unknown).

#### `POST /v1/embeddings`

OpenAI-compatible embeddings endpoint. Only models that advertise `text_embeddings` in their `/v1/models` `_capabilities` array are accepted; others return `capability_not_supported`. `input` may be a string or an array of strings (at most `_endpoint_max_batch` entries, currently 64; a larger batch returns `413 batch_too_large` naming the limit, never a silent drop), and the response returns one OpenAI `embedding` item per input in the same order.

Request fields beyond the OpenAI basics (`model`, `input`, `dimensions`):

- `input_type`: `"query"` or `"document"` (default `"document"`). EmbeddingGemma was trained with a task prefix on every input; the server adds it, counted inside the model's window: `task: search result | query: ` for a query, `title: <title or none> | text: ` for a document. Other embedding families ignore this field.
- `title`: an optional document heading (for example a note's title), folded into the document prefix in place of `none`. Ignored for `input_type: "query"`.
- `encoding_format`: `"float"` (default) or `"base64"` (standard base64 of the vector's little-endian float32 bytes, OpenAI's shape).

Each `data[i]` also reports `tokens` (the token count before any truncation; body and prefix combined, BOS/EOS excluded) and `truncated` (whether the body had to be cut to fit the model's window — the prefix itself is never the part that is cut). A long input is trimmed, never a 500.

Dawn brief items 5 and 6, useful for a client that indexes in the background: while a chat generation is running elsewhere in the process, embeddings run throttled (background thread priority) so they do not slow the live reply; every embedder's `/v1/models` entry states this policy as `_endpoint_throttle_while_generating: "priority"`, and `/v1/ai/runtime` `runtime.activeGeneration` says when it applies. A load that cannot fit in memory returns `503` with a `Retry-After` header and `code: "embedding_memory"`, distinct from the `429`/`Retry-After` a request over the 60/minute rate limit gets.

LiteRT EmbeddingGemma `.tflite` installs require `sentencepiece.model` in the same model directory. New downloads fetch that sidecar automatically. Older installs that only contain the `.tflite` return `embedding_tokenizer_missing` until the model is re-downloaded or the sidecar is added.

The embedder's `/v1/models` entry additionally carries `_endpoint_dimensions` (the model's native output width; recognised families only), `_endpoint_matryoshka_dims` (the only sizes `dimensions` accepts when listed, largest first; any other size gets `400 invalid_dimensions`), `_endpoint_normalized` (`true`: every vector is L2-normalised), `_endpoint_max_batch`, and a stable `_revision` (a cheap hash of the model file's name/size/mtime, never its bytes) a client can use to know when to rebuild its index.

EmbeddingGemma ships fixed-shape graphs per context window (`seq256`, `seq512`, `seq1024`, `seq2048`); every input costs the full window regardless of how short it is. When a new EmbeddingGemma download fetches a graph bigger than `seq512`, it also fetches the smaller `seq256`/`seq512` siblings alongside it, best effort. When more than one window graph is installed next to each other, TAI routes each input independently to the smallest installed window it fits in (prefix + body + BOS/EOS), only falling back to the largest window — and truncating the body, as always — when nothing fits; this is transparent to the request and response shape. `_endpoint_context_window` reports the largest installed window, and a new field, `_endpoint_windows`, lists every installed window ascending, for example `[256, 512, 1024]`, so a client can tell a single-graph install (`_endpoint_windows` has one entry) from a multi-window one.

#### `POST /v1/ai/images/generations`

Text-to-image through the MNN Diffusion engine, on the GPU (OpenCL) by default. The body follows OpenAI's
image generation shape, with a few additions:

| Field | Meaning |
| --- | --- |
| `model` | A registered image model id. Exactly one of `model` and `model_path`. |
| `model_path` | A model folder, under Termux home or shared storage: the way to try a package before it can be imported. |
| `model_type` | `sd15` (default for the Stable Diffusion file layout), `taiyi` or `sana`. Taiyi shares Stable Diffusion's files, so it must be said. |
| `prompt` | Required, up to 2000 characters. |
| `size` | `"WxH"`. Stable Diffusion and Taiyi: `512x512` only (the engine is fixed). Sana: multiples of 32 from 256 to 2048. Default `512x512`. |
| `n` | Only `1`. |
| `steps` | 1-100, default 20. |
| `seed` | Whole number; `-1` (default) picks one, reported back in `tai.seed`. |
| `cfg_scale` | Sana only; default 4.5, `0` turns guidance off. Stable Diffusion's guidance is fixed inside the engine. |
| `image` | Sana only: an input picture to edit. Needs `vae_encoder.mnn` in the package. |
| `backend` | `opencl` (default) or `cpu`. |
| `memory_mode` | `0` saves memory, `1` is fastest and keeps the model loaded (Stable Diffusion/Taiyi), `2` balances. Left out, the fastest mode whose estimate fits the memory free now is chosen, closing idle embeddings, speech models or an idle chat model if that is needed. |
| `output` | A `.png` path under Termux home or shared storage: the image is written there and `data[0].path` returned instead of base64. |
| `response_format` | `b64_json` (the only one). |
| `stream` | `true` answers with SSE progress events. |

Response: `{created, data: [{b64_json | path}], tai: {model, width, height, steps, seed, backend, memoryMode, modelType, loadMs, generateMs, evicted?}}`.
With `stream: true` each event is `{type: "image_generation.progress", progress: 0-100}`, then one
`{type: "image_generation.completed", ...}` carrying the response above (or an error object), then `data: [DONE]`.
A request that cannot run is refused with its own status before any stream starts: `400` for a bad field
(`missing_prompt`, `unsupported_n`, `invalid_size`, `invalid_steps`, `invalid_backend`, `invalid_memory_mode`,
`image_input_unavailable`), `400` with `tokenizer_mtok_missing` for a package that ships the raw tokenizer
(`vocab.json`/`merges.txt`) instead of `tokenizer.mtok`, `403` for a path outside the allowed roots, `404` for an
unknown model or folder, `409 image_generation_active` while another image is being made, `409 insufficient_memory`,
`501 mnn_image_unavailable` when the installed native library predates image support.

Only one image is generated at a time, on a lane of its own: chat and speech are not queued behind it.
The engine ignores the progress callback's return value, so a run cannot be stopped part-way:
`POST /v1/ai/images/cancel` (and a client that disconnects) discards the result when the engine returns.
Image models never appear in `/v1/models` or the chat lists, and `tai load` answers `400 image_model_not_loadable`.

`tai image "prompt" [--model ID | --model-dir DIR] [--type sd15|taiyi|sana] [--out FILE.png] [--steps N] [--seed N] [--size WxH] [--cfg X] [--image IN.png] [--cpu] [--memory-mode 0|1|2]`
calls this route (progress on stderr, the PNG saved to `--out`, default `./tai-image-<time>.png`; Ctrl-C and `tai image --stop` cancel; `tai --json image ...` prints the response).

#### `POST /v1/tokenize`

`{model, input}` in, `{tokens: n}` out: the installed embedding model's own tokenizer, with no task prefix and no BOS/EOS framing added — just the raw count, so a client can split long text on real token counts instead of estimating from characters. Only the LiteRT/EmbeddingGemma path exposes a tokenizer today; other backends return `capability_not_supported`.

### Ollama-compatible

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/api/version` | Compatibility version |
| GET | `/api/tags` | List installed models |
| POST | `/api/show` | Show one model's details and capabilities |
| GET | `/api/ps` | Show the loaded model |
| POST | `/api/chat` | Chat and tool calls, NDJSON streaming |
| POST | `/api/generate` | Prompt-style generation, NDJSON streaming |
| POST | `/api/embed` | Create embeddings when supported |
| POST | `/api/embeddings` | Legacy alias: `{model, prompt}` in, `{embedding: [...]}` out |
| POST | `/api/pull` `/api/create` `/api/push` `/api/copy` `/api/delete` | Return HTTP 501 (not emulated) |

Ollama `/api/chat` and `/api/generate` stream newline-delimited JSON (NDJSON) by default. Ollama registry operations (`pull`, `create`, `push`, `copy`, `delete`) are not emulated because Ollama/GGUF packages are not LiteRT-LM or MNN packages. Install models from the On-device AI catalog or import flow instead.

### Model management

These routes are used by the `tai` CLI and the Settings UI. They share the same auth as the inference routes.

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/v1/ai/status` | Overall status, settings, and limitations |
| GET | `/v1/ai/runtime` | Loaded model and runtime state |
| GET | `/v1/ai/models` | Detailed On-device AI model registry |
| GET | `/v1/ai/models/downloads` | Show download progress/history |
| POST | `/v1/ai/models/import` | Register a supported local package |
| POST | `/v1/ai/models/download` | Download a model from a URL |
| POST | `/v1/ai/models/download-catalog` | Download a catalog model |
| POST | `/v1/ai/models/downloads/cancel` | Cancel a download and delete its partial file |
| POST | `/v1/ai/models/downloads/pause` | Pause a download, keeping its partial file |
| POST | `/v1/ai/models/downloads/resume` | Continue a paused, failed or cancelled download from the bytes it has |
| POST | `/v1/ai/models/downloads/prioritize` | Move a queued download to the front ("start now") |
| POST | `/v1/ai/models/delete` | Delete an installed user model |
| POST | `/v1/ai/models/load` | Load a model into the registry slot |
| POST | `/v1/ai/models/unload` | Unload a model from the registry slot |
| POST | `/v1/ai/runtime/preflight` | Check whether a model can load safely |
| POST | `/v1/ai/runtime/load` | Load a model into the active runtime |
| POST | `/v1/ai/runtime/unload` | Unload the active model |
| POST | `/v1/ai/runtime/keep-warm` | Keep a model loaded temporarily |
| POST | `/v1/ai/runtime/cancel` | Cancel active generation |
| POST | `/v1/auth/rotate` | Rotate the API token and rewrite discovery files |

`POST /v1/ai/runtime/preflight` checks ABI, API level, bundled native libraries, model package readability/format, memory, accelerator policy, and known backend history without touching native LiteRT-LM/MNN runtime code.

## Streaming Notes

- OpenAI endpoints (`/v1/chat/completions`, `/v1/completions`, `/v1/responses`) use SSE (`text/event-stream`) and terminate with `data: [DONE]`.
- Ollama endpoints (`/api/chat`, `/api/generate`) use NDJSON: one JSON object per line, final object flagged `done: true`.
- Non-streaming requests return a single JSON body.

## Rate Limiting

Each protected route has its own token-bucket rate limiter. When a bucket is exhausted the server returns HTTP `429 Too Many Requests` with a `Retry-After: <seconds>` header indicating when the bucket refills. The error body uses the standard error envelope (see below).

Limits are per-route, not global, so heavy generation traffic does not starve unrelated management calls.

## Error Envelope

OpenAI-style routes (`/v1/*`) return a nested OpenAI error object:

```json
{
  "error": {
    "message": "model not loaded",
    "type": "model_not_loaded",
    "code": null
  }
}
```

Ollama-style routes (`/api/*`) return a flat error string in the Ollama convention:

```json
{
  "error": "model not loaded"
}
```

Rate-limit errors use `type: "rate_limit_error"` on `/v1/*` and the same flat `error` string on `/api/*`. HTTP status codes follow OpenAI/Ollama conventions (`400`, `401`, `404`, `409`, `429`, `500`, `501`).

## Terminal LLM Client Configuration

On-device AI exposes OpenAI-compatible HTTP endpoints so terminal clients such as `aichat`, `aider`, `tmuxai`, or any tool that reads `OPENAI_BASE_URL` / `OPENAI_API_KEY` can drive the local model runtime.

Default bind mode is `localhost` (server bound to `127.0.0.1`). With the **Require API token** setting on (default), the bearer token is required for every protected request. Token and endpoint URL are written to:

```sh
~/.launcherctl/token
~/.launcherctl/endpoint
```

Pass the endpoint as `OPENAI_BASE_URL` with `/v1` appended:

```sh
BASE=$(sed -n '1p' ~/.launcherctl/endpoint)
TOKEN=$(cat ~/.launcherctl/token)
export OPENAI_BASE_URL="$BASE/v1"
export OPENAI_API_KEY="$TOKEN"
```

Do not echo `$TOKEN` into shell history. Prefer reading it from the file at call time or storing it in a credentials manager.

If you turned **Require API token** off for localhost use, any placeholder key works:

```sh
export OPENAI_API_KEY="local"
```

### Example: list and call a LiteRT model

```sh
MODEL=$(curl -fsS -H "Authorization: Bearer $TOKEN" \
  "$OPENAI_BASE_URL/models" | jq -r '.data[] | select(._backend=="litert-lm") | .id' | head -n1)
curl -fsS -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d "{\"model\":\"$MODEL\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}" \
  "$OPENAI_BASE_URL/chat/completions"
```

### Example: call an MNN model

MNN models route through the bundled MNN backend. Only models whose `_backend` field equals `mnn-llm` should be requested via MNN. Current MNN catalog models are chat/code models; image, audio, and embeddings requests are rejected unless the model advertises that endpoint capability.

```sh
MODEL=$(curl -fsS -H "Authorization: Bearer $TOKEN" \
  "$OPENAI_BASE_URL/models" | jq -r '.data[] | select(._backend=="mnn-llm" and (._capabilities | index("text_chat"))) | .id' | head -n1)
[ -n "$MODEL" ] && curl -fsS -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d "{\"model\":\"$MODEL\",\"messages\":[{\"role\":\"user\",\"content\":\"Reply exactly OK\"}]}" \
  "$OPENAI_BASE_URL/chat/completions"
```

Inspect `/v1/models` first to confirm both `_backend == "mnn-llm"` and the endpoint capability you intend to use are present.

## Security Model

### Attack Surface
- Localhost API reachable from local device processes.
- Token theft enables API calls.
- Inference output may reflect model/package content.

### Bind Mode
- Default: `localhost` — server bound to `127.0.0.1`. Only processes on the device can reach the API.
- Opt-in: `lan` — server bound to `0.0.0.0`. Any device on the local network can reach the API. **LAN mode always requires the token** even when the localhost token-optional toggle is off.
- LAN exposure is time-boxed: 12 hours after it is enabled the server rebinds to `127.0.0.1` and rotates the API token. Requests arriving after the window closes get `403 lan_session_expired`. The endpoint settings JSON carries `lanSessionStartedAt`, `lanSessionExpiresAt`, and `lanSessionExpired`.

### Mitigations Implemented
- Bearer token auth (or `X-Api-Key`), startup-generated random token.
- Constant-time token comparison.
- Token-optional toggle only loosens localhost auth; LAN always enforces.
- Bounded worker pool (prevents unbounded thread growth).
- HTTP parser limits:
  - request line size,
  - header line size/count,
  - max body size.
- Per-route token-bucket rate limiting (`429` with `Retry-After`).
- Token rotation endpoint.
- Sensitive files written owner-only.
- CORS grants only to loopback origins (echoed back, never a wildcard); cross-origin browser requests are refused outright.
- `Host` header validated against the bound address, which closes the DNS-rebinding path into the loopback listener.
- LAN exposure expires after 12 hours and rotates the token on the way out.
- Media referenced by chat requests is bounded: local paths must resolve under the Termux home or shared storage (never `~/.launcherctl`), and remote URLs must resolve to public addresses, re-checked at every redirect hop.

### LAN Opt-In Considerations
- LAN mode (`bindMode: lan`) is opt-in and surfaces a `lanWarning` field in the endpoint settings JSON plus the `tai` CLI help text.
- Treat the bearer token as a network secret whenever LAN mode is active. Do not paste it into shell history, screenshots, or shared notes.
- Rotate the token (`POST /v1/auth/rotate`) after temporarily enabling LAN mode if the token may have been observed.
- A firewall on the LAN, a per-call `Authorization: Bearer <token>` header, and short-lived sessions are recommended for any non-trivial LAN use.
- LAN mode is unencrypted: requests are plain HTTP, so the bearer token is visible to anyone on the network. Treat every LAN session as one where the token has already been observed — that is why the window expires and rotates on its own.

### Remaining Security Considerations
- Localhost token auth still depends on local process trust.
- If same app UID ecosystem is compromised, token can be read.
- LAN mode trusts every device on the local network; it does not implement per-device authentication, and it carries the token in cleartext.
- Consider Unix domain sockets for tighter local access boundaries in future.
- The pane routes' exposure window is now the launcher process's lifetime, not just its time on
  screen. This does not add capabilities — ownership still confines `write`/`text`/`close` to panes
  opened through the API, and nothing here can bring the launcher to the Android foreground on its
  own — but the same token now reaches a pane for longer.
- The signal routes give the token what any program in a shell already has through escape
  sequences, no more: the clipboard read keeps the on-screen requirement and the read setting
  that gate `OSC 52` (a write works off screen, as Termux:API's always has), and a notification or a progress ring is something the user sees, not
  something the caller learns.

## Troubleshooting

### Token errors (`401`)
- Read the current token: `cat ~/.launcherctl/token`.
- Rotate it with `POST /v1/auth/rotate` or from **Settings → On-device AI → Endpoint & access → Recreate token**, then re-run your command.
- If you turned **Require API token** off, confirm you are still on `localhost` bind mode — LAN mode always requires the token.

### `Connection refused`
- Reopen Termux Launcher so the server starts.
- Check `~/.launcherctl/endpoint` for the current port.
- Run `tai status`.

### Rate limited (`429`)
- Wait for the `Retry-After` interval and retry.
- Space out polling loops; limits are per-route.

## Performance Notes

- The server is request-driven and avoids polling loops.
- Inference is offloaded to the isolated `:tai_runtime` process; the launcher UI/API process stays alive if the native runtime crashes.
- Only one chat/generation model is resident at a time; switching models unloads the previous one.
