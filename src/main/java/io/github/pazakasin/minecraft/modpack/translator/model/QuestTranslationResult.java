package io.github.pazakasin.minecraft.modpack.translator.model;

import java.util.ArrayList;
import java.util.List;

/**
 * クエストファイルの翻訳処理結果を保持するクラス。
 */
public class QuestTranslationResult {
    /** Lang File（en_us.snbt）が存在したか。 */
    public boolean hasLangFile = false;
    
    /** Lang Fileを翻訳したか。 */
    public boolean langFileTranslated = false;
    
    /** Lang Fileの翻訳が成功したか。 */
    public boolean langFileSuccess = false;
    
    /** Lang Fileの翻訳対象文字数。 */
    public int langFileCharacterCount = 0;
    
    /** 処理したクエストファイル数。 */
    public int questFileCount = 0;
    
    /** 翻訳したクエストファイル数。 */
    public int questFileTranslated = 0;
    
    /** クエストファイルの翻訳が成功した数。 */
    public int questFileSuccess = 0;
    
    /** クエストファイルの翻訳対象文字数合計。 */
    public int questFileCharacterCount = 0;
    
    /** バックアップ先パス。nullの場合バックアップなし。 */
    public String backupPath = null;
    
    /** 個別ファイルの処理結果リスト。 */
    public List<QuestFileResult> fileResults = new ArrayList<QuestFileResult>();
    
    /**
     * 翻訳処理を実行したか判定します。
     * @return Lang Fileまたはクエストファイルを翻訳した場合true
     */
    public boolean hasTranslation() {
        return langFileTranslated || questFileTranslated > 0;
    }
    
    /**
     * すべての翻訳が成功したか判定します。
     * @return 翻訳を実行し、すべて成功した場合true
     */
    public boolean isAllSuccess() {
        if (!hasTranslation()) {
            return false;
        }
        
        boolean langSuccess = !langFileTranslated || langFileSuccess;
        boolean questSuccess = questFileTranslated == 0 || questFileSuccess == questFileTranslated;
        
        return langSuccess && questSuccess;
    }
    
    /**
     * 合計翻訳文字数を取得します。
     * @return Lang Fileとクエストファイルの文字数合計
     */
    public int getTotalCharacterCount() {
        return langFileCharacterCount + questFileCharacterCount;
    }
}
