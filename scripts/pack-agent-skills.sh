#!/usr/bin/env bash
# Packs the agent skills under resources/agent-skills/ into one tarball each, for tlstore's
# skill items. Each tarball holds the skill directory as its only top-level entry, so
#   tar -xzf termux-launcher.tar.gz -C ~/.agents/skills
# lands it. termux-launcher also carries the user guide (docs/en/*.md, without _Sidebar.md and
# examples/) as docs/, which its help.md points into; the docs are copied at pack time and never
# committed under the skill.
#
# The output is byte-for-byte reproducible from a commit: names sorted, one fixed mtime, root
# ownership, normalised modes, and no name or timestamp in the gzip header.
#
# Usage: scripts/pack-agent-skills.sh [output dir]   (default: build/agent-skills)
set -euo pipefail

REPO="$(cd "$(dirname "$0")/.." && pwd)"
OUT="${1:-$REPO/build/agent-skills}"
mkdir -p "$OUT"
OUT="$(cd "$OUT" && pwd)"
SKILLS="$REPO/resources/agent-skills"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

pack() {
    local name="$1"
    LC_ALL=C tar --create --directory "$WORK" \
        --sort=name --mtime='@0' --owner=0 --group=0 --numeric-owner \
        --mode='u+rwX,go+rX,go-w' --format=gnu \
        "$name" | gzip -n -9 >"$OUT/$name.tar.gz"
}

cp -R "$SKILLS/termux-launcher" "$WORK/termux-launcher"
mkdir "$WORK/termux-launcher/docs"
for page in "$REPO"/docs/en/*.md; do
    [ "$(basename "$page")" = _Sidebar.md ] && continue
    cp "$page" "$WORK/termux-launcher/docs/"
done
pack termux-launcher

cp -R "$SKILLS/termux-launcher-speak" "$WORK/termux-launcher-speak"
pack termux-launcher-speak

(cd "$OUT" && sha256sum termux-launcher.tar.gz termux-launcher-speak.tar.gz >SHA256SUMS)
cat "$OUT/SHA256SUMS"
echo "version $(git -C "$REPO" describe --tags --always)"
