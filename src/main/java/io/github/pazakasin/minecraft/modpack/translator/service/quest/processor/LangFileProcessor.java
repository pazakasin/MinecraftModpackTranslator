package io.github.pazakasin.minecraft.modpack.translator.service.quest.processor;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.pazakasin.minecraft.modpack.translator.model.QuestFileResult;
import io.github.pazakasin.minecraft.modpack.translator.service.quest.LangFileSNBTExtractor;
import io.github.pazakasin.minecraft.modpack.translator.service.quest.SNBTParser;
import io.github.pazakasin.minecraft.modpack.translator.service.quest.util.QuestTranslationHelper;
import io.github.pazakasin.minecraft.modpack.translator.service.callback.LogCallback;
import net.querz.nbt.tag.Tag;

/**
 * Lang File（en_us.snbt）の処理を担当するクラス。
 * NBTパース、テキスト抽出、翻訳適用を実行。
 */
public class LangFileProcessor {
	/** SNBTパーサー。 */
	private final SNBTParser parser;
	
	/** Lang File抽出器。 */
	private final LangFileSNBTExtractor extractor;
	
	/** 翻訳ヘルパー。 */
	private final QuestTranslationHelper helper;
	
	/** ログコールバック。 */
	private final LogCallback logger;
	
	/** 出力先ディレクトリ。 */
	private final File outputDir;
	
	/**
	 * LangFileProcessorのコンストラクタ。
	 * @param parser SNBTパーサー
	 * @param extractor Lang File抽出器
	 * @param helper 翻訳ヘルパー
	 * @param logger ログコールバック
	 * @param outputDir 出力先ディレクトリ
	 */
	public LangFileProcessor(SNBTParser parser, LangFileSNBTExtractor extractor,
			QuestTranslationHelper helper, LogCallback logger, File outputDir) {
		this.parser = parser;
		this.extractor = extractor;
		this.helper = helper;
		this.logger = logger;
		this.outputDir = outputDir;
	}
	
	/**
	 * Lang Fileを処理します。既存のja_jp.snbtがある場合はコピーします。
	 * @param langFile 元のLang File
	 * @param existingJaJpFile 既存のja_jp.snbtファイル（なければnull）
	 * @return 処理結果
	 */
	public QuestFileResult process(File langFile, File existingJaJpFile) {
		return process(langFile, existingJaJpFile, 0, null);
	}
	
	/**
	 * Lang Fileを処理します。既存のja_jp.snbtがある場合はコピーします。
	 * @param langFile 元のLang File
	 * @param existingJaJpFile 既存のja_jp.snbtファイル（なければnull）
	 * @param progressCallback 進捗コールバック
	 * @return 処理結果
	 */
	public QuestFileResult process(File langFile, File existingJaJpFile, 
			io.github.pazakasin.minecraft.modpack.translator.service.callback.ProgressCallback progressCallback) {
		return process(langFile, existingJaJpFile, 0, progressCallback);
	}
	
	/**
	 * Lang Fileを処理します。既存のja_jp.snbtがある場合はコピーします。
	 * @param langFile 元のLang File
	 * @param existingJaJpFile 既存のja_jp.snbtファイル（なければnull）
	 * @param charCount 文字数（ログ出力用、0の場合は内部で計算）
	 * @param progressCallback 進捗コールバック
	 * @return 処理結果
	 */
	public QuestFileResult process(File langFile, File existingJaJpFile, int charCount,
			io.github.pazakasin.minecraft.modpack.translator.service.callback.ProgressCallback progressCallback) {
		try {
			File outputBase = outputDir.getParentFile();
			File outputLangDir = new File(outputBase, "config/ftbquests/quests/lang");
			outputLangDir.mkdirs();
			File outputFile = new File(outputLangDir, "ja_jp.snbt");
			
			if (existingJaJpFile != null && existingJaJpFile.exists()) {
				Files.copy(existingJaJpFile.toPath(), outputFile.toPath(),
						java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				
				return QuestFileResult.createLangFileResult(
						langFile, outputFile, false, true, 0);
			}
			
			Tag<?> rootTag = parser.parse(langFile);
			List<LangFileSNBTExtractor.ExtractedText> texts = extractor.extract(rootTag);
			
			if (texts.isEmpty()) {
				return QuestFileResult.createLangFileResult(
						langFile, null, false, false, 0);
			}
			
			if (charCount == 0) {
				charCount = countCharacters(texts);
			}
			
			Map<String, String> translations = helper.translateLangFileTexts(texts, progressCallback);
			
			applyTranslationsToLangFile(langFile, outputFile, translations);
			
			return QuestFileResult.createLangFileResult(
					langFile, outputFile, true, true, charCount);
			
		} catch (Exception e) {
			helper.logError("Lang File翻訳エラー", langFile, e);
			return QuestFileResult.createLangFileResult(
					langFile, null, true, false, 0);
		}
	}
	
	/**
	 * Lang File用の翻訳適用メソッド（正規表現ベース）。
	 * @param sourceFile 元のSNBTファイル
	 * @param targetFile 出力先ファイル
	 * @param translations キーと翻訳のマップ
	 * @throws IOException ファイルI/Oエラー
	 */
	private void applyTranslationsToLangFile(File sourceFile, File targetFile,
			Map<String, String> translations) throws IOException {
		String content = Files.readString(sourceFile.toPath(), StandardCharsets.UTF_8);
		
		for (Map.Entry<String, String> entry : translations.entrySet()) {
			String key = escapeRegex(entry.getKey());
			String translatedValue = entry.getValue();
			
			Pattern stringPattern = Pattern.compile(
					"(" + key + ":\\s*)\"([^\"\\\\]*(\\\\.[^\"\\\\]*)*)\"");
			Matcher stringMatcher = stringPattern.matcher(content);
			
			if (stringMatcher.find()) {
				String replacement = stringMatcher.group(1) + "\"" +
						escapeSnbtString(translatedValue) + "\"";
				content = stringMatcher.replaceFirst(Matcher.quoteReplacement(replacement));
			} else {
				Pattern arrayPattern = Pattern.compile(
						"(" + key + ":\\s*\\[)([^\\]]*)(\\])",
						Pattern.DOTALL);
				Matcher arrayMatcher = arrayPattern.matcher(content);
				
				if (arrayMatcher.find()) {
					String prefix = arrayMatcher.group(1);
					String originalArray = arrayMatcher.group(2);
					String suffix = arrayMatcher.group(3);
					
					String indent = extractIndent(originalArray);
					String[] lines = translatedValue.split("\n");
					
					StringBuilder newArray = new StringBuilder();
					for (int i = 0; i < lines.length; i++) {
						newArray.append("\n").append(indent);
						newArray.append("\"").append(escapeSnbtString(lines[i])).append("\"");
					}
					if (lines.length > 0) {
						String baseIndent = indent.length() > 0 && indent.charAt(indent.length() - 1) == '\t'
								? indent.substring(0, indent.length() - 1)
								: indent;
						newArray.append("\n").append(baseIndent);
					}
					
					String replacement = prefix + newArray.toString() + suffix;
					content = arrayMatcher.replaceFirst(Matcher.quoteReplacement(replacement));
				}
			}
		}
		
		Files.writeString(targetFile.toPath(), content, StandardCharsets.UTF_8);
	}
	
	/**
	 * 正規表現で使用する特殊文字をエスケープします。
	 * @param text エスケープ対象テキスト
	 * @return エスケープ済みテキスト
	 */
	private String escapeRegex(String text) {
		return text.replaceAll("([\\\\\\[\\]{}()*+?.^$|])", "\\\\$1");
	}
	
	/**
	 * SNBT文字列値をエスケープします。
	 * @param text エスケープ対象テキスト
	 * @return エスケープ済みテキスト
	 */
	private String escapeSnbtString(String text) {
		return text.replace("\\", "\\\\")
				.replace("\"", "\\\"");
	}
	
	/**
	 * 配列内容からインデントを抽出します。
	 * @param arrayContent 配列の内容
	 * @return インデント文字列
	 */
	private String extractIndent(String arrayContent) {
		int firstNewline = arrayContent.indexOf('\n');
		if (firstNewline == -1) {
			return "\t";
		}
		
		int start = firstNewline + 1;
		int end = start;
		while (end < arrayContent.length() &&
				(arrayContent.charAt(end) == ' ' || arrayContent.charAt(end) == '\t')) {
			end++;
		}
		
		return end > start ? arrayContent.substring(start, end) : "\t";
	}
	
	/**
	 * テキストリストの合計文字数をカウントします。
	 * @param texts テキストリスト
	 * @return 合計文字数
	 */
	private int countCharacters(List<LangFileSNBTExtractor.ExtractedText> texts) {
		int count = 0;
		for (LangFileSNBTExtractor.ExtractedText text : texts) {
			count += text.getValue().length();
		}
		return count;
	}
}
