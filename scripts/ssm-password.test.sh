#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TEST_DIR="$(mktemp -d)"
trap 'rm -rf "$TEST_DIR"' EXIT

mkdir -p "${TEST_DIR}/bin"

cat >"${TEST_DIR}/bin/aws" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

[[ "$*" == *"ssm put-parameter"* ]] || exit 1
[[ "$*" == *"--cli-input-json file:///dev/stdin"* ]] || exit 1

jq -e '
  .Name == "/equipment-inspection/prod/db/password" and
  .Type == "SecureString" and
  .Overwrite == true and
  (.Value | length == 48)
' >/dev/null

printf '{}\n'
EOF

chmod 700 "${TEST_DIR}/bin/aws"

export PATH="${TEST_DIR}/bin:${PATH}"

AWS_PROFILE="terraform-execution"
AWS_REGION="ap-northeast-1"

source "${SCRIPT_DIR}/lib/ssm-password.sh"

TEST_PASSWORD="$(printf '%048d' 0)"

OUTPUT="$(put_db_password_to_ssm "$TEST_PASSWORD" 2>&1)"

[[ -z "$OUTPUT" ]] || {
  echo "FAIL: Unexpected output"
  exit 1
}

echo "PASS: SSM password handoff"
