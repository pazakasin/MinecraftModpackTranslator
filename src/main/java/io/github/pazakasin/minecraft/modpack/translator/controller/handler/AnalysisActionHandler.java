package io.github.pazakasin.minecraft.modpack.translator.controller.handler;

import java.io.File;
import java.util.List;

import javax.swing.JFrame;
import javax.swing.JOptionPane;

import io.github.pazakasin.minecraft.modpack.translator.controller.callback.AnalysisCompletionCallback;
import io.github.pazakasin.minecraft.modpack.translator.controller.callback.AnalysisErrorCallback;
import io.github.pazakasin.minecraft.modpack.translator.controller.callback.AnalysisResultCallback;
import io.github.pazakasin.minecraft.modpack.translator.controller.callback.ButtonStateCallback;
import io.github.pazakasin.minecraft.modpack.translator.controller.callback.TranslateButtonCallback;
import io.github.pazakasin.minecraft.modpack.translator.controller.ui.AnalysisWorker;
import io.github.pazakasin.minecraft.modpack.translator.controller.ui.InputPanel;
import io.github.pazakasin.minecraft.modpack.translator.controller.ui.LogPanel;
import io.github.pazakasin.minecraft.modpack.translator.controller.ui.StatusPanel;
import io.github.pazakasin.minecraft.modpack.translator.controller.ui.UnifiedFileTablePanel;
import io.github.pazakasin.minecraft.modpack.translator.model.TranslatableFile;
import io.github.pazakasin.minecraft.modpack.translator.service.callback.LogCallback;
import io.github.pazakasin.minecraft.modpack.translator.service.callback.ProgressUpdateCallback;

/**
 * ファイル解析アクションを処理するハンドラークラス。
 * 解析の開始、完了、エラー処理を管理。
 */
public class AnalysisActionHandler {
	/** 親フレーム。 */
	private final JFrame parentFrame;
	
	/** 入力パネル。 */
	private final InputPanel inputPanel;
	
	/** ステータスパネル。 */
	private final StatusPanel statusPanel;
	
	/** ファイルテーブルパネル。 */
	private final UnifiedFileTablePanel fileTablePanel;
	
	/** ログパネル。 */
	private final LogPanel logPanel;
	
	/** ボタン有効化コールバック。 */
	private final ButtonStateCallback buttonStateCallback;
	
	/** 翻訳ボタン有効化コールバック。 */
	private final TranslateButtonCallback translateButtonCallback;
	
	/** 解析完了コールバック。 */
	private final AnalysisResultCallback analysisResultCallback;
	
	/**
	 * AnalysisActionHandlerのコンストラクタ。
	 * @param parentFrame 親フレーム
	 * @param inputPanel 入力パネル
	 * @param statusPanel ステータスパネル
	 * @param fileTablePanel ファイルテーブルパネル
	 * @param logPanel ログパネル
	 * @param buttonStateCallback ボタン状態コールバック
	 * @param translateButtonCallback 翻訳ボタンコールバック
	 * @param analysisResultCallback 解析結果コールバック
	 */
	public AnalysisActionHandler(JFrame parentFrame, InputPanel inputPanel,
			StatusPanel statusPanel, UnifiedFileTablePanel fileTablePanel,
			LogPanel logPanel, ButtonStateCallback buttonStateCallback,
			TranslateButtonCallback translateButtonCallback,
			AnalysisResultCallback analysisResultCallback) {
		this.parentFrame = parentFrame;
		this.inputPanel = inputPanel;
		this.statusPanel = statusPanel;
		this.fileTablePanel = fileTablePanel;
		this.logPanel = logPanel;
		this.buttonStateCallback = buttonStateCallback;
		this.translateButtonCallback = translateButtonCallback;
		this.analysisResultCallback = analysisResultCallback;
	}
	
	/**
	 * ファイル解析処理を開始します。
	 */
	public void startAnalysis() {
		String inputPath = inputPanel.getInputPath();
		
		if (inputPath.isEmpty()) {
			JOptionPane.showMessageDialog(parentFrame,
					"ModPackディレクトリを選択してください。",
					"エラー", JOptionPane.ERROR_MESSAGE);
			return;
		}
		
		File modsDir = new File(inputPath, "mods");
		if (!modsDir.exists() || !modsDir.isDirectory()) {
			JOptionPane.showMessageDialog(parentFrame,
					"指定されたディレクトリに'mods'フォルダが見つかりません。",
					"エラー", JOptionPane.ERROR_MESSAGE);
			return;
		}
		
		fileTablePanel.clearTable();
		logPanel.clearLog();
		buttonStateCallback.setButtonsEnabled(false);
		translateButtonCallback.setTranslateButtonEnabled(false);
		
		AnalysisWorker worker = new AnalysisWorker(
				inputPath,
				new LogCallback() {
					@Override
					public void onLog(String message) {
						logPanel.appendLog(message);
					}
				},
				new ProgressUpdateCallback() {
				@Override
				public void onProgressUpdate(int progress) {
				// 使用しない
				}
				
				@Override
				public void onProgressUpdate(String progress) {
				// 使用しない
				}
				},
				new AnalysisCompletionCallback() {
					@Override
					public void onComplete(List<TranslatableFile> files) {
						onAnalysisComplete(files);
					}
				},
				new AnalysisErrorCallback() {
					@Override
					public void onError(Exception error) {
						onAnalysisError(error);
					}
				});
		
		worker.execute();
	}
	
	/**
	 * 解析処理が正常に完了したときに呼ばれます。
	 * @param files 解析されたファイルリスト
	 */
	private void onAnalysisComplete(List<TranslatableFile> files) {
		buttonStateCallback.setButtonsEnabled(true);
		
		analysisResultCallback.setAnalyzedFiles(files);
		fileTablePanel.updateFileList(files);
		translateButtonCallback.setTranslateButtonEnabled(true);
		
		fileTablePanel.getExportCsvButton().setEnabled(true);
		
		JOptionPane.showMessageDialog(parentFrame,
				"ファイル解析が完了しました。\n検出されたファイル数: " + files.size(),
				"完了", JOptionPane.INFORMATION_MESSAGE);
	}
	
	/**
	 * 解析処理中にエラーが発生したときに呼ばれます。
	 * @param e 発生した例外
	 */
	private void onAnalysisError(Exception e) {
		buttonStateCallback.setButtonsEnabled(true);
		
		JOptionPane.showMessageDialog(parentFrame,
				"解析中にエラーが発生しました: " + e.getMessage(),
				"エラー", JOptionPane.ERROR_MESSAGE);
		e.printStackTrace();
	}
}
