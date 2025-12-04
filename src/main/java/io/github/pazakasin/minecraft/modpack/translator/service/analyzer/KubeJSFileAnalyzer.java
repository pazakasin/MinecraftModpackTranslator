package io.github.pazakasin.minecraft.modpack.translator.service.analyzer;

import io.github.pazakasin.minecraft.modpack.translator.model.TranslatableFile;
import io.github.pazakasin.minecraft.modpack.translator.service.callback.LogCallback;
import io.github.pazakasin.minecraft.modpack.translator.service.callback.ProgressUpdateCallback;
import io.github.pazakasin.minecraft.modpack.translator.service.processor.CharacterCounter;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * KubeJS言語ファイルの解析を担当するクラス。
 * kubejs/assets下の言語ファイルを検出。
 */
public class KubeJSFileAnalyzer {
    /** ログコールバック。 */
    private final LogCallback logger;
    
    /** 進捗コールバック。 */
    private final ProgressUpdateCallback progressUpdater;
    
    /** 文字数カウンター。 */
    private final CharacterCounter charCounter;
    
    /**
     * KubeJSFileAnalyzerのコンストラクタ。
     * @param logger ログコールバック
     * @param progressUpdater 進捗コールバック
     */
    public KubeJSFileAnalyzer(LogCallback logger, ProgressUpdateCallback progressUpdater) {
        this.logger = logger;
        this.progressUpdater = progressUpdater;
        this.charCounter = new CharacterCounter();
    }
    
    /**
     * KubeJS言語ファイルを解析します。
     * @param inputPath ModPackディレクトリパス
     * @return 翻訳対象ファイルのリスト
     * @throws Exception ファイルアクセスエラー等
     */
    public List<TranslatableFile> analyze(String inputPath) throws Exception {
        List<TranslatableFile> files = new ArrayList<TranslatableFile>();
        
        File kubeJsDir = new File(inputPath, "kubejs/assets");
        if (!kubeJsDir.exists() || !kubeJsDir.isDirectory()) {
            log("");
            log("kubejs/assetsフォルダが見つかりません。");
            return files;
        }
        
        List<File> langFiles = findLangFiles(kubeJsDir);
        
        if (langFiles.isEmpty()) {
            log("");
            log("KubeJS言語ファイルが見つかりません。");
            return files;
        }
        
        log("");
        log("検出されたKubeJS言語ファイル数: " + langFiles.size());
        log("");
        
        for (int i = 0; i < langFiles.size(); i++) {
            File langFile = langFiles.get(i);
            int currentNum = i + 1;
            int totalNum = langFiles.size();
            
            updateProgress(String.format("[KubeJS %d/%d] 解析中: %s", 
                currentNum, totalNum, extractFileId(langFile)));
            
            try {
                TranslatableFile file = analyzeFile(langFile);
                if (file != null) {
                    files.add(file);
                    log(String.format("[KubeJS %d/%d] %s - %s (%d文字)", 
                        currentNum, totalNum, file.getFileId(),
                        file.isHasExistingJaJp() ? "既存ja_jp有" : "翻訳対象",
                        file.getCharacterCount()));
                }
            } catch (Exception e) {
                log(String.format("[KubeJS %d/%d][エラー] %s: %s", 
                    currentNum, totalNum, extractFileId(langFile), e.getMessage()));
            }
        }
        
        updateProgress(" ");
        return files;
    }
    
    /**
     * kubejs/assets下から言語ファイルを再帰的に検索します。
     * @param assetsDir assetsディレクトリ
     * @return 言語ファイルのリスト
     */
    private List<File> findLangFiles(File assetsDir) {
        List<File> langFiles = new ArrayList<File>();
        findLangFilesRecursive(assetsDir, langFiles);
        return langFiles;
    }
    
    /**
     * 再帰的に言語ファイルを検索します。
     * @param dir ディレクトリ
     * @param result 結果リスト
     */
    private void findLangFilesRecursive(File dir, List<File> result) {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        
        for (File file : files) {
            if (file.isDirectory()) {
                findLangFilesRecursive(file, result);
            } else if (file.getName().equals("en_us.json")) {
                String path = file.getAbsolutePath().replace("\\", "/");
                if (path.contains("/lang/en_us.json")) {
                    result.add(file);
                }
            }
        }
    }
    
    /**
     * 単一のKubeJS言語ファイルを解析します。
     * @param langFile 言語ファイル
     * @return 翻訳対象ファイル
     * @throws Exception 解析エラー
     */
    private TranslatableFile analyzeFile(File langFile) throws Exception {
        String enUsContent = new String(Files.readAllBytes(langFile.toPath()), "UTF-8");
        int charCount = charCounter.countCharacters(enUsContent);
        
        if (charCount == 0) {
            return null;
        }
        
        String fileId = extractFileId(langFile);
        
        File jaJpFile = new File(langFile.getParent(), "ja_jp.json");
        boolean hasJaJp = jaJpFile.exists();
        String jaJpContent = null;
        
        if (hasJaJp) {
            jaJpContent = new String(Files.readAllBytes(jaJpFile.toPath()), "UTF-8");
        }
        
        return TranslatableFile.createKubeJSLangFile(
            langFile.getAbsolutePath(),
            fileId,
            charCount,
            hasJaJp,
            enUsContent,
            jaJpContent
        );
    }
    
    /**
     * KubeJS言語ファイルからIDを抽出します。
     * @param langFile 言語ファイル
     * @return ファイルID
     */
    private String extractFileId(File langFile) {
        String path = langFile.getAbsolutePath().replace("\\", "/");
        int assetsIndex = path.indexOf("/assets/");
        int langIndex = path.indexOf("/lang/");
        
        if (assetsIndex != -1 && langIndex != -1 && assetsIndex < langIndex) {
            String idPart = path.substring(assetsIndex + 8, langIndex);
            return idPart;
        }
        
        return "unknown";
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
     * 進捗メッセージを出力します。
     * @param message 進捗メッセージ
     */
    private void updateProgress(String message) {
        if (progressUpdater != null) {
            progressUpdater.onProgressUpdate(message);
        }
    }
}
