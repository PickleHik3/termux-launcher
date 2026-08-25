# Building terminal showcase tools

Termux Launcher supports Sixel and the Kitty graphics protocol, including terminal-driven animated
images. The normal Termux `fastfetch` package can send a static Kitty image, but it currently sends
only the first frame of an animated GIF. Sigye v0.6.0 does not build from its unmodified source on
Android because its clipboard dependency has no Android backend.

This repository contains pinned, auditable recipes for both applications. They install into
`~/.local`, leaving files owned by APT untouched.

## Before building

Make sure `~/.local/bin` is before `$PREFIX/bin` in your shell's `PATH` so the locally built
Fastfetch wins over the Termux package:

```sh
mkdir -p ~/.local/bin
export PATH="$HOME/.local/bin:$PATH"
```

Add the `export` line to `~/.bashrc`, `~/.zshrc`, or the equivalent file for your shell if it is not
already present.

Clone the recipe repository if you do not already have its source tree:

```sh
pkg install git
git clone --depth 1 --branch dev https://github.com/PickleHik3/termux-launcher.git
cd termux-launcher
```

The recipes use exact upstream commits. Review the scripts and adjacent patch files before running
them if you want to verify every source change.

## Sigye clock

The latest Sigye release checked by this recipe is v0.6.0. Its `arboard` dependency deliberately
has no Android backend, so the unmodified release fails to compile for Termux. The included patch
makes `arboard` a non-Android dependency and uses the Termux clipboard command on Android.

Install the compiler and build:

```sh
pkg install git rust
./recipes/termux/sigye/build.sh
sigye
```

Sigye v0.6.0 requires Rust 1.97.1 or newer. If `rustc --version` reports an older version, update
the Termux `rust` package before building.

Sigye itself runs without Termux:API. Its `u` and `i` clipboard shortcuts additionally require the
`termux-api` package and the matching Termux:API Android add-on:

```sh
pkg install termux-api
```

Use the Termux:API build matching your launcher edition. Without it, Sigye keeps running and reports
that the clipboard is unavailable.

## Fastfetch with animated Kitty graphics

Install the native toolchain and image libraries, then run the recipe:

```sh
pkg install git clang cmake ninja pkg-config linux-headers imagemagick chafa zlib
./recipes/termux/fastfetch/build.sh
hash -r
fastfetch --version
fastfetch --list-features
```

The feature list must contain `imagemagick7` and `zlib`. The patch coalesces GIF frames, sends them
as Kitty animation frames, preserves their delays, and asks Termux Launcher to keep looping the
animation after Fastfetch exits.

Copy the supplied example and change the image path:

```sh
mkdir -p ~/.config/fastfetch
cp docs/en/examples/fastfetch.jsonc ~/.config/fastfetch/config.jsonc
fastfetch
```

The important logo settings are:

```jsonc
"logo": {
  "type": "kitty",
  "source": "/absolute/path/to/image.gif",
  "width": 21
}
```

Use `"kitty"`, not `"kitty-direct"`: Android terminals cannot use Kitty's desktop file-transfer
path, while the direct in-band protocol is supported. Fastfetch's image cache is versioned by the
patch so an old static cached logo is not reused.

The patch places the logo through Kitty's Unicode placeholders, the mechanism
`kitten icat --unicode-placeholder` uses: the image is transmitted once as a virtual placement
(`U=1`) and Fastfetch then prints a grid of U+10EEEE cells whose foreground colour names the image
id and whose combining marks name each cell's row and column. Nothing about the placement depends
on where a terminal leaves the cursor after a graphics command, and the logo is ordinary text, so
it scrolls with the buffer and is repainted from the stored image by anything that redraws those
cells — tmux, a full-screen editor, a resize.

A placeholder grid is a whole number of cells, so the transmission carries both a column and a row
count and a logo whose pixel height is not a multiple of the cell height sits in a box up to one
cell taller than itself; Termux Launcher scales it to fit that box without distorting it. The image
cache is versioned by the patch (`kittyc4`/`kittyu4`), so a logo cached by an earlier build is not
replayed.

This needs a terminal that implements Unicode placeholders, not only the graphics protocol. Termux
Launcher does; a terminal that ignores `U=1` stores the image, draws nothing, and shows the
placeholder cells as missing glyphs.

Set `"printRemaining": true` whenever the logo is taller than the module list. The logo is text
now, so the shell prompt that lands inside it clears those lines and the bottom of the image
disappears; `printRemaining` pads past the logo before Fastfetch exits. Sizing the logo to the
module list (`"width"`/`"height"`) does the same job by making it shorter.

Cross-building instead of building on the phone needs one extra piece: `recipes/cross/
termux-pwd-polyfill.h`, force-included by `recipes/cross/build-fastfetch.sh`. Bionic answers
`getpwuid()` for an app uid with `pw_dir="/data"`, and Fastfetch trusts passwd over `$HOME`, so a
binary built against the stock NDK sysroot looks for its config in `/data/.config/fastfetch`, never
finds it, and silently falls back to the built-in ASCII logo. Termux's own package builds avoid
this because termux-packages patches `pwd.h` inside its copy of the NDK sysroot. To check a binary
on device:

```sh
fastfetch -s Title --format json | grep -o '"homeDir":"[^"]*"'
```

It must report the Termux home, not `/data/`.

## Kitten (kitty's client binary)

`kitten` is the standalone Go client from kitty. Programs shell out to it — `kitten icat` is what
several file managers and Fastfetch's `kitty-icat` logo type invoke — and it is not in the Termux
repositories. Unlike the two recipes above it cannot practically be built on the phone: kitty's
generated Go sources are produced by a generator that needs a built kitty application, so the
recipe for it is a host cross-build. See [`recipes/cross/README.md`](../../recipes/cross/README.md).

Termux Launcher implements the in-band Kitty graphics protocol that `kitten icat` uses, so a
`kitten` binary built this way displays images here. Two things it will not do:

- `kitten icat` warns that it cannot create shared memory, because Android has no `/dev/shm`. It
  falls back to the in-band transfer, which is the supported path — the warning is noise.
- `kitten @ …` remote control does nothing. Termux Launcher implements no kitty remote-control
  endpoint; `launcherctl` and the terminal action registry cover that ground with a different
  interface.

## Building on a host instead of on the phone

Everything in `recipes/termux` can also be cross-compiled from a Linux host with the Android NDK,
which is useful when a device build is slow or the device is unavailable. `recipes/cross` contains
those scripts, including a helper that assembles a Termux sysroot from published `.deb` packages —
no Docker image and no `termux-packages` checkout are involved.

Host-built binaries are still unverified until they run on a device. Install them the same way the
on-device recipes do, into `~/.local/bin`.

## Recipe controls and troubleshooting

Both scripts support these environment variables:

- `TL_INSTALL_PREFIX` changes the install root; the default is `~/.local`.
- `TL_BUILD_JOBS` limits parallel compilation; for example, use `TL_BUILD_JOBS=2` on a device short
  on memory.
- `TL_KEEP_BUILD=1` preserves the temporary source/build tree and prints its location when the
  script exits.

If the system Fastfetch still runs, check `command -v fastfetch` and put `~/.local/bin` first in
`PATH`. If a previous logo is reused, regenerate Fastfetch's image cache with
`fastfetch --logo-recache true`.

These patches are intentionally pinned. When either upstream revision changes, refresh and retest
the patch instead of silently applying it to different source.
