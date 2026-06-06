package com.example.pubsub.pubsub;

import com.example.pubsub.event.TaskEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Pub/Sub の「パブリッシャ(発行側)」。
 *
 * <p>Spring の {@link ApplicationEventPublisher} を薄くラップし、
 * ドメイン上の概念として「メッセージをトピックへ publish する」操作を表現する。
 * 購読側がいくつ居るか・誰かは発行側は関知しない(疎結合)。</p>
 */
@Component
public class TaskEventPublisher {

    private final ApplicationEventPublisher delegate;

    public TaskEventPublisher(ApplicationEventPublisher delegate) {
        this.delegate = delegate;
    }

    /** メッセージを発行し、購読しているすべてのサブスクライバへファンアウトする。 */
    public void publish(TaskEvent event) {
        delegate.publishEvent(event);
    }
}
