package com.example.pubsub.pubsub;

import com.example.pubsub.event.TaskEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 同一トピックを購読するもう一つのサブスクライバ(監査ログ)。
 *
 * <p>{@link TaskProcessingSubscriber} とは独立に、すべての {@link TaskEvent} を購読して
 * ログ出力するだけ。1つのメッセージが複数サブスクライバへ「ファンアウト」される様子を
 * コンソールログで観察できる(Pub/Sub の特徴)。</p>
 */
@Component
public class TaskAuditSubscriber {

    private static final Logger log = LoggerFactory.getLogger(TaskAuditSubscriber.class);

    @EventListener
    public void onAnyTaskEvent(TaskEvent event) {
        log.info("[AUDIT] taskId={} status={} at={}",
                event.taskId(), event.status(), event.occurredAt());
    }
}
