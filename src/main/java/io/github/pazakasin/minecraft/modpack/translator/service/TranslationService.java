package io.github.pazakasin.minecraft.modpack.translator.service;

import com.google.gson.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.Map.Entry;

/**
 * 翻訳サービスクラス（改善版 - 全プロバイダーで一括処理対応）
 */
public class TranslationService {
    private String apiKey;
    private TranslationProvider provider;
    private final Gson gson;
    
    // 一括処理の最大サイズ
    private static final int GOOGLE_BATCH_SIZE = 128;
    private static final int DEEPL_BATCH_SIZE = 50;
    
    public enum TranslationProvider {
        GOOGLE("Google Translation API"),
        DEEPL("DeepL API"),
        CHATGPT("ChatGPT API"),
        CLAUDE("Claude API");
        
        private final String displayName;
        
        TranslationProvider(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    public TranslationService() {
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.provider = TranslationProvider.GOOGLE;
    }
    
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }
    
    public void setProvider(TranslationProvider provider) {
        this.provider = provider;
    }
    
    public TranslationProvider getProvider() {
        return provider;
    }
    
    /**
     * JSON形式の言語ファイルを翻訳（一括処理版）
     */
    public String translateJsonFile(String jsonContent) throws Exception {
        return translateJsonFile(jsonContent, null);
    }
    
    /**
     * JSON形式の言語ファイルを翻訳（進捗コールバック付き）
     */
    public String translateJsonFile(String jsonContent, ProgressCallback progressCallback) throws Exception {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalStateException("APIキーが設定されていません");
        }
        
        switch (provider) {
            case GOOGLE:
                return translateJsonFileWithGoogleBatch(jsonContent, progressCallback);
            case DEEPL:
                return translateJsonFileWithDeepLBatch(jsonContent, progressCallback);
            case CHATGPT:
                return translateJsonFileWithChatGPT(jsonContent, progressCallback);
            case CLAUDE:
                return translateJsonFileWithClaude(jsonContent, progressCallback);
            default:
                throw new IllegalStateException("Unknown provider: " + provider);
        }
    }
    
    /**
     * 進捗コールバックインターフェース
     */
    public interface ProgressCallback {
        void onProgress(int current, int total);
    }
    
    /**
     * Google Translation APIで翻訳（バッチ処理版）
     */
    private String translateJsonFileWithGoogleBatch(String jsonContent, ProgressCallback progressCallback) throws Exception {
        JsonObject original = gson.fromJson(jsonContent, JsonObject.class);
        JsonObject translated = new JsonObject();
        
        List<String> keys = new ArrayList<String>();
        List<String> values = new ArrayList<String>();
        
        for (Entry<String, JsonElement> entry : original.entrySet()) {
            keys.add(entry.getKey());
            values.add(entry.getValue().getAsString());
        }
        
        int totalEntries = values.size();
        
        List<String> translatedValues = new ArrayList<String>();
        for (int i = 0; i < values.size(); i += GOOGLE_BATCH_SIZE) {
            int end = Math.min(i + GOOGLE_BATCH_SIZE, values.size());
            List<String> batch = values.subList(i, end);
            
            if (progressCallback != null) {
                progressCallback.onProgress(i, totalEntries);
            }
            
            List<String> batchResults = translateBatchWithGoogle(batch);
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
    
    private List<String> translateBatchWithGoogle(List<String> texts) throws Exception {
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
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
                    StringBuilder errorResponse = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        errorResponse.append(line);
                    }
                    throw new IOException("Google API Error: " + responseCode + " - " + errorResponse.toString());
                }
            }
            
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line);
                }
                
                JsonObject jsonResponse = gson.fromJson(response.toString(), JsonObject.class);
                JsonArray translations = jsonResponse.getAsJsonObject("data")
                    .getAsJsonArray("translations");
                
                List<String> results = new ArrayList<String>();
                for (int i = 0; i < translations.size(); i++) {
                    results.add(translations.get(i).getAsJsonObject()
                        .get("translatedText").getAsString());
                }
                return results;
            }
        } finally {
            conn.disconnect();
        }
    }
    
    /**
     * DeepL APIで翻訳（バッチ処理版）
     */
    private String translateJsonFileWithDeepLBatch(String jsonContent, ProgressCallback progressCallback) throws Exception {
        JsonObject original = gson.fromJson(jsonContent, JsonObject.class);
        JsonObject translated = new JsonObject();
        
        List<String> keys = new ArrayList<String>();
        List<String> values = new ArrayList<String>();
        
        for (Entry<String, JsonElement> entry : original.entrySet()) {
            keys.add(entry.getKey());
            values.add(entry.getValue().getAsString());
        }
        
        int totalEntries = values.size();
        
        List<String> translatedValues = new ArrayList<String>();
        for (int i = 0; i < values.size(); i += DEEPL_BATCH_SIZE) {
            int end = Math.min(i + DEEPL_BATCH_SIZE, values.size());
            List<String> batch = values.subList(i, end);
            
            if (progressCallback != null) {
                progressCallback.onProgress(i, totalEntries);
            }
            
            List<String> batchResults = translateBatchWithDeepL(batch);
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
    
    private List<String> translateBatchWithDeepL(List<String> texts) throws Exception {
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
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
                    StringBuilder errorResponse = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        errorResponse.append(line);
                    }
                    throw new IOException("DeepL API Error: " + responseCode + " - " + errorResponse.toString());
                }
            }
            
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line);
                }
                
                JsonObject jsonResponse = gson.fromJson(response.toString(), JsonObject.class);
                JsonArray translations = jsonResponse.getAsJsonArray("translations");
                
                List<String> results = new ArrayList<String>();
                for (int i = 0; i < translations.size(); i++) {
                    results.add(translations.get(i).getAsJsonObject()
                        .get("text").getAsString());
                }
                return results;
            }
        } finally {
            conn.disconnect();
        }
    }
    
    /**
     * ChatGPT APIで翻訳（一括処理版）
     */
    private String translateJsonFileWithChatGPT(String jsonContent, ProgressCallback progressCallback) throws Exception {
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
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
                    StringBuilder errorResponse = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        errorResponse.append(line);
                    }
                    throw new IOException("ChatGPT API Error: " + responseCode + " - " + errorResponse.toString());
                }
            }
            
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line);
                }
                
                JsonObject jsonResponse = gson.fromJson(response.toString(), JsonObject.class);
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
            }
        } finally {
            conn.disconnect();
        }
    }
    
    /**
     * Claude APIで翻訳（一括処理版）
     */
    private String translateJsonFileWithClaude(String jsonContent, ProgressCallback progressCallback) throws Exception {
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
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
                    StringBuilder errorResponse = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        errorResponse.append(line);
                    }
                    throw new IOException("Claude API Error: " + responseCode + " - " + errorResponse.toString());
                }
            }
            
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line);
                }
                
                JsonObject jsonResponse = gson.fromJson(response.toString(), JsonObject.class);
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
            }
        } finally {
            conn.disconnect();
        }
    }
}