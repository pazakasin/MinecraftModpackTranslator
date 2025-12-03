package io.github.pazakasin.minecraft.modpack.translator.controller.ui;

/**
 * 翻訳エラー発生時のコールバック。
 */
public interface TranslationErrorCallback {
	/**
	 * エラー発生時に呼ばれます。
	 * @param error 発生した例外
	 */
	void onError(Exception error);
}
