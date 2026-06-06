package com.example.pubsub.event;

import com.example.pubsub.domain.TaskStatus;
import java.time.Instant;

/**
 * Pub/Sub で配信されるタスク関連メッセージの共通インタフェース。
 *
 * <p>Spring の {@code ApplicationEventPublisher} を「トピック」、
 * {@code @EventListener} を「サブスクライバ」と見立てている。
 * このインタフェースを実装したメッセージが各サブスクライバへファンアウトされる。</p>
 */
public sealed interface TaskEvent
        permits TaskQueuedEvent, TaskStatusChangedEvent {

    /** 対象タスクID。 */
    String taskId();

    /** メッセージ発生時刻。 */
    Instant occurredAt();

    /** このメッセージが表すステータス。 */
    TaskStatus status();
}
