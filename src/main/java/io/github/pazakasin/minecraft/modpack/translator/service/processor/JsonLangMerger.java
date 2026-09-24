package io.github.pazakasin.minecraft.modpack.translator.service.processor;

import java.io.StringReader;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;

/**
 * 同一namespaceの言語JSONをキー単位でマージするユーティリティ。
 * Minecraftが複数の言語ファイルを合成する挙動に合わせ、重複キーは後勝ちとする。
 */
public class JsonLangMerger {
    /** 出力用Gson（整形出力・HTMLエスケープなし）。 */
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    
    /**
     * 2つの言語JSONをマージします。
     * @param baseJson 既存のJSON文字列
     * @param addJson 追加するJSON文字列（重複キーはこちらを優先）
     * @param duplicateKeys 値が異なる重複キーの格納先（nullの場合は記録しない）
     * @return マージ後のJSON文字列
     * @throws IllegalArgumentException いずれかがJSONオブジェクトとして解析できない場合
     */
    public static String merge(String baseJson, String addJson, List<String> duplicateKeys) {
        JsonObject base = parseObject(baseJson);
        JsonObject add = parseObject(addJson);
        mergeInto(base, add, "", duplicateKeys);
        return GSON.toJson(base);
    }
    
    /**
     * addの内容をbaseへ再帰的にマージします（双方がオブジェクトの場合のみ再帰）。
     * @param base マージ先
     * @param add マージ元
     * @param prefix 重複キー記録用のキー接頭辞
     * @param duplicateKeys 重複キーの格納先（null可）
     */
    private static void mergeInto(JsonObject base, JsonObject add, String prefix, List<String> duplicateKeys) {
        for (Map.Entry<String, JsonElement> entry : add.entrySet()) {
            String key = entry.getKey();
            JsonElement addValue = entry.getValue();
            JsonElement baseValue = base.get(key);
            
            if (baseValue != null && baseValue.isJsonObject() && addValue.isJsonObject()) {
                mergeInto(baseValue.getAsJsonObject(), addValue.getAsJsonObject(),
                        prefix + key + ".", duplicateKeys);
                continue;
            }
            if (baseValue != null && !baseValue.equals(addValue) && duplicateKeys != null) {
                duplicateKeys.add(prefix + key);
            }
            base.add(key, addValue);
        }
    }
    
    /**
     * JSON文字列をJsonObjectとして寛容モードで解析します。
     * @param json JSON文字列
     * @return 解析結果
     * @throws IllegalArgumentException JSONオブジェクトでない場合
     */
    private static JsonObject parseObject(String json) {
        try {
            JsonReader reader = new JsonReader(new StringReader(json));
            reader.setLenient(true);
            JsonElement element = JsonParser.parseReader(reader);
            if (!element.isJsonObject()) {
                throw new IllegalArgumentException("JSONオブジェクトではありません");
            }
            return element.getAsJsonObject();
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("JSON解析に失敗しました: " + e.getMessage(), e);
        }
    }
}
