/**
 * Controller層のコールバックインターフェース。
 * GUI操作やバックグラウンド処理の完了通知、
 * エラーハンドリングなどに使用される。
 * 
 * <h2>主要インターフェース</h2>
 * <ul>
 *   <li>{@link io.github.pazakasin.minecraft.modpack.translator.controller.callback.AnalysisCompletionCallback} - 解析完了通知</li>
 *   <li>{@link io.github.pazakasin.minecraft.modpack.translator.controller.callback.TranslationCompletionCallback} - 翻訳完了通知</li>
 *   <li>{@link io.github.pazakasin.minecraft.modpack.translator.controller.callback.ButtonStateCallback} - ボタン状態変更</li>
 * </ul>
 */
package io.github.pazakasin.minecraft.modpack.translator.controller.callback;
