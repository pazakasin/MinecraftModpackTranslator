package io.github.pazakasin.minecraft.modpack.translator.comparison;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * loadフォルダの構造を検証するクラス。
 */
public class LoadFolderValidator {
    /**
     * loadフォルダの構造が正しいかを検証。
     * 
     * @param loadFolder loadフォルダのパス
     * @return 検証結果（エラーメッセージのリスト、空なら正常）
     */
    public static List<String> validate(File loadFolder) {
        List<String> errors = new ArrayList<String>();
        
        if (!loadFolder.exists()) {
            errors.add("loadフォルダが存在しません: " + loadFolder.getAbsolutePath());
            return errors;
        }
        
        if (!loadFolder.isDirectory()) {
            errors.add("loadが通常ファイルです。フォルダである必要があります。");
            return errors;
        }
        
        // 必要なサブフォルダの存在確認
        File resourcepacksFolder = new File(loadFolder, "resourcepacks");
        File kubejsFolder = new File(loadFolder, "kubejs");
        File configsFolder = new File(loadFolder, "configs");
        
        boolean hasAnyFolder = resourcepacksFolder.exists() || 
                               kubejsFolder.exists() || 
                               configsFolder.exists();
        
        if (!hasAnyFolder) {
            errors.add("loadフォルダ内に必要なサブフォルダ（resourcepacks, kubejs, configs）が見つかりません。");
        }
        
        return errors;
    }
    
    /**
     * 検証結果がエラーを含むかどうか。
     * @param errors 検証結果
     * @return エラーがある場合true
     */
    public static boolean hasErrors(List<String> errors) {
        return !errors.isEmpty();
    }
    
    /**
     * エラーメッセージリストを1つの文字列に結合。
     * @param errors エラーリスト
     * @return 結合されたエラーメッセージ
     */
    public static String formatErrors(List<String> errors) {
        StringBuilder sb = new StringBuilder();
        sb.append("loadフォルダの検証エラー:\n\n");
        for (String error : errors) {
            sb.append("・").append(error).append("\n");
        }
        return sb.toString();
    }
}
