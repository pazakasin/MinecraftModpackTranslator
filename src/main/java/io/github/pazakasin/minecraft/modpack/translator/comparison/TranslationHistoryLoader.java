package io.github.pazakasin.minecraft.modpack.translator.comparison;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.github.pazakasin.minecraft.modpack.translator.model.TranslatableFile;
import io.github.pazakasin.minecraft.modpack.translator.service.callback.LogCallback;
import io.github.pazakasin.minecraft.modpack.translator.service.callback.ProgressUpdateCallback;

/**
 * loadフォルダから翻訳履歴を読み込むクラス。
 * loadフォルダの構造に合わせて各Analyzerを直接利用。
 */
public class TranslationHistoryLoader {
    /** ログコールバック */
    private final LogCallback logger;
    
    /** 進捗コールバック */
    private final ProgressUpdateCallback progressUpdater;
    
    /**
     * コンストラクタ。
     * @param logger ログコールバック
     * @param progressUpdater 進捗コールバック
     */
    public TranslationHistoryLoader(LogCallback logger, ProgressUpdateCallback progressUpdater) {
        this.logger = logger;
        this.progressUpdater = progressUpdater;
    }
    
    /**
     * Questファイル本体（*.snbt）を再帰的に読込。
     * 
     * @param folder 探索対象フォルダ
     * @param entries 結果を格納するリスト
     * @return 読み込んだファイル数
     * @throws Exception 読込エラー
     */
    private int loadQuestSNBTFiles(File folder, List<TranslationHistoryEntry> entries) 
            throws Exception {
        File[] files = folder.listFiles();
        if (files == null) {
            return 0;
        }
        
        int count = 0;
        for (File file : files) {
            if (file.isDirectory()) {
                // langフォルダはスキップ（既に処理済み）
                if (!"lang".equals(file.getName())) {
                    count += loadQuestSNBTFiles(file, entries);
                }
            } else if (file.getName().endsWith(".snbt")) {
                // SNBTファイルの読込はJSON形式と同じく読めるか試す
                // （SNBTの詳細解析はTranslatableFileで行われるため、ここでは簡易的に読み込む）
                try {
                    Map<String, String> translations = loadJsonFile(file);
                    if (!translations.isEmpty()) {
                        entries.add(new TranslationHistoryEntry(file, translations));
                        count++;
                        if (logger != null) {
                            logger.onLog("[デバッグ] Quest SNBTファイル読込: " + file.getName() + " (" + translations.size() + "キー)");
                        }
                    }
                } catch (Exception e) {
                    // SNBTの解析に失敗した場合はスキップ
                    if (logger != null) {
                        logger.onLog("[デバッグ] SNBT解析スキップ: " + file.getName());
                    }
                }
            }
        }
        
        return count;
    }
    
    /**
     * loadフォルダから翻訳履歴を読み込む。
     * 
     * @param loadFolder loadフォルダのパス
     * @return 翻訳履歴エントリのリスト
     * @throws TranslationHistoryException 読込エラー
     */
    public List<TranslationHistoryEntry> load(File loadFolder) throws TranslationHistoryException {
        List<TranslationHistoryEntry> entries = new ArrayList<TranslationHistoryEntry>();
        
        try {
            // resourcepacks/MyJPpack配下のMod言語ファイルを読込
            loadModLanguageFiles(loadFolder, entries);
            
            // kubejs配下のKubeJS言語ファイルを読込
            loadKubeJSLanguageFiles(loadFolder, entries);
            
            // configs配下のQuestファイルを読込
            loadQuestFiles(loadFolder, entries);
            
            if (logger != null) {
                logger.onLog("[隠し機能] " + entries.size() + " 件の翻訳ファイルを読み込みました");
            }
            
        } catch (Exception e) {
            throw new TranslationHistoryException("翻訳履歴の読込中にエラーが発生しました", e);
        }
        
        return entries;
    }
    
    /**
     * Mod言語ファイル（resourcepacks/MyJPpack/assets/mod_id/lang/ja_jp.json）を読込。
     * 
     * @param loadFolder loadフォルダ
     * @param entries 結果を格納するリスト
     * @throws Exception 読込エラー
     */
    private void loadModLanguageFiles(File loadFolder, List<TranslationHistoryEntry> entries) 
            throws Exception {
        File resourcepacksFolder = new File(loadFolder, "resourcepacks/MyJPpack/assets");
        if (!resourcepacksFolder.exists() || !resourcepacksFolder.isDirectory()) {
            if (logger != null) {
                logger.onLog("[デバッグ] resourcepacks/MyJPpack/assets が見つかりません");
            }
            return;
        }
        
        // assets配下の各mod_idフォルダを探索
        File[] modFolders = resourcepacksFolder.listFiles();
        if (modFolders == null) {
            return;
        }
        
        int count = 0;
        for (File modFolder : modFolders) {
            if (!modFolder.isDirectory()) {
                continue;
            }
            
            File langFolder = new File(modFolder, "lang");
            if (!langFolder.exists() || !langFolder.isDirectory()) {
                continue;
            }
            
            File jaJpFile = new File(langFolder, "ja_jp.json");
            if (jaJpFile.exists() && jaJpFile.isFile()) {
                Map<String, String> translations = loadJsonFile(jaJpFile);
                if (!translations.isEmpty()) {
                    entries.add(new TranslationHistoryEntry(jaJpFile, translations));
                    count++;
                    if (logger != null) {
                        logger.onLog("[デバッグ] Mod言語ファイル読込: " + modFolder.getName() + " (" + translations.size() + "キー)");
                    }
                }
            }
        }
        
        if (logger != null && count > 0) {
            logger.onLog("[隠し機能] Mod言語ファイル: " + count + "件");
        }
    }
    
    /**
     * KubeJS言語ファイル（kubejs/assets/id/lang/ja_jp.json）を読込。
     * 
     * @param loadFolder loadフォルダ
     * @param entries 結果を格納するリスト
     * @throws Exception 読込エラー
     */
    private void loadKubeJSLanguageFiles(File loadFolder, List<TranslationHistoryEntry> entries) 
            throws Exception {
        File kubejsAssetsFolder = new File(loadFolder, "kubejs/assets");
        if (!kubejsAssetsFolder.exists() || !kubejsAssetsFolder.isDirectory()) {
            if (logger != null) {
                logger.onLog("[デバッグ] kubejs/assets が見つかりません");
            }
            return;
        }
        
        if (logger != null) {
            logger.onLog("[デバッグ] kubejs/assets を発見: " + kubejsAssetsFolder.getAbsolutePath());
        }
        
        // assets配下の各idフォルダを探索
        File[] idFolders = kubejsAssetsFolder.listFiles();
        if (idFolders == null) {
            return;
        }
        
        int count = 0;
        for (File idFolder : idFolders) {
            if (!idFolder.isDirectory()) {
                continue;
            }
            
            File langFolder = new File(idFolder, "lang");
            if (!langFolder.exists() || !langFolder.isDirectory()) {
                continue;
            }
            
            File jaJpFile = new File(langFolder, "ja_jp.json");
            if (jaJpFile.exists() && jaJpFile.isFile()) {
                Map<String, String> translations = loadJsonFile(jaJpFile);
                if (!translations.isEmpty()) {
                    entries.add(new TranslationHistoryEntry(jaJpFile, translations));
                    count++;
                    if (logger != null) {
                        logger.onLog("[デバッグ] KubeJS言語ファイル読込: " + idFolder.getName() + " (" + translations.size() + "キー)");
                    }
                }
            }
        }
        
        if (logger != null && count > 0) {
            logger.onLog("[隠し機能] KubeJS言語ファイル: " + count + "件");
        }
    }
    
    /**
     * Questファイル（configs/ftbquests/quests/）を読込。
     * 
     * @param loadFolder loadフォルダ
     * @param entries 結果を格納するリスト
     * @throws Exception 読込エラー
     */
    private void loadQuestFiles(File loadFolder, List<TranslationHistoryEntry> entries) 
            throws Exception {
        File questsFolder = new File(loadFolder, "configs/ftbquests/quests");
        if (!questsFolder.exists() || !questsFolder.isDirectory()) {
            if (logger != null) {
                logger.onLog("[デバッグ] configs/ftbquests/quests が見つかりません");
            }
            return;
        }
        
        if (logger != null) {
            logger.onLog("[デバッグ] configs/ftbquests/quests を発見: " + questsFolder.getAbsolutePath());
        }
        
        // Quest言語ファイル（lang/ja_jp.json）を読込
        File langFolder = new File(questsFolder, "lang");
        int count = 0;
        
        if (langFolder.exists() && langFolder.isDirectory()) {
            File jaJpFile = new File(langFolder, "ja_jp.json");
            if (jaJpFile.exists() && jaJpFile.isFile()) {
                Map<String, String> translations = loadJsonFile(jaJpFile);
                if (!translations.isEmpty()) {
                    entries.add(new TranslationHistoryEntry(jaJpFile, translations));
                    count++;
                    if (logger != null) {
                        logger.onLog("[デバッグ] Quest言語ファイル読込: ja_jp.json (" + translations.size() + "キー)");
                    }
                }
            }
        }
        
        // Questファイル本体（*.snbt）を再帰的に読込
        int snbtCount = loadQuestSNBTFiles(questsFolder, entries);
        count += snbtCount;
        
        if (logger != null && count > 0) {
            logger.onLog("[隠し機能] Questファイル: " + count + "件");
        }
    }
    
    /**
     * JSONファイルを読み込んでMapに変換。
     * 重複キーを許容（最後の値を採用）。
     * 
     * @param file JSONファイル
     * @return キーと値のMap（挿入順保持）
     * @throws Exception 読込エラー
     */
    private Map<String, String> loadJsonFile(File file) throws Exception {
        String content = new String(java.nio.file.Files.readAllBytes(file.toPath()), 
                                    java.nio.charset.StandardCharsets.UTF_8);
        Map<String, String> result = new LinkedHashMap<String, String>();
        
        try {
            JsonElement element = JsonParser.parseString(content);
            if (element.isJsonObject()) {
                JsonObject jsonObject = element.getAsJsonObject();
                for (String key : jsonObject.keySet()) {
                    JsonElement value = jsonObject.get(key);
                    if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                        result.put(key, value.getAsString());
                    }
                }
            }
        } catch (Exception e) {
            if (logger != null) {
                logger.onLog("[警告] JSON解析エラー: " + file.getName());
            }
        }
        
        return result;
    }
    
    /**
     * TranslatableFileから翻訳データを抽出。
     * @param file TranslatableFile
     * @return 翻訳データマップ
     */
    private Map<String, String> extractTranslations(TranslatableFile file) {
        Map<String, String> result = new LinkedHashMap<String, String>();
        
        String content = file.getFileContent();
        if (content == null || content.trim().isEmpty()) {
            return result;
        }
        
        try {
            // JSON形式の場合
            if (content.trim().startsWith("{")) {
                JsonElement element = JsonParser.parseString(content);
                if (element.isJsonObject()) {
                    JsonObject jsonObject = element.getAsJsonObject();
                    for (String key : jsonObject.keySet()) {
                        JsonElement value = jsonObject.get(key);
                        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                            result.put(key, value.getAsString());
                        }
                    }
                }
            }
            // SNBTやその他の形式は、既にTranslatableFileの解析で処理済み
            // TODO: SNBT形式の場合の処理を追加
            
        } catch (Exception e) {
            if (logger != null) {
                logger.onLog("[警告] データ抽出エラー: " + file.getSourceFilePath());
            }
        }
        
        return result;
    }
}
