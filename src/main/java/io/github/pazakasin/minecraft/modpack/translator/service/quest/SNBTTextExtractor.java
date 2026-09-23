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
        
        // デバッグ出力
        System.out.println("=== DEBUG: Extracted Matches ===");
        for (int i = 0; i < matches.size(); i++) {
            TextMatch m = matches.get(i);
            String preview = m.value.length() > 100 ? m.value.substring(0, 100) + "..." : m.value;
            System.out.println(String.format("[%d] key=%s, isArray=%s, start=%d, end=%d", 
                i, m.key, m.isArray, m.start, m.end));
            System.out.println("    value: " + preview.replace("\n", "\\n"));
        }
        System.out.println("================================");
        
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
     * トップレベルの要素のみを抽出し、ネストされた引用符は無視します。
     */
    private String extractArrayElements(String arrayContent) {
        StringBuilder combined = new StringBuilder();
        List<String> elements = parseArrayElements(arrayContent);
        
        System.out.println("  DEBUG: Array has " + elements.size() + " elements");
        for (int i = 0; i < elements.size(); i++) {
            String preview = elements.get(i).length() > 80 ? elements.get(i).substring(0, 80) + "..." : elements.get(i);
            System.out.println("    [" + i + "]: " + preview.replace("\n", "\\n"));
        }
        
        for (String element : elements) {
            String unescaped = SNBTStringHelper.unescapeSnbtString(element);
            
            if (!unescaped.trim().isEmpty() && !SNBTStringHelper.isVariableReference(unescaped)) {
                if (combined.length() > 0) {
                    combined.append("\n");
                }
                combined.append(unescaped);
            }
        }
        
        return combined.toString();
    }
    
    /**
     * 配列内の要素をパースします。
     * トップレベルの文字列のみを抽出し、ネストされた引用符は無視します。
     * @param arrayContent 配列の内容
     * @return 要素のリスト
     */
    private List<String> parseArrayElements(String arrayContent) {
        List<String> elements = new ArrayList<String>();
        int i = 0;
        
        while (i < arrayContent.length()) {
            char c = arrayContent.charAt(i);
            
            // 空白とカンマをスキップ
            if (Character.isWhitespace(c) || c == ',') {
                i++;
                continue;
            }
            
            // 文字列要素の開始
            if (c == '"') {
                int start = i + 1;
                int end = findStringEnd(arrayContent, start);
                
                if (end != -1) {
                    elements.add(arrayContent.substring(start, end));
                    i = end + 1;
                } else {
                    i++;
                }
            } else {
                i++;
            }
        }
        
        return elements;
    }
    
    /**
     * 文字列の終端位置を見つけます。
     * エスケープされた引用符を考慮します。
     * @param content コンテンツ
     * @param start 検索開始位置（最初の'"'の次の位置）
     * @return 文字列を閉じる'"'の位置、見つからない場合は-1
     */
    private int findStringEnd(String content, int start) {
        boolean escaped = false;
        
        for (int i = start; i < content.length(); i++) {
            char c = content.charAt(i);
            
            if (escaped) {
                escaped = false;
                continue;
            }
            
            if (c == '\\') {
                escaped = true;
                continue;
            }
            
            if (c == '"') {
                return i;
            }
        }
        
        return -1;
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
        
        System.out.println("=== DEBUG: Final Key Map ===");
        for (Map.Entry<String, String> entry : texts.entrySet()) {
            String preview = entry.getValue().length() > 100 ? entry.getValue().substring(0, 100) + "..." : entry.getValue();
            System.out.println(entry.getKey() + ": " + preview.replace("\n", "\\n"));
        }
        System.out.println("============================");
        
        return texts;
    }
}
