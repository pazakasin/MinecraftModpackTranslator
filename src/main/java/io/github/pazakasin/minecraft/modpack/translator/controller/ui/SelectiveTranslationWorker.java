package io.github.pazakasin.minecraft.modpack.translator.controller.ui;

import io.github.pazakasin.minecraft.modpack.translator.model.ModProcessingResult;
import io.github.pazakasin.minecraft.modpack.translator.model.TranslatableFile;
import io.github.pazakasin.minecraft.modpack.translator.service.ModPackProcessor;
import io.github.pazakasin.minecraft.modpack.translator.service.TranslationService;
import io.github.pazakasin.minecraft.modpack.translator.service.callback.LogCallback;
import io.github.pazakasin.minecraft.modpack.translator.service.callback.ProgressUpdateCallback;
import io.github.pazakasin.minecraft.modpack.translator.service.callback.FileStateUpdateCallback;

import javax.swing.SwingWorker;
import java.util.List;

/**
 * 選択されたファイルの翻訳処理をバックグラウンドで実行するSwingWorkerクラス。
 * UIスレッドをブロックせずに翻訳処理を実行し、完了時にコールバックを呼び出す。
 */
public class SelectiveTranslationWorker extends SwingWorker<List<ModProcessingResult>, Void> {
    /** ModPackディレクトリパス。 */
    private final String inputPath;
    
    /** 選択された翻訳対象ファイルのリスト。 */
    private final List<TranslatableFile> selectedFiles;
    
    /** 翻訳サービス。 */
    private final TranslationService translationService;
    
    /** ログメッセージを出力するコールバック。 */
    private final LogCallback logCallback;
    
    /** 進捗状況を更新するコールバック。 */
    private final ProgressUpdateCallback progressCallback;
    
    /** 翻訳完了時に呼ばれるコールバック。 */
    private final TranslationCompletionCallback completionCallback;
    
    /** エラー発生時に呼ばれるコールバック。 */
    private final TranslationErrorCallback errorCallback;
    
    /** ファイル状態更新時に呼ばれるコールバック。 */
    private final FileStateUpdateCallback fileStateCallback;
    
    /**
     * SelectiveTranslationWorkerのコンストラクタ。
     * @param inputPath ModPackディレクトリパス
     * @param selectedFiles 選択された翻訳対象ファイル
     * @param translationService 翻訳サービス
     * @param logCallback ログコールバック
     * @param progressCallback 進捗コールバック
     * @param completionCallback 完了コールバック
     * @param errorCallback エラーコールバック
     * @param fileStateCallback ファイル状態更新コールバック
     */
    public SelectiveTranslationWorker(String inputPath,
                                     List<TranslatableFile> selectedFiles,
                                     TranslationService translationService,
                                     LogCallback logCallback,
                                     ProgressUpdateCallback progressCallback,
                                     TranslationCompletionCallback completionCallback,
                                     TranslationErrorCallback errorCallback,
                                     FileStateUpdateCallback fileStateCallback) {
        this.inputPath = inputPath;
        this.selectedFiles = selectedFiles;
        this.translationService = translationService;
        this.logCallback = logCallback;
        this.progressCallback = progressCallback;
        this.completionCallback = completionCallback;
        this.errorCallback = errorCallback;
        this.fileStateCallback = fileStateCallback;
    }
    
    /**
     * バックグラウンドで翻訳処理を実行します。
     */
    @Override
    protected List<ModProcessingResult> doInBackground() throws Exception {
        ModPackProcessor processor = new ModPackProcessor(
            inputPath, translationService, logCallback, progressCallback);
        
        // ファイル状態更新コールバックを設定
        processor.setFileStateCallback(fileStateCallback);
        
        return processor.processSelectedFiles(selectedFiles);
    }
    
    /**
     * 翻訳処理完了後にEDTで実行されます。
     */
    @Override
    protected void done() {
        try {
            List<ModProcessingResult> results = get();
            if (completionCallback != null) {
                completionCallback.onComplete(results);
            }
        } catch (Exception e) {
            if (errorCallback != null) {
                errorCallback.onError(e);
            }
        }
    }
}
