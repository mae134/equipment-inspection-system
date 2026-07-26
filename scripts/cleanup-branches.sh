#!/usr/bin/env bash

# エラー発生時に即終了し、未定義変数やパイプラインのエラーも検知する
set -euo pipefail

# Gitリポジトリ内で実行されているか確認
if ! git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  echo "Error: Not inside a Git repository."
  exit 1
fi

echo "Fetching and pruning remote-tracking branches..."
git fetch --prune

CURRENT_BRANCH="$(git branch --show-current)"
MERGED_BRANCHES=()

# HEADへマージ済みのローカルブランチから、現在のブランチと保護対象を除外する
while IFS= read -r branch_name; do
  [[ -z "$branch_name" ]] && continue
  [[ "$branch_name" == "$CURRENT_BRANCH" ]] && continue

  case "$branch_name" in
    main | master | develop | dev)
      continue
      ;;
  esac

  MERGED_BRANCHES+=("$branch_name")
done < <(git for-each-ref --format='%(refname:short)' --merged=HEAD refs/heads)

if [[ ${#MERGED_BRANCHES[@]} -eq 0 ]]; then
  echo "No merged local branches to delete."
  exit 0
fi

for branch_name in "${MERGED_BRANCHES[@]}"; do
  echo "Deleting merged local branch: $branch_name"
  git branch -d -- "$branch_name"
done

echo "Branch cleanup complete. Deleted ${#MERGED_BRANCHES[@]} branch(es)."
