package com.example.pubsub.event;

import com.example.pubsub.domain.TaskStatus;
import java.time.Instant;

/**
 * 「タスクのステータスが新たに積まれた」というメッセージ。
 *
 * <p>処理ワーカ(プロデューサ)が STARTED / 進捗 / 終端ステータスを stack した直後に publish する。
 * 監査ログ用サブスクライバなどがこれを購読してファンアウトを観察できる。</p>
 */
public record TaskStatusChangedEvent(
        String taskId,
        TaskStatus status,
        int processedCount,
        int totalCount,
        Instant occurredAt
) implements TaskEvent {
}
