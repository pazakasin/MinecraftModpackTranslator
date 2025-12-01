package io.github.pazakasin.minecraft.modpack.translator.service.quest;

import net.querz.nbt.tag.*;
import java.util.*;

public class LangFileSNBTExtractor {
    public static class ExtractedText {
        private final String key;
        private final String value;
        private final boolean isList;

        public ExtractedText(String key, String value, boolean isList) {
            this.key = key;
            this.value = value;
            this.isList = isList;
        }

        public String getKey() { return key; }
        public String getValue() { return value; }
        public boolean isList() { return isList; }
    }

    public List<ExtractedText> extract(Tag<?> rootTag) {
        List<ExtractedText> texts = new ArrayList<>();
        if (!(rootTag instanceof CompoundTag)) return texts;

        CompoundTag root = (CompoundTag) rootTag;
        for (String key : root.keySet()) {
            if (isTranslatableKey(key)) {
                Tag<?> valueTag = root.get(key);
                if (valueTag instanceof StringTag) {
                    texts.add(new ExtractedText(key, ((StringTag) valueTag).getValue(), false));
                } else if (valueTag instanceof ListTag) {
                    String combined = extractListContent((ListTag<?>) valueTag);
                    texts.add(new ExtractedText(key, combined, true));
                }
            }
        }
        return texts;
    }

    private boolean isTranslatableKey(String key) {
        return (key.startsWith("quest.") && (key.endsWith(".title") || key.endsWith(".quest_desc")))
            || (key.startsWith("task.") && key.endsWith(".title"));
    }

    private String extractListContent(ListTag<?> listTag) {
        List<String> parts = new ArrayList<>();
        for (Tag<?> tag : listTag) {
            if (tag instanceof StringTag) {
                parts.add(((StringTag) tag).getValue());
            }
        }
        return String.join("\n", parts);
    }

    public void applyTranslation(Tag<?> rootTag, Map<String, String> translations) {
        if (!(rootTag instanceof CompoundTag)) return;
        CompoundTag root = (CompoundTag) rootTag;

        for (String key : translations.keySet()) {
            if (root.containsKey(key)) {
                String translated = translations.get(key);
                Tag<?> originalTag = root.get(key);

                if (originalTag instanceof ListTag) {
                    String[] lines = translated.split("\n");
                    ListTag<StringTag> newList = new ListTag<>(StringTag.class);
                    for (String line : lines) {
                        newList.add(new StringTag(line));
                    }
                    root.put(key, newList);
                } else {
                    root.putString(key, translated);
                }
            }
        }
    }
}
