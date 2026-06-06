import type { TaskStatus } from "@/lib/types";

/** ステータスごとの配色定義。 */
const STYLES: Record<TaskStatus, { dot: string; chip: string }> = {
  QUEUED: { dot: "bg-slate-400", chip: "bg-slate-100 text-slate-700 ring-slate-300" },
  STARTED: { dot: "bg-blue-500 animate-pulse", chip: "bg-blue-50 text-blue-700 ring-blue-300" },
  SUCCESS: { dot: "bg-emerald-500", chip: "bg-emerald-50 text-emerald-700 ring-emerald-300" },
  WARNING: { dot: "bg-amber-500", chip: "bg-amber-50 text-amber-800 ring-amber-300" },
  ERROR: { dot: "bg-red-500", chip: "bg-red-50 text-red-700 ring-red-300" },
  CANCELLED: { dot: "bg-zinc-500", chip: "bg-zinc-100 text-zinc-700 ring-zinc-300" },
};

export function StatusBadge({
  status,
  label,
}: {
  status: TaskStatus;
  label: string;
}) {
  const style = STYLES[status] ?? STYLES.QUEUED;
  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-medium ring-1 ring-inset ${style.chip}`}
    >
      <span className={`h-1.5 w-1.5 rounded-full ${style.dot}`} aria-hidden />
      {label}
    </span>
  );
}
