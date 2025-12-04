package io.github.pazakasin.minecraft.modpack.translator.controller.handler;

import java.io.File;
import java.util.List;

import javax.swing.JFrame;
import javax.swing.JOptionPane;

import io.github.pazakasin.minecraft.modpack.translator.comparison.ComparisonDialog;
import io.github.pazakasin.minecraft.modpack.translator.comparison.ComparisonResult;
import io.github.pazakasin.minecraft.modpack.translator.comparison.TranslationComparator;
import io.github.pazakasin.minecraft.modpack.translator.controller.callback.AnalyzedFilesCallback;
import io.github.pazakasin.minecraft.modpack.translator.controller.ui.LogPanel;
import io.github.pazakasin.minecraft.modpack.translator.controller.ui.UnifiedFileTablePanel;
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
					"エラー", JOptionPane.ERROR_MESSAGE);
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
	 * 翻訳前後のファイルを比較します。
	 */
	public void compareTranslation() {
		List<TranslatableFile> selectedFiles = fileTablePanel.getSelectedFiles();
		
		if (selectedFiles.isEmpty()) {
			JOptionPane.showMessageDialog(parentFrame,
					"比較対象のファイルを選択してください。",
					"エラー", JOptionPane.ERROR_MESSAGE);
			return;
		}
		
		if (selectedFiles.size() != 1) {
			JOptionPane.showMessageDialog(parentFrame,
					"比較は1つのファイルのみ選択してください。",
					"エラー", JOptionPane.ERROR_MESSAGE);
			return;
		}
		
		TranslatableFile selectedFile = selectedFiles.get(0);
		compareTranslation(selectedFile);
	}
	
	/**
	 * 指定されたファイルの翻訳前後を比較します。
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
		
		File translatedFile = getTranslatedFilePath(selectedFile);
		
		if (!translatedFile.exists()) {
			JOptionPane.showMessageDialog(parentFrame,
					"翻訳済みファイルが見つかりません。\n" +
							"翻訳を実行してください。\n\n" +
							"翻訳先パス: " + translatedFile.getAbsolutePath(),
					"エラー", JOptionPane.ERROR_MESSAGE);
			return;
		}
		
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
		case QUEST_LANG_FILE:
			return new File("output", file.getLangFolderPath().replace("en_us.snbt", "ja_jp.snbt"));
		case QUEST_FILE:
			return new File("output", file.getLangFolderPath());
		default:
			return null;
		}
	}
}
