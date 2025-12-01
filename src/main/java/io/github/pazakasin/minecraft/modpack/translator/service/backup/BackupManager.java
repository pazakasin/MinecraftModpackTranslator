package io.github.pazakasin.minecraft.modpack.translator.service.backup;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.stream.Stream;

/**
 * クエストファイルのバックアップを管理するクラス。
 * 翻訳前に自動的にconfig/ftbquestsをバックアップ。
 */
public class BackupManager {
    /** バックアップディレクトリ名。 */
    private static final String BACKUP_DIR = "backup";
    
    /** バックアップディレクトリのルート。 */
    private final File backupRoot;
    
    /**
     * BackupManagerのコンストラクタ。
     */
    public BackupManager() {
        this.backupRoot = new File(BACKUP_DIR);
    }
    
    /**
     * クエストファイルをバックアップします。
     * @param modpackDir ModPackディレクトリ
     * @return バックアップ結果（バックアップ先パス、nullの場合バックアップ不要）
     * @throws IOException バックアップ失敗
     */
    public BackupResult backup(File modpackDir) throws IOException {
        File questsDir = new File(modpackDir, "config/ftbquests");
        
        if (!questsDir.exists() || !questsDir.isDirectory()) {
            return null;
        }
        
        if (!backupRoot.exists()) {
            backupRoot.mkdirs();
        }
        
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");
        String timestamp = dateFormat.format(new Date());
        String backupDirName = "ftbquests_" + timestamp;
        
        File backupDir = new File(backupRoot, backupDirName);
        backupDir.mkdirs();
        
        int fileCount = copyDirectory(questsDir.toPath(), backupDir.toPath());
        
        BackupResult result = new BackupResult();
        result.backupPath = backupDir.getAbsolutePath();
        result.timestamp = timestamp;
        result.fileCount = fileCount;
        result.sourceDir = questsDir.getAbsolutePath();
        
        return result;
    }
    
    /**
     * ディレクトリを再帰的にコピーします。
     * @param source コピー元
     * @param target コピー先
     * @return コピーしたファイル数
     * @throws IOException コピー失敗
     */
    private int copyDirectory(Path source, Path target) throws IOException {
        int[] count = {0};
        
        try (Stream<Path> stream = Files.walk(source)) {
            stream.forEach(sourcePath -> {
                try {
                    Path targetPath = target.resolve(source.relativize(sourcePath));
                    
                    if (Files.isDirectory(sourcePath)) {
                        Files.createDirectories(targetPath);
                    } else {
                        Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                        count[0]++;
                    }
                } catch (IOException e) {
                    throw new RuntimeException("Failed to copy: " + sourcePath, e);
                }
            });
        }
        
        return count[0];
    }
    
    /**
     * バックアップ結果を保持するクラス。
     */
    public static class BackupResult {
        /** バックアップ先パス（絶対パス）。 */
        public String backupPath;
        
        /** タイムスタンプ。 */
        public String timestamp;
        
        /** バックアップしたファイル数。 */
        public int fileCount;
        
        /** バックアップ元ディレクトリ（絶対パス）。 */
        public String sourceDir;
    }
}
