package org.jahia.community.translation.assisted.service;

import org.apache.commons.lang3.StringUtils;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

import static org.jahia.community.translation.assisted.AssistedTranslationsConstants.*;

@Component(configurationPid = SERVICE_CONFIG_FILE_NAME, immediate = true)
/**
 * Manager of translation services, responsible for providing the appropriate service based on the provider key.
 */
public class TranslationServicesManager {
    private static final Logger logger = LoggerFactory.getLogger(TranslationServicesManager.class);

    @Reference
    private ConfigurationAdmin configurationAdmin;


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
        Map targetLanguages = transformTargetLanguagesPropertiesToMap(properties);
        if (StringUtils.isNotEmpty(openAIKey)) {
            try {
                Configuration configuration = configurationAdmin.getConfiguration(SERVICE_CONFIG_FILE_NAME_OPENAI);
                Dictionary<String, Object> configProperties = configuration.getProperties();
                configProperties = new Hashtable<>();
                configProperties.put(OPENAI_API_KEY, openAIKey);
                if (!targetLanguages.isEmpty()) {
                    Dictionary<String, Object> finalConfigProperties = configProperties;
                    targetLanguages.forEach((k, v) -> finalConfigProperties.put(PROP_PREFIX_TARGET_LANGUAGES + k, v));
                }
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
                Dictionary<String, Object> configProperties = configuration.getProperties();
                if (configProperties == null || !deeplAIKey.equals(configProperties.get(DEEPL_API_KEY))) {
                    configProperties = new Hashtable<>();
                    configProperties.put(DEEPL_API_KEY, openAIKey);
                    if (!targetLanguages.isEmpty()) {
                        Dictionary<String, Object> finalConfigProperties = configProperties;
                        targetLanguages.forEach((k, v) -> finalConfigProperties.put(PROP_PREFIX_TARGET_LANGUAGES + k, v));
                    }
                    configuration.updateIfDifferent(configProperties);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static Map<String, String> transformTargetLanguagesPropertiesToMap(Map<String, String> properties) {
        Map<String, String> targetLanguages = new HashMap<>();
        properties.entrySet().stream().filter(e -> e.getKey().startsWith(PROP_PREFIX_TARGET_LANGUAGES)).forEach(e -> targetLanguages.put(e.getKey().substring(PROP_PREFIX_TARGET_LANGUAGES.length()), e.getValue()));
        return targetLanguages;
    }

}
