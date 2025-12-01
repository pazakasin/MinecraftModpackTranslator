package io.github.pazakasin.minecraft.modpack.translator.model;

import java.io.File;

/**
 * 翻訳対象ファイルの情報を保持するモデルクラス。
 * 解析フェーズで作成され、ファイル選択UIと翻訳実行フェーズで使用される。
 */
public class TranslatableFile {
    /** ファイルの種別を表す列挙型。 */
    public enum FileType {
        /** Mod内の言語ファイル（en_us.json） */
        MOD_LANG_FILE("Mod言語ファイル"),
        /** FTBクエストの言語ファイル（en_us.snbt） */
        QUEST_LANG_FILE("Quest言語ファイル"),
        /** FTBクエストファイル（chapter*.snbt等） */
        QUEST_FILE("Questファイル");
        
        private final String displayName;
        
        FileType(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    /** ファイルの種別。 */
    private FileType fileType;
    
    /** Mod名またはファイル識別名。 */
    private String modName;
    
    /** 元のファイルパス（JARファイルパスまたは実ファイルパス）。 */
    private String sourceFilePath;
    
    /** JAR内の言語ファイルパス（例: assets/modid/lang/en_us.json）。 */
    private String langFolderPath;
    
    /** 翻訳対象文字数。 */
    private int characterCount;
    
    /** 既に日本語ファイルが存在するか。 */
    private boolean hasExistingJaJp;
    
    /** ユーザーによる選択状態（翻訳対象とするか）。 */
    private boolean selected;
    
    /** 元のファイル内容（en_us.jsonまたはen_us.snbtの内容）。 */
    private String fileContent;
    
    /** 既存の日本語ファイル内容（存在する場合）。 */
    private String existingJaJpContent;
    
    /** ModIDまたはファイル識別子。 */
    private String fileId;
    
    /** workフォルダ内のエクスポート先パス。 */
    private String workFilePath;
    
    /**
     * TranslatableFileのデフォルトコンストラクタ。
     */
    public TranslatableFile() {
        this.selected = true; // デフォルトで選択状態
    }
    
    /**
     * Mod言語ファイル用のインスタンスを作成します。
     * @param modName Mod名
     * @param jarFilePath JARファイルパス
     * @param langFolderPath 言語ファイルのパス
     * @param fileId ModID
     * @param characterCount 文字数
     * @param hasExistingJaJp 既存の日本語ファイルの有無
     * @param fileContent ファイル内容
     * @param existingJaJpContent 既存の日本語ファイル内容
     * @return TranslatableFile
     */
    public static TranslatableFile createModLangFile(String modName, String jarFilePath,
                                                     String langFolderPath, String fileId,
                                                     int characterCount, boolean hasExistingJaJp,
                                                     String fileContent, String existingJaJpContent) {
        TranslatableFile file = new TranslatableFile();
        file.fileType = FileType.MOD_LANG_FILE;
        file.modName = modName;
        file.sourceFilePath = jarFilePath;
        file.langFolderPath = langFolderPath;
        file.fileId = fileId;
        file.characterCount = characterCount;
        file.hasExistingJaJp = hasExistingJaJp;
        file.fileContent = fileContent;
        file.existingJaJpContent = existingJaJpContent;
        file.selected = !hasExistingJaJp; // 既存のja_jpがある場合は初期状態で非選択
        return file;
    }
    
    /**
     * クエスト言語ファイル用のインスタンスを作成します。
     * @param filePath ファイルパス
     * @param fileId ファイル識別子
     * @param characterCount 文字数
     * @param fileContent ファイル内容
     * @return TranslatableFile
     */
    public static TranslatableFile createQuestLangFile(String filePath, String fileId,
                                                       int characterCount, String fileContent) {
        TranslatableFile file = new TranslatableFile();
        file.fileType = FileType.QUEST_LANG_FILE;
        file.modName = "FTB Quests";
        file.sourceFilePath = filePath;
        file.langFolderPath = extractRelativePath(filePath);
        file.fileId = fileId;
        file.characterCount = characterCount;
        file.hasExistingJaJp = false;
        file.fileContent = fileContent;
        file.selected = true;
        return file;
    }
    
    /**
     * クエストファイル用のインスタンスを作成します。
     * @param filePath ファイルパス
     * @param fileId ファイル識別子
     * @param characterCount 文字数
     * @param fileContent ファイル内容
     * @return TranslatableFile
     */
    public static TranslatableFile createQuestFile(String filePath, String fileId,
                                                   int characterCount, String fileContent) {
        TranslatableFile file = new TranslatableFile();
        file.fileType = FileType.QUEST_FILE;
        file.modName = "FTB Quests";
        file.sourceFilePath = filePath;
        file.langFolderPath = extractRelativePath(filePath);
        file.fileId = fileId;
        file.characterCount = characterCount;
        file.hasExistingJaJp = false;
        file.fileContent = fileContent;
        file.selected = true;
        return file;
    }
    
    /**
     * ファイルパスから相対パスを抽出します。
     */
    private static String extractRelativePath(String filePath) {
        String path = filePath.replace("\\", "/");
        int configIndex = path.indexOf("config/ftbquests");
        
        if (configIndex != -1) {
            return path.substring(configIndex);
        }
        
        return filePath;
    }
    
    // Getters and Setters
    
    public FileType getFileType() {
        return fileType;
    }
    
    public void setFileType(FileType fileType) {
        this.fileType = fileType;
    }
    
    public String getModName() {
        return modName;
    }
    
    public void setModName(String modName) {
        this.modName = modName;
    }
    
    public String getSourceFilePath() {
        return sourceFilePath;
    }
    
    public void setSourceFilePath(String sourceFilePath) {
        this.sourceFilePath = sourceFilePath;
    }
    
    public String getLangFolderPath() {
        return langFolderPath;
    }
    
    public void setLangFolderPath(String langFolderPath) {
        this.langFolderPath = langFolderPath;
    }
    
    public int getCharacterCount() {
        return characterCount;
    }
    
    public void setCharacterCount(int characterCount) {
        this.characterCount = characterCount;
    }
    
    public boolean isHasExistingJaJp() {
        return hasExistingJaJp;
    }
    
    public void setHasExistingJaJp(boolean hasExistingJaJp) {
        this.hasExistingJaJp = hasExistingJaJp;
    }
    
    public boolean isSelected() {
        return selected;
    }
    
    public void setSelected(boolean selected) {
        this.selected = selected;
    }
    
    public String getFileContent() {
        return fileContent;
    }
    
    public void setFileContent(String fileContent) {
        this.fileContent = fileContent;
    }
    
    public String getExistingJaJpContent() {
        return existingJaJpContent;
    }
    
    public void setExistingJaJpContent(String existingJaJpContent) {
        this.existingJaJpContent = existingJaJpContent;
    }
    
    public String getFileId() {
        return fileId;
    }
    
    public void setFileId(String fileId) {
        this.fileId = fileId;
    }
    
    public String getWorkFilePath() {
        return workFilePath;
    }
    
    public void setWorkFilePath(String workFilePath) {
        this.workFilePath = workFilePath;
    }
}
