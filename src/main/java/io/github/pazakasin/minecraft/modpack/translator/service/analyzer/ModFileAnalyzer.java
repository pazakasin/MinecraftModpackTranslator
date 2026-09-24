package io.github.pazakasin.minecraft.modpack.translator.service.analyzer;

import io.github.pazakasin.minecraft.modpack.translator.model.TranslatableFile;
import io.github.pazakasin.minecraft.modpack.translator.service.callback.LogCallback;
import io.github.pazakasin.minecraft.modpack.translator.service.callback.ProgressUpdateCallback;
import io.github.pazakasin.minecraft.modpack.translator.service.processor.CharacterCounter;
import io.github.pazakasin.minecraft.modpack.translator.service.processor.JarFileAnalyzer;
import io.github.pazakasin.minecraft.modpack.translator.service.processor.LanguageFileInfo;
import io.github.pazakasin.minecraft.modpack.translator.service.processor.NamespaceUsageReport;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.List;

/**
 * Mod JARファイルの解析を担当するクラス。
 * modsフォルダ内のJARファイルから言語ファイルをnamespace単位で検出。
 */
public class ModFileAnalyzer {
    /** ログコールバック。 */
    private final LogCallback logger;
    
    /** 進捗コールバック。 */
    private final ProgressUpdateCallback progressUpdater;
    
    /** JARファイルアナライザー。 */
    private final JarFileAnalyzer jarAnalyzer;
    
    /** 文字数カウンター。 */
    private final CharacterCounter charCounter;
    
    /**
     * ModFileAnalyzerのコンストラクタ。
     * @param logger ログコールバック
     * @param progressUpdater 進捗コールバック
     */
    public ModFileAnalyzer(LogCallback logger, ProgressUpdateCallback progressUpdater) {
        this.logger = logger;
        this.progressUpdater = progressUpdater;
        this.jarAnalyzer = new JarFileAnalyzer();
        this.charCounter = new CharacterCounter();
    }
    
    /**
     * Modファイル（JARファイル）を解析します。
     * @param inputPath ModPackディレクトリパス
     * @return 翻訳対象ファイルのリスト
     * @throws Exception ファイルアクセスエラー等
     */
    public List<TranslatableFile> analyze(String inputPath) throws Exception {
        List<TranslatableFile> files = new ArrayList<TranslatableFile>();
        
        File modsDir = new File(inputPath, "mods");
        File[] jarFiles = modsDir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(".jar");
            }
        });
        
        if (jarFiles == null || jarFiles.length == 0) {
            log("modsフォルダ内にJARファイルが見つかりません。");
            return files;
        }
        
        log("検出されたMod数: " + jarFiles.length);
        log("");
        
        NamespaceUsageReport report = new NamespaceUsageReport();
        
        for (int i = 0; i < jarFiles.length; i++) {
            File jarFile = jarFiles[i];
            int currentModNum = i + 1;
            int totalMods = jarFiles.length;
            
            updateProgress(String.format("[%d/%d] 解析中: %s", 
                currentModNum, totalMods, jarFile.getName()));
            
            try {
                List<TranslatableFile> jarResults = analyzeJar(jarFile);
                if (jarResults.isEmpty()) {
                    log(String.format("[%d/%d] %s - en_us.jsonなし", 
                        currentModNum, totalMods, jarFile.getName()));
                }
                for (TranslatableFile file : jarResults) {
                    files.add(file);
                    report.add(jarFile.getName(), file.getFileId(), file.getCharacterCount());
                    log(String.format("[%d/%d] %s - %s (%d文字)", 
                        currentModNum, totalMods, file.getModName(),
                        file.isHasExistingJaJp() ? "既存ja_jp有" : "翻訳対象",
                        file.getCharacterCount()));
                }
            } catch (Exception e) {
                log(String.format("[%d/%d][エラー] %s: %s", 
                    currentModNum, totalMods, jarFile.getName(), e.getMessage()));
                logStackTrace(e);
            }
        }
        
        report.writeTo(logger);
        updateProgress(" ");
        return files;
    }
    
    /**
     * 単一のMod JARファイルを解析します。en_us.jsonを持つnamespaceごとに1件生成します。
     * @param jarFile JARファイル
     * @return 翻訳対象ファイルのリスト（en_us.jsonがなければ空）
     * @throws Exception 解析エラー
     */
    private List<TranslatableFile> analyzeJar(File jarFile) throws Exception {
        List<LanguageFileInfo> infos = jarAnalyzer.analyze(jarFile);
        List<TranslatableFile> result = new ArrayList<TranslatableFile>();
        int enUsCount = JarFileAnalyzer.countEnUs(infos);
        String baseName = jarFile.getName().replace(".jar", "");
        
        for (LanguageFileInfo langInfo : infos) {
            if (langInfo.enUsContent == null) {
                continue;
            }
            int charCount = charCounter.countCharacters(langInfo.enUsContent);
            result.add(TranslatableFile.createModLangFile(
                JarFileAnalyzer.buildDisplayName(baseName, langInfo.modId, enUsCount),
                jarFile.getAbsolutePath(),
                langInfo.langFolderPath,
                langInfo.modId,
                charCount,
                langInfo.hasJaJp,
                langInfo.enUsContent,
                langInfo.jaJpContent
            ));
        }
        return result;
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
     * スタックトレースをログ出力します。
     * @param e 例外オブジェクト
     */
    private void logStackTrace(Exception e) {
        if (logger != null) {
            java.io.StringWriter sw = new java.io.StringWriter();
            java.io.PrintWriter pw = new java.io.PrintWriter(sw);
            e.printStackTrace(pw);
            logger.onLog(sw.toString());
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
