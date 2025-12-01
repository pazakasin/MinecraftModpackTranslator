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

import io.github.pazakasin.minecraft.modpack.translator.controller.ui.*;
import io.github.pazakasin.minecraft.modpack.translator.model.ModProcessingResult;
import io.github.pazakasin.minecraft.modpack.translator.model.TranslatableFile;
import io.github.pazakasin.minecraft.modpack.translator.service.TranslationService;
import io.github.pazakasin.minecraft.modpack.translator.service.callback.*;
import io.github.pazakasin.minecraft.modpack.translator.util.CsvExporter;

/**
 * Minecraft ModPack日本語翻訳ツールのメインGUIクラス。
 * ファイル解析、選択、翻訳実行、結果表示、CSVエクスポート機能を提供。
 */
public class ModPackTranslatorGUI extends JFrame {
    /** ディレクトリ選択用の入力パネル。 */
    private InputPanel inputPanel;
    
    /** ステータス情報を表示するパネル。 */
    private StatusPanel statusPanel;
    
    /** ファイル選択UIパネル。 */
    private FileSelectionPanel fileSelectionPanel;
    
    /** 処理結果を表形式で表示するパネル。 */
    private ResultTablePanel resultTablePanel;
    
    /** ログメッセージを表示するパネル。 */
    private LogPanel logPanel;
    
    /** 設定ダイアログを開くボタン。 */
    private JButton settingsButton;
    
    /** ファイル解析を開始するボタン。 */
    private JButton analyzeButton;
    
    /** 翻訳処理を開始するボタン。 */
    private JButton translateButton;
    
    /** 処理結果をCSVに出力するボタン。 */
    private JButton exportCsvButton;
    
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
        
        fileSelectionPanel = new FileSelectionPanel();
        resultTablePanel = new ResultTablePanel();
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
        
        // ファイル選択パネルと結果表示を上下に分割
        JSplitPane upperSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                fileSelectionPanel, resultTablePanel);
        upperSplitPane.setResizeWeight(0.5);
        
        // 上部とログを上下に分割
        JSplitPane lowerSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                upperSplitPane, logPanel);
        lowerSplitPane.setResizeWeight(0.7);
        
        mainPanel.add(topPanel, BorderLayout.NORTH);
        mainPanel.add(lowerSplitPane, BorderLayout.CENTER);
        
        add(mainPanel);
        
        // 初期状態では翻訳ボタンを無効化
        translateButton.setEnabled(false);
    }
    
    /** 操作ボタンを含むパネルを作成します。 */
    private JPanel createButtonPanel() {
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
        
        settingsButton = new JButton("⚙ 設定");
        settingsButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                openSettings();
            }
        });
        
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
        
        exportCsvButton = new JButton("CSVエクスポート");
        exportCsvButton.setEnabled(false);
        exportCsvButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                exportCsv();
            }
        });
        
        buttonPanel.add(settingsButton);
        buttonPanel.add(analyzeButton);
        buttonPanel.add(translateButton);
        buttonPanel.add(exportCsvButton);
        
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
        
        fileSelectionPanel.clearTable();
        resultTablePanel.clearTable();
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
            }
        );
        
        worker.execute();
    }
    
    /** 解析処理が正常に完了したときに呼ばれます。 */
    private void onAnalysisComplete(List<TranslatableFile> files) {
        setButtonsEnabled(true);
        statusPanel.setProgressText(" ");
        
        analyzedFiles = files;
        fileSelectionPanel.updateFileList(files);
        translateButton.setEnabled(true);
        
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
        List<TranslatableFile> selectedFiles = fileSelectionPanel.getSelectedFiles();
        
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
        
        resultTablePanel.clearTable();
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
            }
        );
        
        worker.execute();
    }
    
    /** 翻訳処理が正常に完了したときに呼ばれます。 */
    private void onTranslationComplete(List<ModProcessingResult> results) {
        setButtonsEnabled(true);
        translateButton.setEnabled(true);
        statusPanel.setProgressText(" ");
        
        processingResults = results;
        resultTablePanel.updateTable(results);
        exportCsvButton.setEnabled(true);
        
        JOptionPane.showMessageDialog(this,
                "翻訳が完了しました。\n出力先: output/MyJPpack/",
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
            
            logPanel.appendLog("\n[CSV出力] " + outputPath);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "CSVエクスポート中にエラーが発生しました: " + e.getMessage(),
                    "エラー", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
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
