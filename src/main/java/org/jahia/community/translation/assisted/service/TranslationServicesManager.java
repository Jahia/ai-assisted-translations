package org.jahia.community.translation.assisted.service;

import org.jahia.community.translation.assisted.graphql.TranslatedField;
import org.jahia.community.translation.assisted.service.impl.TranslationData;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;

import javax.jcr.RepositoryException;
import java.util.List;
import java.util.Map;

public interface TranslationServicesManager {
    Map<String, String> getTargetLanguages();

    void buildDataToTranslate(JCRNodeWrapper node, TranslationData data, boolean forceTranslation, boolean subtree) throws RepositoryException;

    void buildDataToTranslate(JCRNodeWrapper node, String propertyName, TranslationData data) throws RepositoryException;

    Map<String, TranslatedField> getTranslatedFieldMap(List<TranslatedField> translatedFields);

    List<TranslatedField> getTranslatedFieldList(TranslationData data, boolean subtree, Map<String, String> translatedValues);

    void copyFieldValue(TranslatedField field, JCRSessionWrapper sessionTarget);
}
