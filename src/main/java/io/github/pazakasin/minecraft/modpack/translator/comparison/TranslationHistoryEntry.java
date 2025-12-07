package io.github.pazakasin.minecraft.modpack.translator.comparison;

import java.io.File;
import java.util.Map;

/**
 * 読み込んだ翻訳履歴データを保持するクラス。
 */
public class TranslationHistoryEntry {
    /** ファイルパス */
    private final File file;
    
    /** 翻訳データ（キーと翻訳後テキストのマップ） */
    private final Map<String, String> translations;
    
    /**
     * コンストラクタ。
     * @param file ファイルパス
     * @param translations 翻訳データマップ
     */
    public TranslationHistoryEntry(File file, Map<String, String> translations) {
        this.file = file;
        this.translations = translations;
    }
    
    /**
     * ファイルパスを取得。
     * @return ファイルパス
     */
    public File getFile() {
        return file;
    }
    
    /**
     * 翻訳データマップを取得。
     * @return 翻訳データマップ
     */
    public Map<String, String> getTranslations() {
        return translations;
    }
}
