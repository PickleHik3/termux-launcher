#!/usr/bin/env bash
# Builds tlstore-ui release binaries for the phone ABIs the launcher ships.
#
#   scripts/tlstore/build-ui.sh [abi...]      abi: arm64-v8a (default) and/or x86_64
#
# Needs rustup with the aarch64-linux-android / x86_64-linux-android targets and the
# Android NDK. The NDK is taken from $ANDROID_NDK_HOME, else the SDK's ndk/29.0.14206865
# ($ANDROID_HOME, ~/Android/Sdk, ~/Library/Android/sdk). Binaries link against the
# platform's own libc at API 26, are stripped, and land in tools/tlstore-ui/dist/<abi>/.
set -euo pipefail

API=26
NDK_VERSION=29.0.14206865
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
CRATE="$ROOT/tools/tlstore-ui"

if [ "$#" -eq 0 ]; then
    set -- arm64-v8a x86_64
fi

find_ndk() {
    if [ -n "${ANDROID_NDK_HOME:-}" ] && [ -d "$ANDROID_NDK_HOME" ]; then
        echo "$ANDROID_NDK_HOME"
        return
    fi
    for sdk in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}" "$HOME/Android/Sdk" "$HOME/Library/Android/sdk"; do
        if [ -n "$sdk" ] && [ -d "$sdk/ndk/$NDK_VERSION" ]; then
            echo "$sdk/ndk/$NDK_VERSION"
            return
        fi
    done
    echo "build-ui: Android NDK $NDK_VERSION not found; set ANDROID_NDK_HOME" >&2
    exit 1
}

NDK=$(find_ndk)
case "$(uname -s)" in
    Linux) HOST_TAG=linux-x86_64 ;;
    Darwin) HOST_TAG=darwin-x86_64 ;; # the NDK ships one universal darwin toolchain under this name
    *) echo "build-ui: unsupported host $(uname -s)" >&2; exit 1 ;;
esac
BIN="$NDK/toolchains/llvm/prebuilt/$HOST_TAG/bin"
[ -d "$BIN" ] || { echo "build-ui: no NDK toolchain at $BIN" >&2; exit 1; }

CARGO=cargo
if [ -x "$HOME/.cargo/bin/cargo" ]; then
    CARGO="$HOME/.cargo/bin/cargo"
    export PATH="$HOME/.cargo/bin:$PATH"
fi

size_of() {
    if stat -c %s "$1" >/dev/null 2>&1; then stat -c %s "$1"; else stat -f %z "$1"; fi
}

for abi in "$@"; do
    case "$abi" in
        arm64-v8a) triple=aarch64-linux-android ;;
        x86_64) triple=x86_64-linux-android ;;
        *) echo "build-ui: unknown ABI $abi (use arm64-v8a or x86_64)" >&2; exit 1 ;;
    esac
    if command -v rustup >/dev/null 2>&1 && ! rustup target list --installed | grep -qx "$triple"; then
        echo "build-ui: Rust target $triple is not installed; run: rustup target add $triple" >&2
        exit 1
    fi
    env_triple=$(echo "$triple" | tr 'a-z-' 'A-Z_')
    export "CARGO_TARGET_${env_triple}_LINKER=$BIN/${triple}${API}-clang"
    export "CC_$(echo "$triple" | tr '-' '_')=$BIN/${triple}${API}-clang"
    export "AR_$(echo "$triple" | tr '-' '_')=$BIN/llvm-ar"
    (cd "$CRATE" && "$CARGO" build --release --locked --target "$triple")
    out="$CRATE/target/$triple/release/tlstore-ui"
    "$BIN/llvm-strip" --strip-all "$out"
    mkdir -p "$CRATE/dist/$abi"
    cp "$out" "$CRATE/dist/$abi/tlstore-ui"
    printf '%-10s %8d bytes  %s\n' "$abi" "$(size_of "$CRATE/dist/$abi/tlstore-ui")" "tools/tlstore-ui/dist/$abi/tlstore-ui"
done
