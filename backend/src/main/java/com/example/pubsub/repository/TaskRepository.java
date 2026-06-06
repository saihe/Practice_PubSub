package com.example.pubsub.repository;

import com.example.pubsub.domain.Task;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskRepository extends JpaRepository<Task, String> {

    /** 新しい順(追加日時の降順)で全タスクを取得する。 */
    List<Task> findAllByOrderByCreatedAtDesc();
}
