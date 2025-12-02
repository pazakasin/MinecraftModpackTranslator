package io.github.pazakasin.minecraft.modpack.translator.util;

import io.github.pazakasin.minecraft.modpack.translator.model.TranslatableFile;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 翻訳対象ファイル情報をCSVファイルにエクスポートするクラス。
 * BOM付きUTF-8でExcel互換の形式で出力。
 */
public class CsvExporter {
    /** CSVファイルの出力先ディレクトリ。 */
    private static final String OUTPUT_DIR = "output";

    /**
     * 翻訳対象ファイル情報をCSVファイルとして出力します。
     * @param files 翻訳対象ファイルリスト
     * @return 出力したCSVファイルの絶対パス
     * @throws IOException ファイル作成・書き込み失敗
     */
    public String exportTranslatableFiles(List<TranslatableFile> files) throws IOException {
        File outputDir = new File(OUTPUT_DIR);
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");
        String timestamp = dateFormat.format(new Date());
        String filename = "translatable_files_" + timestamp + ".csv";
        File csvFile = new File(outputDir, filename);

        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(csvFile), StandardCharsets.UTF_8))) {

            // BOMを書き込む
            writer.write('\uFEFF');

            writeHeader(writer);
            
            // 画面表示と同じ順序で出力（ファイルタイプごとにグループ化）
            List<TranslatableFile> sortedFiles = sortFilesByType(files);
            
            for (TranslatableFile file : sortedFiles) {
                writeFileRow(writer, file);
            }
            
            writeSummary(writer, files);
        }

        return csvFile.getAbsolutePath();
    }
    
    /**
     * ファイルを画面表示と同じ順序にソートします。
     * @param files 元のファイルリスト
     * @return ソート済みファイルリスト
     */
    private List<TranslatableFile> sortFilesByType(List<TranslatableFile> files) {
        List<TranslatableFile> sorted = new ArrayList<TranslatableFile>();
        
        // 画面表示と同じ順序
        TranslatableFile.FileType[] order = {
            TranslatableFile.FileType.QUEST_FILE,
            TranslatableFile.FileType.QUEST_LANG_FILE,
            TranslatableFile.FileType.KUBEJS_LANG_FILE,
            TranslatableFile.FileType.MOD_LANG_FILE
        };
        
        for (TranslatableFile.FileType type : order) {
            for (TranslatableFile file : files) {
                if (file.getFileType() == type) {
                    sorted.add(file);
                }
            }
        }
        
        return sorted;
    }
    
    /**
     * CSVヘッダー行を出力します。
     */
    private void writeHeader(BufferedWriter writer) throws IOException {
        writer.write("選択,種別,識別名,パス,文字数,en,ja,状態,結果");
        writer.newLine();
    }
    
    /**
     * ファイルデータ行を出力します。
     */
    private void writeFileRow(BufferedWriter writer, TranslatableFile file) throws IOException {
        // 選択状態
        writer.write(file.isSelected() ? "○" : "×");
        writer.write(",");
        
        // 種別
        writer.write(escapeCSV(file.getFileType().getDisplayName()));
        writer.write(",");
        
        // Mod/ファイル名
        writer.write(escapeCSV(file.getModName()));
        writer.write(",");
        
        // パス
        writer.write(escapeCSV(file.getLangFolderPath()));
        writer.write(",");
        
        // 文字数
        writer.write(String.valueOf(file.getCharacterCount()));
        writer.write(",");
        
        // en
        writer.write(file.getFileContent() != null ? "○" : "×");
        writer.write(",");
        
        // ja
        writer.write(file.isHasExistingJaJp() ? "○" : "×");
        writer.write(",");
        
        // 状態
        writer.write(escapeCSV(file.getProcessingState().getDisplayName()));
        writer.write(",");
        
        // 結果
        writer.write(escapeCSV(file.getResultMessage()));
        
        writer.newLine();
    }
    
    /**
     * サマリー情報を出力します。
     */
    private void writeSummary(BufferedWriter writer, List<TranslatableFile> files) throws IOException {
        writer.newLine();
        writer.write("=== ファイルサマリー ===");
        writer.newLine();
        
        int totalFiles = files.size();
        int selectedFiles = 0;
        int translationTargetFiles = 0;
        int existingFiles = 0;
        int completedFiles = 0;
        int failedFiles = 0;
        int totalChars = 0;
        int translationChars = 0;
        int existingChars = 0;
        
        for (TranslatableFile file : files) {
            totalChars += file.getCharacterCount();
            
            if (file.isSelected()) {
                selectedFiles++;
                
                if (file.isHasExistingJaJp()) {
                    existingFiles++;
                    existingChars += file.getCharacterCount();
                } else {
                    translationTargetFiles++;
                    translationChars += file.getCharacterCount();
                }
            }
            
            if (file.getProcessingState() == TranslatableFile.ProcessingState.COMPLETED) {
                completedFiles++;
            } else if (file.getProcessingState() == TranslatableFile.ProcessingState.FAILED) {
                failedFiles++;
            }
        }
        
        writer.write("総ファイル数," + totalFiles);
        writer.newLine();
        writer.write("選択されたファイル数," + selectedFiles);
        writer.newLine();
        writer.write("翻訳対象ファイル数," + translationTargetFiles);
        writer.newLine();
        writer.write("既存ファイル数," + existingFiles);
        writer.newLine();
        writer.write("翻訳完了ファイル数," + completedFiles);
        writer.newLine();
        writer.write("翻訳失敗ファイル数," + failedFiles);
        writer.newLine();
        writer.write("総文字数," + totalChars);
        writer.newLine();
        writer.write("翻訳対象文字数," + translationChars);
        writer.newLine();
        writer.write("既存ファイル文字数," + existingChars);
        writer.newLine();
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
