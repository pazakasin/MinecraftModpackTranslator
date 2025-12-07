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
			log(String.format("[Quest %d/%d] 処理開始: %s",
					currentIndex, totalCount, questFile.getName()));
			
			Map<String, String> texts = parser.extractTranslatableTexts(questFile);
			
			// デバッグログ（bees.snbtの場合のみ）
			if (questFile.getName().equals("bees.snbt")) {
				log("=== デバッグ: 抽出されたテキスト（全て） ===");
				for (Map.Entry<String, String> entry : texts.entrySet()) {
					String preview = entry.getValue();
					if (preview.length() > 50) {
						preview = preview.substring(0, 50) + "...";
					}
					preview = preview.replace("\n", "\\n");
					log(entry.getKey() + ": " + preview);
				}
				log("合計: " + texts.size() + " 個");
				log("====================================");
			}
			
			File relativePath = getRelativePath(questFile);
			File outputBase = outputDir.getParentFile();
			File outputFile = new File(outputBase, relativePath.getPath());
			outputFile.getParentFile().mkdirs();
			
			if (texts.isEmpty()) {
				log("翻訳対象テキストなし - ファイルをコピー");
				Files.copy(questFile.toPath(), outputFile.toPath(),
						java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				return QuestFileResult.createQuestFileResult(
						questFile, outputFile, false, true, 0);
			}
			
			int charCount = 0;
			for (String value : texts.values()) {
				charCount += value.length();
			}
			
			log(String.format("翻訳対象: %d個 (%d文字)", texts.size(), charCount));
			
			Map<String, String> translations = helper.translateQuestFileTexts(texts, progressCallback);
			
			parser.applyTranslations(questFile, outputFile, translations);
			
			log("翻訳完了: " + outputFile.getAbsolutePath());
			
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
			logger.onLog("[Quest] " + message);
		}
	}
}
