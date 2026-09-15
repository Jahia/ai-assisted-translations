package org.jahia.community.translation.assisted.service.impl.deepl;

import com.deepl.api.DeepLClient;
import com.deepl.api.DeepLException;
import com.deepl.api.GlossaryNotFoundException;
import com.deepl.api.MultilingualGlossaryInfo;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component(service = DeepLGlossaryManager.class, immediate = true)
public class DeepLGlossaryManager {

    private static final Logger logger = LoggerFactory.getLogger(DeepLGlossaryManager.class);
    private static final String GLOSSARY_NAME_PREFIX = "jahia-ai-assisted-translations";
    private static final long GLOSSARY_MAX_AGE_MILLIS = Duration.ofDays(30).toMillis();
    private static final long GLOSSARY_CLEANUP_INTERVAL_MILLIS = Duration.ofHours(12).toMillis();

    private final Map<String, CachedGlossary> glossaryCache = new ConcurrentHashMap<>();
    private volatile long lastGlossaryCleanupTimeMillis;

    public void reset() {
        synchronized (this) {
            glossaryCache.clear();
            lastGlossaryCleanupTimeMillis = 0L;
        }
    }

    public synchronized String getOrCreateGlossaryId(DeepLClient translator, String siteKey, String srcLanguage, String destLanguage, Map<String, String> glossaryTerms) throws InterruptedException, DeepLException {
        if (translator == null || MapUtils.isEmpty(glossaryTerms)) {
            return null;
        }

        // DeepL rejects the whole glossary ("Bad request. Reason: Invalid entries") if a single entry
        // breaks its constraints, so normalize and drop offending entries before uploading. Per
        // https://developers.deepl.com/docs/customize/managing-glossaries an entry must have a unique
        // source term, a non-empty source and target, no control characters, and no leading/trailing
        // whitespace (including non-breaking spaces, which String.trim() leaves in place).
        Map<String, String> sanitizedTerms = sanitizeGlossaryTerms(glossaryTerms);
        if (MapUtils.isEmpty(sanitizedTerms)) {
            return null;
        }

        cleanupStaleGlossariesIfNeeded(translator, false);

        // Scope the glossary by site so that two sites sharing a language pair do not overwrite each
        // other's remote glossary (the cache key and the DeepL glossary name both include the site).
        String site = sanitizeNamePart(siteKey);
        String glossaryKey = site + "|" + srcLanguage + "|" + destLanguage;
        String glossaryHash = computeGlossaryHash(sanitizedTerms);
        CachedGlossary cachedGlossary = glossaryCache.get(glossaryKey);
        if (cachedGlossary != null && StringUtils.equals(cachedGlossary.hash, glossaryHash)) {
            return cachedGlossary.glossaryId;
        }

        String glossaryName = buildGlossaryName(site, srcLanguage, destLanguage);
        String csvContent = buildGlossaryCsvContent(sanitizedTerms);

        String glossaryId = cachedGlossary != null ? cachedGlossary.glossaryId : findGlossaryIdByName(translator, glossaryName);
        if (StringUtils.isBlank(glossaryId)) {
            MultilingualGlossaryInfo created = translator.createMultilingualGlossaryFromCsv(glossaryName, srcLanguage, destLanguage, csvContent);
            glossaryId = created.getGlossaryId();
        } else {
            try {
                translator.replaceMultilingualGlossaryDictionaryFromCsv(glossaryId, srcLanguage, destLanguage, csvContent);
            } catch (GlossaryNotFoundException e) {
                MultilingualGlossaryInfo created = translator.createMultilingualGlossaryFromCsv(glossaryName, srcLanguage, destLanguage, csvContent);
                glossaryId = created.getGlossaryId();
            }
        }

        glossaryCache.put(glossaryKey, new CachedGlossary(glossaryId, glossaryHash));
        return glossaryId;
    }

    public void cleanupStaleGlossariesIfNeeded(DeepLClient translator, boolean force) {
        if (translator == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (!force && now - lastGlossaryCleanupTimeMillis < GLOSSARY_CLEANUP_INTERVAL_MILLIS) {
            return;
        }

        synchronized (this) {
            long synchronizedNow = System.currentTimeMillis();
            if (!force && synchronizedNow - lastGlossaryCleanupTimeMillis < GLOSSARY_CLEANUP_INTERVAL_MILLIS) {
                return;
            }

            Set<String> protectedGlossaryIds = glossaryCache.values().stream()
                    .map(cached -> cached.glossaryId)
                    .filter(StringUtils::isNotBlank)
                    .collect(Collectors.toSet());
            try {
                List<MultilingualGlossaryInfo> glossaryInfos = translator.listMultilingualGlossaries().stream().filter(info -> StringUtils.startsWith(info.getName(), GLOSSARY_NAME_PREFIX) || protectedGlossaryIds.contains(info.getGlossaryId())).collect(Collectors.toList());
                for (MultilingualGlossaryInfo glossaryInfo : glossaryInfos) {
                    Date creationTime = glossaryInfo.getCreationTime();
                    if (creationTime == null || synchronizedNow - creationTime.getTime() < GLOSSARY_MAX_AGE_MILLIS) {
                        continue;
                    }
                    translator.deleteMultilingualGlossary(glossaryInfo);
                    logger.info("Deleted stale DeepL glossary {} ({})", glossaryInfo.getName(), glossaryInfo.getGlossaryId());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("Interrupted while cleaning stale DeepL glossaries", e);
            } catch (DeepLException e) {
                logger.warn("Unable to clean stale DeepL glossaries", e);
            } finally {
                lastGlossaryCleanupTimeMillis = synchronizedNow;
            }
        }
    }

    private String findGlossaryIdByName(DeepLClient translator, String glossaryName) throws InterruptedException, DeepLException {
        for (MultilingualGlossaryInfo info : translator.listMultilingualGlossaries()) {
            if (StringUtils.equals(info.getName(), glossaryName)) {
                return info.getGlossaryId();
            }
        }
        return null;
    }

    private String buildGlossaryName(String site, String srcLanguage, String destLanguage) {
        return GLOSSARY_NAME_PREFIX + "-" + site + "-" + srcLanguage + "-" + destLanguage;
    }

    private String sanitizeNamePart(String value) {
        String trimmed = StringUtils.trimToNull(value);
        if (trimmed == null) {
            return "default";
        }
        // Keep the site segment of the glossary name/cache key simple and unambiguous.
        return trimmed.replaceAll("[^A-Za-z0-9]+", "-");
    }

    private Map<String, String> sanitizeGlossaryTerms(Map<String, String> glossaryTerms) {
        Map<String, String> sanitized = new LinkedHashMap<>();
        Set<String> seenSources = new HashSet<>();
        for (Map.Entry<String, String> entry : glossaryTerms.entrySet()) {
            String source = stripWhitespace(entry.getKey());
            String target = stripWhitespace(entry.getValue());
            if (StringUtils.isEmpty(source) || StringUtils.isEmpty(target)) {
                continue;
            }
            if (containsControlCharacter(source) || containsControlCharacter(target)) {
                logger.warn("Skipping glossary entry with control characters: '{}' -> '{}'", source, target);
                continue;
            }
            // DeepL forbids duplicate source terms. The incoming map cannot hold duplicate keys, but
            // stripping whitespace can make two distinct keys collide (e.g. "Term" and "Term "), so guard
            // against that. Matching is case-sensitive: DeepL accepts sources differing only by case
            // (e.g. "Unil" and "UNIL"), so we must not drop those.
            if (!seenSources.add(source)) {
                logger.warn("Skipping duplicate glossary source term after trimming: '{}'", source);
                continue;
            }
            sanitized.put(source, target);
        }
        return sanitized;
    }

    private String stripWhitespace(String value) {
        if (value == null) {
            return "";
        }
        // Remove leading/trailing whitespace including Unicode separators such as the non-breaking space
        // (U+00A0), which DeepL rejects. String.trim()/StringUtils.strip() and Character.isWhitespace()
        // do not treat U+00A0 as whitespace, so also test Character.isSpaceChar().
        int start = 0;
        int end = value.length();
        while (start < end && isStrippable(value.charAt(start))) {
            start++;
        }
        while (end > start && isStrippable(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(start, end);
    }

    private boolean isStrippable(char c) {
        return Character.isWhitespace(c) || Character.isSpaceChar(c);
    }

    private boolean containsControlCharacter(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\t' || c == '\n' || c == '\r' || Character.getType(c) == Character.CONTROL) {
                return true;
            }
        }
        return false;
    }

    private String buildGlossaryCsvContent(Map<String, String> glossaryTerms) {
        return glossaryTerms.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> csvValue(entry.getKey()) + "," + csvValue(entry.getValue()))
                .collect(Collectors.joining("\n"));
    }

    private String csvValue(String value) {
        String escaped = StringUtils.defaultString(value).replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }

    private String computeGlossaryHash(Map<String, String> glossaryTerms) {
        String normalized = glossaryTerms.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "\u0000" + entry.getValue())
                .collect(Collectors.joining("\u0001"));
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(normalized.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return String.valueOf(normalized.hashCode());
        }
    }

    private static final class CachedGlossary {
        private final String glossaryId;
        private final String hash;

        private CachedGlossary(String glossaryId, String hash) {
            this.glossaryId = glossaryId;
            this.hash = hash;
        }
    }
}

