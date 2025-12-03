package io.github.pazakasin.minecraft.modpack.translator.controller.ui.table;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.table.DefaultTableModel;

import io.github.pazakasin.minecraft.modpack.translator.model.FileType;
import io.github.pazakasin.minecraft.modpack.translator.model.TranslatableFile;

/**
 * ファイルテーブルのデータモデル管理クラス。
 * テーブルデータ、行列マッピング、ファイル管理を担当。
 */
public class FileTableModel {
	/** テーブルのデータモデル。 */
	private final DefaultTableModel tableModel;
	
	/** 現在表示中のファイルリスト。 */
	private List<TranslatableFile> currentFiles;
	
	/** テーブル行からファイルへのマッピング（行番号 -> ファイル）。 */
	private Map<Integer, TranslatableFile> rowToFileMap;
	
	/** グループヘッダー行のインデックスマップ（行番号 -> グループタイプ）。 */
	private Map<Integer, FileType> groupHeaderRows;
	
	/** 各グループの開始インデックス（データ行のみ、ヘッダー除く）。 */
	private Map<FileType, Integer> groupStartIndices;
	
	/**
	 * FileTableModelのコンストラクタ。
	 * @param tableModel テーブルモデル
	 */
	public FileTableModel(DefaultTableModel tableModel) {
		this.tableModel = tableModel;
		this.currentFiles = new ArrayList<TranslatableFile>();
		this.rowToFileMap = new HashMap<Integer, TranslatableFile>();
		this.groupHeaderRows = new HashMap<Integer, FileType>();
		this.groupStartIndices = new HashMap<FileType, Integer>();
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
		
		Map<FileType, List<TranslatableFile>> groupedFiles = groupFiles(files);
		
		FileType[] order = {
			FileType.QUEST_FILE,
			FileType.QUEST_LANG_FILE,
			FileType.KUBEJS_LANG_FILE,
			FileType.MOD_LANG_FILE
		};
		
		int dataIndex = 0;
		
		for (FileType type : order) {
			if (!groupedFiles.containsKey(type) || groupedFiles.get(type).isEmpty()) {
				continue;
			}
			
			List<TranslatableFile> groupFiles = groupedFiles.get(type);
			
			int headerRowIndex = tableModel.getRowCount();
			groupHeaderRows.put(headerRowIndex, type);
			groupStartIndices.put(type, dataIndex);
			
			Object[] headerRow = new Object[] {
				null,
				type.getDisplayName() + " (" + groupFiles.size() + "件)",
				"[操作▼]",
				"", "", "", "", "", ""
			};
			tableModel.addRow(headerRow);
			
			for (TranslatableFile file : groupFiles) {
				int currentRow = tableModel.getRowCount();
				rowToFileMap.put(currentRow, file);
				
				Object[] row = new Object[] {
					true,
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
				
				file.setSelected(true);
				dataIndex++;
			}
		}
	}
	
	/**
	 * ファイルをタイプごとにグループ化します。
	 * @param files ファイルリスト
	 * @return グループ化されたファイルマップ
	 */
	private Map<FileType, List<TranslatableFile>> groupFiles(List<TranslatableFile> files) {
		Map<FileType, List<TranslatableFile>> groupedFiles = 
			new HashMap<FileType, List<TranslatableFile>>();
		
		for (TranslatableFile file : files) {
			FileType type = file.getFileType();
			if (!groupedFiles.containsKey(type)) {
				groupedFiles.put(type, new ArrayList<TranslatableFile>());
			}
			groupedFiles.get(type).add(file);
		}
		
		return groupedFiles;
	}
	
	/**
	 * 特定のファイルの処理状態を更新します。
	 * @param file 対象ファイル
	 */
	public void updateFileState(TranslatableFile file) {
		for (Map.Entry<Integer, TranslatableFile> entry : rowToFileMap.entrySet()) {
			if (entry.getValue() == file) {
				int tableRow = entry.getKey();
				
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
	}
	
	/**
	 * 現在のファイルリストを取得します。
	 * @return ファイルリスト
	 */
	public List<TranslatableFile> getCurrentFiles() {
		return currentFiles;
	}
	
	/**
	 * 行からファイルを取得します。
	 * @param row 行番号
	 * @return ファイル（グループヘッダー行の場合はnull）
	 */
	public TranslatableFile getFileAtRow(int row) {
		return rowToFileMap.get(row);
	}
	
	/**
	 * グループヘッダー行かどうかを判定します。
	 * @param row 行番号
	 * @return グループヘッダー行の場合true
	 */
	public boolean isGroupHeaderRow(int row) {
		return groupHeaderRows.containsKey(row);
	}
	
	/**
	 * 行のグループタイプを取得します。
	 * @param row 行番号
	 * @return グループタイプ（グループヘッダー行でない場合はnull）
	 */
	public FileType getGroupTypeAtRow(int row) {
		return groupHeaderRows.get(row);
	}
	
	/**
	 * テーブルモデルを取得します。
	 * @return テーブルモデル
	 */
	public DefaultTableModel getTableModel() {
		return tableModel;
	}
}
