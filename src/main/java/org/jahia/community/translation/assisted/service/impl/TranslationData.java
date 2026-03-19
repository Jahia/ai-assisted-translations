package org.jahia.community.translation.assisted.service.impl;

import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.StringUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class TranslationData {

    private final Map<String, String> texts = new HashMap<>();
    private final Map<String, String> duplicates = new HashMap<>();

    public boolean hasTextToTranslate() {
        return !MapUtils.isEmpty(texts);
    }

    public Map<String, String> getTexts() {
        return Collections.unmodifiableMap(texts);
    }

    public void trackText(String key, String text) {
        final Optional<Map.Entry<String, String>> duplicate = texts.entrySet().stream()
                .filter(e -> StringUtils.equals(text, e.getValue()))
                .findFirst();
        if (duplicate.isPresent()) {
            duplicates.put(key, duplicate.get().getKey());
        } else {
            texts.put(key, text);
        }
    }

    public Map<String, String> completeTranslations(Map<String, String> translations) {
        duplicates.forEach((key, ref) -> translations.put(key, translations.get(ref)));
        return translations;
    }

    public Map<String, String> getDuplicates() {
        return duplicates;
    }
}
