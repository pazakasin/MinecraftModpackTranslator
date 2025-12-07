package io.github.pazakasin.minecraft.modpack.translator.service.provider;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import io.github.pazakasin.minecraft.modpack.translator.service.callback.ProgressCallback;

/**
 * Anthropic Claude APIを使用した翻訳プロバイダー。
 * 並列処理とレート制限により高速かつ安全に翻訳。
 */
public class ClaudeTranslationProvider implements TranslationProvider {
	/** Anthropic APIのAPIキー。 */
	private final String apiKey;

	/** カスタムプロンプト。 */
	private final String customPrompt;

	/** JSON処理用のGsonインスタンス。 */
	private final Gson gson;

	/** バッチサイズ（一度に翻訳するキーの数）。 */
	private final int batchSize;

	/** 最大同時実行数。 */
	private static final int MAX_CONCURRENT_REQUESTS = 5;

	/** レート制限（40リクエスト/分 = 安全のため余裕を持たせる）。 */
	private static final int RATE_LIMIT_PER_MINUTE = 40;

	/** レート制限用セマフォ。 */
	private final Semaphore rateLimiter;

	/** 最後のリクエスト時刻を記録するリスト。 */
	private final List<Long> requestTimestamps;

	/** デフォルトバッチサイズ。 */
	private static final int DEFAULT_BATCH_SIZE = 100;

	/** デフォルトプロンプト。 */
	private static final String DEFAULT_PROMPT =
			"以下のJSON形式のMinecraft言語ファイルを英語から日本語に翻訳してください。" +
					"これはMinecraft ModまたはFTB Questsのテキストです。" +
					"キー名はそのまま保持し、値のみを翻訳してください。" +
					"アイテム名、クエストタイトル、説明文など、文脈に応じて適切に翻訳してください。" +
					"JSONフォーマットのみを返してください。説明文や追加のテキストは一切不要です。\n\n{jsonContent}";

	/**
	 * ClaudeTranslationProviderのコンストラクタ。
	 * @param apiKey Anthropic APIキー
	 */
	public ClaudeTranslationProvider(String apiKey) {
		this(apiKey, null, DEFAULT_BATCH_SIZE);
	}

	/**
	 * ClaudeTranslationProviderのコンストラクタ（カスタムプロンプト付き）。
	 * @param apiKey Anthropic APIキー
	 * @param customPrompt カスタムプロンプト（nullの場合はデフォルト）
	 */
	public ClaudeTranslationProvider(String apiKey, String customPrompt) {
		this(apiKey, customPrompt, DEFAULT_BATCH_SIZE);
	}

	/**
	 * ClaudeTranslationProviderのコンストラクタ（バッチサイズ指定）。
	 * @param apiKey Anthropic APIキー
	 * @param customPrompt カスタムプロンプト（nullの場合はデフォルト）
	 * @param batchSize バッチサイズ
	 */
	public ClaudeTranslationProvider(String apiKey, String customPrompt, int batchSize) {
		this.apiKey = apiKey;
		this.customPrompt = customPrompt;
		this.batchSize = batchSize > 0 ? batchSize : DEFAULT_BATCH_SIZE;
		this.gson = new GsonBuilder().setPrettyPrinting().create();
		this.rateLimiter = new Semaphore(RATE_LIMIT_PER_MINUTE);
		this.requestTimestamps = new ArrayList<Long>();
	}

	/**
	 * JSON形式の言語ファイルをClaude APIで翻訳します。
	 * 並列処理により高速化、レート制限で安全性を確保。
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
							acquireRateLimit();
							Map<String, String> result = translateBatch(batch);
							int currentProcessed = processedKeys.addAndGet(batch.size());

							if (progressCallback != null) {
								progressCallback.onProgress(currentProcessed, totalKeys);
							}

							return new BatchResult(batchIndex, result, null);
						} catch (Exception e) {
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
	 * レート制限を適用します（40リクエスト/分）。
	 * @throws InterruptedException スレッド中断
	 */
	private synchronized void acquireRateLimit() throws InterruptedException {
		long now = System.currentTimeMillis();
		final long oneMinuteAgo = now - 60000;

		requestTimestamps.removeIf(timestamp -> timestamp < oneMinuteAgo);

		if (requestTimestamps.size() >= RATE_LIMIT_PER_MINUTE) {
			long oldestTimestamp = requestTimestamps.get(0);
			long waitTime = 60000 - (now - oldestTimestamp) + 100;
			if (waitTime > 0) {
				Thread.sleep(waitTime);
			}
			now = System.currentTimeMillis();
			final long oneMinuteAgoAfterWait = now - 60000;
			requestTimestamps.removeIf(timestamp -> timestamp < oneMinuteAgoAfterWait);
		}

		requestTimestamps.add(now);
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
		JsonObject batchJson = new JsonObject();
		for (Map.Entry<String, String> entry : batch.entrySet()) {
			batchJson.addProperty(entry.getKey(), entry.getValue());
		}

		String batchJsonStr = gson.toJson(batchJson);
		String urlStr = "https://api.anthropic.com/v1/messages";
		URL url = new URL(urlStr);
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();

		try {
			conn.setRequestMethod("POST");
			conn.setRequestProperty("Content-Type", "application/json");
			conn.setRequestProperty("x-api-key", apiKey);
			conn.setRequestProperty("anthropic-version", "2023-06-01");
			conn.setDoOutput(true);

			String prompt;
			if (customPrompt != null && !customPrompt.trim().isEmpty()) {
				prompt = customPrompt.replace("{jsonContent}", batchJsonStr);
			} else {
				prompt = DEFAULT_PROMPT.replace("{jsonContent}", batchJsonStr);
			}

			JsonObject requestBody = new JsonObject();
			requestBody.addProperty("model", "claude-sonnet-4-5");
			requestBody.addProperty("max_tokens", 8192);

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
	 * @return "Claude API"
	 */
	@Override
	public String getProviderName() {
		return "Claude API";
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
