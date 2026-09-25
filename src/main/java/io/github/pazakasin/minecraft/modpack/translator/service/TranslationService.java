package io.github.pazakasin.minecraft.modpack.translator.service;

import io.github.pazakasin.minecraft.modpack.translator.service.callback.ProgressCallback;
import io.github.pazakasin.minecraft.modpack.translator.service.processor.JsonLangFlattener;
import io.github.pazakasin.minecraft.modpack.translator.service.provider.*;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import com.google.gson.JsonObject;

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

    /** 配列等を含むJSONの展開・復元を行うクラス。 */
    private final JsonLangFlattener flattener = new JsonLangFlattener();

    /** 同一原文の重複排除と、実行中の翻訳済みMap（プロバイダー切替後も実行単位で保持）。 */
    private final TranslationDeduplicator deduplicator = new TranslationDeduplicator();

    /** 書式コード不一致の警告一覧。 */
    private final FormatWarningReport formatWarnings = new FormatWarningReport();

    /**
     * 翻訳実行の開始時に呼び、翻訳済みMapと書式警告をクリアします。
     * 前回の実行結果を持ち越さないため、実行ごとに必ず呼ぶこと。
     */
    public void startRun() {
        deduplicator.clear();
        formatWarnings.clear();
    }

    /**
     * 書式コード不一致の警告をCSVに書き出します。
     * @param dir 出力先フォルダ
     * @return 出力したファイルの絶対パス（警告0件の場合はnull）
     * @throws IOException 書き込みエラー
     */
    public String writeFormatWarnings(File dir) throws IOException {
        return formatWarnings.writeCsv(dir);
    }

    /**
     * 書式コード不一致の警告件数を取得します。
     * @return 件数
     */
    public int getFormatWarningCount() {
        return formatWarnings.size();
    }

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
        return translateJsonFile(jsonContent, progressCallback, null);
    }

    /**
     * JSON形式の言語ファイルを翻訳します（書式警告にファイル名を記録）。
     * @param jsonContent 翻訳元のJSONコンテンツ
     * @param progressCallback 進捗コールバック（null可）
     * @param fileLabel 書式警告に記録するファイル名（null可）
     * @return 翻訳後のJSONコンテンツ
     * @throws IllegalStateException APIキー未設定
     * @throws Exception 翻訳エラー
     */
    public String translateJsonFile(String jsonContent, ProgressCallback progressCallback,
            String fileLabel) throws Exception {
        if (currentProvider == null) {
            throw new IllegalStateException("APIキーが設定されていません");
        }

        // 【JSONフォーマット誤り対策】
        // 一部Modの言語ファイルには、オブジェクト/配列の閉じ括弧の直前に
        // 不要な末尾カンマ（トレイリングカンマ）が残っているものが存在し、
        // そのままGsonで解析するとMalformedJsonExceptionが発生する。
        // 各プロバイダー（Google/DeepL/ChatGPT/Claude）に渡す前にここで一括して
        // 無害化する。正しいJSONにはこのパターンは出現しないため、
        // 正常なファイルの内容・翻訳結果には影響しない。
        String sanitizedContent = removeTrailingCommas(jsonContent);

        // 【配列等を含むJSON対策】
        // 各プロバイダーは「キー→文字列」の単純なJSONのみを想定しているため、
        // 翻訳前に文字列のみの形へ展開し、翻訳後に元の構造へ復元する。
        // 【同一原文の重複排除】
        // 展開した値は、同じ原文を1つにまとめ、実行中の翻訳済みMapにあるものは再利用する
        // （値がすべて文字列のファイルも同じ経路を通る）。
        JsonObject root;
        try {
            root = flattener.parse(sanitizedContent);
        } catch (IllegalArgumentException e) {
            // 解析できない場合は従来どおりプロバイダーに任せる（エラーはプロバイダー側で通知）
            return currentProvider.translateJsonFile(sanitizedContent, progressCallback);
        }

        Map<String, String> flat = flattener.flatten(root);
        if (flat.isEmpty()) {
            return flattener.toJson(root);
        }
        Map<String, String> translated = deduplicator.translate(flat, currentProvider, progressCallback);
        checkFormat(flat, translated, fileLabel);
        return flattener.toJson(flattener.unflatten(root, translated));
    }

    /**
     * 原文と訳文の書式コードを比較し、不一致を警告に追加します。
     * @param flat キー→原文
     * @param translated キー→訳文
     * @param fileLabel ファイル名（null可）
     */
    private void checkFormat(Map<String, String> flat, Map<String, String> translated, String fileLabel) {
        for (Map.Entry<String, String> entry : flat.entrySet()) {
            String value = translated.get(entry.getKey());
            if (value != null && !FormatCodeProtector.isConsistent(entry.getValue(), value)) {
                formatWarnings.add(fileLabel, entry.getKey(), entry.getValue(), value);
            }
        }
    }

    /**
     * 【JSONフォーマット誤り対策】
     * JSON文字列から末尾カンマ（オブジェクト「}」または配列「]」の
     * 閉じ括弧の直前にある不要な「,」）を除去します。
     * 一部Modの言語ファイルに見られるJSONフォーマット誤り（トレイリングカンマ）に対する
     * 救済措置であり、正しいJSONにはこのパターンが出現しないため、
     * 正常なファイルの内容は変化しません。
     * @param jsonContent 元のJSON文字列
     * @return 末尾カンマを除去したJSON文字列（jsonContentがnullの場合はnull）
     */
    private String removeTrailingCommas(String jsonContent) {
        if (jsonContent == null) {
            return null;
        }

        return jsonContent.replaceAll(",(\\s*[}\\]])", "$1");
    }
}
