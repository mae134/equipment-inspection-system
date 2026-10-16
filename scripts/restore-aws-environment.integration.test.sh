#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RESTORE_SCRIPT="${SCRIPT_DIR}/restore-aws-environment.sh"
MOCK_AWS="${SCRIPT_DIR}/test-helpers/mock-aws.sh"

TEST_DIR="$(mktemp -d)"
trap 'rm -rf "$TEST_DIR"' EXIT

mkdir -p "${TEST_DIR}/bin"
ln -s "$MOCK_AWS" "${TEST_DIR}/bin/aws"

export PATH="${TEST_DIR}/bin:${PATH}"
export MOCK_LOG="${TEST_DIR}/aws.log"

SNAPSHOT_ID="equipment-inspection-test-snapshot"

run_test() {
  local name="$1"
  local expected_result="$2"
  shift 2

  : >"$MOCK_LOG"

  local output
  local status=0

  output="$(env "$@" bash "$RESTORE_SCRIPT" --snapshot-id "$SNAPSHOT_ID" 2>&1)" || status=$?

  if [[ "$expected_result" == "success" && "$status" -ne 0 ]]; then
    echo "FAIL: $name"
    echo "$output"
    exit 1
  fi

  if [[ "$expected_result" == "failure" && "$status" -eq 0 ]]; then
    echo "FAIL: $name (expected failure)"
    echo "$output"
    exit 1
  fi

  if [[ "$expected_result" == "success" ]] &&
    ! grep -q "Preflight checks completed" <<<"$output"; then
    echo "FAIL: $name (missing completion message)"
    echo "$output"
    exit 1
  fi

  if [[ "$expected_result" == "success" ]] &&
    ! grep -qx "SNAPSHOT_DESCRIBED" "$MOCK_LOG"; then
    echo "FAIL: $name (snapshot was not checked)"
    exit 1
  fi

  echo "PASS: $name"
}

# 1. 正常系
run_test \
  "Valid AWS identity and snapshot" \
  success

# 2. AWSアカウント不一致
run_test \
  "Unexpected AWS account was rejected" \
  failure \
  'MOCK_CALLER_IDENTITY={"Account":"000000000000","Arn":"arn:aws:sts::000000000000:assumed-role/terraform-execution-role/test"}'

# 3. IAMロール不一致
run_test \
  "Unexpected IAM role was rejected" \
  failure \
  'MOCK_CALLER_IDENTITY={"Account":"339741260090","Arn":"arn:aws:sts::339741260090:assumed-role/unexpected-role/test"}'

# 4. スナップショットがavailable以外
run_test \
  "Unavailable snapshot was rejected" \
  failure \
  'MOCK_RESTORE_SNAPSHOT_STATUS=creating'

# 5. スナップショットの元DBが違う
run_test \
  "Snapshot from another database was rejected" \
  failure \
  'MOCK_RESTORE_SOURCE_DB=another-database'

run_mode_test() {
  local name="$1"
  local expected_result="$2"
  local expected_message="$3"
  local expected_aws_calls="$4"
  shift 4

  : >"$MOCK_LOG"

  local output
  local status=0

  output="$(bash "$RESTORE_SCRIPT" "$@" 2>&1)" || status=$?

  if [[ "$expected_result" == "success" && "$status" -ne 0 ]] ||
    [[ "$expected_result" == "failure" && "$status" -eq 0 ]]; then
    echo "FAIL: $name (exit status: $status)"
    echo "$output"
    exit 1
  fi

  if ! grep -Fq -- "$expected_message" <<<"$output"; then
    echo "FAIL: $name (unexpected output)"
    echo "$output"
    exit 1
  fi

  if [[ "$expected_aws_calls" == "none" && -s "$MOCK_LOG" ]]; then
    echo "FAIL: $name (unexpected AWS calls)"
    cat "$MOCK_LOG"
    exit 1
  fi

  if [[ "$expected_aws_calls" == "no_snapshot" ]] &&
    grep -q "SNAPSHOT_DESCRIBED" "$MOCK_LOG"; then
    echo "FAIL: $name (snapshot lookup was not expected)"
    exit 1
  fi

  echo "PASS: $name"
}

# 6. 新規作成モード
run_mode_test \
  "New database mode" \
  success \
  "New RDS creation mode selected." \
  no_snapshot \
  --new

# 7. オプションなし
run_mode_test \
  "Missing mode was rejected" \
  failure \
  "ERROR: Specify either --new or --snapshot-id." \
  none

# 8. 排他的なオプションの同時指定
run_mode_test \
  "Conflicting modes were rejected" \
  failure \
  "ERROR: --new and --snapshot-id cannot be used together." \
  none \
  --new --snapshot-id "$SNAPSHOT_ID"

echo "All restore integration tests passed."
