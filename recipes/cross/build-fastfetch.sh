#!/bin/bash
# build-fastfetch.sh — cross-compile the patched Fastfetch for Termux aarch64 from a Linux host.
#
# This is the host-side counterpart of recipes/termux/fastfetch/build.sh. It produces the same
# binary from the same pinned commit and the same patch; it just does not need a phone to do it.
#
# Requires: Android NDK, CMake, Ninja, git, and a Termux sysroot from ./termux-sysroot.sh.
set -euo pipefail

FASTFETCH_URL="https://github.com/fastfetch-cli/fastfetch.git"
FASTFETCH_COMMIT="9c7cfb864ff9154ffe951fae191c14d60bb91544"   # v2.67.0
SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PATCH="$SCRIPT_DIR/../termux/fastfetch/0001-kitty-animation.patch"
PWD_POLYFILL="$SCRIPT_DIR/termux-pwd-polyfill.h"

TL_NDK=${TL_NDK:-"$HOME/android-sdk/ndk/27.2.12479018"}
TL_SYSROOT=${TL_SYSROOT:-"$PWD/sysroot"}
TL_OUT=${TL_OUT:-"$PWD/out"}
TL_BUILD_DIR=${TL_BUILD_DIR:-"$PWD/build-fastfetch"}
TL_ANDROID_API=${TL_ANDROID_API:-24}
TERMUX_PREFIX=${TERMUX_PREFIX:-/data/data/com.termux/files/usr}
TERMUX_HOME=${TERMUX_HOME:-/data/data/com.termux/files/home}

PREFIX_IN_SYSROOT="$TL_SYSROOT$TERMUX_PREFIX"
[ -d "$PREFIX_IN_SYSROOT/lib/pkgconfig" ] || {
    echo "error: no Termux sysroot at $PREFIX_IN_SYSROOT — run ./termux-sysroot.sh first" >&2
    exit 1
}
[ -d "$TL_NDK" ] || { echo "error: NDK not found at $TL_NDK (set TL_NDK)" >&2; exit 1; }
[ -f "$PWD_POLYFILL" ] || { echo "error: passwd polyfill not found at $PWD_POLYFILL" >&2; exit 1; }

mkdir -p "$TL_OUT" "$TL_BUILD_DIR"
source_dir="$TL_BUILD_DIR/source"

if [ ! -d "$source_dir/.git" ]; then
    echo "Fetching Fastfetch v2.67.0 ($FASTFETCH_COMMIT)..."
    git init -q "$source_dir"
    git -C "$source_dir" remote add origin "$FASTFETCH_URL"
    git -C "$source_dir" fetch -q --depth 1 origin "$FASTFETCH_COMMIT"
    git -C "$source_dir" checkout -q --detach FETCH_HEAD
    echo "Applying the animated Kitty graphics patch..."
    git -C "$source_dir" apply "$PATCH"
fi

# CMAKE_FIND_ROOT_PATH_MODE_* must be ONLY. With BOTH, CMake happily finds host headers such as
# Lua's, enables features the device does not have, and the build then fails on the first include.
# PKG_CONFIG_SYSROOT_DIR rewrites the absolute Termux paths inside the .pc files onto the sysroot;
# without it every -I lands on the device path and nothing is found.
#
# glob()/globfree() are not in Bionic — Termux provides them in libandroid-glob, which is why the
# extra -I/-l/-L below exist. RUNPATH points at the device prefix so the dlopened image libraries
# resolve at runtime.
#
# termux-pwd-polyfill.h is force-included for the same class of reason: the stock NDK's getpwuid()
# reports pw_dir="/data" for an app uid, and Fastfetch trusts passwd over $HOME, so without it the
# binary never finds ~/.config/fastfetch and quietly falls back to the built-in ASCII logo.
echo "Configuring..."
PKG_CONFIG_SYSROOT_DIR="$TL_SYSROOT" \
PKG_CONFIG_LIBDIR="$PREFIX_IN_SYSROOT/lib/pkgconfig" \
cmake -S "$source_dir" -B "$TL_BUILD_DIR/build" -G Ninja \
    -DCMAKE_TOOLCHAIN_FILE="$TL_NDK/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI=arm64-v8a \
    -DANDROID_PLATFORM="android-$TL_ANDROID_API" \
    -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_FIND_ROOT_PATH="$PREFIX_IN_SYSROOT" \
    -DCMAKE_FIND_ROOT_PATH_MODE_INCLUDE=ONLY \
    -DCMAKE_FIND_ROOT_PATH_MODE_LIBRARY=ONLY \
    -DCMAKE_FIND_ROOT_PATH_MODE_PACKAGE=ONLY \
    -DCMAKE_C_FLAGS="-I$PREFIX_IN_SYSROOT/include -include $PWD_POLYFILL" \
    -DCMAKE_CXX_FLAGS="-I$PREFIX_IN_SYSROOT/include -include $PWD_POLYFILL" \
    -DCMAKE_EXE_LINKER_FLAGS="-L$PREFIX_IN_SYSROOT/lib -landroid-glob -Wl,-rpath,$TERMUX_PREFIX/lib" \
    -DTARGET_DIR_HOME="$TERMUX_HOME" \
    -DTARGET_DIR_ROOT="$TERMUX_PREFIX" \
    -DTARGET_DIR_USR="$TERMUX_PREFIX" \
    -DCMAKE_INSTALL_PREFIX="$TERMUX_PREFIX" \
    -DENABLE_IMAGEMAGICK7=ON -DENABLE_CHAFA=ON -DENABLE_ZLIB=ON \
    -DBUILD_TESTS=OFF

echo "Building..."
ninja -C "$TL_BUILD_DIR/build" -j"${TL_BUILD_JOBS:-$(nproc)}"

"$TL_NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip" \
    -o "$TL_OUT/fastfetch" "$TL_BUILD_DIR/build/fastfetch"

# The polyfill's login-shell path only lands in the binary when the force-include took effect, so
# its absence means the build would resolve the home directory to "/data" on device.
if ! grep -qa "$TERMUX_PREFIX/bin/login" "$TL_OUT/fastfetch"; then
    echo "error: the passwd polyfill is missing from the build — fastfetch would ignore ~/.config" >&2
    exit 1
fi

echo
echo "Built: $TL_OUT/fastfetch"
"$TL_NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-readelf" -d "$TL_OUT/fastfetch" |
    grep -E "NEEDED|RUNPATH" || true
