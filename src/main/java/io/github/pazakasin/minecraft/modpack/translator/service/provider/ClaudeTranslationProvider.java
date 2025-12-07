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
 * 並列処理と出力トークンベースのレート制限により高速かつ安全に翻訳。
 */
public class ClaudeTranslationProvider implements TranslationProvider {
	/**
	 * デバッグモードを設定します。
	 * @param debugMode trueでデバッグモード有効
	 */
	@Override
	public void setDebugMode(boolean debugMode) {
		this.debugMode = debugMode;
	}

	/** Anthropic APIのAPIキー。 */
	private final String apiKey;

	/** カスタムプロンプト。 */
	private final String customPrompt;

	/** JSON処理用のGsonインスタンス。 */
	private final Gson gson;

	/** バッチサイズ（一度に翻訳するキーの数）。 */
	private final int batchSize;

	/** 最大同時実行数。 */
	private static final int MAX_CONCURRENT_REQUESTS = 3;

	/** 出力トークン制限（1分間の上限、余裕を持たせて9000に設定）。 */
	private static final int OUTPUT_TOKEN_LIMIT_PER_MINUTE = 9000;

	/** 1回のAPIリクエストで予想される最大出力トークン数。 */
	private static final int ESTIMATED_OUTPUT_TOKENS_PER_REQUEST = 2000;

	/** APIリクエストのmax_tokensパラメータ値。 */
	private static final int MAX_TOKENS_PER_REQUEST = 2500;

	/** 429エラー時の最大リトライ回数。 */
	private static final int MAX_RETRY_ATTEMPTS = 3;

	/** 429エラー時の基本待機時間（ミリ秒）。 */
	private static final long RETRY_BASE_WAIT_MS = 65000;

	/** レート制限用セマフォ。 */
	private final Semaphore rateLimiter;

	/** 出力トークン使用量を記録するリスト（時刻とトークン数のペア）。 */
	private final List<TokenUsage> tokenUsages;

	/** デバッグモード（API呼び出しをスキップ）。 */
	private boolean debugMode = false;

	/** デフォルトバッチサイズ。 */
	private static final int DEFAULT_BATCH_SIZE = 20;

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
		this.rateLimiter = new Semaphore(MAX_CONCURRENT_REQUESTS);
		this.tokenUsages = new ArrayList<>();
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
		Map<String, String> translatedMap = new LinkedHashMap<>();
		AtomicInteger processedKeys = new AtomicInteger(0);

		ExecutorService executor = Executors.newFixedThreadPool(MAX_CONCURRENT_REQUESTS);
		List<Future<BatchResult>> futures = new ArrayList<>();

		try {
			for (int i = 0; i < batches.size(); i++) {
				final int batchIndex = i;
				final Map<String, String> batch = batches.get(i);

				Future<BatchResult> future = executor.submit(new Callable<BatchResult>() {
					@Override
					public BatchResult call() throws Exception {
						try {
							acquireRateLimit();
							BatchTranslationResult batchResult = translateBatchWithRetry(batch, batchIndex, batches.size());
							updateActualTokenUsage(batchResult.actualOutputTokens);
							int currentProcessed = processedKeys.addAndGet(batch.size());

							if (progressCallback != null) {
								progressCallback.onProgress(currentProcessed, totalKeys);
							}

							return new BatchResult(batchIndex, batchResult.translations, null);
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
	 * 出力トークン数ベースのレート制限を適用します。
	 * リクエスト数の制限は行わず、トークン使用量のみで制御します。
	 * @throws InterruptedException スレッド中断
	 */
	private synchronized void acquireRateLimit() throws InterruptedException {
		long now = System.currentTimeMillis();
		final long oneMinuteAgo = now - 60000;

		tokenUsages.removeIf(usage -> usage.timestamp < oneMinuteAgo);

		int currentTokenUsage = 0;
		for (TokenUsage usage : tokenUsages) {
			currentTokenUsage += usage.tokens;
		}

		if (currentTokenUsage + ESTIMATED_OUTPUT_TOKENS_PER_REQUEST > OUTPUT_TOKEN_LIMIT_PER_MINUTE) {
			if (!tokenUsages.isEmpty()) {
				long oldestTokenTime = tokenUsages.get(0).timestamp;
				long waitTime = 60000 - (now - oldestTokenTime) + 100;
				
				if (waitTime > 0) {
					System.out.println(String.format(
							"[レート制限] 待機中... (トークン: %d/%d, 待機時間: %.1f秒)",
							currentTokenUsage, OUTPUT_TOKEN_LIMIT_PER_MINUTE,
							waitTime / 1000.0));
					Thread.sleep(waitTime);
				}
			}

			now = System.currentTimeMillis();
			final long oneMinuteAgoAfterWait = now - 60000;
			tokenUsages.removeIf(usage -> usage.timestamp < oneMinuteAgoAfterWait);
		}

		tokenUsages.add(new TokenUsage(now, ESTIMATED_OUTPUT_TOKENS_PER_REQUEST));
	}

	/**
	 * APIレスポンスから取得した実際のトークン使用量で記録を更新します。
	 * @param actualTokens 実際の出力トークン数
	 */
	private synchronized void updateActualTokenUsage(int actualTokens) {
		if (tokenUsages.isEmpty()) {
			return;
		}
		TokenUsage lastUsage = tokenUsages.get(tokenUsages.size() - 1);
		tokenUsages.set(tokenUsages.size() - 1, new TokenUsage(lastUsage.timestamp, actualTokens));
	}

	/**
	 * 429エラー時に自動リトライを行うバッチ翻訳メソッド。
	 * @param batch 翻訳するキーと値のマップ
	 * @param batchIndex バッチインデックス
	 * @param totalBatches 総バッチ数
	 * @return 翻訳結果（翻訳データと実際のトークン数）
	 * @throws Exception API通信エラー等
	 */
	private BatchTranslationResult translateBatchWithRetry(Map<String, String> batch, int batchIndex, int totalBatches) throws Exception {
		Exception lastException = null;

		for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
			try {
				return translateBatch(batch);
			} catch (IOException e) {
				lastException = e;
				if (e.getMessage().contains("429")) {
					long waitTime = RETRY_BASE_WAIT_MS * attempt;
					System.err.println(String.format(
							"[バッチ %d/%d] 429エラー発生 (試行 %d/%d) - %d秒後にリトライします",
							batchIndex + 1, totalBatches, attempt, MAX_RETRY_ATTEMPTS, waitTime / 1000));
					Thread.sleep(waitTime);
				} else {
					throw e;
				}
			}
		}

		throw new Exception("最大リトライ回数に達しました", lastException);
	}

	/**
	 * JSONをバッチに分割します。
	 * @param sourceJson 元のJSONオブジェクト
	 * @return バッチのリスト
	 */
	private List<Map<String, String>> splitIntoBatches(JsonObject sourceJson) {
		List<Map<String, String>> batches = new ArrayList<>();
		Map<String, String> currentBatch = new LinkedHashMap<>();

		for (String key : sourceJson.keySet()) {
			currentBatch.put(key, sourceJson.get(key).getAsString());

			if (currentBatch.size() >= batchSize) {
				batches.add(currentBatch);
				currentBatch = new LinkedHashMap<>();
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
	 * @return 翻訳結果（翻訳データと実際のトークン数）
	 * @throws Exception API通信エラー等
	 */
	private BatchTranslationResult translateBatch(Map<String, String> batch) throws Exception {
		if (debugMode) {
			Thread.sleep(500);
			Map<String, String> result = new LinkedHashMap<>();
			for (Map.Entry<String, String> entry : batch.entrySet()) {
				result.put(entry.getKey(), "[デバッグ] " + entry.getValue());
			}
			return new BatchTranslationResult(result, 100);
		}
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
			requestBody.addProperty("model", "claude-haiku-4-5");
			requestBody.addProperty("max_tokens", MAX_TOKENS_PER_REQUEST);

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
				IOException ioException = new IOException("Claude API Error: " + responseCode + " - " + errorMsg);
				logApiError(ioException);
				throw ioException;
			}

			String response = readInputStream(conn);
			JsonObject jsonResponse = gson.fromJson(response, JsonObject.class);
			
			int actualOutputTokens = 0;
			if (jsonResponse.has("usage")) {
				JsonObject usage = jsonResponse.getAsJsonObject("usage");
				if (usage.has("output_tokens")) {
					actualOutputTokens = usage.get("output_tokens").getAsInt();
				}
			}
			
			JsonArray contentArray = jsonResponse.getAsJsonArray("content");
			String content = contentArray.get(0).getAsJsonObject()
					.get("text").getAsString();

			content = content.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
			JsonObject translatedJson = gson.fromJson(content, JsonObject.class);

			Map<String, String> result = new LinkedHashMap<>();
			for (String key : translatedJson.keySet()) {
				result.put(key, translatedJson.get(key).getAsString());
			}

			return new BatchTranslationResult(result, actualOutputTokens);
		} finally {
			conn.disconnect();
		}
	}

	/**
	 * HTTPレスポンスを文字列として読み込みます。
	 * @param conn HTTP接続
	 * @return レスポンス文字列
	 * @throws IOException 読み込みエラー
	 */
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

	/**
	 * HTTPエラーレスポンスを文字列として読み込みます。
	 * @param conn HTTP接続
	 * @return エラーレスポンス文字列
	 * @throws IOException 読み込みエラー
	 */
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
			if (count >= 3)
				break;
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
		System.err.println("[Claude API エラー] " + e.getMessage());
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

	/**
	 * トークン使用量を記録する内部クラス。
	 */
	private static class TokenUsage {
		final long timestamp;
		final int tokens;

		TokenUsage(long timestamp, int tokens) {
			this.timestamp = timestamp;
			this.tokens = tokens;
		}
	}

	/**
	 * バッチ翻訳結果と実際のトークン使用量を保持する内部クラス。
	 */
	private static class BatchTranslationResult {
		final Map<String, String> translations;
		final int actualOutputTokens;

		BatchTranslationResult(Map<String, String> translations, int actualOutputTokens) {
			this.translations = translations;
			this.actualOutputTokens = actualOutputTokens;
		}
	}
}
