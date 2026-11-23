#!/usr/bin/env bash
# Builds the Wayland host spike (wayland-host/) for arm64-v8a and x86_64:
#   - libxkbcommon as a static library (smithay links it unconditionally),
#   - libtlwayland.so, copied into app/src/main/jniLibs/<abi>/ (gitignored),
#   - the test client, left in wayland-host/target/<triple>/release/tlwayland-testclient.
# Needs: rustup with the two Android targets, cargo-ndk, the NDK, ninja, bison, and meson
# (taken from PATH, else run through `uvx`).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CRATE="$ROOT/wayland-host"
TARGET_DIR="$CRATE/target"
export PATH="$HOME/.cargo/bin:$PATH"
export ANDROID_NDK_HOME="${ANDROID_NDK_HOME:-$HOME/Android/Sdk/ndk/29.0.14206865}"
API=26
ABIS=(arm64-v8a x86_64)

XKB_VERSION=1.9.2
XKB_SHA256=8d68a8b45796f34f7cace357b9f89b8c92b158557274fef5889b03648b55fe59
XKB_URL="https://github.com/xkbcommon/libxkbcommon/archive/refs/tags/xkbcommon-$XKB_VERSION.tar.gz"

if command -v meson >/dev/null 2>&1; then
    MESON=(meson)
else
    MESON=(uvx --from meson==1.6.1 meson)
fi

triple_for() {
    case "$1" in
        arm64-v8a) echo aarch64-linux-android ;;
        x86_64) echo x86_64-linux-android ;;
    esac
}

cpu_for() {
    case "$1" in
        arm64-v8a) echo aarch64 ;;
        x86_64) echo x86_64 ;;
    esac
}

build_xkbcommon() {
    local abi="$1" triple cpu src prefix build cross llvm
    triple="$(triple_for "$abi")"
    cpu="$(cpu_for "$abi")"
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
c = '$llvm/$triple$API-clang'
ar = '$llvm/llvm-ar'
strip = '$llvm/llvm-strip'

[host_machine]
system = 'android'
cpu_family = '$cpu'
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

for abi in "${ABIS[@]}"; do
    build_xkbcommon "$abi"
done

# The static libxkbcommon.a satisfies the xkbcommon crate's `-lxkbcommon`.
export CARGO_TARGET_AARCH64_LINUX_ANDROID_RUSTFLAGS="-L native=$TARGET_DIR/xkbcommon/arm64-v8a/lib"
export CARGO_TARGET_X86_64_LINUX_ANDROID_RUSTFLAGS="-L native=$TARGET_DIR/xkbcommon/x86_64/lib"

cd "$CRATE"
NDK_ARGS=()
for abi in "${ABIS[@]}"; do
    NDK_ARGS+=(-t "$abi")
done

cargo ndk "${NDK_ARGS[@]}" -P "$API" -o "$ROOT/app/src/main/jniLibs" build --release -p tlwayland
cargo ndk "${NDK_ARGS[@]}" -P "$API" build --release -p tlwayland-testclient

for abi in "${ABIS[@]}"; do
    so="$ROOT/app/src/main/jniLibs/$abi/libtlwayland.so"
    client="$TARGET_DIR/$(triple_for "$abi")/release/tlwayland-testclient"
    echo "$abi: $(stat -c %s "$so") bytes $so"
    echo "$abi: $(stat -c %s "$client") bytes $client"
done
