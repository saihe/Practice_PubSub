package com.example.pubsub;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.pubsub.domain.TaskStatus;
import com.example.pubsub.domain.TaskStatusEvent;
import com.example.pubsub.service.TaskService;
import com.example.pubsub.service.TaskView;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * キュー投入 → 非同期処理 → 終端までの一連を検証する。
 * 9の倍数の失敗カウントと、警告/正常終了の判定が要点。
 */
@SpringBootTest(properties = {
        "app.task.item-interval-ms=1",
        "app.task.exception-probability=0.0"
})
class TaskProcessingIntegrationTest {

    @Autowired
    TaskService taskService;

    @Test
    void queuedTaskRunsToTerminalAndCountsFailuresOnMultiplesOfNine() {
        TaskView queued = taskService.queueTask();
        assertThat(queued.status()).isEqualTo(TaskStatus.QUEUED);

        TaskView terminal = awaitTerminal(queued.taskId());

        int total = terminal.totalCount();
        int expectedFails = total / 9; // [1,total] に含まれる9の倍数の個数

        assertThat(terminal.processedCount()).isEqualTo(total);
        assertThat(terminal.successCount() + terminal.failCount()).isEqualTo(total);
        assertThat(terminal.failCount()).isEqualTo(expectedFails);
        assertThat(terminal.status())
                .isEqualTo(expectedFails > 0 ? TaskStatus.WARNING : TaskStatus.SUCCESS);

        // ステータスは「変更」ではなく append-only で積まれている。
        List<TaskStatusEvent> stack = taskService.getStatusHistory(queued.taskId()).orElseThrow();
        assertThat(stack.get(0).getStatus()).isEqualTo(TaskStatus.QUEUED);
        assertThat(stack).anyMatch(e -> e.getStatus() == TaskStatus.STARTED);
        assertThat(stack.get(stack.size() - 1).getStatus()).isEqualTo(terminal.status());
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
