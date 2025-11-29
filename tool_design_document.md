# Minecraft ModPack 日本語翻訳ツール 設計書

## 1. 外部設計

### 1.1 システム概要

**目的**: Minecraft ModPackに含まれる複数Modの英語言語ファイルを日本語に一括翻訳するツール

**ターゲットユーザー**: ModPackプレイヤー、サーバー管理者、翻訳者

**実行環境**: 
- Java 1.8以上
- Windows/Mac/Linux対応
- 必要ライブラリ: Gson 2.10.1以上

---

### 1.2 機能一覧

#### 1.2.1 翻訳機能
| 機能ID | 機能名 | 概要 |
|--------|--------|------|
| F001 | ModPack自動スキャン | 指定ディレクトリ内の全JARファイルを検出 |
| F002 | 言語ファイル抽出 | 各Mod内のen_us.json、ja_jp.jsonを抽出 |
| F003 | 自動翻訳 | 翻訳APIを使用して英語→日本語に翻訳 |
| F004 | 既存翻訳の保持 | ja_jp.jsonが既存の場合はそのまま使用 |
| F005 | 翻訳ファイル出力 | 翻訳結果をResourcePack形式で出力 |

#### 1.2.2 設定機能
| 機能ID | 機能名 | 概要 |
|--------|--------|------|
| F101 | プロバイダー選択 | 4つの翻訳API（Google/DeepL/ChatGPT/Claude）から選択 |
| F102 | APIキー管理 | 各プロバイダーのAPIキーを保存・読み込み |
| F103 | 設定永続化 | 設定をファイルに保存 |

#### 1.2.3 UI機能
| 機能ID | 機能名 | 概要 |
|--------|--------|------|
| F201 | ディレクトリ選択 | ModPackディレクトリをGUIで選択 |
| F202 | 進捗表示 | Mod番号/全Mod数、エントリー数の表示 |
| F203 | 処理ログ表示 | リアルタイムで処理状況を表示 |
| F204 | 結果テーブル表示 | 各Modの処理結果を表形式で表示 |
| F205 | CSV出力 | 処理結果をCSVファイルで出力 |

---

### 1.3 画面設計

#### 1.3.1 メイン画面
```
┌─────────────────────────────────────────────────────┐
│ Minecraft ModPack 日本語翻訳ツール v2.0              │
├─────────────────────────────────────────────────────┤
│ ModPackディレクトリ                                   │
│ ┌───────────────────────────────────┐  ┌─────┐     │
│ │ C:\Games\ModPack\                 │  │参照...│     │
│ └───────────────────────────────────┘  └─────┘     │
├─────────────────────────────────────────────────────┤
│ 翻訳プロバイダー: Google Translation API   [進捗表示]│
│                                                      │
│      [⚙ 設定]  [翻訳開始]  [CSVエクスポート]        │
│                                                      │
│ ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━      │
├─────────────────────────────────────────────────────┤
│ 処理結果                                             │
│ ┌───┬─────┬────┬────┬────┬────┬────┐          │
│ │Mod│言語 │英語│日本│翻訳│文字│結果│          │
│ │名 │フォ │ファ│語フ│実行│数  │    │          │
│ │   │ルダ │イル│ァイ│    │    │    │          │
│ │   │パス │    │ル  │    │    │    │          │
│ ├───┼─────┼────┼────┼────┼────┼────┤          │
│ │mod│asset│ ○ │ × │ ○ │1234│ ○ │          │
│ └───┴─────┴────┴────┴────┴────┴────┘          │
├─────────────────────────────────────────────────────┤
│ 処理ログ                                             │
│ ┌───────────────────────────────────────────┐       │
│ │[1/10][翻訳] mod1.jar - 翻訳完了 (1234文字)│       │
│ │[2/10][既存] mod2.jar - 日本語ファイル存在 │       │
│ └───────────────────────────────────────────┘       │
└─────────────────────────────────────────────────────┘
```

#### 1.3.2 設定画面
```
┌─────────────────────────────────────────┐
│ 翻訳設定                                 │
├─────────────────────────────────────────┤
│ 翻訳プロバイダー: [Google Translation▼] │
├─────────────────────────────────────────┤
│ APIキー設定                              │
│ ┌─────────────────────────────────────┐ │
│ │Google Translation API:               │ │
│ │ [____________________________]       │ │
│ │                                      │ │
│ │DeepL API:                            │ │
│ │ [____________________________]       │ │
│ │                                      │ │
│ │ChatGPT API:                          │ │
│ │ [____________________________]       │ │
│ │                                      │ │
│ │Claude API:                           │ │
│ │ [____________________________]       │ │
│ └─────────────────────────────────────┘ │
├─────────────────────────────────────────┤
│ API取得方法                              │
│ ┌─────────────────────────────────────┐ │
│ │【Google Translation API】            │ │
│ │Google Cloud Console → 認証情報作成   │ │
│ │...                                   │ │
│ └─────────────────────────────────────┘ │
│                                          │
│               [保存] [キャンセル]        │
└─────────────────────────────────────────┘
```

---

### 1.4 入出力仕様

#### 1.4.1 入力
**ディレクトリ構造**:
```
ModPackディレクトリ/
└── mods/
    ├── mod1.jar
    │   └── assets/mod1/lang/
    │       ├── en_us.json  (必須)
    │       └── ja_jp.json  (オプション)
    ├── mod2.jar
    └── mod3.jar
```

**言語ファイル形式** (en_us.json):
```json
{
  "item.modid.item_name": "Item Name",
  "block.modid.block_name": "Block Name"
}
```

#### 1.4.2 出力
**ディレクトリ構造**:
```
output/
├── MyJPpack/
│   └── assets/
│       ├── mod1/
│       │   └── lang/
│       │       ├── en_us.json  (コピー)
│       │       └── ja_jp.json  (翻訳済み/既存)
│       └── mod2/
│           └── lang/
│               ├── en_us.json
│               └── ja_jp.json
└── translation_results_20250101_120000.csv
```

**CSV出力形式**:
```csv
Mod名,言語フォルダパス,英語ファイル存在,日本語ファイル存在,翻訳実行,翻訳対象文字数,翻訳結果
mod1,assets/mod1/lang,○,×,○,1234,○
mod2,assets/mod2/lang,○,○,×,0,既存
```

---

### 1.5 エラー処理

| エラー種別 | 対応 |
|-----------|------|
| ModPackディレクトリ未選択 | ダイアログで警告表示 |
| modsフォルダ不存在 | ダイアログで警告表示 |
| APIキー未設定 | 設定画面への誘導 |
| API呼び出しエラー | ログに出力、該当Modはスキップ |
| ファイルI/Oエラー | ログに出力、処理継続 |
| JSON解析エラー | ログに出力、該当Modはスキップ |

---

## 2. 内部設計

### 2.1 システム構成

#### 2.1.1 アーキテクチャ
```
┌─────────────────────────────────────────┐
│          Presentation Layer              │
│  ┌──────────────┐  ┌─────────────┐     │
│  │ModPackTrans- │  │Settings     │     │
│  │latorGUI      │  │Dialog       │     │
│  └──────────────┘  └─────────────┘     │
├─────────────────────────────────────────┤
│          Business Logic Layer            │
│  ┌──────────────┐  ┌─────────────┐     │
│  │ModPack       │  │Translation  │     │
│  │Processor     │  │Service      │     │
│  └──────────────┘  └─────────────┘     │
├─────────────────────────────────────────┤
│          Data Access Layer               │
│  ┌──────────────┐  ┌─────────────┐     │
│  │CsvExporter   │  │Settings     │     │
│  │              │  │Persistence  │     │
│  └──────────────┘  └─────────────┘     │
└─────────────────────────────────────────┘
```

---

### 2.2 クラス設計

#### 2.2.1 クラス図
```
┌─────────────────────────┐
│  ModPackTranslatorGUI   │
├─────────────────────────┤
│ - translationService    │
│ - processingResults     │
├─────────────────────────┤
│ + main()                │
│ + startTranslation()    │
│ + openSettings()        │
│ + exportCsv()           │
└─────────────────────────┘
            │
            │ uses
            ↓
┌─────────────────────────┐
│  ModPackProcessor       │
├─────────────────────────┤
│ - translationService    │
│ - logger                │
│ - progressUpdater       │
├─────────────────────────┤
│ + process()             │
│ - processModJar()       │
│ - extractModId()        │
│ - countCharacters()     │
└─────────────────────────┘
            │
            │ uses
            ↓
┌─────────────────────────┐
│  TranslationService     │
├─────────────────────────┤
│ - apiKey                │
│ - provider              │
│ - gson                  │
├─────────────────────────┤
│ + translateJsonFile()   │
│ - translateWith...()    │
└─────────────────────────┘

┌─────────────────────────┐
│  ModProcessingResult    │
├─────────────────────────┤
│ + modName               │
│ + langFolderPath        │
│ + hasEnUs               │
│ + hasJaJp               │
│ + translated            │
│ + characterCount        │
│ + translationSuccess    │
└─────────────────────────┘

┌─────────────────────────┐
│  SettingsDialog         │
├─────────────────────────┤
│ - providerComboBox      │
│ - apiKeyFields          │
│ - settings              │
├─────────────────────────┤
│ + loadSettings()        │
│ + saveSettings()        │
│ + getApiKey()           │
└─────────────────────────┘

┌─────────────────────────┐
│  CsvExporter            │
├─────────────────────────┤
│ + export()              │
│ - escapeCSV()           │
└─────────────────────────┘
```

---

### 2.3 主要クラス詳細

#### 2.3.1 ModPackTranslatorGUI
**責務**: メインGUI、ユーザー操作の制御

**主要メソッド**:
- `startTranslation()`: 翻訳処理の開始
- `openSettings()`: 設定画面の表示
- `exportCsv()`: CSV出力
- `updateTable()`: 結果テーブルの更新

**使用パターン**: Swing MVC、Observer (SwingWorker)

---

#### 2.3.2 ModPackProcessor
**責務**: ModPackの解析と翻訳処理の統括

**処理フロー**:
```
1. modsディレクトリ内のJARファイルを列挙
2. 各JARファイルに対して:
   a. JARファイルを開く
   b. assets/*/lang/en_us.json を検索
   c. assets/*/lang/ja_jp.json の存在確認
   d. ja_jp.jsonが存在しない場合:
      - en_us.jsonを翻訳
      - 翻訳結果を保存
   e. ja_jp.jsonが存在する場合:
      - そのままコピー
3. 結果をリストで返却
```

**主要メソッド**:
- `process()`: 全体処理
- `processModJar(File, int, int)`: 個別Mod処理
- `extractModId(String)`: ModIDの抽出
- `countCharacters(String)`: 文字数カウント

---

#### 2.3.3 TranslationService
**責務**: 翻訳APIの統一インターフェース

**サポートプロバイダー**:
1. Google Translation API
2. DeepL API
3. ChatGPT API (GPT-4o-mini)
4. Claude API (Sonnet 4)

**処理方式**:
- **Google/DeepL**: バッチ処理 (128/50エントリーずつ)
- **ChatGPT/Claude**: ファイル一括処理

**主要メソッド**:
- `translateJsonFile(String, ProgressCallback)`: JSON翻訳
- `translateJsonFileWithGoogleBatch()`: Google翻訳
- `translateJsonFileWithDeepLBatch()`: DeepL翻訳
- `translateJsonFileWithChatGPT()`: ChatGPT翻訳
- `translateJsonFileWithClaude()`: Claude翻訳

---

#### 2.3.4 SettingsDialog
**責務**: 設定の入力と永続化

**設定項目**:
- 翻訳プロバイダー選択
- 各プロバイダーのAPIキー

**永続化形式**: Java Properties (translator_settings.properties)
```properties
provider=GOOGLE
google.apikey=AIza...
deepl.apikey=abc123...
chatgpt.apikey=sk-...
claude.apikey=sk-ant-...
```

---

### 2.4 データフロー

#### 2.4.1 翻訳処理のシーケンス
```
User → GUI → ModPackProcessor → TranslationService → API
                    ↓                    ↓
              ModProcessingResult    Translated JSON
                    ↓
              ResultTable/CSV
```

#### 2.4.2 詳細シーケンス図
```
[User] → [GUI]: 翻訳開始ボタンクリック
[GUI] → [GUI]: 入力検証
[GUI] → [SwingWorker]: バックグラウンド実行開始
[SwingWorker] → [ModPackProcessor]: process()
[ModPackProcessor] → [JAR]: JARファイル一覧取得
loop 各JARファイル
    [ModPackProcessor] → [JAR]: 言語ファイル抽出
    alt ja_jp.json存在
        [ModPackProcessor] → [Output]: ja_jp.jsonをコピー
    else ja_jp.json不存在
        [ModPackProcessor] → [TranslationService]: translateJsonFile()
        [TranslationService] → [API]: HTTP POST リクエスト
        [API] → [TranslationService]: 翻訳結果
        [TranslationService] → [ModPackProcessor]: 翻訳済みJSON
        [ModPackProcessor] → [Output]: ja_jp.json保存
    end
    [ModPackProcessor] → [GUI]: 進捗通知
end
[ModPackProcessor] → [SwingWorker]: 結果リスト返却
[SwingWorker] → [GUI]: 完了通知
[GUI] → [User]: 結果表示
```

---

### 2.5 API連携仕様

#### 2.5.1 Google Translation API
**エンドポイント**: `https://translation.googleapis.com/language/translate/v2`

**リクエスト形式**:
```json
{
  "q": ["Text 1", "Text 2", ...],
  "source": "en",
  "target": "ja",
  "format": "text"
}
```

**バッチサイズ**: 最大128テキスト/リクエスト

---

#### 2.5.2 DeepL API
**エンドポイント**: `https://api-free.deepl.com/v2/translate`

**リクエスト形式** (URLエンコード):
```
text=Text+1&text=Text+2&source_lang=EN&target_lang=JA
```

**バッチサイズ**: 最大50テキスト/リクエスト

---

#### 2.5.3 ChatGPT API
**エンドポイント**: `https://api.openai.com/v1/chat/completions`

**リクエスト形式**:
```json
{
  "model": "gpt-4o-mini",
  "temperature": 0.3,
  "messages": [
    {
      "role": "user",
      "content": "以下のJSON形式のMinecraft Mod言語ファイルを...\n{...}"
    }
  ]
}
```

**処理方式**: JSON全体を一括翻訳

---

#### 2.5.4 Claude API
**エンドポイント**: `https://api.anthropic.com/v1/messages`

**リクエスト形式**:
```json
{
  "model": "claude-sonnet-4-20250514",
  "max_tokens": 4096,
  "messages": [
    {
      "role": "user",
      "content": "以下のJSON形式のMinecraft Mod言語ファイルを...\n{...}"
    }
  ]
}
```

**処理方式**: JSON全体を一括翻訳

---

### 2.6 性能設計

#### 2.6.1 処理時間見積もり
**前提条件**: 100Mod、各Mod平均100エントリー

| プロバイダー | API呼び出し回数 | 推定処理時間 |
|------------|----------------|--------------|
| Google | 100回 (各1回) | 約2-3分 |
| DeepL | 200回 (各2回) | 約3-4分 |
| ChatGPT | 100回 (各1回) | 約3-5分 |
| Claude | 100回 (各1回) | 約2-3分 |

#### 2.6.2 料金見積もり
**前提条件**: 100Mod、各Mod平均100エントリー (合計10,000エントリー、約50万文字)

| プロバイダー | 料金 |
|------------|------|
| Google | 約$10 |
| DeepL | 約$12.5 |
| ChatGPT | 約$2.5 |
| Claude | 約$0.25 |

---

### 2.7 エラーハンドリング設計

#### 2.7.1 例外処理方針
```java
try {
    // Mod処理
} catch (IOException e) {
    // ログ出力、該当Modをスキップ、処理継続
} catch (JsonSyntaxException e) {
    // ログ出力、該当Modをスキップ、処理継続
} catch (Exception e) {
    // ログ出力、エラー結果を記録、処理継続
}
```

#### 2.7.2 リトライ設計
- API呼び出し失敗時: リトライなし（該当Modを失敗扱い）
- 理由: 複数Mod処理のため、1つの失敗で全体を停止しない

---

### 2.8 拡張性設計

#### 2.8.1 翻訳プロバイダーの追加
**手順**:
1. `TranslationProvider` enumに追加
2. `translateJsonFile()` のswitchに追加
3. 実装メソッドを作成
4. `SettingsDialog` のAPIキー入力欄を追加

#### 2.8.2 出力形式の追加
**現在**: ResourcePack形式のみ

**拡張可能性**:
- Mod JARファイルへの直接統合
- 複数言語への同時翻訳
- 翻訳メモリ機能

---

## 3. ファイル構成

```
src/
├── ModPackTranslatorGUI.java      (メインGUI)
├── ModPackProcessor.java          (処理ロジック)
├── TranslationService.java        (翻訳API統合)
├── SettingsDialog.java            (設定画面)
├── CsvExporter.java               (CSV出力)
└── ModProcessingResult.java       (データクラス)

output/
├── MyJPpack/                      (翻訳結果)
└── translation_results_*.csv      (処理結果CSV)

translator_settings.properties     (設定ファイル)
```

---

## 4. 開発・運用情報

### 4.1 開発環境
- **言語**: Java 1.8
- **ビルドツール**: Maven
- **必須ライブラリ**: Gson 2.10.1

### 4.2 依存ライブラリ
```xml
<dependency>
    <groupId>com.google.code.gson</groupId>
    <artifactId>gson</artifactId>
    <version>2.10.1</version>
</dependency>
```

### 4.3 ビルド・実行方法
```bash
# コンパイル
mvn clean compile

# 実行
mvn exec:java -Dexec.mainClass="ModPackTranslatorGUI"

# パッケージング
mvn package
```

---

## 5. 今後の改善案

1. **翻訳用語辞書機能**: カスタム用語の統一
2. **差分更新機能**: ModPack更新時の効率化
3. **翻訳レビュー機能**: 翻訳結果の手動修正
4. **バッチ処理機能**: 複数ModPackの一括処理
5. **翻訳キャッシュ**: 同一テキストの再翻訳防止

---

**文書バージョン**: 1.0  
**最終更新日**: 2025年11月  
**作成者**: Claude AI