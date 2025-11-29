package io.github.pazakasin.minecraft.modpack.translator.service;

import io.github.pazakasin.minecraft.modpack.translator.model.ModProcessingResult;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class ModPackProcessor {
    private final String inputPath;
    private final TranslationService translationService;
    private final Consumer<String> logger;
    private final Consumer<Integer> progressUpdater;
    private final File outputDir;
    
    public ModPackProcessor(String inputPath, TranslationService translationService, 
                           Consumer<String> logger, Consumer<Integer> progressUpdater) {
        this.inputPath = inputPath;
        this.translationService = translationService;
        this.logger = logger;
        this.progressUpdater = progressUpdater;
        this.outputDir = new File("output/MyJPpack");
    }
    
    public List<ModProcessingResult> process() throws Exception {
        File modsDir = new File(inputPath, "mods");
        File[] jarFiles = modsDir.listFiles(new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return name.endsWith(".jar");
            }
        });
        
        List<ModProcessingResult> results = new ArrayList<ModProcessingResult>();
        
        if (jarFiles == null || jarFiles.length == 0) {
            log("modsフォルダ内にJARファイルが見つかりません。");
            return results;
        }
        
        log("=== Mod一覧 ===");
        log("検出されたMod数: " + jarFiles.length);
        for (File jarFile : jarFiles) {
            log("  - " + jarFile.getName());
        }
        log("");
        
        log("=== 翻訳処理開始 ===");
        int processed = 0;
        int skipped = 0;
        int translated = 0;
        int totalMods = jarFiles.length;
        
        for (int modIndex = 0; modIndex < jarFiles.length; modIndex++) {
            File jarFile = jarFiles[modIndex];
            int currentModNum = modIndex + 1;
            
            try {
                ModProcessingResult result = processModJar(jarFile, currentModNum, totalMods);
                results.add(result);
                processed++;
                
                if (result.hasJaJp) {
                    log(String.format("[%d/%d][既存] %s - 日本語ファイルが存在します", 
                        currentModNum, totalMods, jarFile.getName()));
                    skipped++;
                } else if (result.translated) {
                    if (result.translationSuccess) {
                        log(String.format("[%d/%d][翻訳] %s - 翻訳完了 (%d文字)", 
                            currentModNum, totalMods, jarFile.getName(), result.characterCount));
                        translated++;
                    } else {
                        log(String.format("[%d/%d][失敗] %s - 翻訳に失敗しました", 
                            currentModNum, totalMods, jarFile.getName()));
                    }
                } else {
                    log(String.format("[%d/%d][スキップ] %s - 英語ファイルが見つかりません", 
                        currentModNum, totalMods, jarFile.getName()));
                    skipped++;
                }
            } catch (Exception e) {
                log(String.format("[%d/%d][エラー] %s: %s", 
                    currentModNum, totalMods, jarFile.getName(), e.getMessage()));
                
                ModProcessingResult errorResult = new ModProcessingResult();
                errorResult.modName = jarFile.getName().replace(".jar", "");
                errorResult.langFolderPath = "エラー";
                errorResult.translationSuccess = false;
                results.add(errorResult);
            }
        }
        
        log("");
        log("=== 処理完了 ===");
        log("処理したMod数: " + processed);
        log("翻訳したMod数: " + translated);
        log("スキップしたMod数: " + skipped);
        log("出力先: " + outputDir.getAbsolutePath());
        
        return results;
    }
    
    private ModProcessingResult processModJar(File jarFile, int currentModNum, int totalMods) throws Exception {
        ModProcessingResult result = new ModProcessingResult();
        result.modName = jarFile.getName().replace(".jar", "");
        
        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();
            String modId = null;
            String enUsContent = null;
            String jaJpContent = null;
            String langFolderPath = null;
            
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String entryName = entry.getName();
                
                if (entryName.contains("assets/") && entryName.endsWith("/lang/en_us.json")) {
                    modId = extractModId(entryName);
                    langFolderPath = extractLangFolderPath(entryName);
                    enUsContent = readEntry(jar, entry);
                    result.hasEnUs = true;
                } else if (entryName.contains("assets/") && entryName.endsWith("/lang/ja_jp.json")) {
                    if (modId == null) {
                        modId = extractModId(entryName);
                        langFolderPath = extractLangFolderPath(entryName);
                    }
                    jaJpContent = readEntry(jar, entry);
                    result.hasJaJp = true;
                }
            }
            
            result.langFolderPath = langFolderPath != null ? langFolderPath : "見つかりません";
            
            if (modId == null || enUsContent == null) {
                result.translationSuccess = false;
                return result;
            }
            
            File langDir = new File(outputDir, "assets/" + modId + "/lang");
            langDir.mkdirs();
            
            Files.write(new File(langDir, "en_us.json").toPath(), 
                       enUsContent.getBytes("UTF-8"));
            
            result.characterCount = countCharacters(enUsContent);
            
            if (jaJpContent != null) {
                Files.write(new File(langDir, "ja_jp.json").toPath(), 
                           jaJpContent.getBytes("UTF-8"));
                result.translationSuccess = true;
            } else {
                try {
                    final int finalCurrentModNum = currentModNum;
                    final int finalTotalMods = totalMods;
                    
                    String translatedContent = translationService.translateJsonFile(
                        enUsContent, 
                        new TranslationService.ProgressCallback() {
                            public void onProgress(int current, int total) {
                                logProgress(String.format("[%d/%d] 翻訳中: %d/%d エントリー", 
                                    finalCurrentModNum, finalTotalMods, current, total));
                            }
                        }
                    );
                    Files.write(new File(langDir, "ja_jp.json").toPath(), 
                               translatedContent.getBytes("UTF-8"));
                    result.translated = true;
                    result.translationSuccess = true;
                    
                    logProgress(" ");
                } catch (Exception e) {
                    result.translated = true;
                    result.translationSuccess = false;
                    logProgress(" ");
                    throw e;
                }
            }
        }
        
        return result;
    }
    
    private String extractModId(String path) {
        String[] parts = path.split("/");
        for (int i = 0; i < parts.length - 1; i++) {
            if (parts[i].equals("assets")) {
                return parts[i + 1];
            }
        }
        return "unknown";
    }
    
    private String extractLangFolderPath(String path) {
        int langIndex = path.indexOf("/lang/");
        if (langIndex != -1) {
            return path.substring(0, langIndex + 5);
        }
        return path;
    }
    
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
    
    private int countCharacters(String jsonContent) {
        int count = 0;
        try {
            boolean inValue = false;
            boolean escaping = false;
            
            for (int i = 0; i < jsonContent.length(); i++) {
                char c = jsonContent.charAt(i);
                
                if (escaping) {
                    escaping = false;
                    if (inValue) count++;
                    continue;
                }
                
                if (c == '\\') {
                    escaping = true;
                    continue;
                }
                
                if (c == ':' && !inValue) {
                    for (int j = i + 1; j < jsonContent.length(); j++) {
                        if (jsonContent.charAt(j) == '"') {
                            inValue = true;
                            i = j;
                            break;
                        } else if (jsonContent.charAt(j) != ' ' && jsonContent.charAt(j) != '\n') {
                            break;
                        }
                    }
                } else if (c == '"' && inValue) {
                    inValue = false;
                } else if (inValue && c != '\n' && c != '\r') {
                    count++;
                }
            }
        } catch (Exception e) {
            return 0;
        }
        
        return count;
    }
    
    private void log(String message) {
        if (logger != null) {
            logger.accept(message);
        }
    }
    
    private void logProgress(String message) {
        if (logger != null) {
            logger.accept("PROGRESS:" + message);
        }
    }
}