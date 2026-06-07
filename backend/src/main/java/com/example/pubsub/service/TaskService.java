package com.example.pubsub.service;

import com.example.pubsub.config.AppProperties;
import com.example.pubsub.domain.Task;
import com.example.pubsub.domain.TaskStatus;
import com.example.pubsub.domain.TaskStatusEvent;
import com.example.pubsub.event.TaskQueuedEvent;
import com.example.pubsub.pubsub.TaskEventPublisher;
import com.example.pubsub.repository.TaskRepository;
import com.example.pubsub.repository.TaskStatusEventRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * タスクのユースケース(キュー投入・中止・参照)を提供するサービス。
 */
@Service
public class TaskService {

    private final TaskRepository taskRepository;
    private final TaskStatusEventRepository eventRepository;
    private final TaskEventStore eventStore;
    private final TaskEventPublisher publisher;
    private final CancellationRegistry cancellationRegistry;
    private final AppProperties props;

    public TaskService(TaskRepository taskRepository,
                       TaskStatusEventRepository eventRepository,
                       TaskEventStore eventStore,
                       TaskEventPublisher publisher,
                       CancellationRegistry cancellationRegistry,
                       AppProperties props) {
        this.taskRepository = taskRepository;
        this.eventRepository = eventRepository;
        this.eventStore = eventStore;
        this.publisher = publisher;
        this.cancellationRegistry = cancellationRegistry;
        this.props = props;
    }

    /**
     * タスクをキューに追加する(プロデューサ)。
     *
     * <p>全体件数を確定し、Task と QUEUED イベントを保存したうえで
     * {@link TaskQueuedEvent} を publish する。実処理は購読側ワーカが別スレッドで行う。</p>
     */
    @Transactional
    public TaskView queueTask() {
        int totalCount = ThreadLocalRandom.current()
                .nextInt(props.totalCountMin(), props.totalCountMax() + 1);
        String id = UUID.randomUUID().toString();
        Instant now = Instant.now();

        taskRepository.save(new Task(id, now, totalCount));
        cancellationRegistry.register(id);
        eventStore.persist(id, TaskStatus.QUEUED, 0, 0, 0, "キュー待機中");

        // ここで「トピックへ publish」。誰が購読しているかは関知しない。
        publisher.publish(new TaskQueuedEvent(id, totalCount, now));

        // POST のレスポンスは「キューに追加した直後」の状態を表す。
        // (購読側ワーカが即座に STARTED を積む可能性があるため、再クエリせず確定値を返す)
        return new TaskView(id, now, TaskStatus.QUEUED, TaskStatus.QUEUED.label(),
                "キュー待機中", 0, 0, 0, totalCount, 0L, false);
    }

    /**
     * タスクの中止を要求する。
     *
     * <p>ステータスは直接書き換えず中止フラグのみ立てる。終端(CANCELLED)の積み込みは
     * 処理ワーカが行う。すでに終端のタスクや未登録IDは中止できない。</p>
     *
     * @return 中止を受け付けられたら現在の {@link TaskView}、不可なら空
     */
    @Transactional(readOnly = true)
    public Optional<TaskView> requestCancel(String taskId) {
        Optional<Task> taskOpt = taskRepository.findById(taskId);
        if (taskOpt.isEmpty()) {
            return Optional.empty();
        }
        TaskStatus current = currentStatus(taskId);
        if (current.terminal()) {
            return Optional.empty();
        }
        cancellationRegistry.requestCancel(taskId);
        return Optional.of(buildView(taskOpt.get()));
    }

    /** 全タスクを新しい順で現在状態に畳み込んで返す。 */
    @Transactional(readOnly = true)
    public List<TaskView> listTasks() {
        return taskRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::buildView)
                .toList();
    }

    /** 単一タスクの現在状態。 */
    @Transactional(readOnly = true)
    public Optional<TaskView> getTask(String taskId) {
        return taskRepository.findById(taskId).map(this::buildView);
    }

    /**
     * 指定 id より後に積まれたステータスイベントを古い順で返す。
     * SSE 再接続時の {@code Last-Event-ID} に基づく取りこぼし再送に使う。
     */
    @Transactional(readOnly = true)
    public List<TaskStatusEvent> statusEventsAfter(long lastEventId) {
        return eventRepository.findByIdGreaterThanOrderByIdAsc(lastEventId);
    }

    /** 指定タスクの積み上がったステータス履歴(スタック)。 */
    @Transactional(readOnly = true)
    public Optional<List<TaskStatusEvent>> getStatusHistory(String taskId) {
        if (!taskRepository.existsById(taskId)) {
            return Optional.empty();
        }
        return Optional.of(eventRepository.findByTaskIdOrderByIdAsc(taskId));
    }

    private TaskStatus currentStatus(String taskId) {
        return eventRepository.findFirstByTaskIdOrderByIdDesc(taskId)
                .map(TaskStatusEvent::getStatus)
                .orElse(TaskStatus.QUEUED);
    }

    /**
     * Task + 積み上がったイベントの最新行から現在状態を畳み込む。
     */
    TaskView buildView(Task task) {
        String id = task.getId();
        TaskStatusEvent latest = eventRepository.findFirstByTaskIdOrderByIdDesc(id).orElse(null);

        TaskStatus status = latest != null ? latest.getStatus() : TaskStatus.QUEUED;
        int processed = latest != null ? latest.getProcessedCount() : 0;
        int success = latest != null ? latest.getSuccessCount() : 0;
        int fail = latest != null ? latest.getFailCount() : 0;
        int total = task.getTotalCount();

        long executionMillis = computeExecutionMillis(id, status, latest);
        String result = computeResult(status, processed, success, fail, total, latest);

        return new TaskView(
                id, task.getCreatedAt(), status, status.label(), result,
                processed, success, fail, total, executionMillis, status.terminal());
    }

    private long computeExecutionMillis(String taskId, TaskStatus status, TaskStatusEvent latest) {
        Optional<TaskStatusEvent> started =
                eventRepository.findFirstByTaskIdAndStatusOrderByIdAsc(taskId, TaskStatus.STARTED);
        if (started.isEmpty()) {
            return 0L;
        }
        Instant start = started.get().getOccurredAt();
        Instant end = (status.terminal() && latest != null) ? latest.getOccurredAt() : Instant.now();
        long millis = Duration.between(start, end).toMillis();
        return Math.max(0L, millis);
    }

    private String computeResult(TaskStatus status, int processed, int success, int fail,
                                 int total, TaskStatusEvent latest) {
        return switch (status) {
            case QUEUED -> "キュー待機中";
            case STARTED -> "処理中 (" + processed + "/" + total + ")";
            case SUCCESS -> "全" + total + "件 正常終了";
            case WARNING -> "完了 (成功 " + success + " / 失敗 " + fail + ")";
            case ERROR -> latest != null && latest.getMessage() != null
                    ? latest.getMessage()
                    : "異常終了";
            case CANCELLED -> "中止 (" + processed + "/" + total + "件 処理済)";
        };
    }
}
