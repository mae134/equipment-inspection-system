#!/usr/bin/env bash

set -euo pipefail

SNAPSHOT_ID=""
NEW_DB=false
APPLY=false

usage() {
  cat <<'EOF'
Usage:
  restore-aws-environment.sh --snapshot-id SNAPSHOT_ID
  restore-aws-environment.sh --new-db

Options:
  --snapshot-id ID  Restore RDS from the specified snapshot.
  --new-db          Create a new RDS database.
  --help            Show this help message.
  --apply           Apply the Terraform plan after safety checks.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
  --new-db)
    NEW_DB=true
    shift
    ;;
  --snapshot-id)
    if [[ $# -lt 2 || -z "$2" || "$2" == --* ]]; then
      echo "ERROR: --snapshot-id requires a value." >&2
      exit 1
    fi
    SNAPSHOT_ID="$2"
    shift 2
    ;;
  --help)
    usage
    exit 0
    ;;
  --apply)
    APPLY=true
    shift
    ;;
  *)
    echo "ERROR: Unknown argument: $1" >&2
    usage >&2
    exit 1
    ;;
  esac
done

if [[ "$NEW_DB" == true && -n "$SNAPSHOT_ID" ]]; then
  echo "ERROR: --new-db and --snapshot-id cannot be used together." >&2
  exit 1
fi

if [[ "$NEW_DB" == false && -z "$SNAPSHOT_ID" ]]; then
  echo "ERROR: Specify either --new-db or --snapshot-id." >&2
  usage >&2
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
RUNTIME_DIR="${PROJECT_ROOT}/terraform/runtime"
source "${SCRIPT_DIR}/../lib/terraform-password.sh"
source "${SCRIPT_DIR}/../lib/rds-password.sh"
source "${SCRIPT_DIR}/../lib/ssm-password.sh"
source "${SCRIPT_DIR}/../lib/ecs-service.sh"
source "${SCRIPT_DIR}/../lib/alb-health.sh"
source "${SCRIPT_DIR}/../lib/ecr-image.sh"

AWS_PROFILE="terraform-execution"
AWS_REGION="ap-northeast-1"
EXPECTED_ACCOUNT_ID="339741260090"
EXPECTED_ROLE_NAME="terraform-execution-role"
DB_INSTANCE_IDENTIFIER="equipment-inspection-db"

generate_db_password() {
  openssl rand -hex 24
}

for command_name in aws jq terraform openssl; do
  if ! command -v "$command_name" >/dev/null 2>&1; then
    echo "ERROR: ${command_name} is not installed." >&2
    exit 1
  fi
done

if [[ ! -d "$RUNTIME_DIR" ]]; then
  echo "ERROR: Terraform runtime directory not found." >&2
  exit 1
fi

# AWSアカウントとIAMロールを検証
CALLER_IDENTITY="$(
  aws sts get-caller-identity \
    --profile "$AWS_PROFILE" \
    --region "$AWS_REGION" \
    --output json
)"

ACCOUNT_ID="$(jq -r '.Account' <<<"$CALLER_IDENTITY")"
CALLER_ARN="$(jq -r '.Arn' <<<"$CALLER_IDENTITY")"

if [[ "$ACCOUNT_ID" != "$EXPECTED_ACCOUNT_ID" ]]; then
  echo "ERROR: Unexpected AWS account: $ACCOUNT_ID" >&2
  exit 1
fi

if [[ "$CALLER_ARN" != arn:aws:sts::"${EXPECTED_ACCOUNT_ID}":assumed-role/"${EXPECTED_ROLE_NAME}"/* ]]; then
  echo "ERROR: Unexpected IAM role: $CALLER_ARN" >&2
  exit 1
fi

echo "AWS account and IAM role verified."
echo "Account: $ACCOUNT_ID"
echo "Role: $CALLER_ARN"
echo "Region: $AWS_REGION"

if [[ "$NEW_DB" == true ]]; then
  echo "New RDS creation mode selected."
else

  # 指定スナップショットの情報を取得
  SNAPSHOT_JSON="$(
    aws rds describe-db-snapshots \
      --profile "$AWS_PROFILE" \
      --region "$AWS_REGION" \
      --db-snapshot-identifier "$SNAPSHOT_ID" \
      --snapshot-type manual \
      --output json
  )"

  SNAPSHOT_STATUS="$(jq -r '.DBSnapshots[0].Status // empty' <<<"$SNAPSHOT_JSON")"
  SOURCE_DB="$(jq -r '.DBSnapshots[0].DBInstanceIdentifier // empty' <<<"$SNAPSHOT_JSON")"

  if [[ "$SNAPSHOT_STATUS" != "available" ]]; then
    echo "ERROR: Snapshot is not available: $SNAPSHOT_STATUS" >&2
    exit 1
  fi

  if [[ "$SOURCE_DB" != "$DB_INSTANCE_IDENTIFIER" ]]; then
    echo "ERROR: Snapshot belongs to an unexpected DB: $SOURCE_DB" >&2
    exit 1
  fi

  echo "RDS snapshot verified."
  echo "Snapshot: $SNAPSHOT_ID"
  echo "Status: $SNAPSHOT_STATUS"
  echo "Source DB: $SOURCE_DB"

fi

echo "Preflight checks completed. No AWS resources were modified."

# Terraform planファイルの一時保存先
PLAN_DIR="$(mktemp -d)"
chmod 700 "$PLAN_DIR"

cleanup() {
  local exit_status=$?

  rm -rf -- "$PLAN_DIR"

  if ((exit_status != 0)); then
    echo "ERROR: AWS environment restoration failed." >&2
    echo "Existing AWS resources were not automatically rolled back." >&2
    echo "Before retrying, inspect Terraform state and AWS resource status." >&2
  fi
}

trap cleanup EXIT

PLAN_FILE="${PLAN_DIR}/restore.tfplan"

VARS_FILE="${PLAN_DIR}/restore.auto.tfvars.json"

if [[ "$NEW_DB" == true ]]; then
  printf '{"db_snapshot_identifier":null,"ecs_desired_count":0}\n' >"$VARS_FILE"
else
  jq -n \
    --arg snapshot_id "$SNAPSHOT_ID" \
    '{
      db_snapshot_identifier: $snapshot_id,
      ecs_desired_count: 0
    }' >"$VARS_FILE"
fi

chmod 600 "$VARS_FILE"

# DBパスワード生成
DB_PASSWORD="$(generate_db_password)"

echo "Planning AWS environment restoration..."

run_terraform_with_db_password \
  "$DB_PASSWORD" \
  plan \
  -var-file="$VARS_FILE" \
  -out="$PLAN_FILE"

echo "Terraform plan completed."

# 保存済みplanをJSON形式で取得
PLAN_JSON="${PLAN_DIR}/restore-plan.json"

terraform -chdir="$RUNTIME_DIR" show -json "$PLAN_FILE" >"$PLAN_JSON"
chmod 600 "$PLAN_JSON"

# plan JSONの形式を検証
if ! jq -e '
  (.resource_changes | type == "array") and
  all(
    .resource_changes[];
    (.change.actions | type == "array") and
    (.change.actions | length > 0)
  )
' "$PLAN_JSON" >/dev/null; then
  echo "ERROR: Invalid Terraform plan JSON." >&2
  exit 1
fi

# リソース削除・置換を検出
if jq -e '
  any(
    .resource_changes[]?;
    any(.change.actions[]; . == "delete")
  )
' "$PLAN_JSON" >/dev/null; then
  echo "ERROR: Terraform plan contains resource deletion or replacement." >&2
  exit 1
fi

echo "Terraform plan safety check passed."

# --applyが指定されていなければplan確認のみで終了
if [[ "$APPLY" != true ]]; then
  echo "Plan-only mode. Terraform apply was not executed."
  exit 0
fi

# 誤実行防止のため、明示的な確認を要求
if [[ "${CONFIRM_AWS_RESTORE:-}" != "YES" ]]; then
  echo "ERROR: Set CONFIRM_AWS_RESTORE=YES to execute Terraform apply." >&2
  exit 1
fi

echo "Applying saved Terraform plan..."

run_terraform_with_db_password \
  "$DB_PASSWORD" \
  apply \
  "$PLAN_FILE"

echo "Terraform apply completed."

# スナップショット復元時はRDSのマスターパスワードを更新
if [[ "$NEW_DB" == false ]]; then
  echo "Updating restored RDS master password..."

  modify_rds_master_password "$DB_PASSWORD"

  echo "RDS master password update requested."

  echo "Waiting for RDS master password update..."

  aws rds wait db-instance-available \
    --profile "$AWS_PROFILE" \
    --region "$AWS_REGION" \
    --db-instance-identifier "$DB_INSTANCE_IDENTIFIER"

  echo "RDS instance is available."
fi

# ECSが参照するDBパスワードをSSMへ登録
echo "Updating DB password in SSM Parameter Store..."

put_db_password_to_ssm "$DB_PASSWORD"

echo "SSM DB password update completed."

# ECS起動前にアプリケーションイメージをECRへ登録
echo "Building and pushing application image to ECR..."

build_and_push_ecr_image || exit 1

# DBパスワードとECRイメージの準備完了後にECSサービスを起動
echo "Starting ECS service..."

start_ecs_service

echo "ECS service start requested."

echo "Waiting for ECS service to become stable..."

wait_for_ecs_service_stable

echo "ECS service is stable."

# ECS安定稼働後にALBターゲットの正常性を確認
echo "Waiting for ALB targets to become healthy..."

wait_for_alb_targets_healthy

echo "ALB health check completed."
