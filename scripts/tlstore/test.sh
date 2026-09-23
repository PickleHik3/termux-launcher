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
#   TLSTORE_HOST=launcher  — which app the store is running in. The suite also
#                            drives the detection itself, through TERM_PROGRAM,
#                            TERM_PROGRAM_VERSION and the marker file the app
#                            writes, and checks all three.
#   TLSTORE_RAW_BASE       — the top of the repository a hand-installed tlstore
#                            reads; the suite points it at a file:// tree laid
#                            out the way this one is.
#   TLSTORE_UI=no-such-ui  — the tlstore-ui binary bare `tlstore` execs inside
#                            the launcher. There is no pty here, so the suite
#                            never actually execs one; it points this at a
#                            fake executable to prove the routing decision, and
#                            at a name that is not there for the fallback.
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

# The Revision 5 columns (category upstream setup standfirst does1..3 try
# notes author licence size picture picture-digest demo featured), appended
# after summary. Every fixture row carries one of these two so the catalog
# stays 26 columns wide; R5_HELLO gives "hello" a category and makes it the
# featured item, so list/search/info --tsv have something real to read back.
R5_NONE="-	-	0	-	-	-	-	-	-	-	-	-	-	-	-	0"
R5_HELLO="Tools	-	0	a greeting from the item list	Shows a greeting	Keeps it plain text	Does nothing else	hello	one note|another note	Test Author	MIT	~1 KB	file://example/hello.jpg	deadbeef	-	1"

# write_catalog <file> <serial> <version> <fakebin payload> — the version is the
# one hello and fakebin carry, so a newer catalog moves a config item on too.
write_catalog() {
    local out="$1" serial="$2" fbver="$3" fbfile="$4"
    {
        printf '# tlstore catalog\tserial=%s\n' "$serial"
        printf '# name\tkind\tversion\tprefixes\tsource\tdigest\ttarget\trequires\toptions\tsummary\tcategory\tupstream\tsetup\tstandfirst\tdoes1\tdoes2\tdoes3\ttry\tnotes\tauthor\tlicence\tsize\tpicture\tpicture-digest\tdemo\tfeatured\n'
        printf 'hello\tfile\t%s\t*\tfile://%s/hello.conf\t%s\t~/.config/hello.conf\t-\t-\tA greeting you can read.\t%s\n' "$fbver" "$FX" "$(sha "$FX/hello.conf")" "$R5_HELLO"
        printf 'mine\tfile-once\t1\t*\tfile://%s/mine.conf\t%s\t~/.config/mine.conf\t-\t-\tYours to edit, installed once.\t%s\n' "$FX" "$(sha "$FX/mine.conf")" "$R5_NONE"
        printf 'fakebin\tbinary\t%s\t*\tfile://%s/%s\t%s\t-\t-\t-\tA small tool for the terminal.\t%s\n' "$fbver" "$FX" "$fbfile" "$(sha "$FX/$fbfile")" "$R5_NONE"
        printf 'badsum\tbinary\t1\t*\tfile://%s/hello.conf\t%s\t~/.local/bin/badsum\t-\t-\tNever installs, on purpose.\t%s\n' "$FX" "0000000000000000000000000000000000000000000000000000000000000000" "$R5_NONE"
        printf 'twin\tbinary\t1\tio.vaj.tl\tfile://%s/other.bin\t%s\t~/.local/bin/twin\t-\t-\tThe other edition build.\t%s\n' "$FX" "$(sha "$FX/other.bin")" "$R5_NONE"
        printf 'twin\tbinary\t1\tcom.termux\tfile://%s/twin.bin\t%s\t~/.local/bin/twin\t-\t-\tThis edition build.\t%s\n' "$FX" "$(sha "$FX/twin.bin")" "$R5_NONE"
        printf 'ghost\tbinary\t1\tio.vaj.tl\tfile://%s/other.bin\t%s\t-\t-\t-\tOnly for another edition.\t%s\n' "$FX" "$(sha "$FX/other.bin")" "$R5_NONE"
        printf 'demo-pkg\tpkg\t-\t*\tdemo-one demo-two\t-\t-\t-\t-\tTwo packages from the package manager.\t%s\n' "$R5_NONE"
        printf 'musl-loader\tbinary\t1\tcom.termux\tfile://%s/loader.bin\t%s\t~/.local/lib/musl/ld-musl-aarch64.so.1\t-\t-\tWhat tools from other systems need to start.\t%s\n' "$FX" "$(sha "$FX/loader.bin")" "$R5_NONE"
        printf 'claude-code\tnpm-musl\tlatest\t*\tnpm:demo-cli#claude\t-\t-\tmusl-loader,demo-pkg\tenv=DEMO_FLAG=1;tz=1;build=demo-build\tA tool that comes from npm.\t%s\n' "$R5_NONE"
        printf 'musl-cxx\tbinary\t1\t*\tfile://%s/musllib.bin\t%s\t~/.local/lib/musl/libdemo++.so.6\t-\thidden=1\tA library tools from other systems need.\t%s\n' "$FX" "$(sha "$FX/musllib.bin")" "$R5_NONE"
        printf 'agent\tnpm-musl\tlatest\t*\tnpm:demo-agent#bin/agent\t-\t-\tmusl-loader,musl-cxx\tmusl-libs=musl-cxx\tAn agent that comes from npm.\t%s\n' "$R5_NONE"
        printf 'kit\tbundle\t-\t*\t-\t-\t-\thello,fakebin,demo-pkg,secret\t-\tA few things at once.\t%s\n' "$R5_NONE"
        printf 'plug\tfisher\t-\t*\tdemo/one demo/two\t-\t-\t-\t-\tPlugins for the shell.\t%s\n' "$R5_NONE"
        printf 'secret\tfile\t1\t*\tfile://%s/mine.conf\t%s\t~/.config/secret.conf\t-\thidden=1\tA part of something else.\t%s\n' "$FX" "$(sha "$FX/mine.conf")" "$R5_NONE"
        printf 'launcheronly\tbinary\t1\t*\tfile://%s/twin.bin\t%s\t~/.local/bin/launcheronly\t-\thost=launcher\tOnly where the launcher runs it.\t%s\n' "$FX" "$(sha "$FX/twin.bin")" "$R5_NONE"
        printf 'termuxonly\tbinary\t1\t*\tfile://%s/other.bin\t%s\t~/.local/bin/termuxonly\t-\thost=termux\tOnly in the plain app.\t%s\n' "$FX" "$(sha "$FX/other.bin")" "$R5_NONE"
        printf 'recentonly\tbinary\t1\t*\tfile://%s/twin.bin\t%s\t~/.local/bin/recentonly\t-\tmin-launcher=0.3.0\tWants a recent app.\t%s\n' "$FX" "$(sha "$FX/twin.bin")" "$R5_NONE"
        # A real, fetchable picture and demo, so `tlstore picture` has
        # something genuine to verify and cache. Digests are computed here,
        # not folded into R5_HELLO/R5_NONE, since they depend on the fixture
        # files this function's caller already made.
        printf 'pictured\tfile\t1\t*\tfile://%s/hello.conf\t%s\t~/.config/pictured.conf\t-\t-\tHas a real picture and demo, for the picture command tests.\tTools\t-\t0\tan item with a picture\tShows a picture\tKeeps it simple\tHas a demo too\tpictured\t-\tTest Author\tMIT\t~1 KB\tfile://%s/pictured.jpg\t%s\tfile://%s/pictured-demo.jpg\t0\n' \
            "$FX" "$(sha "$FX/hello.conf")" "$FX" "$(sha "$FX/pictured.jpg")" "$FX"
        # Same picture file, but the catalog's own digest for it is wrong —
        # `tlstore picture` must refuse it the way any other digest mismatch is.
        printf 'badpic\tfile\t1\t*\tfile://%s/hello.conf\t%s\t~/.config/badpic.conf\t-\t-\tHas a picture whose digest never matches, on purpose.\tTools\t-\t0\t-\t-\t-\t-\t-\t-\tTest Author\tMIT\t-\tfile://%s/pictured.jpg\t0000000000000000000000000000000000000000000000000000000000000000\t-\t0\n' \
            "$FX" "$(sha "$FX/hello.conf")" "$FX"
        # A binary whose source is a FIFO: curl blocks reading it until this
        # test writes to the other end, so a --progress install can be
        # cancelled reliably while it is still in flight.
        printf 'slow\tbinary\t1\t*\tfile://%s/slow.pipe\t-\t-\t-\t-\tA slow item, so a --progress cancel can land mid-download.\t%s\n' "$FX" "$R5_NONE"
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
    printf '#!/bin/sh\necho fakebin 3\n' > "$FX/fakebin-3"
    printf '#!/bin/sh\necho twin here\n' > "$FX/twin.bin"
    printf '#!/bin/sh\necho other edition\n' > "$FX/other.bin"
    printf 'not really a loader\n' > "$FX/loader.bin"
    printf 'a picture worth caching\n' > "$FX/pictured.jpg"
    printf 'a demo worth caching\n' > "$FX/pictured-demo.jpg"
    rm -f "$FX/slow.pipe"
    mkfifo "$FX/slow.pipe"

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

    # A fake npm registry: two package documents and their tarballs.
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

    # The second keeps its executable in a subdirectory and needs a library the
    # loader alone does not provide — opencode's shape.
    mkdir -p "$FX/registry/demo-agent" "$FX/agentsrc/package/bin"
    cat > "$FX/agentsrc/package/bin/agent" <<'EOF'
#!/bin/sh
echo "demo-agent 2.0.0"
EOF
    chmod +x "$FX/agentsrc/package/bin/agent"
    tar czf "$FX/demo-agent-2.0.0.tgz" -C "$FX/agentsrc" package
    printf '{"name":"demo-agent","version":"2.0.0","dist":{"tarball":"file://%s/demo-agent-2.0.0.tgz","integrity":"sha512-%s"}}\n' \
        "$FX" "$(sha512_b64 "$FX/demo-agent-2.0.0.tgz")" > "$FX/registry/demo-agent/latest"
    printf 'the musl C++ library\n' > "$FX/musllib.bin"

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
            TLSTORE_HOST="${HOST_KNOB:-}" \
            TLSTORE_UI="${UI_KNOB:-}" \
            TLSTORE_RAW_BASE="${RAWBASE_KNOB:-}" \
            TERM_PROGRAM="${TP_KNOB:-}" \
            TERM_PROGRAM_VERSION="${TPV_KNOB:-}" \
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
            TLSTORE_HOST="${HOST_KNOB:-}" \
            TLSTORE_UI="${UI_KNOB:-}" \
            TLSTORE_RAW_BASE="${RAWBASE_KNOB:-}" \
            TERM_PROGRAM="${TP_KNOB:-}" \
            TERM_PROGRAM_VERSION="${TPV_KNOB:-}" \
            "${SHCMD[@]}" "$TLSTORE" "$@" < /dev/null 2>&1)"
    fi
    ST=$?
    STDIN_TEXT=""
    TTY_KNOB=0
    HOST_KNOB=""
    UI_KNOB=""
    RAWBASE_KNOB=""
    TP_KNOB=""
    TPV_KNOB=""
    return 0
}

# tl_stdout [args...] — like tl, but OUT is stdout alone (stderr discarded),
# to prove --progress keeps its machine lines clean of the human narration
# say() sends to stderr in that mode; no test besides that one needs it.
tl_stdout() {
    OUT="$(env -i \
        HOME="$TESTHOME" PATH="$RUNPATH" \
        TLSTORE_PREFIX="$TPREFIX" \
        TLSTORE_CATALOG_URL="$CATALOG_URL" \
        TLSTORE_NPM_REGISTRY="file://$FX/registry" \
        TLSTORE_ARCH=aarch64 \
        TLSTORE_PATCHELF=true \
        TLSTORE_ASSUME_TTY="${TTY_KNOB:-0}" \
        TLSTORE_HOST="${HOST_KNOB:-}" \
        TLSTORE_UI="${UI_KNOB:-}" \
        TLSTORE_RAW_BASE="${RAWBASE_KNOB:-}" \
        TERM_PROGRAM="${TP_KNOB:-}" \
        TERM_PROGRAM_VERSION="${TPV_KNOB:-}" \
        "${SHCMD[@]}" "$TLSTORE" "$@" < /dev/null 2>/dev/null)"
    ST=$?
    HOST_KNOB=""
    UI_KNOB=""
    RAWBASE_KNOB=""
    TP_KNOB=""
    TPV_KNOB=""
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
    HOST_KNOB=""
    UI_KNOB=""
    RAWBASE_KNOB=""
    TP_KNOB=""
    TPV_KNOB=""

    # --- help, version, usage ---
    tl help; expect_status "help" 0; expect_out "help lists commands" "tlstore install"
    expect_no_out "help no longer mentions browse" "browse"
    tl version; expect_status "version" 0; expect_out "version names the item list" "2026090601"
    tl nonsense; expect_status "unknown command is a usage error" 2
    tl browse; expect_status "browse is no longer a command" 2
    tl list -x; expect_status "unknown option is a usage error" 2
    tl search; expect_status "search with no term is a usage error" 2
    tl remove; expect_status "remove with no name is a usage error" 2
    tl info nosuch; expect_status "info on an unknown item fails" 1

    # --- bare command: tlstore-ui inside the launcher, the list everywhere else ---
    # This harness has no pty, so on_terminal is always false here — every one
    # of these falls back to the list; the exec itself is proved below with a
    # real pty, when one is available.
    tl
    expect_status "no arguments, not the launcher" 0
    expect_out "prints the item list" "hello"
    expect_out "and one line on how to install" "Run 'tlstore install' to add what you want."
    expect_no_out "not the old full usage text" "tlstore info <name>"
    HOST_KNOB=launcher
    tl
    expect_status "no arguments, the launcher, but no terminal here" 0
    expect_out "still prints the item list" "hello"
    HOST_KNOB=launcher
    UI_KNOB="$ROOT/no-such-tlstore-ui"
    tl
    expect_status "no arguments, the launcher, a ui path that is not there" 0
    expect_out "falls back to the item list" "hello"
    if command -v script >/dev/null 2>&1; then
        ui_fake="$FIXBIN/fake-tlstore-ui"
        printf '#!/bin/sh\necho TLSTORE_UI_RAN\n' > "$ui_fake"
        chmod +x "$ui_fake"
        ui_log="$ROOT/ui.typescript"
        script -qc "env -i HOME='$TESTHOME' PATH='$RUNPATH' TLSTORE_PREFIX='$TPREFIX' \
            TLSTORE_CATALOG_URL='$CATALOG_URL' TLSTORE_HOST=launcher TLSTORE_UI='$ui_fake' \
            ${SHCMD[*]} '$TLSTORE'" "$ui_log" >/dev/null 2>&1
        if grep -q TLSTORE_UI_RAN "$ui_log" 2>/dev/null; then
            pass
        else
            fail "no arguments execs tlstore-ui inside the launcher, with a real terminal"
        fi
        rm -f "$ui_log" "$ui_fake"
    else
        skip "bare-command exec routing" "script is not installed"
    fi

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

    # --- which app the store is running in ---
    # Nothing in the environment and no marker file: the plain app.
    tl list
    expect_no_out "an item for the launcher is hidden in the plain app" "launcheronly"
    expect_out "an item for the plain app shows there" "termuxonly"
    HOST_KNOB=launcher
    tl list
    expect_out "TLSTORE_HOST=launcher shows the launcher's items" "launcheronly"
    expect_no_out "and hides the plain app's" "termuxonly"
    TP_KNOB=termux-launcher
    tl list
    expect_out "the launcher names itself in the environment" "launcheronly"
    TP_KNOB=something-else
    tl list
    expect_no_out "any other app in the environment is not the launcher" "launcheronly"
    # Over ssh nothing names the app; the file it writes on every start does.
    touch "$TPREFIX/libexec/termux-launcher/tlstore/.installed"
    tl list
    expect_out "with nothing else to go on, the app's own file answers" "launcheronly"
    rm -f "$TPREFIX/libexec/termux-launcher/tlstore/.installed"
    tl list
    expect_no_out "and without it this is the plain app" "launcheronly"
    tl info launcheronly
    expect_status "info on an item for the other app fails" 1
    expect_out "and says it is not in the list" "not in the list"
    HOST_KNOB=launcher
    tl info launcheronly
    expect_status "info on it in the launcher works" 0
    tl install launcheronly -y
    expect_status "installing an item for the other app is refused" 1

    # --- an item that wants a recent launcher ---
    TP_KNOB=termux-launcher
    TPV_KNOB=0.2.39
    tl list
    expect_no_out "an older launcher does not see it" "recentonly"
    TP_KNOB=termux-launcher
    TPV_KNOB=0.3.1
    tl list
    expect_out "a newer launcher sees it" "recentonly"
    TP_KNOB=termux-launcher
    tl list
    expect_out "a launcher that does not say its version sees it" "recentonly"
    tl list
    expect_out "and so does the plain app" "recentonly"

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

    # --- npm-musl with extra musl libraries, and an executable in a subdirectory ---
    tl install agent -y
    expect_status "install an npm-musl item that needs more of musl" 0
    expect_file "the extra library is beside the loader" "$TESTHOME/.local/lib/agent/libdemo++.so.6"
    expect_file "the loader is there too" "$TESTHOME/.local/lib/agent/ld-musl-aarch64.so.1"
    expect_file "the executable keeps its own path inside the package" "$TESTHOME/.local/lib/agent/bin/agent"
    expect_file "the wrapper is named after the command, not the path" "$TESTHOME/.local/bin/agent"
    expect_file "the library is also installed on its own" "$TESTHOME/.local/lib/musl/libdemo++.so.6"
    OUT="$("$TESTHOME/.local/bin/agent" 2>&1)"; ST=$?
    expect_status "the wrapper runs" 0
    expect_out "the wrapper runs the package executable" "demo-agent 2.0.0"
    tl remove agent -y
    expect_status "remove it again" 0

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
    expect_out "doctor names the app it is running in" "^App *Termux (com.termux)"
    TP_KNOB=termux-launcher
    TPV_KNOB=0.2.40
    tl doctor
    expect_out "doctor names the launcher and its version" "^App *Termux Launcher 0.2.40 (com.termux)"

    # --- the columns a program reads ---
    tl list --tsv
    expect_status "list --tsv" 0
    expect_out "list --tsv says what is installed, with both versions" \
        $'^hello\tinstalled\t2\t2\tfile\tA greeting'
    expect_out "list --tsv leaves the installed column empty for the rest" \
        $'^twin\tavailable\t1\t\tbinary\t'
    expect_no_out "list --tsv prints no mark column" '^\* '
    expect_no_out "list --tsv hides the parts of other items" "^secret"
    expect_out "list --tsv carries the category and featured columns" \
        $'^hello\tinstalled\t2\t2\tfile\tA greeting you can read.\tTools\t1$'
    expect_out "an item with no category prints -, not featured" \
        $'^twin\tavailable\t1\t\tbinary\t.*\t-\t0$'
    tl list --tsv -a
    expect_no_out "list --tsv -a is only what you do not have" $'^hello\t'
    tl list --tsv -i
    expect_out "list --tsv -i is only what you have" $'^hello\tinstalled\t'
    tl search --tsv greeting
    expect_status "search --tsv" 0
    expect_out "search --tsv has the same columns as list" $'^hello\tinstalled\t2\t2\tfile\t'
    expect_out "search --tsv carries category and featured too" \
        $'^hello\tinstalled\t2\t2\tfile\tA greeting you can read.\tTools\t1$'
    tl info --tsv hello
    expect_status "info --tsv" 0
    expect_out "info --tsv gives one key and value per line" $'^Kind\tfile$'
    expect_out "info --tsv names the version" $'^Version\t2$'
    expect_out "info --tsv names where it came from" $'^From\tfile://'
    expect_out "info --tsv names the checksum" $'^Checksum\t'
    expect_out "info --tsv names what it needs" $'^Needs\t'
    expect_out "info --tsv names where the file goes" $'^Files\t.*hello.conf$'
    expect_out "info --tsv says what is installed" $'^Installed\t2$'
    expect_out "info --tsv ends with the summary" $'^Summary\tA greeting you can read.$'
    expect_no_out "info --tsv is not the form for people" "^hello$"
    # --- the Revision 5 item-spread fields, round-tripped through info --tsv ---
    expect_out "info --tsv names the category" $'^Category\tTools$'
    expect_no_out "info --tsv skips an unknown upstream" $'^Upstream\t'
    expect_out "info --tsv always names setup, 0 being an answer" $'^Setup\t0$'
    expect_out "info --tsv names the standfirst" $'^Standfirst\ta greeting from the item list$'
    expect_out "info --tsv names does1" $'^Does1\tShows a greeting$'
    expect_out "info --tsv names does2" $'^Does2\tKeeps it plain text$'
    expect_out "info --tsv names does3" $'^Does3\tDoes nothing else$'
    expect_out "info --tsv names try" $'^Try\thello$'
    expect_out "info --tsv keeps notes pipe-separated" $'^Notes\tone note|another note$'
    expect_out "info --tsv names the author" $'^Author\tTest Author$'
    expect_out "info --tsv names the licence" $'^Licence\tMIT$'
    expect_out "info --tsv names the size" $'^Size\t~1 KB$'
    expect_out "info --tsv names the picture" $'^Picture\tfile://example/hello.jpg$'
    expect_out "info --tsv names the picture digest alongside it" $'^Picture-digest\tdeadbeef$'
    expect_out "info --tsv always names featured, 0 being an answer" $'^Featured\t1$'
    tl info --tsv twin
    expect_no_out "an item with no picture prints no Picture line" $'^Picture\t'
    expect_out "and featured still prints as 0" $'^Featured\t0$'
    tl info --tsv claude-code
    expect_out "info --tsv names the build tools" $'^Builds with\tdemo-build$'
    tl info --tsv
    expect_status "info --tsv still needs a name" 2
    # --- the same handful, for a person reading the terminal ---
    tl info hello
    expect_out "human info also names the category" "Category *Tools"
    expect_out "human info also names the author" "Author *Test Author"
    expect_out "human info also names the licence" "Licence *MIT"
    expect_out "human info also names the size" "Size *~1 KB"
    expect_no_out "human info does not dump the item-spread copy" "Standfirst"

    # --- tlstore picture: a digest-verified, cached local copy for tlstore-ui ---
    PIC_DIGEST="$(sha "$FX/pictured.jpg")"
    PIC_CACHE="$TESTHOME/.cache/tlstore/pictures/$PIC_DIGEST.jpg"
    rm -rf "$TESTHOME/.cache/tlstore"

    tl_stdout picture pictured
    expect_status "picture prints a path and exits 0" 0
    expect_out "it names the digest-keyed cache copy" "^$PIC_CACHE\$"
    expect_content "the cached copy is really the picture" "$PIC_CACHE" "a picture worth caching"

    # A second call must never touch the source again: move it out of the way
    # and prove the same cached path still comes back, offline.
    mv "$FX/pictured.jpg" "$FX/pictured.jpg.moved"
    tl_stdout picture pictured
    expect_status "a second call is instant and offline" 0
    expect_out "and returns the very same cached path" "^$PIC_CACHE\$"
    mv "$FX/pictured.jpg.moved" "$FX/pictured.jpg"

    tl_stdout picture pictured demo
    expect_status "picture demo prints the demo's path" 0
    expect_content "and it really is the demo picture" "$OUT" "a demo worth caching"

    tl_stdout picture twin
    expect_status "an item with no picture fails" 1
    expect_no_out "and prints nothing on stdout" "."

    tl_stdout picture no-such-item
    expect_status "an unknown item fails" 1
    expect_no_out "and prints nothing on stdout" "."

    tl_stdout picture hello
    expect_status "a picture whose source cannot be fetched fails" 1
    expect_no_out "and prints nothing on stdout" "."
    expect_no_file "and nothing was cached for it" "$TESTHOME/.cache/tlstore/pictures/deadbeef.jpg"

    tl_stdout picture badpic
    expect_status "a picture whose digest does not match fails" 1
    expect_no_out "and prints nothing on stdout" "."
    expect_no_file "and nothing was cached for it either" \
        "$TESTHOME/.cache/tlstore/pictures/0000000000000000000000000000000000000000000000000000000000000000.jpg"

    # A newer list, put in place without a refresh, so there is something to
    # report as out of date.
    write_catalog "$TESTHOME/.local/share/tlstore/catalog.tsv" 2026090610 3 fakebin-3
    tl update --check --tsv --offline
    expect_status "update --check --tsv" 0
    expect_out "it names the item, what you have and what there is" $'^fakebin\t2\t3\t$'
    expect_out "a config item is marked as one that asks" $'^hello\t2\t3\tconfig-asks$'
    expect_out "an item pinned to the newest there is says so" $'^claude-code\t1.0.0\tlatest\tlatest$'
    expect_no_out "and nothing a person would read" "up to date"
    expect_no_out "and no packages line" "package manager"
    write_catalog "$TESTHOME/.local/share/tlstore/catalog.tsv" 2026090603 2 fakebin-2
    tl update --check --tsv --offline
    expect_no_out "with nothing out of date the row is gone" $'^fakebin\t'
    expect_out "and only the item pinned to the newest is left" $'^claude-code\t1.0.0\tlatest\tlatest$'

    # --- keeping tlstore itself current, when it was installed by hand ---
    if [ "$HAVE_MINISIGN" = 1 ]; then
        store="$TPREFIX/libexec/termux-launcher/tlstore"
        # Each base is the top of a repository, laid out the way this one is.
        storepath="app/src/main/assets/tlstore"
        mkdir -p "$FX/selfnew/$storepath" "$FX/selfsame/$storepath" "$FX/selfbad/$storepath"
        sed 's/^TLSTORE_VERSION=.*/TLSTORE_VERSION=9.9/' "$TLSTORE" > "$FX/selfnew/$storepath/tlstore"
        printf '# the newer one\n' >> "$FX/selfnew/$storepath/tlstore"
        cp "$TLSTORE" "$FX/selfsame/$storepath/tlstore"
        cp "$FX/selfnew/$storepath/tlstore" "$FX/selfbad/$storepath/tlstore"
        for d in selfnew selfsame selfbad; do
            minisign -S -s "$ROOT/key.sec" -x "$FX/$d/$storepath/tlstore.minisig" \
                -m "$FX/$d/$storepath/tlstore" >/dev/null 2>&1
        done
        printf '# nudged after signing\n' >> "$FX/selfbad/$storepath/tlstore"

        cp "$TLSTORE" "$TPREFIX/bin/tlstore"
        printf 'file://%s\n' "$FX/selfnew" > "$store/.standalone"
        tl update -y
        expect_status "update on a hand-installed tlstore" 0
        expect_out "it says which version is in place now" "tlstore is now version 9.9"
        if grep -q "^# the newer one" "$TPREFIX/bin/tlstore"; then pass; else fail "the newer tlstore replaced the old one"; fi

        cp "$TLSTORE" "$TPREFIX/bin/tlstore"
        RAWBASE_KNOB="file://$FX/selfsame"
        tl update -y
        expect_no_out "the same version is not installed again" "tlstore is now version"
        if grep -q "^# the newer one" "$TPREFIX/bin/tlstore"; then fail "nothing should have been replaced"; else pass; fi

        printf 'file://%s\n' "$FX/selfbad" > "$store/.standalone"
        tl update -y
        expect_out "a tlstore whose signature does not cover it is refused" "not signed by the launcher"
        if grep -q "^# the newer one" "$TPREFIX/bin/tlstore"; then fail "a refused tlstore must not land"; else pass; fi

        printf 'file://%s\n' "$FX/selfnew" > "$store/.standalone"
        touch "$store/.installed"
        tl update -y
        expect_no_out "inside the launcher tlstore leaves itself alone" "tlstore is now version"
        if grep -q "^# the newer one" "$TPREFIX/bin/tlstore"; then fail "the app's own copy must not be replaced"; else pass; fi
        rm -f "$store/.installed" "$store/.standalone" "$TPREFIX/bin/tlstore"
    else
        skip "self-update tests" "minisign is not installed"
    fi

    # --- --progress: the machine stream tlstore-ui reads ---
    tl remove fakebin -y
    tl_stdout install fakebin --progress
    expect_status "a successful --progress install" 0
    expect_out "the fetched step" $'^step\tfakebin\t10\tfetched$'
    expect_out "the signature-checked step" $'^step\tfakebin\t60\tsignature checked$'
    expect_out "the putting-files step" $'^step\tfakebin\t90\tputting files in place$'
    expect_out "the ready step" $'^step\tfakebin\t100\tready$'
    expect_out "the done line, ok" $'^done\tfakebin\tok\tinstalled$'
    expect_no_out "stdout alone carries no human narration" "Installing:"
    expect_file "the item really is installed" "$TESTHOME/.local/bin/fakebin"

    tl install badsum --progress
    expect_status "a failing --progress install still exits nonzero" 1
    expect_out "the done line, failed" $'^done\tbadsum\tfailed\tcould not install badsum$'
    expect_no_out "a failed item never claims to be ready" $'^step\tbadsum\t100\tready$'

    write_catalog "$TESTHOME/.local/share/tlstore/catalog.tsv" 2026090699 9 fakebin-3
    tl update --progress --offline
    expect_status "a --progress update" 0
    expect_out "the update steps stream on stdout" $'^step\tfakebin\t10\tfetched$'
    expect_out "and finish ready" $'^step\tfakebin\t100\tready$'
    expect_out "the done line for an update" $'^done\tfakebin\tok\tupdated$'
    expect_no_out "self-update never runs inside a --progress stream" "tlstore is now version"

    printf 'the users own greeting\n' > "$TESTHOME/.config/hello.conf"
    forget_state
    tl install hello --progress
    expect_status "a --progress install that would touch a config file still succeeds" 0
    expect_content "the users config is kept, never replaced" \
        "$TESTHOME/.config/hello.conf" "the users own greeting"
    expect_out "the done line says it was kept" $'^done\thello\tok\tkept your hello.conf$'
    expect_no_out "no diff leaks onto stdout" "^---"
    expect_no_out "and no replace question either" "Replace your"

    tl remove hello --progress
    expect_status "a --progress remove" 0
    expect_out "the remove steps stream too" $'^step\thello\t30\tfetched$'
    expect_out "and finish ready" $'^step\thello\t100\tready$'
    expect_out "the done line for a remove" $'^done\thello\tok\tremoved$'

    # --- cancel: SIGTERM to the whole process group (what tlstore-ui sends
    # when the person backs out of a running --progress job) stops cleanly ---
    forget_state
    rm -rf "$TESTHOME/.config/hello.conf" "$TESTHOME/.local/bin"
    CANCEL_OUT="$ROOT/cancel.out"
    CANCEL_PART="$TESTHOME/.local/bin/.slow.part"
    : > "$CANCEL_OUT"
    set -m
    env -i \
        HOME="$TESTHOME" PATH="$RUNPATH" \
        TLSTORE_PREFIX="$TPREFIX" \
        TLSTORE_CATALOG_URL="$CATALOG_URL" \
        TLSTORE_NPM_REGISTRY="file://$FX/registry" \
        TLSTORE_ARCH=aarch64 \
        TLSTORE_PATCHELF=true \
        "${SHCMD[@]}" "$TLSTORE" install hello slow -y --progress > "$CANCEL_OUT" 2>/dev/null &
    CANCEL_PID=$!
    CANCEL_I=0
    while ! pgrep -f 'slow\.pipe' >/dev/null 2>&1 && [ "$CANCEL_I" -lt 100 ]; do
        sleep 0.05
        CANCEL_I=$((CANCEL_I + 1))
    done
    if pgrep -f 'slow\.pipe' >/dev/null 2>&1; then
        pass
    else
        fail "the slow item's download really started before the cancel was sent"
    fi
    kill -TERM -- -"$CANCEL_PID" 2>/dev/null
    wait "$CANCEL_PID" 2>/dev/null
    set +m
    CANCEL_TXT="$(cat "$CANCEL_OUT" 2>/dev/null)"
    if printf '%s' "$CANCEL_TXT" | grep -q "$(printf 'done\tslow\tfailed\tCancelled.')"; then
        pass
    else
        fail "the cancelled item gets a done/failed/Cancelled line" "$CANCEL_TXT"
    fi
    if [ -e "$CANCEL_PART" ]; then
        fail "the half-written part file was left behind"
    else
        pass
    fi
    if printf '%s' "$CANCEL_TXT" | grep -q "$(printf 'done\thello\tok')"; then
        pass
    else
        fail "an item that finished before the cancel still gets its own done line" "$CANCEL_TXT"
    fi
    tl list -i
    expect_out "the item that finished before the cancel stays installed" "hello"
    expect_no_out "the cancelled item was never recorded as installed" "slow"
    forget_state
    rm -rf "$TESTHOME/.config/hello.conf" "$TESTHOME/.local/bin"

    # --- the numbered picker (the only picker now; fzf is gone) ---
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
# ---------------------------------------------------------------------------
# Backward compatibility: a phone's already-installed tlstore against the new
# catalog. Revision 5 only ever appends columns (see the TSV contract in
# project-docs/tlstore/REVISION-5.md), so the script already on dev, unchanged,
# must still read this worktree's catalog shape.
# ---------------------------------------------------------------------------

echo "== the dev-branch tlstore reads the Revision 5 catalog"
OLD_TLSTORE="$(mktemp)"
if git -C "$repo" show dev:app/src/main/assets/tlstore/tlstore > "$OLD_TLSTORE" 2>/dev/null; then
    SHELL_LABEL="dev-branch tlstore"
    build_fixture
    CATALOG_URL="file://$FX/newer.tsv"
    old_env() {
        env -i HOME="$TESTHOME" PATH="$RUNPATH" \
            TLSTORE_PREFIX="$TPREFIX" TLSTORE_CATALOG_URL="$CATALOG_URL" \
            TLSTORE_ARCH=aarch64 TLSTORE_PATCHELF=true \
            /bin/sh "$OLD_TLSTORE" "$@" 2>&1
    }
    OUT="$(old_env list)"; ST=$?
    expect_status "its list command still exits 0 against this catalog" 0
    expect_out "and still shows an item from it" "hello"
    OUT="$(old_env info hello)"; ST=$?
    expect_status "its info command still works" 0
    expect_out "and still reads the summary, unaware of the columns after it" \
        "A greeting you can read."
    mkdir -p "$TESTHOME/.config"
    OUT="$(old_env install hello -y)"; ST=$?
    expect_status "it can still install an item from the Revision 5 catalog" 0
    expect_file "the file landed" "$TESTHOME/.config/hello.conf"
    rm -rf "$ROOT"
else
    skip "dev-branch compatibility check" "no dev branch reachable from this worktree"
fi
rm -f "$OLD_TLSTORE"

echo
echo "== build-catalog.sh"
SHELL_LABEL=build-catalog
BC_ROOT="$(mktemp -d)"
mkdir -p "$BC_ROOT/scripts/tlstore/pictures" "$BC_ROOT/app/src/main/assets/tlstore"
cp "$repo/scripts/tlstore/build-catalog.sh" "$BC_ROOT/scripts/tlstore/build-catalog.sh"
printf 'a fake picture\n' > "$BC_ROOT/scripts/tlstore/pictures/demo.jpg"
BC_PIC_DIGEST="$(sha "$BC_ROOT/scripts/tlstore/pictures/demo.jpg")"
BC_SUMS="$BC_ROOT/SHA256SUMS"
printf '1111111111111111111111111111111111111111111111111111111111111111  demo-aarch64\n' > "$BC_SUMS"

# bc_write_items <items.tsv path> <featured 0|1> <category>
bc_write_items() {
    printf 'demo\tbinary\t1\t*\tbinaries:demo@1.0\t-\t-\t-\tA demo item.\t%s\tdemo/demo\t0\ta demo item for testing\tShows a demo\tDoes another thing\tDoes one more thing\tdemo\t-\tDemo Author\tMIT\t-\tlauncher:scripts/tlstore/pictures/demo.jpg@abc123\t-\t%s\n' \
        "$3" "$2" > "$1"
    printf 'part\tpkg\t-\t*\tdemo-pkg\t-\t-\thidden=1\tA hidden part.\t-\t-\t0\t-\t-\t-\t-\t-\t-\t-\t-\t-\t-\t-\t0\n' >> "$1"
}

bc_items="$BC_ROOT/scripts/tlstore/items.tsv"
bc_cat="$BC_ROOT/app/src/main/assets/tlstore/catalog.tsv"

bc_write_items "$bc_items" 1 Tools
OUT="$(cd "$BC_ROOT" && bash scripts/tlstore/build-catalog.sh "$BC_SUMS" 2>&1)"; ST=$?
if [ "$ST" = 0 ]; then pass; else fail "build-catalog.sh runs against a Revision 5 items.tsv" "$OUT"; fi
if [ -f "$bc_cat" ]; then pass; else fail "it writes the catalog"; fi
if awk -F'\t' 'NR==3 { exit (NF == 26) ? 0 : 1 }' "$bc_cat"; then pass; else fail "the header row has 26 columns"; fi
if awk -F'\t' -v want="$BC_PIC_DIGEST" '$1=="demo" { exit ($24==want) ? 0 : 1 }' "$bc_cat"; then
    pass
else
    fail "the picture's digest was computed the same way the item's own is"
fi
if awk -F'\t' '$1=="demo" { exit ($11=="Tools" && $26=="1") ? 0 : 1 }' "$bc_cat"; then
    pass
else
    fail "category and featured round-trip into the catalog"
fi

printf 'demo\tbinary\t1\t*\tbinaries:demo@1.0\t-\t-\t-\tA demo item.\tTools\n' > "$bc_items"
OUT="$(cd "$BC_ROOT" && bash scripts/tlstore/build-catalog.sh "$BC_SUMS" 2>&1)"; ST=$?
if [ "$ST" != 0 ]; then pass; else fail "a row missing the Revision 5 columns is refused"; fi

bc_write_items "$bc_items" 1 Nonsense
OUT="$(cd "$BC_ROOT" && bash scripts/tlstore/build-catalog.sh "$BC_SUMS" 2>&1)"; ST=$?
if [ "$ST" != 0 ]; then pass; else fail "an unknown category is refused"; fi

bc_write_items "$bc_items" 0 Tools
OUT="$(cd "$BC_ROOT" && bash scripts/tlstore/build-catalog.sh "$BC_SUMS" 2>&1)"; ST=$?
if [ "$ST" != 0 ]; then pass; else fail "no item at all being featured is refused"; fi

rm -rf "$BC_ROOT"

echo
# ---------------------------------------------------------------------------
# The Neovim colour scheme nvim-theme installs
# ---------------------------------------------------------------------------

# The store's nvim-colors/nvim-palette rows still resolve against the pinned
# commit in catalog.tsv, but the working copy of that colour scheme moved into
# the theme-templates pack (and its palette module became a rendered template),
# so this section only runs where the old working copy is still checked out.
# scripts/theme-templates/check.sh covers the pack's own copy.
NVIMDIR="$repo/docs/en/examples/nvim"
if [ -d "$NVIMDIR" ] && command -v nvim >/dev/null 2>&1; then
    echo "== the Neovim colour scheme"
    SHELL_LABEL=nvim
    nvroot="$(mktemp -d)"
    mkdir -p "$nvroot/home/.termux"
    cat > "$nvroot/probe.lua" <<'PROBE'
vim.cmd.colorscheme "launcher-material"
-- Every group the scheme promises to paint, in the families a config actually
-- reads: buffer, syntax, chrome, diagnostics, treesitter, git.
local need = {
  "Normal", "NormalFloat", "Comment", "Constant", "String", "Identifier",
  "Function", "Statement", "Keyword", "Type", "Special", "Error", "Todo",
  "LineNr", "CursorLine", "Visual", "Search", "Pmenu", "PmenuSel",
  "StatusLine", "StatusLineNC", "DiagnosticError", "DiagnosticWarn",
  "DiagnosticInfo", "DiagnosticHint", "@keyword", "@string", "@function",
  "@type", "@comment", "GitSignsAdd", "DiffAdd", "DiffChange", "DiffDelete",
}
local missing = {}
for _, group in ipairs(need) do
  local hl = vim.api.nvim_get_hl(0, { name = group, link = false })
  if not (hl.fg or hl.bg or hl.sp) then
    table.insert(missing, group)
  end
end
local normal = vim.api.nvim_get_hl(0, { name = "Normal", link = false })
io.stdout:write(("colors_name=%s missing=%s normal_fg=%06X\n"):format(
  tostring(vim.g.colors_name),
  #missing == 0 and "none" or table.concat(missing, ","),
  normal.fg or 0))
PROBE
    cat > "$nvroot/palette.sh" <<'PALETTE'
export TERMUX_MATERIAL_TERMINAL_BACKGROUND='#1A1111'
export TERMUX_MATERIAL_TERMINAL_FOREGROUND='#F1DEDD'
export TERMUX_MATERIAL_TERMINAL_COLOR1='#FF5449'
export TERMUX_MATERIAL_TERMINAL_COLOR2='#4CAF50'
export TERMUX_MATERIAL_TERMINAL_COLOR3='#FFC107'
export TERMUX_MATERIAL_TERMINAL_COLOR4='#4D9BE6'
export TERMUX_MATERIAL_TERMINAL_COLOR5='#C77DFF'
export TERMUX_MATERIAL_TERMINAL_COLOR6='#4DD0E1'
export TERMUX_MATERIAL_TERMINAL_COLOR8='#8C7A78'
export TERMUX_MATERIAL_TERMINAL_COLOR9='#FF8A80'
export TERMUX_MATERIAL_TERMINAL_COLOR10='#81C784'
export TERMUX_MATERIAL_TERMINAL_COLOR11='#FFD54F'
export TERMUX_MATERIAL_TERMINAL_COLOR12='#82B1FF'
export TERMUX_MATERIAL_TERMINAL_COLOR13='#E1BEE7'
export TERMUX_MATERIAL_TERMINAL_COLOR14='#80DEEA'
export TERMUX_MATERIAL_TERMINAL_COLOR15='#FFFFFF'
export TERMUX_MATERIAL_ON_SURFACE_VARIANT='#D8C2C0'
export TERMUX_MATERIAL_SURFACE_CONTAINER='#271D1D'
export TERMUX_MATERIAL_SURFACE_CONTAINER_HIGH='#322828'
export TERMUX_MATERIAL_SURFACE_CONTAINER_HIGHEST='#3D3232'
export TERMUX_MATERIAL_OUTLINE='#A08C8B'
export TERMUX_MATERIAL_OUTLINE_VARIANT='#534343'
export TERMUX_MATERIAL_PRIMARY='#FFB4AB'
export TERMUX_MATERIAL_TERTIARY='#E7BF8F'
export TERMUX_MATERIAL_ERROR='#FF5449'
PALETTE

    nvprobe() {
        OUT="$(HOME="$nvroot/home" nvim --headless -u NONE \
            --cmd "set rtp^=$NVIMDIR" -l "$nvroot/probe.lua" 2>&1)"
        ST=$?
    }

    # No palette yet — plain Termux, or before the first wallpaper.
    rm -f "$nvroot/home/.termux/material-colors.sh"
    nvprobe
    expect_status "the colour scheme loads with no wallpaper palette" 0
    expect_out "it names itself" "colors_name=launcher-material"
    expect_out "every group it promises is painted" "missing=none"
    expect_out "the fixed palette is used" "normal_fg=ABB2BF"

    # With the palette the launcher writes on a wallpaper change.
    cp "$nvroot/palette.sh" "$nvroot/home/.termux/material-colors.sh"
    nvprobe
    expect_status "the colour scheme loads from the wallpaper palette" 0
    expect_out "it still names itself" "colors_name=launcher-material"
    expect_out "every group is still painted" "missing=none"
    expect_out "the wallpaper's foreground is used" "normal_fg=F1DEDD"

    rm -rf "$nvroot"
else
    echo "== nvim is not installed — the colour scheme was not loaded"
    SKIP=$((SKIP + 1))
fi

echo "== installer suite (scripts/tlstore/test-install.sh)"
ti_out="$("$repo/scripts/tlstore/test-install.sh" "$@" 2>&1)"
echo "$ti_out" | sed 's/^/   /' | tail -3
ti_line="$(echo "$ti_out" | grep '^passed ' | tail -1)"
if [ -n "$ti_line" ]; then
    read -r _ ti_p _ ti_f _ ti_s <<<"${ti_line//,/}"
    PASS=$((PASS + ti_p)); FAIL=$((FAIL + ti_f)); SKIP=$((SKIP + ti_s))
    [ "$ti_f" = 0 ] || FAILED_NAMES+=("test-install.sh")
else
    FAIL=$((FAIL + 1)); FAILED_NAMES+=("test-install.sh did not finish")
fi

echo
if command -v shellcheck >/dev/null 2>&1; then
    echo "== shellcheck -s sh"
    if shellcheck -s sh "$TLSTORE" "$repo/scripts/tlstore/install.sh"; then
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
