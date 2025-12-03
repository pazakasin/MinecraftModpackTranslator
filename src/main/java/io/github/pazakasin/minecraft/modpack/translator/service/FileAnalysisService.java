package io.github.pazakasin.minecraft.modpack.translator.service;

import io.github.pazakasin.minecraft.modpack.translator.model.TranslatableFile;
import io.github.pazakasin.minecraft.modpack.translator.service.callback.LogCallback;
import io.github.pazakasin.minecraft.modpack.translator.service.callback.ProgressUpdateCallback;
import io.github.pazakasin.minecraft.modpack.translator.service.processor.CharacterCounter;
import io.github.pazakasin.minecraft.modpack.translator.service.processor.JarFileAnalyzer;
import io.github.pazakasin.minecraft.modpack.translator.service.quest.QuestFileDetector;
import io.github.pazakasin.minecraft.modpack.translator.service.quest.LangFileSNBTExtractor;
import io.github.pazakasin.minecraft.modpack.translator.service.quest.SNBTParser;
import net.querz.nbt.tag.Tag;

import java.io.File;
import java.io.FilenameFilter;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * 翻訳対象ファイルの洗い出しを行うサービスクラス。
 * ModPack内のすべての翻訳可能なファイルを検出し、TranslatableFileのリストを返す。
 */
public class FileAnalysisService {
    /** ログメッセージを出力するコールバック。 */
    private final LogCallback logger;
    
    /** 進捗状況を更新するコールバック。 */
    private final ProgressUpdateCallback progressUpdater;
    
    /** JARファイル内の言語ファイルを解析するアナライザー。 */
    private final JarFileAnalyzer jarAnalyzer;
    
    /** 翻訳対象テキストの文字数をカウントするカウンター。 */
    private final CharacterCounter charCounter;
    
    /** クエストファイルを検出するディテクター。 */
    private final QuestFileDetector questDetector;
    
    /** SNBT形式の言語ファイルから翻訳可能な文字列を抽出するエクストラクター。 */
    private final LangFileSNBTExtractor snbtExtractor;
    
    /** SNBTファイルをパースするパーサー。 */
    private final SNBTParser snbtParser;
    
    /**
     * FileAnalysisServiceのコンストラクタ。
     * @param logger ログコールバック
     * @param progressUpdater 進捗コールバック
     */
    public FileAnalysisService(LogCallback logger, ProgressUpdateCallback progressUpdater) {
        this.logger = logger;
        this.progressUpdater = progressUpdater;
        this.jarAnalyzer = new JarFileAnalyzer();
        this.charCounter = new CharacterCounter();
        this.questDetector = new QuestFileDetector();
        this.snbtExtractor = new LangFileSNBTExtractor();
        this.snbtParser = new SNBTParser();
    }
    
    /**
     * ModPack全体の翻訳対象ファイルを解析します。
     * @param inputPath ModPackディレクトリパス
     * @return 翻訳対象ファイルのリスト
     * @throws Exception ファイルアクセスエラー等
     */
    public List<TranslatableFile> analyzeFiles(String inputPath) throws Exception {
        List<TranslatableFile> files = new ArrayList<TranslatableFile>();
        
        // work フォルダをクリア
        clearWorkFolder();
        
        log("=== ファイル解析開始 ===");
        
        // クエストファイルの解析（先に表示）
        List<TranslatableFile> questFiles = analyzeQuestFiles(inputPath);
        files.addAll(questFiles);
        
        // KubeJS言語ファイルの解析
        List<TranslatableFile> kubeJsFiles = analyzeKubeJSFiles(inputPath);
        files.addAll(kubeJsFiles);
        
        // Modファイルの解析
        List<TranslatableFile> modFiles = analyzeModFiles(inputPath);
        files.addAll(modFiles);
        
        // 元ファイルを work/source/ にエクスポート
        exportSourceFiles(files);
        
        // 合計文字数の計算
        int totalCharCount = 0;
        int selectedCharCount = 0;
        for (TranslatableFile file : files) {
            totalCharCount += file.getCharacterCount();
            if (file.isSelected()) {
                selectedCharCount += file.getCharacterCount();
            }
        }
        
        log("");
        log("=== 解析完了 ===");
        log("検出されたファイル数: " + files.size());
        log("合計翻訳対象文字数: " + totalCharCount);
        log("選択済み文字数: " + selectedCharCount);
        log("元ファイル出力先: work/");
        
        return files;
    }
    
    /**
     * Modファイル（JARファイル）を解析します。
     */
    private List<TranslatableFile> analyzeModFiles(String inputPath) throws Exception {
        List<TranslatableFile> files = new ArrayList<TranslatableFile>();
        
        File modsDir = new File(inputPath, "mods");
        File[] jarFiles = modsDir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(".jar");
            }
        });
        
        if (jarFiles == null || jarFiles.length == 0) {
            log("modsフォルダ内にJARファイルが見つかりません。");
            return files;
        }
        
        log("検出されたMod数: " + jarFiles.length);
        log("");
        
        for (int i = 0; i < jarFiles.length; i++) {
            File jarFile = jarFiles[i];
            int currentModNum = i + 1;
            int totalMods = jarFiles.length;
            
            updateProgress(String.format("[%d/%d] 解析中: %s", 
                currentModNum, totalMods, jarFile.getName()));
            
            try {
                TranslatableFile file = analyzeModJar(jarFile);
                if (file != null) {
                    files.add(file);
                    log(String.format("[%d/%d] %s - %s (%d文字)", 
                        currentModNum, totalMods, jarFile.getName(),
                        file.isHasExistingJaJp() ? "既存ja_jp有" : "翻訳対象",
                        file.getCharacterCount()));
                } else {
                    log(String.format("[%d/%d] %s - en_us.jsonなし", 
                        currentModNum, totalMods, jarFile.getName()));
                }
            } catch (Exception e) {
                log(String.format("[%d/%d][エラー] %s: %s", 
                    currentModNum, totalMods, jarFile.getName(), e.getMessage()));
            }
        }
        
        updateProgress(" ");
        return files;
    }
    
    /**
     * 単一のMod JARファイルを解析します。
     */
    private TranslatableFile analyzeModJar(File jarFile) throws Exception {
        JarFileAnalyzer.LanguageFileInfo langInfo = jarAnalyzer.analyze(jarFile);
        
        if (langInfo.modId == null || langInfo.enUsContent == null) {
            return null;
        }
        
        int charCount = charCounter.countCharacters(langInfo.enUsContent);
        String modName = jarFile.getName().replace(".jar", "");
        
        return TranslatableFile.createModLangFile(
            modName,
            jarFile.getAbsolutePath(),
            langInfo.langFolderPath,
            langInfo.modId,
            charCount,
            langInfo.hasJaJp,
            langInfo.enUsContent,
            langInfo.jaJpContent
        );
    }
    
    /**
     * KubeJS言語ファイルを解析します。
     */
    private List<TranslatableFile> analyzeKubeJSFiles(String inputPath) throws Exception {
        List<TranslatableFile> files = new ArrayList<TranslatableFile>();
        
        File kubeJsDir = new File(inputPath, "kubejs/assets");
        if (!kubeJsDir.exists() || !kubeJsDir.isDirectory()) {
            log("");
            log("kubejs/assetsフォルダが見つかりません。");
            return files;
        }
        
        // kubejs/assets/<id>/lang/en_us.json を検索
        List<File> langFiles = findKubeJSLangFiles(kubeJsDir);
        
        if (langFiles.isEmpty()) {
            log("");
            log("KubeJS言語ファイルが見つかりません。");
            return files;
        }
        
        log("");
        log("検出されたKubeJS言語ファイル数: " + langFiles.size());
        log("");
        
        for (int i = 0; i < langFiles.size(); i++) {
            File langFile = langFiles.get(i);
            int currentNum = i + 1;
            int totalNum = langFiles.size();
            
            updateProgress(String.format("[KubeJS %d/%d] 解析中: %s", 
                currentNum, totalNum, extractKubeJSId(langFile)));
            
            try {
                TranslatableFile file = analyzeKubeJSFile(langFile);
                if (file != null) {
                    files.add(file);
                    log(String.format("[KubeJS %d/%d] %s - %s (%d文字)", 
                        currentNum, totalNum, file.getFileId(),
                        file.isHasExistingJaJp() ? "既存ja_jp有" : "翻訳対象",
                        file.getCharacterCount()));
                }
            } catch (Exception e) {
                log(String.format("[KubeJS %d/%d][エラー] %s: %s", 
                    currentNum, totalNum, extractKubeJSId(langFile), e.getMessage()));
            }
        }
        
        updateProgress(" ");
        return files;
    }
    
    /**
     * kubejs/assets下から言語ファイルを再帰的に検索します。
     */
    private List<File> findKubeJSLangFiles(File assetsDir) {
        List<File> langFiles = new ArrayList<File>();
        findKubeJSLangFilesRecursive(assetsDir, langFiles);
        return langFiles;
    }
    
    /**
     * 再帰的に言語ファイルを検索します。
     */
    private void findKubeJSLangFilesRecursive(File dir, List<File> result) {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        
        for (File file : files) {
            if (file.isDirectory()) {
                findKubeJSLangFilesRecursive(file, result);
            } else if (file.getName().equals("en_us.json")) {
                // kubejs/assets/<id>/lang/en_us.json のパターンを確認
                String path = file.getAbsolutePath().replace("\\", "/");
                if (path.contains("/lang/en_us.json")) {
                    result.add(file);
                }
            }
        }
    }
    
    /**
     * 単一のKubeJS言語ファイルを解析します。
     */
    private TranslatableFile analyzeKubeJSFile(File langFile) throws Exception {
        String enUsContent = new String(Files.readAllBytes(langFile.toPath()), "UTF-8");
        int charCount = charCounter.countCharacters(enUsContent);
        
        if (charCount == 0) {
            return null;
        }
        
        // 言語ファイルIDを抽出
        String fileId = extractKubeJSId(langFile);
        
        // ja_jp.jsonの存在を確認
        File jaJpFile = new File(langFile.getParent(), "ja_jp.json");
        boolean hasJaJp = jaJpFile.exists();
        String jaJpContent = null;
        
        if (hasJaJp) {
            jaJpContent = new String(Files.readAllBytes(jaJpFile.toPath()), "UTF-8");
        }
        
        return TranslatableFile.createKubeJSLangFile(
            langFile.getAbsolutePath(),
            fileId,
            charCount,
            hasJaJp,
            enUsContent,
            jaJpContent
        );
    }
    
    /**
     * KubeJS言語ファイルからIDを抽出します。
     * kubejs/assets/<id>/lang/en_us.json から <id> を取得。
     */
    private String extractKubeJSId(File langFile) {
        String path = langFile.getAbsolutePath().replace("\\", "/");
        int assetsIndex = path.indexOf("/assets/");
        int langIndex = path.indexOf("/lang/");
        
        if (assetsIndex != -1 && langIndex != -1 && assetsIndex < langIndex) {
            String idPart = path.substring(assetsIndex + 8, langIndex);
            return idPart;
        }
        
        return "unknown";
    }
    
    /**
     * クエストファイルを解析します。
     */
    private List<TranslatableFile> analyzeQuestFiles(String inputPath) throws Exception {
        List<TranslatableFile> files = new ArrayList<TranslatableFile>();
        
        File modpackDir = new File(inputPath);
        List<QuestFileDetector.QuestFileInfo> questFileInfos = questDetector.detectQuestFiles(modpackDir);
        
        if (questFileInfos.isEmpty()) {
            log("");
            log("クエストファイルが見つかりません。");
            return files;
        }
        
        log("");
        log("検出されたクエストファイル数: " + questFileInfos.size());
        log("");
        
        for (int i = 0; i < questFileInfos.size(); i++) {
            QuestFileDetector.QuestFileInfo info = questFileInfos.get(i);
            int currentNum = i + 1;
            int totalNum = questFileInfos.size();
            
            updateProgress(String.format("[Quest %d/%d] 解析中: %s", 
                currentNum, totalNum, info.getFile().getName()));
            
            try {
                TranslatableFile file = analyzeQuestFile(info);
                if (file != null) {
                    files.add(file);
                    log(String.format("[Quest %d/%d] %s - %s (%d文字)", 
                        currentNum, totalNum, info.getFile().getName(),
                        info.getType() == QuestFileDetector.QuestFileType.LANG_FILE ? 
                            "言語ファイル" : "クエストファイル",
                        file.getCharacterCount()));
                }
            } catch (Exception e) {
                log(String.format("[Quest %d/%d][エラー] %s: %s", 
                    currentNum, totalNum, info.getFile().getName(), e.getMessage()));
            }
        }
        
        updateProgress(" ");
        return files;
    }
    
    /**
     * 単一のクエストファイルを解析します。
     * 翻訳実行時と同じ方法で文字数をカウントします。
     */
    private TranslatableFile analyzeQuestFile(QuestFileDetector.QuestFileInfo info) throws Exception {
        File file = info.getFile();
        String content = new String(Files.readAllBytes(file.toPath()), "UTF-8");
        
        int charCount = 0;
        String fileId = file.getName().replace(".snbt", "");
        
        if (info.getType() == QuestFileDetector.QuestFileType.LANG_FILE) {
            // Quest言語ファイル: LangFileSNBTExtractorで抽出して文字数カウント
            try {
                Tag<?> rootTag = snbtParser.parse(file);
                java.util.List<LangFileSNBTExtractor.ExtractedText> texts = snbtExtractor.extract(rootTag);
                
                for (LangFileSNBTExtractor.ExtractedText text : texts) {
                    charCount += text.getValue().length();
                }
                
                if (charCount == 0) {
                    return null;
                }
                
                // 既存のja_jp.snbtを確認
                File jaJpFile = info.getJaJpFile();
                boolean hasJaJp = info.hasJaJp();
                String jaJpContent = null;
                
                if (hasJaJp && jaJpFile != null && jaJpFile.exists()) {
                    jaJpContent = new String(Files.readAllBytes(jaJpFile.toPath()), "UTF-8");
                }
                
                return TranslatableFile.createQuestLangFile(
                    file.getAbsolutePath(),
                    fileId,
                    charCount,
                    hasJaJp,
                    content,
                    jaJpContent
                );
            } catch (Exception e) {
                log("Quest言語ファイル解析エラー: " + file.getName() + " - " + e.getMessage());
                return null;
            }
        } else {
            // Questファイル本体: SNBTParserで翻訳対象テキストを抽出して文字数カウント
            try {
                java.util.Map<String, String> texts = snbtParser.extractTranslatableTexts(file);
                
                for (String value : texts.values()) {
                    charCount += value.length();
                }
                
                // 翻訳対象がなくてもファイルとして認識（コピー対象）
                return TranslatableFile.createQuestFile(
                    file.getAbsolutePath(),
                    fileId,
                    charCount,
                    content
                );
            } catch (Exception e) {
                log("Questファイル解析エラー: " + file.getName() + " - " + e.getMessage());
                return null;
            }
        }
    }
    
    /** ログメッセージを出力します。 */
    private void log(String message) {
        if (logger != null) {
            logger.onLog(message);
        }
    }
    
    /** 進捗メッセージを出力します。 */
    private void updateProgress(String message) {
        if (progressUpdater != null) {
            progressUpdater.onProgressUpdate(message);
        }
    }
    
    /**
     * work フォルダをクリアします。
     */
    private void clearWorkFolder() {
        File workDir = new File("work");
        if (workDir.exists()) {
            log("work フォルダをクリア中...");
            deleteDirectory(workDir);
        }
    }
    
    /**
     * ディレクトリを再帰的に削除します。
     */
    private void deleteDirectory(File directory) {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }
        directory.delete();
    }
    
    /**
     * 解析したファイルの元ファイルを work/ にエクスポートします。
     */
    private void exportSourceFiles(List<TranslatableFile> files) throws Exception {
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
     */
    private void exportModLangFile(TranslatableFile file, File workDir) throws Exception {
        // work/mods/MyJPpack/assets/modid/lang/en_us.json
        File outputDir = new File(workDir, "mods/MyJPpack/assets/" + file.getFileId() + "/lang");
        outputDir.mkdirs();
        
        File enUsFile = new File(outputDir, "en_us.json");
        Files.write(enUsFile.toPath(), file.getFileContent().getBytes("UTF-8"));
        
        // 既存日本語ファイルがあればja_jp.jsonも出力
        if (file.isHasExistingJaJp() && file.getExistingJaJpContent() != null) {
            File jaJpFile = new File(outputDir, "ja_jp.json");
            Files.write(jaJpFile.toPath(), file.getExistingJaJpContent().getBytes("UTF-8"));
            
            // workファイルパスはja_jp.jsonを設定（ファイル内容確認用）
            file.setWorkFilePath(jaJpFile.getAbsolutePath());
        } else {
            // workファイルパスを設定
            file.setWorkFilePath(enUsFile.getAbsolutePath());
        }
    }
    
    /**
     * KubeJS言語ファイルをエクスポートします。
     */
    private void exportKubeJSLangFile(TranslatableFile file, File workDir) throws Exception {
        // work/kubejs/assets/<id>/lang/en_us.json
        File outputDir = new File(workDir, "kubejs/assets/" + file.getFileId() + "/lang");
        outputDir.mkdirs();
        
        File enUsFile = new File(outputDir, "en_us.json");
        Files.write(enUsFile.toPath(), file.getFileContent().getBytes("UTF-8"));
        
        // 既存日本語ファイルがあればja_jp.jsonも出力
        if (file.isHasExistingJaJp() && file.getExistingJaJpContent() != null) {
            File jaJpFile = new File(outputDir, "ja_jp.json");
            Files.write(jaJpFile.toPath(), file.getExistingJaJpContent().getBytes("UTF-8"));
            
            // workファイルパスはja_jp.jsonを設定（ファイル内容確認用）
            file.setWorkFilePath(jaJpFile.getAbsolutePath());
        } else {
            // workファイルパスを設定
            file.setWorkFilePath(enUsFile.getAbsolutePath());
        }
    }
    
    /**
     * クエストファイルをエクスポートします。
     */
    private void exportQuestFile(TranslatableFile file, File workDir) throws Exception {
        File sourceFile = new File(file.getSourceFilePath());
        
        // 相対パスを抽出 (config/ftbquests/... の部分)
        String relativePath = extractQuestRelativePath(file.getSourceFilePath());
        
        // work/config/ftbquests/quests/... に出力
        File outputFile = new File(workDir, "config/ftbquests/quests/" + relativePath);
        outputFile.getParentFile().mkdirs();
        
        Files.copy(sourceFile.toPath(), outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        
        // Quest言語ファイルで、既存のja_jp.snbtがある場合はそれもエクスポート
        if (file.getFileType() == TranslatableFile.FileType.QUEST_LANG_FILE &&
            file.isHasExistingJaJp() && file.getExistingJaJpContent() != null) {
            
            File jaJpOutputFile = new File(outputFile.getParent(), "ja_jp.snbt");
            Files.write(jaJpOutputFile.toPath(), file.getExistingJaJpContent().getBytes("UTF-8"));
            
            // workファイルパスはja_jp.snbtを設定（ファイル内容確認用）
            file.setWorkFilePath(jaJpOutputFile.getAbsolutePath());
        } else {
            // workファイルパスを設定
            file.setWorkFilePath(outputFile.getAbsolutePath());
        }
    }
    
    /**
     * クエストファイルの相対パスを抽出します。
     */
    private String extractQuestRelativePath(String filePath) {
        String path = filePath.replace("\\", "/");
        
        // config/ftbquests/quests/ 以降を抽出
        int questsIndex = path.indexOf("config/ftbquests/quests/");
        if (questsIndex != -1) {
            return path.substring(questsIndex + "config/ftbquests/quests/".length());
        }
        
        // フォールバック: chapters/ にファイル名を置く
        return "chapters/" + new File(filePath).getName();
    }
}
