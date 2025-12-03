package io.github.pazakasin.minecraft.modpack.translator.service.quest;

/**
 * マッチした翻訳対象テキストの位置情報を保持するクラス。
 * SNBTParserで使用され、テキストの抽出と置換処理を支援。
 */
class TextMatch {
    /** キー名 */
    String key;
    
    /** 値 */
    String value;
    
    /** テキストの開始位置 */
    int start;
    
    /** テキストの終了位置 */
    int end;
    
    /** 配列値かどうか */
    boolean isArray;
    
    /**
     * TextMatchのコンストラクタ。
     * @param key キー名
     * @param value 値
     * @param start 開始位置
     * @param end 終了位置
     * @param isArray 配列値かどうか
     */
    TextMatch(String key, String value, int start, int end, boolean isArray) {
        this.key = key;
        this.value = value;
        this.start = start;
        this.end = end;
        this.isArray = isArray;
    }
}
