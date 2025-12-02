package io.github.pazakasin.minecraft.modpack.translator.service.callback;

import io.github.pazakasin.minecraft.modpack.translator.model.TranslatableFile;

/**
 * ファイルの処理状態が更新されたときに呼ばれるコールバックインタフェース。
 */
public interface FileStateUpdateCallback {
    /**
     * ファイルの処理状態が更新されたときに呼ばれます。
     * @param file 状態が更新されたファイル
     */
    void onFileStateUpdate(TranslatableFile file);
}
