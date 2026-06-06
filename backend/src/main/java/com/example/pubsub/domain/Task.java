package com.example.pubsub.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * タスクの不変な属性のみを保持するエンティティ。
 *
 * <p>現在ステータス・処理件数などの「変化する状態」はここには持たず、
 * {@link TaskStatusEvent} 側に append-only で積む。これにより
 * 「状態をスタックして最新を取得する」モデルを実現する。</p>
 */
@Entity
@Table(name = "task")
public class Task {

    /** タスクID(UUID 文字列)。 */
    @Id
    @Column(name = "id", nullable = false, updatable = false, length = 36)
    private String id;

    /** 追加日時(キュー投入時刻)。 */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** 全体件数(キュー投入時に確定する、10〜100の乱数)。 */
    @Column(name = "total_count", nullable = false, updatable = false)
    private int totalCount;

    protected Task() {
        // for JPA
    }

    public Task(String id, Instant createdAt, int totalCount) {
        this.id = id;
        this.createdAt = createdAt;
        this.totalCount = totalCount;
    }

    public String getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public int getTotalCount() {
        return totalCount;
    }
}
