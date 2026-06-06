# テーブル仕様書

- DBMS: H2（インメモリ、`jdbc:h2:mem:pubsub`）
- スキーマ生成: JPA `ddl-auto: create-drop`（起動時生成・終了時破棄）
- 設計方針: **状態は UPDATE せず INSERT で積み上げる**（append-only）。
  `task` は不変属性のみ、変化する状態は `task_status_event` に積む。

## ER 図

```
 task (1) ───────────< (N) task_status_event
   id  ─────────────────  task_id
```
- `task` 1 件に対し `task_status_event` が時系列で N 件積み上がる。
- 現在状態 = ある `task_id` のうち **最大 `id`** の行。

---

## テーブル: `task`

タスクの **不変な属性** のみを保持する。

| カラム | 型 | 制約 | 説明 |
|---|---|---|---|
| `id` | VARCHAR(36) | PK, NOT NULL | タスクID（UUID 文字列） |
| `created_at` | TIMESTAMP | NOT NULL | 追加日時（キュー投入時刻、UTC Instant） |
| `total_count` | INT | NOT NULL | 全体件数（投入時に確定、10〜100） |

DDL（H2 が生成する相当）:
```sql
CREATE TABLE task (
    id          VARCHAR(36) NOT NULL,
    created_at  TIMESTAMP   NOT NULL,
    total_count INT         NOT NULL,
    PRIMARY KEY (id)
);
```

> `status` や `processed_count` 等の **変化する値はここに持たない**。現在状態は `task_status_event` から導出する。

---

## テーブル: `task_status_event`（append-only / スタックの実体）

ステータス変化・進捗の **スナップショットを追記** する。UPDATE/DELETE は行わない。

| カラム | 型 | 制約 | 説明 |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | スタック順を担保する単調増加ID |
| `task_id` | VARCHAR(36) | NOT NULL, INDEX | 対象タスク（→ `task.id`） |
| `status` | VARCHAR(16) | NOT NULL | `QUEUED`/`STARTED`/`SUCCESS`/`WARNING`/`ERROR`/`CANCELLED` |
| `processed_count` | INT | NOT NULL | 処理済み件数（成功+失敗） |
| `success_count` | INT | NOT NULL | 成功件数 |
| `fail_count` | INT | NOT NULL | 失敗件数（9 の倍数でカウント） |
| `message` | VARCHAR(500) | NULL 可 | 処理結果/例外内容などの補足 |
| `occurred_at` | TIMESTAMP | NOT NULL | 発生時刻 |

DDL（相当）:
```sql
CREATE TABLE task_status_event (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    task_id         VARCHAR(36)  NOT NULL,
    status          VARCHAR(16)  NOT NULL,
    processed_count INT          NOT NULL,
    success_count   INT          NOT NULL,
    fail_count      INT          NOT NULL,
    message         VARCHAR(500),
    occurred_at     TIMESTAMP    NOT NULL,
    PRIMARY KEY (id)
);
CREATE INDEX idx_event_task ON task_status_event (task_id, id);
```

> 外部キー制約は明示していない（デモのため）。論理的に `task_id` は `task.id` を指す。

### どのタイミングで何が積まれるか

| 契機 | status | 代表 message | 積む主体 |
|---|---|---|---|
| 投入 | `QUEUED` | キュー待機中 | `TaskService`（REST スレッド） |
| 処理開始 | `STARTED` | 処理開始 | 処理ワーカ |
| 進捗（既定10件ごと） | `STARTED` | 処理中 (n/total) | 処理ワーカ |
| 完走・失敗0 | `SUCCESS` | 全N件 正常終了 | 処理ワーカ |
| 完走・失敗≥1 | `WARNING` | 完了 (成功x / 失敗y) | 処理ワーカ |
| 例外 | `ERROR` | 異常終了: … | 処理ワーカ |
| 中止検知 | `CANCELLED` | 中止 (n/total件 処理済) | 処理ワーカ |

### 現在状態の導出（クエリ例）
```sql
-- あるタスクの現在状態（最新行）
SELECT * FROM task_status_event
WHERE task_id = ?
ORDER BY id DESC
LIMIT 1;

-- 実行時間 = 最初の STARTED から 最新行(終端なら終端行) までの差
```

### サンプル（1 タスクのスタック）
```
id  task_id   status     processed success fail message
12  82fdb8ed  QUEUED      0         0       0    キュー待機中
15  82fdb8ed  STARTED     0         0       0    処理開始
27  82fdb8ed  STARTED     10        9       1    処理中 (10/71)
39  82fdb8ed  STARTED     20        18      2    処理中 (20/71)
…
81  82fdb8ed  WARNING     71        64      7    完了 (成功 64 / 失敗 7)   ← 最新 = 現在状態
```

> 上記のように **状態を上書きせず積み上げ**、最新 1 行を現在状態として読む。
> `GET /api/tasks/{id}/statuses` がこのスタックをそのまま返す。
