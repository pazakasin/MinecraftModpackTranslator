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

        public QuestFileInfo(File file, QuestFileType type) {
            this.file = file;
            this.type = type;
        }

        public File getFile() {
            return file;
        }

        public QuestFileType getType() {
            return type;
        }

        @Override
        public String toString() {
            return "QuestFileInfo{file=" + file.getPath() + ", type=" + type + "}";
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

    private void detectLangFiles(File langDir, List<QuestFileInfo> result) {
        File enUsFile = new File(langDir, "en_us.snbt");
        if (enUsFile.exists() && enUsFile.isFile()) {
            result.add(new QuestFileInfo(enUsFile, QuestFileType.LANG_FILE));
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
