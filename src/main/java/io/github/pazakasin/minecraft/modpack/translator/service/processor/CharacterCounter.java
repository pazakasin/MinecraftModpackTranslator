package io.github.pazakasin.minecraft.modpack.translator.service.processor;

/**
 * JSON言語ファイルの翻訳対象文字数をカウントするクラス。
 * JSONの値部分（ダブルクォート内のテキスト）のみをカウント。
 * 改行文字も文字数に含めます（翻訳実行時と一致）。
 */
public class CharacterCounter {
    
    /**
     * JSON言語ファイルの翻訳対象文字数をカウントします。
     * 改行文字（\nや\r）も含めてカウントします。
     * @param jsonContent カウント対象のJSONコンテンツ
     * @return 翻訳対象の文字数（エラー時は0）
     */
    public int countCharacters(String jsonContent) {
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
