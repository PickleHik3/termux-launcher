#!/usr/bin/env bash
# Builds the Wayland host spike (wayland-host/) for arm64-v8a, x86_64 and armeabi-v7a (x86 on
# request, see WAYLAND_ABIS below):
#   - libxkbcommon as a static library (smithay links it unconditionally),
#   - smithay 0.7.0 with wayland-host/patches/*.patch applied (its Xwayland support hardcodes
#     /tmp, which Android does not have), in wayland-host/target/patched/,
#   - libtlwayland.so, copied into <outdir>/<abi>/ (first argument; Gradle passes
#     app/build/wayland/jniLibs; default wayland-host/target/jniLibs) and stripped there; the
#     unstripped copy (line tables, for ndk-stack) stays in <outdir>/../symbols/<abi>/, which no
#     APK carries,
#   - the test client, left in wayland-host/target/<triple>/release/tlwayland-testclient.
# Run by Gradle with -Pwayland=true; by hand: scripts/build-wayland-host.sh [outdir].
# Needs: rustup with the Android targets (aarch64-linux-android, x86_64-linux-android,
# armv7-linux-androideabi, i686-linux-android for x86), cargo-ndk, the NDK, ninja, bison, and meson
# (taken from PATH, else run through `uvx`).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CRATE="$ROOT/wayland-host"
TARGET_DIR="$CRATE/target"
export PATH="$HOME/.cargo/bin:$PATH"
OUT_DIR="${1:-$TARGET_DIR/jniLibs}"
# Gradle sets ANDROID_NDK_HOME from the project's ndkVersion.
export ANDROID_NDK_HOME="${ANDROID_NDK_HOME:-$HOME/Android/Sdk/ndk/29.0.14206865}"
API=26
# The ABIs the app ships (app/build.gradle abiFilters). x86 also builds, but no APK carries it:
# WAYLAND_ABIS="arm64-v8a x86_64 armeabi-v7a x86".
read -r -a ABIS <<< "${WAYLAND_ABIS:-arm64-v8a x86_64 armeabi-v7a}"

SMITHAY_VERSION=0.7.0
SMITHAY_SHA256=740cea6927892bc182d5bf70c8f79806c8bc9f68f2fb96e55a30be171b63af98

XKB_VERSION=1.9.2
XKB_SHA256=8d68a8b45796f34f7cace357b9f89b8c92b158557274fef5889b03648b55fe59
XKB_URL="https://github.com/xkbcommon/libxkbcommon/archive/refs/tags/xkbcommon-$XKB_VERSION.tar.gz"

if command -v meson >/dev/null 2>&1; then
    MESON=(meson)
else
    MESON=(uvx --from meson==1.6.1 meson)
fi

# Rust's target triple.
triple_for() {
    case "$1" in
        arm64-v8a) echo aarch64-linux-android ;;
        x86_64) echo x86_64-linux-android ;;
        armeabi-v7a) echo armv7-linux-androideabi ;;
        x86) echo i686-linux-android ;;
    esac
}

# The NDK's clang prefix, which differs from Rust's triple for armv7.
clang_for() {
    case "$1" in
        armeabi-v7a) echo armv7a-linux-androideabi ;;
        *) triple_for "$1" ;;
    esac
}

cpu_family_for() {
    case "$1" in
        arm64-v8a) echo aarch64 ;;
        x86_64) echo x86_64 ;;
        armeabi-v7a) echo arm ;;
        x86) echo x86 ;;
    esac
}

cpu_for() {
    case "$1" in
        arm64-v8a) echo aarch64 ;;
        x86_64) echo x86_64 ;;
        armeabi-v7a) echo armv7 ;;
        x86) echo i686 ;;
    esac
}

build_xkbcommon() {
    local abi="$1" clang cpu family src prefix build cross llvm
    clang="$(clang_for "$abi")"
    cpu="$(cpu_for "$abi")"
    family="$(cpu_family_for "$abi")"
    src="$TARGET_DIR/xkbcommon/src/libxkbcommon-xkbcommon-$XKB_VERSION"
    prefix="$TARGET_DIR/xkbcommon/$abi"
    build="$TARGET_DIR/xkbcommon/build-$abi"
    if [[ -f "$prefix/lib/libxkbcommon.a" ]]; then
        return
    fi
    if [[ ! -d "$src" ]]; then
        mkdir -p "$TARGET_DIR/xkbcommon/src"
        local tarball="$TARGET_DIR/xkbcommon/xkbcommon-$XKB_VERSION.tar.gz"
        curl -sSL -o "$tarball" "$XKB_URL"
        echo "$XKB_SHA256  $tarball" | sha256sum -c -
        tar -xzf "$tarball" -C "$TARGET_DIR/xkbcommon/src"
    fi
    llvm="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin"
    cross="$TARGET_DIR/xkbcommon/cross-$abi.txt"
    cat > "$cross" <<EOF
[binaries]
c = '$llvm/$clang$API-clang'
ar = '$llvm/llvm-ar'
strip = '$llvm/llvm-strip'

[host_machine]
system = 'android'
cpu_family = '$family'
cpu = '$cpu'
endian = 'little'
EOF
    rm -rf "$build"
    # XKB_CONFIG_ROOT is set at run time by the app (the prefix differs per edition); the
    # compiled-in root is never meant to exist.
    "${MESON[@]}" setup "$build" "$src" \
        --cross-file "$cross" \
        --prefix "$prefix" \
        --libdir lib \
        --buildtype release \
        -Ddefault_library=static \
        -Denable-tools=false \
        -Denable-x11=false \
        -Denable-wayland=false \
        -Denable-xkbregistry=false \
        -Denable-docs=false \
        -Denable-bash-completion=false \
        -Dxkb-config-root=/nonexistent/xkb \
        -Dx-locale-root=/nonexistent/locale >/dev/null
    ninja -C "$build" >/dev/null
    ninja -C "$build" install >/dev/null
}

prepare_smithay() {
    local dir="$TARGET_DIR/patched/smithay-$SMITHAY_VERSION"
    local crate="$TARGET_DIR/patched/smithay-$SMITHAY_VERSION.crate"
    local patches=("$CRATE"/patches/smithay-"$SMITHAY_VERSION"-*.patch)
    local stamp
    stamp="$(cat "${patches[@]}" | sha256sum | cut -d' ' -f1)"
    if [[ -f "$dir/.patched" && "$(cat "$dir/.patched")" == "$stamp" ]]; then
        return
    fi
    rm -rf "$dir"
    mkdir -p "$TARGET_DIR/patched"
    if [[ ! -f "$crate" ]]; then
        curl -sSL -o "$crate" "https://static.crates.io/crates/smithay/smithay-$SMITHAY_VERSION.crate"
    fi
    echo "$SMITHAY_SHA256  $crate" | sha256sum -c -
    tar -xzf "$crate" -C "$TARGET_DIR/patched"
    for p in "${patches[@]}"; do
        patch -d "$dir" -p1 < "$p"
    done
    echo "$stamp" > "$dir/.patched"
}

# Cargo resolves the [patch] path before anything builds, so this comes first (also before a
# plain `cargo test` on the host).
prepare_smithay

fail() {
    echo "build-wayland-host: $1" >&2
    exit 1
}

command -v cargo >/dev/null 2>&1 || fail "Rust is missing: install rustup (cargo is looked up in \$HOME/.cargo/bin)"
command -v cargo-ndk >/dev/null 2>&1 || fail "cargo-ndk is missing: run 'cargo install cargo-ndk'"
[[ -d "$ANDROID_NDK_HOME" ]] || fail "the NDK is missing at $ANDROID_NDK_HOME: set ANDROID_NDK_HOME"
INSTALLED_TARGETS="$(rustup target list --installed 2>/dev/null || true)"
for abi in "${ABIS[@]}"; do
    grep -qx "$(triple_for "$abi")" <<< "$INSTALLED_TARGETS" \
        || fail "the Rust target $(triple_for "$abi") is missing: run 'rustup target add $(triple_for "$abi")'"
done

for abi in "${ABIS[@]}"; do
    build_xkbcommon "$abi"
done

# The static libxkbcommon.a satisfies the xkbcommon crate's `-lxkbcommon`.
export CARGO_TARGET_AARCH64_LINUX_ANDROID_RUSTFLAGS="-L native=$TARGET_DIR/xkbcommon/arm64-v8a/lib"
export CARGO_TARGET_X86_64_LINUX_ANDROID_RUSTFLAGS="-L native=$TARGET_DIR/xkbcommon/x86_64/lib"
export CARGO_TARGET_ARMV7_LINUX_ANDROIDEABI_RUSTFLAGS="-L native=$TARGET_DIR/xkbcommon/armeabi-v7a/lib"
export CARGO_TARGET_I686_LINUX_ANDROID_RUSTFLAGS="-L native=$TARGET_DIR/xkbcommon/x86/lib"

cd "$CRATE"
NDK_ARGS=()
for abi in "${ABIS[@]}"; do
    NDK_ARGS+=(-t "$abi")
done

cargo ndk "${NDK_ARGS[@]}" -P "$API" -o "$OUT_DIR" build --release -p tlwayland
cargo ndk "${NDK_ARGS[@]}" -P "$API" build --release -p tlwayland-testclient

# Keep the unstripped library for symbolizing tombstones; package a stripped one.
SYMBOLS_DIR="$(dirname "$OUT_DIR")/symbols"
STRIP="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip"
[[ -x "$STRIP" ]] || fail "llvm-strip is missing at $STRIP"
for abi in "${ABIS[@]}"; do
    so="$OUT_DIR/$abi/libtlwayland.so"
    mkdir -p "$SYMBOLS_DIR/$abi"
    cp "$so" "$SYMBOLS_DIR/$abi/libtlwayland.so"
    "$STRIP" --strip-all "$so"
    echo "$abi: symbols in $SYMBOLS_DIR/$abi/libtlwayland.so"
done

for abi in "${ABIS[@]}"; do
    so="$OUT_DIR/$abi/libtlwayland.so"
    client="$TARGET_DIR/$(triple_for "$abi")/release/tlwayland-testclient"
    echo "$abi: $(stat -c %s "$so") bytes $so"
    echo "$abi: $(stat -c %s "$client") bytes $client"
done
