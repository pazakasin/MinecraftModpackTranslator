package io.github.pazakasin.minecraft.modpack.translator.controller.ui;

import io.github.pazakasin.minecraft.modpack.translator.controller.ModPackTranslatorGUI;
import io.github.pazakasin.minecraft.modpack.translator.model.TranslatableFile;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

/**
 * 翻訳対象ファイルの選択と処理結果を統合表示するパネル。
 * ファイルタイプごとにグループ化し、ドロップダウンメニューで操作可能。
 */
public class UnifiedFileTablePanel extends JPanel {
    /** ファイル一覧を表示するテーブル。 */
    private JTable fileTable;
    
    /** テーブルのデータモデル。 */
    private DefaultTableModel tableModel;
    
    /** ファイル内容確認ボタン。 */
    private JButton viewFileButton;
    
    /** 翻訳比較ボタン。 */
    private JButton compareButton;
    
    /** CSVエクスポートボタン。 */
    private JButton exportCsvButton;
    
    /** 選択済み文字数を表示するラベル。 */
    private JLabel selectedCharCountLabel;
    
    /** 現在表示中のファイルリスト。 */
    private List<TranslatableFile> currentFiles;
    
    /** 親フレーム（ボタンアクション用） */
    private ModPackTranslatorGUI parentFrame;
    
    /** テーブル行からファイルへのマッピング（行番号 -> ファイル）。 */
    private Map<Integer, TranslatableFile> rowToFileMap;
    
    /** グループヘッダー行のインデックスマップ（行番号 -> グループタイプ）。 */
    private Map<Integer, TranslatableFile.FileType> groupHeaderRows;
    
    /** 各グループの開始インデックス（データ行のみ、ヘッダー除く）。 */
    private Map<TranslatableFile.FileType, Integer> groupStartIndices;
    
    /**
     * UnifiedFileTablePanelのコンストラクタ。
     */
    public UnifiedFileTablePanel() {
        this(null);
    }
    
    /**
     * UnifiedFileTablePanelのコンストラクタ。
     * @param parentFrame 親フレーム
     */
    public UnifiedFileTablePanel(ModPackTranslatorGUI parentFrame) {
        this.parentFrame = parentFrame;
        currentFiles = new ArrayList<TranslatableFile>();
        rowToFileMap = new HashMap<Integer, TranslatableFile>();
        groupHeaderRows = new HashMap<Integer, TranslatableFile.FileType>();
        groupStartIndices = new HashMap<TranslatableFile.FileType, Integer>();
        initComponents();
    }
    
    /** UIコンポーネントを初期化します。 */
    private void initComponents() {
        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createTitledBorder("翻訳対象ファイル一覧"));
        
        // テーブルの作成
        String[] columnNames = {"選択", "種別", "識別名", "パス", "文字数", "en", "ja", "状態", "結果"};
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public Class<?> getColumnClass(int column) {
                if (column == 0) {
                    return Boolean.class;
                }
                return String.class;
            }
            
            @Override
            public boolean isCellEditable(int row, int column) {
                // グループヘッダー行は編集不可
                if (groupHeaderRows.containsKey(row)) {
                    return false;
                }
                // チェックボックスのみ編集可能
                return column == 0;
            }
        };
        
        fileTable = new JTable(tableModel);
        fileTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        fileTable.setRowHeight(25);
        
        // グループヘッダー行のレンダラー
        fileTable.setDefaultRenderer(Object.class, new GroupHeaderRenderer());
        
        // 列幅の設定
        TableColumn selectColumn = fileTable.getColumnModel().getColumn(0);
        selectColumn.setPreferredWidth(50);
        selectColumn.setMaxWidth(50);
        
        TableColumn typeColumn = fileTable.getColumnModel().getColumn(1);
        typeColumn.setPreferredWidth(120);
        
        TableColumn modNameColumn = fileTable.getColumnModel().getColumn(2);
        modNameColumn.setPreferredWidth(200);
        
        TableColumn pathColumn = fileTable.getColumnModel().getColumn(3);
        pathColumn.setPreferredWidth(300);
        
        TableColumn charCountColumn = fileTable.getColumnModel().getColumn(4);
        charCountColumn.setPreferredWidth(80);
        
        TableColumn enColumn = fileTable.getColumnModel().getColumn(5);
        enColumn.setPreferredWidth(40);
        enColumn.setMaxWidth(40);
        
        TableColumn jaColumn = fileTable.getColumnModel().getColumn(6);
        jaColumn.setPreferredWidth(40);
        jaColumn.setMaxWidth(40);
        
        TableColumn stateColumn = fileTable.getColumnModel().getColumn(7);
        stateColumn.setPreferredWidth(80);
        
        TableColumn resultColumn = fileTable.getColumnModel().getColumn(8);
        resultColumn.setPreferredWidth(80);
        
        // チェックボックス変更時のイベント
        tableModel.addTableModelListener(e -> updateSelectedCharCount());
        
        // ダブルクリックでファイル内容確認または翻訳比較
        fileTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    // 翻訳後かどうかを判定
                    int selectedRow = fileTable.getSelectedRow();
                    if (selectedRow >= 0 && !groupHeaderRows.containsKey(selectedRow)) {
                        TranslatableFile file = rowToFileMap.get(selectedRow);
                        if (file != null && file.getProcessingState() == TranslatableFile.ProcessingState.COMPLETED) {
                            // 翻訳完了後は比較を実行
                            if (compareButton.isEnabled()) {
                                compareButton.doClick();
                            }
                        } else {
                            // 翻訳前はファイル内容確認
                            viewSelectedFile();
                        }
                    }
                } else if (e.getClickCount() == 1 && e.getButton() == MouseEvent.BUTTON3) {
                    showGroupContextMenu(e);
                }
            }
        });
        
        JScrollPane scrollPane = new JScrollPane(fileTable);
        add(scrollPane, BorderLayout.CENTER);
        
        // ボタンパネル
        JPanel buttonPanel = createButtonPanel();
        add(buttonPanel, BorderLayout.SOUTH);
    }
    
    /** ボタンパネルを作成します。 */
    private JPanel createButtonPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        
        // 左側：操作ボタン
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        
        JButton selectAllButton = new JButton("全選択");
        selectAllButton.addActionListener(e -> selectAll(true));
        
        JButton deselectAllButton = new JButton("全解除");
        deselectAllButton.addActionListener(e -> selectAll(false));
        
        viewFileButton = new JButton("ファイル内容確認");
        viewFileButton.addActionListener(e -> viewSelectedFile());
        
        compareButton = new JButton("翻訳比較");
        compareButton.setEnabled(false);
        compareButton.addActionListener(e -> {
            if (parentFrame != null) {
                parentFrame.handleCompareTranslation();
            }
        });
        
        exportCsvButton = new JButton("CSVエクスポート");
        exportCsvButton.setEnabled(false);
        exportCsvButton.addActionListener(e -> {
            if (parentFrame != null) {
                parentFrame.handleExportCsv();
            }
        });
        
        leftPanel.add(selectAllButton);
        leftPanel.add(deselectAllButton);
        leftPanel.add(new JSeparator(SwingConstants.VERTICAL));
        leftPanel.add(viewFileButton);
        leftPanel.add(compareButton);
        leftPanel.add(exportCsvButton);
        
        // 右側：選択済み文字数表示
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 5));
        selectedCharCountLabel = new JLabel("選択済み文字数: 0");
        selectedCharCountLabel.setFont(new Font("Dialog", Font.BOLD, 12));
        rightPanel.add(selectedCharCountLabel);
        
        panel.add(leftPanel, BorderLayout.WEST);
        panel.add(rightPanel, BorderLayout.EAST);
        
        return panel;
    }
    
    /**
     * ファイルリストを更新します。
     * @param files 翻訳対象ファイルのリスト
     */
    public void updateFileList(List<TranslatableFile> files) {
        currentFiles = files;
        rowToFileMap.clear();
        groupHeaderRows.clear();
        groupStartIndices.clear();
        tableModel.setRowCount(0);
        
        // ファイルタイプごとにグループ化
        Map<TranslatableFile.FileType, List<TranslatableFile>> groupedFiles = 
            new HashMap<TranslatableFile.FileType, List<TranslatableFile>>();
        
        for (TranslatableFile file : files) {
            TranslatableFile.FileType type = file.getFileType();
            if (!groupedFiles.containsKey(type)) {
                groupedFiles.put(type, new ArrayList<TranslatableFile>());
            }
            groupedFiles.get(type).add(file);
        }
        
        // グループごとに表示
        TranslatableFile.FileType[] order = {
            TranslatableFile.FileType.QUEST_FILE,
            TranslatableFile.FileType.QUEST_LANG_FILE,
            TranslatableFile.FileType.KUBEJS_LANG_FILE,
            TranslatableFile.FileType.MOD_LANG_FILE
        };
        
        int dataIndex = 0; // データ行のインデックス（ヘッダー除く）
        
        for (TranslatableFile.FileType type : order) {
            if (!groupedFiles.containsKey(type) || groupedFiles.get(type).isEmpty()) {
                continue;
            }
            
            List<TranslatableFile> groupFiles = groupedFiles.get(type);
            
            // グループヘッダー行を追加
            int headerRowIndex = tableModel.getRowCount();
            groupHeaderRows.put(headerRowIndex, type);
            groupStartIndices.put(type, dataIndex);
            
            Object[] headerRow = new Object[] {
                null, // 選択列は空
                type.getDisplayName() + " (" + groupFiles.size() + "件)",
                "[操作▼]",
                "",
                "",
                "",
                "",
                "",
                ""
            };
            tableModel.addRow(headerRow);
            
            // グループ内のファイル行を追加
            for (TranslatableFile file : groupFiles) {
                int currentRow = tableModel.getRowCount();
                rowToFileMap.put(currentRow, file); // 行とファイルの対応を記録
                
                Object[] row = new Object[] {
                    true, // 既存ファイルもデフォルトで選択
                    file.getFileType().getDisplayName(),
                    file.getModName(),
                    file.getLangFolderPath(),
                    file.getCharacterCount(),
                    file.getFileContent() != null ? "○" : "×",
                    file.isHasExistingJaJp() ? "○" : "×",
                    file.getProcessingState().getDisplayName(),
                    file.getResultMessage()
                };
                tableModel.addRow(row);
                
                // ファイルの選択状態も同期
                file.setSelected(true);
                dataIndex++;
            }
        }
        
        updateSelectedCharCount();
    }
    
    /**
     * 全ファイルの選択状態を変更します。
     * @param selected 選択状態
     */
    private void selectAll(boolean selected) {
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            // グループヘッダー行はスキップ
            if (groupHeaderRows.containsKey(i)) {
                continue;
            }
            
            tableModel.setValueAt(selected, i, 0);
        }
        
        for (TranslatableFile file : currentFiles) {
            file.setSelected(selected);
        }
        
        updateSelectedCharCount();
    }
    
    /**
     * 指定されたグループの選択状態を変更します。
     * @param type ファイルタイプ
     * @param selected 選択状態
     */
    private void selectGroup(TranslatableFile.FileType type, boolean selected) {
        for (TranslatableFile file : currentFiles) {
            if (file.getFileType() == type) {
                file.setSelected(selected);
            }
        }
        
        // テーブルの表示を更新
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            if (groupHeaderRows.containsKey(i)) {
                continue;
            }
            
            TranslatableFile file = rowToFileMap.get(i);
            if (file != null && file.getFileType() == type) {
                tableModel.setValueAt(selected, i, 0);
            }
        }
        
        updateSelectedCharCount();
    }
    
    /**
     * テーブル行番号からファイルインデックスを取得します。
     * @param row テーブル行番号
     * @return ファイルインデックス（グループヘッダー行の場合は-1）
     */
    private int getFileIndexFromRow(int row) {
        // グループヘッダー行の場合
        if (groupHeaderRows.containsKey(row)) {
            return -1;
        }
        
        // 現在の行より前にあるグループヘッダーの数を数える
        int headerCount = 0;
        for (Integer headerRow : groupHeaderRows.keySet()) {
            if (headerRow < row) {
                headerCount++;
            }
        }
        
        // ファイルインデックス = テーブル行番号 - ヘッダー数
        return row - headerCount;
    }
    
    /** 選択済み文字数を更新します。 */
    private void updateSelectedCharCount() {
        int translationCharCount = 0; // 翻訳対象（既存除く）
        int totalCharCount = 0;        // 総文字数（既存含む）
        
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            // グループヘッダー行はスキップ
            if (groupHeaderRows.containsKey(i)) {
                continue;
            }
            
            Boolean selected = (Boolean) tableModel.getValueAt(i, 0);
            TranslatableFile file = rowToFileMap.get(i);
            
            if (file != null) {
                if (selected != null && selected) {
                    file.setSelected(true);
                    int charCount = file.getCharacterCount();
                    totalCharCount += charCount;
                    
                    // 既存ファイル以外は翻訳対象
                    if (!file.isHasExistingJaJp()) {
                        translationCharCount += charCount;
                    }
                } else {
                    file.setSelected(false);
                }
            }
        }
        
        int existingCharCount = totalCharCount - translationCharCount;
        
        // 表示形式: 選択済み文字数: 12,345 (翻訳対象: 8,500 / 既存: 3,845)
        if (existingCharCount > 0) {
            selectedCharCountLabel.setText(String.format(
                "選択済み文字数: %,d (翻訳対象: %,d / 既存: %,d)",
                totalCharCount, translationCharCount, existingCharCount));
        } else {
            selectedCharCountLabel.setText(String.format(
                "選択済み文字数: %,d", totalCharCount));
        }
    }
    
    /** グループヘッダーのコンテキストメニューを表示します。 */
    private void showGroupContextMenu(MouseEvent e) {
        int row = fileTable.rowAtPoint(e.getPoint());
        int col = fileTable.columnAtPoint(e.getPoint());
        
        // グループヘッダー行の「操作」列をクリックした場合
        if (groupHeaderRows.containsKey(row) && col == 2) {
            TranslatableFile.FileType type = groupHeaderRows.get(row);
            
            JPopupMenu menu = new JPopupMenu();
            
            JMenuItem selectAllItem = new JMenuItem("全て選択");
            selectAllItem.addActionListener(ev -> selectGroup(type, true));
            menu.add(selectAllItem);
            
            JMenuItem deselectAllItem = new JMenuItem("全て解除");
            deselectAllItem.addActionListener(ev -> selectGroup(type, false));
            menu.add(deselectAllItem);
            
            menu.show(fileTable, e.getX(), e.getY());
        }
    }
    
    /**
     * 指定されたグループの既存ja_jp除外選択を実行します。
     * @param type ファイルタイプ
     */
    private void selectGroupExcludeExisting(TranslatableFile.FileType type) {
        for (TranslatableFile file : currentFiles) {
            if (file.getFileType() == type) {
                file.setSelected(!file.isHasExistingJaJp());
            }
        }
        
        // テーブルの表示を更新
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            if (groupHeaderRows.containsKey(i)) {
                continue;
            }
            
            TranslatableFile file = rowToFileMap.get(i);
            if (file != null && file.getFileType() == type) {
                tableModel.setValueAt(!file.isHasExistingJaJp(), i, 0);
            }
        }
        
        updateSelectedCharCount();
    }
    
    /** 選択中の行のファイル内容を確認します。 */
    private void viewSelectedFile() {
        int selectedRow = fileTable.getSelectedRow();
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(this,
                "ファイルを選択してください。",
                "情報", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        
        // グループヘッダー行の場合は何もしない
        if (groupHeaderRows.containsKey(selectedRow)) {
            return;
        }
        
        TranslatableFile file = rowToFileMap.get(selectedRow);
        if (file == null) {
            return;
        }
        
        try {
            File targetFile = null;
            
            if (file.getWorkFilePath() != null && !file.getWorkFilePath().isEmpty()) {
                targetFile = new File(file.getWorkFilePath());
                if (!targetFile.exists()) {
                    JOptionPane.showMessageDialog(this,
                        "workフォルダ内のファイルが見つかりません。\n" +
                        "ファイル解析を再実行してください。",
                        "エラー", JOptionPane.ERROR_MESSAGE);
                    return;
                }
            } else {
                targetFile = new File(file.getSourceFilePath());
            }
            
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(targetFile);
            } else {
                JOptionPane.showMessageDialog(this,
                    "ファイルを開く機能がサポートされていません。\n" +
                    "ファイルパス: " + targetFile.getAbsolutePath(),
                    "情報", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                "ファイルを開けませんでした: " + e.getMessage(),
                "エラー", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    /**
     * 選択されたファイルのリストを取得します。
     * @return 選択されたファイルのリスト
     */
    public List<TranslatableFile> getSelectedFiles() {
        List<TranslatableFile> selectedFiles = new ArrayList<TranslatableFile>();
        
        for (TranslatableFile file : currentFiles) {
            if (file.isSelected()) {
                selectedFiles.add(file);
            }
        }
        
        return selectedFiles;
    }
    
    /**
     * 特定のファイルの処理状態を更新します。
     * @param file 対象ファイル
     */
    public void updateFileState(TranslatableFile file) {
        // rowToFileMapから該当する行を検索
        for (Map.Entry<Integer, TranslatableFile> entry : rowToFileMap.entrySet()) {
            if (entry.getValue() == file) {
                int tableRow = entry.getKey();
                
                // 状態列と結果列を更新
                if (tableRow < tableModel.getRowCount()) {
                    tableModel.setValueAt(file.getProcessingState().getDisplayName(), tableRow, 7);
                    tableModel.setValueAt(file.getResultMessage(), tableRow, 8);
                }
                break;
            }
        }
    }
    
    /**
     * テーブルをクリアします。
     */
    public void clearTable() {
        currentFiles.clear();
        rowToFileMap.clear();
        groupHeaderRows.clear();
        groupStartIndices.clear();
        tableModel.setRowCount(0);
        updateSelectedCharCount();
    }
    
    /**
     * 翻訳比較ボタンを取得します。
     * @return 翻訳比較ボタン
     */
    public JButton getCompareButton() {
        return compareButton;
    }
    
    /**
     * CSVエクスポートボタンを取得します。
     * @return CSVエクスポートボタン
     */
    public JButton getExportCsvButton() {
        return exportCsvButton;
    }
    
    /**
     * グループヘッダー行用のカスタムレンダラー。
     */
    private class GroupHeaderRenderer extends JLabel implements TableCellRenderer {
        public GroupHeaderRenderer() {
            setOpaque(true);
        }
        
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            
            if (groupHeaderRows.containsKey(row)) {
                // グループヘッダー行
                setBackground(new Color(220, 220, 220));
                setFont(getFont().deriveFont(Font.BOLD));
                setHorizontalAlignment(SwingConstants.LEFT);
                
                if (column == 1) {
                    // 種別列にグループ名を表示
                    setText(value != null ? value.toString() : "");
                } else if (column == 2) {
                    // 操作列
                    setText(value != null ? value.toString() : "");
                    setForeground(Color.BLUE);
                } else {
                    setText("");
                }
            } else {
                // 通常行
                // 選択されている場合は行全体に背景色を付ける
                if (isSelected) {
                    setBackground(table.getSelectionBackground());
                    setForeground(table.getSelectionForeground());
                } else {
                    setBackground(Color.WHITE);
                    setForeground(Color.BLACK);
                }
                setFont(getFont().deriveFont(Font.PLAIN));
                setText(value != null ? value.toString() : "");
                setHorizontalAlignment(SwingConstants.LEFT);
            }
            
            return this;
        }
    }
}
