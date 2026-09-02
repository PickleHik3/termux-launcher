#!/bin/bash
# termux-sysroot.sh — assemble a Termux aarch64 sysroot from published .deb packages.
#
# The cross recipes need Termux's own headers and shared libraries, not the NDK's. The Termux
# package server publishes both inside ordinary Debian archives, so a sysroot is just a set of
# extracted .deb files — no Docker image and no termux-packages checkout are involved.
#
# Usage:
#   ./termux-sysroot.sh [package ...]
#
# With no arguments it installs the default set needed by the recipes in this directory. The
# sysroot is written to $TL_SYSROOT (default ./sysroot) and keeps the packages' absolute layout,
# so the prefix inside it is $TL_SYSROOT/data/data/com.termux/files/usr.
#
# For an edition that installs under another package name, point TL_TERMUX_REPO at that edition's
# repository instead of relocating this one: the .deb files, and the absolute paths inside their
# .pc files, carry the prefix they were built for. The VAJ edition's repository, for example:
#
#   TL_SYSROOT=$PWD/sysroot-vaj TL_CACHE=$PWD/debs-vaj \
#       TL_TERMUX_REPO=https://repo.pathayam.xyz ./termux-sysroot.sh
#
# then pass that sysroot and prefix to the build script:
#
#   TL_SYSROOT=$PWD/sysroot-vaj TERMUX_PREFIX=/data/data/io.vaj.tl/files/usr \
#       TERMUX_HOME=/data/data/io.vaj.tl/files/home ./build-fastfetch.sh
set -euo pipefail

TL_SYSROOT=${TL_SYSROOT:-"$PWD/sysroot"}
TL_TERMUX_REPO=${TL_TERMUX_REPO:-"https://packages.termux.dev/apt/termux-main"}
TL_TERMUX_ARCH=${TL_TERMUX_ARCH:-aarch64}
TL_CACHE=${TL_CACHE:-"$PWD/debs"}

# Enough to configure and link Fastfetch with ImageMagick 7, Chafa and zlib. Most of these are
# header-only from the compiler's point of view: Fastfetch dlopens the image libraries at runtime.
DEFAULT_PACKAGES=(
    zlib libandroid-support libandroid-glob libandroid-execinfo libandroid-wordexp-static
    imagemagick chafa glib libpng libjpeg-turbo libiconv libffi pcre2 freetype
    mesa mesa-dev libglvnd libglvnd-dev opengl vulkan-headers vulkan-loader-generic
    libx11 libxcb libxrandr libxrender libxext libxau libxdmcp xorgproto
    dbus pulseaudio libelf ocl-icd opencl-headers
)

for tool in curl python3; do
    command -v "$tool" >/dev/null 2>&1 || { echo "error: missing required tool: $tool" >&2; exit 1; }
done

# dpkg-deb is the obvious extractor but is not installed everywhere (no dpkg on Arch, for one).
# A .deb is an ar archive holding data.tar.*, so ar plus a tar that reads xz is a full substitute.
if command -v dpkg-deb >/dev/null 2>&1; then
    extract_deb() { dpkg-deb -x "$1" "$2"; }
elif command -v ar >/dev/null 2>&1 && command -v bsdtar >/dev/null 2>&1; then
    extract_deb() { ar p "$1" "$(ar t "$1" | grep '^data\.tar')" | bsdtar -xf - -C "$2"; }
else
    echo "error: no way to extract .deb files — install dpkg, or binutils and libarchive" >&2
    exit 1
fi

mkdir -p "$TL_SYSROOT" "$TL_CACHE"

index="$TL_CACHE/Packages.gz"
if [ ! -f "$index" ]; then
    echo "Fetching package index for $TL_TERMUX_ARCH..."
    curl -fsS --max-time 120 \
        "$TL_TERMUX_REPO/dists/stable/main/binary-$TL_TERMUX_ARCH/Packages.gz" -o "$index"
fi

packages=("$@")
if [ ${#packages[@]} -eq 0 ]; then
    packages=("${DEFAULT_PACKAGES[@]}")
fi

for package in "${packages[@]}"; do
    filename=$(python3 - "$index" "$package" <<'PY'
import gzip, sys
index, want = sys.argv[1], sys.argv[2]
text = gzip.open(index, 'rt', errors='replace').read()
for block in text.split('\n\n'):
    fields = dict(line.split(': ', 1) for line in block.split('\n') if ': ' in line)
    if fields.get('Package') == want:
        print(fields['Filename'])
        break
PY
)
    if [ -z "$filename" ]; then
        echo "warning: $package is not in the index; skipping" >&2
        continue
    fi
    deb="$TL_CACHE/$(basename "$filename")"
    [ -f "$deb" ] || curl -fsS --max-time 300 "$TL_TERMUX_REPO/$filename" -o "$deb"
    extract_deb "$deb" "$TL_SYSROOT"
    echo "installed $package"
done

echo
echo "Sysroot ready: $TL_SYSROOT"
for prefix in "$TL_SYSROOT"/data/data/*/files/usr; do
    # An if, not an &&: an unmatched glob must not fail the script under set -e.
    if [ -d "$prefix" ]; then
        echo "Prefix inside it: $prefix"
    fi
done
