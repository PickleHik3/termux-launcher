#!/usr/bin/env bash
# Regenerates the pinned digest table inside setup-launcher.
#
# setup-launcher refuses to install a template whose sha256 does not match the
# table it carries, so every template edit has to be followed by a run of this
# script and a commit of the result. Run it from anywhere; it edits the
# setup-launcher next to itself.
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
script="$here/setup-launcher"

# Keep in sync with the files setup-launcher fetches.
files=(
    aliens-material.omp.json
    conf.d-personal.fish
    config.fish
    setup-nvim
    termux-launcher.omp.json
)

table=$'# --- BEGIN DIGESTS (generated: update-setup-launcher-digests.sh) ---\nexpected_digest() {\n    case "$1" in\n'
for name in "${files[@]}"; do
    path="$here/$name"
    [ -f "$path" ] || { echo "missing template: $name" >&2; exit 1; }
    digest="$(sha256sum "$path" | cut -d' ' -f1)"
    table+="$(printf '        %-25s echo %s ;;\n' "$name)" "$digest")"$'\n'
done
table+=$'        *) echo "" ;;\n    esac\n}\n# --- END DIGESTS ---'

python3 - "$script" <<PY
import re, sys
path = sys.argv[1]
table = """$table"""
source = open(path, encoding="utf-8").read()
updated, count = re.subn(
    r"# --- BEGIN DIGESTS.*?# --- END DIGESTS ---",
    lambda _: table,
    source,
    flags=re.DOTALL,
)
if count != 1:
    raise SystemExit("digest block not found in %s" % path)
open(path, "w", encoding="utf-8").write(updated)
PY

echo "updated digest table in $script"
echo "remember to move TEMPLATES_REF to the release tag these files ship in"
