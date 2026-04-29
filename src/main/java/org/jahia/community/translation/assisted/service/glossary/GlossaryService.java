package org.jahia.community.translation.assisted.service.glossary;

import org.jahia.services.content.JCRNodeWrapper;

import javax.jcr.RepositoryException;

public interface GlossaryService {

    ResolvedGlossary resolve(JCRNodeWrapper contextNode, String sourceLanguage, String targetLanguage) throws RepositoryException;

    void clearCache();
}

