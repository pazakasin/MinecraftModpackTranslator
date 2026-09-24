package io.github.pazakasin.minecraft.modpack.translator.service.processor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import io.github.pazakasin.minecraft.modpack.translator.service.callback.LogCallback;

/**
 * Mod JARとnamespaceの対応状況を集計し、影響範囲をログ出力するクラス。
 * 「複数namespaceを持つJAR」と「複数JARで共有されるnamespace」を報告する。
 */
public class NamespaceUsageReport {
    /** JAR名 → 「namespace (文字数)」表示行のリスト（検出順）。 */
    private final Map<String, List<String>> jarToNamespaces = new LinkedHashMap<String, List<String>>();
    
    /** namespace → JAR名のリスト（namespace昇順）。 */
    private final Map<String, List<String>> namespaceToJars = new TreeMap<String, List<String>>();
    
    /**
     * en_us.jsonを持つnamespaceを1件登録します。
     * @param jarName JARファイル名
     * @param namespace namespace
     * @param charCount en_us.jsonの文字数
     */
    public void add(String jarName, String namespace, int charCount) {
        List<String> namespaces = jarToNamespaces.get(jarName);
        if (namespaces == null) {
            namespaces = new ArrayList<String>();
            jarToNamespaces.put(jarName, namespaces);
        }
        namespaces.add(String.format("%s (%d文字)", namespace, charCount));
        
        List<String> jars = namespaceToJars.get(namespace);
        if (jars == null) {
            jars = new ArrayList<String>();
            namespaceToJars.put(namespace, jars);
        }
        jars.add(jarName);
    }
    
    /**
     * 集計結果をログ出力します。
     * @param logger ログコールバック（nullの場合は何もしない）
     */
    public void writeTo(LogCallback logger) {
        if (logger == null) {
            return;
        }
        logger.onLog("");
        logger.onLog("=== namespace検出レポート ===");
        
        int multiNsCount = 0;
        for (List<String> namespaces : jarToNamespaces.values()) {
            if (namespaces.size() > 1) {
                multiNsCount++;
            }
        }
        logger.onLog("[複数namespaceを含むJAR] " + multiNsCount + "件");
        for (Map.Entry<String, List<String>> entry : jarToNamespaces.entrySet()) {
            if (entry.getValue().size() > 1) {
                logger.onLog(String.format("  %s (%d namespace)", entry.getKey(), entry.getValue().size()));
                for (String line : entry.getValue()) {
                    logger.onLog("    - " + line);
                }
            }
        }
        
        int sharedCount = 0;
        for (List<String> jars : namespaceToJars.values()) {
            if (jars.size() > 1) {
                sharedCount++;
            }
        }
        logger.onLog("[複数JARで共有されるnamespace] " + sharedCount + "件（出力時にキー単位でマージ）");
        for (Map.Entry<String, List<String>> entry : namespaceToJars.entrySet()) {
            if (entry.getValue().size() > 1) {
                logger.onLog("  " + entry.getKey() + ": " + String.join(", ", entry.getValue()));
            }
        }
        logger.onLog("");
    }
}
