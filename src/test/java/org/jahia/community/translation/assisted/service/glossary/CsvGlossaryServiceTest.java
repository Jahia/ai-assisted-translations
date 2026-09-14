package org.jahia.community.translation.assisted.service.glossary;

import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CsvGlossaryServiceTest {

    private static final String GLOSSARY_PATH = "/sites/test/files/glossaries/g.csv";

    private CsvGlossaryService service;
    private GlossaryFileLocator locator;
    private JCRNodeWrapper node;
    private JCRSiteNode site;

    @Before
    public void setUp() throws Exception {
        service = new CsvGlossaryService();
        locator = mock(GlossaryFileLocator.class);
        inject(service, "glossaryFileLocator", locator);
        inject(service, "glossaryCsvParser", new GlossaryCsvParser());

        node = mock(JCRNodeWrapper.class, RETURNS_DEEP_STUBS);
        site = mock(JCRSiteNode.class, RETURNS_DEEP_STUBS);
        when(node.getResolveSite()).thenReturn(site);
        when(site.getResolveSite()).thenReturn(site);
        when(site.getPath()).thenReturn("/sites/test");
        when(site.getSession().getWorkspace().getName()).thenReturn("default");
    }

    private static void inject(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private void withGlossaryContent(String csv) throws Exception {
        when(locator.listGlossaryFiles(site)).thenReturn(List.of(new GlossaryFileDescriptor(GLOSSARY_PATH, 1L)));
        when(locator.openGlossaryFile(eq(GLOSSARY_PATH), any()))
                .thenAnswer(invocation -> streamOf(csv));
    }

    private static InputStream streamOf(String csv) {
        return new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void mergesTermsForRequestedLanguagePair() throws Exception {
        withGlossaryContent("key,fr,en\nbonjour,Bonjour,Hello\nmerci,Merci,Thanks\n");

        ResolvedGlossary resolved = service.resolve(node, "fr", "en");

        assertEquals(2, resolved.getTerms().size());
        assertEquals("Hello", resolved.getTerms().get("Bonjour"));
        assertEquals("Thanks", resolved.getTerms().get("Merci"));
    }

    @Test
    public void skipsBadRowsButKeepsGoodTerms() throws Exception {
        withGlossaryContent("key,fr,en\n,Bonjour,Hello\nmerci,Merci,Thanks\n");

        ResolvedGlossary resolved = service.resolve(node, "fr", "en");

        assertEquals("only the valid row contributes a term", 1, resolved.getTerms().size());
        assertEquals("Thanks", resolved.getTerms().get("Merci"));
        assertTrue(resolved.getWarnings().stream().anyMatch(w -> w.contains("key column is mandatory")));
    }

    @Test
    public void fatalFileYieldsNoTermsWithWarning() throws Exception {
        withGlossaryContent("fr,en\nBonjour,Hello\n");

        ResolvedGlossary resolved = service.resolve(node, "fr", "en");

        assertTrue(resolved.getTerms().isEmpty());
        assertTrue(resolved.getWarnings().stream().anyMatch(w -> w.contains("First column must be 'key'")));
    }

    @Test
    public void noGlossaryFilesReturnsEmpty() throws Exception {
        when(locator.listGlossaryFiles(site)).thenReturn(List.of());

        ResolvedGlossary resolved = service.resolve(node, "fr", "en");

        assertTrue(resolved.getTerms().isEmpty());
    }

    @Test
    public void blankLanguageArgumentsReturnEmpty() throws Exception {
        ResolvedGlossary resolved = service.resolve(node, "", "en");
        assertTrue(resolved.getTerms().isEmpty());
    }
}
