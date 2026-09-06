# Tlstore

Tlstore is the launcher's own package manager for the tools and configs it shows off but does not
ship in the APK. The app puts `tlstore` in place for you, along with the shorter `tl` and `tls`, so
all three names run the same store.

## Quick start

```sh
tlstore shell
```

installs the whole fish setup in one go: fish, a prompt that follows your wallpaper colors, a nicer
`ls`, faster directory jumping, and a couple of plugins. From there, browse everything else with:

```sh
tlstore install
```

which opens a picker over every item you do not have yet.

## What's in the store

Seven items, and each one brings whatever it needs along with it.

| item | what you get |
| --- | --- |
| `claude-code` | [Claude Code](https://claude.com/claude-code), Anthropic's coding agent for the terminal. About 200 MB. |
| `fastfetch` | System information beside an animated logo, with the layout and the GIF. |
| `fish-shell` | The fish shell with the launcher's setup: the wallpaper prompt and its theme, `eza`, `zoxide`, and the plugins below. |
| `kitten` | Kitty's companion tool, for showing images and sending files from the terminal. |
| `nvim-theme` | A Neovim colour scheme that follows your wallpaper. |
| `omp-theme` | The prompt theme on its own, if you already use oh-my-posh. |
| `sigye` | A clock for the terminal. |

Ask about any one of them first with `tlstore info <name>`.

Names you see inside a `Needs` line — `fisher`, `musl-loader`, `config-fish` and the rest — are
parts, not choices. They come in with the item that needs them, and `tlstore install` refuses them
by name.

## Commands

| command | what it does |
| --- | --- |
| `tlstore list` | everything in the store; a `*` marks what you already have |
| `tlstore list -i` | only what you have installed |
| `tlstore list -a` | only what you do not have yet |
| `tlstore search fish` | find an item by name or description |
| `tlstore info nvim-theme` | what an item is, its version, and where it goes |
| `tlstore install kitten sigye` | install one or more items by name |
| `tlstore install` | open the picker instead of naming anything |
| `tlstore remove kitten` | remove an item tlstore installed |
| `tlstore update` | bring everything you have up to date |
| `tlstore update --check` | see what is out of date without installing anything |
| `tlstore refresh` | update the list of items without touching what is installed |
| `tlstore shell` | install the whole fish setup |
| `tlstore display` | set up graphics for Linux apps |
| `tlstore doctor` | check that everything is in place |
| `tlstore version` | show the tlstore and item-list versions |

`-y` says yes to everything except a config file of yours. `--configs` on `install` and `update`
answers that one too, for scripts.

Removing a package-based item only tells tlstore to stop tracking it — the package itself stays
installed, the way `apt`/`pacman` already manage it. Everything else tlstore put down is deleted.

## Your config files are never replaced silently

When tlstore is about to write a config file you already have and yours is different, it shows you
the change and asks:

```
$ tlstore update
config-fish
--- /data/data/com.termux/files/home/.config/fish/config.fish
+++ ...
@@ -12,7 +12,7 @@
-    set -gx EDITOR vi
+    set -gx EDITOR nvim
Replace your config.fish? [y/N]
```

The answer defaults to no, and `-y` does not answer it — a config file is your work, not part of
"yes to everything". Answering yes leaves a timestamped backup of your file right beside it.

Either answer is remembered at the shipped version, so declining once is not asked about again
until that file itself changes. `tlstore update --check` lists those as *has a new version; update
shows the change and asks*. In a script, or anywhere there is nobody to ask, your file is kept and
one line says so — add `--configs` if you want the new one.

A few files are only ever installed once and then left alone for you to edit for good: your
personal fish settings, the fastfetch layout, and the logo GIF.

## Tools that are only needed while installing

Some items need a package only while they install and never again. `tlstore list` says so under the
item:

```
  claude-code    Anthropic's Claude Code, about 200 MB.
    needs while installing: patchelf
```

Tlstore installs those for you, and once everything is in place it offers to take back exactly the
ones it added and clear what it downloaded. Say no and they simply stay.

## The fish setup

`tlstore shell` (or `tlstore install fish-shell`) installs fish itself, the launcher's
`config.fish`, a `conf.d/personal.fish` that is yours to edit, oh-my-posh with the wallpaper prompt
theme, `eza`, `zoxide`, and [fisher](https://github.com/jorgebucaran/fisher) with two plugins:

- [`puffer-fish`](https://github.com/nickeb96/puffer-fish) — type `...` and get `../..`, and other
  small text expansions.
- [`autopair.fish`](https://github.com/jorgebucaran/autopair.fish) — brackets and quotes close
  themselves.

Fisher fetches those plugins from GitHub itself, so installing them needs a network connection.
Keeping them current is fisher's job rather than tlstore's — `tlstore update` leaves them alone,
and `fisher update` in a fish shell brings them forward.

## The Neovim theme

`tlstore install nvim-theme` puts two files in your Neovim config — the colour scheme and the piece
that turns your wallpaper into editor colours. It does not install Neovim. Turn it on in whichever
way matches your setup:

```vim
:colorscheme launcher-material
```

```lua
-- a plain config, or anything built on lazy.nvim (LazyVim included)
vim.cmd.colorscheme("launcher-material")
```

```lua
-- AstroNvim
{ "AstroNvim/astroui", opts = { colorscheme = "launcher-material" } }
```

```lua
-- NvChad, which paints its own theme at startup, so ask for this one after it
vim.schedule(function() vim.cmd.colorscheme("launcher-material") end)
```

The editor retints itself when you change your wallpaper. The background is glass by default, so
the wallpaper shows through at whatever opacity your terminal is set to; `:MaterialTransparent off`
makes it solid.

## Where things go

Everything tlstore installs lives under `~/.local` — programs in `~/.local/bin`, larger tools in
`~/.local/lib` — never in Termux's own `bin`. That way a bootstrap reinstall, which wipes Termux's
own directories, never takes your tools with it, and tlstore never fights `apt`/`pacman` over a
name they already own. Configs go where the program that reads them expects, under `~/.config`.

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

`tlstore install claude-code` installs Claude Code. It is about 200 MB, downloaded from npm and run
through a small compatibility loader so it works on Android. Its own built-in updater is switched
off — `tlstore update` is how it gets new versions. Once it is installed, sign in by running:

```sh
claude
```

## For maintainers

The catalog that `tlstore` reads (`app/src/main/assets/tlstore/catalog.tsv`) is generated — never
hand-edit it. To add or change an item:

1. Edit `scripts/tlstore/items.tsv`, the hand-maintained item list.
2. Run `scripts/tlstore/build-catalog.sh <path to termux-launcher-binaries/SHA256SUMS>` to compute
   digests, bump the serial, and write `catalog.tsv`. A plain `http(s)` source is downloaded once
   to hash it, so that step needs the network; it must name a tag or a commit, never a branch.
3. Run `scripts/tlstore/sign-catalog.sh` to sign it.
4. Commit all three: `items.tsv`, `catalog.tsv`, and `catalog.tsv.minisig`.

Anything a user should not choose directly gets `hidden=1` in its options; anything needed only
while installing goes in `build=`. A file in this repository is pinned to a tag or the commit that
last changed it, so the catalog and the payload can never drift apart.

An item whose payload differs per launcher edition (a build linked against one edition's prefix, an
edition-specific binary) gets one row per edition, sharing a name but each with its own source and
digest — never one row trying to serve every edition.
