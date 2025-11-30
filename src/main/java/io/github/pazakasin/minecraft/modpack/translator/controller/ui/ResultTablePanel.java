package io.github.pazakasin.minecraft.modpack.translator.controller.ui;

import io.github.pazakasin.minecraft.modpack.translator.model.ModProcessingResult;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.util.List;

/**
 * 処理結果をテーブル形式で表示するパネル。
 * Mod名、言語ファイル存在、翻訳実行、文字数、翻訳結果を一覧表示。
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
            "Mod名", "言語フォルダパス", "英語ファイル",
            "日本語ファイル", "翻訳実行", "文字数", "翻訳結果"
        };
        
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        
        resultTable = new JTable(tableModel);
        resultTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        resultTable.getColumnModel().getColumn(0).setPreferredWidth(200);
        resultTable.getColumnModel().getColumn(1).setPreferredWidth(250);
        
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
            
            Object[] row = {
                result.modName,
                result.langFolderPath,
                result.hasEnUs ? "○" : "×",
                result.hasJaJp ? "○" : "×",
                result.translated ? "○" : "×",
                result.characterCount,
                translationResult
            };
            tableModel.addRow(row);
        }
    }
}
