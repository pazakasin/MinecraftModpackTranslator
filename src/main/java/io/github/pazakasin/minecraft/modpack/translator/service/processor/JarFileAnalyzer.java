package io.github.pazakasin.minecraft.modpack.translator.service.processor;

import java.io.*;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mod JARファイルから言語ファイル情報を解析するクラス。
 * assets/&lt;namespace&gt;/lang 配下のen_us.json、ja_jp.jsonをnamespace単位で検索・抽出。
 */
public class JarFileAnalyzer {
    
    /** 言語ファイルのエントリパスパターン（JARルート直下のassetsのみ対象）。 */
    private static final Pattern LANG_ENTRY_PATTERN =
            Pattern.compile("^assets/([^/]+)/lang/(en_us|ja_jp)\\.json$");
    
    /**
     * JARファイルを解析し、namespaceごとの言語ファイル情報を抽出します。
     * en_us.jsonとja_jp.jsonは同じnamespace同士で対応付けます。
     * @param jarFile 解析対象のJARファイル
     * @return namespace名の昇順に並んだ言語ファイル情報（言語ファイルがなければ空リスト）
     * @throws IOException JARファイルの読み込み失敗
     */
    public List<LanguageFileInfo> analyze(File jarFile) throws IOException {
        Map<String, LanguageFileInfo> infoMap = new TreeMap<String, LanguageFileInfo>();
        
        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();
            
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                Matcher matcher = LANG_ENTRY_PATTERN.matcher(entry.getName());
                if (!matcher.matches()) {
                    continue;
                }
                
                String namespace = matcher.group(1);
                LanguageFileInfo info = infoMap.get(namespace);
                if (info == null) {
                    info = new LanguageFileInfo();
                    info.modId = namespace;
                    infoMap.put(namespace, info);
                }
                
                if ("en_us".equals(matcher.group(2))) {
                    info.langFolderPath = entry.getName();
                    info.enUsContent = readEntry(jar, entry);
                    info.hasEnUs = true;
                } else {
                    if (info.langFolderPath == null) {
                        info.langFolderPath = entry.getName();
                    }
                    info.jaJpContent = readEntry(jar, entry);
                    info.hasJaJp = true;
                }
            }
        }
        
        return new ArrayList<LanguageFileInfo>(infoMap.values());
    }
    
    /**
     * en_us.jsonを持つnamespaceの数を数えます。
     * @param infos 言語ファイル情報のリスト
     * @return en_us.jsonを持つnamespace数
     */
    public static int countEnUs(List<LanguageFileInfo> infos) {
        int count = 0;
        for (LanguageFileInfo info : infos) {
            if (info.enUsContent != null) {
                count++;
            }
        }
        return count;
    }
    
    /**
     * 表示用のMod名を生成します。en_usを持つnamespaceが複数ある場合のみnamespaceを付記します。
     * @param baseName JAR名（拡張子なし）
     * @param namespace namespace
     * @param enUsCount 同一JAR内でen_usを持つnamespace数
     * @return 表示用Mod名
     */
    public static String buildDisplayName(String baseName, String namespace, int enUsCount) {
        if (enUsCount > 1) {
            return baseName + " [" + namespace + "]";
        }
        return baseName;
    }
    
    /** JARエントリーの内容をUTF-8文字列として読み込みます。 */
    private String readEntry(JarFile jar, JarEntry entry) throws IOException {
        try (InputStream is = jar.getInputStream(entry);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        }
    }
}
