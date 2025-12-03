package io.github.pazakasin.minecraft.modpack.translator.service.modpack;

import java.io.File;

import io.github.pazakasin.minecraft.modpack.translator.model.ModProcessingResult;
import io.github.pazakasin.minecraft.modpack.translator.service.ProgressCallback;
import io.github.pazakasin.minecraft.modpack.translator.service.TranslationService;
import io.github.pazakasin.minecraft.modpack.translator.service.callback.LogCallback;
import io.github.pazakasin.minecraft.modpack.translator.service.processor.CharacterCounter;
import io.github.pazakasin.minecraft.modpack.translator.service.processor.JarFileAnalyzer;
import io.github.pazakasin.minecraft.modpack.translator.service.processor.LanguageFileWriter;

/**
 * Mod JARファイルの処理を担当するクラス。
 * JAR内の言語ファイル解析、翻訳、出力を実行。
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
	 * 単一のMod JARファイルを処理します。
	 * @param jarFile 処理対象JARファイル
	 * @param currentModNum 現在のMod番号
	 * @param totalMods 全Mod数
	 * @return 処理結果
	 * @throws Exception 処理エラー
	 */
	public ModProcessingResult process(File jarFile, int currentModNum, int totalMods) throws Exception {
		ModProcessingResult result = new ModProcessingResult();
		result.modName = jarFile.getName().replace(".jar", "");
		
		JarFileAnalyzer.LanguageFileInfo langInfo = jarAnalyzer.analyze(jarFile);
		
		result.langFolderPath = langInfo.langFolderPath;
		result.hasEnUs = langInfo.hasEnUs;
		result.hasJaJp = langInfo.hasJaJp;
		
		if (langInfo.modId == null || langInfo.enUsContent == null) {
			result.translationSuccess = false;
			return result;
		}
		
		result.characterCount = charCounter.countCharacters(langInfo.enUsContent);
		
		if (langInfo.jaJpContent != null) {
			fileWriter.writeLanguageFiles(langInfo.modId, langInfo.enUsContent, langInfo.jaJpContent);
			result.translationSuccess = true;
		} else {
			try {
				String translatedContent = translateWithProgress(
						langInfo.enUsContent, currentModNum, totalMods);
				
				fileWriter.writeLanguageFiles(langInfo.modId, langInfo.enUsContent, translatedContent);
				result.translated = true;
				result.translationSuccess = true;
				
				logProgress(" ");
			} catch (Exception e) {
				result.translated = true;
				result.translationSuccess = false;
				logProgress(" ");
				throw e;
			}
		}
		
		return result;
	}
	
	/**
	 * 進捗通知付きで翻訳を実行します。
	 * @param content 翻訳対象コンテンツ
	 * @param currentMod 現在のMod番号
	 * @param totalMods 全Mod数
	 * @return 翻訳済みコンテンツ
	 * @throws Exception 翻訳エラー
	 */
	private String translateWithProgress(String content, final int currentMod, final int totalMods) throws Exception {
		return translationService.translateJsonFile(content, new ProgressCallback() {
			@Override
			public void onProgress(int current, int total) {
				logProgress(String.format("[%d/%d] 翻訳中: %d/%d エントリー",
						currentMod, totalMods, current, total));
			}
		});
	}
	
	/**
	 * 進捗ログメッセージを出力します。
	 * @param message ログメッセージ
	 */
	private void logProgress(String message) {
		if (logger != null) {
			logger.onLog("PROGRESS:" + message);
		}
	}
}
