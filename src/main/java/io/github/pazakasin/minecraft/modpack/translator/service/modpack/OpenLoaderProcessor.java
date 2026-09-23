package io.github.pazakasin.minecraft.modpack.translator.service.modpack;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import io.github.pazakasin.minecraft.modpack.translator.model.ModProcessingResult;
import io.github.pazakasin.minecraft.modpack.translator.model.ProcessingState;
import io.github.pazakasin.minecraft.modpack.translator.model.TranslatableFile;
import io.github.pazakasin.minecraft.modpack.translator.service.TranslationService;
import io.github.pazakasin.minecraft.modpack.translator.service.callback.FileStateUpdateCallback;
import io.github.pazakasin.minecraft.modpack.translator.service.callback.LogCallback;
import io.github.pazakasin.minecraft.modpack.translator.service.callback.ProgressCallback;
import io.github.pazakasin.minecraft.modpack.translator.service.processor.TranslationChangeDetector;

/**
 * OpenLoader言語ファイルの処理を担当するクラス。
 * 翻訳を実行し、原文から変更がある場合のみ入力の相対パス構造を保ったまま出力する
 * （既存の日本語ファイルは出力しない）。
 * 出力方式が同じConfig（その他）の言語ファイルにも、表示ラベルを変えて使用する。
 */
public class OpenLoaderProcessor {
	/** 翻訳サービス。 */
	private final TranslationService translationService;

	/** ログコールバック。 */
	private final LogCallback logger;

	/** ファイル状態更新コールバック。 */
	private FileStateUpdateCallback fileStateCallback;

	/** ログ・結果表示用のラベル（例: OpenLoader、Config）。 */
	private final String label;

	/** 翻訳結果の変更有無判定（未変更のファイルは出力しない）。 */
	private final TranslationChangeDetector changeDetector = new TranslationChangeDetector();

	/**
	 * OpenLoaderProcessorのコンストラクタ（ラベルは「OpenLoader」）。
	 * @param translationService 翻訳サービス
	 * @param logger ログコールバック
	 */
	public OpenLoaderProcessor(TranslationService translationService, LogCallback logger) {
		this(translationService, logger, "OpenLoader");
	}

	/**
	 * ラベルを指定するOpenLoaderProcessorのコンストラクタ。
	 * @param translationService 翻訳サービス
	 * @param logger ログコールバック
	 * @param label ログ・結果表示用のラベル
	 */
	public OpenLoaderProcessor(TranslationService translationService, LogCallback logger, String label) {
		this.translationService = translationService;
		this.logger = logger;
		this.label = label;
	}

	/**
	 * ファイル状態更新コールバックを設定します。
	 * @param callback コールバック
	 */
	public void setFileStateCallback(FileStateUpdateCallback callback) {
		this.fileStateCallback = callback;
	}

	/**
	 * 単一のOpenLoader言語ファイルを処理します。
	 * @param file 処理対象ファイル
	 * @param currentNum 現在のインデックス
	 * @param totalFiles 合計数
	 * @param results 結果リスト
	 * @throws Exception 処理エラー
	 */
	public void processSingleFile(TranslatableFile file, int currentNum, int totalFiles,
			List<ModProcessingResult> results) throws Exception {
		ModProcessingResult result = new ModProcessingResult();
		result.modName = label + " - " + file.getFileId();
		result.langFolderPath = file.getLangFolderPath();
		result.hasEnUs = true;
		result.hasJaJp = file.isHasExistingJaJp();
		result.characterCount = file.getCharacterCount();

		try {
			if (file.isHasExistingJaJp()) {
				file.setProcessingState(ProcessingState.EXISTING);
				file.setResultMessage(ProcessingState.EXISTING.getDisplayName());
				updateFileState(file);

				// 未修正のファイルは出力しない（既存の日本語ファイルはModPack側にあるため不要）
				result.translationSuccess = true;

				log(String.format("[%s %d/%d][既存] %s - 既存の日本語ファイルがあるため出力しません",
						label, currentNum, totalFiles, file.getFileId()));
			} else {
				file.setProcessingState(ProcessingState.TRANSLATING);
				file.setResultMessage(ProcessingState.TRANSLATING.getDisplayName());
				updateFileState(file);

				String translatedContent = translateWithProgress(
						file, file.getFileContent(), currentNum, totalFiles);

				result.translated = true;
				result.translationSuccess = true;

				if (changeDetector.isJsonUnchanged(file.getFileContent(), translatedContent)) {
					file.setProcessingState(ProcessingState.UNCHANGED);
					file.setResultMessage(ProcessingState.UNCHANGED.getDisplayName());
					updateFileState(file);

					log(String.format("[%s %d/%d][変更なし] %s - 翻訳結果が原文と同一のため出力しません",
							label, currentNum, totalFiles, file.getFileId()));
				} else {
					writeOpenLoaderLangFile(file, translatedContent);

					file.setProcessingState(ProcessingState.COMPLETED);
					file.setResultMessage(ProcessingState.COMPLETED.getDisplayName());
					updateFileState(file);

					log(String.format("[%s %d/%d][翻訳] %s - 翻訳完了 (%d文字)",
							label, currentNum, totalFiles, file.getFileId(), file.getCharacterCount()));
				}

				logProgress(" ");
			}
		} catch (Exception e) {
			result.translated = true;
			result.translationSuccess = false;
			result.errorException = e;

			file.setProcessingState(ProcessingState.FAILED);
			file.setResultMessage(ProcessingState.FAILED.getDisplayName() + ": " + e.getMessage());
			updateFileState(file);

			log(String.format("[%s %d/%d][失敗] %s: %s",
					label, currentNum, totalFiles, file.getFileId(), e.getMessage()));
			logStackTrace(e);

			logProgress(" ");
		}

		results.add(result);
	}

	/**
	 * 進捗通知付きで翻訳を実行します。
	 * @param file 翻訳対象ファイル
	 * @param content 翻訳対象コンテンツ
	 * @param currentNum 現在の番号
	 * @param totalFiles 全ファイル数
	 * @return 翻訳済みコンテンツ
	 * @throws Exception 翻訳エラー
	 */
	private String translateWithProgress(final TranslatableFile file, String content,
			final int currentNum, final int totalFiles) throws Exception {
		return translationService.translateJsonFile(content, new ProgressCallback() {
			@Override
			public void onProgress(int current, int total) {
				file.setProgress(current, total);
				updateFileState(file);
			}
		});
	}

	/**
	 * OpenLoader言語ファイルを出力します。
	 * 入力ファイルの相対パス構造（config/openloader/resources/...）をoutput配下にそのまま再現し、
	 * ファイル名のみen_us.jsonからja_jp.jsonに置き換えて書き込みます。
	 * @param file 処理対象ファイル（langFolderPathに入力側の相対パスを保持）
	 * @param jaJpContent 日本語コンテンツ
	 * @throws IOException ファイルI/Oエラー
	 */
	private void writeOpenLoaderLangFile(TranslatableFile file, String jaJpContent) throws IOException {
		if (jaJpContent == null) {
			return;
		}

		File relativeSourceFile = new File(file.getLangFolderPath());
		File outputDir = new File("output", relativeSourceFile.getParent());
		outputDir.mkdirs();

		Files.write(new File(outputDir, "ja_jp.json").toPath(),
				jaJpContent.getBytes("UTF-8"));
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
