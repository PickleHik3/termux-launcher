#!/data/data/com.termux/files/usr/bin/sh
set -eu

SIGYE_URL="https://github.com/am2rican5/sigye.git"
SIGYE_COMMIT="0f0b8caaccb4ca01ab5d1fad1237c4a01a49766f"
SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
TL_INSTALL_PREFIX=${TL_INSTALL_PREFIX:-"$HOME/.local"}
TL_BUILD_JOBS=${TL_BUILD_JOBS:-$(nproc)}

if [ -z "${PREFIX:-}" ] || [ ! -d "$PREFIX/bin" ]; then
    echo "error: this recipe must run inside Termux" >&2
    exit 1
fi

missing=""
for command_name in git cargo rustc; do
    if ! command -v "$command_name" >/dev/null 2>&1; then
        missing="$missing $command_name"
    fi
done
if [ -n "$missing" ]; then
    echo "error: missing build tools:$missing" >&2
    echo "install them with: pkg install git rust" >&2
    exit 1
fi

TL_BUILD_DIR=$(mktemp -d "${TMPDIR:-$PREFIX/tmp}/termux-launcher-sigye.XXXXXX")
cleanup() {
    if [ "${TL_KEEP_BUILD:-0}" = "1" ]; then
        echo "Kept build tree: $TL_BUILD_DIR"
    else
        rm -rf -- "$TL_BUILD_DIR"
    fi
}
trap cleanup EXIT HUP INT TERM

echo "Fetching Sigye v0.6.0 ($SIGYE_COMMIT)..."
git init -q "$TL_BUILD_DIR/source"
git -C "$TL_BUILD_DIR/source" remote add origin "$SIGYE_URL"
git -C "$TL_BUILD_DIR/source" fetch -q --depth 1 origin "$SIGYE_COMMIT"
git -C "$TL_BUILD_DIR/source" checkout -q --detach FETCH_HEAD

echo "Applying the Termux clipboard compatibility patch..."
git -C "$TL_BUILD_DIR/source" apply --check "$SCRIPT_DIR/0001-termux-clipboard.patch"
git -C "$TL_BUILD_DIR/source" apply "$SCRIPT_DIR/0001-termux-clipboard.patch"

echo "Building Sigye..."
cargo build \
    --manifest-path "$TL_BUILD_DIR/source/Cargo.toml" \
    --locked \
    --release \
    --package sigye \
    --jobs "$TL_BUILD_JOBS"

mkdir -p "$TL_INSTALL_PREFIX/bin"
install -m 755 "$TL_BUILD_DIR/source/target/release/sigye" "$TL_INSTALL_PREFIX/bin/sigye"

echo "Installed $TL_INSTALL_PREFIX/bin/sigye"
"$TL_INSTALL_PREFIX/bin/sigye" --version

case ":$PATH:" in
    *":$TL_INSTALL_PREFIX/bin:"*) ;;
    *) echo "Add $TL_INSTALL_PREFIX/bin to PATH before running sigye." ;;
esac
