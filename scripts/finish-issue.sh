#!/usr/bin/env bash

# エラー発生時に即終了し、未定義変数やパイプラインのエラーも検知する
set -euo pipefail

# 実行場所に依存せずクリーンアップスクリプトを呼び出せるようパスを解決する
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
CLEANUP_SCRIPT="$SCRIPT_DIR/cleanup-branches.sh"
BASE_BRANCH="dev"
REMOTE="origin"

# Gitリポジトリ内で実行されているか確認
if ! git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  echo "Error: Not inside a Git repository."
  exit 1
fi

# 未コミットの変更がある場合は処理を中断する
if [[ -n "$(git status --porcelain)" ]]; then
  echo "Error: Working tree has uncommitted changes."
  echo "Commit or stash them before finishing the Issue."
  exit 1
fi

# 開発の統合先であるdevブランチへ切り替える
echo "Switching to '$BASE_BRANCH'..."
if ! git switch "$BASE_BRANCH"; then
  echo "Error: Failed to switch to '$BASE_BRANCH'."
  exit 1
fi

# 削除済みリモートブランチの参照を整理し、最新のリモート情報を取得する
echo "Fetching and pruning remote-tracking branches..."
if ! git fetch --prune; then
  echo "Error: Failed to fetch remote branch information."
  exit 1
fi

# マージコミットを作成せず、origin/devからfast-forwardのみで更新する
echo "Updating '$BASE_BRANCH' from '$REMOTE/$BASE_BRANCH'..."
if ! git pull --ff-only "$REMOTE" "$BASE_BRANCH"; then
  echo "Error: Failed to fast-forward '$BASE_BRANCH' from '$REMOTE/$BASE_BRANCH'."
  exit 1
fi

# 既存スクリプトを再利用してマージ済みローカルブランチを削除する
echo "Cleaning up merged local branches..."
if ! "$CLEANUP_SCRIPT"; then
  echo "Error: Failed to clean up merged local branches."
  exit 1
fi

echo "Issue cleanup complete. The repository is on an up-to-date '$BASE_BRANCH' branch."
