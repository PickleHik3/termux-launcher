#!/bin/bash
# build-sigye.sh — cross-compile the patched Sigye clock for Termux aarch64 from a Linux host.
#
# Host-side counterpart of recipes/termux/sigye/build.sh, from the same pinned commit and patch.
# Sigye links against nothing outside Bionic, so no Termux sysroot is needed here.
#
# Requires: rustup with the aarch64-linux-android target, the Android NDK, and git.
set -euo pipefail

SIGYE_URL="https://github.com/am2rican5/sigye.git"
SIGYE_COMMIT="0f0b8caaccb4ca01ab5d1fad1237c4a01a49766f"   # v0.6.0
SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PATCH="$SCRIPT_DIR/../termux/sigye/0001-termux-clipboard.patch"

TL_NDK=${TL_NDK:-"$HOME/android-sdk/ndk/27.2.12479018"}
TL_OUT=${TL_OUT:-"$PWD/out"}
TL_BUILD_DIR=${TL_BUILD_DIR:-"$PWD/build-sigye"}
TL_ANDROID_API=${TL_ANDROID_API:-26}

NDK_BIN="$TL_NDK/toolchains/llvm/prebuilt/linux-x86_64/bin"
[ -d "$NDK_BIN" ] || { echo "error: NDK not found at $TL_NDK (set TL_NDK)" >&2; exit 1; }

# Sigye v0.6.0 declares rust-version 1.97.1; an older toolchain fails during dependency resolution.
rustup target list --installed 2>/dev/null | grep -qx aarch64-linux-android || {
    echo "error: rust target missing — run: rustup target add aarch64-linux-android" >&2
    exit 1
}

mkdir -p "$TL_OUT" "$TL_BUILD_DIR"
source_dir="$TL_BUILD_DIR/source"

if [ ! -d "$source_dir/.git" ]; then
    echo "Fetching Sigye v0.6.0 ($SIGYE_COMMIT)..."
    git init -q "$source_dir"
    git -C "$source_dir" remote add origin "$SIGYE_URL"
    git -C "$source_dir" fetch -q --depth 1 origin "$SIGYE_COMMIT"
    git -C "$source_dir" checkout -q --detach FETCH_HEAD
    echo "Applying the Termux clipboard patch..."
    git -C "$source_dir" apply "$PATCH"
fi

echo "Building..."
(
    cd "$source_dir"
    CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER="$NDK_BIN/aarch64-linux-android$TL_ANDROID_API-clang" \
    CC_aarch64_linux_android="$NDK_BIN/aarch64-linux-android$TL_ANDROID_API-clang" \
    AR_aarch64_linux_android="$NDK_BIN/llvm-ar" \
    cargo build --release --target aarch64-linux-android
)

"$NDK_BIN/llvm-strip" -o "$TL_OUT/sigye" \
    "$source_dir/target/aarch64-linux-android/release/sigye"

echo
echo "Built: $TL_OUT/sigye"
"$NDK_BIN/llvm-readelf" -d "$TL_OUT/sigye" | grep -E "NEEDED|RUNPATH" || true
