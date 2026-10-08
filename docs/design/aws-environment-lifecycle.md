# AWS Environment Lifecycle Design

## 1. 目的

設備点検管理システムのAWS環境について、未使用時のコストを削減しつつ、
必要なデータを保持した状態で安全に停止・削除・再構築できる仕組みを設計する。

本設計では、AWS環境のバックアップ、削除、再構築、RDS Snapshotからの復元を
Shell ScriptとTerraformを組み合わせて実行する。

---

## 2. 基本方針

AWSリソースを以下の2種類に分けて管理する。

### Persistent Resources

環境削除後も保持するリソース。

- Route53 Hosted Zone
- RDS Manual Snapshot

### Runtime Resources

必要に応じてTerraformから再構築できるリソース。

主な対象：

- VPC
- Subnet
- Internet Gateway
- Route Table
- Security Group
- ALB
- Target Group
- Listener
- WAF
- ECS Cluster
- ECS Service
- ECS Task Definition
- RDS DB Instance
- DB Subnet Group
- ECR Repository
- ACM Certificate
- Route53 Records
- SSM Parameters
- CloudWatch Logs / Alarms
- SNS Topic
- Terraform管理対象のIAM Role / Policy
- AWS Budget

Runtime ResourcesはAWS環境停止時に削除し、
再開時にTerraformから再構築する。

---

## 3. Route53 Hosted Zoneの管理

`kdevnest.com` のHosted Zoneは環境削除後も保持する。

Hosted Zoneを削除して再作成するとName Serverが変更される可能性があり、
ドメインレジストラ側でNS設定の変更が必要になるためである。

そのためHosted ZoneはRuntime ResourcesとはTerraformのライフサイクルを分離する。

想定構成：

```text
terraform/
├── persistent/
│   └── route53.tf
│
├── route53.tf
├── rds.tf
├── ecs.tf
├── alb.tf
└── ...
```

`persistent/` ではHosted Zoneを管理する。

通常のTerraform構成では既存Hosted Zoneを参照し、
Route53 Record等のRuntime Resourcesのみを管理する。

既存Hosted ZoneをPersistent管理へ移行する際は、
AWS上のHosted Zoneを削除・再作成せず、
Terraform Stateの管理単位のみを変更する。

---

## 4. RDS Backup方針

AWS環境を削除する前にRDS Manual Snapshotを作成する。

Snapshot作成後、AWS上でSnapshotの状態が `available` になったことを確認する。

Snapshot作成または確認に失敗した場合、
RDSを含むAWS環境の削除処理は実行しない。

```text
RDS
 ↓
Manual Snapshot作成
 ↓
available待機
 ↓
成功確認
 ↓
AWS環境削除
```

RDS SnapshotはTerraformによるRuntime Resourcesの削除対象には含めない。

Snapshotの削除は自動化せず、
不要になったSnapshotをAWS Management Consoleから
人間が確認した上で削除する。

---

## 5. AWS環境削除フロー

`scripts/destroy-aws-environment.sh` を使用する。

想定フロー：

```text
Preflight Check
 ↓
AWS Account確認
 ↓
AWS Region確認
 ↓
IAM Role確認
 ↓
対象AWS環境確認
 ↓
RDS Snapshot作成
 ↓
Snapshot available待機
 ↓
Backup成功確認
 ↓
削除対象 / 保持対象表示
 ↓
Human Confirmation
 ↓
TerraformによるRuntime Resources削除
```

Snapshot作成・確認に失敗した場合はFail Closedとし、
破壊的操作を実行しない。

削除実行前には単純な `y/N` ではなく、
指定文字列の完全一致入力による確認を行う。

例：

```text
Type "destroy equipment-inspection" to continue:
```

---

## 6. AWS環境復元フロー

`scripts/restore-aws-environment.sh` を使用する。

想定フロー：

```text
Preflight Check
 ↓
AWS Account / Region / IAM Role確認
 ↓
既存Runtime Resources確認
 ↓
Hosted Zone確認・選択
 ↓
RDS Snapshot確認・選択
 ↓
復元内容確認
 ↓
Terraform Apply
 ↓
ECRへDocker Image Push
 ↓
ECS/Fargate起動
 ↓
SNS Email Subscription再設定
 ↓
AWS Resource Health確認
 ↓
HTTPS疎通確認
 ↓
DBデータ確認
```

既存Runtime Resourcesとの競合が確認された場合は、
意図しない環境変更を防ぐため復元処理を中断する。

---

## 7. Hosted Zone / RDS Snapshotの選択

Hosted ZoneおよびRDS Snapshotの状態は `.env` に保存しない。

AWS上の実際の状態をSource of Truthとし、
AWS CLIから取得する。

### Hosted Zone

`DOMAIN_NAME` を使用してHosted Zoneを検索する。

- 0件：新規Hosted Zone作成
- 1件：対象を表示して再利用
- 複数件：人間が明示的に選択

新規Hosted Zoneを作成した場合、
ドメインレジストラ側のName Server設定変更が必要になる可能性があるため、
手動確認を行う。

### RDS Snapshot

対象RDSの利用可能なManual Snapshotを一覧表示する。

利用者は以下から選択する。

- 既存Snapshotから復元
- 新規Databaseを作成

複数Snapshotが存在する場合、
スクリプトが自動的にSnapshotを決定しない。

---

## 8. 環境設定

固定的な環境情報は `.env` から取得する。

例：

```dotenv
AWS_PROFILE=terraform-execution
AWS_REGION=ap-northeast-1
AWS_ACCOUNT_ID=339741260090
PROJECT_NAME=equipment-inspection
DOMAIN_NAME=kdevnest.com
RDS_IDENTIFIER=equipment-inspection-db
```

`.env` は環境識別および安全確認のために使用する。

Hosted ZoneやSnapshotなど、
AWS上で変化する状態は `.env` には保存しない。

---

## 9. IAM / AWS Account安全確認

Destroy / Restoreの両スクリプトで、
破壊的操作またはTerraform実行前にAWS実行環境を確認する。

最低限以下を確認する。

- AWS Account ID
- AWS Region
- AWS CLI Profile
- 実行中のIAM Role

想定AccountまたはRoleと一致しない場合は処理を中断する。

Terraform実行用IAM RoleはRuntime Resourcesとは別の
Bootstrap Resourceとして扱い、
AWS環境削除の対象には含めない。

---

## 10. RDS Snapshot復元

Restore時は以下の2種類をサポートする。

```text
Snapshotあり
    ↓
SnapshotからRDSを復元

Snapshotなし
    ↓
新規RDSを作成
```

Snapshot復元時のDatabase Passwordと
TerraformのEphemeral Passwordの整合性については、
実装前に詳細設計を行う。

---

## 11. ECR / ECS復元

ECR RepositoryはRuntime Resourceとして削除可能とする。

Restore時は必要に応じてRepositoryをTerraformから再作成し、
アプリケーションのDocker Imageを再Build / Pushする。

Docker ImageがECRに存在することを確認した後、
ECS/FargateでSpring Boot Applicationを起動する。

ECS Serviceのdesired countとTerraform Stateの整合性を維持する方法については、
実装前に詳細設計を行う。

---

## 12. SNS Email Subscription

SNS TopicはRuntime Resourceとして再作成可能とする。

Email SubscriptionはTerraform管理対象外のため、
SNS Topic再作成後に既存のセットアップスクリプトを使用して再設定する。

---

## 13. 対話方式

初期実装ではDestroy / Restoreともに対話形式で実行する。

SnapshotやHosted Zoneなど、
復元に使用するリソースは利用者が確認・選択する。

将来的にCI/CD等から実行できるよう、
コマンドライン引数によるリソース指定や
非対話モードへ拡張可能な構造を想定する。

非対話モード自体は本Issueの実装対象外とする。

---

## 14. Fail Safe / Fail Closed

以下の場合は処理を中断する。

- AWS Accountが想定と異なる
- AWS Regionが想定と異なる
- IAM Roleが想定と異なる
- AWS CLIによる対象リソース確認に失敗
- RDS Snapshot作成に失敗
- RDS Snapshotが `available` にならない
- 削除対象AWS環境を一意に確認できない
- Restore時に競合するRuntime Resourcesが存在する
- Hosted Zone / Snapshotの選択結果を検証できない

不明な状態では破壊的操作を継続しない。

---

## 15. 復元後確認

Restore完了後、最低限以下を確認する。

- TerraformによるAWS Resource作成成功
- ECS Service / Taskの起動
- ALB Target Health
- HTTPSでSpring Boot Applicationへアクセス可能
- RDSへの接続成功
- Snapshot復元時は既存Databaseデータが保持されていること

---

## 16. 今後の拡張候補

本Issueでは実装しないが、以下への拡張を可能な構造とする。

- CLI引数によるリソース指定
- 非対話モード
- CI/CDからの環境復元
- Snapshot保持世代管理
- Snapshot削除専用スクリプト
