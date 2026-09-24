package io.github.pazakasin.minecraft.modpack.translator.service.analyzer;

import io.github.pazakasin.minecraft.modpack.translator.model.TranslatableFile;
import io.github.pazakasin.minecraft.modpack.translator.model.FileType;
import io.github.pazakasin.minecraft.modpack.translator.service.callback.LogCallback;
import io.github.pazakasin.minecraft.modpack.translator.service.processor.JsonLangMerger;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * workフォルダへのファイルエクスポートを担当するクラス。
 * 解析したファイルをworkフォルダに出力。
 */
public class WorkFolderExporter {
    /** ログコールバック。 */
    private final LogCallback logger;
    
    /** export()1回の中で書き込み済みのMod言語ファイル（絶対パス）。同一namespaceの2回目以降はマージする。 */
    private final Set<String> exportedModLangPaths = new HashSet<String>();
    
    /**
     * WorkFolderExporterのコンストラクタ。
     * @param logger ログコールバック
     */
    public WorkFolderExporter(LogCallback logger) {
        this.logger = logger;
    }
    
    /**
     * 解析したファイルの元ファイルを work/ にエクスポートします。
     * @param files 翻訳対象ファイルのリスト
     * @throws Exception ファイルI/Oエラー
     */
    public void export(List<TranslatableFile> files) throws Exception {
        log("");
        log("=== 元ファイルエクスポート開始 ===");
        
        File workDir = new File("work");
        workDir.mkdirs();
        
        int exportCount = 0;
        exportedModLangPaths.clear();
        
        for (TranslatableFile file : files) {
            try {
                switch (file.getFileType()) {
                    case MOD_LANG_FILE:
                        exportModLangFile(file, workDir);
                        exportCount++;
                        break;
                    case KUBEJS_LANG_FILE:
                        exportKubeJSLangFile(file, workDir);
                        exportCount++;
                        break;
                    case OPENLOADER_LANG_FILE:
                        exportOpenLoaderLangFile(file, workDir);
                        exportCount++;
                        break;
                    case CONFIG_LANG_FILE:
                        // 相対パス構造を保つ点はOpenLoaderと同じ。非対応形式はエクスポートしない
                        if (file.isTranslatable()) {
                            exportOpenLoaderLangFile(file, workDir);
                            exportCount++;
                        }
                        break;
                    case QUEST_LANG_FILE:
                    case QUEST_FILE:
                        exportQuestFile(file, workDir);
                        exportCount++;
                        break;
                }
            } catch (Exception e) {
                log("エクスポートエラー: " + file.getModName() + " - " + e.getMessage());
            }
        }
        
        log("エクスポート完了: " + exportCount + "ファイル");
    }
    
    /**
     * Mod言語ファイルをエクスポートします。
     * @param file 翻訳対象ファイル
     * @param workDir workディレクトリ
     * @throws Exception ファイルI/Oエラー
     */
    private void exportModLangFile(TranslatableFile file, File workDir) throws Exception {
        File outputDir = new File(workDir, "resourcepacks/MyJPpack/assets/" + file.getFileId() + "/lang");
        outputDir.mkdirs();
        
        File enUsFile = new File(outputDir, "en_us.json");
        writeOrMergeModLang(enUsFile, file.getFileContent(), file.getFileId());
        
        if (file.isHasExistingJaJp() && file.getExistingJaJpContent() != null) {
            File jaJpFile = new File(outputDir, "ja_jp.json");
            writeOrMergeModLang(jaJpFile, file.getExistingJaJpContent(), file.getFileId());
            file.setWorkFilePath(jaJpFile.getAbsolutePath());
        } else {
            file.setWorkFilePath(enUsFile.getAbsolutePath());
        }
    }
    
    /**
     * Mod言語ファイルを書き込みます。同一export内で既に書き込んだファイルならキー単位でマージします。
     * @param target 出力先ファイル
     * @param content 書き込む内容
     * @param namespace namespace（ログ用）
     * @throws Exception ファイルI/Oエラー
     */
    private void writeOrMergeModLang(File target, String content, String namespace) throws Exception {
        String key = target.getAbsolutePath();
        String output = content;
        if (exportedModLangPaths.contains(key) && target.isFile()) {
            String existing = new String(Files.readAllBytes(target.toPath()), "UTF-8");
            try {
                output = JsonLangMerger.merge(existing, content, null);
                log("[マージ] work: namespace '" + namespace + "' の " + target.getName() + " を複数JARからマージしました");
            } catch (IllegalArgumentException e) {
                log("[警告] work: namespace '" + namespace + "' のマージに失敗したため上書きします: " + e.getMessage());
            }
        }
        Files.write(target.toPath(), output.getBytes("UTF-8"));
        exportedModLangPaths.add(key);
    }
    
    /**
     * KubeJS言語ファイルをエクスポートします。
     * @param file 翻訳対象ファイル
     * @param workDir workディレクトリ
     * @throws Exception ファイルI/Oエラー
     */
    private void exportKubeJSLangFile(TranslatableFile file, File workDir) throws Exception {
        File outputDir = new File(workDir, "kubejs/assets/" + file.getFileId() + "/lang");
        outputDir.mkdirs();
        
        File enUsFile = new File(outputDir, "en_us.json");
        Files.write(enUsFile.toPath(), file.getFileContent().getBytes("UTF-8"));
        
        if (file.isHasExistingJaJp() && file.getExistingJaJpContent() != null) {
            File jaJpFile = new File(outputDir, "ja_jp.json");
            Files.write(jaJpFile.toPath(), file.getExistingJaJpContent().getBytes("UTF-8"));
            file.setWorkFilePath(jaJpFile.getAbsolutePath());
        } else {
            file.setWorkFilePath(enUsFile.getAbsolutePath());
        }
    }
    
    /**
     * OpenLoader言語ファイル（およびConfig（その他）の言語ファイル）をエクスポートします。
     * 入力ファイルの相対パス構造（config/openloader/resources/...等）をworkフォルダ配下に
     * そのまま再現します（OpenLoaderProcessorの出力パス決定ロジックと同じ考え方）。
     * @param file 翻訳対象ファイル
     * @param workDir workディレクトリ
     * @throws Exception ファイルI/Oエラー
     */
    private void exportOpenLoaderLangFile(TranslatableFile file, File workDir) throws Exception {
        File relativeSourceFile = new File(file.getLangFolderPath());
        File outputDir = new File(workDir, relativeSourceFile.getParent());
        outputDir.mkdirs();

        File enUsFile = new File(outputDir, "en_us.json");
        Files.write(enUsFile.toPath(), file.getFileContent().getBytes("UTF-8"));

        if (file.isHasExistingJaJp() && file.getExistingJaJpContent() != null) {
            File jaJpFile = new File(outputDir, "ja_jp.json");
            Files.write(jaJpFile.toPath(), file.getExistingJaJpContent().getBytes("UTF-8"));
            file.setWorkFilePath(jaJpFile.getAbsolutePath());
        } else {
            file.setWorkFilePath(enUsFile.getAbsolutePath());
        }
    }

    /**
     * クエストファイルをエクスポートします。
     * @param file 翻訳対象ファイル
     * @param workDir workディレクトリ
     * @throws Exception ファイルI/Oエラー
     */
    private void exportQuestFile(TranslatableFile file, File workDir) throws Exception {
        File sourceFile = new File(file.getSourceFilePath());
        
        String relativePath = extractQuestRelativePath(file.getSourceFilePath());
        
        File outputFile = new File(workDir, "config/ftbquests/quests/" + relativePath);
        outputFile.getParentFile().mkdirs();
        
        Files.copy(sourceFile.toPath(), outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        
        if (file.getFileType() == FileType.QUEST_LANG_FILE &&
            file.isHasExistingJaJp() && file.getExistingJaJpContent() != null) {
            
            File jaJpOutputFile = new File(outputFile.getParent(), "ja_jp.snbt");
            Files.write(jaJpOutputFile.toPath(), file.getExistingJaJpContent().getBytes("UTF-8"));
            file.setWorkFilePath(jaJpOutputFile.getAbsolutePath());
        } else {
            file.setWorkFilePath(outputFile.getAbsolutePath());
        }
    }
    
    /**
     * クエストファイルの相対パスを抽出します。
     * @param filePath ファイルパス
     * @return 相対パス
     */
    private String extractQuestRelativePath(String filePath) {
        String path = filePath.replace("\\", "/");
        
        int questsIndex = path.indexOf("config/ftbquests/quests/");
        if (questsIndex != -1) {
            return path.substring(questsIndex + "config/ftbquests/quests/".length());
        }
        
        return "chapters/" + new File(filePath).getName();
    }
    
    /**
     * ログメッセージを出力します。
     * @param message ログメッセージ
     */
    private void log(String message) {
        if (logger != null) {
            logger.onLog(message);
        }
    }
}
