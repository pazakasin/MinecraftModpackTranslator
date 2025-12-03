package io.github.pazakasin.minecraft.modpack.translator.service;

import io.github.pazakasin.minecraft.modpack.translator.model.ModProcessingResult;
import io.github.pazakasin.minecraft.modpack.translator.model.QuestTranslationResult;
import io.github.pazakasin.minecraft.modpack.translator.model.QuestFileResult;
import io.github.pazakasin.minecraft.modpack.translator.model.TranslatableFile;
import io.github.pazakasin.minecraft.modpack.translator.service.processor.*;
import io.github.pazakasin.minecraft.modpack.translator.service.callback.*;
import io.github.pazakasin.minecraft.modpack.translator.service.quest.QuestFileProcessor;
import io.github.pazakasin.minecraft.modpack.translator.service.quest.LangFileSNBTExtractor;
import io.github.pazakasin.minecraft.modpack.translator.service.quest.SNBTParser;
import io.github.pazakasin.minecraft.modpack.translator.service.backup.BackupManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.querz.nbt.tag.Tag;
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
    
    /** ファイル状態更新時に呼ばれるコールバック。 */
    private FileStateUpdateCallback fileStateCallback;
    
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
        this.fileStateCallback = null;
    }
    
    /**
     * ファイル状態更新コールバックを設定します。
     * @param callback コールバック
     */
    public void setFileStateCallback(FileStateUpdateCallback callback) {
        this.fileStateCallback = callback;
    }
    
    /**
     * ファイルの状態を更新し、コールバックを呼び出します。
     * @param file 対象ファイル
     */
    private void updateFileState(TranslatableFile file) {
        if (fileStateCallback != null) {
            fileStateCallback.onFileStateUpdate(file);
        }
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
        log("出力先: " + new File("output").getAbsolutePath());
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
    
    /**
     * 選択された翻訳対象ファイルのみを処理します。
     * @param selectedFiles 選択された翻訳対象ファイルのリスト
     * @return 各ファイルの処理結果リスト
     * @throws Exception ファイルアクセスエラー等
     */
    public List<ModProcessingResult> processSelectedFiles(List<TranslatableFile> selectedFiles) throws Exception {
        List<ModProcessingResult> results = new ArrayList<ModProcessingResult>();
        
        if (selectedFiles == null || selectedFiles.isEmpty()) {
            log("選択されたファイルがありません。");
            return results;
        }
        
        log("=== 選択ファイル翻訳処理開始 ===");
        log("選択されたファイル数: " + selectedFiles.size());
        
        List<TranslatableFile> modLangFiles = new ArrayList<TranslatableFile>();
        List<TranslatableFile> kubeJsLangFiles = new ArrayList<TranslatableFile>();
        List<TranslatableFile> questLangFiles = new ArrayList<TranslatableFile>();
        List<TranslatableFile> questFiles = new ArrayList<TranslatableFile>();
        
        for (TranslatableFile file : selectedFiles) {
            switch (file.getFileType()) {
                case MOD_LANG_FILE:
                    modLangFiles.add(file);
                    break;
                case KUBEJS_LANG_FILE:
                    kubeJsLangFiles.add(file);
                    break;
                case QUEST_LANG_FILE:
                    questLangFiles.add(file);
                    break;
                case QUEST_FILE:
                    questFiles.add(file);
                    break;
            }
        }
        
        if (!modLangFiles.isEmpty()) {
            log("");
            log("=== Mod言語ファイル翻訳 ===");
            processSelectedModLangFiles(modLangFiles, results);
        }
        
        if (!kubeJsLangFiles.isEmpty()) {
            log("");
            log("=== KubeJS言語ファイル翻訳 ===");
            processSelectedKubeJSLangFiles(kubeJsLangFiles, results);
        }
        
        if (!questLangFiles.isEmpty() || !questFiles.isEmpty()) {
            log("");
            log("=== クエストファイル翻訳 ===");
            processSelectedQuestFiles(questLangFiles, questFiles, results);
        }
        
        int translated = 0;
        int failed = 0;
        for (ModProcessingResult result : results) {
            if (result.translated && result.translationSuccess) {
                translated++;
            } else if (result.translated && !result.translationSuccess) {
                failed++;
            }
        }
        
        log("");
        log("=== 翻訳完了 ===");
        log("処理したファイル数: " + selectedFiles.size());
        log("翻訳成功: " + translated);
        log("翻訳失敗: " + failed);
        log("出力先: " + new File("output").getAbsolutePath());
        
        return results;
    }
    
    /** 選択されたKubeJS言語ファイルを処理します。 */
    private void processSelectedKubeJSLangFiles(List<TranslatableFile> kubeJsFiles,
                                                List<ModProcessingResult> results) throws Exception {
        int totalFiles = kubeJsFiles.size();
        
        for (int i = 0; i < kubeJsFiles.size(); i++) {
            TranslatableFile file = kubeJsFiles.get(i);
            int currentNum = i + 1;
            
            ModProcessingResult result = new ModProcessingResult();
            result.modName = "KubeJS - " + file.getFileId();
            result.langFolderPath = file.getLangFolderPath();
            result.hasEnUs = true;
            result.hasJaJp = file.isHasExistingJaJp();
            result.characterCount = file.getCharacterCount();
            
            try {
                if (file.isHasExistingJaJp()) {
                    file.setProcessingState(TranslatableFile.ProcessingState.EXISTING);
                    file.setResultMessage("既存");
                    updateFileState(file);
                    
                    writeKubeJSLangFiles(file);
                    result.translationSuccess = true;
                    
                    log(String.format("[%d/%d][既存] %s - 日本語ファイルをコピー", 
                        currentNum, totalFiles, file.getFileId()));
                } else {
                    file.setProcessingState(TranslatableFile.ProcessingState.TRANSLATING);
                    file.setResultMessage("翻訳中");
                    updateFileState(file);
                    
                    String translatedContent = translateWithProgress(
                        file.getFileContent(), currentNum, totalFiles);
                    
                    writeKubeJSLangFiles(file.getFileId(), file.getFileContent(), translatedContent);
                    result.translated = true;
                    result.translationSuccess = true;
                    
                    file.setProcessingState(TranslatableFile.ProcessingState.COMPLETED);
                    file.setResultMessage("○");
                    updateFileState(file);
                    
                    log(String.format("[%d/%d][翻訳] %s - 翻訳完了 (%d文字)", 
                        currentNum, totalFiles, file.getFileId(), file.getCharacterCount()));
                    
                    logProgress(" ");
                }
            } catch (Exception e) {
                result.translated = true;
                result.translationSuccess = false;
                
                file.setProcessingState(TranslatableFile.ProcessingState.FAILED);
                file.setResultMessage("×");
                updateFileState(file);
                
                log(String.format("[%d/%d][失敗] %s: %s", 
                    currentNum, totalFiles, file.getFileId(), e.getMessage()));
                
                logProgress(" ");
            }
            
            results.add(result);
        }
    }
    
    /** KubeJS言語ファイルを書き込みます（既存ファイル用）。 */
    private void writeKubeJSLangFiles(TranslatableFile file) throws IOException {
        writeKubeJSLangFiles(file.getFileId(), file.getFileContent(), file.getExistingJaJpContent());
    }
    
    /** KubeJS言語ファイルを書き込みます。 */
    private void writeKubeJSLangFiles(String fileId, String enUsContent, String jaJpContent) throws IOException {
        File langDir = new File("output/kubejs/assets/" + fileId + "/lang");
        langDir.mkdirs();
        
        if (enUsContent != null) {
            java.nio.file.Files.write(new File(langDir, "en_us.json").toPath(), 
                       enUsContent.getBytes("UTF-8"));
        }
        
        if (jaJpContent != null) {
            java.nio.file.Files.write(new File(langDir, "ja_jp.json").toPath(), 
                       jaJpContent.getBytes("UTF-8"));
        }
    }
    
    /** 選択されたMod言語ファイルを処理します。 */
    private void processSelectedModLangFiles(List<TranslatableFile> modLangFiles, 
                                            List<ModProcessingResult> results) throws Exception {
        int totalMods = modLangFiles.size();
        
        for (int i = 0; i < modLangFiles.size(); i++) {
            TranslatableFile file = modLangFiles.get(i);
            int currentModNum = i + 1;
            
            ModProcessingResult result = new ModProcessingResult();
            result.modName = file.getModName();
            result.langFolderPath = file.getLangFolderPath();
            result.hasEnUs = true;
            result.hasJaJp = file.isHasExistingJaJp();
            result.characterCount = file.getCharacterCount();
            
            try {
                if (file.isHasExistingJaJp()) {
                    file.setProcessingState(TranslatableFile.ProcessingState.EXISTING);
                    file.setResultMessage("既存");
                    updateFileState(file);
                    
                    fileWriter.writeLanguageFiles(file.getFileId(), 
                        file.getFileContent(), file.getExistingJaJpContent());
                    result.translationSuccess = true;
                    
                    log(String.format("[%d/%d][既存] %s - 日本語ファイルをコピー", 
                        currentModNum, totalMods, file.getModName()));
                } else {
                    file.setProcessingState(TranslatableFile.ProcessingState.TRANSLATING);
                    file.setResultMessage("翻訳中");
                    updateFileState(file);
                    
                    String translatedContent = translateWithProgress(
                        file.getFileContent(), currentModNum, totalMods);
                    
                    fileWriter.writeLanguageFiles(file.getFileId(), 
                        file.getFileContent(), translatedContent);
                    result.translated = true;
                    result.translationSuccess = true;
                    
                    file.setProcessingState(TranslatableFile.ProcessingState.COMPLETED);
                    file.setResultMessage("○");
                    updateFileState(file);
                    
                    log(String.format("[%d/%d][翻訳] %s - 翻訳完了 (%d文字)", 
                        currentModNum, totalMods, file.getModName(), file.getCharacterCount()));
                    
                    logProgress(" ");
                }
            } catch (Exception e) {
                result.translated = true;
                result.translationSuccess = false;
                
                file.setProcessingState(TranslatableFile.ProcessingState.FAILED);
                file.setResultMessage("×");
                updateFileState(file);
                
                log(String.format("[%d/%d][失敗] %s: %s", 
                    currentModNum, totalMods, file.getModName(), e.getMessage()));
                
                logProgress(" ");
            }
            
            results.add(result);
        }
    }
    
    /** 選択されたクエストファイルを処理します。 */
    private void processSelectedQuestFiles(List<TranslatableFile> questLangFiles,
                                          List<TranslatableFile> questFiles,
                                          List<ModProcessingResult> results) throws Exception {
        QuestTranslationResult questResult = new QuestTranslationResult();
        File modpackDir = new File(inputPath);
        
        // バックアップ実行
        BackupManager.BackupResult backupResult = questProcessor.executeBackup(modpackDir);
        if (backupResult != null) {
            questResult.backupPath = backupResult.backupPath;
        }
        
        // Lang Fileの処理
        for (TranslatableFile file : questLangFiles) {
            questResult.hasLangFile = true;
            
            // 既存ファイルがある場合はステータスを変更
            if (file.isHasExistingJaJp()) {
                file.setProcessingState(TranslatableFile.ProcessingState.EXISTING);
                file.setResultMessage("既存");
            } else {
                file.setProcessingState(TranslatableFile.ProcessingState.TRANSLATING);
                file.setResultMessage("翻訳中");
            }
            updateFileState(file);
            
            try {
                File sourceFile = new File(file.getSourceFilePath());
                
                // 既存のja_jp.snbtを確認
                File existingJaJpFile = null;
                if (file.isHasExistingJaJp() && file.getExistingJaJpContent() != null) {
                    File langDir = sourceFile.getParentFile();
                    existingJaJpFile = new File(langDir, "ja_jp.snbt");
                }
                
                QuestFileResult fileResult = questProcessor.processSingleLangFile(
                    sourceFile, existingJaJpFile, file.getCharacterCount());
                
                questResult.fileResults.add(fileResult);
                questResult.langFileTranslated = fileResult.translated;
                questResult.langFileSuccess = fileResult.success;
                questResult.langFileCharacterCount = file.getCharacterCount();
                
                if (fileResult.success) {
                    if (fileResult.translated) {
                        file.setProcessingState(TranslatableFile.ProcessingState.COMPLETED);
                        file.setResultMessage("○");
                    } else {
                        // 既存ファイルを使用した場合
                        file.setProcessingState(TranslatableFile.ProcessingState.EXISTING);
                        file.setResultMessage("既存");
                    }
                } else {
                    file.setProcessingState(TranslatableFile.ProcessingState.FAILED);
                    file.setResultMessage("×");
                }
                
                updateFileState(file);
                
                // ログ出力
                if (fileResult.success) {
                    if (fileResult.translated) {
                        log(String.format("[Quest Lang][翻訳] %s - 翻訳完了 (%d文字)", 
                            file.getModName(), file.getCharacterCount()));
                    } else {
                        log(String.format("[Quest Lang][既存] %s - 日本語ファイルをコピー", 
                            file.getModName()));
                    }
                }
            } catch (Exception e) {
                file.setProcessingState(TranslatableFile.ProcessingState.FAILED);
                file.setResultMessage("×: " + e.getMessage());
                updateFileState(file);
                
                log(String.format("[Quest Lang][失敗] %s: %s", 
                    file.getModName(), e.getMessage()));
            }
        }
        
        // Quest Fileの処理
        questResult.questFileCount = questFiles.size();
        
        for (int i = 0; i < questFiles.size(); i++) {
            TranslatableFile file = questFiles.get(i);
            
            file.setProcessingState(TranslatableFile.ProcessingState.TRANSLATING);
            file.setResultMessage("翻訳中");
            updateFileState(file);
            
            logProgress(String.format("[Quest %d/%d] 翻訳中: %s", 
                i + 1, questFiles.size(), file.getModName()));
            
            try {
                File sourceFile = new File(file.getSourceFilePath());
                QuestFileResult fileResult = questProcessor.processSingleQuestFile(
                    sourceFile, file.getCharacterCount());
                
                questResult.fileResults.add(fileResult);
                questResult.questFileTranslated++;
                
                if (fileResult.success) {
                    questResult.questFileSuccess++;
                    file.setProcessingState(TranslatableFile.ProcessingState.COMPLETED);
                    file.setResultMessage("○");
                } else {
                    file.setProcessingState(TranslatableFile.ProcessingState.FAILED);
                    file.setResultMessage("×");
                }
                
                updateFileState(file);
                questResult.questFileCharacterCount += file.getCharacterCount();
            } catch (Exception e) {
                file.setProcessingState(TranslatableFile.ProcessingState.FAILED);
                file.setResultMessage("×: " + e.getMessage());
                updateFileState(file);
                
                log(String.format("[Quest %d/%d][失敗] %s: %s", 
                    i + 1, questFiles.size(), file.getModName(), e.getMessage()));
            }
        }
        
        logProgress(" ");
        
        // 結果をModProcessingResultに変換
        if (questResult.hasTranslation()) {
            ModProcessingResult questModResult = new ModProcessingResult();
            questModResult.modName = "FTB Quests";
            questModResult.langFolderPath = "config/ftbquests";
            questModResult.hasEnUs = questResult.hasLangFile;
            questModResult.hasJaJp = false;
            questModResult.translated = questResult.hasTranslation();
            questModResult.translationSuccess = questResult.isAllSuccess();
            questModResult.questResult = questResult;
            questModResult.characterCount = questResult.getTotalCharacterCount();
            
            results.add(questModResult);
            
            log("");
            log("=== FTB Quests翻訳完了 ===");
            log("合計翻訳文字数: " + questResult.getTotalCharacterCount() + "文字");
            
            if (questResult.backupPath != null) {
                log("バックアップ先: " + questResult.backupPath);
            }
        }
    }
    
}
