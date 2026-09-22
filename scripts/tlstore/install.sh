#!/bin/sh
# Standalone installer for tlstore on official Termux (and any other Termux
# fork that was not built with the launcher). Puts tlstore in place the same
# way the launcher app does, so the two never fight over the files, and the
# app takes the script over later if the launcher is installed.
#
#   curl -fsSL https://raw.githubusercontent.com/PickleHik3/termux-launcher/main/scripts/tlstore/install.sh | sh
#
# POSIX sh only (Termux's sh is dash): no locals, no arrays, no [[ ]].
# Functions share one variable namespace — every helper prefixes its
# variables (ii_, …) so this file stays safe to extend.
#
# Env:
#   TLSTORE_RAW_BASE    where the files are fetched from (default the raw
#                        GitHub URL for this repository's main branch); tests
#                        point this at a file:// tree shaped the same way
#   TLSTORE_PREFIX      install prefix (tests point it at a fake Termux tree)
#   TLSTORE_ASSUME_YES  1 = same as -y
#   TLSTORE_ARCH        override uname -m (tests exercise aarch64 on a host)
#
# Flags:
#   -y   answer every question yes (installing minisign)
set -u

err() { printf 'tlstore: %s\n' "$*" >&2; }
say() { printf '%s\n' "$*"; }
have() { command -v "$1" >/dev/null 2>&1; }

yes_reply() {
    case "$1" in
        y|Y|yes|YES) return 0 ;;
    esac
    return 1
}

ASSUME_YES="${TLSTORE_ASSUME_YES:-0}"
[ "$ASSUME_YES" = 1 ] || ASSUME_YES=0

for ii_a in "$@"; do
    case "$ii_a" in
        -y) ASSUME_YES=1 ;;
        *) err "usage: install.sh [-y]"; exit 2 ;;
    esac
done

# A yes/no question. -y (or TLSTORE_ASSUME_YES) answers it without asking.
# Under `curl ... | sh` stdin is the script itself, not a person, so a plain
# `read` here would consume a line of the installer and never match yes —
# this reads from /dev/tty instead whenever stdin is not a terminal. With no
# terminal on stdin and no /dev/tty to open either, there is nobody to ask,
# and the answer is no rather than guessing.
ask_yn() {
    [ "$ASSUME_YES" = 1 ] && return 0
    if [ -t 0 ]; then
        printf '%s [Y/n] ' "$1"
        read -r ii_answer || ii_answer=""
    else
        printf '%s [Y/n] ' "$1" > /dev/tty 2>/dev/null || return 1
        read -r ii_answer < /dev/tty 2>/dev/null || return 1
    fi
    [ -n "$ii_answer" ] || ii_answer=y
    yes_reply "$ii_answer"
}

# ---------------------------------------------------------------------------
# Where this is going
# ---------------------------------------------------------------------------

PREFIX="${TLSTORE_PREFIX:-${PREFIX:-}}"
case "$PREFIX" in
    */data/data/*/files/usr) ;;
    *)
        err "this installs into a Termux prefix, and $PREFIX does not look like one"
        exit 1
        ;;
esac

APP_PACKAGE=""
case "$PREFIX" in
    */files/usr)
        APP_PACKAGE="${PREFIX%/files/usr}"
        APP_PACKAGE="${APP_PACKAGE##*/}"
        ;;
esac

BIN_DIR="$PREFIX/bin"
STORE_DIR="$PREFIX/libexec/termux-launcher/tlstore"
MARKER='# written by termux-launcher'

# Termux Launcher is already here — its own installer keeps tlstore current
# on every start, so there is nothing for this one to do.
if [ "${TERM_PROGRAM:-}" = termux-launcher ] || [ -e "$STORE_DIR/.installed" ]; then
    say "Termux Launcher already keeps tlstore up to date here — there is nothing for this to do."
    exit 0
fi

# A tlstore already here that this installer (or the app) did not write is
# someone else's — refuse to take it over silently, the same as the app does.
ii_tlstore_is_foreign() {
    [ -L "$BIN_DIR/tlstore" ] && return 0
    [ -e "$BIN_DIR/tlstore" ] || return 1
    head -5 "$BIN_DIR/tlstore" 2>/dev/null | grep -qF "$MARKER" && return 1
    return 0
}
if ii_tlstore_is_foreign && [ "$ASSUME_YES" != 1 ]; then
    err "there is already a tlstore here that this did not write — rerun with -y to replace it"
    exit 1
fi

ARCH="${TLSTORE_ARCH:-$(uname -m 2>/dev/null || echo unknown)}"
case "$ARCH" in
    aarch64|arm64) ;;
    *) say "This phone's processor is not the one tlstore's prebuilt tools are built for, so a few items will be missing." ;;
esac

have curl || { err "curl is required to install tlstore — install it first"; exit 1; }

# ---------------------------------------------------------------------------
# minisign
# ---------------------------------------------------------------------------

if ! have minisign; then
    if ask_yn "tlstore needs minisign to check what it downloads. Install it?"; then
        have pkg && pkg install -y minisign >/dev/null 2>&1
    fi
    if ! have minisign; then
        err "minisign is required — install it and run this again"
        exit 1
    fi
fi

# ---------------------------------------------------------------------------
# Fetch
# ---------------------------------------------------------------------------

BASE="${TLSTORE_RAW_BASE:-https://raw.githubusercontent.com/PickleHik3/termux-launcher/main}"
ASSET_BASE="$BASE/app/src/main/assets/tlstore"

TMP="$(mktemp -d)" || { err "could not create a place to work"; exit 1; }
trap 'rm -rf "$TMP"' EXIT INT TERM

fetch() {
    curl -fsSL -o "$2" "$1" 2>/dev/null
}

if ! fetch "$ASSET_BASE/trusted.pub" "$TMP/trusted.pub"; then
    err "could not reach $ASSET_BASE — check your connection and try again"
    exit 1
fi
if ! fetch "$ASSET_BASE/tlstore" "$TMP/tlstore" || ! fetch "$ASSET_BASE/tlstore.minisig" "$TMP/tlstore.minisig"; then
    err "could not download tlstore — check your connection and try again"
    exit 1
fi
if ! fetch "$ASSET_BASE/catalog.tsv" "$TMP/catalog.tsv" || ! fetch "$ASSET_BASE/catalog.tsv.minisig" "$TMP/catalog.tsv.minisig"; then
    err "could not download the item list — check your connection and try again"
    exit 1
fi

# ---------------------------------------------------------------------------
# Verify
# ---------------------------------------------------------------------------

if ! minisign -Q -V -p "$TMP/trusted.pub" -x "$TMP/tlstore.minisig" -m "$TMP/tlstore" >/dev/null 2>&1; then
    err "tlstore did not check out against its signature — not installing it"
    exit 1
fi
if ! minisign -Q -V -p "$TMP/trusted.pub" -x "$TMP/catalog.tsv.minisig" -m "$TMP/catalog.tsv" >/dev/null 2>&1; then
    err "the item list did not check out against its signature — not installing it"
    exit 1
fi

# ---------------------------------------------------------------------------
# Install
# ---------------------------------------------------------------------------

mkdir -p "$BIN_DIR" "$STORE_DIR" || { err "could not create $STORE_DIR"; exit 1; }

ii_tmp="$BIN_DIR/.tlstore.$$.tmp"
cp "$TMP/tlstore" "$ii_tmp" && chmod 755 "$ii_tmp" && mv -f "$ii_tmp" "$BIN_DIR/tlstore" || {
    rm -f "$ii_tmp"
    err "could not write $BIN_DIR/tlstore"
    exit 1
}

for ii_alias in tl tls; do
    ii_path="$BIN_DIR/$ii_alias"
    if [ -e "$ii_path" ] || [ -L "$ii_path" ]; then
        say "$ii_alias is already something else here — leaving it alone"
    else
        ln -s tlstore "$ii_path"
    fi
done

cp "$TMP/catalog.tsv" "$STORE_DIR/catalog.tsv"
cp "$TMP/trusted.pub" "$STORE_DIR/trusted.pub"
printf '%s\n' "$BASE" > "$STORE_DIR/.standalone"
rm -f "$STORE_DIR/.installed"

# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------

ii_line() { printf '%-13s %s\n' "$1" "$2"; }

say ""
ii_line Prefix "$PREFIX"
[ -z "$APP_PACKAGE" ] || ii_line Edition "$APP_PACKAGE"
ii_line tlstore "$BIN_DIR/tlstore"
ii_serial="$(sed -n 's/^#.*serial=\([0-9][0-9]*\).*$/\1/p' "$STORE_DIR/catalog.tsv" | head -1)"
[ -z "$ii_serial" ] || ii_line "Item list" "$ii_serial"
say ""
say "tlstore is installed. Run 'tlstore browse' to look around."
