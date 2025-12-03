package io.github.pazakasin.minecraft.modpack.translator.service;

/**
 * 利用可能な翻訳プロバイダーの種類を定義するenum。
 * TranslationServiceで使用され、実行時にプロバイダーを切り替え可能。
 */
public enum ProviderType {
    /** Google Cloud Translation API */
    GOOGLE("Google Translation API"),
    
    /** DeepL Translation API */
    DEEPL("DeepL API"),
    
    /** OpenAI ChatGPT API */
    CHATGPT("ChatGPT API"),
    
    /** Anthropic Claude API */
    CLAUDE("Claude API");
    
    /** プロバイダーの表示名。UI表示用。 */
    private final String displayName;
    
    /**
     * ProviderTypeのコンストラクタ。
     * @param displayName 表示名
     */
    ProviderType(String displayName) {
        this.displayName = displayName;
    }
    
    /**
     * プロバイダーの表示名を取得します。
     * @return 表示名
     */
    public String getDisplayName() {
        return displayName;
    }
}
