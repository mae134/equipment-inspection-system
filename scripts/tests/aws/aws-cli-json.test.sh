
#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TEST_DIR="$(mktemp -d)"
trap 'rm -rf "$TEST_DIR"' EXIT

source "${SCRIPT_DIR}/../../lib/aws-cli-json.sh"

# 一時ファイルのパスを記録するMock
mock_aws_cli() {
  local exit_code="$1"
  shift

  local json_file=""

  while (($#)); do
    if [[ "$1" == "--cli-input-json" ]]; then
      [[ $# -ge 2 ]] || return 1
      json_file="${2#file://}"
      break
    fi
    shift
  done

  [[ "$json_file" == /dev/shm/aws-cli-json.* ]] || return 1
  [[ -f "$json_file" ]] || return 1
  [[ "$(stat -c '%a' "$json_file")" == "600" ]] || return 1

  jq -e '.test == "ok"' "$json_file" >/dev/null || return 1

  printf '%s\n' "$json_file" >"$TEST_DIR/last-json-path"

  return "$exit_code"
}

# 正常終了時に一時ファイルが削除されること
printf '{"test":"ok"}\n' |
  aws_cli_with_secure_json mock_aws_cli 0

json_file="$(cat "$TEST_DIR/last-json-path")"

[[ ! -e "$json_file" ]] || {
  echo "FAIL: Temporary file remains after success"
  exit 1
}

echo "PASS: Temporary file cleanup after success"

# AWS CLI失敗時も一時ファイルが削除されること
if printf '{"test":"ok"}\n' |
  aws_cli_with_secure_json mock_aws_cli 1; then
  echo "FAIL: AWS CLI failure was not propagated"
  exit 1
fi

json_file="$(cat "$TEST_DIR/last-json-path")"

[[ ! -e "$json_file" ]] || {
  echo "FAIL: Temporary file remains after failure"
  exit 1
}

echo "PASS: Temporary file cleanup after failure"
echo "PASS: AWS CLI failure propagation"
