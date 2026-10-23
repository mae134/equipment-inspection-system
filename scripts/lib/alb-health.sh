#!/usr/bin/env bash

get_alb_target_group_arn() {
  aws elbv2 describe-target-groups \
    --profile "$AWS_PROFILE" \
    --region "$AWS_REGION" \
    --names "equipment-inspection-tg" \
    --query 'TargetGroups[0].TargetGroupArn' \
    --output text
}

wait_for_alb_targets_healthy() {
  local target_group_arn
  local health_json
  local attempt

  target_group_arn="$(get_alb_target_group_arn)"

  if [[ -z "$target_group_arn" || "$target_group_arn" == "None" ]]; then
    echo "ERROR: ALB target group ARN could not be resolved." >&2
    return 1
  fi

  # 30秒間隔で最大20回確認
  for ((attempt = 1; attempt <= 20; attempt++)); do
    health_json="$(get_alb_target_health_json "$target_group_arn")" || return 1

    # ターゲットが1件以上あり、すべてhealthyなら成功
    if jq -e '
      (.TargetHealthDescriptions | type == "array") and
      (.TargetHealthDescriptions | length > 0) and
      all(.TargetHealthDescriptions[]; .TargetHealth.State == "healthy")
    ' <<<"$health_json" >/dev/null; then
      echo "ALB targets are healthy."
      return 0
    fi

    if ((attempt < 20)); then
      echo "Waiting for ALB targets to become healthy (${attempt}/20)..."
      sleep 30
    fi
  done

  echo "ERROR: ALB targets did not become healthy within the timeout." >&2
  echo "Last ALB target health:" >&2

  jq -r '
  .TargetHealthDescriptions[]? |
  "  State: \(.TargetHealth.State // "unknown")\n" +
  "  Reason: \(.TargetHealth.Reason // "N/A")\n" +
  "  Description: \(.TargetHealth.Description // "N/A")"
' <<<"$health_json" >&2

  return 1
}

get_alb_target_health_json() (
  local target_group_arn="$1"
  local error_file
  local health_json
  local api_attempt
  local exit_status
  local health_error

  error_file="$(mktemp)" || exit 1
  trap 'rm -f -- "$error_file"' EXIT

  for ((api_attempt = 1; api_attempt <= 3; api_attempt++)); do
    exit_status=0

    health_json="$(
      aws elbv2 describe-target-health \
        --profile "$AWS_PROFILE" \
        --region "$AWS_REGION" \
        --target-group-arn "$target_group_arn" \
        --output json 2>"$error_file"
    )" || exit_status=$?

    if ((exit_status == 0)); then
      printf '%s\n' "$health_json"
      exit 0
    fi

    health_error="$(<"$error_file")"

    if [[ "$health_error" != *"ThrottlingException"* &&
      "$health_error" != *"TooManyRequestsException"* ]]; then
      echo "ERROR: ALB health check API failed (exit $exit_status): $health_error" >&2
      exit "$exit_status"
    fi

    if ((api_attempt == 3)); then
      echo "ERROR: ALB health check API retries exhausted (exit $exit_status): $health_error" >&2
      exit "$exit_status"
    fi

    echo "WARNING: Temporary ALB API error. Retrying (${api_attempt}/3)..." >&2
    sleep 2
  done
)
