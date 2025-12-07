package io.github.pazakasin.minecraft.modpack.translator.service.callback;

import io.github.pazakasin.minecraft.modpack.translator.model.TranslatableFile;

/**
 * 翻訳処理の進捗を通知するコールバックインターフェース。
 * 翻訳サービスは処理の進行状況をこのインターフェースを通じて通知する。
 */
public interface ProgressCallback {
    
    /**
     * 進捗状況を通知します。
     * @param current 現在の処理済み項目数
     * @param total 全体の項目数
     */
    void onProgress(int current, int total);
    
    /**
     * ファイル情報付きで進捗状況を通知します。
     * @param file 処理中のファイル
     * @param current 現在の処理済み項目数
     * @param total 全体の項目数
     */
    default void onProgressWithFile(TranslatableFile file, int current, int total) {
        onProgress(current, total);
    }
}
