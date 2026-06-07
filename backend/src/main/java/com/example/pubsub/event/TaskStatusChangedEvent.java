package com.example.pubsub.event;

import com.example.pubsub.domain.TaskStatus;
import java.time.Instant;

/**
 * 「タスクのステータスが新たに積まれた」というメッセージ。
 *
 * <p>処理ワーカ(プロデューサ)が STARTED / 進捗 / 終端ステータスを stack した直後に publish する。
 * 監査ログ用サブスクライバなどがこれを購読してファンアウトを観察できる。</p>
 *
 * <p>{@code eventId} は append-only スタックの行ID(グローバル単調増加)。
 * SSE 配信ではこれを {@code id:} として使い、再接続時の {@code Last-Event-ID} による
 * 取りこぼし再送の基準にする。</p>
 */
public record TaskStatusChangedEvent(
        long eventId,
        String taskId,
        TaskStatus status,
        int processedCount,
        int totalCount,
        Instant occurredAt
) implements TaskEvent {
}
