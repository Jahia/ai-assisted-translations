package org.jahia.community.translation.assisted.service.impl;

import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public class TranslationData {

    private static final Pattern VALUE_INDEX_SUFFIX = Pattern.compile("___\\d+___$");

    private final Map<String, String> texts = new HashMap<>();
    private final Map<String, String> duplicates = new HashMap<>();
    private final Map<String, String> copiedReferences = new HashMap<>();

    public boolean hasTextToTranslate() {
        return !MapUtils.isEmpty(texts);
    }

    public boolean hasTextToCopy() {
        return !MapUtils.isEmpty(copiedReferences);
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
        Map<String, String> result = new HashMap<>(translations);
        duplicates.forEach((key, ref) -> result.put(key, result.get(ref)));
        result.putAll(copiedReferences);
        return result;
    }

    public void trackCopiedValue(String key, String text) {
        copiedReferences.put(key, text);
    }

    /**
     * The property a key or a field name belongs to: the last path segment, without the
     * <code>___n___</code> suffix that marks one value of a multiple property.
     */
    public static String propertyNameOf(String key) {
        final String name = key.contains("/") ? StringUtils.substringAfterLast(key, "/") : key;
        return VALUE_INDEX_SUFFIX.matcher(name).replaceFirst("");
    }

    /**
     * Keeps only the texts of the given properties, so that nothing else is sent to the translation
     * service, which bills by what it is sent.
     *
     * A text that was tracked as a duplicate of another one is not sent twice. When a kept property
     * duplicates the text of a dropped one, that text has to be sent after all, once, under the kept
     * property.
     */
    public void retainProperties(Collection<String> propertyNames) {
        final Set<String> wanted = new HashSet<>(propertyNames);
        final Predicate<String> keep = key -> wanted.contains(propertyNameOf(key));

        final Map<String, String> promotedTexts = new HashMap<>();
        final Map<String, String> promotedKeyByDroppedKey = new HashMap<>();
        final Map<String, String> keptDuplicates = new HashMap<>();
        duplicates.forEach((key, ref) -> {
            if (!keep.test(key)) {
                return;
            }
            if (keep.test(ref)) {
                keptDuplicates.put(key, ref);
                return;
            }
            final String promotedKey = promotedKeyByDroppedKey.get(ref);
            if (promotedKey == null) {
                promotedKeyByDroppedKey.put(ref, key);
                promotedTexts.put(key, texts.get(ref));
            } else {
                keptDuplicates.put(key, promotedKey);
            }
        });

        texts.keySet().removeIf(key -> !keep.test(key));
        texts.putAll(promotedTexts);
        duplicates.clear();
        duplicates.putAll(keptDuplicates);
        copiedReferences.keySet().removeIf(key -> !keep.test(key));
    }

}
