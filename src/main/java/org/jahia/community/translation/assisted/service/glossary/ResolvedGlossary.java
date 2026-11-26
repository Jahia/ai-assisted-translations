package org.jahia.community.translation.assisted.service.glossary;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ResolvedGlossary {

    private final Map<String, String> terms;
    private final String siteKey;
    private final List<String> sourceFiles;
    private final List<String> warnings;

    public ResolvedGlossary(Map<String, String> terms, String siteKey, List<String> sourceFiles, List<String> warnings) {
        this.terms = Collections.unmodifiableMap(terms);
        this.siteKey = siteKey;
        this.sourceFiles = Collections.unmodifiableList(sourceFiles);
        this.warnings = Collections.unmodifiableList(warnings);
    }

    public static ResolvedGlossary empty() {
        return new ResolvedGlossary(Collections.emptyMap(), null, Collections.emptyList(), Collections.emptyList());
    }

    public Map<String, String> getTerms() {
        return terms;
    }

    /**
     * @return the key of the site the glossary was resolved from, used to scope the remote (DeepL)
     * glossary so that sites sharing a language pair do not overwrite each other. May be null for an
     * empty glossary.
     */
    public String getSiteKey() {
        return siteKey;
    }

    public List<String> getSourceFiles() {
        return sourceFiles;
    }

    public List<String> getWarnings() {
        return warnings;
    }
}

