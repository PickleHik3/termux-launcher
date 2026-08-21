#!/usr/bin/env bash
# Regenerates the pinned digest tables inside setup-launcher.
#
# setup-launcher refuses to install a template whose sha256 does not match the
# table it carries, so every template edit has to be followed by a run of this
# script and a commit of the result. Run it from anywhere; it edits the
# setup-launcher next to itself.
#
#   update-setup-launcher-digests.sh                 templates only
#   update-setup-launcher-digests.sh path/to/dist    templates + showcase binaries
#
# The optional directory holds the release assets, named exactly as they are
# uploaded: sigye-aarch64, fastfetch-aarch64, kitten-aarch64. Build them with
# recipes/cross/*. Without it the binary table is emptied, and setup-launcher
# then skips those items with a message instead of installing them unverified.
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
script="$here/setup-launcher"
dist="${1:-}"

# Keep in sync with the files setup-launcher fetches.
files=(
    aliens-material.omp.json
    conf.d-personal.fish
    config.fish
    setup-nvim
)

# Keep in sync with the binaries setup-launcher offers.
binaries=(
    fastfetch
    kitten
    sigye
)

table=$'# --- BEGIN DIGESTS (generated: update-setup-launcher-digests.sh) ---\nexpected_digest() {\n    case "$1" in\n'
for name in "${files[@]}"; do
    path="$here/$name"
    [ -f "$path" ] || { echo "missing template: $name" >&2; exit 1; }
    digest="$(sha256sum "$path" | cut -d' ' -f1)"
    table+="$(printf '        %-25s echo %s ;;\n' "$name)" "$digest")"$'\n'
done
table+=$'        *) echo "" ;;\n    esac\n}\n# --- END DIGESTS ---'

binary_table=$'# --- BEGIN BINARY DIGESTS (generated: update-setup-launcher-digests.sh) ---\nexpected_binary_digest() {\n    case "$1" in\n'
for name in "${binaries[@]}"; do
    [ -n "$dist" ] || continue
    path="$dist/$name-aarch64"
    if [ ! -f "$path" ]; then
        echo "no built binary for $name at $path — leaving it unpinned" >&2
        continue
    fi
    digest="$(sha256sum "$path" | cut -d' ' -f1)"
    binary_table+="$(printf '        %-12s echo %s ;;\n' "$name)" "$digest")"$'\n'
done
binary_table+=$'        *) echo "" ;;\n    esac\n}\n# --- END BINARY DIGESTS ---'

python3 - "$script" <<PY
import re, sys
path = sys.argv[1]
source = open(path, encoding="utf-8").read()
for begin, end, table in (
    ("# --- BEGIN DIGESTS", "# --- END DIGESTS ---", """$table"""),
    ("# --- BEGIN BINARY DIGESTS", "# --- END BINARY DIGESTS ---", """$binary_table"""),
):
    source, count = re.subn(
        re.escape(begin) + r".*?" + re.escape(end),
        lambda _, table=table: table,
        source,
        flags=re.DOTALL,
    )
    if count != 1:
        raise SystemExit("%s block not found in %s" % (begin, path))
open(path, "w", encoding="utf-8").write(source)
PY

echo "updated digest tables in $script"
echo "remember to move TEMPLATES_REF to the release tag these files ship in"
