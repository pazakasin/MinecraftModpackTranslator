package io.github.pazakasin.minecraft.modpack.translator.service.analyzer;

import io.github.pazakasin.minecraft.modpack.translator.model.TranslatableFile;
import io.github.pazakasin.minecraft.modpack.translator.model.FileType;
import io.github.pazakasin.minecraft.modpack.translator.service.quest.QuestFileInfo;
import io.github.pazakasin.minecraft.modpack.translator.service.quest.QuestFileType;
import io.github.pazakasin.minecraft.modpack.translator.service.quest.QuestFileDetector;
import io.github.pazakasin.minecraft.modpack.translator.service.quest.LangFileSNBTExtractor;
import io.github.pazakasin.minecraft.modpack.translator.service.quest.SNBTParser;
import io.github.pazakasin.minecraft.modpack.translator.service.callback.LogCallback;
import io.github.pazakasin.minecraft.modpack.translator.service.callback.ProgressUpdateCallback;
import net.querz.nbt.tag.Tag;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * クエストファイルの解析を担当するクラス。
 * FTB Questsのファイルを検出し解析。
 */
public class QuestFileAnalyzer {
    /** ログコールバック。 */
    private final LogCallback logger;
    
    /** 進捗コールバック。 */
    private final ProgressUpdateCallback progressUpdater;
    
    /** クエストファイル検出器。 */
    private final QuestFileDetector questDetector;
    
    /** SNBT抽出器。 */
    private final LangFileSNBTExtractor snbtExtractor;
    
    /** SNBTパーサー。 */
    private final SNBTParser snbtParser;
    
    /**
     * QuestFileAnalyzerのコンストラクタ。
     * @param logger ログコールバック
     * @param progressUpdater 進捗コールバック
     */
    public QuestFileAnalyzer(LogCallback logger, ProgressUpdateCallback progressUpdater) {
        this.logger = logger;
        this.progressUpdater = progressUpdater;
        this.questDetector = new QuestFileDetector();
        this.snbtExtractor = new LangFileSNBTExtractor();
        this.snbtParser = new SNBTParser();
    }
    
    /**
     * クエストファイルを解析します。
     * @param inputPath ModPackディレクトリパス
     * @return 翻訳対象ファイルのリスト
     * @throws Exception ファイルアクセスエラー等
     */
    public List<TranslatableFile> analyze(String inputPath) throws Exception {
        List<TranslatableFile> files = new ArrayList<TranslatableFile>();
        
        File modpackDir = new File(inputPath);
        List<QuestFileInfo> questFileInfos = questDetector.detectQuestFiles(modpackDir);
        
        if (questFileInfos.isEmpty()) {
            log("");
            log("クエストファイルが見つかりません。");
            return files;
        }
        
        log("");
        log("検出されたクエストファイル数: " + questFileInfos.size());
        log("");
        
        for (int i = 0; i < questFileInfos.size(); i++) {
            QuestFileInfo info = questFileInfos.get(i);
            int currentNum = i + 1;
            int totalNum = questFileInfos.size();
            
            updateProgress(String.format("[Quest %d/%d] 解析中: %s", 
                currentNum, totalNum, info.getFile().getName()));
            
            try {
                TranslatableFile file = analyzeFile(info);
                if (file != null) {
                    files.add(file);
                    log(String.format("[Quest %d/%d] %s - %s (%d文字)", 
                        currentNum, totalNum, info.getFile().getName(),
                        info.getType() == QuestFileType.LANG_FILE ? 
                            "言語ファイル" : "クエストファイル",
                        file.getCharacterCount()));
                }
            } catch (Exception e) {
                log(String.format("[Quest %d/%d][エラー] %s: %s", 
                    currentNum, totalNum, info.getFile().getName(), e.getMessage()));
            }
        }
        
        updateProgress(" ");
        return files;
    }
    
    /**
     * 単一のクエストファイルを解析します。
     * @param info クエストファイル情報
     * @return 翻訳対象ファイル
     * @throws Exception 解析エラー
     */
    private TranslatableFile analyzeFile(QuestFileInfo info) throws Exception {
        File file = info.getFile();
        String content = new String(Files.readAllBytes(file.toPath()), "UTF-8");
        
        int charCount = 0;
        String fileId = file.getName().replace(".snbt", "");
        
        if (info.getType() == QuestFileType.LANG_FILE) {
            try {
                Tag<?> rootTag = snbtParser.parse(file);
                java.util.List<LangFileSNBTExtractor.ExtractedText> texts = snbtExtractor.extract(rootTag);
                
                for (LangFileSNBTExtractor.ExtractedText text : texts) {
                    charCount += text.getValue().length();
                }
                
                if (charCount == 0) {
                    return null;
                }
                
                File jaJpFile = info.getJaJpFile();
                boolean hasJaJp = info.hasJaJp();
                String jaJpContent = null;
                
                if (hasJaJp && jaJpFile != null && jaJpFile.exists()) {
                    jaJpContent = new String(Files.readAllBytes(jaJpFile.toPath()), "UTF-8");
                }
                
                return TranslatableFile.createQuestLangFile(
                    file.getAbsolutePath(),
                    fileId,
                    charCount,
                    hasJaJp,
                    content,
                    jaJpContent
                );
            } catch (Exception e) {
                log("Quest言語ファイル解析エラー: " + file.getName() + " - " + e.getMessage());
                return null;
            }
        } else {
            try {
                java.util.Map<String, String> texts = snbtParser.extractTranslatableTexts(file);
                
                for (String value : texts.values()) {
                    charCount += value.length();
                }
                
                return TranslatableFile.createQuestFile(
                    file.getAbsolutePath(),
                    fileId,
                    charCount,
                    content
                );
            } catch (Exception e) {
                log("Questファイル解析エラー: " + file.getName() + " - " + e.getMessage());
                return null;
            }
        }
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
