#!/usr/bin/env bash
# Signs app/src/main/assets/tlstore/catalog.tsv and app/src/main/assets/tlstore/tlstore
# so tlstore (and scripts/tlstore/install.sh, the standalone installer) will accept
# them. Both verify the signature against the trusted.pub they ship with — tlstore
# then compares catalog serials, and install.sh takes the script unconditionally
# once it is signed — so an unsigned file of either kind is ignored.
#
#   scripts/tlstore/sign.sh [catalog.tsv] [tlstore]
#
# The signing key is the maintainer's and lives outside every checkout:
# TLSTORE_SIGNING_KEY, default ~/.config/vaj-apt/tlstore-minisign.key. Do not
# generate one here, and never commit a secret key.
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo="$(cd "$here/../.." && pwd)"
catalog="${1:-$repo/app/src/main/assets/tlstore/catalog.tsv}"
script="${2:-$repo/app/src/main/assets/tlstore/tlstore}"
key="${TLSTORE_SIGNING_KEY:-$HOME/.config/vaj-apt/tlstore-minisign.key}"

[ -f "$catalog" ] || { echo "no catalog at $catalog — run build-catalog.sh first" >&2; exit 1; }
[ -f "$script" ] || { echo "no tlstore script at $script" >&2; exit 1; }
[ -f "$key" ] || { echo "no signing key at $key (set TLSTORE_SIGNING_KEY)" >&2; exit 1; }
command -v minisign >/dev/null 2>&1 || { echo "minisign is not installed" >&2; exit 1; }

serial="$(sed -n 's/^#.*serial=\([0-9][0-9]*\).*$/\1/p' "$catalog" | head -1)"
version="$(sed -n 's/^TLSTORE_VERSION=//p' "$script" | head -1)"

minisign -S -s "$key" -x "$catalog.minisig" -m "$catalog" \
    -c "tlstore catalog" -t "tlstore catalog serial=$serial"
echo "signed $catalog.minisig (serial=$serial)"

minisign -S -s "$key" -x "$script.minisig" -m "$script" \
    -c "tlstore script" -t "tlstore $version"
echo "signed $script.minisig (tlstore $version)"
