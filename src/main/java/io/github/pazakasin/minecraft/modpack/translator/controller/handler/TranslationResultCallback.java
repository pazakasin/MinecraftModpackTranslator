package io.github.pazakasin.minecraft.modpack.translator.controller.handler;

import java.util.List;

import io.github.pazakasin.minecraft.modpack.translator.model.ModProcessingResult;

/**
 * 翻訳結果を受け取るコールバック。
 */
public interface TranslationResultCallback {
	/**
	 * 翻訳結果を設定します。
	 * @param results 翻訳結果リスト
	 */
	void setProcessingResults(List<ModProcessingResult> results);
}
