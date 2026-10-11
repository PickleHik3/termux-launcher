#!/usr/bin/env bash
# Cut a pre-release channel drop on every edition (AGENTS.md, "The pre-release channel").
#
#   scripts/cut-prerelease.sh [--publish] [--base <ref>] [--only termux|nix|vaj] X.Y.Z-pre.N [experiment-branch ...]
#
# The drop's content is <base> (default dev) plus every experiment branch merged --no-ff. From it
# the three disposable branches are rebuilt in worktrees under .claude/worktrees/:
#   pre      = content + `release: vX.Y.Z-pre.N`                      tag vX.Y.Z-pre.N
#   pre-nix  = nix-edition    + merge content + `release: nix-v…`      tag nix-vX.Y.Z-pre.N
#   pre-vaj  = io-vaj-package + merge content + `release: vaj-v…`      tag vaj-vX.Y.Z-pre.N
# The edition branches keep their identity (applicationId is checked after the merge); only
# versionName changes, by the release commit. A merge conflict stops the script: resolve it the
# way an edition cut does (identity to the edition's side) in that worktree, then re-run with
# --only for the edition that failed. The release body is project-docs/prerelease-notes.md.
#
# Without --publish nothing leaves this machine: the worktrees stay for inspection and the publish
# commands are printed. With --publish: force-push the branches, push the tags, create the GitHub
# releases (--prerelease --latest=false) and dispatch the APK build on each tag.
set -euo pipefail

REPO_SLUG="PickleHik3/termux-launcher"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WT_DIR="$ROOT/.claude/worktrees"
NOTES="project-docs/prerelease-notes.md"
GRADLE="app/build.gradle"

die() { echo "cut-prerelease: $*" >&2; exit 1; }

PUBLISH=0
BASE="dev"
ONLY=""
while [ $# -gt 0 ]; do
    case "$1" in
        --publish) PUBLISH=1; shift ;;
        --base) [ $# -ge 2 ] || die "--base needs a ref"; BASE="$2"; shift 2 ;;
        --only) [ $# -ge 2 ] || die "--only needs termux, nix or vaj"; ONLY="$2"; shift 2 ;;
        -h|--help) sed -n '2,19p' "$0"; exit 0 ;;
        --*) die "unknown option $1" ;;
        *) break ;;
    esac
done
[ $# -ge 1 ] || die "usage: $0 [--publish] [--base <ref>] [--only termux|nix|vaj] X.Y.Z-pre.N [experiment-branch ...]"
VERSION="$1"; shift
EXPERIMENTS=("$@")

printf '%s' "$VERSION" | grep -qE '^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)-pre\.[1-9][0-9]*$' \
    || die "version must be X.Y.Z-pre.N (got '$VERSION')"

# edition -> source branch, pre branch, tag, applicationId
EDITIONS=(termux nix vaj)
# Edition branches are release branches: take them from origin so a stale local copy cannot
# feed an old identity or versionName into the drop (falls back to the local branch offline).
git fetch -q origin nix-edition io-vaj-package 2>/dev/null && EDITION_REF="origin/" || { echo "cut-prerelease: warning: could not fetch origin; using local edition branches" >&2; EDITION_REF=""; }
src_of()    { case "$1" in termux) echo "$BASE" ;; nix) echo "${EDITION_REF}nix-edition" ;; vaj) echo "${EDITION_REF}io-vaj-package" ;; esac; }
branch_of() { case "$1" in termux) echo pre ;; nix) echo pre-nix ;; vaj) echo pre-vaj ;; esac; }
tag_of()    { case "$1" in termux) echo "v$VERSION" ;; nix) echo "nix-v$VERSION" ;; vaj) echo "vaj-v$VERSION" ;; esac; }
appid_of()  { case "$1" in termux) echo com.termux ;; nix) echo com.termux.launcher.nix ;; vaj) echo io.vaj.tl ;; esac; }
if [ -n "$ONLY" ]; then
    case "$ONLY" in termux|nix|vaj) EDITIONS=("$ONLY") ;; *) die "--only takes termux, nix or vaj" ;; esac
fi

cd "$ROOT"
git rev-parse --verify --quiet "$BASE^{commit}" >/dev/null || die "base ref '$BASE' does not exist"
for b in "${EXPERIMENTS[@]}"; do
    git rev-parse --verify --quiet "$b^{commit}" >/dev/null || die "experiment branch '$b' does not exist"
done
for e in "${EDITIONS[@]}"; do
    git rev-parse --verify --quiet "$(src_of "$e")^{commit}" >/dev/null || die "edition branch '$(src_of "$e")' does not exist"
    t="$(tag_of "$e")"
    git rev-parse --verify --quiet "refs/tags/$t" >/dev/null && die "tag $t already exists locally; N is never reused"
    git ls-remote --exit-code --tags origin "refs/tags/$t" >/dev/null 2>&1 && die "tag $t already exists on origin; N is never reused"
done
if [ -n "$(git status --porcelain -- "$GRADLE" "$NOTES")" ]; then
    echo "cut-prerelease: warning: uncommitted changes to $GRADLE or $NOTES in this checkout are not part of the drop (it builds from '$BASE')." >&2
fi

# A worktree is the script's own; refuse to throw away anything someone left in it.
fresh_worktree() { # <dir> <branch> <start>
    if [ -e "$1" ]; then
        if git -C "$1" rev-parse --is-inside-work-tree >/dev/null 2>&1 && [ -n "$(git -C "$1" status --porcelain)" ]; then
            die "$1 has uncommitted changes (a conflict being resolved?); finish there and re-run with --only, or remove it: git worktree remove --force '$1'"
        fi
        git worktree remove --force "$1"
    fi
    git worktree prune
    git worktree add -B "$2" "$1" "$3" >/dev/null
}
merge_into() { # <dir> <ref> <what>
    if ! git -C "$1" merge --no-ff --no-edit "$2" >/dev/null; then
        echo "cut-prerelease: merging $3 into $(git -C "$1" rev-parse --abbrev-ref HEAD) conflicts in:" >&2
        git -C "$1" diff --name-only --diff-filter=U >&2
        die "resolve it in $1 (identity and versionName to the edition's side, as for a release cut), commit, and re-run with --only; the merge is left in place"
    fi
}
set_version() { # <dir> <tag>
    local line_re='^(\s+versionName )"[^"]+"$'
    [ "$(grep -cE "$line_re" "$1/$GRADLE")" = "1" ] || die "expected exactly one bare versionName line in $1/$GRADLE"
    sed -i -E "s/$line_re/\1\"$VERSION\"/" "$1/$GRADLE"
    grep -qE "^\s+versionName \"$VERSION\"$" "$1/$GRADLE" || die "failed to set versionName in $1/$GRADLE"
    git -C "$1" commit -q -am "release: $2"
    git -C "$1" tag -a "$2" -m "$2"
}

# The drop's content: base plus the experiments, built once on a detached worktree.
CONTENT_WT="$WT_DIR/pre-content"
fresh_worktree "$CONTENT_WT" pre-content "$BASE"
MERGED=()
for b in "${EXPERIMENTS[@]}"; do
    merge_into "$CONTENT_WT" "$b" "experiment '$b'"
    MERGED+=("$b")
done
[ -f "$CONTENT_WT/$NOTES" ] || die "$NOTES is missing from the tree"
if ! grep -qE '^- ' "$CONTENT_WT/$NOTES" || grep -qE '^- Nothing yet' "$CONTENT_WT/$NOTES"; then
    die "$NOTES still has its placeholder lines; write the drop's notes on $BASE first"
fi
CONTENT="$(git -C "$CONTENT_WT" rev-parse HEAD)"
CONTENT_VERSION="$(grep -m1 -E '^\s+versionName "' "$CONTENT_WT/$GRADLE" | sed -r 's/.*"([^"]+)".*/\1/')"

for e in "${EDITIONS[@]}"; do
    wt="$WT_DIR/$(branch_of "$e")"
    if [ "$e" = termux ]; then
        fresh_worktree "$wt" pre "$CONTENT"
    else
        fresh_worktree "$wt" "$(branch_of "$e")" "$(src_of "$e")"
        # The edition's versionName is overwritten below anyway; aligning it with the content's
        # first keeps that line from conflicting when the edition sits on an older stable.
        if ! grep -qE "^\s+versionName \"$CONTENT_VERSION\"$" "$wt/$GRADLE"; then
            sed -i -E "s/^(\s+versionName )\"[^\"]+\"$/\1\"$CONTENT_VERSION\"/" "$wt/$GRADLE"
            git -C "$wt" commit -q -am "chore: align versionName with dev for the pre-release merge"
        fi
        merge_into "$wt" "$CONTENT" "the drop's content"
        # The merge must not have taken dev's side of the identity (the v0.2.31 hotfix).
        got="$(grep -m1 -E '^\s+applicationId\s' "$wt/$GRADLE" | sed -r 's/.*"([^"]+)".*/\1/')"
        [ "$got" = "$(appid_of "$e")" ] || die "$(branch_of "$e") has applicationId '$got', expected '$(appid_of "$e")'; the merge took dev's identity"
    fi
    set_version "$wt" "$(tag_of "$e")"
    echo "$(branch_of "$e") = $(git -C "$wt" rev-parse --short HEAD) from $(src_of "$e") at $(git rev-parse --short "$(src_of "$e")"), tag $(tag_of "$e")"
done
git worktree remove --force "$CONTENT_WT"
git branch -q -D pre-content
if [ ${#MERGED[@]} -gt 0 ]; then echo "merged: ${MERGED[*]}"; else echo "merged: nothing (the drop is $BASE as it stands)"; fi
echo "notes = $WT_DIR/pre/$NOTES (same content on every edition)"

if [ "$PUBLISH" -ne 1 ]; then
    echo
    echo "Nothing was pushed. Inspect the worktrees, then publish with:"
    for e in "${EDITIONS[@]}"; do
        wt="$WT_DIR/$(branch_of "$e")"; t="$(tag_of "$e")"
        echo "  git -C '$wt' push --force origin $(branch_of "$e") && git -C '$wt' push origin '$t'"
        echo "  gh release create '$t' --repo $REPO_SLUG --prerelease --latest=false --title '$t' --notes-file '$wt/$NOTES'"
        echo "  gh workflow run attach_debug_apks_to_release.yml --repo $REPO_SLUG --ref '$t' -f release_tag='$t'"
    done
    echo "or re-run this script with --publish. To discard the drop:"
    for e in "${EDITIONS[@]}"; do
        echo "  git worktree remove --force '$WT_DIR/$(branch_of "$e")' && git branch -D $(branch_of "$e") && git tag -d '$(tag_of "$e")'"
    done
    exit 0
fi

for e in "${EDITIONS[@]}"; do
    wt="$WT_DIR/$(branch_of "$e")"; t="$(tag_of "$e")"
    git -C "$wt" push --force origin "$(branch_of "$e")"
    git -C "$wt" push origin "$t"
    gh release create "$t" --repo "$REPO_SLUG" --prerelease --latest=false --title "$t" --notes-file "$wt/$NOTES"
    gh workflow run attach_debug_apks_to_release.yml --repo "$REPO_SLUG" --ref "$t" -f "release_tag=$t"
    git worktree remove "$wt"
done
echo "published; watch the APK runs with: gh run list --repo $REPO_SLUG --workflow attach_debug_apks_to_release.yml"
