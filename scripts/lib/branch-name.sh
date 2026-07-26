#!/usr/bin/env bash

# 値が有効なGitブランチ名の場合は成功を返す
is_valid_branch_name() {
  local candidate="${1:-}"

  [[ -n "$candidate" ]] && git check-ref-format --branch "$candidate" >/dev/null 2>&1
}

# 選択されたブランチ名を標準出力へ出力する
# 呼び出し元がコマンド置換で安全に値を取得できるよう、プロンプトとエラーは標準エラー出力へ出力する
prompt_for_branch_name() {
  local suggested_branch_name="${1:-}"
  local entered_branch_name

  while true; do
    if [[ -n "$suggested_branch_name" ]]; then
      printf 'Branch name [%s]: ' "$suggested_branch_name" >&2
    else
      printf 'Branch name: ' >&2
    fi

    if ! IFS= read -r entered_branch_name; then
      printf '\nError: Unable to read a branch name.\n' >&2
      return 1
    fi

    if [[ -n "$entered_branch_name" ]]; then
      printf '%s\n' "$entered_branch_name"
      return 0
    fi

    if [[ -n "$suggested_branch_name" ]]; then
      printf '%s\n' "$suggested_branch_name"
      return 0
    fi

    printf 'Error: Branch name is required.\n' >&2
  done
}

# ユーザーが最終的に選択したブランチ名を検証し、CLI向けのエラーを表示する
validate_branch_name() {
  local branch_name="${1:-}"

  if ! is_valid_branch_name "$branch_name"; then
    printf 'Error: Invalid branch name: %s\n' "$branch_name" >&2
    return 1
  fi
}
