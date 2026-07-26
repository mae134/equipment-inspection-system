#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCRIPT_UNDER_TEST="$SCRIPT_DIR/create-issue-branch.sh"
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

  grep -F -- "$expected" "$TRACE_TEST_LOG" >/dev/null ||
    fail "expected command log to contain: $expected"
}

assert_log_excludes() {
  local unexpected="$1"

  if grep -F -- "$unexpected" "$TRACE_TEST_LOG" >/dev/null; then
    fail "expected command log not to contain: $unexpected"
  fi
}

setup_case() {
  CASE_DIR="$TEST_ROOT/case-$TEST_COUNT"
  MOCK_BIN="$CASE_DIR/bin"
  TRACE_TEST_LOG="$CASE_DIR/commands.log"
  TRACE_TEST_BRANCH_CREATED="$CASE_DIR/branch-created"
  TRACE_TEST_CODEX_CALLED="$CASE_DIR/codex-called"
  mkdir -p "$MOCK_BIN"
  : >"$TRACE_TEST_LOG"

  export TRACE_TEST_LOG TRACE_TEST_BRANCH_CREATED TRACE_TEST_CODEX_CALLED

  cat >"$MOCK_BIN/git" <<'EOF'
#!/usr/bin/env bash
set -u
printf 'git %s\n' "$*" >>"$TRACE_TEST_LOG"

case "${1:-}" in
  rev-parse | fetch | switch | pull | branch | push)
    exit 0
    ;;
  status)
    exit 0
    ;;
  check-ref-format)
    if /usr/bin/git check-ref-format --branch "${3:-}" >/dev/null 2>&1; then
      exit 0
    fi
    exit 1
    ;;
  show-ref)
    case "${4:-}" in
      refs/remotes/origin/dev | refs/heads/dev) exit 0 ;;
      *) exit 1 ;;
    esac
    ;;
  ls-remote)
    if [[ -f "$TRACE_TEST_BRANCH_CREATED" ]]; then
      exit 0
    fi
    exit 1
    ;;
esac

exit 0
EOF

  cat >"$MOCK_BIN/gh" <<'EOF'
#!/usr/bin/env bash
set -u
printf 'gh %s\n' "$*" >>"$TRACE_TEST_LOG"

if [[ "${1:-} ${2:-}" == "issue view" ]]; then
  [[ "${3:-}" == "41" ]] || exit 1
  printf '%s\n' 'Issueからブランチを対話的に作成できるようにする'
  exit 0
fi

if [[ "${1:-} ${2:-}" == "issue develop" ]]; then
  touch "$TRACE_TEST_BRANCH_CREATED"
  exit 0
fi

exit 1
EOF

  chmod +x "$MOCK_BIN/git" "$MOCK_BIN/gh"
}

install_fake_codex() {
  cat >"$MOCK_BIN/codex" <<'EOF'
#!/usr/bin/env bash
set -u
touch "$TRACE_TEST_CODEX_CALLED"
while IFS= read -r _line; do :; done

case "${TRACE_TEST_CODEX_BEHAVIOR:-success}" in
  success) printf '%s\n' 'chore/41-interactive-issue-branch' ;;
  custom-suggestion) printf '%s\n' 'feature/41-generated-name' ;;
  failure) exit 2 ;;
  empty) : ;;
  invalid) printf '%s\n' 'Suggested: chore/41-invalid-output' ;;
esac
EOF
  chmod +x "$MOCK_BIN/codex"
}

run_cli() {
  local input="$1"
  shift

  set +e
  CLI_OUTPUT="$(printf '%s' "$input" | PATH="$MOCK_BIN:/usr/bin:/bin" bash "$SCRIPT_UNDER_TEST" "$@" 2>&1)"
  CLI_STATUS=$?
  set -e
}

begin_test() {
  TEST_COUNT=$((TEST_COUNT + 1))
  setup_case
}

# AI succeeds and Enter accepts the default suggestion.
begin_test
install_fake_codex
run_cli $'\n' 41
[[ $CLI_STATUS -eq 0 ]] || fail "AI default flow exited with $CLI_STATUS"
assert_contains "$CLI_OUTPUT" "Branch name [chore/41-interactive-issue-branch]:"
assert_log_contains "gh issue develop 41 --base dev --name chore/41-interactive-issue-branch --checkout"

# AI succeeds and custom input replaces the suggestion.
begin_test
install_fake_codex
run_cli $'feature/41-custom-name\n' 41
[[ $CLI_STATUS -eq 0 ]] || fail "custom interactive flow exited with $CLI_STATUS"
assert_log_contains "gh issue develop 41 --base dev --name feature/41-custom-name --checkout"

# Missing Codex falls back to required manual input.
begin_test
run_cli $'chore/41-manual-name\n' 41
[[ $CLI_STATUS -eq 0 ]] || fail "missing Codex fallback exited with $CLI_STATUS"
assert_contains "$CLI_OUTPUT" "AI branch-name generation is unavailable."
assert_contains "$CLI_OUTPUT" "Branch name:"
assert_log_contains "gh issue develop 41 --base dev --name chore/41-manual-name --checkout"

# Non-zero Codex status falls back to manual input.
begin_test
install_fake_codex
export TRACE_TEST_CODEX_BEHAVIOR=failure
run_cli $'chore/41-after-failure\n' 41
unset TRACE_TEST_CODEX_BEHAVIOR
[[ $CLI_STATUS -eq 0 ]] || fail "Codex failure fallback exited with $CLI_STATUS"
assert_contains "$CLI_OUTPUT" "AI branch-name generation is unavailable."
assert_log_contains "gh issue develop 41 --base dev --name chore/41-after-failure --checkout"

# Empty Codex output falls back to manual input.
begin_test
install_fake_codex
export TRACE_TEST_CODEX_BEHAVIOR=empty
run_cli $'chore/41-after-empty\n' 41
unset TRACE_TEST_CODEX_BEHAVIOR
[[ $CLI_STATUS -eq 0 ]] || fail "empty Codex fallback exited with $CLI_STATUS"
assert_contains "$CLI_OUTPUT" "AI branch-name generation is unavailable."

# Invalid Codex output is never offered as a default.
begin_test
install_fake_codex
export TRACE_TEST_CODEX_BEHAVIOR=invalid
run_cli $'chore/41-after-invalid\n' 41
unset TRACE_TEST_CODEX_BEHAVIOR
[[ $CLI_STATUS -eq 0 ]] || fail "invalid Codex fallback exited with $CLI_STATUS"
assert_contains "$CLI_OUTPUT" "Branch name:"
[[ "$CLI_OUTPUT" != *"Branch name [Suggested:"* ]] || fail "invalid suggestion was offered"

# Explicit branch names remain non-interactive and never call Codex.
begin_test
install_fake_codex
run_cli '' 41 chore/41-explicit-name
[[ $CLI_STATUS -eq 0 ]] || fail "explicit flow exited with $CLI_STATUS"
[[ ! -e "$TRACE_TEST_CODEX_CALLED" ]] || fail "explicit flow called Codex"
assert_log_contains "gh issue develop 41 --base dev --name chore/41-explicit-name --checkout"

# Invalid final user input preserves a failing validation path.
begin_test
run_cli $'bad..branch\n' 41
[[ $CLI_STATUS -ne 0 ]] || fail "invalid branch name unexpectedly succeeded"
assert_contains "$CLI_OUTPUT" "Error: Invalid branch name: bad..branch"
assert_log_excludes "gh issue develop"

# Empty manual input is rejected and retried.
begin_test
run_cli $'\nchore/41-retried-manual\n' 41
[[ $CLI_STATUS -eq 0 ]] || fail "manual retry flow exited with $CLI_STATUS"
assert_contains "$CLI_OUTPUT" "Error: Branch name is required."
assert_log_contains "gh issue develop 41 --base dev --name chore/41-retried-manual --checkout"

# Invalid Issue retrieval stops before branch selection or creation.
begin_test
install_fake_codex
run_cli '' 999 chore/999-missing
[[ $CLI_STATUS -ne 0 ]] || fail "missing Issue unexpectedly succeeded"
assert_contains "$CLI_OUTPUT" "Error: Issue #999 was not found or GitHub CLI authentication failed."
assert_log_excludes "gh issue develop"
[[ ! -e "$TRACE_TEST_CODEX_CALLED" ]] || fail "missing Issue flow called Codex"

printf 'PASS: %d create-issue-branch tests\n' "$TEST_COUNT"
