package org.jahia.community.translation.assisted.service;

import org.apache.commons.lang3.StringUtils;
import org.jahia.api.Constants;
import org.jahia.community.translation.assisted.service.impl.TranslationData;
import org.jahia.exceptions.JahiaRuntimeException;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRPublicationService;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.PublicationInfo;
import org.jahia.services.content.nodetypes.ExtendedPropertyDefinition;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import java.io.IOException;
import java.util.*;

import static org.jahia.community.translation.assisted.AssistedTranslationsConstants.*;

@Component(configurationPid = SERVICE_CONFIG_FILE_NAME, immediate = true, service = TranslationServicesManager.class)
/**
 * Manager of translation services, responsible for providing the appropriate service based on the provider key.
 */
public class TranslationServicesManagerImpl implements TranslationServicesManager {
    private static final Logger logger = LoggerFactory.getLogger(TranslationServicesManagerImpl.class);

    private ConfigurationAdmin configurationAdmin;
    private JCRPublicationService publicationService;
    private Map<String, String> targetLanguages;

    @Reference
    public void setConfigurationAdmin(ConfigurationAdmin configurationAdmin) {
        this.configurationAdmin = configurationAdmin;
    }

    @Reference
    public void setPublicationService(JCRPublicationService publicationService) {
        this.publicationService = publicationService;
    }

    @Activate
    protected void activate(Map<String, String> properties) {
        if (properties == null) {
            logger.warn("Missing configurations: {}", SERVICE_CONFIG_FILE_FULLNAME);
            return;
        }
        final String openAIKey = properties.getOrDefault(OPENAI_API_KEY, "");
        final String deeplAIKey = properties.getOrDefault(DEEPL_API_KEY, "");
        validateAtLeastOneKeyExist(deeplAIKey, openAIKey);
        targetLanguages = transformTargetLanguagesPropertiesToMap(properties);
        configureOpenAI(properties, openAIKey);
        configureDeepL(deeplAIKey, openAIKey);
    }

    @Override
    public Map<String, String> getTargetLanguages() {
        return targetLanguages;
    }

    public Map<String, String> transformTargetLanguagesPropertiesToMap(Map<String, String> properties) {
        Map<String, String> map = new HashMap<>();
        properties.entrySet().stream().filter(e -> e.getKey().startsWith(PROP_PREFIX_TARGET_LANGUAGES)).forEach(e -> map.put(e.getKey().substring(PROP_PREFIX_TARGET_LANGUAGES.length()), e.getValue()));
        return map;
    }

    @Override
    public void buildDataToTranslate(JCRNodeWrapper node, TranslationData data, boolean forceTranslation, boolean subtree) throws RepositoryException {
        if (!isTranslatableNode(node)) {
            return;
        }

        if (subtree) {
            Set<String> languages = new HashSet<>();
            languages.add(node.getSession().getLocale().toString());
            // Use publication service to get the list of nodes part of this node publication
            Set<String> uuids = new HashSet<>();
            List<PublicationInfo> publicationInfo = publicationService.getPublicationInfo(node.getIdentifier(), languages, false, true, false, Constants.EDIT_WORKSPACE, Constants.LIVE_WORKSPACE);
            publicationInfo.forEach(p -> {
                uuids.addAll(p.getAllUuids(false, true));
            });

            JCRSessionWrapper session = node.getSession();
            for (String uuid : uuids) {
                try {
                    JCRNodeWrapper relatedNode = session.getNodeByIdentifier(uuid);
                    if (isTranslatableNode(relatedNode)) {
                        translatePropertiesOfNode(relatedNode, data, forceTranslation);
                    }
                } catch (RepositoryException e) {
                    if (logger.isErrorEnabled()) {
                        logger.error("", e);
                    }
                }
            }
        } else {
            translatePropertiesOfNode(node, data, forceTranslation);
        }
    }

    private void translatePropertiesOfNode(JCRNodeWrapper relatedNode, TranslationData data, boolean forceTranslation) throws RepositoryException {
        final PropertyIterator properties;
        try {
            properties = relatedNode.getProperties();
        } catch (RepositoryException e) {
            if (logger.isErrorEnabled()) {
                logger.error("", e);
            }
            return;
        }
        while (properties.hasNext()) {
            final Property property = properties.nextProperty();
            if (isTranslatableProperty(property)) {
                final String key = relatedNode.getPath() + SLASH + property.getName();
                final String stringValue = org.apache.commons.lang.StringUtils.trimToNull(property.getValue().getString());
                if (forceTranslation || stringValue != null) {
                    data.trackText(key, stringValue);
                }
            }

        }
    }

    @Override
    public void buildDataToTranslate(JCRNodeWrapper node, String propertyName, TranslationData data) throws RepositoryException {
        if (!isTranslatableNode(node)) {
            return;
        }
        if (node.hasProperty(propertyName)) {
            final Property property = node.getProperty(propertyName);
            if (!isTranslatableProperty(property)) {
                return;
            }
            final String key = node.getPath() + SLASH + property.getName();
            final String stringValue = org.apache.commons.lang.StringUtils.trimToNull(property.getValue().getString());
            if (stringValue != null && !stringValue.isEmpty()) {
                data.trackText(key, stringValue);
            }
        }
    }

    private List<String> translatableNodeTypes = Arrays.asList(Constants.JAHIAMIX_MAIN_RESOURCE, "jmix:editorialContent", Constants.JAHIANT_PAGE, Constants.JAHIANT_CONTENT);
    private boolean isTranslatableNode(JCRNodeWrapper node) {
        return translatableNodeTypes.stream().anyMatch(type -> {
            try {
                return node.isNodeType(type);
            } catch (RepositoryException e) {
                if (logger.isErrorEnabled()) {
                    logger.error("", e);
                }
                throw new JahiaRuntimeException(e);
            }
        });
    }

    private boolean isTranslatableProperty(Property property) {
        final ExtendedPropertyDefinition definition;
        try {
            definition = (ExtendedPropertyDefinition) property.getDefinition();
        } catch (RepositoryException e) {
            if (logger.isErrorEnabled()) {
                logger.error("", e);
            }
            return false;
        }

        return definition.isInternationalized() && !definition.isMultiple() && definition.getRequiredType() == PropertyType.STRING && !definition.isHidden() && !definition.isProtected();
    }

    private void configureDeepL(String deeplAIKey, String openAIKey) {
        if (StringUtils.isNotEmpty(deeplAIKey)) {
            try {
                Configuration configuration = configurationAdmin.getConfiguration(SERVICE_CONFIG_FILE_NAME_DEEPL);
                Dictionary<String, Object> configProperties = new Hashtable<>();
                configProperties.put(DEEPL_API_KEY, openAIKey);
                configuration.updateIfDifferent(configProperties);

            } catch (IOException e) {
                throw new JahiaRuntimeException(e);
            }
        }
    }

    private void configureOpenAI(Map<String, String> properties, String openAIKey) {
        if (StringUtils.isNotEmpty(openAIKey)) {
            try {
                Configuration configuration = configurationAdmin.getConfiguration(SERVICE_CONFIG_FILE_NAME_OPENAI);
                Dictionary<String, Object> configProperties = new Hashtable<>();
                configProperties.put(OPENAI_API_KEY, openAIKey);
                if (properties.containsKey(TRANSLATION_OPENAI_PROMPT)) {
                    configProperties.put(TRANSLATION_OPENAI_PROMPT, properties.get(TRANSLATION_OPENAI_PROMPT));
                }
                if (properties.containsKey(TRANSLATION_OPENAI_MODEL)) {
                    configProperties.put(TRANSLATION_OPENAI_MODEL, properties.get(TRANSLATION_OPENAI_MODEL));
                }
                configuration.updateIfDifferent(configProperties);
            } catch (IOException e) {
                throw new JahiaRuntimeException(e);
            }
        }
    }

    private void validateAtLeastOneKeyExist(String deeplAIKey, String openAIKey) {
        // For non-existing key or empty key, we delete the configuration to make sure the service is not available. If both keys are empty, we log a warning.
        try {
            if (StringUtils.isEmpty(deeplAIKey)) {
                Configuration configuration = configurationAdmin.getConfiguration(SERVICE_CONFIG_FILE_NAME_DEEPL);
                if (configuration != null) {
                    configuration.delete();
                }
            }

        } catch (IOException e) {
            logger.info("Error while deleting configuration for DeepL translator service: {}", e.getMessage());
        }
        try {
            if (StringUtils.isEmpty(openAIKey)) {
                Configuration configuration = configurationAdmin.getConfiguration(SERVICE_CONFIG_FILE_NAME_OPENAI);
                if (configuration != null) {
                    configuration.delete();
                }
            }

        } catch (IOException e) {
            logger.info("Error while deleting configuration for OpenAI translator service: {}", e.getMessage());
        }
        if (StringUtils.isEmpty(deeplAIKey) && StringUtils.isEmpty(openAIKey)) {
            logger.warn("No API key provided for DeepL and OpenAI translator services. No translator service will be available.");
        } else if (!StringUtils.isEmpty(deeplAIKey) && !StringUtils.isEmpty(openAIKey)) {
            logger.info("API keys provided for both DeepL and OpenAI translator services. Both services will be available.");
        } else if (StringUtils.isNotEmpty(openAIKey)) {
            logger.info("API key provided for OpenAI translator service. Only OpenAI translator service will be available.");
        } else {
            logger.info("API key provided for DeepL translator service. Only DeepL translator service will be available.");
        }
    }

}
