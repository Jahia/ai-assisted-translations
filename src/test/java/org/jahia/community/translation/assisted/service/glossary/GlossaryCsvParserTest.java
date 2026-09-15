package org.jahia.community.translation.assisted.service.glossary;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GlossaryCsvParserTest {

    private final GlossaryCsvParser parser = new GlossaryCsvParser();

    private ParsedGlossaryFile parse(String csv) throws IOException {
        try (InputStream in = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8))) {
            return parser.parse("test.csv", in);
        }
    }

    @Test
    public void validCsvParsesRowsAndLanguages() throws IOException {
        ParsedGlossaryFile parsed = parse("key,fr,en\nbonjour,Bonjour,Hello\nmerci,Merci,Thanks\n");

        assertFalse("valid file should have no fatal errors", parsed.hasErrors());
        assertFalse("valid file should have no row warnings", parsed.hasRowWarnings());
        assertEquals(2, parsed.getRows().size());
        assertEquals(List.of("fr", "en"), parsed.getLanguages());

        Map<String, String> firstRow = parsed.getRows().get(0);
        assertEquals("bonjour", firstRow.get("key"));
        assertEquals("Bonjour", firstRow.get("fr"));
        assertEquals("Hello", firstRow.get("en"));
    }

    @Test
    public void missingKeyColumnIsFatal() throws IOException {
        ParsedGlossaryFile parsed = parse("fr,en\nBonjour,Hello\n");

        assertTrue("missing key column must be fatal", parsed.hasErrors());
        assertTrue(parsed.getValidationErrors().stream().anyMatch(e -> e.contains("First column must be 'key'")));
    }

    @Test
    public void lessThanTwoLanguageColumnsIsFatal() throws IOException {
        ParsedGlossaryFile parsed = parse("key,en\nhello,Hello\n");

        assertTrue(parsed.hasErrors());
        assertTrue(parsed.getValidationErrors().stream()
                .anyMatch(e -> e.contains("at least two language columns")));
    }

    @Test
    public void emptyCsvIsFatal() throws IOException {
        ParsedGlossaryFile parsed = parse("");

        assertTrue(parsed.hasErrors());
        assertTrue(parsed.getValidationErrors().stream().anyMatch(e -> e.contains("CSV is empty")));
    }

    @Test
    public void blankKeyRowIsSkippedButGoodRowsAreKept() throws IOException {
        ParsedGlossaryFile parsed = parse("key,fr,en\n,Bonjour,Hello\nmerci,Merci,Thanks\n");

        assertFalse("header is valid, so file is not fatal", parsed.hasErrors());
        assertTrue(parsed.hasRowWarnings());
        assertEquals("only the valid row is kept", 1, parsed.getRows().size());
        assertEquals("merci", parsed.getRows().get(0).get("key"));
        assertTrue(parsed.getRowWarnings().stream().anyMatch(w -> w.contains("key column is mandatory")));
    }

    @Test
    public void rowWithNoTranslatableValueIsSkipped() throws IOException {
        ParsedGlossaryFile parsed = parse("key,fr,en\nempty,,\nmerci,Merci,Thanks\n");

        assertFalse(parsed.hasErrors());
        assertTrue(parsed.hasRowWarnings());
        assertEquals(1, parsed.getRows().size());
        assertEquals("merci", parsed.getRows().get(0).get("key"));
        assertTrue(parsed.getRowWarnings().stream().anyMatch(w -> w.contains("no translatable term value")));
    }

    @Test
    public void rowWithMoreColumnsThanHeaderIsSkipped() throws IOException {
        // An unquoted comma in a term produces an extra field; it must be surfaced, not silently truncated.
        ParsedGlossaryFile parsed = parse("key,fr,en\noops,Bonjour, tout le monde,Hello\nmerci,Merci,Thanks\n");

        assertFalse(parsed.hasErrors());
        assertTrue(parsed.hasRowWarnings());
        assertEquals("only the well-formed row is kept", 1, parsed.getRows().size());
        assertEquals("merci", parsed.getRows().get(0).get("key"));
        assertTrue(parsed.getRowWarnings().stream().anyMatch(w -> w.contains("columns but the header defines")));
    }

    @Test
    public void languageHeaderUnderscoreAndDashVariantsAreDuplicates() throws IOException {
        // fr_FR and fr-FR resolve to the same column, so they must be rejected as duplicate headers.
        ParsedGlossaryFile parsed = parse("key,fr_FR,fr-FR\nx,Bonjour,Salut\n");

        assertTrue(parsed.hasErrors());
        assertTrue(parsed.getValidationErrors().stream().anyMatch(e -> e.contains("Duplicate header")));
    }

    @Test
    public void languageHeadersAreNormalizedToDash() throws IOException {
        ParsedGlossaryFile parsed = parse("key,fr_FR,pt-BR\nx,Bonjour,Ola\n");

        assertFalse(parsed.hasErrors());
        assertEquals(List.of("fr-FR", "pt-BR"), parsed.getLanguages());
    }
}
