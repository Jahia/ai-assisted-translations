package org.jahia.community.translation.assisted.service.impl.deepl;

import com.deepl.api.DeepLClient;
import com.deepl.api.MultilingualGlossaryInfo;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class DeepLGlossaryManagerTest {

    private DeepLGlossaryManager manager;
    private DeepLClient translator;

    @Before
    public void setUp() {
        manager = new DeepLGlossaryManager();
        translator = mock(DeepLClient.class);
    }

    @Test
    public void returnsNullWhenTermsAreEmpty() throws Exception {
        assertNull(manager.getOrCreateGlossaryId(translator, "FR", "EN", Map.of()));
        verifyNoInteractions(translator);
    }

    @Test
    public void returnsNullWhenTranslatorIsNull() throws Exception {
        assertNull(manager.getOrCreateGlossaryId(null, "FR", "EN", Map.of("Bonjour", "Hello")));
    }

    @Test
    public void returnsNullWhenAllEntriesAreInvalid() throws Exception {
        // Blank sources/targets are dropped; once nothing valid remains, no glossary is created.
        Map<String, String> terms = new LinkedHashMap<>();
        terms.put("  ", "Hello");   // blank source
        terms.put("Merci", "\u00A0"); // target is only a non-breaking space -> blank after strip

        assertNull(manager.getOrCreateGlossaryId(translator, "FR", "EN", terms));
        verifyNoInteractions(translator);
    }

    @Test
    public void stripsWhitespaceAndDropsBlankEntriesBeforeUpload() throws Exception {
        when(translator.listMultilingualGlossaries()).thenReturn(List.of());
        MultilingualGlossaryInfo created = mock(MultilingualGlossaryInfo.class);
        when(created.getGlossaryId()).thenReturn("glossary-1");
        ArgumentCaptor<String> csvCaptor = ArgumentCaptor.forClass(String.class);
        when(translator.createMultilingualGlossaryFromCsv(anyString(), eq("FR"), eq("EN"), csvCaptor.capture()))
                .thenReturn(created);

        Map<String, String> terms = new LinkedHashMap<>();
        terms.put("  ", "Hello");                 // blank source -> dropped
        terms.put("Merci", "  ");                 // blank target -> dropped
        terms.put("Bonjour", "\u00A0Hello");      // leading non-breaking space -> trimmed, kept
        terms.put("\u00A0Salut ", "Hi");          // leading nbsp + trailing space -> trimmed, kept

        String glossaryId = manager.getOrCreateGlossaryId(translator, "FR", "EN", terms);

        assertEquals("glossary-1", glossaryId);
        String csv = csvCaptor.getValue();
        assertTrue("target whitespace must be stripped", csv.contains("\"Bonjour\",\"Hello\""));
        assertTrue("source whitespace must be stripped", csv.contains("\"Salut\",\"Hi\""));
        assertFalse("no residual non-breaking space", csv.contains("\u00A0"));
        assertEquals("only the two valid entries should remain", 2, csv.split("\n").length);
    }

    @Test
    public void keepsPassThroughEntries() throws Exception {
        // source == target is NOT a documented DeepL restriction (it is used for brand protection),
        // so such entries must be preserved.
        when(translator.listMultilingualGlossaries()).thenReturn(List.of());
        MultilingualGlossaryInfo created = mock(MultilingualGlossaryInfo.class);
        when(created.getGlossaryId()).thenReturn("glossary-1");
        ArgumentCaptor<String> csvCaptor = ArgumentCaptor.forClass(String.class);
        when(translator.createMultilingualGlossaryFromCsv(anyString(), eq("FR"), eq("EN"), csvCaptor.capture()))
                .thenReturn(created);

        Map<String, String> terms = new LinkedHashMap<>();
        terms.put("EPFL", "EPFL");        // pass-through -> kept
        terms.put("Bonjour", "Hello");    // kept

        manager.getOrCreateGlossaryId(translator, "FR", "EN", terms);

        String csv = csvCaptor.getValue();
        assertTrue("pass-through entry must be kept", csv.contains("\"EPFL\",\"EPFL\""));
        assertTrue(csv.contains("\"Bonjour\",\"Hello\""));
        assertEquals(2, csv.split("\n").length);
    }

    @Test
    public void keepsSourcesDifferingOnlyByCase() throws Exception {
        // DeepL dedupes sources case-sensitively, so "Unil" and "UNIL" are distinct and both must survive.
        when(translator.listMultilingualGlossaries()).thenReturn(List.of());
        MultilingualGlossaryInfo created = mock(MultilingualGlossaryInfo.class);
        when(created.getGlossaryId()).thenReturn("glossary-1");
        ArgumentCaptor<String> csvCaptor = ArgumentCaptor.forClass(String.class);
        when(translator.createMultilingualGlossaryFromCsv(anyString(), eq("FR"), eq("EN"), csvCaptor.capture()))
                .thenReturn(created);

        Map<String, String> terms = new LinkedHashMap<>();
        terms.put("Unil", "Unil");
        terms.put("UNIL", "Unil");

        manager.getOrCreateGlossaryId(translator, "FR", "EN", terms);

        String csv = csvCaptor.getValue();
        assertTrue(csv.contains("\"Unil\",\"Unil\""));
        assertTrue(csv.contains("\"UNIL\",\"Unil\""));
        assertEquals("case-differing sources are both kept", 2, csv.split("\n").length);
    }

    @Test
    public void dropsDuplicateSourcesThatCollideAfterTrimming() throws Exception {
        when(translator.listMultilingualGlossaries()).thenReturn(List.of());
        MultilingualGlossaryInfo created = mock(MultilingualGlossaryInfo.class);
        when(created.getGlossaryId()).thenReturn("glossary-1");
        ArgumentCaptor<String> csvCaptor = ArgumentCaptor.forClass(String.class);
        when(translator.createMultilingualGlossaryFromCsv(anyString(), eq("FR"), eq("EN"), csvCaptor.capture()))
                .thenReturn(created);

        Map<String, String> terms = new LinkedHashMap<>();
        terms.put("Bonjour", "Hello");    // kept (first seen)
        terms.put("Bonjour ", "Hi");      // trailing space -> same source after trim -> dropped

        manager.getOrCreateGlossaryId(translator, "FR", "EN", terms);

        String csv = csvCaptor.getValue();
        assertEquals("source colliding after trim must be dropped", 1, csv.split("\n").length);
    }

    @Test
    public void reusesCachedGlossaryForIdenticalTerms() throws Exception {
        when(translator.listMultilingualGlossaries()).thenReturn(List.of());
        MultilingualGlossaryInfo created = mock(MultilingualGlossaryInfo.class);
        when(created.getGlossaryId()).thenReturn("glossary-1");
        when(translator.createMultilingualGlossaryFromCsv(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(created);

        Map<String, String> terms = Map.of("Bonjour", "Hello");

        String first = manager.getOrCreateGlossaryId(translator, "FR", "EN", terms);
        String second = manager.getOrCreateGlossaryId(translator, "FR", "EN", terms);

        assertEquals("glossary-1", first);
        assertEquals("glossary-1", second);
        verify(translator, times(1))
                .createMultilingualGlossaryFromCsv(anyString(), anyString(), anyString(), anyString());
    }
}
