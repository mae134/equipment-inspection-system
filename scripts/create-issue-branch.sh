#!/usr/bin/env bash

# エラー発生時に即終了し、未定義変数やパイプラインのエラーも検知する
set -euo pipefail

# shellcheck source=scripts/lib/branch-name.sh
source "$(dirname "${BASH_SOURCE[0]}")/lib/branch-name.sh"
# shellcheck source=scripts/lib/ai-branch-name.sh
source "$(dirname "${BASH_SOURCE[0]}")/lib/ai-branch-name.sh"
# shellcheck source=scripts/lib/issue.sh
source "$(dirname "${BASH_SOURCE[0]}")/lib/issue.sh"

# コマンドライン引数
ISSUE_NUMBER="${1:-}"
BRANCH_NAME="${2:-}"

# 固定値
BASE_BRANCH="dev"
REMOTE="origin"

# --------------------------------------------------
# 引数チェック
# --------------------------------------------------

# Issue番号が指定され、引数が2個以内であることを確認
if [[ -z "$ISSUE_NUMBER" || $# -gt 2 ]]; then
  echo "Usage: $0 <issue-number> [branch-name]"
  exit 1
fi

# --------------------------------------------------
# 必要なコマンドの存在確認
# --------------------------------------------------

# Gitがインストールされているか確認
if ! command -v git >/dev/null 2>&1; then
  echo "Error: git is not installed."
  exit 1
fi

# GitHub CLIがインストールされているか確認
if ! command -v gh >/dev/null 2>&1; then
  echo "Error: GitHub CLI (gh) is not installed."
  exit 1
fi

# --------------------------------------------------
# 実行環境チェック
# --------------------------------------------------

# Gitリポジトリ内で実行されているか確認
if ! git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  echo "Error: Not inside a Git repository."
  exit 1
fi

# 未コミットの変更がある場合は誤操作防止のため中断
if [[ -n "$(git status --porcelain)" ]]; then
  echo "Error: Working tree has uncommitted changes."
  echo "Commit or stash them before creating a new branch."
  exit 1
fi

# --------------------------------------------------
# Issue・ブランチ存在チェック
# --------------------------------------------------

# 指定したIssueが存在することを確認し、タイトルを取得
if ! ISSUE_TITLE="$(get_issue_title "$ISSUE_NUMBER" 2>/dev/null)"; then
  echo "Error: Issue #$ISSUE_NUMBER was not found or GitHub CLI authentication failed."
  exit 1
fi

# ブランチ名が省略された場合のみ、AI候補と対話入力を使用する
if [[ -z "$BRANCH_NAME" ]]; then
  echo
  echo "Issue #$ISSUE_NUMBER:"
  echo "$ISSUE_TITLE"
  echo

  SUGGESTED_BRANCH_NAME=""
  if SUGGESTED_BRANCH_NAME="$(generate_branch_name_suggestion "$ISSUE_NUMBER" "$ISSUE_TITLE")"; then
    echo "Suggested branch name:"
    echo "$SUGGESTED_BRANCH_NAME"
    echo
  else
    echo "AI branch-name generation is unavailable."
    echo "Please enter a branch name manually."
    echo
  fi

  BRANCH_NAME="$(prompt_for_branch_name "$SUGGESTED_BRANCH_NAME")"
fi

# AI候補・手入力・明示指定のいずれも最終的に同じ検証を通す
if ! validate_branch_name "$BRANCH_NAME"; then
  exit 1
fi

# ローカルに同名ブランチが存在しないか確認
if git show-ref --verify --quiet "refs/heads/$BRANCH_NAME"; then
  echo "Error: Local branch already exists: $BRANCH_NAME"
  exit 1
fi

# リモート情報を最新化
git fetch "$REMOTE" --prune

# ベースブランチ(origin/dev)が存在するか確認
if ! git show-ref --verify --quiet "refs/remotes/$REMOTE/$BASE_BRANCH"; then
  echo "Error: Remote base branch '$REMOTE/$BASE_BRANCH' does not exist."
  exit 1
fi

# リモートに同名ブランチが存在しないか確認
if git ls-remote --exit-code --heads "$REMOTE" "$BRANCH_NAME" >/dev/null 2>&1; then
  echo "Error: Remote branch already exists: $REMOTE/$BRANCH_NAME"
  exit 1
fi

# --------------------------------------------------
# devブランチへ切り替え・最新化
# --------------------------------------------------

# ローカルにdevブランチが存在する場合は切り替え、
# 存在しない場合はorigin/devから作成する
if git show-ref --verify --quiet "refs/heads/$BASE_BRANCH"; then
  git switch "$BASE_BRANCH"
else
  echo "Local '$BASE_BRANCH' branch not found."
  echo "Creating it from '$REMOTE/$BASE_BRANCH'..."
  git switch --create "$BASE_BRANCH" --track "$REMOTE/$BASE_BRANCH"
fi

# devブランチを最新状態に更新
git pull --ff-only "$REMOTE" "$BASE_BRANCH"

# --------------------------------------------------
# Issueに紐付いたブランチを作成
# --------------------------------------------------

# GitHub Issueと関連付けたブランチを作成し、チェックアウトする
gh issue develop "$ISSUE_NUMBER" \
  --base "$BASE_BRANCH" \
  --name "$BRANCH_NAME" \
  --checkout

# --------------------------------------------------
# リモート追跡ブランチ(upstream)設定
# --------------------------------------------------

# GitHub側で既にブランチが作成されている場合はupstreamのみ設定し、
# 作成されていない場合はpushしてupstreamを設定する
if git ls-remote --exit-code --heads "$REMOTE" "$BRANCH_NAME" >/dev/null 2>&1; then
  git branch \
    --set-upstream-to="$REMOTE/$BRANCH_NAME" \
    "$BRANCH_NAME"
else
  git push --set-upstream "$REMOTE" "$BRANCH_NAME"
fi

# --------------------------------------------------
# 完了メッセージ
# --------------------------------------------------

echo
echo "Created and checked out:"
echo "  $BRANCH_NAME"
echo
echo "Base branch:"
echo "  $BASE_BRANCH"
echo
echo "Linked Issue:"
echo "  #$ISSUE_NUMBER"
