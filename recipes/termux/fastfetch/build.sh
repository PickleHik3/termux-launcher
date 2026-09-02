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

# The Kitty transmission header always says f=32 — four 8-bit channels — but ImageMagick writes the
# raw blob at the image's own depth, so a logo that is not already 8-bit transmits a payload of the
# wrong length and the terminal drops it without a word, because Fastfetch asks for silence with
# q=2. The patch normalises every frame to 8 bits; this checks that it still does, across the depths
# a real logo arrives in. Rendering a logo needs a pty, so the check runs only where python3 can open
# one: a missing interpreter skips it, a wrong payload fails the build.
if command -v python3 >/dev/null 2>&1 && command -v magick >/dev/null 2>&1; then
    echo "Checking the Kitty logo transmission..."
    smoke_dir="$TL_BUILD_DIR/smoke"
    mkdir -p "$smoke_dir"
    magick -size 48x48 xc:red "$smoke_dir/depth1.png"
    magick -size 48x48 xc:red -depth 8 -define png:color-type=6 "$smoke_dir/depth8.png"
    magick -size 48x48 gradient:red-blue "$smoke_dir/depth16.png"
    magick -delay 20 -size 48x48 xc:red -size 48x48 xc:blue -loop 0 "$smoke_dir/animated.gif"
    FF_SMOKE_BIN="$TL_BUILD_DIR/source/build/fastfetch" FF_SMOKE_DIR="$smoke_dir" python3 - <<'SMOKE'
import base64, fcntl, os, pty, re, select, struct, sys, termios, zlib

binary = os.environ["FF_SMOKE_BIN"]
directory = os.environ["FF_SMOKE_DIR"]
os.environ["TERM"] = "xterm-kitty"
os.environ["XDG_CACHE_HOME"] = os.path.join(directory, "cache")  # not the caller's own
LOGOS = ("depth1.png", "depth8.png", "depth16.png", "animated.gif")


def render(image):
    pid, fd = pty.fork()
    if pid == 0:
        # 80x24 cells over 640x576 pixels: Fastfetch reads the cell size from here to size the logo.
        fcntl.ioctl(0, termios.TIOCSWINSZ, struct.pack("HHHH", 24, 80, 640, 576))
        os.execv(binary, [binary, "--logo-type", "kitty", "--logo", image,
                          "--logo-recache", "true", "-s", "Title"])
    out = b""
    while select.select([fd], [], [], 30)[0]:
        try:
            chunk = os.read(fd, 65536)
        except OSError:
            break
        if not chunk:
            break
        out += chunk
    os.waitpid(pid, 0)
    return out


def frames(data):
    """One (declared, actual) byte count per transmitted frame; a chunked frame ends at m != 1."""
    found, control, encoded = [], None, b""
    for match in re.finditer(rb"\x1b_G([^;]*);([^\x1b]*)\x1b\\", data):
        keys = dict(p.split("=", 1) for p in match.group(1).decode().split(",") if "=" in p)
        if "s" in keys and "v" in keys:
            control, encoded = keys, b""
        encoded += match.group(2)
        if keys.get("m", "0") != "1" and control is not None:
            blob = base64.b64decode(encoded)
            if control.get("o") == "z":
                blob = zlib.decompress(blob)
            found.append((int(control["s"]) * int(control["v"]) * (int(control["f"]) // 8), len(blob)))
            control, encoded = None, b""
    return found


failed = False
for name in LOGOS:
    transmitted = frames(render(os.path.join(directory, name)))
    if not transmitted:
        print("error: %s transmitted no image at all" % name, file=sys.stderr)
        failed = True
    for index, (declared, actual) in enumerate(transmitted):
        if declared != actual:
            print("error: %s frame %d declares %d bytes and transmits %d — the blob is not 8 bits "
                  "per channel and a terminal will drop it" % (name, index, declared, actual),
                  file=sys.stderr)
            failed = True

sys.exit(1 if failed else 0)
SMOKE
else
    echo "note: python3 and ImageMagick's magick(1) are needed for the Kitty logo check, skipping it"
fi

cmake --install "$TL_BUILD_DIR/source/build"

echo "Installed $TL_INSTALL_PREFIX/bin/fastfetch"
"$TL_INSTALL_PREFIX/bin/fastfetch" --version

case ":$PATH:" in
    *":$TL_INSTALL_PREFIX/bin:"*) ;;
    *) echo "Add $TL_INSTALL_PREFIX/bin to PATH before running fastfetch." ;;
esac
