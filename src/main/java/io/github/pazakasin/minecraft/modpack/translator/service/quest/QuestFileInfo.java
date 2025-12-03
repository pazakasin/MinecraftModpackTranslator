package io.github.pazakasin.minecraft.modpack.translator.service.quest;

import java.io.File;

/**
 * 検出されたクエストファイルの情報を保持するクラス。
 * QuestFileDetectorで使用され、ファイルとそのメタデータを管理。
 */
public class QuestFileInfo {
    /** 検出されたファイル */
    private final File file;
    
    /** ファイルの種別 */
    private final QuestFileType type;
    
    /** 既存の日本語ファイル（存在する場合） */
    private final File jaJpFile;
    
    /**
     * QuestFileInfoのコンストラクタ（日本語ファイルなし）。
     * @param file 検出されたファイル
     * @param type ファイルの種別
     */
    public QuestFileInfo(File file, QuestFileType type) {
        this(file, type, null);
    }
    
    /**
     * QuestFileInfoのコンストラクタ（日本語ファイルあり）。
     * @param file 検出されたファイル
     * @param type ファイルの種別
     * @param jaJpFile 既存の日本語ファイル
     */
    public QuestFileInfo(File file, QuestFileType type, File jaJpFile) {
        this.file = file;
        this.type = type;
        this.jaJpFile = jaJpFile;
    }
    
    /**
     * 検出されたファイルを取得します。
     * @return ファイル
     */
    public File getFile() {
        return file;
    }
    
    /**
     * ファイルの種別を取得します。
     * @return ファイルの種別
     */
    public QuestFileType getType() {
        return type;
    }
    
    /**
     * 既存の日本語ファイルが存在するかを判定します。
     * @return 存在する場合true
     */
    public boolean hasJaJp() {
        return jaJpFile != null && jaJpFile.exists();
    }
    
    /**
     * 既存の日本語ファイルを取得します。
     * @return 日本語ファイル（存在しない場合null）
     */
    public File getJaJpFile() {
        return jaJpFile;
    }
    
    /**
     * 文字列表現を返します。
     * @return 文字列表現
     */
    @Override
    public String toString() {
        return "QuestFileInfo{file=" + file.getPath() + ", type=" + type + ", hasJaJp=" + hasJaJp() + "}";
    }
}
