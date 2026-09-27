---
name: termux-launcher
description: Use when running as a coding agent (Claude Code, opencode) inside Termux Launcher (TERM_PROGRAM=termux-launcher) and you want to show something in a pane or window, notify the user, report progress, set your working/blocked/idle status, open an app or a browser on the display, or use terminal graphics/text features like kitty images or sized text.
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
`io.vaj.tl`) if you need to branch on that.

## Three rules

1. **You own only what you open.** `launcherctl pane`/`window` `write`, `read` and `close` work
   only on panes/windows *you* opened — every other pane, including the user's own shells,
   answers 403 `not_owned`. `list` and `focus` work on anything.
2. **Draw in your own pane, never in the one you're running in.** Anything that puts pixels or
   sized text on the grid (an image, `kitten icat`, OSC 66) belongs in a pane you open with
   `launcherctl pane open`. Writing it into your own pane corrupts whatever your shell or a TUI is
   showing there. Out-of-band signals that don't draw (a notification, the progress ring) are
   fine to send to your own pane's tty — see `terminal.md` for exactly why the difference matters
   and how (your shell tool's stdout is a pipe, not the terminal).
3. **Never relaunch the app** with `am start -n com.termux/.app.TermuxActivity` — it spins up a
   second instance that fights the running one. Use `launcherctl launch` or `pane focus` instead.

## Where to look

| You want to... | Read | Or just run |
| --- | --- | --- |
| Open/drive a pane or window, launch an app, report agent status, show/hide keyboard | `launcherctl.md` | `launcherctl pane open --no-focus -- <cmd>` |
| Notify the user | `terminal.md` | `sh "<skill dir>/scripts/notify.sh" [--title T] "body"` |
| Show progress | `terminal.md` | `sh "<skill dir>/scripts/progress.sh" 42` (or `clear`, `error`) |
| Show an image | `terminal.md` | `sh "<skill dir>/scripts/show.sh" out.png` |
| Use kitty graphics/text-sizing/links/clipboard directly | `terminal.md` | — |
| Open a browser or Linux app on the display | `display.md` | `launcherctl launch firefox` |

`<skill dir>` is wherever this skill is installed (typically `~/.claude/skills/termux-launcher/`).
Run scripts as `sh "<skill dir>/scripts/x.sh"` — they're POSIX sh, no shebang dependency assumed.

## launcherctl in one breath

A CLI on `PATH`, JSON out, exits 1 on an HTTP error:

```sh
launcherctl launch <app>
launcherctl pane open|list|write|read|focus|close ...
launcherctl window open ...
launcherctl agent working|blocked|idle|clear
launcherctl keyboard show|hide
launcherctl x11 gpu
```

Full syntax, exact flags, error codes and rate limits: `launcherctl.md`.

## Manners

- One or two panes, not a wall. Open one to show something, `pane close` it when done.
- Never `write` secrets into a pane — it's visible on screen.
- Clear a progress ring when you're actually done; don't leave it spinning.
- No screenshot exists anywhere in this stack — if you open a browser for the user, say plainly
  that you can't see what it renders.
- If `launcherctl agent install-hooks` has already wired Claude Code's hooks, your working/idle/
  blocked status reports itself — don't also spam `launcherctl agent` calls by hand.
