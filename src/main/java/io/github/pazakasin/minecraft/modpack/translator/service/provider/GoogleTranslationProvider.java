package io.github.pazakasin.minecraft.modpack.translator.service.provider;

import com.google.gson.*;
import io.github.pazakasin.minecraft.modpack.translator.service.TranslationService.ProgressCallback;
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
    /** 1回のAPIリクエストで送信するテキストの最大数。 */
    private static final int BATCH_SIZE = 128;
    
    /** Google Cloud Translation APIのAPIキー。 */
    private final String apiKey;
    
    /** JSON処理用のGsonインスタンス。 */
    private final Gson gson;
    
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
     * @param jsonContent 翻訳元JSONコンテンツ、progressCallback 進捗コールバック
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
    
    /** テキストのバッチをGoogle Translation APIで翻訳します。 */
    private List<String> translateBatch(List<String> texts) throws Exception {
        String urlStr = "https://translation.googleapis.com/language/translate/v2?key=" + apiKey;
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        
        try {
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setDoOutput(true);
            
            JsonObject requestBody = new JsonObject();
            JsonArray qArray = new JsonArray();
            for (String text : texts) {
                qArray.add(text);
            }
            requestBody.add("q", qArray);
            requestBody.addProperty("source", "en");
            requestBody.addProperty("target", "ja");
            requestBody.addProperty("format", "text");
            
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = requestBody.toString().getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            
            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                throw new IOException("Google API Error: " + responseCode + " - " + readErrorStream(conn));
            }
            
            String response = readInputStream(conn);
            JsonObject jsonResponse = gson.fromJson(response, JsonObject.class);
            JsonArray translations = jsonResponse.getAsJsonObject("data")
                .getAsJsonArray("translations");
            
            List<String> results = new ArrayList<>();
            for (int i = 0; i < translations.size(); i++) {
                results.add(translations.get(i).getAsJsonObject()
                    .get("translatedText").getAsString());
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
     * @return "Google Translation API"
     */
    @Override
    public String getProviderName() {
        return "Google Translation API";
    }
}
