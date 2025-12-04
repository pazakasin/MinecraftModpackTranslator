package io.github.pazakasin.minecraft.modpack.translator.service.quest;

import java.io.File;
import java.util.List;

import io.github.pazakasin.minecraft.modpack.translator.model.QuestFileResult;
import io.github.pazakasin.minecraft.modpack.translator.model.QuestTranslationResult;
import io.github.pazakasin.minecraft.modpack.translator.service.TranslationService;
import io.github.pazakasin.minecraft.modpack.translator.service.backup.BackupManager;
import io.github.pazakasin.minecraft.modpack.translator.service.callback.LogCallback;
import io.github.pazakasin.minecraft.modpack.translator.service.quest.processor.LangFileProcessor;
import io.github.pazakasin.minecraft.modpack.translator.service.quest.processor.QuestFileContentProcessor;
import io.github.pazakasin.minecraft.modpack.translator.service.quest.util.QuestTranslationHelper;

/**
 * FTB Questsファイルの翻訳処理を統括するクラス。
 * ファイル検出、バックアップ、各種プロセッサーの呼び出しを管理。
 */
public class QuestFileProcessor {
	/** ログコールバック。 */
	private final LogCallback logger;
	
	/** 出力先ディレクトリ。 */
	private final File outputDir;
	
	/** クエストファイル検出器。 */
	private final QuestFileDetector detector;
	
	/** バックアップマネージャー。 */
	private final BackupManager backupManager;
	
	/** Lang File処理プロセッサー。 */
	private final LangFileProcessor langFileProcessor;
	
	/** Quest File本体処理プロセッサー。 */
	private final QuestFileContentProcessor questFileContentProcessor;
	
	/** 翻訳ヘルパー。 */
	private final QuestTranslationHelper helper;
	
	/**
	 * QuestFileProcessorのコンストラクタ。
	 * @param translationService 翻訳サービス
	 * @param logger ログコールバック
	 * @param outputDir 出力先ディレクトリ
	 */
	public QuestFileProcessor(TranslationService translationService,
			LogCallback logger, File outputDir) {
		this.logger = logger;
		this.outputDir = outputDir;
		this.detector = new QuestFileDetector();
		this.backupManager = new BackupManager();
		
		SNBTParser parser = new SNBTParser();
		LangFileSNBTExtractor extractor = new LangFileSNBTExtractor();
		
		this.helper = new QuestTranslationHelper(translationService, logger);
		this.langFileProcessor = new LangFileProcessor(parser, extractor, helper, logger, outputDir);
		this.questFileContentProcessor = new QuestFileContentProcessor(parser, helper, logger, outputDir);
	}
	
	/**
	 * ModPackディレクトリ内のクエストファイルを検出して翻訳します。
	 * @param modpackDir ModPackディレクトリ
	 * @return 翻訳結果
	 * @throws Exception 処理エラー
	 */
	public QuestTranslationResult process(File modpackDir) throws Exception {
		QuestTranslationResult result = new QuestTranslationResult();
		
		List<QuestFileInfo> files = detector.detectQuestFiles(modpackDir);
		
		if (files.isEmpty()) {
			log("FTB Questsファイルが見つかりませんでした。");
			return result;
		}
		
		log("=== FTB Quests翻訳処理開始 ===");
		log("検出されたファイル数: " + files.size());
		
		for (QuestFileInfo fileInfo : files) {
			if (fileInfo.getType() == QuestFileType.LANG_FILE) {
				processLangFile(fileInfo.getFile(), fileInfo.getJaJpFile(), result);
			} else {
				result.questFileCount++;
			}
		}
		
		int questFileIndex = 0;
		for (QuestFileInfo fileInfo : files) {
			if (fileInfo.getType() == QuestFileType.QUEST_FILE) {
				questFileIndex++;
				processQuestFile(fileInfo.getFile(), result, questFileIndex, result.questFileCount);
			}
		}
		
		logSummary(result);
		
		return result;
	}
	
	/**
	 * Lang Fileを処理します。
	 * @param langFile Lang File
	 * @param existingJaJpFile 既存のja_jp.snbtファイル
	 * @param result 処理結果
	 */
	private void processLangFile(File langFile, File existingJaJpFile, QuestTranslationResult result) {
		result.hasLangFile = true;
		
		try {
			QuestFileResult fileResult = langFileProcessor.process(langFile, existingJaJpFile);
			
			result.langFileTranslated = fileResult.translated;
			result.langFileSuccess = fileResult.success;
			result.langFileCharacterCount = fileResult.characterCount;
			result.fileResults.add(fileResult);
			
		} catch (Exception e) {
			helper.logError("Lang File翻訳エラー", langFile, e);
			result.langFileTranslated = true;
			result.langFileSuccess = false;
			
			QuestFileResult fileResult = QuestFileResult.createLangFileResult(
					langFile, null, true, false, result.langFileCharacterCount);
			result.fileResults.add(fileResult);
		}
	}
	
	/**
	 * Quest File本体を処理します。
	 * @param questFile Quest File
	 * @param result 処理結果
	 * @param currentIndex 現在のインデックス
	 * @param totalCount 合計数
	 */
	private void processQuestFile(File questFile, QuestTranslationResult result,
			int currentIndex, int totalCount) {
		try {
			QuestFileResult fileResult = questFileContentProcessor.process(
					questFile, currentIndex, totalCount);
			
			result.questFileCharacterCount += fileResult.characterCount;
			result.fileResults.add(fileResult);
			
			if (fileResult.translated) {
				result.questFileTranslated++;
				if (fileResult.success) {
					result.questFileSuccess++;
				}
			}
			
		} catch (Exception e) {
			helper.logError("クエストファイル翻訳エラー", questFile, e);
			result.questFileTranslated++;
			
			QuestFileResult fileResult = QuestFileResult.createQuestFileResult(
					questFile, null, true, false, 0);
			result.fileResults.add(fileResult);
		}
	}
	
	/**
	 * 個別のLang Fileを翻訳します（選択的処理用）。
	 * @param sourceFile 元のLang Fileパス
	 * @param existingJaJpFile 既存のja_jp.snbtファイル（なければnull）
	 * @param characterCount 文字数
	 * @return 処理結果
	 */
	public QuestFileResult processSingleLangFile(File sourceFile, File existingJaJpFile, int characterCount) {
		return langFileProcessor.process(sourceFile, existingJaJpFile);
	}
	
	/**
	 * 個別のQuest Fileを翻訳します（選択的処理用）。
	 * @param sourceFile 元のQuest Fileパス
	 * @param characterCount 文字数
	 * @return 処理結果
	 */
	public QuestFileResult processSingleQuestFile(File sourceFile, int characterCount) {
		return questFileContentProcessor.process(sourceFile, 1, 1);
	}
	
	/**
	 * バックアップを実行します（外部から呼び出し可能）。
	 * @param modpackDir ModPackディレクトリ
	 * @return バックアップ結果
	 */
	public BackupManager.BackupResult executeBackup(File modpackDir) {
		try {
			log("バックアップ実行中...");
			BackupManager.BackupResult backupResult = backupManager.backup(modpackDir);
			
			if (backupResult != null) {
				log("バックアップ完了: " + backupResult.fileCount + "ファイル");
				log("バックアップ先: " + backupResult.backupPath);
				return backupResult;
			}
		} catch (Exception e) {
			log("バックアップ失敗: " + e.getMessage());
			log("警告: バックアップなしで翻訳を続行します");
		}
		return null;
	}
	
	/**
	 * 処理結果のサマリーをログ出力します。
	 * @param result 翻訳結果
	 */
	private void logSummary(QuestTranslationResult result) {
		log("");
		log("=== FTB Quests翻訳完了 ===");
		
		if (result.hasLangFile) {
			String status = result.langFileSuccess ? "成功" : "失敗";
			log("Lang File: " + status + " (" + result.langFileCharacterCount + "文字)");
		}
		
		log("クエストファイル: " + result.questFileSuccess + "/" + result.questFileCount + "ファイル成功");
		log("合計翻訳文字数: " + result.getTotalCharacterCount() + "文字");
		log("出力先: " + new File("output").getAbsolutePath());
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
