package io.github.pazakasin.minecraft.modpack.translator.service.processor;

import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

/**
 * JSON言語ファイルを「キー→文字列」の単純な形に展開・復元するクラス。
 * 文字列は翻訳対象、文字列の配列は要素ごとに「キー[番号]」として翻訳対象、
 * それ以外（数値・真偽値・null・入れ子のオブジェクト等）は翻訳対象外として元の値を保持する。
 */
public class JsonLangFlattener {
    /** 出力用Gson（翻訳プロバイダーと同じ整形設定。null値も元どおり出力）。 */
    private final Gson gson;

    /**
     * JsonLangFlattenerのコンストラクタ。
     */
    public JsonLangFlattener() {
        this.gson = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
    }

    /**
     * JSON文字列を解析してオブジェクトとして返します（末尾カンマは除去して解析）。
     * @param jsonContent JSON文字列
     * @return 解析結果のJsonObject
     * @throws IllegalArgumentException ルートがオブジェクトでない、または解析不可の場合
     */
    public JsonObject parse(String jsonContent) {
        if (jsonContent == null) {
            throw new IllegalArgumentException("JSONがnullです");
        }
        JsonElement element;
        try {
            element = JsonParser.parseString(removeTrailingCommas(jsonContent));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("JSON解析エラー: " + e.getMessage(), e);
        }
        if (element == null || !element.isJsonObject()) {
            throw new IllegalArgumentException("JSONのルートがオブジェクトではありません");
        }
        return element.getAsJsonObject();
    }

    /**
     * すべての値が文字列のみで構成された単純なJSONかを判定します。
     * @param root 判定対象
     * @return 値がすべて文字列の場合true
     */
    public boolean isSimple(JsonObject root) {
        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            if (!isString(entry.getValue())) {
                return false;
            }
        }
        return true;
    }

    /**
     * 翻訳対象の文字列を「キー→文字列」の形に展開します。
     * 配列要素の展開キーが既存のキーと衝突する場合、その要素は翻訳対象外とします。
     * @param root 展開元
     * @return 展開結果（元の順序を保持）
     */
    public Map<String, String> flatten(JsonObject root) {
        Map<String, String> result = new LinkedHashMap<String, String>();
        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            String key = entry.getKey();
            JsonElement value = entry.getValue();
            if (isString(value)) {
                result.put(key, value.getAsString());
            } else if (value != null && value.isJsonArray()) {
                JsonArray array = value.getAsJsonArray();
                for (int i = 0; i < array.size(); i++) {
                    String elementKey = toElementKey(key, i);
                    if (isString(array.get(i)) && !root.has(elementKey)) {
                        result.put(elementKey, array.get(i).getAsString());
                    }
                }
            }
        }
        return result;
    }

    /**
     * 翻訳結果を元の構造に書き戻したJsonObjectを作成します。
     * 翻訳結果に含まれないキーは原文の値を使用します。
     * @param root 元の構造
     * @param translated 翻訳結果（flattenと同じキー）
     * @return 翻訳結果を反映した新しいJsonObject
     */
    public JsonObject unflatten(JsonObject root, Map<String, String> translated) {
        JsonObject result = new JsonObject();
        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            String key = entry.getKey();
            JsonElement value = entry.getValue();
            if (isString(value) && translated.containsKey(key)) {
                result.addProperty(key, translated.get(key));
            } else if (value != null && value.isJsonArray()) {
                result.add(key, unflattenArray(root, key, value.getAsJsonArray(), translated));
            } else {
                result.add(key, value == null ? null : value.deepCopy());
            }
        }
        return result;
    }

    /**
     * 展開結果をJSON文字列に変換します。
     * @param flat 展開結果
     * @return JSON文字列
     */
    public String toJson(Map<String, String> flat) {
        JsonObject object = new JsonObject();
        for (Map.Entry<String, String> entry : flat.entrySet()) {
            object.addProperty(entry.getKey(), entry.getValue());
        }
        return gson.toJson(object);
    }

    /**
     * JsonObjectをJSON文字列に変換します。
     * @param object 変換対象
     * @return JSON文字列
     */
    public String toJson(JsonObject object) {
        return gson.toJson(object);
    }

    /**
     * 翻訳結果のJSON文字列を「キー→文字列」の形で読み込みます（文字列以外の値は無視）。
     * @param jsonContent 翻訳結果JSON
     * @return キーと文字列のMap
     */
    public Map<String, String> readFlat(String jsonContent) {
        JsonObject object = parse(jsonContent);
        Map<String, String> result = new LinkedHashMap<String, String>();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            if (isString(entry.getValue())) {
                result.put(entry.getKey(), entry.getValue().getAsString());
            }
        }
        return result;
    }

    /**
     * 配列を翻訳結果で書き戻した新しい配列を作成します。
     * @param root 元の構造（キー衝突判定用）
     * @param key 配列のキー
     * @param array 元の配列
     * @param translated 翻訳結果
     * @return 新しい配列
     */
    private JsonArray unflattenArray(JsonObject root, String key, JsonArray array,
            Map<String, String> translated) {
        JsonArray newArray = new JsonArray();
        for (int i = 0; i < array.size(); i++) {
            String elementKey = toElementKey(key, i);
            JsonElement element = array.get(i);
            if (isString(element) && !root.has(elementKey) && translated.containsKey(elementKey)) {
                newArray.add(new JsonPrimitive(translated.get(elementKey)));
            } else {
                newArray.add(element == null ? null : element.deepCopy());
            }
        }
        return newArray;
    }

    /**
     * 配列要素の展開キーを作成します。
     * @param key 配列のキー
     * @param index 要素番号
     * @return 展開キー（例: command.help[1]）
     */
    private String toElementKey(String key, int index) {
        return key + "[" + index + "]";
    }

    /**
     * 値が文字列かを判定します。
     * @param value 判定対象
     * @return 文字列の場合true
     */
    private boolean isString(JsonElement value) {
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString();
    }

    /**
     * 閉じ括弧直前の不要な末尾カンマを除去します（一部Modの誤ったJSONへの救済措置）。
     * @param jsonContent 元のJSON文字列
     * @return 末尾カンマを除去したJSON文字列
     */
    private String removeTrailingCommas(String jsonContent) {
        return jsonContent.replaceAll(",(\\s*[}\\]])", "$1");
    }
}
