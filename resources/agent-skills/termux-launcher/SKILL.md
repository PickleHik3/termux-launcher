---
name: termux-launcher
description: Use when running as a coding agent (Claude Code, Codex, OpenCode, pi or any agent) inside Termux Launcher (TERM_PROGRAM=termux-launcher) and you want to run or show something in a pane or window and wait on its output, notify the user, report progress, set your working/blocked/idle status, open an app or a browser on the display, use terminal graphics/text features like kitty images or sized text, or use terminal-browser to show or drive a web page beside the user. Also use when the user asks for help setting up or configuring Termux Launcher, or asks how something in the launcher works: settings, keyboard, panes, widgets, on-device AI, tlstore, Shizuku, X11, or the Nix or VAJ edition.
---

# Termux Launcher

You're running inside Termux Launcher, an Android terminal that's also the home screen and tiles
panes like a tiling window manager. Everything you know about stock Termux still holds (same
`$PREFIX`, same `pkg`); this skill is the delta.

## Detect it

```sh
[ "$TERM_PROGRAM" = termux-launcher ] || exit 0
```

`TERMUX_LAUNCHER_PANE` (this pane's id), `TERM_PROGRAM_VERSION`, `COLORTERM=truecolor` are also
set. `$TERMUX_APP__PACKAGE_NAME` names the edition (`com.termux`, `com.termux.launcher.nix`,
`io.vaj.tl`) if you need to branch on that. Without `TERMUX_LAUNCHER_PANE` (opencode's tool
service, a cron job) you are not in a pane: commands about "this pane" need an explicit id.

## Learn the installed CLI

`launcherctl` on `PATH` drives the launcher. The installed script is the authority for syntax:
the launcher rewrites it on every start, so it always matches the launcher you are talking to.
Start with:

```sh
launcherctl --help          # every command, one line each, and the groups
launcherctl pane --help     # one group: usage, every flag, what it does
```

Any group takes `--help`: `launch`, `pane`, `window`, `agent`, `notify`, `progress`, `clipboard`,
`notifications`, `keyboard`, `vibrate`, `torch`, `battery`, `volume`, `toast`, `wallpaper`, `x11`.
A group that needs a command, run bare, prints its help and exits 2 without acting. Do not probe a
command by leaving arguments off: `launcherctl pane open` with nothing else is valid and opens a
pane. Output is JSON; a refusal prints its JSON and exits 1, a command-line mistake exits 2.

## Three rules

1. **You own only what you open.** `launcherctl pane`/`window` `write`, `read`, `wait`, `resize`
   and `close` work only on panes/windows *you* opened — every other pane, including the user's own
   shells, answers 403 `not_owned`. `current`, `get`, `list` and `focus` work on anything.
2. **Draw in your own pane, never in the one you're running in.** Anything that puts pixels or
   sized text on the grid (an image, `kitten icat`, OSC 66) belongs in a pane you open with
   `launcherctl pane open`. Writing it into your own pane corrupts whatever your shell or a TUI is
   showing there — and your shell tool's stdout is a pipe anyway, not the terminal. Things that
   don't draw (a notification, the progress ring, the clipboard) aren't escapes for you at all:
   `launcherctl notify|progress|clipboard` does them over the local API from any process.
3. **Never relaunch the app** with `am start -n com.termux/.app.TermuxActivity` — it spins up a
   second instance that fights the running one. Use `launcherctl launch` or `pane focus` instead.

## Where to look

| You want to... | Read | Or just run |
| --- | --- | --- |
| Run a command in a pane of your own and wait for its output | `launcherctl.md` → *Working in panes and windows* | `id=$(launcherctl pane open --print-id --no-focus)` |
| Know where you are (your window, your pane's size) | `launcherctl.md` | `launcherctl pane current` |
| Open/drive a pane or window, launch an app, report agent status, show/hide keyboard | `launcherctl.md` | `launcherctl <group> --help` |
| Notify the user | `launcherctl.md` | `launcherctl notify [--title T] "body"` (or `scripts/notify.sh`) |
| Show progress | `launcherctl.md` | `launcherctl progress 42` (or `clear`, `error`; `scripts/progress.sh`) |
| Put text on the clipboard | `launcherctl.md` | `launcherctl clipboard copy "text"` / `cmd \| launcherctl clipboard copy` |
| Show an image | `terminal.md` | `sh "<skill dir>/scripts/show.sh" out.png` |
| Use kitty graphics/text-sizing/links directly | `terminal.md` | — |
| Open a browser or Linux app on the display | `display.md` | `launcherctl launch firefox` |
| Show or drive a web page beside the user | *terminal-browser* below | `terminal-browser --help` |
| Answer how the launcher works or how to set it up | `help.md` | — |

`<skill dir>` is wherever this file lives. It is installed at `~/.agents/skills/termux-launcher/`,
with `~/.claude/skills/termux-launcher` linked to it.
Run scripts as `sh "<skill dir>/scripts/x.sh"` — they're POSIX sh, no shebang dependency assumed.

## Helping the user with the launcher

When the user asks how something in the launcher works or how to set it up, read the matching page
under `<skill dir>/docs/` before you answer; `help.md` says which page answers what. Answer from the
page, name the setting by its real on-screen path (Settings → … → …), and say so when the docs do
not cover it. Upstream Termux docs and your memory describe a different app.

## terminal-browser

When the user wants a web page shown beside the shell, or wants you to drive a browser, use
`terminal-browser` if `command -v terminal-browser` finds it; otherwise offer
`tlstore install terminal-browser`. It opens its own pane through the launcher's pane API, and
places it beside you when `launcherctl pane list` lists `placement` under `"features"`. Run
`terminal-browser --help` for its commands. Never draw a page into your own pane.

## Panes in one breath

Look at yourself first (`launcherctl pane current`), split right only when your pane is wide
(about 100 columns or more) and down otherwise, keep the user's focus (`--no-focus`), take the id
with `--print-id`, then `pane write --enter`, `pane wait`, `pane read`, and `pane close` when done.
The workflow, with the reasons, is in `launcherctl.md`. Before relying on a newer flag, check
`launcherctl pane list` for its name under `"features"`.

`notify`, `progress` and `clipboard` work from any process — no terminal, no tty, no
`TERMUX_LAUNCHER_PANE` needed (they fall back to the current pane). They are the same code the
OSC 99 / OSC 9;4 / OSC 52 escapes run, just reachable over HTTP.

## Manners

- One or two panes, not a wall. Open one to show something, `pane close` it when done.
- Never `write` secrets into a pane — it's visible on screen.
- Name panes by id. Never rely on whichever pane the user has focused.
- Clear a progress ring when you're actually done; don't leave it spinning.
- No screenshot exists anywhere in this stack — if you open a browser for the user, say plainly
  that you can't see what it renders.
- If `launcherctl agent install-hooks` has already wired Claude Code's hooks, your working/idle/
  blocked status reports itself — don't also spam `launcherctl agent` calls by hand.
