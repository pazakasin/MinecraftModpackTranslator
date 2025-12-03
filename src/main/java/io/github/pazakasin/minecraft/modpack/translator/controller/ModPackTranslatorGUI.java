package io.github.pazakasin.minecraft.modpack.translator.controller;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.List;
import java.util.Properties;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import io.github.pazakasin.minecraft.modpack.translator.controller.ui.AnalysisWorker;
import io.github.pazakasin.minecraft.modpack.translator.controller.ui.InputPanel;
import io.github.pazakasin.minecraft.modpack.translator.controller.ui.LogPanel;
import io.github.pazakasin.minecraft.modpack.translator.controller.ui.SelectiveTranslationWorker;
import io.github.pazakasin.minecraft.modpack.translator.controller.ui.StatusPanel;
import io.github.pazakasin.minecraft.modpack.translator.controller.ui.UnifiedFileTablePanel;
import io.github.pazakasin.minecraft.modpack.translator.model.ModProcessingResult;
import io.github.pazakasin.minecraft.modpack.translator.model.TranslatableFile;
import io.github.pazakasin.minecraft.modpack.translator.service.TranslationService;
import io.github.pazakasin.minecraft.modpack.translator.service.callback.LogCallback;
import io.github.pazakasin.minecraft.modpack.translator.service.callback.ProgressUpdateCallback;
import io.github.pazakasin.minecraft.modpack.translator.service.callback.FileStateUpdateCallback;
import io.github.pazakasin.minecraft.modpack.translator.util.CsvExporter;
import io.github.pazakasin.minecraft.modpack.translator.comparison.ComparisonDialog;
import io.github.pazakasin.minecraft.modpack.translator.comparison.ComparisonResult;
import io.github.pazakasin.minecraft.modpack.translator.comparison.TranslationComparator;

/**
 * Minecraft ModPack日本語翻訳ツールのメインGUIクラス。
 * ファイル解析、選択、翻訳実行、結果表示、CSVエクスポート機能を提供。
 */
public class ModPackTranslatorGUI extends JFrame {
	/** ディレクトリ選択用の入力パネル。 */
	private InputPanel inputPanel;

	/** ステータス情報を表示するパネル。 */
	private StatusPanel statusPanel;

	/** ファイル選択と処理結果を統合表示するパネル。 */
	private UnifiedFileTablePanel unifiedFileTablePanel;

	/** ログメッセージを表示するパネル。 */
	private LogPanel logPanel;

	/** 設定ダイアログを開くボタン。 */
	private JButton settingsButton;

	/** ファイル解析を開始するボタン。 */
	private JButton analyzeButton;

	/** 翻訳処理を開始するボタン。 */
	private JButton translateButton;

	/** 処理の進捗を表示するプログレスバー。 */
	private JProgressBar progressBar;

	/** 翻訳サービスの管理インスタンス。 */
	private TranslationService translationService;

	/** 最後に実行した処理の結果リスト。 */
	private List<ModProcessingResult> processingResults;

	/** 解析された翻訳対象ファイルのリスト。 */
	private List<TranslatableFile> analyzedFiles;

	/**
	 * ModPackTranslatorGUIのコンストラクタ。
	 */
	public ModPackTranslatorGUI() {
		setTitle("Minecraft ModPack 日本語翻訳ツール v2.1");
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setSize(1200, 800);
		setLocationRelativeTo(null);

		translationService = new TranslationService();
		loadSettings();

		initComponents();
	}

	/** 保存された設定を読み込み、翻訳サービスに適用します。 */
	private void loadSettings() {
		Properties settings = SettingsDialog.getStoredSettings();

		String providerName = settings.getProperty("provider", "GOOGLE");
		try {
			TranslationService.ProviderType provider =
					TranslationService.ProviderType.valueOf(providerName);
			translationService.setProvider(provider);

			String apiKey = settings.getProperty(provider.name().toLowerCase() + ".apikey", "");
			translationService.setApiKey(apiKey);
		} catch (IllegalArgumentException e) {
			// デフォルト設定を使用
		}
	}

	/** UIコンポーネントを初期化し、レイアウトを構築します。 */
	private void initComponents() {
		JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
		mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

		inputPanel = new InputPanel();
		statusPanel = new StatusPanel();
		statusPanel.setStatusText("翻訳プロバイダー: " +
				translationService.getProvider().getDisplayName());

		unifiedFileTablePanel = new UnifiedFileTablePanel(this);
		logPanel = new LogPanel();

		JPanel buttonPanel = createButtonPanel();

		progressBar = new JProgressBar();
		progressBar.setStringPainted(true);

		JPanel topPanel = new JPanel(new BorderLayout(5, 5));
		topPanel.add(inputPanel, BorderLayout.NORTH);

		JPanel middlePanel = new JPanel(new BorderLayout(5, 5));
		middlePanel.add(statusPanel, BorderLayout.NORTH);
		middlePanel.add(buttonPanel, BorderLayout.CENTER);
		middlePanel.add(progressBar, BorderLayout.SOUTH);

		topPanel.add(middlePanel, BorderLayout.CENTER);

		// 統合ファイルテーブルとログを上下に分割
		JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
				unifiedFileTablePanel, logPanel);
		splitPane.setResizeWeight(0.7);

		mainPanel.add(topPanel, BorderLayout.NORTH);
		mainPanel.add(splitPane, BorderLayout.CENTER);

		add(mainPanel);

		// 初期状態では翻訳ボタンを無効化
		translateButton.setEnabled(false);
	}

	/** 操作ボタンを含むパネルを作成します。 */
	private JPanel createButtonPanel() {
		JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));

		analyzeButton = new JButton("ファイル解析");
		analyzeButton.setFont(new Font("Dialog", Font.BOLD, 14));
		analyzeButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				startAnalysis();
			}
		});

		translateButton = new JButton("翻訳実行");
		translateButton.setFont(new Font("Dialog", Font.BOLD, 14));
		translateButton.setEnabled(false);
		translateButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				startTranslation();
			}
		});
		
		settingsButton = new JButton("⚙ 設定");
		settingsButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				openSettings();
			}
		});

		buttonPanel.add(analyzeButton);
		buttonPanel.add(translateButton);
		buttonPanel.add(settingsButton);

		return buttonPanel;
	}

	/** 設定ダイアログを開き、保存された場合は設定を再読み込みします。 */
	private void openSettings() {
		SettingsDialog dialog = new SettingsDialog(this);
		dialog.setVisible(true);

		if (dialog.isSaved()) {
			loadSettings();
			statusPanel.setStatusText("翻訳プロバイダー: " +
					translationService.getProvider().getDisplayName());
		}
	}

	/** ファイル解析処理を開始します。 */
	private void startAnalysis() {
		String inputPath = inputPanel.getInputPath();

		if (inputPath.isEmpty()) {
			JOptionPane.showMessageDialog(this,
					"ModPackディレクトリを選択してください。",
					"エラー", JOptionPane.ERROR_MESSAGE);
			return;
		}

		File modsDir = new File(inputPath, "mods");
		if (!modsDir.exists() || !modsDir.isDirectory()) {
			JOptionPane.showMessageDialog(this,
					"指定されたディレクトリに'mods'フォルダが見つかりません。",
					"エラー", JOptionPane.ERROR_MESSAGE);
			return;
		}

		unifiedFileTablePanel.clearTable();
		logPanel.clearLog();
		setButtonsEnabled(false);
		translateButton.setEnabled(false);

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
						statusPanel.setProgressText(progress);
					}
				},
				new AnalysisWorker.AnalysisCompletionCallback() {
					@Override
					public void onComplete(List<TranslatableFile> files) {
						onAnalysisComplete(files);
					}
				},
				new AnalysisWorker.AnalysisErrorCallback() {
					@Override
					public void onError(Exception error) {
						onAnalysisError(error);
					}
				});

		worker.execute();
	}

	/** 解析処理が正常に完了したときに呼ばれます。 */
	private void onAnalysisComplete(List<TranslatableFile> files) {
		setButtonsEnabled(true);
		statusPanel.setProgressText(" ");

		analyzedFiles = files;
		unifiedFileTablePanel.updateFileList(files);
		translateButton.setEnabled(true);
		
		// UnifiedFileTablePanelのボタンを有効化
		unifiedFileTablePanel.getExportCsvButton().setEnabled(true);

		JOptionPane.showMessageDialog(this,
				"ファイル解析が完了しました。\n検出されたファイル数: " + files.size(),
				"完了", JOptionPane.INFORMATION_MESSAGE);
	}

	/** 解析処理中にエラーが発生したときに呼ばれます。 */
	private void onAnalysisError(Exception e) {
		setButtonsEnabled(true);
		statusPanel.setProgressText(" ");

		JOptionPane.showMessageDialog(this,
				"解析中にエラーが発生しました: " + e.getMessage(),
				"エラー", JOptionPane.ERROR_MESSAGE);
		e.printStackTrace();
	}

	/** 翻訳処理を開始します。 */
	private void startTranslation() {
		List<TranslatableFile> selectedFiles = unifiedFileTablePanel.getSelectedFiles();

		if (selectedFiles.isEmpty()) {
			JOptionPane.showMessageDialog(this,
					"翻訳対象のファイルを選択してください。",
					"エラー", JOptionPane.ERROR_MESSAGE);
			return;
		}

		Properties settings = SettingsDialog.getStoredSettings();
		String apiKey = settings.getProperty(
				translationService.getProvider().name().toLowerCase() + ".apikey", "");

		if (apiKey.isEmpty()) {
			int result = JOptionPane.showConfirmDialog(this,
					"選択した翻訳プロバイダーのAPIキーが設定されていません。\n設定画面を開きますか?",
					"APIキー未設定", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

			if (result == JOptionPane.YES_OPTION) {
				openSettings();
			}
			return;
		}

		setButtonsEnabled(false);
		translateButton.setEnabled(false);

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
						statusPanel.setProgressText(progress);
					}
				},
				new SelectiveTranslationWorker.TranslationCompletionCallback() {
					@Override
					public void onComplete(List<ModProcessingResult> results) {
						onTranslationComplete(results);
					}
				},
				new SelectiveTranslationWorker.TranslationErrorCallback() {
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
				unifiedFileTablePanel.updateFileState(file);
				}
				});
				}
				});

		worker.execute();
	}

	/** 翻訳処理が正常に完了したときに呼ばれます。 */
	private void onTranslationComplete(List<ModProcessingResult> results) {
		setButtonsEnabled(true);
		translateButton.setEnabled(true);
		statusPanel.setProgressText(" ");

		processingResults = results;
		
		// UnifiedFileTablePanelのボタンを有効化
		unifiedFileTablePanel.getCompareButton().setEnabled(true);

		JOptionPane.showMessageDialog(this,
				"翻訳が完了しました。\n出力先: output/",
				"完了", JOptionPane.INFORMATION_MESSAGE);
	}

	/** 翻訳処理中にエラーが発生したときに呼ばれます。 */
	private void onTranslationError(Exception e) {
		setButtonsEnabled(true);
		translateButton.setEnabled(true);
		statusPanel.setProgressText(" ");

		JOptionPane.showMessageDialog(this,
				"エラーが発生しました: " + e.getMessage(),
				"エラー", JOptionPane.ERROR_MESSAGE);
		e.printStackTrace();
	}

	/** ボタンと入力パネルの有効/無効を切り替えます。 */
	private void setButtonsEnabled(boolean enabled) {
		analyzeButton.setEnabled(enabled);
		settingsButton.setEnabled(enabled);
		inputPanel.setEnabled(enabled);
	}

	/** 処理結果をCSVファイルにエクスポートします。 */
	private void exportCsv() {
		if (analyzedFiles == null || analyzedFiles.isEmpty()) {
			JOptionPane.showMessageDialog(this,
					"エクスポートするデータがありません。\nファイル解析を実行してください。",
					"エラー", JOptionPane.ERROR_MESSAGE);
			return;
		}

		try {
			CsvExporter exporter = new CsvExporter();
			String outputPath = exporter.exportTranslatableFiles(analyzedFiles);

			JOptionPane.showMessageDialog(this,
					"CSVファイルをエクスポートしました。\n" + outputPath,
					"成功", JOptionPane.INFORMATION_MESSAGE);

			logPanel.appendLog("\n[CSV出力] " + outputPath);
		} catch (Exception e) {
			JOptionPane.showMessageDialog(this,
					"CSVエクスポート中にエラーが発生しました: " + e.getMessage(),
					"エラー", JOptionPane.ERROR_MESSAGE);
			e.printStackTrace();
		}
	}

	/** 翻訳前後のファイルを比較します。 */
	private void compareTranslation() {
		List<TranslatableFile> selectedFiles = unifiedFileTablePanel.getSelectedFiles();

		if (selectedFiles.isEmpty()) {
			JOptionPane.showMessageDialog(this,
					"比較対象のファイルを選択してください。",
					"エラー", JOptionPane.ERROR_MESSAGE);
			return;
		}

		if (selectedFiles.size() != 1) {
			JOptionPane.showMessageDialog(this,
					"比較は1つのファイルのみ選択してください。",
					"エラー", JOptionPane.ERROR_MESSAGE);
			return;
		}

		TranslatableFile selectedFile = selectedFiles.get(0);
		
		// workフォルダ内の原文ファイルパスを取得
		String workFilePath = selectedFile.getWorkFilePath();
		if (workFilePath == null || workFilePath.isEmpty()) {
			JOptionPane.showMessageDialog(this,
					"workフォルダに原文ファイルが見つかりません。\nファイル解析を再実行してください。",
					"エラー", JOptionPane.ERROR_MESSAGE);
			return;
		}
		
		File originalFile = new File(workFilePath);
		if (!originalFile.exists()) {
			JOptionPane.showMessageDialog(this,
					"workフォルダの原文ファイルが見つかりません: " + workFilePath + "\nファイル解析を再実行してください。",
					"エラー", JOptionPane.ERROR_MESSAGE);
			return;
		}
		
		// 翻訳先ファイルのパスをファイルタイプごとに決定
		File translatedFile;
		switch (selectedFile.getFileType()) {
			case MOD_LANG_FILE:
				// Mod言語ファイル: output/resourcepacks/MyJPpack/assets/modid/lang/ja_jp.json
				translatedFile = new File("output/resourcepacks/MyJPpack/assets/" + selectedFile.getFileId() + "/lang/ja_jp.json");
				break;
			case KUBEJS_LANG_FILE:
				// KubeJS言語ファイル: output/kubejs/assets/fileId/lang/ja_jp.json
				translatedFile = new File("output/kubejs/assets/" + selectedFile.getFileId() + "/lang/ja_jp.json");
				break;
			case QUEST_LANG_FILE:
				// Quest言語ファイル: output/config/ftbquests/quests/lang/ja_jp.snbt
				translatedFile = new File("output", selectedFile.getLangFolderPath().replace("en_us.snbt", "ja_jp.snbt"));
				break;
			case QUEST_FILE:
				// Questファイル: output/config/ftbquests/quests/*.snbt
				translatedFile = new File("output", selectedFile.getLangFolderPath());
				break;
			default:
				JOptionPane.showMessageDialog(this,
						"未対応のファイルタイプです。",
						"エラー", JOptionPane.ERROR_MESSAGE);
				return;
		}

		if (!translatedFile.exists()) {
			JOptionPane.showMessageDialog(this,
					"翻訳済みファイルが見つかりません。\n" +
					"翻訳を実行してください。\n\n" +
					"翻訳先パス: " + translatedFile.getAbsolutePath(),
					"エラー", JOptionPane.ERROR_MESSAGE);
			return;
		}

		try {
			// 比較対象ファイルのパスをログ出力
			logPanel.appendLog("\n=== 翻訳比較開始 ===");
			logPanel.appendLog("比較元ファイル: " + originalFile.getAbsolutePath());
			logPanel.appendLog("比較先ファイル: " + translatedFile.getAbsolutePath());
			
			TranslationComparator comparator = new TranslationComparator();
			List<ComparisonResult> results = comparator.compare(originalFile, translatedFile);

			ComparisonDialog dialog = new ComparisonDialog(this);
			dialog.showResults(results);
			dialog.setVisible(true);

			logPanel.appendLog("[比較完了]");
		} catch (Exception e) {
			JOptionPane.showMessageDialog(this,
					"比較中にエラーが発生しました: " + e.getMessage(),
					"エラー", JOptionPane.ERROR_MESSAGE);
			e.printStackTrace();
		}
	}
	
	/**
	 * 翻訳比較処理を実行します（UnifiedFileTablePanelから呼び出される）。
	 */
	public void handleCompareTranslation() {
		compareTranslation();
	}
	
	/**
	 * CSVエクスポート処理を実行します（UnifiedFileTablePanelから呼び出される）。
	 */
	public void handleExportCsv() {
		exportCsv();
	}

	/**
	 * アプリケーションのエントリーポイント。
	 * @param args コマンドライン引数
	 */
	public static void main(String[] args) {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				try {
					UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
				} catch (Exception e) {
					e.printStackTrace();
				}
				new ModPackTranslatorGUI().setVisible(true);
			}
		});
	}
}
