package com.example.pubsub.repository;

import com.example.pubsub.domain.TaskStatus;
import com.example.pubsub.domain.TaskStatusEvent;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskStatusEventRepository extends JpaRepository<TaskStatusEvent, Long> {

    /** 指定タスクの全イベントを積まれた順(古い→新しい)で取得する。 */
    List<TaskStatusEvent> findByTaskIdOrderByIdAsc(String taskId);

    /** 指定タスクの最新イベント(=現在状態)を取得する。 */
    Optional<TaskStatusEvent> findFirstByTaskIdOrderByIdDesc(String taskId);

    /** 指定タスク・指定ステータスの最初のイベント(例: 開始時刻の取得に使用)。 */
    Optional<TaskStatusEvent> findFirstByTaskIdAndStatusOrderByIdAsc(String taskId, TaskStatus status);
}
