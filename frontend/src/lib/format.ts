// 表示用フォーマット関数。

/** ISO 文字列を日本語ロケールの日時表記へ。 */
export function formatDateTime(iso: string): string {
  const d = new Date(iso);
  return d.toLocaleString("ja-JP", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  });
}

/** 実行時間(ミリ秒)を「12.3s」形式へ。未開始(0)は「—」。 */
export function formatDuration(ms: number): string {
  if (!ms || ms <= 0) {
    return "—";
  }
  const seconds = ms / 1000;
  if (seconds < 60) {
    return `${seconds.toFixed(1)}s`;
  }
  const m = Math.floor(seconds / 60);
  const s = Math.floor(seconds % 60);
  return `${m}分${s.toString().padStart(2, "0")}秒`;
}

/** 時刻のみ(最終更新表示用)。 */
export function formatClock(d: Date): string {
  return d.toLocaleTimeString("ja-JP", {
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  });
}
