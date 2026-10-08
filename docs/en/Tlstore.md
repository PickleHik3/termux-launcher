# Tlstore

Tlstore is the launcher's own store for the tools and configs it shows off but does not ship in the
APK: a fish shell setup, a few terminal programs, three coding agents, and more, all installed and
kept up to date by one command. The app puts `tlstore` in place for you, along with the shorter
`tl` and `tls`; all three names run the same store.

## Quick start

```sh
tlstore install fish-shell
```

installs the whole fish setup in one go. Then:

```sh
tlstore install
```

opens a picker over every item you do not have yet. Inside the launcher, plain `tlstore` with
nothing after it opens the store in a window of its own.

## What's in the store

Ten items; each brings whatever it needs along with it. Ask about any of them first with
`tlstore info <name>`.

| Item | What you get |
| --- | --- |
| `btop` | A resource monitor that sees the whole phone, run as the shell user through [Shizuku](Shizuku.md#the-privileged-lane). Launcher only. |
| `claude-code` | Claude Code, Anthropic's coding agent for the terminal. About 200 MB. Sign in by running `claude`. |
| `codex` | Codex, OpenAI's coding agent, as built for Android by the codex-termux port. About 275 MB. Sign in by running `codex`. |
| `dawn` | A writing pad for the terminal: your markdown takes shape as you type. |
| `fastfetch` | System information beside an animated logo. Launcher only. |
| `fish-shell` | The fish shell with the launcher's setup: a prompt that follows your wallpaper colours, `eza`, `zoxide` and two plugins. |
| `kitten` | Kitty's companion tool, for showing images and sending files from the terminal. |
| `opencode` | opencode, an open source coding agent for the terminal. About 200 MB. |
| `sigye` | A clock for the terminal. |
| `termux-api-shims` | The Termux:API commands, working without the Termux:API app. See [Termux API commands](#termux-api-commands). Launcher only. |

The coding agents' own updaters are switched off; `tlstore update` is how they get new versions.
**dawn** asks the launcher's AI with `Ctrl+/`, using the model picked for **Assistant and
endpoint** in the [Model centre](On_Device_AI.md#functions), and finds notes by meaning through the
**Dawn notes integration** function.

The wallpaper-matching prompt theme and the Neovim colour scheme are not tlstore items: they are
set up from **Tools that follow the terminal colours** on the **Theme & fonts** page (search
Settings for it, or run **Look and feel settings** from the command palette). See
[Look and themes](Look_And_Themes.md).

## Commands

| Command | What it does |
| --- | --- |
| `tlstore list` | Everything in the store; `*` marks what you have. `-i` only installed, `-a` only not installed |
| `tlstore search fish` | Find an item by name or description |
| `tlstore info fish-shell` | What an item is, its version, and where it goes |
| `tlstore install kitten sigye` | Install items by name; with no name, open the picker |
| `tlstore remove kitten` | Remove an item tlstore installed |
| `tlstore update` | Bring everything up to date. `--check` only lists what is out of date; `--offline` uses the item list you already have |
| `tlstore rollback codex` | Go back one version and hold the item there |
| `tlstore hold codex` / `tlstore unhold codex` | Keep an item at its version when you update, or let it update again |
| `tlstore refresh` | Fetch the newest item list without changing anything installed |
| `tlstore display` | Set up graphics for Linux apps on the display |
| `tlstore doctor` | Check that everything is in place |
| `tlstore self-update` | Bring tlstore itself up to date (`--check` only says) |
| `tlstore help` | The list of commands; `tlstore help install` or `tlstore install --help` is one command's own page |
| `tlstore version` | The tlstore and item-list versions |
| `tlstore readme kitten` | Where a copy of the item's README is saved |

- `--dry-run` on `install`, `remove`, `update` and `rollback` shows what would happen and changes
  nothing.
- `-y` says yes to everything except replacing a config file of yours; `--configs` on `install`
  and `update` answers that one too, for scripts.
- `--tsv` on `list`, `search`, `info` and `update --check` prints tab-separated columns for a
  program to read.
- Exit status: 0 done, 1 something failed, 2 a mistake in what you typed.
- `tlstore shell` is gone; it is now `tlstore install fish-shell`.

## Going back a version

`tlstore rollback <name>` puts an item back at the version it had before its last update, then
holds it there so the next `tlstore update` does not undo it.

- A held item is skipped by `tlstore update` and marked in `tlstore list -i` and `tlstore info`.
- `tlstore update <name>` updates it anyway and releases the hold. `tlstore unhold <name>` releases
  it without updating.
- Rollback goes one step only, to a version tlstore recorded.
- Package items, bundles and fish plugins cannot go back.

## Your config files are never replaced silently

When tlstore is about to write a config file you already have and yours is different, it shows you
the change and asks, defaulting to no. Answering yes leaves a timestamped backup beside your file.
Either answer is remembered, so you are not asked again until that file itself changes. In a
script, your file is kept and one line says so.

## Termux API commands

`tlstore install termux-api-shims` puts the familiar Termux:API commands in `~/.local/bin`:
`termux-clipboard-get`, `termux-clipboard-set`, `termux-notification`,
`termux-notification-remove`, `termux-notification-list`, `termux-toast`, `termux-vibrate`,
`termux-torch`, `termux-battery-status`, `termux-volume` and `termux-wallpaper`. They print what the
originals print and take the same options, so scripts written for Termux:API run unchanged, with no
companion app. Each one calls a [launcherctl](LauncherCtl.md#termuxapi-commands-on-top-of-launcherctl)
command.

- Options the launcher cannot honour (notification buttons, sounds, LED colours, toast position)
  are accepted and skipped with a one-line warning.
- When the launcher is not running, the commands exit non-zero and say why.
- tlstore refuses to install them while the real `termux-api` package is installed; remove it
  first with `pkg uninstall termux-api`.

## Where things go

Everything tlstore installs lives under `~/.local` (programs in `~/.local/bin`, larger tools in
`~/.local/lib`), never in Termux's own `bin`, so a bootstrap reinstall never takes your tools with
it. Configs go where the program that reads them expects, under `~/.config`. The list of items is
signed, and tlstore only accepts a newer list whose signature checks out.

## More

Tlstore's full user guide (the store window's keys, the fish setup, which items are offered in the
launcher and in plain Termux, and how to install tlstore on plain Termux) lives with its source:
[PickleHik3/tlstore: docs/user/Tlstore.md](https://github.com/PickleHik3/tlstore/blob/main/docs/user/Tlstore.md).
