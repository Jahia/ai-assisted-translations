package org.jahia.community.translation.assisted.service.glossary;

import org.apache.commons.lang.StringUtils;
import org.jahia.api.Constants;
import org.jahia.community.translation.assisted.AssistedTranslationsConstants;
import org.jahia.services.content.JCRContentUtils;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.decorator.JCRFileNode;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.jahia.community.translation.assisted.AssistedTranslationsConstants.SERVICE_CONFIG_FILE_NAME;

@Component(service = GlossaryFileLocator.class, immediate = true, configurationPid = SERVICE_CONFIG_FILE_NAME)
public class JcrGlossaryFileLocator implements GlossaryFileLocator {

    private static final Logger logger = LoggerFactory.getLogger(JcrGlossaryFileLocator.class);
    private final Pattern glossaryFilePattern = Pattern.compile(".*\\.csv$");
    private String glossaryRelativePath;

    @Activate
    public void activate(Map<String, String> properties) {
        if (properties == null) {
            return;
        }
        glossaryRelativePath = StringUtils.defaultIfBlank(
                properties.get(AssistedTranslationsConstants.TRANSLATION_GLOSSARY_RELATIVE_PATH),
                "/files/glossaries"
        );
    }

    @Override
    public List<GlossaryFileDescriptor> listGlossaryFiles(JCRNodeWrapper contextNode) throws RepositoryException {
        String siteGlossaryPath = buildSiteGlossaryPath(contextNode);
        JCRSessionWrapper session = contextNode.getSession();
        if (!session.nodeExists(siteGlossaryPath)) {
            return List.of();
        }

        JCRNodeWrapper glossaryFolder = session.getNode(siteGlossaryPath);
        List<GlossaryFileDescriptor> descriptors = new ArrayList<>();
        List<JCRNodeWrapper> childrenOfType = JCRContentUtils.getChildrenOfType(glossaryFolder, Constants.JAHIANT_FILE);
        for (JCRNodeWrapper fileNode : childrenOfType) {
            if (isSupportedGlossaryFile(fileNode)) {
                String path = fileNode.getPath();
                long lastModified = readLastModified(fileNode);
                descriptors.add(new GlossaryFileDescriptor(path, lastModified));
            }
        }

        descriptors.sort(Comparator.comparing(GlossaryFileDescriptor::getPath));
        return descriptors;
    }

    @Override
    public InputStream openGlossaryFile(String path, JCRNodeWrapper contextNode) throws RepositoryException {
        JCRSessionWrapper session = contextNode.getSession();
        JCRFileNode fileNode = (JCRFileNode) session.getNode(path);
        return fileNode.getFileContent().downloadFile();
    }

    private String buildSiteGlossaryPath(JCRNodeWrapper contextNode) throws RepositoryException {
        String sitePath = contextNode.getResolveSite().getPath();
        String relative = glossaryRelativePath.startsWith("/") ? glossaryRelativePath : "/" + glossaryRelativePath;
        return sitePath + relative;
    }

    private boolean isSupportedGlossaryFile(JCRNodeWrapper fileNode) {
        try {
            if (!fileNode.isNodeType("nt:file")) {
                return false;
            }
            return glossaryFilePattern.matcher(fileNode.getName()).matches();
        } catch (RepositoryException e) {
            logger.warn("Cannot inspect potential glossary file {}", fileNode, e);
            return false;
        }
    }

    private long readLastModified(JCRNodeWrapper fileNode) {
        try {
            Node contentNode = fileNode.getNode("jcr:content");
            if (contentNode.hasProperty("jcr:lastModified")) {
                return contentNode.getProperty("jcr:lastModified").getDate().getTimeInMillis();
            }
        } catch (RepositoryException e) {
            logger.debug("Cannot read jcr:lastModified for glossary file {}", fileNode, e);
        }
        return -1L;
    }
}

