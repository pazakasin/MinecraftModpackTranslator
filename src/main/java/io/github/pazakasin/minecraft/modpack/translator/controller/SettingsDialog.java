package io.github.pazakasin.minecraft.modpack.translator.controller;

import io.github.pazakasin.minecraft.modpack.translator.service.TranslationService;
import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.util.Properties;

/**
 * 設定ダイアログ - 翻訳プロバイダーとAPIキーの設定
 */
public class SettingsDialog extends JDialog {
    private JComboBox<TranslationService.TranslationProvider> providerComboBox;
    private JTextField googleApiKeyField;
    private JTextField deeplApiKeyField;
    private JTextField chatgptApiKeyField;
    private JTextField claudeApiKeyField;
    private Properties settings;
    private static final String SETTINGS_FILE = "translator_settings.properties";
    
    private boolean saved = false;
    
    public SettingsDialog(Frame parent) {
        super(parent, "翻訳設定", true);
        setSize(600, 400);
        setLocationRelativeTo(parent);
        
        settings = loadSettings();
        initComponents();
        loadCurrentSettings();
    }
    
    private void initComponents() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        
        // プロバイダー選択パネル
        JPanel providerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        providerPanel.add(new JLabel("翻訳プロバイダー:"));
        providerComboBox = new JComboBox<>(TranslationService.TranslationProvider.values());
        providerComboBox.setRenderer(new DefaultListCellRenderer() {
            public Component getListCellRendererComponent(JList<?> list, Object value, 
                    int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof TranslationService.TranslationProvider) {
                    setText(((TranslationService.TranslationProvider) value).getDisplayName());
                }
                return this;
            }
        });
        providerPanel.add(providerComboBox);
        
        // APIキー入力パネル
        JPanel apiKeyPanel = new JPanel(new GridBagLayout());
        apiKeyPanel.setBorder(BorderFactory.createTitledBorder("APIキー設定"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 5, 5, 5);
        
        // Google Translation API
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        apiKeyPanel.add(new JLabel("Google Translation API:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        googleApiKeyField = new JTextField(30);
        apiKeyPanel.add(googleApiKeyField, gbc);
        
        // DeepL API
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        apiKeyPanel.add(new JLabel("DeepL API:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        deeplApiKeyField = new JTextField(30);
        apiKeyPanel.add(deeplApiKeyField, gbc);
        
        // ChatGPT API
        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0;
        apiKeyPanel.add(new JLabel("ChatGPT API:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        chatgptApiKeyField = new JTextField(30);
        apiKeyPanel.add(chatgptApiKeyField, gbc);
        
        // Claude API
        gbc.gridx = 0; gbc.gridy = 3; gbc.weightx = 0;
        apiKeyPanel.add(new JLabel("Claude API:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        claudeApiKeyField = new JTextField(30);
        apiKeyPanel.add(claudeApiKeyField, gbc);
        
        // 説明パネル
        JPanel infoPanel = new JPanel(new BorderLayout());
        infoPanel.setBorder(BorderFactory.createTitledBorder("API取得方法"));
        JTextArea infoArea = new JTextArea(
            "【Google Translation API】\n" +
            "Google Cloud Console → Cloud Translation API を有効化 → APIキーを作成\n\n" +
            "【DeepL API】\n" +
            "https://www.deepl.com/pro-api → 登録 → 認証キーを取得\n\n" +
            "【ChatGPT API】\n" +
            "https://platform.openai.com/ → API keys → Create new secret key\n\n" +
            "【Claude API】\n" +
            "https://console.anthropic.com/ → Get API keys → Create Key"
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
        
        // レイアウト
        JPanel topPanel = new JPanel(new BorderLayout(5, 5));
        topPanel.add(providerPanel, BorderLayout.NORTH);
        topPanel.add(apiKeyPanel, BorderLayout.CENTER);
        
        mainPanel.add(topPanel, BorderLayout.NORTH);
        mainPanel.add(infoPanel, BorderLayout.CENTER);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);
        
        add(mainPanel);
    }
    
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
    
    private void loadCurrentSettings() {
        String providerName = settings.getProperty("provider", "GOOGLE");
        try {
            TranslationService.TranslationProvider provider = 
                TranslationService.TranslationProvider.valueOf(providerName);
            providerComboBox.setSelectedItem(provider);
        } catch (IllegalArgumentException e) {
            providerComboBox.setSelectedIndex(0);
        }
        
        googleApiKeyField.setText(settings.getProperty("google.apikey", ""));
        deeplApiKeyField.setText(settings.getProperty("deepl.apikey", ""));
        chatgptApiKeyField.setText(settings.getProperty("chatgpt.apikey", ""));
        claudeApiKeyField.setText(settings.getProperty("claude.apikey", ""));
    }
    
    private void saveSettings() {
        TranslationService.TranslationProvider selectedProvider = 
            (TranslationService.TranslationProvider) providerComboBox.getSelectedItem();
        
        settings.setProperty("provider", selectedProvider.name());
        settings.setProperty("google.apikey", googleApiKeyField.getText().trim());
        settings.setProperty("deepl.apikey", deeplApiKeyField.getText().trim());
        settings.setProperty("chatgpt.apikey", chatgptApiKeyField.getText().trim());
        settings.setProperty("claude.apikey", claudeApiKeyField.getText().trim());
        
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
    
    public boolean isSaved() {
        return saved;
    }
    
    public TranslationService.TranslationProvider getSelectedProvider() {
        return (TranslationService.TranslationProvider) providerComboBox.getSelectedItem();
    }
    
    public String getApiKey(TranslationService.TranslationProvider provider) {
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
}