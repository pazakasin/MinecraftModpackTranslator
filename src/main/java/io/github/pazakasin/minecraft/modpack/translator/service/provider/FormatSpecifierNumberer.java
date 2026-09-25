package io.github.pazakasin.minecraft.modpack.translator.service.provider;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 番号なしの「%s」が複数ある原文を、翻訳前に番号付き（%1$s, %2$s …）へ書き換えるユーティリティ。
 * 翻訳で語順が入れ替わっても引数の対応を保つため。訳文で順序が変わっていなければ元の「%s」に戻す。
 */
public final class FormatSpecifierNumberer {
    /** 書式指定子1つ（%s・%1$s・%d・%.1f・%% 等）。group1=番号、group2=変換文字。 */
    private static final Pattern SPEC = Pattern.compile(
        "%(?:(\\d+)\\$)?[-#+0,(]*\\d*(?:\\.\\d+)?([a-zA-Z%])");

    /** インスタンス化を禁止する。 */
    private FormatSpecifierNumberer() {
    }

    /**
     * 書き換え対象かを判定する。番号なしの「%s」が2個以上あり、それ以外の指定子（%%を除く）を含まない場合のみ対象。
     * @param text 原文
     * @return 対象ならtrue
     */
    public static boolean needsNumbering(String text) {
        return text != null && countPlain(text) >= 2;
    }

    /**
     * 番号なしの「%s」を前から順に %1$s, %2$s … に書き換える。
     * @param text 原文（needsNumberingがtrueであること）
     * @return 書き換え後の文字列
     */
    public static String number(String text) {
        Matcher m = SPEC.matcher(text);
        StringBuilder sb = new StringBuilder();
        int index = 0;
        while (m.find()) {
            String replacement = m.group();
            if ("%s".equals(replacement)) {
                index++;
                replacement = "%" + index + "$s";
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * 訳文の番号付き指定子が 1, 2, …, count の順にちょうど並んでいれば、番号なしの「%s」に戻す。
     * 順序が入れ替わっている場合は番号付きのまま返す。
     * @param translated 訳文
     * @param count 原文の「%s」の個数
     * @return 復元後の訳文
     */
    public static String revertIfInOrder(String translated, int count) {
        List<Integer> indexes = numberedIndexes(translated);
        if (indexes.size() != count) {
            return translated;
        }
        for (int i = 0; i < count; i++) {
            if (indexes.get(i) != i + 1) {
                return translated;
            }
        }
        Matcher m = SPEC.matcher(translated);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String replacement = m.group(1) != null && "s".equals(m.group(2)) ? "%s" : m.group();
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * 検証用に、番号付きの「%n$s」を「%s」に置き換える。
     * @param text 対象文字列
     * @return 置換後の文字列
     */
    public static String normalize(String text) {
        return text.replaceAll("%\\d+\\$s", "%s");
    }

    /**
     * 訳文の番号付き指定子の番号がすべて 1〜count の範囲にあるかを判定する。
     * @param translated 訳文
     * @param count 原文の「%s」の個数
     * @return 範囲内ならtrue
     */
    public static boolean hasValidIndexes(String translated, int count) {
        for (Integer index : numberedIndexes(translated)) {
            if (index < 1 || index > count) {
                return false;
            }
        }
        return true;
    }

    /**
     * 番号なしの「%s」の個数を数える。他の指定子（%%を除く）が含まれる場合は0を返す。
     * @param text 対象文字列
     * @return 「%s」の個数（対象外なら0）
     */
    public static int countPlain(String text) {
        int count = 0;
        Matcher m = SPEC.matcher(text);
        while (m.find()) {
            if ("%s".equals(m.group())) {
                count++;
            } else if (!"%%".equals(m.group())) {
                return 0;
            }
        }
        return count;
    }

    /**
     * 番号付きの「%n$s」の番号を出現順に取得する。
     * @param text 対象文字列
     * @return 番号のリスト
     */
    private static List<Integer> numberedIndexes(String text) {
        List<Integer> indexes = new ArrayList<Integer>();
        Matcher m = SPEC.matcher(text);
        while (m.find()) {
            if (m.group(1) != null && "s".equals(m.group(2))) {
                indexes.add(Integer.valueOf(m.group(1)));
            }
        }
        return indexes;
    }
}
