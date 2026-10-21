#!/usr/bin/env bash
# Cut a pre-release channel drop on every edition (docs/agents/releasing.md, "The pre-release channel").
#
#   scripts/cut-prerelease.sh [--publish] [--base <ref>] [--only termux|nix|vaj] X.Y.Z-pre.N [branch ...]
#
# `pre` is the channel's own, persistent branch: features bound for the channel are merged into it
# (pull requests target it); what is ready is cherry-picked onto dev at a stable minor cut.
# A drop brings `pre` level with <base> (default dev), merges each extra branch named on the command
# line, and adds one `release: vX.Y.Z-pre.N` commit setting versionName, tagged vX.Y.Z-pre.N. The
# Nix and VAJ builds come from two disposable branches rebuilt per drop in worktrees:
#   pre-nix  = nix-edition    + merge pre   tag nix-vX.Y.Z-pre.N
#   pre-vaj  = io-vaj-package + merge pre   tag vaj-vX.Y.Z-pre.N
# Each keeps its edition's identity (applicationId is checked after the merge); only versionName
# follows pre. A merge conflict stops the script with the merge left in place: resolve it in that
# worktree (identity and versionName to the branch's side, as for a release cut), commit, and re-run;
# merges already done are skipped. The release body is project-docs/prerelease-notes.md as it is on
# pre after the merges.
#
# Without --publish nothing leaves this machine: the worktrees stay under .claude/worktrees/ for
# inspection and the publish commands are printed. With --publish: push pre and the three tags,
# create the GitHub releases as drafts (--prerelease --latest=false), dispatch the APK build on each
# tag, wait for the builds, publish the three with the plain tag last, and retire the drop before: its releases become drafts (kept with their APKs and tags, for a bug
# report that names the build), and drafts beyond the newest KEEP_RETIRED drops are deleted, tag
# kept. The public list shows one pre-release per edition. The edition branches themselves are
# never pushed; their tags carry the commits.
set -euo pipefail

REPO_SLUG="PickleHik3/termux-launcher"
# Retired drops kept as drafts per edition; older drafts are deleted (their tags stay).
KEEP_RETIRED=2
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
        -h|--help) sed -n '2,23p' "$0"; exit 0 ;;
        --*) die "unknown option $1" ;;
        *) break ;;
    esac
done
[ $# -ge 1 ] || die "usage: $0 [--publish] [--base <ref>] [--only termux|nix|vaj] X.Y.Z-pre.N [branch ...]"
VERSION="$1"; shift
EXTRA=("$@")

printf '%s' "$VERSION" | grep -qE '^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)-pre\.[1-9][0-9]*$' \
    || die "version must be X.Y.Z-pre.N (got '$VERSION')"

# The device checks still owed (project-docs/device-checks.md): shown, not gated, so a drop never
# ships an unseen item unknowingly. Items are the "- " lines under its two headings.
CHECKS="project-docs/device-checks.md"
if [ -f "$CHECKS" ] && grep -q '^- ' "$CHECKS"; then
    echo "cut-prerelease: device checks still owed ($CHECKS):" >&2
    grep '^- ' "$CHECKS" | cut -c1-120 | sed 's/^/    /' >&2
fi

# The plain tag last: its publish fires the Discord post that links the other two.
EDITIONS=(nix vaj termux)
git fetch -q origin pre nix-edition io-vaj-package 2>/dev/null && EDITION_REF="origin/" || { echo "cut-prerelease: warning: could not fetch origin; using local branches" >&2; EDITION_REF=""; }
src_of()    { case "$1" in nix) echo "${EDITION_REF}nix-edition" ;; vaj) echo "${EDITION_REF}io-vaj-package" ;; esac; }
branch_of() { case "$1" in termux) echo pre ;; nix) echo pre-nix ;; vaj) echo pre-vaj ;; esac; }
tag_of()    { case "$1" in termux) echo "v$VERSION" ;; nix) echo "nix-v$VERSION" ;; vaj) echo "vaj-v$VERSION" ;; esac; }
appid_of()  { case "$1" in termux) echo com.termux ;; nix) echo com.termux.launcher.nix ;; vaj) echo io.vaj.tl ;; esac; }
# An edition's pre-release tags: the prefix of tag_of, then X.Y.Z-pre.N and nothing else.
tag_re_of() { case "$1" in termux) echo '^v[0-9]+\.[0-9]+\.[0-9]+-pre\.[0-9]+$' ;; nix) echo '^nix-v[0-9]+\.[0-9]+\.[0-9]+-pre\.[0-9]+$' ;; vaj) echo '^vaj-v[0-9]+\.[0-9]+\.[0-9]+-pre\.[0-9]+$' ;; esac; }
if [ -n "$ONLY" ]; then
    case "$ONLY" in termux|nix|vaj) EDITIONS=("$ONLY") ;; *) die "--only takes termux, nix or vaj" ;; esac
fi

cd "$ROOT"
git rev-parse --verify --quiet "$BASE^{commit}" >/dev/null || die "base ref '$BASE' does not exist"
git rev-parse --verify --quiet "refs/heads/pre" >/dev/null || die "branch pre does not exist locally; git branch pre origin/pre"
for b in "${EXTRA[@]}"; do
    git rev-parse --verify --quiet "$b^{commit}" >/dev/null || die "branch '$b' does not exist"
done
# A tag on origin means N was used; a local one is a dry run's or an interrupted run's, and is
# accepted below only where it already points at the commit this run would tag.
for e in "${EDITIONS[@]}"; do
    git ls-remote --exit-code --tags origin "refs/tags/$(tag_of "$e")" >/dev/null 2>&1 && die "tag $(tag_of "$e") already exists on origin; N is never reused"
done
if [ -n "$(git status --porcelain -- "$GRADLE" "$NOTES")" ]; then
    echo "cut-prerelease: warning: uncommitted changes to $GRADLE or $NOTES in this checkout are not part of the drop (it builds from '$BASE')." >&2
fi

version_in() { grep -m1 -E '^\s+versionName "' "$1/$GRADLE" | sed -r 's/.*"([^"]+)".*/\1/'; }
# Sets the bare versionName line; commits with <message> when that changed anything.
set_version() { # <dir> <version> <message>
    local line_re='^(\s+versionName )"[^"]+"$'
    [ "$(grep -cE "$line_re" "$1/$GRADLE")" = "1" ] || die "expected exactly one bare versionName line in $1/$GRADLE"
    [ "$(version_in "$1")" = "$2" ] && return 0
    sed -i -E "s/$line_re/\1\"$2\"/" "$1/$GRADLE"
    grep -qE "^\s+versionName \"$2\"$" "$1/$GRADLE" || die "failed to set versionName in $1/$GRADLE"
    git -C "$1" commit -q -am "$3"
}
# Tags the worktree's HEAD; an existing local tag is fine only when it already sits there.
tag_head() { # <dir> <tag>
    if git rev-parse --verify --quiet "refs/tags/$2" >/dev/null; then
        [ "$(git rev-parse "refs/tags/$2^{commit}")" = "$(git -C "$1" rev-parse HEAD)" ] \
            || die "local tag $2 points elsewhere than $1's HEAD; a stale dry run? git tag -d $2 and re-run"
        return 0
    fi
    git -C "$1" tag -a "$2" -m "$2"
}
version_of_ref() { git show "$1:$GRADLE" | grep -m1 -E '^\s+versionName "' | sed -r 's/.*"([^"]+)".*/\1/'; }
# Merges <ref> into the worktree's branch unless it is already in. The branch's versionName is
# first set to <ref>'s, so that one line (which every release: commit rewrites) never conflicts;
# a real conflict stops the script with the merge left in place.
merge_into() { # <dir> <ref> <what>
    if git -C "$1" merge-base --is-ancestor "$2" HEAD; then return 0; fi
    set_version "$1" "$(version_of_ref "$2")" "chore: align versionName with $3 for the merge"
    if ! git -C "$1" merge --no-ff --no-edit "$2" >/dev/null; then
        echo "cut-prerelease: merging $3 into $(git -C "$1" rev-parse --abbrev-ref HEAD) conflicts in:" >&2
        git -C "$1" diff --name-only --diff-filter=U >&2
        die "resolve it in $1 (identity and versionName to the branch's side), commit, and re-run; the merge is left in place"
    fi
}
# A worktree on an existing branch, or a fresh one reset to <start>; refuses to drop uncommitted work.
worktree_on() { # <dir> <branch> [<start>]
    if [ -e "$1" ]; then
        [ -z "$(git -C "$1" status --porcelain 2>/dev/null)" ] \
            || die "$1 has uncommitted changes (a conflict being resolved?); finish there and re-run, or remove it: git worktree remove --force '$1'"
        if [ "$(git -C "$1" rev-parse --abbrev-ref HEAD)" = "$2" ]; then return 0; fi
        git worktree remove --force "$1"
    fi
    git worktree prune
    if [ $# -ge 3 ]; then git worktree add -B "$2" "$1" "$3" >/dev/null; else git worktree add "$1" "$2" >/dev/null; fi
}

# --- pre: the persistent channel branch, brought level with base plus the named branches ---------
# The worktree that already has pre out (often the main checkout) is used as it is: git keeps a
# branch in one worktree, and switching a shared checkout away moves other sessions' commits. Its
# untracked files are its own business; only tracked changes would leak into the drop.
PRE_WT="$(git worktree list --porcelain | awk '/^worktree /{w=substr($0,10)} $0=="branch refs/heads/pre"{print w}')"
if [ -n "$PRE_WT" ]; then
    [ -z "$(git -C "$PRE_WT" status --porcelain --untracked-files=no)" ] \
        || die "$PRE_WT has pre out with uncommitted changes; commit or stash them there and re-run"
else
    PRE_WT="$WT_DIR/pre"
    worktree_on "$PRE_WT" pre
fi
if [ -n "$EDITION_REF" ] && ! git -C "$PRE_WT" merge -q --ff-only origin/pre 2>/dev/null; then
    die "local pre and origin/pre have diverged; reconcile them first"
fi
merge_into "$PRE_WT" "$BASE" "$BASE"
for b in "${EXTRA[@]}"; do merge_into "$PRE_WT" "$b" "branch '$b'"; done
[ -f "$PRE_WT/$NOTES" ] || die "$NOTES is missing from pre"
if ! grep -qE '^- ' "$PRE_WT/$NOTES" || grep -qE '^- Nothing yet' "$PRE_WT/$NOTES"; then
    die "$NOTES still has its placeholder lines; write the drop's notes first"
fi
if [[ " ${EDITIONS[*]} " == *" termux "* ]]; then
    set_version "$PRE_WT" "$VERSION" "release: v$VERSION"
    tag_head "$PRE_WT" "v$VERSION"
    echo "pre = $(git -C "$PRE_WT" rev-parse --short HEAD) (base $BASE at $(git rev-parse --short "$BASE")), tag v$VERSION"
fi
PRE_HEAD="$(git -C "$PRE_WT" rev-parse HEAD)"
# Where an edition's tag is cut from: pre's own worktree, or its disposable branch's.
wt_of() { [ "$1" = termux ] && echo "$PRE_WT" || echo "$WT_DIR/$(branch_of "$1")"; }

# --- the edition builds: disposable branches, each its edition plus pre ---------------------------
for e in "${EDITIONS[@]}"; do
    [ "$e" = termux ] && continue
    wt="$WT_DIR/$(branch_of "$e")"
    worktree_on "$wt" "$(branch_of "$e")" "$(src_of "$e")"
    merge_into "$wt" "$PRE_HEAD" "pre"
    got="$(grep -m1 -E '^\s+applicationId\s' "$wt/$GRADLE" | sed -r 's/.*"([^"]+)".*/\1/')"
    [ "$got" = "$(appid_of "$e")" ] || die "$(branch_of "$e") has applicationId '$got', expected '$(appid_of "$e")'; the merge took the wrong identity"
    [ "$(version_in "$wt")" = "$VERSION" ] || die "$(branch_of "$e") has versionName '$(version_in "$wt")', expected '$VERSION'"
    tag_head "$wt" "$(tag_of "$e")"
    echo "$(branch_of "$e") = $(git -C "$wt" rev-parse --short HEAD) from $(src_of "$e"), tag $(tag_of "$e")"
done
echo "notes = $PRE_WT/$NOTES (same content on every edition)"

if [ "$PUBLISH" -ne 1 ]; then
    echo
    echo "Nothing was pushed. Inspect the worktrees, then publish with:"
    echo "  git -C '$PRE_WT' push origin pre"
    for e in "${EDITIONS[@]}"; do
        wt="$(wt_of "$e")"; t="$(tag_of "$e")"
        echo "  git -C '$wt' push origin '$t'"
        echo "  gh release create '$t' --repo $REPO_SLUG --draft --prerelease --latest=false --title '$t' --notes-file '$PRE_WT/$NOTES'"
        echo "  gh workflow run attach_debug_apks_to_release.yml --repo $REPO_SLUG --ref '$t' -f release_tag='$t'"
    done
    echo "  (wait for the three attach runs, then publish nix, vaj and the plain tag last:"
    echo "   gh release edit <tag> --repo $REPO_SLUG --draft=false --latest=false)"
    echo "or re-run this script with --publish, which also retires the previous drop (drafts, then"
    echo "deletes beyond the newest $KEEP_RETIRED drafts per edition). To discard the drop:"
    for e in "${EDITIONS[@]}"; do
        [ "$e" = termux ] && { echo "  git tag -d 'v$VERSION' && git -C '$PRE_WT' reset -q --hard origin/pre   # pre: back to origin"; continue; }
        echo "  git worktree remove --force '$WT_DIR/$(branch_of "$e")' && git branch -D $(branch_of "$e") && git tag -d '$(tag_of "$e")'"
    done
    exit 0
fi

# The drop before this one, per edition: every other published pre-release becomes a draft, and
# drafts beyond the newest KEEP_RETIRED are deleted. A release that cannot be found is skipped;
# the tag is never touched. Read-only listing first, so a failing gh never half-retires.
retire_previous() { # <edition> <this drop's tag>
    local re; re="$(tag_re_of "$1")"
    gh release list --repo "$REPO_SLUG" --limit 200 --json tagName,isDraft,isPrerelease \
        --jq '.[] | select(.isPrerelease and (.isDraft|not)) | .tagName' \
        | grep -E "$re" | grep -vx "$2" | while read -r old; do
            gh release edit "$old" --repo "$REPO_SLUG" --draft >/dev/null && echo "retired $old to a draft"
        done
    gh release list --repo "$REPO_SLUG" --limit 200 --json tagName,isDraft,isPrerelease,createdAt \
        --jq '[.[] | select(.isPrerelease and .isDraft)] | sort_by(.createdAt) | reverse | .[].tagName' \
        | grep -E "$re" | tail -n +"$((KEEP_RETIRED + 1))" | while read -r old; do
            gh release delete "$old" --repo "$REPO_SLUG" --yes && echo "deleted draft $old (tag kept)"
        done
}

# Obtainium picks a repo's newest published release by date, whatever its edition, and skips drafts
# (project-docs/active/obtainium-channels-research.md). So every release is created as a draft, its
# APKs are attached while nobody can see it, and only then are the three published, nix and vaj
# first and the plain tag last, a few seconds apart: a user without an edition filter gets the
# Termux build, and nobody is offered a release that has no APK yet. The plain tag's publish also
# fires the Discord post. A re-run skips a release that already exists, so no tag gets two drafts.
newest_attach_run() { # <tag> -> id of the newest attach run dispatched on it, or nothing
    gh run list --repo "$REPO_SLUG" --workflow attach_debug_apks_to_release.yml --branch "$1" \
        --event workflow_dispatch --limit 1 --json databaseId --jq '.[0].databaseId // empty'
}
attach_run_after() { # <tag> <id before dispatch> -> the new run's id, waiting for it to appear
    local id
    for _ in $(seq 30); do
        id="$(newest_attach_run "$1")"
        [ -n "$id" ] && [ "$id" != "$2" ] && { echo "$id"; return; }
        sleep 4
    done
    die "no new attach run appeared for $1"
}

git -C "$PRE_WT" push -q origin pre
declare -A RUN
for e in "${EDITIONS[@]}"; do
    wt="$(wt_of "$e")"; t="$(tag_of "$e")"
    git -C "$wt" push -q origin "$t"
    gh release view "$t" --repo "$REPO_SLUG" >/dev/null 2>&1 \
        || gh release create "$t" --repo "$REPO_SLUG" --draft --prerelease --latest=false --title "$t" --notes-file "$PRE_WT/$NOTES"
    before="$(newest_attach_run "$t")"
    gh workflow run attach_debug_apks_to_release.yml --repo "$REPO_SLUG" --ref "$t" -f "release_tag=$t"
    RUN[$e]="$(attach_run_after "$t" "$before")"
    echo "$t: draft created, attach run ${RUN[$e]}"
done
for e in "${EDITIONS[@]}"; do
    gh run watch "${RUN[$e]}" --repo "$REPO_SLUG" --exit-status >/dev/null \
        || die "the attach run for $(tag_of "$e") failed; the releases are still drafts. Fix it and re-run the same command."
done
for e in "${EDITIONS[@]}"; do # termux is last in EDITIONS
    t="$(tag_of "$e")"
    gh release edit "$t" --repo "$REPO_SLUG" --draft=false --latest=false >/dev/null
    echo "published $t"
    sleep 3
done
for e in "${EDITIONS[@]}"; do
    retire_previous "$e" "$(tag_of "$e")"
    if [ "$e" != termux ]; then git worktree remove "$(wt_of "$e")"; git branch -q -D "$(branch_of "$e")"; fi
done
echo "published"
