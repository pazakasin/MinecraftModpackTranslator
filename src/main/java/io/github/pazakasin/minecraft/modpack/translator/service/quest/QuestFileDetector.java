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
     * ModPackディレクトリからFTB Questsファイルを検出する。
     * @param modpackDir ModPackディレクトリ
     * @return 検出されたファイル情報のリスト
     * @throws IOException ファイル検索エラー
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
     * @param langDir 言語ファイルディレクトリ
     * @param result 結果リスト
     */
    private void detectLangFiles(File langDir, List<QuestFileInfo> result) {
        File enUsFile = new File(langDir, "en_us.snbt");
        if (enUsFile.exists() && enUsFile.isFile()) {
            File jaJpFile = new File(langDir, "ja_jp.snbt");
            result.add(new QuestFileInfo(enUsFile, QuestFileType.LANG_FILE, jaJpFile));
        }
    }

    /**
     * クエストファイルを再帰的に検出する。
     * @param currentDir 現在のディレクトリ
     * @param langDir 言語ファイルディレクトリ
     * @param result 結果リスト
     * @throws IOException ファイル検索エラー
     */
    private void detectQuestFilesRecursive(File currentDir, File langDir, List<QuestFileInfo> result) throws IOException {
        try (Stream<Path> paths = Files.walk(currentDir.toPath())) {
            paths.filter(Files::isRegularFile)
                 .filter(new java.util.function.Predicate<Path>() {
                     @Override
                     public boolean test(Path p) {
                         return p.toString().endsWith(".snbt");
                     }
                 })
                 .filter(new java.util.function.Predicate<Path>() {
                     @Override
                     public boolean test(Path p) {
                         return !isInLangDirectory(p, langDir);
                     }
                 })
                 .forEach(new java.util.function.Consumer<Path>() {
                     @Override
                     public void accept(Path p) {
                         result.add(new QuestFileInfo(p.toFile(), QuestFileType.QUEST_FILE));
                     }
                 });
        }
    }

    /**
     * パスが言語ディレクトリ内かを判定する。
     * @param path 判定対象パス
     * @param langDir 言語ディレクトリ
     * @return 言語ディレクトリ内の場合true
     */
    private boolean isInLangDirectory(Path path, File langDir) {
        if (langDir == null || !langDir.exists()) {
            return false;
        }
        return path.toAbsolutePath().startsWith(langDir.toPath().toAbsolutePath());
    }
}
