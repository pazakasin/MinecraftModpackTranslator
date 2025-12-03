package io.github.pazakasin.minecraft.modpack.translator.service.quest;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * FTB Questsのクエストファイルを検出するクラス。
 * 言語ファイルとクエストファイル本体の両方を検出する。
 */
public class QuestFileDetector {

    /**
     * 検出されたクエストファイルの情報を保持するクラス。
     */
    public static class QuestFileInfo {
        private final File file;
        private final QuestFileType type;
        private final File jaJpFile;

        public QuestFileInfo(File file, QuestFileType type) {
            this(file, type, null);
        }

        public QuestFileInfo(File file, QuestFileType type, File jaJpFile) {
            this.file = file;
            this.type = type;
            this.jaJpFile = jaJpFile;
        }

        public File getFile() {
            return file;
        }

        public QuestFileType getType() {
            return type;
        }

        public boolean hasJaJp() {
            return jaJpFile != null && jaJpFile.exists();
        }

        public File getJaJpFile() {
            return jaJpFile;
        }

        @Override
        public String toString() {
            return "QuestFileInfo{file=" + file.getPath() + ", type=" + type + ", hasJaJp=" + hasJaJp() + "}";
        }
    }

    /**
     * クエストファイルの種類を示す列挙型。
     */
    public enum QuestFileType {
        LANG_FILE,
        QUEST_FILE
    }

    /**
     * ModPackディレクトリからFTB Questsファイルを検出する。
     */
    public List<QuestFileInfo> detectQuestFiles(File modpackDir) throws IOException {
        List<QuestFileInfo> result = new ArrayList<QuestFileInfo>();
        
        File questsDir = new File(modpackDir, "config/ftbquests/quests");
        if (!questsDir.exists() || !questsDir.isDirectory()) {
            return result;
        }

        File langDir = new File(questsDir, "lang");
        if (langDir.exists() && langDir.isDirectory()) {
            detectLangFiles(langDir, result);
        }

        detectQuestFilesRecursive(questsDir, langDir, result);

        return result;
    }

    /**
     * 言語ファイル（en_us.snbt）を検出し、ja_jp.snbtの有無もチェックする。
     */
    private void detectLangFiles(File langDir, List<QuestFileInfo> result) {
        File enUsFile = new File(langDir, "en_us.snbt");
        if (enUsFile.exists() && enUsFile.isFile()) {
            File jaJpFile = new File(langDir, "ja_jp.snbt");
            result.add(new QuestFileInfo(enUsFile, QuestFileType.LANG_FILE, jaJpFile));
        }
    }

    private void detectQuestFilesRecursive(File currentDir, File langDir, List<QuestFileInfo> result) throws IOException {
        try (Stream<Path> paths = Files.walk(currentDir.toPath())) {
            paths.filter(Files::isRegularFile)
                 .filter(p -> p.toString().endsWith(".snbt"))
                 .filter(p -> !isInLangDirectory(p, langDir))
                 .forEach(p -> result.add(new QuestFileInfo(p.toFile(), QuestFileType.QUEST_FILE)));
        }
    }

    private boolean isInLangDirectory(Path path, File langDir) {
        if (langDir == null || !langDir.exists()) {
            return false;
        }
        return path.toAbsolutePath().startsWith(langDir.toPath().toAbsolutePath());
    }
}
