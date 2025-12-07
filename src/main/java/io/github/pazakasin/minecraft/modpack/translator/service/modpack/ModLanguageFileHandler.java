package io.github.pazakasin.minecraft.modpack.translator.service.modpack;

import java.util.List;

import io.github.pazakasin.minecraft.modpack.translator.model.ModProcessingResult;
import io.github.pazakasin.minecraft.modpack.translator.model.ProcessingState;
import io.github.pazakasin.minecraft.modpack.translator.model.TranslatableFile;
import io.github.pazakasin.minecraft.modpack.translator.service.TranslationService;
import io.github.pazakasin.minecraft.modpack.translator.service.callback.FileStateUpdateCallback;
import io.github.pazakasin.minecraft.modpack.translator.service.callback.LogCallback;
import io.github.pazakasin.minecraft.modpack.translator.service.callback.ProgressCallback;
import io.github.pazakasin.minecraft.modpack.translator.service.processor.LanguageFileWriter;

/**
 * Mod言語ファイルの処理を担当するクラス。
 * 既存ファイルのコピーまたは翻訳を実行。
 */
public class ModLanguageFileHandler {
	/** 翻訳サービス。 */
	private final TranslationService translationService;
	
	/** ログコールバック。 */
	private final LogCallback logger;
	
	/** ファイルライター。 */
	private final LanguageFileWriter fileWriter;
	
	/** ファイル状態更新コールバック。 */
	private FileStateUpdateCallback fileStateCallback;
	
	/**
	 * ModLanguageFileHandlerのコンストラクタ。
	 * @param translationService 翻訳サービス
	 * @param logger ログコールバック
	 * @param fileWriter ファイルライター
	 */
	public ModLanguageFileHandler(TranslationService translationService, LogCallback logger,
			LanguageFileWriter fileWriter) {
		this.translationService = translationService;
		this.logger = logger;
		this.fileWriter = fileWriter;
	}
	
	/**
	 * ファイル状態更新コールバックを設定します。
	 * @param callback コールバック
	 */
	public void setFileStateCallback(FileStateUpdateCallback callback) {
		this.fileStateCallback = callback;
	}
	
	/**
	 * 選択されたMod言語ファイルを処理します。
	 * @param modLangFiles 処理対象ファイルリスト
	 * @param results 結果リスト
	 * @throws Exception 処理エラー
	 */
	public void process(List<TranslatableFile> modLangFiles, List<ModProcessingResult> results) throws Exception {
		int totalMods = modLangFiles.size();
		
		for (int i = 0; i < modLangFiles.size(); i++) {
			TranslatableFile file = modLangFiles.get(i);
			int currentModNum = i + 1;
			
			ModProcessingResult result = new ModProcessingResult();
			result.modName = file.getModName();
			result.langFolderPath = file.getLangFolderPath();
			result.hasEnUs = true;
			result.hasJaJp = file.isHasExistingJaJp();
			result.characterCount = file.getCharacterCount();
			
			try {
				if (file.isHasExistingJaJp()) {
					file.setProcessingState(ProcessingState.EXISTING);
					updateFileState(file);
					
					fileWriter.writeLanguageFiles(file.getFileId(),
							file.getFileContent(), file.getExistingJaJpContent());
					result.translationSuccess = true;
					
					log(String.format("[%d/%d][既存] %s - 日本語ファイルをコピー",
							currentModNum, totalMods, file.getModName()));
				} else {
					file.setProcessingState(ProcessingState.TRANSLATING);
					updateFileState(file);
					
					String translatedContent = translateWithProgress(
					file, file.getFileContent(), currentModNum, totalMods);
					
					fileWriter.writeLanguageFiles(file.getFileId(),
							file.getFileContent(), translatedContent);
					result.translated = true;
					result.translationSuccess = true;
					
					file.setProcessingState(ProcessingState.COMPLETED);
					updateFileState(file);
					
					log(String.format("[%d/%d][翻訳] %s - 翻訳完了 (%d文字)",
							currentModNum, totalMods, file.getModName(), file.getCharacterCount()));
					
					logProgress(" ");
				}
			} catch (Exception e) {
				result.translated = true;
				result.translationSuccess = false;
				
				file.setProcessingState(ProcessingState.FAILED);
				updateFileState(file);
				
				log(String.format("[%d/%d][失敗] %s: %s",
						currentModNum, totalMods, file.getModName(), e.getMessage()));
				
				logProgress(" ");
			}
			
			results.add(result);
		}
	}
	
	/**
	 * 進捗通知付きで翻訳を実行します。
	 * @param file 翻訳対象ファイル
	 * @param content 翻訳対象コンテンツ
	 * @param currentMod 現在のMod番号
	 * @param totalMods 全Mod数
	 * @return 翻訳済みコンテンツ
	 * @throws Exception 翻訳エラー
	 */
	private String translateWithProgress(final TranslatableFile file, String content, 
			final int currentMod, final int totalMods) throws Exception {
		return translationService.translateJsonFile(content, new ProgressCallback() {
			@Override
			public void onProgress(int current, int total) {
				file.setProgress(current, total);
				updateFileState(file);
				logProgress(String.format("[%d/%d] 翻訳中: %s (%d/%d)",
						currentMod, totalMods, file.getModName(), current, total));
			}
		});
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
