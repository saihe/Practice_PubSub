package com.example.pubsub.web;

import com.example.pubsub.domain.TaskStatus;
import com.example.pubsub.domain.TaskStatusEvent;
import java.time.Instant;

/**
 * ステータス履歴(スタック)1件分のレスポンス表現。
 */
public record StatusEventDto(
        long sequence,
        TaskStatus status,
        String statusLabel,
        int processedCount,
        int successCount,
        int failCount,
        String message,
        Instant occurredAt
) {
    public static StatusEventDto from(TaskStatusEvent e) {
        return new StatusEventDto(
                e.getId(), e.getStatus(), e.getStatus().label(),
                e.getProcessedCount(), e.getSuccessCount(), e.getFailCount(),
                e.getMessage(), e.getOccurredAt());
    }
}
