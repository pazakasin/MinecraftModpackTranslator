package io.github.pazakasin.minecraft.modpack.translator.service.quest.util;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.github.pazakasin.minecraft.modpack.translator.service.TranslationService;
import io.github.pazakasin.minecraft.modpack.translator.service.callback.LogCallback;
import io.github.pazakasin.minecraft.modpack.translator.service.callback.ProgressCallback;
import io.github.pazakasin.minecraft.modpack.translator.service.quest.LangFileSNBTExtractor;

/**
 * クエストファイル翻訳の共通処理を提供するヘルパークラス。
 * 翻訳API呼び出し、ログ出力を担当。
 */
public class QuestTranslationHelper {
	/** 翻訳サービス。 */
	private final TranslationService translationService;
	
	/** ログコールバック。 */
	private final LogCallback logger;
	
	/** JSON処理用Gsonインスタンス。 */
	private final Gson gson;
	
	/**
	 * QuestTranslationHelperのコンストラクタ。
	 * @param translationService 翻訳サービス
	 * @param logger ログコールバック
	 */
	public QuestTranslationHelper(TranslationService translationService, LogCallback logger) {
		this.translationService = translationService;
		this.logger = logger;
		this.gson = new GsonBuilder().create();
	}
	
	/**
	 * Lang File用のテキストを翻訳します。
	 * @param texts 翻訳対象テキストリスト
	 * @return キーと翻訳結果のマップ
	 * @throws Exception 翻訳エラー
	 */
	public Map<String, String> translateLangFileTexts(List<LangFileSNBTExtractor.ExtractedText> texts) throws Exception {
		return translateLangFileTexts(texts, null);
	}
	
	/**
	 * Lang File用のテキストを翻訳します。
	 * @param texts 翻訳対象テキストリスト
	 * @param externalProgressCallback 外部からの進捗コールバック
	 * @return キーと翻訳結果のマップ
	 * @throws Exception 翻訳エラー
	 */
	public Map<String, String> translateLangFileTexts(List<LangFileSNBTExtractor.ExtractedText> texts, 
			final ProgressCallback externalProgressCallback) throws Exception {
		JsonObject combined = new JsonObject();
		for (LangFileSNBTExtractor.ExtractedText text : texts) {
			combined.addProperty(text.getKey(), text.getValue());
		}
		
		final int totalEntries = texts.size();
		String translatedJson = translationService.translateJsonFile(
				gson.toJson(combined),
				new ProgressCallback() {
					@Override
					public void onProgress(int current, int total) {
						if (externalProgressCallback != null) {
							externalProgressCallback.onProgress(current, total);
						}
						// ログ出力は削除（状態列で表示）
					}
				});
		
		JsonObject result = gson.fromJson(translatedJson, JsonObject.class);
		Map<String, String> translations = new HashMap<>();
		for (Map.Entry<String, JsonElement> entry : result.entrySet()) {
			translations.put(entry.getKey(), entry.getValue().getAsString());
		}
		
		return translations;
	}
	
	/**
	 * Quest File用のテキストを翻訳します。
	 * @param texts キーと値のマップ
	 * @return キーと翻訳結果のマップ
	 * @throws Exception 翻訳エラー
	 */
	public Map<String, String> translateQuestFileTexts(Map<String, String> texts) throws Exception {
		return translateQuestFileTexts(texts, null);
	}
	
	/**
	 * Quest File用のテキストを翻訳します。
	 * @param texts キーと値のマップ
	 * @param externalProgressCallback 外部からの進捗コールバック
	 * @return キーと翻訳結果のマップ
	 * @throws Exception 翻訳エラー
	 */
	public Map<String, String> translateQuestFileTexts(Map<String, String> texts, 
			final ProgressCallback externalProgressCallback) throws Exception {
		JsonObject combined = new JsonObject();
		for (Map.Entry<String, String> entry : texts.entrySet()) {
			combined.addProperty(entry.getKey(), entry.getValue());
		}
		
		final int totalEntries = texts.size();
		String translatedJson = translationService.translateJsonFile(
				gson.toJson(combined),
				new ProgressCallback() {
					@Override
					public void onProgress(int current, int total) {
						if (externalProgressCallback != null) {
							externalProgressCallback.onProgress(current, total);
						}
						// ログ出力は削除（状態列で表示）
					}
				});
		
		JsonObject result = gson.fromJson(translatedJson, JsonObject.class);
		Map<String, String> translations = new HashMap<>();
		for (Map.Entry<String, JsonElement> entry : result.entrySet()) {
			translations.put(entry.getKey(), entry.getValue().getAsString());
		}
		
		return translations;
	}
	
	/**
	 * ログメッセージを出力します。
	 * @param message ログメッセージ
	 */
	public void log(String message) {
		if (logger != null) {
			logger.onLog("[Quest] " + message);
		}
	}
	
	/**
	 * エラーログを詳細情報付きで出力します。
	 * @param prefix エラーメッセージのプレフィックス
	 * @param file 対象ファイル
	 * @param e 例外
	 */
	public void logError(String prefix, File file, Exception e) {
		log(prefix + ": " + e.getMessage());
		log("ファイル: " + file.getAbsolutePath());
		
		Throwable cause = e.getCause();
		if (cause != null && cause.getMessage() != null) {
			log("原因: " + cause.getMessage());
		}
		
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		e.printStackTrace(pw);
		String stackTrace = sw.toString();
		String[] lines = stackTrace.split("\n");
		
		int linesToShow = Math.min(5, lines.length);
		for (int i = 0; i < linesToShow; i++) {
			log("  " + lines[i].trim());
		}
		
		if (lines.length > linesToShow) {
			log("  ... (残り " + (lines.length - linesToShow) + " 行)");
		}
	}
}
