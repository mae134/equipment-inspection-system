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

Destroyのたびに新しい日時付きIdentifierでManual Snapshotを作成する。既存Snapshotは上書きしない。

Manual SnapshotはRDS DB Instanceの削除後も保持され、保存容量に応じた料金が発生する。

Snapshotの世代管理・自動削除はIssue #99の対象外とし、不要になったSnapshotは復元要否を確認してから手動で削除する。

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
- `--new-db`：新規RDSを作成

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

### Terraform RDS Password変数

RDSのMaster Password管理には、以下のTerraform変数を使用する。

- `db_password`：RDS Master Password。EphemeralかつSensitiveとして扱う。
- `db_password_version`：Write-only Passwordのバージョン管理に使用する数値。
- `db_password_enabled`：TerraformによるRDS Password設定の有効・無効を制御するBoolean値。

通常のRestoreでは`db_password_enabled=true`（デフォルト）を使用する。

Destroyでは`TF_VAR_db_password_enabled=false`を指定し、`password_wo`と`password_wo_version`を両方`null`にする。

これにより、Destroy Plan作成時にEphemeral Passwordを要求しないようにする。

Destroy時に`TF_VAR_db_password_version=null`を指定する方式は採用しない。

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
--new-db
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
- `--new-db`
- `--apply`
- `--help`

`--apply`を指定しない場合はTerraform Planの確認のみで終了する。

実際にApplyする場合は`--apply`に加え、環境変数`CONFIRM_AWS_RESTORE=YES`を要求する。

SnapshotやHosted Zoneの対話選択は行わない。

## 14. 実行コマンド例

以下のコマンドは、リポジトリのルートディレクトリから実行する。

### 14.1 Destroy（RDS Snapshot作成あり）

```bash
bash scripts/aws/destroy-aws-environment.sh
```

- Terraform Destroy Planを作成し、削除対象を表示する。
- `DESTROY`の完全一致入力による承認を要求する。
- RDSが存在する場合はManual Snapshotを作成し、`available`になるまで待機する。
- Snapshot作成完了後にRuntime Resourcesを削除する。
- 作成したManual Snapshotは削除後も保持する。

### 14.2 Destroy（RDS Snapshot作成なし）

```bash
bash scripts/aws/destroy-aws-environment.sh --skip-snapshot
```

- RDS Manual Snapshotの作成を省略する。
- `DESTROY`の完全一致入力による承認は省略しない。
- 保存が必要なデータがない場合、または復元可能なバックアップを別途確認した場合に限って使用する。
- RDSに未保存のデータがある場合、削除によって復元できなくなる可能性がある。

### 14.3 Restore（RDS Snapshotから復元）

```bash
CONFIRM_AWS_RESTORE=YES \
  bash scripts/aws/restore-aws-environment.sh \
    --snapshot-id <SNAPSHOT_ID> \
    --apply
```

- 指定したSnapshotからRDSを復元する。
- Terraform Apply後、DB Password設定、アプリケーションBuild、ECR Push、ECS起動、ALB Health確認を実行する。
- `<SNAPSHOT_ID>`は実際に保存されたSnapshot Identifierに置き換える。

### 14.4 Restore（新規RDS作成）

```bash
CONFIRM_AWS_RESTORE=YES \
  bash scripts/aws/restore-aws-environment.sh \
    --new-db \
    --apply
```

- Snapshotを使用せず、新しいRDSを作成する。
- 既存Snapshotのデータは引き継がない。
- アプリケーションのBuild・デプロイとヘルスチェックまで実行する。

### 14.5 Restore（Plan確認のみ）

Snapshotからの復元計画を確認する場合：

```bash
bash scripts/aws/restore-aws-environment.sh \
  --snapshot-id <SNAPSHOT_ID>
```

新規RDS作成の計画を確認する場合：

```bash
bash scripts/aws/restore-aws-environment.sh \
  --new-db
```

- `--apply`を指定しないため、Terraform Applyは実行しない。
- 実際の環境構築には`--apply`と`CONFIRM_AWS_RESTORE=YES`の両方が必要。

### Destroy / Restoreの実行時間計測

AWS環境の削除・再構築にかかる時間を計測する場合は、`time`コマンドを使用する。

#### Destroy

```bash
time bash scripts/aws/destroy-aws-environment.sh
```

#### Restore（Snapshotから復元）

```bash
time env CONFIRM_AWS_RESTORE=YES \
  bash scripts/aws/restore-aws-environment.sh \
    --snapshot-id SNAPSHOT_ID \
    --apply
```

#### Restore（新規RDS作成）

```bash
time env CONFIRM_AWS_RESTORE=YES \
  bash scripts/aws/restore-aws-environment.sh \
    --new-db \
    --apply
```

実行終了後に表示される`real`が、実際の経過時間を表す。

```text
real    12m35.421s
user    0m8.312s
sys     0m2.145s
```

`real`にはAWSリソースの作成・削除待機時間や、ユーザーによる承認入力の待機時間も含まれる。

`time`による計測はスクリプトの処理内容を変更しない。

### 14.6 注意事項

- Destroy前にAWS Account、IAM Role、Terraform Planの削除対象を確認する。
- `--skip-snapshot`はデータ損失の可能性を理解したうえで使用する。
- Restore完了後はHTTPS疎通、ログイン、DBデータ保持状況を手動で確認する。
- AWSへの変更開始後に異常終了した場合は、Terraform StateとAWSリソースの状態を確認してから再実行する。
- Manual Snapshotは自動削除されず、保持中はストレージ料金が発生する場合がある。

### 14.7 RDS Manual Snapshot確認

#### Manual Snapshot一覧確認

```bash
aws rds describe-db-snapshots \
  --profile terraform-execution \
  --region ap-northeast-1 \
  --snapshot-type manual \
  --query 'DBSnapshots[*].[DBSnapshotIdentifier,DBInstanceIdentifier,Status,SnapshotCreateTime]' \
  --output table
```

- Manual SnapshotのIdentifier、対象DB、状態、作成日時を確認する。
- Restoreで使用するSnapshot Identifierを取得できる。
- `available`のSnapshotが復元候補となる。

#### 指定Snapshotの状態確認

```bash
aws rds describe-db-snapshots \
  --profile terraform-execution \
  --region ap-northeast-1 \
  --db-snapshot-identifier equipment-inspection-db-manual-20261022-121956 \
  --query 'DBSnapshots[0].[DBSnapshotIdentifier,Status,SnapshotType]' \
  --output table
```

- 指定したSnapshotの状態と種別を確認する。
- `Status`が`available`、`SnapshotType`が`manual`であることを確認する。
- Snapshot Identifierは実際に作成されたものに置き換える。

#### 注意事項

- Manual SnapshotはRDS DB Instanceを削除しても保持される。
- Snapshotが`creating`の場合は、`available`になるまで復元に使用できない。
- 不要なSnapshotは、復元の必要性を確認したうえで手動削除する。
- Snapshotの保持にはストレージ料金が発生する場合がある。

### 14.8 不要なRDSスナップショットの削除

手動スナップショットはRDSインスタンス削除後も保持されるため、不要になった場合は明示的に削除する。

**1. スナップショット一覧を確認**

```bash
aws rds describe-db-snapshots \
  --profile terraform-execution \
  --region ap-northeast-1 \
  --snapshot-type manual \
  --query 'DBSnapshots[*].[DBSnapshotIdentifier,Status,SnapshotCreateTime]' \
  --output table
```

**2. 削除対象を確認**

削除前に、以下を確認する。

- 今後の環境復元に必要なスナップショットではないこと
- 保存が必要なデータが含まれていないこと
- スナップショットIDが正しいこと

**3. スナップショットを削除**

```bash
aws rds delete-db-snapshot \
  --profile terraform-execution \
  --region ap-northeast-1 \
  --db-snapshot-identifier SNAPSHOT_ID
```

`SNAPSHOT_ID` は実際のスナップショットIDに置き換える。

**4. 削除結果を確認**

```bash
aws rds describe-db-snapshots \
  --profile terraform-execution \
  --region ap-northeast-1 \
  --snapshot-type manual \
  --output table
```

**注意事項**

- スナップショットの削除は元に戻せない。
- 削除後は、そのスナップショットからRDSを復元できない。
- 削除対象がTerraform管理下にある場合は、Terraform側の管理方法も確認する。
- 保存料金はスナップショットの使用容量や無料枠などによって異なる。

## 15. Fail Safe / Fail Closed

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

## 16. 復元後確認

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

## 17. 今後の拡張候補

Issue #99の対象外とする。

- CI/CDからの環境復元
- Snapshot保持世代管理
- Snapshot削除専用スクリプト
- HTTPS・DBデータの自動検証
- ECS ImageへのコミットSHAなどの固定タグ導入
- RDS Password設定の制御方法の見直し
  - 現状、Destroy Plan作成時のTerraform変数エラーを回避するため、`db_password_enabled=false`を使用している。
  - 通常のRestoreでは`true`を使用し、RDS Master Passwordを設定する。
  - 将来的には、通常構築時にパスワード設定を意図せず無効化できない設計を検討する。
  - Terraformの変数設計を改善し、Destroy専用の制御が通常の構築処理に影響しないようにする。

## 18. 実環境検証結果

検証期間：2026-10-22〜2026-10-23

### 18.1 Destroy検証

**結果：成功**

RDS Manual Snapshotを作成する通常のDestroyと、Snapshot作成を省略する`--skip-snapshot`の両方を実環境で検証した。

**Manual Snapshotあり**

- Terraform Destroy Planで47リソースの削除を確認
- `DESTROY`の完全一致入力による承認に成功
- RDS Manual Snapshotの作成に成功
- Snapshotが`available`になるまでの待機に成功
- Terraformによる47リソースの削除に成功
- Destroyスクリプトが正常終了

作成したSnapshot：

- `equipment-inspection-db-manual-20261022-121956`
- `equipment-inspection-db-manual-20261022-130524`

**Snapshot作成なし（`--skip-snapshot`）**

- `--skip-snapshot`を指定してDestroyを実行
- 新しいRDS Manual Snapshotを作成せずに削除処理を実施
- Terraformによる47リソースの削除に成功
- Destroyスクリプトが正常終了
- 1回目の実行時間：5分35.022秒
- 新規RDS構築後の最終Destroy：6分13.104秒

### 18.2 Restore検証（Snapshotから復元）

**結果：成功**

- 指定したManual SnapshotからRDSを復元
- RDS Master Password設定とSSM Parameter同期処理を実施
- Maven Build、Docker Image Buildに成功
- ECRへのImage Pushに成功
- ECS ServiceがStable状態に到達
- ALB Target HealthがHealthy状態に到達
- HTTPS経由でログイン画面を表示
- ログイン成功
- ダッシュボード・設備一覧・点検履歴画面を表示

実行時間：**20分26.790秒**

### 18.3 Restore検証（新規RDS作成）

**結果：成功**

`--new-db`モードを使用し、既存のRDS Snapshotを利用せずにAWS環境を新規構築した。

- Restoreスクリプトが正常終了（Exit code：`0`）
- ALB Target HealthがHealthy状態に到達
- HTTPS経由でアプリケーションにアクセス
- 設備`EQ-001`（モーター設備A）の表示を確認
- 点検履歴が存在しないことを確認
- 以前に登録した点検履歴が新規DB上には存在しないことを画面で確認

実行時間：**18分44.255秒**

### 18.4 AWS環境の構築・削除時間

2026年10月22日〜23日の実環境検証における実測値。

| 操作 | 所要時間 | 結果 |
|---|---:|---|
| Snapshotからの環境復元 | 20分26.790秒 | 成功 |
| Snapshot作成なしの環境削除 | 5分35.022秒 | 成功 |
| 新規RDS作成による環境構築 | 18分44.255秒 | 成功 |
| 新規RDS構築後の最終Destroy | 6分13.104秒 | 成功 |

- 実行時間にはAWSリソースの作成・削除待機時間や、ユーザーによる承認入力時間も含まれる。
- AWSリソースの状態によって所要時間は変動する。

### 18.5 最終Destroy・リソース保持確認

**結果：Runtime Resourcesの削除成功**

新規RDS作成によるRestoreの動作確認後、
`--skip-snapshot`を指定して最終Destroyを実施した。

- Terraformによる47リソースの削除に成功
- Destroyスクリプトが正常終了（Exit code：`0`）
- 実行時間：6分13.104秒

**Persistent Resourcesの保持確認：成功**

AWS CLIによる確認の結果、最終Destroy後も以下のリソースが保持されていることを確認した。

- Route53 Hosted Zone `kdevnest.com.` が存在することを確認
- Hosted Zone ID：`Z06136141UCK9KD6NU41L`
- 既存のRDS Manual Snapshot 2件が`available`状態で保持されていることを確認
  - `equipment-inspection-db-manual-20261022-121956`
  - `equipment-inspection-db-manual-20261022-130524`
- 最終Destroyによる新しいManual Snapshotの作成がないことを確認

これにより、Runtime Resourcesの削除後も、保持対象のHosted ZoneとManual Snapshotが維持されていることを確認した。

**Terraform Stateの確認：成功**

`terraform -chdir=terraform/runtime state list`を実行し、
Runtime Terraform Stateに管理対象リソースが残っていないことを確認した。

**不要なRDS Manual Snapshotの削除：成功**

実環境検証で作成した以下のManual Snapshot 2件について、保存が必要なデータが存在しないことを確認したうえで、AWS CLIから削除した。

- `equipment-inspection-db-manual-20261022-121956`
- `equipment-inspection-db-manual-20261022-130524`

両Snapshotの削除操作は正常に完了し、`Status`が`deleted`となった。

削除後に`describe-db-snapshots --snapshot-type manual`を実行し、Manual Snapshot一覧が空であることを確認した。

これにより、Runtime Resourcesの削除、Persistent Hosted Zoneの保持、および不要なManual Snapshotの整理が完了した。
