package io.github.pazakasin.minecraft.modpack.translator.controller.callback;

/**
 * ボタン有効/無効の切り替えコールバック。
 */
public interface ButtonStateCallback {
	/**
	 * ボタンの有効/無効を設定します。
	 * @param enabled 有効にする場合true
	 */
	void setButtonsEnabled(boolean enabled);
}
