# API 仕様書

- ベース URL: `http://localhost:8080`
- すべてのエンドポイントは `/api` 配下。
- メディアタイプ: レスポンスは **HAL**（`application/hal+json`）。
- 認証: なし（ローカルデモ）。
- CORS: `http://localhost:3000`（フロント）からのアクセスを許可。

## HATEOAS の方針

クライアントがハードコードするのは **ルート `GET /api` だけ**。以降は `_links` を辿る。

| rel | 出現箇所 | メソッド | 意味 |
|---|---|---|---|
| `self` | 各リソース | GET | 自分自身 |
| `tasks` | ルート | GET | タスク一覧 |
| `create` | タスクコレクション | **POST** | タスク投入 |
| `cancel` | タスク（未終端時のみ） | **POST** | 中止 |
| `statuses` | タスク | GET | ステータス履歴（スタック） |
| `task` | ステータス履歴 | GET | 親タスク |

> リンクにメソッドは含まれない。`create`/`cancel` は POST、それ以外は GET という **rel の意味**で運用する。
> `cancel` リンクは **未終端のタスクにのみ**付与される（終端なら消える）。フロントはこの有無で中止可否を判断できる。

---

## 1. ルート

### `GET /api`
API のエントリポイント。

**200 OK**
```json
{
  "_links": {
    "self":  { "href": "http://localhost:8080/api" },
    "tasks": { "href": "http://localhost:8080/api/tasks" }
  }
}
```

---

## 2. タスク一覧

### `GET /api/tasks`
全タスクを **追加日時の降順** で、現在状態に畳み込んで返す。

**200 OK**
```json
{
  "_embedded": {
    "taskViewList": [
      {
        "taskId": "4f478a1b-dd64-4abe-bc01-1ee8e106faee",
        "createdAt": "2026-06-06T03:22:45.065Z",
        "status": "WARNING",
        "statusLabel": "警告終了",
        "result": "完了 (成功 82 / 失敗 10)",
        "processedCount": 92,
        "successCount": 82,
        "failCount": 10,
        "totalCount": 92,
        "executionMillis": 9596,
        "terminal": true,
        "_links": {
          "self":     { "href": ".../api/tasks/4f478a1b-..." },
          "statuses": { "href": ".../api/tasks/4f478a1b-.../statuses" }
        }
      }
    ]
  },
  "_links": {
    "self":   { "href": "http://localhost:8080/api/tasks" },
    "create": { "href": "http://localhost:8080/api/tasks" }
  }
}
```
- 埋め込み配列のキーは `taskViewList`（Spring HATEOAS 既定）。フロントは「`_embedded` の最初の配列」を読む実装にしており、キー名に依存しない。
- `cancel` リンクは未終端タスクの `_links` にのみ含まれる。

---

## 3. タスク投入（タスク実行／追加）

### `POST /api/tasks`
全体件数（10〜100 の乱数）を確定してタスクをキューに追加し、購読側ワーカへ publish する。
**ボディ不要。**

**201 Created**（`Location: .../api/tasks/{id}`）
```json
{
  "taskId": "82fdb8ed-...",
  "createdAt": "2026-06-06T03:32:44.065Z",
  "status": "QUEUED",
  "statusLabel": "キューに追加",
  "result": "キュー待機中",
  "processedCount": 0,
  "successCount": 0,
  "failCount": 0,
  "totalCount": 71,
  "executionMillis": 0,
  "terminal": false,
  "_links": {
    "self":     { "href": ".../api/tasks/82fdb8ed-..." },
    "statuses": { "href": ".../api/tasks/82fdb8ed-.../statuses" },
    "cancel":   { "href": ".../api/tasks/82fdb8ed-.../cancel" }
  }
}
```
- レスポンスは「投入直後（QUEUED）」のスナップショット。実処理は非同期に進む。

---

## 4. タスク取得

### `GET /api/tasks/{id}`
単一タスクの現在状態。

- **200 OK** … タスクのリソース（上記と同形）。
- **404 Not Found** … 未知の id。

---

## 5. タスク中止

### `POST /api/tasks/{id}/cancel`
中止フラグを立てる（協調的キャンセル）。実際の `CANCELLED` 積み込みは購読側ワーカが行う。
**ボディ不要。**

- **202 Accepted** … 中止を受理。ボディは受理時点のタスクリソース（多くは `STARTED` のまま。次のフェッチで `CANCELLED` になる）。
- **404 Not Found** … 未知の id。
- **409 Conflict** … 既に終端（SUCCESS/WARNING/ERROR/CANCELLED）のため中止不可。

---

## 6. ステータス履歴（スタック）

### `GET /api/tasks/{id}/statuses`
append-only に積まれたステータスを **古い→新しい順**で返す。状態が「変更」ではなく「積み上げ」られている様子を確認できる。

**200 OK**
```json
{
  "_embedded": {
    "statusEventDtoList": [
      { "sequence": 12, "status": "QUEUED",  "statusLabel": "キューに追加", "processedCount": 0,  "successCount": 0,  "failCount": 0, "message": "キュー待機中", "occurredAt": "..." },
      { "sequence": 15, "status": "STARTED", "statusLabel": "開始",       "processedCount": 0,  "successCount": 0,  "failCount": 0, "message": "処理開始",   "occurredAt": "..." },
      { "sequence": 27, "status": "STARTED", "statusLabel": "開始",       "processedCount": 10, "successCount": 9,  "failCount": 1, "message": "処理中 (10/71)", "occurredAt": "..." },
      { "sequence": 81, "status": "WARNING", "statusLabel": "警告終了",     "processedCount": 71, "successCount": 64, "failCount": 7, "message": "完了 (成功 64 / 失敗 7)", "occurredAt": "..." }
    ]
  },
  "_links": {
    "self": { "href": ".../api/tasks/{id}/statuses" },
    "task": { "href": ".../api/tasks/{id}" }
  }
}
```
- **404 Not Found** … 未知の id。

---

## 7. レスポンス項目（タスクリソース）

| フィールド | 型 | 説明 | 画面の列 |
|---|---|---|---|
| `taskId` | string(UUID) | タスクID | タスクID |
| `createdAt` | string(ISO-8601) | 追加日時 | 追加日時 |
| `status` | enum | ステータス(英) | ステータス |
| `statusLabel` | string | ステータス(日本語) | ステータス |
| `result` | string | 処理結果メッセージ | 処理結果 |
| `processedCount` | int | 処理済み件数(成功+失敗) | 処理件数 |
| `successCount` | int | 成功件数 | （処理件数の補足） |
| `failCount` | int | 失敗件数 | （処理件数の補足） |
| `totalCount` | int | 全体件数 | 全体件数 |
| `executionMillis` | long | 実行時間(ms, 開始〜終端/現時点) | 実行時間 |
| `terminal` | boolean | 終端かどうか | （中止可否の判断） |

## 8. エラー形式

Spring 既定の `ProblemDetail` 互換 JSON（例）:
```json
{ "timestamp": "...", "status": 409, "error": "Conflict", "path": "/api/tasks/{id}/cancel" }
```

## 9. 補助エンドポイント
- `GET /h2-console` … H2 コンソール。JDBC URL: `jdbc:h2:mem:pubsub` / user `sa` / pass 空。
  `task_status_event` を直接 SELECT して「スタック」を観察できる。
