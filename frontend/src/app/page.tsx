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
import type { TaskResource, TaskStatus } from "@/lib/types";

const DEFAULT_INTERVAL: IntervalSeconds = 10;

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

  // 自動更新: 選択間隔ごとにフェッチ(画面を開いている間ずっと有効)。
  useEffect(() => {
    const id = setInterval(() => {
      void loadTasks(true);
    }, intervalSeconds * 1000);
    return () => clearInterval(id);
  }, [intervalSeconds, loadTasks]);

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
