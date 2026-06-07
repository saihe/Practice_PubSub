package com.example.pubsub.service;

import com.example.pubsub.domain.TaskStatus;
import com.example.pubsub.domain.TaskStatusEvent;
import com.example.pubsub.event.TaskStatusChangedEvent;
import com.example.pubsub.pubsub.TaskEventPublisher;
import com.example.pubsub.repository.TaskStatusEventRepository;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * ステータスイベントを append-only で書き込む唯一の出入口。
 *
 * <p>UPDATE は提供しない。すべて INSERT。これが「状態をスタックする」実装の中心。</p>
 */
@Component
public class TaskEventStore {

    private final TaskStatusEventRepository repository;
    private final TaskEventPublisher publisher;

    public TaskEventStore(TaskStatusEventRepository repository, TaskEventPublisher publisher) {
        this.repository = repository;
        this.publisher = publisher;
    }

    /** イベントを1件積むだけ(Pub/Sub への発行はしない)。QUEUED の初期積み込みに使う。 */
    public TaskStatusEvent persist(String taskId, TaskStatus status, int processedCount,
                                   int successCount, int failCount, String message) {
        TaskStatusEvent event = new TaskStatusEvent(
                taskId, status, processedCount, successCount, failCount, message, Instant.now());
        return repository.save(event);
    }

    /**
     * イベントを1件積み、さらに {@link TaskStatusChangedEvent} を発行(publish)する。
     * 処理ワーカが STARTED / 進捗 / 終端ステータスを積むときに使う。
     */
    public TaskStatusEvent append(String taskId, TaskStatus status, int processedCount,
                                  int successCount, int failCount, int totalCount, String message) {
        TaskStatusEvent event = persist(taskId, status, processedCount, successCount, failCount, message);
        publisher.publish(new TaskStatusChangedEvent(
                event.getId(), taskId, status, processedCount, totalCount, event.getOccurredAt()));
        return event;
    }
}
