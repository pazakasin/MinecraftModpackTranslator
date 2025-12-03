package io.github.pazakasin.minecraft.modpack.translator.service.quest;

import java.util.regex.Pattern;

/**
 * SNBT文字列処理のユーティリティクラス。
 * エスケープ処理、変数参照判定などの補助機能を提供。
 */
public class SNBTStringHelper {
    /**
     * キーが翻訳対象かどうかを判定します。
     * Quest File本体で翻訳対象とするキー: title, description, subtitle
     * @param key キー名
     * @return 翻訳対象の場合true
     */
    public static boolean isTranslatableKey(String key) {
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
    public static boolean isVariableReference(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        
        String trimmed = value.trim();
        
        if (trimmed.isEmpty()) {
            return false;
        }
        
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return true;
        }
        
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
    public static String unescapeSnbtString(String text) {
        return text.replace("\\\"", "\"")
                   .replace("\\\\", "\\")
                   .replace("\\n", "\n")
                   .replace("\\r", "\r")
                   .replace("\\t", "\t");
    }
    
    /**
     * SNBT文字列値をエスケープします。
     * @param text エスケープ対象テキスト
     * @return エスケープ済みテキスト
     */
    public static String escapeSnbtString(String text) {
        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"");
    }
    
    /**
     * 配列内容からインデントを抽出します。
     * @param arrayContent 配列の内容
     * @return インデント文字列
     */
    public static String extractIndent(String arrayContent) {
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
     * マッチ範囲から配列の開始位置（'['の位置）を見つけます。
     * @param content コンテンツ
     * @param matchStart マッチ開始位置
     * @param matchEnd マッチ終了位置
     * @return '['の位置
     */
    public static int findArrayStart(String content, int matchStart, int matchEnd) {
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
    public static int findValueStart(String content, int matchStart, int matchEnd) {
        for (int i = matchStart; i < matchEnd; i++) {
            if (content.charAt(i) == '"') {
                return i;
            }
        }
        return matchStart;
    }
    
    /**
     * 配列の開始位置から対応する閉じ括弧を見つけます。
     * 括弧のネストを考慮します。
     * @param content コンテンツ
     * @param start 開始位置（'['の位置）
     * @return 対応する']'の位置、見つからない場合は-1
     */
    public static int findMatchingBracket(String content, int start) {
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
}
