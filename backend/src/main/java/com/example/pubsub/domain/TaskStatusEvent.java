package com.example.pubsub.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * タスクのステータスイベント(append-only)。
 *
 * <p>1タスクに対して時系列で積み上がる。各行はその時点のスナップショット
 * (ステータス・処理件数・成功/失敗件数・メッセージ)を保持する。
 * 主キー {@code id} は自動採番で単調増加するため、ある task_id について
 * 最大 {@code id} の行が「最新状態」となる。</p>
 *
 * <p>UPDATE は一切行わず INSERT のみ。これがステータスを「スタックする」実体。</p>
 */
@Entity
@Table(name = "task_status_event",
        indexes = {@Index(name = "idx_event_task", columnList = "task_id, id")})
public class TaskStatusEvent {

    /** スタックの順序を担保する自動採番ID(グローバル単調増加)。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    /** 対象タスクID。 */
    @Column(name = "task_id", nullable = false, updatable = false, length = 36)
    private String taskId;

    /** この時点のステータス。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, updatable = false, length = 16)
    private TaskStatus status;

    /** 処理済み件数(成功+失敗)。 */
    @Column(name = "processed_count", nullable = false, updatable = false)
    private int processedCount;

    /** 成功件数。 */
    @Column(name = "success_count", nullable = false, updatable = false)
    private int successCount;

    /** 失敗件数(9の倍数でカウントされる件数)。 */
    @Column(name = "fail_count", nullable = false, updatable = false)
    private int failCount;

    /** 補足メッセージ(処理結果/例外内容など)。 */
    @Column(name = "message", updatable = false, length = 500)
    private String message;

    /** 発生時刻。 */
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected TaskStatusEvent() {
        // for JPA
    }

    public TaskStatusEvent(String taskId, TaskStatus status, int processedCount,
                           int successCount, int failCount, String message, Instant occurredAt) {
        this.taskId = taskId;
        this.status = status;
        this.processedCount = processedCount;
        this.successCount = successCount;
        this.failCount = failCount;
        this.message = message;
        this.occurredAt = occurredAt;
    }

    public Long getId() {
        return id;
    }

    public String getTaskId() {
        return taskId;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public int getProcessedCount() {
        return processedCount;
    }

    public int getSuccessCount() {
        return successCount;
    }

    public int getFailCount() {
        return failCount;
    }

    public String getMessage() {
        return message;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
