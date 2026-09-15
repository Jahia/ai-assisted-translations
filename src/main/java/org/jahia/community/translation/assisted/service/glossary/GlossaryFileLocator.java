package org.jahia.community.translation.assisted.service.glossary;

import org.jahia.services.content.JCRNodeWrapper;

import javax.jcr.RepositoryException;
import java.io.InputStream;
import java.util.List;

public interface GlossaryFileLocator {

    List<GlossaryFileDescriptor> listGlossaryFiles(JCRNodeWrapper contextNode) throws RepositoryException;

    /**
     * Lists the global glossary files stored under the Jahia system site
     * ({@code /sites/systemsite} + the configured relative path). These act as a shared base that every
     * site inherits.
     *
     * <p>Defaults to an empty list so existing implementations remain source/binary compatible; the
     * built-in {@link JcrGlossaryFileLocator} overrides it.
     */
    default List<GlossaryFileDescriptor> listGlobalGlossaryFiles(JCRNodeWrapper contextNode) throws RepositoryException {
        return List.of();
    }

    InputStream openGlossaryFile(String path, JCRNodeWrapper contextNode) throws RepositoryException;
}

