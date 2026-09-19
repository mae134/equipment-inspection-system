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
