package io.github.pazakasin.minecraft.modpack.translator.service.quest;

import net.querz.nbt.io.SNBTUtil;
import net.querz.nbt.tag.Tag;
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
 * SNBTファイルのパース・書き込みを行うクラス。
 * Lang FileにはNBTパース、Quest File本体には正規表現ベースの処理を提供。
 */
public class SNBTParser {
    /**
     * マッチした翻訳対象テキストの位置情報を保持するクラス。
     */
    private static class TextMatch {
        String key;
        String value;
        int start;
        int end;
        boolean isArray;
        
        TextMatch(String key, String value, int start, int end, boolean isArray) {
            this.key = key;
            this.value = value;
            this.start = start;
            this.end = end;
            this.isArray = isArray;
        }
    }
    
    /**
     * 置換情報を保持するクラス。
     */
    private static class Replacement {
        int start;
        int end;
        String replacement;
        
        Replacement(int start, int end, String replacement) {
            this.start = start;
            this.end = end;
            this.replacement = replacement;
        }
    }
    
    /**
     * Lang File用: SNBTファイルをパースしてTagオブジェクトに変換します。
     * @param snbtFile パース対象のSNBTファイル
     * @return パース結果のTagオブジェクト
     * @throws IOException ファイル読み込みまたはパースエラー
     */
    public Tag<?> parse(File snbtFile) throws IOException {
        String content = null;
        try {
            content = Files.readString(snbtFile.toPath(), StandardCharsets.UTF_8);
            content = preprocessFTBQuestsFormat(content);
            return SNBTUtil.fromSNBT(content);
        } catch (IOException e) {
            String preview = "";
            if (content != null && content.length() > 0) {
                int previewLen = Math.min(200, content.length());
                preview = content.substring(0, previewLen).replace("\n", " ").replace("\r", " ");
                if (content.length() > 200) {
                    preview += "...";
                }
            }
            String errorMsg = String.format(
                "Failed to parse SNBT file: %s%nCause: %s%nFile preview: %s",
                snbtFile.getName(),
                e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName(),
                preview.isEmpty() ? "(empty or unreadable)" : preview
            );
            throw new IOException(errorMsg, e);
        }
    }
    
    /**
     * Quest File本体用: 正規表現で翻訳対象テキストを抽出します。
     * 前提: 要素内に改行なし、すべての値を文字列として扱う。
     * 同じキーが複数回出現する場合は連番を付けてユニークにします。
     * @param questFile Quest Fileファイル
     * @return キーと値のマップ（連番付きキー）
     * @throws IOException ファイル読み込みエラー
     */
    public Map<String, String> extractTranslatableTexts(File questFile) throws IOException {
        String content = Files.readString(questFile.toPath(), StandardCharsets.UTF_8);
        List<TextMatch> matches = new ArrayList<TextMatch>();
        
        // 文字列値のみをマッチング
        Pattern stringPattern = Pattern.compile(
            "([a-zA-Z_][a-zA-Z_0-9]*):\\s*\"([^\"\\\\]*(\\\\.[^\"\\\\]*)*)\"");
        Matcher stringMatcher = stringPattern.matcher(content);
        
        while (stringMatcher.find()) {
            String key = stringMatcher.group(1);
            String value = stringMatcher.group(2);
            
            if (isTranslatableKey(key) && value.length() > 0) {
                String unescaped = unescapeSnbtString(value);
                // 変数参照は除外
                if (!isVariableReference(unescaped)) {
                    matches.add(new TextMatch(key, unescaped, 
                        stringMatcher.start(), stringMatcher.end(), false));
                }
            }
        }
        
        // 配列値を手動でパース（括弧のバランスを考慮）
        Pattern arrayKeyPattern = Pattern.compile("([a-zA-Z_][a-zA-Z_0-9]*):\\s*\\[");
        Matcher arrayKeyMatcher = arrayKeyPattern.matcher(content);
        
        while (arrayKeyMatcher.find()) {
            String key = arrayKeyMatcher.group(1);
            
            if (!isTranslatableKey(key)) {
                continue;
            }
            
            int arrayStart = arrayKeyMatcher.end() - 1; // '[' の位置
            int arrayEnd = findMatchingBracket(content, arrayStart);
            
            if (arrayEnd == -1) {
                continue; // 対応する']'が見つからない
            }
            
            String arrayContent = content.substring(arrayStart + 1, arrayEnd);
            
            // 配列の中身がオブジェクトか文字列かを判定
            String trimmed = arrayContent.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("{")) {
                // 空配列またはオブジェクトの配列はスキップ
                continue;
            }
            
            // 文字列配列の場合、各要素を個別に判定
            StringBuilder combined = new StringBuilder();
            Pattern elementPattern = Pattern.compile("\"([^\"\\\\]*(\\\\.[^\"\\\\]*)*)\"");
            Matcher elementMatcher = elementPattern.matcher(arrayContent);
            
            while (elementMatcher.find()) {
                String element = unescapeSnbtString(elementMatcher.group(1));
                
                // 空文字列でなく、変数参照でもない要素のみ追加
                if (!element.trim().isEmpty() && !isVariableReference(element)) {
                    if (combined.length() > 0) {
                        combined.append("\n");
                    }
                    combined.append(element);
                }
            }
            
            if (combined.length() > 0) {
                String combinedValue = combined.toString();
                matches.add(new TextMatch(key, combinedValue, 
                    arrayKeyMatcher.start(), arrayEnd + 1, true));
            }
        }
        
        // 出現順にソート
        Collections.sort(matches, new Comparator<TextMatch>() {
            @Override
            public int compare(TextMatch a, TextMatch b) {
                return Integer.compare(a.start, b.start);
            }
        });
        
        // 連番付きキーでMapを作成（出現順を保持）
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
    
    /**
     * 配列の開始位置から対応する閉じ括弧を見つけます。
     * 括弧のネストを考慮します。
     * @param content コンテンツ
     * @param start 開始位置（'['の位置）
     * @return 対応する']'の位置、見つからない場合は-1
     */
    private int findMatchingBracket(String content, int start) {
        int depth = 1;
        boolean inString = false;
        boolean escaped = false;
        
        for (int i = start + 1; i < content.length(); i++) {
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
                inString = !inString;
                continue;
            }
            
            if (inString) {
                continue;
            }
            
            if (c == '[') {
                depth++;
            } else if (c == ']') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        
        return -1;
    }
    
    /**
     * Quest File本体用: 元のSNBTファイルのフォーマットを保持したまま翻訳を適用します。
     * 連番付きキーを元のキー名に戻し、出現順に置換します。
     * extractTranslatableTextsと同じ順序でマッチングを処理することで、正しい対応を保証します。
     * @param sourceFile 元のSNBTファイル
     * @param targetFile 出力先ファイル
     * @param translations キーと翻訳のマップ（連番付きキー）
     * @throws IOException ファイルI/Oエラー
     */
    public void applyTranslations(File sourceFile, File targetFile, 
                                  Map<String, String> translations) throws IOException {
        String content = Files.readString(sourceFile.toPath(), StandardCharsets.UTF_8);
        
        // extractTranslatableTextsと同じ順序でマッチを収集
        List<TextMatch> matches = new ArrayList<TextMatch>();
        
        // 文字列値をマッチング
        Pattern stringPattern = Pattern.compile(
            "([a-zA-Z_][a-zA-Z_0-9]*):\\s*\"([^\"\\\\]*(\\\\.[^\"\\\\]*)*)\"");
        Matcher stringMatcher = stringPattern.matcher(content);
        
        while (stringMatcher.find()) {
            String key = stringMatcher.group(1);
            String stringValue = stringMatcher.group(2);
            
            if (isTranslatableKey(key) && stringValue.length() > 0) {
                String unescaped = unescapeSnbtString(stringValue);
                // 変数参照は除外
                if (!isVariableReference(unescaped)) {
                    matches.add(new TextMatch(key, stringValue, 
                        stringMatcher.start(), stringMatcher.end(), false));
                }
            }
        }
        
        // 配列値をマッチング
        Pattern arrayKeyPattern = Pattern.compile("([a-zA-Z_][a-zA-Z_0-9]*):\\s*\\[");
        Matcher arrayKeyMatcher = arrayKeyPattern.matcher(content);
        
        while (arrayKeyMatcher.find()) {
            String key = arrayKeyMatcher.group(1);
            
            if (!isTranslatableKey(key)) {
                continue;
            }
            
            int arrayStart = arrayKeyMatcher.end() - 1;
            int arrayEnd = findMatchingBracket(content, arrayStart);
            
            if (arrayEnd == -1) {
                continue;
            }
            
            String arrayContent = content.substring(arrayStart + 1, arrayEnd);
            String trimmed = arrayContent.trim();
            
            if (trimmed.isEmpty() || trimmed.startsWith("{")) {
                continue;
            }
            
            // 配列内に翻訳対象要素があるか確認
            Pattern elementPattern = Pattern.compile("\"([^\"\\\\]*(\\\\.[^\"\\\\]*)*)\"");
            Matcher elementMatcher = elementPattern.matcher(arrayContent);
            boolean hasTranslatableElement = false;
            
            while (elementMatcher.find()) {
                String element = unescapeSnbtString(elementMatcher.group(1));
                if (!isVariableReference(element)) {
                    hasTranslatableElement = true;
                    break;
                }
            }
            
            if (hasTranslatableElement) {
                matches.add(new TextMatch(key, "", 
                    arrayKeyMatcher.start(), arrayEnd + 1, true));
            }
        }
        
        // extractTranslatableTextsと同じく、出現順にソート
        Collections.sort(matches, new Comparator<TextMatch>() {
            @Override
            public int compare(TextMatch a, TextMatch b) {
                return Integer.compare(a.start, b.start);
            }
        });
        
        // ソート順で置換情報を作成
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
                // 配列値の置換: 元の配列要素を読み取り、変数参照以外を翻訳結果で置換
                int arrayStart = findArrayStart(content, match.start, match.end);
                int arrayEnd = match.end - 1; // ']'の位置
                
                String arrayContent = content.substring(arrayStart + 1, arrayEnd);
                String indent = extractIndent(arrayContent);
                
                // 元の配列要素を取得
                List<String> originalElements = new ArrayList<String>();
                Pattern elementPattern = Pattern.compile("\"([^\"\\\\]*(\\\\.[^\"\\\\]*)*)\"");
                Matcher elementMatcher = elementPattern.matcher(arrayContent);
                
                while (elementMatcher.find()) {
                    originalElements.add(elementMatcher.group(1));
                }
                
                // 翻訳結果を改行で分割
                String[] translatedLines = translatedValue.split("\n");
                
                // 元の要素と翻訳結果をマージ
                List<String> mergedElements = new ArrayList<String>();
                int translatedIndex = 0;
                
                for (String originalElement : originalElements) {
                    String unescaped = unescapeSnbtString(originalElement);
                    
                    // 空文字列または変数参照はそのまま保持
                    if (unescaped.trim().isEmpty() || isVariableReference(unescaped)) {
                        mergedElements.add(originalElement);
                    } else {
                        // 翻訳対象は翻訳結果で置換
                        if (translatedIndex < translatedLines.length) {
                            mergedElements.add(escapeSnbtString(translatedLines[translatedIndex]));
                            translatedIndex++;
                        } else {
                            // 翻訳結果が不足する場合は元の値を保持
                            mergedElements.add(originalElement);
                        }
                    }
                }
                
                // 新しい配列を構築
                StringBuilder newArray = new StringBuilder();
                newArray.append("[");
                for (int i = 0; i < mergedElements.size(); i++) {
                    newArray.append("\n").append(indent);
                    newArray.append("\"").append(mergedElements.get(i)).append("\"");
                }
                if (mergedElements.size() > 0) {
                    String baseIndent = indent.length() > 0 && indent.charAt(indent.length() - 1) == '\t' 
                        ? indent.substring(0, indent.length() - 1) : indent;
                    newArray.append("\n").append(baseIndent);
                }
                newArray.append("]");
                
                replacements.add(new Replacement(arrayStart, match.end, newArray.toString()));
            } else {
                // 文字列値の置換
                String replacementText = "\"" + escapeSnbtString(translatedValue) + "\"";
                int valueStart = findValueStart(content, match.start, match.end);
                replacements.add(new Replacement(valueStart, match.end, replacementText));
            }
        }
        
        // 後ろから前に向かって置換（位置がずれない）
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
        
        Files.writeString(targetFile.toPath(), result.toString(), StandardCharsets.UTF_8);
    }
    
    /**
     * マッチ範囲から配列の開始位置（'['の位置）を見つけます。
     * @param content コンテンツ
     * @param matchStart マッチ開始位置
     * @param matchEnd マッチ終了位置
     * @return '['の位置
     */
    private int findArrayStart(String content, int matchStart, int matchEnd) {
        for (int i = matchStart; i < matchEnd; i++) {
            if (content.charAt(i) == '[') {
                return i;
            }
        }
        return matchStart;
    }
    
    /**
     * マッチ範囲から値の開始位置（最初の'"'の位置）を見つけます。
     * @param content コンテンツ
     * @param matchStart マッチ開始位置
     * @param matchEnd マッチ終了位置
     * @return 最初の'"'の位置
     */
    private int findValueStart(String content, int matchStart, int matchEnd) {
        for (int i = matchStart; i < matchEnd; i++) {
            if (content.charAt(i) == '"') {
                return i;
            }
        }
        return matchStart;
    }
    
    /**
     * キーが翻訳対象かどうかを判定します。
     * Quest File本体で翻訳対象とするキー: title, description, subtitle
     * @param key キー名
     * @return 翻訳対象の場合true
     */
    private static boolean isTranslatableKey(String key) {
        return key.equals("title") || 
               key.equals("description") || 
               key.equals("subtitle");
    }
    
    /**
     * 値が変数参照かどうかを判定します。
     * 以下のパターンに該当する場合は変数参照と見なして翻訳対象から除外:
     * 1. ドット区切り参照(3セグメント以上): ftb.shop.notification.guidance
     * 2. 波括弧で囲まれた参照: {.advanced_tech.quests30.title}
     * @param value 判定対象の値
     * @return 変数参照の場合true
     */
    private static boolean isVariableReference(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        
        String trimmed = value.trim();
        
        // 空文字列は変数参照ではない
        if (trimmed.isEmpty()) {
            return false;
        }
        
        // 波括弧で囲まれた参照
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return true;
        }
        
        // ドット区切り参照(3セグメント以上): 小文字始まり、英数字とアンダースコアとドットのみ
        // 例: ftb.shop.notification.guidance (4セグメント)
        Pattern dotReference = Pattern.compile("^[a-z][a-z0-9_]*(\\.[a-z0-9_]+){2,}$");
        if (dotReference.matcher(trimmed).matches()) {
            return true;
        }
        
        return false;
    }
    
    /**
     * SNBT文字列値をアンエスケープします。
     * @param text エスケープされたテキスト
     * @return アンエスケープされたテキスト
     */
    private String unescapeSnbtString(String text) {
        return text.replace("\\\"", "\"")
                   .replace("\\\\", "\\")
                   .replace("\\n", "\n")
                   .replace("\\r", "\r")
                   .replace("\\t", "\t");
    }
    
    /**
     * 正規表現で使用する特殊文字をエスケープします。
     * @param text エスケープ対象テキスト
     * @return エスケープ済みテキスト
     */
    private String escapeRegex(String text) {
        return text.replaceAll("([\\\\\\[\\]{}()*+?.^$|])", "\\\\$1");
    }
    
    /**
     * SNBT文字列値をエスケープします。
     * @param text エスケープ対象テキスト
     * @return エスケープ済みテキスト
     */
    private String escapeSnbtString(String text) {
        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"");
    }
    
    /**
     * 配列内容からインデントを抽出します。
     * @param arrayContent 配列の内容
     * @return インデント文字列
     */
    private String extractIndent(String arrayContent) {
        int firstNewline = arrayContent.indexOf('\n');
        if (firstNewline == -1) {
            return "\t";
        }
        
        int start = firstNewline + 1;
        int end = start;
        while (end < arrayContent.length() && 
               (arrayContent.charAt(end) == ' ' || arrayContent.charAt(end) == '\t')) {
            end++;
        }
        
        return end > start ? arrayContent.substring(start, end) : "\t";
    }
    
    /**
     * FTB Quests形式のSNBTを標準形式に変換します（Lang File用）。
     * FTB Questsはカンマ区切りを省略しているため、パース前に追加。
     * @param content 元のSNBT文字列
     * @return 標準形式に変換されたSNBT文字列
     */
    private String preprocessFTBQuestsFormat(String content) {
        for (int i = 0; i < 5; i++) {
            String before = content;
            content = applySinglePass(content);
            if (content.equals(before)) {
                break;
            }
        }
        return content;
    }
    
    /**
     * カンマ追加処理を1回実行します（Lang File用）。
     * @param content 処理対象の文字列
     * @return 処理後の文字列
     */
    private String applySinglePass(String content) {
        content = content.replaceAll("\"(\\s*[\\r\\n]+\\s*)([a-zA-Z_0-9\"\\}\\]])", "\",$1$2");
        content = content.replaceAll("\\](\\s*[\\r\\n]+\\s*)([a-zA-Z_0-9\\{\"\\}\\]])", "],$1$2");
        content = content.replaceAll("\\}(\\s*[\\r\\n]+\\s*)([a-zA-Z_0-9\\{\"\\}\\]])", "},$1$2");
        content = content.replaceAll("(true|false)(\\s*[\\r\\n]+\\s*)([a-zA-Z_0-9\\}\\]])", "$1,$2$3");
        content = content.replaceAll("([-+]?[0-9]+\\.?[0-9]*([eE][-+]?[0-9]+)?[dDfFlLbBsS]?)(\\s*[\\r\\n]+\\s*)([a-zA-Z_0-9\"\\}\\]])", "$1,$3$4");
        return content;
    }

    /**
     * TagオブジェクトをSNBT文字列に変換します（Lang File用）。
     * @param tag 変換対象のTagオブジェクト
     * @return SNBT形式の文字列
     * @throws IOException 変換エラー
     */
    public String toSNBT(Tag<?> tag) throws IOException {
        try {
            return SNBTUtil.toSNBT(tag);
        } catch (IOException e) {
            throw new IOException("Failed to convert Tag to SNBT: " + e.getMessage(), e);
        }
    }

    /**
     * TagオブジェクトをSNBTファイルとして書き込みます（Lang File用）。
     * @param snbtFile 出力先ファイル
     * @param tag 書き込むTagオブジェクト
     * @throws IOException ファイル書き込みエラー
     */
    public void write(File snbtFile, Tag<?> tag) throws IOException {
        try {
            String snbt = toSNBT(tag);
            Files.writeString(snbtFile.toPath(), snbt, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IOException("Failed to write SNBT file: " + snbtFile.getName() + " - " + e.getMessage(), e);
        }
    }
}
