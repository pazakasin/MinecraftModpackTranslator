package io.github.pazakasin.minecraft.modpack.translator.model;

import io.github.pazakasin.minecraft.modpack.translator.model.QuestTranslationResult;

/**
 * 個別Modの処理結果を保持するデータクラス。
 * 翻訳の実行有無、成否、文字数などの統計情報を含む。
 */
public class ModProcessingResult {
    /** Mod名。JARファイル名から拡張子を除いた文字列。 */
    public String modName;
    
    /** 言語ファイルが格納されているフォルダパス。 */
    public String langFolderPath;
    
    /** 英語言語ファイル（en_us.json）が存在したか。 */
    public boolean hasEnUs = false;
    
    /** 日本語言語ファイル（ja_jp.json）が存在したか。 */
    public boolean hasJaJp = false;
    
    /** 翻訳処理を実行したか。 */
    public boolean translated = false;
    
    /** 翻訳対象の文字数。 */
    public int characterCount = 0;
    
    /** 翻訳に成功したか。 */
    public boolean translationSuccess = false;
    
    /** エラー情報。失敗時のみ設定される。 */
    public Exception errorException = null;
    
    /** クエスト翻訳結果。nullの場合クエスト処理なし。 */
    public QuestTranslationResult questResult = null;
    
    /**
     * ModProcessingResultのデフォルトコンストラクタ。
     */
    public ModProcessingResult() {
    }
    
    /**
     * Mod翻訳とクエスト翻訳の合計文字数を取得します。
     * @return 合計文字数
     */
    public int getTotalCharacterCount() {
        int total = characterCount;
        if (questResult != null) {
            total += questResult.getTotalCharacterCount();
        }
        return total;
    }
    
    /**
     * このオブジェクトの文字列表現を返します。
     * @return 文字列表現
     */
    @Override
    public String toString() {
        return "ModProcessingResult{" +
                "modName='" + modName + '\'' +
                ", langFolderPath='" + langFolderPath + '\'' +
                ", hasEnUs=" + hasEnUs +
                ", hasJaJp=" + hasJaJp +
                ", translated=" + translated +
                ", characterCount=" + characterCount +
                ", translationSuccess=" + translationSuccess +
                ", questResult=" + questResult +
                '}';
    }
}
