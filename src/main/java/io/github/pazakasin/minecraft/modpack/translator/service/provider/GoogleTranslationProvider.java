package io.github.pazakasin.minecraft.modpack.translator.service.provider;

import com.google.gson.*;
import io.github.pazakasin.minecraft.modpack.translator.service.callback.ProgressCallback;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.Map.Entry;

/**
 * Google Cloud Translation APIを使用した翻訳プロバイダー。
 * バッチサイズ128で高速に複数エントリーを翻訳。
 */
public class GoogleTranslationProvider implements TranslationProvider {
    /**
     * デバッグモードを設定します。
     * @param debugMode trueでデバッグモード有効
     */
    @Override
    public void setDebugMode(boolean debugMode) {
        this.debugMode = debugMode;
    }
    
    /** 1回のAPIリクエストで送信するテキストの最大数。 */
    private static final int BATCH_SIZE = 128;
    
    /** Google Cloud Translation APIのAPIキー。 */
    private final String apiKey;
    
    /** JSON処理用のGsonインスタンス。 */
    private final Gson gson;
    
    /** デバッグモード（API呼び出しをスキップ）。 */
    private boolean debugMode = false;
    
    /**
     * GoogleTranslationProviderのコンストラクタ。
     * @param apiKey Google Cloud Translation APIキー
     */
    public GoogleTranslationProvider(String apiKey) {
        this.apiKey = apiKey;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }
    
    /**
     * JSON形式の言語ファイルをGoogle Translation APIで翻訳します。
     * @param jsonContent 翻訳元JSONコンテンツ
     * @param progressCallback 進捗コールバック
     * @return 翻訳後のJSONコンテンツ
     * @throws Exception API通信エラー等
     */
    @Override
    public String translateJsonFile(String jsonContent, ProgressCallback progressCallback) throws Exception {
        JsonObject original = gson.fromJson(jsonContent, JsonObject.class);
        JsonObject translated = new JsonObject();
        
        List<String> keys = new ArrayList<>();
        List<String> values = new ArrayList<>();
        
        for (Entry<String, JsonElement> entry : original.entrySet()) {
            keys.add(entry.getKey());
            values.add(entry.getValue().getAsString());
        }
        
        int totalEntries = values.size();
        List<String> translatedValues = new ArrayList<>();
        
        for (int i = 0; i < values.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, values.size());
            List<String> batch = values.subList(i, end);
            
            if (progressCallback != null) {
                progressCallback.onProgress(i, totalEntries);
            }
            
            List<String> batchResults = translateBatch(batch);
            translatedValues.addAll(batchResults);
            
            if (end < values.size()) {
                Thread.sleep(100);
            }
        }
        
        if (progressCallback != null) {
            progressCallback.onProgress(totalEntries, totalEntries);
        }
        
        for (int i = 0; i < keys.size(); i++) {
            translated.addProperty(keys.get(i), translatedValues.get(i));
        }
        
        return gson.toJson(translated);
    }
    
    /**
     * テキストのバッチをGoogle Translation APIで翻訳します。
     * @param texts 翻訳対象テキストリスト
     * @return 翻訳済みテキストリスト
     * @throws Exception 翻訳エラー
     */
    private List<String> translateBatch(List<String> texts) throws Exception {
        // デバッグモード時はダミーデータを返す
        if (debugMode) {
            Thread.sleep(200);
            List<String> result = new ArrayList<>();
            for (String text : texts) {
                result.add(applyDebugPrefix(text));
            }
            return result;
        }
        String urlStr = "https://translation.googleapis.com/language/translate/v2?key=" + apiKey;
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        
        try {
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setDoOutput(true);
            
            // 書式コード保護のためHTMLモードで送信（翻訳不要なテキストは送らない）
            List<String> results = new ArrayList<>(texts);
            List<Integer> targetIndexes = new ArrayList<>();
            JsonArray qArray = new JsonArray();
            for (int i = 0; i < texts.size(); i++) {
                if (FormatCodeProtector.needsTranslation(texts.get(i))) {
                    targetIndexes.add(i);
                    qArray.add(FormatCodeProtector.protect(texts.get(i)));
                }
            }
            if (targetIndexes.isEmpty()) {
                return results;
            }

            JsonObject requestBody = new JsonObject();
            requestBody.add("q", qArray);
            requestBody.addProperty("source", "en");
            requestBody.addProperty("target", "ja");
            requestBody.addProperty("format", "html");
            
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = requestBody.toString().getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            
            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                String errorMsg = readErrorStream(conn);
                IOException ioException = new IOException("Google API Error: " + responseCode + " - " + errorMsg);
                logApiError(texts, ioException);
                throw ioException;
            }
            
            String response = readInputStream(conn);
            JsonObject jsonResponse = gson.fromJson(response, JsonObject.class);
            JsonArray translations = jsonResponse.getAsJsonObject("data")
                .getAsJsonArray("translations");
            
            for (int i = 0; i < translations.size(); i++) {
                int index = targetIndexes.get(i);
                String translatedText = translations.get(i).getAsJsonObject()
                    .get("translatedText").getAsString();
                results.set(index, FormatCodeProtector.restore(translatedText, texts.get(index)));
            }
            return results;
        } finally {
            conn.disconnect();
        }
    }
    
    /**
     * デバッグモード用のプレフィックスを適用します。
     * 改行を含むテキストの場合、各行（空行を除く）にプレフィックスを付けます。
     * @param text 元のテキスト
     * @return プレフィックス付きテキスト
     */
    private String applyDebugPrefix(String text) {
        if (!text.contains("\n")) {
            return "[デバッグ] " + text;
        }
        
        String[] lines = text.split("\n", -1);
        StringBuilder result = new StringBuilder();
        
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                result.append("\n");
            }
            
            if (!lines[i].isEmpty()) {
                result.append("[デバッグ] ").append(lines[i]);
            }
        }
        
        return result.toString();
    }
    
    /** HTTPレスポンスを文字列として読み込みます。 */
    private String readInputStream(HttpURLConnection conn) throws IOException {
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                response.append(line);
            }
            return response.toString();
        }
    }
    
    /** HTTPエラーレスポンスを文字列として読み込みます。 */
    private String readErrorStream(HttpURLConnection conn) throws IOException {
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
            StringBuilder errorResponse = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                errorResponse.append(line);
            }
            return errorResponse.toString();
        }
    }
    
    /**
     * プロバイダー名を取得します。
     * @return "Google Translation API"
     */
    @Override
    public String getProviderName() {
        return "Google Translation API";
    }
    
    /**
     * API呼び出しエラーをログ出力します。
     * @param texts 処理中のテキストリスト
     * @param e 例外オブジェクト
     */
    private void logApiError(List<String> texts, Exception e) {
        System.err.println("=== Google Translation API Error ===");
        System.err.println("Texts being processed:");
        for (int i = 0; i < texts.size(); i++) {
            System.err.println("  [" + i + "]: " + texts.get(i));
        }
        System.err.println("Error: " + e.getMessage());
        e.printStackTrace();
    }
}
