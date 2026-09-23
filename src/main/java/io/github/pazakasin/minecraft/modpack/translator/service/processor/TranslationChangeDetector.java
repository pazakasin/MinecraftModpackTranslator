package io.github.pazakasin.minecraft.modpack.translator.service.processor;

import java.util.Map;

/**
 * 翻訳結果が原文から変更されているかを判定するクラス。
 * 未修正のファイルを出力しない（ModPack内ファイルの単純な複製を配布しない）ために使用する。
 */
public class TranslationChangeDetector {
    /** JSON展開処理（翻訳時と同じ展開ルールで比較するため）。 */
    private final JsonLangFlattener flattener;

    /**
     * TranslationChangeDetectorのコンストラクタ。
     */
    public TranslationChangeDetector() {
        this.flattener = new JsonLangFlattener();
    }

    /**
     * JSON言語ファイルの翻訳結果が原文と同一かを判定します。
     * 整形の違いは無視し、JsonLangFlattenerで展開した「キー→文字列」同士を比較します。
     * 解析できない場合は文字列そのもの（前後の空白除く）で比較します。
     * @param originalJson 原文JSON
     * @param translatedJson 翻訳結果JSON
     * @return 翻訳による変更がない場合true
     */
    public boolean isJsonUnchanged(String originalJson, String translatedJson) {
        if (originalJson == null || translatedJson == null) {
            return false;
        }
        try {
            Map<String, String> original = flattener.flatten(flattener.parse(originalJson));
            Map<String, String> translated = flattener.flatten(flattener.parse(translatedJson));
            return original.equals(translated);
        } catch (IllegalArgumentException e) {
            return originalJson.trim().equals(translatedJson.trim());
        }
    }

    /**
     * テキストファイル（SNBT等）の翻訳結果が原文と同一かを判定します。
     * @param originalText 原文
     * @param translatedText 翻訳結果
     * @return 翻訳による変更がない場合true
     */
    public boolean isTextUnchanged(String originalText, String translatedText) {
        if (originalText == null || translatedText == null) {
            return false;
        }
        return originalText.equals(translatedText);
    }
}
