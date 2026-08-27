# API設計書

## 1. 概要

本ドキュメントは、設備点検管理システムで提供しているREST APIの仕様を記載する。

現在実装されているController、Request / Response DTO、Service、およびSpring Security設定を基準とする。

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
| 必要Role       | `ADMIN`                        |
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

すべてのAPIで認証および `ADMIN` Roleが必要。

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

## 3.5 設備削除

```http
DELETE /api/equipment/{id}
```

指定された設備を削除する。

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

すべてのAPIで認証および `ADMIN` Roleが必要。

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

## 4.6 点検項目削除

```http
DELETE /api/inspection-items/{id}
```

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

すべてのAPIで認証および `ADMIN` Roleが必要。

## 5.1 点検一覧取得

```http
GET /api/inspections
```

### Response

```text
200 OK
```

---

## 5.2 点検詳細取得

```http
GET /api/inspections/{id}
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

## 5.3 点検登録

```http
POST /api/inspections
```

### Request Body

```json
{
  "equipmentId": 1,
  "userId": 1,
  "inspectionAt": "2026-08-27T23:21:00",
  "comment": "コメント"
}
```

### Request Fields

| Field          | Type          | Required | Validation  |
| -------------- | ------------- | -------: | ----------- |
| `equipmentId`  | Long          |      Yes | -           |
| `userId`       | Long          |      Yes | -           |
| `inspectionAt` | LocalDateTime |      Yes | -           |
| `comment`      | String        |       No | 500文字以内 |

### Response

成功:

```text
200 OK
```

Validation違反:

```text
400 Bad Request
```

設備またはユーザーが存在しない場合:

```text
404 Not Found
```

---

## 5.4 点検更新

```http
PUT /api/inspections/{id}
```

Request Bodyは点検登録APIと同じ。

### Response

成功:

```text
200 OK
```

Validation違反:

```text
400 Bad Request
```

点検、設備、またはユーザーが存在しない場合:

```text
404 Not Found
```

---

## 5.5 点検削除

```http
DELETE /api/inspections/{id}
```

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

# 6. 点検結果管理API

Base Path:

```text
/api/inspection-results
```

すべてのAPIで認証および `ADMIN` Roleが必要。

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

Request Bodyおよび業務ルールは点検結果登録と同じ。

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

## 6.6 点検結果削除

```http
DELETE /api/inspection-results/{id}
```

指定された点検結果を削除する。

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

| Field              | Type                           |
| ------------------ | ------------------------------ |
| `id`               | Long                           |
| `inspectionId`     | Long                           |
| `inspectionItemId` | Long                           |
| `numericValue`     | BigDecimal                     |
| `booleanValue`     | Boolean                        |
| `result`           | `OK` / `NG` / `NOT_APPLICABLE` |
| `comment`          | String                         |
| `createdAt`        | LocalDateTime                  |
| `updatedAt`        | LocalDateTime                  |

---

# 8. API一覧

| Method | Endpoint                                            | 概要                   | Role  |
| ------ | --------------------------------------------------- | ---------------------- | ----- |
| GET    | `/api/equipment`                                    | 設備一覧取得           | ADMIN |
| POST   | `/api/equipment`                                    | 設備登録               | ADMIN |
| GET    | `/api/equipment/{id}`                               | 設備詳細取得           | ADMIN |
| PUT    | `/api/equipment/{id}`                               | 設備更新               | ADMIN |
| DELETE | `/api/equipment/{id}`                               | 設備削除               | ADMIN |
| GET    | `/api/inspection-items`                             | 点検項目一覧取得       | ADMIN |
| GET    | `/api/inspection-items/{id}`                        | 点検項目詳細取得       | ADMIN |
| POST   | `/api/inspection-items`                             | 点検項目登録           | ADMIN |
| PUT    | `/api/inspection-items/{id}`                        | 点検項目更新           | ADMIN |
| DELETE | `/api/inspection-items/{id}`                        | 点検項目削除           | ADMIN |
| GET    | `/api/inspection-items/equipment/{equipmentId}`     | 設備別点検項目取得     | ADMIN |
| GET    | `/api/inspections`                                  | 点検一覧取得           | ADMIN |
| GET    | `/api/inspections/{id}`                             | 点検詳細取得           | ADMIN |
| POST   | `/api/inspections`                                  | 点検登録               | ADMIN |
| PUT    | `/api/inspections/{id}`                             | 点検更新               | ADMIN |
| DELETE | `/api/inspections/{id}`                             | 点検削除               | ADMIN |
| POST   | `/api/inspection-results`                           | 点検結果登録           | ADMIN |
| GET    | `/api/inspection-results`                           | 点検結果一覧取得       | ADMIN |
| GET    | `/api/inspection-results/{id}`                      | 点検結果詳細取得       | ADMIN |
| GET    | `/api/inspection-results/inspection/{inspectionId}` | 点検単位の結果一覧取得 | ADMIN |
| PUT    | `/api/inspection-results/{id}`                      | 点検結果更新           | ADMIN |
| DELETE | `/api/inspection-results/{id}`                      | 点検結果削除           | ADMIN |
