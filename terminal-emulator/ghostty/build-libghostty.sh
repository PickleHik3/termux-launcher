#!/usr/bin/env bash
# Builds libghostty-vt and links it statically into the JNI shim, libtermux-ghostty.so, for the
# experimental Ghostty terminal core. One library per ABI: upstream's shared library is named
# libghostty-vt.so.1, a versioned soname an APK cannot carry.
#
# Usage: build-libghostty.sh <jniLibs output dir> <abi>...
#   ABIs: arm64-v8a, x86_64 (Android), host (x86_64 Linux, for JVM tests).
#
# Only runs when the build opts in with -Pghostty=true. Sources come from the pinned release
# tarballs, checked against their SHA-256; downloads, the unpacked tree and Zig's caches live under
# $GHOSTTY_CACHE_DIR (default ~/.cache/termux-launcher-ghostty), never in the repo.
#
# Needs: zig 0.17.x, curl, sha256sum, and the NDK (ANDROID_NDK_HOME, or the SDK's ndk/<version>).
set -euo pipefail

VERSION=1.0.0
SRC_SHA256=4a6ef758be721923e1969e7b81b443dcc92d8dd3fb5a39600c03a8817a753676
DEPS_SHA256=4d5ea5e448e4c7ea8fb55ccbaa8668f2a97d44f438cee831e2b7ecd162d85c29
BASE_URL="https://release.files.ghostty.org/libghostty-vt/$VERSION"
API_LEVEL=26

OUT_DIR=${1:?output dir}
shift
[ $# -gt 0 ] || { echo "no ABIs given" >&2; exit 2; }

HERE=$(cd "$(dirname "$0")" && pwd)
CACHE=${GHOSTTY_CACHE_DIR:-$HOME/.cache/termux-launcher-ghostty}
mkdir -p "$CACHE/dl" "$CACHE/src"

fetch() {
    local name=$1 sum=$2 file="$CACHE/dl/$1"
    if [ ! -f "$file" ] || ! echo "$sum  $file" | sha256sum -c --quiet - 2>/dev/null; then
        curl -fsSL --retry 2 -o "$file.part" "$BASE_URL/$name"
        mv "$file.part" "$file"
    fi
    echo "$sum  $file" | sha256sum -c --quiet - || { echo "checksum mismatch: $name" >&2; rm -f "$file"; exit 1; }
}

SRC="$CACHE/src/libghostty-vt-$VERSION"
DEPS="$CACHE/src/libghostty-vt-$VERSION-deps"
if [ ! -f "$SRC/LIB_VERSION" ] || [ ! -d "$DEPS" ]; then
    fetch "libghostty-vt-$VERSION.tar.gz" "$SRC_SHA256"
    fetch "libghostty-vt-$VERSION-deps.tar.gz" "$DEPS_SHA256"
    tar -xzf "$CACHE/dl/libghostty-vt-$VERSION.tar.gz" -C "$CACHE/src"
    tar -xzf "$CACHE/dl/libghostty-vt-$VERSION-deps.tar.gz" -C "$CACHE/src"
fi

find_ndk() {
    if [ -n "${ANDROID_NDK_HOME:-}" ]; then echo "$ANDROID_NDK_HOME"; return; fi
    local sdk=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}
    if [ -n "${NDK_VERSION:-}" ] && [ -d "$sdk/ndk/$NDK_VERSION" ]; then echo "$sdk/ndk/$NDK_VERSION"; return; fi
    ls -d "$sdk"/ndk/* 2>/dev/null | sort -V | tail -1
}

for ABI in "$@"; do
    case "$ABI" in
        arm64-v8a) ZIG_TARGET=aarch64-linux-android.$API_LEVEL; CLANG_TARGET=aarch64-linux-android$API_LEVEL ;;
        x86_64) ZIG_TARGET=x86_64-linux-android.$API_LEVEL; CLANG_TARGET=x86_64-linux-android$API_LEVEL ;;
        host) ZIG_TARGET=native; CLANG_TARGET= ;;
        *) echo "unsupported ABI: $ABI" >&2; exit 2 ;;
    esac

    PREFIX="$CACHE/out/$ABI"
    STAMP="$PREFIX/.lib-stamp"
    WANT="$VERSION $ZIG_TARGET"
    DEST="$OUT_DIR/$ABI"
    SHIM="$DEST/libtermux-ghostty.so"

    if [ "$ABI" != host ]; then
        export ANDROID_NDK_HOME
        ANDROID_NDK_HOME=$(find_ndk)
        [ -d "$ANDROID_NDK_HOME" ] || { echo "Android NDK not found" >&2; exit 1; }
    fi

    # The library: rebuilt only when the version or target changes (Zig's cache makes a rerun cheap).
    if [ ! -f "$STAMP" ] || [ "$(cat "$STAMP")" != "$WANT" ] || [ ! -f "$PREFIX/lib/libghostty-vt.a" ]; then
        (cd "$SRC" && ZIG_GLOBAL_CACHE_DIR="$CACHE/zig-global" ZIG_LOCAL_CACHE_DIR="$CACHE/zig-local" \
            zig build --prefix "$PREFIX" --system "$DEPS" \
                -Demit-lib-vt -Dtarget="$ZIG_TARGET" -Doptimize=ReleaseFast -Dcpu=baseline -Dstrip=true)
        echo "$WANT" > "$STAMP"
    fi
    # The shim: relinked when it or the library is newer than the last output.
    if [ -f "$SHIM" ] && [ "$SHIM" -nt "$HERE/ghostty_jni.c" ] && [ "$SHIM" -nt "$STAMP" ]; then
        continue
    fi

    mkdir -p "$DEST"
    if [ "$ABI" = host ]; then
        JAVA_HOME=${JAVA_HOME:-$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")}
        cc -shared -fPIC -O2 -Wall -Wextra -Werror -std=c11 \
            -I"$PREFIX/include" -I"$JAVA_HOME/include" -I"$JAVA_HOME/include/linux" \
            -o "$DEST/libtermux-ghostty.so" "$HERE/ghostty_jni.c" "$PREFIX/lib/libghostty-vt.a" \
            -lm -Wl,--gc-sections -Wl,--exclude-libs,ALL -Wl,--no-undefined
    else
        TOOLS="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin"
        "$TOOLS/clang" --target="$CLANG_TARGET" -shared -fPIC -Os -Wall -Wextra -Werror -std=c11 \
            -I"$PREFIX/include" \
            -o "$DEST/libtermux-ghostty.so" "$HERE/ghostty_jni.c" "$PREFIX/lib/libghostty-vt.a" \
            -lm -llog -Wl,--gc-sections -Wl,--exclude-libs,ALL -Wl,--no-undefined \
            -Wl,-z,max-page-size=16384
        "$TOOLS/llvm-strip" --strip-unneeded "$DEST/libtermux-ghostty.so"
    fi
    echo "$WANT" > "$STAMP"
done
