#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TEST_DIR="$(mktemp -d)"
trap 'rm -rf "$TEST_DIR"' EXIT

mkdir -p "${TEST_DIR}/bin"

cat >"${TEST_DIR}/bin/aws" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

[[ "$*" == *"rds modify-db-instance"* ]] || exit 1

json_file=""

while (($#)); do
  if [[ "$1" == "--cli-input-json" ]]; then
    json_file="${2#file://}"
    break
  fi
  shift
done

[[ "$json_file" == /dev/shm/aws-cli-json.* ]] || exit 1
[[ -f "$json_file" ]] || exit 1
[[ "$(stat -c '%a' "$json_file")" == "600" ]] || exit 1

jq -e '
  .DBInstanceIdentifier == "equipment-inspection-db" and
  .ApplyImmediately == true and
  (.MasterUserPassword | length == 48)
' "$json_file" >/dev/null

printf '{}\n'
EOF

chmod 700 "${TEST_DIR}/bin/aws"
export PATH="${TEST_DIR}/bin:${PATH}"

AWS_PROFILE="terraform-execution"
AWS_REGION="ap-northeast-1"
DB_INSTANCE_IDENTIFIER="equipment-inspection-db"

source "${SCRIPT_DIR}/../../lib/aws-cli-json.sh"
source "${SCRIPT_DIR}/../../lib/rds-password.sh"

TEST_PASSWORD="$(printf '%048d' 0)"

OUTPUT="$(modify_rds_master_password "$TEST_PASSWORD" 2>&1)"

[[ -z "$OUTPUT" ]] || {
  echo "FAIL: Unexpected output"
  exit 1
}

# AWS CLIが失敗した場合、関数も失敗を返すこと
cat >"${TEST_DIR}/bin/aws" <<'EOF'
#!/usr/bin/env bash
exit 1
EOF

chmod 700 "${TEST_DIR}/bin/aws"

if modify_rds_master_password "$TEST_PASSWORD" >/dev/null 2>&1; then
  echo "FAIL: RDS password modification succeeded despite AWS CLI failure"
  exit 1
fi

echo "PASS: RDS password failure propagation"

echo "PASS: RDS password handoff"
