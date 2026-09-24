/**
 * 汎用プロセッサー機能。
 * JAR解析、ファイル書き込み、文字数カウントなどの共通処理を提供。
 * 
 * <h2>主要クラス</h2>
 * <ul>
 *   <li>{@link io.github.pazakasin.minecraft.modpack.translator.service.processor.JarFileAnalyzer} - JARファイル解析</li>
 *   <li>{@link io.github.pazakasin.minecraft.modpack.translator.service.processor.LanguageFileInfo} - namespace単位の言語ファイル情報</li>
 *   <li>{@link io.github.pazakasin.minecraft.modpack.translator.service.processor.LanguageFileWriter} - 言語ファイル書き込み（同一namespaceはマージ）</li>
 *   <li>{@link io.github.pazakasin.minecraft.modpack.translator.service.processor.JsonLangMerger} - 言語JSONのキー単位マージ</li>
 *   <li>{@link io.github.pazakasin.minecraft.modpack.translator.service.processor.NamespaceUsageReport} - namespace検出レポート</li>
 *   <li>{@link io.github.pazakasin.minecraft.modpack.translator.service.processor.CharacterCounter} - 文字数カウント</li>
 * </ul>
 */
package io.github.pazakasin.minecraft.modpack.translator.service.processor;
