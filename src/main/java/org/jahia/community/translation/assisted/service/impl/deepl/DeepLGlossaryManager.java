package org.jahia.community.translation.assisted.service.impl.deepl;

import com.deepl.api.DeepLClient;
import com.deepl.api.DeepLException;
import com.deepl.api.GlossaryNotFoundException;
import com.deepl.api.MultilingualGlossaryInfo;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component(service = DeepLGlossaryManager.class, immediate = true)
public class DeepLGlossaryManager {

    private static final Logger logger = LoggerFactory.getLogger(DeepLGlossaryManager.class);
    private static final String GLOSSARY_NAME_PREFIX = "jahia-ai-assisted-translations";
    private static final long GLOSSARY_MAX_AGE_MILLIS = Duration.ofDays(30).toMillis();
    private static final long GLOSSARY_CLEANUP_INTERVAL_MILLIS = Duration.ofHours(12).toMillis();

    private final Map<String, CachedGlossary> glossaryCache = new HashMap<>();
    private volatile long lastGlossaryCleanupTimeMillis;

    public void reset() {
        synchronized (this) {
            glossaryCache.clear();
            lastGlossaryCleanupTimeMillis = 0L;
        }
    }

    public String getOrCreateGlossaryId(DeepLClient translator, String srcLanguage, String destLanguage, Map<String, String> glossaryTerms) throws InterruptedException, DeepLException {
        if (translator == null || MapUtils.isEmpty(glossaryTerms)) {
            return null;
        }

        cleanupStaleGlossariesIfNeeded(translator, false);

        String glossaryKey = srcLanguage + "|" + destLanguage;
        String glossaryHash = computeGlossaryHash(glossaryTerms);
        CachedGlossary cachedGlossary = glossaryCache.get(glossaryKey);
        if (cachedGlossary != null && StringUtils.equals(cachedGlossary.hash, glossaryHash)) {
            return cachedGlossary.glossaryId;
        }

        String glossaryName = buildGlossaryName(srcLanguage, destLanguage);
        String csvContent = buildGlossaryCsvContent(glossaryTerms);

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
                for (MultilingualGlossaryInfo glossaryInfo : translator.listMultilingualGlossaries()) {
                    if (!StringUtils.startsWith(glossaryInfo.getName(), GLOSSARY_NAME_PREFIX) || protectedGlossaryIds.contains(glossaryInfo.getGlossaryId())) {
                        continue;
                    }
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

    private String buildGlossaryName(String srcLanguage, String destLanguage) {
        return GLOSSARY_NAME_PREFIX + "-" + srcLanguage + "-" + destLanguage;
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

