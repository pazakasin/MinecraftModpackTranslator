package io.github.pazakasin.minecraft.modpack.translator.comparison;

/**
 * 翻訳前後の比較結果を保持するデータクラス
 */
public class ComparisonResult {
    /**
     * 変更タイプ
     */
    public enum ChangeType {
        /** 追加された項目 */
        ADDED,
        /** 削除された項目 */
        REMOVED,
        /** 変更された項目 */
        MODIFIED,
        /** 変更なし */
        UNCHANGED,
        /** キーが存在しないエラー */
        ERROR_KEY_NOT_FOUND
    }
    
    /** JSONキー */
    private final String key;
    /** 翻訳前の値 */
    private final String originalValue;
    /** 翻訳後の値 */
    private final String translatedValue;
    /** 変更タイプ */
    private final ChangeType changeType;
    
    /**
     * コンストラクタ
     * 
     * @param key JSONキー
     * @param originalValue 翻訳前の値
     * @param translatedValue 翻訳後の値
     * @param changeType 変更タイプ
     */
    public ComparisonResult(String key, String originalValue, String translatedValue, ChangeType changeType) {
        this.key = key;
        this.originalValue = originalValue;
        this.translatedValue = translatedValue;
        this.changeType = changeType;
    }
    
    /**
     * JSONキーを取得
     * 
     * @return JSONキー
     */
    public String getKey() {
        return key;
    }
    
    /**
     * 翻訳前の値を取得
     * 
     * @return 翻訳前の値
     */
    public String getOriginalValue() {
        return originalValue;
    }
    
    /**
     * 翻訳後の値を取得
     * 
     * @return 翻訳後の値
     */
    public String getTranslatedValue() {
        return translatedValue;
    }
    
    /**
     * 変更タイプを取得
     * 
     * @return 変更タイプ
     */
    public ChangeType getChangeType() {
        return changeType;
    }
}
