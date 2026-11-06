package org.jahia.community.translation.assisted.service.impl;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TranslationDataTest {

    private static final String NODE = "/sites/luxe/contents/agency";

    @Test
    public void propertyNameIsTheLastSegmentWithoutTheValueIndex() {
        assertEquals("text", TranslationData.propertyNameOf(NODE + "/text"));
        assertEquals("jcr:title", TranslationData.propertyNameOf(NODE + "/jcr:title"));
        assertEquals("tags", TranslationData.propertyNameOf(NODE + "/tags___2___"));
        // A bare field name, as returned in a suggestion, has no path.
        assertEquals("tags", TranslationData.propertyNameOf("tags___0___"));
        assertEquals("text", TranslationData.propertyNameOf("text"));
    }

    @Test
    public void onlyTheRequestedPropertiesAreKept() {
        final TranslationData data = new TranslationData();
        data.trackText(NODE + "/jcr:title", "Title");
        data.trackText(NODE + "/text", "Body");
        data.trackText(NODE + "/tags___0___", "one");
        data.trackText(NODE + "/tags___1___", "two");
        data.trackCopiedValue(NODE + "/link", "ref");

        data.retainProperties(Arrays.asList("text", "tags"));

        final Map<String, String> texts = data.getTexts();
        assertEquals(3, texts.size());
        assertTrue(texts.containsKey(NODE + "/text"));
        assertTrue(texts.containsKey(NODE + "/tags___0___"));
        assertTrue(texts.containsKey(NODE + "/tags___1___"));
        assertFalse(texts.containsKey(NODE + "/jcr:title"));
        assertFalse(data.hasTextToCopy());
    }

    @Test
    public void aPropertyThatOnlyStartsWithTheSameNameIsNotKept() {
        final TranslationData data = new TranslationData();
        data.trackText(NODE + "/tags", "a");
        data.trackText(NODE + "/tagsExtra", "b");

        data.retainProperties(Collections.singleton("tags"));

        assertEquals(1, data.getTexts().size());
        assertTrue(data.getTexts().containsKey(NODE + "/tags"));
    }

    @Test
    public void aKeptDuplicateOfADroppedTextIsStillTranslatedOnce() {
        final TranslationData data = new TranslationData();
        // "Hello" is tracked first for the title, so the text is a duplicate of it.
        data.trackText(NODE + "/jcr:title", "Hello");
        data.trackText(NODE + "/text", "Hello");
        data.trackText(NODE + "/summary", "Hello");

        data.retainProperties(new HashSet<>(Arrays.asList("text", "summary")));

        // The dropped title is not sent, but its text has to be, once, for the two kept properties.
        assertEquals(1, data.getTexts().size());
        final Map<String, String> completed = data.completeTranslations(Collections.singletonMap(data.getTexts().keySet().iterator().next(), "Bonjour"));
        assertEquals("Bonjour", completed.get(NODE + "/text"));
        assertEquals("Bonjour", completed.get(NODE + "/summary"));
        assertFalse(completed.containsKey(NODE + "/jcr:title"));
    }

    @Test
    public void aKeptDuplicateOfAKeptTextNeedsNoExtraText() {
        final TranslationData data = new TranslationData();
        data.trackText(NODE + "/text", "Hello");
        data.trackText(NODE + "/summary", "Hello");

        data.retainProperties(new HashSet<>(Arrays.asList("text", "summary")));

        assertEquals(1, data.getTexts().size());
        final Map<String, String> completed = data.completeTranslations(Collections.singletonMap(NODE + "/text", "Bonjour"));
        assertEquals("Bonjour", completed.get(NODE + "/summary"));
    }
}
