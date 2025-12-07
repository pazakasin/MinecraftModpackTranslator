package io.github.pazakasin.minecraft.modpack.translator.model;

import java.io.File;

/**
 * 翻訳対象ファイルの情報を保持するモデルクラス。
 * 解析フェーズで作成され、ファイル選択UIと翻訳実行フェーズで使用される。
 */
public class TranslatableFile {
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
    
    /** 翻訳処理の現在の状態。 */
    private ProcessingState processingState;
    
    /** 翻訳処理の結果メッセージ。 */
    private String resultMessage;
    
    /** 翻訳処理の現在の進捗（処理済みキー数）。 */
    private int currentProgress;
    
    /** 翻訳処理の合計キー数。 */
    private int totalProgress;
    
    /**
     * TranslatableFileのデフォルトコンストラクタ。
     */
    public TranslatableFile() {
        this.selected = true;
        this.processingState = ProcessingState.PENDING;
        this.resultMessage = "-";
        this.currentProgress = 0;
        this.totalProgress = 0;
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
        file.selected = !hasExistingJaJp;
        return file;
    }
    
    /**
     * クエスト言語ファイル用のインスタンスを作成します。
     * @param filePath ファイルパス
     * @param fileId ファイル識別子
     * @param characterCount 文字数
     * @param hasExistingJaJp 既存の日本語ファイルの有無
     * @param fileContent ファイル内容
     * @param existingJaJpContent 既存の日本語ファイル内容
     * @return TranslatableFile
     */
    public static TranslatableFile createQuestLangFile(String filePath, String fileId,
                                                       int characterCount, boolean hasExistingJaJp,
                                                       String fileContent, String existingJaJpContent) {
        TranslatableFile file = new TranslatableFile();
        file.fileType = FileType.QUEST_LANG_FILE;
        file.modName = "FTB Quests Lang";
        file.sourceFilePath = filePath;
        file.langFolderPath = extractRelativePath(filePath);
        file.fileId = fileId;
        file.characterCount = characterCount;
        file.hasExistingJaJp = hasExistingJaJp;
        file.fileContent = fileContent;
        file.existingJaJpContent = existingJaJpContent;
        file.selected = !hasExistingJaJp;
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
        File f = new File(filePath);
        file.modName = f.getName();
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
     * KubeJS言語ファイル用のインスタンスを作成します。
     * @param filePath ファイルパス
     * @param fileId 言語ファイルID
     * @param characterCount 文字数
     * @param hasExistingJaJp 既存の日本語ファイルの有無
     * @param fileContent ファイル内容
     * @param existingJaJpContent 既存の日本語ファイル内容
     * @return TranslatableFile
     */
    public static TranslatableFile createKubeJSLangFile(String filePath, String fileId,
                                                         int characterCount, boolean hasExistingJaJp,
                                                         String fileContent, String existingJaJpContent) {
        TranslatableFile file = new TranslatableFile();
        file.fileType = FileType.KUBEJS_LANG_FILE;
        file.modName = fileId;
        file.sourceFilePath = filePath;
        file.langFolderPath = extractKubeJSRelativePath(filePath);
        file.fileId = fileId;
        file.characterCount = characterCount;
        file.hasExistingJaJp = hasExistingJaJp;
        file.fileContent = fileContent;
        file.existingJaJpContent = existingJaJpContent;
        file.selected = !hasExistingJaJp;
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
    
    /**
     * KubeJSファイルパスから相対パスを抽出します。
     */
    private static String extractKubeJSRelativePath(String filePath) {
        String path = filePath.replace("\\", "/");
        int kubeJsIndex = path.indexOf("kubejs/assets/");
        
        if (kubeJsIndex != -1) {
            return path.substring(kubeJsIndex);
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
    
    public ProcessingState getProcessingState() {
        return processingState;
    }
    
    public void setProcessingState(ProcessingState processingState) {
        this.processingState = processingState;
    }
    
    public String getResultMessage() {
        return resultMessage;
    }
    
    public void setResultMessage(String resultMessage) {
        this.resultMessage = resultMessage;
    }
    
    public int getCurrentProgress() {
        return currentProgress;
    }
    
    public void setCurrentProgress(int currentProgress) {
        this.currentProgress = currentProgress;
    }
    
    public int getTotalProgress() {
        return totalProgress;
    }
    
    public void setTotalProgress(int totalProgress) {
        this.totalProgress = totalProgress;
    }
    
    /**
     * 翻訳進捗を設定し、結果メッセージを更新します。
     * @param current 現在の進捗
     * @param total 合計
     */
    public void setProgress(int current, int total) {
        this.currentProgress = current;
        this.totalProgress = total;
        if (total > 0 && this.processingState == ProcessingState.TRANSLATING) {
            this.resultMessage = String.format("翻訳中 (%d/%d)", current, total);
        }
    }
}
