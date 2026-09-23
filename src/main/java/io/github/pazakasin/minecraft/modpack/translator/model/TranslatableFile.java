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
    
    /** 翻訳可能か（falseは非対応形式で検出のみ。選択・翻訳不可）。 */
    private boolean translatable;

    /** 翻訳履歴エントリ（loadフォルダから読み込み）。 */
    private io.github.pazakasin.minecraft.modpack.translator.comparison.TranslationHistoryEntry historyEntry;
    
    /**
     * TranslatableFileのデフォルトコンストラクタ。
     */
    public TranslatableFile() {
    this.selected = true;
    this.translatable = true;
    this.processingState = ProcessingState.PENDING;
    this.resultMessage = ProcessingState.PENDING.getDisplayName();
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
        file.applyInitialState();
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
        file.applyInitialState();
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
        file.applyInitialState();
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
        file.applyInitialState();
        return file;
    }
    
    /**
     * OpenLoader言語ファイル用のインスタンスを作成します。
     * @param filePath ファイルパス
     * @param fileId namespace（ファイル識別子）
     * @param characterCount 文字数
     * @param hasExistingJaJp 既存の日本語ファイルの有無
     * @param fileContent ファイル内容
     * @param existingJaJpContent 既存の日本語ファイル内容
     * @return TranslatableFile
     */
    public static TranslatableFile createOpenLoaderLangFile(String filePath, String fileId,
                                                              int characterCount, boolean hasExistingJaJp,
                                                              String fileContent, String existingJaJpContent) {
        TranslatableFile file = new TranslatableFile();
        file.fileType = FileType.OPENLOADER_LANG_FILE;
        file.modName = fileId;
        file.sourceFilePath = filePath;
        file.langFolderPath = extractOpenLoaderRelativePath(filePath);
        file.fileId = fileId;
        file.characterCount = characterCount;
        file.hasExistingJaJp = hasExistingJaJp;
        file.fileContent = fileContent;
        file.existingJaJpContent = existingJaJpContent;
        file.selected = !hasExistingJaJp;
        file.applyInitialState();
        return file;
    }

    /**
     * Config（その他）の言語ファイル用のインスタンスを作成します（初期状態は未選択）。
     * @param filePath ファイルパス
     * @param relativePath ModPackルートからの相対パス（区切りは「/」）
     * @param fileId 識別名（config配下のフォルダ名）
     * @param characterCount 文字数
     * @param hasExistingJaJp 既存の日本語ファイルの有無
     * @param fileContent ファイル内容
     * @param existingJaJpContent 既存の日本語ファイル内容
     * @return TranslatableFile
     */
    public static TranslatableFile createConfigLangFile(String filePath, String relativePath,
                                                        String fileId, int characterCount,
                                                        boolean hasExistingJaJp, String fileContent,
                                                        String existingJaJpContent) {
        TranslatableFile file = new TranslatableFile();
        file.fileType = FileType.CONFIG_LANG_FILE;
        file.modName = fileId;
        file.sourceFilePath = filePath;
        file.langFolderPath = relativePath;
        file.fileId = fileId;
        file.characterCount = characterCount;
        file.hasExistingJaJp = hasExistingJaJp;
        file.fileContent = fileContent;
        file.existingJaJpContent = existingJaJpContent;
        file.selected = false;
        file.applyInitialState();
        return file;
    }

    /**
     * Config（その他）の非対応形式ファイル用のインスタンスを作成します（検出のみ・選択不可）。
     * @param filePath ファイルパス
     * @param relativePath ModPackルートからの相対パス（区切りは「/」）
     * @param fileId 識別名（config配下のフォルダ名）
     * @param hasExistingJaJp 同形式の日本語ファイルの有無
     * @param fileContent ファイル内容（読込不可の場合null）
     * @param reason 非対応の理由（状態欄に表示）
     * @return TranslatableFile
     */
    public static TranslatableFile createUnsupportedConfigLangFile(String filePath, String relativePath,
                                                                   String fileId, boolean hasExistingJaJp,
                                                                   String fileContent, String reason) {
        TranslatableFile file = createConfigLangFile(filePath, relativePath, fileId, 0,
                hasExistingJaJp, fileContent, null);
        file.translatable = false;
        file.processingState = ProcessingState.PENDING;
        file.resultMessage = reason;
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

    /**
     * OpenLoaderファイルパスから相対パスを抽出します。
     * config/openloader/resources以降のパス（ファイル名含む）を返します。
     */
    private static String extractOpenLoaderRelativePath(String filePath) {
        String path = filePath.replace("\\", "/");
        int openLoaderIndex = path.indexOf("config/openloader/resources");

        if (openLoaderIndex != -1) {
            return path.substring(openLoaderIndex);
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

    /**
     * 翻訳可能かを取得します。
     * @return falseの場合は非対応形式（検出のみ）
     */
    public boolean isTranslatable() {
        return translatable;
    }
    
    /**
     * 選択状態を設定します（選択不可のファイルは常に未選択）。
     * @param selected 選択状態
     */
    public void setSelected(boolean selected) {
        this.selected = selected && isSelectable();
    }

    /**
     * 選択（翻訳）可能かを取得します。
     * 非対応形式、既存の日本語ファイルあり、翻訳対象文字数0のQuestファイルは選択不可です。
     * @return 選択可能な場合true
     */
    public boolean isSelectable() {
        return translatable && !hasExistingJaJp && !isEmptyQuestFile();
    }

    /**
     * 翻訳対象文字数0のQuestファイルかを判定します。
     * @return 翻訳対象テキストがないQuestファイルの場合true
     */
    private boolean isEmptyQuestFile() {
        return fileType == FileType.QUEST_FILE && characterCount == 0;
    }

    /**
     * 解析直後の状態を設定します。
     * 選択不可のファイルは「既存あり」または「対象なし」（いずれも出力なし）とします。
     */
    private void applyInitialState() {
        if (hasExistingJaJp) {
            processingState = ProcessingState.EXISTING;
        } else if (isEmptyQuestFile()) {
            processingState = ProcessingState.NO_TARGET;
        } else {
            return;
        }
        resultMessage = processingState.getDisplayName();
        selected = false;
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
    
    /**
     * 翻訳履歴エントリを取得します。
     * @return 翻訳履歴エントリ
     */
    public io.github.pazakasin.minecraft.modpack.translator.comparison.TranslationHistoryEntry getHistoryEntry() {
        return historyEntry;
    }
    
    /**
     * 翻訳履歴エントリを設定し、状態を「履歴あり」に更新します。
     * @param historyEntry 翻訳履歴エントリ
     */
    public void setHistoryEntry(io.github.pazakasin.minecraft.modpack.translator.comparison.TranslationHistoryEntry historyEntry) {
        this.historyEntry = historyEntry;
        if (historyEntry != null && this.processingState == ProcessingState.PENDING) {
            this.processingState = ProcessingState.HAS_HISTORY;
            this.resultMessage = ProcessingState.HAS_HISTORY.getDisplayName();
        }
    }
}
