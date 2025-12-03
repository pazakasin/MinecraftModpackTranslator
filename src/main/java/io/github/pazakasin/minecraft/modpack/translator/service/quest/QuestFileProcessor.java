package io.github.pazakasin.minecraft.modpack.translator.service.quest;

import io.github.pazakasin.minecraft.modpack.translator.model.QuestTranslationResult;
import io.github.pazakasin.minecraft.modpack.translator.model.QuestFileResult;
import io.github.pazakasin.minecraft.modpack.translator.service.TranslationService;
import io.github.pazakasin.minecraft.modpack.translator.service.ProgressCallback;
import io.github.pazakasin.minecraft.modpack.translator.service.callback.LogCallback;
import io.github.pazakasin.minecraft.modpack.translator.service.backup.BackupManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.querz.nbt.tag.Tag;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * FTB Questsファイルの翻訳処理を統括するクラス。
 * Lang FileとQuest File本体の両方を処理。
 */
public class QuestFileProcessor {
    /** 翻訳サービス。 */
    private final TranslationService translationService;
    
    /** ログコールバック。 */
    private final LogCallback logger;
    
    /** 出力先ディレクトリ。 */
    private final File outputDir;
    
    /** クエストファイル検出器。 */
    private final QuestFileDetector detector;
    
    /** SNBTパーサー。 */
    private final SNBTParser parser;
    
    /** Lang File抽出器。 */
    private final LangFileSNBTExtractor extractor;
    
    /** バックアップマネージャー。 */
    private final BackupManager backupManager;
    
    /** JSON処理用Gsonインスタンス。 */
    private final Gson gson;
    
    /**
     * QuestFileProcessorのコンストラクタ。
     * @param translationService 翻訳サービス
     * @param logger ログコールバック
     * @param outputDir 出力先ディレクトリ
     */
    public QuestFileProcessor(TranslationService translationService, 
                             LogCallback logger, File outputDir) {
        this.translationService = translationService;
        this.logger = logger;
        this.outputDir = outputDir;
        this.detector = new QuestFileDetector();
        this.parser = new SNBTParser();
        this.extractor = new LangFileSNBTExtractor();
        this.backupManager = new BackupManager();
        this.gson = new GsonBuilder().create();
    }
    
    /**
     * ModPackディレクトリ内のクエストファイルを検出して翻訳します。
     * @param modpackDir ModPackディレクトリ
     * @return 翻訳結果
     * @throws Exception 処理エラー
     */
    public QuestTranslationResult process(File modpackDir) throws Exception {
        QuestTranslationResult result = new QuestTranslationResult();
        
        List<QuestFileDetector.QuestFileInfo> files = detector.detectQuestFiles(modpackDir);
        
        if (files.isEmpty()) {
            log("FTB Questsファイルが見つかりませんでした。");
            return result;
        }
        
        BackupManager.BackupResult backupResult = executeBackup(modpackDir);
        if (backupResult != null) {
            result.backupPath = backupResult.backupPath;
        }
        
        log("=== FTB Quests翻訳処理開始 ===");
        log("検出されたファイル数: " + files.size());
        
        for (QuestFileDetector.QuestFileInfo fileInfo : files) {
            if (fileInfo.getType() == QuestFileDetector.QuestFileType.LANG_FILE) {
                processLangFile(fileInfo.getFile(), fileInfo.getJaJpFile(), result);
            } else {
                result.questFileCount++;
            }
        }
        
        int questFileIndex = 0;
        for (QuestFileDetector.QuestFileInfo fileInfo : files) {
            if (fileInfo.getType() == QuestFileDetector.QuestFileType.QUEST_FILE) {
                questFileIndex++;
                processQuestFile(fileInfo.getFile(), result, questFileIndex, result.questFileCount);
            }
        }
        
        logSummary(result);
        
        return result;
    }
    
    /**
     * Lang File（en_us.snbt）を処理します（NBTパース使用）。
     * 既存のja_jp.snbtがある場合はそちらを使用します。
     */
    private void processLangFile(File langFile, File existingJaJpFile, QuestTranslationResult result) {
        result.hasLangFile = true;
        
        try {
            log("Lang File処理開始: " + langFile.getName());
            log("ファイルパス: " + langFile.getAbsolutePath());
            
            File outputBase = outputDir.getParentFile();
            File outputLangDir = new File(outputBase, "config/ftbquests/quests/lang");
            outputLangDir.mkdirs();
            File outputFile = new File(outputLangDir, "ja_jp.snbt");
            
            if (existingJaJpFile != null && existingJaJpFile.exists()) {
                log("既存の日本語ファイルが見つかりました: " + existingJaJpFile.getAbsolutePath());
                log("既存ファイルをコピーします。");
                
                Files.copy(existingJaJpFile.toPath(), outputFile.toPath(), 
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                
                result.langFileTranslated = false;
                result.langFileSuccess = true;
                result.langFileCharacterCount = 0;
                
                QuestFileResult fileResult = QuestFileResult.createLangFileResult(
                    langFile, outputFile, false, true, 0);
                result.fileResults.add(fileResult);
                
                log("既存Lang Fileコピー完了: " + outputFile.getAbsolutePath());
                return;
            }
            
            Tag<?> rootTag = parser.parse(langFile);
            List<LangFileSNBTExtractor.ExtractedText> texts = extractor.extract(rootTag);
            
            if (texts.isEmpty()) {
                log("翻訳対象テキストが見つかりませんでした。");
                return;
            }
            
            result.langFileCharacterCount = countCharacters(texts);
            log("翻訳対象テキスト数: " + texts.size() + " (" + result.langFileCharacterCount + "文字)");
            
            Map<String, String> translations = translateTexts(texts);
            
            applyTranslationsToLangFile(langFile, outputFile, translations);
            
            result.langFileTranslated = true;
            result.langFileSuccess = true;
            
            QuestFileResult fileResult = QuestFileResult.createLangFileResult(
                langFile, outputFile, true, true, result.langFileCharacterCount);
            result.fileResults.add(fileResult);
            
            log("Lang File翻訳完了: " + outputFile.getAbsolutePath());
            
        } catch (Exception e) {
            logError("Lang File翻訳エラー", langFile, e);
            result.langFileTranslated = true;
            result.langFileSuccess = false;
            
            QuestFileResult fileResult = QuestFileResult.createLangFileResult(
                langFile, null, true, false, result.langFileCharacterCount);
            result.fileResults.add(fileResult);
        }
    }
    
    /**
     * Lang File用の翻訳適用メソッド（正規表現ベース）。
     * @param sourceFile 元のSNBTファイル
     * @param targetFile 出力先ファイル
     * @param translations キーと翻訳のマップ
     * @throws IOException ファイルI/Oエラー
     */
    private void applyTranslationsToLangFile(File sourceFile, File targetFile, 
                                            Map<String, String> translations) throws IOException {
        String content = Files.readString(sourceFile.toPath(), StandardCharsets.UTF_8);
        
        for (Map.Entry<String, String> entry : translations.entrySet()) {
            String key = escapeRegex(entry.getKey());
            String translatedValue = entry.getValue();
            
            Pattern stringPattern = Pattern.compile(
                "(" + key + ":\\s*)\"([^\"\\\\]*(\\\\.[^\"\\\\]*)*)\"");
            Matcher stringMatcher = stringPattern.matcher(content);
            
            if (stringMatcher.find()) {
                String replacement = stringMatcher.group(1) + "\"" + 
                    escapeSnbtString(translatedValue) + "\"";
                content = stringMatcher.replaceFirst(Matcher.quoteReplacement(replacement));
            } else {
                Pattern arrayPattern = Pattern.compile(
                    "(" + key + ":\\s*\\[)([^\\]]*)(\\])", 
                    Pattern.DOTALL);
                Matcher arrayMatcher = arrayPattern.matcher(content);
                
                if (arrayMatcher.find()) {
                    String prefix = arrayMatcher.group(1);
                    String originalArray = arrayMatcher.group(2);
                    String suffix = arrayMatcher.group(3);
                    
                    String indent = extractIndent(originalArray);
                    String[] lines = translatedValue.split("\n");
                    
                    StringBuilder newArray = new StringBuilder();
                    for (int i = 0; i < lines.length; i++) {
                        newArray.append("\n").append(indent);
                        newArray.append("\"").append(escapeSnbtString(lines[i])).append("\"");
                    }
                    if (lines.length > 0) {
                        String baseIndent = indent.length() > 0 && indent.charAt(indent.length() - 1) == '\t' 
                            ? indent.substring(0, indent.length() - 1) : indent;
                        newArray.append("\n").append(baseIndent);
                    }
                    
                    String replacement = prefix + newArray.toString() + suffix;
                    content = arrayMatcher.replaceFirst(Matcher.quoteReplacement(replacement));
                }
            }
        }
        
        Files.writeString(targetFile.toPath(), content, StandardCharsets.UTF_8);
    }
    
    /**
     * 正規表現で使用する特殊文字をエスケープします。
     * @param text エスケープ対象テキスト
     * @return エスケープ済みテキスト
     */
    private String escapeRegex(String text) {
        return text.replaceAll("([\\\\\\[\\]{}()*+?.^$|])", "\\\\$1");
    }
    
    /**
     * SNBT文字列値をエスケープします。
     * @param text エスケープ対象テキスト
     * @return エスケープ済みテキスト
     */
    private String escapeSnbtString(String text) {
        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"");
    }
    
    /**
     * 配列内容からインデントを抽出します。
     * @param arrayContent 配列の内容
     * @return インデント文字列
     */
    private String extractIndent(String arrayContent) {
        int firstNewline = arrayContent.indexOf('\n');
        if (firstNewline == -1) {
            return "\t";
        }
        
        int start = firstNewline + 1;
        int end = start;
        while (end < arrayContent.length() && 
               (arrayContent.charAt(end) == ' ' || arrayContent.charAt(end) == '\t')) {
            end++;
        }
        
        return end > start ? arrayContent.substring(start, end) : "\t";
    }
    
    /**
     * Quest File本体を処理します（正規表現ベース、NBTパース不使用）。
     */
    private void processQuestFile(File questFile, QuestTranslationResult result, 
                                  int currentIndex, int totalCount) {
        try {
            log(String.format("[Quest %d/%d] 処理開始: %s", 
                currentIndex, totalCount, questFile.getName()));
            
            Map<String, String> texts = parser.extractTranslatableTexts(questFile);
            
            // デバッグログ（bees.snbtの場合のみ）
            if (questFile.getName().equals("bees.snbt")) {
                log("=== デバッグ: 抽出されたテキスト（全て） ===");
                int count = 0;
                for (Map.Entry<String, String> entry : texts.entrySet()) {
                    String preview = entry.getValue();
                    if (preview.length() > 50) {
                        preview = preview.substring(0, 50) + "...";
                    }
                    preview = preview.replace("\n", "\\n");
                    log(entry.getKey() + ": " + preview);
                    count++;
                }
                log("合計: " + texts.size() + " 個");
                log("====================================");
            }
            
            File relativePath = getRelativePath(questFile);
            File outputBase = outputDir.getParentFile();
            File outputFile = new File(outputBase, relativePath.getPath());
            outputFile.getParentFile().mkdirs();
            
            if (texts.isEmpty()) {
                log("翻訳対象テキストなし - ファイルをコピー");
                Files.copy(questFile.toPath(), outputFile.toPath(), 
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                return;
            }
            
            int charCount = 0;
            for (String value : texts.values()) {
                charCount += value.length();
            }
            result.questFileCharacterCount += charCount;
            
            log(String.format("翻訳対象: %d個 (%d文字)", texts.size(), charCount));
            
            Map<String, String> translations = translateTextsMap(texts);
            
            parser.applyTranslations(questFile, outputFile, translations);
            
            result.questFileTranslated++;
            result.questFileSuccess++;
            
            QuestFileResult fileResult = QuestFileResult.createQuestFileResult(
                questFile, outputFile, true, true, charCount);
            result.fileResults.add(fileResult);
            
            log("翻訳完了: " + outputFile.getAbsolutePath());
            
        } catch (Exception e) {
            logError("クエストファイル翻訳エラー", questFile, e);
            result.questFileTranslated++;
            
            QuestFileResult fileResult = QuestFileResult.createQuestFileResult(
                questFile, null, true, false, 0);
            result.fileResults.add(fileResult);
        }
    }
    
    /**
     * Map形式のテキストを翻訳します。
     * @param texts キーと値のマップ
     * @return キーと翻訳結果のマップ
     * @throws Exception 翻訳エラー
     */
    private Map<String, String> translateTextsMap(Map<String, String> texts) throws Exception {
        JsonObject combined = new JsonObject();
        for (Map.Entry<String, String> entry : texts.entrySet()) {
            combined.addProperty(entry.getKey(), entry.getValue());
        }
        
        final int totalEntries = texts.size();
        String translatedJson = translationService.translateJsonFile(
            gson.toJson(combined),
            new ProgressCallback() {
                @Override
                public void onProgress(int current, int total) {
                    double percentage = (current * 100.0) / total;
                    log(String.format("翻訳中: %d/%d エントリー (%.1f%%)", 
                        current, total, percentage));
                }
            });
        
        JsonObject result = gson.fromJson(translatedJson, JsonObject.class);
        Map<String, String> translations = new HashMap<String, String>();
        for (Map.Entry<String, JsonElement> entry : result.entrySet()) {
            translations.put(entry.getKey(), entry.getValue().getAsString());
        }
        
        return translations;
    }
    
    /**
     * 抽出されたテキストをバッチ翻訳します（Lang File用）。
     * @param texts 翻訳対象テキストリスト
     * @return キーと翻訳結果のマップ
     * @throws Exception 翻訳エラー
     */
    private Map<String, String> translateTexts(List<LangFileSNBTExtractor.ExtractedText> texts) throws Exception {
        JsonObject combined = new JsonObject();
        for (LangFileSNBTExtractor.ExtractedText text : texts) {
            combined.addProperty(text.getKey(), text.getValue());
        }
        
        final int totalEntries = texts.size();
        String translatedJson = translationService.translateJsonFile(
            gson.toJson(combined),
            new ProgressCallback() {
                @Override
                public void onProgress(int current, int total) {
                    double percentage = (current * 100.0) / total;
                    log(String.format("翻訳中: %d/%d エントリー (%.1f%%)", 
                        current, total, percentage));
                }
            });
        
        JsonObject result = gson.fromJson(translatedJson, JsonObject.class);
        Map<String, String> translations = new HashMap<String, String>();
        for (Map.Entry<String, JsonElement> entry : result.entrySet()) {
            translations.put(entry.getKey(), entry.getValue().getAsString());
        }
        
        return translations;
    }
    
    /**
     * テキストリストの合計文字数をカウントします。
     */
    private int countCharacters(List<LangFileSNBTExtractor.ExtractedText> texts) {
        int count = 0;
        for (LangFileSNBTExtractor.ExtractedText text : texts) {
            count += text.getValue().length();
        }
        return count;
    }
    
    /**
     * クエストファイルの相対パスを取得します。
     */
    private File getRelativePath(File questFile) {
        String path = questFile.getAbsolutePath();
        int configIndex = path.indexOf("config");
        
        if (configIndex != -1) {
            return new File(path.substring(configIndex));
        }
        
        return new File("config/ftbquests/quests/" + questFile.getName());
    }
    
    /**
     * 処理結果のサマリーをログ出力します。
     */
    private void logSummary(QuestTranslationResult result) {
        log("");
        log("=== FTB Quests翻訳完了 ===");
        
        if (result.hasLangFile) {
            String status = result.langFileSuccess ? "成功" : "失敗";
            log("Lang File: " + status + " (" + result.langFileCharacterCount + "文字)");
        }
        
        log("クエストファイル: " + result.questFileSuccess + "/" + result.questFileCount + "ファイル成功");
        log("合計翻訳文字数: " + result.getTotalCharacterCount() + "文字");
        log("出力先: " + new File("output").getAbsolutePath());
        
        if (result.hasTranslation() && result.backupPath != null) {
            log("");
            log("【重要】config\\ftbquests\\ 配下のファイルを更新しました。");
            log("元のファイルは " + result.backupPath + " にバックアップされています。");
        }
    }
    
    /**
     * 個別のLang Fileを翻訳します（選択的処理用）。
     * 既存のja_jp.snbtがある場合はそちらを使用します。
     * @param sourceFile 元のLang Fileパス
     * @param existingJaJpFile 既存のja_jp.snbtファイル（なければnull）
     * @param characterCount 文字数
     * @return 処理結果
     */
    public QuestFileResult processSingleLangFile(File sourceFile, File existingJaJpFile, int characterCount) {
        try {
            log("Lang File処理開始: " + sourceFile.getName());
            log("ファイルパス: " + sourceFile.getAbsolutePath());
            
            File outputBase = outputDir.getParentFile();
            File outputLangDir = new File(outputBase, "config/ftbquests/quests/lang");
            outputLangDir.mkdirs();
            File outputFile = new File(outputLangDir, "ja_jp.snbt");
            
            if (existingJaJpFile != null && existingJaJpFile.exists()) {
                log("既存の日本語ファイルが見つかりました: " + existingJaJpFile.getAbsolutePath());
                log("既存ファイルをコピーします。");
                
                Files.copy(existingJaJpFile.toPath(), outputFile.toPath(), 
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                
                log("既存Lang Fileコピー完了: " + outputFile.getAbsolutePath());
                
                return QuestFileResult.createLangFileResult(
                    sourceFile, outputFile, false, true, 0);
            }
            
            Tag<?> rootTag = parser.parse(sourceFile);
            List<LangFileSNBTExtractor.ExtractedText> texts = extractor.extract(rootTag);
            
            if (texts.isEmpty()) {
                log("翻訳対象テキストが見つかりませんでした。");
                return QuestFileResult.createLangFileResult(
                    sourceFile, null, false, false, 0);
            }
            
            Map<String, String> translations = translateTexts(texts);
            
            applyTranslationsToLangFile(sourceFile, outputFile, translations);
            
            log("Lang File翻訳完了: " + outputFile.getAbsolutePath());
            
            return QuestFileResult.createLangFileResult(
                sourceFile, outputFile, true, true, characterCount);
            
        } catch (Exception e) {
            logError("Lang File翻訳エラー", sourceFile, e);
            return QuestFileResult.createLangFileResult(
                sourceFile, null, true, false, characterCount);
        }
    }
    
    /**
     * 個別のQuest Fileを翻訳します（選択的処理用）。
     * @param sourceFile 元のQuest Fileパス
     * @param characterCount 文字数
     * @return 処理結果
     */
    public QuestFileResult processSingleQuestFile(File sourceFile, int characterCount) {
        try {
            log("Quest File処理開始: " + sourceFile.getName());
            
            Map<String, String> texts = parser.extractTranslatableTexts(sourceFile);
            
            File relativePath = getRelativePath(sourceFile);
            File outputBase = outputDir.getParentFile();
            File outputFile = new File(outputBase, relativePath.getPath());
            outputFile.getParentFile().mkdirs();
            
            if (texts.isEmpty()) {
                log("翻訳対象テキストなし - ファイルをコピー");
                Files.copy(sourceFile.toPath(), outputFile.toPath(), 
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                return QuestFileResult.createQuestFileResult(
                    sourceFile, outputFile, false, true, 0);
            }
            
            Map<String, String> translations = translateTextsMap(texts);
            
            parser.applyTranslations(sourceFile, outputFile, translations);
            
            log("Quest File翻訳完了: " + outputFile.getAbsolutePath());
            
            return QuestFileResult.createQuestFileResult(
                sourceFile, outputFile, true, true, characterCount);
            
        } catch (Exception e) {
            logError("Quest File翻訳エラー", sourceFile, e);
            return QuestFileResult.createQuestFileResult(
                sourceFile, null, true, false, characterCount);
        }
    }
    
    /**
     * バックアップを実行します（外部から呼び出し可能）。
     * @param modpackDir ModPackディレクトリ
     * @return バックアップ結果
     */
    public BackupManager.BackupResult executeBackup(File modpackDir) {
        try {
            log("バックアップ実行中...");
            BackupManager.BackupResult backupResult = backupManager.backup(modpackDir);
            
            if (backupResult != null) {
                log("バックアップ完了: " + backupResult.fileCount + "ファイル");
                log("バックアップ先: " + backupResult.backupPath);
                return backupResult;
            }
        } catch (Exception e) {
            log("バックアップ失敗: " + e.getMessage());
            log("警告: バックアップなしで翻訳を続行します");
        }
        return null;
    }
    
    /**
     * ログメッセージを出力します。
     */
    private void log(String message) {
        if (logger != null) {
            logger.onLog("[Quest] " + message);
        }
    }
    
    /**
     * エラーログを詳細情報付きで出力します。
     */
    private void logError(String prefix, File file, Exception e) {
        log(prefix + ": " + e.getMessage());
        log("ファイル: " + file.getAbsolutePath());
        
        Throwable cause = e.getCause();
        if (cause != null && cause.getMessage() != null) {
            log("原因: " + cause.getMessage());
        }
        
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        String stackTrace = sw.toString();
        String[] lines = stackTrace.split("\n");
        
        int linesToShow = Math.min(5, lines.length);
        for (int i = 0; i < linesToShow; i++) {
            log("  " + lines[i].trim());
        }
        
        if (lines.length > linesToShow) {
            log("  ... (残り " + (lines.length - linesToShow) + " 行)");
        }
    }
}
