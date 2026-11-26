package org.jahia.community.translation.assisted.service.glossary;

import org.jahia.services.content.JCRCallback;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRTemplate;
import org.jahia.services.content.decorator.JCRFileNode;
import org.jahia.services.usermanager.JahiaUser;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class JcrGlossaryFileLocatorTest {

    private final JcrGlossaryFileLocator locator = new JcrGlossaryFileLocator();

    @Test
    public void readsGlobalFileThroughManagedSystemSession() throws Exception {
        JCRNodeWrapper contextNode = mock(JCRNodeWrapper.class, RETURNS_DEEP_STUBS);
        when(contextNode.getSession().getWorkspace().getName()).thenReturn("default");

        JCRSessionWrapper systemSession = mock(JCRSessionWrapper.class);
        JCRFileNode globalFile = mock(JCRFileNode.class, RETURNS_DEEP_STUBS);
        String path = "/sites/systemsite/files/glossaries/global.csv";
        when(systemSession.getNode(path)).thenReturn(globalFile);
        when(globalFile.getFileContent().downloadFile()).thenReturn(new ByteArrayInputStream("data".getBytes()));

        try (MockedStatic<JCRTemplate> templateStatic = mockStatic(JCRTemplate.class)) {
            JCRTemplate template = mock(JCRTemplate.class);
            templateStatic.when(JCRTemplate::getInstance).thenReturn(template);
            // JCRTemplate owns the system session lifecycle (opens and closes it); run the callback with it.
            when(template.doExecuteWithSystemSessionAsUser(nullable(JahiaUser.class), eq("default"), nullable(Locale.class), any(JCRCallback.class)))
                    .thenAnswer(inv -> ((JCRCallback<?>) inv.getArgument(3)).doInJCR(systemSession));

            InputStream result = locator.openGlossaryFile(path, contextNode);

            verify(systemSession).getNode(path);
            assertEquals("global file must be read via the system session", "data", new String(result.readAllBytes()));
        }
    }

    @Test
    public void readsSiteFileWithUserSessionWithoutSystemSession() throws Exception {
        JCRNodeWrapper contextNode = mock(JCRNodeWrapper.class, RETURNS_DEEP_STUBS);
        JCRSessionWrapper userSession = mock(JCRSessionWrapper.class, RETURNS_DEEP_STUBS);
        when(contextNode.getSession()).thenReturn(userSession);
        JCRFileNode siteFile = mock(JCRFileNode.class, RETURNS_DEEP_STUBS);
        String path = "/sites/acme/files/glossaries/site.csv";
        when(userSession.getNode(path)).thenReturn(siteFile);
        when(siteFile.getFileContent().downloadFile()).thenReturn(new ByteArrayInputStream("x".getBytes()));

        try (MockedStatic<JCRTemplate> templateStatic = mockStatic(JCRTemplate.class)) {
            locator.openGlossaryFile(path, contextNode);

            verify(userSession).getNode(path);
            // A site file must never open a system session.
            templateStatic.verifyNoInteractions();
        }
    }
}
