package io.github.pazakasin.minecraft.modpack.translator.service.provider;

import com.google.gson.*;
import io.github.pazakasin.minecraft.modpack.translator.service.ProgressCallback;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

/**
 * OpenAI ChatGPT APIを使用した翻訳プロバイダー。
 * gpt-4o-miniモデルで1リクエストでファイル全体を翻訳。
 */
public class ChatGPTTranslationProvider implements TranslationProvider {
    /** OpenAI APIのAPIキー。 */
    private final String apiKey;
    
    /** JSON処理用のGsonインスタンス。 */
    private final Gson gson;
    
    /**
     * ChatGPTTranslationProviderのコンストラクタ。
     * @param apiKey OpenAI APIキー
     */
    public ChatGPTTranslationProvider(String apiKey) {
        this.apiKey = apiKey;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }
    
    /**
     * JSON形式の言語ファイルをChatGPT APIで翻訳します。
     * @param jsonContent 翻訳元JSONコンテンツ
     * @param progressCallback 進捗コールバック
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
        
        String urlStr = "https://api.openai.com/v1/chat/completions";
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        
        try {
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setDoOutput(true);
            
            String prompt = "以下のJSON形式のMinecraft Mod言語ファイルを英語から日本語に翻訳してください。" +
                          "キー名はそのまま保持し、値のみを翻訳してください。" +
                          "専門用語は適切に翻訳し、アイテム名などは自然な日本語にしてください。" +
                          "JSONフォーマットのみを返してください。説明文は不要です。\n\n" + jsonContent;
            
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("model", "gpt-4o-mini");
            requestBody.addProperty("temperature", 0.3);
            
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
                throw new IOException("ChatGPT API Error: " + responseCode + " - " + readErrorStream(conn));
            }
            
            String response = readInputStream(conn);
            JsonObject jsonResponse = gson.fromJson(response, JsonObject.class);
            String content = jsonResponse.getAsJsonArray("choices")
                .get(0).getAsJsonObject()
                .getAsJsonObject("message")
                .get("content").getAsString();
            
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
     * @return "ChatGPT API"
     */
    @Override
    public String getProviderName() {
        return "ChatGPT API";
    }
}
