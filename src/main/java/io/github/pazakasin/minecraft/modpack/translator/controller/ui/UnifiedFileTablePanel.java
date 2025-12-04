package io.github.pazakasin.minecraft.modpack.translator.controller.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;

import io.github.pazakasin.minecraft.modpack.translator.controller.ModPackTranslatorGUI;
import io.github.pazakasin.minecraft.modpack.translator.controller.ui.table.FileTableActionHandler;
import io.github.pazakasin.minecraft.modpack.translator.controller.ui.table.FileTableModel;
import io.github.pazakasin.minecraft.modpack.translator.controller.ui.table.GroupHeaderManager;
import io.github.pazakasin.minecraft.modpack.translator.model.TranslatableFile;

/**
 * 翻訳対象ファイルの選択と処理結果を統合表示するパネル。
 * ファイルタイプごとにグループ化し、ドロップダウンメニューで操作可能。
 */
public class UnifiedFileTablePanel extends JPanel {
	/** ファイル一覧を表示するテーブル。 */
	private JTable fileTable;
	
	/** ファイル内容確認ボタン。 */
	private JButton viewFileButton;
	
	/** 翻訳比較ボタン。 */
	private JButton compareButton;
	
	/** CSVエクスポートボタン。 */
	private JButton exportCsvButton;
	
	/** 選択済み文字数を表示するラベル。 */
	private JLabel selectedCharCountLabel;
	
	/** 親フレーム（ボタンアクション用） */
	private ModPackTranslatorGUI parentFrame;
	
	/** ファイルテーブルモデル。 */
	private FileTableModel fileTableModel;
	
	/** グループヘッダーマネージャー。 */
	private GroupHeaderManager groupHeaderManager;
	
	/** アクションハンドラー。 */
	private FileTableActionHandler actionHandler;
	
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
		initComponents();
	}
	
	/**
	 * UIコンポーネントを初期化します。
	 */
	private void initComponents() {
		setLayout(new BorderLayout(5, 5));
		setBorder(BorderFactory.createTitledBorder("翻訳対象ファイル一覧"));
		
		String[] columnNames = {"選択", "種別", "識別名", "パス", "文字数", "en", "ja", "状態", "結果"};
		DefaultTableModel tableModel = new DefaultTableModel(columnNames, 0) {
			@Override
			public Class<?> getColumnClass(int column) {
				if (column == 0) {
					return Boolean.class;
				}
				return String.class;
			}
			
			@Override
			public boolean isCellEditable(int row, int column) {
				if (fileTableModel.isGroupHeaderRow(row)) {
					return false;
				}
				return column == 0;
			}
		};
		
		fileTableModel = new FileTableModel(tableModel);
		groupHeaderManager = new GroupHeaderManager(fileTableModel);
		
		fileTable = new JTable(tableModel);
		fileTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		fileTable.setRowHeight(25);
		
		fileTable.setDefaultRenderer(Object.class, groupHeaderManager.createHeaderRenderer());
		
		setupTableColumns();
		
		selectedCharCountLabel = new JLabel("選択済み文字数: 0");
		selectedCharCountLabel.setFont(new Font("Dialog", Font.BOLD, 12));
		
		actionHandler = new FileTableActionHandler(fileTableModel, fileTable,
				selectedCharCountLabel, this);
		
		tableModel.addTableModelListener(new TableModelListener() {
			@Override
			public void tableChanged(TableModelEvent e) {
				actionHandler.updateSelectedCharCount();
			}
		});
		
		fileTable.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				if (e.getClickCount() == 2) {
					int selectedRow = fileTable.getSelectedRow();
					if (selectedRow >= 0 && !fileTableModel.isGroupHeaderRow(selectedRow)) {
						TranslatableFile file = actionHandler.getFileAtRow(selectedRow);
						if (file != null && actionHandler.isFileCompleted(selectedRow)) {
							if (parentFrame != null) {
								parentFrame.handleCompareTranslation(file);
							}
						} else {
							actionHandler.viewSelectedFile();
						}
					}
				} else if (e.getClickCount() == 1 && e.getButton() == MouseEvent.BUTTON3) {
					actionHandler.showGroupContextMenu(e);
				}
			}
		});
		
		JScrollPane scrollPane = new JScrollPane(fileTable);
		add(scrollPane, BorderLayout.CENTER);
		
		JPanel buttonPanel = createButtonPanel();
		add(buttonPanel, BorderLayout.SOUTH);
	}
	
	/**
	 * テーブル列の設定を行います。
	 */
	private void setupTableColumns() {
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
	}
	
	/**
	 * ボタンパネルを作成します。
	 * @return ボタンパネル
	 */
	private JPanel createButtonPanel() {
		JPanel panel = new JPanel(new BorderLayout(5, 5));
		
		JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
		
		JButton selectAllButton = new JButton("全選択");
		selectAllButton.addActionListener(e -> actionHandler.selectAll(true));
		
		JButton deselectAllButton = new JButton("全解除");
		deselectAllButton.addActionListener(e -> actionHandler.selectAll(false));
		
		viewFileButton = new JButton("ファイル内容確認");
		viewFileButton.addActionListener(e -> actionHandler.viewSelectedFile());
		
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
		
		JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 5));
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
		fileTableModel.updateFileList(files);
		actionHandler.updateSelectedCharCount();
	}
	
	/**
	 * 選択されたファイルのリストを取得します。
	 * @return 選択されたファイルのリスト
	 */
	public List<TranslatableFile> getSelectedFiles() {
		return actionHandler.getSelectedFiles();
	}
	
	/**
	 * 特定のファイルの処理状態を更新します。
	 * @param file 対象ファイル
	 */
	public void updateFileState(TranslatableFile file) {
		fileTableModel.updateFileState(file);
	}
	
	/**
	 * テーブルをクリアします。
	 */
	public void clearTable() {
		fileTableModel.clearTable();
		actionHandler.updateSelectedCharCount();
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
}
