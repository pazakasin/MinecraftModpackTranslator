package io.github.pazakasin.minecraft.modpack.translator.comparison;

/**
 * 翻訳比較のモードを表す列挙型。
 */
public enum ComparisonMode {
    /** 通常の翻訳実行モード */
    NORMAL,
    
    /** 履歴読込モード（loadフォルダから翻訳後ファイルを読み込む） */
    HISTORY_LOAD
}
