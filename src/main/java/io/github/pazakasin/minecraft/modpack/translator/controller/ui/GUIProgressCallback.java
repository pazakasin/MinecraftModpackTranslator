package io.github.pazakasin.minecraft.modpack.translator.controller.ui;

import io.github.pazakasin.minecraft.modpack.translator.service.ProgressCallback;
import javax.swing.JProgressBar;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

/**
 * GUI用の進捗コールバック実装クラス。
 * 進捗バーとステータスラベルを更新する。
 */
public class GUIProgressCallback implements ProgressCallback {
    
    /** 進捗バー */
    private final JProgressBar progressBar;
    
    /** ステータスラベル */
    private final JLabel statusLabel;
    
    /**
     * コンストラクタ。
     * @param progressBar 進捗バー
     * @param statusLabel ステータスラベル
     */
    public GUIProgressCallback(JProgressBar progressBar, JLabel statusLabel) {
        if (progressBar == null) {
            throw new IllegalArgumentException("progressBar must not be null");
        }
        if (statusLabel == null) {
            throw new IllegalArgumentException("statusLabel must not be null");
        }
        this.progressBar = progressBar;
        this.statusLabel = statusLabel;
    }
    
    /**
     * 進捗状況を通知します。
     * GUIコンポーネントの更新はEDTで実行される。
     * @param current 現在の処理済み項目数
     * @param total 全体の項目数
     */
    @Override
    public void onProgress(int current, int total) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                // 進捗率を計算（0-100）
                int percentage = (total > 0) ? (current * 100) / total : 0;
                progressBar.setValue(percentage);
                
                // ステータスメッセージを更新
                statusLabel.setText("翻訳中... (" + current + "/" + total + ")");
            }
        });
    }
}
