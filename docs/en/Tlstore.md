# Tlstore

Tlstore is the launcher's own package manager for the tools and configs it shows off but does not
ship in the APK: the showcase binaries, an opinionated shell setup, a themed editor, and Claude
Code. The app puts `tlstore` in place for you, along with the shorter `tl` and `tls`, so all three
names run the same store.

## Quick start

```sh
tlstore shell
```

installs the whole shell setup in one go: fish, a prompt that follows your wallpaper colors,
faster directory jumping, and a nicer `ls`. From there, browse everything else with:

```sh
tlstore install
```

which opens a picker over every item you do not have yet.

## Commands

| command | what it does |
| --- | --- |
| `tlstore list` | everything in the store, with what you already have marked |
| `tlstore list -i` | only what you have installed |
| `tlstore list -a` | only what you do not have yet |
| `tlstore search fish` | find an item by name or description |
| `tlstore info neovim` | what an item is, its version, and where it goes |
| `tlstore install fish eza` | install one or more items by name |
| `tlstore install` | open the picker instead of naming anything |
| `tlstore remove neovim` | remove an item tlstore installed |
| `tlstore update` | bring everything you have up to date |
| `tlstore update --check` | see what is out of date without installing anything |
| `tlstore refresh` | update the list of items without touching what is installed |
| `tlstore shell` | install the whole shell setup |
| `tlstore doctor` | check that everything is in place |
| `tlstore version` | show the tlstore and item-list versions |

Add `-y` to `install`, `remove`, or `update` to skip the "go ahead?" prompt.

Removing a package-based item (like `neovim` or `build-tools`) only tells tlstore to stop tracking
it — the package itself stays installed, the way `apt`/`pacman` already manage it. Everything else
tlstore put down is deleted.

## The picker

Run `tlstore install` with no names and you get a list of everything you do not have yet. If
[`fzf`](https://github.com/junegunn/fzf) is on your phone, it opens as a fuzzy, type-to-filter,
multi-select list. Without it, tlstore falls back to a plain numbered list: type numbers to toggle
them, `a` for all, `n` for none, and press enter when you are done.

## What's in the store

- **Shell** — fish, a prompt that follows your wallpaper colors, faster directory jumping, and a
  colorful `ls`, plus the launcher's own fish configuration and prompt theme. `tlstore shell`
  installs all of it in one go.
- **Dev tools** — compilers, git, and the rest of a basic build toolchain, plus Neovim set up with
  a themed distribution. `tlstore install dev-tools`.
- **Showcase** — the tools the launcher shows off: a terminal clock, kitty's companion tool for
  images and files, and a fastfetch build with an animated logo. `tlstore install showcase`.
- **Claude Code** — see below.

Ask about any one of them first with `tlstore info <name>`.

## Where things go

Everything tlstore installs lives under `~/.local` — programs in `~/.local/bin`, larger tools in
`~/.local/lib` — never in Termux's own `bin`. That way a bootstrap reinstall, which wipes Termux's
own directories, never takes your tools with it, and tlstore never fights `apt`/`pacman` over a
name they already own.

A config file tlstore replaces (like your fish configuration) keeps a timestamped backup right
beside it, so nothing you changed is thrown away. A few files — your personal fish settings, for
one — are only ever installed once and then left alone for you to edit; reinstalling never
overwrites them.

## Keeping things up to date

`tlstore update` checks for a newer list of items, then upgrades anything you have that has moved
on. `tlstore refresh` only fetches that list, without installing or changing anything.

The list of items is signed by the launcher's maintainer, and tlstore only accepts an update to it
when the signature checks out and it is genuinely newer than the one you have — so a compromised
mirror or a bad network cannot swap in something else under your feet.

## Doctor

`tlstore doctor` looks over your setup — where things are installed, whether your shell finds them
before Termux's own copies, which tools tlstore's items need, and whether anything it installed has
gone missing — and tells you what, if anything, needs attention.

## Claude Code

`tlstore install claude-code` installs [Claude Code](https://claude.com/claude-code), Anthropic's
coding agent for the terminal. It is about 200 MB, downloaded from npm and run through a small
compatibility loader so it works on Android. Its own built-in updater is switched off — `tlstore
update` is how it gets new versions. Once it is installed, sign in by running:

```sh
claude
```

## For maintainers

The catalog that `tlstore` reads (`app/src/main/assets/tlstore/catalog.tsv`) is generated — never
hand-edit it. To add or change an item:

1. Edit `scripts/tlstore/items.tsv`, the hand-maintained item list.
2. Run `scripts/tlstore/build-catalog.sh <path to termux-launcher-binaries/SHA256SUMS>` to compute
   digests, bump the serial, and write `catalog.tsv`.
3. Run `scripts/tlstore/sign-catalog.sh` to sign it.
4. Commit all three: `items.tsv`, `catalog.tsv`, and `catalog.tsv.minisig`.

An item whose payload differs per launcher edition (a build linked against one edition's prefix, an
edition-specific binary) gets one row per edition, sharing a name but each with its own source and
digest — never one row trying to serve every edition.

This supersedes [`setup-launcher`](examples/setup-launcher); that script still works for anyone on
an older release, but new guides and new items should point here.
