package io.github.pazakasin.minecraft.modpack.translator.controller;

import io.github.pazakasin.minecraft.modpack.translator.service.TranslationService;
import io.github.pazakasin.minecraft.modpack.translator.service.ProviderType;
import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.util.Properties;

/**
 * 翻訳プロバイダーとAPIキーを設定するダイアログ。
 * 各プロバイダーのAPIキー入力とAPI取得方法の案内を提供。
 */
public class SettingsDialog extends JDialog {
    /** 翻訳プロバイダー選択用のコンボボックス */
    private JComboBox<ProviderType> providerComboBox;
    /** Google Translation API のAPIキー入力フィールド */
    private JTextField googleApiKeyField;
    /** DeepL API のAPIキー入力フィールド */
    private JTextField deeplApiKeyField;
    /** ChatGPT API のAPIキー入力フィールド */
    private JTextField chatgptApiKeyField;
    /** Claude API のAPIキー入力フィールド */
    private JTextField claudeApiKeyField;
    /** 翻訳プロンプト入力フィールド */
    private JTextArea translationPromptField;
    /** pack_format入力フィールド */
    private JTextField packFormatField;
    /** デバッグモードチェックボックス */
    private JCheckBox debugModeCheckBox;
    /** 設定情報を保持するPropertiesオブジェクト */
    private Properties settings;
    /** 設定ファイルのパス */
    private static final String SETTINGS_FILE = "translator_settings.properties";
    /** ユーザーが設定を保存したかどうか */
    private boolean saved = false;
    
    /** デフォルト翻訳プロンプト */
    private static final String DEFAULT_TRANSLATION_PROMPT =
            "以下のJSON形式のMinecraft言語ファイルを英語から日本語に翻訳してください。\n" +
            "これはMinecraft ModまたはFTB Questsのテキストです。\n" +
            "キー名はそのまま保持し、値のみを翻訳してください。\n" +
            "アイテム名、クエストタイトル、説明文など、文脈に応じて適切に翻訳してください。\n" +
            "JSONフォーマットのみを返してください。説明文は不要です。\n\n{jsonContent}";
    
    /**
     * SettingsDialogのコンストラクタ。
     * @param parent 親フレーム
     */
    public SettingsDialog(Frame parent) {
        super(parent, "設定画面", true);
        setSize(700, 680);
        setLocationRelativeTo(parent);
        
        settings = loadSettings();
        initComponents();
        loadCurrentSettings();
    }
    
    /** UIコンポーネントを初期化し、レイアウトを構築します。 */
    private void initComponents() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        
        // プロバイダー選択パネル
        JPanel providerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        providerPanel.add(new JLabel("翻訳プロバイダー:"));
        providerComboBox = new JComboBox<>(ProviderType.values());
        providerComboBox.setRenderer(new DefaultListCellRenderer() {
            public Component getListCellRendererComponent(JList<?> list, Object value, 
                    int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof ProviderType) {
                    setText(((ProviderType) value).getDisplayName());
                }
                return this;
            }
        });
        providerPanel.add(providerComboBox);
        
        // APIキーパネル
        JPanel apiKeyPanel = new JPanel(new GridBagLayout());
        apiKeyPanel.setBorder(BorderFactory.createTitledBorder("APIキー"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 5, 5, 5);
        
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        apiKeyPanel.add(new JLabel("Google Translation API:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        googleApiKeyField = new JTextField(30);
        apiKeyPanel.add(googleApiKeyField, gbc);
        
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        apiKeyPanel.add(new JLabel("DeepL API:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        deeplApiKeyField = new JTextField(30);
        apiKeyPanel.add(deeplApiKeyField, gbc);
        
        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0;
        apiKeyPanel.add(new JLabel("ChatGPT API:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        chatgptApiKeyField = new JTextField(30);
        apiKeyPanel.add(chatgptApiKeyField, gbc);
        
        gbc.gridx = 0; gbc.gridy = 3; gbc.weightx = 0;
        apiKeyPanel.add(new JLabel("Claude API:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        claudeApiKeyField = new JTextField(30);
        apiKeyPanel.add(claudeApiKeyField, gbc);
        
        // pack_formatパネル
        JPanel packFormatPanel = new JPanel(new GridBagLayout());
        packFormatPanel.setBorder(BorderFactory.createTitledBorder("リソースパック設定"));
        GridBagConstraints gbc2 = new GridBagConstraints();
        gbc2.fill = GridBagConstraints.HORIZONTAL;
        gbc2.insets = new Insets(5, 5, 5, 5);
        
        gbc2.gridx = 0; gbc2.gridy = 0; gbc2.weightx = 0;
        packFormatPanel.add(new JLabel("pack_format:"), gbc2);
        gbc2.gridx = 1; gbc2.weightx = 1.0;
        packFormatField = new JTextField(5);
        packFormatPanel.add(packFormatField, gbc2);
        
        // デバッグモード設定
        gbc2.gridx = 0; gbc2.gridy = 1; gbc2.weightx = 0; gbc2.gridwidth = 2;
        debugModeCheckBox = new JCheckBox("デバッグモード（API呼び出しをスキップし、進捗表示のみテスト）");
        debugModeCheckBox.setFont(new Font("Dialog", Font.PLAIN, 11));
        packFormatPanel.add(debugModeCheckBox, gbc2);
        gbc2.gridwidth = 1;
        
        // 翻訳プロンプトパネル
        JPanel promptPanel = new JPanel(new BorderLayout(5, 5));
        promptPanel.setBorder(BorderFactory.createTitledBorder("翻訳プロンプト（ChatGPT/Claude用）"));
        
        translationPromptField = new JTextArea(5, 50);
        translationPromptField.setLineWrap(true);
        translationPromptField.setWrapStyleWord(true);
        translationPromptField.setFont(new Font("Dialog", Font.PLAIN, 12));
        
        JScrollPane promptScrollPane = new JScrollPane(translationPromptField);
        promptPanel.add(promptScrollPane, BorderLayout.CENTER);
        
        JLabel promptHint = new JLabel("※{jsonContent}がJSONデータに置換されます。空欄の場合はデフォルトプロンプトを使用します。");
        promptHint.setFont(new Font("Dialog", Font.PLAIN, 10));
        promptPanel.add(promptHint, BorderLayout.SOUTH);
        
        // 説明パネル
        JPanel infoPanel = new JPanel(new BorderLayout());
        infoPanel.setBorder(BorderFactory.createTitledBorder("設定方法"));
        JTextArea infoArea = new JTextArea(
            "【Google Translation API】\n" +
            "https://console.cloud.google.com/\n\n" +
            "【DeepL API】\n" +
            "https://www.deepl.com/pro-api\n\n" +
            "【ChatGPT API】\n" +
            "https://platform.openai.com/\n\n" +
            "【Claude API】\n" +
            "https://console.anthropic.com/\n\n" +
            "【pack_format】\n" +
            "リソースパックのpack_format値を指定します。\n" +
            "主なMinecraftバージョンとの対応: 1.20.2-1.20.4=15, 1.20.5-1.20.6=18, 1.21-1.21.1=34\n" +
            "※値が異なる場合、リソースパック適用時に警告が出ますが、強制適用可能です。"
        );
        infoArea.setEditable(false);
        infoArea.setLineWrap(true);
        infoArea.setWrapStyleWord(true);
        infoArea.setFont(new Font("Dialog", Font.PLAIN, 11));
        infoArea.setBackground(new Color(245, 245, 245));
        JScrollPane scrollPane = new JScrollPane(infoArea);
        infoPanel.add(scrollPane, BorderLayout.CENTER);
        
        // ボタンパネル
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton saveButton = new JButton("保存");
        JButton cancelButton = new JButton("キャンセル");
        
        saveButton.addActionListener(e -> saveSettings());
        cancelButton.addActionListener(e -> dispose());
        
        buttonPanel.add(saveButton);
        buttonPanel.add(cancelButton);
        
        JPanel topPanel = new JPanel(new BorderLayout(5, 5));
        topPanel.add(providerPanel, BorderLayout.NORTH);
        
        JPanel settingsPanel = new JPanel(new BorderLayout(5, 5));
        settingsPanel.add(apiKeyPanel, BorderLayout.NORTH);
        
        JPanel lowerSettings = new JPanel(new BorderLayout(5, 5));
        lowerSettings.add(packFormatPanel, BorderLayout.NORTH);
        lowerSettings.add(promptPanel, BorderLayout.CENTER);
        
        settingsPanel.add(lowerSettings, BorderLayout.CENTER);
        
        topPanel.add(settingsPanel, BorderLayout.CENTER);
        
        mainPanel.add(topPanel, BorderLayout.NORTH);
        mainPanel.add(infoPanel, BorderLayout.CENTER);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);
        
        add(mainPanel);
    }
    
    /** 設定ファイルから設定を読み込みます。 */
    private Properties loadSettings() {
        Properties props = new Properties();
        File settingsFile = new File(SETTINGS_FILE);
        
        if (settingsFile.exists()) {
            try (FileInputStream fis = new FileInputStream(settingsFile)) {
                props.load(fis);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        
        return props;
    }
    
    /** 保存された設定をUIコンポーネントに反映します。 */
    private void loadCurrentSettings() {
        String providerName = settings.getProperty("provider", "GOOGLE");
        try {
            ProviderType provider = ProviderType.valueOf(providerName);
            providerComboBox.setSelectedItem(provider);
        } catch (IllegalArgumentException e) {
            providerComboBox.setSelectedIndex(0);
        }
        
        googleApiKeyField.setText(settings.getProperty("google.apikey", ""));
        deeplApiKeyField.setText(settings.getProperty("deepl.apikey", ""));
        chatgptApiKeyField.setText(settings.getProperty("chatgpt.apikey", ""));
        claudeApiKeyField.setText(settings.getProperty("claude.apikey", ""));
        packFormatField.setText(settings.getProperty("pack_format", "15"));
        
        // デバッグモード設定を読み込み
        boolean debugMode = Boolean.parseBoolean(settings.getProperty("debug_mode", "false"));
        debugModeCheckBox.setSelected(debugMode);
        
        String storedPrompt = settings.getProperty("translation.prompt", "");
        translationPromptField.setText(storedPrompt.isEmpty() ? DEFAULT_TRANSLATION_PROMPT : storedPrompt);
    }
    
    /** 現在の設定内容を設定ファイルに保存します。 */
    private void saveSettings() {
        ProviderType selectedProvider = (ProviderType) providerComboBox.getSelectedItem();
        
        settings.setProperty("provider", selectedProvider.name());
        settings.setProperty("google.apikey", googleApiKeyField.getText().trim());
        settings.setProperty("deepl.apikey", deeplApiKeyField.getText().trim());
        settings.setProperty("chatgpt.apikey", chatgptApiKeyField.getText().trim());
        settings.setProperty("claude.apikey", claudeApiKeyField.getText().trim());
        
        String promptText = translationPromptField.getText().trim();
        settings.setProperty("translation.prompt", promptText.isEmpty() ? DEFAULT_TRANSLATION_PROMPT : promptText);
        
        String packFormatValue = packFormatField.getText().trim();
        if (packFormatValue.isEmpty()) {
            packFormatValue = "15";
        }
        settings.setProperty("pack_format", packFormatValue);
        
        // デバッグモード設定を保存
        settings.setProperty("debug_mode", String.valueOf(debugModeCheckBox.isSelected()));
        
        try (FileOutputStream fos = new FileOutputStream(SETTINGS_FILE)) {
            settings.store(fos, "Translation Service Settings");
            saved = true;
            JOptionPane.showMessageDialog(this, 
                "設定を保存しました。", "成功", JOptionPane.INFORMATION_MESSAGE);
            dispose();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, 
                "設定の保存に失敗しました: " + e.getMessage(), 
                "エラー", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    /**
     * ユーザーが設定を保存したかどうかを返します。
     * @return 保存した場合true
     */
    public boolean isSaved() {
        return saved;
    }
    
    /**
     * 選択された翻訳プロバイダーを取得します。
     * @return 選択されたプロバイダータイプ
     */
    public ProviderType getSelectedProvider() {
        return (ProviderType) providerComboBox.getSelectedItem();
    }
    
    /**
     * 指定されたプロバイダーのAPIキーを取得します。
     * @param provider プロバイダータイプ
     * @return APIキー（未設定時は空文字列）
     */
    public String getApiKey(ProviderType provider) {
        switch (provider) {
            case GOOGLE:
                return settings.getProperty("google.apikey", "");
            case DEEPL:
                return settings.getProperty("deepl.apikey", "");
            case CHATGPT:
                return settings.getProperty("chatgpt.apikey", "");
            case CLAUDE:
                return settings.getProperty("claude.apikey", "");
            default:
                return "";
        }
    }
    
    /**
     * 設定ファイルから設定を読み込む静的メソッド。
     * @return 読み込まれたPropertiesオブジェクト
     */
    public static Properties getStoredSettings() {
        Properties props = new Properties();
        File settingsFile = new File(SETTINGS_FILE);
        
        if (settingsFile.exists()) {
            try (FileInputStream fis = new FileInputStream(settingsFile)) {
                props.load(fis);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        
        return props;
    }
    
    /**
     * デバッグモードが有効かどうかを取得します。
     * @return デバッグモード有効時true
     */
    public static boolean isDebugMode() {
        Properties props = getStoredSettings();
        return Boolean.parseBoolean(props.getProperty("debug_mode", "false"));
    }
}
