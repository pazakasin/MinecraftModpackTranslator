package io.github.pazakasin.minecraft.modpack.translator.service.provider;

import com.google.gson.*;
import io.github.pazakasin.minecraft.modpack.translator.service.callback.ProgressCallback;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * OpenAI ChatGPT APIを使用した翻訳プロバイダー。
 * 並列処理により高速化。
 */
public class ChatGPTTranslationProvider implements TranslationProvider {
    /**
     * デバッグモードを設定します。
     * @param debugMode trueでデバッグモード有効
     */
    @Override
    public void setDebugMode(boolean debugMode) {
        this.debugMode = debugMode;
    }
    
    /** OpenAI APIのAPIキー。 */
    private final String apiKey;
    
    /** カスタムプロンプト。 */
    private final String customPrompt;
    
    /** JSON処理用のGsonインスタンス。 */
    private final Gson gson;
    
    /** バッチサイズ（一度に翻訳するキーの数）。 */
    private final int batchSize;
    
    /** デバッグモード（API呼び出しをスキップ）。 */
    private boolean debugMode = false;
    
    /** 最大同時実行数。 */
    private static final int MAX_CONCURRENT_REQUESTS = 10;
    
    /** デフォルトバッチサイズ。 */
    private static final int DEFAULT_BATCH_SIZE = 100;
    
    /** デフォルトプロンプト。 */
    private static final String DEFAULT_PROMPT =
            "以下のJSON形式のMinecraft言語ファイルを英語から日本語に翻訳してください。" +
            "これはMinecraft ModまたはFTB Questsのテキストです。" +
            "キー名はそのまま保持し、値のみを翻訳してください。" +
            "アイテム名、クエストタイトル、説明文など、文脈に応じて適切に翻訳してください。" +
            "JSONフォーマットのみを返してください。説明文は不要です。\n\n{jsonContent}";
    
    /**
     * ChatGPTTranslationProviderのコンストラクタ。
     * @param apiKey OpenAI APIキー
     */
    public ChatGPTTranslationProvider(String apiKey) {
        this(apiKey, null, DEFAULT_BATCH_SIZE);
    }
    
    /**
     * ChatGPTTranslationProviderのコンストラクタ（カスタムプロンプト付き）。
     * @param apiKey OpenAI APIキー
     * @param customPrompt カスタムプロンプト（nullの場合はデフォルト）
     */
    public ChatGPTTranslationProvider(String apiKey, String customPrompt) {
        this(apiKey, customPrompt, DEFAULT_BATCH_SIZE);
    }
    
    /**
     * ChatGPTTranslationProviderのコンストラクタ（バッチサイズ指定）。
     * @param apiKey OpenAI APIキー
     * @param customPrompt カスタムプロンプト（nullの場合はデフォルト）
     * @param batchSize バッチサイズ
     */
    public ChatGPTTranslationProvider(String apiKey, String customPrompt, int batchSize) {
        this.apiKey = apiKey;
        this.customPrompt = customPrompt;
        this.batchSize = batchSize > 0 ? batchSize : DEFAULT_BATCH_SIZE;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }
    
    /**
     * JSON形式の言語ファイルをChatGPT APIで翻訳します。
     * 並列処理により高速化。
     * @param jsonContent 翻訳元JSONコンテンツ
     * @param progressCallback 進捗コールバック
     * @return 翻訳後のJSONコンテンツ
     * @throws Exception API通信エラー等
     */
    @Override
    public String translateJsonFile(String jsonContent, ProgressCallback progressCallback) throws Exception {
        JsonObject sourceJson = gson.fromJson(jsonContent, JsonObject.class);
        int totalKeys = sourceJson.size();

        if (totalKeys == 0) {
            return jsonContent;
        }

        List<Map<String, String>> batches = splitIntoBatches(sourceJson);
        Map<String, String> translatedMap = new LinkedHashMap<String, String>();
        AtomicInteger processedKeys = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(MAX_CONCURRENT_REQUESTS);
        List<Future<BatchResult>> futures = new ArrayList<Future<BatchResult>>();

        try {
            for (int i = 0; i < batches.size(); i++) {
                final int batchIndex = i;
                final Map<String, String> batch = batches.get(i);

                Future<BatchResult> future = executor.submit(new Callable<BatchResult>() {
                    @Override
                    public BatchResult call() throws Exception {
                        try {
                            Map<String, String> result = translateBatch(batch);
                            int currentProcessed = processedKeys.addAndGet(batch.size());

                            if (progressCallback != null) {
                                progressCallback.onProgress(currentProcessed, totalKeys);
                            }

                            return new BatchResult(batchIndex, result, null);
                        } catch (Exception e) {
                            logBatchError(batchIndex, batches.size(), batch, e);
                            return new BatchResult(batchIndex, null, e);
                        }
                    }
                });

                futures.add(future);
            }

            for (Future<BatchResult> future : futures) {
                BatchResult result = future.get();
                if (result.error != null) {
                    throw new Exception("バッチ " + (result.batchIndex + 1) + "/" + batches.size() +
                            " の翻訳に失敗しました: " + result.error.getMessage(), result.error);
                }
                translatedMap.putAll(result.translations);
            }

        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        JsonObject resultJson = new JsonObject();
        for (Map.Entry<String, String> entry : translatedMap.entrySet()) {
            resultJson.addProperty(entry.getKey(), entry.getValue());
        }

        return gson.toJson(resultJson);
    }
    
    /**
     * JSONをバッチに分割します。
     * @param sourceJson 元のJSONオブジェクト
     * @return バッチのリスト
     */
    private List<Map<String, String>> splitIntoBatches(JsonObject sourceJson) {
        List<Map<String, String>> batches = new ArrayList<Map<String, String>>();
        Map<String, String> currentBatch = new LinkedHashMap<String, String>();

        for (String key : sourceJson.keySet()) {
            currentBatch.put(key, sourceJson.get(key).getAsString());

            if (currentBatch.size() >= batchSize) {
                batches.add(currentBatch);
                currentBatch = new LinkedHashMap<String, String>();
            }
        }

        if (!currentBatch.isEmpty()) {
            batches.add(currentBatch);
        }

        return batches;
    }
    
    /**
     * 1バッチ分のデータを翻訳します。
     * @param batch 翻訳するキーと値のマップ
     * @return 翻訳後のキーと値のマップ
     * @throws Exception API通信エラー等
     */
    private Map<String, String> translateBatch(Map<String, String> batch) throws Exception {
        // デバッグモード時はダミーデータを返す
        if (debugMode) {
        Thread.sleep(500); // API呼び出しをシミュレート
        Map<String, String> result = new LinkedHashMap<String, String>();
        for (Map.Entry<String, String> entry : batch.entrySet()) {
        result.put(entry.getKey(), "[デバッグ] " + entry.getValue());
        }
        return result;
        }
        JsonObject batchJson = new JsonObject();
        for (Map.Entry<String, String> entry : batch.entrySet()) {
            batchJson.addProperty(entry.getKey(), entry.getValue());
        }

        String batchJsonStr = gson.toJson(batchJson);
        String urlStr = "https://api.openai.com/v1/chat/completions";
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        
        try {
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setDoOutput(true);
            
            String prompt;
            if (customPrompt != null && !customPrompt.trim().isEmpty()) {
                prompt = customPrompt.replace("{jsonContent}", batchJsonStr);
            } else {
                prompt = DEFAULT_PROMPT.replace("{jsonContent}", batchJsonStr);
            }
            
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
                String errorMsg = readErrorStream(conn);
                IOException ioException = new IOException("ChatGPT API Error: " + responseCode + " - " + errorMsg);
                logApiError(ioException);
                throw ioException;
            }
            
            String response = readInputStream(conn);
            JsonObject jsonResponse = gson.fromJson(response, JsonObject.class);
            String content = jsonResponse.getAsJsonArray("choices")
                .get(0).getAsJsonObject()
                .getAsJsonObject("message")
                .get("content").getAsString();
            
            content = content.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
            JsonObject translatedJson = gson.fromJson(content, JsonObject.class);
            
            Map<String, String> result = new LinkedHashMap<String, String>();
            for (String key : translatedJson.keySet()) {
                result.put(key, translatedJson.get(key).getAsString());
            }
            
            return result;
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
    
    /**
     * バッチ処理エラーをログ出力します。
     * @param batchIndex バッチインデックス
     * @param totalBatches 総バッチ数
     * @param batch 処理中のバッチデータ
     * @param e 例外オブジェクト
     */
    private void logBatchError(int batchIndex, int totalBatches, Map<String, String> batch, Exception e) {
        System.err.println(String.format("[バッチ %d/%d エラー] %s", 
            batchIndex + 1, totalBatches, e.getMessage()));
        System.err.println("処理中のデータ (最初の3エントリー):");
        int count = 0;
        for (Map.Entry<String, String> entry : batch.entrySet()) {
            if (count >= 3) break;
            System.err.println(String.format("  %s: %s", entry.getKey(), 
                entry.getValue().length() > 100 ? entry.getValue().substring(0, 100) + "..." : entry.getValue()));
            count++;
        }
        if (batch.size() > 3) {
            System.err.println(String.format("  ... 他 %d エントリー", batch.size() - 3));
        }
        e.printStackTrace();
    }
    
    /**
     * API呼び出しエラーをログ出力します。
     * @param e 例外オブジェクト
     */
    private void logApiError(Exception e) {
        System.err.println("[ChatGPT API エラー] " + e.getMessage());
        e.printStackTrace();
    }

    /**
     * バッチ翻訳結果を保持する内部クラス。
     */
    private static class BatchResult {
        final int batchIndex;
        final Map<String, String> translations;
        final Exception error;

        BatchResult(int batchIndex, Map<String, String> translations, Exception error) {
            this.batchIndex = batchIndex;
            this.translations = translations;
            this.error = error;
        }
    }
}
