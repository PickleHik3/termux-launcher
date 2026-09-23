#!/usr/bin/env bash
# Host tests for scripts/tlstore/install.sh, the standalone curl-pipe
# installer for official Termux. No framework: a sandbox HOME, a fake Termux
# prefix, a file:// tree shaped like the repository (app/src/main/assets/
# tlstore/...) signed with a throwaway minisign key, and a list of
# assertions. Nothing here touches the network, a device or the real HOME.
#
#   scripts/tlstore/test-install.sh [shell...]
#
# With no arguments it runs the whole suite under every POSIX shell it can
# find (sh, dash, busybox sh, bash --posix), same as scripts/tlstore/test.sh.
# Name shells to run only those.
#
# Two PATHs feed install.sh, built once in build_fixture:
#   RUNPATH_WITH_MINISIGN  the fixture tools plus a real minisign, for the
#                           tests that expect it to already be there.
#   RUNPATH_NO_MINISIGN    the fixture tools and a fake `pkg` that only
#                           produces a minisign when asked to install one —
#                           for the tests about that question itself.
# A fake `pkg` is on both, logging every invocation to pkg.log.
#
# The minisign question is asked with install.sh's own bytes on stdin and no
# controlling terminal at all, or with a real one — the same two shapes
# `curl ... | sh` and a person running it directly ever produce. `setsid`
# gives the first (no /dev/tty to fall back to); `script -qc` gives the
# second (a real pty, so /dev/tty answers even though stdin is still the
# installer). Either missing on this host just skips that one check.

set -u

repo="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
INSTALL="$repo/scripts/tlstore/install.sh"
[ -f "$INSTALL" ] || { echo "missing $INSTALL" >&2; exit 1; }

PASS=0
FAIL=0
SKIP=0
FAILED_NAMES=()

pass() { PASS=$((PASS + 1)); }
fail() {
    FAIL=$((FAIL + 1))
    FAILED_NAMES+=("[$SHELL_LABEL] $1")
    echo "  FAIL  $1"
    [ $# -lt 2 ] || printf '        %s\n' "$2"
    [ -z "${OUT:-}" ] || printf '        output: %s\n' "$(printf '%s' "$OUT" | head -5 | tr '\n' '|')"
}
skip() { SKIP=$((SKIP + 1)); echo "  SKIP  $1${2:+ — $2}"; }

# ---------------------------------------------------------------------------
# Fixture
# ---------------------------------------------------------------------------

build_fixture() {
    ROOT="$(mktemp -d)"
    FX="$ROOT/fixtures"
    ASSETDIR="$FX/app/src/main/assets/tlstore"
    HOME_DIR="$ROOT/home"
    TPREFIX="$ROOT/data/data/com.termux/files/usr"
    FIXBIN="$ROOT/bin"
    mkdir -p "$ASSETDIR" "$HOME_DIR" "$TPREFIX/bin" "$FIXBIN"

    # The script the installer fetches. A small fixture, not the real
    # tlstore — install.sh only ever copies its bytes through, so the
    # content does not matter, only that the marker and version line survive.
    cat > "$ASSETDIR/tlstore" <<'EOF'
#!/system/bin/sh
# written by termux-launcher
# tlstore fixture used by scripts/tlstore/test-install.sh
TLSTORE_VERSION=0.1
echo "fixture tlstore $*"
EOF

    {
        printf '# tlstore catalog\tserial=2026092101\n'
        printf '# name\tkind\tversion\tprefixes\tsource\tdigest\ttarget\trequires\toptions\tsummary\n'
    } > "$ASSETDIR/catalog.tsv"

    HAVE_SETSID=0
    command -v setsid >/dev/null 2>&1 && HAVE_SETSID=1
    HAVE_SCRIPT=0
    command -v script >/dev/null 2>&1 && HAVE_SCRIPT=1

    HAVE_MINISIGN=0
    REAL_MINISIGN="$(command -v minisign 2>/dev/null || true)"
    if [ -n "$REAL_MINISIGN" ]; then
        if minisign -G -W -f -p "$ROOT/key.pub" -s "$ROOT/key.sec" >/dev/null 2>&1; then
            HAVE_MINISIGN=1
        fi
    fi
    if [ "$HAVE_MINISIGN" = 1 ]; then
        cp "$ROOT/key.pub" "$ASSETDIR/trusted.pub"
        minisign -S -s "$ROOT/key.sec" -x "$ASSETDIR/tlstore.minisig" -m "$ASSETDIR/tlstore" \
            -c "tlstore script" -t "tlstore 0.1" >/dev/null 2>&1
        minisign -S -s "$ROOT/key.sec" -x "$ASSETDIR/catalog.tsv.minisig" -m "$ASSETDIR/catalog.tsv" \
            -c "tlstore catalog" -t "tlstore catalog serial=2026092101" >/dev/null 2>&1

        # A base whose tlstore no longer matches its signature: signed, then
        # the fetched bytes changed — the same shape as test.sh's tampered.tsv.
        mkdir -p "$ROOT/badscript"
        cp -r "$FX/app" "$ROOT/badscript/app"
        printf '\n# tampered after signing\n' >> "$ROOT/badscript/app/src/main/assets/tlstore/tlstore"

        # Same, for the catalog.
        mkdir -p "$ROOT/badcatalog"
        cp -r "$FX/app" "$ROOT/badcatalog/app"
        printf '# tampered after signing\n' >> "$ROOT/badcatalog/app/src/main/assets/tlstore/catalog.tsv"
    fi

    # Fake package managers: log what they were asked for; only `pkg install
    # ... minisign` actually produces a minisign, by copying the real one —
    # install.sh's own PATH is what decides whether it can see it yet.
    cat > "$FIXBIN/pkg" <<EOF
#!/bin/sh
echo "\$@" >> "$ROOT/pkg.log"
case "\$*" in
    *minisign*)
        if [ -n "$REAL_MINISIGN" ]; then
            cp "$REAL_MINISIGN" "$FIXBIN/minisign"
            chmod +x "$FIXBIN/minisign"
        fi
        ;;
esac
exit 0
EOF
    chmod +x "$FIXBIN/pkg"

    # The rest of what install.sh calls, so a PATH that excludes minisign's
    # real directory does not also take away curl, mktemp, cp, sed, ...
    for t in uname curl mkdir cp mv chmod ln rm sed head mktemp; do
        p="$(command -v "$t" 2>/dev/null || true)"
        [ -z "$p" ] || ln -sf "$p" "$FIXBIN/$t"
    done

    RUNPATH_WITH_MINISIGN="$FIXBIN:/usr/bin:/bin"
    RUNPATH_NO_MINISIGN="$FIXBIN"
}

# ---------------------------------------------------------------------------
# Running install.sh
# ---------------------------------------------------------------------------

reset_prefix() {
    rm -rf "$TPREFIX"
    mkdir -p "$TPREFIX/bin"
}

# ti [args...] — stdin comes from $STDIN_TEXT when set; PATH from $RUNPATH;
# TERM_PROGRAM from $TERM_PROGRAM_KNOB when set.
ti() {
    local input="${STDIN_TEXT:-}"
    if [ -n "$input" ]; then
        OUT="$(printf '%s' "$input" | env -i \
            HOME="$HOME_DIR" PATH="$RUNPATH" \
            TLSTORE_PREFIX="$TPREFIX" \
            TLSTORE_RAW_BASE="${RAW_BASE:-}" \
            TLSTORE_ARCH=aarch64 \
            TERM_PROGRAM="${TERM_PROGRAM_KNOB:-}" \
            "${SHCMD[@]}" "$INSTALL" "$@" 2>&1)"
    else
        OUT="$(env -i \
            HOME="$HOME_DIR" PATH="$RUNPATH" \
            TLSTORE_PREFIX="$TPREFIX" \
            TLSTORE_RAW_BASE="${RAW_BASE:-}" \
            TLSTORE_ARCH=aarch64 \
            TERM_PROGRAM="${TERM_PROGRAM_KNOB:-}" \
            "${SHCMD[@]}" "$INSTALL" "$@" < /dev/null 2>&1)"
    fi
    ST=$?
    STDIN_TEXT=""
    return 0
}

expect_status() {
    if [ "$ST" = "$2" ]; then pass; else fail "$1" "expected exit $2, got $ST"; fi
}
expect_out() {
    if printf '%s' "$OUT" | grep -q -- "$2"; then pass; else fail "$1" "expected output matching: $2"; fi
}
expect_no_out() {
    if printf '%s' "$OUT" | grep -q -- "$2"; then fail "$1" "did not expect: $2"; else pass; fi
}
expect_file() {
    if [ -e "$2" ]; then pass; else fail "$1" "expected file $2"; fi
}
expect_no_file() {
    if [ -e "$2" ]; then fail "$1" "$2 should be gone"; else pass; fi
}
expect_content() {
    local got
    got="$(cat "$2" 2>/dev/null)"
    if [ "$got" = "$3" ]; then pass; else fail "$1" "$2 holds '$got', expected '$3'"; fi
}
expect_symlink_to() {
    local got
    got="$(readlink "$2" 2>/dev/null)"
    if [ "$got" = "$3" ]; then pass; else fail "$1" "$2 -> '$got', expected -> '$3'"; fi
}

# ---------------------------------------------------------------------------
# The suite
# ---------------------------------------------------------------------------

run_suite() {
    build_fixture
    RAW_BASE="file://$FX"
    RUNPATH="$RUNPATH_WITH_MINISIGN"
    STDIN_TEXT=""
    TERM_PROGRAM_KNOB=""

    # --- prefix checks, before anything is fetched ---
    reset_prefix
    STASH="$TPREFIX"
    TPREFIX=""
    ti
    expect_status "no prefix at all is refused" 1
    expect_out "and says so" "does not look like"
    TPREFIX="$STASH"

    reset_prefix
    STASH="$TPREFIX"
    TPREFIX="$ROOT/not-termux"
    mkdir -p "$TPREFIX/bin"
    ti
    expect_status "a prefix outside /data/data/*/files/usr is refused" 1
    expect_no_file "nothing was written" "$TPREFIX/bin/tlstore"
    TPREFIX="$STASH"

    # --- the launcher is already here: nothing to do, nothing touched ---
    reset_prefix
    mkdir -p "$TPREFIX/libexec/termux-launcher/tlstore"
    : > "$TPREFIX/libexec/termux-launcher/tlstore/.installed"
    ti
    expect_status "a .installed from the app is a no-op" 0
    expect_out "and says so" "Termux Launcher already"
    expect_no_file "nothing was written" "$TPREFIX/bin/tlstore"
    expect_file "the app's own marker is untouched" "$TPREFIX/libexec/termux-launcher/tlstore/.installed"

    reset_prefix
    TERM_PROGRAM_KNOB=termux-launcher
    ti
    expect_status "TERM_PROGRAM=termux-launcher is a no-op" 0
    expect_out "and says so" "Termux Launcher already"
    expect_no_file "nothing was written" "$TPREFIX/bin/tlstore"
    TERM_PROGRAM_KNOB=""

    if [ "$HAVE_MINISIGN" = 1 ]; then
        # --- signatures ---
        reset_prefix
        RAW_BASE="file://$ROOT/badscript"
        ti -y
        expect_status "a tlstore whose signature no longer matches is refused" 1
        expect_out "and says so" "did not check out"
        expect_no_file "nothing was installed" "$TPREFIX/bin/tlstore"

        reset_prefix
        RAW_BASE="file://$ROOT/badcatalog"
        ti -y
        expect_status "a catalog whose signature no longer matches is refused" 1
        expect_out "and says so" "item list did not check out"
        expect_no_file "nothing was installed" "$TPREFIX/bin/tlstore"

        # --- the clean install ---
        reset_prefix
        RAW_BASE="file://$FX"
        ti -y
        expect_status "a clean install" 0
        expect_file "the script landed" "$TPREFIX/bin/tlstore"
        if [ -x "$TPREFIX/bin/tlstore" ]; then pass; else fail "the script is executable"; fi
        if head -2 "$TPREFIX/bin/tlstore" | grep -q '# written by termux-launcher'; then pass; else fail "the marker survives the copy"; fi
        expect_symlink_to "tl points at tlstore" "$TPREFIX/bin/tl" "tlstore"
        expect_symlink_to "tls points at tlstore" "$TPREFIX/bin/tls" "tlstore"
        expect_file "the catalog landed" "$TPREFIX/libexec/termux-launcher/tlstore/catalog.tsv"
        expect_file "the trusted key landed" "$TPREFIX/libexec/termux-launcher/tlstore/trusted.pub"
        expect_content ".standalone names the base" "$TPREFIX/libexec/termux-launcher/tlstore/.standalone" "$RAW_BASE"
        expect_out "the summary names list" "tlstore list"

        # --- a tlstore this did not write is not silently replaced ---
        reset_prefix
        printf '#!/bin/sh\necho not ours\n' > "$TPREFIX/bin/tlstore"
        chmod +x "$TPREFIX/bin/tlstore"
        ti
        expect_status "a tlstore without the marker is refused" 1
        expect_out "and says so" "already a tlstore here"
        expect_content "it is left untouched" "$TPREFIX/bin/tlstore" "#!/bin/sh
echo not ours"
        ti -y
        expect_status "-y replaces it anyway" 0
        if head -2 "$TPREFIX/bin/tlstore" | grep -q '# written by termux-launcher'; then pass; else fail "the marker is there after -y replaces it"; fi

        reset_prefix
        ln -s /bin/true "$TPREFIX/bin/tlstore"
        ti
        expect_status "a tlstore that is a symlink is refused too" 1
        expect_out "and says so" "already a tlstore here"

        reset_prefix
        ti -y
        ti
        expect_status "reinstalling over our own tlstore needs no -y" 0

        # --- tl already taken is left alone; tls still gets it ---
        reset_prefix
        printf '#!/bin/sh\necho not ours\n' > "$TPREFIX/bin/tl"
        chmod +x "$TPREFIX/bin/tl"
        ti -y
        expect_status "install succeeds even when tl is taken" 0
        expect_out "and says so" "tl is already something else here"
        if [ ! -L "$TPREFIX/bin/tl" ] && [ -x "$TPREFIX/bin/tl" ]; then pass; else fail "tl was left alone"; fi
        expect_content "tl's own content is untouched" "$TPREFIX/bin/tl" "#!/bin/sh
echo not ours"
        expect_symlink_to "tls still got the alias" "$TPREFIX/bin/tls" "tlstore"

        # --- minisign missing: the question, asked and answered for real ---
        # Each case starts with no minisign in the fixture PATH — a case that
        # installed one (real or fake) would otherwise leak it into the next.
        # install.sh runs with no filename argument and its own source on
        # stdin, the exact shape of `curl ... | sh` — a plain `read` there
        # would be answered by the installer's own bytes, not a person.

        if [ "$HAVE_SETSID" = 1 ]; then
            reset_prefix
            RUNPATH="$RUNPATH_NO_MINISIGN"
            rm -f "$FIXBIN/minisign"
            OUT="$(setsid env -i \
                HOME="$HOME_DIR" PATH="$RUNPATH" \
                TLSTORE_PREFIX="$TPREFIX" TLSTORE_RAW_BASE="$RAW_BASE" TLSTORE_ARCH=aarch64 \
                "${SHCMD[@]}" < "$INSTALL" 2>&1)"
            ST=$?
            expect_status "no terminal at all: the installer's own bytes are never read as an answer" 1
            expect_out "it stops with a plain sentence instead" "minisign is required"
            expect_no_file "nothing was installed" "$TPREFIX/bin/tlstore"
            RUNPATH="$RUNPATH_WITH_MINISIGN"
        else
            skip "the no-terminal-at-all check" "setsid is not installed"
        fi

        if [ "$HAVE_SCRIPT" = 1 ]; then
            reset_prefix
            RUNPATH="$RUNPATH_NO_MINISIGN"
            rm -f "$FIXBIN/minisign"
            : > "$ROOT/pkg.log"
            OUT="$(printf 'y\n' | script -qc "env -i HOME='$HOME_DIR' PATH='$RUNPATH' TLSTORE_PREFIX='$TPREFIX' TLSTORE_RAW_BASE='$RAW_BASE' TLSTORE_ARCH=aarch64 ${SHCMD[*]} < '$INSTALL'" /dev/null 2>&1)"
            ST=$?
            expect_status "a real terminal answer lands even though stdin is still the installer" 0
            if grep -q -- "install -y minisign" "$ROOT/pkg.log"; then pass; else fail "pkg was asked for minisign"; fi
            RUNPATH="$RUNPATH_WITH_MINISIGN"
        else
            skip "the /dev/tty answer check" "script is not installed"
        fi

        reset_prefix
        RUNPATH="$RUNPATH_NO_MINISIGN"
        rm -f "$FIXBIN/minisign"
        : > "$ROOT/pkg.log"
        ti -y
        expect_status "-y installs minisign without asking" 0
        if grep -q -- "install -y minisign" "$ROOT/pkg.log"; then pass; else fail "pkg was asked for minisign"; fi

        rm -f "$FIXBIN/minisign"
        RUNPATH="$RUNPATH_WITH_MINISIGN"
    else
        skip "signature and clean-install checks" "minisign is not installed"
        skip "the minisign prompt checks" "minisign is not installed"
    fi

    rm -rf "$ROOT"
}

# ---------------------------------------------------------------------------
# Shells
# ---------------------------------------------------------------------------

shells=()
if [ $# -gt 0 ]; then
    for s in "$@"; do shells+=("$s"); done
else
    shells+=("/bin/sh")
    command -v dash >/dev/null 2>&1 && shells+=("$(command -v dash)")
    command -v busybox >/dev/null 2>&1 && shells+=("$(command -v busybox) sh")
    shells+=("/bin/bash --posix")
fi

for entry in "${shells[@]}"; do
    IFS=' ' read -r -a SHCMD <<< "$entry"
    if ! command -v "${SHCMD[0]}" >/dev/null 2>&1; then
        echo "== $entry — not installed, skipped"
        SKIP=$((SKIP + 1))
        continue
    fi
    SHELL_LABEL="$entry"
    before_fail=$FAIL
    echo "== $entry"
    run_suite
    if [ "$FAIL" = "$before_fail" ]; then
        echo "   all checks passed"
    fi
done

echo
if command -v shellcheck >/dev/null 2>&1; then
    echo "== shellcheck -s sh"
    if shellcheck -s sh "$INSTALL"; then
        echo "   clean"
    else
        FAIL=$((FAIL + 1))
        FAILED_NAMES+=("shellcheck")
    fi
else
    echo "== shellcheck is not installed — not run"
fi

echo
echo "passed $PASS, failed $FAIL, skipped $SKIP"
if [ "$FAIL" != 0 ]; then
    printf '  %s\n' "${FAILED_NAMES[@]}"
    exit 1
fi
exit 0
