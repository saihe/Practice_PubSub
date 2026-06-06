package com.example.pubsub.event;

import com.example.pubsub.domain.TaskStatus;
import java.time.Instant;

/**
 * 「タスクがキューに追加された」というメッセージ。
 *
 * <p>これがプロデューサ(REST 受付)→コンシューマ(処理ワーカ)を結ぶ
 * 本デモの中心的な Pub/Sub チャネル。メッセージブローカでいう
 * 「ジョブをトピックに publish した」状態に相当する。</p>
 */
public record TaskQueuedEvent(String taskId, int totalCount, Instant occurredAt) implements TaskEvent {

    @Override
    public TaskStatus status() {
        return TaskStatus.QUEUED;
    }
}
