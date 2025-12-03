package io.github.pazakasin.minecraft.modpack.translator.controller.handler;

/**
 * 翻訳ボタンの有効/無効の切り替えコールバック。
 */
public interface TranslateButtonCallback {
	/**
	 * 翻訳ボタンの有効/無効を設定します。
	 * @param enabled 有効にする場合true
	 */
	void setTranslateButtonEnabled(boolean enabled);
}
