package com.example.pubsub.service;

import com.example.pubsub.domain.TaskStatus;
import java.time.Instant;

/**
 * 画面表示用にタスクの「現在状態」を1件に畳み込んだ読み取りモデル。
 *
 * <p>不変な {@code Task} と、積み上がった {@code TaskStatusEvent} の最新行から構築する。</p>
 *
 * @param taskId         タスクID
 * @param createdAt      追加日時
 * @param status         現在ステータス(最新イベント)
 * @param statusLabel    ステータスの日本語ラベル
 * @param result         処理結果メッセージ(画面の「処理結果」列)
 * @param processedCount 処理件数
 * @param successCount   成功件数
 * @param failCount      失敗件数
 * @param totalCount     全体件数
 * @param executionMillis 実行時間(開始〜終端/現時点, ミリ秒)。未開始は0。
 * @param terminal       終端状態かどうか(中止リンクの有無に使用)
 */
public record TaskView(
        String taskId,
        Instant createdAt,
        TaskStatus status,
        String statusLabel,
        String result,
        int processedCount,
        int successCount,
        int failCount,
        int totalCount,
        long executionMillis,
        boolean terminal
) {
}
