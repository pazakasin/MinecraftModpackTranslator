package io.github.pazakasin.minecraft.modpack.translator.controller.handler;

import java.util.ArrayList;
import java.util.List;

import io.github.pazakasin.minecraft.modpack.translator.model.FileType;
import io.github.pazakasin.minecraft.modpack.translator.model.TranslatableFile;

/**
 * ファイル解析後に表示する「Config（その他）」検出通知の文面を作成するクラス。
 * 翻訳可能なファイルと非対応形式（検出のみ）のファイルを分けて一覧化する。
 */
public class ConfigLangNoticeBuilder {
    /** 1区分あたりの最大表示件数（超過分は件数のみ表示）。 */
    private static final int MAX_LINES = 15;

    /**
     * 通知の文面を作成します。
     * @param files 解析済みファイルリスト
     * @return 通知文面（Config（その他）のファイルがなければnull）
     */
    public String build(List<TranslatableFile> files) {
        List<TranslatableFile> translatable = new ArrayList<TranslatableFile>();
        List<TranslatableFile> unsupported = new ArrayList<TranslatableFile>();
        for (TranslatableFile file : files) {
            if (file.getFileType() != FileType.CONFIG_LANG_FILE) {
                continue;
            }
            if (file.isTranslatable()) {
                translatable.add(file);
            } else {
                unsupported.add(file);
            }
        }
        if (translatable.isEmpty() && unsupported.isEmpty()) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("config内に、個別対応していない言語ファイルが見つかりました。\n");
        sb.append("カテゴリ「").append(FileType.CONFIG_LANG_FILE.getDisplayName())
          .append("」に表示しています（初期状態は未選択）。\n");
        sb.append("MOD独自の読み込み方式の可能性があるため、翻訳する場合は内容を確認してください。\n");

        if (!translatable.isEmpty()) {
            int total = 0;
            for (TranslatableFile file : translatable) {
                total += file.getCharacterCount();
            }
            sb.append("\n■ 翻訳可能: ").append(translatable.size())
              .append("件（合計 ").append(String.format("%,d", total)).append("文字）\n");
            appendLines(sb, translatable, true);
        }
        if (!unsupported.isEmpty()) {
            sb.append("\n■ 非対応形式（検出のみ・翻訳不可）: ").append(unsupported.size()).append("件\n");
            appendLines(sb, unsupported, false);
        }
        return sb.toString();
    }

    /**
     * ファイルの一覧行を追加します（最大件数を超えた分は件数のみ）。
     * @param sb 追加先
     * @param files 対象ファイル
     * @param withCount 文字数を表示するか
     */
    private void appendLines(StringBuilder sb, List<TranslatableFile> files, boolean withCount) {
        for (int i = 0; i < files.size() && i < MAX_LINES; i++) {
            TranslatableFile file = files.get(i);
            sb.append("  ").append(file.getLangFolderPath());
            if (withCount) {
                sb.append("  (").append(String.format("%,d", file.getCharacterCount())).append("文字)");
            }
            if (file.isHasExistingJaJp()) {
                sb.append("  [既存ja_jpあり]");
            }
            sb.append("\n");
        }
        if (files.size() > MAX_LINES) {
            sb.append("  …他 ").append(files.size() - MAX_LINES).append("件\n");
        }
    }
}
