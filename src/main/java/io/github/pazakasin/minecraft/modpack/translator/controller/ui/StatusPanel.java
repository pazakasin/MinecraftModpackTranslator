package io.github.pazakasin.minecraft.modpack.translator.controller.ui;

import javax.swing.*;
import java.awt.*;

/**
 * ステータス情報表示パネル。
 * 現在の翻訳プロバイダーを表示。
 */
public class StatusPanel extends JPanel {
    /** 翻訳プロバイダー等のステータスを表示するラベル */
    private final JLabel statusLabel;
    
    /**
     * StatusPanelのコンストラクタ。
     */
    public StatusPanel() {
        setLayout(new FlowLayout(FlowLayout.LEFT));
        
        statusLabel = new JLabel();
        statusLabel.setFont(new Font("Dialog", Font.BOLD, 12));
        add(statusLabel);
    }
    
    /**
     * ステータステキストを設定します。
     * @param text 表示するステータステキスト
     */
    public void setStatusText(String text) {
        statusLabel.setText(text);
    }
}
