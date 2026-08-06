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
