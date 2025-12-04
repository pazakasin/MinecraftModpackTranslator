package io.github.pazakasin.minecraft.modpack.translator.controller.ui.table;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.TableCellRenderer;

/**
 * グループヘッダー行の表示・レンダリングを管理するクラス。
 */
public class GroupHeaderManager {
	/** ファイルテーブルモデル。 */
	private final FileTableModel fileTableModel;
	
	/**
	 * GroupHeaderManagerのコンストラクタ。
	 * @param fileTableModel ファイルテーブルモデル
	 */
	public GroupHeaderManager(FileTableModel fileTableModel) {
		this.fileTableModel = fileTableModel;
	}
	
	/**
	 * グループヘッダー行用のレンダラーを作成します。
	 * @return レンダラー
	 */
	public TableCellRenderer createHeaderRenderer() {
		return new GroupHeaderRenderer();
	}
	
	/**
	 * チェックボックス列用のレンダラーを作成します。
	 * @return レンダラー
	 */
	public TableCellRenderer createCheckBoxRenderer() {
		return new CheckBoxColumnRenderer();
	}
	
	/**
	 * チェックボックス列用のカスタムレンダラー。
	 * グループヘッダー行ではチェックボックスを表示しない。
	 */
	private class CheckBoxColumnRenderer extends JCheckBox implements TableCellRenderer {
		public CheckBoxColumnRenderer() {
			setHorizontalAlignment(SwingConstants.CENTER);
		}
		
		@Override
		public Component getTableCellRendererComponent(JTable table, Object value,
				boolean isSelected, boolean hasFocus, int row, int column) {
			
			if (fileTableModel.isGroupHeaderRow(row)) {
				JLabel label = new JLabel("");
				label.setOpaque(true);
				label.setBackground(new Color(220, 220, 220));
				return label;
			} else {
				if (isSelected) {
					setBackground(table.getSelectionBackground());
					setForeground(table.getSelectionForeground());
				} else {
					setBackground(Color.WHITE);
					setForeground(Color.BLACK);
				}
				setSelected(value != null && (Boolean) value);
				return this;
			}
		}
	}
	
	/**
	 * グループヘッダー行用のカスタムレンダラー。
	 */
	private class GroupHeaderRenderer extends JLabel implements TableCellRenderer {
		public GroupHeaderRenderer() {
			setOpaque(true);
			setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 5));
		}
		
		@Override
		public Component getTableCellRendererComponent(JTable table, Object value,
				boolean isSelected, boolean hasFocus, int row, int column) {
			
			if (fileTableModel.isGroupHeaderRow(row)) {
				setBackground(new Color(220, 220, 220));
				setFont(getFont().deriveFont(Font.BOLD));
				setHorizontalAlignment(SwingConstants.LEFT);
				
				if (column == 1) {
					setText(value != null ? value.toString() : "");
					setForeground(Color.BLACK);
				} else if (column == 2) {
					setText(value != null ? value.toString() : "");
					setForeground(Color.BLUE);
				} else {
					setText("");
					setForeground(Color.BLACK);
				}
			} else {
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
