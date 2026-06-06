package com.example.pubsub.service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Component;

/**
 * タスクの「中止フラグ」を保持するレジストリ(協調的キャンセル)。
 *
 * <p>中止操作はステータスを直接書き換えるのではなく、ここのフラグを立てるだけ。
 * 実際の終端ステータス(CANCELLED)は、処理ワーカがフラグを検知して自分で stack する。
 * これにより「あるタスクのイベントを書き込むのは(QUEUED 以降)常に処理ワーカ1スレッドのみ」
 * という単純な書き込みモデルを保てる。</p>
 */
@Component
public class CancellationRegistry {

    private final ConcurrentHashMap<String, AtomicBoolean> flags = new ConcurrentHashMap<>();

    /** タスク投入時に中止フラグ(false)を登録する。 */
    public void register(String taskId) {
        flags.put(taskId, new AtomicBoolean(false));
    }

    /** 中止を要求する。対象が登録済みなら true を返す。 */
    public boolean requestCancel(String taskId) {
        AtomicBoolean flag = flags.get(taskId);
        if (flag == null) {
            return false;
        }
        flag.set(true);
        return true;
    }

    /** 中止が要求されているか。 */
    public boolean isCancelled(String taskId) {
        AtomicBoolean flag = flags.get(taskId);
        return flag != null && flag.get();
    }

    /** 終端到達後にフラグを破棄してリークを防ぐ。 */
    public void clear(String taskId) {
        flags.remove(taskId);
    }
}
