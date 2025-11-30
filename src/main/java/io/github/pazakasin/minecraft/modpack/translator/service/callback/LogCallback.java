package io.github.pazakasin.minecraft.modpack.translator.service.callback;

/**
 * ログメッセージを受け取るコールバックインターフェース。
 */
public interface LogCallback {
    /**
     * ログメッセージを受け取ります。
     * @param message ログメッセージ
     */
    void onLog(String message);
}
