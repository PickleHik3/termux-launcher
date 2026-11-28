#!/usr/bin/env bash
# Merges a worker's branch into the branch checked out here and pushes only when the merge builds
# and the contract tests pass.
#
#   scripts/merge-gated.sh BRANCH [-m MESSAGE] [--no-push] [PATTERN...]
#
#   MESSAGE   merge commit subject (default: "Merge BRANCH")
#   PATTERN   extra :app test filters for what the branch touched, e.g. 'com.termux.app.tiling.*'
#
# Exit 0: merged, checked, pushed (or kept local with --no-push).
# Exit 2: conflicts; resolve them, commit, then run scripts/contract-tests.sh --build yourself.
# Exit 1: merged but a check failed; the merge stays local and unpushed. Fix on top, or drop it
#         with the `git reset --hard` line printed below.
set -u
ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT"
[ $# -ge 1 ] || { sed -n '2,13p' "$0"; exit 64; }
branch=$1; shift
message="Merge $branch"
push=1
patterns=()
while [ $# -gt 0 ]; do
    case "$1" in
        -m) message=$2; shift 2 ;;
        --no-push) push=0; shift ;;
        *) patterns+=("$1"); shift ;;
    esac
done

if [ -n "$(git status --porcelain --untracked-files=no)" ]; then
    echo "the tree has uncommitted changes; commit or stash them first" >&2
    exit 64
fi
here=$(git rev-parse --abbrev-ref HEAD)
before=$(git rev-parse --short HEAD)

if ! git merge --no-ff "$branch" -m "$message" > /dev/null 2>&1; then
    echo "conflicts merging $branch into $here:"
    git diff --name-only --diff-filter=U | sed 's/^/  /'
    echo "resolve, commit, then: scripts/contract-tests.sh --build ${patterns[*]:-}"
    exit 2
fi

if ! scripts/contract-tests.sh --build "${patterns[@]}"; then
    echo "NOT pushed. The merge is local on $here; to drop it: git reset --hard $before"
    exit 1
fi

after=$(git rev-parse --short HEAD)
if [ "$push" = 1 ]; then
    git push origin "$here" 2>&1 | tail -1
    echo "merged $branch into $here at $after and pushed"
else
    echo "merged $branch into $here at $after (not pushed)"
fi
