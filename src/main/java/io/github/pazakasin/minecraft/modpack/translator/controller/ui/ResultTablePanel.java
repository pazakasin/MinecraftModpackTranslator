package io.github.pazakasin.minecraft.modpack.translator.controller.ui;

import io.github.pazakasin.minecraft.modpack.translator.model.ModProcessingResult;
import io.github.pazakasin.minecraft.modpack.translator.model.QuestTranslationResult;
import io.github.pazakasin.minecraft.modpack.translator.model.QuestFileResult;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.util.List;

/**
 * 処理結果をテーブル形式で表示するパネル。
 * Mod名、言語ファイル存在、翻訳実行、文字数を一覧表示。
 * クエストファイルは個別の行として表示。
 */
public class ResultTablePanel extends JPanel {
    /** 処理結果を表示するテーブル */
    private final JTable resultTable;
    /** テーブルのデータモデル */
    private final DefaultTableModel tableModel;
    
    /**
     * ResultTablePanelのコンストラクタ。
     */
    public ResultTablePanel() {
        setLayout(new java.awt.BorderLayout());
        setBorder(BorderFactory.createTitledBorder("処理結果"));
        
        String[] columnNames = {
            "種別", "Mod/ファイル名", "ファイルパス", 
            "英語ファイル", "日本語ファイル", 
            "処理", "文字数", "結果"
        };
        
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        
        resultTable = new JTable(tableModel);
        resultTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        resultTable.getColumnModel().getColumn(0).setPreferredWidth(80);
        resultTable.getColumnModel().getColumn(1).setPreferredWidth(200);
        resultTable.getColumnModel().getColumn(2).setPreferredWidth(300);
        
        JScrollPane scrollPane = new JScrollPane(resultTable);
        add(scrollPane, java.awt.BorderLayout.CENTER);
    }
    
    /**
     * テーブルの内容をすべてクリアします。
     */
    public void clearTable() {
        tableModel.setRowCount(0);
    }
    
    /**
     * 処理結果リストをテーブルに反映します。
     * @param results 処理結果のリスト
     */
    public void updateTable(List<ModProcessingResult> results) {
        for (ModProcessingResult result : results) {
            if (result.questResult != null && !result.questResult.fileResults.isEmpty()) {
                addQuestRows(result);
            } else {
                addModRow(result);
            }
        }
    }
    
    /**
     * Mod行を追加します。
     */
    private void addModRow(ModProcessingResult result) {
        String translationResult = getModTranslationResult(result);
        String processType = getModProcessType(result);
        
        Object[] row = {
            "Mod",
            result.modName,
            result.langFolderPath,
            result.hasEnUs ? "○" : "×",
            result.hasJaJp ? "○" : "×",
            processType,
            result.characterCount,
            translationResult
        };
        tableModel.addRow(row);
    }
    
    /**
     * クエスト行を追加します。
     */
    private void addQuestRows(ModProcessingResult result) {
        QuestTranslationResult questResult = result.questResult;
        
        for (QuestFileResult fileResult : questResult.fileResults) {
            Object[] row = {
                fileResult.fileType,
                fileResult.fileName,
                fileResult.filePath,
                "-",
                "-",
                fileResult.translated ? "翻訳" : "スキップ",
                fileResult.characterCount,
                fileResult.success ? "○" : "×"
            };
            tableModel.addRow(row);
        }
    }
    
    /**
     * Mod翻訳結果の表示文字列を生成します。
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
     * Mod処理種別の表示文字列を生成します。
     */
    private String getModProcessType(ModProcessingResult result) {
        if (result.translated) {
            return "翻訳";
        } else if (result.hasJaJp) {
            return "差替";
        } else {
            return "スキップ";
        }
    }
}
