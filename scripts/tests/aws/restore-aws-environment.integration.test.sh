#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RESTORE_SCRIPT="${SCRIPT_DIR}/../../aws/restore-aws-environment.sh"
MOCK_AWS="${SCRIPT_DIR}/../helpers/mock-aws.sh"

TEST_DIR="$(mktemp -d)"
trap 'rm -rf "$TEST_DIR"' EXIT

mkdir -p "${TEST_DIR}/bin"

cat >"${TEST_DIR}/bin/terraform" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

[[ "${1:-}" == -chdir=* ]] || exit 1

# terraform show -json のモック
if [[ "${2:-}" == "show" ]]; then

  # Terraform showの失敗を再現
  if [[ "${MOCK_TERRAFORM_SHOW_FAIL:-false}" == true ]]; then
    echo "MOCK_TERRAFORM_SHOW_FAILED" >&2
    exit 1
  fi

  # 不正なTerraform plan JSONを再現
if [[ "${MOCK_TERRAFORM_INVALID_JSON:-false}" == true ]]; then
  printf '%s\n' '{"resource_changes":"invalid"}'
  exit 0
fi

  [[ "${3:-}" == "-json" ]] || exit 1
  [[ -f "${4:-}" ]] || exit 1

  if [[ "${MOCK_TERRAFORM_PLAN_DELETE:-false}" == true ]]; then
    printf '%s\n' '{"resource_changes":[{"change":{"actions":["delete","create"]}}]}'
  else
    printf '%s\n' '{"resource_changes":[{"change":{"actions":["create"]}}]}'
  fi

  exit 0
fi

# terraform apply のモック
if [[ "${2:-}" == "apply" ]]; then
  [[ $# -eq 3 ]] || exit 1
  [[ -f "${3:-}" ]] || exit 1
  [[ "$(cat "$3")" == "MOCK_PLAN" ]] || exit 1
  [[ "${TF_VAR_db_password:-}" =~ ^[0-9a-f]{48}$ ]] || exit 1

  if [[ "${MOCK_TERRAFORM_APPLY_FAIL:-false}" == true ]]; then
    echo "MOCK_TERRAFORM_APPLY_FAILED" >&2
    exit 1
  fi

  printf 'MOCK_TERRAFORM_APPLY_OK\n'
  exit 0
fi

[[ "${2:-}" == "plan" ]] || exit 1

[[ "${TF_VAR_db_password:-}" =~ ^[0-9a-f]{48}$ ]] || exit 1

# Terraform planの失敗を再現
if [[ "${MOCK_TERRAFORM_PLAN_FAIL:-false}" == true ]]; then
  echo "MOCK_TERRAFORM_PLAN_FAILED" >&2
  exit 1
fi

# -var-fileの指定を取得
vars_file=""

for arg in "$@"; do
  case "$arg" in
  -var-file=*)
    vars_file="${arg#-var-file=}"
    ;;
  esac
done

[[ -f "$vars_file" ]] || {
  echo "ERROR: Terraform variables file not found." >&2
  exit 1
}

# ECSは起動しない
jq -e '.ecs_desired_count == 0' "$vars_file" >/dev/null

# 新規作成・スナップショット復元の設定を検証
# 実行モードごとにスナップショット設定を検証
if [[ "${MOCK_EXPECT_NEW_DB:-false}" == true ]]; then
  jq -e '
    has("db_snapshot_identifier") and
    .db_snapshot_identifier == null
  ' "$vars_file" >/dev/null
else
  jq -e '
    .db_snapshot_identifier == "equipment-inspection-test-snapshot"
  ' "$vars_file" >/dev/null
fi

# -outで指定されたplanファイルを取得
plan_file=""

for arg in "$@"; do
  case "$arg" in
  -out=*)
    plan_file="${arg#-out=}"
    ;;
  esac
done

[[ -n "$plan_file" ]] || {
  echo "ERROR: Terraform plan output path is missing." >&2
  exit 1
}

# planファイルの保存先が一時ディレクトリ内か確認
[[ "$plan_file" == /tmp/*/restore.tfplan ]] || {
  echo "ERROR: Unexpected plan file path." >&2
  exit 1
}

# モックのplanファイルを生成
printf 'MOCK_PLAN\n' >"$plan_file"

printf 'MOCK_TERRAFORM_PLAN_OK\n'
EOF

# Maven Wrapperのモック
cat >"${TEST_DIR}/bin/mock-mvnw" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

[[ "$*" == "-B verify" ]] || exit 1

echo "MAVEN_VERIFY_CALLED" >>"$MOCK_LOG"

if [[ "${MOCK_MAVEN_FAIL:-false}" == true ]]; then
  echo "Simulated Maven build failure" >&2
  exit 1
fi

echo "MAVEN_VERIFY_OK" >>"$MOCK_LOG"
EOF

# Dockerのモック
cat >"${TEST_DIR}/bin/docker" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

case "${1:-}" in
build)
  echo "DOCKER_BUILD_CALLED" >>"$MOCK_LOG"

  if [[ "${MOCK_DOCKER_BUILD_FAIL:-false}" == true ]]; then
    exit 1
  fi
  ;;
login)
  [[ " $* " == *" --password-stdin "* ]] || exit 1

  # 認証情報をログに残さず標準入力を消費
  cat >/dev/null

  echo "DOCKER_LOGIN_OK" >>"$MOCK_LOG"

  if [[ "${MOCK_DOCKER_LOGIN_FAIL:-false}" == true ]]; then
    exit 1
  fi
  ;;
push)
  echo "DOCKER_PUSH_CALLED" >>"$MOCK_LOG"

  if [[ "${MOCK_DOCKER_PUSH_FAIL:-false}" == true ]]; then
    exit 1
  fi
  ;;
*)
  echo "Unexpected Docker command: $*" >&2
  exit 1
  ;;
esac
EOF

chmod 700 \
  "${TEST_DIR}/bin/mock-mvnw" \
  "${TEST_DIR}/bin/docker"

chmod 700 "${TEST_DIR}/bin/terraform"

ln -s "$MOCK_AWS" "${TEST_DIR}/bin/aws"

export PATH="${TEST_DIR}/bin:${PATH}"
export MOCK_LOG="${TEST_DIR}/aws.log"
export MVNW_COMMAND="${TEST_DIR}/bin/mock-mvnw"

SNAPSHOT_ID="equipment-inspection-test-snapshot"

run_test() {
  local name="$1"
  local expected_result="$2"
  shift 2

  : >"$MOCK_LOG"

  local output
  local status=0

  output="$(env "$@" bash "$RESTORE_SCRIPT" --snapshot-id "$SNAPSHOT_ID" 2>&1)" || status=$?

  if [[ "$expected_result" == "success" && "$status" -ne 0 ]]; then
    echo "FAIL: $name"
    echo "$output"
    exit 1
  fi

  if [[ "$expected_result" == "failure" && "$status" -eq 0 ]]; then
    echo "FAIL: $name (expected failure)"
    echo "$output"
    exit 1
  fi

  if [[ "$expected_result" == "success" ]] &&
    ! grep -q "Preflight checks completed" <<<"$output"; then
    echo "FAIL: $name (missing completion message)"
    echo "$output"
    exit 1
  fi

  if [[ "$expected_result" == "success" ]] &&
    ! grep -qx "SNAPSHOT_DESCRIBED" "$MOCK_LOG"; then
    echo "FAIL: $name (snapshot was not checked)"
    exit 1
  fi

  if [[ "$expected_result" == "success" ]] &&
    ! grep -Fq "MOCK_TERRAFORM_PLAN_OK" <<<"$output"; then
    echo "FAIL: $name (Terraform plan was not executed)"
    echo "$output"
    exit 1
  fi

  echo "PASS: $name"
}

# 1. 正常系
run_test \
  "Valid AWS identity and snapshot" \
  success

# 2. AWSアカウント不一致
run_test \
  "Unexpected AWS account was rejected" \
  failure \
  'MOCK_CALLER_IDENTITY={"Account":"000000000000","Arn":"arn:aws:sts::000000000000:assumed-role/terraform-execution-role/test"}'

# 3. IAMロール不一致
run_test \
  "Unexpected IAM role was rejected" \
  failure \
  'MOCK_CALLER_IDENTITY={"Account":"339741260090","Arn":"arn:aws:sts::339741260090:assumed-role/unexpected-role/test"}'

# 4. スナップショットがavailable以外
run_test \
  "Unavailable snapshot was rejected" \
  failure \
  'MOCK_RESTORE_SNAPSHOT_STATUS=creating'

# 5. スナップショットの元DBが違う
run_test \
  "Snapshot from another database was rejected" \
  failure \
  'MOCK_RESTORE_SOURCE_DB=another-database'

run_mode_test() {
  local name="$1"
  local expected_result="$2"
  local expected_message="$3"
  local expected_aws_calls="$4"
  shift 4

  : >"$MOCK_LOG"

  local output
  local status=0

  output="$(MOCK_EXPECT_NEW_DB=true bash "$RESTORE_SCRIPT" "$@" 2>&1)" || status=$?

  if [[ "$expected_result" == "success" && "$status" -ne 0 ]] ||
    [[ "$expected_result" == "failure" && "$status" -eq 0 ]]; then
    echo "FAIL: $name (exit status: $status)"
    echo "$output"
    exit 1
  fi

  if ! grep -Fq -- "$expected_message" <<<"$output"; then
    echo "FAIL: $name (unexpected output)"
    echo "$output"
    exit 1
  fi

  if [[ "$expected_aws_calls" == "none" && -s "$MOCK_LOG" ]]; then
    echo "FAIL: $name (unexpected AWS calls)"
    cat "$MOCK_LOG"
    exit 1
  fi

  if [[ "$expected_aws_calls" == "no_snapshot" ]] &&
    grep -q "SNAPSHOT_DESCRIBED" "$MOCK_LOG"; then
    echo "FAIL: $name (snapshot lookup was not expected)"
    exit 1
  fi

  if [[ "$expected_result" == "success" ]] &&
    ! grep -Fq "MOCK_TERRAFORM_PLAN_OK" <<<"$output"; then
    echo "FAIL: $name (Terraform plan was not executed)"
    echo "$output"
    exit 1
  fi

  echo "PASS: $name"
}

# 6. 新規作成モード
run_mode_test \
  "New database mode" \
  success \
  "New RDS creation mode selected." \
  no_snapshot \
  --new-db

# 7. オプションなし
run_mode_test \
  "Missing mode was rejected" \
  failure \
  "ERROR: Specify either --new-db or --snapshot-id." \
  none

# 8. 排他的なオプションの同時指定
run_mode_test \
  "Conflicting modes were rejected" \
  failure \
  "ERROR: --new-db and --snapshot-id cannot be used together." \
  none \
  --new-db --snapshot-id "$SNAPSHOT_ID"

# 9. Terraform plan失敗時は復元処理を中断する
output=""
status=0

output="$(
  MOCK_EXPECT_NEW_DB=true \
    MOCK_TERRAFORM_PLAN_FAIL=true \
    bash "$RESTORE_SCRIPT" --new-db 2>&1
)" || status=$?

if [[ "$status" -eq 0 ]] ||
  ! grep -Fq "MOCK_TERRAFORM_PLAN_FAILED" <<<"$output" ||
  grep -Fq "Terraform plan completed." <<<"$output"; then
  echo "FAIL: Terraform plan failure was not propagated"
  echo "$output"
  exit 1
fi

echo "PASS: Terraform plan failure was propagated"

# 10. 削除・置換を含むTerraform planを拒否する
output=""
status=0

output="$(
  MOCK_EXPECT_NEW_DB=true \
    MOCK_TERRAFORM_PLAN_DELETE=true \
    bash "$RESTORE_SCRIPT" --new-db 2>&1
)" || status=$?

if [[ "$status" -eq 0 ]] ||
  ! grep -Fq \
    "ERROR: Terraform plan contains resource deletion or replacement." \
    <<<"$output" ||
  grep -Fq "Terraform plan safety check passed." <<<"$output"; then
  echo "FAIL: Terraform plan deletion was not rejected"
  echo "$output"
  exit 1
fi

echo "PASS: Terraform plan deletion was rejected"

# 11. Terraform show失敗時は復元処理を中断する
output=""
status=0

output="$(
  MOCK_EXPECT_NEW_DB=true \
    MOCK_TERRAFORM_SHOW_FAIL=true \
    bash "$RESTORE_SCRIPT" --new-db 2>&1
)" || status=$?

if [[ "$status" -eq 0 ]] ||
  ! grep -Fq "MOCK_TERRAFORM_SHOW_FAILED" <<<"$output" ||
  grep -Fq "Terraform plan safety check passed." <<<"$output"; then
  echo "FAIL: Terraform show failure was not propagated"
  echo "$output"
  exit 1
fi

echo "PASS: Terraform show failure was propagated"

# 12. 不正なTerraform plan JSONを拒否する
output=""
status=0

output="$(
  MOCK_EXPECT_NEW_DB=true \
    MOCK_TERRAFORM_INVALID_JSON=true \
    bash "$RESTORE_SCRIPT" --new-db 2>&1
)" || status=$?

if [[ "$status" -eq 0 ]] ||
  ! grep -Fq "ERROR: Invalid Terraform plan JSON." <<<"$output" ||
  grep -Fq "Terraform plan safety check passed." <<<"$output"; then
  echo "FAIL: Invalid Terraform plan JSON was not rejected"
  echo "$output"
  exit 1
fi

echo "PASS: Invalid Terraform plan JSON was rejected"

# 13. --apply指定時に確認がなければ拒否する
output=""
status=0

output="$(
  env -u CONFIRM_AWS_RESTORE \
    MOCK_EXPECT_NEW_DB=true \
    bash "$RESTORE_SCRIPT" --new-db --apply 2>&1
)" || status=$?

if [[ "$status" -eq 0 ]] ||
  ! grep -Fq "ERROR: Set CONFIRM_AWS_RESTORE=YES" <<<"$output" ||
  grep -Fq "MOCK_TERRAFORM_APPLY_OK" <<<"$output"; then
  echo "FAIL: Terraform apply without confirmation was not rejected"
  echo "$output"
  exit 1
fi

echo "PASS: Terraform apply without confirmation was rejected"

# 14. 確認ありでTerraform applyが成功する
output=""
status=0

output="$(
  CONFIRM_AWS_RESTORE=YES \
    MOCK_EXPECT_NEW_DB=true \
    bash "$RESTORE_SCRIPT" --new-db --apply 2>&1
)" || status=$?

if [[ "$status" -ne 0 ]] ||
  ! grep -Fq "MOCK_TERRAFORM_APPLY_OK" <<<"$output" ||
  ! grep -Fq "Terraform apply completed." <<<"$output"; then
  echo "FAIL: Terraform apply with confirmation"
  echo "$output"
  exit 1
fi

echo "PASS: Terraform apply with confirmation"

# 15. Terraform applyの失敗を検出する
output=""
status=0

output="$(
  CONFIRM_AWS_RESTORE=YES \
    MOCK_EXPECT_NEW_DB=true \
    MOCK_TERRAFORM_APPLY_FAIL=true \
    bash "$RESTORE_SCRIPT" --new-db --apply 2>&1
)" || status=$?

if [[ "$status" -eq 0 ]] ||
  ! grep -Fq "MOCK_TERRAFORM_APPLY_FAILED" <<<"$output" ||
  grep -Fq "Terraform apply completed." <<<"$output"; then
  echo "FAIL: Terraform apply failure was not propagated"
  echo "$output"
  exit 1
fi

echo "PASS: Terraform apply failure was propagated"

# 16. スナップショット復元後にRDSパスワード更新が実行される
output=""
status=0

: >"$MOCK_LOG"

output="$(
  CONFIRM_AWS_RESTORE=YES \
    MOCK_RESTORE_RDS_WAIT=true \
    bash "$RESTORE_SCRIPT" \
    --snapshot-id equipment-inspection-test-snapshot \
    --apply 2>&1
)" || status=$?

if [[ "$status" -ne 0 ]] ||
  ! grep -Fxq "MOCK_RDS_MODIFY_OK" "$MOCK_LOG" ||
  ! grep -Fxq "RDS_PASSWORD_UPDATE_WAIT_COMPLETED" "$MOCK_LOG" ||
  ! grep -Fq "RDS master password update requested." <<<"$output"; then
  echo "FAIL: RDS password update after snapshot restoration"
  echo "$output"
  exit 1
fi

echo "PASS: RDS password update after snapshot restoration"

# 17. RDSパスワード更新失敗時に復元処理を中断する
output=""
status=0

: >"$MOCK_LOG"

output="$(
  CONFIRM_AWS_RESTORE=YES \
    MOCK_RDS_MODIFY_FAIL=true \
    bash "$RESTORE_SCRIPT" \
    --snapshot-id "$SNAPSHOT_ID" \
    --apply 2>&1
)" || status=$?

if [[ "$status" -eq 0 ]] ||
  ! grep -Fxq "MOCK_RDS_MODIFY_FAILED" "$MOCK_LOG" ||
  grep -Fq "RDS master password update requested." <<<"$output"; then
  echo "FAIL: RDS password update failure was not propagated"
  echo "$output"
  exit 1
fi

echo "PASS: RDS password update failure was propagated"

# 18. RDS待機失敗時に復元処理を中断する
output=""
status=0

: >"$MOCK_LOG"

output="$(
  CONFIRM_AWS_RESTORE=YES \
    MOCK_RESTORE_RDS_WAIT=true \
    MOCK_RDS_WAIT_FAIL=true \
    bash "$RESTORE_SCRIPT" \
    --snapshot-id "$SNAPSHOT_ID" \
    --apply 2>&1
)" || status=$?

if [[ "$status" -eq 0 ]] ||
  ! grep -Fxq "MOCK_RDS_MODIFY_OK" "$MOCK_LOG" ||
  ! grep -Fxq "RDS_WAIT_FAILED" "$MOCK_LOG" ||
  grep -Fxq "RDS_PASSWORD_UPDATE_WAIT_COMPLETED" "$MOCK_LOG" ||
  grep -Fq "RDS instance is available." <<<"$output"; then
  echo "FAIL: RDS waiter failure was not propagated"
  echo "$output"
  exit 1
fi

echo "PASS: RDS waiter failure was propagated"

# 19. スナップショット復元後にSSMパスワードが登録される
output=""
status=0

: >"$MOCK_LOG"

rm -f "${MOCK_LOG}.rds-password-hash"

output="$(
  CONFIRM_AWS_RESTORE=YES \
    MOCK_RESTORE_RDS_WAIT=true \
    bash "$RESTORE_SCRIPT" \
    --snapshot-id "$SNAPSHOT_ID" \
    --apply 2>&1
)" || status=$?

if [[ "$status" -ne 0 ]] ||
  ! grep -Fxq "SSM_PUT_OK" "$MOCK_LOG" ||
  ! grep -Fxq "RDS_SSM_PASSWORD_MATCH" "$MOCK_LOG" ||
  ! grep -Fq "SSM DB password update completed." <<<"$output"; then
  echo "FAIL: SSM password update after snapshot restoration"
  echo "$output"
  exit 1
fi

echo "PASS: SSM password update after snapshot restoration"

# 20. SSMパスワード登録失敗時に復元処理を中断する
output=""
status=0

: >"$MOCK_LOG"

rm -f "${MOCK_LOG}.rds-password-hash"

output="$(
  CONFIRM_AWS_RESTORE=YES \
    MOCK_RESTORE_RDS_WAIT=true \
    MOCK_SSM_PUT_FAIL=true \
    bash "$RESTORE_SCRIPT" \
    --snapshot-id "$SNAPSHOT_ID" \
    --apply 2>&1
)" || status=$?

if [[ "$status" -eq 0 ]] ||
  ! grep -Fxq "RDS_PASSWORD_UPDATE_WAIT_COMPLETED" "$MOCK_LOG" ||
  ! grep -Fxq "SSM_PUT_FAILED" "$MOCK_LOG" ||
  grep -Fxq "SSM_PUT_OK" "$MOCK_LOG" ||
  grep -Fq "SSM DB password update completed." <<<"$output"; then
  echo "FAIL: SSM password update failure was not propagated"
  echo "$output"
  exit 1
fi

echo "PASS: SSM password update failure was propagated"

# 21. SSM登録後にECSサービスの起動が要求される
output=""
status=0
: >"$MOCK_LOG"
rm -f "${MOCK_LOG}.rds-password-hash"

output="$(
  CONFIRM_AWS_RESTORE=YES \
    MOCK_RESTORE_RDS_WAIT=true \
    bash "$RESTORE_SCRIPT" \
    --snapshot-id "$SNAPSHOT_ID" \
    --apply 2>&1
)" || status=$?

if [[ "$status" -ne 0 ]] ||
  ! grep -Fxq "SSM_PUT_OK" "$MOCK_LOG" ||
  ! grep -Fxq "ECS_UPDATE_OK" "$MOCK_LOG" ||
  ! grep -Fq "ECS service start requested." <<<"$output"; then
  echo "FAIL: ECS service start after SSM update"
  echo "$output"
  exit 1
fi

# ECS起動がSSM登録より後であることを確認
ssm_line="$(grep -nFx "SSM_PUT_OK" "$MOCK_LOG" | cut -d: -f1)"
ecs_line="$(grep -nFx "ECS_UPDATE_OK" "$MOCK_LOG" | cut -d: -f1)"

if ((ecs_line <= ssm_line)); then
  echo "FAIL: ECS service started before SSM update"
  exit 1
fi

echo "PASS: ECS service start after SSM update"

# 22. ECS起動要求が失敗した場合は復元処理を中断する
output=""
status=0
: >"$MOCK_LOG"
rm -f "${MOCK_LOG}.rds-password-hash"

output="$(
  CONFIRM_AWS_RESTORE=YES \
    MOCK_RESTORE_RDS_WAIT=true \
    MOCK_ECS_UPDATE_FAIL=true \
    bash "$RESTORE_SCRIPT" \
    --snapshot-id "$SNAPSHOT_ID" \
    --apply 2>&1
)" || status=$?

if [[ "$status" -eq 0 ]] ||
  ! grep -Fxq "SSM_PUT_OK" "$MOCK_LOG" ||
  ! grep -Fxq "ECS_UPDATE_FAILED" "$MOCK_LOG" ||
  grep -Fxq "ECS_UPDATE_OK" "$MOCK_LOG" ||
  grep -Fq "ECS service start requested." <<<"$output"; then
  echo "FAIL: ECS service update failure was not propagated"
  echo "$output"
  exit 1
fi

echo "PASS: ECS service update failure was propagated"

# 23. ECSサービスの起動完了待機が成功する
output=""
status=0
: >"$MOCK_LOG"
rm -f "${MOCK_LOG}.rds-password-hash"

output="$(
  CONFIRM_AWS_RESTORE=YES \
    MOCK_RESTORE_RDS_WAIT=true \
    bash "$RESTORE_SCRIPT" \
    --snapshot-id "$SNAPSHOT_ID" \
    --apply 2>&1
)" || status=$?

if [[ "$status" -ne 0 ]] ||
  ! grep -Fxq "ECS_UPDATE_OK" "$MOCK_LOG" ||
  ! grep -Fxq "ECS_WAIT_COMPLETED" "$MOCK_LOG" ||
  ! grep -Fq "ECS service is stable." <<<"$output"; then
  echo "FAIL: ECS service stability wait"
  echo "$output"
  exit 1
fi

echo "PASS: ECS service stability wait"

# 24. ECSサービスの起動完了待機が失敗した場合は中断する
output=""
status=0
: >"$MOCK_LOG"
rm -f "${MOCK_LOG}.rds-password-hash"

output="$(
  CONFIRM_AWS_RESTORE=YES \
    MOCK_RESTORE_RDS_WAIT=true \
    MOCK_ECS_WAIT_FAIL=true \
    bash "$RESTORE_SCRIPT" \
    --snapshot-id "$SNAPSHOT_ID" \
    --apply 2>&1
)" || status=$?

if [[ "$status" -eq 0 ]] ||
  ! grep -Fxq "ECS_UPDATE_OK" "$MOCK_LOG" ||
  ! grep -Fxq "ECS_WAIT_FAILED" "$MOCK_LOG" ||
  grep -Fxq "ECS_WAIT_COMPLETED" "$MOCK_LOG" ||
  grep -Fq "ECS service is stable." <<<"$output"; then
  echo "FAIL: ECS service stability wait failure was not propagated"
  echo "$output"
  exit 1
fi

echo "PASS: ECS service stability wait failure was propagated"

# 25. ECS安定稼働後にALBヘルスチェックが成功する
output=""
status=0
: >"$MOCK_LOG"
rm -f "${MOCK_LOG}.rds-password-hash"

output="$(
  CONFIRM_AWS_RESTORE=YES \
    MOCK_RESTORE_RDS_WAIT=true \
    bash "$RESTORE_SCRIPT" \
    --snapshot-id "$SNAPSHOT_ID" \
    --apply 2>&1
)" || status=$?

if [[ "$status" -ne 0 ]] ||
  ! grep -Fxq "ECS_WAIT_COMPLETED" "$MOCK_LOG" ||
  ! grep -Fxq "ALB_HEALTH_CHECKED" "$MOCK_LOG" ||
  ! grep -Fq "ALB health check completed." <<<"$output"; then
  echo "FAIL: ALB health check after ECS stability"
  echo "$output"
  exit 1
fi

ecs_line="$(grep -nFx "ECS_WAIT_COMPLETED" "$MOCK_LOG" | cut -d: -f1)"
alb_line="$(grep -nFx "ALB_HEALTH_CHECKED" "$MOCK_LOG" | cut -d: -f1)"

if ((alb_line <= ecs_line)); then
  echo "FAIL: ALB health check ran before ECS stability"
  exit 1
fi

echo "PASS: ALB health check after ECS stability"

# 26. ALB API失敗時は復元処理を中断する
output=""
status=0
: >"$MOCK_LOG"
rm -f "${MOCK_LOG}.rds-password-hash"

output="$(
  CONFIRM_AWS_RESTORE=YES \
    MOCK_RESTORE_RDS_WAIT=true \
    MOCK_ALB_HEALTH_STATUS=api-failure \
    bash "$RESTORE_SCRIPT" \
    --snapshot-id "$SNAPSHOT_ID" \
    --apply 2>&1
)" || status=$?

if [[ "$status" -eq 0 ]] ||
  ! grep -Fxq "ECS_WAIT_COMPLETED" "$MOCK_LOG" ||
  ! grep -Fxq "ALB_HEALTH_CHECKED" "$MOCK_LOG" ||
  ! grep -Fq "Simulated ALB API failure" <<<"$output" ||
  grep -Fq "ALB health check completed." <<<"$output"; then
  echo "FAIL: ALB API failure was not propagated"
  echo "$output"
  exit 1
fi

echo "PASS: ALB API failure was propagated"

# 27. 復元途中の異常終了時に復旧案内を表示し、自動削除しない
output=""
status=0
: >"$MOCK_LOG"
rm -f "${MOCK_LOG}.rds-password-hash"

output="$(
  CONFIRM_AWS_RESTORE=YES \
    MOCK_RESTORE_RDS_WAIT=true \
    MOCK_ALB_HEALTH_STATUS=api-failure \
    bash "$RESTORE_SCRIPT" \
    --snapshot-id "$SNAPSHOT_ID" \
    --apply 2>&1
)" || status=$?

if [[ "$status" -eq 0 ]] ||
  ! grep -Fxq "ECS_WAIT_COMPLETED" "$MOCK_LOG" ||
  ! grep -Fxq "ALB_HEALTH_CHECKED" "$MOCK_LOG" ||
  ! grep -Fq "ERROR: AWS environment restoration failed." <<<"$output" ||
  ! grep -Fq "Existing AWS resources were not automatically rolled back." <<<"$output" ||
  ! grep -Fq "Before retrying, inspect Terraform state and AWS resource status." <<<"$output" ||
  grep -Fq "ALB health check completed." <<<"$output"; then
  echo "FAIL: Restore failure recovery guidance"
  echo "$output"
  exit 1
fi

echo "PASS: Restore failure recovery guidance"

# 28〜33. ECRイメージのビルド・Pushと失敗時の停止を検証
run_ecr_test() {
  local name="$1"
  local failure_flag="${2:-}"
  local expected_result="${3:-failure}"
  local output=""
  local status=0

  : >"$MOCK_LOG"
  rm -f "${MOCK_LOG}.rds-password-hash"

  if [[ -n "$failure_flag" ]]; then
    output="$(
      env \
        CONFIRM_AWS_RESTORE=YES \
        MOCK_RESTORE_RDS_WAIT=true \
        "$failure_flag=true" \
        bash "$RESTORE_SCRIPT" \
        --snapshot-id "$SNAPSHOT_ID" \
        --apply 2>&1
    )" || status=$?
  else
    output="$(
      CONFIRM_AWS_RESTORE=YES \
        MOCK_RESTORE_RDS_WAIT=true \
        bash "$RESTORE_SCRIPT" \
        --snapshot-id "$SNAPSHOT_ID" \
        --apply 2>&1
    )" || status=$?
  fi

  if [[ "$expected_result" == "success" && "$status" -ne 0 ]] ||
    [[ "$expected_result" == "failure" && "$status" -eq 0 ]]; then
    echo "FAIL: $name"
    echo "$output"
    exit 1
  fi

  if [[ "$expected_result" == "failure" ]] &&
    grep -Fxq "ECS_UPDATE_OK" "$MOCK_LOG"; then
    echo "FAIL: $name (ECS started after ECR failure)"
    exit 1
  fi

  echo "PASS: $name"
}

# 正常系：ECR PushがECS起動より先に完了する
run_ecr_test "ECR build and push succeeded" "" "success"

expected_steps=(
  "MAVEN_VERIFY_OK"
  "DOCKER_BUILD_CALLED"
  "ECR_LOGIN_PASSWORD_REQUESTED"
  "DOCKER_LOGIN_OK"
  "DOCKER_PUSH_CALLED"
  "ECS_UPDATE_OK"
)

previous_line=0

for step in "${expected_steps[@]}"; do
  line="$(grep -nFx "$step" "$MOCK_LOG" | head -n 1 | cut -d: -f1)"

  if [[ -z "$line" ]] || ((line <= previous_line)); then
    echo "FAIL: ECR build and push order ($step)"
    exit 1
  fi

  previous_line="$line"
done

echo "PASS: ECR build and push order"

# 異常系：各処理の失敗がECS起動を阻止する
run_ecr_test "Maven failure stops restoration" "MOCK_MAVEN_FAIL"
run_ecr_test "Docker build failure stops restoration" "MOCK_DOCKER_BUILD_FAIL"
run_ecr_test "ECR authentication failure stops restoration" "MOCK_ECR_AUTH_FAIL"
run_ecr_test "Docker login failure stops restoration" "MOCK_DOCKER_LOGIN_FAIL"
run_ecr_test "Docker push failure stops restoration" "MOCK_DOCKER_PUSH_FAIL"

echo "All restore integration tests passed."
