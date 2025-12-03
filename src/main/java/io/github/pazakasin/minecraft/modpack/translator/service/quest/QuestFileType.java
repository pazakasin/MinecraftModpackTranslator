package io.github.pazakasin.minecraft.modpack.translator.service.quest;

/**
 * クエストファイルの種類を定義するenum。
 * QuestFileDetectorおよびQuestFileInfoで使用され、検出されたファイルの種別を識別。
 */
public enum QuestFileType {
    /** 言語ファイル（en_us.snbt） */
    LANG_FILE,
    
    /** クエストファイル本体（chapter*.snbt等） */
    QUEST_FILE
}
