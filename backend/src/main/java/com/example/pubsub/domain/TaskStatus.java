package com.example.pubsub.domain;

/**
 * タスクのステータス。
 *
 * <p>状態は「変更(UPDATE)」ではなく {@link TaskStatusEvent} として
 * append-only に積み上げ(stack)、最新の1件を現在状態として読み出す。</p>
 *
 * <ul>
 *   <li>{@link #QUEUED}    キューに追加 … 投入直後・処理待ち</li>
 *   <li>{@link #STARTED}   開始       … 購読側ワーカが処理を開始/進行中</li>
 *   <li>{@link #SUCCESS}   正常終了    … 失敗0件で完走(終端)</li>
 *   <li>{@link #WARNING}   警告終了    … 失敗が1件以上あったが完走(終端)</li>
 *   <li>{@link #ERROR}     異常終了    … 例外が発生して中断(終端)</li>
 *   <li>{@link #CANCELLED} 中止終了    … 「タスク中止」操作で停止(終端)</li>
 * </ul>
 *
 * <p>※ 指示で定義された5ステータス(QUEUED/STARTED/SUCCESS/WARNING/ERROR)は
 * 「通常処理の結果」を表す。{@link #CANCELLED} は「タスク中止」ボタンに正しい終端状態を
 * 与えるために追加した拡張ステータスである(異常終了は例外時のみに予約しているため流用しない)。
 * 詳細は docs/instruction-history.md / docs/spec-table.md を参照。</p>
 */
public enum TaskStatus {
    QUEUED("キューに追加", false),
    STARTED("開始", false),
    SUCCESS("正常終了", true),
    WARNING("警告終了", true),
    ERROR("異常終了", true),
    CANCELLED("中止終了", true);

    private final String label;
    private final boolean terminal;

    TaskStatus(String label, boolean terminal) {
        this.label = label;
        this.terminal = terminal;
    }

    /** 画面表示用の日本語ラベル。 */
    public String label() {
        return label;
    }

    /** 終端(これ以上状態が進まない)ステータスかどうか。 */
    public boolean terminal() {
        return terminal;
    }
}
