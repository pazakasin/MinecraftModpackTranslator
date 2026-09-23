package io.github.pazakasin.minecraft.modpack.translator.service.modpack;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

	/** OpenLoader処理用プロセッサー。 */
	private final OpenLoaderProcessor openLoaderProcessor;

	/** Config（その他）処理用プロセッサー（OpenLoaderと同じ出力方式）。 */
	private final OpenLoaderProcessor configLangProcessor;

	/** クエストファイル処理用プロセッサー。 */
	private final QuestFileProcessor questProcessor;
	
	/** 入力パス。 */
	private final String inputPath;
	
	/**
	 * SelectiveTranslationHandlerのコンストラクタ。
	 * @param logger ログコールバック
	 * @param modLangHandler Mod言語ファイルハンドラー
	 * @param kubeJsProcessor KubeJSプロセッサー
	 * @param openLoaderProcessor OpenLoaderプロセッサー
	 * @param configLangProcessor Config（その他）プロセッサー
	 * @param questProcessor クエストファイルプロセッサー
	 * @param inputPath 入力パス
	 */
	public SelectiveTranslationHandler(LogCallback logger, ModLanguageFileHandler modLangHandler,
			KubeJSProcessor kubeJsProcessor, OpenLoaderProcessor openLoaderProcessor,
			OpenLoaderProcessor configLangProcessor,
			QuestFileProcessor questProcessor, String inputPath) {
		this.logger = logger;
		this.modLangHandler = modLangHandler;
		this.kubeJsProcessor = kubeJsProcessor;
		this.openLoaderProcessor = openLoaderProcessor;
		this.configLangProcessor = configLangProcessor;
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
		openLoaderProcessor.setFileStateCallback(callback);
		configLangProcessor.setFileStateCallback(callback);
	}
	
	/**
	 * 選択された翻訳対象ファイルのみを処理します。
	 * 表の表示順（selectedFilesの順序）で処理を実行します。
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
		
		Map<FileType, Integer> typeCounters = new HashMap<FileType, Integer>();
		Map<FileType, Integer> typeTotals = calculateTypeTotals(selectedFiles);
		Map<FileType, Boolean> typeHeaderPrinted = new HashMap<FileType, Boolean>();
		
		QuestTranslationResult questResult = new QuestTranslationResult();
		
		for (TranslatableFile file : selectedFiles) {
			FileType fileType = file.getFileType();
			
			// 非対応形式（検出のみ）のファイルは処理しない（UI上は選択不可だが念のため）
			if (!file.isTranslatable()) {
				log("[スキップ] 非対応形式のため処理しません: " + file.getLangFolderPath());
				continue;
			}
			
			if (!typeHeaderPrinted.getOrDefault(fileType, false)) {
				log("");
				printTypeHeader(fileType);
				typeHeaderPrinted.put(fileType, true);
			}
			
			int currentIndex = typeCounters.getOrDefault(fileType, 0) + 1;
			int totalCount = typeTotals.get(fileType);
			typeCounters.put(fileType, currentIndex);
			
			switch (fileType) {
				case MOD_LANG_FILE:
					processModLangFile(file, currentIndex, totalCount, results);
					break;
				case KUBEJS_LANG_FILE:
					processKubeJSLangFile(file, currentIndex, totalCount, results);
					break;
				case OPENLOADER_LANG_FILE:
					processOpenLoaderLangFile(file, currentIndex, totalCount, results);
					break;
				case CONFIG_LANG_FILE:
					processConfigLangFile(file, currentIndex, totalCount, results);
					break;
				case QUEST_LANG_FILE:
					processQuestLangFile(file, questResult);
					break;
				case QUEST_FILE:
					processQuestFile(file, currentIndex, totalCount, questResult);
					break;
			}
		}
		
		if (questResult.hasTranslation()) {
			logProgress(" ");
			
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
		
		if (questResult.hasTranslation()) {
			log("");
			log("=== FTB Quests翻訳完了 ===");
			log("合計翻訳文字数: " + questResult.getTotalCharacterCount() + "文字");
		}
		
		return results;
	}
	
	/**
	 * ファイルタイプごとの合計数を計算します。
	 * @param files ファイルリスト
	 * @return タイプごとの合計数マップ
	 */
	private Map<FileType, Integer> calculateTypeTotals(List<TranslatableFile> files) {
		Map<FileType, Integer> totals = new HashMap<FileType, Integer>();
		for (TranslatableFile file : files) {
			FileType type = file.getFileType();
			totals.put(type, totals.getOrDefault(type, 0) + 1);
		}
		return totals;
	}
	
	/**
	 * ファイルタイプごとのヘッダーを出力します。
	 * @param fileType ファイルタイプ
	 */
	private void printTypeHeader(FileType fileType) {
		switch (fileType) {
			case MOD_LANG_FILE:
				log("=== Mod言語ファイル翻訳 ===");
				break;
			case KUBEJS_LANG_FILE:
				log("=== KubeJS言語ファイル翻訳 ===");
				break;
			case OPENLOADER_LANG_FILE:
				log("=== OpenLoader言語ファイル翻訳 ===");
				break;
			case CONFIG_LANG_FILE:
				log("=== Config（その他）言語ファイル翻訳 ===");
				break;
			case QUEST_LANG_FILE:
				log("=== Quest言語ファイル翻訳 ===");
				break;
			case QUEST_FILE:
				log("=== Questファイル翻訳 ===");
				break;
		}
	}
	
	/**
	 * Mod言語ファイルを処理します。
	 * @param file ファイル
	 * @param currentIndex 現在のインデックス
	 * @param totalCount 合計数
	 * @param results 結果リスト
	 */
	private void processModLangFile(TranslatableFile file, int currentIndex, int totalCount,
			List<ModProcessingResult> results) {
		List<TranslatableFile> singleFileList = new ArrayList<TranslatableFile>();
		singleFileList.add(file);
		
		try {
			modLangHandler.processSingleFile(file, currentIndex, totalCount, results);
		} catch (Exception e) {
			log(String.format("[Mod %d/%d][失敗] %s: %s",
					currentIndex, totalCount, file.getModName(), e.getMessage()));
			logStackTrace(e);
		}
	}
	
	/**
	 * KubeJS言語ファイルを処理します。
	 * @param file ファイル
	 * @param currentIndex 現在のインデックス
	 * @param totalCount 合計数
	 * @param results 結果リスト
	 */
	private void processKubeJSLangFile(TranslatableFile file, int currentIndex, int totalCount,
			List<ModProcessingResult> results) {
		try {
			kubeJsProcessor.processSingleFile(file, currentIndex, totalCount, results);
		} catch (Exception e) {
			log(String.format("[KubeJS %d/%d][失敗] %s: %s",
					currentIndex, totalCount, file.getFileId(), e.getMessage()));
			logStackTrace(e);
		}
	}
	
	/**
	 * OpenLoader言語ファイルを処理します。
	 * @param file ファイル
	 * @param currentIndex 現在のインデックス
	 * @param totalCount 合計数
	 * @param results 結果リスト
	 */
	private void processOpenLoaderLangFile(TranslatableFile file, int currentIndex, int totalCount,
			List<ModProcessingResult> results) {
		try {
			openLoaderProcessor.processSingleFile(file, currentIndex, totalCount, results);
		} catch (Exception e) {
			log(String.format("[OpenLoader %d/%d][失敗] %s: %s",
					currentIndex, totalCount, file.getFileId(), e.getMessage()));
			logStackTrace(e);
		}
	}

	/**
	 * Config（その他）の言語ファイルを処理します。
	 * @param file ファイル
	 * @param currentIndex 現在のインデックス
	 * @param totalCount 合計数
	 * @param results 結果リスト
	 */
	private void processConfigLangFile(TranslatableFile file, int currentIndex, int totalCount,
			List<ModProcessingResult> results) {
		try {
			configLangProcessor.processSingleFile(file, currentIndex, totalCount, results);
		} catch (Exception e) {
			log(String.format("[Config %d/%d][失敗] %s: %s",
					currentIndex, totalCount, file.getFileId(), e.getMessage()));
			logStackTrace(e);
		}
	}

	/**
	 * クエスト言語ファイルを処理します。
	 * @param file ファイル
	 * @param questResult クエスト翻訳結果
	 */
	private void processQuestLangFile(TranslatableFile file, QuestTranslationResult questResult) {
		questResult.hasLangFile = true;
		
		// 既存の日本語ファイルがある場合は選択不可のため通常は到達しないが、念のため出力せずに終了
		if (file.isHasExistingJaJp()) {
			file.setProcessingState(ProcessingState.EXISTING);
			file.setResultMessage(ProcessingState.EXISTING.getDisplayName());
			updateFileState(file);
			log(String.format("[Quest Lang][既存] %s - 既存の日本語ファイルがあるため出力しません",
					file.getModName()));
			return;
		}
		
		file.setProcessingState(ProcessingState.TRANSLATING);
		file.setResultMessage(ProcessingState.TRANSLATING.getDisplayName());
		updateFileState(file);
		
		try {
			File sourceFile = new File(file.getSourceFilePath());
			
			final TranslatableFile currentFile = file;
			QuestFileResult fileResult = questProcessor.processSingleLangFile(
					sourceFile, null, file.getCharacterCount(),
					new io.github.pazakasin.minecraft.modpack.translator.service.callback.ProgressCallback() {
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
			
			ProcessingState state = resolveQuestState(fileResult);
			file.setProcessingState(state);
			file.setResultMessage(state.getDisplayName());
			updateFileState(file);
			
			if (state == ProcessingState.COMPLETED) {
				log(String.format("[Quest Lang][翻訳] %s - 翻訳完了 (%d文字)",
						file.getModName(), file.getCharacterCount()));
			} else if (state == ProcessingState.UNCHANGED) {
				log(String.format("[Quest Lang][変更なし] %s - 翻訳結果が原文と同一のため出力しません",
						file.getModName()));
			} else if (state == ProcessingState.NO_TARGET) {
				log(String.format("[Quest Lang][スキップ] %s - 翻訳対象テキストがないため出力しません",
						file.getModName()));
			}
		} catch (Exception e) {
			file.setProcessingState(ProcessingState.FAILED);
			file.setResultMessage(ProcessingState.FAILED.getDisplayName() + ": " + e.getMessage());
			updateFileState(file);
			
			log(String.format("[Quest Lang][失敗] %s: %s",
					file.getModName(), e.getMessage()));
			logStackTrace(e);
		}
	}
	
	/**
	 * Questファイルを処理します。
	 * @param file ファイル
	 * @param currentIndex 現在のインデックス
	 * @param totalCount 合計数
	 * @param questResult クエスト翻訳結果
	 */
	private void processQuestFile(TranslatableFile file, int currentIndex, int totalCount,
			QuestTranslationResult questResult) {
		questResult.questFileCount++;
		
		file.setProcessingState(ProcessingState.TRANSLATING);
		file.setResultMessage(ProcessingState.TRANSLATING.getDisplayName());
		updateFileState(file);
		
		logProgress(String.format("[Quest %d/%d] 翻訳中: %s",
				currentIndex, totalCount, file.getModName()));
		
		try {
			File sourceFile = new File(file.getSourceFilePath());
			final TranslatableFile currentFile = file;
			QuestFileResult fileResult = questProcessor.processSingleQuestFile(
					sourceFile, file.getCharacterCount(), currentIndex, totalCount,
					new io.github.pazakasin.minecraft.modpack.translator.service.callback.ProgressCallback() {
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
			}
			ProcessingState state = resolveQuestState(fileResult);
			file.setProcessingState(state);
			file.setResultMessage(state.getDisplayName());
			
			updateFileState(file);
			questResult.questFileCharacterCount += file.getCharacterCount();
		} catch (Exception e) {
			file.setProcessingState(ProcessingState.FAILED);
			file.setResultMessage("×: " + e.getMessage());
			updateFileState(file);
			
			log(String.format("[Quest %d/%d][失敗] %s: %s",
					currentIndex, totalCount, file.getModName(), e.getMessage()));
			logStackTrace(e);
		}
	}
	
	/**
	 * クエスト処理結果から表示する処理状態を決定します。
	 * @param fileResult クエストファイルの処理結果
	 * @return 処理状態（完了・変更なし・対象なし・失敗）
	 */
	private ProcessingState resolveQuestState(QuestFileResult fileResult) {
		if (!fileResult.success) {
			return ProcessingState.FAILED;
		}
		if (!fileResult.translated) {
			return ProcessingState.NO_TARGET;
		}
		return fileResult.unchanged ? ProcessingState.UNCHANGED : ProcessingState.COMPLETED;
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
	 * スタックトレースをログ出力します。
	 * @param e 例外オブジェクト
	 */
	private void logStackTrace(Exception e) {
		if (logger != null) {
			java.io.StringWriter sw = new java.io.StringWriter();
			java.io.PrintWriter pw = new java.io.PrintWriter(sw);
			e.printStackTrace(pw);
			logger.onLog(sw.toString());
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
