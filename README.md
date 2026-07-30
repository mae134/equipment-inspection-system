## Git運用

このリポジトリでは、以下のGitフローを採用しています。

```text
feature/*
fix/*
chore/*
      │
      ▼
     dev
      │
      ▼
     main
```

### 開発フロー

1. `dev` ブランチから作業ブランチを作成します。
2. 作業ブランチで実装・テストを行います。
3. `dev` ブランチ宛てにPull Requestを作成します。
4. GitHub ActionsによるCIが成功したことを確認します。
5. レビュー完了後、`dev` ブランチへマージします。
6. リリース時に `dev` から `main` へPull Requestを作成します。
7. レビューおよびCI成功後、`main` へマージします。

> **注意**
>
> `feature/*`、`fix/*`、`chore/*` ブランチから `main` へ直接Pull Requestを作成してはいけません。  
> 必ず一度 `dev` ブランチへマージした後、`dev` から `main` へPull Requestを作成してください。

## ブランチ保護

### main

- 直接Pushを禁止する
- Pull Request経由でのみ変更を反映する
- `dev` ブランチからのPull Requestのみ許可する
- 必須のGitHub Actionsが成功していることをマージ条件とする
- Force Pushを禁止する
- ブランチ削除を禁止する

### dev

- 直接Pushを禁止する
- Pull Request経由でのみ変更を反映する
- 必須のGitHub Actionsが成功していることをマージ条件とする
- Force Pushを禁止する

## GitHub Actions

現在、以下のGitHub Actionsを利用しています。

| ワークフロー       | 役割                                                                              |
| ------------------ | --------------------------------------------------------------------------------- |
| CI                 | Java 21環境でコードフォーマット、ビルド、テスト、および品質チェックを実行します。 |
| Validate PR Branch | `main` へのPull Requestが `dev` ブランチから作成されていることを検証します。      |

## ブランチの後片付け

Pull Requestのマージ後は、以下の手順でローカル環境を整理します。

1. GitHubでマージ済みのリモートブランチを自動削除します。
2. `scripts/finish-issue.sh` を実行します。

このスクリプトでは、以下の処理を自動で実行します。

- `dev` ブランチへ切り替え
- `origin/dev` の最新状態を取得
- 不要になったリモート追跡ブランチの削除
- マージ済みローカルブランチの削除

## コード品質

このプロジェクトでは、コード品質を維持するために以下のツールを使用しています。

| ツール         | 目的                                           |
| -------------- | ---------------------------------------------- |
| Spotless       | Javaコードのフォーマットを統一                 |
| Checkstyle     | コーディングルール・静的解析                   |
| GitHub Actions | Push・Pull Request時に自動で品質チェックを実行 |

### コードフォーマット

Javaコードを自動整形します。

```bash
./mvnw spotless:apply
```

フォーマットが適用されているか確認します。

```bash
./mvnw spotless:check
```

### 静的解析

コーディングルールに違反していないか確認します。

```bash
./mvnw checkstyle:check
```

### ビルド・テスト・品質チェック

ビルド、テスト、および品質チェックをまとめて実行します。

```bash
./mvnw verify
```

### CI

GitHub Actionsでは以下の順番で自動実行されます。

1. Spotlessによるフォーマットチェック
2. Maven Verify（コンパイル・テスト・パッケージ・Checkstyle）

## Git Hooks

このプロジェクトでは、Git Hooksの管理に **Husky**、コミットメッセージの検証に **Commitlint** を使用しています。

### セットアップ

依存ライブラリをインストールします。

```bash
npm install
```

Git Hooksは以下のコマンドで自動的にセットアップされます。

```bash
npm run prepare
```

### コミットメッセージ

Conventional Commits のルールに従ってコミットメッセージを記述してください。

例：

```text
feat: add inspection result API
fix: resolve login validation bug
refactor: simplify inspection service
docs: update README
test: add user service tests
chore: configure git hooks

### Hook一覧

| Hook       | 役割                                                 |
| ---------- | ---------------------------------------------------- |
| pre-commit | Spotlessによるコードフォーマットチェック             |
| commit-msg | Conventional Commitsに従ったコミットメッセージを検証 |
```
