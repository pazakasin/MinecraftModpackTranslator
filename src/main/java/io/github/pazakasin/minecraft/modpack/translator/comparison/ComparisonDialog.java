package io.github.pazakasin.minecraft.modpack.translator.comparison;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.util.List;

/**
 * 翻訳比較結果を表示するダイアログ
 */
public class ComparisonDialog extends JDialog {
    /** 比較結果テーブル */
    private JTable resultTable;
    /** テーブルモデル */
    private DefaultTableModel tableModel;
    /** 統計情報ラベル */
    private JLabel statsLabel;
    
    /**
     * コンストラクタ
     * 
     * @param parent 親フレーム
     */
    public ComparisonDialog(Frame parent) {
        super(parent, "翻訳前後の比較", true);
        initComponents();
        setSize(900, 600);
        setLocationRelativeTo(parent);
    }
    
    /**
     * コンポーネントを初期化
     */
    private void initComponents() {
        setLayout(new BorderLayout(10, 10));
        
        JPanel topPanel = createTopPanel();
        add(topPanel, BorderLayout.NORTH);
        
        JScrollPane scrollPane = createTablePanel();
        add(scrollPane, BorderLayout.CENTER);
        
        JPanel bottomPanel = createBottomPanel();
        add(bottomPanel, BorderLayout.SOUTH);
    }
    
    /**
     * 上部パネルを作成（統計情報とフィルタ）
     * 
     * @return 上部パネル
     */
    private JPanel createTopPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));
        
        statsLabel = new JLabel("統計情報: ");
        panel.add(statsLabel, BorderLayout.NORTH);
        
        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        filterPanel.add(new JLabel("フィルタ: "));
        
        JComboBox<String> filterCombo = new JComboBox<String>(new String[]{
            "すべて", "変更のみ", "追加のみ", "削除のみ", "変更なし"
        });
        filterCombo.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
                applyFilter((String)filterCombo.getSelectedItem());
            }
        });
        filterPanel.add(filterCombo);
        
        panel.add(filterPanel, BorderLayout.CENTER);
        
        return panel;
    }
    
    /**
     * テーブルパネルを作成
     * 
     * @return スクロールペイン
     */
    private JScrollPane createTablePanel() {
        String[] columnNames = {"状態", "キー", "翻訳前", "翻訳後"};
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        
        resultTable = new JTable(tableModel);
        resultTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        resultTable.getColumnModel().getColumn(0).setPreferredWidth(80);
        resultTable.getColumnModel().getColumn(1).setPreferredWidth(200);
        resultTable.getColumnModel().getColumn(2).setPreferredWidth(300);
        resultTable.getColumnModel().getColumn(3).setPreferredWidth(300);
        
        TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<DefaultTableModel>(tableModel);
        resultTable.setRowSorter(sorter);
        
        return new JScrollPane(resultTable);
    }
    
    /**
     * 下部パネルを作成（閉じるボタン）
     * 
     * @return 下部パネル
     */
    private JPanel createBottomPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        panel.setBorder(BorderFactory.createEmptyBorder(5, 10, 10, 10));
        
        JButton closeButton = new JButton("閉じる");
        closeButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
                dispose();
            }
        });
        panel.add(closeButton);
        
        return panel;
    }
    
    /**
     * 比較結果を表示
     * 
     * @param results 比較結果のリスト
     */
    public void showResults(List<ComparisonResult> results) {
        tableModel.setRowCount(0);
        
        int addedCount = 0;
        int removedCount = 0;
        int modifiedCount = 0;
        int unchangedCount = 0;
        
        for (ComparisonResult result : results) {
            String status = getStatusString(result.getChangeType());
            tableModel.addRow(new Object[]{
                status,
                result.getKey(),
                result.getOriginalValue(),
                result.getTranslatedValue()
            });
            
            switch (result.getChangeType()) {
                case ADDED:
                    addedCount++;
                    break;
                case REMOVED:
                    removedCount++;
                    break;
                case MODIFIED:
                    modifiedCount++;
                    break;
                case UNCHANGED:
                    unchangedCount++;
                    break;
            }
        }
        
        updateStats(results.size(), addedCount, removedCount, modifiedCount, unchangedCount);
    }
    
    /**
     * 変更タイプを文字列に変換
     * 
     * @param changeType 変更タイプ
     * @return 表示用文字列
     */
    private String getStatusString(ComparisonResult.ChangeType changeType) {
        switch (changeType) {
            case ADDED:
                return "追加";
            case REMOVED:
                return "削除";
            case MODIFIED:
                return "変更";
            case UNCHANGED:
                return "変更なし";
            default:
                return "";
        }
    }
    
    /**
     * 統計情報を更新
     * 
     * @param total 総数
     * @param added 追加数
     * @param removed 削除数
     * @param modified 変更数
     * @param unchanged 変更なし数
     */
    private void updateStats(int total, int added, int removed, int modified, int unchanged) {
        statsLabel.setText(String.format(
            "統計情報: 総数=%d, 追加=%d, 削除=%d, 変更=%d, 変更なし=%d",
            total, added, removed, modified, unchanged
        ));
    }
    
    /**
     * フィルタを適用
     * 
     * @param filterType フィルタタイプ
     */
    private void applyFilter(String filterType) {
        TableRowSorter<DefaultTableModel> sorter = 
            (TableRowSorter<DefaultTableModel>)resultTable.getRowSorter();
        
        if ("すべて".equals(filterType)) {
            sorter.setRowFilter(null);
        } else {
            final String targetStatus;
            if ("変更のみ".equals(filterType)) {
                targetStatus = "変更";
            } else if ("追加のみ".equals(filterType)) {
                targetStatus = "追加";
            } else if ("削除のみ".equals(filterType)) {
                targetStatus = "削除";
            } else {
                targetStatus = "変更なし";
            }
            
            sorter.setRowFilter(new RowFilter<DefaultTableModel, Integer>() {
                @Override
                public boolean include(Entry<? extends DefaultTableModel, ? extends Integer> entry) {
                    return entry.getStringValue(0).equals(targetStatus);
                }
            });
        }
    }
}
