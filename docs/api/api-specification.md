# API設計書

## 1. 概要

本ドキュメントは、設備点検管理システムのREST APIの意図する設計を記載する。

本書の仕様は、要件定義書、テーブル定義書および画面設計書で確定した要件を基準とする。現在の実装との差異は実装上の課題であり、本書に記載した未実装の振る舞いが実装済みであることを意味しない。

---

## 2. 共通仕様

### 2.1 Base URL

ローカル開発環境:

```text
http://localhost:8080
```

### 2.2 Content-Type

JSONをRequest Bodyとして受け取るAPIでは以下を使用する。

```http
Content-Type: application/json
```

### 2.3 認証・認可

`/api/**` 配下のAPIはSpring Securityによる認証・認可の対象とする。

| 項目           | 内容                           |
| -------------- | ------------------------------ |
| 認証方式       | Spring Security セッション認証 |
| セッション     | `JSESSIONID`                   |
| 必要Role       | エンドポイントごとに定義       |
| 未認証アクセス | ログイン画面へリダイレクト     |
| 権限不足       | `403 Forbidden`                |

### 2.4 CSRF

CSRF保護は有効である。

状態を変更する以下のHTTP MethodではCSRFトークンが必要となる。

- `POST`
- `PUT`
- `DELETE`

H2 ConsoleのみCSRF保護対象外としている。

### 2.5 Validation Error

Bean Validationに違反した場合は `400 Bad Request` を返す。

レスポンス例:

```json
{
  "status": 400,
  "message": "Validation failed",
  "errors": {
    "equipmentCode": "設備コードは必須です",
    "name": "設備名は必須です"
  }
}
```

### 2.6 共通エラー

| Status            | 条件                                   |
| ----------------- | -------------------------------------- |
| `400 Bad Request` | Validation違反、不正な業務入力         |
| `403 Forbidden`   | 必要なRoleを持っていない               |
| `404 Not Found`   | 対象リソースが存在しない               |
| `409 Conflict`    | 重複するリソースを登録しようとした場合 |

認証済みであっても、対象データの登録者に基づく操作権限を満たさない場合は `403 Forbidden` とする。各エンドポイントの権限は第8章を参照する。

`ResourceNotFoundException` のレスポンス形式:

```json
{
  "status": 404,
  "message": "Resource not found"
}
```

`DuplicateResourceException` のレスポンス形式:

```json
{
  "status": 409,
  "message": "設備コードは既に登録されています: EQ-001"
}
```

---

# 3. 設備管理API

Base Path:

```text
/api/equipment
```

参照APIは `ADMIN`、`INSPECTOR` の双方、登録・更新・廃止APIは `ADMIN` の認証が必要。

## 3.1 設備一覧取得

```http
GET /api/equipment
```

登録されている設備を一覧取得する。

### Response

`200 OK`

```json
[
  {
    "id": 1,
    "equipmentCode": "EQ-001",
    "name": "モーター設備A",
    "manufacturer": "テスト製作所",
    "model": "MTR-100",
    "location": "第1工場",
    "installedDate": "2025-01-15",
    "inspectionCycleDays": 30,
    "description": "画面動作確認用",
    "active": true,
    "createdAt": "2026-08-27T23:07:45",
    "updatedAt": "2026-08-27T23:07:45"
  }
]
```

---

## 3.2 設備詳細取得

```http
GET /api/equipment/{id}
```

指定されたIDの設備を取得する。

### Path Variable

| Name | Type | Required |
| ---- | ---- | -------- |
| `id` | Long | Yes      |

### Response

成功:

```text
200 OK
```

対象が存在しない場合:

```text
404 Not Found
```

---

## 3.3 設備登録

```http
POST /api/equipment
```

設備を新規登録する。

### Request Body

```json
{
  "equipmentCode": "EQ-001",
  "name": "モーター設備A",
  "manufacturer": "テスト製作所",
  "model": "MTR-100",
  "location": "第1工場",
  "installedDate": "2025-01-15",
  "inspectionCycleDays": 30,
  "description": "画面動作確認用",
  "active": true
}
```

### Request Fields

| Field                 | Type      | Required | Validation            |
| --------------------- | --------- | -------: | --------------------- |
| `equipmentCode`       | String    |      Yes | 空白不可、20文字以内  |
| `name`                | String    |      Yes | 空白不可、100文字以内 |
| `manufacturer`        | String    |       No | 100文字以内           |
| `model`               | String    |       No | 100文字以内           |
| `location`            | String    |       No | 100文字以内           |
| `installedDate`       | LocalDate |       No | -                     |
| `inspectionCycleDays` | Integer   |       No | 1以上                 |
| `description`         | String    |       No | 500文字以内           |
| `active`              | Boolean   |       No | -                     |

### Response

成功:

```text
200 OK
```

Validation違反:

```text
400 Bad Request
```

設備コード重複:

```text
409 Conflict
```

---

## 3.4 設備更新

```http
PUT /api/equipment/{id}
```

指定された設備を更新する。

### Path Variable

| Name | Type | Required |
| ---- | ---- | -------- |
| `id` | Long | Yes      |

Request Bodyは設備登録APIと同じ。

### Response

成功:

```text
200 OK
```

Validation違反:

```text
400 Bad Request
```

対象設備が存在しない場合:

```text
404 Not Found
```

設備コード重複:

```text
409 Conflict
```

---

## 3.5 設備廃止

```http
DELETE /api/equipment/{id}
```

指定された設備を物理削除せず、廃止状態に変更する。

### Response

成功:

```text
204 No Content
```

対象設備が存在しない場合:

```text
404 Not Found
```

---

# 4. 点検項目管理API

Base Path:

```text
/api/inspection-items
```

参照APIは `ADMIN`、`INSPECTOR` の双方、登録・更新・無効化APIは `ADMIN` の認証が必要。

## 4.1 点検項目一覧取得

```http
GET /api/inspection-items
```

### Response

```text
200 OK
```

---

## 4.2 点検項目詳細取得

```http
GET /api/inspection-items/{id}
```

### Response

成功:

```text
200 OK
```

対象が存在しない場合:

```text
404 Not Found
```

---

## 4.3 設備別点検項目取得

```http
GET /api/inspection-items/equipment/{equipmentId}
```

指定された設備に紐づく点検項目を取得する。

### Path Variable

| Name          | Type | Required |
| ------------- | ---- | -------- |
| `equipmentId` | Long | Yes      |

存在しない設備を指定した場合:

```text
404 Not Found
```

---

## 4.4 点検項目登録

```http
POST /api/inspection-items
```

### Request Body

```json
{
  "equipmentId": 1,
  "name": "モーター温度",
  "type": "NUMERIC",
  "unit": "℃",
  "minValue": 0,
  "maxValue": 80,
  "normalBooleanValue": null,
  "description": "モーター表面温度",
  "displayOrder": 1,
  "active": true
}
```

### Request Fields

| Field                | Type       | Required | Validation            |
| -------------------- | ---------- | -------: | --------------------- |
| `equipmentId`        | Long       |      Yes | -                     |
| `name`               | String     |      Yes | 空白不可、100文字以内 |
| `type`               | Enum       |      Yes | `NUMERIC` / `BOOLEAN` |
| `unit`               | String     |       No | 20文字以内            |
| `minValue`           | BigDecimal |       No | 種別依存              |
| `maxValue`           | BigDecimal |       No | 種別依存              |
| `normalBooleanValue` | Boolean    |       No | 種別依存              |
| `description`        | String     |       No | 500文字以内           |
| `displayOrder`       | Integer    |      Yes | -                     |
| `active`             | Boolean    |      Yes | -                     |

### NUMERICルール

- `normalBooleanValue` は `null`
- `minValue` は任意
- `maxValue` は任意
- `minValue <= maxValue`
- `unit` は任意

### BOOLEANルール

- `normalBooleanValue` は必須
- `unit` は `null`
- `minValue` は `null`
- `maxValue` は `null`

### Response

成功:

```text
200 OK
```

不正な入力:

```text
400 Bad Request
```

対象設備が存在しない場合:

```text
404 Not Found
```

---

## 4.5 点検項目更新

```http
PUT /api/inspection-items/{id}
```

Request Bodyおよび業務Validationは点検項目登録と同じ。

### Response

成功:

```text
200 OK
```

不正な入力:

```text
400 Bad Request
```

対象が存在しない場合:

```text
404 Not Found
```

---

## 4.6 点検項目無効化

```http
DELETE /api/inspection-items/{id}
```

指定された点検項目を物理削除せず、無効状態に変更する。過去の点検結果と記録時点のスナップショットは保持する。

### Response

成功:

```text
204 No Content
```

対象が存在しない場合:

```text
404 Not Found
```

---

# 5. 点検管理API

Base Path:

```text
/api/inspections
```

一覧・詳細取得は履歴の閲覧・検索として `ADMIN`、`INSPECTOR` の双方、点検登録は `INSPECTOR` の認証が必要。

## 5.1 点検一覧取得

```http
GET /api/inspections
```

点検履歴を取得する。`ADMIN`、`INSPECTOR` の双方が利用できる。

設備、点検日の開始・終了、点検者および判定結果による絞り込みを行えるものとする。検索条件の具体的なクエリパラメータ名は、正本資料では確定していないため未解決事項とする。取消済み明細を集計から除外し、訂正後の最新結果を使用する。条件未指定時は点検日時の降順（同日時は点検IDの降順）とする。

### Response

```text
200 OK
```

---

## 5.2 点検詳細取得

```http
GET /api/inspections/{id}
```

点検ヘッダー、点検結果明細、および各明細の訂正・取消履歴を取得する。`ADMIN`、`INSPECTOR` の双方が利用でき、元の点検者および点検日時も返す。

### Response

成功:

```text
200 OK
```

対象が存在しない場合:

```text
404 Not Found
```

---

## 5.3 点検登録

```http
POST /api/inspections
```

### Request Body

```json
{
  "equipmentId": 1,
  "comment": "コメント"
}
```

### Request Fields

| Field         | Type   | Required | Validation  |
| ------------- | ------ | -------: | ----------- |
| `equipmentId` | Long   |      Yes | 有効な設備  |
| `comment`     | String |       No | 500文字以内 |

`userId` と `inspectionAt` はリクエストとして受け付けない。点検者は認証済みの `INSPECTOR`、点検日時は登録時のサーバー時刻を使用して記録し、クライアントによる指定・変更を認めない。

### Response

成功:

```text
200 OK
```

Validation違反:

```text
400 Bad Request
```

設備が存在しない場合:

```text
404 Not Found
```

---

## 5.4 点検更新

```http
PUT /api/inspections/{id}
```

現在の実装に存在する経路であるが、点検ヘッダーの更新対象と権限は確定要件から決定できない。特に、元の点検者と点検日時は訂正・取消後も変更してはならない。本経路を意図する設計として利用可能とは定義せず、未解決事項とする。

### Response（未確定）

成功時およびエラー時のHTTPステータスを含むレスポンス仕様は未確定である。本経路を確認済みの利用可能なAPIとして扱わない。

---

## 5.5 点検削除

```http
DELETE /api/inspections/{id}
```

現在の実装に存在する経路であるが、点検ヘッダー全体の削除は確定要件に定義がなく、点検結果および監査履歴を物理削除してはならない。本経路を意図する設計として利用可能とは定義せず、未解決事項とする。

### Response（未確定）

成功時およびエラー時のHTTPステータスを含むレスポンス仕様は未確定である。本経路を確認済みの利用可能なAPIとして扱わない。

---

# 6. 点検結果管理API

Base Path:

```text
/api/inspection-results
```

参照APIは履歴閲覧として `ADMIN`、`INSPECTOR` の双方、登録APIは `INSPECTOR` の認証が必要。訂正・取消APIは `ADMIN`、または対象点検を登録した `INSPECTOR` が利用できる。

## 6.1 点検結果一覧取得

```http
GET /api/inspection-results
```

### Response

```text
200 OK
```

---

## 6.2 点検結果詳細取得

```http
GET /api/inspection-results/{id}
```

現在値、取消状態、点検実施時の判定条件スナップショット、および訂正・取消履歴を取得する。

### Response

成功:

```text
200 OK
```

対象が存在しない場合:

```text
404 Not Found
```

---

## 6.3 点検単位の結果一覧取得

```http
GET /api/inspection-results/inspection/{inspectionId}
```

指定された点検に紐づく点検結果を取得する。

### Response

成功:

```text
200 OK
```

点検が存在しない場合:

```text
404 Not Found
```

---

## 6.4 点検結果登録

```http
POST /api/inspection-results
```

### Request Body

NUMERIC例:

```json
{
  "inspectionId": 1,
  "inspectionItemId": 1,
  "numericValue": 23,
  "booleanValue": null,
  "notApplicable": false,
  "comment": null
}
```

BOOLEAN例:

```json
{
  "inspectionId": 1,
  "inspectionItemId": 2,
  "numericValue": null,
  "booleanValue": false,
  "notApplicable": false,
  "comment": null
}
```

対象外例:

```json
{
  "inspectionId": 1,
  "inspectionItemId": 1,
  "numericValue": null,
  "booleanValue": null,
  "notApplicable": true,
  "comment": "今回は点検対象外"
}
```

### Request Fields

| Field              | Type       | Required | Validation  |
| ------------------ | ---------- | -------: | ----------- |
| `inspectionId`     | Long       |      Yes | -           |
| `inspectionItemId` | Long       |      Yes | -           |
| `numericValue`     | BigDecimal |       No | 種別依存    |
| `booleanValue`     | Boolean    |       No | 種別依存    |
| `notApplicable`    | Boolean    |      Yes | -           |
| `comment`          | String     |       No | 500文字以内 |

### 共通ルール

- 点検項目は対象設備に属している必要がある
- 同一の `inspectionId` と `inspectionItemId` の組み合わせを重複登録できない
- 対象の点検は、認証中の `INSPECTOR` が登録した点検でなければならない
- `result` はクライアントから受け付けず、サーバーが自動判定する
- 自動判定が `NG` の場合、`comment` は必須かつ空白のみ不可

### NOT_APPLICABLE

`notApplicable = true` の場合:

- `numericValue` は `null`
- `booleanValue` は `null`
- `result` は `NOT_APPLICABLE`

### NUMERIC

- `numericValue` 必須
- `booleanValue` は `null`
- `minValue` 未満の場合は `NG`
- `maxValue` 超過の場合は `NG`
- 範囲内の場合は `OK`

### BOOLEAN

- `booleanValue` 必須
- `numericValue` は `null`
- `normalBooleanValue` と一致する場合は `OK`
- 一致しない場合は `NG`

### Result

取り得る値:

```text
OK
NG
NOT_APPLICABLE
```

### Response

成功:

```text
200 OK
```

不正な入力:

```text
400 Bad Request
```

点検または点検項目が存在しない場合:

```text
404 Not Found
```

---

## 6.5 点検結果更新

```http
PUT /api/inspection-results/{id}
```

点検結果明細1件を訂正する。`ADMIN` は全件、`INSPECTOR` は元の点検者が自分である明細のみ訂正できる。権限はサーバー側で、認証中の利用者のロールと `inspection.user_id` により検証する。

### Request Body

NUMERIC訂正例:

```json
{
  "numericValue": 25.5,
  "booleanValue": null,
  "notApplicable": false,
  "comment": null,
  "reason": "測定値の転記誤りを訂正"
}
```

### Request Fields

| Field           | Type       | Required | Validation                  |
| --------------- | ---------- | -------: | --------------------------- |
| `numericValue`  | BigDecimal |       No | 種別依存                    |
| `booleanValue`  | Boolean    |       No | 種別依存                    |
| `notApplicable` | Boolean    |      Yes | -                           |
| `comment`       | String     |       No | 500文字以内。NGの場合は必須 |
| `reason`        | String     |      Yes | 500文字以内、空白のみ不可   |

`inspectionId`、`inspectionItemId`、`result`、元の点検者および点検日時は変更対象にしない。訂正後の判定は、点検実施時に保存した正常条件のスナップショットからサーバーが再計算する。NGの場合は空白でないコメントを必須とする。

訂正前後の値・判定・コメント、理由、認証済みの操作ユーザー、およびサーバー時刻を `CORRECT` の履歴として追記する。現在値の更新と履歴の追加は同一トランザクションで行い、取消済み明細は訂正できない。

### Response

成功:

```text
200 OK
```

不正な入力:

```text
400 Bad Request
```

対象が存在しない場合:

```text
404 Not Found
```

権限がない場合:

```text
403 Forbidden
```

取消済みの場合:

```text
400 Bad Request
```

---

## 6.6 点検結果取消

```http
DELETE /api/inspection-results/{id}
```

指定された点検結果明細を論理的に取り消す。物理削除は行わない。`ADMIN` は全件、`INSPECTOR` は元の点検者が自分である明細のみ取り消せる。

### Request Body（暫定設計案）

取消理由は必須である。ただし、HTTPリクエストでの受渡し形式は確定していない。以下のJSONリクエストボディは、既存のエンドポイントパスとHTTPメソッドを維持するための暫定的な設計案であり、確定したAPI契約ではない。

```json
{
  "reason": "重複登録のため取消"
}
```

| Field    | Type   | Required | Validation                |
| -------- | ------ | -------: | ------------------------- |
| `reason` | String |      Yes | 500文字以内、空白のみ不可 |

対象の `cancelled` を `true` とし、測定値・真偽値・判定・コメント、元の点検者および点検日時を保持する。取消前の値、理由、認証済みの操作ユーザー、およびサーバー時刻を `CANCEL` の履歴として追記する。取消状態の更新と履歴の追加は同一トランザクションで行う。取消済み明細の再訂正・再取消は認めない。

### Response

成功:

```text
204 No Content
```

対象が存在しない場合:

```text
404 Not Found
```

権限がない場合:

```text
403 Forbidden
```

取消済みの場合:

```text
400 Bad Request
```

---

# 7. Response DTO

## 7.1 EquipmentResponse

| Field                 | Type          |
| --------------------- | ------------- |
| `id`                  | Long          |
| `equipmentCode`       | String        |
| `name`                | String        |
| `manufacturer`        | String        |
| `model`               | String        |
| `location`            | String        |
| `installedDate`       | LocalDate     |
| `inspectionCycleDays` | Integer       |
| `description`         | String        |
| `active`              | Boolean       |
| `createdAt`           | LocalDateTime |
| `updatedAt`           | LocalDateTime |

## 7.2 EquipmentInspectionItemResponse

| Field                | Type                  |
| -------------------- | --------------------- |
| `id`                 | Long                  |
| `equipmentId`        | Long                  |
| `name`               | String                |
| `type`               | `NUMERIC` / `BOOLEAN` |
| `unit`               | String                |
| `minValue`           | BigDecimal            |
| `maxValue`           | BigDecimal            |
| `normalBooleanValue` | Boolean               |
| `description`        | String                |
| `displayOrder`       | Integer               |
| `active`             | Boolean               |
| `createdAt`          | LocalDateTime         |
| `updatedAt`          | LocalDateTime         |

## 7.3 InspectionResponse

| Field          | Type          |
| -------------- | ------------- |
| `id`           | Long          |
| `equipmentId`  | Long          |
| `userId`       | Long          |
| `inspectionAt` | LocalDateTime |
| `comment`      | String        |
| `createdAt`    | LocalDateTime |
| `updatedAt`    | LocalDateTime |

## 7.4 InspectionResultResponse

| Field              | Type                              |
| ------------------ | --------------------------------- |
| `id`               | Long                              |
| `inspectionId`     | Long                              |
| `inspectionItemId` | Long                              |
| `numericValue`     | BigDecimal                        |
| `booleanValue`     | Boolean                           |
| `result`           | `OK` / `NG` / `NOT_APPLICABLE`    |
| `comment`          | String                            |
| `cancelled`        | Boolean                           |
| `history`          | InspectionResultHistoryResponse[] |
| `createdAt`        | LocalDateTime                     |
| `updatedAt`        | LocalDateTime                     |

`history` は操作種別（`CORRECT` / `CANCEL`）、操作ユーザー、操作日時、理由、および変更前後の値・判定・コメントを返す。元の登録者は `InspectionResponse.userId` に保持し、訂正・取消の操作者で上書きしない。

## 7.5 InspectionResultHistoryResponse

| Field             | Type                    |
| ----------------- | ----------------------- |
| `id`              | Long                    |
| `action`          | `CORRECT` / `CANCEL`    |
| `changedBy`       | Long                    |
| `changedAt`       | LocalDateTime           |
| `reason`          | String                  |
| `oldNumericValue` | BigDecimal              |
| `newNumericValue` | BigDecimal              |
| `oldBooleanValue` | Boolean                 |
| `newBooleanValue` | Boolean                 |
| `oldResult`       | InspectionResult        |
| `newResult`       | InspectionResult / null |
| `oldComment`      | String                  |
| `newComment`      | String                  |

取消時の `newResult` は `null` とする。履歴は追記のみとし、更新・物理削除しない。

---

# 8. API一覧

| Method | Endpoint                                            | 概要                       | Role                         |
| ------ | --------------------------------------------------- | -------------------------- | ---------------------------- |
| GET    | `/api/equipment`                                    | 設備一覧・検索             | ADMIN, INSPECTOR             |
| POST   | `/api/equipment`                                    | 設備登録                   | ADMIN                        |
| GET    | `/api/equipment/{id}`                               | 設備詳細取得               | ADMIN, INSPECTOR             |
| PUT    | `/api/equipment/{id}`                               | 設備更新                   | ADMIN                        |
| DELETE | `/api/equipment/{id}`                               | 設備廃止                   | ADMIN                        |
| GET    | `/api/inspection-items`                             | 点検項目一覧取得           | ADMIN, INSPECTOR             |
| GET    | `/api/inspection-items/{id}`                        | 点検項目詳細取得           | ADMIN, INSPECTOR             |
| POST   | `/api/inspection-items`                             | 点検項目登録               | ADMIN                        |
| PUT    | `/api/inspection-items/{id}`                        | 点検項目更新               | ADMIN                        |
| DELETE | `/api/inspection-items/{id}`                        | 点検項目無効化             | ADMIN                        |
| GET    | `/api/inspection-items/equipment/{equipmentId}`     | 設備別点検項目取得         | ADMIN, INSPECTOR             |
| GET    | `/api/inspections`                                  | 点検履歴一覧・検索         | ADMIN, INSPECTOR             |
| GET    | `/api/inspections/{id}`                             | 点検履歴詳細・監査履歴取得 | ADMIN, INSPECTOR             |
| POST   | `/api/inspections`                                  | 点検登録                   | INSPECTOR                    |
| PUT    | `/api/inspections/{id}`                             | 点検ヘッダー更新（未解決） | 未確定                       |
| DELETE | `/api/inspections/{id}`                             | 点検ヘッダー削除（未解決） | 未確定                       |
| POST   | `/api/inspection-results`                           | 点検結果登録               | INSPECTOR                    |
| GET    | `/api/inspection-results`                           | 点検結果一覧取得           | ADMIN, INSPECTOR             |
| GET    | `/api/inspection-results/{id}`                      | 点検結果・監査履歴取得     | ADMIN, INSPECTOR             |
| GET    | `/api/inspection-results/inspection/{inspectionId}` | 点検単位の結果一覧取得     | ADMIN, INSPECTOR             |
| PUT    | `/api/inspection-results/{id}`                      | 点検結果訂正               | ADMIN, 登録者本人のINSPECTOR |
| DELETE | `/api/inspection-results/{id}`                      | 点検結果取消（論理取消）   | ADMIN, 登録者本人のINSPECTOR |

---

# 9. 要件対応と未解決事項

本設計の点検結果訂正・取消は、FR-031～FR-035、FR-056～FR-060、およびBR-043～BR-047に対応する。登録時の自動判定・NGコメント必須・点検者と点検日時の自動記録は、FR-037～FR-043に対応する。

以下は正本資料だけではAPIの詳細を確定できないため、実装時に別途決定する。

- 点検履歴検索のクエリパラメータ名、日付形式およびページング表現。検索機能と検索条件、包含範囲、並び順および1ページ20件という要件は確定している。
- `PUT /api/inspections/{id}` と `DELETE /api/inspections/{id}` の扱い。現在の実装には経路が存在するが、点検ヘッダーの更新・削除は確定要件に定義されていない。少なくとも元の点検者・点検日時・点検結果・監査履歴を変更または物理削除してはならない。
- 取消理由を伴う `DELETE /api/inspection-results/{id}` のリクエストボディ形式。取消理由の必須性は確定しているが、正本資料ではHTTP上の受渡し形式までは定義されていない。本書では既存のHTTPメソッドを維持する案を示している。
