# AWS Environment Lifecycle Design

## 1. 目的

設備点検管理システムのAWS環境について、未使用時のコストを削減しつつ、必要なデータを保持して削除・再構築できる仕組みを定義する。

Shell Script、AWS CLI、Terraformを組み合わせ、以下を実現する。

- RDS Manual Snapshotの作成・保持
- Runtime Resourcesの削除
- Terraformによる環境再構築
- RDS Snapshotからの復元、または新規RDS作成
- アプリケーションの再Build・ECRへのPush
- ECS起動とALB Target Healthの確認

本設計はIssue #99の実装を基準とする。

## 2. 基本方針

AWSリソースをPersistent ResourcesとRuntime Resourcesに分離する。

### Persistent Resources

環境削除後も保持するリソース。

- Route53 Hosted Zone
- RDS Manual Snapshot

Hosted Zoneは`terraform/persistent/`で管理する。

RDS Manual SnapshotはAWS CLIで作成し、Runtime Terraform Stateによる削除対象には含めない。

### Runtime Resources

`terraform/runtime/`で管理し、必要に応じて削除・再構築する。

主な対象：

- VPC、Subnet、Route Table、Internet Gateway、Security Group
- ALB、Target Group、Listener、WAF
- ECS Cluster、Service、Task Definition
- RDS DB Instance、DB Subnet Group
- ECR Repository
- ACM Certificate、Route53 Records
- SSM Parameters
- CloudWatch Logs / Alarms
- SNS Topic
- Terraform管理対象のIAM Role / Policy
- AWS Budget

Terraform実行用のBootstrap IAM RoleはRuntime削除対象に含めない。

## 3. Route53 Hosted Zoneの管理

`kdevnest.com`のHosted Zoneは環境削除後も保持する。

Hosted Zoneを削除・再作成するとName Serverが変わる可能性があるため、Runtime ResourcesとはStateを分離する。

構成：

```text
terraform/
├── persistent/
│   └── ...
└── runtime/
    ├── ...
    └── ...
```

Runtime側では既存Hosted Zoneを参照し、Route53 Recordsなどの再構築可能なリソースを管理する。

Hosted Zone自体の新規作成・選択はRestoreスクリプトの対象外とする。

## 4. RDS Backup方針

Destroy時は原則としてRDS Manual Snapshotを作成する。

```text
Human Confirmation
 ↓
RDS状態確認・必要に応じて起動
 ↓
Manual Snapshot作成
 ↓
Snapshot available待機
 ↓
TerraformによるRuntime Resources削除
```

Snapshot作成またはavailable確認に失敗した場合は、Terraformによる削除へ進まない。

`--skip-snapshot`を指定するとSnapshot作成を省略できる。ただし、既存の復元可能なバックアップを確認した場合などに限定して使用する。

Snapshotは自動削除しない。不要なSnapshotは保持状況を確認して手動で削除する。

## 5. AWS環境削除フロー

使用スクリプト：

`scripts/aws/destroy-aws-environment.sh`

基本フロー：

```text
引数・実行環境の確認
 ↓
AWS Account / IAM Role確認
 ↓
Terraform Runtime Stateの保護対象確認
 ↓
Terraform削除計画の安全性確認
 ↓
削除内容の確認
 ↓
「DESTROY」の完全一致入力
 ↓
RDS Manual Snapshot作成・available待機
   （--skip-snapshot時は省略）
 ↓
TerraformによるRuntime Resources削除
```

確認文字列が一致しない場合は削除を実行しない。

AWSへの変更開始後に異常終了した場合は、既存リソースを自動で削除・巻き戻しせず、Terraform StateとAWSリソースの状態確認を案内する。

## 6. AWS環境復元フロー

使用スクリプト：

`scripts/aws/restore-aws-environment.sh`

基本フロー：

```text
引数・実行環境の確認
 ↓
AWS Account / IAM Role確認
 ↓
RDS Snapshot確認（Snapshotモード）
 ↓
Terraform Plan作成・安全性確認
 ↓
実行承認
 ↓
Terraform Apply
 ↓
RDS Password設定・SSM Parameter同期
 ↓
Maven JAR Build
 ↓
Docker Image Build
 ↓
ECR認証・Image Push
 ↓
ECS Service起動
 ↓
ECS安定稼働待機
 ↓
ALB Target Health確認
```

Terraform Planに削除・置換操作が含まれる場合は処理を中断する。

Restore完了後のHTTPS疎通やDBデータ確認は別途手動で実施する。

## 7. Hosted Zone / RDS Snapshotの選択

### Hosted Zone

Hosted ZoneはPersistent Terraform Stateで管理する既存のものを使用する。

RestoreスクリプトでのHosted Zone検索・新規作成・対話選択は実装しない。

### RDS Snapshot

Restoreでは次のいずれかを明示的に指定する。

- `--snapshot-id ID`：指定したManual Snapshotから復元
- `--new`：新規RDSを作成

Snapshotモードでは、指定Snapshotが`available`であり、想定するDB Instanceのものであることを検証する。

Snapshot一覧の対話選択は実装しない。

## 8. 環境設定

Destroy / Restoreスクリプトでは、主要な実行環境を固定値で管理する。

- AWS Profile：`terraform-execution`
- AWS Region：`ap-northeast-1`
- AWS Account ID：想定アカウントと照合
- IAM Role：`terraform-execution-role`
- Terraform Runtime Directory：`terraform/runtime/`

AWS ProfileとRoleの設定・認証情報はAWS CLI側で管理する。

Restore時のDB Passwordは`openssl`で生成し、一時的なTerraform変数として渡す。

Passwordや一時PlanファイルをGit管理対象に含めない。

## 9. IAM / AWS Account安全確認

Destroy / Restoreの両スクリプトで、Terraform実行前に以下を確認する。

- AWS Account ID
- AWS CLI Profile
- AWS Region
- IAM Role

AWS STSで取得したAccount IDとRole ARNが想定値と一致しない場合は処理を中断する。

AWS CLI ProfileとRegionはスクリプト内で明示する。

Terraform実行用IAM RoleはBootstrap Resourceとして保持する。

## 10. RDS Snapshot復元

Restoreは以下の2モードをサポートする。

```text
--snapshot-id ID
    ↓
SnapshotからRDS復元
    ↓
RDS Master Password更新
    ↓
RDS available待機
    ↓
SSM SecureString更新
```

```text
--new
    ↓
新規RDS作成
    ↓
SSM SecureString更新
```

DB Passwordは`openssl rand -hex 24`で生成する。

Snapshot復元時はRDSのMaster Passwordを生成した値に更新し、利用可能状態を待機する。

その後、ECSが参照するSSM Parameter StoreのPasswordを同期する。

Password設定または同期に失敗した場合はECSを起動しない。

## 11. ECR / ECS復元

ECR RepositoryはRuntime ResourceとしてTerraformから再作成する。

Restore時は次の処理を実行する。

1. Maven Wrapperで`verify`を実行してJARを生成
2. Docker ImageをBuild
3. AWS CLIでECR認証情報を取得
4. Docker Login
5. ECRへImageをPush
6. ECS Serviceのdesired countを1に変更
7. ECS Serviceの安定稼働を待機
8. ALB Target Healthを確認

ECR Imageは既存のECS Task Definitionに合わせて`latest`タグを使用する。

Terraform側ではECS Serviceをdesired count 0で作成し、起動はAWS CLIで実行する。

ECS Serviceの`desired_count`はTerraformの`ignore_changes`で管理し、CLIによる起動とTerraform Stateの競合を避ける。

Build・認証・Pushのいずれかが失敗した場合はECS起動へ進まない。

## 12. SNS Email Subscription

SNS TopicはRuntime Resourceとして再作成する。

Email SubscriptionはTerraform管理対象外のため、環境復元後に次の既存スクリプトで手動設定する。

`scripts/aws/setup-sns-email-subscription.sh`

購読確認メールの承認も手動で実施する。

Restoreスクリプトには組み込まない。

## 13. 対話方式

DestroyとRestoreでは誤操作防止のため、異なる承認方式を採用する。

### Destroy

実行時に`DESTROY`の完全一致入力を要求する。

オプション：

- `--skip-snapshot`
- `--help`

### Restore

CLI引数で復元対象を指定する。

- `--snapshot-id ID`
- `--new`
- `--apply`
- `--help`

`--apply`を指定しない場合はTerraform Planの確認のみで終了する。

実際にApplyする場合は`--apply`に加え、環境変数`CONFIRM_AWS_RESTORE=YES`を要求する。

SnapshotやHosted Zoneの対話選択は行わない。

## 14. Fail Safe / Fail Closed

次の場合は処理を中断する。

- AWS AccountまたはIAM Roleが想定と異なる
- 必須コマンドやTerraformディレクトリが存在しない
- Terraform Stateに保護対象リソースが含まれる
- Terraform Planの安全性確認に失敗
- Restore Planに削除・置換操作が含まれる
- 指定Snapshotが利用不可、または想定DBと一致しない
- Snapshot作成・待機に失敗
- RDS Password更新・SSM同期に失敗
- Maven Build、Docker Build、ECR認証、Image Pushに失敗
- ECS起動・安定稼働待機に失敗
- ALB Target Health確認に失敗

AWSへの変更開始後に失敗した場合は、自動Rollbackを実施しない。

Terraform State、RDS Snapshot、AWSリソースの状態を確認してから再実行する。

## 15. 復元後確認

### スクリプトによる自動確認

- Terraform Apply成功
- RDS PasswordとSSM Parameterの設定処理成功
- ECR Image Push成功
- ECS Service安定稼働
- ALB Target Healthが正常

### 手動確認

- `https://kdevnest.com`へのHTTPS疎通
- Spring Boot Applicationの主要画面
- RDSへの接続とデータ保持状況
- SNS Email Subscriptionの再設定・購読確認
- AWS Billing / Cost Explorerでの課金状況

自動確認の成功だけで、アプリケーション全機能の正常性を保証するものではない。

## 16. 今後の拡張候補

Issue #99の対象外とする。

- CI/CDからの環境復元
- Snapshot保持世代管理
- Snapshot削除専用スクリプト
- HTTPS・DBデータの自動検証
- ECS ImageへのコミットSHAなどの固定タグ導入
