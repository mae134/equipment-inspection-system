#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCRIPT_UNDER_TEST="$SCRIPT_DIR/create-pull-request.sh"
TEST_ROOT="$(mktemp -d)"
TEST_COUNT=0

cleanup() {
  rm -rf "$TEST_ROOT"
}
trap cleanup EXIT

fail() {
  printf 'FAIL: %s\n' "$1" >&2
  exit 1
}

assert_contains() {
  local actual="$1"
  local expected="$2"

  [[ "$actual" == *"$expected"* ]] || fail "expected output to contain: $expected"
}

assert_log_contains() {
  local expected="$1"

  grep -F -- "$expected" "$TEST_COMMAND_LOG" >/dev/null ||
    fail "expected command log to contain: $expected"
}

assert_log_excludes() {
  local unexpected="$1"

  if grep -F -- "$unexpected" "$TEST_COMMAND_LOG" >/dev/null; then
    fail "expected command log not to contain: $unexpected"
  fi
}

setup_case() {
  TEST_COUNT=$((TEST_COUNT + 1))
  CASE_DIR="$TEST_ROOT/case-$TEST_COUNT"
  MOCK_BIN="$CASE_DIR/bin"
  TEST_COMMAND_LOG="$CASE_DIR/commands.log"
  TEST_CODEX_INPUT="$CASE_DIR/codex-input.md"
  TEST_CAPTURED_BODY="$CASE_DIR/captured-body.md"
  TEST_BODY_PATH_RECORD="$CASE_DIR/body-path"
  TEST_HEAD_BRANCH="feature/60-create-pr-from-draft"
  TEST_LOCAL_HEAD_SHA="aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
  TEST_REMOTE_HEAD_SHA="$TEST_LOCAL_HEAD_SHA"
  TEST_REMOTE_BASE_SHA="bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
  TEST_WORKTREE_STATUS=""
  TEST_AUTH_STATUS="success"
  TEST_ISSUE_STATUS="success"
  TEST_DUPLICATE_COUNT="0"
  TEST_CODEX_STATUS="success"
  TEST_CREATE_STATUS="success"
  mkdir -p "$MOCK_BIN"
  : >"$TEST_COMMAND_LOG"

  export TEST_COMMAND_LOG TEST_CODEX_INPUT TEST_CAPTURED_BODY TEST_BODY_PATH_RECORD
  export TEST_HEAD_BRANCH TEST_LOCAL_HEAD_SHA TEST_REMOTE_HEAD_SHA TEST_REMOTE_BASE_SHA
  export TEST_WORKTREE_STATUS TEST_AUTH_STATUS TEST_ISSUE_STATUS TEST_DUPLICATE_COUNT
  export TEST_CODEX_STATUS TEST_CREATE_STATUS

  cat >"$MOCK_BIN/git" <<'EOF'
#!/bin/bash
set -u
printf 'git %s\n' "$*" >>"$TEST_COMMAND_LOG"

case "${1:-} ${2:-}" in
  "rev-parse --is-inside-work-tree") exit 0 ;;
  "rev-parse --verify") exit 0 ;;
  "rev-parse HEAD") printf '%s\n' "$TEST_LOCAL_HEAD_SHA" ;;
  "status --porcelain") printf '%s' "$TEST_WORKTREE_STATUS" ;;
  "branch --show-current") printf '%s\n' "$TEST_HEAD_BRANCH" ;;
  "fetch --quiet") exit 0 ;;
  "check-ref-format --branch")
    /usr/bin/git check-ref-format --branch "${3:-}" >/dev/null 2>&1
    ;;
  "ls-remote --exit-code")
    if [[ "${5:-}" == "$TEST_HEAD_BRANCH" ]]; then
      printf '%s\trefs/heads/%s\n' "$TEST_REMOTE_HEAD_SHA" "${5:-}"
    else
      printf '%s\trefs/heads/%s\n' "$TEST_REMOTE_BASE_SHA" "${5:-}"
    fi
    ;;
  "diff --name-status")
    printf '%s\n' 'M README.md' 'A scripts/create-pull-request.sh'
    ;;
  "diff --no-ext-diff")
    printf '%s\n' 'diff --git a/README.md b/README.md' '+generated workflow'
    ;;
  "log --format=%h %s")
    printf '%s\n' 'abc1234 feat: add pull request generation'
    ;;
  *) exit 1 ;;
esac
EOF

  cat >"$MOCK_BIN/gh" <<'EOF'
#!/bin/bash
set -u
printf 'gh %s\n' "$*" >>"$TEST_COMMAND_LOG"

if [[ "${1:-} ${2:-}" == "auth status" ]]; then
  [[ "$TEST_AUTH_STATUS" == "success" ]]
  exit
fi

if [[ "${1:-} ${2:-}" == "issue view" ]]; then
  [[ "$TEST_ISSUE_STATUS" == "success" ]] || exit 1
  printf '%s\n' '{"number":60,"title":"PRを自動生成する","body":"Issue body","url":"https://example.invalid/issues/60"}'
  exit 0
fi

if [[ "${1:-} ${2:-}" == "pr list" ]]; then
  printf '%s\n' "$TEST_DUPLICATE_COUNT"
  exit 0
fi

if [[ "${1:-} ${2:-}" == "pr create" ]]; then
  body_file=""
  shift 2
  while [[ $# -gt 0 ]]; do
    if [[ "$1" == "--body-file" ]]; then
      body_file="$2"
      break
    fi
    shift
  done
  [[ -n "$body_file" ]] || exit 2
  printf '%s\n' "$body_file" >"$TEST_BODY_PATH_RECORD"
  cp "$body_file" "$TEST_CAPTURED_BODY"
  [[ "$TEST_CREATE_STATUS" == "success" ]] || exit 2
  printf '%s\n' 'https://github.com/example/trace-app/pull/60'
  exit 0
fi

exit 1
EOF

  cat >"$MOCK_BIN/codex" <<'EOF'
#!/bin/bash
set -u
cat >"$TEST_CODEX_INPUT"
printf 'codex %s\n' "$*" >>"$TEST_COMMAND_LOG"

case "$TEST_CODEX_STATUS" in
  success)
    printf '%s\n' \
      '# Title' \
      '' \
      'feat: Pull Request自動生成を追加' \
      '' \
      '## Summary' \
      '' \
      'Codex CLIでPRを生成します。' \
      '' \
      '## Changes' \
      '' \
      '- PR生成スクリプトを更新' \
      '' \
      '## Verification' \
      '' \
      '- テスト成功'
    ;;
  failure) exit 2 ;;
  invalid) printf '%s\n' 'invalid output' ;;
  missing-section)
    printf '%s\n' \
      '# Title' \
      '' \
      'feat: セクション不足' \
      '' \
      '## Summary' \
      '' \
      'Summary only.' \
      '' \
      '## Changes' \
      '' \
      '- Change only.'
    ;;
  prefixed)
    printf '%s\n' \
      'Generated Pull Request:' \
      '# Title' \
      '' \
      'feat: 不正な前置き' \
      '' \
      '## Summary' \
      '' \
      'Summary.' \
      '' \
      '## Changes' \
      '' \
      '- Change.' \
      '' \
      '## Verification' \
      '' \
      '- Verified.'
    ;;
esac
EOF

  chmod +x "$MOCK_BIN/git" "$MOCK_BIN/gh" "$MOCK_BIN/codex"
}

run_cli() {
  local input="$1"
  shift

  set +e
  CLI_OUTPUT="$(printf '%s' "$input" | PATH="$MOCK_BIN:/usr/bin:/bin" /bin/bash "$SCRIPT_UNDER_TEST" "$@" 2>&1)"
  CLI_STATUS=$?
  set -e
}

# Normal execution generates content from collected context and creates the PR.
setup_case
run_cli $'y\n' 60
[[ $CLI_STATUS -eq 0 ]] || fail "normal execution exited with $CLI_STATUS"
assert_contains "$CLI_OUTPUT" "Generating Pull Request content with Codex CLI..."
assert_contains "$CLI_OUTPUT" "Title: feat: Pull Request自動生成を追加"
assert_contains "$CLI_OUTPUT" "https://github.com/example/trace-app/pull/60"
assert_log_contains "gh pr create --base dev --head feature/60-create-pr-from-draft --title feat: Pull Request自動生成を追加 --body-file"
assert_log_contains "codex --ask-for-approval never exec --ephemeral --sandbox read-only --color never -"
assert_log_contains "git fetch --quiet origin dev"
assert_log_contains "git branch --show-current"
assert_log_contains "git diff --name-status FETCH_HEAD...HEAD"
assert_log_contains "git diff --no-ext-diff FETCH_HEAD...HEAD"
assert_log_contains "git log --format=%h %s FETCH_HEAD..HEAD"
assert_contains "$(cat "$TEST_CODEX_INPUT")" "# Pull Request Draft Generation Prompt"
assert_contains "$(cat "$TEST_CODEX_INPUT")" "Generate the Pull Request using the Markdown template below."
assert_contains "$(cat "$TEST_CODEX_INPUT")" '"number":60'
assert_contains "$(cat "$TEST_CODEX_INPUT")" $'## Current Branch\nfeature/60-create-pr-from-draft'
assert_contains "$(cat "$TEST_CODEX_INPUT")" "M README.md"
assert_contains "$(cat "$TEST_CODEX_INPUT")" "diff --git a/README.md b/README.md"
assert_contains "$(cat "$TEST_CODEX_INPUT")" "abc1234 feat: add pull request generation"
assert_contains "$(cat "$TEST_CAPTURED_BODY")" "## Summary"
body_path="$(cat "$TEST_BODY_PATH_RECORD")"
[[ ! -e "$body_path" ]] || fail "temporary body file was not removed"

# Base override is used while head remains the current branch.
setup_case
run_cli $'Y\n' 60 --base release
[[ $CLI_STATUS -eq 0 ]] || fail "override flow exited with $CLI_STATUS"
assert_contains "$CLI_OUTPUT" "Base: release"
assert_contains "$CLI_OUTPUT" "Head: feature/60-create-pr-from-draft"
assert_log_contains "gh pr create --base release --head feature/60-create-pr-from-draft"

# --head is not accepted, so another branch can never be selected manually.
setup_case
run_cli '' 60 --head feature/other
[[ $CLI_STATUS -ne 0 ]] || fail "--head unexpectedly succeeded"
assert_contains "$CLI_OUTPUT" "Unknown option: --head"
assert_log_excludes "gh auth status"

# A dirty working tree fails before GitHub or Codex operations.
setup_case
export TEST_WORKTREE_STATUS=' M README.md'
run_cli '' 60
[[ $CLI_STATUS -ne 0 ]] || fail "dirty worktree unexpectedly succeeded"
assert_contains "$CLI_OUTPUT" "Working tree has uncommitted changes."
assert_log_excludes "gh auth status"
[[ ! -e "$TEST_CODEX_INPUT" ]] || fail "dirty worktree called Codex"

# Detached HEAD is rejected because no Pull Request head can be resolved.
setup_case
export TEST_HEAD_BRANCH=""
run_cli '' 60
[[ $CLI_STATUS -ne 0 ]] || fail "detached HEAD unexpectedly succeeded"
assert_contains "$CLI_OUTPUT" "Unable to resolve the head branch from a detached HEAD."
assert_log_excludes "gh auth status"

# An Issue number is required.
setup_case
run_cli ''
[[ $CLI_STATUS -ne 0 ]] || fail "missing Issue number unexpectedly succeeded"
assert_contains "$CLI_OUTPUT" "An Issue number is required."
assert_log_excludes "gh auth status"

# Missing GitHub CLI is reported before repository operations.
setup_case
mv "$MOCK_BIN/gh" "$CASE_DIR/gh-disabled"
ln -s /usr/bin/dirname "$MOCK_BIN/dirname"
set +e
CLI_OUTPUT="$(PATH="$MOCK_BIN" /bin/bash "$SCRIPT_UNDER_TEST" 60 2>&1)"
CLI_STATUS=$?
set -e
[[ $CLI_STATUS -ne 0 ]] || fail "missing GitHub CLI unexpectedly succeeded"
assert_contains "$CLI_OUTPUT" "Required command is not installed: gh"

# Missing Codex CLI is reported clearly.
setup_case
mv "$MOCK_BIN/codex" "$CASE_DIR/codex-disabled"
run_cli '' 60
[[ $CLI_STATUS -ne 0 ]] || fail "missing Codex unexpectedly succeeded"
assert_contains "$CLI_OUTPUT" "Required command is not installed: codex"
assert_log_excludes "gh auth status"

# Codex generation failure never calls GitHub PR creation.
setup_case
export TEST_CODEX_STATUS=failure
run_cli '' 60
[[ $CLI_STATUS -ne 0 ]] || fail "Codex failure unexpectedly succeeded"
assert_contains "$CLI_OUTPUT" "Codex CLI failed to generate Pull Request content."
assert_log_excludes "gh pr create"

# Invalid Codex output is rejected.
setup_case
export TEST_CODEX_STATUS=invalid
run_cli '' 60
[[ $CLI_STATUS -ne 0 ]] || fail "invalid Codex output unexpectedly succeeded"
assert_contains "$CLI_OUTPUT" "does not conform to the Pull Request Draft Prompt Template."
assert_log_excludes "gh pr create"

# Output missing a template-required section is rejected.
setup_case
export TEST_CODEX_STATUS=missing-section
run_cli '' 60
[[ $CLI_STATUS -ne 0 ]] || fail "incomplete Codex output unexpectedly succeeded"
assert_contains "$CLI_OUTPUT" "does not conform to the Pull Request Draft Prompt Template."
assert_log_excludes "gh pr create"

# Output with explanatory text outside the template is rejected.
setup_case
export TEST_CODEX_STATUS=prefixed
run_cli '' 60
[[ $CLI_STATUS -ne 0 ]] || fail "prefixed Codex output unexpectedly succeeded"
assert_contains "$CLI_OUTPUT" "does not conform to the Pull Request Draft Prompt Template."
assert_log_excludes "gh pr create"

# Missing GitHub authentication stops before Issue retrieval and Codex.
setup_case
export TEST_AUTH_STATUS=failure
run_cli '' 60
[[ $CLI_STATUS -ne 0 ]] || fail "missing authentication unexpectedly succeeded"
assert_contains "$CLI_OUTPUT" "GitHub CLI is not authenticated."
assert_log_excludes "gh issue view"
[[ ! -e "$TEST_CODEX_INPUT" ]] || fail "missing authentication called Codex"

# A missing Issue stops generation.
setup_case
export TEST_ISSUE_STATUS=failure
run_cli '' 60
[[ $CLI_STATUS -ne 0 ]] || fail "missing Issue unexpectedly succeeded"
assert_contains "$CLI_OUTPUT" "Issue #60 was not found or could not be retrieved."
[[ ! -e "$TEST_CODEX_INPUT" ]] || fail "missing Issue called Codex"

# Duplicate Pull Requests are rejected before Codex generation.
setup_case
export TEST_DUPLICATE_COUNT=1
run_cli '' 60
[[ $CLI_STATUS -ne 0 ]] || fail "duplicate PR unexpectedly succeeded"
assert_contains "$CLI_OUTPUT" "An open Pull Request already exists"
[[ ! -e "$TEST_CODEX_INPUT" ]] || fail "duplicate PR called Codex"

# Unpushed commits are rejected.
setup_case
export TEST_REMOTE_HEAD_SHA=cccccccccccccccccccccccccccccccccccccccc
run_cli '' 60
[[ $CLI_STATUS -ne 0 ]] || fail "unpushed HEAD unexpectedly succeeded"
assert_contains "$CLI_OUTPUT" "Push all commits before creating a Pull Request."
assert_log_excludes "gh auth status"

# Human cancellation never creates a PR or a body file.
setup_case
run_cli $'n\n' 60
[[ $CLI_STATUS -eq 0 ]] || fail "cancellation exited with $CLI_STATUS"
assert_contains "$CLI_OUTPUT" "Pull Request creation canceled."
assert_log_excludes "gh pr create"
[[ ! -e "$TEST_BODY_PATH_RECORD" ]] || fail "cancellation created a body file"

# GitHub creation failure removes the temporary body file.
setup_case
export TEST_CREATE_STATUS=failure
run_cli $'y\n' 60
[[ $CLI_STATUS -ne 0 ]] || fail "GitHub creation failure unexpectedly succeeded"
assert_contains "$CLI_OUTPUT" "Failed to create the Pull Request."
body_path="$(cat "$TEST_BODY_PATH_RECORD")"
[[ ! -e "$body_path" ]] || fail "failed creation left the body file"

# Protected heads remain invalid.
setup_case
export TEST_HEAD_BRANCH=dev
run_cli '' 60
[[ $CLI_STATUS -ne 0 ]] || fail "protected head unexpectedly succeeded"
assert_contains "$CLI_OUTPUT" "Pull Requests cannot be created from protected branch 'dev'."
assert_log_excludes "gh auth status"

printf 'PASS: %d create-pull-request tests\n' "$TEST_COUNT"
