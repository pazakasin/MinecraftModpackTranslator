package io.github.pazakasin.minecraft.modpack.translator.util;

import io.github.pazakasin.minecraft.modpack.translator.model.ModProcessingResult;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * 処理結果をCSVファイルにエクスポートするクラス。
 * BOM付きUTF-8でExcel互換の形式で出力。
 */
public class CsvExporter {
    /** CSVファイルの出力先ディレクトリ。 */
    private static final String OUTPUT_DIR = "output";

    /**
     * 処理結果をCSVファイルとして出力します。
     * @param results Modの処理結果リスト
     * @return 出力したCSVファイルの絶対パス
     * @throws IOException ファイル作成・書き込み失敗
     */
    public String export(List<ModProcessingResult> results) throws IOException {
        File outputDir = new File(OUTPUT_DIR);
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");
        String timestamp = dateFormat.format(new Date());
        String filename = "translation_results_" + timestamp + ".csv";
        File csvFile = new File(outputDir, filename);

        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(csvFile), StandardCharsets.UTF_8))) {

            // BOM（Byte Order Mark）を追加してExcelで文字化けを防ぐ
            writer.write('\uFEFF');

            // ヘッダー行
            writer.write("Mod名,言語フォルダパス,英語ファイル存在,日本語ファイル存在,翻訳実行,翻訳対象文字数,翻訳結果");
            writer.newLine();

            // データ行
            for (ModProcessingResult result : results) {
                String translationResult;
                if (result.hasJaJp && !result.translated) {
                    translationResult = "既存";
                } else if (result.translated && result.translationSuccess) {
                    translationResult = "○";
                } else if (result.translated && !result.translationSuccess) {
                    translationResult = "×";
                } else {
                    translationResult = "-";
                }

                writer.write(escapeCSV(result.modName));
                writer.write(",");
                writer.write(escapeCSV(result.langFolderPath));
                writer.write(",");
                writer.write(result.hasEnUs ? "○" : "×");
                writer.write(",");
                writer.write(result.hasJaJp ? "○" : "×");
                writer.write(",");
                writer.write(result.translated ? "○" : "×");
                writer.write(",");
                writer.write(String.valueOf(result.characterCount));
                writer.write(",");
                writer.write(translationResult);
                writer.newLine();
            }
        }

        return csvFile.getAbsolutePath();
    }

    /**
     * CSV用に文字列をエスケープします。
     * @param value エスケープ対象の文字列
     * @return エスケープされた文字列
     */
    private String escapeCSV(String value) {
        if (value == null) {
            return "";
        }

        if (value.contains(",") || value.contains("\n") || value.contains("\"")) {
            value = value.replace("\"", "\"\"");
            return "\"" + value + "\"";
        }

        return value;
    }
}
