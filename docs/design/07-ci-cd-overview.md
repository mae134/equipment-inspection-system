# CI/CD概要

## 1. 目的

設備点検管理システムのGitHub Actionsによる継続的インテグレーション（CI）とAWS ECSへの継続的デプロイ（CD）の構成、実行条件、検証内容を記録する。

## 2. ワークフロー構成

| ワークフロー       | ファイル                                   | 役割                                            |
| ------------------ | ------------------------------------------ | ----------------------------------------------- |
| Conditional CI     | `.github/workflows/conditional-ci.yml`     | 変更ファイルに応じたJava・Terraformの検証       |
| Validate PR Branch | `.github/workflows/validate-pr-branch.yml` | `main`向けPRの送信元ブランチ検証                |
| Deploy to AWS      | `.github/workflows/deploy.yml`             | Maven検証、ECRへのイメージ登録、ECSへのデプロイ |

標準的な変更の流れは `作業ブランチ → PR → dev → PR → main`。PR作成・マージは人が行い、ワークフローはそれぞれのイベントで起動する。CIとCDは独立したワークフローであり、CDがCI Gateの結果を直接待機する依存関係はない。

## 3. Conditional CI

### 3.1 実行条件と変更検知

`main`または`dev`に対する`pull_request`、および両ブランチへの`push`で起動する。`dorny/paths-filter@v3`で変更ファイルを検知する。

- **Java**：`src/**`、`pom.xml`、`mvnw`、`mvnw.cmd`、`.mvn/**`、`.github/workflows/ci.yml`、`.github/workflows/conditional-ci.yml`
- **Terraform**：`terraform/**`、`.github/workflows/terraform-ci.yml`、`.github/workflows/conditional-ci.yml`

両方の対象に変更があれば両検証を実行し、対象外のジョブはスキップする。

### 3.2 Java Verify

Temurin Java 21とMaven Wrapperを使用し、以下を実行する。

```bash
./mvnw spotless:check
./mvnw verify
```

### 3.3 Terraform Validate

`terraform/persistent`と`terraform/runtime`をmatrixでそれぞれ検証する。

```bash
terraform fmt -check -recursive
terraform init -backend=false -input=false
terraform validate
```

CIではTerraformの`plan`、`apply`、`destroy`は実行しない。

### 3.4 CI Gate

変更検知の成功と、必要なJava・Terraform検証の成功を判定する。変更対象外のジョブについては`skipped`を期待し、必要な検証が失敗・キャンセルされた場合はGateを失敗させる。ブランチ保護における必須チェックとして設定できるが、GitHub側の保護ルールの設定状況はこのYAMLだけでは確認できない。

## 4. Validate PR Branch

`main`向けPRで起動し、次の条件を確認する。

- 送信元ブランチが`dev`であること
- 送信元リポジトリと送信先リポジトリが同一であること

条件を満たさない場合は失敗する。このワークフロー自体がPRをマージすることはない。

## 5. Deploy to AWS

### 5.1 起動条件

`main`へのpushで次のパスに変更がある場合、または`workflow_dispatch`による手動実行で起動する。

`src/**`、`pom.xml`、`Dockerfile`、`mvnw`、`mvnw.cmd`、`.mvn/**`、`.github/workflows/deploy.yml`

Terraformやドキュメントだけのpushは、このパス条件では自動デプロイ対象にならない。`concurrency`グループは`aws-production-deploy`、`cancel-in-progress: false`である。

### 5.2 処理フロー

1. リポジトリをチェックアウトし、Temurin Java 21で`./mvnw -B verify`を実行する。
2. OIDCトークンの`sub`と`aud`を診断用に表示し、`aws-actions/configure-aws-credentials@v4`でAWS IAMロールを引き受ける。
3. ECSサービスを照会する。`ACTIVE`なら`aws ecs wait services-stable`で安定化を待つ。サービスが存在しない、`INACTIVE`または`DRAINING`の場合は後続のデプロイ処理をスキップする。想定外の状態は失敗とする。
4. デプロイ可能な場合、ECRにログインしてDockerイメージをビルドし、コミットSHAタグと`latest`タグをpushする。
5. ECSサービスの現行タスク定義を取得し、対象コンテナのイメージをコミットSHA付きURIに差し替えて新リビジョンを登録する。
6. ECSサービスを新タスク定義へ更新し、安定化を待機する。
7. ECSサービスが参照するタスク定義ARNと新規登録したARNが一致することを確認する。一致しなければ失敗する。

### 5.3 AWSリソースと認証

| 項目           | 設定                                              |
| -------------- | ------------------------------------------------- |
| リージョン     | `ap-northeast-1`                                  |
| OIDC引受ロール | `equipment-inspection-github-actions-deploy-role` |
| ECSクラスター  | `equipment-inspection-cluster`                    |
| ECSサービス    | `equipment-inspection-service`                    |
| ECRリポジトリ  | `equipment-inspection-app`                        |
| 対象コンテナ   | `equipment-inspection-app`                        |

GitHub Actionsには`contents: read`と`id-token: write`を付与している。OIDCを使うため、ワークフロー内で長期有効なAWSアクセスキーを設定する必要はない。IAM信頼ポリシーの具体的な制約は、AWS側の設定を別途確認する必要がある。

### 5.4 AWS環境が停止している場合

ECSサービスが存在しない場合などは、ECRへのpushとECS更新をスキップする。CDワークフロー自体はTerraformによるAWS環境の構築・復元を実行しない。

## 6. デプロイ失敗時の扱いと制約

- GitHub ActionsはECSサービスの安定化とタスク定義ARNの一致を確認するが、公開URLへのHTTPリクエストによる疎通確認ステップはない。
- ワークフロー内に、旧タスク定義へ明示的に戻す独自のロールバック処理はない。ECS側のロールバック動作はサービス設定に依存する。
- `services-stable`の待機が失敗した場合や、更新後のタスク定義が期待値と異なる場合はデプロイジョブが失敗する。
- 2026年11月2日の障害では、GitHub Actions経由のデプロイが失敗した後、同じタスク定義への手動再デプロイで復旧した。手動復旧の成功は、CDワークフロー全体の再検証完了を意味しない。
- ECSデプロイ時のヘルスチェック安定性改善はIssue #9で対応した。ALBターゲットグループの `healthy_threshold` を5から2に変更し、AWS環境再構築後にGitHub Actions CDが2回連続成功したことを確認した。
- ただし、環境再構築も実施しているため、Healthy判定回数が単独の根本原因だったとは断定していない。詳細は `docs/operations/ecs-deployment-incident.md` を参照する。

## 7. 確認方法

リリース時には必要に応じて、GitHub Actionsの実行結果、ECRのイメージタグ、ECSサービス・タスク定義、ALBターゲットのヘルスチェック状態、公開HTTPSエンドポイントの応答を確認する。

本書は現行のワークフローの概要を説明するものであり、AWS環境の構築・破壊手順および個別障害の詳細な時系列は別文書で扱う。
