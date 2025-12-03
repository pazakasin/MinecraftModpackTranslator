package io.github.pazakasin.minecraft.modpack.translator.model;

/**
 * 翻訳対象ファイルの種別を定義するenum。
 * TranslatableFileで使用され、ファイルの処理方法を決定。
 */
public enum FileType {
    /** Mod内の言語ファイル（en_us.json） */
    MOD_LANG_FILE("Mod言語ファイル"),
    
    /** FTBクエストの言語ファイル（en_us.snbt） */
    QUEST_LANG_FILE("Quest言語ファイル"),
    
    /** FTBクエストファイル（chapter*.snbt等） */
    QUEST_FILE("Questファイル"),
    
    /** KubeJSの言語ファイル（en_us.json） */
    KUBEJS_LANG_FILE("KubeJS言語ファイル");
    
    /** ファイルタイプの表示名。UI表示用。 */
    private final String displayName;
    
    /**
     * FileTypeのコンストラクタ。
     * @param displayName 表示名
     */
    FileType(String displayName) {
        this.displayName = displayName;
    }
    
    /**
     * ファイルタイプの表示名を取得します。
     * @return 表示名
     */
    public String getDisplayName() {
        return displayName;
    }
}
