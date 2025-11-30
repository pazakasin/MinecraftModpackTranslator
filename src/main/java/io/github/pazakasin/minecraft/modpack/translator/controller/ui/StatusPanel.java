package io.github.pazakasin.minecraft.modpack.translator.controller.ui;

import javax.swing.*;
import java.awt.*;

/**
 * ステータス情報表示パネル。
 * 現在の翻訳プロバイダーと処理進捗を表示。
 */
public class StatusPanel extends JPanel {
    /** 翻訳プロバイダー等のステータスを表示するラベル */
    private final JLabel statusLabel;
    /** 処理進捗を表示するラベル */
    private final JLabel progressLabel;
    
    /**
     * StatusPanelのコンストラクタ。
     */
    public StatusPanel() {
        setLayout(new BorderLayout());
        
        JPanel providerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statusLabel = new JLabel();
        statusLabel.setFont(new Font("Dialog", Font.BOLD, 12));
        providerPanel.add(statusLabel);
        
        progressLabel = new JLabel(" ");
        progressLabel.setFont(new Font("Dialog", Font.PLAIN, 11));
        progressLabel.setForeground(new Color(60, 120, 180));
        
        add(providerPanel, BorderLayout.WEST);
        add(progressLabel, BorderLayout.EAST);
    }
    
    /**
     * ステータステキストを設定します。
     * @param text 表示するステータステキスト
     */
    public void setStatusText(String text) {
        statusLabel.setText(text);
    }
    
    /**
     * 進捗テキストを設定します。
     * @param text 表示する進捗テキスト
     */
    public void setProgressText(String text) {
        progressLabel.setText(text);
    }
}
