package org.jahia.community.translation.assisted.service;

import org.apache.commons.lang3.StringUtils;
import org.apache.felix.utils.collections.MapToDictionary;
import org.jahia.api.Constants;
import org.jahia.community.translation.assisted.graphql.TranslatedField;
import org.jahia.community.translation.assisted.service.impl.TranslationData;
import org.jahia.exceptions.JahiaRuntimeException;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRPublicationService;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.PublicationInfo;
import org.jahia.services.content.nodetypes.ExtendedPropertyDefinition;
import org.jahia.services.content.nodetypes.SelectorType;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.*;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.jahia.community.translation.assisted.AssistedTranslationsConstants.*;

@Component(configurationPid = SERVICE_CONFIG_FILE_NAME, immediate = true, service = TranslationServicesManager.class)
public class TranslationServicesManagerImpl implements TranslationServicesManager {
    private static final Logger logger = LoggerFactory.getLogger(TranslationServicesManagerImpl.class);
    private static final Pattern VALUE_IDX_IN_FIELD = Pattern.compile("(.*)___(\\d+)___$");
    private final List<String> translatableNodeTypes = Arrays.asList(Constants.JAHIAMIX_MAIN_RESOURCE, "jmix:editorialContent", Constants.JAHIANT_PAGE, Constants.JAHIANT_CONTENT);
    private final MessageFormat keyFormat = new MessageFormat("{0}/{1}");
    private final MessageFormat indexedKeyFormat = new MessageFormat("{0}/{1}___{2}___");
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
        configureDeepL(deeplAIKey);
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
        if (subtree) {
            Set<String> languages = new HashSet<>();
            languages.add(node.getSession().getLocale().toString());
            // Use publication service to get the list of nodes part of this node publication
            Set<String> uuids = new HashSet<>();
            List<PublicationInfo> publicationInfo = publicationService.getPublicationInfo(node.getIdentifier(), languages, false, true, false, Constants.EDIT_WORKSPACE, Constants.LIVE_WORKSPACE);
            publicationInfo.forEach(p -> uuids.addAll(p.getAllUuids(false, true)));

            JCRSessionWrapper session = node.getSession();
            for (String uuid : uuids) {
                try {
                    JCRNodeWrapper relatedNode = session.getNodeByIdentifier(uuid);
                    if (isTranslatableNode(relatedNode)) {
                        translatePropertiesOfNode(relatedNode, data);
                    }
                } catch (RepositoryException e) {
                    if (logger.isErrorEnabled()) {
                        logger.error("", e);
                    }
                }
            }
        } else if (isTranslatableNode(node)) {
            translatePropertiesOfNode(node, data);
        }
    }

    private void translatePropertiesOfNode(JCRNodeWrapper relatedNode, TranslationData data) throws RepositoryException {
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
            analyzeProperty(relatedNode, data, property);
        }
    }

    @Override
    public void buildDataToTranslate(JCRNodeWrapper node, String propertyName, TranslationData data) throws RepositoryException {
        if (!isTranslatableNode(node)) {
            return;
        }
        if (node.hasProperty(propertyName)) {
            analyzeProperty(node, data, node.getProperty(propertyName));
        }
    }

    @Override
    public Map<String, TranslatedField> getTranslatedFieldMap(List<TranslatedField> translatedFields) {
        Map<String, TranslatedField> translatedFieldMap = new HashMap<>();
        translatedFields.forEach(field -> {
            Matcher matcher = VALUE_IDX_IN_FIELD.matcher(field.getFieldName());
            if (matcher.matches()) {
                String fieldName = matcher.group(1);
                int index = Integer.parseInt(matcher.group(2));
                if (translatedFieldMap.containsKey(fieldName)) {
                    translatedFieldMap.get(fieldName).addTranslatedValue(field.getTranslatedValue(), index);
                } else {
                    TranslatedField translatedField = new TranslatedField(fieldName, new ArrayList<>());
                    translatedField.addTranslatedValue(field.getTranslatedValue(), index);
                    translatedFieldMap.put(fieldName, translatedField);
                }
            } else {
                translatedFieldMap.put(field.getFieldName(), field);
            }
        });
        return translatedFieldMap;
    }

    @Override
    public List<TranslatedField> getTranslatedFieldList(TranslationData data, boolean subtree, Map<String, String> translatedValues) {
        return data.completeTranslations(translatedValues).entrySet().stream()
                .map(e -> new TranslatedField((subtree ? e.getKey() : org.apache.commons.lang.StringUtils.substringAfterLast(e.getKey(), SLASH)), e.getValue()))
                .collect(Collectors.toList());
    }

    @Override
    public void copyFieldValue(TranslatedField field, JCRSessionWrapper sessionTarget) {
        try {
            String path = field.getFieldName();
            final JCRNodeWrapper targetNode = sessionTarget.getNode(org.apache.commons.lang.StringUtils.substringBeforeLast(path, SLASH));
            final String propertyName = org.apache.commons.lang.StringUtils.substringAfterLast(path, SLASH);
            if (field.getTranslatedValues() != null) {
                targetNode.setProperty(propertyName, field.getTranslatedValues().toArray(new String[0]));
            } else if (targetNode.hasProperty(propertyName) && org.apache.commons.lang.StringUtils.equals(targetNode.getPropertyAsString(propertyName), field.getTranslatedValue())) {
                logger.warn("{} is already translated", path);
            } else {
                targetNode.setProperty(propertyName, field.getTranslatedValue());
            }
        } catch (RepositoryException e) {
            if (logger.isErrorEnabled()) {
                logger.error("", e);
            }
        }
    }

    private void analyzeProperty(JCRNodeWrapper node, TranslationData data, Property property) throws RepositoryException {
        int valueIdx = 0;
        switch (getPropertyAction(property)) {
            case TRANSLATE:
                trackProperty(property, node, data::trackText);
                break;
            case TRANSLATE_ARRAY:
                for (Value value : property.getValues()) {
                    trackProperty(property, node, value.getString(), valueIdx++, data::trackText);
                }
                break;
            case COPY:
                trackProperty(property, node, data::trackCopiedValue);
                break;
            case COPY_ARRAY:
                for (Value value : property.getValues()) {
                    trackProperty(property, node, value.getString(), valueIdx++, data::trackCopiedValue);
                }
                break;
            case IGNORE:
        }
    }

    private boolean isTranslatableNode(JCRNodeWrapper node) {
        try {
            // Skip the node if it has no I18N node in this language
            if (node.hasI18N(node.getSession().getLocale())) {
                // The node is translated let's see if we accept the type
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
        } catch (RepositoryException e) {
            throw new JahiaRuntimeException(e);
        }
        return false;
    }

    private PropertyAction getPropertyAction(Property property) {
        final ExtendedPropertyDefinition definition;
        try {
            definition = (ExtendedPropertyDefinition) property.getDefinition();
        } catch (RepositoryException e) {
            logger.error("", e);
            return PropertyAction.IGNORE;
        }

        if (!definition.isInternationalized()
                || definition.isHidden()
                || definition.isProtected()) {
            return PropertyAction.IGNORE;
        }

        // If multiple I18N String with selector in SMALL_TEXT, TEXT_AREA, RICH_TEXT return PropertyAction.TRANSLATE_ARRAY
        if ((definition.getRequiredType() == PropertyType.STRING)
                && (definition.getSelector() == SelectorType.SMALLTEXT
                || definition.getSelector() == SelectorType.TEXTAREA
                || definition.getSelector() == SelectorType.RICHTEXT)) {
            return definition.isMultiple() ? PropertyAction.TRANSLATE_ARRAY : PropertyAction.TRANSLATE;
        }

        // Any other I18N String should be copied
        if (definition.getRequiredType() == PropertyType.STRING || definition.getRequiredType() == PropertyType.WEAKREFERENCE) {
            return definition.isMultiple() ? PropertyAction.COPY_ARRAY : PropertyAction.COPY;
        }

        return PropertyAction.IGNORE;
    }

    private void trackProperty(Property property, JCRNodeWrapper node, BiConsumer<String, String> tracker) {
        try {
            trackProperty(property, node, property.getValue().getString(), -1, tracker);
        } catch (RepositoryException e) {
            logger.error("", e);
        }
    }

    private void trackProperty(Property property, JCRNodeWrapper node, String value, int index, BiConsumer<String, String> tracker) {
        try {
            final String key = index >= 0 ? indexedKeyFormat.format(new Object[]{node.getPath(), property.getName(), index}) : keyFormat.format(new Object[]{node.getPath(), property.getName()});
            final String stringValue = StringUtils.trimToNull(value);
            if (stringValue != null) tracker.accept(key, stringValue);
        } catch (RepositoryException e) {
            logger.error("", e);
        }
    }

    private void configureDeepL(String deeplAIKey) {
        if (StringUtils.isNotEmpty(deeplAIKey)) {
            try {
                Configuration configuration = configurationAdmin.getConfiguration(SERVICE_CONFIG_FILE_NAME_DEEPL);
                Map<String, String> configProperties = new HashMap<>();
                configProperties.put(DEEPL_API_KEY, deeplAIKey);
                configuration.updateIfDifferent(new MapToDictionary(configProperties));

            } catch (IOException e) {
                throw new JahiaRuntimeException(e);
            }
        }
    }

    // Private configuration methods

    private void configureOpenAI(Map<String, String> properties, String openAIKey) {
        if (StringUtils.isNotEmpty(openAIKey)) {
            try {
                Configuration configuration = configurationAdmin.getConfiguration(SERVICE_CONFIG_FILE_NAME_OPENAI);
                Map<String, String> configProperties = new HashMap<>();
                configProperties.put(OPENAI_API_KEY, openAIKey);
                if (properties.containsKey(TRANSLATION_OPENAI_PROMPT)) {
                    configProperties.put(TRANSLATION_OPENAI_PROMPT, properties.get(TRANSLATION_OPENAI_PROMPT));
                }
                if (properties.containsKey(TRANSLATION_OPENAI_MODEL)) {
                    configProperties.put(TRANSLATION_OPENAI_MODEL, properties.get(TRANSLATION_OPENAI_MODEL));
                }
                configuration.updateIfDifferent(new MapToDictionary(configProperties));
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

    private enum PropertyAction {TRANSLATE, TRANSLATE_ARRAY, COPY, COPY_ARRAY, IGNORE}

}
