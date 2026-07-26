#!/usr/bin/env bash

# エラー発生時に即終了し、未定義変数やパイプラインのエラーも検知する
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROMPT_TEMPLATE="$SCRIPT_DIR/../docs/prompts/pull-request-draft-prompt-template.md"

# shellcheck source=scripts/lib/branch-name.sh
source "$SCRIPT_DIR/lib/branch-name.sh"
# shellcheck source=scripts/lib/pull-request-draft.sh
source "$SCRIPT_DIR/lib/pull-request-draft.sh"

DEFAULT_BASE_BRANCH="dev"
REMOTE="origin"

usage() {
  cat <<EOF
Usage: $0 <issue-number> [--base <branch>]

Generate and create a GitHub Pull Request using Codex CLI.

Options:
  --base <branch>  Set the base branch (default: dev).
  --help           Show this help message.
EOF
}

error() {
  printf 'Error: %s\n' "$1" >&2
}

ISSUE_NUMBER=""
BASE_BRANCH="$DEFAULT_BASE_BRANCH"
BODY_FILE=""

cleanup() {
  if [[ -n "$BODY_FILE" && -f "$BODY_FILE" ]]; then
    rm -f -- "$BODY_FILE"
  fi
}
trap cleanup EXIT

while [[ $# -gt 0 ]]; do
  case "$1" in
    --base)
      option="$1"
      if [[ $# -lt 2 || -z "$2" ]]; then
        error "Option '$option' requires a value."
        usage >&2
        exit 1
      fi

      BASE_BRANCH="$2"
      shift 2
      ;;
    --help)
      usage
      exit 0
      ;;
    --*)
      error "Unknown option: $1"
      usage >&2
      exit 1
      ;;
    *)
      if [[ -n "$ISSUE_NUMBER" ]]; then
        error "Only one Issue number may be specified."
        usage >&2
        exit 1
      fi
      ISSUE_NUMBER="$1"
      shift
      ;;
  esac
done

if [[ -z "$ISSUE_NUMBER" ]]; then
  error "An Issue number is required."
  usage >&2
  exit 1
fi

if [[ ! "$ISSUE_NUMBER" =~ ^[1-9][0-9]*$ ]]; then
  error "Invalid Issue number: $ISSUE_NUMBER"
  exit 1
fi

for required_command in git gh codex; do
  if ! command -v "$required_command" >/dev/null 2>&1; then
    error "Required command is not installed: $required_command"
    exit 1
  fi
done

if ! git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  error "Not inside a Git repository."
  exit 1
fi

if [[ ! -f "$PROMPT_TEMPLATE" ]]; then
  error "Pull Request Draft Prompt Template was not found: $PROMPT_TEMPLATE"
  exit 1
fi

if [[ -n "$(git status --porcelain)" ]]; then
  error "Working tree has uncommitted changes. Commit or stash them before creating a Pull Request."
  exit 1
fi

HEAD_BRANCH="$(git branch --show-current)"

if [[ -z "$HEAD_BRANCH" ]]; then
  error "Unable to resolve the head branch from a detached HEAD."
  exit 1
fi

if ! is_valid_branch_name "$BASE_BRANCH"; then
  error "Invalid base branch: $BASE_BRANCH"
  exit 1
fi

if ! is_valid_branch_name "$HEAD_BRANCH"; then
  error "Invalid head branch: $HEAD_BRANCH"
  exit 1
fi

if [[ "$HEAD_BRANCH" == "main" || "$HEAD_BRANCH" == "dev" ]]; then
  error "Pull Requests cannot be created from protected branch '$HEAD_BRANCH'."
  exit 1
fi

if [[ "$BASE_BRANCH" == "$HEAD_BRANCH" ]]; then
  error "Base and head branches must be different."
  exit 1
fi

if ! git rev-parse --verify "$BASE_BRANCH" >/dev/null 2>&1; then
  error "Local base branch '$BASE_BRANCH' was not found."
  exit 1
fi

if ! REMOTE_BASE_SHA="$(git ls-remote --exit-code --heads "$REMOTE" "$BASE_BRANCH" | awk 'NR == 1 { print $1 }')" ||
  [[ -z "$REMOTE_BASE_SHA" ]]; then
  error "Base branch '$REMOTE/$BASE_BRANCH' was not found."
  exit 1
fi

if ! git fetch --quiet "$REMOTE" "$BASE_BRANCH"; then
  error "Failed to fetch base branch '$REMOTE/$BASE_BRANCH'."
  exit 1
fi

if ! REMOTE_HEAD_SHA="$(git ls-remote --exit-code --heads "$REMOTE" "$HEAD_BRANCH" | awk 'NR == 1 { print $1 }')" ||
  [[ -z "$REMOTE_HEAD_SHA" ]]; then
  error "Head branch '$REMOTE/$HEAD_BRANCH' was not found. Push it before creating a Pull Request."
  exit 1
fi

LOCAL_HEAD_SHA="$(git rev-parse HEAD)"
if [[ "$LOCAL_HEAD_SHA" != "$REMOTE_HEAD_SHA" ]]; then
  error "Local HEAD does not match '$REMOTE/$HEAD_BRANCH'. Push all commits before creating a Pull Request."
  exit 1
fi

if ! gh auth status >/dev/null 2>&1; then
  error "GitHub CLI is not authenticated. Run 'gh auth login' first."
  exit 1
fi

if ! ISSUE_CONTEXT="$(gh issue view "$ISSUE_NUMBER" --json number,title,body,url 2>/dev/null)"; then
  error "Issue #$ISSUE_NUMBER was not found or could not be retrieved."
  exit 1
fi

if ! EXISTING_PR_COUNT="$(gh pr list --head "$HEAD_BRANCH" --state open --json number --jq 'length' 2>/dev/null)"; then
  error "Failed to check for an existing Pull Request."
  exit 1
fi

if [[ ! "$EXISTING_PR_COUNT" =~ ^[0-9]+$ ]]; then
  error "GitHub CLI returned an invalid Pull Request count."
  exit 1
fi

if ((EXISTING_PR_COUNT > 0)); then
  error "An open Pull Request already exists for head branch '$HEAD_BRANCH'."
  exit 1
fi

CHANGED_FILES="$(git diff --name-status FETCH_HEAD...HEAD)"
GIT_DIFF="$(git diff --no-ext-diff FETCH_HEAD...HEAD)"
COMMIT_HISTORY="$(git log --format='%h %s' FETCH_HEAD..HEAD)"
PROMPT_CONTENT="$(<"$PROMPT_TEMPLATE")"

CODEX_INPUT="$(printf '%s\n' \
  "$PROMPT_CONTENT" \
  '' \
  '---' \
  '' \
  '# Runtime Instructions' \
  '' \
  '- Generate the Pull Request content now from the context below.' \
  '- Do not create, modify, or save any file.' \
  '- Return only the completed Markdown content to standard output.' \
  '- Follow the loaded Pull Request Draft Prompt Template exactly as the single source of truth.' \
  '- Replace every template placeholder with verified context.' \
  '- Do not include code fences or explanations around the result.' \
  '- Describe only changes shown in the Git diff.' \
  '- Report only verification information explicitly confirmed below.' \
  '' \
  '## Issue ID' \
  "$ISSUE_NUMBER" \
  '' \
  '## GitHub Issue' \
  "$ISSUE_CONTEXT" \
  '' \
  '## Base Branch' \
  "$BASE_BRANCH" \
  '' \
  '## Current Branch' \
  "$HEAD_BRANCH" \
  '' \
  '## Changed Files' \
  "${CHANGED_FILES:-No changed files detected.}" \
  '' \
  '## Git Diff' \
  "${GIT_DIFF:-No diff detected.}" \
  '' \
  '## Commit History' \
  "${COMMIT_HISTORY:-No commits detected.}" \
  '' \
  '## Automatically Collected Verification Results' \
  'Working tree is clean. Local HEAD matches the remote head branch.')"

echo "Generating Pull Request content with Codex CLI..."
if ! GENERATED_PULL_REQUEST="$(printf '%s\n' "$CODEX_INPUT" | codex \
  --ask-for-approval never \
  exec \
  --ephemeral \
  --sandbox read-only \
  --color never \
  - 2>/dev/null)"; then
  error "Codex CLI failed to generate Pull Request content."
  exit 1
fi

if ! validate_pull_request_output "$PROMPT_TEMPLATE" "$GENERATED_PULL_REQUEST"; then
  error "Codex CLI output does not conform to the Pull Request Draft Prompt Template."
  exit 1
fi

PR_TITLE="$(printf '%s\n' "$GENERATED_PULL_REQUEST" | extract_pull_request_title)"
PR_BODY="$(printf '%s\n' "$GENERATED_PULL_REQUEST" | extract_pull_request_body)"

if [[ -z "$PR_TITLE" ]]; then
  error "Codex CLI output must contain a non-empty '# Title' section."
  exit 1
fi

if ! has_non_whitespace_content "$PR_BODY"; then
  error "Codex CLI output must contain a non-empty body after the title."
  exit 1
fi

echo
echo "Pull Request preview:"
echo "  Title: $PR_TITLE"
echo "  Base: $BASE_BRANCH"
echo "  Head: $HEAD_BRANCH"
echo
echo "Body:"
printf '%s\n' "$PR_BODY"
echo
printf 'Create this Pull Request? [y/N] '

if ! IFS= read -r confirmation || [[ ! "$confirmation" =~ ^[Yy]$ ]]; then
  echo
  echo "Pull Request creation canceled."
  exit 0
fi

BODY_FILE="$(mktemp)"
printf '%s\n' "$PR_BODY" >"$BODY_FILE"

if ! PR_URL="$(gh pr create \
  --base "$BASE_BRANCH" \
  --head "$HEAD_BRANCH" \
  --title "$PR_TITLE" \
  --body-file "$BODY_FILE")"; then
  error "Failed to create the Pull Request."
  exit 1
fi

echo
echo "Pull Request created:"
echo "$PR_URL"
