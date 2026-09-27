# Terminal features: graphics, notifications, text

Depth: `docs/en/Terminal_Kitty_Protocols.md`, `docs/en/Terminal_Modernization.md`,
`docs/en/Programs_Inside_The_Terminal.md`.

## The one constraint that shapes everything here

Your shell tool captures stdout/stderr into a pipe — it does **not** write to the pane's real
terminal device. A bare `printf '\033]99;...'` from a tool call goes nowhere. Two different
answers follow, depending on what the escape does:

- **Out-of-band** escapes (a notification, the progress ring, an OSC 52 clipboard write) don't
  draw anything on the grid — they're safe to send straight to *your own pane's* tty device.
  `scripts/tty.sh` finds it (walks up the process tree from `$$` for the first ancestor whose
  fd 0/1/2 points at `/dev/pts/*`, falling back to `/dev/tty`); `notify.sh` and `progress.sh` use
  it already, so prefer those over hand-rolling the escape.
  **This only works when your tools run under your pane.** Claude Code's do. opencode 2 runs
  its tools in a background service (`opencode serve --service`, parent PID 1, stdin
  `/dev/null`) that belongs to no pane, so no tty is found and both scripts exit 1 with a
  message. Tell the user in your reply instead. `TERMUX_LAUNCHER_PANE` is also missing there,
  so `launcherctl agent` needs an explicit `--pane`. Checked on a device, 2026-09-27.
- **Anything that draws** (an image, OSC 66 sized text, styled TUI output) must **never** go to
  your own pane — even if you could reach its tty, you'd scribble over whatever your own output
  or a TUI is showing there. Draw in a pane you open for the purpose:
  `launcherctl pane open --no-focus --title … -- <cmd>` (see `launcherctl.md`). `scripts/show.sh`
  does this for a single image. For anything more (multiple sequences, a script driving a TUI),
  write a small script to a temp file and run *that* in the opened pane, e.g.:
  ```sh
  cat >"$TMPDIR/draw.sh" <<'EOF'
  #!/bin/sh
  printf '\033]66;s=2;Report\033\\\n'
  cat results.txt
  EOF
  launcherctl pane open --no-focus --title report -- sh "$TMPDIR/draw.sh"
  ```

## Detection

```sh
[ "$TERM_PROGRAM" = termux-launcher ] || exit 0   # branch on this, not on TERM
```

`TERM_PROGRAM_VERSION` carries the installed version; `TERM` stays `xterm-256color` unless the
user opted into `terminal-term = xterm-kitty`. `TERMUX_LAUNCHER_PANE` is this pane's id — the
same id the `launcherctl pane` and `agent` routes address. `COLORTERM=truecolor` always. XTVERSION
answers `termux-launcher(<version>)`.

A TUI that only turns on kitty features for `TERM_PROGRAM=kitty` (older Neovim image plugins) can
be told per-invocation: `TERM_PROGRAM=kitty nvim file.md`. Don't export it shell-wide — it breaks
your own and others' `TERM_PROGRAM=termux-launcher` checks and makes some tools assume kitty
features that aren't here (shared-memory image transfer, OSC 5113 file transfer, `kitten @`
remote control — none of those work).

## Notifications

```sh
sh "<skill dir>/scripts/notify.sh" [--title "Build"] "42 tests passed"
```

This sends Android-level notifications (OSC 99): title, body, chunked. They land in the system
notification shade with no icons or buttons, whether or not the launcher is visible. Prefer this
script over `printf` — it targets the right tty for you.

There is no separate "toast" route worth reaching for by hand: OSC 9 and OSC 777 are one-line
in-app toasts, shown only while the launcher is visible, and are strictly weaker than OSC 99.

## Progress ring

```sh
sh "<skill dir>/scripts/progress.sh" 42          # 0-100, on the window chip
sh "<skill dir>/scripts/progress.sh" error 87    # PCT optional, defaults to 0
sh "<skill dir>/scripts/progress.sh" clear       # done — always clear when you're finished
```

ConEmu-style `OSC 9;4;state;pct`. Clear it yourself when the task ends; a killed or forgotten
process otherwise leaves the ring spinning for work that's actually over.

## Graphics

Kitty graphics Tier 2 (direct PNG/RGBA, chunking, placements, animation, Unicode placeholders),
plus sixel and iTerm inline images (OSC 1337). No shared-memory transfer (`t=s` — Android has no
`shm_open`), no kitty file transfer (OSC 5113), no `kitten @` remote control.

```sh
sh "<skill dir>/scripts/show.sh" out.png       # opens a pane: kitten icat --hold out.png
```

Or directly, always in a pane you open (never your own):

```sh
launcherctl pane open --no-focus --title preview -- kitten icat --hold out.png
launcherctl pane open --no-focus --title preview -- timg -pk out.png
launcherctl pane open --no-focus --title preview -- chafa -f kitty out.png
```

`kitten` is installed via tlstore (`tlstore install kitten`); `show.sh` checks for it and fails
with a clear message if missing.

## Text sizing (OSC 66)

`ESC ] 66 ; s=2 ; Heading ESC \` draws text 2-7x larger. Draw-only — same rule applies: send it in
a pane you opened, never your own. Full syntax: `Terminal_Kitty_Protocols.md#text-sizing-osc-66`.

## Links, clipboard, prompts

- **OSC 8 hyperlinks**: only `http`, `https`, `mailto`, `tel`, `sms`, `geo`, `ftp`, `ftps` open on
  tap; other schemes (including `file://`) are copy-only. Emitting one from your own pane is
  safe — it's a link, not a drawn block.
- **OSC 52 clipboard**: writing always works; reading only works when the launcher is visible and
  the user has left **Settings → Terminal → Let programs read the clipboard** on. Don't rely on
  reading it.
- **OSC 133 / OSC 7**: prompt marks and cwd reporting, for shell integration — not something you
  need to emit yourself.

## Kitty keyboard protocol, mouse modes, DECSCUSR, sync output (mode 2026)

All supported if you're driving a TUI; programs negotiate these themselves, nothing to do by hand.
Mouse 1003 reports as 1002. Details: `Terminal_Kitty_Protocols.md`, `Terminal_Modernization.md`.

## What's not here

Kitty file transfer (OSC 5113), shared-memory image transfer, `kitten @` remote control,
multiple top-level Android windows. Note: despite what `Terminal_Modernization.md`'s "Current
limitations" section says near its end, Unicode placeholders, file transmission (`t=f`/`t=t`) and
desktop notification escapes (OSC 99) **are** implemented — that line is stale; trust the kitty
protocols page and this one.
