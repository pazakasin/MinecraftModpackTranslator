package io.github.pazakasin.minecraft.modpack.translator.service.quest;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SNBTファイルから翻訳対象テキストを抽出するクラス。
 * 正規表現ベースで文字列値と配列値を抽出。
 */
public class SNBTTextExtractor {
    /**
     * Quest File本体から翻訳対象テキストを抽出します。
     * 同じキーが複数回出現する場合は連番を付けてユニークにします。
     * @param questFile Quest Fileファイル
     * @return キーと値のマップ（連番付きキー）
     * @throws IOException ファイル読み込みエラー
     */
    public Map<String, String> extractTranslatableTexts(File questFile) throws IOException {
        String content = Files.readString(questFile.toPath(), StandardCharsets.UTF_8);
        List<TextMatch> matches = new ArrayList<TextMatch>();
        
        extractStringValues(content, matches);
        extractArrayValues(content, matches);
        
        Collections.sort(matches, new Comparator<TextMatch>() {
            @Override
            public int compare(TextMatch a, TextMatch b) {
                return Integer.compare(a.start, b.start);
            }
        });
        
        return buildUniqueKeyMap(matches);
    }
    
    /**
     * 文字列値を抽出します。
     */
    private void extractStringValues(String content, List<TextMatch> matches) {
        Pattern stringPattern = Pattern.compile(
            "([a-zA-Z_][a-zA-Z_0-9]*):\\s*\"([^\"\\\\]*(\\\\.[^\"\\\\]*)*)\"");
        Matcher stringMatcher = stringPattern.matcher(content);
        
        while (stringMatcher.find()) {
            String key = stringMatcher.group(1);
            String value = stringMatcher.group(2);
            
            if (SNBTStringHelper.isTranslatableKey(key) && value.length() > 0) {
                String unescaped = SNBTStringHelper.unescapeSnbtString(value);
                if (!SNBTStringHelper.isVariableReference(unescaped)) {
                    matches.add(new TextMatch(key, unescaped, 
                        stringMatcher.start(), stringMatcher.end(), false));
                }
            }
        }
    }
    
    /**
     * 配列値を抽出します。
     */
    private void extractArrayValues(String content, List<TextMatch> matches) {
        Pattern arrayKeyPattern = Pattern.compile("([a-zA-Z_][a-zA-Z_0-9]*):\\s*\\[");
        Matcher arrayKeyMatcher = arrayKeyPattern.matcher(content);
        
        while (arrayKeyMatcher.find()) {
            String key = arrayKeyMatcher.group(1);
            
            if (!SNBTStringHelper.isTranslatableKey(key)) {
                continue;
            }
            
            int arrayStart = arrayKeyMatcher.end() - 1;
            int arrayEnd = SNBTStringHelper.findMatchingBracket(content, arrayStart);
            
            if (arrayEnd == -1) {
                continue;
            }
            
            String arrayContent = content.substring(arrayStart + 1, arrayEnd);
            String trimmed = arrayContent.trim();
            
            if (trimmed.isEmpty() || trimmed.startsWith("{")) {
                continue;
            }
            
            String combinedValue = extractArrayElements(arrayContent);
            
            if (combinedValue != null && combinedValue.length() > 0) {
                matches.add(new TextMatch(key, combinedValue, 
                    arrayKeyMatcher.start(), arrayEnd + 1, true));
            }
        }
    }
    
    /**
     * 配列内の要素を抽出して結合します。
     */
    private String extractArrayElements(String arrayContent) {
        StringBuilder combined = new StringBuilder();
        Pattern elementPattern = Pattern.compile("\"([^\"\\\\]*(\\\\.[^\"\\\\]*)*)\"");
        Matcher elementMatcher = elementPattern.matcher(arrayContent);
        
        while (elementMatcher.find()) {
            String element = SNBTStringHelper.unescapeSnbtString(elementMatcher.group(1));
            
            if (!element.trim().isEmpty() && !SNBTStringHelper.isVariableReference(element)) {
                if (combined.length() > 0) {
                    combined.append("\n");
                }
                combined.append(element);
            }
        }
        
        return combined.toString();
    }
    
    /**
     * マッチリストから連番付きユニークキーのマップを構築します。
     */
    private Map<String, String> buildUniqueKeyMap(List<TextMatch> matches) {
        Map<String, String> texts = new LinkedHashMap<String, String>();
        Map<String, Integer> keyCounters = new HashMap<String, Integer>();
        
        for (TextMatch match : matches) {
            int counter = keyCounters.getOrDefault(match.key, 0);
            String uniqueKey = match.key + "_" + counter;
            texts.put(uniqueKey, match.value);
            keyCounters.put(match.key, counter + 1);
        }
        
        return texts;
    }
}
