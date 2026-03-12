package org.jahia.community.translation.assisted.service;

import org.apache.commons.lang3.StringUtils;
import org.jahia.api.Constants;
import org.jahia.community.translation.assisted.service.impl.TranslationData;
import org.jahia.services.content.JCRNodeWrapper;
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
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

import static org.jahia.community.translation.assisted.AssistedTranslationsConstants.*;

@Component(configurationPid = SERVICE_CONFIG_FILE_NAME, immediate = true, service = TranslationServicesManager.class)
/**
 * Manager of translation services, responsible for providing the appropriate service based on the provider key.
 */
public class TranslationServicesManagerImpl implements TranslationServicesManager {
    private static final Logger logger = LoggerFactory.getLogger(TranslationServicesManagerImpl.class);

    @Reference
    private ConfigurationAdmin configurationAdmin;
    private Map<String, String> targetLanguages;


    @Activate
    protected void activate(Map<String, String> properties) {
        if (properties == null) {
            logger.warn("Missing configurations: {}", SERVICE_CONFIG_FILE_FULLNAME);
            return;
        }
        final String openAIKey = properties.getOrDefault(OPENAI_API_KEY, "");
        final String deeplAIKey = properties.getOrDefault(DEEPL_API_KEY, "");
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
        targetLanguages = transformTargetLanguagesPropertiesToMap(properties);
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
                throw new RuntimeException(e);
            }
        }
        if (StringUtils.isNotEmpty(deeplAIKey)) {
            try {
                Configuration configuration = configurationAdmin.getConfiguration(SERVICE_CONFIG_FILE_NAME_DEEPL);
                Dictionary<String, Object> configProperties = new Hashtable<>();
                    configProperties.put(DEEPL_API_KEY, openAIKey);
                    configuration.updateIfDifferent(configProperties);

            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
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
    public void buildDataToTranslate(JCRNodeWrapper node, TranslationData data, boolean forceTranslation) throws RepositoryException {
        if (!isTranslatableNode(node)) {
            return;
        }

        final PropertyIterator properties;
        try {
            properties = node.getProperties();
        } catch (RepositoryException e) {
            if (logger.isErrorEnabled()) {
                logger.error("", e);
            }
            return;
        }
        while (properties.hasNext()) {
            final Property property = properties.nextProperty();
            if (isTranslatableProperty(property)) {
                final String key = node.getPath() + SLASH + property.getName();
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

    private boolean isTranslatableNode(JCRNodeWrapper node) {
        try {
            if (node.isNodeType(Constants.JAHIANT_PAGE)) {
                return true;
            }
            if (node.isNodeType(Constants.JAHIANT_CONTENT)) {
                return true;
            }
        } catch (RepositoryException e) {
            if (logger.isErrorEnabled()) {
                logger.error("", e);
            }
        }
        return false;
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

}
