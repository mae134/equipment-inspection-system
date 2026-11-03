# AWS Emergency Stop Runbook

## 1. 目的

AWS利用料金に異常な増加が発生した場合に、主要なコスト要因となるAWSリソースを停止し、不要な料金の発生を抑えるための手順を定める。

本Runbookは、AWS BudgetsまたはCost Anomaly Detectionからコスト異常の通知を受信した場合の緊急対応を対象とする。

また、AWS環境に障害が発生した際の基本的な状態確認・原因調査・復旧手順も記載する。

---

## 2. 緊急対応の判断

以下の場合にAWS BillingおよびCost Explorerを確認する。

- AWS Budgetsから予算超過に関する通知を受信した場合
- Cost Anomaly Detectionから異常コストの通知を受信した場合
- 想定していない料金増加を確認した場合
- ECS、RDS、ALBなどのAWSリソースに異常が発生した場合

料金増加の原因を確認し、継続稼働が不要と判断した場合は、第3章の停止手順を実施する。

### 2.1 AWS認証先の確認

AWS CLIによる操作前に、対象アカウントとIAM Roleを確認する。

```bash
aws sts get-caller-identity
```

- AWS CLIの認証設定が事前に完了していること。
- 想定するAWS AccountおよびIAM Roleであること。
- AWS Regionは原則として `ap-northeast-1` を使用する。
- 認証先が不明、または想定と異なる場合は操作を中止する。
- 認証情報やアクセスキーをドキュメントに記載しない。

なお、既存の環境削除・復元スクリプトは独自の認証設定を使用するため、AWS CLIのデフォルト認証先と一致するとは限らない。

### 2.2 コスト異常の初期調査

AWS BillingおよびCost Explorerで以下を確認する。

1. 料金が増加し始めた日時
2. 料金が増加しているAWSサービス
3. 対象Region
4. 増加している使用量または課金項目
5. 想定した運用による料金かどうか

主な確認対象は以下とする。

| AWSサービス | 主な確認項目                          |
| ----------- | ------------------------------------- |
| ECS Fargate | Running Task数、稼働時間              |
| RDS         | DBインスタンスの稼働状態、ストレージ  |
| ALB         | ロードバランサーの存在、稼働時間、LCU |
| NAT Gateway | Gatewayの存在、稼働時間、処理データ量 |
| CloudWatch  | Logsの保存量、ログ取り込み量          |
| WAF         | Web ACL、ルール、リクエスト数         |
| ECR         | 保存イメージ、ストレージ使用量        |

原因が不明な場合は、停止操作の前に第9章の調査コマンドを使用する。

---

## 3. 緊急停止手順

本章のコマンドはAWSリソースの状態を変更する。

実行前に第2.1節の認証確認を行い、対象リソースが存在することを確認する。

また、停止によってアプリケーションが利用できなくなるため、必要に応じて停止時刻と操作内容を記録する。

### 3.1 ECS Fargate

ECS ServiceのDesired Countを `0` に変更する。

対象：

- Cluster: `equipment-inspection-cluster`
- Service: `equipment-inspection-service`

#### 停止前確認

```bash
aws ecs describe-services \
  --region ap-northeast-1 \
  --cluster equipment-inspection-cluster \
  --services equipment-inspection-service \
  --query '{Services:services[*].{Status:status,Desired:desiredCount,Running:runningCount,Pending:pendingCount},Failures:failures}' \
  --output json
```

対象サービスが存在し、想定するClusterとServiceであることを確認する。

#### 停止実行

```bash
aws ecs update-service \
  --region ap-northeast-1 \
  --cluster equipment-inspection-cluster \
  --service equipment-inspection-service \
  --desired-count 0
```

#### 停止完了待機

```bash
aws ecs wait services-stable \
  --region ap-northeast-1 \
  --cluster equipment-inspection-cluster \
  --services equipment-inspection-service
```

#### 停止後確認

```bash
aws ecs describe-services \
  --region ap-northeast-1 \
  --cluster equipment-inspection-cluster \
  --services equipment-inspection-service \
  --query '{Services:services[*].{Status:status,Desired:desiredCount,Running:runningCount,Pending:pendingCount},Failures:failures}' \
  --output json
```

期待値：

- `Status`: `ACTIVE`
- `Desired`: `0`
- `Running`: `0`
- `Pending`: `0`
- `Failures`: 空配列

ECS ServiceのDesired Countを `0` にしても、ALBやNAT Gatewayなどの料金は停止しない。

### 3.2 RDS PostgreSQL

RDS DBインスタンスを一時停止する。

対象：

- DB Instance: `equipment-inspection-db`

#### 停止前確認

```bash
aws rds describe-db-instances \
  --region ap-northeast-1 \
  --db-instance-identifier equipment-inspection-db \
  --query 'DBInstances[0].[DBInstanceIdentifier,DBInstanceStatus]' \
  --output table
```

DBインスタンスが存在し、停止可能な状態であることを確認する。

#### 停止実行

```bash
aws rds stop-db-instance \
  --region ap-northeast-1 \
  --db-instance-identifier equipment-inspection-db
```

#### 停止完了待機

```bash
aws rds wait db-instance-stopped \
  --region ap-northeast-1 \
  --db-instance-identifier equipment-inspection-db
```

#### 停止後確認

```bash
aws rds describe-db-instances \
  --region ap-northeast-1 \
  --db-instance-identifier equipment-inspection-db \
  --query 'DBInstances[0].[DBInstanceIdentifier,DBInstanceStatus]' \
  --output table
```

期待値：

- DB Instance: `equipment-inspection-db`
- Status: `stopped`

#### 注意事項

- RDSの停止可能期間は原則として最大7日間。
- 停止期間の上限に達すると、AWSによって自動的に起動される。
- 停止中もストレージやバックアップなどの料金が発生する場合がある。
- DBが存在しない場合や停止できない状態の場合、停止コマンドは失敗することがある。
- 長期間使用しない場合は、第7章の環境削除を検討する。

### 3.3 Application Load Balancer

ALBはECSやRDSのような一時停止ができない。

長期間公開環境を使用しない場合は、ALBの削除を検討する。

ただし、ALBはTerraform管理対象であるため、原則としてAWS CLIによる単独削除は行わない。

再構築が必要な場合は、第7章の環境削除・復元スクリプトを使用する。

緊急時に手動削除が必要となった場合は、Terraform Stateとの整合性を確認し、操作内容を記録する。

---

## 4. 停止後の確認

### 4.1 ECSの停止状態

```bash
aws ecs describe-services \
  --region ap-northeast-1 \
  --cluster equipment-inspection-cluster \
  --services equipment-inspection-service \
  --query '{Services:services[*].{Desired:desiredCount,Running:runningCount,Pending:pendingCount},Failures:failures}' \
  --output json
```

`Desired`、`Running`、`Pending` がすべて `0` であることを確認する。

サービスが存在しない場合は、正常停止とは区別して扱う。

### 4.2 RDSの停止状態

```bash
aws rds describe-db-instances \
  --region ap-northeast-1 \
  --db-instance-identifier equipment-inspection-db \
  --query 'DBInstances[0].DBInstanceStatus' \
  --output text
```

期待値：

```text
stopped
```

`DBInstanceNotFound` が返された場合は、停止ではなくDBインスタンスが存在しない状態である。

### 4.3 AWS利用料金

AWS BillingおよびCost Explorerで以下を確認する。

- ECS Fargateの追加稼働料金が発生していないこと
- RDSのコンピューティング料金が停止状態に応じて変化していること
- ALB、NAT Gateway、CloudWatchなどの継続課金
- 異常コストの原因となったサービスの使用量

BillingおよびCost Explorerのデータ反映には遅延があるため、停止直後に料金が変化しない場合がある。

また、リソース停止後もストレージ、ログ、スナップショットなどの料金が継続する可能性がある。

---

## 5. 復旧

本章は、ECS ServiceとRDS DBインスタンスが存在し、一時停止している場合の復旧手順を対象とする。

TerraformによってAWS環境を削除済みの場合は、第7章のRestoreスクリプトを使用する。

復旧前にAWS認証先と対象リソースを確認する。

### 5.1 RDS PostgreSQLの起動

```bash
aws rds start-db-instance \
  --region ap-northeast-1 \
  --db-instance-identifier equipment-inspection-db
```

### 5.2 RDS起動完了待機

```bash
aws rds wait db-instance-available \
  --region ap-northeast-1 \
  --db-instance-identifier equipment-inspection-db
```

状態確認：

```bash
aws rds describe-db-instances \
  --region ap-northeast-1 \
  --db-instance-identifier equipment-inspection-db \
  --query 'DBInstances[0].DBInstanceStatus' \
  --output text
```

期待値：

```text
available
```

RDSが利用可能になったことを確認してからECSを起動する。

### 5.3 ECS Serviceの起動

```bash
aws ecs update-service \
  --region ap-northeast-1 \
  --cluster equipment-inspection-cluster \
  --service equipment-inspection-service \
  --desired-count 1
```

### 5.4 ECS安定稼働待機

```bash
aws ecs wait services-stable \
  --region ap-northeast-1 \
  --cluster equipment-inspection-cluster \
  --services equipment-inspection-service
```

状態確認：

```bash
aws ecs describe-services \
  --region ap-northeast-1 \
  --cluster equipment-inspection-cluster \
  --services equipment-inspection-service \
  --query '{Services:services[*].{Status:status,Desired:desiredCount,Running:runningCount,Pending:pendingCount},Failures:failures}' \
  --output json
```

期待値：

- `Status`: `ACTIVE`
- `Desired`: `1`
- `Running`: `1`
- `Pending`: `0`
- `Failures`: 空配列

### 5.5 ALB Target Health確認

```bash
TG_ARN="$(
  aws elbv2 describe-target-groups \
    --region ap-northeast-1 \
    --names equipment-inspection-tg \
    --query 'TargetGroups[0].TargetGroupArn' \
    --output text
)" || exit 1

if [[ -z "$TG_ARN" || "$TG_ARN" == "None" ]]; then
  echo "ERROR: Target Group not found." >&2
else
  aws elbv2 describe-target-health \
    --region ap-northeast-1 \
    --target-group-arn "$TG_ARN" \
    --query 'TargetHealthDescriptions[*].[Target.Id,TargetHealth.State,TargetHealth.Reason]' \
    --output table
fi
```

登録されたターゲットが1件以上存在し、すべて `healthy` であることを確認する。

### 5.6 HTTPS疎通確認

```bash
curl -I https://kdevnest.com/login
```

期待値：

- HTTPステータス `200`
- HTTPS接続が成功すること

`HEAD` リクエストがアプリケーション側で正常に処理されない場合は、GETリクエストで確認する。

```bash
curl -sS -o /dev/null -w '%{http_code}\n' \
  https://kdevnest.com/login
```

### 5.7 アプリケーション確認

- ログイン画面が表示されること
- Spring Boot Applicationが正常に起動していること
- RDSへの接続が成功すること
- 必要なデータが保持されていること
- CloudWatch Logsに重大なエラーが継続していないこと

ALBを削除している場合は、Lifecycleの手順に従って環境を再構築する。

---

## 6. 注意事項

AWS BudgetsおよびCost Anomaly Detectionは、料金のハードキャップや自動停止機能として使用しない。

通知を受信した後に利用状況を確認し、必要に応じて本Runbookの手順を実施する。

また、以下の点に注意する。

- ECS停止だけではALBやNAT Gatewayの料金は停止しない。
- RDS停止中も一部の料金は継続する。
- RDSは停止期間の上限に達すると自動起動する。
- 緊急停止はAWS環境全体の削除を意味しない。
- 課金異常の原因がECS・RDS以外の場合、それらを停止しても異常料金が解消するとは限らない。
- AWS CLIの変更系コマンドを実行する前に、対象Account、Region、リソースを確認する。

---

## 7. 長期間使用しない場合の環境削除・復元

本Runbookの第3章は、料金異常発生時にAWSリソースを一時停止するための緊急対応を対象とする。

一方、長期間AWS環境を使用しない場合は、Issue #99で実装した環境削除・復元スクリプトを使用できる。

### 環境削除

```bash
bash scripts/aws/destroy-aws-environment.sh
```

AWSアカウント・IAM Role・Terraform State・削除計画の安全性確認後、`DESTROY` の入力を要求する。

原則としてRDS Manual Snapshotを作成し、`available` になったことを確認してからRuntime Resourcesを削除する。

### 環境復元

```bash
bash scripts/aws/restore-aws-environment.sh \
  --snapshot-id SNAPSHOT_ID
```

標準ではTerraform Planの確認のみを行う。

内容を確認した後、`--apply` と `CONFIRM_AWS_RESTORE=YES` を指定して復元する。

復元処理には、Terraformによる再構築、RDS・SSMパスワード設定、ECRへのイメージ登録、ECS起動、ALB Target Health確認が含まれる。

復元後はHTTPS疎通、DBデータ、SNSメール購読の状態を手動で確認する。

### 使い分け

- **緊急停止**：ECS・RDSを一時停止し、短期的な料金増加を抑える。
- **環境削除**：長期間使用しないRuntime Resourcesを削除し、継続的な料金を削減する。
- **環境復元**：削除したRuntime Resourcesを再構築してアプリケーションを再稼働する。

詳細は `docs/design/aws-environment-lifecycle.md` を参照する。

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

なお、Terraform Planは設定によって一部の変更を無視する場合があるため、手動操作の記録とAWS CLIによる状態確認も併用する。

### 8.3 運用改善

同じ手動操作が繰り返し必要になる場合は、スクリプトへの組み込みや運用手順の改善を検討する。

ただし、例外的な障害対応をすべて自動化することは目的としない。

通常運用の再現性と緊急時の対応柔軟性を両立する。

---

## 9. 障害発生時の重要確認コマンド

本章は、ECS・RDS・ALB・CloudWatch Logsなどに異常が発生した際の基本的な調査手順をまとめる。

原則として読み取り専用のAWS CLIコマンドを使用する。

AWS環境をDestroy済みの場合、対象リソースが存在しないエラーは想定される結果であり、稼働中の障害とは区別する。

### 9.1 AWS認証先

```bash
aws sts get-caller-identity
```

想定するAWS AccountおよびIAM Roleであることを確認する。

### 9.2 ECS Serviceの状態

```bash
aws ecs describe-services \
  --region ap-northeast-1 \
  --cluster equipment-inspection-cluster \
  --services equipment-inspection-service \
  --query '{Services:services[*].{Status:status,Desired:desiredCount,Running:runningCount,Pending:pendingCount},Failures:failures}' \
  --output json
```

確認項目：

- Serviceが存在するか
- Desired CountとRunning Countが一致しているか
- Pending Taskが残っていないか
- `failures` にエラーが含まれていないか

### 9.3 ECS Serviceの直近イベント

```bash
aws ecs describe-services \
  --region ap-northeast-1 \
  --cluster equipment-inspection-cluster \
  --services equipment-inspection-service \
  --query 'services[0].events[0:10].[createdAt,message]' \
  --output table
```

タスクの起動失敗、デプロイ失敗、ALB Health Checkなどに関連するイベントを確認する。

### 9.4 ECS停止タスクの調査

停止したタスクのARNを確認する。

```bash
aws ecs list-tasks \
  --region ap-northeast-1 \
  --cluster equipment-inspection-cluster \
  --service-name equipment-inspection-service \
  --desired-status STOPPED \
  --query 'taskArns' \
  --output json
```

取得したタスクARNを指定して詳細を確認する。

```bash
aws ecs describe-tasks \
  --region ap-northeast-1 \
  --cluster equipment-inspection-cluster \
  --tasks TASK_ARN \
  --query 'tasks[*].{Task:taskArn,Status:lastStatus,StopCode:stopCode,StoppedReason:stoppedReason,Containers:containers[*].{Name:name,ExitCode:exitCode,Reason:reason}}' \
  --output json
```

`TASK_ARN` は実際のタスクARNに置き換える。

主な確認項目：

- `stopCode`
- `stoppedReason`
- コンテナの `exitCode`
- コンテナの `reason`

停止タスクの情報は無期限に取得できるわけではないため、時間が経過した障害はCloudWatch LogsやECSイベントも確認する。

### 9.5 RDSの状態

```bash
aws rds describe-db-instances \
  --region ap-northeast-1 \
  --db-instance-identifier equipment-inspection-db \
  --query 'DBInstances[0].[DBInstanceIdentifier,DBInstanceStatus]' \
  --output table
```

主な状態：

- `available`：利用可能
- `starting`：起動中
- `stopping`：停止中
- `stopped`：停止済み
- `modifying`：設定変更中

`DBInstanceNotFound` の場合は、対象DBインスタンスが存在しない。

### 9.6 ALB Target Health

```bash
TG_ARN="$(
  aws elbv2 describe-target-groups \
    --region ap-northeast-1 \
    --names equipment-inspection-tg \
    --query 'TargetGroups[0].TargetGroupArn' \
    --output text
)" || exit 1

if [[ -z "$TG_ARN" || "$TG_ARN" == "None" ]]; then
  echo "ERROR: Target Group not found." >&2
else
  aws elbv2 describe-target-health \
    --region ap-northeast-1 \
    --target-group-arn "$TG_ARN" \
    --query 'TargetHealthDescriptions[*].{Target:Target.Id,State:TargetHealth.State,Reason:TargetHealth.Reason,Description:TargetHealth.Description}' \
    --output json
fi
```

確認項目：

- Targetが登録されているか
- `healthy` になっているか
- `unhealthy` の理由
- Health Checkの失敗内容

Target Groupが存在しない場合は、AWS環境がDestroy済みでないか確認する。

### 9.7 CloudWatch Logs

直近30分のアプリケーションログを確認する。

```bash
aws logs tail /ecs/equipment-inspection-task \
  --region ap-northeast-1 \
  --since 30m
```

リアルタイムでログを監視する場合：

```bash
aws logs tail /ecs/equipment-inspection-task \
  --region ap-northeast-1 \
  --since 30m \
  --follow
```

`--follow` は `Ctrl+C` で終了する。

主な確認項目：

- Spring Bootの起動エラー
- PostgreSQL接続エラー
- SSM Parameter取得に関連するエラー
- アプリケーション例外
- コンテナの異常終了に関連するログ

CloudWatch Logsに機密情報が含まれる可能性があるため、外部共有時は内容を確認する。

### 9.8 HTTPS疎通

```bash
curl -I https://kdevnest.com/login
```

必要に応じてGETリクエストで確認する。

```bash
curl -sS -o /dev/null -w '%{http_code}\n' \
  https://kdevnest.com/login
```

主な確認結果：

| 結果          | 確認対象                                     |
| ------------- | -------------------------------------------- |
| `200`         | アプリケーションが応答している               |
| `301` / `302` | リダイレクト先と設定                         |
| `403`         | WAF、認可、アクセス制御                      |
| `502`         | ALBからターゲットへの接続、アプリケーション  |
| `503`         | ALBの正常なターゲットの有無                  |
| `504`         | タイムアウト、ネットワーク、アプリケーション |
| DNS解決失敗   | Route 53、DNS設定、ドメインの状態            |

HTTPステータスだけで原因を断定せず、ECSイベント、ALB Target Health、CloudWatch Logsを組み合わせて調査する。

---

## 10. 障害対応後の確認

障害対応や復旧作業が完了したら、以下を確認する。

- ECS ServiceのDesired CountとRunning Countが一致している。
- RDSが想定する稼働状態になっている。
- ALB Target Healthが正常である。
- HTTPS接続が成功する。
- Spring Boot Applicationの主要機能が動作する。
- CloudWatch Logsに重大なエラーが継続していない。
- AWS Billing / Cost Explorerで想定外の課金が継続していない。
- 手動変更によるTerraform Driftの有無を確認した。
- 障害原因、対応内容、再発防止策を必要に応じて記録した。

障害原因が不明な場合は、推測でTerraform ApplyやDestroyを繰り返さず、ログとAWSリソースの状態を確認してから次の操作を判断する。
