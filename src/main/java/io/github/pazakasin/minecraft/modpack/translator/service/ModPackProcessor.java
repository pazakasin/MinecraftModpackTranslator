package io.github.pazakasin.minecraft.modpack.translator.service;

import io.github.pazakasin.minecraft.modpack.translator.model.ModProcessingResult;
import io.github.pazakasin.minecraft.modpack.translator.model.QuestTranslationResult;
import io.github.pazakasin.minecraft.modpack.translator.service.processor.*;
import io.github.pazakasin.minecraft.modpack.translator.service.callback.*;
import io.github.pazakasin.minecraft.modpack.translator.service.quest.QuestFileProcessor;
import java.io.*;
import java.util.*;

/**
 * Minecraft ModPackの翻訳処理を統括管理するクラス。
 * 指定ディレクトリ内のすべてのModを解析し、en_us.jsonをja_jp.jsonに翻訳。
 */
public class ModPackProcessor {
    /** 処理対象のModPackディレクトリパス。modsフォルダ内のJARを処理。 */
    private final String inputPath;
    
    /** 翻訳処理を実行するサービス。 */
    private final TranslationService translationService;
    
    /** ログメッセージを出力するコールバック。 */
    private final LogCallback logger;
    
    /** 進捗状況を更新するコールバック。 */
    private final ProgressUpdateCallback progressUpdater;
    
    /** 翻訳結果の出力先ディレクトリ。デフォルトは「output/MyJPpack」。 */
    private final File outputDir;
    
    /** JARファイル内の言語ファイルを解析するアナライザー。 */
    private final JarFileAnalyzer jarAnalyzer;
    
    /** 言語ファイルをディスクに書き込むライター。 */
    private final LanguageFileWriter fileWriter;
    
    /** 翻訳対象テキストの文字数をカウントするカウンター。 */
    private final CharacterCounter charCounter;
    
    /** クエストファイルを処理するプロセッサー。 */
    private final QuestFileProcessor questProcessor;
    
    /**
     * ModPackProcessorのコンストラクタ。
     * @param inputPath 処理対象ディレクトリパス
     * @param translationService 翻訳サービス
     * @param logger ログコールバック
     * @param progressUpdater 進捗コールバック
     */
    public ModPackProcessor(String inputPath, TranslationService translationService, 
                           LogCallback logger, ProgressUpdateCallback progressUpdater) {
        this.inputPath = inputPath;
        this.translationService = translationService;
        this.logger = logger;
        this.progressUpdater = progressUpdater;
        this.outputDir = new File("output/MyJPpack");
        
        this.jarAnalyzer = new JarFileAnalyzer();
        this.fileWriter = new LanguageFileWriter(outputDir);
        this.charCounter = new CharacterCounter();
        this.questProcessor = new QuestFileProcessor(translationService, logger, outputDir);
    }
    
    /**
     * ModPack全体の翻訳処理を実行します。
     * @return 各Modの処理結果リスト
     * @throws Exception ファイルアクセスエラー等
     */
    public List<ModProcessingResult> process() throws Exception {
        File modsDir = new File(inputPath, "mods");
        File[] jarFiles = modsDir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(".jar");
            }
        });
        
        List<ModProcessingResult> results = new ArrayList<ModProcessingResult>();
        
        if (jarFiles == null || jarFiles.length == 0) {
            log("modsフォルダ内にJARファイルが見つかりません。");
            return results;
        }
        
        logModList(jarFiles);
        
        log("=== 翻訳処理開始 ===");
        int processed = 0;
        int skipped = 0;
        int translated = 0;
        int totalMods = jarFiles.length;
        
        for (int modIndex = 0; modIndex < jarFiles.length; modIndex++) {
            File jarFile = jarFiles[modIndex];
            int currentModNum = modIndex + 1;
            
            try {
                ModProcessingResult result = processModJar(jarFile, currentModNum, totalMods);
                results.add(result);
                processed++;
                
                logProcessingResult(result, currentModNum, totalMods, jarFile.getName());
                
                if (result.hasJaJp && !result.translated) {
                    skipped++;
                } else if (result.translated && result.translationSuccess) {
                    translated++;
                } else if (!result.hasEnUs) {
                    skipped++;
                }
            } catch (Exception e) {
                log(String.format("[%d/%d][エラー] %s: %s", 
                    currentModNum, totalMods, jarFile.getName(), e.getMessage()));
                
                ModProcessingResult errorResult = createErrorResult(jarFile);
                results.add(errorResult);
            }
        }
        
        logSummary(processed, translated, skipped);
        
        processQuests(results);
        
        return results;
    }
    
    /**
     * クエストファイルを処理します。
     */
    private void processQuests(List<ModProcessingResult> results) {
        try {
            File modpackDir = new File(inputPath);
            QuestTranslationResult questResult = questProcessor.process(modpackDir);
            
            if (questResult.hasTranslation()) {
                ModProcessingResult questModResult = new ModProcessingResult();
                questModResult.modName = "FTB Quests";
                questModResult.langFolderPath = "config/ftbquests";
                questModResult.hasEnUs = questResult.hasLangFile;
                questModResult.hasJaJp = false;
                questModResult.translated = questResult.hasTranslation();
                questModResult.translationSuccess = questResult.isAllSuccess();
                questModResult.questResult = questResult;
                
                results.add(questModResult);
            }
        } catch (Exception e) {
            log("[Quest]エラー: " + e.getMessage());
        }
    }
    
    /** Mod一覧をログ出力します。 */
    private void logModList(File[] jarFiles) {
        log("=== Mod一覧 ===");
        log("検出されたMod数: " + jarFiles.length);
        for (File jarFile : jarFiles) {
            log("  - " + jarFile.getName());
        }
        log("");
    }
    
    /** 個別Modの処理結果をログ出力します。 */
    private void logProcessingResult(ModProcessingResult result, int current, int total, String fileName) {
        if (result.hasJaJp && !result.translated) {
            log(String.format("[%d/%d][既存] %s - 日本語ファイルが存在します", 
                current, total, fileName));
        } else if (result.translated) {
            if (result.translationSuccess) {
                log(String.format("[%d/%d][翻訳] %s - 翻訳完了 (%d文字)", 
                    current, total, fileName, result.characterCount));
            } else {
                log(String.format("[%d/%d][失敗] %s - 翻訳に失敗しました", 
                    current, total, fileName));
            }
        } else {
            log(String.format("[%d/%d][スキップ] %s - 英語ファイルが見つかりません", 
                current, total, fileName));
        }
    }
    
    /** 処理完了時のサマリーをログ出力します。 */
    private void logSummary(int processed, int translated, int skipped) {
        log("");
        log("=== 処理完了 ===");
        log("処理したMod数: " + processed);
        log("翻訳したMod数: " + translated);
        log("スキップしたMod数: " + skipped);
        log("出力先: " + outputDir.getAbsolutePath());
    }
    
    /**
     * 単一のMod JARファイルを処理します。
     * @param jarFile 処理対象JARファイル
     * @param currentModNum 現在のMod番号
     * @param totalMods 全Mod数
     * @return 処理結果
     * @throws Exception 処理エラー
     */
    private ModProcessingResult processModJar(File jarFile, int currentModNum, int totalMods) throws Exception {
        ModProcessingResult result = new ModProcessingResult();
        result.modName = jarFile.getName().replace(".jar", "");
        
        JarFileAnalyzer.LanguageFileInfo langInfo = jarAnalyzer.analyze(jarFile);
        
        result.langFolderPath = langInfo.langFolderPath;
        result.hasEnUs = langInfo.hasEnUs;
        result.hasJaJp = langInfo.hasJaJp;
        
        if (langInfo.modId == null || langInfo.enUsContent == null) {
            result.translationSuccess = false;
            return result;
        }
        
        result.characterCount = charCounter.countCharacters(langInfo.enUsContent);
        
        if (langInfo.jaJpContent != null) {
            fileWriter.writeLanguageFiles(langInfo.modId, langInfo.enUsContent, langInfo.jaJpContent);
            result.translationSuccess = true;
        } else {
            try {
                String translatedContent = translateWithProgress(
                    langInfo.enUsContent, currentModNum, totalMods);
                
                fileWriter.writeLanguageFiles(langInfo.modId, langInfo.enUsContent, translatedContent);
                result.translated = true;
                result.translationSuccess = true;
                
                logProgress(" ");
            } catch (Exception e) {
                result.translated = true;
                result.translationSuccess = false;
                logProgress(" ");
                throw e;
            }
        }
        
        return result;
    }
    
    /** 進捗通知付きで翻訳を実行します。 */
    private String translateWithProgress(String content, final int currentMod, final int totalMods) throws Exception {
        return translationService.translateJsonFile(content, new ProgressCallback() {
            @Override
            public void onProgress(int current, int total) {
                logProgress(String.format("[%d/%d] 翻訳中: %d/%d エントリー", 
                    currentMod, totalMods, current, total));
            }
        });
    }
    
    /** エラー発生時の処理結果を生成します。 */
    private ModProcessingResult createErrorResult(File jarFile) {
        ModProcessingResult errorResult = new ModProcessingResult();
        errorResult.modName = jarFile.getName().replace(".jar", "");
        errorResult.langFolderPath = "エラー";
        errorResult.translationSuccess = false;
        return errorResult;
    }
    
    /** ログメッセージを出力します。 */
    private void log(String message) {
        if (logger != null) {
            logger.onLog(message);
        }
    }
    
    /** 進捗ログメッセージを出力します。 */
    private void logProgress(String message) {
        if (logger != null) {
            logger.onLog("PROGRESS:" + message);
        }
    }
}
