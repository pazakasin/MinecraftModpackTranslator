package io.github.pazakasin.minecraft.modpack.translator.service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import io.github.pazakasin.minecraft.modpack.translator.service.callback.ProgressCallback;
import io.github.pazakasin.minecraft.modpack.translator.service.processor.JsonLangFlattener;
import io.github.pazakasin.minecraft.modpack.translator.service.provider.TranslationProvider;

/**
 * 同一原文の重複を排除して翻訳するクラス。
 * 翻訳済みの原文は実行中キャッシュし、別ファイルに同じ原文があれば翻訳せずに再利用する。
 */
public class TranslationDeduplicator {
    /** 翻訳済みMap（原文→訳文）。1回の翻訳実行の中だけで使い、実行開始時にclearする。 */
    private final Map<String, String> cache = new HashMap<String, String>();

    /** プロバイダーとの受け渡し用JSONの変換に使用する。 */
    private final JsonLangFlattener flattener = new JsonLangFlattener();

    /**
     * 翻訳済みMapを空にします（翻訳実行の開始時に呼ぶ）。
     */
    public void clear() {
        cache.clear();
    }

    /**
     * 翻訳済みMapの件数を取得します。
     * @return 登録済みの原文数
     */
    public int getCacheSize() {
        return cache.size();
    }

    /**
     * 重複を排除して翻訳し、全キーの訳文を返します。
     * 翻訳済みMapにない原文だけを、重複をまとめてプロバイダーに渡します。
     * 代表キーには、その原文が最初に出現したキーを使います（LLM系で文脈のヒントになるため）。
     * @param flat 翻訳対象（キー→原文）
     * @param provider 翻訳プロバイダー
     * @param progressCallback 進捗コールバック（null可）
     * @return キー→訳文（訳文が得られなかったキーは含まない）
     * @throws Exception 翻訳エラー（この場合、翻訳済みMapは更新しない）
     */
    public Map<String, String> translate(Map<String, String> flat, TranslationProvider provider,
            ProgressCallback progressCallback) throws Exception {
        Map<String, String> request = new LinkedHashMap<String, String>();
        Set<String> requested = new HashSet<String>();
        for (Map.Entry<String, String> entry : flat.entrySet()) {
            String original = entry.getValue();
            if (!cache.containsKey(original) && requested.add(original)) {
                request.put(entry.getKey(), original);
            }
        }

        if (request.isEmpty()) {
            if (progressCallback != null) {
                progressCallback.onProgress(flat.size(), flat.size());
            }
        } else {
            String translatedJson = provider.translateJsonFile(flattener.toJson(request), progressCallback);
            Map<String, String> result = flattener.readFlat(translatedJson);
            for (Map.Entry<String, String> entry : request.entrySet()) {
                String translated = result.get(entry.getKey());
                if (translated != null) {
                    cache.put(entry.getValue(), translated);
                }
            }
        }

        Map<String, String> translatedMap = new LinkedHashMap<String, String>();
        for (Map.Entry<String, String> entry : flat.entrySet()) {
            String translated = cache.get(entry.getValue());
            if (translated != null) {
                translatedMap.put(entry.getKey(), translated);
            }
        }
        return translatedMap;
    }
}
