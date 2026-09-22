#!/bin/bash
# build-dawn.sh — cross-compile the dawn markdown drafter for Termux aarch64 from a Linux host.
#
# dawn is plain C with its parsers vendored in, so the only library it needs from the device is
# libcurl. That one library is also the whole reason for a build per launcher edition: Termux
# removes LD_LIBRARY_PATH on Android 7+, so libcurl is found through DT_RUNPATH, and a RUNPATH
# naming another edition's prefix does not resolve.
#
# Requires: Android NDK, CMake, git, and a Termux sysroot from ./termux-sysroot.sh with libcurl:
#
#   ./termux-sysroot.sh libcurl openssl zlib libnghttp2
set -euo pipefail

DAWN_URL="https://github.com/andrewmd5/dawn.git"
DAWN_COMMIT="0e9587477463ece157ef7eea66c9e34bc5c7737a"   # main, 2026-04-29 (v0.1.3 plus fixes)
DAWN_VERSION_STRING="0.1.3+0e958747"
SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PATCH="$SCRIPT_DIR/0001-dawn-termux-clipboard.patch"

TL_NDK=${TL_NDK:-"$HOME/android-sdk/ndk/27.2.12479018"}
TL_SYSROOT=${TL_SYSROOT:-"$PWD/sysroot"}
TL_OUT=${TL_OUT:-"$PWD/out"}
TL_BUILD_DIR=${TL_BUILD_DIR:-"$PWD/build-dawn"}
TL_ANDROID_API=${TL_ANDROID_API:-24}
TERMUX_PREFIX=${TERMUX_PREFIX:-/data/data/com.termux/files/usr}

PREFIX_IN_SYSROOT="$TL_SYSROOT$TERMUX_PREFIX"
[ -f "$PREFIX_IN_SYSROOT/lib/libcurl.so" ] || {
    echo "error: no libcurl in the sysroot at $PREFIX_IN_SYSROOT — run" >&2
    echo "       ./termux-sysroot.sh libcurl openssl zlib libnghttp2 first" >&2
    exit 1
}
[ -d "$TL_NDK" ] || { echo "error: NDK not found at $TL_NDK (set TL_NDK)" >&2; exit 1; }
[ -f "$PATCH" ] || { echo "error: clipboard patch not found at $PATCH" >&2; exit 1; }

mkdir -p "$TL_OUT" "$TL_BUILD_DIR"
source_dir="$TL_BUILD_DIR/source"

if [ ! -d "$source_dir/.git" ]; then
    echo "Fetching dawn $DAWN_COMMIT..."
    git init -q "$source_dir"
    git -C "$source_dir" remote add origin "$DAWN_URL"
    git -C "$source_dir" fetch -q --depth 1 origin "$DAWN_COMMIT"
    git -C "$source_dir" checkout -q --detach FETCH_HEAD
    git -C "$source_dir" submodule update -q --init --recursive --depth 1
    echo "Applying the Termux clipboard patch..."
    git -C "$source_dir" apply "$PATCH"
fi

# The same CMAKE_FIND_ROOT_PATH_MODE_* reasoning as build-fastfetch.sh: with BOTH, CMake finds the
# host's libcurl headers and the link then fails on a library that is not there. The RUNPATH is what
# makes libcurl resolve on device; without it dawn does not start at all.
echo "Configuring for $TERMUX_PREFIX..."
PKG_CONFIG_SYSROOT_DIR="$TL_SYSROOT" \
PKG_CONFIG_LIBDIR="$PREFIX_IN_SYSROOT/lib/pkgconfig" \
cmake -S "$source_dir" -B "$TL_BUILD_DIR/build" \
    -DCMAKE_TOOLCHAIN_FILE="$TL_NDK/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI=arm64-v8a \
    -DANDROID_PLATFORM="android-$TL_ANDROID_API" \
    -DCMAKE_BUILD_TYPE=Release \
    -DDAWN_VERSION="$DAWN_VERSION_STRING" \
    -DCMAKE_FIND_ROOT_PATH="$PREFIX_IN_SYSROOT" \
    -DCMAKE_FIND_ROOT_PATH_MODE_INCLUDE=ONLY \
    -DCMAKE_FIND_ROOT_PATH_MODE_LIBRARY=ONLY \
    -DCMAKE_FIND_ROOT_PATH_MODE_PACKAGE=ONLY \
    -DCMAKE_C_FLAGS="-I$PREFIX_IN_SYSROOT/include" \
    -DCMAKE_EXE_LINKER_FLAGS="-L$PREFIX_IN_SYSROOT/lib -Wl,-rpath,$TERMUX_PREFIX/lib"

echo "Building..."
cmake --build "$TL_BUILD_DIR/build" -j"${TL_BUILD_JOBS:-$(nproc)}"

"$TL_NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip" \
    -o "$TL_OUT/dawn" "$TL_BUILD_DIR/build/dawn"

# Upstream's POSIX clipboard shells out to xclip and xsel, neither of which exists on a phone. The
# patch replaces that path with OSC 52 through the terminal, and only for __ANDROID__ — so the query
# string is in the binary exactly when the patch took, and xclip is gone exactly then too.
if ! grep -qa ']52;c;?' "$TL_OUT/dawn"; then
    echo "error: the clipboard patch is missing from the build — copy and paste would be silent" >&2
    exit 1
fi
if grep -qa 'xclip' "$TL_OUT/dawn"; then
    echo "error: the xclip path is still in the build — the patch did not replace it" >&2
    exit 1
fi

readelf="$TL_NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-readelf"
if ! "$readelf" -d "$TL_OUT/dawn" | grep -q "Library runpath: \[$TERMUX_PREFIX/lib\]"; then
    echo "error: RUNPATH does not name $TERMUX_PREFIX/lib — libcurl would not resolve on device" >&2
    exit 1
fi

echo
echo "Built: $TL_OUT/dawn"
"$readelf" -d "$TL_OUT/dawn" | grep -E "NEEDED|RUNPATH" || true
