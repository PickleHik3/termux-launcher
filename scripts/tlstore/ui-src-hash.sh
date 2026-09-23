#!/usr/bin/env bash
# A stable hash of the tools/tlstore-ui crate sources (src/**, Cargo.toml, Cargo.lock).
#
#   scripts/tlstore/ui-src-hash.sh
#
# Two callers must ever run this and must always agree:
#   - tools/tlstore-ui/build.rs, at compile time, bakes the result into the binary
#     (TLSTORE_UI_SRC_HASH, printed by `tlstore-ui --version`).
#   - app/build.gradle's checkTlstoreUiFresh task, which recomputes it from the working tree
#     and compares it against what the committed asset binaries have baked in, so a source
#     change that never made it through `build-ui.sh --install` fails the build instead of
#     shipping a stale tlstore-ui.
#
# Paths are hashed relative to the crate root so the result does not depend on which worktree
# or absolute path it is run from.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
CRATE="$ROOT/tools/tlstore-ui"

SHASUM=(sha256sum)
command -v sha256sum >/dev/null 2>&1 || SHASUM=(shasum -a 256)

cd "$CRATE"
{
    find src -type f | LC_ALL=C sort
    echo Cargo.toml
    echo Cargo.lock
} | while IFS= read -r f; do
    "${SHASUM[@]}" "$f"
done | "${SHASUM[@]}" | awk '{print $1}'
