#!/usr/bin/env bash

set -euo pipefail

SKIP_SNAPSHOT=false

usage() {
  cat <<'EOF'
Usage: destroy-aws-environment.sh [OPTIONS]

Options:
  --skip-snapshot  Skip RDS snapshot creation before destroying AWS resources.
  --help           Show this help message.
EOF
}

confirm_destroy() {
  local confirmation

  echo "WARNING: This operation may cause data loss."
  echo "Type DESTROY to confirm."

  if ! read -r -p "Confirmation: " confirmation; then
    echo "Destroy cancelled."
    return 1
  fi

  if [[ "$confirmation" != "DESTROY" ]]; then
    echo "Destroy cancelled."
    return 1
  fi

  echo "Destroy confirmed."
  return 0
}

while [[ $# -gt 0 ]]; do
  case "$1" in
  --skip-snapshot)
    SKIP_SNAPSHOT=true
    shift
    ;;
  --help)
    usage
    exit 0
    ;;
  *)
    echo "ERROR: Unknown argument: $1" >&2
    usage >&2
    exit 1
    ;;
  esac
done

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
RUNTIME_DIR="${PROJECT_ROOT}/terraform/runtime"

AWS_PROFILE="terraform-execution"
AWS_REGION="ap-northeast-1"
EXPECTED_ACCOUNT_ID="339741260090"
EXPECTED_ROLE_NAME="terraform-execution-role"

# 必要なコマンドの存在確認
for command_name in aws terraform jq; do
  if ! command -v "$command_name" >/dev/null 2>&1; then
    echo "ERROR: ${command_name} is not installed." >&2
    exit 1
  fi
done

# Terraform実行ディレクトリの存在確認
if [[ ! -d "$RUNTIME_DIR" ]]; then
  echo "ERROR: Terraform runtime directory not found." >&2
  exit 1
fi

# AWSアカウントとIAMロールの確認
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
echo "Runtime directory: $RUNTIME_DIR"

# runtime Stateに保護対象が含まれていないことを確認
echo "Checking Terraform runtime state..."

RUNTIME_STATE_RESOURCES="$(
  terraform -chdir="$RUNTIME_DIR" state list
)"

PROTECTED_STATE_RESOURCES="$(
  grep -E '(^|\.)(aws_route53_zone|aws_sns_topic|aws_sns_topic_policy|aws_budgets_budget)\.' \
    <<<"$RUNTIME_STATE_RESOURCES" || true
)"

if [[ -n "$PROTECTED_STATE_RESOURCES" ]]; then
  echo "ERROR: Protected resources found in runtime state:" >&2
  echo "$PROTECTED_STATE_RESOURCES" >&2
  exit 1
fi

echo "Terraform runtime state protection check passed."

# Terraform Destroy Planの生成
PLAN_FILE="$(mktemp "${TMPDIR:-/tmp}/equipment-destroy.XXXXXXXX.tfplan")"
trap 'rm -f "$PLAN_FILE"' EXIT

echo "Generating Terraform destroy plan..."

terraform -chdir="$RUNTIME_DIR" plan \
  -destroy \
  -input=false \
  -out="$PLAN_FILE"

echo "Terraform destroy plan generated successfully."
echo "Plan file: $PLAN_FILE"
echo "WARNING: Terraform Destroy has not been executed."

# Terraform Destroy Planの安全性チェック
echo "Validating Terraform destroy plan..."

PLAN_JSON="$(terraform -chdir="$RUNTIME_DIR" show -json "$PLAN_FILE")"

# 削除以外の操作が含まれていないことを確認
UNEXPECTED_ACTIONS="$(
  jq -r '
    .resource_changes[]?
    | select(
        .change.actions != ["delete"] and
        .change.actions != ["no-op"]
      )
    | "\(.address): \(.change.actions | join(","))"
  ' <<<"$PLAN_JSON"
)"

if [[ -n "$UNEXPECTED_ACTIONS" ]]; then
  echo "ERROR: Unexpected resource actions detected:" >&2
  echo "$UNEXPECTED_ACTIONS" >&2
  exit 1
fi

# persistent側のリソースが含まれていないことを確認
PROTECTED_RESOURCES="$(
  jq -r '
    .resource_changes[]?
    | select(.change.actions | index("delete"))
    | select(
        .type == "aws_route53_zone" or
        .type == "aws_sns_topic" or
        .type == "aws_sns_topic_policy" or
        .type == "aws_budgets_budget"
      )
    | .address
  ' <<<"$PLAN_JSON"
)"

if [[ -n "$PROTECTED_RESOURCES" ]]; then
  echo "ERROR: Protected resources are included in the destroy plan:" >&2
  echo "$PROTECTED_RESOURCES" >&2
  exit 1
fi

# 削除対象件数を確認
DESTROY_COUNT="$(
  jq -r '
    [.resource_changes[]?
     | select(.change.actions == ["delete"])]
    | length
  ' <<<"$PLAN_JSON"
)"

echo "Terraform destroy plan validation passed."
echo "Resources to destroy: $DESTROY_COUNT"
echo "WARNING: Terraform Destroy has not been executed."

# 削除対象がない場合は正常終了
if [[ "$DESTROY_COUNT" -eq 0 ]]; then
  echo "No runtime resources to destroy."
  exit 0
fi

# 削除対象を表示
echo
echo "Resources scheduled for destruction:"

jq -r '
  .resource_changes[]?
  | select(.change.actions == ["delete"])
  | "  - \(.address)"
' <<<"$PLAN_JSON"

echo
echo "WARNING: The resources listed above will be destroyed."
echo "WARNING: This operation may cause data loss."

confirm_destroy || exit 1

# RDSの存在と状態を確認
DB_INSTANCE_IDENTIFIER="equipment-inspection-db"
DB_STATUS=""

DB_STATUS_OUTPUT="$(
  aws rds describe-db-instances \
    --profile "$AWS_PROFILE" \
    --region "$AWS_REGION" \
    --db-instance-identifier "$DB_INSTANCE_IDENTIFIER" \
    --query 'DBInstances[0].DBInstanceStatus' \
    --output text 2>&1
)" || {
  if [[ "$DB_STATUS_OUTPUT" == *"DBInstanceNotFound"* ]]; then
    DB_STATUS="not-found"
  else
    echo "ERROR: Failed to describe RDS instance." >&2
    echo "$DB_STATUS_OUTPUT" >&2
    exit 1
  fi
}

if [[ -z "${DB_STATUS:-}" ]]; then
  DB_STATUS="$DB_STATUS_OUTPUT"
fi

echo "RDS status: $DB_STATUS"

case "$DB_STATUS" in
available)
  echo "RDS is available."
  ;;
stopped)
  echo "RDS is stopped."
  ;;
not-found) ;;
*)
  echo "ERROR: RDS is in an unsupported state: $DB_STATUS" >&2
  exit 1
  ;;
esac

if [[ "$SKIP_SNAPSHOT" == false && "$DB_STATUS" == "stopped" ]]; then
  echo "Starting RDS instance: $DB_INSTANCE_IDENTIFIER"

  aws rds start-db-instance \
    --profile "$AWS_PROFILE" \
    --region "$AWS_REGION" \
    --db-instance-identifier "$DB_INSTANCE_IDENTIFIER" \
    --no-cli-pager >/dev/null

  echo "Waiting for RDS to become available..."

  aws rds wait db-instance-available \
    --profile "$AWS_PROFILE" \
    --region "$AWS_REGION" \
    --db-instance-identifier "$DB_INSTANCE_IDENTIFIER"

  DB_STATUS="available"
  echo "RDS is now available."
fi

# RDSスナップショットの作成
if [[ "$SKIP_SNAPSHOT" == true ]]; then
  echo "WARNING: RDS snapshot creation is explicitly skipped."
  echo "WARNING: RDS data may be permanently lost during Terraform Destroy."
elif [[ "$DB_STATUS" == "available" ]]; then
  SNAPSHOT_TIMESTAMP="$(date -u +%Y%m%d-%H%M%S)"
  SNAPSHOT_IDENTIFIER="${DB_INSTANCE_IDENTIFIER}-manual-${SNAPSHOT_TIMESTAMP}"

  echo "Creating RDS snapshot: $SNAPSHOT_IDENTIFIER"

  aws rds create-db-snapshot \
    --profile "$AWS_PROFILE" \
    --region "$AWS_REGION" \
    --db-instance-identifier "$DB_INSTANCE_IDENTIFIER" \
    --db-snapshot-identifier "$SNAPSHOT_IDENTIFIER" \
    --no-cli-pager >/dev/null

  echo "Waiting for RDS snapshot to become available..."

  aws rds wait db-snapshot-available \
    --profile "$AWS_PROFILE" \
    --region "$AWS_REGION" \
    --db-snapshot-identifier "$SNAPSHOT_IDENTIFIER"

  echo "RDS snapshot is available: $SNAPSHOT_IDENTIFIER"
elif [[ "$DB_STATUS" == "not-found" ]]; then
  echo "RDS does not exist. Snapshot creation skipped."
else
  echo "ERROR: Cannot create snapshot. RDS status: $DB_STATUS" >&2
  exit 1
fi

echo "Applying approved Terraform destroy plan..."

terraform -chdir="$RUNTIME_DIR" apply \
  -input=false \
  "$PLAN_FILE"

echo "Terraform runtime destroy completed successfully."
