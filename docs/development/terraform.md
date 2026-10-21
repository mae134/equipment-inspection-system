# Terraform

## 1. 目的

既存AWSインフラをTerraformで管理する。

## 2. 前提

- Terraform
- AWS CLI
- terraform-execution profile
- 認証方法
- IAM UserはTerraform管理対象外

## 3. ディレクトリ

terraform/\*.tf の役割

## 4. 基本操作

terraform init
terraform fmt
terraform validate
terraform plan
terraform apply

## 5. State管理

- terraform.tfstateはGit管理しない
- .terraform/もGit管理しない
- Stateには機密情報が含まれる可能性がある
- 現状はlocal state
- 将来remote stateを検討

## 6. 既存AWSリソース

importを使ってTerraform管理へ移行したこと

## 7. 削除・復元

AWS環境の削除・復元はIssue #99で実装した専用スクリプトを使用する。

Terraform Stateは以下のディレクトリで分離している。

- `terraform/persistent/`：Route53 Hosted Zoneなどの保持対象
- `terraform/runtime/`：削除・再構築するAWSリソース

### 環境削除（Destroy）

```bash
bash scripts/aws/destroy-aws-environment.sh
```

実行前にAWSアカウント・IAMロール・Terraform State・削除計画を確認する。

実行時には`DESTROY`の完全一致入力が必要となる。

原則としてRDS Manual Snapshotを作成し、利用可能状態を確認してからRuntime Resourcesを削除する。

`--skip-snapshot`を指定するとSnapshot作成を省略できるが、データ喪失のリスクがあるため慎重に使用する。

### 環境復元（Restore）

Snapshotから復元する場合：

```bash
bash scripts/aws/restore-aws-environment.sh \
  --snapshot-id SNAPSHOT_ID
```

新規RDSを作成する場合：

```bash
bash scripts/aws/restore-aws-environment.sh --new
```

標準ではTerraform Planの確認のみを行う。

実際に環境を再構築する場合は、確認後に`--apply`と`CONFIRM_AWS_RESTORE=YES`を指定する。

```bash
CONFIRM_AWS_RESTORE=YES \
  bash scripts/aws/restore-aws-environment.sh \
  --snapshot-id SNAPSHOT_ID \
  --apply
```

RestoreではTerraform Apply、RDS・SSMパスワード設定、Maven Build、ECR Push、ECS起動、ALB Target Health確認を実施する。

HTTPS疎通、DBデータ確認、SNSメール購読の再設定は手動で実施する。

### 注意事項

- 実行前に対象AWSアカウントとTerraform Stateを確認する。
- Terraform Stateには機密情報が含まれる可能性があるため、Gitへコミットしない。
- 異常終了時は自動Rollbackを行わない。AWSリソースとTerraform Stateの状態を確認してから再実行する。
- 詳細は`docs/design/aws-environment-lifecycle.md`を参照する。

## 8. 検証結果

terraform fmt
terraform validate
terraform plan → No changes
terraform apply → 成功
