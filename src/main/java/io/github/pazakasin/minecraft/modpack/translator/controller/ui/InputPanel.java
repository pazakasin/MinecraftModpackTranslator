package io.github.pazakasin.minecraft.modpack.translator.controller.ui;

import javax.swing.*;
import java.awt.*;
import java.io.File;

/**
 * ModPackディレクトリ入力パネル。
 * ディレクトリパス入力フィールドとブラウザボタンを提供。
 */
public class InputPanel extends JPanel {
    /** ディレクトリパス入力フィールド */
    private final JTextField inputPathField;
    /** ディレクトリ選択ダイアログを開くボタン */
    private final JButton browseButton;
    
    /**
     * InputPanelのコンストラクタ。
     */
    public InputPanel() {
        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createTitledBorder("ModPackディレクトリ"));
        
        inputPathField = new JTextField();
        browseButton = new JButton("参照...");
        browseButton.addActionListener(e -> browseDirectory());
        
        add(inputPathField, BorderLayout.CENTER);
        add(browseButton, BorderLayout.EAST);
    }
    
    /** ディレクトリ選択ダイアログを表示し、選択されたパスを入力フィールドに設定します。 */
    private void browseDirectory() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("ModPackディレクトリを選択");
        
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            inputPathField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }
    
    /**
     * 入力されたディレクトリパスを取得します。
     * @return トリム済みのディレクトリパス
     */
    public String getInputPath() {
        return inputPathField.getText().trim();
    }
    
    /**
     * パネルとその子コンポーネントの有効/無効を設定します。
     * @param enabled 有効/無効
     */
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        inputPathField.setEnabled(enabled);
        browseButton.setEnabled(enabled);
    }
}
