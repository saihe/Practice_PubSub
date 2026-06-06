package com.example.pubsub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yml の {@code app.task.*} をバインドする設定クラス。
 *
 * @param itemIntervalMs      1件あたりの処理間隔(ms)
 * @param totalCountMin       全体件数の下限
 * @param totalCountMax       全体件数の上限
 * @param failureMultiple     失敗扱いにする倍数(9の倍数)
 * @param progressEventEvery  進捗ステータスを stack する件数間隔
 * @param exceptionProbability 1タスクあたり例外を注入する確率(0.0〜1.0)
 */
@ConfigurationProperties(prefix = "app.task")
public record AppProperties(
        int itemIntervalMs,
        int totalCountMin,
        int totalCountMax,
        int failureMultiple,
        int progressEventEvery,
        double exceptionProbability
) {
}
