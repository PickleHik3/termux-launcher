#!/usr/bin/env bash
# Signs app/src/main/assets/tlstore/catalog.tsv so tlstore will accept it as a
# refresh. tlstore verifies the signature against the trusted.pub it ships with,
# and only then compares serials — an unsigned catalog is ignored.
#
#   scripts/tlstore/sign-catalog.sh [catalog.tsv]
#
# The signing key is the maintainer's and lives outside every checkout:
# TLSTORE_SIGNING_KEY, default ~/.config/vaj-apt/tlstore-minisign.key. Do not
# generate one here, and never commit a secret key.
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo="$(cd "$here/../.." && pwd)"
catalog="${1:-$repo/app/src/main/assets/tlstore/catalog.tsv}"
key="${TLSTORE_SIGNING_KEY:-$HOME/.config/vaj-apt/tlstore-minisign.key}"

[ -f "$catalog" ] || { echo "no catalog at $catalog — run build-catalog.sh first" >&2; exit 1; }
[ -f "$key" ] || { echo "no signing key at $key (set TLSTORE_SIGNING_KEY)" >&2; exit 1; }
command -v minisign >/dev/null 2>&1 || { echo "minisign is not installed" >&2; exit 1; }

serial="$(sed -n 's/^#.*serial=\([0-9][0-9]*\).*$/\1/p' "$catalog" | head -1)"

minisign -S -s "$key" -x "$catalog.minisig" -m "$catalog" \
    -c "tlstore catalog" -t "tlstore catalog serial=$serial"

echo "signed $catalog.minisig (serial=$serial)"
