package org.jahia.community.translation.assisted.service;

import org.jahia.community.translation.assisted.graphql.TranslatedField;
import org.jahia.services.content.JCRNodeWrapper;

import org.jahia.community.translation.assisted.service.impl.TranslationData;

import javax.jcr.RepositoryException;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public interface TranslatorService {

    AssistedTranslationResponse translateNode(JCRNodeWrapper node, String sourceLanguage, String targetLanguage) throws RepositoryException, InterruptedException;

    AssistedTranslationResponse translateProperty(JCRNodeWrapper node, String propertyName, String sourceLanguage, String targetLanguage) throws RepositoryException, InterruptedException;

    List<TranslatedField> suggestTranslationForNode(JCRNodeWrapper node, String sourceLanguage, String targetLanguage) throws RepositoryException, InterruptedException;

    /**
     * Suggests translations for some properties of a node only.
     *
     * An implementation should send only those properties to the translation service, which bills by
     * what it is sent. This default keeps an implementation that predates the method working: it
     * translates the whole node and drops the rest of the answer, so it is correct but saves nothing.
     *
     * @param propertyNames the properties to translate, as JCR names; null or empty means the whole node
     */
    default List<TranslatedField> suggestTranslationForNode(JCRNodeWrapper node, String sourceLanguage, String targetLanguage, Collection<String> propertyNames) throws RepositoryException, InterruptedException {
        final List<TranslatedField> suggestions = suggestTranslationForNode(node, sourceLanguage, targetLanguage);
        if (propertyNames == null || propertyNames.isEmpty()) {
            return suggestions;
        }
        return suggestions.stream()
                .filter(field -> propertyNames.contains(TranslationData.propertyNameOf(field.getFieldName())))
                .collect(Collectors.toList());
    }

    String getProviderKey();

    boolean isAvailable();
}
