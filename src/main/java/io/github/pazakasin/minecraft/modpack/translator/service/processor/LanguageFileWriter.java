package io.github.pazakasin.minecraft.modpack.translator.service.processor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.github.pazakasin.minecraft.modpack.translator.service.callback.LogCallback;

/**
 * 言語ファイルを出力ディレクトリに書き込むクラス。
 * Minecraftの標準的なディレクトリ構造（assets/[modid]/lang）で保存。
 * 同一インスタンス（＝同一実行）内で同じ出力先に複数回書き込む場合はキー単位でマージする。
 */
public class LanguageFileWriter {
    /** 重複キーをログに列挙する最大件数。 */
    private static final int MAX_DUPLICATE_LOG = 10;
    
    /** 言語ファイルの出力先ルートディレクトリ。 */
    private final File outputDir;
    
    /** ログコールバック（null可）。 */
    private final LogCallback logger;
    
    /** この実行で書き込み済みの出力ファイル（絶対パス）。2回目以降はマージ対象。 */
    private final Set<String> writtenFiles = new HashSet<String>();
    
    /**
     * LanguageFileWriterのコンストラクタ（ログ出力なし）。
     * @param outputDir 出力先ルートディレクトリ
     */
    public LanguageFileWriter(File outputDir) {
        this(outputDir, null);
    }
    
    /**
     * LanguageFileWriterのコンストラクタ。
     * @param outputDir 出力先ルートディレクトリ
     * @param logger ログコールバック（null可）
     */
    public LanguageFileWriter(File outputDir, LogCallback logger) {
        this.outputDir = outputDir;
        this.logger = logger;
    }
    
    /**
     * 言語ファイルを出力ディレクトリに書き込みます。
     * ja_jp.jsonのみを出力し、en_us.jsonは出力しない（日本語内容がnullの場合はフォルダも作成しない）。
     * KubeJSの場合は専用パス、その他はリソースパック形式で出力。
     * 同一実行内で同じnamespaceへの2回目以降の書き込みは、既存内容とキー単位でマージする（重複キーは後勝ち）。
     * @param modId Mod ID
     * @param enUsContent 英語ファイル内容（使用しない）
     * @param jaJpContent 日本語ファイル内容
     * @throws IOException ファイル書き込み失敗
     */
    public synchronized void writeLanguageFiles(String modId, String enUsContent, String jaJpContent)
            throws IOException {
        File outputBase = outputDir.getParentFile();
        File langDir;
        if ("kubejs".equals(modId)) {
            langDir = new File(outputBase, "kubejs/assets/kubejs/lang");
        } else {
            langDir = new File(outputBase, "resourcepacks/MyJPpack/assets/" + modId + "/lang");
        }
        if (jaJpContent == null) {
            return;
        }
        langDir.mkdirs();
        File jaJpFile = new File(langDir, "ja_jp.json");
        String key = jaJpFile.getAbsolutePath();
        
        String content = jaJpContent;
        if (writtenFiles.contains(key) && jaJpFile.isFile()) {
            content = mergeWithExisting(modId, jaJpFile, jaJpContent);
        }
        Files.write(jaJpFile.toPath(), content.getBytes("UTF-8"));
        writtenFiles.add(key);
    }
    
    /**
     * 既存の出力ファイルと新しい内容をマージします。解析できない場合は新しい内容で上書きします。
     * @param modId Mod ID（ログ用）
     * @param existingFile 既存の出力ファイル
     * @param newContent 新しい内容
     * @return 書き込む内容
     * @throws IOException 既存ファイルの読み込み失敗
     */
    private String mergeWithExisting(String modId, File existingFile, String newContent) throws IOException {
        String existing = new String(Files.readAllBytes(existingFile.toPath()), "UTF-8");
        List<String> duplicates = new ArrayList<String>();
        try {
            String merged = JsonLangMerger.merge(existing, newContent, duplicates);
            log("[マージ] namespace '" + modId + "' は複数のJARで使用されているため、キー単位でマージしました");
            if (!duplicates.isEmpty()) {
                int shown = Math.min(duplicates.size(), MAX_DUPLICATE_LOG);
                log(String.format("  重複キー %d件（後から処理した内容を採用）: %s%s",
                        duplicates.size(), String.join(", ", duplicates.subList(0, shown)),
                        duplicates.size() > shown ? " ..." : ""));
            }
            return merged;
        } catch (IllegalArgumentException e) {
            log("[警告] namespace '" + modId + "' のマージに失敗したため上書きします: " + e.getMessage());
            return newContent;
        }
    }
    
    /**
     * ログを出力します。
     * @param message メッセージ
     */
    private void log(String message) {
        if (logger != null) {
            logger.onLog(message);
        }
    }
}
