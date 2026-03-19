package org.jahia.community.translation.assisted.service;

import org.jahia.community.translation.assisted.service.impl.TranslationData;
import org.jahia.services.content.JCRNodeWrapper;

import javax.jcr.RepositoryException;
import java.util.Map;

public interface TranslationServicesManager {
    Map<String, String> getTargetLanguages();

    void buildDataToTranslate(JCRNodeWrapper node, TranslationData data, boolean forceTranslation, boolean subtree) throws RepositoryException;

    void buildDataToTranslate(JCRNodeWrapper node, String propertyName, TranslationData data) throws RepositoryException;
}
