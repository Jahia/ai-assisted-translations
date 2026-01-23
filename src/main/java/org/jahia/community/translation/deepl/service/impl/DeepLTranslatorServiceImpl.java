package org.jahia.community.translation.deepl.service.impl;

import com.deepl.api.*;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.jahia.api.Constants;
import org.jahia.community.translation.deepl.service.DeepLTranslationResponse;
import org.jahia.community.translation.deepl.service.DeepLTranslatorService;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionFactory;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.nodetypes.ExtendedPropertyDefinition;
import org.jahia.utils.LanguageCodeConverters;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.SocketAddress;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.jahia.community.translation.deepl.DeeplConstants.*;

@Component(service = DeepLTranslatorService.class, configurationPid = SERVICE_CONFIG_FILE_NAME)
public class DeepLTranslatorServiceImpl implements DeepLTranslatorService {

    private static final Logger logger = LoggerFactory.getLogger(DeepLTranslatorServiceImpl.class);
    private static final String SLASH = "/";

    private DeepLClient translator;
    private final Map<String, String> targetLanguages = new HashMap<>();

    @Activate
    public void activate(Map<String, ?> properties) {
        translator = null;
        targetLanguages.clear();
        if (properties == null) {
            logger.warn("Missing configurations: {}", SERVICE_CONFIG_FILE_FULLNAME);
            return;
        }

        final String authKey = (String) properties.getOrDefault(PROP_API_KEY, null);
        logger.debug("DeepL {} = {}", PROP_API_KEY, authKey);
        translator = initializeTranslator(authKey);
        if (translator == null) {
            return;
        }

        properties.entrySet().stream().filter(e -> e.getKey().startsWith(PROP_PREFIX_TARGET_LANGUAGES)).forEach(e -> targetLanguages.put(e.getKey().substring(PROP_PREFIX_TARGET_LANGUAGES.length()), (String) e.getValue()));

    }

    private DeepLClient initializeTranslator(String authKey) {
        if (StringUtils.isBlank(authKey)) {
            logger.warn("{} not defined. Please add it to {}", PROP_API_KEY, SERVICE_CONFIG_FILE_FULLNAME);
            return null;
        }

        final DeepLClientOptions options = (DeepLClientOptions) new DeepLClientOptions().setAppInfo("translation-deepl", "1.2.0").setMaxRetries(3).setTimeout(Duration.ofSeconds(3));

        final String proxyHost = System.getProperty("https.proxyHost");
        final String proxyPort = System.getProperty("https.proxyPort");
        if (proxyHost != null && proxyPort != null) {
            final SocketAddress address = new InetSocketAddress(proxyHost, Integer.parseInt(proxyPort));
            final Proxy proxy = new Proxy(Proxy.Type.HTTP, address);
            options.setProxy(proxy);
        }

        return new DeepLClient(authKey, options);
    }

    @Override
    public DeepLTranslationResponse translateNode(JCRNodeWrapper pNode, String sourceLanguage, String targetLanguage) throws RepositoryException, InterruptedException {
        return translate(pNode, null, sourceLanguage, targetLanguage);
    }

    @Override
    public DeepLTranslationResponse translateProperty(JCRNodeWrapper pNode, String propertyName, String sourceLanguage, String targetLanguage) throws RepositoryException, InterruptedException {
        return translate(pNode, propertyName, sourceLanguage, targetLanguage);
    }

    public DeepLTranslationResponse translate(JCRNodeWrapper pNode, String propertyName, String sourceLanguage, String targetLanguage) throws RepositoryException, InterruptedException {
        final JCRSessionWrapper pNodeSession = pNode.getSession();
        final Set<String> siteLanguages = pNode.getResolveSite().getLanguages();
        if (!siteLanguages.contains(targetLanguage)) {
            final String warnMsg = String.format("The language %s is not allowed on the site", targetLanguage);
            logger.warn(warnMsg);
            return new DeepLTranslationResponseImpl(false, warnMsg);
        } else if (StringUtils.equals(sourceLanguage, targetLanguage)) {
            final String warnMsg = String.format("The language %s is both the source and target languages", targetLanguage);
            logger.warn(warnMsg);
            return new DeepLTranslationResponseImpl(false, warnMsg);
        }

        final JCRSessionWrapper session = JCRSessionFactory.getInstance().getCurrentUserSession(pNodeSession.getWorkspace().getName(), LanguageCodeConverters.languageCodeToLocale(sourceLanguage));
        final String path = pNode.getPath();
        if (!session.nodeExists(path)) {
            final String warnMsg = String.format("Impossible to translate from %s since the node doesn't exist in this language", sourceLanguage);
            logger.warn(warnMsg);
            return new DeepLTranslationResponseImpl(false, warnMsg);
        }
        final JCRNodeWrapper node = session.getNode(path);
        final TranslationData data = new TranslationData();
        if (propertyName == null) {
            buildDataToTranslate(node, data);
        } else {
            buildDataToTranslate(node, propertyName, data);
        }

        return translateAndSave(data, sourceLanguage, targetLanguage);
    }

    private void buildDataToTranslate(JCRNodeWrapper node, TranslationData data) throws RepositoryException {
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
                final String stringValue = StringUtils.trimToNull(property.getValue().getString());
                if (stringValue != null) {
                    data.trackText(key, stringValue);
                }
            }

        }
    }

    private void buildDataToTranslate(JCRNodeWrapper node, String propertyName, TranslationData data) throws RepositoryException {
        if (!isTranslatableNode(node)) {
            return;
        }
        if (node.hasProperty(propertyName)) {
            final Property property = node.getProperty(propertyName);
            if (!isTranslatableProperty(property)) {
                return;
            }
            final String key = node.getPath() + SLASH + property.getName();
            final String stringValue = StringUtils.trimToNull(property.getValue().getString());
            if (stringValue != null && !stringValue.isEmpty()) {
                data.trackText(key, stringValue);
            }
        }
    }

    private DeepLTranslationResponse translateAndSave(TranslationData data, String srcLanguage, String targetLanguage) throws InterruptedException {
        final Map<String, String> translations = generateTranslations(data, srcLanguage, targetLanguage);

        if (MapUtils.isEmpty(translations)) {
            final String warnMsg = String.format(MSG_NOTHING_TO_TRANSLATE, targetLanguage);
            logger.warn(warnMsg);
            return new DeepLTranslationResponseImpl(false, warnMsg);
        }

        try {
            final JCRSessionWrapper session = JCRSessionFactory.getInstance().getCurrentUserSession(Constants.EDIT_WORKSPACE, LanguageCodeConverters.languageCodeToLocale(targetLanguage));
            if (saveTranslations(session, translations)) {
                final String debugMsg = String.format("Content translated in %s", targetLanguage);
                logger.debug(debugMsg);
                return new DeepLTranslationResponseImpl(true, debugMsg);
            } else {
                final String warnMsg = String.format(MSG_NOTHING_TO_TRANSLATE, targetLanguage);
                logger.warn(warnMsg);
                return new DeepLTranslationResponseImpl(false, warnMsg);
            }
        } catch (RepositoryException e) {
            if (logger.isErrorEnabled()) {
                logger.error("", e);
            }
            return new DeepLTranslationResponseImpl(false, "An error occurred while translating the content in " + targetLanguage);
        }
    }

    private Map<String, String> generateTranslations(TranslationData data, String srcLanguage, String destLanguage) throws InterruptedException {
        if (translator == null) {
            logger.warn("Translator is null");
            return Collections.emptyMap();
        }
        if (!data.hasTextToTranslate()) {
            logger.warn("There is no text to translate");
            return Collections.emptyMap();
        }

        final String destDeepLLanguage = targetLanguages.getOrDefault(destLanguage, destLanguage);
        final Map<String, String> texts = data.getTexts();
        final int nbTexts = texts.size();
        final List<String> keys = new ArrayList<>(nbTexts);
        final List<String> srcTexts = new ArrayList<>(nbTexts);
        texts.forEach((k, v) -> {
            keys.add(k);
            srcTexts.add(v);
        });
        final List<TextResult> results;
        try {
            TextTranslationOptions textTranslationOptions = new TextTranslationOptions();
            textTranslationOptions.setTagHandling("html");
            results = translator.translateText(srcTexts, srcLanguage, destDeepLLanguage, textTranslationOptions);
        } catch (DeepLException e) {
            if (logger.isErrorEnabled()) {
                logger.error("Failed to translate content", e);
            }
            return Collections.emptyMap();
        }

        final Map<String, String> translations = IntStream.range(0, nbTexts).boxed()
                .collect(Collectors.toMap(keys::get, i -> StringEscapeUtils.unescapeHtml4(results.get(i).getText())));

        return data.completeTranslations(translations);
    }

    private static boolean saveTranslations(JCRSessionWrapper session, Map<String, String> translations) {
        final AtomicBoolean hasSavedSomething = new AtomicBoolean(false);
        translations.forEach((path, value) -> {
            try {
                final JCRNodeWrapper node = session.getNode(StringUtils.substringBeforeLast(path, SLASH));
                final String propertyName = StringUtils.substringAfterLast(path, SLASH);
                if (node.hasProperty(propertyName) && StringUtils.equals(node.getPropertyAsString(propertyName), value)) {
                    logger.warn("{} is already translated", path);
                } else {
                    node.setProperty(propertyName, value);
                    hasSavedSomething.set(true);
                }
            } catch (RepositoryException e) {
                if (logger.isErrorEnabled()) {
                    logger.error("", e);
                }
            }
        });

        try {
            session.save();
        } catch (RepositoryException e) {
            if (logger.isErrorEnabled()) {
                logger.error("", e);
            }
        }

        return hasSavedSomething.get();
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
