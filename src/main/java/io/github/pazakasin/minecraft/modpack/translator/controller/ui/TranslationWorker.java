package io.github.pazakasin.minecraft.modpack.translator.controller.ui;

import io.github.pazakasin.minecraft.modpack.translator.controller.callback.TranslationCompletionCallback;
import io.github.pazakasin.minecraft.modpack.translator.controller.callback.TranslationErrorCallback;
import io.github.pazakasin.minecraft.modpack.translator.model.ModProcessingResult;
import io.github.pazakasin.minecraft.modpack.translator.service.ModPackProcessor;
import io.github.pazakasin.minecraft.modpack.translator.service.TranslationService;
import io.github.pazakasin.minecraft.modpack.translator.service.callback.*;
import javax.swing.*;
import java.util.List;

/**
 * 翻訳処理をバックグラウンドで実行するSwingWorker。
 * GUIをブロックせずに長時間実行される翻訳処理を実行し、進捗をリアルタイムで通知。
 */
public class TranslationWorker extends SwingWorker<List<ModProcessingResult>, String> {
    /** 処理対象のModPackディレクトリパス */
    private final String inputPath;
    /** 翻訳サービスのインスタンス */
    private final TranslationService translationService;
    /** ログメッセージを受け取るコールバック */
    private final LogCallback logCallback;
    /** 進捗メッセージを受け取るコールバック */
    private final LogCallback progressCallback;
    /** 処理完了時に呼ばれるコールバック */
    private final CompletionCallback completionCallback;
    /** エラー発生時に呼ばれるコールバック */
    private final ErrorCallback errorCallback;
    
    /**
     * TranslationWorkerのコンストラクタ。
     * @param inputPath 処理対象ディレクトリパス
     * @param translationService 翻訳サービス
     * @param logCallback ログコールバック
     * @param progressCallback 進捗コールバック
     * @param completionCallback 完了コールバック
     * @param errorCallback エラーコールバック
     */
    public TranslationWorker(String inputPath, 
                            TranslationService translationService,
                            LogCallback logCallback,
                            LogCallback progressCallback,
                            CompletionCallback completionCallback,
                            ErrorCallback errorCallback) {
        this.inputPath = inputPath;
        this.translationService = translationService;
        this.logCallback = logCallback;
        this.progressCallback = progressCallback;
        this.completionCallback = completionCallback;
        this.errorCallback = errorCallback;
    }
    
    /**
     * バックグラウンドスレッドで翻訳処理を実行します。
     * @return 処理結果のリスト
     * @throws Exception 処理中のエラー
     */
    @Override
    protected List<ModProcessingResult> doInBackground() throws Exception {
        ModPackProcessor processor = new ModPackProcessor(
            inputPath, 
            translationService,
            new LogCallback() {
                @Override
                public void onLog(String message) {
                    publish(message);
                }
            },
            new ProgressUpdateCallback() {
                @Override
                public void onProgressUpdate(int progress) {
                    // 現在未使用
                }
                
                @Override
                public void onProgressUpdate(String progress) {
                    // 現在未使用
                }
            }
        );
        return processor.process();
    }
    
    /**
     * バックグラウンドスレッドから公開されたメッセージを処理します。
     * "PROGRESS:"で始まるメッセージは進捗表示用、それ以外はログ表示用。
     * @param chunks 公開されたメッセージのリスト
     */
    @Override
    protected void process(List<String> chunks) {
        for (String message : chunks) {
            if (message.startsWith("PROGRESS:")) {
                progressCallback.onLog(message.substring(9));
            } else {
                logCallback.onLog(message);
            }
        }
    }
    
    /**
     * 処理完了時に呼ばれます。正常終了時は結果を、エラー時は例外をコールバックに渡します。
     */
    @Override
    protected void done() {
        try {
            List<ModProcessingResult> results = get();
            completionCallback.onComplete(results);
        } catch (Exception e) {
            errorCallback.onError(e);
        }
    }
}
