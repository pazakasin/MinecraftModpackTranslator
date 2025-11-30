package io.github.pazakasin.minecraft.modpack.translator.service.callback;

import io.github.pazakasin.minecraft.modpack.translator.model.ModProcessingResult;
import java.util.List;

/**
 * 処理完了時に呼ばれるコールバックインターフェース。
 */
public interface CompletionCallback {
    /**
     * 処理完了を通知します。
     * @param results 処理結果のリスト
     */
    void onComplete(List<ModProcessingResult> results);
}
