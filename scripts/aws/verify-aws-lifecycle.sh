#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

cd "$PROJECT_ROOT"

# AWS環境の停止・復元に関連するShellスクリプトの構文を検証する。
# 実行はせず、構文エラーの有無のみを確認する。
echo "=== 1. Shell syntax check ==="

bash -n \
  scripts/aws/destroy-aws-environment.sh \
  scripts/aws/restore-aws-environment.sh \
  scripts/lib/aws-cli-json.sh \
  scripts/lib/rds-password.sh \
  scripts/lib/ssm-password.sh \
  scripts/lib/ecr-image.sh \
  scripts/tests/aws/aws-cli-json.test.sh \
  scripts/tests/aws/destroy-aws-environment.integration.test.sh \
  scripts/tests/aws/restore-aws-environment.integration.test.sh \
  scripts/tests/aws/alb-health.integration.test.sh \
  scripts/tests/helpers/mock-aws.sh

# AWS操作に関する共通処理・個別処理を単体テストで検証する。
# AWS CLIのMockを利用するため、実際のAWSリソースは変更しない。
echo "=== 2. AWS unit tests ==="

bash scripts/tests/aws/destroy-aws-environment.test.sh

# 一時JSONファイルの生成、権限、正常時・異常時の削除を検証する。
bash scripts/tests/aws/aws-cli-json.test.sh

# RDS・SSMのパスワード受け渡しと失敗時のエラー伝播を検証する。
bash scripts/tests/aws/rds-password.test.sh
bash scripts/tests/aws/ssm-password.test.sh

# Terraformへ渡すDBパスワードの処理を検証する。
bash scripts/tests/aws/terraform-password.test.sh

# AWS環境停止処理の実行順序、異常系、失敗時の動作を検証する。
echo "=== 3. Destroy integration tests ==="
bash scripts/tests/aws/destroy-aws-environment.integration.test.sh

# AWS環境復元処理の実行順序、異常系、失敗時の動作を検証する。
echo "=== 4. Restore integration tests ==="
bash scripts/tests/aws/restore-aws-environment.integration.test.sh

# ALBのヘルスチェック処理と異常系を検証する。
echo "=== 5. ALB health integration tests ==="
bash scripts/tests/aws/alb-health.integration.test.sh

# Terraform runtime構成のフォーマットと設定の妥当性を検証する。
# plan/applyは実行せず、AWSリソースも変更しない。
echo "=== 6. Terraform runtime validation ==="
terraform -chdir=terraform/runtime fmt -check -recursive
terraform -chdir=terraform/runtime validate

# Terraform persistent構成のフォーマットと設定の妥当性を検証する。
echo "=== 7. Terraform persistent validation ==="
terraform -chdir=terraform/persistent fmt -check -recursive
terraform -chdir=terraform/persistent validate

# Git差分に不要な空白や改行などの問題がないか確認する。
echo "=== 8. Git diff check ==="
git diff --check

echo "=== All AWS lifecycle checks passed ==="
