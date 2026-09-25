package io.github.pazakasin.minecraft.modpack.translator.service;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 書式コード（§コード・書式指定子）の不一致を収集し、CSVに出力するクラス。
 */
public class FormatWarningReport {
    /** 警告の一覧（各要素は ファイル, キー, 原文, 訳文 の4項目）。 */
    private final List<String[]> entries = new ArrayList<String[]>();

    /**
     * 警告を空にします（翻訳実行の開始時に呼ぶ）。
     */
    public void clear() {
        entries.clear();
    }

    /**
     * 警告を1件追加します。
     * @param fileLabel ファイル名（null可）
     * @param key 言語ファイルのキー
     * @param original 原文
     * @param translated 訳文
     */
    public void add(String fileLabel, String key, String original, String translated) {
        entries.add(new String[] {fileLabel == null ? "" : fileLabel, key, original, translated});
    }

    /**
     * 警告の件数を取得します。
     * @return 件数
     */
    public int size() {
        return entries.size();
    }

    /**
     * 警告をCSV（UTF-8、BOM付き）に書き出します。0件の場合は書き出しません。
     * @param dir 出力先フォルダ
     * @return 出力したファイルの絶対パス（0件の場合はnull）
     * @throws IOException 書き込みエラー
     */
    public String writeCsv(File dir) throws IOException {
        if (entries.isEmpty()) {
            return null;
        }
        if (!dir.exists()) {
            dir.mkdirs();
        }
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File csvFile = new File(dir, "format_warnings_" + timestamp + ".csv");
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(csvFile), StandardCharsets.UTF_8))) {
            writer.write('﻿');
            writeRow(writer, new String[] {"ファイル", "キー", "原文", "訳文"});
            for (String[] entry : entries) {
                writeRow(writer, entry);
            }
        }
        return csvFile.getAbsolutePath();
    }

    /**
     * CSVの1行を書き込みます（全項目をダブルクォートで囲む）。
     * @param writer 書き込み先
     * @param values 項目の配列
     * @throws IOException 書き込みエラー
     */
    private void writeRow(BufferedWriter writer, String[] values) throws IOException {
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                writer.write(',');
            }
            String value = values[i] == null ? "" : values[i];
            writer.write('"');
            writer.write(value.replace("\"", "\"\""));
            writer.write('"');
        }
        writer.write("\r\n");
    }
}
