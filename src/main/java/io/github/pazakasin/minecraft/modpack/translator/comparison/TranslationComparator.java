package io.github.pazakasin.minecraft.modpack.translator.comparison;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import io.github.pazakasin.minecraft.modpack.translator.service.quest.SNBTParser;
import io.github.pazakasin.minecraft.modpack.translator.service.quest.LangFileSNBTExtractor;
import net.querz.nbt.tag.Tag;

/**
 * 翻訳前後のJSON/SNBTファイルを比較するクラス
 * SNBTファイルは翻訳対象のキーのみを表示
 */
public class TranslationComparator {
    /** Gsonインスタンス */
    private final Gson gson;
    
    /** SNBTパーサー */
    private final SNBTParser snbtParser;
    
    /** SNBT言語ファイルエクストラクター */
    private final LangFileSNBTExtractor snbtExtractor;
    
    /**
     * コンストラクタ
     */
    public TranslationComparator() {
        this.gson = new Gson();
        this.snbtParser = new SNBTParser();
        this.snbtExtractor = new LangFileSNBTExtractor();
    }
    
    /**
     * 2つのファイルを比較する（JSONまたはSNBT形式）
     * SNBTファイルは翻訳対象のキーのみを比較
     * 
     * @param originalFile 翻訳前のファイル
     * @param translatedFile 翻訳後のファイル
     * @return 比較結果のリスト
     * @throws IOException ファイル読み込みエラー
     * @throws Exception 解析エラー
     */
    public List<ComparisonResult> compare(File originalFile, File translatedFile) 
            throws IOException, Exception {
        
        if (!originalFile.exists()) {
            throw new IOException("翻訳前のファイルが見つかりません: " + originalFile.getAbsolutePath());
        }
        
        if (!translatedFile.exists()) {
            throw new IOException("翻訳後のファイルが見つかりません: " + translatedFile.getAbsolutePath());
        }
        
        // ファイル拡張子で判定
        boolean isSNBT = originalFile.getName().endsWith(".snbt");
        
        Map<String, String> originalMap;
        Map<String, String> translatedMap;
        
        if (isSNBT) {
            // SNBTファイルの種類を判定（Quest言語ファイル vs Questファイル本体）
            boolean isQuestLangFile = originalFile.getParent().contains("lang");
            
            if (isQuestLangFile) {
                // Quest言語ファイル: LangFileSNBTExtractorを使用
                originalMap = loadSNBTLangFile(originalFile);
                translatedMap = loadSNBTLangFile(translatedFile);
            } else {
                // Questファイル本体: extractTranslatableTextsを使用
                originalMap = snbtParser.extractTranslatableTexts(originalFile);
                translatedMap = snbtParser.extractTranslatableTexts(translatedFile);
            }
        } else {
            originalMap = loadJsonFile(originalFile);
            translatedMap = loadJsonFile(translatedFile);
        }
        
        return compareMap(originalMap, translatedMap);
    }
    
    /**
     * JSONファイルを読み込んでMapに変換
     * 
     * @param file JSONファイル
     * @return キーと値のMap（挿入順保持）
     * @throws IOException ファイル読み込みエラー
     * @throws JsonSyntaxException JSON解析エラー
     */
    private Map<String, String> loadJsonFile(File file) throws IOException, JsonSyntaxException {
        String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        // LinkedHashMapを使用して順序を保持
        return gson.fromJson(content, new TypeToken<LinkedHashMap<String, String>>(){}.getType());
    }
    
    /**
     * Quest言語ファイルから翻訳対象のキーのみを抽出
     * 
     * @param file Quest言語ファイル
     * @return キーと値のMap（翻訳対象のみ、挿入順保持）
     * @throws IOException ファイル読み込みエラー
     * @throws Exception SNBT解析エラー
     */
    private Map<String, String> loadSNBTLangFile(File file) throws IOException, Exception {
        Map<String, String> result = new LinkedHashMap<String, String>();
        
        try {
            Tag<?> rootTag = snbtParser.parse(file);
            List<LangFileSNBTExtractor.ExtractedText> texts = snbtExtractor.extract(rootTag);
            
            for (LangFileSNBTExtractor.ExtractedText text : texts) {
                result.put(text.getKey(), text.getValue());
            }
        } catch (Exception e) {
            throw new Exception("SNBT言語ファイルの解析に失敗しました: " + e.getMessage(), e);
        }
        
        return result;
    }
    
    /**
     * 2つのMapを比較
     * 
     * @param originalMap 翻訳前のMap
     * @param translatedMap 翻訳後のMap
     * @return 比較結果のリスト
     */
    private List<ComparisonResult> compareMap(Map<String, String> originalMap, 
                                              Map<String, String> translatedMap) {
        List<ComparisonResult> results = new ArrayList<ComparisonResult>();
        
        // 原文の順序を保持し、翻訳先のみにあるキーを後に追加
        Set<String> processedKeys = new HashSet<String>();
        
        // まず原文のキーを順番に処理
        for (String key : originalMap.keySet()) {
            String originalValue = originalMap.get(key);
            String translatedValue = translatedMap.get(key);
            
            ComparisonResult.ChangeType changeType;
            if (translatedValue == null) {
                changeType = ComparisonResult.ChangeType.REMOVED;
            } else if (originalValue.equals(translatedValue)) {
                changeType = ComparisonResult.ChangeType.UNCHANGED;
            } else {
                changeType = ComparisonResult.ChangeType.MODIFIED;
            }
            
            results.add(new ComparisonResult(
                key,
                originalValue,
                translatedValue != null ? translatedValue : "",
                changeType
            ));
            
            processedKeys.add(key);
        }
        
        // 翻訳先のみにあるキー（追加されたキー）を処理
        for (String key : translatedMap.keySet()) {
            if (!processedKeys.contains(key)) {
                results.add(new ComparisonResult(
                    key,
                    "",
                    translatedMap.get(key),
                    ComparisonResult.ChangeType.ADDED
                ));
            }
        }
        
        return results;
    }
}
