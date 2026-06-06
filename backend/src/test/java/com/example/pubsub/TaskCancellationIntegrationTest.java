package com.example.pubsub;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.pubsub.domain.TaskStatus;
import com.example.pubsub.service.TaskService;
import com.example.pubsub.service.TaskView;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 中止要求が終端ステータス CANCELLED として stack されることを検証する。
 * 処理途中で中止できるよう、1件あたりの処理間隔と全体件数を十分に確保する。
 */
@SpringBootTest(properties = {
        "app.task.item-interval-ms=40",
        "app.task.total-count-min=60",
        "app.task.total-count-max=100",
        "app.task.exception-probability=0.0"
})
class TaskCancellationIntegrationTest {

    @Autowired
    TaskService taskService;

    @Test
    void cancellingRunningTaskEndsWithCancelledStatus() {
        TaskView queued = taskService.queueTask();

        // すぐに中止要求(協調的キャンセル)。
        assertThat(taskService.requestCancel(queued.taskId())).isPresent();

        TaskView terminal = awaitTerminal(queued.taskId());
        assertThat(terminal.status()).isEqualTo(TaskStatus.CANCELLED);
        assertThat(terminal.processedCount()).isLessThan(terminal.totalCount());

        // 終端後の再中止は受け付けない。
        assertThat(taskService.requestCancel(queued.taskId())).isEmpty();
    }

    private TaskView awaitTerminal(String taskId) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(20));
        while (Instant.now().isBefore(deadline)) {
            TaskView view = taskService.getTask(taskId).orElseThrow();
            if (view.terminal()) {
                return view;
            }
            sleep(20);
        }
        throw new AssertionError("task did not reach a terminal status in time");
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
