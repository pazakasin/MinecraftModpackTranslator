package io.github.pazakasin.minecraft.modpack.translator.service.modpack;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import io.github.pazakasin.minecraft.modpack.translator.model.ModProcessingResult;
import io.github.pazakasin.minecraft.modpack.translator.service.TranslationService;
import io.github.pazakasin.minecraft.modpack.translator.service.callback.LogCallback;
import io.github.pazakasin.minecraft.modpack.translator.service.processor.CharacterCounter;
import io.github.pazakasin.minecraft.modpack.translator.service.processor.JarFileAnalyzer;
import io.github.pazakasin.minecraft.modpack.translator.service.processor.LanguageFileInfo;
import io.github.pazakasin.minecraft.modpack.translator.service.processor.LanguageFileWriter;
import io.github.pazakasin.minecraft.modpack.translator.service.processor.NamespaceUsageReport;

/**
 * Mod JARファイルの処理を担当するクラス。
 * JAR内の言語ファイル解析、翻訳、出力をnamespace単位で実行。
 */
public class ModJarProcessor {
	/** 翻訳サービス。 */
	private final TranslationService translationService;
	
	/** ログコールバック。 */
	private final LogCallback logger;
	
	/** JARファイルアナライザー。 */
	private final JarFileAnalyzer jarAnalyzer;
	
	/** ファイルライター。 */
	private final LanguageFileWriter fileWriter;
	
	/** 文字数カウンター。 */
	private final CharacterCounter charCounter;
	
	/**
	 * ModJarProcessorのコンストラクタ。
	 * @param translationService 翻訳サービス
	 * @param logger ログコールバック
	 * @param fileWriter ファイルライター
	 */
	public ModJarProcessor(TranslationService translationService, LogCallback logger,
			LanguageFileWriter fileWriter) {
		this.translationService = translationService;
		this.logger = logger;
		this.jarAnalyzer = new JarFileAnalyzer();
		this.fileWriter = fileWriter;
		this.charCounter = new CharacterCounter();
	}
	
	/**
	 * 単一のMod JARファイルを処理します。en_us.jsonを持つnamespaceごとに結果を1件返します。
	 * @param jarFile 処理対象JARファイル
	 * @param currentModNum 現在のMod番号
	 * @param totalMods 全Mod数
	 * @param report namespace集計（null可）
	 * @return 処理結果のリスト（en_us.jsonがない場合はスキップ結果1件）
	 * @throws Exception 処理エラー
	 */
	public List<ModProcessingResult> process(File jarFile, int currentModNum, int totalMods,
			NamespaceUsageReport report) throws Exception {
		List<ModProcessingResult> results = new ArrayList<ModProcessingResult>();
		String baseName = jarFile.getName().replace(".jar", "");
		List<LanguageFileInfo> infos = jarAnalyzer.analyze(jarFile);
		int enUsCount = JarFileAnalyzer.countEnUs(infos);
		
		if (enUsCount == 0) {
			ModProcessingResult result = new ModProcessingResult();
			result.modName = baseName;
			result.langFolderPath = infos.isEmpty() ? "見つかりません" : infos.get(0).langFolderPath;
			result.hasJaJp = !infos.isEmpty() && infos.get(0).hasJaJp;
			result.translationSuccess = false;
			results.add(result);
			return results;
		}
		
		for (LanguageFileInfo langInfo : infos) {
			if (langInfo.enUsContent == null) {
				continue;
			}
			ModProcessingResult result = processNamespace(jarFile, langInfo,
					JarFileAnalyzer.buildDisplayName(baseName, langInfo.modId, enUsCount));
			if (report != null) {
				report.add(jarFile.getName(), langInfo.modId, result.characterCount);
			}
			results.add(result);
		}
		return results;
	}
	
	/**
	 * 1つのnamespaceの言語ファイルを翻訳・出力します。
	 * @param jarFile 処理対象JARファイル（ログ用）
	 * @param langInfo namespaceの言語ファイル情報
	 * @param displayName 表示用Mod名
	 * @return 処理結果
	 */
	private ModProcessingResult processNamespace(File jarFile, LanguageFileInfo langInfo, String displayName) {
		ModProcessingResult result = new ModProcessingResult();
		result.modName = displayName;
		result.langFolderPath = langInfo.langFolderPath;
		result.hasEnUs = langInfo.hasEnUs;
		result.hasJaJp = langInfo.hasJaJp;
		result.characterCount = charCounter.countCharacters(langInfo.enUsContent);
		
		try {
			if (langInfo.jaJpContent != null) {
				fileWriter.writeLanguageFiles(langInfo.modId, langInfo.enUsContent, langInfo.jaJpContent);
				result.translationSuccess = true;
			} else {
				result.translated = true;
				String translatedContent = translationService.translateJsonFile(langInfo.enUsContent);
				fileWriter.writeLanguageFiles(langInfo.modId, langInfo.enUsContent, translatedContent);
				result.translationSuccess = true;
			}
		} catch (Exception e) {
			result.translationSuccess = false;
			result.errorException = e;
			if (result.translated) {
				// エラー時に処理中のファイル内容をログ出力
				logProcessingContent(jarFile.getName(), langInfo.modId, langInfo.enUsContent, e);
			}
		}
		return result;
	}
	
	/**
	 * 処理中のファイル内容をログ出力します。
	 * @param jarFileName JARファイル名
	 * @param modId Mod ID
	 * @param content 処理中のJSON内容
	 * @param e 発生した例外
	 */
	private void logProcessingContent(String jarFileName, String modId, String content, Exception e) {
		if (logger != null) {
			logger.onLog("");
			logger.onLog("=== エラー発生時の処理内容 ===");
			logger.onLog("JARファイル: " + jarFileName);
			logger.onLog("Mod ID: " + modId);
			logger.onLog("エラー: " + e.getMessage());
			
			// JSON内容の最初の部分を表示（500文字まで）
			if (content != null) {
				logger.onLog("処理中のJSON内容 (最初の500文字):");
				String preview = content.length() > 500 ? content.substring(0, 500) + "..." : content;
				logger.onLog(preview);
				logger.onLog("総文字数: " + content.length());
				
				// エントリー数をカウント
				try {
					com.google.gson.JsonObject json = new com.google.gson.Gson().fromJson(content, com.google.gson.JsonObject.class);
					logger.onLog("エントリー数: " + json.size());
				} catch (Exception parseError) {
					logger.onLog("JSONパースエラー: " + parseError.getMessage());
				}
			}
			logger.onLog("===");
			logger.onLog("");
		}
	}

}
