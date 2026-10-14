#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCRIPT_FILE="${SCRIPT_DIR}/destroy-aws-environment.sh"

TEST_DIR="$(mktemp -d)"
trap 'rm -rf "$TEST_DIR"' EXIT

MOCK_BIN="${TEST_DIR}/bin"
mkdir -p "$MOCK_BIN"

# AWS CLIのモック
cp "${SCRIPT_DIR}/test-helpers/mock-aws.sh" "${MOCK_BIN}/aws"

# Terraformのモック
cat >"${MOCK_BIN}/terraform" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

case "$*" in
  *"state list"*)
    exit 0
    ;;
  *"plan -destroy"*)
    exit 0
    ;;
  *"show -json"*)
    cat <<'JSON'
{"resource_changes":[{"address":"aws_vpc.main","type":"aws_vpc","change":{"actions":["delete"]}}]}
JSON
    ;;
  *"apply "*)
    echo "APPLY_CALLED" >> "$MOCK_LOG"
    exit 0
    ;;
  *)
    echo "Unexpected Terraform command: $*" >&2
    exit 1
    ;;
esac
EOF

chmod +x "${MOCK_BIN}/aws" "${MOCK_BIN}/terraform"

export MOCK_LOG="${TEST_DIR}/terraform.log"

# 未承認（NO）で実行
actual_status=0

printf 'NO\n' | PATH="${MOCK_BIN}:$PATH" \
  bash "$SCRIPT_FILE" >"${TEST_DIR}/output.log" 2>&1 ||
  actual_status=$?

if [[ "$actual_status" -eq 0 ]]; then
  echo "FAIL: Script unexpectedly succeeded without approval."
  exit 1
fi

if [[ -f "$MOCK_LOG" ]] && grep -q "APPLY_CALLED" "$MOCK_LOG"; then
  echo "FAIL: Terraform apply was called without approval."
  exit 1
fi

if ! grep -q "Destroy cancelled." "${TEST_DIR}/output.log"; then
  echo "FAIL: Confirmation step was not reached."
  cat "${TEST_DIR}/output.log"
  exit 1
fi

echo "PASS: Terraform apply was not called without approval."

# 承認後の正常系テスト（RDS available）
cp "${SCRIPT_DIR}/test-helpers/mock-aws.sh" "${MOCK_BIN}/aws"
chmod +x "${MOCK_BIN}/aws"

export MOCK_RDS_STATUS="available"

# 正常系のログを初期化
: >"$MOCK_LOG"

actual_status=0

printf 'DESTROY\n' | PATH="${MOCK_BIN}:$PATH" \
  bash "$SCRIPT_FILE" >"${TEST_DIR}/approved.log" 2>&1 ||
  actual_status=$?

if [[ "$actual_status" -ne 0 ]]; then
  echo "FAIL: Approved destroy workflow failed."
  cat "${TEST_DIR}/approved.log"
  exit 1
fi

# 実行順序を確認
EXPECTED_LOG=$'RDS_DESCRIBED\nSNAPSHOT_CREATED\nSNAPSHOT_AVAILABLE\nAPPLY_CALLED'
ACTUAL_LOG="$(cat "$MOCK_LOG")"

if [[ "$ACTUAL_LOG" != "$EXPECTED_LOG" ]]; then
  echo "FAIL: Unexpected operation order."
  echo "Expected:"
  printf '%s\n' "$EXPECTED_LOG"
  echo "Actual:"
  printf '%s\n' "$ACTUAL_LOG"
  exit 1
fi

echo "PASS: Snapshot was created and completed before Terraform apply."

# 異常系テスト：スナップショット作成失敗
cp "${SCRIPT_DIR}/test-helpers/mock-aws.sh" "${MOCK_BIN}/aws"
chmod +x "${MOCK_BIN}/aws"

export MOCK_RDS_STATUS="available"
export MOCK_SNAPSHOT_RESULT="failure"

# 前のテストのログを初期化
: >"$MOCK_LOG"

actual_status=0

printf 'DESTROY\n' | PATH="${MOCK_BIN}:$PATH" \
  bash "$SCRIPT_FILE" >"${TEST_DIR}/snapshot-failure.log" 2>&1 ||
  actual_status=$?

if [[ "$actual_status" -eq 0 ]]; then
  echo "FAIL: Destroy succeeded despite snapshot creation failure."
  exit 1
fi

if grep -q "APPLY_CALLED" "$MOCK_LOG"; then
  echo "FAIL: Terraform apply was called after snapshot failure."
  exit 1
fi

if ! grep -q "SNAPSHOT_CREATE_FAILED" "$MOCK_LOG"; then
  echo "FAIL: Snapshot creation failure was not simulated."
  exit 1
fi

echo "PASS: Terraform apply was blocked after snapshot creation failure."

unset MOCK_SNAPSHOT_RESULT

# RDS停止状態の正常系テスト
cp "${SCRIPT_DIR}/test-helpers/mock-aws.sh" "${MOCK_BIN}/aws"
chmod +x "${MOCK_BIN}/aws"

export MOCK_RDS_STATUS="stopped"

# 前のテスト結果を初期化
: >"$MOCK_LOG"

actual_status=0

printf 'DESTROY\n' | PATH="${MOCK_BIN}:$PATH" \
  bash "$SCRIPT_FILE" >"${TEST_DIR}/rds-stopped.log" 2>&1 ||
  actual_status=$?

if [[ "$actual_status" -ne 0 ]]; then
  echo "FAIL: Destroy workflow failed for stopped RDS."
  cat "${TEST_DIR}/rds-stopped.log"
  exit 1
fi

EXPECTED_LOG=$'RDS_STOPPED\nRDS_STARTED\nRDS_AVAILABLE\nSNAPSHOT_CREATED\nSNAPSHOT_AVAILABLE\nAPPLY_CALLED'
ACTUAL_LOG="$(cat "$MOCK_LOG")"

if [[ "$ACTUAL_LOG" != "$EXPECTED_LOG" ]]; then
  echo "FAIL: Unexpected operation order for stopped RDS."
  echo "Expected:"
  printf '%s\n' "$EXPECTED_LOG"
  echo "Actual:"
  printf '%s\n' "$ACTUAL_LOG"
  exit 1
fi

echo "PASS: Stopped RDS was started before snapshot and Terraform apply."

unset MOCK_RDS_STATUS

# 異常系テスト：RDSが存在しない場合
cp "${SCRIPT_DIR}/test-helpers/mock-aws.sh" "${MOCK_BIN}/aws"
chmod +x "${MOCK_BIN}/aws"

export MOCK_RDS_STATUS="not-found"

# 前のテスト結果を初期化
: >"$MOCK_LOG"

actual_status=0

printf 'DESTROY\n' | PATH="${MOCK_BIN}:$PATH" \
  bash "$SCRIPT_FILE" >"${TEST_DIR}/rds-not-found.log" 2>&1 ||
  actual_status=$?

if [[ "$actual_status" -ne 0 ]]; then
  echo "FAIL: Destroy workflow failed when RDS was not found."
  cat "${TEST_DIR}/rds-not-found.log"
  exit 1
fi

EXPECTED_LOG=$'RDS_NOT_FOUND\nAPPLY_CALLED'
ACTUAL_LOG="$(cat "$MOCK_LOG")"

if [[ "$ACTUAL_LOG" != "$EXPECTED_LOG" ]]; then
  echo "FAIL: Unexpected operation order when RDS was not found."
  echo "Expected:"
  printf '%s\n' "$EXPECTED_LOG"
  echo "Actual:"
  printf '%s\n' "$ACTUAL_LOG"
  exit 1
fi

if ! grep -q "Snapshot creation skipped." "${TEST_DIR}/rds-not-found.log"; then
  echo "FAIL: Snapshot skip message was not found."
  cat "${TEST_DIR}/rds-not-found.log"
  exit 1
fi

echo "PASS: Missing RDS skipped snapshot and continued Terraform apply."

unset MOCK_RDS_STATUS

# 異常系テスト：スナップショット完了待機に失敗
cp "${SCRIPT_DIR}/test-helpers/mock-aws.sh" "${MOCK_BIN}/aws"
chmod +x "${MOCK_BIN}/aws"

export MOCK_RDS_STATUS="available"
export MOCK_SNAPSHOT_RESULT="wait-failure"

# 前のテスト結果を初期化
: >"$MOCK_LOG"

actual_status=0

printf 'DESTROY\n' | PATH="${MOCK_BIN}:$PATH" \
  bash "$SCRIPT_FILE" >"${TEST_DIR}/snapshot-wait-failure.log" 2>&1 ||
  actual_status=$?

if [[ "$actual_status" -eq 0 ]]; then
  echo "FAIL: Destroy succeeded despite snapshot waiter failure."
  exit 1
fi

EXPECTED_LOG=$'RDS_DESCRIBED\nSNAPSHOT_CREATED\nSNAPSHOT_WAIT_FAILED'
ACTUAL_LOG="$(cat "$MOCK_LOG")"

if [[ "$ACTUAL_LOG" != "$EXPECTED_LOG" ]]; then
  echo "FAIL: Unexpected operation order after snapshot waiter failure."
  echo "Expected:"
  printf '%s\n' "$EXPECTED_LOG"
  echo "Actual:"
  printf '%s\n' "$ACTUAL_LOG"
  exit 1
fi

echo "PASS: Terraform apply was blocked after snapshot waiter failure."

unset MOCK_RDS_STATUS
unset MOCK_SNAPSHOT_RESULT

# 正常系テスト：--skip-snapshot 指定時
cp "${SCRIPT_DIR}/test-helpers/mock-aws.sh" "${MOCK_BIN}/aws"
chmod +x "${MOCK_BIN}/aws"

export MOCK_RDS_STATUS="available"
export MOCK_SKIP_SNAPSHOT="true"

# 未承認では削除されないことを確認
: >"$MOCK_LOG"

actual_status=0

printf 'NO\n' | PATH="${MOCK_BIN}:$PATH" \
  bash "$SCRIPT_FILE" --skip-snapshot \
  >"${TEST_DIR}/skip-unapproved.log" 2>&1 ||
  actual_status=$?

if [[ "$actual_status" -eq 0 ]]; then
  echo "FAIL: Skip-snapshot succeeded without approval."
  exit 1
fi

if [[ -s "$MOCK_LOG" ]]; then
  echo "FAIL: AWS operation or Terraform apply occurred without approval."
  cat "$MOCK_LOG"
  exit 1
fi

if ! grep -q "Destroy cancelled." "${TEST_DIR}/skip-unapproved.log"; then
  echo "FAIL: Confirmation was not enforced."
  exit 1
fi

# 承認後はスナップショットなしでApplyされることを確認
: >"$MOCK_LOG"

actual_status=0

printf 'DESTROY\n' | PATH="${MOCK_BIN}:$PATH" \
  bash "$SCRIPT_FILE" --skip-snapshot \
  >"${TEST_DIR}/skip-approved.log" 2>&1 ||
  actual_status=$?

if [[ "$actual_status" -ne 0 ]]; then
  echo "FAIL: Approved skip-snapshot workflow failed."
  cat "${TEST_DIR}/skip-approved.log"
  exit 1
fi

EXPECTED_LOG=$'RDS_DESCRIBED\nAPPLY_CALLED'
ACTUAL_LOG="$(cat "$MOCK_LOG")"

if [[ "$ACTUAL_LOG" != "$EXPECTED_LOG" ]]; then
  echo "FAIL: Unexpected operations with --skip-snapshot."
  echo "Expected:"
  printf '%s\n' "$EXPECTED_LOG"
  echo "Actual:"
  printf '%s\n' "$ACTUAL_LOG"
  exit 1
fi

echo "PASS: Skip-snapshot required approval and bypassed backup."

unset MOCK_RDS_STATUS
unset MOCK_SKIP_SNAPSHOT

# 正常系テスト：Terraformの削除対象が0件
cat >"${MOCK_BIN}/terraform" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

case "$*" in
  *"state list"*)
    exit 0
    ;;
  *"plan -destroy"*)
    exit 0
    ;;
  *"show -json"*)
    printf '%s\n' '{"resource_changes":[]}'
    ;;
  *"apply "*)
    echo "APPLY_CALLED" >> "$MOCK_LOG"
    exit 0
    ;;
  *)
    echo "Unexpected Terraform command: $*" >&2
    exit 1
    ;;
esac
EOF

chmod +x "${MOCK_BIN}/terraform"

# AWSモックは認証確認だけ許可
cat >"${MOCK_BIN}/aws" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

if [[ "$1" == "sts" && "$2" == "get-caller-identity" ]]; then
  printf '%s\n' '{"Account":"339741260090","Arn":"arn:aws:sts::339741260090:assumed-role/terraform-execution-role/test"}'
  exit 0
fi

echo "UNEXPECTED_AWS_OPERATION" >> "$MOCK_LOG"
exit 1
EOF

chmod +x "${MOCK_BIN}/aws"

: >"$MOCK_LOG"

actual_status=0

# 標準入力なしで実行（確認入力を要求しないことも検証）
PATH="${MOCK_BIN}:$PATH" \
  bash "$SCRIPT_FILE" </dev/null \
  >"${TEST_DIR}/no-resources.log" 2>&1 ||
  actual_status=$?

if [[ "$actual_status" -ne 0 ]]; then
  echo "FAIL: Empty destroy plan did not exit successfully."
  cat "${TEST_DIR}/no-resources.log"
  exit 1
fi

if [[ -s "$MOCK_LOG" ]]; then
  echo "FAIL: Unexpected AWS operation or Terraform apply."
  cat "$MOCK_LOG"
  exit 1
fi

if ! grep -q "No runtime resources to destroy." "${TEST_DIR}/no-resources.log"; then
  echo "FAIL: No-resources message was not found."
  cat "${TEST_DIR}/no-resources.log"
  exit 1
fi

echo "PASS: Empty destroy plan exited without RDS operations or Terraform apply."

# 異常系テスト：保護対象リソースが削除計画に含まれる
cat >"${MOCK_BIN}/terraform" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

case "$*" in
  *"state list"*)
    exit 0
    ;;
  *"plan -destroy"*)
    exit 0
    ;;
  *"show -json"*)
    cat <<'JSON'
{
  "resource_changes": [
    {
      "address": "aws_route53_zone.main",
      "type": "aws_route53_zone",
      "change": {
        "actions": ["delete"]
      }
    }
  ]
}
JSON
    ;;
  *"apply "*)
    echo "APPLY_CALLED" >> "$MOCK_LOG"
    exit 0
    ;;
  *)
    echo "Unexpected Terraform command: $*" >&2
    exit 1
    ;;
esac
EOF

chmod +x "${MOCK_BIN}/terraform"

: >"$MOCK_LOG"

actual_status=0

PATH="${MOCK_BIN}:$PATH" \
  bash "$SCRIPT_FILE" </dev/null \
  >"${TEST_DIR}/protected-resource.log" 2>&1 ||
  actual_status=$?

if [[ "$actual_status" -eq 0 ]]; then
  echo "FAIL: Protected resource deletion was allowed."
  exit 1
fi

if grep -q "APPLY_CALLED" "$MOCK_LOG"; then
  echo "FAIL: Terraform apply was called for protected resource."
  exit 1
fi

if ! grep -qi "protected" "${TEST_DIR}/protected-resource.log"; then
  echo "FAIL: Protected resource rejection message was not found."
  cat "${TEST_DIR}/protected-resource.log"
  exit 1
fi

echo "PASS: Protected resource deletion was blocked."

# 異常系テスト：Terraform Stateに保護対象リソースが存在する
cat >"${MOCK_BIN}/terraform" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

case "$*" in
  *"state list"*)
    printf '%s\n' "aws_route53_zone.main"
    echo "STATE_CHECKED" >> "$MOCK_LOG"
    ;;
  *"plan -destroy"*)
    echo "PLAN_CALLED" >> "$MOCK_LOG"
    exit 0
    ;;
  *"show -json"*)
    echo "SHOW_CALLED" >> "$MOCK_LOG"
    printf '%s\n' '{"resource_changes":[]}'
    ;;
  *"apply "*)
    echo "APPLY_CALLED" >> "$MOCK_LOG"
    exit 0
    ;;
  *)
    echo "Unexpected Terraform command: $*" >&2
    exit 1
    ;;
esac
EOF

chmod +x "${MOCK_BIN}/terraform"

# AWSモックは認証確認だけ許可
cat >"${MOCK_BIN}/aws" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

if [[ "$1" == "sts" && "$2" == "get-caller-identity" ]]; then
  printf '%s\n' '{"Account":"339741260090","Arn":"arn:aws:sts::339741260090:assumed-role/terraform-execution-role/test"}'
  exit 0
fi

echo "UNEXPECTED_AWS_OPERATION" >> "$MOCK_LOG"
exit 1
EOF

chmod +x "${MOCK_BIN}/aws"

: >"$MOCK_LOG"

actual_status=0

PATH="${MOCK_BIN}:$PATH" \
  bash "$SCRIPT_FILE" </dev/null \
  >"${TEST_DIR}/protected-state.log" 2>&1 ||
  actual_status=$?

if [[ "$actual_status" -eq 0 ]]; then
  echo "FAIL: Protected resource in Terraform State was allowed."
  cat "${TEST_DIR}/protected-state.log"
  exit 1
fi

EXPECTED_LOG="STATE_CHECKED"
ACTUAL_LOG="$(cat "$MOCK_LOG")"

if [[ "$ACTUAL_LOG" != "$EXPECTED_LOG" ]]; then
  echo "FAIL: Terraform proceeded beyond protected State check."
  echo "Expected: $EXPECTED_LOG"
  echo "Actual:"
  printf '%s\n' "$ACTUAL_LOG"
  exit 1
fi

if ! grep -qi "protected" "${TEST_DIR}/protected-state.log"; then
  echo "FAIL: Protected State rejection message was not found."
  cat "${TEST_DIR}/protected-state.log"
  exit 1
fi

echo "PASS: Protected resource in Terraform State blocked planning."
echo "All integration tests passed."
