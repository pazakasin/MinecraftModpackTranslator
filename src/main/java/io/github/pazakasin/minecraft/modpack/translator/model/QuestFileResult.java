package io.github.pazakasin.minecraft.modpack.translator.model;

import java.io.File;

/**
 * 個別クエストファイルの処理結果を保持するクラス。
 */
public class QuestFileResult {
    /** ファイル名（例: chapter1.snbt, en_us.snbt）。 */
    public String fileName;
    
    /** ファイルの相対パス（config/ftbquestsからの相対）。 */
    public String filePath;
    
    /** ファイル種別（Lang File / Quest File）。 */
    public String fileType;
    
    /** 翻訳を実行したか。 */
    public boolean translated = false;
    
    /** 翻訳が成功したか。 */
    public boolean success = false;
    
    /** 翻訳対象文字数。 */
    public int characterCount = 0;
    
    /** 出力先ファイルパス（絶対パス）。 */
    public String outputPath;
    
    /**
     * QuestFileResultのデフォルトコンストラクタ。
     */
    public QuestFileResult() {
    }
    
    /**
     * Lang File用の結果を作成します。
     * @param inputFile 入力ファイル
     * @param outputFile 出力ファイル
     * @param translated 翻訳実行フラグ
     * @param success 成功フラグ
     * @param charCount 文字数
     * @return QuestFileResult
     */
    public static QuestFileResult createLangFileResult(File inputFile, File outputFile,
                                                       boolean translated, boolean success, 
                                                       int charCount) {
        QuestFileResult result = new QuestFileResult();
        result.fileName = inputFile.getName();
        result.filePath = extractRelativePath(inputFile);
        result.fileType = "Quest言語ファイル";
        result.translated = translated;
        result.success = success;
        result.characterCount = charCount;
        result.outputPath = outputFile != null ? outputFile.getAbsolutePath() : "-";
        return result;
    }
    
    /**
     * クエストファイル用の結果を作成します。
     * @param inputFile 入力ファイル
     * @param outputFile 出力ファイル
     * @param translated 翻訳実行フラグ
     * @param success 成功フラグ
     * @param charCount 文字数
     * @return QuestFileResult
     */
    public static QuestFileResult createQuestFileResult(File inputFile, File outputFile,
                                                        boolean translated, boolean success,
                                                        int charCount) {
        QuestFileResult result = new QuestFileResult();
        result.fileName = inputFile.getName();
        result.filePath = extractRelativePath(inputFile);
        result.fileType = "Questファイル";
        result.translated = translated;
        result.success = success;
        result.characterCount = charCount;
        result.outputPath = outputFile != null ? outputFile.getAbsolutePath() : "-";
        return result;
    }
    
    /**
     * ファイルから相対パスを抽出します。
     */
    private static String extractRelativePath(File file) {
        String path = file.getAbsolutePath().replace("\\", "/");
        int configIndex = path.indexOf("config/ftbquests");
        
        if (configIndex != -1) {
            return path.substring(configIndex);
        }
        
        return file.getPath();
    }
}
