#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

cd "$PROJECT_ROOT"

echo "=== 1. Shell syntax check ==="

bash -n \
  scripts/aws/destroy-aws-environment.sh \
  scripts/aws/restore-aws-environment.sh \
  scripts/lib/ecr-image.sh \
  scripts/tests/aws/destroy-aws-environment.integration.test.sh \
  scripts/tests/aws/restore-aws-environment.integration.test.sh \
  scripts/tests/aws/alb-health.integration.test.sh \
  scripts/tests/helpers/mock-aws.sh

echo "=== 2. AWS unit tests ==="
bash scripts/tests/aws/destroy-aws-environment.test.sh
bash scripts/tests/aws/rds-password.test.sh
bash scripts/tests/aws/ssm-password.test.sh
bash scripts/tests/aws/terraform-password.test.sh

echo "=== 3. Destroy integration tests ==="
bash scripts/tests/aws/destroy-aws-environment.integration.test.sh

echo "=== 4. Restore integration tests ==="
bash scripts/tests/aws/restore-aws-environment.integration.test.sh

echo "=== 5. ALB health integration tests ==="
bash scripts/tests/aws/alb-health.integration.test.sh

echo "=== 6. Terraform runtime validation ==="
terraform -chdir=terraform/runtime fmt -check -recursive
terraform -chdir=terraform/runtime validate

echo "=== 7. Terraform persistent validation ==="
terraform -chdir=terraform/persistent fmt -check -recursive
terraform -chdir=terraform/persistent validate

echo "=== 8. Git diff check ==="
git diff --check

echo "=== All AWS lifecycle checks passed ==="
