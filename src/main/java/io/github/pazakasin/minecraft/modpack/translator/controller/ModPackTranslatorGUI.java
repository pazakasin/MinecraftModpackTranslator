package io.github.pazakasin.minecraft.modpack.translator.controller;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.File;
import java.util.List;
import java.util.Properties;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.table.DefaultTableModel;

import io.github.pazakasin.minecraft.modpack.translator.model.ModProcessingResult;
import io.github.pazakasin.minecraft.modpack.translator.service.ModPackProcessor;
import io.github.pazakasin.minecraft.modpack.translator.service.TranslationService;
import io.github.pazakasin.minecraft.modpack.translator.util.CsvExporter;

public class ModPackTranslatorGUI extends JFrame {
	private JTextField inputPathField;
	private JButton browseButton;
	private JButton settingsButton;
	private JButton translateButton;
	private JButton exportCsvButton;
	private JLabel statusLabel;
	private JLabel progressLabel;
	private JTextArea logArea;
	private JProgressBar progressBar;
	private JTable resultTable;
	private DefaultTableModel tableModel;
	private TranslationService translationService;
	private List<ModProcessingResult> processingResults;

	public ModPackTranslatorGUI() {
		setTitle("Minecraft ModPack 日本語翻訳ツール v2.0");
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setSize(1200, 700);
		setLocationRelativeTo(null);

		translationService = new TranslationService();
		loadSettings();

		initComponents();
	}

	private void loadSettings() {
		Properties settings = SettingsDialog.getStoredSettings();

		String providerName = settings.getProperty("provider", "GOOGLE");
		try {
			TranslationService.TranslationProvider provider =
					TranslationService.TranslationProvider.valueOf(providerName);
			translationService.setProvider(provider);

			String apiKey = settings.getProperty(provider.name().toLowerCase() + ".apikey", "");
			translationService.setApiKey(apiKey);
		} catch (IllegalArgumentException e) {
			// デフォルト設定を使用
		}
	}

	private void initComponents() {
		JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
		mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

		// 入力パネル
		JPanel inputPanel = new JPanel(new BorderLayout(5, 5));
		inputPanel.setBorder(BorderFactory.createTitledBorder("ModPackディレクトリ"));

		inputPathField = new JTextField();
		browseButton = new JButton("参照...");
		browseButton.addActionListener(e -> browseDirectory());

		inputPanel.add(inputPathField, BorderLayout.CENTER);
		inputPanel.add(browseButton, BorderLayout.EAST);

		// ステータスパネル
		JPanel statusPanel = new JPanel(new BorderLayout());

		JPanel providerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		statusLabel = new JLabel("翻訳プロバイダー: " +
				translationService.getProvider().getDisplayName());
		statusLabel.setFont(new Font("Dialog", Font.BOLD, 12));
		providerPanel.add(statusLabel);

		progressLabel = new JLabel(" ");
		progressLabel.setFont(new Font("Dialog", Font.PLAIN, 11));
		progressLabel.setForeground(new Color(60, 120, 180));

		statusPanel.add(providerPanel, BorderLayout.WEST);
		statusPanel.add(progressLabel, BorderLayout.EAST);

		// 実行ボタンパネル
		JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));

		settingsButton = new JButton("⚙ 設定");
		settingsButton.addActionListener(e -> openSettings());

		translateButton = new JButton("翻訳開始");
		translateButton.setFont(new Font("Dialog", Font.BOLD, 14));
		translateButton.addActionListener(e -> startTranslation());

		exportCsvButton = new JButton("CSVエクスポート");
		exportCsvButton.setEnabled(false);
		exportCsvButton.addActionListener(e -> exportCsv());

		buttonPanel.add(settingsButton);
		buttonPanel.add(translateButton);
		buttonPanel.add(exportCsvButton);

		// プログレスバー
		progressBar = new JProgressBar();
		progressBar.setStringPainted(true);

		// 結果テーブル
		String[] columnNames = {
				"Mod名", "言語フォルダパス", "英語ファイル",
				"日本語ファイル", "翻訳実行", "文字数", "翻訳結果"
		};
		tableModel = new DefaultTableModel(columnNames, 0) {
			@Override
			public boolean isCellEditable(int row, int column) {
				return false;
			}
		};
		resultTable = new JTable(tableModel);
		resultTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
		resultTable.getColumnModel().getColumn(0).setPreferredWidth(200);
		resultTable.getColumnModel().getColumn(1).setPreferredWidth(250);
		JScrollPane tableScrollPane = new JScrollPane(resultTable);
		tableScrollPane.setBorder(BorderFactory.createTitledBorder("処理結果"));

		// ログエリア
		logArea = new JTextArea(8, 0);
		logArea.setEditable(false);
		logArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
		JScrollPane logScrollPane = new JScrollPane(logArea);
		logScrollPane.setBorder(BorderFactory.createTitledBorder("処理ログ"));

		// 分割パネル（テーブルとログ）
		JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
				tableScrollPane, logScrollPane);
		splitPane.setResizeWeight(0.6);

		// パネルの配置
		JPanel topPanel = new JPanel(new BorderLayout(5, 5));
		topPanel.add(inputPanel, BorderLayout.NORTH);

		JPanel middlePanel = new JPanel(new BorderLayout(5, 5));
		middlePanel.add(statusPanel, BorderLayout.NORTH);
		middlePanel.add(buttonPanel, BorderLayout.CENTER);
		middlePanel.add(progressBar, BorderLayout.SOUTH);

		topPanel.add(middlePanel, BorderLayout.CENTER);

		mainPanel.add(topPanel, BorderLayout.NORTH);
		mainPanel.add(splitPane, BorderLayout.CENTER);

		add(mainPanel);
	}

	private void openSettings() {
		SettingsDialog dialog = new SettingsDialog(this);
		dialog.setVisible(true);

		if (dialog.isSaved()) {
			loadSettings();
			statusLabel.setText("翻訳プロバイダー: " +
					translationService.getProvider().getDisplayName());
		}
	}

	private void browseDirectory() {
		JFileChooser chooser = new JFileChooser();
		chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		chooser.setDialogTitle("ModPackディレクトリを選択");

		if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
			inputPathField.setText(chooser.getSelectedFile().getAbsolutePath());
		}
	}

	private void startTranslation() {
		String inputPath = inputPathField.getText().trim();

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

		tableModel.setRowCount(0);

		translateButton.setEnabled(false);
		browseButton.setEnabled(false);
		settingsButton.setEnabled(false);
		exportCsvButton.setEnabled(false);
		logArea.setText("");

		SwingWorker<List<ModProcessingResult>, String> worker =
				new SwingWorker<List<ModProcessingResult>, String>() {
					@Override
					protected List<ModProcessingResult> doInBackground() throws Exception {
						ModPackProcessor processor = new ModPackProcessor(
								inputPath, translationService,
								new Consumer<String>() {
									@Override
									public void accept(String message) {
										publish(message);
									}
								},
								new Consumer<Integer>() {
									@Override
									public void accept(Integer progress) {
										// プログレスバー更新用（未使用だが引数として必要）
									}
								});
						return processor.process();
					}

					@Override
					protected void process(java.util.List<String> chunks) {
						for (String message : chunks) {
							if (message.startsWith("PROGRESS:")) {
								progressLabel.setText(message.substring(9));
							} else {
								logArea.append(message + "\n");
								logArea.setCaretPosition(logArea.getDocument().getLength());
							}
						}
					}

					@Override
					protected void done() {
						translateButton.setEnabled(true);
						browseButton.setEnabled(true);
						settingsButton.setEnabled(true);
						progressLabel.setText(" ");

						try {
							processingResults = get();
							updateTable(processingResults);
							exportCsvButton.setEnabled(true);

							JOptionPane.showMessageDialog(ModPackTranslatorGUI.this,
									"翻訳が完了しました。\n出力先: output/MyJPpack/",
									"完了", JOptionPane.INFORMATION_MESSAGE);
						} catch (Exception e) {
							JOptionPane.showMessageDialog(ModPackTranslatorGUI.this,
									"エラーが発生しました: " + e.getMessage(),
									"エラー", JOptionPane.ERROR_MESSAGE);
							e.printStackTrace();
						}
					}
				};

		worker.execute();
	}

	private void updateTable(List<ModProcessingResult> results) {
		for (ModProcessingResult result : results) {
			String translationResult;
			if (result.hasJaJp && !result.translated) {
				translationResult = "既存";
			} else if (result.translated && result.translationSuccess) {
				translationResult = "○";
			} else if (result.translated && !result.translationSuccess) {
				translationResult = "×";
			} else {
				translationResult = "-";
			}

			Object[] row = {
					result.modName,
					result.langFolderPath,
					result.hasEnUs ? "○" : "×",
					result.hasJaJp ? "○" : "×",
					result.translated ? "○" : "×",
					result.characterCount,
					translationResult
			};
			tableModel.addRow(row);
		}
	}

	private void exportCsv() {
		if (processingResults == null || processingResults.isEmpty()) {
			JOptionPane.showMessageDialog(this,
					"エクスポートするデータがありません。",
					"エラー", JOptionPane.ERROR_MESSAGE);
			return;
		}

		try {
			CsvExporter exporter = new CsvExporter();
			String outputPath = exporter.export(processingResults);

			JOptionPane.showMessageDialog(this,
					"CSVファイルをエクスポートしました。\n" + outputPath,
					"成功", JOptionPane.INFORMATION_MESSAGE);

			logArea.append("\n[CSV出力] " + outputPath + "\n");
		} catch (Exception e) {
			JOptionPane.showMessageDialog(this,
					"CSVエクスポート中にエラーが発生しました: " + e.getMessage(),
					"エラー", JOptionPane.ERROR_MESSAGE);
			e.printStackTrace();
		}
	}

	public static void main(String[] args) {
		SwingUtilities.invokeLater(() -> {
			try {
				UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
			} catch (Exception e) {
				e.printStackTrace();
			}
			new ModPackTranslatorGUI().setVisible(true);
		});
	}
}