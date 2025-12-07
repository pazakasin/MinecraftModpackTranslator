package io.github.pazakasin.minecraft.modpack.translator.service;

import io.github.pazakasin.minecraft.modpack.translator.service.callback.ProgressCallback;
import io.github.pazakasin.minecraft.modpack.translator.service.provider.*;

/**
 * 翻訳サービスの統合管理クラス。
 * 複数の翻訳プロバイダーを統合し、Strategyパターンで実行時に切り替え可能。
 */
public class TranslationService {
    /**
     * デバッグモードを設定します。
     * デバッグモード時はAPI呼び出しをスキップし、進捗表示のみをシミュレートします。
     * @param debugMode trueでデバッグモード有効
     */
    public void setDebugMode(boolean debugMode) {
        if (currentProvider != null) {
            currentProvider.setDebugMode(debugMode);
        }
    }
    
    /** 現在設定されているAPIキー。プロバイダーの認証に使用。 */
    private String apiKey;
    
    /** カスタムプロンプト。 */
    private String customPrompt;
    
    /** 現在選択されている翻訳プロバイダーのタイプ。デフォルトはGoogle。 */
    private ProviderType providerType;
    
    /** 現在アクティブな翻訳プロバイダーのインスタンス。 */
    private TranslationProvider currentProvider;
    
    /**
     * TranslationServiceのデフォルトコンストラクタ。
     * 初期プロバイダーはGoogleに設定。
     */
    public TranslationService() {
        this.providerType = ProviderType.GOOGLE;
    }
    
    /**
     * カスタムプロンプトを設定します。
     * @param customPrompt カスタムプロンプト
     */
    public void setCustomPrompt(String customPrompt) {
        this.customPrompt = customPrompt;
        updateProvider();
    }
    
    /**
     * APIキーを設定し、プロバイダーを更新します。
     * @param apiKey APIキー（nullまたは空文字列で無効化）
     */
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
        updateProvider();
    }
    
    /**
     * 翻訳プロバイダーのタイプを変更します。
     * @param providerType プロバイダータイプ（null不可）
     */
    public void setProvider(ProviderType providerType) {
        this.providerType = providerType;
        updateProvider();
    }
    
    /**
     * 現在設定されている翻訳プロバイダーのタイプを取得します。
     * @return プロバイダータイプ
     */
    public ProviderType getProvider() {
        return providerType;
    }
    
    /**
     * APIキーとプロバイダータイプに基づいてプロバイダーインスタンスを更新します。
     */
    private void updateProvider() {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            currentProvider = null;
            return;
        }
        
        switch (providerType) {
            case GOOGLE:
                currentProvider = new GoogleTranslationProvider(apiKey);
                break;
            case DEEPL:
                currentProvider = new DeepLTranslationProvider(apiKey);
                break;
            case CHATGPT:
                currentProvider = new ChatGPTTranslationProvider(apiKey, customPrompt);
                break;
            case CLAUDE:
                currentProvider = new ClaudeTranslationProvider(apiKey, customPrompt);
                break;
            default:
                throw new IllegalStateException("Unknown provider: " + providerType);
        }
        
        // 設定ファイルからデバッグモードを読み込んで適用
        try {
            Class<?> settingsClass = Class.forName("io.github.pazakasin.minecraft.modpack.translator.controller.SettingsDialog");
            java.lang.reflect.Method isDebugModeMethod = settingsClass.getMethod("isDebugMode");
            Boolean debugMode = (Boolean) isDebugModeMethod.invoke(null);
            if (debugMode != null && debugMode) {
                currentProvider.setDebugMode(true);
            }
        } catch (Exception e) {
            // デバッグモード設定の読み込み失敗しても無視
        }
    }
    
    /**
     * JSON形式の言語ファイルを翻訳します（進捗コールバックなし）。
     * @param jsonContent 翻訳元のJSONコンテンツ
     * @return 翻訳後のJSONコンテンツ
     * @throws Exception 翻訳エラー
     */
    public String translateJsonFile(String jsonContent) throws Exception {
        return translateJsonFile(jsonContent, null);
    }
    
    /**
     * JSON形式の言語ファイルを翻訳します。
     * @param jsonContent 翻訳元のJSONコンテンツ
     * @param progressCallback 進捗コールバック（null可）
     * @return 翻訳後のJSONコンテンツ
     * @throws IllegalStateException APIキー未設定
     * @throws Exception 翻訳エラー
     */
    public String translateJsonFile(String jsonContent, ProgressCallback progressCallback) throws Exception {
        if (currentProvider == null) {
            throw new IllegalStateException("APIキーが設定されていません");
        }
        
        return currentProvider.translateJsonFile(jsonContent, progressCallback);
    }
}
