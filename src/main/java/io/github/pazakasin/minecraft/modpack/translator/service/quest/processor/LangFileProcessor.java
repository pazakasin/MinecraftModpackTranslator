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
import io.github.pazakasin.minecraft.modpack.translator.service.processor.TranslationChangeDetector;
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
	
	/** 翻訳結果の変更有無判定（未変更のファイルは出力しない）。 */
	private final TranslationChangeDetector changeDetector = new TranslationChangeDetector();
	
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
	 * Lang Fileを処理します。既存のja_jp.snbtがある場合や翻訳で変更がない場合は出力しません。
	 * @param langFile 元のLang File
	 * @param existingJaJpFile 既存のja_jp.snbtファイル（なければnull）
	 * @return 処理結果
	 */
	public QuestFileResult process(File langFile, File existingJaJpFile) {
		return process(langFile, existingJaJpFile, 0, null);
	}
	
	/**
	 * Lang Fileを処理します。既存のja_jp.snbtがある場合や翻訳で変更がない場合は出力しません。
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
	 * Lang Fileを処理します。既存のja_jp.snbtがある場合や翻訳で変更がない場合は出力しません。
	 * @param langFile 元のLang File
	 * @param existingJaJpFile 既存のja_jp.snbtファイル（なければnull）
	 * @param charCount 文字数（ログ出力用、0の場合は内部で計算）
	 * @param progressCallback 進捗コールバック
	 * @return 処理結果
	 */
	public QuestFileResult process(File langFile, File existingJaJpFile, int charCount,
			io.github.pazakasin.minecraft.modpack.translator.service.callback.ProgressCallback progressCallback) {
		try {
			if (existingJaJpFile != null && existingJaJpFile.exists()) {
				// 未修正のファイルは出力しない（既存の日本語ファイルはModPack側にあるため不要）
				return QuestFileResult.createLangFileResult(
						langFile, null, false, true, 0);
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
			
			String originalContent = Files.readString(langFile.toPath(), StandardCharsets.UTF_8);
			String translatedContent = buildTranslatedLangContent(originalContent, translations);
			
			if (changeDetector.isTextUnchanged(originalContent, translatedContent)) {
				QuestFileResult unchangedResult = QuestFileResult.createLangFileResult(
						langFile, null, true, true, charCount);
				unchangedResult.unchanged = true;
				return unchangedResult;
			}
			
			File outputLangDir = new File(outputDir.getParentFile(), "config/ftbquests/quests/lang");
			outputLangDir.mkdirs();
			File outputFile = new File(outputLangDir, "ja_jp.snbt");
			Files.writeString(outputFile.toPath(), translatedContent, StandardCharsets.UTF_8);
			
			return QuestFileResult.createLangFileResult(
					langFile, outputFile, true, true, charCount);
			
		} catch (Exception e) {
			helper.logError("Lang File翻訳エラー", langFile, e);
			return QuestFileResult.createLangFileResult(
					langFile, null, true, false, 0);
		}
	}
	
	/**
	 * Lang File用の翻訳適用メソッド（正規表現ベース）。ファイルへの書き込みは行いません。
	 * @param content 元のSNBT内容
	 * @param translations キーと翻訳のマップ
	 * @return 翻訳適用後の内容
	 */
	private String buildTranslatedLangContent(String content, Map<String, String> translations) {
		
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
				int arrayStart = findArrayStart(content, entry.getKey());
				if (arrayStart != -1) {
					int arrayEnd = findMatchingBracket(content, arrayStart);
					if (arrayEnd != -1) {
						String beforeArray = content.substring(0, arrayStart);
						String afterArray = content.substring(arrayEnd + 1);
						String originalArray = content.substring(arrayStart + 1, arrayEnd);
						
						String indent = extractIndent(originalArray);
						String[] lines = translatedValue.split("\n");
						
						StringBuilder newArray = new StringBuilder();
						newArray.append("[");
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
						newArray.append("]");
						
						content = beforeArray + newArray.toString() + afterArray;
					}
				}
			}
		}
		
		return content;
	}
	
	/**
	 * 指定されたキーの配列開始位置を見つけます。
	 * @param content コンテンツ
	 * @param key キー名
	 * @return 配列の'['の位置、見つからない場合は-1
	 */
	private int findArrayStart(String content, String key) {
		Pattern pattern = Pattern.compile(escapeRegex(key) + ":\\s*\\[");
		Matcher matcher = pattern.matcher(content);
		if (matcher.find()) {
			return matcher.end() - 1;
		}
		return -1;
	}
	
	/**
	 * 配列の開始位置から対応する閉じ括弧を見つけます。
	 * 括弧のネストと文字列内の括弧を考慮します。
	 * @param content コンテンツ
	 * @param start 開始位置（'['の位置）
	 * @return 対応する']'の位置、見つからない場合は-1
	 */
	private int findMatchingBracket(String content, int start) {
		int depth = 1;
		boolean inString = false;
		boolean escaped = false;
		
		for (int i = start + 1; i < content.length(); i++) {
			char c = content.charAt(i);
			
			if (escaped) {
				escaped = false;
				continue;
			}
			
			if (c == '\\') {
				escaped = true;
				continue;
			}
			
			if (c == '"') {
				inString = !inString;
				continue;
			}
			
			if (inString) {
				continue;
			}
			
			if (c == '[') {
				depth++;
			} else if (c == ']') {
				depth--;
				if (depth == 0) {
					return i;
				}
			}
		}
		
		return -1;
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
