package io.github.pazakasin.minecraft.modpack.translator.controller.ui;

import io.github.pazakasin.minecraft.modpack.translator.model.TranslatableFile;
import io.github.pazakasin.minecraft.modpack.translator.service.FileAnalysisService;
import io.github.pazakasin.minecraft.modpack.translator.service.callback.LogCallback;
import io.github.pazakasin.minecraft.modpack.translator.service.callback.ProgressUpdateCallback;

import javax.swing.SwingWorker;
import java.util.List;

/**
 * ファイル解析処理をバックグラウンドで実行するSwingWorkerクラス。
 * UIスレッドをブロックせずに解析処理を実行し、完了時にコールバックを呼び出す。
 */
public class AnalysisWorker extends SwingWorker<List<TranslatableFile>, Void> {
    /** ModPackディレクトリパス。 */
    private final String inputPath;
    
    /** ログメッセージを出力するコールバック。 */
    private final LogCallback logCallback;
    
    /** 進捗状況を更新するコールバック。 */
    private final ProgressUpdateCallback progressCallback;
    
    /** 解析完了時に呼ばれるコールバック。 */
    private final AnalysisCompletionCallback completionCallback;
    
    /** エラー発生時に呼ばれるコールバック。 */
    private final AnalysisErrorCallback errorCallback;
    
    /**
     * 解析完了時のコールバックインタフェース。
     */
    public interface AnalysisCompletionCallback {
        /**
         * 解析完了時に呼ばれます。
         * @param files 解析結果のファイルリスト
         */
        void onComplete(List<TranslatableFile> files);
    }
    
    /**
     * エラー発生時のコールバックインタフェース。
     */
    public interface AnalysisErrorCallback {
        /**
         * エラー発生時に呼ばれます。
         * @param error 発生した例外
         */
        void onError(Exception error);
    }
    
    /**
     * AnalysisWorkerのコンストラクタ。
     * @param inputPath ModPackディレクトリパス
     * @param logCallback ログコールバック
     * @param progressCallback 進捗コールバック
     * @param completionCallback 完了コールバック
     * @param errorCallback エラーコールバック
     */
    public AnalysisWorker(String inputPath, LogCallback logCallback,
                         ProgressUpdateCallback progressCallback,
                         AnalysisCompletionCallback completionCallback,
                         AnalysisErrorCallback errorCallback) {
        this.inputPath = inputPath;
        this.logCallback = logCallback;
        this.progressCallback = progressCallback;
        this.completionCallback = completionCallback;
        this.errorCallback = errorCallback;
    }
    
    /**
     * バックグラウンドで解析処理を実行します。
     */
    @Override
    protected List<TranslatableFile> doInBackground() throws Exception {
        FileAnalysisService analysisService = new FileAnalysisService(
            logCallback, progressCallback);
        
        return analysisService.analyzeFiles(inputPath);
    }
    
    /**
     * 解析処理完了後にEDTで実行されます。
     */
    @Override
    protected void done() {
        try {
            List<TranslatableFile> files = get();
            if (completionCallback != null) {
                completionCallback.onComplete(files);
            }
        } catch (Exception e) {
            if (errorCallback != null) {
                errorCallback.onError(e);
            }
        }
    }
}
