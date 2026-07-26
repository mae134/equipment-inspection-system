#!/usr/bin/env bash

# 検証済みのブランチ名候補を1つ生成する
# 親プロセスを終了せず、生成に成功した場合のみ標準出力へ候補を出力する
generate_branch_name_suggestion() {
  local issue_number="${1:-}"
  local issue_title="${2:-}"
  local prompt
  local candidate

  if ! command -v codex >/dev/null 2>&1; then
    printf 'Codex CLI is not installed.\n' >&2
    return 1
  fi

  prompt="$(printf '%s\n' \
    'Generate exactly one Git branch name for the following GitHub Issue.' \
    '' \
    'Rules:' \
    '- Output only the branch name.' \
    '- Do not output Markdown, quotes, code fences, or explanations.' \
    '- Use lowercase ASCII letters, numbers, hyphens, and exactly one slash.' \
    "- Use the format <type>/${issue_number}-<short-description>." \
    "- Include Issue number ${issue_number}." \
    '- Allowed types: feature, fix, chore, docs, refactor, test.' \
    '- Keep the English description concise.' \
    '' \
    'Issue title:' \
    "$issue_title")"

  if ! candidate="$(printf '%s\n' "$prompt" | codex \
    --ask-for-approval never \
    exec \
    --ephemeral \
    --sandbox read-only \
    --color never \
    - 2>/dev/null)"; then
    printf 'Codex CLI could not generate a branch name.\n' >&2
    return 1
  fi

  if [[ ! "$candidate" =~ ^(feature|fix|chore|docs|refactor|test)/${issue_number}-[a-z0-9]+(-[a-z0-9]+)*$ ]] ||
    ! git check-ref-format --branch "$candidate" >/dev/null 2>&1; then
    printf 'Codex CLI returned an invalid branch-name suggestion.\n' >&2
    return 1
  fi

  printf '%s\n' "$candidate"
}
