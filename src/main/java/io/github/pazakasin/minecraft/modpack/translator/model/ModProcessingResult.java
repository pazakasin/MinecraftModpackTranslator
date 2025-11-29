package io.github.pazakasin.minecraft.modpack.translator.model;

/**
 * Mod処理結果を保持するデータクラス
 */
public class ModProcessingResult {
    /** Mod名（JARファイル名から拡張子を除いた文字列） */
    public String modName;
    
    /** 言語ファイルが格納されているフォルダパス */
    public String langFolderPath;
    
    /** 英語言語ファイル（en_us.json）が存在したか */
    public boolean hasEnUs = false;
    
    /** 日本語言語ファイル（ja_jp.json）が存在したか */
    public boolean hasJaJp = false;
    
    /** 翻訳を実行したか */
    public boolean translated = false;
    
    /** 翻訳対象の文字数 */
    public int characterCount = 0;
    
    /** 翻訳に成功したか */
    public boolean translationSuccess = false;
    
    public ModProcessingResult() {
    }
    
    @Override
    public String toString() {
        return "ModProcessingResult{" +
                "modName='" + modName + '\'' +
                ", langFolderPath='" + langFolderPath + '\'' +
                ", hasEnUs=" + hasEnUs +
                ", hasJaJp=" + hasJaJp +
                ", translated=" + translated +
                ", characterCount=" + characterCount +
                ", translationSuccess=" + translationSuccess +
                '}';
    }
}