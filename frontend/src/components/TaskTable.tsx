"use client";

import type { TaskResource } from "@/lib/types";
import { StatusBadge } from "./StatusBadge";
import { formatDateTime, formatDuration } from "@/lib/format";

const COLUMNS = [
  "", // 選択
  "タスクID",
  "追加日時",
  "ステータス",
  "実行時間",
  "処理結果",
  "処理件数",
  "全体件数",
];

export function TaskTable({
  tasks,
  selectedId,
  onSelect,
}: {
  tasks: TaskResource[];
  selectedId: string | null;
  onSelect: (id: string) => void;
}) {
  return (
    <div className="overflow-x-auto rounded-xl border border-slate-200 bg-white shadow-sm">
      <table className="min-w-full divide-y divide-slate-200 text-sm">
        <thead className="bg-slate-50 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">
          <tr>
            {COLUMNS.map((c, i) => (
              <th key={i} className="whitespace-nowrap px-4 py-3">
                {c}
              </th>
            ))}
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {tasks.length === 0 && (
            <tr>
              <td
                colSpan={COLUMNS.length}
                className="px-4 py-10 text-center text-slate-400"
              >
                タスクはまだありません。「タスク実行（追加）」で投入してください。
              </td>
            </tr>
          )}

          {tasks.map((t) => {
            const selected = t.taskId === selectedId;
            const cancellable = !t.terminal;
            const progress =
              t.totalCount > 0
                ? Math.round((t.processedCount / t.totalCount) * 100)
                : 0;
            return (
              <tr
                key={t.taskId}
                onClick={() => onSelect(t.taskId)}
                className={`cursor-pointer transition ${
                  selected ? "bg-blue-50" : "hover:bg-slate-50"
                }`}
              >
                <td className="px-4 py-3">
                  <input
                    type="radio"
                    name="task-select"
                    checked={selected}
                    onChange={() => onSelect(t.taskId)}
                    className="h-4 w-4 cursor-pointer accent-blue-600"
                    aria-label={`タスク ${t.taskId} を選択`}
                  />
                </td>
                <td
                  className="px-4 py-3 font-mono text-xs text-slate-600"
                  title={t.taskId}
                >
                  {t.taskId.slice(0, 8)}
                  {!cancellable && (
                    <span className="ml-1 text-[10px] text-slate-300">●</span>
                  )}
                </td>
                <td className="whitespace-nowrap px-4 py-3 text-slate-600">
                  {formatDateTime(t.createdAt)}
                </td>
                <td className="px-4 py-3">
                  <StatusBadge status={t.status} label={t.statusLabel} />
                </td>
                <td className="whitespace-nowrap px-4 py-3 tabular-nums text-slate-600">
                  {formatDuration(t.executionMillis)}
                </td>
                <td className="px-4 py-3 text-slate-600">{t.result}</td>
                <td className="px-4 py-3">
                  <div className="flex items-center gap-2">
                    <span className="tabular-nums text-slate-700">
                      {t.processedCount}
                    </span>
                    <div className="h-1.5 w-20 overflow-hidden rounded-full bg-slate-100">
                      <div
                        className={`h-full rounded-full ${
                          t.status === "ERROR"
                            ? "bg-red-400"
                            : t.status === "CANCELLED"
                              ? "bg-zinc-400"
                              : t.status === "WARNING"
                                ? "bg-amber-400"
                                : "bg-blue-500"
                        }`}
                        style={{ width: `${progress}%` }}
                      />
                    </div>
                    {(t.successCount > 0 || t.failCount > 0) && (
                      <span className="text-[10px] text-slate-400">
                        成功{t.successCount}/失敗{t.failCount}
                      </span>
                    )}
                  </div>
                </td>
                <td className="px-4 py-3 tabular-nums text-slate-700">
                  {t.totalCount}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
