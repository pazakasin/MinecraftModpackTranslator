package io.github.pazakasin.minecraft.modpack.translator.controller.ui;

import javax.swing.*;
import java.awt.*;

/**
 * 処理ログ表示パネル。
 * 翻訳処理中のメッセージや進捗情報をスクロール可能なテキストエリアに表示。
 */
public class LogPanel extends JPanel {
    /** ログメッセージを表示するテキストエリア */
    private final JTextArea logArea;
    
    /**
     * LogPanelのコンストラクタ。
     */
    public LogPanel() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("処理ログ"));
        
        logArea = new JTextArea(8, 0);
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        
        JScrollPane scrollPane = new JScrollPane(logArea);
        add(scrollPane, BorderLayout.CENTER);
    }
    
    /**
     * ログにメッセージを追加します。自動的にスクロールして最新メッセージを表示。
     * @param message 追加するメッセージ
     */
    public void appendLog(String message) {
        logArea.append(message + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }
    
    /**
     * ログの内容をすべてクリアします。
     */
    public void clearLog() {
        logArea.setText("");
    }
}
