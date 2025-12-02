package io.github.pazakasin.minecraft.modpack.translator.service.processor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * 言語ファイルを出力ディレクトリに書き込むクラス。
 * Minecraftの標準的なディレクトリ構造（assets/[modid]/lang）で保存。
 */
public class LanguageFileWriter {
    /** 言語ファイルの出力先ルートディレクトリ。 */
    private final File outputDir;
    
    /**
     * LanguageFileWriterのコンストラクタ。
     * @param outputDir 出力先ルートディレクトリ
     */
    public LanguageFileWriter(File outputDir) {
        this.outputDir = outputDir;
    }
    
    /**
     * 言語ファイルを出力ディレクトリに書き込みます。
     * KubeJSの場合は専用パス、その他はリソースパック形式で出力。
     * @param modId Mod ID
     * @param enUsContent 英語ファイル内容
     * @param jaJpContent 日本語ファイル内容
     * @throws IOException ファイル書き込み失敗
     */
    public void writeLanguageFiles(String modId, String enUsContent, String jaJpContent) throws IOException {
        File outputBase = outputDir.getParentFile();
        File langDir;
        if ("kubejs".equals(modId)) {
            langDir = new File(outputBase, "kubejs/assets/kubejs/lang");
        } else {
            langDir = new File(outputBase, "resourcepacks/MyJPpack/assets/" + modId + "/lang");
        }
        langDir.mkdirs();
        
        if (enUsContent != null) {
            Files.write(new File(langDir, "en_us.json").toPath(), 
                       enUsContent.getBytes("UTF-8"));
        }
        
        if (jaJpContent != null) {
            Files.write(new File(langDir, "ja_jp.json").toPath(), 
                       jaJpContent.getBytes("UTF-8"));
        }
    }
}
