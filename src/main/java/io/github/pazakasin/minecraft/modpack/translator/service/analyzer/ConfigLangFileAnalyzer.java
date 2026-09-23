package io.github.pazakasin.minecraft.modpack.translator.service.analyzer;

import io.github.pazakasin.minecraft.modpack.translator.model.TranslatableFile;
import io.github.pazakasin.minecraft.modpack.translator.service.callback.LogCallback;
import io.github.pazakasin.minecraft.modpack.translator.service.processor.CharacterCounter;
import io.github.pazakasin.minecraft.modpack.translator.service.processor.JsonLangFlattener;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * config内の個別対応外の言語ファイル（Config（その他））を検出するクラス。
 * config配下の任意階層の「lang/en_us.*」を探し、JSONは翻訳可能、それ以外は検出のみとする。
 */
public class ConfigLangFileAnalyzer {
    /** 探索対象外とするconfig直下のフォルダ（個別対応済みのため）。 */
    private static final List<String> EXCLUDED_DIRS = Arrays.asList("openloader", "ftbquests");

    /** 探索する最大階層（config直下を1とする）。 */
    private static final int MAX_DEPTH = 16;

    /** 非対応形式の状態表示。 */
    private static final String UNSUPPORTED_FORMAT = "非対応形式（検出のみ）";

    /** JSON解析不可の状態表示。 */
    private static final String UNPARSABLE_JSON = "非対応形式（JSON解析不可）";

    /** ログコールバック。 */
    private final LogCallback logger;

    /** 文字数カウンター。 */
    private final CharacterCounter charCounter;

    /** JSON解析用。 */
    private final JsonLangFlattener flattener;

    /**
     * ConfigLangFileAnalyzerのコンストラクタ。
     * @param logger ログコールバック
     */
    public ConfigLangFileAnalyzer(LogCallback logger) {
        this.logger = logger;
        this.charCounter = new CharacterCounter();
        this.flattener = new JsonLangFlattener();
    }

    /**
     * config内のその他の言語ファイルを解析します。
     * @param inputPath ModPackディレクトリパス
     * @return 検出したファイルのリスト（非対応形式を含む）
     */
    public List<TranslatableFile> analyze(String inputPath) {
        List<TranslatableFile> files = new ArrayList<TranslatableFile>();
        File configDir = new File(inputPath, "config");
        if (!configDir.isDirectory()) {
            return files;
        }

        List<File> langFiles = new ArrayList<File>();
        File[] children = configDir.listFiles();
        if (children != null) {
            for (File child : children) {
                if (child.isDirectory() && !EXCLUDED_DIRS.contains(child.getName().toLowerCase())) {
                    findLangFilesRecursive(child, 1, langFiles);
                }
            }
        }

        log("");
        if (langFiles.isEmpty()) {
            log("Config（その他）の言語ファイルは見つかりませんでした。");
            return files;
        }
        log("検出されたConfig（その他）の言語ファイル数: " + langFiles.size());

        File rootDir = new File(inputPath);
        for (File langFile : langFiles) {
            try {
                TranslatableFile file = analyzeFile(rootDir, langFile);
                if (file != null) {
                    files.add(file);
                    log(String.format("[Config] %s - %s", file.getLangFolderPath(), describe(file)));
                }
            } catch (Exception e) {
                log(String.format("[Config][エラー] %s: %s", langFile.getAbsolutePath(), e.getMessage()));
            }
        }
        return files;
    }

    /**
     * 「lang/en_us.*」に該当するファイルを再帰的に検索します（シンボリックリンクは辿らない）。
     * @param dir 探索対象ディレクトリ
     * @param depth 現在の階層
     * @param result 結果リスト
     */
    private void findLangFilesRecursive(File dir, int depth, List<File> result) {
        if (depth > MAX_DEPTH || Files.isSymbolicLink(dir.toPath())) {
            return;
        }
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                findLangFilesRecursive(file, depth + 1, result);
            } else if (isEnUsFile(file) && "lang".equalsIgnoreCase(file.getParentFile().getName())) {
                result.add(file);
            }
        }
    }

    /**
     * ファイル名が「en_us.拡張子」かを判定します（大文字小文字は区別しない）。
     * @param file 判定対象
     * @return en_us.*の場合true
     */
    private boolean isEnUsFile(File file) {
        return file.getName().toLowerCase().startsWith("en_us.");
    }

    /**
     * 単一の言語ファイルを解析します。
     * @param rootDir ModPackルート
     * @param langFile 言語ファイル
     * @return 検出結果（翻訳対象文字がないJSONはnull）
     * @throws Exception 読込エラー
     */
    private TranslatableFile analyzeFile(File rootDir, File langFile) throws Exception {
        String relativePath = rootDir.getAbsoluteFile().toPath()
                .relativize(langFile.getAbsoluteFile().toPath()).toString().replace("\\", "/");
        String fileId = extractFileId(relativePath);
        String extension = langFile.getName().substring("en_us".length());
        String content = new String(Files.readAllBytes(langFile.toPath()), StandardCharsets.UTF_8);
        File jaJpFile = findSibling(langFile, "ja_jp" + extension);

        if (!".json".equalsIgnoreCase(extension)) {
            return TranslatableFile.createUnsupportedConfigLangFile(langFile.getAbsolutePath(),
                    relativePath, fileId, jaJpFile != null, content, UNSUPPORTED_FORMAT);
        }
        try {
            flattener.parse(content);
        } catch (IllegalArgumentException e) {
            return TranslatableFile.createUnsupportedConfigLangFile(langFile.getAbsolutePath(),
                    relativePath, fileId, jaJpFile != null, content, UNPARSABLE_JSON);
        }

        int charCount = charCounter.countCharacters(content);
        if (charCount == 0) {
            log(String.format("[Config] %s - 翻訳対象文字なしのためスキップ", relativePath));
            return null;
        }
        String jaJpContent = jaJpFile != null
                ? new String(Files.readAllBytes(jaJpFile.toPath()), StandardCharsets.UTF_8) : null;
        return TranslatableFile.createConfigLangFile(langFile.getAbsolutePath(), relativePath,
                fileId, charCount, jaJpFile != null, content, jaJpContent);
    }

    /**
     * 同じフォルダ内の指定名のファイルを探します（大文字小文字は区別しない）。
     * @param langFile 基準ファイル
     * @param name 探すファイル名
     * @return 見つかったファイル（なければnull）
     */
    private File findSibling(File langFile, String name) {
        File[] siblings = langFile.getParentFile().listFiles();
        if (siblings != null) {
            for (File sibling : siblings) {
                if (sibling.isFile() && sibling.getName().equalsIgnoreCase(name)) {
                    return sibling;
                }
            }
        }
        return null;
    }

    /**
     * 相対パスから識別名（config直下からlangフォルダの手前まで）を抽出します。
     * @param relativePath 相対パス（例: config/flan/lang/en_us.json）
     * @return 識別名（例: flan）
     */
    private String extractFileId(String relativePath) {
        String inner = relativePath.substring("config/".length());
        int langIndex = inner.toLowerCase().lastIndexOf("/lang/");
        return langIndex > 0 ? inner.substring(0, langIndex) : inner;
    }

    /**
     * ログ用に検出結果の概要を作成します。
     * @param file 検出結果
     * @return 概要文字列
     */
    private String describe(TranslatableFile file) {
        if (!file.isTranslatable()) {
            return file.getResultMessage();
        }
        return (file.isHasExistingJaJp() ? "既存ja_jp有" : "翻訳可能") + " (" + file.getCharacterCount() + "文字)";
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
