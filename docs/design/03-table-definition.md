# テーブル定義書

## 1. 概要

本書は、設備点検管理システムで使用するデータベーステーブルの定義を記載する。

対象テーブルは以下の5テーブルとする。

| No. | 論理名       | 物理名                      | 概要                                    |
| --: | ------------ | --------------------------- | --------------------------------------- |
|   1 | ユーザー     | `users`                     | ログインユーザーおよび権限を管理する    |
|   2 | 設備         | `equipment`                 | 点検対象となる設備を管理する            |
|   3 | 設備点検項目 | `equipment_inspection_item` | 設備ごとの点検項目マスタを管理する      |
|   4 | 点検         | `inspection`                | 1回の点検に関するヘッダー情報を管理する |
|   5 | 点検結果     | `inspection_result`         | 各点検項目の実施結果を管理する          |

---

## 2. 共通ルール

- 主キーは `BIGINT` 型の `id` とする。
- 日時は `TIMESTAMP` 型で管理する。
- 作成日時と更新日時は、それぞれ `created_at`、`updated_at` とする。
- Java側では主キーおよび外部キーを `Long` 型として扱う。
- Java側で固定値を扱う項目は `Enum` の利用を検討する。
- パスワードは平文で保存せず、ハッシュ化した値を保存する。
- 外部キーで参照されているデータは、原則として物理削除しない。

---

## 3. users

### 3.1 テーブル概要

| 項目   | 内容                                                         |
| ------ | ------------------------------------------------------------ |
| 論理名 | ユーザー                                                     |
| 物理名 | `users`                                                      |
| 説明   | ログインユーザーの認証情報、表示名、権限、有効状態を管理する |

### 3.2 カラム定義

| No. | 論理名             | カラム名        | データ型       | PK  | FK  | NULL | UNIQUE | デフォルト | 説明                               |
| --: | ------------------ | --------------- | -------------- | :-: | :-: | :--: | :----: | ---------- | ---------------------------------- |
|   1 | ユーザーID         | `id`            | `BIGINT`       |  ○  |     | 不可 |   ○    | 自動採番   | ユーザーを識別するID               |
|   2 | 氏名               | `name`          | `VARCHAR(100)` |     |     | 不可 |        |            | 画面に表示するユーザー名           |
|   3 | メールアドレス     | `email`         | `VARCHAR(255)` |     |     | 不可 |   ○    |            | ログイン時に使用するメールアドレス |
|   4 | パスワードハッシュ | `password_hash` | `VARCHAR(255)` |     |     | 不可 |        |            | ハッシュ化されたパスワード         |
|   5 | 権限               | `role`          | `VARCHAR(20)`  |     |     | 不可 |        |            | `ADMIN` または `INSPECTOR`         |
|   6 | 有効状態           | `active`        | `BOOLEAN`      |     |     | 不可 |        | `TRUE`     | ログイン可能なユーザーかを表す     |
|   7 | 作成日時           | `created_at`    | `TIMESTAMP`    |     |     | 不可 |        |            | レコード作成日時                   |
|   8 | 更新日時           | `updated_at`    | `TIMESTAMP`    |     |     | 不可 |        |            | レコード更新日時                   |

### 3.3 制約

| 制約名（案）     | 種別        | 対象    | 内容                           |
| ---------------- | ----------- | ------- | ------------------------------ |
| `pk_users`       | PRIMARY KEY | `id`    | ユーザーIDを主キーとする       |
| `uk_users_email` | UNIQUE      | `email` | メールアドレスの重複を禁止する |

---

## 4. equipment

### 4.1 テーブル概要

| 項目   | 内容                                   |
| ------ | -------------------------------------- |
| 論理名 | 設備                                   |
| 物理名 | `equipment`                            |
| 説明   | 点検対象となる設備の基本情報を管理する |

### 4.2 カラム定義

| No. | 論理名         | カラム名                | データ型       | PK  | FK  | NULL | UNIQUE | デフォルト | 説明                       |
| --: | -------------- | ----------------------- | -------------- | :-: | :-: | :--: | :----: | ---------- | -------------------------- |
|   1 | 設備ID         | `id`                    | `BIGINT`       |  ○  |     | 不可 |   ○    | 自動採番   | 設備を識別するID           |
|   2 | 設備コード     | `equipment_code`        | `VARCHAR(20)`  |     |     | 不可 |   ○    |            | 設備を業務上識別するコード |
|   3 | 設備名         | `name`                  | `VARCHAR(100)` |     |     | 不可 |        |            | 設備の名称                 |
|   4 | メーカー       | `manufacturer`          | `VARCHAR(100)` |     |     |  可  |        |            | 設備の製造元               |
|   5 | 型番           | `model`                 | `VARCHAR(100)` |     |     |  可  |        |            | 設備の型番                 |
|   6 | 設置場所       | `location`              | `VARCHAR(100)` |     |     |  可  |        |            | 設備が設置されている場所   |
|   7 | 設置日         | `installed_date`        | `DATE`         |     |     |  可  |        |            | 設備を設置した日           |
|   8 | 点検周期（日） | `inspection_cycle_days` | `INT`          |     |     |  可  |        |            | 点検を行う間隔の日数       |
|   9 | 備考           | `description`           | `VARCHAR(500)` |     |     |  可  |        |            | 設備に関する補足情報       |
|  10 | 稼働状態       | `active`                | `BOOLEAN`      |     |     | 不可 |        | `TRUE`     | 現在使用中の設備かを表す   |
|  11 | 作成日時       | `created_at`            | `TIMESTAMP`    |     |     | 不可 |        |            | レコード作成日時           |
|  12 | 更新日時       | `updated_at`            | `TIMESTAMP`    |     |     | 不可 |        |            | レコード更新日時           |

### 4.3 制約

| 制約名（案）        | 種別        | 対象             | 内容                       |
| ------------------- | ----------- | ---------------- | -------------------------- |
| `pk_equipment`      | PRIMARY KEY | `id`             | 設備IDを主キーとする       |
| `uk_equipment_code` | UNIQUE      | `equipment_code` | 設備コードの重複を禁止する |

---

## 5. equipment_inspection_item

### 5.1 テーブル概要

| 項目   | 内容                                             |
| ------ | ------------------------------------------------ |
| 論理名 | 設備点検項目                                     |
| 物理名 | `equipment_inspection_item`                      |
| 説明   | 設備ごとに使用する点検項目のマスタ情報を管理する |

### 5.2 カラム定義

| No. | 論理名       | カラム名               | データ型        | PK  | FK  | NULL | UNIQUE | デフォルト | 説明                                 |
| --: | ------------ | ---------------------- | --------------- | :-: | :-: | :--: | :----: | ---------- | ------------------------------------ |
|   1 | 点検項目ID   | `id`                   | `BIGINT`        |  ○  |     | 不可 |   ○    | 自動採番   | 点検項目を識別するID                 |
|   2 | 設備ID       | `equipment_id`         | `BIGINT`        |     |  ○  | 不可 |        |            | 対象設備のID                         |
|   3 | 点検項目名   | `name`                 | `VARCHAR(100)`  |     |     | 不可 |        |            | 例：異音確認、油漏れ確認             |
|   4 | 点検項目種別 | `type`                 | `VARCHAR(20)`   |     |     | 不可 |        |            | `NUMERIC` または `BOOLEAN`           |
|   5 | 単位         | `unit`                 | `VARCHAR(20)`   |     |     |  可  |        |            | 数値型で使用する単位。例：`℃`、`MPa` |
|   6 | 正常下限値   | `min_value`            | `DECIMAL(12,4)` |     |     |  可  |        |            | 数値型における正常範囲の下限値       |
|   7 | 正常上限値   | `max_value`            | `DECIMAL(12,4)` |     |     |  可  |        |            | 数値型における正常範囲の上限値       |
|   8 | 正常真偽値   | `normal_boolean_value` | `BOOLEAN`       |     |     |  可  |        |            | `BOOLEAN`型で正常とみなす値          |
|   9 | 説明         | `description`          | `VARCHAR(500)`  |     |     |  可  |        |            | 点検方法や判断基準などの補足         |
|  10 | 表示順       | `display_order`        | `INT`           |     |     | 不可 |        |            | 点検画面に表示する順序               |
|  11 | 有効状態     | `active`               | `BOOLEAN`       |     |     | 不可 |        | `TRUE`     | 新規点検で使用可能な項目かを表す     |
|  12 | 作成日時     | `created_at`           | `TIMESTAMP`     |     |     | 不可 |        |            | レコード作成日時                     |
|  13 | 更新日時     | `updated_at`           | `TIMESTAMP`     |     |     | 不可 |        |            | レコード更新日時                     |

### 5.3 制約

| 制約名（案）                   | 種別        | 対象                   | 内容                                     |
| ------------------------------ | ----------- | ---------------------- | ---------------------------------------- |
| `pk_equipment_inspection_item` | PRIMARY KEY | `id`                   | 点検項目IDを主キーとする                 |
| `fk_item_equipment`            | FOREIGN KEY | `equipment_id`         | `equipment.id` を参照する                |
| `uk_item_equipment_name`       | UNIQUE      | `equipment_id`, `name` | 同一設備への同名項目の重複登録を禁止する |

### 5.4 値の利用ルール

- `type` はJava側では Enum として扱い、DBには文字列として保存する。
- `type = NUMERIC` の場合、必要に応じて `unit`、`min_value`、`max_value` を使用し、`normal_boolean_value` は `NULL` とする。
- `type = BOOLEAN` の場合、`normal_boolean_value` を使用し、`unit`、`min_value`、`max_value` は `NULL` とする。
- `normal_boolean_value` は、`BOOLEAN`型の点検項目で正常とみなす真偽値を表す。
- `min_value` と `max_value` の両方が設定されている場合、`min_value <= max_value` とする。
- 数値型の異常判定では、設定されている正常範囲と `inspection_result.numeric_value` を比較して `result` を決定する。
- 真偽型の異常判定では、`inspection_result.boolean_value` と `normal_boolean_value` が一致する場合は `OK`、一致しない場合は `NG` とする。

---

## 6. inspection

### 6.1 テーブル概要

| 項目   | 内容                                                        |
| ------ | ----------------------------------------------------------- |
| 論理名 | 点検                                                        |
| 物理名 | `inspection`                                                |
| 説明   | 1回の点検に関する対象設備、点検者、実施日時、備考を管理する |

### 6.2 カラム定義

| No. | 論理名   | カラム名        | データ型       | PK  | FK  | NULL | UNIQUE | デフォルト | 説明                       |
| --: | -------- | --------------- | -------------- | :-: | :-: | :--: | :----: | ---------- | -------------------------- |
|   1 | 点検ID   | `id`            | `BIGINT`       |  ○  |     | 不可 |   ○    | 自動採番   | 点検を識別するID           |
|   2 | 設備ID   | `equipment_id`  | `BIGINT`       |     |  ○  | 不可 |        |            | 点検対象となる設備のID     |
|   3 | 点検者ID | `user_id`       | `BIGINT`       |     |  ○  | 不可 |        |            | 点検を実施したユーザーのID |
|   4 | 点検日時 | `inspection_at` | `TIMESTAMP`    |     |     | 不可 |        |            | 点検を実施した日時         |
|   5 | 備考     | `comment`       | `VARCHAR(500)` |     |     |  可  |        |            | 点検全体に関する補足情報   |
|   6 | 作成日時 | `created_at`    | `TIMESTAMP`    |     |     | 不可 |        |            | レコード作成日時           |
|   7 | 更新日時 | `updated_at`    | `TIMESTAMP`    |     |     | 不可 |        |            | レコード更新日時           |

### 6.3 制約

| 制約名（案）              | 種別        | 対象           | 内容                      |
| ------------------------- | ----------- | -------------- | ------------------------- |
| `pk_inspection`           | PRIMARY KEY | `id`           | 点検IDを主キーとする      |
| `fk_inspection_equipment` | FOREIGN KEY | `equipment_id` | `equipment.id` を参照する |
| `fk_inspection_user`      | FOREIGN KEY | `user_id`      | `users.id` を参照する     |

---

## 7. inspection_result

### 7.1 テーブル概要

| 項目   | 内容                                              |
| ------ | ------------------------------------------------- |
| 論理名 | 点検結果                                          |
| 物理名 | `inspection_result`                               |
| 説明   | 1回の点検における各点検項目の結果と備考を管理する |

### 7.2 カラム定義

| No. | 論理名     | カラム名             | データ型        | PK  | FK  | NULL | UNIQUE | デフォルト | 説明                                |
| --: | ---------- | -------------------- | --------------- | :-: | :-: | :--: | :----: | ---------- | ----------------------------------- |
|   1 | 点検結果ID | `id`                 | `BIGINT`        |  ○  |     | 不可 |   ○    | 自動採番   | 点検結果を識別するID                |
|   2 | 点検ID     | `inspection_id`      | `BIGINT`        |     |  ○  | 不可 |        |            | 対象となる点検のID                  |
|   3 | 点検項目ID | `inspection_item_id` | `BIGINT`        |     |  ○  | 不可 |        |            | 対象となる設備点検項目のID          |
|   4 | 数値結果   | `numeric_value`      | `DECIMAL(12,4)` |     |     |  可  |        |            | `NUMERIC`型の点検項目で測定した値   |
|   5 | 真偽結果   | `boolean_value`      | `BOOLEAN`       |     |     |  可  |        |            | `BOOLEAN`型の点検項目で入力した結果 |
|   6 | 判定結果   | `result`             | `VARCHAR(20)`   |     |     | 不可 |        |            | `OK`、`NG`、`NOT_APPLICABLE`        |
|   7 | 備考       | `comment`            | `VARCHAR(500)`  |     |     |  可  |        |            | 点検項目ごとの補足情報              |
|   8 | 作成日時   | `created_at`         | `TIMESTAMP`     |     |     | 不可 |        |            | レコード作成日時                    |
|   9 | 更新日時   | `updated_at`         | `TIMESTAMP`     |     |     | 不可 |        |            | レコード更新日時                    |

### 7.3 制約

| 制約名（案）                | 種別        | 対象                                  | 内容                                                     |
| --------------------------- | ----------- | ------------------------------------- | -------------------------------------------------------- |
| `pk_inspection_result`      | PRIMARY KEY | `id`                                  | 点検結果IDを主キーとする                                 |
| `fk_result_inspection`      | FOREIGN KEY | `inspection_id`                       | `inspection.id` を参照する                               |
| `fk_result_item`            | FOREIGN KEY | `inspection_item_id`                  | `equipment_inspection_item.id` を参照する                |
| `uk_result_inspection_item` | UNIQUE      | `inspection_id`, `inspection_item_id` | 同一点検に同じ点検項目の結果を複数登録できないようにする |

### 7.4 値の利用ルール

- 点検項目の種別が `NUMERIC` の場合、`numeric_value` を使用し、`boolean_value` は `NULL` とする。
- 点検項目の種別が `BOOLEAN` の場合、`boolean_value` を使用し、`numeric_value` は `NULL` とする。
- `result` は異常判定結果を保持する。
- Java側では `result` を Enum として扱い、DBには文字列として保存する。
- `NOT_APPLICABLE` の場合は、`numeric_value` および `boolean_value` を `NULL` とする。
- `inspection_result.inspection_item_id` が参照する点検項目は、対象となる `inspection.equipment_id` と同一設備に属する点検項目でなければならない。

---

## 8. リレーション一覧

| 親テーブル                  | 子テーブル                  | 多重度 | 外部キー                                 |
| --------------------------- | --------------------------- | ------ | ---------------------------------------- |
| `users`                     | `inspection`                | 1:N    | `inspection.user_id`                     |
| `equipment`                 | `inspection`                | 1:N    | `inspection.equipment_id`                |
| `equipment`                 | `equipment_inspection_item` | 1:N    | `equipment_inspection_item.equipment_id` |
| `inspection`                | `inspection_result`         | 1:N    | `inspection_result.inspection_id`        |
| `equipment_inspection_item` | `inspection_result`         | 1:N    | `inspection_result.inspection_item_id`   |

---

## 9. 列挙値

### 9.1 role

| 値          | 説明                                           |
| ----------- | ---------------------------------------------- |
| `ADMIN`     | 設備、点検項目、ユーザーなどを管理できる管理者 |
| `INSPECTOR` | 点検の実施および履歴の閲覧を行う点検者         |

### 9.2 inspection_result.result

| 値               | 説明                 |
| ---------------- | -------------------- |
| `OK`             | 問題なし             |
| `NG`             | 異常あり             |
| `NOT_APPLICABLE` | 今回の点検では対象外 |

### 9.3 equipment_inspection_item.type

| 値        | 説明                                                 |
| --------- | ---------------------------------------------------- |
| `NUMERIC` | 数値を入力し、必要に応じて閾値で正常・異常を判定する |
| `BOOLEAN` | 真偽値を入力して正常・異常を判定する                 |

---

## 10. 補足事項

- `users` はSQLの予約語との衝突を避けるため、単数形の `user` ではなく複数形を採用する。
- `inspection.user_id` には、画面から入力された氏名ではなく、ログイン中のユーザーIDを設定する。
- 設備を使用しなくなった場合は物理削除せず、`equipment.active` を `FALSE` に更新する。
- 点検項目を使用しなくなった場合は物理削除せず、`equipment_inspection_item.active` を `FALSE` に更新する。
- 過去の点検結果を保持するため、`inspection` および `inspection_result` は原則として物理削除しない。
- 日時のタイムゾーン方針は、AWSへのデプロイ設計時に別途決定する。
