package com.example.pubsub.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 非同期(購読側)処理用のスレッドプール定義。
 *
 * <p>複数タスクを同時に流して Pub/Sub の並行処理を観察できるよう、
 * ある程度のワーカ数を確保する。キュー投入(publish)はリクエストスレッドで即時に返り、
 * 実処理(subscribe)はこのプール上で走る。</p>
 */
@Configuration
public class AsyncConfig {

    /** {@code @Async("taskProcessingExecutor")} から参照される購読側ワーカプール。 */
    @Bean("taskProcessingExecutor")
    public Executor taskProcessingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("task-sub-");
        executor.initialize();
        return executor;
    }
}
