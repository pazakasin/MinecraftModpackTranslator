package io.github.pazakasin.minecraft.modpack.translator.controller.ui;

/**
 * エラー発生時のコールバック。
 */
public interface AnalysisErrorCallback {
	/**
	 * エラー発生時に呼ばれます。
	 * @param error 発生した例外
	 */
	void onError(Exception error);
}
