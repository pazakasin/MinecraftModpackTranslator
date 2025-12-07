package io.github.pazakasin.minecraft.modpack.translator.controller.handler;

import java.util.List;
import java.util.Properties;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import io.github.pazakasin.minecraft.modpack.translator.controller.SettingsDialog;
import io.github.pazakasin.minecraft.modpack.translator.controller.ui.InputPanel;
import io.github.pazakasin.minecraft.modpack.translator.controller.ui.LogPanel;
import io.github.pazakasin.minecraft.modpack.translator.controller.ui.SelectiveTranslationWorker;
import io.github.pazakasin.minecraft.modpack.translator.controller.ui.StatusPanel;
import io.github.pazakasin.minecraft.modpack.translator.comparison.ComparisonDialog;
import io.github.pazakasin.minecraft.modpack.translator.comparison.ComparisonMode;
import io.github.pazakasin.minecraft.modpack.translator.comparison.ComparisonResult;
import io.github.pazakasin.minecraft.modpack.translator.comparison.HiddenFeatureManager;
import io.github.pazakasin.minecraft.modpack.translator.comparison.LoadFolderValidator;
import io.github.pazakasin.minecraft.modpack.translator.comparison.TranslationComparator;
import io.github.pazakasin.minecraft.modpack.translator.comparison.TranslationHistoryEntry;
import io.github.pazakasin.minecraft.modpack.translator.comparison.TranslationHistoryException;
import io.github.pazakasin.minecraft.modpack.translator.comparison.TranslationHistoryLoader;
import java.io.File;
import io.github.pazakasin.minecraft.modpack.translator.controller.callback.ButtonStateCallback;
import io.github.pazakasin.minecraft.modpack.translator.controller.callback.SettingsDialogCallback;
import io.github.pazakasin.minecraft.modpack.translator.controller.callback.TranslateButtonCallback;
import io.github.pazakasin.minecraft.modpack.translator.controller.callback.TranslationCompletionCallback;
import io.github.pazakasin.minecraft.modpack.translator.controller.callback.TranslationErrorCallback;
import io.github.pazakasin.minecraft.modpack.translator.controller.callback.TranslationResultCallback;
import io.github.pazakasin.minecraft.modpack.translator.controller.ui.UnifiedFileTablePanel;
import io.github.pazakasin.minecraft.modpack.translator.model.ModProcessingResult;
import io.github.pazakasin.minecraft.modpack.translator.model.TranslatableFile;
import io.github.pazakasin.minecraft.modpack.translator.service.TranslationService;
import io.github.pazakasin.minecraft.modpack.translator.service.callback.FileStateUpdateCallback;
import io.github.pazakasin.minecraft.modpack.translator.service.callback.LogCallback;
import io.github.pazakasin.minecraft.modpack.translator.service.callback.ProgressUpdateCallback;

/**
 * 翻訳アクションを処理するハンドラークラス。
 * 翻訳の開始、完了、エラー処理を管理。
 */
public class TranslationActionHandler {
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
	
	/** 翻訳サービス。 */
	private final TranslationService translationService;
	
	/** ボタン有効化コールバック。 */
	private final ButtonStateCallback buttonStateCallback;
	
	/** 翻訳ボタン有効化コールバック。 */
	private final TranslateButtonCallback translateButtonCallback;
	
	/** 翻訳結果コールバック。 */
	private final TranslationResultCallback translationResultCallback;
	
	/** 設定ダイアログ表示コールバック。 */
	private final SettingsDialogCallback settingsDialogCallback;
	
	/** 比較ハンドラー。 */
	private final ComparisonHandler comparisonHandler;
	
	/**
	 * TranslationActionHandlerのコンストラクタ。
	 * @param parentFrame 親フレーム
	 * @param inputPanel 入力パネル
	 * @param statusPanel ステータスパネル
	 * @param fileTablePanel ファイルテーブルパネル
	 * @param logPanel ログパネル
	 * @param translationService 翻訳サービス
	 * @param buttonStateCallback ボタン状態コールバック
	 * @param translateButtonCallback 翻訳ボタンコールバック
	 * @param translationResultCallback 翻訳結果コールバック
	 * @param settingsDialogCallback 設定ダイアログコールバック
	 * @param comparisonHandler 比較ハンドラー
	 */
	public TranslationActionHandler(JFrame parentFrame, InputPanel inputPanel,
			StatusPanel statusPanel, UnifiedFileTablePanel fileTablePanel,
			LogPanel logPanel, TranslationService translationService,
			ButtonStateCallback buttonStateCallback,
			TranslateButtonCallback translateButtonCallback,
			TranslationResultCallback translationResultCallback,
			SettingsDialogCallback settingsDialogCallback,
			ComparisonHandler comparisonHandler) {
		this.parentFrame = parentFrame;
		this.inputPanel = inputPanel;
		this.statusPanel = statusPanel;
		this.fileTablePanel = fileTablePanel;
		this.logPanel = logPanel;
		this.translationService = translationService;
		this.buttonStateCallback = buttonStateCallback;
		this.translateButtonCallback = translateButtonCallback;
		this.translationResultCallback = translationResultCallback;
		this.settingsDialogCallback = settingsDialogCallback;
		this.comparisonHandler = comparisonHandler;
	}
	
	/**
	 * 翻訳処理を開始します。
	 */
	public void startTranslation() {
		// 隠し機能：翻訳履歴読込モードの判定
		if (HiddenFeatureManager.isHistoryLoadEnabled()) {
			startHistoryComparison();
			return;
		}
		
		List<TranslatableFile> selectedFiles = fileTablePanel.getSelectedFiles();
		
		if (selectedFiles.isEmpty()) {
			JOptionPane.showMessageDialog(parentFrame,
					"翻訳対象のファイルを選択してください。",
					"警告", JOptionPane.WARNING_MESSAGE);
			return;
		}
		
		Properties settings = SettingsDialog.getStoredSettings();
		String apiKey = settings.getProperty(
				translationService.getProvider().name().toLowerCase() + ".apikey", "");
		
		if (apiKey.isEmpty()) {
			int result = JOptionPane.showConfirmDialog(parentFrame,
					"選択した翻訳プロバイダーのAPIキーが設定されていません。\n設定画面を開きますか?",
					"APIキー未設定", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
			
			if (result == JOptionPane.YES_OPTION) {
				settingsDialogCallback.openSettings();
			}
			return;
		}
		
		buttonStateCallback.setButtonsEnabled(false);
		translateButtonCallback.setTranslateButtonEnabled(false);
		
		SelectiveTranslationWorker worker = new SelectiveTranslationWorker(
				inputPanel.getInputPath(),
				selectedFiles,
				translationService,
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
				new TranslationCompletionCallback() {
					@Override
					public void onComplete(List<ModProcessingResult> results) {
						onTranslationComplete(results);
					}
				},
				new TranslationErrorCallback() {
					@Override
					public void onError(Exception error) {
						onTranslationError(error);
					}
				},
				new FileStateUpdateCallback() {
					@Override
					public void onFileStateUpdate(TranslatableFile file) {
						SwingUtilities.invokeLater(new Runnable() {
							@Override
							public void run() {
								fileTablePanel.updateFileState(file);
							}
						});
					}
				});
		
		worker.execute();
	}
	
	/**
	 * 翻訳処理が正常に完了したときに呼ばれます。
	 * @param results 翻訳結果リスト
	 */
	private void onTranslationComplete(List<ModProcessingResult> results) {
		buttonStateCallback.setButtonsEnabled(true);
		translateButtonCallback.setTranslateButtonEnabled(true);
		
		translationResultCallback.setProcessingResults(results);
		
		fileTablePanel.getCompareButton().setEnabled(true);
		
		JOptionPane.showMessageDialog(parentFrame,
				"翻訳が完了しました。\n出力先: output/",
				"完了", JOptionPane.INFORMATION_MESSAGE);
	}
	
	/**
	 * 翻訳処理中にエラーが発生したときに呼ばれます。
	 * @param e 発生した例外
	 */
	private void onTranslationError(Exception e) {
		buttonStateCallback.setButtonsEnabled(true);
		translateButtonCallback.setTranslateButtonEnabled(true);
		
		JOptionPane.showMessageDialog(parentFrame,
				"エラーが発生しました: " + e.getMessage(),
				"エラー", JOptionPane.ERROR_MESSAGE);
		e.printStackTrace();
	}
	
	/**
	 * 翻訳履歴読込モードを開始。
	 */
	private void startHistoryComparison() {
		logPanel.appendLog("[隠し機能] 翻訳履歴読込モードを開始します...");
		
		// loadフォルダの検証
		File loadFolder = HiddenFeatureManager.getLoadFolder();
		List<String> validationErrors = LoadFolderValidator.validate(loadFolder);
		
		if (LoadFolderValidator.hasErrors(validationErrors)) {
			String errorMessage = LoadFolderValidator.formatErrors(validationErrors);
			JOptionPane.showMessageDialog(parentFrame,
					errorMessage,
					"loadフォルダ検証エラー", JOptionPane.ERROR_MESSAGE);
			logPanel.appendLog("[エラー] loadフォルダの検証に失敗しました");
			return;
		}
		
		// 翻訳履歴の読込
		TranslationHistoryLoader loader = new TranslationHistoryLoader(
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
				});
		List<TranslationHistoryEntry> historyEntries;
		
		try {
			logPanel.appendLog("[隠し機能] 翻訳履歴を読み込んでいます...");
			historyEntries = loader.load(loadFolder);
			logPanel.appendLog("[隠し機能] " + historyEntries.size() + " 件の翻訳ファイルを読み込みました");
		} catch (TranslationHistoryException e) {
			JOptionPane.showMessageDialog(parentFrame,
					"翻訳履歴の読込に失敗しました:\n" + e.getMessage(),
					"読込エラー", JOptionPane.ERROR_MESSAGE);
			logPanel.appendLog("[エラー] 翻訳履歴の読込に失敗: " + e.getMessage());
			e.printStackTrace();
			return;
		}
		
		if (historyEntries.isEmpty()) {
			JOptionPane.showMessageDialog(parentFrame,
					"loadフォルダ内に翻訳ファイルが見つかりませんでした",
					"警告", JOptionPane.WARNING_MESSAGE);
			logPanel.appendLog("[警告] 翻訳ファイルが見つかりません");
			return;
		}
		
		// 解析済みファイルを取得（ファイル解析後に実行する必要がある）
		List<TranslatableFile> analyzedFiles = fileTablePanel.getAllFiles();
		
		if (analyzedFiles == null || analyzedFiles.isEmpty()) {
			JOptionPane.showMessageDialog(parentFrame,
					"ファイル解析が実行されていません。\n先に「ファイル解析」を実行してください。",
					"警告", JOptionPane.WARNING_MESSAGE);
			logPanel.appendLog("[警告] ファイル解析が必要です");
			return;
		}
		
		// ComparisonHandlerで比較実行
		comparisonHandler.compareWithHistory(analyzedFiles, historyEntries);
	}
}
