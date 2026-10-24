#!/usr/bin/env bash
# Regenerates the per-target branches from main.
#
# main holds one folder per target (<loader>-<minecraft version>/) and the shared code of each
# Minecraft version (common/<minecraft version>/src/main). Each target also has a branch named like
# its folder, holding that single project at the repository root with the shared code merged into
# src/main, so it builds on its own with ./gradlew build.
#
# The branches are never edited by hand: change main, then run this script from the repository
# root. An existing branch gets a new commit on top of its history (no force push needed); a branch
# whose content did not change is left alone.
#
# Two settings, for a repository whose line is not called main. arcadia-dungeon keeps its
# client/server version on main and its server-side version on serverside-main, which must never
# be mixed, so there it runs as:
#
#     BASE_BRANCH=serverside-main BRANCH_PREFIX=serverside- bash tools/make_branches.sh
#
# and writes serverside-neoforge-1.21.1 and friends, leaving the client/server branches alone.
#
# Author: vyrriox
set -euo pipefail

BASE_BRANCH=${BASE_BRANCH:-main}
BRANCH_PREFIX=${BRANCH_PREFIX:-}

ROOT=$(git rev-parse --show-toplevel)
cd "$ROOT"

if [ -n "$(git status --porcelain)" ]; then
    echo "Commit or stash your changes first: the branches are built from $BASE_BRANCH as committed." >&2
    exit 1
fi

MAIN=$(git rev-parse --verify "refs/heads/$BASE_BRANCH")
# Shared files copied to every branch when they exist on main.
SHARED=(LICENSE NOTICE README.md RULES.md CHANGELOG.md .gitignore .gitattributes .github docs)

WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT

for target in $(git ls-tree -d --name-only "$MAIN"); do
    if [[ ! "$target" =~ ^(neoforge|forge|fabric)-(.+)$ ]]; then
        continue
    fi
    version=${BASH_REMATCH[2]}
    if ! git cat-file -e "$MAIN:$target/build.gradle" 2>/dev/null; then
        continue
    fi

    tree_dir="$WORK/$target"
    mkdir -p "$tree_dir"

    # The project itself, moved to the root.
    git archive "$MAIN" "$target" | tar -x -C "$WORK/"
    # Shared code of this Minecraft version, merged into src/main.
    if git cat-file -e "$MAIN:common/$version/src/main" 2>/dev/null; then
        mkdir -p "$WORK/common-$target"
        git archive "$MAIN" "common/$version/src/main" | tar -x -C "$WORK/common-$target"
        mkdir -p "$tree_dir/src/main"
        cp -R "$WORK/common-$target/common/$version/src/main/." "$tree_dir/src/main/"
        rm -rf "$WORK/common-$target"
    fi
    # Shared documents and repository files.
    for item in "${SHARED[@]}"; do
        if git cat-file -e "$MAIN:$item" 2>/dev/null; then
            git archive "$MAIN" "$item" | tar -x -C "$tree_dir"
        fi
    done

    # Build the tree with a throwaway index, without touching the checked out branch.
    export GIT_INDEX_FILE="$WORK/index-$target"
    rm -f "$GIT_INDEX_FILE"
    git --work-tree="$tree_dir" add -A .
    git --work-tree="$tree_dir" update-index --chmod=+x gradlew 2>/dev/null || true
    tree=$(git write-tree)
    unset GIT_INDEX_FILE

    branch="${BRANCH_PREFIX}${target}"
    old=$(git rev-parse -q --verify "refs/heads/$branch" || true)
    if [ -n "$old" ] && [ "$(git rev-parse "$old^{tree}")" = "$tree" ]; then
        echo "$branch: unchanged"
        continue
    fi
    if [ -n "$old" ]; then
        commit=$(git commit-tree "$tree" -p "$old" -p "$MAIN" -m "chore: update $target project from main")
    else
        commit=$(git commit-tree "$tree" -p "$MAIN" -m "chore: isolate $target project at repository root")
    fi
    git update-ref "refs/heads/$branch" "$commit"
    echo "$branch: $(git rev-parse --short "$commit")"
done
