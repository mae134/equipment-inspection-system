#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCRIPT_FILE="${SCRIPT_DIR}/../../aws/destroy-aws-environment.sh"

# confirm_destroy() の定義だけを読み込む
FUNCTION_DEFINITION="$(
  sed -n '/^confirm_destroy() {$/,/^}$/p' "$SCRIPT_FILE"
)"

if [[ -z "$FUNCTION_DEFINITION" ]]; then
  echo "FAIL: confirm_destroy function not found."
  exit 1
fi

eval "$FUNCTION_DEFINITION"

run_test() {
  local name="$1"
  local input="$2"
  local expected_status="$3"
  local actual_status=0

  confirm_destroy <<<"$input" >/dev/null 2>&1 || actual_status=$?

  if [[ "$actual_status" -eq "$expected_status" ]]; then
    echo "PASS: $name"
  else
    echo "FAIL: $name (expected=$expected_status, actual=$actual_status)"
    exit 1
  fi
}

run_test "Exact confirmation" "DESTROY" 0
run_test "Lowercase confirmation rejected" "destroy" 1
run_test "Other input rejected" "YES" 1
run_test "Empty input rejected" "" 1

# 標準入力がEOFの場合
actual_status=0
confirm_destroy </dev/null >/dev/null 2>&1 || actual_status=$?

if [[ "$actual_status" -eq 1 ]]; then
  echo "PASS: EOF rejected"
else
  echo "FAIL: EOF rejected"
  exit 1
fi

echo "All confirmation tests passed."
