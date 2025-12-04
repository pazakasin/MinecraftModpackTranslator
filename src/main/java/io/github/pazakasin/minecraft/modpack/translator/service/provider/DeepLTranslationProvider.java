package io.github.pazakasin.minecraft.modpack.translator.service.provider;

import com.google.gson.*;
import io.github.pazakasin.minecraft.modpack.translator.service.callback.ProgressCallback;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.Map.Entry;

/**
 * DeepL APIを使用した翻訳プロバイダー。
 * バッチサイズ50で効率的に複数エントリーを翻訳。
 */
public class DeepLTranslationProvider implements TranslationProvider {
    /** 1回のAPIリクエストで送信するテキストの最大数。 */
    private static final int BATCH_SIZE = 50;
    
    /** DeepL APIのAPIキー。 */
    private final String apiKey;
    
    /** JSON処理用のGsonインスタンス。 */
    private final Gson gson;
    
    /**
     * DeepLTranslationProviderのコンストラクタ。
     * @param apiKey DeepL APIキー
     */
    public DeepLTranslationProvider(String apiKey) {
        this.apiKey = apiKey;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }
    
    /**
     * JSON形式の言語ファイルをDeepL APIで翻訳します。
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
    
    /** テキストのバッチをDeepL APIで翻訳します。 */
    private List<String> translateBatch(List<String> texts) throws Exception {
        String urlStr = "https://api-free.deepl.com/v2/translate";
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        
        try {
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setRequestProperty("Authorization", "DeepL-Auth-Key " + apiKey);
            conn.setDoOutput(true);
            
            StringBuilder params = new StringBuilder();
            for (int i = 0; i < texts.size(); i++) {
                if (i > 0) params.append("&");
                params.append("text=").append(URLEncoder.encode(texts.get(i), "UTF-8"));
            }
            params.append("&source_lang=EN&target_lang=JA");
            
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = params.toString().getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            
            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                throw new IOException("DeepL API Error: " + responseCode + " - " + readErrorStream(conn));
            }
            
            String response = readInputStream(conn);
            JsonObject jsonResponse = gson.fromJson(response, JsonObject.class);
            JsonArray translations = jsonResponse.getAsJsonArray("translations");
            
            List<String> results = new ArrayList<>();
            for (int i = 0; i < translations.size(); i++) {
                results.add(translations.get(i).getAsJsonObject()
                    .get("text").getAsString());
            }
            return results;
        } finally {
            conn.disconnect();
        }
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
     * @return "DeepL API"
     */
    @Override
    public String getProviderName() {
        return "DeepL API";
    }
}
