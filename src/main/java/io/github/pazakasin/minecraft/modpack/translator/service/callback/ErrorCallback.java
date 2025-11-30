package io.github.pazakasin.minecraft.modpack.translator.service.callback;

/**
 * エラー発生時に呼ばれるコールバックインターフェース。
 */
public interface ErrorCallback {
    /**
     * エラー発生を通知します。
     * @param exception 発生した例外
     */
    void onError(Exception exception);
}
