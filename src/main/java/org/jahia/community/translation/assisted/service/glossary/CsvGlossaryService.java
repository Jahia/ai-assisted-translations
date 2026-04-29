package org.jahia.community.translation.assisted.service.glossary;

import org.apache.commons.lang.StringUtils;
import org.jahia.services.content.JCRNodeWrapper;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component(service = GlossaryService.class, immediate = true)
public class CsvGlossaryService implements GlossaryService {

    private static final Logger logger = LoggerFactory.getLogger(CsvGlossaryService.class);

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    @Reference
    private GlossaryFileLocator glossaryFileLocator;

    @Reference
    private GlossaryCsvParser glossaryCsvParser;

    @Override
    public ResolvedGlossary resolve(JCRNodeWrapper contextNode, String sourceLanguage, String targetLanguage) throws RepositoryException {
        if (contextNode == null || StringUtils.isBlank(sourceLanguage) || StringUtils.isBlank(targetLanguage)) {
            return ResolvedGlossary.empty();
        }

        final String source = normalizeLanguage(sourceLanguage);
        final String target = normalizeLanguage(targetLanguage);

        JCRNodeWrapper site = contextNode.getResolveSite();
        List<GlossaryFileDescriptor> files = glossaryFileLocator.listGlossaryFiles(site);
        if (files.isEmpty()) {
            return ResolvedGlossary.empty();
        }

        String cacheKey = buildCacheKey(site, source, target);
        String fingerprint = buildFingerprint(files);
        CacheEntry existing = cache.get(cacheKey);
        if (existing != null && StringUtils.equals(existing.fingerprint, fingerprint)) {
            return existing.glossary;
        }

        ResolvedGlossary resolved = buildGlossary(files, site, source, target);
        cache.put(cacheKey, new CacheEntry(fingerprint, resolved));
        return resolved;
    }

    @Override
    public void clearCache() {
        cache.clear();
    }

    private ResolvedGlossary buildGlossary(List<GlossaryFileDescriptor> files, JCRNodeWrapper contextNode, String sourceLanguage, String targetLanguage) {
        Map<String, String> mergedTerms = new HashMap<>();
        List<String> sourceFiles = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        for (GlossaryFileDescriptor descriptor : files) {
            try (InputStream stream = glossaryFileLocator.openGlossaryFile(descriptor.getPath(), contextNode)) {
                ParsedGlossaryFile parsed = glossaryCsvParser.parse(descriptor.getPath(), stream);
                sourceFiles.add(descriptor.getPath());

                if (parsed.hasErrors()) {
                    warnings.addAll(parsed.getValidationErrors().stream()
                            .map(err -> MessageFormat.format("{0}: {1}", descriptor.getPath(), err))
                            .collect(Collectors.toList()));
                    continue;
                }

                for (Map<String, String> row : parsed.getRows()) {
                    String sourceTerm = getRowTerm(row, sourceLanguage);
                    String targetTerm = getRowTerm(row, targetLanguage);
                    if (StringUtils.isBlank(sourceTerm) || StringUtils.isBlank(targetTerm)) {
                        continue;
                    }
                    mergedTerms.put(sourceTerm, targetTerm);
                }
            } catch (IOException | RepositoryException e) {
                warnings.add(MessageFormat.format("{0}: {1}", descriptor.getPath(), e.getMessage()));
                logger.warn("Unable to parse glossary file {}", descriptor.getPath(), e);
            }
        }

        return new ResolvedGlossary(mergedTerms, sourceFiles, warnings);
    }

    private String getRowTerm(Map<String, String> row, String language) {
        String languageWithDash = language.replace('_', '-');
        String languageWithUnderscore = language.replace('-', '_');
        String value = row.get(languageWithDash);
        if (value == null) {
            value = row.get(languageWithUnderscore);
        }
        return StringUtils.trimToNull(value);
    }

    private String buildCacheKey(JCRNodeWrapper contextNode, String sourceLanguage, String targetLanguage) throws RepositoryException {
        String workspace = contextNode.getSession().getWorkspace().getName();
        String sitePath = contextNode.getResolveSite().getPath();
        return MessageFormat.format("{0}|{1}|{2}|{3}", workspace, sitePath, sourceLanguage, targetLanguage);
    }

    private String buildFingerprint(List<GlossaryFileDescriptor> files) {
        StringBuilder builder = new StringBuilder();
        files.forEach(file -> builder
                .append(file.getPath())
                .append("#")
                .append(file.getLastModified())
                .append("|"));
        return builder.toString();
    }

    private String normalizeLanguage(String language) {
        return StringUtils.trimToEmpty(language).replace('_', '-');
    }

    private static final class CacheEntry {
        private final String fingerprint;
        private final ResolvedGlossary glossary;

        private CacheEntry(String fingerprint, ResolvedGlossary glossary) {
            this.fingerprint = fingerprint;
            this.glossary = glossary;
        }
    }
}

