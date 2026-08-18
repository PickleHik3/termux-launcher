#!/bin/bash
# build-kitten.sh — cross-compile kitty's `kitten` client for Android aarch64 from a Linux host.
#
# `kitten` is the standalone Go half of kitty. It is what programs shell out to for `kitten icat`,
# and it is not packaged for Termux. It is CGO-free, so the cross-build itself is trivial; the
# awkward part is that kitty's *_generated.go files are gitignored and are emitted by a script that
# only runs under a built kitty. That is what the `./dev.sh build` step below is for — it downloads
# a self-contained prebuilt dependency bundle, so no system development packages are required.
#
# The target must be android/arm64. A linux/arm64 static build looks more attractive — it is what
# upstream publishes and what makes `kitten update-self` work — but it dies on Android:
#
#   SIGSYS: bad system call
#   syscall.faccessat2(...) -> os/exec.findExecutable -> imaging/magick.init
#
# Go's os/exec.LookPath reaches for faccessat2 on GOOS=linux, and Android's seccomp filter kills
# the process for using it. kitten's ImageMagick detection runs LookPath during package init, so
# every subcommand dies before doing anything. Device-verified on Android 16 (2026-08-16):
# linux/arm64 crashes on `kitten icat`, android/arm64 renders correctly.
#
# Cost of the android build: it is a PIE bound to /system/bin/linker64 rather than a static
# binary, and `kitten update-self` will 404 because upstream publishes no android asset.
#
# Requires: Go (see kitty's go.mod for the minimum), python3, git, and network access.
set -euo pipefail

KITTY_URL="https://github.com/kovidgoyal/kitty.git"
KITTY_TAG=${KITTY_TAG:-v0.48.2}

TL_OUT=${TL_OUT:-"$PWD/out"}
TL_BUILD_DIR=${TL_BUILD_DIR:-"$PWD/build-kitten"}
TL_GOOS=${TL_GOOS:-android}
TL_GOARCH=${TL_GOARCH:-arm64}

for tool in go python3 git; do
    command -v "$tool" >/dev/null 2>&1 || { echo "error: missing required tool: $tool" >&2; exit 1; }
done

mkdir -p "$TL_OUT" "$TL_BUILD_DIR"
source_dir="$TL_BUILD_DIR/source"

if [ ! -d "$source_dir/.git" ]; then
    echo "Cloning kitty $KITTY_TAG..."
    git clone --depth 1 --branch "$KITTY_TAG" "$KITTY_URL" "$source_dir"
fi

# Generated Go sources. `python3 gen/go_code.py` on its own fails on kitty.fast_data_types (kitty's
# C extension), so the kitty app genuinely has to be built first. dev.sh fetches its own toolchain
# and dependencies into source/dependencies/, leaving the host untouched.
if [ -z "$(find "$source_dir" -name '*_generated.go' -print -quit)" ]; then
    echo "Building kitty so it can generate the Go sources (this takes a few minutes)..."
    (cd "$source_dir" && ./dev.sh build)
fi

revision=$(git -C "$source_dir" rev-parse HEAD)
output="$TL_OUT/kitten-$TL_GOOS-$TL_GOARCH"

# IsStandaloneBuild is what makes `kitten update-self` work; without it the command hard-errors.
# VCSRevision no longer exists in the v0.48.2 Go tree — upstream still passes it, and so do we for
# parity, but the revision that actually survives is the toolchain's own vcs.revision stamp.
echo "Building kitten for $TL_GOOS/$TL_GOARCH..."
(
    cd "$source_dir"
    CGO_ENABLED=0 GOOS="$TL_GOOS" GOARCH="$TL_GOARCH" go build -trimpath \
        -ldflags "-s -w -X github.com/kovidgoyal/kitty.VCSRevision=$revision -X github.com/kovidgoyal/kitty.IsStandaloneBuild=true" \
        -o "$output" ./tools/cmd
)

echo
echo "Built: $output"
file "$output"
