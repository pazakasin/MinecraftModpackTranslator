package io.github.pazakasin.minecraft.modpack.translator.service.quest;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SNBTファイルに翻訳を適用するクラス。
 * 元のフォーマットを保持したまま翻訳テキストで置換。
 */
public class SNBTTranslationApplier {
    /**
     * 元のSNBTファイルのフォーマットを保持したまま翻訳を適用します。
     * @param sourceFile 元のSNBTファイル
     * @param targetFile 出力先ファイル
     * @param translations キーと翻訳のマップ（連番付きキー）
     * @throws IOException ファイルI/Oエラー
     */
    public void applyTranslations(File sourceFile, File targetFile, 
                                  Map<String, String> translations) throws IOException {
        String content = Files.readString(sourceFile.toPath(), StandardCharsets.UTF_8);
        
        List<TextMatch> matches = collectMatches(content);
        List<Replacement> replacements = buildReplacements(content, matches, translations);
        
        String result = applyReplacements(content, replacements);
        Files.writeString(targetFile.toPath(), result, StandardCharsets.UTF_8);
    }
    
    /**
     * コンテンツから翻訳対象のマッチを収集します。
     */
    private List<TextMatch> collectMatches(String content) {
        List<TextMatch> matches = new ArrayList<TextMatch>();
        
        // まず配列をマッチ
        List<TextMatch> arrayMatches = new ArrayList<TextMatch>();
        collectArrayMatches(content, arrayMatches);
        
        // 配列の範囲を記録
        Set<Range> arrayRanges = new HashSet<Range>();
        for (TextMatch match : arrayMatches) {
            arrayRanges.add(new Range(match.start, match.end));
        }
        
        // 配列外の文字列をマッチ
        collectStringMatches(content, matches, arrayRanges);
        
        // 配列を追加
        matches.addAll(arrayMatches);
        
        Collections.sort(matches, new Comparator<TextMatch>() {
            @Override
            public int compare(TextMatch a, TextMatch b) {
                return Integer.compare(a.start, b.start);
            }
        });
        
        return matches;
    }
    
    /**
     * 文字列値のマッチを収集します。
     * 配列内の要素は除外します。
     */
    private void collectStringMatches(String content, List<TextMatch> matches, Set<Range> arrayRanges) {
        Pattern stringPattern = Pattern.compile(
            "([a-zA-Z_][a-zA-Z_0-9]*):\\s*\"([^\"\\\\]*(\\\\.[^\"\\\\]*)*)\"");
        Matcher stringMatcher = stringPattern.matcher(content);
        
        while (stringMatcher.find()) {
            String key = stringMatcher.group(1);
            String stringValue = stringMatcher.group(2);
            
            if (!SNBTStringHelper.isTranslatableKey(key) || stringValue.length() == 0) {
                continue;
            }
            
            // 配列内の要素かチェック
            int matchStart = stringMatcher.start();
            int matchEnd = stringMatcher.end();
            boolean inArray = false;
            
            for (Range range : arrayRanges) {
                if (matchStart >= range.start && matchEnd <= range.end) {
                    inArray = true;
                    break;
                }
            }
            
            if (inArray) {
                continue;
            }
            
            String unescaped = SNBTStringHelper.unescapeSnbtString(stringValue);
            if (!SNBTStringHelper.isVariableReference(unescaped)) {
                matches.add(new TextMatch(key, stringValue, 
                    matchStart, matchEnd, false));
            }
        }
    }
    
    /**
     * 配列値のマッチを収集します。
     */
    private void collectArrayMatches(String content, List<TextMatch> matches) {
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
            
            if (hasTranslatableElement(arrayContent)) {
                matches.add(new TextMatch(key, "", 
                    arrayKeyMatcher.start(), arrayEnd + 1, true));
            }
        }
    }
    
    /**
     * 配列内に翻訳対象要素があるか判定します。
     */
    private boolean hasTranslatableElement(String arrayContent) {
        List<String> elements = extractArrayElements(arrayContent);
        
        for (String element : elements) {
            String unescaped = SNBTStringHelper.unescapeSnbtString(element);
            if (!SNBTStringHelper.isVariableReference(unescaped)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * マッチリストと翻訳マップから置換リストを構築します。
     */
    private List<Replacement> buildReplacements(String content, List<TextMatch> matches, 
                                                Map<String, String> translations) {
        List<Replacement> replacements = new ArrayList<Replacement>();
        Map<String, Integer> keyCounters = new HashMap<String, Integer>();
        
        for (TextMatch match : matches) {
            int counter = keyCounters.getOrDefault(match.key, 0);
            String uniqueKey = match.key + "_" + counter;
            keyCounters.put(match.key, counter + 1);
            
            if (!translations.containsKey(uniqueKey)) {
                continue;
            }
            
            String translatedValue = translations.get(uniqueKey);
            
            if (match.isArray) {
                replacements.add(buildArrayReplacement(content, match, translatedValue));
            } else {
                replacements.add(buildStringReplacement(content, match, translatedValue));
            }
        }
        
        return replacements;
    }
    
    /**
     * 文字列値の置換情報を構築します。
     */
    private Replacement buildStringReplacement(String content, TextMatch match, String translatedValue) {
        String replacementText = "\"" + SNBTStringHelper.escapeSnbtString(translatedValue) + "\"";
        int valueStart = SNBTStringHelper.findValueStart(content, match.start, match.end);
        return new Replacement(valueStart, match.end, replacementText);
    }
    
    /**
     * 配列値の置換情報を構築します。
     */
    private Replacement buildArrayReplacement(String content, TextMatch match, String translatedValue) {
        int arrayStart = SNBTStringHelper.findArrayStart(content, match.start, match.end);
        int arrayEnd = match.end - 1;
        
        String arrayContent = content.substring(arrayStart + 1, arrayEnd);
        String indent = SNBTStringHelper.extractIndent(arrayContent);
        
        List<String> originalElements = extractArrayElements(arrayContent);
        String[] translatedLines = translatedValue.split("\n");
        List<String> mergedElements = mergeElements(originalElements, translatedLines);
        
        String newArray = buildArrayString(mergedElements, indent);
        return new Replacement(arrayStart, match.end, newArray);
    }
    
    /**
     * 配列から元の要素を抽出します。
     * トップレベルの要素のみを抽出し、ネストされた引用符は無視します。
     */
    private List<String> extractArrayElements(String arrayContent) {
        List<String> elements = new ArrayList<String>();
        int i = 0;
        
        while (i < arrayContent.length()) {
            char c = arrayContent.charAt(i);
            
            // 空白をスキップ
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
     * 元の要素と翻訳をマージします。
     */
    private List<String> mergeElements(List<String> originalElements, String[] translatedLines) {
        List<String> mergedElements = new ArrayList<String>();
        int translatedIndex = 0;
        
        for (String originalElement : originalElements) {
            String unescaped = SNBTStringHelper.unescapeSnbtString(originalElement);
            
            if (unescaped.trim().isEmpty() || SNBTStringHelper.isVariableReference(unescaped)) {
                mergedElements.add(originalElement);
            } else {
                if (translatedIndex < translatedLines.length) {
                    mergedElements.add(SNBTStringHelper.escapeSnbtString(translatedLines[translatedIndex]));
                    translatedIndex++;
                } else {
                    mergedElements.add(originalElement);
                }
            }
        }
        
        return mergedElements;
    }
    
    /**
     * マージされた要素から配列文字列を構築します。
     */
    private String buildArrayString(List<String> elements, String indent) {
        StringBuilder newArray = new StringBuilder();
        newArray.append("[");
        
        for (int i = 0; i < elements.size(); i++) {
            newArray.append("\n").append(indent);
            newArray.append("\"").append(elements.get(i)).append("\"");
        }
        
        if (elements.size() > 0) {
            String baseIndent = indent.length() > 0 && indent.charAt(indent.length() - 1) == '\t' 
                ? indent.substring(0, indent.length() - 1) : indent;
            newArray.append("\n").append(baseIndent);
        }
        
        newArray.append("]");
        return newArray.toString();
    }
    
    /**
     * コンテンツに置換を適用します。
     */
    private String applyReplacements(String content, List<Replacement> replacements) {
        Collections.sort(replacements, new Comparator<Replacement>() {
            @Override
            public int compare(Replacement a, Replacement b) {
                return Integer.compare(b.start, a.start);
            }
        });
        
        StringBuilder result = new StringBuilder(content);
        for (Replacement r : replacements) {
            result.replace(r.start, r.end, r.replacement);
        }
        
        return result.toString();
    }
    
    /**
     * 範囲を表すヘルパークラス。
     */
    private static class Range {
        int start;
        int end;
        
        Range(int start, int end) {
            this.start = start;
            this.end = end;
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Range range = (Range) o;
            return start == range.start && end == range.end;
        }
        
        @Override
        public int hashCode() {
            return 31 * start + end;
        }
    }
}
