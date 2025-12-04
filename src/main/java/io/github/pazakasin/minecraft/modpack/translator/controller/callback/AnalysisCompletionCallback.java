package io.github.pazakasin.minecraft.modpack.translator.controller.callback;

import java.util.List;

import io.github.pazakasin.minecraft.modpack.translator.model.TranslatableFile;

/**
 * 解析完了時のコールバック。
 */
public interface AnalysisCompletionCallback {
	/**
	 * 解析完了時に呼ばれます。
	 * @param files 解析結果のファイルリスト
	 */
	void onComplete(List<TranslatableFile> files);
}
