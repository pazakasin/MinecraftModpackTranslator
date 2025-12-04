package io.github.pazakasin.minecraft.modpack.translator.service.backup;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * ファイルのバックアップを管理するクラス。
 * クエストファイルのバックアップと出力フォルダの圧縮を実行。
 */
public class BackupManager {
    /** バックアップディレクトリ名。 */
    private static final String BACKUP_DIR = "input_backup";
    
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
     * 出力フォルダを圧縮してoutput_backupフォルダに保存します。
     * @return 圧縮結果（ZIPファイルのパス）
     * @throws IOException 圧縮失敗
     */
    public ZipResult zipOutputFolder() throws IOException {
        File outputDir = new File("output");
        
        if (!outputDir.exists() || !outputDir.isDirectory()) {
            return null;
        }
        
        File outputBackupDir = new File("output_backup");
        if (!outputBackupDir.exists()) {
            outputBackupDir.mkdirs();
        }
        
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");
        String timestamp = dateFormat.format(new Date());
        String zipFileName = "output_" + timestamp + ".zip";
        
        File zipFile = new File(outputBackupDir, zipFileName);
        
        int fileCount = zipDirectory(outputDir.toPath(), zipFile);
        
        ZipResult result = new ZipResult();
        result.zipPath = zipFile.getAbsolutePath();
        result.timestamp = timestamp;
        result.fileCount = fileCount;
        result.sourceDir = outputDir.getAbsolutePath();
        
        return result;
    }
    
    /**
     * workフォルダの内容を圧縮してinput_backupフォルダに保存します。
     * 既に同名ファイルが存在する場合はスキップします。
     * @param modpackName ModPack名（ZIPファイル名に使用）
     * @return 圧縮結果（nullの場合はスキップまたは失敗）
     * @throws IOException 圧縮失敗
     */
    public ZipResult zipWorkFolder(String modpackName) throws IOException {
        File workDir = new File("work");
        
        if (!workDir.exists() || !workDir.isDirectory()) {
            return null;
        }
        
        File inputBackupDir = new File("input_backup");
        if (!inputBackupDir.exists()) {
            inputBackupDir.mkdirs();
        }
        
        String zipFileName = modpackName + ".zip";
        File zipFile = new File(inputBackupDir, zipFileName);
        
        if (zipFile.exists()) {
            return null;
        }
        
        int fileCount = zipWorkDirectory(workDir, zipFile);
        
        ZipResult result = new ZipResult();
        result.zipPath = zipFile.getAbsolutePath();
        result.timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        result.fileCount = fileCount;
        result.sourceDir = workDir.getAbsolutePath();
        
        return result;
    }
    
    /**
     * workディレクトリを再帰的にZIP圧縮します。
     * configとkubejsフォルダのみを圧縮します。
     * @param workDir workディレクトリ
     * @param zipFile 出力ZIPファイル
     * @return 圧縮したファイル数
     * @throws IOException 圧縮失敗
     */
    private int zipWorkDirectory(File workDir, File zipFile) throws IOException {
        int[] count = {0};
        
        try (FileOutputStream fos = new FileOutputStream(zipFile);
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            
            File configDir = new File(workDir, "config");
            if (configDir.exists()) {
                zipSubDirectory(configDir, workDir.toPath(), zos, count);
            }
            
            File kubejsDir = new File(workDir, "kubejs");
            if (kubejsDir.exists()) {
                zipSubDirectory(kubejsDir, workDir.toPath(), zos, count);
            }
        }
        
        return count[0];
    }
    
    /**
     * サブディレクトリを再帰的に圧縮します。
     * @param subDir 圧縮対象ディレクトリ
     * @param basePath ベースパス（相対パス計算用）
     * @param zos ZipOutputStream
     * @param count ファイルカウンタ
     * @throws IOException 圧縮失敗
     */
    private void zipSubDirectory(File subDir, Path basePath, ZipOutputStream zos, int[] count) throws IOException {
        try (Stream<Path> stream = Files.walk(subDir.toPath())) {
            stream.filter(path -> !Files.isDirectory(path))
                  .forEach(path -> {
                try {
                    Path relativePath = basePath.relativize(path);
                    String zipEntryName = relativePath.toString().replace("\\", "/");
                    
                    ZipEntry zipEntry = new ZipEntry(zipEntryName);
                    zos.putNextEntry(zipEntry);
                    
                    try (FileInputStream fis = new FileInputStream(path.toFile())) {
                        byte[] buffer = new byte[8192];
                        int length;
                        while ((length = fis.read(buffer)) > 0) {
                            zos.write(buffer, 0, length);
                        }
                    }
                    
                    zos.closeEntry();
                    count[0]++;
                } catch (IOException e) {
                    throw new RuntimeException("Failed to zip: " + path, e);
                }
            });
        }
    }
    
    /**
     * ディレクトリを再帰的にZIP圧縮します。
     * @param sourceDir 圧縮対象ディレクトリ
     * @param zipFile 出力ZIPファイル
     * @return 圧縮したファイル数
     * @throws IOException 圧縮失敗
     */
    private int zipDirectory(Path sourceDir, File zipFile) throws IOException {
        int[] count = {0};
        
        try (FileOutputStream fos = new FileOutputStream(zipFile);
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            
            try (Stream<Path> stream = Files.walk(sourceDir)) {
                stream.filter(path -> !Files.isDirectory(path))
                      .forEach(path -> {
                    try {
                        Path relativePath = sourceDir.relativize(path);
                        String zipEntryName = relativePath.toString().replace("\\", "/");
                        
                        ZipEntry zipEntry = new ZipEntry(zipEntryName);
                        zos.putNextEntry(zipEntry);
                        
                        try (FileInputStream fis = new FileInputStream(path.toFile())) {
                            byte[] buffer = new byte[8192];
                            int length;
                            while ((length = fis.read(buffer)) > 0) {
                                zos.write(buffer, 0, length);
                            }
                        }
                        
                        zos.closeEntry();
                        count[0]++;
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to zip: " + path, e);
                    }
                });
            }
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
    
    /**
     * ZIP圧縮結果を保持するクラス。
     */
    public static class ZipResult {
        /** ZIPファイルパス（絶対パス）。 */
        public String zipPath;
        
        /** タイムスタンプ。 */
        public String timestamp;
        
        /** 圧縮したファイル数。 */
        public int fileCount;
        
        /** 圧縮元ディレクトリ（絶対パス）。 */
        public String sourceDir;
    }
}
