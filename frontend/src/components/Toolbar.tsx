"use client";

import { IntervalSelect, type IntervalSeconds } from "./IntervalSelect";
import { formatClock } from "@/lib/format";

export function Toolbar({
  onCreate,
  onRefresh,
  onCancel,
  canCancel,
  creating,
  refreshing,
  cancelling,
  intervalSeconds,
  onIntervalChange,
  lastUpdated,
  live,
}: {
  onCreate: () => void;
  onRefresh: () => void;
  onCancel: () => void;
  canCancel: boolean;
  creating: boolean;
  refreshing: boolean;
  cancelling: boolean;
  intervalSeconds: IntervalSeconds;
  onIntervalChange: (v: IntervalSeconds) => void;
  lastUpdated: Date | null;
  live: boolean;
}) {
  return (
    <div className="flex flex-wrap items-end justify-between gap-4 rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
      <div className="flex flex-wrap items-center gap-2">
        <button
          type="button"
          onClick={onCreate}
          disabled={creating}
          className="rounded-md bg-blue-600 px-4 py-2 text-sm font-semibold text-white shadow-sm transition hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:cursor-not-allowed disabled:opacity-50"
        >
          {creating ? "追加中…" : "タスク実行（追加）"}
        </button>

        <button
          type="button"
          onClick={onRefresh}
          disabled={refreshing}
          className="rounded-md border border-slate-300 bg-white px-4 py-2 text-sm font-semibold text-slate-700 shadow-sm transition hover:bg-slate-50 focus:outline-none focus:ring-2 focus:ring-slate-400 disabled:cursor-not-allowed disabled:opacity-50"
        >
          {refreshing ? "更新中…" : "画面更新"}
        </button>

        <button
          type="button"
          onClick={onCancel}
          disabled={!canCancel || cancelling}
          className="rounded-md bg-amber-500 px-4 py-2 text-sm font-semibold text-white shadow-sm transition hover:bg-amber-600 focus:outline-none focus:ring-2 focus:ring-amber-400 disabled:cursor-not-allowed disabled:bg-slate-300"
          title={
            canCancel
              ? "選択中のタスクを中止します"
              : "中止できるタスク(未終端)を選択してください"
          }
        >
          {cancelling ? "中止中…" : "タスク中止"}
        </button>
      </div>

      <div className="flex items-end gap-4">
        <div className="text-right text-xs text-slate-500">
          <div className="flex items-center justify-end gap-1.5">
            <span
              className={`inline-block h-2 w-2 rounded-full ${
                live ? "animate-pulse bg-emerald-500" : "bg-slate-300"
              }`}
              aria-hidden
            />
            <span>{live ? "リアルタイム接続中（SSE）" : "リアルタイム未接続"}</span>
          </div>
          <div>
            {live
              ? "自動更新: SSE リアルタイム（ポーリング停止）"
              : `未接続: ${intervalSeconds} 秒ごとに再取得`}
          </div>
          <div>
            最終更新: {lastUpdated ? formatClock(lastUpdated) : "—"}
            {refreshing && <span className="ml-1 text-blue-500">●</span>}
          </div>
        </div>
        <IntervalSelect value={intervalSeconds} onChange={onIntervalChange} />
      </div>
    </div>
  );
}
