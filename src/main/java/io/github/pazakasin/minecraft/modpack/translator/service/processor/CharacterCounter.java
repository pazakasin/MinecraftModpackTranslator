package io.github.pazakasin.minecraft.modpack.translator.service.processor;

import java.util.Map;

import com.google.gson.JsonObject;

/**
 * JSON言語ファイルの翻訳対象文字数をカウントするクラス。
 * JSONの値部分（ダブルクォート内のテキスト）のみをカウント。
 * 改行文字も文字数に含めます（翻訳実行時と一致）。
 * 配列等を含むJSONは、翻訳時と同じ展開処理で翻訳対象となる文字列のみをカウントします。
 */
public class CharacterCounter {
    /** 配列等を含むJSONの展開を行うクラス。 */
    private final JsonLangFlattener flattener = new JsonLangFlattener();

    
    /**
     * JSON言語ファイルの翻訳対象文字数をカウントします。
     * 改行文字（\nや\r）も含めてカウントします。
     * @param jsonContent カウント対象のJSONコンテンツ
     * @return 翻訳対象の文字数（エラー時は0）
     */
    public int countCharacters(String jsonContent) {
        Integer structuredCount = countStructured(jsonContent);
        if (structuredCount != null) {
            return structuredCount;
        }
        return countLegacy(jsonContent);
    }

    /**
     * 配列等を含むJSONの翻訳対象文字数を、翻訳時と同じ展開処理でカウントします。
     * @param jsonContent カウント対象のJSONコンテンツ
     * @return 文字数（値がすべて文字列の従来形式、または解析不可の場合はnull）
     */
    private Integer countStructured(String jsonContent) {
        try {
            JsonObject root = flattener.parse(jsonContent);
            if (flattener.isSimple(root)) {
                return null;
            }
            int count = 0;
            Map<String, String> flat = flattener.flatten(root);
            for (String value : flat.values()) {
                count += value.length();
            }
            return count;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * 従来形式（値がすべて文字列）のJSONの翻訳対象文字数をカウントします。
     * @param jsonContent カウント対象のJSONコンテンツ
     * @return 翻訳対象の文字数（エラー時は0）
     */
    private int countLegacy(String jsonContent) {
        int count = 0;
        try {
            boolean inValue = false;
            boolean escaping = false;
            
            for (int i = 0; i < jsonContent.length(); i++) {
                char c = jsonContent.charAt(i);
                
                if (escaping) {
                    escaping = false;
                    if (inValue) count++;
                    continue;
                }
                
                if (c == '\\') {
                    escaping = true;
                    continue;
                }
                
                if (c == ':' && !inValue) {
                    for (int j = i + 1; j < jsonContent.length(); j++) {
                        if (jsonContent.charAt(j) == '"') {
                            inValue = true;
                            i = j;
                            break;
                        } else if (jsonContent.charAt(j) != ' ' && jsonContent.charAt(j) != '\n') {
                            break;
                        }
                    }
                } else if (c == '"' && inValue) {
                    inValue = false;
                } else if (inValue) {
                    count++;
                }
            }
        } catch (Exception e) {
            return 0;
        }
        
        return count;
    }
}
