package io.github.pazakasin.minecraft.modpack.translator.service;

import io.github.pazakasin.minecraft.modpack.translator.service.provider.*;

/**
 * 翻訳サービスの統合管理クラス。
 * 複数の翻訳プロバイダーを統合し、Strategyパターンで実行時に切り替え可能。
 */
public class TranslationService {
    /** 現在設定されているAPIキー。プロバイダーの認証に使用。 */
    private String apiKey;
    
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
                currentProvider = new ChatGPTTranslationProvider(apiKey);
                break;
            case CLAUDE:
                currentProvider = new ClaudeTranslationProvider(apiKey);
                break;
            default:
                throw new IllegalStateException("Unknown provider: " + providerType);
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
