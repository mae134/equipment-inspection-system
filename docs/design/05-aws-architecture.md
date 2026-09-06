# AWS Architecture Design

## 1. 目的

設備点検管理システムをAWS上へ安全かつ継続的に公開するため、AWSインフラストラクチャの基本設計方針を定義する。

本設計では、就職活動用ポートフォリオとして以下を重視する。

- Spring Boot / PostgreSQL / DockerをAWS上で実運用できる構成とする
- セキュリティを確保しつつ、過剰な構成を避ける
- 月額コストの予測可能性を重視する
- 障害発生時に原因調査および最低限の復旧が可能な構成とする
- 初回は手動デプロイを行い、その後CI/CDによる継続的な更新を可能にする
- AWS投入前にローカルDocker環境で総合確認を完了する

---

## 2. 設計方針

本システムは企業向け本番システムではなく、就職活動中に公開する個人ポートフォリオである。

そのため、高可用性を最優先する構成ではなく、以下の優先順位で設計する。

1.  セキュリティ
2.  コストの予測可能性
3.  運用・障害調査のしやすさ
4.  AWSおよびコンテナ運用の学習価値
5.  可用性

高可用性のためのMulti-AZ構成や負荷に応じたAuto
Scaling等は初期構成では採用せず、必要最低限の構成とする。

---

## 3. システム構成

想定するAWS構成は以下とする。

```text
                         Internet
                            |
                      独自ドメイン
                            |
                        Route 53
                            |
                       HTTPS : 443
                            |
                  Shield Standard
                            |
                         AWS WAF
                   Rate-based Rule
                            |
                            v
                    +---------------+
                    |      ALB      |
                    | Public Subnet |
                    |   ACM / TLS   |
                    +-------+-------+
                            |
                       HTTP : 8080
                            |
                     ALB-SG -> ECS-SG
                            |
                            v
                  +-------------------+
                  |   ECS / Fargate   |
                  |     Task x 1      |
                  |   Public Subnet   |
                  |    Public IP      |
                  +---------+---------+
                            |
                    PostgreSQL : 5432
                            |
                     ECS-SG -> RDS-SG
                            |
                            v
                  +-------------------+
                  |  RDS PostgreSQL   |
                  |  Private Subnet   |
                  |    Single-AZ      |
                  +-------------------+

ECR ---------------------> ECS / Fargate
Parameter Store ---------> ECS / Fargate
CloudWatch <------------- ECS / ALB / RDS
```

構成図の詳細版は別途Draw.ioおよびPNG形式で作成する。

---

## 4. ネットワーク設計

### 4.1 VPC

設備点検管理システム専用のVPCを作成し、AWSリソースを配置する。

### 4.2 Public Subnet

以下をPublic Subnetへ配置する。

- ALB
- ECS / Fargate Task

ALBはInternetからHTTPSリクエストを受け付ける。

Fargate TaskはPublic IPを付与するが、Security
GroupによってInbound通信元をALBのSecurity
Groupのみに制限し、Internetからアプリケーションへ直接アクセスできないようにする。

### 4.3 Private Subnet

RDS PostgreSQLはPrivate
Subnetへ配置し、Internetから直接アクセスできない構成とする。

### 4.4 Internet Gateway

Public SubnetからInternetへ通信するため、VPCへInternet
Gatewayを接続する。

### 4.5 NAT Gatewayを採用しない理由

Fargate TaskをPrivate
Subnetへ配置した場合、ECR等へのアウトバウンド通信のためNAT
GatewayまたはVPC Endpointが必要となる。

本システムはポートフォリオ用途であり、固定費を抑えることを重視するため、初期構成ではNAT
Gatewayを採用しない。

### 4.6 VPC Endpointを採用しない理由

Private Subnet上のFargate TaskからAWSサービスへ接続する代替手段としてVPC
Endpointを利用できるが、初期構成ではコストおよび構成の複雑化を避けるため採用しない。

---

## 5. コンピュート設計

### 5.1 ECS

Dockerコンテナの実行・管理にはAmazon ECSを利用する。

### 5.2 Fargate

ECSの起動タイプとしてFargateを採用する。

EC2インスタンス自体の管理を不要とし、コンテナ単位でSpring
Bootアプリケーションを実行する。

### 5.3 Task構成

初期構成ではFargate Taskを1個とする。

```text
Desired Count = 1
```

ECS Serviceによって常時1
Taskを維持し、Taskが異常終了した場合には新しいTaskを起動してDesired
Countを維持する。

### 5.4 Auto Scaling

負荷に応じてTask数を自動増減するAuto Scalingは採用しない。

理由は以下の通り。

- ポートフォリオ用途では大規模アクセスを想定しない
- Task数の自動増加による予期しないコスト増加を防止する
- 月額コストの予測可能性を優先する

---

## 6. コンテナイメージ管理

Docker Imageの保存にはAmazon ECRを利用する。

Docker Imageには以下を含める。

- Java実行環境
- Spring BootアプリケーションのJAR
- アプリケーション起動設定

DBパスワード等の機密情報やAWS環境固有の設定値はDocker
Imageへ埋め込まない。

アプリケーションコードを変更した場合は、新しいJARおよびDocker
Imageを生成し、ECRへ登録した上でECSへ反映する。

---

## 7. データベース設計

### 7.1 RDS PostgreSQL

本番データベースとしてAmazon RDS for PostgreSQLを利用する。

### 7.2 Single-AZ

初期構成ではSingle-AZとする。

Multi-AZによる高可用性よりも、ポートフォリオ用途におけるコスト削減を優先する。

### 7.3 ネットワーク

RDSはPrivate Subnetへ配置する。

RDSのSecurity Groupでは、ECS TaskのSecurity
GroupからのPostgreSQL通信のみを許可する。

### 7.4 バックアップ

RDSの自動バックアップを有効化する。

障害や誤操作によるデータ損失時に復旧できることを目的とする。

具体的なバックアップ保持期間は構築時に決定する。

---

## 8. ロードバランサー設計

### 8.1 ALB

InternetからのWebアクセスを受け付けるためApplication Load
Balancerを利用する。

ALBはHTTPSリクエストを受信し、ECS / Fargate上のSpring
Bootアプリケーションへ転送する。

### 8.2 Health Check

ALB Target GroupのHealth
Checkを利用してアプリケーションの正常性を確認する。

Spring Boot Actuator等によるHealth
Check用エンドポイントの利用を検討する。

具体的な以下の設定値は構築時に決定する。

- Health Check Path
- Interval
- Timeout
- Healthy Threshold
- Unhealthy Threshold

---

## 9. DNS / HTTPS設計

### 9.1 Route 53

独自ドメインからALBへアクセスできるよう、DNS管理にAmazon Route
53を利用する。

### 9.2 ACM

HTTPS通信に必要なTLS証明書はAWS Certificate
Manager（ACM）で発行・管理する。

### 9.3 証明書検証

ACM証明書の所有確認にはDNS Validationを利用する。

### 9.4 TLS終端

TLS通信はALBで終端する。

InternetからALBまではHTTPSを使用し、ALBからECS
Taskへの通信はVPC内部通信とする。

### 9.5 HTTPからHTTPSへのリダイレクト

HTTP（80）へのアクセスはHTTPS（443）へリダイレクトする。

---

## 10. Security Group設計

Security GroupはIPアドレスではなくSecurity Group間の参照を基本とする。

### ALB Security Group

Inbound:

- InternetからHTTPS（443）を許可
- HTTP（80）はHTTPSへのリダイレクト用途として必要に応じて許可

### ECS Security Group

Inbound:

- ALB Security GroupからSpring
  Bootのアプリケーションポートへの通信のみ許可
- Internetからの直接アクセスは許可しない

### RDS Security Group

Inbound:

- ECS Security GroupからPostgreSQL（5432）への通信のみ許可
- Internetからのアクセスは許可しない

これにより、接続元のIPアドレスが変化してもSecurity
Group間の関係によって通信制御を維持できる。

---

## 11. IAM設計

IAMは最小権限の原則に従う。

必要以上の権限やAdministratorAccess等の広範な権限をアプリケーションへ付与しない。

### 11.1 Task Execution Role

ECS / FargateがTaskを起動するために必要なAWS操作へ利用する。

主な用途は以下を想定する。

- ECRからDocker Imageを取得
- CloudWatch Logsへログを送信
- Task起動時に必要な秘密情報を取得

### 11.2 Task Role

Spring Bootアプリケーション自身がAWS
APIを操作する必要がある場合に利用する。

アプリケーションからAWSサービスを操作する必要がない場合は、不要な権限を付与しない。

---

## 12. 機密情報・環境設定管理

### 12.1 Parameter Store

DB認証情報等の管理にはAWS Systems Manager Parameter Storeを利用する。

### 12.2 SecureString

パスワード等の機密情報はSecureStringとして保存する。

以下のような情報をGitリポジトリ、Docker
Image、ソースコードへ直接記載しない。

- DBユーザー名
- DBパスワード
- その他の秘密情報

### 12.3 環境依存設定

ローカル、Docker、AWSで異なる設定値は外部から注入できる構成とする。

同一Docker
Imageを環境ごとに再利用できるよう、AWS固有値をImageへ埋め込まない。

---

## 13. 監視・ログ設計

### 13.1 CloudWatch Logs

ECS / Fargate上で動作するSpring BootアプリケーションのログをCloudWatch
Logsへ出力する。

障害発生時やデプロイ失敗時の原因調査に利用する。

ログ保持期間はポートフォリオ用途を考慮し、必要最低限とする。

具体的な保持期間は構築時に決定する。

### 13.2 CloudWatch Alarm

CloudWatch Alarmによって重大な異常を検知できるようにする。

監視候補は以下とする。

- ALBのHealthy Target
- ALBの5xxエラー
- ECSの稼働状態
- RDSの主要メトリクス

ポートフォリオ用途のため過剰な監視は行わず、重要な項目へ限定する。

具体的なAlarm条件・閾値は構築時に決定する。

### 13.3 役割分担

```text
CloudWatch Logs
  -> ログの記録・原因調査

CloudWatch Alarm
  -> システム状態の監視・異常検知

AWS Budgets
  -> 利用料金・予算の監視

Cost Anomaly Detection
  -> 通常と異なるコスト増加の検知
```

---

## 14. セキュリティ・DDoS対策

### 14.1 Shield Standard

AWS Shield Standardによる基本的なDDoS保護を利用する。

### 14.2 AWS WAF

ALBへAWS WAFを関連付ける。

### 14.3 Rate-based Rule

大量のHTTPリクエストによる負荷やコスト増加を抑制するため、Rate-based
Ruleを設定する。

具体的なRate Limit値は構築時に決定する。

### 14.4 Auto Scaling制限

ECS Auto
Scalingを採用せずTask数を固定することで、異常アクセス時にTask数が自動的に増加し、Fargate利用料金が予期せず増加することを防止する。

### 14.5 アプリケーションセキュリティ

AWS側の防御のみではアプリケーション脆弱性を完全には防止できない。

AWS投入前のDocker総合確認で、少なくとも以下を確認する。

- Spring Securityの認証・認可
- CSRF対策
- XSS対策
- SQL Injection対策
- 入力値検証
- エラー画面への機密情報露出
- 秘密情報のGit / Docker Imageへの混入
- 不要ポートの公開
- 依存ライブラリの既知脆弱性

---

## 15. デプロイ・CI/CD設計

### 15.1 初回デプロイ

初回は手動デプロイを実施する。

目的は、Docker
ImageがAWS上へ反映される一連の仕組みを理解し、問題発生時の切り分けを容易にすることである。

想定フロー:

```text
Spring Boot
    |
mvn verify
    |
JAR生成
    |
docker build
    |
ECRへpush
    |
ECS Task Definition更新
    |
ECS Service更新
    |
新しいTask起動
    |
ALB Health Check
    |
本番動作確認
```

### 15.2 継続デプロイ

初回の手動デプロイが正常に完了した後、GitHub
ActionsによるCI/CDを構築する。

最終的な運用フローは以下を想定する。

```text
feature branch
      |
Pull Request
      |
     CI
  +---+----------------+
  |                    |
Test               JaCoCo等
  |                    |
  +---------+----------+
            |
         CI成功
            |
       mainへMerge
            |
            CD
            |
 Docker Image build
            |
        ECR push
            |
      ECSへDeploy
            |
    ALB Health Check
            |
         本番反映
```

mainブランチへのマージを本番デプロイのトリガーとする。

### 15.3 アプリケーション更新

初回公開後も、View、Javaコード、設定等の変更を継続的に本番へ反映できる構成とする。

アプリケーションコードが変更された場合は、新しいJARおよびDocker
Imageを生成し、ECRへ登録してECS Serviceを更新する。

JaCoCo等のテスト・品質確認ツールはCI工程で利用し、本番アプリケーションへ不要な実行環境を持ち込まない。

### 15.4 ローカル開発とDocker Imageの更新方針

日常的なアプリケーション開発では、ソースコードを変更するたびにローカルDocker Imageを再buildすることは必須としない。

通常の実装・単体テスト等はローカル環境で実施し、必要に応じてDocker Compose上のPostgreSQL等を利用する。

以下の場合は、最新のソースコードからDocker Imageを再buildし、ローカルDocker環境で動作確認を行う。

- DockerfileまたはDocker関連設定を変更した場合
- アプリケーションの実行環境に影響する変更を行った場合
- AWSへの初回デプロイ前
- AWSへの本番リリース前に総合確認が必要な場合

ローカルDocker ImageとAWS上のECR Imageは自動的には同期しない。

本番リリース時は、mainブランチへのマージをトリガーとしてCI/CD上で新しいDocker Imageをbuildし、ECRへpushした上でECS Serviceへ反映する。

これにより、日常開発では開発効率を維持しつつ、本番へ反映するコンテナについては再現可能なDocker環境で検証する。

---

## 16. コスト設計

### 16.1 基本方針

本システムは個人ポートフォリオであるため、可用性よりも予期しない高額請求の防止を優先する。

AWS Pricing Calculatorによる見積もり結果、為替変動、独自ドメイン費用、税、従量課金による変動を考慮し、通常運用時のAWS利用料金は以下を目安とする。

```text
通常運用想定 : 12,000円前後 / 月
警戒ライン   : 13,000円 / 月
緊急警告     : 14,000円 / 月
緊急停止判断 : 15,000円付近 / 月
```

上記金額は運用上の判断基準であり、AWSの料金上限を保証するものではない。

為替変動や通常の従量課金による増加を異常と誤判定しないよう一定の余裕を確保する一方、通常とは異なる急激なコスト増加を検知した場合は、金額ラインへの到達前であっても緊急対応を行う。

### 16.2 AWS Budgets

AWS Budgetsによる段階的な料金通知を設定する。

例:

```text
12,000円 -> 通常運用想定額への到達通知
13,000円 -> 警戒通知
14,000円 -> 緊急警告
15,000円付近 -> 緊急停止判断
```

実績額だけでなく、予測コストについても監視する。

AWS Budgetsによる通知は料金監視および対応判断に利用し、通知のみをもって料金上限が保証されるものとはみなさない。

### 16.3 AWSクレジット残高管理

AWS Free Tier等によるクレジットを利用する場合は、月額料金だけでなくクレジット残高も確認する。

運用上の目安は以下とする。

```text
残り $50 付近 -> クレジット消費状況および今後の運用期間を再確認
残り $25 付近 -> 有料運用への移行、構成縮小、停止のいずれかを判断
```

AWSから提供されるクレジット残高に関する通知も利用し、クレジット枯渇後に意図せず有料運用へ移行しないよう、事前に今後の運用方針を判断する。

### 16.4 Cost Anomaly Detection

通常とは異なる急激なコスト増加を検知するため、AWS Cost Anomaly Detectionを利用する。

深夜等、管理者が即時対応できない時間帯も考慮し、月額料金の絶対額だけでなく、通常とは異なる料金増加も緊急対応の判断材料とする。

Cost Anomaly Detectionによって重大な異常増加を検知した場合は、15,000円付近の緊急停止判断ラインへの到達を待たずに状況を確認し、必要に応じて緊急対応を行う。

### 16.5 緊急停止

異常なコスト増加または許容範囲を超える予算超過が発生した場合、可用性よりコスト抑制を優先し、課金リソースを可能な範囲で停止する。

AWS標準機能で対応できる範囲を優先し、標準機能だけでは不足する場合はLambda等を利用した自動停止処理を検討する。

対象候補:

- ECS ServiceのDesired Countを0へ変更
- RDSの停止
- その他、継続課金が発生するリソースへの対応
- 管理者への緊急通知

ALB等、単純な「停止」が存在しないサービスについては、削除・再作成を含む復旧方法と合わせて詳細設計する。

料金情報・異常検知には反映遅延が存在し得るため、15,000円を厳密なハードキャップとはみなさず、余裕を持った検知・停止条件を設定する。

### 16.6 Spend limits

AWSアカウントでSpend limitsが利用可能な場合は、AWS標準機能による支出制御を優先して検討する。

利用可否および適用可能範囲はAWSアカウント作成後に確認する。

### 16.7 Pricing Calculator

AWSアカウント作成・リソース構築前にAWS Pricing Calculatorを利用して、構成全体の月額料金を見積もる。

少なくとも以下を含めた総額を確認する。

- ECS / Fargate
- ECR
- RDS PostgreSQL
- ALB
- AWS WAF
- Route 53
- CloudWatch
- Public IPv4
- データ転送
- その他、本構成で課金対象となるサービス
- 必要に応じて税・独自ドメイン費用を別途確認

初期見積もりでは、主要AWSリソースの通常運用時の料金として以下を確認した。

```text
AWS Pricing Calculator : 72.95 USD / 月
為替レート             : 1 USD = 156.59円
円換算                  : 約11,423円 / 月
```

独自ドメイン登録・更新費用、税、実際のデータ転送量等による変動費は必要に応じて別途考慮する。

為替レートおよびAWSサービス料金は変動する可能性があるため、上記金額を固定的な運用費とはみなさず、AWS構築前および必要に応じて運用中にも再見積もりを行う。

通常アクセス時の総額が許容予算を大きく超える場合は、AWS構築前に構成を見直す。

見積結果はPDFおよびJSON等の形式で保存し、設計・運用資料として管理する。

---

## 17. AWS投入前提条件

AWSアカウント作成および本番環境構築は、MVPとして必要な主要機能が完成し、AWSへデプロイ可能な状態であることを確認してから開始する。

Viewのブラッシュアップなど、AWS構築に影響しない非必須の改善については、AWS環境の構築および初回デプロイ確認後、本番公開前に実施してもよい。

想定フロー:

```text
AWS基本設計
    |
MVP主要機能完成
    |
Docker化
    |
Docker総合確認
  +----------------------+
  | 機能                 |
  | PostgreSQL接続       |
  | Security             |
  | Tests                |
  | 本番向け設定         |
  | 秘密情報             |
  +----------+-----------+
             |
AWS Pricing Calculator
             |
総額が許容予算内か確認
             |
        GO / NO-GO
             |
       AWSアカウント作成
             |
         AWS構築
             |
      初回手動デプロイ
             |
       AWS上で動作確認
             |
   非必須の残タスク対応
   （View改善など）
             |
          CD構築
             |
     本番デプロイ確認
             |
           公開
             |
         就職活動
```

AWSアカウントを先に作成して試行錯誤するのではなく、ローカルDocker環境および設計で可能な限り問題を解消してからAWS構築を開始し、クレジットおよび有料利用期間を有効活用する。

---

## 18. 詳細設計・構築時の決定事項

以下は基本設計時点では固定せず、AWS構築時に実際の設定項目・料金・動作を確認した上で決定する。

- VPC / Subnetの具体的なCIDR
- Availability Zoneの具体的な配置
- ECS / Fargateの最終vCPU・Memory
- RDSの最終インスタンスクラス
- RDSストレージ容量
- RDSバックアップ保持期間
- ALB Health Check Path
- ALB Health Check間隔・閾値
- CloudWatch Logs保持期間
- CloudWatch Alarmの監視対象・閾値
- WAF Rate-based Ruleの具体的な制限値
- Cost Anomaly Detectionの具体的な通知・判定条件
- コスト緊急停止処理の具体的な実装
- 緊急停止後の復旧手順
- Spend limitsの利用可否
- AWS構築時点の料金・為替レートによるPricing Calculatorの再見積もり
- 独自ドメイン名および取得方法
- CI/CDで利用するIAM権限およびGitHub Actionsとの認証方式

---

## 19. 初期構成で採用しないもの

初期構成では以下を採用しない。

- NAT Gateway
- VPC Endpoint
- RDS Multi-AZ
- ECS Auto Scaling
- 複数Fargate Taskによる冗長化
- Shield Advanced
- 過剰なCloudWatch監視・長期ログ保存

必要性が発生した場合は、コスト・セキュリティ・可用性を再評価した上で追加する。

---

## 20. 将来の拡張方針

就職活動後も継続運用する場合、必要に応じて以下を検討する。

- Private SubnetへのECS Task移行
- NAT GatewayまたはVPC Endpointの導入
- ECS Auto Scaling
- Fargate Taskの冗長化
- RDS Multi-AZ
- WAFルールの拡充
- CloudWatch監視の強化
- バックアップ・復旧設計の強化
- Infrastructure as Code
- CI/CDの改善
- コスト最適化
