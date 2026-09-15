package org.jahia.community.translation.assisted.service.impl.deepl;

import com.deepl.api.*;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.jahia.api.Constants;
import org.jahia.community.translation.assisted.graphql.TranslatedField;
import org.jahia.community.translation.assisted.service.AssistedTranslationResponse;
import org.jahia.community.translation.assisted.service.TranslationServicesManager;
import org.jahia.community.translation.assisted.service.TranslatorService;
import org.jahia.community.translation.assisted.service.glossary.GlossaryService;
import org.jahia.community.translation.assisted.service.glossary.ResolvedGlossary;
import org.jahia.community.translation.assisted.service.impl.AssistedTranslationResponseImpl;
import org.jahia.community.translation.assisted.service.impl.TranslationData;
import org.jahia.modules.graphql.provider.dxm.DataFetchingException;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionFactory;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.utils.LanguageCodeConverters;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.SocketAddress;
import java.time.Duration;
import java.util.*;
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

    private DeepLClient translator;
    private boolean available;

    @Reference
    private TranslationServicesManager translationServicesManager;

    @Reference
    private GlossaryService glossaryService;

    @Reference
    private DeepLGlossaryManager deepLGlossaryManager;

    private boolean saveTranslations(JCRSessionWrapper session, Map<String, TranslatedField> translations) {
        boolean hasSavedSomething = false;
        translations.values().forEach(field -> translationServicesManager.copyFieldValue(field, session));

        try {
            hasSavedSomething = session.hasPendingChanges();
            if (!hasSavedSomething) {
                session.save();
            }
        } catch (RepositoryException e) {
            if (logger.isErrorEnabled()) {
                logger.error("", e);
            }
        }

        return hasSavedSomething;
    }

    @Override
    public String getProviderKey() {
        return "deepl";
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Activate
    public void activate(Map<String, String> properties, BundleContext bundleContext) {
        translator = null;
        deepLGlossaryManager.reset();
        if (properties == null) {
            logger.warn("Missing configurations: {}", SERVICE_CONFIG_FILE_FULLNAME);
            return;
        }
        logger.info("Activating DeeplTranslator Service");
        final String authKey = properties.get(DEEPL_API_KEY);
        if (authKey == null || properties.get(OPENAI_API_KEY) != null) {
            available = false;
            return;
        }
        translator = initializeTranslator(authKey, bundleContext.getBundle().getVersion().toString());
        if (translator == null) {
            available = false;
            return;
        }
        deepLGlossaryManager.cleanupStaleGlossariesIfNeeded(translator, true);
        available = true;
    }

    private DeepLClient initializeTranslator(String authKey, String version) {
        if (StringUtils.isBlank(authKey)) {
            logger.warn("{} not defined. Please add it to {}", DEEPL_API_KEY, SERVICE_CONFIG_FILE_FULLNAME);
            return null;
        }

        final DeepLClientOptions options = (DeepLClientOptions) new DeepLClientOptions().setAppInfo("ai-assisted-translations", version).setMaxRetries(3).setTimeout(Duration.ofSeconds(3));

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
        ResolvedGlossary resolvedGlossary = glossaryService.resolve(localizedNode, sourceLanguage, targetLanguage);
        return generateTranslations(data, sourceLanguage, targetLanguage, resolvedGlossary.getTerms(), resolvedGlossary.getSiteKey(), false);
    }

    public AssistedTranslationResponse translate(JCRNodeWrapper pNode, String propertyName, String sourceLanguage, String targetLanguage) throws RepositoryException, InterruptedException {
        final JCRSessionWrapper pNodeSession = pNode.getSession();
        final Set<String> siteLanguages = pNode.getResolveSite().getLanguages();
        if (!siteLanguages.contains(targetLanguage)) {
            final String warnMsg = String.format("The language %s is not allowed on the site", targetLanguage);
            logger.warn(warnMsg);
            return new AssistedTranslationResponseImpl(false, warnMsg);
        } else if (StringUtils.equals(sourceLanguage, targetLanguage)) {
            final String warnMsg = String.format("The language %s is both the source and target languages", targetLanguage);
            logger.warn(warnMsg);
            return new AssistedTranslationResponseImpl(false, warnMsg);
        }

        final JCRSessionWrapper session = JCRSessionFactory.getInstance().getCurrentUserSession(pNodeSession.getWorkspace().getName(), LanguageCodeConverters.languageCodeToLocale(sourceLanguage));
        final String path = pNode.getPath();
        if (!session.nodeExists(path)) {
            final String warnMsg = String.format("Impossible to translate from %s since the node doesn't exist in this language", sourceLanguage);
            logger.warn(warnMsg);
            return new AssistedTranslationResponseImpl(false, warnMsg);
        }
        final JCRNodeWrapper node = session.getNode(path);
        final TranslationData data = new TranslationData();
        if (propertyName == null) {
            translationServicesManager.buildDataToTranslate(node, data, false, true);
        } else {
            translationServicesManager.buildDataToTranslate(node, propertyName, data);
        }

        ResolvedGlossary resolvedGlossary = glossaryService.resolve(node, sourceLanguage, targetLanguage);
        return translateAndSave(data, sourceLanguage, targetLanguage, resolvedGlossary.getTerms(), resolvedGlossary.getSiteKey());
    }

    private AssistedTranslationResponse translateAndSave(TranslationData data, String srcLanguage, String targetLanguage, Map<String, String> glossaryTerms, String siteKey) throws InterruptedException {
        final Map<String, TranslatedField> translations = translationServicesManager.getTranslatedFieldMap(generateTranslations(data, srcLanguage, targetLanguage, glossaryTerms, siteKey, true));

        if (MapUtils.isEmpty(translations)) {
            final String warnMsg = String.format(MSG_NOTHING_TO_TRANSLATE, targetLanguage);
            logger.warn(warnMsg);
            return new AssistedTranslationResponseImpl(false, warnMsg);
        }

        try {
            final JCRSessionWrapper session = JCRSessionFactory.getInstance().getCurrentUserSession(Constants.EDIT_WORKSPACE, LanguageCodeConverters.languageCodeToLocale(targetLanguage));
            if (saveTranslations(session, translations)) {
                final String debugMsg = String.format("Content translated in %s", targetLanguage);
                logger.debug(debugMsg);
                return new AssistedTranslationResponseImpl(true, debugMsg);
            } else {
                final String warnMsg = String.format(MSG_NOTHING_TO_TRANSLATE, targetLanguage);
                logger.warn(warnMsg);
                return new AssistedTranslationResponseImpl(false, warnMsg);
            }
        } catch (RepositoryException e) {
            if (logger.isErrorEnabled()) {
                logger.error("", e);
            }
            return new AssistedTranslationResponseImpl(false, "An error occurred while translating the content in " + targetLanguage);
        }
    }

    private List<TranslatedField> generateTranslations(TranslationData data, String srcLanguage, String destLanguage, Map<String, String> glossaryTerms, String siteKey, boolean subtree) throws InterruptedException {
        if (translator == null) {
            logger.warn("Translator is null");
            return Collections.emptyList();
        }
        if (!data.hasTextToTranslate()) {
            logger.warn("There is no text to translate");
            return Collections.emptyList();
        }

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
            // DeepL support only their own list of languages https://developers.deepl.com/docs/getting-started/supported-languages, so we need to convert the language code to the one supported by DeepL if needed
            Locale sourceLocaleFromCode = LanguageCodeConverters.getLocaleFromCode(srcLanguage);
            Locale targetLocaleFromCode = LanguageCodeConverters.getLocaleFromCode(destLanguage);
            List<Language> translatorSourceLanguages = translator.getSourceLanguages();
            List<Language> translatorTargetLanguages = translator.getTargetLanguages();
            String sourceLocaleFromCodeLanguage = sourceLocaleFromCode.getLanguage();
            String targetLocaleFromCodeLanguage = translationServicesManager.getTargetLanguages().getOrDefault(targetLocaleFromCode.getLanguage(),  targetLocaleFromCode.getLanguage());
            String deeplSrcLanguage = translatorSourceLanguages.stream().filter(l -> StringUtils.equalsIgnoreCase(l.getCode(), sourceLocaleFromCodeLanguage)).findFirst().map(Language::getCode).orElse(null);
            String deeplTargetLanguage = translatorTargetLanguages.stream().filter(l -> StringUtils.equalsIgnoreCase(l.getCode(), targetLocaleFromCodeLanguage)).findFirst().map(Language::getCode).orElse(null);
            if (deeplSrcLanguage == null || deeplTargetLanguage == null) {
                throw new DataFetchingException(String.format("DeepL doesn't support the language %s (%s)", deeplSrcLanguage == null ? sourceLocaleFromCode.getDisplayName() : targetLocaleFromCode.getDisplayName(),deeplSrcLanguage == null ? sourceLocaleFromCodeLanguage : targetLocaleFromCodeLanguage));
            }
            deeplSrcLanguage = deeplSrcLanguage.toUpperCase();
            deeplTargetLanguage = deeplTargetLanguage.toUpperCase();
            TextTranslationOptions textTranslationOptions = new TextTranslationOptions();
            textTranslationOptions.setTagHandling("html");
            textTranslationOptions.setTagHandlingVersion("v2");
            String glossaryId;
            boolean exception = false;
            try {
                glossaryId = deepLGlossaryManager.getOrCreateGlossaryId(translator, siteKey, deeplSrcLanguage, deeplTargetLanguage, glossaryTerms);
                if (StringUtils.isNotBlank(glossaryId)) {
                    textTranslationOptions.setGlossaryId(glossaryId);
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } catch (DeepLException e) {
                exception = true;
                logger.warn("Unable to create or get DeepL glossary, translations will be done without glossary for language pair {} / {}: {}", deeplSrcLanguage, deeplTargetLanguage, e.getMessage());
            }
            if(exception) {
                try {
                    logger.info("Retrying to create or get DeepL glossary without language code conversion for language pair {} / {}", sourceLocaleFromCode.getLanguage().toUpperCase(), targetLocaleFromCode.getLanguage().toUpperCase());
                    glossaryId = deepLGlossaryManager.getOrCreateGlossaryId(translator, siteKey, sourceLocaleFromCode.getLanguage().toUpperCase(), targetLocaleFromCode.getLanguage().toUpperCase(), glossaryTerms);
                    if (StringUtils.isNotBlank(glossaryId)) {
                        textTranslationOptions.setGlossaryId(glossaryId);
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                } catch (DeepLException e) {
                    logger.warn("Unable to create or get DeepL glossary, translations will be done without glossary for language pair {} / {}: {}", sourceLocaleFromCode.getLanguage().toUpperCase(), targetLocaleFromCode.getLanguage().toUpperCase(), e.getMessage());
                }
            }
            results = translator.translateText(srcTexts, deeplSrcLanguage, deeplTargetLanguage, textTranslationOptions);
        } catch (DeepLException e) {
            if (logger.isErrorEnabled()) {
                logger.error("Failed to translate content", e);
            }
            return Collections.emptyList();
        }

        final Map<String, String> translations = IntStream.range(0, nbTexts).boxed()
                .collect(Collectors.toMap(keys::get, i -> StringEscapeUtils.unescapeHtml4(results.get(i).getText())));

        return translationServicesManager.getTranslatedFieldList(data, subtree, translations);
    }
}
