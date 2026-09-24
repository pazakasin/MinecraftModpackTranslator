package io.github.pazakasin.minecraft.modpack.translator.service.processor;

/**
 * JAR内の1つのnamespace（assets/&lt;namespace&gt;/lang）の言語ファイル情報を保持するデータクラス。
 * 1つのJARが複数のnamespaceを持つ場合、namespaceごとに1インスタンスを生成する。
 */
public class LanguageFileInfo {
    /** Mod ID（assets配下のディレクトリ名＝namespace）。 */
    public String modId;
    
    /** 言語ファイルのパス（例: "assets/examplemod/lang/en_us.json"）。 */
    public String langFolderPath;
    
    /** en_us.jsonファイルの内容。存在しない場合はnull。 */
    public String enUsContent;
    
    /** ja_jp.jsonファイルの内容。存在しない場合はnull。 */
    public String jaJpContent;
    
    /** en_us.jsonファイルが存在するか。 */
    public boolean hasEnUs;
    
    /** ja_jp.jsonファイルが存在するか。 */
    public boolean hasJaJp;
}
