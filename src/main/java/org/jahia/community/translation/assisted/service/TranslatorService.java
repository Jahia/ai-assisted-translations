package org.jahia.community.translation.assisted.service;

import org.jahia.community.translation.assisted.graphql.TranslatedField;
import org.jahia.services.content.JCRNodeWrapper;

import javax.jcr.RepositoryException;
import java.util.List;

public interface TranslatorService {

    AssistedTranslationResponse translateNode(JCRNodeWrapper node, String sourceLanguage, String targetLanguage) throws RepositoryException, InterruptedException;
    AssistedTranslationResponse translateProperty(JCRNodeWrapper node, String propertyName, String sourceLanguage, String targetLanguage) throws RepositoryException, InterruptedException;
    List<TranslatedField> suggestTranslationForNode(JCRNodeWrapper node, String sourceLanguage, String targetLanguage) throws RepositoryException, InterruptedException;
    String getProviderKey();
    Boolean isAvailable();
}
