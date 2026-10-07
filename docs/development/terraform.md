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

destroyはAWSリソースを実際に削除するので注意
完全な退避・停止・復元・疎通検証は別Issue

## 8. 検証結果

terraform fmt
terraform validate
terraform plan → No changes
terraform apply → 成功
