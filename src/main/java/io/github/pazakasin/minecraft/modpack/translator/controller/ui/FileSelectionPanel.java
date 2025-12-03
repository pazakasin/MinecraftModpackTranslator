package io.github.pazakasin.minecraft.modpack.translator.controller.ui;

import io.github.pazakasin.minecraft.modpack.translator.model.TranslatableFile;
import io.github.pazakasin.minecraft.modpack.translator.model.FileType;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 翻訳対象ファイルの選択UIパネル。
 * チェックボックス付きテーブルでファイルを表示し、選択/非選択を切り替え可能。
 */
public class FileSelectionPanel extends JPanel {
    /** ファイル一覧を表示するテーブル。 */
    private JTable fileTable;
    
    /** テーブルのデータモデル。 */
    private DefaultTableModel tableModel;
    
    /** 全選択ボタン。 */
    private JButton selectAllButton;
    
    /** 全解除ボタン。 */
    private JButton deselectAllButton;
    
    /** ファイル内容確認ボタン。 */
    private JButton viewFileButton;
    
    /** 選択済み文字数を表示するラベル。 */
    private JLabel selectedCharCountLabel;
    
    /** 現在表示中のファイルリスト。 */
    private List<TranslatableFile> currentFiles;
    
    /**
     * FileSelectionPanelのコンストラクタ。
     */
    public FileSelectionPanel() {
        currentFiles = new ArrayList<TranslatableFile>();
        initComponents();
    }
    
    /** UIコンポーネントを初期化します。 */
    private void initComponents() {
        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createTitledBorder("翻訳対象ファイル選択"));
        
        // テーブルの作成
        String[] columnNames = {"選択", "種別", "Mod/ファイル名", "パス", "文字数", "状態"};
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
                return column == 0; // チェックボックスのみ編集可能
            }
        };
        
        fileTable = new JTable(tableModel);
        fileTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        fileTable.setRowHeight(25);
        fileTable.setAutoCreateRowSorter(true); // ソート機能を有効化
        
        // 文字数列（第5列）を数値としてソート
        TableRowSorter<DefaultTableModel> sorter = 
            (TableRowSorter<DefaultTableModel>) fileTable.getRowSorter();
        sorter.setComparator(4, new java.util.Comparator<Object>() {
            @Override
            public int compare(Object o1, Object o2) {
                Integer i1 = Integer.parseInt(o1.toString());
                Integer i2 = Integer.parseInt(o2.toString());
                return i1.compareTo(i2);
            }
        });
        
        // 列幅の設定
        TableColumn selectColumn = fileTable.getColumnModel().getColumn(0);
        selectColumn.setPreferredWidth(50);
        selectColumn.setMaxWidth(50);
        
        TableColumn typeColumn = fileTable.getColumnModel().getColumn(1);
        typeColumn.setPreferredWidth(120);
        
        TableColumn modNameColumn = fileTable.getColumnModel().getColumn(2);
        modNameColumn.setPreferredWidth(260); // 30%増（200 → 260）
        
        TableColumn pathColumn = fileTable.getColumnModel().getColumn(3);
        pathColumn.setPreferredWidth(600); // 2倍（300 → 600）
        
        TableColumn charCountColumn = fileTable.getColumnModel().getColumn(4);
        charCountColumn.setPreferredWidth(80);
        
        TableColumn statusColumn = fileTable.getColumnModel().getColumn(5);
        statusColumn.setPreferredWidth(150); // 「既存日本語ファイル有」が表示できる幅
        
        // チェックボックス変更時のイベント
        tableModel.addTableModelListener(e -> updateSelectedCharCount());
        
        // ダブルクリックでファイル内容確認
        fileTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    viewSelectedFile();
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
        
        // 左側：選択操作ボタン
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        
        selectAllButton = new JButton("全選択");
        selectAllButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                selectAll(true);
            }
        });
        
        deselectAllButton = new JButton("全解除");
        deselectAllButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                selectAll(false);
            }
        });
        
        // クエストファイル用ボタン
        JButton selectQuestButton = new JButton("クエスト全選択");
        selectQuestButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                selectByFileType(true, FileType.QUEST_LANG_FILE, 
                                      FileType.QUEST_FILE);
            }
        });
        
        JButton deselectQuestButton = new JButton("クエスト全解除");
        deselectQuestButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                selectByFileType(false, FileType.QUEST_LANG_FILE, 
                                       FileType.QUEST_FILE);
            }
        });
        
        // Mod言語ファイル用ボタン
        JButton selectModButton = new JButton("Mod全選択");
        selectModButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                selectByFileType(true, FileType.MOD_LANG_FILE);
            }
        });
        
        JButton deselectModButton = new JButton("Mod全解除");
        deselectModButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                selectByFileType(false, FileType.MOD_LANG_FILE);
            }
        });
        
        viewFileButton = new JButton("ファイル内容確認");
        viewFileButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                viewSelectedFile();
            }
        });
        
        leftPanel.add(selectAllButton);
        leftPanel.add(deselectAllButton);
        leftPanel.add(new JSeparator(SwingConstants.VERTICAL));
        leftPanel.add(selectQuestButton);
        leftPanel.add(deselectQuestButton);
        leftPanel.add(new JSeparator(SwingConstants.VERTICAL));
        leftPanel.add(selectModButton);
        leftPanel.add(deselectModButton);
        leftPanel.add(new JSeparator(SwingConstants.VERTICAL));
        leftPanel.add(viewFileButton);
        
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
        tableModel.setRowCount(0);
        
        for (TranslatableFile file : files) {
            Object[] row = new Object[] {
                file.isSelected(),
                file.getFileType().getDisplayName(),
                file.getModName(),
                file.getLangFolderPath(),
                file.getCharacterCount(),
                file.isHasExistingJaJp() ? "既存日本語ファイル有" : "翻訳対象"
            };
            tableModel.addRow(row);
        }
        
        updateSelectedCharCount();
    }
    
    /**
     * 全ファイルの選択状態を変更します。
     * @param selected 選択状態
     */
    private void selectAll(boolean selected) {
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            tableModel.setValueAt(selected, i, 0);
            currentFiles.get(i).setSelected(selected);
        }
        updateSelectedCharCount();
    }
    
    /**
     * 指定されたファイル種別のみ選択状態を変更します。
     * @param selected 選択状態
     * @param fileTypes 対象ファイル種別（可変長引数）
     */
    private void selectByFileType(boolean selected, FileType... fileTypes) {
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            TranslatableFile file = currentFiles.get(i);
            
            // 指定されたファイル種別のいずれかに一致するかチェック
            boolean match = false;
            for (FileType type : fileTypes) {
                if (file.getFileType() == type) {
                    match = true;
                    break;
                }
            }
            
            if (match) {
                tableModel.setValueAt(selected, i, 0);
                file.setSelected(selected);
            }
        }
        updateSelectedCharCount();
    }
    
    /** 選択済み文字数を更新します。 */
    private void updateSelectedCharCount() {
        int selectedCount = 0;
        
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            Boolean selected = (Boolean) tableModel.getValueAt(i, 0);
            if (selected != null && selected) {
                // ソートを考慮して元のインデックスを取得
                int modelIndex = fileTable.convertRowIndexToModel(i);
                currentFiles.get(modelIndex).setSelected(true);
                selectedCount += currentFiles.get(modelIndex).getCharacterCount();
            } else {
                int modelIndex = fileTable.convertRowIndexToModel(i);
                currentFiles.get(modelIndex).setSelected(false);
            }
        }
        
        selectedCharCountLabel.setText("選択済み文字数: " + selectedCount);
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
        
        // ソートを考慮して元のインデックスを取得
        int modelIndex = fileTable.convertRowIndexToModel(selectedRow);
        TranslatableFile file = currentFiles.get(modelIndex);
        
        try {
            File targetFile = null;
            
            // Mod言語ファイルの場合はworkフォルダ内のファイルを開く
            if (file.getFileType() == FileType.MOD_LANG_FILE) {
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
                    JOptionPane.showMessageDialog(this,
                        "ファイルパスが設定されていません。",
                        "エラー", JOptionPane.ERROR_MESSAGE);
                    return;
                }
            } else {
                // クエストファイルの場合もworkフォルダ内のファイルを開く
                if (file.getWorkFilePath() != null && !file.getWorkFilePath().isEmpty()) {
                    targetFile = new File(file.getWorkFilePath());
                } else {
                    targetFile = new File(file.getSourceFilePath());
                }
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
     * テーブルをクリアします。
     */
    public void clearTable() {
        currentFiles.clear();
        tableModel.setRowCount(0);
        updateSelectedCharCount();
    }
}
