package io.github.pazakasin.minecraft.modpack.translator.model;

/**
 * 翻訳処理の状態を定義するenum。
 * TranslatableFileで使用され、ファイルの処理進行状況を表現。
 */
public enum ProcessingState {
    /** 待機中（翻訳前） */
    PENDING("-"),
    
    /** 翻訳中 */
    TRANSLATING("翻訳中"),
    
    /** 翻訳完了 */
    COMPLETED("翻訳完了"),
    
    /** 翻訳失敗 */
    FAILED("失敗"),
    
    /** 既存ファイル使用 */
    EXISTING("既存適用"),
    
    /** 未処理（選択されていない） */
    SKIPPED("未処理");
    
    /** 処理状態の表示名。UI表示用。 */
    private final String displayName;
    
    /**
     * ProcessingStateのコンストラクタ。
     * @param displayName 表示名
     */
    ProcessingState(String displayName) {
        this.displayName = displayName;
    }
    
    /**
     * 処理状態の表示名を取得します。
     * @return 表示名
     */
    public String getDisplayName() {
        return displayName;
    }
}
