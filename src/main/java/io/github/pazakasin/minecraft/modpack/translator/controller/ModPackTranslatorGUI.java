package io.github.pazakasin.minecraft.modpack.translator.controller;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.List;
import java.util.Properties;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import io.github.pazakasin.minecraft.modpack.translator.controller.handler.AnalysisActionHandler;
import io.github.pazakasin.minecraft.modpack.translator.controller.handler.AnalysisResultCallback;
import io.github.pazakasin.minecraft.modpack.translator.controller.handler.AnalyzedFilesCallback;
import io.github.pazakasin.minecraft.modpack.translator.controller.handler.ButtonStateCallback;
import io.github.pazakasin.minecraft.modpack.translator.controller.handler.ComparisonHandler;
import io.github.pazakasin.minecraft.modpack.translator.controller.handler.SettingsDialogCallback;
import io.github.pazakasin.minecraft.modpack.translator.controller.handler.TranslateButtonCallback;
import io.github.pazakasin.minecraft.modpack.translator.controller.handler.TranslationActionHandler;
import io.github.pazakasin.minecraft.modpack.translator.controller.handler.TranslationResultCallback;
import io.github.pazakasin.minecraft.modpack.translator.controller.ui.InputPanel;
import io.github.pazakasin.minecraft.modpack.translator.controller.ui.LogPanel;
import io.github.pazakasin.minecraft.modpack.translator.controller.ui.StatusPanel;
import io.github.pazakasin.minecraft.modpack.translator.controller.ui.UnifiedFileTablePanel;
import io.github.pazakasin.minecraft.modpack.translator.model.ModProcessingResult;
import io.github.pazakasin.minecraft.modpack.translator.model.TranslatableFile;
import io.github.pazakasin.minecraft.modpack.translator.service.ProviderType;
import io.github.pazakasin.minecraft.modpack.translator.service.TranslationService;

/**
 * Minecraft ModPack日本語翻訳ツールのメインGUIクラス。
 * UIコンポーネントの構築とレイアウト管理を担当。
 */
public class ModPackTranslatorGUI extends JFrame {
	/** 入力パネル。 */
	private InputPanel inputPanel;
	
	/** ステータスパネル。 */
	private StatusPanel statusPanel;
	
	/** ファイルテーブルパネル。 */
	private UnifiedFileTablePanel unifiedFileTablePanel;
	
	/** ログパネル。 */
	private LogPanel logPanel;
	
	/** 設定ボタン。 */
	private JButton settingsButton;
	
	/** 解析ボタン。 */
	private JButton analyzeButton;
	
	/** 翻訳ボタン。 */
	private JButton translateButton;
	

	
	/** 翻訳サービス。 */
	private TranslationService translationService;
	
	/** 処理結果リスト。 */
	private List<ModProcessingResult> processingResults;
	
	/** 解析済みファイルリスト。 */
	private List<TranslatableFile> analyzedFiles;
	
	/** 解析アクションハンドラー。 */
	private AnalysisActionHandler analysisHandler;
	
	/** 翻訳アクションハンドラー。 */
	private TranslationActionHandler translationHandler;
	
	/** 比較・エクスポートハンドラー。 */
	private ComparisonHandler comparisonHandler;
	
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
		initHandlers();
	}
	
	/**
	 * 保存された設定を読み込みます。
	 */
	private void loadSettings() {
		Properties settings = SettingsDialog.getStoredSettings();
		
		String providerName = settings.getProperty("provider", "GOOGLE");
		try {
			ProviderType provider = ProviderType.valueOf(providerName);
			translationService.setProvider(provider);
			
			String apiKey = settings.getProperty(provider.name().toLowerCase() + ".apikey", "");
			translationService.setApiKey(apiKey);
		} catch (IllegalArgumentException e) {
			// デフォルト設定を使用
		}
	}
	
	/**
	 * UIコンポーネントを初期化します。
	 */
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
		
		JPanel topPanel = new JPanel(new BorderLayout(5, 5));
		topPanel.add(inputPanel, BorderLayout.NORTH);
		
		JPanel middlePanel = new JPanel(new BorderLayout(5, 5));
		middlePanel.add(statusPanel, BorderLayout.NORTH);
		middlePanel.add(buttonPanel, BorderLayout.CENTER);
		
		topPanel.add(middlePanel, BorderLayout.CENTER);
		
		JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
				unifiedFileTablePanel, logPanel);
		splitPane.setResizeWeight(0.7);
		
		mainPanel.add(topPanel, BorderLayout.NORTH);
		mainPanel.add(splitPane, BorderLayout.CENTER);
		
		add(mainPanel);
		
		translateButton.setEnabled(false);
	}
	
	/**
	 * ボタンパネルを作成します。
	 * @return ボタンパネル
	 */
	private JPanel createButtonPanel() {
		JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
		
		analyzeButton = new JButton("ファイル解析");
		analyzeButton.setFont(new Font("Dialog", Font.BOLD, 14));
		analyzeButton.addActionListener(e -> analysisHandler.startAnalysis());
		
		translateButton = new JButton("翻訳実行");
		translateButton.setFont(new Font("Dialog", Font.BOLD, 14));
		translateButton.setEnabled(false);
		translateButton.addActionListener(e -> translationHandler.startTranslation());
		
		settingsButton = new JButton("⚙ 設定");
		settingsButton.addActionListener(e -> openSettings());
		
		buttonPanel.add(analyzeButton);
		buttonPanel.add(translateButton);
		buttonPanel.add(settingsButton);
		
		return buttonPanel;
	}
	
	/**
	 * アクションハンドラーを初期化します。
	 */
	private void initHandlers() {
		analysisHandler = new AnalysisActionHandler(
				this, inputPanel, statusPanel, unifiedFileTablePanel, logPanel,
				new ButtonStateCallback() {
					@Override
					public void setButtonsEnabled(boolean enabled) {
						ModPackTranslatorGUI.this.setButtonsEnabled(enabled);
					}
				},
				new TranslateButtonCallback() {
					@Override
					public void setTranslateButtonEnabled(boolean enabled) {
						translateButton.setEnabled(enabled);
					}
				},
				new AnalysisResultCallback() {
					@Override
					public void setAnalyzedFiles(List<TranslatableFile> files) {
						analyzedFiles = files;
					}
				});
		
		translationHandler = new TranslationActionHandler(
				this, inputPanel, statusPanel, unifiedFileTablePanel, logPanel,
				translationService,
				new ButtonStateCallback() {
					@Override
					public void setButtonsEnabled(boolean enabled) {
						ModPackTranslatorGUI.this.setButtonsEnabled(enabled);
					}
				},
				new TranslateButtonCallback() {
					@Override
					public void setTranslateButtonEnabled(boolean enabled) {
						translateButton.setEnabled(enabled);
					}
				},
				new TranslationResultCallback() {
					@Override
					public void setProcessingResults(List<ModProcessingResult> results) {
						processingResults = results;
					}
				},
				new SettingsDialogCallback() {
					@Override
					public void openSettings() {
						ModPackTranslatorGUI.this.openSettings();
					}
				});
		
		comparisonHandler = new ComparisonHandler(
				this, unifiedFileTablePanel, logPanel,
				new AnalyzedFilesCallback() {
					@Override
					public List<TranslatableFile> getAnalyzedFiles() {
						return analyzedFiles;
					}
				});
	}
	
	/**
	 * 設定ダイアログを開きます。
	 */
	private void openSettings() {
		SettingsDialog dialog = new SettingsDialog(this);
		dialog.setVisible(true);
		
		if (dialog.isSaved()) {
			loadSettings();
			statusPanel.setStatusText("翻訳プロバイダー: " +
					translationService.getProvider().getDisplayName());
		}
	}
	
	/**
	 * ボタンの有効/無効を切り替えます。
	 * @param enabled 有効にする場合true
	 */
	private void setButtonsEnabled(boolean enabled) {
		analyzeButton.setEnabled(enabled);
		settingsButton.setEnabled(enabled);
		inputPanel.setEnabled(enabled);
	}
	
	/**
	 * 翻訳比較を実行します（UnifiedFileTablePanelから呼び出される）。
	 */
	public void handleCompareTranslation() {
		comparisonHandler.compareTranslation();
	}
	
	/**
	 * CSVエクスポートを実行します（UnifiedFileTablePanelから呼び出される）。
	 */
	public void handleExportCsv() {
		comparisonHandler.exportCsv();
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
