#!/usr/bin/env bash
# Host tests for app/src/main/assets/tlstore/tlstore. No framework: a sandbox
# HOME, a fake prefix, a catalog whose sources are file:// URLs, and a list of
# assertions. Nothing here touches the network, a device or the real HOME.
#
#   scripts/tlstore/test.sh [shell...]
#
# With no arguments it runs the whole suite under every POSIX shell it can find
# (sh, dash, busybox sh, bash --posix) — tlstore has to work under all of them,
# and dash is what Termux's sh is. Name shells to run only those, e.g.
#   scripts/tlstore/test.sh /bin/dash "/path/to/busybox sh"
#
# Two knobs let the suite exercise phone-only code paths on a Linux host, and
# tlstore reads both by design:
#   TLSTORE_ARCH=aarch64   — binary and npm-musl items are hidden on other
#                            processors; the fixture sets this so they show up.
#   TLSTORE_PATCHELF=true  — the npm-musl install runs patchelf on the
#                            downloaded executable. The fixture payload is a
#                            shell script, so patchelf would (rightly) refuse
#                            it; `true` stands in and the rest of the path —
#                            registry document, sha512, extraction, loader,
#                            wrapper — is exercised for real. Nothing else is
#                            stubbed: curl, sha256sum, sha512sum, tar, base64,
#                            od and minisign are the real tools.
# The catalog signature tests need minisign. Without it they are skipped, and
# the suite says so instead of passing quietly.

set -u

repo="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TLSTORE="$repo/app/src/main/assets/tlstore/tlstore"
[ -f "$TLSTORE" ] || { echo "missing $TLSTORE" >&2; exit 1; }

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

sha() { sha256sum "$1" | cut -d' ' -f1; }

# npm's dist.integrity is base64 of the raw sha512.
sha512_b64() {
    if command -v openssl >/dev/null 2>&1; then
        openssl dgst -sha512 -binary "$1" | base64 | tr -d '\n'
    elif command -v python3 >/dev/null 2>&1; then
        python3 -c 'import base64,hashlib,sys;print(base64.b64encode(hashlib.sha512(open(sys.argv[1],"rb").read()).digest()).decode())' "$1"
    else
        echo "need openssl or python3 to build the npm fixture" >&2
        return 1
    fi
}

# write_catalog <file> <serial> <fakebin version> <fakebin payload>
write_catalog() {
    local out="$1" serial="$2" fbver="$3" fbfile="$4"
    {
        printf '# tlstore catalog\tserial=%s\n' "$serial"
        printf '# name\tkind\tversion\tprefixes\tsource\tdigest\ttarget\trequires\toptions\tsummary\n'
        printf 'hello\tfile\t1\t*\tfile://%s/hello.conf\t%s\t~/.config/hello.conf\t-\t-\tA greeting you can read.\n' "$FX" "$(sha "$FX/hello.conf")"
        printf 'mine\tfile-once\t1\t*\tfile://%s/mine.conf\t%s\t~/.config/mine.conf\t-\t-\tYours to edit, installed once.\n' "$FX" "$(sha "$FX/mine.conf")"
        printf 'fakebin\tbinary\t%s\t*\tfile://%s/%s\t%s\t-\t-\t-\tA small tool for the terminal.\n' "$fbver" "$FX" "$fbfile" "$(sha "$FX/$fbfile")"
        printf 'badsum\tbinary\t1\t*\tfile://%s/hello.conf\t%s\t~/.local/bin/badsum\t-\t-\tNever installs, on purpose.\n' "$FX" "0000000000000000000000000000000000000000000000000000000000000000"
        printf 'twin\tbinary\t1\tio.vaj.tl\tfile://%s/other.bin\t%s\t~/.local/bin/twin\t-\t-\tThe other edition build.\n' "$FX" "$(sha "$FX/other.bin")"
        printf 'twin\tbinary\t1\tcom.termux\tfile://%s/twin.bin\t%s\t~/.local/bin/twin\t-\t-\tThis edition build.\n' "$FX" "$(sha "$FX/twin.bin")"
        printf 'ghost\tbinary\t1\tio.vaj.tl\tfile://%s/other.bin\t%s\t-\t-\t-\tOnly for another edition.\n' "$FX" "$(sha "$FX/other.bin")"
        printf 'demo-pkg\tpkg\t-\t*\tdemo-one demo-two\t-\t-\t-\t-\tTwo packages from the package manager.\n'
        printf 'musl-loader\tbinary\t1\tcom.termux\tfile://%s/loader.bin\t%s\t~/.local/lib/musl/ld-musl-aarch64.so.1\t-\t-\tWhat tools from other systems need to start.\n' "$FX" "$(sha "$FX/loader.bin")"
        printf 'claude-code\tnpm-musl\tlatest\t*\tnpm:demo-cli#claude\t-\t-\tmusl-loader,demo-pkg\tenv=DEMO_FLAG=1;tz=1\tA tool that comes from npm.\n'
        printf 'kit\tbundle\t-\t*\t-\t-\t-\thello,fakebin,demo-pkg\t-\tA few things at once.\n'
    } > "$out"
}

build_fixture() {
    ROOT="$(mktemp -d)"
    FX="$ROOT/fixtures"
    TESTHOME="$ROOT/home"
    TPREFIX="$ROOT/data/data/com.termux/files/usr"
    FIXBIN="$ROOT/bin"
    mkdir -p "$FX" "$TESTHOME" "$TPREFIX/bin" "$TPREFIX/libexec/termux-launcher/tlstore" "$FIXBIN"

    # A shell for the generated wrapper's shebang, which names $PREFIX/bin/sh.
    ln -sf /bin/sh "$TPREFIX/bin/sh"

    printf 'greeting from the catalog\n' > "$FX/hello.conf"
    printf 'your own settings go here\n' > "$FX/mine.conf"
    printf '#!/bin/sh\necho fakebin 1\n' > "$FX/fakebin-1"
    printf '#!/bin/sh\necho fakebin 2\n' > "$FX/fakebin-2"
    printf '#!/bin/sh\necho twin here\n' > "$FX/twin.bin"
    printf '#!/bin/sh\necho other edition\n' > "$FX/other.bin"
    printf 'not really a loader\n' > "$FX/loader.bin"

    # Fake package managers: they record what they were asked for. Both names
    # are needed — tlstore prefers pacman, and a host may have a real one.
    cat > "$FIXBIN/pkg" <<EOF
#!/bin/sh
echo "\$@" >> "$ROOT/pkg.log"
exit 0
EOF
    chmod +x "$FIXBIN/pkg"
    cp "$FIXBIN/pkg" "$FIXBIN/pacman"
    cp "$FIXBIN/pkg" "$FIXBIN/apt"

    # A fake npm registry: one package document and its tarball.
    mkdir -p "$FX/registry/demo-cli" "$FX/pkgsrc/package"
    cat > "$FX/pkgsrc/package/claude" <<'EOF'
#!/bin/sh
echo "demo-cli 1.0.0"
echo "DEMO_FLAG=${DEMO_FLAG:-unset}"
EOF
    chmod +x "$FX/pkgsrc/package/claude"
    printf 'MIT\n' > "$FX/pkgsrc/package/LICENSE.md"
    tar czf "$FX/demo-cli-1.0.0.tgz" -C "$FX/pkgsrc" package
    printf '{"name":"demo-cli","version":"1.0.0","dist":{"tarball":"file://%s/demo-cli-1.0.0.tgz","integrity":"sha512-%s"}}\n' \
        "$FX" "$(sha512_b64 "$FX/demo-cli-1.0.0.tgz")" > "$FX/registry/demo-cli/latest"

    # The catalog the app ships, plus three the refresh can be pointed at.
    write_catalog "$TPREFIX/libexec/termux-launcher/tlstore/catalog.tsv" 2026090601 1 fakebin-1
    write_catalog "$FX/newer.tsv" 2026090602 2 fakebin-2
    write_catalog "$FX/older.tsv" 2026090600 1 fakebin-1
    write_catalog "$FX/newest.tsv" 2026090603 2 fakebin-2
    write_catalog "$FX/tampered.tsv" 2026090604 2 fakebin-2

    HAVE_MINISIGN=0
    if command -v minisign >/dev/null 2>&1; then
        HAVE_MINISIGN=1
        minisign -G -W -f -p "$ROOT/key.pub" -s "$ROOT/key.sec" >/dev/null 2>&1 || HAVE_MINISIGN=0
    fi
    if [ "$HAVE_MINISIGN" = 1 ]; then
        cp "$ROOT/key.pub" "$TPREFIX/libexec/termux-launcher/tlstore/trusted.pub"
        for c in newer older newest tampered; do
            minisign -S -s "$ROOT/key.sec" -x "$FX/$c.tsv.minisig" -m "$FX/$c.tsv" >/dev/null 2>&1
        done
        # Signed, then changed: the signature no longer covers the file.
        printf '# nudged after signing\n' >> "$FX/tampered.tsv"
    fi

    RUNPATH="$FIXBIN"
    if command -v minisign >/dev/null 2>&1; then
        RUNPATH="$RUNPATH:$(dirname "$(command -v minisign)")"
    fi
    RUNPATH="$RUNPATH:/usr/bin:/bin"
}

# ---------------------------------------------------------------------------
# Running tlstore
# ---------------------------------------------------------------------------

# tl [args...] — stdin comes from $STDIN_TEXT when set.
tl() {
    local input="${STDIN_TEXT:-}"
    if [ -n "$input" ]; then
        OUT="$(printf '%s' "$input" | env -i \
            HOME="$TESTHOME" PATH="$RUNPATH" \
            TLSTORE_PREFIX="$TPREFIX" \
            TLSTORE_CATALOG_URL="$CATALOG_URL" \
            TLSTORE_NPM_REGISTRY="file://$FX/registry" \
            TLSTORE_ARCH=aarch64 \
            TLSTORE_PATCHELF=true \
            "${SHCMD[@]}" "$TLSTORE" "$@" 2>&1)"
    else
        OUT="$(env -i \
            HOME="$TESTHOME" PATH="$RUNPATH" \
            TLSTORE_PREFIX="$TPREFIX" \
            TLSTORE_CATALOG_URL="$CATALOG_URL" \
            TLSTORE_NPM_REGISTRY="file://$FX/registry" \
            TLSTORE_ARCH=aarch64 \
            TLSTORE_PATCHELF=true \
            "${SHCMD[@]}" "$TLSTORE" "$@" < /dev/null 2>&1)"
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

# ---------------------------------------------------------------------------
# The suite
# ---------------------------------------------------------------------------

run_suite() {
    build_fixture
    CATALOG_URL="file://$FX/newer.tsv"
    STDIN_TEXT=""

    # --- help, version, usage ---
    tl; expect_status "no arguments prints help" 0; expect_out "no arguments prints help" "tlstore install"
    tl version; expect_status "version" 0; expect_out "version names the item list" "2026090601"
    tl nonsense; expect_status "unknown command is a usage error" 2
    tl list -x; expect_status "unknown option is a usage error" 2
    tl search; expect_status "search with no term is a usage error" 2
    tl remove; expect_status "remove with no name is a usage error" 2
    tl info nosuch; expect_status "info on an unknown item fails" 1

    # --- list, search, info, per-prefix and per-arch selection ---
    tl list
    expect_status "list" 0
    expect_out "list shows a file item" "hello"
    expect_out "list shows a bundle" "kit"
    expect_no_out "list hides another edition's item" "ghost"
    tl info twin
    expect_out "the row for this prefix wins" "twin.bin"
    expect_no_out "the other edition's row is not used" "other.bin"
    tl search greeting
    expect_status "search" 0
    expect_out "search matches the summary" "hello"
    tl info hello
    expect_status "info" 0
    expect_out "info names where the file goes" ".config/hello.conf"
    expect_out "info names the checksum" "$(sha "$FX/hello.conf")"

    # --- a file, over an existing one ---
    mkdir -p "$TESTHOME/.config"
    printf 'the users own greeting\n' > "$TESTHOME/.config/hello.conf"
    tl install hello -y
    expect_status "install a file" 0
    expect_content "the file was replaced" "$TESTHOME/.config/hello.conf" "greeting from the catalog"
    if ls "$TESTHOME/.config/hello.conf".bak-* >/dev/null 2>&1; then pass; else fail "the old file was kept beside it"; fi
    tl list -i
    expect_out "list -i shows what is installed" "hello"
    tl list -a
    expect_no_out "list -a hides what is installed" "^hello"

    # --- a file-once, twice ---
    printf 'already mine\n' > "$TESTHOME/.config/mine.conf"
    tl install mine -y
    expect_status "install a file-once over an existing file" 0
    expect_content "a file-once leaves the users file alone" "$TESTHOME/.config/mine.conf" "already mine"
    tl install mine -y
    expect_status "install a file-once again" 0
    expect_content "the second run keeps it" "$TESTHOME/.config/mine.conf" "already mine"

    # --- a binary, and a digest that does not match ---
    tl install fakebin -y
    expect_status "install a binary" 0
    expect_file "the binary landed in ~/.local/bin" "$TESTHOME/.local/bin/fakebin"
    if [ -x "$TESTHOME/.local/bin/fakebin" ]; then pass; else fail "the binary is executable"; fi
    tl install badsum -y
    expect_status "a payload that does not match its checksum fails" 1
    expect_no_file "nothing is left behind after a checksum failure" "$TESTHOME/.local/bin/badsum"

    # --- a bundle, with a package item in it ---
    tl install kit -y
    expect_status "install a bundle" 0
    expect_out "the bundle installs its members" "demo-pkg"
    if grep -q demo-one "$ROOT/pkg.log" 2>/dev/null; then pass; else fail "the package manager was asked for the packages"; fi
    tl list -i
    expect_out "the bundle is recorded" "kit"

    # --- npm-musl ---
    tl install claude-code -y
    expect_status "install an npm-musl item" 0
    expect_file "the loader is in place" "$TESTHOME/.local/lib/claude-code/ld-musl-aarch64.so.1"
    expect_file "the package executable is in place" "$TESTHOME/.local/lib/claude-code/claude"
    expect_content "the installed version is recorded" "$TESTHOME/.local/lib/claude-code/version" "1.0.0"
    expect_file "the wrapper is named after the command" "$TESTHOME/.local/bin/claude"
    OUT="$("$TESTHOME/.local/bin/claude" 2>&1)"; ST=$?
    expect_status "the wrapper runs" 0
    expect_out "the wrapper runs the package executable" "demo-cli 1.0.0"
    expect_out "the wrapper exports the options" "DEMO_FLAG=1"

    # --- remove, with the backup put back ---
    tl remove hello -y
    expect_status "remove" 0
    expect_content "the users own file came back" "$TESTHOME/.config/hello.conf" "the users own greeting"
    tl list -i
    expect_no_out "a removed item is no longer installed" "^hello"
    tl remove hello -y
    expect_status "removing something twice is not an error" 0
    expect_out "removing something twice says so" "not installed"

    # --- update ---
    tl update --check
    expect_status "update --check" 0
    expect_out "update --check took the newer item list" "Updated the list of items"
    expect_out "update --check names what is out of date" "fakebin"
    tl version
    expect_out "the refreshed item list is in use" "2026090602"
    tl update -y
    expect_status "update" 0
    OUT="$("$TESTHOME/.local/bin/fakebin" 2>&1)"; ST=$?
    expect_out "update replaced the payload" "fakebin 2"
    tl update --check
    expect_out "nothing is out of date afterwards" "up to date"
    tl update --check --offline
    expect_status "update --offline" 0

    # --- refresh ---
    if [ "$HAVE_MINISIGN" = 1 ]; then
        CATALOG_URL="file://$FX/older.tsv"
        tl refresh
        expect_status "an older item list is refused" 1
        expect_out "and says so" "older"
        tl version
        expect_out "the item list did not move" "2026090602"

        CATALOG_URL="file://$FX/tampered.tsv"
        tl refresh
        expect_status "an item list with a broken signature is refused" 1
        expect_out "and says so" "not signed by the launcher"
        tl version
        expect_out "the item list still did not move" "2026090602"

        CATALOG_URL="file://$FX/newest.tsv"
        tl refresh
        expect_status "a newer, signed item list is taken" 0
        tl version
        expect_out "the newer item list is in use" "2026090603"
        tl refresh
        expect_status "refreshing again is fine" 0
        expect_out "and says there is nothing new" "up to date"
    else
        skip "catalog signature tests" "minisign is not installed"
    fi

    # --- doctor ---
    tl doctor
    expect_status "doctor" 0
    expect_out "doctor names the prefix" "$TPREFIX"
    expect_out "doctor names the item list" "Item list"

    # --- the numbered picker ---
    rm -rf "$TESTHOME/.local/share/tlstore" "$TESTHOME/.config/hello.conf"
    CATALOG_URL="file://$FX/newer.tsv"
    STDIN_TEXT='a
n
1

'
    tl install -y
    expect_status "the picker" 0
    expect_out "the picker numbers the items" "\[ \]  1"
    expect_content "the picker installed exactly what was ticked" "$TESTHOME/.config/hello.conf" "greeting from the catalog"
    expect_no_file "the picker installed nothing else" "$TESTHOME/.local/bin/twin"
    STDIN_TEXT='

'
    tl install -y
    expect_status "picking nothing" 0
    expect_out "picking nothing installs nothing" "Nothing to install"

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
    if shellcheck -s sh "$TLSTORE"; then
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
