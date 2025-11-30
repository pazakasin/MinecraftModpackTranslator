package io.github.pazakasin.minecraft.modpack.translator.service.callback;

/**
 * 処理進捗の更新を受け取るコールバックインターフェース。
 */
public interface ProgressUpdateCallback {
    /**
     * 進捗更新を受け取ります。
     * @param progress 進捗値
     */
    void onProgressUpdate(int progress);
}
