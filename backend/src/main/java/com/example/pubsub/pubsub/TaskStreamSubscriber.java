package com.example.pubsub.pubsub;

import com.example.pubsub.domain.TaskStatusEvent;
import com.example.pubsub.event.TaskStatusChangedEvent;
import com.example.pubsub.service.TaskService;
import com.example.pubsub.service.TaskView;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Pub/Sub の購読側を「ブラウザ」まで延長するリアルタイム配信サブスクライバ。
 *
 * <p>{@link TaskAuditSubscriber} と同じトピック({@link TaskStatusChangedEvent})を購読し、
 * 接続中の各ブラウザへ Server-Sent Events(SSE)で push する。1メッセージが複数ブラウザへ
 * ファンアウトする様子は Pub/Sub の特徴そのもの。</p>
 *
 * <p>各 SSE イベントには append-only スタックの行ID({@code eventId})を {@code id:} として付与する。
 * ブラウザの再接続時には {@code Last-Event-ID} ヘッダが送られてくるので、それ以降に積まれた
 * イベントを {@link #subscribe(String)} で再送(リプレイ)してから live 配信に合流させる。
 * これにより切断中のイベントも取りこぼさない(at-least-once 相当)。</p>
 */
@Component
public class TaskStreamSubscriber {

    private static final Logger log = LoggerFactory.getLogger(TaskStreamSubscriber.class);

    /** SSE 接続の保持上限(ブラウザを開いている間。30分でタイムアウトし、ブラウザが自動再接続)。 */
    private static final long SSE_TIMEOUT_MS = 30 * 60 * 1000L;

    private final TaskService taskService;

    /** 接続中のブラウザ購読者。配信中の追加/削除に強い CopyOnWrite を採用。 */
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public TaskStreamSubscriber(TaskService taskService) {
        this.taskService = taskService;
    }

    /**
     * 新しいブラウザ購読者を登録する。
     *
     * <p>処理順:
     * <ol>
     *   <li>接続確立の合図(connected)を送る。</li>
     *   <li>{@code Last-Event-ID} が指定されていれば、それ以降のイベントをリプレイ(取りこぼし再送)。</li>
     *   <li>その後に live 配信リストへ加える。</li>
     * </ol>
     * リプレイは登録前に「このリクエストスレッドだけ」で行うため、live 配信(別スレッド)と
     * 同一 emitter への同時書き込みが起きない。リプレイ中のごく短い隙間はクライアント側の
     * 再接続時 reconcile フェッチが backstop する。</p>
     *
     * @param lastEventId ブラウザが最後に受信したイベントID(初回接続時は null)
     */
    public SseEmitter subscribe(String lastEventId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));

        try {
            // 接続確立(プロキシのバッファリング対策にもなる)。id は付けない=Last-Event-ID を巻き戻さない。
            emitter.send(SseEmitter.event().name("connected").data("ok"));
            replayMissed(emitter, lastEventId);
        } catch (IOException e) {
            emitter.completeWithError(e);
            return emitter;
        }

        emitters.add(emitter);
        log.info("[SSE] subscriber connected (total={}, lastEventId={})", emitters.size(), lastEventId);
        return emitter;
    }

    /** {@code Last-Event-ID} 以降に積まれたイベントを、行IDを id として再送する。 */
    private void replayMissed(SseEmitter emitter, String lastEventId) throws IOException {
        long lastId = parseLastEventId(lastEventId);
        if (lastId < 0) {
            return;
        }
        List<TaskStatusEvent> missed = taskService.statusEventsAfter(lastId);
        if (missed.isEmpty()) {
            return;
        }
        // 同一タスクの最新状態は1回だけ畳み込んで使い回す(無駄な再クエリを避ける)。
        Map<String, TaskView> snapshotCache = new HashMap<>();
        int replayed = 0;
        for (TaskStatusEvent ev : missed) {
            TaskView view = snapshotCache.computeIfAbsent(ev.getTaskId(),
                    id -> taskService.getTask(id).orElse(null));
            if (view == null) {
                continue;
            }
            emitter.send(SseEmitter.event().id(String.valueOf(ev.getId())).name("task").data(view));
            replayed++;
        }
        log.info("[SSE] replayed {} missed event(s) since id={}", replayed, lastId);
    }

    private long parseLastEventId(String lastEventId) {
        if (lastEventId == null || lastEventId.isBlank()) {
            return -1L;
        }
        try {
            return Long.parseLong(lastEventId.trim());
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    /**
     * ステータスが積まれるたびに、対象タスクの最新スナップショットを全購読者へ live 配信する。
     * SSE の {@code id:} には行ID(eventId)を載せ、再接続時の取りこぼし再送の基準にする。
     */
    @EventListener
    public void onStatusChanged(TaskStatusChangedEvent event) {
        taskService.getTask(event.taskId())
                .ifPresent(view -> broadcast(event.eventId(), view));
    }

    private void broadcast(long eventId, TaskView view) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().id(String.valueOf(eventId)).name("task").data(view));
            } catch (Exception e) {
                // 送信に失敗した購読者は切断とみなして除去。
                emitters.remove(emitter);
            }
        }
    }
}
