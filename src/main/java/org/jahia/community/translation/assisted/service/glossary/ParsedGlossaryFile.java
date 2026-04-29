package org.jahia.community.translation.assisted.service.glossary;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ParsedGlossaryFile {

    private final String sourceName;
    private final List<String> languages;
    private final List<Map<String, String>> rows;
    private final List<String> validationErrors;

    public ParsedGlossaryFile(String sourceName, List<String> languages, List<Map<String, String>> rows, List<String> validationErrors) {
        this.sourceName = sourceName;
        this.languages = Collections.unmodifiableList(languages);
        this.rows = Collections.unmodifiableList(rows);
        this.validationErrors = Collections.unmodifiableList(validationErrors);
    }

    public String getSourceName() {
        return sourceName;
    }

    public List<String> getLanguages() {
        return languages;
    }

    public List<Map<String, String>> getRows() {
        return rows;
    }

    public List<String> getValidationErrors() {
        return validationErrors;
    }

    public boolean hasErrors() {
        return !validationErrors.isEmpty();
    }
}

