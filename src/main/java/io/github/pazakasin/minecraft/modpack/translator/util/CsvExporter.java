package io.github.pazakasin.minecraft.modpack.translator.util;

import io.github.pazakasin.minecraft.modpack.translator.model.ModProcessingResult;
import io.github.pazakasin.minecraft.modpack.translator.model.QuestTranslationResult;
import io.github.pazakasin.minecraft.modpack.translator.model.QuestFileResult;
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

            writer.write('\uFEFF');

            writeHeader(writer);
            
            for (ModProcessingResult result : results) {
                if (result.questResult != null && !result.questResult.fileResults.isEmpty()) {
                    writeQuestRows(writer, result);
                } else {
                    writeModRow(writer, result);
                }
            }
            
            writeSummary(writer, results);
        }

        return csvFile.getAbsolutePath();
    }
    
    /**
     * CSVヘッダー行を出力します。
     */
    private void writeHeader(BufferedWriter writer) throws IOException {
        writer.write("種別,Mod/ファイル名,ファイルパス,英語ファイル存在,日本語ファイル存在,");
        writer.write("処理種別,翻訳文字数,出力先パス,翻訳結果");
        writer.newLine();
    }
    
    /**
     * Modデータ行を出力します。
     */
    private void writeModRow(BufferedWriter writer, ModProcessingResult result) throws IOException {
        writer.write("Mod");
        writer.write(",");
        writer.write(escapeCSV(result.modName));
        writer.write(",");
        writer.write(escapeCSV(result.langFolderPath));
        writer.write(",");
        writer.write(result.hasEnUs ? "○" : "×");
        writer.write(",");
        writer.write(result.hasJaJp ? "○" : "×");
        writer.write(",");
        
        String processType;
        if (result.translated) {
            processType = "翻訳";
        } else if (result.hasJaJp) {
            processType = "差替";
        } else {
            processType = "スキップ";
        }
        writer.write(processType);
        writer.write(",");
        
        writer.write(String.valueOf(result.characterCount));
        writer.write(",");
        writer.write("-");
        writer.write(",");
        
        String translationResult = getModTranslationResult(result);
        writer.write(translationResult);
        
        writer.newLine();
    }
    
    /**
     * クエストデータ行を出力します。
     */
    private void writeQuestRows(BufferedWriter writer, ModProcessingResult result) throws IOException {
        QuestTranslationResult questResult = result.questResult;
        
        for (QuestFileResult fileResult : questResult.fileResults) {
            writer.write(escapeCSV(fileResult.fileType));
            writer.write(",");
            writer.write(escapeCSV(fileResult.fileName));
            writer.write(",");
            writer.write(escapeCSV(fileResult.filePath));
            writer.write(",");
            writer.write("-");
            writer.write(",");
            writer.write("-");
            writer.write(",");
            writer.write(fileResult.translated ? "翻訳" : "スキップ");
            writer.write(",");
            writer.write(String.valueOf(fileResult.characterCount));
            writer.write(",");
            writer.write(escapeCSV(fileResult.outputPath));
            writer.write(",");
            writer.write(fileResult.success ? "○" : "×");
            writer.newLine();
        }
    }
    
    /**
     * サマリー情報を出力します。
     */
    private void writeSummary(BufferedWriter writer, List<ModProcessingResult> results) throws IOException {
        writer.newLine();
        writer.write("=== 処理サマリー ===");
        writer.newLine();
        
        int totalMods = 0;
        int translatedMods = 0;
        int replacedMods = 0;
        int skippedMods = 0;
        int totalChars = 0;
        int questLangFiles = 0;
        int questFiles = 0;
        
        for (ModProcessingResult result : results) {
            if (result.questResult != null && !result.questResult.fileResults.isEmpty()) {
                for (QuestFileResult fileResult : result.questResult.fileResults) {
                    if ("Lang File".equals(fileResult.fileType)) {
                        questLangFiles++;
                    } else {
                        questFiles++;
                    }
                    totalChars += fileResult.characterCount;
                }
            } else {
                totalMods++;
                totalChars += result.characterCount;
                
                if (result.translated) {
                    translatedMods++;
                } else if (result.hasJaJp) {
                    replacedMods++;
                } else {
                    skippedMods++;
                }
            }
        }
        
        writer.write("処理したMod数," + totalMods);
        writer.newLine();
        writer.write("翻訳したMod数," + translatedMods);
        writer.newLine();
        writer.write("差し替えたMod数," + replacedMods);
        writer.newLine();
        writer.write("スキップしたMod数," + skippedMods);
        writer.newLine();
        writer.write("クエストLang File数," + questLangFiles);
        writer.newLine();
        writer.write("クエストファイル数," + questFiles);
        writer.newLine();
        writer.write("合計翻訳文字数," + totalChars);
        writer.newLine();
    }
    
    /**
     * Mod翻訳結果の文字列を取得します。
     */
    private String getModTranslationResult(ModProcessingResult result) {
        if (result.hasJaJp && !result.translated) {
            return "既存";
        } else if (result.translated && result.translationSuccess) {
            return "○";
        } else if (result.translated && !result.translationSuccess) {
            return "×";
        } else {
            return "-";
        }
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
