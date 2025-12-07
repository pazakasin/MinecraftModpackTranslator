package io.github.pazakasin.minecraft.modpack.translator.comparison;

import java.io.File;

import io.github.pazakasin.minecraft.modpack.translator.controller.SettingsDialog;

/**
 * 隠し機能の有効化を判定するクラス。
 */
public class HiddenFeatureManager {
    /** loadフォルダ名 */
    private static final String LOAD_FOLDER = "load";
    
    /**
     * 隠し機能（翻訳履歴読込モード）が有効かどうかを判定。
     * 条件: 設定のデバッグモードが有効 かつ loadフォルダが存在
     * 
     * @return 有効な場合true
     */
    public static boolean isHistoryLoadEnabled() {
        File loadFolder = new File(LOAD_FOLDER);
        
        return SettingsDialog.isDebugMode() && 
               loadFolder.exists() && 
               loadFolder.isDirectory();
    }
    
    /**
     * loadフォルダのパスを取得。
     * @return loadフォルダのFileオブジェクト
     */
    public static File getLoadFolder() {
        return new File(LOAD_FOLDER);
    }
}
