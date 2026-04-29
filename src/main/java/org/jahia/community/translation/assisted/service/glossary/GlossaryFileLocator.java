package org.jahia.community.translation.assisted.service.glossary;

import org.jahia.services.content.JCRNodeWrapper;

import javax.jcr.RepositoryException;
import java.io.InputStream;
import java.util.List;

public interface GlossaryFileLocator {

    List<GlossaryFileDescriptor> listGlossaryFiles(JCRNodeWrapper contextNode) throws RepositoryException;

    InputStream openGlossaryFile(String path, JCRNodeWrapper contextNode) throws RepositoryException;
}

