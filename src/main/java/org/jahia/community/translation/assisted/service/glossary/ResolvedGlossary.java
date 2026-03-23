package org.jahia.community.translation.assisted.service.glossary;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ResolvedGlossary {

    private final Map<String, String> terms;
    private final List<String> sourceFiles;
    private final List<String> warnings;

    public ResolvedGlossary(Map<String, String> terms, List<String> sourceFiles, List<String> warnings) {
        this.terms = Collections.unmodifiableMap(terms);
        this.sourceFiles = Collections.unmodifiableList(sourceFiles);
        this.warnings = Collections.unmodifiableList(warnings);
    }

    public static ResolvedGlossary empty() {
        return new ResolvedGlossary(Collections.emptyMap(), Collections.emptyList(), Collections.emptyList());
    }

    public Map<String, String> getTerms() {
        return terms;
    }

    public List<String> getSourceFiles() {
        return sourceFiles;
    }

    public List<String> getWarnings() {
        return warnings;
    }
}

