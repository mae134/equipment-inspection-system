#!/usr/bin/env bash
set -euo pipefail

MOCK_RDS_STATUS="${MOCK_RDS_STATUS:-unexpected}"
MOCK_SNAPSHOT_RESULT="${MOCK_SNAPSHOT_RESULT:-success}"
MOCK_SKIP_SNAPSHOT="${MOCK_SKIP_SNAPSHOT:-false}"

case "${1:-} ${2:-}" in
"sts get-caller-identity")
  printf '%s\n' \
    "${MOCK_CALLER_IDENTITY:-{\"Account\":\"339741260090\",\"Arn\":\"arn:aws:sts::339741260090:assumed-role/terraform-execution-role/test\"}}"
  ;;

"rds describe-db-snapshots")
  SNAPSHOT_STATUS="${MOCK_RESTORE_SNAPSHOT_STATUS:-available}"
  SOURCE_DB="${MOCK_RESTORE_SOURCE_DB:-equipment-inspection-db}"

  jq -n \
    --arg status "$SNAPSHOT_STATUS" \
    --arg source "$SOURCE_DB" \
    '{
      DBSnapshots: [
        {
          Status: $status,
          DBInstanceIdentifier: $source
        }
      ]
    }'

  echo "SNAPSHOT_DESCRIBED" >>"$MOCK_LOG"
  ;;

"rds describe-db-instances")
  case "$MOCK_RDS_STATUS" in
  available)
    echo "available"
    echo "RDS_DESCRIBED" >>"$MOCK_LOG"
    ;;
  stopped)
    echo "stopped"
    echo "RDS_STOPPED" >>"$MOCK_LOG"
    ;;
  not-found)
    echo "DBInstanceNotFound: DB instance does not exist" >&2
    echo "RDS_NOT_FOUND" >>"$MOCK_LOG"
    exit 255
    ;;
  *)
    echo "Unexpected RDS status: $MOCK_RDS_STATUS" >&2
    exit 1
    ;;
  esac
  ;;

"rds start-db-instance")

  if [[ "$MOCK_SKIP_SNAPSHOT" == "true" ]]; then
    echo "UNEXPECTED_RDS_OPERATION" >>"$MOCK_LOG"
    exit 1
  fi

  if [[ "$MOCK_RDS_STATUS" != "stopped" ]]; then
    echo "Unexpected RDS start" >&2
    exit 1
  fi

  echo "RDS_STARTED" >>"$MOCK_LOG"
  ;;

"rds create-db-snapshot")

  if [[ "$MOCK_SKIP_SNAPSHOT" == "true" ]]; then
    echo "UNEXPECTED_RDS_OPERATION" >>"$MOCK_LOG"
    exit 1
  fi

  if [[ "$MOCK_RDS_STATUS" != "available" && "$MOCK_RDS_STATUS" != "stopped" ]]; then
    echo "Unexpected snapshot creation" >&2
    exit 1
  fi

  if [[ "$MOCK_RDS_STATUS" == "stopped" ]]; then
    if ! grep -qx "RDS_AVAILABLE" "$MOCK_LOG"; then
      echo "Snapshot attempted before RDS became available" >&2
      exit 1
    fi
  fi

  if [[ "$MOCK_SNAPSHOT_RESULT" == "failure" ]]; then
    echo "SNAPSHOT_CREATE_FAILED" >>"$MOCK_LOG"
    echo "Simulated snapshot creation failure" >&2
    exit 1
  fi

  echo "SNAPSHOT_CREATED" >>"$MOCK_LOG"
  ;;

"rds modify-db-instance")
  if [[ "${MOCK_RDS_MODIFY_FAIL:-false}" == true ]]; then
    echo "MOCK_RDS_MODIFY_FAILED" >>"$MOCK_LOG"
    echo "Simulated RDS password update failure" >&2
    exit 1
  fi

  # AWS CLIのJSON入力を読み取り、パスワードのハッシュ値を記録
  rds_input="$(cat)"

  if ! jq -e '
    .DBInstanceIdentifier == "equipment-inspection-db" and
    .ApplyImmediately == true and
    (.MasterUserPassword | type == "string" and test("^[0-9a-f]{48}$"))
  ' <<<"$rds_input" >/dev/null; then
    echo "ERROR: Invalid RDS password update JSON." >&2
    exit 1
  fi

  rds_password_hash="$(
    jq -r '.MasterUserPassword' <<<"$rds_input" |
      tr -d '\n' |
      sha256sum |
      cut -d ' ' -f 1
  )"

  printf '%s\n' "$rds_password_hash" >"${MOCK_LOG}.rds-password-hash"

  echo "MOCK_RDS_MODIFY_OK" >>"$MOCK_LOG"
  printf '{}\n'
  ;;

"ssm put-parameter")
  # AWS CLIへ渡されたJSONを検証（パスワード自体は記録しない）
  cli_input=""

  while [[ $# -gt 0 ]]; do
    case "$1" in
    --cli-input-json)
      if [[ $# -lt 2 ]]; then
        echo "ERROR: Missing SSM CLI input value." >&2
        exit 1
      fi
      cli_input="$2"
      shift 2
      ;;
    --cli-input-json=*)
      cli_input="${1#--cli-input-json=}"
      shift
      ;;
    *)
      shift
      ;;
    esac
  done

  if [[ "$cli_input" != "file:///dev/stdin" ]]; then
    echo "ERROR: Unexpected SSM CLI input." >&2
    exit 1
  fi

  ssm_input="$(cat)"

  if ! jq -e '
    .Name == "/equipment-inspection/prod/db/password" and
    .Type == "SecureString" and
    .Overwrite == true and
    (.Value | type == "string" and test("^[0-9a-f]{48}$"))
' <<<"$ssm_input" >/dev/null; then
    echo "ERROR: Invalid SSM parameter JSON." >&2
    exit 1
  fi

  # RDS側で設定したパスワードとの一致を確認
  if [[ -f "${MOCK_LOG}.rds-password-hash" ]]; then
    ssm_password_hash="$(
      jq -r '.Value' <<<"$ssm_input" |
        tr -d '\n' |
        sha256sum |
        cut -d ' ' -f 1
    )"

    rds_password_hash="$(cat "${MOCK_LOG}.rds-password-hash")"

    if [[ "$ssm_password_hash" != "$rds_password_hash" ]]; then
      echo "ERROR: RDS and SSM passwords do not match." >&2
      exit 1
    fi

    echo "RDS_SSM_PASSWORD_MATCH" >>"$MOCK_LOG"
  fi

  if [[ "${MOCK_SSM_PUT_FAIL:-false}" == true ]]; then
    echo "SSM_PUT_FAILED" >>"$MOCK_LOG"
    echo "Simulated SSM parameter update failure" >&2
    exit 1
  fi

  echo "SSM_PUT_OK" >>"$MOCK_LOG"
  printf '{}\n'
  ;;

"ecs update-service")
  if [[ "${MOCK_ECS_UPDATE_FAIL:-false}" == true ]]; then
    echo "ECS_UPDATE_FAILED" >>"$MOCK_LOG"
    echo "Simulated ECS service update failure" >&2
    exit 1
  fi

  if [[ " $* " != *" --cluster equipment-inspection-cluster "* ]] ||
    [[ " $* " != *" --service equipment-inspection-service "* ]] ||
    [[ " $* " != *" --desired-count 1 "* ]]; then
    echo "ERROR: Unexpected ECS update-service arguments." >&2
    exit 1
  fi

  echo "ECS_UPDATE_OK" >>"$MOCK_LOG"
  printf '{}\n'
  ;;

  "ecr get-login-password")
  echo "ECR_LOGIN_PASSWORD_REQUESTED" >>"$MOCK_LOG"

  if [[ "${MOCK_ECR_AUTH_FAIL:-false}" == true ]]; then
    echo "Simulated ECR authentication failure" >&2
    exit 1
  fi

  printf '%s\n' "mock-ecr-password"
  ;;

"ecs wait")
  if [[ "${3:-}" != "services-stable" ]] ||
    [[ " $* " != *" --cluster equipment-inspection-cluster "* ]] ||
    [[ " $* " != *" --services equipment-inspection-service "* ]]; then
    echo "ERROR: Unexpected ECS waiter arguments." >&2
    exit 1
  fi

  # ECS起動要求が先に成功していることを確認
  if ! grep -Fxq "ECS_UPDATE_OK" "$MOCK_LOG"; then
    echo "ERROR: ECS waiter called before service update." >&2
    exit 1
  fi

  if [[ "${MOCK_ECS_WAIT_FAIL:-false}" == true ]]; then
    echo "ECS_WAIT_FAILED" >>"$MOCK_LOG"
    echo "Simulated ECS waiter failure" >&2
    exit 255
  fi

  echo "ECS_WAIT_COMPLETED" >>"$MOCK_LOG"
  printf '{}\n'
  ;;

"elbv2 describe-target-groups")
  if [[ " $* " != *" --names equipment-inspection-tg "* ]]; then
    echo "ERROR: Unexpected target group name." >&2
    exit 1
  fi

  echo "ALB_TARGET_GROUP_DESCRIBED" >>"$MOCK_LOG"
  printf '%s\n' \
    "${MOCK_ALB_TARGET_GROUP_ARN:-arn:aws:elasticloadbalancing:ap-northeast-1:339741260090:targetgroup/equipment-inspection-tg/mock123}"
  ;;

"elbv2 describe-target-health")
  if [[ " $* " != *" --target-group-arn arn:aws:elasticloadbalancing:ap-northeast-1:339741260090:targetgroup/equipment-inspection-tg/mock123 "* ]]; then
    echo "ERROR: Unexpected target group ARN." >&2
    exit 1
  fi

  echo "ALB_HEALTH_CHECKED" >>"$MOCK_LOG"

  case "${MOCK_ALB_HEALTH_STATUS:-healthy}" in
  healthy)
    printf '%s\n' '{"TargetHealthDescriptions":[{"TargetHealth":{"State":"healthy"}}]}'
    ;;
  unhealthy)
    printf '%s\n' '{"TargetHealthDescriptions":[{"TargetHealth":{"State":"unhealthy"}}]}'
    ;;
  empty)
    printf '%s\n' '{"TargetHealthDescriptions":[]}'
    ;;
  api-failure)
    echo "Simulated ALB API failure" >&2
    exit 1
    ;;
  *)
    echo "ERROR: Unknown mock ALB health status." >&2
    exit 1
    ;;
  esac
  ;;

"rds wait")

  if [[ "$MOCK_SKIP_SNAPSHOT" == "true" ]]; then
    echo "UNEXPECTED_RDS_OPERATION" >>"$MOCK_LOG"
    exit 1
  fi

  case "${3:-}" in
  db-instance-available)
    if [[ "${MOCK_RESTORE_RDS_WAIT:-false}" == true ]]; then
      # 復元時のRDS待機
      if [[ "${MOCK_RDS_WAIT_FAIL:-false}" == true ]]; then
        echo "RDS_WAIT_FAILED" >>"$MOCK_LOG"
        echo "Simulated RDS waiter failure" >&2
        exit 255
      fi

      if ! grep -Fxq "MOCK_RDS_MODIFY_OK" "$MOCK_LOG"; then
        echo "RDS waiter called before password update" >&2
        exit 1
      fi

      echo "RDS_PASSWORD_UPDATE_WAIT_COMPLETED" >>"$MOCK_LOG"
    else
      # 既存のdestroy側のRDS待機
      if [[ "$MOCK_RDS_STATUS" != "stopped" ]]; then
        echo "Unexpected RDS instance waiter" >&2
        exit 1
      fi

      echo "RDS_AVAILABLE" >>"$MOCK_LOG"
    fi
    ;;

  db-snapshot-available)
    if [[ "$MOCK_SNAPSHOT_RESULT" == "wait-failure" ]]; then
      echo "SNAPSHOT_WAIT_FAILED" >>"$MOCK_LOG"
      echo "Simulated snapshot waiter failure" >&2
      exit 255
    fi

    echo "SNAPSHOT_AVAILABLE" >>"$MOCK_LOG"
    ;;
  *)
    echo "Unexpected RDS waiter: $*" >&2
    exit 1
    ;;
  esac
  ;;

*)
  echo "Unexpected AWS command: $*" >&2
  exit 1
  ;;
esac
