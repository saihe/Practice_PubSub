# アーキテクチャ / 設計ノート

## 1. 全体構成

```
┌─────────────────────────┐        HTTP (HAL+JSON)        ┌──────────────────────────────┐
│  ブラウザ (localhost)     │  ───────────────────────────▶ │  Backend : Spring Boot         │
│  Frontend : Next.js      │   GET /api(ルート)から          │  - REST + HATEOAS              │
│  - 1画面のタスク管理       │   _links を辿って全操作         │  - Pub/Sub(アプリ内)            │
│  - 10秒ポーリング          │ ◀───────────────────────────  │  - 非同期ワーカ(購読側)          │
│  - スナックバー            │                               │  - H2 (append-only ステータス)  │
└─────────────────────────┘                               └──────────────────────────────┘
        :3000                                                          :8080
```

- フロントエンドはホスト PC のブラウザで動作するため、バックエンドへは `http://localhost:8080` で到達する。
- フロントが知っている URL は **API ルート `http://localhost:8080/api` のみ**。それ以外は HATEOAS のリンクで発見する。
- `docker compose up` でフロント(3000)・バック(8080)の 2 コンテナが起動する。

## 2. Pub/Sub の実装方式

本デモの主目的は「Pub/Sub の実装と動きの確認」。外部メッセージブローカ(RabbitMQ/Kafka 等)を増やさず、
**Spring の `ApplicationEventPublisher`（=トピック）と `@EventListener`（=サブスクライバ）** でアプリ内 Pub/Sub を構成した。

### 登場人物
| 役割 | クラス | 説明 |
|---|---|---|
| パブリッシャ | `pubsub/TaskEventPublisher` | `ApplicationEventPublisher` の薄いラッパ。メッセージをトピックへ publish。 |
| メッセージ | `event/TaskQueuedEvent` / `TaskStatusChangedEvent` | トピックを流れるイベント(sealed interface `TaskEvent`)。 |
| サブスクライバ① | `pubsub/TaskProcessingSubscriber` | `TaskQueuedEvent` を購読し **別スレッド(`@Async`)で実処理**。コンシューマ(ジョブワーカ)。 |
| サブスクライバ② | `pubsub/TaskAuditSubscriber` | すべての `TaskEvent` を購読してログ出力。**1メッセージが複数購読者へファンアウト**する様子を観察できる。 |

### フロー
```
[REST] POST /api/tasks
   │  TaskService.queueTask()
   │   - Task 保存 / QUEUED を stack
   │   - publisher.publish(TaskQueuedEvent)   ← ★ ここがトピックへの publish
   ▼
(ApplicationEventPublisher = トピック)
   ├─▶ TaskProcessingSubscriber.onTaskQueued()  @Async  … 実処理(別スレッド)
   │        - STARTED を stack → publish(TaskStatusChangedEvent)
   │        - ループ(約100ms/件)。9の倍数は失敗カウント。進捗を一定間隔で stack。
   │        - 終端(SUCCESS/WARNING/ERROR/CANCELLED)を stack → publish
   └─▶ TaskAuditSubscriber.onAnyTaskEvent()            … 監査ログ(同一トピックの別購読者)
```

publish 側は購読者の数や正体を知らない（疎結合）。これは外部ブローカでの「トピックに publish すれば
購読している全コンシューマへ配信される」挙動と同じ考え方。

### 実ブローカへの対応関係（学習用メモ）
| 本デモ(アプリ内) | 一般的なメッセージブローカ |
|---|---|
| `ApplicationEventPublisher` | トピック / Exchange |
| `TaskQueuedEvent` | メッセージ(ジョブ) |
| `@Async @EventListener`(処理) | コンシューマ / ワーカ |
| `@EventListener`(監査) | 同一トピックの別サブスクライバ(ファンアウト) |
| `ThreadPoolTaskExecutor` | コンシューマの並行数(プリフェッチ/並列度) |

> アプリ内 Pub/Sub は **プロセス内・無永続・at-most-once** である点が実ブローカと異なる。
> 永続キューや再配信が必要なら Redis Streams / Kafka 等へ置き換える。後述の DB 選定も参照。

## 3. 状態管理：ステータスのスタック（append-only）

「ステータス変更ではなく、ステータスをスタックして最新を取得する」という指示に従い、**イベントソーシング寄り**の構造にした。

- `task` … 不変な属性のみ（id・追加日時・全体件数）。
- `task_status_event` … **INSERT のみ**の追記専用テーブル。1 タスクに時系列で状態が積み上がる。
  - 各行はその時点のスナップショット（ステータス・処理件数・成功/失敗件数・メッセージ・発生時刻）。
  - 主キー `id` は自動採番で単調増加。**ある task_id について最大 id の行 = 現在状態**。
- 画面表示（`TaskView`）は、最新行へ畳み込んで生成する。

UPDATE を一切行わないため、状態遷移の全履歴が残る（`GET /api/tasks/{id}/statuses` で参照可能）。

## 4. ステータス定義

| enum | ラベル | 終端 | 意味 |
|---|---|:--:|---|
| `QUEUED` | キューに追加 | | 投入直後・処理待ち |
| `STARTED` | 開始 | | 処理開始/進行中（進捗もこのステータスで積む） |
| `SUCCESS` | 正常終了 | ✓ | 失敗 0 件で完走 |
| `WARNING` | 警告終了 | ✓ | 失敗 1 件以上で完走 |
| `ERROR` | 異常終了 | ✓ | 例外発生で中断 |
| `CANCELLED` | 中止終了 | ✓ | 「タスク中止」操作で停止（指定 5 ステータスへの追加分） |

### 重要な帰結：SUCCESS は通常運用では発生しない
全体件数は 10〜100、かつ 9 の倍数の件で失敗する。**total ≥ 10 では必ず item=9 を通過**するため
失敗が最低 1 件発生し、**完走すれば必ず WARNING(警告終了)** になる。
SUCCESS(正常終了) を観測したい場合は `app.task.total-count-min` を 9 未満にする（既定 10）。
コード経路自体は実装済み（失敗 0 件なら SUCCESS）。

## 5. 処理ロジック（購読側ワーカ）

`TaskProcessingSubscriber.onTaskQueued()`：

1. 開始前に中止フラグが立っていれば即 `CANCELLED`。
2. `STARTED` を stack。
3. 確率 `exception-probability` で「毒入りインデックス」を 1 つ抽選（異常終了の観測用）。
4. `for i in 1..total`：
   - `item-interval-ms`(既定100ms) スリープ → おおよそ毎秒10件。
   - 中止フラグ検知 → `CANCELLED`(処理途中件数で) を stack して終了。
   - `i == 毒入り` → 例外送出（→ catch で `ERROR`）。
   - `i % 9 == 0` → 失敗カウント、それ以外は成功カウント（**中断しない**）。
   - `progress-event-every`(既定10) 件ごとに `STARTED` 進捗を stack。
5. 完走 → 失敗>0 で `WARNING`、失敗0 で `SUCCESS`。
6. 例外 → `ERROR`。
7. `finally` で中止フラグを破棄。

## 6. キャンセル方式（協調的キャンセル）

- 中止操作はステータスを直接書き換えず、`CancellationRegistry` のフラグを立てるだけ（`POST /cancel` は 202 を返す）。
- 終端 `CANCELLED` の積み込みは **処理ワーカが自分で行う**。
  これにより「QUEUED 以降あるタスクのイベントを書くのは常に処理ワーカ 1 スレッドのみ」という単純な書き込みモデルを保てる。
- 既に終端のタスク・未知IDの中止は不可（409/404）。

## 7. DB 選定（H2 を採用、代替案の比較）

指示どおり **H2（インメモリ）** を採用。判断根拠と代替案は以下。

| 候補 | 向いている点 | 本デモで採用しない理由 |
|---|---|---|
| **H2 (採用)** | 追加依存ゼロ・組込み・起動が速い・SQL/JPA そのまま・`/h2-console` で中身を直接観察できる | （採用） |
| PostgreSQL 等 RDBMS | 本番相当・永続・並行性 | デモにはコンテナ/セットアップが過剰。append-only モデルは H2 でも十分表現できる |
| **Redis Streams** | **Pub/Sub と相性が良く、永続キュー+コンシューマグループ+再配信が得られる**。状態スタックも追記型で自然 | 学習対象を「Pub/Sub の最小実装」に絞るため、まずはアプリ内 Pub/Sub + H2 とした。発展課題として最有力 |
| MongoDB 等ドキュメントDB | 1 タスク=1 ドキュメントに履歴配列を持たせやすい | スキーマレスの利点が本ケースでは小さい。集計/結合も RDB で十分 |
| イベントストア専用(EventStoreDB 等) | イベントソーシングそのもの | デモには重厚 |

**結論**：デモの目的（ゼロ依存で Pub/Sub と状態スタックを観察）には H2 が最適。
「RDBMS 以外の有力候補」としては **Redis Streams** を推す（永続 Pub/Sub・コンシューマグループ・再配信が一体で得られ、
本デモのアプリ内 Pub/Sub をそのまま置き換える発展先になる）。

## 8. 設定（application.yml / 環境変数）

`app.task.*`（`config/AppProperties`）:

| キー | 既定 | 説明 |
|---|---|---|
| `item-interval-ms` | 100 | 1 件あたりの処理間隔（毎秒約10件） |
| `total-count-min` / `max` | 10 / 100 | 全体件数の抽選範囲 |
| `failure-multiple` | 9 | 失敗扱いにする倍数 |
| `progress-event-every` | 10 | 進捗を stack する件数間隔 |
| `exception-probability` | 0.10 | 1 タスクで例外注入する確率（異常終了の観測用、0で無効） |

フロント:
- `NEXT_PUBLIC_API_ROOT`（既定 `http://localhost:8080/api`）… 唯一ハードコードされる API ルート。

## 9. テスト

`backend/src/test`:
- `TaskProcessingIntegrationTest` … 投入→終端まで。9 の倍数失敗カウントと警告/正常判定、スタック順序。
- `TaskCancellationIntegrationTest` … 中止が `CANCELLED` として積まれること、終端後の再中止不可。
- `TaskApiHateoasTest` … ルートから `tasks`、collection の `create`、生成タスクの `self/statuses/cancel` リンク。

`mvn test` で 5 件すべて green を確認済み。
