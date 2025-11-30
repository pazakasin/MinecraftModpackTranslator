package io.github.pazakasin.minecraft.modpack.translator.controller.ui;

import io.github.pazakasin.minecraft.modpack.translator.model.ModProcessingResult;
import io.github.pazakasin.minecraft.modpack.translator.service.ModPackProcessor;
import io.github.pazakasin.minecraft.modpack.translator.service.TranslationService;
import javax.swing.*;
import java.util.List;
import java.util.function.Consumer;

/**
 * 翻訳処理をバックグラウンドで実行するSwingWorker。
 * GUIをブロックせずに長時間実行される翻訳処理を実行し、進捗をリアルタイムで通知。
 */
public class TranslationWorker extends SwingWorker<List<ModProcessingResult>, String> {
    /** 処理対象のModPackディレクトリパス */
    private final String inputPath;
    /** 翻訳サービスのインスタンス */
    private final TranslationService translationService;
    /** ログメッセージを受け取るコンシューマー */
    private final Consumer<String> logConsumer;
    /** 進捗メッセージを受け取るコンシューマー */
    private final Consumer<String> progressConsumer;
    /** 処理完了時に呼ばれるコールバック */
    private final Consumer<List<ModProcessingResult>> onComplete;
    /** エラー発生時に呼ばれるコールバック */
    private final Consumer<Exception> onError;
    
    /**
     * TranslationWorkerのコンストラクタ。
     * @param inputPath 処理対象ディレクトリパス、translationService 翻訳サービス、logConsumer ログコールバック、progressConsumer 進捗コールバック、onComplete 完了コールバック、onError エラーコールバック
     */
    public TranslationWorker(String inputPath, 
                            TranslationService translationService,
                            Consumer<String> logConsumer,
                            Consumer<String> progressConsumer,
                            Consumer<List<ModProcessingResult>> onComplete,
                            Consumer<Exception> onError) {
        this.inputPath = inputPath;
        this.translationService = translationService;
        this.logConsumer = logConsumer;
        this.progressConsumer = progressConsumer;
        this.onComplete = onComplete;
        this.onError = onError;
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
            message -> publish(message),
            progress -> {}
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
                progressConsumer.accept(message.substring(9));
            } else {
                logConsumer.accept(message);
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
            onComplete.accept(results);
        } catch (Exception e) {
            onError.accept(e);
        }
    }
}
