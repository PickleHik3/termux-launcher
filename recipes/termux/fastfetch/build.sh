#!/data/data/com.termux/files/usr/bin/sh
set -eu

FASTFETCH_URL="https://github.com/fastfetch-cli/fastfetch.git"
FASTFETCH_COMMIT="9c7cfb864ff9154ffe951fae191c14d60bb91544"
SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
TL_INSTALL_PREFIX=${TL_INSTALL_PREFIX:-"$HOME/.local"}
TL_BUILD_JOBS=${TL_BUILD_JOBS:-$(nproc)}

if [ -z "${PREFIX:-}" ] || [ ! -d "$PREFIX/bin" ]; then
    echo "error: this recipe must run inside Termux" >&2
    exit 1
fi

missing=""
for command_name in git clang cmake ninja pkg-config; do
    if ! command -v "$command_name" >/dev/null 2>&1; then
        missing="$missing $command_name"
    fi
done
if [ -n "$missing" ]; then
    echo "error: missing build tools:$missing" >&2
    echo "install dependencies with:" >&2
    echo "  pkg install git clang cmake ninja pkg-config linux-headers imagemagick chafa zlib" >&2
    exit 1
fi

if ! pkg-config --exists ImageMagick-7.Q16HDRI && \
   ! pkg-config --exists ImageMagick-7.Q16 && \
   ! pkg-config --exists ImageMagick-7; then
    echo "error: ImageMagick 7 development files are unavailable" >&2
    echo "install dependencies with:" >&2
    echo "  pkg install linux-headers imagemagick chafa zlib" >&2
    exit 1
fi

TL_BUILD_DIR=$(mktemp -d "${TMPDIR:-$PREFIX/tmp}/termux-launcher-fastfetch.XXXXXX")
cleanup() {
    if [ "${TL_KEEP_BUILD:-0}" = "1" ]; then
        echo "Kept build tree: $TL_BUILD_DIR"
    else
        rm -rf -- "$TL_BUILD_DIR"
    fi
}
trap cleanup EXIT HUP INT TERM

echo "Fetching Fastfetch v2.67.0 ($FASTFETCH_COMMIT)..."
git init -q "$TL_BUILD_DIR/source"
git -C "$TL_BUILD_DIR/source" remote add origin "$FASTFETCH_URL"
git -C "$TL_BUILD_DIR/source" fetch -q --depth 1 origin "$FASTFETCH_COMMIT"
git -C "$TL_BUILD_DIR/source" checkout -q --detach FETCH_HEAD

echo "Applying the animated Kitty graphics patch..."
git -C "$TL_BUILD_DIR/source" apply --check "$SCRIPT_DIR/0001-kitty-animation.patch"
git -C "$TL_BUILD_DIR/source" apply "$SCRIPT_DIR/0001-kitty-animation.patch"

echo "Configuring Fastfetch with ImageMagick, zlib, and Chafa..."
cmake \
    -S "$TL_BUILD_DIR/source" \
    -B "$TL_BUILD_DIR/source/build" \
    -G Ninja \
    -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_INSTALL_PREFIX="$TL_INSTALL_PREFIX" \
    -DBUILD_TESTS=OFF \
    -DBUILD_FLASHFETCH=OFF \
    -DSET_TWEAK=OFF \
    -DENABLE_VULKAN=OFF \
    -DENABLE_WAYLAND=OFF \
    -DENABLE_XCB_RANDR=OFF \
    -DENABLE_XRANDR=OFF \
    -DENABLE_DRM=OFF \
    -DENABLE_VADRM=OFF \
    -DENABLE_VAX11=OFF \
    -DENABLE_VDPAU=OFF \
    -DENABLE_GIO=OFF \
    -DENABLE_DCONF=OFF \
    -DENABLE_EET=OFF \
    -DENABLE_DBUS=OFF \
    -DENABLE_IMAGEMAGICK7=ON \
    -DENABLE_IMAGEMAGICK6=OFF \
    -DENABLE_ZLIB=ON \
    -DENABLE_CHAFA=ON \
    -DENABLE_EGL=OFF \
    -DENABLE_GLX=OFF \
    -DENABLE_OPENCL=OFF \
    -DENABLE_FREETYPE=OFF \
    -DENABLE_PULSE=OFF \
    -DENABLE_ELF=OFF \
    -DENABLE_LUA=OFF \
    -DENABLE_QUICKJS=OFF \
    -DENABLE_LIBZFS=OFF

cmake --build "$TL_BUILD_DIR/source/build" --parallel "$TL_BUILD_JOBS"

features=$("$TL_BUILD_DIR/source/build/fastfetch" --list-features)
if ! printf '%s\n' "$features" | grep -qx 'imagemagick7'; then
    echo "error: Fastfetch was built without ImageMagick 7 support" >&2
    exit 1
fi
if ! printf '%s\n' "$features" | grep -qx 'zlib'; then
    echo "error: Fastfetch was built without zlib support" >&2
    exit 1
fi

cmake --install "$TL_BUILD_DIR/source/build"

echo "Installed $TL_INSTALL_PREFIX/bin/fastfetch"
"$TL_INSTALL_PREFIX/bin/fastfetch" --version

case ":$PATH:" in
    *":$TL_INSTALL_PREFIX/bin:"*) ;;
    *) echo "Add $TL_INSTALL_PREFIX/bin to PATH before running fastfetch." ;;
esac
