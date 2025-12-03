package io.github.pazakasin.minecraft.modpack.translator.service.quest;

import net.querz.nbt.io.SNBTUtil;
import net.querz.nbt.tag.Tag;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;

/**
 * SNBTファイルのパース・書き込みを行うメインクラス。
 * Lang FileにはNBTパース、Quest File本体には正規表現ベースの処理を提供。
 */
public class SNBTParser {
    /** テキスト抽出処理を担当するヘルパー */
    private final SNBTTextExtractor textExtractor;
    
    /** 翻訳適用処理を担当するヘルパー */
    private final SNBTTranslationApplier translationApplier;
    
    /**
     * SNBTParserのコンストラクタ。
     */
    public SNBTParser() {
        this.textExtractor = new SNBTTextExtractor();
        this.translationApplier = new SNBTTranslationApplier();
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
     * @param questFile Quest Fileファイル
     * @return キーと値のマップ（連番付きキー）
     * @throws IOException ファイル読み込みエラー
     */
    public Map<String, String> extractTranslatableTexts(File questFile) throws IOException {
        return textExtractor.extractTranslatableTexts(questFile);
    }
    
    /**
     * Quest File本体用: 元のSNBTファイルのフォーマットを保持したまま翻訳を適用します。
     * @param sourceFile 元のSNBTファイル
     * @param targetFile 出力先ファイル
     * @param translations キーと翻訳のマップ（連番付きキー）
     * @throws IOException ファイルI/Oエラー
     */
    public void applyTranslations(File sourceFile, File targetFile, 
                                  Map<String, String> translations) throws IOException {
        translationApplier.applyTranslations(sourceFile, targetFile, translations);
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
