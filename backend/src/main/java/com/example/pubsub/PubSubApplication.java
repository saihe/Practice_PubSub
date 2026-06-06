package com.example.pubsub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * アプリケーションのエントリポイント。
 *
 * <p>{@link EnableAsync} を有効化することで、Pub/Sub の購読側
 * ({@code @EventListener} + {@code @Async}) が呼び出し元と別スレッドで動作する。
 * これにより「タスクを発行(publish)したスレッド」と「タスクを処理(subscribe)するスレッド」が
 * 分離され、メッセージブローカ的な非同期処理を疑似的に体験できる。</p>
 */
@SpringBootApplication
@EnableAsync
@ConfigurationPropertiesScan
public class PubSubApplication {

    public static void main(String[] args) {
        SpringApplication.run(PubSubApplication.class, args);
    }
}
