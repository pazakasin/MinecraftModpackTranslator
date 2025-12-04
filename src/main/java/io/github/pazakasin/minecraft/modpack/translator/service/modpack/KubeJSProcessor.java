package io.github.pazakasin.minecraft.modpack.translator.service.modpack;

import java.io.File;
import java.io.IOException;
import java.util.List;

import io.github.pazakasin.minecraft.modpack.translator.model.ModProcessingResult;
import io.github.pazakasin.minecraft.modpack.translator.model.ProcessingState;
import io.github.pazakasin.minecraft.modpack.translator.model.TranslatableFile;
import io.github.pazakasin.minecraft.modpack.translator.service.ProgressCallback;
import io.github.pazakasin.minecraft.modpack.translator.service.TranslationService;
import io.github.pazakasin.minecraft.modpack.translator.service.callback.FileStateUpdateCallback;
import io.github.pazakasin.minecraft.modpack.translator.service.callback.LogCallback;

/**
 * KubeJS言語ファイルの処理を担当するクラス。
 * 既存ファイルのコピーまたは翻訳を実行。
 */
public class KubeJSProcessor {
	/** 翻訳サービス。 */
	private final TranslationService translationService;
	
	/** ログコールバック。 */
	private final LogCallback logger;
	
	/** ファイル状態更新コールバック。 */
	private FileStateUpdateCallback fileStateCallback;
	
	/**
	 * KubeJSProcessorのコンストラクタ。
	 * @param translationService 翻訳サービス
	 * @param logger ログコールバック
	 */
	public KubeJSProcessor(TranslationService translationService, LogCallback logger) {
		this.translationService = translationService;
		this.logger = logger;
	}
	
	/**
	 * ファイル状態更新コールバックを設定します。
	 * @param callback コールバック
	 */
	public void setFileStateCallback(FileStateUpdateCallback callback) {
		this.fileStateCallback = callback;
	}
	
	/**
	 * 選択されたKubeJS言語ファイルを処理します。
	 * @param kubeJsFiles 処理対象ファイルリスト
	 * @param results 結果リスト
	 * @throws Exception 処理エラー
	 */
	public void process(List<TranslatableFile> kubeJsFiles, List<ModProcessingResult> results) throws Exception {
		int totalFiles = kubeJsFiles.size();
		
		for (int i = 0; i < kubeJsFiles.size(); i++) {
			TranslatableFile file = kubeJsFiles.get(i);
			int currentNum = i + 1;
			
			ModProcessingResult result = new ModProcessingResult();
			result.modName = "KubeJS - " + file.getFileId();
			result.langFolderPath = file.getLangFolderPath();
			result.hasEnUs = true;
			result.hasJaJp = file.isHasExistingJaJp();
			result.characterCount = file.getCharacterCount();
			
			try {
				if (file.isHasExistingJaJp()) {
					file.setProcessingState(ProcessingState.EXISTING);
					file.setResultMessage("既存");
					updateFileState(file);
					
					writeKubeJSLangFiles(file);
					result.translationSuccess = true;
					
					log(String.format("[%d/%d][既存] %s - 日本語ファイルをコピー",
							currentNum, totalFiles, file.getFileId()));
				} else {
					file.setProcessingState(ProcessingState.TRANSLATING);
					file.setResultMessage("翻訳中");
					updateFileState(file);
					
					String translatedContent = translateWithProgress(
							file.getFileContent(), currentNum, totalFiles);
					
					writeKubeJSLangFiles(file.getFileId(), file.getFileContent(), translatedContent);
					result.translated = true;
					result.translationSuccess = true;
					
					file.setProcessingState(ProcessingState.COMPLETED);
					file.setResultMessage("○");
					updateFileState(file);
					
					log(String.format("[%d/%d][翻訳] %s - 翻訳完了 (%d文字)",
							currentNum, totalFiles, file.getFileId(), file.getCharacterCount()));
					
					logProgress(" ");
				}
			} catch (Exception e) {
				result.translated = true;
				result.translationSuccess = false;
				
				file.setProcessingState(ProcessingState.FAILED);
				file.setResultMessage("×");
				updateFileState(file);
				
				log(String.format("[%d/%d][失敗] %s: %s",
						currentNum, totalFiles, file.getFileId(), e.getMessage()));
				
				logProgress(" ");
			}
			
			results.add(result);
		}
	}
	
	/**
	 * 進捗通知付きで翻訳を実行します。
	 * @param content 翻訳対象コンテンツ
	 * @param currentNum 現在の番号
	 * @param totalFiles 全ファイル数
	 * @return 翻訳済みコンテンツ
	 * @throws Exception 翻訳エラー
	 */
	private String translateWithProgress(String content, final int currentNum, final int totalFiles) throws Exception {
		return translationService.translateJsonFile(content, new ProgressCallback() {
			@Override
			public void onProgress(int current, int total) {
				logProgress(String.format("[%d/%d] 翻訳中: %d/%d エントリー",
						currentNum, totalFiles, current, total));
			}
		});
	}
	
	/**
	 * KubeJS言語ファイルを書き込みます（既存ファイル用）。
	 * @param file ファイル情報
	 * @throws IOException ファイルI/Oエラー
	 */
	private void writeKubeJSLangFiles(TranslatableFile file) throws IOException {
		writeKubeJSLangFiles(file.getFileId(), file.getFileContent(), file.getExistingJaJpContent());
	}
	
	/**
	 * KubeJS言語ファイルを書き込みます。
	 * ja_jp.jsonのみを出力し、en_us.jsonは出力しない。
	 * @param fileId ファイルID
	 * @param enUsContent 英語コンテンツ（使用しない）
	 * @param jaJpContent 日本語コンテンツ
	 * @throws IOException ファイルI/Oエラー
	 */
	private void writeKubeJSLangFiles(String fileId, String enUsContent, String jaJpContent) throws IOException {
		File langDir = new File("output/kubejs/assets/" + fileId + "/lang");
		langDir.mkdirs();
		
		if (jaJpContent != null) {
			java.nio.file.Files.write(new File(langDir, "ja_jp.json").toPath(),
					jaJpContent.getBytes("UTF-8"));
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
