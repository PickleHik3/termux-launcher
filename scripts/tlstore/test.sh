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
#   TLSTORE_ASSUME_TTY=1   — the questions tlstore only asks a person (replace
#                            this config file? remove the build tools?) are
#                            skipped when stdin is not a terminal. The suite has
#                            no pty, so it sets this knob and pipes the answers
#                            in; without it the "nobody is there" paths are what
#                            get tested, and both are.
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

# write_catalog <file> <serial> <version> <fakebin payload> — the version is the
# one hello and fakebin carry, so a newer catalog moves a config item on too.
write_catalog() {
    local out="$1" serial="$2" fbver="$3" fbfile="$4"
    {
        printf '# tlstore catalog\tserial=%s\n' "$serial"
        printf '# name\tkind\tversion\tprefixes\tsource\tdigest\ttarget\trequires\toptions\tsummary\n'
        printf 'hello\tfile\t%s\t*\tfile://%s/hello.conf\t%s\t~/.config/hello.conf\t-\t-\tA greeting you can read.\n' "$fbver" "$FX" "$(sha "$FX/hello.conf")"
        printf 'mine\tfile-once\t1\t*\tfile://%s/mine.conf\t%s\t~/.config/mine.conf\t-\t-\tYours to edit, installed once.\n' "$FX" "$(sha "$FX/mine.conf")"
        printf 'fakebin\tbinary\t%s\t*\tfile://%s/%s\t%s\t-\t-\t-\tA small tool for the terminal.\n' "$fbver" "$FX" "$fbfile" "$(sha "$FX/$fbfile")"
        printf 'badsum\tbinary\t1\t*\tfile://%s/hello.conf\t%s\t~/.local/bin/badsum\t-\t-\tNever installs, on purpose.\n' "$FX" "0000000000000000000000000000000000000000000000000000000000000000"
        printf 'twin\tbinary\t1\tio.vaj.tl\tfile://%s/other.bin\t%s\t~/.local/bin/twin\t-\t-\tThe other edition build.\n' "$FX" "$(sha "$FX/other.bin")"
        printf 'twin\tbinary\t1\tcom.termux\tfile://%s/twin.bin\t%s\t~/.local/bin/twin\t-\t-\tThis edition build.\n' "$FX" "$(sha "$FX/twin.bin")"
        printf 'ghost\tbinary\t1\tio.vaj.tl\tfile://%s/other.bin\t%s\t-\t-\t-\tOnly for another edition.\n' "$FX" "$(sha "$FX/other.bin")"
        printf 'demo-pkg\tpkg\t-\t*\tdemo-one demo-two\t-\t-\t-\t-\tTwo packages from the package manager.\n'
        printf 'musl-loader\tbinary\t1\tcom.termux\tfile://%s/loader.bin\t%s\t~/.local/lib/musl/ld-musl-aarch64.so.1\t-\t-\tWhat tools from other systems need to start.\n' "$FX" "$(sha "$FX/loader.bin")"
        printf 'claude-code\tnpm-musl\tlatest\t*\tnpm:demo-cli#claude\t-\t-\tmusl-loader,demo-pkg\tenv=DEMO_FLAG=1;tz=1;build=demo-build\tA tool that comes from npm.\n'
        printf 'kit\tbundle\t-\t*\t-\t-\t-\thello,fakebin,demo-pkg,secret\t-\tA few things at once.\n'
        printf 'plug\tfisher\t-\t*\tdemo/one demo/two\t-\t-\t-\t-\tPlugins for the shell.\n'
        printf 'secret\tfile\t1\t*\tfile://%s/mine.conf\t%s\t~/.config/secret.conf\t-\thidden=1\tA part of something else.\n' "$FX" "$(sha "$FX/mine.conf")"
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
    cp "$FIXBIN/pkg" "$FIXBIN/apt"
    # pacman is the one tlstore prefers, and the only one it asks whether a
    # package is already there: -Q says no for the build tool, yes for the rest.
    cat > "$FIXBIN/pacman" <<EOF
#!/bin/sh
echo "\$@" >> "$ROOT/pkg.log"
[ "\${1:-}" = -Q ] || exit 0
[ "\${2:-}" = demo-build ] && exit 1
exit 0
EOF
    chmod +x "$FIXBIN/pacman"

    # A fake fish, so the plugin manager's install and remove can be seen.
    cat > "$FIXBIN/fish" <<EOF
#!/bin/sh
echo "\$@" >> "$ROOT/fish.log"
exit 0
EOF
    chmod +x "$FIXBIN/fish"

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
            TLSTORE_ASSUME_TTY="${TTY_KNOB:-0}" \
            "${SHCMD[@]}" "$TLSTORE" "$@" 2>&1)"
    else
        OUT="$(env -i \
            HOME="$TESTHOME" PATH="$RUNPATH" \
            TLSTORE_PREFIX="$TPREFIX" \
            TLSTORE_CATALOG_URL="$CATALOG_URL" \
            TLSTORE_NPM_REGISTRY="file://$FX/registry" \
            TLSTORE_ARCH=aarch64 \
            TLSTORE_PATCHELF=true \
            TLSTORE_ASSUME_TTY="${TTY_KNOB:-0}" \
            "${SHCMD[@]}" "$TLSTORE" "$@" < /dev/null 2>&1)"
    fi
    ST=$?
    STDIN_TEXT=""
    TTY_KNOB=0
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
    expect_no_out "list hides the parts of other items" "secret"
    expect_out "list leaves room for the installed mark" "^  hello"
    expect_out "list hints at what an item builds with" "needs while installing: demo-build"
    tl search part
    expect_no_out "search hides them too" "secret"
    tl info secret
    expect_status "info still explains a part" 0
    expect_out "info names the part" "part of something else"
    tl install secret -y
    expect_status "installing a part by name is refused" 1
    expect_out "and says why" "part of another item"
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

    # --- a file, where there is none yet ---
    mkdir -p "$TESTHOME/.config"
    tl install hello -y
    expect_status "install a file" 0
    expect_content "the file was written" "$TESTHOME/.config/hello.conf" "greeting from the catalog"
    tl list -i
    expect_out "list -i shows what is installed" "hello"
    tl list -a
    expect_no_out "list -a hides what is installed" "hello"

    # --- a config file that already differs: never replaced silently ---
    # Each case starts as a first install over a file tlstore did not put there,
    # which is what a user with their own config.fish actually has.
    forget_state() { rm -f "$TESTHOME/.local/share/tlstore/installed.tsv"; }

    printf 'the users own greeting\n' > "$TESTHOME/.config/hello.conf"

    # nobody there to ask: kept, one line, still recorded
    forget_state
    tl install hello -y
    expect_status "a config that differs, with nobody to ask" 0
    expect_content "the users own config is kept" "$TESTHOME/.config/hello.conf" "the users own greeting"
    expect_out "and it says so" "kept your hello.conf"
    expect_no_out "-y does not answer the config question" "Replace your"

    # asked, and answered no
    forget_state
    TTY_KNOB=1
    STDIN_TEXT='n
'
    tl install hello -y
    expect_status "a config that differs, answered no" 0
    expect_out "the change is shown" "greeting from the catalog"
    expect_out "and the question is asked" "Replace your hello.conf"
    expect_content "answering no keeps the users file" "$TESTHOME/.config/hello.conf" "the users own greeting"

    # asked, and answered yes
    forget_state
    TTY_KNOB=1
    STDIN_TEXT='y
'
    tl install hello -y
    expect_status "a config that differs, answered yes" 0
    expect_content "answering yes takes the new file" "$TESTHOME/.config/hello.conf" "greeting from the catalog"
    if ls "$TESTHOME/.config/hello.conf".bak-* >/dev/null 2>&1; then pass; else fail "the old config was kept beside it"; fi

    # identical: nothing to show, nothing to ask
    forget_state
    tl install hello -y
    expect_status "a config that is already identical" 0
    expect_no_out "an identical config asks nothing" "Replace your"
    expect_no_out "an identical config shows nothing" "^---"

    # --configs answers it without a person
    printf 'changed again\n' > "$TESTHOME/.config/hello.conf"
    forget_state
    tl install hello -y --configs
    expect_status "--configs" 0
    expect_content "--configs takes the new config" "$TESTHOME/.config/hello.conf" "greeting from the catalog"
    expect_no_out "--configs does not ask" "Replace your"

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
    expect_file "a bundle brings its hidden parts in" "$TESTHOME/.config/secret.conf"
    if grep -q demo-one "$ROOT/pkg.log" 2>/dev/null; then pass; else fail "the package manager was asked for the packages"; fi
    tl list -i
    expect_out "the bundle is recorded" "kit"
    expect_out "list marks what you have" "^\* kit"

    # --- fish plugins, which fisher fetches and fisher keeps current ---
    tl install plug -y
    expect_status "install fish plugins" 0
    if grep -q -- "-c fisher install demo/one demo/two" "$ROOT/fish.log" 2>/dev/null; then pass; else fail "fisher was asked to install the plugins"; fi
    tl install plug -y
    expect_out "installing them again says they are there" "already installed"
    tl remove plug -y
    expect_status "remove fish plugins" 0
    if grep -q -- "-c fisher remove demo/one demo/two" "$ROOT/fish.log" 2>/dev/null; then pass; else fail "fisher was asked to remove the plugins"; fi

    # --- npm-musl ---
    tl install claude-code -y
    expect_status "install an npm-musl item" 0
    expect_file "the loader is in place" "$TESTHOME/.local/lib/claude-code/ld-musl-aarch64.so.1"
    expect_file "the package executable is in place" "$TESTHOME/.local/lib/claude-code/claude"
    expect_content "the installed version is recorded" "$TESTHOME/.local/lib/claude-code/version" "1.0.0"
    expect_file "the wrapper is named after the command" "$TESTHOME/.local/bin/claude"
    expect_out "the build tool is installed first" "Installing what is needed to build: demo-build"
    expect_out "-y also answers the cleanup question" "Removed them"
    OUT="$("$TESTHOME/.local/bin/claude" 2>&1)"; ST=$?
    expect_status "the wrapper runs" 0
    expect_out "the wrapper runs the package executable" "demo-cli 1.0.0"
    expect_out "the wrapper exports the options" "DEMO_FLAG=1"
    if grep -q -- "-S --needed --noconfirm demo-build" "$ROOT/pkg.log"; then pass; else fail "the build tool was installed"; fi
    if grep -q -- "-R --noconfirm demo-build" "$ROOT/pkg.log"; then pass; else fail "the build tool was removed again"; fi
    tl info claude-code
    expect_out "info names the build tools" "Builds with demo-build"

    # --- build tools, with nobody to ask and with an answer ---
    : > "$ROOT/pkg.log"
    tl install claude-code
    expect_status "reinstall with nobody to ask" 0
    expect_no_out "nobody there means the build tools stay" "Removed them"
    if grep -q -- "-R --noconfirm demo-build" "$ROOT/pkg.log"; then fail "the build tool should have stayed"; else pass; fi

    : > "$ROOT/pkg.log"
    TTY_KNOB=1
    STDIN_TEXT='y
n
'
    tl install claude-code
    expect_status "reinstall, cleanup declined" 0
    expect_out "the cleanup question names the packages" "only needed for installing (demo-build)"
    if grep -q -- "-R --noconfirm demo-build" "$ROOT/pkg.log"; then fail "answering no should keep them"; else pass; fi

    : > "$ROOT/pkg.log"
    TTY_KNOB=1
    STDIN_TEXT='y
y
'
    tl install claude-code
    expect_status "reinstall, cleanup accepted" 0
    if grep -q -- "-R --noconfirm demo-build" "$ROOT/pkg.log"; then pass; else fail "answering yes should remove them"; fi

    # An item already here asks for nothing to build with.
    : > "$ROOT/pkg.log"
    tl install fakebin -y
    expect_no_out "nothing is built for what is already installed" "needed to build"

    # --- remove, with the backup put back ---
    tl remove hello -y
    expect_status "remove" 0
    expect_content "the file that was there came back" "$TESTHOME/.config/hello.conf" "changed again"
    tl list -i
    expect_no_out "a removed item is no longer installed" "hello"
    tl remove hello -y
    expect_status "removing something twice is not an error" 0
    expect_out "removing something twice says so" "not installed"

    # --- update ---
    # Put a config item back first, from the catalog the app ships, so the
    # refresh below has something whose shipped version has moved on.
    tl install hello -y --configs
    tl update --check
    expect_status "update --check" 0
    expect_out "update --check took the newer item list" "Updated the list of items"
    expect_out "update --check names what is out of date" "fakebin"
    expect_out "a config with a new version says the change will be shown" "hello has a new version"
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

    # --- graphics setup, which the app installs and tlstore only runs ---
    tl display
    expect_status "display without the app's script" 1
    expect_out "and says what to do" "update the app"
    printf '#!/bin/sh\necho "gpu-setup $*"\n' > "$TPREFIX/bin/termux-x11-gpu-setup"
    chmod +x "$TPREFIX/bin/termux-x11-gpu-setup"
    tl display --yes --keep
    expect_status "display runs the app's script" 0
    expect_out "and passes the arguments through" "gpu-setup --yes --keep"

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
