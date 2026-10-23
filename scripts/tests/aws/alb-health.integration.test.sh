#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

TEST_DIR="$(mktemp -d)"
trap 'rm -rf "$TEST_DIR"' EXIT

mkdir -p "$TEST_DIR/bin"
ln -s "$SCRIPT_DIR/../helpers/mock-aws.sh" "$TEST_DIR/bin/aws"

export PATH="$TEST_DIR/bin:$PATH"
export MOCK_LOG="$TEST_DIR/aws.log"

export AWS_PROFILE="terraform-execution"
export AWS_REGION="ap-northeast-1"

source "$SCRIPT_DIR/../../lib/alb-health.sh"

# テスト中は実際に待機しない
sleep() {
  case "$1" in
  2 | 30) ;;
  *)
    echo "ERROR: Unexpected sleep duration: $1" >&2
    return 1
    ;;
  esac
}

# 1. ターゲットがhealthyなら成功
: >"$MOCK_LOG"

wait_for_alb_targets_healthy

if ! grep -Fxq "ALB_TARGET_GROUP_DESCRIBED" "$MOCK_LOG" ||
  ! grep -Fxq "ALB_HEALTH_CHECKED" "$MOCK_LOG"; then
  echo "FAIL: ALB healthy target"
  exit 1
fi

echo "PASS: ALB healthy target"

# 2. AWS APIが失敗したら中断
: >"$MOCK_LOG"

status=0
output="$(
  MOCK_ALB_HEALTH_STATUS=api-failure \
    wait_for_alb_targets_healthy 2>&1
)" || status=$?

if [[ "$status" -eq 0 ]] ||
  ! grep -Fq "Simulated ALB API failure" <<<"$output"; then
  echo "FAIL: ALB API failure"
  echo "Exit status: $status"
  echo "Output: $output"
  exit 1
fi

echo "PASS: ALB API failure"

# 3. ターゲットグループARNを取得できなければ中断
: >"$MOCK_LOG"

status=0
output="$(
  MOCK_ALB_TARGET_GROUP_ARN=None \
    wait_for_alb_targets_healthy 2>&1
)" || status=$?

if [[ "$status" -eq 0 ]] ||
  ! grep -Fq "ALB target group ARN could not be resolved." <<<"$output"; then
  echo "FAIL: ALB target group ARN missing"
  exit 1
fi

echo "PASS: ALB target group ARN missing"

# 4. unhealthyが続いた場合はタイムアウト
: >"$MOCK_LOG"

status=0
output="$(
  MOCK_ALB_HEALTH_STATUS=unhealthy \
    wait_for_alb_targets_healthy 2>&1
)" || status=$?

if [[ "$status" -eq 0 ]] ||
  ! grep -Fq "ALB targets did not become healthy within the timeout." <<<"$output" ||
  ! grep -Fq "Target.FailedHealthChecks" <<<"$output" ||
  ! grep -Fq "Health checks failed" <<<"$output" ||
  [[ "$(grep -Fxc "ALB_HEALTH_CHECKED" "$MOCK_LOG")" -ne 20 ]]; then
  echo "FAIL: ALB unhealthy timeout"
  echo "$output"
  exit 1
fi

echo "PASS: ALB unhealthy timeout"

# 5. ターゲットが0件の場合もタイムアウト
: >"$MOCK_LOG"

status=0
output="$(
  MOCK_ALB_HEALTH_STATUS=empty \
    wait_for_alb_targets_healthy 2>&1
)" || status=$?

if [[ "$status" -eq 0 ]] ||
  ! grep -Fq "ALB targets did not become healthy within the timeout." <<<"$output" ||
  [[ "$(grep -Fxc "ALB_HEALTH_CHECKED" "$MOCK_LOG")" -ne 20 ]]; then
  echo "FAIL: ALB empty target timeout"
  echo "$output"
  exit 1
fi

echo "PASS: ALB empty target timeout"

# 一時的なAWS APIエラーから回復したら成功
: >"$MOCK_LOG"

status=0
output="$(
  MOCK_ALB_HEALTH_STATUS=transient-failure \
    wait_for_alb_targets_healthy 2>&1
)" || status=$?

if [[ "$status" -ne 0 ]] ||
  [[ "$(grep -Fxc "ALB_HEALTH_CHECKED" "$MOCK_LOG")" -ne 2 ]]; then
  echo "FAIL: ALB transient API failure recovery"
  echo "$output"
  exit 1
fi

echo "PASS: ALB transient API failure recovery"

# 7. 認証期限切れの場合は再試行せず終了
: >"$MOCK_LOG"

status=0
output="$(
  MOCK_ALB_HEALTH_STATUS=auth-failure \
    wait_for_alb_targets_healthy 2>&1
)" || status=$?

if [[ "$status" -eq 0 ]] ||
  ! grep -Fq "ExpiredToken" <<<"$output" ||
  [[ "$(grep -Fxc "ALB_HEALTH_CHECKED" "$MOCK_LOG")" -ne 1 ]]; then
  echo "FAIL: ALB authentication failure"
  echo "$output"
  exit 1
fi

echo "PASS: ALB authentication failure"

# 8. IAM権限不足の場合は再試行せず終了
: >"$MOCK_LOG"

status=0
output="$(
  MOCK_ALB_HEALTH_STATUS=access-denied \
    wait_for_alb_targets_healthy 2>&1
)" || status=$?

if [[ "$status" -eq 0 ]] ||
  ! grep -Fq "AccessDeniedException" <<<"$output" ||
  [[ "$(grep -Fxc "ALB_HEALTH_CHECKED" "$MOCK_LOG")" -ne 1 ]]; then
  echo "FAIL: ALB access denied"
  echo "$output"
  exit 1
fi

echo "PASS: ALB access denied"

# 9. 一時的なAPIエラーが継続した場合は3回で終了
: >"$MOCK_LOG"

status=0
output="$(
  MOCK_ALB_HEALTH_STATUS=throttling-always \
    wait_for_alb_targets_healthy 2>&1
)" || status=$?

if [[ "$status" -eq 0 ]] ||
  ! grep -Fq "ALB health check API retries exhausted" <<<"$output" ||
  [[ "$(grep -Fxc "ALB_HEALTH_CHECKED" "$MOCK_LOG")" -ne 3 ]]; then
  echo "FAIL: ALB API retry exhaustion"
  echo "$output"
  exit 1
fi

echo "PASS: ALB API retry exhaustion"

echo "All ALB health integration tests passed."
