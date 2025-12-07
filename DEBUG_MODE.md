# デバッグモード使用方法

## 概要

API呼び出しを行わずに進捗表示のみをテストできるデバッグモードを実装しました。

## 使用方法

### 1. コードでデバッグモードを有効化

`TranslationService`を使用している箇所で、デバッグモードを有効化します。

```java
TranslationService translationService = new TranslationService();
translationService.setApiKey("dummy-key"); // ダミーキーでOK
translationService.setProvider(ProviderType.CLAUDE);

// デバッグモードを有効化
translationService.setDebugMode(true);
```

### 2. 環境変数での有効化（推奨）

アプリケーション起動時に環境変数を確認して自動的に有効化する方法：

```java
// メインクラスまたは初期化処理で
String debugMode = System.getenv("TRANSLATION_DEBUG_MODE");
if ("true".equalsIgnoreCase(debugMode)) {
    translationService.setDebugMode(true);
    System.out.println("デバッグモードが有効です - API呼び出しはスキップされます");
}
```

実行時：
```bash
# Windows
set TRANSLATION_DEBUG_MODE=true
java -jar MinecraftModpackTranslator.jar

# Linux/Mac
TRANSLATION_DEBUG_MODE=true java -jar MinecraftModpackTranslator.jar
```

## デバッグモードの動作

### API呼び出しをスキップ
- 実際のAPI（Claude, ChatGPT, Google, DeepL）を呼び出しません
- 課金されません

### 進捗表示をシミュレート
- 各バッチ処理で200-500msの待機時間を設けて実際のAPIレスポンスをシミュレート
- 進捗コールバックは正常に動作
- UIの進捗表示（例: 翻訳中 50/100）が確認できる

### ダミーデータを返却
- 翻訳結果として「[デバッグ] 元のテキスト」を返します
- 翻訳が正常に完了したかどうかの確認が可能

## テスト手順

1. デバッグモードを有効化
2. 翻訳を実行
3. 以下を確認：
   - 状態列に「翻訳中 (X/Y)」と表示されるか
   - 進捗が正しく更新されるか
   - 翻訳完了後に「○」と表示されるか
   - 出力ファイルに「[デバッグ]」プレフィックス付きのテキストが含まれるか

## 注意事項

- デバッグモードは**テスト目的のみ**で使用してください
- 本番環境では必ずデバッグモードを無効化してください
- デバッグモードで生成されたファイルは実際の翻訳ではありません

## 各プロバイダーのシミュレート時間

- **Claude/ChatGPT**: 500ms/バッチ
- **Google/DeepL**: 200ms/バッチ

これにより、並列処理の効果も確認できます。
