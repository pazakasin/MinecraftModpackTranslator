package io.github.pazakasin.minecraft.modpack.translator.controller;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

/**
 * 使い方を表示するヘルプダイアログ。
 */
public class HelpDialog extends JDialog {
	/**
	 * HelpDialogのコンストラクタ。
	 * @param parent 親フレーム
	 */
	public HelpDialog(JFrame parent) {
		super(parent, "使い方", true);
		setSize(500, 300);
		setLocationRelativeTo(parent);
		setResizable(false);
		
		initComponents();
	}
	
	/**
	 * コンポーネントを初期化します。
	 */
	private void initComponents() {
		JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
		mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
		
		JTextArea helpText = new JTextArea();
		helpText.setEditable(false);
		helpText.setFont(new Font("Dialog", Font.PLAIN, 13));
		helpText.setLineWrap(true);
		helpText.setWrapStyleWord(true);
		helpText.setText(getHelpContent());
		
		JScrollPane scrollPane = new JScrollPane(helpText);
		scrollPane.setPreferredSize(new Dimension(450, 180));
		
		JButton closeButton = new JButton("閉じる");
		closeButton.addActionListener(e -> dispose());
		
		JPanel buttonPanel = new JPanel();
		buttonPanel.add(closeButton);
		
		mainPanel.add(scrollPane, BorderLayout.CENTER);
		mainPanel.add(buttonPanel, BorderLayout.SOUTH);
		
		add(mainPanel);
	}
	
	/**
	 * ヘルプ内容を取得します。
	 * @return ヘルプ内容
	 */
	private String getHelpContent() {
		StringBuilder sb = new StringBuilder();
		sb.append("【使い方】\n\n");
		sb.append("1. 「⚙設定」から翻訳プロバイダーとAPIキーを設定\n");
		sb.append("2. 「ModPackディレクトリ」を入力\n");
		sb.append("3. 「ファイル解析」で翻訳可能なファイルを検出\n");
		sb.append("4. 「翻訳対象ファイル一覧」で翻訳対象を選択\n");
		sb.append("5. 「翻訳実行」で翻訳を開始\n");
		return sb.toString();
	}
}
