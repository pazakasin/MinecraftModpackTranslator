package io.github.pazakasin.minecraft.modpack.translator.service;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.List;

import io.github.pazakasin.minecraft.modpack.translator.model.ModProcessingResult;
import io.github.pazakasin.minecraft.modpack.translator.model.QuestTranslationResult;
import io.github.pazakasin.minecraft.modpack.translator.model.TranslatableFile;
import io.github.pazakasin.minecraft.modpack.translator.service.callback.FileStateUpdateCallback;
import io.github.pazakasin.minecraft.modpack.translator.service.callback.LogCallback;
import io.github.pazakasin.minecraft.modpack.translator.service.callback.ProgressUpdateCallback;
import io.github.pazakasin.minecraft.modpack.translator.service.modpack.KubeJSProcessor;
import io.github.pazakasin.minecraft.modpack.translator.service.modpack.ModJarProcessor;
import io.github.pazakasin.minecraft.modpack.translator.service.modpack.ModLanguageFileHandler;
import io.github.pazakasin.minecraft.modpack.translator.service.modpack.SelectiveTranslationHandler;
import io.github.pazakasin.minecraft.modpack.translator.service.processor.LanguageFileWriter;
import io.github.pazakasin.minecraft.modpack.translator.service.quest.QuestFileProcessor;
import io.github.pazakasin.minecraft.modpack.translator.service.backup.BackupManager;

/**
 * Minecraft ModPackの翻訳処理を統括管理するクラス。
 * 指定ディレクトリ内のすべてのModを解析し、en_us.jsonをja_jp.jsonに翻訳。
 */
public class ModPackProcessor {
	/** 処理対象のModPackディレクトリパス。modsフォルダ内のJARを処理。 */
	private final String inputPath;
	
	/** ログメッセージを出力するコールバック。 */
	private final LogCallback logger;
	
	/** 進捗状況を更新するコールバック。 */
	private final ProgressUpdateCallback progressUpdater;
	
	/** 翻訳結果の出力先ディレクトリ。デフォルトは「output/MyJPpack」。 */
	private final File outputDir;
	
	/** Mod JAR処理プロセッサー。 */
	private final ModJarProcessor modJarProcessor;
	
	/** クエストファイル処理プロセッサー。 */
	private final QuestFileProcessor questProcessor;
	
	/** 選択的翻訳処理ハンドラー。 */
	private final SelectiveTranslationHandler selectiveHandler;
	
	/** ファイル状態更新時に呼ばれるコールバック。 */
	private FileStateUpdateCallback fileStateCallback;
	
	/** バックアップマネージャー。 */
	private final BackupManager backupManager;
	
	/**
	 * ModPackProcessorのコンストラクタ。
	 * @param inputPath 処理対象ディレクトリパス
	 * @param translationService 翻訳サービス
	 * @param logger ログコールバック
	 * @param progressUpdater 進捗コールバック
	 */
	public ModPackProcessor(String inputPath, TranslationService translationService,
			LogCallback logger, ProgressUpdateCallback progressUpdater) {
		this.inputPath = inputPath;
		this.logger = logger;
		this.progressUpdater = progressUpdater;
		this.outputDir = new File("output/MyJPpack");
		
		LanguageFileWriter fileWriter = new LanguageFileWriter(outputDir);
		
		this.modJarProcessor = new ModJarProcessor(translationService, logger, fileWriter);
		this.questProcessor = new QuestFileProcessor(translationService, logger, outputDir);
		
		ModLanguageFileHandler modLangHandler = new ModLanguageFileHandler(
				translationService, logger, fileWriter);
		KubeJSProcessor kubeJsProcessor = new KubeJSProcessor(translationService, logger);
		
		this.selectiveHandler = new SelectiveTranslationHandler(logger, modLangHandler,
				kubeJsProcessor, questProcessor, inputPath);
		
		this.backupManager = new BackupManager();
		this.fileStateCallback = null;
	}
	
	/**
	 * ファイル状態更新コールバックを設定します。
	 * @param callback コールバック
	 */
	public void setFileStateCallback(FileStateUpdateCallback callback) {
		this.fileStateCallback = callback;
		selectiveHandler.setFileStateCallback(callback);
	}
	
	/**
	 * ModPack全体の翻訳処理を実行します。
	 * @return 各Modの処理結果リスト
	 * @throws Exception ファイルアクセスエラー等
	 */
	public List<ModProcessingResult> process() throws Exception {
		File modsDir = new File(inputPath, "mods");
		File[] jarFiles = modsDir.listFiles(new FilenameFilter() {
			@Override
			public boolean accept(File dir, String name) {
				return name.endsWith(".jar");
			}
		});
		
		List<ModProcessingResult> results = new ArrayList<ModProcessingResult>();
		
		if (jarFiles == null || jarFiles.length == 0) {
			log("modsフォルダ内にJARファイルが見つかりません。");
			return results;
		}
		
		logModList(jarFiles);
		
		log("=== 翻訳処理開始 ===");
		int processed = 0;
		int skipped = 0;
		int translated = 0;
		int totalMods = jarFiles.length;
		
		for (int modIndex = 0; modIndex < jarFiles.length; modIndex++) {
			File jarFile = jarFiles[modIndex];
			int currentModNum = modIndex + 1;
			
			try {
				ModProcessingResult result = modJarProcessor.process(jarFile, currentModNum, totalMods);
				results.add(result);
				processed++;
				
				logProcessingResult(result, currentModNum, totalMods, jarFile.getName());
				
				if (result.hasJaJp && !result.translated) {
					skipped++;
				} else if (result.translated && result.translationSuccess) {
					translated++;
				} else if (!result.hasEnUs) {
					skipped++;
				}
			} catch (Exception e) {
				log(String.format("[%d/%d][エラー] %s: %s",
						currentModNum, totalMods, jarFile.getName(), e.getMessage()));
				
				ModProcessingResult errorResult = createErrorResult(jarFile);
				results.add(errorResult);
			}
		}
		
		logSummary(processed, translated, skipped);
		
		processQuests(results);
		
		writePackMcmeta();
		backupOutputFolder();
		
		return results;
	}
	
	/**
	 * クエストファイルを処理します。
	 * @param results 結果リスト
	 */
	private void processQuests(List<ModProcessingResult> results) {
		try {
			File modpackDir = new File(inputPath);
			QuestTranslationResult questResult = questProcessor.process(modpackDir);
			
			if (questResult.hasTranslation()) {
				ModProcessingResult questModResult = new ModProcessingResult();
				questModResult.modName = "FTB Quests";
				questModResult.langFolderPath = "config/ftbquests";
				questModResult.hasEnUs = questResult.hasLangFile;
				questModResult.hasJaJp = false;
				questModResult.translated = questResult.hasTranslation();
				questModResult.translationSuccess = questResult.isAllSuccess();
				questModResult.questResult = questResult;
				
				results.add(questModResult);
			}
		} catch (Exception e) {
			log("[Quest]エラー: " + e.getMessage());
		}
	}
	
	/**
	 * 選択された翻訳対象ファイルのみを処理します。
	 * @param selectedFiles 選択された翻訳対象ファイルのリスト
	 * @return 各ファイルの処理結果リスト
	 * @throws Exception ファイルアクセスエラー等
	 */
	public List<ModProcessingResult> processSelectedFiles(List<TranslatableFile> selectedFiles) throws Exception {
		List<ModProcessingResult> results = selectiveHandler.process(selectedFiles);
		writePackMcmeta();
		backupOutputFolder();
		return results;
	}
	
	/**
	 * Mod一覧をログ出力します。
	 * @param jarFiles JARファイルリスト
	 */
	private void logModList(File[] jarFiles) {
		log("=== Mod一覧 ===");
		log("検出されたMod数: " + jarFiles.length);
		for (File jarFile : jarFiles) {
			log("  - " + jarFile.getName());
		}
		log("");
	}
	
	/**
	 * 個別Modの処理結果をログ出力します。
	 * @param result 処理結果
	 * @param current 現在の番号
	 * @param total 合計数
	 * @param fileName ファイル名
	 */
	private void logProcessingResult(ModProcessingResult result, int current, int total, String fileName) {
		if (result.hasJaJp && !result.translated) {
			log(String.format("[%d/%d][既存] %s - 日本語ファイルが存在します",
					current, total, fileName));
		} else if (result.translated) {
			if (result.translationSuccess) {
				log(String.format("[%d/%d][翻訳] %s - 翻訳完了 (%d文字)",
						current, total, fileName, result.characterCount));
			} else {
				log(String.format("[%d/%d][失敗] %s - 翻訳に失敗しました",
						current, total, fileName));
			}
		} else {
			log(String.format("[%d/%d][スキップ] %s - 英語ファイルが見つかりません",
					current, total, fileName));
		}
	}
	
	/**
	 * 処理完了時のサマリーをログ出力します。
	 * @param processed 処理済み数
	 * @param translated 翻訳済み数
	 * @param skipped スキップ数
	 */
	private void logSummary(int processed, int translated, int skipped) {
		log("");
		log("=== 処理完了 ===");
		log("処理したMod数: " + processed);
		log("翻訳したMod数: " + translated);
		log("スキップしたMod数: " + skipped);
		log("出力先: " + new File("output").getAbsolutePath());
	}
	
	/**
	 * エラー発生時の処理結果を生成します。
	 * @param jarFile JARファイル
	 * @return エラー結果
	 */
	private ModProcessingResult createErrorResult(File jarFile) {
		ModProcessingResult errorResult = new ModProcessingResult();
		errorResult.modName = jarFile.getName().replace(".jar", "");
		errorResult.langFolderPath = "エラー";
		errorResult.translationSuccess = false;
		return errorResult;
	}
	
	/**
	 * pack.mcmetaファイルを出力します。
	 */
	private void writePackMcmeta() {
		try {
			File inputDir = new File(inputPath);
			String modpackName = inputDir.getName();
			
			java.util.Properties settings = io.github.pazakasin.minecraft.modpack.translator.controller.SettingsDialog.getStoredSettings();
			String packFormat = settings.getProperty("pack_format", "15");
			
			File packMetaFile = new File("output/resourcepacks/MyJPpack/pack.mcmeta");
			packMetaFile.getParentFile().mkdirs();
			
			String content = String.format(
				"{\n" +
				"  \"pack\": {\n" +
				"    \"pack_format\": %s,\n" +
				"    \"description\": \"%s 日本語翻訳パック\"\n" +
				"  }\n" +
				"}\n",
				packFormat,
				modpackName
			);
			
			java.nio.file.Files.write(packMetaFile.toPath(), content.getBytes("UTF-8"));
			
			log("");
			log("pack.mcmetaを出力しました: " + packMetaFile.getAbsolutePath());
		} catch (Exception e) {
			log("pack.mcmetaの出力に失敗しました: " + e.getMessage());
		}
	}
	
	/**
	 * 出力フォルダを圧縮してoutput_backupに保存します。
	 */
	private void backupOutputFolder() {
		try {
			BackupManager.ZipResult zipResult = backupManager.zipOutputFolder();
			if (zipResult != null) {
				log("");
				log("=== 出力フォルダバックアップ ===");
				log("圧縮ファイル: " + zipResult.zipPath);
				log("圧縮ファイル数: " + zipResult.fileCount);
			}
		} catch (Exception e) {
			log("出力フォルダの圧縮に失敗しました: " + e.getMessage());
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
}
