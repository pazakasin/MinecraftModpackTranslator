package io.github.pazakasin.minecraft.modpack.translator.controller.callback;

import java.util.List;

import io.github.pazakasin.minecraft.modpack.translator.model.TranslatableFile;

/**
 * 解析結果を受け取るコールバック。
 */
public interface AnalysisResultCallback {
	/**
	 * 解析結果を設定します。
	 * @param files 解析されたファイルリスト
	 */
	void setAnalyzedFiles(List<TranslatableFile> files);
}
