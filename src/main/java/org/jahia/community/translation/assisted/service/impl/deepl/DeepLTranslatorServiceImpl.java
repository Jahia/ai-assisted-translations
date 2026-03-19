package org.jahia.community.translation.assisted.service.impl.deepl;

import com.deepl.api.*;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.jahia.api.Constants;
import org.jahia.community.translation.assisted.graphql.TranslatedField;
import org.jahia.community.translation.assisted.service.AssistedTranslationResponse;
import org.jahia.community.translation.assisted.service.TranslationServicesManager;
import org.jahia.community.translation.assisted.service.TranslationServicesManagerImpl;
import org.jahia.community.translation.assisted.service.TranslatorService;
import org.jahia.community.translation.assisted.service.impl.TranslationData;
import org.jahia.osgi.BundleUtils;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionFactory;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.utils.LanguageCodeConverters;
import org.osgi.framework.BundleException;
import org.osgi.service.component.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.SocketAddress;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.jahia.community.translation.assisted.AssistedTranslationsConstants.*;

@Component(service = TranslatorService.class,
        configurationPid = SERVICE_CONFIG_FILE_NAME_DEEPL,
        property = {"service.translation.provider=deepl", "service.ranking=5.0"},
        immediate = true,
        configurationPolicy = ConfigurationPolicy.REQUIRE)
public class DeepLTranslatorServiceImpl implements TranslatorService {

    private static final Logger logger = LoggerFactory.getLogger(DeepLTranslatorServiceImpl.class);
    private static final String SLASH = "/";

    private DeepLClient translator;
    private boolean available;

    @Reference
    private TranslationServicesManager translationServicesManager;

    @Override
    public String getProviderKey() {
        return "deepl";
    }

    @Override
    public Boolean isAvailable() {
        return available;
    }

    @Activate
    public void activate(Map<String, String> properties) throws BundleException {
        translator = null;
        if (properties == null) {
            logger.warn("Missing configurations: {}", SERVICE_CONFIG_FILE_FULLNAME);
            return;
        }
        logger.info("Activating DeeplTranslator Service");
        final String authKey = properties.getOrDefault(DEEPL_API_KEY, null);
        if (authKey == null || properties.getOrDefault(OPENAI_API_KEY, null) != null) {
            available = false;
            return;
        }
        translator = initializeTranslator(authKey);
        if (translator == null) {
            available = false;
            return;
        }
        available = true;
    }

    private DeepLClient initializeTranslator(String authKey) {
        if (StringUtils.isBlank(authKey)) {
            logger.warn("{} not defined. Please add it to {}", DEEPL_API_KEY, SERVICE_CONFIG_FILE_FULLNAME);
            return null;
        }

        final DeepLClientOptions options = (DeepLClientOptions) new DeepLClientOptions().setAppInfo("ai-assisted-translations", "1.2.0").setMaxRetries(3).setTimeout(Duration.ofSeconds(3));

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
    public AssistedTranslationResponse translateNode(JCRNodeWrapper pNode, String sourceLanguage, String targetLanguage) throws RepositoryException, InterruptedException {
        return translate(pNode, null, sourceLanguage, targetLanguage);
    }

    @Override
    public AssistedTranslationResponse translateProperty(JCRNodeWrapper pNode, String propertyName, String sourceLanguage, String targetLanguage) throws RepositoryException, InterruptedException {
        return translate(pNode, propertyName, sourceLanguage, targetLanguage);
    }

    @Override
    public List<TranslatedField> suggestTranslationForNode(JCRNodeWrapper node, String sourceLanguage, String targetLanguage) throws RepositoryException, InterruptedException {
            // Ensure we get the nod ein the source language to be able to suggest translations
            final JCRSessionWrapper session = JCRSessionFactory.getInstance().getCurrentUserSession(node.getSession().getWorkspace().getName(), LanguageCodeConverters.languageCodeToLocale(sourceLanguage));
            final JCRNodeWrapper localizedNode = session.getNodeByIdentifier(node.getIdentifier());
            final TranslationData data = new TranslationData();

            translationServicesManager.buildDataToTranslate(localizedNode, data, true, false);
            final Map<String, String> translations = generateTranslations(data, sourceLanguage, targetLanguage);
            // The keys of the translations are in the format path/to/node/property, we need to transform them to be able to return the property and its translation suggestion
            return translations.entrySet().stream().map(e -> {
                final String propertyName = StringUtils.substringAfterLast(e.getKey(), SLASH);
                return new TranslatedField(propertyName, e.getValue());
            }).collect(Collectors.toList());
    }

    public AssistedTranslationResponse translate(JCRNodeWrapper pNode, String propertyName, String sourceLanguage, String targetLanguage) throws RepositoryException, InterruptedException {
        final JCRSessionWrapper pNodeSession = pNode.getSession();
        final Set<String> siteLanguages = pNode.getResolveSite().getLanguages();
        if (!siteLanguages.contains(targetLanguage)) {
            final String warnMsg = String.format("The language %s is not allowed on the site", targetLanguage);
            logger.warn(warnMsg);
            return new DeepLAssistedTranslationResponseImpl(false, warnMsg);
        } else if (StringUtils.equals(sourceLanguage, targetLanguage)) {
            final String warnMsg = String.format("The language %s is both the source and target languages", targetLanguage);
            logger.warn(warnMsg);
            return new DeepLAssistedTranslationResponseImpl(false, warnMsg);
        }

        final JCRSessionWrapper session = JCRSessionFactory.getInstance().getCurrentUserSession(pNodeSession.getWorkspace().getName(), LanguageCodeConverters.languageCodeToLocale(sourceLanguage));
        final String path = pNode.getPath();
        if (!session.nodeExists(path)) {
            final String warnMsg = String.format("Impossible to translate from %s since the node doesn't exist in this language", sourceLanguage);
            logger.warn(warnMsg);
            return new DeepLAssistedTranslationResponseImpl(false, warnMsg);
        }
        final JCRNodeWrapper node = session.getNode(path);
        final TranslationData data = new TranslationData();
        if (propertyName == null) {
            translationServicesManager.buildDataToTranslate(node, data, false, true);
        } else {
            translationServicesManager.buildDataToTranslate(node, propertyName, data);
        }

        return translateAndSave(data, sourceLanguage, targetLanguage);
    }



    private AssistedTranslationResponse translateAndSave(TranslationData data, String srcLanguage, String targetLanguage) throws InterruptedException {
        final Map<String, String> translations = generateTranslations(data, srcLanguage, targetLanguage);

        if (MapUtils.isEmpty(translations)) {
            final String warnMsg = String.format(MSG_NOTHING_TO_TRANSLATE, targetLanguage);
            logger.warn(warnMsg);
            return new DeepLAssistedTranslationResponseImpl(false, warnMsg);
        }

        try {
            final JCRSessionWrapper session = JCRSessionFactory.getInstance().getCurrentUserSession(Constants.EDIT_WORKSPACE, LanguageCodeConverters.languageCodeToLocale(targetLanguage));
            if (saveTranslations(session, translations)) {
                final String debugMsg = String.format("Content translated in %s", targetLanguage);
                logger.debug(debugMsg);
                return new DeepLAssistedTranslationResponseImpl(true, debugMsg);
            } else {
                final String warnMsg = String.format(MSG_NOTHING_TO_TRANSLATE, targetLanguage);
                logger.warn(warnMsg);
                return new DeepLAssistedTranslationResponseImpl(false, warnMsg);
            }
        } catch (RepositoryException e) {
            if (logger.isErrorEnabled()) {
                logger.error("", e);
            }
            return new DeepLAssistedTranslationResponseImpl(false, "An error occurred while translating the content in " + targetLanguage);
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

        final String destDeepLLanguage = translationServicesManager.getTargetLanguages().getOrDefault(destLanguage, destLanguage);
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
}
