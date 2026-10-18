#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

TEST_DIR="$(mktemp -d)"
trap 'rm -rf "$TEST_DIR"' EXIT

mkdir -p "${TEST_DIR}/bin"

cat >"${TEST_DIR}/bin/terraform" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

[[ -n "${TF_VAR_db_password:-}" ]] || exit 1
[[ "${#TF_VAR_db_password}" -eq 48 ]] || exit 1

printf 'MOCK_TERRAFORM_OK\n'
EOF

chmod 700 "${TEST_DIR}/bin/terraform"

export PATH="${TEST_DIR}/bin:${PATH}"
RUNTIME_DIR="${TEST_DIR}/runtime"

source "${SCRIPT_DIR}/lib/terraform-password.sh"

TEST_PASSWORD="$(printf '%048d' 0)"

OUTPUT="$(run_terraform_with_db_password "$TEST_PASSWORD" plan 2>&1)"

[[ "$OUTPUT" == "MOCK_TERRAFORM_OK" ]] || {
  echo "FAIL: Terraform invocation"
  exit 1
}

[[ "$OUTPUT" != *"$TEST_PASSWORD"* ]] || {
  echo "FAIL: Password leaked to output"
  exit 1
}

[[ -z "${TF_VAR_db_password:-}" ]] || {
  echo "FAIL: Password remained in parent environment"
  exit 1
}

echo "PASS: Terraform password handoff"
