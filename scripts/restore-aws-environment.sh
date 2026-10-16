#!/usr/bin/env bash

set -euo pipefail

SNAPSHOT_ID=""
NEW_DB=false

usage() {
  cat <<'EOF'
Usage:
  restore-aws-environment.sh --snapshot-id SNAPSHOT_ID
  restore-aws-environment.sh --new

Options:
  --snapshot-id ID  Restore RDS from the specified snapshot.
  --new             Create a new RDS database.
  --help            Show this help message.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
  --new)
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
  *)
    echo "ERROR: Unknown argument: $1" >&2
    usage >&2
    exit 1
    ;;
  esac
done

if [[ "$NEW_DB" == true && -n "$SNAPSHOT_ID" ]]; then
  echo "ERROR: --new and --snapshot-id cannot be used together." >&2
  exit 1
fi

if [[ "$NEW_DB" == false && -z "$SNAPSHOT_ID" ]]; then
  echo "ERROR: Specify either --new or --snapshot-id." >&2
  usage >&2
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
RUNTIME_DIR="${PROJECT_ROOT}/terraform/runtime"
source "${SCRIPT_DIR}/lib/terraform-password.sh"

AWS_PROFILE="terraform-execution"
AWS_REGION="ap-northeast-1"
EXPECTED_ACCOUNT_ID="339741260090"
EXPECTED_ROLE_NAME="terraform-execution-role"
DB_INSTANCE_IDENTIFIER="equipment-inspection-db"

generate_db_password() {
  openssl rand -hex 24
}

run_terraform_with_db_password() {
  local db_password="$1"
  shift

  TF_VAR_db_password="$db_password" terraform \
    -chdir="$RUNTIME_DIR" "$@"
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
