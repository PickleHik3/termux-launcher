# Programs and agents inside the terminal

You are running inside **Termux Launcher**, a fork of Termux that is also the Android home screen.
Everything you know about Termux holds: same `$PREFIX`, same `pkg`, same package `com.termux`
(`com.termux.launcher.nix` on the Nix edition, `io.vaj.tl` on the VAJ demo edition). This page is
the delta: what this terminal can do that stock Termux cannot, and the two things that bite.

## Know where you are

Every shell gets these on top of the stock Termux environment:

| Variable | Value | Use it for |
| --- | --- | --- |
| `TERM_PROGRAM` | `termux-launcher` | Branch on the launcher instead of sniffing `TERM` |
| `TERM_PROGRAM_VERSION` | installed version | Feature-gate on a release |
| `TERMUX_LAUNCHER_PANE` | id of the pane you run in | `launcherctl` reports and pane calls default to it |
| `COLORTERM` | `truecolor` | 24-bit colour is real |

`TERM` stays `xterm-256color`; the `terminal-term` property can switch it to `xterm-kitty` (see
[Kitty protocols](Terminal_Kitty_Protocols.md#terminal-identity-and-detection)). XTVERSION answers
`termux-launcher(<version>)`. A tool that only trusts `TERM_PROGRAM=kitty` for pictures needs that value for its own process; the recipes are in [Telling a TUI it is in kitty](Terminal_Kitty_Protocols.md#telling-a-tui-it-is-in-kitty).

## Two things that bite

- **Never relaunch the app with `am start -n com.termux/.app.TermuxActivity`.** The launcher is the
  home task; a plain `am start` creates a second instance and the two tear each other down. To bring
  a pane forward use `launcherctl pane focus <id>`; to open something use `launcherctl launch`.
- **Panes are the unit, not sessions.** A window holds split and floating panes; each pane is one
  shell. `launcherctl pane list` shows the tree. Panes you open through `launcherctl` are yours to
  write, read and close; everyone else's you can only list and focus.

## What the terminal understands beyond stock Termux

Send these and they work. Details and limits live in [Kitty protocols](Terminal_Kitty_Protocols.md).

| Sequence | What happens |
| --- | --- |
| Kitty graphics (`ESC _ G`), direct, file (`t=f`) and temp-file (`t=t`) media, animation, unicode placeholders | Pictures in the pane; `timg -pk`, `kitten icat`, image.nvim, md-render.nvim |
| Sixel, iTerm inline images (OSC 1337) | Same, older protocols |
| Kitty keyboard protocol (`CSI > flags u`) | Exact modifiers, key release, disambiguated Esc |
| OSC 8 | Clickable links |
| OSC 52 | Write the Android clipboard |
| OSC 133 A/C/D | Prompt marks; the window chip shows whether the foreground is a command or an idle prompt |
| OSC 7 `file:///path` | New panes opened from this one start in that folder |
| OSC 9;4;state;pct | Progress ring on the window chip (ConEmu style) |
| OSC 9 / OSC 777 / OSC 99 | Notifications. 9 and 777 are one line; 99 is kitty's: title, body, urgency, chunked, tap returns to the pane |
| OSC 22 | Pointer shape for a hardware mouse |
| Private mode 2026 | Synchronized output: hold redraws, paint once |
| Private mode 2048 | In-band resize reports with cell and pixel sizes |
| Private mode 2031 | Dark/light theme change notifications |
| `CSI # P` / `# Q` / `# R` | Push, pop, report the colour palette |
| DECSCUSR, SGR 4:3 curly and coloured underlines, focus events, bracketed paste, XTWINOPS 14/16 | As in kitty |

Not there: kitty text sizing (OSC 66), kitty file transfer (OSC 5113), shared-memory image
transfer (Android has no `shm_open`; fall back to `t=f`).

The shell integration that emits the OSC 133 and OSC 7 marks is written to
`~/.termux/shell-integration/termux-launcher.{bash,zsh}` on startup; source the one for your shell if
the prompt marks are missing.

A notification from a script, kitty style:

```sh
printf '\033]99;i=1:d=0:p=title;Build finished\033\\'
printf '\033]99;i=1:d=1:p=body;42 tests passed\033\\'
```

## launcherctl: drive the launcher from the shell

`$PREFIX/bin/launcherctl` wraps a localhost HTTP API. The app writes the endpoint and bearer token to
`~/.launcherctl/endpoint` and `~/.launcherctl/token` on startup; output is JSON.

```sh
launcherctl launch <app name, package or activity>         # open an Android app
launcherctl pane list | open [--cwd DIR] [--title NAME] [--no-focus] [-- CMD...] | focus <id> | write <id> [--enter] <text> | read <id> [--lines N] | close <id>
launcherctl agent working|blocked|idle|clear [--pane ID]    # what the chips say about you
launcherctl agent install-hooks                             # wires the Claude Code hooks that send those states
launcherctl keyboard show|hide [--source focus]             # the in-app keyboard
launcherctl x11 gpu [--env]                                 # the GPU row that fits this phone
```

`launcherctl agent` is how an AI coding agent tells the window chips and the sessions browser that
it is **Working**, **Needs you** or **Idle**, so a pane waiting on an answer is visible from any
place. Claude Code users run `install-hooks` once. Full route tables, auth and the OpenAI and Ollama
compatible model endpoints: [LauncherCtl API](LauncherCtl_API.md).

## Graphics, tools, models

- **Linux display.** `termux-x11 :0 &` then `export DISPLAY=:0`; the Display place shows it.
  `launcherctl keyboard show --source focus` raises the keyboard when an X text field takes focus.
  [The Linux display](X11_Display.md).
- **tlstore** is the launcher's own tool store: `tlstore list`, `tlstore install <name>`,
  `tlstore doctor`. It never replaces a config file silently. [Tlstore](Tlstore.md).
- **Local models.** `tai` and an OpenAI/Ollama compatible endpoint at the same base URL as
  launcherctl. [Termux AI](Termux_AI.md).
