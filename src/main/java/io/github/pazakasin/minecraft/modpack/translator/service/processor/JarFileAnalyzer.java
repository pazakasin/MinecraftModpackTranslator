package io.github.pazakasin.minecraft.modpack.translator.service.processor;

import java.io.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.*;

/**
 * Mod JARファイルから言語ファイル情報を解析するクラス。
 * assets配下のen_us.json、ja_jp.jsonを検索・抽出。
 */
public class JarFileAnalyzer {
    
    /**
     * JARファイル解析の結果を保持するデータクラス。
     */
    public static class LanguageFileInfo {
        /** Mod ID（assets配下のディレクトリ名）。 */
        public String modId;
        
        /** 言語フォルダのパス（例: "assets/examplemod/lang"）。 */
        public String langFolderPath;
        
        /** en_us.jsonファイルの内容。 */
        public String enUsContent;
        
        /** ja_jp.jsonファイルの内容。 */
        public String jaJpContent;
        
        /** en_us.jsonファイルが存在するか。 */
        public boolean hasEnUs;
        
        /** ja_jp.jsonファイルが存在するか。 */
        public boolean hasJaJp;
    }
    
    /**
     * JARファイルを解析し、言語ファイル情報を抽出します。
     * @param jarFile 解析対象のJARファイル
     * @return 言語ファイル情報
     * @throws IOException JARファイルの読み込み失敗
     */
    public LanguageFileInfo analyze(File jarFile) throws IOException {
        LanguageFileInfo info = new LanguageFileInfo();
        
        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();
            
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String entryName = entry.getName();
                
                if (entryName.contains("assets/") && entryName.endsWith("/lang/en_us.json")) {
                    info.modId = extractModId(entryName);
                    info.langFolderPath = extractLangFolderPath(entryName);
                    info.enUsContent = readEntry(jar, entry);
                    info.hasEnUs = true;
                } else if (entryName.contains("assets/") && entryName.endsWith("/lang/ja_jp.json")) {
                    if (info.modId == null) {
                        info.modId = extractModId(entryName);
                        info.langFolderPath = extractLangFolderPath(entryName);
                    }
                    info.jaJpContent = readEntry(jar, entry);
                    info.hasJaJp = true;
                }
            }
        }
        
        if (info.langFolderPath == null) {
            info.langFolderPath = "見つかりません";
        }
        
        return info;
    }
    
    /** エントリーパスからMod IDを抽出します。 */
    private String extractModId(String path) {
        String[] parts = path.split("/");
        for (int i = 0; i < parts.length - 1; i++) {
            if (parts[i].equals("assets")) {
                return parts[i + 1];
            }
        }
        return "unknown";
    }
    
    /** エントリーパスから言語ファイルのパスを抽出します（ファイル名まで含む）。 */
    private String extractLangFolderPath(String path) {
        // assets/modid/lang/en_us.json 全体を返す
        if (path.startsWith("assets/")) {
            return path;
        }
        return path;
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
