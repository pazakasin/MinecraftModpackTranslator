package io.github.pazakasin.minecraft.modpack.translator.service.analyzer;

import io.github.pazakasin.minecraft.modpack.translator.model.TranslatableFile;
import io.github.pazakasin.minecraft.modpack.translator.model.FileType;
import io.github.pazakasin.minecraft.modpack.translator.service.callback.LogCallback;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * workフォルダへのファイルエクスポートを担当するクラス。
 * 解析したファイルをworkフォルダに出力。
 */
public class WorkFolderExporter {
    /** ログコールバック。 */
    private final LogCallback logger;
    
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
