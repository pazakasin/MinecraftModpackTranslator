package io.github.pazakasin.minecraft.modpack.translator.controller.ui;

import java.util.List;

import io.github.pazakasin.minecraft.modpack.translator.model.ModProcessingResult;

/**
 * 翻訳完了時のコールバック。
 */
public interface TranslationCompletionCallback {
	/**
	 * 翻訳完了時に呼ばれます。
	 * @param results 翻訳結果のリスト
	 */
	void onComplete(List<ModProcessingResult> results);
}
