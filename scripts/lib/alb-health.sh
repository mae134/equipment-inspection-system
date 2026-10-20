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
    health_json="$(
      aws elbv2 describe-target-health \
        --profile "$AWS_PROFILE" \
        --region "$AWS_REGION" \
        --target-group-arn "$target_group_arn" \
        --output json
    )" || return 1

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
  return 1
}
