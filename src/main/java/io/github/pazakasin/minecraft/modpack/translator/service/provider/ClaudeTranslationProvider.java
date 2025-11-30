package io.github.pazakasin.minecraft.modpack.translator.service.provider;

import com.google.gson.*;
import io.github.pazakasin.minecraft.modpack.translator.service.TranslationService.ProgressCallback;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

/**
 * Anthropic Claude APIを使用した翻訳プロバイダー。
 * Sonnet 4モデルで1リクエストでファイル全体を翻訳。
 */
public class ClaudeTranslationProvider implements TranslationProvider {
    /** Anthropic APIのAPIキー。 */
    private final String apiKey;
    
    /** JSON処理用のGsonインスタンス。 */
    private final Gson gson;
    
    /**
     * ClaudeTranslationProviderのコンストラクタ。
     * @param apiKey Anthropic APIキー
     */
    public ClaudeTranslationProvider(String apiKey) {
        this.apiKey = apiKey;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }
    
    /**
     * JSON形式の言語ファイルをClaude APIで翻訳します。
     * @param jsonContent 翻訳元JSONコンテンツ、progressCallback 進捗コールバック
     * @return 翻訳後のJSONコンテンツ
     * @throws Exception API通信エラー等
     */
    @Override
    public String translateJsonFile(String jsonContent, ProgressCallback progressCallback) throws Exception {
        if (progressCallback != null) {
            JsonObject temp = gson.fromJson(jsonContent, JsonObject.class);
            int total = temp.size();
            progressCallback.onProgress(0, total);
        }
        
        String urlStr = "https://api.anthropic.com/v1/messages";
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        
        try {
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("x-api-key", apiKey);
            conn.setRequestProperty("anthropic-version", "2023-06-01");
            conn.setDoOutput(true);
            
            String prompt = "以下のJSON形式のMinecraft Mod言語ファイルを英語から日本語に翻訳してください。" +
                          "キー名はそのまま保持し、値のみを翻訳してください。" +
                          "専門用語は適切に翻訳し、ゲーム内で自然な日本語になるようにしてください。" +
                          "JSONフォーマットのみを返してください。説明文や追加のテキストは一切不要です。\n\n" + jsonContent;
            
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("model", "claude-sonnet-4-20250514");
            requestBody.addProperty("max_tokens", 4096);
            
            JsonArray messages = new JsonArray();
            JsonObject message = new JsonObject();
            message.addProperty("role", "user");
            message.addProperty("content", prompt);
            messages.add(message);
            requestBody.add("messages", messages);
            
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = requestBody.toString().getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            
            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                throw new IOException("Claude API Error: " + responseCode + " - " + readErrorStream(conn));
            }
            
            String response = readInputStream(conn);
            JsonObject jsonResponse = gson.fromJson(response, JsonObject.class);
            JsonArray contentArray = jsonResponse.getAsJsonArray("content");
            String content = contentArray.get(0).getAsJsonObject()
                .get("text").getAsString();
            
            content = content.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
            gson.fromJson(content, JsonObject.class);
            
            if (progressCallback != null) {
                JsonObject temp = gson.fromJson(content, JsonObject.class);
                progressCallback.onProgress(temp.size(), temp.size());
            }
            
            return content;
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
     * @return "Claude API"
     */
    @Override
    public String getProviderName() {
        return "Claude API";
    }
}
