package com.example.pubsub.pubsub;

import com.example.pubsub.config.AppProperties;
import com.example.pubsub.domain.TaskStatus;
import com.example.pubsub.event.TaskQueuedEvent;
import com.example.pubsub.service.CancellationRegistry;
import com.example.pubsub.service.TaskEventStore;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Pub/Sub の「サブスクライバ(購読側・コンシューマ)」。
 *
 * <p>{@link TaskQueuedEvent} を購読し、別スレッド({@code @Async})で実処理を行う。
 * 発行側(REST 受付)とは疎結合で、ここが本デモの「ジョブワーカ」に相当する。</p>
 *
 * <p>処理ルール:
 * <ul>
 *   <li>全体件数ぶん、約 100ms/件(毎秒10件)でループ。</li>
 *   <li>9の倍数の件は「失敗」としてカウントのみ(中断しない)。</li>
 *   <li>失敗が1件以上 → 警告終了 / 失敗0件 → 正常終了。</li>
 *   <li>例外発生時のみ異常終了(観測用に確率的な例外注入あり)。</li>
 *   <li>中止フラグが立っていたら CANCELLED で終端。</li>
 * </ul>
 * いずれの終端ステータスも「変更」ではなく append-only で stack する。</p>
 */
@Component
public class TaskProcessingSubscriber {

    private static final Logger log = LoggerFactory.getLogger(TaskProcessingSubscriber.class);

    private final TaskEventStore eventStore;
    private final CancellationRegistry cancellationRegistry;
    private final AppProperties props;

    public TaskProcessingSubscriber(TaskEventStore eventStore,
                                    CancellationRegistry cancellationRegistry,
                                    AppProperties props) {
        this.eventStore = eventStore;
        this.cancellationRegistry = cancellationRegistry;
        this.props = props;
    }

    @Async("taskProcessingExecutor")
    @EventListener
    public void onTaskQueued(TaskQueuedEvent event) {
        String taskId = event.taskId();
        int total = event.totalCount();
        log.info("[SUB] received TaskQueuedEvent taskId={} total={}", taskId, total);

        int success = 0;
        int fail = 0;
        try {
            // 開始前に中止されていたら処理せず終端。
            if (cancellationRegistry.isCancelled(taskId)) {
                eventStore.append(taskId, TaskStatus.CANCELLED, 0, 0, 0, total,
                        "中止 (0/" + total + "件 処理済)");
                return;
            }

            eventStore.append(taskId, TaskStatus.STARTED, 0, 0, 0, total, "処理開始");

            // 異常終了(例外)を観測するための確率的な毒入りインデックス。
            int poisonIndex = -1;
            if (ThreadLocalRandom.current().nextDouble() < props.exceptionProbability()) {
                poisonIndex = ThreadLocalRandom.current().nextInt(1, total + 1);
            }

            for (int i = 1; i <= total; i++) {
                Thread.sleep(props.itemIntervalMs());

                // 中止要求の検知(協調的キャンセル)。
                if (cancellationRegistry.isCancelled(taskId)) {
                    int processed = success + fail;
                    eventStore.append(taskId, TaskStatus.CANCELLED, processed, success, fail, total,
                            "中止 (" + processed + "/" + total + "件 処理済)");
                    log.info("[SUB] cancelled taskId={} at {}/{}", taskId, processed, total);
                    return;
                }

                // 例外注入(発生時のみ異常終了)。
                if (i == poisonIndex) {
                    throw new IllegalStateException("シミュレートされた例外 (item #" + i + ")");
                }

                // 9の倍数は失敗カウントのみ(中断しない)。
                if (i % props.failureMultiple() == 0) {
                    fail++;
                } else {
                    success++;
                }

                // 進捗を一定間隔で stack(ポーリングで進捗が見えるように)。
                if (i % props.progressEventEvery() == 0 && i < total) {
                    eventStore.append(taskId, TaskStatus.STARTED, i, success, fail, total,
                            "処理中 (" + i + "/" + total + ")");
                }
            }

            // 完走。失敗有無で警告/正常を判定。
            TaskStatus terminal = fail > 0 ? TaskStatus.WARNING : TaskStatus.SUCCESS;
            String message = fail > 0
                    ? "完了 (成功 " + success + " / 失敗 " + fail + ")"
                    : "全" + total + "件 正常終了";
            eventStore.append(taskId, terminal, total, success, fail, total, message);
            log.info("[SUB] finished taskId={} status={} success={} fail={}",
                    taskId, terminal, success, fail);

        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            int processed = success + fail;
            eventStore.append(taskId, TaskStatus.ERROR, processed, success, fail, total,
                    "異常終了: スレッド割り込みにより中断");
            log.warn("[SUB] interrupted taskId={}", taskId);
        } catch (Exception ex) {
            int processed = success + fail;
            eventStore.append(taskId, TaskStatus.ERROR, processed, success, fail, total,
                    "異常終了: " + ex.getMessage());
            log.warn("[SUB] error taskId={} message={}", taskId, ex.getMessage());
        } finally {
            cancellationRegistry.clear(taskId);
        }
    }
}
