"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { Toolbar } from "@/components/Toolbar";
import { TaskTable } from "@/components/TaskTable";
import { SnackbarStack, type Toast, type ToastTone } from "@/components/Snackbar";
import type { IntervalSeconds } from "@/components/IntervalSelect";
import {
  cancelTask,
  createTask,
  fetchTasks,
  resolveTasksHref,
} from "@/lib/api";
import type { Links, TaskResource, TaskSnapshot, TaskStatus } from "@/lib/types";

const DEFAULT_INTERVAL: IntervalSeconds = 10;

/** 終端に達したタスクからは中止リンクを落とす(以後は中止不可)。 */
function omitCancel(links: Links): Links {
  const { cancel, ...rest } = links;
  return rest;
}

function toneForStatus(status: TaskStatus): ToastTone {
  switch (status) {
    case "SUCCESS":
      return "success";
    case "WARNING":
      return "warning";
    case "ERROR":
      return "error";
    default:
      return "info";
  }
}

export default function Page() {
  const [tasks, setTasks] = useState<TaskResource[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [intervalSeconds, setIntervalSeconds] =
    useState<IntervalSeconds>(DEFAULT_INTERVAL);
  const [creating, setCreating] = useState(false);
  const [refreshing, setRefreshing] = useState(false);
  const [cancelling, setCancelling] = useState(false);
  const [lastUpdated, setLastUpdated] = useState<Date | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [toasts, setToasts] = useState<Toast[]>([]);
  const [streamHref, setStreamHref] = useState<string | null>(null);
  const [live, setLive] = useState(false);

  // HATEOAS で発見したリンクと、通知制御用の集合。
  const tasksHrefRef = useRef<string | null>(null);
  const createHrefRef = useRef<string | null>(null);
  const sessionQueuedIds = useRef<Set<string>>(new Set());
  const notifiedIds = useRef<Set<string>>(new Set());

  const addToast = useCallback((tone: ToastTone, message: string) => {
    setToasts((prev) => [
      ...prev,
      { id: crypto.randomUUID(), tone, message },
    ]);
  }, []);

  const dismissToast = useCallback((id: string) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

  // 画面から投入したタスクが終端に達したらスナックバー通知(1回だけ)。
  const detectTerminals = useCallback(
    (fetched: TaskResource[]) => {
      for (const t of fetched) {
        if (
          t.terminal &&
          sessionQueuedIds.current.has(t.taskId) &&
          !notifiedIds.current.has(t.taskId)
        ) {
          notifiedIds.current.add(t.taskId);
          addToast(
            toneForStatus(t.status),
            `タスク ${t.taskId.slice(0, 8)} が「${t.statusLabel}」で終了しました（成功 ${t.successCount} / 失敗 ${t.failCount}）`,
          );
        }
      }
    },
    [addToast],
  );

  const loadTasks = useCallback(
    async (showSpinner: boolean) => {
      const href = tasksHrefRef.current;
      if (!href) return;
      try {
        if (showSpinner) setRefreshing(true);
        const { tasks: fetched, links } = await fetchTasks(href);
        if (links["create"]?.href) {
          createHrefRef.current = links["create"].href;
        }
        // SSE(リアルタイム配信)の購読先を HATEOAS リンクから発見。
        if (links["stream"]?.href) {
          setStreamHref((prev) => prev ?? links["stream"].href);
        }
        detectTerminals(fetched);
        setTasks(fetched);
        setLastUpdated(new Date());
        setError(null);
      } catch (e) {
        setError((e as Error).message);
      } finally {
        if (showSpinner) setRefreshing(false);
      }
    },
    [detectTerminals],
  );

  // SSE で push された1件のスナップショットを一覧へ反映(リンクは手元の値を温存)。
  const mergeTask = useCallback(
    (snap: TaskSnapshot) => {
      setTasks((prev) => {
        const idx = prev.findIndex((t) => t.taskId === snap.taskId);
        if (idx === -1) {
          // 一覧未取得のタスク(別タブ等で発生)。リンクは次のポーリングで補完。
          return [{ ...snap, _links: {} }, ...prev];
        }
        const existing = prev[idx];
        const links = snap.terminal ? omitCancel(existing._links) : existing._links;
        const next = [...prev];
        next[idx] = { ...existing, ...snap, _links: links };
        return next;
      });
      // 終端到達ならその場で通知(ポーリングを待たない)。
      detectTerminals([{ ...snap, _links: {} }]);
      setLastUpdated(new Date());
    },
    [detectTerminals],
  );

  // 初期化: ルートから tasks リンクを発見 → 初回ロード。
  useEffect(() => {
    let active = true;
    (async () => {
      try {
        const href = await resolveTasksHref();
        if (!active) return;
        tasksHrefRef.current = href;
        await loadTasks(true);
      } catch (e) {
        if (active) setError((e as Error).message);
      }
    })();
    return () => {
      active = false;
    };
  }, [loadTasks]);

  // リアルタイム配信: HATEOAS で発見した stream リンクへ EventSource を張る。
  // 進捗・終端が push されるたびに該当行を即時更新し、終端ならスナックバー通知。
  // 取りこぼし対策は2段構え: サーバ側は Last-Event-ID で未受信分を再送(EventSource が
  // 自動付与・自動再接続)。クライアント側は接続/再接続のたびに1回だけ reconcile フェッチ。
  useEffect(() => {
    if (!streamHref) return;
    const es = new EventSource(streamHref);
    es.addEventListener("connected", () => {
      setLive(true);
      // 再接続直後に一覧を取り直して整合(Last-Event-ID 再送の隙間も埋める backstop)。
      void loadTasks(false);
    });
    es.addEventListener("task", (e) => {
      try {
        const snap = JSON.parse((e as MessageEvent).data) as TaskSnapshot;
        mergeTask(snap);
      } catch {
        // 不正なペイロードは無視(次のイベント/再接続時の reconcile で回復)。
      }
    });
    es.onerror = () => {
      // ブラウザが自動再接続する。切断中は下のフォールバックポーリングが補う。
      setLive(false);
    };
    return () => {
      es.close();
      setLive(false);
    };
  }, [streamHref, mergeTask, loadTasks]);

  // フォールバック更新: SSE が切断されている間だけ間隔ポーリングで再同期する。
  // SSE が健全なとき(live)はリアルタイム反映に任せ、冗長なフェッチは行わない。
  useEffect(() => {
    if (live) return;
    const id = setInterval(() => {
      void loadTasks(true);
    }, intervalSeconds * 1000);
    return () => clearInterval(id);
  }, [live, intervalSeconds, loadTasks]);

  const handleCreate = useCallback(async () => {
    const href = createHrefRef.current;
    if (!href) {
      setError("作成リンク(create)が未取得です。画面更新を試してください。");
      return;
    }
    try {
      setCreating(true);
      const created = await createTask(href);
      sessionQueuedIds.current.add(created.taskId);
      addToast("info", `タスク ${created.taskId.slice(0, 8)} をキューに追加しました`);
      await loadTasks(false);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setCreating(false);
    }
  }, [addToast, loadTasks]);

  const handleCancel = useCallback(async () => {
    if (!selectedId) return;
    const task = tasks.find((t) => t.taskId === selectedId);
    const cancelHref = task?._links["cancel"]?.href;
    if (!cancelHref) return;
    try {
      setCancelling(true);
      await cancelTask(cancelHref);
      addToast("info", `タスク ${selectedId.slice(0, 8)} に中止を要求しました`);
      await loadTasks(false);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setCancelling(false);
    }
  }, [selectedId, tasks, addToast, loadTasks]);

  const selectedTask = tasks.find((t) => t.taskId === selectedId) ?? null;
  const canCancel = !!selectedTask && !selectedTask.terminal;

  return (
    <main className="mx-auto max-w-6xl px-4 py-8">
      <header className="mb-6">
        <h1 className="text-2xl font-bold text-slate-900">
          Pub/Sub タスク管理
        </h1>
        <p className="mt-1 text-sm text-slate-500">
          「タスク実行（追加）」でジョブをキュー(トピック)へ publish し、バックエンドの購読側ワーカが
          非同期に処理します。ステータスは変更ではなく append-only で積み上げ、最新を表示しています。
          画面はサーバからの <span className="font-medium">SSE（リアルタイム配信）</span>で更新し、
          終端到達と同時に通知します（接続が切れている間だけ自動更新でフォールバック）。
        </p>
      </header>

      {error && (
        <div className="mb-4 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
          エラー: {error}
          <span className="ml-2 text-red-400">
            （バックエンド http://localhost:8080 が起動しているか確認してください）
          </span>
        </div>
      )}

      <div className="space-y-4">
        <Toolbar
          onCreate={handleCreate}
          onRefresh={() => loadTasks(true)}
          onCancel={handleCancel}
          canCancel={canCancel}
          creating={creating}
          refreshing={refreshing}
          cancelling={cancelling}
          intervalSeconds={intervalSeconds}
          onIntervalChange={setIntervalSeconds}
          lastUpdated={lastUpdated}
          live={live}
        />

        <TaskTable
          tasks={tasks}
          selectedId={selectedId}
          onSelect={setSelectedId}
        />
      </div>

      <footer className="mt-6 text-xs text-slate-400">
        中止するタスクを行クリック(またはラジオ)で選択し「タスク中止」を押してください。
        終端(正常/警告/異常/中止)に達したタスクは中止できません。
      </footer>

      <SnackbarStack toasts={toasts} onDismiss={dismissToast} />
    </main>
  );
}
