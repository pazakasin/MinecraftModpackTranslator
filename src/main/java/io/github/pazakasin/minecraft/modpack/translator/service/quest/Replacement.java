package io.github.pazakasin.minecraft.modpack.translator.service.quest;

/**
 * 置換情報を保持するクラス。
 * SNBTParserで使用され、翻訳適用時の置換処理を管理。
 */
class Replacement {
    /** 置換開始位置 */
    int start;
    
    /** 置換終了位置 */
    int end;
    
    /** 置換後の文字列 */
    String replacement;
    
    /**
     * Replacementのコンストラクタ。
     * @param start 置換開始位置
     * @param end 置換終了位置
     * @param replacement 置換後の文字列
     */
    Replacement(int start, int end, String replacement) {
        this.start = start;
        this.end = end;
        this.replacement = replacement;
    }
}
