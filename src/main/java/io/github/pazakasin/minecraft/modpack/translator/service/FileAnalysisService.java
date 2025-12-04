package io.github.pazakasin.minecraft.modpack.translator.service;

import io.github.pazakasin.minecraft.modpack.translator.model.TranslatableFile;
import io.github.pazakasin.minecraft.modpack.translator.service.callback.LogCallback;
import io.github.pazakasin.minecraft.modpack.translator.service.callback.ProgressUpdateCallback;
import io.github.pazakasin.minecraft.modpack.translator.service.analyzer.ModFileAnalyzer;
import io.github.pazakasin.minecraft.modpack.translator.service.analyzer.KubeJSFileAnalyzer;
import io.github.pazakasin.minecraft.modpack.translator.service.analyzer.QuestFileAnalyzer;
import io.github.pazakasin.minecraft.modpack.translator.service.analyzer.WorkFolderExporter;
import io.github.pazakasin.minecraft.modpack.translator.service.backup.BackupManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 翻訳対象ファイルの洗い出しを行うサービスクラス。
 * ModPack内のすべての翻訳可能なファイルを検出し、TranslatableFileのリストを返す。
 */
public class FileAnalysisService {
    /** ログメッセージを出力するコールバック。 */
    private final LogCallback logger;
    
    /** 進捗状況を更新するコールバック。 */
    private final ProgressUpdateCallback progressUpdater;
    
    /** Modファイルアナライザー。 */
    private final ModFileAnalyzer modAnalyzer;
    
    /** KubeJSファイルアナライザー。 */
    private final KubeJSFileAnalyzer kubeJsAnalyzer;
    
    /** クエストファイルアナライザー。 */
    private final QuestFileAnalyzer questAnalyzer;
    
    /** workフォルダエクスポーター。 */
    private final WorkFolderExporter workExporter;
    
    /**
     * FileAnalysisServiceのコンストラクタ。
     * @param logger ログコールバック
     * @param progressUpdater 進捗コールバック
     */
    public FileAnalysisService(LogCallback logger, ProgressUpdateCallback progressUpdater) {
        this.logger = logger;
        this.progressUpdater = progressUpdater;
        this.modAnalyzer = new ModFileAnalyzer(logger, progressUpdater);
        this.kubeJsAnalyzer = new KubeJSFileAnalyzer(logger, progressUpdater);
        this.questAnalyzer = new QuestFileAnalyzer(logger, progressUpdater);
        this.workExporter = new WorkFolderExporter(logger);
    }
    
    /**
     * ModPack全体の翻訳対象ファイルを解析します。
     * @param inputPath ModPackディレクトリパス
     * @return 翻訳対象ファイルのリスト
     * @throws Exception ファイルアクセスエラー等
     */
    public List<TranslatableFile> analyzeFiles(String inputPath) throws Exception {
        List<TranslatableFile> files = new ArrayList<TranslatableFile>();
        
        clearWorkFolder();
        clearOutputFolder();
        
        log("=== ファイル解析開始 ===");
        
        List<TranslatableFile> questFiles = questAnalyzer.analyze(inputPath);
        files.addAll(questFiles);
        
        List<TranslatableFile> kubeJsFiles = kubeJsAnalyzer.analyze(inputPath);
        files.addAll(kubeJsFiles);
        
        List<TranslatableFile> modFiles = modAnalyzer.analyze(inputPath);
        files.addAll(modFiles);
        
        workExporter.export(files);
        
        int totalCharCount = 0;
        int selectedCharCount = 0;
        for (TranslatableFile file : files) {
            totalCharCount += file.getCharacterCount();
            if (file.isSelected()) {
                selectedCharCount += file.getCharacterCount();
            }
        }
        
        log("");
        log("=== 解析完了 ===");
        log("検出されたファイル数: " + files.size());
        log("合計翻訳対象文字数: " + totalCharCount);
        log("選択済み文字数: " + selectedCharCount);
        log("元ファイル出力先: work/");
        
        backupWorkFolder(inputPath);
        
        return files;
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
     * work フォルダをクリアします。
     */
    private void clearWorkFolder() {
        File workDir = new File("work");
        if (workDir.exists()) {
            log("work フォルダをクリア中...");
            deleteDirectory(workDir);
        }
    }
    
    /**
     * output フォルダをクリアします。
     */
    private void clearOutputFolder() {
        File outputDir = new File("output");
        if (outputDir.exists()) {
            log("output フォルダをクリア中...");
            deleteDirectory(outputDir);
        }
    }
    
    /**
     * ディレクトリを再帰的に削除します。
     * @param directory 削除対象ディレクトリ
     */
    private void deleteDirectory(File directory) {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }
        directory.delete();
    }
    
    /**
     * work フォルダをバックアップします。
     * @param inputPath ModPackディレクトリパス
     */
    private void backupWorkFolder(String inputPath) {
        try {
            File inputDir = new File(inputPath);
            String modpackName = inputDir.getName();
            
            BackupManager backupManager = new BackupManager();
            BackupManager.ZipResult zipResult = backupManager.zipWorkFolder(modpackName);
            
            if (zipResult != null) {
                log("");
                log("=== workフォルダバックアップ ===");
                log("圧縮ファイル: " + zipResult.zipPath);
                log("圧縮ファイル数: " + zipResult.fileCount);
            } else {
                log("");
                log("同名のバックアップファイルが存在するため、スキップしました。");
            }
        } catch (Exception e) {
            log("workフォルダのバックアップに失敗しました: " + e.getMessage());
        }
    }
}
