package org.jahia.community.translation.assisted.service.glossary;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.StringUtils;
import org.osgi.service.component.annotations.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component(service = GlossaryCsvParser.class, immediate = true)
public class GlossaryCsvParser {

    private static final Pattern LANGUAGE_HEADER_PATTERN = Pattern.compile("^[a-z]{2}([_-][A-Za-z]{2})?$");

    public ParsedGlossaryFile parse(String sourceName, InputStream inputStream) throws IOException {
        List<String> errors = new ArrayList<>();
        List<String> rowWarnings = new ArrayList<>();
        List<Map<String, String>> rows = new ArrayList<>();
        List<String> headers;
        CSVFormat csvFormat = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .get();

        try (CSVParser parser = csvFormat.parse(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            headers = new ArrayList<>(parser.getHeaderNames());
            if (headers.isEmpty()) {
                errors.add("CSV is empty");
                return new ParsedGlossaryFile(sourceName, List.of(), List.of(), errors, rowWarnings);
            }

            validateHeaders(headers, errors);

            for (CSVRecord csvRecord : parser) {
                int lineNumber = (int) csvRecord.getRecordNumber() + 1;
                Map<String, String> row = new LinkedHashMap<>();
                for (int i = 0; i < headers.size(); i++) {
                    String value = i < csvRecord.size() ? csvRecord.get(i) : "";
                    row.put(headers.get(i), StringUtils.trimToEmpty(value));
                }
                if (validateRow(lineNumber, row, headers, rowWarnings)) {
                    rows.add(row);
                }
            }
        }

        List<String> languageHeaders = headers.stream()
                .filter(this::isLanguageHeader)
                .map(this::normalizeLanguage)
                .collect(Collectors.toList());

        return new ParsedGlossaryFile(sourceName, languageHeaders, rows, errors, rowWarnings);
    }

    private void validateHeaders(List<String> headers, List<String> errors) {
        String firstHeader = StringUtils.trimToEmpty(headers.get(0));
        if (!"key".equalsIgnoreCase(firstHeader)) {
            errors.add("First column must be 'key' but was '" + firstHeader + "' (columns found: " + headers + ")");
        }

        Set<String> duplicateCheck = new LinkedHashSet<>();
        List<String> languageHeaders = new ArrayList<>();
        for (String header : headers) {
            String normalized = StringUtils.trimToEmpty(header).toLowerCase(Locale.ROOT);
            if (StringUtils.isBlank(normalized)) {
                errors.add("Headers cannot contain empty column names (columns found: " + headers + ")");
                continue;
            }
            if (!duplicateCheck.add(normalized)) {
                errors.add("Duplicate header: " + header);
            }
            if (isLanguageHeader(header)) {
                languageHeaders.add(header);
            }
        }

        if (languageHeaders.size() < 2) {
            errors.add("CSV must define at least two language columns matching 'xx' or 'xx-YY' (e.g. en, fr, pt-BR); found "
                    + languageHeaders.size() + ": " + languageHeaders + " (columns found: " + headers + ")");
        }
    }

    private boolean validateRow(int lineNumber, Map<String, String> row, List<String> headers, List<String> rowWarnings) {
        String key = row.get(headers.get(0));
        if (StringUtils.isBlank(key)) {
            rowWarnings.add("Line " + lineNumber + ": key column is mandatory, row skipped");
            return false;
        }

        boolean hasAtLeastOneTerm = headers.stream()
                .filter(this::isLanguageHeader)
                .anyMatch(header -> StringUtils.isNotBlank(row.get(header)));

        if (!hasAtLeastOneTerm) {
            rowWarnings.add("Line " + lineNumber + ": row has no translatable term value, row skipped");
            return false;
        }
        return true;
    }

    private boolean isLanguageHeader(String header) {
        return LANGUAGE_HEADER_PATTERN.matcher(StringUtils.trimToEmpty(header)).matches();
    }

    private String normalizeLanguage(String header) {
        return StringUtils.trimToEmpty(header).replace('_', '-');
    }
}

