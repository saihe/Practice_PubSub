// バックエンドの HAL レスポンスに対応する型定義。

/** HAL の 1リンク。 */
export interface Link {
  href: string;
}

/** HAL の _links。rel(関係名)→ Link のマップ。 */
export type Links = Record<string, Link>;

/** タスクのステータス(バックエンドの enum 名)。 */
export type TaskStatus =
  | "QUEUED"
  | "STARTED"
  | "SUCCESS"
  | "WARNING"
  | "ERROR"
  | "CANCELLED";

/** 終端ステータスの集合。 */
export const TERMINAL_STATUSES: ReadonlySet<TaskStatus> = new Set<TaskStatus>([
  "SUCCESS",
  "WARNING",
  "ERROR",
  "CANCELLED",
]);

/** タスク1件の表現(現在状態に畳み込み済み)。 */
export interface TaskResource {
  taskId: string;
  createdAt: string;
  status: TaskStatus;
  statusLabel: string;
  result: string;
  processedCount: number;
  successCount: number;
  failCount: number;
  totalCount: number;
  executionMillis: number;
  terminal: boolean;
  _links: Links;
}

/** タスクコレクションの取得結果(一覧 + コレクションのリンク)。 */
export interface TaskCollection {
  tasks: TaskResource[];
  links: Links;
}

/**
 * SSE(リアルタイム配信)で push されるタスクのスナップショット。
 * バックエンドの TaskView をそのまま JSON 化したもの。リンクは含まないため、
 * フロントは手元の `_links` を温存しつつフィールドだけをマージする。
 */
export type TaskSnapshot = Omit<TaskResource, "_links">;
