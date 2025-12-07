package io.github.pazakasin.minecraft.modpack.translator.service.quest.processor;

import java.io.File;
import java.nio.file.Files;
import java.util.Map;

import io.github.pazakasin.minecraft.modpack.translator.model.QuestFileResult;
import io.github.pazakasin.minecraft.modpack.translator.service.quest.SNBTParser;
import io.github.pazakasin.minecraft.modpack.translator.service.quest.util.QuestTranslationHelper;
import io.github.pazakasin.minecraft.modpack.translator.service.callback.LogCallback;

/**
 * Quest File本体の処理を担当するクラス。
 * 正規表現ベースでテキスト抽出、翻訳適用を実行。
 */
public class QuestFileContentProcessor {
	/** SNBTパーサー。 */
	private final SNBTParser parser;
	
	/** 翻訳ヘルパー。 */
	private final QuestTranslationHelper helper;
	
	/** ログコールバック。 */
	private final LogCallback logger;
	
	/** 出力先ディレクトリ。 */
	private final File outputDir;
	
	/**
	 * QuestFileContentProcessorのコンストラクタ。
	 * @param parser SNBTパーサー
	 * @param helper 翻訳ヘルパー
	 * @param logger ログコールバック
	 * @param outputDir 出力先ディレクトリ
	 */
	public QuestFileContentProcessor(SNBTParser parser, QuestTranslationHelper helper,
			LogCallback logger, File outputDir) {
		this.parser = parser;
		this.helper = helper;
		this.logger = logger;
		this.outputDir = outputDir;
	}
	
	/**
	 * Quest Fileを処理します。
	 * @param questFile 元のQuest File
	 * @param currentIndex 現在のインデックス
	 * @param totalCount 合計数
	 * @return 処理結果
	 */
	public QuestFileResult process(File questFile, int currentIndex, int totalCount) {
		return process(questFile, currentIndex, totalCount, null);
	}
	
	/**
	 * Quest Fileを処理します。
	 * @param questFile 元のQuest File
	 * @param currentIndex 現在のインデックス
	 * @param totalCount 合計数
	 * @param progressCallback 進捗コールバック
	 * @return 処理結果
	 */
	public QuestFileResult process(File questFile, int currentIndex, int totalCount, 
			io.github.pazakasin.minecraft.modpack.translator.service.callback.ProgressCallback progressCallback) {
		try {
			// ログ出力を削除（開始メッセージ不要）
			
			Map<String, String> texts = parser.extractTranslatableTexts(questFile);
			
			File relativePath = getRelativePath(questFile);
			File outputBase = outputDir.getParentFile();
			File outputFile = new File(outputBase, relativePath.getPath());
			outputFile.getParentFile().mkdirs();
			
			if (texts.isEmpty()) {
				// 翻訳対象なしの場合もログ出力
				log(String.format("[Quest %d/%d][スキップ] %s - 翻訳対象テキストなし",
						currentIndex, totalCount, questFile.getName()));
				Files.copy(questFile.toPath(), outputFile.toPath(),
						java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				return QuestFileResult.createQuestFileResult(
						questFile, outputFile, false, true, 0);
			}
			
			int charCount = 0;
			for (String value : texts.values()) {
				charCount += value.length();
			}
			
			// ログ出力を削除（状態列で表示）
			
			Map<String, String> translations = helper.translateQuestFileTexts(texts, progressCallback);
			
			parser.applyTranslations(questFile, outputFile, translations);
			
			// Mod言語ファイル形式に合わせたログ
			log(String.format("[Quest %d/%d][翻訳] %s - 翻訳完了 (%d文字)",
					currentIndex, totalCount, questFile.getName(), charCount));
			
			return QuestFileResult.createQuestFileResult(
					questFile, outputFile, true, true, charCount);
			
		} catch (Exception e) {
			helper.logError("クエストファイル翻訳エラー", questFile, e);
			return QuestFileResult.createQuestFileResult(
					questFile, null, true, false, 0);
		}
	}
	
	/**
	 * クエストファイルの相対パスを取得します。
	 * @param questFile クエストファイル
	 * @return 相対パス
	 */
	private File getRelativePath(File questFile) {
		String path = questFile.getAbsolutePath();
		int configIndex = path.indexOf("config");
		
		if (configIndex != -1) {
			return new File(path.substring(configIndex));
		}
		
		return new File("config/ftbquests/quests/" + questFile.getName());
	}
	
	/**
	 * ログメッセージを出力します。
	 * @param message ログメッセージ
	 */
	private void log(String message) {
		if (logger != null) {
			logger.onLog(message);
		}
	}
}
