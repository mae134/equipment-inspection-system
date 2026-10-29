# Docker Compose Development Environment

## 1. Overview

ローカル開発環境では、PostgreSQLをDocker Composeで起動し、
Spring BootアプリケーションはWSL上で直接起動する。

Spring Bootアプリケーション自体のDocker化は別Issueで対応する。

```text
Browser
   |
   | HTTP :8080
   v
Spring Boot
WSL / Local
   |
   | JDBC
   | localhost:5433
   v
Docker Compose
   |
   | Host 5433 -> Container 5432
   v
PostgreSQL 17
```

## 2. Prerequisites

以下が利用可能であること。

- Docker
- Docker Compose
- Java
- Maven Wrapper

Docker環境は以下のコマンドで確認できる。

```bash
docker version
docker compose version
```

## 3. Environment Variables

リポジトリルートの `.env.example` を参考に `.env` を作成する。

```bash
cp .env.example .env
```

`.env` の `DB_PASSWORD` にローカル開発用のPostgreSQLパスワードを設定する。

```env
DB_PASSWORD=<local-development-password>
```

`.env` はGit管理対象外とし、実際の認証情報をコミットしない。

## 4. Start PostgreSQL

リポジトリルートで以下を実行する。

```bash
docker compose up -d
```

起動状態を確認する。

```bash
docker compose ps
```

PostgreSQLコンテナが `healthy` になっていることを確認する。

## 5. Port Configuration

Docker Compose上のPostgreSQLでは以下のポートマッピングを使用する。

```text
Host      : 5433
Container : 5432
```

ローカルWSL環境ですでにPostgreSQLが `localhost:5432` を使用しているため、
Docker Compose側ではホストポート `5433` を使用する。

PostgreSQLコンテナ内部では標準ポート `5432` を使用する。

## 6. Start Spring Boot

Spring BootアプリケーションはWSL上で直接起動する。

```bash
DB_PASSWORD=<local-development-password> \
DB_URL=jdbc:postgresql://localhost:5433/equipment_inspection \
DB_USERNAME=equipment_user \
./mvnw spring-boot:run
```

正常に起動した場合、以下へアクセスできる。

```text
http://localhost:8080
```

## 7. Connect to PostgreSQL

PostgreSQLコンテナへ接続する場合は以下を実行する。

```bash
docker compose exec postgres \
  psql -U equipment_user -d equipment_inspection
```

テーブル一覧は以下で確認できる。

```sql
\dt
```

接続を終了する。

```text
\q
```

## 8. Stop the Environment

Spring Bootアプリケーションは起動中のターミナルで `Ctrl+C` により停止する。

PostgreSQLコンテナは以下で停止する。

```bash
docker compose down
```

通常の停止ではNamed Volumeは削除されない。

Volumeを削除するとPostgreSQLのデータも削除されるため、
`docker compose down -v` はデータを削除してよい場合にのみ使用する。

## 9. Verification

開発環境構築後、以下を確認する。

```bash
docker compose ps
./mvnw verify
```

確認項目:

- PostgreSQLコンテナが `healthy` である
- Spring BootからPostgreSQLへ接続できる
- Spring Bootが正常に起動する
- ログイン画面を表示できる
- ログイン後にダッシュボードを表示できる
- PostgreSQL上の設備データを画面から参照できる
- Maven Verifyが成功する

## 10. Notes

通常のJavaコード変更では、Docker Imageの再作成は必要ない。

現在の開発環境ではSpring BootをWSL上で直接起動し、
PostgreSQLのみDocker Composeで管理する。

Spring BootアプリケーションのDocker Image作成および
アプリケーションコンテナからPostgreSQLコンテナへの接続は後続Issueで対応する。
