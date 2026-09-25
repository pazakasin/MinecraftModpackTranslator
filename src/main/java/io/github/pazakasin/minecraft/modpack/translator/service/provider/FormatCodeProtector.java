package io.github.pazakasin.minecraft.modpack.translator.service.provider;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 翻訳APIに渡すテキストの書式コード（§コード・書式指定子）と改行を保護・復元するユーティリティ。
 * §コードは効果範囲の文字を &lt;span data-mc&gt; で包み、書式指定子は &lt;span translate="no"&gt; で囲む。
 */
public final class FormatCodeProtector {
    /** §コード1つの正規表現。 */
    private static final String CODE = "§[0-9a-fk-orA-FK-OR]";

    /** 書式指定子1つ（%s・%1$s・%.1f・%%等）の正規表現。 */
    private static final String SPEC = "%(?:\\d+\\$)?[-#+0,(]*\\d*(?:\\.\\d+)?[sdf%]";

    /** 連続する§コード（例：§4§l）。 */
    private static final Pattern CODE_RUN = Pattern.compile("(?:" + CODE + ")+");

    /** 連続する書式指定子。 */
    private static final Pattern SPEC_RUN = Pattern.compile("(?:" + SPEC + ")+");

    /** 保護対象トークン1つ（検証・判定用）。 */
    private static final Pattern TOKEN_SINGLE = Pattern.compile(CODE + "|" + SPEC);

    /** 翻訳結果内の翻訳除外span。内容は書式コードのみ。 */
    private static final Pattern NO_TRANSLATE_SPAN = Pattern.compile(
        "<span\\b[^>]*translate\\s*=\\s*[\"']no[\"'][^>]*>(.*?)</span\\s*>",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /** 翻訳結果内の色範囲spanの開始タグ。属性値が§コード。 */
    private static final Pattern MC_OPEN = Pattern.compile(
        "<span\\b[^>]*data-mc\\s*=\\s*[\"']([^\"']*)[\"'][^>]*>", Pattern.CASE_INSENSITIVE);

    /** spanの終了タグ。 */
    private static final Pattern SPAN_END = Pattern.compile("</span\\s*>", Pattern.CASE_INSENSITIVE);

    /** 改行タグ。APIが前後に付けた空白も含めて検出する。 */
    private static final Pattern BR = Pattern.compile("[ \\t]*<br\\s*/?>[ \\t]*", Pattern.CASE_INSENSITIVE);

    /** 数値文字参照（&#39; / &#x27; 等）。 */
    private static final Pattern NUMERIC_ENTITY = Pattern.compile("&#(x[0-9a-fA-F]+|\\d+);");

    /** 日本語文字（かな・漢字・全角記号）の文字クラス。 */
    private static final String JP = "[\\u3000-\\u30FF\\u3400-\\u9FFF\\uFF00-\\uFFEF]";

    /** 日本語文字（後ろに§コードが続いてもよい）の直後の半角空白。 */
    private static final Pattern SPACE_AFTER_JP = Pattern.compile("(" + JP + "(?:" + CODE + ")*) +");

    /** 日本語文字（間に§コードや空白があってもよい）の直前の半角空白。 */
    private static final Pattern SPACE_BEFORE_JP = Pattern.compile(" +(?=(?:" + CODE + "| )*" + JP + ")");

    /** 不正な§（直後が書式コード文字でないもの）。 */
    private static final Pattern BROKEN_CODE = Pattern.compile("§(?![0-9a-fk-orA-FK-OR])");

    /** 英字（翻訳が必要かの判定用）。 */
    private static final Pattern LETTER = Pattern.compile("[A-Za-z]");

    /** 文字参照と改行タグ（英字判定から除外する用）。 */
    private static final Pattern MARKUP = Pattern.compile("&#?\\w+;|<br>");

    /** 翻訳除外spanの開始タグ。 */
    private static final String NO_OPEN = "<span translate=\"no\">";

    /** spanの終了タグ文字列。 */
    private static final String CLOSE = "</span>";

    /** インスタンス化を禁止する。 */
    private FormatCodeProtector() {
    }

    /**
     * 翻訳が必要なテキストかを判定する（書式コードを除いて英字を含むか）。
     * @param text 原文
     * @return 翻訳が必要ならtrue
     */
    public static boolean needsTranslation(String text) {
        return text != null && hasLetter(text);
    }

    /**
     * 原文をHTMLモード翻訳用に変換する（エスケープ、改行→br、書式コードの保護）。
     * 前後の空白は除去する（restoreで原文から復元）。
     * @param text 原文
     * @return 保護済みテキスト
     */
    public static String protect(String text) {
        String source = FormatSpecifierNumberer.needsNumbering(text)
            ? FormatSpecifierNumberer.number(text) : text;
        String escaped = source.strip()
            .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\r\n", "\n").replace("\n", "<br>");
        StringBuilder sb = new StringBuilder();
        Matcher m = CODE_RUN.matcher(escaped);
        String run = null;
        int pos = 0;
        while (m.find()) {
            appendSegment(sb, run, escaped.substring(pos, m.start()));
            run = m.group();
            pos = m.end();
        }
        appendSegment(sb, run, escaped.substring(pos));
        return sb.toString();
    }

    /**
     * §コードとその効果範囲の文字を保護形式で追記する。
     * 英字を含む範囲はdata-mcで包み、含まない範囲はコードのみ翻訳除外にする。
     * @param sb 追記先
     * @param run 範囲の先頭の§コード（先頭の無修飾部分はnull）
     * @param segment 範囲の文字列
     */
    private static void appendSegment(StringBuilder sb, String run, String segment) {
        String body = SPEC_RUN.matcher(segment).replaceAll(
            Matcher.quoteReplacement(NO_OPEN) + "$0" + Matcher.quoteReplacement(CLOSE));
        if (run == null) {
            sb.append(body);
        } else if (hasLetter(MARKUP.matcher(segment).replaceAll(""))) {
            sb.append("<span data-mc=\"").append(run).append("\">").append(body).append(CLOSE);
        } else {
            sb.append(NO_OPEN).append(run).append(CLOSE).append(body);
        }
    }

    /**
     * 翻訳結果を元の形式に戻す（span除去、br→改行、文字参照と空白の整理、前後空白の復元）。
     * @param translated APIの翻訳結果
     * @param original 原文（前後の空白の復元に使用）
     * @return 復元済みテキスト
     */
    public static String restore(String translated, String original) {
        Matcher m = NO_TRANSLATE_SPAN.matcher(translated);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(m.group(1).replaceAll("\\s+", "")));
        }
        m.appendTail(sb);
        m = MC_OPEN.matcher(sb.toString());
        sb = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(m.group(1).replaceAll("\\s+", "")));
        }
        m.appendTail(sb);
        String text = SPAN_END.matcher(sb.toString()).replaceAll("");
        text = unescapeHtml(BR.matcher(text).replaceAll("\n"));
        text = SPACE_AFTER_JP.matcher(text).replaceAll("$1");
        text = SPACE_BEFORE_JP.matcher(text).replaceAll("");
        text = complementLeadingCode(text.strip(), original.strip());
        if (FormatSpecifierNumberer.needsNumbering(original)) {
            text = FormatSpecifierNumberer.revertIfInOrder(text, FormatSpecifierNumberer.countPlain(original));
        }
        return leadingSpace(original) + text + trailingSpace(original);
    }

    /**
     * 原文がコードで始まり訳文がコードで始まらない場合、原文の先頭コードを訳文の先頭に補う。
     * 語順の入れ替えで先頭コードが行の途中へ移り、行頭が既定色になるのを防ぐ。
     * @param text 復元済みの訳文（前後空白なし）
     * @param original 原文（前後空白なし）
     * @return 補完後の訳文
     */
    private static String complementLeadingCode(String text, String original) {
        Matcher m = CODE_RUN.matcher(original);
        if (!m.lookingAt() || CODE_RUN.matcher(text).lookingAt()) {
            return text;
        }
        return m.group() + text;
    }

    /**
     * 原文と訳文で書式コードの種類が一致し、不正な§が増えていないかを検証する。
     * 個数は先頭コードの補完やAPIによる重複で変わりうるため比較しない。
     * 番号付けの対象だった原文では、訳文の「%n$s」を「%s」とみなし、番号の範囲も確認する。
     * @param original 原文
     * @param translated 訳文
     * @return 一致していればtrue
     */
    public static boolean isConsistent(String original, String translated) {
        if (original == null || translated == null) {
            return original == translated;
        }
        String target = translated;
        if (FormatSpecifierNumberer.needsNumbering(original)) {
            if (!FormatSpecifierNumberer.hasValidIndexes(translated,
                    FormatSpecifierNumberer.countPlain(original))) {
                return false;
            }
            target = FormatSpecifierNumberer.normalize(translated);
        }
        return countTokens(original).keySet().equals(countTokens(target).keySet())
            && countMatches(BROKEN_CODE, original) == countMatches(BROKEN_CODE, target);
    }

    /**
     * 書式コードを除いた文字列に英字が含まれるかを判定する。
     * @param text 対象テキスト
     * @return 英字を含めばtrue
     */
    private static boolean hasLetter(String text) {
        return LETTER.matcher(TOKEN_SINGLE.matcher(text).replaceAll("")).find();
    }

    /**
     * テキスト内の保護対象トークンを種類別に数える。
     * @param text 対象テキスト
     * @return トークン→出現数のマップ
     */
    private static Map<String, Integer> countTokens(String text) {
        Map<String, Integer> counts = new HashMap<>();
        Matcher m = TOKEN_SINGLE.matcher(text);
        while (m.find()) {
            Integer c = counts.get(m.group());
            counts.put(m.group(), c == null ? 1 : c + 1);
        }
        return counts;
    }

    /**
     * パターンの出現数を数える。
     * @param pattern 検出パターン
     * @param text 対象テキスト
     * @return 出現数
     */
    private static int countMatches(Pattern pattern, String text) {
        int count = 0;
        Matcher m = pattern.matcher(text);
        while (m.find()) {
            count++;
        }
        return count;
    }

    /**
     * HTML文字参照を通常の文字に戻す。
     * @param text 対象テキスト
     * @return 復元済みテキスト
     */
    private static String unescapeHtml(String text) {
        Matcher m = NUMERIC_ENTITY.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String num = m.group(1);
            String replacement = m.group();
            try {
                int cp = num.startsWith("x")
                    ? Integer.parseInt(num.substring(1), 16) : Integer.parseInt(num);
                replacement = new String(Character.toChars(cp));
            } catch (IllegalArgumentException e) {
                // 解釈できない参照はそのまま残す
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString()
            .replace("&quot;", "\"").replace("&apos;", "'").replace("&nbsp;", " ")
            .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&");
    }

    /**
     * 文字列の先頭の空白部分を取得する。
     * @param text 対象テキスト
     * @return 先頭の空白（なければ空文字）
     */
    private static String leadingSpace(String text) {
        return text.substring(0, text.length() - text.stripLeading().length());
    }

    /**
     * 文字列の末尾の空白部分を取得する。
     * @param text 対象テキスト
     * @return 末尾の空白（なければ空文字）
     */
    private static String trailingSpace(String text) {
        return text.substring(text.stripTrailing().length());
    }
}
