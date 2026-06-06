// HATEOAS クライアント。
//
// フロントエンドがハードコードする URL は「API ルート」だけ。
// それ以外のエンドポイント(一覧取得・作成・中止・履歴)は、サーバが返す
// _links を辿って発見する。これにより URL 体系の変更に強くなる。

import type { Links, TaskCollection, TaskResource } from "./types";

/** 唯一ハードコードする URL。ブラウザから到達できるバックエンドのルート。 */
const API_ROOT =
  process.env.NEXT_PUBLIC_API_ROOT ?? "http://localhost:8080/api";

/** ルートの _links をキャッシュ(初回のみ取得)。 */
let rootLinksCache: Links | null = null;

async function getJson(url: string, init?: RequestInit): Promise<any> {
  const res = await fetch(url, {
    ...init,
    headers: { Accept: "application/hal+json, application/json", ...(init?.headers ?? {}) },
    cache: "no-store",
  });
  if (!res.ok) {
    throw new Error(`リクエスト失敗: ${res.status} ${res.statusText} (${url})`);
  }
  // 204 などボディなしに備える。
  const text = await res.text();
  return text ? JSON.parse(text) : null;
}

/** API ルートを取得し _links を返す(HATEOAS の起点)。 */
export async function discoverRoot(): Promise<Links> {
  if (rootLinksCache) {
    return rootLinksCache;
  }
  const json = await getJson(API_ROOT);
  rootLinksCache = (json?._links ?? {}) as Links;
  return rootLinksCache;
}

/** ルートの `tasks` リンクから一覧取得用 URL を解決する。 */
export async function resolveTasksHref(): Promise<string> {
  const links = await discoverRoot();
  const href = links["tasks"]?.href;
  if (!href) {
    throw new Error("ルートに 'tasks' リンクがありません。");
  }
  return href;
}

/** _embedded から最初の配列を取り出す(埋め込み rel 名に依存しない)。 */
function extractEmbeddedTasks(json: any): TaskResource[] {
  const embedded = json?._embedded;
  if (!embedded) {
    return [];
  }
  const firstKey = Object.keys(embedded)[0];
  const list = firstKey ? embedded[firstKey] : [];
  return Array.isArray(list) ? (list as TaskResource[]) : [];
}

/** タスク一覧を取得する。collection の _links(create など)も併せて返す。 */
export async function fetchTasks(tasksHref: string): Promise<TaskCollection> {
  const json = await getJson(tasksHref);
  return {
    tasks: extractEmbeddedTasks(json),
    links: (json?._links ?? {}) as Links,
  };
}

/** コレクションの `create` リンクへ POST してタスクをキューに追加する。 */
export async function createTask(createHref: string): Promise<TaskResource> {
  return (await getJson(createHref, { method: "POST" })) as TaskResource;
}

/** タスクの `cancel` リンクへ POST して中止を要求する。 */
export async function cancelTask(cancelHref: string): Promise<TaskResource> {
  return (await getJson(cancelHref, { method: "POST" })) as TaskResource;
}

export { API_ROOT };
