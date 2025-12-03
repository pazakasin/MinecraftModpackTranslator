package io.github.pazakasin.minecraft.modpack.translator.controller.handler;

import java.util.List;

import io.github.pazakasin.minecraft.modpack.translator.model.TranslatableFile;

/**
 * 解析済みファイルリストを取得するコールバック。
 */
public interface AnalyzedFilesCallback {
	/**
	 * 解析済みファイルリストを取得します。
	 * @return 解析済みファイルリスト
	 */
	List<TranslatableFile> getAnalyzedFiles();
}
