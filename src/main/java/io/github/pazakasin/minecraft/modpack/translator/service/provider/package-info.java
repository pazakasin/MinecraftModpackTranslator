/**
 * 翻訳プロバイダー実装。
 * Google、DeepL、ChatGPT、Claudeなど複数の翻訳APIを統合。
 * 
 * <h2>主要インターフェース</h2>
 * <ul>
 *   <li>{@link io.github.pazakasin.minecraft.modpack.translator.service.provider.TranslationProvider} - 翻訳プロバイダー共通インターフェース</li>
 * </ul>
 * 
 * <h2>実装クラス</h2>
 * <ul>
 *   <li>{@link io.github.pazakasin.minecraft.modpack.translator.service.provider.GoogleTranslationProvider} - Google翻訳</li>
 *   <li>{@link io.github.pazakasin.minecraft.modpack.translator.service.provider.DeepLTranslationProvider} - DeepL翻訳</li>
 *   <li>{@link io.github.pazakasin.minecraft.modpack.translator.service.provider.ChatGPTTranslationProvider} - ChatGPT翻訳</li>
 *   <li>{@link io.github.pazakasin.minecraft.modpack.translator.service.provider.ClaudeTranslationProvider} - Claude翻訳</li>
 * </ul>
 */
package io.github.pazakasin.minecraft.modpack.translator.service.provider;
