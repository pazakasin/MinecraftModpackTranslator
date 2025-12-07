package io.github.pazakasin.minecraft.modpack.translator.service.provider;

import io.github.pazakasin.minecraft.modpack.translator.service.callback.ProgressCallback;

/**
 * 翻訳プロバイダーの共通インターフェース。
 * 複数の翻訳サービスを統一的に扱うためのStrategy パターンの基盤。
 */
public interface TranslationProvider {
    /**
     * JSON形式の言語ファイルを翻訳します。
     * @param jsonContent 翻訳元のJSONコンテンツ（null不可）
     * @param progressCallback 進捗コールバック（null可）
     * @return 翻訳後のJSONコンテンツ
     * @throws Exception 翻訳処理中のエラー
     */
    String translateJsonFile(String jsonContent, ProgressCallback progressCallback) throws Exception;
    
    /**
     * プロバイダー名を取得します。
     * @return プロバイダー名
     */
    String getProviderName();
    
    /**
     * デバッグモードを設定します。
     * デバッグモード時はAPI呼び出しをスキップし、進捗表示のみをシミュレートします。
     * @param debugMode trueでデバッグモード有効
     */
    default void setDebugMode(boolean debugMode) {
        // デフォルト実装は何もしない（後方互換性のため）
    }
}
