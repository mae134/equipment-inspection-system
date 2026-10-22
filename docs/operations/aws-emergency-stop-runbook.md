# AWS Emergency Stop Runbook

## 1. 目的

AWS利用料金に異常な増加が発生した場合に、
主要なコスト要因となるAWSリソースを停止し、
不要な料金の発生を抑えるための手順を定める。

本Runbookは、AWS BudgetsまたはCost Anomaly Detectionから
コスト異常の通知を受信した場合の緊急対応を対象とする。

---

## 2. 緊急対応の判断

以下の場合にAWS BillingおよびCost Explorerを確認する。

- AWS Budgetsから予算超過に関する通知を受信した場合
- Cost Anomaly Detectionから異常コストの通知を受信した場合
- 想定していない料金増加を確認した場合

料金増加の原因を確認し、継続稼働が不要と判断した場合は、
以下の停止手順を実施する。

---

## 3. 緊急停止手順

### 3.1 ECS Fargate

ECS ServiceのDesired Countを`0`に変更する。

対象:

- Cluster: `equipment-inspection-cluster`
- Service: `equipment-inspection-service`

変更後、Running Taskが`0`になったことを確認する。

---

### 3.2 RDS PostgreSQL

RDS DBインスタンスを一時停止する。

対象:

- DB Instance: `equipment-inspection-db`

停止後、DBインスタンスのステータスを確認する。

RDSの一時停止には最大停止期間があり、
一定期間経過後に自動的に再起動するため注意する。

---

### 3.3 Application Load Balancer

ALB構築後、長期間公開環境を使用しない場合は削除を検討する。

ALBはECSやRDSのような一時停止ができないため、
不要な期間の料金を停止する場合は削除が必要となる。

再作成に必要な設定を確認した上で削除する。

---

## 4. 停止後の確認

リソース停止後、以下を確認する。

- ECSのRunning Taskが`0`であること
- RDSが停止していること
- AWS Billing / Cost Explorerで料金推移を確認すること
- 異常コストの原因となったサービスを確認すること

なお、リソースを停止した場合でも、
ストレージやログなど一部の料金が継続して発生する可能性がある。

---

## 5. 復旧

AWS環境を再度利用する場合は、以下の順序で復旧する。

1. RDS PostgreSQLを起動する
2. RDSが利用可能になったことを確認する
3. ECS ServiceのDesired Countを`1`に変更する
4. ECS Taskが正常に起動したことを確認する
5. アプリケーションからRDSへ接続できることを確認する

ALBを削除している場合は、公開環境の構成に従って再作成する。

---

## 6. 注意事項

AWS BudgetsおよびCost Anomaly Detectionは、
料金のハードキャップや自動停止機能として使用しない。

通知を受信した後に利用状況を確認し、
必要に応じて本Runbookの手順を実施する。

---

## 7. 長期間使用しない場合の環境削除・復元

本Runbookの第3章は、料金異常発生時にAWSリソースを一時停止するための緊急対応を対象とする。

一方、長期間AWS環境を使用しない場合は、Issue #99で実装した環境削除・復元スクリプトを使用できる。



### 環境削除

```bash
bash scripts/aws/destroy-aws-environment.sh
```

AWSアカウント・IAMロール・Terraform State・削除計画の安全性確認後、`DESTROY`の入力を要求する。

原則としてRDS Manual Snapshotを作成し、`available`になったことを確認してからRuntime Resourcesを削除する。

### 環境復元

```bash
bash scripts/aws/restore-aws-environment.sh \
  --snapshot-id SNAPSHOT_ID
```

標準ではTerraform Planの確認のみを行う。

内容を確認した後、`--apply`と`CONFIRM_AWS_RESTORE=YES`を指定して復元する。

復元処理には、Terraformによる再構築、RDS・SSMパスワード設定、ECRへのイメージ登録、ECS起動、ALB Target Health確認が含まれる。

復元後はHTTPS疎通、DBデータ、SNSメール購読の状態を手動で確認する。

### 使い分け

- **緊急停止**：ECS・RDSを一時停止し、短期的な料金増加を抑える。
- **環境削除**：長期間使用しないRuntime Resourcesを削除し、継続的な料金を削減する。
- **環境復元**：削除したRuntime Resourcesを再構築してアプリケーションを再稼働する。

詳細は`docs/design/aws-environment-lifecycle.md`を参照する。

---

## 8. AWS手動操作の運用方針

通常のAWS環境削除・復元では、Issue #99で実装したDestroy / Restoreスクリプトを使用する。

ただし、障害調査や緊急停止・復旧など、スクリプトによる対応が困難な場合は、AWS Management ConsoleまたはAWS CLIによる手動操作を許容する。

### 8.1 手動操作を行う場合の原則

- 操作前にAWS Account、Region、対象リソースを確認する。
- 可能な限り操作前の状態と変更内容を記録する。
- 削除などの不可逆な操作では、必要なバックアップの有無を確認する。
- 操作後は対象リソースの状態とアプリケーションへの影響を確認する。
- Terraform管理対象を手動変更した場合は、Terraform Stateと実際のAWSリソースの差異を確認する。

### 8.2 Terraform管理との整合性

Terraform管理対象リソースをAWS Management ConsoleやAWS CLIで変更すると、Terraformの構成・Stateと実際のAWS環境に差異（Drift）が生じる可能性がある。

手動操作後は、必要に応じて以下を実行して差異を確認する。

```bash
terraform -chdir=terraform/runtime plan
```

差異が確認された場合は、Terraform構成の修正、Stateの調整、またはAWSリソースの状態修正を検討する。

差異を確認せずにTerraform ApplyやDestroyを実行しない。

### 8.3 運用改善

同じ手動操作が繰り返し必要になる場合は、スクリプトへの組み込みや運用手順の改善を検討する。

ただし、例外的な障害対応をすべて自動化することは目的としない。

通常運用の再現性と緊急時の対応柔軟性を両立する。
