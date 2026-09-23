package io.github.pazakasin.minecraft.modpack.translator.controller.handler;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JFrame;
import javax.swing.JOptionPane;

import io.github.pazakasin.minecraft.modpack.translator.comparison.ComparisonDialog;
import io.github.pazakasin.minecraft.modpack.translator.comparison.ComparisonResult;
import io.github.pazakasin.minecraft.modpack.translator.comparison.TranslationComparator;
import io.github.pazakasin.minecraft.modpack.translator.comparison.TranslationHistoryEntry;
import io.github.pazakasin.minecraft.modpack.translator.controller.callback.AnalyzedFilesCallback;
import io.github.pazakasin.minecraft.modpack.translator.controller.ui.LogPanel;
import io.github.pazakasin.minecraft.modpack.translator.controller.ui.UnifiedFileTablePanel;
import io.github.pazakasin.minecraft.modpack.translator.model.FileType;
import io.github.pazakasin.minecraft.modpack.translator.model.ProcessingState;
import io.github.pazakasin.minecraft.modpack.translator.model.TranslatableFile;
import io.github.pazakasin.minecraft.modpack.translator.util.CsvExporter;

/**
 * 翻訳比較とCSVエクスポート機能を処理するハンドラークラス。
 */
public class ComparisonHandler {
	/** 親フレーム。 */
	private final JFrame parentFrame;

	/** ファイルテーブルパネル。 */
	private final UnifiedFileTablePanel fileTablePanel;

	/** ログパネル。 */
	private final LogPanel logPanel;

	/** 解析済みファイルリスト取得コールバック。 */
	private final AnalyzedFilesCallback analyzedFilesCallback;

	/**
	 * ComparisonHandlerのコンストラクタ。
	 * @param parentFrame 親フレーム
	 * @param fileTablePanel ファイルテーブルパネル
	 * @param logPanel ログパネル
	 * @param analyzedFilesCallback 解析済みファイルコールバック
	 */
	public ComparisonHandler(JFrame parentFrame, UnifiedFileTablePanel fileTablePanel,
			LogPanel logPanel, AnalyzedFilesCallback analyzedFilesCallback) {
		this.parentFrame = parentFrame;
		this.fileTablePanel = fileTablePanel;
		this.logPanel = logPanel;
		this.analyzedFilesCallback = analyzedFilesCallback;
	}

	/**
	 * 処理結果をCSVファイルにエクスポートします。
	 */
	public void exportCsv() {
		List<TranslatableFile> analyzedFiles = analyzedFilesCallback.getAnalyzedFiles();

		if (analyzedFiles == null || analyzedFiles.isEmpty()) {
			JOptionPane.showMessageDialog(parentFrame,
					"エクスポートするデータがありません。\nファイル解析を実行してください。",
					"警告", JOptionPane.WARNING_MESSAGE);
			return;
		}

		try {
			CsvExporter exporter = new CsvExporter();
			String outputPath = exporter.exportTranslatableFiles(analyzedFiles);

			JOptionPane.showMessageDialog(parentFrame,
					"CSVファイルをエクスポートしました。\n" + outputPath,
					"成功", JOptionPane.INFORMATION_MESSAGE);

			logPanel.appendLog("\n[CSV出力] " + outputPath);
		} catch (Exception e) {
			JOptionPane.showMessageDialog(parentFrame,
					"CSVエクスポート中にエラーが発生しました: " + e.getMessage(),
					"エラー", JOptionPane.ERROR_MESSAGE);
			e.printStackTrace();
		}
	}

	/**
	 * 翻訳履歴との比較を実行します。
	 * @param originalFile workフォルダの原文ファイル
	 * @param historyEntry 翻訳履歴エントリ
	 * @param selectedFile 選択されたファイル
	 */
	private void compareWithHistory(File originalFile, TranslationHistoryEntry historyEntry,
			TranslatableFile selectedFile) {
		try {
			logPanel.appendLog("\n=== 翻訳履歴との比較開始 ===");
			logPanel.appendLog("原文ファイル: " + originalFile.getAbsolutePath());
			logPanel.appendLog("翻訳履歴: " + historyEntry.getFile().getAbsolutePath());

			// workファイルをMapに読み込む
			Map<String, String> workMap = loadFileAsMap(originalFile);

			// 翻訳履歴は既にMapとして保持されている
			Map<String, String> historyMap = historyEntry.getTranslations();

			TranslationComparator comparator = new TranslationComparator();
			List<ComparisonResult> results = comparator.compareWithHistory(workMap, historyMap);

			ComparisonDialog dialog = new ComparisonDialog(parentFrame);
			dialog.setTitle("翻訳履歴との比較: " + selectedFile.getModName());
			dialog.showResults(results);
			dialog.setVisible(true);

			logPanel.appendLog("[比較完了]");
		} catch (Exception e) {
			JOptionPane.showMessageDialog(parentFrame,
					"履歴との比較中にエラーが発生しました: " + e.getMessage(),
					"エラー", JOptionPane.ERROR_MESSAGE);
			e.printStackTrace();
		}
	}

	/**
	 * 表でクリック（行選択）されているファイルの翻訳前後を比較します。
	 */
	public void compareTranslation() {
		TranslatableFile highlightedFile = fileTablePanel.getHighlightedFile();

		if (highlightedFile == null) {
			JOptionPane.showMessageDialog(parentFrame,
					"比較対象のファイルを表で選択してください。",
					"警告", JOptionPane.WARNING_MESSAGE);
			return;
		}

		compareTranslation(highlightedFile);
	}

	/**
	 * 指定されたファイルの翻訳前後を比較します。
	 * 翻訳履歴がある場合は履歴との比較を表示します。
	 * @param selectedFile 比較対象ファイル
	 */
	public void compareTranslation(TranslatableFile selectedFile) {

		String workFilePath = selectedFile.getWorkFilePath();
		if (workFilePath == null || workFilePath.isEmpty()) {
			JOptionPane.showMessageDialog(parentFrame,
					"workフォルダに原文ファイルが見つかりません。\nファイル解析を再実行してください。",
					"エラー", JOptionPane.ERROR_MESSAGE);
			return;
		}

		File originalFile = new File(workFilePath);
		if (!originalFile.exists()) {
			JOptionPane.showMessageDialog(parentFrame,
					"workフォルダの原文ファイルが見つかりません: " + workFilePath + "\nファイル解析を再実行してください。",
					"エラー", JOptionPane.ERROR_MESSAGE);
			return;
		}

		// 既存の日本語ファイルがある場合はworkフォルダの原文と既存ja_jpを比較
		if (selectedFile.getProcessingState() == ProcessingState.EXISTING) {
			compareWithExistingJaJp(selectedFile, originalFile);
			return;
		}

		// 翻訳履歴がある場合は履歴との比較を実行
		TranslationHistoryEntry historyEntry = selectedFile.getHistoryEntry();
		if (historyEntry != null) {
			compareWithHistory(originalFile, historyEntry, selectedFile);
			return;
		}

		// 通常の比較（outputフォルダとの比較）
		File translatedFile = getTranslatedFilePath(selectedFile);

		if (!translatedFile.exists()) {
			JOptionPane.showMessageDialog(parentFrame,
					"翻訳済みファイルが見つかりません。\n" +
							"翻訳を実行してください。\n\n" +
							"翻訳先パス: " + translatedFile.getAbsolutePath(),
					"エラー", JOptionPane.ERROR_MESSAGE);
			return;
		}

		runComparison(originalFile, translatedFile);
	}

	/**
	 * workフォルダの原文（en_us）と既存の日本語ファイルを比較します。
	 * @param selectedFile 比較対象ファイル（既存ja_jpあり）
	 * @param jaJpFile workフォルダ内の既存日本語ファイル
	 */
	private void compareWithExistingJaJp(TranslatableFile selectedFile, File jaJpFile) {
		String enUsName = selectedFile.getFileType() == FileType.QUEST_LANG_FILE
				? new File(selectedFile.getSourceFilePath()).getName()
				: "en_us.json";
		File enUsFile = new File(jaJpFile.getParentFile(), enUsName);
		if (!enUsFile.exists()) {
			JOptionPane.showMessageDialog(parentFrame,
					"workフォルダの原文ファイルが見つかりません: " + enUsFile.getAbsolutePath()
							+ "\nファイル解析を再実行してください。",
					"エラー", JOptionPane.ERROR_MESSAGE);
			return;
		}
		runComparison(enUsFile, jaJpFile);
	}

	/**
	 * 2つのファイルを比較し、結果ダイアログを表示します。
	 * @param originalFile 比較元（原文）ファイル
	 * @param translatedFile 比較先（日本語）ファイル
	 */
	private void runComparison(File originalFile, File translatedFile) {
		try {
			logPanel.appendLog("\n=== 翻訳比較開始 ===");
			logPanel.appendLog("比較元ファイル: " + originalFile.getAbsolutePath());
			logPanel.appendLog("比較先ファイル: " + translatedFile.getAbsolutePath());

			TranslationComparator comparator = new TranslationComparator();
			List<ComparisonResult> results = comparator.compare(originalFile, translatedFile);

			ComparisonDialog dialog = new ComparisonDialog(parentFrame);
			dialog.showResults(results);
			dialog.setVisible(true);

			logPanel.appendLog("[比較完了]");
		} catch (Exception e) {
			JOptionPane.showMessageDialog(parentFrame,
					"比較中にエラーが発生しました: " + e.getMessage(),
					"エラー", JOptionPane.ERROR_MESSAGE);
			e.printStackTrace();
		}
	}

	/**
	 * 翻訳先ファイルのパスを取得します。
	 * @param file 対象ファイル
	 * @return 翻訳先ファイルのパス
	 */
	private File getTranslatedFilePath(TranslatableFile file) {
		switch (file.getFileType()) {
		case MOD_LANG_FILE:
			return new File("output/resourcepacks/MyJPpack/assets/" + file.getFileId() + "/lang/ja_jp.json");
		case KUBEJS_LANG_FILE:
			return new File("output/kubejs/assets/" + file.getFileId() + "/lang/ja_jp.json");
		case OPENLOADER_LANG_FILE:
		case CONFIG_LANG_FILE:
			File openLoaderRelative = new File(file.getLangFolderPath());
			return new File(new File("output", openLoaderRelative.getParent()), "ja_jp.json");
		case QUEST_LANG_FILE:
			return new File("output", file.getLangFolderPath().replace("en_us.snbt", "ja_jp.snbt"));
		case QUEST_FILE:
			return new File("output", file.getLangFolderPath());
		default:
			return null;
		}
	}

	/**
	 * 翻訳前ファイルと翻訳履歴を比較します（隠し機能）。
	 * @param analyzedFiles 解析済みファイルリスト
	 * @param historyEntries 翻訳履歴エントリリスト
	 */
	public void compareWithHistory(List<TranslatableFile> analyzedFiles,
			List<TranslationHistoryEntry> historyEntries) {
		if (analyzedFiles == null || analyzedFiles.isEmpty()) {
			JOptionPane.showMessageDialog(parentFrame,
					"解析済みファイルがありません。\nファイル解析を実行してください。",
					"警告", JOptionPane.WARNING_MESSAGE);
			return;
		}

		if (historyEntries == null || historyEntries.isEmpty()) {
			JOptionPane.showMessageDialog(parentFrame,
					"翻訳履歴がありません。",
					"警告", JOptionPane.WARNING_MESSAGE);
			return;
		}

		logPanel.appendLog("\n=== 翻訳履歴比較開始 ===");
		logPanel.appendLog("解析済みファイル: " + analyzedFiles.size() + "件");
		logPanel.appendLog("翻訳履歴: " + historyEntries.size() + "件");

		int comparisonCount = 0;
		TranslationComparator comparator = new TranslationComparator();

		// 最初の5件のファイル情報を詳細ログ出力（デバッグ用）
		if (!analyzedFiles.isEmpty()) {
			logPanel.appendLog("[デバッグ] 解析済みファイル詳細サンプル:");
			for (int i = 0; i < Math.min(5, analyzedFiles.size()); i++) {
				TranslatableFile f = analyzedFiles.get(i);
				logPanel.appendLog("  - タイプ: " + f.getFileType() +
						", ID: " + f.getFileId() +
						", 名前: " + f.getModName() +
						", workパス: " + f.getWorkFilePath());
			}
		}

		if (!historyEntries.isEmpty()) {
			logPanel.appendLog("[デバッグ] 翻訳履歴パスサンプル:");
			for (int i = 0; i < Math.min(5, historyEntries.size()); i++) {
				TranslationHistoryEntry e = historyEntries.get(i);
				String path = e.getFile().getAbsolutePath();
				String normalizedPath = path.replace("\\", "/");
				logPanel.appendLog("  - 元パス: " + path);
				logPanel.appendLog("    正規化: " + normalizedPath);
			}
		}

		// 各解析済みファイルに対して、対応する履歴を探して設定
		int debugCount = 0;
		for (TranslatableFile analyzedFile : analyzedFiles) {
			if (debugCount < 5) {
				logPanel.appendLog("[デバッグ] マッチング試行 #" + (debugCount + 1) +
						": タイプ=" + analyzedFile.getFileType() +
						", ID=" + analyzedFile.getFileId() +
						", 名前=" + analyzedFile.getModName());
			}

			TranslationHistoryEntry matchedHistory = findMatchingHistory(analyzedFile, historyEntries, debugCount < 5);

			if (matchedHistory != null) {
				comparisonCount++;
				// 履歴をTranslatableFileに設定（状態が「履歴あり」に更新される）
				analyzedFile.setHistoryEntry(matchedHistory);

				if (debugCount < 5) {
					logPanel.appendLog("✓ マッチング成功: " + analyzedFile.getModName() + " <-> " +
							matchedHistory.getFile().getName());
				}
			} else {
				if (debugCount < 5) {
					logPanel.appendLog("✗ マッチング失敗: " + analyzedFile.getModName());
				}
			}

			debugCount++;
			if (debugCount == 5) {
				logPanel.appendLog("[デバッグ] 以降のマッチング試行ログは省略...");
			}
		}

		if (comparisonCount == 0) {
			JOptionPane.showMessageDialog(parentFrame,
					"解析結果と翻訳履歴のファイルが一致しません。",
					"警告", JOptionPane.WARNING_MESSAGE);
			return;
		}

		logPanel.appendLog("マッチング成功: " + comparisonCount + "/" + analyzedFiles.size() + "件");
		logPanel.appendLog("[履歴読み込み完了] ファイル一覧の状態欄に反映されました。");
		logPanel.appendLog("※ファイルをクリックして「翻訳前後を比較」を実行すると、履歴との比較が表示されます。");

		// テーブルを更新して状態を反映
		fileTablePanel.refreshTable();

		JOptionPane.showMessageDialog(parentFrame,
				"翻訳履歴を読み込みました。\n\n" +
						"マッチング成功: " + comparisonCount + "/" + analyzedFiles.size() + "件\n\n" +
						"※ファイルを選択して「翻訳前後を比較」を実行すると、\n" +
						"  履歴との比較結果が表示されます。",
				"情報", JOptionPane.INFORMATION_MESSAGE);
	}

	/**
	 * 解析済みファイルに対応する翻訳履歴を探します。
	 * @param analyzedFile 解析済みファイル
	 * @param historyEntries 翻訳履歴エントリリスト
	 * @param debug デバッグログ出力フラグ
	 * @return マッチする履歴エントリ、見つからなければnull
	 */
	private TranslationHistoryEntry findMatchingHistory(TranslatableFile analyzedFile,
			List<TranslationHistoryEntry> historyEntries,
			boolean debug) {
		// ファイルIDでマッチング
		String fileId = analyzedFile.getFileId();

		if (fileId == null || fileId.isEmpty()) {
			if (debug) {
				logPanel.appendLog("  [デバッグ] fileIdがnullまたは空");
			}
			return null;
		}

		// ファイルタイプごとにマッチングロジックを切り替え
		switch (analyzedFile.getFileType()) {
		case MOD_LANG_FILE:
			// Mod言語ファイル: /assets/{fileId}/lang/ja_jp.json
			if (debug) {
				logPanel.appendLog("  [デバッグ] MOD_LANG_FILEとしてマッチング試行");
			}
			return findByPattern(historyEntries, "/assets/" + fileId + "/lang/ja_jp.json", debug);

		case KUBEJS_LANG_FILE:
			// KubeJS言語ファイル: /kubejs/assets/{fileId}/lang/ja_jp.json
			if (debug) {
				logPanel.appendLog("  [デバッグ] KUBEJS_LANG_FILEとしてマッチング試行");
			}
			return findByPattern(historyEntries, "/kubejs/assets/" + fileId + "/lang/ja_jp.json", debug);

		case OPENLOADER_LANG_FILE:
			// OpenLoader言語ファイル: 入力の相対パスのen_us.jsonをja_jp.jsonに置き換えたパターンでマッチング
			if (debug) {
				logPanel.appendLog("  [デバッグ] OPENLOADER_LANG_FILEとしてマッチング試行");
			}
			String openLoaderPattern = analyzedFile.getLangFolderPath().replace("en_us.json", "ja_jp.json");
			return findByPattern(historyEntries, openLoaderPattern, debug);

		case CONFIG_LANG_FILE:
			// Config（その他）: 入力の相対パスのlangフォルダ内ja_jp.jsonでマッチング（非対応形式は対象外）
			if (!analyzedFile.isTranslatable()) {
				return null;
			}
			if (debug) {
				logPanel.appendLog("  [デバッグ] CONFIG_LANG_FILEとしてマッチング試行");
			}
			String configPath = analyzedFile.getLangFolderPath();
			String configPattern = configPath.substring(0, configPath.lastIndexOf('/') + 1) + "ja_jp.json";
			return findByPattern(historyEntries, configPattern, debug);

		case QUEST_LANG_FILE:
			// Quest言語ファイル: /quests/lang/ja_jp.json または ja_jp.snbt
			if (debug) {
				logPanel.appendLog("  [デバッグ] QUEST_LANG_FILEとしてマッチング試行");
			}
			TranslationHistoryEntry jsonEntry = findByPattern(historyEntries, "/quests/lang/ja_jp.json", debug);
			if (jsonEntry != null) {
				return jsonEntry;
			}
			return findByPattern(historyEntries, "/quests/lang/ja_jp.snbt", debug);

		case QUEST_FILE:
			// Questファイル本体: ファイル名でマッチング
			if (debug) {
				logPanel.appendLog("  [デバッグ] QUEST_FILEとしてマッチング試行");
			}
			String fileName = analyzedFile.getModName(); // 例: "chickens.snbt"
			return findByFileName(historyEntries, fileName, debug);

		default:
			if (debug) {
				logPanel.appendLog("  [デバッグ] 不明なファイルタイプ: " + analyzedFile.getFileType());
			}
			return null;
		}
	}

	/**
	 * パターンにマッチする履歴エントリを探す。
	 * @param historyEntries 翻訳履歴エントリリスト
	 * @param pattern パスに含まれるべきパターン
	 * @param debug デバッグログ出力フラグ
	 * @return マッチする履歴エントリ、見つからなければnull
	 */
	private TranslationHistoryEntry findByPattern(List<TranslationHistoryEntry> historyEntries,
			String pattern,
			boolean debug) {
		if (debug) {
			logPanel.appendLog("  [デバッグ] パターン検索: " + pattern);
		}

		for (TranslationHistoryEntry entry : historyEntries) {
			File historyFile = entry.getFile();
			String historyPath = historyFile.getAbsolutePath().replace("\\", "/");

			if (historyPath.contains(pattern)) {
				if (debug) {
					logPanel.appendLog("  [デバッグ] パターンマッチ成功: " + historyPath);
				}
				return entry;
			}
		}

		if (debug) {
			logPanel.appendLog("  [デバッグ] パターンマッチ失敗");
		}
		return null;
	}

	/**
	 * ファイル名にマッチする履歴エントリを探す。
	 * @param historyEntries 翻訳履歴エントリリスト
	 * @param fileName ファイル名
	 * @param debug デバッグログ出力フラグ
	 * @return マッチする履歴エントリ、見つからなければnull
	 */
	private TranslationHistoryEntry findByFileName(List<TranslationHistoryEntry> historyEntries,
			String fileName,
			boolean debug) {
		if (debug) {
			logPanel.appendLog("  [デバッグ] ファイル名検索: " + fileName);
		}

		int matchCount = 0;
		for (TranslationHistoryEntry entry : historyEntries) {
			File historyFile = entry.getFile();
			String entryFileName = historyFile.getName();

			if (debug && matchCount < 3) {
				logPanel.appendLog("    比較: " + entryFileName + " vs " + fileName);
				matchCount++;
			}

			if (entryFileName.equals(fileName)) {
				if (debug) {
					logPanel.appendLog("  [デバッグ] ファイル名マッチ成功: " + historyFile.getAbsolutePath());
				}
				return entry;
			}
		}

		if (debug) {
			logPanel.appendLog("  [デバッグ] ファイル名マッチ失敗");
		}
		return null;
	}

	/**
	 * ファイルをMapに読み込む。
	 * @param file 対象ファイル
	 * @return キーと値のMap
	 * @throws Exception 読み込みエラー
	 */
	private Map<String, String> loadFileAsMap(File file) throws Exception {
		// TranslationComparatorの既存機能を利用してファイルを読み込む
		// 空の一時ファイルを作成し、compare()で原文を読み込む
		File tempFile = File.createTempFile("temp", file.getName().endsWith(".snbt") ? ".snbt" : ".json");
		try {
			// 空のファイルを作成
			if (file.getName().endsWith(".snbt")) {
				// SNBT: 空のタグを書き込む
				java.nio.file.Files.writeString(tempFile.toPath(), "{}");
			} else {
				// JSON: 空のオブジェクトを書き込む
				java.nio.file.Files.writeString(tempFile.toPath(), "{}");
			}

			TranslationComparator comparator = new TranslationComparator();
			List<ComparisonResult> results = comparator.compare(file, tempFile);

			// REMOVEDとUNCHANGEDから原文データを抽出
			Map<String, String> map = new LinkedHashMap<>();
			for (ComparisonResult result : results) {
				if (result.getChangeType() == ComparisonResult.ChangeType.REMOVED ||
						result.getChangeType() == ComparisonResult.ChangeType.UNCHANGED) {
					map.put(result.getKey(), result.getOriginalValue());
				}
			}
			return map;
		} finally {
			tempFile.delete();
		}
	}
}
