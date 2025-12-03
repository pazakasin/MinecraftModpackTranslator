package io.github.pazakasin.minecraft.modpack.translator.controller.ui.table;

import java.awt.Desktop;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.List;

import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;

import io.github.pazakasin.minecraft.modpack.translator.model.FileType;
import io.github.pazakasin.minecraft.modpack.translator.model.ProcessingState;
import io.github.pazakasin.minecraft.modpack.translator.model.TranslatableFile;

/**
 * ファイルテーブルのアクション処理を担当するクラス。
 * ボタンクリック、右クリックメニュー、ファイル選択を管理。
 */
public class FileTableActionHandler {
	/** ファイルテーブルモデル。 */
	private final FileTableModel fileTableModel;
	
	/** テーブル。 */
	private final JTable fileTable;
	
	/** テーブルモデル。 */
	private final DefaultTableModel tableModel;
	
	/** 選択済み文字数ラベル。 */
	private final JLabel selectedCharCountLabel;
	
	/** 親パネル。 */
	private final JPanel parentPanel;
	
	/**
	 * FileTableActionHandlerのコンストラクタ。
	 * @param fileTableModel ファイルテーブルモデル
	 * @param fileTable テーブル
	 * @param selectedCharCountLabel 選択済み文字数ラベル
	 * @param parentPanel 親パネル
	 */
	public FileTableActionHandler(FileTableModel fileTableModel, JTable fileTable,
			JLabel selectedCharCountLabel, JPanel parentPanel) {
		this.fileTableModel = fileTableModel;
		this.fileTable = fileTable;
		this.tableModel = fileTableModel.getTableModel();
		this.selectedCharCountLabel = selectedCharCountLabel;
		this.parentPanel = parentPanel;
	}
	
	/**
	 * 全ファイルの選択状態を変更します。
	 * @param selected 選択状態
	 */
	public void selectAll(boolean selected) {
		for (int i = 0; i < tableModel.getRowCount(); i++) {
			if (fileTableModel.isGroupHeaderRow(i)) {
				continue;
			}
			
			tableModel.setValueAt(selected, i, 0);
		}
		
		for (TranslatableFile file : fileTableModel.getCurrentFiles()) {
			file.setSelected(selected);
		}
		
		updateSelectedCharCount();
	}
	
	/**
	 * 指定されたグループの選択状態を変更します。
	 * @param type ファイルタイプ
	 * @param selected 選択状態
	 */
	public void selectGroup(FileType type, boolean selected) {
		for (TranslatableFile file : fileTableModel.getCurrentFiles()) {
			if (file.getFileType() == type) {
				file.setSelected(selected);
			}
		}
		
		for (int i = 0; i < tableModel.getRowCount(); i++) {
			if (fileTableModel.isGroupHeaderRow(i)) {
				continue;
			}
			
			TranslatableFile file = fileTableModel.getFileAtRow(i);
			if (file != null && file.getFileType() == type) {
				tableModel.setValueAt(selected, i, 0);
			}
		}
		
		updateSelectedCharCount();
	}
	
	/**
	 * 選択済み文字数を更新します。
	 */
	public void updateSelectedCharCount() {
		int translationCharCount = 0;
		int totalCharCount = 0;
		
		for (int i = 0; i < tableModel.getRowCount(); i++) {
			if (fileTableModel.isGroupHeaderRow(i)) {
				continue;
			}
			
			Boolean selected = (Boolean) tableModel.getValueAt(i, 0);
			TranslatableFile file = fileTableModel.getFileAtRow(i);
			
			if (file != null) {
				if (selected != null && selected) {
					file.setSelected(true);
					int charCount = file.getCharacterCount();
					totalCharCount += charCount;
					
					if (!file.isHasExistingJaJp()) {
						translationCharCount += charCount;
					}
				} else {
					file.setSelected(false);
				}
			}
		}
		
		int existingCharCount = totalCharCount - translationCharCount;
		
		if (existingCharCount > 0) {
			selectedCharCountLabel.setText(String.format(
					"選択済み文字数: %,d (翻訳対象: %,d / 既存: %,d)",
					totalCharCount, translationCharCount, existingCharCount));
		} else {
			selectedCharCountLabel.setText(String.format(
					"選択済み文字数: %,d", totalCharCount));
		}
	}
	
	/**
	 * グループヘッダーのコンテキストメニューを表示します。
	 * @param e マウスイベント
	 */
	public void showGroupContextMenu(MouseEvent e) {
		int row = fileTable.rowAtPoint(e.getPoint());
		int col = fileTable.columnAtPoint(e.getPoint());
		
		if (fileTableModel.isGroupHeaderRow(row) && col == 2) {
			FileType type = fileTableModel.getGroupTypeAtRow(row);
			
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
	 * 選択中の行のファイル内容を確認します。
	 */
	public void viewSelectedFile() {
		int selectedRow = fileTable.getSelectedRow();
		if (selectedRow < 0) {
			JOptionPane.showMessageDialog(parentPanel,
					"ファイルを選択してください。",
					"情報", JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		
		if (fileTableModel.isGroupHeaderRow(selectedRow)) {
			return;
		}
		
		TranslatableFile file = fileTableModel.getFileAtRow(selectedRow);
		if (file == null) {
			return;
		}
		
		try {
			File targetFile = null;
			
			if (file.getWorkFilePath() != null && !file.getWorkFilePath().isEmpty()) {
				targetFile = new File(file.getWorkFilePath());
				if (!targetFile.exists()) {
					JOptionPane.showMessageDialog(parentPanel,
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
				JOptionPane.showMessageDialog(parentPanel,
						"ファイルを開く機能がサポートされていません。\n" +
						"ファイルパス: " + targetFile.getAbsolutePath(),
						"情報", JOptionPane.INFORMATION_MESSAGE);
			}
		} catch (Exception e) {
			JOptionPane.showMessageDialog(parentPanel,
					"ファイルを開けませんでした: " + e.getMessage(),
					"エラー", JOptionPane.ERROR_MESSAGE);
		}
	}
	
	/**
	 * 選択されたファイルのリストを取得します。
	 * @return 選択されたファイルのリスト
	 */
	public List<TranslatableFile> getSelectedFiles() {
		List<TranslatableFile> selectedFiles = new java.util.ArrayList<TranslatableFile>();
		
		for (TranslatableFile file : fileTableModel.getCurrentFiles()) {
			if (file.isSelected()) {
				selectedFiles.add(file);
			}
		}
		
		return selectedFiles;
	}
	
	/**
	 * ファイルが翻訳完了かどうかを判定します。
	 * @param row 行番号
	 * @return 翻訳完了の場合true
	 */
	public boolean isFileCompleted(int row) {
		TranslatableFile file = fileTableModel.getFileAtRow(row);
		return file != null && file.getProcessingState() == ProcessingState.COMPLETED;
	}
}
