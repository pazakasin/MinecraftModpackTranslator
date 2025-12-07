package io.github.pazakasin.minecraft.modpack.translator.comparison;

/**
 * 翻訳履歴読込時の例外。
 */
public class TranslationHistoryException extends Exception {
    /** シリアルバージョンUID */
    private static final long serialVersionUID = 1L;
    
    /**
     * コンストラクタ。
     * @param message エラーメッセージ
     */
    public TranslationHistoryException(String message) {
        super(message);
    }
    
    /**
     * コンストラクタ。
     * @param message エラーメッセージ
     * @param cause 原因となった例外
     */
    public TranslationHistoryException(String message, Throwable cause) {
        super(message, cause);
    }
}
