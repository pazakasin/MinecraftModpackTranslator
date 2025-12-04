/**
 * サービス層のコールバックインターフェース。
 * ログ出力、進捗更新、完了通知などに使用。
 * 
 * <h2>主要インターフェース</h2>
 * <ul>
 *   <li>{@link io.github.pazakasin.minecraft.modpack.translator.service.callback.LogCallback} - ログ出力</li>
 *   <li>{@link io.github.pazakasin.minecraft.modpack.translator.service.callback.ProgressCallback} - 翻訳進捗通知</li>
 *   <li>{@link io.github.pazakasin.minecraft.modpack.translator.service.callback.ProgressUpdateCallback} - 進捗更新</li>
 *   <li>{@link io.github.pazakasin.minecraft.modpack.translator.service.callback.CompletionCallback} - 完了通知</li>
 *   <li>{@link io.github.pazakasin.minecraft.modpack.translator.service.callback.ErrorCallback} - エラー通知</li>
 *   <li>{@link io.github.pazakasin.minecraft.modpack.translator.service.callback.FileStateUpdateCallback} - ファイル状態更新</li>
 * </ul>
 */
package io.github.pazakasin.minecraft.modpack.translator.service.callback;
