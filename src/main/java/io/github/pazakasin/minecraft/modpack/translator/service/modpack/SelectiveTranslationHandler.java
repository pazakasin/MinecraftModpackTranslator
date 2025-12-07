package io.github.pazakasin.minecraft.modpack.translator.service.modpack;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import io.github.pazakasin.minecraft.modpack.translator.model.FileType;
import io.github.pazakasin.minecraft.modpack.translator.model.ModProcessingResult;
import io.github.pazakasin.minecraft.modpack.translator.model.ProcessingState;
import io.github.pazakasin.minecraft.modpack.translator.model.QuestFileResult;
import io.github.pazakasin.minecraft.modpack.translator.model.QuestTranslationResult;
import io.github.pazakasin.minecraft.modpack.translator.model.TranslatableFile;
import io.github.pazakasin.minecraft.modpack.translator.service.callback.FileStateUpdateCallback;
import io.github.pazakasin.minecraft.modpack.translator.service.callback.LogCallback;
import io.github.pazakasin.minecraft.modpack.translator.service.quest.QuestFileProcessor;
import io.github.pazakasin.minecraft.modpack.translator.service.backup.BackupManager;
import io.github.pazakasin.minecraft.modpack.translator.service.processor.LanguageFileWriter;

/**
 * 選択されたファイルの翻訳処理を担当するクラス。
 * Mod言語ファイル、KubeJS、クエストファイルの選択的処理を実行。
 */
public class SelectiveTranslationHandler {
	/** ログコールバック。 */
	private final LogCallback logger;
	
	/** ファイル状態更新コールバック。 */
	private FileStateUpdateCallback fileStateCallback;
	
	/** Mod言語ファイル処理用プロセッサー。 */
	private final ModLanguageFileHandler modLangHandler;
	
	/** KubeJS処理用プロセッサー。 */
	private final KubeJSProcessor kubeJsProcessor;
	
	/** クエストファイル処理用プロセッサー。 */
	private final QuestFileProcessor questProcessor;
	
	/** 入力パス。 */
	private final String inputPath;
	
	/**
	 * SelectiveTranslationHandlerのコンストラクタ。
	 * @param logger ログコールバック
	 * @param modLangHandler Mod言語ファイルハンドラー
	 * @param kubeJsProcessor KubeJSプロセッサー
	 * @param questProcessor クエストファイルプロセッサー
	 * @param inputPath 入力パス
	 */
	public SelectiveTranslationHandler(LogCallback logger, ModLanguageFileHandler modLangHandler,
			KubeJSProcessor kubeJsProcessor, QuestFileProcessor questProcessor, String inputPath) {
		this.logger = logger;
		this.modLangHandler = modLangHandler;
		this.kubeJsProcessor = kubeJsProcessor;
		this.questProcessor = questProcessor;
		this.inputPath = inputPath;
	}
	
	/**
	 * ファイル状態更新コールバックを設定します。
	 * @param callback コールバック
	 */
	public void setFileStateCallback(FileStateUpdateCallback callback) {
		this.fileStateCallback = callback;
		modLangHandler.setFileStateCallback(callback);
		kubeJsProcessor.setFileStateCallback(callback);
	}
	
	/**
	 * 選択された翻訳対象ファイルのみを処理します。
	 * @param selectedFiles 選択された翻訳対象ファイルのリスト
	 * @return 各ファイルの処理結果リスト
	 * @throws Exception ファイルアクセスエラー等
	 */
	public List<ModProcessingResult> process(List<TranslatableFile> selectedFiles) throws Exception {
		List<ModProcessingResult> results = new ArrayList<ModProcessingResult>();
		
		if (selectedFiles == null || selectedFiles.isEmpty()) {
			log("選択されたファイルがありません。");
			return results;
		}
		
		log("=== 選択ファイル翻訳処理開始 ===");
		log("選択されたファイル数: " + selectedFiles.size());
		
		List<TranslatableFile> modLangFiles = new ArrayList<TranslatableFile>();
		List<TranslatableFile> kubeJsLangFiles = new ArrayList<TranslatableFile>();
		List<TranslatableFile> questLangFiles = new ArrayList<TranslatableFile>();
		List<TranslatableFile> questFiles = new ArrayList<TranslatableFile>();
		
		for (TranslatableFile file : selectedFiles) {
			switch (file.getFileType()) {
				case MOD_LANG_FILE:
					modLangFiles.add(file);
					break;
				case KUBEJS_LANG_FILE:
					kubeJsLangFiles.add(file);
					break;
				case QUEST_LANG_FILE:
					questLangFiles.add(file);
					break;
				case QUEST_FILE:
					questFiles.add(file);
					break;
			}
		}
		
		if (!modLangFiles.isEmpty()) {
			log("");
			log("=== Mod言語ファイル翻訳 ===");
			modLangHandler.process(modLangFiles, results);
		}
		
		if (!kubeJsLangFiles.isEmpty()) {
			log("");
			log("=== KubeJS言語ファイル翻訳 ===");
			kubeJsProcessor.process(kubeJsLangFiles, results);
		}
		
		if (!questLangFiles.isEmpty() || !questFiles.isEmpty()) {
			log("");
			log("=== クエストファイル翻訳 ===");
			processQuestFiles(questLangFiles, questFiles, results);
		}
		
		int translated = 0;
		int failed = 0;
		for (ModProcessingResult result : results) {
			if (result.translated && result.translationSuccess) {
				translated++;
			} else if (result.translated && !result.translationSuccess) {
				failed++;
			}
		}
		
		log("");
		log("=== 翻訳完了 ===");
		log("処理したファイル数: " + selectedFiles.size());
		log("翻訳成功: " + translated);
		log("翻訳失敗: " + failed);
		log("出力先: " + new File("output").getAbsolutePath());
		
		return results;
	}
	
	/**
	 * 選択されたクエストファイルを処理します。
	 * @param questLangFiles クエストLang Fileリスト
	 * @param questFiles Quest Fileリスト
	 * @param results 結果リスト
	 * @throws Exception 処理エラー
	 */
	private void processQuestFiles(List<TranslatableFile> questLangFiles,
			List<TranslatableFile> questFiles,
			List<ModProcessingResult> results) throws Exception {
		QuestTranslationResult questResult = new QuestTranslationResult();
		
		processQuestLangFiles(questLangFiles, questResult);
		processQuestContentFiles(questFiles, questResult);
		
		logProgress(" ");
		
		if (questResult.hasTranslation()) {
			ModProcessingResult questModResult = new ModProcessingResult();
			questModResult.modName = "FTB Quests";
			questModResult.langFolderPath = "config/ftbquests";
			questModResult.hasEnUs = questResult.hasLangFile;
			questModResult.hasJaJp = false;
			questModResult.translated = questResult.hasTranslation();
			questModResult.translationSuccess = questResult.isAllSuccess();
			questModResult.questResult = questResult;
			questModResult.characterCount = questResult.getTotalCharacterCount();
			
			results.add(questModResult);
			
			log("");
			log("=== FTB Quests翻訳完了 ===");
			log("合計翻訳文字数: " + questResult.getTotalCharacterCount() + "文字");
		}
	}
	
	/**
	 * クエストLang Fileを処理します。
	 * @param questLangFiles ファイルリスト
	 * @param questResult 翻訳結果
	 */
	private void processQuestLangFiles(List<TranslatableFile> questLangFiles, QuestTranslationResult questResult) {
		for (TranslatableFile file : questLangFiles) {
			questResult.hasLangFile = true;
			
			if (file.isHasExistingJaJp()) {
				file.setProcessingState(ProcessingState.EXISTING);
				file.setResultMessage("既存");
			} else {
				file.setProcessingState(ProcessingState.TRANSLATING);
				file.setResultMessage("翻訳中");
			}
			updateFileState(file);
			
			try {
				File sourceFile = new File(file.getSourceFilePath());
				
				File existingJaJpFile = null;
				if (file.isHasExistingJaJp() && file.getExistingJaJpContent() != null) {
					File langDir = sourceFile.getParentFile();
					existingJaJpFile = new File(langDir, "ja_jp.snbt");
				}
				
				final TranslatableFile currentFile = file;
				QuestFileResult fileResult = questProcessor.processSingleLangFile(
						sourceFile, existingJaJpFile, file.getCharacterCount(), new io.github.pazakasin.minecraft.modpack.translator.service.callback.ProgressCallback() {
							@Override
							public void onProgress(int current, int total) {
								currentFile.setProgress(current, total);
								updateFileState(currentFile);
							}
						});
				
				questResult.fileResults.add(fileResult);
				questResult.langFileTranslated = fileResult.translated;
				questResult.langFileSuccess = fileResult.success;
				questResult.langFileCharacterCount = file.getCharacterCount();
				
				if (fileResult.success) {
					if (fileResult.translated) {
						file.setProcessingState(ProcessingState.COMPLETED);
						file.setResultMessage("○");
					} else {
						file.setProcessingState(ProcessingState.EXISTING);
						file.setResultMessage("既存");
					}
				} else {
					file.setProcessingState(ProcessingState.FAILED);
					file.setResultMessage("×");
				}
				
				updateFileState(file);
				
				if (fileResult.success) {
					if (fileResult.translated) {
						log(String.format("[Quest Lang][翻訳] %s - 翻訳完了 (%d文字)",
								file.getModName(), file.getCharacterCount()));
					} else {
						log(String.format("[Quest Lang][既存] %s - 日本語ファイルをコピー",
								file.getModName()));
					}
				}
			} catch (Exception e) {
				file.setProcessingState(ProcessingState.FAILED);
				file.setResultMessage("×: " + e.getMessage());
				updateFileState(file);
				
				log(String.format("[Quest Lang][失敗] %s: %s",
						file.getModName(), e.getMessage()));
			}
		}
	}
	
	/**
	 * Quest本体ファイルを処理します。
	 * @param questFiles ファイルリスト
	 * @param questResult 翻訳結果
	 */
	private void processQuestContentFiles(List<TranslatableFile> questFiles, QuestTranslationResult questResult) {
		questResult.questFileCount = questFiles.size();
		
		for (int i = 0; i < questFiles.size(); i++) {
			TranslatableFile file = questFiles.get(i);
			
			file.setProcessingState(ProcessingState.TRANSLATING);
			file.setResultMessage("翻訳中");
			updateFileState(file);
			
			logProgress(String.format("[Quest %d/%d] 翻訳中: %s",
					i + 1, questFiles.size(), file.getModName()));
			
			try {
				File sourceFile = new File(file.getSourceFilePath());
				final TranslatableFile currentFile = file;
				QuestFileResult fileResult = questProcessor.processSingleQuestFile(
						sourceFile, file.getCharacterCount(), new io.github.pazakasin.minecraft.modpack.translator.service.callback.ProgressCallback() {
							@Override
							public void onProgress(int current, int total) {
								currentFile.setProgress(current, total);
								updateFileState(currentFile);
							}
						});
				
				questResult.fileResults.add(fileResult);
				questResult.questFileTranslated++;
				
				if (fileResult.success) {
					questResult.questFileSuccess++;
					file.setProcessingState(ProcessingState.COMPLETED);
					file.setResultMessage("○");
				} else {
					file.setProcessingState(ProcessingState.FAILED);
					file.setResultMessage("×");
				}
				
				updateFileState(file);
				questResult.questFileCharacterCount += file.getCharacterCount();
			} catch (Exception e) {
				file.setProcessingState(ProcessingState.FAILED);
				file.setResultMessage("×: " + e.getMessage());
				updateFileState(file);
				
				log(String.format("[Quest %d/%d][失敗] %s: %s",
						i + 1, questFiles.size(), file.getModName(), e.getMessage()));
			}
		}
	}
	
	/**
	 * ファイルの状態を更新し、コールバックを呼び出します。
	 * @param file 対象ファイル
	 */
	private void updateFileState(TranslatableFile file) {
		if (fileStateCallback != null) {
			fileStateCallback.onFileStateUpdate(file);
		}
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
